package com.hengshucredit.rule.core.engine;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class RuntimeContextBridgeTest {

    @After
    public void clearContext() {
        RuntimeContextBridge.clear();
    }

    @Test
    public void installedSnapshotIsIsolatedAndRestoresCallerContext() throws Exception {
        List<Map<String, Object>> callerEvents = new ArrayList<>();
        RuntimeContextBridge.setRuleContext(singletonMap("code", "PARENT"),
                Collections.singletonList("parent-condition"));
        RuntimeContextBridge.replaceSourceStates(singletonState("VARIABLE:1", "OUTCOME", "SUCCESS"));
        RuntimeContextBridge.bindTraceEventListener(callerEvents::add);
        RuntimeContextBridge.ContextSnapshot snapshot = RuntimeContextBridge.captureContext();

        List<Map<String, Object>> workerEvents = new ArrayList<>();
        AtomicReference<Map<String, Object>> workerRuleAfterScope = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try (RuntimeContextBridge.ContextScope ignored =
                         RuntimeContextBridge.installContext(snapshot, workerEvents::add)) {
                assertEquals("PARENT", RuntimeContextBridge.currentRule().get("code"));
                assertEquals("SUCCESS", RuntimeContextBridge.currentSourceStates()
                        .get("VARIABLE:1").get("OUTCOME"));
                RuntimeContextBridge.addTraceEvent(singletonMap("type", "WORKER"));
                RuntimeContextBridge.setRuleContext(singletonMap("code", "WORKER"),
                        Collections.emptyList());
            }
            workerRuleAfterScope.set(RuntimeContextBridge.currentRule());
        });
        worker.start();
        worker.join(2000);

        RuntimeContextBridge.addTraceEvent(singletonMap("type", "CALLER"));

        assertEquals(Collections.emptyMap(), workerRuleAfterScope.get());
        assertEquals("PARENT", RuntimeContextBridge.currentRule().get("code"));
        assertEquals("SUCCESS", RuntimeContextBridge.currentSourceStates()
                .get("VARIABLE:1").get("OUTCOME"));
        assertEquals("WORKER", workerEvents.get(0).get("type"));
        assertEquals("CALLER", callerEvents.get(0).get("type"));
    }

    @Test
    public void callerRunsScopeRestoresExistingTraceListener() {
        List<Map<String, Object>> callerEvents = new ArrayList<>();
        List<Map<String, Object>> scopedEvents = new ArrayList<>();
        RuntimeContextBridge.setRuleContext(singletonMap("code", "PARENT"), Collections.emptyList());
        RuntimeContextBridge.bindTraceEventListener(callerEvents::add);
        RuntimeContextBridge.ContextSnapshot snapshot = RuntimeContextBridge.captureContext();

        try (RuntimeContextBridge.ContextScope ignored =
                     RuntimeContextBridge.installContext(snapshot, scopedEvents::add)) {
            RuntimeContextBridge.addTraceEvent(singletonMap("type", "SCOPED"));
        }
        RuntimeContextBridge.addTraceEvent(singletonMap("type", "CALLER"));

        assertEquals("SCOPED", scopedEvents.get(0).get("type"));
        assertEquals("CALLER", callerEvents.get(0).get("type"));
        assertEquals("PARENT", RuntimeContextBridge.currentRule().get("code"));
    }

    @Test
    public void callerRunsWorkerDoesNotLeakConstantsOrWritesIntoParent() {
        Map<String, Object> writes = new LinkedHashMap<>();
        RuntimeContextBridge.bind(writes::put);
        RuntimeContextBridge.registerConstant("PARENT", 1);
        try (RuntimeContextBridge.ContextScope ignored =
                     RuntimeContextBridge.installContext(RuntimeContextBridge.captureContext(), event -> {})) {
            RuntimeContextBridge.registerConstant("WORKER", 2);
            RuntimeContextBridge.setValue("value", 3);
        }
        org.junit.Assert.assertTrue(writes.isEmpty());
        RuntimeContextBridge.setValue("WORKER", 4);
        assertEquals(4, writes.get("WORKER"));
        org.junit.Assert.assertThrows(IllegalStateException.class,
                () -> RuntimeContextBridge.setValue("PARENT", 5));
    }

    @Test
    public void nestedWriteScopesReplayOnlyTheirWritesAndResetBetweenRequests() {
        RequestContext request = new RequestContext();
        Map<String, Object> captured = new LinkedHashMap<>();
        request.bind(captured::put);
        int outer = request.beginRuntimeWriteScope();
        int empty = request.beginRuntimeWriteScope();
        request.replayRuntimeWrites(empty, captured);
        request.endRuntimeWriteScope();
        request.setValue("before", 1);
        int inner = request.beginRuntimeWriteScope();
        request.setValue("inside", 2);
        Map<String, Object> innerValues = new LinkedHashMap<>();
        request.replayRuntimeWrites(inner, innerValues);
        request.endRuntimeWriteScope();
        assertEquals(Map.of("inside", 2), innerValues);
        request.setValue("after", 3);
        Map<String, Object> outerValues = new LinkedHashMap<>();
        request.replayRuntimeWrites(outer, outerValues);
        request.endRuntimeWriteScope();
        assertEquals(Map.of("before", 1, "inside", 2, "after", 3), outerValues);

        int next = request.beginRuntimeWriteScope();
        Map<String, Object> nextValues = new LinkedHashMap<>();
        request.replayRuntimeWrites(next, nextValues);
        request.endRuntimeWriteScope();
        assertEquals(Collections.emptyMap(), nextValues);
    }

    @Test
    public void firstConstantRegistrationRemainsReadOnlyAndProtectsMutableValues() {
        RequestContext request = new RequestContext();
        assertEquals(Collections.emptySet(), request.constantNames());
        List<Integer> limit = new ArrayList<>(List.of(1, 2));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("LIMIT", limit);
        request.registerConstant("LIMIT", limit);
        request.registerConstant("OTHER", 3);
        values.put("OTHER", 3);
        org.junit.Assert.assertThrows(UnsupportedOperationException.class,
                () -> request.constantNames().remove("LIMIT"));
        limit.set(0, 9);
        org.junit.Assert.assertThrows(IllegalStateException.class,
                () -> request.assertConstantsUnchanged(values));
        assertEquals(List.of(1, 2), values.get("LIMIT"));
        org.junit.Assert.assertThrows(IllegalStateException.class,
                () -> request.setValue("OTHER", 4));
    }

    private Map<String, Object> singletonMap(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private Map<String, Map<String, Object>> singletonState(
            String key, String dimension, Object value) {
        Map<String, Map<String, Object>> states = new LinkedHashMap<>();
        states.put(key, singletonMap(dimension, value));
        return states;
    }
}
