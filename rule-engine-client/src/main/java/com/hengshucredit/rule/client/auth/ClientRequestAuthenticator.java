package com.hengshucredit.rule.client.auth;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okio.Buffer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

public class ClientRequestAuthenticator {

    private final ClientAuthConfig authConfig;
    private final TokenExchangeManager tokenExchangeManager;

    public ClientRequestAuthenticator(String serverUrl, int timeoutMs, ClientAuthConfig authConfig) {
        this(authConfig, authConfig != null && authConfig.isTokenExchangeEnabled()
                ? new TokenExchangeManager(serverUrl, timeoutMs, authConfig)
                : null);
    }

    ClientRequestAuthenticator(ClientAuthConfig authConfig, TokenExchangeManager tokenExchangeManager) {
        this.authConfig = authConfig;
        this.tokenExchangeManager = tokenExchangeManager;
    }

    public Request authenticate(Request request) throws IOException {
        if (authConfig == null) return request;
        if (tokenExchangeManager != null) {
            return request.newBuilder()
                    .header("Authorization", "Bearer " + tokenExchangeManager.getAccessToken())
                    .build();
        }
        return authenticateBase(request);
    }

    Request authenticateBase(Request request) throws IOException {
        String authType = required(authConfig.getAuthType(), "Authentication type is required")
                .toUpperCase(Locale.ROOT);
        Request.Builder builder = request.newBuilder();
        if (ClientAuthConfig.LEGACY_TOKEN.equals(authType)) {
            return builder.header("X-Rule-Token",
                    required(authConfig.getLegacyToken(), "Legacy token is required")).build();
        }
        if (ClientAuthConfig.BASIC.equals(authType)) {
            String credentials = required(authConfig.getUsername(), "Basic username is required") + ":"
                    + required(authConfig.getPassword(), "Basic password is required");
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            return builder.header("Authorization", "Basic " + encoded).build();
        }
        if (ClientAuthConfig.API_KEY.equals(authType)) {
            String name = required(authConfig.getApiKeyParameterName(), "API key parameter name is required");
            String value = required(authConfig.getApiKey(), "API key is required");
            if ("QUERY".equalsIgnoreCase(authConfig.getApiKeyPlacement())) {
                HttpUrl url = request.url().newBuilder().addQueryParameter(name, value).build();
                return builder.url(url).build();
            }
            return builder.header(name, value).build();
        }
        if (ClientAuthConfig.HMAC_SHA256.equals(authType)) {
            String accessKey = required(authConfig.getAccessKey(), "HMAC access key is required");
            String secret = required(authConfig.getHmacSecret(), "HMAC secret is required");
            String timestamp = String.valueOf(currentEpochSeconds());
            String nonce = generateNonce();
            String signature = sign(secret, request, timestamp, nonce);
            return builder.header("X-Rule-Access-Key", accessKey)
                    .header("X-Rule-Timestamp", timestamp)
                    .header("X-Rule-Nonce", nonce)
                    .header("X-Rule-Signature", signature)
                    .build();
        }
        throw new IOException("Unsupported authentication type: " + authType);
    }

    protected long currentEpochSeconds() {
        return Instant.now().getEpochSecond();
    }

    protected String generateNonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String sign(String secret, Request request, String timestamp, String nonce) throws IOException {
        byte[] body = new byte[0];
        if (request.body() != null) {
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            body = buffer.readByteArray();
        }
        String canonical = request.method() + "\n"
                + request.url().encodedPath() + "\n"
                + valueOrEmpty(request.url().encodedQuery()) + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + sha256Hex(body);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IOException("Unable to sign HMAC request", e);
        }
    }

    private String sha256Hex(byte[] value) throws IOException {
        try {
            return toHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IOException("Unable to hash request body", e);
        }
    }

    private String toHex(byte[] value) {
        StringBuilder hex = new StringBuilder(value.length * 2);
        for (byte b : value) hex.append(String.format("%02x", b & 0xff));
        return hex.toString();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String required(String value, String message) throws IOException {
        if (value == null || value.trim().isEmpty()) throw new IOException(message);
        return value;
    }
}
