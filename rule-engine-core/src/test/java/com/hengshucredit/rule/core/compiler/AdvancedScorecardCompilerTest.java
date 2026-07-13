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
                + "\"resultVar\":{\"operand\":{\"kind\":\"REFERENCE\",\"code\":\"totalScore\",\"value\":\"totalScore\",\"valueType\":\"NUMBER\"}},"
                + "\"dimensionGroups\":[{\"dimensions\":[{\"rules\":[{\"conditions\":[{"
                + "\"leftOperand\":{\"kind\":\"REFERENCE\",\"code\":\"creditScore\",\"value\":\"creditScore\",\"valueType\":\"NUMBER\"},"
                + "\"operator\":\">=\",\"rightOperand\":{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}}],\"score\":20}]}]}],"
                + "\"thresholds\":[{\"min\":120,\"max\":200,\"resultOperand\":{\"kind\":\"LITERAL\",\"value\":\"PASS\",\"valueType\":\"STRING\"}}]}"
        );

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getCompiledScript().contains("creditScore >= 600"));
        assertTrue(result.getCompiledScript().contains("riskLevel = \"PASS\""));
    }
}
