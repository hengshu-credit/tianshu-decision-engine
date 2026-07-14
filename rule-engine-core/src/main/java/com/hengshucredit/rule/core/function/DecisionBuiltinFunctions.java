package com.hengshucredit.rule.core.function;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONPath;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 决策引擎通用内置函数：覆盖 JSONPath、对象、数组、字符串和数值加工。
 */
public class DecisionBuiltinFunctions {

    private static final String DEFAULT_DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final List<String> DATE_PATTERNS = Arrays.asList(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd",
            "yyyyMMddHHmmss",
            "yyyyMMdd"
    );

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

    public double numMax(double left, double right) {
        return Math.max(left, right);
    }

    public double numMin(double left, double right) {
        return Math.min(left, right);
    }

    public double numSin(double value) {
        return Math.sin(value);
    }

    public double numCos(double value) {
        return Math.cos(value);
    }

    public double numTan(double value) {
        return Math.tan(value);
    }

    public double numCot(double value) {
        return 1d / Math.tan(value);
    }

    public double numLn(double value) {
        return Math.log(value);
    }

    public double numLog10(double value) {
        return Math.log10(value);
    }

    public double numCeil(double value) {
        return Math.ceil(value);
    }

    public double numFloor(double value) {
        return Math.floor(value);
    }

    public double numRoundInteger(double value) {
        return Math.round(value);
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

    public String strSubstring(String text, double start, double end) {
        if (text == null) return null;
        int from = clampIndex((int) start, text.length());
        int to = clampIndex((int) end, text.length());
        return text.substring(Math.min(from, to), Math.max(from, to));
    }

    public String strSubstringFrom(String text, double start) {
        if (text == null) return null;
        return text.substring(clampIndex((int) start, text.length()));
    }

    public String strSubstringTo(String text, double end) {
        if (text == null) return null;
        return text.substring(0, clampIndex((int) end, text.length()));
    }

    public String strLower(String text) {
        return text == null ? null : text.toLowerCase(java.util.Locale.ROOT);
    }

    public String strUpper(String text) {
        return text == null ? null : text.toUpperCase(java.util.Locale.ROOT);
    }

    public String strCharAt(String text, double index) {
        if (text == null) return null;
        int value = (int) index;
        return value >= 0 && value < text.length() ? String.valueOf(text.charAt(value)) : null;
    }

    public long strIndexOf(String text, String target) {
        return text == null || target == null ? -1L : text.indexOf(target);
    }

    public long strLastIndexOf(String text, String target) {
        return text == null || target == null ? -1L : text.lastIndexOf(target);
    }

    public String strReplaceLiteral(String text, String target, String replacement) {
        if (text == null || target == null) return text;
        return text.replace(target, replacement == null ? "" : replacement);
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

    public Object arrMax(Object values) {
        return arrayExtreme(values, true);
    }

    public Object arrMin(Object values) {
        return arrayExtreme(values, false);
    }

    public List<Object> arrAdd(Object values, Object value) {
        List<Object> result = new ArrayList<>(toElements(values));
        result.add(value);
        return result;
    }

    public List<Object> arrRemove(Object values, Object value) {
        List<Object> result = new ArrayList<>();
        for (Object item : toElements(values)) {
            if (!valueEquals(item, value)) result.add(item);
        }
        return result;
    }

    public List<Object> arrSortBy(Object values, final String path, String direction) {
        List<Object> result = new ArrayList<>(toElements(values));
        final boolean desc = direction != null && "DESC".equalsIgnoreCase(direction.trim());
        result.sort(new Comparator<Object>() {
            @Override
            public int compare(Object left, Object right) {
                int compared = compareValue(readByPath(left, path), readByPath(right, path));
                return desc ? -compared : compared;
            }
        });
        return result;
    }

    public List<Object> arrPluck(Object values, String path) {
        List<Object> result = new ArrayList<>();
        for (Object item : toElements(values)) result.add(readByPath(item, path));
        return result;
    }

    public boolean arrIsEmpty(Object values) {
        return toElements(values).isEmpty();
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

    public Map<String, Object> mapPut(Object source, String key, Object value) {
        Map<String, Object> result = toMapValue(source);
        result.put(key, value);
        return result;
    }

    public Map<String, Object> mapRemove(Object source, String key) {
        Map<String, Object> result = toMapValue(source);
        result.remove(key);
        return result;
    }

    public boolean mapHasKey(Object source, String key) {
        return toMapValue(source).containsKey(key);
    }

    public Object mapGet(Object source, String key) {
        return toMapValue(source).get(key);
    }

    public long mapSize(Object source) {
        return toMapValue(source).size();
    }

    public List<Object> mapKeys(Object source) {
        return new ArrayList<Object>(toMapValue(source).keySet());
    }

    public List<Object> mapValues(Object source) {
        return new ArrayList<>(toMapValue(source).values());
    }

    public Map<String, Object> newMap() {
        return new LinkedHashMap<>();
    }

    public List<Object> newList() {
        return new ArrayList<>();
    }

    public Object newLike(Object source) {
        if (source instanceof Map) return new LinkedHashMap<>();
        if (source instanceof Set) return new LinkedHashSet<>();
        if (source instanceof Collection) return new ArrayList<>();
        if (source != null && source.getClass().isArray()) {
            return Array.newInstance(source.getClass().getComponentType(), 0);
        }
        return null;
    }

    public String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public BigDecimal toNumberValue(Object value) {
        BigDecimal result = toBigDecimal(value);
        if (result == null) throw new IllegalArgumentException("无法转换为数值: " + value);
        return result;
    }

    public Boolean toBooleanValue(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return BigDecimal.ZERO.compareTo(toNumberValue(value)) != 0;
        String text = value == null ? "" : String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(text)) return Boolean.FALSE;
        throw new IllegalArgumentException("无法转换为布尔值: " + value);
    }

    public List<Object> toListValue(Object value) {
        return new ArrayList<>(toElements(value));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> toMapValue(Object value) {
        Object parsed = parseJsonTextIfNeeded(value);
        if (parsed == null) return new LinkedHashMap<>();
        if (!(parsed instanceof Map)) throw new IllegalArgumentException("无法转换为 Map: " + value);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) parsed).entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    public String currentDate() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public String currentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(DEFAULT_DATETIME_PATTERN));
    }

    public String dateFormat(Object date, String pattern) {
        LocalDateTime value = toDateTime(date, null);
        if (value == null) {
            return null;
        }
        return formatDateTime(value, pattern);
    }

    public String dateConvert(String text, String fromPattern, String toPattern) {
        LocalDateTime value = toDateTime(text, fromPattern);
        if (value == null) {
            return null;
        }
        return formatDateTime(value, toPattern);
    }

    public String dateAdd(Object date, double amount, String unit) {
        LocalDateTime value = toDateTime(date, null);
        if (value == null) {
            return null;
        }
        return formatDateTime(plusDateTime(value, amount, unit), DEFAULT_DATETIME_PATTERN);
    }

    public String dateSub(Object date, double amount, String unit) {
        return dateAdd(date, -amount, unit);
    }

    public String dateAddParts(Object date, double years, double months, double days,
                               double hours, double minutes, double seconds) {
        LocalDateTime value = toDateTime(date, null);
        if (value == null) return null;
        value = value.plusYears((long) years).plusMonths((long) months).plusDays((long) days)
                .plusHours((long) hours).plusMinutes((long) minutes).plusSeconds((long) seconds);
        return formatDateTime(value, DEFAULT_DATETIME_PATTERN);
    }

    public String dateSubParts(Object date, double years, double months, double days,
                               double hours, double minutes, double seconds) {
        return dateAddParts(date, -years, -months, -days, -hours, -minutes, -seconds);
    }

    public String dateAddYears(Object date, double amount) { return dateAdd(date, amount, "YEAR"); }
    public String dateAddMonths(Object date, double amount) { return dateAdd(date, amount, "MONTH"); }
    public String dateAddDays(Object date, double amount) { return dateAdd(date, amount, "DAY"); }
    public String dateAddHours(Object date, double amount) { return dateAdd(date, amount, "HOUR"); }
    public String dateAddMinutes(Object date, double amount) { return dateAdd(date, amount, "MINUTE"); }
    public String dateAddSeconds(Object date, double amount) { return dateAdd(date, amount, "SECOND"); }
    public String dateSubYears(Object date, double amount) { return dateSub(date, amount, "YEAR"); }
    public String dateSubMonths(Object date, double amount) { return dateSub(date, amount, "MONTH"); }
    public String dateSubDays(Object date, double amount) { return dateSub(date, amount, "DAY"); }
    public String dateSubHours(Object date, double amount) { return dateSub(date, amount, "HOUR"); }
    public String dateSubMinutes(Object date, double amount) { return dateSub(date, amount, "MINUTE"); }
    public String dateSubSeconds(Object date, double amount) { return dateSub(date, amount, "SECOND"); }

    public long dateDiff(Object start, Object end, String unit) {
        LocalDateTime left = toDateTime(start, null);
        LocalDateTime right = toDateTime(end, null);
        if (left == null || right == null) {
            return 0L;
        }
        String normalized = normalizeDateUnit(unit);
        if ("QUARTER".equals(normalized)) {
            return ChronoUnit.MONTHS.between(left, right) / 3L;
        }
        if ("MILLISECOND".equals(normalized)) {
            return ChronoUnit.MILLIS.between(left, right);
        }
        return chronoUnit(normalized).between(left, right);
    }

    public long dateYear(Object date) { return datePart(date, "YEAR"); }
    public long dateMonth(Object date) { return datePart(date, "MONTH"); }
    public long dateWeekday(Object date) { return datePart(date, "WEEKDAY"); }
    public long dateDay(Object date) { return datePart(date, "DAY"); }
    public long dateHour(Object date) { return datePart(date, "HOUR"); }
    public long dateMinute(Object date) { return datePart(date, "MINUTE"); }
    public long dateSecond(Object date) { return datePart(date, "SECOND"); }
    public long dateDiffMillis(Object start, Object end) { return dateDiff(start, end, "MILLISECOND"); }
    public long dateDiffSeconds(Object start, Object end) { return dateDiff(start, end, "SECOND"); }
    public long dateDiffMinutes(Object start, Object end) { return dateDiff(start, end, "MINUTE"); }
    public long dateDiffHours(Object start, Object end) { return dateDiff(start, end, "HOUR"); }
    public long dateDiffDays(Object start, Object end) { return dateDiff(start, end, "DAY"); }
    public long dateDiffWeeks(Object start, Object end) { return dateDiff(start, end, "WEEK"); }

    public List<String> dateDaysInMonths(Object date) {
        LocalDate value = toLocalDate(date);
        if (value == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        LocalDate current = value.withDayOfMonth(1);
        for (int i = 0; i < value.lengthOfMonth(); i++) {
            result.add(current.plusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        return result;
    }

    public List<String> dateDaysOutsideMonths(Object date) {
        LocalDate value = toLocalDate(date);
        if (value == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        LocalDate current = LocalDate.of(value.getYear(), 1, 1);
        int days = current.lengthOfYear();
        for (int i = 0; i < days; i++) {
            LocalDate candidate = current.plusDays(i);
            if (candidate.getMonthValue() != value.getMonthValue()) {
                result.add(candidate.format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
        }
        return result;
    }

    public List<String> dateDaysInSpecifiedMonths(Object start, Object end, String months) {
        return dateDaysByMonths(start, end, months, true);
    }

    public List<String> dateDaysOutsideSpecifiedMonths(Object start, Object end, String months) {
        return dateDaysByMonths(start, end, months, false);
    }

    public long dateToMillis(Object date) {
        LocalDateTime value = toDateTime(date, null);
        if (value == null) {
            return 0L;
        }
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant()).getTime();
    }

    public String millisToDate(double timestamp, String pattern) {
        LocalDateTime value = LocalDateTime.ofInstant(new Date((long) timestamp).toInstant(), ZoneId.systemDefault());
        return formatDateTime(value, pattern);
    }

    public String idCardBirthDate(String idCard) {
        LocalDate birthDate = idCardBirthLocalDate(idCard);
        return birthDate == null ? null : birthDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public long idCardAge(String idCard, Object currentDate, String calcMode) {
        LocalDate birthDate = idCardBirthLocalDate(idCard);
        LocalDate effectiveDate = currentDate == null ? LocalDate.now() : toLocalDate(currentDate);
        if (birthDate == null || effectiveDate == null || birthDate.isAfter(effectiveDate)) {
            return -1L;
        }
        int years = effectiveDate.getYear() - birthDate.getYear();
        if (calcMode != null && "YEAR".equalsIgnoreCase(calcMode.trim())) {
            return years;
        }
        return effectiveDate.isBefore(birthDate.plusYears(years)) ? years - 1L : years;
    }

    public BigDecimal scoreByOdds(double odds, double a, double b, String direction) {
        if (odds <= 0d) {
            return null;
        }
        double sign = scoreDirectionSign(direction);
        return decimal(a).add(decimal(sign * b * Math.log(odds))).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal scoreByProbability(double probability, double a, double b, String direction) {
        if (probability <= 0d || probability >= 1d) {
            return null;
        }
        double logOdds = Math.log(probability / (1d - probability));
        return decimal(a).add(decimal(probabilityDirectionSign(direction) * b * logOdds))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal scoreByOddsPdo(double odds, double baseScore, double baseOdds, double pdo, String direction) {
        if (odds <= 0d || baseOdds <= 0d || pdo == 0d) {
            return null;
        }
        double b = pdo / Math.log(2d);
        double sign = scoreDirectionSign(direction);
        return decimal(baseScore).add(decimal(sign * b * Math.log(odds / baseOdds))).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal scoreByBadRatePdo(double badRate, double baseScore, double baseOdds, double pdo, String direction) {
        if (badRate <= 0d || badRate >= 1d) {
            return null;
        }
        double odds = (1d - badRate) / badRate;
        return scoreByOddsPdo(odds, baseScore, baseOdds, pdo, direction);
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

    private static int clampIndex(int value, int length) {
        if (value < 0) return 0;
        return Math.min(value, length);
    }

    private static Object arrayExtreme(Object values, boolean max) {
        Object selected = null;
        for (Object item : toElements(values)) {
            if (selected == null || (max ? compareValue(item, selected) > 0 : compareValue(item, selected) < 0)) {
                selected = item;
            }
        }
        return selected;
    }

    private static long datePart(Object date, String part) {
        LocalDateTime value = toDateTime(date, null);
        if (value == null) return 0L;
        if ("YEAR".equals(part)) return value.getYear();
        if ("MONTH".equals(part)) return value.getMonthValue();
        if ("WEEKDAY".equals(part)) return value.getDayOfWeek().getValue();
        if ("DAY".equals(part)) return value.getDayOfMonth();
        if ("HOUR".equals(part)) return value.getHour();
        if ("MINUTE".equals(part)) return value.getMinute();
        return value.getSecond();
    }

    private static List<String> dateDaysByMonths(Object start, Object end, String months, boolean included) {
        LocalDate from = toLocalDate(start);
        LocalDate to = toLocalDate(end);
        List<String> result = new ArrayList<>();
        if (from == null || to == null) return result;
        if (from.isAfter(to)) {
            LocalDate swap = from;
            from = to;
            to = swap;
        }
        Set<Integer> selected = new LinkedHashSet<>();
        if (months != null) {
            for (String item : months.split(",")) {
                try {
                    int month = Integer.parseInt(item.trim());
                    if (month >= 1 && month <= 12) selected.add(month);
                } catch (NumberFormatException ignored) {
                    // 忽略无效月份，其余有效月份仍继续计算。
                }
            }
        }
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            if (selected.contains(cursor.getMonthValue()) == included) {
                result.add(cursor.format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
        }
        return result;
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

    private static LocalDateTime plusDateTime(LocalDateTime value, double amount, String unit) {
        long delta = (long) amount;
        String normalized = normalizeDateUnit(unit);
        if ("QUARTER".equals(normalized)) {
            return value.plusMonths(delta * 3L);
        }
        if ("MILLISECOND".equals(normalized)) {
            return value.plus(delta, ChronoUnit.MILLIS);
        }
        return value.plus(delta, chronoUnit(normalized));
    }

    private static ChronoUnit chronoUnit(String unit) {
        switch (normalizeDateUnit(unit)) {
            case "YEAR": return ChronoUnit.YEARS;
            case "MONTH": return ChronoUnit.MONTHS;
            case "WEEK": return ChronoUnit.WEEKS;
            case "DAY": return ChronoUnit.DAYS;
            case "HOUR": return ChronoUnit.HOURS;
            case "MINUTE": return ChronoUnit.MINUTES;
            case "SECOND": return ChronoUnit.SECONDS;
            default: return ChronoUnit.DAYS;
        }
    }

    private static String normalizeDateUnit(String unit) {
        String text = unit == null ? "" : unit.trim().toUpperCase();
        if ("YEAR".equals(text) || "YEARS".equals(text) || "Y".equals(text) || "年".equals(text)) return "YEAR";
        if ("QUARTER".equals(text) || "QUARTERS".equals(text) || "Q".equals(text) || "季".equals(text) || "季度".equals(text)) return "QUARTER";
        if ("MONTH".equals(text) || "MONTHS".equals(text) || "M".equals(text) || "月".equals(text)) return "MONTH";
        if ("WEEK".equals(text) || "WEEKS".equals(text) || "W".equals(text) || "周".equals(text) || "星期".equals(text)) return "WEEK";
        if ("DAY".equals(text) || "DAYS".equals(text) || "D".equals(text) || "日".equals(text) || "天".equals(text)) return "DAY";
        if ("HOUR".equals(text) || "HOURS".equals(text) || "H".equals(text) || "小时".equals(text)) return "HOUR";
        if ("MINUTE".equals(text) || "MINUTES".equals(text) || "MI".equals(text) || "N".equals(text) || "分钟".equals(text)) return "MINUTE";
        if ("SECOND".equals(text) || "SECONDS".equals(text) || "S".equals(text) || "秒".equals(text)) return "SECOND";
        if ("MILLISECOND".equals(text) || "MILLISECONDS".equals(text) || "MILLIS".equals(text) || "MS".equals(text) || "毫秒".equals(text)) return "MILLISECOND";
        return "DAY";
    }

    private static String formatDateTime(LocalDateTime value, String pattern) {
        String effectivePattern = pattern == null || pattern.trim().isEmpty() ? DEFAULT_DATETIME_PATTERN : pattern.trim();
        try {
            return value.format(java.time.format.DateTimeFormatter.ofPattern(effectivePattern));
        } catch (Exception e) {
            return value.format(java.time.format.DateTimeFormatter.ofPattern(DEFAULT_DATETIME_PATTERN));
        }
    }

    private static LocalDateTime toDateTime(Object value, String pattern) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof Date) {
            return LocalDateTime.ofInstant(((Date) value).toInstant(), ZoneId.systemDefault());
        }
        if (value instanceof Number) {
            return LocalDateTime.ofInstant(new Date(((Number) value).longValue()).toInstant(), ZoneId.systemDefault());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.matches("^-?\\d{10,}$")) {
            try {
                return LocalDateTime.ofInstant(new Date(Long.parseLong(text)).toInstant(), ZoneId.systemDefault());
            } catch (Exception ignored) {
                return null;
            }
        }
        if (pattern != null && !pattern.trim().isEmpty()) {
            return parseDateTime(text, pattern.trim());
        }
        for (String candidate : DATE_PATTERNS) {
            LocalDateTime parsed = parseDateTime(text, candidate);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        LocalDateTime dateTime = toDateTime(value, null);
        return dateTime == null ? null : dateTime.toLocalDate();
    }

    private static LocalDate idCardBirthLocalDate(String idCard) {
        if (idCard == null) {
            return null;
        }
        String text = idCard.trim();
        String birthText;
        if (text.matches("[0-9]{17}[0-9Xx]")) {
            birthText = text.substring(6, 14);
        } else if (text.matches("[0-9]{15}")) {
            birthText = "19" + text.substring(6, 12);
        } else {
            return null;
        }
        try {
            return LocalDate.parse(birthText, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LocalDateTime parseDateTime(String text, String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern);
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date date = format.parse(text, position);
        if (date == null || position.getIndex() != text.length()) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private static double scoreDirectionSign(String direction) {
        String text = direction == null ? "" : direction.trim().toUpperCase();
        return ("DESC".equals(text) || "DOWN".equals(text) || "REVERSE".equals(text) || "降序".equals(text) || "下降".equals(text)) ? -1d : 1d;
    }

    private static double probabilityDirectionSign(String direction) {
        String text = direction == null ? "" : direction.trim().toUpperCase();
        return ("LOW_GOOD".equals(text) || "DESC".equals(text) || "越小越好".equals(text)) ? 1d : -1d;
    }

}
