package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.service.RuleRuntimeCallLogService;
import com.hengshucredit.rule.server.service.RuleExecutionStateService;
import com.hengshucredit.rule.server.service.RuleIdempotencyService;
import com.hengshucredit.rule.server.service.RuleExecuteService;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Proxy;

public class RuleRuntimeControllerTest {

    @Test
    public void externalCallPayloadIsProjectScoped() {
        RecordingRuntimeCallLogService logs = new RecordingRuntimeCallLogService();
        RuleRuntimeController controller = new RuleRuntimeController();
        ReflectionTestUtils.setField(controller, "runtimeCallLogService", logs);

        MockHttpServletRequest request = new MockHttpServletRequest();
        ProjectAuthContext.direct(8L, "project-8", 1L, "auth", "TOKEN").attach(request);

        var response = controller.getExternalCallPayload("call-1", request);

        Assert.assertEquals(8L, logs.projectId.longValue());
        Assert.assertEquals("call-1", logs.callId);
        Assert.assertEquals("call-1", response.getData().get("callId"));
    }

    @Test
    public void externalCallPayloadRejectsMissingProjectToken() {
        RuleRuntimeController controller = new RuleRuntimeController();
        ReflectionTestUtils.setField(controller, "runtimeCallLogService", new RecordingRuntimeCallLogService());

        var response = controller.getExternalCallPayload("call-1", new MockHttpServletRequest());

        Assert.assertEquals(401, response.getCode());
    }

    @Test
    public void executionResultIsProjectScopedAndReadableAfterClientTimeout() {
        RuleExecutionStateService.State state = new RuleExecutionStateService.State(
                1L, 8L, 9L, "credit_rule", "key", "digest", "trace-1", "SUCCEEDED", 2,
                3L, 3L, "artifact", null, "{\"success\":true,\"result\":{\"score\":720}}",
                null, null, null, null);
        RuleRuntimeController controller = new RuleRuntimeController();
        ReflectionTestUtils.setField(controller, "stateService", new RuleExecutionStateService() {
            @Override public State findByRootTraceId(String traceId) { return state; }
        });
        MockHttpServletRequest request = new MockHttpServletRequest();
        ProjectAuthContext.direct(8L, "project-8", 1L, "auth", "TOKEN").attach(request);

        var response = controller.getExecutionResult("trace-1", request);

        Assert.assertEquals(200, response.getCode());
        Map<?, ?> payload = (Map<?, ?>) response.getData();
        Assert.assertEquals(720, ((Map<?, ?>) payload.get("result")).get("score"));
    }

    @Test
    public void executionResultDoesNotCrossProjectBoundary() {
        RuleExecutionStateService.State state = new RuleExecutionStateService.State(
                1L, 9L, 9L, "credit_rule", "key", "digest", "trace-1", "SUCCEEDED", 1,
                3L, 3L, "artifact", null, "{\"success\":true}", null, null, null, null);
        RuleRuntimeController controller = new RuleRuntimeController();
        ReflectionTestUtils.setField(controller, "stateService", new RuleExecutionStateService() {
            @Override public State findByRootTraceId(String traceId) { return state; }
        });
        MockHttpServletRequest request = new MockHttpServletRequest();
        ProjectAuthContext.direct(8L, "project-8", 1L, "auth", "TOKEN").attach(request);

        Assert.assertEquals(404, controller.getExecutionResult("trace-1", request).getCode());
    }

    @Test
    public void statusEndpointReturnsIntermediateWhileWaitingAndResultWhenDone() {
        RuleExecutionStateService.State waiting = new RuleExecutionStateService.State(
                1L, 8L, 9L, "credit_rule", "key", "digest", "trace-wait", "WAITING_EXTERNAL", 2,
                3L, 3L, "artifact",
                "{\"sourceStates\":{\"VARIABLE:7\":{\"OUTCOME\":\"TIMEOUT\"}},\"steps\":{\"VARIABLE:7\":{\"sourceKey\":\"VARIABLE:7\",\"sourceType\":\"VARIABLE\",\"status\":\"WAITING_EXTERNAL\",\"metadata\":{\"taskId\":\"task-7\",\"protocol\":{\"secret\":\"hidden\"}}}}}",
                "{\"success\":false,\"executionStatus\":\"WAITING_EXTERNAL\"}", null, null, null, null);
        RuleRuntimeController controller = new RuleRuntimeController();
        ReflectionTestUtils.setField(controller, "stateService", new RuleExecutionStateService() {
            @Override public State findByRootTraceId(String traceId) { return waiting; }
        });
        MockHttpServletRequest request = new MockHttpServletRequest();
        ProjectAuthContext.direct(8L, "project-8", 1L, "auth", "TOKEN").attach(request);

        var response = controller.getExecutionStatus("trace-wait", request);
        Assert.assertEquals(200, response.getCode());
        Assert.assertEquals("WAITING_EXTERNAL", response.getData().getStatus());
        Assert.assertEquals("task-7", ((Map<?, ?>) ((Map<?, ?>) response.getData().getIntermediate()
                .get("steps")).get("VARIABLE:7")).get("metadata") instanceof Map<?, ?> metadata
                ? metadata.get("taskId") : null);
        Assert.assertNull(response.getData().getResult());
    }

    @Test
    public void statusEndpointIncludesResultAfterCompletion() {
        RuleExecutionStateService.State completed = new RuleExecutionStateService.State(
                2L, 8L, 9L, "credit_rule", "key", "digest", "trace-done", "SUCCEEDED", 3,
                3L, 3L, "artifact", "{}", "{\"success\":true,\"result\":{\"score\":720}}",
                null, null, null, null);
        RuleRuntimeController controller = new RuleRuntimeController();
        ReflectionTestUtils.setField(controller, "stateService", new RuleExecutionStateService() {
            @Override public State findByRootTraceId(String traceId) { return completed; }
        });
        MockHttpServletRequest request = new MockHttpServletRequest();
        ProjectAuthContext.direct(8L, "project-8", 1L, "auth", "TOKEN").attach(request);

        var response = controller.getExecutionStatus("trace-done", request);
        Assert.assertEquals(200, response.getCode());
        Assert.assertEquals("SUCCEEDED", response.getData().getStatus());
        Assert.assertEquals(720, ((Map<?, ?>) response.getData().getResult().getResult()).get("score"));
        Assert.assertNull(response.getData().getIntermediate());
    }

    @Test
    public void retryableFailureRemainsIntermediateAndResultEndpointWaits() {
        RuleExecutionStateService.State retryable = new RuleExecutionStateService.State(
                3L, 8L, 9L, "credit_rule", "key", "digest", "trace-retry", "FAILED_RETRYABLE", 3,
                3L, 3L, "artifact", "{\"steps\":{}}", "{\"success\":false}", null, null, null, null);
        RuleRuntimeController controller = new RuleRuntimeController();
        ReflectionTestUtils.setField(controller, "stateService", new RuleExecutionStateService() {
            @Override public State findByRootTraceId(String traceId) { return retryable; }
        });
        MockHttpServletRequest request = new MockHttpServletRequest();
        ProjectAuthContext.direct(8L, "project-8", 1L, "auth", "TOKEN").attach(request);

        var status = controller.getExecutionStatus("trace-retry", request);
        Assert.assertEquals(200, status.getCode());
        Assert.assertEquals("FAILED_RETRYABLE", status.getData().getStatus());
        Assert.assertNull(status.getData().getResult());
        Assert.assertNotNull(status.getData().getIntermediate());
        Assert.assertEquals(202, controller.getExecutionResult("trace-retry", request).getCode());
    }

    @Test
    public void retryableFailureResumeExecutesAgainInsteadOfReturningStoredFailure() {
        RuleExecutionStateService.State state = new RuleExecutionStateService.State(
                3L, 8L, 9L, "credit_rule", "key", "digest", "trace-retry", "FAILED_RETRYABLE", 3,
                3L, 3L, "artifact", "{}", "{\"success\":false}", null, null, null, null);
        RulePublished published = new RulePublished();
        published.setDefinitionId(9L);
        published.setRevisionId(3L);
        published.setArtifactDigest("artifact");
        AtomicBoolean executed = new AtomicBoolean();
        RuleRuntimeController controller = new RuleRuntimeController();
        ReflectionTestUtils.setField(controller, "stateService", new RuleExecutionStateService() {
            @Override public State findByRootTraceId(String traceId) { return state; }
        });
        ReflectionTestUtils.setField(controller, "publishedMapper", Proxy.newProxyInstance(
                RulePublishedMapper.class.getClassLoader(), new Class<?>[]{RulePublishedMapper.class},
                (proxy, method, args) -> published));
        ReflectionTestUtils.setField(controller, "idempotencyService", new RuleIdempotencyService() {
            @Override public Decision resumeByTrace(RuleExecutionStateService.State ignored, RulePublished ignoredPublished, Map<String, Object> params) {
                return claimedDecision(state);
            }
            @Override public <T> T withDecision(Decision decision, Callable<T> task) {
                try { return task.call(); } catch (Exception error) { throw new IllegalStateException(error); }
            }
            @Override public void complete(Decision decision, RuleResult result, String checkpointJson) { }
        });
        ReflectionTestUtils.setField(controller, "executeService", new RuleExecuteService() {
            @Override public RuleResult executePublished(RulePublished ignored, Map<String, Object> params,
                    Long projectId, String clientAppName, ProjectAuthContext auth,
                    boolean collectTrace, boolean recordTrace) {
                executed.set(true);
                RuleResult result = new RuleResult();
                result.setSuccess(true);
                result.setResult(Map.of("score", 720));
                return result;
            }
        });
        MockHttpServletRequest request = new MockHttpServletRequest();
        ProjectAuthContext.direct(8L, "project-8", 1L, "auth", "TOKEN").attach(request);

        var response = controller.resumeExecution("trace-retry", Map.of("orderNo", "A-1"), request);

        Assert.assertEquals(200, response.getCode());
        Assert.assertTrue(executed.get());
        RuleResult result = (RuleResult) response.getData();
        Assert.assertEquals(720, ((Map<?, ?>) result.getResult()).get("score"));
    }

    private RuleIdempotencyService.Decision claimedDecision(RuleExecutionStateService.State state) {
        try {
            var method = RuleIdempotencyService.Decision.class.getDeclaredMethod("claimed",
                    RuleExecutionStateService.State.class, String.class, int.class, int.class, Long.class, String.class);
            method.setAccessible(true);
            return (RuleIdempotencyService.Decision) method.invoke(null, state, "owner", 3600, 120, 3L, "artifact");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static class RecordingRuntimeCallLogService extends RuleRuntimeCallLogService {
        private String callId;
        private Long projectId;

        @Override
        public Map<String, Object> payloadByCallId(String callId, Long projectId) {
            this.callId = callId;
            this.projectId = projectId;
            return Collections.singletonMap("callId", callId);
        }
    }
}
