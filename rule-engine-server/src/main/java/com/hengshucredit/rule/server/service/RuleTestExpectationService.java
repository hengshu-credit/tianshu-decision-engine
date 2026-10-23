package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.ApiResponse;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hengshucredit.rule.model.dto.ResolvedField;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.dto.TestExpectationResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 测试专用静态预期比较。只解释字段ID、比较方式和字面量，不编译或执行QL。 */
@Service
public class RuleTestExpectationService {
    private static final Object MISSING = new Object();
    private static final int MAX_CHECKS = 100;
    private static final int MAX_DIFFS = 100;
    private static final ObjectMapper JSON_READER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final Object SNAPSHOT_ROOT = new Object();
    private static final ObjectWriter EXECUTION_SNAPSHOT_WRITER = JSON_READER.copy()
            .addMixIn(RuleResult.class, ExecutionSnapshotMixin.class)
            .writer(new SimpleFilterProvider().addFilter("executionSnapshot", new SimpleBeanPropertyFilter() {
                @Override public void serializeAsField(Object bean, JsonGenerator generator,
                        SerializerProvider provider, PropertyWriter writer) throws Exception {
                    if (bean == provider.getAttribute(SNAPSHOT_ROOT) && "traces".equals(writer.getName())) return;
                    writer.serializeAsField(bean, generator, provider);
                }
            }));
    private static final Set<String> VALUE_FREE = Set.of("is_null", "not_null", "is_missing", "exists");
    private static final Set<String> OPERATORS = Set.of("==", "!=", ">", ">=", "<", "<=", "between", "not_between",
            "in", "not_in", "contains", "not_contains", "contains_any", "contains_all", "is_null", "not_null", "is_missing", "exists");

    public void validateConfiguration(String expectationJson) {
        if (expectationJson == null) return;
        configuration(expectationJson);
    }

    public boolean needsOutputSchema(String expectationJson) {
        if (expectationJson == null) return false;
        try { return "FIELDS".equals(object(parse(expectationJson), "预期配置格式无效").get("mode")); }
        catch (IllegalArgumentException invalid) { return false; }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> requestParams(String requestJson) {
        Map<String, Object> request = object(parse(requestJson), "请求报文必须是JSON对象");
        if (!request.containsKey("params")) return new LinkedHashMap<>();
        return new LinkedHashMap<>(object(request.get("params"), "请求params必须是JSON对象"));
    }

    public Map<String, Object> readJsonObject(String json, String label) {
        return object(parse(json), label + "必须是JSON对象");
    }

    public Long requestProjectId(String requestJson) {
        Object value = readJsonObject(requestJson, "请求报文").get("projectId");
        if (value == null) return null;
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("项目ID必须是有效整数"); }
    }

    public String writeJson(Object value) {
        try { return JSON_READER.writeValueAsString(value); }
        catch (Exception invalid) { throw new IllegalArgumentException("结果无法保存为有效JSON"); }
    }

    /** 业务结果快照沿用结果封装，不是HTTP原文；调试追踪由DTO.execution单独携带。 */
    public String writeExecutionSnapshot(RuleResult execution) {
        try {
            // ObjectWriter属性属于本次写入，不修改共享执行对象或通用ObjectMapper。
            return EXECUTION_SNAPSHOT_WRITER.withAttribute(SNAPSHOT_ROOT, execution).writeValueAsString(ApiResponse.ok(execution));
        } catch (Exception invalid) {
            throw new IllegalArgumentException("结果无法保存为有效JSON");
        }
    }

    @JsonFilter("executionSnapshot")
    private abstract static class ExecutionSnapshotMixin { }

    public TestExpectationResult evaluate(String expectationJson, String expectedResponseJson,
                                          String actualResponseJson, List<ResolvedField> outputs) {
        try {
            Map<String, Object> config = expectationJson == null ? Map.of("mode", "SNAPSHOT") : configuration(expectationJson);
            String mode = text(config.get("mode"));
            if ("NONE".equals(mode)) return result("NOT_CONFIGURED", "未设置预期，不判断用例是否通过");
            if ("SNAPSHOT".equals(mode)) return compareSnapshots(expectedResponseJson, actualResponseJson);
            List<?> checks = list(config.get("checks"), "字段校验列表格式无效");
            if (checks.isEmpty()) return result("NOT_CONFIGURED", "尚未添加输出字段预期");
            Map<String, Object> actual = payload(parse(actualResponseJson));
            // 执行失败与断言失败是不同事实；没有业务输出时不把字段断言误报为通过。
            if (!Boolean.TRUE.equals(actual.get("success"))) return result("NOT_EVALUATED", "执行未成功，未进行输出字段校验");
            Object output = output(actual);
            TestExpectationResult result = result("PASSED", "所有输出字段预期均满足");
            for (Object entry : checks) {
                Map<String, Object> check = object(entry, "字段校验项格式无效");
                ResolvedField field = resolveOutput(check, outputs);
                String operator = text(check.get("operator"));
                String type = text(check.get("valueType"));
                Object expected = VALUE_FREE.contains(operator) ? MISSING : expected(check.get("expected"), type, operator);
                Object value = output instanceof Map<?, ?> ? pathValue(output, firstText(field.getScriptName(), field.getCode()))
                        : outputs != null && outputs.size() == 1 ? output : MISSING;
                boolean passed = matches(value, expected, type, operator);
                if (!passed) result.setStatus("FAILED");
                result.getChecks().add(new TestExpectationResult.Check(field.getRefType(), String.valueOf(field.getRefId()),
                        firstText(field.getLabel(), field.getCode()), operator, passed ? "PASSED" : "FAILED",
                        passed ? "符合预期" : value == MISSING ? "本次输出缺少该字段" : "实际输出不符合预期",
                        expected == MISSING ? null : writeJson(expected), value == MISSING ? null : writeJson(value), value == MISSING));
            }
            if ("FAILED".equals(result.getStatus())) result.setMessage("存在不符合预期的输出字段");
            return result;
        } catch (RuntimeException invalid) {
            return result("INVALID", invalid.getMessage() == null ? "预期配置或结果格式无效" : invalid.getMessage());
        }
    }

    public TestExpectationResult compareSnapshots(String expectedJson, String actualJson) {
        try {
            if (expectedJson == null || expectedJson.isBlank()) return result("NOT_CONFIGURED", "未保存JSON预期快照");
            Map<String, Object> expected = payload(parse(expectedJson));
            if (!expected.containsKey("success") && output(expected) == MISSING
                    && !expected.containsKey("errorMessage") && !expected.containsKey("error")) {
                return result("NOT_CONFIGURED", "JSON快照没有可比较的执行结果");
            }
            TestExpectationResult result = result("PASSED", "与JSON预期快照一致");
            Map<String, Object> expectedView = comparable(expected);
            Map<String, Object> actualView = comparable(payload(parse(actualJson)));
            // 先检查完整业务值，不能因缺字段/类型不同而提前结束比较并绕过深度预算。
            for (Object value : expectedView.values()) validateSnapshotDepth(value, 0);
            for (Object value : actualView.values()) validateSnapshotDepth(value, 0);
            // R/data/comparable只是传输或比较外壳；每个业务值自身从depth=0开始。
            appendDiffs(expectedView, actualView, "$", result, -1);
            if (!result.getDiffs().isEmpty()) {
                result.setStatus("FAILED");
                result.setMessage(result.isTruncated() ? "与JSON预期快照不同，仅展示前100项差异" : "与JSON预期快照不同");
            }
            return result;
        } catch (RuntimeException invalid) {
            return result("INVALID", invalid.getMessage() == null ? "JSON快照格式无效" : invalid.getMessage());
        }
    }

    private Map<String, Object> configuration(String json) {
        Map<String, Object> config = object(parse(json), "预期配置必须是JSON对象");
        if (!(config.get("version") instanceof Number version) || decimal(version).compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("不支持的预期配置版本");
        }
        String mode = text(config.get("mode"));
        if (!Set.of("NONE", "FIELDS", "SNAPSHOT").contains(mode)) throw new IllegalArgumentException("请选择不校验、字段预期或JSON快照");
        if (!"FIELDS".equals(mode)) return config;
        List<?> checks = list(config.get("checks"), "字段校验列表格式无效");
        if (checks.size() > MAX_CHECKS) throw new IllegalArgumentException("每个用例最多配置100项输出校验");
        for (Object entry : checks) {
            Map<String, Object> check = object(entry, "字段校验项格式无效");
            refId(check.get("refId"));
            if (text(check.get("refType")).isBlank() || text(check.get("valueType")).isBlank()) throw new IllegalArgumentException("预期字段必须保留引用类型、ID和数据类型");
            String operator = text(check.get("operator"));
            if (!OPERATORS.contains(operator)) throw new IllegalArgumentException("不支持的预期比较方式");
            validateOperator(text(check.get("valueType")), operator);
            if (!VALUE_FREE.contains(operator)) expected(check.get("expected"), text(check.get("valueType")), operator);
        }
        return config;
    }

    private ResolvedField resolveOutput(Map<String, Object> check, List<ResolvedField> outputs) {
        long id = refId(check.get("refId"));
        String refType = text(check.get("refType"));
        List<ResolvedField> matches = outputs == null ? List.of() : outputs.stream().filter(field ->
                Objects.equals(field.getRefId(), id) && refType.equalsIgnoreCase(field.getRefType())).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("预期字段引用已失效或不唯一：" + refType + ':' + id + "，请重新选择输出字段");
        ResolvedField field = matches.get(0);
        if (!text(check.get("valueType")).equalsIgnoreCase(field.getValueType())) {
            throw new IllegalArgumentException("输出字段类型已变化：" + firstText(field.getLabel(), field.getCode()) + "，请重新确认预期");
        }
        if (firstText(field.getScriptName(), field.getCode()) == null) throw new IllegalArgumentException("输出字段没有可读取路径，请检查规则输出配置");
        return field;
    }

    private void validateOperator(String type, String operator) {
        if (VALUE_FREE.contains(operator) || "==".equals(operator) || "!=".equals(operator)) return;
        boolean allowed;
        if (numeric(type)) allowed = Set.of(">", ">=", "<", "<=", "between", "not_between", "in", "not_in").contains(operator);
        else if (dateType(type)) allowed = Set.of(">", ">=", "<", "<=", "between", "not_between").contains(operator);
        else if (Set.of("LIST", "ARRAY", "VECTOR").contains(type.toUpperCase(Locale.ROOT))) allowed = Set.of("contains", "not_contains", "contains_any", "contains_all").contains(operator);
        else if (Set.of("STRING", "ENUM").contains(type.toUpperCase(Locale.ROOT))) allowed = Set.of("contains", "not_contains", "in", "not_in").contains(operator);
        else allowed = Set.of("BOOLEAN", "BOOL").contains(type.toUpperCase(Locale.ROOT)) && Set.of("in", "not_in").contains(operator);
        if (!allowed) throw new IllegalArgumentException("该数据类型不支持所选比较方式");
    }

    private long refId(Object value) {
        try {
            long id = Long.parseLong(String.valueOf(value));
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("请选择具有稳定ID的输出字段");
    }

    private Object expected(Object operandValue, String type, String operator) {
        Map<String, Object> operand = object(operandValue, "请填写预期值");
        if ("between".equals(operator) || "not_between".equals(operator)) {
            if (!"LITERAL".equals(operand.get("kind"))) throw new IllegalArgumentException("区间预期必须填写两个固定边界");
            String[] parts = text(operand.get("value")).split(",", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) throw new IllegalArgumentException("请完整填写区间下限和上限");
            Object lower = literal(parts[0].trim(), type), upper = literal(parts[1].trim(), type);
            if (ordered(lower, upper, type) > 0) throw new IllegalArgumentException("区间下限不能大于上限");
            return List.of(lower, upper);
        }
        if (Set.of("in", "not_in", "contains_any", "contains_all").contains(operator)
                || (Set.of("LIST", "ARRAY", "VECTOR").contains(type.toUpperCase(Locale.ROOT))
                && Set.of("==", "!=").contains(operator) && "ARRAY".equals(operand.get("kind")))) {
            if (!"ARRAY".equals(operand.get("kind"))) throw new IllegalArgumentException("集合预期请逐项配置固定值");
            List<?> members = list(operand.get("items"), "集合预期格式无效");
            if (members.isEmpty() || members.size() > 1000) throw new IllegalArgumentException("集合预期需要1至1000个成员");
            List<Object> values = new ArrayList<>();
            for (Object item : members) values.add(literalOperand(object(item, "集合成员格式无效")));
            return values;
        }
        String expectedType = "CAST".equals(operand.get("kind")) ? text(operand.get("targetType")) : text(operand.get("valueType"));
        boolean listMember = "LIST".equals(family(type)) && Set.of("contains", "not_contains").contains(operator);
        if (!listMember && !family(type).equals(family(expectedType))) {
            throw new IllegalArgumentException("预期值类型与输出字段不一致，请重新确认预期");
        }
        return literalOperand(operand);
    }

    private Object literalOperand(Map<String, Object> operand) {
        if ("CAST".equals(operand.get("kind")) && "NUMBER".equals(operand.get("targetType"))) {
            Map<String, Object> raw = object(operand.get("operand"), "数值预期格式无效");
            if (!"LITERAL".equals(raw.get("kind")) || !"STRING".equals(raw.get("valueType"))) throw new IllegalArgumentException("预期不支持字段引用、函数或QL表达式");
            return decimal(raw.get("value"));
        }
        if (!"LITERAL".equals(operand.get("kind")) || !operand.containsKey("value")) throw new IllegalArgumentException("预期必须是固定值，不支持字段引用、函数或QL表达式");
        return literal(operand.get("value"), text(operand.get("valueType")));
    }

    private Object literal(Object value, String type) {
        if (value == null) return null;
        if (numeric(type)) return decimal(value);
        if ("BOOLEAN".equalsIgnoreCase(type) || "BOOL".equalsIgnoreCase(type)) {
            if (Boolean.TRUE.equals(value) || "true".equals(value)) return true;
            if (Boolean.FALSE.equals(value) || "false".equals(value)) return false;
            throw new IllegalArgumentException("布尔预期只能选择true或false");
        }
        if (dateType(type)) { dateMillis(value); return String.valueOf(value); }
        if (Set.of("LIST", "ARRAY", "MAP", "OBJECT").contains(type.toUpperCase(Locale.ROOT))) {
            Object parsed = value instanceof String text ? parse(text) : value;
            if (!(parsed instanceof Map) && !(parsed instanceof List)) throw new IllegalArgumentException("集合或对象预期必须使用有效JSON");
            return parsed;
        }
        return String.valueOf(value);
    }

    private boolean matches(Object actual, Object expected, String type, String operator) {
        if ("is_missing".equals(operator)) return actual == MISSING;
        if ("exists".equals(operator)) return actual != MISSING;
        if ("is_null".equals(operator)) return actual == null;
        if ("not_null".equals(operator)) return actual != null && actual != MISSING;
        if (actual == MISSING) return false;
        // 空值不因负向操作被判为符合业务值预期；显式等于null仍保持兼容。
        if (actual == null) return "==".equals(operator) && expected == null;
        if (!actualTypeMatches(actual, type)) return false;
        if ("==".equals(operator) || "!=".equals(operator)) {
            boolean equal = dateType(type) && actual != null && expected != null
                    ? ordered(actual, expected, type) == 0 : same(actual, expected, 0);
            return "==".equals(operator) == equal;
        }
        if ("in".equals(operator) || "not_in".equals(operator)) {
            boolean found = ((List<?>) expected).stream().anyMatch(item -> same(actual, item, 0));
            return "in".equals(operator) == found;
        }
        if ("contains_any".equals(operator) || "contains_all".equals(operator)) {
            if (!(actual instanceof List<?> values)) return false;
            var stream = ((List<?>) expected).stream();
            return "contains_all".equals(operator) ? stream.allMatch(item -> values.stream().anyMatch(value -> same(value, item, 0)))
                    : stream.anyMatch(item -> values.stream().anyMatch(value -> same(value, item, 0)));
        }
        if ("contains".equals(operator) || "not_contains".equals(operator)) {
            boolean found = actual instanceof List<?> values ? values.stream().anyMatch(item -> same(item, expected, 0))
                    : actual instanceof String value && expected instanceof String part && value.contains(part);
            return "contains".equals(operator) == found;
        }
        if ("between".equals(operator) || "not_between".equals(operator)) {
            List<?> bounds = (List<?>) expected;
            boolean found = ordered(actual, bounds.get(0), type) >= 0 && ordered(actual, bounds.get(1), type) <= 0;
            return "between".equals(operator) == found;
        }
        int compared = ordered(actual, expected, type);
        return switch (operator) {
            case ">" -> compared > 0; case ">=" -> compared >= 0;
            case "<" -> compared < 0; case "<=" -> compared <= 0;
            default -> throw new IllegalArgumentException("不支持的预期比较方式");
        };
    }

    private int ordered(Object left, Object right, String type) {
        if (dateType(type)) return Long.compare(dateMillis(left), dateMillis(right));
        if (!numeric(type) || !(left instanceof Number) || !(right instanceof Number)) throw new IllegalArgumentException("大小和区间比较要求实际输出与预期为数值或日期");
        return decimal(left).compareTo(decimal(right));
    }

    private long dateMillis(Object value) {
        if (value instanceof Number) {
            try { return decimal(value).longValueExact(); }
            catch (ArithmeticException invalid) { throw new IllegalArgumentException("日期毫秒值必须是有效整数"); }
        }
        String text = String.valueOf(value).trim();
        try { return OffsetDateTime.parse(text).toInstant().toEpochMilli(); }
        catch (DateTimeParseException ignored) { /* 无时区值沿用下方的本地时区语义。 */ }
        if (text.matches("^-?\\d{10,}$")) {
            try { return Long.parseLong(text); } catch (NumberFormatException ignored) { }
        } else {
            for (String pattern : List.of("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd", "yyyyMMdd")) {
                SimpleDateFormat parser = new SimpleDateFormat(pattern); parser.setLenient(false);
                ParsePosition position = new ParsePosition(0); Date parsed = parser.parse(text, position);
                if (parsed != null && position.getIndex() == text.length()) return parsed.getTime();
            }
        }
        throw new IllegalArgumentException("日期预期或实际输出格式无效");
    }

    private boolean numeric(String type) {
        return Set.of("NUMBER", "BYTE", "SHORT", "INT", "INTEGER", "LONG", "FLOAT", "DOUBLE", "DECIMAL", "BIGDECIMAL", "PROBABILITY").contains(type.toUpperCase(Locale.ROOT));
    }
    private String family(String type) {
        if (numeric(type)) return "NUMBER";
        if (dateType(type)) return "DATE";
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "BOOL", "BOOLEAN" -> "BOOLEAN";
            case "ENUM", "STRING" -> "STRING";
            case "ARRAY", "LIST", "VECTOR" -> "LIST";
            case "OBJECT", "MAP" -> "OBJECT";
            default -> type.toUpperCase(Locale.ROOT);
        };
    }
    private boolean actualTypeMatches(Object value, String type) {
        return switch (family(type)) {
            case "NUMBER" -> value instanceof Number;
            case "BOOLEAN" -> value instanceof Boolean;
            case "STRING" -> value instanceof String;
            case "LIST" -> value instanceof List;
            case "OBJECT" -> value instanceof Map;
            case "DATE" -> {
                try { dateMillis(value); yield true; }
                catch (IllegalArgumentException invalid) { yield false; }
            }
            default -> false;
        };
    }
    private boolean dateType(String type) { return Set.of("DATE", "DATETIME", "TIMESTAMP", "LOCALDATE", "LOCALDATETIME").contains(type.toUpperCase(Locale.ROOT)); }
    private BigDecimal decimal(Object value) {
        try { return new BigDecimal(String.valueOf(value).trim()); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("请输入有效且可精确比较的数字"); }
    }

    private boolean same(Object left, Object right, int depth) {
        if (depth > 128) throw new IllegalArgumentException("JSON嵌套超过可比较深度");
        if (left == right) return true;
        if (left == MISSING || right == MISSING || left == null || right == null) return false;
        if (left instanceof Number && right instanceof Number) return decimal(left).compareTo(decimal(right)) == 0;
        if (left instanceof Map<?, ?> a && right instanceof Map<?, ?> b) {
            return a.keySet().equals(b.keySet()) && a.keySet().stream().allMatch(key -> same(a.get(key), b.get(key), depth + 1));
        }
        if (left instanceof List<?> a && right instanceof List<?> b) {
            if (a.size() != b.size()) return false;
            for (int index = 0; index < a.size(); index++) if (!same(a.get(index), b.get(index), depth + 1)) return false;
            return true;
        }
        return left.equals(right);
    }

    private void validateSnapshotDepth(Object value, int depth) {
        if (depth > 128) throw new IllegalArgumentException("JSON嵌套超过可比较深度");
        if (value instanceof Map<?, ?> object) {
            for (Object child : object.values()) validateSnapshotDepth(child, depth + 1);
        } else if (value instanceof List<?> values) {
            for (Object child : values) validateSnapshotDepth(child, depth + 1);
        }
    }

    private void appendDiffs(Object expected, Object actual, String path, TestExpectationResult result, int depth) {
        if (same(expected, actual, depth)) return;
        if (result.getDiffs().size() >= MAX_DIFFS) { result.setTruncated(true); return; }
        if (expected instanceof Map<?, ?> a && actual instanceof Map<?, ?> b) {
            Set<Object> keys = new LinkedHashSet<>(a.keySet()); keys.addAll(b.keySet());
            for (Object key : keys) appendDiffs(a.containsKey(key) ? a.get(key) : MISSING,
                    b.containsKey(key) ? b.get(key) : MISSING, path + '.' + key, result, depth + 1);
        } else if (expected instanceof List<?> a && actual instanceof List<?> b) {
            for (int index = 0; index < Math.max(a.size(), b.size()); index++) appendDiffs(index < a.size() ? a.get(index) : MISSING,
                    index < b.size() ? b.get(index) : MISSING, path + '[' + index + ']', result, depth + 1);
        } else {
            result.getDiffs().add(new TestExpectationResult.Diff(path, expected == MISSING ? null : writeJson(expected),
                    actual == MISSING ? null : writeJson(actual), expected == MISSING, actual == MISSING));
        }
    }

    private Map<String, Object> comparable(Map<String, Object> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", value.containsKey("success") ? value.get("success") : MISSING);
        result.put("result", output(value));
        Object error = value.containsKey("errorMessage") ? value.get("errorMessage") : value.get("error");
        result.put("errorMessage", error == null ? "" : error);
        return result;
    }
    private Object output(Map<String, Object> value) {
        for (String key : List.of("result", "outputs", "value", "resolvedValue")) if (value.containsKey(key)) return value.get(key);
        return MISSING;
    }
    private Object pathValue(Object value, String path) {
        if (!(value instanceof Map<?, ?> root) || path == null) return MISSING;
        if (root.containsKey(path)) return root.get(path);
        Object current = root;
        for (String key : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> object) || !object.containsKey(key)) return MISSING;
            current = object.get(key);
        }
        return current;
    }
    private Object parse(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("JSON内容不能为空");
        try { return JSON_READER.readValue(json, Object.class); }
        catch (Exception invalid) { throw new IllegalArgumentException("JSON格式无效或数字超出支持范围"); }
    }
    private Map<String, Object> payload(Object value) {
        Map<String, Object> root = object(value, "执行结果快照必须是JSON对象");
        return root.get("data") instanceof Map<?, ?> ? object(root.get("data"), "执行结果格式无效") : root;
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value, String error) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException(error);
        return (Map<String, Object>) value;
    }
    private List<?> list(Object value, String error) { if (!(value instanceof List<?> values)) throw new IllegalArgumentException(error); return values; }
    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private String firstText(String first, String second) { return first == null || first.isBlank() ? second : first; }
    private TestExpectationResult result(String status, String message) {
        TestExpectationResult result = new TestExpectationResult(); result.setStatus(status); result.setMessage(message); return result;
    }
}
