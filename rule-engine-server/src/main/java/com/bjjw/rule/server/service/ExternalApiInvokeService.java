package com.bjjw.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjjw.rule.model.dto.RuleResult;
import com.bjjw.rule.model.entity.RuleRuntimeCallLog;
import com.bjjw.rule.model.entity.RuleExternalApiConfig;
import com.bjjw.rule.model.entity.RuleExternalDatasource;
import com.bjjw.rule.model.entity.RulePublished;
import com.bjjw.rule.server.mapper.RuleExternalApiConfigMapper;
import com.bjjw.rule.server.mapper.RuleExternalDatasourceMapper;
import com.bjjw.rule.server.mapper.RulePublishedMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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

    @Resource
    private RulePublishedMapper publishedMapper;

    @Resource
    @Lazy
    private RuleExecuteService ruleExecuteService;

    @Resource
    private RuleProjectService projectService;

    @Resource
    private RuleRuntimeCallLogService runtimeCallLogService;

    private final ConcurrentMap<String, TokenCache> tokenCaches = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ApiResponseCache> responseCaches = new ConcurrentHashMap<>();

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
        int responseCacheSeconds = apiConfig.getResponseCacheSeconds() == null ? 0 : Math.max(apiConfig.getResponseCacheSeconds(), 0);
        String responseCacheKey = buildResponseCacheKey(apiConfig.getId(), params == null ? new HashMap<>() : params);
        ApiResponseCache cachedResponse = responseCacheSeconds > 0 ? responseCaches.get(responseCacheKey) : null;
        long start = System.currentTimeMillis();
        InvokeTrace trace = new InvokeTrace();
        trace.requestParams = params == null ? new HashMap<>() : params;
        if (cachedResponse != null && cachedResponse.expiresAt > start) {
            Map<String, Object> cached = copyCachedResponse(cachedResponse.response, true, false, 0);
            logDatasourceCall(apiConfig, datasource, trace, true, cached, null, 0);
            return cached;
        }
        Exception lastError = null;
        for (int i = 0; i <= retryCount; i++) {
            try {
                Map<String, Object> result = doInvoke(apiConfig, datasource, params == null ? new HashMap<>() : params, trace);
                long cost = System.currentTimeMillis() - start;
                result.put("costTimeMs", cost);
                result.put("cached", false);
                if (responseCacheSeconds > 0) {
                    responseCaches.put(responseCacheKey, new ApiResponseCache(copyResponse(result),
                            System.currentTimeMillis() + responseCacheSeconds * 1000L));
                }
                billingService.recordApiExecution(apiConfig, datasource, true, cost, null);
                logDatasourceCall(apiConfig, datasource, trace, true, result, null, cost);
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
        if ("USE_CACHE".equals(apiConfig.getExceptionStrategy()) && cachedResponse != null) {
            Map<String, Object> cached = copyCachedResponse(cachedResponse.response, true, true, cost);
            logDatasourceCall(apiConfig, datasource, trace, true, cached, message, cost);
            return cached;
        }
        billingService.recordApiExecution(apiConfig, datasource, false, cost, message);
        if ("RETURN_DEFAULT".equals(apiConfig.getExceptionStrategy())) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("success", false);
            fallback.put("fallback", true);
            fallback.put("body", parseJsonOrRaw(apiConfig.getFallbackValue()));
            fallback.put("errorMessage", message);
            fallback.put("costTimeMs", cost);
            logDatasourceCall(apiConfig, datasource, trace, false, fallback, message, cost);
            return fallback;
        }
        logDatasourceCall(apiConfig, datasource, trace, false, null, message, cost);
        throw new IllegalStateException(message, lastError);
    }

    public Map<String, Object> testDatasourceAuth(Long datasourceId, Map<String, Object> params) {
        RuleExternalDatasource datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            throw new IllegalArgumentException("外数数据源不存在");
        }
        String authType = datasource.getAuthType();
        Map<String, Object> config = parseJsonMap(datasource.getAuthConfig());
        long start = System.currentTimeMillis();
        InvokeTrace trace = new InvokeTrace();
        trace.requestParams = params == null ? new HashMap<>() : params;
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("authType", authType);
            result.put("datasourceCode", datasource.getDatasourceCode());
            if (!"TOKEN_API".equals(authType) && !"OAUTH2".equals(authType)) {
                result.put("success", true);
                result.put("message", "当前鉴权方式不需要请求 Token 接口");
                result.put("configPreview", previewStaticAuthConfig(authType, config, params == null ? new HashMap<>() : params));
                logDatasourceAuthTest(datasource, trace, true, result, null, System.currentTimeMillis() - start);
                return result;
            }
            Map<String, Object> tokenResult = requestTokenForTest(datasource, config,
                    params == null ? new HashMap<>() : params, trace);
            tokenResult.put("success", true);
            logDatasourceAuthTest(datasource, trace, true, tokenResult, null, System.currentTimeMillis() - start);
            return tokenResult;
        } catch (Exception e) {
            Map<String, Object> failed = new LinkedHashMap<>();
            failed.put("success", false);
            failed.put("errorMessage", e.getMessage());
            logDatasourceAuthTest(datasource, trace, false, failed, e.getMessage(), System.currentTimeMillis() - start);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    String buildResponseCacheKey(Long apiConfigId, Map<String, Object> params) {
        return apiConfigId + ":" + JSON.toJSONString(params == null ? new HashMap<>() : params);
    }

    Map<String, Object> copyCachedResponse(Map<String, Object> cached, boolean hit, boolean stale, long costTimeMs) {
        Map<String, Object> response = copyResponse(cached);
        response.put("cached", hit);
        response.put("cacheStale", stale);
        response.put("costTimeMs", costTimeMs);
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copyResponse(Map<String, Object> response) {
        if (response == null) {
            return new LinkedHashMap<>();
        }
        return JSON.parseObject(JSON.toJSONString(response), LinkedHashMap.class);
    }

    private Map<String, Object> doInvoke(RuleExternalApiConfig apiConfig, RuleExternalDatasource datasource,
                                         Map<String, Object> params, InvokeTrace trace) throws Exception {
        if ("RULE_ENGINE".equalsIgnoreCase(datasource.getProtocol())) {
            return doInvokeRuleEngine(apiConfig, datasource, params, trace);
        }
        String url = buildUrl(datasource.getBaseUrl(), apiConfig.getEndpointUrl());
        HttpHeaders headers = new HttpHeaders();
        if (hasText(apiConfig.getContentType())) {
            headers.setContentType(MediaType.parseMediaType(apiConfig.getContentType()));
        }
        applyJsonHeaders(headers, apiConfig.getHeaderConfig(), params);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        applyQueryParams(builder, apiConfig.getQueryConfig(), params);
        applyAuth(builder, headers, datasource, apiConfig, params);

        Object requestBody = buildBody(apiConfig, params);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeout = apiConfig.getTimeoutMs() == null ? 3000 : apiConfig.getTimeoutMs();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        HttpMethod method = HttpMethod.resolve(apiConfig.getRequestMethod());
        if (method == null) {
            method = HttpMethod.POST;
        }
        trace.requestMethod = method.name();
        trace.requestUrl = builder.build(true).toUriString();
        trace.requestHeaders = headersToLog(headers);
        trace.requestBody = requestBody;
        ResponseEntity<String> response = restTemplate.exchange(
                builder.build(true).toUri(),
                method,
                new HttpEntity<>(requestBody, headers),
                String.class);

        Map<String, Object> result = new LinkedHashMap<>();
        Object responseBody = parseJsonOrRaw(response.getBody());
        trace.responseStatus = response.getStatusCodeValue();
        trace.responseBody = responseBody;
        result.put("success", response.getStatusCode().is2xxSuccessful());
        result.put("httpStatus", response.getStatusCodeValue());
        result.put("body", responseBody);
        return applyResponseMapping(apiConfig, result);
    }

    private Map<String, Object> doInvokeRuleEngine(RuleExternalApiConfig apiConfig, RuleExternalDatasource datasource,
                                                  Map<String, Object> params, InvokeTrace trace) {
        Map<String, Object> config = parseJsonMap(apiConfig.getRequestMapping());
        String ruleCode = firstText(config.get("ruleCode"), apiConfig.getEndpointUrl(), apiConfig.getApiCode());
        if (ruleCode.startsWith("/")) {
            ruleCode = ruleCode.substring(ruleCode.lastIndexOf('/') + 1);
        }
        LambdaQueryWrapper<RulePublished> wrapper = new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getRuleCode, ruleCode)
                .eq(RulePublished::getStatus, 1);
        String projectCode = resolveProjectCode(datasource.getProjectId());
        if (projectCode != null && !projectCode.trim().isEmpty()) {
            wrapper.eq(RulePublished::getProjectCode, projectCode);
        }
        RulePublished published = publishedMapper.selectOne(wrapper);
        if (published == null) {
            throw new IllegalArgumentException("内部规则不存在或未发布: " + ruleCode);
        }
        Object mapped = config.get("params");
        Map<String, Object> ruleParams = mapped == null ? params : parseNestedMap(resolveValue(mapped, params));
        trace.requestMethod = "POST";
        trace.requestUrl = "rule-engine://local/" + ruleCode;
        trace.requestBody = ruleParams;
        RuleResult result = ruleExecuteService.executePublished(published, ruleParams, datasource.getProjectId(), "RULE_ENGINE_DATASOURCE");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.isSuccess());
        response.put("body", result.getResult());
        response.put("errorMessage", result.getErrorMessage());
        response.put("executeTimeMs", result.getExecuteTimeMs());
        response.put("traces", result.getTraces());
        trace.responseStatus = result.isSuccess() ? 200 : 500;
        trace.responseBody = response;
        return applyResponseMapping(apiConfig, response);
    }

    private String resolveProjectCode(Long projectId) {
        if (projectId == null) {
            return null;
        }
        com.bjjw.rule.model.entity.RuleProject project = projectService.getById(projectId);
        return project == null ? null : project.getProjectCode();
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
            String tokenUrl = buildTokenUrl(datasource, stringValue(config.get("tokenUrl")));
            String cacheKey = apiConfig.getId() + ":" + authMode + ":" + tokenUrl;
            String token = requestToken(config, params, tokenUrl, cacheKey, cacheSeconds);
            if (token != null && !token.isEmpty()) {
                headers.setBearerAuth(token);
            }
        }
    }

    private String requestToken(Map<String, Object> config, Map<String, Object> params, String tokenUrl,
                                String cacheKey, int cacheSeconds) {
        TokenCache cached = tokenCaches.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt > now && cached.token != null && !cached.token.isEmpty()) {
            return cached.token;
        }
        if (tokenUrl.isEmpty()) {
            return "";
        }
        HttpHeaders headers = new HttpHeaders();
        MediaType contentType = resolveTokenContentType(config);
        headers.setContentType(contentType);
        Map<String, Object> tokenHeaders = parseNestedMap(config.get("headers"));
        for (Map.Entry<String, Object> entry : tokenHeaders.entrySet()) {
            headers.set(entry.getKey(), stringValue(resolveValue(entry.getValue(), params)));
        }
        Object body = buildTokenRequestBody(config.get("body"), params, contentType);
        String methodText = stringValue(config.get("method"));
        HttpMethod method = methodText.isEmpty() ? HttpMethod.POST : HttpMethod.resolve(methodText);
        if (method == null) {
            method = HttpMethod.POST;
        }
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(tokenUrl, method, new HttpEntity<>(body, headers), String.class);
        Object parsed = parseJsonOrRaw(response.getBody());
        Object token = readToken(response, parsed, stringValue(config.get("tokenPath")));
        String tokenText = token == null ? "" : stripBearerPrefix(String.valueOf(token));
        int ttlSeconds = resolveTokenTtlSeconds(config, response, parsed, cacheSeconds);
        if (!tokenText.isEmpty() && ttlSeconds > 0) {
            tokenCaches.put(cacheKey, new TokenCache(tokenText, now + ttlSeconds * 1000L));
        }
        return tokenText;
    }

    private Map<String, Object> requestTokenForTest(RuleExternalDatasource datasource, Map<String, Object> config,
                                                    Map<String, Object> params, InvokeTrace trace) {
        String tokenUrl = buildTokenUrl(datasource, stringValue(config.get("tokenUrl")));
        if (!hasText(tokenUrl)) {
            throw new IllegalArgumentException("Token接口地址 tokenUrl 不能为空");
        }
        HttpHeaders headers = new HttpHeaders();
        MediaType contentType = resolveTokenContentType(config);
        headers.setContentType(contentType);
        Map<String, Object> tokenHeaders = parseNestedMap(config.get("headers"));
        for (Map.Entry<String, Object> entry : tokenHeaders.entrySet()) {
            headers.set(entry.getKey(), stringValue(resolveValue(entry.getValue(), params)));
        }
        Object body = buildTokenRequestBody(config.get("body"), params, contentType);
        String methodText = stringValue(config.get("method"));
        HttpMethod method = methodText.isEmpty() ? HttpMethod.POST : HttpMethod.resolve(methodText);
        if (method == null) {
            method = HttpMethod.POST;
        }
        trace.requestMethod = method.name();
        trace.requestUrl = tokenUrl;
        trace.requestHeaders = headersToLog(headers);
        trace.requestBody = body;
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(tokenUrl, method, new HttpEntity<>(body, headers), String.class);
        Object parsed = parseJsonOrRaw(response.getBody());
        Object token = readToken(response, parsed, stringValue(config.get("tokenPath")));
        int ttlSeconds = resolveTokenTtlSeconds(config, response, parsed,
                datasource.getTokenCacheSeconds() == null ? 0 : datasource.getTokenCacheSeconds());

        Map<String, Object> responseDetail = new LinkedHashMap<>();
        responseDetail.put("httpStatus", response.getStatusCodeValue());
        responseDetail.put("headers", headersToLog(response.getHeaders()));
        responseDetail.put("body", parsed);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authType", datasource.getAuthType());
        result.put("tokenUrl", tokenUrl);
        result.put("method", method.name());
        result.put("contentType", contentType.toString());
        result.put("tokenPath", firstText(config.get("tokenPath"), "body.data.access_token/body.access_token/body.token/headers.Authorization"));
        result.put("expiresInPath", stringValue(config.get("expiresInPath")));
        result.put("token", maskToken(token == null ? "" : String.valueOf(token)));
        result.put("expiresInSeconds", ttlSeconds);
        result.put("request", requestDetail(trace));
        result.put("response", responseDetail);
        trace.responseStatus = response.getStatusCodeValue();
        trace.responseBody = responseDetail;
        return result;
    }

    private String buildTokenUrl(RuleExternalDatasource datasource, String tokenUrl) {
        if (!hasText(tokenUrl)) {
            return "";
        }
        if (tokenUrl.startsWith("http://") || tokenUrl.startsWith("https://")) {
            return tokenUrl;
        }
        return buildUrl(datasource.getBaseUrl(), tokenUrl);
    }

    MediaType resolveTokenContentType(Map<String, Object> config) {
        String contentType = firstText(config.get("contentType"), config.get("content_type"), "application/json");
        return MediaType.parseMediaType(contentType);
    }

    Object buildTokenRequestBody(Object bodyConfig, Map<String, Object> params, MediaType contentType) {
        Object body = resolveValue(bodyConfig, params);
        if (MediaType.MULTIPART_FORM_DATA.includes(contentType)
                || MediaType.APPLICATION_FORM_URLENCODED.includes(contentType)) {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            Map<String, Object> bodyMap = parseNestedMap(body);
            for (Map.Entry<String, Object> entry : bodyMap.entrySet()) {
                form.add(entry.getKey(), entry.getValue());
            }
            return form;
        }
        return body;
    }

    private Object readToken(ResponseEntity<String> response, Object parsed, String tokenPath) {
        if (hasText(tokenPath)) {
            return readResponsePath(response, parsed, tokenPath);
        }
        Object token = readResponsePath(response, parsed, "body.data.access_token");
        if (token != null) {
            return token;
        }
        token = readResponsePath(response, parsed, "body.access_token");
        if (token != null) {
            return token;
        }
        token = readResponsePath(response, parsed, "body.token");
        return token != null ? token : readResponsePath(response, parsed, "headers.Authorization");
    }

    private int resolveTokenTtlSeconds(Map<String, Object> config, ResponseEntity<String> response,
                                       Object tokenResponse, int defaultCacheSeconds) {
        Object expiresIn = readResponsePath(response, tokenResponse, stringValue(config.get("expiresInPath")));
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

    private Object readResponsePath(ResponseEntity<String> response, Object body, String path) {
        if (!hasText(path)) {
            return body;
        }
        String normalized = path.startsWith("$.") ? path.substring(2) : path;
        if (normalized.startsWith("body.")) {
            return readPath(body, normalized.substring("body.".length()));
        }
        String headerName = headerNameFromPath(normalized);
        if (headerName != null) {
            return response == null ? null : response.getHeaders().getFirst(headerName);
        }
        return readPath(body, normalized);
    }

    private String headerNameFromPath(String normalized) {
        String[] prefixes = {"headers.", "header.", "responseHeaders."};
        for (String prefix : prefixes) {
            if (normalized.startsWith(prefix)) {
                String name = normalized.substring(prefix.length());
                return hasText(name) ? name : null;
            }
        }
        return null;
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

    Map<String, Object> applyResponseMapping(RuleExternalApiConfig apiConfig, Map<String, Object> response) {
        Map<String, Object> mapping = parseJsonMap(apiConfig.getResponseMapping());
        if (mapping.isEmpty()) {
            return response;
        }
        Object body = response.get("body");
        Map<String, Object> mapped = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            Object path = entry.getValue();
            Object value = readPath(response, stringValue(path));
            if (value == null && body != null) {
                value = readPath(body, stringValue(path));
            }
            mapped.put(entry.getKey(), value);
        }
        response.put("rawBody", body);
        response.put("body", mapped);
        response.put("mapped", mapped);
        return response;
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
        String normalized = path.startsWith("$.") ? path.substring(2) : path;
        Object current = root;
        String[] parts = normalized.split("\\.");
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String firstText(Object... values) {
        if (values == null) {
            return "";
        }
        for (Object value : values) {
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private void logDatasourceCall(RuleExternalApiConfig apiConfig, RuleExternalDatasource datasource, InvokeTrace trace,
                                   boolean success, Map<String, Object> response, String errorMessage, long costTimeMs) {
        if (runtimeCallLogService == null) {
            return;
        }
        RuleRuntimeCallLog log = new RuleRuntimeCallLog();
        log.setModuleType("DATASOURCE");
        log.setActionType("API_INVOKE");
        log.setProjectId(datasource.getProjectId());
        log.setTargetRefId(apiConfig.getId());
        log.setTargetCode(apiConfig.getApiCode());
        log.setTargetName(apiConfig.getApiName());
        log.setSuccess(success ? 1 : 0);
        if (trace != null) {
            log.setRequestMethod(trace.requestMethod);
            log.setRequestUrl(trace.requestUrl);
            log.setRequestHeaders(runtimeCallLogService.toJson(trace.requestHeaders));
            log.setRequestParams(runtimeCallLogService.toJson(trace.requestParams));
            log.setRequestBody(runtimeCallLogService.toJson(trace.requestBody));
            log.setResponseStatus(trace.responseStatus);
        }
        log.setResponseBody(runtimeCallLogService.toJson(response != null ? response : (trace == null ? null : trace.responseBody)));
        log.setErrorMessage(errorMessage);
        log.setCostTimeMs(costTimeMs);
        runtimeCallLogService.safeSave(log);
    }

    private void logDatasourceAuthTest(RuleExternalDatasource datasource, InvokeTrace trace,
                                       boolean success, Map<String, Object> response,
                                       String errorMessage, long costTimeMs) {
        if (runtimeCallLogService == null) {
            return;
        }
        RuleRuntimeCallLog log = new RuleRuntimeCallLog();
        log.setModuleType("DATASOURCE");
        log.setActionType("AUTH_TEST");
        log.setProjectId(datasource.getProjectId());
        log.setTargetRefId(datasource.getId());
        log.setTargetCode(datasource.getDatasourceCode());
        log.setTargetName(datasource.getDatasourceName());
        log.setSuccess(success ? 1 : 0);
        if (trace != null) {
            log.setRequestMethod(trace.requestMethod);
            log.setRequestUrl(trace.requestUrl);
            log.setRequestHeaders(runtimeCallLogService.toJson(trace.requestHeaders));
            log.setRequestParams(runtimeCallLogService.toJson(trace.requestParams));
            log.setRequestBody(runtimeCallLogService.toJson(trace.requestBody));
            log.setResponseStatus(trace.responseStatus);
        }
        log.setResponseBody(runtimeCallLogService.toJson(response));
        log.setErrorMessage(errorMessage);
        log.setCostTimeMs(costTimeMs);
        runtimeCallLogService.safeSave(log);
    }

    private Map<String, Object> previewStaticAuthConfig(String authType, Map<String, Object> config, Map<String, Object> params) {
        Map<String, Object> preview = new LinkedHashMap<>();
        if ("BASIC".equals(authType)) {
            preview.put("header", HttpHeaders.AUTHORIZATION);
            preview.put("value", "Basic ******");
        } else if ("BEARER".equals(authType)) {
            preview.put("header", HttpHeaders.AUTHORIZATION);
            preview.put("value", "Bearer " + maskToken(stringValue(resolveValue(config.get("token"), params))));
        } else if ("API_KEY".equals(authType)) {
            preview.put("location", firstText(config.get("location"), "HEADER"));
            preview.put("name", stringValue(config.get("name")));
            preview.put("value", maskToken(stringValue(resolveValue(config.get("value"), params))));
        } else {
            preview.put("message", "未配置鉴权或使用自定义鉴权");
        }
        return preview;
    }

    private Map<String, Object> requestDetail(InvokeTrace trace) {
        Map<String, Object> request = new LinkedHashMap<>();
        if (trace == null) {
            return request;
        }
        request.put("method", trace.requestMethod);
        request.put("url", trace.requestUrl);
        request.put("headers", trace.requestHeaders);
        request.put("params", trace.requestParams);
        request.put("body", trace.requestBody);
        return request;
    }

    private Map<String, Object> headersToLog(HttpHeaders headers) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (headers == null) {
            return result;
        }
        for (Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
            String value = entry.getValue() == null || entry.getValue().isEmpty() ? "" : entry.getValue().get(0);
            result.put(entry.getKey(), maskHeaderValue(entry.getKey(), value));
        }
        return result;
    }

    private String maskHeaderValue(String name, String value) {
        if (value == null) {
            return null;
        }
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.contains("authorization") || lower.contains("token")
                || lower.contains("secret") || lower.contains("key")) {
            return maskToken(value);
        }
        return value;
    }

    private String maskToken(String value) {
        if (!hasText(value)) {
            return "";
        }
        String text = value.trim();
        String prefix = "";
        if (text.toLowerCase().startsWith("bearer ")) {
            prefix = text.substring(0, 7);
            text = text.substring(7);
        }
        if (text.length() <= 8) {
            return prefix + "******";
        }
        return prefix + text.substring(0, 4) + "******" + text.substring(text.length() - 4);
    }

    private String stripBearerPrefix(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.toLowerCase().startsWith("bearer ") ? text.substring(7).trim() : text;
    }

    private static class InvokeTrace {
        private String requestMethod;
        private String requestUrl;
        private Map<String, Object> requestHeaders;
        private Object requestParams;
        private Object requestBody;
        private Integer responseStatus;
        private Object responseBody;
    }

    private static class TokenCache {
        private final String token;
        private final long expiresAt;

        private TokenCache(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }

    private static class ApiResponseCache {
        private final Map<String, Object> response;
        private final long expiresAt;

        private ApiResponseCache(Map<String, Object> response, long expiresAt) {
            this.response = response;
            this.expiresAt = expiresAt;
        }
    }
}
