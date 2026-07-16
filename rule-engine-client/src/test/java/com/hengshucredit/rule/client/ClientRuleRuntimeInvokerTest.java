package com.hengshucredit.rule.client;

import com.hengshucredit.rule.client.cache.CachedRule;
import com.hengshucredit.rule.client.cache.L1MemoryCache;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RuleTerminationSignal;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.dto.RuleTraceFrame;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ClientRuleRuntimeInvokerTest {

    @Test
    public void childCurrentRuleReturnAllowsLocalParentToContinue() {
        L1MemoryCache cache = new L1MemoryCache(10);
        CachedRule root = rule("ROOT", "RULE_SET", "childResult = executeRule(\"CHILD\"); "
                + "setRuntimeValue(\"parentContinued\", true); childResult");
        CachedRule child = rule("CHILD", "FLOW", "_result = {\"decision\": \"CHILD\"}; "
                + "return _result; setRuntimeValue(\"childAfterEnd\", true)");
        cache.put(root);
        cache.put(child);
        RuleEngineClientConfig config = new RuleEngineClientConfig();
        config.setProjectId(1L);
        QLExpressEngine engine = new QLExpressEngine();
        ClientRuleRuntimeInvoker invoker = new ClientRuleRuntimeInvoker(cache, null, engine, config);
        invoker.register(engine.getRunner());
        Map<String, Object> values = new LinkedHashMap<>();

        invoker.enter(root, values);
        try {
            RuleResult result = engine.execute(root.getCompiledScript(), values, true);

            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals("CHILD", ((Map<?, ?>) result.getResult()).get("decision"));
            assertEquals(Boolean.TRUE, values.get("parentContinued"));
            assertFalse(values.containsKey("childAfterEnd"));
        } finally {
            invoker.exit();
        }
    }

    @Test
    public void childAllRulesTerminationStopsLocalRootAndCollectsRootOutputs() {
        L1MemoryCache cache = new L1MemoryCache(10);
        CachedRule root = rule("ROOT", "RULE_SET", "executeRule(\"CHILD\"); "
                + "setRuntimeValue(\"parentAfterChild\", true)");
        root.setOutputScriptNames(Arrays.asList("decision", "notAssigned"));
        CachedRule child = rule("CHILD", "FLOW", "setRuntimeValue(\"decision\", \"STOP\"); "
                + "terminateAllRules(); setRuntimeValue(\"childAfterEnd\", true)");
        cache.put(root);
        cache.put(child);
        RuleEngineClientConfig config = new RuleEngineClientConfig();
        config.setProjectId(1L);
        QLExpressEngine engine = new QLExpressEngine();
        ClientRuleRuntimeInvoker invoker = new ClientRuleRuntimeInvoker(cache, null, engine, config);
        invoker.register(engine.getRunner());
        Map<String, Object> values = new LinkedHashMap<>();

        invoker.enter(root, values);
        try {
            try {
                engine.execute(root.getCompiledScript(), values, true);
                fail("子规则的整体结束节点应终止本地根规则");
            } catch (RuleTerminationSignal expected) {
                Map<String, Object> result = invoker.collectTerminationResult();
                assertEquals("STOP", result.get("decision"));
                assertTrue(result.containsKey("notAssigned"));
                assertEquals(null, result.get("notAssigned"));
                assertFalse(values.containsKey("parentAfterChild"));
                assertFalse(values.containsKey("childAfterEnd"));
            }
        } finally {
            invoker.exit();
        }
    }

    @Test
    public void nestedRuleSharesValuesAndMergesChildTraceIntoRoot() {
        L1MemoryCache cache = new L1MemoryCache(10);
        CachedRule root = rule("ROOT", "SCRIPT", "executeRule(\"CHILD\"); CREDIT_AMOUNT");
        CachedRule child = rule("CHILD", "RULE_SET", "CREDIT_AMOUNT = 3000; CREDIT_AMOUNT");
        cache.put(root);
        cache.put(child);
        RuleEngineClientConfig config = new RuleEngineClientConfig();
        config.setProjectId(1L);
        config.setTraceEnabled(true);
        QLExpressEngine engine = new QLExpressEngine();
        ClientRuleRuntimeInvoker invoker = new ClientRuleRuntimeInvoker(cache, null, engine, config);
        invoker.register(engine.getRunner());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("CREDIT_AMOUNT", 1000);

        invoker.enter(root, values);
        RuleResult result;
        try {
            result = engine.execute(root.getCompiledScript(), values, true);
            invoker.completeRoot(result);
        } finally {
            invoker.exit();
        }

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(3000, ((Number) values.get("CREDIT_AMOUNT")).intValue());
        assertEquals(3000, ((Number) result.getResult()).intValue());
        RuleTraceFrame rootTrace = (RuleTraceFrame) result.getTraces().get(0);
        assertTrue(rootTrace.getTraceId().startsWith("QLP0001"));
        assertEquals("{\"code\":\"ROOT\"}", rootTrace.getModelJson());
        assertEquals(1, rootTrace.getChildren().size());
        RuleTraceFrame childTrace = rootTrace.getChildren().get(0);
        assertTrue(childTrace.getTraceId().startsWith("RSP0001"));
        assertEquals("{\"code\":\"CHILD\"}", childTrace.getModelJson());
        assertNotEquals(rootTrace.getTraceId(), childTrace.getTraceId());
    }

    private CachedRule rule(String code, String modelType, String script) {
        CachedRule rule = new CachedRule();
        rule.setRuleCode(code);
        rule.setProjectCode("P001");
        rule.setModelType(modelType);
        rule.setCompiledScript(script);
        rule.setModelJson("{\"code\":\"" + code + "\"}");
        rule.setVersion(1);
        return rule;
    }
}
