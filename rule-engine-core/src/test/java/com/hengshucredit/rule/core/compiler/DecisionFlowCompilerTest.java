package com.hengshucredit.rule.core.compiler;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
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
    public void compileFlowWithoutEndNodeSuccessfully() {
        CompileResult result = compiler.compile("{"
                + "\"nodes\":[{\"id\":\"start\",\"type\":\"start\"}],"
                + "\"edges\":[]"
                + "}");

        assertTrue(result.getErrorMessage(), result.isSuccess());
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
        VarContext varContext = new VarContext(vars);

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
                + "{\"type\":\"leaf\",\"varCode\":\"age\",\"_varId\":1,\"operator\":\">=\",\"valueKind\":\"CONST\",\"value\":18,\"varType\":\"NUMBER\"},"
                + "{\"type\":\"group\",\"op\":\"OR\",\"children\":["
                + "{\"type\":\"leaf\",\"varCode\":\"age\",\"_varId\":1,\"operator\":\"<=\",\"valueKind\":\"VAR\",\"value\":\"maxAge\",\"_rightVarId\":2},"
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
    }
}
