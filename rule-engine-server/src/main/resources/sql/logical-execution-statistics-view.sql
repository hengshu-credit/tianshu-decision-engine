-- 与rule_execution_log_current相同的代表行选择；查询主体不再读取原始追踪和输入输出大字段。
CREATE OR REPLACE SQL SECURITY INVOKER VIEW `rule_execution_statistics_current` AS
SELECT statistics.*
FROM rule_execution_statistics statistics
LEFT JOIN rule_execution_log_identity logical_identity
  ON logical_identity.trace_id = CONVERT(statistics.trace_id USING utf8mb4) COLLATE utf8mb4_unicode_ci
WHERE NULLIF(TRIM(statistics.trace_id), '') IS NULL
   OR (logical_identity.log_id = statistics.id AND logical_identity.log_create_time = statistics.create_time)
   OR (logical_identity.log_id IS NULL AND EXISTS (
       SELECT 1 FROM rule_execution_log_current legacy
       WHERE legacy.id = statistics.id AND legacy.create_time = statistics.create_time
   ));
