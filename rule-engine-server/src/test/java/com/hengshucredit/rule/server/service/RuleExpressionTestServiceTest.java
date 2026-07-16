package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ResolutionPlan;
import com.hengshucredit.rule.model.dto.RuleExecuteResult;
import com.hengshucredit.rule.model.dto.RuleExpressionRequest;
import com.hengshucredit.rule.model.dto.RuleTestSchema;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleListLibrary;
import com.hengshucredit.rule.model.entity.RuleVariable;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleExpressionTestServiceTest {

    @Test
    public void currentModeUsesSubmittedValuesWithoutResolvingExternalSources() {
        RecordingVariableResolver sourceResolver = new RecordingVariableResolver();
        RuleExpressionTestService service = service(sourceResolver, new PassThroughFieldAnalyzer());
        RuleExpressionRequest request = request("CURRENT");
        request.setParams(Collections.<String, Object>singletonMap("riskScore", 80));

        RuleExecuteResult result = service.execute(request);

        assertTrue(result.isSuccess());
        assertEquals(85d, ((Number) result.getResult()).doubleValue(), 0d);
        assertEquals(0, sourceResolver.callCount);
    }

    @Test
    public void executeRejectsManagedReferenceWhoseIdIsOutsideRuleProject() {
        RecordingVariableResolver sourceResolver = new RecordingVariableResolver();
        RuleExpressionTestService service = service(sourceResolver, new PassThroughFieldAnalyzer());
        RuleExpressionRequest request = request("CURRENT");
        Map<String, Object> forged = reference();
        forged.put("refId", 99L);
        forged.put("value", "riskScore");
        forged.put("code", "riskScore");
        request.setOperand(operation(forged, literal(5)));
        request.setParams(Collections.<String, Object>singletonMap("riskScore", 80));

        RuleExecuteResult result = service.execute(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("VARIABLE:99"));
        assertEquals(0, sourceResolver.callCount);
    }

    @Test
    public void executeRejectsFunctionWhoseIdIsOutsideRuleProject() {
        RecordingVariableResolver sourceResolver = new RecordingVariableResolver();
        ScopedFunctionService functionService = new ScopedFunctionService();
        RuleExpressionTestService service = service(sourceResolver, new PassThroughFieldAnalyzer(),
                functionService, new RecordingListMatchMatrix());
        RuleExpressionRequest request = request("CURRENT");
        request.setOperand(function(99L, "foreignFunction"));

        RuleExecuteResult result = service.execute(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("ID=99"));
        assertEquals(0, functionService.invokeCount);
    }

    @Test
    public void deepModeForceRefreshesOnlyExpressionReferences() {
        RecordingVariableResolver sourceResolver = new RecordingVariableResolver();
        RuleExpressionTestService service = service(sourceResolver, new PassThroughFieldAnalyzer());
        RuleExpressionRequest request = request("DEEP");

        RuleExecuteResult result = service.execute(request);

        assertTrue(result.isSuccess());
        assertEquals(93d, ((Number) result.getResult()).doubleValue(), 0d);
        assertEquals(1, sourceResolver.callCount);
        assertTrue(sourceResolver.options.isForceRefreshSource());
        assertTrue(sourceResolver.options.isRequiredNamesUpstreamOnly());
        assertEquals(Collections.singleton("riskScore"), sourceResolver.options.getRequiredScriptNames());
    }

    @Test
    public void listQuerySchemaAndExecutionUseSubmittedMatchValue() {
        RecordingVariableResolver sourceResolver = new RecordingVariableResolver();
        RecordingListMatchMatrix listMatchMatrix = new RecordingListMatchMatrix();
        RuleExpressionTestService service = service(sourceResolver, new PassThroughFieldAnalyzer(),
                new ScopedFunctionService(), listMatchMatrix);
        RuleExpressionRequest request = request("CURRENT");
        request.setOperand(listQuery());
        request.setParams(Collections.<String, Object>singletonMap("__expressionListQueryValue", "13800138000"));

        RuleTestSchema schema = service.buildSchema(request);
        RuleExecuteResult result = service.execute(request);

        assertEquals(1, schema.getInputs().size());
        assertEquals("__expressionListQueryValue", schema.getInputs().get(0).getScriptName());
        assertTrue(result.isSuccess());
        assertEquals(Boolean.TRUE, result.getResult());
        assertEquals(Collections.<Object>singletonList("13800138000"), listMatchMatrix.values);
        assertEquals(Collections.singletonList(5L), listMatchMatrix.listIds);
    }

    @Test
    public void schemaSeparatesCurrentInputsFromDeepRuntimeNodes() {
        RecordingVariableResolver sourceResolver = new RecordingVariableResolver();
        RuleExpressionTestService service = service(sourceResolver, new PassThroughFieldAnalyzer());

        RuleTestSchema current = service.buildSchema(request("CURRENT"));
        RuleTestSchema deep = service.buildSchema(request("DEEP"));

        assertEquals(1, current.getInputs().size());
        assertEquals("riskScore", current.getInputs().get(0).getScriptName());
        assertTrue(current.getRuntimeNodes().isEmpty());
        assertEquals(1, deep.getRuntimeNodes().size());
        assertEquals("API", deep.getRuntimeNodes().get(0).getSourceType());
        assertFalse(deep.getInputs().isEmpty());
    }

    private RuleExpressionTestService service(RecordingVariableResolver sourceResolver,
                                              RuleFieldAnalyzer fieldAnalyzer) {
        return service(sourceResolver, fieldAnalyzer, new ScopedFunctionService(), new RecordingListMatchMatrix());
    }

    private RuleExpressionTestService service(RecordingVariableResolver sourceResolver,
                                              RuleFieldAnalyzer fieldAnalyzer,
                                              RuleFunctionService functionService,
                                              ListMatchMatrix listMatchMatrix) {
        RuleExpressionTestService service = new RuleExpressionTestService();
        ReflectionTestUtils.setField(service, "definitionService", new RuleDefinitionService() {
            @Override
            public RuleDefinition getById(java.io.Serializable id) {
                RuleDefinition definition = new RuleDefinition();
                definition.setId(9L);
                definition.setProjectId(3L);
                return definition;
            }
        });
        ReflectionTestUtils.setField(service, "variableService", new RuleVariableService() {
            @Override
            public Map<String, String> buildRefScriptNameMap(Long projectId) {
                return Collections.singletonMap("VARIABLE:7", "riskScore");
            }

            @Override
            public Map<String, Object> buildRefConstantValueMap(Long projectId) {
                return Collections.emptyMap();
            }

            @Override
            public RuleVariable getById(java.io.Serializable id) {
                RuleVariable variable = new RuleVariable();
                variable.setId(7L);
                variable.setVarCode("risk_score_old");
                variable.setScriptName("riskScore");
                variable.setVarLabel("风险分");
                variable.setVarType("NUMBER");
                variable.setVarSource("API");
                return variable;
            }
        });
        ReflectionTestUtils.setField(service, "functionService", functionService);
        ReflectionTestUtils.setField(service, "ruleListService", new RuleListService() {
            @Override
            public RuleListLibrary getById(java.io.Serializable id) {
                if (!Long.valueOf(5L).equals(id)) return null;
                RuleListLibrary library = new RuleListLibrary();
                library.setId(5L);
                library.setProjectId(3L);
                library.setScope(RuleListService.SCOPE_PROJECT);
                library.setStatus(1);
                return library;
            }
        });
        ReflectionTestUtils.setField(service, "listMatchMatrix", listMatchMatrix);
        ReflectionTestUtils.setField(service, "ruleFieldAnalyzer", fieldAnalyzer);
        ReflectionTestUtils.setField(service, "testSchemaService", new RuleTestSchemaService());
        ReflectionTestUtils.setField(service, "variableSourceResolver", sourceResolver);
        return service;
    }

    private RuleExpressionRequest request(String mode) {
        RuleExpressionRequest request = new RuleExpressionRequest();
        request.setRuleId(9L);
        request.setResolutionMode(mode);
        request.setOperand(operation(reference(), literal(5)));
        request.setParams(new LinkedHashMap<String, Object>());
        return request;
    }

    private Map<String, Object> reference() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("kind", "REFERENCE");
        value.put("value", "risk_score_old");
        value.put("code", "risk_score_old");
        value.put("label", "风险分");
        value.put("valueType", "NUMBER");
        value.put("refId", 7L);
        value.put("refType", "VARIABLE");
        return value;
    }

    private Map<String, Object> literal(int value) {
        Map<String, Object> operand = new LinkedHashMap<>();
        operand.put("kind", "LITERAL");
        operand.put("value", value);
        operand.put("valueType", "NUMBER");
        return operand;
    }

    private Map<String, Object> operation(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("operator", "");
        first.put("operand", left);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("operator", "+");
        second.put("operand", right);
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("kind", "OPERATION");
        operation.put("terms", Arrays.asList(first, second));
        return operation;
    }

    private Map<String, Object> function(Long functionId, String functionCode) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("kind", "FUNCTION");
        function.put("functionId", functionId);
        function.put("functionCode", functionCode);
        function.put("args", Collections.emptyList());
        return function;
    }

    private Map<String, Object> listQuery() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("kind", "LIST_QUERY");
        query.put("listIds", Collections.singletonList(5L));
        query.put("itemTypes", Collections.singletonList("MOBILE"));
        query.put("combinationMode", ListMatchMatrix.ANY_FIELD_ANY_LIST);
        query.put("matchMode", "IN_LIST");
        return query;
    }

    private static class RecordingVariableResolver extends VariableSourceResolver {
        private int callCount;
        private VariableResolveOptions options;

        @Override
        public Map<String, Object> resolveInto(Long projectId, Map<String, Object> target,
                                               VariableResolveOptions options) {
            callCount++;
            this.options = options;
            target.put("riskScore", 88);
            return target;
        }
    }

    private static class ScopedFunctionService extends RuleFunctionService {
        private int invokeCount;

        @Override
        public Map<Long, String> buildFunctionCodeMap(Long projectId) {
            return Collections.singletonMap(11L, "trustedFunction");
        }

        @Override
        public Map<Long, Integer> buildFunctionArityMap(Long projectId) {
            return Collections.singletonMap(11L, 0);
        }

        @Override
        public Object invoke(Long functionId, List<Object> args) {
            invokeCount++;
            return 42;
        }
    }

    private static class RecordingListMatchMatrix extends ListMatchMatrix {
        private List<Long> listIds;
        private List<Object> values;

        private RecordingListMatchMatrix() {
            super(null);
        }

        @Override
        public boolean match(List<Long> listIds, List<Object> values, String combinationMode,
                             String matchMode, List<String> itemTypes, LocalDateTime matchTime) {
            this.listIds = listIds;
            this.values = values;
            return true;
        }
    }

    private static class PassThroughFieldAnalyzer extends RuleFieldAnalyzer {
        @Override
        public List<RuleDefinitionInputField> resolveInputFields(List<RuleDefinitionInputField> fields,
                                                                 Long projectId) {
            RuleDefinitionInputField input = new RuleDefinitionInputField();
            input.setVarId(21L);
            input.setRefType("DATA_OBJECT");
            input.setFieldName("customerId");
            input.setFieldLabel("客户编号");
            input.setScriptName("request.customerId");
            input.setFieldType("STRING");
            return Collections.singletonList(input);
        }
    }
}
