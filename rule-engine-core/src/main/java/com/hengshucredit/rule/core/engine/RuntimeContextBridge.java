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
import java.util.regex.Pattern;

/**
 * 将编译脚本产生的中间结果通知给当前请求的运行时上下文。
 * 未绑定监听器时为安全的空操作，保证客户端和纯核心执行兼容。
 */
public final class RuntimeContextBridge {

    private static final ThreadLocal<BiConsumer<String, Object>> LISTENER = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> CURRENT_RULE = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> MATCHED_CONDITIONS = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> CONSTANT_VALUES = new ThreadLocal<>();
    private static final ThreadLocal<Consumer<Map<String, Object>>> TRACE_EVENT_LISTENER = new ThreadLocal<>();

    private RuntimeContextBridge() {
    }

    public static void bind(BiConsumer<String, Object> listener) {
        if (listener == null) {
            LISTENER.remove();
        } else {
            LISTENER.set(listener);
        }
    }

    public static void clear() {
        LISTENER.remove();
        CURRENT_RULE.remove();
        MATCHED_CONDITIONS.remove();
        CONSTANT_VALUES.remove();
        TRACE_EVENT_LISTENER.remove();
    }

    public static void setRuleContext(Map<String, Object> rule, List<String> matchedConditions) {
        Map<String, Object> safeRule = rule == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(rule));
        List<String> safeConditions = matchedConditions == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(matchedConditions));
        CURRENT_RULE.set(safeRule);
        MATCHED_CONDITIONS.set(safeConditions);
    }

    public static void bindTraceEventListener(Consumer<Map<String, Object>> listener) {
        if (listener == null) {
            TRACE_EVENT_LISTENER.remove();
        } else {
            TRACE_EVENT_LISTENER.set(listener);
        }
    }

    public static void addTraceEvent(Map<String, Object> event) {
        Consumer<Map<String, Object>> listener = TRACE_EVENT_LISTENER.get();
        if (listener != null && event != null) {
            listener.accept(event);
        }
    }

    public static Map<String, Object> currentRule() {
        Map<String, Object> rule = CURRENT_RULE.get();
        return rule == null ? Collections.<String, Object>emptyMap() : rule;
    }

    public static List<String> currentMatchedConditions() {
        List<String> conditions = MATCHED_CONDITIONS.get();
        return conditions == null ? Collections.<String>emptyList() : conditions;
    }

    public static void registerConstant(String path, Object value) {
        String rootPath = rootPath(path);
        if (rootPath == null) {
            return;
        }
        Map<String, Object> constants = CONSTANT_VALUES.get();
        if (constants == null) {
            constants = new LinkedHashMap<>();
            CONSTANT_VALUES.set(constants);
        }
        constants.put(rootPath, snapshotValue(value));
    }

    public static void assertScriptDoesNotAssignConstants(String script) {
        Map<String, Object> constants = CONSTANT_VALUES.get();
        if (script == null || constants == null || constants.isEmpty()) {
            return;
        }
        String executable = maskStringsAndComments(script);
        for (String constant : constants.keySet()) {
            String target = "(?<![A-Za-z0-9_$])" + Pattern.quote(constant)
                    + "(?:\\s*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*|\\[[^\\]]+\\]))*";
            String assignment = target
                    + "\\s*(?:\\+\\+|--|\\+=|-=|\\*=|/=|%=|(?<![!<>=])=(?!=))";
            if (Pattern.compile(assignment).matcher(executable).find()) {
                throw new IllegalStateException("常量字段不允许赋值: " + constant);
            }
        }
    }

    public static void assertConstantsUnchanged(Object context) {
        if (!(context instanceof Map)) {
            return;
        }
        Map<String, Object> constants = CONSTANT_VALUES.get();
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

    @SuppressWarnings("unchecked")
    public static void restoreConstants(Object context) {
        if (!(context instanceof Map)) {
            return;
        }
        Map<String, Object> constants = CONSTANT_VALUES.get();
        if (constants == null || constants.isEmpty()) {
            return;
        }
        Map<String, Object> values = (Map<String, Object>) context;
        for (Map.Entry<String, Object> entry : constants.entrySet()) {
            values.put(entry.getKey(), snapshotValue(entry.getValue()));
        }
    }

    public static Object setValue(String path, Object value) {
        String rootPath = rootPath(path);
        Map<String, Object> constants = CONSTANT_VALUES.get();
        if (rootPath != null && constants != null && constants.containsKey(rootPath)) {
            throw new IllegalStateException("常量字段不允许赋值: " + rootPath);
        }
        BiConsumer<String, Object> listener = LISTENER.get();
        if (listener != null) {
            listener.accept(path, value);
        }
        return value;
    }

    /**
     * QLExpress 的脚本局部赋值不会自动回写调用方 Map。开启追踪的规则执行结束后，
     * 按实际求值顺序回放赋值节点，使 QL 脚本与设计器动作共享同一份会话字段。
     */
    public static void syncTraceAssignments(List<ExpressionTrace> traces, Object context) {
        if (!(context instanceof Map) || traces == null || traces.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) context;
        for (ExpressionTrace trace : traces) {
            syncTraceAssignment(trace, values);
        }
    }

    private static void syncTraceAssignment(ExpressionTrace trace, Map<String, Object> values) {
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
        setValue(path, value);
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

    private static String maskStringsAndComments(String script) {
        StringBuilder masked = new StringBuilder(script.length());
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean lineComment = false;
        boolean blockComment = false;
        boolean escaped = false;
        for (int i = 0; i < script.length(); i++) {
            char current = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : '\0';
            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                    masked.append(current);
                } else {
                    masked.append(' ');
                }
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    masked.append("  ");
                    i++;
                    blockComment = false;
                } else {
                    masked.append(current == '\n' || current == '\r' ? current : ' ');
                }
                continue;
            }
            if (singleQuoted || doubleQuoted) {
                masked.append(current == '\n' || current == '\r' ? current : ' ');
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if ((singleQuoted && current == '\'') || (doubleQuoted && current == '"')) {
                    singleQuoted = false;
                    doubleQuoted = false;
                }
                continue;
            }
            if (current == '/' && next == '/') {
                masked.append("  ");
                i++;
                lineComment = true;
            } else if (current == '/' && next == '*') {
                masked.append("  ");
                i++;
                blockComment = true;
            } else if (current == '\'') {
                masked.append(' ');
                singleQuoted = true;
            } else if (current == '"') {
                masked.append(' ');
                doubleQuoted = true;
            } else {
                masked.append(current);
            }
        }
        return masked.toString();
    }
}
