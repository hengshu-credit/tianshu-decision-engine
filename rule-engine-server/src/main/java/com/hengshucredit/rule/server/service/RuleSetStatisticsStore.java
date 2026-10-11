package com.hengshucredit.rule.server.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 与原日志共用事务，采用原日志实际存储的范围、时间和attempt，不信任输入的派生字段。 */
@Service
public class RuleSetStatisticsStore {
    static final int FORMAT_VERSION = 2;
    private final JdbcTemplate jdbc;
    public RuleSetStatisticsStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void persist(long id, LocalDateTime time, String json, String status) {
        if (time == null) throw new IllegalStateException("统计快照缺少日志物理位置");
        int written = jdbc.update("""
            INSERT INTO rule_execution_statistics (id,create_time,trace_id,attempt_no,project_code,rule_set_stats_json,rule_set_stats_status,format_version)
            SELECT id,create_time,trace_id,attempt_no,project_code,?, ?,2 FROM rule_execution_log WHERE id=? AND create_time=?
            ON DUPLICATE KEY UPDATE trace_id=VALUES(trace_id),attempt_no=VALUES(attempt_no),project_code=VALUES(project_code),
                rule_set_stats_json=VALUES(rule_set_stats_json),rule_set_stats_status=VALUES(rule_set_stats_status),format_version=2
            """, json, status, id, time);
        if (written == 0 && !Integer.valueOf(1).equals(jdbc.queryForObject("SELECT COUNT(*) FROM rule_execution_statistics "
                + "WHERE id=? AND create_time=? AND rule_set_stats_json=CAST(? AS JSON) AND rule_set_stats_status=?", Integer.class, id, time, json, status))) {
            throw new IllegalStateException("规则集统计快照未写入");
        }
        jdbc.update("DELETE FROM rule_set_statistics_event WHERE log_id=? AND create_time=?", id, time);
        // 只在写入时展开一次JSON；父帧和规则项采用显式kind，读取时直接使用结构化字段。
        jdbc.update("""
            INSERT INTO rule_set_statistics_event
              (log_id,create_time,frame_index,item_index,kind,rule_key,item_key,rule_id,parent_code,rule_code,rule_name,cost,hit,failed)
            SELECT ?,?,frame_index,0,'RULE',rule_key,'',rule_id,rule_code,rule_code,rule_name,cost,hit,failed
            FROM JSON_TABLE(CAST(? AS JSON),'$[*]' COLUMNS(frame_index FOR ORDINALITY,rule_key VARCHAR(80) PATH '$.key',
              rule_id BIGINT PATH '$.ruleId',rule_code LONGTEXT PATH '$.ruleCode',rule_name LONGTEXT PATH '$.ruleName',
              cost BIGINT PATH '$.cost',hit INT PATH '$.hit',failed INT PATH '$.failed')) frames
            UNION ALL
            SELECT ?,?,frame_index,item_index,'ITEM',rule_key,item_key,rule_id,parent_code,rule_code,rule_name,0,hit,0
            FROM JSON_TABLE(CAST(? AS JSON),'$[*]' COLUMNS(frame_index FOR ORDINALITY,rule_key VARCHAR(80) PATH '$.key',
              rule_id BIGINT PATH '$.ruleId',parent_code LONGTEXT PATH '$.ruleCode',NESTED PATH '$.items[*]' COLUMNS(
              item_index FOR ORDINALITY,item_key VARCHAR(64) PATH '$.key',rule_code LONGTEXT PATH '$.ruleCode',
              rule_name LONGTEXT PATH '$.ruleName',hit INT PATH '$.hit'))) items WHERE item_index IS NOT NULL AND item_key IS NOT NULL
            """, id,time,json,id,time,json);
    }
}
