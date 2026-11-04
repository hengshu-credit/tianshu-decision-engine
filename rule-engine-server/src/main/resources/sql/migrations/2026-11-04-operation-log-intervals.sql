-- 已有数据库的操作日志区间升级；保留历史记录，不推测历史离开时间。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `console_operation_log_lock` (
  `user_id` BIGINT NOT NULL COMMENT '控制台账户标识',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志按账户串行追加锁';

SET @operation_leave_sql = IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'console_operation_log' AND column_name = 'leave_time'),
  'SELECT 1',
  'ALTER TABLE console_operation_log ADD COLUMN leave_time DATETIME(6) DEFAULT NULL COMMENT ''连续相同操作最后一次完成的时间'''
);
PREPARE operation_leave_stmt FROM @operation_leave_sql;
EXECUTE operation_leave_stmt;
DEALLOCATE PREPARE operation_leave_stmt;

SET @operation_latest_sql = IF(
  EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'console_operation_log' AND index_name = 'idx_console_operation_user_last'),
  'SELECT 1',
  'ALTER TABLE console_operation_log ADD INDEX idx_console_operation_user_last (user_id, id)'
);
PREPARE operation_latest_stmt FROM @operation_latest_sql;
EXECUTE operation_latest_stmt;
DEALLOCATE PREPARE operation_latest_stmt;
