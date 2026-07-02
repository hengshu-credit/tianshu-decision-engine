package com.hengshucredit.rule.core.compiler;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class RuleSetCompilerTest {

    private RuleSetCompiler compiler;
    private QLExpressEngine engine;

    @Before
    public void setUp() {
        compiler = new RuleSetCompiler();
        engine = new QLExpressEngine();
    }

    @Test
    public void testSerialModeUsesPriorityAndStopsAfterFirstHit() {
        CompileResult compiled = compiler.compile("{\n" +
                "  \"executionMode\":\"SERIAL\",\n" +
                "  \"rules\":[\n" +
                "    {\"ruleCode\":\"R0001\",\"ruleName\":\"低优先级\",\"priority\":1,\"conditionRoot\":{\"type\":\"leaf\",\"varCode\":\"score\",\"varType\":\"NUMBER\",\"operator\":\">=\",\"value\":\"60\"},\"actionData\":[{\"type\":\"assign\",\"target\":\"decision\",\"value\":\"\\\"LOW\\\"\"}]},\n" +
                "    {\"ruleCode\":\"R0002\",\"ruleName\":\"高优先级\",\"priority\":9,\"conditionRoot\":{\"type\":\"leaf\",\"varCode\":\"score\",\"varType\":\"NUMBER\",\"operator\":\">=\",\"value\":\"80\"},\"actionData\":[{\"type\":\"assign\",\"target\":\"decision\",\"value\":\"\\\"HIGH\\\"\"}]}\n" +
                "  ]\n" +
                "}");

        assertTrue(compiled.getErrorMessage(), compiled.isSuccess());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("score", 90);

        RuleResult result = engine.execute(compiled.getCompiledScript(), params);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getResult() instanceof List);
        List<?> hits = (List<?>) result.getResult();
        assertEquals(1, hits.size());
        Map<?, ?> hit = (Map<?, ?>) hits.get(0);
        assertEquals("R0002", hit.get("ruleCode"));
        assertEquals("高优先级", hit.get("ruleName"));
        assertEquals(9, ((Number) hit.get("priority")).intValue());
    }

    @Test
    public void testSerialModeReturnsHitListWhenTraceEnabled() {
        CompileResult compiled = compiler.compile("{\n" +
                "  \"executionMode\":\"SERIAL\",\n" +
                "  \"rules\":[\n" +
                "    {\"ruleCode\":\"R0001\",\"ruleName\":\"低优先级\",\"priority\":1,\"conditionRoot\":{\"type\":\"group\",\"operator\":\"AND\",\"children\":[]}},\n" +
                "    {\"ruleCode\":\"R0002\",\"ruleName\":\"高优先级\",\"priority\":5,\"conditionRoot\":{\"type\":\"group\",\"operator\":\"AND\",\"children\":[]}}\n" +
                "  ]\n" +
                "}");

        assertTrue(compiled.getErrorMessage(), compiled.isSuccess());

        RuleResult result = engine.execute(compiled.getCompiledScript(), new LinkedHashMap<String, Object>(), true);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getResult() instanceof List);
        List<?> hits = (List<?>) result.getResult();
        assertEquals(1, hits.size());
        assertEquals("R0002", ((Map<?, ?>) hits.get(0)).get("ruleCode"));
    }

    @Test
    public void testParallelModeReturnsAllHitsByPriorityThenOrder() {
        CompileResult compiled = compiler.compile("{\n" +
                "  \"executionMode\":\"PARALLEL\",\n" +
                "  \"rules\":[\n" +
                "    {\"ruleCode\":\"R0001\",\"ruleName\":\"规则一\",\"priority\":1,\"conditionRoot\":{\"type\":\"leaf\",\"varCode\":\"score\",\"varType\":\"NUMBER\",\"operator\":\">=\",\"value\":\"60\"}},\n" +
                "    {\"ruleCode\":\"R0002\",\"ruleName\":\"规则二\",\"priority\":9,\"conditionRoot\":{\"type\":\"leaf\",\"varCode\":\"score\",\"varType\":\"NUMBER\",\"operator\":\">=\",\"value\":\"80\"}},\n" +
                "    {\"ruleCode\":\"R0003\",\"ruleName\":\"规则三\",\"priority\":9,\"conditionRoot\":{\"type\":\"leaf\",\"varCode\":\"score\",\"varType\":\"NUMBER\",\"operator\":\">=\",\"value\":\"70\"}}\n" +
                "  ]\n" +
                "}");

        assertTrue(compiled.getErrorMessage(), compiled.isSuccess());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("score", 90);

        RuleResult result = engine.execute(compiled.getCompiledScript(), params);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        List<?> hits = (List<?>) result.getResult();
        assertEquals(3, hits.size());
        assertEquals("R0002", ((Map<?, ?>) hits.get(0)).get("ruleCode"));
        assertEquals("R0003", ((Map<?, ?>) hits.get(1)).get("ruleCode"));
        assertEquals("R0001", ((Map<?, ?>) hits.get(2)).get("ruleCode"));
    }

    @Test
    public void testVarContextIsUsedByConditionsAndActions() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(100L, "customerScore");
        varIdMap.put(200L, "riskDecision");
        VarContext context = new VarContext(varIdMap);

        CompileResult compiled = compiler.compile("{\n" +
                "  \"rules\":[{\n" +
                "    \"ruleCode\":\"R0001\",\n" +
                "    \"conditionRoot\":{\"type\":\"leaf\",\"_varId\":100,\"varCode\":\"scoreTmp\",\"varType\":\"NUMBER\",\"operator\":\">=\",\"value\":\"600\"},\n" +
                "    \"actionData\":[{\"type\":\"assign\",\"target\":\"decisionTmp\",\"_targetVarId\":200,\"value\":\"\\\"PASS\\\"\"}]\n" +
                "  }]\n" +
                "}", context);

        assertTrue(compiled.getErrorMessage(), compiled.isSuccess());
        String script = compiled.getCompiledScript();
        assertTrue(script.contains("customerScore"));
        assertTrue(script.contains("riskDecision = \"PASS\""));
        assertFalse(script.contains("scoreTmp"));
        assertFalse(script.contains("decisionTmp"));
    }

    @Test
    public void testDisabledRuleIsSkipped() {
        CompileResult compiled = compiler.compile("{\"rules\":[{\"ruleCode\":\"R0001\",\"enabled\":false},{\"ruleCode\":\"R0002\"}]}");

        assertTrue(compiled.getErrorMessage(), compiled.isSuccess());
        assertFalse(compiled.getCompiledScript().contains("\"R0001\""));
        assertTrue(compiled.getCompiledScript().contains("\"R0002\""));
    }
}
