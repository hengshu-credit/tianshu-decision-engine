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
    public void apiDocScenarioSchemaCreatesTableWhenMissing() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate();
        jdbcTemplate.missingTables.add("rule_api_doc_scenario");
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod("ensureApiDocScenarioSchema");
        method.setAccessible(true);
        method.invoke(service);

        assertTrue(containsSql(jdbcTemplate.sqlList, "CREATE TABLE `rule_api_doc_scenario`"));
        assertTrue(containsSql(jdbcTemplate.sqlList,
                "KEY `idx_api_doc_scenario_export` (`definition_id`, `status`, `include_in_doc`, `sort_order`)"));
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

        private FakeJdbcTemplate(String... missingColumns) {
            this.missingColumns = new HashSet<>(Arrays.asList(missingColumns));
        }

        @Override
        public void execute(String sql) {
            sqlList.add(sql);
        }

        @Override
        public <T> T queryForObject(String sql, Object[] args, Class<T> requiredType) {
            if (sql.contains("INFORMATION_SCHEMA.TABLES") && args != null && args.length > 0
                    && missingTables.contains(String.valueOf(args[0]))) {
                return requiredType.cast(Integer.valueOf(0));
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
        public <T> List<T> queryForList(String sql, Object[] args, Class<T> elementType) {
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
