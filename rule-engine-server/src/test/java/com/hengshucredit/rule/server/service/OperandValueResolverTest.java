package com.hengshucredit.rule.server.service;

import org.junit.Assert;
import org.junit.Test;

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
}
