package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.core.util.MissingValueSemantics;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.Resource;
import java.lang.reflect.Array;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
    private RuntimeTraceService runtimeTraceService;

    @Resource
    private ExternalApiCallbackStore callbackStore;

    @Resource
    private ExternalApiScriptService externalApiScriptService = new ExternalApiScriptService();

    @Resource
    private ApiHttpClientRegistry apiHttpClientRegistry = new ApiHttpClientRegistry();

    @Resource
    private ExternalTokenCache externalTokenCache = new ExternalTokenCache(
            new ExternalCallProperties(), (ExternalTokenDistributedStore) null);

    @Resource
    private ExternalApiGuardRegistry externalApiGuardRegistry =
            new ExternalApiGuardRegistry(new ExternalCallProperties());

    @Resource
    private ExternalApiCircuitBreakerRegistry circuitBreakerRegistry =
            new ExternalApiCircuitBreakerRegistry(new ExternalCallProperties());

    @Resource
    private ExternalApiResponseCache externalApiResponseCache = new ExternalApiResponseCache(
            new ExternalCallProperties(), null, System::currentTimeMillis);

    public Map<String, Object> invoke(Long apiConfigId, Map<String, Object> params) {
        RuleExternalApiConfig apiConfig = apiConfigMapper.selectById(apiConfigId);
        if (apiConfig == null) {
            throw new IllegalArgumentException("API接口配置不存在");
        }
        if (apiConfig.getStatus() != null && !Integer.valueOf(1).equals(apiConfig.getStatus())) {
            throw new IllegalArgumentException("外数API接口未启用");
        }
        return invoke(apiConfig, params, true);
    }

    /** 使用页面当前配置执行测试，不要求先覆盖数据库中的已保存配置。 */
    public Map<String, Object> invoke(RuleExternalApiConfig apiConfig, Map<String, Object> params) {
        return invoke(apiConfig, params, false);
    }

    private Map<String, Object> invoke(RuleExternalApiConfig apiConfig, Map<String, Object> params,
                                       boolean useResponseCache) {
        if (apiConfig == null) {
            throw new IllegalArgumentException("API接口配置不能为空");
        }
        ExternalApiConfigValidator.validate(apiConfig);
        int totalTimeoutMs = apiConfig.getTimeoutMs() == null ? 3000 : Math.max(1, apiConfig.getTimeoutMs());
        try (RequestDeadlineContext.Scope ignored = RequestDeadlineContext.limit(totalTimeoutMs)) {
        RuleExternalDatasource datasource = datasourceMapper.selectById(apiConfig.getDatasourceId());
        if (datasource == null) {
            throw new IllegalArgumentException("外数数据源不存在");
        }
        if (datasource.getStatus() != null && !Integer.valueOf(1).equals(datasource.getStatus())) {
            throw new IllegalArgumentException("外数数据源未启用");
        }
        int retryCount = apiConfig.getRetryCount() == null ? 0 : Math.max(apiConfig.getRetryCount(), 0);
        int retryIntervalMs = apiConfig.getRetryIntervalMs() == null ? 0 : Math.max(apiConfig.getRetryIntervalMs(), 0);
        int responseCacheSeconds = useResponseCache && apiConfig.getResponseCacheSeconds() != null
                ? Math.max(apiConfig.getResponseCacheSeconds(), 0) : 0;
        Map<String, Object> invokeParams = normalizeRequestParams(params);
        String responseCacheKey = responseCacheSeconds > 0
                ? buildResponseCacheKey(apiConfig.getId(), apiConfig.getCacheKeyConfig(), invokeParams) : null;
        ExternalApiResponseCache.Lookup cachedResponse = responseCacheKey == null
                ? null : externalApiResponseCache.get(apiConfig, responseCacheKey);
        long start = System.currentTimeMillis();
        InvokeTrace trace = new InvokeTrace();
        trace.callId = UUID.randomUUID().toString();
        trace.requestParams = invokeParams;
        trace.rootInput = com.hengshucredit.rule.core.engine.RuntimeContextBridge.currentContext().rootInput();
        trace.cacheKey = responseCacheKey;
        trace.cacheStatus = responseCacheSeconds <= 0 ? "DISABLED"
                : responseCacheKey == null ? "CACHE_KEY_INCOMPLETE" : "MISS";
        if (runtimeTraceService != null) {
            trace.runtimeTrace = runtimeTraceService.startModule(
                    "DATASOURCE", datasource.getProjectId(), apiConfig.getId(), apiConfig.getApiCode());
        }
        Map<String, Object> rootInputStep = new LinkedHashMap<>();
        rootInputStep.put("rootRequestBody", trace.rootInput);
        rootInputStep.put("apiRequestParams", trace.requestParams);
        recordTraceStep(trace, "REQUEST_INPUT", "规则请求入参", "SUCCESS",
                rootInputStep, null, null);
        if (cachedResponse != null && !cachedResponse.isStale()) {
            trace.cacheStatus = "HIT";
            Map<String, Object> cached = copyCachedResponse(cachedResponse.getResponse(), true, false, 0);
            restoreCachedTrace(cached, trace);
            attachExternalCall(cached, trace);
            logDatasourceCall(apiConfig, datasource, trace, true, cached, null, 0);
            return cached;
        }
        Exception lastError = null;
        for (int i = 0; i <= retryCount; i++) {
            try {
                Map<String, Object> result;
                if (responseCacheSeconds > 0 && responseCacheKey != null) {
                    ExternalApiResponseCache.LoadResult loaded = externalApiResponseCache.singleFlight(
                            apiConfig, responseCacheKey,
                            () -> doInvoke(apiConfig, datasource, invokeParams, trace));
                    result = loaded.getResponse();
                    if (loaded.isShared()) trace.cacheStatus = "COALESCED";
                } else {
                    result = doInvoke(apiConfig, datasource, invokeParams, trace);
                }
                RequestDeadlineContext.check();
                long cost = System.currentTimeMillis() - start;
                result.put("costTimeMs", cost);
                result.put("cached", false);
                result.put("cacheConfigured", responseCacheSeconds > 0);
                result.put("cacheStatus", trace.cacheStatus);
                result.put("dataOrigin", "LIVE");
                result.put("sourceOutcome", !result.containsKey("success") || booleanValue(result.get("success"))
                        ? "SUCCESS" : "ERROR");
                attachExternalCall(result, trace);
                if (responseCacheSeconds > 0 && responseCacheKey != null
                        && (!result.containsKey("success") || booleanValue(result.get("success")))) {
                    externalApiResponseCache.put(apiConfig, responseCacheKey, result);
                }
                if (shouldRecordSuccessBilling(apiConfig, result)) {
                    billingService.recordApiExecution(apiConfig, datasource, true, cost, null);
                }
                logDatasourceCall(apiConfig, datasource, trace, true, result, null, cost);
                return result;
            } catch (Exception e) {
                if (e instanceof RuleRuntimeCallLogService.HistoryLogWriteException historyFailure) throw historyFailure;
                lastError = e;
                if (e instanceof TokenRefreshRejectedException
                        || e instanceof ExternalApiGuardRegistry.RejectedException
                        || e instanceof ExternalApiCircuitBreakerRegistry.OpenException
                        || e instanceof ApiHttpClientRegistry.PoolBusyException) {
                    break;
                }
                if ("ASYNC".equals(apiConfig.getRequestMode())
                        || !retryAllowedForMethod(apiConfig)
                        || i >= retryCount || !shouldRetry(apiConfig, e)) {
                    break;
                }
                int retryDelayMs = retryDelayMs(apiConfig, retryIntervalMs, i);
                if (retryDelayMs > 0) {
                    try {
                        int remaining = RequestDeadlineContext.remainingMillis();
                        if (remaining == 0) {
                            break;
                        }
                        Thread.sleep(Math.min(retryDelayMs, remaining));
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
            trace.cacheStatus = "STALE";
            Map<String, Object> cached = copyCachedResponse(cachedResponse.getResponse(), true, true, cost);
            restoreCachedTrace(cached, trace);
            cached.put("sourceOutcome", failureOutcome(lastError));
            cached.put("fallback", true);
            attachExternalCall(cached, trace);
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
            fallback.put("cacheConfigured", responseCacheSeconds > 0);
            fallback.put("cacheStatus", trace.cacheStatus);
            fallback.put("dataOrigin", "FALLBACK");
            fallback.put("sourceOutcome", failureOutcome(lastError));
            attachExternalCall(fallback, trace);
            logDatasourceCall(apiConfig, datasource, trace, false, fallback, message, cost);
            return fallback;
        }
        logDatasourceCall(apiConfig, datasource, trace, false, null, message, cost);
        throw new ApiInvokeException(message, lastError, responseCacheSeconds > 0, trace.cacheStatus,
                trace.requestIssued && trace.responseStatus == null);
        }
    }

    /** 保持业务字符串原样，同时收敛空容器和 Java 非有限浮点值。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeRequestParams(Map<String, Object> params) {
        Object normalized = MissingValueSemantics.normalize(params == null ? new HashMap<>() : params);
        if (normalized instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    public Map<String, Object> testDatasourceAuth(Long datasourceId, Map<String, Object> params) {
        RuleExternalDatasource datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            throw new IllegalArgumentException("外数数据源不存在");
        }
        if (datasource.getStatus() != null && !Integer.valueOf(1).equals(datasource.getStatus())) {
            throw new IllegalArgumentException("外数数据源未启用");
        }
        String authType = firstText(datasource.getAuthType(), "NONE")
                .toUpperCase(Locale.ROOT);
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

    /** 只构造最终请求并脱敏返回，不访问令牌接口、外部 HTTP 地址或内部规则。 */
    public Map<String, Object> previewRequest(RuleExternalApiConfig apiConfig, Map<String, Object> params,
                                              String previewToken) {
        if (apiConfig == null) {
            throw new IllegalArgumentException("API接口配置不能为空");
        }
        RuleExternalDatasource datasource = datasourceMapper.selectById(apiConfig.getDatasourceId());
        if (datasource == null) {
            throw new IllegalArgumentException("外数数据源不存在");
        }
        Map<String, Object> input = params == null ? new LinkedHashMap<>() : params;
        Map<String, Object> result = new LinkedHashMap<>();
        if (isRuleEngineDatasource(datasource)) {
            Map<String, Object> state = new LinkedHashMap<>();
            Map<String, Object> mapping = parseJsonMap(apiConfig.getRequestMapping());
            Object mapped = mapping.get("params");
            Map<String, Object> body = mapped == null ? new LinkedHashMap<>(input)
                    : parseNestedMap(resolveRequestMappingObject(mapped, input, requestNullPolicy(mapping)));
            body = requireScriptBodyMap(executeRequestScript(apiConfig, input, body,
                    new LinkedHashMap<>(), new LinkedHashMap<>(), state, previewToken,
                    apiConfig.getEndpointUrl(), "POST"));
            result.put("method", "POST");
            result.put("url", "rule-engine://local/" + firstText(mapping.get("ruleCode"),
                    apiConfig.getEndpointUrl(), apiConfig.getApiCode()));
            result.put("headers", new LinkedHashMap<>());
            result.put("query", new LinkedHashMap<>());
            result.put("body", maskSensitiveForPreview(body));
        } else {
            PreparedHttpRequest prepared = prepareHttpRequest(apiConfig, datasource, input, true, previewToken);
            result.put("method", prepared.method.name());
            result.put("url", prepared.baseUrl);
            result.put("contentType", prepared.headers.getContentType() == null
                    ? null : prepared.headers.getContentType().toString());
            result.put("headers", maskSensitiveForPreview(headersForScript(prepared.headers)));
            result.put("query", maskSensitiveForPreview(prepared.query));
            result.put("body", maskSensitiveForPreview(prepared.scriptBody));
        }
        result.put("networkCalled", false);
        return result;
    }

    String buildResponseCacheKey(Long apiConfigId, String cacheKeyConfig, Map<String, Object> params) {
        if (apiConfigId == null) {
            return null;
        }
        if (!hasText(cacheKeyConfig)) {
            return apiConfigId + ":" + sha256(canonicalParams(params));
        }
        Map<String, Object> config = parseJsonMap(cacheKeyConfig);
        Object componentsObject = config.get("components");
        if (!(componentsObject instanceof Iterable)) {
            return null;
        }
        List<Object> values = new ArrayList<>();
        for (Object componentObject : (Iterable<?>) componentsObject) {
            String path;
            if (componentObject instanceof Map) {
                Map<String, Object> component = parseNestedMap(componentObject);
                path = firstText(component.get("path"), component.get("sourcePath"));
            } else {
                path = stringValue(componentObject);
            }
            if (!hasText(path)) {
                return null;
            }
            Object value = readPath(params, path);
            if (value == null || (value instanceof CharSequence && !hasText(String.valueOf(value)))) {
                return null;
            }
            values.add(value);
        }
        if (values.isEmpty()) {
            return null;
        }
        return apiConfigId + ":" + sha256(JSON.toJSONString(values));
    }

    /** 规则根执行内的 API 幂等键：优先使用配置的缓存键，否则对完整请求参数做摘要。 */
    String invocationCacheKey(Long apiConfigId, Map<String, Object> params) {
        if (apiConfigId == null) {
            throw new IllegalArgumentException("API接口配置ID不能为空");
        }
        RuleExternalApiConfig config = apiConfigMapper == null
                ? null : apiConfigMapper.selectById(apiConfigId);
        String configuredKey = config == null ? null
                : buildResponseCacheKey(apiConfigId, config.getCacheKeyConfig(), params);
        if (configuredKey != null) {
            return "API:" + configuredKey;
        }
        return "API:" + apiConfigId + ":PARAMS:" + sha256(canonicalParams(params));
    }

    private String canonicalParams(Map<String, Object> params) {
        return JSON.toJSONString(canonicalize(params == null ? new LinkedHashMap<>() : params));
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new java.util.TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalize(item)));
            return sorted;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> values = new ArrayList<>();
            for (Object item : iterable) values.add(canonicalize(item));
            if (value instanceof Set<?>) {
                values.sort((left, right) -> JSON.toJSONString(left).compareTo(JSON.toJSONString(right)));
            }
            return values;
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) {
                values.add(canonicalize(Array.get(value, i)));
            }
            return values;
        }
        return value;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", e);
        }
    }

    Map<String, Object> copyCachedResponse(Map<String, Object> cached, boolean hit, boolean stale, long costTimeMs) {
        Map<String, Object> response = copyResponse(cached);
        response.put("cached", hit);
        response.put("cacheStale", stale);
        response.put("costTimeMs", costTimeMs);
        response.put("cacheConfigured", true);
        response.put("cacheStatus", stale ? "STALE" : (hit ? "HIT" : "MISS"));
        response.put("dataOrigin", stale ? "STALE_CACHE" : (hit ? "CACHE" : "LIVE"));
        return response;
    }

    private void restoreCachedTrace(Map<String, Object> cached, InvokeTrace trace) {
        if (cached == null || trace == null) return;
        trace.responseBody = cached.get("body");
        Object rawRequest = cached.get("rawRequestBody");
        trace.rawRequestBody = rawRequest == null ? null : String.valueOf(rawRequest);
        Object requestMetadata = cached.get("rawRequestMetadata");
        if (requestMetadata instanceof Map) {
            trace.rawRequestMetadata = new LinkedHashMap<>((Map<String, Object>) requestMetadata);
        }
        Object raw = cached.get("rawResponseBody");
        trace.rawResponseBody = raw == null ? rawPayload(cached.get("body")) : String.valueOf(raw);
        trace.originalRequestBody = stringOrNull(cached.get("originalRequestBody"));
        trace.originalResponseBody = stringOrNull(cached.get("originalResponseBody"));
        Object responseMetadata = cached.get("rawResponseMetadata");
        if (responseMetadata instanceof Map) {
            trace.rawResponseMetadata = (Map<String, Object>) responseMetadata;
        }
        Object externalCall = cached.get("externalCall");
        if (externalCall instanceof Map) {
            Object request = ((Map<?, ?>) externalCall).get("request");
            if (request instanceof Map) {
                Map<?, ?> requestMap = (Map<?, ?>) request;
                trace.requestMethod = stringOrNull(requestMap.get("method"));
                trace.requestUrl = stringOrNull(requestMap.get("url"));
                Object headers = requestMap.get("headers");
                if (headers instanceof Map) trace.requestHeaders = new LinkedHashMap<>((Map<String, Object>) headers);
                trace.requestParams = requestMap.get("params");
                trace.requestBody = requestMap.get("body");
            }
        }
        Object status = cached.get("httpStatus");
        if (!(status instanceof Number)) status = cached.get("responseStatus");
        if (status instanceof Number) trace.responseStatus = ((Number) status).intValue();
        if (trace.responseStatus == null && externalCall instanceof Map) {
            Object externalStatus = ((Map<?, ?>) externalCall).get("responseStatus");
            if (externalStatus instanceof Number) trace.responseStatus = ((Number) externalStatus).intValue();
        }
        Map<String, Object> cacheStepInput = new LinkedHashMap<>();
        cacheStepInput.put("cacheStatus", trace.cacheStatus);
        recordTraceStep(trace, "CACHE_RESULT", "缓存返回外数结果", "SUCCESS",
                cacheStepInput, maskSensitiveForLog(trace.responseBody), null);
    }

    private String failureOutcome(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String className = current.getClass().getSimpleName().toLowerCase();
            String message = current.getMessage();
            if (className.contains("timeout") || (message != null
                    && (message.toLowerCase().contains("timeout") || message.contains("超时")))) {
                return "TIMEOUT";
            }
            current = current.getCause();
        }
        return "ERROR";
    }

    private boolean shouldRetry(RuleExternalApiConfig apiConfig, Throwable error) {
        BusinessResponseException businessError = findCause(error, BusinessResponseException.class);
        if (businessError != null) {
            return businessError.isRetryable();
        }
        HttpStatusCodeException statusError = findCause(error, HttpStatusCodeException.class);
        if (statusError != null) {
            return retryStatusCodes(apiConfig.getRetryStatusCodes()).contains(statusError.getRawStatusCode());
        }
        if (isTimeoutError(error)) {
            return Integer.valueOf(1).equals(apiConfig.getRetryOnTimeout());
        }
        return !Integer.valueOf(0).equals(apiConfig.getRetryOnConnectionError())
                && (findCause(error, ResourceAccessException.class) != null
                || findCause(error, IOException.class) != null);
    }

    private boolean retryAllowedForMethod(RuleExternalApiConfig apiConfig) {
        String method = firstText(apiConfig == null ? null : apiConfig.getRequestMethod(), "POST")
                .toUpperCase(Locale.ROOT);
        if (Set.of("GET", "HEAD", "OPTIONS").contains(method)) {
            return true;
        }
        return Integer.valueOf(1).equals(apiConfig.getRetryNonIdempotent());
    }

    private Set<Integer> retryStatusCodes(String configured) {
        String value = hasText(configured) ? configured : "502,503,504";
        Set<Integer> result = new HashSet<>();
        for (String item : value.split(",")) {
            try {
                int status = Integer.parseInt(item.trim());
                if (status >= 100 && status <= 599) result.add(status);
            } catch (NumberFormatException ignored) {
                // Invalid items are ignored; save-time validation prevents new invalid configurations.
            }
        }
        return result;
    }

    private int retryDelayMs(RuleExternalApiConfig apiConfig, int baseIntervalMs, int attemptIndex) {
        double multiplier = apiConfig.getRetryBackoffMultiplier() == null
                ? 2D : Math.max(1D, apiConfig.getRetryBackoffMultiplier().doubleValue());
        int maxInterval = apiConfig.getRetryMaxIntervalMs() == null
                ? 1000 : Math.max(0, apiConfig.getRetryMaxIntervalMs());
        long exponential = Math.round(Math.max(0, baseIntervalMs) * Math.pow(multiplier, attemptIndex));
        long bounded = Math.min(maxInterval, exponential);
        int jitterBound = (int) Math.min(100L, bounded / 5L);
        long withJitter = bounded + (jitterBound <= 0 ? 0 : ThreadLocalRandom.current().nextInt(jitterBound + 1));
        return (int) Math.min(maxInterval, withJitter);
    }

    private boolean isTimeoutError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String name = current.getClass().getSimpleName().toLowerCase();
            String message = current.getMessage();
            if (name.contains("timeout") || (message != null && message.toLowerCase().contains("timed out"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) return type.cast(current);
            current = current.getCause();
        }
        return null;
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
        try (ExternalApiGuardRegistry.Permit ignored = externalApiGuardRegistry.acquire(apiConfig)) {
            ExternalApiCircuitBreakerRegistry.CircuitPermit circuitPermit;
            try {
                circuitPermit = circuitBreakerRegistry.acquire(apiConfig);
                trace.circuitState = circuitPermit.getState();
            } catch (ExternalApiCircuitBreakerRegistry.OpenException e) {
                trace.circuitState = e.getState();
                throw e;
            }
            try {
                Map<String, Object> result = doInvokeGuarded(apiConfig, datasource, params, trace);
                circuitPermit.success();
                return result;
            } catch (Exception e) {
                circuitPermit.failure();
                throw e;
            }
        }
    }

    private Map<String, Object> doInvokeGuarded(RuleExternalApiConfig apiConfig,
                                                RuleExternalDatasource datasource,
                                                Map<String, Object> params, InvokeTrace trace) throws Exception {
        if (isRuleEngineDatasource(datasource)) {
            if ("ASYNC".equals(apiConfig.getRequestMode())) {
                throw new IllegalArgumentException("内部规则数据源不支持异步外数协议");
            }
            return doInvokeRuleEngine(apiConfig, datasource, params, trace);
        }
        Map<String, Object> result = "ASYNC".equals(apiConfig.getRequestMode())
                ? invokeAsync(apiConfig, datasource, params, trace)
                : invokeHttp(apiConfig, datasource, params, trace);
        if (!matchesResponseCondition(apiConfig.getSuccessCondition(), result)) {
            result.put("success", false);
            boolean retryable = hasText(apiConfig.getRetryCondition())
                    && matchesResponseCondition(apiConfig.getRetryCondition(), result);
            throw new BusinessResponseException(businessResponseMessage(result), retryable);
        }
        Map<String, Object> beforeMapping = copyResponse(result);
        Map<String, Object> mapped = applyResponseMapping(apiConfig, result);
        recordResponseMappingTrace(apiConfig, trace, beforeMapping, mapped);
        return mapped;
    }

    private Map<String, Object> invokeHttp(RuleExternalApiConfig config, RuleExternalDatasource datasource,
                                          Map<String, Object> params, InvokeTrace trace) throws Exception {
        boolean refresh = false;
        while (true) {
            RequestDeadlineContext.check();
            PreparedHttpRequest prepared = prepareHttpRequest(config, datasource, params, false, null, refresh);
            updateRequestTrace(config, trace, prepared);
            ResponseEntity<String> response;
            HttpStatusCodeException httpError = null;
            try {
                response = exchangeHttp(config, datasource, prepared, effectiveTimeout(config.getTimeoutMs()), trace);
            } catch (HttpStatusCodeException error) {
                recordHttpError(trace, error);
                httpError = error;
                response = new ResponseEntity<>(error.getResponseBodyAsString(), error.getResponseHeaders(), error.getStatusCode());
            }
            ExternalApiPayloadCapturePolicy.Capture originalResponseCapture = capturePayload(
                    config, "response", response.getBody(), null);
            trace.originalResponseBody = ExternalApiPayloadCapturePolicy.originalBody(
                    config.getPayloadCaptureConfig(), "response", response.getBody(), originalResponseCapture);
            trace.rawResponseBody = originalResponseCapture.capturedBody();
            trace.rawResponseMetadata = originalResponseCapture.metadata();
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("httpStatus", response.getStatusCodeValue());
            envelope.put("headers", headersForScript(response.getHeaders()));
            envelope.put("body", MissingValueSemantics.normalize(parseJsonOrRaw(response.getBody())));
            if (prepared.tokenCacheKey != null && shouldRefreshToken(config, envelope)) {
                if (trace.tokenRefreshAttempted || !retryAllowedForMethod(config)) {
                    throw new TokenRefreshRejectedException(httpError);
                }
                trace.tokenRefreshAttempted = true;
                refresh = true;
                continue;
            }
            if (httpError != null) throw httpError;
            Object body;
            try {
                body = executeResponseScript(config, params, envelope.get("body"), response.getBody(),
                        response.getStatusCodeValue(), response.getHeaders(), prepared.state);
            } catch (RuntimeException scriptError) {
                recordTraceStep(trace, "RESPONSE_PROCESSING", "响应脚本处理", "FAILED",
                        envelope.get("body"), Map.of("errorMessage", scriptError.getMessage()), config.getId());
                throw scriptError;
            }
            envelope.put("body", body);
            envelope.put("success", response.getStatusCode().is2xxSuccessful());
            trace.responseStatus = response.getStatusCodeValue();
            trace.responseBody = body;
            trace.processedResponseBody = prepared.state.containsKey("logResponseBody")
                    ? prepared.state.get("logResponseBody") : body;
            ExternalApiPayloadCapturePolicy.Capture processedResponseCapture = capturePayload(
                    config, "response", response.getBody(), trace.processedResponseBody);
            trace.rawResponseBody = processedResponseCapture.capturedBody();
            trace.rawResponseMetadata = processedResponseCapture.metadata();
            trace.originalResponseBody = ExternalApiPayloadCapturePolicy.originalBody(
                    config.getPayloadCaptureConfig(), "response", response.getBody(), processedResponseCapture);
            if (hasText(config.getResponseScript())
                    || prepared.state.containsKey("logResponseBody")) {
                recordTraceStep(trace, "RESPONSE_PROCESSING", "响应脚本与映射结果", "SUCCESS",
                        trace.processedResponseBody, body, config.getId());
            }
            return envelope;
        }
    }

    private Map<String, Object> invokeAsync(RuleExternalApiConfig config, RuleExternalDatasource datasource,
                                           Map<String, Object> params, InvokeTrace trace) throws Exception {
        boolean callback = "CALLBACK".equals(config.getAsyncResultMode());
        JSONObject protocol = JSON.parseObject(callback ? config.getAsyncCallbackConfig() : config.getAsyncPollConfig());
        String invocationId = callback ? callbackStore.register(protocol) : null;
        Map<String, Object> input = new LinkedHashMap<>(params);
        if (callback) input.put("callbackUrl", config.getAsyncCallbackUrl().replace("${invocationId}", invocationId));
        try {
            Map<String, Object> receipt = invokeHttp(config, datasource, input, trace);
            String taskPath = callback ? firstText(protocol.get("submissionTaskIdPath"), protocol.get("taskIdPath"))
                    : protocol.getString("taskIdPath");
            Object taskId = readPath(receipt, taskPath);
            if (taskId == null || String.valueOf(taskId).isBlank()) {
                throw new IllegalStateException("异步提交响应缺少任务号：" + taskPath);
            }
            input.put("taskId", taskId);
            input.put("submission", receipt);
            Map<String, Object> completed = callback
                    ? awaitCallback(invocationId, protocol, taskId)
                    : pollAsync(config, datasource, protocol, input, trace);
            String resultPath = firstText(config.getAsyncResultPath(), protocol.get("resultPath"));
            Object result = hasText(resultPath) ? readPath(completed, resultPath) : completed.get("body");
            if (hasText(resultPath) && result == null) throw new IllegalStateException("异步最终结果路径无值：" + resultPath);
            Map<String, Object> response = new LinkedHashMap<>(completed);
            response.put("asyncTaskId", taskId);
            response.put("asyncResultMode", config.getAsyncResultMode());
            response.put("asyncResponse", completed.get("body"));
            response.put("body", result);
            response.put("success", true);
            trace.responseStatus = ((Number) response.get("httpStatus")).intValue();
            trace.responseBody = result;
            trace.processedResponseBody = result;
            String originalResponseBody = rawPayload(completed.get("body"));
            ExternalApiPayloadCapturePolicy.Capture finalCapture = capturePayload(
                    config, "response", originalResponseBody, trace.processedResponseBody);
            trace.rawResponseBody = finalCapture.capturedBody();
            trace.rawResponseMetadata = finalCapture.metadata();
            trace.originalResponseBody = ExternalApiPayloadCapturePolicy.originalBody(
                    config.getPayloadCaptureConfig(), "response", originalResponseBody, finalCapture);
            return response;
        } finally {
            if (invocationId != null) callbackStore.close(invocationId);
        }
    }

    private Map<String, Object> pollAsync(RuleExternalApiConfig config, RuleExternalDatasource datasource,
                                         JSONObject protocol, Map<String, Object> params, InvokeTrace trace) throws Exception {
        RuleExternalApiConfig poll = JSON.parseObject(JSON.toJSONString(config), RuleExternalApiConfig.class);
        poll.setRequestMode("SYNC");
        poll.setEndpointUrl(protocol.getString("resultEndpointUrl"));
        poll.setRequestMethod(firstText(protocol.get("requestMethod"), "GET"));
        poll.setContentType(firstText(protocol.get("contentType"), config.getContentType()));
        poll.setHeaderConfig(protocol.containsKey("headerConfig") ? JSON.toJSONString(protocol.get("headerConfig")) : config.getHeaderConfig());
        poll.setQueryConfig(JSON.toJSONString(protocol.getOrDefault("queryConfig", Map.of())));
        poll.setRequestMapping(JSON.toJSONString(protocol.getOrDefault("requestMapping", Map.of())));
        poll.setBodyTemplate("{}");
        poll.setRequestScript(protocol.getString("requestScript"));
        poll.setResponseScript(protocol.getString("responseScript"));
        int attempts = ExternalApiConfigValidator.positive(protocol, "maxAttempts", 20);
        int interval = ExternalApiConfigValidator.positive(protocol, "intervalMs", 3000);
        int failures = 0;
        int retries = config.getRetryCount() == null ? 0 : Math.max(0, config.getRetryCount());
        for (int i = 0; i < attempts; i++) {
            if (i > 0) waitWithinDeadline(interval);
            Map<String, Object> result;
            try {
                result = invokeHttp(poll, datasource, params, trace);
            } catch (Exception error) {
                if (error instanceof TokenRefreshRejectedException || !retryAllowedForMethod(poll)
                        || failures++ >= retries
                        || !shouldRetry(config, error) || i + 1 == attempts) throw error;
                continue;
            }
            failures = 0;
            if (asyncCompleted(protocol, result)) return result;
        }
        throw new TimeoutException("异步轮询达到最大次数，仍未取得最终结果");
    }

    private Map<String, Object> awaitCallback(String invocationId, JSONObject protocol, Object taskId) throws Exception {
        while (true) {
            RequestDeadlineContext.check();
            Map<String, Object> result = callbackStore.result(invocationId);
            if (result != null) {
                Object callbackTaskId = readPath(result, protocol.getString("taskIdPath"));
                if (!valuesEqual(taskId, callbackTaskId)) throw new IllegalStateException("回调任务号与提交任务号不一致");
                if (asyncCompleted(protocol, result)) return result;
            }
            waitWithinDeadline(100);
        }
    }

    private boolean asyncCompleted(JSONObject protocol, Map<String, Object> response) {
        Object status = readPath(response, protocol.getString("statusPath"));
        if (status == null) throw new IllegalStateException("异步响应缺少状态字段：" + protocol.getString("statusPath"));
        if (hasText(protocol.getString("failureValue")) && valuesEqual(status, protocol.get("failureValue"))) {
            throw new IllegalStateException("异步外数任务执行失败，状态：" + status);
        }
        return valuesEqual(status, protocol.get("successValue"));
    }

    private void waitWithinDeadline(int millis) throws Exception {
        RequestDeadlineContext.check();
        try {
            Thread.sleep(Math.min(millis, RequestDeadlineContext.remainingMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
        RequestDeadlineContext.check();
    }

    private String businessResponseMessage(Map<String, Object> response) {
        Object body = response == null ? null : response.get("body");
        if (!(body instanceof Map)) {
            return "供应商业务响应未满足成功条件";
        }
        Map<?, ?> bodyMap = (Map<?, ?>) body;
        Object code = bodyMap.containsKey("response_code")
                ? bodyMap.get("response_code") : bodyMap.get("code");
        Object message = bodyMap.containsKey("message")
                ? bodyMap.get("message") : bodyMap.get("response_msg");
        StringBuilder result = new StringBuilder("供应商业务响应未满足成功条件");
        if (code != null && hasText(String.valueOf(code))) {
            result.append("，code=").append(code);
        }
        if (message != null && hasText(String.valueOf(message))) {
            result.append("，message=").append(message);
        }
        return result.toString();
    }

    private ResponseEntity<String> exchangeHttp(RuleExternalApiConfig apiConfig,
                                                RuleExternalDatasource datasource,
                                                PreparedHttpRequest prepared, int timeout,
                                                InvokeTrace trace) {
        int attemptNo = ++trace.providerAttemptNo;
        prepared.attemptNo = attemptNo;
        long start = System.currentTimeMillis();
        String clientKey = "api:" + String.valueOf(apiConfig.getId());
        ApiHttpClientRegistry.ClientSettings settings =
                ApiHttpClientRegistry.ClientSettings.from(apiConfig, timeout);
        try {
            ResponseEntity<String> response;
            try (ApiHttpClientRegistry.ClientLease lease = apiHttpClientRegistry.acquire(clientKey, settings)) {
                recordTraceStep(trace, "EXTERNAL_REQUEST", "发起外部请求", "SENT",
                        externalRequestStep(prepared), null, apiConfig.getId());
                response = lease.getRestTemplate().exchange(prepared.finalUrl, prepared.method,
                        new HttpEntity<>(prepared.requestBody, prepared.headers), String.class);
            }
            Map<String, Object> responseInput = responseStepInput(response.getStatusCodeValue());
            responseInput.put("attemptNo", attemptNo);
            recordTraceStep(trace, "EXTERNAL_RESPONSE", "外部数据响应",
                    response.getStatusCode().is2xxSuccessful() ? "SUCCESS" : "FAILED",
                    responseInput, parseJsonOrRaw(response.getBody()),
                    apiConfig.getId());
            logApiAttempt(apiConfig, datasource, prepared, trace, attemptNo,
                    response.getStatusCodeValue(), parseJsonOrRaw(response.getBody()), response.getBody(), null,
                    System.currentTimeMillis() - start);
            return response;
        } catch (RuntimeException e) {
            HttpStatusCodeException statusError = findCause(e, HttpStatusCodeException.class);
            Integer status = statusError == null ? null : statusError.getRawStatusCode();
            Object responseBody = statusError == null ? null
                    : parseJsonOrRaw(statusError.getResponseBodyAsString());
            Map<String, Object> responseInput = responseStepInput(status);
            responseInput.put("attemptNo", attemptNo);
            recordTraceStep(trace, "EXTERNAL_RESPONSE", "外部数据响应", "FAILED",
                    responseInput, statusError == null ? Map.of("errorMessage", e.getMessage()) : responseBody,
                    apiConfig.getId());
            logApiAttempt(apiConfig, datasource, prepared, trace, attemptNo,
                    status, responseBody, statusError == null ? null : statusError.getResponseBodyAsString(),
                    e, System.currentTimeMillis() - start);
            throw e;
        }
    }

    private void updateRequestTrace(RuleExternalApiConfig config, InvokeTrace trace, PreparedHttpRequest prepared) {
        trace.requestMethod = prepared.method.name();
        trace.requestUrl = requestUrlForLog(prepared);
        trace.requestHeaders = headersToLog(prepared.headers);
        trace.requestBody = prepared.requestBody;
        String originalRequestBody = rawRequestPayload(prepared.requestBody, prepared.headers.getContentType());
        trace.processedRequestBody = prepared.state != null && prepared.state.containsKey("logRequestBody")
                ? prepared.state.get("logRequestBody") : prepared.scriptBody;
        ExternalApiPayloadCapturePolicy.Capture capture = capturePayload(
                config, "request", originalRequestBody,
                trace.processedRequestBody);
        trace.rawRequestBody = capture.capturedBody();
        trace.rawRequestMetadata = capture.metadata();
        trace.originalRequestBody = ExternalApiPayloadCapturePolicy.originalBody(
                config.getPayloadCaptureConfig(), "request", originalRequestBody, capture);
        trace.tokenCacheStatus = prepared.tokenCacheStatus;
        trace.requestIssued = true;
        recordTraceStep(trace, "API_REQUEST", "API请求报文拼装", "SUCCESS",
                requestStepInput(trace), maskSensitiveForLog(trace.requestBody), config.getId());
        recordTraceStep(trace, "AUTHENTICATION", "外数鉴权（已脱敏）", "SUCCESS",
                authenticationStepInput(prepared), authenticationStepOutput(prepared), config.getId());
    }

    private void recordHttpError(InvokeTrace trace, HttpStatusCodeException error) {
        trace.responseStatus = error.getRawStatusCode();
        trace.responseBody = parseJsonOrRaw(error.getResponseBodyAsString());
        trace.rawResponseBody = error.getResponseBodyAsString();
        trace.originalResponseBody = error.getResponseBodyAsString();
    }

    private void recordTraceStep(InvokeTrace trace, String type, String label, String status,
                                 Object input, Object output, Long resourceId) {
        if (trace == null) return;
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("sequence", trace.traceSteps.size() + 1);
        step.put("type", type);
        step.put("label", label);
        step.put("status", status);
        step.put("callId", trace.callId);
        if (trace.runtimeTrace != null) step.put("traceId", trace.runtimeTrace.getTraceId());
        if (resourceId != null) step.put("resourceId", resourceId);
        if (input instanceof Map && ((Map<?, ?>) input).get("attemptNo") != null) {
            step.put("attemptNo", ((Map<?, ?>) input).get("attemptNo"));
        }
        if (input != null) step.put("input", traceSnapshot(maskSensitiveForLog(input)));
        if (output != null) step.put("output", traceSnapshot(maskSensitiveForLog(output)));
        trace.traceSteps.add(step);
        syncTraceSteps(trace);
    }

    private void syncTraceSteps(InvokeTrace trace) {
        if (trace == null || runtimeTraceService == null || trace.runtimeTrace == null) return;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("callId", trace.callId);
        details.put("traceSteps", trace.traceSteps);
        runtimeTraceService.updateModuleDetails(trace.runtimeTrace, details);
    }

    private Object traceSnapshot(Object value) {
        if (value == null) return null;
        String serialized;
        try {
            serialized = JSON.toJSONString(value);
        } catch (RuntimeException error) {
            serialized = String.valueOf(value);
        }
        if (serialized.getBytes(StandardCharsets.UTF_8).length <= 32768) return value;
        Map<String, Object> omitted = new LinkedHashMap<>();
        omitted.put("truncated", true);
        omitted.put("bytes", serialized.getBytes(StandardCharsets.UTF_8).length);
        omitted.put("reason", "阶段追踪字段超过32KiB，仅保留摘要");
        return omitted;
    }

    private Map<String, Object> requestStepInput(InvokeTrace trace) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("rootRequestBody", trace == null ? null : trace.rootInput);
        input.put("params", trace == null ? null : trace.requestParams);
        input.put("cacheStatus", trace == null ? null : trace.cacheStatus);
        return input;
    }

    private Map<String, Object> externalRequestStep(PreparedHttpRequest prepared) {
        Map<String, Object> request = new LinkedHashMap<>();
        if (prepared == null) return request;
        request.put("method", prepared.method == null ? null : prepared.method.name());
        request.put("url", requestUrlForLog(prepared));
        request.put("headers", headersToLog(prepared.headers));
        request.put("body", prepared.requestBody);
        request.put("attemptNo", prepared.attemptNo);
        return request;
    }

    private Map<String, Object> responseStepInput(Integer status) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("httpStatus", status);
        return input;
    }

    private void recordResponseMappingTrace(RuleExternalApiConfig config, InvokeTrace trace,
                                            Map<String, Object> before, Map<String, Object> mapped) {
        if (config == null || trace == null || parseJsonMap(config.getResponseMapping()).isEmpty()) return;
        Object input = before == null ? null : before.get("body");
        Object output = mapped == null ? null : mapped.get("body");
        if (input instanceof Map) input = JSON.parseObject(JSON.toJSONString(input), LinkedHashMap.class);
        if (output instanceof Map) output = JSON.parseObject(JSON.toJSONString(output), LinkedHashMap.class);
        recordTraceStep(trace, "RESPONSE_MAPPING", "响应字段映射", "SUCCESS", input, output,
                config.getId());
    }

    private Map<String, Object> authenticationStepInput(PreparedHttpRequest prepared) {
        Map<String, Object> input = new LinkedHashMap<>();
        if (prepared == null) return input;
        input.put("authMode", prepared.authMode);
        input.put("tokenCacheKey", prepared.tokenCacheKey);
        input.put("tokenCacheStatus", prepared.tokenCacheStatus);
        return input;
    }

    private Map<String, Object> authenticationStepOutput(PreparedHttpRequest prepared) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (prepared == null) return output;
        output.put("headers", headersToLog(prepared.headers));
        output.put("query", prepared.query == null ? Map.of() : maskSensitiveForLog(prepared.query));
        output.put("tokenApplied", prepared.tokenCacheKey != null || prepared.authMode != null
                && ("BASIC".equals(prepared.authMode) || "BEARER".equals(prepared.authMode)
                || "API_KEY".equals(prepared.authMode) || "TOKEN_API".equals(prepared.authMode)
                || "OAUTH2".equals(prepared.authMode)));
        return output;
    }

    private boolean shouldRefreshToken(RuleExternalApiConfig apiConfig, Map<String, Object> response) {
        if (Integer.valueOf(0).equals(apiConfig.getTokenRefreshOnUnauthorized())) return false;
        if (hasText(apiConfig.getTokenFailureCondition())) {
            return matchesResponseCondition(apiConfig.getTokenFailureCondition(), response);
        }
        return Integer.valueOf(401).equals(response.get("httpStatus"))
                || Integer.valueOf(403).equals(response.get("httpStatus"));
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
        Map<String, Object> ruleParams = mapped == null ? params
                : parseNestedMap(resolveRequestMappingObject(mapped, params, requestNullPolicy(config)));
        Map<String, Object> state = new LinkedHashMap<>();
        ruleParams = requireScriptBodyMap(executeRequestScript(apiConfig, params, ruleParams,
                new LinkedHashMap<>(), new LinkedHashMap<>(), state, "",
                "rule-engine://local/" + ruleCode, "POST"));
        trace.requestMethod = "POST";
        trace.requestUrl = "rule-engine://local/" + ruleCode;
        trace.requestBody = ruleParams;
        trace.originalRequestBody = rawPayload(ruleParams);
        trace.processedRequestBody = ruleParams;
        ExternalApiPayloadCapturePolicy.Capture requestCapture = capturePayload(
                apiConfig, "request", rawPayload(ruleParams), trace.processedRequestBody);
        trace.originalRequestBody = ExternalApiPayloadCapturePolicy.originalBody(
                apiConfig.getPayloadCaptureConfig(), "request", rawPayload(ruleParams), requestCapture);
        trace.rawRequestBody = requestCapture.capturedBody();
        trace.rawRequestMetadata = requestCapture.metadata();
        trace.requestIssued = true;
        RuleResult result = ruleExecuteService.executePublished(published, ruleParams, datasource.getProjectId(), "RULE_ENGINE_DATASOURCE");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.isSuccess());
        Object responseBody = executeResponseScript(apiConfig, params, result.getResult(),
                JSON.toJSONString(result.getResult()), result.isSuccess() ? 200 : 500, new HttpHeaders(), state);
        response.put("body", responseBody);
        response.put("errorMessage", result.getErrorMessage());
        response.put("executeTimeMs", result.getExecuteTimeMs());
        response.put("traces", result.getTraces());
        trace.responseStatus = result.isSuccess() ? 200 : 500;
        trace.responseBody = responseBody;
        String originalResponseBody = rawPayload(responseBody);
        trace.processedResponseBody = responseBody;
        ExternalApiPayloadCapturePolicy.Capture responseCapture = capturePayload(
                apiConfig, "response", rawPayload(responseBody), trace.processedResponseBody);
        trace.originalResponseBody = ExternalApiPayloadCapturePolicy.originalBody(
                apiConfig.getPayloadCaptureConfig(), "response", originalResponseBody, responseCapture);
        trace.rawResponseBody = responseCapture.capturedBody();
        trace.rawResponseMetadata = responseCapture.metadata();
            return response;
    }

    private PreparedHttpRequest prepareHttpRequest(RuleExternalApiConfig apiConfig,
                                                   RuleExternalDatasource datasource,
                                                   Map<String, Object> params,
                                                   boolean preview,
                                                   String previewToken) {
        return prepareHttpRequest(apiConfig, datasource, params, preview, previewToken, false);
    }

    private PreparedHttpRequest prepareHttpRequest(RuleExternalApiConfig apiConfig,
                                                   RuleExternalDatasource datasource,
                                                   Map<String, Object> params,
                                                   boolean preview,
                                                   String previewToken,
                                                   boolean forceTokenRefresh) {
        String url = resolveRequestUrl(datasource, apiConfig, params);
        HttpHeaders headers = new HttpHeaders();
        if (hasText(apiConfig.getContentType())) {
            headers.setContentType(MediaType.parseMediaType(apiConfig.getContentType()));
        }
        RequestNullPolicy nullPolicy = requestNullPolicy(parseJsonMap(apiConfig.getRequestMapping()));
        NonJsonNullPolicy nonJsonPolicy = nonJsonNullPolicy(parseJsonMap(apiConfig.getRequestMapping()));
        applyJsonHeaders(headers, apiConfig.getHeaderConfig(), params, nullPolicy, nonJsonPolicy);
        UriComponentsBuilder initialBuilder = httpUrlBuilder(url);
        applyQueryParams(initialBuilder, apiConfig.getQueryConfig(), params, nullPolicy, nonJsonPolicy);
        AppliedAuth appliedAuth = applyAuth(initialBuilder, headers, datasource, apiConfig, params,
                preview, previewToken, forceTokenRefresh, nonJsonPolicy);
        String token = appliedAuth.token;

        Map<String, Object> scriptHeaders = headersForScript(headers);
        Map<String, Object> scriptQuery = queryForScript(initialBuilder);
        Map<String, Object> state = new LinkedHashMap<>();
        Object scriptBody = executeRequestScript(apiConfig, params, buildBody(apiConfig, params),
                scriptHeaders, scriptQuery, state, token, url, firstText(apiConfig.getRequestMethod(), "POST"));
        replaceHeaders(headers, scriptHeaders);
        UriComponentsBuilder finalBuilder = httpUrlBuilder(url);
        applyPreparedQuery(finalBuilder, scriptQuery);

        PreparedHttpRequest prepared = new PreparedHttpRequest();
        prepared.baseUrl = url;
        prepared.finalUrl = finalBuilder.build(false).encode(StandardCharsets.UTF_8).toUriString();
        prepared.headers = headers;
        prepared.query = scriptQuery;
        prepared.scriptBody = scriptBody;
        prepared.state = state;
        prepared.requestBody = buildHttpRequestBody(scriptBody, headers.getContentType());
        prepared.tokenCacheKey = appliedAuth.tokenCacheKey;
        prepared.tokenCacheStatus = appliedAuth.tokenCacheStatus;
        prepared.authMode = resolveAuthMode(apiConfig, datasource);
        prepared.method = resolveHttpMethod(apiConfig.getRequestMethod());
        if (prepared.method == null) prepared.method = HttpMethod.POST;
        return prepared;
    }

    private String resolveAuthMode(RuleExternalApiConfig apiConfig, RuleExternalDatasource datasource) {
        String mode = firstText(apiConfig == null ? null : apiConfig.getAuthMode(), "INHERIT")
                .toUpperCase(Locale.ROOT);
        if ("INHERIT".equals(mode)) {
            mode = firstText(datasource == null ? null : datasource.getAuthType(), "NONE")
                    .toUpperCase(Locale.ROOT);
        }
        return mode;
    }

    private HttpMethod resolveHttpMethod(String methodText) {
        if (methodText == null) {
            return null;
        }
        for (HttpMethod method : HttpMethod.values()) {
            if (method.matches(methodText)) {
                return method;
            }
        }
        return null;
    }

    private UriComponentsBuilder httpUrlBuilder(String url) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        UriComponents components = builder.build();
        String scheme = components.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || !hasText(components.getHost())) {
            throw new IllegalArgumentException("Invalid HTTP URL: " + url);
        }
        return builder;
    }

    private Object executeRequestScript(RuleExternalApiConfig apiConfig, Map<String, Object> input,
                                        Object body, Map<String, Object> headers,
                                        Map<String, Object> query, Map<String, Object> state, String token,
                                        String endpoint, String method) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("input", input);
        context.put("body", body);
        context.put("headers", headers);
        context.put("query", query);
        context.put("state", state);
        context.put("vars", externalApiScriptService.parseScriptVariables(apiConfig.getAuthApiConfig()));
        context.put("token", token);
        context.put("endpoint", endpoint);
        context.put("method", method);
        context.put("nowMillis", System.currentTimeMillis());
        context.put("requestId", resolveRequestId(input));
        return externalApiScriptService.executeRequest(apiConfig.getRequestScript(), context);
    }

    private Object executeResponseScript(RuleExternalApiConfig apiConfig, Map<String, Object> input,
                                         Object body, String rawBody, int httpStatus,
                                         HttpHeaders responseHeaders, Map<String, Object> state) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("input", input);
        context.put("body", body);
        context.put("rawBody", rawBody);
        context.put("httpStatus", httpStatus);
        context.put("headers", headersForScript(responseHeaders));
        context.put("state", state == null ? new LinkedHashMap<>() : state);
        context.put("vars", externalApiScriptService.parseScriptVariables(apiConfig.getAuthApiConfig()));
        return externalApiScriptService.executeResponse(apiConfig.getResponseScript(), context);
    }

    private String resolveRequestId(Map<String, Object> input) {
        String value = firstText(input == null ? null : input.get("request_id"),
                input == null ? null : input.get("requestId"));
        return hasText(value) ? value : UUID.randomUUID().toString().replace("-", "");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireScriptBodyMap(Object body) {
        if (!(body instanceof Map)) {
            throw new IllegalArgumentException("内部规则请求脚本必须返回对象");
        }
        return (Map<String, Object>) body;
    }

    private Map<String, Object> headersForScript(HttpHeaders headers) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (headers == null) return result;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
            result.put(entry.getKey(), entry.getValue().size() == 1
                    ? entry.getValue().get(0) : new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    private void replaceHeaders(HttpHeaders headers, Map<String, Object> values) {
        headers.clear();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue() instanceof Iterable) {
                for (Object item : (Iterable<?>) entry.getValue()) {
                    if (item == null) throw new IllegalArgumentException("Header[" + entry.getKey() + "]为 null");
                    headers.add(entry.getKey(), String.valueOf(item));
                }
            } else {
                if (entry.getValue() == null) throw new IllegalArgumentException("Header[" + entry.getKey() + "]为 null");
                headers.set(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
    }

    private Map<String, Object> queryForScript(UriComponentsBuilder builder) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : builder.build(false).getQueryParams().entrySet()) {
            result.put(entry.getKey(), entry.getValue().size() == 1
                    ? entry.getValue().get(0) : new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    private void applyPreparedQuery(UriComponentsBuilder builder, Map<String, Object> query) {
        for (Map.Entry<String, Object> entry : query.entrySet()) {
            if (entry.getValue() instanceof Iterable) {
                for (Object item : (Iterable<?>) entry.getValue()) {
                    if (item == null) throw new IllegalArgumentException("Query[" + entry.getKey() + "]为 null");
                    builder.queryParam(entry.getKey(), item);
                }
            } else {
                if (entry.getValue() == null) throw new IllegalArgumentException("Query[" + entry.getKey() + "]为 null");
                builder.queryParam(entry.getKey(), entry.getValue());
            }
        }
    }

    private String requestUrlForLog(PreparedHttpRequest prepared) {
        UriComponentsBuilder builder = httpUrlBuilder(prepared.baseUrl);
        Map<String, Object> query = prepared.query == null ? new LinkedHashMap<>() : prepared.query;
        for (Map.Entry<String, Object> entry : query.entrySet()) {
            Object value = isSensitiveKey(entry.getKey()) ? "******" : entry.getValue();
            if (value instanceof Iterable) {
                for (Object item : (Iterable<?>) value) builder.queryParam(entry.getKey(), item);
            } else if (value != null) {
                builder.queryParam(entry.getKey(), value);
            }
        }
        return builder.build(false).encode(StandardCharsets.UTF_8).toUriString();
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

    private void applyJsonHeaders(HttpHeaders headers, String headerConfig, Map<String, Object> params,
                                  RequestNullPolicy policy) {
        applyJsonHeaders(headers, headerConfig, params, policy, NonJsonNullPolicy.DEFAULT);
    }

    private void applyJsonHeaders(HttpHeaders headers, String headerConfig, Map<String, Object> params,
                                  RequestNullPolicy policy, NonJsonNullPolicy nonJsonPolicy) {
        Map<String, Object> map = parseJsonMap(headerConfig);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            ResolvedRequestValue resolved = resolveRequestValue(entry.getValue(), params, policy);
            if (!resolved.present || resolved.value == null) {
                String value = nonJsonValue(resolved, entry.getKey(), "Header", nonJsonPolicy.header);
                if (value == null) continue;
                headers.set(entry.getKey(), value);
                continue;
            }
            headers.set(entry.getKey(), String.valueOf(resolved.value));
        }
    }

    private void applyQueryParams(UriComponentsBuilder builder, String queryConfig, Map<String, Object> params,
                                  RequestNullPolicy policy) {
        applyQueryParams(builder, queryConfig, params, policy, NonJsonNullPolicy.DEFAULT);
    }

    private void applyQueryParams(UriComponentsBuilder builder, String queryConfig, Map<String, Object> params,
                                  RequestNullPolicy policy, NonJsonNullPolicy nonJsonPolicy) {
        Map<String, Object> map = parseJsonMap(queryConfig);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            ResolvedRequestValue resolved = resolveRequestValue(entry.getValue(), params, policy);
            if (resolved.present && resolved.value == null
                    && nonJsonPolicy.query == NonJsonAction.PRESERVE_NULL) {
                builder.queryParam(entry.getKey(), (Object) null);
                continue;
            }
            if (!resolved.present || resolved.value == null) {
                String value = nonJsonValue(resolved, entry.getKey(), "Query", nonJsonPolicy.query);
                if (value == null) continue;
                builder.queryParam(entry.getKey(), value);
                continue;
            }
            builder.queryParam(entry.getKey(), resolved.value);
        }
    }

    private AppliedAuth applyAuth(UriComponentsBuilder builder, HttpHeaders headers,
                                  RuleExternalDatasource datasource, RuleExternalApiConfig apiConfig,
                                  Map<String, Object> params, boolean preview, String previewToken,
                                  boolean forceTokenRefresh, NonJsonNullPolicy nonJsonPolicy) {
        String authMode = firstText(apiConfig.getAuthMode(), "INHERIT")
                .toUpperCase(Locale.ROOT);
        String authConfig = apiConfig.getAuthApiConfig();
        if ("INHERIT".equals(authMode)) {
            authMode = firstText(datasource.getAuthType(), "NONE")
                    .toUpperCase(Locale.ROOT);
            authConfig = datasource.getAuthConfig();
        }
        if (authMode == null || "NONE".equals(authMode)) {
            return new AppliedAuth("", null, null);
        }
        Map<String, Object> config = parseJsonMap(authConfig);
        if ("BASIC".equals(authMode)) {
            String username = authValue(resolveValue(config.get("username"), params), "Basic username", nonJsonPolicy.auth);
            String password = authValue(resolveValue(config.get("password"), params), "Basic password", nonJsonPolicy.auth);
            if (username == null || password == null) return new AppliedAuth("", null, null);
            String raw = username + ":" + password;
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        } else if ("BEARER".equals(authMode)) {
            String token = authValue(resolveValue(config.get("token"), params), "Bearer token", nonJsonPolicy.auth);
            if (token == null) return new AppliedAuth("", null, null);
            headers.setBearerAuth(token);
        } else if ("API_KEY".equals(authMode)) {
            String name = authValue(config.get("name"), "API Key name", nonJsonPolicy.auth);
            String value = authValue(resolveValue(config.get("value"), params), "API Key value", nonJsonPolicy.auth);
            if (name == null || value == null) return new AppliedAuth("", null, null);
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
            String cacheKey = buildTokenCacheKey(datasource, apiConfig, authMode, tokenUrl, config, params);
            ExternalTokenCache.TokenResult tokenResult = preview ? null
                    : requestToken(datasource, config, params, tokenUrl, cacheKey, cacheSeconds,
                    effectiveTimeout(apiConfig.getTimeoutMs()), forceTokenRefresh, apiConfig);
            String token = preview ? firstText(previewToken, "") : tokenResult.getToken();
            if (token != null && !token.isEmpty() && shouldWriteTokenHeader(config)) {
                applyTokenHeader(headers, config, token);
            }
            return new AppliedAuth(token, preview ? null : cacheKey,
                    tokenResult == null ? null : tokenResult.getCacheStatus());
        }
        return new AppliedAuth("", null, null);
    }

    private boolean shouldWriteTokenHeader(Map<String, Object> config) {
        String placement = firstText(config.get("tokenPlacement"), "HEADER").trim().toUpperCase();
        if ("HEADER".equals(placement)) {
            return true;
        }
        if ("SCRIPT_ONLY".equals(placement)) {
            return false;
        }
        throw new IllegalArgumentException("Token 放置方式仅支持 HEADER 或 SCRIPT_ONLY");
    }

    private void applyTokenHeader(HttpHeaders headers, Map<String, Object> config, String token) {
        String headerName = config.containsKey("tokenHeaderName")
                ? stringValue(config.get("tokenHeaderName")).trim()
                : HttpHeaders.AUTHORIZATION;
        if (headerName.isEmpty()) {
            throw new IllegalArgumentException("Token Header名称不能为空");
        }
        String prefix = config.containsKey("tokenPrefix") ? stringValue(config.get("tokenPrefix")) : "";
        headers.set(headerName, prefix + token);
    }

    private ExternalTokenCache.TokenResult requestToken(RuleExternalDatasource datasource,
                                                        Map<String, Object> config,
                                                        Map<String, Object> params, String tokenUrl,
                                                        String cacheKey, int cacheSeconds, int timeoutMs,
                                                        boolean forceRefresh,
                                                        RuleExternalApiConfig apiConfig) {
        if (tokenUrl.isEmpty()) {
            throw new IllegalArgumentException("Token接口地址不能为空");
        }
        if (forceRefresh) {
            logTokenCacheEvent(datasource, apiConfig, "TOKEN_INVALIDATE", cacheKey,
                    "INVALIDATED", true);
        }
        ExternalTokenCache.TokenResult result = externalTokenCache.getOrLoadResult(cacheKey, forceRefresh,
                () -> requestTokenFromProvider(datasource, config, params, tokenUrl, cacheKey,
                        cacheSeconds, timeoutMs, forceRefresh, apiConfig));
        if (result.getCacheStatus().endsWith("HIT") || "COALESCED".equals(result.getCacheStatus())) {
            logTokenCacheEvent(datasource, apiConfig, "TOKEN_CACHE_HIT", cacheKey,
                    result.getCacheStatus(), true);
        }
        return result;
    }

    private String buildTokenCacheKey(RuleExternalDatasource datasource, RuleExternalApiConfig apiConfig,
                                      String authMode, String tokenUrl, Map<String, Object> config,
                                      Map<String, Object> params) {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("datasourceId", datasource.getId());
        identity.put("apiConfigId", apiConfig.getId());
        identity.put("authMode", authMode);
        identity.put("tokenUrl", tokenUrl);
        identity.put("method", firstText(config.get("method"), "POST").toUpperCase());
        identity.put("username", resolveValue(config.get("username"), params));
        Map<String, Object> resolvedHeaders = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : parseNestedMap(config.get("headers")).entrySet()) {
            resolvedHeaders.put(entry.getKey(), resolveValue(entry.getValue(), params));
        }
        identity.put("headers", resolvedHeaders);
        identity.put("body", buildTokenRequestBody(config.get("body"), params, resolveTokenContentType(config)));
        identity.put("authConfigDigest", sha256(JSON.toJSONString(config)));
        return sha256(JSON.toJSONString(identity));
    }

    private ExternalTokenCache.CachedToken requestTokenFromProvider(
            RuleExternalDatasource datasource, Map<String, Object> config, Map<String, Object> params,
            String tokenUrl, String cacheKey, int cacheSeconds, int timeoutMs,
            boolean forceRefresh, RuleExternalApiConfig apiConfig) {
        long start = System.currentTimeMillis();
        HttpHeaders headers = new HttpHeaders();
        NonJsonNullPolicy nonJsonPolicy = nonJsonNullPolicy(parseJsonMap(apiConfig.getRequestMapping()));
        MediaType contentType = resolveTokenContentType(config);
        headers.setContentType(contentType);
        Map<String, Object> tokenHeaders = parseNestedMap(config.get("headers"));
        for (Map.Entry<String, Object> entry : tokenHeaders.entrySet()) {
            Object headerValue = resolveValue(entry.getValue(), params);
            String value = authValue(headerValue, "Token Header[" + entry.getKey() + "]", nonJsonPolicy.auth);
            if (value != null) headers.set(entry.getKey(), value);
        }
        Object body = buildTokenRequestBody(config.get("body"), params, contentType);
        String methodText = stringValue(config.get("method"));
        HttpMethod method = methodText.isEmpty() ? HttpMethod.POST : resolveHttpMethod(methodText);
        if (method == null) {
            method = HttpMethod.POST;
        }
        ResponseEntity<String> response = null;
        try {
            ApiHttpClientRegistry.ClientSettings settings =
                    ApiHttpClientRegistry.ClientSettings.from(apiConfig, timeoutMs);
            try (ApiHttpClientRegistry.ClientLease lease = apiHttpClientRegistry.acquire(
                    "api:" + String.valueOf(apiConfig.getId()), settings)) {
                response = lease.getRestTemplate().exchange(tokenUrl, method,
                        new HttpEntity<>(body, headers), String.class);
            }
            Object parsed = parseJsonOrRaw(response.getBody());
            parsed = executeTokenResponseScript(config, params, response, parsed);
            Object token = readToken(response, parsed, stringValue(config.get("tokenPath")));
            String tokenText = token == null ? "" : stripBearerPrefix(String.valueOf(token));
            if (!hasText(tokenText)) {
                throw new IllegalStateException("Token接口返回了空Token");
            }
            int ttlSeconds = resolveTokenTtlSeconds(config, response, parsed, cacheSeconds);
            long now = System.currentTimeMillis();
            long expiresAt = ttlSeconds > 0 ? now + ttlSeconds * 1000L : now;
            int configuredAhead = apiConfig.getTokenRefreshAheadSeconds() == null
                    ? 60 : Math.max(0, apiConfig.getTokenRefreshAheadSeconds());
            int refreshAheadSeconds = ttlSeconds <= 0 ? 0 : Math.min(configuredAhead, ttlSeconds / 2);
            long usableExpiresAt = expiresAt - refreshAheadSeconds * 1000L;
            logTokenCall(datasource, apiConfig, forceRefresh, cacheKey, method, tokenUrl,
                    headers, body, response.getStatusCodeValue(), true, ttlSeconds, null,
                    response.getBody(),
                    System.currentTimeMillis() - start);
            return new ExternalTokenCache.CachedToken(tokenText, expiresAt, usableExpiresAt);
        } catch (RuntimeException e) {
            Integer status = response == null ? null : response.getStatusCodeValue();
            HttpStatusCodeException statusError = findCause(e, HttpStatusCodeException.class);
            if (statusError != null) status = statusError.getRawStatusCode();
            logTokenCall(datasource, apiConfig, forceRefresh, cacheKey, method, tokenUrl,
                    headers, body, status, false, null, e.getMessage(),
                    statusError == null ? null : statusError.getResponseBodyAsString(),
                    System.currentTimeMillis() - start);
            throw e;
        }
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
            Object headerValue = resolveValue(entry.getValue(), params);
            String value = authValue(headerValue, "Token Header[" + entry.getKey() + "]", NonJsonNullPolicy.DEFAULT.auth);
            if (value != null) headers.set(entry.getKey(), value);
        }
        Object body = buildTokenRequestBody(config.get("body"), params, contentType);
        String methodText = stringValue(config.get("method"));
        HttpMethod method = methodText.isEmpty() ? HttpMethod.POST : resolveHttpMethod(methodText);
        if (method == null) {
            method = HttpMethod.POST;
        }
        trace.requestMethod = method.name();
        trace.requestUrl = tokenUrl;
        trace.requestHeaders = headersToLog(headers);
        trace.requestBody = body;
        trace.originalRequestBody = rawPayload(body);
        int timeout = effectiveTimeout(3000);
        ResponseEntity<String> response;
        try (ApiHttpClientRegistry.ClientLease lease = apiHttpClientRegistry.acquire(
                "token-test:" + String.valueOf(datasource.getId()), timeout, timeout)) {
            response = lease.getRestTemplate().exchange(tokenUrl, method,
                    new HttpEntity<>(body, headers), String.class);
        }
        Object parsed = parseJsonOrRaw(response.getBody());
        parsed = executeTokenResponseScript(config, params, response, parsed);
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
        trace.originalResponseBody = response.getBody();
        return result;
    }

    private Object executeTokenResponseScript(Map<String, Object> config, Map<String, Object> params,
                                              ResponseEntity<String> response, Object parsed) {
        String script = stringValue(config.get("tokenResponseScript"));
        if (!hasText(script)) return parsed;
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("input", params);
        context.put("body", parsed);
        context.put("rawBody", response.getBody());
        context.put("httpStatus", response.getStatusCodeValue());
        context.put("headers", headersForScript(response.getHeaders()));
        context.put("vars", externalApiScriptService.parseScriptVariables(JSON.toJSONString(config)));
        return externalApiScriptService.executeResponse(script, context);
    }

    private int effectiveTimeout(Integer configuredTimeoutMs) {
        int configured = configuredTimeoutMs == null ? 3000 : Math.max(1, configuredTimeoutMs);
        int remaining = RequestDeadlineContext.remainingMillis();
        if (remaining == 0) {
            throw new IllegalStateException("项目请求总超时", new TimeoutException("项目请求总超时"));
        }
        return Math.min(configured, remaining);
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
                if (entry.getValue() == null) throw new IllegalArgumentException("Token Form[" + entry.getKey() + "]为 null");
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
            // 表单协议没有 JSON null；删除空字段不会改变请求语义，避免把 null 编码成字符串。
            if (entry.getValue() != null) form.add(entry.getKey(), String.valueOf(entry.getValue()));
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
        if (!mapping.isEmpty() && mapping.keySet().stream().anyMatch(key -> !key.startsWith("_"))) {
            return resolveRequestMappingObject(mapping, params, requestNullPolicy(mapping));
        }
        if (apiConfig.getBodyTemplate() != null && !apiConfig.getBodyTemplate().trim().isEmpty()) {
            Object template = parseRequestTemplate(apiConfig.getBodyTemplate(), apiConfig.getContentType());
            RequestNullPolicy policy = requestNullPolicy(mapping);
            if (template instanceof Map || template instanceof List) {
                return resolveTemplateObject(template, params, policy);
            }
            if (template == null) return null;
            // 非 JSON 模板没有字段边界可以安全删除；缺失占位符直接报错，避免静默发出错误请求。
            return template instanceof String ? resolveTemplate((String) template, params) : template;
        }
        return params;
    }

    /** 先解析 JSON 结构，再按字段替换；同时兼容历史模板中未加引号的 ${path}。 */
    private Object parseRequestTemplate(String template, String contentType) {
        StringBuilder json = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < template.length(); i++) {
            char ch = template.charAt(i);
            if (quote != 0) {
                json.append(ch);
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == quote) quote = 0;
            } else if (ch == '"' || ch == '\'') {
                quote = ch;
                json.append(ch);
            } else if (ch == '$' && i + 1 < template.length() && template.charAt(i + 1) == '{') {
                int end = template.indexOf('}', i + 2);
                if (end < 0) throw new IllegalArgumentException("请求模板占位符未闭合");
                json.append(JSON.toJSONString(template.substring(i, end + 1)));
                i = end;
            } else {
                json.append(ch);
            }
        }
        try {
            return JSON.parse(json.toString());
        } catch (RuntimeException error) {
            if (contentType == null || contentType.toLowerCase(Locale.ROOT).contains("json")) {
                throw new IllegalArgumentException("请求体模板不是有效 JSON", error);
            }
            return template;
        }
    }

    private Object resolveTemplateObject(Object value, Map<String, Object> params,
                                         RequestNullPolicy policy) {
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : parseNestedMap(value).entrySet()) {
                ResolvedRequestValue resolved = resolveTemplateValue(entry.getValue(), params, policy);
                if (!resolved.present && policy.omitMissing) continue;
                if (resolved.present && resolved.value == null && policy.omitExplicitNull) continue;
                result.put(entry.getKey(), resolved.value);
            }
            return result;
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                ResolvedRequestValue resolved = resolveTemplateValue(item, params, policy);
                result.add(resolved.present ? resolved.value : null);
            }
            return result;
        }
        ResolvedRequestValue resolved = resolveTemplateValue(value, params, policy);
        return resolved.present ? resolved.value : null;
    }

    private ResolvedRequestValue resolveTemplateValue(Object value, Map<String, Object> params,
                                                       RequestNullPolicy policy) {
        if (value instanceof Map || value instanceof List) {
            return ResolvedRequestValue.present(resolveTemplateObject(value, params, policy));
        }
        if (!(value instanceof String text) || !text.contains("${")) {
            return ResolvedRequestValue.present(MissingValueSemantics.normalize(value));
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")
                && trimmed.indexOf("${", 2) < 0) {
            String path = trimmed.substring(2, trimmed.length() - 1).trim();
            if (!containsPath(params, path)) return ResolvedRequestValue.missing();
            return ResolvedRequestValue.present(MissingValueSemantics.normalize(readPath(params, path)));
        }
        for (int start = text.indexOf("${"); start >= 0; start = text.indexOf("${", start + 2)) {
            int end = text.indexOf('}', start + 2);
            if (end < 0) throw new IllegalArgumentException("请求模板占位符未闭合");
            String path = text.substring(start + 2, end).trim();
            if (!containsPath(params, path)) return ResolvedRequestValue.missing();
            if (readPath(params, path) == null) return ResolvedRequestValue.present(null);
        }
        return ResolvedRequestValue.present(resolveTemplate(text, params));
    }

    private Object resolveRequestMappingObject(Object value, Map<String, Object> params) {
        return resolveRequestMappingObject(value, params, RequestNullPolicy.DEFAULT);
    }

    private Object resolveRequestMappingObject(Object value, Map<String, Object> params,
                                               RequestNullPolicy policy) {
        if (value instanceof Map) {
            Map<String, Object> source = parseNestedMap(value);
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (entry.getKey().startsWith("_")) continue;
                ResolvedRequestValue resolved = resolveRequestValue(entry.getValue(), params, policy);
                if (!resolved.present && policy.omitMissing) continue;
                if (resolved.present && resolved.value == null && policy.omitExplicitNull) continue;
                putMappedRequestValue(result, normalizeOutputFieldName(entry.getKey()),
                        resolved.value);
            }
            return result;
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                ResolvedRequestValue resolved = resolveRequestValue(item, params, policy);
                result.add(resolved.present ? resolved.value : null);
            }
            return result;
        }
        ResolvedRequestValue resolved = resolveRequestValue(value, params, policy);
        return resolved.present ? resolved.value : null;
    }

    private RequestNullPolicy requestNullPolicy(Map<String, Object> mapping) {
        Object configured = mapping == null ? null : mapping.get("_nullPolicy");
        if (configured == null && mapping != null) configured = mapping.get("nullValuePolicy");
        String value = configured == null ? "" : String.valueOf(configured).trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "SEND_ALL", "SEND_MISSING_SEND_NULL" -> new RequestNullPolicy(false, false);
            case "OMIT_ALL", "OMIT_MISSING_OMIT_NULL" -> new RequestNullPolicy(true, true);
            default -> RequestNullPolicy.DEFAULT;
        };
    }

    private NonJsonNullPolicy nonJsonNullPolicy(Map<String, Object> mapping) {
        Object configured = mapping == null ? null : mapping.get("_nonJsonNullPolicy");
        if (!(configured instanceof Map<?, ?> values)) return NonJsonNullPolicy.DEFAULT;
        return new NonJsonNullPolicy(
                NonJsonAction.parse(values.get("header"), NonJsonAction.OMIT),
                NonJsonAction.parse(values.get("query"), NonJsonAction.PRESERVE_NULL),
                NonJsonAction.parse(values.get("auth"), NonJsonAction.EMPTY));
    }

    private String nonJsonValue(ResolvedRequestValue resolved, String name, String location,
                                NonJsonAction action) {
        if (action == NonJsonAction.OMIT) return null;
        if (action == NonJsonAction.PRESERVE_NULL) return null;
        if (action == NonJsonAction.EMPTY) return "";
        throw new IllegalArgumentException(location + "[" + name + "]为缺失值；请在请求配置中选择省略、空字符串或报错策略");
    }

    private String authValue(Object value, String label, NonJsonAction action) {
        if (value != null) return String.valueOf(value);
        if (action == NonJsonAction.OMIT) return null;
        if (action == NonJsonAction.PRESERVE_NULL) return null;
        if (action == NonJsonAction.EMPTY) return "";
        throw new IllegalArgumentException(label + " 为空；请在请求配置中选择省略、空字符串或报错策略");
    }

    private ResolvedRequestValue resolveRequestValue(Object value, Map<String, Object> params) {
        return resolveRequestValue(value, params, RequestNullPolicy.DEFAULT);
    }

    private ResolvedRequestValue resolveRequestValue(Object value, Map<String, Object> params,
                                                     RequestNullPolicy policy) {
        if (value instanceof String text && text.startsWith("$.")) {
            String path = text.substring(2);
            if (!containsPath(params, path)) return ResolvedRequestValue.missing();
            return ResolvedRequestValue.present(MissingValueSemantics.normalize(readPath(params, path)));
        }
        if (value instanceof String text && text.trim().startsWith("${")
                && text.trim().endsWith("}") && text.trim().indexOf("${", 2) < 0) {
            String path = text.trim().substring(2, text.trim().length() - 1).trim();
            if (!containsPath(params, path)) return ResolvedRequestValue.missing();
            return ResolvedRequestValue.present(MissingValueSemantics.normalize(readPath(params, path)));
        }
        if (value instanceof Map || value instanceof List) {
            return ResolvedRequestValue.present(resolveRequestMappingObject(value, params, policy));
        }
        return ResolvedRequestValue.present(MissingValueSemantics.normalize(resolveValue(value, params)));
    }

    private boolean containsPath(Object root, String path) {
        if (root == null || path == null || path.trim().isEmpty()) return false;
        Object current = root;
        for (String part : path.split("\\.")) {
            if (current instanceof Map<?, ?> map && map.containsKey(part)) {
                current = map.get(part);
            } else if (current instanceof List<?> list) {
                Integer index = parseIndex(part);
                if (index == null || index < 0 || index >= list.size()) return false;
                current = list.get(index);
            } else {
                return false;
            }
        }
        return true;
    }

    private static final class RequestNullPolicy {
        private static final RequestNullPolicy DEFAULT = new RequestNullPolicy(true, false);
        private final boolean omitMissing;
        private final boolean omitExplicitNull;

        private RequestNullPolicy(boolean omitMissing, boolean omitExplicitNull) {
            this.omitMissing = omitMissing;
            this.omitExplicitNull = omitExplicitNull;
        }
    }

    private enum NonJsonAction {
        OMIT, EMPTY, PRESERVE_NULL, FAIL;

        private static NonJsonAction parse(Object value, NonJsonAction fallback) {
            if (value == null) return fallback;
            try {
                return valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    private static final class NonJsonNullPolicy {
        private static final NonJsonNullPolicy DEFAULT =
                new NonJsonNullPolicy(NonJsonAction.OMIT, NonJsonAction.PRESERVE_NULL, NonJsonAction.EMPTY);
        private final NonJsonAction header;
        private final NonJsonAction query;
        private final NonJsonAction auth;

        private NonJsonNullPolicy(NonJsonAction header, NonJsonAction query, NonJsonAction auth) {
            this.header = header;
            this.query = query;
            this.auth = auth;
        }
    }

    private static final class ResolvedRequestValue {
        private final boolean present;
        private final Object value;

        private ResolvedRequestValue(boolean present, Object value) {
            this.present = present;
            this.value = value;
        }

        private static ResolvedRequestValue missing() {
            return new ResolvedRequestValue(false, null);
        }

        private static ResolvedRequestValue present(Object value) {
            return new ResolvedRequestValue(true, value);
        }
    }

    private static final class ResponseMappedValue {
        private final boolean present;
        private final Object value;

        private ResponseMappedValue(boolean present, Object value) {
            this.present = present;
            this.value = value;
        }

        private static ResponseMappedValue missing() {
            return new ResponseMappedValue(false, null);
        }

        private static ResponseMappedValue present(Object value) {
            return new ResponseMappedValue(true, value);
        }

        private static ResponseMappedValue of(PathRead value) {
            return new ResponseMappedValue(value.present, value.value);
        }
    }

    private static final class PathRead {
        private final boolean present;
        private final Object value;

        private PathRead(boolean present, Object value) {
            this.present = present;
            this.value = value;
        }
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

    private String resolveRequestUrl(RuleExternalDatasource datasource, RuleExternalApiConfig apiConfig,
                                     Map<String, Object> params) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (params != null) {
            context.putAll(params);
        }
        Map<String, Object> variables = externalApiScriptService.parseScriptVariables(apiConfig.getAuthApiConfig());
        context.putAll(variables);
        context.put("input", params == null ? new LinkedHashMap<>() : params);
        context.put("vars", variables);
        String baseUrl = resolveRequiredTemplate(datasource.getBaseUrl(), context, "基础地址");
        String endpointUrl = resolveRequiredTemplate(apiConfig.getEndpointUrl(), context, "接口地址");
        return buildUrl(baseUrl, endpointUrl);
    }

    private String resolveRequiredTemplate(String template, Map<String, Object> context, String label) {
        if (!hasText(template) || !template.contains("${")) {
            return template;
        }
        String result = template;
        int start = result.indexOf("${");
        while (start >= 0) {
            int end = result.indexOf("}", start);
            if (end < 0) {
                throw new IllegalArgumentException(label + "占位符未闭合");
            }
            String path = result.substring(start + 2, end).trim();
            Object value = readPath(context, path);
            if (value == null || String.valueOf(value).trim().isEmpty()) {
                throw new IllegalArgumentException(label + "占位变量未配置: " + path);
            }
            result = result.substring(0, start) + value + result.substring(end + 1);
            start = result.indexOf("${", start + String.valueOf(value).length());
        }
        return result;
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
        Map<String, Boolean> mappedPresence = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            ResponseMappedValue value = resolveResponseMappingValue(entry.getValue(), response, body);
            mappedPresence.put(entry.getKey(), value.present);
            if (value.present) mapped.put(entry.getKey(), value.value);
        }
        response.put("rawBody", body);
        response.put("body", mapped);
        response.put("mapped", mapped);
        // 显式 null 会出现在 body 中，未返回字段不会出现；该元数据供调用方无需猜测即可区分。
        response.put("mappedPresence", mappedPresence);
        return response;
    }

    private ResponseMappedValue resolveResponseMappingValue(Object rule, Map<String, Object> response, Object body) {
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
                        ResponseMappedValue value = resolveResponseMappingCase(caseConfig, response, body);
                        if (value.present || caseConfig.containsKey("default")) {
                            return value;
                        }
                    }
                }
                return config.containsKey("default")
                        ? ResponseMappedValue.present(config.get("default")) : ResponseMappedValue.missing();
            }
            if (config.containsKey("path") || config.containsKey("paths")) {
                return resolveResponseMappingCase(config, response, body);
            }
            return ResponseMappedValue.present(resolveValue(config, response));
        }
        return readMappedPathValue(response, body, stringValue(rule));
    }

    private ResponseMappedValue resolveResponseMappingCase(Map<String, Object> config,
                                                            Map<String, Object> response, Object body) {
        ResponseMappedValue value = ResponseMappedValue.missing();
        if (config.get("paths") instanceof List) {
            value = firstReadableValue((List<?>) config.get("paths"), response, body);
        }
        if (!value.present && config.containsKey("path")) {
            value = readMappedPathValue(response, body, stringValue(config.get("path")));
        }
        return !value.present && config.containsKey("default")
                ? ResponseMappedValue.present(config.get("default")) : value;
    }

    private ResponseMappedValue firstReadableValue(List<?> paths, Map<String, Object> response, Object body) {
        for (Object path : paths) {
            ResponseMappedValue value = readMappedPathValue(response, body, stringValue(path));
            if (value.present) {
                return value;
            }
        }
        return ResponseMappedValue.missing();
    }

    private Object readMappedPath(Map<String, Object> response, Object body, String path) {
        return readMappedPathValue(response, body, path).value;
    }

    private ResponseMappedValue readMappedPathValue(Map<String, Object> response, Object body, String path) {
        PathRead responseValue = readPathValue(response, path);
        if (responseValue.present) return ResponseMappedValue.of(responseValue);
        if (body != null) {
            PathRead bodyValue = readPathValue(body, path);
            if (bodyValue.present) return ResponseMappedValue.of(bodyValue);
        }
        return ResponseMappedValue.missing();
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
            String op = firstText(condition.get("operator"), condition.get("op"), "AND");
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
        ResponseMappedValue actualRead = readMappedPathValue(response, body, path);
        Object actual = actualRead.value;
        boolean actualPresent = actualRead.present;
        Object expected = condition.containsKey("values") ? condition.get("values") : condition.get("value");
        boolean expectedPresent = true;
        if ("VAR".equalsIgnoreCase(firstText(condition.get("valueKind")))) {
            ResponseMappedValue expectedRead = readMappedPathValue(response, body, stringValue(expected));
            expected = expectedRead.value;
            expectedPresent = expectedRead.present;
        }
        String operator = firstText(condition.get("operator"), "==").toLowerCase();
        return compareConditionValue(actual, expected, operator, actualPresent, expectedPresent);
    }

    private boolean compareConditionValue(Object actual, Object expected, String operator) {
        return compareConditionValue(actual, expected, operator, true, true);
    }

    private boolean compareConditionValue(Object actual, Object expected, String operator,
                                           boolean actualPresent, boolean expectedPresent) {
        String op = operator == null ? "==" : operator.toLowerCase();
        if ("*".equals(op)) return true;
        if ("is_null".equals(op)) return actualPresent && actual == null;
        if ("not_null".equals(op)) return actualPresent && actual != null;
        if ("is_empty".equals(op)) return isEmptyValue(actual);
        if ("not_empty".equals(op)) return !isEmptyValue(actual);
        if ("is_true".equals(op)) return actual != null && booleanValue(actual);
        if ("is_false".equals(op)) return actual != null && !booleanValue(actual);

        // 未返回字段与返回 null 都是缺失语义，但映射条件不能把它们误当作显式 null。
        if (!actualPresent || !expectedPresent) return false;

        if (MissingValueSemantics.isMissing(actual) || MissingValueSemantics.isMissing(expected)) {
            if ("==".equals(op) || "eq".equals(op)) return valuesEqual(actual, expected);
            if ("!=".equals(op) || "<>".equals(op) || "ne".equals(op)) return !valuesEqual(actual, expected);
            return false;
        }

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
        if ("regex".equals(op) || "matches".equals(op) || "string_matches".equals(op)) {
            try {
                return Pattern.compile(expectedText).matcher(actualText).matches();
            } catch (PatternSyntaxException e) {
                return false;
            }
        }
        if ("not_regex".equals(op) || "not_matches".equals(op)) {
            try {
                return !Pattern.compile(expectedText).matcher(actualText).matches();
            } catch (PatternSyntaxException e) {
                return false;
            }
        }
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
        if (value instanceof CharSequence) return String.valueOf(value).trim().isEmpty();
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
        Object tree = condition.containsKey("condition") ? condition.get("condition") : condition;
        return responseConditionMatches(tree, response, response == null ? null : response.get("body"));
    }

    boolean matchesResponseCondition(String conditionConfig, Map<String, Object> response) {
        if (!hasText(conditionConfig)) {
            return true;
        }
        Map<String, Object> condition = parseJsonMap(conditionConfig);
        Object tree = condition.containsKey("condition") ? condition.get("condition") : condition;
        return responseConditionMatches(tree, response, response == null ? null : response.get("body"));
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
                throw new IllegalArgumentException("请求模板占位符未闭合");
            }
            String path = result.substring(start + 2, end).trim();
            if (!containsPath(params, path)) {
                throw new IllegalArgumentException("请求模板占位变量未配置: " + path);
            }
            Object value = readPath(params, path);
            if (value == null) {
                throw new IllegalArgumentException("请求模板占位变量为 null，无法嵌入字符串: " + path);
            }
            result = result.substring(0, start) + String.valueOf(value) + result.substring(end + 1);
            start = result.indexOf("${", start + String.valueOf(value).length());
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

    private PathRead readPathValue(Object root, String path) {
        if (root == null) return new PathRead(false, null);
        if (path == null || path.trim().isEmpty()) return new PathRead(true, root);
        String normalized = path.startsWith("$.") ? path.substring(2) : path;
        PathRead value = readPathParts(root, normalized);
        return !value.present && normalized.startsWith("body.")
                ? readPathParts(root, normalized.substring("body.".length())) : value;
    }

    private Integer parseIndex(String text) {
        try {
            return Integer.valueOf(text);
        } catch (Exception e) {
            return null;
        }
    }

    private PathRead readPathParts(Object root, String path) {
        if (path == null || path.isEmpty()) return new PathRead(true, root);
        Object current = root;
        for (String part : path.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                if (!map.containsKey(part)) return new PathRead(false, null);
                current = map.get(part);
            } else if (current instanceof List<?> list) {
                Integer index = parseIndex(part);
                if (index == null || index < 0 || index >= list.size()) return new PathRead(false, null);
                current = list.get(index);
            } else if (current != null && current.getClass().isArray()) {
                Integer index = parseIndex(part);
                if (index == null || index < 0 || index >= Array.getLength(current)) return new PathRead(false, null);
                current = Array.get(current, index);
            } else {
                return new PathRead(false, null);
            }
        }
        return new PathRead(true, current);
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

    private void logTokenCacheEvent(RuleExternalDatasource datasource, RuleExternalApiConfig apiConfig,
                                    String actionType, String cacheKey, String cacheStatus,
                                    boolean success) {
        if (runtimeCallLogService == null || Integer.valueOf(0).equals(apiConfig.getTokenLogEnabled())) {
            return;
        }
        RuleRuntimeCallLog log = new RuleRuntimeCallLog();
        log.setModuleType("DATASOURCE");
        log.setActionType(actionType);
        log.setProjectId(resolveRuntimeProjectId(datasource));
        log.setDatasourceId(datasource.getId());
        log.setTargetRefId(apiConfig.getId());
        log.setTargetCode(apiConfig.getApiCode());
        log.setTargetName(apiConfig.getApiName());
        log.setSuccess(success ? 1 : 0);
        log.setRequestSuccess(success ? 1 : 0);
        log.setProviderRequest(0);
        log.setCacheKey(cacheKey);
        log.setTokenCacheStatus(cacheStatus);
        runtimeCallLogService.safeSave(log);
    }

    private void logTokenCall(RuleExternalDatasource datasource, RuleExternalApiConfig apiConfig,
                              boolean refresh, String cacheKey, HttpMethod method, String tokenUrl,
                              HttpHeaders headers, Object body, Integer responseStatus,
                              boolean success, Integer ttlSeconds, String errorMessage,
                              String originalResponseBody, long costTimeMs) {
        if (runtimeCallLogService == null || Integer.valueOf(0).equals(apiConfig.getTokenLogEnabled())) {
            return;
        }
        RuleRuntimeCallLog log = new RuleRuntimeCallLog();
        log.setModuleType("DATASOURCE");
        log.setActionType(success ? (refresh ? "TOKEN_REFRESH" : "TOKEN_FETCH") : "TOKEN_FETCH_FAILED");
        log.setProjectId(resolveRuntimeProjectId(datasource));
        log.setDatasourceId(datasource.getId());
        log.setTargetRefId(apiConfig.getId());
        log.setTargetCode(apiConfig.getApiCode());
        log.setTargetName(apiConfig.getApiName());
        log.setSuccess(success ? 1 : 0);
        log.setRequestSuccess(success ? 1 : 0);
        log.setProviderRequest(1);
        log.setCacheStatus(refresh ? "REFRESH" : "MISS");
        log.setCacheKey(cacheKey);
        log.setTokenCacheStatus(success ? (refresh ? "REFRESH" : "FETCH")
                : (refresh ? "REFRESH_FAILED" : "FETCH_FAILED"));
        log.setRequestMethod(method == null ? null : method.name());
        log.setRequestUrl(maskUrlForLog(tokenUrl));
        log.setRequestHeaders(runtimeCallLogService.toJson(headersToLog(headers)));
        log.setRequestBody(runtimeCallLogService.toJson(maskSensitiveForLog(body)));
        String originalRequestBody = rawPayload(body);
        ExternalApiPayloadCapturePolicy.Capture requestCapture = capturePayload(
                apiConfig, "request", originalRequestBody, body);
        log.setRawRequestBody(requestCapture.capturedBody());
        log.setRawRequestMetadata(runtimeCallLogService.toJson(requestCapture.metadata()));
        log.setOriginalRequestBody(ExternalApiPayloadCapturePolicy.originalBody(
                apiConfig.getPayloadCaptureConfig(), "request", originalRequestBody, requestCapture));
        log.setResponseStatus(responseStatus);
        Map<String, Object> responseSummary = new LinkedHashMap<>();
        responseSummary.put("tokenReceived", success);
        if (ttlSeconds != null) responseSummary.put("ttlSeconds", ttlSeconds);
        log.setResponseBody(runtimeCallLogService.toJson(responseSummary));
        ExternalApiPayloadCapturePolicy.Capture responseCapture = capturePayload(
                apiConfig, "response", originalResponseBody, null);
        log.setRawResponseBody(responseCapture.capturedBody());
        log.setRawResponseMetadata(runtimeCallLogService.toJson(responseCapture.metadata()));
        log.setOriginalResponseBody(ExternalApiPayloadCapturePolicy.originalBody(
                apiConfig.getPayloadCaptureConfig(), "response", originalResponseBody, responseCapture));
        log.setErrorType(success ? null : "TOKEN_FETCH_FAILED");
        log.setErrorMessage(errorMessage);
        log.setCostTimeMs(costTimeMs);
        runtimeCallLogService.safeSave(log);
    }

    private void logApiAttempt(RuleExternalApiConfig apiConfig, RuleExternalDatasource datasource,
                               PreparedHttpRequest prepared, InvokeTrace trace, int attemptNo,
                               Integer responseStatus, Object responseBody, String rawResponseBody,
                               RuntimeException error,
                               long costTimeMs) {
        if (runtimeCallLogService == null) return;
        RuleRuntimeCallLog log = new RuleRuntimeCallLog();
        if (trace.runtimeTrace != null) {
            log.setTraceId(trace.runtimeTrace.getTraceId());
            log.setRuleTraceId(trace.runtimeTrace.getRuleTraceId());
        }
        log.setModuleType("DATASOURCE");
        log.setActionType("API_ATTEMPT");
        log.setProjectId(resolveRuntimeProjectId(datasource));
        log.setDatasourceId(datasource.getId());
        log.setRequestId(prepared.headers.getFirst("X-Request-Id"));
        log.setCallId(trace.callId);
        log.setTargetRefId(apiConfig.getId());
        log.setTargetCode(apiConfig.getApiCode());
        log.setTargetName(apiConfig.getApiName());
        log.setSuccess(error == null ? 1 : 0);
        log.setRequestSuccess(error == null ? 1 : 0);
        log.setProviderRequest(1);
        log.setCacheStatus(trace.cacheStatus);
        log.setCacheKey(trace.cacheKey);
        log.setAttemptNo(attemptNo);
        log.setCircuitState(trace.circuitState);
        log.setTokenCacheStatus(prepared.tokenCacheStatus);
        log.setRequestMethod(prepared.method.name());
        log.setRequestUrl(requestUrlForLog(prepared));
        log.setRequestHeaders(runtimeCallLogService.toJson(headersToLog(prepared.headers)));
        log.setRequestParams(runtimeCallLogService.toJson(maskSensitiveForLog(trace.requestParams)));
        ExternalApiPayloadCapturePolicy.Capture requestCapture = capturePayload(
                apiConfig, "request", rawRequestPayload(prepared.requestBody, prepared.headers.getContentType()),
                trace.processedRequestBody);
        log.setRequestBody(runtimeCallLogService.toJson(maskSensitiveForLog(
                payloadForLog(prepared.requestBody, requestCapture.capturedBody(), requestCapture.metadata()))));
        log.setRawRequestBody(requestCapture.capturedBody());
        log.setRawRequestMetadata(runtimeCallLogService.toJson(requestCapture.metadata()));
        log.setOriginalRequestBody(ExternalApiPayloadCapturePolicy.originalBody(
                apiConfig.getPayloadCaptureConfig(), "request",
                rawRequestPayload(prepared.requestBody, prepared.headers.getContentType()), requestCapture));
        log.setResponseStatus(responseStatus);
        ExternalApiPayloadCapturePolicy.Capture responseCapture = capturePayload(
                apiConfig, "response", rawResponseBody, trace.processedResponseBody);
        log.setResponseBody(runtimeCallLogService.toJson(maskSensitiveForLog(
                payloadForLog(responseBody, responseCapture.capturedBody(), responseCapture.metadata()))));
        log.setRawResponseBody(responseCapture.capturedBody());
        log.setRawResponseMetadata(runtimeCallLogService.toJson(responseCapture.metadata()));
        log.setOriginalResponseBody(ExternalApiPayloadCapturePolicy.originalBody(
                apiConfig.getPayloadCaptureConfig(), "response", rawResponseBody, responseCapture));
        log.setTraceSteps(runtimeCallLogService.toJson(trace.traceSteps));
        log.setErrorType(errorType(error));
        log.setErrorMessage(error == null ? null : error.getMessage());
        log.setCostTimeMs(costTimeMs);
        runtimeCallLogService.safeSave(log);
    }

    private String errorType(Throwable error) {
        if (error == null) return null;
        Throwable current = error;
        while (current != null) {
            if (current instanceof ApiHttpClientRegistry.PoolBusyException) {
                return ((ApiHttpClientRegistry.PoolBusyException) current).getErrorType();
            }
            if (current.getCause() == null) break;
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    private String rawPayload(Object value) {
        if (value == null) return null;
        if (value instanceof String) return (String) value;
        try {
            return JSON.toJSONString(value);
        } catch (RuntimeException ignored) {
            return String.valueOf(value);
        }
    }

    private String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String rawRequestPayload(Object value, MediaType contentType) {
        if (value instanceof MultiValueMap && contentType != null
                && MediaType.APPLICATION_FORM_URLENCODED.includes(contentType)) {
            StringBuilder body = new StringBuilder();
            for (Map.Entry<?, ?> entry : ((MultiValueMap<?, ?>) value).entrySet()) {
                Object key = entry.getKey();
                Object values = entry.getValue();
                if (!(values instanceof Iterable)) continue;
                for (Object item : (Iterable<?>) values) {
                    if (body.length() > 0) body.append('&');
                    body.append(urlEncode(String.valueOf(key))).append('=').append(urlEncode(String.valueOf(item)));
                }
            }
            return body.toString();
        }
        return rawPayload(value);
    }

    private Object payloadForLog(Object original, String capturedBody, Map<String, Object> metadata) {
        if (metadata != null && "FAILED".equals(String.valueOf(metadata.get("status")))) {
            return java.util.Collections.singletonMap("omitted", true);
        }
        if (metadata != null && "UNAVAILABLE".equals(String.valueOf(metadata.get("status")))
                && "PROCESSED".equals(String.valueOf(metadata.get("source")))) {
            return java.util.Collections.singletonMap("omitted", true);
        }
        if (capturedBody == null || metadata == null) return original;
        String status = String.valueOf(metadata.get("status"));
        String source = String.valueOf(metadata.get("source"));
        if ("FILTERED".equals(status) || source.contains("DECRYPTED")) {
            Object parsed = parseJsonOrRaw(capturedBody);
            return parsed == null ? capturedBody : parsed;
        }
        return original;
    }

    private ExternalApiPayloadCapturePolicy.Capture capturePayload(RuleExternalApiConfig config,
                                                                    String phase,
                                                                    String originalBody,
                                                                    Object processedBody) {
        String captureConfig = config == null ? null : config.getPayloadCaptureConfig();
        Map<String, Object> variables = config == null
                ? java.util.Collections.emptyMap()
                : externalApiScriptService.parseScriptVariables(config.getAuthApiConfig());
        return ExternalApiPayloadCapturePolicy.capture(captureConfig, phase, originalBody, processedBody, variables);
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return value == null ? "" : value;
        }
    }

    private String maskUrlForLog(String url) {
        if (!hasText(url) || url.indexOf('?') < 0) return url;
        try {
            int queryIndex = url.indexOf('?');
            String baseUrl = url.substring(0, queryIndex);
            MultiValueMap<String, String> query = UriComponentsBuilder.fromHttpUrl(url)
                    .build(false).getQueryParams();
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl);
            for (Map.Entry<String, List<String>> entry : query.entrySet()) {
                for (String value : entry.getValue()) {
                    builder.queryParam(entry.getKey(), isSensitiveKey(entry.getKey()) ? "******" : value);
                }
            }
            return builder.build(false).encode(StandardCharsets.UTF_8).toUriString();
        } catch (RuntimeException ignored) {
            return url.substring(0, url.indexOf('?')) + "?******";
        }
    }

    private void logDatasourceCall(RuleExternalApiConfig apiConfig, RuleExternalDatasource datasource, InvokeTrace trace,
                                   boolean success, Map<String, Object> response, String errorMessage, long costTimeMs) {
        if (runtimeTraceService != null && trace != null) {
            runtimeTraceService.completeModule(trace.runtimeTrace, success, errorMessage, costTimeMs);
        }
        if (runtimeCallLogService == null) {
            return;
        }
        RuleRuntimeCallLog log = new RuleRuntimeCallLog();
        if (trace != null && trace.runtimeTrace != null) {
            log.setTraceId(trace.runtimeTrace.getTraceId());
            log.setRuleTraceId(trace.runtimeTrace.getRuleTraceId());
        }
        if (trace != null) log.setCallId(trace.callId);
        log.setModuleType("DATASOURCE");
        log.setActionType("API_INVOKE");
        var context = com.hengshucredit.rule.core.engine.RuntimeContextBridge.currentContext();
        Object rootTraceId = context.rootRule().get("traceId");
        if (rootTraceId != null) {
            log.setRootTraceId(String.valueOf(rootTraceId));
            Map<String, Object> fields = new LinkedHashMap<>();
            context.externalFieldPaths(apiConfig.getId()).forEach((key, path) ->
                    fields.put(key, response == null ? null : VariableSourceResolver.readPath(response, path)));
            log.setHistoryFields(JSON.toJSONString(fields, com.alibaba.fastjson.serializer.SerializerFeature.WriteMapNullValue));
        }
        log.setProjectId(resolveRuntimeProjectId(datasource));
        log.setDatasourceId(datasource.getId());
        log.setTargetRefId(apiConfig.getId());
        log.setTargetCode(apiConfig.getApiCode());
        log.setTargetName(apiConfig.getApiName());
        log.setSuccess(success ? 1 : 0);
        Map<String, Object> conditionResponse = conditionResponse(response, trace);
        boolean requestSuccess = hasText(apiConfig.getSuccessCondition())
                ? hasProviderResponse(response, trace)
                    && matchesResponseCondition(apiConfig.getSuccessCondition(), conditionResponse)
                : defaultRequestSuccess(success, conditionResponse);
        log.setRequestSuccess(requestSuccess ? 1 : 0);
        log.setFound(requestSuccess && matchesBillingCondition(apiConfig.getBillingCondition(), conditionResponse) ? 1 : 0);
        if (trace != null) {
            log.setRequestMethod(trace.requestMethod);
            log.setRequestUrl(trace.requestUrl);
            Object requestId = trace.requestHeaders == null ? null : trace.requestHeaders.get("X-Request-Id");
            log.setRequestId(requestId == null ? null : String.valueOf(requestId));
            log.setAttemptNo(trace.providerAttemptNo);
            log.setCircuitState(trace.circuitState);
            log.setTokenCacheStatus(trace.tokenCacheStatus);
            log.setRequestHeaders(runtimeCallLogService.toJson(trace.requestHeaders));
            log.setRequestParams(runtimeCallLogService.toJson(maskSensitiveForLog(trace.requestParams)));
            log.setRequestBody(runtimeCallLogService.toJson(maskSensitiveForLog(
                    payloadForLog(trace.requestBody, trace.rawRequestBody, trace.rawRequestMetadata))));
            log.setRawRequestBody(trace.rawRequestBody);
            log.setRawRequestMetadata(runtimeCallLogService.toJson(trace.rawRequestMetadata));
            log.setOriginalRequestBody(trace.originalRequestBody);
            log.setTraceSteps(runtimeCallLogService.toJson(trace.traceSteps));
            Object responseStatus = trace.responseStatus == null ? conditionResponse.get("httpStatus") : trace.responseStatus;
            log.setResponseStatus(responseStatus instanceof Number ? ((Number) responseStatus).intValue() : null);
            log.setProviderRequest(trace.requestIssued ? 1 : 0);
            log.setCacheStatus(trace.cacheStatus);
            log.setCacheKey(trace.cacheKey);
        }
        Object responseForLog = response != null ? response : (trace == null ? null : trace.responseBody);
        if (trace != null) responseForLog = payloadForLog(responseForLog, trace.rawResponseBody, trace.rawResponseMetadata);
        log.setResponseBody(runtimeCallLogService.toJson(maskSensitiveForLog(responseForLog)));
        log.setRawResponseBody(trace == null ? null : trace.rawResponseBody);
        log.setRawResponseMetadata(runtimeCallLogService.toJson(trace == null ? null : trace.rawResponseMetadata));
        log.setOriginalResponseBody(trace == null ? null : trace.originalResponseBody);
        log.setErrorType(errorMessage == null ? null : "API_INVOKE_FAILED");
        log.setErrorMessage(errorMessage);
        log.setCostTimeMs(costTimeMs);
        runtimeCallLogService.safeSave(log);
    }

    /** 全局数据源被项目规则调用时，日志必须归属实际执行项目，便于业务令牌按项目取回。 */
    private Long resolveRuntimeProjectId(RuleExternalDatasource datasource) {
        Object rootProject = com.hengshucredit.rule.core.engine.RuntimeContextBridge.currentContext()
                .rootRule().get("projectId");
        if (rootProject instanceof Number number && number.longValue() > 0) return number.longValue();
        if (rootProject != null) {
            try {
                long value = Long.parseLong(String.valueOf(rootProject));
                if (value > 0) return value;
            } catch (NumberFormatException ignored) {
                // 管理端直接测试没有根项目上下文，回退到数据源归属。
            }
        }
        return datasource == null ? null : datasource.getProjectId();
    }

    private Map<String, Object> conditionResponse(Map<String, Object> response, InvokeTrace trace) {
        Map<String, Object> result = response == null ? new LinkedHashMap<>() : copyResponse(response);
        if (trace != null && trace.responseStatus != null) {
            result.putIfAbsent("httpStatus", trace.responseStatus);
            result.putIfAbsent("responseStatus", trace.responseStatus);
        }
        if (!result.containsKey("body") && trace != null && trace.responseBody != null) {
            result.put("body", trace.responseBody);
        }
        return result;
    }

    /** 将一次逻辑外数调用的原始报文和关联键放入统一返回槽位，避免下游判断映射配置。 */
    private Map<String, Object> attachExternalCall(Map<String, Object> result, InvokeTrace trace) {
        if (result == null) result = new LinkedHashMap<>();
        if (trace == null) return result;
        Map<String, Object> externalCall = new LinkedHashMap<>();
        externalCall.put("callId", trace.callId);
        externalCall.put("traceId", trace.runtimeTrace == null ? null : trace.runtimeTrace.getTraceId());
        externalCall.put("rootTraceId", com.hengshucredit.rule.core.engine.RuntimeContextBridge.currentContext()
                .rootRule().get("traceId"));
        externalCall.put("requestId", trace.requestHeaders == null ? null : trace.requestHeaders.get("X-Request-Id"));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", trace.requestMethod);
        request.put("url", trace.requestUrl);
        request.put("headers", trace.requestHeaders);
        request.put("params", trace.requestParams);
        request.put("body", trace.requestBody);
        request.put("rawBody", trace.rawRequestBody);
        request.put("captureMetadata", trace.rawRequestMetadata);
        request.put("originalBody", trace.originalRequestBody);
        externalCall.put("request", request);
        externalCall.put("rawRequestAvailable", trace.rawRequestBody != null);
        externalCall.put("originalRequestAvailable", trace.originalRequestBody != null);
        externalCall.put("responseStatus", trace.responseStatus);
        externalCall.put("rawResponseBody", trace.rawResponseBody);
        externalCall.put("rawResponse", parseJsonOrRaw(trace.rawResponseBody));
        externalCall.put("rawResponseMetadata", trace.rawResponseMetadata);
        externalCall.put("originalResponseBody", trace.originalResponseBody);
        externalCall.put("traceSteps", trace.traceSteps);
        externalCall.put("rawResponseAvailable", trace.rawResponseBody != null);
        externalCall.put("originalResponseAvailable", trace.originalResponseBody != null);
        result.put("externalCall", externalCall);
        result.put("callId", trace.callId);
        result.put("rootTraceId", com.hengshucredit.rule.core.engine.RuntimeContextBridge.currentContext()
                .rootRule().get("traceId"));
        result.put("rawRequestAvailable", trace.rawRequestBody != null);
        result.put("originalRequestAvailable", trace.originalRequestBody != null);
        result.put("rawRequestBody", trace.rawRequestBody);
        result.put("rawRequest", parseJsonOrRaw(trace.rawRequestBody));
        result.put("rawRequestMetadata", trace.rawRequestMetadata);
        result.put("originalRequestBody", trace.originalRequestBody);
        result.put("rawResponseAvailable", trace.rawResponseBody != null);
        result.put("originalResponseAvailable", trace.originalResponseBody != null);
        result.put("rawResponseBody", trace.rawResponseBody);
        result.put("rawResponse", parseJsonOrRaw(trace.rawResponseBody));
        result.put("rawResponseMetadata", trace.rawResponseMetadata);
        result.put("originalResponseBody", trace.originalResponseBody);
        result.put("traceSteps", trace.traceSteps);
        return result;
    }

    private boolean hasProviderResponse(Map<String, Object> response, InvokeTrace trace) {
        return response != null || (trace != null && trace.responseStatus != null);
    }

    private boolean defaultRequestSuccess(boolean success, Map<String, Object> response) {
        Object status = response == null ? null : response.get("httpStatus");
        if (status == null && response != null) {
            status = response.get("responseStatus");
        }
        Integer httpStatus = status instanceof Number ? ((Number) status).intValue() : parseIndex(stringValue(status));
        return httpStatus == null ? success : httpStatus >= 200 && httpStatus < 300;
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
        log.setProjectId(resolveRuntimeProjectId(datasource));
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
            log.setOriginalRequestBody(trace.originalRequestBody);
            log.setResponseStatus(trace.responseStatus);
        }
        log.setResponseBody(runtimeCallLogService.toJson(maskSensitiveForLog(response)));
        log.setOriginalResponseBody(trace == null ? null : trace.originalResponseBody);
        log.setErrorMessage(errorMessage);
        log.setCostTimeMs(costTimeMs);
        runtimeCallLogService.safeSave(log);
    }

    private Map<String, Object> previewStaticAuthConfig(String authType, Map<String, Object> config, Map<String, Object> params) {
        authType = firstText(authType, "NONE").toUpperCase(Locale.ROOT);
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

    private Object maskSensitiveForPreview(Object value) {
        if (value instanceof Map) {
            Map<String, Object> masked = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey());
                masked.put(key, isSensitiveKey(key) ? "******" : maskSensitiveForPreview(entry.getValue()));
            }
            return masked;
        }
        if (value instanceof Iterable) {
            List<Object> masked = new ArrayList<>();
            for (Object item : (Iterable<?>) value) masked.add(maskSensitiveForPreview(item));
            return masked;
        }
        return value;
    }

    private boolean isSensitiveKey(String name) {
        String lower = name == null ? "" : name.toLowerCase();
        return lower.contains("password") || lower.contains("token")
                || lower.contains("secret") || lower.contains("key")
                || lower.contains("authorization") || lower.contains("signature")
                || lower.equals("sign") || lower.contains("private");
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
        private String callId;
        private boolean tokenRefreshAttempted;
        private RuntimeTraceService.ModuleTrace runtimeTrace;
        private String requestMethod;
        private String requestUrl;
        private Map<String, Object> requestHeaders;
        private Object requestParams;
        private Object rootInput;
        private Object requestBody;
        private Object processedRequestBody;
        private String rawRequestBody;
        private String originalRequestBody;
        private Integer responseStatus;
        private Object responseBody;
        private Object processedResponseBody;
        private String rawResponseBody;
        private String originalResponseBody;
        private Map<String, Object> rawRequestMetadata;
        private Map<String, Object> rawResponseMetadata;
        private boolean requestIssued;
        private String cacheStatus;
        private String cacheKey;
        private int providerAttemptNo;
        private String circuitState;
        private String tokenCacheStatus;
        private String authMode;
        private final List<Map<String, Object>> traceSteps = new ArrayList<>();
    }

    static class ApiInvokeException extends IllegalStateException {
        private final boolean cacheConfigured;
        private final String cacheStatus;
        private final boolean unknown;

        ApiInvokeException(String message, Throwable cause, boolean cacheConfigured, String cacheStatus,
                           boolean unknown) {
            super(message, cause);
            this.cacheConfigured = cacheConfigured;
            this.cacheStatus = cacheStatus;
            this.unknown = unknown;
        }

        boolean isCacheConfigured() {
            return cacheConfigured;
        }

        String getCacheStatus() {
            return cacheStatus;
        }

        boolean isUnknown() {
            return unknown;
        }
    }

    private static class BusinessResponseException extends IllegalStateException {
        private final boolean retryable;

        private BusinessResponseException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        private boolean isRetryable() {
            return retryable;
        }
    }

    private static class PreparedHttpRequest {
        private String baseUrl;
        private String finalUrl;
        private HttpMethod method;
        private HttpHeaders headers;
        private Map<String, Object> query;
        private Map<String, Object> state;
        private Object scriptBody;
        private Object requestBody;
        private String tokenCacheKey;
        private String tokenCacheStatus;
        private String authMode;
        private int attemptNo;
    }

    private static class AppliedAuth {
        private final String token;
        private final String tokenCacheKey;
        private final String tokenCacheStatus;

        private AppliedAuth(String token, String tokenCacheKey, String tokenCacheStatus) {
            this.token = token;
            this.tokenCacheKey = tokenCacheKey;
            this.tokenCacheStatus = tokenCacheStatus;
        }
    }

    private static class TokenRefreshRejectedException extends IllegalStateException {
        private TokenRefreshRejectedException(HttpStatusCodeException cause) {
            super("Provider rejected the refreshed token", cause);
        }
    }

}
