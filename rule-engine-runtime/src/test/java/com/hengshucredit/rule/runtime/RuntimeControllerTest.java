package com.hengshucredit.rule.runtime;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.client.RuleEngineClient;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class RuntimeControllerTest {
    private static final String TOKEN = "runtime-token-for-isolated-tests-12345";

    @Test public void unsupportedAcceptStillReceivesAPlatformJsonError() throws Exception {
        RuleResult result = new RuleResult(); result.setSuccess(true);
        var response = endpoint((code, params) -> result).perform(post("/api/rule/execute/MixedCase")
                .header("Authorization", "Bearer " + TOKEN).contentType("application/json").accept("text/plain")
                .content("{\"params\":{}}")).andReturn().getResponse();
        assertEquals(406, response.getStatus());
        assertEquals("100008", JSON.parseObject(response.getContentAsString()).getString("code"));
    }

    @Test public void runtimePassesPerRequestOptionsToSdkExecutor() throws Exception {
        var received = new java.util.concurrent.atomic.AtomicReference<com.hengshucredit.rule.model.RuleExecutionRequest>();
        var env = new MockEnvironment().withProperty("RULE_ALLOWED_CODES", "MixedCase").withProperty("RUNTIME_ACCESS_TOKEN", TOKEN);
        RuleResult result = new RuleResult(); result.setSuccess(true);
        var mvc = MockMvcBuilders.standaloneSetup(new RuntimeController((code, input) -> {
            received.set(input); return result;
        }, env)).setControllerAdvice(new RuntimeExceptionHandler()).build();
        var response = mvc.perform(post("/api/rule/execute/MixedCase").header("Authorization", "Bearer " + TOKEN)
                .contentType("application/json").content(JSON.toJSONString(Map.of("params", Map.of("Age", 18),
                        "traceEnabled", false, "clientAppName", "request-app"))))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(Boolean.FALSE, received.get().getTraceEnabled());
        assertEquals("request-app", received.get().getClientAppName());
        assertEquals(18, received.get().getParams().get("Age"));
    }

    @Test
    public void upstreamAuthenticationAndCapacityDoNotBecomeGeneric502() throws Exception {
        for (var code : java.util.List.of(com.hengshucredit.rule.model.PlatformCode.TOKEN_INVALID,
                com.hengshucredit.rule.model.PlatformCode.CAPACITY_EXCEEDED)) {
            MockMvc mvc = endpoint((rule, params) -> {
                throw new com.hengshucredit.rule.client.http.RuleHttpException(null, code.httpStatus(), code.code(),
                        "REJECTED", "upstream-trace", 1000L);
            });
            var response = mvc.perform(post("/api/rule/execute/MixedCase")
                    .header("Authorization", "Bearer " + TOKEN).contentType("application/json")
                    .content("{\"params\":{}}")).andReturn().getResponse();
            assertEquals(code.httpStatus(), response.getStatus());
            var body = JSON.parseObject(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(code.code(), body.getString("code"));
            assertEquals(code.message(), body.getString("message"));
            assertEquals("upstream-trace", body.getString("traceId"));
        }
    }

    @Test
    public void platformResponsePreservesWaitingAndUsesOneSuccessCode() throws Exception {
        RuleResult result = new RuleResult();
        result.setSuccess(true);
        MockMvc mvc = endpoint((code, params) -> result);
        var success = mvc.perform(post("/api/rule/execute/MixedCase")
                .header("Authorization", "Bearer " + TOKEN).contentType("application/json")
                .content("{\"params\":{}}")).andReturn().getResponse();
        assertEquals("000000", JSON.parseObject(success.getContentAsString()).get("code"));
        assertEquals("成功", JSON.parseObject(success.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).getString("message"));

        result.setSuccess(false);
        result.setExecutionStatus("WAITING_EXTERNAL");
        result.setTraceId("waiting-trace");
        var waiting = mvc.perform(post("/api/rule/execute/MixedCase")
                .header("Authorization", "Bearer " + TOKEN).contentType("application/json")
                .content("{\"params\":{}}")).andReturn().getResponse();
        assertEquals(202, waiting.getStatus());
        assertEquals("400006", JSON.parseObject(waiting.getContentAsString()).get("code"));
        assertEquals("waiting-trace", JSON.parseObject(waiting.getContentAsString()).getString("traceId"));
    }

    private MockMvc endpoint(BiFunction<String, Map<String, Object>, RuleResult> executor) {
        MockEnvironment env = new MockEnvironment().withProperty("RULE_ALLOWED_CODES", "MixedCase")
                .withProperty("RUNTIME_ACCESS_TOKEN", TOKEN);
        return MockMvcBuilders.standaloneSetup(new RuntimeController((code, input) -> executor.apply(code, input.getParams()), env))
                .setControllerAdvice(new RuntimeExceptionHandler())
                .build();
    }

    @Test
    public void sharedPathPreservesInputAndRejectsInvalidOrUnauthenticatedCalls() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        MockMvc mvc = endpoint((code, params) -> {
            executions.incrementAndGet();
            assertEquals("MixedCase", code);
            assertEquals(18, params.get("Age"));
            RuleResult result = new RuleResult();
            result.setSuccess(true);
            result.setResult(Map.of("decision", "REJECT"));
            return result;
        });
        String request = "{\"params\":{\"Age\":18}}";
        var ok = mvc.perform(post("/api/rule/execute/MixedCase").header("Authorization", "Bearer " + TOKEN)
                .contentType("application/json").content(request)).andReturn().getResponse();
        assertEquals(200, ok.getStatus());
        assertEquals("REJECT", JSON.parseObject(ok.getContentAsString()).getJSONObject("data")
                .getJSONObject("result").getString("decision"));
        assertEquals(401, mvc.perform(post("/api/rule/execute/MixedCase")
                .contentType("application/json").content(request)).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(post("/api/rule/execute/mixedcase").header("Authorization", "Bearer " + TOKEN)
                .contentType("application/json").content(request)).andReturn().getResponse().getStatus());
        for (String invalid : new String[]{"{}", "{\"params\":[]}"}) {
            assertEquals(400, mvc.perform(post("/api/rule/execute/MixedCase").header("Authorization", "Bearer " + TOKEN)
                    .contentType("application/json").content(invalid)).andReturn().getResponse().getStatus());
        }
        for (String removed : new String[]{"/api/http", "/api/sdk", "/api/example"}) {
            assertEquals(404, mvc.perform(post(removed + "/execute/MixedCase")
                    .header("Authorization", "Bearer " + TOKEN).contentType("application/json")
                    .content(request)).andReturn().getResponse().getStatus());
        }
        assertEquals(1, executions.get());
    }

    @Test
    public void globalAccessDoesNotRequireRuleWhitelist() throws Exception {
        MockEnvironment env = new MockEnvironment()
                .withProperty("RULE_AUTH_TYPE", "GLOBAL_KEY")
                .withProperty("RUNTIME_ACCESS_TOKEN", TOKEN);
        RuleResult result = new RuleResult();
        result.setSuccess(true);
        result.setResult("all-projects");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new RuntimeController((code, params) -> result, env))
                .build();

        var response = mvc.perform(post("/api/rule/execute/any-project-rule")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content("{\"params\":{}}"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
    }

    @Test
    public void sdkFetchesScriptOnceAndExecutesLocallyWithoutRemoteExecution() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        AtomicInteger remoteExecutions = new AtomicInteger();
        AtomicInteger badAuth = new AtomicInteger();
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/api/rule/sync/MixedCase", exchange -> {
            fetches.incrementAndGet();
            if (!"project-token".equals(exchange.getRequestHeaders().getFirst("X-Rule-Token"))) badAuth.incrementAndGet();
            byte[] json = ("{\"code\":\"000000\",\"success\":true,\"message\":\"成功\",\"data\":{\"ruleCode\":\"MixedCase\",\"definitionId\":7,"
                    + "\"projectCode\":\"ProjectA\",\"version\":1,\"modelType\":\"SCRIPT\","
                    + "\"compiledScript\":\"Age + 2\",\"requiresServerExecution\":false}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });
        upstream.createContext("/api/rule/sync/execute/", exchange -> {
            remoteExecutions.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        upstream.start();
        RedisConnectionFactory unusedRedis = (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(), new Class<?>[]{RedisConnectionFactory.class},
                (proxy, method, args) -> { throw new AssertionError("This request must only use the local cache"); });
        RuleEngineClient client = RuleEngineClient.builder().serverUrl("http://127.0.0.1:" + upstream.getAddress().getPort())
                .token("project-token").projectCode("ProjectA").projectId(1L).connectionFactory(unusedRedis)
                .serverSideExecution(false).logReportEnabled(false).traceEnabled(false).build();
        try {
            client.refreshRule("MixedCase");
            assertNotNull(client.getRuleInfo("MixedCase"));
            assertTrue(client.execute("MixedCase", Map.of("Age", 18)).isSuccess());
            MockMvc mvc = endpoint(client::execute);
            for (int age : new int[]{18, 33}) {
                var response = mvc.perform(post("/api/rule/execute/MixedCase")
                        .header("Authorization", "Bearer " + TOKEN).contentType("application/json")
                        .content("{\"params\":{\"Age\":" + age + "}}")).andReturn().getResponse();
                assertEquals(response.getContentAsString(), 200, response.getStatus());
                assertEquals(age + 2, JSON.parseObject(response.getContentAsString()).getJSONObject("data").getIntValue("result"));
            }
            assertEquals(1, fetches.get());
            assertEquals(0, remoteExecutions.get());
            assertEquals(0, badAuth.get());
        } finally {
            client.close();
            upstream.stop(0);
        }
    }
}
