package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IdempotencyKeyCalculatorTest {

    @Test
    public void omitsOnlyMissingComponentsAndKeepsStableReferenceIdentity() {
        JSONObject configuration = config(reference(101, "oldOrderCode"), reference(102, "optionalChannel"),
                reference(103, "optionalFlag"));
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("order", Map.of("code", "A-100", "flag", false));
        Map<String, String> paths = Map.of("VARIABLE:101", "order.code",
                "VARIABLE:102", "order.channel", "VARIABLE:103", "order.flag");

        var first = IdempotencyKeyCalculator.calculate(configuration, input, paths);
        var renamed = IdempotencyKeyCalculator.calculate(
                config(reference(101, "renamedOrderCode"), reference(102, "optionalChannel"),
                        reference(103, "renamedFlag")), input, paths);

        assertEquals(first.keyHash(), renamed.keyHash());
        assertEquals(2, first.componentCount());
        assertTrue(first.canonicalKey().contains("VARIABLE:101"));
        assertTrue(first.canonicalKey().contains("VARIABLE:103"));
        assertFalse(first.canonicalKey().contains("VARIABLE:102"));
        assertFalse(first.canonicalKey().contains("oldOrderCode"));
    }

    @Test
    public void normalizationMakesEquivalentNumbersAndMapOrdersTheSameKey() {
        JSONObject configuration = config(reference(1, "amount"), reference(2, "metadata"));
        Map<String, String> paths = Map.of("VARIABLE:1", "amount", "VARIABLE:2", "metadata");
        Map<String, Object> firstInput = new LinkedHashMap<>();
        firstInput.put("amount", 1);
        firstInput.put("metadata", Map.of("a", 1, "b", 2));
        Map<String, Object> secondInput = new LinkedHashMap<>();
        secondInput.put("metadata", Map.of("b", 2.0, "a", 1.0));
        secondInput.put("amount", 1.0);

        var first = IdempotencyKeyCalculator.calculate(configuration, firstInput, paths);
        var second = IdempotencyKeyCalculator.calculate(configuration, secondInput, paths);

        assertEquals(first.canonicalKey(), second.canonicalKey());
        assertEquals(first.keyHash(), second.keyHash());
        assertEquals(first.inputDigest(), second.inputDigest());
        assertEquals(64, first.keyHash().length());
    }

    @Test
    public void pureTransformationCanUseSeveralFieldsAndIgnoresAbsentInput() {
        JSONObject transformed = JSON.parseObject("{\"kind\":\"OPERATION\",\"terms\":["
                + "{\"operand\":{\"kind\":\"FUNCTION\",\"functionCode\":\"strUpper\",\"args\":["
                + "{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":1,\"code\":\"oldCode\"}]}} ,"
                + "{\"operator\":\"+\",\"operand\":{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":2,\"code\":\"optionalRegion\"}}]}" );
        JSONObject configuration = config(transformed);
        Map<String, String> paths = Map.of("VARIABLE:1", "code", "VARIABLE:2", "region");

        var first = IdempotencyKeyCalculator.calculate(configuration, Map.of("code", "abc"), paths);
        var second = IdempotencyKeyCalculator.calculate(configuration, Map.of("code", "ABC"), paths);
        var third = IdempotencyKeyCalculator.calculate(configuration, Map.of("code", "abc", "region", "N"), paths);

        assertEquals(first.keyHash(), second.keyHash());
        assertNotEquals(first.keyHash(), third.keyHash());
    }

    @Test
    public void rejectsAllEmptyInputsAndUnsafeNodes() {
        JSONObject configuration = config(reference(1, "orderId"));
        Map<String, String> paths = Map.of("VARIABLE:1", "orderId");
        assertThrows(IllegalArgumentException.class, () ->
                IdempotencyKeyCalculator.calculate(configuration, Map.of("orderId", "  "), paths));
        assertThrows(IllegalArgumentException.class, () ->
                IdempotencyKeyCalculator.calculate(configuration, Map.of(), paths));
        assertThrows(IllegalArgumentException.class, () ->
                IdempotencyKeyCalculator.calculate(configuration, Map.of("oldCode", "forged"), paths));

        for (JSONObject unsafe : List.of(
                JSON.parseObject("{\"kind\":\"PATH\",\"value\":\"orderId\"}"),
                JSON.parseObject("{\"kind\":\"LIST_QUERY\",\"listIds\":[1]}"),
                JSON.parseObject("{\"kind\":\"FUNCTION\",\"functionCode\":\"randomInt\",\"args\":[]}"),
                JSON.parseObject("{\"kind\":\"FUNCTION\",\"functionCode\":\"currentRule\",\"args\":[]}"),
                JSON.parseObject("{\"kind\":\"FUNCTION\",\"functionCode\":\"listMatch\",\"args\":[]}"),
                JSON.parseObject("{\"kind\":\"FUNCTION\",\"functionId\":5,\"functionCode\":\"strUpper\",\"args\":[]}"),
                JSON.parseObject("{\"kind\":\"REFERENCE\",\"refType\":\"MODEL\",\"refId\":5}"))) {
            assertThrows(IllegalArgumentException.class, () ->
                    IdempotencyKeyCalculator.calculate(config(unsafe), Map.of("orderId", "X"), paths));
        }
    }

    @Test
    public void sameValueFromDifferentFieldIdsDoesNotCollide() {
        var left = IdempotencyKeyCalculator.calculate(config(reference(1, "value")),
                Map.of("value", "A"), Map.of("VARIABLE:1", "value"));
        var right = IdempotencyKeyCalculator.calculate(config(reference(2, "value")),
                Map.of("value", "A"), Map.of("VARIABLE:2", "value"));
        assertNotEquals(left.keyHash(), right.keyHash());
    }

    @Test
    public void transformedReferenceIdentityIgnoresRenamedDisplayCode() {
        JSONObject first = JSON.parseObject("{\"kind\":\"FUNCTION\",\"functionCode\":\"strUpper\",\"args\":["
                + "{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":1,\"code\":\"old\"}]}" );
        JSONObject second = JSON.parseObject("{\"kind\":\"FUNCTION\",\"functionCode\":\"strUpper\",\"args\":["
                + "{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":1,\"code\":\"new\"}]}" );
        Map<String, String> paths = Map.of("VARIABLE:1", "value");

        var left = IdempotencyKeyCalculator.calculate(config(first), Map.of("value", "A"), paths);
        var right = IdempotencyKeyCalculator.calculate(config(second), Map.of("value", "A"), paths);

        assertEquals(left.keyHash(), right.keyHash());
    }

    private JSONObject config(JSONObject... operands) {
        JSONObject configuration = new JSONObject();
        configuration.put("enabled", true);
        configuration.put("components", java.util.Arrays.stream(operands).map(operand -> {
            JSONObject component = new JSONObject();
            component.put("operand", operand);
            return component;
        }).toList());
        return configuration;
    }

    private JSONObject reference(long id, String oldCode) {
        JSONObject node = new JSONObject();
        node.put("kind", "REFERENCE");
        node.put("refType", "VARIABLE");
        node.put("refId", id);
        node.put("code", oldCode);
        return node;
    }
}
