package com.hengshucredit.rule.client.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class RuleHttpClientTest {
    private HttpServer server;
    private String url;
    private String response;
    private int status;
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private JSONObject request;
    private com.sun.net.httpserver.Headers headers;
    private final AtomicInteger exchanges = new AtomicInteger();

    @Before public void start() throws Exception {
        status = 200;
        response = "{\"code\":200,\"data\":{\"success\":true,\"traceId\":\"trace-1\",\"result\":{\"decision\":\"REJECT\",\"额度\":12.5},\"executeTimeMs\":3}}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getRawPath());
            byte[] bytes;
            if (exchange.getRequestURI().getPath().endsWith("/auth/token")) {
                exchanges.incrementAndGet();
                bytes = "{\"code\":200,\"data\":{\"accessToken\":\"temporary\",\"expiresInSeconds\":7200,\"graceExpiresInSeconds\":7800}}".getBytes(StandardCharsets.UTF_8);
            } else {
                headers = exchange.getRequestHeaders();
                request = JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                bytes = response.getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().set("Location", url + "/redirect-target");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After public void stop() { server.stop(0); }

    @Test public void executesMapAndPojoWithoutSyncingAndPreservesBusinessResult() {
        try (RuleHttpClient client = builder().build()) {
            assertTrue("构造客户端不应触发任何请求", paths.isEmpty());
            RuleResult result = client.execute("风控 A", Map.of("Age_Value", 17, "姓名", "示例"));
            assertTrue(result.isSuccess());
            assertEquals("REJECT", ((Map<?, ?>) result.getResult()).get("decision"));
            assertEquals("trace-1", result.getTraceId());
            assertEquals(Boolean.FALSE, request.get("traceEnabled"));
            assertEquals(17, request.getJSONObject("params").getIntValue("Age_Value"));
            assertEquals("示例", request.getJSONObject("params").getString("姓名"));
            assertEquals("token-only-for-test", headers.getFirst("X-Rule-Token"));
            client.execute("风控 A", new Query(), true);
            assertEquals(Boolean.TRUE, request.get("traceEnabled"));
            assertEquals(18, request.getJSONObject("params").getIntValue("age"));
            client.execute("风控 A", null);
            assertEquals(Boolean.FALSE, request.get("traceEnabled"));
            assertTrue(request.getJSONObject("params").isEmpty());
            assertEquals(3, paths.size());
            assertTrue(paths.stream().allMatch(path -> path.startsWith("/api/rule/sync/execute/") && path.endsWith("%20A")));
        }
    }

    @Test public void ruleFailureIsReturnedButPlatformFailureThrows() {
        try (RuleHttpClient client = builder().build()) {
            response = "{\"code\":200,\"data\":{\"success\":false,\"errorMessage\":\"规则错误\"}}";
            assertFalse(client.execute("RULE", Map.of()).isSuccess());
            for (int code : new int[]{400, 401, 403, 404, 500}) {
                response = "{\"code\":" + code + ",\"message\":\"do-not-leak-token\"}";
                RuleHttpException error = assertThrows(RuleHttpException.class, () -> client.execute("RULE", Map.of()));
                assertEquals(Integer.valueOf(code), error.getPlatformCode());
                assertFalse(error.getMessage().contains("do-not-leak"));
            }
        }
    }

    @Test public void rejectsMalformedResponsesAndDoesNotFollowRedirects() {
        try (RuleHttpClient client = builder().build()) {
            for (String invalid : new String[]{"", "null", "<html>bad</html>", "{}", "{\"code\":200}", "{\"code\":200,\"data\":{}}"}) {
                response = invalid;
                assertThrows(RuleHttpException.class, () -> client.execute("RULE", Map.of()));
            }
            paths.clear();
            status = 302;
            assertEquals(302, assertThrows(RuleHttpException.class, () -> client.execute("RULE", Map.of())).getHttpStatus());
            assertEquals(1, paths.size());
        }
    }

    @Test public void reusesExchangedBearerTokenAndRejectsClosedClient() {
        RuleHttpClient client = builder().authConfig(ClientAuthConfig.basic("account", "secret")).build();
        client.execute("RULE", Map.of());
        client.execute("RULE", Map.of());
        assertEquals(1, exchanges.get());
        assertEquals("Bearer temporary", headers.getFirst("Authorization"));
        client.close();
        assertThrows(IllegalStateException.class, () -> client.execute("RULE", Map.of()));
    }

    @Test public void validatesConfigurationAndNeverSendsInvalidParams() {
        assertThrows(IllegalArgumentException.class, () -> builder().serverUrl("http://user:secret@host").build());
        assertThrows(IllegalArgumentException.class, () -> builder().timeoutMs(0).build());
        assertThrows(IllegalArgumentException.class, () -> builder().token("").build());
        try (RuleHttpClient client = builder().build()) {
            assertThrows(IllegalArgumentException.class, () -> client.execute("../all", Map.of()));
            assertThrows(IllegalArgumentException.class, () -> client.execute("R", List.of(1)));
            assertTrue(paths.isEmpty());
        }
    }

    @Test public void networkFailureDoesNotBecomeAnApprovedDecision() {
        server.stop(0);
        try (RuleHttpClient client = builder().timeoutMs(200).build()) {
            assertEquals(0, assertThrows(RuleHttpException.class, () -> client.execute("RULE", Map.of())).getHttpStatus());
        }
    }

    private RuleHttpClient.Builder builder() { return RuleHttpClient.builder().serverUrl(url).token("token-only-for-test"); }
    public static class Query { public int getAge() { return 18; } }
}
