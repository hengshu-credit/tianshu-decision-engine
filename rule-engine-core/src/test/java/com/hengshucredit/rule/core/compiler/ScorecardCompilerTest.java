package com.hengshucredit.rule.core.compiler;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScorecardCompilerTest {

    private ScorecardCompiler compiler;
    private QLExpressEngine engine;

    @Before
    public void setUp() {
        compiler = new ScorecardCompiler();
        engine = new QLExpressEngine();
    }

    @Test
    public void test结构化条件优先于旧Condition文本并使用VarContext() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(32L, "applicant.version");
        varIdMap.put(33L, "testResult");
        VarContext ctx = new VarContext(varIdMap);

        CompileResult result = compiler.compile("{\n" +
                "  \"initialScore\": 2,\n" +
                "  \"scoreItems\": [{\n" +
                "    \"condVar\": \"version\",\n" +
                "    \"condOperator\": \">=\",\n" +
                "    \"condValue\": \"10\",\n" +
                "    \"condVarType\": \"NUMBER\",\n" +
                "    \"condition\": \"oldVersion >= 10\",\n" +
                "    \"score\": 1,\n" +
                "    \"weight\": 1,\n" +
                "    \"_varId\": 32\n" +
                "  }],\n" +
                "  \"resultVar\": {\"varCode\":\"resultCode\", \"_varId\":33, \"varType\":\"NUMBER\"}\n" +
                "}", ctx);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        String script = result.getCompiledScript();
        assertTrue(script.contains("applicant.version >= 10"));
        assertTrue(script.contains("testResult = testResult + 1.0"));
        assertFalse(script.contains("oldVersion"));
        assertFalse(script.contains("resultCode"));
    }

    @Test
    public void test字符串条件值会按变量类型加引号并可执行() {
        CompileResult compileResult = compiler.compile("{\n" +
                "  \"initialScore\": 0,\n" +
                "  \"scoreItems\": [{\n" +
                "    \"condVar\": \"customerType\",\n" +
                "    \"condOperator\": \"==\",\n" +
                "    \"condValue\": \"VIP\",\n" +
                "    \"condVarType\": \"STRING\",\n" +
                "    \"score\": 10,\n" +
                "    \"weight\": 1\n" +
                "  }],\n" +
                "  \"resultVar\": {\"varCode\":\"score\", \"varType\":\"NUMBER\"}\n" +
                "}");

        assertTrue(compileResult.getErrorMessage(), compileResult.isSuccess());
        assertTrue(compileResult.getCompiledScript().contains("customerType == \"VIP\""));

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("customerType", "VIP");
        RuleResult result = engine.execute(compileResult.getCompiledScript(), context);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertEquals(10.0, ((Number) output.get("score")).doubleValue(), 0.000001);
    }
}
