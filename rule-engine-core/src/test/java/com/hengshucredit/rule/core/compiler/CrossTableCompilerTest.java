package com.hengshucredit.rule.core.compiler;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class CrossTableCompilerTest {

    private CrossTableCompiler compiler;
    private QLExpressEngine engine;

    @Before
    public void setUp() {
        compiler = new CrossTableCompiler();
        engine = new QLExpressEngine();
    }

    @Test
    public void defaultActionDoesNotOverrideMatchedCell() {
        CompileResult compileResult = compiler.compile(modelJsonWithDefaultAction());

        assertTrue(compileResult.getErrorMessage(), compileResult.isSuccess());

        Map<String, Object> context = new HashMap<>();
        context.put("age", 30);
        context.put("score", 700);
        RuleResult result = engine.execute(compileResult.getCompiledScript(), context);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getResult() instanceof Map);
        Map<?, ?> resultMap = (Map<?, ?>) result.getResult();
        assertEquals("APPROVE", resultMap.get("decision"));
    }

    @Test
    public void defaultActionRunsWhenNoCellMatches() {
        CompileResult compileResult = compiler.compile(modelJsonWithDefaultAction());

        assertTrue(compileResult.getErrorMessage(), compileResult.isSuccess());

        Map<String, Object> context = new HashMap<>();
        context.put("age", 70);
        context.put("score", 700);
        RuleResult result = engine.execute(compileResult.getCompiledScript(), context);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getResult() instanceof Map);
        Map<?, ?> resultMap = (Map<?, ?>) result.getResult();
        assertEquals("REVIEW", resultMap.get("decision"));
    }

    @Test
    public void simpleMatrixFormatReturnsResultMap() {
        CompileResult compileResult = compiler.compile("{"
                + "\"rowVar\":{\"varCode\":\"taxpayerType\",\"varType\":\"STRING\"},"
                + "\"colVar\":{\"varCode\":\"goodsCategory\",\"varType\":\"STRING\"},"
                + "\"resultVar\":{\"varCode\":\"taxRate\",\"varType\":\"NUMBER\"},"
                + "\"rowHeaders\":[\"一般纳税人\",\"小规模纳税人\"],"
                + "\"colHeaders\":[\"货物\",\"服务\"],"
                + "\"cells\":[[\"0.13\",\"0.06\"],[\"0.03\",\"0.03\"]]"
                + "}");

        assertTrue(compileResult.getErrorMessage(), compileResult.isSuccess());

        Map<String, Object> context = new HashMap<>();
        context.put("taxpayerType", "一般纳税人");
        context.put("goodsCategory", "货物");
        RuleResult result = engine.execute(compileResult.getCompiledScript(), context);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getResult() instanceof Map);
        Map<?, ?> resultMap = (Map<?, ?>) result.getResult();
        assertEquals(0.13, ((Number) resultMap.get("taxRate")).doubleValue(), 0.000001);
    }

    private String modelJsonWithDefaultAction() {
        return "{"
                + "\"rowDefs\":[{\"varCode\":\"age\",\"varType\":\"NUMBER\",\"conditions\":["
                + "{\"operator\":\">=\",\"value\":\"18\"},{\"operator\":\"<\",\"value\":\"60\"}"
                + "]}],"
                + "\"colDefs\":[{\"varCode\":\"score\",\"varType\":\"NUMBER\",\"conditions\":["
                + "{\"operator\":\">=\",\"value\":\"600\"}"
                + "]}],"
                + "\"cells\":[{\"row\":0,\"col\":0,\"action\":{\"varCode\":\"decision\",\"varType\":\"STRING\",\"value\":\"APPROVE\"}}],"
                + "\"defaultAction\":{\"varCode\":\"decision\",\"varType\":\"STRING\",\"value\":\"REVIEW\"}"
                + "}";
    }
}
