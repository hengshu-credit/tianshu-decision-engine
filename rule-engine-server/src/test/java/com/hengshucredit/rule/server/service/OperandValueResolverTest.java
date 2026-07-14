package com.hengshucredit.rule.server.service;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class OperandValueResolverTest {

    @Test
    public void resolvesLiteralPathReferenceAndFunction() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("score", 620);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("request", request);
        root.put("fallback", 600);

        Assert.assertEquals("PASS", OperandValueResolver.resolve("{\"kind\":\"LITERAL\",\"value\":\"PASS\",\"valueType\":\"STRING\"}", root));
        Assert.assertEquals(620, OperandValueResolver.resolve("{\"kind\":\"PATH\",\"value\":\"request.score\"}", root));
        Assert.assertEquals(600, OperandValueResolver.resolve("{\"kind\":\"REFERENCE\",\"code\":\"fallback\",\"refId\":1,\"refType\":\"VARIABLE\"}", root));
        Assert.assertEquals(620, OperandValueResolver.resolve("{\"kind\":\"FUNCTION\",\"functionCode\":\"max\",\"args\":[{\"kind\":\"PATH\",\"value\":\"request.score\"},{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}]}", root));
    }

    @Test
    public void unresolvedPathReturnsNullWithoutChangingCase() {
        Assert.assertNull(OperandValueResolver.resolve("{\"kind\":\"PATH\",\"value\":\"Payload.Custom_Path\"}", new LinkedHashMap<String, Object>()));
    }

    @Test
    public void constantReferenceUsesTrustedValueByStableId() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("EMPTY_STRING", "tampered");
        Map<String, Object> references = new LinkedHashMap<>();
        references.put("CONSTANT:7", "");

        Object resolved = OperandValueResolver.resolve(
                "{\"kind\":\"REFERENCE\",\"code\":\"EMPTY_STRING\",\"refId\":7,\"refType\":\"CONSTANT\"}",
                values, references);

        Assert.assertEquals("", resolved);
    }

    @Test
    public void missingConstantReferenceFailsInsteadOfReadingCallerInput() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("EMPTY_STRING", "tampered");

        try {
            OperandValueResolver.resolve(
                    "{\"kind\":\"REFERENCE\",\"code\":\"EMPTY_STRING\",\"refId\":7,\"refType\":\"CONSTANT\"}",
                    values, new LinkedHashMap<String, Object>());
            Assert.fail("Expected missing constant reference to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("常量"));
            Assert.assertTrue(expected.getMessage().contains("7"));
        }
    }

    @Test
    public void resolvesOperationAccessCastAndArray() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("score", "12.5");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("payload", payload);
        root.put("items", Arrays.asList("A", "B"));

        Assert.assertEquals(new BigDecimal("25.0"), OperandValueResolver.resolve("{\"kind\":\"OPERATION\",\"operator\":\"*\",\"operands\":[{\"kind\":\"CAST\",\"targetType\":\"NUMBER\",\"operand\":{\"kind\":\"ACCESS\",\"accessType\":\"KEY\",\"target\":{\"kind\":\"PATH\",\"value\":\"payload\"},\"accessor\":{\"kind\":\"LITERAL\",\"value\":\"score\",\"valueType\":\"STRING\"}}},{\"kind\":\"LITERAL\",\"value\":\"2\",\"valueType\":\"NUMBER\"}]}" , root));
        Assert.assertEquals("B", OperandValueResolver.resolve("{\"kind\":\"ACCESS\",\"accessType\":\"INDEX\",\"target\":{\"kind\":\"PATH\",\"value\":\"items\"},\"accessor\":{\"kind\":\"LITERAL\",\"value\":\"1\",\"valueType\":\"NUMBER\"}}", root));
        Assert.assertEquals(Arrays.asList("A", new BigDecimal("3")), OperandValueResolver.resolve("{\"kind\":\"ARRAY\",\"items\":[{\"kind\":\"LITERAL\",\"value\":\"A\",\"valueType\":\"STRING\"},{\"kind\":\"LITERAL\",\"value\":\"3\",\"valueType\":\"NUMBER\"}]}", root));
    }

    @Test
    public void resolvesAmountFormula() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("monthlyRepayment", 5000);
        values.put("usedAmount", 6000);
        values.put("riskFactor", 1.2);
        values.put("riskAmount", 4000);

        String json = "{\"kind\":\"OPERATION\",\"operator\":\"*\",\"operands\":["
                + "{\"kind\":\"FUNCTION\",\"functionCode\":\"numCeil\",\"args\":[{\"kind\":\"OPERATION\",\"operator\":\"/\",\"operands\":["
                + "{\"kind\":\"FUNCTION\",\"functionCode\":\"max\",\"args\":[{\"kind\":\"LITERAL\",\"value\":\"4200\",\"valueType\":\"NUMBER\"},{\"kind\":\"FUNCTION\",\"functionCode\":\"min\",\"args\":["
                + "{\"kind\":\"OPERATION\",\"operator\":\"+\",\"operands\":[{\"kind\":\"OPERATION\",\"operator\":\"*\",\"operands\":[{\"kind\":\"FUNCTION\",\"functionCode\":\"min\",\"args\":[{\"kind\":\"FUNCTION\",\"functionCode\":\"max\",\"args\":[{\"kind\":\"REFERENCE\",\"refId\":101,\"refType\":\"VARIABLE\",\"code\":\"monthlyRepayment\"},{\"kind\":\"REFERENCE\",\"refId\":102,\"refType\":\"VARIABLE\",\"code\":\"usedAmount\"}]},{\"kind\":\"LITERAL\",\"value\":\"9000\",\"valueType\":\"NUMBER\"}]},{\"kind\":\"REFERENCE\",\"refId\":103,\"refType\":\"VARIABLE\",\"code\":\"riskFactor\"},{\"kind\":\"LITERAL\",\"value\":\"0.3\",\"valueType\":\"NUMBER\"}]},{\"kind\":\"OPERATION\",\"operator\":\"*\",\"operands\":[{\"kind\":\"REFERENCE\",\"refId\":104,\"refType\":\"VARIABLE\",\"code\":\"riskAmount\"},{\"kind\":\"LITERAL\",\"value\":\"0.5\",\"valueType\":\"NUMBER\"}]}]},{\"kind\":\"LITERAL\",\"value\":\"7000\",\"valueType\":\"NUMBER\"}]}]},{\"kind\":\"LITERAL\",\"value\":\"500\",\"valueType\":\"NUMBER\"}]}]},{\"kind\":\"LITERAL\",\"value\":\"500\",\"valueType\":\"NUMBER\"}]}";

        Assert.assertEquals(new BigDecimal("4500"), OperandValueResolver.resolve(json, values));
        Assert.assertEquals(Arrays.asList("monthlyRepayment", "usedAmount", "riskFactor", "riskAmount"), Arrays.asList(OperandValueResolver.collectPaths(json).toArray()));
    }

    @Test
    public void rejectsUnknownOrIncompleteNodes() {
        assertResolveFails("{\"kind\":\"UNKNOWN\"}", "不支持");
        assertResolveFails("{\"kind\":\"OPERATION\",\"operator\":\"+\",\"operands\":[null]}", "参数");
    }

    private void assertResolveFails(String json, String message) {
        try {
            OperandValueResolver.resolve(json, new LinkedHashMap<String, Object>());
            Assert.fail("Expected resolve to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }
}
