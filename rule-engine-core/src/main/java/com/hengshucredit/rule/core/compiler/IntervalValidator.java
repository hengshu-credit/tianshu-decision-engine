package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.math.BigDecimal;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 编译期校验可静态确定的数值和日期区间，避免生成具有歧义的 if/else if 链。
 */
final class IntervalValidator {

    private IntervalValidator() {
    }

    static void validateDimensionRanges(JSONArray dimensions) {
        if (dimensions == null) return;
        for (int i = 0; i < dimensions.size(); i++) {
            JSONObject dimension = dimensions.getJSONObject(i);
            if (dimension == null) continue;
            validateRangeSegments(dimension.getJSONArray("segments"), "维度 " + (i + 1),
                    dimension.getString("varType"));
        }
    }

    static void validateRangeSegments(JSONArray segments, String scope, String dimensionType) {
        if (segments == null) return;
        List<Interval> intervals = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            JSONObject segment = segments.getJSONObject(i);
            if (segment == null || !"range".equals(segment.getString("operator"))) continue;
            JSONObject minOperand = segment.getJSONObject("minOperand");
            JSONObject maxOperand = segment.getJSONObject("maxOperand");
            String min = staticValue(minOperand, segment.getString("min"));
            String max = staticValue(maxOperand, segment.getString("max"));
            if (min == null || max == null) continue;
            boolean dateInterval = isDateType(dimensionType)
                    || isDateType(minOperand == null ? null : minOperand.getString("valueType"))
                    || isDateType(maxOperand == null ? null : maxOperand.getString("valueType"));
            BigDecimal lower = comparableValue(min, dateInterval);
            BigDecimal upper = comparableValue(max, dateInterval);
            if (lower == null || upper == null) continue;
            intervals.add(new Interval(lower, upper, normalizeBoundary(segment.getString("rangeBoundary")), i + 1));
        }
        validate(intervals, scope);
    }

    static void validateThresholds(JSONArray thresholds) {
        if (thresholds == null) return;
        List<Interval> intervals = new ArrayList<>();
        for (int i = 0; i < thresholds.size(); i++) {
            JSONObject threshold = thresholds.getJSONObject(i);
            if (threshold == null) continue;
            BigDecimal lower = numberValue(threshold.getString("min"));
            BigDecimal upper = numberValue(threshold.getString("max"));
            if (lower == null || upper == null) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个阈值区间端点必须是数字");
            }
            intervals.add(new Interval(lower, upper, "[)", i + 1));
        }
        validate(intervals, "阈值");
    }

    private static void validate(List<Interval> intervals, String scope) {
        for (Interval interval : intervals) {
            int comparison = interval.lower.compareTo(interval.upper);
            if (comparison > 0 || (comparison == 0 && (!interval.leftClosed || !interval.rightClosed))) {
                throw new IllegalArgumentException(scope + "第 " + interval.index + " 个区间下界必须小于上界");
            }
        }
        for (int i = 0; i < intervals.size(); i++) {
            for (int j = i + 1; j < intervals.size(); j++) {
                if (overlaps(intervals.get(i), intervals.get(j))) {
                    throw new IllegalArgumentException(scope + "第 " + intervals.get(i).index
                            + " 个与第 " + intervals.get(j).index + " 个区间重叠");
                }
            }
        }
    }

    private static boolean overlaps(Interval left, Interval right) {
        if (left.upper.compareTo(right.lower) < 0 || right.upper.compareTo(left.lower) < 0) return false;
        if (left.upper.compareTo(right.lower) == 0) return left.rightClosed && right.leftClosed;
        if (right.upper.compareTo(left.lower) == 0) return right.rightClosed && left.leftClosed;
        return true;
    }

    private static String staticValue(JSONObject operand, String legacyValue) {
        if (operand == null) return legacyValue;
        return "LITERAL".equals(operand.getString("kind")) ? operand.getString("value") : null;
    }

    private static BigDecimal numberValue(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static BigDecimal comparableValue(String value, boolean dateInterval) {
        return dateInterval ? dateValue(value) : numberValue(value);
    }

    private static BigDecimal dateValue(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String trimmed = value.trim();
        if (trimmed.matches("^-?\\d{10,}$")) return numberValue(trimmed);
        String[] datePatterns = {
                "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd",
                "yyyyMMddHHmmss", "yyyyMMdd"
        };
        for (String pattern : datePatterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = format.parse(trimmed, position);
            if (parsed != null && position.getIndex() == trimmed.length()) {
                return BigDecimal.valueOf(parsed.getTime());
            }
        }
        return null;
    }

    private static boolean isDateType(String valueType) {
        if (valueType == null) return false;
        String normalized = valueType.trim().toUpperCase();
        return "DATE".equals(normalized) || "DATETIME".equals(normalized)
                || "TIMESTAMP".equals(normalized) || "LOCALDATE".equals(normalized)
                || "LOCALDATETIME".equals(normalized);
    }

    private static String normalizeBoundary(String rangeBoundary) {
        if ("[)".equals(rangeBoundary) || "()".equals(rangeBoundary)
                || "[]".equals(rangeBoundary) || "(]".equals(rangeBoundary)) {
            return rangeBoundary;
        }
        return "[)";
    }

    private static final class Interval {
        private final BigDecimal lower;
        private final BigDecimal upper;
        private final boolean leftClosed;
        private final boolean rightClosed;
        private final int index;

        private Interval(BigDecimal lower, BigDecimal upper, String boundary, int index) {
            this.lower = lower;
            this.upper = upper;
            this.leftClosed = boundary.startsWith("[");
            this.rightClosed = boundary.endsWith("]");
            this.index = index;
        }
    }
}
