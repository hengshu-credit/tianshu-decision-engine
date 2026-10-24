package com.hengshucredit.rule.server.governance;

import java.util.Locale;
import java.util.Map;

/** 治理快照的宽容取值；业务字段校验仍由资源适配器负责。 */
final class SnapshotValues {
    private SnapshotValues() { }

    static Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    static Integer integerValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    static String upper(Object value, String fallback) {
        String text = stringValue(value);
        return hasText(text) ? text.trim().toUpperCase(Locale.ROOT) : fallback;
    }

    static long defaultLong(Object value, long fallback) {
        Long parsed = longValue(value);
        return parsed == null ? fallback : parsed;
    }

    static int defaultInteger(Object value, int fallback) {
        Integer parsed = integerValue(value);
        return parsed == null ? fallback : parsed;
    }

    static void putLong(Map<String, Object> target, String key, Object value) {
        Long parsed = longValue(value);
        if (parsed != null) target.put(key, parsed);
    }
}
