USE rule_engine;

-- 外数结构化配置使用 JSON，便于按路径查询；脚本和纯文本请求体仍保留 LONGTEXT。
ALTER TABLE rule_external_datasource
  MODIFY COLUMN auth_config JSON DEFAULT NULL COMMENT '默认鉴权配置JSON';

ALTER TABLE rule_external_api_config
  MODIFY COLUMN execution_config JSON DEFAULT NULL COMMENT '统一外数请求响应与多步链路配置',
  MODIFY COLUMN header_config JSON DEFAULT NULL COMMENT '请求头配置JSON',
  MODIFY COLUMN query_config JSON DEFAULT NULL COMMENT 'Query参数配置JSON',
  MODIFY COLUMN request_mapping JSON DEFAULT NULL COMMENT '入参映射配置JSON',
  MODIFY COLUMN response_mapping JSON DEFAULT NULL COMMENT '响应映射配置JSON',
  MODIFY COLUMN payload_capture_config JSON DEFAULT NULL COMMENT '请求/响应诊断报文留存策略JSON',
  MODIFY COLUMN auth_api_config JSON DEFAULT NULL COMMENT '接口级鉴权与token获取配置JSON',
  MODIFY COLUMN cache_key_config JSON DEFAULT NULL COMMENT '缓存键组件配置JSON',
  MODIFY COLUMN success_condition JSON DEFAULT NULL COMMENT '请求成功响应条件树JSON',
  MODIFY COLUMN token_failure_condition JSON DEFAULT NULL COMMENT 'Token鉴权失败条件',
  MODIFY COLUMN retry_condition JSON DEFAULT NULL COMMENT '业务响应重试条件树JSON',
  MODIFY COLUMN async_poll_config JSON DEFAULT NULL COMMENT '异步轮询配置JSON',
  MODIFY COLUMN async_callback_config JSON DEFAULT NULL COMMENT '异步回调配置JSON',
  MODIFY COLUMN billing_condition JSON DEFAULT NULL COMMENT '计费条件JSON',
  MODIFY COLUMN fallback_value JSON DEFAULT NULL COMMENT '兜底返回值JSON',
  MODIFY COLUMN test_sample_params JSON DEFAULT NULL COMMENT 'API调用测试样例JSON';
