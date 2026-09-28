package com.hengshucredit.rule.server.publish;

import com.hengshucredit.rule.model.dto.RulePushMessage;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.service.RuleFunctionService;
import com.hengshucredit.rule.server.service.RuleProjectService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class RuleFunctionPushServiceTest {

    @Test
    public void prepareGlobalPushDoesNotRewriteCallerFunctionScopeOrProjectId() {
        RuleFunctionPushService service = new RuleFunctionPushService();
        ReflectionTestUtils.setField(service, "projectService",
                new RuleProjectService());

        RuleFunction function = new RuleFunction();
        function.setScope(RuleFunctionService.SCOPE_GLOBAL);
        function.setProjectId(97L);
        function.setFuncCode("globalRiskScore");

        RulePushMessage message = service.prepare(function, "FUNC_UPDATE");

        assertEquals(RuleFunctionService.SCOPE_GLOBAL, function.getScope());
        assertEquals(Long.valueOf(97L), function.getProjectId());
        assertEquals(RuleFunctionService.SCOPE_GLOBAL, message.getScope());
        assertEquals(null, message.getProjectCode());
        assertNotNull(message.getOperationId());
    }

    @Test
    public void prepareProjectPushUsesAuthoritativeCodeWithoutRewritingFunction() {
        RuleFunctionPushService service = new RuleFunctionPushService();
        ReflectionTestUtils.setField(service, "projectService",
                projectService(18L, "credit-app"));

        RuleFunction function = new RuleFunction();
        function.setScope(RuleFunctionService.SCOPE_PROJECT);
        function.setProjectId(18L);
        function.setFuncCode("riskScore");

        RulePushMessage message = service.prepare(function, "FUNC_UPDATE");

        assertEquals(RuleFunctionService.SCOPE_PROJECT, function.getScope());
        assertEquals(Long.valueOf(18L), function.getProjectId());
        assertEquals("PROJECT", message.getScope());
        assertEquals("credit-app", message.getProjectCode());
    }

    private static RuleProjectService projectService(Long id, String code) {
        return new RuleProjectService() {
            @Override
            public RuleProject getById(Serializable projectId) {
                if (!id.equals(projectId)) return null;
                RuleProject project = new RuleProject();
                project.setId(id);
                project.setProjectCode(code);
                return project;
            }
        };
    }
}
