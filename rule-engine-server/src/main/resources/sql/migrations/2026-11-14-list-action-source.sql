-- 升级前备份并停止应用。既有名单和日志保留原始来源信息。
SET @add_source = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='rule_list_action_receipt' AND column_name='action_source'),
  'SELECT 1', 'ALTER TABLE rule_list_action_receipt ADD COLUMN `action_source` JSON DEFAULT NULL COMMENT ''规则名单动作来源快照，含触发规则ID与追踪ID、实验组及根规则执行结果''');
PREPARE migrate_source FROM @add_source;
EXECUTE migrate_source;
DEALLOCATE PREPARE migrate_source;

SET @add_source = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='rule_list_record_log' AND column_name='action_source'),
  'SELECT 1', 'ALTER TABLE rule_list_record_log ADD COLUMN `action_source` JSON DEFAULT NULL COMMENT ''规则名单动作来源快照，含触发规则ID与追踪ID、实验组及根规则执行结果''');
PREPARE migrate_source FROM @add_source;
EXECUTE migrate_source;
DEALLOCATE PREPARE migrate_source;
