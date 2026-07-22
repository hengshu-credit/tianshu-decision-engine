package com.hengshucredit.rule.client.sync;

import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import com.hengshucredit.rule.client.auth.ClientRequestAuthenticator;
import com.hengshucredit.rule.client.auth.ProjectClientAuthenticationException;
import com.hengshucredit.rule.client.cache.CachedRule;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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
        this.authenticator = authenticator;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    public CachedRule fetchRule(String ruleCode) {
        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(serverUrl + "/api/rule/sync/" + ruleCode)
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
            log.warn("Failed to fetch rule {}: {}", ruleCode, e.getMessage());
        }
        return null;
    }

    public List<CachedRule> fetchAll() {
        List<CachedRule> rules = new ArrayList<>();
        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(serverUrl + "/api/rule/sync/all")
                    .get();
            Request request = authenticate(requestBuilder.build());
            try (Response response = httpClient.newCall(request).execute()) {
                throwIfAuthenticationFailure(response);
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject json = JSON.parseObject(response.body().string());
                    if (json.getIntValue("code") == 200) {
                        JSONArray arr = json.getJSONArray("data");
                        if (arr != null) {
                            for (int i = 0; i < arr.size(); i++) {
                                CachedRule r = toCachedRule(arr.getJSONObject(i));
                                if (r != null) rules.add(r);
                            }
                        }
                    }
                }
            }
        } catch (ProjectClientAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch all rules: {}", e.getMessage());
        }
        return rules;
    }

    /**
     * 从服务端拉取项目函数列表（JAVA/BEAN/SCRIPT 类型）
     *
     * @param projectId 项目 ID
     * @return 函数元数据 JSON 列表
     */
    public List<JSONObject> fetchFunctions(long projectId) {
        List<JSONObject> functions = new ArrayList<>();
        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(serverUrl + "/api/rule/sync/functions/" + projectId)
                    .get();
            Request request = authenticate(requestBuilder.build());
            try (Response response = httpClient.newCall(request).execute()) {
                throwIfAuthenticationFailure(response);
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject json = JSON.parseObject(response.body().string());
                    if (json.getIntValue("code") == 200) {
                        JSONArray arr = json.getJSONArray("data");
                        if (arr != null) {
                            for (int i = 0; i < arr.size(); i++) {
                                functions.add(arr.getJSONObject(i));
                            }
                        }
                    }
                }
            }
        } catch (ProjectClientAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch functions for project {}: {}", projectId, e.getMessage());
        }
        return functions;
    }

    public RuleResult executeRule(String ruleCode, Object params, String clientAppName) {
        try {
            JSONObject body = new JSONObject();
            body.put("params", params);
            body.put("clientAppName", clientAppName);
            RequestBody requestBody = RequestBody.create(JSON.toJSONString(body), JSON_MEDIA_TYPE);
            Request.Builder requestBuilder = new Request.Builder()
                    .url(serverUrl + "/api/rule/sync/execute/" + ruleCode)
                    .post(requestBody);
            Request request = authenticate(requestBuilder.build());
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.body() == null) {
                    return failure("Empty response from rule server");
                }
                JSONObject json = JSON.parseObject(response.body().string());
                if (response.isSuccessful() && json.getIntValue("code") == 200 && json.get("data") != null) {
                    return json.getJSONObject("data").toJavaObject(RuleResult.class);
                }
                return failure(json.getString("message"));
            }
        } catch (Exception e) {
            log.warn("Failed to execute rule {} on server: {}", ruleCode, e.getMessage());
            return failure(e.getMessage());
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
        rule.setProjectCode(obj.getString("projectCode"));
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
        RuleResult result = new RuleResult();
        result.setSuccess(false);
        result.setErrorMessage(message == null || message.isEmpty() ? "Rule server execution failed" : message);
        return result;
    }
}
