package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DecisionExecutionPersistenceTest {
    @Test
    public void confirmsLogAndBillingBeforeReturnWithoutRecoveryStorage() throws Exception {
        AtomicInteger logs = new AtomicInteger();
        AtomicInteger billing = new AtomicInteger();
        RuleExecutionLogService logService = new RuleExecutionLogService() {
            @Override
            public boolean save(RuleExecutionLog entity) {
                logs.incrementAndGet();
                return true;
            }
        };
        RuleBillingService billingService = new NoPriceBillingService() {
            @Override
            public void recordEngineExecutionSnapshot(String snapshotJson) {
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
    public void journalAcceptsEveryEventBeforeReturnWithoutDatabaseOutbox() {
        AtomicInteger journalWrites = new AtomicInteger();
        DurableExecutionPersistenceJournal journal = new DurableExecutionPersistenceJournal() {
            @Override
            public synchronized boolean append(Entry entry) {
                journalWrites.incrementAndGet();
                return true;
            }
        };
        DecisionExecutionPersistence persistence = new DecisionExecutionPersistence(
                new RuleExecutionLogService(), new RuleBillingService(), 100);
        ReflectionTestUtils.setField(persistence, "journal", journal);
        for (int i = 0; i < 101; i++) {
            persistence.offer(new RuleExecutionLog(), new RuleDefinition(), true, 12L, null, null);
        }

        Map<String, Object> snapshot = persistence.snapshot();
        assertEquals(101, journalWrites.get());
        assertEquals(101L, snapshot.get("journalQueued"));
        assertEquals(0, snapshot.get("queueDepth"));
        assertEquals(0L, snapshot.get("fallback"));
        assertEquals("JOURNAL_THEN_SYNC_FALLBACK", snapshot.get("overflowStrategy"));
        persistence.close();
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
            org.junit.Assert.assertThrows(IllegalStateException.class, () ->
                    persistence.offer(new RuleExecutionLog(), null, true, 12L, null, null));
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
        RuleBillingService billingService = new NoPriceBillingService() {
            @Override
            public void recordEngineExecutionSnapshot(String snapshotJson) {
                billing.incrementAndGet();
            }
        };
        DecisionExecutionPersistence persistence =
                new DecisionExecutionPersistence(logService, billingService, 100);
        persistence.start();
        try {
            org.junit.Assert.assertThrows(IllegalStateException.class, () ->
                    persistence.offer(new RuleExecutionLog(), new RuleDefinition(), true, 12L, null, null));
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
        RuleBillingService billingService = new NoPriceBillingService() {
            @Override
            public void recordEngineExecutionSnapshot(String snapshotJson) {
                billing.incrementAndGet();
                throw new IllegalStateException("billing unavailable");
            }
        };
        DecisionExecutionPersistence persistence =
                new DecisionExecutionPersistence(logService, billingService, 100);
        persistence.start();
        try {
            org.junit.Assert.assertThrows(IllegalStateException.class, () ->
                    persistence.offer(new RuleExecutionLog(), new RuleDefinition(), true, 12L, null, null));
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
            private boolean first = true;
            @Override
            public com.hengshucredit.rule.model.entity.RuleExecutionPersistenceOutbox accept(
                    DurableExecutionPersistenceJournal.Entry entry) {
                if (first) {
                    first = false;
                    throw new IllegalStateException("first durable accept unavailable");
                }
                recoveryEvents.incrementAndGet();
                if (entry.logPending()) recoveryLogPending.incrementAndGet();
                if (entry.billingPending()) recoveryBillingPending.incrementAndGet();
                return new com.hengshucredit.rule.model.entity.RuleExecutionPersistenceOutbox();
            }
        };
        RuleExecutionLogService logService = new RuleExecutionLogService() {
            @Override public void saveLogical(RuleExecutionLog entity) { throw new IllegalStateException("log unavailable"); }
        };
        RuleBillingService billingService = new NoPriceBillingService() {
            @Override public void recordEngineExecutionSnapshot(String snapshotJson) { }
        };
        DecisionExecutionPersistence persistence =
                new DecisionExecutionPersistence(logService, billingService, 100, recovery);
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

    @Test
    public void databaseAndRecoveryOutboxFailureFallsBackToLocalJournal() throws Exception {
        Path directory = Files.createTempDirectory("tianshu-journal-fallback-");
        DurableExecutionPersistenceJournal journal = new DurableExecutionPersistenceJournal();
        ReflectionTestUtils.setField(journal, "directory", directory.toString());
        ReflectionTestUtils.setField(journal, "enabled", true);
        journal.initialize();
        RuleExecutionLogService logService = new RuleExecutionLogService() {
            @Override public void saveLogical(RuleExecutionLog entity) {
                throw new IllegalStateException("database unavailable");
            }
        };
        RuleBillingService billingService = new NoPriceBillingService() {
            @Override public void recordEngineExecutionSnapshot(String snapshotJson) {
                throw new IllegalStateException("database unavailable");
            }
        };
        DecisionExecutionPersistence persistence =
                new DecisionExecutionPersistence(logService, billingService, 100);
        ReflectionTestUtils.setField(persistence, "journal", journal);
        persistence.start();
        try {
            persistence.offer(new RuleExecutionLog(), new RuleDefinition(), true, 12L, null, null);
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline && journal.read(10).isEmpty()) Thread.sleep(10);
            assertEquals(1, journal.read(10).size());
            assertTrue(journal.read(10).get(0).logPending());
            assertTrue(journal.read(10).get(0).billingPending());
        } finally {
            persistence.close();
        }
    }
}
