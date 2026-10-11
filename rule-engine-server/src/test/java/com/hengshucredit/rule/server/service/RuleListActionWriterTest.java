package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.Assert.*;

public class RuleListActionWriterTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 10, 0);
    @Test public void writePoolDoesNotBorrowEngineQueryConnections() throws Exception {
        try (var main = new com.zaxxer.hikari.HikariDataSource()) {
            main.setJdbcUrl("jdbc:mysql://127.0.0.1:1/list_action_pool_test");
            main.setMaximumPoolSize(12); main.setMinimumIdle(0);
            try (var writer = new RuleListActionWriter(main)) {
                var pool = (com.zaxxer.hikari.HikariDataSource) org.springframework.test.util.ReflectionTestUtils.getField(writer, "writePool");
                assertNotSame(main, pool);
                assertEquals(12, main.getMaximumPoolSize());
                assertEquals(1, pool.getMaximumPoolSize());
                assertEquals(0, pool.getMinimumIdle());
                assertEquals("rule-list-write", pool.getPoolName());
            }
        }
    }

    @Test public void allSelectedListsReceiveEachTypedContentAndBlanksAreSkipped() {
        var db = new FakeJdbc(); var config = config("OVERWRITE", "PERMANENT");
        config.put("listIds", List.of(9, 8, 9)); config.put("itemTypes", List.of("MOBILE", "EMAIL", "OTHER"));
        int count = writer(db).write("multi", 1L, config, java.util.Arrays.asList("13800138000", "validation@example.invalid", null), NOW);
        assertEquals(4, count); assertEquals(4, db.recordWrites);
    }
    @Test public void mergePreservesPermanentWhileOverwriteCanClearExpiration() {
        var db = new FakeJdbc();
        db.previous.put("effective_time", NOW.minusDays(5)); db.previous.put("expire_time", NOW.plusDays(60));
        var writer = writer(db);
        writer.write("merge", 1L, config("MERGE", "DURATION"), List.of("13800138000"), NOW);
        assertEquals(NOW.minusDays(5), db.updated[0]); assertEquals(NOW.plusDays(60), db.updated[1]);
        writer.write("overwrite", 1L, config("OVERWRITE", "PERMANENT"), List.of("13800138000"), NOW);
        assertEquals(NOW, db.updated[0]); assertNull(db.updated[1]);
        db.previous.remove("expire_time");
        writer.write("permanent", 1L, config("MERGE", "DURATION"), List.of("13800138000"), NOW);
        assertNull(db.updated[1]);
    }
    @Test public void duplicateTaskDoesNotApplyAgainAndRemoveDoesNotInsertMissingContent() {
        var db = new FakeJdbc(); var writer = writer(db);
        writer.write("once", 1L, config("OVERWRITE", "PERMANENT"), List.of("x"), NOW);
        int writes = db.recordWrites;
        writer.write("once", 1L, config("OVERWRITE", "PERMANENT"), List.of("x"), NOW.plusDays(2));
        assertEquals(writes, db.recordWrites);
        db.previous.clear();
        writer.write("remove", 1L, config("REMOVE", "PERMANENT"), List.of("missing"), NOW);
        assertEquals(writes, db.recordWrites);
    }
    @Test public void crossProjectAndObjectContentAreRejected() {
        var writer = writer(new FakeJdbc());
        assertThrows(IllegalArgumentException.class, () -> writer.write("foreign", 2L, config("MERGE", "PERMANENT"), List.of("x"), NOW));
        assertThrows(IllegalArgumentException.class, () -> writer.write("object", 1L, config("MERGE", "PERMANENT"), List.of(Map.of("phone", "x")), NOW));
    }
    private static JSONObject config(String operation, String mode) {
        var config = JSON.parseObject("{\"listIds\":[8],\"itemTypes\":[\"MOBILE\"],\"duration\":30,\"durationUnit\":\"DAYS\"}");
        config.put("operation", operation); config.put("expiryMode", mode); return config;
    }
    private static RuleListActionWriter writer(FakeJdbc db) {
        return new RuleListActionWriter(db, new TransactionTemplate() {
            @Override public <T> T execute(TransactionCallback<T> callback) { return callback.doInTransaction(null); }
        });
    }
    private static class FakeJdbc extends JdbcTemplate {
        final Set<Object> tasks = new HashSet<>();
        final Map<String, Object> previous = new HashMap<>(Map.of("id", 11L, "status", 1));
        Object[] updated; int recordWrites;
        @Override public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("rule_list_library")) return List.of(Map.of("scope", "PROJECT", "project_id", 1L, "status", 1));
            return previous.isEmpty() ? List.of() : List.of(previous);
        }
        @Override public int update(String sql, Object... args) {
            if (sql.startsWith("INSERT IGNORE")) return tasks.add(args[0]) ? 1 : 0;
            if (sql.startsWith("UPDATE rule_list_record ") || sql.startsWith("INSERT INTO rule_list_record ")) { updated = args; recordWrites++; }
            return 1;
        }
    }
}
