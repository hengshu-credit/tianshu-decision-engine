package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.RulePushMessage;
import com.hengshucredit.rule.model.entity.RulePublishOutbox;
import com.hengshucredit.rule.server.mapper.RulePublishOutboxMapper;
import com.hengshucredit.rule.server.publish.RulePushService;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class RulePublishOutboxService {
    private static final int MAX_RETRY_DELAY_SECONDS = 300;

    @Resource
    private RulePublishOutboxMapper outboxMapper;
    @Resource
    private RulePushService pushService;

    private final ThreadLocal<String> lastDeliveryError = new ThreadLocal<>();

    @Scheduled(fixedDelayString = "${rule-engine.publish-outbox.poll-delay-ms:3000}")
    public void deliverPending() {
        for (RulePublishOutbox outbox : loadPending(100)) {
            deliver(outbox);
        }
    }

    public void deliver(RulePublishOutbox outbox) {
        if (outbox == null || !"PENDING".equals(outbox.getDeliveryStatus())) return;
        lastDeliveryError.remove();
        try {
            RulePushMessage message = JSON.parseObject(outbox.getMessageJson(), RulePushMessage.class);
            if (send(message)) {
                outbox.setDeliveryStatus("DELIVERED");
                outbox.setDeliveredTime(LocalDateTime.now());
                outbox.setLastError(null);
                outbox.setNextRetryTime(null);
            } else {
                scheduleRetry(outbox, lastDeliveryError.get() == null
                        ? "Redis 发布失败" : lastDeliveryError.get());
            }
        } catch (RuntimeException e) {
            scheduleRetry(outbox, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
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

    private void scheduleRetry(RulePublishOutbox outbox, String error) {
        int retry = (outbox.getRetryCount() == null ? 0 : outbox.getRetryCount()) + 1;
        int delay = Math.min(MAX_RETRY_DELAY_SECONDS, 1 << Math.min(retry, 8));
        outbox.setDeliveryStatus("PENDING");
        outbox.setRetryCount(retry);
        outbox.setLastError(error);
        outbox.setNextRetryTime(LocalDateTime.now().plusSeconds(delay));
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

    protected boolean send(RulePushMessage message) {
        return pushService.pushReliable(message);
    }

    protected void setLastDeliveryError(String error) {
        lastDeliveryError.set(error);
    }

    protected void updateOutbox(RulePublishOutbox outbox) {
        outboxMapper.updateById(outbox);
    }
}
