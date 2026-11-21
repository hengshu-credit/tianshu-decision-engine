CREATE TABLE IF NOT EXISTS `console_user_session_state` (
  `user_id` BIGINT NOT NULL COMMENT '会话所属控制台账户的稳定标识',
  `latest_sequence` BIGINT NOT NULL COMMENT '该账户最近一次成功登录的会话序号，零表示尚未签发',
  `minimum_sequence` BIGINT NOT NULL COMMENT '仍允许访问控制台的最小会话序号，零兼容升级前的登录',
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_console_session_state_user` FOREIGN KEY (`user_id`) REFERENCES `console_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制台账户登录会话有效范围';
