package com.hengshucredit.rule.core.engine;

import com.alibaba.qlexpress4.runtime.trace.ExpressionTrace;
import com.alibaba.qlexpress4.runtime.trace.TraceType;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 将编译脚本产生的中间结果通知给当前请求的运行时上下文。
 * 每次执行显式持有；跨线程仅分享不可变元数据。
 */
public final class RequestContext {

    private BiConsumer<String, Object> listener;
    private Map<String, Object> rule = Collections.emptyMap();
    private Map<String, Object> rootRule = Collections.emptyMap();
    private Object rootInput;

    /** 在进入根规则时保存快照，工作线程共享只读输入，避免后续赋值覆盖调用方报文。 */
    public void setRootInput(Object input) { rootInput = snapshotValue(input); }
    public Object rootInput() { return snapshotValue(rootInput); }
    private java.time.LocalDateTime startedAt = java.time.LocalDateTime.now();
    private List<String> matchedConditions = Collections.emptyList();
    private Map<String, Object> constantValues;
    private Consumer<Map<String, Object>> traceEventListener;
    private Map<String, Map<String, Object>> sourceStates = Collections.emptyMap();
    private Consumer<String> sourceStateResolver;
    private final ArrayDeque<Map<String, Object>> executionContexts = new ArrayDeque<>();
    private Runnable checkpointListener;
    /** Shared by worker contexts forked from one logical execution. */
    private Map<String, Object> randomValues = new ConcurrentHashMap<>();
    private AtomicLong randomSequence = new AtomicLong();
    /** 每个规则/函数/参数签名的调用序号，避免前面插入了另一个随机节点后发生整体错位。 */
    private Map<String, AtomicLong> randomSlots = new ConcurrentHashMap<>();

    public void bindCheckpointListener(Runnable listener) {
        checkpointListener = listener;
    }

    public Object randomValue(String function, Object argumentsKey, Supplier<Object> generator) {
        return randomValue(function, null, argumentsKey, generator);
    }

    /**
     * Stable random slot API. Compiler/runtime integrations can pass a node identity;
     * legacy callers continue to use the sequence based key.
     */
    public Object randomValue(String function, String stableIdentity,
                              Object argumentsKey, Supplier<Object> generator) {
        long sequence = randomSequence.getAndIncrement();
        String arguments = String.valueOf(argumentsKey);
        String ruleIdentity = ruleIdentity();
        String signature = ruleIdentity + "|" + function + "|" + arguments;
        long slot = randomSlots.computeIfAbsent(signature, ignored -> new AtomicLong()).getAndIncrement();
        String key = stableIdentity == null || stableIdentity.trim().isEmpty()
                ? "slot:" + ruleIdentity + "|" + function + "|" + slot + "|" + arguments
                : "slot:" + stableIdentity.trim() + "#" + arguments;
        Object restored = randomValues.get(key);
        if (restored != null || randomValues.containsKey(key)) return restored;
        // 兼容旧检查点（function#sequence#arguments），按同一参数签名迁移，
        // 规则前面新增了不同随机节点时仍能命中原结果。
        if (stableIdentity == null || stableIdentity.trim().isEmpty()) {
            String legacyPrefix = function + "#";
            String legacySuffix = "#" + arguments;
            long wanted = slot;
            for (Map.Entry<String, Object> entry : randomValues.entrySet()) {
                if (!entry.getKey().startsWith(legacyPrefix) || !entry.getKey().endsWith(legacySuffix)) continue;
                if (wanted-- == 0) {
                    randomValues.put(key, entry.getValue());
                    return entry.getValue();
                }
            }
        }
        Object value = generator.get();
        randomValues.put(key, snapshotValue(value));
        Runnable listener = checkpointListener;
        if (listener != null) listener.run();
        return value;
    }

    public Map<String, Object> randomSnapshot() {
        return new LinkedHashMap<>(randomValues);
    }

    private String ruleIdentity() {
        Object id = rule == null ? null : rule.get("id");
        if (id != null) return String.valueOf(id);
        Object code = rule == null ? null : rule.get("code");
        return code == null ? "ROOT" : String.valueOf(code);
    }

    public void restoreRandomValues(Map<String, Object> values) {
        randomValues.clear();
        randomSlots.clear();
        if (values != null) randomValues.putAll(values);
        long next = randomValues.keySet().stream().map(this::sequenceOf)
                .filter(java.util.Objects::nonNull).mapToLong(Long::longValue).max().orElse(-1L) + 1L;
        randomSequence.set(next);
    }

    private Long sequenceOf(String key) {
        int start = key == null ? -1 : key.indexOf('#');
        int end = start < 0 ? -1 : key.indexOf('#', start + 1);
        if (start < 0 || end < 0) return null;
        try { return Long.valueOf(key.substring(start + 1, end)); }
        catch (NumberFormatException ignored) { return null; }
    }

    public SourceStateScope bindSourceStateResolver(Consumer<String> resolver) {
        SourceStateScope scope = new SourceStateScope(sourceStateResolver);
        sourceStateResolver = resolver;
        return scope;
    }

    public final class SourceStateScope implements AutoCloseable {
        private final Consumer<String> previous;
        private boolean closed;
        private SourceStateScope(Consumer<String> previous) { this.previous = previous; }
        @Override public void close() {
            if (!closed) { sourceStateResolver = previous; closed = true; }
        }
    }
    private List<RuntimeWrite> runtimeWrites;
    private int runtimeWriteDepth;
    private Map<String, CustomFunction> functions = Collections.emptyMap();
    private Map<Long, Map<String, String>> externalFieldPaths = new java.util.concurrent.ConcurrentHashMap<>();
    private Map<Long, Map<String, String>> externalDefaultPaths = new java.util.concurrent.ConcurrentHashMap<>();
    private Map<Long, Map<String, String>> externalDefaultAliases = new java.util.concurrent.ConcurrentHashMap<>();

    public void registerExternalDefaultField(Long apiId, String key, String path, String sourceKey) {
        if (apiId == null || key == null || path == null) return;
        externalDefaultPaths.computeIfAbsent(apiId, ignored -> new java.util.concurrent.ConcurrentHashMap<>()).putIfAbsent(key, path);
        if (sourceKey != null && !sourceKey.equals(key)) externalDefaultAliases.computeIfAbsent(apiId,
                ignored -> new java.util.concurrent.ConcurrentHashMap<>()).putIfAbsent(key, sourceKey);
    }

    /** 根请求内只增加 ID 绑定的接口字段元信息，工作线程共享并发安全的注册表。 */
    public void registerExternalField(Long apiId, String referenceKey, String resultPath) {
        if (apiId == null || referenceKey == null || resultPath == null) return;
        externalFieldPaths.computeIfAbsent(apiId, ignored -> new java.util.concurrent.ConcurrentHashMap<>())
                .putIfAbsent(referenceKey, resultPath);
    }

    public Map<String, String> externalFieldPaths(Long apiId) {
        Map<String, String> result = new LinkedHashMap<>(externalDefaultPaths.getOrDefault(apiId, Collections.emptyMap()));
        Map<String, String> actual = externalFieldPaths.getOrDefault(apiId, Collections.emptyMap());
        externalDefaultAliases.getOrDefault(apiId, Collections.emptyMap()).forEach((key, source) -> {
            String previous = result.get(source);
            String replacement = actual.get(source);
            String path = result.get(key);
            if (previous != null && replacement != null && path != null && (path.equals(previous) || path.startsWith(previous + "."))) {
                result.put(key, replacement + path.substring(previous.length()));
            }
        });
        result.putAll(actual);
        return result;
    }

    public CustomFunction function(String code) {
        return functions.get(code);
    }

    /** A rule or conversion function owns its entire binding set; missing targets never fall back to another rule. */
    public FunctionScope bindFunctions(Map<String, CustomFunction> bindings) {
        FunctionScope scope = new FunctionScope(functions);
        functions = Map.copyOf(bindings);
        return scope;
    }

    public final class FunctionScope implements AutoCloseable {
        private final Map<String, CustomFunction> previous;
        private boolean closed;

        private FunctionScope(Map<String, CustomFunction> previous) { this.previous = previous; }

        @Override
        public void close() {
            if (!closed) {
                functions = previous;
                closed = true;
            }
        }
    }

    public void bind(BiConsumer<String, Object> listener) {
        this.listener = listener;
    }

    /** 当前 QLExpress 局部上下文；运行时写入会同步回同一份 Map。 */
    public ExecutionContextScope bindExecutionContext(Object context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> values = context instanceof Map ? (Map<String, Object>) context : null;
        if (values == null) return new ExecutionContextScope(false);
        executionContexts.addLast(values);
        return new ExecutionContextScope(true);
    }

    public final class ExecutionContextScope implements AutoCloseable {
        private final boolean bound;
        private boolean closed;
        private ExecutionContextScope(boolean bound) { this.bound = bound; }
        @Override public void close() {
            if (!closed) {
                if (bound && !executionContexts.isEmpty()) executionContexts.removeLast();
                closed = true;
            }
        }
    }

    public void setRuleContext(Map<String, Object> rule, List<String> matchedConditions) {
        Map<String, Object> safeRule = rule == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(rule));
        List<String> safeConditions = matchedConditions == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(matchedConditions));
        this.rule = safeRule;
        if (rootRule.isEmpty() && !safeRule.isEmpty()) rootRule = safeRule;
        this.matchedConditions = safeConditions;
    }

    public void bindTraceEventListener(Consumer<Map<String, Object>> listener) {
        this.traceEventListener = listener;
    }

    /** Shares immutable metadata, never request writes, constants or listeners. */
    RequestContext forkForWorker(Consumer<Map<String, Object>> listener) {
        RequestContext child = new RequestContext();
        child.rule = rule;
        child.rootRule = rootRule;
        child.rootInput = rootInput;
        child.startedAt = startedAt;
        child.matchedConditions = matchedConditions;
        child.sourceStates = sourceStates;
        child.traceEventListener = listener;
        child.functions = functions;
        child.externalFieldPaths = externalFieldPaths;
        child.externalDefaultPaths = externalDefaultPaths;
        child.externalDefaultAliases = externalDefaultAliases;
        child.checkpointListener = checkpointListener;
        child.randomValues = randomValues;
        child.randomSequence = randomSequence;
        child.randomSlots = randomSlots;
        return child;
    }

    public Set<String> constantNames() {
        return constantValues == null ? Collections.emptySet()
                : Collections.unmodifiableSet(constantValues.keySet());
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

    public Map<String, Object> rootRule() { return rootRule; }

    public java.time.LocalDateTime startedAt() { return startedAt; }

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
        if (constants == null) {
            constants = new LinkedHashMap<>();
            constantValues = constants;
        }
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
        if (sourceStateResolver != null) sourceStateResolver.accept(sourceStateKey(refType, refId));
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
                if (runtimeWrites == null) runtimeWrites = new ArrayList<>();
                runtimeWrites.add(new RuntimeWrite(path, value));
            }
        }
        for (Map<String, Object> activeContext : executionContexts) {
            if (activeContext != null) writePathSafely(activeContext, path, value);
        }
        return value;
    }

    int beginRuntimeWriteScope() {
        runtimeWriteDepth++;
        return runtimeWrites == null ? 0 : runtimeWrites.size();
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
            writePathSafely(values, write.path, write.value);
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
        writePathSafely(values, path, value);
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
                Object child = rawValue(current, part);
                if (!(child instanceof Map)) {
                    child = new LinkedHashMap<String, Object>();
                    current.put(part, child);
                }
                current = (Map<String, Object>) child;
            }
        }
    }

    private static Object rawValue(Map<String, Object> values, String key) {
        if (values instanceof RuntimeWriteTarget target) return target.runtimeValue(key);
        return values.get(key);
    }

    /** 只读调用方上下文仍应收到运行时监听事件；无法回写时保持原有只读语义。 */
    private static void writePathSafely(Map<String, Object> values, String path, Object value) {
        try {
            writePath(values, path, value);
        } catch (UnsupportedOperationException ignored) {
            // Map.of/Collections.unmodifiableMap 等只读上下文不能接收运行时回写。
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
