package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ExternalApiTrialServiceTest {
    @Test public void workflowSharedTaskMappingSurvivesCheckpointAndResumesWithoutSubmittingAgain() throws Exception {
        assertSharedTaskSurvivesResume(true);
    }

    @Test public void legacyAsyncSharedTaskMappingSurvivesInternalCopiesAndResumeWithoutSubmittingAgain() throws Exception {
        assertSharedTaskSurvivesResume(false);
    }

    private void assertSharedTaskSurvivesResume(boolean workflow) throws Exception {
        for (boolean pause : new boolean[]{false, true}) {
            var now = new java.util.concurrent.atomic.AtomicLong();
            RequestDeadlineContext.setNanoTimeSource(now::get);
            try (Fixture fixture = new Fixture()) {
                fixture.service.slice = 10_000;
                var received = new java.util.concurrent.atomic.AtomicReference<JSONObject>();
                fixture.server.createContext("/shared-query", exchange -> {
                    fixture.polls.incrementAndGet();
                    JSONObject body = JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    received.set(body);
                    boolean valid = body != null && "task1".equals(body.getString("taskId"));
                    byte[] response = (valid ? "{\"status\":\"DONE\",\"score\":88}" : "{\"error\":\"missing taskId\"}").getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(valid ? 200 : 400, response.length);
                    exchange.getResponseBody().write(response); exchange.close();
                });
                if (pause) {
                    var expire = new java.util.concurrent.atomic.AtomicBoolean(true);
                    fixture.store.afterCheckpoint = raw -> {
                        JSONObject state = JSON.parseObject(raw);
                        String path = workflow ? "continuation.results.submit" : "continuation.taskId";
                        if (ExternalApiRequestPlan.read(state, path) != null && expire.compareAndSet(true, false)) {
                            now.addAndGet(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(10_001));
                        }
                    };
                }
                RuleExternalApiConfig config = fixture.config();
                config.setRequestMode("ASYNC"); config.setAsyncTimeoutMs(10_000); config.setExceptionStrategy("WAIT");
                if (workflow) {
                    config.setExecutionConfig("""
                            {"version":2,"requestFields":[],"steps":[
                              {"id":"submit","type":"HTTP","endpointUrl":"/submit","requestFields":[],
                               "responseBranches":[{"id":"receipt","mode":"FIELDS","outputFields":[
                                 {"path":"original","value":{"kind":"PATH","value":"response.body"}},
                                 {"path":"lookup","value":{"kind":"PATH","value":"response.body"}}]}]},
                              {"id":"query","type":"HTTP","endpointUrl":"/shared-query","requestFields":[
                                {"id":"task","location":"JSON","path":"taskId","required":true,
                                 "value":{"kind":"PATH","value":"steps.submit.body.lookup.taskId"}}]}]}
                            """);
                } else {
                    config.setResponseScript("_result = newMap(); _result = mapPut(_result, \"original\", body); _result = mapPut(_result, \"lookup\", body); _result");
                    config.setAsyncResultMode("POLL");
                    config.setAsyncPollConfig("""
                            {"taskIdPath":"body.lookup.taskId","resultEndpointUrl":"/shared-query","requestMethod":"POST",
                             "requestMapping":{"taskId":"${submission.body.lookup.taskId}"},
                             "statusPath":"body.status","successValue":"DONE","intervalMs":1,"maxAttempts":1}
                            """);
                }
                Map<String, Object> first = fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of());
                if (pause) {
                    assertEquals("提交后应保存等待状态: " + first.get("errorMessage"), "WAITING", first.get("status"));
                    assertEquals(1, fixture.submissions.get()); assertEquals(0, fixture.polls.get());
                    String id = (String) first.get("trialId");
                    JSONObject saved = JSON.parseObject(fixture.store.get(id, "owner").pendingJson());
                    String taskPath = workflow ? "continuation.results.submit.body.lookup.taskId" : "continuation.submission.body.lookup.taskId";
                    assertEquals("持久检查点必须保留业务值", "task1", ExternalApiRequestPlan.read(saved, taskPath));
                    if (!workflow) {
                        assertEquals("查询上下文不能反向污染已发提交报文", Map.of(),
                                ExternalApiRequestPlan.read(saved, "continuation.submission.request.body"));
                    }
                    Map<String, Object> resumed = fixture.service.resume("owner", id);
                    assertEquals("恢复必须保留共享映射中的任务号: " + resumed.get("errorMessage"), "SUCCEEDED", resumed.get("status"));
                } else {
                    assertEquals("连续执行必须先证明相同配置有效: " + first.get("errorMessage"), "SUCCEEDED", first.get("status"));
                }
                assertEquals("task1", received.get().getString("taskId"));
                assertEquals("已完成的提交永远只调用一次", 1, fixture.submissions.get());
                assertEquals(1, fixture.polls.get());
            } finally { RequestDeadlineContext.clear(); RequestDeadlineContext.resetNanoTimeSource(); }
        }
    }

    @Test public void synchronousResultKeepsFullTraceArraysAfterPersistenceAndReload() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Map<String, Object> created = fixture.service.create("owner", UUID.randomUUID().toString(), fixture.config(), Map.of());
            assertEquals("SUCCEEDED", created.get("status"));
            assertTraceArrays(created.get("result"));
            String trialId = (String) created.get("trialId");
            String persisted = fixture.store.get(trialId, "owner").resultJson();
            assertFalse("新存储不能产生供浏览器误读的追踪引用对象", persisted.contains("\"$ref\""));
            assertTraceArrays(fixture.service.get("owner", trialId).get("result"));
            assertTraceArrays(fixture.service.list("owner").get(0).get("result"));
            assertEquals("读取历史不能再访问供应商", 1, fixture.submissions.get());
        }
    }

    @Test public void billingUnknownKeepsObservedResponseTraceArraysWithoutCallingProviderAgain() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.bills.fail = true;
            Map<String, Object> created = fixture.service.create("owner", UUID.randomUUID().toString(), fixture.config(), Map.of());
            assertEquals("UNKNOWN", created.get("status"));
            assertEquals(false, created.get("canResume"));
            assertTraceArrays(created.get("result"));
            String trialId = (String) created.get("trialId");
            assertTraceArrays(fixture.service.get("owner", trialId).get("result"));
            assertEquals(1, fixture.submissions.get());
        }
    }

    @Test public void storedTraceAliasesRecoverOnlyFromRealTraceArraysWithoutChangingBusinessRefs() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Map<String, Object> created = fixture.service.create("owner", UUID.randomUUID().toString(), fixture.config(), Map.of());
            String trialId = (String) created.get("trialId");
            String legacy = """
                    {"success":true,"body":{"$ref":"$.externalCall.traceSteps","traceSteps":{"$ref":"business-only"}},
                     "externalCall":{"traceSteps":[{"sequence":1,"type":"WORKFLOW_STEP","output":{
                       "success":true,"body":{"$ref":"supplier-literal"},"externalCall":{"traceSteps":[{"sequence":1,"type":"EXTERNAL_RESPONSE"}]},
                       "traceSteps":{"$ref":"$.externalCall.traceSteps[0].output.externalCall.traceSteps"}}}]},
                     "traceSteps":{"$ref":"$.externalCall.traceSteps"},
                     "steps":{"submit":{"success":true,"traceSteps":[{"sequence":1,"type":"EXTERNAL_REQUEST"}],
                       "externalCall":{"traceSteps":{"$ref":"$.steps.submit.traceSteps"}}},
                       "unavailable":{"traceSteps":{"$ref":"$.missing.traceSteps"},"externalCall":{}},
                       "empty":{"traceSteps":{"$ref":"$.missing.traceSteps"},"externalCall":{"traceSteps":[]}}}}
                    """;
            replaceStoredResult(fixture, trialId, legacy);
            for (Map<String, Object> view : List.of(fixture.service.get("owner", trialId), fixture.service.list("owner").get(0))) {
                Map<?, ?> result = (Map<?, ?>) view.get("result");
                List<?> steps = assertTraceArrays(result);
                Map<?, ?> output = (Map<?, ?>) ((Map<?, ?>) steps.get(0)).get("output");
                assertTraceArrays(output);
                assertEquals("supplier-literal", ((Map<?, ?>) output.get("body")).get("$ref"));
                Map<?, ?> business = (Map<?, ?>) result.get("body");
                assertEquals("$.externalCall.traceSteps", business.get("$ref"));
                assertEquals("business-only", ((Map<?, ?>) business.get("traceSteps")).get("$ref"));
                Map<?, ?> completed = (Map<?, ?>) result.get("steps");
                assertTraceArrays(completed.get("submit"));
                assertEquals("不能凭空制造空追踪", Map.of("$ref", "$.missing.traceSteps"),
                        ((Map<?, ?>) completed.get("unavailable")).get("traceSteps"));
                assertEquals("空的备用槽位也不能证明旧引用已经恢复", Map.of("$ref", "$.missing.traceSteps"),
                        ((Map<?, ?>) completed.get("empty")).get("traceSteps"));
            }
            assertEquals("读取兼容不迁移原历史记录", legacy, fixture.store.get(trialId, "owner").resultJson());
            assertEquals(1, fixture.submissions.get());
        }
    }

    @Test public void resultSerializationRetainsPreciseNumbersAndLiteralBusinessRef() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var decimal = new BigDecimal("9007199254740993.12345678901234567890");
            var integer = new java.math.BigInteger("9007199254740993123456789");
            Map<String, Object> business = new LinkedHashMap<>();
            business.put("decimal", decimal); business.put("integer", integer);
            business.put("schema", Map.of("$ref", "#/definitions/score"));
            var phases = List.of(Map.of("sequence", 1, "type", "EXTERNAL_RESPONSE"));
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true); response.put("body", business);
            response.put("externalCall", Map.of("traceSteps", phases)); response.put("traceSteps", phases);
            ReflectionTestUtils.setField(fixture.service, "invoker", new ExternalApiInvokeService() {
                @Override Map<String, Object> invokeTrial(RuleExternalApiConfig config, Map<String, Object> params, Map<String, Object> pending) { return response; }
            });
            var created = fixture.service.create("owner", UUID.randomUUID().toString(), fixture.config(), Map.of());
            assertEquals("SUCCEEDED", created.get("status"));
            assertTraceArrays(created.get("result"));
            Map<?, ?> storedBody = (Map<?, ?>) ((Map<?, ?>) created.get("result")).get("body");
            assertEquals(decimal, storedBody.get("decimal")); assertEquals(integer, storedBody.get("integer"));
            assertEquals(Map.of("$ref", "#/definitions/score"), storedBody.get("schema"));
            assertSame("不能清空/替换共享运行结果来临时序列化", phases, response.get("traceSteps"));
            assertSame(phases, ((Map<?, ?>) response.get("externalCall")).get("traceSteps"));
        }
    }

    @Test public void realCycleFailsExplicitlyWithoutStackOverflowOrResending() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var invoked = new AtomicInteger();
            Map<String, Object> cycle = new LinkedHashMap<>(); cycle.put("self", cycle);
            ReflectionTestUtils.setField(fixture.service, "invoker", new ExternalApiInvokeService() {
                @Override Map<String, Object> invokeTrial(RuleExternalApiConfig config, Map<String, Object> params, Map<String, Object> pending) {
                    invoked.incrementAndGet(); return Map.of("success", true, "body", cycle);
                }
            });
            Map<String, Object> created = fixture.service.create("owner", UUID.randomUUID().toString(), fixture.config(), Map.of());
            assertEquals("不能把无法表示的循环业务结果伪装成成功", "FAILED", created.get("status"));
            assertEquals(false, created.get("canResume")); assertNotNull(created.get("errorMessage"));
            fixture.service.resume("owner", (String) created.get("trialId"));
            assertEquals(1, invoked.get());
        }
    }

    @Test public void unrepresentableObservedBillingResponseRemainsUnknownWithoutRetry() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var invoked = new AtomicInteger();
            Map<String, Object> cycle = new LinkedHashMap<>(); cycle.put("self", cycle);
            ReflectionTestUtils.setField(fixture.service, "invoker", new ExternalApiInvokeService() {
                @Override Map<String, Object> invokeTrial(RuleExternalApiConfig config, Map<String, Object> params, Map<String, Object> pending) {
                    invoked.incrementAndGet();
                    throw new BillingPersistenceException("费用尚未确认", new IllegalStateException("账单存储不可用"),
                            Map.of("success", true, "body", cycle));
                }
            });
            Map<String, Object> created = fixture.service.create("owner", UUID.randomUUID().toString(), fixture.config(), Map.of());
            assertEquals("响应快照异常不能把费用未确认降级为普通失败", "UNKNOWN", created.get("status"));
            assertEquals(false, created.get("canResume"));
            assertTrue(String.valueOf(created.get("errorMessage")).contains("费用尚未确认"));
            assertTrue("必须说明无法保存响应，不输出循环引用冒充正常结果",
                    String.valueOf(created.get("errorMessage")).contains("响应快照"));
            assertNull(created.get("result"));
            fixture.service.resume("owner", (String) created.get("trialId"));
            assertEquals(1, invoked.get());
        }
    }

    private static List<?> assertTraceArrays(Object value) {
        assertTrue("必须保留实际调用结果", value instanceof Map<?, ?>);
        Map<?, ?> result = (Map<?, ?>) value;
        assertTrue("顶层追踪必须是完整阶段数组", result.get("traceSteps") instanceof List<?>);
        Map<?, ?> call = (Map<?, ?>) result.get("externalCall");
        assertTrue("externalCall追踪必须是完整阶段数组", call.get("traceSteps") instanceof List<?>);
        List<?> phases = (List<?>) result.get("traceSteps");
        assertFalse("不能以空数组掩盖阶段丢失", phases.isEmpty());
        assertEquals(phases, call.get("traceSteps"));
        assertTrue(((Map<?, ?>) phases.get(0)).get("type") instanceof String);
        return phases;
    }

    private static void replaceStoredResult(Fixture fixture, String id, String result) {
        var s = fixture.store.get(id, "owner");
        fixture.store.rows.put(id, new ExternalApiTrialStore.State(s.id(), id, s.owner(), s.requestKey(), s.datasourceId(),
                s.snapshotDigest(), s.snapshotJson(), s.status(), s.attemptNo(), s.leaseOwner(), s.leaseUntil(), s.expiresAt(),
                s.pendingJson(), result, s.errorMessage(), s.createdAt(), s.updatedAt()));
    }

    @Test public void realDatasourceMetadataDatesRemainEqualAfterFrozenEnvelopeRoundTrip() throws Exception {
        try (Fixture fixture = new Fixture()) {
            completeSourceMetadata(fixture.source);
            Map<String, Object> result = fixture.service.create("owner", UUID.randomUUID().toString(), fixture.config(), Map.of("amount", 88));
            assertEquals("未发生变更的数据源不能被日期表示差异阻断", "SUCCEEDED", result.get("status"));
            assertEquals(1, fixture.submissions.get());
            JSONObject frozen = JSON.parseObject(fixture.store.get((String) result.get("trialId"), "owner").snapshotJson()).getJSONObject("datasources").getJSONObject("1");
            assertEquals(fixture.source.getCreateTime(), frozen.getObject("createTime", java.time.LocalDateTime.class));
            assertEquals(fixture.source.getUpdateTime(), frozen.getObject("updateTime", java.time.LocalDateTime.class));
        }
    }

    @Test public void realDateOrCredentialOrBusinessConfigurationChangeStillStopsBeforeNetwork() throws Exception {
        List<java.util.function.Consumer<RuleExternalDatasource>> changes = List.of(
                source -> source.setUpdateTime(source.getUpdateTime().plusSeconds(1)),
                source -> source.setCreateTime(source.getCreateTime().plusSeconds(1)),
                source -> source.setAuthConfig("{\"credential\":\"changed-test-only-value\"}"),
                source -> source.setDatasourceName("真实名称已变更"),
                source -> source.setBaseUrl(source.getBaseUrl() + "/changed"));
        for (var change : changes) {
            try (Fixture fixture = new Fixture()) {
                completeSourceMetadata(fixture.source);
                Map<String, Object> first = fixture.service.create("owner", UUID.randomUUID().toString(), fixture.config(), Map.of());
                assertEquals("SUCCEEDED", first.get("status"));
                String id = (String) first.get("trialId"); fixture.store.force(id, "WAITING");
                change.accept(fixture.source);
                assertEquals("FAILED", fixture.service.resume("owner", id).get("status"));
                assertEquals("变更依赖不允许继续发供应商请求", 1, fixture.submissions.get());
            }
        }
    }

    @Test public void linkedApiDatesAndAllMetadataAreComparedAsTheSameEntityWithoutDroppingUnknownFields() throws Exception {
        try (Fixture fixture = new Fixture()) {
        RuleExternalApiConfig api = new RuleExternalApiConfig();
        api.setId(23L); api.setDatasourceId(1L); api.setApiCode("owned-api"); api.setApiName("关联接口");
        api.setDatasourceName("所属来源"); api.setDatasourceCode("owned-source"); api.setStatus(1);
        api.setRequestMethod("POST"); api.setRequestMode("SYNC"); api.setEndpointUrl("/submit");
        api.setContentType("application/json"); api.setHeaderConfig("{\"X-Example\":\"kept\"}");
        api.setAuthMode("CUSTOM"); api.setAuthApiConfig("{\"testOnlyCredential\":\"unchanged\"}");
        api.setUnitPrice(new BigDecimal("0.125000")); api.setQpsLimit(new BigDecimal("12.3400"));
        api.setTimeoutMs(1000); api.setDescription("所有元字段继续进入冻结比较");
        api.setCreateTime(java.time.LocalDateTime.of(2026, 10, 9, 2, 33, 13, 123456000)); api.setUpdateTime(api.getCreateTime());
        ReflectionTestUtils.setField(fixture.service, "apiMapper", mapper(RuleExternalApiConfigMapper.class, api));
        RuleExternalApiConfig trialConfig = fixture.config();
        trialConfig.setExecutionConfig("{\"version\":2,\"requestFields\":[],\"steps\":[{\"id\":\"linked\",\"type\":\"HTTP\",\"apiConfigId\":23}]}");
        JSONObject envelope = ReflectionTestUtils.invokeMethod(fixture.service, "freeze", trialConfig, Map.of());
        JSONObject frozen = JSON.parseObject(envelope.toJSONString()).getJSONObject("apis").getJSONObject("23");
        var service = fixture.service;
        assertEquals(api.getCreateTime(), frozen.getObject("createTime", java.time.LocalDateTime.class));
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(service, "sameSnapshot", api, frozen));
        api.setUnitPrice(new BigDecimal("0.125001"));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(service, "sameSnapshot", api, frozen));
        api.setUnitPrice(new BigDecimal("0.125000")); api.setUpdateTime(api.getUpdateTime().plusNanos(1000));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(service, "sameSnapshot", api, frozen));
        api.setUpdateTime(api.getCreateTime()); frozen.put("unknownFrozenField", "must-not-be-discarded");
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(service, "sameSnapshot", api, frozen));
        }
    }

    private static void completeSourceMetadata(RuleExternalDatasource source) {
        source.setProjectId(7L); source.setScope("PROJECT"); source.setProjectName("测试项目元信息");
        source.setDatasourceName("只用于试运行回归的来源"); source.setProviderName("本机模拟供应商"); source.setProtocol("HTTP");
        source.setAuthConfig("{\"credential\":\"unchanged-test-only-value\"}"); source.setTokenCacheSeconds(300);
        source.setDescription("完整已审批来源元字段");
        source.setCreateTime(java.time.LocalDateTime.of(2026, 10, 9, 2, 33, 13)); source.setUpdateTime(source.getCreateTime());
    }

    @Test public void realSynchronousCallHasIndependentIdentityFrozenConfigAndIdempotentCreate() throws Exception {
        try (Fixture fixture = new Fixture()) {
            RuleExternalApiConfig config = fixture.config();
            config.setUnitPrice(new BigDecimal("0.25"));
            String key = UUID.randomUUID().toString();
            Map<String, Object> first = fixture.service.create("owner", key, config, Map.of("amount", 88));
            assertEquals("SUCCEEDED", first.get("status"));
            assertEquals(1, fixture.submissions.get());
            assertEquals(1, fixture.bills.calls.size());
            assertEquals(new BigDecimal("0.25"), fixture.bills.calls.get(0).getUnitPrice());
            assertTrue(fixture.bills.calls.get(0).getId() < 0);
            assertNull(config.getId());
            assertNull(config.getExecutionCallId());
            assertEquals(first.get("trialId"), fixture.service.create("owner", key, config, Map.of("amount", 88)).get("trialId"));
            assertEquals(1, fixture.submissions.get());
            config.setUnitPrice(BigDecimal.TEN);
            expectRejected(() -> fixture.service.create("owner", key, config, Map.of("amount", 88)), "不能更改");
            assertEquals(1, fixture.submissions.get());
            Map<String, Object> next = fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of());
            assertNotEquals(first.get("trialId"), next.get("trialId"));
            assertNotEquals(fixture.bills.calls.get(0).getId(), fixture.bills.calls.get(1).getId());
            assertEquals(0, fixture.pool.size());
            assertNull(ExternalApiTrialContext.current());
            assertEquals(Integer.MAX_VALUE, RequestDeadlineContext.remainingMillis());
        }
    }

    @Test public void asynchronousPollingResumesWithoutRepeatingSubmissionAndKeepsOriginalBillingCallId() throws Exception {
        try (Fixture fixture = new Fixture()) {
            RuleExternalApiConfig config = fixture.config();
            config.setRequestMode("ASYNC");
            config.setExceptionStrategy("WAIT");
            config.setAsyncResultMode("POLL");
            config.setAsyncTimeoutMs(1000);
            config.setAsyncPollConfig("{\"taskIdPath\":\"body.taskId\",\"statusPath\":\"body.status\",\"successValue\":\"DONE\",\"resultEndpointUrl\":\"/poll\",\"intervalMs\":1,\"maxAttempts\":1}");
            Map<String, Object> first = fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of());
            assertEquals("WAITING", first.get("status"));
            assertEquals(true, first.get("canResume"));
            assertEquals(1, fixture.submissions.get());
            fixture.ready = true;
            Map<String, Object> completed = fixture.service.resume("owner", (String) first.get("trialId"));
            assertEquals("SUCCEEDED", completed.get("status"));
            assertEquals(1, fixture.submissions.get());
            assertEquals(2, fixture.polls.get());
            assertEquals(fixture.bills.calls.get(0).getExecutionCallId(), fixture.bills.calls.get(1).getExecutionCallId());
            fixture.service.resume("owner", (String) first.get("trialId"));
            assertEquals(2, fixture.polls.get());
        }
    }

    @Test public void legacyAsyncResumeKeepsPendingTaskWhenBudgetExpiresBeforePollSend() throws Exception {
        var now = new java.util.concurrent.atomic.AtomicLong();
        RequestDeadlineContext.setNanoTimeSource(now::get);
        try (Fixture fixture = new Fixture()) {
            fixture.service.slice = 10_000;
            var expireNextPoll = new java.util.concurrent.atomic.AtomicBoolean();
            Object invoker = ReflectionTestUtils.getField(fixture.service, "invoker");
            ReflectionTestUtils.setField(invoker, "externalApiScriptService", new ExternalApiScriptService() {
                @Override public Object executeRequest(String script, Map<String, Object> context) {
                    if (script != null && script.contains("expire-on-resume") && expireNextPoll.compareAndSet(true, false)) {
                        now.addAndGet(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(10_001));
                    }
                    return context.get("body");
                }
            });
            RuleExternalApiConfig config = fixture.config();
            config.setRequestMode("ASYNC"); config.setExceptionStrategy("WAIT"); config.setAsyncResultMode("POLL");
            config.setAsyncTimeoutMs(10_000);
            config.setAsyncPollConfig("{\"taskIdPath\":\"body.taskId\",\"statusPath\":\"body.status\",\"successValue\":\"DONE\","
                    + "\"resultEndpointUrl\":\"/poll\",\"intervalMs\":1,\"maxAttempts\":1,\"requestScript\":\"return body; // expire-on-resume\"}");
            Map<String, Object> first = fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of());
            assertEquals("WAITING", first.get("status"));
            String id = (String) first.get("trialId");
            assertEquals(1, fixture.submissions.get()); assertEquals(1, fixture.polls.get());
            expireNextPoll.set(true);
            Map<String, Object> paused = fixture.service.resume("owner", id);
            assertEquals("本片预算耗尽前尚未发poll", 1, fixture.polls.get());
            assertEquals("原任务仍可恢复: " + paused.get("errorMessage"), "WAITING", paused.get("status"));
            assertEquals(true, paused.get("canResume"));
            JSONObject saved = JSON.parseObject(fixture.store.get(id, "owner").pendingJson());
            assertEquals("task1", ExternalApiRequestPlan.read(saved, "continuation.taskId"));
            fixture.ready = true;
            assertEquals("SUCCEEDED", fixture.service.resume("owner", id).get("status"));
            assertEquals(1, fixture.submissions.get()); assertEquals(2, fixture.polls.get());
        } finally { RequestDeadlineContext.clear(); RequestDeadlineContext.resetNanoTimeSource(); }
    }

    @Test public void multiStepWaitResumesAtPendingStepUsingDistinctStableStepIdentity() throws Exception {
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong();
        RequestDeadlineContext.setNanoTimeSource(now::get);
        try (Fixture fixture = new Fixture()) {
            fixture.service.slice = 120;
            var expireOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
            fixture.store.afterCheckpoint = pending -> {
                JSONObject saved = JSON.parseObject(pending);
                if (ExternalApiRequestPlan.read(saved, "continuation.results.submit") != null && expireOnce.compareAndSet(true, false)) {
                    now.addAndGet(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(120));
                }
            };
            RuleExternalApiConfig config = fixture.config();
            config.setRequestMode("ASYNC");
            config.setAsyncTimeoutMs(1000);
            config.setExceptionStrategy("WAIT");
            config.setExecutionConfig("""
                {"version":2,"requestFields":[],"steps":[
                  {"id":"submit","type":"HTTP","endpointUrl":"/submit","requestFields":[]},
                  {"id":"wait","type":"HTTP","endpointUrl":"/poll","requestFields":[],
                   "poll":{"intervalMs":100,"maxAttempts":0,"until":{"path":"body.status","operator":"==","value":"DONE"}}}]}
                """);
            Map<String, Object> first = fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of());
            assertEquals("首次执行: " + first.get("errorMessage") + "; submit=" + fixture.submissions.get()
                    + "; poll=" + fixture.polls.get(), "WAITING", first.get("status"));
            assertEquals(1, fixture.submissions.get());
            fixture.ready = true;
            Map<String, Object> completed = fixture.service.resume("owner", (String) first.get("trialId"));
            assertEquals("SUCCEEDED", completed.get("status"));
            assertEquals(1, fixture.submissions.get());
            assertTrue(fixture.guardIdentities.stream().distinct().count() >= 2);
            assertTrue(fixture.guardIdentities.stream().allMatch(id -> id < 0));
        } finally {
            RequestDeadlineContext.clear();
            RequestDeadlineContext.resetNanoTimeSource();
        }
    }

    @Test public void deadlineBeforePendingPollIsSentKeepsCompletedSubmissionResumable() throws Exception {
        assertDeadlineBeforePollKeepsCallbacks("WAIT");
    }

    @Test public void failFastCannotDiscardUnsentPollWhenLocalSliceExpires() throws Exception {
        assertDeadlineBeforePollKeepsCallbacks("FAIL_FAST");
    }

    @Test public void returnDefaultCannotDiscardUnsentPollWhenLocalSliceExpires() throws Exception {
        assertDeadlineBeforePollKeepsCallbacks("RETURN_DEFAULT");
    }

    private void assertDeadlineBeforePollKeepsCallbacks(String strategy) throws Exception {
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong();
        RequestDeadlineContext.setNanoTimeSource(now::get);
        try (Fixture fixture = new Fixture()) {
            // 单调时钟精确控制切片耗尽位置，真实本机HTTP仍有充足传输预算。
            fixture.service.slice = 10_000;
            var expireOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
            ExternalApiInvokeService invoker = (ExternalApiInvokeService) ReflectionTestUtils.getField(fixture.service, "invoker");
            ExternalApiCallbackStore callbacks = ExternalApiCallbackStoreTest.store();
            ReflectionTestUtils.setField(invoker, "callbackStore", callbacks);
            ReflectionTestUtils.setField(invoker, "externalApiScriptService", new ExternalApiScriptService() {
                @Override public Object executeRequest(String script, Map<String, Object> context) {
                    if (script != null && script.contains("expire-before-poll") && expireOnce.compareAndSet(true, false)) {
                        now.addAndGet(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(10_001));
                    }
                    return context.get("body");
                }
            });
            RuleExternalApiConfig config = fixture.config();
            config.setRequestMode("ASYNC"); config.setAsyncTimeoutMs(10_000); config.setExceptionStrategy(strategy);
            config.setFallbackValue("{\"supplierFallback\":true}");
            config.setExecutionConfig("""
                {"version":2,"requestFields":[],"steps":[
                  {"id":"submit","type":"HTTP","endpointUrl":"/submit","requestFields":[]},
                  {"id":"wait","type":"HTTP","endpointUrl":"/poll","requestFields":[],"requestScript":"return body; // expire-before-poll",
                   "poll":{"intervalMs":100,"maxAttempts":0,"until":{"path":"body.status","operator":"==","value":"DONE"}}},
                  {"id":"notification","type":"CALLBACK","callback":{"url":"http://localhost/api/external-callback/${invocationId}",
                    "signatureHeader":"X-Signature","signatureSecret":"test-secret","statusPath":"body.status","successValue":"DONE"}}]}
                """);
            Map<String, Object> first = fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of());
            String trialId = (String) first.get("trialId");
            JSONObject saved = JSON.parseObject(fixture.store.get(trialId, "owner").pendingJson());
            Map<String, Object> diagnostic = new LinkedHashMap<>();
            diagnostic.put("status", first.get("status")); diagnostic.put("errorMessage", first.get("errorMessage"));
            diagnostic.put("submissions", fixture.submissions.get()); diagnostic.put("polls", fixture.polls.get());
            diagnostic.put("pendingStep", ExternalApiRequestPlan.read(saved, "continuation.stepId"));
            System.out.println("deadlineBeforePollDiagnostic=" + JSON.toJSONString(diagnostic));
            assertEquals("已完成的提交请求不能重复发出", 1, fixture.submissions.get());
            assertEquals("预算在准备阶段耗尽，本次poll尚未发网", 0, fixture.polls.get());
            assertNotNull("成功提交的检查点应仍保留", ExternalApiRequestPlan.read(saved, "continuation.results.submit"));
            assertEquals(JSON.toJSONString(diagnostic), "WAITING", first.get("status"));
            String registration = saved.getJSONArray("callbackIds").getString(0);
            byte[] callback = "{\"status\":\"DONE\",\"score\":88}".getBytes(StandardCharsets.UTF_8);
            callbacks.accept(registration, ExternalApiCallbackStoreTest.signature(callback), callback);
            fixture.ready = true;
            Map<String, Object> completed = fixture.service.resume("owner", trialId);
            assertEquals("SUCCEEDED", completed.get("status"));
            assertEquals(1, fixture.submissions.get());
            assertEquals(1, fixture.polls.get());
            assertThrows(IllegalArgumentException.class, () -> callbacks.accept(registration, ExternalApiCallbackStoreTest.signature(callback), callback));
        } finally {
            RequestDeadlineContext.clear();
            RequestDeadlineContext.resetNanoTimeSource();
        }
    }

    @Test public void deadlineAfterKnownSubmitResponseCannotMakeSubmissionRepeatable() throws Exception {
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong();
        RequestDeadlineContext.setNanoTimeSource(now::get);
        try (Fixture fixture = new Fixture()) {
            fixture.service.slice = 10_000;
            var receivedStatus = new AtomicInteger();
            Object invoker = ReflectionTestUtils.getField(fixture.service, "invoker");
            ReflectionTestUtils.setField(invoker, "externalApiScriptService", new ExternalApiScriptService() {
                @Override public Object executeResponse(String script, Map<String, Object> context) {
                    receivedStatus.set(((Number) context.get("httpStatus")).intValue());
                    now.addAndGet(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(10_001));
                    return context.get("body");
                }
            });
            RuleExternalApiConfig config = twoStepConfig(fixture, "WAIT");
            config.setResponseScript("return body;");
            Map<String, Object> first = fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of());
            assertEquals("响应处理钩子必须在真实200响应之后耗尽本地预算", 200, receivedStatus.get());
            assertEquals(1, fixture.submissions.get()); assertEquals(0, fixture.polls.get());
            assertNotEquals("WAITING", first.get("status"));
            assertEquals(false, first.get("canResume"));
            fixture.service.resume("owner", (String) first.get("trialId"));
            assertEquals("已确认供应商收到submit，禁止重新提交", 1, fixture.submissions.get());
        } finally { RequestDeadlineContext.clear(); RequestDeadlineContext.resetNanoTimeSource(); }
    }

    @Test public void issuedSubmissionWithoutResponseRemainsUnknownAndCannotResume() throws Exception {
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong();
        RequestDeadlineContext.setNanoTimeSource(now::get);
        try (Fixture fixture = new Fixture()) {
            fixture.service.slice = 10_000;
            fixture.dropSubmitResponse = true;
            fixture.beforeSend = () -> now.addAndGet(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(10_001));
            Map<String, Object> first = fixture.service.create("owner", UUID.randomUUID().toString(), twoStepConfig(fixture, "WAIT"), Map.of());
            assertEquals(1, fixture.submissions.get()); assertEquals(0, fixture.polls.get());
            assertEquals("已发无响应诊断: " + first.get("errorMessage"), "UNKNOWN", first.get("status"));
            assertEquals(false, first.get("canResume"));
            fixture.service.resume("owner", (String) first.get("trialId"));
            assertEquals("响应未知不能重新发submit", 1, fixture.submissions.get());
        } finally { RequestDeadlineContext.clear(); RequestDeadlineContext.resetNanoTimeSource(); }
    }

    @Test public void ordinarySupplierFailuresRetainConfiguredStrategyRatherThanBudgetWaiting() throws Exception {
        for (String strategy : List.of("WAIT", "FAIL_FAST", "RETURN_DEFAULT")) {
            try (Fixture fixture = new Fixture()) {
                fixture.submitStatus = 500;
                RuleExternalApiConfig config = fixture.config();
                config.setExceptionStrategy(strategy); config.setFallbackValue("{\"supplierFallback\":true}");
                Map<String, Object> first = fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of());
                assertEquals(strategy, "FAILED", first.get("status"));
                assertEquals(false, first.get("canResume"));
                assertEquals(1, fixture.submissions.get());
                if ("RETURN_DEFAULT".equals(strategy)) {
                    assertEquals(true, ExternalApiRequestPlan.read(first, "result.fallback"));
                    assertEquals(true, ExternalApiRequestPlan.read(first, "result.body.supplierFallback"));
                }
            }
        }
    }

    private RuleExternalApiConfig twoStepConfig(Fixture fixture, String strategy) {
        RuleExternalApiConfig config = fixture.config();
        config.setRequestMode("ASYNC"); config.setAsyncTimeoutMs(10_000); config.setExceptionStrategy(strategy);
        config.setExecutionConfig("""
            {"version":2,"requestFields":[],"steps":[
              {"id":"submit","type":"HTTP","endpointUrl":"/submit","requestFields":[]},
              {"id":"wait","type":"HTTP","endpointUrl":"/poll","requestFields":[]}]}
            """);
        return config;
    }

    @Test public void stepCallUuidsAreStoredBeforeNetworkAndInvocationUsesTheWinningSnapshot() throws Exception {
        try (Fixture fixture = new Fixture()) {
            RuleExternalApiConfig config = fixture.config(); config.setRequestMode("ASYNC"); config.setAsyncTimeoutMs(1000);
            config.setExecutionConfig("""
                {"version":2,"requestFields":[],"steps":[
                  {"id":"first","type":"HTTP","endpointUrl":"/submit","requestFields":[]},
                  {"id":"second","type":"HTTP","endpointUrl":"/submit","requestFields":[]}]}
                """);
            String firstId = UUID.randomUUID().toString(), secondId = UUID.randomUUID().toString();
            fixture.store.snapshotOnCreate = json -> {
                JSONObject proposed = JSON.parseObject(json);
                assertNotNull("必须在开始执行之前生成待持久化的步骤身份", proposed.getJSONObject("stepCallIds"));
                JSONObject winner = JSON.parseObject(json);
                winner.put("stepCallIds", Map.of("first", firstId, "second", secondId));
                return winner.toJSONString();
            };
            java.util.concurrent.atomic.AtomicReference<JSONObject> persistedAtSend = new java.util.concurrent.atomic.AtomicReference<>();
            fixture.beforeSend = () -> persistedAtSend.set(JSON.parseObject(fixture.store.list("owner", 20).get(0).snapshotJson()));
            Map<String, Object> result = fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of());
            assertEquals("SUCCEEDED", result.get("status"));
            assertEquals(firstId, persistedAtSend.get().getJSONObject("stepCallIds").getString("first"));
            assertEquals(firstId, ExternalApiRequestPlan.read(result, "result.steps.first.callId"));
            assertEquals(secondId, ExternalApiRequestPlan.read(result, "result.steps.second.callId"));
            assertEquals(result.get("trialId"), ExternalApiRequestPlan.read(result, "result.rootTraceId"));
            assertEquals(result.get("trialId"), ExternalApiRequestPlan.read(result, "result.steps.first.externalCall.rootTraceId"));
            assertEquals(36, firstId.length()); assertEquals(36, secondId.length());
            assertEquals(2, fixture.submissions.get());
        }
    }

    @Test public void incompleteStepIdentitySnapshotFailsBeforeAnySupplierCall() throws Exception {
        try (Fixture fixture = new Fixture()) {
            RuleExternalApiConfig config = fixture.config(); config.setRequestMode("ASYNC"); config.setAsyncTimeoutMs(1000);
            config.setExecutionConfig("{\"version\":2,\"requestFields\":[],\"steps\":[{\"id\":\"first\",\"type\":\"HTTP\",\"endpointUrl\":\"/submit\",\"requestFields\":[]}]}");
            fixture.store.snapshotOnCreate = json -> {
                JSONObject snapshot = JSON.parseObject(json); snapshot.remove("stepCallIds"); return snapshot.toJSONString();
            };
            assertEquals("FAILED", fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of()).get("status"));
            assertEquals(0, fixture.submissions.get());
        }
    }

    @Test public void cancelledOrUnknownTrialNeverResubmitsAndOtherOwnerCannotSeeIt() throws Exception {
        try (Fixture fixture = new Fixture()) {
            RuleExternalApiConfig config = fixture.config();
            Map<String, Object> completed = fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of());
            String id = (String) completed.get("trialId");
            expectRejected(() -> fixture.service.get("other", id), "不属于");
            assertTrue(fixture.service.list("other").isEmpty());
            fixture.store.force(id, "WAITING");
            assertEquals("CANCELLED", fixture.service.cancel("owner", id).get("status"));
            assertEquals(false, fixture.service.resume("owner", id).get("canResume"));
            fixture.store.force(id, "UNKNOWN");
            assertEquals("UNKNOWN", fixture.service.resume("owner", id).get("status"));
            assertEquals(1, fixture.submissions.get());
        }
    }

    @Test public void callbackChainSurvivesRequestBoundaryAndCancellationClosesItsRegistration() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.service.slice = 180;
            ExternalApiCallbackStore callbacks = ExternalApiCallbackStoreTest.store();
            ExternalApiInvokeService invoker = (ExternalApiInvokeService) ReflectionTestUtils.getField(fixture.service, "invoker");
            ReflectionTestUtils.setField(invoker, "callbackStore", callbacks);
            RuleExternalApiConfig config = fixture.config(); config.setRequestMode("ASYNC"); config.setAsyncTimeoutMs(1000);
            config.setExceptionStrategy("WAIT");
            config.setExecutionConfig("""
                {"version":2,"requestFields":[],"steps":[
                  {"id":"submit","type":"HTTP","endpointUrl":"/submit","requestFields":[]},
                  {"id":"callback","type":"CALLBACK","callback":{"url":"http://localhost/api/external-callback/${invocationId}",
                    "signatureHeader":"X-Signature","signatureSecret":"test-secret","statusPath":"body.status","successValue":"DONE"}}]}
                """);
            Map<String, Object> first = fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of());
            assertEquals("WAITING", first.get("status"));
            String trialId = (String) first.get("trialId");
            String registration = JSON.parseObject(fixture.store.get(trialId, "owner").pendingJson()).getJSONArray("callbackIds").getString(0);
            byte[] reply = "{\"job\":\"task1\",\"status\":\"DONE\",\"score\":88}".getBytes(StandardCharsets.UTF_8);
            callbacks.accept(registration, ExternalApiCallbackStoreTest.signature(reply), reply);
            assertEquals("SUCCEEDED", fixture.service.resume("owner", trialId).get("status"));
            assertEquals(1, fixture.submissions.get());
            assertThrows(IllegalArgumentException.class, () -> callbacks.accept(registration, ExternalApiCallbackStoreTest.signature(reply), reply));
            Map<String, Object> second = fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of());
            String cancelledId = (String) second.get("trialId");
            String cancelledCallback = JSON.parseObject(fixture.store.get(cancelledId, "owner").pendingJson()).getJSONArray("callbackIds").getString(0);
            assertEquals("CANCELLED", fixture.service.cancel("owner", cancelledId).get("status"));
            assertThrows(IllegalArgumentException.class, () -> callbacks.accept(cancelledCallback, ExternalApiCallbackStoreTest.signature(reply), reply));
            fixture.service.resume("owner", cancelledId);
            assertEquals(2, fixture.submissions.get());
        }
    }

    @Test public void changedOrDisabledDatasourceStopsResumeBeforeNetwork() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Map<String, Object> first = fixture.service.create("owner", UUID.randomUUID().toString(), fixture.config(), Map.of());
            String id = (String) first.get("trialId");
            fixture.store.force(id, "WAITING");
            fixture.source.setStatus(0);
            assertEquals("FAILED", fixture.service.resume("owner", id).get("status"));
            assertEquals(1, fixture.submissions.get());
            expectRejected(() -> fixture.service.create("owner", UUID.randomUUID().toString(), fixture.config(), Map.of()), "停用");
            assertEquals(1, fixture.submissions.get());
        }
    }

    @Test public void failedDurableCheckpointCannotSendNextWorkflowStep() throws Exception {
        try (Fixture fixture = new Fixture()) {
            RuleExternalApiConfig config = fixture.config();
            config.setRequestMode("ASYNC"); config.setAsyncTimeoutMs(1000);
            config.setExecutionConfig("""
                {"version":2,"requestFields":[],"steps":[
                  {"id":"first","type":"HTTP","endpointUrl":"/submit","requestFields":[]},
                  {"id":"second","type":"HTTP","endpointUrl":"/submit","requestFields":[]}]}
                """);
            fixture.store.rejectCheckpoint = true;
            Map<String, Object> response = fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of());
            assertEquals(1, fixture.submissions.get());
            assertNotEquals("SUCCEEDED", response.get("status"));
        }
    }

    @Test public void delayedResumeReadsCheckpointAfterWinningLeaseInsteadOfReplayingItsEarlierRead() throws Exception {
        try (Fixture fixture = new Fixture()) {
            String id = (String) fixture.service.create("owner", UUID.randomUUID().toString(), fixture.config(), Map.of()).get("trialId");
            fixture.store.update(id, "WAITING", "{\"continuation\":{\"taskId\":\"old\"}}", null, null);
            fixture.store.beforeClaim = () -> fixture.store.update(id, "WAITING", "{\"continuation\":{\"taskId\":\"new\"}}", null, null);
            java.util.concurrent.atomic.AtomicReference<Map<String, Object>> received = new java.util.concurrent.atomic.AtomicReference<>();
            ReflectionTestUtils.setField(fixture.service, "invoker", new ExternalApiInvokeService() {
                @Override Map<String, Object> invokeTrial(RuleExternalApiConfig config, Map<String, Object> params, Map<String, Object> pending) {
                    received.set(pending); return Map.of("success", true);
                }
                @Override void cleanTrialResources(long row, JSONObject snapshot, Map<String, Object> saved) { }
            });
            assertEquals("SUCCEEDED", fixture.service.resume("owner", id).get("status"));
            assertEquals("new", received.get().get("taskId"));
        }
    }

    @Test public void trialNeverFallsBackToLiveDependencyWhenItWasNotFrozen() throws Exception {
        try (Fixture fixture = new Fixture(); ExternalApiTrialContext trial = new ExternalApiTrialContext("trial", 1,
                System.currentTimeMillis() + 60000, JSON.parseObject("{\"apis\":{},\"datasources\":{}}"), Map.of(), () -> { }, ignored -> { })) {
            Object invoker = ReflectionTestUtils.getField(fixture.service, "invoker");
            assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(invoker, "invocationApi", 9L));
            assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(invoker, "invocationDatasource", 1L));
        }
    }

    @Test public void invalidInternalIdentityFailsBeforeNetworkWithoutLeavingRunningLease() throws Exception {
        try (Fixture fixture = new Fixture()) {
            String id = (String) fixture.service.create("owner", UUID.randomUUID().toString(), fixture.config(), Map.of()).get("trialId");
            ExternalApiTrialStore.State s = fixture.store.rows.get(id);
            fixture.store.rows.put(id, new ExternalApiTrialStore.State(Long.MAX_VALUE, id, s.owner(), s.requestKey(), s.datasourceId(),
                    s.snapshotDigest(), s.snapshotJson(), "WAITING", s.attemptNo(), null, null, s.expiresAt(), s.pendingJson(), s.resultJson(), null, s.createdAt(), s.updatedAt()));
            assertEquals("FAILED", fixture.service.resume("owner", id).get("status"));
            assertEquals(1, fixture.submissions.get());
        }
    }

    @Test public void cancellationWhilePreparingRequestPreventsSendingSupplierRequest() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Object invoker = ReflectionTestUtils.getField(fixture.service, "invoker");
            ReflectionTestUtils.setField(invoker, "externalApiScriptService", new ExternalApiScriptService() {
                @Override public Object executeRequest(String script, Map<String, Object> context) {
                    fixture.store.cancel(ExternalApiTrialContext.current().trialId, "owner");
                    return context.get("body");
                }
            });
            RuleExternalApiConfig config = fixture.config(); config.setRequestScript("return body;");
            assertEquals("CANCELLED", fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of()).get("status"));
            assertEquals(0, fixture.submissions.get());
            assertTrue(fixture.bills.calls.isEmpty());
        }
    }

    @Test public void billingPersistenceTimeoutNeverRetriesTheAlreadyCompletedSupplierRequest() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.bills.fail = true;
            RuleExternalApiConfig config = fixture.config();
            config.setRequestMethod("GET"); config.setRetryCount(2); config.setRetryOnTimeout(1); config.setRetryIntervalMs(0);
            Map<String, Object> result = fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of());
            assertEquals(1, fixture.submissions.get());
            assertEquals("UNKNOWN", result.get("status"));
            fixture.service.resume("owner", (String) result.get("trialId"));
            assertEquals(1, fixture.submissions.get());
        }
    }

    @Test public void formalApiBillingFailureDoesNotRetrySupplierOrPublishAnUnconfirmedSuccessCache() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.bills.fail = true; fixture.bills.allowFormal = true;
            RuleExternalApiConfig config = fixture.config(); config.setId(7L); config.setStatus(1);
            config.setRequestMethod("GET"); config.setRetryCount(2); config.setRetryOnTimeout(1); config.setRetryIntervalMs(0);
            config.setResponseCacheSeconds(60);
            ExternalApiInvokeService invoker = (ExternalApiInvokeService) ReflectionTestUtils.getField(fixture.service, "invoker");
            ReflectionTestUtils.setField(invoker, "apiConfigMapper", mapper(RuleExternalApiConfigMapper.class, config));
            RuntimeException failure = assertThrows(RuntimeException.class, () -> invoker.invoke(7L, Map.of()));
            assertEquals(1, fixture.submissions.get());
            assertTrue(failure instanceof ExternalApiInvokeService.ApiInvokeException);
            assertTrue(((ExternalApiInvokeService.ApiInvokeException) failure).isUnknown());
            Map<String, Object> observed = ReflectionTestUtils.invokeMethod(failure, "getObservedResponse");
            assertEquals(200, observed.get("httpStatus"));
            assertEquals(88, ExternalApiRequestPlan.read(observed, "body.score"));
            assertEquals("UNKNOWN", ExternalApiRequestPlan.read(observed, "status.billingState"));
            fixture.bills.fail = false;
            assertEquals(true, invoker.invoke(7L, Map.of()).get("success"));
            assertEquals("未确认计费的成功响应不能进入缓存", 2, fixture.submissions.get());
        }
    }

    @Test public void nestedUnknownProviderOutcomeCannotBeDowngradedByWorkflowWrapper() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ReflectionTestUtils.setField(fixture.service, "invoker", new ExternalApiInvokeService() {
                @Override Map<String, Object> invokeTrial(RuleExternalApiConfig config, Map<String, Object> params, Map<String, Object> pending) {
                    var child = new ApiInvokeException("子请求结果未知", null, false, "DISABLED", true, null, "WAIT");
                    throw new ApiInvokeException("链路失败", child, false, "DISABLED", false, null, "WAIT");
                }
                @Override void cleanTrialResources(long row, JSONObject snapshot, Map<String, Object> saved) { }
            });
            Map<String, Object> result = fixture.service.create("owner", UUID.randomUUID().toString(), fixture.config(), Map.of());
            assertEquals("UNKNOWN", result.get("status"));
            assertEquals(false, result.get("canResume"));
        }
    }

    @Test public void internalRuleTrialCancelledDuringPreparationDoesNotExecutePublishedRule() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.source.setProtocol("RULE_ENGINE");
            ExternalApiInvokeService invoker = (ExternalApiInvokeService) ReflectionTestUtils.getField(fixture.service, "invoker");
            var published = new com.hengshucredit.rule.model.entity.RulePublished();
            published.setId(1L); published.setRuleCode("submit");
            ReflectionTestUtils.setField(invoker, "publishedMapper", mapper(com.hengshucredit.rule.server.mapper.RulePublishedMapper.class, published));
            AtomicInteger executions = new AtomicInteger();
            ReflectionTestUtils.setField(invoker, "ruleExecuteService", new RuleExecuteService() {
                @Override public com.hengshucredit.rule.model.dto.RuleResult executePublished(
                        com.hengshucredit.rule.model.entity.RulePublished rule, Map<String, Object> params, Long project, String origin) {
                    executions.incrementAndGet();
                    var result = new com.hengshucredit.rule.model.dto.RuleResult(); result.setSuccess(true); result.setResult(Map.of("score", 88)); return result;
                }
            });
            ReflectionTestUtils.setField(invoker, "externalApiScriptService", new ExternalApiScriptService() {
                @Override public Object executeRequest(String script, Map<String, Object> context) {
                    fixture.store.cancel(ExternalApiTrialContext.current().trialId, "owner"); return context.get("body");
                }
            });
            RuleExternalApiConfig config = fixture.config(); config.setRequestScript("return body;");
            assertEquals("CANCELLED", fixture.service.create("owner", UUID.randomUUID().toString(), config, Map.of()).get("status"));
            assertEquals(0, executions.get());
            assertTrue(fixture.bills.calls.isEmpty());
        }
    }

    private static void expectRejected(Runnable action, String message) {
        try { action.run(); fail("Expected validation failure"); }
        catch (IllegalArgumentException error) { assertTrue(error.getMessage(), error.getMessage().contains(message)); }
    }

    private static final class TestService extends ExternalApiTrialService {
        int slice = 1000;
        @Override int sliceMillis() { return slice; }
    }

    private static final class Billing extends RuleBillingService {
        final List<RuleExternalApiConfig> calls = new ArrayList<>();
        boolean fail;
        boolean allowFormal;
        @Override public boolean recordApiTrialExecution(String trialId, RuleExternalApiConfig api, RuleExternalDatasource source,
                                                         boolean success, Long cost, String error) {
            calls.add(JSON.parseObject(JSON.toJSONString(api), RuleExternalApiConfig.class));
            if (fail) throw new org.springframework.web.client.ResourceAccessException("billing persistence timeout", new java.net.SocketTimeoutException());
            return true;
        }
        @Override public boolean recordApiExecution(RuleExternalApiConfig api, RuleExternalDatasource source, boolean success, Long cost, String error) {
            if (!allowFormal) fail("Draft invocation must use the explicit trial billing path");
            calls.add(JSON.parseObject(JSON.toJSONString(api), RuleExternalApiConfig.class));
            if (fail) throw new org.springframework.web.client.ResourceAccessException("billing persistence timeout", new java.net.SocketTimeoutException());
            return true;
        }
    }

    private static final class Fixture implements AutoCloseable {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final AtomicInteger submissions = new AtomicInteger();
        final AtomicInteger polls = new AtomicInteger();
        final RuleExternalDatasource source = new RuleExternalDatasource();
        final FakeStore store = new FakeStore();
        final TestService service = new TestService();
        final Billing bills = new Billing();
        final ApiHttpClientRegistry pool = new ApiHttpClientRegistry();
        final List<Long> guardIdentities = new ArrayList<>();
        volatile boolean ready;
        Runnable beforeSend;
        boolean dropSubmitResponse;
        int submitStatus = 200;
        Fixture() throws Exception {
            server.createContext("/submit", exchange -> {
                if (beforeSend != null) beforeSend.run();
                submissions.incrementAndGet();
                if (dropSubmitResponse) throw new java.io.IOException("controlled disconnect before response");
                byte[] bytes = "{\"taskId\":\"task1\",\"status\":\"PENDING\",\"score\":88}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(submitStatus, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.createContext("/poll", exchange -> {
                polls.incrementAndGet();
                byte[] bytes = ("{\"taskId\":\"task1\",\"status\":\"" + (ready ? "DONE" : "PENDING") + "\",\"score\":88}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.start();
            source.setId(1L); source.setStatus(1); source.setScope("GLOBAL"); source.setDatasourceCode("TRIAL_LOCAL");
            source.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort()); source.setAuthType("NONE");
            RuleExternalDatasourceMapper sourceMapper = mapper(RuleExternalDatasourceMapper.class, source);
            RuleExternalApiConfigMapper apiMapper = mapper(RuleExternalApiConfigMapper.class, null);
            RuleExternalApiConfigService configs = new RuleExternalApiConfigService();
            ReflectionTestUtils.setField(configs, "datasourceMapper", sourceMapper);
            ReflectionTestUtils.setField(configs, "dataObjectMapper", mapper(RuleDataObjectMapper.class, null));
            ExternalApiInvokeService invoker = new ExternalApiInvokeService();
            ReflectionTestUtils.setField(invoker, "datasourceMapper", sourceMapper);
            ReflectionTestUtils.setField(invoker, "apiConfigMapper", apiMapper);
            ReflectionTestUtils.setField(invoker, "billingService", bills);
            ReflectionTestUtils.setField(invoker, "apiHttpClientRegistry", pool);
            ReflectionTestUtils.setField(invoker, "externalApiGuardRegistry", new ExternalApiGuardRegistry(new ExternalCallProperties()) {
                @Override public Permit acquire(RuleExternalApiConfig config) { guardIdentities.add(config.getId()); return super.acquire(config); }
            });
            ReflectionTestUtils.setField(service, "store", store);
            ReflectionTestUtils.setField(service, "configService", configs);
            ReflectionTestUtils.setField(service, "datasourceMapper", sourceMapper);
            ReflectionTestUtils.setField(service, "apiMapper", apiMapper);
            ReflectionTestUtils.setField(service, "invoker", invoker);
        }
        RuleExternalApiConfig config() {
            RuleExternalApiConfig config = new RuleExternalApiConfig();
            config.setDatasourceId(1L); config.setApiCode("TRIAL"); config.setApiName("本机试运行");
            config.setRequestMode("SYNC"); config.setRequestMethod("POST"); config.setEndpointUrl("/submit");
            config.setContentType("application/json"); config.setTimeoutMs(1000); config.setAuthMode("NONE");
            config.setBillingItemCode("TRIAL_PRICE"); config.setUnitPrice(BigDecimal.ONE);
            return config;
        }
        @Override public void close() { server.stop(0); pool.close(); }
    }

    @SuppressWarnings("unchecked") private static <T> T mapper(Class<T> type, Object row) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if ("selectById".equals(method.getName()) || "selectOne".equals(method.getName())) return row;
            if ("toString".equals(method.getName())) return type.getSimpleName();
            throw new AssertionError("Unexpected mapper write/read: " + method.getName());
        });
    }

    static final class FakeStore extends ExternalApiTrialStore {
        final Map<String, State> rows = new LinkedHashMap<>();
        boolean rejectCheckpoint;
        Runnable beforeClaim;
        java.util.function.Consumer<String> afterCheckpoint;
        java.util.function.UnaryOperator<String> snapshotOnCreate = value -> value;
        @Override public synchronized State create(String owner, String key, Long source, String digest, String snapshot, int ttl) {
            State existing = findByRequestKey(owner, key); if (existing != null) return existing;
            long now = System.currentTimeMillis(); String id = UUID.randomUUID().toString();
            State state = new State(rows.size() + 1L, id, owner, key, source, digest, snapshotOnCreate.apply(snapshot), "CREATED", 0,
                    null, null, now + ttl * 1000L, null, null, null, now, now);
            rows.put(id, state); return state;
        }
        @Override public synchronized State findByRequestKey(String owner, String key) { return rows.values().stream().filter(s -> s.owner().equals(owner) && s.requestKey().equals(key)).findFirst().orElse(null); }
        @Override public synchronized State get(String id, String owner) { State state = rows.get(id); return state != null && state.owner().equals(owner) ? state : null; }
        @Override public synchronized List<State> list(String owner, int limit) { return rows.values().stream().filter(s -> s.owner().equals(owner)).limit(limit).toList(); }
        @Override public synchronized boolean claim(String id, String owner, String token, int seconds) {
            if (beforeClaim != null) { Runnable action = beforeClaim; beforeClaim = null; action.run(); }
            State state = get(id, owner);
            if (state == null || !List.of("CREATED", "WAITING").contains(state.status())) return false;
            rows.put(id, new State(state.id(), id, owner, state.requestKey(), state.datasourceId(), state.snapshotDigest(), state.snapshotJson(), "RUNNING",
                    state.attemptNo() + 1, token, System.currentTimeMillis() + seconds * 1000L, state.expiresAt(), state.pendingJson(), state.resultJson(), state.errorMessage(), state.createdAt(), System.currentTimeMillis()));
            return true;
        }
        @Override public synchronized boolean isRunning(String id, String token) { State state = rows.get(id); return state != null && "RUNNING".equals(state.status()) && token.equals(state.leaseOwner()); }
        @Override public synchronized boolean checkpoint(String id, String token, String pending) {
            if (rejectCheckpoint || !isRunning(id, token)) return false;
            update(id, "RUNNING", pending, rows.get(id).resultJson(), null);
            if (afterCheckpoint != null) afterCheckpoint.accept(pending);
            return true;
        }
        @Override public synchronized boolean finish(String id, String token, String status, String pending, String result, String error) {
            if (!isRunning(id, token)) return false;
            update(id, status, pending, result, error); return true;
        }
        @Override public synchronized State cancel(String id, String owner) {
            State state = get(id, owner); if (state == null) return null;
            if (List.of("CREATED", "RUNNING", "WAITING").contains(state.status())) force(id, "CANCELLED");
            return get(id, owner);
        }
        synchronized void force(String id, String status) { State state = rows.get(id); update(id, status, state.pendingJson(), state.resultJson(), state.errorMessage()); }
        private void update(String id, String status, String pending, String result, String error) {
            State s = rows.get(id);
            rows.put(id, new State(s.id(), id, s.owner(), s.requestKey(), s.datasourceId(), s.snapshotDigest(), s.snapshotJson(), status,
                    s.attemptNo(), s.leaseOwner(), s.leaseUntil(), s.expiresAt(), pending, result, error, s.createdAt(), System.currentTimeMillis()));
        }
    }
}
