package com.hengshucredit.rule.server.service;

import org.junit.Test;
import org.junit.After;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DurableExecutionPersistenceJournalTest {
    private DurableExecutionPersistenceJournal journal;

    @After
    public void closeJournal() {
        if (journal != null) journal.close();
    }

    @Test
    public void appendReadAndReplaceAreDurableAndPreservePendingSides() throws Exception {
        Path directory = Files.createTempDirectory("tianshu-journal-");
        journal = new DurableExecutionPersistenceJournal();
        ReflectionTestUtils.setField(journal, "directory", directory.toString());
        ReflectionTestUtils.setField(journal, "enabled", true);
        ReflectionTestUtils.setField(journal, "maxEntryBytes", 1024 * 1024L);
        journal.initialize();

        DurableExecutionPersistenceJournal.Entry entry = new DurableExecutionPersistenceJournal.Entry(
                "event-1", "{\"traceId\":\"t1\"}", "{\"id\":1}", true,
                12L, null, 3L, "P", 4L, "A", "TOKEN", 5L, "T", "OPEN",
                true, false);
        assertTrue(journal.append(entry));
        assertEquals(List.of(entry), journal.read(10));

        journal.replace(List.of(entry));
        assertEquals(List.of(entry), journal.read(10));
        journal.replace(List.of());
        assertEquals(List.of(), journal.read(10));
    }

    @Test
    public void replacesOnlyProcessedPrefixAndKeepsTail() throws Exception {
        Path directory = Files.createTempDirectory("tianshu-journal-prefix-");
        journal = new DurableExecutionPersistenceJournal();
        ReflectionTestUtils.setField(journal, "directory", directory.toString());
        ReflectionTestUtils.setField(journal, "enabled", true);
        ReflectionTestUtils.setField(journal, "maxEntryBytes", 1024 * 1024L);
        journal.initialize();
        DurableExecutionPersistenceJournal.Entry first = entry("first");
        DurableExecutionPersistenceJournal.Entry second = entry("second");
        DurableExecutionPersistenceJournal.Entry third = entry("third");
        DurableExecutionPersistenceJournal.Entry retry = entry("retry");
        journal.append(first);
        journal.append(second);
        journal.append(third);

        assertEquals(List.of(first, second), journal.read(2));
        journal.replacePrefix(2, List.of(retry));

        assertEquals(List.of(retry, third), journal.read(10));
    }

    private DurableExecutionPersistenceJournal.Entry entry(String id) {
        return new DurableExecutionPersistenceJournal.Entry(id, "{}", "{}", true,
                12L, null, 3L, "P", 4L, "A", "TOKEN", 5L, "T", "OPEN", true, false);
    }

    @Test
    public void partialReplayRetainsEventIdentityAndOnlyTheFailedSide() throws Exception {
        Path directory = Files.createTempDirectory("tianshu-journal-retry-");
        journal = new DurableExecutionPersistenceJournal();
        ReflectionTestUtils.setField(journal, "directory", directory.toString());
        journal.initialize();
        journal.append(new DurableExecutionPersistenceJournal.Entry("stable-event", "{}", "{}", true,
                12L, null, 3L, "P", 4L, "A", "TOKEN", 5L, "T", "OPEN", true, true));
        RuleExecutionLogService logs = new RuleExecutionLogService() {
            @Override public void saveLogical(com.hengshucredit.rule.model.entity.RuleExecutionLog row) { }
        };
        RuleBillingService billing = new RuleBillingService() {
            @Override public void recordLegacyEngineExecution(com.hengshucredit.rule.model.entity.RuleDefinition definition,
                    boolean success, Long cost, String error,
                    com.hengshucredit.rule.server.auth.ProjectAuthContext auth) {
                throw new IllegalStateException("billing unavailable");
            }
        };
        DecisionExecutionPersistence persistence = new DecisionExecutionPersistence(logs, billing, 100);
        ReflectionTestUtils.setField(persistence, "journal", journal);
        persistence.replayJournal();
        List<DurableExecutionPersistenceJournal.Entry> remaining = journal.read(10);
        assertEquals(1, remaining.size());
        assertEquals("stable-event", remaining.get(0).eventId());
        org.junit.Assert.assertFalse(remaining.get(0).logPending());
        assertTrue(remaining.get(0).billingPending());
        persistence.close();
    }

    @Test
    public void unavailableOutboxDoesNotHoldJournalLockOrRetryEveryEntry() throws Exception {
        Path directory = Files.createTempDirectory("tianshu-journal-concurrent-");
        journal = new DurableExecutionPersistenceJournal();
        ReflectionTestUtils.setField(journal, "directory", directory.toString());
        journal.initialize();
        journal.append(entry("first"));
        journal.append(entry("second"));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        RuleExecutionPersistenceOutboxService outbox = new RuleExecutionPersistenceOutboxService() {
            @Override public com.hengshucredit.rule.model.entity.RuleExecutionPersistenceOutbox accept(
                    DurableExecutionPersistenceJournal.Entry value) {
                attempts.incrementAndGet();
                entered.countDown();
                try { release.await(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                throw new IllegalStateException("outbox down");
            }
        };
        DecisionExecutionPersistence persistence = new DecisionExecutionPersistence(
                new RuleExecutionLogService(), new RuleBillingService(), 100, outbox);
        ReflectionTestUtils.setField(persistence, "journal", journal);
        var tasks = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var replay = tasks.submit(persistence::replayJournal);
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(tasks.submit(() -> journal.append(entry("new-request")))
                    .get(2, java.util.concurrent.TimeUnit.SECONDS));
            release.countDown();
            replay.get(2, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1, attempts.get());
            assertEquals(List.of("first", "second", "new-request"),
                    journal.read(10).stream().map(DurableExecutionPersistenceJournal.Entry::eventId).toList());
        } finally {
            release.countDown();
            tasks.shutdownNow();
            persistence.close();
        }
    }
}
