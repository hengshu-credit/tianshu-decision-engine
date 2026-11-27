SET @bill_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_billing_summary' AND column_name='min_unit_price'), 'DO 0', 'ALTER TABLE `rule_billing_summary` ADD COLUMN `min_unit_price` DECIMAL(18,6) DEFAULT NULL COMMENT ''该日汇总所含计费明细的最低实际单价''');
PREPARE bill_stmt FROM @bill_ddl;
EXECUTE bill_stmt;
DEALLOCATE PREPARE bill_stmt;

SET @bill_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_billing_summary' AND column_name='max_unit_price'), 'DO 0', 'ALTER TABLE `rule_billing_summary` ADD COLUMN `max_unit_price` DECIMAL(18,6) DEFAULT NULL COMMENT ''该日汇总所含计费明细的最高实际单价''');
PREPARE bill_stmt FROM @bill_ddl;
EXECUTE bill_stmt;
DEALLOCATE PREPARE bill_stmt;
