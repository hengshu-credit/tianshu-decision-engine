package com.hengshucredit.rule.server.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.After;
import org.junit.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DBConnectPoolsDeadlineTest {
    @After
    public void clearDeadlineAndInterrupt() {
        RequestDeadlineContext.clear();
        RequestDeadlineContext.resetNanoTimeSource();
        Thread.interrupted();
    }

    @Test
    public void expiredRequestDoesNotOpenConnectionPool() throws Exception {
        AtomicLong now = clock();
        RequestDeadlineContext.start(1000);
        now.set(TimeUnit.SECONDS.toNanos(1));
        try (JdbcFixture fixture = new JdbcFixture()) {
            assertThrows(SQLTimeoutException.class, () -> fixture.query(0, 0));
            assertEquals(0, fixture.poolLookups);
        }
    }

    @Test
    public void connectionWaitConsumesQueryBudgetAndZeroDoesNotDisableRequestDeadline() throws Exception {
        AtomicLong now = clock();
        RequestDeadlineContext.start(5000);
        try (JdbcFixture fixture = new JdbcFixture()) {
            fixture.onConnection = () -> now.set(TimeUnit.MILLISECONDS.toNanos(3100));
            fixture.onPrepare = () -> now.set(TimeUnit.MILLISECONDS.toNanos(4100));

            assertEquals(List.of(Map.of("score", 88)), fixture.query(0, 0));

            assertEquals(1, fixture.queryTimeout);
            assertEquals("request deadline must not silently truncate rows", 0, fixture.maxRows);
            assertTrue(fixture.networkTimeouts.stream().anyMatch(value -> value > 0 && value <= 900));
            assertEquals("pooled connection must retain its original network timeout", 0, fixture.networkTimeout);
        }
    }

    @Test
    public void deadlineDoesNotRelaxShorterConfiguredOrDriverTimeouts() throws Exception {
        clock();
        RequestDeadlineContext.start(5000);
        try (JdbcFixture fixture = new JdbcFixture()) {
            fixture.networkTimeout = 700;
            fixture.query(2000, 1);

            assertEquals(1, fixture.queryTimeout);
            assertEquals(2000, fixture.maxRows);
            assertEquals(700, fixture.networkTimeout);
            assertTrue(fixture.networkTimeouts.stream().allMatch(value -> value <= 700));
        }
    }

    @Test
    public void expirationWhileReadingRowsFailsAndClosesResourcesInsteadOfReturningPartialResult() throws Exception {
        AtomicLong now = clock();
        RequestDeadlineContext.start(1000);
        try (JdbcFixture fixture = new JdbcFixture()) {
            fixture.rowCount = 2;
            fixture.onValue = () -> now.set(TimeUnit.SECONDS.toNanos(2));

            assertThrows(SQLTimeoutException.class, () -> fixture.query(0, 0));

            assertTrue(fixture.resultClosed);
            assertTrue(fixture.statementClosed);
            assertTrue(fixture.connectionClosed);
            assertEquals("expired request must not fetch another row", 1, fixture.valuesRead);
            assertEquals(0, fixture.networkTimeout);
        }
    }

    @Test
    public void expirationOnFinalResultReadStillFails() throws Exception {
        AtomicLong now = clock();
        RequestDeadlineContext.start(1000);
        try (JdbcFixture fixture = new JdbcFixture()) {
            fixture.onEnd = () -> now.set(TimeUnit.SECONDS.toNanos(2));
            assertThrows(SQLTimeoutException.class, () -> fixture.query(0, 0));
            assertTrue(fixture.resultClosed);
        }
    }

    @Test
    public void unsupportedNetworkTimeoutStillHonorsStatementAndResultDeadline() throws Exception {
        AtomicLong now = clock();
        RequestDeadlineContext.start(1000);
        try (JdbcFixture fixture = new JdbcFixture()) {
            fixture.networkTimeoutSupported = false;
            fixture.onExecute = () -> now.set(TimeUnit.SECONDS.toNanos(2));

            assertThrows(SQLTimeoutException.class, () -> fixture.query(0, 0));
            assertEquals(1, fixture.queryTimeout);
            assertTrue(fixture.statementClosed);
            assertTrue(fixture.connectionClosed);
        }
    }

    @Test
    public void failureRestoringNetworkTimeoutDoesNotMaskOriginalDeadlineFailure() throws Exception {
        AtomicLong now = clock();
        RequestDeadlineContext.start(1000);
        try (JdbcFixture fixture = new JdbcFixture()) {
            fixture.restoreFailure = new SQLException("network timeout restore failed");
            fixture.onExecute = () -> now.set(TimeUnit.SECONDS.toNanos(2));

            SQLTimeoutException failure = assertThrows(SQLTimeoutException.class, () -> fixture.query(0, 0));

            assertEquals("HYT00", failure.getSQLState());
            assertEquals(1, failure.getSuppressed().length);
            assertSame(fixture.restoreFailure, failure.getSuppressed()[0]);
            assertTrue(fixture.connectionClosed);
        }
    }

    @Test
    public void interruptedRequestStopsBeforeOpeningPoolAndPreservesInterrupt() throws Exception {
        RequestDeadlineContext.start(5000);
        try (JdbcFixture fixture = new JdbcFixture()) {
            Thread.currentThread().interrupt();
            SQLException interrupted = assertThrows(SQLException.class, () -> fixture.query(0, 0));
            assertEquals("HY008", interrupted.getSQLState());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, fixture.poolLookups);
        }
    }

    @Test
    public void completedQueryCannotInterruptNextOperationOnSameThread() throws Exception {
        try (JdbcFixture fixture = new JdbcFixture()) {
            RequestDeadlineContext.start(500);
            fixture.query(0, 0);
            RequestDeadlineContext.clear();

            assertFalse(new CountDownLatch(1).await(600, TimeUnit.MILLISECONDS));
            assertFalse(Thread.currentThread().isInterrupted());
        }
    }

    @Test
    public void actualHikariConnectionWaitStopsAtRequestDeadlineWithoutChangingSharedPoolTimeout() throws Exception {
        try (JdbcFixture fixture = new JdbcFixture()) {
            HikariConfig config = new HikariConfig();
            config.setPoolName("database-deadline-test");
            config.setDataSource(jdbcProxy(DataSource.class, (name, args) -> switch (name) {
                case "getConnection" -> fixture.connection;
                case "getLoginTimeout" -> 0;
                default -> null;
            }));
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(1500);
            try (HikariDataSource dataSource = new HikariDataSource(config);
                 Connection occupied = dataSource.getConnection()) {
                DBConnectPools pools = new DBConnectPools() {
                    @Override public HikariDataSource getDataSource(Long id) { return dataSource; }
                };
                RequestDeadlineContext.start(100);
                long started = System.nanoTime();
                try {
                    assertThrows(SQLTimeoutException.class,
                            () -> pools.query(1L, "select score from risk", List.of(), 0, 0));
                    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                    assertTrue("must not wait for the 1500 ms shared pool timeout: " + elapsed, elapsed < 700);
                    assertEquals(1500L, dataSource.getConnectionTimeout());
                } finally {
                    Thread.interrupted();
                    pools.destroy();
                }
            }
        }
    }

    private AtomicLong clock() {
        AtomicLong now = new AtomicLong();
        RequestDeadlineContext.setNanoTimeSource(now::get);
        return now;
    }

    @FunctionalInterface
    private interface JdbcCall {
        Object invoke(String method, Object[] args) throws Throwable;
    }

    private static <T> T jdbcProxy(Class<T> type, JdbcCall handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (proxy, method, args) -> handler.invoke(method.getName(), args)));
    }

    private static final class JdbcFixture implements AutoCloseable {
        private int poolLookups;
        private int queryTimeout;
        private int maxRows;
        private int networkTimeout;
        private int rowCount = 1;
        private int rowsRead;
        private int valuesRead;
        private boolean networkTimeoutSupported = true;
        private boolean resultClosed;
        private boolean statementClosed;
        private boolean connectionClosed;
        private SQLException restoreFailure;
        private final List<Integer> networkTimeouts = new ArrayList<>();
        private Runnable onConnection = () -> { };
        private Runnable onPrepare = () -> { };
        private Runnable onExecute = () -> { };
        private Runnable onValue = () -> { };
        private Runnable onEnd = () -> { };
        private final Connection connection;
        private final HikariDataSource dataSource;
        private final DBConnectPools pools;

        private JdbcFixture() {
            ResultSetMetaData metadata = jdbcProxy(ResultSetMetaData.class, (name, args) -> switch (name) {
                case "getColumnCount" -> 1;
                case "getColumnLabel" -> "score";
                default -> null;
            });
            ResultSet result = jdbcProxy(ResultSet.class, (name, args) -> switch (name) {
                case "getMetaData" -> metadata;
                case "next" -> {
                    boolean present = rowsRead++ < rowCount;
                    if (!present) onEnd.run();
                    yield present;
                }
                case "getObject" -> { valuesRead++; onValue.run(); yield 88; }
                case "close" -> { resultClosed = true; yield null; }
                default -> null;
            });
            PreparedStatement statement = jdbcProxy(PreparedStatement.class, (name, args) -> switch (name) {
                case "setMaxRows" -> { maxRows = (int) args[0]; yield null; }
                case "setQueryTimeout" -> { queryTimeout = (int) args[0]; yield null; }
                case "executeQuery" -> { onExecute.run(); yield result; }
                case "close" -> { statementClosed = true; yield null; }
                default -> null;
            });
            connection = jdbcProxy(Connection.class, (name, args) -> switch (name) {
                case "prepareStatement" -> { onPrepare.run(); yield statement; }
                case "getNetworkTimeout" -> {
                    if (!networkTimeoutSupported) throw new SQLFeatureNotSupportedException();
                    yield networkTimeout;
                }
                case "setNetworkTimeout" -> {
                    if (!networkTimeoutSupported) throw new SQLFeatureNotSupportedException();
                    if ((int) args[1] == 0 && networkTimeout > 0 && restoreFailure != null) throw restoreFailure;
                    networkTimeout = (int) args[1];
                    networkTimeouts.add(networkTimeout);
                    yield null;
                }
                case "getAutoCommit", "isValid" -> true;
                case "isReadOnly", "isClosed" -> false;
                case "getTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
                case "close" -> { connectionClosed = true; yield null; }
                case "toString" -> "deadline-test-connection";
                default -> null;
            });
            dataSource = new HikariDataSource() {
                @Override public Connection getConnection() { onConnection.run(); return connection; }
            };
            pools = new DBConnectPools() {
                @Override public HikariDataSource getDataSource(Long id) {
                    poolLookups++;
                    return dataSource;
                }
            };
        }

        private List<Map<String, Object>> query(int maxRows, int timeout) throws Exception {
            return pools.query(1L, "select score from risk", List.of(), maxRows, timeout);
        }

        @Override
        public void close() throws Exception {
            pools.destroy();
            dataSource.close();
        }
    }
}
