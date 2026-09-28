package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LogRetentionServiceTest {
    @Test
    public void disabledRetentionDoesNotIssueDelete() {
        LogRetentionProperties properties = new LogRetentionProperties();
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        LogRetentionService service = new LogRetentionService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "properties", properties);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);

        service.cleanup();

        assertEquals(0, jdbc.calls);
        assertEquals(Boolean.FALSE, service.snapshot().get("enabled"));
    }

    @Test
    public void enabledRetentionDeletesOnlyConfiguredWhitelistedTables() {
        LogRetentionProperties properties = new LogRetentionProperties();
        properties.setEnabled(true);
        properties.setAuthAccessLogDays(90);
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        LogRetentionService service = new LogRetentionService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "properties", properties);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);

        service.cleanup();

        assertEquals(1, jdbc.calls);
        assertEquals(Map.of("authAccessLog", 1L), service.snapshot().get("lastDeleted"));
        assertEquals(List.of("executionLog", "runtimeCallLog", "experimentLog", "billingRecord", "lifecycleEvent", "listRecordLog"), service.snapshot().get("neverDeleteCategories"));
    }

    @Test
    public void everyDeleteHasAnExplicitBatchLimit() {
        LogRetentionProperties properties = new LogRetentionProperties();
        properties.setEnabled(true);
        properties.setAuthAccessLogDays(30);
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        LogRetentionService service = new LogRetentionService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "properties", properties);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);

        service.cleanup();

        assertTrue("大表清理必须有单批条数限制", jdbc.deletes.get(0).contains(" LIMIT ?"));
        assertEquals(properties.getBatchSize(), jdbc.lastParameters.get(2));
        Timestamp cutoff = (Timestamp) jdbc.lastParameters.get(1);
        assertTrue(cutoff.toLocalDateTime().isBefore(LocalDateTime.now().minusDays(29)));
        assertEquals(properties.getQueryTimeoutSeconds(), jdbc.lastTimeout);
        assertEquals(1, jdbc.releases);
    }

    @Test
    public void allPermanentPoliciesNeverBorrowAConnectionEvenWhenEnabled() {
        var properties = new LogRetentionProperties();
        properties.setEnabled(true);
        var jdbc = new RecordingJdbcTemplate();
        service(properties, jdbc).cleanup();
        assertEquals(0, jdbc.connections);
    }

    @Test
    public void fullBatchesStopAtBudgetAndReportUnfinishedWork() {
        var properties = new LogRetentionProperties();
        properties.setEnabled(true);
        properties.setAuthAccessLogDays(30);
        properties.setBatchSize(2);
        properties.setMaxBatchesPerTable(3);
        var jdbc = new RecordingJdbcTemplate();
        jdbc.rowsPerBatch = 2;
        var service = service(properties, jdbc);

        service.cleanup();

        assertEquals(3, jdbc.calls);
        assertEquals(Map.of("authAccessLog", 6L), service.snapshot().get("lastDeleted"));
        assertEquals("BATCH_LIMIT_REACHED", service.snapshot().get("lastRunStatus"));
        assertEquals(List.of("authAccessLog"), service.snapshot().get("batchLimitedTables"));
    }

    @Test
    public void missingTimeIndexSkipsOnlyTheAffectedTable() {
        var properties = new LogRetentionProperties();
        properties.setEnabled(true);
        properties.setAuthAccessLogDays(90);
        var jdbc = new RecordingJdbcTemplate();
        jdbc.unindexed = "rule_auth_access_log";
        var service = service(properties, jdbc);

        service.cleanup();

        assertEquals(0, jdbc.calls);
        assertEquals(Map.of(), service.snapshot().get("lastDeleted"));
        assertEquals("FAILED", service.snapshot().get("lastRunStatus"));
        assertTrue(((Map<?, ?>) service.snapshot().get("lastFailures")).containsKey("authAccessLog"));
    }

    @Test
    public void failedBatchKeepsConfirmedCountsAndReleasesTheDatabaseLock() {
        var properties = new LogRetentionProperties();
        properties.setEnabled(true);
        properties.setAuthAccessLogDays(90);
        properties.setBatchSize(2);
        var jdbc = new RecordingJdbcTemplate();
        jdbc.rowsPerBatch = 2;
        jdbc.failBillingBatch = 2;
        var service = service(properties, jdbc);

        service.cleanup();

        assertEquals("FAILED", service.snapshot().get("lastRunStatus"));
        assertEquals(Map.of("authAccessLog", 2L), service.snapshot().get("lastDeleted"));
        assertEquals(1, jdbc.releases);
        jdbc.failBillingBatch = 0;
        jdbc.rowsPerBatch = 1;
        service.cleanup();
        assertEquals("COMPLETED", service.snapshot().get("lastRunStatus"));
        assertEquals(Map.of(), service.snapshot().get("lastFailures"));
    }

    @Test
    public void lockHeldByAnotherNodeDoesNotIssueAnyDelete() {
        var properties = new LogRetentionProperties();
        properties.setEnabled(true);
        properties.setAuthAccessLogDays(30);
        var jdbc = new RecordingJdbcTemplate();
        jdbc.lockAvailable = false;
        var service = service(properties, jdbc);

        service.cleanup();

        assertEquals(0, jdbc.calls);
        assertEquals(0, jdbc.releases);
        assertEquals("SKIPPED_LOCKED", service.snapshot().get("lastRunStatus"));
    }

    @Test
    public void releaseFailureAbortsTheConnectionBeforeReturningItToThePool() {
        var properties = new LogRetentionProperties();
        properties.setEnabled(true);
        properties.setAuthAccessLogDays(30);
        var jdbc = new RecordingJdbcTemplate();
        jdbc.failRelease = true;
        var service = service(properties, jdbc);

        service.cleanup();

        assertEquals(1, jdbc.aborts);
        assertEquals("FAILED", service.snapshot().get("lastRunStatus"));
    }

    private LogRetentionService service(LogRetentionProperties properties, RecordingJdbcTemplate jdbc) {
        var service = new LogRetentionService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "properties", properties);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        return service;
    }

    private static final class RecordingJdbcTemplate extends org.springframework.jdbc.core.JdbcTemplate {
        private int calls;
        private int connections;
        private int releases;
        private int aborts;
        private int lastTimeout;
        private int rowsPerBatch = 1;
        private int failBillingBatch;
        private int billingBatches;
        private boolean lockAvailable = true;
        private boolean failRelease;
        private String unindexed;
        private final List<String> deletes = new ArrayList<>();
        private Map<Integer, Object> lastParameters;

        @Override
        public <T> T execute(ConnectionCallback<T> action) {
            connections++;
            Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName())) return statement((String) args[0]);
                        if ("abort".equals(method.getName())) { aborts++; return null; }
                        throw new AssertionError("unexpected connection method: " + method.getName());
                    });
            try { return action.doInConnection(connection); }
            catch (SQLException error) { throw new DataAccessResourceFailureException("test database failure", error); }
        }

        private PreparedStatement statement(String sql) {
            Map<Integer, Object> parameters = new LinkedHashMap<>();
            return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                        String name = method.getName();
                        if ("setQueryTimeout".equals(name)) { lastTimeout = (Integer) args[0]; return null; }
                        if ("setString".equals(name) || "setInt".equals(name) || "setTimestamp".equals(name)) {
                            parameters.put((Integer) args[0], args[1]); return null;
                        }
                        if ("executeQuery".equals(name)) {
                            int value;
                            if (sql.contains("GET_LOCK")) value = lockAvailable ? 1 : 0;
                            else if (sql.contains("RELEASE_LOCK")) {
                                releases++;
                                if (failRelease) throw new SQLException("release failed");
                                value = 1;
                            } else value = parameters.get(1).equals(unindexed) ? 0 : 1;
                            return result(value);
                        }
                        if ("executeUpdate".equals(name)) {
                            calls++; deletes.add(sql); lastParameters = Map.copyOf(parameters);
                            if (sql.contains("rule_auth_access_log")) {
                                if (++billingBatches == failBillingBatch) throw new SQLException("batch failed");
                                return rowsPerBatch;
                            }
                            return 1;
                        }
                        if ("close".equals(name)) return null;
                        throw new AssertionError("unexpected statement method: " + name);
                    });
        }

        private ResultSet result(int value) {
            boolean[] read = {false};
            return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "next" -> { boolean next = !read[0]; read[0] = true; yield next; }
                        case "getInt" -> value;
                        case "wasNull" -> false;
                        case "close" -> null;
                        default -> throw new AssertionError("unexpected result method: " + method.getName());
                    });
        }
    }
}
