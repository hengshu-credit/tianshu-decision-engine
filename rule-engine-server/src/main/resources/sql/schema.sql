SET NAMES utf8mb4;
SET character_set_connection = utf8mb4;

-- 天枢决策引擎完整数据库结构。
-- MySQL Connector/J 通过 createDatabaseIfNotExist=true 确保 MYSQL_DATABASE 存在；本文件只依赖当前连接的数据库，不创建或切换数据库。
-- 所有表显式使用 utf8mb4；外部数据库账号需要具备目标库的建库（首次部署）和建表/变更权限。

CREATE TABLE IF NOT EXISTS `rule_project` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_code` VARCHAR(64)  NOT NULL                COMMENT '项目编码',
  `trace_scope_code` CHAR(4)  DEFAULT NULL            COMMENT '四位三十六进制项目追踪作用域码，由项目标识生成',
  `project_name` VARCHAR(128) NOT NULL                COMMENT '项目名称（中文）',
  `description`  VARCHAR(512) DEFAULT NULL             COMMENT '项目描述',
  `status`       TINYINT      NOT NULL DEFAULT 1       COMMENT '资源状态：-1=已逻辑删除，0=停用，1=启用',
  `access_token` VARCHAR(64)  DEFAULT NULL             COMMENT '访问令牌',
  `create_by`    VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    VARCHAR(64)  DEFAULT NULL             COMMENT '更新人',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_code` (`project_code`),
  UNIQUE KEY `uk_project_trace_scope` (`trace_scope_code`),
  UNIQUE KEY `uk_access_token` (`access_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则项目表';

CREATE TABLE IF NOT EXISTS `rule_project_auth` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`            BIGINT       NOT NULL                COMMENT '所属项目ID',
  `auth_code`             VARCHAR(128) NOT NULL                COMMENT '鉴权配置编码',
  `auth_name`             VARCHAR(128) NOT NULL                COMMENT '鉴权配置名称',
  `auth_type`             VARCHAR(32)  NOT NULL                COMMENT '项目凭据类型：LEGACY_TOKEN=访问令牌，API_KEY=接口密钥，BASIC=账号密码，HMAC_SHA256=摘要签名',
  `lookup_key`            CHAR(64)     NOT NULL                COMMENT '凭据定位摘要',
  `identifier_ciphertext` TEXT         DEFAULT NULL            COMMENT '凭据标识密文',
  `secret_ciphertext`     TEXT         NOT NULL                COMMENT '凭据密文',
  `config_json`           JSON         DEFAULT NULL            COMMENT '非敏感鉴权配置',
  `access_policy_json`    JSON         DEFAULT NULL            COMMENT 'IP/Host、QPS、并发和总超时策略',
  `async_access_log_enabled` TINYINT   NOT NULL DEFAULT 1      COMMENT '是否异步记录访问日志：0=否，1=是',
  `token_ttl_seconds`     INT          NOT NULL DEFAULT 7200   COMMENT '临时令牌有效秒数',
  `token_grace_seconds`   INT          NOT NULL DEFAULT 600    COMMENT '临时令牌宽限秒数',
  `status`                TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0-停用，1-启用',
  `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_auth_code` (`auth_code`),
  UNIQUE KEY `uk_project_auth_lookup` (`lookup_key`),
  KEY `idx_project_auth_project` (`project_id`, `status`),
  KEY `idx_project_auth_type` (`auth_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目鉴权配置表';

CREATE TABLE IF NOT EXISTS `rule_project_auth_token` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`        BIGINT       NOT NULL                COMMENT '所属项目ID',
  `auth_id`           BIGINT       NOT NULL                COMMENT '来源鉴权配置ID',
  `token_code`        VARCHAR(128) NOT NULL                COMMENT '令牌展示编码',
  `lookup_key`        CHAR(64)     NOT NULL                COMMENT '令牌定位摘要',
  `token_ciphertext`  TEXT         NOT NULL                COMMENT '令牌密文',
  `issued_time`       DATETIME     NOT NULL                COMMENT '签发时间',
  `expire_time`       DATETIME     NOT NULL                COMMENT '正常到期时间',
  `grace_expire_time` DATETIME     NOT NULL                COMMENT '宽限截止时间',
  `last_used_time`    DATETIME     DEFAULT NULL            COMMENT '最后使用时间',
  `revoked_time`      DATETIME     DEFAULT NULL            COMMENT '撤销时间',
  `status`            TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0-撤销，1-有效',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_auth_token_code` (`token_code`),
  UNIQUE KEY `uk_project_auth_token_lookup` (`lookup_key`),
  KEY `idx_project_auth_token_auth` (`auth_id`, `status`),
  KEY `idx_project_auth_token_project` (`project_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目临时访问Token表';

CREATE TABLE IF NOT EXISTS `rule_auth_access_log` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`     BIGINT        DEFAULT NULL            COMMENT '项目ID',
  `project_code`   VARCHAR(128)  DEFAULT NULL            COMMENT '项目编码快照',
  `auth_id`        BIGINT        DEFAULT NULL            COMMENT '鉴权配置ID',
  `auth_code`      VARCHAR(128)  DEFAULT NULL            COMMENT '鉴权配置编码快照',
  `auth_type`      VARCHAR(32)   DEFAULT NULL            COMMENT '鉴权类型快照：LEGACY_TOKEN=项目访问令牌，API_KEY=接口密钥，BASIC=账号密码，HMAC_SHA256=摘要签名，BEARER_TOKEN=临时访问令牌，GLOBAL_KEY=全局公私钥签名',
  `token_id`       BIGINT        DEFAULT NULL            COMMENT '临时令牌 ID',
  `token_code`     VARCHAR(128)  DEFAULT NULL            COMMENT '临时令牌编码快照',
  `auth_phase`     VARCHAR(16)   DEFAULT NULL            COMMENT '鉴权阶段：DIRECT=长期凭据直接认证，VALID=临时令牌有效期，GRACE=临时令牌宽限期',
  `request_method` VARCHAR(16)   DEFAULT NULL            COMMENT '请求方法：GET=读取，POST=提交，PUT=整体更新，PATCH=局部更新，DELETE=删除，HEAD=仅响应头，OPTIONS=能力查询',
  `request_uri`    VARCHAR(1024) DEFAULT NULL            COMMENT '请求路径',
  `request_id`     VARCHAR(128)  DEFAULT NULL            COMMENT '请求ID',
  `client_ip`      VARCHAR(64)   DEFAULT NULL            COMMENT '客户端IP',
  `success`        TINYINT       NOT NULL DEFAULT 1      COMMENT '执行是否成功：0=否，1=是',
  `failure_reason` VARCHAR(512)  DEFAULT NULL            COMMENT '失败原因',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_auth_access_project_time` (`project_id`, `create_time`),
  KEY `idx_auth_access_auth_time` (`auth_id`, `create_time`),
  KEY `idx_auth_access_token_time` (`token_id`, `create_time`),
  KEY `idx_auth_access_success_time` (`success`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目鉴权访问日志表';

-- 串行化低频账户/角色管理及凭证状态转换，保护邮箱唯一性和最后管理员。
CREATE TABLE IF NOT EXISTS `console_security_lock` (
  `id` TINYINT NOT NULL PRIMARY KEY COMMENT '账户安全全局互斥锁标识，固定为 1',
  `initialized_at` DATETIME DEFAULT NULL COMMENT '首次初始化完成时间，不能因重启或账户停用清除',
  `administrator_user_id` BIGINT DEFAULT NULL COMMENT '超管恢复目标，关联稳定账户ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制台账户安全与初始化互斥锁表';

CREATE TABLE IF NOT EXISTS `console_user` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username`           VARCHAR(64)  NOT NULL                COMMENT '登录用户名',
  `operator_name`      VARCHAR(64)  DEFAULT NULL             COMMENT '固定业务归属标识，恢复改名时保留',
  `merged_into_user_id` BIGINT      DEFAULT NULL             COMMENT '合并归档后的主账户ID',
  `merged_username`    VARCHAR(64)  DEFAULT NULL             COMMENT '合并前登录名，保留给主账户恢复使用',
  `display_name`       VARCHAR(128) NOT NULL                COMMENT '显示名称',
  `password_hash`      VARCHAR(128) NOT NULL                COMMENT 'BCrypt密码摘要',
  `email`              VARCHAR(256) DEFAULT NULL             COMMENT '注册及找回密码邮箱',
  `email_verified_at`  DATETIME     DEFAULT NULL             COMMENT '邮箱验证时间',
  `registration_status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '注册状态：PENDING=待邮箱验证，ACTIVE=已激活，MERGED=已合并归档不可登录',
  `registration_source` VARCHAR(16) NOT NULL DEFAULT 'ADMIN' COMMENT '账户来源：ADMIN=管理员创建，SELF_REGISTER=用户自主注册',
  `status`             TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `permission_version` BIGINT       NOT NULL DEFAULT 1       COMMENT '权限版本号',
  `project_access_mode` VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT '项目访问模式：ALL=默认不限制项目，SELECTED=仅授权项目',
  `global_editable` TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许编辑全局内容：0=只读，1=可编辑；仍需对应模块权限',
  `last_login_time`    DATETIME     DEFAULT NULL             COMMENT '最后登录时间',
  `create_by`          VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
  `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          VARCHAR(64)  DEFAULT NULL             COMMENT '更新人',
  `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_console_user_username` (`username`),
  UNIQUE KEY `uk_console_user_operator_name` (`operator_name`),
  KEY `idx_console_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制台账户表';

CREATE TABLE IF NOT EXISTS `console_user_project` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `user_id` BIGINT NOT NULL COMMENT '控制台账户标识，关联 console_user.id',
  `project_id` BIGINT NOT NULL COMMENT '所属项目标识，关联 rule_project.id；0 表示全局资源',
  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人标识',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_console_user_project` (`user_id`, `project_id`),
  KEY `idx_console_project_user` (`project_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制台账户可访问项目；无分配仅能访问获授权的全局内容';

CREATE TABLE IF NOT EXISTS `console_account_token` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`      BIGINT       NOT NULL                COMMENT '控制台账户ID',
  `purpose`      VARCHAR(32)  NOT NULL                COMMENT '令牌用途：EMAIL_VERIFY=邮箱验证，PASSWORD_RESET=密码重置，ADMIN_RECOVERY=服务器签发的超管恢复码，REGISTRATION_VERIFY=自主注册激活验证',
  `token_hash`   CHAR(64)     NOT NULL                COMMENT '令牌SHA-256摘要',
  `target_email` VARCHAR(256) DEFAULT NULL            COMMENT '签发时的邮箱，防止链接验证其他邮箱',
  `expires_at`   DATETIME     NOT NULL                COMMENT '过期时间',
  `consumed_at`  DATETIME     DEFAULT NULL             COMMENT '消费时间',
  `request_ip`   VARCHAR(64)  DEFAULT NULL             COMMENT '请求IP',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_console_account_token_hash` (`token_hash`),
  KEY `idx_console_account_token_user_purpose` (`user_id`, `purpose`, `consumed_at`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制台账户邮箱验证及密码找回令牌';

CREATE TABLE IF NOT EXISTS `console_user_project_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键标识',
  `user_id` BIGINT NOT NULL COMMENT '控制台账户标识，关联 console_user.id',
  `project_id` BIGINT NOT NULL COMMENT '所属项目标识，关联 rule_project.id；0 表示全局资源',
  `permission_id` BIGINT NOT NULL COMMENT '功能权限标识，关联 console_permission.id',
  `effect` VARCHAR(8) NOT NULL COMMENT '项目权限效果：ALLOW=允许，DENY=拒绝；无记录时继承账户默认权限',
  UNIQUE KEY `uk_console_project_permission` (`user_id`, `project_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按项目覆盖账户模块权限';

CREATE TABLE IF NOT EXISTS `console_user_preference` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`          BIGINT      NOT NULL                COMMENT '控制台用户ID',
  `preference_key`   VARCHAR(64) NOT NULL                COMMENT '偏好键',
  `preference_value` JSON        NOT NULL                COMMENT '偏好JSON',
  `create_by`        VARCHAR(64) DEFAULT NULL            COMMENT '创建人',
  `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        VARCHAR(64) DEFAULT NULL            COMMENT '更新人',
  `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_console_user_preference` (`user_id`, `preference_key`),
  KEY `idx_console_user_preference_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制台用户偏好表';

CREATE TABLE IF NOT EXISTS `console_role` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_code`   VARCHAR(64)  NOT NULL                COMMENT '角色编码',
  `role_name`   VARCHAR(128) NOT NULL                COMMENT '角色名称',
  `description` VARCHAR(512) DEFAULT NULL             COMMENT '角色说明',
  `status`      TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `system_role` TINYINT      NOT NULL DEFAULT 0       COMMENT '是否系统内置角色：0=否，1=是',
  `create_by`   VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   VARCHAR(64)  DEFAULT NULL             COMMENT '更新人',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_console_role_code` (`role_code`),
  KEY `idx_console_role_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制台角色表';

CREATE TABLE IF NOT EXISTS `console_permission` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `permission_code`  VARCHAR(128) NOT NULL                COMMENT '权限编码',
  `permission_name`  VARCHAR(128) NOT NULL                COMMENT '权限名称',
  `permission_group` VARCHAR(64)  NOT NULL                COMMENT '权限分组',
  `permission_type`  VARCHAR(16)  NOT NULL                COMMENT '权限类型：MENU=菜单访问，ACTION=业务操作',
  `menu_path`        VARCHAR(256) DEFAULT NULL             COMMENT '菜单路径',
  `sort_order`       INT          NOT NULL DEFAULT 0       COMMENT '排序号',
  `status`           TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_console_permission_code` (`permission_code`),
  KEY `idx_console_permission_group` (`permission_group`, `permission_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制台功能权限表';

CREATE TABLE IF NOT EXISTS `console_user_role` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`     BIGINT      NOT NULL                COMMENT '账户ID',
  `role_id`     BIGINT      NOT NULL                COMMENT '角色ID',
  `create_by`   VARCHAR(64) DEFAULT NULL             COMMENT '创建人',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_console_user_role` (`user_id`, `role_id`),
  KEY `idx_console_user_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制台账户角色关系表';

CREATE TABLE IF NOT EXISTS `console_role_permission` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id`       BIGINT      NOT NULL                COMMENT '角色ID',
  `permission_id` BIGINT      NOT NULL                COMMENT '权限ID',
  `create_by`     VARCHAR(64) DEFAULT NULL             COMMENT '创建人',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_console_role_permission` (`role_id`, `permission_id`),
  KEY `idx_console_role_permission_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制台角色权限关系表';

CREATE TABLE IF NOT EXISTS `console_user_permission_override` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`       BIGINT      NOT NULL                COMMENT '账户ID',
  `permission_id` BIGINT      NOT NULL                COMMENT '权限ID',
  `effect` VARCHAR(8) NOT NULL COMMENT '账户权限覆盖效果：ALLOW=允许，DENY=拒绝',
  `create_by`     VARCHAR(64) DEFAULT NULL             COMMENT '创建人',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     VARCHAR(64) DEFAULT NULL             COMMENT '更新人',
  `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_console_user_permission_override` (`user_id`, `permission_id`),
  KEY `idx_console_user_permission_override_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制台账户权限覆盖表';

CREATE TABLE IF NOT EXISTS `console_security_audit_log` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`      BIGINT        DEFAULT NULL            COMMENT '账户ID',
  `username`     VARCHAR(64)   DEFAULT NULL            COMMENT '账户名快照',
  `action`       VARCHAR(64)   NOT NULL                COMMENT '安全操作编码：INITIALIZE_SUPER_ADMIN=初始化超管，LOGIN_SUCCESS=登录成功，REGISTER_REQUEST=注册申请，EMAIL_VERIFY=验证邮箱，PASSWORD_RESET_REQUEST=申请重置密码，PASSWORD_RESET_SUCCESS=密码重置成功，PASSWORD_CHANGE=修改密码，PROFILE_UPDATE=修改个人资料，ADMIN_RECOVERY_CODE_ISSUED=签发超管恢复码，SUPER_ADMIN_ACCOUNT_RECOVERED=超管恢复，ACCOUNT_CREATE=新增账户，ACCOUNT_UPDATE=修改账户，ACCOUNT_ENABLE=启用账户，ACCOUNT_DISABLE=停用账户，ACCOUNT_PASSWORD_RESET=管理员重置密码，ROLE_CREATE=新增角色，ROLE_UPDATE=修改角色，ROLE_ENABLE=启用角色，ROLE_DISABLE=停用角色，ACCOUNT_PERMISSION_UPDATE=调整账户授权',
  `target_type`  VARCHAR(64)   DEFAULT NULL            COMMENT '审计目标类型：CONSOLE_USER=控制台账户，CONSOLE_ROLE=控制台角色',
  `target_id`    VARCHAR(128)  DEFAULT NULL            COMMENT '目标ID',
  `details_json` LONGTEXT      DEFAULT NULL            COMMENT '操作详情JSON',
  `client_ip`    VARCHAR(64)   DEFAULT NULL            COMMENT '客户端IP',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_console_audit_user_time` (`user_id`, `create_time`),
  KEY `idx_console_audit_action_time` (`action`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制台安全审计日志表';

CREATE TABLE IF NOT EXISTS `governed_resource` (
  `id`                   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `resource_type`        VARCHAR(32) NOT NULL                COMMENT '治理资源类型：VARIABLE=变量，DATA_OBJECT=数据对象，MODEL=模型，EXTERNAL_DATASOURCE=外数数据源，EXTERNAL_API=外数接口，DATABASE=数据库，SFTP=文件数据源，FUNCTION=函数，RULE=规则，RULE_PROJECT_BINDING=规则项目关联，LIST_LIBRARY=名单库，LIST_RECORD_BATCH=名单变更批次，FIELD_VALIDATION=字段校验，BILLING_CONFIG=计费配置，EXPERIMENT=实验，PROJECT=项目，SYSTEM_SETTING=系统配置',
  `resource_id`          BIGINT      NOT NULL                COMMENT '业务资源ID',
  `project_id`           BIGINT      DEFAULT NULL            COMMENT '所属项目ID',
  `effective_version_id` BIGINT      DEFAULT NULL            COMMENT '当前生效治理版本ID',
  `effective_version_no` INT         NOT NULL DEFAULT 0       COMMENT '当前生效版本号',
  `effective_status`     VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '生效状态：ACTIVE=启用，DISABLED=停用，DELETED=已删除',
  `lock_version`         INT         NOT NULL DEFAULT 0       COMMENT '乐观锁版本',
  `create_time`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_governed_resource_identity` (`resource_type`, `resource_id`),
  KEY `idx_governed_resource_project` (`project_id`, `resource_type`, `effective_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一治理资源表';

CREATE TABLE IF NOT EXISTS `governed_resource_version` (
  `id`                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `governed_resource_id`      BIGINT       NOT NULL                COMMENT '治理资源ID',
  `resource_type`             VARCHAR(32)  NOT NULL                COMMENT '治理资源类型：VARIABLE=变量，DATA_OBJECT=数据对象，MODEL=模型，EXTERNAL_DATASOURCE=外数数据源，EXTERNAL_API=外数接口，DATABASE=数据库，SFTP=文件数据源，FUNCTION=函数，RULE=规则，RULE_PROJECT_BINDING=规则项目关联，LIST_LIBRARY=名单库，LIST_RECORD_BATCH=名单变更批次，FIELD_VALIDATION=字段校验，BILLING_CONFIG=计费配置，EXPERIMENT=实验，PROJECT=项目，SYSTEM_SETTING=系统配置',
  `resource_id`               BIGINT       NOT NULL                COMMENT '业务资源ID',
  `version_no`                INT          NOT NULL                COMMENT '版本号',
  `business_version`          INT          DEFAULT NULL            COMMENT '业务版本号，可复用；version_no为快照序号',
  `deleted`                   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已逻辑删除：0=未删除，1=已删除',
  `source_version_id`         BIGINT       DEFAULT NULL            COMMENT '历史恢复来源版本ID',
  `approval_request_id`       BIGINT       DEFAULT NULL            COMMENT '来源审批单ID',
  `snapshot_json`             LONGTEXT     NOT NULL                COMMENT '规范资源快照',
  `snapshot_digest`           CHAR(64)     NOT NULL                COMMENT '快照SHA-256摘要',
  `secret_payload_ciphertext` LONGTEXT     DEFAULT NULL            COMMENT '敏感字段AES-GCM密文',
  `secret_digest`             CHAR(64)     DEFAULT NULL            COMMENT '敏感字段摘要',
  `effective_status`          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '生效状态：ACTIVE=启用，DISABLED=停用，DELETED=已删除',
  `change_summary`            VARCHAR(512) DEFAULT NULL            COMMENT '变更摘要',
  `legacy_source_type`        VARCHAR(32)  DEFAULT NULL            COMMENT '旧版本来源资源类型：VARIABLE=变量，DATA_OBJECT=数据对象，MODEL=模型，EXTERNAL_DATASOURCE=外数数据源，EXTERNAL_API=外数接口，DATABASE=数据库，SFTP=文件数据源，FUNCTION=函数，RULE=规则，RULE_PROJECT_BINDING=规则项目关联，LIST_LIBRARY=名单库，LIST_RECORD_BATCH=名单变更批次，FIELD_VALIDATION=字段校验，BILLING_CONFIG=计费配置，EXPERIMENT=实验，PROJECT=项目，SYSTEM_SETTING=系统配置',
  `legacy_source_id`          BIGINT       DEFAULT NULL            COMMENT '旧版本来源ID',
  `create_by`                 VARCHAR(64)  DEFAULT NULL            COMMENT '创建人',
  `create_time`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_governed_resource_version_no` (`governed_resource_id`, `version_no`),
  UNIQUE KEY `uk_governed_version_legacy_source` (`legacy_source_type`, `legacy_source_id`),
  KEY `idx_governed_version_resource` (`resource_type`, `resource_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一治理不可变版本表';

CREATE TABLE IF NOT EXISTS `governance_approval_request` (
  `id`                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_no`                VARCHAR(64)  NOT NULL                COMMENT '审批单号',
  `resource_type`             VARCHAR(32)  NOT NULL                COMMENT '治理资源类型：VARIABLE=变量，DATA_OBJECT=数据对象，MODEL=模型，EXTERNAL_DATASOURCE=外数数据源，EXTERNAL_API=外数接口，DATABASE=数据库，SFTP=文件数据源，FUNCTION=函数，RULE=规则，RULE_PROJECT_BINDING=规则项目关联，LIST_LIBRARY=名单库，LIST_RECORD_BATCH=名单变更批次，FIELD_VALIDATION=字段校验，BILLING_CONFIG=计费配置，EXPERIMENT=实验，PROJECT=项目，SYSTEM_SETTING=系统配置',
  `resource_id`               BIGINT       NOT NULL                COMMENT '业务资源ID',
  `project_id`                BIGINT       DEFAULT NULL            COMMENT '所属项目ID',
  `action`                    VARCHAR(16)  NOT NULL                COMMENT '审批动作：CREATE=新增，UPDATE=修改，ENABLE=启用，DISABLE=停用，DELETE=删除，RESTORE=历史恢复',
  `status`                    VARCHAR(16)  NOT NULL                COMMENT '审批状态：EDITING=编辑中，PENDING=待审批，APPROVED=已批准，REJECTED=已驳回，CANCELLED=已撤销，CONFLICT=版本冲突',
  `active_resource_key`       VARCHAR(96)  DEFAULT NULL            COMMENT '活动申请唯一键，终态清空',
  `base_version_id`           BIGINT       DEFAULT NULL            COMMENT '基准版本ID',
  `base_version_no`           INT          DEFAULT NULL            COMMENT '基准版本号',
  `source_version_id`         BIGINT       DEFAULT NULL            COMMENT '恢复来源版本ID',
  `draft_snapshot_json`       LONGTEXT     NOT NULL                COMMENT '可编辑草稿快照',
  `restore_mode`              VARCHAR(16)  DEFAULT NULL            COMMENT '历史恢复方式：NEW=生成新的业务版本，REUSE=复用来源业务版本号',
  `publish_mode`              VARCHAR(16)  DEFAULT NULL COMMENT '发布方式：NEW=新建业务版本，OVERWRITE=覆盖指定业务版本',
  `target_version_id`         BIGINT       DEFAULT NULL COMMENT '目标规则业务版本绑定标识，关联 rule_version_binding.id',
  `expected_target_snapshot_id` BIGINT     DEFAULT NULL COMMENT '预期目标版本的快照标识，用于检测并发覆盖',
  `submitted_snapshot_json`   LONGTEXT     DEFAULT NULL            COMMENT '提交后冻结快照',
  `snapshot_digest`           CHAR(64)     DEFAULT NULL            COMMENT '提交快照摘要',
  `secret_payload_ciphertext` LONGTEXT     DEFAULT NULL            COMMENT '敏感字段AES-GCM密文',
  `secret_digest`             CHAR(64)     DEFAULT NULL            COMMENT '敏感字段摘要',
  `dependency_digest`         CHAR(64)     DEFAULT NULL            COMMENT '依赖快照摘要',
  `validation_report_json`    LONGTEXT     DEFAULT NULL            COMMENT '预检结果JSON',
  `change_summary`            VARCHAR(512) DEFAULT NULL            COMMENT '变更摘要',
  `submit_comment`            VARCHAR(1024) DEFAULT NULL           COMMENT '提交说明',
  `review_comment`            VARCHAR(1024) DEFAULT NULL           COMMENT '审批意见',
  `applicant`                 VARCHAR(64)  NOT NULL                COMMENT '申请人',
  `submit_time`               DATETIME     DEFAULT NULL            COMMENT '提交时间',
  `reviewer`                  VARCHAR(64)  DEFAULT NULL            COMMENT '审批人',
  `review_time`               DATETIME     DEFAULT NULL            COMMENT '审批时间',
  `lock_version`              INT          NOT NULL DEFAULT 0       COMMENT '乐观锁版本',
  `create_time`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_governance_request_no` (`request_no`),
  UNIQUE KEY `uk_governance_active_resource` (`active_resource_key`),
  KEY `idx_governance_request_tab` (`resource_type`, `status`, `create_time`),
  KEY `idx_governance_request_resource` (`resource_type`, `resource_id`, `id`),
  KEY `idx_governance_request_project` (`project_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一生命周期审批单';

CREATE TABLE IF NOT EXISTS `governance_approval_event` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_id`   BIGINT        NOT NULL                COMMENT '审批单ID',
  `action`       VARCHAR(32)   NOT NULL                COMMENT '事件动作：CREATE_DRAFT=创建草稿，SAVE_DRAFT=保存草稿，SUBMIT=提交审核，APPROVE=批准，REJECT=驳回，CANCEL=撤销，CONFLICT=检测到版本冲突',
  `from_status`  VARCHAR(16)   DEFAULT NULL            COMMENT '变更前审批状态：EDITING=编辑中，PENDING=待审批，APPROVED=已批准，REJECTED=已驳回，CANCELLED=已撤销，CONFLICT=版本冲突；首次事件为空',
  `to_status`    VARCHAR(16)   NOT NULL                COMMENT '变更后审批状态：EDITING=编辑中，PENDING=待审批，APPROVED=已批准，REJECTED=已驳回，CANCELLED=已撤销，CONFLICT=版本冲突',
  `actor`        VARCHAR(64)   NOT NULL                COMMENT '操作人',
  `comment`      VARCHAR(1024) DEFAULT NULL            COMMENT '操作意见',
  `details_json` LONGTEXT      DEFAULT NULL            COMMENT '事件详情JSON',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_governance_event_request` (`request_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一生命周期审批历史事件';

CREATE TABLE IF NOT EXISTS `governance_dependency_snapshot` (
  `id`                     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_id`             BIGINT        NOT NULL                COMMENT '审批单ID',
  `version_id`             BIGINT        DEFAULT NULL            COMMENT '生效版本ID',
  `source_resource_type`   VARCHAR(32)   NOT NULL                COMMENT '治理资源类型：VARIABLE=变量，DATA_OBJECT=数据对象，MODEL=模型，EXTERNAL_DATASOURCE=外数数据源，EXTERNAL_API=外数接口，DATABASE=数据库，SFTP=文件数据源，FUNCTION=函数，RULE=规则，RULE_PROJECT_BINDING=规则项目关联，LIST_LIBRARY=名单库，LIST_RECORD_BATCH=名单变更批次，FIELD_VALIDATION=字段校验，BILLING_CONFIG=计费配置，EXPERIMENT=实验，PROJECT=项目，SYSTEM_SETTING=系统配置',
  `source_resource_id`     BIGINT        NOT NULL                COMMENT '来源资源ID',
  `target_resource_type`   VARCHAR(32)   NOT NULL                COMMENT '治理资源类型：VARIABLE=变量，DATA_OBJECT=数据对象，MODEL=模型，EXTERNAL_DATASOURCE=外数数据源，EXTERNAL_API=外数接口，DATABASE=数据库，SFTP=文件数据源，FUNCTION=函数，RULE=规则，RULE_PROJECT_BINDING=规则项目关联，LIST_LIBRARY=名单库，LIST_RECORD_BATCH=名单变更批次，FIELD_VALIDATION=字段校验，BILLING_CONFIG=计费配置，EXPERIMENT=实验，PROJECT=项目，SYSTEM_SETTING=系统配置',
  `target_resource_id`     BIGINT        NOT NULL                COMMENT '依赖资源ID',
  `target_version_id`      BIGINT        DEFAULT NULL            COMMENT '目标规则业务版本绑定标识，关联 rule_version_binding.id',
  `target_version_no`      INT           DEFAULT NULL            COMMENT '依赖生效版本号',
  `version_selection_json` LONGTEXT      DEFAULT NULL            COMMENT '固定版本原始引用；NULL为最新版',
  `reference_path`         VARCHAR(512)  DEFAULT NULL            COMMENT '引用路径',
  `relation_type`          VARCHAR(32)   DEFAULT NULL            COMMENT '依赖关系：REFERENCES=配置引用，BELONGS_TO=归属关系；规则依赖还记录实际字段或调用关系编码',
  `required`               TINYINT       NOT NULL DEFAULT 1       COMMENT '是否必需依赖：0=否，1=是',
  `resolution_status`      VARCHAR(32)   NOT NULL                COMMENT '解析状态：RESOLVED=已解析，MISSING=资源缺失，INACTIVE=资源未生效，VERSION_MISSING=版本缺失，VERSION_CHANGED=版本已变更，DIGEST_CHANGED=摘要已变更，TYPE_MISMATCH=类型不符，SELF_REFERENCE=自引用，VERSION_UNAVAILABLE=指定版本不可用',
  `target_digest`          CHAR(64)      DEFAULT NULL            COMMENT '依赖版本摘要',
  `issue_code`             VARCHAR(64)   DEFAULT NULL            COMMENT '问题编码',
  `issue_message`          VARCHAR(1024) DEFAULT NULL            COMMENT '问题说明',
  `create_time`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_governance_dependency_request` (`request_id`, `id`),
  KEY `idx_governance_dependency_target` (`target_resource_type`, `target_resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一生命周期审批依赖快照';

CREATE TABLE IF NOT EXISTS `rule_definition` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`        BIGINT       NOT NULL                COMMENT '所属项目ID',
  `project_code`      VARCHAR(64)  DEFAULT NULL             COMMENT '所属项目编码',
  `project_name`      VARCHAR(128) DEFAULT NULL             COMMENT '所属项目名称',
  `rule_code`         VARCHAR(128) NOT NULL                COMMENT '规则编码（客户端调用标识）',
  `rule_name`         VARCHAR(256) NOT NULL                COMMENT '规则名称（中文）',
  `model_type`        VARCHAR(16)  NOT NULL                COMMENT '决策模型类型：TABLE=决策表，TREE=决策树，FLOW=决策流，RULE_SET=规则集，CROSS=交叉表，SCORE=评分卡，CROSS_ADV=复杂交叉表，SCORE_ADV=复杂评分卡，SCRIPT=脚本',
  `description`       VARCHAR(512) DEFAULT NULL             COMMENT '规则描述',
  `scope`             VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL=全局，PROJECT=项目级',
  `current_version`   INT          NOT NULL DEFAULT 0       COMMENT '当前设计版本号',
  `published_version` INT          DEFAULT NULL             COMMENT '已发布版本号',
  `status`            TINYINT      NOT NULL DEFAULT 0       COMMENT '规则状态：-1=已逻辑删除，0=草稿，1=已发布，2=已下线',
  `input_fields`     TEXT         DEFAULT NULL             COMMENT '输入字段（JSON数组，如 ["amount","age"]）',
  `output_fields`    TEXT         DEFAULT NULL             COMMENT '输出字段（JSON数组，如 ["resultScore","level"]）',
  `create_by`         VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`         VARCHAR(64)  DEFAULT NULL             COMMENT '更新人',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_code` (`rule_code`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_model_type` (`model_type`),
  KEY `idx_dashboard_definition_project_status_scope` (`project_id`, `status`, `scope`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则定义表';

CREATE TABLE IF NOT EXISTS `rule_definition_input_field` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `definition_id`    BIGINT       NOT NULL                COMMENT '所属规则ID',
  `var_id`          BIGINT       DEFAULT NULL             COMMENT '关联字段ID，需结合 ref_type 判断所属资源表',
  `ref_type`        VARCHAR(32)  DEFAULT NULL             COMMENT '引用类型：VARIABLE=变量，CONSTANT=常量，DATA_OBJECT=对象字段，DATA_OBJECT_ROOT=对象整体，MODEL=模型，MODEL_OUTPUT=模型输出字段',
  `field_name`       VARCHAR(128) NOT NULL                COMMENT '字段名称（原始名称）',
  `field_label`      VARCHAR(128) DEFAULT NULL             COMMENT '字段中文名称',
  `script_name`      VARCHAR(128) DEFAULT NULL             COMMENT '脚本引用名称，原样保留用户配置，不自动转换大小写或命名格式',
  `field_type`       VARCHAR(32)  DEFAULT NULL             COMMENT '字段数据类型：STRING=字符串，NUMBER=数值，INTEGER=整数，DOUBLE=双精度数值，BOOLEAN=布尔，DATE=日期，ENUM=枚举，OBJECT=对象，LIST=列表，MAP=映射',
  `missing_value`    VARCHAR(256) DEFAULT NULL             COMMENT '缺失值处理策略',
  `default_value`    VARCHAR(256) DEFAULT NULL             COMMENT '默认值',
  `valid_values`     TEXT         DEFAULT NULL             COMMENT '有效值列表（JSON数组）',
  `transform_type`   VARCHAR(32)  DEFAULT NULL             COMMENT '输入转换类型：NONE=不转换，NORMALIZE=标准化，DISCRETIZE=离散分箱，MAPVALUES=值映射，MINMAX=区间缩放',
  `transform_params` JSON         DEFAULT NULL             COMMENT '转换参数',
  `validation_rule_ids` JSON      DEFAULT NULL             COMMENT '字段校验规则ID列表JSON',
  `validation_override` TINYINT   NOT NULL DEFAULT 0       COMMENT '是否由当前规则覆盖子规则校验：0=否，1=是',
  `sort_order`       INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  `status`           TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_definition_id` (`definition_id`),
  KEY `idx_var_id` (`var_id`),
  KEY `idx_ref_type_var_id` (`ref_type`, `var_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则输入字段表';

CREATE TABLE IF NOT EXISTS `rule_definition_output_field` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `definition_id`    BIGINT       NOT NULL                COMMENT '所属规则ID',
  `var_id`          BIGINT       DEFAULT NULL             COMMENT '关联字段ID，需结合 ref_type 判断所属资源表',
  `ref_type`        VARCHAR(32)  DEFAULT NULL             COMMENT '引用类型：VARIABLE=变量，CONSTANT=常量，DATA_OBJECT=对象字段，DATA_OBJECT_ROOT=对象整体，MODEL=模型，MODEL_OUTPUT=模型输出字段',
  `field_name`       VARCHAR(128) NOT NULL                COMMENT '字段名称（输出变量名）',
  `field_label`      VARCHAR(128) DEFAULT NULL             COMMENT '字段中文名称',
  `script_name`      VARCHAR(128) DEFAULT NULL             COMMENT '脚本引用名称，原样保留用户配置，不自动转换大小写或命名格式',
  `field_type`       VARCHAR(32)  DEFAULT NULL             COMMENT '字段数据类型：STRING=字符串，NUMBER=数值，INTEGER=整数，DOUBLE=双精度数值，BOOLEAN=布尔，DATE=日期，ENUM=枚举，OBJECT=对象，LIST=列表，MAP=映射',
  `transform_type`   VARCHAR(32)  DEFAULT NULL             COMMENT '输出转换类型：NONE=不转换，RENAME=重命名，SCALE=比例缩放，OHE=独热编码',
  `transform_params` JSON         DEFAULT NULL             COMMENT '转换参数',
  `valid_values`     TEXT         DEFAULT NULL             COMMENT '有效值列表（JSON数组，分类变量）',
  `sort_order`       INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  `status`           TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_definition_id` (`definition_id`),
  KEY `idx_var_id` (`var_id`),
  KEY `idx_ref_type_var_id` (`ref_type`, `var_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则输出字段表';

CREATE TABLE IF NOT EXISTS `rule_api_doc_scenario` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `definition_id`      BIGINT       NOT NULL                COMMENT '规则定义ID',
  `scenario_name`      VARCHAR(128) NOT NULL                COMMENT '场景名称',
  `description`        VARCHAR(512) DEFAULT NULL            COMMENT '场景说明',
  `request_json`       LONGTEXT     NOT NULL                COMMENT '完整请求报文',
  `response_json`      LONGTEXT     NOT NULL                COMMENT '完整响应报文',
  `expectation_json`   LONGTEXT     DEFAULT NULL            COMMENT '版本化测试预期；NULL保留历史JSON快照模式',
  `response_source`    VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT '响应来源：MANUAL=手工填写，EXECUTED=实际执行采集',
  `outer_code`         INT          DEFAULT NULL            COMMENT '平台外层响应码',
  `business_code_path` VARCHAR(256) DEFAULT NULL            COMMENT '内层业务码路径',
  `business_code`      VARCHAR(256) DEFAULT NULL            COMMENT '内层业务码展示值',
  `rule_version`       INT          NOT NULL                COMMENT '保存时规则版本',
  `revision_id`        BIGINT       DEFAULT NULL            COMMENT '测试场景绑定的规则修订ID',
  `artifact_digest`    CHAR(64)     DEFAULT NULL            COMMENT '测试场景绑定的不可变制品摘要',
  `include_in_doc`     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否纳入接口文档：0=否，1=是',
  `sort_order`         INT          NOT NULL DEFAULT 0      COMMENT '展示顺序',
  `status`             TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0-停用，1-启用',
  `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_doc_scenario_name` (`definition_id`, `scenario_name`),
  KEY `idx_api_doc_scenario_export` (`definition_id`, `status`, `include_in_doc`, `sort_order`),
  KEY `idx_api_doc_scenario_revision` (`definition_id`, `revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则 API 文档测试场景';

CREATE TABLE IF NOT EXISTS `rule_regression_evidence` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `definition_id` BIGINT NOT NULL COMMENT '规则定义标识，关联 rule_definition.id',
  `scenario_id` BIGINT NOT NULL COMMENT '固定测试用例标识，关联 rule_api_doc_scenario.id',
  `scenario_name` VARCHAR(128) NOT NULL COMMENT '执行时的测试用例名称快照',
  `revision_id` BIGINT DEFAULT NULL COMMENT '规则修订标识，关联 rule_revision.id',
  `project_id` BIGINT DEFAULT NULL COMMENT '所属项目标识，关联 rule_project.id；0 表示全局资源',
  `source_type` VARCHAR(32) DEFAULT NULL COMMENT '测试来源：WORKSPACE=当前工作区，PREVIEW=画布预览，REVISION=修订，VERSION=不可变版本快照，LATEST=当前已发布版本，BUSINESS_VERSION=指定业务版本',
  `source_id` VARCHAR(64) DEFAULT NULL COMMENT '测试来源的稳定标识，与 source_type 配套解析',
  `content_digest` CHAR(64) DEFAULT NULL COMMENT '内容规范快照的 SHA-256 摘要',
  `dependency_digest` CHAR(64) DEFAULT NULL COMMENT '执行依赖快照的 SHA-256 摘要',
  `scenario_digest` CHAR(64) NOT NULL COMMENT '测试输入与预期配置的 SHA-256 摘要',
  `eligible` TINYINT NOT NULL DEFAULT 0 COMMENT '能否作为发布回归依据：0=不能，1=可以',
  `state` VARCHAR(24) NOT NULL COMMENT '证据状态：RUNNING=执行中，COMPLETED=已完成，STALE=内容已变更而失效，ERROR=执行出错',
  `success` TINYINT DEFAULT NULL COMMENT '执行是否成功：0=否，1=是；空值表示未知或不适用',
  `validation_status` VARCHAR(32) DEFAULT NULL COMMENT '预期校验状态：PASSED=通过，FAILED=未通过，NOT_CONFIGURED=未设置预期，NOT_EVALUATED=执行未成功而未校验；空值表示未完成，INVALID=预期或结果格式无效',
  `validation_json` LONGTEXT COMMENT '用例预期校验报告，JSON 格式',
  `message` VARCHAR(2000) DEFAULT NULL COMMENT '结果或失效原因说明',
  `trace_id` VARCHAR(128) DEFAULT NULL COMMENT '执行追踪唯一标识',
  `execution_identity_json` LONGTEXT COMMENT '实际执行的修订、版本及制品身份快照，JSON 格式',
  `executed_by` VARCHAR(128) DEFAULT NULL COMMENT '执行测试的账户标识',
  `create_time` DATETIME(3) NOT NULL COMMENT '记录创建时间',
  `completed_at` DATETIME(3) DEFAULT NULL COMMENT '处理完成时间',
  PRIMARY KEY (`id`),
  KEY `idx_regression_history` (`definition_id`, `id`),
  KEY `idx_regression_case` (`definition_id`, `revision_id`, `eligible`, `scenario_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务端实际回归执行证据';

CREATE TABLE IF NOT EXISTS `rule_regression_evidence_latest` (
  `definition_id` BIGINT NOT NULL COMMENT '规则定义标识，关联 rule_definition.id',
  `revision_id` BIGINT NOT NULL COMMENT '用于发布回归门禁的规则修订标识',
  `scenario_id` BIGINT NOT NULL COMMENT '固定测试用例标识，关联 rule_api_doc_scenario.id',
  `evidence_id` BIGINT NOT NULL COMMENT '最新有效回归尝试标识，关联 rule_regression_evidence.id',
  PRIMARY KEY (`definition_id`, `revision_id`, `scenario_id`),
  KEY `idx_regression_latest_report` (`evidence_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每个修订和固定用例的最新有效回归尝试';

CREATE TABLE IF NOT EXISTS `rule_definition_content` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `definition_id`   BIGINT   NOT NULL                COMMENT '规则定义ID',
  `model_json`      LONGTEXT NOT NULL                COMMENT '模型设计数据（JSON）',
  `compiled_script` LONGTEXT DEFAULT NULL             COMMENT '编译后脚本',
  `compiled_type`   VARCHAR(16) DEFAULT NULL          COMMENT '编译产物类型：QLEXPRESS=规则表达式脚本',
  `compile_status`  TINYINT  NOT NULL DEFAULT 0       COMMENT '编译状态：0-未编译，1-成功，2-失败',
  `compile_message` VARCHAR(1024) DEFAULT NULL        COMMENT '编译信息',
  `compile_time`    DATETIME DEFAULT NULL             COMMENT '最近编译时间',
  `script_mode`     VARCHAR(16) NOT NULL DEFAULT 'visual' COMMENT '编辑模式：visual-可视化，script-脚本模式',
  `open_api_config_json` LONGTEXT DEFAULT NULL             COMMENT '对外规则接口草稿配置JSON',
  `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_definition_id` (`definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则内容表（设计态数据和编译产物）';

CREATE TABLE IF NOT EXISTS `rule_definition_ref` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `definition_id`   BIGINT   NOT NULL                COMMENT '全局规则定义ID',
  `project_id`      BIGINT   NOT NULL                COMMENT '关联项目ID',
  `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_definition_project` (`definition_id`, `project_id`),
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则关联表（用于项目关联全局规则）';

CREATE TABLE IF NOT EXISTS `rule_definition_version` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `definition_id`   BIGINT       NOT NULL                COMMENT '规则定义ID',
  `version`         INT          NOT NULL                COMMENT '版本号',
  `model_json`      LONGTEXT     NOT NULL                COMMENT '版本快照 - 模型JSON',
  `compiled_script` LONGTEXT     DEFAULT NULL             COMMENT '版本快照 - 编译后脚本',
  `compiled_type`   VARCHAR(16)  DEFAULT NULL             COMMENT '编译产物类型：QLEXPRESS=规则表达式脚本',
  `open_api_config_json` LONGTEXT DEFAULT NULL            COMMENT '对外规则接口版本快照JSON',
  `change_log`      VARCHAR(512) DEFAULT NULL             COMMENT '变更说明（中文）',
  `publish_by`      VARCHAR(64)  DEFAULT NULL             COMMENT '发布人',
  `publish_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `business_version`     INT          DEFAULT NULL COMMENT '业务版本号',
  `version_binding_id`   BIGINT       DEFAULT NULL COMMENT '业务版本绑定ID',
  `binding_generation`    BIGINT       DEFAULT NULL COMMENT '业务版本绑定代数',
  `revision_id`           BIGINT       DEFAULT NULL COMMENT '关联规则修订ID',
  `artifact_id`           BIGINT       DEFAULT NULL COMMENT '关联决策制品ID',
  `artifact_digest`       CHAR(64)     DEFAULT NULL COMMENT '关联决策制品摘要',
  PRIMARY KEY (`id`, `definition_id`),
  UNIQUE KEY `uk_def_version` (`definition_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则版本历史表'
PARTITION BY HASH(`definition_id`) PARTITIONS 8;

CREATE TABLE IF NOT EXISTS `rule_published` (
  `revision_id`     BIGINT       DEFAULT NULL             COMMENT '规则修订标识，关联 rule_revision.id',
  `artifact_id`     BIGINT       DEFAULT NULL             COMMENT '决策制品标识，关联 decision_artifact.id',
  `artifact_digest` CHAR(64)     DEFAULT NULL             COMMENT '决策制品的 SHA-256 摘要',
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_code`       VARCHAR(128) NOT NULL                COMMENT '规则编码',
  `definition_id`   BIGINT       NOT NULL                COMMENT '规则定义ID',
  `project_code`    VARCHAR(64)  DEFAULT NULL             COMMENT '所属项目编码',
  `version`         INT          NOT NULL                COMMENT '发布版本号',
  `model_type`      VARCHAR(16)  NOT NULL                COMMENT '决策模型类型：TABLE=决策表，TREE=决策树，FLOW=决策流，RULE_SET=规则集，CROSS=交叉表，SCORE=评分卡，CROSS_ADV=复杂交叉表，SCORE_ADV=复杂评分卡，SCRIPT=脚本',
  `compiled_script` LONGTEXT     NOT NULL                COMMENT '编译后脚本',
  `compiled_type`   VARCHAR(16)  DEFAULT NULL             COMMENT '编译产物类型：QLEXPRESS=规则表达式脚本',
  `model_json`      LONGTEXT     DEFAULT NULL             COMMENT '模型JSON（设计器回显用）',
  `open_api_config_json` LONGTEXT DEFAULT NULL            COMMENT '当前已发布对外规则接口配置JSON',
  `status`          TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-已下线，1-已上线',
  `publish_by`      VARCHAR(64)  DEFAULT NULL             COMMENT '发布人',
  `publish_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `offline_time`    DATETIME     DEFAULT NULL             COMMENT '下线时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_code` (`rule_code`),
  KEY `idx_status` (`status`),
  KEY `idx_definition_id` (`definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已发布规则表，供客户端同步执行';

CREATE TABLE IF NOT EXISTS `rule_revision` (
  `id`                       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `definition_id`            BIGINT        NOT NULL COMMENT '规则定义标识，关联 rule_definition.id',
  `revision_no`              INT           NOT NULL COMMENT '规则内递增的修订序号',
  `state`                    VARCHAR(16)   NOT NULL COMMENT '修订生命周期状态：DRAFT=草稿，REVIEW=审核中，REJECTED=已驳回，APPROVED=已批准，PUBLISHED=已发布，OFFLINE=已下线，DELETED=已删除',
  `base_revision_id`         BIGINT        DEFAULT NULL COMMENT '派生本修订的基准修订标识',
  `base_artifact_id`         BIGINT        DEFAULT NULL COMMENT '派生本修订的基准决策制品标识',
  `source_type`              VARCHAR(16)   DEFAULT NULL COMMENT '草稿来源类型：REVISION=其他规则修订，VERSION=历史版本快照；空值表示未指定来源',
  `source_id`                BIGINT        DEFAULT NULL COMMENT '草稿来源修订或版本快照的标识，与 source_type 配套',
  `model_json`               LONGTEXT      NOT NULL COMMENT '规则设计模型快照，JSON 格式',
  `compiled_script`          LONGTEXT      DEFAULT NULL COMMENT '编译后的可执行规则脚本',
  `compiled_type`            VARCHAR(16)   DEFAULT NULL COMMENT '编译产物类型：QLEXPRESS=规则表达式脚本',
  `open_api_config_json`     LONGTEXT      DEFAULT NULL COMMENT '规则开放接口契约快照，JSON 格式',
  `input_schema_json`        LONGTEXT      DEFAULT NULL COMMENT '精确输入字段结构及类型约束，JSON 格式',
  `output_schema_json`       LONGTEXT      DEFAULT NULL COMMENT '精确输出字段结构及类型约束，JSON 格式',
  `content_digest`           CHAR(64)      DEFAULT NULL COMMENT '内容规范快照的 SHA-256 摘要',
  `validation_report_digest` CHAR(64)      DEFAULT NULL COMMENT '发布前校验报告的 SHA-256 摘要',
  `artifact_id`              BIGINT        DEFAULT NULL COMMENT '决策制品标识，关联 decision_artifact.id',
  `governance_request_id`    BIGINT        DEFAULT NULL COMMENT '关联的统一审批单标识',
  `publish_mode`             VARCHAR(16)   DEFAULT NULL COMMENT '发布方式：NEW=新建业务版本，OVERWRITE=覆盖指定业务版本',
  `target_version_id`        BIGINT        DEFAULT NULL COMMENT '目标规则业务版本绑定标识，关联 rule_version_binding.id',
  `target_generation`        BIGINT        DEFAULT NULL COMMENT '目标业务版本绑定的预期代数，用于并发覆盖校验',
  `force_publish_reason`     VARCHAR(1024) DEFAULT NULL COMMENT '强制发布的原因说明',
  `lock_version`             INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，每次变更递增',
  `create_by`                VARCHAR(64)   NOT NULL COMMENT '创建人标识',
  `create_time`              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_by`                VARCHAR(64)   NOT NULL COMMENT '最后更新人标识',
  `update_time`              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
  `submit_by`                VARCHAR(64)   DEFAULT NULL COMMENT '提交审核人标识',
  `submit_time`              DATETIME      DEFAULT NULL COMMENT '提交审核时间',
  `approve_by`               VARCHAR(64)   DEFAULT NULL COMMENT '审核通过人标识',
  `approve_time`             DATETIME      DEFAULT NULL COMMENT '审核通过时间',
  `publish_by`               VARCHAR(64)   DEFAULT NULL COMMENT '发布人标识',
  `publish_time`             DATETIME      DEFAULT NULL COMMENT '发布时间',
  `offline_by`               VARCHAR(64)   DEFAULT NULL COMMENT '下线操作人标识',
  `offline_time`             DATETIME      DEFAULT NULL COMMENT '下线时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_revision` (`definition_id`, `revision_no`),
  KEY `idx_revision_lifecycle` (`definition_id`, `state`, `revision_no`),
  KEY `idx_revision_artifact` (`artifact_id`),
  KEY `idx_revision_governance_request` (`governance_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则修订生命周期表';

CREATE TABLE IF NOT EXISTS `rule_designer_save_operation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `definition_id` BIGINT NOT NULL COMMENT '规则定义标识，关联 rule_definition.id',
  `request_id` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '调用方请求标识',
  `request_digest` CHAR(64) NOT NULL COMMENT '请求内容的 SHA-256 摘要，用于检查幂等重试内容是否相同',
  `response_json` LONGTEXT NOT NULL COMMENT '首次处理结果快照，JSON 格式',
  `create_by` VARCHAR(64) NOT NULL COMMENT '创建人标识',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_designer_save_request` (`definition_id`, `request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则设计器保存幂等记录及原始响应表';

CREATE TABLE IF NOT EXISTS `rule_lifecycle_event` (
  `id`                       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `definition_id`            BIGINT        NOT NULL COMMENT '规则定义标识，关联 rule_definition.id',
  `revision_id`              BIGINT        NOT NULL COMMENT '规则修订标识，关联 rule_revision.id',
  `action`                   VARCHAR(32)   NOT NULL COMMENT '生命周期动作：CREATE_DRAFT=创建草稿，OVERWRITE_DRAFT=覆盖草稿，DELETE_DRAFT=删除草稿，SUBMIT=提交审核，APPROVE=批准，REJECT=驳回，PUBLISH=发布，OFFLINE=下线，AUTO_OFFLINE=新版本发布自动下线旧版，IMPORT_APPROVED_ARTIFACT=导入已批准制品，DELETE_UNAPPROVED=删除未批准规则，MIGRATE_DRAFT=迁移旧草稿，MIGRATE_PUBLISHED_ARTIFACT=迁移已发布制品，MIGRATE_UNRESOLVED=迁移存在未解析依赖的规则',
  `from_state`               VARCHAR(16)   DEFAULT NULL COMMENT '变更前修订状态：DRAFT=草稿，REVIEW=审核中，REJECTED=已驳回，APPROVED=已批准，PUBLISHED=已发布，OFFLINE=已下线，DELETED=已删除；首次事件为空',
  `to_state`                 VARCHAR(16)   NOT NULL COMMENT '变更后修订状态：DRAFT=草稿，REVIEW=审核中，REJECTED=已驳回，APPROVED=已批准，PUBLISHED=已发布，OFFLINE=已下线，DELETED=已删除',
  `actor`                    VARCHAR(64)   NOT NULL COMMENT '事件操作人标识',
  `comment`                  VARCHAR(1024) DEFAULT NULL COMMENT '操作意见或原因',
  `content_digest`           CHAR(64)      DEFAULT NULL COMMENT '内容规范快照的 SHA-256 摘要',
  `validation_report_digest` CHAR(64)      DEFAULT NULL COMMENT '发布前校验报告的 SHA-256 摘要',
  `artifact_digest`          CHAR(64)      DEFAULT NULL COMMENT '决策制品的 SHA-256 摘要',
  `request_source`           VARCHAR(32)   DEFAULT NULL COMMENT '事件请求来源：CONSOLE=控制台操作，MIGRATION=存量规则迁移',
  `deployment_id`            BIGINT        DEFAULT NULL COMMENT '关联制品部署记录标识',
  `details_json`             LONGTEXT      DEFAULT NULL COMMENT '事件附加详情，JSON 格式',
  `create_time`              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_lifecycle_revision` (`revision_id`, `create_time`),
  KEY `idx_lifecycle_definition` (`definition_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则生命周期事件追加记录表';

CREATE TABLE IF NOT EXISTS `decision_artifact` (
  `id`                       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `definition_id`            BIGINT       NOT NULL COMMENT '规则定义标识，关联 rule_definition.id',
  `revision_id`              BIGINT       NOT NULL COMMENT '规则修订标识，关联 rule_revision.id',
  `artifact_digest`          CHAR(64)     NOT NULL COMMENT '决策制品的 SHA-256 摘要',
  `package_digest`           CHAR(64)     NOT NULL COMMENT '完整制品包的 SHA-256 摘要',
  `format_version`           VARCHAR(16)  NOT NULL COMMENT '制品包格式版本，当前为 1',
  `manifest_json`            LONGTEXT     NOT NULL COMMENT '制品清单，包含组件和依赖身份，JSON 格式',
  `validation_report_json`   LONGTEXT     NOT NULL COMMENT '制品或模型的校验报告，JSON 格式',
  `runtime_constraints_json` LONGTEXT     NOT NULL COMMENT '运行环境与依赖版本约束，JSON 格式',
  `package_content`          LONGBLOB     NOT NULL COMMENT '完整决策制品压缩包的二进制内容',
  `package_size`             BIGINT       NOT NULL COMMENT '完整制品压缩包大小，单位字节',
  `create_by`                VARCHAR(64)  NOT NULL COMMENT '创建人标识',
  `create_time`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_artifact_digest` (`artifact_digest`),
  KEY `idx_artifact_revision` (`revision_id`),
  KEY `idx_artifact_definition` (`definition_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变决策制品表';

CREATE TABLE IF NOT EXISTS `decision_artifact_component` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `artifact_id`     BIGINT       NOT NULL COMMENT '决策制品标识，关联 decision_artifact.id',
  `component_id`    VARCHAR(64)  NOT NULL COMMENT '制品包内稳定且唯一的组件标识',
  `component_type`  VARCHAR(32)  NOT NULL COMMENT '制品组件类型：RULE=规则，VARIABLE=变量，CONSTANT=常量，MODEL=模型，FUNCTION=函数，DATA_OBJECT=对象字段，DATA_OBJECT_ROOT=对象整体，BINDING=外部资源绑定，RESOURCE_VERSION=固定资源版本',
  `source_type`     VARCHAR(32)  DEFAULT NULL COMMENT '组件来源类型：RULE_REVISION=规则修订，IMPORTED=导入资源；其他来源为 RULE=规则，VARIABLE=变量，CONSTANT=常量，MODEL=模型，FUNCTION=函数，DATA_OBJECT=对象字段，DATA_OBJECT_ROOT=对象整体，BINDING=外部资源绑定，RESOURCE_VERSION=固定资源版本',
  `source_id`       BIGINT       DEFAULT NULL COMMENT '组件来源资源的稳定标识，与 source_type 配套',
  `source_version`  INT          DEFAULT NULL COMMENT '来源资源版本号快照',
  `package_path`    VARCHAR(512) NOT NULL COMMENT '组件在制品压缩包内的相对路径',
  `media_type`      VARCHAR(128) NOT NULL COMMENT '组件内容的媒体类型，例如 application/json、application/octet-stream',
  `content_digest`  CHAR(64)     NOT NULL COMMENT '内容规范快照的 SHA-256 摘要',
  `content_size`    BIGINT       NOT NULL COMMENT '组件内容大小，单位字节',
  `metadata_json`   LONGTEXT     DEFAULT NULL COMMENT '组件来源、版本及其他元数据，JSON 格式',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_artifact_component` (`artifact_id`, `component_id`),
  UNIQUE KEY `uk_artifact_component_path` (`artifact_id`, `package_path`),
  KEY `idx_component_source` (`source_type`, `source_id`, `source_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策制品组件及来源表';

CREATE TABLE IF NOT EXISTS `artifact_deployment` (
  `id`                        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `artifact_id`               BIGINT        NOT NULL COMMENT '决策制品标识，关联 decision_artifact.id',
  `environment_code`          VARCHAR(128)  NOT NULL COMMENT '目标部署环境编码',
  `target_definition_id`      BIGINT        DEFAULT NULL COMMENT '部署到目标环境的规则定义标识',
  `create_rule`               TINYINT       NOT NULL DEFAULT 0 COMMENT '是否在目标环境新建规则：0=绑定已有规则，1=新建规则',
  `status`                    VARCHAR(32)   NOT NULL COMMENT '部署结果状态：DEPLOYED=已成功部署',
  `compatibility_report_json` LONGTEXT      DEFAULT NULL COMMENT '目标环境兼容性校验报告，JSON 格式',
  `binding_report_json`       LONGTEXT      DEFAULT NULL COMMENT '制品组件与目标资源绑定报告，JSON 格式',
  `error_message`             VARCHAR(2048) DEFAULT NULL COMMENT '失败原因或异常说明',
  `deploy_by`                 VARCHAR(64)   DEFAULT NULL COMMENT '部署操作人标识',
  `deploy_time`               DATETIME      DEFAULT NULL COMMENT '制品部署完成时间',
  `create_time`               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_deployment_artifact` (`artifact_id`, `create_time`),
  KEY `idx_deployment_target` (`target_definition_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策制品部署历史表';

CREATE TABLE IF NOT EXISTS `artifact_resource_binding` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `deployment_id`      BIGINT       NOT NULL COMMENT '关联制品部署记录标识',
  `component_id`       VARCHAR(64)  NOT NULL COMMENT '制品包内稳定且唯一的组件标识',
  `resource_type`      VARCHAR(32)  NOT NULL COMMENT '绑定目标类型：EXTERNAL_API=外数接口，DB_DATASOURCE=数据库数据源，SFTP=文件数据源，LIST_LIBRARY=名单库，DATA_OBJECT_ROOT=数据对象',
  `target_resource_id` BIGINT       NOT NULL COMMENT '显式绑定的目标业务资源标识，资源表由 resource_type 决定',
  `binding_digest`     CHAR(64)     NOT NULL COMMENT '资源绑定配置的 SHA-256 摘要',
  `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_deployment_binding` (`deployment_id`, `component_id`),
  KEY `idx_binding_target` (`resource_type`, `target_resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策制品与目标资源显式绑定表';

CREATE TABLE IF NOT EXISTS `resource_impact_analysis` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `analysis_token` CHAR(36)      NOT NULL COMMENT '影响分析结果的唯一确认令牌',
  `resource_type`  VARCHAR(32)   NOT NULL COMMENT '待分析的资源类型编码，当前模型变更使用 MODEL=模型',
  `resource_id`    BIGINT        NOT NULL COMMENT '业务资源标识，资源表由 resource_type 决定',
  `action`         VARCHAR(32)   NOT NULL COMMENT '拟执行的资源变更动作，例如 DELETE=删除、REPLACE=替换、OFFLINE=下线',
  `impact_digest`  CHAR(64)      NOT NULL COMMENT '影响分析依赖集合及内容的 SHA-256 摘要',
  `report_json`    LONGTEXT      NOT NULL COMMENT '资源变更影响分析报告，JSON 格式',
  `status`         VARCHAR(16)   NOT NULL COMMENT '影响分析状态：PENDING=待确认，CONFIRMED=已确认',
  `expires_at`     DATETIME      NOT NULL COMMENT '影响分析确认令牌的失效时间',
  `create_by`      VARCHAR(64)   NOT NULL COMMENT '创建人标识',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `confirm_by`     VARCHAR(64)   DEFAULT NULL COMMENT '确认影响的操作人标识',
  `confirm_time`   DATETIME      DEFAULT NULL COMMENT '影响确认时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_impact_token` (`analysis_token`),
  KEY `idx_impact_resource` (`resource_type`, `resource_id`, `action`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源变更影响分析及确认表';

CREATE TABLE IF NOT EXISTS `rule_publish_outbox` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `operation_id`    CHAR(36)      NOT NULL COMMENT '发布操作的全局唯一标识，用于消息去重',
  `definition_id`   BIGINT        NOT NULL COMMENT '规则定义标识，关联 rule_definition.id',
  `revision_id`     BIGINT        NOT NULL COMMENT '规则修订标识，关联 rule_revision.id',
  `artifact_id`     BIGINT        NOT NULL COMMENT '决策制品标识，关联 decision_artifact.id',
  `message_json`    LONGTEXT      NOT NULL COMMENT '待投递的规则或函数变更通知，JSON 格式',
  `delivery_status` VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '消息投递状态：PENDING=待投递，DELIVERING=领取处理中，DELIVERED=已投递，DEAD_LETTER=进入死信',
  `retry_count`     INT           NOT NULL DEFAULT 0 COMMENT '已重试次数',
  `next_retry_time` DATETIME      DEFAULT NULL COMMENT '允许下一次重试的时间',
  `last_error`      VARCHAR(2048) DEFAULT NULL COMMENT '最近一次投递失败原因',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
  `delivered_time`  DATETIME      DEFAULT NULL COMMENT '消息或持久化事件成功投递时间',
  `dead_letter_time` DATETIME      DEFAULT NULL COMMENT '超过重试上限后进入死信的时间',
  `claim_token`     CHAR(36)       DEFAULT NULL COMMENT '本次投递领取令牌，用于防止过期执行者回写',
  `lease_until`     DATETIME       DEFAULT NULL COMMENT '当前执行租约的到期时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_publish_operation` (`operation_id`),
  KEY `idx_outbox_poll` (`delivery_status`, `next_retry_time`),
  KEY `idx_outbox_revision` (`revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则发布通知可靠投递事务发件箱';

CREATE TABLE IF NOT EXISTS `rule_data_object` (
  `lazy_load_references` TINYINT NOT NULL DEFAULT 0 COMMENT '是否对引用型叶子字段按需取值：0=否，1=是',
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`       BIGINT       NOT NULL                COMMENT '所属项目ID',
  `scope`             VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL=全局，PROJECT=项目级',
  `object_code`      VARCHAR(128) NOT NULL                COMMENT '对象编码（Java类名/JSON键名）',
  `object_label`     VARCHAR(128) DEFAULT NULL             COMMENT '对象中文名称',
  `script_name`      VARCHAR(128) DEFAULT NULL             COMMENT '脚本引用名称，原样保留用户配置，不自动转换大小写或命名格式',
  `object_type`      VARCHAR(16)  NOT NULL DEFAULT 'INPUT' COMMENT '对象用途：INPUT=输入，OUTPUT=输出，INOUT=输入及输出',
  `source_type`      VARCHAR(16)  DEFAULT NULL             COMMENT '来源类型：JAVA=实体类导入，JSON=结构导入，DDL=建表语句导入，API=外数接口，DB=数据库，MANUAL=手工维护',
  `source_content`   LONGTEXT     DEFAULT NULL             COMMENT '原始文件内容',
  `parent_object_id` BIGINT       DEFAULT NULL             COMMENT '父对象ID（嵌套对象）',
  `status`           TINYINT      NOT NULL DEFAULT 1       COMMENT '资源状态：-1=已逻辑删除，0=停用，1=启用',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_project_object` (`scope`, `project_id`, `object_code`),
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据对象定义表（Java实体类/JSON对象）';

CREATE TABLE IF NOT EXISTS `rule_data_object_field` (
  `reference_mode` VARCHAR(16) NOT NULL DEFAULT 'VALUE' COMMENT '引用方式：VALUE=缺失值时引用取值，STRUCTURE=仅复用结构定义',
  `record_result`    TINYINT NOT NULL DEFAULT 0 COMMENT '是否显式记录字段结果供历史统计：0=否，1=是',
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`       BIGINT       NOT NULL                COMMENT '所属项目ID',
  `scope`             VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL=全局，PROJECT=项目级',
  `object_id`        BIGINT       NOT NULL                COMMENT '所属数据对象ID',
  `var_code`         VARCHAR(128) NOT NULL                COMMENT '字段编码',
  `var_label`        VARCHAR(128) NOT NULL                COMMENT '字段中文名称',
  `script_name`      VARCHAR(128) DEFAULT NULL             COMMENT '脚本引用名称，原样保留用户配置，不自动转换大小写或命名格式',
  `var_type`         VARCHAR(32)  NOT NULL                COMMENT '变量或对象字段数据类型：STRING=字符串，NUMBER=数值，INTEGER=整数，DOUBLE=双精度数值，BOOLEAN=布尔，DATE=日期，ENUM=枚举，OBJECT=对象，LIST=列表，MAP=映射',
  `source_path`      VARCHAR(512) DEFAULT NULL             COMMENT 'API响应或数据库首行结果中的字段取值路径',
  `source_config`    LONGTEXT DEFAULT NULL COMMENT '对象子字段外数入参覆盖',
  `ref_object_code`  VARCHAR(128) DEFAULT NULL             COMMENT '引用对象的编码快照；对象关联以 ref_object_id 为准',
  `ref_object_id`    BIGINT       DEFAULT NULL             COMMENT '引用的数据对象标识，关联 rule_data_object.id',
  `ref_variable_id`  BIGINT       DEFAULT NULL             COMMENT '字段值直接引用的变量ID（指向 rule_variable.id）',
  `generic_type`      VARCHAR(32)  DEFAULT NULL             COMMENT '集合元素类型：STRING=字符串，NUMBER=数值，INTEGER=整数，DOUBLE=双精度数值，BOOLEAN=布尔，DATE=日期，ENUM=枚举，OBJECT=对象，LIST=列表，MAP=映射',
  `parent_field_id`  BIGINT       DEFAULT NULL             COMMENT '父字段ID（嵌套预留）',
  `sort_order`       INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  `status`           TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_object_var_code` (`object_id`, `parent_field_id`, `var_code`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_object_id` (`object_id`),
  KEY `idx_ref_object_id` (`ref_object_id`),
  KEY `idx_ref_variable_id` (`ref_variable_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据对象字段表';

CREATE TABLE IF NOT EXISTS `rule_data_object_field_option` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `field_id`     BIGINT       NOT NULL                COMMENT '所属对象字段ID',
  `option_value` VARCHAR(256) NOT NULL                COMMENT '选项值',
  `option_label` VARCHAR(256) NOT NULL                COMMENT '选项中文标签',
  `sort_order`   INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  PRIMARY KEY (`id`),
  KEY `idx_field_id` (`field_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据对象字段枚举选项';

CREATE TABLE IF NOT EXISTS `rule_variable` (
  `record_result`    TINYINT NOT NULL DEFAULT 0 COMMENT '是否显式记录字段结果供历史统计：0=否，1=是',
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`        BIGINT       NOT NULL                COMMENT '所属项目ID',
  `scope`             VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL=全局，PROJECT=项目级',
  `var_code`          VARCHAR(128) NOT NULL                COMMENT '变量编码',
  `var_label`         VARCHAR(128) NOT NULL                COMMENT '变量中文名称',
  `script_name`       VARCHAR(128) DEFAULT NULL             COMMENT '脚本引用名称，原样保留用户配置，不自动转换大小写或命名格式',
  `var_type`          VARCHAR(32)  NOT NULL                COMMENT '变量或对象字段数据类型：STRING=字符串，NUMBER=数值，INTEGER=整数，DOUBLE=双精度数值，BOOLEAN=布尔，DATE=日期，ENUM=枚举，OBJECT=对象，LIST=列表，MAP=映射',
  `var_source`        VARCHAR(32)  NOT NULL DEFAULT 'INPUT' COMMENT '变量来源：INPUT=请求输入，COMPUTED=计算变量，CONSTANT=常量，DB=数据库，API=外数接口，LIST=名单匹配，SFTP=远程文件，DERIVED=历史派生统计',
  `source_config`     JSON         DEFAULT NULL             COMMENT '外部来源配置JSON：API/DB/LIST变量绑定接口、SQL、入参映射、结果路径等',
  `default_value`     TEXT         DEFAULT NULL             COMMENT '默认值（常量必填，可为较长 JSON）',
  `value_range`       VARCHAR(512) DEFAULT NULL             COMMENT '取值范围描述',
  `example_value`     VARCHAR(256) DEFAULT NULL             COMMENT '示例值',
  `description`       VARCHAR(512) DEFAULT NULL             COMMENT '变量说明',
  `sort_order`        INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  `status`            TINYINT      NOT NULL DEFAULT 1       COMMENT '资源状态：-1=已逻辑删除，0=停用，1=启用',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_project_var` (`scope`, `project_id`, `var_code`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_var_source` (`var_source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则变量表（普通变量与常量）';

CREATE TABLE IF NOT EXISTS `rule_field_validation` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`       BIGINT       NOT NULL DEFAULT 0       COMMENT '所属项目ID，0表示全局',
  `scope`            VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL=全局，PROJECT=项目级',
  `validation_code`  VARCHAR(128) NOT NULL                 COMMENT '校验编码',
  `validation_name`  VARCHAR(128) NOT NULL                 COMMENT '校验名称',
  `validation_type`  VARCHAR(32)  NOT NULL                 COMMENT '校验类型：REQUIRED=必填，REGEX=正则表达式，MIN_VALUE=数值下限，MAX_VALUE=数值上限，MIN_LENGTH=长度下限，MAX_LENGTH=长度上限，IN=必须属于集合，NOT_IN=不得属于集合',
  `validation_value` TEXT         DEFAULT NULL             COMMENT '校验值',
  `error_message`    VARCHAR(512) NOT NULL                 COMMENT '校验失败提示',
  `description`      VARCHAR(512) DEFAULT NULL             COMMENT '说明',
  `status`           TINYINT      NOT NULL DEFAULT 1       COMMENT '资源状态：-1=已逻辑删除，0=停用，1=启用',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_field_validation_scope_code` (`scope`, `project_id`, `validation_code`),
  KEY `idx_field_validation_project` (`project_id`),
  KEY `idx_field_validation_type_status` (`validation_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段校验规则库';

CREATE TABLE IF NOT EXISTS `rule_variable_option` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `variable_id`  BIGINT       NOT NULL                COMMENT '所属变量ID',
  `option_value` VARCHAR(256) NOT NULL                COMMENT '选项值',
  `option_label` VARCHAR(256) NOT NULL                COMMENT '选项中文标签',
  `sort_order`   INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  PRIMARY KEY (`id`),
  KEY `idx_variable_id` (`variable_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则变量选项表（枚举变量的可选值）';

CREATE TABLE IF NOT EXISTS `rule_list_library` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`   BIGINT       NOT NULL DEFAULT 0       COMMENT '所属项目ID，0 表示全局',
  `scope`        VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL=全局，PROJECT=项目级',
  `list_code`    VARCHAR(128) NOT NULL                 COMMENT '名单库编码',
  `list_name`    VARCHAR(128) NOT NULL                 COMMENT '名单库名称',
  `list_type`    VARCHAR(32)  NOT NULL DEFAULT 'BLACK' COMMENT '名单库标签：BLACK=黑名单，GREY=灰名单，WHITE=白名单，OTHER=其他；仅用于分类标识',
  `description`  VARCHAR(512) DEFAULT NULL             COMMENT '说明',
  `status`       TINYINT      NOT NULL DEFAULT 1       COMMENT '资源状态：-1=已逻辑删除，0=停用，1=启用',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_list_scope_project_code` (`scope`, `project_id`, `list_code`),
  KEY `idx_list_project_id` (`project_id`),
  KEY `idx_list_type_status` (`list_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单库配置表';

CREATE TABLE IF NOT EXISTS `rule_list_record` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `list_id`        BIGINT       NOT NULL                COMMENT '名单库ID',
  `item_type`      VARCHAR(32)  NOT NULL                COMMENT '名单内容类型：MOBILE=手机号，ID_CARD=身份证号，ADDRESS=地址，IP=网络地址，DEVICE=设备标识，NAME=姓名，GPS=地理坐标，EMAIL=邮箱，BANK_CARD=银行卡号，OTHER=其他',
  `item_content`   VARCHAR(512) NOT NULL                COMMENT '名单内容',
  `effective_time` DATETIME     DEFAULT NULL            COMMENT '生效时间',
  `expire_time`    DATETIME     DEFAULT NULL            COMMENT '失效时间',
  `reason`         VARCHAR(512) DEFAULT NULL            COMMENT '插入原因',
  `remark`         VARCHAR(512) DEFAULT NULL            COMMENT '插入备注',
  `last_operation` VARCHAR(16)  NOT NULL DEFAULT 'ADD'  COMMENT '最近变更操作：ADD=新增，UPDATE=修改，DELETE=删除',
  `status`         TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0-停用，1-启用',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_list_type_content` (`list_id`, `item_type`, `item_content`),
  KEY `idx_list_record_lookup` (`list_id`, `item_type`, `item_content`, `status`),
  KEY `idx_list_record_content_status` (`list_id`, `item_content`, `status`),
  KEY `idx_list_record_effective_lookup` (`list_id`, `status`, `effective_time`, `expire_time`),
  KEY `idx_list_record_effective` (`effective_time`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单当前记录表';

CREATE TABLE IF NOT EXISTS `rule_list_action_receipt` (
  `task_id` VARCHAR(64) NOT NULL COMMENT '名单动作任务唯一标识，用于幂等去重',
  `project_id` BIGINT DEFAULT NULL COMMENT '所属项目标识，关联 rule_project.id；0 表示全局资源',
  `root_trace_id` CHAR(36) DEFAULT NULL COMMENT '本次逻辑执行的根追踪标识',
  `rule_code` VARCHAR(128) DEFAULT NULL COMMENT '规则编码快照',
  `rule_version` VARCHAR(32) DEFAULT NULL COMMENT '执行时的规则业务版本号',
  `revision_id` VARCHAR(64) DEFAULT NULL COMMENT '规则修订标识，关联 rule_revision.id',
  `artifact_digest` CHAR(64) DEFAULT NULL COMMENT '决策制品的 SHA-256 摘要',
  `changed_count` INT NOT NULL DEFAULT 0 COMMENT '已实际变更的名单记录数量',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步名单动作幂等回执';

CREATE TABLE IF NOT EXISTS `rule_list_record_log` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `list_id`        BIGINT       NOT NULL                COMMENT '名单库ID',
  `record_id`      BIGINT       DEFAULT NULL            COMMENT '名单记录ID',
  `item_type`      VARCHAR(32)  NOT NULL                COMMENT '名单内容类型：MOBILE=手机号，ID_CARD=身份证号，ADDRESS=地址，IP=网络地址，DEVICE=设备标识，NAME=姓名，GPS=地理坐标，EMAIL=邮箱，BANK_CARD=银行卡号，OTHER=其他',
  `item_content`   VARCHAR(512) NOT NULL                COMMENT '名单内容',
  `effective_time` DATETIME     DEFAULT NULL            COMMENT '生效时间',
  `expire_time`    DATETIME     DEFAULT NULL            COMMENT '失效时间',
  `reason`         VARCHAR(512) DEFAULT NULL            COMMENT '插入原因',
  `remark`         VARCHAR(512) DEFAULT NULL            COMMENT '插入备注',
  `operation`      VARCHAR(16)  NOT NULL                COMMENT '名单变更操作：ADD=新增，UPDATE=修改，DELETE=删除',
  `operator`       VARCHAR(64)  DEFAULT NULL            COMMENT '操作人',
  `task_id`        VARCHAR(64)  DEFAULT NULL            COMMENT '规则名单动作任务ID',
  `root_trace_id`  CHAR(36)     DEFAULT NULL            COMMENT '规则根追踪 ID',
  `rule_code`      VARCHAR(128) DEFAULT NULL            COMMENT '规则编码',
  `rule_version`   VARCHAR(32)  DEFAULT NULL            COMMENT '规则版本',
  `revision_id`    VARCHAR(64)  DEFAULT NULL            COMMENT '规则修订ID',
  `artifact_digest` CHAR(64)    DEFAULT NULL            COMMENT '规则制品摘要',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_list_log_list_record` (`list_id`, `record_id`),
  KEY `idx_list_log_content` (`list_id`, `item_type`, `item_content`),
  KEY `idx_list_log_task_trace` (`task_id`, `root_trace_id`, `create_time`),
  KEY `idx_list_log_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单变更日志表';

CREATE TABLE IF NOT EXISTS `rule_list_change_batch` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `list_id`             BIGINT       NOT NULL                COMMENT '名单库ID',
  `source_type`         VARCHAR(16)  NOT NULL                COMMENT '变更来源：SINGLE=单条编辑，IMPORT=批量导入',
  `status`              VARCHAR(32)  NOT NULL                COMMENT '批次状态：STAGED=已暂存待提交，VALIDATION_FAILED=校验失败，APPLIED=已应用，REJECTED=审批驳回，CANCELLED=申请撤销，CONFLICT=并发冲突',
  `total_count`         INT          NOT NULL DEFAULT 0      COMMENT '总行数',
  `add_count`           INT          NOT NULL DEFAULT 0      COMMENT '新增数',
  `update_count`        INT          NOT NULL DEFAULT 0      COMMENT '更新数',
  `delete_count`        INT          NOT NULL DEFAULT 0      COMMENT '删除数',
  `duplicate_count`     INT          NOT NULL DEFAULT 0      COMMENT '重复数',
  `invalid_count`       INT          NOT NULL DEFAULT 0      COMMENT '无效数',
  `content_digest`      CHAR(64)     NOT NULL                COMMENT '规范化批次内容SHA-256',
  `approval_request_id` BIGINT       DEFAULT NULL            COMMENT '审批申请ID',
  `created_by`          VARCHAR(64)  NOT NULL                COMMENT '创建人',
  `applied_by`          VARCHAR(64)  DEFAULT NULL            COMMENT '应用人',
  `applied_time`        DATETIME     DEFAULT NULL            COMMENT '应用时间',
  `terminal_message`    VARCHAR(512) DEFAULT NULL            COMMENT '终止或失败说明',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_list_change_batch_list_status` (`list_id`, `status`),
  KEY `idx_list_change_batch_approval` (`approval_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单变更暂存批次';

CREATE TABLE IF NOT EXISTS `rule_list_change_item` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_id`          BIGINT       NOT NULL                COMMENT '变更批次ID',
  `source_row`        INT          NOT NULL                COMMENT '来源行号',
  `operation`         VARCHAR(16)  DEFAULT NULL            COMMENT '名单变更操作：ADD=新增，UPDATE=修改，DELETE=删除',
  `target_record_id`  BIGINT       DEFAULT NULL            COMMENT '目标生效记录ID',
  `item_type`         VARCHAR(32)  DEFAULT NULL            COMMENT '名单内容类型：MOBILE=手机号，ID_CARD=身份证号，ADDRESS=地址，IP=网络地址，DEVICE=设备标识，NAME=姓名，GPS=地理坐标，EMAIL=邮箱，BANK_CARD=银行卡号，OTHER=其他',
  `item_content`      VARCHAR(512) DEFAULT NULL            COMMENT '名单内容',
  `effective_time`    DATETIME     DEFAULT NULL            COMMENT '生效时间',
  `expire_time`       DATETIME     DEFAULT NULL            COMMENT '失效时间',
  `reason`            VARCHAR(512) DEFAULT NULL            COMMENT '原因',
  `remark`            VARCHAR(512) DEFAULT NULL            COMMENT '备注',
  `target_status`     TINYINT      DEFAULT NULL            COMMENT '应用后的名单状态：0=停用，1=启用',
  `validation_status` VARCHAR(16)  NOT NULL                COMMENT '行校验结果：VALID=有效，DUPLICATE=重复，INVALID=无效',
  `validation_message` VARCHAR(512) DEFAULT NULL           COMMENT '校验信息',
  `baseline_digest`   CHAR(64)     DEFAULT NULL            COMMENT '目标记录基线SHA-256',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_list_change_item_row` (`batch_id`, `source_row`),
  KEY `idx_list_change_item_batch_status` (`batch_id`, `validation_status`),
  KEY `idx_list_change_item_target` (`target_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单变更暂存明细';

CREATE TABLE IF NOT EXISTS `rule_function` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`    BIGINT       NOT NULL                COMMENT '所属项目ID',
  `scope`         VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL=全局，PROJECT=项目级',
  `func_code`     VARCHAR(128) NOT NULL                COMMENT '函数编码（QLExpress 中的函数名）',
  `func_name`     VARCHAR(128) NOT NULL                COMMENT '函数中文名称',
  `description`   VARCHAR(512) DEFAULT NULL             COMMENT '函数说明',
  `params_json`   TEXT         DEFAULT NULL             COMMENT '参数定义JSON [{"name":"a","type":"NUMBER","label":"金额"}]',
  `return_type`   VARCHAR(32)  DEFAULT 'STRING'         COMMENT '返回数据类型：STRING=字符串，NUMBER=数值，INTEGER=整数，DOUBLE=双精度数值，BOOLEAN=布尔，DATE=日期，ENUM=枚举，OBJECT=对象，LIST=列表，MAP=映射,ANY=任意类型',
  `impl_type`     VARCHAR(16)  NOT NULL DEFAULT 'SCRIPT' COMMENT '实现方式：SCRIPT-QLExpress脚本/JAVA-Java类/BEAN-Spring Bean',
  `impl_script`   TEXT         DEFAULT NULL             COMMENT 'QLExpress 实现脚本（SCRIPT 类型）',
  `impl_class`    VARCHAR(256) DEFAULT NULL             COMMENT 'Java 实现类全限定名（JAVA 类型）',
  `impl_method`   VARCHAR(128) DEFAULT NULL             COMMENT '方法名（JAVA/BEAN 类型时指定）',
  `impl_bean_name` VARCHAR(128) DEFAULT NULL            COMMENT 'Spring Bean 名称（BEAN 类型时指定）',
  `status`        TINYINT      NOT NULL DEFAULT 1       COMMENT '资源状态：-1=已逻辑删除，0=停用，1=启用',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_project_func` (`scope`, `project_id`, `func_code`),
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自定义函数定义表';

CREATE TABLE IF NOT EXISTS `rule_function_version` (
  `business_version` INT DEFAULT NULL COMMENT '业务版本号',
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `function_id`   BIGINT       NOT NULL                COMMENT '所属函数标识，关联 rule_function.id',
  `version`       INT          NOT NULL                COMMENT '不可变函数快照序号',
  `function_json` TEXT         NOT NULL                COMMENT '函数定义的版本快照，JSON 格式',
  `change_log`    VARCHAR(512) DEFAULT NULL            COMMENT '版本变更说明',
  `publish_by`    VARCHAR(64)  DEFAULT NULL            COMMENT '保存版本快照的操作人',
  `publish_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '版本快照保存时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_function_version` (`function_id`, `version`),
  KEY `idx_function_version_time` (`function_id`, `publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='函数版本历史表';

CREATE TABLE IF NOT EXISTS `rule_execution_log` (
  `root_rule_id` BIGINT DEFAULT NULL COMMENT '根规则ID快照',
  `execution_project_id` BIGINT DEFAULT NULL COMMENT '执行项目ID快照',
  `started_at` DATETIME(6) DEFAULT NULL COMMENT '根请求开始时间',
  `history_fields` LONGTEXT DEFAULT NULL COMMENT '按ID存储的请求和显式记录结果快照',
   `revision_id`     BIGINT        DEFAULT NULL             COMMENT '规则修订标识，关联 rule_revision.id',
   `artifact_digest` CHAR(64)      DEFAULT NULL             COMMENT '决策制品的 SHA-256 摘要',
   `attempt_no`      INT           DEFAULT NULL             COMMENT '逻辑执行恢复尝试次数',
   `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
   `trace_id`        CHAR(36)      DEFAULT NULL             COMMENT '全局唯一追踪 ID',
   `rule_code`       VARCHAR(128)  NOT NULL                COMMENT '规则编码',
   `project_code`    VARCHAR(128)  DEFAULT NULL             COMMENT '项目编码',
   `rule_version`    INT           DEFAULT NULL             COMMENT '规则版本号',
   `model_type`      VARCHAR(16)   DEFAULT NULL             COMMENT '决策模型类型：TABLE=决策表，TREE=决策树，FLOW=决策流，RULE_SET=规则集，CROSS=交叉表，SCORE=评分卡，CROSS_ADV=复杂交叉表，SCORE_ADV=复杂评分卡，SCRIPT=脚本',
   `source`          VARCHAR(32)   NOT NULL DEFAULT 'SERVER' COMMENT '执行来源：SERVER=服务端执行，CLIENT=客户端本地执行',
   `client_app_name` VARCHAR(128)  DEFAULT NULL             COMMENT '客户端应用名称',
   `client_ip`       VARCHAR(64)   DEFAULT NULL             COMMENT '客户端IP',
   `auth_id`         BIGINT        DEFAULT NULL             COMMENT '鉴权配置ID',
   `auth_code`       VARCHAR(128)  DEFAULT NULL             COMMENT '鉴权配置编码快照',
   `auth_type`       VARCHAR(32)   DEFAULT NULL             COMMENT '鉴权类型快照：LEGACY_TOKEN=项目访问令牌，API_KEY=接口密钥，BASIC=账号密码，HMAC_SHA256=摘要签名，BEARER_TOKEN=临时访问令牌，GLOBAL_KEY=全局公私钥签名',
   `token_id`        BIGINT        DEFAULT NULL             COMMENT '临时令牌 ID',
   `token_code`      VARCHAR(128)  DEFAULT NULL             COMMENT '临时令牌编码快照',
   `auth_phase`      VARCHAR(16)   DEFAULT NULL             COMMENT '鉴权阶段：DIRECT=长期凭据直接认证，VALID=临时令牌有效期，GRACE=临时令牌宽限期',
   `input_params`    LONGTEXT      DEFAULT NULL             COMMENT '输入参数（JSON）',
   `output_result`   LONGTEXT      DEFAULT NULL             COMMENT '输出结果（JSON）',
   `trace_info`      LONGTEXT      DEFAULT NULL             COMMENT '表达式追踪树（JSON）',
   `rule_set_stats_status` VARCHAR(16) DEFAULT NULL COMMENT '统计提取状态：READY=已生成，NO_TRACE=无追踪，INVALID=追踪无效，TRUNCATED=追踪已截断；空值表示待提取',
   `rule_set_stats_version` INT DEFAULT NULL COMMENT '统计提取格式版本，NULL待补齐',
   `success`         TINYINT       NOT NULL DEFAULT 1       COMMENT '执行是否成功：0=否，1=是',
   `error_message`   VARCHAR(1024) DEFAULT NULL             COMMENT '错误信息',
   `execute_time_ms` BIGINT        DEFAULT NULL             COMMENT '执行耗时（毫秒）',
   `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
   PRIMARY KEY (`id`, `create_time`),
   KEY `idx_rule_code` (`rule_code`, `create_time`),
   KEY `idx_project_code` (`project_code`, `create_time`),
   KEY `idx_source` (`source`, `create_time`),
    KEY `idx_dashboard_execution_time_project` (`create_time`, `project_code`),
    KEY `idx_execution_project_time` (`execution_project_id`, `create_time`, `id`),
    KEY `idx_dashboard_execution_rule_time` (`project_code`, `rule_code`, `create_time`),
    KEY `idx_execution_trace` (`trace_id`, `create_time`),
    KEY `idx_rule_set_stats_pending` (`rule_set_stats_version`, `id`, `create_time`),
    KEY `idx_client_app` (`client_app_name`, `create_time`),
    KEY `idx_execution_log_auth` (`auth_id`, `token_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    COMMENT='规则执行日志表（按月分区）'
    PARTITION BY RANGE (TO_DAYS(`create_time`)) (
        -- 2026年
        PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')) COMMENT '2026年01月',
        PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')) COMMENT '2026年02月',
        PARTITION p202603 VALUES LESS THAN (TO_DAYS('2026-04-01')) COMMENT '2026年03月',
        PARTITION p202604 VALUES LESS THAN (TO_DAYS('2026-05-01')) COMMENT '2026年04月',
        PARTITION p202605 VALUES LESS THAN (TO_DAYS('2026-06-01')) COMMENT '2026年05月',
        PARTITION p202606 VALUES LESS THAN (TO_DAYS('2026-07-01')) COMMENT '2026年06月',
        PARTITION p202607 VALUES LESS THAN (TO_DAYS('2026-08-01')) COMMENT '2026年07月',
        PARTITION p202608 VALUES LESS THAN (TO_DAYS('2026-09-01')) COMMENT '2026年08月',
        PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01')) COMMENT '2026年09月',
        PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')) COMMENT '2026年10月',
        PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')) COMMENT '2026年11月',
        PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01')) COMMENT '2026年12月',
        -- 2027年
        PARTITION p202701 VALUES LESS THAN (TO_DAYS('2027-02-01')) COMMENT '2027年01月',
        PARTITION p202702 VALUES LESS THAN (TO_DAYS('2027-03-01')) COMMENT '2027年02月',
        PARTITION p202703 VALUES LESS THAN (TO_DAYS('2027-04-01')) COMMENT '2027年03月',
        PARTITION p202704 VALUES LESS THAN (TO_DAYS('2027-05-01')) COMMENT '2027年04月',
        PARTITION p202705 VALUES LESS THAN (TO_DAYS('2027-06-01')) COMMENT '2027年05月',
        PARTITION p202706 VALUES LESS THAN (TO_DAYS('2027-07-01')) COMMENT '2027年06月',
        PARTITION p202707 VALUES LESS THAN (TO_DAYS('2027-08-01')) COMMENT '2027年07月',
        PARTITION p202708 VALUES LESS THAN (TO_DAYS('2027-09-01')) COMMENT '2027年08月',
        PARTITION p202709 VALUES LESS THAN (TO_DAYS('2027-10-01')) COMMENT '2027年09月',
        PARTITION p202710 VALUES LESS THAN (TO_DAYS('2027-11-01')) COMMENT '2027年10月',
        PARTITION p202711 VALUES LESS THAN (TO_DAYS('2027-12-01')) COMMENT '2027年11月',
        PARTITION p202712 VALUES LESS THAN (TO_DAYS('2028-01-01')) COMMENT '2027年12月',
        -- 2028年
        PARTITION p202801 VALUES LESS THAN (TO_DAYS('2028-02-01')) COMMENT '2028年01月',
        PARTITION p202802 VALUES LESS THAN (TO_DAYS('2028-03-01')) COMMENT '2028年02月',
        PARTITION p202803 VALUES LESS THAN (TO_DAYS('2028-04-01')) COMMENT '2028年03月',
        PARTITION p202804 VALUES LESS THAN (TO_DAYS('2028-05-01')) COMMENT '2028年04月',
        PARTITION p202805 VALUES LESS THAN (TO_DAYS('2028-06-01')) COMMENT '2028年05月',
        PARTITION p202806 VALUES LESS THAN (TO_DAYS('2028-07-01')) COMMENT '2028年06月',
        PARTITION p202807 VALUES LESS THAN (TO_DAYS('2028-08-01')) COMMENT '2028年07月',
        PARTITION p202808 VALUES LESS THAN (TO_DAYS('2028-09-01')) COMMENT '2028年08月',
        PARTITION p202809 VALUES LESS THAN (TO_DAYS('2028-10-01')) COMMENT '2028年09月',
        PARTITION p202810 VALUES LESS THAN (TO_DAYS('2028-11-01')) COMMENT '2028年10月',
        PARTITION p202811 VALUES LESS THAN (TO_DAYS('2028-12-01')) COMMENT '2028年11月',
        PARTITION p202812 VALUES LESS THAN (TO_DAYS('2029-01-01')) COMMENT '2028年12月',
        -- 2029年
        PARTITION p202901 VALUES LESS THAN (TO_DAYS('2029-02-01')) COMMENT '2029年01月',
        PARTITION p202902 VALUES LESS THAN (TO_DAYS('2029-03-01')) COMMENT '2029年02月',
        PARTITION p202903 VALUES LESS THAN (TO_DAYS('2029-04-01')) COMMENT '2029年03月',
        PARTITION p202904 VALUES LESS THAN (TO_DAYS('2029-05-01')) COMMENT '2029年04月',
        PARTITION p202905 VALUES LESS THAN (TO_DAYS('2029-06-01')) COMMENT '2029年05月',
        PARTITION p202906 VALUES LESS THAN (TO_DAYS('2029-07-01')) COMMENT '2029年06月',
        PARTITION p202907 VALUES LESS THAN (TO_DAYS('2029-08-01')) COMMENT '2029年07月',
        PARTITION p202908 VALUES LESS THAN (TO_DAYS('2029-09-01')) COMMENT '2029年08月',
        PARTITION p202909 VALUES LESS THAN (TO_DAYS('2029-10-01')) COMMENT '2029年09月',
        PARTITION p202910 VALUES LESS THAN (TO_DAYS('2029-11-01')) COMMENT '2029年10月',
        PARTITION p202911 VALUES LESS THAN (TO_DAYS('2029-12-01')) COMMENT '2029年11月',
        PARTITION p202912 VALUES LESS THAN (TO_DAYS('2030-01-01')) COMMENT '2029年12月',
        -- 2030年
        PARTITION p203001 VALUES LESS THAN (TO_DAYS('2030-02-01')) COMMENT '2030年01月',
        PARTITION p203002 VALUES LESS THAN (TO_DAYS('2030-03-01')) COMMENT '2030年02月',
        PARTITION p203003 VALUES LESS THAN (TO_DAYS('2030-04-01')) COMMENT '2030年03月',
        PARTITION p203004 VALUES LESS THAN (TO_DAYS('2030-05-01')) COMMENT '2030年04月',
        PARTITION p203005 VALUES LESS THAN (TO_DAYS('2030-06-01')) COMMENT '2030年05月',
        PARTITION p203006 VALUES LESS THAN (TO_DAYS('2030-07-01')) COMMENT '2030年06月',
        PARTITION p203007 VALUES LESS THAN (TO_DAYS('2030-08-01')) COMMENT '2030年07月',
        PARTITION p203008 VALUES LESS THAN (TO_DAYS('2030-09-01')) COMMENT '2030年08月',
        PARTITION p203009 VALUES LESS THAN (TO_DAYS('2030-10-01')) COMMENT '2030年09月',
        PARTITION p203010 VALUES LESS THAN (TO_DAYS('2030-11-01')) COMMENT '2030年10月',
        PARTITION p203011 VALUES LESS THAN (TO_DAYS('2030-12-01')) COMMENT '2030年11月',
        PARTITION p203012 VALUES LESS THAN (TO_DAYS('2031-01-01')) COMMENT '2030年12月',
        -- 2031年
        PARTITION p203101 VALUES LESS THAN (TO_DAYS('2031-02-01')) COMMENT '2031年01月',
        PARTITION p203102 VALUES LESS THAN (TO_DAYS('2031-03-01')) COMMENT '2031年02月',
        PARTITION p203103 VALUES LESS THAN (TO_DAYS('2031-04-01')) COMMENT '2031年03月',
        PARTITION p203104 VALUES LESS THAN (TO_DAYS('2031-05-01')) COMMENT '2031年04月',
        PARTITION p203105 VALUES LESS THAN (TO_DAYS('2031-06-01')) COMMENT '2031年05月',
        PARTITION p203106 VALUES LESS THAN (TO_DAYS('2031-07-01')) COMMENT '2031年06月',
        PARTITION p203107 VALUES LESS THAN (TO_DAYS('2031-08-01')) COMMENT '2031年07月',
        PARTITION p203108 VALUES LESS THAN (TO_DAYS('2031-09-01')) COMMENT '2031年08月',
        PARTITION p203109 VALUES LESS THAN (TO_DAYS('2031-10-01')) COMMENT '2031年09月',
        PARTITION p203110 VALUES LESS THAN (TO_DAYS('2031-11-01')) COMMENT '2031年10月',
        PARTITION p203111 VALUES LESS THAN (TO_DAYS('2031-12-01')) COMMENT '2031年11月',
        PARTITION p203112 VALUES LESS THAN (TO_DAYS('2032-01-01')) COMMENT '2031年12月',
        PARTITION p_future VALUES LESS THAN MAXVALUE              COMMENT '兜底分区'
        );

-- 不分区的逻辑身份表负责 trace 全局唯一；原分区审计行完整保留。
CREATE TABLE IF NOT EXISTS `rule_execution_statistics` (
  `id` BIGINT NOT NULL COMMENT '对应执行日志的物理标识，关联 rule_execution_log.id',
  `create_time` DATETIME NOT NULL COMMENT '对应执行日志的创建时间，与 id 共同定位分区行',
  `trace_id` CHAR(36) DEFAULT NULL COMMENT '执行追踪唯一标识',
  `attempt_no` INT DEFAULT NULL COMMENT '同一逻辑请求的执行尝试序号',
  `project_code` VARCHAR(128) DEFAULT NULL COMMENT '所属项目编码快照',
  `rule_set_stats_json` JSON NOT NULL COMMENT '从执行追踪提取的精简规则集统计快照，JSON 格式',
  `rule_set_stats_status` VARCHAR(16) NOT NULL COMMENT '统计提取状态：READY=已生成，NO_TRACE=无追踪，INVALID=追踪无效，TRUNCATED=追踪已截断；空值表示待提取',
  `format_version` INT NOT NULL DEFAULT 1 COMMENT '规则集统计提取格式版本，当前为 2',
  PRIMARY KEY (`id`,`create_time`),
  KEY `idx_execution_statistics_time` (`create_time`,`project_code`),
  KEY `idx_execution_statistics_project` (`project_code`,`create_time`),
  KEY `idx_execution_statistics_trace` (`trace_id`,`create_time`),
  KEY `idx_execution_statistics_scope` (`format_version`,`project_code`,`create_time`,`trace_id`,`rule_set_stats_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行日志的精简规则集统计快照';

CREATE TABLE IF NOT EXISTS `rule_set_statistics_event` (
  `log_id` BIGINT NOT NULL COMMENT '所属执行日志的物理标识，与 create_time 共同关联日志行',
  `create_time` DATETIME NOT NULL COMMENT '所属执行日志创建时间，与 log_id 共同定位分区行',
  `frame_index` INT NOT NULL COMMENT '规则集执行帧在统计快照中的序号，从 1 开始',
  `item_index` INT NOT NULL COMMENT '规则集内规则项序号，从 1 开始；0 表示规则集父帧',
  `order_key` VARBINARY(22) GENERATED ALWAYS AS (UNHEX(CONCAT(LPAD(HEX(TO_SECONDS(`create_time`)),12,'0'),LPAD(HEX(`log_id`),16,'0'),LPAD(HEX(`frame_index`),8,'0'),LPAD(HEX(`item_index`),8,'0')))) STORED COMMENT '由执行时间、日志标识、帧序号和项序号生成的稳定排序键',
  `kind` VARCHAR(8) NOT NULL COMMENT '统计素材类型：RULE=规则集执行帧，ITEM=规则集内规则项',
  `rule_key` VARCHAR(80) NOT NULL COMMENT '规则集统计聚合键',
  `item_key` VARCHAR(64) NOT NULL COMMENT '规则项统计聚合键；规则集父帧使用空字符串',
  `rule_id` BIGINT DEFAULT NULL COMMENT '规则定义标识，关联 rule_definition.id',
  `parent_code` LONGTEXT DEFAULT NULL COMMENT '所属规则集的编码快照',
  `rule_code` LONGTEXT DEFAULT NULL COMMENT '规则编码快照',
  `rule_name` LONGTEXT DEFAULT NULL COMMENT '规则或规则项名称快照',
  `cost` BIGINT NOT NULL COMMENT '执行耗时，单位毫秒；规则项素材记为 0',
  `hit` TINYINT NOT NULL COMMENT '是否命中：0=未命中，1=命中',
  `failed` TINYINT NOT NULL COMMENT '是否执行失败：0=未失败，1=失败',
  PRIMARY KEY (`log_id`,`create_time`,`frame_index`,`item_index`),
  UNIQUE KEY `uk_rule_set_event_order` (`order_key`),
  KEY `idx_rule_set_event_time` (`create_time`,`kind`,`rule_key`,`cost`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实际执行的规则集帧与规则项统计素材';

CREATE TABLE IF NOT EXISTS `rule_execution_log_identity` (
  `trace_id` CHAR(36) NOT NULL COMMENT '逻辑执行追踪标识，跨分区全局唯一',
  `log_id` BIGINT DEFAULT NULL COMMENT '当前逻辑执行对应的物理日志标识，空值表示尚未关联日志',
  `log_create_time` DATETIME(6) DEFAULT NULL COMMENT '逻辑执行所指向的物理日志创建时间，与 log_id 共同定位分区行',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`trace_id`),
  UNIQUE KEY `uk_execution_log_identity_row` (`log_id`, `log_create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='逻辑执行日志的稳定物理位置';

-- 逻辑读取视图由 SchemaSync 补齐旧库列之后执行 logical-execution-log-view.sql 创建。

CREATE TABLE IF NOT EXISTS `rule_model` (
  `model_digest`            CHAR(64)     DEFAULT NULL       COMMENT '模型文件原始内容的 SHA-256 摘要',
  `input_schema_json`       LONGTEXT     DEFAULT NULL       COMMENT '精确输入字段结构及类型约束，JSON 格式',
  `output_schema_json`      LONGTEXT     DEFAULT NULL       COMMENT '精确输出字段结构及类型约束，JSON 格式',
  `validation_report_json`  LONGTEXT     DEFAULT NULL       COMMENT '制品或模型的校验报告，JSON 格式',
  `runtime_constraints_json` LONGTEXT    DEFAULT NULL       COMMENT '运行环境与依赖版本约束，JSON 格式',
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`         BIGINT       DEFAULT NULL             COMMENT '所属项目ID（全局模型可为空）',
  `project_code`       VARCHAR(64)  DEFAULT NULL             COMMENT '所属项目编码',
  `project_name`       VARCHAR(128) DEFAULT NULL             COMMENT '所属项目名称',
  `scope`              VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL=全局，PROJECT=项目级',
  `model_code`         VARCHAR(128) NOT NULL                COMMENT '模型编码（唯一）',
  `model_code_scope_key` VARCHAR(256) GENERATED ALWAYS AS (IF(`scope` = 'GLOBAL', CONCAT('GLOBAL:', `model_code`), CONCAT('PROJECT:', COALESCE(`project_id`, 0), ':', `model_code`))) STORED COMMENT '模型编码作用域唯一键',
  `model_name`         VARCHAR(256) NOT NULL                COMMENT '模型名称（中文）',
  `model_type`         VARCHAR(32)  NOT NULL                COMMENT '模型类型：LR=逻辑回归，XGBOOST=梯度提升树，LIGHTGBM=轻量梯度提升树，CATBOOST=类别特征提升树，RANDOM_FOREST=随机森林，NEURAL_NET=神经网络，SVM=支持向量机，CLASSIFICATION=分类，REGRESSION=回归，CLUSTERING=聚类，ML=通用机器学习',
  `model_format`       VARCHAR(32)  NOT NULL                COMMENT '模型文件格式：PMML=预测模型标记语言，ONNX=开放神经网络交换格式；当前仅支持这两种格式',
  `description`        VARCHAR(512) DEFAULT NULL             COMMENT '模型描述',
  `model_content`      LONGTEXT     DEFAULT NULL             COMMENT '模型文件原始内容（Base64编码）',
  `model_file_name`    VARCHAR(256) DEFAULT NULL             COMMENT '上传时的文件名',
  `model_file_size`    BIGINT       DEFAULT NULL             COMMENT '文件大小（字节）',
  `model_config`       JSON         DEFAULT NULL             COMMENT '模型特有配置（格式无关JSON）',
  `preload_on_startup` TINYINT      NOT NULL DEFAULT 0       COMMENT '服务启动时预加载：0-否，1-是',
  `execution_timeout_ms` INT        NOT NULL DEFAULT 120000  COMMENT '单次模型执行超时时间（毫秒）',
  `input_field_count`  INT          DEFAULT NULL             COMMENT '输入字段数量',
  `output_field_count` INT          DEFAULT NULL             COMMENT '输出字段数量',
  `target_categories`  VARCHAR(256) DEFAULT NULL             COMMENT '目标变量类别数（分类模型）',
  `model_version`      VARCHAR(64)  DEFAULT NULL             COMMENT '模型自身的版本号',
  `training_info`      JSON         DEFAULT NULL             COMMENT '训练信息（特征重要性等）',
  `current_version`     INT          NOT NULL DEFAULT 0       COMMENT '平台当前设计版本号',
  `business_version`    INT          DEFAULT NULL             COMMENT '业务版本号',
  `published_version`   INT          DEFAULT NULL             COMMENT '平台已发布版本号',
  `status`             TINYINT      NOT NULL DEFAULT 1       COMMENT '资源状态：-1=已逻辑删除，0=停用，1=启用',
  `create_by`          VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
  `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          VARCHAR(64)  DEFAULT NULL             COMMENT '更新人',
  `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_code_scope` (`model_code_scope_key`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_scope` (`scope`),
  KEY `idx_model_format` (`model_format`),
  KEY `idx_model_type` (`model_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一模型主表';

CREATE TABLE IF NOT EXISTS `rule_model_input_field` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_id`         BIGINT       NOT NULL                COMMENT '所属模型ID',
  `var_id`          BIGINT       DEFAULT NULL             COMMENT '关联字段ID，需结合 ref_type 判断所属资源表',
  `ref_type`        VARCHAR(32)  DEFAULT NULL             COMMENT '引用类型：VARIABLE=变量，CONSTANT=常量，DATA_OBJECT=对象字段，DATA_OBJECT_ROOT=对象整体，MODEL=模型，MODEL_OUTPUT=模型输出字段',
  `field_name`       VARCHAR(128) NOT NULL                COMMENT '字段名称（原始名称）',
  `field_label`      VARCHAR(128) NOT NULL                COMMENT '字段中文名称',
  `script_name`      VARCHAR(128) DEFAULT NULL             COMMENT '脚本引用名称，原样保留用户配置，不自动转换大小写或命名格式',
  `field_type`       VARCHAR(32)  NOT NULL                COMMENT '字段数据类型：STRING=字符串，NUMBER=数值，INTEGER=整数，DOUBLE=双精度数值，BOOLEAN=布尔，DATE=日期，ENUM=枚举，OBJECT=对象，LIST=列表，MAP=映射',
  `data_type`        VARCHAR(32)  DEFAULT NULL             COMMENT '模型字段用途类型：CONTINUOUS=连续值，CATEGORICAL=类别值，ORDINAL=有序类别',
  `default_value`    VARCHAR(256) DEFAULT NULL             COMMENT '默认值',
  `source_operand`   JSON         DEFAULT NULL             COMMENT '模型输入来源 操作数表达式',
  `default_operand`  JSON         DEFAULT NULL             COMMENT '模型输入默认值 操作数表达式',
  `valid_values`     TEXT         DEFAULT NULL             COMMENT '有效值列表（JSON数组，分类变量）',
  `feature_name`     VARCHAR(128) DEFAULT NULL             COMMENT '模型内部特征名称（如XGBoost的f0）',
  `transform_type`   VARCHAR(32)  DEFAULT NULL             COMMENT '输入转换类型：NONE=不转换，NORMALIZE=标准化，DISCRETIZE=离散分箱，MAPVALUES=值映射，MINMAX=区间缩放',
  `transform_params` JSON         DEFAULT NULL             COMMENT '预处理参数',
  `importance_score` DECIMAL(10,6) DEFAULT NULL             COMMENT '特征重要性得分',
  `sort_order`       INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  `status`           TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_model_id` (`model_id`),
  KEY `idx_var_id` (`var_id`),
  KEY `idx_ref_type_var_id` (`ref_type`, `var_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一模型输入字段表';

CREATE TABLE IF NOT EXISTS `rule_model_output_field` (
  `record_result`    TINYINT NOT NULL DEFAULT 0 COMMENT '是否显式记录字段结果供历史统计：0=否，1=是',
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_id`         BIGINT       NOT NULL                COMMENT '所属模型ID',
  `var_id`          BIGINT       DEFAULT NULL             COMMENT '关联字段ID，需结合 ref_type 判断所属资源表',
  `ref_type`        VARCHAR(32)  DEFAULT NULL             COMMENT '引用类型：VARIABLE=变量，CONSTANT=常量，DATA_OBJECT=对象字段，DATA_OBJECT_ROOT=对象整体，MODEL=模型，MODEL_OUTPUT=模型输出字段',
  `field_name`       VARCHAR(128) NOT NULL                COMMENT '字段名称（输出变量名）',
  `field_label`      VARCHAR(128) NOT NULL                COMMENT '字段中文名称',
  `script_name`      VARCHAR(128) DEFAULT NULL             COMMENT '脚本引用名称，原样保留用户配置，不自动转换大小写或命名格式',
  `field_type`       VARCHAR(32)  NOT NULL                COMMENT '输出字段类型：STRING=字符串，NUMBER=数值，INTEGER=整数，DOUBLE=双精度数值，BOOLEAN=布尔，DATE=日期，ENUM=枚举，OBJECT=对象，LIST=列表，MAP=映射，PROBABILITY=概率，VECTOR=向量',
  `target_field`     VARCHAR(128) DEFAULT NULL             COMMENT '对应的目标变量名',
  `target_operand`   JSON         DEFAULT NULL             COMMENT '模型输出目标 操作数表达式',
  `feature_name`     VARCHAR(128) DEFAULT NULL             COMMENT '模型内部输出特征名',
  `transform_operand` JSON        DEFAULT NULL             COMMENT '模型输出函数转换 操作数表达式',
  `is_probability`   TINYINT      NOT NULL DEFAULT 0       COMMENT '是否概率输出：0-否，1-是',
  `category`         VARCHAR(64)  DEFAULT NULL             COMMENT '类别标签（概率输出时指定）',
  `sort_order`       INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_model_id` (`model_id`),
  KEY `idx_var_id` (`var_id`),
  KEY `idx_ref_type_var_id` (`ref_type`, `var_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一模型输出字段表';

CREATE TABLE IF NOT EXISTS `rule_model_version` (
  `business_version`          INT DEFAULT NULL COMMENT '业务版本号',
  `execution_snapshot_json`   LONGTEXT DEFAULT NULL COMMENT '完整模型配置与冻结执行依赖',
  `execution_snapshot_digest` CHAR(64) DEFAULT NULL COMMENT '完整执行快照SHA256',
  `model_format`             VARCHAR(32)  DEFAULT NULL COMMENT '模型文件格式：PMML=预测模型标记语言，ONNX=开放神经网络交换格式；当前仅支持这两种格式',
  `model_file_name`          VARCHAR(256) DEFAULT NULL COMMENT '上传时的模型文件名快照',
  `model_file_size`          BIGINT       DEFAULT NULL COMMENT '模型文件大小，单位字节',
  `model_digest`             CHAR(64)     DEFAULT NULL COMMENT '模型文件原始内容的 SHA-256 摘要',
  `input_schema_json`        LONGTEXT     DEFAULT NULL COMMENT '精确输入字段结构及类型约束，JSON 格式',
  `output_schema_json`       LONGTEXT     DEFAULT NULL COMMENT '精确输出字段结构及类型约束，JSON 格式',
  `validation_report_json`   LONGTEXT     DEFAULT NULL COMMENT '制品或模型的校验报告，JSON 格式',
  `runtime_constraints_json` LONGTEXT     DEFAULT NULL COMMENT '运行环境与依赖版本约束，JSON 格式',
  `sample_status`            VARCHAR(32)  DEFAULT NULL COMMENT '模型样例验证状态：NOT_PROVIDED=未提供样例，PASSED=样例验证通过；空值表示无历史记录',
  `status`                   TINYINT      NOT NULL DEFAULT 1 COMMENT '模型版本状态：0=停用，1=启用',
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_id`        BIGINT       NOT NULL                COMMENT '模型ID',
  `version`         INT          NOT NULL                COMMENT '版本号',
  `model_content`   LONGTEXT     NOT NULL                COMMENT '版本快照 - 模型内容（Base64）',
  `model_config`    JSON         DEFAULT NULL             COMMENT '版本快照 - 模型配置',
  `change_log`      VARCHAR(512) DEFAULT NULL             COMMENT '变更说明',
  `publish_by`      VARCHAR(64)  DEFAULT NULL             COMMENT '发布人',
  `publish_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_version` (`model_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型版本历史表';

CREATE TABLE IF NOT EXISTS `rule_model_ref` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_id`   BIGINT   NOT NULL                COMMENT '全局模型ID',
  `project_id` BIGINT   NOT NULL                COMMENT '关联项目ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_project` (`model_id`, `project_id`),
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型关联表（用于项目关联全局模型）';

CREATE TABLE IF NOT EXISTS `rule_external_datasource` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`          BIGINT       NOT NULL DEFAULT 0      COMMENT '所属项目ID，0表示全局',
  `scope`               VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL=全局，PROJECT=项目级',
  `datasource_code`     VARCHAR(128) NOT NULL                COMMENT '外数数据源编码',
  `datasource_name`     VARCHAR(128) NOT NULL                COMMENT '外数数据源名称',
  `provider_name`       VARCHAR(128) DEFAULT NULL            COMMENT '第三方服务提供方',
  `protocol`            VARCHAR(16)  NOT NULL DEFAULT 'HTTP' COMMENT '协议类型：HTTP=超文本传输，HTTPS=加密超文本传输，RULE_ENGINE=规则引擎接口',
  `base_url`            VARCHAR(512) NOT NULL                COMMENT '基础地址',
  `auth_type`           VARCHAR(32)  NOT NULL DEFAULT 'NONE' COMMENT '默认鉴权：NONE=无需鉴权，BASIC=账号密码，BEARER=访问令牌，API_KEY=接口密钥，OAUTH2=开放授权，TOKEN_API=调用接口获取令牌，CUSTOM=自定义',
  `auth_config`         JSON     DEFAULT NULL            COMMENT '默认鉴权配置JSON',
  `token_cache_seconds` INT          NOT NULL DEFAULT 0      COMMENT '令牌缓存秒数，0表示不缓存',
  `description`         VARCHAR(512) DEFAULT NULL            COMMENT '说明',
  `status`              TINYINT      NOT NULL DEFAULT 1      COMMENT '资源状态：-1=已逻辑删除，0=停用，1=启用',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ext_ds_scope_project_code` (`scope`, `project_id`, `datasource_code`),
  KEY `idx_ext_ds_project_id` (`project_id`),
  KEY `idx_ext_ds_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部API数据源定义表';

CREATE TABLE IF NOT EXISTS `rule_external_api_config` (
  `execution_config` JSON DEFAULT NULL COMMENT '统一外数请求响应与多步链路配置',
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `datasource_id`        BIGINT       NOT NULL                COMMENT '外数数据源ID',
  `api_code`             VARCHAR(128) NOT NULL                COMMENT '接口编码',
  `api_name`             VARCHAR(128) NOT NULL                COMMENT '接口名称',
  `request_method`       VARCHAR(16)  NOT NULL DEFAULT 'POST' COMMENT '请求方法：GET=读取，POST=提交，PUT=整体更新，PATCH=局部更新，DELETE=删除，HEAD=仅响应头，OPTIONS=能力查询',
  `endpoint_url`         VARCHAR(512) NOT NULL                COMMENT '接口相对或完整地址',
  `content_type`         VARCHAR(128) DEFAULT NULL            COMMENT '请求Content-Type，空表示不主动设置',
  `request_mode`         VARCHAR(16)  NOT NULL DEFAULT 'SYNC' COMMENT '调用模式：SYNC=同步调用，ASYNC=异步提交后等待结果',
  `request_object_id`    BIGINT       DEFAULT NULL            COMMENT '请求数据对象ID',
  `response_object_id`   BIGINT       DEFAULT NULL            COMMENT '响应数据对象ID',
  `header_config`        JSON     DEFAULT NULL            COMMENT '请求头配置JSON',
  `query_config`         JSON     DEFAULT NULL            COMMENT '查询参数配置JSON',
  `request_mapping`      JSON     DEFAULT NULL            COMMENT '入参映射配置JSON',
  `response_mapping`     JSON     DEFAULT NULL            COMMENT '响应映射配置JSON',
  `response_script`      LONGTEXT     DEFAULT NULL            COMMENT '响应映射前QLExpress处理脚本',
  `body_template`        LONGTEXT     DEFAULT NULL            COMMENT '请求体模板',
  `request_script`       LONGTEXT     DEFAULT NULL            COMMENT '请求发送前QLExpress处理脚本',
  `payload_capture_config` JSON   DEFAULT NULL             COMMENT '请求/响应诊断报文留存、解密和字段排除策略JSON',
  `auth_mode`            VARCHAR(32)  NOT NULL DEFAULT 'INHERIT' COMMENT '接口鉴权：INHERIT=继承数据源，NONE=无需鉴权，BASIC=账号密码，BEARER=访问令牌，API_KEY=接口密钥，OAUTH2=开放授权，TOKEN_API=调用接口获取令牌，CUSTOM=自定义',
  `auth_api_config`      JSON     DEFAULT NULL            COMMENT '接口级鉴权与令牌获取配置JSON',
  `token_cache_seconds`  INT          NOT NULL DEFAULT 0      COMMENT '接口令牌缓存秒数',
  `response_cache_seconds` INT        NOT NULL DEFAULT 0      COMMENT '接口响应缓存秒数，0表示不缓存',
  `response_cache_max_size` INT       NOT NULL DEFAULT 10000  COMMENT '响应缓存最大条数',
  `response_cache_max_bytes` INT      NOT NULL DEFAULT 1048576 COMMENT '单条响应缓存最大字节数',
  `response_cache_redis_enabled` TINYINT NOT NULL DEFAULT 0   COMMENT '是否启用 Redis 二级响应缓存：0=否，1=是',
  `stale_cache_seconds`   INT         NOT NULL DEFAULT 0      COMMENT '允许使用过期缓存的秒数',
  `cache_key_config`     JSON     DEFAULT NULL            COMMENT '缓存键组件配置JSON，组件按顺序且必须全部有值',
  `success_condition`   JSON      DEFAULT NULL            COMMENT '请求成功响应条件树JSON',
  `exception_condition` JSON      DEFAULT NULL            COMMENT '接口异常响应条件树JSON',
  `timeout_ms`           INT          NOT NULL DEFAULT 3000   COMMENT '调用超时时间毫秒',
  `async_timeout_ms`     INT          NOT NULL DEFAULT 30000  COMMENT '异步提交后轮询/回调等待预算毫秒',
  `max_connections`      INT          NOT NULL DEFAULT 100    COMMENT '该API最大连接数',
  `max_connections_per_route` INT     NOT NULL DEFAULT 100    COMMENT '单路由最大连接数',
  `connection_request_timeout_ms` INT NOT NULL DEFAULT 100    COMMENT '连接池取连接超时',
  `connect_timeout_ms`   INT          NOT NULL DEFAULT 500    COMMENT '建立连接超时',
  `read_timeout_ms`      INT          NOT NULL DEFAULT 3000   COMMENT '读取响应超时',
  `idle_connection_timeout_seconds` INT NOT NULL DEFAULT 30  COMMENT '空闲连接清理秒数',
  `connection_ttl_seconds` INT        NOT NULL DEFAULT 300    COMMENT '连接最大存活秒数',
  `qps_limit`            DECIMAL(18,6) DEFAULT NULL           COMMENT 'API每秒请求数限制',
  `burst_capacity`       INT          DEFAULT NULL            COMMENT 'API突发容量',
  `max_concurrent`       INT          NOT NULL DEFAULT 50     COMMENT 'API最大并发数',
  `concurrent_wait_timeout_ms` INT     NOT NULL DEFAULT 0      COMMENT '等待并发许可的毫秒数',
  `token_refresh_ahead_seconds` INT    NOT NULL DEFAULT 60     COMMENT '令牌提前刷新秒数',
  `token_refresh_on_unauthorized` TINYINT NOT NULL DEFAULT 1  COMMENT '遇到未授权响应时是否强制刷新令牌：0=否，1=是',
  `token_failure_condition` JSON DEFAULT NULL COMMENT '令牌鉴权失败条件；空值按HTTP 401或403判断',
  `token_log_enabled`    TINYINT      NOT NULL DEFAULT 1      COMMENT '是否记录令牌动作日志：0=否，1=是',
  `retry_count`          INT          NOT NULL DEFAULT 0      COMMENT '重试次数',
  `retry_non_idempotent` TINYINT      NOT NULL DEFAULT 0      COMMENT '是否允许非幂等请求重试：0=否，1=是',
  `retry_interval_ms`    INT          NOT NULL DEFAULT 200    COMMENT '重试间隔毫秒',
  `retry_status_codes`   VARCHAR(256) DEFAULT '502,503,504'   COMMENT '允许重试的 HTTP 状态码列表，逗号分隔；默认 502=网关错误、503=服务不可用、504=网关超时',
  `retry_on_connection_error` TINYINT NOT NULL DEFAULT 1     COMMENT '连接异常时是否重试：0=否，1=是',
  `retry_on_timeout`     TINYINT      NOT NULL DEFAULT 0      COMMENT '请求超时时是否重试：0=否，1=是',
  `retry_condition`      JSON     DEFAULT NULL            COMMENT '业务响应重试条件树JSON',
  `retry_backoff_multiplier` DECIMAL(10,4) NOT NULL DEFAULT 2 COMMENT '重试退避倍数',
  `retry_max_interval_ms` INT         NOT NULL DEFAULT 1000   COMMENT '最大重试间隔',
  `circuit_breaker_enabled` TINYINT   NOT NULL DEFAULT 1      COMMENT '是否启用熔断：0=否，1=是',
  `circuit_failure_rate` INT          NOT NULL DEFAULT 50     COMMENT '熔断失败率百分比',
  `circuit_min_calls`    INT          NOT NULL DEFAULT 20     COMMENT '熔断最小调用数',
  `circuit_window_size`  INT          NOT NULL DEFAULT 50     COMMENT '熔断滑动窗口大小',
  `circuit_open_seconds` INT          NOT NULL DEFAULT 10     COMMENT '熔断打开秒数',
  `circuit_half_open_calls` INT       NOT NULL DEFAULT 5      COMMENT '半开探测调用数',
  `exception_strategy`   VARCHAR(32)  NOT NULL DEFAULT 'FAIL_FAST' COMMENT '异常处理策略：FAIL_FAST=立即失败，RETURN_DEFAULT=返回默认值，IGNORE=忽略异常，USE_CACHE=使用可用缓存',
  `fallback_value`       JSON     DEFAULT NULL            COMMENT '兜底返回值JSON',
  `async_result_mode`    VARCHAR(32)  DEFAULT NULL            COMMENT '异步结果获取方式：POLL=轮询，CALLBACK=回调',
  `async_poll_config`    JSON     DEFAULT NULL            COMMENT '异步轮询配置JSON',
  `async_callback_config` JSON    DEFAULT NULL            COMMENT '异步回调配置JSON',
  `async_callback_url`   VARCHAR(512) DEFAULT NULL            COMMENT '异步回调地址',
  `async_result_path`    VARCHAR(256) DEFAULT NULL            COMMENT '异步结果提取路径',
  `billing_item_code`    VARCHAR(128) DEFAULT NULL            COMMENT '计费项目编码',
  `billing_condition`    JSON     DEFAULT NULL            COMMENT '计费条件JSON，空表示正常计费',
  `unit_price`           DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '单次调用价格',
  `description`          VARCHAR(512) DEFAULT NULL            COMMENT '说明',
  `test_sample_params`   JSON     DEFAULT NULL            COMMENT 'API调用测试样例JSON',
  `status`               TINYINT      NOT NULL DEFAULT 1      COMMENT '资源状态：-1=已逻辑删除，0=停用，1=启用',
  `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ext_api_datasource_code` (`datasource_id`, `api_code`),
  KEY `idx_ext_api_datasource_id` (`datasource_id`),
  KEY `idx_ext_api_request_mode` (`request_mode`),
  KEY `idx_ext_api_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部API接口配置表';

CREATE TABLE IF NOT EXISTS `rule_runtime_call_log` (
  `root_trace_id` CHAR(36) DEFAULT NULL COMMENT '根请求追踪 ID',
  `history_fields` LONGTEXT DEFAULT NULL COMMENT '逻辑外数调用的受管字段ID结果快照',
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `trace_id`        CHAR(36)     DEFAULT NULL            COMMENT '全局唯一追踪 ID',
  `call_id`         CHAR(36)     DEFAULT NULL            COMMENT '一次逻辑外数调用ID，重试共用',
  `rule_trace_id`   CHAR(36)     DEFAULT NULL            COMMENT '发起调用的规则追踪 ID',
  `module_type`     VARCHAR(32)  NOT NULL                COMMENT '调用模块：DATASOURCE=外数接口，DATABASE=数据库，SFTP=远程文件，LIST=名单，MODEL=模型',
  `action_type`     VARCHAR(64)  NOT NULL                COMMENT '调用动作：API_INVOKE=外数调用，API_ATTEMPT=单次上游请求，API_DRAFT_INVOKE=草稿外数调用，API_DRAFT_ATTEMPT=草稿单次请求，API_ASSIGNMENT=外数结果赋值，AUTH_TEST=鉴权测试，TOKEN_CACHE_HIT=令牌缓存命中，TOKEN_FETCH=获取令牌，TOKEN_REFRESH=刷新令牌，TOKEN_FETCH_FAILED=令牌获取失败，QUERY=数据查询，EXECUTE=模型测试，MODEL_EXECUTE=模型执行，TEST_CONNECTION=连接测试，TEST_CONNECTION_DRAFT=草稿连接测试；可由模块扩展；TOKEN_INVALIDATE=令牌失效处理',
  `project_id`      BIGINT       DEFAULT NULL            COMMENT '项目ID',
  `project_code`    VARCHAR(128) DEFAULT NULL            COMMENT '项目编码',
  `datasource_id`   BIGINT       DEFAULT NULL            COMMENT '外数数据源ID',
  `request_id`      VARCHAR(128) DEFAULT NULL            COMMENT '调用方请求ID',
  `target_ref_id`   BIGINT       DEFAULT NULL            COMMENT '目标配置ID',
  `target_code`     VARCHAR(128) DEFAULT NULL            COMMENT '目标编码',
  `target_name`     VARCHAR(128) DEFAULT NULL            COMMENT '目标名称',
  `model_version`   INT          DEFAULT NULL            COMMENT '模型业务执行版本',
  `model_digest`    CHAR(64)     DEFAULT NULL            COMMENT '模型文件摘要',
  `configuration_digest` CHAR(64) DEFAULT NULL          COMMENT '模型配置摘要',
  `configured_provider` VARCHAR(16) DEFAULT NULL        COMMENT '配置的模型执行设备：CPU=处理器，CUDA=显卡加速',
  `effective_provider` VARCHAR(16) DEFAULT NULL         COMMENT '实际模型执行设备：CPU=处理器，CUDA=显卡加速；显卡失败时可能回退处理器',
  `execution_status` VARCHAR(32) DEFAULT NULL           COMMENT '模型执行状态：SUCCESS=成功，ERROR=错误，TIMEOUT=超时，THROTTLED=并发容量限制',
  `success`         TINYINT      NOT NULL DEFAULT 1      COMMENT '执行是否成功：0=否，1=是',
  `request_success` TINYINT      DEFAULT NULL            COMMENT '是否满足接口响应成功条件：0=否，1=是；空值表示未知或不适用',
  `found`           TINYINT      DEFAULT NULL            COMMENT '是否满足计费查得条件：0=否，1=是；空值表示未知或不适用',
  `provider_request` TINYINT     DEFAULT NULL            COMMENT '是否实际向外部服务发起请求：0=否，1=是；空值表示未知或不适用',
  `cache_status`    VARCHAR(32)  DEFAULT NULL            COMMENT '响应缓存状态：HIT=命中，MISS=未命中，DISABLED=禁用，CACHE_KEY_INCOMPLETE=缓存键不完整，STALE=使用过期缓存，REFRESH=刷新缓存',
  `cache_key`       VARCHAR(160) DEFAULT NULL            COMMENT '脱敏后的缓存键摘要',
  `attempt_no`      INT          DEFAULT NULL            COMMENT '本次实际上游请求序号',
  `circuit_state`   VARCHAR(32)  DEFAULT NULL            COMMENT '熔断状态：CLOSED=关闭，OPEN=熔断，HALF_OPEN=半开探测，DISABLED=未启用',
  `token_cache_status` VARCHAR(32) DEFAULT NULL          COMMENT '令牌缓存状态：L1_HIT=本地缓存命中，L2_HIT=共享缓存命中，COALESCED=复用并发请求，FETCH=获取，REFRESH=刷新，FETCH_FAILED=获取失败，REFRESH_FAILED=刷新失败',
  `request_method`  VARCHAR(16)  DEFAULT NULL            COMMENT '请求方法：GET=读取，POST=提交，PUT=整体更新，PATCH=局部更新，DELETE=删除，HEAD=仅响应头，OPTIONS=能力查询；模块操作还包括 SQL=数据库查询，SFTP=远程文件，ASSIGN=外数赋值，EXECUTE=模型执行',
  `request_url`     VARCHAR(1024) DEFAULT NULL           COMMENT '请求地址',
  `request_headers` TEXT         DEFAULT NULL            COMMENT '请求头JSON（敏感值脱敏）',
  `request_params`  LONGTEXT     DEFAULT NULL            COMMENT '请求入参JSON',
  `request_body`    LONGTEXT     DEFAULT NULL            COMMENT '请求体JSON或文本',
  `raw_request_body` LONGTEXT    DEFAULT NULL            COMMENT '原始请求体，仅受控接口返回',
  `raw_request_metadata` LONGTEXT DEFAULT NULL            COMMENT '原始请求留存策略元数据JSON',
  `original_request_body` LONGTEXT DEFAULT NULL           COMMENT '供应商实际收到的原始请求体，受控接口返回',
  `trace_steps` LONGTEXT DEFAULT NULL                    COMMENT '外数调用阶段链路JSON，仅保存脱敏分析副本',
  `response_status` INT          DEFAULT NULL            COMMENT '响应状态码',
  `response_body`   LONGTEXT     DEFAULT NULL            COMMENT '响应内容JSON或文本',
  `raw_response_body` LONGTEXT   DEFAULT NULL            COMMENT '上游原始响应体，仅受控接口返回',
  `raw_response_metadata` LONGTEXT DEFAULT NULL           COMMENT '原始响应留存策略元数据JSON',
  `original_response_body` LONGTEXT DEFAULT NULL          COMMENT '供应商实际返回的原始响应体，受控接口返回',
  `error_type`      VARCHAR(128) DEFAULT NULL            COMMENT '异常类型',
  `error_message`   VARCHAR(2048) DEFAULT NULL           COMMENT '错误信息',
  `cost_time_ms`    BIGINT       DEFAULT NULL            COMMENT '耗时毫秒',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_runtime_log_module_time` (`module_type`, `create_time`),
  KEY `idx_dashboard_runtime_project_module_action_time` (`module_type`, `action_type`, `provider_request`, `create_time`, `project_code`),
  KEY `idx_runtime_log_target_time` (`target_ref_id`, `create_time`),
  KEY `idx_runtime_trace` (`trace_id`, `rule_trace_id`),
  KEY `idx_runtime_call_id` (`call_id`),
  KEY `idx_runtime_log_success` (`success`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行时调用诊断日志表';

CREATE TABLE IF NOT EXISTS `rule_experiment` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`      BIGINT       DEFAULT NULL            COMMENT '所属项目ID',
  `project_code`    VARCHAR(128) DEFAULT NULL            COMMENT '所属项目编码',
  `experiment_code` VARCHAR(128) NOT NULL                COMMENT '实验编码',
  `experiment_name` VARCHAR(128) NOT NULL                COMMENT '实验名称',
  `description`     VARCHAR(512) DEFAULT NULL            COMMENT '说明',
  `routing_mode`    VARCHAR(32)  NOT NULL DEFAULT 'RATIO' COMMENT '生产分流方式：RATIO=按比例权重，CONDITION=按条件',
  `test_routing_mode` VARCHAR(32) NOT NULL DEFAULT 'CONDITION' COMMENT '测试组分流方式：RATIO=按比例权重，CONDITION=按条件',
  `condition_rule_code` VARCHAR(128) DEFAULT NULL        COMMENT '条件分流规则编码',
  `request_key_path` LONGTEXT DEFAULT NULL COMMENT '请求唯一键路径或组合表达式JSON',
  `input_validation_config` JSON DEFAULT NULL             COMMENT '实验输入字段校验绑定JSON',
  `test_exclusive`  TINYINT      NOT NULL DEFAULT 1      COMMENT '测试组是否互斥：0=否，1=是',
  `status`          TINYINT      NOT NULL DEFAULT 1      COMMENT '资源状态：-1=已逻辑删除，0=停用，1=启用',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_experiment_code` (`experiment_code`),
  KEY `idx_experiment_project` (`project_id`),
  KEY `idx_experiment_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分流实验定义表';

CREATE TABLE IF NOT EXISTS `rule_experiment_group` (
  `version_mode`          VARCHAR(16) DEFAULT 'LATEST' COMMENT '规则版本选择：LATEST=最新发布版本，FIXED=指定业务版本',
  `version_binding_id`    BIGINT DEFAULT NULL COMMENT '规则业务版本绑定ID',
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `experiment_id`   BIGINT       NOT NULL                COMMENT '实验ID',
  `group_code`      VARCHAR(128) NOT NULL                COMMENT '组编码',
  `group_name`      VARCHAR(128) NOT NULL                COMMENT '组名称',
  `group_type`      VARCHAR(32)  NOT NULL                COMMENT '实验组类型：CHAMPION=冠军组，CHALLENGER=挑战组，TEST=测试组',
  `rule_id`         BIGINT       DEFAULT NULL            COMMENT '执行规则定义ID',
  `rule_code`       VARCHAR(128) NOT NULL                COMMENT '执行规则编码',
  `traffic_ratio`   DECIMAL(8,4) NOT NULL DEFAULT 0.0000 COMMENT '比例分流权重',
  `condition_value` VARCHAR(128) DEFAULT NULL            COMMENT '条件分流返回值',
  `condition_expression` LONGTEXT DEFAULT NULL          COMMENT '条件分流命中表达式',
  `condition_config` LONGTEXT DEFAULT NULL               COMMENT '可视化条件配置JSON',
  `invoke_external_source` TINYINT NOT NULL DEFAULT 1    COMMENT '测试组是否实际调用外数接口：0=否，1=是',
  `status`          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0-停用，1-启用',
  `sort_order`      INT          NOT NULL DEFAULT 0      COMMENT '排序',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_experiment_group_code` (`experiment_id`, `group_code`),
  KEY `idx_experiment_group_rule` (`rule_id`),
  KEY `idx_experiment_group_type` (`experiment_id`, `group_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分流实验组表';

CREATE TABLE IF NOT EXISTS `rule_experiment_execution_log` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `experiment_id`   BIGINT       NOT NULL                COMMENT '实验ID',
  `experiment_code` VARCHAR(128) NOT NULL                COMMENT '实验编码',
  `config_digest` CHAR(64) DEFAULT NULL                  COMMENT '实验配置摘要',
  `experiment_trace_id` CHAR(36) DEFAULT NULL            COMMENT '本次分流实验追踪 ID',
  `root_trace_id`      CHAR(36) DEFAULT NULL              COMMENT '外层规则根追踪 ID',
  `child_trace_id`  CHAR(36)     DEFAULT NULL            COMMENT '实际执行组规则追踪 ID',
  `request_key`     VARCHAR(128) DEFAULT NULL            COMMENT '请求唯一键',
  `stage`           VARCHAR(32)  NOT NULL                COMMENT '实验执行阶段：PRODUCTION=生产组，TEST=测试组',
  `group_id`        BIGINT       DEFAULT NULL            COMMENT '实验组ID',
  `group_code`      VARCHAR(128) DEFAULT NULL            COMMENT '实验组编码',
  `group_name`      VARCHAR(128) DEFAULT NULL            COMMENT '实验组名称',
  `group_type`      VARCHAR(32)  DEFAULT NULL            COMMENT '实验组类型：CHAMPION=冠军组，CHALLENGER=挑战组，TEST=测试组',
  `rule_code`       VARCHAR(128) DEFAULT NULL            COMMENT '执行规则编码',
  `route_reason`    VARCHAR(512) DEFAULT NULL            COMMENT '分流原因',
  `success`         TINYINT      NOT NULL DEFAULT 1      COMMENT '执行是否成功：0=否，1=是',
  `input_params`    TEXT         DEFAULT NULL            COMMENT '解析后入参',
  `output_result`   TEXT         DEFAULT NULL            COMMENT '执行结果',
  `trace_info`      LONGTEXT     DEFAULT NULL            COMMENT '执行轨迹',
  `error_message`   VARCHAR(1024) DEFAULT NULL           COMMENT '错误信息',
  `execute_time_ms` BIGINT       DEFAULT NULL            COMMENT '执行耗时，单位毫秒',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  PRIMARY KEY (`id`),
  KEY `idx_exp_log_request` (`experiment_id`, `request_key`, `stage`),
  KEY `idx_exp_log_replay` (`experiment_id`, `group_id`, `request_key`, `config_digest`, `stage`),
  KEY `idx_exp_log_group` (`group_id`, `create_time`),
  KEY `idx_exp_trace` (`experiment_trace_id`, `child_trace_id`),
  KEY `idx_exp_root_trace` (`root_trace_id`, `create_time`),
  KEY `idx_exp_log_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分流实验执行明细表';

CREATE TABLE IF NOT EXISTS `rule_experiment_execution_state` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `project_id` BIGINT NOT NULL COMMENT '所属项目标识，关联 rule_project.id；0 表示全局资源',
  `experiment_id` BIGINT NOT NULL COMMENT '分流实验标识，关联 rule_experiment.id',
  `experiment_code` VARCHAR(128) NOT NULL COMMENT '实验编码快照',
  `request_key_hash` CHAR(64) NOT NULL COMMENT '请求幂等键的 SHA-256 摘要',
  `request_digest` CHAR(64) NOT NULL COMMENT '请求内容的 SHA-256 摘要，用于检查幂等重试内容是否相同',
  `experiment_trace_id` CHAR(36) NOT NULL COMMENT '分流实验本次逻辑执行的唯一追踪标识',
  `config_digest` CHAR(64) NOT NULL COMMENT '本次实验配置的 SHA-256 摘要',
  `status` VARCHAR(32) NOT NULL COMMENT '幂等执行状态：RUNNING=执行中，SUCCEEDED=已成功，FAILED_RETRYABLE=失败可重试',
  `attempt_no` INT NOT NULL DEFAULT 1 COMMENT '同一逻辑请求的执行尝试序号',
  `result_json` LONGTEXT DEFAULT NULL COMMENT '已完成执行的结果快照，JSON 格式',
  `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '失败原因或异常说明',
  `lease_owner` VARCHAR(128) DEFAULT NULL COMMENT '持有当前执行租约的实例或请求标识',
  `lease_until` DATETIME(6) DEFAULT NULL COMMENT '当前执行租约的到期时间',
  `expire_time` DATETIME(6) DEFAULT NULL COMMENT '幂等结果的保留截止时间',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '记录最后更新时间',
  `finish_time` DATETIME(6) DEFAULT NULL COMMENT '本轮执行结束时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_experiment_execution_state_key` (`project_id`, `experiment_id`, `request_key_hash`),
  UNIQUE KEY `uk_experiment_execution_state_trace` (`experiment_trace_id`),
  KEY `idx_experiment_execution_state_lease` (`status`, `lease_until`),
  KEY `idx_experiment_execution_state_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验幂等执行状态';

CREATE TABLE IF NOT EXISTS `rule_trace_registry` (
  `trace_id`        CHAR(36)     NOT NULL                COMMENT '全局唯一追踪 ID',
  `trace_type`      CHAR(2)      NOT NULL                COMMENT '追踪类型：TB=决策表，TR=决策树，DF=决策流，RS=规则集，CT=交叉表，SC=评分卡，AC=复杂交叉表，AS=复杂评分卡，QL=脚本，AP=外数接口，DB=数据库，SF=远程文件，LS=名单，MD=模型，EX=实验',
  `scope_type`      CHAR(1)      NOT NULL                COMMENT '追踪作用域：G=全局，P=项目级',
  `scope_code`      CHAR(4)      NOT NULL                COMMENT '四位三十六进制作用域码；全局为 0000，项目级由项目标识编码',
  `project_id`      BIGINT       DEFAULT NULL            COMMENT '项目ID',
  `resource_type`   VARCHAR(32)  NOT NULL                COMMENT '追踪资源类型：RULE=规则，DATASOURCE=外数接口，DATABASE=数据库，SFTP=远程文件，LIST=名单，MODEL=模型，EXPERIMENT=实验',
  `resource_id`     BIGINT       DEFAULT NULL            COMMENT '资源ID',
  `resource_code`   VARCHAR(128) DEFAULT NULL            COMMENT '资源编码快照',
  `parent_trace_id` CHAR(36)     DEFAULT NULL            COMMENT '父规则追踪 ID',
  `create_time`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`trace_id`),
  KEY `idx_trace_parent` (`parent_trace_id`),
  KEY `idx_trace_resource` (`resource_type`, `resource_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局Trace编号注册表';

CREATE TABLE IF NOT EXISTS `rule_experiment_version` (
  `business_version` INT DEFAULT NULL COMMENT '业务版本号',
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `experiment_id`   BIGINT       NOT NULL                COMMENT '所属分流实验标识',
  `version`         INT          NOT NULL                COMMENT '不可变实验快照序号',
  `experiment_json` TEXT         NOT NULL                COMMENT '实验定义版本快照，JSON 格式',
  `groups_json`     TEXT         NOT NULL                COMMENT '实验组配置版本快照，JSON 格式',
  `change_log`      VARCHAR(512) DEFAULT NULL            COMMENT '版本变更说明',
  `publish_by`      VARCHAR(64)  DEFAULT NULL            COMMENT '保存版本快照的操作人',
  `publish_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '版本快照保存时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_experiment_version` (`experiment_id`, `version`),
  KEY `idx_experiment_version_time` (`experiment_id`, `publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分流实验版本历史表';

CREATE TABLE IF NOT EXISTS `rule_db_datasource` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`            BIGINT       NOT NULL DEFAULT 0      COMMENT '所属项目ID，0表示全局',
  `scope`                 VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL=全局，PROJECT=项目级',
  `datasource_code`       VARCHAR(128) NOT NULL                COMMENT '数据库数据源编码',
  `datasource_name`       VARCHAR(128) NOT NULL                COMMENT '数据库数据源名称',
  `db_type`               VARCHAR(32)  NOT NULL DEFAULT 'MYSQL' COMMENT '数据库类型：MYSQL=关系数据库，POSTGRESQL=对象关系数据库，ORACLE=甲骨文数据库，SQLSERVER=微软数据库，OTHER=其他 JDBC 数据库',
  `connection_mode`       VARCHAR(32)  NOT NULL DEFAULT 'DIRECT' COMMENT '连接方式：DIRECT-直连，SSH_TUNNEL-SSH隧道',
  `host`                  VARCHAR(256) DEFAULT NULL            COMMENT '数据库主机（用于表单生成JDBC URL和SSH远端转发）',
  `port`                  INT          DEFAULT NULL            COMMENT '数据库端口',
  `database_name`         VARCHAR(128) DEFAULT NULL            COMMENT '数据库名/服务名',
  `jdbc_params`           VARCHAR(1024) DEFAULT NULL           COMMENT 'JDBC扩展参数，不含前导问号',
  `driver_class_name`     VARCHAR(256) DEFAULT 'com.mysql.cj.jdbc.Driver' COMMENT 'JDBC驱动类',
  `jdbc_url`              VARCHAR(1024) NOT NULL               COMMENT 'JDBC连接串',
  `username`              VARCHAR(128) DEFAULT NULL            COMMENT '用户名',
  `password`              VARCHAR(512) DEFAULT NULL            COMMENT '密码',
  `ssh_host`              VARCHAR(256) DEFAULT NULL            COMMENT 'SSH堡垒机主机',
  `ssh_port`              INT          DEFAULT NULL            COMMENT 'SSH堡垒机端口',
  `ssh_username`          VARCHAR(128) DEFAULT NULL            COMMENT 'SSH用户名',
  `ssh_password`          VARCHAR(512) DEFAULT NULL            COMMENT 'SSH密码',
  `ssh_private_key`       TEXT         DEFAULT NULL            COMMENT 'SSH私钥内容',
  `ssh_passphrase`        VARCHAR(512) DEFAULT NULL            COMMENT 'SSH私钥口令',
  `ssh_timeout_ms`        INT          NOT NULL DEFAULT 10000  COMMENT 'SSH连接超时时间毫秒',
  `max_pool_size`         INT          NOT NULL DEFAULT 5      COMMENT '最大连接数',
  `min_idle`              INT          NOT NULL DEFAULT 1      COMMENT '最小空闲连接数',
  `connection_timeout_ms` INT          NOT NULL DEFAULT 3000   COMMENT '连接超时时间毫秒',
  `idle_timeout_ms`       INT          NOT NULL DEFAULT 600000 COMMENT '空闲超时时间毫秒',
  `validation_query`      VARCHAR(256) NOT NULL DEFAULT 'SELECT 1' COMMENT '连接校验SQL',
  `description`           VARCHAR(512) DEFAULT NULL            COMMENT '说明',
  `status`                TINYINT      NOT NULL DEFAULT 1      COMMENT '资源状态：-1=已逻辑删除，0=停用，1=启用',
  `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_db_ds_scope_project_code` (`scope`, `project_id`, `datasource_code`),
  KEY `idx_db_ds_project_id` (`project_id`),
  KEY `idx_db_ds_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部数据库数据源定义表';

CREATE TABLE IF NOT EXISTS `rule_sftp_datasource` (
  `id`                       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`               BIGINT       NOT NULL DEFAULT 0      COMMENT '所属项目ID，0表示全局',
  `scope`                    VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL=全局，PROJECT=项目级',
  `datasource_code`          VARCHAR(128) NOT NULL                COMMENT 'SFTP数据源编码',
  `datasource_name`          VARCHAR(128) NOT NULL                COMMENT 'SFTP数据源名称',
  `connection_mode`          VARCHAR(32)  NOT NULL DEFAULT 'DIRECT' COMMENT '连接方式：DIRECT=直连，PROXY=通过代理连接',
  `host`                     VARCHAR(256) NOT NULL                COMMENT 'SFTP主机',
  `port`                     INT          NOT NULL DEFAULT 22       COMMENT 'SFTP端口',
  `base_path`                VARCHAR(1024) DEFAULT NULL            COMMENT '远端根目录',
  `username`                 VARCHAR(128) NOT NULL                COMMENT 'SFTP用户名',
  `password`                 VARCHAR(1024) DEFAULT NULL            COMMENT '密码',
  `private_key`              TEXT         DEFAULT NULL            COMMENT '私钥内容',
  `passphrase`               VARCHAR(512) DEFAULT NULL            COMMENT '私钥口令',
  `known_hosts`              TEXT         DEFAULT NULL            COMMENT '已知远程主机指纹清单，用于服务器身份校验',
  `strict_host_key_checking` TINYINT      NOT NULL DEFAULT 0       COMMENT '是否校验远程主机指纹：0=否，1=是',
  `connect_timeout_ms`       INT          NOT NULL DEFAULT 10000   COMMENT '连接超时毫秒',
  `session_timeout_ms`       INT          NOT NULL DEFAULT 30000   COMMENT '会话超时毫秒',
  `socket_timeout_ms`        INT          NOT NULL DEFAULT 30000   COMMENT 'Socket超时毫秒',
  `max_pool_size`            INT          NOT NULL DEFAULT 8        COMMENT '最大连接数',
  `min_idle`                 INT          NOT NULL DEFAULT 1        COMMENT '最小空闲连接数',
  `borrow_timeout_ms`        INT          NOT NULL DEFAULT 5000     COMMENT '借用连接超时毫秒',
  `idle_timeout_ms`          INT          NOT NULL DEFAULT 600000   COMMENT '空闲连接超时毫秒',
  `max_file_size_bytes`      INT          NOT NULL DEFAULT 10485760 COMMENT '单文件最大读取字节数',
  `file_name_encoding`       VARCHAR(64)  NOT NULL DEFAULT 'UTF-8'  COMMENT '文件名编码',
  `extra_config`             JSON         DEFAULT NULL              COMMENT 'JSch扩展配置JSON',
  `description`              VARCHAR(512) DEFAULT NULL              COMMENT '说明',
  `status`                   TINYINT      NOT NULL DEFAULT 1        COMMENT '资源状态：-1=已逻辑删除，0=停用，1=启用',
  `create_time`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sftp_ds_scope_project_code` (`scope`, `project_id`, `datasource_code`),
  KEY `idx_sftp_ds_project_id` (`project_id`),
  KEY `idx_sftp_ds_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SFTP连接池数据源定义表';

CREATE TABLE IF NOT EXISTS `rule_billing_config` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`      BIGINT       NOT NULL DEFAULT 0      COMMENT '所属项目ID，0表示全局',
  `scope`           VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL=全局，PROJECT=项目级',
  `billing_code`    VARCHAR(128) NOT NULL                COMMENT '计费项编码',
  `billing_name`    VARCHAR(128) NOT NULL                COMMENT '计费项名称',
  `billing_target`  VARCHAR(32)  NOT NULL DEFAULT 'ENGINE' COMMENT '计费对象：ENGINE=规则引擎，API=外数接口，DB=数据库查询',
  `target_ref_id`   BIGINT       DEFAULT NULL            COMMENT '具体计费对象ID，空表示同类型全部',
  `charge_type`     VARCHAR(32)  NOT NULL DEFAULT 'COUNT' COMMENT '计费方式：COUNT=按调用次数，SUCCESS=仅成功调用，DURATION=按耗时，FIXED=每次固定金额',
  `unit_price`      DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '单价',
  `currency`        VARCHAR(16)  NOT NULL DEFAULT 'CNY'  COMMENT '币种',
  `effective_time`  DATETIME     DEFAULT NULL            COMMENT '生效时间',
  `expire_time`     DATETIME     DEFAULT NULL            COMMENT '失效时间',
  `description`     VARCHAR(512) DEFAULT NULL            COMMENT '说明',
  `status`          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0-停用，1-启用',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_scope_project_code` (`scope`, `project_id`, `billing_code`),
  KEY `idx_billing_target` (`billing_target`, `target_ref_id`),
  KEY `idx_billing_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计费配置表';

CREATE TABLE IF NOT EXISTS `rule_billing_record` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `billing_config_id` BIGINT     DEFAULT NULL COMMENT '计费项ID，历史明细可为空',
  `billing_basis`   VARCHAR(32)   DEFAULT NULL COMMENT '计费依据：EXECUTION_SNAPSHOT=执行时冻结快照，CURRENT_CONFIG=记账时配置，LEGACY_REPLAY=旧事件重放',
  `project_id`      BIGINT       DEFAULT NULL            COMMENT '项目ID',
  `project_code`    VARCHAR(128) DEFAULT NULL            COMMENT '项目编码',
  `billing_code`    VARCHAR(128) NOT NULL                COMMENT '计费项编码',
  `billing_name`    VARCHAR(128) DEFAULT NULL            COMMENT '计费项名称',
  `billing_target`  VARCHAR(32)  NOT NULL                COMMENT '计费对象：ENGINE=规则引擎，API=外数接口，DB=数据库查询',
  `target_ref_id`   BIGINT       DEFAULT NULL            COMMENT '具体计费对象ID',
  `root_trace_id`   CHAR(36)     DEFAULT NULL            COMMENT '规则根追踪 ID',
  `rule_id` BIGINT DEFAULT NULL COMMENT '规则定义标识，关联 rule_definition.id',
  `rule_version` INT DEFAULT NULL COMMENT '执行时的规则业务版本号',
  `revision_id` BIGINT DEFAULT NULL COMMENT '规则修订标识，关联 rule_revision.id',
  `artifact_digest` VARCHAR(64) DEFAULT NULL COMMENT '决策制品的 SHA-256 摘要',
  `billing_dedup_key` CHAR(64)   DEFAULT NULL            COMMENT '引擎计费幂等键摘要，仅根追踪计费记录使用',
  `attempt_no`      INT          DEFAULT NULL            COMMENT '逻辑执行恢复尝试次数',
  `request_id`      VARCHAR(128) DEFAULT NULL            COMMENT '请求ID',
  `rule_code`       VARCHAR(128) DEFAULT NULL            COMMENT '规则编码',
  `api_code`        VARCHAR(128) DEFAULT NULL            COMMENT 'API编码',
  `datasource_code` VARCHAR(128) DEFAULT NULL            COMMENT '数据源编码',
  `auth_id`         BIGINT       DEFAULT NULL            COMMENT '鉴权配置ID',
  `auth_code`       VARCHAR(128) DEFAULT NULL            COMMENT '鉴权配置编码快照',
  `auth_type`       VARCHAR(32)  DEFAULT NULL            COMMENT '鉴权类型快照：LEGACY_TOKEN=项目访问令牌，API_KEY=接口密钥，BASIC=账号密码，HMAC_SHA256=摘要签名，BEARER_TOKEN=临时访问令牌，GLOBAL_KEY=全局公私钥签名',
  `token_id`        BIGINT       DEFAULT NULL            COMMENT '临时令牌 ID',
  `token_code`      VARCHAR(128) DEFAULT NULL            COMMENT '临时令牌编码快照',
  `auth_phase`      VARCHAR(16)  DEFAULT NULL            COMMENT '鉴权阶段：DIRECT=长期凭据直接认证，VALID=临时令牌有效期，GRACE=临时令牌宽限期',
  `success`         TINYINT      NOT NULL DEFAULT 1      COMMENT '执行是否成功：0=否，1=是',
  `quantity`        DECIMAL(18,6) NOT NULL DEFAULT 1.000000 COMMENT '计费数量',
  `unit_price`      DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '单价',
  `amount`          DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '金额',
  `currency`        VARCHAR(16)  NOT NULL DEFAULT 'CNY'  COMMENT '币种',
  `cost_time_ms`    BIGINT       DEFAULT NULL            COMMENT '耗时毫秒',
  `error_message`   VARCHAR(1024) DEFAULT NULL           COMMENT '错误信息',
  `occur_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_record_dedup_key` (`billing_dedup_key`),
  KEY `idx_billing_record_occur` (`occur_time`),
  KEY `idx_billing_record_target` (`billing_target`, `target_ref_id`),
  KEY `idx_billing_record_root_trace` (`root_trace_id`, `occur_time`),
  KEY `idx_billing_record_project` (`project_code`, `occur_time`),
  KEY `idx_dashboard_billing_rule_time` (`rule_code`, `occur_time`, `project_code`),
  KEY `idx_billing_record_auth` (`auth_id`, `token_id`, `occur_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计费明细表';

CREATE TABLE IF NOT EXISTS `rule_billing_summary_lock` (
  `summary_date` DATE NOT NULL COMMENT '串行化同一日期的汇总替换',
  PRIMARY KEY (`summary_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计费日汇总事务锁';

CREATE TABLE IF NOT EXISTS `rule_billing_summary_task` (
  `task_id` CHAR(36) NOT NULL COMMENT '日汇总刷新任务的唯一标识',
  `summary_date` DATE NOT NULL COMMENT '计费汇总所属自然日',
  `status` VARCHAR(16) NOT NULL COMMENT '任务状态：PENDING=待处理，RUNNING=处理中，SUCCEEDED=成功，FAILED=失败',
  `active_date` DATE GENERATED ALWAYS AS (CASE WHEN `status` IN ('PENDING','RUNNING') THEN `summary_date` ELSE NULL END) STORED COMMENT '仅 PENDING=待处理或 RUNNING=处理中时等于汇总日期，其余为空，用于防止同日重复活动任务',
  `applicant` VARCHAR(128) DEFAULT NULL COMMENT '发起汇总刷新的账户标识',
  `summary_count` INT DEFAULT NULL COMMENT '刷新后生成的汇总行数',
  `record_count` BIGINT DEFAULT NULL COMMENT '参与本次汇总的明细记录数',
  `error_message` VARCHAR(512) DEFAULT NULL COMMENT '失败原因或异常说明',
  `requested_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '任务提交时间',
  `started_at` DATETIME(6) DEFAULT NULL COMMENT '任务开始处理时间',
  `completed_at` DATETIME(6) DEFAULT NULL COMMENT '处理完成时间',
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uk_billing_summary_active_date` (`active_date`),
  KEY `idx_billing_summary_task_pending` (`status`, `requested_at`),
  KEY `idx_billing_summary_task_date` (`summary_date`, `requested_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计费日汇总刷新任务';

CREATE TABLE IF NOT EXISTS `rule_billing_summary` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `summary_date`    DATE         NOT NULL                COMMENT '汇总日期',
  `project_id`      BIGINT       DEFAULT NULL            COMMENT '项目ID',
  `project_code`    VARCHAR(128) DEFAULT NULL            COMMENT '项目编码',
  `auth_id`         BIGINT       DEFAULT NULL            COMMENT '鉴权配置ID',
  `auth_code`       VARCHAR(128) DEFAULT NULL            COMMENT '鉴权配置编码快照',
  `auth_type`       VARCHAR(32)  DEFAULT NULL            COMMENT '鉴权类型快照：LEGACY_TOKEN=项目访问令牌，API_KEY=接口密钥，BASIC=账号密码，HMAC_SHA256=摘要签名，BEARER_TOKEN=临时访问令牌，GLOBAL_KEY=全局公私钥签名',
  `billing_code`    VARCHAR(128) NOT NULL                COMMENT '计费项编码',
  `billing_target`  VARCHAR(32)  NOT NULL                COMMENT '计费对象：ENGINE=规则引擎，API=外数接口，DB=数据库查询',
  `target_ref_id`   BIGINT       DEFAULT NULL            COMMENT '具体计费对象ID',
  `total_count`     BIGINT       NOT NULL DEFAULT 0      COMMENT '总调用次数',
  `success_count`   BIGINT       NOT NULL DEFAULT 0      COMMENT '成功次数',
  `fail_count`      BIGINT       NOT NULL DEFAULT 0      COMMENT '失败次数',
  `total_quantity`  DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '总计费数量',
  `total_amount`    DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '总金额',
  `currency`        VARCHAR(16)  NOT NULL DEFAULT 'CNY'  COMMENT '币种',
  `avg_cost_time_ms` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '平均耗时毫秒',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_summary_key` (`summary_date`, `project_code`, `billing_code`, `billing_target`, `target_ref_id`, `auth_id`, `currency`),
  KEY `idx_billing_summary_date` (`summary_date`),
  KEY `idx_billing_summary_target` (`billing_target`, `target_ref_id`),
  KEY `idx_billing_summary_auth` (`auth_id`, `summary_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计费汇总表';

CREATE TABLE IF NOT EXISTS `rule_external_api_trial` (
  `id`                 BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `trial_id`           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '外数草稿试运行的全局唯一标识',
  `owner`              VARBINARY(512) NOT NULL COMMENT '控制台用户身份，按字节精确隔离',
  `request_key`        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同一用户提交试运行的幂等请求键',
  `datasource_id`      BIGINT NOT NULL COMMENT '外数数据源标识，关联 rule_external_datasource.id',
  `snapshot_digest`    CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变试运行配置和参数快照的 SHA-256 摘要',
  `snapshot_json`      LONGTEXT NOT NULL COMMENT 'CredentialCipher加密的不可变配置及参数快照',
  `status`             VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '试运行状态：CREATED=已创建，RUNNING=执行中，WAITING=等待异步结果，SUCCEEDED=成功，FAILED=失败，UNKNOWN=执行结果未知，CANCELLED=已取消，EXPIRED=已过期',
  `attempt_no`         INT NOT NULL DEFAULT 0 COMMENT '同一逻辑请求的执行尝试序号',
  `lease_owner`        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '持有当前执行租约的实例或请求标识',
  `lease_until_ms`     BIGINT DEFAULT NULL COMMENT '执行租约到期的时间戳，单位毫秒',
  `expires_at_ms`      BIGINT NOT NULL COMMENT '试运行记录失效的时间戳，单位毫秒',
  `pending_json`       LONGTEXT DEFAULT NULL COMMENT 'CredentialCipher加密的异步恢复检查点',
  `result_json`        LONGTEXT DEFAULT NULL COMMENT 'CredentialCipher加密的试运行结果',
  `error_message`      VARCHAR(2048) DEFAULT NULL COMMENT '失败原因或异常说明',
  `created_at_ms`      BIGINT NOT NULL COMMENT '试运行记录创建的时间戳，单位毫秒',
  `updated_at_ms`      BIGINT NOT NULL COMMENT '试运行记录更新的时间戳，单位毫秒',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_external_api_trial_id` (`trial_id`),
  UNIQUE KEY `uk_external_api_trial_request` (`owner`, `request_key`),
  KEY `idx_external_api_trial_owner_created` (`owner`, `created_at_ms`, `id`),
  KEY `idx_external_api_trial_owner_expire` (`owner`, `status`, `expires_at_ms`),
  KEY `idx_external_api_trial_owner_lease` (`owner`, `status`, `lease_until_ms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外数草稿试运行状态与不可变快照';

CREATE TABLE IF NOT EXISTS `rule_execution_state` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `project_id`            BIGINT       DEFAULT NULL COMMENT '所属项目标识，关联 rule_project.id；0 表示全局资源',
  `definition_id`         BIGINT       NOT NULL COMMENT '规则定义标识，关联 rule_definition.id',
  `rule_code`             VARCHAR(128) NOT NULL COMMENT '规则编码快照',
  `idempotency_key_hash`  CHAR(64)     NOT NULL COMMENT '请求幂等键的 SHA-256 摘要',
  `request_digest`        CHAR(64)     NOT NULL COMMENT '请求内容的 SHA-256 摘要，用于检查幂等重试内容是否相同',
  `root_trace_id`         CHAR(36)     NOT NULL COMMENT '本次逻辑执行的根追踪标识',
  `status`                VARCHAR(32)  NOT NULL COMMENT '逻辑执行状态：RUNNING=执行中，SUCCEEDED=成功，WAITING_EXTERNAL=等待外部结果，UNKNOWN_RETRYABLE=结果未知可恢复，FAILED_RETRYABLE=失败可恢复',
  `attempt_no`            INT          NOT NULL DEFAULT 1 COMMENT '同一逻辑请求的执行尝试序号',
  `initial_revision_id`   BIGINT       DEFAULT NULL COMMENT '首次执行使用的规则修订标识',
  `current_revision_id`   BIGINT       DEFAULT NULL COMMENT '最近执行尝试使用的规则修订标识',
  `current_artifact_digest` CHAR(64)   DEFAULT NULL COMMENT '最近执行尝试使用的制品 SHA-256 摘要',
  `checkpoint_json`       LONGTEXT     DEFAULT NULL COMMENT '本次逻辑执行的恢复检查点快照，JSON 格式',
  `result_json`           LONGTEXT     DEFAULT NULL COMMENT '已完成执行的结果快照，JSON 格式',
  `lease_owner`           VARCHAR(128) DEFAULT NULL COMMENT '持有当前执行租约的实例或请求标识',
  `lease_until`           DATETIME(6)  DEFAULT NULL COMMENT '当前执行租约的到期时间',
  `error_message`         VARCHAR(1024) DEFAULT NULL COMMENT '失败原因或异常说明',
  `expire_time`           DATETIME(6)  DEFAULT NULL COMMENT '幂等结果保留截止时间',
  `create_time`           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
  `update_time`           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '记录最后更新时间',
  `finish_time`           DATETIME(6)  DEFAULT NULL COMMENT '本轮执行结束时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_execution_state_idempotency` (`project_id`, `definition_id`, `idempotency_key_hash`),
  UNIQUE KEY `uk_execution_state_trace` (`root_trace_id`),
  KEY `idx_execution_state_status_lease` (`status`, `lease_until`),
  KEY `idx_execution_state_expire` (`expire_time`),
  KEY `idx_execution_state_rule_time` (`rule_code`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则断点恢复状态，不作为执行日志';

CREATE TABLE IF NOT EXISTS `rule_execution_persistence_outbox` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `event_id`        CHAR(36)     NOT NULL COMMENT '执行持久化事件的全局唯一标识，用于重复投递去重',
  `log_json`        LONGTEXT     DEFAULT NULL COMMENT '待保存的执行日志快照，JSON 格式',
  `definition_json` LONGTEXT     DEFAULT NULL COMMENT '执行时规则定义及计费归属快照，JSON 格式',
  `billing_snapshot_json` LONGTEXT DEFAULT NULL COMMENT '接受执行事件时冻结的计费事实',
  `success`         TINYINT      NOT NULL DEFAULT 0 COMMENT '执行是否成功：0=否，1=是',
  `cost_time_ms`    BIGINT       DEFAULT NULL COMMENT '执行耗时，单位毫秒',
  `error_message`   VARCHAR(2048) DEFAULT NULL COMMENT '失败原因或异常说明',
  `project_id`      BIGINT       DEFAULT NULL COMMENT '所属项目标识，关联 rule_project.id；0 表示全局资源',
  `project_code`    VARCHAR(128) DEFAULT NULL COMMENT '所属项目编码快照',
  `auth_id`         BIGINT       DEFAULT NULL COMMENT '鉴权配置标识',
  `auth_code`       VARCHAR(128) DEFAULT NULL COMMENT '鉴权配置编码快照',
  `auth_type`       VARCHAR(32)  DEFAULT NULL COMMENT '鉴权类型快照：LEGACY_TOKEN=项目访问令牌，API_KEY=接口密钥，BASIC=账号密码，HMAC_SHA256=摘要签名，BEARER_TOKEN=临时访问令牌，GLOBAL_KEY=全局公私钥签名',
  `token_id`        BIGINT       DEFAULT NULL COMMENT '临时访问令牌标识',
  `token_code`      VARCHAR(128) DEFAULT NULL COMMENT '临时访问令牌编码快照',
  `auth_phase`      VARCHAR(32)  DEFAULT NULL COMMENT '鉴权阶段：DIRECT=长期凭据直接认证，VALID=临时令牌有效期，GRACE=临时令牌宽限期',
  `log_pending`     TINYINT      NOT NULL DEFAULT 0 COMMENT '日志是否仍需落库：0=无需或已完成，1=待补偿',
  `billing_pending` TINYINT      NOT NULL DEFAULT 0 COMMENT '计费是否仍需落库：0=无需或已完成，1=待补偿',
  `delivery_status` VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '持久化补偿状态：PENDING=待处理，DELIVERED=已完成',
  `retry_count`     INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
  `next_retry_time` DATETIME     DEFAULT NULL COMMENT '允许下一次重试的时间',
  `last_error`      VARCHAR(2048) DEFAULT NULL COMMENT '最近一次投递失败原因',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
  `delivered_time`  DATETIME     DEFAULT NULL COMMENT '消息或持久化事件成功投递时间',
  `claim_token`     CHAR(36)     DEFAULT NULL COMMENT '本次投递领取令牌，用于防止过期执行者回写',
  `lease_until`     DATETIME     DEFAULT NULL COMMENT '当前执行租约的到期时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_execution_persistence_event` (`event_id`),
  KEY `idx_execution_persistence_poll` (`delivery_status`, `next_retry_time`),
  KEY `idx_execution_persistence_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则执行日志和计费失败恢复事件';

CREATE TABLE IF NOT EXISTS `rule_execution_checkpoint` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `root_trace_id`         CHAR(36)     NOT NULL COMMENT '本次逻辑执行的根追踪标识',
  `step_id`               VARCHAR(128) NOT NULL COMMENT '逻辑执行内的稳定步骤标识',
  `step_type`             VARCHAR(32)  NOT NULL COMMENT '检查点类型：ROOT=整体执行，VARIABLE=变量取值，EXTERNAL_API=外数调用，MODEL=模型执行',
  `step_status`           VARCHAR(32)  NOT NULL COMMENT '步骤状态：SUCCESS=来源成功，WAITING_EXTERNAL=等待外部结果；整体执行可为 RUNNING=执行中，SUCCEEDED=成功，WAITING_EXTERNAL=等待外部结果，UNKNOWN_RETRYABLE=结果未知可恢复，FAILED_RETRYABLE=失败可恢复',
  `module_trace_id`       CHAR(36)     DEFAULT NULL COMMENT '步骤对应的模块调用追踪标识',
  `parent_trace_id`       CHAR(36)     DEFAULT NULL COMMENT '父级规则或模块的追踪标识',
  `input_digest`          CHAR(64)     DEFAULT NULL COMMENT '步骤输入参数的 SHA-256 摘要',
  `dependency_digest`     CHAR(64)     DEFAULT NULL COMMENT '执行依赖快照的 SHA-256 摘要',
  `source_revision_id`    BIGINT       DEFAULT NULL COMMENT '生成该检查点的规则修订标识',
  `source_artifact_digest` CHAR(64)    DEFAULT NULL COMMENT '生成该检查点的制品 SHA-256 摘要',
  `resolved_value`        LONGTEXT     DEFAULT NULL COMMENT '步骤已解析的数据值快照，JSON 格式',
  `external_result`       LONGTEXT     DEFAULT NULL COMMENT '外部来源响应及状态快照，JSON 格式',
  `provider_request_id`   VARCHAR(256) DEFAULT NULL COMMENT '外部服务提供方的请求标识',
  `attempt_history`       LONGTEXT     DEFAULT NULL COMMENT '步骤历史执行尝试及恢复信息，JSON 格式',
  `create_time`           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
  `update_time`           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '记录最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_execution_checkpoint_step` (`root_trace_id`, `step_id`),
  KEY `idx_execution_checkpoint_status` (`step_status`, `update_time`),
  KEY `idx_execution_checkpoint_module` (`module_trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则断点恢复步骤检查点';

CREATE TABLE IF NOT EXISTS `rule_version_binding` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `definition_id` BIGINT NOT NULL COMMENT '规则定义标识，关联 rule_definition.id',
  `version_no` INT NOT NULL COMMENT '规则对外展示的业务版本号',
  `generation` BIGINT NOT NULL DEFAULT 0 COMMENT '版本绑定代数，每次覆盖该业务版本递增',
  `snapshot_id` BIGINT DEFAULT NULL COMMENT '业务版本当前指向的不可变规则版本快照标识',
  `status` INT NOT NULL DEFAULT 1 COMMENT '业务版本状态：0=不可用，1=可用',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_business_version` (`definition_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则业务版本与不可变快照绑定表';

CREATE TABLE IF NOT EXISTS `rule_application_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `project_id` BIGINT DEFAULT NULL COMMENT '所属项目标识，关联 rule_project.id；0 表示全局资源',
  `root_rule_id` BIGINT NOT NULL COMMENT '根规则定义标识，关联 rule_definition.id',
  `trace_id` VARCHAR(128) NOT NULL COMMENT '执行追踪唯一标识',
  `occurred_at` DATETIME(6) NOT NULL COMMENT '业务申请对应逻辑执行的发生时间',
  `field_values` JSON NOT NULL COMMENT '按稳定字段标识保存的历史输入及显式记录结果，JSON 格式',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_application_trace` (`trace_id`),
  KEY `idx_application_time` (`occurred_at`),
  KEY `idx_application_project_time` (`project_id`, `occurred_at`),
  KEY `idx_application_rule_time` (`root_rule_id`, `project_id`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='按字段标识保存的业务申请历史表';

CREATE TABLE IF NOT EXISTS `console_operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `user_id` BIGINT DEFAULT NULL COMMENT '控制台账户标识，关联 console_user.id',
  `username` VARCHAR(128) NOT NULL COMMENT '操作账户名称快照',
  `module` VARCHAR(64) NOT NULL COMMENT '控制台业务模块编码，例如 project=项目、rule=规则、field=字段、model=模型、system=系统配置',
  `action` VARCHAR(32) NOT NULL COMMENT '业务操作编码，例如 VIEW=查看、CREATE=新增、UPDATE=修改、DELETE=删除、TEST=测试、SUBMIT=提交、APPROVE=批准、PUBLISH=发布、RESTORE=恢复',
  `request_method` VARCHAR(16) NOT NULL COMMENT '请求方法：GET=读取，POST=提交，PUT=整体更新，PATCH=局部更新，DELETE=删除，HEAD=仅响应头，OPTIONS=能力查询，INTERNAL=内部通知事件',
  `request_path` VARCHAR(512) NOT NULL COMMENT '接口请求路径',
  `page_path` VARCHAR(512) DEFAULT NULL COMMENT '发起操作的控制台页面路径',
  `target_type` VARCHAR(64) DEFAULT NULL COMMENT '操作对象的资源类型编码快照，例如 RULE=规则、MODEL=模型、PROJECT=项目',
  `target_id` VARCHAR(128) DEFAULT NULL COMMENT '操作目标标识快照',
  `target_name` VARCHAR(255) DEFAULT NULL COMMENT '操作目标名称快照',
  `outcome` VARCHAR(16) NOT NULL COMMENT '操作结果：SUCCESS=成功，FAILED=失败，DENIED=鉴权或权限拒绝',
  `result_code` INT NOT NULL COMMENT '本次操作的 HTTP 状态码；2xx=成功，401=未登录，403=无权限，其他按接口结果记录',
  `target_version` VARCHAR(64) DEFAULT NULL COMMENT '本次操作关联的业务版本标识',
  `change_summary` VARCHAR(160) DEFAULT NULL COMMENT '操作与变更摘要',
  `client_ip` VARCHAR(64) DEFAULT NULL COMMENT '操作客户端网络地址',
  `duration_ms` BIGINT NOT NULL COMMENT '操作耗时，单位毫秒',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_console_operation_time` (`create_time`, `id`),
  KEY `idx_console_operation_user_time` (`username`, `create_time`),
  KEY `idx_console_operation_module_time` (`module`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='控制台账户操作记录';

CREATE TABLE IF NOT EXISTS `offline_transfer_log_resource` (
  `log_id` BIGINT NOT NULL COMMENT '迁移日志标识，关联 offline_transfer_log.id',
  `resource_type` VARCHAR(32) NOT NULL COMMENT '迁移资源类型：PROJECT=项目，RULE=规则，RULE_VERSION=规则版本，VARIABLE=变量，DATA_OBJECT=对象，FUNCTION=函数，DATABASE=数据库，SFTP=文件数据源，EXTERNAL_DATASOURCE=外数数据源，EXTERNAL_API=外数接口，LIST_LIBRARY=名单库，LIST_RECORD_BATCH=名单批次，MODEL=模型，EXPERIMENT=实验，FIELD_VALIDATION=字段校验',
  `resource_id` BIGINT NOT NULL COMMENT '本地资源ID；0代表用户上传包中的全局内容',
  PRIMARY KEY (`log_id`, `resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='迁移日志内容权限索引';

CREATE TABLE IF NOT EXISTS `offline_transfer_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `operation_type` VARCHAR(16) NOT NULL COMMENT '迁移操作：EXPORT=导出，IMPORT=导入',
  `status` VARCHAR(16) NOT NULL COMMENT '操作状态：SUCCESS=成功，FAILED=失败',
  `operator` VARCHAR(128) DEFAULT NULL COMMENT '操作人标识',
  `package_digest` CHAR(64) DEFAULT NULL COMMENT '完整制品包的 SHA-256 摘要',
  `roots_json` LONGTEXT NOT NULL COMMENT '离线导出或导入的根资源标识列表，JSON 格式',
  `resource_count` INT NOT NULL DEFAULT 0 COMMENT '迁移涉及的资源数量',
  `content_json` LONGTEXT DEFAULT NULL COMMENT '迁移预检、映射及处理结果详情，JSON 格式',
  `error_message` VARCHAR(2000) DEFAULT NULL COMMENT '失败原因或异常说明',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_offline_transfer_log_time` (`create_time`),
  KEY `idx_offline_transfer_log_operation` (`operation_type`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='离线资源迁移操作日志表';

-- 系统常量依赖 uk_scope_project_var 唯一索引幂等补齐；已有常量的 ID 和配置保持不变。
-- 可选的业务初始变量和数据对象由 server 的 InitialDataInitializer 执行 sql/initial-data.sql，
-- 通过 RULE_ENGINE_INITIAL_DATA_ENABLED 控制，避免结构 schema 默认写入业务配置。
INSERT INTO `rule_variable`
  (`project_id`, `scope`, `var_code`, `var_label`, `script_name`, `var_type`, `var_source`,
   `default_value`, `value_range`, `example_value`, `description`, `sort_order`,
   `status`, `create_time`, `update_time`)
SELECT 0, 'GLOBAL', 'NULL_VALUE', '空值', 'NULL_VALUE', 'OBJECT', 'CONSTANT',
       'null', NULL, 'null', '系统常量：缺失值。', -999, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `rule_variable`
  (`project_id`, `scope`, `var_code`, `var_label`, `script_name`, `var_type`, `var_source`,
   `default_value`, `value_range`, `example_value`, `description`, `sort_order`,
   `status`, `create_time`, `update_time`)
SELECT 0, 'GLOBAL', 'EMPTY_STRING', '空字符串', 'EMPTY_STRING', 'STRING', 'CONSTANT',
       '', NULL, '', '系统常量：空字符串。', -998, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `rule_variable`
  (`project_id`, `scope`, `var_code`, `var_label`, `script_name`, `var_type`, `var_source`,
   `default_value`, `value_range`, `example_value`, `description`, `sort_order`,
   `status`, `create_time`, `update_time`)
SELECT 0, 'GLOBAL', 'EMPTY_LIST', '空列表', 'EMPTY_LIST', 'LIST', 'CONSTANT',
       '[]', NULL, '[]', '系统常量：空列表。', -997, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `rule_variable`
  (`project_id`, `scope`, `var_code`, `var_label`, `script_name`, `var_type`, `var_source`,
   `default_value`, `value_range`, `example_value`, `description`, `sort_order`,
   `status`, `create_time`, `update_time`)
SELECT 0, 'GLOBAL', 'EMPTY_MAP', '空映射', 'EMPTY_MAP', 'MAP', 'CONSTANT',
       '{}', NULL, '{}', '系统常量：空键值映射。', -996, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `rule_variable`
  (`project_id`, `scope`, `var_code`, `var_label`, `script_name`, `var_type`, `var_source`,
   `default_value`, `value_range`, `example_value`, `description`, `sort_order`,
   `status`, `create_time`, `update_time`)
SELECT 0, 'GLOBAL', 'FALSE_VALUE', '布尔假', 'FALSE_VALUE', 'BOOLEAN', 'CONSTANT',
       'false', NULL, 'false', '系统常量：布尔假。', -995, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `rule_variable`
  (`project_id`, `scope`, `var_code`, `var_label`, `script_name`, `var_type`, `var_source`,
   `default_value`, `value_range`, `example_value`, `description`, `sort_order`,
   `status`, `create_time`, `update_time`)
SELECT 0, 'GLOBAL', 'TRUE_VALUE', '布尔真', 'TRUE_VALUE', 'BOOLEAN', 'CONSTANT',
       'true', NULL, 'true', '系统常量：布尔真。', -994, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `rule_variable`
  (`project_id`, `scope`, `var_code`, `var_label`, `script_name`, `var_type`, `var_source`,
   `default_value`, `value_range`, `example_value`, `description`, `sort_order`,
   `status`, `create_time`, `update_time`)
SELECT 0, 'GLOBAL', 'NEGATIVE_ONE', '负一', 'NEGATIVE_ONE', 'NUMBER', 'CONSTANT',
       '-1', NULL, '-1', '系统常量：数值负一。', -993, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `rule_variable`
  (`project_id`, `scope`, `var_code`, `var_label`, `script_name`, `var_type`, `var_source`,
   `default_value`, `value_range`, `example_value`, `description`, `sort_order`,
   `status`, `create_time`, `update_time`)
SELECT 0, 'GLOBAL', 'ZERO', '零', 'ZERO', 'NUMBER', 'CONSTANT',
       '0', NULL, '0', '系统常量：数值零。', -992, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `rule_variable`
  (`project_id`, `scope`, `var_code`, `var_label`, `script_name`, `var_type`, `var_source`,
   `default_value`, `value_range`, `example_value`, `description`, `sort_order`,
   `status`, `create_time`, `update_time`)
SELECT 0, 'GLOBAL', 'ONE', '一', 'ONE', 'NUMBER', 'CONSTANT',
       '1', NULL, '1', '系统常量：数值一。', -991, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `rule_variable`
  (`project_id`, `scope`, `var_code`, `var_label`, `script_name`, `var_type`, `var_source`,
   `default_value`, `value_range`, `example_value`, `description`, `sort_order`,
   `status`, `create_time`, `update_time`)
SELECT 0, 'GLOBAL', 'NEGATIVE_INFINITY', '负无穷', 'NEGATIVE_INFINITY', 'DOUBLE', 'CONSTANT',
       '-Infinity', NULL, '-Infinity', '系统常量：数值比较下界。', -1000, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `rule_variable`
  (`project_id`, `scope`, `var_code`, `var_label`, `script_name`, `var_type`, `var_source`,
   `default_value`, `value_range`, `example_value`, `description`, `sort_order`,
   `status`, `create_time`, `update_time`)
SELECT 0, 'GLOBAL', 'POSITIVE_INFINITY', '正无穷', 'POSITIVE_INFINITY', 'DOUBLE', 'CONSTANT',
       'Infinity', NULL, 'Infinity', '系统常量：数值比较上界。', -990, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
ON DUPLICATE KEY UPDATE `id` = `id`;

-- 旧库缺少逻辑视图依赖字段时先跳过视图创建，SchemaSyncService 补齐字段后再创建；新库和已升级库直接创建。
SET @schema_log_view_sql = IF(
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE() AND table_name IN ('rule_execution_log', 'rule_execution_log_identity')) = 2
  AND (SELECT COUNT(*) FROM information_schema.columns
       WHERE table_schema = DATABASE() AND table_name = 'rule_execution_log'
         AND column_name IN ('trace_id', 'attempt_no')) = 2,
  'CREATE OR REPLACE SQL SECURITY INVOKER VIEW `rule_execution_log_current` AS
SELECT log_row.*
FROM rule_execution_log log_row
LEFT JOIN rule_execution_log_identity logical_identity
  ON logical_identity.trace_id = CONVERT(log_row.trace_id USING utf8mb4) COLLATE utf8mb4_unicode_ci
WHERE NULLIF(TRIM(log_row.trace_id), '''') IS NULL
   OR (logical_identity.log_id = log_row.id AND logical_identity.log_create_time = log_row.create_time)
   OR (logical_identity.log_id IS NULL AND NOT EXISTS (
       SELECT 1 FROM rule_execution_log newer
       WHERE newer.trace_id = log_row.trace_id
         AND (COALESCE(newer.attempt_no, 1) > COALESCE(log_row.attempt_no, 1)
           OR (COALESCE(newer.attempt_no, 1) = COALESCE(log_row.attempt_no, 1)
             AND (newer.create_time > log_row.create_time
               OR (newer.create_time = log_row.create_time AND newer.id > log_row.id))))
   ))',
  'SELECT 1'
);
PREPARE schema_log_view_stmt FROM @schema_log_view_sql;
EXECUTE schema_log_view_stmt;
DEALLOCATE PREPARE schema_log_view_stmt;

SET @schema_statistics_view_sql = IF(
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE() AND table_name IN ('rule_execution_statistics', 'rule_execution_log_current')) = 2
  AND (SELECT COUNT(*) FROM information_schema.columns
       WHERE table_schema = DATABASE() AND table_name = 'rule_execution_statistics'
         AND column_name IN ('trace_id', 'attempt_no')) = 2,
  'CREATE OR REPLACE SQL SECURITY INVOKER VIEW `rule_execution_statistics_current` AS
SELECT statistics.*
FROM rule_execution_statistics statistics
LEFT JOIN rule_execution_log_identity logical_identity
  ON logical_identity.trace_id = CONVERT(statistics.trace_id USING utf8mb4) COLLATE utf8mb4_unicode_ci
WHERE NULLIF(TRIM(statistics.trace_id), '''') IS NULL
   OR (logical_identity.log_id = statistics.id AND logical_identity.log_create_time = statistics.create_time)
   OR (logical_identity.log_id IS NULL AND EXISTS (
       SELECT 1 FROM rule_execution_log_current legacy
       WHERE legacy.id = statistics.id AND legacy.create_time = statistics.create_time
   ))',
  'SELECT 1'
);
PREPARE schema_statistics_view_stmt FROM @schema_statistics_view_sql;
EXECUTE schema_statistics_view_stmt;
DEALLOCATE PREPARE schema_statistics_view_stmt;

-- 全局系统配置：固定分组 ID，业务接口只读取已生效快照。
CREATE TABLE IF NOT EXISTS `rule_system_setting` (
  `id` BIGINT NOT NULL COMMENT '固定配置分组标识：1=品牌，2=通知邮箱，3=账户访问策略，4=对外结果码映射',
  `scope` VARCHAR(16) NOT NULL DEFAULT 'GLOBAL' COMMENT '配置作用范围，固定 GLOBAL=全局',
  `project_id` BIGINT NOT NULL DEFAULT 0 COMMENT '配置所属项目标识，固定 0=全局配置',
  `config_json` LONGTEXT NOT NULL COMMENT '已生效的系统配置快照，JSON 格式',
  `secret_ciphertext` LONGTEXT COMMENT '敏感配置的 AES-GCM 加密密文',
  `secret_digest` VARCHAR(64) COMMENT '敏感配置内容的 SHA-256 摘要',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '生效配置版本号',
  `update_by` VARCHAR(128) COMMENT '最后更新人标识',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局系统配置生效快照';

CREATE TABLE IF NOT EXISTS `rule_system_asset` (
  `id` VARCHAR(36) NOT NULL COMMENT '品牌图片的全局唯一标识',
  `content_type` VARCHAR(64) NOT NULL COMMENT '存储图片媒体类型，固定 image/png=便携式网络图像格式；上传后统一转码',
  `content` MEDIUMBLOB NOT NULL COMMENT '品牌图片文件的二进制内容',
  `create_by` VARCHAR(128) COMMENT '创建人标识',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统品牌图片';
