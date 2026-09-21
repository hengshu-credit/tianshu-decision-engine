package com.hengshucredit.rule.core.engine;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class QLExpressEngineTest {

    @Test
    public void preparedScriptIsReusedAndBoundToItsEngine() {
        QLExpressEngine engine = new QLExpressEngine();
        QLExpressEngine.PreparedScript prepared = engine.prepare("return age + 1;");
        org.junit.Assert.assertSame(prepared, engine.prepare("return age + 1;"));
        assertEquals(19, engine.execute(prepared, Map.of("age", 18), false).getResult());
        assertEquals(31, engine.execute(prepared, Map.of("age", 30), false).getResult());
        assertFalse(new QLExpressEngine().execute(prepared, Map.of("age", 18), false).isSuccess());
    }

    @Test
    public void preparationChurnRetainsFrequentlyUsedScriptsAndEvictsColdScripts() {
        QLExpressEngine engine = new QLExpressEngine();
        Map<String, QLExpressEngine.PreparedScript> hot = new LinkedHashMap<>();
        for (int i = 0; i < 32; i++) {
            String script = "return " + i + "; // hot";
            hot.put(script, engine.prepare(script));
        }
        QLExpressEngine.PreparedScript cold = engine.prepare("return -1; // cold");
        int reloads = 0;
        for (int i = 0; i < 2048; i++) {
            for (Map.Entry<String, QLExpressEngine.PreparedScript> entry : hot.entrySet()) {
                QLExpressEngine.PreparedScript current = engine.prepare(entry.getKey());
                if (current != entry.getValue()) {
                    reloads++;
                    entry.setValue(current);
                }
            }
            engine.prepare("return " + i + "; // cold");
        }
        assertEquals("hot scripts should not be reparsed under cold-script churn", 0, reloads);
        org.junit.Assert.assertNotSame(cold, engine.prepare("return -1; // cold"));
        assertEquals(-1, engine.execute(cold, Map.of(), false).getResult());
    }

    @Test
    public void clearingPreparationCacheRepreparesWithoutInvalidatingInFlightHandles() {
        QLExpressEngine engine = new QLExpressEngine();
        QLExpressEngine.PreparedScript prepared = engine.prepare("return 7;");
        engine.clearPreparedScripts();
        org.junit.Assert.assertNotSame(prepared, engine.prepare("return 7;"));
        assertEquals(7, engine.execute(prepared, Map.of(), false).getResult());
    }

    @Test
    public void concurrentPreparationAndClearingKeepHandlesExecutable() throws Exception {
        QLExpressEngine engine = new QLExpressEngine();
        for (int i = 0; i < 1024; i++) engine.prepare("return " + i + ";");
        ExecutorService workers = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int worker = 0; worker < 4; worker++) {
                final int workerId = worker;
                results.add(workers.submit(() -> {
                    start.await();
                    for (int i = 0; i < 512; i++) {
                        int expected = 1024 + workerId * 512 + i;
                        QLExpressEngine.PreparedScript prepared = engine.prepare("return " + expected + ";");
                        RuleResult result = engine.execute(prepared, Map.of(), false, new RequestContext());
                        assertTrue(result.getErrorMessage(), result.isSuccess());
                        assertEquals(expected, result.getResult());
                    }
                    return null;
                }));
            }
            results.add(workers.submit(() -> {
                start.await();
                for (int i = 0; i < 64; i++) engine.clearPreparedScripts();
                return null;
            }));
            start.countDown();
            for (Future<?> result : results) result.get(10, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void failedExecutionDoesNotReplayWritesIntoNextExecution() {
        QLExpressEngine engine = new QLExpressEngine();
        engine.getRunner().addFunction("fail", (Runnable) () -> { throw new IllegalStateException("test failure"); });
        RequestContext request = new RequestContext();
        Map<String, Object> captured = new LinkedHashMap<>();
        request.bind(captured::put);
        RuleResult failed = engine.execute(engine.prepare("setRuntimeValue('old', 1); fail();"),
                new LinkedHashMap<>(), false, request);
        assertFalse(failed.isSuccess());
        assertEquals(1, captured.get("old"));

        Map<String, Object> next = new LinkedHashMap<>();
        RuleResult result = engine.execute(engine.prepare("return 7;"), next, false, request);
        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(7, result.getResult());
        assertTrue("previous writes must be cleared even after failure", next.isEmpty());
    }

    @Test
    public void preparationRejectsConstantWritesWithoutRequestContext() {
        QLExpressEngine engine = new QLExpressEngine();
        for (String script : new String[] { "LIMIT = 1", "LIMIT += 1", "++LIMIT", "LIMIT--",
                "LIMIT.value = 1", "LIMIT[0] = 1" }) {
            org.junit.Assert.assertThrows(script, IllegalStateException.class,
                    () -> engine.prepare(script, Set.of("LIMIT")));
        }
        engine.prepare("// LIMIT = 1\nreturn \"LIMIT++\";", Set.of("LIMIT"));
        engine.prepare("return LIMIT == 1;", Set.of("LIMIT"));
    }

    @Test
    public void preparedExecutionStillRejectsDynamicConstantWritesWithoutTrace() {
        QLExpressEngine engine = new QLExpressEngine();
        QLExpressEngine.PreparedScript prepared = engine.prepare("setRuntimeValue(path, 1)");
        RuntimeContextBridge.registerConstant("LIMIT", 5000);
        try {
            RuleResult result = engine.execute(prepared, Map.of("path", "LIMIT", "LIMIT", 5000), false);
            assertFalse(result.isSuccess());
        } finally {
            RuntimeContextBridge.clear();
        }
    }

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
    public void sourceStatusFunctionIsRegisteredInDefaultEngine() {
        RuntimeContextBridge.putSourceState("VARIABLE", 9L, "CACHE_STATE", "MISS");
        try {
            RuleResult result = new QLExpressEngine().execute(
                    "sourceStatus(\"VARIABLE\", \"9\", \"CACHE_STATE\", \"MISS\")", new HashMap<>());

            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals(Boolean.TRUE, result.getResult());
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
