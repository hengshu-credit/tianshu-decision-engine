CREATE TABLE IF NOT EXISTS `console_user_login_activity` (
  `user_id` BIGINT NOT NULL COMMENT '登录账户的稳定ID，关联控制台账户',
  `last_login_time` DATETIME NOT NULL COMMENT '该账户最近一次成功登录时间',
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_console_login_activity_user` FOREIGN KEY (`user_id`) REFERENCES `console_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制台账户登录活动表';
