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
            assertEquals(100, snapshot.get("queueCapacity"));
            assertEquals("SYNC_FALLBACK", snapshot.get("overflowStrategy"));
            assertEquals(0L, snapshot.get("logFailed"));
            assertEquals(0L, snapshot.get("billingFailed"));
            assertTrue(((Number) snapshot.get("queueUtilization")).doubleValue() >= 0D);
            assertTrue(((Number) snapshot.get("avgWriteMs")).doubleValue() >= 0D);
        } finally {
            persistence.close();
        }
    }

    @Test
    public void excludesFailedWriteTimeFromAverage() throws Exception {
        RuleExecutionLogService logService = new RuleExecutionLogService() {
            @Override
            public boolean save(RuleExecutionLog entity) {
                throw new IllegalStateException("write unavailable");
            }
        };
        DecisionExecutionPersistence persistence =
                new DecisionExecutionPersistence(logService, new RuleBillingService(), 100);
        persistence.start();
        try {
            persistence.offer(new RuleExecutionLog(), null, true, 12L, null, null);
            long deadline = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < deadline
                    && ((Number) persistence.snapshot().get("failed")).longValue() < 1) {
                Thread.sleep(10);
            }
            Map<String, Object> snapshot = persistence.snapshot();
            assertEquals(1L, snapshot.get("failed"));
            assertEquals(0L, snapshot.get("persisted"));
            assertEquals(0D, ((Number) snapshot.get("avgWriteMs")).doubleValue(), 0D);
        } finally {
            persistence.close();
        }
    }

    @Test
    public void logFailureDoesNotPreventBillingPersistence() throws Exception {
        AtomicInteger billing = new AtomicInteger();
        RuleExecutionLogService logService = new RuleExecutionLogService() {
            @Override
            public void saveLogical(RuleExecutionLog entity) {
                throw new IllegalStateException("log unavailable");
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
                    && ((Number) persistence.snapshot().get("failed")).longValue() < 1) {
                Thread.sleep(10);
            }
            Map<String, Object> snapshot = persistence.snapshot();
            assertEquals(1, billing.get());
            assertEquals(1L, snapshot.get("failed"));
            assertEquals(1L, snapshot.get("logFailed"));
            assertEquals(0L, snapshot.get("billingFailed"));
        } finally {
            persistence.close();
        }
    }

    @Test
    public void billingFailureDoesNotDuplicateSuccessfulLogWrite() throws Exception {
        AtomicInteger logs = new AtomicInteger();
        AtomicInteger billing = new AtomicInteger();
        RuleExecutionLogService logService = new RuleExecutionLogService() {
            @Override
            public void saveLogical(RuleExecutionLog entity) {
                logs.incrementAndGet();
            }
        };
        RuleBillingService billingService = new RuleBillingService() {
            @Override
            public void recordEngineExecution(RuleDefinition definition, boolean success,
                                               Long costTimeMs, String errorMessage,
                                               com.hengshucredit.rule.server.auth.ProjectAuthContext authContext) {
                billing.incrementAndGet();
                throw new IllegalStateException("billing unavailable");
            }
        };
        DecisionExecutionPersistence persistence =
                new DecisionExecutionPersistence(logService, billingService, 100);
        persistence.start();
        try {
            persistence.offer(new RuleExecutionLog(), new RuleDefinition(), true, 12L, null, null);
            long deadline = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < deadline
                    && ((Number) persistence.snapshot().get("failed")).longValue() < 1) {
                Thread.sleep(10);
            }
            Map<String, Object> snapshot = persistence.snapshot();
            assertEquals(1, logs.get());
            assertEquals(3, billing.get());
            assertEquals(0L, snapshot.get("logFailed"));
            assertEquals(1L, snapshot.get("billingFailed"));
        } finally {
            persistence.close();
        }
    }

    @Test
    public void partialWriteFailureEnqueuesOnlyTheFailedSideForRecovery() throws Exception {
        AtomicInteger recoveryEvents = new AtomicInteger();
        AtomicInteger recoveryLogPending = new AtomicInteger();
        AtomicInteger recoveryBillingPending = new AtomicInteger();
        RuleExecutionPersistenceOutboxService recovery = new RuleExecutionPersistenceOutboxService() {
            @Override
            public void enqueue(RuleExecutionLog log, RuleDefinition definition, boolean success,
                                Long costTimeMs, String errorMessage,
                                com.hengshucredit.rule.server.auth.ProjectAuthContext authContext,
                                boolean logPending, boolean billingPending) {
                recoveryEvents.incrementAndGet();
                if (logPending) recoveryLogPending.incrementAndGet();
                if (billingPending) recoveryBillingPending.incrementAndGet();
            }
        };
        RuleExecutionLogService logService = new RuleExecutionLogService() {
            @Override public void saveLogical(RuleExecutionLog entity) { throw new IllegalStateException("log unavailable"); }
        };
        RuleBillingService billingService = new RuleBillingService() {
            @Override public void recordEngineExecution(RuleDefinition definition, boolean success,
                                                        Long costTimeMs, String errorMessage,
                                                        com.hengshucredit.rule.server.auth.ProjectAuthContext authContext) { }
        };
        DecisionExecutionPersistence persistence =
                new DecisionExecutionPersistence(logService, billingService, 100, recovery);
        persistence.start();
        try {
            persistence.offer(new RuleExecutionLog(), new RuleDefinition(), true, 12L, null, null);
            long deadline = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < deadline && recoveryEvents.get() == 0) Thread.sleep(10);
            assertEquals(1, recoveryEvents.get());
            assertEquals(1, recoveryLogPending.get());
            assertEquals(0, recoveryBillingPending.get());
        } finally {
            persistence.close();
        }
    }
}
