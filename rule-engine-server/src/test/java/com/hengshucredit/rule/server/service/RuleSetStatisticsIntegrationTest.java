package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.server.mapper.RuleExecutionLogMapper;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import static org.junit.Assert.*;

public class RuleSetStatisticsIntegrationTest {
    @Test(timeout = 60000)
    public void historicalBatchRollsBackAndRetriesWithoutChangingOriginalTraces() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("ruleSetStatistics.integration"));
        try (BillingSummaryTestDatabase db = new BillingSummaryTestDatabase()) {
            createLogTables(db);
            for (int index = 0; index < 12; index++) db.jdbc.update("INSERT INTO rule_execution_log(trace_id,rule_code,trace_info,create_time) VALUES (?,?,?,'2026-10-11 12:00:00')",
                    "backfill-" + index, "root", index == 0 ? "{broken" : frame(1,"SET",10,true,false));
            db.jdbc.update("UPDATE rule_execution_log SET rule_set_stats_version=1,rule_set_stats_status='READY' WHERE trace_id='backfill-1'");
            db.jdbc.update("INSERT INTO rule_execution_statistics (id,create_time,trace_id,rule_set_stats_json,rule_set_stats_status,format_version) "
                    + "SELECT id,create_time,trace_id,'[]','READY',1 FROM rule_execution_log WHERE trace_id='backfill-1'");
            JdbcTemplate failing = new JdbcTemplate(db.source) {
                private int writes;
                @Override public int update(String sql, Object... args) {
                    int result = super.update(sql, args);
                    if (++writes == 2) throw new IllegalStateException("Injected batch rollback");
                    return result;
                }
            };
            RuleSetStatisticsBackfill first = new RuleSetStatisticsBackfill(failing, new DataSourceTransactionManager(db.source));
            RuleSetStatisticsBackfill retry = new RuleSetStatisticsBackfill(db.jdbc, new DataSourceTransactionManager(db.source));
            try {
                assertThrows(IllegalStateException.class, first::processBatch);
                assertEquals(Long.valueOf(12), db.jdbc.queryForObject("SELECT COUNT(*) FROM rule_execution_log WHERE rule_set_stats_version IS NULL OR rule_set_stats_version<2", Long.class));
                assertEquals(Long.valueOf(1), db.jdbc.queryForObject("SELECT COUNT(*) FROM rule_execution_statistics WHERE format_version=1", Long.class));
                assertEquals(Long.valueOf(0), db.jdbc.queryForObject("SELECT COUNT(*) FROM rule_set_statistics_event", Long.class));
                assertEquals(10, retry.processBatch());
                assertEquals(2, retry.processBatch());
                assertEquals(0, retry.processBatch());
                assertEquals(Long.valueOf(1), db.jdbc.queryForObject("SELECT COUNT(*) FROM rule_execution_log WHERE rule_set_stats_status='INVALID'", Long.class));
                assertEquals("{broken", db.jdbc.queryForObject("SELECT trace_info FROM rule_execution_log WHERE trace_id='backfill-0'", String.class));
                assertEquals(Long.valueOf(11), db.jdbc.queryForObject("SELECT COUNT(*) FROM rule_execution_log WHERE rule_set_stats_status='READY'", Long.class));
                assertEquals(Long.valueOf(12), db.jdbc.queryForObject("SELECT COUNT(*) FROM rule_execution_statistics WHERE format_version=2", Long.class));
                assertEquals(Long.valueOf(22), db.jdbc.queryForObject("SELECT COUNT(*) FROM rule_set_statistics_event", Long.class));
            } finally { first.close(); retry.close(); }
        }
    }

    @Test(timeout = 60000)
    public void originalLogAndStatisticsCommitTogetherAndRetryOnlyCountsOnce() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("ruleSetStatistics.integration"));
        try (BillingSummaryTestDatabase db = new BillingSummaryTestDatabase()) {
            createLogTables(db);
            RuleExecutionLogService logs = logs(db);
            RuleExecutionLog record = log("atomic", frame(1,"SET",12,true,false));
            RuleExecutionLogService target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(logs);
            RuleSetStatisticsStore failing = new RuleSetStatisticsStore(db.jdbc) {
                @Override void persist(long id, LocalDateTime time, String json, String status) {
                    super.persist(id,time,json,status); throw new IllegalStateException("Injected compact statistics failure");
                }
            };
            ReflectionTestUtils.setField(target,"statisticsStore",failing);
            assertThrows(IllegalStateException.class, () -> logs.saveLogical(record));
            for (String table : List.of("rule_execution_log","rule_execution_statistics","rule_execution_log_identity")) {
                assertEquals(table,Long.valueOf(0),db.jdbc.queryForObject("SELECT COUNT(*) FROM " + table,Long.class));
            }
            ReflectionTestUtils.setField(target,"statisticsStore",new RuleSetStatisticsStore(db.jdbc));
            logs.saveLogical(record); logs.saveLogical(record);
            assertEquals(1L,((Map<?,?>)logs.ruleSetStats(null,null,null,null,null).get("overview")).get("evaluationCount"));
        }
    }

    @Test(timeout = 60000)
    public void coverageOverviewAndItemCountsShareOneReadSnapshotDuringNewAttempt() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("ruleSetStatistics.integration"));
        try (BillingSummaryTestDatabase db = new BillingSummaryTestDatabase()) {
            createLogTables(db);
            RuleExecutionLogService logs = logs(db);
            RuleExecutionLog record = log("snapshot", frame(1,"SET",10,false,true)); logs.saveLogical(record);
            CountDownLatch covered = new CountDownLatch(1), updated = new CountDownLatch(1);
            JdbcTemplate observed = new JdbcTemplate(db.source) {
                @Override public void query(String sql, RowCallbackHandler handler, Object... args) {
                    super.query(sql, handler, args);
                    if (sql.startsWith("SELECT COUNT(*) AS amount FROM rule_execution_log_current")) {
                        covered.countDown();
                        try { assertTrue(updated.await(10, TimeUnit.SECONDS)); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                    }
                }
            };
            var worker = Executors.newSingleThreadExecutor();
            try {
                var change = worker.submit(() -> {
                    try { assertTrue(covered.await(10, TimeUnit.SECONDS)); record.setAttemptNo(2); record.setTraceInfo(frame(1,"SET",999,true,false)); logs.saveLogical(record); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                    finally { updated.countDown(); }
                });
                RuleSetStatisticsQuery query = transactionalQuery(db, observed);
                Map<String, Object> result = query.query(null,null,null,null,null);
                assertEquals(0L, ((Map<?, ?>)result.get("overview")).get("hitCount"));
                Map<?, ?> set = (Map<?, ?>)((List<?>)result.get("ruleSets")).get(0);
                assertEquals(0L, set.get("hitCount")); assertEquals(10L, set.get("p95CostTimeMs"));
                assertEquals(0L, ((Map<?, ?>)((List<?>)set.get("items")).get(0)).get("hitCount"));
                change.get(5,TimeUnit.SECONDS);
                assertEquals(1L, ((Map<?, ?>)logs.ruleSetStats(null,null,null,null,null).get("overview")).get("hitCount"));
            } finally { updated.countDown(); worker.shutdownNow(); worker.awaitTermination(5,TimeUnit.SECONDS); }
        }
    }
    @Test(timeout = 60000)
    public void projectsNestedFramesDeduplicatesAttemptsAndReportsTraceCoverage() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("ruleSetStatistics.integration"));
        try (BillingSummaryTestDatabase db = new BillingSummaryTestDatabase()) {
            createLogTables(db);
            RuleExecutionLogService logs = logs(db);
            RuleExecutionLog first = log("stats-first", """
              [{"modelType":"FLOW","children":[{"ruleId":10,"ruleCode":"RS-A","ruleName":"准入规则集","modelType":"RULE_SET","status":"SUCCESS","durationMs":100,
                "events":[{"type":"RULE_SET_ITEM","ruleCode":"A-1","ruleName":"年龄准入","evaluated":true,"hit":true},
                          {"type":"RULE_SET_ITEM","ruleCode":"A-2","ruleName":"评分准入","evaluated":true,"hit":false},
                          {"type":"RULE_SET_SUMMARY","evaluated":true,"hit":true}],
                "children":[{"ruleId":11,"ruleCode":"RS-B","ruleName":"子规则集","modelType":"RULE_SET","status":"SUCCESS","durationMs":50,
                 "events":[{"type":"RULE_SET_ITEM","ruleCode":"B-1","evaluated":true,"hit":false},
                           {"type":"RULE_SET_SUMMARY","evaluated":true,"hit":false}]}]}]}]
              """);
            RuleExecutionLog second = log("stats-second", frame(10, "RS-A", 200, false, true));
            logs.saveLogical(first); logs.saveLogical(second); logs.saveLogical(second);
            Map<String, Object> result = logs.ruleSetStats("stats-project", null, null, null, null);
            Map<?, ?> overview = (Map<?, ?>)result.get("overview");
            List<?> sets = (List<?>)result.get("ruleSets");
            assertEquals(3L, overview.get("evaluationCount"));
            assertEquals(1L, overview.get("hitCount"));
            assertEquals(1L, overview.get("failureCount"));
            assertEquals(2, sets.size());
            Map<?, ?> setA = (Map<?, ?>)sets.get(0);
            assertEquals("RS-A", setA.get("ruleCode"));
            assertEquals(2L, setA.get("evaluationCount"));
            assertEquals(0.5D, (Double)setA.get("hitRate"), 0.0001);
            assertEquals(0.5D, (Double)setA.get("failureRate"), 0.0001);
            assertEquals(200L, setA.get("p95CostTimeMs"));
            List<?> items = (List<?>)setA.get("items");
            assertEquals(2, items.size());
            assertEquals(2L, ((Map<?, ?>)items.get(0)).get("evaluationCount"));
            assertEquals(1L, ((Map<?, ?>)items.get(0)).get("hitCount"));
            assertEquals(1L, ((Map<?, ?>)items.get(1)).get("evaluationCount"));

            second.setAttemptNo(2); second.setTraceInfo(frame(10, "RS-A", 300, true, false)); logs.saveLogical(second);
            RuleExecutionLog stale = log("stats-second", frame(10, "RS-A", 999, false, true)); logs.saveLogical(stale);
            overview = (Map<?, ?>)logs.ruleSetStats(null, null, "RS-A", null, null).get("overview");
            assertEquals(2L, overview.get("evaluationCount"));
            assertEquals(2L, overview.get("hitCount"));
            assertEquals(0L, overview.get("failureCount"));
            assertEquals(300L, overview.get("p99CostTimeMs"));

            second.setAttemptNo(3); second.setTraceInfo(null); logs.saveLogical(second);
            assertNull(db.jdbc.queryForObject("SELECT trace_info FROM rule_execution_log WHERE trace_id='stats-second'", String.class));
            logs.saveLogical(log("corrupt", "{bad"));
            logs.saveLogical(log("truncated", "{\"truncated\":true}"));
            db.jdbc.update("INSERT INTO rule_execution_log (trace_id,rule_code,project_code,model_type,trace_info,create_time) VALUES ('legacy','root','stats-project','RULE_SET',?,'2026-10-11 10:00:00')", frame(12, "RS-C", 10, true, false));
            result = logs.ruleSetStats("stats-project", null, null, null, null);
            Map<?, ?> coverage = (Map<?, ?>)result.get("coverage");
            assertEquals(1L, coverage.get("pendingCount"));
            assertEquals(1L, coverage.get("noTraceCount"));
            assertEquals(1L, coverage.get("invalidCount"));
            assertEquals(1L, coverage.get("truncatedCount"));
            RuleSetStatisticsBackfill backfill = new RuleSetStatisticsBackfill(db.jdbc, new DataSourceTransactionManager(db.source));
            try { assertEquals(1, backfill.processBatch()); assertEquals(0, backfill.processBatch()); } finally { backfill.close(); }
            result = logs.ruleSetStats("stats-project", null, null, null, null);
            assertEquals(0L, ((Map<?, ?>)result.get("coverage")).get("pendingCount"));
            assertEquals(3L, ((Map<?, ?>)result.get("overview")).get("evaluationCount"));
            assertEquals(0L, ((Map<?, ?>)logs.ruleSetStats("another-project", null, null, null, null).get("overview")).get("evaluationCount"));
        }
    }

    static void createLogTables(BillingSummaryTestDatabase db) throws Exception {
        String schema = new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8);
        for (String table : List.of("rule_execution_log", "rule_execution_log_identity", "rule_execution_statistics", "rule_set_statistics_event")) {
            int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `" + table + "`");
            assertTrue(start >= 0); db.jdbc.execute(schema.substring(start, schema.indexOf(';', start) + 1));
        }
        db.jdbc.execute(new ClassPathResource("sql/logical-execution-log-view.sql").getContentAsString(StandardCharsets.UTF_8));
        db.jdbc.execute(new ClassPathResource("sql/logical-execution-statistics-view.sql").getContentAsString(StandardCharsets.UTF_8));
    }

    static RuleExecutionLogService logs(BillingSummaryTestDatabase db) {
        db.sessions.getConfiguration().addMapper(RuleExecutionLogMapper.class);
        RuleExecutionLogService target = new RuleExecutionLogService();
        ReflectionTestUtils.setField(target, "baseMapper", db.sessions.getMapper(RuleExecutionLogMapper.class));
        ReflectionTestUtils.setField(target, "entityClass", RuleExecutionLog.class);
        ReflectionTestUtils.setField(target, "statistics", transactionalQuery(db, db.jdbc));
        ReflectionTestUtils.setField(target, "statisticsStore", new RuleSetStatisticsStore(db.jdbc));
        ReflectionTestUtils.setField(target, "projectFilterService", new ProjectFilterService());
        ProxyFactory proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(db.source), new AnnotationTransactionAttributeSource()));
        return (RuleExecutionLogService)proxy.getProxy();
    }

    static RuleSetStatisticsQuery transactionalQuery(BillingSummaryTestDatabase db, JdbcTemplate jdbc) {
        ProxyFactory proxy = new ProxyFactory(new RuleSetStatisticsQuery(jdbc)); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(db.source), new AnnotationTransactionAttributeSource()));
        return (RuleSetStatisticsQuery)proxy.getProxy();
    }

    static RuleExecutionLog log(String trace, String json) {
        RuleExecutionLog value = new RuleExecutionLog(); value.setTraceId(trace); value.setTraceInfo(json);
        value.setRuleCode("root"); value.setProjectCode("stats-project"); value.setModelType("RULE_SET");
        value.setAttemptNo(1); value.setCreateTime(LocalDateTime.of(2026, 10, 11, 12, 0)); return value;
    }

    static String frame(long id, String code, long cost, boolean hit, boolean failed) {
        return "[{\"ruleId\":" + id + ",\"ruleCode\":\"" + code + "\",\"modelType\":\"RULE_SET\",\"durationMs\":" + cost
                + ",\"status\":\"" + (failed ? "FAILED" : "SUCCESS") + "\",\"events\":[{\"type\":\"RULE_SET_ITEM\",\"ruleCode\":\"A-1\",\"evaluated\":true,\"hit\":" + hit
                + "},{\"type\":\"RULE_SET_SUMMARY\",\"evaluated\":true,\"hit\":" + hit + "}]}]";
    }
}
