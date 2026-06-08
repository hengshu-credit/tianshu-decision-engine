-- ============================================================
-- 迁移 SQL：为模型出入参表添加 var_id 和 script_name 字段
-- 用于关联变量管理中的变量（通过外键 var_id -> rule_variable.id）
-- ============================================================

-- 1. rule_model_input_field 添加字段
ALTER TABLE `rule_model_input_field`
  ADD COLUMN `var_id` BIGINT DEFAULT NULL COMMENT '关联的变量ID（外键 -> rule_variable.id）' AFTER `model_id`,
  ADD COLUMN `script_name` VARCHAR(128) DEFAULT NULL COMMENT '脚本中的引用名（驼峰）' AFTER `var_id`,
  ADD KEY `idx_input_var_id` (`var_id`);

-- 2. rule_model_output_field 添加字段
ALTER TABLE `rule_model_output_field`
  ADD COLUMN `var_id` BIGINT DEFAULT NULL COMMENT '关联的变量ID（外键 -> rule_variable.id）' AFTER `model_id`,
  ADD COLUMN `script_name` VARCHAR(128) DEFAULT NULL COMMENT '脚本中的引用名（驼峰）' AFTER `var_id`,
  ADD KEY `idx_output_var_id` (`var_id`);