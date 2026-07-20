package com.hengshucredit.rule.core.compiler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 来源状态操作符与运行时 sidecar 维度的唯一映射。 */
final class SourceStatusOperators {

    private static final Map<String, Expectation> EXPECTATIONS;

    static {
        Map<String, Expectation> values = new LinkedHashMap<>();
        put(values, "source_success", "OUTCOME", "SUCCESS");
        put(values, "source_error", "OUTCOME", "ERROR");
        put(values, "source_timeout", "OUTCOME", "TIMEOUT");
        put(values, "source_fallback", "FALLBACK_USED", "TRUE");
        put(values, "source_cache_enabled", "CACHE_CONFIGURED", "TRUE");
        put(values, "source_cache_disabled", "CACHE_CONFIGURED", "FALSE");
        put(values, "source_cache_hit", "CACHE_STATE", "HIT");
        put(values, "source_cache_miss", "CACHE_STATE", "MISS");
        put(values, "source_cache_unavailable", "CACHE_STATE", "UNAVAILABLE");
        put(values, "source_origin_live", "DATA_ORIGIN", "LIVE");
        put(values, "source_origin_cache", "DATA_ORIGIN", "CACHE");
        put(values, "source_origin_stale_cache", "DATA_ORIGIN", "STALE_CACHE");
        put(values, "source_has_data", "DATA_STATE", "HAS_DATA");
        put(values, "source_no_data", "DATA_STATE", "NO_DATA");
        put(values, "source_match_hit", "MATCH_STATE", "HIT");
        put(values, "source_match_miss", "MATCH_STATE", "MISS");
        put(values, "source_output_present", "PRESENCE", "PRESENT");
        put(values, "source_output_missing", "PRESENCE", "MISSING");
        put(values, "source_field_present", "PRESENCE", "PRESENT");
        put(values, "source_field_missing", "PRESENCE", "MISSING");
        put(values, "source_field_invalid", "PRESENCE", "INVALID");
        EXPECTATIONS = Collections.unmodifiableMap(values);
    }

    private SourceStatusOperators() {
    }

    static boolean supports(String operator) {
        return EXPECTATIONS.containsKey(operator);
    }

    static Expectation expectation(String operator) {
        return EXPECTATIONS.get(operator);
    }

    private static void put(Map<String, Expectation> values, String operator,
                            String dimension, String expected) {
        values.put(operator, new Expectation(dimension, expected));
    }

    static final class Expectation {
        final String dimension;
        final String expected;

        Expectation(String dimension, String expected) {
            this.dimension = dimension;
            this.expected = expected;
        }
    }
}
