package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hengshucredit.rule.model.entity.RuleBillingRecord;
import com.hengshucredit.rule.server.mapper.RuleBillingRecordMapper;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.Assume;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class BillingAttemptConcurrencyIntegrationTest {
    @Test
    public void anOlderReadCannotOverwriteANewerCommittedAttemptWithoutAnOuterTransaction() throws Exception {
        try (Fixture fixture = fixture()) {
            for (Integer incomingAttempt : java.util.Arrays.asList(2, null)) {
                String trace = UUID.randomUUID().toString();
                Probe newest = fixture.service();
                newest.write(record(trace, incomingAttempt == null ? null : 1, "1", "initial-error"));
                CountDownLatch oldRead = new CountDownLatch(1);
                CountDownLatch allowOldUpdate = new CountDownLatch(1);
                AtomicBoolean pause = new AtomicBoolean(true);
                Probe older = new Probe() {
                    @Override protected RuleBillingRecord findByBillingDedupKey(String key) {
                        RuleBillingRecord result = super.findByBillingDedupKey(key);
                        if (pause.compareAndSet(true, false)) {
                            oldRead.countDown();
                            try { assertTrue(allowOldUpdate.await(8, TimeUnit.SECONDS)); }
                            catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(interrupted);
                            }
                        }
                        return result;
                    }
                };
                ReflectionTestUtils.setField(older, "recordMapper", fixture.mapper);
                var workers = Executors.newFixedThreadPool(2);
                try {
                    var low = workers.submit(() -> older.write(record(trace, incomingAttempt, "2", "older-error")));
                    assertTrue("旧消费者必须确实先读到旧账单", oldRead.await(5, TimeUnit.SECONDS));
                    workers.submit(() -> newest.write(record(trace, 3, "9", null))).get(5, TimeUnit.SECONDS);
                    RuleBillingRecord confirmedNewest = fixture.stored(trace);
                    assertEquals(Integer.valueOf(3), confirmedNewest.getAttemptNo());
                    assertNull("胜出的成功快照不能保留旧错误", confirmedNewest.getErrorMessage());
                    assertEquals(new BigDecimal("9.000000"), confirmedNewest.getAmount());
                    allowOldUpdate.countDown();
                    low.get(5, TimeUnit.SECONDS);

                    assertFacts("incoming attempt=" + incomingAttempt, confirmedNewest, fixture.stored(trace));
                } finally {
                    allowOldUpdate.countDown();
                    workers.shutdownNow();
                    assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
                }
            }
        }
    }

    @Test
    public void affectedRowsModeAcceptsAnActuallyIdenticalReplayAfterDatabasePrecisionConversion() throws Exception {
        try (Fixture fixture = fixture()) {
            Probe billing = fixture.service();
            String trace = UUID.randomUUID().toString();
            RuleBillingRecord first = record(trace, 2, "1.12345678", null);
            first.setOccurTime(LocalDateTime.of(2026, 10, 8, 12, 0, 0, 765432100));
            first.setProjectCode("Old_Project");
            first.setAuthCode("OLD_AUTH");
            first.setTokenCode("Old_Token");
            first.setRequestId("request-before");
            first.setDatasourceCode("Legacy_DS");
            billing.write(first);
            RuleBillingRecord stored = fixture.stored(trace);
            int before = fixture.zeroUpdates.get();

            try { billing.write(copy(first)); }
            catch (RuntimeException failure) {
                throw new AssertionError("已入库且事实相同的同attempt重放不应失败", failure);
            }

            assertTrue("useAffectedRows=true必须实际产生0行UPDATE，不能用匹配行模式假通过",
                    fixture.zeroUpdates.get() > before);
            assertFacts("same attempt and facts", stored, fixture.stored(trace));
            RuleBillingRecord equalAttemptNewFacts = record(trace, 2, "8", "same-attempt-new-result");
            billing.write(equalAttemptNewFacts);
            assertEquals(new BigDecimal("8.000000"), fixture.stored(trace).getAmount());
            assertEquals("same-attempt-new-result", fixture.stored(trace).getErrorMessage());
            assertNull(fixture.stored(trace).getProjectCode());
            assertNull(fixture.stored(trace).getAuthCode());
            assertNull(fixture.stored(trace).getTokenCode());
            assertNull(fixture.stored(trace).getRequestId());
            assertNull(fixture.stored(trace).getDatasourceCode());
        }
    }

    @Test
    public void aZeroWriteCannotConfirmDifferentFactsOrAnUnwrittenHigherAttempt() throws Exception {
        try (Fixture fixture = fixture()) {
            for (int scenario : List.of(0, 1, 2)) {
                int incomingAttempt = scenario == 1 ? 2 : 1;
                Probe billing = fixture.service();
                String trace = UUID.randomUUID().toString();
                billing.write(record(trace, 1, "1", "before"));
                RuleBillingRecord original = fixture.stored(trace);
                fixture.suppressUpdate.set(true);
                try {
                    assertThrows("未执行的UPDATE不能仅因attempt相同就确认不同事实", IllegalStateException.class,
                            () -> billing.write(record(trace, incomingAttempt, scenario == 1 ? "7" : "1",
                                    scenario == 2 ? "BEFORE" : "unwritten")));
                    assertFacts("unconfirmed write", original, fixture.stored(trace));
                } finally { fixture.suppressUpdate.set(false); }
            }
        }
    }

    @Test
    public void aRepeatableReadSnapshotCannotTurnASupersededUpdateIntoFailureOrOverwrite() throws Exception {
        try (Fixture fixture = fixture()) {
            String trace = UUID.randomUUID().toString();
            Probe billing = fixture.service();
            billing.write(record(trace, 1, "1", "before"));
            RuleBillingRecord first = fixture.stored(trace);
            TransactionTemplate outer = new TransactionTemplate(new DataSourceTransactionManager(fixture.source));
            outer.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            var writer = Executors.newSingleThreadExecutor();
            try {
                outer.executeWithoutResult(status -> {
                    assertEquals(Integer.valueOf(1), fixture.mapper.selectById(first.getId()).getAttemptNo());
                    try { writer.submit(() -> fixture.service().write(record(trace, 3, "9", null))).get(5, TimeUnit.SECONDS); }
                    catch (Exception error) { throw new IllegalStateException(error); }
                    int zeroBefore = fixture.zeroUpdates.get();
                    billing.write(record(trace, 3, "9", null));
                    assertTrue("0行确认必须用current-read而不是事务已建立的旧RR视图", fixture.zeroUpdates.get() > zeroBefore);
                    // 旧RR视图仍能读到attempt1；UPDATE与0行后的确认必须依据最新行。
                    billing.write(record(trace, 2, "2", "stale"));
                });
                RuleBillingRecord latest = fixture.stored(trace);
                assertEquals(Integer.valueOf(3), latest.getAttemptNo());
                assertEquals(new BigDecimal("9.000000"), latest.getAmount());
                assertNull(latest.getErrorMessage());
            } finally {
                writer.shutdownNow();
                assertTrue(writer.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
    }

    private static class Probe extends RuleBillingService {
        void write(RuleBillingRecord record) { persistEngineBillingRecord(record); }
    }

    private static RuleBillingRecord record(String trace, Integer attempt, String price, String error) {
        RuleBillingRecord record = new RuleBillingRecord();
        record.setRootTraceId(trace);
        record.setBillingConfigId(934811L);
        record.setBillingBasis("EXECUTION_SNAPSHOT");
        record.setBillingCode("attempt-integration");
        record.setBillingName("并发尝试计费测试");
        record.setBillingTarget("ENGINE");
        record.setAttemptNo(attempt);
        record.setQuantity(BigDecimal.ONE);
        record.setUnitPrice(new BigDecimal(price));
        record.setAmount(new BigDecimal(price).setScale(6, java.math.RoundingMode.HALF_UP));
        record.setCurrency("CNY");
        record.setSuccess(error == null ? 1 : 0);
        record.setCostTimeMs(10L);
        record.setErrorMessage(error);
        record.setOccurTime(LocalDateTime.of(2026, 10, 8, 12, 0).plusSeconds(attempt == null ? 1 : attempt));
        // 独立MyBatis工厂没有Spring MetaObjectHandler，显式提供生产中自动填充的字段。
        record.setCreateTime(LocalDateTime.of(2026, 10, 8, 12, 0));
        return record;
    }

    private static RuleBillingRecord copy(RuleBillingRecord record) {
        RuleBillingRecord copy = com.alibaba.fastjson.JSON.parseObject(com.alibaba.fastjson.JSON.toJSONString(record), RuleBillingRecord.class);
        copy.setId(null);
        return copy;
    }

    private static void assertFacts(String message, RuleBillingRecord expected, RuleBillingRecord actual) {
        assertEquals(message, expected.getAttemptNo(), actual.getAttemptNo());
        assertEquals(message, expected.getUnitPrice(), actual.getUnitPrice());
        assertEquals(message, expected.getAmount(), actual.getAmount());
        assertEquals(message, expected.getOccurTime(), actual.getOccurTime());
        assertEquals(message, expected.getErrorMessage(), actual.getErrorMessage());
        assertEquals(message, expected.getSuccess(), actual.getSuccess());
        assertEquals(message, expected.getBillingConfigId(), actual.getBillingConfigId());
        assertEquals(message, expected.getBillingBasis(), actual.getBillingBasis());
    }

    private Fixture fixture() throws Exception {
        Assume.assumeTrue("显式开启后仅使用本机MySQL与UUID隔离账单表", Boolean.getBoolean("executionPersistence.integration"));
        return new Fixture();
    }

    private static final class Fixture implements AutoCloseable {
        final DataSource source = localDataSource();
        final String table = "it_billing_attempt_" + UUID.randomUUID().toString().replace("-", "");
        final AtomicBoolean suppressUpdate = new AtomicBoolean();
        final AtomicInteger zeroUpdates = new AtomicInteger();
        final RuleBillingRecordMapper mapper;

        Fixture() throws Exception {
            if (!table.matches("it_billing_attempt_[a-f0-9]{32}")) throw new IllegalStateException("隔离表名无效");
            try (Connection connection = source.getConnection(); var statement = connection.createStatement()) {
                assertTrue(connection.getAutoCommit());
                statement.execute("CREATE TABLE `" + table + "` LIKE rule_billing_record");
            }
            try {
                MybatisConfiguration configuration = new MybatisConfiguration();
                configuration.setMapUnderscoreToCamelCase(true);
                configuration.setLogImpl(NoLoggingImpl.class);
                configuration.addInterceptor(new IsolatedBillingSql(table, suppressUpdate, zeroUpdates));
                MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
                factory.setDataSource(source);
                factory.setConfiguration(configuration);
                factory.setTransactionFactory(new SpringManagedTransactionFactory());
                var sessions = factory.getObject();
                assertNotNull(sessions);
                sessions.getConfiguration().addMapper(RuleBillingRecordMapper.class);
                mapper = new SqlSessionTemplate(sessions).getMapper(RuleBillingRecordMapper.class);
            } catch (Exception failed) {
                close();
                throw failed;
            }
        }

        Probe service() {
            Probe billing = new Probe();
            ReflectionTestUtils.setField(billing, "recordMapper", mapper);
            return billing;
        }

        RuleBillingRecord stored(String trace) {
            var records = mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RuleBillingRecord>()
                    .eq(RuleBillingRecord::getRootTraceId, trace));
            assertEquals("同一执行和配置只能保留一条账单", 1, records.size());
            return records.get(0);
        }

        @Override public void close() throws Exception {
            try (Connection connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP TABLE `" + table + "`");
            }
        }
    }

    @Intercepts({
            @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}),
            @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})})
    private static final class IsolatedBillingSql implements Interceptor {
        private final String table;
        private final AtomicBoolean suppress;
        private final AtomicInteger zeros;
        IsolatedBillingSql(String table, AtomicBoolean suppress, AtomicInteger zeros) {
            this.table = table; this.suppress = suppress; this.zeros = zeros;
        }
        @Override public Object intercept(Invocation invocation) throws Throwable {
            if (invocation.getTarget() instanceof StatementHandler statement) {
                var sql = statement.getBoundSql();
                String original = sql.getSql();
                if (!original.contains("rule_billing_record")) throw new IllegalStateException("隔离测试拒绝访问非账单表");
                SystemMetaObject.forObject(sql).setValue("sql", original.replace("rule_billing_record", "`" + table + "`"));
                return invocation.proceed();
            }
            boolean update = ((MappedStatement) invocation.getArgs()[0]).getSqlCommandType() == SqlCommandType.UPDATE;
            if (update && suppress.get()) { zeros.incrementAndGet(); return 0; }
            Object result = invocation.proceed();
            if (update && Integer.valueOf(0).equals(result)) zeros.incrementAndGet();
            return result;
        }
    }

    private static DataSource localDataSource() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("rule-engine-server"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("无法定位仓库根目录");
        Properties settings = new Properties();
        try (Reader reader = Files.newBufferedReader(root.resolve(".env"), StandardCharsets.UTF_8)) { settings.load(reader); }
        InetAddress address = InetAddress.getByName(required(settings, "MYSQL_HOST"));
        if (!address.isLoopbackAddress()) throw new IllegalStateException("只允许本机MySQL");
        String database = required(settings, "MYSQL_DATABASE");
        if (!database.matches("[A-Za-z0-9_$-]+")) throw new IllegalStateException("数据库名无效");
        int port = Integer.parseInt(required(settings, "MYSQL_PORT"));
        if (port < 1 || port > 65535) throw new IllegalStateException("数据库端口无效");
        String host = address.getHostAddress();
        if (host.contains(":")) host = '[' + host + ']';
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl("jdbc:mysql://" + host + ':' + port + '/' + database
                + "?serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=10000&useAffectedRows=true");
        source.setUsername(required(settings, "MYSQL_USERNAME"));
        source.setPassword(required(settings, "MYSQL_PASSWORD"));
        return source;
    }

    private static String required(Properties settings, String key) {
        String value = settings.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("根.env缺少 " + key);
        return value;
    }
}
