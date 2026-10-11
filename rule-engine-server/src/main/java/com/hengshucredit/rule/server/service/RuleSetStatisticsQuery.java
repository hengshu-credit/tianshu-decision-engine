package com.hengshucredit.rule.server.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询只读取已提取的计数素材；完整追踪不会进入控制台查询的Java堆。 */
@Service
public class RuleSetStatisticsQuery {
    private final JdbcTemplate jdbc;
    public RuleSetStatisticsQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> query(String projectCode, List<String> projectCodes, String ruleCode,
                                     LocalDateTime start, LocalDateTime end) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (projectCode != null && !projectCode.isBlank()) { where.append(" AND project_code LIKE ?"); parameters.add('%' + projectCode + '%'); }
        if (projectCodes != null) {
            if (projectCodes.isEmpty()) where.append(" AND 1=0");
            else { where.append(" AND project_code IN (").append(String.join(",", java.util.Collections.nCopies(projectCodes.size(), "?"))).append(')'); parameters.addAll(projectCodes); }
        }
        if (start != null) { where.append(" AND create_time>=?"); parameters.add(start); }
        if (end != null) { where.append(" AND create_time<=?"); parameters.add(end); }
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("queriedAt", LocalDateTime.now());
        for (String field : List.of("totalCount", "pendingCount", "readyCount", "noTraceCount", "invalidCount", "truncatedCount")) coverage.put(field, 0L);
        jdbc.query("SELECT COUNT(*) AS amount FROM rule_execution_log_current" + where + " AND (rule_set_stats_version IS NULL OR rule_set_stats_version<2)", rs -> {
            long pending = rs.getLong("amount"); coverage.put("pendingCount",pending); coverage.put("totalCount",pending);
        }, parameters.toArray());
        String eventWhere = " WHERE 1=1";
        List<Object> eventParameters = new ArrayList<>(parameters);
        if (ruleCode != null && !ruleCode.isBlank()) { eventWhere += " AND CAST(e.parent_code AS BINARY)=CAST(? AS BINARY)"; eventParameters.add(ruleCode); }
        // 以存储时生成的排序键选择首次非空名称，避免逐条拼接大字符串。
        String costs = "WITH eligible AS (SELECT id,create_time,rule_set_stats_status FROM rule_execution_statistics_current" + where
                + " AND format_version=2 GROUP BY id,create_time,rule_set_stats_status), costs AS (SELECT e.kind,e.rule_key,e.item_key,e.cost,MAX(e.rule_id) rule_id,COUNT(*) frequency,SUM(e.hit) hits,SUM(e.failed) failures,"
                + "MIN(CASE WHEN e.rule_code IS NOT NULL THEN e.order_key END) first_code,MIN(CASE WHEN e.rule_name IS NOT NULL THEN e.order_key END) first_name "
                + "FROM eligible h JOIN rule_set_statistics_event e ON e.log_id=h.id AND e.create_time=h.create_time"
                + eventWhere + " GROUP BY e.kind,e.rule_key,e.item_key,e.cost),"
                + " all_costs AS (SELECT * FROM costs UNION ALL SELECT 'OVERVIEW',NULL,NULL,cost,NULL,SUM(frequency),SUM(hits),SUM(failures),NULL,NULL FROM costs WHERE kind='RULE' GROUP BY cost),"
                + " ranked AS (SELECT all_costs.*,SUM(frequency) OVER(PARTITION BY kind,rule_key,item_key ORDER BY cost) cumulative,"
                + "SUM(frequency) OVER(PARTITION BY kind,rule_key,item_key) total FROM all_costs) ";
        String query = costs + ", totals AS (SELECT kind,rule_key,item_key,MAX(rule_id) ruleId,MIN(first_code) code_key,"
                + "MIN(first_name) name_key,SUM(frequency) evaluationCount,SUM(hits) hitCount,SUM(failures) failureCount,"
                + "SUM(cost*frequency)/SUM(frequency) avgCostTimeMs,MIN(CASE WHEN cumulative>=CEIL(total*0.95) THEN cost END) p95CostTimeMs,"
                + "MIN(CASE WHEN cumulative>=CEIL(total*0.99) THEN cost END) p99CostTimeMs FROM ranked GROUP BY kind,rule_key,item_key) "
                + "SELECT totals.*,code.rule_code ruleCode,name.rule_name ruleName FROM totals "
                + "LEFT JOIN rule_set_statistics_event code ON code.order_key=totals.code_key LEFT JOIN rule_set_statistics_event name ON name.order_key=totals.name_key "
                + "UNION ALL SELECT 'COVERAGE',NULL,NULL,NULL,NULL,NULL,COUNT(*),0,0,0,0,0,rule_set_stats_status,NULL FROM eligible GROUP BY rule_set_stats_status";
        List<Map<String, Object>> rows = jdbc.query(query, (rs, row) -> {
            Map<String, Object> result = metrics(rs);
            result.put("kind",rs.getString("kind")); result.put("key",rs.getString("rule_key")); result.put("ruleId",rs.getObject("ruleId"));
            result.put("ruleCode",rs.getString("ruleCode")); result.put("ruleName",rs.getString("ruleName")); return result;
        }, eventParameters.toArray());
        Map<String, Object> overview = new LinkedHashMap<>(Map.of("evaluationCount",0L,"hitCount",0L,"failureCount",0L,
                "hitRate",0D,"failureRate",0D,"avgCostTimeMs",0D,"p95CostTimeMs",0L,"p99CostTimeMs",0L));
        List<Map<String, Object>> sets = new ArrayList<>();
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if ("COVERAGE".equals(row.get("kind"))) {
                long count = (Long)row.get("evaluationCount");
                String field = switch (String.valueOf(row.get("ruleCode"))) { case "READY" -> "readyCount"; case "NO_TRACE" -> "noTraceCount"; case "TRUNCATED" -> "truncatedCount"; default -> "invalidCount"; };
                coverage.put(field,(Long)coverage.get(field)+count); coverage.put("totalCount",(Long)coverage.get("totalCount")+count);
            } else if ("OVERVIEW".equals(row.get("kind"))) {
                row.remove("kind"); row.remove("key"); row.remove("ruleId"); row.remove("ruleCode"); row.remove("ruleName"); overview = row;
            } else if ("RULE".equals(row.get("kind"))) {
                row.remove("kind"); String key = String.valueOf(row.remove("key")); row.put("items",new ArrayList<Map<String,Object>>());
                sets.add(row); byKey.put(key,row);
            }
        }
        for (Map<String, Object> row : rows) {
            if (!"ITEM".equals(row.get("kind"))) continue;
            Map<String,Object> set = byKey.get(String.valueOf(row.get("key")));
            if (set == null) continue;
            row.keySet().retainAll(List.of("ruleCode","ruleName","evaluationCount","hitCount","hitRate"));
            @SuppressWarnings("unchecked") List<Map<String, Object>> itemRows = (List<Map<String, Object>>)set.get("items");
            itemRows.add(row);
        }
        Comparator<Map<String, Object>> byCode = Comparator.comparing(row -> String.valueOf(row.get("ruleCode")));
        sets.sort(byCode);
        for (Map<String, Object> set : sets) {
            @SuppressWarnings("unchecked") List<Map<String, Object>> itemRows = (List<Map<String, Object>>)set.get("items");
            itemRows.sort(byCode);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", overview); result.put("ruleSets", sets); result.put("coverage", coverage);
        return result;
    }

    private static Map<String, Object> metrics(ResultSet rs) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        long count = rs.getLong("evaluationCount"), hit = rs.getLong("hitCount"), failed = rs.getLong("failureCount");
        result.put("evaluationCount", count); result.put("hitCount", hit); result.put("failureCount", failed);
        result.put("hitRate", count == 0 ? 0D : (double)hit/count); result.put("failureRate", count == 0 ? 0D : (double)failed/count);
        result.put("avgCostTimeMs", rs.getDouble("avgCostTimeMs")); result.put("p95CostTimeMs", rs.getLong("p95CostTimeMs")); result.put("p99CostTimeMs", rs.getLong("p99CostTimeMs"));
        return result;
    }
}
