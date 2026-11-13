-- 在已有库停止应用后执行。历史索引小时版本从零开始，事务提交时递增。
CREATE TABLE IF NOT EXISTS `rule_history_bucket_revision` (
  `bucket_hour` DATETIME NOT NULL COMMENT '历史进件开始时间所在的本地小时',
  `execution_project_id` BIGINT NOT NULL COMMENT '根进件所属项目标识，无项目时为零',
  `root_rule_id` BIGINT NOT NULL COMMENT '根进件实际执行的规则标识，无归属时为零',
  `revision` BIGINT NOT NULL COMMENT '该小时根进件新增、替换或失效的累计变更序号',
  PRIMARY KEY (`execution_project_id`,`bucket_hour`,`root_rule_id`),
  KEY `idx_history_revision_time` (`bucket_hour`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史小时统计变更版本，用于验证精确空间统计缓存';

SET @add_request = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='rule_experiment_execution_state' AND column_name='request_json'),
  'SELECT 1', 'ALTER TABLE rule_experiment_execution_state ADD COLUMN `request_json` LONGTEXT NULL COMMENT ''用于按实验追踪号恢复的原始实验请求，旧记录可能为空''');
PREPARE migrate_request FROM @add_request;
EXECUTE migrate_request;
DEALLOCATE PREPARE migrate_request;
