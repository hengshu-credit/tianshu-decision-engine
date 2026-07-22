package com.hengshucredit.rule.server.controller.open;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleProjectAuth;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.openapi.OpenApiContract;
import com.hengshucredit.rule.server.openapi.OpenApiContractService;
import com.hengshucredit.rule.server.openapi.OpenRequestMapper;
import com.hengshucredit.rule.server.openapi.OpenResponseRenderer;
import com.hengshucredit.rule.server.service.OpenRuleExecutionExecutor;
import com.hengshucredit.rule.server.service.FieldValidationException;
import com.hengshucredit.rule.server.service.FieldValidationViolation;
import com.hengshucredit.rule.server.service.RuleFieldValidationService;
import com.hengshucredit.rule.server.service.RuleExecuteService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpenRuleControllerTest {

    @Test
    public void acceptsDirectBusinessJsonAndUsesSameEnvelopeForSuccessAndFailure() {
        OpenApiContractService.ResolvedContract resolved = resolvedContract();
        RecordingExecuteService executeService = new RecordingExecuteService();
        OpenRuleController controller = controller(resolved, executeService);
        MockHttpServletRequest request = authenticatedRequest();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Code", "AUTH_1");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customer", Collections.singletonMap("idNo", "A001"));

        ResponseEntity<Object> success = controller.execute("OPEN_RISK", body, headers, request);
        executeService.result.setSuccess(false);
        executeService.result.setErrorMessage("规则执行失败");
        ResponseEntity<Object> failure = controller.execute("OPEN_RISK", body, headers, request);

        Map<?, ?> successBody = (Map<?, ?>) success.getBody();
        Map<?, ?> failureBody = (Map<?, ?>) failure.getBody();
        Assert.assertEquals(200, success.getStatusCodeValue());
        Assert.assertEquals(500, failure.getStatusCodeValue());
        Assert.assertEquals(successBody.keySet(), failureBody.keySet());
        Assert.assertEquals("A001", executeService.params.get("currentIdNo"));
        Assert.assertEquals("PASS", ((Map<?, ?>) successBody.get("payload")).get("decision"));
        Assert.assertEquals("200001", ((Map<?, ?>) failureBody.get("payload")).get("errorCode"));
        Assert.assertEquals(success.getHeaders().getFirst("X-Trace-Id"), successBody.get("requestId"));
        Assert.assertFalse(executeService.collectTrace);
        Assert.assertFalse(executeService.recordTrace);
    }

    @Test
    public void mappingErrorsUseThePublishedEnvelope() {
        OpenApiContractService.ResolvedContract resolved = resolvedContract();
        OpenRuleController controller = controller(resolved, new RecordingExecuteService());
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Code", "AUTH_1");

        ResponseEntity<Object> response = controller.execute("OPEN_RISK", Collections.emptyMap(),
                headers, authenticatedRequest());

        Assert.assertEquals(400, response.getStatusCodeValue());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        Assert.assertEquals("100002", body.get("retCode"));
        Assert.assertEquals("100002", ((Map<?, ?>) body.get("payload")).get("errorCode"));
    }

    @Test
    public void fieldValidationFailureUsesPublishedErrorEnvelopeWithoutExecutingRule() {
        OpenApiContractService.ResolvedContract resolved = resolvedContract();
        resolved.getContract().setErrorDataTemplate(JSON.parse(
                "{\"errorCode\":\"${status.code}\",\"field\":\"${error.field}\","
                        + "\"validationCode\":\"${error.validationCode}\","
                        + "\"validationName\":\"${error.validationName}\"}"));
        RecordingExecuteService executeService = new RecordingExecuteService();
        RuleFieldValidationService validationService = new RuleFieldValidationService() {
            @Override
            public void validateDefinitionInput(Long definitionId, Map<String, Object> params) {
                Assert.assertEquals(Long.valueOf(10L), definitionId);
                throw new FieldValidationException(Collections.singletonList(
                        new FieldValidationViolation("currentIdNo", 9L, "id_no_regex",
                                "证件号格式", "证件号格式错误")));
            }
        };
        OpenRuleController controller = controller(resolved, executeService, validationService);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Code", "AUTH_1");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customer", Collections.singletonMap("idNo", "bad"));

        ResponseEntity<Object> response = controller.execute("OPEN_RISK", body, headers, authenticatedRequest());

        Assert.assertEquals(400, response.getStatusCodeValue());
        Map<?, ?> responseBody = (Map<?, ?>) response.getBody();
        Assert.assertEquals("100001", responseBody.get("retCode"));
        Map<?, ?> payload = (Map<?, ?>) responseBody.get("payload");
        Assert.assertEquals("100001", payload.get("errorCode"));
        Assert.assertEquals("currentIdNo", payload.get("field"));
        Assert.assertEquals("id_no_regex", payload.get("validationCode"));
        Assert.assertEquals("证件号格式", payload.get("validationName"));
        Assert.assertNull(executeService.params);
    }

    private OpenRuleController controller(OpenApiContractService.ResolvedContract resolved,
                                          RuleExecuteService executeService) {
        return controller(resolved, executeService, new RuleFieldValidationService() {
            @Override
            public void validateDefinitionInput(Long definitionId, Map<String, Object> params) {
                // no-op
            }
        });
    }

    private OpenRuleController controller(OpenApiContractService.ResolvedContract resolved,
                                          RuleExecuteService executeService,
                                          RuleFieldValidationService validationService) {
        OpenRuleController controller = new OpenRuleController();
        ReflectionTestUtils.setField(controller, "contractService", new OpenApiContractService() {
            @Override
            public ResolvedContract resolve(Long authenticatedAuthId, String authCode, String ruleCode) {
                Assert.assertEquals(Long.valueOf(5L), authenticatedAuthId);
                return resolved;
            }
        });
        ReflectionTestUtils.setField(controller, "requestMapper", new OpenRequestMapper());
        ReflectionTestUtils.setField(controller, "responseRenderer", new OpenResponseRenderer());
        ReflectionTestUtils.setField(controller, "executeService", executeService);
        ReflectionTestUtils.setField(controller, "executionExecutor", new OpenRuleExecutionExecutor(1, 4));
        ReflectionTestUtils.setField(controller, "fieldValidationService", validationService);
        return controller;
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ProjectAuthContext.direct(7L, "P001", 5L, "AUTH_1", "API_KEY").attach(request);
        return request;
    }

    private OpenApiContractService.ResolvedContract resolvedContract() {
        OpenApiContract contract = new OpenApiContract();
        contract.setEnabled(true);
        OpenApiContract.RequestMapping mapping = new OpenApiContract.RequestMapping();
        mapping.setTargetRefType("VARIABLE");
        mapping.setTargetVarId(101L);
        mapping.setSourceType("BODY");
        mapping.setSourcePath("$.customer.idNo");
        mapping.setRequired(true);
        mapping.setTargetType("STRING");
        contract.setRequestMappings(Collections.singletonList(mapping));
        contract.setEnvelopeTemplate(JSON.parse("{\"retCode\":\"${status.code}\",\"retMessage\":\"${status.message}\",\"requestId\":\"${traceId}\",\"payload\":\"${data}\"}"));
        contract.setDataPath("$.payload");
        contract.setSuccessDataTemplate(JSON.parse("{\"decision\":\"${output.VARIABLE.201}\"}"));
        contract.setErrorDataTemplate(JSON.parse("{\"errorCode\":\"${status.code}\",\"errorMessage\":\"${status.message}\"}"));
        RuleProjectAuth auth = new RuleProjectAuth();
        auth.setId(5L);
        auth.setProjectId(7L);
        auth.setAuthCode("AUTH_1");
        RuleProject project = new RuleProject();
        project.setId(7L);
        project.setProjectCode("P001");
        project.setTraceScopeCode("0007");
        RulePublished published = new RulePublished();
        published.setDefinitionId(10L);
        published.setRuleCode("OPEN_RISK");
        published.setProjectCode("P001");
        published.setVersion(2);
        Map<String, String> inputs = Collections.singletonMap("VARIABLE:101", "currentIdNo");
        Map<String, String> outputs = Collections.singletonMap("VARIABLE:201", "decision");
        return new OpenApiContractService.ResolvedContract(auth, project, published, contract, inputs, outputs);
    }

    @Test
    public void mapsPublishedOutputsToConfiguredExternalResponseFields() {
        OpenApiContractService.ResolvedContract resolved = resolvedContract();
        OpenApiContract.ResponseMapping mapping = new OpenApiContract.ResponseMapping();
        mapping.setSourceRefType("VARIABLE");
        mapping.setSourceVarId(201L);
        mapping.setTargetField("decision_result");
        resolved.getContract().setResponseMappings(Collections.singletonList(mapping));
        resolved.getContract().setSuccessDataTemplate("${response}");
        OpenRuleController controller = controller(resolved, new RecordingExecuteService());
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Code", "AUTH_1");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customer", Collections.singletonMap("idNo", "A001"));

        ResponseEntity<Object> response = controller.execute("OPEN_RISK", body, headers, authenticatedRequest());

        Map<?, ?> responseBody = (Map<?, ?>) response.getBody();
        Assert.assertEquals("PASS", ((Map<?, ?>) responseBody.get("payload")).get("decision_result"));
    }

    private static class RecordingExecuteService extends RuleExecuteService {
        private final RuleResult result = new RuleResult();
        private Map<String, Object> params;
        private boolean collectTrace = true;
        private boolean recordTrace = true;

        private RecordingExecuteService() {
            result.setSuccess(true);
            result.setTraceId("AP-P-TRACE");
            result.setResult(Collections.singletonMap("decision", "PASS"));
        }

        @Override
        public RuleResult executePublished(RulePublished published, Map<String, Object> params,
                                           Long projectId, String clientAppName,
                                           ProjectAuthContext authContext, boolean collectTrace,
                                           boolean recordTrace) {
            this.params = params;
            this.collectTrace = collectTrace;
            this.recordTrace = recordTrace;
            return result;
        }
    }
}
