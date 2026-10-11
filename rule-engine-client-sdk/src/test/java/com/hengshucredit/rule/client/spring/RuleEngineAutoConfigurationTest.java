package com.hengshucredit.rule.client.spring;

import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import com.hengshucredit.rule.client.log.ExecutionLogReporter;
import com.hengshucredit.rule.client.log.KafkaLogReporter;
import com.hengshucredit.rule.client.log.LogReportTransport;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.Collections;
import java.util.Map;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class RuleEngineAutoConfigurationTest {

    @Test
    public void unrelatedKafkaTemplateDoesNotEnableKafkaLoggingByDefault() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RuleEngineClientProperties.class, RuleEngineClientProperties::new);
            context.registerBean(KafkaTemplate.class,
                    () -> new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Collections.emptyMap())));
            context.register(RuleEngineAutoConfiguration.KafkaLogReporterConfiguration.class);
            context.refresh();

            assertFalse(context.containsBean("kafkaLogReporter"));
        }
    }

    @Test
    public void explicitKafkaCreatesOneManagedReporter() {
        try (AnnotationConfigApplicationContext context = kafkaContext(Collections.emptyMap())) {
            context.refresh();

            assertTrue(context.getBean(ExecutionLogReporter.class) instanceof KafkaLogReporter);
            assertEquals(1, context.getBeansOfType(ExecutionLogReporter.class).size());
        }
    }

    @Test
    public void customReporterHasPriorityEvenWhenKafkaIsSelected() {
        ExecutionLogReporter custom = logs -> { };
        try (AnnotationConfigApplicationContext context = kafkaContext(Collections.emptyMap())) {
            context.registerBean("customReporter", ExecutionLogReporter.class, () -> custom);
            context.refresh();

            assertFalse(context.containsBean("kafkaLogReporter"));
            assertSame(custom, context.getBean(ExecutionLogReporter.class));
        }
    }

    @Test
    public void disabledLogsAndServerExecutionDoNotAllocateKafkaResources() {
        for (Map<String, Object> overrides : java.util.List.of(
                Map.<String, Object>of("rule-engine.client.log-report-enabled", "false"),
                Map.<String, Object>of("rule-engine.client.server-side-execution", "true"))) {
            try (AnnotationConfigApplicationContext context = kafkaContext(overrides)) {
                context.refresh();
                assertFalse(context.containsBean("kafkaLogReporter"));
            }
        }
    }

    @Test
    public void missingKafkaReporterFailsClearlyBeforeStartingTheClient() {
        RuleEngineClientProperties properties = new RuleEngineClientProperties();
        properties.setLogReportTransport(LogReportTransport.KAFKA);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> new RuleEngineAutoConfiguration().ruleEngineClient(properties,
                            context.getBeanProvider(RedisConnectionFactory.class), context,
                            context.getBeanProvider(ExecutionLogReporter.class)));
            assertTrue(error.getMessage().contains("KafkaTemplate"));
        }
    }

    private AnnotationConfigApplicationContext kafkaContext(Map<String, Object> overrides) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        Map<String, Object> values = new java.util.HashMap<>(overrides);
        values.put("rule-engine.client.log-report-transport", "KAFKA");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", values));
        context.registerBean(RuleEngineClientProperties.class, () -> {
            RuleEngineClientProperties properties = new RuleEngineClientProperties();
            properties.setLogReportTransport(LogReportTransport.KAFKA);
            properties.setProjectId(18L);
            properties.setKafkaHttpFallbackEnabled(false);
            return properties;
        });
        context.registerBean(KafkaTemplate.class,
                () -> new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Collections.emptyMap())));
        context.register(RuleEngineAutoConfiguration.KafkaLogReporterConfiguration.class);
        return context;
    }

    @Test
    public void registersAutoConfigurationWithSpringBoot3Imports() throws Exception {
        String resource = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        Enumeration<URL> resources = RuleEngineAutoConfigurationTest.class.getClassLoader().getResources(resource);
        boolean registered = false;
        while (resources.hasMoreElements()) {
            try (InputStream input = resources.nextElement().openStream()) {
                String imports = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));
                registered = registered || imports.contains(RuleEngineAutoConfiguration.class.getName());
            }
        }

        assertTrue("RuleEngineAutoConfiguration is not registered for Spring Boot 3", registered);
    }

    @Test
    public void disabledLoggingDoesNotUseExternalReporter() {
        RuleEngineClientProperties properties = new RuleEngineClientProperties();
        properties.setLogReportEnabled(false);

        assertFalse(RuleEngineAutoConfiguration.shouldUseExternalReporter(properties));
    }

    @Test
    public void serverSideExecutionDoesNotUseExternalReporter() {
        RuleEngineClientProperties properties = new RuleEngineClientProperties();
        properties.setServerSideExecution(true);

        assertFalse(RuleEngineAutoConfiguration.shouldUseExternalReporter(properties));
    }

    @Test
    public void unusedLazyReporterIsNotInstantiatedForServerExecution() {
        RuleEngineClientProperties properties = new RuleEngineClientProperties();
        properties.setServerUrl("http://127.0.0.1:8080");
        properties.setServerSideExecution(true);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("lazyReporter", ExecutionLogReporter.class, () -> {
                throw new IllegalStateException("unused reporter must not be initialized");
            }, definition -> definition.setLazyInit(true));
            context.refresh();
            com.hengshucredit.rule.client.RuleEngineClient client = new RuleEngineAutoConfiguration()
                    .ruleEngineClient(properties, context.getBeanProvider(RedisConnectionFactory.class),
                            context, context.getBeanProvider(ExecutionLogReporter.class));
            client.close();
            assertFalse(context.getBeanFactory().containsSingleton("lazyReporter"));
        }
    }

    @Test
    public void authenticatedConfigurationStillUsesProvidedKafkaReporter() {
        RuleEngineClientProperties properties = new RuleEngineClientProperties();
        properties.setAuthType(ClientAuthConfig.BASIC);

        assertTrue(RuleEngineAutoConfiguration.shouldUseExternalReporter(properties));
    }

    @Test
    public void legacyTokenConfigurationStillUsesProvidedKafkaReporter() {
        RuleEngineClientProperties properties = new RuleEngineClientProperties();
        properties.setToken("legacy-token");

        assertTrue(RuleEngineAutoConfiguration.shouldUseExternalReporter(properties));
    }

    @Test
    public void unauthenticatedConfigurationMayUseExternalReporter() {
        assertTrue(RuleEngineAutoConfiguration.shouldUseExternalReporter(new RuleEngineClientProperties()));
    }
}
