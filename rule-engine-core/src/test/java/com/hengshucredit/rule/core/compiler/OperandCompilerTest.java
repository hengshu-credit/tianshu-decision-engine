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
    public void compilesNestedFunction() {
        Assert.assertEquals("max(request.score, 600)", compile("{\"kind\":\"FUNCTION\",\"functionCode\":\"max\",\"args\":[{\"kind\":\"PATH\",\"value\":\"request.score\"},{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}]}"));
    }

    private String compile(String json) {
        return OperandCompiler.compile(JSON.parseObject(json), null);
    }
}
