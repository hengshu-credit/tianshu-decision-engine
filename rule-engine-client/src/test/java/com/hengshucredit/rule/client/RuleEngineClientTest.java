package com.hengshucredit.rule.client;

import com.hengshucredit.rule.client.cache.CachedRule;
import com.hengshucredit.rule.client.cache.L1MemoryCache;
import com.hengshucredit.rule.client.auth.ProjectClientAuthenticationException;
import com.hengshucredit.rule.client.sync.HttpSyncClient;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import org.junit.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RuleEngineClientTest {

    @Test
    public void localExecutionConvertsNestedAllRulesTerminationToSuccess() throws Exception {
        RecordingReporter reporter = new RecordingReporter();
        RedisConnectionFactory connectionFactory = (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(),
                new Class<?>[]{RedisConnectionFactory.class},
                (proxy, method, args) -> null);
        RuleEngineClient client = RuleEngineClient.builder()
                .connectionFactory(connectionFactory)
                .logReporter(reporter)
                .projectId(1L)
                .build();

        CachedRule root = rule("ROOT", "RULE_SET", "executeRule(\"CHILD\"); "
                + "setRuntimeValue(\"parentAfterChild\", true)");
        root.setOutputScriptNames(Arrays.asList("decision", "notAssigned"));
        root.setRevisionId(22L);
        root.setArtifactDigest("artifact-digest");
        CachedRule child = rule("CHILD", "FLOW", "setRuntimeValue(\"decision\", \"STOP\"); "
                + "terminateAllRules(); setRuntimeValue(\"childAfterEnd\", true)");
        L1MemoryCache cache = (L1MemoryCache) getField(client, "l1Cache");
        cache.put(root);
        cache.put(child);

        Map<String, Object> values = new LinkedHashMap<>();
        RuleResult result = client.execute("ROOT", values);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals("STOP", ((Map<?, ?>) result.getResult()).get("decision"));
        assertTrue(((Map<?, ?>) result.getResult()).containsKey("notAssigned"));
        assertFalse(values.containsKey("parentAfterChild"));
        assertFalse(values.containsKey("childAfterEnd"));
        assertEquals(1, reporter.logs.size());
        assertEquals(Integer.valueOf(1), reporter.logs.get(0).getSuccess());
        assertEquals(Long.valueOf(22L), reporter.logs.get(0).getRevisionId());
        assertEquals("artifact-digest", reporter.logs.get(0).getArtifactDigest());
    }

    @Test
    public void fullSyncReplacesCacheWithAuthoritativeSnapshot() throws Exception {
        RuleEngineClient client = client();
        L1MemoryCache cache = (L1MemoryCache) getField(client, "l1Cache");
        cache.put(rule("obsolete", "SCRIPT", "1"));
        CachedRule current = rule("current", "SCRIPT", "2");
        setField(client, "httpSyncClient", new StubHttpSyncClient(Collections.singletonList(current)));

        invoke(client, "fullSync");

        assertNull(cache.get("obsolete"));
        assertEquals("current", cache.get("current").getRuleCode());
    }

    @Test
    public void failedFullSyncKeepsPreviouslyCachedRules() throws Exception {
        RuleEngineClient client = client();
        L1MemoryCache cache = (L1MemoryCache) getField(client, "l1Cache");
        cache.put(rule("still-available", "SCRIPT", "1"));
        setField(client, "httpSyncClient", new StubHttpSyncClient(null));

        invoke(client, "fullSync");

        assertEquals("still-available", cache.get("still-available").getRuleCode());
    }

    @Test
    public void startIsIdempotentAfterSuccessfulInitialSync() throws Exception {
        RuleEngineClient client = client();
        StubHttpSyncClient syncClient = new StubHttpSyncClient(Collections.emptyList());
        setField(client, "httpSyncClient", syncClient);

        client.start();
        client.start();
        client.close();

        assertEquals(1, syncClient.fetchAllCalls);
    }

    @Test
    public void startRethrowsProjectAuthenticationFailure() throws Exception {
        RuleEngineClient client = client();
        StubHttpSyncClient syncClient = new StubHttpSyncClient(Collections.emptyList());
        syncClient.failure = new ProjectClientAuthenticationException("expired token");
        setField(client, "httpSyncClient", syncClient);

        try {
            client.start();
            fail("Expected authentication failure from initial full sync");
        } catch (ProjectClientAuthenticationException expected) {
            assertTrue(expected.getMessage().contains("expired token"));
        } finally {
            client.close();
        }
    }

    @Test
    public void traceDisabledSuppressesRootAndNestedExpressionTraces() throws Exception {
        RuleEngineClient client = RuleEngineClient.builder()
                .connectionFactory(connectionFactory())
                .projectCode("P001")
                .projectId(1L)
                .traceEnabled(false)
                .logReporter(new RecordingReporter())
                .build();
        L1MemoryCache cache = (L1MemoryCache) getField(client, "l1Cache");
        cache.put(rule("ROOT", "SCRIPT", "executeRule(\"CHILD\")"));
        cache.put(rule("CHILD", "SCRIPT", "1 + 1"));

        RuleResult result = client.execute("ROOT", new LinkedHashMap<>());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertNull(result.getTraces());
    }

    @Test
    public void disabledLogReportingOverridesExternalReporterForMapAndPojo() throws Exception {
        RecordingReporter reporter = new RecordingReporter();
        RuleEngineClient client = RuleEngineClient.builder()
                .connectionFactory(connectionFactory())
                .projectId(1L)
                .logReportEnabled(false)
                .logReporter(reporter)
                .build();
        L1MemoryCache cache = (L1MemoryCache) getField(client, "l1Cache");
        cache.put(rule("LOCAL", "SCRIPT", "1 + 1"));

        for (Object params : Arrays.asList(new LinkedHashMap<>(), new Query())) {
            RuleResult result = client.execute("LOCAL", params);
            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals(2, ((Number) result.getResult()).intValue());
            assertNull("关闭日志时不能调用外部 reporter", reporter.logs);
        }
        client.close();
    }

    @Test
    public void remoteExecutionForwardsTraceSettingForMapAndPojo() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.atomic.AtomicReference<com.alibaba.fastjson.JSONObject> request =
                new java.util.concurrent.atomic.AtomicReference<>();
        server.createContext("/api/rule/sync/execute/REMOTE", exchange -> {
            request.set(com.alibaba.fastjson.JSON.parseObject(new String(
                    exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
            byte[] response = "{\"code\":200,\"data\":{\"success\":true,\"result\":2}}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            for (boolean enabled : new boolean[]{false, true}) {
                RuleEngineClient client = RuleEngineClient.builder()
                        .connectionFactory(connectionFactory())
                        .serverUrl("http://127.0.0.1:" + server.getAddress().getPort())
                        .appName("business-service")
                        .serverSideExecution(true)
                        .traceEnabled(enabled)
                        .logReportEnabled(false)
                        .build();
                try {
                    RuleResult mapResult = client.execute("REMOTE", Collections.singletonMap("age", 18));
                    assertTrue(mapResult.getErrorMessage(), mapResult.isSuccess());
                    assertEquals(Boolean.valueOf(enabled), request.get().get("traceEnabled"));
                    assertEquals("business-service", request.get().getString("clientAppName"));
                    assertEquals(18, request.get().getJSONObject("params").getIntValue("age"));
                    RuleResult pojoResult = client.execute("REMOTE", new Query());
                    assertTrue(pojoResult.getErrorMessage(), pojoResult.isSuccess());
                    assertEquals(Boolean.valueOf(enabled), request.get().get("traceEnabled"));
                    assertEquals(18, request.get().getJSONObject("params").getIntValue("age"));
                } finally {
                    client.close();
                }
            }
        } finally {
            server.stop(0);
        }
    }

    public static class Query {
        public int getAge() { return 18; }
    }

    @Test
    public void reporterFailureDoesNotChangeSuccessfulRuleResult() throws Exception {
        RuleEngineClient client = RuleEngineClient.builder()
                .connectionFactory(connectionFactory())
                .projectId(1L)
                .logReporter(logs -> { throw new IllegalStateException("reporter unavailable"); })
                .build();
        L1MemoryCache cache = (L1MemoryCache) getField(client, "l1Cache");
        cache.put(rule("LOCAL", "SCRIPT", "1 + 1"));

        RuleResult result = client.execute("LOCAL", new LinkedHashMap<>());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(2, ((Number) result.getResult()).intValue());
    }

    @Test
    public void clientDoesNotStartOrCloseExternallyManagedReporter() throws Exception {
        LifecycleRecordingReporter reporter = new LifecycleRecordingReporter();
        RuleEngineClient client = RuleEngineClient.builder()
                .connectionFactory(connectionFactory())
                .logReporter(reporter)
                .build();

        invoke(client, "startOwnedLogReporter");
        client.close();

        assertEquals(0, reporter.startCalls);
        assertEquals(0, reporter.closeCalls);
    }

    @Test
    public void closeDuringInitialSyncCannotLeaveSchedulerRunning() throws Exception {
        RuleEngineClient client = client();
        BlockingHttpSyncClient syncClient = new BlockingHttpSyncClient();
        setField(client, "httpSyncClient", syncClient);
        Thread starter = new Thread(client::start);
        starter.start();
        assertTrue(syncClient.fetchAllEntered.await(2, TimeUnit.SECONDS));

        Thread closer = new Thread(client::close);
        closer.start();
        syncClient.allowFetchAll.countDown();
        starter.join(6000);
        closer.join(6000);

        assertFalse(starter.isAlive());
        assertFalse(closer.isAlive());
        assertNull(getField(client, "scheduler"));
    }

    @Test
    public void concurrentStartsPerformOnlyOneInitialSyncAndCreateOneScheduler() throws Exception {
        RuleEngineClient client = client();
        BlockingHttpSyncClient syncClient = new BlockingHttpSyncClient();
        setField(client, "httpSyncClient", syncClient);
        Thread first = new Thread(client::start);
        Thread second = new Thread(client::start);
        first.start();
        assertTrue(syncClient.fetchAllEntered.await(2, TimeUnit.SECONDS));
        second.start();
        syncClient.allowFetchAll.countDown();
        first.join(6000);
        second.join(6000);

        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        assertEquals(1, syncClient.fetchAllCalls);
        assertTrue(getField(client, "scheduler") instanceof java.util.concurrent.ScheduledExecutorService);
        client.close();
    }

    private CachedRule rule(String code, String modelType, String script) {
        CachedRule rule = new CachedRule();
        rule.setRuleCode(code);
        rule.setProjectCode("P001");
        rule.setModelType(modelType);
        rule.setCompiledScript(script);
        rule.setVersion(1);
        return rule;
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void invoke(Object target, String methodName) throws Exception {
        java.lang.reflect.Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private RuleEngineClient client() {
        return RuleEngineClient.builder()
                .connectionFactory(connectionFactory())
                .logReporter(new RecordingReporter())
                .build();
    }

    private RedisConnectionFactory connectionFactory() {
        return (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(),
                new Class<?>[]{RedisConnectionFactory.class},
                (proxy, method, args) -> null);
    }

    private static class StubHttpSyncClient extends HttpSyncClient {
        private final List<CachedRule> rules;
        private RuntimeException failure;
        protected int fetchAllCalls;

        private StubHttpSyncClient(List<CachedRule> rules) {
            super("http://localhost", 1000);
            this.rules = rules;
        }

        @Override
        public List<CachedRule> fetchAll() {
            fetchAllCalls++;
            if (failure != null) throw failure;
            return rules;
        }
    }

    private static class BlockingHttpSyncClient extends StubHttpSyncClient {
        private final CountDownLatch fetchAllEntered = new CountDownLatch(1);
        private final CountDownLatch allowFetchAll = new CountDownLatch(1);

        private BlockingHttpSyncClient() {
            super(Collections.emptyList());
        }

        @Override
        public List<CachedRule> fetchAll() {
            fetchAllEntered.countDown();
            try {
                if (!allowFetchAll.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("Initial sync release timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            return super.fetchAll();
        }
    }

    private static class RecordingReporter implements com.hengshucredit.rule.client.log.ExecutionLogReporter {
        private List<RuleExecutionLog> logs;

        @Override
        public void report(List<RuleExecutionLog> logs) {
            this.logs = logs;
        }
    }

    private static class LifecycleRecordingReporter extends RecordingReporter {
        private int startCalls;
        private int closeCalls;

        @Override
        public void start() {
            startCalls++;
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }
}
