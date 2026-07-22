package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ExternalApiInvokeServiceTest {

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
            config.setRetryCount(3);
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
        MultiValueMap<String, ?> form = (MultiValueMap<String, ?>) requestBody;
        assertEquals("merchant-1", form.getFirst("member_id"));
        assertEquals("json", form.getFirst("data_type"));
        assertEquals(null, form.getFirst("empty"));
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
            config.setCacheKeyConfig("{\"components\":[{\"path\":\"request_id\"}]}");
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

            service.invoke(101L, params);
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

            assertEquals("Bearer header-token-123456", authHeader.get());
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
            clientRegistry.close();
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

            assertEquals("Bearer xml-token-123", authorization.get());
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
            config.setRequestScript("apiPut(state, \"transientKey\", \"EPHEMERAL\"); apiPut(body, \"sign\", apiMd5(mapGet(vars, \"secret\") + mapGet(body, \"mobile\"))); body");
            config.setResponseScript("_result = newMap(); _result = mapPut(_result, \"score\", toNumberValue(mapGet(body, \"encryptedScore\"))); _result = mapPut(_result, \"shared\", mapGet(state, \"transientKey\")); _result");
            config.setResponseMapping("{\"score\":\"body.score\",\"shared\":\"body.shared\"}");
            config.setResponseCacheSeconds(0);
            config.setTimeoutMs(3000);
            config.setRetryCount(0);
            config.setRetryIntervalMs(0);
            config.setExceptionStrategy("FAIL_FAST");

            ExternalApiInvokeService service = configuredService(config, datasource);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mobile_no", "13800138000");

            Map<String, Object> result = service.invoke(110L, params);

            assertEquals("caa2bb3d3bb8a610f0d76c6c3c0898dd", receivedBody.get().get("sign"));
            assertEquals(720, ((Number) ((Map<String, Object>) result.get("body")).get("score")).intValue());
            assertEquals("EPHEMERAL", ((Map<String, Object>) result.get("body")).get("shared"));
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
    }

    @Test(expected = IllegalArgumentException.class)
    public void tokenApiRejectsBlankCustomHeaderName() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("tokenHeaderName", " ");
        config.put("tokenPrefix", "");

        ReflectionTestUtils.invokeMethod(new ExternalApiInvokeService(), "applyTokenHeader",
                new HttpHeaders(), config, "token-value");
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
        public void recordApiExecution(RuleExternalApiConfig apiConfig, RuleExternalDatasource datasource,
                                       boolean success, Long costTimeMs, String errorMessage) {
            recordCount.incrementAndGet();
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
