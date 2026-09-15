-- 存量库无损升级；不删除或改写规则、审批、发布数据。可重复执行。
CREATE TABLE IF NOT EXISTS `rule_engine`.`rule_designer_save_operation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `definition_id` BIGINT NOT NULL,
  `request_id` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `request_digest` CHAR(64) NOT NULL,
  `response_json` LONGTEXT NOT NULL,
  `create_by` VARCHAR(64) NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_designer_save_request` (`definition_id`, `request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Designer save idempotency and original response';

DROP PROCEDURE IF EXISTS `rule_engine`.`ensure_designer_draft_columns`;
DELIMITER $$
CREATE PROCEDURE `rule_engine`.`ensure_designer_draft_columns`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'rule_engine' AND TABLE_NAME = 'rule_revision' AND COLUMN_NAME = 'source_type') THEN
    ALTER TABLE `rule_engine`.`rule_revision` ADD COLUMN `source_type` VARCHAR(16) DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'rule_engine' AND TABLE_NAME = 'rule_revision' AND COLUMN_NAME = 'source_id') THEN
    ALTER TABLE `rule_engine`.`rule_revision` ADD COLUMN `source_id` BIGINT DEFAULT NULL;
  END IF;
END$$
DELIMITER ;
CALL `rule_engine`.`ensure_designer_draft_columns`();
DROP PROCEDURE `rule_engine`.`ensure_designer_draft_columns`;
