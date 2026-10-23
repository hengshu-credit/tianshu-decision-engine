package com.hengshucredit.rule.runtime;

import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import com.hengshucredit.rule.client.RuleEngineClient;
import com.hengshucredit.rule.client.RuleEngineClientConfig;
import com.hengshucredit.rule.client.log.ExecutionLogReporter;
import com.hengshucredit.rule.client.log.KafkaLogReporter;
import com.hengshucredit.rule.client.log.LogReportTransport;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.function.BiFunction;

/** 最小 SDK 样例：规则和函数来自服务端，不注册税率/评分 demo、不在本地写业务脚本。 */
@Configuration
@Profile("sdk")
public class SdkRuntimeConfiguration {
    @Bean(destroyMethod = "close")
    public RuleEngineClient sdkClient(Environment env, RedisConnectionFactory redis, ApplicationContext context,
                                      ObjectProvider<ExecutionLogReporter> reporterProvider) {
        String authType = env.getProperty("RULE_AUTH_TYPE", ClientAuthConfig.LEGACY_TOKEN)
                .toUpperCase(java.util.Locale.ROOT);
        boolean globalAccess = ClientAuthConfig.GLOBAL_KEY.equals(authType);
        long projectId = globalAccess ? 0L : env.getProperty("RULE_PROJECT_ID", Long.class, 0L);
        if (!globalAccess && projectId <= 0) throw new IllegalArgumentException("RULE_PROJECT_ID 必须为真实项目 ID");
        var codes = RuntimeConfiguration.allowedCodes(env);
        boolean logReportEnabled = env.getProperty("SDK_LOG_REPORT_ENABLED", Boolean.class, false);
        RuleEngineClient.Builder builder = RuleEngineClient.builder()
                .serverUrl(RuntimeConfiguration.required(env, "RULE_SERVER_URL"))
                .authConfig(RuntimeConfiguration.projectAuth(env))
                .projectCode(globalAccess ? null : RuntimeConfiguration.required(env, "RULE_PROJECT_CODE"))
                .projectId(projectId)
                .appName(env.getProperty("RUNTIME_APP_NAME", "sdk-runtime"))
                .httpTimeoutMs(env.getProperty("RULE_HTTP_TIMEOUT_MS", Integer.class, 30000))
                .l1CacheMaxSize(env.getProperty("SDK_CACHE_MAX_SIZE", Integer.class, 1000))
                .traceEnabled(env.getProperty("RULE_TRACE_ENABLED", Boolean.class, true))
                .logReportEnabled(logReportEnabled)
                .logReportTransport(env.getProperty("SDK_LOG_REPORT_TRANSPORT", LogReportTransport.class,
                        LogReportTransport.HTTP))
                .connectionFactory(redis).applicationContext(context)
                .serverSideExecution(false);
        if (logReportEnabled) builder.logReporter(reporterProvider.getIfAvailable());
        RuleEngineClient client = createClient(builder);
        try {
            // 按上游凭证同步可访问的规则和函数，并启动订阅、心跳与日志生命周期。
            // 入口白名单由 RuntimeController 执行，缓存也需要依赖规则，不能省略客户端启动。
            client.start();
            if (!globalAccess) {
                for (String code : codes) {
                    if (!executable(client, code)) {
                        throw new IllegalStateException("SDK 仅接受已发布的本地纯计算规则: " + code);
                    }
                    var rule = client.getRuleInfo(code);
                    if (rule.getProjectId() != null && rule.getProjectId() > 0
                            && (rule.getProjectId() != projectId
                            || !java.util.Objects.equals(rule.getProjectCode(), env.getProperty("RULE_PROJECT_CODE")))) {
                        throw new IllegalArgumentException("RULE_PROJECT_ID / RULE_PROJECT_CODE 与上游规则归属不一致，无法建立正确的项目订阅");
                    }
                }
            }
            return client;
        } catch (RuntimeException error) {
            client.close();
            throw error;
        }
    }

    RuleEngineClient createClient(RuleEngineClient.Builder builder) { return builder.build(); }

    @Configuration(proxyBeanMethods = false)
    @Profile("sdk")
    @ConditionalOnProperty(name = "SDK_LOG_REPORT_TRANSPORT", havingValue = "KAFKA")
    static class KafkaLogConfiguration {
        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean(ExecutionLogReporter.class)
        @ConditionalOnProperty(name = "SDK_LOG_REPORT_ENABLED", havingValue = "true")
        public ExecutionLogReporter sdkKafkaLogReporter(Environment env, KafkaTemplate<String, String> template) {
            RuleEngineClientConfig config = new RuleEngineClientConfig();
            config.setServerUrl(RuntimeConfiguration.required(env, "RULE_SERVER_URL"));
            config.setAuthConfig(RuntimeConfiguration.projectAuth(env));
            config.setHttpTimeoutMs(env.getProperty("RULE_HTTP_TIMEOUT_MS", Integer.class, 30000));
            config.setProjectId(env.getRequiredProperty("RULE_PROJECT_ID", Long.class));
            config.setKafkaLogTopic(env.getProperty("SDK_KAFKA_LOG_TOPIC", "rule-execution-log"));
            config.setKafkaHttpFallbackEnabled(env.getProperty("SDK_KAFKA_HTTP_FALLBACK_ENABLED", Boolean.class, true));
            return new KafkaLogReporter(template, config);
        }
    }

    @Bean
    public BiFunction<String, com.hengshucredit.rule.model.RuleExecutionRequest, RuleResult> sdkExecutor(RuleEngineClient client) {
        return client::executeRequest; // 在本进程执行已拉取脚本，绝不转发到服务端 execute。
    }

    @Bean
    public HealthIndicator sdkRulesHealthIndicator(RuleEngineClient client, Environment env) {
        boolean globalAccess = ClientAuthConfig.GLOBAL_KEY.equalsIgnoreCase(
                env.getProperty("RULE_AUTH_TYPE", ClientAuthConfig.LEGACY_TOKEN));
        var codes = RuntimeConfiguration.allowedCodes(env);
        return () -> globalAccess || codes.stream().allMatch(code -> executable(client, code))
                ? Health.up().build() : Health.down().build();
    }

    private static boolean executable(RuleEngineClient client, String code) {
        var rule = client.getRuleInfo(code);
        return rule != null && !rule.isRequiresServerExecution();
    }
}
