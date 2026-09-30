package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class ExternalApiWorkflowTest {
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
