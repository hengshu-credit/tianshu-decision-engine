-- 已有数据库在部署账户记录隔离版本前执行；不回填或推测历史记录的账户归属。
SET NAMES utf8mb4;
-- 系统常量身份由初始化维护，不接受业务请求指定。
SET @system_constant_column_sql = IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rule_variable' AND column_name = 'system_constant'),
  'SELECT 1',
  'ALTER TABLE rule_variable ADD COLUMN system_constant TINYINT NOT NULL DEFAULT 0'
);
PREPARE system_constant_column_stmt FROM @system_constant_column_sql;
EXECUTE system_constant_column_stmt;
DEALLOCATE PREPARE system_constant_column_stmt;

-- 审计记录账户归属：只新增列，不按名称或旧配置猜测账户。
SET @record_owner_sql = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rule_execution_log' AND column_name = 'actor_user_id'), 'SELECT 1', 'ALTER TABLE rule_execution_log ADD COLUMN actor_user_id BIGINT DEFAULT NULL');
PREPARE record_owner_stmt FROM @record_owner_sql;
EXECUTE record_owner_stmt;
DEALLOCATE PREPARE record_owner_stmt;
SET @record_owner_sql = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rule_runtime_call_log' AND column_name = 'actor_user_id'), 'SELECT 1', 'ALTER TABLE rule_runtime_call_log ADD COLUMN actor_user_id BIGINT DEFAULT NULL');
PREPARE record_owner_stmt FROM @record_owner_sql;
EXECUTE record_owner_stmt;
DEALLOCATE PREPARE record_owner_stmt;
SET @record_owner_sql = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rule_billing_record' AND column_name = 'actor_user_id'), 'SELECT 1', 'ALTER TABLE rule_billing_record ADD COLUMN actor_user_id BIGINT DEFAULT NULL');
PREPARE record_owner_stmt FROM @record_owner_sql;
EXECUTE record_owner_stmt;
DEALLOCATE PREPARE record_owner_stmt;
SET @record_owner_sql = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rule_experiment_execution_log' AND column_name = 'actor_user_id'), 'SELECT 1', 'ALTER TABLE rule_experiment_execution_log ADD COLUMN actor_user_id BIGINT DEFAULT NULL');
PREPARE record_owner_stmt FROM @record_owner_sql;
EXECUTE record_owner_stmt;
DEALLOCATE PREPARE record_owner_stmt;
SET @record_owner_sql = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rule_list_record_log' AND column_name = 'actor_user_id'), 'SELECT 1', 'ALTER TABLE rule_list_record_log ADD COLUMN actor_user_id BIGINT DEFAULT NULL');
PREPARE record_owner_stmt FROM @record_owner_sql;
EXECUTE record_owner_stmt;
DEALLOCATE PREPARE record_owner_stmt;
SET @record_owner_sql = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'offline_transfer_log' AND column_name = 'actor_user_id'), 'SELECT 1', 'ALTER TABLE offline_transfer_log ADD COLUMN actor_user_id BIGINT DEFAULT NULL');
PREPARE record_owner_stmt FROM @record_owner_sql;
EXECUTE record_owner_stmt;
DEALLOCATE PREPARE record_owner_stmt;
SET @record_owner_sql = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'governance_approval_request' AND column_name = 'actor_user_id'), 'SELECT 1', 'ALTER TABLE governance_approval_request ADD COLUMN actor_user_id BIGINT DEFAULT NULL');
PREPARE record_owner_stmt FROM @record_owner_sql;
EXECUTE record_owner_stmt;
DEALLOCATE PREPARE record_owner_stmt;
SET @record_owner_sql = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'governed_resource_version' AND column_name = 'actor_user_id'), 'SELECT 1', 'ALTER TABLE governed_resource_version ADD COLUMN actor_user_id BIGINT DEFAULT NULL');
PREPARE record_owner_stmt FROM @record_owner_sql;
EXECUTE record_owner_stmt;
DEALLOCATE PREPARE record_owner_stmt;
SET @record_owner_sql = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rule_definition_version' AND column_name = 'actor_user_id'), 'SELECT 1', 'ALTER TABLE rule_definition_version ADD COLUMN actor_user_id BIGINT DEFAULT NULL');
PREPARE record_owner_stmt FROM @record_owner_sql;
EXECUTE record_owner_stmt;
DEALLOCATE PREPARE record_owner_stmt;
SET @record_owner_sql = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rule_model_version' AND column_name = 'actor_user_id'), 'SELECT 1', 'ALTER TABLE rule_model_version ADD COLUMN actor_user_id BIGINT DEFAULT NULL');
PREPARE record_owner_stmt FROM @record_owner_sql;
EXECUTE record_owner_stmt;
DEALLOCATE PREPARE record_owner_stmt;
SET @record_owner_sql = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rule_function_version' AND column_name = 'actor_user_id'), 'SELECT 1', 'ALTER TABLE rule_function_version ADD COLUMN actor_user_id BIGINT DEFAULT NULL');
PREPARE record_owner_stmt FROM @record_owner_sql;
EXECUTE record_owner_stmt;
DEALLOCATE PREPARE record_owner_stmt;
SET @record_owner_sql = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rule_experiment_version' AND column_name = 'actor_user_id'), 'SELECT 1', 'ALTER TABLE rule_experiment_version ADD COLUMN actor_user_id BIGINT DEFAULT NULL');
PREPARE record_owner_stmt FROM @record_owner_sql;
EXECUTE record_owner_stmt;
DEALLOCATE PREPARE record_owner_stmt;
SET @record_owner_sql = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rule_revision' AND column_name = 'actor_user_id'), 'SELECT 1', 'ALTER TABLE rule_revision ADD COLUMN actor_user_id BIGINT DEFAULT NULL');
PREPARE record_owner_stmt FROM @record_owner_sql;
EXECUTE record_owner_stmt;
DEALLOCATE PREPARE record_owner_stmt;
SET @record_owner_sql = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rule_auth_access_log' AND column_name = 'actor_user_id'), 'SELECT 1', 'ALTER TABLE rule_auth_access_log ADD COLUMN actor_user_id BIGINT DEFAULT NULL');
PREPARE record_owner_stmt FROM @record_owner_sql;
EXECUTE record_owner_stmt;
DEALLOCATE PREPARE record_owner_stmt;
SET @record_owner_sql = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'rule_billing_summary' AND column_name = 'actor_user_id'), 'SELECT 1', 'ALTER TABLE rule_billing_summary ADD COLUMN actor_user_id BIGINT DEFAULT NULL');
PREPARE record_owner_stmt FROM @record_owner_sql;
EXECUTE record_owner_stmt;
DEALLOCATE PREPARE record_owner_stmt;
SET @record_owner_sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'rule_billing_summary' AND index_name = 'uk_billing_summary_key' AND column_name IN ('actor_user_id', 'project_id')) = 2, 'SELECT 1', 'ALTER TABLE rule_billing_summary DROP INDEX uk_billing_summary_key, ADD UNIQUE KEY uk_billing_summary_key (actor_user_id, summary_date, project_id, project_code, billing_code, billing_target, target_ref_id, auth_id, currency)');
PREPARE record_owner_stmt FROM @record_owner_sql;
EXECUTE record_owner_stmt;
DEALLOCATE PREPARE record_owner_stmt;
