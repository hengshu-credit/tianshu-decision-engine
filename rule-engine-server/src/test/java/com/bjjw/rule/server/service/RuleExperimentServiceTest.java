package com.bjjw.rule.server.service;

import com.bjjw.rule.core.engine.QLExpressEngine;
import com.bjjw.rule.model.entity.RuleExperiment;
import com.bjjw.rule.model.entity.RuleExperimentGroup;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
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

    private RuleExperimentGroup routeGroup(Object routeChoice) throws Exception {
        Field field = routeChoice.getClass().getDeclaredField("group");
        field.setAccessible(true);
        return (RuleExperimentGroup) field.get(routeChoice);
    }
}
