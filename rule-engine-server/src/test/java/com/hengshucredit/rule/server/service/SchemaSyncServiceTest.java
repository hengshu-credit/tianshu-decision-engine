package com.hengshucredit.rule.server.service;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class FakeJdbcTemplate extends JdbcTemplate {
        private final List<String> sqlList = new ArrayList<>();

        @Override
        public void execute(String sql) {
            sqlList.add(sql);
        }

        @Override
        public <T> T queryForObject(String sql, Object[] args, Class<T> requiredType) {
            return requiredType.cast(Integer.valueOf(1));
        }
    }
}
