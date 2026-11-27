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

    @Test
    public void sourceStatusSegmentsCompileThroughUnifiedConditionCompiler() {
        String json = "{"
                + "\"resultVar\":{\"varCode\":\"rate\",\"varType\":\"NUMBER\"},"
                + "\"rowDimensions\":[{\"operand\":{\"kind\":\"REFERENCE\",\"refId\":12,\"refType\":\"VARIABLE\",\"code\":\"apiScore\",\"valueType\":\"NUMBER\"},\"varType\":\"NUMBER\",\"segments\":["
                + "{\"operator\":\"source_cache_hit\"}]}],"
                + "\"colDimensions\":[{\"varCode\":\"enabled\",\"varType\":\"BOOLEAN\",\"segments\":["
                + "{\"operator\":\"is_true\"}]}],"
                + "\"cells\":[[\"1\"]]}";

        CompileResult result = compiler.compile(json);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getCompiledScript().contains("sourceStatus(\"VARIABLE\", \"12\", \"CACHE_STATE\", \"HIT\")"));
        assertTrue(result.getCompiledScript().contains("enabled == true"));
    }

    @Test
    public void rangeBoundaryControlsWhetherEndpointsAreIncluded() {
        assertTrue(matchesRangeBoundary("[)", 0));
        assertFalse(matchesRangeBoundary("[)", 2000));

        assertFalse(matchesRangeBoundary("()", 0));
        assertTrue(matchesRangeBoundary("()", 1000));
        assertFalse(matchesRangeBoundary("()", 2000));

        assertTrue(matchesRangeBoundary("[]", 0));
        assertTrue(matchesRangeBoundary("[]", 2000));

        assertFalse(matchesRangeBoundary("(]", 0));
        assertTrue(matchesRangeBoundary("(]", 2000));
    }

    @Test
    public void missingOrInvalidRangeBoundaryDefaultsToLeftClosedRightOpen() {
        assertTrue(matchesRangeBoundary(null, 0));
        assertFalse(matchesRangeBoundary(null, 2000));
        assertTrue(matchesRangeBoundary("invalid", 0));
        assertFalse(matchesRangeBoundary("invalid", 2000));
    }

    @Test
    public void rejectsOverlappingRangeSegmentsInOneDimension() {
        CompileResult result = compiler.compile(rangeDimensionModel("0", "250", "200", "350"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("重叠"));
    }

    @Test
    public void rejectsRangeSegmentWithReversedBounds() {
        CompileResult result = compiler.compile(rangeDimensionModel("350", "250", null, null));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("下界"));
    }

    @Test
    public void acceptsAdjacentLeftClosedRightOpenRangeSegments() {
        CompileResult result = compiler.compile(rangeDimensionModel("0", "250", "250", "350"));

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<String, Object> context = new HashMap<>();
        context.put("amount", 250);
        context.put("enabled", 1);
        RuleResult execution = engine.execute(result.getCompiledScript(), context);

        assertTrue(execution.getErrorMessage(), execution.isSuccess());
        assertEquals(2, ((Number) ((Map<?, ?>) execution.getResult()).get("rate")).intValue());
    }

    @Test
    public void rejectsReversedStaticDateRangeSegments() {
        CompileResult result = compiler.compile(dateRangeDimensionModel("2024-03-01", "2024-02-01", null, null));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("下界"));
    }

    @Test
    public void rejectsOverlappingStaticDateRangeSegments() {
        CompileResult result = compiler.compile(dateRangeDimensionModel(
                "2024-01-01", "2024-03-01", "2024-02-01", "2024-04-01"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("重叠"));
    }

    @Test
    public void acceptsAdjacentStaticDateRangesAtLeftClosedRightOpenBoundary() {
        CompileResult result = compiler.compile(dateRangeDimensionModel(
                "2024-01-01", "2024-02-01", "2024-02-01", "2024-03-01"));

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<String, Object> context = new HashMap<>();
        context.put("applicationDate", "2024-02-01");
        context.put("enabled", 1);
        RuleResult execution = engine.execute(result.getCompiledScript(), context);

        assertTrue(execution.getErrorMessage(), execution.isSuccess());
        assertEquals(2, ((Number) ((Map<?, ?>) execution.getResult()).get("rate")).intValue());
    }

    private boolean matchesRangeBoundary(String rangeBoundary, int amount) {
        String boundaryJson = rangeBoundary == null ? "" : ",\"rangeBoundary\":\"" + rangeBoundary + "\"";
        String json = "{"
                + "\"resultVar\":{\"varCode\":\"rate\",\"varType\":\"NUMBER\"},"
                + "\"rowDimensions\":[{\"varCode\":\"amount\",\"varType\":\"NUMBER\",\"segments\":["
                + "{\"operator\":\"range\",\"min\":\"0\",\"max\":\"2000\"" + boundaryJson + "}]}],"
                + "\"colDimensions\":[{\"varCode\":\"enabled\",\"varType\":\"NUMBER\",\"segments\":["
                + "{\"operator\":\"==\",\"value\":\"1\"}]}],"
                + "\"cells\":[[\"3000\"]]}";
        CompileResult compileResult = compiler.compile(json);
        assertTrue(compileResult.getErrorMessage(), compileResult.isSuccess());

        Map<String, Object> context = new HashMap<>();
        context.put("amount", amount);
        context.put("enabled", 1);
        RuleResult execution = engine.execute(compileResult.getCompiledScript(), context);
        assertTrue(execution.getErrorMessage(), execution.isSuccess());
        return ((Map<?, ?>) execution.getResult()).get("rate") != null;
    }

    @Test
    public void emptyDesignerCellsSkipAssignmentAndKeepExplicitValues() {
        for (String type : java.util.List.of("NUMBER", "BOOLEAN", "STRING")) {
            String filled = switch (type) {
                case "NUMBER" -> "0";
                case "BOOLEAN" -> "false";
                default -> "ok";
            };
            var model = com.alibaba.fastjson.JSON.parseObject(modelJson());
            model.getJSONObject("resultVar").put("varType", type);
            var dimension = model.getJSONArray("colDimensions").getJSONObject(0);
            dimension.put("segments", java.util.List.of(Map.of("operator", "==", "value", "1"),
                    Map.of("operator", "==", "value", "2")));
            model.put("cells", java.util.List.of(java.util.List.of(
                    Map.of("kind", "LITERAL", "value", "", "valueType", type),
                    Map.of("kind", "LITERAL", "value", filled, "valueType", type))));
            CompileResult compiled = compiler.compile(model.toJSONString());
            assertTrue(compiled.getErrorMessage(), compiled.isSuccess());
            for (int score : new int[]{1, 2}) {
                var result = engine.execute(compiled.getCompiledScript(), new HashMap<>(Map.of("age", 30, "score", score)));
                assertTrue(result.getErrorMessage(), result.isSuccess());
                Map<?, ?> outputs = (Map<?, ?>) result.getResult();
                assertTrue(outputs.containsKey("rate"));
                if (score == 1) assertNull(outputs.get("rate"));
                else if ("NUMBER".equals(type)) assertEquals(0, ((Number) outputs.get("rate")).intValue());
                else if ("BOOLEAN".equals(type)) assertEquals(Boolean.FALSE, outputs.get("rate"));
                else assertEquals(filled, outputs.get("rate"));
            }
        }
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

    private String rangeDimensionModel(String firstMin, String firstMax, String secondMin, String secondMax) {
        String secondSegment = secondMin == null ? "" : ",{\"operator\":\"range\",\"min\":\""
                + secondMin + "\",\"max\":\"" + secondMax + "\",\"rangeBoundary\":\"[)\"}";
        String cells = secondMin == null ? "[[\"1\"]]" : "[[\"1\"],[\"2\"]]";
        return "{"
                + "\"resultVar\":{\"varCode\":\"rate\",\"varType\":\"NUMBER\"},"
                + "\"rowDimensions\":[{\"varCode\":\"amount\",\"varType\":\"NUMBER\",\"segments\":["
                + "{\"operator\":\"range\",\"min\":\"" + firstMin + "\",\"max\":\"" + firstMax + "\",\"rangeBoundary\":\"[)\"}"
                + secondSegment + "]}],"
                + "\"colDimensions\":[{\"varCode\":\"enabled\",\"varType\":\"NUMBER\",\"segments\":[{\"operator\":\"==\",\"value\":\"1\"}]}],"
                + "\"cells\":" + cells + "}";
    }

    private String dateRangeDimensionModel(String firstMin, String firstMax, String secondMin, String secondMax) {
        String secondSegment = secondMin == null ? "" : "," + dateRangeSegment(secondMin, secondMax);
        String cells = secondMin == null ? "[[\"1\"]]" : "[[\"1\"],[\"2\"]]";
        return "{"
                + "\"resultVar\":{\"varCode\":\"rate\",\"varType\":\"NUMBER\"},"
                + "\"rowDimensions\":[{\"varCode\":\"applicationDate\",\"varType\":\"DATE\",\"segments\":["
                + dateRangeSegment(firstMin, firstMax) + secondSegment + "]}],"
                + "\"colDimensions\":[{\"varCode\":\"enabled\",\"varType\":\"NUMBER\",\"segments\":[{\"operator\":\"==\",\"value\":\"1\"}]}],"
                + "\"cells\":" + cells + "}";
    }

    private String dateRangeSegment(String min, String max) {
        return "{\"operator\":\"range\",\"rangeBoundary\":\"[)\","
                + "\"minOperand\":{\"kind\":\"LITERAL\",\"value\":\"" + min + "\",\"valueType\":\"DATE\"},"
                + "\"maxOperand\":{\"kind\":\"LITERAL\",\"value\":\"" + max + "\",\"valueType\":\"DATE\"}}";
    }
}
