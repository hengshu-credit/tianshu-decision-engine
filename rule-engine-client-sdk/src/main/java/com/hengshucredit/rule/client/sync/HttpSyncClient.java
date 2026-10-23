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
import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.client.http.PlatformHttpResponses;
import com.hengshucredit.rule.client.http.RuleHttpException;
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
                JSONObject json = PlatformHttpResponses.read(response);
                if (PlatformCode.RULE_NOT_FOUND.code().equals(json.getString("code"))
                        || PlatformCode.RESOURCE_NOT_FOUND.code().equals(json.getString("code"))) return null;
                PlatformHttpResponses.requireSuccess(response, json);
                return json.get("data") == null ? null : toCachedRule(json.getJSONObject("data"));
            }
        } catch (ProjectClientAuthenticationException e) {
            throw e;
        } catch (RuleHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleHttpException("规则同步失败", 0, PlatformCode.UPSTREAM_CALL_FAILED.code(), "REJECTED", null, null);
        }
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
                JSONObject json = PlatformHttpResponses.read(response);
                if (json == null || !PlatformCode.SUCCESS.code().equals(json.getString("code"))) {
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
                JSONObject json = PlatformHttpResponses.read(response);
                if (json == null || !PlatformCode.SUCCESS.code().equals(json.getString("code"))) {
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

    /** 从服务端拉取全局鉴权客户端可见的全部启用函数。 */
    public List<JSONObject> fetchFunctionsAll() {
        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(serverUrl + "/api/rule/sync/functions/all")
                    .get();
            Request request = authenticate(requestBuilder.build());
            try (Response response = httpClient.newCall(request).execute()) {
                throwIfAuthenticationFailure(response);
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("Failed to fetch all functions: HTTP {}", response.code());
                    return null;
                }
                JSONObject json = PlatformHttpResponses.read(response);
                if (json == null || !PlatformCode.SUCCESS.code().equals(json.getString("code"))) return null;
                JSONArray arr = json.getJSONArray("data");
                if (arr == null) return null;
                List<JSONObject> functions = new ArrayList<>();
                for (int i = 0; i < arr.size(); i++) functions.add(arr.getJSONObject(i));
                return functions;
            }
        } catch (ProjectClientAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch all functions: {}", e.getMessage());
            return null;
        }
    }

    public RuleResult executeRule(String ruleCode, Object params, String clientAppName) {
        return executeRule(ruleCode, params, clientAppName, true);
    }

    public RuleResult executeRule(String ruleCode, Object params, String clientAppName, boolean traceEnabled) {
        requiredPathSegment(ruleCode, "ruleCode");
        JSONObject body = new JSONObject();
        body.put("params", params == null ? java.util.Map.of() : params);
        body.put("clientAppName", clientAppName);
        body.put("traceEnabled", traceEnabled);
        try (Response response = executeRequest(executionUrl(ruleCode), body, false)) {
            return PlatformHttpResponses.ruleResult(response, PlatformHttpResponses.read(response));
        } catch (java.io.IOException e) {
            throw unknown(null);
        }
    }

    public RuleExperimentExecuteResult executeExperiment(String experimentCode, RuleExperimentExecuteRequest request) {
        requiredPathSegment(experimentCode, "experimentCode");
        String url = baseUrl.newBuilder().addPathSegments("api/rule/runtime/experiment/execute")
                .addPathSegment(experimentCode).build().toString();
        try (Response response = executeRequest(url, request, false)) {
            return PlatformHttpResponses.experimentResult(response, PlatformHttpResponses.read(response));
        } catch (java.io.IOException e) {
            throw unknown(null);
        }
    }

    public RuleExecutionStatus getExecutionStatus(String traceId) {
        requiredPathSegment(traceId, "traceId");
        try (Response response = executeRequest(stateUrl(traceId, false), null, true)) {
            JSONObject envelope = PlatformHttpResponses.read(response);
            PlatformHttpResponses.requireSuccess(response, envelope);
            if (!(envelope.get("data") instanceof JSONObject)) throw PlatformHttpResponses.invalid(response.code());
            return envelope.getJSONObject("data").toJavaObject(RuleExecutionStatus.class);
        } catch (java.io.IOException e) {
            throw unknown(traceId);
        }
    }

    public RuleResult resumeExecution(String traceId, Object params) {
        requiredPathSegment(traceId, "traceId");
        try (Response response = executeRequest(stateUrl(traceId, true), params, false)) {
            return PlatformHttpResponses.ruleResult(response, PlatformHttpResponses.read(response));
        } catch (java.io.IOException e) {
            throw unknown(traceId);
        }
    }

    private String stateUrl(String traceId, boolean resume) {
        HttpUrl.Builder url = baseUrl.newBuilder().addPathSegments("api/rule/runtime/executions").addPathSegment(traceId);
        if (resume) url.addPathSegment("resume");
        return url.build().toString();
    }

    private Response executeRequest(String url, Object body, boolean get) throws java.io.IOException {
        Request.Builder builder = new Request.Builder().url(url);
        if (get) builder.get();
        else builder.post(RequestBody.create(body == null ? new byte[0] : JSON.toJSONBytes(body), JSON_MEDIA_TYPE));
        return httpClient.newCall(authenticate(builder.build())).execute();
    }

    private RuleHttpException unknown(String traceId) {
        return new RuleHttpException(null, 0, PlatformCode.EXECUTION_UNKNOWN.code(), "UNKNOWN", traceId, null);
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

    private Request authenticate(Request request) {
        try {
            return authenticator == null ? request : authenticator.authenticate(request);
        } catch (java.io.IOException e) {
            if (e instanceof com.hengshucredit.rule.client.auth.ProjectTokenExchangeException token) {
                throw new ProjectClientAuthenticationException(token.platformStatus(), null);
            }
            throw new ProjectClientAuthenticationException(PlatformCode.UPSTREAM_CALL_FAILED,
                    "Project authentication failed: " + e.getMessage(), e);
        }
    }

    private void throwIfAuthenticationFailure(Response response) {
        if (response.code() != 401 && response.code() != 403) {
            return;
        }
        try {
            JSONObject body = PlatformHttpResponses.read(response);
            throw new ProjectClientAuthenticationException(PlatformCode.find(body.getString("code")), body.getString("detail"));
        } catch (java.io.IOException e) {
            throw new ProjectClientAuthenticationException(PlatformCode.UPSTREAM_CALL_FAILED, "读取鉴权响应失败", e);
        }
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
        rule.setProjectId(obj.getLong("projectId"));
        rule.setRequiresServerExecution(obj.getBooleanValue("requiresServerExecution"));
        rule.setServerExecutionReason(obj.getString("serverExecutionReason"));
        rule.setVersion(obj.getIntValue("version"));
        rule.setRevisionId(obj.getLong("revisionId"));
        rule.setArtifactDigest(obj.getString("artifactDigest"));
        JSONArray frozenFunctions = obj.getJSONArray("frozenFunctions");
        if (frozenFunctions != null) rule.setFrozenFunctions(frozenFunctions.toJavaList(JSONObject.class));
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


}
