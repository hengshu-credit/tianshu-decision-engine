SET NAMES utf8mb4;
SET character_set_connection = utf8mb4;
SET collation_connection = utf8mb4_unicode_ci;

-- 前置：先执行 schema.sql；可选 initial-data.sql 在本文件前后执行均可。
-- 不依赖空库、自增起点或插入顺序。@example_* 仅为本次导入的 ID 映射，业务关联仍保存真实 ID。
-- 只补齐缺失配置；末尾仅修正完整匹配旧版默认种子的三个来源变量，不覆盖用户修改。
-- 在同一连接中完整执行，遇错停止（不要使用 mysql --force）；失败断开连接会回滚未提交的数据。
-- 同编码多条 GLOBAL 记录属于歧义，标量 ID 查询会报错，不能任意选择或合并。
SET @example_init_lock = CONCAT('tianshu:initial-data:', DATABASE());
SET @example_lock_acquired = GET_LOCK(@example_init_lock, 120);
-- 锁未取得时用严格 JSON 转换主动失败，保证不会继续执行导入。
SET @example_lock_guard = CAST(IF(@example_lock_acquired = 1, 'true', 'INITIAL_DATA_LOCK_NOT_ACQUIRED') AS JSON);
START TRANSACTION;

-- rule_field_validation: 1
INSERT INTO rule_field_validation (`project_id`,`scope`,`validation_code`,`validation_name`,`validation_type`,`validation_value`,`error_message`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','builtin_required','必填','REQUIRED',NULL,'该字段不能为空','校验字段必须提供有效值。',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_required');
SET @example_field_validation_1 = (SELECT existing.id FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_required');

-- rule_field_validation: 2
INSERT INTO rule_field_validation (`project_id`,`scope`,`validation_code`,`validation_name`,`validation_type`,`validation_value`,`error_message`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','builtin_digits','数字','REGEX','^[0-9]*$','请输入数字','只允许输入数字。',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_digits');
SET @example_field_validation_2 = (SELECT existing.id FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_digits');

-- rule_field_validation: 3
INSERT INTO rule_field_validation (`project_id`,`scope`,`validation_code`,`validation_name`,`validation_type`,`validation_value`,`error_message`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','builtin_digits_min_16','至少16位数字','REGEX','^\\d{16,}$','请输入至少16位数字','只允许输入不少于16位的数字。',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_digits_min_16');
SET @example_field_validation_3 = (SELECT existing.id FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_digits_min_16');

-- rule_field_validation: 4
INSERT INTO rule_field_validation (`project_id`,`scope`,`validation_code`,`validation_name`,`validation_type`,`validation_value`,`error_message`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','builtin_digits_15_to_18','15-18位数字','REGEX','^\\d{15,18}$','请输入15至18位数字','只允许输入不少于15位且不超过18位的数字。',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_digits_15_to_18');
SET @example_field_validation_4 = (SELECT existing.id FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_digits_15_to_18');

-- rule_field_validation: 5
INSERT INTO rule_field_validation (`project_id`,`scope`,`validation_code`,`validation_name`,`validation_type`,`validation_value`,`error_message`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','builtin_chinese','汉字','REGEX','^[\\u4e00-\\u9fa5]{0,}$','请输入汉字','只允许输入汉字。',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_chinese');
SET @example_field_validation_5 = (SELECT existing.id FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_chinese');

-- rule_field_validation: 6
INSERT INTO rule_field_validation (`project_id`,`scope`,`validation_code`,`validation_name`,`validation_type`,`validation_value`,`error_message`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','builtin_alphanumeric','英文和数字','REGEX','^[a-zA-Z0-9]+$','请输入英文或数字','只允许输入英文字母和数字。',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_alphanumeric');
SET @example_field_validation_6 = (SELECT existing.id FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_alphanumeric');

-- rule_field_validation: 7
INSERT INTO rule_field_validation (`project_id`,`scope`,`validation_code`,`validation_name`,`validation_type`,`validation_value`,`error_message`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','builtin_email','邮箱','REGEX','^\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*$','请输入正确的邮箱地址','校验常用邮箱地址格式。',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_email');
SET @example_field_validation_7 = (SELECT existing.id FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_email');

-- rule_field_validation: 8
INSERT INTO rule_field_validation (`project_id`,`scope`,`validation_code`,`validation_name`,`validation_type`,`validation_value`,`error_message`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','builtin_domain','域名','REGEX','^[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+\\.?$','请输入正确的域名','校验常用域名格式。',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_domain');
SET @example_field_validation_8 = (SELECT existing.id FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_domain');

-- rule_field_validation: 9
INSERT INTO rule_field_validation (`project_id`,`scope`,`validation_code`,`validation_name`,`validation_type`,`validation_value`,`error_message`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','builtin_mobile','手机号','REGEX','^1[0-9]{10}$','请输入正确的手机号','校验中国大陆11位手机号格式。',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_mobile');
SET @example_field_validation_9 = (SELECT existing.id FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_mobile');

-- rule_field_validation: 10
INSERT INTO rule_field_validation (`project_id`,`scope`,`validation_code`,`validation_name`,`validation_type`,`validation_value`,`error_message`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','builtin_id_card','身份证号','REGEX','^[1-9]\\d{5}(18|19|20)\\d{2}((0[1-9])|(1[0-2]))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$','请输入正确的身份证号','校验中国大陆18位身份证号的基础格式。',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_id_card');
SET @example_field_validation_10 = (SELECT existing.id FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_id_card');

-- rule_field_validation: 11
INSERT INTO rule_field_validation (`project_id`,`scope`,`validation_code`,`validation_name`,`validation_type`,`validation_value`,`error_message`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','builtin_ip_address','IP地址','REGEX','^((?:(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d))$','请输入正确的IP地址','校验 IPv4 地址格式。',1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_ip_address');
SET @example_field_validation_11 = (SELECT existing.id FROM rule_field_validation existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`validation_code` <=> 'builtin_ip_address');

-- rule_data_object: contact
INSERT INTO rule_data_object (`project_id`,`scope`,`object_code`,`object_label`,`script_name`,`object_type`,`source_type`,`source_content`,`parent_object_id`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','contact','联系人','','INPUT','JSON',NULL,NULL,1,'2026-07-03 23:06:23','2026-07-03 23:16:33'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`object_code` <=> 'contact');
SET @example_data_object_1 = (SELECT existing.id FROM rule_data_object existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`object_code` <=> 'contact');

-- rule_data_object: contact1
INSERT INTO rule_data_object (`project_id`,`scope`,`object_code`,`object_label`,`script_name`,`object_type`,`source_type`,`source_content`,`parent_object_id`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','contact1','联系人1','','INPUT','JSON',NULL,NULL,1,'2026-07-03 23:06:23','2026-07-03 23:16:33'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`object_code` <=> 'contact1');
SET @example_data_object_2 = (SELECT existing.id FROM rule_data_object existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`object_code` <=> 'contact1');

-- rule_data_object: bankcard
INSERT INTO rule_data_object (`project_id`,`scope`,`object_code`,`object_label`,`script_name`,`object_type`,`source_type`,`source_content`,`parent_object_id`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','bankcard','银行卡信息','','INPUT','JSON',NULL,NULL,1,'2026-07-03 23:15:27','2026-07-03 23:15:27'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`object_code` <=> 'bankcard');
SET @example_data_object_3 = (SELECT existing.id FROM rule_data_object existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`object_code` <=> 'bankcard');

-- rule_data_object: contact2
INSERT INTO rule_data_object (`project_id`,`scope`,`object_code`,`object_label`,`script_name`,`object_type`,`source_type`,`source_content`,`parent_object_id`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','contact2','联系人2','','INPUT','JSON',NULL,NULL,1,'2026-07-03 23:06:23','2026-07-03 23:16:33'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`object_code` <=> 'contact2');
SET @example_data_object_4 = (SELECT existing.id FROM rule_data_object existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`object_code` <=> 'contact2');

-- rule_data_object: authorization_for_contracts_execution
INSERT INTO rule_data_object (`project_id`,`scope`,`object_code`,`object_label`,`script_name`,`object_type`,`source_type`,`source_content`,`parent_object_id`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','authorization_for_contracts_execution','合同签署授权','','INPUT','JSON',NULL,NULL,1,'2026-07-04 02:20:58','2026-07-04 02:20:58'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`object_code` <=> 'authorization_for_contracts_execution');
SET @example_data_object_5 = (SELECT existing.id FROM rule_data_object existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`object_code` <=> 'authorization_for_contracts_execution');

-- rule_data_object: score_f1_fields
INSERT INTO rule_data_object (`project_id`,`scope`,`object_code`,`object_label`,`script_name`,`object_type`,`source_type`,`source_content`,`parent_object_id`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','score_f1_fields','反欺诈评分F1','','INPUT','DDL',NULL,NULL,1,'2026-07-04 03:18:33','2026-07-04 03:18:33'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`object_code` <=> 'score_f1_fields');
SET @example_data_object_6 = (SELECT existing.id FROM rule_data_object existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`object_code` <=> 'score_f1_fields');

-- rule_data_object: TSRequestBody
INSERT INTO rule_data_object (`project_id`,`scope`,`object_code`,`object_label`,`script_name`,`object_type`,`source_type`,`source_content`,`parent_object_id`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','TSRequestBody','引擎请求体','','INPUT','JSON',NULL,NULL,1,'2026-07-04 03:45:26','2026-07-04 03:45:26'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`object_code` <=> 'TSRequestBody');
SET @example_data_object_7 = (SELECT existing.id FROM rule_data_object existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`object_code` <=> 'TSRequestBody');

-- rule_data_object: TSResponseBody
INSERT INTO rule_data_object (`project_id`,`scope`,`object_code`,`object_label`,`script_name`,`object_type`,`source_type`,`source_content`,`parent_object_id`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','TSResponseBody','引擎相应体','','INPUT','JSON',NULL,NULL,1,'2026-07-04 03:46:00','2026-07-04 03:46:00'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`object_code` <=> 'TSResponseBody');
SET @example_data_object_8 = (SELECT existing.id FROM rule_data_object existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`object_code` <=> 'TSResponseBody');

-- rule_data_object_field: name
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_1,'name','姓名','name','STRING',NULL,NULL,NULL,NULL,0,1,'2026-07-03 23:06:43','2026-07-04 01:46:10'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_1 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'name');
SET @example_data_object_field_1 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_1 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'name');

-- rule_data_object_field: mobile_no
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_1,'mobile_no','手机号','mobile_no','STRING',NULL,NULL,NULL,NULL,1,1,'2026-07-03 23:07:03','2026-07-04 01:46:11'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_1 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'mobile_no');
SET @example_data_object_field_2 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_1 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'mobile_no');

-- rule_data_object_field: relation
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_1,'relation','关系','relation','ENUM',NULL,NULL,NULL,NULL,2,1,'2026-07-03 23:08:06','2026-07-03 23:08:51'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_1 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'relation');
SET @example_data_object_field_3 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_1 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'relation');

-- rule_data_object_field: relation_type
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_1,'relation_type','关系类型','relation_type','ENUM',NULL,NULL,NULL,NULL,3,1,'2026-07-03 23:13:05','2026-07-03 23:13:12'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_1 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'relation_type');
SET @example_data_object_field_4 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_1 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'relation_type');

-- rule_data_object_field: name
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_3,'name','开户人','name','STRING',NULL,NULL,NULL,NULL,0,1,'2026-07-03 23:16:51','2026-07-03 23:16:51'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_3 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'name');
SET @example_data_object_field_5 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_3 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'name');

-- rule_data_object_field: bank_card_no
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_3,'bank_card_no','银行卡号','bank_card_no','STRING',NULL,NULL,NULL,NULL,1,1,'2026-07-03 23:17:10','2026-07-04 01:46:58'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_3 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'bank_card_no');
SET @example_data_object_field_6 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_3 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'bank_card_no');

-- rule_data_object_field: account_opening_bank
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_3,'account_opening_bank','开户行','account_opening_bank','STRING',NULL,NULL,NULL,NULL,2,1,'2026-07-03 23:17:56','2026-07-04 01:47:31'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_3 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'account_opening_bank');
SET @example_data_object_field_7 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_3 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'account_opening_bank');

-- rule_data_object_field: bank_branch
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_3,'bank_branch','营业网点/支行','bank_branch','STRING',NULL,NULL,NULL,NULL,3,1,'2026-07-03 23:22:13','2026-07-04 01:46:38'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_3 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'bank_branch');
SET @example_data_object_field_8 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_3 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'bank_branch');

-- rule_data_object_field: mobile
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_3,'mobile','手机号','mobile','STRING',NULL,NULL,NULL,NULL,0,1,'2026-07-03 23:22:45','2026-07-03 23:22:54'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_3 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'mobile');
SET @example_data_object_field_9 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_3 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'mobile');

-- rule_data_object_field: name
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_2,'name','姓名','name','STRING',NULL,NULL,NULL,NULL,0,1,'2026-07-03 23:06:43','2026-07-04 01:46:10'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_2 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'name');
SET @example_data_object_field_10 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_2 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'name');

-- rule_data_object_field: mobile_no
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_2,'mobile_no','手机号','mobile_no','STRING',NULL,NULL,NULL,NULL,1,1,'2026-07-03 23:07:03','2026-07-04 01:46:11'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_2 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'mobile_no');
SET @example_data_object_field_11 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_2 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'mobile_no');

-- rule_data_object_field: relation
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_2,'relation','关系','relation','ENUM',NULL,NULL,NULL,NULL,2,1,'2026-07-03 23:08:06','2026-07-03 23:08:51'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_2 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'relation');
SET @example_data_object_field_12 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_2 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'relation');

-- rule_data_object_field: relation_type
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_2,'relation_type','关系类型','relation_type','ENUM',NULL,NULL,NULL,NULL,3,1,'2026-07-03 23:13:05','2026-07-03 23:13:12'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_2 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'relation_type');
SET @example_data_object_field_13 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_2 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'relation_type');

-- rule_data_object_field: name
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_4,'name','姓名','name','STRING',NULL,NULL,NULL,NULL,0,1,'2026-07-03 23:06:43','2026-07-04 01:46:10'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_4 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'name');
SET @example_data_object_field_14 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_4 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'name');

-- rule_data_object_field: mobile_no
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_4,'mobile_no','手机号','mobile_no','STRING',NULL,NULL,NULL,NULL,1,1,'2026-07-03 23:07:03','2026-07-04 01:46:11'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_4 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'mobile_no');
SET @example_data_object_field_15 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_4 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'mobile_no');

-- rule_data_object_field: relation
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_4,'relation','关系','relation','ENUM',NULL,NULL,NULL,NULL,2,1,'2026-07-03 23:08:06','2026-07-03 23:08:51'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_4 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'relation');
SET @example_data_object_field_16 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_4 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'relation');

-- rule_data_object_field: relation_type
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_4,'relation_type','关系类型','relation_type','ENUM',NULL,NULL,NULL,NULL,3,1,'2026-07-03 23:13:05','2026-07-03 23:13:12'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_4 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'relation_type');
SET @example_data_object_field_17 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_4 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'relation_type');

-- rule_data_object_field: credit_bureau_consent
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_5,'credit_bureau_consent','征信查询授权','credit_bureau_consent','STRING',NULL,NULL,NULL,NULL,0,1,'2026-07-04 02:21:19','2026-07-04 02:22:59'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_5 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'credit_bureau_consent');
SET @example_data_object_field_18 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_5 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'credit_bureau_consent');

-- rule_data_object_field: personal_data_consent
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_5,'personal_data_consent','个人信息授权','personal_data_consent','STRING',NULL,NULL,NULL,NULL,1,1,'2026-07-04 02:21:30','2026-07-04 02:23:00'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_5 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'personal_data_consent');
SET @example_data_object_field_19 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_5 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'personal_data_consent');

-- rule_data_object_field: guarantee_agreement_consent
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_5,'guarantee_agreement_consent','担保合同授权','guarantee_agreement_consent','STRING',NULL,NULL,NULL,NULL,2,1,'2026-07-04 02:22:43','2026-07-04 02:23:00'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_5 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'guarantee_agreement_consent');
SET @example_data_object_field_20 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_5 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'guarantee_agreement_consent');

-- rule_data_object_field: credit_agreement
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_5,'credit_agreement','信贷合同','credit_agreement','STRING',NULL,NULL,NULL,NULL,3,1,'2026-07-04 02:23:48','2026-07-04 02:51:41'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_5 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'credit_agreement');
SET @example_data_object_field_21 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_5 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'credit_agreement');

-- rule_data_object_field: idcard_front_image
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_5,'idcard_front_image','证件正面图片','idcard_front_image','STRING',NULL,NULL,NULL,NULL,4,1,'2026-07-04 02:50:28','2026-07-04 02:50:28'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_5 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'idcard_front_image');
SET @example_data_object_field_22 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_5 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'idcard_front_image');

-- rule_data_object_field: idcard_back_image
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_5,'idcard_back_image','证件反面图片','idcard_back_image','STRING',NULL,NULL,NULL,NULL,5,1,'2026-07-04 02:50:40','2026-07-04 02:50:40'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_5 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'idcard_back_image');
SET @example_data_object_field_23 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_5 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'idcard_back_image');

-- rule_data_object_field: face_image
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_5,'face_image','人脸图片','face_image','STRING',NULL,NULL,NULL,NULL,6,1,'2026-07-04 02:51:14','2026-07-04 02:51:14'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_5 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'face_image');
SET @example_data_object_field_24 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_5 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'face_image');

-- rule_data_object_field: idcard_image
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_5,'idcard_image','证件照片','idcard_image','STRING',NULL,NULL,NULL,NULL,7,1,'2026-07-17 00:00:00','2026-07-17 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_5 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'idcard_image');
SET @example_data_object_field_25 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_5 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'idcard_image');

-- rule_data_object_field: HYBASE_X115
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_6,'HYBASE_X115','HYBASE_X115','HYBASE_X115','NUMBER',NULL,NULL,NULL,NULL,0,1,'2026-07-04 03:19:24','2026-07-04 03:19:24'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYBASE_X115');
SET @example_data_object_field_26 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYBASE_X115');

-- rule_data_object_field: HYBASE_X142
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_6,'HYBASE_X142','HYBASE_X142','HYBASE_X142','NUMBER',NULL,NULL,NULL,NULL,1,1,'2026-07-04 03:19:33','2026-07-04 03:19:33'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYBASE_X142');
SET @example_data_object_field_27 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYBASE_X142');

-- rule_data_object_field: HYBASE_X245
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_6,'HYBASE_X245','HYBASE_X245','HYBASE_X245','NUMBER',NULL,NULL,NULL,NULL,2,1,'2026-07-04 03:19:42','2026-07-04 03:19:42'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYBASE_X245');
SET @example_data_object_field_28 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYBASE_X245');

-- rule_data_object_field: HYDK_X1276
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_6,'HYDK_X1276','HYDK_X1276','HYDK_X1276','NUMBER',NULL,NULL,NULL,NULL,3,1,'2026-07-04 03:19:51','2026-07-04 03:19:51'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYDK_X1276');
SET @example_data_object_field_29 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYDK_X1276');

-- rule_data_object_field: HYDK_X1359
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_6,'HYDK_X1359','HYDK_X1359','HYDK_X1359','NUMBER',NULL,NULL,NULL,NULL,4,1,'2026-07-04 03:19:59','2026-07-04 03:19:59'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYDK_X1359');
SET @example_data_object_field_30 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYDK_X1359');

-- rule_data_object_field: HYDK_X2623
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_6,'HYDK_X2623','HYDK_X2623','HYDK_X2623','NUMBER',NULL,NULL,NULL,NULL,5,1,'2026-07-04 03:20:06','2026-07-04 03:20:06'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYDK_X2623');
SET @example_data_object_field_31 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYDK_X2623');

-- rule_data_object_field: HYDK_X2761
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_6,'HYDK_X2761','HYDK_X2761','HYDK_X2761','NUMBER',NULL,NULL,NULL,NULL,6,1,'2026-07-04 03:20:14','2026-07-04 03:20:14'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYDK_X2761');
SET @example_data_object_field_32 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYDK_X2761');

-- rule_data_object_field: HYDK_X760
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_6,'HYDK_X760','HYDK_X760','HYDK_X760','NUMBER',NULL,NULL,NULL,NULL,7,1,'2026-07-04 03:20:22','2026-07-04 03:20:22'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYDK_X760');
SET @example_data_object_field_33 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYDK_X760');

-- rule_data_object_field: HYJH_X54
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_6,'HYJH_X54','HYJH_X54','HYJH_X54','NUMBER',NULL,NULL,NULL,NULL,8,1,'2026-07-04 03:20:30','2026-07-04 03:20:30'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYJH_X54');
SET @example_data_object_field_34 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_6 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'HYJH_X54');

-- rule_data_object_field: clientAppName
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_7,'clientAppName','调用方应用名','clientAppName','STRING',NULL,NULL,NULL,NULL,0,1,'2026-07-04 03:53:49','2026-07-04 03:54:19'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_7 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'clientAppName');
SET @example_data_object_field_35 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_7 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'clientAppName');

-- rule_data_object_field: params
INSERT INTO rule_data_object_field (`project_id`,`scope`,`object_id`,`var_code`,`var_label`,`script_name`,`var_type`,`ref_object_code`,`ref_object_id`,`generic_type`,`parent_field_id`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL',@example_data_object_7,'params','输入参数','params','OBJECT',NULL,NULL,NULL,NULL,1,1,'2026-07-04 03:54:13','2026-07-04 03:54:13'
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_7 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'params');
SET @example_data_object_field_36 = (SELECT existing.id FROM rule_data_object_field existing WHERE existing.`object_id` <=> @example_data_object_7 AND existing.`parent_field_id` <=> NULL AND existing.`var_code` <=> 'params');

-- rule_data_object_field_option: 1
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'0','父亲',0
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '0');
SET @example_data_object_field_option_1 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '0');

-- rule_data_object_field_option: 2
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'1','母亲',1
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '1');
SET @example_data_object_field_option_2 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '1');

-- rule_data_object_field_option: 3
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'2','爷爷',2
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '2');
SET @example_data_object_field_option_3 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '2');

-- rule_data_object_field_option: 4
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'3','奶奶',3
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '3');
SET @example_data_object_field_option_4 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '3');

-- rule_data_object_field_option: 5
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'4','外公',4
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '4');
SET @example_data_object_field_option_5 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '4');

-- rule_data_object_field_option: 6
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'5','外婆',5
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '5');
SET @example_data_object_field_option_6 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '5');

-- rule_data_object_field_option: 7
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'6','哥哥',6
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '6');
SET @example_data_object_field_option_7 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '6');

-- rule_data_object_field_option: 8
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'7','姐姐',7
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '7');
SET @example_data_object_field_option_8 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '7');

-- rule_data_object_field_option: 9
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'8','弟弟',8
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '8');
SET @example_data_object_field_option_9 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '8');

-- rule_data_object_field_option: 10
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'9','妹妹',9
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '9');
SET @example_data_object_field_option_10 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '9');

-- rule_data_object_field_option: 11
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'10','表哥',10
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '10');
SET @example_data_object_field_option_11 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '10');

-- rule_data_object_field_option: 12
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'11','表姐',11
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '11');
SET @example_data_object_field_option_12 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '11');

-- rule_data_object_field_option: 13
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'12','堂兄',12
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '12');
SET @example_data_object_field_option_13 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '12');

-- rule_data_object_field_option: 14
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'13','堂姐',13
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '13');
SET @example_data_object_field_option_14 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '13');

-- rule_data_object_field_option: 15
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'14','表弟',14
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '14');
SET @example_data_object_field_option_15 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '14');

-- rule_data_object_field_option: 16
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'15','表妹',15
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '15');
SET @example_data_object_field_option_16 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '15');

-- rule_data_object_field_option: 17
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'16','堂弟',16
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '16');
SET @example_data_object_field_option_17 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '16');

-- rule_data_object_field_option: 18
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'17','堂妹',17
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '17');
SET @example_data_object_field_option_18 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '17');

-- rule_data_object_field_option: 19
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'18','叔叔',18
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '18');
SET @example_data_object_field_option_19 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '18');

-- rule_data_object_field_option: 20
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_3,'-1','其他',19
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '-1');
SET @example_data_object_field_option_20 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_3 AND existing.`option_value` <=> '-1');

-- rule_data_object_field_option: 21
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_4,'0','亲属',0
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_4 AND existing.`option_value` <=> '0');
SET @example_data_object_field_option_21 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_4 AND existing.`option_value` <=> '0');

-- rule_data_object_field_option: 22
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_4,'1','朋友',1
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_4 AND existing.`option_value` <=> '1');
SET @example_data_object_field_option_22 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_4 AND existing.`option_value` <=> '1');

-- rule_data_object_field_option: 23
INSERT INTO rule_data_object_field_option (`field_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_data_object_field_4,'-1','其他',2
WHERE NOT EXISTS (SELECT 1 FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_4 AND existing.`option_value` <=> '-1');
SET @example_data_object_field_option_23 = (SELECT existing.id FROM rule_data_object_field_option existing WHERE existing.`field_id` <=> @example_data_object_field_4 AND existing.`option_value` <=> '-1');

-- rule_db_datasource: rule_engine_mysql
INSERT INTO rule_db_datasource (`project_id`,`scope`,`datasource_code`,`datasource_name`,`db_type`,`connection_mode`,`host`,`port`,`database_name`,`jdbc_params`,`driver_class_name`,`jdbc_url`,`username`,`password`,`ssh_host`,`ssh_port`,`ssh_username`,`ssh_password`,`ssh_private_key`,`ssh_passphrase`,`ssh_timeout_ms`,`max_pool_size`,`min_idle`,`connection_timeout_ms`,`idle_timeout_ms`,`validation_query`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','rule_engine_mysql','业务数据库','MYSQL','DIRECT','127.0.0.1',3306,DATABASE(),'useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false','com.mysql.cj.jdbc.Driver',CONCAT('jdbc:mysql://127.0.0.1:3306/',DATABASE(),'?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false'),'root','1qaz@WSX','',22,'','','','',10000,5,1,3000,600000,'SELECT 1','',1,'2026-07-04 03:14:11','2026-07-04 03:14:11'
WHERE NOT EXISTS (SELECT 1 FROM rule_db_datasource existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`datasource_code` <=> 'rule_engine_mysql');
SET @example_db_datasource_1 = (SELECT existing.id FROM rule_db_datasource existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`datasource_code` <=> 'rule_engine_mysql');

-- rule_definition: JCZR
INSERT INTO rule_definition (`project_id`,`project_code`,`project_name`,`rule_code`,`rule_name`,`model_type`,`description`,`scope`,`current_version`,`published_version`,`status`,`input_fields`,`output_fields`,`create_by`,`create_time`,`update_by`,`update_time`)
SELECT 0,NULL,NULL,'JCZR','基础准入','RULE_SET','','GLOBAL',5,NULL,1,'','',NULL,'2026-07-04 03:26:10',NULL,'2026-07-04 03:26:10'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition existing WHERE existing.`rule_code` <=> 'JCZR');
SET @example_definition_1 = (SELECT existing.id FROM rule_definition existing WHERE existing.`rule_code` <=> 'JCZR');

-- rule_project: BNLP
INSERT INTO rule_project (`project_code`,`project_name`,`description`,`status`,`access_token`,`create_by`,`create_time`,`update_by`,`update_time`)
SELECT 'BNLP','先享后付','循环额度分期产品风控样例',1,'327d0665e1074c2e9c349ed38497801c',NULL,'2026-07-04 03:09:21',NULL,'2026-07-04 03:09:21'
WHERE NOT EXISTS (SELECT 1 FROM rule_project existing WHERE existing.`project_code` <=> 'BNLP');
SET @example_project_1 = (SELECT existing.id FROM rule_project existing WHERE existing.`project_code` <=> 'BNLP');

-- rule_definition: BDZR
INSERT INTO rule_definition (`project_id`,`project_code`,`project_name`,`rule_code`,`rule_name`,`model_type`,`description`,`scope`,`current_version`,`published_version`,`status`,`input_fields`,`output_fields`,`create_by`,`create_time`,`update_by`,`update_time`)
SELECT @example_project_1,'BNLP','先享后付','BDZR','黑名单准入','RULE_SET','','PROJECT',2,1,1,'','',NULL,'2026-07-04 03:33:34',NULL,'2026-07-04 03:33:34'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition existing WHERE existing.`rule_code` <=> 'BDZR');
SET @example_definition_2 = (SELECT existing.id FROM rule_definition existing WHERE existing.`rule_code` <=> 'BDZR');

-- rule_definition: MSZR
INSERT INTO rule_definition (`project_id`,`project_code`,`project_name`,`rule_code`,`rule_name`,`model_type`,`description`,`scope`,`current_version`,`published_version`,`status`,`input_fields`,`output_fields`,`create_by`,`create_time`,`update_by`,`update_time`)
SELECT 0,NULL,NULL,'MSZR','外数准入','RULE_SET','','GLOBAL',2,NULL,1,'','',NULL,'2026-07-04 03:53:19',NULL,'2026-07-04 03:53:19'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition existing WHERE existing.`rule_code` <=> 'MSZR');
SET @example_definition_3 = (SELECT existing.id FROM rule_definition existing WHERE existing.`rule_code` <=> 'MSZR');

-- rule_definition: DBQUERY
INSERT INTO rule_definition (`project_id`,`project_code`,`project_name`,`rule_code`,`rule_name`,`model_type`,`description`,`scope`,`current_version`,`published_version`,`status`,`input_fields`,`output_fields`,`create_by`,`create_time`,`update_by`,`update_time`)
SELECT @example_project_1,'BNLP','先享后付','DBQUERY','数据库查询规则','RULE_SET','','PROJECT',4,2,1,'','',NULL,'2026-07-04 04:05:59',NULL,'2026-07-04 04:05:59'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition existing WHERE existing.`rule_code` <=> 'DBQUERY');
SET @example_definition_4 = (SELECT existing.id FROM rule_definition existing WHERE existing.`rule_code` <=> 'DBQUERY');

-- rule_definition: TEST
INSERT INTO rule_definition (`project_id`,`project_code`,`project_name`,`rule_code`,`rule_name`,`model_type`,`description`,`scope`,`current_version`,`published_version`,`status`,`input_fields`,`output_fields`,`create_by`,`create_time`,`update_by`,`update_time`)
SELECT 0,NULL,NULL,'TEST','函数测试','FLOW','','GLOBAL',2,NULL,1,'','',NULL,'2026-07-07 00:37:55',NULL,'2026-07-07 00:37:55'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition existing WHERE existing.`rule_code` <=> 'TEST');
SET @example_definition_5 = (SELECT existing.id FROM rule_definition existing WHERE existing.`rule_code` <=> 'TEST');

-- rule_definition: JCLTest
INSERT INTO rule_definition (`project_id`,`project_code`,`project_name`,`rule_code`,`rule_name`,`model_type`,`description`,`scope`,`current_version`,`published_version`,`status`,`input_fields`,`output_fields`,`create_by`,`create_time`,`update_by`,`update_time`)
SELECT 0,NULL,NULL,'JCLTest','决策流测试','FLOW','','GLOBAL',0,NULL,1,'','',NULL,'2026-07-07 02:33:42',NULL,'2026-07-07 02:33:42'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition existing WHERE existing.`rule_code` <=> 'JCLTest');
SET @example_definition_6 = (SELECT existing.id FROM rule_definition existing WHERE existing.`rule_code` <=> 'JCLTest');

-- rule_variable: age
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','age','年龄','age','NUMBER','INPUT',NULL,'','','22','',0,1,'2026-07-04 01:18:49','2026-07-04 01:18:49'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'age');
SET @example_variable_37 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'age');

-- rule_variable: result
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','result','决策结果','result','ENUM','COMPUTED',NULL,'','','','',0,1,'2026-07-04 02:34:27','2026-07-04 02:34:27'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'result');
SET @example_variable_141 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'result');

-- rule_variable: amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','amount','金额','amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 01:54:19','2026-07-04 01:54:19'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'amount');
SET @example_variable_77 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'amount');

-- rule_variable: period
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','period','期数','period','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 01:54:06','2026-07-04 01:54:06'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'period');
SET @example_variable_76 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'period');

-- rule_model: score_f1
INSERT INTO rule_model (`project_id`,`project_code`,`project_name`,`scope`,`model_code`,`model_name`,`model_type`,`model_format`,`description`,`model_content`,`model_file_name`,`model_file_size`,`model_config`,`input_field_count`,`output_field_count`,`target_categories`,`model_version`,`training_info`,`current_version`,`published_version`,`status`,`create_by`,`create_time`,`update_by`,`update_time`)
SELECT NULL,NULL,NULL,'GLOBAL','score_f1','欺诈分F1','XGBOOST','PMML','','PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9InllcyI/Pgo8UE1NTCB4bWxucz0iaHR0cDovL3d3dy5kbWcub3JnL1BNTUwtNF80IiB4bWxuczpkYXRhPSJodHRwOi8vanBtbWwub3JnL2pwbW1sLW1vZGVsL0lubGluZVRhYmxlIiB2ZXJzaW9uPSI0LjQiPgoJPEhlYWRlcj4KCQk8QXBwbGljYXRpb24gbmFtZT0iU2tMZWFybjJQTU1MIHBhY2thZ2UiIHZlcnNpb249IjAuOTAuNCIvPgoJCTxUaW1lc3RhbXA+MjAyMy0xMC0wOFQwOTo1ODoxMFo8L1RpbWVzdGFtcD4KCTwvSGVhZGVyPgoJPE1pbmluZ0J1aWxkVGFzaz4KCQk8RXh0ZW5zaW9uIG5hbWU9InJlcHIiPlBNTUxQaXBlbGluZShzdGVwcz1bKCdwcmVwcm9jZXNzaW5nJywgRGF0YUZyYW1lTWFwcGVyKGRmX291dD1UcnVlLCBkcm9wX2NvbHM9W10sCiAgICAgICAgICAgICAgICBmZWF0dXJlcz1bKFsnSFlCQVNFX1gxMTUnXSwKICAgICAgICAgICAgICAgICAgICAgICAgICAgRXhwcmVzc2lvblRyYW5zZm9ybWVyKGV4cHI9JzguMCBpZiBYWzBdICZsdDsgMi4wIGVsc2UgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnKDI1LjE3IGlmIFhbMF0gJmd0Oz0gMi4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2FuZCBYWzBdICZsdDsgMy4wIGVsc2UgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnKDQwLjU3IGlmIFhbMF0gJmd0Oz0gMy4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2Vsc2UgLTguODkpKScpKSwKICAgICAgICAgICAgICAgICAgICAgICAgICAoWydIWUJBU0VfWDE0MiddLAogICAgICAgICAgICAgICAgICAgICAgICAgICBFeHByZXNzaW9uVHJhbnNmb3JtZXIoZXhwcj0nNTQuNjIgaWYgWFswXSAmbHQ7IDMuMCAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdlbHNlICgzNC4yMiBpZiBYWzBdICZndDs9ICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJzMuMCBhbmQgWFswXSAmbHQ7IDQuMCBlbHNlICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJygyNi44OCBpZiBYWzBdICZndDs9IDQuMCAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdlbHNlIDY0LjQ0KSknKSksCiAgICAgICAgICAgICAgICAgICAgICAgICAgKFsnSFlCQVNFX1gyNDUnXSwKICAgICAgICAgICAgICAgICAgICAgICAgICAgRXhwcmVzc2lvblRyLi4uCiAgICAgICAgICAgICAgICAgICAgICAgICAgKFsnSFlES19YMjc2MSddLAogICAgICAgICAgICAgICAgICAgICAgICAgICBFeHByZXNzaW9uVHJhbnNmb3JtZXIoZXhwcj0nMTQuODcgaWYgWFswXSAmbHQ7IDM2ODUuMCAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdlbHNlICgyMi4xOCBpZiBYWzBdICZndDs9ICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJzM2ODUuMCBhbmQgWFswXSAmbHQ7ICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJzk5NzAuMCBlbHNlICgzMC40OCBpZiAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdYWzBdICZndDs9IDk5NzAuMCBhbmQgWFswXSAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICcmbHQ7IDIyNTAwLjAgZWxzZSAoNDMuMDQgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnaWYgWFswXSAmZ3Q7PSAyMjUwMC4wIGVsc2UgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnMzguMDgpKSknKSksCiAgICAgICAgICAgICAgICAgICAgICAgICAgKFsnSFlES19YNzYwJ10sCiAgICAgICAgICAgICAgICAgICAgICAgICAgIEV4cHJlc3Npb25UcmFuc2Zvcm1lcihleHByPSczNC4xNCBpZiBYWzBdICZsdDsgMS4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2Vsc2UgKDExLjQxIGlmIFhbMF0gJmd0Oz0gJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnMS4wIGVsc2UgNDYuMjkpJykpLAogICAgICAgICAgICAgICAgICAgICAgICAgIChbJ0hZSkhfWDU0J10sCiAgICAgICAgICAgICAgICAgICAgICAgICAgIEV4cHJlc3Npb25UcmFuc2Zvcm1lcihleHByPSczNi45OSBpZiBYWzBdICZsdDsgMS4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2Vsc2UgLTUuMDcnKSldKSksCiAgICAgICAoJ3Njb3JlY2FyZCcsIExpbmVhclJlZ3Jlc3Npb24oKSldKTwvRXh0ZW5zaW9uPgoJPC9NaW5pbmdCdWlsZFRhc2s+Cgk8RGF0YURpY3Rpb25hcnk+CgkJPERhdGFGaWVsZCBuYW1lPSJzY29yZSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUJBU0VfWDExNSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUJBU0VfWDE0MiIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUJBU0VfWDI0NSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWURLX1gxMjc2IiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiLz4KCQk8RGF0YUZpZWxkIG5hbWU9IkhZREtfWDEzNTkiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSIvPgoJCTxEYXRhRmllbGQgbmFtZT0iSFlES19YMjYyMyIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWURLX1gyNzYxIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiLz4KCQk8RGF0YUZpZWxkIG5hbWU9IkhZREtfWDc2MCIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUpIX1g1NCIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+Cgk8L0RhdGFEaWN0aW9uYXJ5PgoJPFJlZ3Jlc3Npb25Nb2RlbCBmdW5jdGlvbk5hbWU9InJlZ3Jlc3Npb24iIGFsZ29yaXRobU5hbWU9InNrbGVhcm4ubGluZWFyX21vZGVsLl9iYXNlLkxpbmVhclJlZ3Jlc3Npb24iPgoJCTxNaW5pbmdTY2hlbWE+CgkJCTxNaW5pbmdGaWVsZCBuYW1lPSJzY29yZSIgdXNhZ2VUeXBlPSJ0YXJnZXQiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZQkFTRV9YMTE1Ii8+CgkJCTxNaW5pbmdGaWVsZCBuYW1lPSJIWUJBU0VfWDE0MiIvPgoJCQk8TWluaW5nRmllbGQgbmFtZT0iSFlCQVNFX1gyNDUiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDEyNzYiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDEzNTkiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDI2MjMiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDI3NjEiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDc2MCIvPgoJCQk8TWluaW5nRmllbGQgbmFtZT0iSFlKSF9YNTQiLz4KCQk8L01pbmluZ1NjaGVtYT4KCQk8TG9jYWxUcmFuc2Zvcm1hdGlvbnM+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCg4LjAgaWYgWFswXSAmbHQ7IDIuMCBlbHNlICgyNS4xNyBpZiBYWzBdICZndDs9IDIuMCBhbmQgWFswXSAmbHQ7IDMuMCBlbHNlICg0MC41NyBpZiBYWzBdICZndDs9IDMuMCBlbHNlIC04Ljg5KSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gxMTUiLz4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIuMDwvQ29uc3RhbnQ+CgkJCQkJPC9BcHBseT4KCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+OC4wPC9Db25zdGFudD4KCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJhbmQiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDExNSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4yLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gxMTUiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+My4wPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4yNS4xNzwvQ29uc3RhbnQ+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDExNSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj40MC41NzwvQ29uc3RhbnQ+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+LTguODk8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCTwvQXBwbHk+CgkJCQk8L0FwcGx5PgoJCQk8L0Rlcml2ZWRGaWVsZD4KCQkJPERlcml2ZWRGaWVsZCBuYW1lPSJldmFsKDU0LjYyIGlmIFhbMF0gJmx0OyAzLjAgZWxzZSAoMzQuMjIgaWYgWFswXSAmZ3Q7PSAzLjAgYW5kIFhbMF0gJmx0OyA0LjAgZWxzZSAoMjYuODggaWYgWFswXSAmZ3Q7PSA0LjAgZWxzZSA2NC40NCkpKSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIj4KCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZQkFTRV9YMTQyIi8+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zLjA8L0NvbnN0YW50PgoJCQkJCTwvQXBwbHk+CgkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjU0LjYyPC9Db25zdGFudD4KCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJhbmQiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDE0MiIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gxNDIiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NC4wPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNC4yMjwvQ29uc3RhbnQ+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDE0MiIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj40LjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4yNi44ODwvQ29uc3RhbnQ+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NjQuNDQ8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCTwvQXBwbHk+CgkJCQk8L0FwcGx5PgoJCQk8L0Rlcml2ZWRGaWVsZD4KCQkJPERlcml2ZWRGaWVsZCBuYW1lPSJldmFsKDY0LjYgaWYgWFswXSAmbHQ7IDAuMzY4NDIwODU4NzI1ODYzOCBlbHNlICg0MS43NyBpZiBYWzBdICZndDs9IDAuMzY4NDIwODU4NzI1ODYzOCBhbmQgWFswXSAmbHQ7IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlICgyMS41NiBpZiBYWzBdICZndDs9IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlIDEzLjM3KSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gyNDUiLz4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjAuMzY4NDIwODU4NzI1ODYzODwvQ29uc3RhbnQ+CgkJCQkJPC9BcHBseT4KCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NjQuNjwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gyNDUiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC4zNjg0MjA4NTg3MjU4NjM4PC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZQkFTRV9YMjQ1Ii8+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjAuNzA1ODgxOTM3NzE2NTA3MjwvQ29uc3RhbnQ+CgkJCQkJCQk8L0FwcGx5PgoJCQkJCQk8L0FwcGx5PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NDEuNzc8L0NvbnN0YW50PgoJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gyNDUiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC43MDU4ODE5Mzc3MTY1MDcyPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MjEuNTY8L0NvbnN0YW50PgoJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjEzLjM3PC9Db25zdGFudD4KCQkJCQkJPC9BcHBseT4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCgzNC4wNCBpZiBYWzBdICZsdDsgMS4wIGVsc2UgKDMuNSBpZiBYWzBdICZndDs9IDEuMCBlbHNlIDU0LjAxKSkiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWURLX1gxMjc2Ii8+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xLjA8L0NvbnN0YW50PgoJCQkJCTwvQXBwbHk+CgkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjM0LjA0PC9Db25zdGFudD4KCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDEyNzYiLz4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xLjA8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+My41PC9Db25zdGFudD4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjU0LjAxPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCg1MC40NCBpZiBYWzBdICZsdDsgMC4xMTk5OTk5OTg0IGVsc2UgKDQxLjY3IGlmIFhbMF0gJmd0Oz0gMC4xMTk5OTk5OTg0IGFuZCBYWzBdICZsdDsgMC4yNDk5OTk5NzkxNjY2Njg0IGVsc2UgKDI4LjY2IGlmIFhbMF0gJmd0Oz0gMC4yNDk5OTk5NzkxNjY2Njg0IGFuZCBYWzBdICZsdDsgMC40OTk5OTk4NzUwMDAwMzEyIGVsc2UgKC00LjkyIGlmIFhbMF0gJmd0Oz0gMC40OTk5OTk4NzUwMDAwMzEyIGVsc2UgNjEuODYpKSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC4xMTk5OTk5OTg0PC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj41MC40NDwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4wLjExOTk5OTk5ODQ8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4wLjI0OTk5OTk3OTE2NjY2ODQ8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjQxLjY3PC9Db25zdGFudD4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImFuZCI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC4yNDk5OTk5NzkxNjY2Njg0PC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDEzNTkiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjAuNDk5OTk5ODc1MDAwMDMxMjwvQ29uc3RhbnQ+CgkJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MjguNjY8L0NvbnN0YW50PgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC40OTk5OTk4NzUwMDAwMzEyPC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4tNC45MjwvQ29uc3RhbnQ+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjYxLjg2PC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJPC9BcHBseT4KCQkJCTwvQXBwbHk+CgkJCTwvRGVyaXZlZEZpZWxkPgoJCQk8RGVyaXZlZEZpZWxkIG5hbWU9ImV2YWwoLTMuMDIgaWYgWFswXSAmbHQ7IDEzMTg4LjAgZWxzZSAoMjQuNzIgaWYgWFswXSAmZ3Q7PSAxMzE4OC4wIGFuZCBYWzBdICZsdDsgMTU3NjA3LjAgZWxzZSAoMzIuOTEgaWYgWFswXSAmZ3Q7PSAxNTc2MDcuMCBhbmQgWFswXSAmbHQ7IDM4MDAwMC4wIGVsc2UgKDcxLjY0IGlmIFhbMF0gJmd0Oz0gMzgwMDAwLjAgZWxzZSA1OS4yNSkpKSkiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWURLX1gyNjIzIi8+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xMzE4OC4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4tMy4wMjwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjYyMyIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xMzE4OC4wPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI2MjMiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MTU3NjA3LjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjI0LjcyPC9Db25zdGFudD4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImFuZCI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjYyMyIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MTU3NjA3LjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjYyMyIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MzgwMDAwLjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjMyLjkxPC9Db25zdGFudD4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI2MjMiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjM4MDAwMC4wPC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj43MS42NDwvQ29uc3RhbnQ+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjU5LjI1PC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJPC9BcHBseT4KCQkJCTwvQXBwbHk+CgkJCTwvRGVyaXZlZEZpZWxkPgoJCQk8RGVyaXZlZEZpZWxkIG5hbWU9ImV2YWwoMTQuODcgaWYgWFswXSAmbHQ7IDM2ODUuMCBlbHNlICgyMi4xOCBpZiBYWzBdICZndDs9IDM2ODUuMCBhbmQgWFswXSAmbHQ7IDk5NzAuMCBlbHNlICgzMC40OCBpZiBYWzBdICZndDs9IDk5NzAuMCBhbmQgWFswXSAmbHQ7IDIyNTAwLjAgZWxzZSAoNDMuMDQgaWYgWFswXSAmZ3Q7PSAyMjUwMC4wIGVsc2UgMzguMDgpKSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MzY4NS4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xNC44NzwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNjg1LjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj45OTcwLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIyLjE4PC9Db25zdGFudD4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImFuZCI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+OTk3MC4wPC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI3NjEiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIyNTAwLjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjMwLjQ4PC9Db25zdGFudD4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI3NjEiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIyNTAwLjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjQzLjA0PC9Db25zdGFudD4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MzguMDg8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCgzNC4xNCBpZiBYWzBdICZsdDsgMS4wIGVsc2UgKDExLjQxIGlmIFhbMF0gJmd0Oz0gMS4wIGVsc2UgNDYuMjkpKSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIj4KCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDc2MCIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MS4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNC4xNDwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWURLX1g3NjAiLz4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xLjA8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MTEuNDE8L0NvbnN0YW50PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NDYuMjk8L0NvbnN0YW50PgoJCQkJCTwvQXBwbHk+CgkJCQk8L0FwcGx5PgoJCQk8L0Rlcml2ZWRGaWVsZD4KCQkJPERlcml2ZWRGaWVsZCBuYW1lPSJldmFsKDM2Ljk5IGlmIFhbMF0gJmx0OyAxLjAgZWxzZSAtNS4wNykiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUpIX1g1NCIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MS4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNi45OTwvQ29uc3RhbnQ+CgkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPi01LjA3PC9Db25zdGFudD4KCQkJCTwvQXBwbHk+CgkJCTwvRGVyaXZlZEZpZWxkPgoJCTwvTG9jYWxUcmFuc2Zvcm1hdGlvbnM+CgkJPFJlZ3Jlc3Npb25UYWJsZSBpbnRlcmNlcHQ9IjAuMCI+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoOC4wIGlmIFhbMF0gJmx0OyAyLjAgZWxzZSAoMjUuMTcgaWYgWFswXSAmZ3Q7PSAyLjAgYW5kIFhbMF0gJmx0OyAzLjAgZWxzZSAoNDAuNTcgaWYgWFswXSAmZ3Q7PSAzLjAgZWxzZSAtOC44OSkpKSIgY29lZmZpY2llbnQ9IjEuMCIvPgoJCQk8TnVtZXJpY1ByZWRpY3RvciBuYW1lPSJldmFsKDU0LjYyIGlmIFhbMF0gJmx0OyAzLjAgZWxzZSAoMzQuMjIgaWYgWFswXSAmZ3Q7PSAzLjAgYW5kIFhbMF0gJmx0OyA0LjAgZWxzZSAoMjYuODggaWYgWFswXSAmZ3Q7PSA0LjAgZWxzZSA2NC40NCkpKSIgY29lZmZpY2llbnQ9IjEuMCIvPgoJCQk8TnVtZXJpY1ByZWRpY3RvciBuYW1lPSJldmFsKDY0LjYgaWYgWFswXSAmbHQ7IDAuMzY4NDIwODU4NzI1ODYzOCBlbHNlICg0MS43NyBpZiBYWzBdICZndDs9IDAuMzY4NDIwODU4NzI1ODYzOCBhbmQgWFswXSAmbHQ7IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlICgyMS41NiBpZiBYWzBdICZndDs9IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlIDEzLjM3KSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoMzQuMDQgaWYgWFswXSAmbHQ7IDEuMCBlbHNlICgzLjUgaWYgWFswXSAmZ3Q7PSAxLjAgZWxzZSA1NC4wMSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoNTAuNDQgaWYgWFswXSAmbHQ7IDAuMTE5OTk5OTk4NCBlbHNlICg0MS42NyBpZiBYWzBdICZndDs9IDAuMTE5OTk5OTk4NCBhbmQgWFswXSAmbHQ7IDAuMjQ5OTk5OTc5MTY2NjY4NCBlbHNlICgyOC42NiBpZiBYWzBdICZndDs9IDAuMjQ5OTk5OTc5MTY2NjY4NCBhbmQgWFswXSAmbHQ7IDAuNDk5OTk5ODc1MDAwMDMxMiBlbHNlICgtNC45MiBpZiBYWzBdICZndDs9IDAuNDk5OTk5ODc1MDAwMDMxMiBlbHNlIDYxLjg2KSkpKSIgY29lZmZpY2llbnQ9IjEuMCIvPgoJCQk8TnVtZXJpY1ByZWRpY3RvciBuYW1lPSJldmFsKC0zLjAyIGlmIFhbMF0gJmx0OyAxMzE4OC4wIGVsc2UgKDI0LjcyIGlmIFhbMF0gJmd0Oz0gMTMxODguMCBhbmQgWFswXSAmbHQ7IDE1NzYwNy4wIGVsc2UgKDMyLjkxIGlmIFhbMF0gJmd0Oz0gMTU3NjA3LjAgYW5kIFhbMF0gJmx0OyAzODAwMDAuMCBlbHNlICg3MS42NCBpZiBYWzBdICZndDs9IDM4MDAwMC4wIGVsc2UgNTkuMjUpKSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoMTQuODcgaWYgWFswXSAmbHQ7IDM2ODUuMCBlbHNlICgyMi4xOCBpZiBYWzBdICZndDs9IDM2ODUuMCBhbmQgWFswXSAmbHQ7IDk5NzAuMCBlbHNlICgzMC40OCBpZiBYWzBdICZndDs9IDk5NzAuMCBhbmQgWFswXSAmbHQ7IDIyNTAwLjAgZWxzZSAoNDMuMDQgaWYgWFswXSAmZ3Q7PSAyMjUwMC4wIGVsc2UgMzguMDgpKSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoMzQuMTQgaWYgWFswXSAmbHQ7IDEuMCBlbHNlICgxMS40MSBpZiBYWzBdICZndDs9IDEuMCBlbHNlIDQ2LjI5KSkiIGNvZWZmaWNpZW50PSIxLjAiLz4KCQkJPE51bWVyaWNQcmVkaWN0b3IgbmFtZT0iZXZhbCgzNi45OSBpZiBYWzBdICZsdDsgMS4wIGVsc2UgLTUuMDcpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJPC9SZWdyZXNzaW9uVGFibGU+Cgk8L1JlZ3Jlc3Npb25Nb2RlbD4KPC9QTU1MPgo=','fraud_scorecard.pmml',16103,'{"summary": "Regression", "functionName": "regression", "algorithmName": "sklearn.linear_model._base.LinearRegression"}',9,1,NULL,NULL,NULL,4,4,1,NULL,'2026-07-04 03:15:32',NULL,'2026-07-04 03:15:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_model existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`model_code` <=> 'score_f1');
SET @example_model_1 = (SELECT existing.id FROM rule_model existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`model_code` <=> 'score_f1');

-- rule_variable: score
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','score','评分','score','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:25:26','2026-07-04 02:25:26'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'score');
SET @example_variable_130 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'score');

-- rule_model_output_field: score
INSERT INTO rule_model_output_field (`model_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`target_field`,`feature_name`,`transform_operand`,`is_probability`,`category`,`sort_order`,`create_time`)
SELECT @example_model_1,@example_variable_130,'VARIABLE','score','评分','score','DOUBLE',NULL,NULL,NULL,0,NULL,0,'2026-07-04 03:15:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_model_output_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'score');
SET @example_model_output_field_1 = (SELECT existing.id FROM rule_model_output_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'score');

-- rule_definition_content: 1
INSERT INTO rule_definition_content (`definition_id`,`model_json`,`compiled_script`,`compiled_type`,`compile_status`,`compile_message`,`compile_time`,`script_mode`,`update_time`)
SELECT @example_definition_1,JSON_SET('{"executionMode":"SERIAL","rules":[{"ruleCode":"R0001","ruleName":"规则1","priority":1,"enabled":true,"conditionRoot":{"type":"group","op":"OR","children":[{"type":"leaf","varCode":"age","varLabel":"年龄 age","varType":"NUMBER","enumOptions":"","operator":"<","valueKind":"CONST","value":"18","_varId":37,"_refType":"VARIABLE"},{"type":"leaf","varCode":"age","varLabel":"年龄 age","varType":"NUMBER","enumOptions":"","operator":">","valueKind":"CONST","value":"55","_varId":37,"_refType":"VARIABLE"}]},"actionData":[{"type":"assign","target":"result","value":"1","_targetVarId":141,"_targetRefType":"VARIABLE","_varId":141,"_refType":"VARIABLE"},{"type":"assign","target":"amount","value":"0","_targetVarId":77,"_targetRefType":"VARIABLE","_varId":77,"_refType":"VARIABLE"},{"type":"assign","target":"period","value":"0","_targetVarId":76,"_targetRefType":"VARIABLE","_varId":76,"_refType":"VARIABLE"}],"status":1},{"ruleCode":"R0002","ruleName":"规则2","priority":1,"enabled":true,"conditionRoot":{"type":"group","op":"OR","children":[{"type":"leaf","varCode":"score_f1.score","varLabel":"欺诈分F1/评分 score","varType":"DOUBLE","enumOptions":"","operator":"is_null","valueKind":"CONST","value":"","_varId":1,"_refType":"MODEL_OUTPUT","rightVarType":"","rightVarLabel":""},{"type":"group","op":"AND","children":[{"type":"leaf","varCode":"score_f1.score","varLabel":"欺诈分F1/评分 score","varType":"DOUBLE","enumOptions":"","operator":">=","valueKind":"CONST","value":"0","_varId":1,"_refType":"MODEL_OUTPUT"},{"type":"leaf","varCode":"score_f1.score","varLabel":"欺诈分F1/评分 score","varType":"DOUBLE","enumOptions":"","operator":"<","valueKind":"CONST","value":"250","_varId":1,"_refType":"MODEL_OUTPUT"}]}]},"actionData":[{"type":"assign","target":"result","value":"1","_targetVarId":141,"_targetRefType":"VARIABLE","_varId":141,"_refType":"VARIABLE"},{"type":"assign","target":"amount","value":"0","_targetVarId":77,"_targetRefType":"VARIABLE","_varId":77,"_refType":"VARIABLE"},{"type":"assign","target":"period","value":"0","_targetVarId":76,"_targetRefType":"VARIABLE","_varId":76,"_refType":"VARIABLE"}],"status":1}]}', '$."rules"[0]."conditionRoot"."children"[0]."_varId"', CAST(@example_variable_37 AS UNSIGNED), '$."rules"[0]."conditionRoot"."children"[1]."_varId"', CAST(@example_variable_37 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_targetVarId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_varId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[0]."actionData"[1]."_targetVarId"', CAST(@example_variable_77 AS UNSIGNED), '$."rules"[0]."actionData"[1]."_varId"', CAST(@example_variable_77 AS UNSIGNED), '$."rules"[0]."actionData"[2]."_targetVarId"', CAST(@example_variable_76 AS UNSIGNED), '$."rules"[0]."actionData"[2]."_varId"', CAST(@example_variable_76 AS UNSIGNED), '$."rules"[1]."conditionRoot"."children"[0]."_varId"', CAST(@example_model_output_field_1 AS UNSIGNED), '$."rules"[1]."conditionRoot"."children"[1]."children"[0]."_varId"', CAST(@example_model_output_field_1 AS UNSIGNED), '$."rules"[1]."conditionRoot"."children"[1]."children"[1]."_varId"', CAST(@example_model_output_field_1 AS UNSIGNED), '$."rules"[1]."actionData"[0]."_targetVarId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[1]."actionData"[0]."_varId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[1]."actionData"[1]."_targetVarId"', CAST(@example_variable_77 AS UNSIGNED), '$."rules"[1]."actionData"[1]."_varId"', CAST(@example_variable_77 AS UNSIGNED), '$."rules"[1]."actionData"[2]."_targetVarId"', CAST(@example_variable_76 AS UNSIGNED), '$."rules"[1]."actionData"[2]."_varId"', CAST(@example_variable_76 AS UNSIGNED)),'_ruleSetHits = [];
_ruleSetHitCount = 0;
_ruleSetMatched = false;
if (!_ruleSetMatched && ((age < 18 || age > 55))) {
    result = 1
    amount = 0
    period = 0
    _ruleSetHit = {"ruleCode": "R0001", "ruleName": "规则1", "priority": 1, "order": 1};
    if (_ruleSetHitCount == 0) {
        _ruleSetHits = [_ruleSetHit];
    }
    _ruleSetHitCount = _ruleSetHitCount + 1;
    _ruleSetMatched = true;
}
if (!_ruleSetMatched && ((score_f1.score == null || (score_f1.score >= 0 && score_f1.score < 250)))) {
    result = 1
    amount = 0
    period = 0
    _ruleSetHit = {"ruleCode": "R0002", "ruleName": "规则2", "priority": 1, "order": 2};
    if (_ruleSetHitCount == 0) {
        _ruleSetHits = [_ruleSetHit];
    } else if (_ruleSetHitCount == 1) {
        _ruleSetHits = [_ruleSetHits[0], _ruleSetHit];
    }
    _ruleSetHitCount = _ruleSetHitCount + 1;
    _ruleSetMatched = true;
}
_ruleSetResult = _ruleSetHits;
_ruleSetResult
','QLEXPRESS',1,NULL,'2026-07-04 03:29:59','visual','2026-07-04 03:26:10'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_content existing WHERE existing.`definition_id` <=> @example_definition_1);
SET @example_definition_content_1 = (SELECT existing.id FROM rule_definition_content existing WHERE existing.`definition_id` <=> @example_definition_1);

-- rule_list_library: black_list
INSERT INTO rule_list_library (`project_id`,`scope`,`list_code`,`list_name`,`list_type`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','black_list','风险黑名单','BLACK','',1,'2026-07-04 03:10:23','2026-07-04 03:10:23'
WHERE NOT EXISTS (SELECT 1 FROM rule_list_library existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`list_code` <=> 'black_list');
SET @example_list_library_1 = (SELECT existing.id FROM rule_list_library existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`list_code` <=> 'black_list');

-- rule_variable: mobile_no
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','mobile_no','手机号','mobile_no','STRING','INPUT',NULL,'','','14519144474','示例值随机生成',0,1,'2026-07-03 22:59:40','2026-07-03 22:59:40'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'mobile_no');
SET @example_variable_7 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'mobile_no');

-- rule_variable: black_hit
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','black_hit','是否命中黑名单','black_hit','NUMBER','LIST',JSON_SET('{"listIds":[1],"queryOperands":[{"kind":"REFERENCE","refType":"VARIABLE","refId":7,"code":"mobile_no","label":"手机号","valueType":"STRING"}],"combinationMode":"ANY_FIELD_ANY_LIST","matchMode":"IN_LIST","itemTypes":["MOBILE","ID_CARD","ADDRESS","IP"],"returnMode":"NUMBER"}', '$."listIds"[0]', CAST(@example_list_library_1 AS UNSIGNED), '$."queryOperands"[0]."refId"', CAST(@example_variable_7 AS UNSIGNED)),'-1','','','',0,1,'2026-07-04 03:35:32','2026-07-04 03:35:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'black_hit');
SET @example_variable_194 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'black_hit');

-- rule_definition_content: 2
INSERT INTO rule_definition_content (`definition_id`,`model_json`,`compiled_script`,`compiled_type`,`compile_status`,`compile_message`,`compile_time`,`script_mode`,`update_time`)
SELECT @example_definition_2,JSON_SET('{"executionMode":"SERIAL","rules":[{"ruleCode":"R0001","ruleName":"规则1","priority":1,"enabled":true,"conditionRoot":{"type":"group","op":"AND","children":[{"type":"leaf","varCode":"black_hit","varLabel":"是否命中黑名单 black_hit","varType":"NUMBER","enumOptions":"","operator":"==","valueKind":"CONST","value":"1","_varId":194,"_refType":"VARIABLE"}]},"actionData":[{"type":"assign","target":"result","value":"1","_targetVarId":141,"_targetRefType":"VARIABLE","_varId":141,"_refType":"VARIABLE"}],"status":1}]}', '$."rules"[0]."conditionRoot"."children"[0]."_varId"', CAST(@example_variable_194 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_targetVarId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_varId"', CAST(@example_variable_141 AS UNSIGNED)),'_ruleSetHits = [];
_ruleSetHitCount = 0;
_ruleSetMatched = false;
if (!_ruleSetMatched && ((black_hit == 1))) {
    result = 1
    _ruleSetHit = {"ruleCode": "R0001", "ruleName": "规则1", "priority": 1, "order": 1};
    if (_ruleSetHitCount == 0) {
        _ruleSetHits = [_ruleSetHit];
    }
    _ruleSetHitCount = _ruleSetHitCount + 1;
    _ruleSetMatched = true;
}
_ruleSetResult = _ruleSetHits;
_ruleSetResult
','QLEXPRESS',1,NULL,'2026-07-04 03:37:58','visual','2026-07-04 03:33:34'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_content existing WHERE existing.`definition_id` <=> @example_definition_2);
SET @example_definition_content_2 = (SELECT existing.id FROM rule_definition_content existing WHERE existing.`definition_id` <=> @example_definition_2);

-- rule_external_datasource: tianshu_rule_engine
INSERT INTO rule_external_datasource (`project_id`,`scope`,`datasource_code`,`datasource_name`,`provider_name`,`protocol`,`base_url`,`auth_type`,`auth_config`,`token_cache_seconds`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','tianshu_rule_engine','天枢决策引擎','衡枢真信','RULE_ENGINE','rule-engine://local','NONE',NULL,0,'',1,'2026-07-04 03:40:56','2026-07-04 03:40:56'
WHERE NOT EXISTS (SELECT 1 FROM rule_external_datasource existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`datasource_code` <=> 'tianshu_rule_engine');
SET @example_external_datasource_1 = (SELECT existing.id FROM rule_external_datasource existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`datasource_code` <=> 'tianshu_rule_engine');

-- rule_external_api_config: BDZR
INSERT INTO rule_external_api_config (`datasource_id`,`api_code`,`api_name`,`request_method`,`endpoint_url`,`content_type`,`request_mode`,`execution_config`,`auth_mode`,`timeout_ms`,`exception_strategy`,`status`,`create_time`,`update_time`)
SELECT @example_external_datasource_1,'BDZR','BNLP黑名单准入','POST','/BDZR','application/json','SYNC',JSON_SET('{"version":2,"requestFields":[{"id":"mobile","location":"JSON","path":"params.mobile_no","value":{"kind":"REFERENCE","refType":"VARIABLE","refId":7,"code":"mobile_no"}}]}', '$."requestFields"[0]."value"."refId"', CAST(@example_variable_7 AS UNSIGNED)),'INHERIT',3000,'FAIL_FAST',1,'2026-07-04 03:42:53','2026-07-04 03:42:53'
WHERE NOT EXISTS (SELECT 1 FROM rule_external_api_config existing WHERE existing.`datasource_id` <=> @example_external_datasource_1 AND existing.`api_code` <=> 'BDZR');
SET @example_external_api_config_1 = (SELECT existing.id FROM rule_external_api_config existing WHERE existing.`datasource_id` <=> @example_external_datasource_1 AND existing.`api_code` <=> 'BDZR');

-- rule_variable: engine_bdrules
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','engine_bdrules','引擎黑规则结果','engine_bdrules','LIST','API',JSON_SET('{"resultPath": "body", "apiConfigId": 1, "forceRefresh": false, "fallbackValue": null, "exceptionStrategy": "ERROR"}', '$."apiConfigId"', CAST(@example_external_api_config_1 AS UNSIGNED)),'','','','',0,1,'2026-07-04 03:51:57','2026-07-04 03:51:57'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'engine_bdrules');
SET @example_variable_195 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'engine_bdrules');

-- rule_definition_content: 3
INSERT INTO rule_definition_content (`definition_id`,`model_json`,`compiled_script`,`compiled_type`,`compile_status`,`compile_message`,`compile_time`,`script_mode`,`update_time`)
SELECT @example_definition_3,JSON_SET('{"executionMode":"SERIAL","rules":[{"ruleCode":"R0001","ruleName":"规则1","priority":1,"enabled":true,"conditionRoot":{"type":"group","op":"AND","children":[{"type":"leaf","varCode":"engine_bdrules","varLabel":"引擎黑规则结果 engine_bdrules","varType":"LIST","enumOptions":"","operator":"is_empty","valueKind":"CONST","value":"","_varId":195,"_refType":"VARIABLE","rightVarType":"","rightVarLabel":""}]},"actionData":[{"type":"assign","target":"result","value":"1","_targetVarId":141,"_targetRefType":"VARIABLE","_varId":141,"_refType":"VARIABLE"}],"status":1}]}', '$."rules"[0]."conditionRoot"."children"[0]."_varId"', CAST(@example_variable_195 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_targetVarId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_varId"', CAST(@example_variable_141 AS UNSIGNED)),'_ruleSetHits = [];
_ruleSetHitCount = 0;
_ruleSetMatched = false;
if (!_ruleSetMatched && ((isBlank(engine_bdrules)))) {
    result = 1
    _ruleSetHit = {"ruleCode": "R0001", "ruleName": "规则1", "priority": 1, "order": 1};
    if (_ruleSetHitCount == 0) {
        _ruleSetHits = [_ruleSetHit];
    }
    _ruleSetHitCount = _ruleSetHitCount + 1;
    _ruleSetMatched = true;
}
_ruleSetResult = _ruleSetHits;
_ruleSetResult
','QLEXPRESS',1,'规则集模型缺少必要字段: rules','2026-07-04 03:56:57','visual','2026-07-04 03:53:19'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_content existing WHERE existing.`definition_id` <=> @example_definition_3);
SET @example_definition_content_3 = (SELECT existing.id FROM rule_definition_content existing WHERE existing.`definition_id` <=> @example_definition_3);

-- rule_variable: black_user_count
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','black_user_count','黑名单人数','black_user_count','NUMBER','DB',JSON_SET('{"sql": "SELECT count(1) user_count\\r\\nFROM rule_list_record AS rlr\\r\\nWHERE list_id = 1 and item_content = ?", "params": [{"kind":"REFERENCE","refType":"VARIABLE","refId":7,"code":"mobile_no"}], "maxRows": 1, "resultPath": "0.user_count", "datasourceId": 1, "forceRefresh": false, "fallbackValue": null, "exceptionStrategy": "ERROR"}', '$."params"[0]."refId"', CAST(@example_variable_7 AS UNSIGNED), '$."datasourceId"', CAST(@example_db_datasource_1 AS UNSIGNED), '$.sql', CONCAT('SELECT count(1) user_count\r\nFROM rule_list_record AS rlr\r\nWHERE list_id = ', CAST(@example_list_library_1 AS CHAR), ' and item_content = ?')),'','','','',0,1,'2026-07-04 04:05:12','2026-07-07 02:42:18'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'black_user_count');
SET @example_variable_196 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'black_user_count');

-- rule_definition_content: 4
INSERT INTO rule_definition_content (`definition_id`,`model_json`,`compiled_script`,`compiled_type`,`compile_status`,`compile_message`,`compile_time`,`script_mode`,`update_time`)
SELECT @example_definition_4,JSON_SET('{"executionMode":"SERIAL","rules":[{"ruleCode":"R0001","ruleName":"规则1","priority":1,"enabled":true,"conditionRoot":{"type":"group","op":"AND","children":[{"type":"leaf","varCode":"black_user_count","varLabel":"黑名单人数 black_user_count","varType":"NUMBER","enumOptions":"","operator":">","valueKind":"CONST","value":"0","_varId":196,"_refType":"VARIABLE"}]},"actionData":[{"type":"assign","target":"result","value":"1","_targetVarId":141,"_targetRefType":"VARIABLE","_varId":141,"_refType":"VARIABLE"}],"status":1},{"ruleCode":"R0002","ruleName":"规则2","priority":1,"enabled":true,"conditionRoot":{"type":"group","op":"AND","children":[{"type":"leaf","varCode":"score_f1.score","varLabel":"欺诈分F1/评分 score","varType":"DOUBLE","enumOptions":"","operator":"<","valueKind":"CONST","value":"250","_varId":1,"_refType":"MODEL_OUTPUT"}]},"actionData":[{"type":"assign","target":"result","value":"1","_targetVarId":141,"_targetRefType":"VARIABLE","_varId":141,"_refType":"VARIABLE"}],"status":1}]}', '$."rules"[0]."conditionRoot"."children"[0]."_varId"', CAST(@example_variable_196 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_targetVarId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_varId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[1]."conditionRoot"."children"[0]."_varId"', CAST(@example_model_output_field_1 AS UNSIGNED), '$."rules"[1]."actionData"[0]."_targetVarId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[1]."actionData"[0]."_varId"', CAST(@example_variable_141 AS UNSIGNED)),'_ruleSetHits = [];
_ruleSetHitCount = 0;
_ruleSetMatched = false;
if (!_ruleSetMatched && ((black_user_count > 0))) {
    result = 1
    _ruleSetHit = {"ruleCode": "R0001", "ruleName": "规则1", "priority": 1, "order": 1};
    if (_ruleSetHitCount == 0) {
        _ruleSetHits = [_ruleSetHit];
    }
    _ruleSetHitCount = _ruleSetHitCount + 1;
    _ruleSetMatched = true;
}
if (!_ruleSetMatched && ((score_f1.score < 250))) {
    result = 1
    _ruleSetHit = {"ruleCode": "R0002", "ruleName": "规则2", "priority": 1, "order": 2};
    if (_ruleSetHitCount == 0) {
        _ruleSetHits = [_ruleSetHit];
    } else if (_ruleSetHitCount == 1) {
        _ruleSetHits = [_ruleSetHits[0], _ruleSetHit];
    }
    _ruleSetHitCount = _ruleSetHitCount + 1;
    _ruleSetMatched = true;
}
_ruleSetResult = _ruleSetHits;
_ruleSetResult
','QLEXPRESS',1,NULL,'2026-07-04 04:09:31','visual','2026-07-04 04:05:59'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_content existing WHERE existing.`definition_id` <=> @example_definition_4);
SET @example_definition_content_4 = (SELECT existing.id FROM rule_definition_content existing WHERE existing.`definition_id` <=> @example_definition_4);

-- rule_variable: idcard_birthday
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','idcard_birthday','身份证生日','idcard_birthday','DATE','COMPUTED',NULL,'','','','',0,1,'2026-07-07 00:36:27','2026-07-07 00:36:27'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'idcard_birthday');
SET @example_variable_197 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'idcard_birthday');

-- rule_variable: idcard_no
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','idcard_no','身份证号','idcard_no','STRING','INPUT',NULL,'','','500107200503133193','示例值为随机生成',0,1,'2026-07-03 22:58:43','2026-07-03 22:58:43'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'idcard_no');
SET @example_variable_6 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'idcard_no');

-- rule_definition_content: 5
INSERT INTO rule_definition_content (`definition_id`,`model_json`,`compiled_script`,`compiled_type`,`compile_status`,`compile_message`,`compile_time`,`script_mode`,`update_time`)
SELECT @example_definition_5,JSON_SET('{"nodes":[{"id":"node_1783355879477_shvtszko2","type":"start","name":"开始","x":160,"y":300,"actionData":[],"gatewayDirection":"","leftVarId":null,"leftRefType":null,"rightVarId":null,"rightRefType":null},{"id":"node_1783355879477_p8gnnm1nl","type":"end","name":"结束","x":800,"y":300,"actionData":[],"gatewayDirection":"","leftVarId":null,"leftRefType":null,"rightVarId":null,"rightRefType":null},{"id":"c58709d3-7329-452f-8d45-68af6f4aec1d","type":"task","name":"执行动作","x":460,"y":300,"actionData":[{"type":"func-call","target":"idcard_birthday","funcName":"idCardBirthDate","args":["idcard_no"],"_targetVarId":197,"_targetRefType":"VARIABLE","_varId":197,"_refType":"VARIABLE","_argRefs":[{"_varId":6,"_refType":"VARIABLE"}]}],"gatewayDirection":"Diverging","leftVarId":null,"leftRefType":null,"rightVarId":null,"rightRefType":null}],"edges":[{"id":"8f8d0ba0-8cde-4500-b172-d6606c506a3b","source":"node_1783355879477_shvtszko2","target":"c58709d3-7329-452f-8d45-68af6f4aec1d","conditionExpression":"","conditionConfig":null,"name":"","leftVarId":null,"leftRefType":null,"rightVarId":null,"rightRefType":null},{"id":"f8679ca7-56a2-441b-a24b-bc1ff31f24ca","source":"c58709d3-7329-452f-8d45-68af6f4aec1d","target":"node_1783355879477_p8gnnm1nl","conditionExpression":"","conditionConfig":null,"name":"","leftVarId":null,"leftRefType":null,"rightVarId":null,"rightRefType":null}],"defaultEdgeLineType":"polyline","logicflow":{"nodes":[{"id":"node_1783355879477_shvtszko2","type":"start-event","x":160,"y":300,"properties":{"nodeName":"开始","nodeCode":"START_1783355879477","nodeDesc":"流程开始节点","actionData":[]}},{"id":"node_1783355879477_p8gnnm1nl","type":"end-event","x":800,"y":300,"properties":{"nodeName":"结束","nodeCode":"END_1783355879477","nodeDesc":"流程结束节点","actionData":[]}},{"id":"c58709d3-7329-452f-8d45-68af6f4aec1d","type":"script-task","x":460,"y":300,"properties":{"nodeName":"执行动作","nodeCode":"SCRIPT_TASK_1783355882855_TL9Y","nodeDesc":"","actionData":[{"type":"func-call","target":"idcard_birthday","funcName":"idCardBirthDate","args":["idcard_no"],"_targetVarId":197,"_targetRefType":"VARIABLE","_varId":197,"_refType":"VARIABLE","_argRefs":[{"_varId":6,"_refType":"VARIABLE"}]}],"gatewayDirection":"Diverging"}}],"edges":[{"id":"8f8d0ba0-8cde-4500-b172-d6606c506a3b","type":"polyline","sourceNodeId":"node_1783355879477_shvtszko2","targetNodeId":"c58709d3-7329-452f-8d45-68af6f4aec1d","startPoint":{"x":185,"y":300},"endPoint":{"x":380,"y":300},"properties":{},"pointsList":[{"x":185,"y":300},{"x":380,"y":300}]},{"id":"f8679ca7-56a2-441b-a24b-bc1ff31f24ca","type":"polyline","sourceNodeId":"c58709d3-7329-452f-8d45-68af6f4aec1d","targetNodeId":"node_1783355879477_p8gnnm1nl","startPoint":{"x":540,"y":300},"endPoint":{"x":775,"y":300},"properties":{},"pointsList":[{"x":540,"y":300},{"x":775,"y":300}]}]}}', '$."nodes"[2]."actionData"[0]."_targetVarId"', CAST(@example_variable_197 AS UNSIGNED), '$."nodes"[2]."actionData"[0]."_varId"', CAST(@example_variable_197 AS UNSIGNED), '$."nodes"[2]."actionData"[0]."_argRefs"[0]."_varId"', CAST(@example_variable_6 AS UNSIGNED), '$."logicflow"."nodes"[2]."properties"."actionData"[0]."_targetVarId"', CAST(@example_variable_197 AS UNSIGNED), '$."logicflow"."nodes"[2]."properties"."actionData"[0]."_varId"', CAST(@example_variable_197 AS UNSIGNED), '$."logicflow"."nodes"[2]."properties"."actionData"[0]."_argRefs"[0]."_varId"', CAST(@example_variable_6 AS UNSIGNED)),'idcard_birthday = null
// 执行动作
idcard_birthday = idCardBirthDate(idcard_no)
_result = {"idcard_birthday": idcard_birthday}
_result
','QLEXPRESS',1,NULL,'2026-07-07 00:39:05','visual','2026-07-07 00:37:55'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_content existing WHERE existing.`definition_id` <=> @example_definition_5);
SET @example_definition_content_5 = (SELECT existing.id FROM rule_definition_content existing WHERE existing.`definition_id` <=> @example_definition_5);

-- rule_definition_content: 6
INSERT INTO rule_definition_content (`definition_id`,`model_json`,`compiled_script`,`compiled_type`,`compile_status`,`compile_message`,`compile_time`,`script_mode`,`update_time`)
SELECT @example_definition_6,'{}',NULL,NULL,0,NULL,NULL,'visual','2026-07-07 02:33:42'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_content existing WHERE existing.`definition_id` <=> @example_definition_6);
SET @example_definition_content_6 = (SELECT existing.id FROM rule_definition_content existing WHERE existing.`definition_id` <=> @example_definition_6);

-- rule_definition_input_field: age
INSERT INTO rule_definition_input_field (`definition_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`missing_value`,`default_value`,`valid_values`,`transform_type`,`transform_params`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT @example_definition_1,@example_variable_37,'VARIABLE','age','年龄','age','NUMBER',NULL,NULL,NULL,NULL,NULL,0,1,'2026-07-04 03:33:35','2026-07-04 03:33:35'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_input_field existing WHERE existing.`definition_id` <=> @example_definition_1 AND existing.`field_name` <=> 'age');
SET @example_definition_input_field_1 = (SELECT existing.id FROM rule_definition_input_field existing WHERE existing.`definition_id` <=> @example_definition_1 AND existing.`field_name` <=> 'age');

-- rule_definition_input_field: score_f1.score
INSERT INTO rule_definition_input_field (`definition_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`missing_value`,`default_value`,`valid_values`,`transform_type`,`transform_params`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT @example_definition_1,@example_model_output_field_1,'MODEL_OUTPUT','score_f1.score','欺诈分F1/评分','score_f1.score','DOUBLE',NULL,NULL,NULL,NULL,NULL,1,1,'2026-07-04 03:33:35','2026-07-04 03:33:35'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_input_field existing WHERE existing.`definition_id` <=> @example_definition_1 AND existing.`field_name` <=> 'score_f1.score');
SET @example_definition_input_field_2 = (SELECT existing.id FROM rule_definition_input_field existing WHERE existing.`definition_id` <=> @example_definition_1 AND existing.`field_name` <=> 'score_f1.score');

-- rule_definition_input_field: black_hit
INSERT INTO rule_definition_input_field (`definition_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`missing_value`,`default_value`,`valid_values`,`transform_type`,`transform_params`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT @example_definition_2,@example_variable_194,'VARIABLE','black_hit','是否命中黑名单','black_hit','NUMBER',NULL,NULL,NULL,NULL,NULL,0,1,'2026-07-04 03:37:58','2026-07-04 03:37:58'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_input_field existing WHERE existing.`definition_id` <=> @example_definition_2 AND existing.`field_name` <=> 'black_hit');
SET @example_definition_input_field_3 = (SELECT existing.id FROM rule_definition_input_field existing WHERE existing.`definition_id` <=> @example_definition_2 AND existing.`field_name` <=> 'black_hit');

-- rule_definition_input_field: mobile_no
INSERT INTO rule_definition_input_field (`definition_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`missing_value`,`default_value`,`valid_values`,`transform_type`,`transform_params`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT @example_definition_2,@example_variable_7,'VARIABLE','mobile_no','手机号','mobile_no','STRING',NULL,NULL,NULL,NULL,NULL,1,1,'2026-07-04 03:37:58','2026-07-04 03:37:58'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_input_field existing WHERE existing.`definition_id` <=> @example_definition_2 AND existing.`field_name` <=> 'mobile_no');
SET @example_definition_input_field_4 = (SELECT existing.id FROM rule_definition_input_field existing WHERE existing.`definition_id` <=> @example_definition_2 AND existing.`field_name` <=> 'mobile_no');

-- rule_definition_input_field: engine_bdrules
INSERT INTO rule_definition_input_field (`definition_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`missing_value`,`default_value`,`valid_values`,`transform_type`,`transform_params`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT @example_definition_3,@example_variable_195,'VARIABLE','engine_bdrules','引擎黑规则结果','engine_bdrules','LIST',NULL,NULL,NULL,NULL,NULL,0,1,'2026-07-04 04:02:03','2026-07-04 04:02:03'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_input_field existing WHERE existing.`definition_id` <=> @example_definition_3 AND existing.`field_name` <=> 'engine_bdrules');
SET @example_definition_input_field_5 = (SELECT existing.id FROM rule_definition_input_field existing WHERE existing.`definition_id` <=> @example_definition_3 AND existing.`field_name` <=> 'engine_bdrules');

-- rule_definition_input_field: idcard_no
INSERT INTO rule_definition_input_field (`definition_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`missing_value`,`default_value`,`valid_values`,`transform_type`,`transform_params`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT @example_definition_5,@example_variable_6,'VARIABLE','idcard_no','身份证号','idcard_no','STRING',NULL,NULL,NULL,NULL,NULL,0,1,'2026-07-07 00:39:05','2026-07-07 00:39:04'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_input_field existing WHERE existing.`definition_id` <=> @example_definition_5 AND existing.`field_name` <=> 'idcard_no');
SET @example_definition_input_field_6 = (SELECT existing.id FROM rule_definition_input_field existing WHERE existing.`definition_id` <=> @example_definition_5 AND existing.`field_name` <=> 'idcard_no');

-- rule_definition_input_field: black_user_count
INSERT INTO rule_definition_input_field (`definition_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`missing_value`,`default_value`,`valid_values`,`transform_type`,`transform_params`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT @example_definition_4,@example_variable_196,'VARIABLE','black_user_count','黑名单人数','black_user_count','INTEGER',NULL,NULL,NULL,NULL,NULL,0,1,'2026-07-07 01:51:31','2026-07-07 01:51:31'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_input_field existing WHERE existing.`definition_id` <=> @example_definition_4 AND existing.`field_name` <=> 'black_user_count');
SET @example_definition_input_field_7 = (SELECT existing.id FROM rule_definition_input_field existing WHERE existing.`definition_id` <=> @example_definition_4 AND existing.`field_name` <=> 'black_user_count');

-- rule_definition_input_field: score_f1.score
INSERT INTO rule_definition_input_field (`definition_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`missing_value`,`default_value`,`valid_values`,`transform_type`,`transform_params`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT @example_definition_4,@example_model_output_field_1,'MODEL_OUTPUT','score_f1.score','欺诈分F1/评分','score_f1.score','DOUBLE',NULL,NULL,NULL,NULL,NULL,1,1,'2026-07-07 01:51:31','2026-07-07 01:51:31'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_input_field existing WHERE existing.`definition_id` <=> @example_definition_4 AND existing.`field_name` <=> 'score_f1.score');
SET @example_definition_input_field_8 = (SELECT existing.id FROM rule_definition_input_field existing WHERE existing.`definition_id` <=> @example_definition_4 AND existing.`field_name` <=> 'score_f1.score');

-- rule_definition_output_field: result
INSERT INTO rule_definition_output_field (`definition_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`transform_type`,`transform_params`,`valid_values`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT @example_definition_1,@example_variable_141,'VARIABLE','result','决策结果','result','ENUM',NULL,NULL,NULL,0,1,'2026-07-04 03:33:35','2026-07-04 03:33:35'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_output_field existing WHERE existing.`definition_id` <=> @example_definition_1 AND existing.`field_name` <=> 'result');
SET @example_definition_output_field_1 = (SELECT existing.id FROM rule_definition_output_field existing WHERE existing.`definition_id` <=> @example_definition_1 AND existing.`field_name` <=> 'result');

-- rule_definition_output_field: amount
INSERT INTO rule_definition_output_field (`definition_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`transform_type`,`transform_params`,`valid_values`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT @example_definition_1,@example_variable_77,'VARIABLE','amount','金额','amount','DOUBLE',NULL,NULL,NULL,1,1,'2026-07-04 03:33:35','2026-07-04 03:33:35'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_output_field existing WHERE existing.`definition_id` <=> @example_definition_1 AND existing.`field_name` <=> 'amount');
SET @example_definition_output_field_2 = (SELECT existing.id FROM rule_definition_output_field existing WHERE existing.`definition_id` <=> @example_definition_1 AND existing.`field_name` <=> 'amount');

-- rule_definition_output_field: period
INSERT INTO rule_definition_output_field (`definition_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`transform_type`,`transform_params`,`valid_values`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT @example_definition_1,@example_variable_76,'VARIABLE','period','期数','period','NUMBER',NULL,NULL,NULL,2,1,'2026-07-04 03:33:35','2026-07-04 03:33:35'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_output_field existing WHERE existing.`definition_id` <=> @example_definition_1 AND existing.`field_name` <=> 'period');
SET @example_definition_output_field_3 = (SELECT existing.id FROM rule_definition_output_field existing WHERE existing.`definition_id` <=> @example_definition_1 AND existing.`field_name` <=> 'period');

-- rule_definition_output_field: result
INSERT INTO rule_definition_output_field (`definition_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`transform_type`,`transform_params`,`valid_values`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT @example_definition_2,@example_variable_141,'VARIABLE','result','决策结果','result','ENUM',NULL,NULL,NULL,0,1,'2026-07-04 03:37:58','2026-07-04 03:37:58'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_output_field existing WHERE existing.`definition_id` <=> @example_definition_2 AND existing.`field_name` <=> 'result');
SET @example_definition_output_field_4 = (SELECT existing.id FROM rule_definition_output_field existing WHERE existing.`definition_id` <=> @example_definition_2 AND existing.`field_name` <=> 'result');

-- rule_definition_output_field: result
INSERT INTO rule_definition_output_field (`definition_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`transform_type`,`transform_params`,`valid_values`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT @example_definition_3,@example_variable_141,'VARIABLE','result','决策结果','result','ENUM',NULL,NULL,NULL,0,1,'2026-07-04 04:02:03','2026-07-04 04:02:03'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_output_field existing WHERE existing.`definition_id` <=> @example_definition_3 AND existing.`field_name` <=> 'result');
SET @example_definition_output_field_5 = (SELECT existing.id FROM rule_definition_output_field existing WHERE existing.`definition_id` <=> @example_definition_3 AND existing.`field_name` <=> 'result');

-- rule_definition_output_field: idcard_birthday
INSERT INTO rule_definition_output_field (`definition_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`transform_type`,`transform_params`,`valid_values`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT @example_definition_5,@example_variable_197,'VARIABLE','idcard_birthday','身份证生日','idcard_birthday','DATE',NULL,NULL,NULL,0,1,'2026-07-07 00:39:05','2026-07-07 00:39:04'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_output_field existing WHERE existing.`definition_id` <=> @example_definition_5 AND existing.`field_name` <=> 'idcard_birthday');
SET @example_definition_output_field_6 = (SELECT existing.id FROM rule_definition_output_field existing WHERE existing.`definition_id` <=> @example_definition_5 AND existing.`field_name` <=> 'idcard_birthday');

-- rule_definition_output_field: result
INSERT INTO rule_definition_output_field (`definition_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`transform_type`,`transform_params`,`valid_values`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT @example_definition_4,@example_variable_141,'VARIABLE','result','决策结果','result','ENUM',NULL,NULL,NULL,0,1,'2026-07-07 01:51:31','2026-07-07 01:51:31'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_output_field existing WHERE existing.`definition_id` <=> @example_definition_4 AND existing.`field_name` <=> 'result');
SET @example_definition_output_field_7 = (SELECT existing.id FROM rule_definition_output_field existing WHERE existing.`definition_id` <=> @example_definition_4 AND existing.`field_name` <=> 'result');

-- rule_definition_ref: 1
INSERT INTO rule_definition_ref (`definition_id`,`project_id`,`create_time`)
SELECT @example_definition_1,@example_project_1,'2026-07-04 03:30:18'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_ref existing WHERE existing.`definition_id` <=> @example_definition_1 AND existing.`project_id` <=> @example_project_1);
SET @example_definition_ref_1 = (SELECT existing.id FROM rule_definition_ref existing WHERE existing.`definition_id` <=> @example_definition_1 AND existing.`project_id` <=> @example_project_1);

-- rule_definition_ref: 2
INSERT INTO rule_definition_ref (`definition_id`,`project_id`,`create_time`)
SELECT @example_definition_3,@example_project_1,'2026-07-04 03:57:36'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_ref existing WHERE existing.`definition_id` <=> @example_definition_3 AND existing.`project_id` <=> @example_project_1);
SET @example_definition_ref_2 = (SELECT existing.id FROM rule_definition_ref existing WHERE existing.`definition_id` <=> @example_definition_3 AND existing.`project_id` <=> @example_project_1);

-- rule_definition_version: 1
INSERT INTO rule_definition_version (`definition_id`,`version`,`model_json`,`compiled_script`,`compiled_type`,`change_log`,`publish_by`,`publish_time`)
SELECT @example_definition_2,1,JSON_SET('{"executionMode":"SERIAL","rules":[{"ruleCode":"R0001","ruleName":"规则1","priority":1,"enabled":true,"conditionRoot":{"type":"group","op":"AND","children":[{"type":"leaf","varCode":"black_hit","varLabel":"是否命中黑名单 black_hit","varType":"NUMBER","enumOptions":"","operator":"==","valueKind":"CONST","value":"1","_varId":194,"_refType":"VARIABLE"}]},"actionData":[{"type":"assign","target":"result","value":"1","_targetVarId":141,"_targetRefType":"VARIABLE","_varId":141,"_refType":"VARIABLE"}],"status":1}]}', '$."rules"[0]."conditionRoot"."children"[0]."_varId"', CAST(@example_variable_194 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_targetVarId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_varId"', CAST(@example_variable_141 AS UNSIGNED)),'_ruleSetHits = [];
_ruleSetHitCount = 0;
_ruleSetMatched = false;
if (!_ruleSetMatched && ((black_hit == 1))) {
    result = 1
    _ruleSetHit = {"ruleCode": "R0001", "ruleName": "规则1", "priority": 1, "order": 1};
    if (_ruleSetHitCount == 0) {
        _ruleSetHits = [_ruleSetHit];
    }
    _ruleSetHitCount = _ruleSetHitCount + 1;
    _ruleSetMatched = true;
}
_ruleSetResult = _ruleSetHits;
_ruleSetResult
','QLEXPRESS',NULL,NULL,'2026-07-04 03:39:04'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_version existing WHERE existing.`definition_id` <=> @example_definition_2 AND existing.`version` <=> 1);
SET @example_definition_version_1 = (SELECT existing.id FROM rule_definition_version existing WHERE existing.`definition_id` <=> @example_definition_2 AND existing.`version` <=> 1);

-- rule_definition_version: 2
INSERT INTO rule_definition_version (`definition_id`,`version`,`model_json`,`compiled_script`,`compiled_type`,`change_log`,`publish_by`,`publish_time`)
SELECT @example_definition_4,1,JSON_SET('{"executionMode":"SERIAL","rules":[{"ruleCode":"R0001","ruleName":"规则1","priority":1,"enabled":true,"conditionRoot":{"type":"group","op":"AND","children":[{"type":"leaf","varCode":"black_user_count","varLabel":"黑名单人数 black_user_count","varType":"NUMBER","enumOptions":"","operator":">","valueKind":"CONST","value":"0","_varId":196,"_refType":"VARIABLE"}]},"actionData":[{"type":"assign","target":"result","value":"1","_targetVarId":141,"_targetRefType":"VARIABLE","_varId":141,"_refType":"VARIABLE"}],"status":1}]}', '$."rules"[0]."conditionRoot"."children"[0]."_varId"', CAST(@example_variable_196 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_targetVarId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_varId"', CAST(@example_variable_141 AS UNSIGNED)),'_ruleSetHits = [];
_ruleSetHitCount = 0;
_ruleSetMatched = false;
if (!_ruleSetMatched && ((black_user_count > 0))) {
    result = 1
    _ruleSetHit = {"ruleCode": "R0001", "ruleName": "规则1", "priority": 1, "order": 1};
    if (_ruleSetHitCount == 0) {
        _ruleSetHits = [_ruleSetHit];
    }
    _ruleSetHitCount = _ruleSetHitCount + 1;
    _ruleSetMatched = true;
}
_ruleSetResult = _ruleSetHits;
_ruleSetResult
','QLEXPRESS',NULL,NULL,'2026-07-04 04:07:41'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_version existing WHERE existing.`definition_id` <=> @example_definition_4 AND existing.`version` <=> 1);
SET @example_definition_version_2 = (SELECT existing.id FROM rule_definition_version existing WHERE existing.`definition_id` <=> @example_definition_4 AND existing.`version` <=> 1);

-- rule_definition_version: 3
INSERT INTO rule_definition_version (`definition_id`,`version`,`model_json`,`compiled_script`,`compiled_type`,`change_log`,`publish_by`,`publish_time`)
SELECT @example_definition_4,2,JSON_SET('{"executionMode":"SERIAL","rules":[{"ruleCode":"R0001","ruleName":"规则1","priority":1,"enabled":true,"conditionRoot":{"type":"group","op":"AND","children":[{"type":"leaf","varCode":"black_user_count","varLabel":"黑名单人数 black_user_count","varType":"NUMBER","enumOptions":"","operator":">","valueKind":"CONST","value":"0","_varId":196,"_refType":"VARIABLE"}]},"actionData":[{"type":"assign","target":"result","value":"1","_targetVarId":141,"_targetRefType":"VARIABLE","_varId":141,"_refType":"VARIABLE"}],"status":1},{"ruleCode":"R0002","ruleName":"规则2","priority":1,"enabled":true,"conditionRoot":{"type":"group","op":"AND","children":[{"type":"leaf","varCode":"score_f1.score","varLabel":"欺诈分F1/评分 score","varType":"DOUBLE","enumOptions":"","operator":"<","valueKind":"CONST","value":"250","_varId":1,"_refType":"MODEL_OUTPUT"}]},"actionData":[{"type":"assign","target":"result","value":"1","_targetVarId":141,"_targetRefType":"VARIABLE","_varId":141,"_refType":"VARIABLE"}],"status":1}]}', '$."rules"[0]."conditionRoot"."children"[0]."_varId"', CAST(@example_variable_196 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_targetVarId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_varId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[1]."conditionRoot"."children"[0]."_varId"', CAST(@example_model_output_field_1 AS UNSIGNED), '$."rules"[1]."actionData"[0]."_targetVarId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[1]."actionData"[0]."_varId"', CAST(@example_variable_141 AS UNSIGNED)),'_ruleSetHits = [];
_ruleSetHitCount = 0;
_ruleSetMatched = false;
if (!_ruleSetMatched && ((black_user_count > 0))) {
    result = 1
    _ruleSetHit = {"ruleCode": "R0001", "ruleName": "规则1", "priority": 1, "order": 1};
    if (_ruleSetHitCount == 0) {
        _ruleSetHits = [_ruleSetHit];
    }
    _ruleSetHitCount = _ruleSetHitCount + 1;
    _ruleSetMatched = true;
}
if (!_ruleSetMatched && ((score_f1.score < 250))) {
    result = 1
    _ruleSetHit = {"ruleCode": "R0002", "ruleName": "规则2", "priority": 1, "order": 2};
    if (_ruleSetHitCount == 0) {
        _ruleSetHits = [_ruleSetHit];
    } else if (_ruleSetHitCount == 1) {
        _ruleSetHits = [_ruleSetHits[0], _ruleSetHit];
    }
    _ruleSetHitCount = _ruleSetHitCount + 1;
    _ruleSetMatched = true;
}
_ruleSetResult = _ruleSetHits;
_ruleSetResult
','QLEXPRESS',NULL,NULL,'2026-07-04 04:09:35'
WHERE NOT EXISTS (SELECT 1 FROM rule_definition_version existing WHERE existing.`definition_id` <=> @example_definition_4 AND existing.`version` <=> 2);
SET @example_definition_version_3 = (SELECT existing.id FROM rule_definition_version existing WHERE existing.`definition_id` <=> @example_definition_4 AND existing.`version` <=> 2);

-- rule_variable: request_id
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','request_id','请求编号','request_id','STRING','INPUT',NULL,'','','R0000000001','',0,1,'2026-07-03 22:55:59','2026-07-03 22:55:59'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'request_id');
SET @example_variable_3 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'request_id');

-- rule_external_api_config: DBQUERY
INSERT INTO rule_external_api_config (`datasource_id`,`api_code`,`api_name`,`request_method`,`endpoint_url`,`content_type`,`request_mode`,`execution_config`,`auth_mode`,`timeout_ms`,`exception_strategy`,`status`,`create_time`,`update_time`)
SELECT @example_external_datasource_1,'DBQUERY','评分规则准入结果','POST','/DBQUERY','application/json','SYNC',JSON_SET('{"version":2,"requestFields":[{"id":"request-header","location":"HEADER","path":"X-Request-Id","value":{"kind":"REFERENCE","refType":"VARIABLE","refId":3,"code":"request_id"}},{"id":"request-id","location":"JSON","path":"params.request_id","value":{"kind":"REFERENCE","refType":"VARIABLE","refId":3,"code":"request_id"}},{"id":"idcard","location":"JSON","path":"params.idcard_no","value":{"kind":"REFERENCE","refType":"VARIABLE","refId":6,"code":"idcard_no"}},{"id":"mobile","location":"JSON","path":"params.mobile_no","value":{"kind":"REFERENCE","refType":"VARIABLE","refId":7,"code":"mobile_no"}},{"id":"black-count","location":"JSON","path":"params.black_user_count","value":{"kind":"REFERENCE","refType":"VARIABLE","refId":196,"code":"black_user_count"}},{"id":"model-score","location":"JSON","path":"params.score_f1.score","value":{"kind":"REFERENCE","refType":"MODEL_OUTPUT","refId":1,"code":"score_f1.score"}}]}', '$."requestFields"[0]."value"."refId"', CAST(@example_variable_3 AS UNSIGNED), '$."requestFields"[1]."value"."refId"', CAST(@example_variable_3 AS UNSIGNED), '$."requestFields"[2]."value"."refId"', CAST(@example_variable_6 AS UNSIGNED), '$."requestFields"[3]."value"."refId"', CAST(@example_variable_7 AS UNSIGNED), '$."requestFields"[4]."value"."refId"', CAST(@example_variable_196 AS UNSIGNED), '$."requestFields"[5]."value"."refId"', CAST(@example_model_output_field_1 AS UNSIGNED)),'INHERIT',3000,'FAIL_FAST',1,'2026-07-04 04:00:41','2026-07-04 04:00:41'
WHERE NOT EXISTS (SELECT 1 FROM rule_external_api_config existing WHERE existing.`datasource_id` <=> @example_external_datasource_1 AND existing.`api_code` <=> 'DBQUERY');
SET @example_external_api_config_2 = (SELECT existing.id FROM rule_external_api_config existing WHERE existing.`datasource_id` <=> @example_external_datasource_1 AND existing.`api_code` <=> 'DBQUERY');

-- rule_list_library: grey_list
INSERT INTO rule_list_library (`project_id`,`scope`,`list_code`,`list_name`,`list_type`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','grey_list','风险灰名单','GREY','',1,'2026-07-04 03:10:47','2026-07-04 03:10:47'
WHERE NOT EXISTS (SELECT 1 FROM rule_list_library existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`list_code` <=> 'grey_list');
SET @example_list_library_2 = (SELECT existing.id FROM rule_list_library existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`list_code` <=> 'grey_list');

-- rule_list_library: white_list
INSERT INTO rule_list_library (`project_id`,`scope`,`list_code`,`list_name`,`list_type`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','white_list','风险白名单','WHITE','',1,'2026-07-04 03:11:07','2026-07-04 03:11:07'
WHERE NOT EXISTS (SELECT 1 FROM rule_list_library existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`list_code` <=> 'white_list');
SET @example_list_library_3 = (SELECT existing.id FROM rule_list_library existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`list_code` <=> 'white_list');

-- rule_list_record: 1
INSERT INTO rule_list_record (`list_id`,`item_type`,`item_content`,`effective_time`,`expire_time`,`reason`,`remark`,`last_operation`,`status`,`create_time`,`update_time`)
SELECT @example_list_library_3,'MOBILE','13800138000','2026-01-01 00:00:00','2026-12-31 23:59:59','测试样例','导入时可删除样例行','ADD',1,'2026-07-04 03:11:52','2026-07-04 03:11:52'
WHERE NOT EXISTS (SELECT 1 FROM rule_list_record existing WHERE existing.`list_id` <=> @example_list_library_3 AND existing.`item_type` <=> 'MOBILE' AND existing.`item_content` <=> '13800138000');
SET @example_list_record_1 = (SELECT existing.id FROM rule_list_record existing WHERE existing.`list_id` <=> @example_list_library_3 AND existing.`item_type` <=> 'MOBILE' AND existing.`item_content` <=> '13800138000');

-- rule_list_record: 2
INSERT INTO rule_list_record (`list_id`,`item_type`,`item_content`,`effective_time`,`expire_time`,`reason`,`remark`,`last_operation`,`status`,`create_time`,`update_time`)
SELECT @example_list_library_2,'MOBILE','13800138001','2026-01-01 00:00:00','2026-12-31 23:59:59','测试样例','导入时可删除样例行','ADD',1,'2026-07-04 03:12:23','2026-07-04 03:12:23'
WHERE NOT EXISTS (SELECT 1 FROM rule_list_record existing WHERE existing.`list_id` <=> @example_list_library_2 AND existing.`item_type` <=> 'MOBILE' AND existing.`item_content` <=> '13800138001');
SET @example_list_record_2 = (SELECT existing.id FROM rule_list_record existing WHERE existing.`list_id` <=> @example_list_library_2 AND existing.`item_type` <=> 'MOBILE' AND existing.`item_content` <=> '13800138001');

-- rule_list_record: 3
INSERT INTO rule_list_record (`list_id`,`item_type`,`item_content`,`effective_time`,`expire_time`,`reason`,`remark`,`last_operation`,`status`,`create_time`,`update_time`)
SELECT @example_list_library_1,'MOBILE','13800138002','2026-01-01 00:00:00','2026-12-31 23:59:59','测试样例','导入时可删除样例行','ADD',1,'2026-07-04 03:12:37','2026-07-04 03:12:37'
WHERE NOT EXISTS (SELECT 1 FROM rule_list_record existing WHERE existing.`list_id` <=> @example_list_library_1 AND existing.`item_type` <=> 'MOBILE' AND existing.`item_content` <=> '13800138002');
SET @example_list_record_3 = (SELECT existing.id FROM rule_list_record existing WHERE existing.`list_id` <=> @example_list_library_1 AND existing.`item_type` <=> 'MOBILE' AND existing.`item_content` <=> '13800138002');

-- rule_model_input_field: HYBASE_X115
INSERT INTO rule_model_input_field (`model_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`data_type`,`default_value`,`valid_values`,`feature_name`,`transform_type`,`transform_params`,`importance_score`,`sort_order`,`status`,`create_time`)
SELECT @example_model_1,@example_data_object_field_26,'DATA_OBJECT','HYBASE_X115','HYBASE_X115','HYBASE_X115','DOUBLE','CONTINUOUS',NULL,NULL,NULL,'NONE',NULL,NULL,0,1,'2026-07-04 03:15:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYBASE_X115');
SET @example_model_input_field_1 = (SELECT existing.id FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYBASE_X115');

-- rule_model_input_field: HYBASE_X142
INSERT INTO rule_model_input_field (`model_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`data_type`,`default_value`,`valid_values`,`feature_name`,`transform_type`,`transform_params`,`importance_score`,`sort_order`,`status`,`create_time`)
SELECT @example_model_1,@example_data_object_field_27,'DATA_OBJECT','HYBASE_X142','HYBASE_X142','HYBASE_X142','DOUBLE','CONTINUOUS',NULL,NULL,NULL,'NONE',NULL,NULL,1,1,'2026-07-04 03:15:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYBASE_X142');
SET @example_model_input_field_2 = (SELECT existing.id FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYBASE_X142');

-- rule_model_input_field: HYBASE_X245
INSERT INTO rule_model_input_field (`model_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`data_type`,`default_value`,`valid_values`,`feature_name`,`transform_type`,`transform_params`,`importance_score`,`sort_order`,`status`,`create_time`)
SELECT @example_model_1,@example_data_object_field_28,'DATA_OBJECT','HYBASE_X245','HYBASE_X245','HYBASE_X245','DOUBLE','CONTINUOUS',NULL,NULL,NULL,'NONE',NULL,NULL,2,1,'2026-07-04 03:15:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYBASE_X245');
SET @example_model_input_field_3 = (SELECT existing.id FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYBASE_X245');

-- rule_model_input_field: HYDK_X1276
INSERT INTO rule_model_input_field (`model_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`data_type`,`default_value`,`valid_values`,`feature_name`,`transform_type`,`transform_params`,`importance_score`,`sort_order`,`status`,`create_time`)
SELECT @example_model_1,@example_data_object_field_29,'DATA_OBJECT','HYDK_X1276','HYDK_X1276','HYDK_X1276','DOUBLE','CONTINUOUS',NULL,NULL,NULL,'NONE',NULL,NULL,3,1,'2026-07-04 03:15:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYDK_X1276');
SET @example_model_input_field_4 = (SELECT existing.id FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYDK_X1276');

-- rule_model_input_field: HYDK_X1359
INSERT INTO rule_model_input_field (`model_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`data_type`,`default_value`,`valid_values`,`feature_name`,`transform_type`,`transform_params`,`importance_score`,`sort_order`,`status`,`create_time`)
SELECT @example_model_1,@example_data_object_field_30,'DATA_OBJECT','HYDK_X1359','HYDK_X1359','HYDK_X1359','DOUBLE','CONTINUOUS',NULL,NULL,NULL,'NONE',NULL,NULL,4,1,'2026-07-04 03:15:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYDK_X1359');
SET @example_model_input_field_5 = (SELECT existing.id FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYDK_X1359');

-- rule_model_input_field: HYDK_X2623
INSERT INTO rule_model_input_field (`model_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`data_type`,`default_value`,`valid_values`,`feature_name`,`transform_type`,`transform_params`,`importance_score`,`sort_order`,`status`,`create_time`)
SELECT @example_model_1,@example_data_object_field_31,'DATA_OBJECT','HYDK_X2623','HYDK_X2623','HYDK_X2623','DOUBLE','CONTINUOUS',NULL,NULL,NULL,'NONE',NULL,NULL,5,1,'2026-07-04 03:15:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYDK_X2623');
SET @example_model_input_field_6 = (SELECT existing.id FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYDK_X2623');

-- rule_model_input_field: HYDK_X2761
INSERT INTO rule_model_input_field (`model_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`data_type`,`default_value`,`valid_values`,`feature_name`,`transform_type`,`transform_params`,`importance_score`,`sort_order`,`status`,`create_time`)
SELECT @example_model_1,@example_data_object_field_32,'DATA_OBJECT','HYDK_X2761','HYDK_X2761','HYDK_X2761','DOUBLE','CONTINUOUS',NULL,NULL,NULL,'NONE',NULL,NULL,6,1,'2026-07-04 03:15:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYDK_X2761');
SET @example_model_input_field_7 = (SELECT existing.id FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYDK_X2761');

-- rule_model_input_field: HYDK_X760
INSERT INTO rule_model_input_field (`model_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`data_type`,`default_value`,`valid_values`,`feature_name`,`transform_type`,`transform_params`,`importance_score`,`sort_order`,`status`,`create_time`)
SELECT @example_model_1,@example_data_object_field_33,'DATA_OBJECT','HYDK_X760','HYDK_X760','HYDK_X760','DOUBLE','CONTINUOUS',NULL,NULL,NULL,'NONE',NULL,NULL,7,1,'2026-07-04 03:15:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYDK_X760');
SET @example_model_input_field_8 = (SELECT existing.id FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYDK_X760');

-- rule_model_input_field: HYJH_X54
INSERT INTO rule_model_input_field (`model_id`,`var_id`,`ref_type`,`field_name`,`field_label`,`script_name`,`field_type`,`data_type`,`default_value`,`valid_values`,`feature_name`,`transform_type`,`transform_params`,`importance_score`,`sort_order`,`status`,`create_time`)
SELECT @example_model_1,@example_data_object_field_34,'DATA_OBJECT','HYJH_X54','HYJH_X54','HYJH_X54','DOUBLE','CONTINUOUS',NULL,NULL,NULL,'NONE',NULL,NULL,8,1,'2026-07-04 03:15:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYJH_X54');
SET @example_model_input_field_9 = (SELECT existing.id FROM rule_model_input_field existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`field_name` <=> 'HYJH_X54');

-- rule_model_version: 1
INSERT INTO rule_model_version (`model_id`,`version`,`model_content`,`model_config`,`change_log`,`publish_by`,`publish_time`)
SELECT @example_model_1,1,'PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9InllcyI/Pgo8UE1NTCB4bWxucz0iaHR0cDovL3d3dy5kbWcub3JnL1BNTUwtNF80IiB4bWxuczpkYXRhPSJodHRwOi8vanBtbWwub3JnL2pwbW1sLW1vZGVsL0lubGluZVRhYmxlIiB2ZXJzaW9uPSI0LjQiPgoJPEhlYWRlcj4KCQk8QXBwbGljYXRpb24gbmFtZT0iU2tMZWFybjJQTU1MIHBhY2thZ2UiIHZlcnNpb249IjAuOTAuNCIvPgoJCTxUaW1lc3RhbXA+MjAyMy0xMC0wOFQwOTo1ODoxMFo8L1RpbWVzdGFtcD4KCTwvSGVhZGVyPgoJPE1pbmluZ0J1aWxkVGFzaz4KCQk8RXh0ZW5zaW9uIG5hbWU9InJlcHIiPlBNTUxQaXBlbGluZShzdGVwcz1bKCdwcmVwcm9jZXNzaW5nJywgRGF0YUZyYW1lTWFwcGVyKGRmX291dD1UcnVlLCBkcm9wX2NvbHM9W10sCiAgICAgICAgICAgICAgICBmZWF0dXJlcz1bKFsnSFlCQVNFX1gxMTUnXSwKICAgICAgICAgICAgICAgICAgICAgICAgICAgRXhwcmVzc2lvblRyYW5zZm9ybWVyKGV4cHI9JzguMCBpZiBYWzBdICZsdDsgMi4wIGVsc2UgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnKDI1LjE3IGlmIFhbMF0gJmd0Oz0gMi4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2FuZCBYWzBdICZsdDsgMy4wIGVsc2UgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnKDQwLjU3IGlmIFhbMF0gJmd0Oz0gMy4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2Vsc2UgLTguODkpKScpKSwKICAgICAgICAgICAgICAgICAgICAgICAgICAoWydIWUJBU0VfWDE0MiddLAogICAgICAgICAgICAgICAgICAgICAgICAgICBFeHByZXNzaW9uVHJhbnNmb3JtZXIoZXhwcj0nNTQuNjIgaWYgWFswXSAmbHQ7IDMuMCAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdlbHNlICgzNC4yMiBpZiBYWzBdICZndDs9ICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJzMuMCBhbmQgWFswXSAmbHQ7IDQuMCBlbHNlICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJygyNi44OCBpZiBYWzBdICZndDs9IDQuMCAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdlbHNlIDY0LjQ0KSknKSksCiAgICAgICAgICAgICAgICAgICAgICAgICAgKFsnSFlCQVNFX1gyNDUnXSwKICAgICAgICAgICAgICAgICAgICAgICAgICAgRXhwcmVzc2lvblRyLi4uCiAgICAgICAgICAgICAgICAgICAgICAgICAgKFsnSFlES19YMjc2MSddLAogICAgICAgICAgICAgICAgICAgICAgICAgICBFeHByZXNzaW9uVHJhbnNmb3JtZXIoZXhwcj0nMTQuODcgaWYgWFswXSAmbHQ7IDM2ODUuMCAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdlbHNlICgyMi4xOCBpZiBYWzBdICZndDs9ICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJzM2ODUuMCBhbmQgWFswXSAmbHQ7ICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJzk5NzAuMCBlbHNlICgzMC40OCBpZiAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdYWzBdICZndDs9IDk5NzAuMCBhbmQgWFswXSAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICcmbHQ7IDIyNTAwLjAgZWxzZSAoNDMuMDQgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnaWYgWFswXSAmZ3Q7PSAyMjUwMC4wIGVsc2UgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnMzguMDgpKSknKSksCiAgICAgICAgICAgICAgICAgICAgICAgICAgKFsnSFlES19YNzYwJ10sCiAgICAgICAgICAgICAgICAgICAgICAgICAgIEV4cHJlc3Npb25UcmFuc2Zvcm1lcihleHByPSczNC4xNCBpZiBYWzBdICZsdDsgMS4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2Vsc2UgKDExLjQxIGlmIFhbMF0gJmd0Oz0gJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnMS4wIGVsc2UgNDYuMjkpJykpLAogICAgICAgICAgICAgICAgICAgICAgICAgIChbJ0hZSkhfWDU0J10sCiAgICAgICAgICAgICAgICAgICAgICAgICAgIEV4cHJlc3Npb25UcmFuc2Zvcm1lcihleHByPSczNi45OSBpZiBYWzBdICZsdDsgMS4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2Vsc2UgLTUuMDcnKSldKSksCiAgICAgICAoJ3Njb3JlY2FyZCcsIExpbmVhclJlZ3Jlc3Npb24oKSldKTwvRXh0ZW5zaW9uPgoJPC9NaW5pbmdCdWlsZFRhc2s+Cgk8RGF0YURpY3Rpb25hcnk+CgkJPERhdGFGaWVsZCBuYW1lPSJzY29yZSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUJBU0VfWDExNSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUJBU0VfWDE0MiIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUJBU0VfWDI0NSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWURLX1gxMjc2IiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiLz4KCQk8RGF0YUZpZWxkIG5hbWU9IkhZREtfWDEzNTkiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSIvPgoJCTxEYXRhRmllbGQgbmFtZT0iSFlES19YMjYyMyIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWURLX1gyNzYxIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiLz4KCQk8RGF0YUZpZWxkIG5hbWU9IkhZREtfWDc2MCIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUpIX1g1NCIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+Cgk8L0RhdGFEaWN0aW9uYXJ5PgoJPFJlZ3Jlc3Npb25Nb2RlbCBmdW5jdGlvbk5hbWU9InJlZ3Jlc3Npb24iIGFsZ29yaXRobU5hbWU9InNrbGVhcm4ubGluZWFyX21vZGVsLl9iYXNlLkxpbmVhclJlZ3Jlc3Npb24iPgoJCTxNaW5pbmdTY2hlbWE+CgkJCTxNaW5pbmdGaWVsZCBuYW1lPSJzY29yZSIgdXNhZ2VUeXBlPSJ0YXJnZXQiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZQkFTRV9YMTE1Ii8+CgkJCTxNaW5pbmdGaWVsZCBuYW1lPSJIWUJBU0VfWDE0MiIvPgoJCQk8TWluaW5nRmllbGQgbmFtZT0iSFlCQVNFX1gyNDUiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDEyNzYiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDEzNTkiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDI2MjMiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDI3NjEiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDc2MCIvPgoJCQk8TWluaW5nRmllbGQgbmFtZT0iSFlKSF9YNTQiLz4KCQk8L01pbmluZ1NjaGVtYT4KCQk8TG9jYWxUcmFuc2Zvcm1hdGlvbnM+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCg4LjAgaWYgWFswXSAmbHQ7IDIuMCBlbHNlICgyNS4xNyBpZiBYWzBdICZndDs9IDIuMCBhbmQgWFswXSAmbHQ7IDMuMCBlbHNlICg0MC41NyBpZiBYWzBdICZndDs9IDMuMCBlbHNlIC04Ljg5KSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gxMTUiLz4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIuMDwvQ29uc3RhbnQ+CgkJCQkJPC9BcHBseT4KCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+OC4wPC9Db25zdGFudD4KCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJhbmQiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDExNSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4yLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gxMTUiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+My4wPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4yNS4xNzwvQ29uc3RhbnQ+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDExNSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj40MC41NzwvQ29uc3RhbnQ+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+LTguODk8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCTwvQXBwbHk+CgkJCQk8L0FwcGx5PgoJCQk8L0Rlcml2ZWRGaWVsZD4KCQkJPERlcml2ZWRGaWVsZCBuYW1lPSJldmFsKDU0LjYyIGlmIFhbMF0gJmx0OyAzLjAgZWxzZSAoMzQuMjIgaWYgWFswXSAmZ3Q7PSAzLjAgYW5kIFhbMF0gJmx0OyA0LjAgZWxzZSAoMjYuODggaWYgWFswXSAmZ3Q7PSA0LjAgZWxzZSA2NC40NCkpKSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIj4KCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZQkFTRV9YMTQyIi8+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zLjA8L0NvbnN0YW50PgoJCQkJCTwvQXBwbHk+CgkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjU0LjYyPC9Db25zdGFudD4KCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJhbmQiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDE0MiIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gxNDIiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NC4wPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNC4yMjwvQ29uc3RhbnQ+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDE0MiIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj40LjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4yNi44ODwvQ29uc3RhbnQ+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NjQuNDQ8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCTwvQXBwbHk+CgkJCQk8L0FwcGx5PgoJCQk8L0Rlcml2ZWRGaWVsZD4KCQkJPERlcml2ZWRGaWVsZCBuYW1lPSJldmFsKDY0LjYgaWYgWFswXSAmbHQ7IDAuMzY4NDIwODU4NzI1ODYzOCBlbHNlICg0MS43NyBpZiBYWzBdICZndDs9IDAuMzY4NDIwODU4NzI1ODYzOCBhbmQgWFswXSAmbHQ7IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlICgyMS41NiBpZiBYWzBdICZndDs9IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlIDEzLjM3KSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gyNDUiLz4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjAuMzY4NDIwODU4NzI1ODYzODwvQ29uc3RhbnQ+CgkJCQkJPC9BcHBseT4KCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NjQuNjwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gyNDUiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC4zNjg0MjA4NTg3MjU4NjM4PC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZQkFTRV9YMjQ1Ii8+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjAuNzA1ODgxOTM3NzE2NTA3MjwvQ29uc3RhbnQ+CgkJCQkJCQk8L0FwcGx5PgoJCQkJCQk8L0FwcGx5PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NDEuNzc8L0NvbnN0YW50PgoJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gyNDUiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC43MDU4ODE5Mzc3MTY1MDcyPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MjEuNTY8L0NvbnN0YW50PgoJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjEzLjM3PC9Db25zdGFudD4KCQkJCQkJPC9BcHBseT4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCgzNC4wNCBpZiBYWzBdICZsdDsgMS4wIGVsc2UgKDMuNSBpZiBYWzBdICZndDs9IDEuMCBlbHNlIDU0LjAxKSkiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWURLX1gxMjc2Ii8+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xLjA8L0NvbnN0YW50PgoJCQkJCTwvQXBwbHk+CgkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjM0LjA0PC9Db25zdGFudD4KCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDEyNzYiLz4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xLjA8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+My41PC9Db25zdGFudD4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjU0LjAxPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCg1MC40NCBpZiBYWzBdICZsdDsgMC4xMTk5OTk5OTg0IGVsc2UgKDQxLjY3IGlmIFhbMF0gJmd0Oz0gMC4xMTk5OTk5OTg0IGFuZCBYWzBdICZsdDsgMC4yNDk5OTk5NzkxNjY2Njg0IGVsc2UgKDI4LjY2IGlmIFhbMF0gJmd0Oz0gMC4yNDk5OTk5NzkxNjY2Njg0IGFuZCBYWzBdICZsdDsgMC40OTk5OTk4NzUwMDAwMzEyIGVsc2UgKC00LjkyIGlmIFhbMF0gJmd0Oz0gMC40OTk5OTk4NzUwMDAwMzEyIGVsc2UgNjEuODYpKSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC4xMTk5OTk5OTg0PC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj41MC40NDwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4wLjExOTk5OTk5ODQ8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4wLjI0OTk5OTk3OTE2NjY2ODQ8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjQxLjY3PC9Db25zdGFudD4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImFuZCI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC4yNDk5OTk5NzkxNjY2Njg0PC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDEzNTkiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjAuNDk5OTk5ODc1MDAwMDMxMjwvQ29uc3RhbnQ+CgkJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MjguNjY8L0NvbnN0YW50PgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC40OTk5OTk4NzUwMDAwMzEyPC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4tNC45MjwvQ29uc3RhbnQ+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjYxLjg2PC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJPC9BcHBseT4KCQkJCTwvQXBwbHk+CgkJCTwvRGVyaXZlZEZpZWxkPgoJCQk8RGVyaXZlZEZpZWxkIG5hbWU9ImV2YWwoLTMuMDIgaWYgWFswXSAmbHQ7IDEzMTg4LjAgZWxzZSAoMjQuNzIgaWYgWFswXSAmZ3Q7PSAxMzE4OC4wIGFuZCBYWzBdICZsdDsgMTU3NjA3LjAgZWxzZSAoMzIuOTEgaWYgWFswXSAmZ3Q7PSAxNTc2MDcuMCBhbmQgWFswXSAmbHQ7IDM4MDAwMC4wIGVsc2UgKDcxLjY0IGlmIFhbMF0gJmd0Oz0gMzgwMDAwLjAgZWxzZSA1OS4yNSkpKSkiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWURLX1gyNjIzIi8+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xMzE4OC4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4tMy4wMjwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjYyMyIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xMzE4OC4wPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI2MjMiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MTU3NjA3LjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjI0LjcyPC9Db25zdGFudD4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImFuZCI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjYyMyIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MTU3NjA3LjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjYyMyIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MzgwMDAwLjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjMyLjkxPC9Db25zdGFudD4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI2MjMiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjM4MDAwMC4wPC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj43MS42NDwvQ29uc3RhbnQ+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjU5LjI1PC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJPC9BcHBseT4KCQkJCTwvQXBwbHk+CgkJCTwvRGVyaXZlZEZpZWxkPgoJCQk8RGVyaXZlZEZpZWxkIG5hbWU9ImV2YWwoMTQuODcgaWYgWFswXSAmbHQ7IDM2ODUuMCBlbHNlICgyMi4xOCBpZiBYWzBdICZndDs9IDM2ODUuMCBhbmQgWFswXSAmbHQ7IDk5NzAuMCBlbHNlICgzMC40OCBpZiBYWzBdICZndDs9IDk5NzAuMCBhbmQgWFswXSAmbHQ7IDIyNTAwLjAgZWxzZSAoNDMuMDQgaWYgWFswXSAmZ3Q7PSAyMjUwMC4wIGVsc2UgMzguMDgpKSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MzY4NS4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xNC44NzwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNjg1LjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj45OTcwLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIyLjE4PC9Db25zdGFudD4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImFuZCI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+OTk3MC4wPC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI3NjEiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIyNTAwLjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjMwLjQ4PC9Db25zdGFudD4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI3NjEiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIyNTAwLjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjQzLjA0PC9Db25zdGFudD4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MzguMDg8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCgzNC4xNCBpZiBYWzBdICZsdDsgMS4wIGVsc2UgKDExLjQxIGlmIFhbMF0gJmd0Oz0gMS4wIGVsc2UgNDYuMjkpKSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIj4KCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDc2MCIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MS4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNC4xNDwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWURLX1g3NjAiLz4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xLjA8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MTEuNDE8L0NvbnN0YW50PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NDYuMjk8L0NvbnN0YW50PgoJCQkJCTwvQXBwbHk+CgkJCQk8L0FwcGx5PgoJCQk8L0Rlcml2ZWRGaWVsZD4KCQkJPERlcml2ZWRGaWVsZCBuYW1lPSJldmFsKDM2Ljk5IGlmIFhbMF0gJmx0OyAxLjAgZWxzZSAtNS4wNykiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUpIX1g1NCIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MS4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNi45OTwvQ29uc3RhbnQ+CgkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPi01LjA3PC9Db25zdGFudD4KCQkJCTwvQXBwbHk+CgkJCTwvRGVyaXZlZEZpZWxkPgoJCTwvTG9jYWxUcmFuc2Zvcm1hdGlvbnM+CgkJPFJlZ3Jlc3Npb25UYWJsZSBpbnRlcmNlcHQ9IjAuMCI+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoOC4wIGlmIFhbMF0gJmx0OyAyLjAgZWxzZSAoMjUuMTcgaWYgWFswXSAmZ3Q7PSAyLjAgYW5kIFhbMF0gJmx0OyAzLjAgZWxzZSAoNDAuNTcgaWYgWFswXSAmZ3Q7PSAzLjAgZWxzZSAtOC44OSkpKSIgY29lZmZpY2llbnQ9IjEuMCIvPgoJCQk8TnVtZXJpY1ByZWRpY3RvciBuYW1lPSJldmFsKDU0LjYyIGlmIFhbMF0gJmx0OyAzLjAgZWxzZSAoMzQuMjIgaWYgWFswXSAmZ3Q7PSAzLjAgYW5kIFhbMF0gJmx0OyA0LjAgZWxzZSAoMjYuODggaWYgWFswXSAmZ3Q7PSA0LjAgZWxzZSA2NC40NCkpKSIgY29lZmZpY2llbnQ9IjEuMCIvPgoJCQk8TnVtZXJpY1ByZWRpY3RvciBuYW1lPSJldmFsKDY0LjYgaWYgWFswXSAmbHQ7IDAuMzY4NDIwODU4NzI1ODYzOCBlbHNlICg0MS43NyBpZiBYWzBdICZndDs9IDAuMzY4NDIwODU4NzI1ODYzOCBhbmQgWFswXSAmbHQ7IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlICgyMS41NiBpZiBYWzBdICZndDs9IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlIDEzLjM3KSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoMzQuMDQgaWYgWFswXSAmbHQ7IDEuMCBlbHNlICgzLjUgaWYgWFswXSAmZ3Q7PSAxLjAgZWxzZSA1NC4wMSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoNTAuNDQgaWYgWFswXSAmbHQ7IDAuMTE5OTk5OTk4NCBlbHNlICg0MS42NyBpZiBYWzBdICZndDs9IDAuMTE5OTk5OTk4NCBhbmQgWFswXSAmbHQ7IDAuMjQ5OTk5OTc5MTY2NjY4NCBlbHNlICgyOC42NiBpZiBYWzBdICZndDs9IDAuMjQ5OTk5OTc5MTY2NjY4NCBhbmQgWFswXSAmbHQ7IDAuNDk5OTk5ODc1MDAwMDMxMiBlbHNlICgtNC45MiBpZiBYWzBdICZndDs9IDAuNDk5OTk5ODc1MDAwMDMxMiBlbHNlIDYxLjg2KSkpKSIgY29lZmZpY2llbnQ9IjEuMCIvPgoJCQk8TnVtZXJpY1ByZWRpY3RvciBuYW1lPSJldmFsKC0zLjAyIGlmIFhbMF0gJmx0OyAxMzE4OC4wIGVsc2UgKDI0LjcyIGlmIFhbMF0gJmd0Oz0gMTMxODguMCBhbmQgWFswXSAmbHQ7IDE1NzYwNy4wIGVsc2UgKDMyLjkxIGlmIFhbMF0gJmd0Oz0gMTU3NjA3LjAgYW5kIFhbMF0gJmx0OyAzODAwMDAuMCBlbHNlICg3MS42NCBpZiBYWzBdICZndDs9IDM4MDAwMC4wIGVsc2UgNTkuMjUpKSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoMTQuODcgaWYgWFswXSAmbHQ7IDM2ODUuMCBlbHNlICgyMi4xOCBpZiBYWzBdICZndDs9IDM2ODUuMCBhbmQgWFswXSAmbHQ7IDk5NzAuMCBlbHNlICgzMC40OCBpZiBYWzBdICZndDs9IDk5NzAuMCBhbmQgWFswXSAmbHQ7IDIyNTAwLjAgZWxzZSAoNDMuMDQgaWYgWFswXSAmZ3Q7PSAyMjUwMC4wIGVsc2UgMzguMDgpKSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoMzQuMTQgaWYgWFswXSAmbHQ7IDEuMCBlbHNlICgxMS40MSBpZiBYWzBdICZndDs9IDEuMCBlbHNlIDQ2LjI5KSkiIGNvZWZmaWNpZW50PSIxLjAiLz4KCQkJPE51bWVyaWNQcmVkaWN0b3IgbmFtZT0iZXZhbCgzNi45OSBpZiBYWzBdICZsdDsgMS4wIGVsc2UgLTUuMDcpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJPC9SZWdyZXNzaW9uVGFibGU+Cgk8L1JlZ3Jlc3Npb25Nb2RlbD4KPC9QTU1MPgo=','{"summary": "Regression", "functionName": "regression", "algorithmName": "sklearn.linear_model._base.LinearRegression"}','',NULL,'2026-07-04 03:15:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_model_version existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`version` <=> 1);
SET @example_model_version_1 = (SELECT existing.id FROM rule_model_version existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`version` <=> 1);

-- rule_model_version: 2
INSERT INTO rule_model_version (`model_id`,`version`,`model_content`,`model_config`,`change_log`,`publish_by`,`publish_time`)
SELECT @example_model_1,2,'PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9InllcyI/Pgo8UE1NTCB4bWxucz0iaHR0cDovL3d3dy5kbWcub3JnL1BNTUwtNF80IiB4bWxuczpkYXRhPSJodHRwOi8vanBtbWwub3JnL2pwbW1sLW1vZGVsL0lubGluZVRhYmxlIiB2ZXJzaW9uPSI0LjQiPgoJPEhlYWRlcj4KCQk8QXBwbGljYXRpb24gbmFtZT0iU2tMZWFybjJQTU1MIHBhY2thZ2UiIHZlcnNpb249IjAuOTAuNCIvPgoJCTxUaW1lc3RhbXA+MjAyMy0xMC0wOFQwOTo1ODoxMFo8L1RpbWVzdGFtcD4KCTwvSGVhZGVyPgoJPE1pbmluZ0J1aWxkVGFzaz4KCQk8RXh0ZW5zaW9uIG5hbWU9InJlcHIiPlBNTUxQaXBlbGluZShzdGVwcz1bKCdwcmVwcm9jZXNzaW5nJywgRGF0YUZyYW1lTWFwcGVyKGRmX291dD1UcnVlLCBkcm9wX2NvbHM9W10sCiAgICAgICAgICAgICAgICBmZWF0dXJlcz1bKFsnSFlCQVNFX1gxMTUnXSwKICAgICAgICAgICAgICAgICAgICAgICAgICAgRXhwcmVzc2lvblRyYW5zZm9ybWVyKGV4cHI9JzguMCBpZiBYWzBdICZsdDsgMi4wIGVsc2UgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnKDI1LjE3IGlmIFhbMF0gJmd0Oz0gMi4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2FuZCBYWzBdICZsdDsgMy4wIGVsc2UgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnKDQwLjU3IGlmIFhbMF0gJmd0Oz0gMy4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2Vsc2UgLTguODkpKScpKSwKICAgICAgICAgICAgICAgICAgICAgICAgICAoWydIWUJBU0VfWDE0MiddLAogICAgICAgICAgICAgICAgICAgICAgICAgICBFeHByZXNzaW9uVHJhbnNmb3JtZXIoZXhwcj0nNTQuNjIgaWYgWFswXSAmbHQ7IDMuMCAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdlbHNlICgzNC4yMiBpZiBYWzBdICZndDs9ICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJzMuMCBhbmQgWFswXSAmbHQ7IDQuMCBlbHNlICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJygyNi44OCBpZiBYWzBdICZndDs9IDQuMCAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdlbHNlIDY0LjQ0KSknKSksCiAgICAgICAgICAgICAgICAgICAgICAgICAgKFsnSFlCQVNFX1gyNDUnXSwKICAgICAgICAgICAgICAgICAgICAgICAgICAgRXhwcmVzc2lvblRyLi4uCiAgICAgICAgICAgICAgICAgICAgICAgICAgKFsnSFlES19YMjc2MSddLAogICAgICAgICAgICAgICAgICAgICAgICAgICBFeHByZXNzaW9uVHJhbnNmb3JtZXIoZXhwcj0nMTQuODcgaWYgWFswXSAmbHQ7IDM2ODUuMCAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdlbHNlICgyMi4xOCBpZiBYWzBdICZndDs9ICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJzM2ODUuMCBhbmQgWFswXSAmbHQ7ICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJzk5NzAuMCBlbHNlICgzMC40OCBpZiAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdYWzBdICZndDs9IDk5NzAuMCBhbmQgWFswXSAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICcmbHQ7IDIyNTAwLjAgZWxzZSAoNDMuMDQgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnaWYgWFswXSAmZ3Q7PSAyMjUwMC4wIGVsc2UgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnMzguMDgpKSknKSksCiAgICAgICAgICAgICAgICAgICAgICAgICAgKFsnSFlES19YNzYwJ10sCiAgICAgICAgICAgICAgICAgICAgICAgICAgIEV4cHJlc3Npb25UcmFuc2Zvcm1lcihleHByPSczNC4xNCBpZiBYWzBdICZsdDsgMS4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2Vsc2UgKDExLjQxIGlmIFhbMF0gJmd0Oz0gJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnMS4wIGVsc2UgNDYuMjkpJykpLAogICAgICAgICAgICAgICAgICAgICAgICAgIChbJ0hZSkhfWDU0J10sCiAgICAgICAgICAgICAgICAgICAgICAgICAgIEV4cHJlc3Npb25UcmFuc2Zvcm1lcihleHByPSczNi45OSBpZiBYWzBdICZsdDsgMS4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2Vsc2UgLTUuMDcnKSldKSksCiAgICAgICAoJ3Njb3JlY2FyZCcsIExpbmVhclJlZ3Jlc3Npb24oKSldKTwvRXh0ZW5zaW9uPgoJPC9NaW5pbmdCdWlsZFRhc2s+Cgk8RGF0YURpY3Rpb25hcnk+CgkJPERhdGFGaWVsZCBuYW1lPSJzY29yZSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUJBU0VfWDExNSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUJBU0VfWDE0MiIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUJBU0VfWDI0NSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWURLX1gxMjc2IiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiLz4KCQk8RGF0YUZpZWxkIG5hbWU9IkhZREtfWDEzNTkiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSIvPgoJCTxEYXRhRmllbGQgbmFtZT0iSFlES19YMjYyMyIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWURLX1gyNzYxIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiLz4KCQk8RGF0YUZpZWxkIG5hbWU9IkhZREtfWDc2MCIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUpIX1g1NCIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+Cgk8L0RhdGFEaWN0aW9uYXJ5PgoJPFJlZ3Jlc3Npb25Nb2RlbCBmdW5jdGlvbk5hbWU9InJlZ3Jlc3Npb24iIGFsZ29yaXRobU5hbWU9InNrbGVhcm4ubGluZWFyX21vZGVsLl9iYXNlLkxpbmVhclJlZ3Jlc3Npb24iPgoJCTxNaW5pbmdTY2hlbWE+CgkJCTxNaW5pbmdGaWVsZCBuYW1lPSJzY29yZSIgdXNhZ2VUeXBlPSJ0YXJnZXQiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZQkFTRV9YMTE1Ii8+CgkJCTxNaW5pbmdGaWVsZCBuYW1lPSJIWUJBU0VfWDE0MiIvPgoJCQk8TWluaW5nRmllbGQgbmFtZT0iSFlCQVNFX1gyNDUiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDEyNzYiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDEzNTkiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDI2MjMiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDI3NjEiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDc2MCIvPgoJCQk8TWluaW5nRmllbGQgbmFtZT0iSFlKSF9YNTQiLz4KCQk8L01pbmluZ1NjaGVtYT4KCQk8TG9jYWxUcmFuc2Zvcm1hdGlvbnM+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCg4LjAgaWYgWFswXSAmbHQ7IDIuMCBlbHNlICgyNS4xNyBpZiBYWzBdICZndDs9IDIuMCBhbmQgWFswXSAmbHQ7IDMuMCBlbHNlICg0MC41NyBpZiBYWzBdICZndDs9IDMuMCBlbHNlIC04Ljg5KSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gxMTUiLz4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIuMDwvQ29uc3RhbnQ+CgkJCQkJPC9BcHBseT4KCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+OC4wPC9Db25zdGFudD4KCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJhbmQiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDExNSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4yLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gxMTUiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+My4wPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4yNS4xNzwvQ29uc3RhbnQ+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDExNSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj40MC41NzwvQ29uc3RhbnQ+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+LTguODk8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCTwvQXBwbHk+CgkJCQk8L0FwcGx5PgoJCQk8L0Rlcml2ZWRGaWVsZD4KCQkJPERlcml2ZWRGaWVsZCBuYW1lPSJldmFsKDU0LjYyIGlmIFhbMF0gJmx0OyAzLjAgZWxzZSAoMzQuMjIgaWYgWFswXSAmZ3Q7PSAzLjAgYW5kIFhbMF0gJmx0OyA0LjAgZWxzZSAoMjYuODggaWYgWFswXSAmZ3Q7PSA0LjAgZWxzZSA2NC40NCkpKSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIj4KCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZQkFTRV9YMTQyIi8+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zLjA8L0NvbnN0YW50PgoJCQkJCTwvQXBwbHk+CgkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjU0LjYyPC9Db25zdGFudD4KCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJhbmQiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDE0MiIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gxNDIiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NC4wPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNC4yMjwvQ29uc3RhbnQ+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDE0MiIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj40LjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4yNi44ODwvQ29uc3RhbnQ+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NjQuNDQ8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCTwvQXBwbHk+CgkJCQk8L0FwcGx5PgoJCQk8L0Rlcml2ZWRGaWVsZD4KCQkJPERlcml2ZWRGaWVsZCBuYW1lPSJldmFsKDY0LjYgaWYgWFswXSAmbHQ7IDAuMzY4NDIwODU4NzI1ODYzOCBlbHNlICg0MS43NyBpZiBYWzBdICZndDs9IDAuMzY4NDIwODU4NzI1ODYzOCBhbmQgWFswXSAmbHQ7IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlICgyMS41NiBpZiBYWzBdICZndDs9IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlIDEzLjM3KSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gyNDUiLz4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjAuMzY4NDIwODU4NzI1ODYzODwvQ29uc3RhbnQ+CgkJCQkJPC9BcHBseT4KCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NjQuNjwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gyNDUiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC4zNjg0MjA4NTg3MjU4NjM4PC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZQkFTRV9YMjQ1Ii8+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjAuNzA1ODgxOTM3NzE2NTA3MjwvQ29uc3RhbnQ+CgkJCQkJCQk8L0FwcGx5PgoJCQkJCQk8L0FwcGx5PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NDEuNzc8L0NvbnN0YW50PgoJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gyNDUiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC43MDU4ODE5Mzc3MTY1MDcyPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MjEuNTY8L0NvbnN0YW50PgoJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjEzLjM3PC9Db25zdGFudD4KCQkJCQkJPC9BcHBseT4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCgzNC4wNCBpZiBYWzBdICZsdDsgMS4wIGVsc2UgKDMuNSBpZiBYWzBdICZndDs9IDEuMCBlbHNlIDU0LjAxKSkiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWURLX1gxMjc2Ii8+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xLjA8L0NvbnN0YW50PgoJCQkJCTwvQXBwbHk+CgkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjM0LjA0PC9Db25zdGFudD4KCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDEyNzYiLz4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xLjA8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+My41PC9Db25zdGFudD4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjU0LjAxPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCg1MC40NCBpZiBYWzBdICZsdDsgMC4xMTk5OTk5OTg0IGVsc2UgKDQxLjY3IGlmIFhbMF0gJmd0Oz0gMC4xMTk5OTk5OTg0IGFuZCBYWzBdICZsdDsgMC4yNDk5OTk5NzkxNjY2Njg0IGVsc2UgKDI4LjY2IGlmIFhbMF0gJmd0Oz0gMC4yNDk5OTk5NzkxNjY2Njg0IGFuZCBYWzBdICZsdDsgMC40OTk5OTk4NzUwMDAwMzEyIGVsc2UgKC00LjkyIGlmIFhbMF0gJmd0Oz0gMC40OTk5OTk4NzUwMDAwMzEyIGVsc2UgNjEuODYpKSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC4xMTk5OTk5OTg0PC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj41MC40NDwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4wLjExOTk5OTk5ODQ8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4wLjI0OTk5OTk3OTE2NjY2ODQ8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjQxLjY3PC9Db25zdGFudD4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImFuZCI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC4yNDk5OTk5NzkxNjY2Njg0PC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDEzNTkiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjAuNDk5OTk5ODc1MDAwMDMxMjwvQ29uc3RhbnQ+CgkJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MjguNjY8L0NvbnN0YW50PgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC40OTk5OTk4NzUwMDAwMzEyPC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4tNC45MjwvQ29uc3RhbnQ+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjYxLjg2PC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJPC9BcHBseT4KCQkJCTwvQXBwbHk+CgkJCTwvRGVyaXZlZEZpZWxkPgoJCQk8RGVyaXZlZEZpZWxkIG5hbWU9ImV2YWwoLTMuMDIgaWYgWFswXSAmbHQ7IDEzMTg4LjAgZWxzZSAoMjQuNzIgaWYgWFswXSAmZ3Q7PSAxMzE4OC4wIGFuZCBYWzBdICZsdDsgMTU3NjA3LjAgZWxzZSAoMzIuOTEgaWYgWFswXSAmZ3Q7PSAxNTc2MDcuMCBhbmQgWFswXSAmbHQ7IDM4MDAwMC4wIGVsc2UgKDcxLjY0IGlmIFhbMF0gJmd0Oz0gMzgwMDAwLjAgZWxzZSA1OS4yNSkpKSkiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWURLX1gyNjIzIi8+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xMzE4OC4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4tMy4wMjwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjYyMyIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xMzE4OC4wPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI2MjMiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MTU3NjA3LjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjI0LjcyPC9Db25zdGFudD4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImFuZCI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjYyMyIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MTU3NjA3LjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjYyMyIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MzgwMDAwLjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjMyLjkxPC9Db25zdGFudD4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI2MjMiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjM4MDAwMC4wPC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj43MS42NDwvQ29uc3RhbnQ+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjU5LjI1PC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJPC9BcHBseT4KCQkJCTwvQXBwbHk+CgkJCTwvRGVyaXZlZEZpZWxkPgoJCQk8RGVyaXZlZEZpZWxkIG5hbWU9ImV2YWwoMTQuODcgaWYgWFswXSAmbHQ7IDM2ODUuMCBlbHNlICgyMi4xOCBpZiBYWzBdICZndDs9IDM2ODUuMCBhbmQgWFswXSAmbHQ7IDk5NzAuMCBlbHNlICgzMC40OCBpZiBYWzBdICZndDs9IDk5NzAuMCBhbmQgWFswXSAmbHQ7IDIyNTAwLjAgZWxzZSAoNDMuMDQgaWYgWFswXSAmZ3Q7PSAyMjUwMC4wIGVsc2UgMzguMDgpKSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MzY4NS4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xNC44NzwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNjg1LjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj45OTcwLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIyLjE4PC9Db25zdGFudD4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImFuZCI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+OTk3MC4wPC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI3NjEiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIyNTAwLjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjMwLjQ4PC9Db25zdGFudD4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI3NjEiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIyNTAwLjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjQzLjA0PC9Db25zdGFudD4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MzguMDg8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCgzNC4xNCBpZiBYWzBdICZsdDsgMS4wIGVsc2UgKDExLjQxIGlmIFhbMF0gJmd0Oz0gMS4wIGVsc2UgNDYuMjkpKSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIj4KCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDc2MCIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MS4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNC4xNDwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWURLX1g3NjAiLz4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xLjA8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MTEuNDE8L0NvbnN0YW50PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NDYuMjk8L0NvbnN0YW50PgoJCQkJCTwvQXBwbHk+CgkJCQk8L0FwcGx5PgoJCQk8L0Rlcml2ZWRGaWVsZD4KCQkJPERlcml2ZWRGaWVsZCBuYW1lPSJldmFsKDM2Ljk5IGlmIFhbMF0gJmx0OyAxLjAgZWxzZSAtNS4wNykiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUpIX1g1NCIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MS4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNi45OTwvQ29uc3RhbnQ+CgkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPi01LjA3PC9Db25zdGFudD4KCQkJCTwvQXBwbHk+CgkJCTwvRGVyaXZlZEZpZWxkPgoJCTwvTG9jYWxUcmFuc2Zvcm1hdGlvbnM+CgkJPFJlZ3Jlc3Npb25UYWJsZSBpbnRlcmNlcHQ9IjAuMCI+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoOC4wIGlmIFhbMF0gJmx0OyAyLjAgZWxzZSAoMjUuMTcgaWYgWFswXSAmZ3Q7PSAyLjAgYW5kIFhbMF0gJmx0OyAzLjAgZWxzZSAoNDAuNTcgaWYgWFswXSAmZ3Q7PSAzLjAgZWxzZSAtOC44OSkpKSIgY29lZmZpY2llbnQ9IjEuMCIvPgoJCQk8TnVtZXJpY1ByZWRpY3RvciBuYW1lPSJldmFsKDU0LjYyIGlmIFhbMF0gJmx0OyAzLjAgZWxzZSAoMzQuMjIgaWYgWFswXSAmZ3Q7PSAzLjAgYW5kIFhbMF0gJmx0OyA0LjAgZWxzZSAoMjYuODggaWYgWFswXSAmZ3Q7PSA0LjAgZWxzZSA2NC40NCkpKSIgY29lZmZpY2llbnQ9IjEuMCIvPgoJCQk8TnVtZXJpY1ByZWRpY3RvciBuYW1lPSJldmFsKDY0LjYgaWYgWFswXSAmbHQ7IDAuMzY4NDIwODU4NzI1ODYzOCBlbHNlICg0MS43NyBpZiBYWzBdICZndDs9IDAuMzY4NDIwODU4NzI1ODYzOCBhbmQgWFswXSAmbHQ7IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlICgyMS41NiBpZiBYWzBdICZndDs9IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlIDEzLjM3KSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoMzQuMDQgaWYgWFswXSAmbHQ7IDEuMCBlbHNlICgzLjUgaWYgWFswXSAmZ3Q7PSAxLjAgZWxzZSA1NC4wMSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoNTAuNDQgaWYgWFswXSAmbHQ7IDAuMTE5OTk5OTk4NCBlbHNlICg0MS42NyBpZiBYWzBdICZndDs9IDAuMTE5OTk5OTk4NCBhbmQgWFswXSAmbHQ7IDAuMjQ5OTk5OTc5MTY2NjY4NCBlbHNlICgyOC42NiBpZiBYWzBdICZndDs9IDAuMjQ5OTk5OTc5MTY2NjY4NCBhbmQgWFswXSAmbHQ7IDAuNDk5OTk5ODc1MDAwMDMxMiBlbHNlICgtNC45MiBpZiBYWzBdICZndDs9IDAuNDk5OTk5ODc1MDAwMDMxMiBlbHNlIDYxLjg2KSkpKSIgY29lZmZpY2llbnQ9IjEuMCIvPgoJCQk8TnVtZXJpY1ByZWRpY3RvciBuYW1lPSJldmFsKC0zLjAyIGlmIFhbMF0gJmx0OyAxMzE4OC4wIGVsc2UgKDI0LjcyIGlmIFhbMF0gJmd0Oz0gMTMxODguMCBhbmQgWFswXSAmbHQ7IDE1NzYwNy4wIGVsc2UgKDMyLjkxIGlmIFhbMF0gJmd0Oz0gMTU3NjA3LjAgYW5kIFhbMF0gJmx0OyAzODAwMDAuMCBlbHNlICg3MS42NCBpZiBYWzBdICZndDs9IDM4MDAwMC4wIGVsc2UgNTkuMjUpKSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoMTQuODcgaWYgWFswXSAmbHQ7IDM2ODUuMCBlbHNlICgyMi4xOCBpZiBYWzBdICZndDs9IDM2ODUuMCBhbmQgWFswXSAmbHQ7IDk5NzAuMCBlbHNlICgzMC40OCBpZiBYWzBdICZndDs9IDk5NzAuMCBhbmQgWFswXSAmbHQ7IDIyNTAwLjAgZWxzZSAoNDMuMDQgaWYgWFswXSAmZ3Q7PSAyMjUwMC4wIGVsc2UgMzguMDgpKSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoMzQuMTQgaWYgWFswXSAmbHQ7IDEuMCBlbHNlICgxMS40MSBpZiBYWzBdICZndDs9IDEuMCBlbHNlIDQ2LjI5KSkiIGNvZWZmaWNpZW50PSIxLjAiLz4KCQkJPE51bWVyaWNQcmVkaWN0b3IgbmFtZT0iZXZhbCgzNi45OSBpZiBYWzBdICZsdDsgMS4wIGVsc2UgLTUuMDcpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJPC9SZWdyZXNzaW9uVGFibGU+Cgk8L1JlZ3Jlc3Npb25Nb2RlbD4KPC9QTU1MPgo=','{"summary": "Regression", "functionName": "regression", "algorithmName": "sklearn.linear_model._base.LinearRegression"}',NULL,NULL,'2026-07-04 03:16:37'
WHERE NOT EXISTS (SELECT 1 FROM rule_model_version existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`version` <=> 2);
SET @example_model_version_2 = (SELECT existing.id FROM rule_model_version existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`version` <=> 2);

-- rule_model_version: 3
INSERT INTO rule_model_version (`model_id`,`version`,`model_content`,`model_config`,`change_log`,`publish_by`,`publish_time`)
SELECT @example_model_1,3,'PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9InllcyI/Pgo8UE1NTCB4bWxucz0iaHR0cDovL3d3dy5kbWcub3JnL1BNTUwtNF80IiB4bWxuczpkYXRhPSJodHRwOi8vanBtbWwub3JnL2pwbW1sLW1vZGVsL0lubGluZVRhYmxlIiB2ZXJzaW9uPSI0LjQiPgoJPEhlYWRlcj4KCQk8QXBwbGljYXRpb24gbmFtZT0iU2tMZWFybjJQTU1MIHBhY2thZ2UiIHZlcnNpb249IjAuOTAuNCIvPgoJCTxUaW1lc3RhbXA+MjAyMy0xMC0wOFQwOTo1ODoxMFo8L1RpbWVzdGFtcD4KCTwvSGVhZGVyPgoJPE1pbmluZ0J1aWxkVGFzaz4KCQk8RXh0ZW5zaW9uIG5hbWU9InJlcHIiPlBNTUxQaXBlbGluZShzdGVwcz1bKCdwcmVwcm9jZXNzaW5nJywgRGF0YUZyYW1lTWFwcGVyKGRmX291dD1UcnVlLCBkcm9wX2NvbHM9W10sCiAgICAgICAgICAgICAgICBmZWF0dXJlcz1bKFsnSFlCQVNFX1gxMTUnXSwKICAgICAgICAgICAgICAgICAgICAgICAgICAgRXhwcmVzc2lvblRyYW5zZm9ybWVyKGV4cHI9JzguMCBpZiBYWzBdICZsdDsgMi4wIGVsc2UgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnKDI1LjE3IGlmIFhbMF0gJmd0Oz0gMi4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2FuZCBYWzBdICZsdDsgMy4wIGVsc2UgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnKDQwLjU3IGlmIFhbMF0gJmd0Oz0gMy4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2Vsc2UgLTguODkpKScpKSwKICAgICAgICAgICAgICAgICAgICAgICAgICAoWydIWUJBU0VfWDE0MiddLAogICAgICAgICAgICAgICAgICAgICAgICAgICBFeHByZXNzaW9uVHJhbnNmb3JtZXIoZXhwcj0nNTQuNjIgaWYgWFswXSAmbHQ7IDMuMCAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdlbHNlICgzNC4yMiBpZiBYWzBdICZndDs9ICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJzMuMCBhbmQgWFswXSAmbHQ7IDQuMCBlbHNlICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJygyNi44OCBpZiBYWzBdICZndDs9IDQuMCAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdlbHNlIDY0LjQ0KSknKSksCiAgICAgICAgICAgICAgICAgICAgICAgICAgKFsnSFlCQVNFX1gyNDUnXSwKICAgICAgICAgICAgICAgICAgICAgICAgICAgRXhwcmVzc2lvblRyLi4uCiAgICAgICAgICAgICAgICAgICAgICAgICAgKFsnSFlES19YMjc2MSddLAogICAgICAgICAgICAgICAgICAgICAgICAgICBFeHByZXNzaW9uVHJhbnNmb3JtZXIoZXhwcj0nMTQuODcgaWYgWFswXSAmbHQ7IDM2ODUuMCAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdlbHNlICgyMi4xOCBpZiBYWzBdICZndDs9ICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJzM2ODUuMCBhbmQgWFswXSAmbHQ7ICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJzk5NzAuMCBlbHNlICgzMC40OCBpZiAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdYWzBdICZndDs9IDk5NzAuMCBhbmQgWFswXSAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICcmbHQ7IDIyNTAwLjAgZWxzZSAoNDMuMDQgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnaWYgWFswXSAmZ3Q7PSAyMjUwMC4wIGVsc2UgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnMzguMDgpKSknKSksCiAgICAgICAgICAgICAgICAgICAgICAgICAgKFsnSFlES19YNzYwJ10sCiAgICAgICAgICAgICAgICAgICAgICAgICAgIEV4cHJlc3Npb25UcmFuc2Zvcm1lcihleHByPSczNC4xNCBpZiBYWzBdICZsdDsgMS4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2Vsc2UgKDExLjQxIGlmIFhbMF0gJmd0Oz0gJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnMS4wIGVsc2UgNDYuMjkpJykpLAogICAgICAgICAgICAgICAgICAgICAgICAgIChbJ0hZSkhfWDU0J10sCiAgICAgICAgICAgICAgICAgICAgICAgICAgIEV4cHJlc3Npb25UcmFuc2Zvcm1lcihleHByPSczNi45OSBpZiBYWzBdICZsdDsgMS4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2Vsc2UgLTUuMDcnKSldKSksCiAgICAgICAoJ3Njb3JlY2FyZCcsIExpbmVhclJlZ3Jlc3Npb24oKSldKTwvRXh0ZW5zaW9uPgoJPC9NaW5pbmdCdWlsZFRhc2s+Cgk8RGF0YURpY3Rpb25hcnk+CgkJPERhdGFGaWVsZCBuYW1lPSJzY29yZSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUJBU0VfWDExNSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUJBU0VfWDE0MiIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUJBU0VfWDI0NSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWURLX1gxMjc2IiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiLz4KCQk8RGF0YUZpZWxkIG5hbWU9IkhZREtfWDEzNTkiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSIvPgoJCTxEYXRhRmllbGQgbmFtZT0iSFlES19YMjYyMyIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWURLX1gyNzYxIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiLz4KCQk8RGF0YUZpZWxkIG5hbWU9IkhZREtfWDc2MCIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUpIX1g1NCIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+Cgk8L0RhdGFEaWN0aW9uYXJ5PgoJPFJlZ3Jlc3Npb25Nb2RlbCBmdW5jdGlvbk5hbWU9InJlZ3Jlc3Npb24iIGFsZ29yaXRobU5hbWU9InNrbGVhcm4ubGluZWFyX21vZGVsLl9iYXNlLkxpbmVhclJlZ3Jlc3Npb24iPgoJCTxNaW5pbmdTY2hlbWE+CgkJCTxNaW5pbmdGaWVsZCBuYW1lPSJzY29yZSIgdXNhZ2VUeXBlPSJ0YXJnZXQiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZQkFTRV9YMTE1Ii8+CgkJCTxNaW5pbmdGaWVsZCBuYW1lPSJIWUJBU0VfWDE0MiIvPgoJCQk8TWluaW5nRmllbGQgbmFtZT0iSFlCQVNFX1gyNDUiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDEyNzYiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDEzNTkiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDI2MjMiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDI3NjEiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDc2MCIvPgoJCQk8TWluaW5nRmllbGQgbmFtZT0iSFlKSF9YNTQiLz4KCQk8L01pbmluZ1NjaGVtYT4KCQk8TG9jYWxUcmFuc2Zvcm1hdGlvbnM+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCg4LjAgaWYgWFswXSAmbHQ7IDIuMCBlbHNlICgyNS4xNyBpZiBYWzBdICZndDs9IDIuMCBhbmQgWFswXSAmbHQ7IDMuMCBlbHNlICg0MC41NyBpZiBYWzBdICZndDs9IDMuMCBlbHNlIC04Ljg5KSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gxMTUiLz4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIuMDwvQ29uc3RhbnQ+CgkJCQkJPC9BcHBseT4KCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+OC4wPC9Db25zdGFudD4KCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJhbmQiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDExNSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4yLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gxMTUiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+My4wPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4yNS4xNzwvQ29uc3RhbnQ+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDExNSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj40MC41NzwvQ29uc3RhbnQ+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+LTguODk8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCTwvQXBwbHk+CgkJCQk8L0FwcGx5PgoJCQk8L0Rlcml2ZWRGaWVsZD4KCQkJPERlcml2ZWRGaWVsZCBuYW1lPSJldmFsKDU0LjYyIGlmIFhbMF0gJmx0OyAzLjAgZWxzZSAoMzQuMjIgaWYgWFswXSAmZ3Q7PSAzLjAgYW5kIFhbMF0gJmx0OyA0LjAgZWxzZSAoMjYuODggaWYgWFswXSAmZ3Q7PSA0LjAgZWxzZSA2NC40NCkpKSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIj4KCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZQkFTRV9YMTQyIi8+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zLjA8L0NvbnN0YW50PgoJCQkJCTwvQXBwbHk+CgkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjU0LjYyPC9Db25zdGFudD4KCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJhbmQiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDE0MiIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gxNDIiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NC4wPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNC4yMjwvQ29uc3RhbnQ+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDE0MiIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj40LjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4yNi44ODwvQ29uc3RhbnQ+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NjQuNDQ8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCTwvQXBwbHk+CgkJCQk8L0FwcGx5PgoJCQk8L0Rlcml2ZWRGaWVsZD4KCQkJPERlcml2ZWRGaWVsZCBuYW1lPSJldmFsKDY0LjYgaWYgWFswXSAmbHQ7IDAuMzY4NDIwODU4NzI1ODYzOCBlbHNlICg0MS43NyBpZiBYWzBdICZndDs9IDAuMzY4NDIwODU4NzI1ODYzOCBhbmQgWFswXSAmbHQ7IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlICgyMS41NiBpZiBYWzBdICZndDs9IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlIDEzLjM3KSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gyNDUiLz4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjAuMzY4NDIwODU4NzI1ODYzODwvQ29uc3RhbnQ+CgkJCQkJPC9BcHBseT4KCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NjQuNjwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gyNDUiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC4zNjg0MjA4NTg3MjU4NjM4PC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZQkFTRV9YMjQ1Ii8+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjAuNzA1ODgxOTM3NzE2NTA3MjwvQ29uc3RhbnQ+CgkJCQkJCQk8L0FwcGx5PgoJCQkJCQk8L0FwcGx5PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NDEuNzc8L0NvbnN0YW50PgoJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gyNDUiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC43MDU4ODE5Mzc3MTY1MDcyPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MjEuNTY8L0NvbnN0YW50PgoJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjEzLjM3PC9Db25zdGFudD4KCQkJCQkJPC9BcHBseT4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCgzNC4wNCBpZiBYWzBdICZsdDsgMS4wIGVsc2UgKDMuNSBpZiBYWzBdICZndDs9IDEuMCBlbHNlIDU0LjAxKSkiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWURLX1gxMjc2Ii8+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xLjA8L0NvbnN0YW50PgoJCQkJCTwvQXBwbHk+CgkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjM0LjA0PC9Db25zdGFudD4KCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDEyNzYiLz4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xLjA8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+My41PC9Db25zdGFudD4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjU0LjAxPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCg1MC40NCBpZiBYWzBdICZsdDsgMC4xMTk5OTk5OTg0IGVsc2UgKDQxLjY3IGlmIFhbMF0gJmd0Oz0gMC4xMTk5OTk5OTg0IGFuZCBYWzBdICZsdDsgMC4yNDk5OTk5NzkxNjY2Njg0IGVsc2UgKDI4LjY2IGlmIFhbMF0gJmd0Oz0gMC4yNDk5OTk5NzkxNjY2Njg0IGFuZCBYWzBdICZsdDsgMC40OTk5OTk4NzUwMDAwMzEyIGVsc2UgKC00LjkyIGlmIFhbMF0gJmd0Oz0gMC40OTk5OTk4NzUwMDAwMzEyIGVsc2UgNjEuODYpKSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC4xMTk5OTk5OTg0PC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj41MC40NDwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4wLjExOTk5OTk5ODQ8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4wLjI0OTk5OTk3OTE2NjY2ODQ8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjQxLjY3PC9Db25zdGFudD4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImFuZCI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC4yNDk5OTk5NzkxNjY2Njg0PC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDEzNTkiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjAuNDk5OTk5ODc1MDAwMDMxMjwvQ29uc3RhbnQ+CgkJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MjguNjY8L0NvbnN0YW50PgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC40OTk5OTk4NzUwMDAwMzEyPC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4tNC45MjwvQ29uc3RhbnQ+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjYxLjg2PC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJPC9BcHBseT4KCQkJCTwvQXBwbHk+CgkJCTwvRGVyaXZlZEZpZWxkPgoJCQk8RGVyaXZlZEZpZWxkIG5hbWU9ImV2YWwoLTMuMDIgaWYgWFswXSAmbHQ7IDEzMTg4LjAgZWxzZSAoMjQuNzIgaWYgWFswXSAmZ3Q7PSAxMzE4OC4wIGFuZCBYWzBdICZsdDsgMTU3NjA3LjAgZWxzZSAoMzIuOTEgaWYgWFswXSAmZ3Q7PSAxNTc2MDcuMCBhbmQgWFswXSAmbHQ7IDM4MDAwMC4wIGVsc2UgKDcxLjY0IGlmIFhbMF0gJmd0Oz0gMzgwMDAwLjAgZWxzZSA1OS4yNSkpKSkiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWURLX1gyNjIzIi8+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xMzE4OC4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4tMy4wMjwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjYyMyIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xMzE4OC4wPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI2MjMiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MTU3NjA3LjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjI0LjcyPC9Db25zdGFudD4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImFuZCI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjYyMyIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MTU3NjA3LjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjYyMyIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MzgwMDAwLjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjMyLjkxPC9Db25zdGFudD4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI2MjMiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjM4MDAwMC4wPC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj43MS42NDwvQ29uc3RhbnQ+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjU5LjI1PC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJPC9BcHBseT4KCQkJCTwvQXBwbHk+CgkJCTwvRGVyaXZlZEZpZWxkPgoJCQk8RGVyaXZlZEZpZWxkIG5hbWU9ImV2YWwoMTQuODcgaWYgWFswXSAmbHQ7IDM2ODUuMCBlbHNlICgyMi4xOCBpZiBYWzBdICZndDs9IDM2ODUuMCBhbmQgWFswXSAmbHQ7IDk5NzAuMCBlbHNlICgzMC40OCBpZiBYWzBdICZndDs9IDk5NzAuMCBhbmQgWFswXSAmbHQ7IDIyNTAwLjAgZWxzZSAoNDMuMDQgaWYgWFswXSAmZ3Q7PSAyMjUwMC4wIGVsc2UgMzguMDgpKSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MzY4NS4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xNC44NzwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNjg1LjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj45OTcwLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIyLjE4PC9Db25zdGFudD4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImFuZCI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+OTk3MC4wPC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI3NjEiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIyNTAwLjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjMwLjQ4PC9Db25zdGFudD4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI3NjEiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIyNTAwLjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjQzLjA0PC9Db25zdGFudD4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MzguMDg8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCgzNC4xNCBpZiBYWzBdICZsdDsgMS4wIGVsc2UgKDExLjQxIGlmIFhbMF0gJmd0Oz0gMS4wIGVsc2UgNDYuMjkpKSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIj4KCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDc2MCIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MS4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNC4xNDwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWURLX1g3NjAiLz4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xLjA8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MTEuNDE8L0NvbnN0YW50PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NDYuMjk8L0NvbnN0YW50PgoJCQkJCTwvQXBwbHk+CgkJCQk8L0FwcGx5PgoJCQk8L0Rlcml2ZWRGaWVsZD4KCQkJPERlcml2ZWRGaWVsZCBuYW1lPSJldmFsKDM2Ljk5IGlmIFhbMF0gJmx0OyAxLjAgZWxzZSAtNS4wNykiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUpIX1g1NCIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MS4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNi45OTwvQ29uc3RhbnQ+CgkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPi01LjA3PC9Db25zdGFudD4KCQkJCTwvQXBwbHk+CgkJCTwvRGVyaXZlZEZpZWxkPgoJCTwvTG9jYWxUcmFuc2Zvcm1hdGlvbnM+CgkJPFJlZ3Jlc3Npb25UYWJsZSBpbnRlcmNlcHQ9IjAuMCI+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoOC4wIGlmIFhbMF0gJmx0OyAyLjAgZWxzZSAoMjUuMTcgaWYgWFswXSAmZ3Q7PSAyLjAgYW5kIFhbMF0gJmx0OyAzLjAgZWxzZSAoNDAuNTcgaWYgWFswXSAmZ3Q7PSAzLjAgZWxzZSAtOC44OSkpKSIgY29lZmZpY2llbnQ9IjEuMCIvPgoJCQk8TnVtZXJpY1ByZWRpY3RvciBuYW1lPSJldmFsKDU0LjYyIGlmIFhbMF0gJmx0OyAzLjAgZWxzZSAoMzQuMjIgaWYgWFswXSAmZ3Q7PSAzLjAgYW5kIFhbMF0gJmx0OyA0LjAgZWxzZSAoMjYuODggaWYgWFswXSAmZ3Q7PSA0LjAgZWxzZSA2NC40NCkpKSIgY29lZmZpY2llbnQ9IjEuMCIvPgoJCQk8TnVtZXJpY1ByZWRpY3RvciBuYW1lPSJldmFsKDY0LjYgaWYgWFswXSAmbHQ7IDAuMzY4NDIwODU4NzI1ODYzOCBlbHNlICg0MS43NyBpZiBYWzBdICZndDs9IDAuMzY4NDIwODU4NzI1ODYzOCBhbmQgWFswXSAmbHQ7IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlICgyMS41NiBpZiBYWzBdICZndDs9IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlIDEzLjM3KSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoMzQuMDQgaWYgWFswXSAmbHQ7IDEuMCBlbHNlICgzLjUgaWYgWFswXSAmZ3Q7PSAxLjAgZWxzZSA1NC4wMSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoNTAuNDQgaWYgWFswXSAmbHQ7IDAuMTE5OTk5OTk4NCBlbHNlICg0MS42NyBpZiBYWzBdICZndDs9IDAuMTE5OTk5OTk4NCBhbmQgWFswXSAmbHQ7IDAuMjQ5OTk5OTc5MTY2NjY4NCBlbHNlICgyOC42NiBpZiBYWzBdICZndDs9IDAuMjQ5OTk5OTc5MTY2NjY4NCBhbmQgWFswXSAmbHQ7IDAuNDk5OTk5ODc1MDAwMDMxMiBlbHNlICgtNC45MiBpZiBYWzBdICZndDs9IDAuNDk5OTk5ODc1MDAwMDMxMiBlbHNlIDYxLjg2KSkpKSIgY29lZmZpY2llbnQ9IjEuMCIvPgoJCQk8TnVtZXJpY1ByZWRpY3RvciBuYW1lPSJldmFsKC0zLjAyIGlmIFhbMF0gJmx0OyAxMzE4OC4wIGVsc2UgKDI0LjcyIGlmIFhbMF0gJmd0Oz0gMTMxODguMCBhbmQgWFswXSAmbHQ7IDE1NzYwNy4wIGVsc2UgKDMyLjkxIGlmIFhbMF0gJmd0Oz0gMTU3NjA3LjAgYW5kIFhbMF0gJmx0OyAzODAwMDAuMCBlbHNlICg3MS42NCBpZiBYWzBdICZndDs9IDM4MDAwMC4wIGVsc2UgNTkuMjUpKSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoMTQuODcgaWYgWFswXSAmbHQ7IDM2ODUuMCBlbHNlICgyMi4xOCBpZiBYWzBdICZndDs9IDM2ODUuMCBhbmQgWFswXSAmbHQ7IDk5NzAuMCBlbHNlICgzMC40OCBpZiBYWzBdICZndDs9IDk5NzAuMCBhbmQgWFswXSAmbHQ7IDIyNTAwLjAgZWxzZSAoNDMuMDQgaWYgWFswXSAmZ3Q7PSAyMjUwMC4wIGVsc2UgMzguMDgpKSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoMzQuMTQgaWYgWFswXSAmbHQ7IDEuMCBlbHNlICgxMS40MSBpZiBYWzBdICZndDs9IDEuMCBlbHNlIDQ2LjI5KSkiIGNvZWZmaWNpZW50PSIxLjAiLz4KCQkJPE51bWVyaWNQcmVkaWN0b3IgbmFtZT0iZXZhbCgzNi45OSBpZiBYWzBdICZsdDsgMS4wIGVsc2UgLTUuMDcpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJPC9SZWdyZXNzaW9uVGFibGU+Cgk8L1JlZ3Jlc3Npb25Nb2RlbD4KPC9QTU1MPgo=','{"summary": "Regression", "functionName": "regression", "algorithmName": "sklearn.linear_model._base.LinearRegression"}',NULL,NULL,'2026-07-04 03:21:52'
WHERE NOT EXISTS (SELECT 1 FROM rule_model_version existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`version` <=> 3);
SET @example_model_version_3 = (SELECT existing.id FROM rule_model_version existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`version` <=> 3);

-- rule_model_version: 4
INSERT INTO rule_model_version (`model_id`,`version`,`model_content`,`model_config`,`change_log`,`publish_by`,`publish_time`)
SELECT @example_model_1,4,'PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9InllcyI/Pgo8UE1NTCB4bWxucz0iaHR0cDovL3d3dy5kbWcub3JnL1BNTUwtNF80IiB4bWxuczpkYXRhPSJodHRwOi8vanBtbWwub3JnL2pwbW1sLW1vZGVsL0lubGluZVRhYmxlIiB2ZXJzaW9uPSI0LjQiPgoJPEhlYWRlcj4KCQk8QXBwbGljYXRpb24gbmFtZT0iU2tMZWFybjJQTU1MIHBhY2thZ2UiIHZlcnNpb249IjAuOTAuNCIvPgoJCTxUaW1lc3RhbXA+MjAyMy0xMC0wOFQwOTo1ODoxMFo8L1RpbWVzdGFtcD4KCTwvSGVhZGVyPgoJPE1pbmluZ0J1aWxkVGFzaz4KCQk8RXh0ZW5zaW9uIG5hbWU9InJlcHIiPlBNTUxQaXBlbGluZShzdGVwcz1bKCdwcmVwcm9jZXNzaW5nJywgRGF0YUZyYW1lTWFwcGVyKGRmX291dD1UcnVlLCBkcm9wX2NvbHM9W10sCiAgICAgICAgICAgICAgICBmZWF0dXJlcz1bKFsnSFlCQVNFX1gxMTUnXSwKICAgICAgICAgICAgICAgICAgICAgICAgICAgRXhwcmVzc2lvblRyYW5zZm9ybWVyKGV4cHI9JzguMCBpZiBYWzBdICZsdDsgMi4wIGVsc2UgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnKDI1LjE3IGlmIFhbMF0gJmd0Oz0gMi4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2FuZCBYWzBdICZsdDsgMy4wIGVsc2UgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnKDQwLjU3IGlmIFhbMF0gJmd0Oz0gMy4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2Vsc2UgLTguODkpKScpKSwKICAgICAgICAgICAgICAgICAgICAgICAgICAoWydIWUJBU0VfWDE0MiddLAogICAgICAgICAgICAgICAgICAgICAgICAgICBFeHByZXNzaW9uVHJhbnNmb3JtZXIoZXhwcj0nNTQuNjIgaWYgWFswXSAmbHQ7IDMuMCAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdlbHNlICgzNC4yMiBpZiBYWzBdICZndDs9ICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJzMuMCBhbmQgWFswXSAmbHQ7IDQuMCBlbHNlICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJygyNi44OCBpZiBYWzBdICZndDs9IDQuMCAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdlbHNlIDY0LjQ0KSknKSksCiAgICAgICAgICAgICAgICAgICAgICAgICAgKFsnSFlCQVNFX1gyNDUnXSwKICAgICAgICAgICAgICAgICAgICAgICAgICAgRXhwcmVzc2lvblRyLi4uCiAgICAgICAgICAgICAgICAgICAgICAgICAgKFsnSFlES19YMjc2MSddLAogICAgICAgICAgICAgICAgICAgICAgICAgICBFeHByZXNzaW9uVHJhbnNmb3JtZXIoZXhwcj0nMTQuODcgaWYgWFswXSAmbHQ7IDM2ODUuMCAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdlbHNlICgyMi4xOCBpZiBYWzBdICZndDs9ICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJzM2ODUuMCBhbmQgWFswXSAmbHQ7ICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJzk5NzAuMCBlbHNlICgzMC40OCBpZiAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICdYWzBdICZndDs9IDk5NzAuMCBhbmQgWFswXSAnCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICcmbHQ7IDIyNTAwLjAgZWxzZSAoNDMuMDQgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnaWYgWFswXSAmZ3Q7PSAyMjUwMC4wIGVsc2UgJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnMzguMDgpKSknKSksCiAgICAgICAgICAgICAgICAgICAgICAgICAgKFsnSFlES19YNzYwJ10sCiAgICAgICAgICAgICAgICAgICAgICAgICAgIEV4cHJlc3Npb25UcmFuc2Zvcm1lcihleHByPSczNC4xNCBpZiBYWzBdICZsdDsgMS4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2Vsc2UgKDExLjQxIGlmIFhbMF0gJmd0Oz0gJwogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAnMS4wIGVsc2UgNDYuMjkpJykpLAogICAgICAgICAgICAgICAgICAgICAgICAgIChbJ0hZSkhfWDU0J10sCiAgICAgICAgICAgICAgICAgICAgICAgICAgIEV4cHJlc3Npb25UcmFuc2Zvcm1lcihleHByPSczNi45OSBpZiBYWzBdICZsdDsgMS4wICcKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgJ2Vsc2UgLTUuMDcnKSldKSksCiAgICAgICAoJ3Njb3JlY2FyZCcsIExpbmVhclJlZ3Jlc3Npb24oKSldKTwvRXh0ZW5zaW9uPgoJPC9NaW5pbmdCdWlsZFRhc2s+Cgk8RGF0YURpY3Rpb25hcnk+CgkJPERhdGFGaWVsZCBuYW1lPSJzY29yZSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUJBU0VfWDExNSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUJBU0VfWDE0MiIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUJBU0VfWDI0NSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWURLX1gxMjc2IiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiLz4KCQk8RGF0YUZpZWxkIG5hbWU9IkhZREtfWDEzNTkiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSIvPgoJCTxEYXRhRmllbGQgbmFtZT0iSFlES19YMjYyMyIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWURLX1gyNzYxIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiLz4KCQk8RGF0YUZpZWxkIG5hbWU9IkhZREtfWDc2MCIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+CgkJPERhdGFGaWVsZCBuYW1lPSJIWUpIX1g1NCIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIi8+Cgk8L0RhdGFEaWN0aW9uYXJ5PgoJPFJlZ3Jlc3Npb25Nb2RlbCBmdW5jdGlvbk5hbWU9InJlZ3Jlc3Npb24iIGFsZ29yaXRobU5hbWU9InNrbGVhcm4ubGluZWFyX21vZGVsLl9iYXNlLkxpbmVhclJlZ3Jlc3Npb24iPgoJCTxNaW5pbmdTY2hlbWE+CgkJCTxNaW5pbmdGaWVsZCBuYW1lPSJzY29yZSIgdXNhZ2VUeXBlPSJ0YXJnZXQiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZQkFTRV9YMTE1Ii8+CgkJCTxNaW5pbmdGaWVsZCBuYW1lPSJIWUJBU0VfWDE0MiIvPgoJCQk8TWluaW5nRmllbGQgbmFtZT0iSFlCQVNFX1gyNDUiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDEyNzYiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDEzNTkiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDI2MjMiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDI3NjEiLz4KCQkJPE1pbmluZ0ZpZWxkIG5hbWU9IkhZREtfWDc2MCIvPgoJCQk8TWluaW5nRmllbGQgbmFtZT0iSFlKSF9YNTQiLz4KCQk8L01pbmluZ1NjaGVtYT4KCQk8TG9jYWxUcmFuc2Zvcm1hdGlvbnM+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCg4LjAgaWYgWFswXSAmbHQ7IDIuMCBlbHNlICgyNS4xNyBpZiBYWzBdICZndDs9IDIuMCBhbmQgWFswXSAmbHQ7IDMuMCBlbHNlICg0MC41NyBpZiBYWzBdICZndDs9IDMuMCBlbHNlIC04Ljg5KSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gxMTUiLz4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIuMDwvQ29uc3RhbnQ+CgkJCQkJPC9BcHBseT4KCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+OC4wPC9Db25zdGFudD4KCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJhbmQiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDExNSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4yLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gxMTUiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+My4wPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4yNS4xNzwvQ29uc3RhbnQ+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDExNSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj40MC41NzwvQ29uc3RhbnQ+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+LTguODk8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCTwvQXBwbHk+CgkJCQk8L0FwcGx5PgoJCQk8L0Rlcml2ZWRGaWVsZD4KCQkJPERlcml2ZWRGaWVsZCBuYW1lPSJldmFsKDU0LjYyIGlmIFhbMF0gJmx0OyAzLjAgZWxzZSAoMzQuMjIgaWYgWFswXSAmZ3Q7PSAzLjAgYW5kIFhbMF0gJmx0OyA0LjAgZWxzZSAoMjYuODggaWYgWFswXSAmZ3Q7PSA0LjAgZWxzZSA2NC40NCkpKSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIj4KCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZQkFTRV9YMTQyIi8+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zLjA8L0NvbnN0YW50PgoJCQkJCTwvQXBwbHk+CgkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjU0LjYyPC9Db25zdGFudD4KCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJhbmQiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDE0MiIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gxNDIiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NC4wPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNC4yMjwvQ29uc3RhbnQ+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUJBU0VfWDE0MiIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj40LjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4yNi44ODwvQ29uc3RhbnQ+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NjQuNDQ8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCTwvQXBwbHk+CgkJCQk8L0FwcGx5PgoJCQk8L0Rlcml2ZWRGaWVsZD4KCQkJPERlcml2ZWRGaWVsZCBuYW1lPSJldmFsKDY0LjYgaWYgWFswXSAmbHQ7IDAuMzY4NDIwODU4NzI1ODYzOCBlbHNlICg0MS43NyBpZiBYWzBdICZndDs9IDAuMzY4NDIwODU4NzI1ODYzOCBhbmQgWFswXSAmbHQ7IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlICgyMS41NiBpZiBYWzBdICZndDs9IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlIDEzLjM3KSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gyNDUiLz4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjAuMzY4NDIwODU4NzI1ODYzODwvQ29uc3RhbnQ+CgkJCQkJPC9BcHBseT4KCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NjQuNjwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gyNDUiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC4zNjg0MjA4NTg3MjU4NjM4PC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZQkFTRV9YMjQ1Ii8+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjAuNzA1ODgxOTM3NzE2NTA3MjwvQ29uc3RhbnQ+CgkJCQkJCQk8L0FwcGx5PgoJCQkJCQk8L0FwcGx5PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NDEuNzc8L0NvbnN0YW50PgoJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlCQVNFX1gyNDUiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC43MDU4ODE5Mzc3MTY1MDcyPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MjEuNTY8L0NvbnN0YW50PgoJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjEzLjM3PC9Db25zdGFudD4KCQkJCQkJPC9BcHBseT4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCgzNC4wNCBpZiBYWzBdICZsdDsgMS4wIGVsc2UgKDMuNSBpZiBYWzBdICZndDs9IDEuMCBlbHNlIDU0LjAxKSkiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWURLX1gxMjc2Ii8+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xLjA8L0NvbnN0YW50PgoJCQkJCTwvQXBwbHk+CgkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjM0LjA0PC9Db25zdGFudD4KCQkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDEyNzYiLz4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xLjA8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+My41PC9Db25zdGFudD4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjU0LjAxPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCg1MC40NCBpZiBYWzBdICZsdDsgMC4xMTk5OTk5OTg0IGVsc2UgKDQxLjY3IGlmIFhbMF0gJmd0Oz0gMC4xMTk5OTk5OTg0IGFuZCBYWzBdICZsdDsgMC4yNDk5OTk5NzkxNjY2Njg0IGVsc2UgKDI4LjY2IGlmIFhbMF0gJmd0Oz0gMC4yNDk5OTk5NzkxNjY2Njg0IGFuZCBYWzBdICZsdDsgMC40OTk5OTk4NzUwMDAwMzEyIGVsc2UgKC00LjkyIGlmIFhbMF0gJmd0Oz0gMC40OTk5OTk4NzUwMDAwMzEyIGVsc2UgNjEuODYpKSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC4xMTk5OTk5OTg0PC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj41MC40NDwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4wLjExOTk5OTk5ODQ8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4wLjI0OTk5OTk3OTE2NjY2ODQ8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjQxLjY3PC9Db25zdGFudD4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImFuZCI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC4yNDk5OTk5NzkxNjY2Njg0PC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDEzNTkiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjAuNDk5OTk5ODc1MDAwMDMxMjwvQ29uc3RhbnQ+CgkJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MjguNjY8L0NvbnN0YW50PgoJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMTM1OSIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MC40OTk5OTk4NzUwMDAwMzEyPC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4tNC45MjwvQ29uc3RhbnQ+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjYxLjg2PC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJPC9BcHBseT4KCQkJCTwvQXBwbHk+CgkJCTwvRGVyaXZlZEZpZWxkPgoJCQk8RGVyaXZlZEZpZWxkIG5hbWU9ImV2YWwoLTMuMDIgaWYgWFswXSAmbHQ7IDEzMTg4LjAgZWxzZSAoMjQuNzIgaWYgWFswXSAmZ3Q7PSAxMzE4OC4wIGFuZCBYWzBdICZsdDsgMTU3NjA3LjAgZWxzZSAoMzIuOTEgaWYgWFswXSAmZ3Q7PSAxNTc2MDcuMCBhbmQgWFswXSAmbHQ7IDM4MDAwMC4wIGVsc2UgKDcxLjY0IGlmIFhbMF0gJmd0Oz0gMzgwMDAwLjAgZWxzZSA1OS4yNSkpKSkiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWURLX1gyNjIzIi8+CgkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xMzE4OC4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4tMy4wMjwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjYyMyIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xMzE4OC4wPC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI2MjMiLz4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MTU3NjA3LjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjI0LjcyPC9Db25zdGFudD4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImFuZCI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjYyMyIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MTU3NjA3LjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjYyMyIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MzgwMDAwLjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjMyLjkxPC9Db25zdGFudD4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI2MjMiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjM4MDAwMC4wPC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj43MS42NDwvQ29uc3RhbnQ+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjU5LjI1PC9Db25zdGFudD4KCQkJCQkJCTwvQXBwbHk+CgkJCQkJCTwvQXBwbHk+CgkJCQkJPC9BcHBseT4KCQkJCTwvQXBwbHk+CgkJCTwvRGVyaXZlZEZpZWxkPgoJCQk8RGVyaXZlZEZpZWxkIG5hbWU9ImV2YWwoMTQuODcgaWYgWFswXSAmbHQ7IDM2ODUuMCBlbHNlICgyMi4xOCBpZiBYWzBdICZndDs9IDM2ODUuMCBhbmQgWFswXSAmbHQ7IDk5NzAuMCBlbHNlICgzMC40OCBpZiBYWzBdICZndDs9IDk5NzAuMCBhbmQgWFswXSAmbHQ7IDIyNTAwLjAgZWxzZSAoNDMuMDQgaWYgWFswXSAmZ3Q7PSAyMjUwMC4wIGVsc2UgMzguMDgpKSkpIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiPgoJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJsZXNzVGhhbiI+CgkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MzY4NS4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xNC44NzwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iYW5kIj4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNjg1LjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj45OTcwLjA8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIyLjE4PC9Db25zdGFudD4KCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCQk8QXBwbHkgZnVuY3Rpb249ImFuZCI+CgkJCQkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJncmVhdGVyT3JFcXVhbCI+CgkJCQkJCQkJCTxGaWVsZFJlZiBmaWVsZD0iSFlES19YMjc2MSIvPgoJCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+OTk3MC4wPC9Db25zdGFudD4KCQkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI3NjEiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIyNTAwLjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQk8L0FwcGx5PgoJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjMwLjQ4PC9Db25zdGFudD4KCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDI3NjEiLz4KCQkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIyNTAwLjA8L0NvbnN0YW50PgoJCQkJCQkJCTwvQXBwbHk+CgkJCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjQzLjA0PC9Db25zdGFudD4KCQkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MzguMDg8L0NvbnN0YW50PgoJCQkJCQkJPC9BcHBseT4KCQkJCQkJPC9BcHBseT4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZXZhbCgzNC4xNCBpZiBYWzBdICZsdDsgMS4wIGVsc2UgKDExLjQxIGlmIFhbMF0gJmd0Oz0gMS4wIGVsc2UgNDYuMjkpKSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIj4KCQkJCTxBcHBseSBmdW5jdGlvbj0iaWYiPgoJCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc1RoYW4iPgoJCQkJCQk8RmllbGRSZWYgZmllbGQ9IkhZREtfWDc2MCIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MS4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNC4xNDwvQ29uc3RhbnQ+CgkJCQkJPEFwcGx5IGZ1bmN0aW9uPSJpZiI+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iZ3JlYXRlck9yRXF1YWwiPgoJCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWURLX1g3NjAiLz4KCQkJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4xLjA8L0NvbnN0YW50PgoJCQkJCQk8L0FwcGx5PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MTEuNDE8L0NvbnN0YW50PgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+NDYuMjk8L0NvbnN0YW50PgoJCQkJCTwvQXBwbHk+CgkJCQk8L0FwcGx5PgoJCQk8L0Rlcml2ZWRGaWVsZD4KCQkJPERlcml2ZWRGaWVsZCBuYW1lPSJldmFsKDM2Ljk5IGlmIFhbMF0gJmx0OyAxLjAgZWxzZSAtNS4wNykiIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249ImlmIj4KCQkJCQk8QXBwbHkgZnVuY3Rpb249Imxlc3NUaGFuIj4KCQkJCQkJPEZpZWxkUmVmIGZpZWxkPSJIWUpIX1g1NCIvPgoJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+MS4wPC9Db25zdGFudD4KCQkJCQk8L0FwcGx5PgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4zNi45OTwvQ29uc3RhbnQ+CgkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPi01LjA3PC9Db25zdGFudD4KCQkJCTwvQXBwbHk+CgkJCTwvRGVyaXZlZEZpZWxkPgoJCTwvTG9jYWxUcmFuc2Zvcm1hdGlvbnM+CgkJPFJlZ3Jlc3Npb25UYWJsZSBpbnRlcmNlcHQ9IjAuMCI+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoOC4wIGlmIFhbMF0gJmx0OyAyLjAgZWxzZSAoMjUuMTcgaWYgWFswXSAmZ3Q7PSAyLjAgYW5kIFhbMF0gJmx0OyAzLjAgZWxzZSAoNDAuNTcgaWYgWFswXSAmZ3Q7PSAzLjAgZWxzZSAtOC44OSkpKSIgY29lZmZpY2llbnQ9IjEuMCIvPgoJCQk8TnVtZXJpY1ByZWRpY3RvciBuYW1lPSJldmFsKDU0LjYyIGlmIFhbMF0gJmx0OyAzLjAgZWxzZSAoMzQuMjIgaWYgWFswXSAmZ3Q7PSAzLjAgYW5kIFhbMF0gJmx0OyA0LjAgZWxzZSAoMjYuODggaWYgWFswXSAmZ3Q7PSA0LjAgZWxzZSA2NC40NCkpKSIgY29lZmZpY2llbnQ9IjEuMCIvPgoJCQk8TnVtZXJpY1ByZWRpY3RvciBuYW1lPSJldmFsKDY0LjYgaWYgWFswXSAmbHQ7IDAuMzY4NDIwODU4NzI1ODYzOCBlbHNlICg0MS43NyBpZiBYWzBdICZndDs9IDAuMzY4NDIwODU4NzI1ODYzOCBhbmQgWFswXSAmbHQ7IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlICgyMS41NiBpZiBYWzBdICZndDs9IDAuNzA1ODgxOTM3NzE2NTA3MiBlbHNlIDEzLjM3KSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoMzQuMDQgaWYgWFswXSAmbHQ7IDEuMCBlbHNlICgzLjUgaWYgWFswXSAmZ3Q7PSAxLjAgZWxzZSA1NC4wMSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoNTAuNDQgaWYgWFswXSAmbHQ7IDAuMTE5OTk5OTk4NCBlbHNlICg0MS42NyBpZiBYWzBdICZndDs9IDAuMTE5OTk5OTk4NCBhbmQgWFswXSAmbHQ7IDAuMjQ5OTk5OTc5MTY2NjY4NCBlbHNlICgyOC42NiBpZiBYWzBdICZndDs9IDAuMjQ5OTk5OTc5MTY2NjY4NCBhbmQgWFswXSAmbHQ7IDAuNDk5OTk5ODc1MDAwMDMxMiBlbHNlICgtNC45MiBpZiBYWzBdICZndDs9IDAuNDk5OTk5ODc1MDAwMDMxMiBlbHNlIDYxLjg2KSkpKSIgY29lZmZpY2llbnQ9IjEuMCIvPgoJCQk8TnVtZXJpY1ByZWRpY3RvciBuYW1lPSJldmFsKC0zLjAyIGlmIFhbMF0gJmx0OyAxMzE4OC4wIGVsc2UgKDI0LjcyIGlmIFhbMF0gJmd0Oz0gMTMxODguMCBhbmQgWFswXSAmbHQ7IDE1NzYwNy4wIGVsc2UgKDMyLjkxIGlmIFhbMF0gJmd0Oz0gMTU3NjA3LjAgYW5kIFhbMF0gJmx0OyAzODAwMDAuMCBlbHNlICg3MS42NCBpZiBYWzBdICZndDs9IDM4MDAwMC4wIGVsc2UgNTkuMjUpKSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoMTQuODcgaWYgWFswXSAmbHQ7IDM2ODUuMCBlbHNlICgyMi4xOCBpZiBYWzBdICZndDs9IDM2ODUuMCBhbmQgWFswXSAmbHQ7IDk5NzAuMCBlbHNlICgzMC40OCBpZiBYWzBdICZndDs9IDk5NzAuMCBhbmQgWFswXSAmbHQ7IDIyNTAwLjAgZWxzZSAoNDMuMDQgaWYgWFswXSAmZ3Q7PSAyMjUwMC4wIGVsc2UgMzguMDgpKSkpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJCTxOdW1lcmljUHJlZGljdG9yIG5hbWU9ImV2YWwoMzQuMTQgaWYgWFswXSAmbHQ7IDEuMCBlbHNlICgxMS40MSBpZiBYWzBdICZndDs9IDEuMCBlbHNlIDQ2LjI5KSkiIGNvZWZmaWNpZW50PSIxLjAiLz4KCQkJPE51bWVyaWNQcmVkaWN0b3IgbmFtZT0iZXZhbCgzNi45OSBpZiBYWzBdICZsdDsgMS4wIGVsc2UgLTUuMDcpIiBjb2VmZmljaWVudD0iMS4wIi8+CgkJPC9SZWdyZXNzaW9uVGFibGU+Cgk8L1JlZ3Jlc3Npb25Nb2RlbD4KPC9QTU1MPgo=','{"summary": "Regression", "functionName": "regression", "algorithmName": "sklearn.linear_model._base.LinearRegression"}',NULL,NULL,'2026-07-04 11:18:54'
WHERE NOT EXISTS (SELECT 1 FROM rule_model_version existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`version` <=> 4);
SET @example_model_version_4 = (SELECT existing.id FROM rule_model_version existing WHERE existing.`model_id` <=> @example_model_1 AND existing.`version` <=> 4);

-- rule_published: BDZR
INSERT INTO rule_published (`rule_code`,`definition_id`,`project_code`,`version`,`model_type`,`compiled_script`,`compiled_type`,`model_json`,`status`,`publish_by`,`publish_time`,`offline_time`)
SELECT 'BDZR',@example_definition_2,'BNLP',1,'RULE_SET','_ruleSetHits = [];
_ruleSetHitCount = 0;
_ruleSetMatched = false;
if (!_ruleSetMatched && ((black_hit == 1))) {
    result = 1
    _ruleSetHit = {"ruleCode": "R0001", "ruleName": "规则1", "priority": 1, "order": 1};
    if (_ruleSetHitCount == 0) {
        _ruleSetHits = [_ruleSetHit];
    }
    _ruleSetHitCount = _ruleSetHitCount + 1;
    _ruleSetMatched = true;
}
_ruleSetResult = _ruleSetHits;
_ruleSetResult
','QLEXPRESS',JSON_SET('{"executionMode":"SERIAL","rules":[{"ruleCode":"R0001","ruleName":"规则1","priority":1,"enabled":true,"conditionRoot":{"type":"group","op":"AND","children":[{"type":"leaf","varCode":"black_hit","varLabel":"是否命中黑名单 black_hit","varType":"NUMBER","enumOptions":"","operator":"==","valueKind":"CONST","value":"1","_varId":194,"_refType":"VARIABLE"}]},"actionData":[{"type":"assign","target":"result","value":"1","_targetVarId":141,"_targetRefType":"VARIABLE","_varId":141,"_refType":"VARIABLE"}],"status":1}]}', '$."rules"[0]."conditionRoot"."children"[0]."_varId"', CAST(@example_variable_194 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_targetVarId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_varId"', CAST(@example_variable_141 AS UNSIGNED)),1,NULL,'2026-07-04 03:39:04',NULL
WHERE NOT EXISTS (SELECT 1 FROM rule_published existing WHERE existing.`rule_code` <=> 'BDZR');
SET @example_published_1 = (SELECT existing.id FROM rule_published existing WHERE existing.`rule_code` <=> 'BDZR');

-- rule_published: DBQUERY
INSERT INTO rule_published (`rule_code`,`definition_id`,`project_code`,`version`,`model_type`,`compiled_script`,`compiled_type`,`model_json`,`status`,`publish_by`,`publish_time`,`offline_time`)
SELECT 'DBQUERY',@example_definition_4,'BNLP',2,'RULE_SET','_ruleSetHits = [];
_ruleSetHitCount = 0;
_ruleSetMatched = false;
if (!_ruleSetMatched && ((black_user_count > 0))) {
    result = 1
    _ruleSetHit = {"ruleCode": "R0001", "ruleName": "规则1", "priority": 1, "order": 1};
    if (_ruleSetHitCount == 0) {
        _ruleSetHits = [_ruleSetHit];
    }
    _ruleSetHitCount = _ruleSetHitCount + 1;
    _ruleSetMatched = true;
}
if (!_ruleSetMatched && ((score_f1.score < 250))) {
    result = 1
    _ruleSetHit = {"ruleCode": "R0002", "ruleName": "规则2", "priority": 1, "order": 2};
    if (_ruleSetHitCount == 0) {
        _ruleSetHits = [_ruleSetHit];
    } else if (_ruleSetHitCount == 1) {
        _ruleSetHits = [_ruleSetHits[0], _ruleSetHit];
    }
    _ruleSetHitCount = _ruleSetHitCount + 1;
    _ruleSetMatched = true;
}
_ruleSetResult = _ruleSetHits;
_ruleSetResult
','QLEXPRESS',JSON_SET('{"executionMode":"SERIAL","rules":[{"ruleCode":"R0001","ruleName":"规则1","priority":1,"enabled":true,"conditionRoot":{"type":"group","op":"AND","children":[{"type":"leaf","varCode":"black_user_count","varLabel":"黑名单人数 black_user_count","varType":"NUMBER","enumOptions":"","operator":">","valueKind":"CONST","value":"0","_varId":196,"_refType":"VARIABLE"}]},"actionData":[{"type":"assign","target":"result","value":"1","_targetVarId":141,"_targetRefType":"VARIABLE","_varId":141,"_refType":"VARIABLE"}],"status":1},{"ruleCode":"R0002","ruleName":"规则2","priority":1,"enabled":true,"conditionRoot":{"type":"group","op":"AND","children":[{"type":"leaf","varCode":"score_f1.score","varLabel":"欺诈分F1/评分 score","varType":"DOUBLE","enumOptions":"","operator":"<","valueKind":"CONST","value":"250","_varId":1,"_refType":"MODEL_OUTPUT"}]},"actionData":[{"type":"assign","target":"result","value":"1","_targetVarId":141,"_targetRefType":"VARIABLE","_varId":141,"_refType":"VARIABLE"}],"status":1}]}', '$."rules"[0]."conditionRoot"."children"[0]."_varId"', CAST(@example_variable_196 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_targetVarId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[0]."actionData"[0]."_varId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[1]."conditionRoot"."children"[0]."_varId"', CAST(@example_model_output_field_1 AS UNSIGNED), '$."rules"[1]."actionData"[0]."_targetVarId"', CAST(@example_variable_141 AS UNSIGNED), '$."rules"[1]."actionData"[0]."_varId"', CAST(@example_variable_141 AS UNSIGNED)),1,NULL,'2026-07-04 04:09:35',NULL
WHERE NOT EXISTS (SELECT 1 FROM rule_published existing WHERE existing.`rule_code` <=> 'DBQUERY');
SET @example_published_2 = (SELECT existing.id FROM rule_published existing WHERE existing.`rule_code` <=> 'DBQUERY');

-- rule_variable: credit_id
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','credit_id','授信编号','credit_id','STRING','INPUT',NULL,'','','C0000000001','',0,1,'2026-07-03 22:53:45','2026-07-03 22:53:45'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_id');
SET @example_variable_1 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_id');

-- rule_variable: loan_id
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','loan_id','借款编号','loan_id','STRING','INPUT',NULL,'','','L0000000001','',0,1,'2026-07-03 22:54:50','2026-07-03 22:54:50'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'loan_id');
SET @example_variable_2 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'loan_id');

-- rule_variable: user_id
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','user_id','用户编号','user_id','STRING','INPUT',NULL,'','','U0000000001','',0,1,'2026-07-03 22:56:30','2026-07-03 22:56:30'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'user_id');
SET @example_variable_4 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'user_id');

-- rule_variable: name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','name','姓名','name','STRING','INPUT',NULL,'','','横枢','',0,1,'2026-07-03 22:57:40','2026-07-03 22:57:40'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'name');
SET @example_variable_5 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'name');

-- rule_variable: credit_time
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','credit_time','授信时间','credit_time','DATE','INPUT',NULL,'','','2026-01-04 05:20:00','',0,1,'2026-07-03 23:02:25','2026-07-03 23:02:25'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_time');
SET @example_variable_8 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_time');

-- rule_variable: loan_time
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','loan_time','借款时间','loan_time','DATE','INPUT',NULL,'','','2026-03-01 05:41:31','',0,1,'2026-07-03 23:03:57','2026-07-03 23:03:57'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'loan_time');
SET @example_variable_9 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'loan_time');

-- rule_variable: request_time
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','request_time','请求时间','request_time','DATE','INPUT',NULL,'','','2026-04-01 13:14:52','',0,1,'2026-07-03 23:04:48','2026-07-03 23:04:48'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'request_time');
SET @example_variable_10 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'request_time');

-- rule_variable: contacts
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','contacts','联系人数组','contacts','LIST','INPUT',NULL,'','','','与数据对象 concat 搭配',0,1,'2026-07-03 23:23:34','2026-07-03 23:23:34'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'contacts');
SET @example_variable_11 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'contacts');

-- rule_variable: nation
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','nation','民族','nation','STRING','INPUT',NULL,'','','汉','',0,1,'2026-07-04 00:57:37','2026-07-04 00:57:37'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'nation');
SET @example_variable_12 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'nation');

-- rule_variable: sex
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','sex','性别','sex','ENUM','INPUT',NULL,'','','男','',0,1,'2026-07-04 00:58:04','2026-07-04 00:58:04'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'sex');
SET @example_variable_13 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'sex');

-- rule_variable: device_id
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','device_id','设备号','device_id','STRING','INPUT',NULL,'','','5ede5aab-24d6-4c8a-9598-e186ce9512cb','',0,1,'2026-07-04 01:01:28','2026-07-04 01:01:28'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'device_id');
SET @example_variable_14 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'device_id');

-- rule_variable: imei
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','imei','国际移动设备识别码','imei','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:02:01','2026-07-04 01:02:01'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'imei');
SET @example_variable_15 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'imei');

-- rule_variable: meid
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','meid','移动设备识别码','meid','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:02:15','2026-07-04 01:02:15'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'meid');
SET @example_variable_16 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'meid');

-- rule_variable: android_id
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','android_id','AndroidID','android_id','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:02:55','2026-07-04 01:02:55'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'android_id');
SET @example_variable_17 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'android_id');

-- rule_variable: serial_number
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','serial_number','设备序列号','serial_number','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:03:20','2026-07-04 01:03:20'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'serial_number');
SET @example_variable_18 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'serial_number');

-- rule_variable: open_id
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','open_id','OpenID','open_id','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:04:25','2026-07-04 01:04:25'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'open_id');
SET @example_variable_19 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'open_id');

-- rule_variable: gaid
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','gaid','谷歌广告ID','gaid','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:05:27','2026-07-04 01:05:27'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'gaid');
SET @example_variable_20 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'gaid');

-- rule_variable: idfa
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','idfa','IOS广告ID','idfa','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:06:23','2026-07-04 01:06:23'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'idfa');
SET @example_variable_21 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'idfa');

-- rule_variable: udid
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','udid','唯一设备标识码','udid','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:06:41','2026-07-04 01:06:41'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'udid');
SET @example_variable_22 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'udid');

-- rule_variable: uuid
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','uuid','通用唯一识别码','uuid','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:06:55','2026-07-04 01:06:55'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'uuid');
SET @example_variable_23 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'uuid');

-- rule_variable: oaid
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','oaid','开放匿名设备标识','oaid','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:07:37','2026-07-04 01:07:37'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'oaid');
SET @example_variable_24 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'oaid');

-- rule_variable: idcard_address
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','idcard_address','户籍地址','idcard_address','STRING','INPUT',NULL,'','','广东省珠海市斗门县吾斯塘路755号','示例值为随机生成',0,1,'2026-07-04 01:09:00','2026-07-04 01:09:00'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'idcard_address');
SET @example_variable_25 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'idcard_address');

-- rule_variable: live_address
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','live_address','居住地址','live_address','STRING','INPUT',NULL,'','','广东省珠海市斗门县吾斯塘路755号','示例值为随机生成',0,1,'2026-07-04 01:10:12','2026-07-04 01:10:12'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'live_address');
SET @example_variable_26 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'live_address');

-- rule_variable: company_address
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','company_address','公司地址','company_address','STRING','INPUT',NULL,'','','广东省珠海市斗门县吾斯塘路755号','示例值为随机生成',0,1,'2026-07-04 01:11:14','2026-07-04 01:11:14'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'company_address');
SET @example_variable_27 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'company_address');

-- rule_variable: sender_address
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','sender_address','发货地址','sender_address','STRING','INPUT',NULL,'','','广东省珠海市斗门县吾斯塘路755号','示例值为随机生成',0,1,'2026-07-04 01:12:53','2026-07-04 01:12:53'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'sender_address');
SET @example_variable_28 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'sender_address');

-- rule_variable: delivery_address
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','delivery_address','收货地址','delivery_address','STRING','INPUT',NULL,'','','广东省珠海市斗门县吾斯塘路755号','示例值为随机生成',0,1,'2026-07-04 01:13:26','2026-07-04 01:13:26'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'delivery_address');
SET @example_variable_29 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'delivery_address');

-- rule_variable: address
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','address','地址','address','STRING','INPUT',NULL,'','','广东省珠海市斗门县吾斯塘路755号','示例值为随机生成',0,1,'2026-07-04 01:13:55','2026-07-04 01:13:55'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'address');
SET @example_variable_30 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'address');

-- rule_variable: delivery_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','delivery_name','收货人姓名','delivery_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:14:18','2026-07-04 01:15:52'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'delivery_name');
SET @example_variable_31 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'delivery_name');

-- rule_variable: delivery_mobile
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','delivery_mobile','收货人手机','delivery_mobile','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:14:35','2026-07-04 01:15:52'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'delivery_mobile');
SET @example_variable_32 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'delivery_mobile');

-- rule_variable: sender_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','sender_name','发货人姓名','sender_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:14:52','2026-07-04 01:14:52'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'sender_name');
SET @example_variable_33 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'sender_name');

-- rule_variable: sender_mobile
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','sender_mobile','发货人手机号','sender_mobile','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:15:08','2026-07-04 01:15:08'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'sender_mobile');
SET @example_variable_34 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'sender_mobile');

-- rule_variable: idcard_issue_date
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','idcard_issue_date','身份证开始有效期','idcard_issue_date','STRING','INPUT',NULL,'','','2015-01-01','',0,1,'2026-07-04 01:17:01','2026-07-04 01:33:46'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'idcard_issue_date');
SET @example_variable_35 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'idcard_issue_date');

-- rule_variable: idcard_expiry_date
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','idcard_expiry_date','身份证结束有效期','idcard_expiry_date','STRING','INPUT',NULL,'','','长期','',0,1,'2026-07-04 01:17:36','2026-07-04 01:33:46'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'idcard_expiry_date');
SET @example_variable_36 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'idcard_expiry_date');

-- rule_variable: idcard_validity_days
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','idcard_validity_days','身份证有效天数','idcard_validity_days','NUMBER','INPUT',NULL,'','','180','',0,1,'2026-07-04 01:20:05','2026-07-04 01:20:05'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'idcard_validity_days');
SET @example_variable_38 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'idcard_validity_days');

-- rule_variable: marital_status
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','marital_status','婚姻状况','marital_status','STRING','INPUT',NULL,'','','已婚','',0,1,'2026-07-04 01:21:09','2026-07-04 01:21:09'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'marital_status');
SET @example_variable_39 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'marital_status');

-- rule_variable: spouse_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','spouse_name','配偶姓名','spouse_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:22:13','2026-07-04 01:22:13'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'spouse_name');
SET @example_variable_40 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'spouse_name');

-- rule_variable: spouse_sex
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','spouse_sex','配偶性别','spouse_sex','ENUM','INPUT',NULL,'','','女','',0,1,'2026-07-04 01:22:37','2026-07-04 01:22:37'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'spouse_sex');
SET @example_variable_41 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'spouse_sex');

-- rule_variable: spouse_mobile
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','spouse_mobile','配偶手机号','spouse_mobile','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:23:55','2026-07-04 01:23:55'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'spouse_mobile');
SET @example_variable_42 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'spouse_mobile');

-- rule_variable: spouse_idcard_no
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','spouse_idcard_no','配偶身份证号','spouse_idcard_no','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:24:14','2026-07-04 01:24:14'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'spouse_idcard_no');
SET @example_variable_43 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'spouse_idcard_no');

-- rule_variable: number_of_children
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','number_of_children','抚养孩童数量','number_of_children','NUMBER','INPUT',NULL,'','','1','',0,1,'2026-07-04 01:25:04','2026-07-04 01:25:04'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'number_of_children');
SET @example_variable_44 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'number_of_children');

-- rule_variable: number_of_elderly_supported
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','number_of_elderly_supported','赡养老人数量','number_of_elderly_supported','NUMBER','INPUT',NULL,'','','2','',0,1,'2026-07-04 01:26:18','2026-07-04 01:26:18'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'number_of_elderly_supported');
SET @example_variable_45 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'number_of_elderly_supported');

-- rule_variable: education
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','education','学历','education','STRING','INPUT',NULL,'','','本科','',0,1,'2026-07-04 01:27:20','2026-07-04 01:29:16'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'education');
SET @example_variable_46 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'education');

-- rule_variable: degree
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','degree','学位','degree','STRING','INPUT',NULL,'','','经济学学士','',0,1,'2026-07-04 01:28:56','2026-07-04 01:28:56'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'degree');
SET @example_variable_47 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'degree');

-- rule_variable: device_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','device_name','设备名称','device_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:31:13','2026-07-04 01:31:13'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'device_name');
SET @example_variable_48 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'device_name');

-- rule_variable: id_type
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','id_type','证件类型','id_type','STRING','INPUT',NULL,'','','身份证','',0,1,'2026-07-04 01:31:43','2026-07-04 01:31:43'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'id_type');
SET @example_variable_49 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'id_type');

-- rule_variable: idcard_issuing_authority
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','idcard_issuing_authority','证件签发机关','idcard_issuing_authority','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:34:22','2026-07-04 01:34:22'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'idcard_issuing_authority');
SET @example_variable_50 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'idcard_issuing_authority');

-- rule_variable: email
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','email','邮箱','email','STRING','INPUT',NULL,'','','hscredit@hengshucredit.com','',0,1,'2026-07-04 01:35:00','2026-07-04 01:35:00'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'email');
SET @example_variable_51 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'email');

-- rule_variable: contact_method
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','contact_method','联系方法','contact_method','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:35:37','2026-07-04 01:35:37'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'contact_method');
SET @example_variable_52 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'contact_method');

-- rule_variable: residential_address
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','residential_address','常住地址','residential_address','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:36:12','2026-07-04 01:36:12'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'residential_address');
SET @example_variable_53 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'residential_address');

-- rule_variable: country
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','country','国家','country','STRING','INPUT',NULL,'','','中国','',0,1,'2026-07-04 01:36:37','2026-07-04 01:36:37'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'country');
SET @example_variable_54 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'country');

-- rule_variable: city
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','city','城市','city','STRING','INPUT',NULL,'','','上海市','',0,1,'2026-07-04 01:37:05','2026-07-04 01:37:05'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'city');
SET @example_variable_55 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'city');

-- rule_variable: district
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','district','区/县','district','STRING','INPUT',NULL,'','','静安区','',0,1,'2026-07-04 01:37:30','2026-07-04 01:37:30'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'district');
SET @example_variable_56 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'district');

-- rule_variable: detail_address
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','detail_address','详细地址','detail_address','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:38:09','2026-07-04 01:38:09'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'detail_address');
SET @example_variable_57 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'detail_address');

-- rule_variable: postal_code
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','postal_code','邮政编码','postal_code','STRING','INPUT',NULL,'','','2000000','',0,1,'2026-07-04 01:38:36','2026-07-04 01:38:36'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'postal_code');
SET @example_variable_58 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'postal_code');

-- rule_variable: employment_status
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','employment_status','工作状态','employment_status','STRING','INPUT',NULL,'','','在职','',0,1,'2026-07-04 01:39:34','2026-07-04 01:39:34'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'employment_status');
SET @example_variable_59 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'employment_status');

-- rule_variable: company_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','company_name','公司名称','company_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:40:18','2026-07-04 01:40:18'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'company_name');
SET @example_variable_60 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'company_name');

-- rule_variable: company_mobile
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','company_mobile','公司电话','company_mobile','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:40:43','2026-07-04 01:40:43'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'company_mobile');
SET @example_variable_61 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'company_mobile');

-- rule_variable: company_type
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','company_type','公司类型','company_type','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:41:02','2026-07-04 01:41:02'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'company_type');
SET @example_variable_62 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'company_type');

-- rule_variable: company_industry
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','company_industry','公司行业','company_industry','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:41:25','2026-07-04 01:41:25'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'company_industry');
SET @example_variable_63 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'company_industry');

-- rule_variable: occupation_type
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','occupation_type','职业类型','occupation_type','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:41:56','2026-07-04 01:41:56'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'occupation_type');
SET @example_variable_64 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'occupation_type');

-- rule_variable: occupation_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','occupation_name','职业名称','occupation_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:42:10','2026-07-04 01:42:10'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'occupation_name');
SET @example_variable_65 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'occupation_name');

-- rule_variable: job_title
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','job_title','职务/职位','job_title','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:42:28','2026-07-04 01:42:28'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'job_title');
SET @example_variable_66 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'job_title');

-- rule_variable: employment_start_date
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','employment_start_date','入职开始时间','employment_start_date','DATE','INPUT',NULL,'','','','',0,1,'2026-07-04 01:42:50','2026-07-04 01:42:50'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'employment_start_date');
SET @example_variable_67 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'employment_start_date');

-- rule_variable: employment_tenure
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','employment_tenure','工作年限','employment_tenure','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 01:43:09','2026-07-04 01:43:09'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'employment_tenure');
SET @example_variable_68 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'employment_tenure');

-- rule_variable: income_monthly
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','income_monthly','月收入','income_monthly','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:43:46','2026-07-04 01:43:46'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'income_monthly');
SET @example_variable_69 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'income_monthly');

-- rule_variable: income_yearly
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','income_yearly','年收入','income_yearly','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:44:06','2026-07-04 01:44:06'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'income_yearly');
SET @example_variable_70 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'income_yearly');

-- rule_variable: currency
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','currency','币种','currency','STRING','INPUT',NULL,'','','人民币','',0,1,'2026-07-04 01:44:31','2026-07-04 01:44:31'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'currency');
SET @example_variable_71 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'currency');

-- rule_variable: income_source
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','income_source','收入来源','income_source','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:44:47','2026-07-04 02:02:38'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'income_source');
SET @example_variable_72 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'income_source');

-- rule_variable: gps_longitude
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','gps_longitude','经度','gps_longitude','NUMBER','INPUT',NULL,'','-180~180','','',0,1,'2026-07-04 01:49:17','2026-07-04 01:49:17'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'gps_longitude');
SET @example_variable_73 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'gps_longitude');

-- rule_variable: gps_latitude
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','gps_latitude','纬度','gps_latitude','NUMBER','INPUT',NULL,'','-90~90','','',0,1,'2026-07-04 01:49:59','2026-07-04 01:49:59'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'gps_latitude');
SET @example_variable_74 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'gps_latitude');

-- rule_variable: coordinate_reference_system
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','coordinate_reference_system','坐标参考系','coordinate_reference_system','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:52:29','2026-07-04 01:52:29'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'coordinate_reference_system');
SET @example_variable_75 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'coordinate_reference_system');

-- rule_variable: risk_level
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','risk_level','风险等级','risk_level','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:54:38','2026-07-04 01:54:38'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'risk_level');
SET @example_variable_78 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'risk_level');

-- rule_variable: device_brand
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','device_brand','设备品牌','device_brand','STRING','INPUT',NULL,'','','','Apple',0,1,'2026-07-04 01:54:58','2026-07-04 01:54:58'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'device_brand');
SET @example_variable_79 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'device_brand');

-- rule_variable: device_model
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','device_model','设备型号','device_model','STRING','INPUT',NULL,'','','iPhone 15','',0,1,'2026-07-04 01:55:09','2026-07-04 01:55:09'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'device_model');
SET @example_variable_80 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'device_model');

-- rule_variable: screen_width
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','screen_width','屏幕宽度','screen_width','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 01:55:47','2026-07-04 01:55:47'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'screen_width');
SET @example_variable_81 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'screen_width');

-- rule_variable: screen_height
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','screen_height','屏幕高度','screen_height','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 01:55:57','2026-07-04 01:55:57'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'screen_height');
SET @example_variable_82 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'screen_height');

-- rule_variable: screen_resolution
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','screen_resolution','屏幕分辨率','screen_resolution','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:56:10','2026-07-04 01:56:10'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'screen_resolution');
SET @example_variable_83 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'screen_resolution');

-- rule_variable: cpu_core_count
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','cpu_core_count','CPU核心数','cpu_core_count','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 01:56:28','2026-07-04 01:56:28'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'cpu_core_count');
SET @example_variable_84 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'cpu_core_count');

-- rule_variable: device_memory
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','device_memory','设备内存','device_memory','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:56:39','2026-07-04 01:56:39'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'device_memory');
SET @example_variable_85 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'device_memory');

-- rule_variable: os_version
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','os_version','系统版本','os_version','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:56:54','2026-07-04 01:56:54'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'os_version');
SET @example_variable_86 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'os_version');

-- rule_variable: operating_system
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','operating_system','操作系统','operating_system','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:57:05','2026-07-04 01:57:05'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'operating_system');
SET @example_variable_87 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'operating_system');

-- rule_variable: system_language
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','system_language','系统语言','system_language','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:57:14','2026-07-04 01:57:14'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'system_language');
SET @example_variable_88 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'system_language');

-- rule_variable: time_zone
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','time_zone','时区','time_zone','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:57:20','2026-07-04 01:57:20'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'time_zone');
SET @example_variable_89 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'time_zone');

-- rule_variable: is_rooted
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','is_rooted','是否Root','is_rooted','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:57:34','2026-07-04 01:57:34'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_rooted');
SET @example_variable_90 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_rooted');

-- rule_variable: is_jailbroken
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','is_jailbroken','是否越狱','is_jailbroken','NUMBER','INPUT',NULL,'','','1','',0,1,'2026-07-04 01:57:42','2026-07-04 01:57:42'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_jailbroken');
SET @example_variable_91 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_jailbroken');

-- rule_variable: is_emulator
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','is_emulator','是否模拟器','is_emulator','NUMBER','INPUT',NULL,'','','1','',0,1,'2026-07-04 01:57:50','2026-07-04 01:57:50'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_emulator');
SET @example_variable_92 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_emulator');

-- rule_variable: is_proxy
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','is_proxy','是否代理','is_proxy','NUMBER','INPUT',NULL,'','','1','',0,1,'2026-07-04 01:58:17','2026-07-04 01:58:17'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_proxy');
SET @example_variable_93 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_proxy');

-- rule_variable: is_remote_control_app_detected
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','is_remote_control_app_detected','是否存在远程控制软件','is_remote_control_app_detected','NUMBER','INPUT',NULL,'','','1','',0,1,'2026-07-04 01:59:07','2026-07-04 01:59:07'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_remote_control_app_detected');
SET @example_variable_94 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_remote_control_app_detected');

-- rule_variable: browser_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','browser_name','浏览器名称','browser_name','STRING','INPUT',NULL,'','','Chrome','',0,1,'2026-07-04 01:59:25','2026-07-04 01:59:25'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'browser_name');
SET @example_variable_95 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'browser_name');

-- rule_variable: browser_version
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','browser_version','浏览器版本','browser_version','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:59:36','2026-07-04 01:59:36'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'browser_version');
SET @example_variable_96 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'browser_version');

-- rule_variable: user_agent
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','user_agent','请求头','user_agent','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 01:59:50','2026-07-04 01:59:50'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'user_agent');
SET @example_variable_97 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'user_agent');

-- rule_variable: ip_address
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','ip_address','IP地址','ip_address','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:00:26','2026-07-04 02:00:26'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'ip_address');
SET @example_variable_98 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'ip_address');

-- rule_variable: ip_isp
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','ip_isp','IP所属运营商','ip_isp','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:00:45','2026-07-04 02:00:45'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'ip_isp');
SET @example_variable_99 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'ip_isp');

-- rule_variable: network_type
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','network_type','网络类型','network_type','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:01:00','2026-07-04 02:01:00'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'network_type');
SET @example_variable_100 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'network_type');

-- rule_variable: app_package_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','app_package_name','App包名','app_package_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:01:29','2026-07-04 02:01:29'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'app_package_name');
SET @example_variable_101 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'app_package_name');

-- rule_variable: app_version
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','app_version','App版本','app_version','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:01:39','2026-07-04 02:01:39'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'app_version');
SET @example_variable_102 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'app_version');

-- rule_variable: app_install_source
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','app_install_source','安装来源','app_install_source','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:01:53','2026-07-04 02:01:53'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'app_install_source');
SET @example_variable_103 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'app_install_source');

-- rule_variable: product_id
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','product_id','产品编码','product_id','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:03:06','2026-07-04 02:03:06'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'product_id');
SET @example_variable_104 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'product_id');

-- rule_variable: product_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','product_name','产品名称','product_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:03:26','2026-07-04 02:03:26'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'product_name');
SET @example_variable_105 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'product_name');

-- rule_variable: channel_code
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','channel_code','渠道编码','channel_code','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:03:49','2026-07-04 02:03:49'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'channel_code');
SET @example_variable_106 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'channel_code');

-- rule_variable: channel_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','channel_name','渠道名称','channel_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:04:03','2026-07-04 02:04:03'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'channel_name');
SET @example_variable_107 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'channel_name');

-- rule_variable: sub_channel_code
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','sub_channel_code','子渠道编码','sub_channel_code','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:04:23','2026-07-04 02:04:23'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'sub_channel_code');
SET @example_variable_108 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'sub_channel_code');

-- rule_variable: sub_channel_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','sub_channel_name','子渠道名称','sub_channel_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:04:42','2026-07-04 02:04:42'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'sub_channel_name');
SET @example_variable_109 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'sub_channel_name');

-- rule_variable: loan_purpose
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','loan_purpose','借款用途','loan_purpose','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:04:57','2026-07-04 02:04:57'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'loan_purpose');
SET @example_variable_110 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'loan_purpose');

-- rule_variable: repayment_method
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','repayment_method','还款方式','repayment_method','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:05:08','2026-07-04 02:05:08'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'repayment_method');
SET @example_variable_111 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'repayment_method');

-- rule_variable: source_channel_code
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','source_channel_code','渠道来源编码','source_channel_code','STRING','INPUT',NULL,'','','H5','',0,1,'2026-07-04 02:07:00','2026-07-04 02:07:00'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'source_channel_code');
SET @example_variable_112 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'source_channel_code');

-- rule_variable: source_channel_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','source_channel_name','渠道来源名称','source_channel_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:07:33','2026-07-04 02:07:33'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'source_channel_name');
SET @example_variable_113 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'source_channel_name');

-- rule_variable: update_time
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','update_time','更新时间','update_time','DATE','INPUT',NULL,'','','','',0,1,'2026-07-04 02:08:01','2026-07-04 02:08:01'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'update_time');
SET @example_variable_114 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'update_time');

-- rule_variable: marketing_code
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','marketing_code','推广编码','marketing_code','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:08:27','2026-07-04 02:08:27'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'marketing_code');
SET @example_variable_115 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'marketing_code');

-- rule_variable: marketing_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','marketing_name','推广名称','marketing_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:08:37','2026-07-04 02:08:37'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'marketing_name');
SET @example_variable_116 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'marketing_name');

-- rule_variable: wifi_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','wifi_name','WIFI名称','wifi_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:09:16','2026-07-04 02:09:16'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'wifi_name');
SET @example_variable_117 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'wifi_name');

-- rule_variable: wifi_mac_address
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','wifi_mac_address','WIFI MAC 地址','wifi_mac_address','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:10:14','2026-07-04 02:10:14'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'wifi_mac_address');
SET @example_variable_118 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'wifi_mac_address');

-- rule_variable: wifi_ip_address
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','wifi_ip_address','WIFI IP 地址','wifi_ip_address','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:10:59','2026-07-04 02:10:59'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'wifi_ip_address');
SET @example_variable_119 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'wifi_ip_address');

-- rule_variable: dns_servers
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','dns_servers','DNS地址','dns_servers','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:11:17','2026-07-04 02:11:17'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'dns_servers');
SET @example_variable_120 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'dns_servers');

-- rule_variable: wifi_network_fingerprint
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','wifi_network_fingerprint','WIFI标识','wifi_network_fingerprint','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:11:49','2026-07-04 02:11:49'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'wifi_network_fingerprint');
SET @example_variable_121 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'wifi_network_fingerprint');

-- rule_variable: credit_period
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','credit_period','授信期数','credit_period','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:12:37','2026-07-04 02:12:37'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_period');
SET @example_variable_122 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_period');

-- rule_variable: loan_period
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','loan_period','借款期数','loan_period','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:12:51','2026-07-04 02:12:51'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'loan_period');
SET @example_variable_123 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'loan_period');

-- rule_variable: apply_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','apply_amount','申请金额','apply_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:13:09','2026-07-04 02:13:09'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'apply_amount');
SET @example_variable_124 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'apply_amount');

-- rule_variable: credit_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','credit_amount','授信金额','credit_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:13:27','2026-07-04 02:13:27'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_amount');
SET @example_variable_125 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_amount');

-- rule_variable: loan_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','loan_amount','借款金额','loan_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:13:38','2026-07-04 02:13:38'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'loan_amount');
SET @example_variable_126 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'loan_amount');

-- rule_variable: principal_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','principal_amount','本金','principal_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:24:46','2026-07-04 02:24:46'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'principal_amount');
SET @example_variable_127 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'principal_amount');

-- rule_variable: interest_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','interest_amount','利息','interest_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:24:56','2026-07-04 02:24:56'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'interest_amount');
SET @example_variable_128 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'interest_amount');

-- rule_variable: penalty_interest_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','penalty_interest_amount','罚息','penalty_interest_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:25:06','2026-07-04 02:25:06'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'penalty_interest_amount');
SET @example_variable_129 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'penalty_interest_amount');

-- rule_variable: level
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','level','等级','level','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:25:48','2026-07-04 02:25:48'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'level');
SET @example_variable_131 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'level');

-- rule_variable: customer_type
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','customer_type','客户类型','customer_type','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:26:11','2026-07-04 02:26:11'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'customer_type');
SET @example_variable_132 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'customer_type');

-- rule_variable: customer_id
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','customer_id','客户编号','customer_id','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:26:32','2026-07-04 02:26:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'customer_id');
SET @example_variable_133 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'customer_id');

-- rule_variable: customer_level
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','customer_level','客户等级','customer_level','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:26:57','2026-07-04 02:26:57'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'customer_level');
SET @example_variable_134 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'customer_level');

-- rule_variable: customer_tags
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','customer_tags','客户标签','customer_tags','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:27:10','2026-07-04 02:27:10'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'customer_tags');
SET @example_variable_135 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'customer_tags');

-- rule_variable: customer_groups
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','customer_groups','客户群组','customer_groups','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:27:46','2026-07-04 02:27:46'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'customer_groups');
SET @example_variable_136 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'customer_groups');

-- rule_variable: current_overdue_days
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','current_overdue_days','当前逾期天数','current_overdue_days','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:28:14','2026-07-04 02:28:14'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'current_overdue_days');
SET @example_variable_137 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'current_overdue_days');

-- rule_variable: history_overdue_days
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','history_overdue_days','历史逾期天数','history_overdue_days','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:28:32','2026-07-04 02:28:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'history_overdue_days');
SET @example_variable_138 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'history_overdue_days');

-- rule_variable: history_overdue_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','history_overdue_amount','历史逾期金额','history_overdue_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:28:32','2026-07-04 02:28:32'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'history_overdue_amount');
SET @example_variable_139 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'history_overdue_amount');

-- rule_variable: current_overdue_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','current_overdue_amount','当前逾期金额','current_overdue_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:28:14','2026-07-04 02:28:14'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'current_overdue_amount');
SET @example_variable_140 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'current_overdue_amount');

-- rule_variable: rule_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','rule_name','规则名称','rule_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:36:09','2026-07-04 02:36:09'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'rule_name');
SET @example_variable_142 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'rule_name');

-- rule_variable: rule_code
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','rule_code','规则编码','rule_code','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:36:19','2026-07-04 02:36:19'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'rule_code');
SET @example_variable_143 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'rule_code');

-- rule_variable: rule_type
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','rule_type','规则类型','rule_type','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:36:38','2026-07-04 02:36:38'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'rule_type');
SET @example_variable_144 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'rule_type');

-- rule_variable: is_free
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','is_free','是否计费','is_free','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:37:02','2026-07-04 02:37:02'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_free');
SET @example_variable_145 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_free');

-- rule_variable: is_found
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','is_found','是否查得','is_found','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:37:24','2026-07-04 02:37:24'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_found');
SET @example_variable_146 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_found');

-- rule_variable: is_hit
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','is_hit','是否命中','is_hit','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:37:49','2026-07-04 02:37:49'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_hit');
SET @example_variable_147 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_hit');

-- rule_variable: message
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','message','描述','message','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:38:06','2026-07-04 02:38:06'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'message');
SET @example_variable_148 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'message');

-- rule_variable: project_id
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','project_id','项目编号','project_id','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:39:00','2026-07-04 02:39:00'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'project_id');
SET @example_variable_149 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'project_id');

-- rule_variable: project_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','project_name','项目名称','project_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:39:09','2026-07-04 02:39:09'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'project_name');
SET @example_variable_150 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'project_name');

-- rule_variable: probability
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','probability','概率','probability','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:40:12','2026-07-04 02:40:12'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'probability');
SET @example_variable_151 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'probability');

-- rule_variable: random_int
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','random_int','随机整数','random_int','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:40:28','2026-07-04 02:40:28'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'random_int');
SET @example_variable_152 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'random_int');

-- rule_variable: random_float
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','random_float','随机浮点数','random_float','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:40:54','2026-07-04 02:40:54'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'random_float');
SET @example_variable_153 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'random_float');

-- rule_variable: random_probability
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','random_probability','随机概率','random_probability','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:41:14','2026-07-04 02:41:14'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'random_probability');
SET @example_variable_154 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'random_probability');

-- rule_variable: available_credit_limit
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','available_credit_limit','可用额度','available_credit_limit','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:43:34','2026-07-04 02:43:34'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'available_credit_limit');
SET @example_variable_155 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'available_credit_limit');

-- rule_variable: temporary_credit_limit
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','temporary_credit_limit','临时额度','temporary_credit_limit','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:43:45','2026-07-04 02:43:45'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'temporary_credit_limit');
SET @example_variable_156 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'temporary_credit_limit');

-- rule_variable: fixed_credit_limit
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','fixed_credit_limit','固定额度','fixed_credit_limit','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:43:56','2026-07-04 02:43:56'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'fixed_credit_limit');
SET @example_variable_157 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'fixed_credit_limit');

-- rule_variable: credit_limit_expiry_date
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','credit_limit_expiry_date','额度有效期','credit_limit_expiry_date','DATE','INPUT',NULL,'','','','',0,1,'2026-07-04 02:44:11','2026-07-04 02:44:11'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit_expiry_date');
SET @example_variable_158 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit_expiry_date');

-- rule_variable: credit_limit_validity_days
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','credit_limit_validity_days','额度有效天数','credit_limit_validity_days','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:44:23','2026-07-04 02:44:23'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit_validity_days');
SET @example_variable_159 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit_validity_days');

-- rule_variable: is_credit_limit_frozen
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','is_credit_limit_frozen','是否额度冻结','is_credit_limit_frozen','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:44:35','2026-07-04 02:44:35'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_credit_limit_frozen');
SET @example_variable_160 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'is_credit_limit_frozen');

-- rule_variable: freeze_days
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','freeze_days','冻结天数','freeze_days','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:44:56','2026-07-04 02:44:56'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'freeze_days');
SET @example_variable_161 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'freeze_days');

-- rule_variable: credit_limit_freeze_reason
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','credit_limit_freeze_reason','额度冻结原因','credit_limit_freeze_reason','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:45:26','2026-07-04 02:45:26'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit_freeze_reason');
SET @example_variable_162 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit_freeze_reason');

-- rule_variable: used_credit_limit
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','used_credit_limit','已用额度','used_credit_limit','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:46:16','2026-07-04 02:46:16'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'used_credit_limit');
SET @example_variable_163 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'used_credit_limit');

-- rule_variable: face_score
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','face_score','人脸分数','face_score','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:49:20','2026-07-04 02:49:20'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'face_score');
SET @example_variable_164 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'face_score');

-- rule_variable: balance
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','balance','在贷余额','balance','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:52:22','2026-07-04 02:52:22'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'balance');
SET @example_variable_165 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'balance');

-- rule_variable: active_loan_count
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','active_loan_count','在贷笔数','active_loan_count','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:52:49','2026-07-04 02:52:49'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'active_loan_count');
SET @example_variable_166 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'active_loan_count');

-- rule_variable: credit_limit_adjustment_reason
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','credit_limit_adjustment_reason','额度调整原因','credit_limit_adjustment_reason','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:53:10','2026-07-04 02:53:10'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit_adjustment_reason');
SET @example_variable_167 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit_adjustment_reason');

-- rule_variable: credit_limit_adjustment_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','credit_limit_adjustment_amount','额度调整金额','credit_limit_adjustment_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:53:19','2026-07-04 02:54:01'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit_adjustment_amount');
SET @example_variable_168 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit_adjustment_amount');

-- rule_variable: credit_limit
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','credit_limit','授信额度','credit_limit','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:53:42','2026-07-04 02:53:42'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit');
SET @example_variable_169 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit');

-- rule_variable: credit_limit_utilization_rate
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','credit_limit_utilization_rate','额度使用率','credit_limit_utilization_rate','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:54:26','2026-07-04 02:54:26'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit_utilization_rate');
SET @example_variable_170 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit_utilization_rate');

-- rule_variable: credit_limit_freeze_end_time
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','credit_limit_freeze_end_time','额度冻结结束时间','credit_limit_freeze_end_time','DATE','INPUT',NULL,'','','','',0,1,'2026-07-04 02:54:49','2026-07-04 02:54:49'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit_freeze_end_time');
SET @example_variable_171 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit_freeze_end_time');

-- rule_variable: credit_limit_freeze_start_time
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','credit_limit_freeze_start_time','额度冻结开始时间','credit_limit_freeze_start_time','DATE','INPUT',NULL,'','','','',0,1,'2026-07-04 02:55:00','2026-07-04 02:55:00'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit_freeze_start_time');
SET @example_variable_172 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_limit_freeze_start_time');

-- rule_variable: paid_principal_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','paid_principal_amount','已还本金','paid_principal_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:55:36','2026-07-04 02:55:36'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'paid_principal_amount');
SET @example_variable_173 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'paid_principal_amount');

-- rule_variable: paid_interest_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','paid_interest_amount','已还利息','paid_interest_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:55:46','2026-07-04 02:55:46'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'paid_interest_amount');
SET @example_variable_174 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'paid_interest_amount');

-- rule_variable: paid_penalty_interest_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','paid_penalty_interest_amount','已还罚息','paid_penalty_interest_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:55:56','2026-07-04 02:55:56'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'paid_penalty_interest_amount');
SET @example_variable_175 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'paid_penalty_interest_amount');

-- rule_variable: paid_fee_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','paid_fee_amount','已还费用','paid_fee_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:56:07','2026-07-04 02:56:07'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'paid_fee_amount');
SET @example_variable_176 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'paid_fee_amount');

-- rule_variable: paid_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','paid_amount','已还金额','paid_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:56:35','2026-07-04 02:56:35'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'paid_amount');
SET @example_variable_177 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'paid_amount');

-- rule_variable: outstanding_principal_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','outstanding_principal_amount','未还本金','outstanding_principal_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:57:02','2026-07-04 02:57:02'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'outstanding_principal_amount');
SET @example_variable_178 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'outstanding_principal_amount');

-- rule_variable: outstanding_interest_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','outstanding_interest_amount','未还利息','outstanding_interest_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:57:11','2026-07-04 02:57:11'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'outstanding_interest_amount');
SET @example_variable_179 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'outstanding_interest_amount');

-- rule_variable: outstanding_penalty_interest_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','outstanding_penalty_interest_amount','未还罚息','outstanding_penalty_interest_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:57:21','2026-07-04 02:57:21'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'outstanding_penalty_interest_amount');
SET @example_variable_180 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'outstanding_penalty_interest_amount');

-- rule_variable: outstanding_amount
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','outstanding_amount','剩余应还总额','outstanding_amount','NUMBER','INPUT',NULL,'','','','',0,1,'2026-07-04 02:57:37','2026-07-04 02:58:17'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'outstanding_amount');
SET @example_variable_181 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'outstanding_amount');

-- rule_variable: decision_flow_code
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','decision_flow_code','决策流编码','decision_flow_code','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:58:52','2026-07-04 02:58:52'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'decision_flow_code');
SET @example_variable_182 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'decision_flow_code');

-- rule_variable: decision_flow_name
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','decision_flow_name','决策流名称','decision_flow_name','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:59:07','2026-07-04 02:59:07'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'decision_flow_name');
SET @example_variable_183 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'decision_flow_name');

-- rule_variable: decision_flow_version
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','decision_flow_version','决策流版本','decision_flow_version','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 02:59:21','2026-07-04 02:59:21'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'decision_flow_version');
SET @example_variable_184 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'decision_flow_version');

-- rule_variable: interest_rate
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','interest_rate','利率','interest_rate','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 03:01:47','2026-07-04 03:01:47'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'interest_rate');
SET @example_variable_185 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'interest_rate');

-- rule_variable: contract_id
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','contract_id','合同编号','contract_id','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 03:03:35','2026-07-04 03:03:35'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'contract_id');
SET @example_variable_186 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'contract_id');

-- rule_variable: order_id
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','order_id','订单编号','order_id','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 03:03:49','2026-07-04 03:03:49'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'order_id');
SET @example_variable_187 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'order_id');

-- rule_variable: credit_status
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','credit_status','授信状态','credit_status','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 03:04:08','2026-07-04 03:04:08'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_status');
SET @example_variable_188 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'credit_status');

-- rule_variable: loan_status
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','loan_status','借款状态','loan_status','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 03:04:17','2026-07-04 03:04:17'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'loan_status');
SET @example_variable_189 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'loan_status');

-- rule_variable: application_status
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','application_status','申请状态','application_status','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 03:04:26','2026-07-04 03:04:26'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'application_status');
SET @example_variable_190 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'application_status');

-- rule_variable: approval_status
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','approval_status','审批状态','approval_status','STRING','INPUT',NULL,'','','','',0,1,'2026-07-04 03:04:34','2026-07-04 03:04:34'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'approval_status');
SET @example_variable_191 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'approval_status');

-- rule_variable: cancel_time
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','cancel_time','取消时间','cancel_time','DATE','INPUT',NULL,'','','','',0,1,'2026-07-04 03:04:47','2026-07-04 03:04:47'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'cancel_time');
SET @example_variable_192 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'cancel_time');

-- rule_variable: close_time
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','close_time','关闭时间','close_time','DATE','INPUT',NULL,'','','','',0,1,'2026-07-04 03:05:03','2026-07-04 03:05:03'
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'close_time');
SET @example_variable_193 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'close_time');

-- rule_variable: NEGATIVE_INFINITY
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','NEGATIVE_INFINITY','负无穷','NEGATIVE_INFINITY','DOUBLE','CONSTANT',NULL,'-Infinity','','-Infinity','系统常量：数值比较下界，可在条件右侧通过字段选择器引用。',-1000,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'NEGATIVE_INFINITY');
SET @example_variable_198 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'NEGATIVE_INFINITY');

-- rule_variable: NULL_VALUE
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','NULL_VALUE','空值','NULL_VALUE','OBJECT','CONSTANT',NULL,'null','','null','系统常量：空值，可在条件右侧通过字段选择器引用。',-999,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'NULL_VALUE');
SET @example_variable_199 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'NULL_VALUE');

-- rule_variable: EMPTY_STRING
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','EMPTY_STRING','空字符串','EMPTY_STRING','STRING','CONSTANT',NULL,'','','','系统常量：空字符串，可在条件右侧通过字段选择器引用。',-998,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'EMPTY_STRING');
SET @example_variable_200 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'EMPTY_STRING');

-- rule_variable: EMPTY_LIST
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','EMPTY_LIST','空列表','EMPTY_LIST','LIST','CONSTANT',NULL,'[]','','[]','系统常量：空列表，可在条件右侧通过字段选择器引用。',-997,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'EMPTY_LIST');
SET @example_variable_201 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'EMPTY_LIST');

-- rule_variable: EMPTY_MAP
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','EMPTY_MAP','空映射','EMPTY_MAP','MAP','CONSTANT',NULL,'{}','','{}','系统常量：空键值映射，可在条件右侧通过字段选择器引用。',-996,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'EMPTY_MAP');
SET @example_variable_202 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'EMPTY_MAP');

-- rule_variable: FALSE_VALUE
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','FALSE_VALUE','布尔假','FALSE_VALUE','BOOLEAN','CONSTANT',NULL,'false','','false','系统常量：布尔假。',-995,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'FALSE_VALUE');
SET @example_variable_203 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'FALSE_VALUE');

-- rule_variable: TRUE_VALUE
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','TRUE_VALUE','布尔真','TRUE_VALUE','BOOLEAN','CONSTANT',NULL,'true','','true','系统常量：布尔真。',-994,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'TRUE_VALUE');
SET @example_variable_204 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'TRUE_VALUE');

-- rule_variable: NEGATIVE_ONE
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','NEGATIVE_ONE','负一','NEGATIVE_ONE','NUMBER','CONSTANT',NULL,'-1','','-1','系统常量：数值负一。',-993,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'NEGATIVE_ONE');
SET @example_variable_205 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'NEGATIVE_ONE');

-- rule_variable: ZERO
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','ZERO','零','ZERO','NUMBER','CONSTANT',NULL,'0','','0','系统常量：数值零。',-992,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'ZERO');
SET @example_variable_206 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'ZERO');

-- rule_variable: ONE
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','ONE','一','ONE','NUMBER','CONSTANT',NULL,'1','','1','系统常量：数值一。',-991,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'ONE');
SET @example_variable_207 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'ONE');

-- rule_variable: POSITIVE_INFINITY
INSERT INTO rule_variable (`project_id`,`scope`,`var_code`,`var_label`,`script_name`,`var_type`,`var_source`,`source_config`,`default_value`,`value_range`,`example_value`,`description`,`sort_order`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','POSITIVE_INFINITY','正无穷','POSITIVE_INFINITY','DOUBLE','CONSTANT',NULL,'Infinity','','Infinity','系统常量：数值比较上界，可在条件右侧通过字段选择器引用。',-990,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'POSITIVE_INFINITY');
SET @example_variable_208 = (SELECT existing.id FROM rule_variable existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`var_code` <=> 'POSITIVE_INFINITY');

-- rule_variable_option: 1
INSERT INTO rule_variable_option (`variable_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_variable_13,'0','女性',0
WHERE NOT EXISTS (SELECT 1 FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_13 AND existing.`option_value` <=> '0');
SET @example_variable_option_1 = (SELECT existing.id FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_13 AND existing.`option_value` <=> '0');

-- rule_variable_option: 2
INSERT INTO rule_variable_option (`variable_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_variable_13,'1','男性',1
WHERE NOT EXISTS (SELECT 1 FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_13 AND existing.`option_value` <=> '1');
SET @example_variable_option_2 = (SELECT existing.id FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_13 AND existing.`option_value` <=> '1');

-- rule_variable_option: 3
INSERT INTO rule_variable_option (`variable_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_variable_13,'-1','未知',2
WHERE NOT EXISTS (SELECT 1 FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_13 AND existing.`option_value` <=> '-1');
SET @example_variable_option_3 = (SELECT existing.id FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_13 AND existing.`option_value` <=> '-1');

-- rule_variable_option: 4
INSERT INTO rule_variable_option (`variable_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_variable_41,'0','女',0
WHERE NOT EXISTS (SELECT 1 FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_41 AND existing.`option_value` <=> '0');
SET @example_variable_option_4 = (SELECT existing.id FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_41 AND existing.`option_value` <=> '0');

-- rule_variable_option: 5
INSERT INTO rule_variable_option (`variable_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_variable_41,'1','男',1
WHERE NOT EXISTS (SELECT 1 FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_41 AND existing.`option_value` <=> '1');
SET @example_variable_option_5 = (SELECT existing.id FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_41 AND existing.`option_value` <=> '1');

-- rule_variable_option: 6
INSERT INTO rule_variable_option (`variable_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_variable_41,'-1','未知',2
WHERE NOT EXISTS (SELECT 1 FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_41 AND existing.`option_value` <=> '-1');
SET @example_variable_option_6 = (SELECT existing.id FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_41 AND existing.`option_value` <=> '-1');

-- rule_variable_option: 7
INSERT INTO rule_variable_option (`variable_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_variable_141,'0','通过',0
WHERE NOT EXISTS (SELECT 1 FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_141 AND existing.`option_value` <=> '0');
SET @example_variable_option_7 = (SELECT existing.id FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_141 AND existing.`option_value` <=> '0');

-- rule_variable_option: 8
INSERT INTO rule_variable_option (`variable_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_variable_141,'1','拒绝',1
WHERE NOT EXISTS (SELECT 1 FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_141 AND existing.`option_value` <=> '1');
SET @example_variable_option_8 = (SELECT existing.id FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_141 AND existing.`option_value` <=> '1');

-- rule_variable_option: 9
INSERT INTO rule_variable_option (`variable_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_variable_141,'2','转人工',2
WHERE NOT EXISTS (SELECT 1 FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_141 AND existing.`option_value` <=> '2');
SET @example_variable_option_9 = (SELECT existing.id FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_141 AND existing.`option_value` <=> '2');

-- rule_variable_option: 10
INSERT INTO rule_variable_option (`variable_id`,`option_value`,`option_label`,`sort_order`)
SELECT @example_variable_141,'-1','异常',3
WHERE NOT EXISTS (SELECT 1 FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_141 AND existing.`option_value` <=> '-1');
SET @example_variable_option_10 = (SELECT existing.id FROM rule_variable_option existing WHERE existing.`variable_id` <=> @example_variable_141 AND existing.`option_value` <=> '-1');

-- rule_billing_config: 1
INSERT INTO rule_billing_config (`project_id`,`scope`,`billing_code`,`billing_name`,`billing_target`,`target_ref_id`,`charge_type`,`unit_price`,`currency`,`effective_time`,`expire_time`,`description`,`status`,`create_time`,`update_time`)
SELECT 0,'GLOBAL','demo_engine_call','示例引擎调用','ENGINE',NULL,'COUNT',0.010000,'CNY',NULL,NULL,'样例计费配置，实际部署前请按业务资费调整。',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_billing_config existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`billing_code` <=> 'demo_engine_call');
SET @example_billing_config_1 = (SELECT existing.id FROM rule_billing_config existing WHERE existing.`scope` <=> 'GLOBAL' AND existing.`project_id` <=> 0 AND existing.`billing_code` <=> 'demo_engine_call');

-- 兼容旧版 initial-data.sql：只有所有原始种子字段和配置均未改变时才修正失配引用。
UPDATE rule_variable
SET source_config = JSON_SET('{"listIds":[1],"queryOperands":[{"kind":"REFERENCE","refType":"VARIABLE","refId":7,"code":"mobile_no","label":"手机号","valueType":"STRING"}],"combinationMode":"ANY_FIELD_ANY_LIST","matchMode":"IN_LIST","itemTypes":["MOBILE","ID_CARD","ADDRESS","IP"],"returnMode":"NUMBER"}', '$."listIds"[0]', CAST(@example_list_library_1 AS UNSIGNED), '$."queryOperands"[0]."refId"', CAST(@example_variable_7 AS UNSIGNED)), update_time = update_time
WHERE id = @example_variable_194
  AND `project_id` <=> 0
  AND `scope` <=> 'GLOBAL'
  AND `var_code` <=> 'black_hit'
  AND `var_label` <=> '是否命中黑名单'
  AND `script_name` <=> 'black_hit'
  AND `var_type` <=> 'NUMBER'
  AND `var_source` <=> 'LIST'
  AND `default_value` <=> '-1'
  AND `value_range` <=> ''
  AND `example_value` <=> ''
  AND `description` <=> ''
  AND `sort_order` <=> 0
  AND `status` <=> 1
  AND `create_time` <=> '2026-07-04 03:35:32'
  AND `update_time` <=> '2026-07-04 03:35:32'
  AND source_config = CAST('{"listId": 1, "itemTypes": ["MOBILE", "ID_CARD", "ADDRESS", "IP"], "matchMode": "IN_LIST", "queryField": "mobile_no", "returnMode": "NUMBER", "forceRefresh": false}' AS JSON);

UPDATE rule_variable
SET source_config = JSON_SET('{"resultPath": "body", "apiConfigId": 1, "forceRefresh": false, "fallbackValue": null, "exceptionStrategy": "ERROR"}', '$."apiConfigId"', CAST(@example_external_api_config_1 AS UNSIGNED)), update_time = update_time
WHERE id = @example_variable_195
  AND `project_id` <=> 0
  AND `scope` <=> 'GLOBAL'
  AND `var_code` <=> 'engine_bdrules'
  AND `var_label` <=> '引擎黑规则结果'
  AND `script_name` <=> 'engine_bdrules'
  AND `var_type` <=> 'LIST'
  AND `var_source` <=> 'API'
  AND `default_value` <=> ''
  AND `value_range` <=> ''
  AND `example_value` <=> ''
  AND `description` <=> ''
  AND `sort_order` <=> 0
  AND `status` <=> 1
  AND `create_time` <=> '2026-07-04 03:51:57'
  AND `update_time` <=> '2026-07-04 03:51:57'
  AND source_config = CAST('{"resultPath": "body", "apiConfigId": 1, "forceRefresh": false, "fallbackValue": null, "exceptionStrategy": "ERROR"}' AS JSON);

UPDATE rule_variable
SET source_config = JSON_SET('{"sql": "SELECT count(1) user_count\\r\\nFROM rule_list_record AS rlr\\r\\nWHERE list_id = 1 and item_content = ?", "params": [{"kind":"REFERENCE","refType":"VARIABLE","refId":7,"code":"mobile_no"}], "maxRows": 1, "resultPath": "0.user_count", "datasourceId": 1, "forceRefresh": false, "fallbackValue": null, "exceptionStrategy": "ERROR"}', '$."params"[0]."refId"', CAST(@example_variable_7 AS UNSIGNED), '$."datasourceId"', CAST(@example_db_datasource_1 AS UNSIGNED), '$.sql', CONCAT('SELECT count(1) user_count\r\nFROM rule_list_record AS rlr\r\nWHERE list_id = ', CAST(@example_list_library_1 AS CHAR), ' and item_content = ?')), update_time = update_time
WHERE id = @example_variable_196
  AND `project_id` <=> 0
  AND `scope` <=> 'GLOBAL'
  AND `var_code` <=> 'black_user_count'
  AND `var_label` <=> '黑名单人数'
  AND `script_name` <=> 'black_user_count'
  AND `var_type` <=> 'NUMBER'
  AND `var_source` <=> 'DB'
  AND `default_value` <=> ''
  AND `value_range` <=> ''
  AND `example_value` <=> ''
  AND `description` <=> ''
  AND `sort_order` <=> 0
  AND `status` <=> 1
  AND `create_time` <=> '2026-07-04 04:05:12'
  AND `update_time` <=> '2026-07-07 02:42:18'
  AND source_config = CAST('{"sql": "SELECT count(1) user_count\\r\\nFROM rule_list_record AS rlr\\r\\nWHERE list_id = 1 and item_content = ?", "params": [{"kind":"REFERENCE","refType":"VARIABLE","refId":7,"code":"mobile_no"}], "maxRows": 1, "resultPath": "0.user_count", "datasourceId": 1, "forceRefresh": false, "fallbackValue": null, "exceptionStrategy": "ERROR"}' AS JSON);

-- 分流实验使用当前库中的真实项目与规则 ID。
INSERT INTO rule_experiment (project_id,project_code,experiment_code,experiment_name,description,routing_mode,test_routing_mode,condition_rule_code,request_key_path,test_exclusive,status,create_time,update_time)
SELECT @example_project_1,'BNLP','EXP_DEMO_CHAMPION','演示-冠军挑战分流','演示冠军组与挑战组的规则分流。','RATIO','CONDITION',NULL,'requestId',0,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_experiment WHERE experiment_code='EXP_DEMO_CHAMPION');
SET @example_experiment_1 = (SELECT id FROM rule_experiment WHERE experiment_code='EXP_DEMO_CHAMPION');
INSERT INTO rule_experiment_group (experiment_id,group_code,group_name,group_type,rule_id,rule_code,version_mode,traffic_ratio,condition_value,condition_expression,invoke_external_source,status,sort_order,create_time,update_time)
SELECT @example_experiment_1,'champion','冠军组-黑名单规则','CHAMPION',@example_definition_2,'BDZR','LATEST',70.0000,'champion',NULL,1,1,0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_experiment_group WHERE experiment_id=@example_experiment_1 AND group_code='champion');
INSERT INTO rule_experiment_group (experiment_id,group_code,group_name,group_type,rule_id,rule_code,version_mode,traffic_ratio,condition_value,condition_expression,invoke_external_source,status,sort_order,create_time,update_time)
SELECT @example_experiment_1,'challenger','挑战组-数据库规则','CHALLENGER',@example_definition_4,'DBQUERY','LATEST',30.0000,'challenger',NULL,1,1,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM rule_experiment_group WHERE experiment_id=@example_experiment_1 AND group_code='challenger');

COMMIT;
DO RELEASE_LOCK(@example_init_lock);
