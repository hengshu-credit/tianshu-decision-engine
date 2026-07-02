package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

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
}
