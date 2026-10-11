package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DBConnectPoolsLifecycleTest {
    @Test
    public void simultaneousColdRequestsShareOnePoolAndDoNotCloseEachOthersPool() throws Exception {
        try (PoolFixture fixture = new PoolFixture()) {
            PoolPlan plan = fixture.configure(1L, "original", true);
            Future<HikariDataSource> first = fixture.callers.submit(() -> fixture.pools.getDataSource(1L));
            assertTrue(plan.entered.await(1, TimeUnit.SECONDS));
            AtomicReference<Thread> waitingThread = new AtomicReference<>();
            Future<HikariDataSource> second = fixture.callers.submit(() -> {
                waitingThread.set(Thread.currentThread());
                return fixture.pools.getDataSource(1L);
            });
            awaitWaiting(waitingThread);
            plan.release.countDown();

            HikariDataSource pool = first.get(2, TimeUnit.SECONDS);
            assertSame(pool, second.get(2, TimeUnit.SECONDS));
            assertFalse(pool.isClosed());
            assertEquals(1, plan.connections.get());
        }
    }

    @Test
    public void slowColdDatasourceDoesNotBlockUnrelatedWarmPool() throws Exception {
        try (PoolFixture fixture = new PoolFixture()) {
            fixture.configure(2L, "warm", false);
            HikariDataSource warm = fixture.pools.getDataSource(2L);
            PoolPlan cold = fixture.configure(1L, "slow", true);
            Future<HikariDataSource> loading = fixture.callers.submit(() -> fixture.pools.getDataSource(1L));
            assertTrue(cold.entered.await(1, TimeUnit.SECONDS));

            Future<HikariDataSource> reading = fixture.callers.submit(() -> fixture.pools.getDataSource(2L));
            assertSame(warm, reading.get(500, TimeUnit.MILLISECONDS));
            assertEquals(1L, cold.release.getCount());
            cold.release.countDown();
            assertFalse(loading.get(2, TimeUnit.SECONDS).isClosed());
        }
    }

    @Test
    public void refreshDuringLoadCannotReplaceFreshPoolWithLateOldConfiguration() throws Exception {
        try (PoolFixture fixture = new PoolFixture()) {
            PoolPlan old = fixture.configure(1L, "old", true);
            Future<HikariDataSource> loading = fixture.callers.submit(() -> fixture.pools.getDataSource(1L));
            assertTrue(old.entered.await(1, TimeUnit.SECONDS));
            fixture.configure(1L, "fresh", false);

            fixture.pools.refresh(1L);
            HikariDataSource fresh = fixture.pools.getDataSource(1L);
            old.release.countDown();

            ExecutionException retired = assertThrows(ExecutionException.class,
                    () -> loading.get(2, TimeUnit.SECONDS));
            assertTrue(retired.getCause() instanceof IllegalStateException);
            assertSame(fresh, fixture.pools.getDataSource(1L));
            assertFalse(fresh.isClosed());
            assertEquals(1, old.closed.get());
        }
    }

    @Test
    public void deletionDuringLoadCannotResurrectDeletedDatasource() throws Exception {
        try (PoolFixture fixture = new PoolFixture()) {
            PoolPlan old = fixture.configure(1L, "deleted", true);
            Future<HikariDataSource> loading = fixture.callers.submit(() -> fixture.pools.getDataSource(1L));
            assertTrue(old.entered.await(1, TimeUnit.SECONDS));
            fixture.datasources.remove(1L);
            fixture.pools.refresh(1L);
            old.release.countDown();

            assertThrows(ExecutionException.class, () -> loading.get(2, TimeUnit.SECONDS));
            assertThrows(IllegalArgumentException.class, () -> fixture.pools.getDataSource(1L));
            assertEquals(1, old.closed.get());
        }
    }

    @Test
    public void refreshReleasesWaitingRequestsBeforeOldDriverFinishesConnecting() throws Exception {
        try (PoolFixture fixture = new PoolFixture()) {
            PoolPlan old = fixture.configure(1L, "retired", true);
            Future<HikariDataSource> loading = fixture.callers.submit(() -> fixture.pools.getDataSource(1L));
            assertTrue(old.entered.await(1, TimeUnit.SECONDS));
            AtomicReference<Thread> waitingThread = new AtomicReference<>();
            Future<HikariDataSource> waiter = fixture.callers.submit(() -> {
                waitingThread.set(Thread.currentThread());
                return fixture.pools.getDataSource(1L);
            });
            awaitWaiting(waitingThread);

            fixture.pools.refresh(1L);

            ExecutionException retired = assertThrows(ExecutionException.class,
                    () -> waiter.get(500, TimeUnit.MILLISECONDS));
            assertTrue(retired.getCause() instanceof IllegalStateException);
            assertEquals(1L, old.release.getCount());
            old.release.countDown();
            assertThrows(ExecutionException.class, () -> loading.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    public void destroyRejectsNewRequestsAndClosesLatePoolWithoutLeavingAnOrphan() throws Exception {
        try (PoolFixture fixture = new PoolFixture()) {
            PoolPlan old = fixture.configure(1L, "closing", true);
            Future<HikariDataSource> loading = fixture.callers.submit(() -> fixture.pools.getDataSource(1L));
            assertTrue(old.entered.await(1, TimeUnit.SECONDS));

            fixture.pools.destroy();
            assertThrows(IllegalStateException.class, () -> fixture.pools.getDataSource(2L));
            old.release.countDown();

            assertThrows(ExecutionException.class, () -> loading.get(2, TimeUnit.SECONDS));
            assertEquals(1, old.closed.get());
            assertEquals(0, fixture.pools.snapshot().get("poolCount"));
        }
    }

    @Test
    public void interruptedWaiterDoesNotCancelSharedInitialization() throws Exception {
        try (PoolFixture fixture = new PoolFixture()) {
            PoolPlan plan = fixture.configure(1L, "shared", true);
            Future<HikariDataSource> first = fixture.callers.submit(() -> fixture.pools.getDataSource(1L));
            assertTrue(plan.entered.await(1, TimeUnit.SECONDS));
            AtomicReference<Thread> waitingThread = new AtomicReference<>();
            Future<Boolean> waiter = fixture.callers.submit(() -> {
                waitingThread.set(Thread.currentThread());
                assertThrows(IllegalStateException.class, () -> fixture.pools.getDataSource(1L));
                return Thread.currentThread().isInterrupted();
            });
            awaitWaiting(waitingThread);
            waitingThread.get().interrupt();
            assertTrue(waiter.get(1, TimeUnit.SECONDS));
            plan.release.countDown();

            assertFalse(first.get(2, TimeUnit.SECONDS).isClosed());
            assertEquals(1, plan.connections.get());
        }
    }

    @Test
    public void failedConfigurationCanBeCorrectedAndRetried() throws Exception {
        try (PoolFixture fixture = new PoolFixture()) {
            assertThrows(IllegalArgumentException.class, () -> fixture.pools.getDataSource(1L));
            fixture.configure(1L, "corrected", false);
            assertFalse(fixture.pools.getDataSource(1L).isClosed());
        }
    }

    @Test
    public void closedPoolIsRebuiltOnceOnNextAccess() throws Exception {
        try (PoolFixture fixture = new PoolFixture()) {
            PoolPlan plan = fixture.configure(1L, "reopen", false);
            HikariDataSource old = fixture.pools.getDataSource(1L);
            old.close();

            HikariDataSource fresh = fixture.pools.getDataSource(1L);
            assertNotSame(old, fresh);
            assertFalse(fresh.isClosed());
            assertEquals(2, plan.connections.get());
        }
    }

    private static void awaitWaiting(AtomicReference<Thread> thread) throws Exception {
        await(() -> thread.get() != null && (thread.get().getState() == Thread.State.WAITING
                || thread.get().getState() == Thread.State.TIMED_WAITING));
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(1);
        assertTrue(condition.getAsBoolean());
    }

    private static final class PoolPlan {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release;
        private final AtomicInteger connections = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();

        private PoolPlan(boolean blocked) {
            release = new CountDownLatch(blocked ? 1 : 0);
        }

        private Connection connect() throws SQLException {
            connections.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new SQLException("test connection was not released");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new SQLException("test connection interrupted", interrupted);
            }
            AtomicInteger networkTimeout = new AtomicInteger();
            java.util.concurrent.atomic.AtomicBoolean connectionClosed = new java.util.concurrent.atomic.AtomicBoolean();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAutoCommit", "isValid" -> true;
                        case "isReadOnly" -> false;
                        case "isClosed" -> connectionClosed.get();
                        case "getNetworkTimeout" -> networkTimeout.get();
                        case "setNetworkTimeout" -> { networkTimeout.set((int) args[1]); yield null; }
                        case "getTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
                        case "close", "abort" -> {
                            if (connectionClosed.compareAndSet(false, true)) closed.incrementAndGet();
                            yield null;
                        }
                        case "toString" -> "pool-lifecycle-test-connection";
                        default -> null;
                    });
        }
    }

    private static final class PoolFixture implements AutoCloseable, Driver {
        private final String prefix = "jdbc:pool-lifecycle:" + UUID.randomUUID() + ":";
        private final Map<String, PoolPlan> plans = new ConcurrentHashMap<>();
        private final Map<Long, RuleDbDatasource> datasources = new ConcurrentHashMap<>();
        private final ExecutorService callers = Executors.newFixedThreadPool(3);
        private final DBConnectPools pools = new DBConnectPools();

        private PoolFixture() throws SQLException {
            DriverManager.registerDriver(this);
            RuleDbDatasourceMapper mapper = (RuleDbDatasourceMapper) Proxy.newProxyInstance(
                    RuleDbDatasourceMapper.class.getClassLoader(), new Class[]{RuleDbDatasourceMapper.class},
                    (proxy, method, args) -> "selectById".equals(method.getName()) ? datasources.get(args[0]) : null);
            ReflectionTestUtils.setField(pools, "datasourceMapper", mapper);
        }

        private PoolPlan configure(Long id, String version, boolean blocked) {
            PoolPlan plan = new PoolPlan(blocked);
            String url = prefix + version;
            plans.put(url, plan);
            RuleDbDatasource config = new RuleDbDatasource();
            config.setId(id);
            config.setDatasourceCode("pool_test_" + id);
            config.setJdbcUrl(url);
            config.setStatus(1);
            config.setMaxPoolSize(1);
            config.setMinIdle(1);
            config.setConnectionTimeoutMs(250);
            datasources.put(id, config);
            return plan;
        }

        @Override public Connection connect(String url, Properties info) throws SQLException {
            PoolPlan plan = plans.get(url);
            return plan == null ? null : plan.connect();
        }
        @Override public boolean acceptsURL(String url) { return url != null && url.startsWith(prefix); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
        @Override public int getMajorVersion() { return 1; }
        @Override public int getMinorVersion() { return 0; }
        @Override public boolean jdbcCompliant() { return false; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }

        @Override
        public void close() throws Exception {
            plans.values().forEach(plan -> plan.release.countDown());
            callers.shutdownNow();
            callers.awaitTermination(3, TimeUnit.SECONDS);
            pools.destroy();
            DriverManager.deregisterDriver(this);
        }
    }
}
