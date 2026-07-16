-- 三方外数 API 配置模板（2026-07-16）
-- 说明：本文件可重复执行，会覆盖相同 datasource_code 的模板配置。
-- 所有数据源和 API 默认停用；REPLACE_BEFORE_ENABLE_* 必须在启用前替换。
-- 本文件不调用任何供应商接口，姓名/身份证/手机号/请求号统一取全局 code：
-- name / idcard_no / mobile_no / request_id。

USE rule_engine;

DELETE api
FROM rule_external_api_config api
JOIN rule_external_datasource ds ON ds.id = api.datasource_id
WHERE ds.datasource_code IN (
  'icekredit_qingyun', 'pudao_credit', 'rong360_zhanxin', 'tcredit_xingyao', 'baihang_attention',
  'shanhe_qilin', 'yinrong_tongzhi', 'xiaoke_tongfen', 'tianxing_credibility', 'jitui_mobile',
  'huadao_risk', 'hengzhi_score', 'bileizhen_tuzhi', 'baiwei_credit', 'bairong_special'
);

DELETE FROM rule_external_datasource
WHERE datasource_code IN (
  'icekredit_qingyun', 'pudao_credit', 'rong360_zhanxin', 'tcredit_xingyao', 'baihang_attention',
  'shanhe_qilin', 'yinrong_tongzhi', 'xiaoke_tongfen', 'tianxing_credibility', 'jitui_mobile',
  'huadao_risk', 'hengzhi_score', 'bileizhen_tuzhi', 'baiwei_credit', 'bairong_special'
);

INSERT INTO rule_external_datasource
  (id, project_id, scope, datasource_code, datasource_name, provider_name, protocol, base_url,
   auth_type, auth_config, token_cache_seconds, description, status, create_time, update_time)
VALUES
  (9201, 0, 'GLOBAL', 'icekredit_qingyun', '冰鉴个人评分平台', '冰鉴科技', 'HTTPS', 'https://api.icekredit.com',
   'TOKEN_API', '{"tokenUrl":"https://sso.icekredit.com/api/login.do","method":"POST","contentType":"application/x-www-form-urlencoded","headers":{},"body":{"merchant_name":"REPLACE_BEFORE_ENABLE_MERCHANT","merchant_pwd":"REPLACE_BEFORE_ENABLE_MD5_PASSWORD"},"tokenPath":"body.token_id","tokenPlacement":"HEADER","tokenHeaderName":"token_id","tokenPrefix":""}',
   3300, '昨日接口重构版。登录获取 token_id 后写入同名 Header；凭据为占位符。', 0, NOW(), NOW()),
  (9202, 0, 'GLOBAL', 'pudao_credit', '朴道征信与海纳数科', '朴道征信', 'HTTPS', 'https://api.xunxin-ai.com',
   'NONE', NULL, 0, '协议资料不完整，两个接口均保持停用。', 0, NOW(), NOW()),
  (9203, 0, 'GLOBAL', 'rong360_zhanxin', '融360占信分', '融360', 'HTTPS', 'https://openapi.rong360.com',
   'NONE', NULL, 0, 'RSA 签名原文规则缺失，保持停用。', 0, NOW(), NOW()),
  (9204, 0, 'GLOBAL', 'tcredit_xingyao', '天创信用司南', '天创信用', 'HTTPS', 'https://api.tcredit.com',
   'NONE', NULL, 0, '排序键值串加 MD5 已改用通用请求脚本。', 0, NOW(), NOW()),
  (9205, 0, 'GLOBAL', 'baihang_attention', '百行征信特别关注', '百行征信', 'HTTPS', 'https://replace-before-enable.invalid',
   'NONE', NULL, 0, '文档未给生产域名；3DES 与 HMAC-SHA1 已改用通用脚本，启用前补地址和密钥。', 0, NOW(), NOW()),
  (9206, 0, 'GLOBAL', 'shanhe_qilin', '山河麒麟分 V3', '山河汇聚', 'HTTPS', 'https://apisandbox.ishanhe.cn',
   'NONE', NULL, 0, '使用文档沙箱地址；启用前替换正式地址和商户凭据。', 0, NOW(), NOW()),
  (9207, 0, 'GLOBAL', 'yinrong_tongzhi', '通智分 B4', '银融致信', 'HTTP', 'http://replace-before-enable.invalid',
   'NONE', NULL, 0, '文档仅给 server:port，启用前替换联调地址。', 0, NOW(), NOW()),
  (9208, 0, 'GLOBAL', 'xiaoke_tongfen', '瞳分风险评分 V3', '效科数智', 'HTTPS', 'https://service.xksztech.com',
   'NONE', NULL, 0, 'Param URL 编码与双层 MD5 大写签名由请求脚本完成。', 0, NOW(), NOW()),
  (9209, 0, 'GLOBAL', 'tianxing_credibility', '可信度认证 IV', '天行数科', 'HTTPS', 'https://test.tianxingshuke.com',
   'NONE', NULL, 0, '使用测试地址与 SM3 身份证入参；文档提示测试环境也可能产生费用，因此保持停用。', 0, NOW(), NOW()),
  (9210, 0, 'GLOBAL', 'jitui_mobile', '手机在网时长 V2', '极推科技', 'HTTPS', 'https://replace-before-enable.invalid',
   'NONE', NULL, 0, '文档未给域名，启用前替换；手机号按 MD5 方式配置。', 0, NOW(), NOW()),
  (9211, 0, 'GLOBAL', 'huadao_risk', 'HDB010 风险名单', '华道征信', 'HTTP', 'http://opensdk.bihootech.com',
   'TOKEN_API', '{"tokenUrl":"/HD_GetAccess_Token.asmx/GetACCESS_TOKEN","method":"POST","contentType":"application/x-www-form-urlencoded","headers":{},"body":{"AppID":"REPLACE_BEFORE_ENABLE_APP_ID","AppSecret":"REPLACE_BEFORE_ENABLE_APP_SECRET"},"tokenPath":"body.access_token","tokenPlacement":"SCRIPT_ONLY","tokenResponseScript":"_json = strRegexExtract(rawBody, \\"<string[^>]*>(.*)</string>\\", 1); jsonParse(_json)"}',
   86400, 'XML 包装 Token 由响应脚本解包，Token 仅供业务请求脚本写入表单。', 0, NOW(), NOW()),
  (9212, 0, 'GLOBAL', 'hengzhi_score', '信用评分 Score_b22', '恒智普惠', 'HTTPS', 'https://apigateway.hzphfin.com',
   'NONE', NULL, 0, '账号与密码通过脚本变量配置。', 0, NOW(), NOW()),
  (9213, 0, 'GLOBAL', 'bileizhen_tuzhi', '图智分 04', '避雷针', 'HTTPS', 'https://replace-before-enable.invalid',
   'NONE', NULL, 0, '域名未提供；路径 appId、动态 Header 与请求体签名均配置化。', 0, NOW(), NOW()),
  (9214, 0, 'GLOBAL', 'baiwei_credit', '信用分升级版 2.0', '百维金科', 'HTTPS', 'https://api.bwfintech.cn',
   'NONE', NULL, 0, '协议缺少精确密钥格式、签名算法和验签向量，不可启用；保留可审查脚本骨架。', 0, NOW(), NOW()),
  (9215, 0, 'GLOBAL', 'bairong_special', '百融特殊名单', '百融云创', 'HTTPS', 'https://sandbox-api2.100credit.cn',
   'TOKEN_API', '{"tokenUrl":"/bankServer2/user/login.action","method":"POST","contentType":"application/x-www-form-urlencoded","headers":{},"body":{"userName":"REPLACE_BEFORE_ENABLE_USERNAME","password":"REPLACE_BEFORE_ENABLE_PASSWORD","apiCode":"REPLACE_BEFORE_ENABLE_API_CODE"},"tokenPath":"body.tokenid","tokenPlacement":"SCRIPT_ONLY"}',
   3300, '登录 Token 仅供脚本构造 tokenid/checkCode，默认使用沙箱地址。', 0, NOW(), NOW());

INSERT INTO rule_external_api_config
  (id, datasource_id, api_code, api_name, request_method, endpoint_url, content_type, request_mode,
   header_config, query_config, request_mapping, response_mapping, body_template,
   request_script, response_script, auth_mode, auth_api_config, token_cache_seconds,
   response_cache_seconds, timeout_ms, retry_count, retry_interval_ms, exception_strategy,
   fallback_value, billing_condition, unit_price, description, test_sample_params, status,
   create_time, update_time)
VALUES
  (9301, 9201, 'qingyun24_score', '冰鉴青云分 2.4', 'POST', '/person-ds/credit/QingYun24', 'application/json', 'SYNC',
   NULL, NULL, '{"name":"$.name","id":"$.idcard_no","mobile":"$.mobile_no"}',
   '{"code":"body.code","score":"body.result.qing_yun24.bj_score"}', NULL,
   NULL, NULL, 'INHERIT', NULL, 3300, 0, 3000, 0, 200, 'FAIL_FAST', NULL,
   '{"mode":"HIT","path":"body.code","operator":"==","value":"00"}', 0,
   '昨日接口重构版；不实际调用，启用前替换登录凭据。',
   '{"name":"张三","idcard_no":"110101199001011234","mobile_no":"13800138000","request_id":"REQ0001"}', 0, NOW(), NOW()),

  (9302, 9202, 'haina_performance_index_140', '海纳履约指数 140', 'POST', '/product/encrypt/negative/v4/black', 'application/x-www-form-urlencoded', 'SYNC',
   NULL, NULL, '{"data_content":"$.idcard_no"}', '{"code":"body.code","data":"body.data"}', NULL,
   'apiPut(body, "member_id", mapGet(vars, "memberId")); apiPut(body, "data_type", mapGet(vars, "dataType")); body', NULL,
   'NONE', '{"scriptVariables":[{"name":"memberId","value":"REPLACE_BEFORE_ENABLE_MEMBER_ID","sensitive":true},{"name":"dataType","value":"REPLACE_BEFORE_ENABLE_DATA_TYPE","sensitive":false}]}',
   0, 0, 3000, 0, 200, 'FAIL_FAST', NULL, NULL, 0,
   'data_content 加密规则未提供，不可启用。',
   '{"name":"张三","idcard_no":"110101199001011234","mobile_no":"13800138000","request_id":"REQ0002"}', 0, NOW(), NOW()),

  (9303, 9202, 'pudao_sm_product_query', '朴道国密产品查询', 'POST', 'https://api.pudaocredit.com.cn/v1/credit/at/prodservice', 'application/json', 'SYNC',
   '{"X-PUDAO-SGN":"REPLACE_BEFORE_ENABLE_SIGNATURE"}', NULL,
   '{"rqstHead":{"appId":"REPLACE_BEFORE_ENABLE_APP_ID","productCode":"REPLACE_BEFORE_ENABLE_PRODUCT_CODE"},"rqstBody":{"name":"$.name","idCard":"$.idcard_no","mobile":"$.mobile_no"}}',
   '{"code":"body.respHead.code","data":"body.respBody"}', NULL, NULL, NULL, 'CUSTOM',
   '{"scriptVariables":[{"name":"protocolReady","value":"false","sensitive":false}]}', 0, 0, 3000, 0, 200, 'FAIL_FAST', NULL, NULL, 0,
   'SM2/SM3 证书、签名原文和产品代码待确认，不可启用。',
   '{"name":"张三","idcard_no":"110101199001011234","mobile_no":"13800138000","request_id":"REQ0003"}', 0, NOW(), NOW()),

  (9304, 9203, 'zhanxin_risk_score_v11', '融360占信分 V11', 'POST', '/gateway', 'application/x-www-form-urlencoded', 'SYNC',
   NULL, NULL, '{"biz_data":{"name":"$.name","id_card":"$.idcard_no","mobile":"$.mobile_no"}}',
   '{"code":"body.code","score":"body.data.score"}', NULL,
   'apiPut(body, "method", mapGet(vars, "method")); apiPut(body, "app_id", mapGet(vars, "appId")); apiPut(body, "sign_type", "RSA"); apiPut(body, "version", "1.0"); apiPut(body, "format", "json"); apiPut(body, "timestamp", apiTimestamp("yyyy-MM-dd HH:mm:ss")); body',
   NULL, 'CUSTOM',
   '{"scriptVariables":[{"name":"method","value":"REPLACE_BEFORE_ENABLE_METHOD","sensitive":false},{"name":"appId","value":"REPLACE_BEFORE_ENABLE_APP_ID","sensitive":true}]}',
   0, 0, 3000, 0, 200, 'FAIL_FAST', NULL, NULL, 0,
   'RSA 签名原文规则缺失，脚本仅保留可确认字段，不可启用。',
   '{"name":"张三","idcard_no":"110101199001011234","mobile_no":"13800138000","request_id":"REQ0004"}', 0, NOW(), NOW()),

  (9305, 9204, 'credit_compass_xingyao_pro', '信用司南星耀专业版', 'POST', '/integration/jk13113', 'application/json', 'SYNC',
   NULL, NULL, '{"name":"$.name","idCard":"$.idcard_no","mobile":"$.mobile_no"}',
   '{"status":"body.status","message":"body.message","seqNum":"body.seqNum","gid":"body.gid","data":"body.data"}', NULL,
   'apiPut(body, "appId", mapGet(vars, "appId")); apiPut(body, "tokenKey", apiMd5(endpoint + mapGet(vars, "tokenId") + apiSortedKeyValue(body, ",", "appId,tokenKey"))); body',
   NULL, 'NONE',
   '{"scriptVariables":[{"name":"appId","value":"REPLACE_BEFORE_ENABLE_APP_ID","sensitive":true},{"name":"tokenId","value":"REPLACE_BEFORE_ENABLE_TOKEN_ID","sensitive":true}]}',
   0, 0, 3000, 0, 200, 'FAIL_FAST', NULL, NULL, 0,
   '昨日天创专用安全档案已由排序键值串与 MD5 通用脚本替代。',
   '{"name":"张三","idcard_no":"110101199001011234","mobile_no":"13800138000","request_id":"REQ0005"}', 0, NOW(), NOW()),

  (9306, 9205, 'FRAI001C_attention_compatible', '百行特别关注兼容版', 'POST', '/openapi/queryData/FRAI001C', 'application/json', 'SYNC',
   NULL, NULL, '{"name":"$.name","certNo":"$.idcard_no"}', '{"head":"body.head","response":"body.response"}', NULL,
   'apiPut(body, "queryReason", mapGet(vars, "queryReason")); apiPut(body, "applyDate", apiTimestamp("yyyyMMddHHmmss")); _encrypted = apiTripleDesEncryptBase64(toJson(body), mapGet(vars, "secretKey")); _head = newMap(); _head = mapPut(_head, "requestRefId", requestId); _head = mapPut(_head, "secretId", mapGet(vars, "secretId")); _head = mapPut(_head, "signature", apiHmacSha1Base64Key("requestRefId=" + requestId + "&secretId=" + mapGet(vars, "secretId"), mapGet(vars, "secretKey"))); _out = newMap(); _out = mapPut(_out, "head", _head); _out = mapPut(_out, "request", _encrypted); _out',
   'if (body != null && mapGet(body, "response") != null) { apiPut(body, "response", jsonParse(apiTripleDesDecryptBase64(mapGet(body, "response"), mapGet(vars, "secretKey")))); } body',
   'NONE', '{"scriptVariables":[{"name":"secretId","value":"REPLACE_BEFORE_ENABLE_SECRET_ID","sensitive":true},{"name":"secretKey","value":"REPLACE_BEFORE_ENABLE_BASE64_24_BYTE_KEY","sensitive":true},{"name":"queryReason","value":"1","sensitive":false}]}',
   0, 0, 3000, 0, 200, 'FAIL_FAST', NULL, NULL, 0,
   '昨日百行专用安全档案已由 3DES、Base64 密钥 HMAC-SHA1 与响应脚本替代。',
   '{"name":"张三","idcard_no":"110101199001011234","mobile_no":"13800138000","request_id":"REQ0006"}', 0, NOW(), NOW()),

  (9307, 9205, 'FRAI001A_attention_detail', '百行特别关注明细版', 'POST', '/openapi/queryData/FRAI001A', 'application/json', 'SYNC',
   NULL, NULL, '{"name":"$.name","certNo":"$.idcard_no"}', '{"head":"body.head","response":"body.response"}', NULL,
   'apiPut(body, "queryReason", mapGet(vars, "queryReason")); apiPut(body, "applyDate", apiTimestamp("yyyyMMddHHmmss")); _encrypted = apiTripleDesEncryptBase64(toJson(body), mapGet(vars, "secretKey")); _head = newMap(); _head = mapPut(_head, "requestRefId", requestId); _head = mapPut(_head, "secretId", mapGet(vars, "secretId")); _head = mapPut(_head, "signature", apiHmacSha1Base64Key("requestRefId=" + requestId + "&secretId=" + mapGet(vars, "secretId"), mapGet(vars, "secretKey"))); _out = newMap(); _out = mapPut(_out, "head", _head); _out = mapPut(_out, "request", _encrypted); _out',
   'if (body != null && mapGet(body, "response") != null) { apiPut(body, "response", jsonParse(apiTripleDesDecryptBase64(mapGet(body, "response"), mapGet(vars, "secretKey")))); } body',
   'NONE', '{"scriptVariables":[{"name":"secretId","value":"REPLACE_BEFORE_ENABLE_SECRET_ID","sensitive":true},{"name":"secretKey","value":"REPLACE_BEFORE_ENABLE_BASE64_24_BYTE_KEY","sensitive":true},{"name":"queryReason","value":"1","sensitive":false}]}',
   0, 0, 3000, 0, 200, 'FAIL_FAST', NULL, NULL, 0,
   '明细版与兼容版共用同一套通用 3DES/HMAC 脚本。',
   '{"name":"张三","idcard_no":"110101199001011234","mobile_no":"13800138000","request_id":"REQ0007"}', 0, NOW(), NOW()),

  (9308, 9206, 'shanhe_qilin_score_v3', '山河麒麟分 V3', 'POST', '/shqlf/score/v3', 'application/json', 'SYNC',
   NULL, NULL, '{"mobile":"$.mobile_no","name":"$.name","idNo":"$.idcard_no"}',
   '{"score":"body.data.score","shCode":"body.shCode","shMsg":"body.shMsg","fee":"body.fee","serialNo":"body.shSerialNumber"}', NULL,
   '_ts = toStringValue(nowMillis); apiPut(body, "timestamp", _ts); apiPut(body, "appkey", mapGet(vars, "appKey")); apiPut(body, "sign", apiMd5(mapGet(vars, "appKey") + mapGet(vars, "appSecret") + _ts)); apiPut(body, "mobile", apiMd5(mapGet(body, "mobile"))); apiPut(body, "name", apiMd5(mapGet(body, "name"))); apiPut(body, "idNo", apiMd5(mapGet(body, "idNo"))); apiPut(body, "encryptType", 1); body',
   NULL, 'NONE', '{"scriptVariables":[{"name":"appKey","value":"REPLACE_BEFORE_ENABLE_APP_KEY","sensitive":true},{"name":"appSecret","value":"REPLACE_BEFORE_ENABLE_APP_SECRET","sensitive":true}]}',
   0, 0, 3000, 0, 200, 'FAIL_FAST', NULL, '{"mode":"HIT","path":"body.shCode","operator":"==","value":"200"}', 0,
   'MD5 字段加密、13 位时间戳与 appKey/appSecret 签名均由请求脚本完成。',
   '{"name":"张三","idcard_no":"110101199001011234","mobile_no":"13800138000","request_id":"REQ0008"}', 0, NOW(), NOW()),

  (9309, 9207, 'tongzhi_score_b4', '通智分 B4', 'GET', '/yrzx/score/tongzhi/b4', 'application/json', 'SYNC',
   NULL, NULL, NULL, '{"code":"body.code","msg":"body.msg","uid":"body.uid","reqid":"body.reqid","score":"body.result.score"}', NULL,
   '_reqid = strSubstringTo(requestId, 20); apiPut(query, "account", mapGet(vars, "account")); apiPut(query, "cid", mapGet(input, "idcard_no")); apiPut(query, "name", mapGet(input, "name")); apiPut(query, "mobile", mapGet(input, "mobile_no")); apiPut(query, "reqid", _reqid); apiPut(query, "verify", apiMd5(mapGet(vars, "account") + mapGet(input, "idcard_no") + mapGet(input, "name") + mapGet(input, "mobile_no") + _reqid + mapGet(vars, "key"))); body',
   NULL, 'NONE', '{"scriptVariables":[{"name":"account","value":"REPLACE_BEFORE_ENABLE_ACCOUNT","sensitive":true},{"name":"key","value":"REPLACE_BEFORE_ENABLE_KEY","sensitive":true}]}',
   0, 0, 3000, 0, 200, 'FAIL_FAST', NULL, '{"mode":"HIT","path":"body.code","operator":"==","value":"001"}', 0,
   'reqid 自动截断到 20 位，签名按 account+cid+name+mobile+reqid+key 拼接。',
   '{"name":"张三","idcard_no":"110101199001011234","mobile_no":"13800138000","request_id":"REQ0009"}', 0, NOW(), NOW()),

  (9310, 9208, 'xiaoke_tongfen_risk_v3', '瞳分风险评分 V3', 'POST', '/api', 'application/x-www-form-urlencoded', 'SYNC',
   NULL, NULL, '{}', '{"riskScore":"body.Data.RiskScore_HR","clientNo":"body.ClientNo","serverNo":"body.ServerNo","responseCode":"body.ResponseCode","result":"body.Result"}', NULL,
   '_param = newMap(); _param = mapPut(_param, "Name", mapGet(input, "name")); _param = mapPut(_param, "Mobile", apiMd5(mapGet(input, "mobile_no"))); _param = mapPut(_param, "IDNumber", apiMd5(strUpper(mapGet(input, "idcard_no")))); _param = mapPut(_param, "Enctype", 1); _param = mapPut(_param, "ClientNo", requestId); _encoded = apiUrlEncode(toJson(_param)); _out = newMap(); _out = mapPut(_out, "ACode", "100284"); _out = mapPut(_out, "Param", _encoded); _out = mapPut(_out, "Account", mapGet(vars, "account")); _out = mapPut(_out, "Customer", mapGet(vars, "customer")); _out = mapPut(_out, "Sign", strUpper(apiMd5("100284" + _encoded + mapGet(vars, "account") + strUpper(apiMd5(mapGet(vars, "secretKey")))))); _out',
   NULL, 'NONE', '{"scriptVariables":[{"name":"account","value":"REPLACE_BEFORE_ENABLE_ACCOUNT","sensitive":true},{"name":"secretKey","value":"REPLACE_BEFORE_ENABLE_SECRET_KEY","sensitive":true},{"name":"customer","value":"REPLACE_BEFORE_ENABLE_CUSTOMER","sensitive":false}]}',
   0, 0, 3000, 0, 200, 'FAIL_FAST', NULL, '{"mode":"HIT","path":"body.ResponseCode","operator":"==","value":100}', 0,
   'Param 业务 JSON URL 编码，手机号/身份证 MD5，签名两层 MD5 并转大写。',
   '{"name":"张三","idcard_no":"11010119900101123X","mobile_no":"13800138000","request_id":"REQ0010"}', 0, NOW(), NOW()),

  (9311, 9209, 'tianxing_credibility_iv', '可信度认证 IV', 'GET', '/api/rest/riskTip/credCertify4', 'application/json', 'SYNC',
   NULL, NULL, NULL, '{"success":"body.success","requestOrder":"body.requestOrder","status":"body.data.status","caseType":"body.data.caseType","typeDesc":"body.data.typeDesc","code":"body.code","errorDesc":"body.errorDesc"}', NULL,
   'apiPut(query, "account", mapGet(vars, "account")); apiPut(query, "accessToken", mapGet(vars, "accessToken")); apiPut(query, "idNo", apiSm3(strUpper(mapGet(input, "idcard_no")))); apiPut(query, "isEncrypt", "4"); apiPut(query, "mockType", mapGet(vars, "mockType")); body',
   NULL, 'NONE', '{"scriptVariables":[{"name":"account","value":"REPLACE_BEFORE_ENABLE_ACCOUNT","sensitive":true},{"name":"accessToken","value":"REPLACE_BEFORE_ENABLE_ACCESS_TOKEN","sensitive":true},{"name":"mockType","value":"EXIST","sensitive":false}]}',
   0, 0, 30000, 0, 200, 'FAIL_FAST', NULL, '{"mode":"HIT","path":"body.success","operator":"==","value":true}', 0,
   '身份证使用 SM3、isEncrypt=4；mockType 仅测试环境生效。',
   '{"name":"张三","idcard_no":"11010119900101123X","mobile_no":"13800138000","request_id":"REQ0011"}', 0, NOW(), NOW()),

  (9312, 9210, 'jitui_mobile_online_v2', '手机在网时长 V2', 'POST', '/api/mobile/onlinec/v2', 'application/json', 'SYNC',
   NULL, NULL, '{"mobile":"$.mobile_no"}',
   '{"code":"body.code","msg":"body.msg","rangeStart":"body.result.rangeStart","rangeEnd":"body.result.rangeEnd","provider":"body.result.provider","providerName":"body.result.providerName","province":"body.result.province","city":"body.result.city"}', NULL,
   '_ts = toStringValue(nowMillis); apiPut(body, "timestamp", _ts); apiPut(body, "appkey", mapGet(vars, "appKey")); apiPut(body, "sign", apiMd5(mapGet(vars, "appKey") + mapGet(vars, "appSecret") + _ts)); apiPut(body, "mobile", apiMd5(mapGet(body, "mobile"))); apiPut(body, "encrypt", "md5"); apiPut(body, "encryptFields", ["mobile"]); apiPut(body, "proid", mapGet(vars, "proid")); body',
   NULL, 'NONE', '{"scriptVariables":[{"name":"appKey","value":"REPLACE_BEFORE_ENABLE_APP_KEY","sensitive":true},{"name":"appSecret","value":"REPLACE_BEFORE_ENABLE_APP_SECRET","sensitive":true},{"name":"proid","value":"1","sensitive":false}]}',
   0, 0, 3000, 0, 200, 'FAIL_FAST', NULL, NULL, 0,
   '签名 150 秒有效，使用同一次 nowMillis；手机号按 MD5 配置。',
   '{"name":"张三","idcard_no":"110101199001011234","mobile_no":"13800138000","request_id":"REQ0012"}', 0, NOW(), NOW()),

  (9313, 9211, 'huadao_hdb010_risk_list', 'HDB010 风险名单', 'POST', '/HDB_Series/getConsumptionRiskQuery', 'application/x-www-form-urlencoded', 'SYNC',
   NULL, NULL, '{"NAME":"$.name","IDCARD":"$.idcard_no","PHONE":"$.mobile_no"}',
   '{"code":"body.CODE","exists":"body.EXISTS","riskType":"body.DATA","errcode":"body.errcode","errmsg":"body.errmsg"}', NULL,
   'apiPut(body, "ACCESS_TOKEN", token); body', NULL, 'INHERIT', NULL, 86400, 0, 5000, 0, 200, 'FAIL_FAST', NULL,
   '{"mode":"HIT","path":"body.CODE","operator":"==","value":"200"}', 0,
   'Token 响应 XML 解包后仅注入 ACCESS_TOKEN 表单字段，不额外发送 Authorization Header。',
   '{"name":"张三","idcard_no":"110101199001011234","mobile_no":"13800138000","request_id":"REQ0013"}', 0, NOW(), NOW()),

  (9314, 9212, 'hengzhi_score_b22', '信用评分 Score_b22', 'POST', '/finance/credit_score_b1_v5', 'application/json', 'SYNC',
   NULL, NULL, '{"data":{"idNo":"$.idcard_no","name":"$.name","mobile":"$.mobile_no"}}',
   '{"errorCode":"body.error_code","errorMessage":"body.error_msg","score":"body.data","seqNo":"body.seqNo"}', NULL,
   'apiPut(body, "account", mapGet(vars, "account")); apiPut(body, "pwd", mapGet(vars, "password")); body', NULL,
   'NONE', '{"scriptVariables":[{"name":"account","value":"REPLACE_BEFORE_ENABLE_ACCOUNT","sensitive":true},{"name":"password","value":"REPLACE_BEFORE_ENABLE_PASSWORD","sensitive":true}]}',
   0, 0, 3000, 0, 200, 'FAIL_FAST', NULL, '{"mode":"HIT","path":"body.error_code","operator":"==","value":"0000"}', 0,
   '分数 0 到 100，越高表示信用资质越差。',
   '{"name":"张三","idcard_no":"110101199001011234","mobile_no":"13800138000","request_id":"REQ0014"}', 0, NOW(), NOW()),

  (9315, 9213, 'tuzhi_score_04', '图智分 04', 'POST', '/api/v1/score_tuzhi_04/${appId}', 'application/json', 'SYNC',
   NULL, NULL, '{"name":"$.name","idNo":"$.idcard_no","phone":"$.mobile_no"}',
   '{"code":"body.code","message":"body.msg","feeFlag":"body.feeFlag","requestId":"body.reqId","transactionId":"body.trxId","score":"body.data.tz_00004"}', NULL,
   '_rid = requestId; _ts = toStringValue(nowMillis); apiPut(headers, "REQUESTID", _rid); apiPut(headers, "TIMESTAMP", _ts); apiPut(headers, "AUTHID", mapGet(vars, "authId")); apiPut(headers, "ISCPT", mapGet(vars, "isCpt")); apiPut(headers, "TMCNAME", mapGet(vars, "tmcName")); _plain = _rid + "," + _ts + "," + mapGet(vars, "authId") + "," + mapGet(vars, "isCpt") + "," + mapGet(vars, "tmcName") + "," + mapGet(vars, "secret") + "," + toJson(body); apiPut(headers, "SIGNATURE", strUpper(apiMd5(_plain))); body',
   NULL, 'NONE', '{"scriptVariables":[{"name":"appId","value":"REPLACE_BEFORE_ENABLE_APP_ID","sensitive":false},{"name":"secret","value":"REPLACE_BEFORE_ENABLE_SECRET","sensitive":true},{"name":"authId","value":"1","sensitive":false},{"name":"isCpt","value":"1","sensitive":false},{"name":"tmcName","value":"REPLACE_BEFORE_ENABLE_TMC_NAME","sensitive":false}]}',
   0, 0, 3000, 0, 200, 'FAIL_FAST', NULL, '{"mode":"HIT","path":"body.code","operator":"==","value":0}', 0,
   '接口路径通过 appId 脚本变量解析；请求体文本参与动态 Header 签名。',
   '{"name":"张三","idcard_no":"11010119900101123X","mobile_no":"13800138000","request_id":"REQ0015"}', 0, NOW(), NOW()),

  (9316, 9214, 'baiwei_credit_score_plus_2', '百维信用分升级版 2.0', 'POST', '/api/Fscoreplus2', 'application/x-www-form-urlencoded', 'SYNC',
   NULL, NULL, '{}', '{"status":"body.status","code":"body.code","message":"body.msg","checkResult":"body.checkResult","score":"body.bizResponse.Fscoreplus2"}', NULL,
   'if (mapGet(vars, "protocolReady") == "true") { _key = apiRandomBase64(24); apiPut(state, "symmetricKey", _key); _biz = newMap(); _biz = mapPut(_biz, "idCard", apiMd5(mapGet(input, "idcard_no"))); _biz = mapPut(_biz, "mobile", apiMd5(mapGet(input, "mobile_no"))); _biz = mapPut(_biz, "name", apiMd5(mapGet(input, "name"))); _biz = mapPut(_biz, "encryptionFlag", "00"); _encrypted = apiTripleDesEncryptBase64(toJson(_biz), _key); _out = newMap(); _out = mapPut(_out, "sequence", requestId); _out = mapPut(_out, "timestamp", toStringValue(nowMillis)); _out = mapPut(_out, "merchantNo", mapGet(vars, "merchantNo")); _out = mapPut(_out, "bizRequest", _encrypted); _out = mapPut(_out, "secret", apiRsaEncryptBase64(_key, mapGet(vars, "serverPublicKey"))); _out = mapPut(_out, "sign", apiRsaSignBase64(_encrypted, mapGet(vars, "clientPrivateKey"), mapGet(vars, "signatureAlgorithm"))); _out } else { body }',
   'if (mapGet(vars, "protocolReady") == "true" && body != null && mapGet(body, "bizResponse") != null) { apiPut(body, "bizResponse", jsonParse(apiTripleDesDecryptBase64(mapGet(body, "bizResponse"), mapGet(state, "symmetricKey")))); } body',
   'NONE', '{"scriptVariables":[{"name":"protocolReady","value":"false","sensitive":false},{"name":"merchantNo","value":"REPLACE_BEFORE_ENABLE_MERCHANT_NO","sensitive":true},{"name":"serverPublicKey","value":"REPLACE_BEFORE_ENABLE_RSA_PUBLIC_KEY","sensitive":true},{"name":"clientPrivateKey","value":"REPLACE_BEFORE_ENABLE_RSA_PRIVATE_KEY","sensitive":true},{"name":"signatureAlgorithm","value":"SHA256withRSA","sensitive":false}]}',
   0, 0, 5000, 0, 200, 'FAIL_FAST', NULL, NULL, 0,
   '不可启用：文档未闭合签名算法、密钥格式与验签向量。protocolReady=false 时仅生成明文安全预览；脚本骨架演示随机 3DES 密钥经 state 传给响应脚本。',
   '{"name":"张三","idcard_no":"110101199001011234","mobile_no":"13800138000","request_id":"REQ0016"}', 0, NOW(), NOW()),

  (9317, 9215, 'bairong_special_list_c', '百融特殊名单 SpecialList_c', 'POST', '/huaxiang/v1/get_report', 'application/x-www-form-urlencoded', 'SYNC',
   NULL, NULL, '{}',
   '{"code":"body.code","swiftNumber":"body.swift_number","flag":"body.Flag.specialList_c","specialList":"body.SpecialList_c"}', NULL,
   '_jsonBody = newMap(); _jsonBody = mapPut(_jsonBody, "id", apiMd5(mapGet(input, "idcard_no"))); _jsonBody = mapPut(_jsonBody, "cell", apiMd5(mapGet(input, "mobile_no"))); _jsonBody = mapPut(_jsonBody, "name", apiMd5(mapGet(input, "name"))); _jsonBody = mapPut(_jsonBody, "meal", "SpecialList_c"); _jsonData = toJson(_jsonBody); _out = newMap(); _out = mapPut(_out, "tokenid", token); _out = mapPut(_out, "apiCode", mapGet(vars, "apiCode")); _out = mapPut(_out, "jsonData", _jsonData); _out = mapPut(_out, "checkCode", apiMd5(_jsonData + apiMd5(mapGet(vars, "apiCode") + token))); _out',
   NULL, 'INHERIT', '{"scriptVariables":[{"name":"apiCode","value":"REPLACE_BEFORE_ENABLE_API_CODE","sensitive":true}]}',
   3300, 0, 5000, 0, 200, 'FAIL_FAST', NULL, '{"mode":"HIT","path":"body.code","operator":"==","value":"00"}', 0,
   '登录 tokenid 仅进入表单；id/cell/name 使用 MD5，checkCode=MD5(jsonData+MD5(apiCode+tokenid))。',
   '{"name":"张三","idcard_no":"110101199001011234","mobile_no":"13800138000","request_id":"REQ0017"}', 0, NOW(), NOW());
