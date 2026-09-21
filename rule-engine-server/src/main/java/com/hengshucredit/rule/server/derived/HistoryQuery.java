package com.hengshucredit.rule.server.derived;

import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.server.functions.GeoFunctions;
import com.hengshucredit.rule.server.service.OperandValueResolver;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static com.hengshucredit.rule.server.derived.DerivedVariableConfig.*;

/** 在已按时间和作用范围筛选的进件快照上执行有界关联；每层是半连接，不复制命中记录。 */
public final class HistoryQuery {
    public record Row(long id, LocalDateTime occurredAt, Map<String, Object> fields) { }
    private static final MathContext PRECISION = MathContext.DECIMAL128;

    private HistoryQuery() { }

    public static Object evaluate(JSONObject config, List<Row> history,
                                  Function<JSONObject, Object> input,
                                  OperandValueResolver.FunctionInvoker functions) {
        validate(config);
        JSONObject geo = config.getJSONObject("geo");
        if (geo != null) validateGeo(geo, input);
        List<Row> selected = history;
        List<JSONObject> steps = objects(config, "steps");
        for (int i = 0; i < steps.size(); i++) {
            JSONObject step = steps.get(i);
            Set<List<Object>> wanted = new LinkedHashSet<>();
            if (i == 0) {
                wanted.addAll(keys(objects(step, "inputs").stream().map(input).toList()));
            } else {
                for (Row row : selected) wanted.addAll(fieldKeys(row, objects(step, "fromFields")));
            }
            List<JSONObject> targetFields = objects(step, "fields");
            List<Row> matches = new ArrayList<>();
            for (Row row : history) {
                if (fieldKeys(row, targetFields).stream().anyMatch(wanted::contains)
                        && matchesFilters(row, step, input)) matches.add(row);
            }
            selected = matches;
        }
        List<Row> filtered = new ArrayList<>();
        for (Row row : selected) {
            if (matchesFilters(row, config, input) && matchesGeo(row, config.getJSONObject("geo"), input)) filtered.add(row);
        }
        List<JSONObject> subjects = objects(config, "subjectFields");
        if ("LATEST_PER_SUBJECT".equals(config.getString("recordMode"))) {
            Map<List<Object>, Row> latest = new LinkedHashMap<>();
            Comparator<Row> newer = Comparator.comparing(Row::occurredAt).thenComparingLong(Row::id);
            for (Row row : filtered) {
                Set<List<Object>> keys = fieldKeys(row, subjects);
                require(keys.size() <= 1, "主体 key 必须是标量字段，不能是多值列表");
                for (List<Object> key : keys) latest.merge(key, row, (a, b) -> newer.compare(a, b) >= 0 ? a : b);
            }
            filtered = new ArrayList<>(latest.values());
        }
        String aggregate = config.getString("aggregate");
        if ("COUNT".equals(aggregate)) return (long) filtered.size();
        if ("DISTINCT_COUNT".equals(aggregate)) {
            Set<List<Object>> unique = new LinkedHashSet<>();
            for (Row row : filtered) unique.addAll(fieldKeys(row, subjects));
            return (long) unique.size();
        }
        List<Object> values = new ArrayList<>();
        for (Row row : filtered) {
            Object value = value(row, config.getJSONObject("valueField"));
            if (value != null) values.add(value);
        }
        if ("CUSTOM".equals(aggregate)) {
            require(functions != null, "自定义聚合函数执行器不可用");
            return functions.invoke(config.getLong("functionId"), null, List.of(values));
        }
        if ("STRING_LENGTH".equals(aggregate)) {
            long length = 0;
            for (Object value : values) {
                require(value instanceof String, "字符串长度只支持字符串字段");
                String text = (String) value;
                length = Math.addExact(length, text.codePointCount(0, text.length()));
            }
            return length;
        }
        if ("MIN".equals(aggregate) || "MAX".equals(aggregate)) {
            Object result = null;
            for (Object value : values) {
                if (result == null || ("MIN".equals(aggregate) ? compare(value, result) < 0 : compare(value, result) > 0)) result = value;
            }
            return result;
        }
        List<BigDecimal> numbers = values.stream().map(HistoryQuery::number).toList();
        BigDecimal sum = numbers.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if ("SUM".equals(aggregate)) return sum;
        if (numbers.isEmpty()) return null;
        BigDecimal mean = sum.divide(BigDecimal.valueOf(numbers.size()), PRECISION);
        if ("AVG".equals(aggregate)) return mean;
        boolean sample = aggregate.startsWith("SAMPLE_");
        if (sample && numbers.size() < 2) return null;
        BigDecimal squared = BigDecimal.ZERO;
        for (BigDecimal value : numbers) {
            BigDecimal delta = value.subtract(mean);
            squared = squared.add(delta.multiply(delta, PRECISION));
        }
        BigDecimal variance = squared.divide(BigDecimal.valueOf(numbers.size() - (sample ? 1 : 0)), PRECISION);
        return aggregate.endsWith("STDDEV") ? variance.sqrt(PRECISION) : variance;
    }

    private static boolean matchesFilters(Row row, JSONObject config, Function<JSONObject, Object> input) {
        for (JSONObject filter : objects(config, "filters")) {
            Object actual = value(row, filter.getJSONObject("field"));
            String operator = filter.getString("operator");
            if ("IS_NULL".equals(operator)) { if (actual != null) return false; continue; }
            if ("NOT_NULL".equals(operator)) { if (actual == null) return false; continue; }
            Object expected = input.apply(filter.getJSONObject("value"));
            if (actual == null || expected == null) return false;
            int comparison = compare(actual, expected);
            boolean matched = switch (operator) {
                case "EQ" -> comparison == 0;
                case "NE" -> comparison != 0;
                case "GT" -> comparison > 0;
                case "GE" -> comparison >= 0;
                case "LT" -> comparison < 0;
                case "LE" -> comparison <= 0;
                default -> false;
            };
            if (!matched) return false;
        }
        return true;
    }

    private static boolean matchesGeo(Row row, JSONObject geo, Function<JSONObject, Object> input) {
        if (geo == null) return true;
        double longitude = number(input.apply(geo.getJSONObject("longitude"))).doubleValue();
        double latitude = number(input.apply(geo.getJSONObject("latitude"))).doubleValue();
        double radius = number(input.apply(geo.getJSONObject("radiusMeters"))).doubleValue();
        require(Double.isFinite(radius) && radius >= 0, "地理半径必须是有限的非负米数");
        GeoFunctions functions = new GeoFunctions();
        functions.distanceMeters(longitude, latitude, longitude, latitude);
        Object rowLongitude = value(row, geo.getJSONObject("longitudeField"));
        Object rowLatitude = value(row, geo.getJSONObject("latitudeField"));
        if (rowLongitude == null || rowLatitude == null) return false;
        return functions.distanceMeters(longitude, latitude,
                number(rowLongitude).doubleValue(), number(rowLatitude).doubleValue()) <= radius + 0.000001;
    }

    private static void validateGeo(JSONObject geo, Function<JSONObject, Object> input) {
        double longitude = number(input.apply(geo.getJSONObject("longitude"))).doubleValue();
        double latitude = number(input.apply(geo.getJSONObject("latitude"))).doubleValue();
        double radius = number(input.apply(geo.getJSONObject("radiusMeters"))).doubleValue();
        require(Double.isFinite(radius) && radius >= 0, "地理半径必须是有限的非负米数");
        new GeoFunctions().distanceMeters(longitude, latitude, longitude, latitude);
    }

    private static Object value(Row row, JSONObject field) {
        return row.fields().get(fieldKey(field));
    }

    private static Set<List<Object>> fieldKeys(Row row, List<JSONObject> fields) {
        return keys(fields.stream().map(field -> value(row, field)).toList());
    }

    private static Set<List<Object>> keys(List<Object> parts) {
        Set<List<Object>> result = new LinkedHashSet<>();
        if (parts.size() == 1 && parts.get(0) instanceof Collection<?> values) {
            for (Object value : values) result.addAll(keys(java.util.Collections.singletonList(value)));
            return result;
        }
        if (parts.isEmpty()) return result;
        List<Object> key = new ArrayList<>();
        for (Object part : parts) {
            if (part == null || part instanceof String text && text.isBlank()) return result;
            require(!(part instanceof Collection<?>) && !(part instanceof Map<?, ?>),
                    "联合 key 只支持标量字段；列表关联请单独配置一步，避免破坏列表元素对应关系");
            key.add(part instanceof Number ? number(part).stripTrailingZeros() : part);
        }
        result.add(List.copyOf(key));
        return result;
    }

    private static BigDecimal number(Object value) {
        require(value instanceof Number, "数值统计只支持数值字段，不会自动把字符串转成数值");
        try { return new BigDecimal(value.toString()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("统计值必须是有限数值", exception); }
    }

    private static int compare(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) return number(left).compareTo(number(right));
        if (left instanceof String a && right instanceof String b) return a.compareTo(b);
        if (left instanceof Boolean a && right instanceof Boolean b) return a.compareTo(b);
        throw new IllegalArgumentException("筛选或排序的字段类型不兼容");
    }
}
