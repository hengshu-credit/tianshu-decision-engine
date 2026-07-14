package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
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
        Assert.assertEquals(620d, ((Number) OperandValueResolver.resolve("{\"kind\":\"FUNCTION\",\"functionCode\":\"numMax\",\"args\":[{\"kind\":\"PATH\",\"value\":\"request.score\"},{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}]}", root)).doubleValue(), 0d);
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
    public void accessUsesTheSameNegativeIndexAndNestedPathSemanticsAsQlExpress() {
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("age", 36);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customer", customer);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", Arrays.asList("A", "B"));
        root.put("payload", payload);
        root.put("payloadJson", "{\"customer\":{\"age\":36}}");

        Assert.assertEquals("B", OperandValueResolver.resolve("{\"kind\":\"ACCESS\",\"accessType\":\"INDEX\",\"target\":{\"kind\":\"PATH\",\"value\":\"items\"},\"accessor\":{\"kind\":\"LITERAL\",\"value\":\"-1\",\"valueType\":\"NUMBER\"}}", root));
        Assert.assertEquals(36, OperandValueResolver.resolve("{\"kind\":\"ACCESS\",\"accessType\":\"KEY\",\"target\":{\"kind\":\"PATH\",\"value\":\"payload\"},\"accessor\":{\"kind\":\"LITERAL\",\"value\":\"customer.age\",\"valueType\":\"STRING\"}}", root));
        Assert.assertEquals(36, OperandValueResolver.resolve("{\"kind\":\"ACCESS\",\"accessType\":\"KEY\",\"target\":{\"kind\":\"PATH\",\"value\":\"payloadJson\"},\"accessor\":{\"kind\":\"LITERAL\",\"value\":\"customer.age\",\"valueType\":\"STRING\"}}", root));
    }

    @Test
    public void managedReferenceUsesStableIdValueInsteadOfStaleCode() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("oldCode", 10);
        Map<String, Object> references = new LinkedHashMap<>();
        references.put("VARIABLE:7", 99);

        Assert.assertEquals(99, OperandValueResolver.resolve(
                "{\"kind\":\"REFERENCE\",\"code\":\"oldCode\",\"refId\":7,\"refType\":\"VARIABLE\"}",
                values, references));
    }

    @Test
    public void buildsManagedReferenceValuesFromCurrentStableIdPaths() {
        Map<String, String> referencePaths = new LinkedHashMap<>();
        referencePaths.put("VARIABLE:7", "customer.currentScore");
        referencePaths.put("DATA_OBJECT:8", "customer.profile.age");
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("age", 36);
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("currentScore", 99);
        customer.put("profile", profile);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("customer", customer);
        Map<String, Object> trusted = new LinkedHashMap<>();
        trusted.put("CONSTANT:9", "ACTIVE");

        Map<String, Object> references = OperandValueResolver.buildReferenceValues(
                referencePaths, values, trusted);

        Assert.assertEquals(99, references.get("VARIABLE:7"));
        Assert.assertEquals(36, references.get("DATA_OBJECT:8"));
        Assert.assertEquals("ACTIVE", references.get("CONSTANT:9"));
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
                + "{\"kind\":\"FUNCTION\",\"functionCode\":\"numMax\",\"args\":[{\"kind\":\"LITERAL\",\"value\":\"4200\",\"valueType\":\"NUMBER\"},{\"kind\":\"FUNCTION\",\"functionCode\":\"numMin\",\"args\":["
                + "{\"kind\":\"OPERATION\",\"operator\":\"+\",\"operands\":[{\"kind\":\"OPERATION\",\"operator\":\"*\",\"operands\":[{\"kind\":\"OPERATION\",\"operator\":\"*\",\"operands\":[{\"kind\":\"FUNCTION\",\"functionCode\":\"numMin\",\"args\":[{\"kind\":\"FUNCTION\",\"functionCode\":\"numMax\",\"args\":[{\"kind\":\"REFERENCE\",\"refId\":101,\"refType\":\"VARIABLE\",\"code\":\"monthlyRepayment\"},{\"kind\":\"REFERENCE\",\"refId\":102,\"refType\":\"VARIABLE\",\"code\":\"usedAmount\"}]},{\"kind\":\"LITERAL\",\"value\":\"9000\",\"valueType\":\"NUMBER\"}]},{\"kind\":\"REFERENCE\",\"refId\":103,\"refType\":\"VARIABLE\",\"code\":\"riskFactor\"}]},{\"kind\":\"LITERAL\",\"value\":\"0.3\",\"valueType\":\"NUMBER\"}]},{\"kind\":\"OPERATION\",\"operator\":\"*\",\"operands\":[{\"kind\":\"REFERENCE\",\"refId\":104,\"refType\":\"VARIABLE\",\"code\":\"riskAmount\"},{\"kind\":\"LITERAL\",\"value\":\"0.5\",\"valueType\":\"NUMBER\"}]}]},{\"kind\":\"LITERAL\",\"value\":\"7000\",\"valueType\":\"NUMBER\"}]}]},{\"kind\":\"LITERAL\",\"value\":\"500\",\"valueType\":\"NUMBER\"}]}]},{\"kind\":\"LITERAL\",\"value\":\"500\",\"valueType\":\"NUMBER\"}]}";

        Assert.assertEquals(0, new BigDecimal("4500").compareTo((BigDecimal) OperandValueResolver.resolve(json, values)));
        Assert.assertEquals(Arrays.asList("monthlyRepayment", "usedAmount", "riskFactor", "riskAmount"), Arrays.asList(OperandValueResolver.collectPaths(json).toArray()));
    }

    @Test
    public void resolvesAllRegisteredBuiltinFunctionFamilies() {
        Assert.assertEquals(9d, ((Number) OperandValueResolver.resolve("{\"kind\":\"FUNCTION\",\"functionCode\":\"numMax\",\"args\":[{\"kind\":\"LITERAL\",\"value\":\"9\",\"valueType\":\"NUMBER\"},{\"kind\":\"LITERAL\",\"value\":\"2\",\"valueType\":\"NUMBER\"}]}", Collections.<String, Object>emptyMap())).doubleValue(), 0d);
        Assert.assertEquals("ABC", OperandValueResolver.resolve("{\"kind\":\"FUNCTION\",\"functionCode\":\"strUpper\",\"args\":[{\"kind\":\"LITERAL\",\"value\":\"abc\",\"valueType\":\"STRING\"}]}", Collections.<String, Object>emptyMap()));
        Assert.assertEquals("B", OperandValueResolver.resolve("{\"kind\":\"FUNCTION\",\"functionCode\":\"arrGet\",\"args\":[{\"kind\":\"ARRAY\",\"items\":[{\"kind\":\"LITERAL\",\"value\":\"A\",\"valueType\":\"STRING\"},{\"kind\":\"LITERAL\",\"value\":\"B\",\"valueType\":\"STRING\"}]},{\"kind\":\"LITERAL\",\"value\":\"1\",\"valueType\":\"NUMBER\"}]}", Collections.<String, Object>emptyMap()));
        Assert.assertTrue(String.valueOf(OperandValueResolver.resolve("{\"kind\":\"FUNCTION\",\"functionCode\":\"currentDate\",\"args\":[]}", Collections.<String, Object>emptyMap())).matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    public void delegatesManagedFunctionReferencesByStableId() {
        Object result = OperandValueResolver.resolve(
                JSON.parseObject("{\"kind\":\"FUNCTION\",\"functionId\":501,\"functionCode\":\"projectRisk\",\"args\":[{\"kind\":\"LITERAL\",\"value\":\"9\",\"valueType\":\"NUMBER\"}]}"),
                Collections.<String, Object>emptyMap(), Collections.<String, Object>emptyMap(),
                (functionId, functionCode, args) -> functionCode + ":" + functionId + ":" + args.get(0));

        Assert.assertEquals("projectRisk:501:9", result);
    }

    @Test
    public void rejectsUnknownOrIncompleteNodes() {
        assertResolveFails("{\"kind\":\"UNKNOWN\"}", "不支持");
        assertResolveFails("{\"kind\":\"OPERATION\",\"operator\":\"+\",\"operands\":[null]}", "参数");
        assertResolveFails("{\"kind\":\"OPERATION\",\"operator\":\"<\",\"operands\":[{\"kind\":\"LITERAL\",\"value\":\"1\",\"valueType\":\"NUMBER\"},{\"kind\":\"LITERAL\",\"value\":\"2\",\"valueType\":\"NUMBER\"},{\"kind\":\"LITERAL\",\"value\":\"3\",\"valueType\":\"NUMBER\"}]}", "2");
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
