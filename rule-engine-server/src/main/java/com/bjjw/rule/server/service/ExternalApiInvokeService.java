package com.bjjw.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bjjw.rule.model.entity.RuleExternalApiConfig;
import com.bjjw.rule.model.entity.RuleExternalDatasource;
import com.bjjw.rule.server.mapper.RuleExternalApiConfigMapper;
import com.bjjw.rule.server.mapper.RuleExternalDatasourceMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ExternalApiInvokeService {

    @Resource
    private RuleExternalApiConfigMapper apiConfigMapper;

    @Resource
    private RuleExternalDatasourceMapper datasourceMapper;

    @Resource
    private RuleBillingService billingService;

    private final ConcurrentMap<String, TokenCache> tokenCaches = new ConcurrentHashMap<>();

    public Map<String, Object> invoke(Long apiConfigId, Map<String, Object> params) {
        RuleExternalApiConfig apiConfig = apiConfigMapper.selectById(apiConfigId);
        if (apiConfig == null) {
            throw new IllegalArgumentException("API接口配置不存在");
        }
        RuleExternalDatasource datasource = datasourceMapper.selectById(apiConfig.getDatasourceId());
        if (datasource == null) {
            throw new IllegalArgumentException("外数数据源不存在");
        }
        int retryCount = apiConfig.getRetryCount() == null ? 0 : Math.max(apiConfig.getRetryCount(), 0);
        int retryIntervalMs = apiConfig.getRetryIntervalMs() == null ? 0 : Math.max(apiConfig.getRetryIntervalMs(), 0);
        Exception lastError = null;
        long start = System.currentTimeMillis();
        for (int i = 0; i <= retryCount; i++) {
            try {
                Map<String, Object> result = doInvoke(apiConfig, datasource, params == null ? new HashMap<>() : params);
                long cost = System.currentTimeMillis() - start;
                result.put("costTimeMs", cost);
                billingService.recordApiExecution(apiConfig, datasource, true, cost, null);
                return result;
            } catch (Exception e) {
                lastError = e;
                if (i < retryCount && retryIntervalMs > 0) {
                    try {
                        Thread.sleep(retryIntervalMs);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        long cost = System.currentTimeMillis() - start;
        String message = lastError == null ? "调用失败" : lastError.getMessage();
        billingService.recordApiExecution(apiConfig, datasource, false, cost, message);
        if ("RETURN_DEFAULT".equals(apiConfig.getExceptionStrategy())) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("success", false);
            fallback.put("fallback", true);
            fallback.put("body", parseJsonOrRaw(apiConfig.getFallbackValue()));
            fallback.put("errorMessage", message);
            fallback.put("costTimeMs", cost);
            return fallback;
        }
        throw new IllegalStateException(message, lastError);
    }

    private Map<String, Object> doInvoke(RuleExternalApiConfig apiConfig, RuleExternalDatasource datasource,
                                         Map<String, Object> params) throws Exception {
        String url = buildUrl(datasource.getBaseUrl(), apiConfig.getEndpointUrl());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(apiConfig.getContentType()));
        applyJsonHeaders(headers, apiConfig.getHeaderConfig(), params);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        applyQueryParams(builder, apiConfig.getQueryConfig(), params);
        applyAuth(builder, headers, datasource, apiConfig, params);

        Object body = buildBody(apiConfig, params);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeout = apiConfig.getTimeoutMs() == null ? 3000 : apiConfig.getTimeoutMs();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        HttpMethod method = HttpMethod.resolve(apiConfig.getRequestMethod());
        if (method == null) {
            method = HttpMethod.POST;
        }
        ResponseEntity<String> response = restTemplate.exchange(
                builder.build(true).toUri(),
                method,
                new HttpEntity<>(body, headers),
                String.class);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", response.getStatusCode().is2xxSuccessful());
        result.put("httpStatus", response.getStatusCodeValue());
        result.put("body", parseJsonOrRaw(response.getBody()));
        return result;
    }

    private void applyJsonHeaders(HttpHeaders headers, String headerConfig, Map<String, Object> params) {
        Map<String, Object> map = parseJsonMap(headerConfig);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            headers.set(entry.getKey(), String.valueOf(resolveValue(entry.getValue(), params)));
        }
    }

    private void applyQueryParams(UriComponentsBuilder builder, String queryConfig, Map<String, Object> params) {
        Map<String, Object> map = parseJsonMap(queryConfig);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            builder.queryParam(entry.getKey(), resolveValue(entry.getValue(), params));
        }
    }

    private void applyAuth(UriComponentsBuilder builder, HttpHeaders headers, RuleExternalDatasource datasource,
                           RuleExternalApiConfig apiConfig, Map<String, Object> params) {
        String authMode = apiConfig.getAuthMode();
        String authConfig = apiConfig.getAuthApiConfig();
        if (authMode == null || authMode.trim().isEmpty() || "INHERIT".equals(authMode)) {
            authMode = datasource.getAuthType();
            authConfig = datasource.getAuthConfig();
        }
        if (authMode == null || "NONE".equals(authMode)) {
            return;
        }
        Map<String, Object> config = parseJsonMap(authConfig);
        if ("BASIC".equals(authMode)) {
            String raw = stringValue(resolveValue(config.get("username"), params)) + ":" + stringValue(resolveValue(config.get("password"), params));
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        } else if ("BEARER".equals(authMode)) {
            headers.setBearerAuth(stringValue(resolveValue(config.get("token"), params)));
        } else if ("API_KEY".equals(authMode)) {
            String name = stringValue(config.get("name"));
            String value = stringValue(resolveValue(config.get("value"), params));
            String location = stringValue(config.get("location"));
            if ("QUERY".equalsIgnoreCase(location)) {
                builder.queryParam(name, value);
            } else {
                headers.set(name, value);
            }
        } else if ("TOKEN_API".equals(authMode) || "OAUTH2".equals(authMode)) {
            int cacheSeconds = apiConfig.getTokenCacheSeconds() != null && apiConfig.getTokenCacheSeconds() > 0
                    ? apiConfig.getTokenCacheSeconds()
                    : (datasource.getTokenCacheSeconds() == null ? 0 : datasource.getTokenCacheSeconds());
            String cacheKey = apiConfig.getId() + ":" + authMode + ":" + stringValue(config.get("tokenUrl"));
            String token = requestToken(config, params, cacheKey, cacheSeconds);
            if (token != null && !token.isEmpty()) {
                headers.setBearerAuth(token);
            }
        }
    }

    private String requestToken(Map<String, Object> config, Map<String, Object> params, String cacheKey, int cacheSeconds) {
        TokenCache cached = tokenCaches.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt > now && cached.token != null && !cached.token.isEmpty()) {
            return cached.token;
        }
        String tokenUrl = stringValue(config.get("tokenUrl"));
        if (tokenUrl.isEmpty()) {
            return "";
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> tokenHeaders = parseNestedMap(config.get("headers"));
        for (Map.Entry<String, Object> entry : tokenHeaders.entrySet()) {
            headers.set(entry.getKey(), stringValue(resolveValue(entry.getValue(), params)));
        }
        Object body = resolveValue(config.get("body"), params);
        String methodText = stringValue(config.get("method"));
        HttpMethod method = methodText.isEmpty() ? HttpMethod.POST : HttpMethod.resolve(methodText);
        if (method == null) {
            method = HttpMethod.POST;
        }
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(tokenUrl, method, new HttpEntity<>(body, headers), String.class);
        Object parsed = parseJsonOrRaw(response.getBody());
        Object token = readPath(parsed, stringValue(config.get("tokenPath")));
        String tokenText = token == null ? "" : String.valueOf(token);
        int ttlSeconds = resolveTokenTtlSeconds(config, parsed, cacheSeconds);
        if (!tokenText.isEmpty() && ttlSeconds > 0) {
            tokenCaches.put(cacheKey, new TokenCache(tokenText, now + ttlSeconds * 1000L));
        }
        return tokenText;
    }

    private int resolveTokenTtlSeconds(Map<String, Object> config, Object tokenResponse, int defaultCacheSeconds) {
        Object expiresIn = readPath(tokenResponse, stringValue(config.get("expiresInPath")));
        if (expiresIn instanceof Number) {
            return ((Number) expiresIn).intValue();
        }
        if (expiresIn != null) {
            try {
                return Integer.parseInt(String.valueOf(expiresIn));
            } catch (NumberFormatException ignored) {
                return defaultCacheSeconds;
            }
        }
        return defaultCacheSeconds;
    }

    private Object buildBody(RuleExternalApiConfig apiConfig, Map<String, Object> params) {
        if ("GET".equals(apiConfig.getRequestMethod()) || "DELETE".equals(apiConfig.getRequestMethod())) {
            return null;
        }
        if (apiConfig.getBodyTemplate() != null && !apiConfig.getBodyTemplate().trim().isEmpty()) {
            String resolved = resolveTemplate(apiConfig.getBodyTemplate(), params);
            return parseJsonOrRaw(resolved);
        }
        Map<String, Object> mapping = parseJsonMap(apiConfig.getRequestMapping());
        if (!mapping.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : mapping.entrySet()) {
                body.put(entry.getKey(), resolveValue(entry.getValue(), params));
            }
            return body;
        }
        return params;
    }

    private String buildUrl(String baseUrl, String endpointUrl) {
        if (endpointUrl != null && (endpointUrl.startsWith("http://") || endpointUrl.startsWith("https://"))) {
            return endpointUrl;
        }
        String base = baseUrl == null ? "" : baseUrl;
        String endpoint = endpointUrl == null ? "" : endpointUrl;
        if (base.endsWith("/") && endpoint.startsWith("/")) {
            return base + endpoint.substring(1);
        }
        if (!base.endsWith("/") && !endpoint.startsWith("/")) {
            return base + "/" + endpoint;
        }
        return base + endpoint;
    }

    private Map<String, Object> parseJsonMap(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object parsed = JSON.parse(text);
        return parseNestedMap(parsed);
    }

    private Map<String, Object> parseNestedMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private Object parseJsonOrRaw(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return JSON.parse(text);
        } catch (Exception e) {
            return text;
        }
    }

    private Object resolveValue(Object value, Map<String, Object> params) {
        if (value instanceof String) {
            String text = (String) value;
            if (text.startsWith("$.")) {
                return readPath(params, text.substring(2));
            }
            if (text.contains("${")) {
                return resolveTemplate(text, params);
            }
        }
        if (value instanceof Map) {
            Map<String, Object> map = parseNestedMap(value);
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                resolved.put(entry.getKey(), resolveValue(entry.getValue(), params));
            }
            return resolved;
        }
        return value;
    }

    private String resolveTemplate(String template, Map<String, Object> params) {
        String result = template;
        int start = result.indexOf("${");
        while (start >= 0) {
            int end = result.indexOf("}", start);
            if (end < 0) {
                break;
            }
            String path = result.substring(start + 2, end);
            Object value = readPath(params, path);
            result = result.substring(0, start) + (value == null ? "" : String.valueOf(value)) + result.substring(end + 1);
            start = result.indexOf("${", start + 1);
        }
        return result;
    }

    private Object readPath(Object root, String path) {
        if (root == null || path == null || path.trim().isEmpty()) {
            return root;
        }
        Object current = root;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            if (current instanceof JSONObject) {
                current = ((JSONObject) current).get(part);
            } else if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static class TokenCache {
        private final String token;
        private final long expiresAt;

        private TokenCache(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }
}
