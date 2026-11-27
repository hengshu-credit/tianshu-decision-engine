-- 仅用于历史 Mock 匹配数据，先确认目标列已由 server 补齐。内置初始化器也会执行相同的转换。
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
