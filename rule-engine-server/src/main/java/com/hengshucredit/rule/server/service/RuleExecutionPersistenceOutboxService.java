package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleExecutionPersistenceOutbox;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.mapper.RuleExecutionPersistenceOutboxMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 请求确认前写入的日志/计费事件；由带租约的消费者异步落业务表。 */
@Service
public class RuleExecutionPersistenceOutboxService {
    static final int DELIVERY_BATCH_SIZE = 20;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RuleExecutionPersistenceOutboxService.class);
    private static final int CLAIM_LEASE_SECONDS = 120;

    @Resource private RuleExecutionPersistenceOutboxMapper mapper;
    @Resource private RuleExecutionLogService logService;
    @Resource private RuleBillingService billingService;
    @Autowired(required = false) private PlatformTransactionManager transactionManager;
    @Value("${rule-engine.execution-persistence-outbox.max-retries:20}")
    private int maxRetries = 20;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(RuleExecutionLog log, RuleDefinition definition, boolean success,
                        Long costTimeMs, String errorMessage, ProjectAuthContext authContext,
                        boolean logPending, boolean billingPending) {
        if (!logPending && !billingPending) return;
        if (definition != null && log != null) {
            if (log.getTraceId() != null) definition.setExecutionTraceId(log.getTraceId());
            if (log.getAttemptNo() != null) definition.setExecutionAttemptNo(log.getAttemptNo());
        }
        accept(new DurableExecutionPersistenceJournal.Entry(UUID.randomUUID().toString(),
                log == null ? null : JSON.toJSONString(log),
                definition == null ? null : JSON.toJSONString(definition), success, costTimeMs, errorMessage,
                authContext == null ? null : authContext.getProjectId(),
                authContext == null ? null : authContext.getProjectCode(),
                authContext == null ? null : authContext.getAuthId(),
                authContext == null ? null : authContext.getAuthCode(),
                authContext == null ? null : authContext.getAuthType(),
                authContext == null ? null : authContext.getTokenId(),
                authContext == null ? null : authContext.getTokenCode(),
                authContext == null ? null : authContext.getAuthPhase(), logPending, billingPending,
                billingPending ? billingService.captureEngineExecutionBilling(definition, success,
                        costTimeMs, errorMessage, authContext) : null));
    }

    /** 独立提交后才返回，调用方的外层事务回滚不能带走已接受的执行事件。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RuleExecutionPersistenceOutbox accept(DurableExecutionPersistenceJournal.Entry entry) {
        RuleExecutionPersistenceOutbox event = new RuleExecutionPersistenceOutbox();
        event.setEventId(entry.eventId());
        event.setLogJson(entry.logJson());
        event.setDefinitionJson(entry.definitionJson());
        event.setBillingSnapshotJson(entry.billingSnapshotJson());
        event.setSuccess(entry.success() ? 1 : 0);
        event.setCostTimeMs(entry.costTimeMs());
        event.setErrorMessage(entry.errorMessage());
        event.setProjectId(entry.projectId());
        event.setProjectCode(entry.projectCode());
        event.setAuthId(entry.authId());
        event.setAuthCode(entry.authCode());
        event.setAuthType(entry.authType());
        event.setTokenId(entry.tokenId());
        event.setTokenCode(entry.tokenCode());
        event.setAuthPhase(entry.authPhase());
        event.setLogPending(entry.logPending() ? 1 : 0);
        event.setBillingPending(entry.billingPending() ? 1 : 0);
        event.setDeliveryStatus("PENDING");
        event.setRetryCount(0);
        event.setCreateTime(LocalDateTime.now());
        event.setUpdateTime(LocalDateTime.now());
        try {
            if (mapper.insert(event) != 1) throw new IllegalStateException("执行持久化事件未写入");
        } catch (DuplicateKeyException duplicate) {
            // journal 转交 outbox 后进程退出，重放必须继续使用同一事件身份。
            RuleExecutionPersistenceOutbox existing = mapper.selectOne(new LambdaQueryWrapper<RuleExecutionPersistenceOutbox>()
                    .eq(RuleExecutionPersistenceOutbox::getEventId, entry.eventId()));
            if (existing == null) throw duplicate;
            return existing;
        }
        return event;
    }

    @Scheduled(fixedDelayString = "${rule-engine.execution-persistence-outbox.poll-delay-ms:5000}")
    public void replayPending() {
        mapper.releaseExpiredClaims(CLAIM_LEASE_SECONDS);
        if (transactionManager != null) {
            // 保留每轮最多100条的恢复预算；正常路径每20条只提交一次。
            for (int batch = 0; batch < 5; batch++) {
                if (replayBatch().isEmpty()) break;
            }
            return;
        }
        List<RuleExecutionPersistenceOutbox> events = mapper.selectList(new LambdaQueryWrapper<RuleExecutionPersistenceOutbox>()
                .eq(RuleExecutionPersistenceOutbox::getDeliveryStatus, "PENDING")
                .and(w -> w.isNull(RuleExecutionPersistenceOutbox::getNextRetryTime)
                        .or().le(RuleExecutionPersistenceOutbox::getNextRetryTime, LocalDateTime.now()))
                .orderByAsc(RuleExecutionPersistenceOutbox::getId)
                .last("LIMIT 100"));
        for (RuleExecutionPersistenceOutbox event : events) {
            deliver(event.getId());
        }
    }

    public RuleExecutionPersistenceOutbox deliver(Long eventId) {
        String claimToken = UUID.randomUUID().toString();
        if (mapper.claimPending(eventId, claimToken, CLAIM_LEASE_SECONDS) != 1) return null;
        // 内存通知可能晚于定时重试，不能用通知中的旧 pending 标记和 retryCount 覆盖现状。
        RuleExecutionPersistenceOutbox event = mapper.selectClaimed(eventId, claimToken);
        if (event == null) return null;
        event.setClaimToken(claimToken);
        replay(event);
        return event;
    }

    public List<RuleExecutionPersistenceOutbox> deliverBatch(List<Long> eventIds) {
        List<RuleExecutionPersistenceOutbox> delivered = new java.util.ArrayList<>();
        if (eventIds == null || eventIds.isEmpty()) return delivered;
        List<Long> ids = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(eventIds));
        for (int offset = 0; offset < ids.size(); offset += DELIVERY_BATCH_SIZE) {
            List<Long> batch = ids.subList(offset, Math.min(ids.size(), offset + DELIVERY_BATCH_SIZE));
            if (transactionManager != null) {
                try {
                    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                    List<RuleExecutionPersistenceOutbox> completed = transaction.execute(status -> completeBatch(batch));
                    if (completed != null) delivered.addAll(completed);
                    continue;
                } catch (RuntimeException failure) {
                    // 必须让整个批次事务先回滚；不能复用已在内存中清除pending的事件。
                    log.warn("批量执行事件消费未提交，转为独立领取逐条恢复", failure);
                }
            }
            for (Long eventId : batch) {
                RuleExecutionPersistenceOutbox event = deliver(eventId);
                if (event != null) delivered.add(event);
            }
        }
        return delivered;
    }

    public List<RuleExecutionPersistenceOutbox> replayBatch() {
        if (transactionManager == null) return java.util.Collections.emptyList();
        List<Long> selectedIds = new java.util.ArrayList<>();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // 批次只依赖显式事件/身份行锁；RR 范围锁会阻塞尚未确认的新事件 INSERT。
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try {
            List<RuleExecutionPersistenceOutbox> completed = transaction.execute(status -> {
                // 选取与加锁必须在同一条 SQL 中完成，才能越过整批已锁住的队首。
                List<RuleExecutionPersistenceOutbox> events = mapper.lockDueBatch(DELIVERY_BATCH_SIZE);
                for (RuleExecutionPersistenceOutbox event : events) selectedIds.add(event.getId());
                return completeLockedEvents(events);
            });
            return completed == null ? java.util.Collections.emptyList() : completed;
        } catch (RuntimeException failure) {
            if (selectedIds.isEmpty()) throw failure;
            log.warn("恢复批次消费未提交，转为独立领取逐条恢复", failure);
        }
        List<RuleExecutionPersistenceOutbox> delivered = new java.util.ArrayList<>();
        for (Long id : selectedIds) {
            RuleExecutionPersistenceOutbox event = deliver(id);
            if (event != null) delivered.add(event);
        }
        return delivered;
    }

    private List<RuleExecutionPersistenceOutbox> completeBatch(List<Long> ids) {
        return completeLockedEvents(mapper.lockPendingBatch(ids));
    }

    private List<RuleExecutionPersistenceOutbox> completeLockedEvents(List<RuleExecutionPersistenceOutbox> events) {
        if (events.isEmpty()) return events;
        Map<Long, String> traces = new java.util.HashMap<>();
        for (RuleExecutionPersistenceOutbox event : events) {
            RuleExecutionLog execution = JSON.parseObject(event.getLogJson(), RuleExecutionLog.class);
            String trace = execution == null ? null : execution.getTraceId();
            if (trace == null || trace.isBlank()) {
                // 实验只持久化计费事件，也必须按实际根 Trace 的顺序锁定账单。
                RuleDefinition definition = JSON.parseObject(event.getDefinitionJson(), RuleDefinition.class);
                trace = definition == null ? null : definition.getExecutionTraceId();
            }
            traces.put(event.getId(), trace == null ? "" : trace);
        }
        // 与日志批量入口使用同一trace锁序；不可用队列ID顺序代替逻辑执行身份顺序。
        events.sort(java.util.Comparator.comparing(
                (RuleExecutionPersistenceOutbox event) -> traces.get(event.getId()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RuleExecutionPersistenceOutbox::getId));
        for (RuleExecutionPersistenceOutbox event : events) {
            if (Integer.valueOf(1).equals(event.getLogPending())) writeLog(event);
            if (Integer.valueOf(1).equals(event.getBillingPending())) writeBilling(event);
        }
        if (mapper.completeLockedBatch(events.stream().map(RuleExecutionPersistenceOutbox::getId).toList()) != events.size()) {
            throw new IllegalStateException("批量执行事件确认不完整");
        }
        for (RuleExecutionPersistenceOutbox event : events) markDelivered(event);
        return events;
    }

    private void writeLog(RuleExecutionPersistenceOutbox event) {
        RuleExecutionLog execution = JSON.parseObject(event.getLogJson(), RuleExecutionLog.class);
        if (execution == null) throw new IllegalStateException("恢复事件缺少执行日志");
        logService.saveLogical(execution);
        event.setLogPending(0);
    }

    private void writeBilling(RuleExecutionPersistenceOutbox event) {
        if (event.getBillingSnapshotJson() != null) {
            billingService.recordEngineExecutionSnapshot(event.getBillingSnapshotJson());
            event.setBillingPending(0);
            return;
        }
        RuleDefinition definition = JSON.parseObject(event.getDefinitionJson(), RuleDefinition.class);
        if (definition == null) throw new IllegalStateException("计费恢复缺少规则定义");
        if (definition.getExecutionTraceId() == null && event.getLogJson() != null) {
            RuleExecutionLog execution = JSON.parseObject(event.getLogJson(), RuleExecutionLog.class);
            definition.setExecutionTraceId(execution == null ? null : execution.getTraceId());
            definition.setExecutionAttemptNo(execution == null ? null : execution.getAttemptNo());
        }
        billingService.recordLegacyEngineExecution(definition, event.getSuccess() != null && event.getSuccess() == 1,
                event.getCostTimeMs(), event.getErrorMessage(), authContext(event));
        event.setBillingPending(0);
    }

    private void markDelivered(RuleExecutionPersistenceOutbox event) {
        event.setDeliveryStatus("DELIVERED");
        event.setDeliveredTime(LocalDateTime.now());
        event.setLastError(null);
        event.setNextRetryTime(null);
        event.setLogJson(null);
        event.setDefinitionJson(null);
        event.setBillingSnapshotJson(null);
    }

    private void replay(RuleExecutionPersistenceOutbox event) {
        String failure = null;
        if (Integer.valueOf(1).equals(event.getLogPending())) {
            try {
                writeLog(event);
            } catch (RuntimeException error) {
                failure = "日志恢复失败: " + message(error);
            }
        }
        if (Integer.valueOf(1).equals(event.getBillingPending())) {
            try {
                writeBilling(event);
            } catch (RuntimeException error) {
                failure = failure == null ? "计费恢复失败: " + message(error)
                        : failure + "; " + message(error);
            }
        }
        if (Integer.valueOf(0).equals(event.getLogPending())
                && Integer.valueOf(0).equals(event.getBillingPending())) {
            markDelivered(event);
        } else {
            int retry = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
            event.setRetryCount(retry);
            event.setLastError(failure == null ? "恢复事件仍有待处理项" : failure);
            if (retry >= Math.max(1, maxRetries)) {
                event.setDeliveryStatus("DEAD_LETTER");
                event.setNextRetryTime(null);
            } else {
                event.setDeliveryStatus("PENDING");
                event.setNextRetryTime(LocalDateTime.now().plusSeconds(Math.min(300, 1L << Math.min(retry, 8))));
            }
        }
        event.setUpdateTime(LocalDateTime.now());
        try {
            if (mapper.updateClaimed(event) != 1) {
                throw new IllegalStateException("执行事件消费确认失败，租约可能已失效，保留数据库事件等待恢复");
            }
        } finally {
            event.setClaimToken(null);
            event.setLeaseUntil(null);
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pending", mapper.selectCount(new LambdaQueryWrapper<RuleExecutionPersistenceOutbox>()
                .eq(RuleExecutionPersistenceOutbox::getDeliveryStatus, "PENDING")));
        result.put("processing", mapper.selectCount(new LambdaQueryWrapper<RuleExecutionPersistenceOutbox>()
                .eq(RuleExecutionPersistenceOutbox::getDeliveryStatus, "PROCESSING")));
        result.put("deadLetter", mapper.selectCount(new LambdaQueryWrapper<RuleExecutionPersistenceOutbox>()
                .eq(RuleExecutionPersistenceOutbox::getDeliveryStatus, "DEAD_LETTER")));
        result.put("delivered", mapper.selectCount(new LambdaQueryWrapper<RuleExecutionPersistenceOutbox>()
                .eq(RuleExecutionPersistenceOutbox::getDeliveryStatus, "DELIVERED")));
        result.put("maxRetries", maxRetries);
        return result;
    }

    private ProjectAuthContext authContext(RuleExecutionPersistenceOutbox event) {
        if (event.getAuthType() == null && event.getProjectId() == null && event.getTokenId() == null) return null;
        if (event.getTokenId() != null) {
            return ProjectAuthContext.temporary(event.getProjectId(), event.getProjectCode(), event.getAuthId(),
                    event.getAuthCode(), event.getAuthType(), event.getTokenId(), event.getTokenCode(), event.getAuthPhase());
        }
        return ProjectAuthContext.direct(event.getProjectId(), event.getProjectCode(), event.getAuthId(),
                event.getAuthCode(), event.getAuthType());
    }

    private String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
