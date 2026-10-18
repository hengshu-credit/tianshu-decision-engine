package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.action.ListWriteConfig;
import com.hengshucredit.rule.core.util.MissingValueSemantics;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 专用单连接写池，不借用规则查询池；接收回执、名单与审计在同一事务中提交。 */
@Service
public class RuleListActionWriter implements AutoCloseable {
    private final HikariDataSource writePool;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    @org.springframework.beans.factory.annotation.Autowired
    public RuleListActionWriter(DataSource dataSource) throws SQLException {
        HikariConfig config = new HikariConfig();
        dataSource.unwrap(HikariDataSource.class).copyStateTo(config);
        config.setPoolName("rule-list-write"); config.setMaximumPoolSize(1); config.setMinimumIdle(0);
        config.setConnectionTimeout(1000); config.setInitializationFailTimeout(-1);
        config.setRegisterMbeans(false);
        writePool = new HikariDataSource(config);
        jdbc = new JdbcTemplate(writePool); jdbc.setQueryTimeout(5);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(writePool));
        transaction.setTimeout(10);
    }

    RuleListActionWriter(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.writePool = null; this.jdbc = jdbc; this.transaction = transaction;
    }

    int write(String taskId, Long projectId, JSONObject config, List<?> values, LocalDateTime actionTime) {
        return write(taskId, projectId, config, values, actionTime, null);
    }

    int write(String taskId, Long projectId, JSONObject config, List<?> values,
              LocalDateTime actionTime, JSONObject rule) {
        LocalDateTime expires = validateRequest(config, values, actionTime);
        List<Long> ids = config.getJSONArray("listIds").stream().map(id -> ListWriteConfig.positiveLong(id, "名单 ID")).distinct().sorted().toList();
        String rootTraceId = rule == null ? null : rule.getString("traceId");
        String ruleCode = rule == null ? null : rule.getString("code");
        String ruleVersion = rule == null || rule.get("version") == null ? null : String.valueOf(rule.get("version"));
        String revisionId = rule == null ? null : rule.getString("revisionId");
        String artifactDigest = rule == null ? null : rule.getString("artifactDigest");
        Integer count = transaction.execute(status -> {
            int accepted = jdbc.update("INSERT IGNORE INTO rule_list_action_receipt (task_id, project_id, root_trace_id, rule_code, rule_version, revision_id, artifact_digest) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    taskId, projectId, rootTraceId, ruleCode, ruleVersion, revisionId, artifactDigest);
            if (accepted == 0) return 0;
            for (Long id : ids) {
                var libraries = jdbc.queryForList("SELECT scope, project_id, status FROM rule_list_library WHERE id = ? FOR UPDATE", id);
                if (libraries.isEmpty()) throw new IllegalArgumentException("名单不存在: " + id);
                var library = libraries.get(0);
                if (((Number) library.get("status")).intValue() != 1 || !"GLOBAL".equals(library.get("scope"))
                        && !Objects.equals(projectId, library.get("project_id") == null ? null : ((Number) library.get("project_id")).longValue())) throw new IllegalArgumentException("名单已停用或不属于当前项目: " + id);
            }
            int changed = 0;
            for (Long id : ids) for (int i = 0; i < values.size(); i++) {
                Object value = values.get(i);
                if (MissingValueSemantics.isMissing(value)) continue;
                changed += writeRecord(id, config.getJSONArray("itemTypes").getString(i), String.valueOf(value), config.getString("operation"), actionTime, expires,
                        taskId, rootTraceId, ruleCode, ruleVersion, revisionId, artifactDigest);
            }
            jdbc.update("UPDATE rule_list_action_receipt SET changed_count = ? WHERE task_id = ?", changed, taskId);
            return changed;
        });
        return count == null ? 0 : count;
    }

    static LocalDateTime validateRequest(JSONObject config, List<?> values, LocalDateTime actionTime) {
        ListWriteConfig.validate(config);
        var types = config.getJSONArray("itemTypes");
        if (values == null || types.size() != values.size()) throw new IllegalArgumentException("名单字段与内容类型数量不一致");
        for (Object value : values) {
            if (MissingValueSemantics.isMissing(value)) continue;
            if (value instanceof Map || value instanceof java.util.Collection || value.getClass().isArray()) throw new IllegalArgumentException("名单内容请选择具体字段，不能写入整个对象或集合");
            if (String.valueOf(value).length() > 512) throw new IllegalArgumentException("名单内容不能超过512个字符");
        }
        LocalDateTime expires = ListWriteConfig.expiresAt(config, actionTime);
        if (expires != null && !expires.isAfter(actionTime)) throw new IllegalArgumentException("名单失效时间必须晚于动作执行时间");
        return expires;
    }

    private int writeRecord(Long listId, String type, String content, String operation,
                            LocalDateTime now, LocalDateTime expires, String taskId,
                            String rootTraceId, String ruleCode, String ruleVersion,
                            String revisionId, String artifactDigest) {
        var rows = jdbc.queryForList("SELECT id, status, effective_time, expire_time FROM rule_list_record WHERE list_id = ? AND item_type = ? AND item_content = ? FOR UPDATE", listId, type, content);
        Map<String, Object> previous = rows.isEmpty() ? null : rows.get(0);
        boolean enabled = previous != null && ((Number) previous.get("status")).intValue() == 1;
        LocalDateTime previousEnd = previous == null ? null : date(previous.get("expire_time"));
        boolean active = enabled && (previousEnd == null || !previousEnd.isBefore(now));
        if ("REMOVE".equals(operation) && !enabled || "ADD_ONLY".equals(operation) && active) return 0;
        LocalDateTime start = now, end = expires;
        if ("MERGE".equals(operation) && active) {
            LocalDateTime previousStart = date(previous.get("effective_time"));
            if (previousStart == null || previousStart.isBefore(start)) start = previousStart;
            if (previousEnd == null || end != null && previousEnd.isAfter(end)) end = previousEnd;
        }
        String change = "REMOVE".equals(operation) ? "DELETE" : previous == null ? "ADD" : "UPDATE";
        String reason = "规则动作：" + operation;
        if (previous == null) jdbc.update("INSERT INTO rule_list_record (list_id,item_type,item_content,effective_time,expire_time,reason,last_operation,status) VALUES (?,?,?,?,?,?,?,1)", listId, type, content, start, end, reason, change);
        else if ("REMOVE".equals(operation)) jdbc.update("UPDATE rule_list_record SET status=0,last_operation='DELETE',reason=? WHERE id=?", reason, previous.get("id"));
        else jdbc.update("UPDATE rule_list_record SET effective_time=?,expire_time=?,reason=?,last_operation=?,status=1 WHERE id=?", start, end, reason, change, previous.get("id"));
        jdbc.update("INSERT INTO rule_list_record_log (list_id,record_id,item_type,item_content,effective_time,expire_time,reason,operation,operator,task_id,root_trace_id,rule_code,rule_version,revision_id,artifact_digest) SELECT list_id,id,item_type,item_content,effective_time,expire_time,reason,last_operation,?,?,?,?,?,?,? FROM rule_list_record WHERE list_id=? AND item_type=? AND item_content=?",
                "规则任务:" + taskId, taskId, rootTraceId, ruleCode, ruleVersion, revisionId, artifactDigest, listId, type, content);
        return 1;
    }

    private static LocalDateTime date(Object value) {
        return value == null ? null : value instanceof Timestamp stamp ? stamp.toLocalDateTime() : (LocalDateTime) value;
    }

    @Override @PreDestroy public void close() { if (writePool != null) writePool.close(); }
}
