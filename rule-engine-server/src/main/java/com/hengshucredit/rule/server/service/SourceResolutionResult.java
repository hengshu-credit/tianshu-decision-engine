package com.hengshucredit.rule.server.service;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SourceResolutionResult {

    private final int order;
    private final String scriptName;
    private final boolean resolved;
    private final Object value;
    private final Map<String, Map<String, Object>> sourceStates;
    private final List<Map<String, Object>> traceEvents;
    private final RuntimeException failure;

    public SourceResolutionResult(int order, String scriptName, boolean resolved, Object value,
                                  Map<String, Map<String, Object>> sourceStates,
                                  List<Map<String, Object>> traceEvents) {
        this(order, scriptName, resolved, value, sourceStates, traceEvents, null);
    }

    public SourceResolutionResult(int order, String scriptName, boolean resolved, Object value,
                                  Map<String, Map<String, Object>> sourceStates,
                                  List<Map<String, Object>> traceEvents, RuntimeException failure) {
        this.order = order;
        this.scriptName = scriptName;
        this.resolved = resolved;
        this.value = copyValue(value);
        this.sourceStates = copyStates(sourceStates);
        this.traceEvents = copyEvents(traceEvents);
        this.failure = failure;
    }

    public RuntimeException getFailure() { return failure; }

    public int getOrder() {
        return order;
    }

    public String getScriptName() {
        return scriptName;
    }

    public boolean isResolved() {
        return resolved;
    }

    public Object getValue() {
        return copyValue(value);
    }

    public Map<String, Map<String, Object>> getSourceStates() {
        return sourceStates;
    }

    public List<Map<String, Object>> getTraceEvents() {
        return traceEvents;
    }

    private static Map<String, Map<String, Object>> copyStates(
            Map<String, Map<String, Object>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : source.entrySet()) {
            Map<String, Object> state = entry.getValue() == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(copyMap(entry.getValue()));
            copy.put(entry.getKey(), state);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static List<Map<String, Object>> copyEvents(List<Map<String, Object>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> event : source) {
            copy.add(Collections.unmodifiableMap(copyMap(event)));
        }
        return Collections.unmodifiableList(copy);
    }

    private static Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
        }
        return copy;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map) {
            return copyMap((Map<?, ?>) value);
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<?>) value) {
                copy.add(copyValue(item));
            }
            return copy;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object[] copy = new Object[length];
            for (int i = 0; i < length; i++) {
                copy[i] = copyValue(Array.get(value, i));
            }
            return copy;
        }
        return value;
    }
}
