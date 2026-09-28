package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.RulePushMessage;
import com.hengshucredit.rule.model.dto.RulePublishOutboxSummary;
import com.hengshucredit.rule.model.entity.RulePublishOutbox;
import com.hengshucredit.rule.server.mapper.RulePublishOutboxMapper;
import com.hengshucredit.rule.server.publish.RulePushService;
import jakarta.annotation.Resource;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RulePublishOutboxService {
    private static final int MAX_RETRY_DELAY_SECONDS = 300;
    /** Redis 发布正常应在数秒内完成；租约只用于实例崩溃后的恢复。 */
    private static final int DELIVERY_LEASE_SECONDS = 120;

    @Value("${rule-engine.publish-outbox.max-retries:20}")
    private int maxRetries = 20;

    @Resource
    private RulePublishOutboxMapper outboxMapper;
    @Resource
    private RulePushService pushService;

    private final ThreadLocal<String> lastDeliveryError = new ThreadLocal<>();
    private final ScheduledExecutorService leaseExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "rule-publish-outbox-lease");
        thread.setDaemon(true);
        return thread;
    });

    @Scheduled(fixedDelayString = "${rule-engine.publish-outbox.poll-delay-ms:3000}")
    public void deliverPending() {
        releaseExpiredClaims();
        for (RulePublishOutbox outbox : loadPending(100)) {
            deliver(outbox);
        }
    }

    public void deliver(RulePublishOutbox outbox) {
        if (outbox == null) return;
        if ("PENDING".equals(outbox.getDeliveryStatus())) {
            String claimToken = UUID.randomUUID().toString();
            if (!claim(outbox, claimToken)) return;
            outbox.setClaimToken(claimToken);
            outbox.setDeliveryStatus("DELIVERING");
        } else if (!"DELIVERING".equals(outbox.getDeliveryStatus())) {
            return;
        } else if (outboxMapper != null && (outbox.getClaimToken() == null || outbox.getClaimToken().isBlank())) {
            return;
        } else if (outboxMapper != null && (outbox.getLeaseUntil() == null
                || !outbox.getLeaseUntil().isAfter(LocalDateTime.now()))) {
            return;
        }
        lastDeliveryError.remove();
        LeaseHeartbeat heartbeat = beginLeaseHeartbeat(outbox);
        try {
            RulePushMessage message = JSON.parseObject(outbox.getMessageJson(), RulePushMessage.class);
            if (outbox.getOperationId() != null && !outbox.getOperationId().isBlank()) {
                message.setOperationId(outbox.getOperationId());
            }
            if (send(message)) {
                outbox.setDeliveryStatus("DELIVERED");
                outbox.setDeliveredTime(LocalDateTime.now());
                outbox.setLastError(null);
                outbox.setNextRetryTime(null);
                outbox.setDeadLetterTime(null);
            } else {
                scheduleRetry(outbox, lastDeliveryError.get() == null
                        ? "Redis 发布失败" : lastDeliveryError.get());
            }
        } catch (RuntimeException e) {
            scheduleRetry(outbox, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            if (heartbeat != null) heartbeat.close();
            outbox.setUpdateTime(LocalDateTime.now());
            updateOutbox(outbox);
            lastDeliveryError.remove();
        }
    }

    public List<RulePublishOutbox> listRecent(Long definitionId, int limit) {
        if (definitionId == null) throw new IllegalArgumentException("definitionId 不能为空");
        if (outboxMapper == null) return Collections.emptyList();
        return outboxMapper.selectList(new LambdaQueryWrapper<RulePublishOutbox>()
                .eq(RulePublishOutbox::getDefinitionId, definitionId)
                .orderByDesc(RulePublishOutbox::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    public List<RulePublishOutboxSummary> listRecentSummary(Long definitionId, int limit) {
        List<RulePublishOutbox> records = listRecent(definitionId, limit);
        List<RulePublishOutboxSummary> summaries = new ArrayList<>(records.size());
        for (RulePublishOutbox record : records) {
            RulePublishOutboxSummary summary = new RulePublishOutboxSummary();
            summary.setId(record.getId());
            summary.setOperationId(record.getOperationId());
            summary.setDefinitionId(record.getDefinitionId());
            summary.setRevisionId(record.getRevisionId());
            summary.setArtifactId(record.getArtifactId());
            summary.setDeliveryStatus(record.getDeliveryStatus());
            summary.setRetryCount(record.getRetryCount());
            summary.setLastError(record.getLastError());
            summary.setMessageDigest(sha256(record.getMessageJson()));
            summary.setCreateTime(record.getCreateTime());
            summary.setUpdateTime(record.getUpdateTime());
            summary.setDeliveredTime(record.getDeliveredTime());
            summary.setDeadLetterTime(record.getDeadLetterTime());
            summaries.add(summary);
        }
        return summaries;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (outboxMapper == null) return result;
        result.put("pending", outboxMapper.countByStatus("PENDING"));
        result.put("delivering", outboxMapper.countByStatus("DELIVERING"));
        result.put("delivered", outboxMapper.countByStatus("DELIVERED"));
        result.put("deadLetter", outboxMapper.countByStatus("DEAD_LETTER"));
        result.put("retrying", outboxMapper.countPendingRetries());
        java.time.LocalDateTime oldest = outboxMapper.oldestActiveTime();
        result.put("oldestActiveAt", oldest);
        result.put("oldestActiveAgeSeconds", oldest == null
                ? null : Math.max(0L, java.time.Duration.between(oldest, LocalDateTime.now()).getSeconds()));
        result.put("maxRetries", maxRetries);
        return result;
    }

    private void scheduleRetry(RulePublishOutbox outbox, String error) {
        int retry = (outbox.getRetryCount() == null ? 0 : outbox.getRetryCount()) + 1;
        outbox.setRetryCount(retry);
        outbox.setLastError(error);
        if (retry >= Math.max(1, maxRetries)) {
            outbox.setDeliveryStatus("DEAD_LETTER");
            outbox.setNextRetryTime(null);
            outbox.setDeadLetterTime(LocalDateTime.now());
            return;
        }
        int delay = Math.min(MAX_RETRY_DELAY_SECONDS, 1 << Math.min(retry, 8));
        outbox.setDeliveryStatus("PENDING");
        outbox.setNextRetryTime(LocalDateTime.now().plusSeconds(delay));
        outbox.setDeadLetterTime(null);
    }

    protected List<RulePublishOutbox> loadPending(int limit) {
        if (outboxMapper == null) return Collections.emptyList();
        return outboxMapper.selectList(new LambdaQueryWrapper<RulePublishOutbox>()
                .eq(RulePublishOutbox::getDeliveryStatus, "PENDING")
                .and(wrapper -> wrapper.isNull(RulePublishOutbox::getNextRetryTime)
                        .or().le(RulePublishOutbox::getNextRetryTime, LocalDateTime.now()))
                .orderByAsc(RulePublishOutbox::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 1000))));
    }

    private boolean claim(RulePublishOutbox outbox, String claimToken) {
        // Unit tests and lightweight callers may not provide a mapper; retain their
        // previous in-memory behavior while production always uses the atomic claim.
        if (outboxMapper == null || outbox.getId() == null) return true;
        return outboxMapper.claimPending(outbox.getId(), claimToken, DELIVERY_LEASE_SECONDS) == 1;
    }

    private void releaseExpiredClaims() {
        if (outboxMapper != null) {
            outboxMapper.releaseExpiredClaims(DELIVERY_LEASE_SECONDS);
        }
    }

    protected boolean send(RulePushMessage message) {
        return pushService.pushReliable(message);
    }

    protected void setLastDeliveryError(String error) {
        lastDeliveryError.set(error);
    }

    protected void updateOutbox(RulePublishOutbox outbox) {
        try {
            if (outboxMapper != null) outboxMapper.updateClaimed(outbox);
        } finally {
            outbox.setClaimToken(null);
            outbox.setLeaseUntil(null);
        }
    }

    private LeaseHeartbeat beginLeaseHeartbeat(RulePublishOutbox outbox) {
        if (outboxMapper == null || outbox.getId() == null
                || outbox.getClaimToken() == null || outbox.getClaimToken().isBlank()) return null;
        final String claimToken = outbox.getClaimToken();
        long intervalSeconds = Math.max(1, DELIVERY_LEASE_SECONDS / 3);
        AtomicBoolean leaseLost = new AtomicBoolean();
        ScheduledFuture<?> future = leaseExecutor.scheduleAtFixedRate(() -> {
            try {
                if (outboxMapper.renewClaim(outbox.getId(), claimToken, DELIVERY_LEASE_SECONDS) != 1) {
                    leaseLost.set(true);
                }
            } catch (RuntimeException error) {
                leaseLost.set(true);
                // 下一次续租或最终 token 校验会阻止旧实例覆盖新实例状态。
                org.slf4j.LoggerFactory.getLogger(RulePublishOutboxService.class)
                        .debug("发布 outbox 租约续期失败: {}", error.getMessage());
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        return new LeaseHeartbeat(future, leaseLost);
    }

    @PreDestroy
    public void shutdownLeaseExecutor() {
        leaseExecutor.shutdownNow();
    }

    private record LeaseHeartbeat(ScheduledFuture<?> future, AtomicBoolean leaseLost) implements AutoCloseable {
        @Override
        public void close() {
            future.cancel(false);
            if (leaseLost.get()) {
                org.slf4j.LoggerFactory.getLogger(RulePublishOutboxService.class)
                        .warn("发布 outbox 租约已丢失，最终状态将由 claim token 校验决定");
            }
        }
    }

    private String sha256(String message) {
        if (message == null) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
