package com.bjjw.rule.core.compiler;

import com.bjjw.rule.core.engine.QLExpressEngine;
import com.bjjw.rule.model.dto.RuleResult;
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
