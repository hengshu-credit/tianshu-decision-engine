package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;

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

    private static RuleDefinitionInputField ruleField(String path, String type) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setScriptName(path);
        field.setFieldName(path);
        field.setFieldType(type);
        return field;
    }
}
