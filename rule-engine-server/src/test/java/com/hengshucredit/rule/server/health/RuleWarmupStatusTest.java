package com.hengshucredit.rule.server.health;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class RuleWarmupStatusTest {
    @Test
    public void failedPublishedRuleKeepsReadinessOutOfService() {
        RuleWarmupStatus status = new RuleWarmupStatus();
        status.start(2);
        status.recordPrepared();
        status.recordFailure(17L, 3, new IllegalStateException("函数未绑定"));
        status.complete();

        assertEquals(RuleWarmupState.FAILED, status.getState());
        assertEquals("PRODUCTION_ACTIVE", status.details().get("targetScope"));
        assertEquals(1, status.details().get("failureCount"));
        assertEquals(17L, ((java.util.Map<?, ?>) ((java.util.List<?>) status.details().get("failures")).get(0)).get("definitionId"));
        assertEquals(null, ((java.util.Map<?, ?>) ((java.util.List<?>) status.details().get("failures")).get(0)).get("revisionId"));
        assertNotNull(status.details().get("runId"));
        assertNotNull(status.details().get("completedAt"));
        assertEquals("函数未绑定",
                ((java.util.List<?>) status.details().get("failures")).get(0) instanceof java.util.Map
                        ? ((java.util.Map<?, ?>) ((java.util.List<?>) status.details().get("failures")).get(0)).get("message")
                        : null);
        assertEquals("FUNCTION_NOT_BOUND",
                ((java.util.Map<?, ?>) ((java.util.List<?>) status.details().get("failures")).get(0)).get("code"));
        assertEquals("在规则设计器中重新选择对应函数，保存并重新编译发布",
                ((java.util.Map<?, ?>) ((java.util.List<?>) status.details().get("failures")).get(0)).get("nextAction"));
    }

    @Test
    public void successfulWarmupBecomesReady() {
        RuleWarmupStatus status = new RuleWarmupStatus();
        status.start(1);
        status.recordPrepared();
        status.complete();

        assertEquals(RuleWarmupState.READY, status.getState());
    }

    @Test
    public void failedWarmupCanBeRetriedAfterRepair() {
        RuleWarmupStatus status = new RuleWarmupStatus();
        status.start(1);
        status.recordFailure(17L, 3, new IllegalStateException("函数未绑定"));
        status.complete();

        status.retry(1);
        status.recordPrepared();
        status.complete();

        assertEquals(RuleWarmupState.READY, status.getState());
        assertEquals(0, status.details().get("failureCount"));
        assertEquals(1, status.details().get("preparedCount"));
    }

    @Test
    public void syntaxFailureIncludesRepairAction() {
        RuleWarmupStatus status = new RuleWarmupStatus();
        status.start(1);
        status.recordFailure(17L, 1, new IllegalStateException("SYNTAX_ERROR: mismatched input '='"));
        status.complete();

        java.util.Map<?, ?> failure = (java.util.Map<?, ?>) ((java.util.List<?>) status.details().get("failures")).get(0);
        assertEquals("INVALID_SCRIPT", failure.get("code"));
        assertEquals("打开规则设计器修复配置或脚本，编译通过后重新发布", failure.get("nextAction"));
    }
}
