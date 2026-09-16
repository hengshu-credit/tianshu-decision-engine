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
