package com.hengshucredit.rule.server.derived;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * 直接从正式规则执行日志读取进件历史。
 * 只消费对外/客户端成功的根规则日志，避免把管理端测试和子规则重复计入。
 */
@Repository
public class ExecutionLogHistoryRepository {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.hengshucredit.rule.server.security.ConsoleDataPermissionHandler consoleDataScope;

    private String secured(String sql) { return consoleDataScope == null ? sql : consoleDataScope.filterSql(sql); }

    private static final int MAX_QUERY_ROWS = 100000;
    private final JdbcTemplate jdbc;

    public ExecutionLogHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<HistoryQuery.Row> query(String scope, Long projectId, Long rootRuleId,
                                        LocalDateTime from, LocalDateTime before,
                                        Map<String, String> referencePaths) {
        return query(scope, projectId, rootRuleId, from, before, Set.of(), Map.of());
    }

    public List<HistoryQuery.Row> query(String scope, Long projectId, Long rootRuleId,
            LocalDateTime from, LocalDateTime before, Set<String> requiredFields,
            Map<String, HistoricalFieldDefinition> definitions) {
        if (consoleDataScope != null) consoleDataScope.requireHistoryScope(scope, projectId);
        StringBuilder sql = new StringBuilder(
                "SELECT l.id, l.trace_id, l.started_at, l.history_fields "
                        + "FROM rule_execution_log_current l "
                        + "WHERE l.started_at >= ? AND l.started_at < ? AND l.success = 1 "
                        + "AND l.source IN ('CLIENT_SERVER','EXPERIMENT_LIVE') AND l.history_fields IS NOT NULL ");
        List<Object> parameters = new ArrayList<>();
        parameters.add(Timestamp.valueOf(from));
        parameters.add(Timestamp.valueOf(before));
        if ("RULE".equals(scope)) {
            DerivedVariableConfig.require(rootRuleId != null,
                    "当前规则范围需要从规则测试或正式规则入口执行");
            sql.append("AND l.root_rule_id = ? ");
            parameters.add(rootRuleId);
        }
        if ("PROJECT".equals(scope) || "RULE".equals(scope)) {
            DerivedVariableConfig.require(projectId != null && projectId > 0,
                    "当前项目范围需要明确执行项目");
            sql.append("AND l.execution_project_id = ? ");
            parameters.add(projectId);
        } else {
            DerivedVariableConfig.require("GLOBAL".equals(scope), "历史统计范围无效");
        }
        sql.append("ORDER BY l.started_at, l.id LIMIT ").append(MAX_QUERY_ROWS + 1);
        long[] bytesRead = {0};
        Map<String, HistoryQuery.Row> rowsByTrace = new LinkedHashMap<>();
        Map<String, Set<Long>> expectedApis = new LinkedHashMap<>();
        List<HistoryQuery.Row> rows = new ArrayList<>(jdbc.query(connection -> {
            var statement = connection.prepareStatement(secured(sql.toString()));
            statement.setQueryTimeout(10);
            for (int i = 0; i < parameters.size(); i++) statement.setObject(i + 1, parameters.get(i));
            return statement;
        }, (result, index) -> {
            if (index >= MAX_QUERY_ROWS) throw new IllegalStateException("执行日志历史窗口超过 100000 条，请缩小时间或作用范围；未返回截断统计值");
            JSONObject snapshot = parseSnapshot(result.getString("history_fields"), bytesRead);
            if (snapshot.getIntValue("version") != 1 || snapshot.getJSONObject("fields") == null) {
                throw new IllegalStateException("历史字段快照版本无效，不能按当前名称推测历史值");
            }
            String trace = result.getString("trace_id");
            var row = new HistoryQuery.Row(result.getLong("id"), result.getTimestamp("started_at").toLocalDateTime(),
                    new LinkedHashMap<>(snapshot.getJSONObject("fields")));
            rowsByTrace.put(trace, row);
            Set<Long> apis = new LinkedHashSet<>();
            if (snapshot.getJSONArray("apiIds") != null) for (Object id : snapshot.getJSONArray("apiIds")) apis.add(((Number) id).longValue());
            expectedApis.put(trace, apis);
            return row;
        }));
        // API bindings (and even source kinds) can change after an application.
        // Read that root's logical logs (including explicit API nodes), then match stored field IDs.
        if (!requiredFields.isEmpty()) {
            rowsByTrace.entrySet().removeIf(entry -> entry.getValue().fields().keySet().containsAll(requiredFields));
            if (!rowsByTrace.isEmpty()) loadApiFields(rowsByTrace, expectedApis, bytesRead, requiredFields);
        }
        return rows;
    }

    private JSONObject parseSnapshot(String json, long[] bytesRead) {
        if (json == null || json.isBlank()) throw new IllegalStateException("历史结果日志缺少字段快照，统计不完整");
        bytesRead[0] += json.length() * 2L;
        if (bytesRead[0] > 64L * 1024 * 1024) {
            throw new IllegalStateException("历史窗口超过 64 MiB，请缩小范围；未返回截断统计值");
        }
        return JSON.parseObject(json);
    }

    private record ApiResult(String trace, JSONObject fields, boolean workflow, String callId) {}

    private boolean ownsSteps(ApiResult summary, List<ApiResult> calls) {
        if (summary.callId() == null) return false;
        var traces = jdbc.queryForList(secured("SELECT trace_steps FROM rule_runtime_call_log WHERE "
                + "action_type='API_INVOKE' AND call_id=? AND root_trace_id=?"), String.class,
                summary.callId(), summary.trace());
        if (traces.size() != 1 || traces.get(0) == null) return false;
        Set<String> childIds = new LinkedHashSet<>();
        for (Object raw : JSON.parseArray(traces.get(0))) {
            if (!(raw instanceof JSONObject step) || !"WORKFLOW_STEP".equals(step.getString("type"))) continue;
            if (step.get("output") instanceof JSONObject output && output.getString("callId") != null)
                childIds.add(output.getString("callId"));
            if (step.get("children") instanceof List<?> children) {
                for (Object child : children) if (child instanceof JSONObject event) childIds.add(event.getString("callId"));
            }
        }
        childIds.remove(null);
        return calls.stream().allMatch(call -> call == summary || childIds.contains(call.callId()));
    }

    private void loadApiFields(Map<String, HistoryQuery.Row> rows, Map<String, Set<Long>> expected,
                                long[] bytesRead, Set<String> requiredFields) {
        List<String> traces = new ArrayList<>(rows.keySet());
        Set<String> seen = new LinkedHashSet<>();
        long deadline = System.nanoTime() + 10_000_000_000L;
        for (int offset = 0; offset < traces.size(); offset += 500) {
            if (System.nanoTime() >= deadline) throw new IllegalStateException("历史外数结果查询超时");
            List<String> batch = traces.subList(offset, Math.min(offset + 500, traces.size()));
            String sql = "SELECT root_trace_id, target_ref_id, history_fields, call_id, JSON_EXTRACT(IF(JSON_VALID(trace_steps),trace_steps,NULL), '$[*].type') AS step_types FROM rule_runtime_call_log "
                    + "WHERE action_type = 'API_INVOKE' AND module_type = 'DATASOURCE' AND root_trace_id IN ("
                    + String.join(",", java.util.Collections.nCopies(batch.size(), "?")) + ") ORDER BY id";
            Map<String, List<ApiResult>> results = new LinkedHashMap<>();
            jdbc.query(connection -> {
                var statement = connection.prepareStatement(secured(sql));
                statement.setQueryTimeout(Math.max(1, (int) ((deadline - System.nanoTime()) / 1_000_000_000L)));
                int index = 1;
                for (String trace : batch) statement.setString(index++, trace);
                return statement;
            }, (result, index) -> {
                String trace = result.getString("root_trace_id");
                Long api = result.getLong("target_ref_id");
                // Older versions labelled workflow transports as API_INVOKE. Only an identified
                // workflow summary can supersede them; genuinely ambiguous logical results still fail.
                boolean workflow = false;
                String types = result.getString("step_types");
                if (types != null) workflow = JSON.parseArray(types).contains("WORKFLOW_STEP");
                String owner = trace + ":" + api;
                results.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(
                        new ApiResult(trace, parseSnapshot(result.getString("history_fields"), bytesRead), workflow,
                                result.getString("call_id")));
                return 0;
            });
            for (var group : results.entrySet()) {
                List<ApiResult> calls = group.getValue();
                ApiResult selected = calls.get(0);
                if (calls.size() > 1) {
                    var summaries = calls.stream().filter(ApiResult::workflow).toList();
                    if (summaries.size() != 1 || !ownsSteps(summaries.get(0), calls)) {
                        throw new IllegalStateException("同一进件同一接口存在多个逻辑结果，无法唯一回溯");
                    }
                    selected = summaries.get(0);
                }
                seen.add(group.getKey());
                var fields = rows.get(selected.trace()).fields();
                for (String key : requiredFields) {
                    if (!fields.containsKey(key) && selected.fields().containsKey(key)) fields.put(key, selected.fields().get(key));
                }
            }
        }
        for (var entry : expected.entrySet()) {
            if (!rows.containsKey(entry.getKey())) continue;
            for (Long api : entry.getValue()) {
                if (!seen.contains(entry.getKey() + ":" + api)) {
                    throw new IllegalStateException("历史外数逻辑结果日志缺失，统计不完整: " + entry.getKey());
                }
            }
        }
    }
}

