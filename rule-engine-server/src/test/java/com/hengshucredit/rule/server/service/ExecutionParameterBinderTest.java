package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ExecutionParameterBinderTest {

    private final ExecutionParameterBinder binder = new ExecutionParameterBinder();

    @Test
    public void bindsNumericStringsAtNestedPathsWithoutChangingStrings() {
        Map<String, Object> input = JSON.parseObject(
                "{\"age\":\"22\",\"card\":{\"score\":\"350.5\"},\"idcard_no\":\"0012\"}");

        Map<String, Object> bound = binder.bindRuleInputs(Arrays.asList(
                ruleField("age", "INTEGER"),
                ruleField("card.score", "DECIMAL"),
                ruleField("idcard_no", "STRING")
        ), input);

        assertEquals(Integer.valueOf(22), bound.get("age"));
        assertEquals(new BigDecimal("350.5"), ((Map<?, ?>) bound.get("card")).get("score"));
        assertEquals("0012", bound.get("idcard_no"));
    }

    @Test
    public void bindsModelInputsWithTheSameTypeRules() {
        RuleModelInputField score = new RuleModelInputField();
        score.setScriptName("score");
        score.setFieldType("DOUBLE");

        Map<String, Object> bound = binder.bindModelInputs(
                Arrays.asList(score), JSON.parseObject("{\"score\":\"98.6\"}"));

        assertEquals(98.6d, ((Number) bound.get("score")).doubleValue(), 0d);
    }

    @Test
    public void rejectsInvalidNumbersWithFieldPath() {
        try {
            binder.bindRuleInputs(Arrays.asList(ruleField("age", "INTEGER")),
                    JSON.parseObject("{\"age\":\"twenty-two\"}"));
            fail("expected invalid number error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("age"));
            assertTrue(e.getMessage().contains("INTEGER"));
        }
    }

    @Test
    public void bindsBooleanLongArrayAndObjectWithTheSameNestedPathRules() {
        Map<String, Object> bound = binder.bindRuleInputs(Arrays.asList(
                ruleField("enabled", "BOOLEAN"),
                ruleField("request.sequence", "LONG"),
                ruleField("tags", "ARRAY"),
                ruleField("profile", "OBJECT")
        ), JSON.parseObject("{\"enabled\":\"false\",\"request\":{\"sequence\":\"900719925474099\"},"
                + "\"tags\":\"[\\\"A\\\",\\\"B\\\"]\",\"profile\":\"{\\\"level\\\":\\\"A\\\"}\"}"));

        assertEquals(Boolean.FALSE, bound.get("enabled"));
        assertEquals(Long.valueOf(900719925474099L), ((Map<?, ?>) bound.get("request")).get("sequence"));
        assertEquals(Arrays.asList("A", "B"), bound.get("tags"));
        assertEquals("A", ((Map<?, ?>) bound.get("profile")).get("level"));
    }

    @Test
    public void rejectsAmbiguousBooleanStrings() {
        try {
            binder.bindRuleInputs(Arrays.asList(ruleField("enabled", "BOOLEAN")),
                    JSON.parseObject("{\"enabled\":\"yes\"}"));
            fail("expected invalid boolean error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("enabled"));
            assertTrue(e.getMessage().contains("BOOLEAN"));
        }
    }

    @Test
    public void selectedDataObjectStatusCapturesMissingAndInvalidWithoutAbortingBinding() {
        RuleDefinitionInputField missing = ruleField("request.age", "INTEGER");
        missing.setVarId(11L);
        missing.setRefType("DATA_OBJECT");
        RuleDefinitionInputField invalid = ruleField("request.score", "DOUBLE");
        invalid.setVarId(12L);
        invalid.setRefType("DATA_OBJECT");
        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setStatusReferenceKeys(new java.util.LinkedHashSet<>(Arrays.asList(
                "DATA_OBJECT:11", "DATA_OBJECT:12")));

        Map<String, Object> bound = binder.bindRuleInputs(Arrays.asList(missing, invalid),
                JSON.parseObject("{\"request\":{\"score\":\"not-a-number\"}}"), options);

        assertEquals("MISSING", options.getSourceStates().get("DATA_OBJECT:11").get("PRESENCE"));
        assertEquals("INVALID", options.getSourceStates().get("DATA_OBJECT:12").get("PRESENCE"));
        assertEquals(null, ((Map<?, ?>) bound.get("request")).get("score"));
    }

    @Test
    public void unselectedInvalidDataObjectKeepsFailFastCompatibility() {
        RuleDefinitionInputField field = ruleField("request.score", "DOUBLE");
        field.setVarId(12L);
        field.setRefType("DATA_OBJECT");
        try {
            binder.bindRuleInputs(Collections.singletonList(field),
                    JSON.parseObject("{\"request\":{\"score\":\"not-a-number\"}}"),
                    VariableResolveOptions.defaults());
            fail("expected invalid number error");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("request.score"));
        }
    }

    private static RuleDefinitionInputField ruleField(String path, String type) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setScriptName(path);
        field.setFieldName(path);
        field.setFieldType(type);
        return field;
    }
}
