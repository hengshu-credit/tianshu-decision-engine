-- 原日志行同时保存待投递状态，存量记录在启用 ES 后按批次补齐。
SET @es_ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_runtime_call_log' AND column_name='es_body_aliases'), 'DO 0', 'ALTER TABLE `rule_runtime_call_log` ADD COLUMN `es_body_aliases` TINYINT NOT NULL DEFAULT 0 COMMENT ''相同大报文共用原文的标志，位一表示请求、位二表示响应与相应原始留存副本相同''');
PREPARE es_stmt FROM @es_ddl;
EXECUTE es_stmt;
DEALLOCATE PREPARE es_stmt;

SET @es_ddl = IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='rule_execution_log' AND index_name='idx_execution_history_project'), 'DO 0', 'ALTER TABLE `rule_execution_log` ADD INDEX `idx_execution_history_project` (`execution_project_id`,`started_at`,`root_rule_id`,`id`)');
PREPARE es_stmt FROM @es_ddl;
EXECUTE es_stmt;
DEALLOCATE PREPARE es_stmt;

SET @es_ddl = IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='rule_execution_log' AND index_name='idx_execution_history_window'), 'DO 0', 'ALTER TABLE `rule_execution_log` ADD INDEX `idx_execution_history_window` (`started_at`,`id`)');
PREPARE es_stmt FROM @es_ddl;
EXECUTE es_stmt;
DEALLOCATE PREPARE es_stmt;

SET @es_ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_execution_log' AND column_name='es_revision'), 'DO 0', 'ALTER TABLE `rule_execution_log` ADD COLUMN `es_revision` BIGINT NOT NULL DEFAULT 1 COMMENT ''当前日志内容的检索修订号，同一次执行被覆盖时递增''');
PREPARE es_stmt FROM @es_ddl;
EXECUTE es_stmt;
DEALLOCATE PREPARE es_stmt;

SET @es_ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_execution_log' AND column_name='es_due_at'), 'DO 0', 'ALTER TABLE `rule_execution_log` ADD COLUMN `es_due_at` DATETIME(6) DEFAULT ''2000-01-01 00:00:00'' COMMENT ''下次可领取检索投递的数据库时间，空表示本修订已写入全部索引''');
PREPARE es_stmt FROM @es_ddl;
EXECUTE es_stmt;
DEALLOCATE PREPARE es_stmt;

SET @es_ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_execution_log' AND column_name='es_attempts'), 'DO 0', 'ALTER TABLE `rule_execution_log` ADD COLUMN `es_attempts` INT NOT NULL DEFAULT 0 COMMENT ''当前修订的检索投递领取次数，用于租约校验和失败退避''');
PREPARE es_stmt FROM @es_ddl;
EXECUTE es_stmt;
DEALLOCATE PREPARE es_stmt;

SET @es_ddl = IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='rule_execution_log' AND index_name='idx_es_due'), 'DO 0', 'ALTER TABLE `rule_execution_log` ADD INDEX `idx_es_due` (`es_due_at`, `id`)');
PREPARE es_stmt FROM @es_ddl;
EXECUTE es_stmt;
DEALLOCATE PREPARE es_stmt;

SET @es_ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_runtime_call_log' AND column_name='es_revision'), 'DO 0', 'ALTER TABLE `rule_runtime_call_log` ADD COLUMN `es_revision` BIGINT NOT NULL DEFAULT 1 COMMENT ''当前日志内容的检索修订号，同一次执行被覆盖时递增''');
PREPARE es_stmt FROM @es_ddl;
EXECUTE es_stmt;
DEALLOCATE PREPARE es_stmt;

SET @es_ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_runtime_call_log' AND column_name='es_due_at'), 'DO 0', 'ALTER TABLE `rule_runtime_call_log` ADD COLUMN `es_due_at` DATETIME(6) DEFAULT ''2000-01-01 00:00:00'' COMMENT ''下次可领取检索投递的数据库时间，空表示本修订已写入全部索引''');
PREPARE es_stmt FROM @es_ddl;
EXECUTE es_stmt;
DEALLOCATE PREPARE es_stmt;

SET @es_ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_runtime_call_log' AND column_name='es_attempts'), 'DO 0', 'ALTER TABLE `rule_runtime_call_log` ADD COLUMN `es_attempts` INT NOT NULL DEFAULT 0 COMMENT ''当前修订的检索投递领取次数，用于租约校验和失败退避''');
PREPARE es_stmt FROM @es_ddl;
EXECUTE es_stmt;
DEALLOCATE PREPARE es_stmt;

SET @es_ddl = IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='rule_runtime_call_log' AND index_name='idx_es_due'), 'DO 0', 'ALTER TABLE `rule_runtime_call_log` ADD INDEX `idx_es_due` (`es_due_at`, `id`)');
PREPARE es_stmt FROM @es_ddl;
EXECUTE es_stmt;
DEALLOCATE PREPARE es_stmt;

SET @es_ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_execution_log' AND column_name='es_trace_archive'), 'DO 0', 'ALTER TABLE `rule_execution_log` ADD COLUMN `es_trace_archive` LONGBLOB DEFAULT NULL COMMENT ''检索确认后无损压缩的执行追踪正文，供重建索引和数据库回退读取''');
PREPARE es_stmt FROM @es_ddl;
EXECUTE es_stmt;
DEALLOCATE PREPARE es_stmt;
