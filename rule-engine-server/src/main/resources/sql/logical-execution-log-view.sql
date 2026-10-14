-- schema.sql 包含带字段存在性守卫的权威定义；此独立副本供 SchemaSync 在旧库补齐字段后重建视图。
CREATE OR REPLACE SQL SECURITY INVOKER VIEW `rule_execution_log_current` AS
SELECT log_row.*
FROM rule_execution_log log_row
LEFT JOIN rule_execution_log_identity logical_identity
  ON logical_identity.trace_id = CONVERT(log_row.trace_id USING utf8mb4) COLLATE utf8mb4_unicode_ci
WHERE NULLIF(TRIM(log_row.trace_id), '') IS NULL
   OR (logical_identity.log_id = log_row.id AND logical_identity.log_create_time = log_row.create_time)
   OR (logical_identity.log_id IS NULL AND NOT EXISTS (
       SELECT 1 FROM rule_execution_log newer
       WHERE newer.trace_id = log_row.trace_id
         AND (COALESCE(newer.attempt_no, 1) > COALESCE(log_row.attempt_no, 1)
           OR (COALESCE(newer.attempt_no, 1) = COALESCE(log_row.attempt_no, 1)
             AND (newer.create_time > log_row.create_time
               OR (newer.create_time = log_row.create_time AND newer.id > log_row.id))))
   ));
