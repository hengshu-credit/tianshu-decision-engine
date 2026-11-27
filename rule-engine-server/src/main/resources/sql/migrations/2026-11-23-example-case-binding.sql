SET @case_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_variable' AND column_name='example_case_binding'), 'DO 0', 'ALTER TABLE `rule_variable` ADD `example_case_binding` JSON DEFAULT NULL COMMENT ''绑定测试用例的资源类型、用例主键与输入值 JSON Pointer 路径''');
PREPARE case_stmt FROM @case_ddl;
EXECUTE case_stmt;
DEALLOCATE PREPARE case_stmt;
ALTER TABLE `rule_variable` MODIFY `example_mode` VARCHAR(12) DEFAULT NULL COMMENT '测试输入的示例方式：AUTO 自动、FIXED 固定、RULE 指定 Mock、CASE 绑定测试用例、NONE 不生成';

SET @case_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_data_object' AND column_name='example_case_binding'), 'DO 0', 'ALTER TABLE `rule_data_object` ADD `example_case_binding` JSON DEFAULT NULL COMMENT ''绑定测试用例的资源类型、用例主键与输入值 JSON Pointer 路径''');
PREPARE case_stmt FROM @case_ddl;
EXECUTE case_stmt;
DEALLOCATE PREPARE case_stmt;
ALTER TABLE `rule_data_object` MODIFY `example_mode` VARCHAR(12) DEFAULT NULL COMMENT '测试输入的示例方式：AUTO 自动、FIXED 固定、RULE 指定 Mock、CASE 绑定测试用例、NONE 不生成';

SET @case_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_data_object_field' AND column_name='example_case_binding'), 'DO 0', 'ALTER TABLE `rule_data_object_field` ADD `example_case_binding` JSON DEFAULT NULL COMMENT ''绑定测试用例的资源类型、用例主键与输入值 JSON Pointer 路径''');
PREPARE case_stmt FROM @case_ddl;
EXECUTE case_stmt;
DEALLOCATE PREPARE case_stmt;
ALTER TABLE `rule_data_object_field` MODIFY `example_mode` VARCHAR(12) DEFAULT NULL COMMENT '测试输入的示例方式：AUTO 自动、FIXED 固定、RULE 指定 Mock、CASE 绑定测试用例、NONE 不生成';

ALTER TABLE `rule_variable` MODIFY `example_value` LONGTEXT DEFAULT NULL COMMENT '测试输入的固定示例值，支持结构化对象和数组';
