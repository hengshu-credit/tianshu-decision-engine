package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hengshucredit.rule.model.entity.RuleBillingRecord;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.config.MybatisPlusConfig;
import com.hengshucredit.rule.server.mapper.RuleBillingRecordMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleRuntimeCallLogMapper;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.Assume;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** 真实36字符列契约：只克隆UUID隔离表，实际service/mapper写入，不启动应用或修改业务行。 */
public class ExternalApiTrialIdentityIntegrationTest {
    @Test public void realTrialBillingFitsRootColumnAndReplayKeepsFirstPriceTimeAndFailure() throws Exception {
        try (Fixture f = fixture()) {
            ExternalApiTrialStore.State state = f.acceptSnapshot();
            RuleExternalApiConfig config = f.api(-state.id() * 65536L, state.trialId());
            config.setUnitPrice(new BigDecimal("0.250000"));
            AtomicInteger attempts = new AtomicInteger();
            LocalDateTime firstTime = LocalDateTime.of(2026, 10, 9, 1, 2, 3);
            RuleBillingService billing = new RuleBillingService() {
                @Override protected void insertRecord(RuleBillingRecord record) {
                    // 固定两次调用的时钟事实，避免同秒重放把错误UPDATE藏住。
                    record.setOccurTime(firstTime.plusDays(attempts.getAndIncrement()));
                    super.insertRecord(record);
                }
            };
            ReflectionTestUtils.setField(billing, "recordMapper", f.billingMapper);
            assertTrue(billing.recordApiTrialExecution(state.trialId(), config, f.source, false, 13L, "owned-first-failure"));
            RuleBillingRecord first = f.onlyBill();
            assertEquals(state.trialId(), first.getRootTraceId()); assertEquals(36, first.getRootTraceId().length());
            assertEquals("DRAFT_TRIAL", first.getBillingBasis()); assertNull(first.getTargetRefId());
            assertEquals(Integer.valueOf(0), first.getSuccess()); assertEquals(firstTime, first.getOccurTime());
            config.setUnitPrice(new BigDecimal("9.000000"));
            assertTrue(billing.recordApiTrialExecution(state.trialId(), config, f.source, true, 999L, null));
            RuleBillingRecord replayed = f.onlyBill();
            assertEquals(first.getId(), replayed.getId()); assertEquals(first.getBillingDedupKey(), replayed.getBillingDedupKey());
            assertEquals(first.getUnitPrice(), replayed.getUnitPrice()); assertEquals(first.getAmount(), replayed.getAmount());
            assertEquals(first.getOccurTime(), replayed.getOccurTime()); assertEquals(first.getSuccess(), replayed.getSuccess());
            assertEquals(first.getErrorMessage(), replayed.getErrorMessage()); assertEquals(first.getCostTimeMs(), replayed.getCostTimeMs());
        }
    }

    @Test public void realInvokerRootLogUsesTrialUuidAndFitsItsActualDatabaseColumn() throws Exception {
        try (Fixture f = fixture()) {
            ExternalApiTrialStore.State state = f.acceptSnapshot();
            try (ExternalApiTrialContext context = f.context(state)) {
                String callId = context.callId(context.rootIdentity);
                f.writeActualInvokerLog(f.api(context.rootIdentity, callId), callId);
                List<RuleRuntimeCallLog> logs = f.runtimeMapper.selectList(null);
                assertEquals(1, logs.size());
                assertEquals(state.trialId(), logs.get(0).getCallId()); assertEquals(state.trialId(), logs.get(0).getRootTraceId());
                assertEquals("API_DRAFT_INVOKE", logs.get(0).getActionType()); assertNull(logs.get(0).getTargetRefId());
            }
        }
    }

    @Test public void actualStepLogsUsePersistedWinningSnapshotUuidsAcrossReloadAndDuplicateCreate() throws Exception {
        try (Fixture f = fixture()) {
            ExternalApiTrialStore.State first = f.acceptSnapshot();
            JSONObject frozen = JSON.parseObject(first.snapshotJson());
            List<String> actualIds = new ArrayList<>();
            try (ExternalApiTrialContext context = f.context(first)) {
                for (String step : List.of("first", "second")) {
                    long identity = context.stepIdentity(step); String callId = context.callId(identity);
                    f.writeActualInvokerLog(f.api(identity, callId), callId);
                    actualIds.add(callId);
                }
            }
            assertNotEquals(actualIds.get(0), actualIds.get(1));
            for (String id : actualIds) { assertEquals(36, id.length()); assertEquals(id, UUID.fromString(id).toString()); assertNotEquals(first.trialId(), id); }
            assertNotNull("步骤UUID必须来自执行前已持久化的冻结快照", frozen.getJSONObject("stepCallIds"));
            assertEquals(actualIds, List.of(frozen.getJSONObject("stepCallIds").getString("first"), frozen.getJSONObject("stepCallIds").getString("second")));
            var winner = f.store.create(f.owner, f.requestKey, 17L, "a".repeat(64), f.freeze().toJSONString(), 3600);
            assertEquals(first.snapshotJson(), winner.snapshotJson()); assertEquals(first.expiresAt(), winner.expiresAt());
            ExternalApiTrialStore.State reloaded = f.newStore().get(first.trialId(), f.owner);
            try (ExternalApiTrialContext context = f.context(reloaded)) {
                assertEquals(actualIds.get(0), context.callId(context.stepIdentity("first")));
                assertEquals(actualIds.get(1), context.callId(context.stepIdentity("second")));
            }
            List<RuleRuntimeCallLog> logs = f.runtimeMapper.selectList(null);
            assertEquals(2, logs.size());
            assertTrue(logs.stream().allMatch(log -> first.trialId().equals(log.getRootTraceId()) && actualIds.contains(log.getCallId())));
        }
    }

    private static Fixture fixture() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("externalApiTrial.integration"));
        return new Fixture();
    }
    private static final class Fixture implements AutoCloseable {
        final DataSource datasource = localDataSource();
        final JdbcTemplate jdbc = new JdbcTemplate(datasource);
        final Map<String, String> tables = new LinkedHashMap<>();
        final List<String> createdTables = new ArrayList<>();
        final String owner = "owned-identity-" + UUID.randomUUID(), requestKey = UUID.randomUUID().toString();
        final RuleExternalDatasource source = new RuleExternalDatasource();
        final CredentialCipher cipher;
        final ExternalApiTrialStore store;
        final RuleBillingRecordMapper billingMapper;
        final RuleRuntimeCallLogMapper runtimeMapper;
        Fixture() throws Exception {
            try {
                for (String original : List.of("rule_billing_record", "rule_runtime_call_log", "rule_external_api_trial")) {
                    String table = "it_trial_identity_" + UUID.randomUUID().toString().replace("-", "");
                    assertTrue(table.matches("it_trial_identity_[a-f0-9]{32}"));
                    jdbc.execute("CREATE TABLE `" + table + "` LIKE `" + original + "`");
                    createdTables.add(table); tables.put(original, table);
                }
                for (String original : List.of("rule_billing_record", "rule_runtime_call_log")) {
                    Long width = jdbc.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME='root_trace_id'", Long.class, tables.get(original));
                    assertEquals("实际clone schema必须保留生产36列约束", Long.valueOf(36), width);
                }
                assertEquals(Long.valueOf(36), jdbc.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME='call_id'", Long.class, tables.get("rule_runtime_call_log")));
                MybatisConfiguration configuration = new MybatisConfiguration();
                configuration.setMapUnderscoreToCamelCase(true); configuration.setLogImpl(NoLoggingImpl.class);
                configuration.addInterceptor(new IsolatedSql(tables));
                var factoryBean = new MybatisSqlSessionFactoryBean(); factoryBean.setDataSource(datasource); factoryBean.setConfiguration(configuration);
                factoryBean.setGlobalConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig().setMetaObjectHandler(new MybatisPlusConfig().metaObjectHandler()));
                factoryBean.setTransactionFactory(new SpringManagedTransactionFactory());
                var factory = factoryBean.getObject(); factory.getConfiguration().addMapper(RuleBillingRecordMapper.class); factory.getConfiguration().addMapper(RuleRuntimeCallLogMapper.class);
                SqlSessionTemplate sessions = new SqlSessionTemplate(factory);
                billingMapper = sessions.getMapper(RuleBillingRecordMapper.class); runtimeMapper = sessions.getMapper(RuleRuntimeCallLogMapper.class);
                var keys = new ProjectAuthProperties(); keys.setActiveKeyId("test"); keys.setMasterKeys(Map.of("test", "owned-identity-key-" + UUID.randomUUID())); cipher = new CredentialCipher(keys);
                source.setId(17L); source.setStatus(1); source.setScope("GLOBAL"); source.setDatasourceCode("OWNED_IDENTITY"); source.setDatasourceName("隔离身份回归"); source.setAuthType("NONE");
                source.setBaseUrl("http://127.0.0.1/unused-readonly-test");
                store = newStore();
            } catch (Exception failure) { close(); throw failure; }
        }
        ExternalApiTrialStore newStore() {
            ExternalApiTrialStore result = new ExternalApiTrialStore();
            JdbcTemplate redirected = new JdbcTemplate(datasource) {
                private String rewrite(String sql) {
                    if (!sql.contains("rule_external_api_trial")) throw new IllegalStateException("试运行隔离Jdbc拒绝其它表");
                    return sql.replace("rule_external_api_trial", tables.get("rule_external_api_trial"));
                }
                @Override public int update(String sql, Object... args) { return super.update(rewrite(sql), args); }
                @Override public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) { return super.query(rewrite(sql), mapper, args); }
            };
            ReflectionTestUtils.setField(result, "jdbcTemplate", redirected); ReflectionTestUtils.setField(result, "credentialCipher", cipher);
            ReflectionTestUtils.setField(result, "transactionManager", new DataSourceTransactionManager(datasource)); return result;
        }
        @SuppressWarnings("unchecked") JSONObject freeze() {
            ExternalApiTrialService service = new ExternalApiTrialService();
            RuleExternalDatasourceMapper sources = (RuleExternalDatasourceMapper) Proxy.newProxyInstance(RuleExternalDatasourceMapper.class.getClassLoader(), new Class[]{RuleExternalDatasourceMapper.class}, (proxy, method, args) -> {
                if ("selectById".equals(method.getName())) return source;
                throw new AssertionError("Unexpected source operation: " + method.getName());
            });
            ReflectionTestUtils.setField(service, "datasourceMapper", sources);
            RuleExternalApiConfig api = api(null, null);
            api.setExecutionConfig("{\"version\":2,\"requestFields\":[],\"steps\":[{\"id\":\"first\",\"type\":\"HTTP\",\"endpointUrl\":\"/first\"},{\"id\":\"second\",\"type\":\"HTTP\",\"endpointUrl\":\"/second\"}]}");
            return ReflectionTestUtils.invokeMethod(service, "freeze", api, Map.of("amount", new BigDecimal("9007199254740993.123456789")));
        }
        ExternalApiTrialStore.State acceptSnapshot() { return store.create(owner, requestKey, 17L, "a".repeat(64), freeze().toJSONString(), 1800); }
        ExternalApiTrialContext context(ExternalApiTrialStore.State state) {
            return new ExternalApiTrialContext(state.trialId(), state.id(), state.expiresAt(), JSON.parseObject(state.snapshotJson()), Map.of(), () -> { }, pending -> { });
        }
        RuleExternalApiConfig api(Long identity, String callId) {
            RuleExternalApiConfig api = new RuleExternalApiConfig(); api.setId(identity); api.setDatasourceId(17L); api.setApiCode("OWNED_IDENTITY");
            api.setApiName("身份真实持久回归"); api.setRequestMode("SYNC"); api.setRequestMethod("POST"); api.setAuthMode("NONE");
            api.setBillingItemCode("OWNED_TRIAL_PRICE"); api.setUnitPrice(BigDecimal.ONE); api.setExecutionCallId(callId); return api;
        }
        void writeActualInvokerLog(RuleExternalApiConfig api, String callId) throws Exception {
            ExternalApiInvokeService invoker = new ExternalApiInvokeService();
            ReflectionTestUtils.setField(invoker, "runtimeCallLogService", new RuleRuntimeCallLogService() {
                @Override public void safeSave(RuleRuntimeCallLog log) { assertEquals(1, runtimeMapper.insert(log)); }
            });
            Class<?> type = Class.forName(ExternalApiInvokeService.class.getName() + "$InvokeTrace");
            var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true); Object trace = constructor.newInstance();
            ReflectionTestUtils.setField(trace, "callId", callId); ReflectionTestUtils.setField(trace, "responseStatus", 200);
            ReflectionTestUtils.setField(trace, "requestIssued", true); ReflectionTestUtils.setField(trace, "providerAttemptNo", 1);
            ReflectionTestUtils.setField(trace, "requestMethod", "POST"); ReflectionTestUtils.setField(trace, "requestUrl", "http://127.0.0.1/fixture");
            ReflectionTestUtils.setField(trace, "requestHeaders", Map.of());
            ReflectionTestUtils.invokeMethod(invoker, "logDatasourceCall", api, source, trace, true, Map.of("success", true, "httpStatus", 200, "body", Map.of("score", 88)), null, 1L);
        }
        RuleBillingRecord onlyBill() { List<RuleBillingRecord> rows = billingMapper.selectList(null); assertEquals(1, rows.size()); return rows.get(0); }
        @Override public void close() {
            for (String table : createdTables) { assertTrue(table.matches("it_trial_identity_[a-f0-9]{32}")); jdbc.execute("DROP TABLE IF EXISTS `" + table + '`'); }
        }
    }
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    private static final class IsolatedSql implements Interceptor {
        private final Map<String, String> tables;
        IsolatedSql(Map<String, String> tables) { this.tables = tables; }
        @Override public Object intercept(Invocation invocation) throws Throwable {
            var sql = ((StatementHandler) invocation.getTarget()).getBoundSql(); String original = sql.getSql();
            for (String permitted : List.of("rule_billing_record", "rule_runtime_call_log")) {
                if (original.contains(permitted)) {
                    SystemMetaObject.forObject(sql).setValue("sql", original.replaceAll("`?" + permitted + "`?", '`' + tables.get(permitted) + '`'));
                    return invocation.proceed();
                }
            }
            throw new IllegalStateException("隔离mapper拒绝未授权表");
        }
    }
    private static DataSource localDataSource() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("rule-engine-server"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("无法定位仓库根目录");
        Properties env = LocalIntegrationSettings.load(root);
        InetAddress address = InetAddress.getByName(required(env, "MYSQL_HOST"));
        if (!address.isLoopbackAddress()) throw new IllegalStateException("身份持久化测试仅允许本机MySQL");
        String database = required(env, "MYSQL_DATABASE"); if (!database.matches("[A-Za-z0-9_$-]+")) throw new IllegalStateException("数据库名无效");
        int port = Integer.parseInt(required(env, "MYSQL_PORT")); if (port < 1 || port > 65535) throw new IllegalStateException("端口无效");
        String host = address.getHostAddress(); if (host.contains(":")) host = '[' + host + ']';
        DriverManagerDataSource source = new DriverManagerDataSource() {
            @Override public Connection getConnection() throws java.sql.SQLException {
                Connection connection = super.getConnection();
                try (var statement = connection.createStatement()) { statement.execute("SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION'"); }
                catch (java.sql.SQLException error) { connection.close(); throw error; }
                return connection;
            }
        };
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl("jdbc:mysql://" + host + ':' + port + '/' + database + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=15000");
        source.setUsername(required(env, "MYSQL_USERNAME")); source.setPassword(required(env, "MYSQL_PASSWORD")); return source;
    }
    private static String required(Properties env, String name) { String value = env.getProperty(name); if (value == null || value.isBlank()) throw new IllegalStateException("本机服务配置缺少" + name); return value; }
}
