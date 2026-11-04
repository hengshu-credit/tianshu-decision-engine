package com.hengshucredit.rule.server.dashboard;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
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
                                 Set<Long> rejectRefIds) {
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
                                  DecisionValues decisionValues) {
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
