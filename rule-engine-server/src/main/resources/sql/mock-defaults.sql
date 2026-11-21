INSERT INTO rule_mock_rule (seed_key, name, value_type, match_names, generator, config_json) VALUES
('text','示例文本','STRING','*','TEXT','null'),
('number','常用数值','NUMBER','*','INTEGER','[0,100]'),
('bool','随机布尔值','BOOLEAN','*','BOOLEAN','null'),
('date','近期日期','DATE','*','DATE','null'),
('list','示例列表','LIST','*','FIXED','[]'),
('object','空对象','OBJECT','','FIXED','{}'),
('mobile','手机号码','STRING','mobile,phone,phoneNumber,手机号,手机号码','PHONE','null'),
('email','电子邮箱','STRING','email,emailAddress,电子邮箱,邮箱','EMAIL','null'),
('request','请求流水号','STRING','requestId,traceId,请求流水号','UUID','null'),
('age','年龄','NUMBER','age,年龄','INTEGER','[18,75]'),
('score','信用评分','NUMBER','creditScore,信用评分','INTEGER','[300,850]'),
('amount','申请金额','NUMBER','amount,loanAmount,申请金额','INTEGER','[1000,100000]')
ON DUPLICATE KEY UPDATE seed_key=rule_mock_rule.seed_key;
