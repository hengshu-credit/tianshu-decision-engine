package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 将根执行日志和计费写入移出规则请求线程；队列满时回退到当前线程，保证审计和计费不丢失。
 */
@Component
public class DecisionExecutionPersistence implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DecisionExecutionPersistence.class);
    private static final String OVERFLOW_STRATEGY = "SYNC_FALLBACK";

    private final RuleExecutionLogService logService;
    private final RuleBillingService billingService;
    private final RuleExecutionPersistenceOutboxService recoveryOutbox;
    private final ArrayBlockingQueue<Event> queue;
    private final int queueCapacity;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong offered = new AtomicLong();
    private final AtomicLong persisted = new AtomicLong();
    private final AtomicLong fallback = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong logFailed = new AtomicLong();
    private final AtomicLong billingFailed = new AtomicLong();
    private final AtomicLong totalWriteMs = new AtomicLong();
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
        Event event = new Event(log, definition, success, costTimeMs, errorMessage, authContext);
        if (!queue.offer(event)) {
            fallback.incrementAndGet();
            lastFallbackAt.set(System.currentTimeMillis());
            persist(event);
            return;
        }
        int queueDepth = queue.size();
        maxQueueDepth.accumulateAndGet(queueDepth, Math::max);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        int queueDepth = queue.size();
        result.put("queueDepth", queueDepth);
        result.put("queueCapacity", queueCapacity);
        result.put("offered", offered.get());
        result.put("persisted", persisted.get());
        result.put("fallback", fallback.get());
        result.put("failed", failed.get());
        result.put("logFailed", logFailed.get());
        result.put("billingFailed", billingFailed.get());
        result.put("maxQueueDepth", maxQueueDepth.get());
        result.put("queueUtilization", (double) queueDepth / (double) queueCapacity);
        result.put("overflowStrategy", OVERFLOW_STRATEGY);
        result.put("lastFallbackAt", lastFallbackAt.get() == 0 ? null : lastFallbackAt.get());
        result.put("lastFailureAt", lastFailureAt.get() == 0 ? null : lastFailureAt.get());
        long count = persisted.get();
        result.put("avgWriteMs", count == 0 ? 0D : (double) totalWriteMs.get() / count);
        return result;
    }

    private void runLoop() {
        while (running.get() || !queue.isEmpty()) {
            try {
                Event event = queue.poll(200, TimeUnit.MILLISECONDS);
                if (event != null) persist(event);
            } catch (InterruptedException interrupted) {
                if (!running.get()) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void persist(Event event) {
        long started = System.nanoTime();
        boolean logSucceeded = event.log == null || retry("日志", () -> logService.saveLogical(event.log));
        if (!logSucceeded) logFailed.incrementAndGet();

        if (event.definition != null && event.log != null) {
            event.definition.setExecutionTraceId(event.log.getTraceId());
        }
        boolean billingSucceeded = event.definition == null || retry("计费", () ->
                billingService.recordEngineExecution(event.definition, event.success,
                        event.costTimeMs, event.errorMessage, event.authContext));
        if (!billingSucceeded) billingFailed.incrementAndGet();

        boolean succeeded = logSucceeded && billingSucceeded;
        if (!succeeded) {
            failed.incrementAndGet();
            lastFailureAt.set(System.currentTimeMillis());
            if (recoveryOutbox != null) {
                try {
                    recoveryOutbox.enqueue(event.log, event.definition, event.success, event.costTimeMs,
                            event.errorMessage, event.authContext,
                            event.log != null && !logSucceeded,
                            event.definition != null && !billingSucceeded);
                } catch (RuntimeException outboxError) {
                    log.error("持久化恢复事件入队失败: {}", outboxError.getMessage(), outboxError);
                }
            }
        }
        if (succeeded) {
            persisted.incrementAndGet();
            totalWriteMs.addAndGet(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }

    private boolean retry(String operation, Runnable action) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                action.run();
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
        if (!running.compareAndSet(true, false)) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private record Event(RuleExecutionLog log, RuleDefinition definition, boolean success,
                         Long costTimeMs, String errorMessage, ProjectAuthContext authContext) {
    }
}
