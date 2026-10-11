package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExecutionPersistenceOutbox;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.mapper.RuleExecutionPersistenceOutboxMapper;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Assume;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.Reader;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ExecutionOutboxBatchOrderingTest {
    @Test
    public void recoveryBatchDoesNotBlockIndependentAdmissionWhileBusinessWritesArePending() throws Exception {
        assertIndependentAdmissionDuringBatch(true);
    }

    @Test
    public void notifiedBatchDoesNotBlockIndependentAdmissionWhileBusinessWritesArePending() throws Exception {
        assertIndependentAdmissionDuringBatch(false);
    }

    private void assertIndependentAdmissionDuringBatch(boolean recovery) throws Exception {
        Assume.assumeTrue("设置 -DexecutionPersistence.integration=true 后验证真实 MySQL 消费与接收隔离",
                Boolean.getBoolean("executionPersistence.integration"));
        // 模拟生产库默认 RR；消费者必须自行缩小锁范围，不能依赖修改数据库全局配置。
        DataSource source = new DelegatingDataSource(localDataSource()) {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                return connection;
            }
        };
        String table = "it_exec_outbox_" + UUID.randomUUID().toString().replace("-", "");
        if (!table.matches("it_exec_outbox_[a-f0-9]{32}")) throw new IllegalStateException("测试表名无效");
        try (Connection administration = source.getConnection()) {
            try (var ddl = administration.createStatement()) {
                ddl.execute("CREATE TABLE `" + table + "` LIKE rule_execution_persistence_outbox");
            }
            try {
                RuleExecutionPersistenceOutboxMapper mapper = isolatedMapper(source, table);
                DataSourceTransactionManager transactions = new DataSourceTransactionManager(source);
                RuleExecutionPersistenceOutboxService service = new RuleExecutionPersistenceOutboxService();
                ReflectionTestUtils.setField(service, "mapper", mapper);
                ReflectionTestUtils.setField(service, "transactionManager", transactions);
                CountDownLatch writingLog = new CountDownLatch(1);
                CountDownLatch finishLog = new CountDownLatch(1);
                AtomicInteger consumerIsolation = new AtomicInteger();
                ReflectionTestUtils.setField(service, "logService", new RuleExecutionLogService() {
                    @Override public void saveLogical(RuleExecutionLog log) {
                        try {
                            consumerIsolation.set(DataSourceUtils.getConnection(source).getTransactionIsolation());
                            writingLog.countDown();
                            if (!finishLog.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("测试日志写入未获释放");
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(interrupted);
                        } catch (SQLException failure) {
                            throw new IllegalStateException(failure);
                        }
                    }
                });
                Long original = accept(service, "existing");
                var workers = Executors.newFixedThreadPool(2);
                var consumer = workers.submit(() -> recovery ? service.replayBatch() : service.deliverBatch(List.of(original)));
                java.util.concurrent.Future<Long> producer = null;
                try {
                    assertTrue("消费者应进入业务写入并保持批次事务未提交", writingLog.await(5, TimeUnit.SECONDS));
                    CountDownLatch accepting = new CountDownLatch(1);
                    producer = workers.submit(() -> {
                        TransactionTemplate transaction = new TransactionTemplate(transactions);
                        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                        return transaction.execute(status -> {
                            accepting.countDown();
                            return accept(service, "new-arrival");
                        });
                    });
                    assertTrue(accepting.await(5, TimeUnit.SECONDS));
                    Long accepted;
                    try {
                        accepted = producer.get(2, TimeUnit.SECONDS);
                    } catch (TimeoutException blocked) {
                        try (PreparedStatement locks = administration.prepareStatement("SELECT INDEX_NAME, LOCK_MODE, LOCK_STATUS "
                                + "FROM performance_schema.data_locks WHERE OBJECT_SCHEMA = DATABASE() AND OBJECT_NAME = ?")) {
                            locks.setString(1, table);
                            try (ResultSet rows = locks.executeQuery()) {
                                while (rows.next()) System.out.println("isolated-outbox-lock: " + rows.getString(1)
                                        + ' ' + rows.getString(2) + ' ' + rows.getString(3));
                            }
                        } catch (SQLException unavailable) {
                            System.out.println("isolated-outbox-lock-diagnostic-unavailable: " + unavailable.getSQLState());
                        }
                        throw new AssertionError("消费批次持锁期间，新事件独立事务不应等待范围锁释放，isolation="
                                + consumerIsolation.get(), blocked);
                    }
                    System.out.println("isolated-outbox-admission-completed: recovery=" + recovery
                            + ", consumer-isolation=" + consumerIsolation.get());
                    assertFalse("新事件必须在旧消费事务提交前完成接收", consumer.isDone());
                    assertEquals("新事件应已独立提交，对其他连接可见", "PENDING", mapper.selectById(accepted).getDeliveryStatus());
                    assertEquals("旧事件仍未完成消费提交", "PENDING", mapper.selectById(original).getDeliveryStatus());
                } finally {
                    finishLog.countDown();
                    workers.shutdown();
                    assertTrue("隔离测试工作线程必须退出后才能清理测试表", workers.awaitTermination(10, TimeUnit.SECONDS));
                }
                assertEquals(1, consumer.get().size());
                assertNotNull(producer);
                Long accepted = producer.get();
                assertEquals("DELIVERED", mapper.selectById(original).getDeliveryStatus());
                assertEquals("PENDING", mapper.selectById(accepted).getDeliveryStatus());
                assertEquals("新事件仍须由后续消费批次处理", accepted, service.replayBatch().get(0).getId());
                assertTrue(service.replayBatch().isEmpty());
            } finally {
                try (var ddl = administration.createStatement()) { ddl.execute("DROP TABLE `" + table + "`"); }
            }
        }
    }

    @Test
    public void billingOnlyEventsFollowDefinitionTraceOrderInsteadOfQueueId() {
        List<RuleExecutionPersistenceOutbox> rows = new ArrayList<>(List.of(billingEvent(1L, "trace-b"), billingEvent(2L, "trace-a")));
        List<String> billingOrder = new ArrayList<>();
        RuleExecutionPersistenceOutboxMapper mapper = (RuleExecutionPersistenceOutboxMapper) Proxy.newProxyInstance(
                RuleExecutionPersistenceOutboxMapper.class.getClassLoader(), new Class[]{RuleExecutionPersistenceOutboxMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "lockPendingBatch" -> new ArrayList<>(rows);
                    case "completeLockedBatch" -> rows.size();
                    default -> null;
                });
        // 此用例只观察写入锁序；实际事务和 SKIP LOCKED 使用下方 MySQL 用例验证。
        PlatformTransactionManager transaction = new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
            @Override public void commit(TransactionStatus status) { }
            @Override public void rollback(TransactionStatus status) { }
        };
        RuleExecutionPersistenceOutboxService service = new RuleExecutionPersistenceOutboxService();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "transactionManager", transaction);
        ReflectionTestUtils.setField(service, "billingService", new RuleBillingService() {
            @Override public void recordLegacyEngineExecution(RuleDefinition definition, boolean success, Long cost,
                    String error, ProjectAuthContext auth) { billingOrder.add(definition.getExecutionTraceId()); }
        });

        assertEquals(2, service.deliverBatch(List.of(1L, 2L)).size());
        assertEquals(List.of("trace-a", "trace-b"), billingOrder);
    }

    @Test
    public void recoverySkipsAnEntireLockedHeadBatchAndConsumesLaterDueEvents() throws Exception {
        Assume.assumeTrue("设置 -DexecutionPersistence.integration=true 后验证真实 MySQL SKIP LOCKED",
                Boolean.getBoolean("executionPersistence.integration"));
        DataSource source = localDataSource();
        String table = "it_exec_outbox_" + UUID.randomUUID().toString().replace("-", "");
        if (!table.matches("it_exec_outbox_[a-f0-9]{32}")) throw new IllegalStateException("测试表名无效");
        try (Connection administration = source.getConnection()) {
            try (var ddl = administration.createStatement()) {
                ddl.execute("CREATE TABLE `" + table + "` LIKE rule_execution_persistence_outbox");
            }
            try {
                RuleExecutionPersistenceOutboxMapper mapper = isolatedMapper(source, table);
                RuleExecutionPersistenceOutboxService service = new RuleExecutionPersistenceOutboxService();
                ReflectionTestUtils.setField(service, "mapper", mapper);
                ReflectionTestUtils.setField(service, "transactionManager", new DataSourceTransactionManager(source));
                List<String> deliveredTraces = new ArrayList<>();
                ReflectionTestUtils.setField(service, "logService", new RuleExecutionLogService() {
                    @Override public void saveLogical(RuleExecutionLog log) { deliveredTraces.add(log.getTraceId()); }
                });
                List<Long> head = new ArrayList<>();
                for (int i = 0; i < RuleExecutionPersistenceOutboxService.DELIVERY_BATCH_SIZE; i++) {
                    head.add(accept(service, "head-" + i));
                }
                Long tail = accept(service, "tail");
                try (Connection blocker = source.getConnection()) {
                    blocker.setAutoCommit(false);
                    try (PreparedStatement lock = blocker.prepareStatement("SELECT id FROM `" + table
                            + "` ORDER BY id LIMIT " + RuleExecutionPersistenceOutboxService.DELIVERY_BATCH_SIZE + " FOR UPDATE");
                         ResultSet locked = lock.executeQuery()) {
                        int count = 0;
                        while (locked.next()) count++;
                        assertEquals(RuleExecutionPersistenceOutboxService.DELIVERY_BATCH_SIZE, count);

                        List<RuleExecutionPersistenceOutbox> delivered = service.replayBatch();

                        assertEquals("锁住最旧整批不能挡住后面的可消费事件", 1, delivered.size());
                        assertEquals(tail, delivered.get(0).getId());
                        assertEquals(List.of("tail"), deliveredTraces);
                        assertEquals("DELIVERED", mapper.selectById(tail).getDeliveryStatus());
                        for (Long id : head) assertEquals("PENDING", mapper.selectById(id).getDeliveryStatus());
                        assertTrue("队首仍被锁住时不能重复投递尾部", service.replayBatch().isEmpty());
                    } finally {
                        blocker.rollback();
                    }
                }
                assertEquals(RuleExecutionPersistenceOutboxService.DELIVERY_BATCH_SIZE, service.replayBatch().size());
                assertEquals(RuleExecutionPersistenceOutboxService.DELIVERY_BATCH_SIZE + 1, deliveredTraces.size());
                assertTrue(service.replayBatch().isEmpty());
            } finally {
                try (var ddl = administration.createStatement()) { ddl.execute("DROP TABLE `" + table + "`"); }
            }
        }
    }

    private static RuleExecutionPersistenceOutbox billingEvent(long id, String trace) {
        RuleExecutionPersistenceOutbox event = new RuleExecutionPersistenceOutbox();
        event.setId(id);
        event.setLogPending(0);
        event.setBillingPending(1);
        event.setDefinitionJson("{\"executionTraceId\":\"" + trace + "\"}");
        event.setDeliveryStatus("PENDING");
        return event;
    }

    private static Long accept(RuleExecutionPersistenceOutboxService service, String trace) {
        return service.accept(new DurableExecutionPersistenceJournal.Entry(UUID.randomUUID().toString(),
                "{\"traceId\":\"" + trace + "\"}", null, true, 0L, null,
                null, null, null, null, null, null, null, null, true, false)).getId();
    }

    private static RuleExecutionPersistenceOutboxMapper isolatedMapper(DataSource source, String table) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        configuration.addInterceptor(new IsolatedOutboxTable(table));
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(source);
        factoryBean.setConfiguration(configuration);
        factoryBean.setTransactionFactory(new SpringManagedTransactionFactory());
        SqlSessionFactory factory = factoryBean.getObject();
        assertNotNull(factory);
        factory.getConfiguration().addMapper(RuleExecutionPersistenceOutboxMapper.class);
        return new SqlSessionTemplate(factory).getMapper(RuleExecutionPersistenceOutboxMapper.class);
    }

    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    private static final class IsolatedOutboxTable implements Interceptor {
        private final String table;
        private IsolatedOutboxTable(String table) { this.table = table; }
        @Override public Object intercept(Invocation invocation) throws Throwable {
            StatementHandler statement = (StatementHandler) invocation.getTarget();
            var sql = statement.getBoundSql();
            String original = sql.getSql();
            if (!original.contains("rule_execution_persistence_outbox")) {
                throw new IllegalStateException("隔离测试拒绝访问非 outbox 表");
            }
            SystemMetaObject.forObject(sql).setValue("sql", original.replace("rule_execution_persistence_outbox", "`" + table + "`"));
            return invocation.proceed();
        }
    }

    private static DataSource localDataSource() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("rule-engine-server"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("无法定位仓库根目录");
        Properties settings = new Properties();
        try (Reader reader = Files.newBufferedReader(root.resolve(".env"), StandardCharsets.UTF_8)) { settings.load(reader); }
        InetAddress address = InetAddress.getByName(required(settings, "MYSQL_HOST"));
        if (!address.isLoopbackAddress()) throw new IllegalStateException("事务集成测试只允许本机 MySQL");
        String database = required(settings, "MYSQL_DATABASE");
        if (!database.matches("[A-Za-z0-9_$-]+")) throw new IllegalStateException("MYSQL_DATABASE 格式无效");
        int port = Integer.parseInt(required(settings, "MYSQL_PORT"));
        if (port < 1 || port > 65535) throw new IllegalStateException("MYSQL_PORT 无效");
        String host = address.getHostAddress();
        if (host.contains(":")) host = '[' + host + ']';
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl("jdbc:mysql://" + host + ':' + port + '/' + database
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=10000");
        source.setUsername(required(settings, "MYSQL_USERNAME"));
        source.setPassword(required(settings, "MYSQL_PASSWORD"));
        return source;
    }

    private static String required(Properties settings, String key) {
        String value = settings.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("根 .env 缺少 " + key);
        return value;
    }
}
