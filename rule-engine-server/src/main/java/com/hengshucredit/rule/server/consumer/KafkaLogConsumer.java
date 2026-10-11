package com.hengshucredit.rule.server.consumer;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.server.service.ClientExecutionLogIngestionService;
import com.hengshucredit.rule.server.service.ClientLogValidationException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** 每条日志独立完成项目校验与数据库事务，成功或死信确认之后才能推进消费位置。 */
@Component
@ConditionalOnProperty(prefix = "rule-engine.kafka", name = "enabled", havingValue = "true")
public class KafkaLogConsumer {
    public static final String PROJECT_ID_HEADER = "X-Rule-Project-Id";

    private final ClientExecutionLogIngestionService ingestionService;
    private final RuleKafkaLogProperties properties;
    private final KafkaLogMetrics metrics;

    public KafkaLogConsumer(ClientExecutionLogIngestionService ingestionService,
                            RuleKafkaLogProperties properties, KafkaLogMetrics metrics) {
        this.ingestionService = ingestionService;
        this.properties = properties;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "#{@kafkaLogProperties.topics()}",
            groupId = "#{@kafkaLogProperties.groupId}", containerFactory = "kafkaLogListenerContainerFactory")
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            Long projectId = projectId(record);
            RuleExecutionLog entry;
            try {
                entry = JSON.parseObject(record.value(), RuleExecutionLog.class);
            } catch (RuntimeException parseFailure) {
                // JSON 异常可能含原始输入，不将其作为 cause 传播到应用日志。
                throw new ClientLogValidationException(400, "Kafka log JSON is invalid");
            }
            if (entry == null) throw new ClientLogValidationException(400, "Kafka log must be a JSON object");
            int inserted = ingestionService.ingestKafka(entry, projectId);
            metrics.increment(inserted == 0 ? "duplicate" : "accepted");
        } catch (ClientLogValidationException rejected) {
            metrics.increment("rejected");
            throw rejected;
        } catch (RuntimeException failure) {
            metrics.increment("failed");
            throw failure;
        }
    }

    private Long projectId(ConsumerRecord<String, String> record) {
        Long boundId = properties.getTopicProjects().get(record.topic());
        Header project = null;
        for (Header header : record.headers().headers(PROJECT_ID_HEADER)) {
            if (project != null) throw new ClientLogValidationException(403, "Duplicate Kafka project header");
            project = header;
        }
        if (boundId == null || project == null || project.value() == null
                || !boundId.toString().equals(new String(project.value(), StandardCharsets.UTF_8))) {
            throw new ClientLogValidationException(403, "Kafka log project header does not match topic binding");
        }
        return boundId;
    }
}
