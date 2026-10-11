package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleExecutionPersistenceOutbox;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.mapper.RuleExecutionPersistenceOutboxMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class DecisionExecutionDurabilityTest {
    @org.junit.Rule public org.junit.rules.TemporaryFolder journalFolder = new org.junit.rules.TemporaryFolder();

    @Test
    public void billingSnapshotSurvivesJournalRestartAndRecovery() throws Exception {
        SnapshotBilling billing = new SnapshotBilling();
        java.nio.file.Path directory = journalFolder.newFolder().toPath();
        RuleExecutionLogService logs = new RuleExecutionLogService();
        DurableExecutionPersistenceJournal journal = journal(directory);
        try (DecisionExecutionPersistence original = new DecisionExecutionPersistence(logs, billing, 100)) {
            ReflectionTestUtils.setField(original, "journal", journal);
            original.offer(null, billingDefinition(), true, 250L, null, null);
            assertNotNull(journal.read(1).get(0).billingSnapshotJson());
        } finally { journal.close(); }
        billing.enabled = false;
        DurableExecutionPersistenceJournal restartedJournal = journal(directory);
        try (DecisionExecutionPersistence restarted = new DecisionExecutionPersistence(logs, billing, 100)) {
            ReflectionTestUtils.setField(restarted, "journal", restartedJournal);
            restarted.replayJournal();
            restarted.replayJournal();
            assertEquals(1, billing.records.size());
            assertEquals(new java.math.BigDecimal("0.250000"), billing.records.get(0).getAmount());
            assertTrue(restartedJournal.read(1).isEmpty());
        } finally { restartedJournal.close(); }
    }

    private static DurableExecutionPersistenceJournal journal(java.nio.file.Path directory) {
        DurableExecutionPersistenceJournal journal = new DurableExecutionPersistenceJournal();
        ReflectionTestUtils.setField(journal, "directory", directory.toString());
        ReflectionTestUtils.setField(journal, "enabled", true);
        journal.initialize();
        return journal;
    }

    @Test
    public void delayedBillingUsesThePriceAndOccurrenceAtAcceptance() {
        SnapshotBilling billing = new SnapshotBilling();
        List<RuleExecutionPersistenceOutbox> stored = new ArrayList<>();
        RuleExecutionLogService logs = new RuleExecutionLogService();
        RuleDefinition definition = billingDefinition();
        try (DecisionExecutionPersistence original = new DecisionExecutionPersistence(
                logs, billing, 100, outbox(stored, logs, billing, false))) {
            original.offer(null, definition, true, 250L, null, null);
            java.time.LocalDateTime acceptedAt = java.time.LocalDateTime.now();
            billing.config.setUnitPrice(new java.math.BigDecimal("9.00"));
            billing.config.setBillingCode("RENAMED_LATER");
            outbox(stored, logs, billing, false).replayPending();

            assertEquals(1, billing.records.size());
            var record = billing.records.get(0);
            assertEquals(new java.math.BigDecimal("0.250000"), record.getAmount());
            assertEquals("ORIGINAL_PRICE", record.getBillingCode());
            assertEquals(Long.valueOf(31L), record.getBillingConfigId());
            assertEquals("EXECUTION_SNAPSHOT", record.getBillingBasis());
            assertFalse("恢复时间不能替代执行发生时间", record.getOccurTime().isAfter(acceptedAt));
            assertEquals("DELIVERED", stored.get(0).getDeliveryStatus());
            assertNull(stored.get(0).getBillingSnapshotJson());
        }
    }

    @Test
    public void disablingPriceAfterAcceptanceDoesNotEraseTheOriginalCharge() {
        SnapshotBilling billing = new SnapshotBilling();
        List<RuleExecutionPersistenceOutbox> stored = new ArrayList<>();
        RuleExecutionLogService logs = new RuleExecutionLogService();
        try (DecisionExecutionPersistence original = new DecisionExecutionPersistence(
                logs, billing, 100, outbox(stored, logs, billing, false))) {
            original.offer(null, billingDefinition(), true, 250L, null, null);
            billing.enabled = false;
            outbox(stored, logs, billing, false).replayPending();
            assertEquals("执行时适用的价格必须保留", 1, billing.records.size());
        }
    }

    @Test
    public void enablingPriceAfterAcceptanceDoesNotChargeAnOriginallyFreeExecution() {
        SnapshotBilling billing = new SnapshotBilling();
        billing.enabled = false;
        List<RuleExecutionPersistenceOutbox> stored = new ArrayList<>();
        RuleExecutionLogService logs = new RuleExecutionLogService();
        try (DecisionExecutionPersistence original = new DecisionExecutionPersistence(
                logs, billing, 100, outbox(stored, logs, billing, false))) {
            original.offer(null, billingDefinition(), true, 250L, null, null);
            billing.enabled = true;
            outbox(stored, logs, billing, false).replayPending();
            assertTrue("已确认没有计费项与尚未读取计费项必须区分", billing.records.isEmpty());
            assertEquals("DELIVERED", stored.get(0).getDeliveryStatus());
        }
    }

    @Test
    public void unavailablePriceAtAcceptanceCannotSilentlyUseARecoveryTimePrice() {
        SnapshotBilling billing = new SnapshotBilling();
        billing.unavailable = true;
        List<RuleExecutionPersistenceOutbox> stored = new ArrayList<>();
        RuleExecutionLogService logs = new RuleExecutionLogService();
        try (DecisionExecutionPersistence original = new DecisionExecutionPersistence(
                logs, billing, 100, outbox(stored, logs, billing, false))) {
            original.offer(null, billingDefinition(), true, 250L, null, null);
            billing.unavailable = false;
            outbox(stored, logs, billing, false).replayPending();
            assertTrue("读取失败不能转换成新价格或零收费", billing.records.isEmpty());
            assertEquals(Integer.valueOf(1), stored.get(0).getBillingPending());
            assertEquals("PENDING", stored.get(0).getDeliveryStatus());
            assertNotNull(stored.get(0).getLastError());
        }
    }

    private static RuleDefinition billingDefinition() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(12L);
        definition.setProjectId(7L);
        definition.setRuleCode("snapshot-rule");
        definition.setExecutionTraceId("snapshot-trace");
        definition.setExecutionAttemptNo(1);
        return definition;
    }

    private static class SnapshotBilling extends RuleBillingService {
        private final com.hengshucredit.rule.model.entity.RuleBillingConfig config =
                new com.hengshucredit.rule.model.entity.RuleBillingConfig();
        private final List<com.hengshucredit.rule.model.entity.RuleBillingRecord> records = new ArrayList<>();
        private boolean enabled = true;
        private boolean unavailable;

        private SnapshotBilling() {
            config.setId(31L);
            config.setBillingCode("ORIGINAL_PRICE");
            config.setBillingName("按执行时长计费");
            config.setUnitPrice(java.math.BigDecimal.ONE);
            config.setChargeType("DURATION");
        }

        @Override protected List<com.hengshucredit.rule.model.entity.RuleBillingConfig> findActiveEngineConfigs(
                RuleDefinition definition, java.time.LocalDateTime now, Long projectId) {
            if (unavailable) throw new IllegalStateException("price store unavailable");
            return enabled ? List.of(config) : List.of();
        }
        @Override protected com.hengshucredit.rule.model.entity.RuleProject findProject(Long id) { return null; }
        @Override protected boolean hasLegacyEngineRecord(String trace) {
            return records.stream().anyMatch(record -> trace.equals(record.getRootTraceId())
                    && "ENGINE".equals(record.getBillingTarget()) && record.getBillingConfigId() == null);
        }
        @Override protected void insertRecord(com.hengshucredit.rule.model.entity.RuleBillingRecord record) {
            records.add(record);
        }
    }

    @Test
    public void acceptedEventCanRecoverWithoutStartingTheOriginalWorker() {
        List<RuleExecutionPersistenceOutbox> stored = new ArrayList<>();
        AtomicInteger logs = new AtomicInteger();
        AtomicInteger bills = new AtomicInteger();
        RuleExecutionLogService logService = new RuleExecutionLogService() {
            @Override public void saveLogical(RuleExecutionLog row) { logs.incrementAndGet(); }
        };
        RuleBillingService billing = billing(bills, false);
        RuleExecutionPersistenceOutboxService outbox = outbox(stored, logService, billing, false);
        DecisionExecutionPersistence original = new DecisionExecutionPersistence(logService, billing, 100, outbox);
        RuleExecutionLog log = new RuleExecutionLog();
        log.setTraceId("accepted-before-worker");
        RuleDefinition definition = new RuleDefinition();
        definition.setId(12L);
        definition.setRuleCode("durable-rule");
        original.offer(log, definition, true, 7L, null, null);

        assertEquals("成功接受必须先有可恢复记录", 1, stored.size());
        assertEquals(0, logs.get());
        assertEquals(0, bills.get());
        assertEquals("PENDING", stored.get(0).getDeliveryStatus());
        log.setTraceId("mutated-after-acceptance");
        assertTrue(stored.get(0).getLogJson().contains("accepted-before-worker"));
        assertFalse(stored.get(0).getLogJson().contains("mutated-after-acceptance"));

        RuleExecutionPersistenceOutboxService restarted = outbox(stored, logService, billing, false);
        restarted.replayPending();
        restarted.replayPending();
        assertEquals(1, logs.get());
        assertEquals(1, bills.get());
        assertEquals("DELIVERED", stored.get(0).getDeliveryStatus());
        assertNull(stored.get(0).getLogJson());
        assertNull(stored.get(0).getDefinitionJson());
        original.close();
    }

    @Test
    public void noRecoveryStoreFallsBackToConfirmedWritesBeforeReturning() {
        AtomicInteger logs = new AtomicInteger();
        AtomicInteger bills = new AtomicInteger();
        DecisionExecutionPersistence persistence = new DecisionExecutionPersistence(new RuleExecutionLogService() {
            @Override public void saveLogical(RuleExecutionLog row) { logs.incrementAndGet(); }
        }, billing(bills, false), 100);
        persistence.offer(new RuleExecutionLog(), new RuleDefinition(), true, 7L, null, null);
        assertEquals(1, logs.get());
        assertEquals(1, bills.get());
        assertEquals(0, persistence.snapshot().get("queueDepth"));
        persistence.close();
    }

    @Test
    public void unavailableStoresCannotAcknowledgeAnInMemoryOnlyEvent() {
        RuleExecutionLogService logs = new RuleExecutionLogService() {
            @Override public void saveLogical(RuleExecutionLog row) { throw new IllegalStateException("database down"); }
        };
        RuleBillingService bills = billing(new AtomicInteger(), true);
        DecisionExecutionPersistence persistence = new DecisionExecutionPersistence(logs, bills, 100,
                outbox(new ArrayList<>(), logs, bills, true));
        assertThrows(IllegalStateException.class, () ->
                persistence.offer(new RuleExecutionLog(), new RuleDefinition(), true, 7L, null, null));
        assertEquals(0, persistence.snapshot().get("queueDepth"));
        persistence.close();
    }

    @Test
    public void asynchronousDeliveryFailureReportsTheFailedSideWithoutSynchronousFallback() throws Exception {
        List<RuleExecutionPersistenceOutbox> stored = new java.util.concurrent.CopyOnWriteArrayList<>();
        RuleExecutionLogService logs = new RuleExecutionLogService() {
            @Override public void saveLogical(RuleExecutionLog row) { throw new IllegalStateException("log down"); }
        };
        RuleBillingService bills = billing(new AtomicInteger(), false);
        DecisionExecutionPersistence persistence = new DecisionExecutionPersistence(logs, bills, 100,
                outbox(stored, logs, bills, false));
        persistence.start();
        try {
            persistence.offer(new RuleExecutionLog(), new RuleDefinition(), true, 7L, null, null);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while ((long) persistence.snapshot().get("failed") == 0 && System.nanoTime() < deadline) Thread.sleep(5);
            assertEquals(1L, persistence.snapshot().get("failed"));
            assertEquals(1L, persistence.snapshot().get("logFailed"));
            assertEquals(0L, persistence.snapshot().get("billingFailed"));
            assertEquals(0L, persistence.snapshot().get("fallback"));
            assertEquals(1L, persistence.snapshot().get("durablyAccepted"));
        } finally { persistence.close(); }
    }

    @Test
    public void fullNotificationQueueDefersDurableDeliveryWithoutCountingASynchronousWrite() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        RuleExecutionPersistenceOutboxService outbox = new RuleExecutionPersistenceOutboxService() {
            @Override public RuleExecutionPersistenceOutbox accept(DurableExecutionPersistenceJournal.Entry entry) {
                RuleExecutionPersistenceOutbox row = new RuleExecutionPersistenceOutbox();
                row.setId((long) accepted.incrementAndGet());
                return row;
            }
            @Override public RuleExecutionPersistenceOutbox deliver(Long id) {
                entered.countDown();
                try { release.await(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                return null;
            }
        };
        DecisionExecutionPersistence persistence = new DecisionExecutionPersistence(
                new RuleExecutionLogService(), new RuleBillingService(), 100, outbox);
        persistence.start();
        try {
            persistence.offer(new RuleExecutionLog(), null, true, 7L, null, null);
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            for (int i = 0; i < 101; i++) persistence.offer(new RuleExecutionLog(), null, true, 7L, null, null);
            assertEquals(102, accepted.get());
            assertEquals(100, persistence.snapshot().get("queueDepth"));
            assertEquals(0L, persistence.snapshot().get("fallback"));
            assertEquals(1L, persistence.snapshot().get("outboxDeferred"));
        } finally {
            release.countDown();
            persistence.close();
        }
    }

    @Test
    public void databaseOnlyBacklogProgressesWhileNotificationsRemainQueued() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var replayed = new java.util.concurrent.CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger notificationDeliveries = new AtomicInteger();
        AtomicInteger deliveriesBeforeReplay = new AtomicInteger(-1);
        RuleExecutionPersistenceOutboxService outbox = new RuleExecutionPersistenceOutboxService() {
            @Override public RuleExecutionPersistenceOutbox accept(DurableExecutionPersistenceJournal.Entry entry) {
                RuleExecutionPersistenceOutbox row = new RuleExecutionPersistenceOutbox();
                row.setId((long) accepted.incrementAndGet());
                return row;
            }
            @Override public List<RuleExecutionPersistenceOutbox> deliverBatch(List<Long> ids) {
                if (notificationDeliveries.get() == 0) {
                    entered.countDown();
                    try { assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
                    catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(failure);
                    }
                }
                notificationDeliveries.addAndGet(ids.size());
                return ids.stream().map(id -> {
                    RuleExecutionPersistenceOutbox row = new RuleExecutionPersistenceOutbox();
                    row.setId(id);
                    row.setDeliveryStatus("DELIVERED");
                    return row;
                }).toList();
            }
            @Override public List<RuleExecutionPersistenceOutbox> replayBatch() {
                if (accepted.get() == 102 && deliveriesBeforeReplay.compareAndSet(-1, notificationDeliveries.get())) {
                    replayed.countDown();
                }
                return List.of();
            }
        };
        DecisionExecutionPersistence persistence = new DecisionExecutionPersistence(
                new RuleExecutionLogService(), new RuleBillingService(), 100, outbox);
        persistence.start();
        try {
            persistence.offer(new RuleExecutionLog(), null, true, 7L, null, null);
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            for (int i = 0; i < 101; i++) persistence.offer(new RuleExecutionLog(), null, true, 7L, null, null);
            assertEquals(1L, persistence.snapshot().get("outboxDeferred"));
            release.countDown();
            assertTrue(replayed.await(3, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue("数据库恢复不能等待所有101条新通知消费完才获得机会",
                    deliveriesBeforeReplay.get() < 101);
        } finally {
            release.countDown();
            persistence.close();
        }
    }

    private static RuleBillingService billing(AtomicInteger calls, boolean fail) {
        return new NoPriceBillingService() {
            @Override public void recordEngineExecutionSnapshot(String snapshotJson) {
                calls.incrementAndGet();
                if (fail) throw new IllegalStateException("billing down");
            }
        };
    }

    private static RuleExecutionPersistenceOutboxService outbox(List<RuleExecutionPersistenceOutbox> stored,
            RuleExecutionLogService logs, RuleBillingService bills, boolean unavailable) {
        RuleExecutionPersistenceOutboxMapper mapper = (RuleExecutionPersistenceOutboxMapper) Proxy.newProxyInstance(
                RuleExecutionPersistenceOutboxMapper.class.getClassLoader(),
                new Class<?>[]{RuleExecutionPersistenceOutboxMapper.class}, (proxy, method, args) -> {
                    if (unavailable) throw new IllegalStateException("outbox unavailable");
                    if ("insert".equals(method.getName())) {
                        RuleExecutionPersistenceOutbox row = (RuleExecutionPersistenceOutbox) args[0];
                        row.setId((long) stored.size() + 1);
                        stored.add(copy(row));
                        return 1;
                    }
                    if ("selectList".equals(method.getName())) {
                        return stored.stream().filter(row -> "PENDING".equals(row.getDeliveryStatus())).map(DecisionExecutionDurabilityTest::copy).toList();
                    }
                    if ("claimPending".equals(method.getName())) {
                        RuleExecutionPersistenceOutbox row = stored.stream().filter(item -> item.getId().equals(args[0])).findFirst().orElseThrow();
                        if (!"PENDING".equals(row.getDeliveryStatus())) return 0;
                        row.setDeliveryStatus("PROCESSING");
                        row.setClaimToken((String) args[1]);
                        return 1;
                    }
                    if ("selectClaimed".equals(method.getName())) {
                        return stored.stream().filter(row -> row.getId().equals(args[0])
                                && "PROCESSING".equals(row.getDeliveryStatus()) && args[1].equals(row.getClaimToken()))
                                .map(DecisionExecutionDurabilityTest::copy).findFirst().orElse(null);
                    }
                    if ("updateClaimed".equals(method.getName())) {
                        RuleExecutionPersistenceOutbox change = (RuleExecutionPersistenceOutbox) args[0];
                        for (int i = 0; i < stored.size(); i++) {
                            RuleExecutionPersistenceOutbox row = stored.get(i);
                            if (row.getId().equals(change.getId()) && "PROCESSING".equals(row.getDeliveryStatus())
                                    && change.getClaimToken().equals(row.getClaimToken())) {
                                RuleExecutionPersistenceOutbox updated = copy(change);
                                updated.setClaimToken(null);
                                stored.set(i, updated);
                                return 1;
                            }
                        }
                        return 0;
                    }
                    if (method.getReturnType().isPrimitive()) return method.getReturnType() == boolean.class ? false : 0;
                    return null;
                });
        RuleExecutionPersistenceOutboxService service = new RuleExecutionPersistenceOutboxService();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "logService", logs);
        ReflectionTestUtils.setField(service, "billingService", bills);
        return service;
    }

    private static RuleExecutionPersistenceOutbox copy(RuleExecutionPersistenceOutbox row) {
        return com.alibaba.fastjson.JSON.parseObject(com.alibaba.fastjson.JSON.toJSONString(row), RuleExecutionPersistenceOutbox.class);
    }
}
