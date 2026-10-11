package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.ApiDocScenarioCopyRequest;
import com.hengshucredit.rule.model.dto.ApiDocScenarioSaveRequest;
import com.hengshucredit.rule.model.dto.ApiDocScenarioSortRequest;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.dto.ApiScenarioExecutionRequest;
import com.hengshucredit.rule.model.dto.ResolvedField;
import com.hengshucredit.rule.model.dto.RuleTestSchema;
import com.hengshucredit.rule.model.dto.RuleTestSchemaRequest;
import com.hengshucredit.rule.model.entity.RuleApiDocScenario;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleApiDocScenarioService;
import com.hengshucredit.rule.server.service.RuleExecuteService;
import com.hengshucredit.rule.server.service.RuleTestExpectationService;
import com.hengshucredit.rule.server.service.RuleTestSchemaService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigInteger;
import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleApiDocScenarioControllerTest {

    @Test public void selectedTargetOutputValidationDoesNotReloadLiveInputsAfterExecution() {
        var definition = new com.hengshucredit.rule.model.entity.RuleDefinition(); definition.setId(9L);
        var input = new com.hengshucredit.rule.model.entity.RuleDefinitionInputField();
        input.setVarId(1L); input.setRefType("VARIABLE"); input.setScriptName("apiInput");
        var output = new com.hengshucredit.rule.model.entity.RuleDefinitionOutputField();
        output.setVarId(77L); output.setRefType("VARIABLE"); output.setScriptName("score"); output.setFieldType("NUMBER");
        var fields = new com.hengshucredit.rule.server.service.RuleFieldAnalyzer.ResolvedFields(List.of(input), List.of(output));
        var target = new com.hengshucredit.rule.server.service.RuleTestTargetResolver.Target(definition, 1L, "{}", "SCRIPT", "88", fields,
                null, null, new com.hengshucredit.rule.model.dto.RuleTestExecutionIdentity());
        boolean[] executed = {false}; int[] lateReads = {0};
        var dependencies = new com.hengshucredit.rule.server.service.FieldDependencyResolver();
        ReflectionTestUtils.setField(dependencies, "variableService", new com.hengshucredit.rule.server.service.RuleVariableService() {
            @Override public com.hengshucredit.rule.model.entity.RuleVariable getById(java.io.Serializable id) {
                if (executed[0]) lateReads[0]++;
                var variable = new com.hengshucredit.rule.model.entity.RuleVariable();
                variable.setId(1L); variable.setVarSource("API"); variable.setStatus(executed[0] ? 0 : 1);
                return variable;
            }
        });
        var schemas = new RuleTestSchemaService();
        ReflectionTestUtils.setField(schemas, "fieldDependencyResolver", dependencies);
        ReflectionTestUtils.setField(controller, "testSchemaService", schemas);
        ReflectionTestUtils.setField(controller, "executeService", new RuleExecuteService() {
            @Override public com.hengshucredit.rule.server.service.RuleTestTargetResolver.Target resolveTestTarget(Long id,
                    com.hengshucredit.rule.model.dto.RuleTestExecutionSource source, String modelJson, String modelType, Long projectId) { return target; }
            @Override public RuleResult testExecuteTarget(com.hengshucredit.rule.server.service.RuleTestTargetResolver.Target selected, Map<String, Object> params) {
                assertSame(target, selected); executed[0] = true;
                RuleResult result = new RuleResult(); result.setSuccess(true); result.setResult(Map.of("score", 88)); return result;
            }
        });
        ApiScenarioExecutionRequest request = new ApiScenarioExecutionRequest();
        request.setExecutionSource(com.hengshucredit.rule.model.dto.RuleTestExecutionSource.from(Map.of("sourceType", "REVISION", "sourceId", "12", "lockVersion", 0)));
        request.setRequestJson("{\"params\":{}}");
        request.setExpectationJson("{\"version\":1,\"mode\":\"FIELDS\",\"checks\":[{\"refType\":\"VARIABLE\",\"refId\":\"77\",\"valueType\":\"NUMBER\",\"operator\":\"==\",\"expected\":{\"kind\":\"LITERAL\",\"valueType\":\"NUMBER\",\"value\":\"88\"}}]}");
        var response = controller.executeValidated(9L, request);
        assertEquals(200, response.getCode());
        assertEquals("同一执行的输出校验不能被执行后停用的输入变量改变", "PASSED", response.getData().validation().getStatus());
        assertEquals(0, lateReads[0]);
    }

    private RuleApiDocScenarioController controller;
    private FakeScenarioService scenarioService;
    private FakeExecuteService executeService;

    @Before
    public void setUp() {
        controller = new RuleApiDocScenarioController();
        scenarioService = new FakeScenarioService();
        executeService = new FakeExecuteService();
        ReflectionTestUtils.setField(controller, "scenarioService", scenarioService);
        ReflectionTestUtils.setField(controller, "executeService", executeService);
        ReflectionTestUtils.setField(controller, "expectationService", new RuleTestExpectationService());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void validatedSnapshotExcludesOnlyRootDebugTracesAndPreservesAllBusinessFacts() {
        RuleResult execution = new RuleResult();
        List<Object> debugTraces = List.of(Map.of("debugOnly", "debug-frame-".repeat(10000)));
        RuleResult nestedBusinessResult = new RuleResult();
        nestedBusinessResult.setTraces(List.of("business-nested-trace"));
        Map<String, Object> business = new LinkedHashMap<>();
        business.put("amount", new BigInteger("9007199254740993"));
        business.put("rate", new BigDecimal("0.123456789012345678901"));
        business.put("traces", List.of("business-trace"));
        business.put("nested", nestedBusinessResult);
        business.put("optional", null);
        execution.setResult(business);
        execution.setTraces(debugTraces);
        execution.setTraceId("root-execution-trace");
        execution.setErrorMessage("controlled business error");
        execution.setExecutionStatus("FAILED_RETRYABLE");
        execution.setAttemptNo(3);
        execution.setResumed(true);
        execution.setCapacityResource("DATABASE");
        execution.setPlatformCode(429);
        execution.setRetryAfterMs(500L);
        execution.setRevisionId(19L);
        execution.setArtifactDigest("artifact-digest");
        executeService.result = execution;
        ApiScenarioExecutionRequest request = new ApiScenarioExecutionRequest();
        request.setRequestJson("{\"params\":{}}");
        request.setExpectationJson("{\"version\":1,\"mode\":\"NONE\"}");

        var response = controller.executeValidated(9L, request);

        assertEquals(200, response.getCode());
        assertSame(execution, response.getData().execution());
        assertSame(debugTraces, execution.getTraces());
        assertSame(business, execution.getResult());
        RuleTestExpectationService json = new RuleTestExpectationService();
        Map<String, Object> snapshot = json.readJsonObject(response.getData().responseJson(), "结果快照");
        Map<String, Object> data = (Map<String, Object>) snapshot.get("data");
        assertFalse("业务结果快照不能重复携带顶层调试追踪", data.containsKey("traces"));
        Map<String, Object> original = json.readJsonObject(json.writeJson(R.ok(execution)), "完整执行结果");
        Map<String, Object> originalData = (Map<String, Object>) original.get("data");
        assertTrue("专用过滤不能影响通用JSON写入及其它接口", originalData.containsKey("traces"));
        originalData.remove("traces");
        assertEquals("其它元字段与业务字段必须完整保留", original, snapshot);
        assertTrue(response.getData().responseJson().contains("9007199254740993"));
        assertTrue(response.getData().responseJson().contains("0.123456789012345678901"));
        assertTrue(response.getData().responseJson().length() < 4096);
        assertSame(debugTraces, response.getData().execution().getTraces());
    }

    @Test
    public void deepDebugTracesDoNotExceedTheBusinessSnapshotDepthBudget() throws Exception {
        Object deep = Map.of("debugOnly", true);
        for (int depth = 0; depth < 160; depth++) deep = Map.of("next", deep);
        List<Object> traces = List.of(deep);
        RuleResult execution = new RuleResult(); execution.setSuccess(true);
        execution.setResult(Map.of("score", 88)); execution.setTraces(traces);
        executeService.result = execution;
        ApiScenarioExecutionRequest request = new ApiScenarioExecutionRequest();
        request.setRequestJson("{\"params\":{}}");
        request.setExpectedResponseJson("{\"data\":{\"success\":true,\"result\":{\"score\":88}}}");

        var response = controller.executeValidated(9L, request);

        assertEquals(200, response.getCode());
        assertEquals("PASSED", response.getData().validation().getStatus());
        var factory = com.fasterxml.jackson.core.JsonFactory.builder().streamReadConstraints(
                com.fasterxml.jackson.core.StreamReadConstraints.builder().maxNestingDepth(128).build()).build();
        try {
            new com.fasterxml.jackson.databind.ObjectMapper(factory).readTree(response.getData().responseJson());
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalidSnapshot) {
            throw new AssertionError("简单业务输出快照不能被深层调试追踪阻断", invalidSnapshot);
        }
        assertSame(traces, response.getData().execution().getTraces());
        assertSame(deep, execution.getTraces().get(0));
    }

    @Test
    public void storedSnapshotsWithTracesStayComparableAndTheOldExecuteEndpointRetainsTraces() {
        RuleResult execution = new RuleResult(); execution.setSuccess(true);
        execution.setResult(Map.of("traces", List.of("business-value")));
        List<Object> traces = List.of(Map.of("debugOnly", true)); execution.setTraces(traces);
        executeService.result = execution;
        RuleTestExpectationService json = new RuleTestExpectationService();
        String historical = json.writeJson(R.ok(execution));
        ApiScenarioExecutionRequest request = new ApiScenarioExecutionRequest();
        request.setRequestJson("{\"params\":{}}"); request.setExpectedResponseJson(historical);

        var response = controller.executeValidated(9L, request);

        assertEquals(200, response.getCode());
        assertEquals("PASSED", response.getData().validation().getStatus());
        assertEquals(historical, request.getExpectedResponseJson());
        assertSame(traces, controller.execute(9L, Map.of()).getData().getTraces());
        assertFalse(response.getData().responseJson().contains("debugOnly"));
        assertTrue(response.getData().responseJson().contains("business-value"));
    }

    @Test
    public void validatedExecutionLoadsStoredJsonWithoutClientRoundingAndReturnsExactSnapshot() {
        scenarioService.saved.setRequestJson("{\"projectId\":3,\"params\":{\"amount\":9007199254740993,\"rate\":0.123456789012345678901,\"$ref\":\"literal\"}}");
        scenarioService.saved.setResponseJson("{\"data\":{\"success\":true,\"result\":{\"amount\":9007199254740993}}}");
        executeService.result = new RuleResult(); executeService.result.setSuccess(true);
        executeService.result.setResult(Map.of("amount", new BigInteger("9007199254740993")));
        ApiScenarioExecutionRequest request = new ApiScenarioExecutionRequest(); request.setScenarioId(77L);

        var response = controller.executeValidated(9L, request);

        assertEquals(200, response.getCode());
        assertEquals(Long.valueOf(77), scenarioService.scenarioId);
        assertEquals(Long.valueOf(9), executeService.definitionId);
        assertEquals(Long.valueOf(3), executeService.projectId);
        assertEquals(new BigInteger("9007199254740993"), executeService.params.get("amount"));
        assertEquals(new BigDecimal("0.123456789012345678901"), executeService.params.get("rate"));
        assertEquals("literal", executeService.params.get("$ref"));
        org.junit.Assert.assertTrue(response.getData().responseJson().contains("9007199254740993"));
        assertEquals("PASSED", response.getData().validation().getStatus());
    }

    @Test
    public void validatedExecutionKeepsExecutionSuccessSeparateFromFieldMismatchAndNoExpectation() {
        executeService.result = new RuleResult(); executeService.result.setSuccess(true);
        executeService.result.setResult(Map.of("amount", new BigInteger("9007199254740993")));
        ReflectionTestUtils.setField(controller, "testSchemaService", new RuleTestSchemaService() {
            @Override public RuleTestSchema build(RuleTestSchemaRequest request) {
                assertEquals(Long.valueOf(9), request.getTargetId());
                ResolvedField field = new ResolvedField(); field.setRefType("VARIABLE"); field.setRefId(19L);
                field.setValueType("NUMBER"); field.setScriptName("amount"); field.setLabel("额度");
                RuleTestSchema schema = new RuleTestSchema(); schema.setOutputs(List.of(field)); return schema;
            }
        });
        ApiScenarioExecutionRequest request = new ApiScenarioExecutionRequest(); request.setRequestJson("{\"params\":{}}");
        request.setExpectationJson("{\"version\":1,\"mode\":\"FIELDS\",\"checks\":[{\"refType\":\"VARIABLE\",\"refId\":\"19\",\"valueType\":\"NUMBER\",\"operator\":\"==\",\"expected\":{\"kind\":\"LITERAL\",\"valueType\":\"NUMBER\",\"value\":\"9007199254740992\"}}]}");
        var mismatch = controller.executeValidated(9L, request);
        org.junit.Assert.assertTrue(mismatch.getData().execution().isSuccess());
        assertEquals("FAILED", mismatch.getData().validation().getStatus());
        request.setExpectationJson("{\"version\":1,\"mode\":\"NONE\"}");
        assertEquals("NOT_CONFIGURED", controller.executeValidated(9L, request).getData().validation().getStatus());
    }

    @Test
    public void listAndCreateUseDefinitionFromPath() {
        RuleApiDocScenario existing = new RuleApiDocScenario();
        existing.setId(11L);
        scenarioService.scenarios = Collections.singletonList(existing);
        ApiDocScenarioSaveRequest request = new ApiDocScenarioSaveRequest();

        R<List<RuleApiDocScenario>> listResponse = controller.list(9L);
        R<RuleApiDocScenario> createResponse = controller.create(9L, request);

        assertSame(scenarioService.scenarios, listResponse.getData());
        assertSame(scenarioService.saved, createResponse.getData());
        assertEquals(Long.valueOf(9L), scenarioService.definitionId);
        assertSame(request, scenarioService.saveRequest);
    }

    @Test
    public void updateCopySortAndDeleteDelegateScopedIdentifiers() {
        ApiDocScenarioSaveRequest saveRequest = new ApiDocScenarioSaveRequest();
        ApiDocScenarioCopyRequest copyRequest = new ApiDocScenarioCopyRequest();
        copyRequest.setScenarioName("复制场景");
        ApiDocScenarioSortRequest sortRequest = new ApiDocScenarioSortRequest();
        sortRequest.setScenarioIds(Arrays.asList(13L, 12L));

        controller.update(9L, 12L, saveRequest);
        controller.copy(9L, 12L, copyRequest);
        controller.sort(9L, sortRequest);
        controller.delete(9L, 12L);

        assertEquals(Long.valueOf(9L), scenarioService.definitionId);
        assertEquals(Long.valueOf(12L), scenarioService.scenarioId);
        assertEquals("复制场景", scenarioService.copyName);
        assertEquals(Arrays.asList(13L, 12L), scenarioService.sortedIds);
        assertEquals(Long.valueOf(12L), scenarioService.deletedScenarioId);
    }

    @Test
    public void executeReturnsFullPlatformEnvelope() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientAppName", "api-doc-example");
        body.put("projectId", 7L);
        body.put("params", Collections.singletonMap("age", 17));
        RuleResult ruleResult = new RuleResult();
        ruleResult.setSuccess(true);
        executeService.result = ruleResult;

        R<RuleResult> response = controller.execute(9L, body);

        assertEquals(200, response.getCode());
        assertEquals("success", response.getMessage());
        assertSame(ruleResult, response.getData());
        assertEquals(Long.valueOf(9L), executeService.definitionId);
        assertEquals(Long.valueOf(7L), executeService.projectId);
        assertEquals(17, executeService.params.get("age"));
        assertEquals(1, executeService.params.size());
    }

    @Test
    public void invalidServiceRequestReturnsBadRequestEnvelope() {
        scenarioService.error = new IllegalArgumentException("请求报文不是合法 JSON");

        R<RuleApiDocScenario> response = controller.create(9L, new ApiDocScenarioSaveRequest());

        assertEquals(400, response.getCode());
        assertEquals("请求报文不是合法 JSON", response.getMessage());
    }

    private static class FakeScenarioService extends RuleApiDocScenarioService {
        private Long definitionId;
        private Long scenarioId;
        private Long deletedScenarioId;
        private String copyName;
        private List<Long> sortedIds;
        private List<RuleApiDocScenario> scenarios = Collections.emptyList();
        private final RuleApiDocScenario saved = new RuleApiDocScenario();
        private ApiDocScenarioSaveRequest saveRequest;
        private IllegalArgumentException error;

        @Override public RuleApiDocScenario getForExecution(Long definitionId, Long scenarioId) {
            failIfNeeded(); this.definitionId = definitionId; this.scenarioId = scenarioId; return saved;
        }

        @Override
        public List<RuleApiDocScenario> listByDefinition(Long definitionId) {
            failIfNeeded();
            this.definitionId = definitionId;
            return scenarios;
        }

        @Override
        public RuleApiDocScenario create(Long definitionId, ApiDocScenarioSaveRequest request) {
            failIfNeeded();
            this.definitionId = definitionId;
            this.saveRequest = request;
            return saved;
        }

        @Override
        public RuleApiDocScenario update(Long definitionId, Long scenarioId,
                                         ApiDocScenarioSaveRequest request) {
            failIfNeeded();
            this.definitionId = definitionId;
            this.scenarioId = scenarioId;
            this.saveRequest = request;
            return saved;
        }

        @Override
        public RuleApiDocScenario copy(Long definitionId, Long scenarioId, String scenarioName) {
            failIfNeeded();
            this.definitionId = definitionId;
            this.scenarioId = scenarioId;
            this.copyName = scenarioName;
            return saved;
        }

        @Override
        public void sort(Long definitionId, List<Long> scenarioIds) {
            failIfNeeded();
            this.definitionId = definitionId;
            this.sortedIds = scenarioIds;
        }

        @Override
        public void delete(Long definitionId, Long scenarioId) {
            failIfNeeded();
            this.definitionId = definitionId;
            this.deletedScenarioId = scenarioId;
        }

        private void failIfNeeded() {
            if (error != null) throw error;
        }
    }

    private static class FakeExecuteService extends RuleExecuteService {
        private Long definitionId;
        private Long projectId;
        private Map<String, Object> params;
        private RuleResult result;

        @Override
        public RuleResult testExecute(Long definitionId, Map<String, Object> params,
                                      Long projectId) {
            this.definitionId = definitionId;
            this.params = params;
            this.projectId = projectId;
            return result;
        }
    }
}
