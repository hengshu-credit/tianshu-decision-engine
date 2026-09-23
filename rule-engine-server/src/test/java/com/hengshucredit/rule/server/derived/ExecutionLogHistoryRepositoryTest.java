package com.hengshucredit.rule.server.derived;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class ExecutionLogHistoryRepositoryTest {
    @Test
    public void actualObjectInputWinsOverReusableApiFieldSnapshotIncludingNull() {
        for (String value : List.of("0", "null")) {
            CapturingJdbc jdbc = new CapturingJdbc();
            jdbc.roots = List.of(root("{\"version\":1,\"fields\":{\"DATA_OBJECT:12\":" + value + "},\"apiIds\":[8]}"));
            jdbc.apis = List.of(Map.of("root_trace_id", "root", "target_ref_id", 8L, "history_fields", "{\"DATA_OBJECT:12\":42}"));
            var rows = new ExecutionLogHistoryRepository(jdbc).query("GLOBAL", null, null,
                    LocalDateTime.of(2026, 9, 22, 0, 0), LocalDateTime.of(2026, 9, 23, 0, 0), Set.of("DATA_OBJECT:12"), Map.of());
            assertEquals("0".equals(value) ? 0 : null, rows.get(0).fields().get("DATA_OBJECT:12"));
        }
    }
    @Test
    public void changingSourceOrApiDoesNotHidePreviouslyRecordedFieldResults() {
        for (String source : List.of("INPUT", "API")) {
            CapturingJdbc jdbc = new CapturingJdbc();
            jdbc.roots = List.of(root("{\"version\":1,\"fields\":{},\"apiIds\":[8]}"));
            jdbc.apis = List.of(Map.of("root_trace_id", "root", "target_ref_id", 8L, "history_fields", "{\"VARIABLE:12\":42}"));
            var definition = new HistoricalFieldDefinition("VARIABLE:12", "currentName", source, false, null, 99L, "body.newPath");
            var rows = new ExecutionLogHistoryRepository(jdbc).query("GLOBAL", null, null,
                    LocalDateTime.of(2026, 9, 22, 0, 0), LocalDateTime.of(2026, 9, 23, 0, 0), Set.of("VARIABLE:12"), Map.of("VARIABLE:12", definition));
            assertEquals(42, rows.get(0).fields().get("VARIABLE:12"));
            assertFalse(jdbc.boundValues.contains(99L));
        }
    }

    @Test
    public void explicitApiNodeResultsAreAlsoReadFromTheRootCallLog() {
        CapturingJdbc jdbc = new CapturingJdbc();
        jdbc.roots = List.of(root("{\"version\":1,\"fields\":{},\"apiIds\":[]}"));
        jdbc.apis = List.of(Map.of("root_trace_id", "root", "target_ref_id", 8L, "history_fields", "{\"VARIABLE:12\":42}"));
        var rows = new ExecutionLogHistoryRepository(jdbc).query("GLOBAL", null, null,
                LocalDateTime.of(2026, 9, 22, 0, 0), LocalDateTime.of(2026, 9, 23, 0, 0), Set.of("VARIABLE:12"), Map.of());
        assertEquals(42, rows.get(0).fields().get("VARIABLE:12"));
        assertFalse(jdbc.sql.contains("target_ref_id IN"));
    }

    @Test
    public void readsOnlySuccessfulClientRootLogsAndUsesStableReferencePaths() {
        CapturingJdbc jdbc = new CapturingJdbc();
        ExecutionLogHistoryRepository repository = new ExecutionLogHistoryRepository(jdbc);
        repository.query("RULE", 3L, 49L,
                LocalDateTime.of(2026, 9, 22, 0, 0),
                LocalDateTime.of(2026, 9, 23, 0, 0),
                Map.of("VARIABLE:6", "idcard_no", "VARIABLE:73", "gps_longitude"));

        assertTrue(jdbc.sql.contains("rule_execution_log"));
        assertTrue(jdbc.sql.contains("l.success = 1"));
        assertTrue(jdbc.sql.contains("CLIENT_SERVER"));
        assertTrue(jdbc.sql.contains("l.root_rule_id = ?"));
        assertFalse(jdbc.sql.contains("rule_code"));
        assertFalse(jdbc.sql.contains("project_code"));
        assertEquals(4, jdbc.parameterCount);
    }

    @Test
    public void renamedDisplayPathCannotChangeHistoricalFieldIdentity() {
        CapturingJdbc jdbc = new CapturingJdbc();
        jdbc.roots = List.of(root("{\"version\":1,\"fields\":{\"VARIABLE:6\":88},\"apiIds\":[]}"));
        var rows = new ExecutionLogHistoryRepository(jdbc).query("GLOBAL", null, null,
                LocalDateTime.of(2026, 9, 22, 0, 0), LocalDateTime.of(2026, 9, 23, 0, 0), Map.of("VARIABLE:6", "renamed"));
        assertEquals(88, rows.get(0).fields().get("VARIABLE:6"));
    }

    @Test
    public void historicalApiValueComesFromThatRootLogicalCallNotCurrentMapping() {
        CapturingJdbc jdbc = new CapturingJdbc();
        jdbc.roots = List.of(root("{\"version\":1,\"fields\":{},\"apiIds\":[8]}"));
        jdbc.apis = List.of(Map.of("root_trace_id", "root", "target_ref_id", 8L, "history_fields", "{\"VARIABLE:12\":42}"));
        var definition = new HistoricalFieldDefinition("VARIABLE:12", "newName", "API", false, null, 8L, "body.newPath");
        var rows = new ExecutionLogHistoryRepository(jdbc).query("GLOBAL", null, null,
                LocalDateTime.of(2026, 9, 22, 0, 0), LocalDateTime.of(2026, 9, 23, 0, 0), Set.of("VARIABLE:12"), Map.of("VARIABLE:12", definition));
        assertEquals(42, rows.get(0).fields().get("VARIABLE:12"));
        assertTrue(jdbc.sql.contains("action_type = 'API_INVOKE'"));
        jdbc.apis = List.of();
        assertThrows(IllegalStateException.class, () -> new ExecutionLogHistoryRepository(jdbc).query("GLOBAL", null, null,
                LocalDateTime.of(2026, 9, 22, 0, 0), LocalDateTime.of(2026, 9, 23, 0, 0), Set.of("VARIABLE:12"), Map.of("VARIABLE:12", definition)));
    }

    @Test
    public void overLimitIsRejectedBeforeDiscardingAnEmptySentinelRow() {
        CapturingJdbc jdbc = new CapturingJdbc();
        jdbc.roots = new ArrayList<>(java.util.Collections.nCopies(100000, root("{\"version\":1,\"fields\":{},\"apiIds\":[]}")));
        jdbc.roots.add(root(""));
        var error = assertThrows(IllegalStateException.class, () -> new ExecutionLogHistoryRepository(jdbc).query("GLOBAL", null, null,
                LocalDateTime.of(2026, 9, 22, 0, 0), LocalDateTime.of(2026, 9, 23, 0, 0), Map.of()));
        assertTrue(error.getMessage().contains("100000"));
    }

    private static Map<String, Object> root(String snapshot) {
        return Map.of("id", 1L, "trace_id", "root", "started_at", Timestamp.valueOf("2026-09-22 12:00:00"), "history_fields", snapshot);
    }

    private static final class CapturingJdbc extends JdbcTemplate {
        private String sql;
        private int parameterCount;
        private List<Map<String, Object>> roots = List.of();
        private List<Map<String, Object>> apis = List.of();
        private final List<Object> boundValues = new ArrayList<>();

        @Override
        public <T> List<T> query(PreparedStatementCreator creator, RowMapper<T> mapper) {
            boundValues.clear();
            PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                    (proxy, method, args) -> {
                        if ("setObject".equals(method.getName())) parameterCount++;
                        if (Set.of("setObject", "setLong", "setString").contains(method.getName())) boundValues.add(args[1]);
                        return null;
                    });
            Connection connection = (Connection) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName())) {
                            sql = String.valueOf(args[0]);
                            return statement;
                        }
                        return null;
                    });
            try {
                creator.createPreparedStatement(connection);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            List<T> result = new ArrayList<>();
            for (Map<String, Object> row : sql.contains("rule_execution_log") ? roots : apis) {
                ResultSet data = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class},
                        (proxy, method, args) -> row.get(String.valueOf(args[0])));
                try { result.add(mapper.mapRow(data, result.size())); }
                catch (java.sql.SQLException e) { throw new AssertionError(e); }
            }
            return result;
        }
    }
}
