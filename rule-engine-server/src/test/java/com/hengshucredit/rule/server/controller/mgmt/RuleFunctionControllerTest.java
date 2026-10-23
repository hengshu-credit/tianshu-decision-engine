package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.model.dto.RulePushMessage;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.publish.RuleFunctionPushService;
import com.hengshucredit.rule.server.publish.RulePushService;
import com.hengshucredit.rule.server.service.RuleFunctionService;
import com.hengshucredit.rule.server.service.RuleProjectService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class RuleFunctionControllerTest {

    @Test public void methodCatalogHttpResponseContainsExactTypesAndIsReadOnly() throws Exception {
        RuleFunctionController controller = new RuleFunctionController();
        ReflectionTestUtils.setField(controller, "methodCatalogService", new com.hengshucredit.rule.server.service.FunctionMethodCatalogService());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        RuleFunction function = new RuleFunction(); function.setFuncCode("maximum"); function.setImplType("JAVA");
        function.setImplClass("java.lang.Math"); function.setImplMethod("max");
        function.setParamsJson("[{\"name\":\"left\",\"type\":\"NUMBER\",\"javaType\":\"int\"},{\"name\":\"right\",\"type\":\"NUMBER\",\"javaType\":\"int\"}]");
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/rule/function/methods")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(com.alibaba.fastjson.JSON.toJSONString(function)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        var data = com.alibaba.fastjson.JSON.parseObject(response).getJSONObject("data");
        org.junit.Assert.assertTrue(data.getBooleanValue("valid"));
        assertEquals("max(int,int)", data.getString("signature"));
        assertEquals("java.lang.Math", data.getString("resolvedClassName"));
        assertEquals("function:view", RuleFunctionController.class.getMethod("inspectMethods", RuleFunction.class)
                .getAnnotation(com.hengshucredit.rule.server.security.RequirePermission.class).value());
    }

    @Test
    public void projectFunctionUpdatePushesAuthoritativeProjectCode() {
        RuleFunctionController controller = new RuleFunctionController();
        RecordingFunctionService functionService = new RecordingFunctionService();
        RecordingPushService pushService = new RecordingPushService();
        ReflectionTestUtils.setField(controller, "functionService", functionService);
        ReflectionTestUtils.setField(controller, "functionPushService",
                functionPushService(pushService, 18L, "credit-app"));

        RuleFunction function = projectFunction(18L, "riskScore");
        ApiResponse<Void> result = controller.update(function);

        assertEquals("000000", result.getCode());
        assertEquals(function, functionService.updated);
        assertEquals("PROJECT", pushService.message.getScope());
        assertEquals("credit-app", pushService.message.getProjectCode());
    }

    @Test
    public void projectFunctionDeletePushesAuthoritativeProjectCode() {
        RuleFunctionController controller = new RuleFunctionController();
        RecordingFunctionService functionService = new RecordingFunctionService();
        RecordingPushService pushService = new RecordingPushService();
        ReflectionTestUtils.setField(controller, "functionService", functionService);
        ReflectionTestUtils.setField(controller, "functionPushService",
                functionPushService(pushService, 18L, "credit-app"));

        RuleFunction function = projectFunction(18L, "riskScore");
        function.setId(91L);
        functionService.existing = function;
        ApiResponse<Void> result = controller.delete(91L);

        assertEquals("000000", result.getCode());
        assertEquals(Long.valueOf(91L), functionService.deletedId);
        assertEquals("PROJECT", pushService.message.getScope());
        assertEquals("credit-app", pushService.message.getProjectCode());
    }

    @Test
    public void globalFunctionUpdateBroadcastsWithoutProjectCode() {
        RuleFunctionController controller = new RuleFunctionController();
        RecordingFunctionService functionService = new RecordingFunctionService();
        RecordingPushService pushService = new RecordingPushService();
        ReflectionTestUtils.setField(controller, "functionService", functionService);
        ReflectionTestUtils.setField(controller, "functionPushService",
                functionPushService(pushService, null, null));

        RuleFunction function = new RuleFunction();
        function.setScope(RuleFunctionService.SCOPE_GLOBAL);
        function.setProjectId(0L);
        function.setFuncCode("globalRiskScore");
        ApiResponse<Void> result = controller.update(function);

        assertEquals("000000", result.getCode());
        assertEquals("GLOBAL", pushService.message.getScope());
        assertNull(pushService.message.getProjectCode());
    }

    @Test
    public void projectFunctionWithoutResolvableProjectIsRejectedBeforeUpdate() {
        RuleFunctionController controller = new RuleFunctionController();
        RecordingFunctionService functionService = new RecordingFunctionService();
        RecordingPushService pushService = new RecordingPushService();
        ReflectionTestUtils.setField(controller, "functionService", functionService);
        ReflectionTestUtils.setField(controller, "functionPushService",
                functionPushService(pushService, null, null));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> controller.update(projectFunction(404L, "riskScore")));

        assertEquals("项目函数所属项目不存在: 404", exception.getMessage());
        assertNull(functionService.updated);
        assertNull(pushService.message);
    }

    @Test
    public void projectFunctionWithoutResolvableProjectIsRejectedBeforeCreate() {
        RuleFunctionController controller = new RuleFunctionController();
        RecordingFunctionService functionService = new RecordingFunctionService();
        RecordingPushService pushService = new RecordingPushService();
        ReflectionTestUtils.setField(controller, "functionService", functionService);
        ReflectionTestUtils.setField(controller, "functionPushService",
                functionPushService(pushService, null, null));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> controller.create(projectFunction(404L, "riskScore")));

        assertEquals("项目函数所属项目不存在: 404", exception.getMessage());
        assertNull(functionService.created);
        assertNull(pushService.message);
    }

    private static RuleFunction projectFunction(Long projectId, String funcCode) {
        RuleFunction function = new RuleFunction();
        function.setScope(RuleFunctionService.SCOPE_PROJECT);
        function.setProjectId(projectId);
        function.setFuncCode(funcCode);
        return function;
    }

    private static RuleProjectService projectService(Long id, String projectCode) {
        return new RuleProjectService() {
            @Override
            public RuleProject getById(Serializable projectId) {
                if (id == null || !id.equals(projectId)) {
                    return null;
                }
                RuleProject project = new RuleProject();
                project.setId(id);
                project.setProjectCode(projectCode);
                return project;
            }
        };
    }

    private static RuleFunctionPushService functionPushService(
            RecordingPushService pushService, Long projectId,
            String projectCode) {
        RuleFunctionPushService functionPushService =
                new RuleFunctionPushService();
        ReflectionTestUtils.setField(functionPushService, "projectService",
                projectService(projectId, projectCode));
        ReflectionTestUtils.setField(functionPushService, "pushService",
                pushService);
        return functionPushService;
    }

    private static class RecordingFunctionService extends RuleFunctionService {
        private RuleFunction existing;
        private RuleFunction created;
        private RuleFunction updated;
        private Long deletedId;

        @Override
        public RuleFunction getById(Long id) {
            return existing;
        }

        @Override
        public void update(RuleFunction function) {
            updated = function;
        }

        @Override
        public void create(RuleFunction function) {
            created = function;
        }

        @Override
        public void delete(Long id) {
            deletedId = id;
        }
    }

    private static class RecordingPushService extends RulePushService {
        private RulePushMessage message;

        @Override
        public void push(RulePushMessage value) {
            message = value;
        }
    }
}
