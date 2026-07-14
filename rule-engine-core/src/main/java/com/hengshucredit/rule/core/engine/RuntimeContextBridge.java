package com.hengshucredit.rule.core.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 将编译脚本产生的中间结果通知给当前请求的运行时上下文。
 * 未绑定监听器时为安全的空操作，保证客户端和纯核心执行兼容。
 */
public final class RuntimeContextBridge {

    private static final ThreadLocal<BiConsumer<String, Object>> LISTENER = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> CURRENT_RULE = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> MATCHED_CONDITIONS = new ThreadLocal<>();

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

    public static Map<String, Object> currentRule() {
        Map<String, Object> rule = CURRENT_RULE.get();
        return rule == null ? Collections.<String, Object>emptyMap() : rule;
    }

    public static List<String> currentMatchedConditions() {
        List<String> conditions = MATCHED_CONDITIONS.get();
        return conditions == null ? Collections.<String>emptyList() : conditions;
    }

    public static Object setValue(String path, Object value) {
        BiConsumer<String, Object> listener = LISTENER.get();
        if (listener != null) {
            listener.accept(path, value);
        }
        return value;
    }
}
