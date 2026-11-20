CREATE TABLE IF NOT EXISTS `rule_variable_test_case` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '测试用例主键',
  `variable_id` BIGINT NOT NULL COMMENT '被测变量的主键',
  `context_definition_id` BIGINT DEFAULT NULL COMMENT '统计历史进件时使用的根规则主键',
  `case_code` VARCHAR(64) NOT NULL COMMENT '资源内唯一的测试场景编码',
  `case_name` VARCHAR(128) NOT NULL COMMENT '供业务人员选择的测试场景名称',
  `params_json` JSON NOT NULL COMMENT '执行测试使用的上下文入参',
  `expected_json` JSON NOT NULL COMMENT '对业务输出的路径、条件和期望值断言',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_variable_test_code` (`variable_id`,`case_code`),
  CONSTRAINT `fk_variable_test_context` FOREIGN KEY (`context_definition_id`) REFERENCES `rule_definition` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_variable_test_owner` FOREIGN KEY (`variable_id`) REFERENCES `rule_variable` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='变量测试用例';

CREATE TABLE IF NOT EXISTS `rule_api_test_case` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '测试用例主键',
  `api_id` BIGINT NOT NULL COMMENT '被测外数接口的主键',
  `case_code` VARCHAR(64) NOT NULL COMMENT '资源内唯一的测试场景编码',
  `case_name` VARCHAR(128) NOT NULL COMMENT '供业务人员选择的测试场景名称',
  `params_json` JSON NOT NULL COMMENT '执行测试使用的上下文入参',
  `expected_json` JSON NOT NULL COMMENT '对业务输出的路径、条件和期望值断言',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_test_code` (`api_id`,`case_code`),
  CONSTRAINT `fk_api_test_owner` FOREIGN KEY (`api_id`) REFERENCES `rule_external_api_config` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外数接口测试用例';

CREATE TABLE IF NOT EXISTS `rule_model_test_case` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '测试用例主键',
  `model_id` BIGINT NOT NULL COMMENT '被测模型的主键',
  `case_code` VARCHAR(64) NOT NULL COMMENT '资源内唯一的测试场景编码',
  `case_name` VARCHAR(128) NOT NULL COMMENT '供业务人员选择的测试场景名称',
  `params_json` JSON NOT NULL COMMENT '执行测试使用的上下文入参',
  `expected_json` JSON NOT NULL COMMENT '对业务输出的路径、条件和期望值断言',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_test_code` (`model_id`,`case_code`),
  CONSTRAINT `fk_model_test_owner` FOREIGN KEY (`model_id`) REFERENCES `rule_model` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型测试用例';
