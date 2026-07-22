package com.hengshucredit.rule.client.spring;

import com.hengshucredit.rule.client.RuleEngineClient;
import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import com.hengshucredit.rule.client.log.ExecutionLogReporter;
import com.hengshucredit.rule.client.log.KafkaLogReporter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;

@AutoConfiguration(after = {RedisAutoConfiguration.class, KafkaAutoConfiguration.class})
@ConditionalOnProperty(prefix = "rule-engine.client", name = "server-url")
public class RuleEngineAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "rule-engine.client")
    public RuleEngineClientProperties ruleEngineClientProperties() {
        return new RuleEngineClientProperties();
    }

    @Configuration
    @ConditionalOnClass(KafkaTemplate.class)
    @ConditionalOnBean(KafkaTemplate.class)
    static class KafkaLogReporterConfiguration {

        @Bean
        @ConditionalOnMissingBean(ExecutionLogReporter.class)
        public ExecutionLogReporter kafkaLogReporter(KafkaTemplate<String, String> kafkaTemplate,
                                                     RuleEngineClientProperties props) {
            return new KafkaLogReporter(kafkaTemplate, props.getKafkaLogTopic());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RuleEngineClient ruleEngineClient(RuleEngineClientProperties props,
                                              RedisConnectionFactory connectionFactory,
                                              ApplicationContext applicationContext,
                                              ObjectProvider<ExecutionLogReporter> logReporterProvider) {
        RuleEngineClient.Builder builder = RuleEngineClient.builder()
                .serverUrl(props.getServerUrl())
                .appName(props.getAppName())
                .projectCode(props.getProjectCode())
                .token(props.getToken())
                .authConfig(props.toAuthConfig())
                .connectionFactory(connectionFactory)
                .applicationContext(applicationContext)
                .l1CacheMaxSize(props.getL1CacheMaxSize())
                .httpTimeoutMs(props.getHttpTimeoutMs())
                .projectId(props.getProjectId())
                .traceEnabled(props.isTraceEnabled())
                .serverSideExecution(props.isServerSideExecution());

        ExecutionLogReporter reporter = logReporterProvider.getIfAvailable();
        if (reporter != null && shouldUseExternalReporter(props)) {
            builder.logReporter(reporter);
        }

        RuleEngineClient client = builder.build();
        client.start();
        return client;
    }

    static boolean shouldUseExternalReporter(RuleEngineClientProperties properties) {
        String authType = properties.getAuthType();
        return !hasText(properties.getToken())
                && !ClientAuthConfig.LEGACY_TOKEN.equalsIgnoreCase(authType)
                && !ClientAuthConfig.BASIC.equalsIgnoreCase(authType)
                && !ClientAuthConfig.API_KEY.equalsIgnoreCase(authType)
                && !ClientAuthConfig.HMAC_SHA256.equalsIgnoreCase(authType);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
