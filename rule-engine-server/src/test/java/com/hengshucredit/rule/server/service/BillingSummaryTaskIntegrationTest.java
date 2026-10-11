package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.mapper.RuleBillingSummaryMapper;
import com.hengshucredit.rule.server.mapper.RuleBillingSummaryTaskMapper;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class BillingSummaryTaskIntegrationTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 11);

    @Test(timeout = 60000)
    public void killedWorkerRollsBackPartialRefreshAndAnotherProcessCanRecover() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("billingSummary.integration"));
        try (BillingSummaryTestDatabase db = new BillingSummaryTestDatabase()) {
            seed(db); previousSummary(db);
            BillingSummaryTaskService service = tasks(db);
            Path output = Files.createTempFile("billing-worker-", ".log");
            Process child = null;
            try {
                String id = service.submit(DATE).getTaskId();
                String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
                String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
                child = new ProcessBuilder(java, "-cp", classpath, BillingSummaryTaskIntegrationTest.class.getName(), db.database)
                        .redirectErrorStream(true).redirectOutput(output.toFile()).start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                while (child.isAlive() && !Files.readString(output).contains("AGGREGATE_WRITTEN") && System.nanoTime() < deadline) Thread.sleep(50);
                assertTrue("子进程必须在汇总写入后、提交前停留: " + Files.readString(output), Files.readString(output).contains("AGGREGATE_WRITTEN"));
                assertEquals("RUNNING", service.get(id).getStatus());
                assertEquals(new BigDecimal("999.000000"), amount(db, "CNY"));
                child.destroyForcibly();
                assertTrue(child.waitFor(5, TimeUnit.SECONDS));
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!"SUCCEEDED".equals(service.get(id).getStatus()) && System.nanoTime() < deadline) {
                    service.processNext(); Thread.sleep(50);
                }
                assertEquals("SUCCEEDED", service.get(id).getStatus());
                assertEquals(Long.valueOf(3), service.get(id).getRecordCount());
                assertEquals(new BigDecimal("2.460000"), amount(db, "CNY"));
                assertEquals(new BigDecimal("2.500000"), amount(db, "USD"));
            } finally {
                if (child != null && child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); }
                Files.deleteIfExists(output); service.close();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].matches("codex_billing_[a-f0-9]{32}")) throw new IllegalArgumentException("Expected owned test database");
        try (BillingSummaryTestDatabase db = new BillingSummaryTestDatabase(args[0], false)) {
            var original = db.sessions.getMapper(RuleBillingSummaryMapper.class);
            ReflectionTestUtils.setField(db.target, "summaryMapper", proxy(original, () -> {
                System.out.println("AGGREGATE_WRITTEN"); System.out.flush();
                new CountDownLatch(1).await(40, TimeUnit.SECONDS);
            }));
            BillingSummaryTaskService service = tasks(db);
            try { service.processNext(); } finally { service.close(); }
        }
    }

    @Test(timeout = 60000)
    public void concurrentSubmissionsCoalesceAndFinishedHistoryRemainsQueryable() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("billingSummary.integration"));
        try (BillingSummaryTestDatabase db = new BillingSummaryTestDatabase()) {
            BillingSummaryTaskService tasks = tasks(db);
            var clients = Executors.newFixedThreadPool(4);
            try {
                seed(db);
                CountDownLatch ready = new CountDownLatch(4), go = new CountDownLatch(1);
                var requests = new ArrayList<java.util.concurrent.Future<String>>();
                for (int i = 0; i < 4; i++) requests.add(clients.submit(() -> {
                    ready.countDown(); assertTrue(go.await(5, TimeUnit.SECONDS));
                    return tasks.submit(DATE).getTaskId();
                }));
                assertTrue(ready.await(5, TimeUnit.SECONDS)); go.countDown();
                var ids = new HashSet<String>();
                for (var request : requests) ids.add(request.get(10, TimeUnit.SECONDS));
                assertEquals(1, ids.size());
                String id = ids.iterator().next();
                assertEquals("PENDING", tasks.get(id).getStatus());
                assertTrue(tasks.processNext());
                assertEquals("SUCCEEDED", tasks.get(id).getStatus());
                assertEquals(Integer.valueOf(2), tasks.get(id).getSummaryCount());
                assertEquals(Long.valueOf(3), tasks.get(id).getRecordCount());
                assertFalse(tasks.processNext());
                String second = tasks.submit(DATE).getTaskId();
                assertNotEquals(id, second);
                assertEquals("SUCCEEDED", tasks.get(id).getStatus());
                assertTrue(tasks.processNext());
                assertEquals(Long.valueOf(3), tasks.get(second).getRecordCount());
                assertEquals(new BigDecimal("2.460000"), amount(db, "CNY"));
            } finally { clients.shutdownNow(); clients.awaitTermination(5, TimeUnit.SECONDS); tasks.close(); }
        }
    }

    @Test(timeout = 60000)
    public void refreshKeepsPreviousSnapshotReadableAndDoesNotBlockNewBillingRecords() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("billingSummary.integration"));
        try (BillingSummaryTestDatabase db = new BillingSummaryTestDatabase()) {
            seed(db); previousSummary(db);
            var original = db.sessions.getMapper(RuleBillingSummaryMapper.class);
            CountDownLatch written = new CountDownLatch(1), commit = new CountDownLatch(1);
            ReflectionTestUtils.setField(db.target, "summaryMapper", proxy(original, () -> {
                written.countDown(); assertTrue(commit.await(10, TimeUnit.SECONDS));
            }));
            BillingSummaryTaskService tasks = tasks(db), anotherNode = tasks(db);
            var clients = Executors.newFixedThreadPool(3);
            try {
                String id = tasks.submit(DATE).getTaskId();
                var refresh = clients.submit(tasks::processNext);
                assertTrue(written.await(8, TimeUnit.SECONDS));
                assertEquals("RUNNING", tasks.get(id).getStatus());
                assertEquals(new BigDecimal("999.000000"), amount(db, "CNY"));
                assertEquals(id, clients.submit(() -> anotherNode.submit(DATE).getTaskId()).get(3, TimeUnit.SECONDS));
                clients.submit(() -> db.jdbc.update("INSERT INTO rule_billing_record (project_id, project_code, billing_code, billing_target, currency, amount, occur_time) "
                        + "VALUES (7,'task_test','CALL','ENGINE','CNY',9,'2026-10-11 13:00:00')")).get(3, TimeUnit.SECONDS);
                assertFalse("其他节点不能重复处理正持锁的任务", anotherNode.processNext());
                commit.countDown(); assertTrue(refresh.get(8, TimeUnit.SECONDS));
                assertEquals("SUCCEEDED", tasks.get(id).getStatus());
                assertEquals(Long.valueOf(3), tasks.get(id).getRecordCount());
                assertEquals(new BigDecimal("2.460000"), amount(db, "CNY"));
                ReflectionTestUtils.setField(db.target, "summaryMapper", original);
                String newer = tasks.submit(DATE).getTaskId();
                assertTrue(tasks.processNext());
                assertEquals(Long.valueOf(4), tasks.get(newer).getRecordCount());
                assertEquals(new BigDecimal("11.460000"), amount(db, "CNY"));
            } finally {
                commit.countDown(); clients.shutdownNow(); clients.awaitTermination(5, TimeUnit.SECONDS);
                tasks.close(); anotherNode.close();
            }
        }
    }

    @Test(timeout = 60000)
    public void recoversUnownedRunningTaskAndFailureRollsBackBeforeExplicitRetry() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("billingSummary.integration"));
        try (BillingSummaryTestDatabase db = new BillingSummaryTestDatabase()) {
            seed(db); previousSummary(db);
            BillingSummaryTaskService firstNode = tasks(db), recoveredNode = tasks(db);
            try {
                String id = firstNode.submit(DATE).getTaskId();
                db.jdbc.update("UPDATE rule_billing_summary_task SET status='RUNNING',started_at=NOW() WHERE task_id=?", id);
                firstNode.close();
                assertTrue(recoveredNode.processNext());
                assertEquals("SUCCEEDED", recoveredNode.get(id).getStatus());
                String failing = recoveredNode.submit(DATE).getTaskId();
                var original = db.sessions.getMapper(RuleBillingSummaryMapper.class);
                db.jdbc.update("INSERT INTO rule_billing_record (project_id, project_code, billing_code, billing_target, currency, amount, occur_time) "
                        + "VALUES (7,'task_test','CALL','ENGINE','CNY',9,'2026-10-11 13:00:00')");
                ReflectionTestUtils.setField(db.target, "summaryMapper", proxy(original, () -> { throw new IllegalStateException("Injected after aggregate write"); }));
                assertTrue(recoveredNode.processNext());
                assertEquals("FAILED", recoveredNode.get(failing).getStatus());
                assertNotNull(recoveredNode.get(failing).getErrorMessage());
                assertEquals(new BigDecimal("2.460000"), amount(db, "CNY"));
                assertFalse(recoveredNode.processNext());
                ReflectionTestUtils.setField(db.target, "summaryMapper", original);
                String retried = recoveredNode.submit(DATE).getTaskId();
                assertNotEquals(failing, retried);
                assertTrue(recoveredNode.processNext());
                assertEquals("SUCCEEDED", recoveredNode.get(retried).getStatus());
                assertEquals("FAILED", recoveredNode.get(failing).getStatus());
                assertEquals(new BigDecimal("11.460000"), amount(db, "CNY"));
            } finally { firstNode.close(); recoveredNode.close(); }
        }
    }

    private static BillingSummaryTaskService tasks(BillingSummaryTestDatabase db) {
        BillingSummaryTaskService service = new BillingSummaryTaskService() { @Override public void schedule() { } };
        ReflectionTestUtils.setField(service, "tasks", db.sessions.getMapper(RuleBillingSummaryTaskMapper.class));
        ReflectionTestUtils.setField(service, "billing", db.target);
        ReflectionTestUtils.setField(service, "operator", new ConsoleOperatorResolver());
        ReflectionTestUtils.setField(service, "transactionManager", new DataSourceTransactionManager(db.source));
        return service;
    }

    private interface AfterWrite { void run() throws Exception; }
    private static RuleBillingSummaryMapper proxy(RuleBillingSummaryMapper original, AfterWrite callback) {
        return (RuleBillingSummaryMapper) Proxy.newProxyInstance(RuleBillingSummaryMapper.class.getClassLoader(),
                new Class<?>[]{RuleBillingSummaryMapper.class}, (p, method, args) -> {
                    try {
                        Object result = method.invoke(original, args);
                        if ("aggregateDate".equals(method.getName())) callback.run();
                        return result;
                    } catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
    }

    private static void seed(BillingSummaryTestDatabase db) {
        db.jdbc.update("INSERT INTO rule_billing_record (project_id,project_code,billing_code,billing_target,currency,amount,occur_time,token_id) VALUES "
                + "(7,'task_test','CALL','ENGINE','CNY',1.23,'2026-10-11 12:00:00',1),"
                + "(7,'task_test','CALL','ENGINE','CNY',1.23,'2026-10-11 12:00:00',2),"
                + "(7,'task_test','CALL','ENGINE','USD',2.50,'2026-10-11 12:00:00',1)");
    }

    private static void previousSummary(BillingSummaryTestDatabase db) {
        db.jdbc.update("INSERT INTO rule_billing_summary (summary_date,project_id,project_code,billing_code,billing_target,currency,total_amount) "
                + "VALUES ('2026-10-11',7,'task_test','CALL','ENGINE','CNY',999)");
    }

    private static BigDecimal amount(BillingSummaryTestDatabase db, String currency) {
        return db.jdbc.queryForObject("SELECT total_amount FROM rule_billing_summary WHERE currency=?", BigDecimal.class, currency);
    }
}
