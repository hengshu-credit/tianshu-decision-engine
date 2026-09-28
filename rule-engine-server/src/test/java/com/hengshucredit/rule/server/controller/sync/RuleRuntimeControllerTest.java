package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.service.RuleRuntimeCallLogService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;

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
