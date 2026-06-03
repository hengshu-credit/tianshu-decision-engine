-- ============================================================
-- 变量和函数全局/项目级分离 - 数据库迁移脚本
-- ============================================================
-- 执行前请备份数据库！
-- ============================================================

USE `rule_engine`;

-- ============================================================
-- 1. rule_variable - 添加 scope 字段
-- ============================================================
ALTER TABLE `rule_variable`
    ADD COLUMN `scope` VARCHAR(16) NOT NULL DEFAULT 'PROJECT' COMMENT '作用域：GLOBAL-全局，PROJECT-项目级' AFTER `project_id`;

-- 修改唯一约束
ALTER TABLE `rule_variable`
    DROP INDEX `uk_project_var`,
    ADD UNIQUE KEY `uk_scope_project_var` (`scope`, `project_id`, `var_code`);

-- ============================================================
-- 2. rule_function - 添加 scope 字段
-- ============================================================
ALTER TABLE `rule_function`
    ADD COLUMN `scope` VARCHAR(16) NOT NULL DEFAULT 'PROJECT' COMMENT '作用域：GLOBAL-全局，PROJECT-项目级' AFTER `project_id`;

-- 修改唯一约束
ALTER TABLE `rule_function`
    DROP INDEX `uk_project_func`,
    ADD UNIQUE KEY `uk_scope_project_func` (`scope`, `project_id`, `func_code`);

-- ============================================================
-- 3. rule_data_object - 添加 scope 字段
-- ============================================================
ALTER TABLE `rule_data_object`
    ADD COLUMN `scope` VARCHAR(16) NOT NULL DEFAULT 'PROJECT' COMMENT '作用域：GLOBAL-全局，PROJECT-项目级' AFTER `project_id`;

-- 修改唯一约束
ALTER TABLE `rule_data_object`
    DROP INDEX `uk_project_object`,
    ADD UNIQUE KEY `uk_scope_project_object` (`scope`, `project_id`, `object_code`);

-- ============================================================
-- 4. rule_data_object_field - 添加 scope 字段
-- ============================================================
ALTER TABLE `rule_data_object_field`
    ADD COLUMN `scope` VARCHAR(16) NOT NULL DEFAULT 'PROJECT' COMMENT '作用域：GLOBAL-全局，PROJECT-项目级' AFTER `project_id`;

-- 修改唯一约束（原约束是 object_id + var_code，保持不变）

-- ============================================================
-- 5. 数据迁移：更新现有数据为 PROJECT 级别
-- ============================================================
UPDATE `rule_variable` SET scope = 'PROJECT' WHERE scope IS NULL OR scope = '';
UPDATE `rule_function` SET scope = 'PROJECT' WHERE scope IS NULL OR scope = '';
UPDATE `rule_data_object` SET scope = 'PROJECT' WHERE scope IS NULL OR scope = '';
UPDATE `rule_data_object_field` SET scope = 'PROJECT' WHERE scope IS NULL OR scope = '';

-- ============================================================
-- 6. 验证迁移结果
-- ============================================================
-- SELECT 'rule_variable scope distribution:' AS info;
-- SELECT scope, COUNT(*) as count FROM `rule_variable` GROUP BY scope;

-- SELECT 'rule_function scope distribution:' AS info;
-- SELECT scope, COUNT(*) as count FROM `rule_function` GROUP BY scope;

-- SELECT 'rule_data_object scope distribution:' AS info;
-- SELECT scope, COUNT(*) as count FROM `rule_data_object` GROUP BY scope;

-- SELECT 'rule_data_object_field scope distribution:' AS info;
-- SELECT scope, COUNT(*) as count FROM `rule_data_object_field` GROUP BY scope;