-- 停应用并备份后执行。历史实验采用兜底行为，已有数据和分组原样保留。
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='rule_experiment' AND column_name='routing_error_policy'),
    'SELECT 1',
    'ALTER TABLE rule_experiment ADD COLUMN `routing_error_policy` VARCHAR(16) NOT NULL DEFAULT ''FALLBACK'' COMMENT ''生产分流异常策略：FALLBACK执行兜底组，PAUSE保留订单等待修复后恢复''');
PREPARE migrate_route_policy FROM @ddl;
EXECUTE migrate_route_policy;
DEALLOCATE PREPARE migrate_route_policy;
ALTER TABLE rule_experiment_execution_state MODIFY COLUMN status VARCHAR(32) NOT NULL
  COMMENT '实验订单状态：QUEUED待执行、RUNNING执行中、SUCCEEDED已完成、WAITING_EXTERNAL等待外数、PAUSED_ROUTING等待修复分流条件、FAILED_RETRYABLE可重试';
