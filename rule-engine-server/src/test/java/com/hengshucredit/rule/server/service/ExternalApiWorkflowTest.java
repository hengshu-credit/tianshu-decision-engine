package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class ExternalApiWorkflowTest {
    @Test public void wrappedLocalDeadlineBeforeSendPreservesCheckpointAndCallbackUntilResume() throws Exception {
        var now = new java.util.concurrent.atomic.AtomicLong();
        RequestDeadlineContext.setNanoTimeSource(now::get);
        var callbacks = ExternalApiCallbackStoreTest.store();
        var callback = ExternalApiCallbackStoreTest.protocol();
        callback.put("url", "http://localhost/api/external-callback/${invocationId}");
        var spec = JSON.parseObject("{\"steps\":[{\"id\":\"submit\",\"type\":\"HTTP\"},{\"id\":\"poll\",\"type\":\"HTTP\"},{\"id\":\"notification\",\"type\":\"CALLBACK\"}]}");
        spec.getJSONArray("steps").getJSONObject(2).put("callback", callback);
        List<String> calls = new ArrayList<>();
        try {
            Map<String, Object> pending;
            try (var ignored = RequestDeadlineContext.limit(1000)) {
                ExternalApiWorkflow.PendingException stopped = assertThrows(ExternalApiWorkflow.PendingException.class, () ->
                        ExternalApiWorkflow.execute(spec, Map.of(), (step, context) -> {
                            calls.add(step.getString("id"));
                            if ("poll".equals(step.getString("id"))) {
                                now.addAndGet(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(1001));
                                throw new ExternalApiInvokeService.ApiInvokeException("准备查询时切片耗尽", expiredDeadline(), false,
                                        "DISABLED", false, null, "WAIT", Boolean.FALSE);
                            }
                            return Map.of("success", true, "body", Map.of("taskId", "task-1"));
                        }, callbacks, (condition, context) -> true));
                pending = stopped.toMap();
            }
            assertEquals("poll", pending.get("stepId"));
            assertNotNull(ExternalApiRequestPlan.read(pending, "results.submit.body.taskId"));
            String registration = String.valueOf(ExternalApiRequestPlan.read(pending, "registrations.notification"));
            byte[] body = "{\"status\":\"DONE\",\"score\":91}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            callbacks.accept(registration, ExternalApiCallbackStoreTest.signature(body), body);
            try (var ignored = RequestDeadlineContext.limit(1000)) {
                var completed = ExternalApiWorkflow.execute(spec, Map.of(), (step, context) -> {
                    calls.add(step.getString("id"));
                    return Map.of("success", true, "body", Map.of("score", 91));
                }, callbacks, (condition, context) -> true, (step, response) -> { }, pending);
                assertEquals(true, completed.get("success"));
            }
            assertEquals(List.of("submit", "poll", "poll"), calls);
            assertNull(callbacks.result(registration));
        } finally { RequestDeadlineContext.clear(); RequestDeadlineContext.resetNanoTimeSource(); }
    }

    @Test public void workflowNeverTreatsUnknownIssuedOrOrdinaryTimeoutAsUnsentBudgetWait() {
        var now = new java.util.concurrent.atomic.AtomicLong();
        RequestDeadlineContext.setNanoTimeSource(now::get);
        var spec = JSON.parseObject("{\"steps\":[{\"id\":\"submit\",\"type\":\"HTTP\"}]}");
        try {
            for (int scenario = 0; scenario < 8; scenario++) {
                final int selected = scenario;
                now.set(0);
                try (var ignored = RequestDeadlineContext.limit(1000)) {
                    var emitted = new java.util.concurrent.atomic.AtomicReference<ExternalApiInvokeService.ApiInvokeException>();
                    RuntimeException actual = assertThrows(RuntimeException.class, () -> ExternalApiWorkflow.execute(spec, Map.of(), (step, context) -> {
                        if (selected != 4) now.addAndGet(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(1001));
                        Throwable cause = selected == 3 ? new java.util.concurrent.TimeoutException("项目请求总超时")
                                : selected == 4 ? new RequestDeadlineContext.DeadlineTimeoutException() : expiredDeadline();
                        Boolean issued = selected == 0 ? null : selected == 1 ? Boolean.TRUE : Boolean.FALSE;
                        ExternalApiInvokeService.ApiInvokeException failure = selected == 0
                                ? new ExternalApiInvokeService.ApiInvokeException("旧调用未记录发送事实", cause, false, "DISABLED", false, null, "WAIT")
                                : new ExternalApiInvokeService.ApiInvokeException("调用已失败", cause, false, "DISABLED", selected == 2, null, "WAIT", issued);
                        if (selected >= 5) {
                            ExternalApiInvokeService.ApiInvokeException nested = selected == 6
                                    ? new ExternalApiInvokeService.ApiInvokeException("下游旧调用未记录发送事实", cause, false, "DISABLED", false, null, "WAIT")
                                    : new ExternalApiInvokeService.ApiInvokeException("下游调用失败", cause, false, "DISABLED", selected == 7, null, "WAIT", selected == 5);
                            failure = new ExternalApiInvokeService.ApiInvokeException("外层自身未发送不能抹掉下游事实", nested,
                                    false, "DISABLED", false, null, "WAIT", Boolean.FALSE);
                        }
                        emitted.set(failure);
                        throw failure;
                    }, new ExternalApiCallbackStore(null, null), (a, b) -> true));
                    assertSame("只有typed deadline+本地耗尽+确定未发送才可等待，场景" + scenario, emitted.get(), actual);
                }
            }
        } finally { RequestDeadlineContext.clear(); RequestDeadlineContext.resetNanoTimeSource(); }
    }

    private static Throwable expiredDeadline() {
        try { RequestDeadlineContext.check(); throw new AssertionError("测试时钟必须已超过本地deadline"); }
        catch (java.util.concurrent.TimeoutException expected) { return new IllegalStateException("内部包装", expected); }
    }

    @Test public void callbackIsRegisteredBeforeSubmissionAndItsTerminalResultFeedsLaterSteps() throws Exception {
        var store = ExternalApiCallbackStoreTest.store();
        var protocol = ExternalApiCallbackStoreTest.protocol();
        protocol.put("url", "http://localhost/api/external-callback/${invocationId}");
        var specification = JSON.parseObject("{\"steps\":[{\"id\":\"submit\",\"type\":\"HTTP\"},{\"id\":\"notification\",\"type\":\"CALLBACK\"},{\"id\":\"finish\",\"type\":\"HTTP\"}]}");
        specification.getJSONArray("steps").getJSONObject(1).put("callback", protocol);
        List<String> registrations = new ArrayList<>();
        try (var ignored = RequestDeadlineContext.limit(1000)) {
            var result = ExternalApiWorkflow.execute(specification, Map.of(), (step, context) -> {
                if ("submit".equals(step.getString("id"))) {
                    String url = String.valueOf(ExternalApiRequestPlan.read(context, "callbacks.notification.url"));
                    String id = url.substring(url.lastIndexOf('/') + 1); registrations.add(id);
                    byte[] body = "{\"status\":\"DONE\",\"report\":{\"score\":91}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    try { store.accept(id, ExternalApiCallbackStoreTest.signature(body), body); } catch (Exception error) { throw new IllegalStateException(error); }
                } else assertEquals(91, ExternalApiRequestPlan.read(context, "steps.notification.body.report.score"));
                return Map.of("success", true, "body", Map.of());
            }, store, (a, b) -> true);
            assertEquals(3, ((Map<?, ?>) result.get("steps")).size());
            assertNull(store.result(registrations.get(0)));
        }
    }
    @Test public void laterStepsSeeAllEarlierResultsAndPollingOnlyRepeatsItsOwnStep() throws Exception {
        var spec = JSON.parseObject("{\"steps\":[{\"id\":\"submit\",\"type\":\"HTTP\"},{\"id\":\"poll\",\"type\":\"HTTP\",\"poll\":{\"until\":{},\"intervalMs\":1,\"maxAttempts\":3}},{\"id\":\"finish\",\"type\":\"HTTP\"}]}");
        List<String> calls = new ArrayList<>();
        var callbacks = new ExternalApiCallbackStore(null, null);
        try (var ignored = RequestDeadlineContext.limit(1000)) {
            var result = ExternalApiWorkflow.execute(spec, Map.of("phone", "111"), (step, context) -> {
                String id = step.getString("id"); calls.add(id);
                if (!"submit".equals(id)) assertEquals("task1", ExternalApiRequestPlan.read(context, "steps.submit.body.taskId"));
                if ("finish".equals(id)) assertNotNull(ExternalApiRequestPlan.read(context, "steps.poll.body"));
                return Map.of("success", true, "body", Map.of("taskId", "task1"), "done", calls.size() >= 3);
            }, callbacks, (condition, context) -> Boolean.TRUE.equals(context.get("done")));
            assertEquals(List.of("submit", "poll", "poll", "finish"), calls);
            assertEquals(3, ((Map<?, ?>) result.get("steps")).size());
        }
    }
    @Test public void failureDoesNotReplayCompletedSubmission() {
        var spec = JSON.parseObject("{\"steps\":[{\"id\":\"one\",\"type\":\"HTTP\"},{\"id\":\"two\",\"type\":\"HTTP\"},{\"id\":\"three\",\"type\":\"HTTP\"}]}");
        List<String> calls = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> ExternalApiWorkflow.execute(spec, Map.of(), (step, context) -> {
            calls.add(step.getString("id")); if ("two".equals(step.getString("id"))) throw new IllegalStateException("failed"); return Map.of();
        }, new ExternalApiCallbackStore(null, null), (a, b) -> true));
        assertEquals(List.of("one", "two"), calls);
    }
    @Test public void futureStepReferencesAreRejectedAtSaveTime() {
        RuleExternalApiConfig api = new RuleExternalApiConfig(); api.setRequestMode("ASYNC");
        api.setExecutionConfig("{\"version\":2,\"steps\":[{\"id\":\"one\",\"type\":\"HTTP\",\"endpointUrl\":\"/submit\",\"requestFields\":[{\"id\":\"v\",\"location\":\"JSON\",\"path\":\"v\",\"value\":{\"kind\":\"PATH\",\"value\":\"steps.two.body\"}}]},{\"id\":\"two\",\"type\":\"HTTP\",\"endpointUrl\":\"/result\"}]}");
        assertThrows(IllegalArgumentException.class, () -> ExternalApiConfigValidator.validate(api));
    }

    @Test public void timeoutKeepsCompletedStepsAndResumeSkipsSubmission() throws Exception {
        var spec = JSON.parseObject("{\"steps\":[{\"id\":\"submit\",\"type\":\"HTTP\"},{\"id\":\"poll\",\"type\":\"HTTP\",\"poll\":{\"until\":{\"path\":\"done\",\"operator\":\"==\",\"value\":true},\"intervalMs\":1000,\"maxAttempts\":3}}]}");
        List<String> calls = new ArrayList<>();
        Map<String, Object> pending;
        try (var ignored = RequestDeadlineContext.limit(50)) {
            try {
                ExternalApiWorkflow.execute(spec, Map.of(), (step, context) -> {
                    calls.add(step.getString("id"));
                    return Map.of("success", true, "body", Map.of("taskId", "task-1"));
                }, new ExternalApiCallbackStore(null, null), (condition, context) -> {
                    Object value = ExternalApiRequestPlan.read(context, "done");
                    return Boolean.TRUE.equals(value);
                });
                fail("应返回可恢复的工作流等待状态");
                return;
            } catch (ExternalApiWorkflow.PendingException expected) {
                pending = expected.toMap();
            }
        }
        assertEquals("poll", pending.get("stepId"));
        assertEquals(List.of("submit", "poll"), calls);
        try (var ignored = RequestDeadlineContext.limit(1000)) {
            Map<String, Object> result = ExternalApiWorkflow.execute(spec, Map.of(), (step, context) -> {
                calls.add(step.getString("id"));
                if ("poll".equals(step.getString("id"))) context.put("done", true);
                return Map.of("success", true, "body", Map.of("taskId", "task-1"));
            }, new ExternalApiCallbackStore(null, null), (condition, context) ->
                    Boolean.TRUE.equals(ExternalApiRequestPlan.read(context, "done")),
                    (step, response) -> { }, pending);
            assertEquals(true, result.get("success"));
        }
        assertEquals(List.of("submit", "poll", "poll"), calls);
    }
}
