-- 先执行 2026-11-26-engine-sftp.sql。已有上传账户继续按原权限读写。
SET @sftp_mode_sql = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='engine_sftp_account' AND column_name='access_mode'), 'SELECT 1', 'ALTER TABLE engine_sftp_account ADD COLUMN `access_mode` VARCHAR(16) NOT NULL DEFAULT ''READ_WRITE'' COMMENT ''上传账户文件权限，READ_WRITE 可读写、READ_ONLY 只读、SUSPENDED 暂停登录''');
PREPARE sftp_mode_change FROM @sftp_mode_sql;
EXECUTE sftp_mode_change;
DEALLOCATE PREPARE sftp_mode_change;

CREATE TABLE IF NOT EXISTS `engine_sftp_policy` (
  `policy_id` INT NOT NULL COMMENT '引擎 SFTP 全局策略标识，固定为 1',
  `ip_attempts_per_minute` INT NOT NULL DEFAULT 120 COMMENT '同一来源 IP 每分钟允许的 SSH 认证请求数',
  `file_requests_per_second` INT NOT NULL DEFAULT 256 COMMENT '每个上传账户每秒允许的文件数据读写请求合计数',
  `failure_threshold` INT NOT NULL DEFAULT 8 COMMENT '认证失败窗口内触发临时封禁的次数',
  `failure_window_seconds` INT NOT NULL DEFAULT 300 COMMENT '统计认证失败的窗口时长，单位秒',
  `ban_seconds` INT NOT NULL DEFAULT 900 COMMENT '达到认证失败阈值后的封禁时长，单位秒',
  `revision` BIGINT NOT NULL DEFAULT 0 COMMENT '并发修改策略时校验的版本号',
  PRIMARY KEY (`policy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='独立引擎 SFTP 的认证和文件请求限流策略';

CREATE TABLE IF NOT EXISTS `engine_sftp_auth_state` (
  `subject_key` VARCHAR(96) NOT NULL COMMENT '防护对象键，区分来源 IP、上传账户 ID、引擎服务身份及短期 SSH 会话封禁回执',
  `attempt_start_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '来源认证请求计数窗口的开始时间戳，毫秒',
  `attempts` INT NOT NULL DEFAULT 0 COMMENT '当前来源窗口内的认证请求数',
  `failure_start_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '失败统计窗口的开始时间戳，毫秒',
  `failures` INT NOT NULL DEFAULT 0 COMMENT '当前窗口累计的认证失败次数，账户完整登录成功后清零',
  `blocked_until_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '允许再次认证的时间戳，毫秒，零表示未封禁',
  `block_reason` VARCHAR(16) NOT NULL DEFAULT '' COMMENT '封禁原因，RATE 为来源频率超限，FAILURE 为认证失败超限',
  `last_seen_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '最近一次更新防护状态的时间戳，毫秒',
  PRIMARY KEY (`subject_key`),
  KEY `idx_sftp_block_expiry` (`blocked_until_ms`),
  KEY `idx_sftp_auth_seen` (`last_seen_ms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨 SSH 连接持久化的 SFTP 认证计数与临时封禁状态';
