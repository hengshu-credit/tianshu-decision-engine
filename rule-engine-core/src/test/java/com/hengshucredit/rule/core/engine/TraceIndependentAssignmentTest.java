package com.hengshucredit.rule.core.engine;

import com.hengshucredit.rule.core.compiler.ScorecardCompiler;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class TraceIndependentAssignmentTest {
    @Test public void scorecardAssignmentsReachCallerAndListenerWithoutCollectingTraces() {
        String model = "{\"initialScore\":0,\"resultVar\":{\"varCode\":\"score\"},\"scoreItems\":[{\"condVar\":\"age\",\"condOperator\":\">=\",\"condValue\":\"18\",\"condVarType\":\"NUMBER\",\"score\":5}]}";
        var compiled = new ScorecardCompiler().compile(model);
        assertTrue(compiled.getErrorMessage(), compiled.isSuccess());
        for (boolean trace : new boolean[]{false, true}) {
            var context = new LinkedHashMap<String, Object>(Map.of("age", 25));
            var writes = new LinkedHashMap<String, Object>();
            var request = new RequestContext(); request.bind(writes::put);
            var engine = new QLExpressEngine();
            var result = engine.execute(engine.prepare(compiled.getCompiledScript()), context, trace, request);
            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals(5.0, ((Number) ((Map<?, ?>) result.getResult()).get("score")).doubleValue(), 0);
            assertEquals("赋值不能依赖trace=" + trace, Double.valueOf(5), context.get("score"));
            assertEquals(context.get("score"), writes.get("score"));
            if (!trace) assertTrue(result.getTraces() == null || result.getTraces().isEmpty());
        }
    }

    @Test public void explicitRuntimeWritesAndOrdinaryAssignmentsKeepActualExecutionOrder() {
        for (boolean trace : new boolean[]{false, true}) {
            var values = new LinkedHashMap<String, Object>();
            var writes = new LinkedHashMap<String, Object>();
            var request = new RequestContext(); request.bind(writes::put);
            var engine = new QLExpressEngine();
            var result = engine.execute(engine.prepare("value = 1; setRuntimeValue('value', 7); value += 2; value++; return value;"), values, trace, request);
            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals(10, ((Number) result.getResult()).intValue());
            assertEquals(10, ((Number) values.get("value")).intValue());
            assertEquals(10, ((Number) writes.get("value")).intValue());
        }
    }

    @Test public void readonlyInputKeepsLocalWritesAndBridgeUpdatesReadable() {
        for (boolean trace : new boolean[]{false, true}) {
            Map<String, Object> input = Map.of("age", 25);
            var request = new RequestContext(); var writes = new LinkedHashMap<String, Object>(); request.bind(writes::put);
            var engine = new QLExpressEngine();
            var result = engine.execute(engine.prepare("score = age; setRuntimeValue('score', 4); score += 1; return score;"), input, trace, request);
            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals(5, ((Number) result.getResult()).intValue());
            assertEquals(5, ((Number) writes.get("score")).intValue());
            assertEquals(Map.of("age", 25), input);
        }
    }

    @Test public void unusedBranchDoesNotReadLazyInputAndDeclaredLocalDoesNotOverwriteCaller() {
        AtomicInteger reads = new AtomicInteger();
        Map<String, Object> values = new LinkedHashMap<>(Map.of("age", 25)) {
            @Override public Object get(Object name) { if ("expensive".equals(name)) reads.incrementAndGet(); return super.get(name); }
        };
        var result = new QLExpressEngine().execute("if (false) { result = expensive; }\nif (true) { int age = 99; localSeen = age; }\nresult = age; return result;", values, false);
        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(0, reads.get()); assertEquals(25, result.getResult()); assertEquals(25, values.get("age"));
        assertEquals(25, values.get("result"));
    }

    @Test public void loopLocalsStayLocalAndObjectAliasesKeepTheOriginalReference() {
        for (boolean trace : new boolean[]{false, true}) {
            Map<String, Object> object = new LinkedHashMap<>(Map.of("score", 0));
            Map<String, Object> values = new LinkedHashMap<>(Map.of("inputObject", object));
            var result = new QLExpressEngine().execute("score = 0; for (int i = 0; i < 3; i++) { score += i; } alias = inputObject; alias.score = score; return alias.score;", values, trace);
            assertTrue(result.getErrorMessage(), result.isSuccess()); assertEquals(3, result.getResult());
            assertEquals(3, values.get("score")); assertFalse(values.containsKey("i"));
            assertSame(object, values.get("alias")); assertEquals(3, object.get("score"));
        }
    }

    @Test public void childEngineWritesAreImmediatelyVisibleToTheParentWithoutTraceReplay() {
        for (boolean trace : new boolean[]{false, true}) {
            var engine = new QLExpressEngine(); var request = new RequestContext();
            var values = new LinkedHashMap<String, Object>(); var writes = new LinkedHashMap<String, Object>(); request.bind(writes::put);
            engine.getRunner().addFunction("child", (Runnable) () -> {
                var child = engine.execute(engine.prepare("score = 7; return score;"), values, trace, request);
                assertTrue(child.getErrorMessage(), child.isSuccess());
            });
            var result = engine.execute(engine.prepare("score = 1; child(); score += 2; return score;"), values, trace, request);
            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals(9, result.getResult()); assertEquals(9, values.get("score")); assertEquals(9, writes.get("score"));
        }
    }

    @Test public void bothTraceModesStillRejectConstantMutation() {
        for (boolean trace : new boolean[]{false, true}) {
            var request = new RequestContext(); request.registerConstant("LIMIT", 100);
            var values = new LinkedHashMap<String, Object>(Map.of("LIMIT", 100));
            var engine = new QLExpressEngine();
            var result = engine.execute(engine.prepare("LIMIT = 0; return LIMIT;"), values, trace, request);
            assertFalse(result.isSuccess()); assertEquals(100, values.get("LIMIT"));
        }
    }
}
