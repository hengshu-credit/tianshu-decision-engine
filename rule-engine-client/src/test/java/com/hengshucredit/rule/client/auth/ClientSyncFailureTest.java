package com.hengshucredit.rule.client.auth;

import com.hengshucredit.rule.client.RuleEngineClient;
import com.hengshucredit.rule.client.cache.CachedRule;
import com.hengshucredit.rule.client.cache.L1MemoryCache;
import com.hengshucredit.rule.client.function.ClientFunctionRegistrar;
import com.hengshucredit.rule.client.sync.HttpSyncClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClientSyncFailureTest {

    private final AtomicReference<ResponseSpec> ruleResponse = new AtomicReference<>();
    private final AtomicReference<ResponseSpec> functionResponse = new AtomicReference<>();
    private HttpServer server;
    private String serverUrl;

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/rule/sync/all", exchange -> respond(exchange, ruleResponse.get()));
        server.createContext("/api/rule/sync/functions/1", exchange -> respond(exchange, functionResponse.get()));
        server.start();
        serverUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    public void failedRuleAndFunctionSyncResponsesReturnNull() {
        HttpSyncClient syncClient = new HttpSyncClient(serverUrl, 1000);
        for (ResponseSpec failure : failures()) {
            ruleResponse.set(failure);
            functionResponse.set(failure);

            assertNull(syncClient.fetchAll());
            assertNull(syncClient.fetchFunctions(1L));
        }
    }

    @Test
    public void failedRuleAndFunctionSyncResponsesKeepExistingSnapshots() throws Exception {
        RuleEngineClient client = RuleEngineClient.builder()
                .serverUrl(serverUrl)
                .projectId(1L)
                .projectCode("P001")
                .logReportEnabled(false)
                .connectionFactory(connectionFactory())
                .build();
        L1MemoryCache cache = (L1MemoryCache) field(client, "l1Cache");
        ClientFunctionRegistrar registrar = (ClientFunctionRegistrar) field(client, "functionRegistrar");
        Method fullSync = privateMethod("fullSync");
        Method syncFunctions = privateMethod("syncFunctions");

        for (ResponseSpec failure : failures()) {
            cache.put(rule("cached-rule"));
            registrar.registerRemoteFromPush("GLOBAL", null, "cachedFunction", "SCRIPT", "7",
                    null, null, null, null);
            ruleResponse.set(failure);
            functionResponse.set(failure);

            fullSync.invoke(client);
            syncFunctions.invoke(client);

            assertEquals("cached-rule", cache.get("cached-rule").getRuleCode());
            assertTrue("Expected remote function after " + failure.description,
                    registrar.hasRemoteFunction("GLOBAL", null, "cachedFunction"));
        }
    }

    private ResponseSpec[] failures() {
        return new ResponseSpec[]{
                new ResponseSpec("HTTP 500", 500, "{\"code\":500,\"message\":\"server error\"}"),
                new ResponseSpec("empty body", 200, ""),
                new ResponseSpec("business error", 200, "{\"code\":500,\"message\":\"business error\"}"),
                new ResponseSpec("malformed JSON", 200, "not-json")
        };
    }

    private Method privateMethod(String name) throws Exception {
        Method method = RuleEngineClient.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method;
    }

    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private RedisConnectionFactory connectionFactory() {
        return (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(), new Class<?>[]{RedisConnectionFactory.class},
                (proxy, method, args) -> null);
    }

    private CachedRule rule(String code) {
        CachedRule rule = new CachedRule();
        rule.setRuleCode(code);
        rule.setCompiledScript("return 1;");
        return rule;
    }

    private void respond(HttpExchange exchange, ResponseSpec response) throws java.io.IOException {
        byte[] body = response.body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static class ResponseSpec {
        private final String description;
        private final int status;
        private final String body;

        private ResponseSpec(String description, int status, String body) {
            this.description = description;
            this.status = status;
            this.body = body;
        }
    }
}
