CREATE TABLE IF NOT EXISTS `offline_transfer_task` (
  `task_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '后台迁移任务或临时上传文件的唯一标识',
  `session_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发起迁移的控制台会话摘要，用于隔离任务和临时文件',
  `operation_type` VARCHAR(24) NOT NULL COMMENT '迁移操作类型，包括上传、导出、普通包或项目合集预检和导入',
  `status` VARCHAR(16) NOT NULL COMMENT '任务状态，包括待上传、运行中、已成功和已失败',
  `file_size` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上传或导出的配置包总字节数',
  `result_json` LONGTEXT COMMENT '任务完成后的预检、导入结果或失败说明',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近上传、执行或完成时间，用于回收临时数据',
  PRIMARY KEY (`task_id`),
  KEY `idx_transfer_task_expiry` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制台后台迁移任务和临时文件登记';

CREATE TABLE IF NOT EXISTS `offline_transfer_part` (
  `task_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属上传文件或导出任务标识',
  `part_index` INT UNSIGNED NOT NULL COMMENT '文件分块序号，从零开始连续排列',
  `content` MEDIUMBLOB NOT NULL COMMENT '该序号对应的原始配置包字节',
  PRIMARY KEY (`task_id`, `part_index`),
  CONSTRAINT `fk_transfer_part_task` FOREIGN KEY (`task_id`) REFERENCES `offline_transfer_task` (`task_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨服务节点共享的临时迁移文件分块';

CREATE TABLE IF NOT EXISTS `offline_transfer_action` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '执行动作的顺序标识，用于按游标读取新增日志',
  `task_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '执行此动作的迁移任务标识',
  `progress` TINYINT UNSIGNED NOT NULL COMMENT '进入该执行步骤时的服务端进度百分比',
  `message` TEXT NOT NULL COMMENT '当前正在处理的项目、资源或迁移步骤说明',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '服务端记录该执行动作的时间',
  PRIMARY KEY (`id`),
  KEY `idx_transfer_action_task` (`task_id`, `id`),
  CONSTRAINT `fk_transfer_action_task` FOREIGN KEY (`task_id`) REFERENCES `offline_transfer_task` (`task_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='迁移任务按执行顺序记录的进度明细';
