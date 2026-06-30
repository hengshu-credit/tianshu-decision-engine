package com.bjjw.rule.core.compiler;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DecisionFlowCompilerTest {

    private DecisionFlowCompiler compiler;

    @Before
    public void setUp() {
        compiler = new DecisionFlowCompiler();
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
}
