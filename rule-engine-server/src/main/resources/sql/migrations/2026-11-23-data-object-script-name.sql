-- 整对象脚本引用沿用已有运行时默认值，仅补齐空名称，保留原业务更新时间。
UPDATE rule_data_object
SET script_name = object_code, update_time = update_time
WHERE (script_name IS NULL OR script_name REGEXP '^[[:space:]]*$')
  AND object_code IS NOT NULL AND CHAR_LENGTH(TRIM(object_code)) > 0;
