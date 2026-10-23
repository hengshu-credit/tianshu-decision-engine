package com.hengshucredit.rule.runtime;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.client.RuleEngineClient;
import com.hengshucredit.rule.client.log.ExecutionLogReporter;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.BeansException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class SdkRuntimeLogTransportTest {
    private HttpServer upstream;
    private final AtomicInteger httpReports = new AtomicInteger();
    private final AtomicInteger kafkaCreations = new AtomicInteger();
    private final AtomicReference<String> httpToken = new AtomicReference<>();
    private final AtomicReference<String> httpBody = new AtomicReference<>();
    private final AtomicReference<ProducerRecord<String, String>> kafkaRecord = new AtomicReference<>();
    private final CountDownLatch kafkaSent = new CountDownLatch(1);
    private boolean kafkaFails;
    private final AtomicInteger subscriptions = new AtomicInteger();
    private MockEnvironment env;

    @Before
    public void startUpstream() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/api/rule/sync/MixedCase", exchange -> {
            byte[] json = ("{\"code\":\"000000\",\"success\":true,\"message\":\"成功\",\"data\":{\"ruleCode\":\"MixedCase\",\"definitionId\":7,"
                    + "\"projectCode\":\"ProjectA\",\"projectId\":42,\"version\":1,\"modelType\":\"SCRIPT\","
                    + "\"compiledScript\":\"Age + 2\",\"requiresServerExecution\":false}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });
        upstream.createContext("/api/rule/log/report", exchange -> {
            httpReports.incrementAndGet();
            httpToken.set(exchange.getRequestHeaders().getFirst("X-Rule-Token"));
            httpBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] json = JSON.toJSONBytes(com.hengshucredit.rule.model.ApiResponse.ok());
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });
        upstream.createContext("/api/rule/sync/all", exchange -> {
            byte[] json = JSON.toJSONBytes(com.hengshucredit.rule.model.ApiResponse.ok(java.util.List.of(Map.of(
                    "ruleCode", "MixedCase", "definitionId", 7, "projectCode", "ProjectA", "projectId", 42, "version", 1,
                    "modelType", "SCRIPT", "compiledScript", "Age + 2", "requiresServerExecution", false))));
            exchange.sendResponseHeaders(200, json.length); exchange.getResponseBody().write(json); exchange.close();
        });
        upstream.createContext("/api/rule/sync/functions/42", exchange -> {
            byte[] json = JSON.toJSONBytes(com.hengshucredit.rule.model.ApiResponse.ok(java.util.List.of()));
            exchange.sendResponseHeaders(200, json.length); exchange.getResponseBody().write(json); exchange.close();
        });
        upstream.start();
        env = new MockEnvironment()
                .withProperty("RULE_SERVER_URL", "http://127.0.0.1:" + upstream.getAddress().getPort())
                .withProperty("PROJECT_ACCESS_TOKEN", "project-token")
                .withProperty("RULE_PROJECT_ID", "42")
                .withProperty("RULE_PROJECT_CODE", "ProjectA")
                .withProperty("RULE_ALLOWED_CODES", "MixedCase")
                .withProperty("SDK_LOG_REPORT_ENABLED", "true")
                .withProperty("RULE_TRACE_ENABLED", "false");
        env.setActiveProfiles("sdk");
    }

    @After
    public void stopUpstream() {
        upstream.stop(0);
    }

    @Test
    public void projectRuntimeStartsSubscriptionsAndRecoveryHeartbeat() {
        try (var context = context(false)) {
            context.refresh();
            assertEquals("项目模式必须启动规则订阅", 1, subscriptions.get());
            assertTrue(org.springframework.test.util.ReflectionTestUtils.getField(context.getBean(RuleEngineClient.class), "scheduler")
                    instanceof java.util.concurrent.ScheduledExecutorService);
            execute(context);
        }
    }

    @Test public void wrongProjectCodeCannotSilentlySubscribeToAnUnrelatedChannel() {
        env.withProperty("RULE_PROJECT_CODE", "WrongProject");
        try (var context = context(false)) {
            BeansException failure = assertThrows(BeansException.class, context::refresh);
            assertTrue(failure.getMostSpecificCause().getMessage().contains("RULE_PROJECT_CODE"));
        }
    }

    @Test
    public void defaultsToHttpEvenWhenKafkaTemplateIsAvailable() {
        try (var context = context(true)) {
            context.refresh();
            execute(context);
        }
        assertEquals(1, httpReports.get());
        assertEquals(0, kafkaCreations.get());
        assertNull(kafkaRecord.get());
    }

    @Test
    public void explicitKafkaUsesConfiguredTopicAndProjectHeader() throws Exception {
        env.withProperty("SDK_LOG_REPORT_TRANSPORT", "KAFKA")
                .withProperty("SDK_KAFKA_LOG_TOPIC", "runtime-audit")
                .withProperty("SDK_KAFKA_HTTP_FALLBACK_ENABLED", "false");
        try (var context = context(true)) {
            context.refresh();
            execute(context);
            assertTrue("显式 KAFKA 必须向 Kafka 发送执行日志", kafkaSent.await(5, TimeUnit.SECONDS));
        }
        assertEquals(1, kafkaCreations.get());
        assertEquals(0, httpReports.get());
        assertEquals("runtime-audit", kafkaRecord.get().topic());
        assertEquals("42", new String(kafkaRecord.get().headers().lastHeader("X-Rule-Project-Id").value(),
                StandardCharsets.UTF_8));
        assertEquals(JSON.parseObject(kafkaRecord.get().value()).getString("traceId"), kafkaRecord.get().key());
    }

    @Test
    public void kafkaFailureFallsBackToAuthenticatedHttpAndFlushesAtShutdown() throws Exception {
        env.withProperty("SDK_LOG_REPORT_TRANSPORT", "KAFKA");
        kafkaFails = true;
        try (var context = context(true)) {
            context.refresh();
            execute(context);
            assertTrue(kafkaSent.await(5, TimeUnit.SECONDS));
        }
        assertEquals(1, httpReports.get());
        assertEquals("project-token", httpToken.get());
        assertEquals(kafkaRecord.get().key(), JSON.parseArray(httpBody.get()).getJSONObject(0).getString("traceId"));
    }

    @Test
    public void disablingHttpFallbackDoesNotSendFailedKafkaLogsOverHttp() throws Exception {
        env.withProperty("SDK_LOG_REPORT_TRANSPORT", "KAFKA")
                .withProperty("SDK_KAFKA_HTTP_FALLBACK_ENABLED", "false");
        kafkaFails = true;
        try (var context = context(true)) {
            context.refresh();
            execute(context);
            assertTrue(kafkaSent.await(5, TimeUnit.SECONDS));
        }
        assertEquals(0, httpReports.get());
    }

    @Test
    public void httpProfileDoesNotCreateSdkKafkaReporter() {
        env.setActiveProfiles("http");
        env.withProperty("SDK_LOG_REPORT_TRANSPORT", "KAFKA");
        try (var context = context(true)) {
            context.register(SdkRuntimeConfiguration.KafkaLogConfiguration.class);
            context.refresh();
            assertTrue(context.getBeansOfType(ExecutionLogReporter.class).isEmpty());
        }
        assertEquals(0, kafkaCreations.get());
    }

    @Test
    public void customReporterTakesPriorityWithoutRequiringKafkaTemplate() {
        env.withProperty("SDK_LOG_REPORT_TRANSPORT", "KAFKA");
        for (boolean withKafka : new boolean[]{true, false}) {
            AtomicInteger customReports = new AtomicInteger();
            ExecutionLogReporter custom = logs -> customReports.addAndGet(logs.size());
            try (var context = context(withKafka)) {
                context.registerBean("customReporter", ExecutionLogReporter.class, () -> custom);
                context.refresh();
                execute(context);
                assertSame(custom, context.getBean(ExecutionLogReporter.class));
                assertEquals(1, customReports.get());
            }
        }
        assertEquals(0, kafkaCreations.get());
        assertEquals(0, httpReports.get());
    }

    @Test
    public void disabledReportingDoesNotInitializeKafka() {
        env.withProperty("SDK_LOG_REPORT_ENABLED", "false").withProperty("SDK_LOG_REPORT_TRANSPORT", "KAFKA");
        try (var context = context(true)) {
            context.refresh();
            execute(context);
        }
        assertEquals(0, kafkaCreations.get());
        assertEquals(0, httpReports.get());
    }

    @Test
    public void explicitKafkaWithoutTemplateFailsAtStartup() {
        env.withProperty("SDK_LOG_REPORT_TRANSPORT", "KAFKA");
        try (var context = context(false)) {
            BeansException error = assertThrows(BeansException.class, context::refresh);
            assertTrue(error.getMostSpecificCause().getMessage().contains("KafkaTemplate"));
        }
    }

    @Test
    public void unknownTransportFailsAtStartup() {
        env.withProperty("SDK_LOG_REPORT_TRANSPORT", "KAFKA_TYPO");
        try (var context = context(true)) {
            BeansException error = assertThrows(BeansException.class, context::refresh);
            assertTrue(error.getMostSpecificCause().getMessage().contains("KAFKA_TYPO"));
        }
        assertEquals(0, kafkaCreations.get());
    }

    private AnnotationConfigApplicationContext context(boolean withKafka) {
        var context = new AnnotationConfigApplicationContext();
        context.setEnvironment(env);
        context.registerBean(SdkRuntimeConfiguration.class, () -> new SdkRuntimeConfiguration() {
            @Override RuleEngineClient createClient(RuleEngineClient.Builder builder) {
                RuleEngineClient client = super.createClient(builder);
                org.springframework.test.util.ReflectionTestUtils.setField(client, "redisSubscriber",
                        new com.hengshucredit.rule.client.sync.RedisSubscriber(null, null, "ProjectA") {
                            @Override public void start() { subscriptions.incrementAndGet(); }
                            @Override public void stop() { }
                        });
                return client;
            }
        });
        context.registerBean(RedisConnectionFactory.class, () -> (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(), new Class<?>[]{RedisConnectionFactory.class},
                (proxy, method, args) -> { throw new AssertionError("本地执行不得访问 Redis"); }));
        if (withKafka) {
            context.registerBean("kafkaTemplate", KafkaTemplate.class, () -> {
                kafkaCreations.incrementAndGet();
                return new KafkaTemplate<String, String>(new DefaultKafkaProducerFactory<>(Map.of())) {
                    @Override
                    public CompletableFuture<SendResult<String, String>> send(ProducerRecord<String, String> record) {
                        kafkaRecord.set(record);
                        kafkaSent.countDown();
                        return kafkaFails ? CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"))
                                : CompletableFuture.completedFuture(null);
                    }
                };
            }, definition -> definition.setLazyInit(true));
        }
        return context;
    }

    private void execute(AnnotationConfigApplicationContext context) {
        var result = context.getBean(RuleEngineClient.class).execute("MixedCase", Map.of("Age", 18));
        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(20, result.getResult());
    }
}
