-- 在停应用、备份之后执行，历史订单保留原请求与状态。
SET @add_dispatch = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='rule_execution_state' AND column_name='dispatch_json'),
  'SELECT 1', 'ALTER TABLE rule_execution_state ADD COLUMN `dispatch_json` JSON DEFAULT NULL COMMENT ''服务端接单上下文，保存鉴权标识、执行方式及恢复选项，不含凭据''');
PREPARE migrate_dispatch FROM @add_dispatch;
EXECUTE migrate_dispatch;
DEALLOCATE PREPARE migrate_dispatch;
ALTER TABLE rule_execution_state MODIFY COLUMN `status` VARCHAR(32) NOT NULL COMMENT '规则订单状态：QUEUED待执行、RUNNING执行中、SUCCEEDED成功、WAITING_EXTERNAL等待外数、UNKNOWN_RETRYABLE待核实、FAILED_RETRYABLE可重试';

SET @add_dispatch = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='rule_experiment_execution_state' AND column_name='dispatch_json'),
  'SELECT 1', 'ALTER TABLE rule_experiment_execution_state ADD COLUMN `dispatch_json` JSON DEFAULT NULL COMMENT ''服务端接单上下文，保存鉴权标识、执行方式及恢复选项，不含凭据''');
PREPARE migrate_dispatch FROM @add_dispatch;
EXECUTE migrate_dispatch;
DEALLOCATE PREPARE migrate_dispatch;
ALTER TABLE rule_experiment_execution_state MODIFY COLUMN `status` VARCHAR(32) NOT NULL COMMENT '实验订单状态：QUEUED待执行、RUNNING执行中、SUCCEEDED已完成、WAITING_EXTERNAL等待外数、FAILED_RETRYABLE可重试';

-- 只修改新测试组的缺省行为，已有显式开关照常生效。
ALTER TABLE rule_experiment_group ALTER COLUMN invoke_external_source SET DEFAULT 0;

ALTER TABLE rule_experiment_execution_log
  MODIFY COLUMN input_params LONGTEXT DEFAULT NULL COMMENT '分流实验组解析后的完整输入参数，JSON格式',
  MODIFY COLUMN output_result LONGTEXT DEFAULT NULL COMMENT '分流实验组的完整业务执行结果，JSON格式';
