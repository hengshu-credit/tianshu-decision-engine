package com.hengshucredit.rule.client.auth;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

public class TokenExchangeManager {

    private static final Logger log = LoggerFactory.getLogger(TokenExchangeManager.class);
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final String tokenUrl;
    private final ClientAuthConfig authConfig;
    private final OkHttpClient httpClient;
    private final ClientRequestAuthenticator baseAuthenticator;
    private TokenState currentToken;

    public TokenExchangeManager(String serverUrl, int timeoutMs, ClientAuthConfig authConfig) {
        String baseUrl = serverUrl.endsWith("/")
                ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.tokenUrl = baseUrl + "/api/rule/auth/token";
        this.authConfig = authConfig;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
        this.baseAuthenticator = new ClientRequestAuthenticator(authConfig, null);
    }

    public synchronized String getAccessToken() throws IOException {
        LocalDateTime now = currentDateTime();
        int refreshAhead = Math.max(0, authConfig.getRefreshAheadSeconds());
        if (currentToken != null && now.isBefore(currentToken.getExpiresAt().minusSeconds(refreshAhead))) {
            return currentToken.getAccessToken();
        }
        try {
            TokenState refreshed = requestToken();
            if (refreshed == null || refreshed.getAccessToken() == null
                    || refreshed.getExpiresAt() == null || refreshed.getGraceExpiresAt() == null) {
                throw new IOException("Token endpoint returned an incomplete token");
            }
            currentToken = refreshed;
            return currentToken.getAccessToken();
        } catch (IOException e) {
            if (currentToken != null && !now.isAfter(currentToken.getGraceExpiresAt())) {
                log.warn("Project token refresh failed; retaining the previous token during its grace period: {}",
                        e.getMessage());
                return currentToken.getAccessToken();
            }
            throw e;
        }
    }

    protected TokenState requestToken() throws IOException {
        Request request = new Request.Builder()
                .url(tokenUrl)
                .post(RequestBody.create("", JSON_TYPE))
                .build();
        request = baseAuthenticator.authenticateBase(request);
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.body() == null) throw new IOException("Token endpoint returned an empty response");
            JSONObject json = JSON.parseObject(response.body().string());
            if (!response.isSuccessful() || json.getIntValue("code") != 200 || json.get("data") == null) {
                String message = json.getString("message");
                throw new IOException(message == null ? "Token exchange failed" : message);
            }
            JSONObject data = json.getJSONObject("data");
            return new TokenState(data.getString("accessToken"),
                    parseTime(data.getString("expiresAt")),
                    parseTime(data.getString("graceExpiresAt")));
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("Unable to parse token endpoint response", e);
        }
    }

    protected LocalDateTime currentDateTime() {
        return LocalDateTime.now();
    }

    private LocalDateTime parseTime(String value) throws IOException {
        try {
            return value == null ? null : LocalDateTime.parse(value);
        } catch (RuntimeException e) {
            throw new IOException("Token endpoint returned an invalid expiration time", e);
        }
    }

    public static class TokenState {
        private final String accessToken;
        private final LocalDateTime expiresAt;
        private final LocalDateTime graceExpiresAt;

        public TokenState(String accessToken, LocalDateTime expiresAt, LocalDateTime graceExpiresAt) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
            this.graceExpiresAt = graceExpiresAt;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }

        public LocalDateTime getGraceExpiresAt() {
            return graceExpiresAt;
        }
    }
}
