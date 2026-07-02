package com.hengshucredit.rule.core.compiler;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DecisionTreeCompilerTest {

    private DecisionTreeCompiler compiler;
    private QLExpressEngine engine;

    @Before
    public void setUp() {
        compiler = new DecisionTreeCompiler();
        engine = new QLExpressEngine();
    }

    @Test
    public void executeTreeReturnsResultMapFromActionData() {
        CompileResult result = compiler.compile("{"
                + "\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"decision\",\"type\":\"decision\"},"
                + "{\"id\":\"pass\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"decisionResult\",\"value\":\"\\\"PASS\\\"\"}]},"
                + "{\"id\":\"reject\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"decisionResult\",\"value\":\"\\\"REJECT\\\"\"}]},"
                + "{\"id\":\"end_pass\",\"type\":\"end\"},"
                + "{\"id\":\"end_reject\",\"type\":\"end\"}"
                + "],"
                + "\"edges\":["
                + "{\"source\":\"start\",\"target\":\"decision\"},"
                + "{\"source\":\"decision\",\"target\":\"pass\",\"conditionExpression\":\"score >= 600\"},"
                + "{\"source\":\"decision\",\"target\":\"reject\"},"
                + "{\"source\":\"pass\",\"target\":\"end_pass\"},"
                + "{\"source\":\"reject\",\"target\":\"end_reject\"}"
                + "]"
                + "}");

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<String, Object> params = new HashMap<>();
        params.put("score", 500);

        RuleResult ruleResult = engine.execute(result.getCompiledScript(), params);

        assertTrue(ruleResult.getErrorMessage(), ruleResult.isSuccess());
        assertTrue(ruleResult.getResult() instanceof Map);
        assertEquals("REJECT", ((Map<?, ?>) ruleResult.getResult()).get("decisionResult"));
    }
}
