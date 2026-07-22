package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.RulePushMessage;
import com.hengshucredit.rule.model.entity.RulePublishOutbox;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDateTime;

public class RulePublishOutboxServiceTest {

    @Test
    public void successfulDeliveryMarksOutboxDelivered() {
        FixtureService service = new FixtureService(true);
        RulePublishOutbox outbox = pending();

        service.deliver(outbox);

        Assert.assertEquals("DELIVERED", outbox.getDeliveryStatus());
        Assert.assertNotNull(outbox.getDeliveredTime());
        Assert.assertNull(outbox.getLastError());
    }

    @Test
    public void failedDeliveryKeepsPendingWithBoundedRetry() {
        FixtureService service = new FixtureService(false);
        RulePublishOutbox outbox = pending();
        LocalDateTime before = LocalDateTime.now();

        service.deliver(outbox);

        Assert.assertEquals("PENDING", outbox.getDeliveryStatus());
        Assert.assertEquals(Integer.valueOf(1), outbox.getRetryCount());
        Assert.assertNotNull(outbox.getLastError());
        Assert.assertTrue(outbox.getNextRetryTime().isAfter(before));
        Assert.assertTrue(outbox.getNextRetryTime().isBefore(before.plusSeconds(301)));
    }

    private static RulePublishOutbox pending() {
        RulePushMessage message = new RulePushMessage();
        message.setRuleCode("R1");
        message.setAction("PUBLISH");
        RulePublishOutbox outbox = new RulePublishOutbox();
        outbox.setId(1L);
        outbox.setDeliveryStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setMessageJson(com.alibaba.fastjson.JSON.toJSONString(message));
        return outbox;
    }

    private static final class FixtureService extends RulePublishOutboxService {
        private final boolean success;

        private FixtureService(boolean success) {
            this.success = success;
        }

        @Override
        protected boolean send(RulePushMessage message) {
            if (!success) setLastDeliveryError("redis unavailable");
            return success;
        }

        @Override
        protected void updateOutbox(RulePublishOutbox outbox) {
        }
    }
}
