package com.hengshucredit.rule.server.derived;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ApplicationHistoryRepositoryTest {
    private final LocalDateTime before = LocalDateTime.of(2026, 9, 21, 12, 0);

    @Test public void scopeUsesIdsAndHalfOpenWindowInParameterizedSql() {
        CapturingJdbc jdbc = new CapturingJdbc();
        ApplicationHistoryRepository repository = new ApplicationHistoryRepository(jdbc);
        repository.query("RULE", 3L, 7L, before.minusDays(30), before);
        assertTrue(jdbc.sql.contains("occurred_at >= ? AND occurred_at < ?"));
        assertTrue(jdbc.sql.contains("root_rule_id = ? AND project_id = ?"));
        assertEquals(List.of(Timestamp.valueOf(before.minusDays(30)), Timestamp.valueOf(before), 7L, 3L), jdbc.args);
        assertEquals(10, jdbc.timeout);
        repository.query("PROJECT", 3L, 7L, before.minusDays(1), before);
        assertFalse(jdbc.sql.contains("root_rule_id ="));
        assertTrue(jdbc.sql.contains("project_id ="));
        repository.query("GLOBAL", null, null, before.minusDays(1), before);
        assertFalse(jdbc.sql.contains("project_id ="));
        assertEquals(2, jdbc.args.size());
    }

    @Test public void missingScopeNeverFallsBackToGlobalAndOversizedWindowsNeverTruncate() {
        CapturingJdbc jdbc = new CapturingJdbc();
        ApplicationHistoryRepository repository = new ApplicationHistoryRepository(jdbc);
        assertThrows(IllegalArgumentException.class, () -> repository.query("RULE", 3L, null, before.minusDays(1), before));
        assertThrows(IllegalArgumentException.class, () -> repository.query("PROJECT", null, null, before.minusDays(1), before));
        assertThrows(IllegalArgumentException.class, () -> repository.query("UNKNOWN", null, null, before.minusDays(1), before));
        jdbc.rows = Collections.nCopies(100001, new HistoryQuery.Row(1, before, Map.of()));
        assertThrows(IllegalStateException.class, () -> repository.query("GLOBAL", null, null, before.minusDays(1), before));
    }

    @Test public void recordsStableFieldIdsAndDeduplicatesOnlyTheSameRootTrace() {
        CapturingJdbc jdbc = new CapturingJdbc();
        new ApplicationHistoryRepository(jdbc).record(3L, 7L, "trace-1", before, Map.of("VARIABLE:9", "synthetic"));
        assertTrue(jdbc.sql.contains("ON DUPLICATE KEY UPDATE trace_id = trace_id"));
        assertEquals(List.of(3L, 7L, "trace-1", Timestamp.valueOf(before), "{\"VARIABLE:9\":\"synthetic\"}"), jdbc.args);
    }

    private static class CapturingJdbc extends JdbcTemplate {
        String sql; List<Object> args; int timeout; List<HistoryQuery.Row> rows = List.of();
        @Override public int update(String sql, Object... args) {
            this.sql = sql; this.args = java.util.Arrays.asList(args); return 1;
        }
        @Override @SuppressWarnings("unchecked")
        public <T> List<T> query(PreparedStatementCreator creator, RowMapper<T> mapper) {
            args = new ArrayList<>();
            PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (proxy, method, values) -> {
                if ("setObject".equals(method.getName())) args.add(values[1]);
                if ("setQueryTimeout".equals(method.getName())) timeout = (Integer) values[0];
                return null;
            });
            Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, values) -> {
                if ("prepareStatement".equals(method.getName())) { sql = (String) values[0]; return statement; }
                return null;
            });
            try { creator.createPreparedStatement(connection); }
            catch (java.sql.SQLException exception) { throw new AssertionError(exception); }
            return (List<T>) rows;
        }
    }
}
