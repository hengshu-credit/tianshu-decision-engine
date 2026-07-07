package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ExternalApiInvokeServiceTest {

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
    public void tokenContentTypeDefaultsToJson() {
        assertEquals(MediaType.APPLICATION_JSON,
                new ExternalApiInvokeService().resolveTokenContentType(new LinkedHashMap<>()));
    }

    @Test
    public void responseCacheKeyUsesApiIdAndParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("request_id", "r1");

        assertEquals("7:{\"request_id\":\"r1\"}",
                new ExternalApiInvokeService().buildResponseCacheKey(7L, params));
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
            Map<String, Object> first = service.invoke(99L, params);
            Map<String, Object> second = service.invoke(99L, params);

            assertEquals(1, callCount.get());
            assertEquals(Boolean.FALSE, first.get("cached"));
            assertEquals(Boolean.TRUE, second.get("cached"));
            assertEquals(0L, second.get("costTimeMs"));
            assertEquals(88, ((Map<?, ?>) second.get("body")).get("data") instanceof Map
                    ? ((Map<?, ?>) ((Map<?, ?>) second.get("body")).get("data")).get("score")
                    : null);
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

        service.invoke(99L, new LinkedHashMap<>());

        assertEquals(0, billingService.recordCount.get());
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

    private static class RecordingBillingService extends RuleBillingService {
        private final AtomicInteger recordCount = new AtomicInteger();

        @Override
        public void recordApiExecution(RuleExternalApiConfig apiConfig, RuleExternalDatasource datasource,
                                       boolean success, Long costTimeMs, String errorMessage) {
            recordCount.incrementAndGet();
        }
    }
}
