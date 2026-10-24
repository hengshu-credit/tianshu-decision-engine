SET NAMES utf8mb4;
SET character_set_connection = utf8mb4;
SET collation_connection = utf8mb4_unicode_ci;

-- 可选启动种子数据：只补齐缺失配置，不更新已有记录。
-- 名单/API/数据库的演示来源变量由样例脚本创建，不预置缺少依赖的固定 ID 配置。
-- 变量写入 GLOBAL + project_id=0，但只要已有任意 GLOBAL 同 var_code 就跳过；对象以 GLOBAL + project_id=0 + object_code 幂等。
INSERT INTO rule_data_object (project_id, `scope`, object_code, object_label, script_name, object_type, source_type, source_content, parent_object_id, status, create_time, update_time) VALUES
 (0,'GLOBAL','contact','联系人','','INPUT','JSON',NULL,NULL,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
 (0,'GLOBAL','contact1','联系人1','','INPUT','JSON',NULL,NULL,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
 (0,'GLOBAL','bankcard','银行卡信息','','INPUT','JSON',NULL,NULL,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
 (0,'GLOBAL','contact2','联系人2','','INPUT','JSON',NULL,NULL,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
 (0,'GLOBAL','authorization_for_contracts_execution','合同签署授权','','INPUT','JSON',NULL,NULL,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','credit_id','授信编号','credit_id','STRING','INPUT',NULL,'','','C0000000001','',0,1,'2026-07-03 22:53:45','2026-07-03 22:53:45'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'credit_id');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','loan_id','借款编号','loan_id','STRING','INPUT',NULL,'','','L0000000001','',0,1,'2026-07-03 22:54:50','2026-07-03 22:54:50'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'loan_id');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','request_id','请求编号','request_id','STRING','INPUT',NULL,'','','R0000000001','',0,1,'2026-07-03 22:55:59','2026-07-03 22:55:59'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'request_id');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','user_id','用户编号','user_id','STRING','INPUT',NULL,'','','U0000000001','',0,1,'2026-07-03 22:56:30','2026-07-03 22:56:30'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'user_id');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','name','姓名','name','STRING','INPUT',NULL,'','','横枢','',0,1,'2026-07-03 22:57:40','2026-07-03 22:57:40'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','idcard_no','身份证号','idcard_no','STRING','INPUT',NULL,'','','500107200503133193','示例值为随机生成',0,1,'2026-07-03 22:58:43','2026-07-03 22:58:43'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'idcard_no');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','mobile_no','手机号','mobile_no','STRING','INPUT',NULL,'','','14519144474','示例值随机生成',0,1,'2026-07-03 22:59:40','2026-07-03 22:59:40'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'mobile_no');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','credit_time','授信时间','credit_time','DATE','INPUT',NULL,'','','2026-01-04 05:20:00','',0,1,'2026-07-03 23:02:25','2026-07-03 23:02:25'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'credit_time');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','loan_time','借款时间','loan_time','DATE','INPUT',NULL,'','','2026-03-01 05:41:31','',0,1,'2026-07-03 23:03:57','2026-07-03 23:03:57'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'loan_time');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','request_time','请求时间','request_time','DATE','INPUT',NULL,'','','2026-04-01 13:14:52','',0,1,'2026-07-03 23:04:48','2026-07-03 23:04:48'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'request_time');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','contacts','联系人数组','contacts','LIST','INPUT',NULL,'','','','与数据对象 concat 搭配',0,1,'2026-07-03 23:23:34','2026-07-03 23:23:34'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'contacts');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','nation','民族','nation','STRING','INPUT',NULL,'','','汉','',0,1,'2026-07-04 00:57:37','2026-07-04 00:57:37'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'nation');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','sex','性别','sex','ENUM','INPUT',NULL,'','','男','',0,1,'2026-07-04 00:58:04','2026-07-04 00:58:04'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'sex');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','device_id','设备号','device_id','STRING','INPUT',NULL,'','','5ede5aab-24d6-4c8a-9598-e186ce9512cb','',0,1,'2026-07-04 01:01:28','2026-07-04 01:01:28'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'device_id');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','imei','国际移动设备识别码','imei','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:02:01','2026-07-04 01:02:01'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'imei');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','meid','移动设备识别码','meid','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:02:15','2026-07-04 01:02:15'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'meid');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','android_id','AndroidID','android_id','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:02:55','2026-07-04 01:02:55'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'android_id');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','serial_number','设备序列号','serial_number','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:03:20','2026-07-04 01:03:20'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'serial_number');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','open_id','OpenID','open_id','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:04:25','2026-07-04 01:04:25'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'open_id');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','gaid','谷歌广告ID','gaid','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:05:27','2026-07-04 01:05:27'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'gaid');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','idfa','IOS广告ID','idfa','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:06:23','2026-07-04 01:06:23'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'idfa');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','udid','唯一设备标识码','udid','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:06:41','2026-07-04 01:06:41'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'udid');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','uuid','通用唯一识别码','uuid','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:06:55','2026-07-04 01:06:55'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'uuid');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','oaid','开放匿名设备标识','oaid','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:07:37','2026-07-04 01:07:37'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'oaid');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','idcard_address','户籍地址','idcard_address','STRING','INPUT',NULL,'','','广东省珠海市斗门县吾斯塘路755号','示例值为随机生成',0,1,'2026-07-04 01:09:00','2026-07-04 01:09:00'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'idcard_address');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','live_address','居住地址','live_address','STRING','INPUT',NULL,'','','广东省珠海市斗门县吾斯塘路755号','示例值为随机生成',0,1,'2026-07-04 01:10:12','2026-07-04 01:10:12'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'live_address');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','company_address','公司地址','company_address','STRING','INPUT',NULL,'','','广东省珠海市斗门县吾斯塘路755号','示例值为随机生成',0,1,'2026-07-04 01:11:14','2026-07-04 01:11:14'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'company_address');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','sender_address','发货地址','sender_address','STRING','INPUT',NULL,'','','广东省珠海市斗门县吾斯塘路755号','示例值为随机生成',0,1,'2026-07-04 01:12:53','2026-07-04 01:12:53'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'sender_address');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','delivery_address','收货地址','delivery_address','STRING','INPUT',NULL,'','','广东省珠海市斗门县吾斯塘路755号','示例值为随机生成',0,1,'2026-07-04 01:13:26','2026-07-04 01:13:26'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'delivery_address');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','address','地址','address','STRING','INPUT',NULL,'','','广东省珠海市斗门县吾斯塘路755号','示例值为随机生成',0,1,'2026-07-04 01:13:55','2026-07-04 01:13:55'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'address');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','delivery_name','收货人姓名','delivery_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:14:18','2026-07-04 01:15:52'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'delivery_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','delivery_mobile','收货人手机','delivery_mobile','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:14:35','2026-07-04 01:15:52'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'delivery_mobile');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','sender_name','发货人姓名','sender_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:14:52','2026-07-04 01:14:52'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'sender_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','sender_mobile','发货人手机号','sender_mobile','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:15:08','2026-07-04 01:15:08'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'sender_mobile');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','idcard_issue_date','身份证开始有效期','idcard_issue_date','STRING','INPUT',NULL,'','','2015-01-01','',0,1,'2026-07-04 01:17:01','2026-07-04 01:33:46'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'idcard_issue_date');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','idcard_expiry_date','身份证结束有效期','idcard_expiry_date','STRING','INPUT',NULL,'','','长期','',0,1,'2026-07-04 01:17:36','2026-07-04 01:33:46'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'idcard_expiry_date');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','age','年龄','age','NUMBER','INPUT',NULL,'','','22','',0,1,'2026-07-04 01:18:49','2026-07-04 01:18:49'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'age');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','idcard_validity_days','身份证有效天数','idcard_validity_days','NUMBER','INPUT',NULL,'','','180','',0,1,'2026-07-04 01:20:05','2026-07-04 01:20:05'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'idcard_validity_days');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','marital_status','婚姻状况','marital_status','STRING','INPUT',NULL,'','','已婚','',0,1,'2026-07-04 01:21:09','2026-07-04 01:21:09'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'marital_status');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','spouse_name','配偶姓名','spouse_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:22:13','2026-07-04 01:22:13'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'spouse_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','spouse_sex','配偶性别','spouse_sex','ENUM','INPUT',NULL,'','','女','',0,1,'2026-07-04 01:22:37','2026-07-04 01:22:37'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'spouse_sex');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','spouse_mobile','配偶手机号','spouse_mobile','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:23:55','2026-07-04 01:23:55'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'spouse_mobile');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','spouse_idcard_no','配偶身份证号','spouse_idcard_no','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:24:14','2026-07-04 01:24:14'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'spouse_idcard_no');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','number_of_children','抚养孩童数量','number_of_children','NUMBER','INPUT',NULL,'','','1','',0,1,'2026-07-04 01:25:04','2026-07-04 01:25:04'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'number_of_children');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','number_of_elderly_supported','赡养老人数量','number_of_elderly_supported','NUMBER','INPUT',NULL,'','','2','',0,1,'2026-07-04 01:26:18','2026-07-04 01:26:18'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'number_of_elderly_supported');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','education','学历','education','STRING','INPUT',NULL,'','','本科','',0,1,'2026-07-04 01:27:20','2026-07-04 01:29:16'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'education');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','degree','学位','degree','STRING','INPUT',NULL,'','','经济学学士','',0,1,'2026-07-04 01:28:56','2026-07-04 01:28:56'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'degree');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','device_name','设备名称','device_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:31:13','2026-07-04 01:31:13'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'device_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','id_type','证件类型','id_type','STRING','INPUT',NULL,'','','身份证','',0,1,'2026-07-04 01:31:43','2026-07-04 01:31:43'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'id_type');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','idcard_issuing_authority','证件签发机关','idcard_issuing_authority','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:34:22','2026-07-04 01:34:22'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'idcard_issuing_authority');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','email','邮箱','email','STRING','INPUT',NULL,'','','hscredit@hengshucredit.com','',0,1,'2026-07-04 01:35:00','2026-07-04 01:35:00'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'email');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','contact_method','联系方法','contact_method','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:35:37','2026-07-04 01:35:37'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'contact_method');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','residential_address','常住地址','residential_address','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:36:12','2026-07-04 01:36:12'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'residential_address');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','country','国家','country','STRING','INPUT',NULL,'','','中国','',0,1,'2026-07-04 01:36:37','2026-07-04 01:36:37'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'country');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','city','城市','city','STRING','INPUT',NULL,'','','上海市','',0,1,'2026-07-04 01:37:05','2026-07-04 01:37:05'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'city');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','district','区/县','district','STRING','INPUT',NULL,'','','静安区','',0,1,'2026-07-04 01:37:30','2026-07-04 01:37:30'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'district');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','detail_address','详细地址','detail_address','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:38:09','2026-07-04 01:38:09'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'detail_address');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','postal_code','邮政编码','postal_code','STRING','INPUT',NULL,'','','2000000','',0,1,'2026-07-04 01:38:36','2026-07-04 01:38:36'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'postal_code');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','employment_status','工作状态','employment_status','STRING','INPUT',NULL,'','','在职','',0,1,'2026-07-04 01:39:34','2026-07-04 01:39:34'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'employment_status');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','company_name','公司名称','company_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:40:18','2026-07-04 01:40:18'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'company_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','company_mobile','公司电话','company_mobile','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:40:43','2026-07-04 01:40:43'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'company_mobile');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','company_type','公司类型','company_type','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:41:02','2026-07-04 01:41:02'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'company_type');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','company_industry','公司行业','company_industry','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:41:25','2026-07-04 01:41:25'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'company_industry');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','occupation_type','职业类型','occupation_type','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:41:56','2026-07-04 01:41:56'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'occupation_type');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','occupation_name','职业名称','occupation_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:42:10','2026-07-04 01:42:10'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'occupation_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','job_title','职务/职位','job_title','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:42:28','2026-07-04 01:42:28'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'job_title');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','employment_start_date','入职开始时间','employment_start_date','DATE','INPUT',NULL,'','','','',0,1,'2026-07-04 01:42:50','2026-07-04 01:42:50'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'employment_start_date');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','employment_tenure','工作年限','employment_tenure','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 01:43:09','2026-07-04 01:43:09'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'employment_tenure');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','income_monthly','月收入','income_monthly','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:43:46','2026-07-04 01:43:46'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'income_monthly');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','income_yearly','年收入','income_yearly','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:44:06','2026-07-04 01:44:06'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'income_yearly');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','currency','币种','currency','STRING','INPUT',NULL,'','','人民币','',0,1,'2026-07-04 01:44:31','2026-07-04 01:44:31'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'currency');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','income_source','收入来源','income_source','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:44:47','2026-07-04 02:02:38'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'income_source');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','gps_longitude','经度','gps_longitude','NUMBER','INPUT',NULL,'','-180~180','','',0,1,'2026-07-04 01:49:17','2026-07-04 01:49:17'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'gps_longitude');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','gps_latitude','纬度','gps_latitude','NUMBER','INPUT',NULL,'','-90~90','','',0,1,'2026-07-04 01:49:59','2026-07-04 01:49:59'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'gps_latitude');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','coordinate_reference_system','坐标参考系','coordinate_reference_system','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:52:29','2026-07-04 01:52:29'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'coordinate_reference_system');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','period','期数','period','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 01:54:06','2026-07-04 01:54:06'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'period');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','amount','金额','amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 01:54:19','2026-07-04 01:54:19'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','risk_level','风险等级','risk_level','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:54:38','2026-07-04 01:54:38'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'risk_level');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','device_brand','设备品牌','device_brand','STRING','INPUT',NULL,'','','','Apple',0,1,'2026-07-04 01:54:58','2026-07-04 01:54:58'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'device_brand');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','device_model','设备型号','device_model','STRING','INPUT',NULL,'','','iPhone 15','',0,1,'2026-07-04 01:55:09','2026-07-04 01:55:09'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'device_model');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','screen_width','屏幕宽度','screen_width','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 01:55:47','2026-07-04 01:55:47'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'screen_width');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','screen_height','屏幕高度','screen_height','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 01:55:57','2026-07-04 01:55:57'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'screen_height');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','screen_resolution','屏幕分辨率','screen_resolution','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:56:10','2026-07-04 01:56:10'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'screen_resolution');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','cpu_core_count','CPU核心数','cpu_core_count','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 01:56:28','2026-07-04 01:56:28'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'cpu_core_count');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','device_memory','设备内存','device_memory','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:56:39','2026-07-04 01:56:39'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'device_memory');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','os_version','系统版本','os_version','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:56:54','2026-07-04 01:56:54'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'os_version');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','operating_system','操作系统','operating_system','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:57:05','2026-07-04 01:57:05'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'operating_system');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','system_language','系统语言','system_language','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:57:14','2026-07-04 01:57:14'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'system_language');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','time_zone','时区','time_zone','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:57:20','2026-07-04 01:57:20'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'time_zone');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','is_rooted','是否Root','is_rooted','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:57:34','2026-07-04 01:57:34'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'is_rooted');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','is_jailbroken','是否越狱','is_jailbroken','NUMBER','INPUT',NULL,'','','1','',0,1,'2026-07-04 01:57:42','2026-07-04 01:57:42'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'is_jailbroken');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','is_emulator','是否模拟器','is_emulator','NUMBER','INPUT',NULL,'','','1','',0,1,'2026-07-04 01:57:50','2026-07-04 01:57:50'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'is_emulator');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','is_proxy','是否代理','is_proxy','NUMBER','INPUT',NULL,'','','1','',0,1,'2026-07-04 01:58:17','2026-07-04 01:58:17'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'is_proxy');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','is_remote_control_app_detected','是否存在远程控制软件','is_remote_control_app_detected','NUMBER','INPUT',NULL,'','','1','',0,1,'2026-07-04 01:59:07','2026-07-04 01:59:07'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'is_remote_control_app_detected');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','browser_name','浏览器名称','browser_name','STRING','INPUT',NULL,'','','Chrome','',0,1,'2026-07-04 01:59:25','2026-07-04 01:59:25'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'browser_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','browser_version','浏览器版本','browser_version','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:59:36','2026-07-04 01:59:36'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'browser_version');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','user_agent','请求头','user_agent','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:59:50','2026-07-04 01:59:50'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'user_agent');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','ip_address','IP地址','ip_address','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:00:26','2026-07-04 02:00:26'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'ip_address');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','ip_isp','IP所属运营商','ip_isp','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:00:45','2026-07-04 02:00:45'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'ip_isp');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','network_type','网络类型','network_type','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:01:00','2026-07-04 02:01:00'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'network_type');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','app_package_name','App包名','app_package_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:01:29','2026-07-04 02:01:29'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'app_package_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','app_version','App版本','app_version','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:01:39','2026-07-04 02:01:39'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'app_version');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','app_install_source','安装来源','app_install_source','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:01:53','2026-07-04 02:01:53'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'app_install_source');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','product_id','产品编码','product_id','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:03:06','2026-07-04 02:03:06'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'product_id');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','product_name','产品名称','product_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:03:26','2026-07-04 02:03:26'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'product_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','channel_code','渠道编码','channel_code','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:03:49','2026-07-04 02:03:49'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'channel_code');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','channel_name','渠道名称','channel_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:04:03','2026-07-04 02:04:03'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'channel_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','sub_channel_code','子渠道编码','sub_channel_code','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:04:23','2026-07-04 02:04:23'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'sub_channel_code');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','sub_channel_name','子渠道名称','sub_channel_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:04:42','2026-07-04 02:04:42'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'sub_channel_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','loan_purpose','借款用途','loan_purpose','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:04:57','2026-07-04 02:04:57'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'loan_purpose');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','repayment_method','还款方式','repayment_method','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:05:08','2026-07-04 02:05:08'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'repayment_method');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','source_channel_code','渠道来源编码','source_channel_code','STRING','INPUT',NULL,'','','H5','',0,1,'2026-07-04 02:07:00','2026-07-04 02:07:00'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'source_channel_code');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','source_channel_name','渠道来源名称','source_channel_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:07:33','2026-07-04 02:07:33'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'source_channel_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','update_time','更新时间','update_time','DATE','INPUT',NULL,'','','','',0,1,'2026-07-04 02:08:01','2026-07-04 02:08:01'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'update_time');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','marketing_code','推广编码','marketing_code','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:08:27','2026-07-04 02:08:27'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'marketing_code');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','marketing_name','推广名称','marketing_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:08:37','2026-07-04 02:08:37'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'marketing_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','wifi_name','WIFI名称','wifi_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:09:16','2026-07-04 02:09:16'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'wifi_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','wifi_mac_address','WIFI MAC 地址','wifi_mac_address','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:10:14','2026-07-04 02:10:14'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'wifi_mac_address');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','wifi_ip_address','WIFI IP 地址','wifi_ip_address','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:10:59','2026-07-04 02:10:59'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'wifi_ip_address');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','dns_servers','DNS地址','dns_servers','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:11:17','2026-07-04 02:11:17'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'dns_servers');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','wifi_network_fingerprint','WIFI标识','wifi_network_fingerprint','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:11:49','2026-07-04 02:11:49'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'wifi_network_fingerprint');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','credit_period','授信期数','credit_period','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:12:37','2026-07-04 02:12:37'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'credit_period');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','loan_period','借款期数','loan_period','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:12:51','2026-07-04 02:12:51'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'loan_period');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','apply_amount','申请金额','apply_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:13:09','2026-07-04 02:13:09'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'apply_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','credit_amount','授信金额','credit_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:13:27','2026-07-04 02:13:27'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'credit_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','loan_amount','借款金额','loan_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:13:38','2026-07-04 02:13:38'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'loan_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','principal_amount','本金','principal_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:24:46','2026-07-04 02:24:46'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'principal_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','interest_amount','利息','interest_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:24:56','2026-07-04 02:24:56'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'interest_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','penalty_interest_amount','罚息','penalty_interest_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:25:06','2026-07-04 02:25:06'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'penalty_interest_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','score','评分','score','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:25:26','2026-07-04 02:25:26'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'score');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','level','等级','level','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:25:48','2026-07-04 02:25:48'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'level');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','customer_type','客户类型','customer_type','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:26:11','2026-07-04 02:26:11'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'customer_type');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','customer_id','客户编号','customer_id','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:26:32','2026-07-04 02:26:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'customer_id');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','customer_level','客户等级','customer_level','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:26:57','2026-07-04 02:26:57'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'customer_level');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','customer_tags','客户标签','customer_tags','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:27:10','2026-07-04 02:27:10'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'customer_tags');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','customer_groups','客户群组','customer_groups','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:27:46','2026-07-04 02:27:46'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'customer_groups');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','current_overdue_days','当前逾期天数','current_overdue_days','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:28:14','2026-07-04 02:28:14'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'current_overdue_days');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','history_overdue_days','历史逾期天数','history_overdue_days','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:28:32','2026-07-04 02:28:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'history_overdue_days');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','history_overdue_amount','历史逾期金额','history_overdue_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:28:32','2026-07-04 02:28:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'history_overdue_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','current_overdue_amount','当前逾期金额','current_overdue_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:28:14','2026-07-04 02:28:14'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'current_overdue_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','result','决策结果','result','ENUM','COMPUTED',NULL,'','','','',0,1,'2026-07-04 02:34:27','2026-07-04 02:34:27'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'result');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','rule_name','规则名称','rule_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:36:09','2026-07-04 02:36:09'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'rule_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','rule_code','规则编码','rule_code','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:36:19','2026-07-04 02:36:19'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'rule_code');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','rule_type','规则类型','rule_type','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:36:38','2026-07-04 02:36:38'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'rule_type');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','is_free','是否计费','is_free','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:37:02','2026-07-04 02:37:02'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'is_free');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','is_found','是否查得','is_found','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:37:24','2026-07-04 02:37:24'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'is_found');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','is_hit','是否命中','is_hit','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:37:49','2026-07-04 02:37:49'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'is_hit');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','message','描述','message','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:38:06','2026-07-04 02:38:06'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'message');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','project_id','项目编号','project_id','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:39:00','2026-07-04 02:39:00'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'project_id');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','project_name','项目名称','project_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:39:09','2026-07-04 02:39:09'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'project_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','probability','概率','probability','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:40:12','2026-07-04 02:40:12'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'probability');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','random_int','随机整数','random_int','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:40:28','2026-07-04 02:40:28'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'random_int');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','random_float','随机浮点数','random_float','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:40:54','2026-07-04 02:40:54'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'random_float');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','random_probability','随机概率','random_probability','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:41:14','2026-07-04 02:41:14'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'random_probability');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','available_credit_limit','可用额度','available_credit_limit','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:43:34','2026-07-04 02:43:34'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'available_credit_limit');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','temporary_credit_limit','临时额度','temporary_credit_limit','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:43:45','2026-07-04 02:43:45'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'temporary_credit_limit');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','fixed_credit_limit','固定额度','fixed_credit_limit','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:43:56','2026-07-04 02:43:56'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'fixed_credit_limit');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','credit_limit_expiry_date','额度有效期','credit_limit_expiry_date','DATE','INPUT',NULL,'','','','',0,1,'2026-07-04 02:44:11','2026-07-04 02:44:11'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'credit_limit_expiry_date');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','credit_limit_validity_days','额度有效天数','credit_limit_validity_days','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:44:23','2026-07-04 02:44:23'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'credit_limit_validity_days');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','is_credit_limit_frozen','是否额度冻结','is_credit_limit_frozen','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:44:35','2026-07-04 02:44:35'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'is_credit_limit_frozen');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','freeze_days','冻结天数','freeze_days','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:44:56','2026-07-04 02:44:56'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'freeze_days');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','credit_limit_freeze_reason','额度冻结原因','credit_limit_freeze_reason','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:45:26','2026-07-04 02:45:26'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'credit_limit_freeze_reason');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','used_credit_limit','已用额度','used_credit_limit','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:46:16','2026-07-04 02:46:16'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'used_credit_limit');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','face_score','人脸分数','face_score','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:49:20','2026-07-04 02:49:20'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'face_score');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','balance','在贷余额','balance','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:52:22','2026-07-04 02:52:22'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'balance');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','active_loan_count','在贷笔数','active_loan_count','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:52:49','2026-07-04 02:52:49'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'active_loan_count');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','credit_limit_adjustment_reason','额度调整原因','credit_limit_adjustment_reason','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:53:10','2026-07-04 02:53:10'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'credit_limit_adjustment_reason');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','credit_limit_adjustment_amount','额度调整金额','credit_limit_adjustment_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:53:19','2026-07-04 02:54:01'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'credit_limit_adjustment_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','credit_limit','授信额度','credit_limit','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:53:42','2026-07-04 02:53:42'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'credit_limit');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','credit_limit_utilization_rate','额度使用率','credit_limit_utilization_rate','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:54:26','2026-07-04 02:54:26'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'credit_limit_utilization_rate');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','credit_limit_freeze_end_time','额度冻结结束时间','credit_limit_freeze_end_time','DATE','INPUT',NULL,'','','','',0,1,'2026-07-04 02:54:49','2026-07-04 02:54:49'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'credit_limit_freeze_end_time');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','credit_limit_freeze_start_time','额度冻结开始时间','credit_limit_freeze_start_time','DATE','INPUT',NULL,'','','','',0,1,'2026-07-04 02:55:00','2026-07-04 02:55:00'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'credit_limit_freeze_start_time');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','paid_principal_amount','已还本金','paid_principal_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:55:36','2026-07-04 02:55:36'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'paid_principal_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','paid_interest_amount','已还利息','paid_interest_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:55:46','2026-07-04 02:55:46'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'paid_interest_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','paid_penalty_interest_amount','已还罚息','paid_penalty_interest_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:55:56','2026-07-04 02:55:56'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'paid_penalty_interest_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','paid_fee_amount','已还费用','paid_fee_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:56:07','2026-07-04 02:56:07'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'paid_fee_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','paid_amount','已还金额','paid_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:56:35','2026-07-04 02:56:35'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'paid_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','outstanding_principal_amount','未还本金','outstanding_principal_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:57:02','2026-07-04 02:57:02'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'outstanding_principal_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','outstanding_interest_amount','未还利息','outstanding_interest_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:57:11','2026-07-04 02:57:11'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'outstanding_interest_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','outstanding_penalty_interest_amount','未还罚息','outstanding_penalty_interest_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:57:21','2026-07-04 02:57:21'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'outstanding_penalty_interest_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','outstanding_amount','剩余应还总额','outstanding_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:57:37','2026-07-04 02:58:17'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'outstanding_amount');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','decision_flow_code','决策流编码','decision_flow_code','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:58:52','2026-07-04 02:58:52'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'decision_flow_code');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','decision_flow_name','决策流名称','decision_flow_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:59:07','2026-07-04 02:59:07'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'decision_flow_name');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','decision_flow_version','决策流版本','decision_flow_version','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:59:21','2026-07-04 02:59:21'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'decision_flow_version');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','interest_rate','利率','interest_rate','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 03:01:47','2026-07-04 03:01:47'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'interest_rate');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','contract_id','合同编号','contract_id','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 03:03:35','2026-07-04 03:03:35'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'contract_id');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','order_id','订单编号','order_id','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 03:03:49','2026-07-04 03:03:49'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'order_id');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','credit_status','授信状态','credit_status','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 03:04:08','2026-07-04 03:04:08'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'credit_status');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','loan_status','借款状态','loan_status','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 03:04:17','2026-07-04 03:04:17'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'loan_status');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','application_status','申请状态','application_status','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 03:04:26','2026-07-04 03:04:26'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'application_status');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','approval_status','审批状态','approval_status','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 03:04:34','2026-07-04 03:04:34'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'approval_status');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','cancel_time','取消时间','cancel_time','DATE','INPUT',NULL,'','','','',0,1,'2026-07-04 03:04:47','2026-07-04 03:04:47'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'cancel_time');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','close_time','关闭时间','close_time','DATE','INPUT',NULL,'','','','',0,1,'2026-07-04 03:05:03','2026-07-04 03:05:03'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'close_time');
INSERT INTO rule_variable (project_id,`scope`,var_code,var_label,script_name,var_type,var_source,source_config,default_value,value_range,example_value,description,sort_order,status,create_time,update_time)
SELECT 0,'GLOBAL','idcard_birthday','身份证生日','idcard_birthday','DATE','COMPUTED',NULL,'','','','',0,1,'2026-07-07 00:36:27','2026-07-07 00:36:27'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing
  WHERE existing.scope = 'GLOBAL' AND existing.var_code = 'idcard_birthday');

-- 枚举选项按变量编码定位，不依赖变量自增 ID。
INSERT INTO rule_variable_option (variable_id, option_value, option_label, sort_order)
SELECT v.id, '0', '女性', 0
FROM rule_variable v
WHERE v.project_id = 0 AND v.scope = 'GLOBAL' AND v.var_code = 'sex'
  AND NOT EXISTS (SELECT 1 FROM rule_variable_option o WHERE o.variable_id = v.id AND o.option_value = '0'); 
INSERT INTO rule_variable_option (variable_id, option_value, option_label, sort_order)
SELECT v.id, '1', '男性', 1
FROM rule_variable v
WHERE v.project_id = 0 AND v.scope = 'GLOBAL' AND v.var_code = 'sex'
  AND NOT EXISTS (SELECT 1 FROM rule_variable_option o WHERE o.variable_id = v.id AND o.option_value = '1'); 
INSERT INTO rule_variable_option (variable_id, option_value, option_label, sort_order)
SELECT v.id, '-1', '未知', 2
FROM rule_variable v
WHERE v.project_id = 0 AND v.scope = 'GLOBAL' AND v.var_code = 'sex'
  AND NOT EXISTS (SELECT 1 FROM rule_variable_option o WHERE o.variable_id = v.id AND o.option_value = '-1'); 
INSERT INTO rule_variable_option (variable_id, option_value, option_label, sort_order)
SELECT v.id, '0', '女', 0
FROM rule_variable v
WHERE v.project_id = 0 AND v.scope = 'GLOBAL' AND v.var_code = 'spouse_sex'
  AND NOT EXISTS (SELECT 1 FROM rule_variable_option o WHERE o.variable_id = v.id AND o.option_value = '0'); 
INSERT INTO rule_variable_option (variable_id, option_value, option_label, sort_order)
SELECT v.id, '1', '男', 1
FROM rule_variable v
WHERE v.project_id = 0 AND v.scope = 'GLOBAL' AND v.var_code = 'spouse_sex'
  AND NOT EXISTS (SELECT 1 FROM rule_variable_option o WHERE o.variable_id = v.id AND o.option_value = '1'); 
INSERT INTO rule_variable_option (variable_id, option_value, option_label, sort_order)
SELECT v.id, '-1', '未知', 2
FROM rule_variable v
WHERE v.project_id = 0 AND v.scope = 'GLOBAL' AND v.var_code = 'spouse_sex'
  AND NOT EXISTS (SELECT 1 FROM rule_variable_option o WHERE o.variable_id = v.id AND o.option_value = '-1'); 
INSERT INTO rule_variable_option (variable_id, option_value, option_label, sort_order)
SELECT v.id, '0', '通过', 0
FROM rule_variable v
WHERE v.project_id = 0 AND v.scope = 'GLOBAL' AND v.var_code = 'result'
  AND NOT EXISTS (SELECT 1 FROM rule_variable_option o WHERE o.variable_id = v.id AND o.option_value = '0'); 
INSERT INTO rule_variable_option (variable_id, option_value, option_label, sort_order)
SELECT v.id, '1', '拒绝', 1
FROM rule_variable v
WHERE v.project_id = 0 AND v.scope = 'GLOBAL' AND v.var_code = 'result'
  AND NOT EXISTS (SELECT 1 FROM rule_variable_option o WHERE o.variable_id = v.id AND o.option_value = '1'); 
INSERT INTO rule_variable_option (variable_id, option_value, option_label, sort_order)
SELECT v.id, '2', '转人工', 2
FROM rule_variable v
WHERE v.project_id = 0 AND v.scope = 'GLOBAL' AND v.var_code = 'result'
  AND NOT EXISTS (SELECT 1 FROM rule_variable_option o WHERE o.variable_id = v.id AND o.option_value = '2'); 
INSERT INTO rule_variable_option (variable_id, option_value, option_label, sort_order)
SELECT v.id, '-1', '异常', 3
FROM rule_variable v
WHERE v.project_id = 0 AND v.scope = 'GLOBAL' AND v.var_code = 'result'
  AND NOT EXISTS (SELECT 1 FROM rule_variable_option o WHERE o.variable_id = v.id AND o.option_value = '-1'); 

-- 数据对象字段按对象编码 + 字段编码定位；已有字段内容不会被覆盖。
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'name', '姓名', 'name', 'STRING', NULL, NULL, NULL, NULL, 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'name');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'mobile_no', '手机号', 'mobile_no', 'STRING', NULL, NULL, NULL, NULL, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'mobile_no');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'relation', '关系', 'relation', 'ENUM', NULL, NULL, NULL, NULL, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'relation');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'relation_type', '关系类型', 'relation_type', 'ENUM', NULL, NULL, NULL, NULL, 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'relation_type');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'name', '开户人', 'name', 'STRING', NULL, NULL, NULL, NULL, 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'bankcard'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'name');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'bank_card_no', '银行卡号', 'bank_card_no', 'STRING', NULL, NULL, NULL, NULL, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'bankcard'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'bank_card_no');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'account_opening_bank', '开户行', 'account_opening_bank', 'STRING', NULL, NULL, NULL, NULL, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'bankcard'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'account_opening_bank');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'bank_branch', '营业网点/支行', 'bank_branch', 'STRING', NULL, NULL, NULL, NULL, 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'bankcard'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'bank_branch');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'mobile', '手机号', 'mobile', 'STRING', NULL, NULL, NULL, NULL, 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'bankcard'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'mobile');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'name', '姓名', 'name', 'STRING', NULL, NULL, NULL, NULL, 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact1'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'name');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'mobile_no', '手机号', 'mobile_no', 'STRING', NULL, NULL, NULL, NULL, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact1'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'mobile_no');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'relation', '关系', 'relation', 'ENUM', NULL, NULL, NULL, NULL, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact1'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'relation');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'relation_type', '关系类型', 'relation_type', 'ENUM', NULL, NULL, NULL, NULL, 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact1'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'relation_type');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'name', '姓名', 'name', 'STRING', NULL, NULL, NULL, NULL, 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact2'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'name');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'mobile_no', '手机号', 'mobile_no', 'STRING', NULL, NULL, NULL, NULL, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact2'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'mobile_no');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'relation', '关系', 'relation', 'ENUM', NULL, NULL, NULL, NULL, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact2'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'relation');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'relation_type', '关系类型', 'relation_type', 'ENUM', NULL, NULL, NULL, NULL, 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact2'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'relation_type');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'credit_bureau_consent', '征信查询授权', 'credit_bureau_consent', 'STRING', NULL, NULL, NULL, NULL, 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'authorization_for_contracts_execution'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'credit_bureau_consent');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'personal_data_consent', '个人信息授权', 'personal_data_consent', 'STRING', NULL, NULL, NULL, NULL, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'authorization_for_contracts_execution'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'personal_data_consent');
INSERT INTO rule_data_object_field (project_id, `scope`, object_id, var_code, var_label, script_name, var_type, ref_object_code, ref_object_id, generic_type, parent_field_id, sort_order, status, create_time, update_time)
SELECT 0, 'GLOBAL', o.id, 'guarantee_agreement_consent', '担保合同授权', 'guarantee_agreement_consent', 'STRING', NULL, NULL, NULL, NULL, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM rule_data_object o
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'authorization_for_contracts_execution'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field f WHERE f.object_id = o.id AND f.parent_field_id IS NULL AND f.var_code = 'guarantee_agreement_consent');

-- 数据对象字段枚举选项按对象编码 + 字段编码定位，不依赖字段自增 ID。
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '0', '父亲', 0
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '0');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '1', '母亲', 1
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '1');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '2', '爷爷', 2
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '2');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '3', '奶奶', 3
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '3');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '4', '外公', 4
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '4');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '5', '外婆', 5
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '5');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '6', '哥哥', 6
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '6');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '7', '姐姐', 7
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '7');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '8', '弟弟', 8
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '8');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '9', '妹妹', 9
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '9');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '10', '表哥', 10
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '10');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '11', '表姐', 11
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '11');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '12', '堂兄', 12
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '12');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '13', '堂姐', 13
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '13');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '14', '表弟', 14
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '14');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '15', '表妹', 15
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '15');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '16', '堂弟', 16
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '16');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '17', '堂妹', 17
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '17');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '18', '叔叔', 18
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '18');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '-1', '其他', 19
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '-1');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '0', '亲属', 0
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation_type'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '0');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '1', '朋友', 1
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation_type'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '1');
INSERT INTO rule_data_object_field_option (field_id, option_value, option_label, sort_order)
SELECT f.id, '-1', '其他', 2
FROM rule_data_object o JOIN rule_data_object_field f ON f.object_id = o.id
WHERE o.project_id = 0 AND o.scope = 'GLOBAL' AND o.object_code = 'contact'
  AND f.parent_field_id IS NULL AND f.var_code = 'relation_type'
  AND NOT EXISTS (SELECT 1 FROM rule_data_object_field_option x WHERE x.field_id = f.id AND x.option_value = '-1');
