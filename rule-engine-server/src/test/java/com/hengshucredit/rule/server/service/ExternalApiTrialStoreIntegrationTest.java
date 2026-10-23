package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/** 显式本机MySQL门禁；每项仅创建/删除自己UUID命名的独立表，不触碰生产trial行。 */
public class ExternalApiTrialStoreIntegrationTest {
    private static final String DB_NOW = "CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS UNSIGNED)";
    private String table;
    private DataSource dataSource;
    private JdbcTemplate observer;
    private ReplacingJdbcTemplate storeJdbc;
    private DataSourceTransactionManager transactions;
    private ProjectAuthProperties keys;
    private ExternalApiTrialStore store;
    private final String owner = "trial-owner-" + UUID.randomUUID();
    private final String snapshot = "{\"params\":{\"amount\":9007199254740993.123456789012345678901},\"credential\":\"owned-test-secret\"}";

    @Before public void setUp() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("externalApiTrial.integration"));
        dataSource = localDataSource(); observer = new JdbcTemplate(dataSource);
        table = "trial_it_" + UUID.randomUUID().toString().replace("-", "");
        String schema;
        try (var input = new ClassPathResource("sql/schema.sql").getInputStream()) { schema = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
        var ddl = Pattern.compile("CREATE TABLE IF NOT EXISTS `rule_external_api_trial`[\\s\\S]*?;").matcher(schema);
        assertTrue("schema.sql必须包含实际独立状态表", ddl.find());
        observer.execute(ddl.group().replace("`rule_external_api_trial`", '`' + table + '`'));
        transactions = new DataSourceTransactionManager(dataSource);
        storeJdbc = new ReplacingJdbcTemplate(dataSource, table);
        keys = new ProjectAuthProperties(); keys.setActiveKeyId("test"); keys.setMasterKeys(Map.of("test", UUID.randomUUID() + "-isolated-test-key"));
        store = newStore();
    }

    @After public void cleanup() {
        if (table != null && observer != null) {
            assertTrue(table.matches("trial_it_[0-9a-f]{32}"));
            observer.execute("DROP TABLE IF EXISTS `" + table + '`');
        }
    }

    @Test public void duplicateCreationKeepsOriginalDigestSnapshotAndExpiryAcrossReload() {
        String key = UUID.randomUUID().toString();
        var first = store.create(owner, key, 17L, "a".repeat(64), snapshot, 1800);
        var duplicate = store.create(owner, key, 18L, "b".repeat(64), "{\"changed\":true}", 3600);
        assertEquals(first.id(), duplicate.id()); assertEquals(first.trialId(), duplicate.trialId());
        assertEquals(first.expiresAt(), duplicate.expiresAt()); assertEquals(first.createdAt(), duplicate.createdAt());
        assertEquals("a".repeat(64), duplicate.snapshotDigest()); assertEquals(snapshot, duplicate.snapshotJson());
        assertEquals(Long.valueOf(17L), duplicate.datasourceId());
        assertEquals(snapshot, newStore().get(first.trialId(), owner).snapshotJson());
        assertEquals(first.id(), newStore().findByRequestKey(owner, key).id());
        String encrypted = observer.queryForObject("SELECT snapshot_json FROM `" + table + "` WHERE id=?", String.class, first.id());
        assertTrue(encrypted.startsWith("v1:test:")); assertFalse(encrypted.contains("owned-test-secret"));
        assertEquals(snapshot, new CredentialCipher(keys).decrypt(encrypted));
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, storeJdbc.lastIsolation);
    }

    @Test public void concurrentClaimsHaveOneWinnerAndOwnerAndTokenAreExact() throws Exception {
        String key = UUID.randomUUID().toString();
        var created = store.create(owner, key, 17L, "a".repeat(64), snapshot, 1800);
        assertNull(store.get(created.trialId(), owner.toUpperCase()));
        assertNull(store.get(created.trialId(), owner + ' '));
        assertNotEquals(created.id(), store.create(owner + ' ', key, 17L, "a".repeat(64), snapshot, 1800).id());
        assertFalse(store.claim(created.trialId(), owner.toUpperCase(), UUID.randomUUID().toString(), 30));
        var executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8), go = new CountDownLatch(1);
        List<Future<String>> claims = new ArrayList<>();
        try {
            for (int index = 0; index < 8; index++) claims.add(executor.submit(() -> {
                String token = UUID.randomUUID().toString(); ready.countDown();
                assertTrue(go.await(10, TimeUnit.SECONDS));
                return store.claim(created.trialId(), owner, token, 30) ? token : null;
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); go.countDown();
            List<String> winners = new ArrayList<>();
            for (var claim : claims) { String token = claim.get(15, TimeUnit.SECONDS); if (token != null) winners.add(token); }
            assertEquals(1, winners.size());
            var running = store.get(created.trialId(), owner);
            assertEquals("RUNNING", running.status()); assertEquals(1, running.attemptNo()); assertEquals(winners.get(0), running.leaseOwner());
            String wrong = UUID.randomUUID().toString();
            assertFalse(store.isRunning(created.trialId(), wrong));
            assertFalse(store.checkpoint(created.trialId(), wrong, "{\"secret\":\"wrong\"}"));
            assertFalse(store.finish(created.trialId(), wrong, "SUCCEEDED", null, "{}", null));
            assertTrue(store.isRunning(created.trialId(), winners.get(0)));
        } finally { go.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS)); }
    }

    @Test public void waitingCheckpointAndResultAreEncryptedAndOldLeaseCannotWriteAfterReclaim() {
        var trial = create(); String first = UUID.randomUUID().toString(), second = UUID.randomUUID().toString();
        assertTrue(store.claim(trial.trialId(), owner, first, 30));
        String pending = "{\"providerRequestId\":\"owned-pending-secret\",\"amount\":9007199254740993}";
        assertTrue(store.checkpoint(trial.trialId(), first, pending));
        assertEquals(pending, newStore().get(trial.trialId(), owner).pendingJson());
        String encrypted = observer.queryForObject("SELECT pending_json FROM `" + table + "` WHERE id=?", String.class, trial.id());
        assertFalse(encrypted.contains("owned-pending-secret"));
        assertTrue(store.finish(trial.trialId(), first, "WAITING", pending, null, null));
        assertFalse(store.isRunning(trial.trialId(), first));
        assertTrue(store.claim(trial.trialId(), owner, second, 30));
        assertFalse(store.checkpoint(trial.trialId(), first, "{}"));
        assertFalse(store.finish(trial.trialId(), first, "FAILED", null, "{}", "old worker"));
        String result = "{\"value\":9007199254740993.123456789012345678901}";
        assertTrue(store.finish(trial.trialId(), second, "SUCCEEDED", null, result, null));
        var done = newStore().get(trial.trialId(), owner);
        assertEquals("SUCCEEDED", done.status()); assertEquals(result, done.resultJson()); assertEquals(2, done.attemptNo());
        String raw = observer.queryForObject("SELECT result_json FROM `" + table + "` WHERE id=?", String.class, trial.id());
        assertFalse(raw.contains("9007199254740993")); assertEquals(result, new CredentialCipher(keys).decrypt(raw));
    }

    @Test public void runningExpiryBecomesUnknownBeforeGeneralExpiryAndCanNeverBeReclaimed() {
        for (String expired : List.of("lease_until_ms=" + DB_NOW + "-1", "expires_at_ms=" + DB_NOW + "-1",
                "lease_until_ms=" + DB_NOW + "-1, expires_at_ms=" + DB_NOW + "-1")) {
            var running = create(); String token = UUID.randomUUID().toString();
            assertTrue(store.claim(running.trialId(), owner, token, 30));
            observer.update("UPDATE `" + table + "` SET " + expired + " WHERE id=?", running.id());
            assertFalse(store.finish(running.trialId(), token, "SUCCEEDED", null, "{}", null));
            assertFalse(store.checkpoint(running.trialId(), token, "{}"));
            assertFalse(store.isRunning(running.trialId(), token));
            assertEquals("UNKNOWN", store.get(running.trialId(), owner).status());
            assertEquals("UNKNOWN", store.cancel(running.trialId(), owner).status());
            assertFalse(store.claim(running.trialId(), owner, UUID.randomUUID().toString(), 30));
        }
        var created = create();
        observer.update("UPDATE `" + table + "` SET expires_at_ms=" + DB_NOW + "-1 WHERE id=?", created.id());
        assertEquals("EXPIRED", store.findByRequestKey(owner, created.requestKey()).status());
        var waiting = create(); String waitingToken = UUID.randomUUID().toString();
        assertTrue(store.claim(waiting.trialId(), owner, waitingToken, 30));
        assertTrue(store.finish(waiting.trialId(), waitingToken, "WAITING", "{}", null, null));
        observer.update("UPDATE `" + table + "` SET expires_at_ms=" + DB_NOW + "-1 WHERE id=?", waiting.id());
        assertTrue(store.list(owner, 20).stream().anyMatch(state -> state.id() == waiting.id() && state.status().equals("EXPIRED")));
    }

    @Test public void cancellationAndFinishRaceKeepTheFirstTerminalOutcome() throws Exception {
        var trial = create(); String token = UUID.randomUUID().toString();
        assertTrue(store.claim(trial.trialId(), owner, token, 30));
        assertNull(store.cancel(trial.trialId(), owner.toUpperCase()));
        var executor = Executors.newFixedThreadPool(2); CountDownLatch go = new CountDownLatch(1);
        try {
            Future<Boolean> finish = executor.submit(() -> { assertTrue(go.await(10, TimeUnit.SECONDS)); return store.finish(trial.trialId(), token, "SUCCEEDED", null, "{}", null); });
            Future<ExternalApiTrialStore.State> cancel = executor.submit(() -> { assertTrue(go.await(10, TimeUnit.SECONDS)); return store.cancel(trial.trialId(), owner); });
            go.countDown(); boolean completed = finish.get(15, TimeUnit.SECONDS); var cancelled = cancel.get(15, TimeUnit.SECONDS);
            String expected = completed ? "SUCCEEDED" : "CANCELLED";
            assertEquals(expected, cancelled.status()); assertEquals(expected, store.get(trial.trialId(), owner).status());
            assertFalse(store.checkpoint(trial.trialId(), token, "{}"));
            assertFalse(store.claim(trial.trialId(), owner, UUID.randomUUID().toString(), 30));
        } finally { go.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS)); }
    }

    @Test public void acceptedStateAndEveryTransitionCommitOutsideCallerRollback() {
        final ExternalApiTrialStore.State[] created = new ExternalApiTrialStore.State[1];
        TransactionTemplate parent = new TransactionTemplate(transactions);
        parent.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        parent.executeWithoutResult(tx -> { created[0] = create(); tx.setRollbackOnly(); });
        assertNotNull(newStore().get(created[0].trialId(), owner));
        String token = UUID.randomUUID().toString();
        parent.executeWithoutResult(tx -> { assertTrue(store.claim(created[0].trialId(), owner, token, 30)); tx.setRollbackOnly(); });
        assertEquals("RUNNING", newStore().get(created[0].trialId(), owner).status());
        parent.executeWithoutResult(tx -> { assertTrue(store.checkpoint(created[0].trialId(), token, "{\"p\":1}")); tx.setRollbackOnly(); });
        assertEquals("{\"p\":1}", newStore().get(created[0].trialId(), owner).pendingJson());
        parent.executeWithoutResult(tx -> { assertTrue(store.finish(created[0].trialId(), token, "SUCCEEDED", null, "{\"r\":1}", null)); tx.setRollbackOnly(); });
        assertEquals("SUCCEEDED", newStore().get(created[0].trialId(), owner).status());
    }

    private ExternalApiTrialStore.State create() { return store.create(owner, UUID.randomUUID().toString(), 17L, "a".repeat(64), snapshot, 1800); }
    private ExternalApiTrialStore newStore() {
        ExternalApiTrialStore result = new ExternalApiTrialStore();
        ReflectionTestUtils.setField(result, "jdbcTemplate", storeJdbc);
        ReflectionTestUtils.setField(result, "transactionManager", transactions);
        ReflectionTestUtils.setField(result, "credentialCipher", new CredentialCipher(keys));
        return result;
    }
    private static final class ReplacingJdbcTemplate extends JdbcTemplate {
        private final String table; private volatile int lastIsolation;
        ReplacingJdbcTemplate(DataSource source, String table) { super(source); this.table = table; }
        private String sql(String sql) { return sql.replace("rule_external_api_trial", table); }
        @Override public int update(String sql, Object... args) {
            Connection connection = DataSourceUtils.getConnection(getDataSource());
            try { lastIsolation = connection.getTransactionIsolation(); }
            catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
            finally { DataSourceUtils.releaseConnection(connection, getDataSource()); }
            return super.update(sql(sql), args);
        }
        @Override public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) { return super.query(sql(sql), mapper, args); }
    }
    private static DataSource localDataSource() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("rule-engine-server"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("无法定位仓库根目录");
        Properties settings = LocalIntegrationSettings.load(root);
        InetAddress address = InetAddress.getByName(required(settings, "MYSQL_HOST"));
        if (!address.isLoopbackAddress()) throw new IllegalStateException("试运行状态集成测试仅允许本机MySQL");
        String database = required(settings, "MYSQL_DATABASE");
        if (!database.matches("[A-Za-z0-9_$-]+")) throw new IllegalStateException("MYSQL_DATABASE格式无效");
        int port = Integer.parseInt(required(settings, "MYSQL_PORT"));
        if (port < 1 || port > 65535) throw new IllegalStateException("MYSQL_PORT无效");
        String host = address.getHostAddress(); if (host.contains(":")) host = '[' + host + ']';
        DriverManagerDataSource source = new DriverManagerDataSource(); source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl("jdbc:mysql://" + host + ':' + port + '/' + database + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=15000");
        source.setUsername(required(settings, "MYSQL_USERNAME")); source.setPassword(required(settings, "MYSQL_PASSWORD")); return source;
    }
    private static String required(Properties settings, String key) {
        String value = settings.getProperty(key); if (value == null || value.isBlank()) throw new IllegalStateException("本机服务配置缺少" + key); return value;
    }
}
