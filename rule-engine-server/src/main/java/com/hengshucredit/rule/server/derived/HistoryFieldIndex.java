package com.hengshucredit.rule.server.derived;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Maintained in the logical-log transaction, including retries that invalidate an earlier success. */
@Repository
public class HistoryFieldIndex {
    private final JdbcTemplate jdbc;

    public HistoryFieldIndex(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static boolean eligible(RuleExecutionLog log) {
        return log != null && Integer.valueOf(1).equals(log.getSuccess()) && log.getStartedAt() != null
                && log.getHistoryFields() != null
                && ("CLIENT_SERVER".equals(log.getSource()) || "EXPERIMENT_LIVE".equals(log.getSource()));
    }

    public void replace(RuleExecutionLog previous, RuleExecutionLog current) {
        if (eligible(previous) && jdbc.update("DELETE FROM rule_history_root WHERE log_id=? AND log_create_time=?",
                previous.getId(), Timestamp.valueOf(previous.getCreateTime())) > 0) {
            updateBucket(previous, JSON.parseObject(previous.getHistoryFields()).getJSONObject("fields"), -1);
        }
        if (!eligible(current)) return;
        var snapshot = JSON.parseObject(current.getHistoryFields());
        if (snapshot.getIntValue("version") != 1 || snapshot.getJSONObject("fields") == null) {
            throw new IllegalStateException("历史字段快照无效，无法建立进件索引");
        }
        var time = Timestamp.valueOf(current.getCreateTime());
        var started = Timestamp.valueOf(current.getStartedAt());
        jdbc.update("INSERT INTO rule_history_root (log_id,log_create_time,trace_id,execution_project_id,project_code,"
                        + "actor_user_id,root_rule_id,started_at) VALUES (?,?,?,?,?,?,?,?)",
                current.getId(), time, current.getTraceId(), current.getExecutionProjectId(), current.getProjectCode(),
                current.getActorUserId(), current.getRootRuleId(), started);
        var fields = snapshot.getJSONObject("fields");
        var entries = fields.entrySet();
        jdbc.batchUpdate("INSERT INTO rule_history_numeric_field (log_id,log_create_time,field_key,numeric_value,"
                        + "value_kind,execution_project_id,started_at) VALUES (?,?,?,?,?,?,?)", entries, 256,
                (ps, entry) -> {
                    Object value = entry.getValue();
                    ps.setLong(1, current.getId());
                    ps.setTimestamp(2, time);
                    ps.setString(3, entry.getKey());
                    if (value instanceof Number number) ps.setDouble(4, number.doubleValue());
                    else ps.setNull(4, Types.DOUBLE);
                    ps.setString(5, value == null ? "Z" : value instanceof Number ? "N" : "X");
                    ps.setObject(6, current.getExecutionProjectId(), Types.BIGINT);
                    ps.setTimestamp(7, started);
                });
        updateBucket(current, fields, 1);
    }

    private void updateBucket(RuleExecutionLog log, Map<String, Object> fields, int delta) {
        var hour = Timestamp.valueOf(log.getStartedAt().truncatedTo(java.time.temporal.ChronoUnit.HOURS));
        long project = log.getExecutionProjectId() == null ? 0 : log.getExecutionProjectId();
        long rule = log.getRootRuleId() == null ? 0 : log.getRootRuleId();
        // All writers acquire a bucket's root first and then its field keys in a consistent order.
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("", null);
        keys.putAll(new TreeMap<>(fields));
        jdbc.batchUpdate("INSERT INTO rule_history_bucket(bucket_hour,execution_project_id,root_rule_id,field_key,"
                        + "sample_count,numeric_count,null_count,min_value,max_value) VALUES(?,?,?,?,?,?,?,?,?) AS incoming "
                        + "ON DUPLICATE KEY UPDATE sample_count=rule_history_bucket.sample_count+incoming.sample_count,"
                        + "numeric_count=rule_history_bucket.numeric_count+incoming.numeric_count,null_count=rule_history_bucket.null_count+incoming.null_count,"
                        + "min_value=COALESCE(LEAST(rule_history_bucket.min_value,incoming.min_value),rule_history_bucket.min_value,incoming.min_value),"
                        + "max_value=COALESCE(GREATEST(rule_history_bucket.max_value,incoming.max_value),rule_history_bucket.max_value,incoming.max_value)",
                keys.entrySet(), 256, (ps, entry) -> {
                    Object value = entry.getValue();
                    ps.setTimestamp(1, hour);
                    ps.setLong(2, project);
                    ps.setLong(3, rule);
                    ps.setString(4, entry.getKey());
                    ps.setInt(5, delta);
                    ps.setInt(6, value instanceof Number ? delta : 0);
                    ps.setInt(7, !entry.getKey().isEmpty() && value == null ? delta : 0);
                    Double number = value instanceof Number n && delta > 0 ? n.doubleValue() : null;
                    ps.setObject(8, number, Types.DOUBLE);
                    ps.setObject(9, number, Types.DOUBLE);
                });
    }
}
