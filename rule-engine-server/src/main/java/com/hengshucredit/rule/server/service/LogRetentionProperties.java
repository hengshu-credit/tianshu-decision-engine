package com.hengshucredit.rule.server.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 生产日志留存策略；天数为 0 表示永久保留，避免默认部署误删审计数据。 */
@Data
@ConfigurationProperties(prefix = "rule-engine.log-retention")
public class LogRetentionProperties {
    private boolean enabled;
    private long intervalMs = 86_400_000L;
    private int batchSize = 1000;
    private int maxBatchesPerTable = 10;
    private int queryTimeoutSeconds = 5;
    private int authAccessLogDays;

    public void validate() {
        if (intervalMs < 60_000L || intervalMs > 7L * 86_400_000L) {
            throw new IllegalStateException("log-retention.interval-ms must be between 60000 and 604800000");
        }
        if (batchSize < 1 || batchSize > 10_000) {
            throw new IllegalStateException("log-retention.batch-size must be between 1 and 10000");
        }
        if (maxBatchesPerTable < 1 || maxBatchesPerTable > 100) {
            throw new IllegalStateException("log-retention.max-batches-per-table must be between 1 and 100");
        }
        if (queryTimeoutSeconds < 1 || queryTimeoutSeconds > 30) {
            throw new IllegalStateException("log-retention.query-timeout-seconds must be between 1 and 30");
        }
        validateDays(authAccessLogDays, "auth-access-log-days");
    }

    private void validateDays(int days, String name) {
        if (days < 0 || days > 36_500) {
            throw new IllegalStateException("log-retention." + name + " must be between 0 and 36500");
        }
    }
}
