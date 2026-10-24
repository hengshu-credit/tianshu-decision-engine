package com.hengshucredit.rule.client.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import com.hengshucredit.rule.client.auth.ClientRequestAuthenticator;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteRequest;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteResult;
import com.hengshucredit.rule.model.dto.RuleExecutionStatus;
import com.hengshucredit.rule.model.PlatformCode;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 面向业务系统的线程安全 HTTP 客户端，仅访问执行和 Token 接口。
 * 不下载规则、函数、制品，不创建本地执行引擎，不连接 Redis/MySQL。
 */
public final class RuleHttpClient implements AutoCloseable {
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private final HttpUrl baseUrl;
    private final String appName;
    private final boolean traceEnabled;
    private final ClientRequestAuthenticator authenticator;
    private final OkHttpClient http;
    private volatile boolean closed;

    private RuleHttpClient(Builder builder) {
        baseUrl = HttpUrl.parse(required(builder.serverUrl, "serverUrl"));
        if (baseUrl == null || !baseUrl.username().isEmpty() || !baseUrl.password().isEmpty()
                || baseUrl.query() != null || baseUrl.fragment() != null) {
            throw new IllegalArgumentException("serverUrl 必须是无凭据、查询串及片段的 HTTP(S) 地址");
        }
        if (builder.timeoutMs <= 0) throw new IllegalArgumentException("timeoutMs 必须大于 0");
        appName = required(builder.appName, "appName");
        traceEnabled = builder.traceEnabled;
        if (builder.auth == null) throw new IllegalArgumentException("必须配置项目鉴权");
        ClientAuthConfig auth = JSON.parseObject(JSON.toJSONString(builder.auth), ClientAuthConfig.class);
        validateAuth(auth);
        authenticator = new ClientRequestAuthenticator(baseUrl.toString(), builder.timeoutMs, auth);
        http = new OkHttpClient.Builder()
                .connectTimeout(builder.timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(builder.timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(builder.timeoutMs, TimeUnit.MILLISECONDS)
                // 决策可能产生计费和外部调用，不自动重试或跟随重定向重放请求。
                .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
                .build();
    }

    public static Builder builder() { return new Builder(); }

    public com.hengshucredit.rule.model.ExternalCodeMapping fetchCodeMapping() {
        if (closed) throw new IllegalStateException("客户端已关闭");
        try {
            var url = baseUrl.newBuilder().addPathSegments("api/rule/sync/system-code-mapping").build();
            Request request = authenticator.authenticate(new Request.Builder().url(url).get().build());
            try (Response response = http.newCall(request).execute()) {
                if (response.code() == 404) return com.hengshucredit.rule.model.ExternalCodeMapping.DEFAULT;
                JSONObject body = PlatformHttpResponses.read(response);
                PlatformHttpResponses.requireSuccess(response, body);
                JSONObject data = body.getJSONObject("data");
                if (data == null) throw PlatformHttpResponses.invalid(response.code());
                return com.hengshucredit.rule.model.ExternalCodeMapping.from(data.getLongValue("version"), data);
            }
        } catch (IOException error) { throw new IllegalStateException("结果码映射读取失败", error); }
    }

    public RuleResult execute(String ruleCode, Object params) {
        return execute(ruleCode, params, traceEnabled);
    }

    /** 单次追踪开关不修改客户端全局配置，支持并发调用。 */
    public RuleResult executeRequest(String ruleCode, com.hengshucredit.rule.model.RuleExecutionRequest input) {
        return execute(ruleCode, input.getParams(), input.getTraceEnabled() == null ? traceEnabled : input.getTraceEnabled(),
                input.getClientAppName() == null ? appName : input.getClientAppName());
    }

    public RuleResult execute(String ruleCode, Object params, boolean collectTrace) {
        return execute(ruleCode, params, collectTrace, appName);
    }

    private RuleResult execute(String ruleCode, Object params, boolean collectTrace, String clientAppName) {
        if (closed) throw new IllegalStateException("客户端已关闭");
        required(ruleCode, "ruleCode");
        if (".".equals(ruleCode) || "..".equals(ruleCode) || ruleCode.contains("/")) {
            throw new IllegalArgumentException("ruleCode 不能包含路径段");
        }
        Object input = params == null ? Collections.emptyMap() : JSON.toJSON(params);
        if (!(input instanceof java.util.Map)) throw new IllegalArgumentException("params 必须是对象或 Map");
        JSONObject body = new JSONObject();
        body.put("params", input);
        body.put("clientAppName", clientAppName);
        body.put("traceEnabled", collectTrace);
        HttpUrl url = executionUrl("sync", ruleCode);
        Request request = new Request.Builder().url(url)
                .post(RequestBody.create(JSON.toJSONString(body), JSON_TYPE)).build();
        try {
            request = authenticator.authenticate(request);
        } catch (IOException e) {
            throw authenticationError(e);
        }
        try (Response response = http.newCall(request).execute()) {
            return PlatformHttpResponses.ruleResult(response, PlatformHttpResponses.read(response));
        } catch (IOException e) {
            throw new RuleHttpException("引擎连接失败或超时；执行状态可能未知，请勿盲目重试", 0, null);
        }
    }

    public RuleExperimentExecuteResult executeExperiment(String experimentCode,
                                                          RuleExperimentExecuteRequest request) {
        if (closed) throw new IllegalStateException("客户端已关闭");
        required(experimentCode, "experimentCode");
        JSONObject body = request == null ? new JSONObject() : JSON.parseObject(JSON.toJSONString(request));
        Request requestBuilder = new Request.Builder()
                .url(baseUrl.newBuilder().addPathSegments("api/rule/runtime/experiment/execute/")
                        .addPathSegment(experimentCode).build())
                .post(RequestBody.create(JSON.toJSONString(body), JSON_TYPE)).build();
        try {
            requestBuilder = authenticator.authenticate(requestBuilder);
        } catch (IOException e) {
            throw authenticationError(e);
        }
        try (Response response = http.newCall(requestBuilder).execute()) {
            return PlatformHttpResponses.experimentResult(response, PlatformHttpResponses.read(response));
        } catch (IOException e) {
            throw new RuleHttpException("引擎连接失败或超时；执行状态可能未知，请勿盲目重试", 0, null);
        }
    }

    /** 查询同一根 trace 的跨请求执行状态，供网络未知或执行中场景轮询。 */
    public RuleExecutionStatus getExecutionStatus(String traceId) {
        if (closed) throw new IllegalStateException("客户端已关闭");
        required(traceId, "traceId");
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("api").addPathSegment("rule").addPathSegment("runtime")
                .addPathSegment("executions").addPathSegment(traceId).build();
        Request request = new Request.Builder().url(url).get().build();
        try {
            request = authenticator.authenticate(request);
        } catch (IOException e) {
            throw authenticationError(e);
        }
        try (Response response = http.newCall(request).execute()) {
            JSONObject envelope = PlatformHttpResponses.read(response);
            return PlatformHttpResponses.executionStatus(response, envelope);
        } catch (IOException e) {
            throw new RuleHttpException("执行状态查询失败；状态可能未知，请稍后重试", 0, null,
                    "UNKNOWN", traceId, null);
        }
    }

    /** 使用原订单入参（可为空，服务端优先读取检查点）恢复等待中的规则。 */
    public RuleResult resumeExecution(String traceId, Map<String, Object> params) {
        if (closed) throw new IllegalStateException("客户端已关闭");
        required(traceId, "traceId");
        HttpUrl url = baseUrl.newBuilder().addPathSegment("api").addPathSegment("rule")
                .addPathSegment("runtime").addPathSegment("executions")
                .addPathSegment(traceId).addPathSegment("resume").build();
        Request request = new Request.Builder().url(url)
                .post(RequestBody.create(params == null ? new byte[0] : JSON.toJSONBytes(params), JSON_TYPE))
                .build();
        try {
            request = authenticator.authenticate(request);
        } catch (IOException e) {
            throw authenticationError(e);
        }
        try (Response response = http.newCall(request).execute()) {
            return PlatformHttpResponses.ruleResult(response, PlatformHttpResponses.read(response));
        } catch (IOException e) {
            throw new RuleHttpException("恢复规则执行失败；请继续使用 trace 查询状态", 0, null,
                    "UNKNOWN", traceId, null);
        }
    }

    private static RuleHttpException invalidResponse(int status) {
        return PlatformHttpResponses.invalid(status);
    }

    private static RuleHttpException authenticationError(IOException error) {
        if (error instanceof com.hengshucredit.rule.client.auth.ProjectTokenExchangeException token) {
            RuleHttpException mapped = new RuleHttpException(null, token.httpStatus(), token.platformStatus().code(),
                    "REJECTED", token.traceId(), null);
            mapped.mapExternal(token.mapping()); return mapped;
        }
        return new RuleHttpException("项目鉴权服务连接失败", 0, PlatformCode.UPSTREAM_CALL_FAILED.code(),
                "REJECTED", null, null);
    }

    private HttpUrl executionUrl(String kind, String code) {
        requiredPathSegment(code, kind + "Code");
        return baseUrl.newBuilder().addPathSegment("api").addPathSegment("rule")
                .addPathSegment("sync").addPathSegment("execute").addPathSegment(code).build();
    }

    private static void requiredPathSegment(String value, String name) {
        required(value, name);
        if (".".equals(value) || "..".equals(value) || value.contains("/") || value.contains("\\")) {
            throw new IllegalArgumentException(name + " 不能包含路径段");
        }
    }

    private static void validateAuth(ClientAuthConfig auth) {
        String type = required(auth.getAuthType(), "authType");
        if (ClientAuthConfig.LEGACY_TOKEN.equalsIgnoreCase(type)) required(auth.getLegacyToken(), "token");
        else if (ClientAuthConfig.BASIC.equalsIgnoreCase(type)) {
            required(auth.getUsername(), "username"); required(auth.getPassword(), "password");
        } else if (ClientAuthConfig.API_KEY.equalsIgnoreCase(type)) {
            required(auth.getApiKey(), "apiKey"); required(auth.getApiKeyParameterName(), "apiKeyParameterName");
            if (!"HEADER".equalsIgnoreCase(auth.getApiKeyPlacement()) && !"QUERY".equalsIgnoreCase(auth.getApiKeyPlacement())) {
                throw new IllegalArgumentException("apiKeyPlacement 必须为 HEADER 或 QUERY");
            }
        } else if (ClientAuthConfig.HMAC_SHA256.equalsIgnoreCase(type)) {
            required(auth.getAccessKey(), "accessKey"); required(auth.getHmacSecret(), "hmacSecret");
        } else if (ClientAuthConfig.GLOBAL_KEY.equalsIgnoreCase(type)) {
            required(auth.getGlobalKeyId(), "globalKeyId");
            required(auth.getGlobalPrivateKey(), "globalPrivateKey");
        } else throw new IllegalArgumentException("不支持的 authType");
    }

    private static String required(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " 不能为空");
        return value;
    }

    @Override public void close() {
        closed = true;
        http.dispatcher().cancelAll();
        http.connectionPool().evictAll();
        http.dispatcher().executorService().shutdown();
    }

    public static final class Builder {
        private String serverUrl;
        private String appName = "business-service";
        private ClientAuthConfig auth;
        private int timeoutMs = 10000;
        private boolean traceEnabled = false;
        public Builder serverUrl(String value) { serverUrl = value; return this; }
        public Builder appName(String value) { appName = value; return this; }
        public Builder authConfig(ClientAuthConfig value) { auth = value; return this; }
        public Builder token(String value) { return authConfig(ClientAuthConfig.legacyToken(value)); }
        public Builder globalKeyAuth(String keyId, String privateKey) {
            return authConfig(ClientAuthConfig.globalKey(keyId, privateKey));
        }
        public Builder globalKeyAuth(String privateKey) {
            return authConfig(ClientAuthConfig.globalKey(privateKey));
        }
        public Builder timeoutMs(int value) { timeoutMs = value; return this; }
        public Builder traceEnabled(boolean value) { traceEnabled = value; return this; }
        public RuleHttpClient build() { return new RuleHttpClient(this); }
    }
}
