package com.hengshucredit.rule.client.auth;

import lombok.Data;

@Data
public class ClientAuthConfig {

    public static final String LEGACY_TOKEN = "LEGACY_TOKEN";
    public static final String BASIC = "BASIC";
    public static final String API_KEY = "API_KEY";
    public static final String HMAC_SHA256 = "HMAC_SHA256";

    private String authType;
    private String legacyToken;
    private String username;
    private String password;
    private String apiKey;
    private String apiKeyPlacement = "HEADER";
    private String apiKeyParameterName = "X-Rule-Api-Key";
    private String accessKey;
    private String hmacSecret;
    private boolean tokenExchangeEnabled = true;
    private int refreshAheadSeconds = 60;

    public static ClientAuthConfig legacyToken(String token) {
        ClientAuthConfig config = new ClientAuthConfig();
        config.setAuthType(LEGACY_TOKEN);
        config.setLegacyToken(token);
        config.setTokenExchangeEnabled(false);
        return config;
    }

    public static ClientAuthConfig basic(String username, String password) {
        ClientAuthConfig config = new ClientAuthConfig();
        config.setAuthType(BASIC);
        config.setUsername(username);
        config.setPassword(password);
        return config;
    }

    public static ClientAuthConfig apiKey(String parameterName, String apiKey, String placement) {
        ClientAuthConfig config = new ClientAuthConfig();
        config.setAuthType(API_KEY);
        config.setApiKeyParameterName(parameterName);
        config.setApiKey(apiKey);
        config.setApiKeyPlacement(placement);
        return config;
    }

    public static ClientAuthConfig hmac(String accessKey, String hmacSecret) {
        ClientAuthConfig config = new ClientAuthConfig();
        config.setAuthType(HMAC_SHA256);
        config.setAccessKey(accessKey);
        config.setHmacSecret(hmacSecret);
        return config;
    }
}
