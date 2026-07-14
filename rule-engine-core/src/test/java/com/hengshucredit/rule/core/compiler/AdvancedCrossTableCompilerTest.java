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

    @Test
    public void compilesUnifiedOperandsForDimensionsSegmentsAndCells() {
        String json = "{"
                + "\"resultVar\":{\"operand\":{\"kind\":\"PATH\",\"value\":\"decision.rate\",\"code\":\"decision.rate\"},\"varType\":\"DOUBLE\"},"
                + "\"rowDimensions\":[{\"operand\":{\"kind\":\"REFERENCE\",\"refId\":1,\"refType\":\"VARIABLE\",\"code\":\"age\",\"value\":\"age\",\"valueType\":\"NUMBER\"},\"varType\":\"NUMBER\",\"segments\":["
                + "{\"operator\":\">=\",\"valueOperand\":{\"kind\":\"LITERAL\",\"value\":\"18\",\"valueType\":\"NUMBER\"}}]}],"
                + "\"colDimensions\":[{\"operand\":{\"kind\":\"REFERENCE\",\"refId\":2,\"refType\":\"VARIABLE\",\"code\":\"limit\",\"value\":\"limit\",\"valueType\":\"NUMBER\"},\"varType\":\"NUMBER\",\"segments\":["
                + "{\"operator\":\">\",\"valueOperand\":{\"kind\":\"REFERENCE\",\"refId\":3,\"refType\":\"VARIABLE\",\"code\":\"minLimit\",\"value\":\"minLimit\",\"valueType\":\"NUMBER\"}}]}],"
                + "\"cells\":[[{\"kind\":\"LITERAL\",\"value\":\"0.2\",\"valueType\":\"NUMBER\"}]]}"
                ;

        CompileResult result = compiler.compile(json);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getCompiledScript().contains("age >= 18"));
        assertTrue(result.getCompiledScript().contains("limit > minLimit"));
        assertTrue(result.getCompiledScript().contains("decision.rate = 0.2"));
    }

    @Test
    public void numericDimensionsCoerceLiteralSegmentOperandsToNumbers() {
        String json = "{"
                + "\"resultVar\":{\"varCode\":\"rate\",\"varType\":\"NUMBER\"},"
                + "\"rowDimensions\":[{\"varCode\":\"credit_limit\",\"varType\":\"NUMBER\",\"segments\":["
                + "{\"operator\":\"range\",\"minOperand\":{\"kind\":\"LITERAL\",\"value\":\"0\",\"valueType\":\"STRING\"},"
                + "\"maxOperand\":{\"kind\":\"LITERAL\",\"value\":\"2000\",\"valueType\":\"STRING\"}}]}],"
                + "\"colDimensions\":[{\"varCode\":\"available_credit_limit\",\"varType\":\"NUMBER\",\"segments\":["
                + "{\"operator\":\">=\",\"valueOperand\":{\"kind\":\"LITERAL\",\"value\":\"500\",\"valueType\":\"STRING\"}}]}],"
                + "\"cells\":[[{\"kind\":\"LITERAL\",\"value\":\"3000\",\"valueType\":\"STRING\"}]]}";

        CompileResult result = compiler.compile(json);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getCompiledScript().contains("credit_limit >= 0"));
        assertTrue(result.getCompiledScript().contains("credit_limit < 2000"));
        assertTrue(result.getCompiledScript().contains("available_credit_limit >= 500"));
        assertFalse(result.getCompiledScript().contains("credit_limit >= \"0\""));
        assertTrue(result.getCompiledScript().contains("rate = 3000"));
        assertFalse(result.getCompiledScript().contains("rate = \"3000\""));

        Map<String, Object> context = new HashMap<>();
        context.put("credit_limit", 1000.0);
        context.put("available_credit_limit", 750.0);
        RuleResult execution = engine.execute(result.getCompiledScript(), context);
        assertTrue(execution.getErrorMessage(), execution.isSuccess());
        assertEquals(3000, ((Number) ((Map<?, ?>) execution.getResult()).get("rate")).intValue());
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
