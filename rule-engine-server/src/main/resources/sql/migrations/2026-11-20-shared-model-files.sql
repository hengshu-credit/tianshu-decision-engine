CREATE TABLE IF NOT EXISTS `rule_file_object` (
  `digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '解压还原后原始文件的 SHA-256，是全库内容身份',
  `byte_size` BIGINT NOT NULL COMMENT '原始文件字节数',
  `encoding` VARCHAR(16) NOT NULL COMMENT '存储编码：DEFLATE 为无损压缩原件，IDENTITY 为保持原字节的制品',
  `stored_size` BIGINT NOT NULL DEFAULT 0 COMMENT '组成文件的分块字节总数，复用块也计入',
  `part_count` INT NOT NULL DEFAULT 0 COMMENT '还原文件所需载荷数，常规模型原件为一条',
  PRIMARY KEY (`digest`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按原始文件摘要去重的共享文件目录';

CREATE TABLE IF NOT EXISTS `rule_file_chunk` (
  `digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本块实际存储字节的 SHA-256',
  `content` LONGBLOB NOT NULL COMMENT '完整压缩原件或制品载荷，超出服务端单包上限时保存必要的分段',
  PRIMARY KEY (`digest`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型原件与制品共用的不可变二进制载荷';

CREATE TABLE IF NOT EXISTS `rule_file_object_part` (
  `object_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属共享文件的原始 SHA-256',
  `part_no` INT NOT NULL COMMENT '分块在文件中的顺序，从零开始',
  `chunk_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前顺序对应的数据块摘要',
  PRIMARY KEY (`object_digest`,`part_no`),
  KEY `idx_file_part_chunk` (`chunk_digest`),
  CONSTRAINT `fk_file_part_object` FOREIGN KEY (`object_digest`) REFERENCES `rule_file_object` (`digest`) ON DELETE CASCADE,
  CONSTRAINT `fk_file_part_chunk` FOREIGN KEY (`chunk_digest`) REFERENCES `rule_file_chunk` (`digest`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共享文件的有序分块清单';

CREATE TABLE IF NOT EXISTS `rule_file_reference` (
  `file_ref` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '已授权写入的项目原件或制品存储标识，区分大小写',
  `object_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '标识指向的共享文件 SHA-256',
  PRIMARY KEY (`file_ref`),
  KEY `idx_file_reference_object` (`object_digest`),
  CONSTRAINT `fk_file_reference_object` FOREIGN KEY (`object_digest`) REFERENCES `rule_file_object` (`digest`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务存储标识到去重内容的不可变绑定';
-- 兼容曾以 MEDIUMBLOB 建立本表的安装；已是 LONGBLOB 的库跳过 DDL。
SET @file_payload_upgrade = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='rule_file_chunk' AND column_name='content' AND data_type='mediumblob'),
  'ALTER TABLE rule_file_chunk MODIFY COLUMN content LONGBLOB NOT NULL COMMENT ''完整压缩原件或制品载荷，超出服务端单包上限时保存必要的分段''',
  'DO 0');
PREPARE file_payload_upgrade_stmt FROM @file_payload_upgrade;
EXECUTE file_payload_upgrade_stmt;
DEALLOCATE PREPARE file_payload_upgrade_stmt;
