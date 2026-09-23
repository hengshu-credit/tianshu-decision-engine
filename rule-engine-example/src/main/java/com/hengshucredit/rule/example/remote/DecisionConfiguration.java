package com.hengshucredit.rule.example.remote;

import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import com.hengshucredit.rule.client.http.RuleHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DecisionConfiguration {
    @Bean(destroyMethod = "close")
    public RuleHttpClient ruleHttpClient(Environment env) {
        String type = env.getProperty("RULE_AUTH_TYPE", "LEGACY_TOKEN");
        ClientAuthConfig auth;
        switch (type.toUpperCase(java.util.Locale.ROOT)) {
            case "LEGACY_TOKEN": auth = ClientAuthConfig.legacyToken(env.getRequiredProperty("PROJECT_ACCESS_TOKEN")); break;
            case "BASIC": auth = ClientAuthConfig.basic(env.getRequiredProperty("RULE_USERNAME"), env.getRequiredProperty("RULE_PASSWORD")); break;
            case "API_KEY": auth = ClientAuthConfig.apiKey(env.getProperty("RULE_API_KEY_NAME", "X-Rule-Api-Key"),
                    env.getRequiredProperty("RULE_API_KEY"), env.getProperty("RULE_API_KEY_PLACEMENT", "HEADER")); break;
            case "HMAC_SHA256": auth = ClientAuthConfig.hmac(env.getRequiredProperty("RULE_ACCESS_KEY"), env.getRequiredProperty("RULE_HMAC_SECRET")); break;
            default: throw new IllegalArgumentException("不支持的 RULE_AUTH_TYPE");
        }
        if (!ClientAuthConfig.LEGACY_TOKEN.equals(auth.getAuthType())) {
            auth.setTokenExchangeEnabled(env.getProperty("RULE_TOKEN_EXCHANGE_ENABLED", Boolean.class, true));
        }
        return RuleHttpClient.builder()
                .serverUrl(env.getRequiredProperty("RULE_SERVER_URL"))
                .appName(env.getProperty("RULE_CLIENT_APP_NAME", "business-service"))
                .authConfig(auth)
                .timeoutMs(env.getProperty("RULE_HTTP_TIMEOUT_MS", Integer.class, 10000))
                .traceEnabled(env.getProperty("RULE_TRACE_ENABLED", Boolean.class, false))
                .build();
    }
}
