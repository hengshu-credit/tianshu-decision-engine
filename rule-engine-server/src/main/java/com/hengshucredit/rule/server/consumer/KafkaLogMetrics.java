package com.hengshucredit.rule.server.consumer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** 固定结果标签，不将项目、trace 或消息正文写入指标。 */
public class KafkaLogMetrics {
    private final MeterRegistry registry;

    public KafkaLogMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void increment(String outcome) {
        Counter.builder("rule.engine.kafka.log.records").tag("outcome", outcome).register(registry).increment();
    }
}
