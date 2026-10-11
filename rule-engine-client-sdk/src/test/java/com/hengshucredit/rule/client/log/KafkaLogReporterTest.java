package com.hengshucredit.rule.client.log;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.client.RuleEngineClientConfig;
import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.After;
import org.junit.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class KafkaLogReporterTest {

    private HttpServer server;
    private final List<String> httpTokens = new CopyOnWriteArrayList<>();
    private final List<RuleExecutionLog> httpLogs = new CopyOnWriteArrayList<>();
    private final AtomicInteger httpRequests = new AtomicInteger();

    @After
    public void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    public void successIsCountedOnlyAfterBrokerAcknowledgesAndCarriesStableIdentity() throws Exception {
        CompletableFuture<SendResult<String, String>> acknowledgement = new CompletableFuture<>();
        StubKafkaTemplate template = new StubKafkaTemplate(record -> acknowledgement);
        try (KafkaLogReporter reporter = new KafkaLogReporter(template, config(false))) {
            RuleExecutionLog entry = entry("trace-123");
            entry.setRevisionId(27L);
            entry.setArtifactDigest("digest");
            reporter.report(Collections.singletonList(entry));

            assertTrue(template.entered.await(2, TimeUnit.SECONDS));
            assertEquals(0, reporter.getSuccessCount());
            assertEquals(1, reporter.getPendingLogCount());
            ProducerRecord<String, String> sent = template.records.get(0);
            assertEquals("rule-execution-log", sent.topic());
            assertEquals("trace-123", sent.key());
            assertEquals("18", new String(sent.headers().lastHeader("X-Rule-Project-Id").value(), StandardCharsets.UTF_8));
            RuleExecutionLog payload = JSON.parseObject(sent.value(), RuleExecutionLog.class);
            assertEquals("EXECUTING_PROJECT", payload.getProjectCode());
            assertEquals(Long.valueOf(27L), payload.getRevisionId());
            assertEquals("digest", payload.getArtifactDigest());

            acknowledgement.complete(null);
            await(() -> reporter.getSuccessCount() == 1);
            assertEquals(0, reporter.getFailureCount());
            assertEquals(0, reporter.getFallbackAttemptCount());
        }
    }

    @Test
    public void asynchronousAndSynchronousFailuresUseAuthenticatedHttpFallback() throws Exception {
        RuleEngineClientConfig config = config(true);
        config.setServerUrl(startHttpServer(200));
        config.setToken("project-token");
        CompletableFuture<SendResult<String, String>> acknowledgement = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        StubKafkaTemplate template = new StubKafkaTemplate(record -> {
            if (calls.incrementAndGet() == 1) return acknowledgement;
            throw new IllegalStateException("metadata unavailable");
        });
        try (KafkaLogReporter reporter = new KafkaLogReporter(template, config)) {
            reporter.report(List.of(entry("async-failure"), entry("sync-failure")));
            assertTrue(template.entered.await(2, TimeUnit.SECONDS));
            acknowledgement.completeExceptionally(new IllegalStateException("broker rejected"));

            await(() -> reporter.getFallbackSuccessCount() == 2);
            assertEquals(2, reporter.getFailureCount());
            assertEquals(2, reporter.getFallbackAttemptCount());
            assertEquals(0, reporter.getSuccessCount());
            assertEquals(0, reporter.getDroppedLogCount());
            assertEquals(2, httpLogs.size());
            assertTrue(httpLogs.stream().anyMatch(log -> "async-failure".equals(log.getTraceId())));
            assertTrue(httpLogs.stream().anyMatch(log -> "sync-failure".equals(log.getTraceId())));
            assertTrue(httpTokens.stream().allMatch("project-token"::equals));
        }
    }

    @Test
    public void failedHttpFallbackIsNotCountedAsSuccessfulRecovery() throws Exception {
        RuleEngineClientConfig config = config(true);
        config.setServerUrl(startHttpServer(500));
        config.setAuthConfig(ClientAuthConfig.legacyToken("project-token"));
        StubKafkaTemplate template = new StubKafkaTemplate(record -> CompletableFuture.failedFuture(
                new IllegalStateException("broker unavailable")));
        try (KafkaLogReporter reporter = new KafkaLogReporter(template, config)) {
            reporter.report(List.of(entry("http-failure")));

            await(() -> reporter.getFallbackFailedLogCount() == 1);
            assertEquals(1, reporter.getFallbackAttemptCount());
            assertEquals(0, reporter.getFallbackSuccessCount());
            assertEquals(3, httpRequests.get());
        }
    }

    @Test
    public void pendingAcknowledgementsAreBoundedAndDisabledFallbackTracksLoss() throws Exception {
        RuleEngineClientConfig config = config(false);
        config.setLogBufferSize(1);
        CompletableFuture<SendResult<String, String>> acknowledgement = new CompletableFuture<>();
        StubKafkaTemplate template = new StubKafkaTemplate(record -> acknowledgement);
        try (KafkaLogReporter reporter = new KafkaLogReporter(template, config)) {
            reporter.report(List.of(entry("pending")));
            assertTrue(template.entered.await(2, TimeUnit.SECONDS));
            reporter.report(List.of(entry("overflow")));

            assertEquals(1, reporter.getPendingLogCount());
            assertEquals(1, template.records.size());
            assertEquals(1, reporter.getFailureCount());
            assertEquals(1, reporter.getDroppedLogCount());
            acknowledgement.completeExceptionally(new IllegalStateException("late failure"));
            await(() -> reporter.getFailureCount() == 2);
            assertEquals(2, reporter.getDroppedLogCount());
            assertEquals(0, reporter.getFallbackAttemptCount());
        }
    }

    @Test
    public void kafkaMetadataWaitDoesNotBlockRuleThread() throws Exception {
        CountDownLatch releaseSend = new CountDownLatch(1);
        StubKafkaTemplate template = new StubKafkaTemplate(record -> {
            try {
                assertTrue(releaseSend.await(3, TimeUnit.SECONDS));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
            return CompletableFuture.completedFuture(null);
        });
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try (KafkaLogReporter reporter = new KafkaLogReporter(template, config(false))) {
            Future<?> call = caller.submit(() -> reporter.report(List.of(entry("blocked-metadata"))));
            assertTrue(template.entered.await(2, TimeUnit.SECONDS));
            call.get(300, TimeUnit.MILLISECONDS);
            releaseSend.countDown();
            await(() -> reporter.getSuccessCount() == 1);
        } finally {
            releaseSend.countDown();
            caller.shutdownNow();
        }
    }

    @Test
    public void shutdownAccountsForUnconfirmedLogsAndLateCallbackDoesNotResubmit() throws Exception {
        RuleEngineClientConfig config = config(true);
        config.setServerUrl(startHttpServer(200));
        config.setToken("project-token");
        CompletableFuture<SendResult<String, String>> acknowledgement = new CompletableFuture<>();
        StubKafkaTemplate template = new StubKafkaTemplate(record -> acknowledgement);
        KafkaLogReporter reporter = new KafkaLogReporter(template, config);
        reporter.report(List.of(entry("unconfirmed")));
        assertTrue(template.entered.await(2, TimeUnit.SECONDS));

        long started = System.nanoTime();
        reporter.close();
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2000);
        assertEquals(0, reporter.getPendingLogCount());
        assertEquals(1, reporter.getFailureCount());
        assertEquals(1, reporter.getFallbackAttemptCount());
        assertEquals(1, reporter.getFallbackSuccessCount());
        acknowledgement.completeExceptionally(new IllegalStateException("late failure"));
        reporter.close();
        assertEquals(1, reporter.getFallbackAttemptCount());
        reporter.report(List.of(entry("after-close")));
        assertEquals(1, reporter.getDroppedLogCount());
        assertEquals(1, httpLogs.size());
    }

    @Test
    public void missingTraceIsRejectedWithoutInventingAnIdentity() {
        StubKafkaTemplate template = new StubKafkaTemplate(record -> CompletableFuture.completedFuture(null));
        try (KafkaLogReporter reporter = new KafkaLogReporter(template, config(false))) {
            reporter.report(List.of(entry(" ")));
            assertEquals(1, reporter.getDroppedLogCount());
            assertEquals(0, reporter.getPendingLogCount());
            assertTrue(template.records.isEmpty());
        }
    }

    @Test
    public void kafkaRequiresProjectIdAndFallbackRequiresCompleteProjectAuthentication() {
        StubKafkaTemplate template = new StubKafkaTemplate(record -> CompletableFuture.completedFuture(null));
        RuleEngineClientConfig config = config(false);
        config.setProjectId(0);
        assertThrows(IllegalArgumentException.class, () -> new KafkaLogReporter(template, config));
        config.setProjectId(18);
        config.setKafkaHttpFallbackEnabled(true);
        assertThrows(IllegalArgumentException.class, () -> new KafkaLogReporter(template, config));
        config.setAuthConfig(ClientAuthConfig.basic("user", " "));
        assertThrows(IllegalArgumentException.class, () -> new KafkaLogReporter(template, config));
        config.setAuthConfig(ClientAuthConfig.apiKey("X-Key", "key", "BODY"));
        assertThrows(IllegalArgumentException.class, () -> new KafkaLogReporter(template, config));
    }

    private RuleEngineClientConfig config(boolean fallback) {
        RuleEngineClientConfig config = new RuleEngineClientConfig();
        config.setProjectId(18);
        config.setKafkaHttpFallbackEnabled(fallback);
        config.setLogBatchSize(1);
        config.setLogFlushIntervalMs(10);
        return config;
    }

    private RuleExecutionLog entry(String traceId) {
        RuleExecutionLog entry = new RuleExecutionLog();
        entry.setTraceId(traceId);
        entry.setRuleCode("RULE");
        entry.setProjectCode("EXECUTING_PROJECT");
        entry.setSource("CLIENT");
        return entry;
    }

    private String startHttpServer(int responseStatus) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/rule/log/report", exchange -> {
            httpRequests.incrementAndGet();
            httpTokens.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-Rule-Token")));
            httpLogs.addAll(JSON.parseArray(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8), RuleExecutionLog.class));
            byte[] body = ("{\"code\":" + responseStatus + "}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void await(BooleanSupplier predicate) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!predicate.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue("Asynchronous report did not reach the expected state", predicate.getAsBoolean());
    }

    private static class StubKafkaTemplate extends KafkaTemplate<String, String> {
        private final Function<ProducerRecord<String, String>, CompletableFuture<SendResult<String, String>>> behavior;
        private final List<ProducerRecord<String, String>> records = new CopyOnWriteArrayList<>();
        private final CountDownLatch entered = new CountDownLatch(1);

        private StubKafkaTemplate(Function<ProducerRecord<String, String>, CompletableFuture<SendResult<String, String>>> behavior) {
            super(new DefaultKafkaProducerFactory<>(Collections.emptyMap()));
            this.behavior = behavior;
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(ProducerRecord<String, String> record) {
            records.add(record);
            entered.countDown();
            return behavior.apply(record);
        }
    }
}
