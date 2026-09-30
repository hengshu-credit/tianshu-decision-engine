USE rule_engine;
SET @migration_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule_external_api_config' AND COLUMN_NAME = 'execution_config') = 0,
  'ALTER TABLE rule_external_api_config ADD COLUMN execution_config LONGTEXT DEFAULT NULL', 'SELECT 1');
PREPARE api_execution_migration FROM @migration_sql;
EXECUTE api_execution_migration;
DEALLOCATE PREPARE api_execution_migration;
SET @migration_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule_data_object_field' AND COLUMN_NAME = 'source_config') = 0,
  'ALTER TABLE rule_data_object_field ADD COLUMN source_config LONGTEXT DEFAULT NULL', 'SELECT 1');
PREPARE api_execution_migration FROM @migration_sql;
EXECUTE api_execution_migration;
DEALLOCATE PREPARE api_execution_migration;
SET @migration_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule_data_object_field' AND COLUMN_NAME = 'source_path') = 0,
  'ALTER TABLE rule_data_object_field ADD COLUMN source_path VARCHAR(512) DEFAULT NULL', 'SELECT 1');
PREPARE api_execution_migration FROM @migration_sql;
EXECUTE api_execution_migration;
DEALLOCATE PREPARE api_execution_migration;
