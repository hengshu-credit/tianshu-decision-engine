package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ActionDataCompilerTest {

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
