package com.hengshucredit.rule.client.sync;

import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import com.hengshucredit.rule.client.auth.ClientRequestAuthenticator;
import com.hengshucredit.rule.client.auth.ProjectClientAuthenticationException;
import com.hengshucredit.rule.client.cache.CachedRule;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteRequest;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteResult;
import com.hengshucredit.rule.model.dto.RuleExecutionStatus;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HttpSyncClient {

    private static final Logger log = LoggerFactory.getLogger(HttpSyncClient.class);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient httpClient;
    private final String serverUrl;
    private final HttpUrl baseUrl;
    private final ClientRequestAuthenticator authenticator;

    public HttpSyncClient(String serverUrl, int timeoutMs) {
        this(serverUrl, timeoutMs, (ClientRequestAuthenticator) null);
    }
    
    public HttpSyncClient(String serverUrl, int timeoutMs, String token) {
        this(serverUrl, timeoutMs, token == null || token.isEmpty() ? null
                : new ClientRequestAuthenticator(serverUrl, timeoutMs, ClientAuthConfig.legacyToken(token)));
    }

    public HttpSyncClient(String serverUrl, int timeoutMs, ClientRequestAuthenticator authenticator) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.baseUrl = HttpUrl.parse(this.serverUrl);
        if (this.baseUrl == null || !this.baseUrl.username().isEmpty() || !this.baseUrl.password().isEmpty()
                || this.baseUrl.query() != null || this.baseUrl.fragment() != null) {
            throw new IllegalArgumentException("serverUrl 必须是无凭据、查询串及片段的 HTTP(S) 地址");
        }
        this.authenticator = authenticator;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                // 规则执行可能产生外部调用和计费，不能让 OkHttp 在连接失败时自动重放请求。
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    public CachedRule fetchRule(String ruleCode) {
        requiredPathSegment(ruleCode, "ruleCode");
        return fetchRulePath(ruleUrl(ruleCode, null));
    }

    public CachedRule fetchRuleById(Long ruleId, Long bindingId) {
        if (ruleId == null || ruleId <= 0) throw new IllegalArgumentException("ruleId 必须为正数");
        HttpUrl.Builder url = baseUrl.newBuilder().addPathSegment("api").addPathSegment("rule")
                .addPathSegment("sync").addPathSegment("by-id").addPathSegment(String.valueOf(ruleId));
        if (bindingId != null) {
            if (bindingId <= 0) throw new IllegalArgumentException("bindingId 必须为正数");
            url.addQueryParameter("versionBindingId", String.valueOf(bindingId));
        }
        CachedRule rule = fetchRulePath(url.build().toString());
        if (rule != null) rule.setFixedVersion(bindingId != null);
        return rule;
    }

    private CachedRule fetchRulePath(String path) {
        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(path)
                    .get();
            Request request = authenticate(requestBuilder.build());
            try (Response response = httpClient.newCall(request).execute()) {
                throwIfAuthenticationFailure(response);
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject json = JSON.parseObject(response.body().string());
                    if (json.getIntValue("code") == 200 && json.get("data") != null) {
                        return toCachedRule(json.getJSONObject("data"));
                    }
                }
            }
        } catch (ProjectClientAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch rule {}: {}", path, e.getMessage());
        }
        return null;
    }

    public List<CachedRule> fetchAll() {
        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(serverUrl + "/api/rule/sync/all")
                    .get();
            Request request = authenticate(requestBuilder.build());
            try (Response response = httpClient.newCall(request).execute()) {
                throwIfAuthenticationFailure(response);
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("Failed to fetch all rules: HTTP {}", response.code());
                    return null;
                }
                JSONObject json = JSON.parseObject(response.body().string());
                if (json == null || json.getIntValue("code") != 200) {
                    log.warn("Failed to fetch all rules: invalid response");
                    return null;
                }
                JSONArray arr = json.getJSONArray("data");
                if (arr == null) {
                    log.warn("Failed to fetch all rules: missing data");
                    return null;
                }
                List<CachedRule> rules = new ArrayList<>();
                for (int i = 0; i < arr.size(); i++) {
                    CachedRule r = toCachedRule(arr.getJSONObject(i));
                    if (r != null) rules.add(r);
                }
                return rules;
            }
        } catch (ProjectClientAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch all rules: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从服务端拉取项目函数列表（JAVA/BEAN/SCRIPT 类型）
     *
     * @param projectId 项目 ID
     * @return 函数元数据 JSON 列表
     */
    public List<JSONObject> fetchFunctions(long projectId) {
        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(serverUrl + "/api/rule/sync/functions/" + projectId)
                    .get();
            Request request = authenticate(requestBuilder.build());
            try (Response response = httpClient.newCall(request).execute()) {
                throwIfAuthenticationFailure(response);
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("Failed to fetch functions for project {}: HTTP {}", projectId, response.code());
                    return null;
                }
                JSONObject json = JSON.parseObject(response.body().string());
                if (json == null || json.getIntValue("code") != 200) {
                    log.warn("Failed to fetch functions for project {}: invalid response", projectId);
                    return null;
                }
                JSONArray arr = json.getJSONArray("data");
                if (arr == null) {
                    log.warn("Failed to fetch functions for project {}: missing data", projectId);
                    return null;
                }
                List<JSONObject> functions = new ArrayList<>();
                for (int i = 0; i < arr.size(); i++) {
                    functions.add(arr.getJSONObject(i));
                }
                return functions;
            }
        } catch (ProjectClientAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch functions for project {}: {}", projectId, e.getMessage());
        }
        return null;
    }

    public RuleResult executeRule(String ruleCode, Object params, String clientAppName) {
        return executeRule(ruleCode, params, clientAppName, true);
    }

    public RuleResult executeRule(String ruleCode, Object params, String clientAppName, boolean traceEnabled) {
        requiredPathSegment(ruleCode, "ruleCode");
        try {
            JSONObject body = new JSONObject();
            body.put("params", params);
            body.put("clientAppName", clientAppName);
            body.put("traceEnabled", traceEnabled);
            RequestBody requestBody = RequestBody.create(JSON.toJSONString(body), JSON_MEDIA_TYPE);
            Request.Builder requestBuilder = new Request.Builder()
                    .url(executionUrl(ruleCode))
                    .post(requestBody);
            Request request = authenticate(requestBuilder.build());
            try (Response response = httpClient.newCall(request).execute()) {
                throwIfAuthenticationFailure(response);
                if (response.body() == null) {
                    return failure("Empty response from rule server");
                }
                JSONObject json;
                try {
                    json = JSON.parseObject(response.body().string());
                } catch (RuntimeException e) {
                    return failure("Rule server returned malformed JSON");
                }
                if (json == null) return failure("Rule server returned an empty response");
                int platformCode = json.getIntValue("code");
                if (response.isSuccessful() && platformCode == 200 && json.get("data") != null) {
                    RuleResult result = json.getJSONObject("data").toJavaObject(RuleResult.class);
                    if (result.getExecutionStatus() == null || result.getExecutionStatus().isBlank()) {
                        result.setExecutionStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
                    }
                    return result;
                }
                JSONObject data = json.getJSONObject("data");
                RuleResult result = failure(json.getString("message"), statusFor(platformCode, response.code()));
                result.setPlatformCode(platformCode == 0 ? null : platformCode);
                result.setRetryAfterMs(retryAfterMillis(response.header("Retry-After")));
                if (data != null) {
                    String errorTraceId = data.getString("traceId");
                    if (errorTraceId == null || errorTraceId.isBlank()) {
                        errorTraceId = json.getString("traceId");
                    }
                    result.setTraceId(errorTraceId);
                    if (data.getString("executionStatus") != null) {
                        result.setExecutionStatus(data.getString("executionStatus"));
                    }
                }
                return result;
            }
        } catch (ProjectClientAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to execute rule {} on server: {}", ruleCode, e.getMessage());
            return failure("引擎连接失败或超时；执行状态可能未知，请勿盲目重试", "UNKNOWN");
        }
    }

    public RuleExperimentExecuteResult executeExperiment(String experimentCode,
                                                          RuleExperimentExecuteRequest request) {
        requiredPathSegment(experimentCode, "experimentCode");
        try {
            JSONObject body = request == null ? new JSONObject()
                    : JSON.parseObject(JSON.toJSONString(request));
            Request requestBuilder = new Request.Builder()
                    .url(baseUrl.newBuilder().addPathSegment("api").addPathSegment("rule")
                            .addPathSegment("runtime").addPathSegment("experiment").addPathSegment("execute")
                            .addPathSegment(experimentCode).build())
                    .post(RequestBody.create(JSON.toJSONString(body), JSON_MEDIA_TYPE)).build();
            Request authenticated = authenticate(requestBuilder);
            try (Response response = httpClient.newCall(authenticated).execute()) {
                throwIfAuthenticationFailure(response);
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IllegalStateException("实验执行 HTTP 调用失败: " + response.code());
                }
                JSONObject json = JSON.parseObject(response.body().string());
                if (json.getIntValue("code") != 200 || json.get("data") == null) {
                    throw new IllegalStateException(json.getString("message"));
                }
                return json.getJSONObject("data").toJavaObject(RuleExperimentExecuteResult.class);
            }
        } catch (ProjectClientAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("实验执行失败: " + e.getMessage(), e);
        }
    }

    /** 查询跨请求恢复状态；调用方可在 UNKNOWN/IN_PROGRESS 后按 traceId 轮询。 */
    public RuleExecutionStatus getExecutionStatus(String traceId) {
        if (traceId == null || traceId.trim().isEmpty()) throw new IllegalArgumentException("traceId 不能为空");
        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(baseUrl.newBuilder().addPathSegment("api").addPathSegment("rule")
                            .addPathSegment("runtime").addPathSegment("executions")
                            .addPathSegment(traceId).build())
                    .get();
            Request request = authenticate(requestBuilder.build());
            try (Response response = httpClient.newCall(request).execute()) {
                throwIfAuthenticationFailure(response);
                if (response.body() == null) throw new IllegalStateException("状态查询响应为空");
                JSONObject json = JSON.parseObject(response.body().string());
                int code = json == null ? 0 : json.getIntValue("code");
                if (!response.isSuccessful() || code != 200 || json.get("data") == null) {
                    throw new IllegalStateException("状态查询失败，平台响应码: " + code);
                }
                return json.getJSONObject("data").toJavaObject(RuleExecutionStatus.class);
            }
        } catch (ProjectClientAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("执行状态查询失败；状态可能未知，请稍后重试", e);
        }
    }

    /** 使用原订单入参按 trace 恢复等待中的规则；服务端会校验入参摘要并复用已完成检查点。 */
    public RuleResult resumeExecution(String traceId, Object params) {
        if (traceId == null || traceId.trim().isEmpty()) throw new IllegalArgumentException("traceId 不能为空");
        try {
            Request request = authenticate(new Request.Builder()
                    .url(baseUrl.newBuilder().addPathSegment("api").addPathSegment("rule")
                            .addPathSegment("runtime").addPathSegment("executions")
                            .addPathSegment(traceId).addPathSegment("resume").build())
                    .post(RequestBody.create(params == null ? new byte[0] : JSON.toJSONBytes(params), JSON_MEDIA_TYPE))
                    .build());
            try (Response response = httpClient.newCall(request).execute()) {
                throwIfAuthenticationFailure(response);
                if (response.body() == null) throw new IllegalStateException("恢复响应为空");
                JSONObject json = JSON.parseObject(response.body().string());
                if (!response.isSuccessful() || json == null || json.getIntValue("code") != 200
                        || json.get("data") == null) {
                    throw new IllegalStateException(json == null ? "恢复执行失败" : json.getString("message"));
                }
                return json.getJSONObject("data").toJavaObject(RuleResult.class);
            }
        } catch (ProjectClientAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("恢复规则执行失败；请继续使用 trace 查询状态", e);
        }
    }

    private String ruleUrl(String ruleCode, Long bindingId) {
        HttpUrl.Builder builder = baseUrl.newBuilder().addPathSegment("api").addPathSegment("rule")
                .addPathSegment("sync").addPathSegment(ruleCode);
        if (bindingId != null) builder.addQueryParameter("versionBindingId", String.valueOf(bindingId));
        return builder.build().toString();
    }

    private String executionUrl(String ruleCode) {
        return baseUrl.newBuilder().addPathSegment("api").addPathSegment("rule")
                .addPathSegment("sync").addPathSegment("execute").addPathSegment(ruleCode)
                .build().toString();
    }

    private static void requiredPathSegment(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " 不能为空");
        if (".".equals(value) || "..".equals(value) || value.contains("/") || value.contains("\\")) {
            throw new IllegalArgumentException(name + " 不能包含路径段");
        }
    }

    private static String statusFor(int platformCode, int httpStatus) {
        if (platformCode == 400004) return "CONFLICT";
        if (platformCode == 400005 || platformCode == 409) return "IN_PROGRESS";
        if (httpStatus == 408 || httpStatus == 429 || httpStatus >= 500) return "UNKNOWN";
        return "REJECTED";
    }

    private static Long retryAfterMillis(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Math.max(0L, Long.parseLong(value.trim())) * 1000L;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Request authenticate(Request request) {
        try {
            return authenticator == null ? request : authenticator.authenticate(request);
        } catch (java.io.IOException e) {
            throw new ProjectClientAuthenticationException("Project authentication failed: " + e.getMessage(), e);
        }
    }

    private void throwIfAuthenticationFailure(Response response) {
        if (response.code() != 401 && response.code() != 403) {
            return;
        }
        String message = null;
        try {
            if (response.body() != null) {
                message = JSON.parseObject(response.body().string()).getString("message");
            }
        } catch (RuntimeException | java.io.IOException ignored) {
            // Fall back to the HTTP status when the error body is not valid JSON.
        }
        if (message == null || message.trim().isEmpty()) {
            message = "HTTP " + response.code();
        }
        throw new ProjectClientAuthenticationException("Project authentication failed: " + message);
    }

    private CachedRule toCachedRule(JSONObject obj) {
        if (obj == null) return null;
        CachedRule rule = new CachedRule();
        rule.setRuleCode(obj.getString("ruleCode"));
        rule.setDefinitionId(obj.getLong("definitionId"));
        rule.setVersionBindingId(obj.getLong("versionBindingId"));
        rule.setBindingGeneration(obj.getLong("bindingGeneration"));
        rule.setImported(obj.getBooleanValue("imported"));
        JSONObject bindings = obj.getJSONObject("importBindings");
        if (bindings != null) {
            java.util.Map<String, Long> ids = new java.util.LinkedHashMap<>();
            for (String key : bindings.keySet()) ids.put(key, bindings.getLong(key));
            rule.setImportBindings(ids);
        }
        rule.setProjectCode(obj.getString("projectCode"));
        rule.setRequiresServerExecution(obj.getBooleanValue("requiresServerExecution"));
        rule.setServerExecutionReason(obj.getString("serverExecutionReason"));
        rule.setVersion(obj.getIntValue("version"));
        rule.setRevisionId(obj.getLong("revisionId"));
        rule.setArtifactDigest(obj.getString("artifactDigest"));
        rule.setModelType(obj.getString("modelType"));
        rule.setCompiledScript(obj.getString("compiledScript"));
        rule.setCompiledType(obj.getString("compiledType"));
        rule.setModelJson(obj.getString("modelJson"));
        JSONArray outputScriptNames = obj.getJSONArray("outputScriptNames");
        if (outputScriptNames != null) {
            rule.setOutputScriptNames(outputScriptNames.toJavaList(String.class));
        }
        rule.setLastUpdateTime(System.currentTimeMillis());
        return rule;
    }

    private RuleResult failure(String message) {
        return failure(message, "FAILED");
    }

    private RuleResult failure(String message, String status) {
        RuleResult result = new RuleResult();
        result.setSuccess(false);
        result.setErrorMessage(message == null || message.isEmpty() ? "Rule server execution failed" : message);
        result.setExecutionStatus(status);
        return result;
    }
}
