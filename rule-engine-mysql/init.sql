-- 开启root远程登录
USE mysql;
ALTER USER 'root'@'%' IDENTIFIED WITH mysql_native_password BY '1qaz@WSX';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;

CREATE DATABASE IF NOT EXISTS `rule_engine` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `rule_engine`;

SET NAMES utf8mb4;
SET character_set_connection = utf8mb4;

-- ============================================================
-- 1. rule_project - 规则项目表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_project` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_code` VARCHAR(64)  NOT NULL                COMMENT '项目编码',
  `project_name` VARCHAR(128) NOT NULL                COMMENT '项目名称（中文）',
  `description`  VARCHAR(512) DEFAULT NULL             COMMENT '项目描述',
  `status`       TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `access_token` VARCHAR(64)  DEFAULT NULL             COMMENT '访问Token',
  `create_by`    VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    VARCHAR(64)  DEFAULT NULL             COMMENT '更新人',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_code` (`project_code`),
  UNIQUE KEY `uk_access_token` (`access_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则项目表';

-- ============================================================
-- 2. rule_definition - 规则定义表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_definition` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`        BIGINT       NOT NULL                COMMENT '所属项目ID',
  `project_code`      VARCHAR(64)  DEFAULT NULL             COMMENT '所属项目编码',
  `project_name`      VARCHAR(128) DEFAULT NULL             COMMENT '所属项目名称',
  `rule_code`         VARCHAR(128) NOT NULL                COMMENT '规则编码（Client SDK调用标识）',
  `rule_name`         VARCHAR(256) NOT NULL                COMMENT '规则名称（中文）',
  `model_type`        VARCHAR(16)  NOT NULL                COMMENT '决策模型类型：TABLE/TREE/FLOW/CROSS/SCORE',
  `description`       VARCHAR(512) DEFAULT NULL             COMMENT '规则描述',
  `scope`             VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL-全局，PROJECT-项目级',
  `current_version`   INT          NOT NULL DEFAULT 0       COMMENT '当前设计版本号',
  `published_version` INT          DEFAULT NULL             COMMENT '已发布版本号',
  `status`            TINYINT      NOT NULL DEFAULT 0       COMMENT '状态：0-草稿，1-已发布，2-已下线',
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
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则定义表';

-- ============================================================
-- 2.1 rule_definition_input_field - 规则输入字段表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_definition_input_field` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `definition_id`    BIGINT       NOT NULL                COMMENT '所属规则ID',
  `var_id`          BIGINT       DEFAULT NULL             COMMENT '关联字段ID，需结合 ref_type 判断所属资源表',
  `ref_type`        VARCHAR(32)  DEFAULT NULL             COMMENT '引用类型：VARIABLE/CONSTANT/DATA_OBJECT/MODEL',
  `field_name`       VARCHAR(128) NOT NULL                COMMENT '字段名称（原始名称）',
  `field_label`      VARCHAR(128) DEFAULT NULL             COMMENT '字段中文名称',
  `script_name`      VARCHAR(128) DEFAULT NULL             COMMENT '脚本中的引用名（驼峰）',
  `field_type`       VARCHAR(32)  DEFAULT NULL             COMMENT '数据类型：STRING/NUMBER/INTEGER/DOUBLE/BOOLEAN/DATE',
  `missing_value`    VARCHAR(256) DEFAULT NULL             COMMENT '缺失值处理策略',
  `default_value`    VARCHAR(256) DEFAULT NULL             COMMENT '默认值',
  `valid_values`     TEXT         DEFAULT NULL             COMMENT '有效值列表（JSON数组）',
  `transform_type`   VARCHAR(32)  DEFAULT NULL             COMMENT '转换类型：NONE/NORMALIZE/DISCRETIZE/MAPVALUES/MINMAX',
  `transform_params` JSON         DEFAULT NULL             COMMENT '转换参数',
  `sort_order`       INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  `status`           TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_definition_id` (`definition_id`),
  KEY `idx_var_id` (`var_id`),
  KEY `idx_ref_type_var_id` (`ref_type`, `var_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则输入字段表';

-- ============================================================
-- 2.2 rule_definition_output_field - 规则输出字段表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_definition_output_field` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `definition_id`    BIGINT       NOT NULL                COMMENT '所属规则ID',
  `var_id`          BIGINT       DEFAULT NULL             COMMENT '关联字段ID，需结合 ref_type 判断所属资源表',
  `ref_type`        VARCHAR(32)  DEFAULT NULL             COMMENT '引用类型：VARIABLE/CONSTANT/DATA_OBJECT/MODEL',
  `field_name`       VARCHAR(128) NOT NULL                COMMENT '字段名称（输出变量名）',
  `field_label`      VARCHAR(128) DEFAULT NULL             COMMENT '字段中文名称',
  `script_name`      VARCHAR(128) DEFAULT NULL             COMMENT '脚本中的引用名（驼峰）',
  `field_type`       VARCHAR(32)  DEFAULT NULL             COMMENT '字段类型：STRING/NUMBER/INTEGER/DOUBLE',
  `transform_type`   VARCHAR(32)  DEFAULT NULL             COMMENT '转换方法：NONE/RENAME/SCALE/OHE',
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

-- ============================================================
-- 3. rule_definition_content - 规则内容表（设计态）
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_definition_content` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `definition_id`   BIGINT   NOT NULL                COMMENT '规则定义ID',
  `model_json`      LONGTEXT NOT NULL                COMMENT '模型设计数据（JSON）',
  `compiled_script` TEXT     DEFAULT NULL             COMMENT '编译后脚本',
  `compiled_type`   VARCHAR(16) DEFAULT NULL          COMMENT '编译产物类型：QLEXPRESS',
  `compile_status`  TINYINT  NOT NULL DEFAULT 0       COMMENT '编译状态：0-未编译，1-成功，2-失败',
  `compile_message` VARCHAR(1024) DEFAULT NULL        COMMENT '编译信息',
  `compile_time`    DATETIME DEFAULT NULL             COMMENT '最近编译时间',
  `script_mode`     VARCHAR(16) NOT NULL DEFAULT 'visual' COMMENT '编辑模式：visual-可视化，script-脚本模式',
  `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_definition_id` (`definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则内容表（设计态数据和编译产物）';

-- ============================================================
-- 4. rule_definition_ref - 规则关联表（项目关联全局规则）
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_definition_ref` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `definition_id`   BIGINT   NOT NULL                COMMENT '全局规则定义ID',
  `project_id`      BIGINT   NOT NULL                COMMENT '关联项目ID',
  `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_definition_project` (`definition_id`, `project_id`),
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则关联表（用于项目关联全局规则）';

-- ============================================================
-- 4. rule_definition_version - 规则版本历史表（HASH分区）
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_definition_version` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `definition_id`   BIGINT       NOT NULL                COMMENT '规则定义ID',
  `version`         INT          NOT NULL                COMMENT '版本号',
  `model_json`      LONGTEXT     NOT NULL                COMMENT '版本快照 - 模型JSON',
  `compiled_script` TEXT         DEFAULT NULL             COMMENT '版本快照 - 编译后脚本',
  `compiled_type`   VARCHAR(16)  DEFAULT NULL             COMMENT '编译产物类型',
  `change_log`      VARCHAR(512) DEFAULT NULL             COMMENT '变更说明（中文）',
  `publish_by`      VARCHAR(64)  DEFAULT NULL             COMMENT '发布人',
  `publish_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (`id`, `definition_id`),
  UNIQUE KEY `uk_def_version` (`definition_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则版本历史表'
PARTITION BY HASH(`definition_id`) PARTITIONS 8;

-- ============================================================
-- 5. rule_published - 已发布规则表（Client SDK同步数据源）
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_published` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_code`       VARCHAR(128) NOT NULL                COMMENT '规则编码',
  `definition_id`   BIGINT       NOT NULL                COMMENT '规则定义ID',
  `project_code`    VARCHAR(64)  DEFAULT NULL             COMMENT '所属项目编码',
  `version`         INT          NOT NULL                COMMENT '发布版本号',
  `model_type`      VARCHAR(16)  NOT NULL                COMMENT '决策模型类型',
  `compiled_script` TEXT         NOT NULL                COMMENT '编译后脚本',
  `compiled_type`   VARCHAR(16)  DEFAULT NULL             COMMENT '编译产物类型',
  `model_json`      LONGTEXT     DEFAULT NULL             COMMENT '模型JSON（设计器回显用）',
  `status`          TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-已下线，1-已上线',
  `publish_by`      VARCHAR(64)  DEFAULT NULL             COMMENT '发布人',
  `publish_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `offline_time`    DATETIME     DEFAULT NULL             COMMENT '下线时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_code` (`rule_code`),
  KEY `idx_status` (`status`),
  KEY `idx_definition_id` (`definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已发布规则表（Client SDK同步数据源）';

-- ============================================================
-- 6. rule_data_object - 数据对象定义表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_data_object` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`       BIGINT       NOT NULL                COMMENT '所属项目ID',
  `scope`             VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL-全局，PROJECT-项目级',
  `object_code`      VARCHAR(128) NOT NULL                COMMENT '对象编码（Java类名/JSON键名）',
  `object_label`     VARCHAR(128) DEFAULT NULL             COMMENT '对象中文名称',
  `script_name`      VARCHAR(128) DEFAULT NULL             COMMENT '脚本中的对象引用名（默认驼峰，如 taxRequest）',
  `object_type`      VARCHAR(16)  NOT NULL DEFAULT 'INPUT' COMMENT '对象类型：INPUT-输入/OUTPUT-输出/INOUT-输入输出',
  `source_type`      VARCHAR(16)  DEFAULT NULL             COMMENT '来源类型：JAVA/JSON',
  `source_content`   LONGTEXT     DEFAULT NULL             COMMENT '原始文件内容',
  `parent_object_id` BIGINT       DEFAULT NULL             COMMENT '父对象ID（嵌套对象）',
  `status`           TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_project_object` (`scope`, `project_id`, `object_code`),
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据对象定义表（Java实体类/JSON对象）';

-- ============================================================
-- 7. rule_data_object_field - 数据对象字段表（与 rule_variable 解耦）
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_data_object_field` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`       BIGINT       NOT NULL                COMMENT '所属项目ID',
  `scope`             VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL-全局，PROJECT-项目级',
  `object_id`        BIGINT       NOT NULL                COMMENT '所属数据对象ID',
  `var_code`         VARCHAR(128) NOT NULL                COMMENT '字段编码',
  `var_label`        VARCHAR(128) NOT NULL                COMMENT '字段中文名称',
  `script_name`      VARCHAR(128) DEFAULT NULL             COMMENT '脚本中的字段名（驼峰）',
  `var_type`         VARCHAR(32)  NOT NULL                COMMENT '数据类型：STRING/NUMBER/BOOLEAN/DATE/ENUM/OBJECT/LIST/MAP',
  `ref_object_code`  VARCHAR(128) DEFAULT NULL             COMMENT 'OBJECT 时引用的对象编码（兼容旧逻辑，铁律四后以 ref_object_id 为准）',
  `ref_object_id`    BIGINT       DEFAULT NULL             COMMENT 'OBJECT 时引用的对象ID（铁律四：指向 rule_data_object.id）',
  `generic_type`      VARCHAR(32)  DEFAULT NULL             COMMENT '泛型类型（LIST 类型字段的元素类型，如 OBJECT/STRING/NUMBER）',
  `parent_field_id`  BIGINT       DEFAULT NULL             COMMENT '父字段ID（嵌套预留）',
  `sort_order`       INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  `status`           TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_object_var_code` (`object_id`, `parent_field_id`, `var_code`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_object_id` (`object_id`),
  KEY `idx_ref_object_id` (`ref_object_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据对象字段表';

-- ============================================================
-- 8. rule_data_object_field_option - 对象字段枚举选项
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_data_object_field_option` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `field_id`     BIGINT       NOT NULL                COMMENT '所属对象字段ID',
  `option_value` VARCHAR(256) NOT NULL                COMMENT '选项值',
  `option_label` VARCHAR(256) NOT NULL                COMMENT '选项中文标签',
  `sort_order`   INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  PRIMARY KEY (`id`),
  KEY `idx_field_id` (`field_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据对象字段枚举选项';

-- ============================================================
-- 9. rule_variable - 规则变量表（普通变量与常量，var_source=CONSTANT 时须配置 default_value）
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_variable` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`        BIGINT       NOT NULL                COMMENT '所属项目ID',
  `scope`             VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL-全局，PROJECT-项目级',
  `var_code`          VARCHAR(128) NOT NULL                COMMENT '变量编码',
  `var_label`         VARCHAR(128) NOT NULL                COMMENT '变量中文名称',
  `script_name`       VARCHAR(128) DEFAULT NULL             COMMENT '脚本中的变量名（默认驼峰）',
  `var_type`          VARCHAR(32)  NOT NULL                COMMENT '数据类型：STRING/NUMBER/BOOLEAN/DATE/ENUM/OBJECT/LIST/MAP',
  `var_source`        VARCHAR(32)  NOT NULL DEFAULT 'INPUT' COMMENT '来源：INPUT/COMPUTED/CONSTANT/DB/API/LIST',
  `source_config`     JSON         DEFAULT NULL             COMMENT '外部来源配置JSON：API/DB/LIST变量绑定接口、SQL、入参映射、结果路径等',
  `default_value`     TEXT         DEFAULT NULL             COMMENT '默认值（常量必填，可为较长 JSON）',
  `value_range`       VARCHAR(512) DEFAULT NULL             COMMENT '取值范围描述',
  `example_value`     VARCHAR(256) DEFAULT NULL             COMMENT '示例值',
  `description`       VARCHAR(512) DEFAULT NULL             COMMENT '变量说明',
  `sort_order`        INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  `status`            TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_project_var` (`scope`, `project_id`, `var_code`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_var_source` (`var_source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则变量表（普通变量与常量）';

-- ============================================================
-- 10. rule_variable_option - 规则变量选项表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_variable_option` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `variable_id`  BIGINT       NOT NULL                COMMENT '所属变量ID',
  `option_value` VARCHAR(256) NOT NULL                COMMENT '选项值',
  `option_label` VARCHAR(256) NOT NULL                COMMENT '选项中文标签',
  `sort_order`   INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  PRIMARY KEY (`id`),
  KEY `idx_variable_id` (`variable_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则变量选项表（枚举变量的可选值）';

-- ============================================================
-- 10.1 rule_list_library - 名单库配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_list_library` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`   BIGINT       NOT NULL DEFAULT 0       COMMENT '所属项目ID，0 表示全局',
  `scope`        VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL/PROJECT',
  `list_code`    VARCHAR(128) NOT NULL                 COMMENT '名单库编码',
  `list_name`    VARCHAR(128) NOT NULL                 COMMENT '名单库名称',
  `list_type`    VARCHAR(32)  NOT NULL DEFAULT 'BLACK' COMMENT '名单库类型：BLACK/GREY/WHITE/OTHER，仅用于标识',
  `description`  VARCHAR(512) DEFAULT NULL             COMMENT '说明',
  `status`       TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_list_scope_project_code` (`scope`, `project_id`, `list_code`),
  KEY `idx_list_project_id` (`project_id`),
  KEY `idx_list_type_status` (`list_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单库配置表';

-- ============================================================
-- 10.2 rule_list_record - 名单当前记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_list_record` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `list_id`        BIGINT       NOT NULL                COMMENT '名单库ID',
  `item_type`      VARCHAR(32)  NOT NULL                COMMENT '名单内容类型：MOBILE/ID_CARD/ADDRESS/IP/DEVICE/NAME/GPS/EMAIL/BANK_CARD/OTHER',
  `item_content`   VARCHAR(512) NOT NULL                COMMENT '名单内容',
  `effective_time` DATETIME     DEFAULT NULL            COMMENT '生效时间',
  `expire_time`    DATETIME     DEFAULT NULL            COMMENT '失效时间',
  `reason`         VARCHAR(512) DEFAULT NULL            COMMENT '插入原因',
  `remark`         VARCHAR(512) DEFAULT NULL            COMMENT '插入备注',
  `last_operation` VARCHAR(16)  NOT NULL DEFAULT 'ADD'  COMMENT '最近一次操作：ADD/UPDATE/DELETE',
  `status`         TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0-停用，1-启用',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_list_type_content` (`list_id`, `item_type`, `item_content`),
  KEY `idx_list_record_lookup` (`list_id`, `item_type`, `item_content`, `status`),
  KEY `idx_list_record_effective` (`effective_time`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单当前记录表';

-- ============================================================
-- 10.3 rule_list_record_log - 名单变更日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_list_record_log` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `list_id`        BIGINT       NOT NULL                COMMENT '名单库ID',
  `record_id`      BIGINT       DEFAULT NULL            COMMENT '名单记录ID',
  `item_type`      VARCHAR(32)  NOT NULL                COMMENT '名单内容类型',
  `item_content`   VARCHAR(512) NOT NULL                COMMENT '名单内容',
  `effective_time` DATETIME     DEFAULT NULL            COMMENT '生效时间',
  `expire_time`    DATETIME     DEFAULT NULL            COMMENT '失效时间',
  `reason`         VARCHAR(512) DEFAULT NULL            COMMENT '插入原因',
  `remark`         VARCHAR(512) DEFAULT NULL            COMMENT '插入备注',
  `operation`      VARCHAR(16)  NOT NULL                COMMENT '执行操作：ADD/UPDATE/DELETE',
  `operator`       VARCHAR(64)  DEFAULT NULL            COMMENT '操作人',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_list_log_list_record` (`list_id`, `record_id`),
  KEY `idx_list_log_content` (`list_id`, `item_type`, `item_content`),
  KEY `idx_list_log_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单变更日志表';

-- ============================================================
-- 11. rule_function - 自定义函数定义表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_function` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`    BIGINT       NOT NULL                COMMENT '所属项目ID',
  `scope`         VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL-全局，PROJECT-项目级',
  `func_code`     VARCHAR(128) NOT NULL                COMMENT '函数编码（QLExpress 中的函数名）',
  `func_name`     VARCHAR(128) NOT NULL                COMMENT '函数中文名称',
  `description`   VARCHAR(512) DEFAULT NULL             COMMENT '函数说明',
  `params_json`   TEXT         DEFAULT NULL             COMMENT '参数定义JSON [{"name":"a","type":"NUMBER","label":"金额"}]',
  `return_type`   VARCHAR(32)  DEFAULT 'STRING'         COMMENT '返回值类型：STRING/NUMBER/BOOLEAN/OBJECT',
  `impl_type`     VARCHAR(16)  NOT NULL DEFAULT 'SCRIPT' COMMENT '实现方式：SCRIPT-QLExpress脚本/JAVA-Java类/BEAN-Spring Bean',
  `impl_script`   TEXT         DEFAULT NULL             COMMENT 'QLExpress 实现脚本（SCRIPT 类型）',
  `impl_class`    VARCHAR(256) DEFAULT NULL             COMMENT 'Java 实现类全限定名（JAVA 类型）',
  `impl_method`   VARCHAR(128) DEFAULT NULL             COMMENT '方法名（JAVA/BEAN 类型时指定）',
  `impl_bean_name` VARCHAR(128) DEFAULT NULL            COMMENT 'Spring Bean 名称（BEAN 类型时指定）',
  `status`        TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_project_func` (`scope`, `project_id`, `func_code`),
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自定义函数定义表';

CREATE TABLE IF NOT EXISTS `rule_function_version` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'primary id',
  `function_id`   BIGINT       NOT NULL                COMMENT 'function id',
  `version`       INT          NOT NULL                COMMENT 'version',
  `function_json` TEXT         NOT NULL                COMMENT 'function snapshot',
  `change_log`    VARCHAR(512) DEFAULT NULL            COMMENT 'change log',
  `publish_by`    VARCHAR(64)  DEFAULT NULL            COMMENT 'operator',
  `publish_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'snapshot time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_function_version` (`function_id`, `version`),
  KEY `idx_function_version_time` (`function_id`, `publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='function version history';

-- ============================================================
-- 12. rule_execution_log - 规则执行日志表（按月RANGE分区）
-- ============================================================
-- 先删除原有分区（如果是修改现有表）
-- ALTER TABLE rule_execution_log REMOVE PARTITIONING;
CREATE TABLE IF NOT EXISTS `rule_execution_log` (
   `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
   `rule_code`       VARCHAR(128)  NOT NULL                COMMENT '规则编码',
   `project_code`    VARCHAR(128)  DEFAULT NULL             COMMENT '项目编码',
   `rule_version`    INT           DEFAULT NULL             COMMENT '规则版本号',
   `model_type`      VARCHAR(16)   DEFAULT NULL             COMMENT '决策模型类型',
   `source`          VARCHAR(32)   NOT NULL DEFAULT 'SERVER' COMMENT '来源：SERVER-服务端测试，CLIENT-客户端执行',
   `client_app_name` VARCHAR(128)  DEFAULT NULL             COMMENT '客户端应用名称',
   `client_ip`       VARCHAR(64)   DEFAULT NULL             COMMENT '客户端IP',
   `input_params`    TEXT          DEFAULT NULL             COMMENT '输入参数（JSON）',
   `output_result`   TEXT          DEFAULT NULL             COMMENT '输出结果（JSON）',
   `trace_info`      LONGTEXT      DEFAULT NULL             COMMENT '表达式追踪树（JSON）',
   `success`         TINYINT       NOT NULL DEFAULT 1       COMMENT '执行结果：0-失败，1-成功',
   `error_message`   VARCHAR(1024) DEFAULT NULL             COMMENT '错误信息',
   `execute_time_ms` BIGINT        DEFAULT NULL             COMMENT '执行耗时（毫秒）',
   `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
   PRIMARY KEY (`id`, `create_time`),
   KEY `idx_rule_code` (`rule_code`, `create_time`),
   KEY `idx_project_code` (`project_code`, `create_time`),
   KEY `idx_source` (`source`, `create_time`),
   KEY `idx_client_app` (`client_app_name`, `create_time`)
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

-- ============================================================
-- 13. rule_model - 统一模型主表
-- 支持多种模型格式（PMML/ONNX/TENSORFLOW/LIGHTGBM/PICKLE等），格式特有配置存入 model_config（JSON）
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_model` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`         BIGINT       DEFAULT NULL             COMMENT '所属项目ID（全局模型可为空）',
  `project_code`       VARCHAR(64)  DEFAULT NULL             COMMENT '所属项目编码',
  `project_name`       VARCHAR(128) DEFAULT NULL             COMMENT '所属项目名称',
  `scope`              VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL-全局，PROJECT-项目级',
  `model_code`         VARCHAR(128) NOT NULL                COMMENT '模型编码（唯一）',
  `model_name`         VARCHAR(256) NOT NULL                COMMENT '模型名称（中文）',
  `model_type`         VARCHAR(32)  NOT NULL                COMMENT '模型大类：CLASSIFICATION-分类/REGRESSION-回归/CLUSTERING-聚类/ML-机器学习',
  `model_format`       VARCHAR(32)  NOT NULL                COMMENT '模型格式：PMML/PICKLE/DILL/ONNX',
  `description`        VARCHAR(512) DEFAULT NULL             COMMENT '模型描述',
  `model_content`      LONGTEXT     DEFAULT NULL             COMMENT '模型文件原始内容（Base64编码）',
  `model_file_name`    VARCHAR(256) DEFAULT NULL             COMMENT '上传时的文件名',
  `model_file_size`    BIGINT       DEFAULT NULL             COMMENT '文件大小（字节）',
  `model_config`       JSON         DEFAULT NULL             COMMENT '模型特有配置（格式无关JSON）',
  `input_field_count`  INT          DEFAULT NULL             COMMENT '输入字段数量',
  `output_field_count` INT          DEFAULT NULL             COMMENT '输出字段数量',
  `target_categories`  VARCHAR(256) DEFAULT NULL             COMMENT '目标变量类别数（分类模型）',
  `model_version`      VARCHAR(64)  DEFAULT NULL             COMMENT '模型自身的版本号',
  `training_info`      JSON         DEFAULT NULL             COMMENT '训练信息（特征重要性等）',
  `current_version`     INT          NOT NULL DEFAULT 0       COMMENT '平台当前设计版本号',
  `published_version`   INT          DEFAULT NULL             COMMENT '平台已发布版本号',
  `status`             TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：0-停用，1-启用',
  `create_by`          VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
  `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          VARCHAR(64)  DEFAULT NULL             COMMENT '更新人',
  `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_code` (`model_code`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_scope` (`scope`),
  KEY `idx_model_format` (`model_format`),
  KEY `idx_model_type` (`model_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一模型主表';

-- ============================================================
-- 14. rule_model_input_field - 统一模型输入字段表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_model_input_field` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_id`         BIGINT       NOT NULL                COMMENT '所属模型ID',
  `var_id`          BIGINT       DEFAULT NULL             COMMENT '关联字段ID，需结合 ref_type 判断所属资源表',
  `ref_type`        VARCHAR(32)  DEFAULT NULL             COMMENT '引用类型：VARIABLE/CONSTANT/DATA_OBJECT/MODEL',
  `field_name`       VARCHAR(128) NOT NULL                COMMENT '字段名称（原始名称）',
  `field_label`      VARCHAR(128) NOT NULL                COMMENT '字段中文名称',
  `script_name`      VARCHAR(128) DEFAULT NULL             COMMENT '脚本中的引用名（驼峰）',
  `field_type`       VARCHAR(32)  NOT NULL                COMMENT '数据类型：STRING/NUMBER/INTEGER/DOUBLE/BOOLEAN/DATE',
  `data_type`        VARCHAR(32)  DEFAULT NULL             COMMENT '数据用途类型：CONTINUOUS-连续/CATEGORICAL-类别/ORDINAL-有序',
  `missing_value`    VARCHAR(256) DEFAULT NULL             COMMENT '缺失值处理策略',
  `default_value`    VARCHAR(256) DEFAULT NULL             COMMENT '默认值',
  `valid_values`     TEXT         DEFAULT NULL             COMMENT '有效值列表（JSON数组，分类变量）',
  `feature_name`     VARCHAR(128) DEFAULT NULL             COMMENT '模型内部特征名称（如XGBoost的f0）',
  `transform_type`   VARCHAR(32)  DEFAULT NULL             COMMENT '预处理类型：NONE/NORMALIZE/DISCRETIZE/MAPVALUES/MINMAX',
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

-- ============================================================
-- 15. rule_model_output_field - 统一模型输出字段表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_model_output_field` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_id`         BIGINT       NOT NULL                COMMENT '所属模型ID',
  `var_id`          BIGINT       DEFAULT NULL             COMMENT '关联字段ID，需结合 ref_type 判断所属资源表',
  `ref_type`        VARCHAR(32)  DEFAULT NULL             COMMENT '引用类型：VARIABLE/CONSTANT/DATA_OBJECT/MODEL',
  `field_name`       VARCHAR(128) NOT NULL                COMMENT '字段名称（输出变量名）',
  `field_label`      VARCHAR(128) NOT NULL                COMMENT '字段中文名称',
  `script_name`      VARCHAR(128) DEFAULT NULL             COMMENT '脚本中的引用名（驼峰）',
  `field_type`       VARCHAR(32)  NOT NULL                COMMENT '字段类型：STRING/NUMBER/INTEGER/DOUBLE/PROBABILITY/VECTOR',
  `target_field`     VARCHAR(128) DEFAULT NULL             COMMENT '对应的目标变量名',
  `feature_name`     VARCHAR(128) DEFAULT NULL             COMMENT '模型内部输出特征名',
  `transform_type`   VARCHAR(32)  DEFAULT NULL             COMMENT '转换方法：NONE/RENAME/SCALE/OHE',
  `is_probability`   TINYINT      NOT NULL DEFAULT 0       COMMENT '是否概率输出：0-否，1-是',
  `category`         VARCHAR(64)  DEFAULT NULL             COMMENT '类别标签（概率输出时指定）',
  `sort_order`       INT          NOT NULL DEFAULT 0       COMMENT '排序序号',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_model_id` (`model_id`),
  KEY `idx_var_id` (`var_id`),
  KEY `idx_ref_type_var_id` (`ref_type`, `var_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一模型输出字段表';

-- ============================================================
-- 16. rule_model_version - 模型版本历史表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_model_version` (
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

-- ============================================================
-- 17. rule_model_ref - 模型关联表（项目关联全局模型）
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_model_ref` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_id`   BIGINT   NOT NULL                COMMENT '全局模型ID',
  `project_id` BIGINT   NOT NULL                COMMENT '关联项目ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_project` (`model_id`, `project_id`),
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型关联表（用于项目关联全局模型）';

-- ============================================================
-- 18. rule_external_datasource - 外部 API 数据源定义表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_external_datasource` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`          BIGINT       NOT NULL DEFAULT 0      COMMENT '所属项目ID，0表示全局',
  `scope`               VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL/PROJECT',
  `datasource_code`     VARCHAR(128) NOT NULL                COMMENT '外数数据源编码',
  `datasource_name`     VARCHAR(128) NOT NULL                COMMENT '外数数据源名称',
  `provider_name`       VARCHAR(128) DEFAULT NULL            COMMENT '第三方服务提供方',
  `protocol`            VARCHAR(16)  NOT NULL DEFAULT 'HTTP' COMMENT '协议类型：HTTP/HTTPS/RULE_ENGINE',
  `base_url`            VARCHAR(512) NOT NULL                COMMENT '基础地址',
  `auth_type`           VARCHAR(32)  NOT NULL DEFAULT 'NONE' COMMENT '默认鉴权方式：NONE/BASIC/BEARER/API_KEY/OAUTH2/TOKEN_API/CUSTOM',
  `auth_config`         JSON         DEFAULT NULL            COMMENT '默认鉴权配置JSON',
  `token_cache_seconds` INT          NOT NULL DEFAULT 0      COMMENT 'token缓存秒数，0表示不缓存',
  `description`         VARCHAR(512) DEFAULT NULL            COMMENT '说明',
  `status`              TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0-停用，1-启用',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ext_ds_scope_project_code` (`scope`, `project_id`, `datasource_code`),
  KEY `idx_ext_ds_project_id` (`project_id`),
  KEY `idx_ext_ds_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部API数据源定义表';

-- ============================================================
-- 19. rule_external_api_config - 外部 API 接口配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_external_api_config` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `datasource_id`        BIGINT       NOT NULL                COMMENT '外数数据源ID',
  `api_code`             VARCHAR(128) NOT NULL                COMMENT '接口编码',
  `api_name`             VARCHAR(128) NOT NULL                COMMENT '接口名称',
  `request_method`       VARCHAR(16)  NOT NULL DEFAULT 'POST' COMMENT '请求方法：GET/POST/PUT/DELETE/PATCH',
  `endpoint_url`         VARCHAR(512) NOT NULL                COMMENT '接口相对或完整地址',
  `content_type`         VARCHAR(128) DEFAULT NULL            COMMENT '请求Content-Type，空表示不主动设置',
  `request_mode`         VARCHAR(16)  NOT NULL DEFAULT 'SYNC' COMMENT '调用模式：SYNC/ASYNC',
  `request_object_id`    BIGINT       DEFAULT NULL            COMMENT '请求数据对象ID',
  `response_object_id`   BIGINT       DEFAULT NULL            COMMENT '响应数据对象ID',
  `header_config`        JSON         DEFAULT NULL            COMMENT '请求头配置JSON',
  `query_config`         JSON         DEFAULT NULL            COMMENT 'Query参数配置JSON',
  `request_mapping`      JSON         DEFAULT NULL            COMMENT '入参映射配置JSON',
  `response_mapping`     JSON         DEFAULT NULL            COMMENT '响应映射配置JSON',
  `body_template`        LONGTEXT     DEFAULT NULL            COMMENT '请求体模板',
  `auth_mode`            VARCHAR(32)  NOT NULL DEFAULT 'INHERIT' COMMENT '接口鉴权：INHERIT/NONE/BASIC/BEARER/API_KEY/OAUTH2/TOKEN_API/CUSTOM',
  `auth_api_config`      JSON         DEFAULT NULL            COMMENT '接口级鉴权与token获取配置JSON',
  `token_cache_seconds`  INT          NOT NULL DEFAULT 0      COMMENT '接口token缓存秒数',
  `response_cache_seconds` INT        NOT NULL DEFAULT 0      COMMENT '接口响应缓存秒数，0表示不缓存',
  `timeout_ms`           INT          NOT NULL DEFAULT 3000   COMMENT '调用超时时间毫秒',
  `retry_count`          INT          NOT NULL DEFAULT 0      COMMENT '重试次数',
  `retry_interval_ms`    INT          NOT NULL DEFAULT 200    COMMENT '重试间隔毫秒',
  `exception_strategy`   VARCHAR(32)  NOT NULL DEFAULT 'FAIL_FAST' COMMENT '异常策略：FAIL_FAST/RETURN_DEFAULT/IGNORE/USE_CACHE',
  `fallback_value`       LONGTEXT     DEFAULT NULL            COMMENT '兜底返回值JSON',
  `async_callback_url`   VARCHAR(512) DEFAULT NULL            COMMENT '异步回调地址',
  `async_result_path`    VARCHAR(256) DEFAULT NULL            COMMENT '异步结果提取路径',
  `billing_item_code`    VARCHAR(128) DEFAULT NULL            COMMENT '计费项目编码',
  `billing_condition`    JSON         DEFAULT NULL            COMMENT '计费条件JSON，空表示正常计费',
  `unit_price`           DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '单次调用价格',
  `description`          VARCHAR(512) DEFAULT NULL            COMMENT '说明',
  `status`               TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0-停用，1-启用',
  `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ext_api_datasource_code` (`datasource_id`, `api_code`),
  KEY `idx_ext_api_datasource_id` (`datasource_id`),
  KEY `idx_ext_api_request_mode` (`request_mode`),
  KEY `idx_ext_api_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部API接口配置表';

-- ============================================================
-- 19.1 rule_runtime_call_log - 运行时调用诊断日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_runtime_call_log` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `module_type`     VARCHAR(32)  NOT NULL                COMMENT '模块类型：DATASOURCE/DATABASE/LIST/MODEL',
  `action_type`     VARCHAR(64)  NOT NULL                COMMENT '动作类型：API_INVOKE/AUTH_TEST/QUERY/EXECUTE等',
  `project_id`      BIGINT       DEFAULT NULL            COMMENT '项目ID',
  `project_code`    VARCHAR(128) DEFAULT NULL            COMMENT '项目编码',
  `target_ref_id`   BIGINT       DEFAULT NULL            COMMENT '目标配置ID',
  `target_code`     VARCHAR(128) DEFAULT NULL            COMMENT '目标编码',
  `target_name`     VARCHAR(128) DEFAULT NULL            COMMENT '目标名称',
  `success`         TINYINT      NOT NULL DEFAULT 1      COMMENT '是否成功：0-失败，1-成功',
  `request_method`  VARCHAR(16)  DEFAULT NULL            COMMENT '请求方法',
  `request_url`     VARCHAR(1024) DEFAULT NULL           COMMENT '请求地址',
  `request_headers` TEXT         DEFAULT NULL            COMMENT '请求头JSON（敏感值脱敏）',
  `request_params`  LONGTEXT     DEFAULT NULL            COMMENT '请求入参JSON',
  `request_body`    LONGTEXT     DEFAULT NULL            COMMENT '请求体JSON或文本',
  `response_status` INT          DEFAULT NULL            COMMENT '响应状态码',
  `response_body`   LONGTEXT     DEFAULT NULL            COMMENT '响应内容JSON或文本',
  `error_message`   VARCHAR(2048) DEFAULT NULL           COMMENT '错误信息',
  `cost_time_ms`    BIGINT       DEFAULT NULL            COMMENT '耗时毫秒',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_runtime_log_module_time` (`module_type`, `create_time`),
  KEY `idx_runtime_log_target_time` (`target_ref_id`, `create_time`),
  KEY `idx_runtime_log_success` (`success`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行时调用诊断日志表';

-- ============================================================
-- 19.2 rule_experiment - 分流实验定义表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_experiment` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`      BIGINT       DEFAULT NULL            COMMENT '所属项目ID',
  `project_code`    VARCHAR(128) DEFAULT NULL            COMMENT '所属项目编码',
  `experiment_code` VARCHAR(128) NOT NULL                COMMENT '实验编码',
  `experiment_name` VARCHAR(128) NOT NULL                COMMENT '实验名称',
  `description`     VARCHAR(512) DEFAULT NULL            COMMENT '说明',
  `routing_mode`    VARCHAR(32)  NOT NULL DEFAULT 'RATIO' COMMENT '冠军挑战分流方式：RATIO/CONDITION',
  `test_routing_mode` VARCHAR(32) NOT NULL DEFAULT 'CONDITION' COMMENT '测试组分流方式：RATIO/CONDITION',
  `condition_rule_code` VARCHAR(128) DEFAULT NULL        COMMENT '条件分流规则编码',
  `request_key_path` VARCHAR(128) NOT NULL DEFAULT 'requestId' COMMENT '请求唯一键路径',
  `test_exclusive`  TINYINT      NOT NULL DEFAULT 1      COMMENT '测试组是否互斥',
  `status`          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0-停用，1-启用',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_experiment_code` (`experiment_code`),
  KEY `idx_experiment_project` (`project_id`),
  KEY `idx_experiment_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分流实验定义表';

CREATE TABLE IF NOT EXISTS `rule_experiment_group` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `experiment_id`   BIGINT       NOT NULL                COMMENT '实验ID',
  `group_code`      VARCHAR(128) NOT NULL                COMMENT '组编码',
  `group_name`      VARCHAR(128) NOT NULL                COMMENT '组名称',
  `group_type`      VARCHAR(32)  NOT NULL                COMMENT '组类型：CHAMPION/CHALLENGER/TEST',
  `rule_code`       VARCHAR(128) NOT NULL                COMMENT '执行规则编码',
  `traffic_ratio`   DECIMAL(8,4) NOT NULL DEFAULT 0.0000 COMMENT '比例分流权重',
  `condition_value` VARCHAR(128) DEFAULT NULL            COMMENT '条件分流返回值',
  `condition_expression` VARCHAR(1024) DEFAULT NULL      COMMENT '条件分流命中表达式',
  `condition_config` TEXT DEFAULT NULL                   COMMENT '可视化条件配置JSON',
  `invoke_external_source` TINYINT NOT NULL DEFAULT 1    COMMENT '测试组是否调用API外数',
  `status`          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0-停用，1-启用',
  `sort_order`      INT          NOT NULL DEFAULT 0      COMMENT '排序',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_experiment_group_code` (`experiment_id`, `group_code`),
  KEY `idx_experiment_group_type` (`experiment_id`, `group_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分流实验组表';

CREATE TABLE IF NOT EXISTS `rule_experiment_execution_log` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `experiment_id`   BIGINT       NOT NULL                COMMENT '实验ID',
  `experiment_code` VARCHAR(128) NOT NULL                COMMENT '实验编码',
  `request_key`     VARCHAR(128) DEFAULT NULL            COMMENT '请求唯一键',
  `stage`           VARCHAR(32)  NOT NULL                COMMENT '阶段：PRODUCTION/TEST',
  `group_id`        BIGINT       DEFAULT NULL            COMMENT '实验组ID',
  `group_code`      VARCHAR(128) DEFAULT NULL            COMMENT '实验组编码',
  `group_name`      VARCHAR(128) DEFAULT NULL            COMMENT '实验组名称',
  `group_type`      VARCHAR(32)  DEFAULT NULL            COMMENT '实验组类型',
  `rule_code`       VARCHAR(128) DEFAULT NULL            COMMENT '执行规则编码',
  `route_reason`    VARCHAR(512) DEFAULT NULL            COMMENT '分流原因',
  `success`         TINYINT      NOT NULL DEFAULT 1      COMMENT '执行结果',
  `input_params`    TEXT         DEFAULT NULL            COMMENT '解析后入参',
  `output_result`   TEXT         DEFAULT NULL            COMMENT '执行结果',
  `trace_info`      LONGTEXT     DEFAULT NULL            COMMENT '执行轨迹',
  `error_message`   VARCHAR(1024) DEFAULT NULL           COMMENT '错误信息',
  `execute_time_ms` BIGINT       DEFAULT NULL            COMMENT '执行耗时',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  PRIMARY KEY (`id`),
  KEY `idx_exp_log_request` (`experiment_id`, `request_key`, `stage`),
  KEY `idx_exp_log_group` (`group_id`, `create_time`),
  KEY `idx_exp_log_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分流实验执行明细表';

-- ============================================================
-- 19.3 rule_experiment_version - 分流实验版本历史表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_experiment_version` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'primary id',
  `experiment_id`   BIGINT       NOT NULL                COMMENT 'experiment id',
  `version`         INT          NOT NULL                COMMENT 'version',
  `experiment_json` TEXT         NOT NULL                COMMENT 'experiment snapshot',
  `groups_json`     TEXT         NOT NULL                COMMENT 'group snapshot',
  `change_log`      VARCHAR(512) DEFAULT NULL            COMMENT 'change log',
  `publish_by`      VARCHAR(64)  DEFAULT NULL            COMMENT 'operator',
  `publish_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'snapshot time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_experiment_version` (`experiment_id`, `version`),
  KEY `idx_experiment_version_time` (`experiment_id`, `publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='experiment version history';

-- ============================================================
-- 20. rule_db_datasource - 外部数据库数据源定义表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_db_datasource` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`            BIGINT       NOT NULL DEFAULT 0      COMMENT '所属项目ID，0表示全局',
  `scope`                 VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL/PROJECT',
  `datasource_code`       VARCHAR(128) NOT NULL                COMMENT '数据库数据源编码',
  `datasource_name`       VARCHAR(128) NOT NULL                COMMENT '数据库数据源名称',
  `db_type`               VARCHAR(32)  NOT NULL DEFAULT 'MYSQL' COMMENT '数据库类型：MYSQL/POSTGRESQL/ORACLE/SQLSERVER/OTHER',
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
  `status`                TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0-停用，1-启用',
  `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_db_ds_scope_project_code` (`scope`, `project_id`, `datasource_code`),
  KEY `idx_db_ds_project_id` (`project_id`),
  KEY `idx_db_ds_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部数据库数据源定义表';

-- ============================================================
-- 21. rule_billing_config - 计费配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_billing_config` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`      BIGINT       NOT NULL DEFAULT 0      COMMENT '所属项目ID，0表示全局',
  `scope`           VARCHAR(16)  NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL/PROJECT',
  `billing_code`    VARCHAR(128) NOT NULL                COMMENT '计费项编码',
  `billing_name`    VARCHAR(128) NOT NULL                COMMENT '计费项名称',
  `billing_target`  VARCHAR(32)  NOT NULL DEFAULT 'ENGINE' COMMENT '计费对象：ENGINE/API/DB',
  `target_ref_id`   BIGINT       DEFAULT NULL            COMMENT '具体计费对象ID，空表示同类型全部',
  `charge_type`     VARCHAR(32)  NOT NULL DEFAULT 'COUNT' COMMENT '计费方式：COUNT/SUCCESS/DURATION/FIXED',
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

-- ============================================================
-- 22. rule_billing_record - 计费明细表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_billing_record` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`      BIGINT       DEFAULT NULL            COMMENT '项目ID',
  `project_code`    VARCHAR(128) DEFAULT NULL            COMMENT '项目编码',
  `billing_code`    VARCHAR(128) NOT NULL                COMMENT '计费项编码',
  `billing_name`    VARCHAR(128) DEFAULT NULL            COMMENT '计费项名称',
  `billing_target`  VARCHAR(32)  NOT NULL                COMMENT '计费对象：ENGINE/API/DB',
  `target_ref_id`   BIGINT       DEFAULT NULL            COMMENT '具体计费对象ID',
  `request_id`      VARCHAR(128) DEFAULT NULL            COMMENT '请求ID',
  `rule_code`       VARCHAR(128) DEFAULT NULL            COMMENT '规则编码',
  `api_code`        VARCHAR(128) DEFAULT NULL            COMMENT 'API编码',
  `datasource_code` VARCHAR(128) DEFAULT NULL            COMMENT '数据源编码',
  `success`         TINYINT      NOT NULL DEFAULT 1      COMMENT '是否成功：0-失败，1-成功',
  `quantity`        DECIMAL(18,6) NOT NULL DEFAULT 1.000000 COMMENT '计费数量',
  `unit_price`      DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '单价',
  `amount`          DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '金额',
  `currency`        VARCHAR(16)  NOT NULL DEFAULT 'CNY'  COMMENT '币种',
  `cost_time_ms`    BIGINT       DEFAULT NULL            COMMENT '耗时毫秒',
  `error_message`   VARCHAR(1024) DEFAULT NULL           COMMENT '错误信息',
  `occur_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_billing_record_occur` (`occur_time`),
  KEY `idx_billing_record_target` (`billing_target`, `target_ref_id`),
  KEY `idx_billing_record_project` (`project_code`, `occur_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计费明细表';

-- ============================================================
-- 23. rule_billing_summary - 计费汇总表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rule_billing_summary` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `summary_date`    DATE         NOT NULL                COMMENT '汇总日期',
  `project_id`      BIGINT       DEFAULT NULL            COMMENT '项目ID',
  `project_code`    VARCHAR(128) DEFAULT NULL            COMMENT '项目编码',
  `billing_code`    VARCHAR(128) NOT NULL                COMMENT '计费项编码',
  `billing_target`  VARCHAR(32)  NOT NULL                COMMENT '计费对象',
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
  UNIQUE KEY `uk_billing_summary_key` (`summary_date`, `project_code`, `billing_code`, `billing_target`, `target_ref_id`),
  KEY `idx_billing_summary_date` (`summary_date`),
  KEY `idx_billing_summary_target` (`billing_target`, `target_ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计费汇总表';
