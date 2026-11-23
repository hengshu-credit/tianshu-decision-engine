SET @bill_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_billing_config' AND column_name='bill_company_name'), 'DO 0', 'ALTER TABLE `rule_billing_config` ADD COLUMN `bill_company_name` VARCHAR(128) DEFAULT NULL COMMENT ''账单公司抬头，空值表示发送时使用已生效的系统名称''');
PREPARE bill_stmt FROM @bill_ddl;
EXECUTE bill_stmt;
DEALLOCATE PREPARE bill_stmt;
