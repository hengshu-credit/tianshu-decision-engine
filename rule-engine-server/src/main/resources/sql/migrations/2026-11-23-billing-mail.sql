SET @bill_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_billing_config' AND column_name='bill_email'), 'DO 0', 'ALTER TABLE `rule_billing_config` ADD COLUMN `bill_email` VARCHAR(254) DEFAULT NULL COMMENT ''审批生效的月度账单收件邮箱，空值表示关闭发送''');
PREPARE bill_stmt FROM @bill_ddl;
EXECUTE bill_stmt;
DEALLOCATE PREPARE bill_stmt;

SET @bill_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_billing_summary' AND column_name='billing_config_id'), 'DO 0', 'ALTER TABLE `rule_billing_summary` ADD COLUMN `billing_config_id` BIGINT DEFAULT NULL COMMENT ''汇总明细共同引用的计费配置主键，历史或混合来源可为空''');
PREPARE bill_stmt FROM @bill_ddl;
EXECUTE bill_stmt;
DEALLOCATE PREPARE bill_stmt;

SET @bill_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_billing_summary' AND column_name='request_count'), 'DO 0', 'ALTER TABLE `rule_billing_summary` ADD COLUMN `request_count` BIGINT DEFAULT NULL COMMENT ''当日按请求标识去重的请求数，缺少标识时为空''');
PREPARE bill_stmt FROM @bill_ddl;
EXECUTE bill_stmt;
DEALLOCATE PREPARE bill_stmt;

SET @bill_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_billing_summary' AND column_name='charged_count'), 'DO 0', 'ALTER TABLE `rule_billing_summary` ADD COLUMN `charged_count` BIGINT DEFAULT NULL COMMENT ''当日计费数量大于零的明细条数''');
PREPARE bill_stmt FROM @bill_ddl;
EXECUTE bill_stmt;
DEALLOCATE PREPARE bill_stmt;

SET @bill_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_billing_summary' AND column_name='billed_unit_price'), 'DO 0', 'ALTER TABLE `rule_billing_summary` ADD COLUMN `billed_unit_price` DECIMAL(18,6) DEFAULT NULL COMMENT ''计费明细的共同单价，存在多种单价时为空''');
PREPARE bill_stmt FROM @bill_ddl;
EXECUTE bill_stmt;
DEALLOCATE PREPARE bill_stmt;

-- 旧版外数专属计费明细没有持久化配置主键。仅回填由真实 API ID 派生的专属编码，
-- 要求配置先于调用存在、项目范围匹配且没有其他可能的计费项。草稿试运行保持原样。
UPDATE rule_billing_record r
JOIN rule_billing_config c ON c.billing_target='API' AND c.target_ref_id=r.target_ref_id
 AND BINARY c.billing_code=BINARY r.billing_code AND c.create_time<=r.occur_time
 AND (c.scope='GLOBAL' OR c.project_id=r.project_id)
LEFT JOIN rule_billing_config other ON other.id<>c.id AND other.billing_target='API'
 AND BINARY other.billing_code=BINARY r.billing_code AND other.create_time<=r.occur_time
 AND (other.target_ref_id IS NULL OR other.target_ref_id=r.target_ref_id)
 AND (other.scope='GLOBAL' OR other.project_id=r.project_id)
SET r.billing_config_id=c.id
WHERE r.billing_config_id IS NULL AND r.billing_basis IS NULL AND r.billing_target='API'
 AND BINARY r.billing_code=BINARY CONCAT('API_',r.target_ref_id) AND other.id IS NULL;
