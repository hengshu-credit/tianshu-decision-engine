package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** 真实 HTTP 外数 + 消费入口 + 对象赋值 + JSON 检查点恢复，不依赖供应商服务。 */
public class ExternalApiRecoveryContractTest {
    @Test
    public void objectWaitRemainsAWaitEvenWhenRuleReadsSourceStatus() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var options = fixture.options();
            options.setStatusReferenceKeys(Set.of("DATA_OBJECT:50"));
            Map<String, Object> values = new LinkedHashMap<>(Map.of("mobile", "111"));
            assertThrows(ExternalApiWaitingException.class, () -> fixture.resolve(values, options));
            assertEquals("WAITING_EXTERNAL", options.getSourceStates().get("DATA_OBJECT:50").get("OUTCOME"));
            assertFalse(values.containsKey("report"));
        }
    }

    @Test
    public void objectResumesSameTaskAfterUnrelatedValuesChangeAndReusesCompletedResponse() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var initial = fixture.options();
            assertThrows(RuntimeException.class, () -> fixture.resolve(
                    new LinkedHashMap<>(Map.of("mobile", "111", "localScore", 1)), initial));
            var resumed = fixture.restore(initial);
            Map<String, Object> values = new LinkedHashMap<>(Map.of("mobile", "111", "localScore", 2));
            fixture.resolve(values, resumed);
            assertEquals(699, ExternalApiRequestPlan.read(values, "report.score"));
            assertEquals("恢复只能查询原任务，不能再次提交", 1, fixture.submissions.get());
            assertEquals(2, fixture.polls.get());

            var restoredAgain = fixture.restore(resumed);
            fixture.resolve(new LinkedHashMap<>(Map.of("mobile", "111")), restoredAgain);
            assertEquals(1, fixture.submissions.get());
            assertEquals("已取得结果的任务不能再次轮询", 2, fixture.polls.get());
            assertTrue(restoredAgain.getInvocationCache().snapshotCompletedSteps().values().stream()
                    .allMatch(raw -> "SUCCESS".equals(((Map<?, ?>) raw).get("status"))));
        }
    }

    @Test
    public void anotherConsumerWithSameRequestReusesPendingTask() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var initial = fixture.options();
            assertThrows(RuntimeException.class, () -> fixture.resolve(
                    new LinkedHashMap<>(Map.of("mobile", "111")), initial));
            var resumed = fixture.restore(initial);
            var response = fixture.consumer.resolve(1L, 9L, Map.of(), Map.of("mobile", "111"), resumed, "VARIABLE:7");
            assertEquals(699, ExternalApiConsumerService.select(response, "body.score"));
            assertEquals(1, fixture.submissions.get());
            assertEquals(2, fixture.polls.get());
        }
    }

    @Test
    public void changedRequestDoesNotReadAnotherOrdersPendingTask() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var initial = fixture.options();
            assertThrows(RuntimeException.class, () -> fixture.resolve(
                    new LinkedHashMap<>(Map.of("mobile", "111")), initial));
            var resumed = fixture.restore(initial);
            fixture.resolve(new LinkedHashMap<>(Map.of("mobile", "222")), resumed);
            assertEquals(2, fixture.submissions.get());
        }
    }

    @Test
    public void variableWaitPreservesTaskCauseAndCannotBeSuppressedByStatusOperator() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var initial = fixture.options();
            initial.setStatusReferenceKeys(Set.of("VARIABLE:7"));
            RuleVariable variable = new RuleVariable();
            variable.setId(7L); variable.setProjectId(1L); variable.setVarCode("riskScore");
            variable.setVarType("NUMBER"); variable.setVarSource("API"); variable.setStatus(1);
            variable.setSourceConfig("{\"apiConfigId\":9,\"resultPath\":\"body.score\",\"exceptionStrategy\":\"WAIT\"}");
            var resolver = new VariableSourceResolver();
            ReflectionTestUtils.setField(resolver, "externalApiConsumerService", fixture.consumer);
            ReflectionTestUtils.setField(resolver, "externalApiInvokeService", fixture.invoke);
            var error = assertThrows(ExternalApiWaitingException.class, () -> resolver.resolveIntoSnapshot(
                    List.of(variable), List.of(), List.of(), new LinkedHashMap<>(Map.of("mobile", "111")), initial));
            assertEquals("task-1", ((ExternalApiInvokeService.ApiInvokeException) error.getCause()).getPendingAsync().get("taskId"));
            assertEquals("WAITING_EXTERNAL", initial.getSourceStates().get("VARIABLE:7").get("OUTCOME"));

            var resumed = fixture.restore(initial);
            Map<String, Object> values = new LinkedHashMap<>(Map.of("mobile", "111", "unrelated", 17));
            resolver.resolveIntoSnapshot(List.of(variable), List.of(), List.of(), values, resumed);
            assertEquals(699, values.get("riskScore"));
            assertEquals(1, fixture.submissions.get());
            assertEquals(2, fixture.polls.get());
        }
    }

    @Test
    public void qlFunctionWaitReachesRuleCoordinatorWithCause() {
        QLExpressEngine engine = new QLExpressEngine();
        var failure = new ExternalApiWaitingException("等待外数", new IllegalStateException("供应商维护"));
        engine.getRunner().addFunction("waitForReport", (Runnable) () -> { throw failure; });
        var thrown = assertThrows(ExternalApiWaitingException.class,
                () -> engine.execute("waitForReport(); return 1;", new LinkedHashMap<>(), false));
        assertSame(failure, thrown);
    }

    private static final class Fixture implements AutoCloseable {
        final AtomicInteger submissions = new AtomicInteger();
        final AtomicInteger polls = new AtomicInteger();
        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        final ExternalApiConsumerService consumer = new ExternalApiConsumerService();
        final ExternalApiInvokeService invoke = new ExternalApiInvokeService();
        final DataObjectSourceResolver resolver = new DataObjectSourceResolver();
        final RuleDataObjectField field = new RuleDataObjectField();
        final RuleDefinitionInputField direct = new RuleDefinitionInputField();

        Fixture() throws Exception {
            server.createContext("/submit", exchange -> {
                submissions.incrementAndGet();
                byte[] body = "{\"taskId\":\"task-1\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(202, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            server.createContext("/poll/task-1", exchange -> {
                String result = polls.incrementAndGet() == 1 ? "{\"status\":\"PENDING\"}"
                        : "{\"status\":\"DONE\",\"data\":{\"score\":699}}";
                byte[] body = result.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            server.start();
            RuleExternalApiConfig api = new RuleExternalApiConfig();
            api.setId(9L); api.setDatasourceId(1L); api.setStatus(1); api.setRequestMethod("POST");
            api.setEndpointUrl("/submit"); api.setRequestMapping("{\"mobile\":\"$.mobile\"}");
            api.setAuthMode("NONE"); api.setContentType("application/json");
            api.setRequestMode("ASYNC"); api.setAsyncResultMode("POLL"); api.setExceptionStrategy("WAIT");
            api.setAsyncPollConfig("{\"taskIdPath\":\"body.taskId\",\"resultEndpointUrl\":\"/poll/${taskId}\","
                    + "\"statusPath\":\"body.status\",\"successValue\":\"DONE\",\"resultPath\":\"body.data\",\"maxAttempts\":1,\"intervalMs\":1}");
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(1L); datasource.setStatus(1); datasource.setScope("GLOBAL");
            datasource.setProtocol("HTTP"); datasource.setAuthType("NONE");
            datasource.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            var apiMapper = mapper(RuleExternalApiConfigMapper.class, api);
            var datasourceMapper = mapper(RuleExternalDatasourceMapper.class, datasource);
            ReflectionTestUtils.setField(invoke, "apiConfigMapper", apiMapper);
            ReflectionTestUtils.setField(invoke, "datasourceMapper", datasourceMapper);
            ReflectionTestUtils.setField(consumer, "apiConfigMapper", apiMapper);
            ReflectionTestUtils.setField(consumer, "datasourceMapper", datasourceMapper);
            ReflectionTestUtils.setField(consumer, "invokeService", invoke);
            ReflectionTestUtils.setField(resolver, "externalApiConsumerService", consumer);
            field.setId(50L); field.setObjectId(100L); field.setVarCode("score"); field.setSourcePath("body.score");
            field.setObjectSourceType("API"); field.setObjectScriptName("report");
            field.setObjectSourceContent("{\"apiConfigId\":9,\"bindingMode\":\"FIELDS\"}");
            direct.setVarId(50L); direct.setRefType("DATA_OBJECT"); direct.setScriptName("report.score");
        }

        VariableResolveOptions options() {
            var options = VariableResolveOptions.defaults();
            options.setDerivedReferencePaths(Map.of());
            options.setInvocationCache(new VariableResolutionInvocationCache());
            return options;
        }

        VariableResolveOptions restore(VariableResolveOptions previous) {
            var options = options();
            String checkpoint = new RuleIdempotencyService().snapshotCheckpoint(previous.getInvocationCache(), Map.of(), previous.getSourceStates());
            var state = new RuleExecutionStateService.State(1L, 1L, 1L, "rule", "key", "digest", "trace", "WAITING_EXTERNAL",
                    1, 1L, 1L, "artifact", checkpoint, null, null, null, null, null);
            var decision = RuleIdempotencyService.Decision.claimed(state, "worker", 3600, 120, 1L, "artifact");
            new RuleIdempotencyService().restoreCheckpoint(decision, options.getInvocationCache(), new LinkedHashMap<>(), options);
            return options;
        }

        void resolve(Map<String, Object> values, VariableResolveOptions options) {
            resolver.resolve(1L, List.of(direct), values, options, List.of(field));
        }

        @Override public void close() { server.stop(0); }
    }

    private static <T> T mapper(Class<T> type, Object value) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> "selectById".equals(method.getName()) ? value : null));
    }
}
