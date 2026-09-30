package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.core.engine.RequestContext;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ExternalApiInvokeServiceTest {

    @Test public void creditPendingCodesOnlyRepeatStatusQueryAndPreserveWholeLargeData() throws Exception {
        AtomicInteger authorizations = new AtomicInteger(); AtomicInteger polls = new AtomicInteger(); AtomicInteger reports = new AtomicInteger();
        Map<String, Object> features = new LinkedHashMap<>();
        for (int i = 0; i < 9937; i++) features.put("QY_" + i, i % 2 == 0 ? 0 : false);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/C9004", exchange -> { authorizations.incrementAndGet(); byte[] bytes = "{\"code\":\"0000\",\"data\":{\"serialNumber\":\"serial\"}}".getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close(); });
        server.createContext("/C9005", exchange -> { String code = polls.incrementAndGet() < 3 ? "B0013" : "0000"; byte[] bytes = ("{\"code\":\"" + code + "\",\"data\":{\"reportStatus\":\"01\"}}").getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close(); });
        server.createContext("/R9005", exchange -> { reports.incrementAndGet(); byte[] bytes = JSON.toJSONBytes(Map.of("code", "0000", "data", Map.of("code", "0000", "securityComputingResult", features, "ErrorInfo", ""))); exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close(); });
        server.start();
        try {
            RuleExternalApiConfig api = basicApiConfig(1L, 1L, "/C9004"); api.setRequestMode("ASYNC");
            api.setExecutionConfig("""
                {"version":2,"requestFields":[],"steps":[
                  {"id":"auth","type":"HTTP","endpointUrl":"/C9004","requestFields":[]},
                  {"id":"status","type":"HTTP","endpointUrl":"/C9005","requestFields":[{"id":"serial","location":"JSON","path":"serialNumber","required":true,"value":{"kind":"PATH","value":"steps.auth.body.data.serialNumber"}}],
                   "poll":{"maxAttempts":4,"intervalMs":1,"until":{"path":"response.body.code","operator":"==","value":"0000"},"failure":{"path":"response.body.code","operator":"not_in","values":["0000","B0013","B0015","B0016"]}}},
                  {"id":"report","type":"HTTP","endpointUrl":"/R9005","requestFields":[]}],
                 "responseBranches":[{"id":"data","mode":"VALUE","value":{"kind":"PATH","value":"response.body.data"}}]}
                """);
            var result = configuredService(api, httpDatasource(server)).invoke(api, Map.of());
            assertEquals(true, result.get("success")); assertEquals(1, authorizations.get()); assertEquals(3, polls.get()); assertEquals(1, reports.get());
            assertEquals(features, ExternalApiRequestPlan.read(result, "body.securityComputingResult"));
            assertEquals("", ExternalApiRequestPlan.read(result, "body.ErrorInfo"));
        } finally { server.stop(0); }
    }

    @Test public void authorizationFileUrlsAreOnlyDownloadedDuringInvocationAndCanBeZipped() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        AtomicInteger submissions = new AtomicInteger();
        AtomicReference<JSONObject> submitted = new AtomicReference<>();
        byte[] pdf = "%PDF-1.4\nlocal synthetic authorization\n%%EOF".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/authorization.pdf", exchange -> {
            downloads.incrementAndGet(); exchange.sendResponseHeaders(200, pdf.length);
            exchange.getResponseBody().write(pdf); exchange.close();
        });
        server.createContext("/authorization", exchange -> {
            submissions.incrementAndGet(); submitted.set(JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] response = "{\"code\":\"0000\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/authorization.pdf";
            RuleExternalApiConfig api = basicApiConfig(1L, 1L, "/authorization"); api.setRequestMethod("POST");
            api.setExecutionConfig(JSON.toJSONString(Map.of("version", 2, "requestFields", List.of(
                    Map.of("id", "ca", "location", "JSON", "path", "caFile", "required", true, "value", Map.of("kind", "LITERAL", "value", url), "file", Map.of("mode", "BASE64", "kind", "PDF", "maxBytes", 307200)),
                    Map.of("id", "other", "location", "JSON", "path", "otherFile", "value", Map.of("kind", "LITERAL", "value", url), "file", Map.of("mode", "ZIP_BASE64", "kind", "PDF", "maxBytes", 307200, "name", "application.pdf"))))));
            var service = configuredService(api, httpDatasource(server));
            var preview = service.previewRequest(api, Map.of(), null);
            assertEquals(0, downloads.get()); assertEquals(0, submissions.get());
            assertTrue(String.valueOf(ExternalApiRequestPlan.read(preview, "body.caFile")).contains("预览占位"));
            var response = service.invoke(api, Map.of());
            assertEquals(true, response.get("success")); assertEquals(1, downloads.get()); assertEquals(1, submissions.get());
            org.junit.Assert.assertArrayEquals(pdf, java.util.Base64.getDecoder().decode(submitted.get().getString("caFile")));
            try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(submitted.get().getString("otherFile"))))) {
                assertEquals("application.pdf", zip.getNextEntry().getName()); org.junit.Assert.assertArrayEquals(pdf, zip.readAllBytes());
            }
            api.setExecutionConfig(api.getExecutionConfig().replace("307200", "1"));
            var rejected = service.invoke(api, Map.of());
            assertEquals(false, rejected.get("success")); assertEquals(1, submissions.get());
            assertEquals(false, ExternalApiRequestPlan.read(rejected, "status.requestIssued"));
        } finally { server.stop(0); }
    }

    @Test
    public void unifiedWorkflowPreviewsWithoutRequestsAndKeepsRawResponsesAndAccurateStatus() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger pollStatus = new AtomicInteger(200);
        AtomicReference<String> submitted = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/submit", exchange -> {
            calls.incrementAndGet();
            submitted.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"task\":\"t1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/poll", exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"score\":88,\"state\":\"DONE\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(pollStatus.get(), body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            RuleExternalApiConfig api = basicApiConfig(1L, 1L, "/submit");
            api.setRequestMode("ASYNC");
            api.setExecutionConfig("""
                {"version":2,"requestFields":[{"id":"phone","location":"JSON","path":"phone","value":{"kind":"LITERAL","value":"13000000001"}}],
                 "steps":[{"id":"submit","type":"HTTP","endpointUrl":"/submit","requestMethod":"POST","sample":{"task":"t1"},
                    "requestFields":[{"id":"p","location":"JSON","path":"phone","value":{"kind":"PATH","value":"input.__apiFields.phone"}}]},
                    {"id":"poll","type":"HTTP","endpointUrl":"/poll","requestMethod":"GET","requestFields":[{"id":"t","location":"QUERY","path":"task","required":true,"value":{"kind":"PATH","value":"steps.submit.body.task"}}]}],
                 "responseBranches":[{"id":"result","outputFields":[{"id":"s","path":"score","value":{"kind":"PATH","value":"response.body.score"}}]}]}
                """);
            ExternalApiInvokeService service = configuredService(api, httpDatasource(server));
            ReflectionTestUtils.setField(service, "billingService", new RuleBillingService() {
                @Override public boolean recordApiExecution(RuleExternalApiConfig a, RuleExternalDatasource d, boolean success, Long cost, String error) { return false; }
            });
            var preview = service.previewRequest(api, Map.of(), null);
            assertEquals(false, preview.get("networkCalled"));
            assertEquals("t1", ExternalApiRequestPlan.read(preview, "steps.poll.request.query.task"));
            assertEquals(0, calls.get());
            var result = service.invoke(api, Map.of());
            assertEquals(2, calls.get());
            assertEquals("13000000001", JSON.parseObject(submitted.get()).getString("phone"));
            assertEquals(JSON.toJSONString(result), 88, ExternalApiRequestPlan.read(result, "body.score"));
            assertEquals("DONE", ExternalApiRequestPlan.read(result, "response.body.state"));
            assertEquals("t1", ExternalApiRequestPlan.read(result, "steps.submit.response.body.task"));
            assertEquals(0, ExternalApiRequestPlan.read(result, "status.retryCount"));
            assertEquals(false, ExternalApiRequestPlan.read(result, "status.billed"));
            List<?> stages = (List<?>) result.get("traceSteps");
            for (Object raw : stages) {
                Map<?, ?> step = (Map<?, ?>) raw;
                if ("WORKFLOW_STEP".equals(step.get("type"))) {
                    assertFalse(Boolean.TRUE.equals(ExternalApiRequestPlan.read(step, "output.truncated")));
                    assertNotNull(ExternalApiRequestPlan.read(step, "output.externalCall.traceSteps"));
                }
            }
            pollStatus.set(503);
            var failed = service.invoke(api, Map.of());
            assertEquals(false, failed.get("success"));
            assertEquals(503, ExternalApiRequestPlan.read(failed, "status.httpStatus"));
            assertEquals(4, calls.get());
        } finally { server.stop(0); }
    }

    @Test
    public void largeWorkflowResponseKeepsInnerTraceStagesWhileSummarizingPayload() throws Exception {
        String report = "x".repeat(40000);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/report", exchange -> {
            byte[] body = JSON.toJSONBytes(Map.of("code", "0000", "data", Map.of("report", report)));
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalApiConfig api = basicApiConfig(1L, 1L, "/report");
            api.setRequestMode("ASYNC");
            api.setExecutionConfig("""
                {"version":2,"requestFields":[],
                 "steps":[{"id":"report","type":"HTTP","endpointUrl":"/report","requestFields":[]}],
                 "responseBranches":[{"id":"result","mode":"VALUE","value":{"kind":"PATH","value":"response.body.data"}}]}
                """);
            var result = configuredService(api, httpDatasource(server)).invoke(api, Map.of());
            assertEquals(true, result.get("success"));
            assertEquals(report, ExternalApiRequestPlan.read(result, "body.report"));
            Map<?, ?> stage = ((List<?>) result.get("traceSteps")).stream()
                    .map(value -> (Map<?, ?>) value).filter(value -> "WORKFLOW_STEP".equals(value.get("type")))
                    .findFirst().orElseThrow();
            assertEquals(true, ExternalApiRequestPlan.read(stage, "output.truncated"));
            assertTrue("大报文摘要不能删除步骤内调用过程", stage.get("children") instanceof List<?>);
            List<?> children = (List<?>) stage.get("children");
            assertEquals(List.of("REQUEST_INPUT", "API_REQUEST", "AUTHENTICATION", "EXTERNAL_REQUEST", "EXTERNAL_RESPONSE", "RESPONSE_MAPPING"),
                    children.stream().map(value -> ((Map<?, ?>) value).get("type")).toList());
            assertEquals(true, ExternalApiRequestPlan.read(children.get(4), "output.truncated"));
            assertEquals(true, ExternalApiRequestPlan.read(children.get(5), "output.truncated"));
            for (int i = 0; i < children.size(); i++) {
                assertEquals(i + 1, ((Map<?, ?>) children.get(i)).get("sequence"));
                assertNotNull(((Map<?, ?>) children.get(i)).get("callId"));
            }
        } finally { server.stop(0); }
    }

    @Test public void responseConditionOperandsSupportNumericComparisonsAndPureExpressions() {
        var service = new ExternalApiInvokeService();
        String condition = "{\"type\":\"group\",\"operator\":\"AND\",\"children\":[{\"left\":{\"kind\":\"PATH\",\"value\":\"response.body.score\"},\"operator\":\">=\",\"right\":{\"kind\":\"LITERAL\",\"valueType\":\"NUMBER\",\"value\":80}}]}";
        assertTrue(service.matchesResponseCondition(condition, Map.of("response", Map.of("body", Map.of("score", 88)))));
        assertFalse(service.matchesResponseCondition(condition, Map.of("response", Map.of("body", Map.of("score", 60)))));
    }

    @Test
    public void businessTokenFailureRefreshesBeforeResponseMapping() throws Exception {
        AtomicInteger tokens = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/token", exchange -> {
            byte[] body = ("{\"token\":\"t" + tokens.incrementAndGet() + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/score", exchange -> {
            String result = calls.incrementAndGet() == 1 ? "{\"code\":\"TOKEN_EXPIRED\"}" : "{\"score\":720}";
            byte[] body = result.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = httpDatasource(server);
            datasource.setAuthType("TOKEN_API");
            datasource.setAuthConfig("{\"tokenUrl\":\"/token\",\"tokenPath\":\"body.token\"}");
            RuleExternalApiConfig config = basicApiConfig(1L, 1L, "/score");
            config.setAuthMode("INHERIT");
            com.alibaba.fastjson.JSONObject json = (com.alibaba.fastjson.JSONObject) com.alibaba.fastjson.JSON.toJSON(config);
            json.put("tokenFailureCondition", "{\"path\":\"body.code\",\"operator\":\"==\",\"value\":\"TOKEN_EXPIRED\"}");
            config = json.toJavaObject(RuleExternalApiConfig.class);
            Map<String, Object> result = configuredService(config, datasource).invoke(1L, Map.of());
            assertEquals(720, ((Map<?, ?>) result.get("body")).get("score"));
            assertEquals(2, tokens.get());
            assertEquals(2, calls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void inheritedBearerAuthAcceptsLegacyLowercaseTypes() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/score", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"score\":720}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = httpDatasource(server);
            datasource.setAuthType("bearer");
            datasource.setAuthConfig("{\"token\":\"legacy-token\"}");
            RuleExternalApiConfig config = basicApiConfig(2L, 1L, "/score");
            config.setAuthMode("inherit");

            Map<String, Object> result = configuredService(config, datasource).invoke(2L, Map.of());

            assertEquals(720, ((Map<?, ?>) result.get("body")).get("score"));
            assertEquals("Bearer legacy-token", authorization.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void disabledApiCannotBeInvokedByRuleExecution() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/disabled", exchange -> {
            providerCalls.incrementAndGet();
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = httpDatasource(server);
            RuleExternalApiConfig config = basicApiConfig(3L, 1L, "/disabled");
            config.setStatus(0);

            try {
                configuredService(config, datasource).invoke(3L, Map.of());
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("未启用"));
            }

            assertEquals(0, providerCalls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void invocationCacheKeyUsesConfiguredComponentsOrAllParamsHashByDefault() {
        RuleExternalApiConfig config = basicApiConfig(4L, 1L, "/score");
        config.setCacheKeyConfig("{\"components\":[{\"path\":\"customerId\"}]}");
        ExternalApiInvokeService service = new ExternalApiInvokeService();
        ReflectionTestUtils.setField(service, "apiConfigMapper",
                mapperProxy(RuleExternalApiConfigMapper.class, config));

        String configuredA = service.invocationCacheKey(4L,
                Map.of("customerId", "A", "trace", "1"));
        String configuredB = service.invocationCacheKey(4L,
                Map.of("customerId", "A", "trace", "2"));
        assertEquals("已配置缓存键时只由配置组件决定", configuredA, configuredB);

        config.setCacheKeyConfig(null);
        String defaultA = service.invocationCacheKey(4L,
                Map.of("customerId", "A", "trace", "1"));
        String defaultB = service.invocationCacheKey(4L,
                Map.of("customerId", "A", "trace", "2"));
        assertNotEquals("未配置缓存键时必须纳入全部请求参数", defaultA, defaultB);
    }

    @Test
    public void postIsNotRetriedWithoutExplicitIdempotencyConfirmation() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/charge", exchange -> {
            providerCalls.incrementAndGet();
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = httpDatasource(server);
            RuleExternalApiConfig config = basicApiConfig(5L, 1L, "/charge");
            config.setRequestMethod("POST");
            config.setRetryCount(2);
            config.setRetryStatusCodes("503");

            try {
                configuredService(config, datasource).invoke(5L, Map.of("amount", 100));
            } catch (ExternalApiInvokeService.ApiInvokeException ignored) {
                // expected
            }

            assertEquals(1, providerCalls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void asyncPollReturnsFinalResultInsteadOfSubmissionReceipt() throws Exception {
        AtomicInteger submissions = new AtomicInteger();
        AtomicInteger polls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/submit", exchange -> {
            submissions.incrementAndGet();
            byte[] body = "{\"taskId\":\"task-1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/result/task-1", exchange -> {
            String value = polls.incrementAndGet() == 1 ? "{\"status\":\"PENDING\"}"
                    : "{\"status\":\"SUCCESS\",\"data\":{\"score\":680}}";
            byte[] body = value.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalApiConfig config = basicApiConfig(1L, 1L, "/submit");
            config.setRequestMode("ASYNC");
            config.setAsyncResultMode("POLL");
            config.setAsyncPollConfig("{\"taskIdPath\":\"body.taskId\",\"resultEndpointUrl\":\"/result/${taskId}\","
                    + "\"requestMethod\":\"GET\",\"intervalMs\":1,\"maxAttempts\":3,"
                    + "\"statusPath\":\"body.status\",\"successValue\":\"SUCCESS\",\"resultPath\":\"body.data\"}");
            config.setPayloadCaptureConfig("{\"response\":{\"source\":\"ORIGINAL\",\"saveOriginal\":true}}");
            Map<String, Object> result = configuredService(config, httpDatasource(server)).invoke(1L, Map.of());
            assertEquals(680, ((Map<?, ?>) result.get("body")).get("score"));
            assertEquals(680, com.alibaba.fastjson.JSON.parseObject(
                    (String) result.get("originalResponseBody")).getJSONObject("data").getInteger("score").intValue());
            assertEquals(1, submissions.get());
            assertEquals(2, polls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void asyncGetPollingCanRetryWhenPostSubmissionRetryIsDisabled() throws Exception {
        AtomicInteger submissions = new AtomicInteger();
        AtomicInteger polls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/submit", exchange -> {
            submissions.incrementAndGet();
            byte[] body = "{\"taskId\":\"task-2\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/result/task-2", exchange -> {
            int attempt = polls.incrementAndGet();
            int status = attempt == 1 ? 503 : 200;
            byte[] body = (attempt == 1 ? "{\"status\":\"TEMPORARY\"}" : "{\"status\":\"SUCCESS\",\"data\":{\"score\":681}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalApiConfig config = basicApiConfig(7L, 1L, "/submit");
            config.setRequestMethod("POST");
            config.setRequestMode("ASYNC");
            config.setRetryCount(1);
            config.setAsyncResultMode("POLL");
            config.setAsyncPollConfig("{\"taskIdPath\":\"body.taskId\",\"resultEndpointUrl\":\"/result/${taskId}\","
                    + "\"requestMethod\":\"GET\",\"intervalMs\":1,\"maxAttempts\":3,"
                    + "\"statusPath\":\"body.status\",\"successValue\":\"SUCCESS\",\"resultPath\":\"body.data\"}");

            Map<String, Object> result = configuredService(config, httpDatasource(server)).invoke(7L, Map.of());

            assertEquals(681, ((Map<?, ?>) result.get("body")).get("score"));
            assertEquals(1, submissions.get());
            assertEquals(2, polls.get());
        } finally {
            server.stop(0);
        }
    }

    private RuleExternalDatasource httpDatasource(HttpServer server) {
        RuleExternalDatasource datasource = new RuleExternalDatasource();
        datasource.setId(1L);
        datasource.setProtocol("HTTP");
        datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        datasource.setAuthType("NONE");
        return datasource;
    }

    @Test
    public void callbackArrivingBeforeSubmissionResponseIsUsedByVariableAndRule() throws Exception {
        ExternalApiCallbackStore callbacks = ExternalApiCallbackStoreTest.store();
        AtomicReference<Throwable> callbackError = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/submit", exchange -> {
            try {
                var submitted = com.alibaba.fastjson.JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String url = submitted.getString("callback");
                byte[] body = "{\"job\":\"task-2\",\"status\":\"DONE\",\"report\":{\"score\":730}}".getBytes(StandardCharsets.UTF_8);
                callbacks.accept(url.substring(url.lastIndexOf('/') + 1), ExternalApiCallbackStoreTest.signature(body), body);
                byte[] receipt = "{\"taskId\":\"task-2\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(202, receipt.length);
                exchange.getResponseBody().write(receipt);
            } catch (Throwable error) {
                callbackError.set(error);
            } finally { exchange.close(); }
        });
        server.start();
        try {
            RuleExternalApiConfig config = basicApiConfig(1L, 1L, "/submit");
            config.setRequestMethod("POST");
            config.setRequestMode("ASYNC");
            config.setAsyncResultMode("CALLBACK");
            config.setAsyncCallbackConfig(ExternalApiCallbackStoreTest.protocol().toJSONString());
            config.setAsyncCallbackUrl("http://engine.example/api/external-callback/${invocationId}");
            config.setRequestMapping("{\"callback\":\"$.callbackUrl\"}");
            config.setPayloadCaptureConfig("{\"response\":{\"source\":\"ORIGINAL\",\"saveOriginal\":true}}");
            ExternalApiInvokeService api = configuredService(config, httpDatasource(server));
            ReflectionTestUtils.setField(api, "callbackStore", callbacks);
            Map<String, Object> directResult = api.invoke(1L, Map.of());
            assertEquals(730, com.alibaba.fastjson.JSON.parseObject(
                    (String) directResult.get("originalResponseBody")).getJSONObject("report").getInteger("score").intValue());
            var variable = new com.hengshucredit.rule.model.entity.RuleVariable();
            variable.setId(3L); variable.setProjectId(1L); variable.setVarCode("riskScore");
            variable.setVarSource("API"); variable.setStatus(1);
            variable.setSourceConfig("{\"apiConfigId\":1,\"resultPath\":\"body.score\"}");
            VariableSourceResolver resolver = new VariableSourceResolver();
            ReflectionTestUtils.setField(resolver, "variableService", new RuleVariableService() {
                @Override
                public java.util.List<com.hengshucredit.rule.model.entity.RuleVariable> listByProject(Long id, String source) {
                    return java.util.List.of(variable);
                }
            });
            ReflectionTestUtils.setField(resolver, "externalApiInvokeService", api);
            Map<String, Object> values = resolver.resolve(1L, Map.of());
            assertNull(callbackError.get());
            assertEquals(730, values.get("riskScore"));
            var runner = new com.alibaba.qlexpress4.Express4Runner(com.alibaba.qlexpress4.InitOptions.builder().build());
            assertEquals(true, runner.execute("riskScore > 700", values, com.alibaba.qlexpress4.QLOptions.builder().build()).getResult());
        } finally { server.stop(0); }
    }

    @Test
    public void asynchronousTimeoutDoesNotResubmitAndUsesConfiguredFallback() throws Exception {
        AtomicInteger submissions = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/submit", exchange -> {
            submissions.incrementAndGet();
            byte[] body = "{\"taskId\":\"t\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/poll", exchange -> {
            byte[] body = "{\"status\":\"PENDING\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            RuleExternalApiConfig config = basicApiConfig(1L, 1L, "/submit");
            config.setRequestMode("ASYNC"); config.setAsyncResultMode("POLL");
            config.setAsyncPollConfig("{\"taskIdPath\":\"body.taskId\",\"resultEndpointUrl\":\"/poll\",\"statusPath\":\"body.status\",\"successValue\":\"DONE\",\"intervalMs\":1,\"maxAttempts\":2}");
            config.setRetryCount(2); config.setRetryOnTimeout(1);
            config.setExceptionStrategy("RETURN_DEFAULT"); config.setFallbackValue("{\"score\":0}");
            Map<String, Object> result = configuredService(config, httpDatasource(server)).invoke(1L, Map.of());
            assertEquals(1, submissions.get());
            assertEquals(true, result.get("fallback"));
            assertEquals("TIMEOUT", result.get("sourceOutcome"));
            assertEquals(0, ((Map<?, ?>) result.get("body")).get("score"));
        } finally { server.stop(0); }
    }

    @Test
    public void apiTimeoutIsATotalDeadlineAcrossRetries() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/slow-unavailable", exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(80L);
                byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(503, response.length);
                exchange.getResponseBody().write(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(4L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("NONE");
            RuleExternalApiConfig config = basicApiConfig(4L, 4L, "/slow-unavailable");
            config.setTimeoutMs(100);
            config.setRetryCount(2);
            config.setRetryStatusCodes("503");
            ExternalApiInvokeService service = configuredService(config, datasource);

            long start = System.currentTimeMillis();
            try {
                service.invoke(4L, Collections.emptyMap());
            } catch (ExternalApiInvokeService.ApiInvokeException ignored) {
                // expected
            }

            assertTrue(System.currentTimeMillis() - start < 1000L);
            assertTrue(calls.get() < 3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void pendingAsyncInvocationResumesPollingWithoutResubmitting() throws Exception {
        AtomicInteger submissions = new AtomicInteger();
        AtomicInteger polls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/submit", exchange -> {
            submissions.incrementAndGet();
            byte[] body = "{\"taskId\":\"resume-task\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/poll/resume-task", exchange -> {
            int attempt = polls.incrementAndGet();
            byte[] body = (attempt == 1 ? "{\"status\":\"PENDING\"}"
                    : "{\"status\":\"SUCCESS\",\"data\":{\"score\":699}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalApiConfig config = basicApiConfig(91L, 1L, "/submit");
            config.setRequestMode("ASYNC");
            config.setAsyncResultMode("POLL");
            config.setAsyncTimeoutMs(1000);
            config.setAsyncPollConfig("{\"taskIdPath\":\"body.taskId\",\"resultEndpointUrl\":\"/poll/${taskId}\","
                    + "\"statusPath\":\"body.status\",\"successValue\":\"SUCCESS\",\"resultPath\":\"body.data\","
                    + "\"intervalMs\":2000,\"maxAttempts\":3}");
            ExternalApiInvokeService service = configuredService(config, httpDatasource(server));
            ExternalApiInvokeService.ApiInvokeException error;
            try {
                service.invoke(91L, Map.of());
                fail("第一次调用应因等待预算耗尽而返回可恢复异常");
                return;
            } catch (ExternalApiInvokeService.ApiInvokeException expected) {
                error = expected;
            }
            Map<String, Object> pending = error.getPendingAsync();
            assertNotNull(pending);
            assertEquals("resume-task", pending.get("taskId"));
            assertEquals(1, submissions.get());
            assertEquals(1, polls.get());

            ExternalApiRequestPlan plan = ExternalApiRequestPlan.prepare(config, Map.of(), Map.of(), Map.of());
            Map<String, Object> resumed = service.invokePending(plan, pending);
            assertEquals(true, resumed.get("success"));
            assertEquals(699, ((Map<?, ?>) resumed.get("body")).get("score"));
            assertEquals(1, submissions.get());
            assertEquals(2, polls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void externalRequestUsesRemainingProjectDeadlineAsItsTimeout() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(500L);
                byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(6L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("NONE");
            RuleExternalApiConfig config = basicApiConfig(6L, 6L, "/slow");
            config.setTimeoutMs(3000);
            ExternalApiInvokeService service = configuredService(config, datasource);

            long start = System.currentTimeMillis();
            boolean timeout = false;
            RequestDeadlineContext.start(50);
            try {
                service.invoke(6L, Collections.emptyMap());
            } catch (ExternalApiInvokeService.ApiInvokeException e) {
                timeout = true;
            } finally {
                RequestDeadlineContext.clear();
            }

            assertTrue(timeout);
            assertTrue(System.currentTimeMillis() - start < 1000L);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void clientErrorIsNotRetriedByGenericRetryPolicy() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/bad-request", exchange -> {
            providerCalls.incrementAndGet();
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(5L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("NONE");
            RuleExternalApiConfig config = basicApiConfig(5L, 5L, "/bad-request");
            config.setRetryCount(2);
            ExternalApiInvokeService service = configuredService(config, datasource);

            try {
                service.invoke(5L, Collections.emptyMap());
            } catch (ExternalApiInvokeService.ApiInvokeException ignored) {
                // expected
            }

            assertEquals(1, providerCalls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void businessRateLimitResponseRetriesByConditionTreeAndThenSucceeds() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/business-retry", exchange -> {
            int attempt = providerCalls.incrementAndGet();
            String json = attempt < 3
                    ? "{\"response_code\":\"10000429\",\"message\":\"too frequent\"}"
                    : "{\"response_code\":\"00\",\"message\":\"success\",\"result\":{\"score\":720}}";
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(51L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("NONE");
            RuleExternalApiConfig config = basicApiConfig(51L, 51L, "/business-retry");
            config.setRequestMethod("POST");
            config.setRetryCount(3);
            config.setRetryNonIdempotent(1);
            config.setSuccessCondition("{\"type\":\"condition\",\"path\":\"body.response_code\",\"operator\":\"==\",\"value\":\"00\"}");
            config.setRetryCondition("{\"type\":\"condition\",\"path\":\"body.response_code\",\"operator\":\"==\",\"value\":\"10000429\"}");
            ExternalApiInvokeService service = configuredService(config, datasource);

            Map<String, Object> result = service.invoke(51L, Collections.emptyMap());

            assertEquals(3, providerCalls.get());
            assertEquals(Boolean.TRUE, result.get("success"));
            assertEquals("00", ((Map<?, ?>) result.get("body")).get("response_code"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void nonRetryableBusinessErrorFailsWithoutAnotherProviderCall() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/business-error", exchange -> {
            providerCalls.incrementAndGet();
            byte[] response = ("{\"response_code\":\"20100103\","
                    + "\"message\":\"invalid parameter\",\"trace_id\":\"trace-error\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(52L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("NONE");
            RuleExternalApiConfig config = basicApiConfig(52L, 52L, "/business-error");
            config.setRetryCount(3);
            config.setSuccessCondition("{\"type\":\"condition\",\"path\":\"body.response_code\",\"operator\":\"==\",\"value\":\"00\"}");
            config.setRetryCondition("{\"type\":\"condition\",\"path\":\"body.response_code\",\"operator\":\"==\",\"value\":\"10000429\"}");
            ExternalApiInvokeService service = configuredService(config, datasource);

            boolean failed = false;
            try {
                service.invoke(52L, Collections.emptyMap());
            } catch (ExternalApiInvokeService.ApiInvokeException e) {
                failed = true;
                assertTrue(e.getMessage().contains("20100103"));
            }

            assertTrue(failed);
            assertEquals(1, providerCalls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void previewInvocationUsesCurrentDraftConfigInsteadOfPersistedConfig() throws Exception {
        AtomicReference<String> requestedPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/saved", exchange -> {
            requestedPath.set("saved");
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/draft", exchange -> {
            requestedPath.set("draft");
            byte[] response = "{\"score\":88}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(8L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("NONE");

            RuleExternalApiConfig saved = basicApiConfig(100L, 8L, "/saved");
            RuleExternalApiConfig draft = basicApiConfig(100L, 8L, "/draft");
            saved.setResponseCacheSeconds(60);
            draft.setResponseCacheSeconds(60);
            ExternalApiInvokeService service = new ExternalApiInvokeService();
            ReflectionTestUtils.setField(service, "apiConfigMapper",
                    mapperProxy(RuleExternalApiConfigMapper.class, saved));
            ReflectionTestUtils.setField(service, "datasourceMapper",
                    mapperProxy(RuleExternalDatasourceMapper.class, datasource));
            ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());

            service.invoke(100L, Collections.emptyMap());
            assertEquals("saved", requestedPath.get());
            Map<String, Object> result = service.invoke(draft, Collections.emptyMap());

            assertEquals("draft", requestedPath.get());
            assertTrue(String.valueOf(result.get("body")).contains("88"));
        } finally {
            server.stop(0);
        }
    }

    private RuleExternalApiConfig basicApiConfig(Long id, Long datasourceId, String endpointUrl) {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setId(id);
        config.setDatasourceId(datasourceId);
        config.setRequestMethod("GET");
        config.setEndpointUrl(endpointUrl);
        config.setContentType("application/json");
        config.setAuthMode("NONE");
        config.setResponseCacheSeconds(0);
        config.setTimeoutMs(3000);
        config.setRetryCount(0);
        config.setRetryIntervalMs(0);
        config.setExceptionStrategy("FAIL_FAST");
        return config;
    }

    @SuppressWarnings("unchecked")
    private List<JSONObject> steps(Object value) {
        if (value instanceof String) value = JSON.parse((String) value);
        List<JSONObject> result = new ArrayList<>();
        if (value instanceof Iterable) {
            for (Object item : (Iterable<Object>) value) {
                result.add(item instanceof JSONObject ? (JSONObject) item : JSON.parseObject(JSON.toJSONString(item)));
            }
        }
        return result;
    }

    private List<String> stepTypes(List<JSONObject> steps) {
        List<String> result = new ArrayList<>();
        for (JSONObject step : steps) result.add(step.getString("type"));
        return result;
    }

    @Test
    public void responseMappingReplacesBodyWithMappedFields() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setResponseMapping("{\"score\":\"body.data.score\",\"level\":\"$.body.data.level\",\"ok\":\"success\",\"missing\":\"body.notFound\"}");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("score", 88);
        data.put("level", "A");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("body", body);

        Map<String, Object> mappedResponse = new ExternalApiInvokeService().applyResponseMapping(config, response);

        assertSame(body, mappedResponse.get("rawBody"));
        Map<?, ?> mapped = (Map<?, ?>) mappedResponse.get("body");
        assertEquals(88, mapped.get("score"));
        assertEquals("A", mapped.get("level"));
        assertEquals(Boolean.TRUE, mapped.get("ok"));
        assertEquals(null, mapped.get("missing"));
        assertSame(mapped, mappedResponse.get("mapped"));
    }

    @Test
    public void exceptionConditionSupportsMissingEmptyAndTypeChecks() {
        ExternalApiInvokeService service = new ExternalApiInvokeService();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("body", new LinkedHashMap<>(Map.of("empty", "", "score", 88, "flag", true)));

        assertTrue(service.matchesResponseCondition("{\"path\":\"body.missing\",\"operator\":\"missing\"}", response));
        assertTrue(service.matchesResponseCondition("{\"path\":\"body.empty\",\"operator\":\"is_empty\"}", response));
        assertTrue(service.matchesResponseCondition("{\"path\":\"body.score\",\"operator\":\"type_is\",\"value\":\"NUMBER\"}", response));
        assertTrue(service.matchesResponseCondition("{\"path\":\"body.flag\",\"operator\":\"type_changed\",\"value\":\"STRING\"}", response));
        assertFalse(service.matchesResponseCondition("{\"path\":\"body.missing\",\"operator\":\"is_empty\"}", response));
    }

    @Test
    public void exceptionConditionCanClassifyHttpErrorBodyBeforeFallback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/error", exchange -> {
            byte[] body = "{\"errorCode\":\"MAINTENANCE\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalApiConfig config = basicApiConfig(92L, 1L, "/error");
            config.setExceptionCondition("{\"path\":\"body.errorCode\",\"operator\":\"==\",\"value\":\"MAINTENANCE\"}");
            config.setExceptionStrategy("RETURN_DEFAULT");
            config.setFallbackValue("{\"available\":false}");
            Map<String, Object> result = configuredService(config, httpDatasource(server)).invoke(92L, Map.of());
            assertEquals(true, result.get("fallback"));
            assertEquals(false, ((Map<?, ?>) result.get("body")).get("available"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void workflowStepExceptionConditionTreeClassifiesItsOwnErrorResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/step-error", exchange -> {
            byte[] body = "{\"errorCode\":\"MAINTENANCE\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalApiConfig config = basicApiConfig(93L, 1L, "/step-error");
            config.setRequestMode("ASYNC");
            config.setExceptionStrategy("RETURN_DEFAULT");
            config.setExecutionConfig("""
                {"version":2,"requestFields":[],"steps":[
                  {"id":"provider","type":"HTTP","endpointUrl":"/step-error","requestFields":[],
                   "exceptionConditionTree":{"type":"group","operator":"AND","children":[
                     {"path":"response.body.errorCode","operator":"==","value":"MAINTENANCE"}]}}
                ]}
                """);
            Map<String, Object> result = configuredService(config, httpDatasource(server)).invoke(93L, Map.of());
            assertEquals(false, result.get("success"));
            assertTrue(JSON.toJSONString(result.get("steps")).contains("\"type\":\"EXCEPTION_CONDITION\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void responseMappingUsesFirstAvailablePathForDynamicStructures() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setResponseMapping("{\"score\":[\"body.data.score\",\"body.model_params.br_applyloanstr_v2.score\",\"body.score\"],\"firstReason\":\"body.reasons.0.code\"}");

        Map<String, Object> v1 = new LinkedHashMap<>();
        v1.put("score", 661.8);
        Map<String, Object> modelParams = new LinkedHashMap<>();
        modelParams.put("br_applyloanstr_v2", v1);
        Map<String, Object> reason = new LinkedHashMap<>();
        reason.put("code", "R001");
        ArrayList<Object> reasons = new ArrayList<>();
        reasons.add(reason);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model_params", modelParams);
        body.put("reasons", reasons);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("body", body);

        Map<String, Object> mappedResponse = new ExternalApiInvokeService().applyResponseMapping(config, response);

        Map<?, ?> mapped = (Map<?, ?>) mappedResponse.get("body");
        assertEquals(661.8, ((Number) mapped.get("score")).doubleValue(), 0.000001);
        assertEquals("R001", mapped.get("firstReason"));
    }

    @Test
    public void requestPathPresenceSupportsArrayAndQuotedObjectSegments() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("score.value", 88);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("items", List.of(item));
        ExternalApiInvokeService service = new ExternalApiInvokeService();
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(service, "containsPath",
                input, "$.items[0]['score.value']"));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(service, "containsPath",
                input, "$.items[1]['score.value']"));
    }

    @Test
    public void responseMappingSupportsConditionalCasesAndDefaultValue() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setResponseMapping("{\"riskScore\":{\"cases\":[{\"when\":{\"path\":\"body.code\",\"operator\":\"==\",\"value\":\"00\"},\"path\":\"body.data.score\"},{\"when\":{\"path\":\"body.code\",\"operator\":\"!=\",\"value\":\"00\"},\"path\":\"body.error.score\"}],\"default\":-1}}");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "E001");
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("score", 0);
        body.put("error", error);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("body", body);

        Map<String, Object> mappedResponse = new ExternalApiInvokeService().applyResponseMapping(config, response);

        assertEquals(0, ((Map<?, ?>) mappedResponse.get("body")).get("riskScore"));
    }

    @Test
    public void responseMappingUsesDefaultWhenConditionalCasesHaveNoValue() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setResponseMapping("{\"riskScore\":{\"cases\":[{\"when\":{\"path\":\"body.code\",\"value\":\"00\"},\"path\":\"body.data.score\"}],\"default\":-1}}");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "00");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("body", body);

        Map<String, Object> mappedResponse = new ExternalApiInvokeService().applyResponseMapping(config, response);

        assertEquals(-1, ((Map<?, ?>) mappedResponse.get("body")).get("riskScore"));
    }

    @Test
    public void responseMappingSupportsNestedConditionTreeAndFallbackCase() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setResponseMapping("{\"riskScore\":{\"cases\":["
                + "{\"when\":{\"type\":\"group\",\"op\":\"AND\",\"children\":["
                + "{\"type\":\"leaf\",\"varCode\":\"body.code\",\"operator\":\"==\",\"value\":\"00\"},"
                + "{\"type\":\"group\",\"op\":\"OR\",\"children\":["
                + "{\"type\":\"leaf\",\"varCode\":\"body.data.level\",\"operator\":\"==\",\"value\":\"A\"},"
                + "{\"type\":\"leaf\",\"varCode\":\"body.data.score\",\"operator\":\">=\",\"value\":\"700\",\"varType\":\"NUMBER\"}"
                + "]}]},\"path\":\"body.data.score\"},"
                + "{\"path\":\"body.error.score\"}"
                + "]}}");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("level", "B");
        data.put("score", 720);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "00");
        body.put("data", data);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("body", body);

        Map<String, Object> mappedResponse = new ExternalApiInvokeService().applyResponseMapping(config, response);

        assertEquals(720, ((Map<?, ?>) mappedResponse.get("body")).get("riskScore"));

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("score", -2);
        Map<String, Object> fallbackBody = new LinkedHashMap<>();
        fallbackBody.put("code", "E001");
        fallbackBody.put("error", error);
        Map<String, Object> fallbackResponse = new LinkedHashMap<>();
        fallbackResponse.put("success", true);
        fallbackResponse.put("body", fallbackBody);
        mappedResponse = new ExternalApiInvokeService().applyResponseMapping(config, fallbackResponse);

        assertEquals(-2, ((Map<?, ?>) mappedResponse.get("body")).get("riskScore"));
    }

    @Test
    public void tokenRequestBodyUsesMultipartFormWhenConfigured() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "shujupingtai2");
        body.put("password", "${password}");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("password", "md5-password");

        Object requestBody = new ExternalApiInvokeService().buildTokenRequestBody(
                body, params, MediaType.MULTIPART_FORM_DATA);

        assertTrue(requestBody instanceof MultiValueMap);
        @SuppressWarnings("unchecked")
        MultiValueMap<String, Object> form = (MultiValueMap<String, Object>) requestBody;
        assertEquals("shujupingtai2", form.getFirst("username"));
        assertEquals("md5-password", form.getFirst("password"));
    }

    @Test
    public void tokenRequestBodyKeepsJsonObjectByDefault() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "user");

        Object requestBody = new ExternalApiInvokeService().buildTokenRequestBody(
                body, new LinkedHashMap<>(), MediaType.APPLICATION_JSON);

        assertEquals(body, requestBody);
    }

    @Test
    public void apiRequestBodyUsesFormEncodingWhenConfigured() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("member_id", "merchant-1");
        body.put("data_type", "json");
        body.put("empty", null);

        Object requestBody = new ExternalApiInvokeService().buildHttpRequestBody(
                body, MediaType.APPLICATION_FORM_URLENCODED);

        assertTrue(requestBody instanceof MultiValueMap);
        assertEquals("merchant-1", firstFormValue(requestBody, "member_id"));
        assertEquals("json", firstFormValue(requestBody, "data_type"));
        assertEquals(null, firstFormValue(requestBody, "empty"));
    }

    @SuppressWarnings("unchecked")
    private static Object firstFormValue(Object value, String key) {
        return ((MultiValueMap<String, ?>) value).getFirst(key);
    }

    @Test
    public void formRequestRawPayloadUsesWireCompatibleEncoding() {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", "张三");
        form.add("note", "a b");

        String raw = ReflectionTestUtils.invokeMethod(new ExternalApiInvokeService(),
                "rawRequestPayload", form, MediaType.APPLICATION_FORM_URLENCODED);

        assertEquals("name=%E5%BC%A0%E4%B8%89&note=a+b", raw);
    }

    @Test
    public void tokenContentTypeDefaultsToJson() {
        assertEquals(MediaType.APPLICATION_JSON,
                new ExternalApiInvokeService().resolveTokenContentType(new LinkedHashMap<>()));
    }

    @Test
    public void responseCacheKeyUsesConfiguredComponentsInOrderAndMasksSensitiveValues() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "张三");
        params.put("idCard", "110101199001010010");
        params.put("mobile", "13800138000");
        String config = "{\"components\":[{\"path\":\"name\"},{\"path\":\"idCard\"},{\"path\":\"mobile\"}]}";

        ExternalApiInvokeService service = new ExternalApiInvokeService();
        String key = service.buildResponseCacheKey(7L, config, params);
        Map<String, Object> changed = new LinkedHashMap<>(params);
        changed.put("mobile", "13900139000");

        assertTrue(key.startsWith("7:"));
        assertFalse(key.contains("张三"));
        assertFalse(key.contains("110101199001010010"));
        assertNotEquals(key, service.buildResponseCacheKey(7L, config, changed));
        assertNull(service.buildResponseCacheKey(7L, config,
                Collections.singletonMap("name", "张三")));
    }

    @Test
    public void globalDatasourceLogUsesProjectFromActiveRuleContext() {
        RuleExternalDatasource datasource = new RuleExternalDatasource();
        datasource.setProjectId(0L);
        RequestContext context = new RequestContext();
        context.setRuleContext(Map.of("projectId", 88L, "traceId", "root-88"), List.of());
        try (RuntimeContextBridge.ContextScope ignored = RuntimeContextBridge.install(context)) {
            Long projectId = ReflectionTestUtils.invokeMethod(new ExternalApiInvokeService(),
                    "resolveRuntimeProjectId", datasource);
            assertEquals(Long.valueOf(88L), projectId);
        }
    }

    @Test
    public void responseCacheKeyDefaultsToAllRequestParamsWhenNotConfigured() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("name", "张三");
        first.put("nested", Map.of("score", 88, "level", "A"));
        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("nested", Map.of("level", "A", "score", 88));
        reordered.put("name", "张三");
        Map<String, Object> changed = new LinkedHashMap<>(first);
        changed.put("name", "李四");

        ExternalApiInvokeService service = new ExternalApiInvokeService();
        String key = service.buildResponseCacheKey(8L, null, first);

        assertTrue(key.startsWith("8:"));
        assertFalse(key.contains("张三"));
        assertEquals(key, service.buildResponseCacheKey(8L, "", reordered));
        assertNotEquals(key, service.buildResponseCacheKey(8L, null, changed));
    }

    @Test
    public void copyCachedResponseMarksCacheStateAndKeepsBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("score", 88);
        Map<String, Object> cached = new LinkedHashMap<>();
        cached.put("success", true);
        cached.put("body", body);

        Map<String, Object> response = new ExternalApiInvokeService()
                .copyCachedResponse(cached, true, true, 5);

        assertEquals(Boolean.TRUE, response.get("success"));
        assertEquals(Boolean.TRUE, response.get("cached"));
        assertEquals(Boolean.TRUE, response.get("cacheStale"));
        assertEquals("STALE", response.get("cacheStatus"));
        assertEquals("STALE_CACHE", response.get("dataOrigin"));
        assertEquals(5L, response.get("costTimeMs"));
        assertEquals(88, ((Map<?, ?>) response.get("body")).get("score"));
    }

    @Test
    public void cachedTraceRestoresCapturedRequestAndRequestStageFields() throws Exception {
        ExternalApiInvokeService service = new ExternalApiInvokeService();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", "POST");
        request.put("url", "https://provider.example/score");
        request.put("headers", Map.of("X-Request-Id", "req-1"));
        request.put("params", Map.of("customerId", "c-1"));
        request.put("body", Map.of("score", 88));
        Map<String, Object> cached = new LinkedHashMap<>();
        cached.put("body", Map.of("decision", "PASS"));
        cached.put("rawRequestBody", "{\"score\":88}");
        cached.put("rawRequestMetadata", Map.of("status", "CAPTURED"));
        cached.put("originalRequestBody", "{\"score\":88,\"attachment\":\"large\"}");
        cached.put("rawResponseBody", "{\"decision\":\"PASS\"}");
        cached.put("rawResponseMetadata", Map.of("status", "CAPTURED"));
        cached.put("originalResponseBody", "{\"decision\":\"PASS\"}");
        cached.put("responseStatus", 200);
        cached.put("externalCall", Map.of("request", request));

        Class<?> traceType = java.util.Arrays.stream(ExternalApiInvokeService.class.getDeclaredClasses())
                .filter(type -> "InvokeTrace".equals(type.getSimpleName())).findFirst().orElseThrow();
        var constructor = traceType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object trace = constructor.newInstance();
        ReflectionTestUtils.invokeMethod(service, "restoreCachedTrace", cached, trace);

        assertEquals("{\"score\":88}", ReflectionTestUtils.getField(trace, "rawRequestBody"));
        assertEquals(Map.of("status", "CAPTURED"), ReflectionTestUtils.getField(trace, "rawRequestMetadata"));
        assertEquals("POST", ReflectionTestUtils.getField(trace, "requestMethod"));
        assertEquals("https://provider.example/score", ReflectionTestUtils.getField(trace, "requestUrl"));
        assertEquals(Map.of("customerId", "c-1"), ReflectionTestUtils.getField(trace, "requestParams"));
        assertEquals(Map.of("score", 88), ReflectionTestUtils.getField(trace, "requestBody"));
        assertEquals(200, ReflectionTestUtils.getField(trace, "responseStatus"));
    }

    @Test
    public void billingConditionMatchesMappedResponsePath() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "0");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("body", body);

        ExternalApiInvokeService service = new ExternalApiInvokeService();

        assertTrue(service.matchesBillingCondition(
                "{\"path\":\"body.status\",\"operator\":\"==\",\"value\":0}", response));
        assertTrue(service.matchesBillingCondition(
                "{\"path\":\"body.status\",\"operator\":\"!=\",\"value\":1}", response));
    }

    @Test
    public void billingConditionRejectsUnmatchedResponsePath() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 1);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("body", body);

        boolean matched = new ExternalApiInvokeService().matchesBillingCondition(
                "{\"path\":\"body.status\",\"operator\":\"==\",\"value\":0}", response);

        assertEquals(false, matched);
    }

    @Test
    public void responseConditionTreeSupportsNestedMultiSelectAndStringOperators() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "2001");
        body.put("message", "SUCCESS-001");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("httpStatus", 200);
        response.put("body", body);
        String condition = "{\"type\":\"group\",\"operator\":\"AND\",\"children\":["
                + "{\"path\":\"httpStatus\",\"operator\":\"starts_with\",\"value\":\"2\"},"
                + "{\"type\":\"group\",\"operator\":\"OR\",\"children\":["
                + "{\"path\":\"body.code\",\"operator\":\"in\",\"values\":[\"2000\",\"2001\"]},"
                + "{\"path\":\"body.message\",\"operator\":\"regex\",\"value\":\"^OK-.*\"}]}]}";

        ExternalApiInvokeService service = new ExternalApiInvokeService();

        assertTrue(service.matchesResponseCondition(condition, response));
        assertFalse(service.matchesResponseCondition(
                "{\"path\":\"body.code\",\"operator\":\"not_in\",\"values\":[\"2001\",\"2002\"]}",
                response));
        assertTrue(service.matchesResponseCondition(
                "{\"path\":\"body.message\",\"operator\":\"regex\",\"value\":\"^SUCCESS-\\\\d+$\"}",
                response));
    }

    @Test
    public void tianshuDatasourceUsesLocalRuleEnginePathEvenWhenLegacyProtocolIsHttps() {
        RuleExternalDatasource datasource = new RuleExternalDatasource();
        datasource.setProtocol("HTTPS");
        datasource.setDatasourceCode("tianshu_rule_engine");

        assertTrue(new ExternalApiInvokeService().isRuleEngineDatasource(datasource));
    }

    @Test
    public void invokeUsesResponseCacheWithinTtl() throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/score", exchange -> {
            callCount.incrementAndGet();
            byte[] response = "{\"data\":{\"score\":88}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(7L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("NONE");

            RuleExternalApiConfig config = new RuleExternalApiConfig();
            config.setId(99L);
            config.setDatasourceId(7L);
            config.setRequestMethod("POST");
            config.setEndpointUrl("/score");
            config.setContentType("application/json");
            config.setResponseCacheSeconds(60);
            config.setTimeoutMs(3000);
            config.setRetryCount(0);
            config.setRetryIntervalMs(0);
            config.setExceptionStrategy("FAIL_FAST");

            ExternalApiInvokeService service = new ExternalApiInvokeService();
            ReflectionTestUtils.setField(service, "apiConfigMapper",
                    mapperProxy(RuleExternalApiConfigMapper.class, config));
            ReflectionTestUtils.setField(service, "datasourceMapper",
                    mapperProxy(RuleExternalDatasourceMapper.class, datasource));
            ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("request_id", "r1");
            params.put("trace", "same-request");
            Map<String, Object> first = service.invoke(99L, params);
            Map<String, Object> second = service.invoke(99L, params);

            assertEquals(1, callCount.get());
            assertEquals(Boolean.FALSE, first.get("cached"));
            assertEquals(Boolean.TRUE, second.get("cached"));
            assertEquals(Boolean.TRUE, first.get("cacheConfigured"));
            assertEquals("MISS", first.get("cacheStatus"));
            assertEquals("LIVE", first.get("dataOrigin"));
            assertEquals("HIT", second.get("cacheStatus"));
            assertEquals("CACHE", second.get("dataOrigin"));
            assertEquals(0L, second.get("costTimeMs"));
            assertEquals(first.get("rawResponseBody"), second.get("rawResponseBody"));
            assertNotNull(second.get("callId"));
            assertNotEquals(first.get("callId"), second.get("callId"));
            List<JSONObject> cachedSteps = steps(second.get("traceSteps"));
            assertEquals(List.of("REQUEST_INPUT", "CACHE_RESULT"), stepTypes(cachedSteps));
            assertEquals(Map.of("data", Map.of("score", 88)), cachedSteps.get(1).get("output"));
            for (JSONObject step : cachedSteps) assertEquals(second.get("callId"), step.get("callId"));
            assertEquals(88, ((Map<?, ?>) second.get("body")).get("data") instanceof Map
                    ? ((Map<?, ?>) ((Map<?, ?>) second.get("body")).get("data")).get("score")
                    : null);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void incompleteConfiguredCacheKeyBypassesCacheAndIsLogged() throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/score", exchange -> {
            callCount.incrementAndGet();
            byte[] response = "{\"code\":\"2000\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(8L);
            datasource.setProjectId(3L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("NONE");
            RuleExternalApiConfig config = basicApiConfig(101L, 8L, "/score");
            config.setApiCode("credit_score");
            config.setApiName("信用评分");
            config.setResponseCacheSeconds(60);
            config.setCacheKeyConfig("{\"components\":[{\"path\":\"name\"},{\"path\":\"idCard\"}]}");
            config.setSuccessCondition("{\"path\":\"body.code\",\"operator\":\"starts_with\",\"value\":\"2\"}");
            config.setBillingCondition("{\"path\":\"body.code\",\"operator\":\"in\",\"values\":[\"2000\",\"2001\"]}");
            RecordingRuntimeCallLogService logs = new RecordingRuntimeCallLogService();
            ExternalApiInvokeService service = configuredService(config, datasource);
            ReflectionTestUtils.setField(service, "runtimeCallLogService", logs);
            Map<String, Object> params = Collections.singletonMap("name", "张三");

            Map<String, Object> firstResult = service.invoke(101L, params);
            service.invoke(101L, params);

            assertEquals(2, callCount.get());
            java.util.List<RuleRuntimeCallLog> summaries = new ArrayList<>();
            for (RuleRuntimeCallLog item : logs.logs) {
                if ("API_INVOKE".equals(item.getActionType())) summaries.add(item);
            }
            assertEquals(2, summaries.size());
            RuleRuntimeCallLog log = summaries.get(0);
            assertEquals("CACHE_KEY_INCOMPLETE", log.getCacheStatus());
            assertNull(log.getCacheKey());
            assertEquals(Integer.valueOf(1), log.getProviderRequest());
            assertEquals(Integer.valueOf(1), log.getRequestSuccess());
            assertEquals(Integer.valueOf(1), log.getFound());
            assertNotNull(log.getCallId());
            assertNull(log.getRawRequestBody());
            assertEquals("{\"code\":\"2000\"}", log.getRawResponseBody());
            assertEquals(log.getCallId(), firstResult.get("callId"));
            assertNull(firstResult.get("rawRequestBody"));
            assertEquals("{\"code\":\"2000\"}", firstResult.get("rawResponseBody"));
            assertTrue(firstResult.get("externalCall") instanceof Map);
            assertEquals(List.of("REQUEST_INPUT", "API_REQUEST", "AUTHENTICATION", "EXTERNAL_REQUEST", "EXTERNAL_RESPONSE"),
                    stepTypes(steps(log.getTraceSteps())));
            assertTrue(firstResult.get("traceSteps") instanceof java.util.List);
            RuleRuntimeCallLog attempt = logs.logs.stream()
                    .filter(item -> "API_ATTEMPT".equals(item.getActionType()))
                    .findFirst().orElseThrow();
            assertEquals(log.getCallId(), attempt.getCallId());
            assertEquals(log.getRawResponseBody(), attempt.getRawResponseBody());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void invokeBuildsNestedRequestBodyFromRequestMapping() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/score", exchange -> {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int len;
            while ((len = exchange.getRequestBody().read(chunk)) >= 0) {
                buffer.write(chunk, 0, len);
            }
            requestBody.set(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            byte[] response = "{\"code\":\"00\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(7L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("NONE");

            RuleExternalApiConfig config = new RuleExternalApiConfig();
            config.setId(99L);
            config.setDatasourceId(7L);
            config.setRequestMethod("POST");
            config.setEndpointUrl("/score");
            config.setContentType("application/json");
            config.setRequestMapping("{\"request_id\":\"$.request_id\",\"model_id\":\"$.model_id\",\"model_params\":{\"br_applyloanstr_v2\":\"$.model_params.br_applyloanstr_v2\"}}");
            config.setResponseCacheSeconds(0);
            config.setTimeoutMs(3000);
            config.setRetryCount(0);
            config.setRetryIntervalMs(0);
            config.setExceptionStrategy("FAIL_FAST");

            ExternalApiInvokeService service = new ExternalApiInvokeService();
            ReflectionTestUtils.setField(service, "apiConfigMapper",
                    mapperProxy(RuleExternalApiConfigMapper.class, config));
            ReflectionTestUtils.setField(service, "datasourceMapper",
                    mapperProxy(RuleExternalDatasourceMapper.class, datasource));
            ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());

            Map<String, Object> scoreParams = new LinkedHashMap<>();
            scoreParams.put("swift_number", "3010685_20240221073528_32822730A");
            Map<String, Object> modelParams = new LinkedHashMap<>();
            modelParams.put("br_applyloanstr_v2", scoreParams);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("request_id", "20a22af66cafc72a64a4e91b53fdda81");
            params.put("model_id", "20a22af66cafc72a64a4e91b53fdda81");
            params.put("model_params", modelParams);

            service.invoke(99L, params);

            assertTrue(requestBody.get().contains("\"request_id\":\"20a22af66cafc72a64a4e91b53fdda81\""));
            assertTrue(requestBody.get().contains("\"model_params\":{\"br_applyloanstr_v2\""));
            assertTrue(requestBody.get().contains("\"swift_number\":\"3010685_20240221073528_32822730A\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void invokeBuildsNestedRequestBodyWhenTargetPathStartsWithDollar() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/score", exchange -> {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int len;
            while ((len = exchange.getRequestBody().read(chunk)) >= 0) {
                buffer.write(chunk, 0, len);
            }
            requestBody.set(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            byte[] response = "{\"code\":\"00\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(7L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("NONE");

            RuleExternalApiConfig config = new RuleExternalApiConfig();
            config.setId(99L);
            config.setDatasourceId(7L);
            config.setRequestMethod("POST");
            config.setEndpointUrl("/score");
            config.setContentType("application/json");
            config.setRequestMapping("{\"$.params.mobile_no\":\"$.mobile_no\",\"$.params.idcard_no\":\"$.idcard_no\"}");
            config.setResponseCacheSeconds(0);
            config.setTimeoutMs(3000);
            config.setRetryCount(0);
            config.setRetryIntervalMs(0);
            config.setExceptionStrategy("FAIL_FAST");

            ExternalApiInvokeService service = new ExternalApiInvokeService();
            ReflectionTestUtils.setField(service, "apiConfigMapper",
                    mapperProxy(RuleExternalApiConfigMapper.class, config));
            ReflectionTestUtils.setField(service, "datasourceMapper",
                    mapperProxy(RuleExternalDatasourceMapper.class, datasource));
            ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mobile_no", "13800000000");
            params.put("idcard_no", "110101199001010011");

            service.invoke(99L, params);

            assertTrue(requestBody.get().contains("\"params\":{"));
            assertTrue(requestBody.get().contains("\"mobile_no\":\"13800000000\""));
            assertTrue(requestBody.get().contains("\"idcard_no\":\"110101199001010011\""));
            assertEquals(false, requestBody.get().contains("\"$.params.mobile_no\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void requestMappingTakesPrecedenceOverLegacyBodyTemplate() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/score", exchange -> {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int len;
            while ((len = exchange.getRequestBody().read(chunk)) >= 0) {
                buffer.write(chunk, 0, len);
            }
            requestBody.set(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(7L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("NONE");

            RuleExternalApiConfig config = new RuleExternalApiConfig();
            config.setId(99L);
            config.setDatasourceId(7L);
            config.setRequestMethod("POST");
            config.setEndpointUrl("/score");
            config.setContentType("application/json");
            config.setRequestMapping("{\"params\":{\"$.mobile_no\":\"$.mobile_no\"},\"clientAppName\":\"BNLP\"}");
            config.setBodyTemplate("{\"params\":{\"mobile_no\":\"legacy\"}}");
            config.setResponseCacheSeconds(0);
            config.setTimeoutMs(3000);
            config.setRetryCount(0);
            config.setRetryIntervalMs(0);
            config.setExceptionStrategy("FAIL_FAST");

            ExternalApiInvokeService service = new ExternalApiInvokeService();
            ReflectionTestUtils.setField(service, "apiConfigMapper",
                    mapperProxy(RuleExternalApiConfigMapper.class, config));
            ReflectionTestUtils.setField(service, "datasourceMapper",
                    mapperProxy(RuleExternalDatasourceMapper.class, datasource));
            ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mobile_no", "13800138000");

            service.invoke(99L, params);

            assertTrue(requestBody.get().contains("\"mobile_no\":\"13800138000\""));
            assertEquals(false, requestBody.get().contains("legacy"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void queryBillingSkipsLocalExceptionBeforeRequestIssued() {
        RuleExternalDatasource datasource = new RuleExternalDatasource();
        datasource.setId(7L);
        datasource.setProtocol("HTTP");
        datasource.setBaseUrl("bad-url");
        datasource.setAuthType("NONE");

        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setId(99L);
        config.setDatasourceId(7L);
        config.setRequestMethod("POST");
        config.setEndpointUrl("/score");
        config.setContentType("application/json");
        config.setBillingCondition("{\"mode\":\"QUERY\"}");
        config.setResponseCacheSeconds(0);
        config.setTimeoutMs(3000);
        config.setRetryCount(0);
        config.setRetryIntervalMs(0);
        config.setExceptionStrategy("RETURN_DEFAULT");
        config.setFallbackValue("{}");

        RecordingBillingService billingService = new RecordingBillingService();
        ExternalApiInvokeService service = new ExternalApiInvokeService();
        ReflectionTestUtils.setField(service, "apiConfigMapper",
                mapperProxy(RuleExternalApiConfigMapper.class, config));
        ReflectionTestUtils.setField(service, "datasourceMapper",
                mapperProxy(RuleExternalDatasourceMapper.class, datasource));
        ReflectionTestUtils.setField(service, "billingService", billingService);

        Map<String, Object> response = service.invoke(99L, new LinkedHashMap<>());

        assertEquals(0, billingService.recordCount.get());
        assertEquals("ERROR", response.get("sourceOutcome"));
        assertEquals(Boolean.TRUE, response.get("fallback"));
        assertEquals(Boolean.FALSE, response.get("cacheConfigured"));
        assertEquals("FALLBACK", response.get("dataOrigin"));
    }

    @Test
    public void hitBillingRecordsOnlyWhenConditionMatchesResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/score", exchange -> {
            byte[] response = "{\"hit\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(7L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("NONE");

            RuleExternalApiConfig config = new RuleExternalApiConfig();
            config.setId(99L);
            config.setDatasourceId(7L);
            config.setRequestMethod("POST");
            config.setEndpointUrl("/score");
            config.setContentType("application/json");
            config.setBillingCondition("{\"mode\":\"HIT\",\"path\":\"body.hit\",\"operator\":\"==\",\"value\":true}");
            config.setResponseCacheSeconds(0);
            config.setTimeoutMs(3000);
            config.setRetryCount(0);
            config.setRetryIntervalMs(0);
            config.setExceptionStrategy("FAIL_FAST");

            RecordingBillingService billingService = new RecordingBillingService();
            ExternalApiInvokeService service = new ExternalApiInvokeService();
            ReflectionTestUtils.setField(service, "apiConfigMapper",
                    mapperProxy(RuleExternalApiConfigMapper.class, config));
            ReflectionTestUtils.setField(service, "datasourceMapper",
                    mapperProxy(RuleExternalDatasourceMapper.class, datasource));
            ReflectionTestUtils.setField(service, "billingService", billingService);

            service.invoke(99L, new LinkedHashMap<>());

            assertEquals(1, billingService.recordCount.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void sensitiveRequestFieldsAreMaskedBeforeLogging() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "alice");
        body.put("password", "plain-password");
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("accessToken", "abcdef1234567890");
        body.put("auth", nested);
        ArrayList<Object> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("secretKey", "key-1234567890");
        items.add(item);
        body.put("items", items);

        Object masked = new ExternalApiInvokeService().maskSensitiveForLog(body);

        Map<?, ?> maskedMap = (Map<?, ?>) masked;
        assertEquals("alice", maskedMap.get("username"));
        assertEquals("plai******word", maskedMap.get("password"));
        assertEquals("abcd******7890", ((Map<?, ?>) maskedMap.get("auth")).get("accessToken"));
        assertEquals("key-******7890", ((Map<?, ?>) ((java.util.List<?>) maskedMap.get("items")).get(0)).get("secretKey"));
    }

    @Test
    public void tokenApiCanReadTokenFromResponseHeader() throws Exception {
        AtomicReference<String> authHeader = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/token", exchange -> {
            exchange.getResponseHeaders().add("Authorization", "Bearer header-token-123456");
            exchange.getResponseHeaders().add("X-Expires-In", "120");
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/score", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"data\":{\"score\":91}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(8L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("TOKEN_API");
            datasource.setAuthConfig("{\"tokenUrl\":\"/token\",\"method\":\"POST\",\"tokenPath\":\"headers.Authorization\",\"expiresInPath\":\"headers.X-Expires-In\"}");
            datasource.setTokenCacheSeconds(0);

            RuleExternalApiConfig config = new RuleExternalApiConfig();
            config.setId(100L);
            config.setDatasourceId(8L);
            config.setRequestMethod("POST");
            config.setEndpointUrl("/score");
            config.setContentType("application/json");
            config.setAuthMode("INHERIT");
            config.setResponseCacheSeconds(0);
            config.setTimeoutMs(3000);
            config.setRetryCount(0);
            config.setRetryIntervalMs(0);
            config.setExceptionStrategy("FAIL_FAST");

            ExternalApiInvokeService service = new ExternalApiInvokeService();
            ReflectionTestUtils.setField(service, "apiConfigMapper",
                    mapperProxy(RuleExternalApiConfigMapper.class, config));
            ReflectionTestUtils.setField(service, "datasourceMapper",
                    mapperProxy(RuleExternalDatasourceMapper.class, datasource));
            ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());

            service.invoke(100L, new LinkedHashMap<>());

            assertEquals("header-token-123456", authHeader.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void unauthorizedProviderResponseRefreshesTokenOnlyOncePerInvocation() throws Exception {
        AtomicInteger tokenCalls = new AtomicInteger();
        AtomicInteger providerCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/token", exchange -> {
            int sequence = tokenCalls.incrementAndGet();
            byte[] response = ("{\"token\":\"token-" + sequence + "\",\"expires_in\":120}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/score", exchange -> {
            providerCalls.incrementAndGet();
            byte[] response = "{\"message\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(81L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("TOKEN_API");
            datasource.setAuthConfig("{\"tokenUrl\":\"/token\",\"tokenPath\":\"body.token\"," +
                    "\"expiresInPath\":\"body.expires_in\"}");
            RuleExternalApiConfig config = basicApiConfig(810L, 81L, "/score");
            config.setAuthMode("INHERIT");
            config.setRetryCount(2);
            config.setTokenLogEnabled(1);
            ExternalApiInvokeService service = configuredService(config, datasource);
            RecordingRuntimeCallLogService runtimeLogs = new RecordingRuntimeCallLogService();
            ExternalCallProperties properties = new ExternalCallProperties();
            ApiHttpClientRegistry clientRegistry = new ApiHttpClientRegistry(properties);
            ReflectionTestUtils.setField(service, "runtimeCallLogService", runtimeLogs);
            ReflectionTestUtils.setField(service, "apiHttpClientRegistry", clientRegistry);

            boolean failed = false;
            try {
                service.invoke(810L, new LinkedHashMap<>());
            } catch (ExternalApiInvokeService.ApiInvokeException e) {
                failed = true;
            }

            assertTrue(failed);
            assertEquals(2, tokenCalls.get());
            assertEquals(2, providerCalls.get());
            assertEquals(1, clientRegistry.size());
            java.util.List<RuleRuntimeCallLog> tokenLogs = new ArrayList<>();
            java.util.List<RuleRuntimeCallLog> attemptLogs = new ArrayList<>();
            boolean invalidated = false;
            for (RuleRuntimeCallLog log : runtimeLogs.logs) {
                if ("TOKEN_FETCH".equals(log.getActionType()) || "TOKEN_REFRESH".equals(log.getActionType())) {
                    tokenLogs.add(log);
                }
                if ("TOKEN_INVALIDATE".equals(log.getActionType())) invalidated = true;
                if ("API_ATTEMPT".equals(log.getActionType())) attemptLogs.add(log);
            }
            assertEquals(2, tokenLogs.size());
            assertEquals("TOKEN_FETCH", tokenLogs.get(0).getActionType());
            assertEquals("TOKEN_REFRESH", tokenLogs.get(1).getActionType());
            assertFalse(tokenLogs.get(0).getResponseBody().contains("token-1"));
            assertFalse(tokenLogs.get(1).getResponseBody().contains("token-2"));
            assertTrue(invalidated);
            assertEquals(2, attemptLogs.size());
            assertEquals(Integer.valueOf(1), attemptLogs.get(0).getAttemptNo());
            assertEquals(Integer.valueOf(2), attemptLogs.get(1).getAttemptNo());
            assertEquals("FETCH", attemptLogs.get(0).getTokenCacheStatus());
            assertEquals("REFRESH", attemptLogs.get(1).getTokenCacheStatus());
            List<JSONObject> firstAttempt = steps(attemptLogs.get(0).getTraceSteps());
            assertEquals(List.of("REQUEST_INPUT", "API_REQUEST", "AUTHENTICATION", "EXTERNAL_REQUEST", "EXTERNAL_RESPONSE"), stepTypes(firstAttempt));
            assertEquals(Map.of("message", "unauthorized"), firstAttempt.get(4).get("output"));
            assertEquals("FAILED", firstAttempt.get(4).getString("status"));
            assertEquals(1, firstAttempt.get(4).getIntValue("attemptNo"));
            List<JSONObject> retried = steps(attemptLogs.get(1).getTraceSteps());
            assertEquals(List.of("REQUEST_INPUT", "API_REQUEST", "AUTHENTICATION", "EXTERNAL_REQUEST", "EXTERNAL_RESPONSE",
                    "API_REQUEST", "AUTHENTICATION", "EXTERNAL_REQUEST", "EXTERNAL_RESPONSE"), stepTypes(retried));
            assertEquals(Map.of("message", "unauthorized"), retried.get(8).get("output"));
            assertEquals(2, retried.get(8).getIntValue("attemptNo"));
            clientRegistry.close();
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void postDoesNotRefreshTokenOrRepeatProviderCallWithoutIdempotencyConfirmation() throws Exception {
        AtomicInteger tokenCalls = new AtomicInteger();
        AtomicInteger providerCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/token", exchange -> {
            tokenCalls.incrementAndGet();
            byte[] response = "{\"token\":\"token-1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/charge", exchange -> {
            providerCalls.incrementAndGet();
            byte[] response = "{\"message\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = httpDatasource(server);
            datasource.setAuthType("TOKEN_API");
            datasource.setAuthConfig("{\"tokenUrl\":\"/token\",\"tokenPath\":\"body.token\"}");
            RuleExternalApiConfig config = basicApiConfig(811L, 1L, "/charge");
            config.setRequestMethod("POST");
            config.setAuthMode("INHERIT");
            config.setRetryCount(2);

            try {
                configuredService(config, datasource).invoke(811L, Map.of("amount", 100));
                fail("应因供应商 401 失败");
            } catch (ExternalApiInvokeService.ApiInvokeException expected) {
                // 未确认供应商幂等性时，POST 不得刷新 Token 后再次提交。
            }

            assertEquals(1, tokenCalls.get());
            assertEquals(1, providerCalls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void tokenApiCanWriteRawTokenToCustomHeader() throws Exception {
        AtomicReference<String> tokenHeader = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/token", exchange -> {
            byte[] response = "{\"token_id\":\"ice-token-123\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/score", exchange -> {
            tokenHeader.set(exchange.getRequestHeaders().getFirst("token_id"));
            byte[] response = "{\"code\":\"00\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(9L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("TOKEN_API");
            datasource.setAuthConfig("{\"tokenUrl\":\"/token\",\"method\":\"POST\","
                    + "\"tokenPath\":\"body.token_id\",\"tokenHeaderName\":\"token_id\",\"tokenPrefix\":\"\"}");
            datasource.setTokenCacheSeconds(0);

            RuleExternalApiConfig config = new RuleExternalApiConfig();
            config.setId(101L);
            config.setDatasourceId(9L);
            config.setRequestMethod("POST");
            config.setEndpointUrl("/score");
            config.setContentType("application/json");
            config.setAuthMode("INHERIT");
            config.setResponseCacheSeconds(0);
            config.setTimeoutMs(3000);
            config.setRetryCount(0);
            config.setRetryIntervalMs(0);
            config.setExceptionStrategy("FAIL_FAST");

            ExternalApiInvokeService service = new ExternalApiInvokeService();
            ReflectionTestUtils.setField(service, "apiConfigMapper",
                    mapperProxy(RuleExternalApiConfigMapper.class, config));
            ReflectionTestUtils.setField(service, "datasourceMapper",
                    mapperProxy(RuleExternalDatasourceMapper.class, datasource));
            ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());

            service.invoke(101L, new LinkedHashMap<>());

            assertEquals("ice-token-123", tokenHeader.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void tokenResponseScriptCanUnwrapXmlBeforeTokenPathExtraction() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/xml-token", exchange -> {
            byte[] response = "<string>{\"access_token\":\"xml-token-123\"}</string>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/xml-score", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(114L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("TOKEN_API");
            datasource.setAuthConfig("{\"tokenUrl\":\"/xml-token\",\"method\":\"POST\","
                    + "\"tokenPath\":\"body.access_token\","
                    + "\"tokenResponseScript\":\"jsonParse(strSubstring(rawBody, 8, strLength(rawBody) - 9))\"}");
            datasource.setTokenCacheSeconds(0);

            RuleExternalApiConfig config = new RuleExternalApiConfig();
            config.setId(114L);
            config.setDatasourceId(114L);
            config.setRequestMethod("POST");
            config.setEndpointUrl("/xml-score");
            config.setContentType("application/json");
            config.setAuthMode("INHERIT");
            config.setResponseCacheSeconds(0);
            config.setTimeoutMs(3000);
            config.setRetryCount(0);
            config.setExceptionStrategy("FAIL_FAST");

            configuredService(config, datasource).invoke(114L, new LinkedHashMap<>());

            assertEquals("xml-token-123", authorization.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void tokenApiCanExposeTokenToScriptWithoutWritingAuthorizationHeader() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/script-token", exchange -> {
            byte[] response = "{\"access_token\":\"form-token-123\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/script-score", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[256];
            int length;
            while ((length = exchange.getRequestBody().read(chunk)) >= 0) buffer.write(chunk, 0, length);
            requestBody.set(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(115L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("TOKEN_API");
            datasource.setAuthConfig("{\"tokenUrl\":\"/script-token\",\"method\":\"POST\","
                    + "\"tokenPath\":\"body.access_token\",\"tokenPlacement\":\"SCRIPT_ONLY\"}");
            datasource.setTokenCacheSeconds(0);

            RuleExternalApiConfig config = new RuleExternalApiConfig();
            config.setId(115L);
            config.setDatasourceId(115L);
            config.setRequestMethod("POST");
            config.setEndpointUrl("/script-score");
            config.setContentType("application/x-www-form-urlencoded");
            config.setAuthMode("INHERIT");
            config.setRequestScript("apiPut(body, \"ACCESS_TOKEN\", token); body");
            config.setResponseCacheSeconds(0);
            config.setTimeoutMs(3000);
            config.setRetryCount(0);
            config.setExceptionStrategy("FAIL_FAST");

            configuredService(config, datasource).invoke(115L, new LinkedHashMap<>());

            assertEquals(null, authorization.get());
            assertEquals("ACCESS_TOKEN=form-token-123", requestBody.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void requestPreviewResolvesEndpointPlaceholdersFromScriptVariables() {
        RuleExternalDatasource datasource = new RuleExternalDatasource();
        datasource.setId(116L);
        datasource.setProtocol("HTTPS");
        datasource.setBaseUrl("https://vendor.example.com");
        datasource.setAuthType("NONE");

        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setId(116L);
        config.setDatasourceId(116L);
        config.setRequestMethod("POST");
        config.setEndpointUrl("/api/v1/score/${appId}");
        config.setContentType("application/json");
        config.setAuthMode("NONE");
        config.setAuthApiConfig("{\"scriptVariables\":[{\"name\":\"appId\",\"value\":\"APP001\",\"sensitive\":false}]}");

        Map<String, Object> preview = configuredService(config, datasource)
                .previewRequest(config, new LinkedHashMap<>(), null);

        assertEquals("https://vendor.example.com/api/v1/score/APP001", preview.get("url"));
        assertEquals(false, preview.get("networkCalled"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void requestAndResponseScriptsRunAroundHttpTransportAndMapping() throws Exception {
        AtomicReference<Map<String, Object>> receivedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/script-score", exchange -> {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[256];
            int length;
            while ((length = exchange.getRequestBody().read(chunk)) >= 0) buffer.write(chunk, 0, length);
            receivedBody.set(com.alibaba.fastjson.JSON.parseObject(
                    new String(buffer.toByteArray(), StandardCharsets.UTF_8), LinkedHashMap.class));
            byte[] response = "{\"encryptedScore\":\"720\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(110L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("NONE");

            RuleExternalApiConfig config = new RuleExternalApiConfig();
            config.setId(110L);
            config.setDatasourceId(110L);
            config.setRequestMethod("POST");
            config.setEndpointUrl("/script-score");
            config.setContentType("application/json");
            config.setAuthMode("NONE");
            config.setRequestMapping("{\"mobile\":\"$.mobile_no\"}");
            config.setAuthApiConfig("{\"scriptVariables\":[{\"name\":\"secret\",\"value\":\"S001\",\"sensitive\":true}]}");
            config.setRequestScript("apiPut(state, \"logRequestBody\", body); apiPut(state, \"transientKey\", \"EPHEMERAL\"); apiPut(body, \"sign\", apiMd5(mapGet(vars, \"secret\") + mapGet(body, \"mobile\"))); body");
            config.setResponseScript("apiPut(state, \"logResponseBody\", body); _result = newMap(); _result = mapPut(_result, \"score\", toNumberValue(mapGet(body, \"encryptedScore\"))); _result = mapPut(_result, \"shared\", mapGet(state, \"transientKey\")); _result");
            config.setPayloadCaptureConfig("{\"request\":{\"source\":\"PROCESSED\"},\"response\":{\"source\":\"PROCESSED\"}}");
            config.setResponseMapping("{\"score\":\"body.score\",\"shared\":\"body.shared\"}");
            config.setResponseCacheSeconds(0);
            config.setTimeoutMs(3000);
            config.setRetryCount(0);
            config.setRetryIntervalMs(0);
            config.setExceptionStrategy("FAIL_FAST");

            ExternalApiInvokeService service = configuredService(config, datasource);
            RecordingRuntimeCallLogService runtimeLogs = new RecordingRuntimeCallLogService();
            ReflectionTestUtils.setField(service, "runtimeCallLogService", runtimeLogs);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mobile_no", "13800138000");

            Map<String, Object> result = service.invoke(110L, params);

            assertEquals("caa2bb3d3bb8a610f0d76c6c3c0898dd", receivedBody.get().get("sign"));
            assertEquals(720, ((Number) ((Map<String, Object>) result.get("body")).get("score")).intValue());
            assertEquals("EPHEMERAL", ((Map<String, Object>) result.get("body")).get("shared"));
            RuleRuntimeCallLog summary = runtimeLogs.logs.stream()
                    .filter(item -> "API_INVOKE".equals(item.getActionType()))
                    .findFirst().orElseThrow();
            assertTrue(summary.getRawRequestBody().contains("\"mobile\":\"13800138000\""));
            assertTrue(summary.getOriginalRequestBody().contains("\"mobile\":\"13800138000\""));
            assertEquals("{\"encryptedScore\":\"720\"}", summary.getRawResponseBody());
            assertEquals("{\"encryptedScore\":\"720\"}", summary.getOriginalResponseBody());
            assertEquals(summary.getRawRequestBody(), result.get("rawRequestBody"));
            assertEquals(summary.getRawResponseBody(), result.get("rawResponseBody"));
            List<JSONObject> trace = steps(result.get("traceSteps"));
            assertEquals(List.of("REQUEST_INPUT", "API_REQUEST", "AUTHENTICATION", "EXTERNAL_REQUEST", "EXTERNAL_RESPONSE",
                    "RESPONSE_PROCESSING", "RESPONSE_MAPPING"), stepTypes(trace));
            assertEquals(Map.of("encryptedScore", "720"), trace.get(4).get("output"));
            assertEquals(Map.of("encryptedScore", "720"), trace.get(5).get("input"));
            assertEquals(720, trace.get(5).getJSONObject("output").getIntValue("score"));
            assertEquals(trace.get(5).get("output"), trace.get(6).get("input"));
            assertEquals(JSON.toJSONString(result.get("body")), JSON.toJSONString(trace.get(6).get("output")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void requestPreviewUsesPlaceholderTokenAndNeverCallsTokenEndpoint() throws Exception {
        AtomicInteger tokenCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/token", exchange -> {
            tokenCalls.incrementAndGet();
            byte[] response = "{\"token\":\"unexpected\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(111L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("TOKEN_API");
            datasource.setAuthConfig("{\"tokenUrl\":\"/token\",\"tokenPath\":\"body.token\"}");

            RuleExternalApiConfig config = new RuleExternalApiConfig();
            config.setId(111L);
            config.setDatasourceId(111L);
            config.setRequestMethod("POST");
            config.setEndpointUrl("/never-called");
            config.setContentType("application/json");
            config.setAuthMode("INHERIT");
            config.setRequestMapping("{\"mobile\":\"$.mobile_no\"}");
            config.setAuthApiConfig("{\"scriptVariables\":[{\"name\":\"secretKey\",\"value\":\"S001\",\"sensitive\":true}]}");
            config.setRequestScript("apiPut(body, \"signature\", apiMd5(mapGet(vars, \"secretKey\") + token)); body");

            ExternalApiInvokeService service = configuredService(config, datasource);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mobile_no", "13800138000");

            Map<String, Object> preview = service.previewRequest(config, params, "preview-token");

            assertEquals(0, tokenCalls.get());
            assertEquals(false, preview.get("networkCalled"));
            assertEquals("******", ((Map<String, Object>) preview.get("headers")).get("Authorization"));
            assertEquals("******", ((Map<String, Object>) preview.get("body")).get("signature"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void requestScriptFailureStopsBeforeHttpRequest() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/must-not-run", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(112L);
            datasource.setProtocol("HTTP");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            datasource.setAuthType("NONE");

            RuleExternalApiConfig config = new RuleExternalApiConfig();
            config.setId(112L);
            config.setDatasourceId(112L);
            config.setRequestMethod("POST");
            config.setEndpointUrl("/must-not-run");
            config.setContentType("application/json");
            config.setAuthMode("NONE");
            config.setRequestScript("apiTripleDesEncryptBase64(\"payload\", \"bad-key\")");
            config.setResponseCacheSeconds(0);
            config.setTimeoutMs(3000);
            config.setRetryCount(0);
            config.setExceptionStrategy("FAIL_FAST");

            try {
                configuredService(config, datasource).invoke(112L, new LinkedHashMap<>());
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("请求脚本执行失败"));
            }
            assertEquals(0, calls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void ruleEngineDatasourceRunsRequestAndResponseScriptsWithoutHttp() {
        RuleExternalDatasource datasource = new RuleExternalDatasource();
        datasource.setId(113L);
        datasource.setProtocol("RULE_ENGINE");
        datasource.setDatasourceCode("tianshu_rule_engine");
        datasource.setAuthType("NONE");

        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setId(113L);
        config.setDatasourceId(113L);
        config.setApiCode("rule_script_test");
        config.setRequestMethod("POST");
        config.setEndpointUrl("RULE_SCRIPT_TEST");
        config.setAuthMode("NONE");
        config.setRequestMapping("{\"ruleCode\":\"RULE_SCRIPT_TEST\",\"params\":{\"mobile_no\":\"$.mobile_no\"}}");
        config.setRequestScript("apiPut(body, \"signed\", \"YES\"); body");
        config.setResponseScript("_result = newMap(); _result = mapPut(_result, \"score\", mapGet(body, \"rawScore\")); _result");
        config.setResponseMapping("{\"score\":\"body.score\"}");
        config.setResponseCacheSeconds(0);
        config.setRetryCount(0);
        config.setExceptionStrategy("FAIL_FAST");

        RulePublished published = new RulePublished();
        published.setId(1L);
        published.setRuleCode("RULE_SCRIPT_TEST");
        published.setStatus(1);
        AtomicReference<Map<String, Object>> ruleInput = new AtomicReference<>();

        ExternalApiInvokeService service = configuredService(config, datasource);
        ReflectionTestUtils.setField(service, "publishedMapper",
                selectOneMapperProxy(RulePublishedMapper.class, published));
        ReflectionTestUtils.setField(service, "ruleExecuteService", new RuleExecuteService() {
            @Override
            public RuleResult executePublished(RulePublished ignored, Map<String, Object> params,
                                               Long projectId, String clientAppName) {
                ruleInput.set(params);
                RuleResult result = new RuleResult();
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("rawScore", 680);
                result.setResult(body);
                result.setSuccess(true);
                return result;
            }
        });

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mobile_no", "13800138000");
        Map<String, Object> response = service.invoke(113L, params);

        assertEquals("YES", ruleInput.get().get("signed"));
        assertEquals("13800138000", ruleInput.get().get("mobile_no"));
        assertEquals(680, ((Number) ((Map<String, Object>) response.get("body")).get("score")).intValue());

        config.setExceptionCondition("{\"path\":\"body.score\",\"operator\":\"type_changed\",\"value\":\"STRING\"}");
        config.setExceptionStrategy("RETURN_DEFAULT");
        config.setFallbackValue("{\"available\":false}");
        Map<String, Object> fallback = service.invoke(113L, params);
        assertEquals(true, fallback.get("fallback"));
        assertEquals(false, ((Map<String, Object>) fallback.get("body")).get("available"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void tokenApiRejectsBlankCustomHeaderName() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("tokenHeaderName", " ");
        config.put("tokenPrefix", "");

        ReflectionTestUtils.invokeMethod(new ExternalApiInvokeService(), "applyTokenHeader",
                new HttpHeaders(), config, "token-value");
    }

    @Test
    public void tokenApiDefaultsToRawAuthorizationAndPreservesPrefixSpaces() {
        ExternalApiInvokeService service = new ExternalApiInvokeService();

        HttpHeaders defaultHeaders = new HttpHeaders();
        ReflectionTestUtils.invokeMethod(service, "applyTokenHeader", defaultHeaders,
                new LinkedHashMap<>(), "token-value");
        assertEquals("token-value", defaultHeaders.getFirst(HttpHeaders.AUTHORIZATION));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("tokenHeaderName", "X-Custom-Token");
        config.put("tokenPrefix", "Custom  ");
        HttpHeaders customHeaders = new HttpHeaders();
        ReflectionTestUtils.invokeMethod(service, "applyTokenHeader", customHeaders,
                config, "token-value");
        assertEquals("Custom  token-value", customHeaders.getFirst("X-Custom-Token"));
    }

    @SuppressWarnings("unchecked")
    private <T> T mapperProxy(Class<T> type, Object selectByIdResult) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) {
                return selectByIdResult;
            }
            if ("toString".equals(method.getName())) {
                return type.getSimpleName() + "Proxy";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T selectOneMapperProxy(Class<T> type, Object selectOneResult) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if ("selectOne".equals(method.getName())) return selectOneResult;
            if ("toString".equals(method.getName())) return type.getSimpleName() + "Proxy";
            if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
            if ("equals".equals(method.getName())) return proxy == args[0];
            return null;
        });
    }

    private ExternalApiInvokeService configuredService(RuleExternalApiConfig config,
                                                       RuleExternalDatasource datasource) {
        ExternalApiInvokeService service = new ExternalApiInvokeService();
        ReflectionTestUtils.setField(service, "apiConfigMapper",
                mapperProxy(RuleExternalApiConfigMapper.class, config));
        ReflectionTestUtils.setField(service, "datasourceMapper",
                mapperProxy(RuleExternalDatasourceMapper.class, datasource));
        ReflectionTestUtils.setField(service, "billingService", new RecordingBillingService());
        return service;
    }

    private static class RecordingBillingService extends RuleBillingService {
        private final AtomicInteger recordCount = new AtomicInteger();

        @Override
        public boolean recordApiExecution(RuleExternalApiConfig apiConfig, RuleExternalDatasource datasource,
                                       boolean success, Long costTimeMs, String errorMessage) {
            recordCount.incrementAndGet();
            return true;
        }
    }

    private static class RecordingRuntimeCallLogService extends RuleRuntimeCallLogService {
        private final java.util.List<RuleRuntimeCallLog> logs = new ArrayList<>();

        @Override
        public void safeSave(RuleRuntimeCallLog log) {
            logs.add(log);
        }
    }
}
