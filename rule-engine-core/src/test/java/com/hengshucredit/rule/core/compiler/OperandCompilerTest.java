package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
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

    @Test
    public void compilesFlatMixedOperationTerms() {
        Assert.assertEquals("(1 + 2 * 3 - 4)", compile(
                "{\"kind\":\"OPERATION\",\"terms\":["
                        + "{\"operand\":{\"kind\":\"LITERAL\",\"value\":\"1\",\"valueType\":\"NUMBER\"}},"
                        + "{\"operator\":\"+\",\"operand\":{\"kind\":\"LITERAL\",\"value\":\"2\",\"valueType\":\"NUMBER\"}},"
                        + "{\"operator\":\"*\",\"operand\":{\"kind\":\"LITERAL\",\"value\":\"3\",\"valueType\":\"NUMBER\"}},"
                        + "{\"operator\":\"-\",\"operand\":{\"kind\":\"LITERAL\",\"value\":\"4\",\"valueType\":\"NUMBER\"}}]}"));
    }

    @Test
    public void rejectsLegacyBinaryOperationShape() {
        assertCompileFails("{\"kind\":\"OPERATION\",\"operator\":\"+\",\"operands\":[]}", "terms");
    }

    @Test
    public void resolvesManagedFunctionCodeByStableId() {
        VarContext context = new VarContext(Collections.<Long, String>emptyMap(),
                Collections.<String, String>emptyMap(), Collections.<String, String>emptyMap(),
                Collections.<Long, String>emptyMap(), Collections.singletonMap(88L, "renamedFunction"),
                Collections.singletonMap(88L, 2));
        JSONObject operand = JSON.parseObject("{\"kind\":\"FUNCTION\",\"functionId\":88,\"functionCode\":\"oldFunction\",\"args\":[{\"kind\":\"LITERAL\",\"value\":\"1\",\"valueType\":\"NUMBER\"},{\"kind\":\"LITERAL\",\"value\":\"2\",\"valueType\":\"NUMBER\"}]}");

        Assert.assertEquals("renamedFunction(1, 2)", OperandCompiler.compile(operand, context));
    }

    @Test
    public void compilesRecursiveExpressionNodes() {
        Assert.assertEquals("(request.amount + 100)", compile("{\"kind\":\"OPERATION\",\"terms\":[{\"operand\":{\"kind\":\"PATH\",\"value\":\"request.amount\"}},{\"operator\":\"+\",\"operand\":{\"kind\":\"LITERAL\",\"value\":\"100\",\"valueType\":\"NUMBER\"}}]}"));
        Assert.assertEquals("objGet(request.payload, \"score\")", compile("{\"kind\":\"ACCESS\",\"accessType\":\"KEY\",\"target\":{\"kind\":\"PATH\",\"value\":\"request.payload\"},\"accessor\":{\"kind\":\"LITERAL\",\"value\":\"score\",\"valueType\":\"STRING\"}}"));
        Assert.assertEquals("arrGet(request.items, 0)", compile("{\"kind\":\"ACCESS\",\"accessType\":\"INDEX\",\"target\":{\"kind\":\"PATH\",\"value\":\"request.items\"},\"accessor\":{\"kind\":\"LITERAL\",\"value\":\"0\",\"valueType\":\"NUMBER\"}}"));
        Assert.assertEquals("toNumberValue(\"12.5\")", compile("{\"kind\":\"CAST\",\"targetType\":\"NUMBER\",\"operand\":{\"kind\":\"LITERAL\",\"value\":\"12.5\",\"valueType\":\"STRING\"}}"));
        Assert.assertEquals("[1, \"A\"]", compile("{\"kind\":\"ARRAY\",\"items\":[{\"kind\":\"LITERAL\",\"value\":\"1\",\"valueType\":\"NUMBER\"},{\"kind\":\"LITERAL\",\"value\":\"A\",\"valueType\":\"STRING\"}]}"));
        Assert.assertEquals("listQuery([1, 2], [\"MOBILE\"], \"ANY_FIELD_ALL_LISTS\", \"IN_LIST\")", compile("{\"kind\":\"LIST_QUERY\",\"listIds\":[1,2],\"itemTypes\":[\"MOBILE\"],\"combinationMode\":\"ANY_FIELD_ALL_LISTS\",\"matchMode\":\"IN_LIST\"}"));
    }

    @Test
    public void compilesAmountFormulaWithStableReferences() {
        JSONObject expression = operation("*",
                function("numCeil", operation("/",
                        function("numMax", number("4200"), function("numMin",
                                operation("+",
                                        operation("*", operation("*", function("numMin", function("numMax", reference(101, "monthlyRepayment"), reference(102, "usedAmount")), number("9000")), reference(103, "riskFactor")), number("0.3")),
                                        operation("*", reference(104, "riskAmount"), number("0.5"))),
                                number("7000"))),
                        number("500"))),
                number("500"));

        String script = OperandCompiler.compile(expression, null);
        Assert.assertEquals("(numCeil((numMax(4200, numMin((((numMin(numMax(monthlyRepayment, usedAmount), 9000) * riskFactor) * 0.3) + (riskAmount * 0.5)), 7000)) / 500)) * 500)", script);

        Map<String, Object> values = new HashMap<>();
        values.put("monthlyRepayment", 5000);
        values.put("usedAmount", 6000);
        values.put("riskFactor", 1.2);
        values.put("riskAmount", 4000);
        RuleResult result = new QLExpressEngine().execute(script, values);
        Assert.assertTrue(result.getErrorMessage(), result.isSuccess());
        Assert.assertEquals(4500d, ((Number) result.getResult()).doubleValue(), 0d);
    }

    @Test
    public void rejectsInvalidExpressionNodes() {
        assertCompileFails("{\"kind\":\"UNKNOWN\"}", "不支持");
        assertCompileFails("{\"kind\":\"OPERATION\",\"terms\":[{\"operand\":null},{\"operator\":\"+\",\"operand\":null}]}", "参数");
        assertCompileFails("{\"kind\":\"OPERATION\",\"terms\":[{\"operand\":{\"kind\":\"LITERAL\",\"value\":\"1\",\"valueType\":\"NUMBER\"}},{\"operator\":\"^\",\"operand\":{\"kind\":\"LITERAL\",\"value\":\"2\",\"valueType\":\"NUMBER\"}}]}", "不支持");
        assertCompileFails("{\"kind\":\"REFERENCE\",\"code\":\"score\"}", "ID");
    }

    private void assertCompileFails(String json, String message) {
        try {
            compile(json);
            Assert.fail("Expected compile to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }

    private JSONObject number(String value) {
        JSONObject node = new JSONObject();
        node.put("kind", "LITERAL");
        node.put("value", value);
        node.put("valueType", "NUMBER");
        return node;
    }

    private JSONObject reference(long id, String code) {
        JSONObject node = new JSONObject();
        node.put("kind", "REFERENCE");
        node.put("refId", id);
        node.put("refType", "VARIABLE");
        node.put("code", code);
        return node;
    }

    private JSONObject function(String code, JSONObject... args) {
        JSONObject node = new JSONObject();
        node.put("kind", "FUNCTION");
        node.put("functionCode", code);
        node.put("args", args);
        return node;
    }

    private JSONObject operation(String operator, JSONObject... operands) {
        JSONObject node = new JSONObject();
        JSONArray terms = new JSONArray();
        for (int i = 0; i < operands.length; i++) {
            JSONObject term = new JSONObject();
            if (i > 0) term.put("operator", operator);
            term.put("operand", operands[i]);
            terms.add(term);
        }
        node.put("kind", "OPERATION");
        node.put("terms", terms);
        return node;
    }

    private String compile(String json) {
        return OperandCompiler.compile(JSON.parseObject(json), null);
    }
}
