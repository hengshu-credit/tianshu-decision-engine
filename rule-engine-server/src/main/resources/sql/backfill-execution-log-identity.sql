-- 逻辑日志身份分批回填：仅新增映射，不修改或删除原始审计行。
-- 在独立连接、无外层事务时运行；每次最多处理 500 个 trace，可在两批间暂停。
-- 首批 @execution_log_after_trace 为空；后续仅使用上一次成功返回的 next_cursor。
-- 可选 @execution_log_through_trace 限定本次维护范围；NULL 表示没有上界。
-- 所有旧写入节点升级后再运行，正常服务写入与本脚本依靠相同主键协调。
SET @execution_log_after_trace = COALESCE(@execution_log_after_trace, '');
SET @execution_log_batch_end = (
  SELECT MAX(batch.trace_id) FROM (
    SELECT DISTINCT trace_id FROM rule_execution_log
    WHERE trace_id > @execution_log_after_trace
      AND NULLIF(TRIM(trace_id), '') IS NOT NULL
      AND (@execution_log_through_trace IS NULL OR trace_id <= @execution_log_through_trace)
    ORDER BY trace_id LIMIT 500
  ) batch
);

-- INSERT SELECT 在 READ COMMITTED 下不持有审计源行锁，避免与在线“先身份后日志”锁序交叉。
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
START TRANSACTION;
INSERT INTO rule_execution_log_identity (trace_id, log_id, log_create_time)
SELECT log_row.trace_id, log_row.id, log_row.create_time
FROM rule_execution_log log_row
WHERE log_row.trace_id > @execution_log_after_trace
  AND log_row.trace_id <= @execution_log_batch_end
  AND NOT EXISTS (
    SELECT 1 FROM rule_execution_log newer
    WHERE newer.trace_id = log_row.trace_id
      AND (COALESCE(newer.attempt_no, 1) > COALESCE(log_row.attempt_no, 1)
        OR (COALESCE(newer.attempt_no, 1) = COALESCE(log_row.attempt_no, 1)
          AND (newer.create_time > log_row.create_time
            OR (newer.create_time = log_row.create_time AND newer.id > log_row.id))))
  )
ON DUPLICATE KEY UPDATE trace_id = rule_execution_log_identity.trace_id;
COMMIT;
SELECT COALESCE(@execution_log_batch_end, @execution_log_after_trace) AS next_cursor,
       @execution_log_batch_end IS NULL AS completed;
