package com.hengshucredit.rule.core.util;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一运行时缺失值语义。
 *
 * <p>字符串内容始终保持原样。空字符串和空白字符串只在判断语义中被识别为缺失，
 * 不会在参数归一化时改写成 null。
 * Java 浮点 NaN 视为真正缺失值归一为 null；无穷值保留其数值方向并收敛到对应类型的最大有限值。
 * JSON/HTTP 中的字符串 "Infinity"、"-Infinity" 和 "NaN" 不做字符串转换。</p>
 */
public final class MissingValueSemantics {

    private MissingValueSemantics() {
    }

    /**
     * Normalize only a scalar boundary value.  Callers that are evaluating an
     * operator should use this method so an empty mutable map/list is never
     * accidentally rewritten while looking at one operand.
     */
    public static Object normalizeScalar(Object value) {
        if (value == null) return null;
        if (value instanceof CharSequence) return value;
        if (value instanceof Double number) {
            if (number.isNaN()) return null;
            if (number == Double.POSITIVE_INFINITY) return Double.MAX_VALUE;
            if (number == Double.NEGATIVE_INFINITY) return -Double.MAX_VALUE;
            return number;
        }
        if (value instanceof Float number) {
            if (number.isNaN()) return null;
            if (number == Float.POSITIVE_INFINITY) return Float.MAX_VALUE;
            if (number == Float.NEGATIVE_INFINITY) return -Float.MAX_VALUE;
            return number;
        }
        return value;
    }

    public static Object normalize(Object value) {
        Object scalar = normalizeScalar(value);
        if (scalar != value) return scalar;
        if (value == null || value instanceof CharSequence) return value;
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), normalize(entry.getValue()));
            }
            return normalized.isEmpty() ? null : normalized;
        }
        if (value instanceof Collection<?> source) {
            if (source.isEmpty()) return null;
            List<Object> normalized = new ArrayList<>(source.size());
            for (Object item : source) normalized.add(normalize(item));
            return normalized;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            if (length == 0) return null;
            List<Object> normalized = new ArrayList<>(length);
            for (int i = 0; i < length; i++) normalized.add(normalize(Array.get(value, i)));
            return normalized;
        }
        return value;
    }

    public static boolean isMissing(Object value) {
        if (value == null) return true;
        if (value instanceof Double number && number.isNaN()) return true;
        if (value instanceof Float number && number.isNaN()) return true;
        if (value instanceof CharSequence) return value.toString().trim().isEmpty();
        if (value instanceof Collection<?> collection) return collection.isEmpty();
        if (value instanceof Map<?, ?> map) return map.isEmpty();
        return value.getClass().isArray() && Array.getLength(value) == 0;
    }
}
