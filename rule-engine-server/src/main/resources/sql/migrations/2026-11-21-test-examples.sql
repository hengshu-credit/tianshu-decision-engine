CREATE TABLE IF NOT EXISTS `rule_mock_field_binding` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '生成方法主动关联字段的记录主键',
  `rule_id` BIGINT NOT NULL COMMENT '字段自动匹配时使用的生成方法主键',
  `variable_id` BIGINT DEFAULT NULL COMMENT '关联的输入变量主键',
  `object_id` BIGINT DEFAULT NULL COMMENT '关联的整体数据对象主键',
  `field_id` BIGINT DEFAULT NULL COMMENT '关联的数据对象子字段主键',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mock_bind_var` (`variable_id`),
  UNIQUE KEY `uk_mock_bind_obj` (`object_id`),
  UNIQUE KEY `uk_mock_bind_field` (`field_id`),
  CONSTRAINT `fk_mock_bind_method` FOREIGN KEY (`rule_id`) REFERENCES `rule_mock_rule` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_mock_bind_var` FOREIGN KEY (`variable_id`) REFERENCES `rule_variable` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_mock_bind_obj` FOREIGN KEY (`object_id`) REFERENCES `rule_data_object` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_mock_bind_field` FOREIGN KEY (`field_id`) REFERENCES `rule_data_object_field` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Mock 方法主动绑定的输入变量、对象和子字段';

SET @mock_example_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_variable' AND column_name='example_mode'), 'DO 0', 'ALTER TABLE `rule_variable` ADD `example_mode` VARCHAR(12) DEFAULT NULL COMMENT ''测试示例方式：AUTO 自动匹配、FIXED 固定值、RULE 指定方法、NONE 不生成，空值兼容已有配置''');
PREPARE mock_example_stmt FROM @mock_example_ddl;
EXECUTE mock_example_stmt;
DEALLOCATE PREPARE mock_example_stmt;
SET @mock_example_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_data_object' AND column_name='example_mode'), 'DO 0', 'ALTER TABLE `rule_data_object` ADD `example_mode` VARCHAR(12) DEFAULT NULL COMMENT ''测试示例方式：AUTO 自动匹配、FIXED 固定值、RULE 指定方法、NONE 不生成，空值兼容已有配置''');
PREPARE mock_example_stmt FROM @mock_example_ddl;
EXECUTE mock_example_stmt;
DEALLOCATE PREPARE mock_example_stmt;
SET @mock_example_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_data_object' AND column_name='example_value'), 'DO 0', 'ALTER TABLE `rule_data_object` ADD `example_value` LONGTEXT DEFAULT NULL COMMENT ''测试输入使用的固定示例值，保留配置文本和数字精度''');
PREPARE mock_example_stmt FROM @mock_example_ddl;
EXECUTE mock_example_stmt;
DEALLOCATE PREPARE mock_example_stmt;
SET @mock_example_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_data_object_field' AND column_name='example_mode'), 'DO 0', 'ALTER TABLE `rule_data_object_field` ADD `example_mode` VARCHAR(12) DEFAULT NULL COMMENT ''测试示例方式：AUTO 自动匹配、FIXED 固定值、RULE 指定方法、NONE 不生成，空值兼容已有配置''');
PREPARE mock_example_stmt FROM @mock_example_ddl;
EXECUTE mock_example_stmt;
DEALLOCATE PREPARE mock_example_stmt;
SET @mock_example_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_data_object_field' AND column_name='example_value'), 'DO 0', 'ALTER TABLE `rule_data_object_field` ADD `example_value` LONGTEXT DEFAULT NULL COMMENT ''测试输入使用的固定示例值，保留配置文本和数字精度''');
PREPARE mock_example_stmt FROM @mock_example_ddl;
EXECUTE mock_example_stmt;
DEALLOCATE PREPARE mock_example_stmt;

UPDATE rule_mock_rule SET match_names=IF(BINARY match_names=BINARY (CASE seed_key
  WHEN 'text' THEN '*'
  WHEN 'number' THEN '*'
  WHEN 'bool' THEN '*'
  WHEN 'date' THEN '*'
  WHEN 'list' THEN '*'
  WHEN 'mobile' THEN 'mobile,phone,phoneNumber,手机号,手机号码'
  WHEN 'email' THEN 'email,emailAddress,电子邮箱,邮箱'
  WHEN 'request' THEN 'requestId,traceId,请求流水号'
  WHEN 'age' THEN 'age,年龄'
  WHEN 'score' THEN 'creditScore,信用评分'
  WHEN 'amount' THEN 'amount,loanAmount,申请金额'
  ELSE '' END), '', match_names), seed_key=CONCAT('v2.',seed_key)
WHERE seed_key IN ('text', 'number', 'bool', 'date', 'list', 'mobile', 'email', 'request', 'age', 'score', 'amount', 'object');
