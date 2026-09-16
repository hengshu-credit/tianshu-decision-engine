package com.hengshucredit.rule.core.engine;

import com.alibaba.qlexpress4.runtime.trace.ExpressionTrace;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Adapter for QL builtins; request execution passes RequestContext explicitly. */
public final class RuntimeContextBridge {
    private static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();

    private RuntimeContextBridge() { }

    public static RequestContext currentContext() {
        RequestContext context = CURRENT.get();
        if (context == null) {
            context = new RequestContext();
            CURRENT.set(context);
        }
        return context;
    }

    public static void clear() { CURRENT.remove(); }

    static RequestContext executionContext() {
        RequestContext context = CURRENT.get();
        return context == null ? new RequestContext() : context;
    }

    public static ContextScope install(RequestContext context) {
        ContextScope scope = new ContextScope(CURRENT.get());
        CURRENT.set(context);
        return scope;
    }

    public static ContextSnapshot captureContext() {
        return new ContextSnapshot(currentContext().forkForWorker(null));
    }

    public static ContextScope installContext(ContextSnapshot snapshot, Consumer<Map<String, Object>> listener) {
        RequestContext source = snapshot == null ? new RequestContext() : snapshot.context;
        return install(source.forkForWorker(listener));
    }

    public static final class ContextSnapshot {
        private final RequestContext context;
        private ContextSnapshot(RequestContext context) { this.context = context; }
    }

    public static final class ContextScope implements AutoCloseable {
        private final RequestContext previous;
        private boolean closed;
        private ContextScope(RequestContext previous) { this.previous = previous; }
        @Override
        public void close() {
            if (closed) return;
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            closed = true;
        }
    }

    public static void bind(BiConsumer<String, Object> listener) {
        currentContext().bind(listener);
    }

    public static void setRuleContext(Map<String, Object> rule, List<String> matchedConditions) {
        currentContext().setRuleContext(rule, matchedConditions);
    }

    public static void bindTraceEventListener(Consumer<Map<String, Object>> listener) {
        currentContext().bindTraceEventListener(listener);
    }

    public static void addTraceEvent(Map<String, Object> event) {
        currentContext().addTraceEvent(event);
    }

    public static Map<String, Object> currentRule() {
        return currentContext().currentRule();
    }

    public static List<String> currentMatchedConditions() {
        return currentContext().currentMatchedConditions();
    }

    public static void registerConstant(String path, Object value) {
        currentContext().registerConstant(path, value);
    }

    public static void assertConstantsUnchanged(Object context) {
        currentContext().assertConstantsUnchanged(context);
    }

    public static void replaceSourceStates(Map<String, Map<String, Object>> states) {
        currentContext().replaceSourceStates(states);
    }

    public static Map<String, Map<String, Object>> currentSourceStates() {
        return currentContext().currentSourceStates();
    }

    public static void putSourceState(String refType, Long refId, String dimension, Object value) {
        currentContext().putSourceState(refType, refId, dimension, value);
    }

    public static boolean sourceStatusMatches(String refType, String refId, String dimension, String expected) {
        return currentContext().sourceStatusMatches(refType, refId, dimension, expected);
    }

    public static boolean containsRegisteredConstants(Object context) {
        return currentContext().containsRegisteredConstants(context);
    }

    public static void restoreConstants(Object context) {
        currentContext().restoreConstants(context);
    }

    public static Object setValue(String path, Object value) {
        return currentContext().setValue(path, value);
    }

    public static void syncTraceAssignments(List<ExpressionTrace> traces, Object context) {
        currentContext().syncTraceAssignments(traces, context);
    }

}
