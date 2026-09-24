package com.hengshucredit.rule.server.health;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RuleWarmupStatusTest {
    @Test
    public void failedPublishedRuleKeepsReadinessOutOfService() {
        RuleWarmupStatus status = new RuleWarmupStatus();
        status.start(2);
        status.recordPrepared();
        status.recordFailure(17L, 3, new IllegalStateException("函数未绑定"));
        status.complete();

        assertEquals(RuleWarmupState.FAILED, status.getState());
        assertEquals(1, status.details().get("failureCount"));
        assertEquals("函数未绑定",
                ((java.util.List<?>) status.details().get("failures")).get(0) instanceof java.util.Map
                        ? ((java.util.Map<?, ?>) ((java.util.List<?>) status.details().get("failures")).get(0)).get("message")
                        : null);
    }

    @Test
    public void successfulWarmupBecomesReady() {
        RuleWarmupStatus status = new RuleWarmupStatus();
        status.start(1);
        status.recordPrepared();
        status.complete();

        assertEquals(RuleWarmupState.READY, status.getState());
    }
}
