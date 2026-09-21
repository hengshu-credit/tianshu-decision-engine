package com.hengshucredit.rule.server.derived;

import com.alibaba.fastjson.JSON;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class ApplicationHistoryRepository {
    private static final int MAX_QUERY_ROWS = 100000;
    private final JdbcTemplate jdbc;

    public ApplicationHistoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(Long projectId, Long rootRuleId, String traceId, LocalDateTime occurredAt, Map<String, Object> fields) {
        if (traceId == null || rootRuleId == null) throw new IllegalArgumentException("进件快照缺少根规则或请求标识");
        jdbc.update("INSERT INTO rule_application_history (project_id, root_rule_id, trace_id, occurred_at, field_values) "
                        + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE trace_id = trace_id",
                projectId, rootRuleId, traceId, Timestamp.valueOf(occurredAt), JSON.toJSONString(fields));
    }

    public List<HistoryQuery.Row> query(String scope, Long projectId, Long rootRuleId,
                                       LocalDateTime from, LocalDateTime before) {
        StringBuilder sql = new StringBuilder("SELECT id, occurred_at, field_values FROM rule_application_history "
                + "WHERE occurred_at >= ? AND occurred_at < ?");
        List<Object> parameters = new ArrayList<>(List.of(Timestamp.valueOf(from), Timestamp.valueOf(before)));
        if ("RULE".equals(scope)) {
            DerivedVariableConfig.require(rootRuleId != null, "当前规则范围需要从规则测试或正式规则入口执行");
            sql.append(" AND root_rule_id = ?"); parameters.add(rootRuleId);
        }
        if ("PROJECT".equals(scope) || "RULE".equals(scope)) {
            DerivedVariableConfig.require(projectId != null && projectId > 0, "当前项目范围需要明确执行项目");
            sql.append(" AND project_id = ?"); parameters.add(projectId);
        } else {
            DerivedVariableConfig.require("GLOBAL".equals(scope), "历史统计范围无效");
        }
        sql.append(" ORDER BY occurred_at, id LIMIT ").append(MAX_QUERY_ROWS + 1);
        long[] bytesRead = {0};
        List<HistoryQuery.Row> rows = jdbc.query(connection -> {
            var statement = connection.prepareStatement(sql.toString());
            statement.setQueryTimeout(10);
            for (int i = 0; i < parameters.size(); i++) statement.setObject(i + 1, parameters.get(i));
            return statement;
        }, (result, index) -> {
            String json = result.getString("field_values");
            bytesRead[0] += json.length() * 2L;
            if (bytesRead[0] > 64L * 1024 * 1024) throw new IllegalStateException("历史窗口数据超过 64 MiB，请缩小查询范围；未返回截断统计值");
            return new HistoryQuery.Row(result.getLong("id"), result.getTimestamp("occurred_at").toLocalDateTime(), JSON.parseObject(json));
        });
        if (rows.size() > MAX_QUERY_ROWS) throw new IllegalStateException("历史窗口超过 100000 条进件，请缩小时间或作用范围；未返回截断统计值");
        return rows;
    }
}
