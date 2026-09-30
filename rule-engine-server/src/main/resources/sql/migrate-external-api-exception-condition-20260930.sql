-- 为外数接口增加可配置异常响应条件树。
SET @schema_name = DATABASE();
SET @table_name = 'rule_external_api_config';
SET @column_name = 'exception_condition';
SET @column_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @schema_name AND table_name = @table_name AND column_name = @column_name
);
SET @sql = IF(@column_exists = 0,
  'ALTER TABLE `rule_external_api_config` ADD COLUMN `exception_condition` JSON DEFAULT NULL COMMENT ''接口异常响应条件树JSON'' AFTER `success_condition`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
