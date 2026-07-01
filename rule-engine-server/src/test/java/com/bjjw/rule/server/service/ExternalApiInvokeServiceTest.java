package com.bjjw.rule.server.service;

import com.bjjw.rule.model.entity.RuleExternalApiConfig;
import com.bjjw.rule.model.entity.RuleExternalDatasource;
import com.bjjw.rule.server.mapper.RuleExternalApiConfigMapper;
import com.bjjw.rule.server.mapper.RuleExternalDatasourceMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
        @Override
        public void recordApiExecution(RuleExternalApiConfig apiConfig, RuleExternalDatasource datasource,
                                       boolean success, Long costTimeMs, String errorMessage) {
        }
    }
}
