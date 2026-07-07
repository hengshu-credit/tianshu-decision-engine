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

public class SchemaSyncServiceTest {

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
    public void externalApiAsyncColumnsAreAddedWhenMissing() throws Exception {
        SchemaSyncService service = new SchemaSyncService();
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate("async_result_mode", "async_poll_config", "async_callback_config", "test_sample_params");
        setField(service, "jdbcTemplate", jdbcTemplate);

        Method method = SchemaSyncService.class.getDeclaredMethod("ensureExternalApiCacheColumns");
        method.setAccessible(true);
        method.invoke(service);

        assertEquals(5, jdbcTemplate.sqlList.size());
        assertEquals("ALTER TABLE `rule_external_api_config` MODIFY COLUMN `content_type` VARCHAR(128) DEFAULT NULL COMMENT '请求Content-Type，空表示不主动设置'",
                jdbcTemplate.sqlList.get(0));
        assertEquals("ALTER TABLE `rule_external_api_config` ADD COLUMN `async_result_mode` VARCHAR(32) DEFAULT NULL COMMENT '异步结果获取方式：POLL/CALLBACK' AFTER `fallback_value`",
                jdbcTemplate.sqlList.get(1));
        assertEquals("ALTER TABLE `rule_external_api_config` ADD COLUMN `async_poll_config` JSON DEFAULT NULL COMMENT '异步轮询配置JSON' AFTER `async_result_mode`",
                jdbcTemplate.sqlList.get(2));
        assertEquals("ALTER TABLE `rule_external_api_config` ADD COLUMN `async_callback_config` JSON DEFAULT NULL COMMENT '异步回调配置JSON' AFTER `async_poll_config`",
                jdbcTemplate.sqlList.get(3));
        assertEquals("ALTER TABLE `rule_external_api_config` ADD COLUMN `test_sample_params` LONGTEXT DEFAULT NULL COMMENT 'API调用测试样例JSON' AFTER `description`",
                jdbcTemplate.sqlList.get(4));
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class FakeJdbcTemplate extends JdbcTemplate {
        private final List<String> sqlList = new ArrayList<>();
        private final Set<String> missingColumns;

        private FakeJdbcTemplate(String... missingColumns) {
            this.missingColumns = new HashSet<>(Arrays.asList(missingColumns));
        }

        @Override
        public void execute(String sql) {
            sqlList.add(sql);
        }

        @Override
        public <T> T queryForObject(String sql, Object[] args, Class<T> requiredType) {
            if (sql.contains("INFORMATION_SCHEMA.COLUMNS") && args != null && args.length > 1
                    && missingColumns.contains(String.valueOf(args[1]))) {
                return requiredType.cast(Integer.valueOf(0));
            }
            return requiredType.cast(Integer.valueOf(1));
        }
    }
}
