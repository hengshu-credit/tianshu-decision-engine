package com.hengshucredit.rule.server.governance;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.hengshucredit.rule.server.service.DBConnectPools;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** 通过实际 Bean 工厂和 Spring JDBC 事务管理器验证失效时机，不手工调用事务回调。 */
public class DatabaseGovernancePoolInvalidationTest {
    @Test
    public void everyAppliedLifecycleActionInvalidatesOnlyAfterCommitAndUsesResourceId() throws Exception {
        for (String action : List.of("UPDATE", "ENABLE", "DISABLE", "DELETE", "RESTORE")) {
            try (Fixture fixture = new Fixture()) {
                AppliedResource applied = fixture.transaction.execute(status -> {
                    AppliedResource result = fixture.adapter.apply(context(17L, action, "ACTIVE"));
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals("pool must remain usable until the approval transaction commits",
                            List.of("WRITE:17"), fixture.events);
                    return result;
                });

                assertEquals(List.of("WRITE:17", "COMMIT", "REFRESH:17"), fixture.events);
                assertEquals(Long.valueOf(17), applied.resourceId());
                assertEquals(Map.of("UPDATE", 1, "ENABLE", 1, "DISABLE", 0, "DELETE", -1, "RESTORE", 1)
                        .get(action), fixture.lastWritten.getStatus());
                assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
            }
        }
    }

    @Test
    public void updatingStatusThroughDatabaseFormInvalidatesAfterCommit() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.transaction.executeWithoutResult(status ->
                    fixture.adapter.apply(context(17L, "UPDATE", "DISABLED")));

            assertEquals(Integer.valueOf(0), fixture.lastWritten.getStatus());
            assertEquals(List.of("WRITE:17", "COMMIT", "REFRESH:17"), fixture.events);
        }
    }

    @Test
    public void approvalFailureAfterProjectionWriteRollsBackWithoutInvalidatingHotPool() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IllegalStateException versionFailure = new IllegalStateException("approval version write failed");

            IllegalStateException actual = assertThrows(IllegalStateException.class, () ->
                    fixture.transaction.executeWithoutResult(status -> {
                        fixture.adapter.apply(context(17L, "UPDATE", "ACTIVE"));
                        throw versionFailure;
                    }));

            assertSame(versionFailure, actual);
            assertEquals(List.of("WRITE:17", "ROLLBACK"), fixture.events);
        }
    }

    @Test
    public void rollbackOnlyApprovalDoesNotInvalidatePool() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.transaction.executeWithoutResult(status -> {
                fixture.adapter.apply(context(17L, "DELETE", "ACTIVE"));
                status.setRollbackOnly();
            });

            assertEquals(List.of("WRITE:17", "ROLLBACK"), fixture.events);
        }
    }

    @Test
    public void joinedApprovalWaitsForOutermostTransactionCommit() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.transaction.executeWithoutResult(outer -> {
                fixture.transaction.executeWithoutResult(inner ->
                        fixture.adapter.apply(context(17L, "RESTORE", "ACTIVE")));
                assertEquals(List.of("WRITE:17"), fixture.events);
            });

            assertEquals(List.of("WRITE:17", "COMMIT", "REFRESH:17"), fixture.events);
        }
    }

    @Test
    public void rejectedDatabaseWriteDoesNotRegisterInvalidation() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.rejectWrite = true;
            assertThrows(IllegalStateException.class, () -> fixture.transaction.executeWithoutResult(status ->
                    fixture.adapter.apply(context(17L, "UPDATE", "ACTIVE"))));

            assertEquals(List.of("WRITE:17", "ROLLBACK"), fixture.events);
        }
    }

    @Test
    public void directCreateWithoutTransactionRefreshesTheGeneratedIdAfterSuccessfulWrite() throws Exception {
        try (Fixture fixture = new Fixture()) {
            AppliedResource applied = fixture.adapter.apply(context(null, "CREATE", "ACTIVE"));

            assertEquals(Long.valueOf(41), applied.resourceId());
            assertEquals(List.of("WRITE:41", "REFRESH:41"), fixture.events);
        }
    }

    @Test
    public void postCommitInvalidationFailureIsLoggedWithoutReportingAnApprovalRollback() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(
                "com.hengshucredit.rule.server.governance.DatabaseGovernedResourceAdapter");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (Fixture fixture = new Fixture()) {
            fixture.failRefresh = true;
            AppliedResource applied = fixture.transaction.execute(status ->
                    fixture.adapter.apply(context(17L, "UPDATE", "ACTIVE")));

            assertEquals(Long.valueOf(17), applied.resourceId());
            assertEquals(List.of("WRITE:17", "COMMIT", "REFRESH:17"), fixture.events);
            assertTrue("committed change and local invalidation failure must be visible in diagnostics",
                    appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("已提交")
                            && event.getFormattedMessage().contains("17")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static ApprovalApplyContext context(Long id, String action, String effectiveStatus) {
        return new ApprovalApplyContext(91L, id, 2, action,
                new ResourceSnapshot("{\"id\":999,\"datasourceCode\":\"editable_code\","
                        + "\"datasourceName\":\"可修改名称\",\"dbType\":\"MYSQL\"}",
                        effectiveStatus, null, null), "reviewer", "RESTORE".equals(action) ? 3L : null);
    }

    private static final class Fixture implements AutoCloseable {
        private final List<String> events = new ArrayList<>();
        private boolean rejectWrite;
        private boolean failRefresh;
        private RuleDbDatasource lastWritten;
        private final DBConnectPools pools = new DBConnectPools() {
            @Override public void refresh(Long id) {
                events.add("REFRESH:" + id);
                if (failRefresh) throw new IllegalStateException("test pool close failed");
            }
        };
        private final TransactionTemplate transaction;
        private final GovernedResourceAdapter adapter;

        private Fixture() throws Exception {
            DataSource source = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                    new Class[]{DataSource.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getConnection" -> connection();
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "database-governance-test-source";
                        default -> null;
                    });
            transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
            RuleDbDatasourceMapper mapper = (RuleDbDatasourceMapper) Proxy.newProxyInstance(
                    RuleDbDatasourceMapper.class.getClassLoader(), new Class[]{RuleDbDatasourceMapper.class},
                    (proxy, method, args) -> {
                        if ("insert".equals(method.getName()) || "updateById".equals(method.getName())) {
                            lastWritten = (RuleDbDatasource) args[0];
                            if ("insert".equals(method.getName())) lastWritten.setId(41L);
                            events.add("WRITE:" + lastWritten.getId());
                            return rejectWrite ? 0 : 1;
                        }
                        return null;
                    });
            Method factory = Arrays.stream(GovernanceResourceAdapterConfiguration.class.getDeclaredMethods())
                    .filter(method -> method.getName().equals("databaseGovernanceAdapter")).findFirst().orElseThrow();
            Object[] dependencies = Arrays.stream(factory.getParameterTypes()).map(type -> {
                if (type == RuleDbDatasourceMapper.class) return mapper;
                if (type == GovernanceSecretCodec.class) return new GovernanceSecretCodec(null);
                if (type == DBConnectPools.class) return pools;
                throw new AssertionError("Unexpected database adapter dependency: " + type.getSimpleName());
            }).toArray();
            adapter = (GovernedResourceAdapter) factory.invoke(new GovernanceResourceAdapterConfiguration(), dependencies);
        }

        private Connection connection() {
            AtomicBoolean autoCommit = new AtomicBoolean(true);
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAutoCommit" -> autoCommit.get();
                        case "setAutoCommit" -> { autoCommit.set((boolean) args[0]); yield null; }
                        case "isReadOnly", "isClosed" -> false;
                        case "commit" -> { events.add("COMMIT"); yield null; }
                        case "rollback" -> { events.add("ROLLBACK"); yield null; }
                        case "toString" -> "database-governance-transaction-test";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    });
        }

        @Override public void close() { pools.destroy(); }
    }
}
