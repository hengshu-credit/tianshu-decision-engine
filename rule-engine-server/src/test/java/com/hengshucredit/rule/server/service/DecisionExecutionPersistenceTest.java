package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DecisionExecutionPersistenceTest {
    @Test
    public void persistsLogAndBillingOffRequestThread() throws Exception {
        AtomicInteger logs = new AtomicInteger();
        AtomicInteger billing = new AtomicInteger();
        RuleExecutionLogService logService = new RuleExecutionLogService() {
            @Override
            public boolean save(RuleExecutionLog entity) {
                logs.incrementAndGet();
                return true;
            }
        };
        RuleBillingService billingService = new RuleBillingService() {
            @Override
            public void recordEngineExecution(RuleDefinition definition, boolean success,
                                               Long costTimeMs, String errorMessage,
                                               com.hengshucredit.rule.server.auth.ProjectAuthContext authContext) {
                billing.incrementAndGet();
            }
        };
        DecisionExecutionPersistence persistence =
                new DecisionExecutionPersistence(logService, billingService, 100);
        persistence.start();
        try {
            persistence.offer(new RuleExecutionLog(), new RuleDefinition(), true, 12L, null, null);
            long deadline = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < deadline
                    && ((Number) persistence.snapshot().get("persisted")).longValue() < 1) {
                Thread.sleep(10);
            }
            Map<String, Object> snapshot = persistence.snapshot();
            assertEquals(1, logs.get());
            assertEquals(1, billing.get());
            assertEquals(1L, snapshot.get("persisted"));
            assertTrue(((Number) snapshot.get("avgWriteMs")).doubleValue() >= 0D);
        } finally {
            persistence.close();
        }
    }
}
