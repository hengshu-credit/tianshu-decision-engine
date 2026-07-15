package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
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
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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

    @Resource
    private ExternalApiSecurityService externalApiSecurityService = new ExternalApiSecurityService();

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
                if (shouldRecordSuccessBilling(apiConfig, result)) {
                    billingService.recordApiExecution(apiConfig, datasource, true, cost, null);
                }
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
        if (shouldRecordFailedBilling(apiConfig, trace)) {
            billingService.recordApiExecution(apiConfig, datasource, false, cost, message);
        }
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
        if (isRuleEngineDatasource(datasource)) {
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
        requestBody = externalApiSecurityService.prepareRequest(url, requestBody, apiConfig.getAuthApiConfig());
        requestBody = buildHttpRequestBody(requestBody, headers.getContentType());
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
        trace.requestIssued = true;
        ResponseEntity<String> response = restTemplate.exchange(
                builder.build(true).toUri(),
                method,
                new HttpEntity<>(requestBody, headers),
                String.class);

        Map<String, Object> result = new LinkedHashMap<>();
        Object responseBody = parseJsonOrRaw(response.getBody());
        responseBody = externalApiSecurityService.processResponse(responseBody, apiConfig.getAuthApiConfig());
        trace.responseStatus = response.getStatusCodeValue();
        trace.responseBody = responseBody;
        result.put("success", response.getStatusCode().is2xxSuccessful());
        result.put("httpStatus", response.getStatusCodeValue());
        result.put("body", responseBody);
        return applyResponseMapping(apiConfig, result);
    }

    boolean isRuleEngineDatasource(RuleExternalDatasource datasource) {
        if (datasource == null) {
            return false;
        }
        if ("RULE_ENGINE".equalsIgnoreCase(datasource.getProtocol())) {
            return true;
        }
        return "tianshu_rule_engine".equalsIgnoreCase(datasource.getDatasourceCode());
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
            wrapper.and(w -> w.eq(RulePublished::getProjectCode, projectCode)
                    .or()
                    .exists(buildLinkedGlobalRuleExistsSql(datasource.getProjectId())));
        } else if (datasource.getProjectId() != null && datasource.getProjectId() > 0) {
            wrapper.exists(buildLinkedGlobalRuleExistsSql(datasource.getProjectId()));
        }
        RulePublished published = publishedMapper.selectOne(wrapper);
        if (published == null) {
            throw new IllegalArgumentException("内部规则不存在或未发布: " + ruleCode);
        }
        Object mapped = config.get("params");
        Map<String, Object> ruleParams = mapped == null ? params : parseNestedMap(resolveRequestMappingObject(mapped, params));
        trace.requestMethod = "POST";
        trace.requestUrl = "rule-engine://local/" + ruleCode;
        trace.requestBody = ruleParams;
        trace.requestIssued = true;
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

    private String buildLinkedGlobalRuleExistsSql(Long projectId) {
        return "SELECT 1 FROM rule_definition_ref rdr " +
                "WHERE rdr.definition_id = rule_published.definition_id " +
                "AND rdr.project_id = " + projectId;
    }

    private String resolveProjectCode(Long projectId) {
        if (projectId == null) {
            return null;
        }
        com.hengshucredit.rule.model.entity.RuleProject project = projectService.getById(projectId);
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
                applyTokenHeader(headers, config, token);
            }
        }
    }

    private void applyTokenHeader(HttpHeaders headers, Map<String, Object> config, String token) {
        if (!config.containsKey("tokenHeaderName") && !config.containsKey("tokenPrefix")) {
            headers.setBearerAuth(token);
            return;
        }
        String headerName = stringValue(config.get("tokenHeaderName")).trim();
        if (headerName.isEmpty()) {
            throw new IllegalArgumentException("Token Header名称不能为空");
        }
        String prefix = config.containsKey("tokenPrefix") ? stringValue(config.get("tokenPrefix")) : "Bearer ";
        headers.set(headerName, prefix + token);
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

    Object buildHttpRequestBody(Object body, MediaType contentType) {
        if (!(body instanceof Map) || contentType == null
                || !MediaType.APPLICATION_FORM_URLENCODED.includes(contentType)) {
            return body;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        for (Map.Entry<String, Object> entry : parseNestedMap(body).entrySet()) {
            if (entry.getValue() != null) {
                form.add(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return form;
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
        Map<String, Object> mapping = parseJsonMap(apiConfig.getRequestMapping());
        if (!mapping.isEmpty()) {
            return resolveRequestMappingObject(mapping, params);
        }
        if (apiConfig.getBodyTemplate() != null && !apiConfig.getBodyTemplate().trim().isEmpty()) {
            String resolved = resolveTemplate(apiConfig.getBodyTemplate(), params);
            return parseJsonOrRaw(resolved);
        }
        return params;
    }

    private Object resolveRequestMappingObject(Object value, Map<String, Object> params) {
        if (value instanceof Map) {
            Map<String, Object> source = parseNestedMap(value);
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                putMappedRequestValue(result, normalizeOutputFieldName(entry.getKey()),
                        resolveRequestMappingObject(entry.getValue(), params));
            }
            return result;
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                result.add(resolveRequestMappingObject(item, params));
            }
            return result;
        }
        return resolveValue(value, params);
    }

    private String normalizeOutputFieldName(String name) {
        if (name == null) {
            return "";
        }
        String text = String.valueOf(name).trim();
        return text.startsWith("$.") ? text.substring(2) : text;
    }

    private void putMappedRequestValue(Map<String, Object> result, String outputPath, Object value) {
        if (!hasText(outputPath)) {
            return;
        }
        String[] parts = outputPath.split("\\.");
        Map<String, Object> current = result;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i] == null ? "" : parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            if (i == parts.length - 1) {
                current.put(part, value);
                return;
            }
            Object next = current.get(part);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                current.put(part, next);
            }
            current = castStringObjectMap(next);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castStringObjectMap(Object value) {
        return (Map<String, Object>) value;
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
            mapped.put(entry.getKey(), resolveResponseMappingValue(entry.getValue(), response, body));
        }
        response.put("rawBody", body);
        response.put("body", mapped);
        response.put("mapped", mapped);
        return response;
    }

    private Object resolveResponseMappingValue(Object rule, Map<String, Object> response, Object body) {
        if (rule instanceof List) {
            return firstReadableValue((List<?>) rule, response, body);
        }
        if (rule instanceof Map) {
            Map<String, Object> config = parseNestedMap(rule);
            Object cases = config.get("cases");
            if (cases instanceof Iterable) {
                for (Object item : (Iterable<?>) cases) {
                    Map<String, Object> caseConfig = parseNestedMap(item);
                    if (caseConfig.isEmpty() || responseConditionMatches(caseConfig.get("when"), response, body)) {
                        Object value = resolveResponseMappingCase(caseConfig, response, body);
                        if (value != null || caseConfig.containsKey("default")) {
                            return value;
                        }
                    }
                }
                return config.containsKey("default") ? config.get("default") : null;
            }
            if (config.containsKey("path") || config.containsKey("paths")) {
                return resolveResponseMappingCase(config, response, body);
            }
            return resolveValue(config, response);
        }
        return readMappedPath(response, body, stringValue(rule));
    }

    private Object resolveResponseMappingCase(Map<String, Object> config, Map<String, Object> response, Object body) {
        Object value = null;
        if (config.get("paths") instanceof List) {
            value = firstReadableValue((List<?>) config.get("paths"), response, body);
        }
        if (value == null && config.containsKey("path")) {
            value = readMappedPath(response, body, stringValue(config.get("path")));
        }
        return value == null && config.containsKey("default") ? config.get("default") : value;
    }

    private Object firstReadableValue(List<?> paths, Map<String, Object> response, Object body) {
        for (Object path : paths) {
            Object value = readMappedPath(response, body, stringValue(path));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Object readMappedPath(Map<String, Object> response, Object body, String path) {
        Object value = readPath(response, path);
        if (value == null && body != null) {
            value = readPath(body, path);
        }
        return value;
    }

    private boolean responseConditionMatches(Object conditionObject, Map<String, Object> response, Object body) {
        if (conditionObject == null) {
            return true;
        }
        Map<String, Object> condition = parseNestedMap(conditionObject);
        String type = firstText(condition.get("type"));
        if ("group".equalsIgnoreCase(type)) {
            Object childrenObject = condition.get("children");
            if (!(childrenObject instanceof Iterable)) {
                return true;
            }
            String op = firstText(condition.get("op"), "AND");
            boolean hasChild = false;
            if ("OR".equalsIgnoreCase(op)) {
                for (Object child : (Iterable<?>) childrenObject) {
                    hasChild = true;
                    if (responseConditionMatches(child, response, body)) {
                        return true;
                    }
                }
                return !hasChild;
            }
            for (Object child : (Iterable<?>) childrenObject) {
                hasChild = true;
                if (!responseConditionMatches(child, response, body)) {
                    return false;
                }
            }
            return true;
        }
        String path = firstText(condition.get("varCode"), condition.get("path"), condition.get("field"));
        if (!hasText(path)) {
            return true;
        }
        Object actual = readMappedPath(response, body, path);
        Object expected = condition.get("value");
        if ("VAR".equalsIgnoreCase(firstText(condition.get("valueKind")))) {
            expected = readMappedPath(response, body, stringValue(expected));
        }
        String operator = firstText(condition.get("operator"), "==").toLowerCase();
        return compareConditionValue(actual, expected, operator);
    }

    private boolean compareConditionValue(Object actual, Object expected, String operator) {
        String op = operator == null ? "==" : operator.toLowerCase();
        if ("*".equals(op)) return true;
        if ("is_null".equals(op)) return actual == null;
        if ("not_null".equals(op)) return actual != null;
        if ("is_empty".equals(op)) return isEmptyValue(actual);
        if ("not_empty".equals(op)) return !isEmptyValue(actual);
        if ("is_true".equals(op)) return actual != null && booleanValue(actual);
        if ("is_false".equals(op)) return actual != null && !booleanValue(actual);

        boolean equal = valuesEqual(actual, expected);
        if ("==".equals(op) || "eq".equals(op)) return equal;
        if ("!=".equals(op) || "<>".equals(op) || "ne".equals(op)) return !equal;

        java.math.BigDecimal actualNumber = toBigDecimal(actual);
        java.math.BigDecimal expectedNumber = toBigDecimal(expected);
        if (actualNumber != null && expectedNumber != null) {
            int cmp = actualNumber.compareTo(expectedNumber);
            if (">".equals(op)) return cmp > 0;
            if (">=".equals(op)) return cmp >= 0;
            if ("<".equals(op)) return cmp < 0;
            if ("<=".equals(op)) return cmp <= 0;
            if ("between".equals(op) || "not_between".equals(op)) {
                List<String> values = splitConditionValues(expected);
                if (values.size() < 2) return false;
                java.math.BigDecimal left = toBigDecimal(values.get(0));
                java.math.BigDecimal right = toBigDecimal(values.get(1));
                boolean inRange = left != null && right != null
                        && actualNumber.compareTo(left) >= 0
                        && actualNumber.compareTo(right) <= 0;
                return "between".equals(op) ? inRange : !inRange;
            }
        }

        String actualText = actual == null ? "" : String.valueOf(actual);
        String expectedText = expected == null ? "" : String.valueOf(expected);
        if ("contains".equals(op)) return containsValue(actual, expected);
        if ("not_contains".equals(op)) return !containsValue(actual, expected);
        if ("starts_with".equals(op)) return actualText.startsWith(expectedText);
        if ("not_starts_with".equals(op)) return !actualText.startsWith(expectedText);
        if ("ends_with".equals(op)) return actualText.endsWith(expectedText);
        if ("not_ends_with".equals(op)) return !actualText.endsWith(expectedText);
        if ("in".equals(op) || "not_in".equals(op)) {
            boolean found = splitConditionValues(expected).stream().anyMatch(item -> valuesEqual(actual, item));
            return "in".equals(op) ? found : !found;
        }
        if ("contains_any".equals(op) || "contains_all".equals(op)) {
            List<String> values = splitConditionValues(expected);
            boolean matched = "contains_all".equals(op)
                    ? values.stream().allMatch(item -> containsValue(actual, item))
                    : values.stream().anyMatch(item -> containsValue(actual, item));
            return matched;
        }
        if ("has_key".equals(op) || "not_has_key".equals(op)) {
            boolean hasKey = actual instanceof Map && ((Map<?, ?>) actual).containsKey(expectedText);
            return "has_key".equals(op) ? hasKey : !hasKey;
        }
        return equal;
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) return true;
        if (value instanceof CharSequence) return String.valueOf(value).isEmpty();
        if (value instanceof Map) return ((Map<?, ?>) value).isEmpty();
        if (value instanceof Iterable) return !((Iterable<?>) value).iterator().hasNext();
        if (value.getClass().isArray()) return Array.getLength(value) == 0;
        return false;
    }

    private boolean containsValue(Object actual, Object expected) {
        if (actual == null) return false;
        if (actual instanceof Map) return ((Map<?, ?>) actual).containsKey(String.valueOf(expected));
        if (actual instanceof Iterable) {
            for (Object item : (Iterable<?>) actual) {
                if (valuesEqual(item, expected)) return true;
            }
            return false;
        }
        if (actual.getClass().isArray()) {
            int length = Array.getLength(actual);
            for (int i = 0; i < length; i++) {
                if (valuesEqual(Array.get(actual, i), expected)) return true;
            }
            return false;
        }
        return String.valueOf(actual).contains(String.valueOf(expected));
    }

    private List<String> splitConditionValues(Object value) {
        if (value instanceof Iterable) {
            List<String> result = new ArrayList<>();
            for (Object item : (Iterable<?>) value) {
                if (item != null && hasText(String.valueOf(item))) result.add(String.valueOf(item).trim());
            }
            return result;
        }
        String text = value == null ? "" : String.valueOf(value);
        String[] parts = text.split(",");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (hasText(part)) result.add(part.trim());
        }
        return result;
    }

    boolean matchesBillingCondition(String billingCondition, Map<String, Object> response) {
        if (!hasText(billingCondition)) {
            return true;
        }
        Map<String, Object> condition = parseJsonMap(billingCondition);
        String mode = firstText(condition.get("mode"), condition.get("billingMode"));
        if ("QUERY".equalsIgnoreCase(mode)) {
            return true;
        }
        String path = firstText(condition.get("path"), condition.get("field"));
        if (!hasText(path)) {
            return true;
        }
        Object actual = readPath(response, path);
        Object expected = condition.get("value");
        String operator = firstText(condition.get("operator"), "==").toLowerCase();
        boolean equal = valuesEqual(actual, expected);
        if ("!=".equals(operator) || "<>".equals(operator) || "ne".equals(operator)) {
            return !equal;
        }
        return equal;
    }

    private boolean shouldRecordSuccessBilling(RuleExternalApiConfig apiConfig, Map<String, Object> result) {
        return matchesBillingCondition(apiConfig.getBillingCondition(), result);
    }

    private boolean shouldRecordFailedBilling(RuleExternalApiConfig apiConfig, InvokeTrace trace) {
        if (trace == null || !trace.requestIssued) {
            return false;
        }
        Map<String, Object> condition = parseJsonMap(apiConfig.getBillingCondition());
        String mode = firstText(condition.get("mode"), condition.get("billingMode"));
        return !("HIT".equalsIgnoreCase(mode));
    }

    private boolean valuesEqual(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        java.math.BigDecimal actualNumber = toBigDecimal(actual);
        java.math.BigDecimal expectedNumber = toBigDecimal(expected);
        if (actualNumber != null && expectedNumber != null) {
            return actualNumber.compareTo(expectedNumber) == 0;
        }
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    private java.math.BigDecimal toBigDecimal(Object value) {
        if (value instanceof Number || value instanceof String) {
            try {
                return new java.math.BigDecimal(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
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
            } else if (current instanceof List) {
                Integer index = parseIndex(part);
                if (index == null || index < 0 || index >= ((List<?>) current).size()) {
                    return null;
                }
                current = ((List<?>) current).get(index);
            } else if (current != null && current.getClass().isArray()) {
                Integer index = parseIndex(part);
                if (index == null || index < 0 || index >= Array.getLength(current)) {
                    return null;
                }
                current = Array.get(current, index);
            } else {
                return null;
            }
        }
        return current;
    }

    private Integer parseIndex(String text) {
        try {
            return Integer.valueOf(text);
        } catch (Exception e) {
            return null;
        }
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
            log.setRequestParams(runtimeCallLogService.toJson(maskSensitiveForLog(trace.requestParams)));
            log.setRequestBody(runtimeCallLogService.toJson(maskSensitiveForLog(trace.requestBody)));
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
            log.setRequestParams(runtimeCallLogService.toJson(maskSensitiveForLog(trace.requestParams)));
            log.setRequestBody(runtimeCallLogService.toJson(maskSensitiveForLog(trace.requestBody)));
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
        request.put("params", maskSensitiveForLog(trace.requestParams));
        request.put("body", maskSensitiveForLog(trace.requestBody));
        return request;
    }

    Object maskSensitiveForLog(Object value) {
        if (value instanceof Map) {
            Map<?, ?> source = (Map<?, ?>) value;
            Map<String, Object> masked = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey());
                Object item = entry.getValue();
                masked.put(key, isSensitiveKey(key) ? maskToken(String.valueOf(item)) : maskSensitiveForLog(item));
            }
            return masked;
        }
        if (value instanceof List) {
            List<?> source = (List<?>) value;
            List<Object> masked = new ArrayList<>(source.size());
            for (Object item : source) {
                masked.add(maskSensitiveForLog(item));
            }
            return masked;
        }
        return value;
    }

    private boolean isSensitiveKey(String name) {
        String lower = name == null ? "" : name.toLowerCase();
        return lower.contains("password") || lower.contains("token")
                || lower.contains("secret") || lower.contains("key")
                || lower.contains("authorization");
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
        if (isSensitiveKey(lower)) {
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
        private boolean requestIssued;
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
