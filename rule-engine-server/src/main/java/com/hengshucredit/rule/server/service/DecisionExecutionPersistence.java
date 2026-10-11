package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleExecutionPersistenceOutbox;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求返回前先独立提交恢复事件，内存队列仅用于加速消费。
 * 数据库恢复队列不可用时先写 journal；所有恢复存储不可用则同步写业务表并明确报告失败。
 */
@Component
public class DecisionExecutionPersistence implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DecisionExecutionPersistence.class);

    private final RuleExecutionLogService logService;
    private final RuleBillingService billingService;
    private final RuleExecutionPersistenceOutboxService recoveryOutbox;
    @Autowired(required = false)
    private DurableExecutionPersistenceJournal journal;
    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;
    @Value("${rule-engine.execution-persistence.journal-replay-batch-size:1000}")
    private int journalReplayBatchSize = 1000;
    private final ArrayBlockingQueue<Long> queue;
    private final int queueCapacity;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean replayingJournal = new AtomicBoolean();
    private final AtomicLong offered = new AtomicLong();
    private final AtomicLong durablyAccepted = new AtomicLong();
    private final AtomicLong outboxDeferred = new AtomicLong();
    private final AtomicLong persisted = new AtomicLong();
    private final AtomicLong fallback = new AtomicLong();
    private final AtomicLong journalQueued = new AtomicLong();
    private final AtomicLong journalFailed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong logFailed = new AtomicLong();
    private final AtomicLong billingFailed = new AtomicLong();
    private final AtomicLong totalWriteMs = new AtomicLong();
    private final AtomicLong writeTimingSamples = new AtomicLong();
    private final AtomicLong maxQueueDepth = new AtomicLong();
    private final AtomicLong lastFallbackAt = new AtomicLong();
    private final AtomicLong lastFailureAt = new AtomicLong();

    public DecisionExecutionPersistence(RuleExecutionLogService logService,
                                        RuleBillingService billingService,
                                        @Value("${rule-engine.execution-persistence.queue-capacity:5000}") int capacity) {
        this(logService, billingService, capacity, null);
    }

    @Autowired
    public DecisionExecutionPersistence(RuleExecutionLogService logService,
                                        RuleBillingService billingService,
                                        @Value("${rule-engine.execution-persistence.queue-capacity:5000}") int capacity,
                                        RuleExecutionPersistenceOutboxService recoveryOutbox) {
        this.logService = logService;
        this.billingService = billingService;
        this.recoveryOutbox = recoveryOutbox;
        this.queueCapacity = Math.max(100, capacity);
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "decision-execution-persistence");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PostConstruct
    public void start() {
        if (running.compareAndSet(false, true)) executor.submit(this::runLoop);
    }

    public void offer(RuleExecutionLog log, RuleDefinition definition,
                      boolean success, Long costTimeMs, String errorMessage,
                      ProjectAuthContext authContext) {
        if (log == null && definition == null) return;
        offered.incrementAndGet();
        if (definition != null && log != null) {
            definition.setExecutionVersion(log.getRuleVersion());
            definition.setExecutionRevisionId(log.getRevisionId());
            definition.setExecutionArtifactDigest(log.getArtifactDigest());
            if (log.getTraceId() != null) definition.setExecutionTraceId(log.getTraceId());
            if (log.getAttemptNo() != null) definition.setExecutionAttemptNo(log.getAttemptNo());
        }
        String billingSnapshot = definition == null ? null : billingService.captureEngineExecutionBilling(
                definition, success, costTimeMs, errorMessage, authContext);
        Event event = new Event(log, definition, success, costTimeMs, errorMessage, authContext,
                log != null, definition != null, billingSnapshot);
        if (!storeForRecovery(event)) {
            fallback.incrementAndGet();
            lastFallbackAt.set(System.currentTimeMillis());
            boolean confirmed = persist(event);
            if (!confirmed && !storeForRecovery(event)) {
                throw new IllegalStateException("执行事件无法持久化，结果可能已经产生，请勿盲目重试");
            }
        }
        durablyAccepted.incrementAndGet();
        if (event.durableEvent != null) {
            // 排队失败或进程退出均由 outbox 的租约消费者接手，内存通知不承担交付保证。
            if (running.get() && !queue.offer(event.durableEvent.getId())) outboxDeferred.incrementAndGet();
            maxQueueDepth.accumulateAndGet(queue.size(), Math::max);
        }
    }

    private boolean storeForRecovery(Event event) {
        if (recoveryOutbox != null) {
            try {
                event.durableEvent = recoveryOutbox.accept(toJournalEntry(event));
                return true;
            } catch (RuntimeException unavailable) {
                DecisionExecutionPersistence.log.warn("执行事件恢复队列不可写，尝试本地 journal", unavailable);
            }
        }
        if (journal != null && journal.append(toJournalEntry(event))) {
            journalQueued.incrementAndGet();
            return true;
        }
        if (journal != null) journalFailed.incrementAndGet();
        return false;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        int queueDepth = queue.size();
        result.put("queueDepth", queueDepth);
        result.put("queueCapacity", queueCapacity);
        result.put("offered", offered.get());
        result.put("durablyAccepted", durablyAccepted.get());
        result.put("outboxDeferred", outboxDeferred.get());
        result.put("persisted", persisted.get());
        result.put("fallback", fallback.get());
        result.put("journalQueued", journalQueued.get());
        result.put("journalFailed", journalFailed.get());
        result.put("failed", failed.get());
        result.put("logFailed", logFailed.get());
        result.put("billingFailed", billingFailed.get());
        result.put("maxQueueDepth", maxQueueDepth.get());
        result.put("queueUtilization", (double) queueDepth / (double) queueCapacity);
        result.put("overflowStrategy", recoveryOutbox != null ? "DURABLE_OUTBOX_REPLAY"
                : journal == null ? "SYNC_FALLBACK" : "JOURNAL_THEN_SYNC_FALLBACK");
        result.put("lastFallbackAt", lastFallbackAt.get() == 0 ? null : lastFallbackAt.get());
        result.put("lastFailureAt", lastFailureAt.get() == 0 ? null : lastFailureAt.get());
        long count = writeTimingSamples.get();
        result.put("writeTimingSamples", count);
        result.put("avgWriteMs", count == 0 ? 0D : (double) totalWriteMs.get() / count);
        if (journal != null) result.put("journal", journal.snapshot());
        return result;
    }

    private void runLoop() {
        boolean recovering = false;
        boolean recoverBeforeNotification = false;
        while (running.get() || !queue.isEmpty()) {
            try {
                // 新通知持续到达时，也给重启或队列溢出留下的数据库事件消费机会。
                boolean recoveryTurn = recoverBeforeNotification && running.get();
                recoverBeforeNotification = false;
                Long first = recoveryTurn ? null : recovering ? queue.poll() : queue.poll(200, TimeUnit.MILLISECONDS);
                if (recoveryOutbox != null) {
                    long started = System.nanoTime();
                    List<RuleExecutionPersistenceOutbox> outcomes;
                    if (first != null) {
                        List<Long> ids = new ArrayList<>();
                        ids.add(first);
                        queue.drainTo(ids, RuleExecutionPersistenceOutboxService.DELIVERY_BATCH_SIZE - 1);
                        recoverBeforeNotification = true;
                        outcomes = recoveryOutbox.deliverBatch(ids);
                    } else {
                        outcomes = running.get() ? recoveryOutbox.replayBatch() : java.util.Collections.emptyList();
                    }
                    recovering = !outcomes.isEmpty();
                    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                    if (!outcomes.isEmpty() && outcomes.stream().allMatch(row -> "DELIVERED".equals(row.getDeliveryStatus()))) {
                        totalWriteMs.addAndGet(elapsed);
                        writeTimingSamples.addAndGet(outcomes.size());
                    }
                    for (RuleExecutionPersistenceOutbox delivered : outcomes) {
                        if ("DELIVERED".equals(delivered.getDeliveryStatus())) {
                            persisted.incrementAndGet();
                        } else {
                            if (Integer.valueOf(1).equals(delivered.getLogPending())) logFailed.incrementAndGet();
                            if (Integer.valueOf(1).equals(delivered.getBillingPending())) billingFailed.incrementAndGet();
                            failed.incrementAndGet();
                            lastFailureAt.set(System.currentTimeMillis());
                        }
                    }
                }
            } catch (InterruptedException interrupted) {
                if (!running.get()) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } catch (RuntimeException persistenceFailure) {
                recovering = false;
                failed.incrementAndGet();
                lastFailureAt.set(System.currentTimeMillis());
                log.error("执行事件异步消费失败，已接受事件保留在恢复队列中", persistenceFailure);
            }
        }
    }

    private boolean persist(Event event) {
        long started = System.nanoTime();
        boolean logSucceeded = !event.logPending || event.log == null
                || retry("日志", () -> logService.saveLogical(event.log));
        if (!logSucceeded) logFailed.incrementAndGet();

        boolean billingSucceeded = !event.billingPending || event.definition == null || retry("计费", () -> {
            if (event.billingSnapshotJson != null) billingService.recordEngineExecutionSnapshot(event.billingSnapshotJson);
            else billingService.recordLegacyEngineExecution(event.definition, event.success,
                    event.costTimeMs, event.errorMessage, event.authContext);
        });
        if (!billingSucceeded) billingFailed.incrementAndGet();

        event.logPending = event.log != null && !logSucceeded;
        event.billingPending = event.definition != null && !billingSucceeded;

        boolean succeeded = logSucceeded && billingSucceeded;
        if (!succeeded) {
            failed.incrementAndGet();
            lastFailureAt.set(System.currentTimeMillis());
        }
        if (succeeded) {
            persisted.incrementAndGet();
            writeTimingSamples.incrementAndGet();
            totalWriteMs.addAndGet(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
        return succeeded;
    }

    /** 数据库恢复后重放此前无法写入数据库 outbox 的本地事件。 */
    @Scheduled(fixedDelayString = "${rule-engine.execution-persistence.journal-replay-delay-ms:5000}")
    public void replayJournal() {
        if (journal == null || !replayingJournal.compareAndSet(false, true)) return;
        try {
            List<DurableExecutionPersistenceJournal.Entry> entries = journal.read(
                    Math.max(1, journalReplayBatchSize));
            if (entries.isEmpty()) {
                // 只含空白行的完整前缀也可安全前进，避免一直重扫同一字节预算。
                journal.replacePrefix(0, List.of());
                return;
            }
            List<DurableExecutionPersistenceJournal.Entry> remaining = new ArrayList<>();
            int processed = 0;
            for (DurableExecutionPersistenceJournal.Entry entry : entries) {
                if (recoveryOutbox != null) {
                    try {
                        // 先提交同一身份的 outbox 事件，之后才能从 journal 移除；崩溃重放可去重。
                        recoveryOutbox.accept(entry);
                        processed++;
                        continue;
                    } catch (RuntimeException unavailable) {
                        // 不让持续数据库故障阻塞整个批次；未尝试的尾部仍留在原文件。
                        break;
                    }
                }
                processed++;
                Event event = fromJournalEntry(entry);
                persist(event);
                if (event.logPending || event.billingPending) remaining.add(toJournalEntry(event));
            }
            // 文件锁只用于短时间读写，恢复期间 append 可继续写入，replacePrefix保留新增尾部。
            if (processed > 0) journal.replacePrefix(processed, remaining);
        } finally {
            replayingJournal.set(false);
        }
    }

    private DurableExecutionPersistenceJournal.Entry toJournalEntry(Event event) {
        ProjectAuthContext auth = event.authContext;
        return new DurableExecutionPersistenceJournal.Entry(
                event.eventId,
                event.log == null ? null : com.alibaba.fastjson.JSON.toJSONString(event.log),
                event.definition == null ? null : com.alibaba.fastjson.JSON.toJSONString(event.definition),
                event.success, event.costTimeMs, event.errorMessage,
                auth == null ? null : auth.getProjectId(), auth == null ? null : auth.getProjectCode(),
                auth == null ? null : auth.getAuthId(), auth == null ? null : auth.getAuthCode(),
                auth == null ? null : auth.getAuthType(), auth == null ? null : auth.getTokenId(),
                auth == null ? null : auth.getTokenCode(), auth == null ? null : auth.getAuthPhase(),
                event.logPending, event.billingPending, event.billingSnapshotJson);
    }

    private Event fromJournalEntry(DurableExecutionPersistenceJournal.Entry entry) {
        RuleExecutionLog log = entry.logJson() == null ? null
                : com.alibaba.fastjson.JSON.parseObject(entry.logJson(), RuleExecutionLog.class);
        RuleDefinition definition = entry.definitionJson() == null ? null
                : com.alibaba.fastjson.JSON.parseObject(entry.definitionJson(), RuleDefinition.class);
        ProjectAuthContext auth = null;
        if (entry.tokenId() != null) {
            auth = ProjectAuthContext.temporary(entry.projectId(), entry.projectCode(), entry.authId(),
                    entry.authCode(), entry.authType(), entry.tokenId(), entry.tokenCode(), entry.authPhase());
        } else if (entry.projectId() != null || entry.authId() != null) {
            auth = ProjectAuthContext.direct(entry.projectId(), entry.projectCode(), entry.authId(),
                    entry.authCode(), entry.authType());
        }
        return new Event(entry.eventId(), log, definition, entry.success(), entry.costTimeMs(), entry.errorMessage(), auth,
                entry.logPending(), entry.billingPending(), entry.billingSnapshotJson());
    }

    private boolean retry(String operation, Runnable action) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                if (transactionManager == null) {
                    action.run();
                } else {
                    // 每次重试独立提交。死锁可能回滚整个事务，不能只在同一事务内重试后一侧。
                    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    transaction.executeWithoutResult(status -> action.run());
                }
                return true;
            } catch (RuntimeException error) {
                lastError = error;
                if (attempt < 3) {
                    try { Thread.sleep(attempt * 100L); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        if (lastError != null) {
            log.error("异步持久化{}失败: {}", operation, lastError.getMessage(), lastError);
        }
        return false;
    }

    @PreDestroy
    @Override
    public void close() {
        running.set(false);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private static final class Event {
        private final String eventId;
        private RuleExecutionPersistenceOutbox durableEvent;
        private final RuleExecutionLog log;
        private final RuleDefinition definition;
        private final boolean success;
        private final Long costTimeMs;
        private final String errorMessage;
        private final ProjectAuthContext authContext;
        private final String billingSnapshotJson;
        private boolean logPending;
        private boolean billingPending;

        private Event(RuleExecutionLog log, RuleDefinition definition, boolean success,
                      Long costTimeMs, String errorMessage, ProjectAuthContext authContext,
                      boolean logPending, boolean billingPending, String billingSnapshotJson) {
            this(java.util.UUID.randomUUID().toString(), log, definition, success, costTimeMs,
                    errorMessage, authContext, logPending, billingPending, billingSnapshotJson);
        }

        private Event(String eventId, RuleExecutionLog log, RuleDefinition definition, boolean success,
                      Long costTimeMs, String errorMessage, ProjectAuthContext authContext,
                      boolean logPending, boolean billingPending, String billingSnapshotJson) {
            this.eventId = eventId;
            this.log = log;
            this.definition = definition;
            this.success = success;
            this.costTimeMs = costTimeMs;
            this.errorMessage = errorMessage;
            this.authContext = authContext;
            this.billingSnapshotJson = billingSnapshotJson;
            this.logPending = logPending;
            this.billingPending = billingPending;
        }
    }
}
