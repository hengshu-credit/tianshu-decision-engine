package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import org.junit.After;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DataObjectFieldReferenceResolverTest {

    @Test
    public void assignedObjectValueWinsEvenWhenReusableFieldHasRuntimeSource() {
        for (Object assigned : java.util.Arrays.asList(0, false, "", null, List.of())) {
            var plan = plan("API");
            Map<String, Object> request = new LinkedHashMap<>(); request.put("age", assigned);
            Map<String, Object> values = new LinkedHashMap<>(Map.of("request", request, "age", 999));
            plan.apply(values, plan.captureExplicitTargets(values));
            assertEquals(assigned, request.get("age"));
        }
    }

    @Test
    public void structureReferenceDoesNotBecomeRuntimeDependency() {
        var field = com.alibaba.fastjson.JSON.parseObject("{\"id\":30,\"refVariableId\":9,\"referenceMode\":\"STRUCTURE\"}", RuleDataObjectField.class);
        var direct = new RuleDefinitionInputField(); direct.setVarId(30L); direct.setRefType("DATA_OBJECT"); direct.setScriptName("request.age");
        var source = new RuleVariable(); source.setId(9L); source.setScriptName("remoteAge"); source.setVarSource("API");
        var plan = resolver.resolveSnapshot(List.of(direct), List.of(field), List.of(source));
        assertTrue(plan.requiredSourceNames().isEmpty());
        Map<String, Object> values = new LinkedHashMap<>(Map.of("remoteAge", 99));
        plan.apply(values, Set.of());
        assertTrue(!values.containsKey("request"));
    }

    private final DataObjectFieldReferenceResolver resolver =
            new DataObjectFieldReferenceResolver();
    private final ExecutionParameterBinder binder = new ExecutionParameterBinder();

    @After
    public void clearRuntimeContext() {
        RuntimeContextBridge.clear();
    }

    @Test
    public void sourceVariableIsAssembledIntoMappedObjectPath() {
        DataObjectFieldReferenceResolver.ReferencePlan plan = plan();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("age", "18");
        Set<String> explicitTargets = plan.captureExplicitTargets(input);

        Map<String, Object> values = binder.bindRuleInputs(
                plan.mergeBindingFields(Collections.emptyList()), input);
        plan.apply(values, explicitTargets);

        assertEquals(Double.valueOf(18D),
                ((Map<?, ?>) values.get("request")).get("age"));
        assertTrue(plan.requiredSourceNames().contains("age"));
    }

    @Test
    public void explicitObjectPathWinsOverReferencedVariable() {
        DataObjectFieldReferenceResolver.ReferencePlan plan = plan();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("age", "21");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("request", request);
        input.put("age", "18");
        Set<String> explicitTargets = plan.captureExplicitTargets(input);

        Map<String, Object> values = binder.bindRuleInputs(
                plan.mergeBindingFields(Collections.emptyList()), input);
        plan.apply(values, explicitTargets);

        assertEquals(Double.valueOf(21D),
                ((Map<?, ?>) values.get("request")).get("age"));
    }

    @Test
    public void explicitNullObjectPathAlsoWinsOverReferencedVariable() {
        DataObjectFieldReferenceResolver.ReferencePlan plan = plan();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("age", null);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("request", request);
        input.put("age", "18");
        Set<String> explicitTargets = plan.captureExplicitTargets(input);

        Map<String, Object> values = binder.bindRuleInputs(
                plan.mergeBindingFields(Collections.emptyList()), input);
        plan.apply(values, explicitTargets);

        assertNull(((Map<?, ?>) values.get("request")).get("age"));
    }

    @Test
    public void flatExplicitObjectPathIsNormalizedAndStillWins() {
        DataObjectFieldReferenceResolver.ReferencePlan plan = plan();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("request.age", "21");
        input.put("age", "18");
        Set<String> explicitTargets = plan.captureExplicitTargets(input);

        Map<String, Object> values = binder.bindRuleInputs(
                plan.mergeBindingFields(Collections.emptyList()), input);
        plan.apply(values, explicitTargets);

        assertEquals(Double.valueOf(21D),
                ((Map<?, ?>) values.get("request")).get("age"));
        assertTrue(!values.containsKey("request.age"));
    }

    private DataObjectFieldReferenceResolver.ReferencePlan plan() {
        return plan("INPUT");
    }

    @Test
    public void referencedConstantDefaultSuppliesTheMissingObjectField() {
        var plan = plan("CONSTANT");
        Map<String, Object> input = new LinkedHashMap<>();
        var bindingFields = plan.mergeBindingFields(Collections.emptyList());
        assertTrue(bindingFields.stream().anyMatch(field -> "CONSTANT".equals(field.getRefType())));
        Map<String, Object> values = binder.bindRuleInputs(bindingFields, input);
        RuleVariable constant = new RuleVariable();
        constant.setId(9L);
        constant.setVarCode("age");
        constant.setScriptName("age");
        constant.setVarType("NUMBER");
        constant.setVarSource("CONSTANT");
        constant.setDefaultValue("65");
        constant.setStatus(1);
        new VariableSourceResolver().resolveIntoSnapshot(List.of(constant), List.of(), List.of(), values, null);
        plan.apply(values, plan.captureExplicitTargets(input));
        assertEquals(65D, ((Number) ((Map<?, ?>) values.get("request")).get("age")).doubleValue(), 0D);
    }

    private DataObjectFieldReferenceResolver.ReferencePlan plan(String sourceType) {
        RuleDefinitionInputField target = new RuleDefinitionInputField();
        target.setVarId(30L);
        target.setRefType("DATA_OBJECT");
        target.setFieldName("age");
        target.setScriptName("request.age");
        target.setFieldType("NUMBER");

        RuleDataObjectField mappedField = new RuleDataObjectField();
        mappedField.setId(30L);
        mappedField.setRefVariableId(9L);

        RuleVariable source = new RuleVariable();
        source.setId(9L);
        source.setVarCode("age");
        source.setVarLabel("年龄");
        source.setScriptName("age");
        source.setVarType("NUMBER");
        source.setVarSource(sourceType);
        if ("CONSTANT".equals(sourceType)) source.setDefaultValue("65");
        source.setStatus(1);

        return resolver.resolveSnapshot(
                Collections.singletonList(target),
                Collections.singletonList(mappedField),
                Collections.singletonList(source));
    }
}
