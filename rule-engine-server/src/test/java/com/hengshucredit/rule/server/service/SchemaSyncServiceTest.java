package com.hengshucredit.rule.server.service;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SchemaSyncServiceTest {

    @Test
    public void schemaResourceHasNoFilesystemOverrideOrWritebackEntryPoint() {
        assertFalse(hasDeclaredField(SchemaSyncService.class, "schemaPath"));
        assertFalse(hasDeclaredMethod(SchemaSyncService.class, "resolveSchemaPath"));
        assertFalse(hasDeclaredMethod(SchemaSyncService.class, "updateTableBlock"));
    }

    @Test
    public void listTablesAreConvertedToUtf8mb4() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate();
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod("ensureUtf8mb4Table", String.class);
        method.setAccessible(true);
        method.invoke(service, "rule_list_record");

        assertEquals(1, jdbcTemplate.sqlList.size());
        assertEquals("ALTER TABLE `rule_list_record` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci",
                jdbcTemplate.sqlList.get(0));
    }

    @Test
    public void listChangeBatchSchemaCreatesBothStagingTablesWhenMissing()
            throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate();
        jdbcTemplate.missingTables.addAll(Arrays.asList(
                "rule_list_change_batch", "rule_list_change_item"));
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod(
                "ensureListChangeBatchSchema");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList,
                "CREATE TABLE `rule_list_change_batch`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "CREATE TABLE `rule_list_change_item`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "KEY `idx_list_change_batch_approval` (`approval_request_id`)"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "UNIQUE KEY `uk_list_change_item_row` (`batch_id`, `source_row`)"));
    }

    @Test
    public void listChangeItemSchemaRenamesReservedRowNumberColumn()
            throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate("source_row");
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod(
                "ensureListChangeBatchSchema");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList,
                "CHANGE COLUMN `row_number` `source_row` INT NOT NULL"));
    }

    @Test
    public void externalApiScriptAndAsyncColumnsAreAddedWhenMissing() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate("request_script", "response_script",
                "async_result_mode", "async_poll_config", "async_callback_config", "test_sample_params");
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod("ensureExternalApiCacheColumns");
        method.setAccessible(true);
        method.invoke(service);

        assertEquals(7, jdbcTemplate.sqlList.size());
        assertEquals("ALTER TABLE `rule_external_api_config` MODIFY COLUMN `content_type` VARCHAR(128) DEFAULT NULL COMMENT '请求Content-Type，空表示不主动设置'",
                jdbcTemplate.sqlList.get(0));
        assertEquals("ALTER TABLE `rule_external_api_config` ADD COLUMN `request_script` LONGTEXT DEFAULT NULL COMMENT '请求发送前QLExpress处理脚本' AFTER `body_template`",
                jdbcTemplate.sqlList.get(1));
        assertEquals("ALTER TABLE `rule_external_api_config` ADD COLUMN `response_script` LONGTEXT DEFAULT NULL COMMENT '响应映射前QLExpress处理脚本' AFTER `response_mapping`",
                jdbcTemplate.sqlList.get(2));
        assertEquals("ALTER TABLE `rule_external_api_config` ADD COLUMN `async_result_mode` VARCHAR(32) DEFAULT NULL COMMENT '异步结果获取方式：POLL/CALLBACK' AFTER `fallback_value`",
                jdbcTemplate.sqlList.get(3));
        assertEquals("ALTER TABLE `rule_external_api_config` ADD COLUMN `async_poll_config` JSON DEFAULT NULL COMMENT '异步轮询配置JSON' AFTER `async_result_mode`",
                jdbcTemplate.sqlList.get(4));
        assertEquals("ALTER TABLE `rule_external_api_config` ADD COLUMN `async_callback_config` JSON DEFAULT NULL COMMENT '异步回调配置JSON' AFTER `async_poll_config`",
                jdbcTemplate.sqlList.get(5));
        assertEquals("ALTER TABLE `rule_external_api_config` ADD COLUMN `test_sample_params` LONGTEXT DEFAULT NULL COMMENT 'API调用测试样例JSON' AFTER `description`",
                jdbcTemplate.sqlList.get(6));
    }

    @Test
    public void externalApiBusinessRetryConditionColumnIsAddedWhenMissing() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate("retry_condition");
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod("ensureExternalApiCacheColumns");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ADD COLUMN `retry_condition` JSON DEFAULT NULL COMMENT '业务响应重试条件树JSON' AFTER `retry_on_timeout`"));
    }

    @Test
    public void projectAuthSchemaCreatesTablesAndAttributionColumnsWhenMissing() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate(
                "auth_id", "auth_code", "auth_type", "token_id", "token_code", "auth_phase");
        jdbcTemplate.missingTables.addAll(Arrays.asList(
                "rule_project_auth", "rule_project_auth_token", "rule_auth_access_log"));
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod("ensureProjectAuthSchema");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList, "CREATE TABLE `rule_project_auth`"));
        assertTrue(containsSql(jdbcTemplate.sqlList, "CREATE TABLE `rule_project_auth_token`"));
        assertTrue(containsSql(jdbcTemplate.sqlList, "CREATE TABLE `rule_auth_access_log`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ALTER TABLE `rule_execution_log` ADD COLUMN `auth_id`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ALTER TABLE `rule_billing_record` ADD COLUMN `token_code`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ALTER TABLE `rule_billing_summary` ADD COLUMN `auth_code`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ALTER TABLE `rule_billing_summary` DROP INDEX `uk_billing_summary_key`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ADD UNIQUE KEY `uk_billing_summary_key` (`summary_date`, `project_code`, `billing_code`, `billing_target`, `target_ref_id`, `auth_id`)"));
    }

    @Test
    public void billingRecordDedupSchemaAddsColumnAndUniqueConstraintWhenMissing() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate("billing_dedup_key");
        jdbcTemplate.missingIndexes.add("uk_billing_record_dedup_key");
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod("ensureProjectAuthSchema");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ALTER TABLE `rule_billing_record` ADD COLUMN `billing_dedup_key`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ALTER TABLE `rule_billing_record` ADD UNIQUE KEY `uk_billing_record_dedup_key` (`billing_dedup_key`)"));
    }

    @Test
    public void consoleRbacSchemaCreatesEveryAuthorizationTableWhenMissing() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate();
        jdbcTemplate.missingTables.addAll(Arrays.asList(
                "console_user",
                "console_role",
                "console_permission",
                "console_user_role",
                "console_role_permission",
                "console_user_permission_override",
                "console_security_audit_log"));
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod("ensureConsoleRbacSchema");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList, "CREATE TABLE `console_user`"));
        assertTrue(containsSql(jdbcTemplate.sqlList, "CREATE TABLE `console_role`"));
        assertTrue(containsSql(jdbcTemplate.sqlList, "CREATE TABLE `console_permission`"));
        assertTrue(containsSql(jdbcTemplate.sqlList, "CREATE TABLE `console_user_role`"));
        assertTrue(containsSql(jdbcTemplate.sqlList, "CREATE TABLE `console_role_permission`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "CREATE TABLE `console_user_permission_override`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "CREATE TABLE `console_security_audit_log`"));
    }

    @Test
    public void governanceSchemaCreatesEveryLifecycleTableWhenMissing() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate();
        jdbcTemplate.missingTables.addAll(Arrays.asList(
                "governed_resource",
                "governed_resource_version",
                "governance_approval_request",
                "governance_approval_event",
                "governance_dependency_snapshot"));
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod(
                "ensureGovernanceSchema");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList,
                "CREATE TABLE `governed_resource`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "CREATE TABLE `governed_resource_version`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "CREATE TABLE `governance_approval_request`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "CREATE TABLE `governance_approval_event`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "CREATE TABLE `governance_dependency_snapshot`"));
    }

    @Test
    public void apiDocScenarioSchemaCreatesTableWhenMissing() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate();
        jdbcTemplate.missingTables.add("rule_api_doc_scenario");
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod("ensureApiDocScenarioSchema");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList, "CREATE TABLE `rule_api_doc_scenario`"));
        assertTrue(containsSql(jdbcTemplate.sqlList, "`request_json` LONGTEXT NOT NULL"));
        assertTrue(containsSql(jdbcTemplate.sqlList, "`response_json` LONGTEXT NOT NULL"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "KEY `idx_api_doc_scenario_export` (`definition_id`, `status`, `include_in_doc`, `sort_order`)"));
    }

    @Test
    public void fieldValidationSchemaCreatesRuleLibraryAndInputAssignments() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate(
                "validation_rule_ids", "validation_override");
        jdbcTemplate.missingTables.add("rule_field_validation");
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod("ensureFieldValidationSchema");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList, "CREATE TABLE `rule_field_validation`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ADD COLUMN `validation_rule_ids` JSON DEFAULT NULL"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ADD COLUMN `validation_override` TINYINT NOT NULL DEFAULT 0"));
    }

    @Test
    public void apiDocScenarioSchemaUpgradesJsonPayloadColumns() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate();
        jdbcTemplate.nonLongTextColumns.add("request_json");
        jdbcTemplate.nonLongTextColumns.add("response_json");
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod("ensureApiDocScenarioSchema");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList,
                "MODIFY COLUMN `request_json` LONGTEXT NOT NULL"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "MODIFY COLUMN `response_json` LONGTEXT NOT NULL"));
    }

    @Test
    public void compiledScriptColumnsAreUpgradedForLargeDecisionArtifacts() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate();
        jdbcTemplate.nonLongTextColumns.add("rule_definition_content.compiled_script");
        jdbcTemplate.nonLongTextColumns.add("rule_definition_version.compiled_script");
        jdbcTemplate.nonLongTextColumns.add("rule_published.compiled_script");
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod(
                "ensureCompiledScriptCapacity");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ALTER TABLE `rule_definition_content` MODIFY COLUMN `compiled_script` LONGTEXT DEFAULT NULL"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ALTER TABLE `rule_definition_version` MODIFY COLUMN `compiled_script` LONGTEXT DEFAULT NULL"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ALTER TABLE `rule_published` MODIFY COLUMN `compiled_script` LONGTEXT NOT NULL"));

        String schema = service.readSchema();
        assertTrue(schema.contains("`compiled_script` LONGTEXT DEFAULT NULL             COMMENT '编译后脚本'"));
        assertTrue(schema.contains("`compiled_script` LONGTEXT     DEFAULT NULL             COMMENT '版本快照 - 编译后脚本'"));
        assertTrue(schema.contains("`compiled_script` LONGTEXT     NOT NULL                COMMENT '编译后脚本'"));
    }

    @Test
    public void publishedRulesRepairStaleGovernanceEffectiveStatus() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate();
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod(
                "ensureRuleGovernanceEffectiveStatus");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList,
                "UPDATE `governed_resource` gr JOIN `rule_definition` rd"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "SET gr.`effective_status` = 'ACTIVE'"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "rd.`status` = 1"));
    }

    @Test
    public void traceSchemaCreatesRegistryAndAddsLinkColumns() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate(
                "trace_scope_code", "trace_id", "rule_trace_id", "experiment_trace_id", "child_trace_id");
        jdbcTemplate.missingTables.add("rule_trace_registry");
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod("ensureTraceSchema");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList, "CREATE TABLE `rule_trace_registry`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ALTER TABLE `rule_project` ADD COLUMN `trace_scope_code`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ALTER TABLE `rule_execution_log` ADD COLUMN `trace_id`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ALTER TABLE `rule_runtime_call_log` ADD COLUMN `rule_trace_id`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ALTER TABLE `rule_experiment_execution_log` ADD COLUMN `experiment_trace_id`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ALTER TABLE `rule_experiment_execution_log` ADD COLUMN `child_trace_id`"));
    }

    @Test
    public void traceSchemaNormalizesExistingRegistryCollation() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate();
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod("ensureTraceSchema");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ALTER TABLE `rule_trace_registry` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"));
    }

    @Test
    public void experimentGroupsAddStableRuleReferenceWhenMissing() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate("rule_id");
        jdbcTemplate.missingIndexes.add("idx_experiment_group_rule");
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod("ensureExperimentRuleReferenceSchema");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ALTER TABLE `rule_experiment_group` ADD COLUMN `rule_id`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ADD KEY `idx_experiment_group_rule` (`rule_id`)"));
    }

    @Test
    public void dataObjectFieldsAddStableVariableReferenceWhenMissing() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate("ref_variable_id");
        jdbcTemplate.missingIndexes.add("idx_ref_variable_id");
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod(
                "ensureDataObjectFieldReferenceSchema");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ADD COLUMN `ref_variable_id` BIGINT DEFAULT NULL"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "ADD INDEX `idx_ref_variable_id` (`ref_variable_id`)"));
    }

    @Test
    public void globalModelOwnershipIsRepairedIdempotently() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate();
        setField(service, "jdbcTemplate", jdbcTemplate);

        assertTrue(hasDeclaredMethod(SchemaSyncService.class, "ensureModelScopeConsistency"));
        Method method = SchemaSyncService.class.getDeclaredMethod("ensureModelScopeConsistency");
        method.setAccessible(true);
        method.invoke(service);

        assertEquals(1, jdbcTemplate.sqlList.size());
        String sql = jdbcTemplate.sqlList.get(0);
        assertTrue(sql, sql.contains("UPDATE `rule_model`"));
        assertTrue(sql, sql.contains("`project_id` = NULL"));
        assertTrue(sql, sql.contains("`project_code` = NULL"));
        assertTrue(sql, sql.contains("`project_name` = NULL"));
        assertTrue(sql, sql.contains("`scope` = 'GLOBAL'"));
    }

    private boolean containsSql(List<String> sqlList, String fragment) {
        for (String sql : sqlList) {
            if (sql.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDeclaredField(Class<?> type, String name) {
        for (Field field : type.getDeclaredFields()) {
            if (name.equals(field.getName())) return true;
        }
        return false;
    }

    private boolean hasDeclaredMethod(Class<?> type, String name) {
        for (Method method : type.getDeclaredMethods()) {
            if (name.equals(method.getName())) return true;
        }
        return false;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class FakeJdbcTemplate extends JdbcTemplate {
        private final List<String> sqlList = new ArrayList<>();
        private final Set<String> missingColumns;
        private final Set<String> missingTables = new HashSet<>();
        private final Set<String> missingIndexes = new HashSet<>();
        private final Set<String> nonLongTextColumns = new HashSet<>();

        private FakeJdbcTemplate(String... missingColumns) {
            this.missingColumns = new HashSet<>(Arrays.asList(missingColumns));
        }

        @Override
        public void execute(String sql) {
            sqlList.add(sql);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("INFORMATION_SCHEMA.TABLES") && args != null && args.length > 0
                    && missingTables.contains(String.valueOf(args[0]))) {
                return requiredType.cast(Integer.valueOf(0));
            }
            if (sql.contains("INFORMATION_SCHEMA.COLUMNS") && sql.contains("DATA_TYPE = ?")
                    && args != null && args.length > 2) {
                String columnKey = String.valueOf(args[0]) + "." + String.valueOf(args[1]);
                if (nonLongTextColumns.contains(columnKey)
                        || nonLongTextColumns.contains(String.valueOf(args[1]))) {
                    return requiredType.cast(Integer.valueOf(0));
                }
            }
            if (sql.contains("INFORMATION_SCHEMA.COLUMNS") && args != null && args.length > 1
                    && missingColumns.contains(String.valueOf(args[1]))) {
                return requiredType.cast(Integer.valueOf(0));
            }
            if (sql.contains("INFORMATION_SCHEMA.STATISTICS") && args != null && args.length > 1
                    && missingIndexes.contains(String.valueOf(args[1]))) {
                return requiredType.cast(Integer.valueOf(0));
            }
            return requiredType.cast(Integer.valueOf(1));
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            if (sql.contains("INFORMATION_SCHEMA.STATISTICS") && args != null && args.length > 1
                    && "uk_billing_summary_key".equals(String.valueOf(args[1]))) {
                List<String> columns = Arrays.asList("summary_date", "project_code", "billing_code",
                        "billing_target", "target_ref_id");
                @SuppressWarnings("unchecked")
                List<T> result = (List<T>) columns;
                return result;
            }
            return new ArrayList<>();
        }
    }
}
