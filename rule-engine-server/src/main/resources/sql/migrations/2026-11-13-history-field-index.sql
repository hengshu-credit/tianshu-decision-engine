-- server 按目标 schema 建好历史索引表和逻辑视图后，用本脚本回填原有执行记录。
-- 停止应用后执行。逻辑视图已按追踪号/尝试号选出当前代表行。
-- 大库可按日志分区时间拆批执行以下事务；所有适用批次完成后再启动应用。
START TRANSACTION;
INSERT INTO rule_history_root
    (log_id,log_create_time,trace_id,execution_project_id,project_code,actor_user_id,root_rule_id,started_at)
SELECT id,create_time,trace_id,execution_project_id,project_code,actor_user_id,root_rule_id,started_at
FROM rule_execution_log_current
WHERE success=1 AND source IN ('CLIENT_SERVER','EXPERIMENT_LIVE')
  AND started_at IS NOT NULL AND history_fields IS NOT NULL
ON DUPLICATE KEY UPDATE log_id=rule_history_root.log_id;

INSERT INTO rule_history_numeric_field
    (log_id,log_create_time,field_key,numeric_value,value_kind,execution_project_id,started_at)
SELECT v.log_id,v.log_create_time,v.field_key,
       CASE WHEN JSON_TYPE(v.val) IN ('INTEGER','DOUBLE','DECIMAL','UNSIGNED INTEGER') THEN CAST(v.val AS DOUBLE) END,
       CASE WHEN JSON_TYPE(v.val) IN ('INTEGER','DOUBLE','DECIMAL','UNSIGNED INTEGER') THEN 'N'
            WHEN JSON_TYPE(v.val)='NULL' THEN 'Z' ELSE 'X' END,v.execution_project_id,v.started_at
FROM (
    SELECT r.log_id,r.log_create_time,r.execution_project_id,r.started_at,k.field_key,
           JSON_EXTRACT(l.history_fields,CONCAT('$.fields.',JSON_QUOTE(k.field_key))) AS val
    FROM rule_history_root r JOIN rule_execution_log l ON l.id=r.log_id AND l.create_time=r.log_create_time
    JOIN JSON_TABLE(JSON_KEYS(l.history_fields,'$.fields'), '$[*]' COLUMNS(field_key VARCHAR(64) PATH '$')) k
    WHERE JSON_EXTRACT(l.history_fields,'$.version')=1 AND JSON_TYPE(JSON_EXTRACT(l.history_fields,'$.fields'))='OBJECT'
) v
ON DUPLICATE KEY UPDATE log_id=rule_history_numeric_field.log_id;
DELETE FROM rule_history_bucket;
INSERT INTO rule_history_bucket
    (bucket_hour,execution_project_id,root_rule_id,field_key,sample_count,numeric_count,null_count,min_value,max_value)
SELECT DATE_FORMAT(started_at,'%Y-%m-%d %H:00:00'),COALESCE(execution_project_id,0),COALESCE(root_rule_id,0),'',COUNT(*),0,0,NULL,NULL
FROM rule_history_root GROUP BY 1,2,3
UNION ALL
SELECT DATE_FORMAT(r.started_at,'%Y-%m-%d %H:00:00'),COALESCE(r.execution_project_id,0),COALESCE(r.root_rule_id,0),
       f.field_key,COUNT(*),SUM(f.value_kind='N'),SUM(f.value_kind='Z'),MIN(f.numeric_value),MAX(f.numeric_value)
FROM rule_history_root r JOIN rule_history_numeric_field f ON f.log_id=r.log_id AND f.log_create_time=r.log_create_time
GROUP BY 1,2,3,4;

INSERT INTO rule_history_index_state(format_version) VALUES (1)
ON DUPLICATE KEY UPDATE format_version=1;
COMMIT;
