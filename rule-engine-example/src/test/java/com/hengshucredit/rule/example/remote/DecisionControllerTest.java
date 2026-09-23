package com.hengshucredit.rule.example.remote;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.client.http.RuleHttpClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

public class DecisionControllerTest {
    @Test public void businessEndpointUsesRealHttpSdkAndNeverTreatsFailuresAsApproval() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/rule/sync/execute/RISK", exchange -> {
            calls.incrementAndGet();
            String input = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(Boolean.FALSE, JSON.parseObject(input).get("traceEnabled"));
            int age = JSON.parseObject(input).getJSONObject("params").getIntValue("age");
            String body = age < 0 ? "{\"code\":200,\"data\":{\"success\":false,\"errorMessage\":\"invalid age\"}}"
                    : "{\"code\":200,\"data\":{\"success\":true,\"result\":{\"decision\":\"REJECT\"}}}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try (RuleHttpClient client = RuleHttpClient.builder().serverUrl("http://127.0.0.1:" + server.getAddress().getPort()).token("test").build()) {
            MockEnvironment env = new MockEnvironment().withProperty("RULE_ALLOWED_CODES", "RISK,项目规则");
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new DecisionController(new DecisionService(client, env)))
                    .setControllerAdvice(new DecisionExceptionHandler()).build();
            MvcResult ok = mvc.perform(post("/api/example/execute").contentType("application/json")
                    .content("{\"ruleCode\":\"RISK\",\"params\":{\"age\":17}}" )).andReturn();
            assertEquals(200, ok.getResponse().getStatus());
            assertEquals("REJECT", JSON.parseObject(ok.getResponse().getContentAsString()).getJSONObject("result").getString("decision"));
            MvcResult failure = mvc.perform(post("/api/example/execute").contentType("application/json")
                    .content("{\"ruleCode\":\"RISK\",\"params\":{\"age\":-1}}" )).andReturn();
            assertEquals(422, failure.getResponse().getStatus());
            for (String invalid : new String[]{"{}", "{\"ruleCode\":\"NOT_ALLOWED\",\"params\":{}}", "{\"ruleCode\":\"RISK\",\"params\":[]}", "not json"}) {
                assertEquals(400, mvc.perform(post("/api/example/execute").contentType("application/json").content(invalid)).andReturn().getResponse().getStatus());
            }
            assertEquals(2, calls.get());
            server.stop(0);
            assertEquals(502, mvc.perform(post("/api/example/execute").contentType("application/json")
                    .content("{\"ruleCode\":\"RISK\",\"params\":{}}" )).andReturn().getResponse().getStatus());
            String list = mvc.perform(get("/api/example/rules")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertTrue(list.contains("项目规则"));
            assertFalse(list.contains("test"));
        } finally { server.stop(0); }
    }
}
