package com.hengshucredit.rule.core.compiler;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class DecisionFlowCompilerTest {

    private DecisionFlowCompiler compiler;
    private QLExpressEngine engine;

    @Before
    public void setUp() {
        compiler = new DecisionFlowCompiler();
        engine = new QLExpressEngine();
    }

    @Test
    public void returnFiveLandmarksFromAnOperandOnlyAssignmentAfterRenamingTheTarget() {
        String graph = """
                {"nodes":[{"id":"start","type":"start"},{"id":"detect","type":"task","actionData":[
                    {"type":"assign",
                     "targetOperand":{"kind":"REFERENCE","refId":7,"refType":"VARIABLE","code":"oldName"},
                     "valueOperand":{"kind":"REFERENCE","refId":9,"refType":"VARIABLE","code":"predictedPoints"}}
                ]},{"id":"end","type":"end"}],"edges":[{"source":"start","target":"detect"},{"source":"detect","target":"end"}]}
                """;
        VarContext references = new VarContext(Map.of(), Map.of(),
                Map.of("VARIABLE:7", "landmarks", "VARIABLE:9", "predictedPoints"));
        CompileResult compiled = compiler.compile(graph, references);
        assertTrue(compiled.getErrorMessage(), compiled.isSuccess());
        List<List<Integer>> points = List.of(List.of(31, 44), List.of(72, 43), List.of(52, 65),
                List.of(35, 81), List.of(69, 80));
        Map<String, Object> input = new HashMap<>();
        input.put("predictedPoints", points);
        RuleResult executed = engine.execute(compiled.getCompiledScript(), input);
        assertTrue(executed.getErrorMessage(), executed.isSuccess());
        assertEquals(Map.of("landmarks", points), executed.getResult());
    }

    @Test
    public void compileAcyclicFlowSuccessfully() {
        CompileResult result = compiler.compile("{"
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"task\",\"type\":\"task\",\"name\":\"Set result\",\"qlExpressScript\":\"result = 1\"},"
                + "{\"id\":\"end\",\"type\":\"end\"}"
                + "],"
                + "\"edges\":["
                + "{\"source\":\"start\",\"target\":\"task\"},"
                + "{\"source\":\"task\",\"target\":\"end\"}"
                + "]"
                + "}");

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getCompiledScript().contains("result = 1"));
    }

    @Test
    public void rejectFlowWithoutReachableEndNode() {
        CompileResult result = compiler.compile("{"
                + "\"nodes\":[{\"id\":\"start\",\"type\":\"start\"}],"
                + "\"edges\":[]"
                + "}");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("结束节点"));
    }

    @Test
    public void rejectStartNodeWithMultipleOutgoingEdges() {
        CompileResult result = compiler.compile("{"
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"a\",\"type\":\"end\"},"
                + "{\"id\":\"b\",\"type\":\"end\"}],"
                + "\"edges\":["
                + "{\"source\":\"start\",\"target\":\"a\"},"
                + "{\"source\":\"start\",\"target\":\"b\"}]"
                + "}");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("只能有一条出边"));
    }

    @Test
    public void rejectDecisionWithMultipleDefaultEdges() {
        CompileResult result = compiler.compile("{"
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"decision\",\"type\":\"decision\"},"
                + "{\"id\":\"hit\",\"type\":\"end\"},"
                + "{\"id\":\"fallbackA\",\"type\":\"end\"},"
                + "{\"id\":\"fallbackB\",\"type\":\"end\"}],"
                + "\"edges\":["
                + "{\"source\":\"start\",\"target\":\"decision\"},"
                + "{\"source\":\"decision\",\"target\":\"hit\",\"conditionExpression\":\"score > 0\"},"
                + "{\"source\":\"decision\",\"target\":\"fallbackA\"},"
                + "{\"source\":\"decision\",\"target\":\"fallbackB\"}]"
                + "}");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("默认分支"));
    }

    @Test
    public void rejectUnreachableBusinessNode() {
        CompileResult result = compiler.compile("{"
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"end\",\"type\":\"end\"},"
                + "{\"id\":\"orphan\",\"type\":\"task\",\"qlExpressScript\":\"x = 1\"}],"
                + "\"edges\":[{\"source\":\"start\",\"target\":\"end\"}]"
                + "}");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("不可达"));
    }

    @Test
    public void rejectJoinNodeWithSingleIncomingEdge() {
        CompileResult result = compiler.compile("{"
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"join\",\"type\":\"join\"},"
                + "{\"id\":\"end\",\"type\":\"end\"}],"
                + "\"edges\":["
                + "{\"source\":\"start\",\"target\":\"join\"},"
                + "{\"source\":\"join\",\"target\":\"end\"}]"
                + "}");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("至少两条入边"));
    }

    @Test
    public void conditionalBranchesUseExplicitPriorityBeforeArrayOrder() {
        CompileResult result = compiler.compile("{"
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"decision\",\"type\":\"decision\"},"
                + "{\"id\":\"firstInArray\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"decisionResult\",\"value\":\"\\\"ARRAY_FIRST\\\"\"}]},"
                + "{\"id\":\"higherPriority\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"decisionResult\",\"value\":\"\\\"PRIORITY_FIRST\\\"\"}]},"
                + "{\"id\":\"endA\",\"type\":\"end\"},"
                + "{\"id\":\"endB\",\"type\":\"end\"}],"
                + "\"edges\":["
                + "{\"source\":\"start\",\"target\":\"decision\"},"
                + "{\"source\":\"decision\",\"target\":\"firstInArray\",\"conditionExpression\":\"score >= 0\",\"priority\":20},"
                + "{\"source\":\"decision\",\"target\":\"higherPriority\",\"conditionExpression\":\"score >= 0\",\"priority\":10},"
                + "{\"source\":\"firstInArray\",\"target\":\"endA\"},"
                + "{\"source\":\"higherPriority\",\"target\":\"endB\"}]"
                + "}");

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<String, Object> params = new HashMap<>();
        params.put("score", 1);
        RuleResult executed = engine.execute(result.getCompiledScript(), params);
        assertTrue(executed.getErrorMessage(), executed.isSuccess());
        assertEquals("PRIORITY_FIRST", ((Map<?, ?>) executed.getResult()).get("decisionResult"));
    }

    @Test
    public void compileCurrentRuleEndAsReturn() {
        CompileResult result = compiler.compile("{"
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"task\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"decisionResult\",\"value\":\"\\\"PASS\\\"\"}]},"
                + "{\"id\":\"end\",\"type\":\"end\",\"terminationScope\":\"CURRENT_RULE\"}"
                + "],"
                + "\"edges\":["
                + "{\"source\":\"start\",\"target\":\"task\"},"
                + "{\"source\":\"task\",\"target\":\"end\"}"
                + "]"
                + "}");

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getCompiledScript(), result.getCompiledScript().contains("return _result"));
    }

    @Test
    public void compileAllRulesEndAsTerminationFunctionCall() {
        CompileResult result = compiler.compile("{"
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"end\",\"type\":\"end\",\"terminationScope\":\"ALL_RULES\"}"
                + "],"
                + "\"edges\":[{\"source\":\"start\",\"target\":\"end\"}]"
                + "}");

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getCompiledScript(), result.getCompiledScript().contains("terminateAllRules()"));
    }

    @Test
    public void rejectFlowWithCycle() {
        CompileResult result = compiler.compile("{"
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"task\",\"type\":\"task\",\"name\":\"Task\",\"qlExpressScript\":\"result = 1\"},"
                + "{\"id\":\"decision\",\"type\":\"decision\"},"
                + "{\"id\":\"end\",\"type\":\"end\"}"
                + "],"
                + "\"edges\":["
                + "{\"source\":\"start\",\"target\":\"task\"},"
                + "{\"source\":\"task\",\"target\":\"decision\"},"
                + "{\"source\":\"decision\",\"target\":\"task\",\"conditionExpression\":\"score >= 600\"},"
                + "{\"source\":\"decision\",\"target\":\"end\"}"
                + "]"
                + "}");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("循环"));
    }

    @Test
    public void compileNestedConditionConfigWithVarContext() {
        Map<Long, String> vars = new HashMap<>();
        vars.put(1L, "applicant.age");
        vars.put(2L, "policy.maxAge");
        Map<String, String> refs = new HashMap<>();
        refs.put("VARIABLE:1", "applicant.age");
        refs.put("VARIABLE:2", "policy.maxAge");
        VarContext varContext = new VarContext(vars, new HashMap<String, String>(), refs);

        CompileResult result = compiler.compile("{"
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"decision\",\"type\":\"decision\"},"
                + "{\"id\":\"hit\",\"type\":\"task\",\"name\":\"Hit\",\"qlExpressScript\":\"result = 1\"},"
                + "{\"id\":\"miss\",\"type\":\"task\",\"name\":\"Miss\",\"qlExpressScript\":\"result = 0\"},"
                + "{\"id\":\"end\",\"type\":\"end\"}"
                + "],"
                + "\"edges\":["
                + "{\"source\":\"start\",\"target\":\"decision\"},"
                + "{\"source\":\"decision\",\"target\":\"hit\",\"conditionConfig\":{"
                + "\"type\":\"group\",\"op\":\"AND\",\"children\":["
                + "{\"type\":\"leaf\",\"varCode\":\"age\",\"_varId\":1,\"_refType\":\"VARIABLE\",\"operator\":\">=\",\"valueKind\":\"CONST\",\"value\":18,\"varType\":\"NUMBER\"},"
                + "{\"type\":\"group\",\"op\":\"OR\",\"children\":["
                + "{\"type\":\"leaf\",\"varCode\":\"age\",\"_varId\":1,\"_refType\":\"VARIABLE\",\"operator\":\"<=\",\"valueKind\":\"VAR\",\"value\":\"maxAge\",\"_rightVarId\":2,\"_rightRefType\":\"VARIABLE\"},"
                + "{\"type\":\"leaf\",\"varCode\":\"status\",\"operator\":\"==\",\"valueKind\":\"CONST\",\"value\":\"ACTIVE\",\"varType\":\"STRING\"}"
                + "]}"
                + "]}},"
                + "{\"source\":\"decision\",\"target\":\"miss\"},"
                + "{\"source\":\"hit\",\"target\":\"end\"},"
                + "{\"source\":\"miss\",\"target\":\"end\"}"
                + "]"
                + "}", varContext);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getCompiledScript().contains("if ("));
        assertTrue(result.getCompiledScript().contains("applicant.age >= 18"));
        assertTrue(result.getCompiledScript().contains("applicant.age <= policy.maxAge"));
        assertTrue(result.getCompiledScript().contains("status == \"ACTIVE\""));
        assertTrue(result.getCompiledScript().contains("||"));
    }

    @Test
    public void executeFlowReturnsResultMapFromActionData() {
        CompileResult result = compiler.compile("{"
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"decision\",\"type\":\"decision\"},"
                + "{\"id\":\"pass\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"decisionResult\",\"value\":\"\\\"PASS\\\"\"}]},"
                + "{\"id\":\"reject\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"decisionResult\",\"value\":\"\\\"REJECT\\\"\"}]},"
                + "{\"id\":\"end\",\"type\":\"end\"}"
                + "],"
                + "\"edges\":["
                + "{\"source\":\"start\",\"target\":\"decision\"},"
                + "{\"source\":\"decision\",\"target\":\"pass\",\"conditionExpression\":\"score >= 600\"},"
                + "{\"source\":\"decision\",\"target\":\"reject\"},"
                + "{\"source\":\"pass\",\"target\":\"end\"},"
                + "{\"source\":\"reject\",\"target\":\"end\"}"
                + "]"
                + "}");

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<String, Object> params = new HashMap<>();
        params.put("score", 700);

        RuleResult ruleResult = engine.execute(result.getCompiledScript(), params);

        assertTrue(ruleResult.getErrorMessage(), ruleResult.isSuccess());
        assertTrue(ruleResult.getResult() instanceof Map);
        assertEquals("PASS", ((Map<?, ?>) ruleResult.getResult()).get("decisionResult"));

        params.put("score", 500);
        RuleResult defaultResult = engine.execute(result.getCompiledScript(), params);
        assertTrue(defaultResult.getErrorMessage(), defaultResult.isSuccess());
        assertEquals("REJECT", ((Map<?, ?>) defaultResult.getResult()).get("decisionResult"));
    }

    @Test
    public void skipMergeContinuationWhenNoConditionalBranchMatches() {
        CompileResult result = compiler.compile("{"
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"decision\",\"type\":\"decision\"},"
                + "{\"id\":\"high\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"branchResult\",\"value\":\"\\\"HIGH\\\"\"}]},"
                + "{\"id\":\"low\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"branchResult\",\"value\":\"\\\"LOW\\\"\"}]},"
                + "{\"id\":\"join\",\"type\":\"join\"},"
                + "{\"id\":\"after\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"afterResult\",\"value\":\"\\\"AFTER\\\"\"}]},"
                + "{\"id\":\"end\",\"type\":\"end\"}"
                + "],"
                + "\"edges\":["
                + "{\"source\":\"start\",\"target\":\"decision\"},"
                + "{\"source\":\"decision\",\"target\":\"high\",\"conditionExpression\":\"score >= 600\"},"
                + "{\"source\":\"decision\",\"target\":\"low\",\"conditionExpression\":\"score < 300\"},"
                + "{\"source\":\"high\",\"target\":\"join\"},"
                + "{\"source\":\"low\",\"target\":\"join\"},"
                + "{\"source\":\"join\",\"target\":\"after\"},"
                + "{\"source\":\"after\",\"target\":\"end\"}"
                + "]"
                + "}");

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(result.getCompiledScript(), 1,
                occurrences(result.getCompiledScript(), "afterResult = \"AFTER\""));

        Map<?, ?> unmatched = executeResultMap(result, 450);
        assertNull(unmatched.get("branchResult"));
        assertNull(unmatched.get("afterResult"));

        Map<?, ?> high = executeResultMap(result, 700);
        assertEquals("HIGH", high.get("branchResult"));
        assertEquals("AFTER", high.get("afterResult"));

        Map<?, ?> low = executeResultMap(result, 100);
        assertEquals("LOW", low.get("branchResult"));
        assertEquals("AFTER", low.get("afterResult"));
    }

    @Test
    public void nestedUnmatchedDecisionDoesNotReachOuterMerge() {
        CompileResult result = compiler.compile("{"
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"outer\",\"type\":\"decision\"},"
                + "{\"id\":\"inner\",\"type\":\"decision\"},"
                + "{\"id\":\"innerHigh\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"branchResult\",\"value\":\"\\\"INNER_HIGH\\\"\"}]},"
                + "{\"id\":\"innerLow\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"branchResult\",\"value\":\"\\\"INNER_LOW\\\"\"}]},"
                + "{\"id\":\"outerAlternative\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"branchResult\",\"value\":\"\\\"OUTER_ALTERNATIVE\\\"\"}]},"
                + "{\"id\":\"innerJoin\",\"type\":\"join\"},"
                + "{\"id\":\"outerJoin\",\"type\":\"join\"},"
                + "{\"id\":\"after\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"afterResult\",\"value\":\"\\\"AFTER\\\"\"}]},"
                + "{\"id\":\"end\",\"type\":\"end\"}"
                + "],"
                + "\"edges\":["
                + "{\"source\":\"start\",\"target\":\"outer\"},"
                + "{\"source\":\"outer\",\"target\":\"inner\",\"conditionExpression\":\"route == 1\"},"
                + "{\"source\":\"outer\",\"target\":\"outerAlternative\",\"conditionExpression\":\"route == 2\"},"
                + "{\"source\":\"inner\",\"target\":\"innerHigh\",\"conditionExpression\":\"score >= 600\"},"
                + "{\"source\":\"inner\",\"target\":\"innerLow\",\"conditionExpression\":\"score < 300\"},"
                + "{\"source\":\"innerHigh\",\"target\":\"innerJoin\"},"
                + "{\"source\":\"innerLow\",\"target\":\"innerJoin\"},"
                + "{\"source\":\"innerJoin\",\"target\":\"outerJoin\"},"
                + "{\"source\":\"outerAlternative\",\"target\":\"outerJoin\"},"
                + "{\"source\":\"outerJoin\",\"target\":\"after\"},"
                + "{\"source\":\"after\",\"target\":\"end\"}"
                + "]"
                + "}");

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(result.getCompiledScript(), 1,
                occurrences(result.getCompiledScript(), "afterResult = \"AFTER\""));

        Map<?, ?> innerUnmatched = executeResultMap(result, params("route", 1, "score", 450));
        assertNull(innerUnmatched.get("branchResult"));
        assertNull(innerUnmatched.get("afterResult"));

        Map<?, ?> innerMatched = executeResultMap(result, params("route", 1, "score", 700));
        assertEquals("INNER_HIGH", innerMatched.get("branchResult"));
        assertEquals("AFTER", innerMatched.get("afterResult"));

        Map<?, ?> outerAlternative = executeResultMap(result, params("route", 2, "score", 450));
        assertEquals("OUTER_ALTERNATIVE", outerAlternative.get("branchResult"));
        assertEquals("AFTER", outerAlternative.get("afterResult"));

        Map<?, ?> outerUnmatched = executeResultMap(result, params("route", 0, "score", 700));
        assertNull(outerUnmatched.get("branchResult"));
        assertNull(outerUnmatched.get("afterResult"));
    }

    @Test
    public void internalMatchFlagDoesNotCollideWithManagedScriptName() {
        Map<String, String> refs = new HashMap<>();
        refs.put("VARIABLE:1", "_tsDecisionMatched0");
        VarContext varContext = new VarContext(new HashMap<Long, String>(),
                new HashMap<String, String>(), refs);
        CompileResult result = compiler.compile("{"
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"decision\",\"type\":\"decision\"},"
                + "{\"id\":\"hit\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"branchResult\",\"value\":\"\\\"HIT\\\"\"}]},"
                + "{\"id\":\"miss\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"branchResult\",\"value\":\"\\\"MISS\\\"\"}]},"
                + "{\"id\":\"join\",\"type\":\"join\"},"
                + "{\"id\":\"after\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"afterResult\",\"value\":\"\\\"AFTER\\\"\"}]},"
                + "{\"id\":\"end\",\"type\":\"end\"}"
                + "],"
                + "\"edges\":["
                + "{\"source\":\"start\",\"target\":\"decision\"},"
                + "{\"source\":\"decision\",\"target\":\"hit\",\"conditionExpression\":\"score >= 600\",\"leftVarId\":1,\"leftRefType\":\"VARIABLE\"},"
                + "{\"source\":\"decision\",\"target\":\"miss\",\"conditionExpression\":\"score < 300\",\"leftVarId\":1,\"leftRefType\":\"VARIABLE\"},"
                + "{\"source\":\"hit\",\"target\":\"join\"},"
                + "{\"source\":\"miss\",\"target\":\"join\"},"
                + "{\"source\":\"join\",\"target\":\"after\"},"
                + "{\"source\":\"after\",\"target\":\"end\"}"
                + "]"
                + "}", varContext);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getCompiledScript(), result.getCompiledScript().contains("_tsDecisionMatched0 >= 600"));
        assertTrue(result.getCompiledScript(), result.getCompiledScript().contains("_tsDecisionMatched1 = false"));
        Map<?, ?> matched = executeResultMap(result, params("_tsDecisionMatched0", 700));
        assertEquals("HIT", matched.get("branchResult"));
        assertEquals("AFTER", matched.get("afterResult"));
    }

    private Map<?, ?> executeResultMap(CompileResult compileResult, int score) {
        Map<String, Object> params = new HashMap<>();
        params.put("score", score);
        return executeResultMap(compileResult, params);
    }

    private Map<?, ?> executeResultMap(CompileResult compileResult, Map<String, Object> params) {
        RuleResult ruleResult = engine.execute(compileResult.getCompiledScript(), params);
        assertTrue(ruleResult.getErrorMessage(), ruleResult.isSuccess());
        assertTrue(ruleResult.getResult() instanceof Map);
        return (Map<?, ?>) ruleResult.getResult();
    }

    private static Map<String, Object> params(Object... values) {
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            params.put(String.valueOf(values[i]), values[i + 1]);
        }
        return params;
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
