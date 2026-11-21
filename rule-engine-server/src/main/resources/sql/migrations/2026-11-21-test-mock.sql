CREATE TABLE IF NOT EXISTS `rule_mock_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '可复用的测试数据生成规则主键',
  `seed_key` VARCHAR(32) DEFAULT NULL COMMENT '内置规则的稳定初始化标识，自定义规则为空',
  `name` VARCHAR(128) NOT NULL COMMENT '业务人员选择生成方式时看到的名称',
  `value_type` VARCHAR(24) NOT NULL COMMENT '生成结果的逻辑类型，用于限制字段匹配',
  `match_names` VARCHAR(512) NOT NULL DEFAULT '' COMMENT '自动匹配的字段名称或中文标签，多个用逗号分隔，星号匹配整个类型',
  `generator` VARCHAR(24) NOT NULL COMMENT '固定值、候选值、范围或业务格式生成方式',
  `config_json` JSON NOT NULL COMMENT '生成器使用的固定值、候选列表或数值边界',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_mock_seed` (`seed_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试输入的通用类型及业务字段生成规则';

SET @mock_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_variable' AND column_name='mock_rule_id'), 'DO 0', 'ALTER TABLE `rule_variable` ADD `mock_rule_id` BIGINT DEFAULT NULL COMMENT ''变量绑定的测试生成规则主键，空值使用自动匹配''');
PREPARE mock_upgrade FROM @mock_ddl;
EXECUTE mock_upgrade;
DEALLOCATE PREPARE mock_upgrade;
SET @mock_ddl = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='rule_variable' AND constraint_name='fk_variable_mock_rule'), 'DO 0', 'ALTER TABLE `rule_variable` ADD CONSTRAINT `fk_variable_mock_rule` FOREIGN KEY (`mock_rule_id`) REFERENCES `rule_mock_rule` (`id`) ON DELETE RESTRICT');
PREPARE mock_upgrade FROM @mock_ddl;
EXECUTE mock_upgrade;
DEALLOCATE PREPARE mock_upgrade;
SET @mock_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_data_object' AND column_name='mock_rule_id'), 'DO 0', 'ALTER TABLE `rule_data_object` ADD `mock_rule_id` BIGINT DEFAULT NULL COMMENT ''数据对象整体绑定的测试生成规则主键，空值使用自动匹配''');
PREPARE mock_upgrade FROM @mock_ddl;
EXECUTE mock_upgrade;
DEALLOCATE PREPARE mock_upgrade;
SET @mock_ddl = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='rule_data_object' AND constraint_name='fk_object_mock_rule'), 'DO 0', 'ALTER TABLE `rule_data_object` ADD CONSTRAINT `fk_object_mock_rule` FOREIGN KEY (`mock_rule_id`) REFERENCES `rule_mock_rule` (`id`) ON DELETE RESTRICT');
PREPARE mock_upgrade FROM @mock_ddl;
EXECUTE mock_upgrade;
DEALLOCATE PREPARE mock_upgrade;
SET @mock_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_data_object_field' AND column_name='mock_rule_id'), 'DO 0', 'ALTER TABLE `rule_data_object_field` ADD `mock_rule_id` BIGINT DEFAULT NULL COMMENT ''对象子字段绑定的测试生成规则主键，空值使用自动匹配''');
PREPARE mock_upgrade FROM @mock_ddl;
EXECUTE mock_upgrade;
DEALLOCATE PREPARE mock_upgrade;
SET @mock_ddl = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='rule_data_object_field' AND constraint_name='fk_field_mock_rule'), 'DO 0', 'ALTER TABLE `rule_data_object_field` ADD CONSTRAINT `fk_field_mock_rule` FOREIGN KEY (`mock_rule_id`) REFERENCES `rule_mock_rule` (`id`) ON DELETE RESTRICT');
PREPARE mock_upgrade FROM @mock_ddl;
EXECUTE mock_upgrade;
DEALLOCATE PREPARE mock_upgrade;
