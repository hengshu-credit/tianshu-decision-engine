package com.hengshucredit.rule.runtime;

import com.hengshucredit.rule.client.RuleEngineClient;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.Map;
import java.util.function.BiFunction;

/** 最小 SDK 样例：规则和函数来自服务端，不注册税率/评分 demo、不在本地写业务脚本。 */
@Configuration
@Profile("sdk")
public class SdkRuntimeConfiguration {
    @Bean(destroyMethod = "close")
    public RuleEngineClient sdkClient(Environment env, RedisConnectionFactory redis, ApplicationContext context) {
        long projectId = env.getRequiredProperty("RULE_PROJECT_ID", Long.class);
        if (projectId <= 0) throw new IllegalArgumentException("RULE_PROJECT_ID 必须为真实项目 ID");
        var codes = RuntimeConfiguration.allowedCodes(env);
        RuleEngineClient client = RuleEngineClient.builder()
                .serverUrl(RuntimeConfiguration.required(env, "RULE_SERVER_URL"))
                .authConfig(RuntimeConfiguration.projectAuth(env))
                .projectCode(RuntimeConfiguration.required(env, "RULE_PROJECT_CODE")).projectId(projectId)
                .appName(env.getProperty("RUNTIME_APP_NAME", "sdk-runtime"))
                .httpTimeoutMs(env.getProperty("RULE_HTTP_TIMEOUT_MS", Integer.class, 30000))
                .l1CacheMaxSize(env.getProperty("SDK_CACHE_MAX_SIZE", Integer.class, 1000))
                .traceEnabled(env.getProperty("RULE_TRACE_ENABLED", Boolean.class, true))
                .logReportEnabled(env.getProperty("SDK_LOG_REPORT_ENABLED", Boolean.class, false))
                .connectionFactory(redis).applicationContext(context)
                .serverSideExecution(false).build();
        try {
            // 最小样例只按白名单逐条 HTTP 拉取规则，不启动函数同步、定时全量同步或 Redis 推送订阅。
            // 这样可以明确展示“服务端规则 -> SDK L1 缓存 -> 本地执行”的链路。
            for (String code : codes) {
                client.refreshRule(code);
                if (!executable(client, code)) {
                    throw new IllegalStateException("SDK 仅接受已发布的本地纯计算规则: " + code);
                }
            }
            return client;
        } catch (RuntimeException error) {
            client.close();
            throw error;
        }
    }

    @Bean
    public BiFunction<String, Map<String, Object>, RuleResult> sdkExecutor(RuleEngineClient client) {
        return client::execute; // 在本进程执行已拉取脚本，绝不转发到服务端 execute。
    }

    @Bean
    public HealthIndicator sdkRulesHealthIndicator(RuleEngineClient client, Environment env) {
        var codes = RuntimeConfiguration.allowedCodes(env);
        return () -> codes.stream().allMatch(code -> executable(client, code))
                ? Health.up().build() : Health.down().build();
    }

    private static boolean executable(RuleEngineClient client, String code) {
        var rule = client.getRuleInfo(code);
        return rule != null && !rule.isRequiresServerExecution();
    }
}
