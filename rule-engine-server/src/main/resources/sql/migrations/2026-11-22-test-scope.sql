SET @test_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_mock_rule' AND column_name='project_id'), 'DO 0', 'ALTER TABLE `rule_mock_rule` ADD COLUMN `project_id` BIGINT DEFAULT NULL COMMENT ''生成规则所属项目主键，空值表示全局可用''');
PREPARE test_upgrade FROM @test_ddl;
EXECUTE test_upgrade;
DEALLOCATE PREPARE test_upgrade;

SET @test_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_definition' AND column_name='test_input_json'), 'DO 0', 'ALTER TABLE `rule_definition` ADD COLUMN `test_input_json` JSON DEFAULT NULL COMMENT ''规则测试的默认输入参数，空值表示按字段生成''');
PREPARE test_upgrade FROM @test_ddl;
EXECUTE test_upgrade;
DEALLOCATE PREPARE test_upgrade;

SET @test_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_variable' AND column_name='test_input_json'), 'DO 0', 'ALTER TABLE `rule_variable` ADD COLUMN `test_input_json` JSON DEFAULT NULL COMMENT ''变量在线测试的默认输入参数，空值表示按字段生成''');
PREPARE test_upgrade FROM @test_ddl;
EXECUTE test_upgrade;
DEALLOCATE PREPARE test_upgrade;
