package com.hengshucredit.rule.server.consumer;

import com.hengshucredit.rule.server.service.ClientLogValidationException;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "rule-engine.kafka", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaLogConfiguration {
    private static final Logger log = LoggerFactory.getLogger(KafkaLogConfiguration.class);

    @Bean
    @org.springframework.boot.context.properties.ConfigurationProperties(prefix = "rule-engine.kafka")
    public RuleKafkaLogProperties kafkaLogProperties() {
        return new RuleKafkaLogProperties();
    }

    @Bean
    public KafkaLogMetrics kafkaLogMetrics(MeterRegistry registry) {
        return new KafkaLogMetrics(registry);
    }

    @Bean(destroyMethod = "destroy")
    public DefaultKafkaProducerFactory<String, String> kafkaLogDltProducerFactory(
            KafkaProperties kafka, RuleKafkaLogProperties properties, Environment environment,
            ObjectProvider<SslBundles> bundles) {
        validate(properties, environment);
        Map<String, Object> config = new HashMap<>(kafka.buildProducerProperties(bundles.getIfAvailable()));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10000);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaLogDltTemplate(
            @Qualifier("kafkaLogDltProducerFactory") DefaultKafkaProducerFactory<String, String> factory) {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(factory);
        // 默认 ProducerListener 可能输出 value，执行日志仅记录位置。
        template.setProducerListener(new ProducerListener<>() {
            @Override
            public void onError(ProducerRecord<String, String> record, RecordMetadata metadata, Exception error) {
                log.warn("Kafka DLT send failed: topic={}, partition={}", record.topic(), record.partition());
            }
        });
        return template;
    }

    @Bean
    public DeadLetterPublishingRecoverer kafkaLogRecoverer(
            @Qualifier("kafkaLogDltTemplate") KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, error) -> new TopicPartition(record.topic() + ".DLT", -1)) {
            @Override
            protected void publish(ProducerRecord<Object, Object> record, KafkaOperations<Object, Object> operations,
                                   ConsumerRecord<?, ?> original) {
                try {
                    // 保留框架的有界 broker 确认，避免默认失败日志携带 ProducerRecord 正文。
                    verifySendResult(operations, record, operations.send(record), original);
                } catch (RuntimeException failure) {
                    throw new KafkaException("Kafka DLT publish failed: " + original.topic() + "-"
                            + original.partition() + "@" + original.offset());
                }
            }
        };
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setLogRecoveryRecord(false);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(35));
        recoverer.setThrowIfNoDestinationReturned(true);
        return recoverer;
    }

    @Bean
    public DefaultErrorHandler kafkaLogErrorHandler(
            @Qualifier("kafkaLogRecoverer") DeadLetterPublishingRecoverer kafkaLogRecoverer,
                                                    RuleKafkaLogProperties properties, KafkaLogMetrics metrics) {
        DefaultErrorHandler handler = new DefaultErrorHandler(kafkaLogRecoverer,
                new FixedBackOff(properties.getRetryIntervalMs(), properties.getMaxRetries()));
        handler.addNotRetryableExceptions(ClientLogValidationException.class);
        handler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception error, int deliveryAttempt) {
                if (deliveryAttempt > 1) metrics.increment("retry");
                log.warn("Kafka log delivery failed: topic={}, partition={}, offset={}, attempt={}",
                        record.topic(), record.partition(), record.offset(), deliveryAttempt);
            }
            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception error) {
                metrics.increment("dead_letter");
            }
            @Override
            public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
                metrics.increment("dead_letter_failure");
                log.warn("Kafka log recovery failed: topic={}, partition={}, offset={}",
                        record.topic(), record.partition(), record.offset());
            }
        });
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaLogListenerContainerFactory(
            KafkaProperties kafka, RuleKafkaLogProperties properties, Environment environment,
            ObjectProvider<SslBundles> bundles,
            @Qualifier("kafkaLogErrorHandler") DefaultErrorHandler kafkaLogErrorHandler) {
        validate(properties, environment);
        Map<String, Object> config = new HashMap<>(kafka.buildConsumerProperties(bundles.getIfAvailable()));
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        factory.setBatchListener(false);
        factory.setCommonErrorHandler(kafkaLogErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    private void validate(RuleKafkaLogProperties properties, Environment environment) {
        List<String> servers = Binder.get(environment)
                .bind("spring.kafka.bootstrap-servers", Bindable.listOf(String.class)).orElse(List.of());
        properties.validate(servers.isEmpty() || servers.stream().anyMatch(String::isBlank)
                ? null : String.join(",", servers));
    }
}

