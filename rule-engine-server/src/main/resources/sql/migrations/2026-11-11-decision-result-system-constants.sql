-- 已有业务库补齐决策结果系统常量。前置：rule_variable 已有 system_constant 和 uk_scope_project_var。
-- 旧版本若缺少 system_constant，先执行适用的 2026-11-04-account-record-isolation.sql。
-- 可重复执行；只插入缺失项，不覆盖已有变量、常量、引用 ID 或看板配置。
SET NAMES utf8mb4;

-- 决策结果系统常量：默认初始化即提供，不依赖可选业务数据或样例导入。
-- 已有同名业务字段不自动改值、改 ID 或改为系统常量；冲突须由管理员核对。
INSERT INTO `rule_variable`
  (`system_constant`, `project_id`, `scope`, `var_code`, `var_label`, `script_name`, `var_type`, `var_source`,
   `default_value`, `value_range`, `example_value`, `description`, `sort_order`,
   `status`, `create_time`, `update_time`)
SELECT 1, 0, 'GLOBAL', 'DECISION_PASS', '通过', 'DECISION_PASS', 'NUMBER', 'CONSTANT',
       '0', NULL, '0', '系统常量：决策结果为通过。', -989, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `rule_variable`
  (`system_constant`, `project_id`, `scope`, `var_code`, `var_label`, `script_name`, `var_type`, `var_source`,
   `default_value`, `value_range`, `example_value`, `description`, `sort_order`,
   `status`, `create_time`, `update_time`)
SELECT 1, 0, 'GLOBAL', 'DECISION_REVIEW', '人审', 'DECISION_REVIEW', 'NUMBER', 'CONSTANT',
       '2', NULL, '2', '系统常量：决策结果为人审。', -988, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `rule_variable`
  (`system_constant`, `project_id`, `scope`, `var_code`, `var_label`, `script_name`, `var_type`, `var_source`,
   `default_value`, `value_range`, `example_value`, `description`, `sort_order`,
   `status`, `create_time`, `update_time`)
SELECT 1, 0, 'GLOBAL', 'DECISION_REJECT', '拒绝', 'DECISION_REJECT', 'NUMBER', 'CONSTANT',
       '1', NULL, '1', '系统常量：决策结果为拒绝。', -987, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
ON DUPLICATE KEY UPDATE `id` = `id`;
-- 决策结果系统常量结束。

-- 核对三个编码均为启用的全局数值系统常量；若已有同名业务字段则保留原样，由管理员处理冲突。
SELECT id, var_code, var_label, var_type, var_source, default_value, system_constant, status
FROM rule_variable
WHERE scope = 'GLOBAL' AND project_id = 0
  AND var_code IN ('DECISION_PASS', 'DECISION_REVIEW', 'DECISION_REJECT')
ORDER BY sort_order;
