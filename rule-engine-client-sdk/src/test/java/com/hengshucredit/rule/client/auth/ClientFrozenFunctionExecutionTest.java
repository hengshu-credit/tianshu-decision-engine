package com.hengshucredit.rule.client.auth;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.client.RuleEngineClient;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** 走真实 HTTP 同步负载和本地 SDK 执行，不绕过 CachedRule 的反序列化。 */
public class ClientFrozenFunctionExecutionTest {
    @Test public void serverOnlyMissingBeanCannotBlockPureRulesFullSnapshotRefresh() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.rule("PURE", 1, 11, "return 1;", List.of());
            assertValue(1, fixture.client.execute("PURE", new LinkedHashMap<>()));
            Map<String, Object> updated = fixture.rule("PURE", 1, 11, "return 2;", List.of());
            updated.put("version", 2); updated.put("bindingGeneration", 2);
            JSONObject unavailable = function("BEAN", "oldVersion"); unavailable.put("implBeanName", "serverOnlyBean");
            Map<String, Object> serverOnly = fixture.rule("SERVER_ONLY", 2, 12, "return frozenScore();", List.of(unavailable));
            serverOnly.put("requiresServerExecution", true); serverOnly.put("serverExecutionReason", "包含模型执行");
            byte[] body = JSON.toJSONString(Map.of("code", "000000", "message", "成功", "success", true, "data", List.of(updated, serverOnly))).getBytes(StandardCharsets.UTF_8);
            fixture.server.createContext("/api/rule/sync/all", exchange -> fixture.send(exchange, body));
            fixture.client.refreshAll();
            assertValue(2, fixture.client.execute("PURE", new LinkedHashMap<>()));
            assertTrue(fixture.client.getRuleInfo("SERVER_ONLY").isRequiresServerExecution());
            com.hengshucredit.rule.model.PlatformException rejected = assertThrows(com.hengshucredit.rule.model.PlatformException.class,
                    () -> fixture.client.execute("SERVER_ONLY", new LinkedHashMap<>()));
            assertEquals("201002", rejected.getCode());
            assertTrue("应在执行本地能力门禁拒绝，而非加载不需要的服务端Bean", rejected.getDetail().contains("serverSideExecution"));
        }
    }

    @Test public void fixedChildKeepsFrozenJavaMethodAfterLatestFunctionPushAndCacheHit() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.rule("ROOT", 1, 11, "return executeRuleVersionById(\"22\", \"81\");", List.of());
            fixture.rule("CHILD", 22, 81, "return frozenScore();", List.of(function("JAVA", "oldVersion")));
            fixture.live("oldVersion");
            assertValue(1, fixture.client.execute("ROOT", new LinkedHashMap<>()));
            fixture.live("newVersion");
            assertValue(1, fixture.client.execute("ROOT", new LinkedHashMap<>()));
            assertEquals("指定子版本命中缓存，不能通过重新取latest碰巧通过", 1, fixture.fixedLoads.get());
        }
    }

    @Test public void childFunctionScopeRestoresParentDefinitionEvenForSameFunctionCode() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.rule("ROOT", 1, 11, "return frozenScore() * 100 + executeRuleVersionById(\"22\", \"81\") * 10 + frozenScore();",
                    List.of(function("JAVA", "newVersion")));
            fixture.rule("CHILD", 22, 81, "return frozenScore();", List.of(function("JAVA", "oldVersion")));
            fixture.live("newVersion");
            assertValue(212, fixture.client.execute("ROOT", new LinkedHashMap<>()));
        }
    }

    @Test public void concurrentRulesDoNotShareMutableLatestFunctionTarget() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.rule("OLD", 1, 11, "return frozenScore();", List.of(function("JAVA", "oldVersion")));
            fixture.rule("NEW", 2, 12, "return frozenScore();", List.of(function("JAVA", "newVersion")));
            fixture.live("newVersion");
            var executor = Executors.newFixedThreadPool(2);
            var start = new CountDownLatch(1);
            try {
                var old = executor.submit(() -> { start.await(); var values = new java.util.ArrayList<RuleResult>(); for (int i = 0; i < 30; i++) values.add(fixture.client.execute("OLD", new LinkedHashMap<>())); return values; });
                var newer = executor.submit(() -> { start.await(); var values = new java.util.ArrayList<RuleResult>(); for (int i = 0; i < 30; i++) values.add(fixture.client.execute("NEW", new LinkedHashMap<>())); return values; });
                start.countDown();
                for (RuleResult result : old.get(10, TimeUnit.SECONDS)) assertValue(1, result);
                for (RuleResult result : newer.get(10, TimeUnit.SECONDS)) assertValue(2, result);
            } finally { executor.shutdownNow(); }
        }
    }

    @Test public void frozenBeanMethodUsesExistingLocalBeanWithoutBorrowingCurrentMethod() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.rule("BEAN_RULE", 1, 11, "return frozenScore();", List.of(function("BEAN", "oldVersion")));
            fixture.live("newVersion");
            assertValue(1, fixture.client.execute("BEAN_RULE", new LinkedHashMap<>()));
        }
    }

    @Test public void missingLocalImplementationCannotFallBackToLatestFunction() throws Exception {
        try (Fixture fixture = new Fixture()) {
            JSONObject unavailable = function("JAVA", "oldVersion");
            unavailable.put("implClass", "example.unavailable.FrozenFunction");
            fixture.rule("MISSING", 1, 11, "return frozenScore();", List.of(unavailable));
            fixture.live("newVersion");
            int before = Versions.newCalls.get();
            assertFailure(() -> fixture.client.execute("MISSING", new LinkedHashMap<>()));
            assertEquals("不得调用最新实现来掩盖本地能力缺失", before, Versions.newCalls.get());
        }
    }

    @Test public void frozenEmptyOrMissingFunctionSnapshotDoesNotBorrowRemoteRegistry() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.rule("EMPTY", 1, 11, "return frozenScore();", List.of());
            fixture.rule("MISSING_SNAPSHOT", 2, 12, "return frozenScore();", null);
            fixture.live("newVersion");
            assertFailure(() -> fixture.client.execute("EMPTY", new LinkedHashMap<>()));
            assertFailure(() -> fixture.client.execute("MISSING_SNAPSHOT", new LinkedHashMap<>()));
        }
    }

    @Test public void failedChildAndInterruptedFunctionNeverLeakTheirBindings() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.rule("ROOT", 1, 11, "return executeRuleVersionById(\"22\", \"81\");", List.of(function("JAVA", "newVersion")));
            fixture.rule("CHILD", 22, 81, "return frozenScore();", List.of(function("JAVA", "interrupted")));
            fixture.live("newVersion");
            try { assertFailure(() -> fixture.client.execute("ROOT", new LinkedHashMap<>())); }
            finally { Thread.interrupted(); }
            assertValue(2, fixture.client.getEngine().execute("frozenScore()", new LinkedHashMap<>(), false));
        }
    }

    @Test public void embeddedScriptAndUnversionedManualCallsKeepExistingLocalSemantics() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.rule("SCRIPT_RULE", 1, 11, "function frozenScore() { return 1; } return frozenScore();", List.of());
            fixture.live("newVersion");
            assertValue(1, fixture.client.execute("SCRIPT_RULE", new LinkedHashMap<>()));
            assertValue(2, fixture.client.getEngine().execute("frozenScore()", new LinkedHashMap<>(), false));
        }
    }

    private static void assertValue(int expected, RuleResult result) {
        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(expected, ((Number) result.getResult()).intValue());
    }

    private static void assertFailure(java.util.concurrent.Callable<RuleResult> action) throws Exception {
        try { assertFalse("冻结依赖不可用时不能运行当前实现", action.call().isSuccess()); }
        catch (IllegalStateException | IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    private static JSONObject function(String type, String method) {
        JSONObject value = new JSONObject(true);
        value.put("id", 7L); value.put("funcCode", "frozenScore"); value.put("implType", type);
        value.put("implClass", Versions.class.getName()); value.put("implBeanName", "frozenVersions");
        value.put("implMethod", method); value.put("paramsJson", "[]"); value.put("scope", "PROJECT");
        value.put("projectId", 1L); value.put("projectCode", "client-project"); value.put("status", 1);
        return value;
    }

    public static final class Versions {
        static final AtomicInteger newCalls = new AtomicInteger();
        public int oldVersion() { Thread.yield(); return 1; }
        public int newVersion() { newCalls.incrementAndGet(); Thread.yield(); return 2; }
        public int interrupted() { Thread.currentThread().interrupt(); throw new IllegalStateException("受控取消"); }
    }

    private static final class Fixture implements AutoCloseable {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final GenericApplicationContext context = new GenericApplicationContext();
        final AtomicInteger fixedLoads = new AtomicInteger();
        final RuleEngineClient client;
        Fixture() throws Exception {
            context.getBeanFactory().registerSingleton("frozenVersions", new Versions()); context.refresh();
            server.start();
            RedisConnectionFactory unused = (RedisConnectionFactory) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{RedisConnectionFactory.class}, (proxy, method, arguments) -> null);
            client = RuleEngineClient.builder().serverUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .projectId(1L).projectCode("client-project").connectionFactory(unused).applicationContext(context)
                    .logReportEnabled(false).traceEnabled(false).build();
        }
        Map<String, Object> rule(String code, long id, long binding, String script, List<JSONObject> functions) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("ruleCode", code); rule.put("definitionId", id); rule.put("versionBindingId", binding);
            rule.put("bindingGeneration", 1); rule.put("version", 1); rule.put("revisionId", id + 100);
            rule.put("artifactDigest", String.valueOf(id % 10).repeat(64)); rule.put("modelType", "SCRIPT");
            rule.put("compiledScript", script); rule.put("compiledType", "QL"); rule.put("projectCode", "client-project");
            if (functions != null) rule.put("frozenFunctions", functions);
            byte[] body = JSON.toJSONString(Map.of("code", "000000", "message", "成功", "success", true, "data", rule)).getBytes(StandardCharsets.UTF_8);
            server.createContext("/api/rule/sync/" + code, exchange -> send(exchange, body));
            server.createContext("/api/rule/sync/by-id/" + id, exchange -> { fixedLoads.incrementAndGet(); send(exchange, body); });
            return rule;
        }
        void live(String method) { client.getFunctionRegistrar().replaceRemoteSnapshot(List.of(function("JAVA", method))); }
        private void send(HttpExchange exchange, byte[] body) throws java.io.IOException {
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        }
        @Override public void close() { client.close(); server.stop(0); context.close(); }
    }
}
