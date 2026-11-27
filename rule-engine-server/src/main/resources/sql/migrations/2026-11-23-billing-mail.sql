-- 表与列由 server 自动升级。本脚本仅回填历史账单归属。
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
