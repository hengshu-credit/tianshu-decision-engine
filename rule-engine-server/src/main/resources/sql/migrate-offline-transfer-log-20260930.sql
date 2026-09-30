CREATE TABLE IF NOT EXISTS `rule_engine`.`offline_transfer_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `operation_type` VARCHAR(16) NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `operator` VARCHAR(128) DEFAULT NULL,
  `package_digest` CHAR(64) DEFAULT NULL,
  `roots_json` LONGTEXT NOT NULL,
  `resource_count` INT NOT NULL DEFAULT 0,
  `content_json` LONGTEXT DEFAULT NULL,
  `error_message` VARCHAR(2000) DEFAULT NULL,
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_offline_transfer_log_time` (`create_time`),
  KEY `idx_offline_transfer_log_operation` (`operation_type`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
