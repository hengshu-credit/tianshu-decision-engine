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

    private MockMvc endpoint(BiFunction<String, Map<String, Object>, RuleResult> executor) {
        MockEnvironment env = new MockEnvironment().withProperty("RULE_ALLOWED_CODES", "MixedCase")
                .withProperty("RUNTIME_ACCESS_TOKEN", TOKEN);
        return MockMvcBuilders.standaloneSetup(new RuntimeController(executor, env))
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
    public void sdkFetchesScriptOnceAndExecutesLocallyWithoutRemoteExecution() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        AtomicInteger remoteExecutions = new AtomicInteger();
        AtomicInteger badAuth = new AtomicInteger();
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/api/rule/sync/MixedCase", exchange -> {
            fetches.incrementAndGet();
            if (!"project-token".equals(exchange.getRequestHeaders().getFirst("X-Rule-Token"))) badAuth.incrementAndGet();
            byte[] json = ("{\"code\":200,\"data\":{\"ruleCode\":\"MixedCase\",\"definitionId\":7,"
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
