package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActionDataCompilerTest {

    @Test
    public void compileUnifiedOperands() {
        JSONArray actionData = JSON.parseArray("["
                + "{\"type\":\"assign\",\"targetOperand\":{\"kind\":\"PATH\",\"value\":\"result\"},\"valueOperand\":{\"kind\":\"LITERAL\",\"value\":\"PASS\",\"valueType\":\"STRING\"}},"
                + "{\"type\":\"func-call\",\"functionCode\":\"max\",\"args\":[{\"kind\":\"PATH\",\"value\":\"request.score\"},{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}]}"
                + "]");

        String script = ActionDataCompiler.compile(actionData);

        assertTrue(script.contains("result = \"PASS\""));
        assertTrue(script.contains("max(request.score, 600)"));
    }

    @Test
    public void compileListConditionUsesServerListFunction() {
        JSONArray actionData = JSON.parseArray("[{\"type\":\"if-block\",\"branches\":[{"
                + "\"type\":\"if\",\"leftOperand\":{\"kind\":\"PATH\",\"value\":\"mobile\",\"valueType\":\"STRING\"},"
                + "\"operator\":\"in_list\",\"rightOperand\":{\"kind\":\"LIST_QUERY\",\"listIds\":[9,10],"
                + "\"itemTypes\":[\"MOBILE\"],\"combinationMode\":\"ANY_FIELD_ANY_LIST\",\"matchMode\":\"IN_LIST\"},"
                + "\"actions\":[{\"type\":\"assign\",\"targetOperand\":{\"kind\":\"PATH\",\"value\":\"hit\"},"
                + "\"valueOperand\":{\"kind\":\"LITERAL\",\"value\":\"true\",\"valueType\":\"BOOLEAN\"}}]}]}]");

        String script = ActionDataCompiler.compile(actionData);

        assertTrue(script.contains("listMatch([mobile], [9, 10], \"ANY_FIELD_ANY_LIST\", \"IN_LIST\", [\"MOBILE\"])"));
    }

    @Test
    public void compileUsesFieldLevelVarIds() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(1L, "scoreInput");
        varIdMap.put(2L, "decisionOutput");
        varIdMap.put(3L, "statusInput");
        varIdMap.put(4L, "flagOutput");
        VarContext context = new VarContext(varIdMap);
        JSONArray actionData = JSON.parseArray("["
                + "{\"type\":\"ternary\",\"target\":\"decision\",\"_targetVarId\":2,\"condVar\":\"score\",\"_condVarId\":1,\"condOp\":\">=\",\"condValue\":\"60\",\"trueValue\":\"\\\"PASS\\\"\",\"falseValue\":\"\\\"REJECT\\\"\"},"
                + "{\"type\":\"in-check\",\"target\":\"flag\",\"_targetVarId\":4,\"checkVar\":\"status\",\"_checkVarId\":3,\"inValues\":[\"\\\"A\\\"\"],\"trueValue\":\"true\",\"falseValue\":\"false\"}"
                + "]");

        String script = ActionDataCompiler.compile(actionData, context);

        assertTrue(script.contains("decisionOutput = scoreInput >="));
        assertTrue(script.contains("flagOutput = statusInput in"));
    }

    @Test
    public void compileFunctionArgsUseArgRefs() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(1L, "applicant.score");
        VarContext context = new VarContext(varIdMap);
        JSONArray actionData = JSON.parseArray("["
                + "{\"type\":\"func-call\",\"target\":\"risk\",\"funcName\":\"max\",\"args\":[\"score\",\"100\"],\"_argRefs\":[{\"_varId\":1},null]}"
                + "]");

        String script = ActionDataCompiler.compile(actionData, context);

        assertTrue(script.contains("risk = max(applicant.score, 100)"));
    }

    @Test
    public void compileFunctionTreatsUnreferencedIdentifierAsLiteralAndSyncsTarget() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(6L, "idcard_no");
        varIdMap.put(8L, "credit_time");
        VarContext context = new VarContext(varIdMap);
        JSONArray actionData = JSON.parseArray("["
                + "{\"type\":\"func-call\",\"target\":\"age\",\"funcName\":\"idCardAge\","
                + "\"args\":[\"idcard_no\",\"credit_time\",\"DAY\"],"
                + "\"_argRefs\":[{\"_varId\":6},{\"_varId\":8},null]}"
                + "]");

        String script = ActionDataCompiler.compile(actionData, context);

        assertTrue(script.contains("age = idCardAge(idcard_no, credit_time, \"DAY\")"));
        assertTrue(script.contains("setRuntimeValue(\"age\", age)"));
    }

    @Test
    public void compileRuleCallSupportsWholeResultAndOutputField() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(5L, "risk.decision");
        VarContext context = new VarContext(varIdMap);
        JSONArray actionData = JSON.parseArray("["
                + "{\"type\":\"rule-call\",\"target\":\"decision\",\"_targetVarId\":5,\"ruleCode\":\"credit_flow\"},"
                + "{\"type\":\"rule-call\",\"target\":\"score\",\"ruleCode\":\"score_card\",\"outputField\":\"score\"}"
                + "]");

        String script = ActionDataCompiler.compile(actionData, context);

        assertTrue(script.contains("risk.decision = executeRule(\"credit_flow\")"));
        assertTrue(script.contains("score = executeRuleField(\"score_card\", \"score\")"));
    }

    @Test
    public void compileRuleCallUsesStableRuleIdWhenAvailable() {
        JSONArray actionData = JSON.parseArray("["
                + "{\"type\":\"rule-call\",\"ruleId\":1,\"ruleCode\":\"JCZR\"},"
                + "{\"type\":\"rule-call\",\"target\":\"score\",\"ruleId\":2,\"ruleCode\":\"SCORE\",\"outputField\":\"score\"}"
                + "]");

        String script = ActionDataCompiler.compile(actionData);

        assertTrue(script.contains("executeRuleById(\"1\")"));
        assertTrue(script.contains("score = executeRuleFieldById(\"2\", \"score\")"));
    }

    @Test
    public void compileRuleCallIgnoresRetainedMappingWhenExplicitlyDisabled() {
        JSONArray actionData = JSON.parseArray("["
                + "{\"type\":\"rule-call\",\"ruleId\":2,\"ruleCode\":\"SCORE\","
                + "\"enableOutputMapping\":false,\"outputField\":\"score\","
                + "\"targetOperand\":{\"kind\":\"REFERENCE\",\"refId\":5,"
                + "\"refType\":\"VARIABLE\",\"code\":\"risk.score\",\"resolved\":true}}"
                + "]");

        String script = ActionDataCompiler.compile(actionData);

        assertEquals("executeRuleById(\"2\")", script);
        assertFalse(script.contains("executeRuleField"));
        assertFalse(script.contains("risk.score ="));
    }

    @Test
    public void switchBlockExecutesMatchedCaseAndDefault() {
        JSONArray actionData = JSON.parseArray("["
                + "{\"type\":\"switch-block\",\"matchVar\":\"grade\",\"cases\":["
                + "{\"value\":\"A\",\"actions\":[{\"type\":\"assign\",\"target\":\"decision\",\"value\":\"\\\"PASS\\\"\"}]},"
                + "{\"value\":\"B\",\"actions\":[{\"type\":\"assign\",\"target\":\"decision\",\"value\":\"\\\"REVIEW\\\"\"}]}"
                + "],\"defaultActions\":[{\"type\":\"assign\",\"target\":\"decision\",\"value\":\"\\\"REJECT\\\"\"}]}"
                + "]");
        String script = ActionDataCompiler.compile(actionData) + "\ndecision";
        QLExpressEngine engine = new QLExpressEngine();

        Map<String, Object> matchedParams = new LinkedHashMap<>();
        matchedParams.put("grade", "A");
        RuleResult matched = engine.execute(script, matchedParams);

        assertTrue(matched.getErrorMessage(), matched.isSuccess());
        assertEquals("PASS", matched.getResult());

        Map<String, Object> defaultParams = new LinkedHashMap<>();
        defaultParams.put("grade", "C");
        RuleResult defaulted = engine.execute(script, defaultParams);

        assertTrue(defaulted.getErrorMessage(), defaulted.isSuccess());
        assertEquals("REJECT", defaulted.getResult());
    }

    @Test
    public void roundingAssignExecutesWithRoundScaleBuiltin() {
        JSONArray actionData = JSON.parseArray("["
                + "{\"type\":\"assign\",\"target\":\"amount\",\"value\":\"10 / 3\",\"enableRounding\":true,\"decimalPlaces\":2,\"roundingMode\":\"HALF_UP\"}"
                + "]");

        RuleResult result = new QLExpressEngine().execute(ActionDataCompiler.compile(actionData) + "\namount", new LinkedHashMap<String, Object>());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(3.33d, ((Number) result.getResult()).doubleValue(), 0.001d);
    }

    @Test
    public void nestedAmountExpressionExecutesEndToEnd() {
        JSONObject expression = operation("*",
                function("numCeil", operation("/",
                        function("numMax", number("4200"), function("numMin",
                                operation("+",
                                        operation("*",
                                                operation("*",
                                                        function("numMin",
                                                                function("numMax", reference(101, "monthlyRepayment"), reference(102, "usedAmount")),
                                                                number("9000")),
                                                        reference(103, "riskFactor")),
                                                number("0.3")),
                                        operation("*", reference(104, "riskAmount"), number("0.5"))),
                                number("7000"))),
                        number("500"))),
                number("500"));
        JSONObject action = new JSONObject();
        action.put("type", "assign");
        action.put("targetOperand", reference(105, "amount"));
        action.put("valueOperand", expression);
        JSONArray actionData = new JSONArray();
        actionData.add(action);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("monthlyRepayment", 5000);
        params.put("usedAmount", 6000);
        params.put("riskFactor", 1.2);
        params.put("riskAmount", 4000);

        RuleResult result = new QLExpressEngine().execute(
                ActionDataCompiler.compile(actionData) + "\namount", params);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(4500d, ((Number) result.getResult()).doubleValue(), 0d);
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
        JSONArray terms = new JSONArray();
        for (int i = 0; i < operands.length; i++) {
            JSONObject term = new JSONObject();
            if (i > 0) term.put("operator", operator);
            term.put("operand", operands[i]);
            terms.add(term);
        }
        JSONObject node = new JSONObject();
        node.put("kind", "OPERATION");
        node.put("terms", terms);
        return node;
    }

    @Test
    public void foreachBlockExecutesNestedActions() {
        JSONArray actionData = JSON.parseArray("["
                + "{\"type\":\"assign\",\"target\":\"total\",\"value\":\"0\"},"
                + "{\"type\":\"foreach\",\"itemVar\":\"item\",\"listExpr\":\"items\",\"actions\":["
                + "{\"type\":\"assign\",\"target\":\"total\",\"value\":\"total + item\"}"
                + "]}"
                + "]");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("items", Arrays.asList(1, 2, 3));

        RuleResult result = new QLExpressEngine().execute(ActionDataCompiler.compile(actionData) + "\ntotal", params);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(6, ((Number) result.getResult()).intValue());
    }
}
