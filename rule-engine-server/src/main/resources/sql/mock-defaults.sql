-- 升级内置目录标识一次，保留自定义匹配、方法参数及所有外键 ID。
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

INSERT INTO rule_mock_rule (seed_key, name, value_type, match_names, generator, config_json) VALUES
('v2.text','示例文本','STRING','','TEXT','null'),
('v2.number','常用数值','NUMBER','','INTEGER','[0,100]'),
('v2.bool','随机布尔值','BOOLEAN','','BOOLEAN','null'),
('v2.date','近期日期','DATE','','DATE','null'),
('v2.list','示例列表','LIST','','FIXED','[]'),
('v2.object','空对象','OBJECT','','FIXED','{}'),
('v2.mobile','手机号码','STRING','','PHONE','null'),
('v2.email','电子邮箱','STRING','','EMAIL','null'),
('v2.request','请求流水号','STRING','','UUID','null'),
('v2.age','年龄','NUMBER','','INTEGER','[18,75]'),
('v2.score','信用评分','NUMBER','','INTEGER','[300,850]'),
('v2.amount','申请金额','NUMBER','','INTEGER','[1000,100000]'),
('decimal','两位小数','NUMBER','','DECIMAL','[0,1000,2]'),
('choice','随机候选文本','STRING','','CHOICE','["A","B","C"]'),
('template','带日期的流水号','STRING','','TEMPLATE','"REQ-{date}-{digits}"'),
('datetime','日期时间','DATETIME','','DATETIME','null'),
('timestamp','毫秒时间戳','TIMESTAMP','','TIMESTAMP','null')
ON DUPLICATE KEY UPDATE seed_key=rule_mock_rule.seed_key;
