package com.hengshucredit.rule.runtime;

import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@Configuration
public class RuntimeConfiguration {
    static ClientAuthConfig projectAuth(Environment env) {
        // SDK 运行时复用 Java SDK 的签名和 Token 交换协议。
        String type = env.getProperty("RULE_AUTH_TYPE", ClientAuthConfig.LEGACY_TOKEN)
                .toUpperCase(java.util.Locale.ROOT);
        ClientAuthConfig auth = switch (type) {
            case ClientAuthConfig.LEGACY_TOKEN -> ClientAuthConfig.legacyToken(required(env, "PROJECT_ACCESS_TOKEN"));
            case ClientAuthConfig.BASIC -> ClientAuthConfig.basic(required(env, "RULE_USERNAME"), required(env, "RULE_PASSWORD"));
            case ClientAuthConfig.API_KEY -> {
                String placement = env.getProperty("RULE_API_KEY_PLACEMENT", "HEADER");
                if (!"HEADER".equalsIgnoreCase(placement) && !"QUERY".equalsIgnoreCase(placement)) {
                    throw new IllegalArgumentException("RULE_API_KEY_PLACEMENT 必须为 HEADER 或 QUERY");
                }
                String name = env.getProperty("RULE_API_KEY_NAME", "X-Rule-Api-Key");
                if (name.isBlank()) throw new IllegalArgumentException("RULE_API_KEY_NAME 不能为空");
                yield ClientAuthConfig.apiKey(name, required(env, "RULE_API_KEY"), placement);
            }
            case ClientAuthConfig.HMAC_SHA256 -> ClientAuthConfig.hmac(required(env, "RULE_ACCESS_KEY"), required(env, "RULE_HMAC_SECRET"));
            case ClientAuthConfig.GLOBAL_KEY -> ClientAuthConfig.globalKey(
                    env.getProperty("RULE_GLOBAL_KEY_ID", "default"),
                    required(env, "RULE_GLOBAL_PRIVATE_KEY"));
            default -> throw new IllegalArgumentException("不支持的 RULE_AUTH_TYPE: " + type);
        };
        if (!ClientAuthConfig.LEGACY_TOKEN.equals(type)
                && !ClientAuthConfig.GLOBAL_KEY.equals(type)) {
            auth.setTokenExchangeEnabled(env.getProperty("RULE_TOKEN_EXCHANGE_ENABLED", Boolean.class, true));
        }
        return auth;
    }

    static java.util.List<String> allowedCodes(Environment env) {
        String authType = env.getProperty("RULE_AUTH_TYPE", ClientAuthConfig.LEGACY_TOKEN);
        if (ClientAuthConfig.GLOBAL_KEY.equalsIgnoreCase(authType)
                && !org.springframework.util.StringUtils.hasText(env.getProperty("RULE_ALLOWED_CODES"))) {
            return java.util.List.of();
        }
        var codes = Arrays.stream(required(env, "RULE_ALLOWED_CODES").split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        if (codes.isEmpty()) throw new IllegalArgumentException("RULE_ALLOWED_CODES 不能为空");
        return codes;
    }

    static String required(Environment env, String key) {
        String value = env.getRequiredProperty(key);
        if (value.isBlank() || value.startsWith("replace-with-")) {
            throw new IllegalArgumentException(key + " 必须配置真实值");
        }
        return value;
    }
}
