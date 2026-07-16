package com.hengshucredit.rule.core.engine;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class QLExpressEngineTest {

    public static class TerminationDelegate {
        public Object terminateAllRules() {
            throw new RuleTerminationSignal();
        }
    }

    @Test(expected = RuleTerminationSignal.class)
    public void controlledTerminationEscapesMapExecution() {
        QLExpressEngine engine = new QLExpressEngine();
        engine.getRunner().addFunctionOfServiceMethod(
                "terminateAllRules", new TerminationDelegate(), "terminateAllRules", new Class<?>[]{});

        engine.execute("terminateAllRules()", new HashMap<>());
    }

    @Test(expected = RuleTerminationSignal.class)
    public void controlledTerminationEscapesObjectExecution() {
        QLExpressEngine engine = new QLExpressEngine();
        engine.getRunner().addFunctionOfServiceMethod(
                "terminateAllRules", new TerminationDelegate(), "terminateAllRules", new Class<?>[]{});

        engine.execute("terminateAllRules()", new Object(), false);
    }

    @Test
    public void executeKeepsCompleteOversizedTrace() {
        StringBuilder script = new StringBuilder();
        script.append("x = 0\n");
        for (int i = 0; i < 1200; i++) {
            script.append("if (a >= 0) { x = x + 1 }\n");
        }
        script.append("x");

        Map<String, Object> context = new HashMap<>();
        context.put("a", 1);

        RuleResult result = new QLExpressEngine().execute(script.toString(), context, true);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(1200, ((Number) result.getResult()).intValue());
        assertNotNull(result.getTraces());
        assertEquals(1, result.getTraces().size());
        String traceJson = JSON.toJSONString(result.getTraces());
        assertTrue(traceJson.length() > 100000);
        assertFalse(traceJson.contains("TRACE_TRUNCATED"));
    }

    @Test
    public void defaultEngineRejectsJavaMemberAccess() {
        RuleResult result = new QLExpressEngine().execute("\"abc\".getClass().getName()", new HashMap<>());

        assertFalse(result.isSuccess());
    }

    @Test
    public void runtimeValueFunctionNotifiesRequestScopedBridge() {
        Map<String, Object> captured = new LinkedHashMap<>();
        RuntimeContextBridge.bind(captured::put);
        try {
            RuleResult result = new QLExpressEngine().execute(
                    "setRuntimeValue(\"age\", 22); age = 22; age", new HashMap<>());

            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals(Integer.valueOf(22), captured.get("age"));
            assertEquals(22, ((Number) result.getResult()).intValue());
        } finally {
            RuntimeContextBridge.clear();
        }
    }

    @Test
    public void tracedDirectAssignmentWritesLatestValueBackToSharedContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("CREDIT_AMOUNT", 1000);

        RuleResult result = new QLExpressEngine().execute(
                "CREDIT_AMOUNT = 3000; CREDIT_AMOUNT", context, true);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(3000, ((Number) context.get("CREDIT_AMOUNT")).intValue());
    }

    @Test
    public void directScriptAssignmentCannotChangeConstant() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("CREDIT_AMOUNT", 5000);
        RuntimeContextBridge.registerConstant("CREDIT_AMOUNT", 5000);
        try {
            RuleResult result = new QLExpressEngine().execute(
                    "CREDIT_AMOUNT = 3000; CREDIT_AMOUNT", context, true);

            assertFalse(result.isSuccess());
            assertEquals(5000, ((Number) context.get("CREDIT_AMOUNT")).intValue());
        } finally {
            RuntimeContextBridge.clear();
        }
    }
}
