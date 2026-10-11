package com.hengshucredit.rule.server.consumer;

import com.hengshucredit.rule.server.service.ClientLogValidationException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class KafkaLogConfigurationTest {
    @Test
    public void enabledBeansWireTogetherAndMissingBindingsFailAtStartup() {
        try (AnnotationConfigApplicationContext context = context(true)) {
            context.refresh();
            assertNotNull(context.getBean("kafkaLogListenerContainerFactory"));
            assertNotNull(context.getBean("kafkaLogErrorHandler"));
            assertNotNull(context.getBean(KafkaLogConsumer.class));
            assertArrayEquals(new String[]{"logs"}, context.getBean(RuleKafkaLogProperties.class).topics());
        }
        try (AnnotationConfigApplicationContext context = context(false)) {
            assertThrows(org.springframework.beans.BeansException.class, context::refresh);
        }
    }

    private AnnotationConfigApplicationContext context(boolean includeBinding) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        java.util.Map<String, Object> settings = new java.util.HashMap<>();
        settings.put("rule-engine.kafka.enabled", "true");
        settings.put("spring.kafka.bootstrap-servers[0]", "localhost:9092");
        if (includeBinding) settings.put("rule-engine.kafka.topic-projects.logs", "1");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", settings));
        context.registerBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new);
        context.getBeanFactory().registerSingleton("ingestionService",
                new com.hengshucredit.rule.server.service.ClientExecutionLogIngestionService());
        context.register(KafkaLogConfiguration.class, KafkaLogConsumer.class);
        return context;
    }

    @Test
    public void dedicatedFactoryIgnoresGlobalBatchAndSerializerOverridesAndAcceptsYamlList() {
        KafkaLogConfiguration config = new KafkaLogConfiguration();
        KafkaProperties kafka = new KafkaProperties();
        kafka.getListener().setType(KafkaProperties.Listener.Type.BATCH);
        kafka.getConsumer().setEnableAutoCommit(true);
        kafka.getConsumer().setKeyDeserializer(org.apache.kafka.common.serialization.ByteArrayDeserializer.class);
        kafka.getProducer().setValueSerializer(org.apache.kafka.common.serialization.ByteArraySerializer.class);
        RuleKafkaLogProperties properties = properties();
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("spring.kafka.bootstrap-servers[0]", "localhost:9092")));
        var bundles = new DefaultListableBeanFactory().getBeanProvider(SslBundles.class);
        var factory = config.kafkaLogListenerContainerFactory(kafka, properties, environment, bundles,
                new DefaultErrorHandler());
        assertEquals(Boolean.FALSE, factory.isBatchListener());
        assertEquals(ContainerProperties.AckMode.RECORD, factory.getContainerProperties().getAckMode());
        Map<String, Object> consumer = factory.getConsumerFactory().getConfigurationProperties();
        assertEquals(false, consumer.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
        assertEquals(StringDeserializer.class, consumer.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
        assertEquals(StringDeserializer.class, consumer.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals(false, consumer.get(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG));

        var producer = config.kafkaLogDltProducerFactory(kafka, properties, environment, bundles);
        try {
            Map<String, Object> producerConfig = producer.getConfigurationProperties();
            assertEquals(StringSerializer.class, producerConfig.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
            assertEquals(StringSerializer.class, producerConfig.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
            assertEquals("all", producerConfig.get(ProducerConfig.ACKS_CONFIG));
        } finally {
            producer.destroy();
        }
    }

    @Test
    public void enabledConfigurationCannotRelyOnBootDefaultBrokerOrUnboundTopics() {
        RuleKafkaLogProperties properties = properties();
        assertFalse(properties.isEnabled());
        assertThrows(IllegalArgumentException.class, () -> properties.validate(null));
        properties.getTopicProjects().clear();
        assertThrows(IllegalArgumentException.class, () -> properties.validate("localhost:9092"));
        properties.getTopicProjects().put("logs.DLT", 1L);
        assertThrows(IllegalArgumentException.class, () -> properties.validate("localhost:9092"));
        properties.getTopicProjects().clear();
        properties.getTopicProjects().put("logs", 0L);
        assertThrows(IllegalArgumentException.class, () -> properties.validate("localhost:9092"));
    }

    @Test
    public void transientFailureRetriesTwiceThenRequiresConfirmedDeadLetter() {
        Fixture f = new Fixture();
        var record = KafkaLogConsumerTest.record("logs", "1", "secret-customer-value");
        var failure = new DataAccessResourceFailureException("database down");
        try (MockConsumer<String, String> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST)) {
            assertFalse(f.handler.handleOne(failure, record, consumer, null));
            assertFalse(f.handler.handleOne(failure, record, consumer, null));
            assertEquals(0, f.template.sends.get());
            assertTrue(f.handler.handleOne(failure, record, consumer, null));
            assertEquals(1, f.template.sends.get());
            assertEquals("logs.DLT", f.template.last.topic());
            assertEquals("secret-customer-value", f.template.last.value());
            assertEquals(2, f.count("retry"), 0);
            assertEquals(1, f.count("dead_letter"), 0);
        }
    }

    @Test
    public void malformedInputGoesDirectlyToDeadLetterButFailedSendCannotBeAcknowledged() {
        Fixture f = new Fixture();
        var record = KafkaLogConsumerTest.record("logs", "1", "secret-customer-value");
        f.template.fail = true;
        try (MockConsumer<String, String> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST)) {
            assertFalse("死信发送失败不能被视为已恢复", f.handler.handleOne(
                    new ClientLogValidationException(400, "invalid"), record, consumer, null));
            assertEquals(1, f.template.sends.get());
            assertEquals(0, f.count("dead_letter"), 0);
            assertEquals(1, f.count("dead_letter_failure"), 0);

            f.template.fail = false;
            assertTrue(f.handler.handleOne(new ClientLogValidationException(400, "invalid"),
                    record, consumer, null));
            assertEquals(2, f.template.sends.get());
            assertEquals(1, f.count("dead_letter"), 0);
        }
    }

    private static RuleKafkaLogProperties properties() {
        RuleKafkaLogProperties properties = new RuleKafkaLogProperties();
        properties.getTopicProjects().put("logs", 1L);
        properties.setRetryIntervalMs(0);
        return properties;
    }

    private static class Fixture {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final RecordingTemplate template = new RecordingTemplate();
        final DefaultErrorHandler handler;
        Fixture() {
            KafkaLogConfiguration config = new KafkaLogConfiguration();
            handler = config.kafkaLogErrorHandler(config.kafkaLogRecoverer(template), properties(),
                    new KafkaLogMetrics(registry));
        }
        double count(String outcome) {
            var counter = registry.find("rule.engine.kafka.log.records").tag("outcome", outcome).counter();
            return counter == null ? 0 : counter.count();
        }
    }

    private static class RecordingTemplate extends KafkaTemplate<String, String> {
        final AtomicInteger sends = new AtomicInteger();
        ProducerRecord<String, String> last;
        boolean fail;
        RecordingTemplate() {
            super(new DefaultKafkaProducerFactory<>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
        }
        @Override public CompletableFuture<SendResult<String, String>> send(ProducerRecord<String, String> record) {
            sends.incrementAndGet();
            last = record;
            if (fail) return CompletableFuture.failedFuture(new IllegalStateException("send failed"));
            return CompletableFuture.completedFuture(new SendResult<>(record, null));
        }
    }
}

