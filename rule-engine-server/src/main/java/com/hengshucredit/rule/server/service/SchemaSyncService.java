package com.hengshucredit.rule.server.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Regenerates specific DDL blocks in schema.sql to keep them
 * in sync with the current table structure definitions.
 */
@Service
public class SchemaSyncService {

    private static final Logger log = LoggerFactory.getLogger(SchemaSyncService.class);

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    private static final Pattern TABLE_BLOCK_PATTERN = Pattern.compile(
            "(-- =+\\s*\\n-- \\d+\\. %s -[^\n]*\\n-- =+\\s*\\n)(CREATE TABLE IF NOT EXISTS `%s`[\\s\\S]*?;)",
            Pattern.MULTILINE);

    private static final List<String> REF_TYPE_TABLES = Arrays.asList(
            "rule_definition_input_field",
            "rule_definition_output_field",
            "rule_model_input_field",
            "rule_model_output_field"
    );

    @PostConstruct
    public void ensureRuntimeSchema() {
        if (jdbcTemplate == null) return;
        try {
            ensureRefTypeColumns();
            ensureVariableSourceConfigColumn();
            ensureListTables();
            ensureExperimentTables();
            ensureExperimentRuleReferenceSchema();
            ensureRuntimeCallLogTable();
            ensureTraceSchema();
            ensureProjectAuthSchema();
            ensureApiDocScenarioSchema();
            ensureExternalApiCacheColumns();
            ensureDbDatasourceConnectionColumns();
            ensureModelRuntimeColumns();
            ensureModelFieldForeignKeysRemoved();
            ensureDataObjectFieldUniqueKey();
        } catch (Exception e) {
            log.warn("运行时数据库结构同步失败，请检查 sql/schema.sql 与当前数据库: {}", e.getMessage());
        }
    }

    /**
     * Read the current schema.sql and return its content.
     */
    public String readSchema() throws IOException {
        ClassPathResource resource = new ClassPathResource("sql/schema.sql");
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    /**
     * Trigger a full schema sync — re-read and update all managed table blocks.
     * Currently this just verifies the file is readable; the actual DDL is maintained
     * by the schema.sql file directly, which was updated at migration time.
     */
    public String syncAndGetStatus() throws IOException {
        String content = readSchema();
        boolean hasDataObject = content.contains("rule_data_object");
        boolean hasObjectField = content.contains("rule_data_object_field");

        StringBuilder sb = new StringBuilder();
        sb.append("rule_data_object: ").append(hasDataObject ? "OK" : "MISSING").append("; ");
        sb.append("rule_data_object_field: ").append(hasObjectField ? "OK" : "MISSING");
        return sb.toString();
    }

    private void ensureRefTypeColumns() {
        for (String table : REF_TYPE_TABLES) {
            if (!tableExists(table)) continue;
            if (!columnExists(table, "ref_type")) {
                jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD COLUMN `ref_type` VARCHAR(32) DEFAULT NULL COMMENT '引用类型：VARIABLE/CONSTANT/DATA_OBJECT/MODEL' AFTER `var_id`");
            }
            if (!indexExists(table, "idx_ref_type_var_id")) {
                jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD INDEX `idx_ref_type_var_id` (`ref_type`, `var_id`)");
            }
        }
    }

    private void ensureVariableSourceConfigColumn() {
        String table = "rule_variable";
        if (!tableExists(table)) return;
        if (!columnExists(table, "source_config")) {
            jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD COLUMN `source_config` JSON DEFAULT NULL COMMENT '外部来源配置JSON：API/DB/LIST变量绑定接口、SQL、入参映射、结果路径等' AFTER `var_source`");
        }
    }

    private void ensureListTables() {
        if (!tableExists("rule_list_library")) {
            jdbcTemplate.execute("CREATE TABLE `rule_list_library` ("
                    + "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',"
                    + "`project_id` BIGINT NOT NULL DEFAULT 0 COMMENT '所属项目ID，0 表示全局',"
                    + "`scope` VARCHAR(16) NOT NULL DEFAULT 'PROJECT' COMMENT '作用范围：GLOBAL/PROJECT',"
                    + "`list_code` VARCHAR(128) NOT NULL COMMENT '名单库编码',"
                    + "`list_name` VARCHAR(128) NOT NULL COMMENT '名单库名称',"
                    + "`list_type` VARCHAR(32) NOT NULL DEFAULT 'BLACK' COMMENT '名单库类型：BLACK/GREY/WHITE/OTHER，仅用于标识',"
                    + "`description` VARCHAR(512) DEFAULT NULL COMMENT '说明',"
                    + "`status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',"
                    + "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                    + "`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                    + "PRIMARY KEY (`id`),"
                    + "UNIQUE KEY `uk_list_scope_project_code` (`scope`, `project_id`, `list_code`),"
                    + "KEY `idx_list_project_id` (`project_id`),"
                    + "KEY `idx_list_type_status` (`list_type`, `status`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单库配置表'");
        }
        ensureUtf8mb4Table("rule_list_library");
        if (!tableExists("rule_list_record")) {
            jdbcTemplate.execute("CREATE TABLE `rule_list_record` ("
                    + "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',"
                    + "`list_id` BIGINT NOT NULL COMMENT '名单库ID',"
                    + "`item_type` VARCHAR(32) NOT NULL COMMENT '名单内容类型',"
                    + "`item_content` VARCHAR(512) NOT NULL COMMENT '名单内容',"
                    + "`effective_time` DATETIME DEFAULT NULL COMMENT '生效时间',"
                    + "`expire_time` DATETIME DEFAULT NULL COMMENT '失效时间',"
                    + "`reason` VARCHAR(512) DEFAULT NULL COMMENT '插入原因',"
                    + "`remark` VARCHAR(512) DEFAULT NULL COMMENT '插入备注',"
                    + "`last_operation` VARCHAR(16) NOT NULL DEFAULT 'ADD' COMMENT '最近一次操作：ADD/UPDATE/DELETE',"
                    + "`status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',"
                    + "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入时间',"
                    + "`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                    + "PRIMARY KEY (`id`),"
                    + "UNIQUE KEY `uk_list_type_content` (`list_id`, `item_type`, `item_content`),"
                    + "KEY `idx_list_record_lookup` (`list_id`, `item_type`, `item_content`, `status`),"
                    + "KEY `idx_list_record_effective` (`effective_time`, `expire_time`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单当前记录表'");
        }
        ensureUtf8mb4Table("rule_list_record");
        if (!tableExists("rule_list_record_log")) {
            jdbcTemplate.execute("CREATE TABLE `rule_list_record_log` ("
                    + "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',"
                    + "`list_id` BIGINT NOT NULL COMMENT '名单库ID',"
                    + "`record_id` BIGINT DEFAULT NULL COMMENT '名单记录ID',"
                    + "`item_type` VARCHAR(32) NOT NULL COMMENT '名单内容类型',"
                    + "`item_content` VARCHAR(512) NOT NULL COMMENT '名单内容',"
                    + "`effective_time` DATETIME DEFAULT NULL COMMENT '生效时间',"
                    + "`expire_time` DATETIME DEFAULT NULL COMMENT '失效时间',"
                    + "`reason` VARCHAR(512) DEFAULT NULL COMMENT '插入原因',"
                    + "`remark` VARCHAR(512) DEFAULT NULL COMMENT '插入备注',"
                    + "`operation` VARCHAR(16) NOT NULL COMMENT '执行操作：ADD/UPDATE/DELETE',"
                    + "`operator` VARCHAR(64) DEFAULT NULL COMMENT '操作人',"
                    + "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',"
                    + "PRIMARY KEY (`id`),"
                    + "KEY `idx_list_log_list_record` (`list_id`, `record_id`),"
                    + "KEY `idx_list_log_content` (`list_id`, `item_type`, `item_content`),"
                    + "KEY `idx_list_log_time` (`create_time`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名单变更日志表'");
        }
        ensureUtf8mb4Table("rule_list_record_log");
    }

    private void ensureExperimentTables() {
        if (!tableExists("rule_experiment")) {
            jdbcTemplate.execute("CREATE TABLE `rule_experiment` ("
                    + "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',"
                    + "`project_id` BIGINT DEFAULT NULL COMMENT '所属项目ID',"
                    + "`project_code` VARCHAR(128) DEFAULT NULL COMMENT '所属项目编码',"
                    + "`experiment_code` VARCHAR(128) NOT NULL COMMENT '实验编码',"
                    + "`experiment_name` VARCHAR(128) NOT NULL COMMENT '实验名称',"
                    + "`description` VARCHAR(512) DEFAULT NULL COMMENT '说明',"
                    + "`routing_mode` VARCHAR(32) NOT NULL DEFAULT 'RATIO' COMMENT '分流方式',"
                    + "`test_routing_mode` VARCHAR(32) NOT NULL DEFAULT 'CONDITION' COMMENT '测试组分流方式',"
                    + "`condition_rule_code` VARCHAR(128) DEFAULT NULL COMMENT '条件分流规则编码',"
                    + "`request_key_path` VARCHAR(128) NOT NULL DEFAULT 'requestId' COMMENT '请求唯一键路径',"
                    + "`test_exclusive` TINYINT NOT NULL DEFAULT 1 COMMENT '测试组是否互斥',"
                    + "`status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',"
                    + "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                    + "`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                    + "PRIMARY KEY (`id`),"
                    + "UNIQUE KEY `uk_experiment_code` (`experiment_code`),"
                    + "KEY `idx_experiment_project` (`project_id`),"
                    + "KEY `idx_experiment_status` (`status`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分流实验定义表'");
        }
        ensureUtf8mb4Table("rule_experiment");
        addColumnIfMissing("rule_experiment", "test_routing_mode",
                "`test_routing_mode` VARCHAR(32) NOT NULL DEFAULT 'CONDITION' COMMENT '测试组分流方式' AFTER `routing_mode`");
        if (!tableExists("rule_experiment_group")) {
            jdbcTemplate.execute("CREATE TABLE `rule_experiment_group` ("
                    + "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',"
                    + "`experiment_id` BIGINT NOT NULL COMMENT '实验ID',"
                    + "`group_code` VARCHAR(128) NOT NULL COMMENT '组编码',"
                    + "`group_name` VARCHAR(128) NOT NULL COMMENT '组名称',"
                    + "`group_type` VARCHAR(32) NOT NULL COMMENT '组类型',"
                    + "`rule_code` VARCHAR(128) NOT NULL COMMENT '执行规则编码',"
                    + "`traffic_ratio` DECIMAL(8,4) NOT NULL DEFAULT 0.0000 COMMENT '比例分流权重',"
                    + "`condition_value` VARCHAR(128) DEFAULT NULL COMMENT '条件分流返回值',"
                    + "`condition_expression` VARCHAR(1024) DEFAULT NULL COMMENT '条件分流命中表达式',"
                    + "`condition_config` TEXT DEFAULT NULL COMMENT '可视化条件配置JSON',"
                    + "`invoke_external_source` TINYINT NOT NULL DEFAULT 1 COMMENT '测试组是否调用API外数',"
                    + "`status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',"
                    + "`sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',"
                    + "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                    + "`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                    + "PRIMARY KEY (`id`),"
                    + "UNIQUE KEY `uk_experiment_group_code` (`experiment_id`, `group_code`),"
                    + "KEY `idx_experiment_group_type` (`experiment_id`, `group_type`, `status`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分流实验组表'");
        }
        ensureUtf8mb4Table("rule_experiment_group");
        addColumnIfMissing("rule_experiment_group", "condition_config",
                "`condition_config` TEXT DEFAULT NULL COMMENT '可视化条件配置JSON' AFTER `condition_expression`");
        if (!tableExists("rule_experiment_execution_log")) {
            jdbcTemplate.execute("CREATE TABLE `rule_experiment_execution_log` ("
                    + "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',"
                    + "`experiment_id` BIGINT NOT NULL COMMENT '实验ID',"
                    + "`experiment_code` VARCHAR(128) NOT NULL COMMENT '实验编码',"
                    + "`request_key` VARCHAR(128) DEFAULT NULL COMMENT '请求唯一键',"
                    + "`stage` VARCHAR(32) NOT NULL COMMENT '阶段',"
                    + "`group_id` BIGINT DEFAULT NULL COMMENT '实验组ID',"
                    + "`group_code` VARCHAR(128) DEFAULT NULL COMMENT '实验组编码',"
                    + "`group_name` VARCHAR(128) DEFAULT NULL COMMENT '实验组名称',"
                    + "`group_type` VARCHAR(32) DEFAULT NULL COMMENT '实验组类型',"
                    + "`rule_code` VARCHAR(128) DEFAULT NULL COMMENT '执行规则编码',"
                    + "`route_reason` VARCHAR(512) DEFAULT NULL COMMENT '分流原因',"
                    + "`success` TINYINT NOT NULL DEFAULT 1 COMMENT '执行结果',"
                    + "`input_params` TEXT DEFAULT NULL COMMENT '解析后入参',"
                    + "`output_result` TEXT DEFAULT NULL COMMENT '执行结果',"
                    + "`trace_info` LONGTEXT DEFAULT NULL COMMENT '执行轨迹',"
                    + "`error_message` VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',"
                    + "`execute_time_ms` BIGINT DEFAULT NULL COMMENT '执行耗时',"
                    + "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',"
                    + "PRIMARY KEY (`id`),"
                    + "KEY `idx_exp_log_request` (`experiment_id`, `request_key`, `stage`),"
                    + "KEY `idx_exp_log_group` (`group_id`, `create_time`),"
                    + "KEY `idx_exp_log_create` (`create_time`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分流实验执行明细表'");
        }
        ensureUtf8mb4Table("rule_experiment_execution_log");
    }

    private void ensureRuntimeCallLogTable() {
        if (!tableExists("rule_runtime_call_log")) {
            jdbcTemplate.execute("CREATE TABLE `rule_runtime_call_log` ("
                    + "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',"
                    + "`module_type` VARCHAR(32) NOT NULL COMMENT '模块类型：DATASOURCE/DATABASE/LIST/MODEL',"
                    + "`action_type` VARCHAR(64) NOT NULL COMMENT '动作类型：API_INVOKE/AUTH_TEST/QUERY/EXECUTE等',"
                    + "`project_id` BIGINT DEFAULT NULL COMMENT '项目ID',"
                    + "`project_code` VARCHAR(128) DEFAULT NULL COMMENT '项目编码',"
                    + "`target_ref_id` BIGINT DEFAULT NULL COMMENT '目标配置ID',"
                    + "`target_code` VARCHAR(128) DEFAULT NULL COMMENT '目标编码',"
                    + "`target_name` VARCHAR(128) DEFAULT NULL COMMENT '目标名称',"
                    + "`success` TINYINT NOT NULL DEFAULT 1 COMMENT '是否成功',"
                    + "`request_method` VARCHAR(16) DEFAULT NULL COMMENT '请求方法',"
                    + "`request_url` VARCHAR(1024) DEFAULT NULL COMMENT '请求地址',"
                    + "`request_headers` TEXT DEFAULT NULL COMMENT '请求头JSON（敏感值脱敏）',"
                    + "`request_params` LONGTEXT DEFAULT NULL COMMENT '请求入参JSON',"
                    + "`request_body` LONGTEXT DEFAULT NULL COMMENT '请求体JSON或文本',"
                    + "`response_status` INT DEFAULT NULL COMMENT '响应状态码',"
                    + "`response_body` LONGTEXT DEFAULT NULL COMMENT '响应内容JSON或文本',"
                    + "`error_message` VARCHAR(2048) DEFAULT NULL COMMENT '错误信息',"
                    + "`cost_time_ms` BIGINT DEFAULT NULL COMMENT '耗时毫秒',"
                    + "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',"
                    + "PRIMARY KEY (`id`),"
                    + "KEY `idx_runtime_log_module_time` (`module_type`, `create_time`),"
                    + "KEY `idx_runtime_log_target_time` (`target_ref_id`, `create_time`),"
                    + "KEY `idx_runtime_log_success` (`success`, `create_time`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行时调用诊断日志表'");
        }
        addColumnIfMissing("rule_runtime_call_log", "request_success",
                "`request_success` TINYINT DEFAULT NULL COMMENT '按接口响应条件树判断的请求成功标记' AFTER `success`");
        addColumnIfMissing("rule_runtime_call_log", "found",
                "`found` TINYINT DEFAULT NULL COMMENT '按计费条件树判断的查得标记' AFTER `request_success`");
        addColumnIfMissing("rule_runtime_call_log", "provider_request",
                "`provider_request` TINYINT DEFAULT NULL COMMENT '是否实际向外部供应商发起请求' AFTER `found`");
        addColumnIfMissing("rule_runtime_call_log", "cache_status",
                "`cache_status` VARCHAR(32) DEFAULT NULL COMMENT '缓存状态' AFTER `provider_request`");
        addColumnIfMissing("rule_runtime_call_log", "cache_key",
                "`cache_key` VARCHAR(160) DEFAULT NULL COMMENT '脱敏后的缓存键摘要' AFTER `cache_status`");
        ensureUtf8mb4Table("rule_runtime_call_log");
    }

    private void ensureExperimentRuleReferenceSchema() {
        if (!tableExists("rule_experiment_group")) return;
        addColumnIfMissing("rule_experiment_group", "rule_id",
                "`rule_id` BIGINT DEFAULT NULL COMMENT '执行规则定义ID' AFTER `group_type`");
        if (!indexExists("rule_experiment_group", "idx_experiment_group_rule")) {
            jdbcTemplate.execute("ALTER TABLE `rule_experiment_group` ADD KEY `idx_experiment_group_rule` (`rule_id`)");
        }
    }

    private void ensureTraceSchema() {
        if (!tableExists("rule_trace_registry")) {
            jdbcTemplate.execute("CREATE TABLE `rule_trace_registry` ("
                    + "`trace_id` CHAR(36) NOT NULL,"
                    + "`trace_type` CHAR(2) NOT NULL,"
                    + "`scope_type` CHAR(1) NOT NULL,"
                    + "`scope_code` CHAR(4) NOT NULL,"
                    + "`project_id` BIGINT DEFAULT NULL,"
                    + "`resource_type` VARCHAR(32) NOT NULL,"
                    + "`resource_id` BIGINT DEFAULT NULL,"
                    + "`resource_code` VARCHAR(128) DEFAULT NULL,"
                    + "`parent_trace_id` CHAR(36) DEFAULT NULL,"
                    + "`create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),"
                    + "PRIMARY KEY (`trace_id`),"
                    + "KEY `idx_trace_parent` (`parent_trace_id`),"
                    + "KEY `idx_trace_resource` (`resource_type`, `resource_id`, `create_time`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局Trace编号注册表'");
        }
        if (tableExists("rule_project")) {
            addColumnIfMissing("rule_project", "trace_scope_code",
                    "`trace_scope_code` CHAR(4) DEFAULT NULL COMMENT 'Trace项目作用域码' AFTER `project_code`");
            jdbcTemplate.execute("UPDATE `rule_project` SET `trace_scope_code` = LPAD(UPPER(CONV(`id`, 10, 36)), 4, '0') "
                    + "WHERE `trace_scope_code` IS NULL AND `id` BETWEEN 1 AND 1679615");
            if (!indexExists("rule_project", "uk_project_trace_scope")) {
                jdbcTemplate.execute("ALTER TABLE `rule_project` ADD UNIQUE KEY `uk_project_trace_scope` (`trace_scope_code`)");
            }
        }
        addTraceColumns("rule_execution_log", false);
        addTraceColumns("rule_runtime_call_log", true);
        if (tableExists("rule_experiment_execution_log")) {
            addColumnIfMissing("rule_experiment_execution_log", "experiment_trace_id",
                    "`experiment_trace_id` CHAR(36) DEFAULT NULL COMMENT '本次分流实验Trace ID' AFTER `experiment_code`");
            addColumnIfMissing("rule_experiment_execution_log", "child_trace_id",
                    "`child_trace_id` CHAR(36) DEFAULT NULL COMMENT '实际执行组规则Trace ID' AFTER `experiment_trace_id`");
            if (!indexExists("rule_experiment_execution_log", "idx_exp_trace")) {
                jdbcTemplate.execute("ALTER TABLE `rule_experiment_execution_log` ADD INDEX `idx_exp_trace` (`experiment_trace_id`, `child_trace_id`)");
            }
        }
    }

    private void addTraceColumns(String table, boolean runtimeCall) {
        if (!tableExists(table)) return;
        addColumnIfMissing(table, "trace_id",
                "`trace_id` CHAR(36) DEFAULT NULL COMMENT '全局唯一Trace ID' AFTER `id`");
        if (runtimeCall) {
            addColumnIfMissing(table, "rule_trace_id",
                    "`rule_trace_id` CHAR(36) DEFAULT NULL COMMENT '发起调用的规则Trace ID' AFTER `trace_id`");
        }
        String indexName = runtimeCall ? "idx_runtime_trace" : "idx_execution_trace";
        if (!indexExists(table, indexName)) {
            String columns = runtimeCall ? "`trace_id`, `rule_trace_id`" : "`trace_id`, `create_time`";
            jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD INDEX `" + indexName + "` (" + columns + ")");
        }
    }

    private void ensureProjectAuthSchema() {
        if (!tableExists("rule_project_auth")) {
            jdbcTemplate.execute("CREATE TABLE `rule_project_auth` ("
                    + "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',"
                    + "`project_id` BIGINT NOT NULL COMMENT '所属项目ID',"
                    + "`auth_code` VARCHAR(128) NOT NULL COMMENT '鉴权配置编码',"
                    + "`auth_name` VARCHAR(128) NOT NULL COMMENT '鉴权配置名称',"
                    + "`auth_type` VARCHAR(32) NOT NULL COMMENT '鉴权类型',"
                    + "`lookup_key` CHAR(64) NOT NULL COMMENT '凭据定位摘要',"
                    + "`identifier_ciphertext` TEXT DEFAULT NULL COMMENT '凭据标识密文',"
                    + "`secret_ciphertext` TEXT NOT NULL COMMENT '凭据密文',"
                    + "`config_json` JSON DEFAULT NULL COMMENT '非敏感鉴权配置',"
                    + "`token_ttl_seconds` INT NOT NULL DEFAULT 7200 COMMENT '临时Token有效秒数',"
                    + "`token_grace_seconds` INT NOT NULL DEFAULT 600 COMMENT '临时Token宽限秒数',"
                    + "`status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',"
                    + "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                    + "`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                    + "PRIMARY KEY (`id`),"
                    + "UNIQUE KEY `uk_project_auth_code` (`auth_code`),"
                    + "UNIQUE KEY `uk_project_auth_lookup` (`lookup_key`),"
                    + "KEY `idx_project_auth_project` (`project_id`, `status`),"
                    + "KEY `idx_project_auth_type` (`auth_type`, `status`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目鉴权配置表'");
        }
        if (!tableExists("rule_project_auth_token")) {
            jdbcTemplate.execute("CREATE TABLE `rule_project_auth_token` ("
                    + "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',"
                    + "`project_id` BIGINT NOT NULL COMMENT '所属项目ID',"
                    + "`auth_id` BIGINT NOT NULL COMMENT '来源鉴权配置ID',"
                    + "`token_code` VARCHAR(128) NOT NULL COMMENT 'Token展示编码',"
                    + "`lookup_key` CHAR(64) NOT NULL COMMENT 'Token定位摘要',"
                    + "`token_ciphertext` TEXT NOT NULL COMMENT 'Token密文',"
                    + "`issued_time` DATETIME NOT NULL COMMENT '签发时间',"
                    + "`expire_time` DATETIME NOT NULL COMMENT '正常到期时间',"
                    + "`grace_expire_time` DATETIME NOT NULL COMMENT '宽限截止时间',"
                    + "`last_used_time` DATETIME DEFAULT NULL COMMENT '最后使用时间',"
                    + "`revoked_time` DATETIME DEFAULT NULL COMMENT '撤销时间',"
                    + "`status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-撤销，1-有效',"
                    + "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                    + "`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
                    + "PRIMARY KEY (`id`),"
                    + "UNIQUE KEY `uk_project_auth_token_code` (`token_code`),"
                    + "UNIQUE KEY `uk_project_auth_token_lookup` (`lookup_key`),"
                    + "KEY `idx_project_auth_token_auth` (`auth_id`, `status`),"
                    + "KEY `idx_project_auth_token_project` (`project_id`, `status`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目临时访问Token表'");
        }
        if (!tableExists("rule_auth_access_log")) {
            jdbcTemplate.execute("CREATE TABLE `rule_auth_access_log` ("
                    + "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',"
                    + "`project_id` BIGINT DEFAULT NULL COMMENT '项目ID',"
                    + "`project_code` VARCHAR(128) DEFAULT NULL COMMENT '项目编码快照',"
                    + "`auth_id` BIGINT DEFAULT NULL COMMENT '鉴权配置ID',"
                    + "`auth_code` VARCHAR(128) DEFAULT NULL COMMENT '鉴权配置编码快照',"
                    + "`auth_type` VARCHAR(32) DEFAULT NULL COMMENT '鉴权类型快照',"
                    + "`token_id` BIGINT DEFAULT NULL COMMENT '临时Token ID',"
                    + "`token_code` VARCHAR(128) DEFAULT NULL COMMENT '临时Token编码快照',"
                    + "`auth_phase` VARCHAR(16) DEFAULT NULL COMMENT '鉴权阶段：DIRECT/VALID/GRACE',"
                    + "`request_method` VARCHAR(16) DEFAULT NULL COMMENT '请求方法',"
                    + "`request_uri` VARCHAR(1024) DEFAULT NULL COMMENT '请求路径',"
                    + "`request_id` VARCHAR(128) DEFAULT NULL COMMENT '请求ID',"
                    + "`client_ip` VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',"
                    + "`success` TINYINT NOT NULL DEFAULT 1 COMMENT '是否成功',"
                    + "`failure_reason` VARCHAR(512) DEFAULT NULL COMMENT '失败原因',"
                    + "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',"
                    + "PRIMARY KEY (`id`),"
                    + "KEY `idx_auth_access_project_time` (`project_id`, `create_time`),"
                    + "KEY `idx_auth_access_auth_time` (`auth_id`, `create_time`),"
                    + "KEY `idx_auth_access_token_time` (`token_id`, `create_time`),"
                    + "KEY `idx_auth_access_success_time` (`success`, `create_time`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目鉴权访问日志表'");
        }

        addAuthAttributionColumns("rule_execution_log", "create_time", true);
        addAuthAttributionColumns("rule_billing_record", "occur_time", true);
        addAuthAttributionColumns("rule_billing_summary", "summary_date", false);
        ensureBillingSummaryAuthUniqueKey();
    }

    private void ensureApiDocScenarioSchema() {
        if (tableExists("rule_api_doc_scenario")) return;
        jdbcTemplate.execute("CREATE TABLE `rule_api_doc_scenario` ("
                + "`id` BIGINT NOT NULL AUTO_INCREMENT,"
                + "`definition_id` BIGINT NOT NULL,"
                + "`scenario_name` VARCHAR(128) NOT NULL,"
                + "`description` VARCHAR(512) DEFAULT NULL,"
                + "`request_json` JSON NOT NULL,"
                + "`response_json` JSON NOT NULL,"
                + "`response_source` VARCHAR(16) NOT NULL DEFAULT 'MANUAL',"
                + "`outer_code` INT DEFAULT NULL,"
                + "`business_code_path` VARCHAR(256) DEFAULT NULL,"
                + "`business_code` VARCHAR(256) DEFAULT NULL,"
                + "`rule_version` INT NOT NULL,"
                + "`include_in_doc` TINYINT NOT NULL DEFAULT 0,"
                + "`sort_order` INT NOT NULL DEFAULT 0,"
                + "`status` TINYINT NOT NULL DEFAULT 1,"
                + "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (`id`),"
                + "UNIQUE KEY `uk_api_doc_scenario_name` (`definition_id`, `scenario_name`),"
                + "KEY `idx_api_doc_scenario_export` (`definition_id`, `status`, `include_in_doc`, `sort_order`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Rule API documentation scenarios'");
    }

    private void addAuthAttributionColumns(String table, String timeColumn, boolean includeToken) {
        if (!tableExists(table)) return;
        addColumnIfMissing(table, "auth_id", "`auth_id` BIGINT DEFAULT NULL COMMENT '鉴权配置ID'");
        addColumnIfMissing(table, "auth_code", "`auth_code` VARCHAR(128) DEFAULT NULL COMMENT '鉴权配置编码快照'");
        addColumnIfMissing(table, "auth_type", "`auth_type` VARCHAR(32) DEFAULT NULL COMMENT '鉴权类型快照'");
        if (includeToken) {
            addColumnIfMissing(table, "token_id", "`token_id` BIGINT DEFAULT NULL COMMENT '临时Token ID'");
            addColumnIfMissing(table, "token_code", "`token_code` VARCHAR(128) DEFAULT NULL COMMENT '临时Token编码快照'");
            addColumnIfMissing(table, "auth_phase", "`auth_phase` VARCHAR(16) DEFAULT NULL COMMENT '鉴权阶段'");
        }
        String indexName = "idx_" + table.replace("rule_", "") + "_auth";
        if (!indexExists(table, indexName)) {
            String columns = includeToken
                    ? "`auth_id`, `token_id`, `" + timeColumn + "`"
                    : "`auth_id`, `" + timeColumn + "`";
            jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD INDEX `" + indexName + "` (" + columns + ")");
        }
    }

    private void ensureBillingSummaryAuthUniqueKey() {
        String table = "rule_billing_summary";
        String index = "uk_billing_summary_key";
        if (!tableExists(table)) return;
        List<String> expected = Arrays.asList("summary_date", "project_code", "billing_code",
                "billing_target", "target_ref_id", "auth_id");
        boolean create = !indexExists(table, index);
        if (!create && !sameColumns(indexColumns(table, index), expected)) {
            jdbcTemplate.execute("ALTER TABLE `" + table + "` DROP INDEX `" + index + "`");
            create = true;
        }
        if (create) {
            jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD UNIQUE KEY `" + index
                    + "` (`summary_date`, `project_code`, `billing_code`, `billing_target`, `target_ref_id`, `auth_id`)");
        }
    }

    private void ensureDbDatasourceConnectionColumns() {
        String table = "rule_db_datasource";
        if (!tableExists(table)) return;
        addColumnIfMissing(table, "connection_mode",
                "`connection_mode` VARCHAR(32) NOT NULL DEFAULT 'DIRECT' COMMENT '连接方式：DIRECT-直连，SSH_TUNNEL-SSH隧道' AFTER `db_type`");
        addColumnIfMissing(table, "host",
                "`host` VARCHAR(256) DEFAULT NULL COMMENT '数据库主机（用于表单生成JDBC URL和SSH远端转发）' AFTER `connection_mode`");
        addColumnIfMissing(table, "port",
                "`port` INT DEFAULT NULL COMMENT '数据库端口' AFTER `host`");
        addColumnIfMissing(table, "database_name",
                "`database_name` VARCHAR(128) DEFAULT NULL COMMENT '数据库名/服务名' AFTER `port`");
        addColumnIfMissing(table, "jdbc_params",
                "`jdbc_params` VARCHAR(1024) DEFAULT NULL COMMENT 'JDBC扩展参数，不含前导问号' AFTER `database_name`");
        addColumnIfMissing(table, "ssh_host",
                "`ssh_host` VARCHAR(256) DEFAULT NULL COMMENT 'SSH堡垒机主机' AFTER `password`");
        addColumnIfMissing(table, "ssh_port",
                "`ssh_port` INT DEFAULT NULL COMMENT 'SSH堡垒机端口' AFTER `ssh_host`");
        addColumnIfMissing(table, "ssh_username",
                "`ssh_username` VARCHAR(128) DEFAULT NULL COMMENT 'SSH用户名' AFTER `ssh_port`");
        addColumnIfMissing(table, "ssh_password",
                "`ssh_password` VARCHAR(512) DEFAULT NULL COMMENT 'SSH密码' AFTER `ssh_username`");
        addColumnIfMissing(table, "ssh_private_key",
                "`ssh_private_key` TEXT DEFAULT NULL COMMENT 'SSH私钥内容' AFTER `ssh_password`");
        addColumnIfMissing(table, "ssh_passphrase",
                "`ssh_passphrase` VARCHAR(512) DEFAULT NULL COMMENT 'SSH私钥口令' AFTER `ssh_private_key`");
        addColumnIfMissing(table, "ssh_timeout_ms",
                "`ssh_timeout_ms` INT NOT NULL DEFAULT 10000 COMMENT 'SSH连接超时时间毫秒' AFTER `ssh_passphrase`");
    }

    private void ensureExternalApiCacheColumns() {
        String table = "rule_external_api_config";
        if (!tableExists(table)) return;
        if (columnExists(table, "content_type")) {
            jdbcTemplate.execute("ALTER TABLE `" + table + "` MODIFY COLUMN `content_type` VARCHAR(128) DEFAULT NULL COMMENT '请求Content-Type，空表示不主动设置'");
        }
        addColumnIfMissing(table, "response_cache_seconds",
                "`response_cache_seconds` INT NOT NULL DEFAULT 0 COMMENT '接口响应缓存秒数，0表示不缓存' AFTER `token_cache_seconds`");
        addColumnIfMissing(table, "cache_key_config",
                "`cache_key_config` JSON DEFAULT NULL COMMENT '缓存键组件配置JSON，组件按顺序且必须全部有值' AFTER `response_cache_seconds`");
        addColumnIfMissing(table, "success_condition",
                "`success_condition` JSON DEFAULT NULL COMMENT '请求成功响应条件树JSON' AFTER `cache_key_config`");
        addColumnIfMissing(table, "request_script",
                "`request_script` LONGTEXT DEFAULT NULL COMMENT '请求发送前QLExpress处理脚本' AFTER `body_template`");
        addColumnIfMissing(table, "response_script",
                "`response_script` LONGTEXT DEFAULT NULL COMMENT '响应映射前QLExpress处理脚本' AFTER `response_mapping`");
        addColumnIfMissing(table, "billing_condition",
                "`billing_condition` JSON DEFAULT NULL COMMENT '计费条件JSON，空表示正常计费' AFTER `billing_item_code`");
        addColumnIfMissing(table, "async_result_mode",
                "`async_result_mode` VARCHAR(32) DEFAULT NULL COMMENT '异步结果获取方式：POLL/CALLBACK' AFTER `fallback_value`");
        addColumnIfMissing(table, "async_poll_config",
                "`async_poll_config` JSON DEFAULT NULL COMMENT '异步轮询配置JSON' AFTER `async_result_mode`");
        addColumnIfMissing(table, "async_callback_config",
                "`async_callback_config` JSON DEFAULT NULL COMMENT '异步回调配置JSON' AFTER `async_poll_config`");
        addColumnIfMissing(table, "test_sample_params",
                "`test_sample_params` LONGTEXT DEFAULT NULL COMMENT 'API调用测试样例JSON' AFTER `description`");
    }

    private void ensureModelFieldForeignKeysRemoved() {
        dropForeignKeyIfExists("rule_model_input_field", "fk_input_var");
        dropForeignKeyIfExists("rule_model_output_field", "fk_output_var");
    }

    private void ensureModelRuntimeColumns() {
        String table = "rule_model";
        if (!tableExists(table)) return;
        addColumnIfMissing(table, "preload_on_startup",
                "`preload_on_startup` TINYINT NOT NULL DEFAULT 0 COMMENT '服务启动时预加载：0-否，1-是' AFTER `model_config`");
        addColumnIfMissing(table, "execution_timeout_ms",
                "`execution_timeout_ms` INT NOT NULL DEFAULT 120000 COMMENT '单次模型执行超时时间（毫秒）' AFTER `preload_on_startup`");
    }

    private void ensureDataObjectFieldUniqueKey() {
        String table = "rule_data_object_field";
        if (!tableExists(table)) return;
        List<String> expected = Arrays.asList("object_id", "parent_field_id", "var_code");
        if (indexExists(table, "uk_object_var_code")) {
            List<String> actual = indexColumns(table, "uk_object_var_code");
            if (!sameColumns(actual, expected)) {
                jdbcTemplate.execute("ALTER TABLE `" + table + "` DROP INDEX `uk_object_var_code`");
            }
        }
        if (!indexExists(table, "uk_object_var_code")) {
            jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD UNIQUE KEY `uk_object_var_code` (`object_id`, `parent_field_id`, `var_code`)");
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                new Object[]{tableName},
                Integer.class);
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                new Object[]{tableName, columnName},
                Integer.class);
        return count != null && count > 0;
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        if (!columnExists(tableName, columnName)) {
            jdbcTemplate.execute("ALTER TABLE `" + tableName + "` ADD COLUMN " + definition);
        }
    }

    private void ensureUtf8mb4Table(String tableName) {
        if (!tableExists(tableName)) return;
        jdbcTemplate.execute("ALTER TABLE `" + tableName + "` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                new Object[]{tableName, indexName},
                Integer.class);
        return count != null && count > 0;
    }

    private List<String> indexColumns(String tableName, String indexName) {
        if (!indexExists(tableName, indexName)) return Collections.emptyList();
        return jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ? ORDER BY SEQ_IN_INDEX",
                new Object[]{tableName, indexName},
                String.class);
    }

    private boolean sameColumns(List<String> actual, List<String> expected) {
        if (actual == null || actual.size() != expected.size()) return false;
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).equalsIgnoreCase(actual.get(i))) return false;
        }
        return true;
    }

    private void dropForeignKeyIfExists(String tableName, String constraintName) {
        if (!tableExists(tableName)) return;
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ? AND CONSTRAINT_TYPE = 'FOREIGN KEY'",
                new Object[]{tableName, constraintName},
                Integer.class);
        if (count != null && count > 0) {
            jdbcTemplate.execute("ALTER TABLE `" + tableName + "` DROP FOREIGN KEY `" + constraintName + "`");
        }
    }

}
