package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleExecutionPersistenceOutbox;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.mapper.RuleExecutionPersistenceOutboxMapper;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Assume;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 使用本机服务配置 指向的本机 MySQL；显式开启后配置、连接或表结构异常必须使测试失败。 */
public class ExecutionPersistenceTransactionIntegrationTest {
    private static final ThreadLocal<String> TEST_TABLE = new ThreadLocal<>();

    @Before public void isolateOutboxFromRunningApplicationWorkers() throws Exception {
        if (!Boolean.getBoolean("executionPersistence.integration")) return;
        String table = "test_ep_txn_" + UUID.randomUUID().toString().replace("-", "");
        TEST_TABLE.set(table);
        try (Connection connection = physicalDataSource().getConnection(); var ddl = connection.createStatement()) {
            assertTransactionalTable(connection);
            // LIKE 复用真实表结构/索引/事务引擎，仅隔离异步工作节点可见的测试数据。
            ddl.execute("CREATE TABLE `" + table + "` LIKE rule_execution_persistence_outbox");
        }
    }

    @After public void removeIsolatedOutbox() throws Exception {
        String table = TEST_TABLE.get(); TEST_TABLE.remove();
        if (table == null) return;
        if (!table.matches("test_ep_txn_[a-f0-9]{32}")) throw new IllegalStateException("拒绝清理非本测试表");
        try (Connection connection = physicalDataSource().getConnection(); var ddl = connection.createStatement()) {
            ddl.execute("DROP TABLE IF EXISTS `" + table + "`");
        }
    }

    @Test
    public void billingSnapshotRoundTripsThroughMysqlAndClearsOnlyAfterDelivery() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("executionPersistence.integration"));
        DataSource dataSource = localDataSource();
        RuleExecutionPersistenceOutboxMapper outboxMapper = mapper(dataSource);
        var recordMapper = mapper(dataSource, com.hengshucredit.rule.server.mapper.RuleBillingRecordMapper.class);
        var price = new com.hengshucredit.rule.model.entity.RuleBillingConfig();
        price.setId(983489L);
        price.setBillingCode("snapshot-integration");
        price.setBillingName("计费快照集成测试");
        price.setUnitPrice(new java.math.BigDecimal("0.125"));
        java.util.concurrent.atomic.AtomicBoolean enabled = new java.util.concurrent.atomic.AtomicBoolean(true);
        RuleBillingService billing = new RuleBillingService() {
            @Override protected java.util.List<com.hengshucredit.rule.model.entity.RuleBillingConfig> findActiveEngineConfigs(
                    RuleDefinition definition, java.time.LocalDateTime time, Long project) {
                return enabled.get() ? java.util.List.of(price) : java.util.List.of();
            }
            @Override protected com.hengshucredit.rule.model.entity.RuleProject findProject(Long id) { return null; }
        };
        ReflectionTestUtils.setField(billing, "recordMapper", recordMapper);
        RuleExecutionPersistenceOutboxService outbox = deliveryService(outboxMapper,
                new DataSourceTransactionManager(dataSource));
        ReflectionTestUtils.setField(outbox, "billingService", billing);
        RuleDefinition definition = new RuleDefinition();
        definition.setId(983489L);
        definition.setRuleCode("snapshot-integration");
        definition.setExecutionTraceId(com.hengshucredit.rule.core.trace.TraceIdGenerator.generate("SC", "G", "0000"));
        definition.setExecutionAttemptNo(1);
        String snapshot = billing.captureEngineExecutionBilling(definition, true, 7L, null, null);
        java.util.List<String> owned = new java.util.ArrayList<>();
        try (Connection observer = dataSource.getConnection()) {
            try {
                enabled.set(false);
                for (int pass = 0; pass < 2; pass++) {
                    String eventId = UUID.randomUUID().toString();
                    owned.add(eventId);
                    RuleExecutionPersistenceOutbox accepted = outbox.accept(new DurableExecutionPersistenceJournal.Entry(
                            eventId, null, com.alibaba.fastjson.JSON.toJSONString(definition), true, 7L, null,
                            null, null, null, null, null, null, null, null, false, true, snapshot));
                    assertEquals(snapshot, outboxMapper.selectById(accepted.getId()).getBillingSnapshotJson());
                    if (pass == 0) outbox.deliverBatch(java.util.List.of(accepted.getId()));
                    else outbox.deliver(accepted.getId());
                    RuleExecutionPersistenceOutbox delivered = outboxMapper.selectById(accepted.getId());
                    assertEquals(delivered.getLastError(), "DELIVERED", delivered.getDeliveryStatus());
                    org.junit.Assert.assertNull(delivered.getBillingSnapshotJson());
                }
                try (PreparedStatement query = observer.prepareStatement(
                        "SELECT billing_config_id, billing_basis, amount FROM rule_billing_record WHERE root_trace_id = ?")) {
                    query.setString(1, definition.getExecutionTraceId());
                    try (ResultSet result = query.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals(983489L, result.getLong(1));
                        assertEquals("EXECUTION_SNAPSHOT", result.getString(2));
                        assertEquals(new java.math.BigDecimal("0.125000"), result.getBigDecimal(3));
                        org.junit.Assert.assertFalse("重复事件恢复不能重复收费", result.next());
                    }
                }
            } finally {
                try (PreparedStatement cleanup = observer.prepareStatement("DELETE FROM rule_billing_record WHERE root_trace_id = ?")) {
                    cleanup.setString(1, definition.getExecutionTraceId());
                    cleanup.executeUpdate();
                }
                deleteOwned(observer, owned);
            }
        }
    }

    @Test
    public void acceptedOutboxEventSurvivesOuterTransactionRollback() throws Exception {
        Assume.assumeTrue("设置 -DexecutionPersistence.integration=true 后运行真实 MySQL 事务验证",
                Boolean.getBoolean("executionPersistence.integration"));
        DataSource dataSource = localDataSource();
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        RuleExecutionPersistenceOutboxMapper mapper = mapper(dataSource);

        RuleExecutionPersistenceOutboxService target = new RuleExecutionPersistenceOutboxService();
        ReflectionTestUtils.setField(target, "mapper", mapper);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        interceptor.afterPropertiesSet();
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(interceptor);
        RuleExecutionPersistenceOutboxService proxied =
                (RuleExecutionPersistenceOutboxService) proxyFactory.getProxy();

        String acceptedId = UUID.randomUUID().toString();
        String rolledBackId = UUID.randomUUID().toString();
        AtomicReference<Long> acceptedDatabaseId = new AtomicReference<>();
        try (Connection observer = dataSource.getConnection()) {
            assertTrue("观察连接必须独立自动提交", observer.getAutoCommit());
            assertTransactionalTable(observer);
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    // 同一真实 mapper 的非代理调用加入外层事务，作为不能假通过的回滚对照。
                    RuleExecutionPersistenceOutbox control = target.accept(entry(rolledBackId));
                    RuleExecutionPersistenceOutbox accepted = proxied.accept(entry(acceptedId));
                    assertNotNull(control.getId());
                    assertNotNull(accepted.getId());
                    acceptedDatabaseId.set(accepted.getId());
                    assertNotNull("外层事务应看见自身未提交写入", mapper.selectById(control.getId()));
                    assertEquals("accept 返回前必须已独立提交", 1, count(observer, acceptedId));
                    assertEquals("独立连接不能看见外层未提交事件", 0, count(observer, rolledBackId));
                    status.setRollbackOnly();
                });
                assertEquals("外层回滚不能撤销已接受事件", 1, count(observer, acceptedId));
                assertEquals("真实外层事务必须确实回滚", 0, count(observer, rolledBackId));
                RuleExecutionPersistenceOutbox replayed = proxied.accept(entry(acceptedId));
                assertEquals("重复 event_id 应返回此前独立提交的事件", acceptedDatabaseId.get(), replayed.getId());
                assertEquals("真实唯一键冲突分支不能产生第二条事件", 1, count(observer, acceptedId));
            } finally {
                deleteTestEvents(observer, acceptedId, rolledBackId);
            }
        }
    }

    @Test
    public void synchronousFallbackCommitsEachSideAcrossBillingTransactionRollback() throws Exception {
        Assume.assumeTrue("设置 -DexecutionPersistence.integration=true 后运行真实 MySQL 事务验证",
                Boolean.getBoolean("executionPersistence.integration"));
        DataSource dataSource = localDataSource();
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        RuleExecutionPersistenceOutboxMapper mapper = mapper(dataSource);
        String logId = UUID.randomUUID().toString();
        String billingId = UUID.randomUUID().toString();
        AtomicInteger logAttempts = new AtomicInteger();
        AtomicInteger billingAttempts = new AtomicInteger();
        RuleExecutionLogService logs = new RuleExecutionLogService() {
            @Override
            public void saveLogical(RuleExecutionLog ignored) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                logAttempts.incrementAndGet();
                insertMarker(mapper, logId);
            }
        };
        RuleBillingService billing = new NoPriceBillingService() {
            @Override
            public void recordEngineExecutionSnapshot(String snapshotJson) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                int attempt = billingAttempts.incrementAndGet();
                insertMarker(mapper, billingId);
                if (attempt == 1) {
                    Connection current = DataSourceUtils.getConnection(dataSource);
                    try {
                        assertTrue("回滚必须作用于本次账单写入的真实事务连接",
                                DataSourceUtils.isConnectionTransactional(current, dataSource));
                        current.rollback();
                    } catch (SQLException failure) {
                        throw new IllegalStateException("模拟 MySQL 事务回滚失败", failure);
                    } finally {
                        DataSourceUtils.releaseConnection(current, dataSource);
                    }
                    throw new TransactionSystemException("模拟 MySQL 已回滚整个账单事务");
                }
            }
        };
        try (Connection observer = dataSource.getConnection();
             DecisionExecutionPersistence persistence = new DecisionExecutionPersistence(logs, billing, 100)) {
            assertTrue(observer.getAutoCommit());
            assertTransactionalTable(observer);
            ReflectionTestUtils.setField(persistence, "transactionManager", transactionManager);
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    persistence.offer(new RuleExecutionLog(), new RuleDefinition(), true, 0L, null, null);
                    assertEquals("日志独立提交不能被账单整事务回滚带走", 1, count(observer, logId));
                    assertEquals("账单重试必须在返回前独立提交", 1, count(observer, billingId));
                    status.setRollbackOnly();
                });
                assertEquals(1, logAttempts.get());
                assertEquals(2, billingAttempts.get());
                assertEquals(1, count(observer, logId));
                assertEquals(1, count(observer, billingId));
                assertEquals("SYNC_FALLBACK", persistence.snapshot().get("overflowStrategy"));
                assertEquals(1L, persistence.snapshot().get("durablyAccepted"));
                assertEquals(1L, persistence.snapshot().get("persisted"));
            } finally {
                deleteTestEvents(observer, logId, billingId);
            }
        }
    }

    @Test
    public void healthyDeliveryBatchUsesOneCommitWithoutAutocommitSideWrites() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("executionPersistence.integration"));
        CountingDataSource dataSource = new CountingDataSource(localDataSource());
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        RuleExecutionPersistenceOutboxMapper mapper = mapper(dataSource);
        RuleExecutionPersistenceOutboxService service = deliveryService(mapper, manager);
        java.util.List<String> owned = new java.util.ArrayList<>();
        java.util.List<Long> events = new java.util.ArrayList<>();
        try (Connection observer = dataSource.getTargetDataSource().getConnection()) {
            try {
                for (int i = 0; i < 4; i++) events.add(prepareDelivery(service, owned));
                dataSource.commits.set(0);
                dataSource.autocommitWrites.set(0);
                var delivered = service.deliverBatch(events);
                assertEquals(4, delivered.size());
                for (Long id : events) {
                    assertEquals("DELIVERED", mapper.selectById(id).getDeliveryStatus());
                    org.junit.Assert.assertNull(mapper.selectById(id).getLogJson());
                }
                for (String id : owned) assertEquals(1, count(observer, id));
                assertEquals("健康批次应一次提交所有业务写入和恢复事件确认", 1, dataSource.commits.get());
                assertEquals("不能在批次事务外逐条自动提交业务事实", 0, dataSource.autocommitWrites.get());
            } finally { deleteOwned(observer, owned); }
        }
    }

    @Test
    public void failedBatchRollsBackBeforeIndependentRecoveryWithoutDuplicatingTheHealthySide() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("executionPersistence.integration"));
        CountingDataSource dataSource = new CountingDataSource(localDataSource());
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        RuleExecutionPersistenceOutboxMapper mapper = mapper(dataSource);
        RuleExecutionPersistenceOutboxService service = deliveryService(mapper, manager);
        java.util.List<String> owned = new java.util.ArrayList<>();
        try (Connection observer = dataSource.getTargetDataSource().getConnection()) {
            try {
                Long first = prepareDelivery(service, owned);
                Long second = prepareDelivery(service, owned);
                boolean firstIsGood = owned.get(1).compareToIgnoreCase(owned.get(3)) < 0;
                Long good = firstIsGood ? first : second;
                Long bad = firstIsGood ? second : first;
                String goodMarker = owned.get(firstIsGood ? 1 : 3);
                String badMarker = owned.get(firstIsGood ? 3 : 1);
                AtomicInteger healthyWrites = new AtomicInteger();
                ReflectionTestUtils.setField(service, "logService", new RuleExecutionLogService() {
                    @Override public void saveLogical(RuleExecutionLog log) {
                        if (badMarker.equals(log.getTraceId())) throw new IllegalStateException("injected log write failure");
                        assertEquals(goodMarker, log.getTraceId());
                        insertMarker(mapper, log.getTraceId());
                        healthyWrites.incrementAndGet();
                    }
                });
                service.deliverBatch(java.util.List.of(good, bad));
                assertEquals("DELIVERED", mapper.selectById(good).getDeliveryStatus());
                assertEquals("PENDING", mapper.selectById(bad).getDeliveryStatus());
                assertEquals(Integer.valueOf(1), mapper.selectById(bad).getRetryCount());
                assertEquals("健康写入应先在失败批次发生，再于回滚后恢复", 2, healthyWrites.get());
                assertEquals(1, count(observer, goodMarker));
                assertEquals(0, count(observer, badMarker));
                assertTrue("失败批次必须回滚后才能尝试逐条恢复", dataSource.rollbacks.get() > 0);
            } finally { deleteOwned(observer, owned); }
        }
    }

    @Test
    public void concurrentDeliverySkipsLockedEventsAndDoesNotWriteThemTwice() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("executionPersistence.integration"));
        DataSource dataSource = localDataSource();
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        RuleExecutionPersistenceOutboxMapper mapper = mapper(dataSource);
        RuleExecutionPersistenceOutboxService service = deliveryService(mapper, manager);
        java.util.List<String> owned = new java.util.ArrayList<>();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        AtomicInteger writes = new AtomicInteger();
        ReflectionTestUtils.setField(service, "logService", new RuleExecutionLogService() {
            @Override public void saveLogical(RuleExecutionLog log) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                writes.incrementAndGet();
                insertMarker(mapper, log.getTraceId());
                entered.countDown();
                try {
                    assertTrue("测试应释放占用的事务", release.await(10, java.util.concurrent.TimeUnit.SECONDS));
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
            }
        });
        try (Connection observer = dataSource.getConnection()) {
            try {
                Long event = prepareDelivery(service, owned);
                var first = workers.submit(() -> service.deliverBatch(java.util.List.of(event)));
                assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
                var competitor = workers.submit(() -> service.deliverBatch(java.util.List.of(event)));
                assertTrue("竞争消费者必须跳过已锁定事件", competitor.get(3, java.util.concurrent.TimeUnit.SECONDS).isEmpty());
                assertEquals("批次提交前业务写入不能被独立观察者看见", 0, count(observer, owned.get(1)));
                release.countDown();
                assertEquals(1, first.get(5, java.util.concurrent.TimeUnit.SECONDS).size());
                assertTrue("已完成事件不能再次消费", service.deliverBatch(java.util.List.of(event)).isEmpty());
                assertEquals(1, writes.get());
                assertEquals(1, count(observer, owned.get(1)));
                assertEquals("DELIVERED", mapper.selectById(event).getDeliveryStatus());
            } finally {
                release.countDown();
                workers.shutdown();
                assertTrue(workers.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));
                deleteOwned(observer, owned);
            }
        }
    }

    @Test
    public void incompleteBatchAcknowledgementRollsBackBusinessWritesBeforeRecovery() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("executionPersistence.integration"));
        CountingDataSource dataSource = new CountingDataSource(localDataSource());
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        RuleExecutionPersistenceOutboxMapper actualMapper = mapper(dataSource);
        AtomicInteger acknowledgements = new AtomicInteger();
        RuleExecutionPersistenceOutboxMapper interruptedAck = (RuleExecutionPersistenceOutboxMapper)
                java.lang.reflect.Proxy.newProxyInstance(RuleExecutionPersistenceOutboxMapper.class.getClassLoader(),
                        new Class<?>[]{RuleExecutionPersistenceOutboxMapper.class}, (proxy, method, args) -> {
                            Object result = CountingDataSource.invoke(method, actualMapper, args);
                            if (method.getName().equals("completeLockedBatch")) {
                                acknowledgements.incrementAndGet();
                                return 0; // SQL已执行，但批次确认未满足完整性契约。
                            }
                            return result;
                        });
        RuleExecutionPersistenceOutboxService service = deliveryService(interruptedAck, manager);
        java.util.List<String> owned = new java.util.ArrayList<>();
        try (Connection observer = dataSource.getTargetDataSource().getConnection()) {
            try {
                Long event = prepareDelivery(service, owned);
                var delivered = service.deliverBatch(java.util.List.of(event));
                assertEquals(1, acknowledgements.get());
                assertEquals("未完整确认的批次必须真正回滚", 1, dataSource.rollbacks.get());
                assertEquals(1, delivered.size());
                assertEquals("DELIVERED", actualMapper.selectById(event).getDeliveryStatus());
                assertEquals("逐条恢复成功证明前次未留下重复业务事实", 1, count(observer, owned.get(1)));
            } finally { deleteOwned(observer, owned); }
        }
    }

    private static RuleExecutionPersistenceOutboxService deliveryService(RuleExecutionPersistenceOutboxMapper mapper,
            DataSourceTransactionManager manager) {
        RuleExecutionPersistenceOutboxService service = new RuleExecutionPersistenceOutboxService();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "transactionManager", manager);
        ReflectionTestUtils.setField(service, "logService", new RuleExecutionLogService() {
            @Override public void saveLogical(RuleExecutionLog log) {
                insertMarker(mapper, log.getTraceId());
            }
        });
        ReflectionTestUtils.setField(service, "billingService", new RuleBillingService());
        return service;
    }

    private static Long prepareDelivery(RuleExecutionPersistenceOutboxService service, java.util.List<String> owned) {
        String event = UUID.randomUUID().toString();
        String marker = UUID.randomUUID().toString();
        owned.add(event);
        owned.add(marker);
        RuleExecutionPersistenceOutbox accepted = service.accept(new DurableExecutionPersistenceJournal.Entry(
                event, "{\"traceId\":\"" + marker + "\"}", null, true, 0L, "batch integration test",
                null, null, null, null, null, null, null, null, true, false));
        return accepted.getId();
    }

    private static void deleteOwned(Connection observer, java.util.List<String> owned) throws SQLException {
        try (PreparedStatement statement = observer.prepareStatement(
                "DELETE FROM rule_execution_persistence_outbox WHERE event_id = ?")) {
            for (String id : owned) { statement.setString(1, id); statement.executeUpdate(); }
        }
        for (String id : owned) assertEquals("测试事件与业务标记应全部清理", 0, count(observer, id));
    }

    private static final class CountingDataSource extends org.springframework.jdbc.datasource.DelegatingDataSource {
        final AtomicInteger commits = new AtomicInteger();
        final AtomicInteger rollbacks = new AtomicInteger();
        final AtomicInteger autocommitWrites = new AtomicInteger();
        CountingDataSource(DataSource delegate) { super(delegate); }
        @Override public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        Object value = invoke(method, connection, args);
                        if (method.getName().equals("commit")) commits.incrementAndGet();
                        if (method.getName().equals("rollback")) rollbacks.incrementAndGet();
                        if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                                && sql.stripLeading().matches("(?is)^(INSERT|UPDATE|DELETE)\\b.*")) {
                            PreparedStatement statement = (PreparedStatement) value;
                            return java.lang.reflect.Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                                    new Class<?>[]{PreparedStatement.class}, (ignored, operation, parameters) -> {
                                        Object result = invoke(operation, statement, parameters);
                                        if (java.util.Set.of("execute", "executeUpdate", "executeLargeUpdate", "executeBatch").contains(operation.getName())
                                                && connection.getAutoCommit()) autocommitWrites.incrementAndGet();
                                        return result;
                                    });
                        }
                        return value;
                    });
        }
        private static Object invoke(java.lang.reflect.Method method, Object target, Object[] args) throws Throwable {
            try { return method.invoke(target, args); }
            catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
        }
    }

    private static RuleExecutionPersistenceOutboxMapper mapper(DataSource dataSource) throws Exception {
        return mapper(dataSource, RuleExecutionPersistenceOutboxMapper.class);
    }

    private static <T> T mapper(DataSource dataSource, Class<T> type) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        factoryBean.setGlobalConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig()
                .setMetaObjectHandler(new com.hengshucredit.rule.server.config.MybatisPlusConfig().metaObjectHandler()));
        factoryBean.setTransactionFactory(new SpringManagedTransactionFactory());
        SqlSessionFactory factory = factoryBean.getObject();
        assertNotNull(factory);
        factory.getConfiguration().addMapper(type);
        return new SqlSessionTemplate(factory).getMapper(type);
    }

    private static void insertMarker(RuleExecutionPersistenceOutboxMapper mapper, String eventId) {
        RuleExecutionPersistenceOutbox marker = new RuleExecutionPersistenceOutbox();
        marker.setEventId(eventId);
        marker.setLogPending(0);
        marker.setBillingPending(0);
        marker.setErrorMessage("transaction integration test");
        assertEquals(1, mapper.insert(marker));
    }

    private static void deleteTestEvents(Connection connection, String firstId, String secondId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM rule_execution_persistence_outbox WHERE event_id IN (?, ?)")) {
            statement.setString(1, firstId);
            statement.setString(2, secondId);
            statement.executeUpdate();
        }
        assertEquals("仅本测试事件应已清理", 0, count(connection, firstId));
        assertEquals("仅本测试事件应已清理", 0, count(connection, secondId));
    }

    private static DurableExecutionPersistenceJournal.Entry entry(String eventId) {
        // 不调度日志和计费副作用，即使本地另有服务运行也仅处理这条测试 outbox 记录。
        return new DurableExecutionPersistenceJournal.Entry(eventId, null, null,
                true, 0L, "transaction integration test", null, null, null,
                null, null, null, null, null, false, false);
    }

    private static int count(Connection connection, String eventId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM rule_execution_persistence_outbox WHERE event_id = ?")) {
            statement.setString(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("独立连接读取测试 outbox 事件失败", failure);
        }
    }

    private static void assertTransactionalTable(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT ENGINE FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name = 'rule_execution_persistence_outbox'");
             ResultSet result = statement.executeQuery()) {
            assertTrue("需要本机已初始化的 outbox 结构；不会修改原表", result.next());
            assertEquals("原表及其测试克隆必须支持真实事务", "InnoDB", result.getString(1));
        }
    }

    private static DataSource localDataSource() throws Exception {
        DataSource physical = physicalDataSource();
        String table = TEST_TABLE.get();
        if (table == null) throw new IllegalStateException("测试 outbox 尚未隔离");
        return new org.springframework.jdbc.datasource.DelegatingDataSource(physical) {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                        (proxy, method, args) -> {
                            if ("prepareStatement".equals(method.getName()) && args[0] instanceof String sql) {
                                Object[] rewritten = args.clone();
                                rewritten[0] = sql.replace("rule_execution_persistence_outbox", table);
                                return CountingDataSource.invoke(method, connection, rewritten);
                            }
                            return CountingDataSource.invoke(method, connection, args);
                        });
            }
        };
    }

    private static DataSource physicalDataSource() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("rule-engine-server"))) {
            root = root.getParent();
        }
        if (root == null) throw new IllegalStateException("无法定位仓库根目录");
        Properties settings = LocalIntegrationSettings.load(root);
        String host = required(settings, "MYSQL_HOST");
        InetAddress address = InetAddress.getByName(host);
        if (!address.isLoopbackAddress()) {
            throw new IllegalStateException("事务集成测试仅允许本机服务配置 的 MYSQL_HOST 指向本机");
        }
        int port = Integer.parseInt(required(settings, "MYSQL_PORT"));
        if (port < 1 || port > 65535) throw new IllegalStateException("MYSQL_PORT 无效");
        String database = required(settings, "MYSQL_DATABASE");
        if (!database.matches("[A-Za-z0-9_$-]+")) {
            throw new IllegalStateException("MYSQL_DATABASE 格式无效");
        }
        String endpoint = address.getHostAddress();
        if (endpoint.contains(":")) endpoint = '[' + endpoint + ']';
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://" + endpoint + ':' + port + '/' + database
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                + "&connectTimeout=5000&socketTimeout=10000");
        dataSource.setUsername(required(settings, "MYSQL_USERNAME"));
        dataSource.setPassword(required(settings, "MYSQL_PASSWORD"));
        return dataSource;
    }

    private static String required(Properties settings, String key) {
        String value = settings.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("本机服务配置 缺少 " + key);
        return value;
    }
}
