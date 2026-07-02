package com.hengshucredit.rule.core.compiler;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class AdvancedCrossTableCompilerTest {

    private AdvancedCrossTableCompiler compiler;
    private QLExpressEngine engine;

    @Before
    public void setUp() {
        compiler = new AdvancedCrossTableCompiler();
        engine = new QLExpressEngine();
    }

    @Test
    public void compileReturnsResultMapWhenCellMatches() {
        CompileResult compileResult = compiler.compile(modelJson());

        assertTrue(compileResult.getErrorMessage(), compileResult.isSuccess());
        String script = compileResult.getCompiledScript();
        assertTrue(script.contains("rate = null"));
        assertTrue(script.contains("_result"));

        Map<String, Object> context = new HashMap<>();
        context.put("age", 30);
        context.put("score", 700);
        RuleResult result = engine.execute(script, context);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getResult() instanceof Map);
        Map<?, ?> resultMap = (Map<?, ?>) result.getResult();
        assertEquals(0.1, ((Number) resultMap.get("rate")).doubleValue(), 0.000001);
    }

    @Test
    public void compileReturnsNullResultMapWhenNoCellMatches() {
        CompileResult compileResult = compiler.compile(modelJson());

        assertTrue(compileResult.getErrorMessage(), compileResult.isSuccess());

        Map<String, Object> context = new HashMap<>();
        context.put("age", 70);
        context.put("score", 700);
        RuleResult result = engine.execute(compileResult.getCompiledScript(), context);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getResult() instanceof Map);
        Map<?, ?> resultMap = (Map<?, ?>) result.getResult();
        assertTrue(resultMap.containsKey("rate"));
        assertNull(resultMap.get("rate"));
    }

    private String modelJson() {
        return "{"
                + "\"resultVar\":{\"varCode\":\"rate\",\"varType\":\"DOUBLE\"},"
                + "\"rowDimensions\":[{\"varCode\":\"age\",\"varType\":\"NUMBER\",\"segments\":["
                + "{\"operator\":\"range\",\"min\":\"18\",\"max\":\"60\"}"
                + "]}],"
                + "\"colDimensions\":[{\"varCode\":\"score\",\"varType\":\"NUMBER\",\"segments\":["
                + "{\"operator\":\">=\",\"value\":\"600\"}"
                + "]}],"
                + "\"cells\":[[\"0.1\"]]"
                + "}";
    }
}
