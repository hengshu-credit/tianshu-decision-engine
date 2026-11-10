package com.hengshucredit.rule.server.dashboard;

import com.alibaba.fastjson.JSONObject;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DashboardMapping {

    private DashboardMapping() {
    }

    public enum RefType {
        VARIABLE,
        DATA_OBJECT_FIELD
    }

    public enum JsonSource {
        INPUT,
        OUTPUT
    }

    public enum MappingSource {
        PROJECT_OVERRIDE,
        USER_DEFAULT,
        SYSTEM_DEFAULT
    }

    public record DecisionValues(Set<String> pass,
                                 Set<String> review,
                                 Set<String> reject,
                                 Set<Long> passRefIds,
                                 Set<Long> reviewRefIds,
                                 Set<Long> rejectRefIds,
                                 List<JSONObject> passOperands,
                                 List<JSONObject> reviewOperands,
                                 List<JSONObject> rejectOperands) {
        public DecisionValues(Set<String> pass, Set<String> review, Set<String> reject,
                              Set<Long> passRefIds, Set<Long> reviewRefIds, Set<Long> rejectRefIds) {
            this(pass, review, reject, passRefIds, reviewRefIds, rejectRefIds, List.of(), List.of(), List.of());
        }
        public DecisionValues(Set<String> pass, Set<String> review, Set<String> reject) {
            this(pass, review, reject, Set.of(), Set.of(), Set.of());
        }
        public DecisionValues {
            pass = immutable(pass);
            review = immutable(review);
            reject = immutable(reject);
            passRefIds = immutable(passRefIds);
            reviewRefIds = immutable(reviewRefIds);
            rejectRefIds = immutable(rejectRefIds);
            passOperands = copyOperands(passOperands);
            reviewOperands = copyOperands(reviewOperands);
            rejectOperands = copyOperands(rejectOperands);
        }

        private static List<JSONObject> copyOperands(List<JSONObject> values) {
            return values == null ? List.of() : values.stream()
                    .map(value -> JSONObject.parseObject(JSONObject.toJSONString(value))).toList();
        }

        private static <T> Set<T> immutable(Set<T> values) {
            return values == null
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(values));
        }
    }

    public static String normalizeDecisionValue(String value) {
        String text = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (text.matches("-?(0|[1-9]\\d*)(\\.\\d+)?([Ee][+-]?\\d+)?")) {
            try { return new java.math.BigDecimal(text).stripTrailingZeros().toEngineeringString(); }
            catch (NumberFormatException ignored) { /* 保留非数值结果编码。 */ }
        }
        return text;
    }

    public record StoredMapping(RefType refType,
                                Long refId,
                                JsonSource jsonSource,
                                DecisionValues decisionValues) {
    }

    public record ResolvedMapping(DashboardMetricField metricField,
                                  RefType refType,
                                  Long refId,
                                  JsonSource jsonSource,
                                  String jsonPath,
                                  String referenceLabel,
                                  MappingSource mappingSource,
                                  boolean valid,
                                  String errorMessage,
                                  DecisionValues decisionValues,
                                  @com.fasterxml.jackson.annotation.JsonIgnore DashboardDecisionExpressions.Prepared decisionExpressions) {
        public ResolvedMapping(DashboardMetricField metricField, RefType refType, Long refId,
                               JsonSource jsonSource, String jsonPath, String referenceLabel,
                               MappingSource mappingSource, boolean valid, String errorMessage,
                               DecisionValues decisionValues) {
            this(metricField, refType, refId, jsonSource, jsonPath, referenceLabel, mappingSource,
                    valid, errorMessage, decisionValues, null);
        }
    }

    public static final class ResolvedMappings {
        private final Map<DashboardMetricField, ResolvedMapping> values;

        public ResolvedMappings(Map<DashboardMetricField, ResolvedMapping> values) {
            EnumMap<DashboardMetricField, ResolvedMapping> copy =
                    new EnumMap<>(DashboardMetricField.class);
            if (values != null) copy.putAll(values);
            this.values = Collections.unmodifiableMap(copy);
        }

        public ResolvedMapping get(DashboardMetricField field) {
            return values.get(field);
        }

        public Map<DashboardMetricField, ResolvedMapping> values() {
            return values;
        }
    }
}
