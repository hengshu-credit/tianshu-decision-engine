package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteRequest;
import com.hengshucredit.rule.model.entity.RuleExperiment;
import com.hengshucredit.rule.model.entity.RuleExperimentGroup;
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

import static org.junit.Assert.assertEquals;

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
            public List<RuleDefinitionInputField> listInputFields(Long definitionId) {
                RuleDefinitionInputField input = new RuleDefinitionInputField();
                input.setScriptName("score_f1_fields.HYBASE_X115");
                input.setFieldType("DOUBLE");
                return Collections.singletonList(input);
            }

            @Override
            public List<RuleDefinitionOutputField> listOutputFields(Long definitionId) {
                RuleDefinitionOutputField output = new RuleDefinitionOutputField();
                output.setScriptName("decision");
                return Collections.singletonList(output);
            }
        });
        setField(resolverService, "ruleFieldAnalyzer", new RuleFieldAnalyzer() {
            @Override
            public ResolvedFields resolveFields(Long definitionId, String modelJson, String modelType, Long projectId) {
                RuleDefinitionInputField input = new RuleDefinitionInputField();
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
