package com.hengshucredit.rule.core.function;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONPath;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 决策引擎通用内置函数：覆盖 JSONPath、对象、数组、字符串和数值加工。
 */
public class DecisionBuiltinFunctions {

    public BigDecimal numAdd(double left, double right) {
        return decimal(left).add(decimal(right));
    }

    public BigDecimal numSub(double left, double right) {
        return decimal(left).subtract(decimal(right));
    }

    public BigDecimal numMul(double left, double right) {
        return decimal(left).multiply(decimal(right));
    }

    public BigDecimal numDiv(double left, double right, double scale) {
        if (right == 0d) {
            return null;
        }
        int effectiveScale = Math.max(0, (int) scale);
        return decimal(left).divide(decimal(right), effectiveScale, RoundingMode.HALF_UP);
    }

    public BigDecimal numRound(double value, double scale) {
        return decimal(value).setScale(Math.max(0, (int) scale), RoundingMode.HALF_UP);
    }

    public BigDecimal numAbs(double value) {
        return decimal(value).abs();
    }

    public double numPow(double value, double exponent) {
        return Math.pow(value, exponent);
    }

    public boolean numBetween(double value, double min, double max) {
        return value >= min && value <= max;
    }

    public int strLength(String text) {
        return text == null ? 0 : text.length();
    }

    public String strTrim(String text) {
        return text == null ? null : text.trim();
    }

    public boolean strContains(String text, String keyword) {
        return text != null && keyword != null && text.contains(keyword);
    }

    public String strReplace(String text, String regex, String replacement) {
        if (text == null || regex == null) {
            return text;
        }
        try {
            return text.replaceAll(regex, replacement == null ? "" : replacement);
        } catch (Exception e) {
            return text;
        }
    }

    public String strRegexExtract(String text, String regex, double groupIndex) {
        if (text == null || regex == null) {
            return null;
        }
        try {
            Matcher matcher = Pattern.compile(regex).matcher(text);
            int index = Math.max(0, (int) groupIndex);
            if (matcher.find() && index <= matcher.groupCount()) {
                return matcher.group(index);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    public List<String> strSplit(String text, String delimiter) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        if (delimiter == null || delimiter.isEmpty()) {
            result.add(text);
            return result;
        }
        String[] parts = text.split(Pattern.quote(delimiter), -1);
        for (String part : parts) {
            result.add(part);
        }
        return result;
    }

    public String strJoin(Object values, String delimiter) {
        String sep = delimiter == null ? "" : delimiter;
        StringBuilder sb = new StringBuilder();
        List<Object> list = toElements(values);
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            Object value = list.get(i);
            if (value != null) {
                sb.append(value);
            }
        }
        return sb.toString();
    }

    public long arrSize(Object values) {
        return sizeOf(values);
    }

    public Object arrGet(Object values, double index) {
        List<Object> list = toElements(values);
        int idx = (int) index;
        if (idx < 0) {
            idx = list.size() + idx;
        }
        return idx >= 0 && idx < list.size() ? list.get(idx) : null;
    }

    public Object arrFirst(Object values) {
        return arrGet(values, 0);
    }

    public Object arrLast(Object values) {
        return arrGet(values, -1);
    }

    public List<Object> arrDistinct(Object values) {
        List<Object> result = new ArrayList<>();
        for (Object item : toElements(values)) {
            boolean exists = false;
            for (Object kept : result) {
                if (valueEquals(kept, item)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                result.add(item);
            }
        }
        return result;
    }

    public List<Object> arrSort(Object values, String direction) {
        List<Object> result = new ArrayList<>(toElements(values));
        final boolean desc = direction != null && "DESC".equalsIgnoreCase(direction.trim());
        result.sort(new Comparator<Object>() {
            @Override
            public int compare(Object left, Object right) {
                int compared = compareValue(left, right);
                return desc ? -compared : compared;
            }
        });
        return result;
    }

    public boolean arrContains(Object values, Object target) {
        for (Object item : toElements(values)) {
            if (valueEquals(item, target)) {
                return true;
            }
        }
        return false;
    }

    public Object jsonParse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return JSON.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    public Object jsonGet(Object json, String path) {
        return readByPath(json, path);
    }

    public List<Object> jsonList(Object json, String path) {
        return toElements(readByPath(json, path));
    }

    public boolean jsonExists(Object json, String path) {
        if (json == null) {
            return false;
        }
        if (path == null || path.trim().isEmpty()) {
            return true;
        }
        try {
            Object source = parseJsonTextIfNeeded(json);
            return JSONPath.contains(source, normalizeJsonPath(path));
        } catch (Exception e) {
            return readByPath(json, path) != null;
        }
    }

    public long jsonCount(Object json, String path) {
        return toElements(readByPath(json, path)).size();
    }

    public BigDecimal jsonSum(Object json, String path) {
        BigDecimal total = BigDecimal.ZERO;
        for (Object item : toElements(readByPath(json, path))) {
            BigDecimal number = toBigDecimal(item);
            if (number != null) {
                total = total.add(number);
            }
        }
        return total;
    }

    public BigDecimal jsonAvg(Object json, String path) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (Object item : toElements(readByPath(json, path))) {
            BigDecimal number = toBigDecimal(item);
            if (number != null) {
                total = total.add(number);
                count++;
            }
        }
        return count == 0 ? null : total.divide(BigDecimal.valueOf(count), 10, RoundingMode.HALF_UP);
    }

    public BigDecimal jsonMin(Object json, String path) {
        BigDecimal best = null;
        for (Object item : toElements(readByPath(json, path))) {
            BigDecimal number = toBigDecimal(item);
            if (number != null && (best == null || number.compareTo(best) < 0)) {
                best = number;
            }
        }
        return best;
    }

    public BigDecimal jsonMax(Object json, String path) {
        BigDecimal best = null;
        for (Object item : toElements(readByPath(json, path))) {
            BigDecimal number = toBigDecimal(item);
            if (number != null && (best == null || number.compareTo(best) > 0)) {
                best = number;
            }
        }
        return best;
    }

    public Object objGet(Object object, String path) {
        return readByPath(object, path);
    }

    public Object objGetOrDefault(Object object, String path, Object fallback) {
        Object value = readByPath(object, path);
        return value == null ? fallback : value;
    }

    public boolean objHas(Object object, String path) {
        return jsonExists(object, path);
    }

    public long objSize(Object object) {
        return sizeOf(parseJsonTextIfNeeded(object));
    }

    public List<Object> objKeys(Object object) {
        Object value = parseJsonTextIfNeeded(object);
        List<Object> keys = new ArrayList<>();
        if (value instanceof Map) {
            keys.addAll(((Map<?, ?>) value).keySet());
        }
        return keys;
    }

    public List<Object> objValues(Object object) {
        Object value = parseJsonTextIfNeeded(object);
        List<Object> values = new ArrayList<>();
        if (value instanceof Map) {
            values.addAll(((Map<?, ?>) value).values());
        }
        return values;
    }

    public String toJson(Object value) {
        return JSON.toJSONString(value);
    }

    private static Object readByPath(Object source, String path) {
        if (source == null) {
            return null;
        }
        if (path == null || path.trim().isEmpty()) {
            return parseJsonTextIfNeeded(source);
        }
        String normalizedPath = normalizeJsonPath(path);
        try {
            if (source instanceof CharSequence) {
                String text = source.toString().trim();
                if (!looksLikeJson(text)) {
                    return null;
                }
                return JSONPath.extract(text, normalizedPath);
            }
            return JSONPath.eval(source, normalizedPath);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object parseJsonTextIfNeeded(Object value) {
        if (value instanceof CharSequence) {
            String text = value.toString().trim();
            if (looksLikeJson(text)) {
                try {
                    return JSON.parse(text);
                } catch (Exception ignored) {
                    return value;
                }
            }
        }
        return value;
    }

    private static String normalizeJsonPath(String path) {
        String text = path == null ? "" : path.trim();
        if (text.isEmpty() || text.startsWith("$")) {
            return text;
        }
        if (text.startsWith("[")) {
            return "$" + text;
        }
        return "$." + text;
    }

    private static boolean looksLikeJson(String text) {
        return text != null && !text.isEmpty()
                && (text.charAt(0) == '{' || text.charAt(0) == '[');
    }

    private static long sizeOf(Object value) {
        if (value == null) {
            return 0;
        }
        Object effective = parseJsonTextIfNeeded(value);
        if (effective instanceof Map) {
            return ((Map<?, ?>) effective).size();
        }
        if (effective instanceof Collection) {
            return ((Collection<?>) effective).size();
        }
        if (effective.getClass().isArray()) {
            return Array.getLength(effective);
        }
        if (effective instanceof CharSequence) {
            return effective.toString().length();
        }
        return 1;
    }

    private static List<Object> toElements(Object value) {
        Object effective = parseJsonTextIfNeeded(value);
        List<Object> result = new ArrayList<>();
        if (effective == null) {
            return result;
        }
        if (effective instanceof Collection) {
            result.addAll((Collection<?>) effective);
            return result;
        }
        if (effective instanceof Map) {
            result.addAll(((Map<?, ?>) effective).values());
            return result;
        }
        if (effective.getClass().isArray()) {
            int len = Array.getLength(effective);
            for (int i = 0; i < len; i++) {
                result.add(Array.get(effective, i));
            }
            return result;
        }
        result.add(effective);
        return result;
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof CharSequence) {
            try {
                return new BigDecimal(value.toString().trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean valueEquals(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        BigDecimal leftNumber = toBigDecimal(left);
        BigDecimal rightNumber = toBigDecimal(right);
        if (leftNumber != null && rightNumber != null) {
            return leftNumber.compareTo(rightNumber) == 0;
        }
        return left.equals(right) || String.valueOf(left).equals(String.valueOf(right));
    }

    private static int compareValue(Object left, Object right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        BigDecimal leftNumber = toBigDecimal(left);
        BigDecimal rightNumber = toBigDecimal(right);
        if (leftNumber != null && rightNumber != null) {
            return leftNumber.compareTo(rightNumber);
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

}
