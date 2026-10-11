package com.hengshucredit.rule.server.consumer;

import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.server.service.ClientExecutionLogIngestionService;
import com.hengshucredit.rule.server.service.ClientLogValidationException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class KafkaLogConsumerTest {
    @Test
    public void usesTopicBindingAndReportsAcceptedOrDuplicateOnlyAfterIngestionReturns() {
        Fixture f = new Fixture();
        f.consumer.onMessage(record("logs-project-1", "1", "{\"ruleCode\":\"RULE\"}"));
        assertEquals(Long.valueOf(1L), f.service.projectId);
        assertEquals("RULE", f.service.log.getRuleCode());
        assertEquals(1, f.count("accepted"), 0);
        f.service.inserted = 0;
        f.consumer.onMessage(record("logs-project-1", "1", "{}"));
        assertEquals(1, f.count("duplicate"), 0);
    }

    @Test
    public void rejectsUnknownTopicMissingWrongOrDuplicateProjectHeaders() {
        for (ConsumerRecord<String, String> record : java.util.List.of(
                record("unbound", "1", "{}"), record("logs-project-1", null, "{}"),
                record("logs-project-1", "2", "{}"))) {
            Fixture f = new Fixture();
            assertThrows(ClientLogValidationException.class, () -> f.consumer.onMessage(record));
            assertNull(f.service.log);
            assertEquals(1, f.count("rejected"), 0);
        }
        Fixture f = new Fixture();
        ConsumerRecord<String, String> duplicate = record("logs-project-1", "1", "{}");
        duplicate.headers().add(KafkaLogConsumer.PROJECT_ID_HEADER, "1".getBytes(StandardCharsets.UTF_8));
        assertThrows(ClientLogValidationException.class, () -> f.consumer.onMessage(duplicate));
        assertNull(f.service.log);
    }

    @Test
    public void invalidJsonIsNonRetryableWithoutPayloadInExceptionChain() {
        for (String payload : java.util.List.of("[{secret_customer_input", "null", "[]", "123")) {
            Fixture f = new Fixture();
            ClientLogValidationException failure = assertThrows(ClientLogValidationException.class,
                    () -> f.consumer.onMessage(record("logs-project-1", "1", payload)));
            assertNull(failure.getCause());
            assertFalse(failure.getMessage().contains("secret_customer_input"));
            assertNull(f.service.log);
            assertEquals(1, f.count("rejected"), 0);
        }
    }

    @Test
    public void storageFailuresEscapeForRetryAndNeverCountAsAccepted() {
        Fixture f = new Fixture();
        f.service.fail = true;
        assertThrows(DataAccessResourceFailureException.class,
                () -> f.consumer.onMessage(record("logs-project-1", "1", "{}")));
        assertEquals(1, f.count("failed"), 0);
        assertEquals(0, f.count("accepted"), 0);
    }

    static ConsumerRecord<String, String> record(String topic, String projectId, String value) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topic, 0, 8L, "trace-key", value);
        if (projectId != null) record.headers().add(KafkaLogConsumer.PROJECT_ID_HEADER,
                projectId.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private static class Fixture {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final CapturingIngestion service = new CapturingIngestion();
        final KafkaLogConsumer consumer;
        Fixture() {
            RuleKafkaLogProperties properties = new RuleKafkaLogProperties();
            properties.getTopicProjects().put("logs-project-1", 1L);
            consumer = new KafkaLogConsumer(service, properties, new KafkaLogMetrics(registry));
        }
        double count(String outcome) {
            var counter = registry.find("rule.engine.kafka.log.records").tag("outcome", outcome).counter();
            return counter == null ? 0 : counter.count();
        }
    }

    private static class CapturingIngestion extends ClientExecutionLogIngestionService {
        Long projectId;
        RuleExecutionLog log;
        int inserted = 1;
        boolean fail;
        @Override public int ingestKafka(RuleExecutionLog log, Long projectId) {
            if (fail) throw new DataAccessResourceFailureException("database unavailable");
            this.log = log;
            this.projectId = projectId;
            return inserted;
        }
    }
}

