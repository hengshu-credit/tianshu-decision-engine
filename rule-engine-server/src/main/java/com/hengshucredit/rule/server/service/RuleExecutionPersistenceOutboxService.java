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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 持久化日志/计费部分失败后的数据库恢复队列。 */
@Service
public class RuleExecutionPersistenceOutboxService {
    private static final int CLAIM_LEASE_SECONDS = 120;

    @Resource private RuleExecutionPersistenceOutboxMapper mapper;
    @Resource private RuleExecutionLogService logService;
    @Resource private RuleBillingService billingService;
    @Value("${rule-engine.execution-persistence-outbox.max-retries:20}")
    private int maxRetries = 20;

    public void enqueue(RuleExecutionLog log, RuleDefinition definition, boolean success,
                        Long costTimeMs, String errorMessage, ProjectAuthContext authContext,
                        boolean logPending, boolean billingPending) {
        if (!logPending && !billingPending) return;
        RuleExecutionPersistenceOutbox event = new RuleExecutionPersistenceOutbox();
        event.setEventId(UUID.randomUUID().toString());
        event.setLogJson(log == null ? null : JSON.toJSONString(log));
        event.setDefinitionJson(definition == null ? null : JSON.toJSONString(definition));
        event.setSuccess(success ? 1 : 0);
        event.setCostTimeMs(costTimeMs);
        event.setErrorMessage(errorMessage);
        if (authContext != null) {
            event.setProjectId(authContext.getProjectId());
            event.setProjectCode(authContext.getProjectCode());
            event.setAuthId(authContext.getAuthId());
            event.setAuthCode(authContext.getAuthCode());
            event.setAuthType(authContext.getAuthType());
            event.setTokenId(authContext.getTokenId());
            event.setTokenCode(authContext.getTokenCode());
            event.setAuthPhase(authContext.getAuthPhase());
        }
        event.setLogPending(logPending ? 1 : 0);
        event.setBillingPending(billingPending ? 1 : 0);
        event.setDeliveryStatus("PENDING");
        event.setRetryCount(0);
        event.setCreateTime(LocalDateTime.now());
        event.setUpdateTime(LocalDateTime.now());
        mapper.insert(event);
    }

    @Scheduled(fixedDelayString = "${rule-engine.execution-persistence-outbox.poll-delay-ms:5000}")
    public void replayPending() {
        mapper.releaseExpiredClaims(CLAIM_LEASE_SECONDS);
        List<RuleExecutionPersistenceOutbox> events = mapper.selectList(new LambdaQueryWrapper<RuleExecutionPersistenceOutbox>()
                .eq(RuleExecutionPersistenceOutbox::getDeliveryStatus, "PENDING")
                .and(w -> w.isNull(RuleExecutionPersistenceOutbox::getNextRetryTime)
                        .or().le(RuleExecutionPersistenceOutbox::getNextRetryTime, LocalDateTime.now()))
                .orderByAsc(RuleExecutionPersistenceOutbox::getId)
                .last("LIMIT 100"));
        for (RuleExecutionPersistenceOutbox event : events) {
            String claimToken = UUID.randomUUID().toString();
            if (mapper.claimPending(event.getId(), claimToken, CLAIM_LEASE_SECONDS) == 1) {
                event.setClaimToken(claimToken);
                replay(event);
            }
        }
    }

    private void replay(RuleExecutionPersistenceOutbox event) {
        String failure = null;
        if (Integer.valueOf(1).equals(event.getLogPending())) {
            try {
                RuleExecutionLog log = JSON.parseObject(event.getLogJson(), RuleExecutionLog.class);
                if (log == null) throw new IllegalStateException("恢复事件缺少执行日志");
                logService.saveLogical(log);
                event.setLogPending(0);
            } catch (RuntimeException error) {
                failure = "日志恢复失败: " + message(error);
            }
        }
        if (Integer.valueOf(1).equals(event.getBillingPending())) {
            try {
                RuleDefinition definition = JSON.parseObject(event.getDefinitionJson(), RuleDefinition.class);
                if (definition == null) throw new IllegalStateException("计费恢复缺少规则定义");
                if (definition.getExecutionTraceId() == null && event.getLogJson() != null) {
                    RuleExecutionLog log = JSON.parseObject(event.getLogJson(), RuleExecutionLog.class);
                    definition.setExecutionTraceId(log == null ? null : log.getTraceId());
                    definition.setExecutionAttemptNo(log == null ? null : log.getAttemptNo());
                }
                billingService.recordEngineExecution(definition, event.getSuccess() != null && event.getSuccess() == 1,
                        event.getCostTimeMs(), event.getErrorMessage(), authContext(event));
                event.setBillingPending(0);
            } catch (RuntimeException error) {
                failure = failure == null ? "计费恢复失败: " + message(error)
                        : failure + "; " + message(error);
            }
        }
        if (Integer.valueOf(0).equals(event.getLogPending())
                && Integer.valueOf(0).equals(event.getBillingPending())) {
            event.setDeliveryStatus("DELIVERED");
            event.setDeliveredTime(LocalDateTime.now());
            event.setLastError(null);
            event.setNextRetryTime(null);
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
            mapper.updateClaimed(event);
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
