-- 在停应用并备份后执行。仅替换未经修改的五参数出厂定义，保留函数 ID 与历史版本。
UPDATE rule_function
SET description = '待评分坏账率和基础坏账率均用小数。基础好坏比与基础坏账率只填一项，另一项填 null。好坏比每变为原来的 rate 倍，ASC 加 pdo 分，DESC 减 pdo 分。',
    params_json = '[
      {"name":"current_bad_rate","type":"NUMBER","label":"待评分坏账率","example":0.1,"javaType":"double"},
      {"name":"base_score","type":"NUMBER","label":"基础分","example":600,"javaType":"double"},
      {"name":"base_odds","type":"NUMBER","label":"基础好坏比（二选一）","example":3,"javaType":"java.lang.Number","help":"base_odds = (1 − bad_rate) / bad_rate"},
      {"name":"bad_rate","type":"NUMBER","label":"基础坏账率（二选一）","example":null,"javaType":"java.lang.Number","help":"bad_rate = 1 / (1 + base_odds)"},
      {"name":"rate","type":"NUMBER","label":"好坏比变化倍数","example":3,"javaType":"double"},
      {"name":"pdo","type":"NUMBER","label":"每次变动的分数","example":20,"javaType":"double"},
      {"name":"direction","type":"STRING","label":"评分方向","example":"ASC","javaType":"java.lang.String"}
    ]'
WHERE scope = 'GLOBAL' AND project_id = 0 AND BINARY func_code = 'scoreByBadRatePdo'
  AND BINARY func_name = '坏账率按 PDO 转评分'
  AND BINARY description = '先将 bad_rate 转为 good/bad odds，再按基础分、基础 odds、PDO 和方向转换为评分'
  AND BINARY impl_type = 'JAVA' AND BINARY return_type = 'NUMBER'
  AND BINARY impl_class = 'com.hengshucredit.rule.core.function.DecisionBuiltinFunctions'
  AND BINARY impl_method = 'scoreByBadRatePdo'
  AND status = 1
  AND CAST(IF(JSON_VALID(params_json), params_json, 'null') AS JSON) = CAST('[
      {"name":"bad_rate","type":"NUMBER","label":"坏账率","example":0.05},
      {"name":"base_score","type":"NUMBER","label":"基础分","example":600},
      {"name":"base_odds","type":"NUMBER","label":"基础 odds","example":20},
      {"name":"pdo","type":"NUMBER","label":"PDO","example":20},
      {"name":"direction","type":"STRING","label":"方向","example":"ASC"}
    ]' AS JSON);
