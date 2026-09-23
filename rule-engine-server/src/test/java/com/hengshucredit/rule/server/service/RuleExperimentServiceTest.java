package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteRequest;
import com.hengshucredit.rule.model.entity.RuleExperiment;
import com.hengshucredit.rule.model.entity.RuleExperimentGroup;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuleExperimentServiceTest {

    private RuleExperimentService service;

    @Before
    public void setUp() throws Exception {
        service = new RuleExperimentService();
        Field engineField = RuleExperimentService.class.getDeclaredField("qlExpressEngine");
        engineField.setAccessible(true);
        engineField.set(service, new QLExpressEngine());
    }

    @Test
    public void conditionProductionUsesFirstMatchAndFallback() throws Exception {
        RuleExperiment experiment = experiment("CONDITION", "CONDITION");
        List<RuleExperimentGroup> groups = new ArrayList<>();
        groups.add(group("champion", "CHAMPION", 0, 0, "amount >= 100", null));
        groups.add(group("challenger", "CHALLENGER", 0, 1, "amount >= 200", null));
        groups.add(group("fallback", "CHALLENGER", 0, 2, "", "{\"fallback\":true}"));

        Map<String, Object> params = new HashMap<>();
        params.put("amount", 250);
        Object matched = chooseProductionGroup(experiment, groups, params);
        assertEquals("champion", routeGroup(matched).getGroupCode());

        params.put("amount", 50);
        Object fallback = chooseProductionGroup(experiment, groups, params);
        assertEquals("fallback", routeGroup(fallback).getGroupCode());
    }

    @Test
    public void ratioRoutingTraceContainsBucketAndSelectedGroup() throws Exception {
        RuleExperiment experiment = experiment("RATIO", "CONDITION");
        List<RuleExperimentGroup> groups = Collections.singletonList(
                group("champion", "CHAMPION", 100, 0, "", null));

        Object choice = chooseProductionGroup(experiment, groups, Collections.<String, Object>emptyMap());
        Field traceField = choice.getClass().getDeclaredField("routeTrace");
        traceField.setAccessible(true);
        List<?> routeTrace = (List<?>) traceField.get(choice);

        assertTrue(routeTrace.toString().contains("ROUTING_START"));
        assertTrue(routeTrace.toString().contains("RANDOM_VALUE"));
        assertTrue(routeTrace.toString().contains("GROUP_SELECTED"));
    }

    @Test
    public void conditionTestGroupsHonorExclusiveFlag() throws Exception {
        RuleExperiment experiment = experiment("RATIO", "CONDITION");
        experiment.setTestExclusive(0);
        List<RuleExperimentGroup> groups = new ArrayList<>();
        groups.add(group("champion", "CHAMPION", 100, 0, "", null));
        groups.add(group("test_a", "TEST", 0, 1, "score >= 60", null));
        groups.add(group("test_b", "TEST", 0, 2, "score >= 80", null));
        groups.add(group("test_fallback", "TEST", 0, 3, "", "{\"fallback\":true}"));

        Map<String, Object> params = new HashMap<>();
        params.put("score", 90);
        List<?> nonExclusive = chooseTestGroups(experiment, groups, params);
        assertEquals(2, nonExclusive.size());
        assertEquals("test_a", routeGroup(nonExclusive.get(0)).getGroupCode());
        assertEquals("test_b", routeGroup(nonExclusive.get(1)).getGroupCode());

        experiment.setTestExclusive(1);
        List<?> exclusive = chooseTestGroups(experiment, groups, params);
        assertEquals(1, exclusive.size());
        assertEquals("test_a", routeGroup(exclusive.get(0)).getGroupCode());
    }

    @Test
    public void conditionTestGroupsUseFallbackWhenNoConditionMatches() throws Exception {
        RuleExperiment experiment = experiment("RATIO", "CONDITION");
        List<RuleExperimentGroup> groups = new ArrayList<>();
        groups.add(group("champion", "CHAMPION", 100, 0, "", null));
        groups.add(group("test_a", "TEST", 0, 1, "score >= 60", null));
        groups.add(group("test_fallback", "TEST", 0, 2, "", "{\"fallback\":true}"));

        Map<String, Object> params = new HashMap<>();
        params.put("score", 30);
        List<?> choices = chooseTestGroups(experiment, groups, params);

        assertEquals(1, choices.size());
        assertEquals("test_fallback", routeGroup(choices.get(0)).getGroupCode());
    }

    @Test
    public void validateRuntimeGroupsRejectsInvalidTestRatioTotal() throws Exception {
        RuleExperiment experiment = experiment("RATIO", "RATIO");
        List<RuleExperimentGroup> groups = new ArrayList<>();
        groups.add(group("champion", "CHAMPION", 100, 0, "", null));
        groups.add(group("test_a", "TEST", 60, 1, "", null));
        groups.add(group("test_b", "TEST", 30, 2, "", null));

        try {
            validateRuntimeGroups(experiment, groups);
        } catch (InvocationTargetException e) {
            assertEquals("测试组分流比例之和必须为100%", e.getTargetException().getMessage());
            return;
        }
        throw new AssertionError("expected invalid test ratio to be rejected");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void experimentParametersUseTheSharedTypeBinderBeforeRouting() throws Exception {
        RuleExperimentService typedService = new RuleExperimentService() {
            @Override
            public RuleFieldAnalyzer.ResolvedFields resolveTestFields(Long experimentId) {
                RuleDefinitionInputField age = new RuleDefinitionInputField();
                age.setScriptName("age");
                age.setFieldType("INTEGER");
                return new RuleFieldAnalyzer.ResolvedFields(
                        Collections.singletonList(age), Collections.emptyList());
            }
        };
        setField(typedService, "executionParameterBinder", new ExecutionParameterBinder());
        RuleExperiment experiment = experiment("CONDITION", "CONDITION");
        experiment.setId(3L);
        Map<String, Object> params = new HashMap<>();
        params.put("age", "22");

        Method method = RuleExperimentService.class.getDeclaredMethod(
                "bindExperimentParams", RuleExperiment.class, Map.class);
        method.setAccessible(true);
        Map<String, Object> bound = (Map<String, Object>) method.invoke(typedService, experiment, params);

        assertEquals(Integer.valueOf(22), bound.get("age"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void routingResolvesOnlyConditionAndRequestKeyDependencies() throws Exception {
        RuleExperiment experiment = experiment("CONDITION", "RATIO");
        experiment.setId(9L);
        experiment.setProjectId(3L);
        experiment.setRequestKeyPath("{\"kind\":\"REFERENCE\",\"refId\":37,\"refType\":\"VARIABLE\",\"code\":\"age\",\"value\":\"age\"}");
        List<RuleExperimentGroup> groups = new ArrayList<>();
        groups.add(group("champion", "CHAMPION", 100, 0, "age >= 22", null));
        groups.add(group("challenger", "CHALLENGER", 0, 1, "", null));

        RuleDefinitionInputField age = new RuleDefinitionInputField();
        age.setVarId(37L);
        age.setRefType("VARIABLE");
        age.setScriptName("age");
        age.setFieldType("NUMBER");
        RuleDefinitionInputField unrelatedApi = new RuleDefinitionInputField();
        unrelatedApi.setVarId(228L);
        unrelatedApi.setRefType("VARIABLE");
        unrelatedApi.setScriptName("current_overdue_days");
        unrelatedApi.setFieldType("NUMBER");

        setField(service, "ruleFieldAnalyzer", new RuleFieldAnalyzer() {
            @Override
            public ResolvedFields resolveFields(Long definitionId, String modelJson, String modelType, Long projectId) {
                return new ResolvedFields(Collections.singletonList(age), Collections.emptyList());
            }
        });
        setField(service, "variableService", new RuleVariableService() {
            @Override
            public Map<String, String> buildRefScriptNameMap(Long projectId) {
                return Map.of("VARIABLE:37", "age", "VARIABLE:228", "current_overdue_days");
            }
        });
        final Set<String>[] captured = new Set[]{null};
        setField(service, "variableSourceResolver", new VariableSourceResolver() {
            @Override
            public Map<String, Object> resolve(Long projectId, Map<String, Object> params,
                                                VariableResolveOptions options) {
                captured[0] = new LinkedHashSet<>(options.getRequiredScriptNames());
                params.put("age", 36);
                return params;
            }
        });

        Method method = RuleExperimentService.class.getDeclaredMethod(
                "resolveRoutingParams", RuleExperiment.class, List.class, Map.class);
        method.setAccessible(true);
        Map<String, Object> params = new HashMap<>();
        params.put("age", 99);
        Map<String, Object> resolved = (Map<String, Object>) method.invoke(service, experiment, groups, params);

        assertEquals(Set.of("age"), captured[0]);
        assertEquals(Integer.valueOf(36), resolved.get("age"));
    }

    @Test
    public void routingConditionRetainsDerivedNodeAfterPublicInputProjection() throws Exception {
        RuleExperiment experiment = experiment("CONDITION", "RATIO");
        experiment.setProjectId(3L); experiment.setRequestKeyPath(null);
        var champion = group("champion", "CHAMPION", 100, 0, "", null);
        champion.setConditionConfig("{\"type\":\"group\",\"op\":\"AND\",\"children\":[{\"type\":\"leaf\",\"operator\":\">=\",\"leftOperand\":{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":37,\"code\":\"staleAge\"},\"rightOperand\":{\"kind\":\"LITERAL\",\"value\":22,\"valueType\":\"NUMBER\"}}]}");
        RuleDefinitionInputField raw = new RuleDefinitionInputField();
        raw.setVarId(6L); raw.setRefType("VARIABLE"); raw.setScriptName("idcard_no");
        setField(service, "ruleFieldAnalyzer", new RuleFieldAnalyzer() {
            @Override public ResolvedFields resolveFields(Long id, String json, String type, Long project) {
                return new ResolvedFields(List.of(raw), List.of());
            }
        });
        setField(service, "variableService", new RuleVariableService() {
            @Override public Map<String, String> buildRefScriptNameMap(Long project) {
                return Map.of("VARIABLE:6", "idcard_no", "VARIABLE:37", "age");
            }
        });
        setField(service, "variableSourceResolver", new VariableSourceResolver() {
            @Override public Map<String, Object> resolve(Long project, Map<String, Object> params, VariableResolveOptions options) {
                assertTrue("只传入身份证不能代替计算年龄", options.getRequiredScriptNames().contains("age"));
                assertTrue(options.getRequiredScriptNames().contains("idcard_no"));
                return params;
            }
        });
        Method method = RuleExperimentService.class.getDeclaredMethod("resolveRoutingParams", RuleExperiment.class, List.class, Map.class);
        method.setAccessible(true);
        method.invoke(service, experiment, List.of(champion), new HashMap<>());
    }

    @Test
    public void resolveTestFieldsMergesRuleConditionsAndRequestKey() throws Exception {
        RuleExperiment experiment = experiment("CONDITION", "CONDITION");
        experiment.setId(3L);
        experiment.setProjectId(1L);
        experiment.setRequestKeyPath("{\"kind\":\"REFERENCE\",\"refId\":12,\"refType\":\"VARIABLE\",\"code\":\"request.id\",\"value\":\"request.id\",\"valueType\":\"STRING\"}");
        experiment.setGroups(Collections.singletonList(
                group("champion", "CHAMPION", 100, 0, "customer.level == \"A\"", null)));
        RuleExperimentService resolverService = new RuleExperimentService() {
            @Override
            public RuleExperiment getDetail(Long id) {
                return experiment;
            }

            @Override
            public List<Long> listReferencedDefinitionIds(Long experimentId) {
                return Collections.singletonList(7L);
            }
        };
        setField(resolverService, "definitionService", new RuleDefinitionService() {
            @Override
            public RuleDefinition getById(java.io.Serializable id) {
                RuleDefinition definition = new RuleDefinition();
                definition.setId(7L);
                definition.setProjectId(1L);
                definition.setModelType("FLOW");
                return definition;
            }

            @Override
            public RuleDefinitionContent getContent(Long definitionId) {
                RuleDefinitionContent content = new RuleDefinitionContent();
                content.setDefinitionId(definitionId);
                content.setModelJson("{\"nodes\":[]}");
                return content;
            }

            @Override
            public List<RuleDefinitionInputField> listInputFields(Long definitionId) {
                RuleDefinitionInputField stale = new RuleDefinitionInputField();
                stale.setScriptName("stale_intermediate");
                return Collections.singletonList(stale);
            }
        });
        setField(resolverService, "ruleFieldAnalyzer", new RuleFieldAnalyzer() {
            @Override
            public ResolvedFields resolveFields(Long definitionId, String modelJson, String modelType, Long projectId) {
                RuleDefinitionInputField input = new RuleDefinitionInputField();
                RuleDefinitionOutputField output = new RuleDefinitionOutputField();
                if (definitionId != null) {
                    input.setScriptName("score_f1_fields.HYBASE_X115");
                    input.setFieldType("DOUBLE");
                    output.setScriptName("decision");
                    return new ResolvedFields(Collections.singletonList(input), Collections.singletonList(output));
                }
                input.setScriptName("customer.level");
                input.setFieldType("STRING");
                return new ResolvedFields(Collections.singletonList(input), Collections.emptyList());
            }
        });

        RuleFieldAnalyzer.ResolvedFields fields = resolverService.resolveTestFields(3L);

        List<String> paths = new ArrayList<>();
        for (RuleDefinitionInputField field : fields.getInputFields()) paths.add(field.getScriptName());
        assertEquals(3, paths.size());
        assertEquals("score_f1_fields.HYBASE_X115", paths.get(0));
        assertEquals("customer.level", paths.get(1));
        assertEquals("request.id", paths.get(2));
        assertTrue(!paths.contains("stale_intermediate"));
        assertEquals(Long.valueOf(12), fields.getInputFields().get(2).getVarId());
        assertEquals("VARIABLE", fields.getInputFields().get(2).getRefType());
        assertEquals("decision", fields.getOutputFields().get(0).getScriptName());
    }

    @Test
    public void requestKeySupportsRecursiveOperandExpression() throws Exception {
        RuleExperiment experiment = experiment("RATIO", "CONDITION");
        experiment.setRequestKeyPath("{\"kind\":\"CAST\",\"targetType\":\"STRING\",\"operand\":{\"kind\":\"FUNCTION\",\"functionCode\":\"numMax\",\"args\":[{\"kind\":\"REFERENCE\",\"refId\":21,\"refType\":\"VARIABLE\",\"code\":\"score\"},{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}]}}");
        Map<String, Object> params = new HashMap<>();
        params.put("score", 680);

        Method method = RuleExperimentService.class.getDeclaredMethod(
                "resolveRequestKey", RuleExperiment.class, RuleExperimentExecuteRequest.class, Map.class);
        method.setAccessible(true);

        assertEquals("680.0", method.invoke(new RuleExperimentService(), experiment, null, params));
    }

    @Test
    public void referencedDefinitionsPreferStableGroupRuleIds() {
        RuleExperiment experiment = experiment("RATIO", "CONDITION");
        RuleExperimentGroup group = group("champion", "CHAMPION", 100, 0, "", null);
        group.setRuleId(42L);
        group.setRuleCode("OLD_RULE_CODE");
        experiment.setGroups(Collections.singletonList(group));
        RuleExperimentService resolverService = new RuleExperimentService() {
            @Override
            public RuleExperiment getDetail(Long id) {
                return experiment;
            }
        };

        assertEquals(Collections.singletonList(42L), resolverService.listReferencedDefinitionIds(1L));
    }

    @Test
    public void linkedGlobalGroupResolutionUsesDefinitionIdWithoutProjectCodeFilter() throws Exception {
        RuleExperiment experiment = experiment("RATIO", "CONDITION");
        experiment.setProjectId(7L);
        experiment.setProjectCode("P0007");
        RuleExperimentGroup group = group("champion", "CHAMPION", 100, 0, "", null);
        group.setRuleId(88L);
        final boolean[] availabilityChecked = {false};

        setField(service, "definitionService", new RuleDefinitionService() {
            @Override
            public RuleDefinition getById(java.io.Serializable id) {
                RuleDefinition definition = new RuleDefinition();
                definition.setId(88L);
                definition.setRuleCode("GLOBAL_SCORE");
                definition.setStatus(1);
                return definition;
            }

            @Override
            public boolean isDefinitionAvailableInProject(Long definitionId, Long projectId) {
                availabilityChecked[0] = true;
                assertEquals(Long.valueOf(88L), definitionId);
                assertEquals(Long.valueOf(7L), projectId);
                return true;
            }
        });

        Method method = RuleExperimentService.class.getDeclaredMethod(
                "syncGroupRuleReference", RuleExperiment.class, RuleExperimentGroup.class);
        method.setAccessible(true);
        method.invoke(service, experiment, group);

        assertTrue(availabilityChecked[0]);
        assertEquals("GLOBAL_SCORE", group.getRuleCode());
    }

    private RuleExperiment experiment(String routingMode, String testRoutingMode) {
        RuleExperiment experiment = new RuleExperiment();
        experiment.setExperimentCode("EXP_TEST");
        experiment.setRoutingMode(routingMode);
        experiment.setTestRoutingMode(testRoutingMode);
        experiment.setTestExclusive(1);
        return experiment;
    }

    private RuleExperimentGroup group(String code, String type, int ratio, int sortOrder,
                                      String conditionExpression, String conditionConfig) {
        RuleExperimentGroup group = new RuleExperimentGroup();
        group.setGroupCode(code);
        group.setGroupName(code);
        group.setGroupType(type);
        group.setRuleCode(code + "_rule");
        group.setTrafficRatio(new BigDecimal(ratio));
        group.setConditionExpression(conditionExpression);
        group.setConditionConfig(conditionConfig);
        group.setStatus(1);
        group.setSortOrder(sortOrder);
        group.setInvokeExternalSource(1);
        return group;
    }

    private Object chooseProductionGroup(RuleExperiment experiment, List<RuleExperimentGroup> groups,
                                         Map<String, Object> params) throws Exception {
        Method method = RuleExperimentService.class.getDeclaredMethod("chooseProductionGroup",
                RuleExperiment.class, List.class, Map.class, String.class, String.class);
        method.setAccessible(true);
        return method.invoke(service, experiment, groups, params, "REQ001", null);
    }

    private List<?> chooseTestGroups(RuleExperiment experiment, List<RuleExperimentGroup> groups,
                                     Map<String, Object> params) throws Exception {
        Method method = RuleExperimentService.class.getDeclaredMethod("chooseTestGroups",
                RuleExperiment.class, List.class, Map.class, String.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(service, experiment, groups, params, "REQ001");
    }

    private void validateRuntimeGroups(RuleExperiment experiment, List<RuleExperimentGroup> groups) throws Exception {
        Method method = RuleExperimentService.class.getDeclaredMethod("validateRuntimeGroups",
                RuleExperiment.class, List.class);
        method.setAccessible(true);
        method.invoke(service, experiment, groups);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = RuleExperimentService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private RuleExperimentGroup routeGroup(Object routeChoice) throws Exception {
        Field field = routeChoice.getClass().getDeclaredField("group");
        field.setAccessible(true);
        return (RuleExperimentGroup) field.get(routeChoice);
    }
}
