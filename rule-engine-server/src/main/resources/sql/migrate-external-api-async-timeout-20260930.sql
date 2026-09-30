USE rule_engine;
SET @migration_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule_external_api_config' AND COLUMN_NAME = 'async_timeout_ms') = 0,
  'ALTER TABLE rule_external_api_config ADD COLUMN async_timeout_ms INT NOT NULL DEFAULT 30000 COMMENT ''异步提交后轮询/回调等待预算毫秒'' AFTER timeout_ms', 'SELECT 1');
PREPARE api_async_timeout_migration FROM @migration_sql;
EXECUTE api_async_timeout_migration;
DEALLOCATE PREPARE api_async_timeout_migration;
