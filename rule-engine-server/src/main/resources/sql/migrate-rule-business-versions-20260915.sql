-- Additive business-version migration; historical scripts and IDs are unchanged.
CREATE TABLE IF NOT EXISTS `rule_engine`.`rule_version_binding` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `definition_id` BIGINT NOT NULL,
  `version_no` INT NOT NULL,
  `generation` BIGINT NOT NULL DEFAULT 0,
  `snapshot_id` BIGINT DEFAULT NULL,
  `status` INT NOT NULL DEFAULT 1,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_business_version` (`definition_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP PROCEDURE IF EXISTS `rule_engine`.`ensure_rule_business_versions`;
DELIMITER $$
CREATE PROCEDURE `rule_engine`.`ensure_rule_business_versions`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='rule_engine' AND TABLE_NAME='rule_revision' AND COLUMN_NAME='publish_mode') THEN
    ALTER TABLE `rule_engine`.`rule_revision` ADD COLUMN `publish_mode` VARCHAR(16) DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='rule_engine' AND TABLE_NAME='rule_revision' AND COLUMN_NAME='target_version_id') THEN
    ALTER TABLE `rule_engine`.`rule_revision` ADD COLUMN `target_version_id` BIGINT DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='rule_engine' AND TABLE_NAME='rule_revision' AND COLUMN_NAME='target_generation') THEN
    ALTER TABLE `rule_engine`.`rule_revision` ADD COLUMN `target_generation` BIGINT DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='rule_engine' AND TABLE_NAME='rule_definition_version' AND COLUMN_NAME='business_version') THEN
    ALTER TABLE `rule_engine`.`rule_definition_version` ADD COLUMN `business_version` INT DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='rule_engine' AND TABLE_NAME='rule_definition_version' AND COLUMN_NAME='version_binding_id') THEN
    ALTER TABLE `rule_engine`.`rule_definition_version` ADD COLUMN `version_binding_id` BIGINT DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='rule_engine' AND TABLE_NAME='rule_definition_version' AND COLUMN_NAME='binding_generation') THEN
    ALTER TABLE `rule_engine`.`rule_definition_version` ADD COLUMN `binding_generation` BIGINT DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='rule_engine' AND TABLE_NAME='rule_definition_version' AND COLUMN_NAME='revision_id') THEN
    ALTER TABLE `rule_engine`.`rule_definition_version` ADD COLUMN `revision_id` BIGINT DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='rule_engine' AND TABLE_NAME='rule_definition_version' AND COLUMN_NAME='artifact_id') THEN
    ALTER TABLE `rule_engine`.`rule_definition_version` ADD COLUMN `artifact_id` BIGINT DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='rule_engine' AND TABLE_NAME='rule_definition_version' AND COLUMN_NAME='artifact_digest') THEN
    ALTER TABLE `rule_engine`.`rule_definition_version` ADD COLUMN `artifact_digest` CHAR(64) DEFAULT NULL;
  END IF;
END$$
DELIMITER ;
CALL `rule_engine`.`ensure_rule_business_versions`();
DROP PROCEDURE `rule_engine`.`ensure_rule_business_versions`;

INSERT INTO `rule_engine`.`rule_version_binding` (`definition_id`,`version_no`,`generation`,`snapshot_id`,`status`)
SELECT v.definition_id, COALESCE(v.business_version,v.version), COALESCE(v.binding_generation,1), v.id, 1
FROM `rule_engine`.`rule_definition_version` v
JOIN (SELECT definition_id,COALESCE(business_version,version) business_version,MAX(version) seq FROM `rule_engine`.`rule_definition_version` GROUP BY definition_id,COALESCE(business_version,version)) latest
ON latest.definition_id=v.definition_id AND latest.seq=v.version
WHERE NOT EXISTS (SELECT 1 FROM `rule_engine`.`rule_version_binding` b WHERE b.definition_id=v.definition_id AND b.version_no=COALESCE(v.business_version,v.version));

