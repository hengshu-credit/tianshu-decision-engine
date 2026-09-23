package com.hengshucredit.rule.core.compiler;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AdvancedScorecardCompilerTest {

    private AdvancedScorecardCompiler compiler;
    private QLExpressEngine engine;

    @Before
    public void setUp() {
        compiler = new AdvancedScorecardCompiler();
        engine = new QLExpressEngine();
    }

    @Test
    public void executeAdvancedScorecardReturnsScoreAndLevel() {
        CompileResult result = compiler.compile("{"
                + "\"initialScore\":100,"
                + "\"resultVar\":{\"varCode\":\"totalScore\",\"varType\":\"NUMBER\"},"
                + "\"dimensionGroups\":[{\"groupLabel\":\"credit\",\"dimensions\":[{"
                + "\"varLabel\":\"score\","
                + "\"rules\":["
                + "{\"conditions\":[{\"varCode\":\"creditScore\",\"operator\":\">=\",\"value\":\"600\"}],\"score\":20},"
                + "{\"conditions\":[{\"varCode\":\"creditScore\",\"operator\":\"<\",\"value\":\"600\"}],\"score\":-30}"
                + "]"
                + "}]}],"
                + "\"thresholds\":["
                + "{\"min\":120,\"max\":200,\"result\":\"PASS\",\"resultVar\":\"riskLevel\"},"
                + "{\"min\":0,\"max\":120,\"result\":\"REJECT\",\"resultVar\":\"riskLevel\"}"
                + "]"
                + "}");

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<String, Object> params = new HashMap<>();
        params.put("creditScore", 650);

        RuleResult ruleResult = engine.execute(result.getCompiledScript(), params);

        assertTrue(ruleResult.getErrorMessage(), ruleResult.isSuccess());
        assertTrue(ruleResult.getResult() instanceof Map);
        Map<?, ?> resultMap = (Map<?, ?>) ruleResult.getResult();
        assertEquals(120.0, ((Number) resultMap.get("totalScore")).doubleValue(), 0.000001);
        assertEquals("PASS", resultMap.get("riskLevel"));
    }

    @Test
    public void compilesUnifiedOperandsForConditionsAndThresholdResults() {
        CompileResult result = compiler.compile("{"
                + "\"initialScore\":100,"
                + "\"resultVar\":{\"operand\":{\"kind\":\"REFERENCE\",\"refId\":1,\"refType\":\"VARIABLE\",\"code\":\"totalScore\",\"value\":\"totalScore\",\"valueType\":\"NUMBER\"}},"
                + "\"dimensionGroups\":[{\"dimensions\":[{\"rules\":[{\"conditions\":[{"
                + "\"leftOperand\":{\"kind\":\"REFERENCE\",\"refId\":2,\"refType\":\"VARIABLE\",\"code\":\"creditScore\",\"value\":\"creditScore\",\"valueType\":\"NUMBER\"},"
                + "\"operator\":\">=\",\"rightOperand\":{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}}],\"score\":20}]}]}],"
                + "\"thresholds\":[{\"min\":120,\"max\":200,\"resultOperand\":{\"kind\":\"LITERAL\",\"value\":\"PASS\",\"valueType\":\"STRING\"}}]}"
        );

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getCompiledScript().contains("creditScore >= 600"));
        assertTrue(result.getCompiledScript().contains("riskLevel = \"PASS\""));
    }

    @Test
    public void thresholdsUseLeftClosedRightOpenBoundaryAtSharedEndpoint() {
        CompileResult compileResult = compiler.compile(thresholdModel("0", "250", "250", "350"));

        assertTrue(compileResult.getErrorMessage(), compileResult.isSuccess());
        RuleResult result = engine.execute(compileResult.getCompiledScript(), new HashMap<String, Object>());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals("HIGH", ((Map<?, ?>) result.getResult()).get("riskLevel"));
    }

    @Test
    public void rejectsOverlappingThresholds() {
        CompileResult result = compiler.compile(thresholdModel("0", "250", "200", "350"));

        assertTrue(result.getErrorMessage(), !result.isSuccess());
        assertTrue(result.getErrorMessage().contains("重叠"));
    }

    @Test
    public void appliesGroupAndDimensionWeightsToMatchedScore() {
        CompileResult result = compiler.compile("{"
                + "\"initialScore\":0,"
                + "\"resultVar\":{\"varCode\":\"totalScore\",\"varType\":\"NUMBER\"},"
                + "\"dimensionGroups\":[{\"groupLabel\":\"credit\",\"weight\":0.5,\"dimensions\":[{"
                + "\"varLabel\":\"score\",\"weight\":1,\"rules\":[{\"conditions\":[],\"score\":10}]}]}]"
                + "}");

        assertTrue(result.getErrorMessage(), result.isSuccess());
        RuleResult execution = engine.execute(result.getCompiledScript(), new HashMap<String, Object>());

        assertTrue(execution.getErrorMessage(), execution.isSuccess());
        assertEquals(5.0, ((Number) ((Map<?, ?>) execution.getResult()).get("totalScore")).doubleValue(), 0.000001);
    }

    private String thresholdModel(String firstMin, String firstMax, String secondMin, String secondMax) {
        return "{"
                + "\"initialScore\":250,\"resultVar\":{\"varCode\":\"totalScore\",\"varType\":\"NUMBER\"},"
                + "\"thresholds\":[{\"min\":" + firstMin + ",\"max\":" + firstMax + ",\"result\":\"LOW\",\"resultVar\":\"riskLevel\"},"
                + "{\"min\":" + secondMin + ",\"max\":" + secondMax + ",\"result\":\"HIGH\",\"resultVar\":\"riskLevel\"}]}";
    }
}
