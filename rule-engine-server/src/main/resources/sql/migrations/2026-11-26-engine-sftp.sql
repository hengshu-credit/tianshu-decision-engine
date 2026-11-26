CREATE TABLE IF NOT EXISTS `engine_sftp_account` (
  `account_id` BIGINT NOT NULL COMMENT '已开通内置 SFTP 的控制台账户 ID',
  `key_fingerprint` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '获准登录该账户的 SSH 公钥 SHA-256 指纹',
  PRIMARY KEY (`account_id`),
  CONSTRAINT `fk_engine_sftp_account_user` FOREIGN KEY (`account_id`) REFERENCES `console_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内置 SFTP 上传账户公钥授权';

CREATE TABLE IF NOT EXISTS `rule_ledger_asset_file` (
  `asset_id` BIGINT NOT NULL COMMENT '通过扫描并重新编码的台账素材 ID',
  `png_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '共享文件仓中规范化 PNG 内容的 SHA-256 摘要',
  PRIMARY KEY (`asset_id`),
  CONSTRAINT `fk_ledger_asset_file_asset` FOREIGN KEY (`asset_id`) REFERENCES `rule_ledger_asset` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='台账素材与共享文件仓中不可变图片的关联';
