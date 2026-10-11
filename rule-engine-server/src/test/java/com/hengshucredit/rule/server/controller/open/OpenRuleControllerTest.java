package com.hengshucredit.rule.server.controller.open;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.core.engine.ExecutionCapacityException;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleProjectAuth;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleFieldValidation;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.service.FieldValidationEvaluator;
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
import com.hengshucredit.rule.server.service.RuleIdempotencyService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class OpenRuleControllerTest {

    @Test
    public void draftFieldRenameCannotRejectValidPublishedInput() {
        var resolved = resolvedContract();
        RuleDefinitionInputField draft = new RuleDefinitionInputField();
        draft.setScriptName("draftIdNo"); draft.setValidationRuleIds("[77,78]");
        RecordingExecuteService execution = new RecordingExecuteService();
        var controller = controller(resolved, execution, realFieldValidation(List.of(draft)));
        var response = controller.execute("OPEN_RISK", Map.of("customer", Map.of("idNo", "A001")),
                new HttpHeaders(), authenticatedRequest());
        Assert.assertEquals(200, response.getStatusCode().value());
        Assert.assertEquals("A001", execution.params.get("currentIdNo"));
    }

    @Test
    public void removingDraftValidationCannotLetInvalidPublishedInputExecute() {
        RecordingExecuteService execution = new RecordingExecuteService();
        var controller = controller(resolvedContract(), execution, realFieldValidation(List.of()));
        var response = controller.execute("OPEN_RISK", Map.of("customer", Map.of("idNo", "bad")),
                new HttpHeaders(), authenticatedRequest());
        Assert.assertEquals(400, response.getStatusCode().value());
        Assert.assertNull(execution.params);
    }

    private RuleFieldValidationService realFieldValidation(List<RuleDefinitionInputField> draftFields) {
        RuleFieldValidation required = new RuleFieldValidation();
        required.setId(77L); required.setValidationType("REQUIRED"); required.setStatus(1);
        required.setValidationCode("required"); required.setErrorMessage("必填");
        RuleFieldValidation pattern = new RuleFieldValidation();
        pattern.setId(78L); pattern.setValidationType("REGEX"); pattern.setStatus(1);
        pattern.setValidationValue("^[A-Z]\\d{3}$"); pattern.setValidationCode("format");
        pattern.setErrorMessage("格式不符");
        RuleFieldValidationService service = new RuleFieldValidationService() {
            @Override public List<RuleFieldValidation> listByIds(java.util.Collection<? extends java.io.Serializable> ids) {
                Assert.assertEquals(java.util.Set.of(77L, 78L), new java.util.HashSet<>(ids));
                return List.of(required, pattern);
            }
        };
        RuleDefinitionInputFieldMapper mapper = (RuleDefinitionInputFieldMapper) java.lang.reflect.Proxy.newProxyInstance(
                RuleDefinitionInputFieldMapper.class.getClassLoader(), new Class<?>[]{RuleDefinitionInputFieldMapper.class},
                (target, method, args) -> {
                    if ("selectList".equals(method.getName())) return draftFields;
                    throw new AssertionError("unexpected input field read: " + method.getName());
                });
        ReflectionTestUtils.setField(service, "inputFieldMapper", mapper);
        ReflectionTestUtils.setField(service, "evaluator", new FieldValidationEvaluator());
        return service;
    }

    @Test
    public void modelCapacityWithoutIdempotencyReturnsItsOwnLimitHeader() {
        assertModelCapacityResponse("NONE");
    }

    @Test
    public void claimedIdempotencyKeepsModelCapacityResponseMetadata() {
        assertModelCapacityResponse("CLAIMED");
    }

    @Test
    public void storedIdempotentResultKeepsModelCapacityMetadata() {
        assertModelCapacityResponse("COMPLETED");
    }

    private void assertModelCapacityResponse(String mode) {
        for (ExecutionCapacityException.Resource resource : ExecutionCapacityException.Resource.values()) {
            RecordingExecuteService executeService = new RecordingExecuteService();
            executeService.result.setSuccess(false);
            executeService.result.setExecutionStatus("THROTTLED");
            executeService.result.setCapacityResource(resource.name());
            executeService.result.setRetryAfterMs(1000L);
            executeService.result.setErrorMessage(resource.name() + " capacity unavailable");
            OpenRuleController controller = controller(resolvedContract(), executeService);
            AtomicReference<RuleResult> completed = new AtomicReference<>();
            if (!"NONE".equals(mode)) {
                RuleResult stored = JSON.parseObject(JSON.toJSONString(executeService.result), RuleResult.class);
                RuleIdempotencyService.Decision decision = "COMPLETED".equals(mode)
                        ? ReflectionTestUtils.invokeMethod(RuleIdempotencyService.Decision.class, "completed", null, stored)
                        : ReflectionTestUtils.invokeMethod(RuleIdempotencyService.Decision.class, "claimed", null, "owner", 60, 10, null, null);
                ReflectionTestUtils.setField(controller, "idempotencyService", new RuleIdempotencyService() {
                    @Override public Decision begin(RulePublished published, Long projectId, Map<String, Object> params) {
                        return decision;
                    }
                    @Override public void complete(Decision current, RuleResult result, String checkpoint) {
                        completed.set(result);
                    }
                });
            }
            try {
                ResponseEntity<Object> response = controller.execute("OPEN_RISK",
                        Collections.singletonMap("customer", Collections.singletonMap("idNo", "A001")),
                        new HttpHeaders(), authenticatedRequest());
                Assert.assertEquals(429, response.getStatusCode().value());
                Assert.assertEquals(resource.getLimitName(), response.getHeaders().getFirst("X-Rule-Limit-Name"));
                Assert.assertNull("模型缓存拒绝不能伪报开放规则线程池队列深度", response.getHeaders().getFirst("X-Rule-Queue-Depth"));
                Assert.assertEquals("1", response.getHeaders().getFirst("Retry-After"));
                Map<?, ?> body = (Map<?, ?>) response.getBody();
                Assert.assertEquals("400001", body.get("retCode"));
                Assert.assertEquals(executeService.result.getErrorMessage(), ((Map<?, ?>) body.get("payload")).get("errorMessage"));
                if ("CLAIMED".equals(mode)) Assert.assertSame(executeService.result, completed.get());
                if ("COMPLETED".equals(mode)) Assert.assertNull(executeService.params);
            } finally {
                ((OpenRuleExecutionExecutor) ReflectionTestUtils.getField(controller, "executionExecutor")).close();
            }
        }
    }

    @Test
    public void untypedModelErrorStillReturns500WithoutLimitHeaders() {
        RecordingExecuteService executeService = new RecordingExecuteService();
        executeService.result.setSuccess(false);
        executeService.result.setErrorMessage("PMML 模型加载并发已达上限");
        OpenRuleController controller = controller(resolvedContract(), executeService);
        try {
            ResponseEntity<Object> response = controller.execute("OPEN_RISK",
                    Collections.singletonMap("customer", Collections.singletonMap("idNo", "A001")),
                    new HttpHeaders(), authenticatedRequest());
            Assert.assertEquals(500, response.getStatusCode().value());
            Assert.assertEquals("200001", ((Map<?, ?>) response.getBody()).get("retCode"));
            Assert.assertNull(response.getHeaders().getFirst("X-Rule-Limit-Name"));
            Assert.assertNull(response.getHeaders().getFirst("Retry-After"));
        } finally {
            ((OpenRuleExecutionExecutor) ReflectionTestUtils.getField(controller, "executionExecutor")).close();
        }
    }

    @Test
    public void typedCapacityEscapingExecutionServiceStillReturns429() {
        for (int wrapper = 0; wrapper < 5; wrapper++) {
            ExecutionCapacityException capacity = new ExecutionCapacityException(
                    ExecutionCapacityException.Resource.ONNX_SESSION_CACHE, "capacity unavailable");
            RuntimeException rejection = wrapper == 0 ? capacity : wrapper == 1
                    ? new IllegalArgumentException("model binding", capacity) : wrapper == 2
                    ? new IllegalStateException("execution wrapper", capacity) : wrapper == 3
                    ? new java.util.concurrent.RejectedExecutionException("capacity unavailable")
                    : new IllegalArgumentException("invalid parameter");
            RuleExecuteService rejected = new RuleExecuteService() {
                @Override public RuleResult executePublished(RulePublished published, Map<String, Object> params,
                        Long projectId, String app, ProjectAuthContext auth, boolean collectTrace, boolean recordTrace) {
                    throw rejection;
                }
            };
            OpenRuleController controller = controller(resolvedContract(), rejected);
            try {
                ResponseEntity<Object> response = controller.execute("OPEN_RISK",
                        Collections.singletonMap("customer", Collections.singletonMap("idNo", "A001")),
                        new HttpHeaders(), authenticatedRequest());
                if (wrapper < 3) {
                    Assert.assertEquals(429, response.getStatusCode().value());
                    Assert.assertEquals("onnx-session-cache", response.getHeaders().getFirst("X-Rule-Limit-Name"));
                    Assert.assertEquals("400001", ((Map<?, ?>) response.getBody()).get("retCode"));
                } else {
                    Assert.assertEquals(wrapper == 3 ? 500 : 400, response.getStatusCode().value());
                    Assert.assertNull(response.getHeaders().getFirst("X-Rule-Limit-Name"));
                    Assert.assertEquals(wrapper == 3 ? "900001" : "100001", ((Map<?, ?>) response.getBody()).get("retCode"));
                }
            } finally {
                ((OpenRuleExecutionExecutor) ReflectionTestUtils.getField(controller, "executionExecutor")).close();
            }
        }
    }

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
        Assert.assertEquals(200, success.getStatusCode().value());
        Assert.assertEquals(500, failure.getStatusCode().value());
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

        Assert.assertEquals(400, response.getStatusCode().value());
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
            public void validateInputFields(List<RuleDefinitionInputField> fields, Map<String, Object> params) {
                Assert.assertEquals(Long.valueOf(101L), fields.get(0).getVarId());
                Assert.assertEquals("currentIdNo", fields.get(0).getScriptName());
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

        Assert.assertEquals(400, response.getStatusCode().value());
        Map<?, ?> responseBody = (Map<?, ?>) response.getBody();
        Assert.assertEquals("100001", responseBody.get("retCode"));
        Map<?, ?> payload = (Map<?, ?>) responseBody.get("payload");
        Assert.assertEquals("100001", payload.get("errorCode"));
        Assert.assertEquals("currentIdNo", payload.get("field"));
        Assert.assertEquals("id_no_regex", payload.get("validationCode"));
        Assert.assertEquals("证件号格式", payload.get("validationName"));
        Assert.assertNull(executeService.params);
    }

    @Test
    public void throttledExecutionReturnsCapacityHeaders() {
        OpenApiContractService.ResolvedContract resolved = resolvedContract();
        RecordingExecuteService executeService = new RecordingExecuteService();
        executeService.result.setSuccess(false);
        executeService.result.setExecutionStatus("THROTTLED");
        executeService.result.setErrorMessage("外数API达到最大并发数");
        OpenRuleController controller = controller(resolved, executeService);

        ResponseEntity<Object> response = controller.execute("OPEN_RISK",
                Collections.singletonMap("customer", Collections.singletonMap("idNo", "A001")),
                new HttpHeaders(), authenticatedRequest());

        Assert.assertEquals(429, response.getStatusCode().value());
        Assert.assertEquals("1", response.getHeaders().getFirst("Retry-After"));
        Assert.assertEquals("open-execution-queue", response.getHeaders().getFirst("X-Rule-Limit-Name"));
        Assert.assertNotNull(response.getHeaders().getFirst("X-Rule-Queue-Depth"));
    }

    private OpenRuleController controller(OpenApiContractService.ResolvedContract resolved,
                                          RuleExecuteService executeService) {
        return controller(resolved, executeService, new RuleFieldValidationService() {
            @Override
            public void validateInputFields(List<RuleDefinitionInputField> fields, Map<String, Object> params) {
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
        RuleDefinitionInputField input = new RuleDefinitionInputField();
        input.setVarId(101L); input.setRefType("VARIABLE"); input.setScriptName("currentIdNo");
        input.setValidationRuleIds("[77,78]"); input.setStatus(1);
        return new OpenApiContractService.ResolvedContract(auth, project, published, contract, List.of(input), inputs, outputs);
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

    @Test
    public void exposesPublishedRevisionAndArtifactToResponseTemplates() {
        OpenApiContractService.ResolvedContract resolved = resolvedContract();
        resolved.getContract().setSuccessDataTemplate(JSON.parse(
                "{\"revision\":\"${revisionId}\",\"artifact\":\"${artifactDigest}\"}"));
        resolved.getContract().setEnvelopeTemplate(JSON.parse(
                "{\"code\":\"${status.code}\",\"data\":\"${data}\"}"));
        resolved.getContract().setDataPath("$.data");
        RecordingExecuteService executeService = new RecordingExecuteService();
        executeService.result.setRevisionId(42L);
        executeService.result.setArtifactDigest("artifact-42");
        OpenRuleController controller = controller(resolved, executeService);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Code", "AUTH_1");

        ResponseEntity<Object> response = controller.execute("OPEN_RISK",
                Collections.singletonMap("customer", Collections.singletonMap("idNo", "A001")),
                headers, authenticatedRequest());

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        Assert.assertEquals(Long.valueOf(42L), data.get("revision"));
        Assert.assertEquals("artifact-42", data.get("artifact"));
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
