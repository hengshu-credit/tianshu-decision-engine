package com.hengshucredit.rule.core.compiler;

import com.alibaba.qlexpress4.runtime.trace.ExpressionTrace;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.ArrayList;
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

    @After
    public void tearDown() {
        RuntimeContextBridge.clear();
    }

    @Test
    public void serialModeRecordsOnlyReachedRuleEvaluationAndRuleSetOverallHit() {
        CompileResult compiled = compiler.compile("{\"executionMode\":\"SERIAL\",\"rules\":["
                + "{\"ruleCode\":\"R-LOW\",\"ruleName\":\"低优先级\",\"priority\":1,\"conditionRoot\":{\"type\":\"group\",\"op\":\"AND\",\"children\":[]}},"
                + "{\"ruleCode\":\"R-HIGH\",\"ruleName\":\"高优先级\",\"priority\":9,\"conditionRoot\":{\"type\":\"group\",\"op\":\"AND\",\"children\":[]}}]}" );
        List<Map<String, Object>> events = new ArrayList<>();
        RuntimeContextBridge.bindTraceEventListener(events::add);

        RuleResult result = engine.execute(compiled.getCompiledScript(), new LinkedHashMap<String, Object>());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(2, events.size());
        assertEquals("RULE_SET_ITEM", events.get(0).get("type"));
        assertEquals("R-HIGH", events.get(0).get("ruleCode"));
        assertEquals(Boolean.TRUE, events.get(0).get("hit"));
        assertEquals("RULE_SET_SUMMARY", events.get(1).get("type"));
        assertEquals(Boolean.TRUE, events.get(1).get("hit"));
    }

    @Test
    public void testNestedAndOrConditionGroupsKeepBackendExecutionSemantics() {
        CompileResult compiled = compiler.compile("{"
                + "\"executionMode\":\"PARALLEL\","
                + "\"rules\":[{\"ruleCode\":\"R-NESTED\",\"conditionRoot\":{"
                + "\"type\":\"group\",\"op\":\"AND\",\"children\":["
                + "{\"type\":\"leaf\",\"varCode\":\"age\",\"varType\":\"NUMBER\",\"operator\":\">=\",\"value\":\"18\"},"
                + "{\"type\":\"group\",\"op\":\"OR\",\"children\":["
                + "{\"type\":\"leaf\",\"varCode\":\"score\",\"varType\":\"NUMBER\",\"operator\":\">=\",\"value\":\"60\"},"
                + "{\"type\":\"leaf\",\"varCode\":\"vip\",\"varType\":\"BOOLEAN\",\"operator\":\"==\",\"value\":\"true\"}"
                + "]}]}}]}");

        assertTrue(compiled.getErrorMessage(), compiled.isSuccess());
        Map<String, Object> hitParams = new LinkedHashMap<>();
        hitParams.put("age", 20);
        hitParams.put("score", 50);
        hitParams.put("vip", true);
        RuleResult hitResult = engine.execute(compiled.getCompiledScript(), hitParams, true);
        assertTrue(hitResult.getErrorMessage(), hitResult.isSuccess());
        assertEquals(1, ((List<?>) hitResult.getResult()).size());
        assertNotNull(hitResult.getTraces());
        List<?> expressionRoots = (List<?>) hitResult.getTraces().get(0);
        ExpressionTrace nestedAnd = findOperatorWithDirectChild(expressionRoots, "&&", "||");
        assertNotNull("后端追踪应保留 AND 下嵌套 OR 的条件层级", nestedAnd);
        assertTrue(nestedAnd.isEvaluated());

        Map<String, Object> missParams = new LinkedHashMap<>();
        missParams.put("age", 20);
        missParams.put("score", 50);
        missParams.put("vip", false);
        RuleResult missResult = engine.execute(compiled.getCompiledScript(), missParams);
        assertTrue(missResult.getErrorMessage(), missResult.isSuccess());
        assertTrue(((List<?>) missResult.getResult()).isEmpty());
    }

    private ExpressionTrace findOperatorWithDirectChild(List<?> traces, String operator, String childOperator) {
        if (traces == null) return null;
        for (Object item : traces) {
            if (!(item instanceof ExpressionTrace)) continue;
            ExpressionTrace trace = (ExpressionTrace) item;
            if (operator.equals(trace.getToken()) && trace.getChildren() != null) {
                for (ExpressionTrace child : trace.getChildren()) {
                    if (childOperator.equals(child.getToken())) return trace;
                }
            }
            ExpressionTrace nested = findOperatorWithDirectChild(trace.getChildren(), operator, childOperator);
            if (nested != null) return nested;
        }
        return null;
    }

    @Test
    public void testVarContextIsUsedByConditionsAndActions() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(100L, "customerScore");
        varIdMap.put(200L, "riskDecision");
        Map<String, String> refMap = new LinkedHashMap<>();
        refMap.put("VARIABLE:100", "customerScore");
        refMap.put("VARIABLE:200", "riskDecision");
        VarContext context = new VarContext(varIdMap, new LinkedHashMap<String, String>(), refMap);

        CompileResult compiled = compiler.compile("{\n" +
                "  \"rules\":[{\n" +
                "    \"ruleCode\":\"R0001\",\n" +
                "    \"conditionRoot\":{\"type\":\"leaf\",\"_varId\":100,\"_refType\":\"VARIABLE\",\"varCode\":\"scoreTmp\",\"varType\":\"NUMBER\",\"operator\":\">=\",\"value\":\"600\"},\n" +
                "    \"actionData\":[{\"type\":\"assign\",\"target\":\"decisionTmp\",\"_targetVarId\":200,\"_targetRefType\":\"VARIABLE\",\"value\":\"\\\"PASS\\\"\"}]\n" +
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

    @Test
    public void testOptionalListResultVarReceivesHitsWithoutChangingTopLevelResult() {
        Map<String, String> refIdMap = new LinkedHashMap<>();
        refIdMap.put("VARIABLE:12", "hitRules");
        VarContext context = new VarContext(new LinkedHashMap<Long, String>(),
                new LinkedHashMap<String, String>(), refIdMap);
        CompileResult compiled = compiler.compile("{"
                + "\"executionMode\":\"PARALLEL\","
                + "\"resultVar\":{\"varCode\":\"legacyHits\",\"varType\":\"LIST\",\"_varId\":12,\"_refType\":\"VARIABLE\","
                + "\"operand\":{\"kind\":\"REFERENCE\",\"code\":\"legacyHits\",\"valueType\":\"LIST\",\"refId\":12,\"refType\":\"VARIABLE\",\"resolved\":true}},"
                + "\"rules\":[{\"ruleCode\":\"R0001\",\"conditionRoot\":{\"type\":\"group\",\"operator\":\"AND\",\"children\":[]}}]"
                + "}", context);

        assertTrue(compiled.getErrorMessage(), compiled.isSuccess());
        assertTrue(compiled.getCompiledScript().contains("hitRules = _ruleSetHits"));
        Map<String, Object> captured = new LinkedHashMap<>();
        RuntimeContextBridge.bind(captured::put);
        try {
            RuleResult result = engine.execute(compiled.getCompiledScript(), new LinkedHashMap<String, Object>());

            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertTrue(result.getResult() instanceof List);
            assertEquals(1, ((List<?>) result.getResult()).size());
            assertTrue(captured.get("hitRules") instanceof List);
            assertEquals(1, ((List<?>) captured.get("hitRules")).size());
        } finally {
            RuntimeContextBridge.clear();
        }
    }

    @Test
    public void testDataObjectListResultVarReceivesEmptyHitList() {
        Map<String, String> refIdMap = new LinkedHashMap<>();
        refIdMap.put("DATA_OBJECT:33", "request.hitRules");
        VarContext context = new VarContext(new LinkedHashMap<Long, String>(), new LinkedHashMap<String, String>(), refIdMap);
        CompileResult compiled = compiler.compile("{"
                + "\"resultVar\":{\"varCode\":\"request.hitRules\",\"varType\":\"LIST\",\"_varId\":33,\"_refType\":\"DATA_OBJECT\","
                + "\"operand\":{\"kind\":\"PATH\",\"value\":\"request.hitRules\",\"code\":\"request.hitRules\",\"valueType\":\"LIST\",\"refId\":33,\"refType\":\"DATA_OBJECT\",\"resolved\":true}},"
                + "\"rules\":[{\"ruleCode\":\"R0001\",\"conditionRoot\":{\"type\":\"leaf\",\"varCode\":\"score\",\"varType\":\"NUMBER\",\"operator\":\">\",\"value\":\"100\"}}]"
                + "}", context);

        assertTrue(compiled.getErrorMessage(), compiled.isSuccess());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("hitRules", null);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("request", request);
        params.put("score", 10);
        RuleResult result = engine.execute(compiled.getCompiledScript(), params);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getResult() instanceof List);
        assertTrue(((List<?>) result.getResult()).isEmpty());
        assertTrue(request.get("hitRules") instanceof List);
        assertTrue(((List<?>) request.get("hitRules")).isEmpty());
    }

    @Test
    public void testResultVarRejectsNonListType() {
        CompileResult compiled = compiler.compile("{"
                + "\"resultVar\":{\"operand\":{\"kind\":\"REFERENCE\",\"code\":\"score\",\"valueType\":\"NUMBER\",\"refId\":12,\"refType\":\"VARIABLE\",\"resolved\":true}},"
                + "\"rules\":[]"
                + "}");

        assertFalse(compiled.isSuccess());
        assertTrue(compiled.getErrorMessage().contains("LIST"));
    }

    @Test
    public void testResultVarRejectsMissingTypedReferenceEvenWhenLegacyMappingsMatch() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(12L, "legacyHitRules");
        Map<String, String> varCodeMap = new LinkedHashMap<>();
        varCodeMap.put("legacyHits", "legacyHitRules");
        VarContext context = new VarContext(varIdMap, varCodeMap, new LinkedHashMap<String, String>());

        CompileResult compiled = compiler.compile("{"
                + "\"resultVar\":{\"operand\":{\"kind\":\"REFERENCE\",\"code\":\"legacyHits\",\"valueType\":\"LIST\",\"refId\":12,\"refType\":\"VARIABLE\",\"resolved\":true}},"
                + "\"rules\":[]"
                + "}", context);

        assertFalse(compiled.isSuccess());
        assertTrue(compiled.getErrorMessage().contains("VARIABLE:12"));
    }

    @Test
    public void testResultVarDoesNotReuseSameIdFromAnotherReferenceType() {
        Map<Long, String> varIdMap = new LinkedHashMap<>();
        varIdMap.put(33L, "wrongVariable");
        Map<String, String> refIdMap = new LinkedHashMap<>();
        refIdMap.put("VARIABLE:33", "wrongVariable");
        VarContext context = new VarContext(varIdMap, new LinkedHashMap<String, String>(), refIdMap);

        CompileResult compiled = compiler.compile("{"
                + "\"resultVar\":{\"operand\":{\"kind\":\"PATH\",\"code\":\"request.hitRules\",\"valueType\":\"LIST\",\"refId\":33,\"refType\":\"DATA_OBJECT\",\"resolved\":true}},"
                + "\"rules\":[]"
                + "}", context);

        assertFalse(compiled.isSuccess());
        assertTrue(compiled.getErrorMessage().contains("DATA_OBJECT:33"));
    }
}
