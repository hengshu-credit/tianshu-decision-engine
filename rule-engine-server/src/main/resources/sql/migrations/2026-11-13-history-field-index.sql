CREATE TABLE IF NOT EXISTS `rule_history_index_state` (
  `format_version` INT NOT NULL COMMENT '已完整回填并可用于统计的历史索引格式版本',
  `ready_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '历史索引完成初始化或回填的时间',
  PRIMARY KEY (`format_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史字段索引就绪记录，用于防止部分回填数据参与统计';

CREATE TABLE IF NOT EXISTS `rule_history_bucket` (
  `bucket_hour` DATETIME NOT NULL COMMENT '按根进件开始时间截整的本地小时',
  `execution_project_id` BIGINT NOT NULL COMMENT '根进件实际所属项目，无项目时为零',
  `root_rule_id` BIGINT NOT NULL COMMENT '根规则标识，历史无归属时为零',
  `field_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定字段引用类型及标识，空串表示该小时的根进件总量',
  `sample_count` BIGINT NOT NULL COMMENT '当前逻辑根记录数或包含该字段的根记录数',
  `numeric_count` BIGINT NOT NULL COMMENT '该字段具有有效数值的根记录数',
  `null_count` BIGINT NOT NULL COMMENT '该字段显式记录为空值的根记录数',
  `min_value` DOUBLE DEFAULT NULL COMMENT '该小时字段历史数值下界，逻辑日志修订后可能保守地偏小',
  `max_value` DOUBLE DEFAULT NULL COMMENT '该小时字段历史数值上界，逻辑日志修订后可能保守地偏大',
  PRIMARY KEY (`execution_project_id`,`bucket_hour`,`root_rule_id`,`field_key`),
  KEY `idx_history_bucket_global` (`field_key`,`bucket_hour`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史窗口按小时汇总的根记录数及稳定字段覆盖情况';

CREATE TABLE IF NOT EXISTS `rule_history_root` (
  `log_id` BIGINT NOT NULL COMMENT '根进件当前逻辑执行日志的物理行标识',
  `log_create_time` DATETIME NOT NULL COMMENT '根执行日志分区键，与日志标识共同确定唯一记录',
  `trace_id` CHAR(36) DEFAULT NULL COMMENT '根执行追踪号，用于关联同一进件的外数历史字段',
  `execution_project_id` BIGINT DEFAULT NULL COMMENT '执行时实际项目标识，用于历史统计范围和数据权限',
  `project_code` VARCHAR(128) DEFAULT NULL COMMENT '执行时项目编码快照，用于识别无项目归属的全局日志',
  `actor_user_id` BIGINT DEFAULT NULL COMMENT '执行操作的控制台账户标识，未知或非控制台执行时为空',
  `root_rule_id` BIGINT DEFAULT NULL COMMENT '实际执行的根规则标识，用于规则范围统计',
  `started_at` DATETIME(6) NOT NULL COMMENT '根进件开始时间，按此时间划分统计窗口',
  PRIMARY KEY (`log_id`,`log_create_time`),
  KEY `idx_history_project_window` (`execution_project_id`,`started_at`,`log_id`),
  KEY `idx_history_rule_window` (`execution_project_id`,`root_rule_id`,`started_at`),
  KEY `idx_history_global_window` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成功正式进件的历史统计索引，每个有效逻辑根日志一行';

CREATE TABLE IF NOT EXISTS `rule_history_numeric_field` (
  `log_id` BIGINT NOT NULL COMMENT '所属根进件逻辑执行日志的物理行标识',
  `log_create_time` DATETIME NOT NULL COMMENT '所属根日志的分区时间键',
  `field_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '历史字段稳定身份，由引用类型和资源标识组成',
  `numeric_value` DOUBLE DEFAULT NULL COMMENT '历史数值的双精度投影；显式空值或非数值时为空',
  `value_kind` CHAR(1) CHARACTER SET ascii NOT NULL COMMENT '值类型：N为数值，Z为显式空值，X为其他类型',
  `execution_project_id` BIGINT DEFAULT NULL COMMENT '根进件所属执行项目标识，用于按项目筛选数值范围',
  `started_at` DATETIME(6) NOT NULL COMMENT '根进件开始时间，用于过滤历史窗口',
  PRIMARY KEY (`log_id`,`log_create_time`,`field_key`),
  KEY `idx_history_value_project` (`execution_project_id`,`field_key`,`numeric_value`,`started_at`),
  KEY `idx_history_value_global` (`field_key`,`numeric_value`,`started_at`),
  KEY `idx_history_field_window` (`field_key`,`execution_project_id`,`started_at`,`value_kind`,`numeric_value`),
  CONSTRAINT `fk_history_numeric_root` FOREIGN KEY (`log_id`,`log_create_time`)
    REFERENCES `rule_history_root` (`log_id`,`log_create_time`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按稳定字段身份检索的进件快照投影，支持数值范围与地理统计';

-- 停止应用后执行。逻辑视图已按追踪号/尝试号选出当前代表行。
-- 大库可按日志分区时间拆批执行以下事务；所有适用批次完成后再启动应用。
START TRANSACTION;
INSERT INTO rule_history_root
    (log_id,log_create_time,trace_id,execution_project_id,project_code,actor_user_id,root_rule_id,started_at)
SELECT id,create_time,trace_id,execution_project_id,project_code,actor_user_id,root_rule_id,started_at
FROM rule_execution_log_current
WHERE success=1 AND source IN ('CLIENT_SERVER','EXPERIMENT_LIVE')
  AND started_at IS NOT NULL AND history_fields IS NOT NULL
ON DUPLICATE KEY UPDATE log_id=rule_history_root.log_id;

INSERT INTO rule_history_numeric_field
    (log_id,log_create_time,field_key,numeric_value,value_kind,execution_project_id,started_at)
SELECT v.log_id,v.log_create_time,v.field_key,
       CASE WHEN JSON_TYPE(v.val) IN ('INTEGER','DOUBLE','DECIMAL','UNSIGNED INTEGER') THEN CAST(v.val AS DOUBLE) END,
       CASE WHEN JSON_TYPE(v.val) IN ('INTEGER','DOUBLE','DECIMAL','UNSIGNED INTEGER') THEN 'N'
            WHEN JSON_TYPE(v.val)='NULL' THEN 'Z' ELSE 'X' END,v.execution_project_id,v.started_at
FROM (
    SELECT r.log_id,r.log_create_time,r.execution_project_id,r.started_at,k.field_key,
           JSON_EXTRACT(l.history_fields,CONCAT('$.fields.',JSON_QUOTE(k.field_key))) AS val
    FROM rule_history_root r JOIN rule_execution_log l ON l.id=r.log_id AND l.create_time=r.log_create_time
    JOIN JSON_TABLE(JSON_KEYS(l.history_fields,'$.fields'), '$[*]' COLUMNS(field_key VARCHAR(64) PATH '$')) k
    WHERE JSON_EXTRACT(l.history_fields,'$.version')=1 AND JSON_TYPE(JSON_EXTRACT(l.history_fields,'$.fields'))='OBJECT'
) v
ON DUPLICATE KEY UPDATE log_id=rule_history_numeric_field.log_id;
DELETE FROM rule_history_bucket;
INSERT INTO rule_history_bucket
    (bucket_hour,execution_project_id,root_rule_id,field_key,sample_count,numeric_count,null_count,min_value,max_value)
SELECT DATE_FORMAT(started_at,'%Y-%m-%d %H:00:00'),COALESCE(execution_project_id,0),COALESCE(root_rule_id,0),'',COUNT(*),0,0,NULL,NULL
FROM rule_history_root GROUP BY 1,2,3
UNION ALL
SELECT DATE_FORMAT(r.started_at,'%Y-%m-%d %H:00:00'),COALESCE(r.execution_project_id,0),COALESCE(r.root_rule_id,0),
       f.field_key,COUNT(*),SUM(f.value_kind='N'),SUM(f.value_kind='Z'),MIN(f.numeric_value),MAX(f.numeric_value)
FROM rule_history_root r JOIN rule_history_numeric_field f ON f.log_id=r.log_id AND f.log_create_time=r.log_create_time
GROUP BY 1,2,3,4;

INSERT INTO rule_history_index_state(format_version) VALUES (1)
ON DUPLICATE KEY UPDATE format_version=1;
COMMIT;
