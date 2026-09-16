package com.hengshucredit.rule.core.engine;

import com.alibaba.qlexpress4.runtime.trace.ExpressionTrace;
import com.alibaba.qlexpress4.runtime.trace.TraceType;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.Set;

/**
 * 将编译脚本产生的中间结果通知给当前请求的运行时上下文。
 * 每次执行显式持有；跨线程仅分享不可变元数据。
 */
public final class RequestContext {

    private BiConsumer<String, Object> listener;
    private Map<String, Object> rule = Collections.emptyMap();
    private List<String> matchedConditions = Collections.emptyList();
    private final Map<String, Object> constantValues = new LinkedHashMap<>();
    private Consumer<Map<String, Object>> traceEventListener;
    private Map<String, Map<String, Object>> sourceStates = Collections.emptyMap();
    private List<RuntimeWrite> runtimeWrites;
    private int runtimeWriteDepth;

    public void bind(BiConsumer<String, Object> listener) {
        this.listener = listener;
    }

    public void setRuleContext(Map<String, Object> rule, List<String> matchedConditions) {
        Map<String, Object> safeRule = rule == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(rule));
        List<String> safeConditions = matchedConditions == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(matchedConditions));
        this.rule = safeRule;
        this.matchedConditions = safeConditions;
    }

    public void bindTraceEventListener(Consumer<Map<String, Object>> listener) {
        this.traceEventListener = listener;
    }

    /** Shares immutable metadata, never request writes, constants or listeners. */
    RequestContext forkForWorker(Consumer<Map<String, Object>> listener) {
        RequestContext child = new RequestContext();
        child.rule = rule;
        child.matchedConditions = matchedConditions;
        child.sourceStates = sourceStates;
        child.traceEventListener = listener;
        return child;
    }

    public Set<String> constantNames() {
        return Collections.unmodifiableSet(constantValues.keySet());
    }

    public void addTraceEvent(Map<String, Object> event) {
        Consumer<Map<String, Object>> listener = this.traceEventListener;
        if (listener != null && event != null) {
            listener.accept(event);
        }
    }

    public Map<String, Object> currentRule() {
        Map<String, Object> rule = this.rule;
        return rule == null ? Collections.<String, Object>emptyMap() : rule;
    }

    public List<String> currentMatchedConditions() {
        List<String> conditions = this.matchedConditions;
        return conditions == null ? Collections.<String>emptyList() : conditions;
    }

    public void registerConstant(String path, Object value) {
        String rootPath = rootPath(path);
        if (rootPath == null) {
            return;
        }
        Map<String, Object> constants = constantValues;
        constants.put(rootPath, snapshotValue(value));
    }

    public void assertConstantsUnchanged(Object context) {
        if (!(context instanceof Map)) {
            return;
        }
        Map<String, Object> constants = constantValues;
        if (constants == null || constants.isEmpty()) {
            return;
        }
        Map<?, ?> values = (Map<?, ?>) context;
        for (Map.Entry<String, Object> entry : constants.entrySet()) {
            if (!values.containsKey(entry.getKey())
                    || !Objects.deepEquals(entry.getValue(), values.get(entry.getKey()))) {
                restoreConstants(context);
                throw new IllegalStateException("常量字段不允许赋值: " + entry.getKey());
            }
        }
    }

    public void replaceSourceStates(Map<String, Map<String, Object>> states) {
        sourceStates = copySourceStates(states);
    }

    public Map<String, Map<String, Object>> currentSourceStates() {
        return sourceStates;
    }

    public void putSourceState(String refType, Long refId, String dimension, Object value) {
        if (empty(refType) || refId == null || empty(dimension)) return;
        String key = sourceStateKey(refType, String.valueOf(refId));
        Map<String, Map<String, Object>> next = new LinkedHashMap<>(sourceStates);
        Map<String, Object> state = new LinkedHashMap<>(sourceStates.getOrDefault(key, Collections.emptyMap()));
        state.put(dimension.trim().toUpperCase(), value);
        next.put(key, Collections.unmodifiableMap(state));
        sourceStates = Collections.unmodifiableMap(next);
    }

    public boolean sourceStatusMatches(String refType, String refId,
                                              String dimension, String expected) {
        if (empty(refType) || empty(refId) || empty(dimension) || expected == null) return false;
        Map<String, Map<String, Object>> states = sourceStates;
        if (states == null) return false;
        Map<String, Object> state = states.get(sourceStateKey(refType, refId));
        if (state == null) return false;
        Object actual = state.get(dimension.trim().toUpperCase());
        return actual != null && String.valueOf(actual).equalsIgnoreCase(expected.trim());
    }

    private static String sourceStateKey(String refType, String refId) {
        return refType.trim().toUpperCase() + ":" + refId.trim();
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }

    public boolean containsRegisteredConstants(Object context) {
        if (!(context instanceof Map)) {
            return false;
        }
        Map<String, Object> constants = constantValues;
        if (constants == null || constants.isEmpty()) {
            return false;
        }
        Map<?, ?> values = (Map<?, ?>) context;
        for (Map.Entry<String, Object> entry : constants.entrySet()) {
            if (!values.containsKey(entry.getKey())
                    || !Objects.deepEquals(entry.getValue(), values.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public void restoreConstants(Object context) {
        if (!(context instanceof Map)) {
            return;
        }
        Map<String, Object> constants = constantValues;
        if (constants == null || constants.isEmpty()) {
            return;
        }
        Map<String, Object> values = (Map<String, Object>) context;
        for (Map.Entry<String, Object> entry : constants.entrySet()) {
            if (!values.containsKey(entry.getKey())
                    || !Objects.deepEquals(entry.getValue(), values.get(entry.getKey()))) {
                values.put(entry.getKey(), snapshotValue(entry.getValue()));
            }
        }
    }

    public Object setValue(String path, Object value) {
        return setValue(path, value, true);
    }

    private Object setValue(String path, Object value, boolean recordRuntimeWrite) {
        String rootPath = rootPath(path);
        Map<String, Object> constants = constantValues;
        if (rootPath != null && constants != null && constants.containsKey(rootPath)) {
            throw new IllegalStateException("常量字段不允许赋值: " + rootPath);
        }
        BiConsumer<String, Object> listener = this.listener;
        if (listener != null) {
            listener.accept(path, value);
            if (recordRuntimeWrite && runtimeWriteDepth > 0) {
                runtimeWrites.add(new RuntimeWrite(path, value));
            }
        }
        return value;
    }

    int beginRuntimeWriteScope() {
        List<RuntimeWrite> writes = runtimeWrites;
        if (writes == null) {
            writes = new ArrayList<>();
            runtimeWrites = writes;
        }
        runtimeWriteDepth++;
        return writes.size();
    }

    void replayRuntimeWrites(int marker, Object context) {
        if (!(context instanceof Map)) {
            return;
        }
        List<RuntimeWrite> writes = runtimeWrites;
        if (writes == null || marker >= writes.size()) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) context;
        for (int i = Math.max(0, marker); i < writes.size(); i++) {
            RuntimeWrite write = writes.get(i);
            setValue(write.path, write.value, false);
            writePath(values, write.path, write.value);
        }
    }

    void endRuntimeWriteScope() {
        if (--runtimeWriteDepth == 0) runtimeWrites = null;
    }

    /**
     * QLExpress 的脚本局部赋值不会自动回写调用方 Map。开启追踪的规则执行结束后，
     * 按实际求值顺序回放赋值节点，使 QL 脚本与设计器动作共享同一份会话字段。
     */
    public void syncTraceAssignments(List<ExpressionTrace> traces, Object context) {
        if (!(context instanceof Map) || traces == null || traces.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) context;
        for (ExpressionTrace trace : traces) {
            syncTraceAssignment(trace, values);
        }
    }

    private void syncTraceAssignment(ExpressionTrace trace, Map<String, Object> values) {
        if (trace == null) {
            return;
        }
        List<ExpressionTrace> children = trace.getChildren();
        if (children != null) {
            for (ExpressionTrace child : children) {
                syncTraceAssignment(child, values);
            }
        }
        if (!trace.isEvaluated() || trace.getType() != TraceType.OPERATOR
                || !isAssignmentOperator(trace.getToken()) || children == null || children.isEmpty()) {
            return;
        }
        String path = assignmentPath(children.get(0));
        if (path == null) {
            return;
        }
        Object value = trace.getValue();
        setValue(path, value, false);
        writePath(values, path, value);
    }

    private static boolean isAssignmentOperator(String token) {
        return "=".equals(token) || "+=".equals(token) || "-=".equals(token)
                || "*=".equals(token) || "/=".equals(token) || "%=".equals(token)
                || "++".equals(token) || "--".equals(token);
    }

    private static String assignmentPath(ExpressionTrace target) {
        if (target == null || target.getType() != TraceType.VARIABLE) {
            return null;
        }
        String token = target.getToken();
        return token == null || token.trim().isEmpty() ? null : token.trim();
    }

    @SuppressWarnings("unchecked")
    private static void writePath(Map<String, Object> values, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = values;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            if (i == parts.length - 1) {
                current.put(part, value);
            } else {
                Object child = current.get(part);
                if (!(child instanceof Map)) {
                    child = new LinkedHashMap<String, Object>();
                    current.put(part, child);
                }
                current = (Map<String, Object>) child;
            }
        }
    }

    private static String rootPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        String normalized = path.trim();
        int dot = normalized.indexOf('.');
        return dot < 0 ? normalized : normalized.substring(0, dot);
    }

    private static final class RuntimeWrite {
        private final String path;
        private final Object value;

        private RuntimeWrite(String path, Object value) {
            this.path = path;
            this.value = value;
        }
    }

    private static Map<String, Map<String, Object>> copySourceStates(
            Map<String, Map<String, Object>> states) {
        if (states == null || states.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : states.entrySet()) {
            Map<String, Object> value = entry.getValue() == null
                    ? Collections.<String, Object>emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue()));
            copy.put(entry.getKey(), value);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object snapshotValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                copy.put(String.valueOf(entry.getKey()), snapshotValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<?>) value) {
                copy.add(snapshotValue(item));
            }
            return copy;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object[] copy = new Object[length];
            for (int i = 0; i < length; i++) {
                copy[i] = snapshotValue(Array.get(value, i));
            }
            return copy;
        }
        return value;
    }

}
