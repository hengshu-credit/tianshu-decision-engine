package com.hengshucredit.rule.runtime;

import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import com.hengshucredit.rule.client.http.RuleHttpClient;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BiFunction;

@Configuration
public class RuntimeConfiguration {
    @Bean(destroyMethod = "close")
    @Profile("http")
    public RuleHttpClient httpClient(Environment env) {
        return RuleHttpClient.builder()
                .serverUrl(required(env, "RULE_SERVER_URL"))
                .authConfig(projectAuth(env))
                .appName(env.getProperty("RUNTIME_APP_NAME", "http-runtime"))
                .timeoutMs(env.getProperty("RULE_HTTP_TIMEOUT_MS", Integer.class, 30000))
                .traceEnabled(env.getProperty("RULE_TRACE_ENABLED", Boolean.class, true))
                .build();
    }

    @Bean
    @Profile("http")
    public BiFunction<String, Map<String, Object>, RuleResult> httpExecutor(RuleHttpClient client) {
        return client::execute;
    }

    @Bean
    @Profile("http")
    public HealthIndicator upstreamHealthIndicator(Environment env) {
        URI uri = URI.create(required(env, "RULE_SERVER_URL").replaceAll("/+$", "")
                + "/actuator/health/readiness");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        return () -> {
            try {
                var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
                return response.statusCode() == 200 ? Health.up().build() : Health.down().build();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return Health.down().build();
            } catch (java.io.IOException error) {
                return Health.down().build();
            }
        };
    }

    static ClientAuthConfig projectAuth(Environment env) {
        // HTTP 运行时与 HTTP SDK 使用同一组鉴权工厂；不实现另一套签名/Token 交换协议。
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
            default -> throw new IllegalArgumentException("不支持的 RULE_AUTH_TYPE: " + type);
        };
        if (!ClientAuthConfig.LEGACY_TOKEN.equals(type)) {
            auth.setTokenExchangeEnabled(env.getProperty("RULE_TOKEN_EXCHANGE_ENABLED", Boolean.class, true));
        }
        return auth;
    }

    static java.util.List<String> allowedCodes(Environment env) {
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
