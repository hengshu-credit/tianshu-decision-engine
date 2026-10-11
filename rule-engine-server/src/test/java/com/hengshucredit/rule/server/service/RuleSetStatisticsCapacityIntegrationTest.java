package com.hengshucredit.rule.server.service;

import org.junit.Assume;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowCallbackHandler;

import static org.junit.Assert.*;

/** 本机独有测试库；100万条已落库日志，-Xmx128m验收统计查询不读取原始追踪。 */
public class RuleSetStatisticsCapacityIntegrationTest {
    @Test(timeout = 1800000)
    public void oneMillionPreparedTracesUseBoundedJavaHeapAndExactPercentiles() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("ruleSetStatistics.capacity"));
        try (BillingSummaryTestDatabase db = new BillingSummaryTestDatabase()) {
            RuleSetStatisticsIntegrationTest.createLogTables(db);
            int rows = Integer.getInteger("ruleSetStatistics.rows", 1000000);
            assertTrue(rows >= 10000 && rows <= 1000000 && rows % 10000 == 0);
            for (int batch = 0; batch < rows / 10000; batch++) {
                long previousId = db.jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM rule_execution_log",Long.class);
                db.jdbc.update("""
                INSERT INTO rule_execution_log (trace_id,rule_code,project_code,model_type,create_time,trace_info,rule_set_stats_status,rule_set_stats_version)
                WITH RECURSIVE numbers AS (SELECT 0 AS n UNION ALL SELECT n+1 FROM numbers WHERE n<999)
                SELECT CONCAT('capacity-',LPAD(?*10000+a.n*1000+b.n,27,'0')),'FLOW-ROOT','capacity','FLOW','2026-10-11 12:00:00',
                    JSON_ARRAY(JSON_OBJECT('ruleId',10,'ruleCode','RS-CAP','ruleName','容量规则集','modelType','RULE_SET','durationMs',b.n,'status',IF(MOD(b.n,10)=0,'FAILED','SUCCESS'),
                      'inputParams',JSON_OBJECT('payload',REPEAT('x',256)),
                      'events',JSON_ARRAY(JSON_OBJECT('type','RULE_SET_ITEM','ruleCode','ITEM','ruleName','条件','evaluated',true,'hit',MOD(b.n,2)=0),
                          JSON_OBJECT('type','RULE_SET_SUMMARY','evaluated',true,'hit',MOD(b.n,2)=0)))),
                    'READY',2
                FROM numbers a CROSS JOIN numbers b WHERE a.n<10
                """, batch);
            db.jdbc.update("""
                INSERT INTO rule_execution_statistics (id,create_time,trace_id,project_code,rule_set_stats_status,format_version,rule_set_stats_json)
                SELECT id,create_time,trace_id,project_code,'READY',2,
                    JSON_ARRAY(JSON_OBJECT('key','ID:10','ruleId',10,'ruleCode','RS-CAP','ruleName','容量规则集',
                    'cost',CAST(JSON_UNQUOTE(JSON_EXTRACT(trace_info,'$[0].durationMs')) AS UNSIGNED),
                    'hit',IF(JSON_UNQUOTE(JSON_EXTRACT(trace_info,'$[0].events[0].hit')) IN ('true','1'),1,0),
                    'failed',IF(JSON_UNQUOTE(JSON_EXTRACT(trace_info,'$[0].status'))='FAILED',1,0),
                    'items',JSON_ARRAY(JSON_OBJECT('key',SHA2('CODE:ITEM',256),'ruleCode','ITEM','ruleName','条件',
                    'hit',IF(JSON_UNQUOTE(JSON_EXTRACT(trace_info,'$[0].events[0].hit')) IN ('true','1'),1,0)))))
                FROM rule_execution_log WHERE id>?
                """, previousId);
                db.jdbc.update("""
                    INSERT INTO rule_set_statistics_event (log_id,create_time,frame_index,item_index,kind,rule_key,item_key,rule_id,parent_code,rule_code,rule_name,cost,hit,failed)
                    SELECT id,create_time,1,0,'RULE','ID:10','',10,'RS-CAP','RS-CAP','容量规则集',
                       CAST(JSON_EXTRACT(rule_set_stats_json,'$[0].cost') AS UNSIGNED),CAST(JSON_EXTRACT(rule_set_stats_json,'$[0].hit') AS UNSIGNED),CAST(JSON_EXTRACT(rule_set_stats_json,'$[0].failed') AS UNSIGNED)
                    FROM rule_execution_statistics WHERE id>?
                    UNION ALL
                    SELECT id,create_time,1,1,'ITEM','ID:10',SHA2('CODE:ITEM',256),10,'RS-CAP','ITEM','条件',0,
                       CAST(JSON_EXTRACT(rule_set_stats_json,'$[0].hit') AS UNSIGNED),0
                    FROM rule_execution_statistics WHERE id>?
                    """, previousId, previousId);
            db.jdbc.update("INSERT INTO rule_execution_log_identity (trace_id,log_id,log_create_time) SELECT trace_id,id,create_time FROM rule_execution_log WHERE id>?", previousId);
                if (batch % 10 == 9) System.out.println("RULE_SET_CAPACITY_SEEDED=" + (batch + 1) * 10000);
            }
            assertEquals(Long.valueOf(rows), db.jdbc.queryForObject("SELECT COUNT(*) FROM rule_execution_log", Long.class));
            db.jdbc.query("SELECT l.trace_info,s.rule_set_stats_json FROM rule_execution_log l JOIN rule_execution_statistics s ON s.id=l.id AND s.create_time=l.create_time ORDER BY l.id LIMIT 3", row -> {
                assertEquals(com.alibaba.fastjson.JSON.parse(RuleSetTraceSummary.fromTrace(row.getString("trace_info")).json()),
                        com.alibaba.fastjson.JSON.parse(row.getString("rule_set_stats_json")));
            });
            long started = System.nanoTime();
            Map<String, Object> result = new RuleSetStatisticsQuery(new TimedJdbc(db.source)).query("capacity", null, "RS-CAP",
                    LocalDateTime.of(2026,10,11,0,0), LocalDateTime.of(2026,10,12,0,0));
            Map<?, ?> overview = (Map<?, ?>)result.get("overview");
            assertEquals((long)rows, overview.get("evaluationCount"));
            assertEquals(rows / 2L, overview.get("hitCount"));
            assertEquals(rows / 10L, overview.get("failureCount"));
            assertEquals(949L, overview.get("p95CostTimeMs"));
            assertEquals(989L, overview.get("p99CostTimeMs"));
            assertEquals(499.5D, (Double)overview.get("avgCostTimeMs"), 0.000001);
            List<?> sets = (List<?>)result.get("ruleSets");
            assertEquals(1, sets.size());
            Map<?, ?> set = (Map<?, ?>)sets.get(0);
            assertEquals(949L, set.get("p95CostTimeMs"));
            assertEquals((long)rows, ((Map<?, ?>)((List<?>)set.get("items")).get(0)).get("evaluationCount"));
            assertEquals(0L, ((Map<?, ?>)result.get("coverage")).get("pendingCount"));
            System.out.println("RULE_SET_CAPACITY rows="+rows+" elapsedMs="+(System.nanoTime()-started)/1000000+" maxHeapBytes="+Runtime.getRuntime().maxMemory());
        }
    }
    private static final class TimedJdbc extends JdbcTemplate {
        private TimedJdbc(javax.sql.DataSource source) { super(source); }
        @Override public void query(String sql, RowCallbackHandler handler, Object... args) {
            long started = System.nanoTime();
            super.query(sql, handler, args);
            System.out.println("RULE_SET_PHASE " + (sql.startsWith("WITH items") ? "items" : "coverage") + " ms=" + (System.nanoTime()-started)/1000000);
        }
        @Override public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            long started = System.nanoTime(); T result = super.queryForObject(sql,type,args);
            System.out.println("RULE_SET_PHASE pending ms="+(System.nanoTime()-started)/1000000); return result;
        }
        @Override public <T> T queryForObject(String sql, RowMapper<T> mapper, Object... args) {
            long started = System.nanoTime(); T result = super.queryForObject(sql,mapper,args);
            System.out.println("RULE_SET_PHASE overview ms="+(System.nanoTime()-started)/1000000); return result;
        }
        @Override public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
            long started = System.nanoTime(); List<T> result = super.query(sql,mapper,args);
            System.out.println("RULE_SET_PHASE groups ms="+(System.nanoTime()-started)/1000000);
            if (Boolean.getBoolean("ruleSetStatistics.explain") && sql.startsWith("WITH")) {
                for (Map<String,Object> row : super.queryForList("EXPLAIN ANALYZE " + sql,args)) System.out.println(row);
            }
            return result;
        }
    }

}
