package com.hengshucredit.rule.client.log;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.client.RuleEngineClientConfig;
import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import com.hengshucredit.rule.client.auth.ClientRequestAuthenticator;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Kafka 发送和确认均不占用规则执行线程；HTTP 补报沿用有界队列和项目鉴权。 */
public class KafkaLogReporter implements ExecutionLogReporter {

    public static final String PROJECT_ID_HEADER = "X-Rule-Project-Id";
    private static final Logger log = LoggerFactory.getLogger(KafkaLogReporter.class);
    private static final long SHUTDOWN_GRACE_MS = 500L;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final byte[] projectId;
    private final int capacity;
    private final HttpLogReporter httpFallback;
    private final ThreadPoolExecutor sender;
    private final Object lifecycleLock = new Object();
    // 包括等待发送和等待 broker 确认的日志，总量受同一个容量限制。
    private final Set<Delivery> pending = new LinkedHashSet<>();
    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicLong fallbackAttemptCount = new AtomicLong();
    private final AtomicLong droppedLogCount = new AtomicLong();
    private boolean closed;

    public KafkaLogReporter(KafkaTemplate<String, String> kafkaTemplate, RuleEngineClientConfig config) {
        if (kafkaTemplate == null) throw new IllegalArgumentException("KafkaTemplate is required");
        if (config.getProjectId() <= 0) throw new IllegalArgumentException("Kafka 日志上报必须配置真实 project-id");
        if (!hasText(config.getKafkaLogTopic())) throw new IllegalArgumentException("kafka-log-topic 不能为空");
        if (config.getLogBufferSize() <= 0) throw new IllegalArgumentException("log-buffer-size 必须大于 0");
        this.kafkaTemplate = kafkaTemplate;
        this.topic = config.getKafkaLogTopic();
        this.projectId = Long.toString(config.getProjectId()).getBytes(StandardCharsets.UTF_8);
        this.capacity = config.getLogBufferSize();
        this.httpFallback = createHttpFallback(config);
        this.sender = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), task -> {
                    Thread thread = new Thread(task, "rule-client-kafka-log-reporter");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    private static HttpLogReporter createHttpFallback(RuleEngineClientConfig config) {
        if (!config.isKafkaHttpFallbackEnabled()) return null;
        ClientAuthConfig auth = config.getAuthConfig();
        if (auth == null && hasText(config.getToken())) auth = ClientAuthConfig.legacyToken(config.getToken());
        if (!validAuth(auth)) {
            throw new IllegalArgumentException("Kafka HTTP 补报必须配置完整项目鉴权；"
                    + "如不需要补报，请显式关闭 kafka-http-fallback-enabled");
        }
        if (!hasText(config.getServerUrl())) throw new IllegalArgumentException("HTTP 补报需要 server-url");
        return new HttpLogReporter(config.getServerUrl(), config.getHttpTimeoutMs(),
                new ClientRequestAuthenticator(config.getServerUrl(), config.getHttpTimeoutMs(), auth),
                config.getLogBufferSize(), config.getLogBatchSize(), config.getLogFlushIntervalMs());
    }

    private static boolean validAuth(ClientAuthConfig auth) {
        if (auth == null || !hasText(auth.getAuthType())) return false;
        return switch (auth.getAuthType().toUpperCase(Locale.ROOT)) {
            case ClientAuthConfig.LEGACY_TOKEN -> hasText(auth.getLegacyToken());
            case ClientAuthConfig.BASIC -> hasText(auth.getUsername()) && hasText(auth.getPassword());
            case ClientAuthConfig.API_KEY -> hasText(auth.getApiKey()) && hasText(auth.getApiKeyParameterName())
                    && ("HEADER".equalsIgnoreCase(auth.getApiKeyPlacement())
                    || "QUERY".equalsIgnoreCase(auth.getApiKeyPlacement()));
            case ClientAuthConfig.HMAC_SHA256 -> hasText(auth.getAccessKey()) && hasText(auth.getHmacSecret());
            default -> false;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public void report(List<RuleExecutionLog> logs) {
        if (logs == null) return;
        for (RuleExecutionLog entry : logs) {
            if (entry == null) continue;
            synchronized (lifecycleLock) {
                if (closed || !hasText(entry.getTraceId())) {
                    droppedLogCount.incrementAndGet();
                    log.warn("Kafka execution log rejected: reporter closed or traceId missing");
                    continue;
                }
                if (pending.size() >= capacity) {
                    failed(entry, "Kafka log buffer is full");
                    continue;
                }
                Delivery delivery = new Delivery(entry);
                pending.add(delivery);
                try {
                    sender.execute(() -> send(delivery));
                } catch (RuntimeException error) {
                    complete(delivery, error);
                }
            }
        }
    }

    private void send(Delivery delivery) {
        synchronized (lifecycleLock) {
            if (!pending.contains(delivery)) return;
        }
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic,
                    delivery.entry.getTraceId(), JSON.toJSONString(delivery.entry));
            record.headers().add(PROJECT_ID_HEADER, projectId.clone());
            kafkaTemplate.send(record).whenComplete((result, error) -> complete(delivery, error));
        } catch (Exception error) {
            complete(delivery, error);
        }
    }

    private void complete(Delivery delivery, Throwable error) {
        synchronized (lifecycleLock) {
            // 关闭时已经补报的未知结果，后续回调不重复计数或补报。
            if (!pending.remove(delivery)) return;
            if (error == null) successCount.incrementAndGet();
            else failed(delivery.entry, error.getMessage());
            lifecycleLock.notifyAll();
        }
    }

    private void failed(RuleExecutionLog entry, String reason) {
        long failures = failureCount.incrementAndGet();
        if (httpFallback != null) {
            fallbackAttemptCount.incrementAndGet();
            httpFallback.report(Collections.singletonList(entry));
        } else {
            droppedLogCount.incrementAndGet();
        }
        if (failures == 1 || failures % 100 == 0) {
            log.warn("Kafka execution log delivery failed; totalFailures={}, httpFallback={}, error={}",
                    failures, httpFallback != null, reason);
        }
    }

    public long getSuccessCount() { return successCount.get(); }
    public long getFailureCount() { return failureCount.get(); }
    /** 表示提交 HTTP 队列的次数，不代表 HTTP 服务端已经接收。 */
    public long getFallbackAttemptCount() { return fallbackAttemptCount.get(); }
    public long getFallbackSuccessCount() { return httpFallback == null ? 0 : httpFallback.getReportedLogCount(); }
    public long getFallbackFailedLogCount() { return httpFallback == null ? 0 : httpFallback.getFailedLogCount(); }
    public long getDroppedLogCount() {
        return droppedLogCount.get() + (httpFallback == null ? 0 : httpFallback.getDroppedLogCount());
    }
    public int getPendingLogCount() {
        synchronized (lifecycleLock) { return pending.size(); }
    }

    @Override
    public void close() {
        boolean interrupted = false;
        synchronized (lifecycleLock) {
            if (closed) return;
            closed = true;
            sender.shutdown();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SHUTDOWN_GRACE_MS);
            while (!pending.isEmpty()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                try {
                    TimeUnit.NANOSECONDS.timedWait(lifecycleLock, remaining);
                } catch (InterruptedException error) {
                    interrupted = true;
                    break;
                }
            }
            sender.shutdownNow();
            for (Delivery delivery : List.copyOf(pending)) {
                complete(delivery, new IllegalStateException("Kafka delivery unconfirmed at shutdown"));
            }
        }
        // sender 不再能提交补报后，再让 HTTP reporter 有界排空并关闭。
        if (httpFallback != null) httpFallback.close();
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static class Delivery {
        private final RuleExecutionLog entry;

        private Delivery(RuleExecutionLog entry) {
            this.entry = entry;
        }
    }
}
