-- 仅用于旧版可选初始化迁移测试，保留旧固定引用以复现已初始化状态。
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','black_hit','是否命中黑名单','black_hit','NUMBER','LIST','{"listId": 1, "itemTypes": ["MOBILE", "ID_CARD", "ADDRESS", "IP"], "matchMode": "IN_LIST", "queryField": "mobile_no", "returnMode": "NUMBER", "forceRefresh": false}','-1','','','',0,1,'2026-07-04 03:35:32','2026-07-04 03:35:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'black_hit');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','engine_bdrules','引擎黑规则结果','engine_bdrules','LIST','API','{"resultPath": "body", "apiConfigId": 1, "forceRefresh": false, "fallbackValue": null, "exceptionStrategy": "ERROR"}','','','','',0,1,'2026-07-04 03:51:57','2026-07-04 03:51:57'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'engine_bdrules');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','black_user_count','黑名单人数','black_user_count','NUMBER','DB','{"sql": "SELECT count(1) user_count\\r\\nFROM rule_list_record AS rlr\\r\\nWHERE list_id = 1 and item_content = ?", "params": [{"kind":"REFERENCE","refType":"VARIABLE","refId":7,"code":"mobile_no"}], "maxRows": 1, "resultPath": "0.user_count", "datasourceId": 1, "forceRefresh": false, "fallbackValue": null, "exceptionStrategy": "ERROR"}','','','','',0,1,'2026-07-04 04:05:12','2026-07-07 02:42:18'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'black_user_count');
