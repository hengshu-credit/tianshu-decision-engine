package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.RulePushMessage;
import com.hengshucredit.rule.model.entity.RulePublishOutbox;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDateTime;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Update;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    public void maxRetriesMoveFailedDeliveryToDeadLetter() {
        FixtureService service = new FixtureService(false);
        ReflectionTestUtils.setField(service, "maxRetries", 1);
        RulePublishOutbox outbox = pending();

        service.deliver(outbox);

        Assert.assertEquals("DEAD_LETTER", outbox.getDeliveryStatus());
        Assert.assertEquals(Integer.valueOf(1), outbox.getRetryCount());
        Assert.assertNotNull(outbox.getDeadLetterTime());
        Assert.assertNull(outbox.getNextRetryTime());
    }

    @Test
    public void deliveryUsesOutboxOperationIdOverPayloadValue() {
        FixtureService service = new FixtureService(true);
        RulePublishOutbox outbox = pending();
        outbox.setOperationId("outbox-operation");
        outbox.setMessageJson(com.alibaba.fastjson.JSON.toJSONString(messageWithOperation("payload-operation")));

        service.deliver(outbox);

        Assert.assertEquals("outbox-operation", service.sent.getOperationId());
    }

    @Test
    public void mapperRequiresUnexpiredLeaseForRenewAndWriteback() throws Exception {
        Method renew = com.hengshucredit.rule.server.mapper.RulePublishOutboxMapper.class
                .getDeclaredMethod("renewClaim", Long.class, String.class, int.class);
        Method update = com.hengshucredit.rule.server.mapper.RulePublishOutboxMapper.class
                .getDeclaredMethod("updateClaimed", RulePublishOutbox.class);
        Assert.assertTrue(java.util.Arrays.stream(renew.getAnnotation(Update.class).value())
                .anyMatch(value -> value.contains("lease_until > CURRENT_TIMESTAMP")));
        Assert.assertTrue(java.util.Arrays.stream(update.getAnnotation(Update.class).value())
                .anyMatch(value -> value.contains("lease_until > CURRENT_TIMESTAMP")));
    }

    private static RulePublishOutbox pending() {
        RulePublishOutbox outbox = new RulePublishOutbox();
        outbox.setId(1L);
        outbox.setDeliveryStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setMessageJson(com.alibaba.fastjson.JSON.toJSONString(messageWithOperation(null)));
        return outbox;
    }

    private static RulePushMessage messageWithOperation(String operationId) {
        RulePushMessage message = new RulePushMessage();
        message.setRuleCode("R1");
        message.setAction("PUBLISH");
        message.setOperationId(operationId);
        return message;
    }

    private static final class FixtureService extends RulePublishOutboxService {
        private final boolean success;
        private RulePushMessage sent;

        private FixtureService(boolean success) {
            this.success = success;
        }

        @Override
        protected boolean send(RulePushMessage message) {
            sent = message;
            if (!success) setLastDeliveryError("redis unavailable");
            return success;
        }

        @Override
        protected void updateOutbox(RulePublishOutbox outbox) {
        }
    }
}
