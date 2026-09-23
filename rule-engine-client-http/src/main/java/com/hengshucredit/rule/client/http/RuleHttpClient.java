package com.hengshucredit.rule.client.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import com.hengshucredit.rule.client.auth.ClientRequestAuthenticator;
import com.hengshucredit.rule.model.dto.RuleResult;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.Collections;
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

    public RuleResult execute(String ruleCode, Object params) {
        return execute(ruleCode, params, traceEnabled);
    }

    /** 单次追踪开关不修改客户端全局配置，支持并发调用。 */
    public RuleResult execute(String ruleCode, Object params, boolean collectTrace) {
        if (closed) throw new IllegalStateException("客户端已关闭");
        required(ruleCode, "ruleCode");
        if (".".equals(ruleCode) || "..".equals(ruleCode) || ruleCode.contains("/")) {
            throw new IllegalArgumentException("ruleCode 不能包含路径段");
        }
        Object input = params == null ? Collections.emptyMap() : JSON.toJSON(params);
        if (!(input instanceof java.util.Map)) throw new IllegalArgumentException("params 必须是对象或 Map");
        JSONObject body = new JSONObject();
        body.put("params", input);
        body.put("clientAppName", appName);
        body.put("traceEnabled", collectTrace);
        String prefix = baseUrl.encodedPath().replaceAll("/+$", "");
        HttpUrl url = baseUrl.newBuilder().encodedPath(prefix + "/api/rule/sync/execute/")
                .addPathSegment(ruleCode).build();
        Request request = new Request.Builder().url(url)
                .post(RequestBody.create(JSON.toJSONString(body), JSON_TYPE)).build();
        try {
            request = authenticator.authenticate(request);
        } catch (IOException e) {
            throw new RuleHttpException("项目鉴权失败，请检查凭据、Token 服务及网络", 0, null);
        }
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuleHttpException("引擎 HTTP 调用失败：" + response.code(), response.code(), null);
            }
            if (response.body() == null) throw invalidResponse(response.code());
            JSONObject envelope;
            try {
                envelope = JSON.parseObject(response.body().string());
            } catch (RuntimeException e) {
                throw invalidResponse(response.code());
            }
            if (envelope == null || !(envelope.get("code") instanceof Number)) throw invalidResponse(response.code());
            int code = envelope.getIntValue("code");
            if (code != 200) {
                throw new RuleHttpException("引擎拒绝请求，平台响应码：" + code, response.code(), code);
            }
            if (!(envelope.get("data") instanceof JSONObject)) throw invalidResponse(response.code());
            JSONObject data = envelope.getJSONObject("data");
            if (!(data.get("success") instanceof Boolean)) throw invalidResponse(response.code());
            try {
                return data.toJavaObject(RuleResult.class);
            } catch (RuntimeException e) {
                throw invalidResponse(response.code());
            }
        } catch (IOException e) {
            throw new RuleHttpException("引擎连接失败或超时；执行状态可能未知，请勿盲目重试", 0, null);
        }
    }

    private static RuleHttpException invalidResponse(int status) {
        return new RuleHttpException("引擎响应格式无效", status, null);
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
        public Builder timeoutMs(int value) { timeoutMs = value; return this; }
        public Builder traceEnabled(boolean value) { traceEnabled = value; return this; }
        public RuleHttpClient build() { return new RuleHttpClient(this); }
    }
}
