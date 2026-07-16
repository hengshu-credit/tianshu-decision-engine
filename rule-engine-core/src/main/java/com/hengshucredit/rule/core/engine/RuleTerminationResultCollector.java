package com.hengshucredit.rule.core.engine;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在整体规则提前终止时，按根规则输出定义收集当前已产生的结果。
 */
public final class RuleTerminationResultCollector {

    private RuleTerminationResultCollector() {
    }

    public static Map<String, Object> collect(Map<String, Object> values,
                                              Collection<String> outputScriptNames) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (outputScriptNames == null) {
            return result;
        }
        for (String scriptName : outputScriptNames) {
            if (scriptName != null && !scriptName.trim().isEmpty()) {
                String normalized = scriptName.trim();
                result.put(normalized, readPath(values, normalized));
            }
        }
        return result;
    }

    private static Object readPath(Map<String, Object> values, String path) {
        Object current = values;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(part);
        }
        return current;
    }
}
