package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OperandCompilerTest {

    @Test
    public void compilesTypedLiteralAndPath() {
        Assert.assertEquals("\"threshold\"", compile("{\"kind\":\"LITERAL\",\"value\":\"threshold\",\"valueType\":\"STRING\"}"));
        Assert.assertEquals("600", compile("{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}"));
        Assert.assertEquals("request.score", compile("{\"kind\":\"PATH\",\"value\":\"request.score\",\"code\":\"request.score\"}"));
    }

    @Test
    public void resolvesReferenceByStableId() {
        Map<String, String> refs = new HashMap<>();
        refs.put("VARIABLE:7", "creditScore");
        VarContext context = new VarContext(Collections.<Long, String>emptyMap(), Collections.<String, String>emptyMap(), refs);
        JSONObject operand = JSON.parseObject("{\"kind\":\"REFERENCE\",\"code\":\"score\",\"refId\":7,\"refType\":\"VARIABLE\"}");
        Assert.assertEquals("creditScore", OperandCompiler.compile(operand, context));
    }

    @Test
    public void compilesConstantReferenceByStableId() {
        Map<Long, String> constants = new HashMap<>();
        constants.put(7L, "1.0 / 0.0");
        VarContext context = new VarContext(Collections.<Long, String>emptyMap(),
                Collections.<String, String>emptyMap(), Collections.<String, String>emptyMap(), constants);
        JSONObject operand = JSON.parseObject("{\"kind\":\"REFERENCE\",\"code\":\"POSITIVE_INFINITY\",\"refId\":7,\"refType\":\"CONSTANT\"}");

        Assert.assertEquals("1.0 / 0.0", OperandCompiler.compile(operand, context));
    }

    @Test
    public void rejectsConstantReferenceWithoutTrustedValue() {
        VarContext context = new VarContext(Collections.<Long, String>emptyMap(),
                Collections.<String, String>emptyMap(), Collections.<String, String>emptyMap(),
                Collections.<Long, String>emptyMap());
        JSONObject operand = JSON.parseObject("{\"kind\":\"REFERENCE\",\"code\":\"EMPTY_STRING\",\"refId\":8,\"refType\":\"CONSTANT\"}");

        try {
            OperandCompiler.compile(operand, context);
            Assert.fail("Expected missing constant reference to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("常量"));
            Assert.assertTrue(expected.getMessage().contains("8"));
        }
    }

    @Test
    public void resolvesLegacyConstantReferenceByStableId() {
        VarContext context = new VarContext(Collections.<Long, String>emptyMap(),
                Collections.<String, String>emptyMap(), Collections.<String, String>emptyMap(),
                Collections.singletonMap(8L, "''"));

        Assert.assertEquals("''", context.resolveVar(8L, "CONSTANT", "EMPTY_STRING"));
    }

    @Test
    public void compilesNestedFunction() {
        Assert.assertEquals("max(request.score, 600)", compile("{\"kind\":\"FUNCTION\",\"functionCode\":\"max\",\"args\":[{\"kind\":\"PATH\",\"value\":\"request.score\"},{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}]}"));
    }

    private String compile(String json) {
        return OperandCompiler.compile(JSON.parseObject(json), null);
    }
}
