package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.function.BuiltinFunctionInvoker;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.model.entity.RuleFunction;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** 计算规则用户配置的幂等键；只允许纯 Operand，不触发任何外部来源。 */
public final class IdempotencyKeyCalculator {
    private IdempotencyKeyCalculator() { }

    private static final Set<String> FORBIDDEN_REFERENCE_TYPES = Set.of(
            "MODEL", "MODEL_OUTPUT", "LIST", "LIST_QUERY", "RULE", "EXPERIMENT",
            "API", "EXTERNAL_API", "DATABASE", "DB", "DATASOURCE", "DATA_SOURCE",
            "ASYNC_API", "RANDOM");
    private static final Set<String> NON_DETERMINISTIC_FUNCTIONS = Set.of(
            "randomInt", "randomDecimal", "currentDate", "currentDateTime",
            "imageToBase64", "idCardAge");
    private static final Set<String> OPERATORS = Set.of(
            "+", "-", "*", "/", "%", ">", ">=", "<", "<=", "==", "!=", "&&", "||");
    private static final Set<String> PURE_BUILTINS = pureBuiltinCodes();

    public static Result calculate(String configurationJson, Map<String, Object> input,
                                   Map<String, String> referencePaths) {
        if (configurationJson == null || configurationJson.trim().isEmpty()) {
            throw new IllegalArgumentException("幂等键配置不能为空");
        }
        try {
            return calculate(JSONObject.parseObject(configurationJson), input, referencePaths);
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException) throw e;
            throw new IllegalArgumentException("幂等键配置 JSON 无效", e);
        }
    }

    public static Result calculate(Map<String, Object> configuration, Map<String, Object> input,
                                   Map<String, String> referencePaths) {
        return calculate((JSONObject) JSONObject.toJSON(configuration), input, referencePaths);
    }

    public static void validate(JSONObject configuration, Map<String, String> referencePaths) {
        if (configuration == null || !configuration.getBooleanValue("enabled")) {
            throw new IllegalArgumentException("幂等键配置未启用");
        }
        Map<String, String> normalizedPaths = normalizedReferencePaths(referencePaths);
        JSONArray components = configuration.getJSONArray("components");
        List<JSONObject> operands = new ArrayList<>();
        if (components != null && !components.isEmpty()) {
            for (int i = 0; i < components.size(); i++) {
                Object raw = components.get(i);
                Object converted = raw instanceof JSONObject ? raw : JSON.toJSON(raw);
                if (!(converted instanceof JSONObject)) throw new IllegalArgumentException("幂等键组件必须是对象");
                JSONObject component = (JSONObject) converted;
                JSONObject operand = component.getJSONObject("operand");
                operands.add(operand == null ? component : operand);
            }
        } else if (configuration.getJSONObject("operand") != null) {
            operands.add(configuration.getJSONObject("operand"));
        }
        if (operands.isEmpty()) throw new IllegalArgumentException("幂等键未配置表达式");
        boolean hasStableReference = false;
        for (JSONObject operand : operands) {
            validatePure(operand, normalizedPaths, referencePaths != null);
            hasStableReference |= containsStableReference(operand);
        }
        if (!hasStableReference) {
            throw new IllegalArgumentException("幂等键至少需要一个稳定的请求字段引用，不能只使用常量");
        }
    }

    public static Result calculate(JSONObject configuration, Map<String, Object> input,
                                   Map<String, String> referencePaths) {
        if (configuration == null || !configuration.getBooleanValue("enabled")) {
            throw new IllegalArgumentException("幂等键配置未启用");
        }
        Map<String, Object> values = input == null ? Collections.emptyMap() : input;
        List<JSONObject> operands = new ArrayList<>();
        JSONArray components = configuration.getJSONArray("components");
        if (components != null && !components.isEmpty()) {
            for (int i = 0; i < components.size(); i++) {
                Object raw = components.get(i);
                Object converted = raw instanceof JSONObject ? raw : JSON.toJSON(raw);
                if (!(converted instanceof JSONObject)) throw new IllegalArgumentException("幂等键组件必须是对象");
                JSONObject component = (JSONObject) converted;
                JSONObject operand = component.getJSONObject("operand");
                operands.add(operand == null ? component : operand);
            }
        } else if (configuration.getJSONObject("operand") != null) {
            operands.add(configuration.getJSONObject("operand"));
        }
        if (operands.isEmpty()) throw new IllegalArgumentException("幂等键未配置表达式");
        List<String> parts = new ArrayList<>();
        Map<String, String> normalizedPaths = normalizedReferencePaths(referencePaths);
        for (JSONObject operand : operands) {
            validatePure(operand, normalizedPaths, true);
            Object value = evaluate(operand, values, normalizedPaths);
            if (blank(value)) continue;
            String identity = identity(operand);
            parts.add(identity + "=" + canonical(value));
        }
        if (parts.isEmpty()) throw new IllegalArgumentException("幂等键没有任何非空字段");
        String key = String.join("|", parts);
        return new Result(key, sha256(key), sha256(canonical(values)), parts.size());
    }

    private static Object evaluate(JSONObject operand, Map<String, Object> values, Map<String, String> paths) {
        if (operand == null) throw new IllegalArgumentException("幂等键表达式不能为空");
        String kind = text(operand.getString("kind")).toUpperCase();
        switch (kind) {
            case "LITERAL": return operand.get("value");
            case "REFERENCE": {
                Long id = operand.getLong("refId");
                String type = text(operand.getString("refType")).toUpperCase();
                if (id == null || type.isEmpty()) throw new IllegalArgumentException("幂等键字段引用缺少稳定ID");
                if (FORBIDDEN_REFERENCE_TYPES.contains(type)) throw new IllegalArgumentException("幂等键不能引用运行时来源: " + type);
                String path = paths.get(type + ":" + id);
                if (path == null) throw new IllegalArgumentException("幂等键引用不存在: " + type + ":" + id);
                return readPath(values, path);
            }
            case "PATH": throw new IllegalArgumentException("幂等键不允许未绑定的PATH引用");
            case "FUNCTION": return function(operand, values, paths);
            case "OPERATION": return operation(operand, values, paths);
            case "CAST": return cast(operand.getString("targetType"), evaluate(operand.getJSONObject("operand"), values, paths));
            case "ACCESS": {
                Object target = evaluate(operand.getJSONObject("target"), values, paths);
                Object accessor = evaluate(operand.getJSONObject("accessor"), values, paths);
                if (target instanceof Map) return ((Map<?, ?>) target).get(String.valueOf(accessor));
                if (target instanceof List) return ((List<?>) target).get(Integer.parseInt(String.valueOf(accessor)));
                return null;
            }
            case "ARRAY": {
                JSONArray items = operand.getJSONArray("items");
                List<Object> result = new ArrayList<>();
                if (items != null) for (int i = 0; i < items.size(); i++) result.add(evaluate(items.getJSONObject(i), values, paths));
                return result;
            }
            case "LIST_QUERY": throw new IllegalArgumentException("幂等键不能使用名单查询");
            default: throw new IllegalArgumentException("幂等键不支持节点: " + kind);
        }
    }

    private static Object function(JSONObject operand, Map<String, Object> values, Map<String, String> paths) {
        if (operand.getLong("functionId") != null) throw new IllegalArgumentException("幂等键不能调用自定义函数");
        String code = text(operand.getString("functionCode"));
        JSONArray args = operand.getJSONArray("args");
        List<Object> valuesList = new ArrayList<>();
        if (args != null) for (int i = 0; i < args.size(); i++) valuesList.add(evaluate(args.getJSONObject(i), values, paths));
        if (!PURE_BUILTINS.contains(code) || NON_DETERMINISTIC_FUNCTIONS.contains(code)) {
            throw new IllegalArgumentException("幂等键函数不是纯内置函数: " + code);
        }
        return BuiltinFunctionInvoker.invoke(code, valuesList);
    }

    private static Object operation(JSONObject operand, Map<String, Object> values, Map<String, String> paths) {
        JSONArray terms = operand.getJSONArray("terms");
        if (terms == null || terms.isEmpty()) throw new IllegalArgumentException("幂等键运算项不足");
        List<Object> operands = new ArrayList<>();
        List<String> operators = new ArrayList<>();
        operands.add(evaluate(terms.getJSONObject(0).getJSONObject("operand"), values, paths));
        for (int i = 1; i < terms.size(); i++) {
            JSONObject term = terms.getJSONObject(i);
            String operator = term.getString("operator");
            if (!OPERATORS.contains(operator)) throw new IllegalArgumentException("幂等键不支持运算符: " + operator);
            while (!operators.isEmpty() && precedence(operators.get(operators.size() - 1)) >= precedence(operator)) {
                apply(operands, operators.remove(operators.size() - 1));
            }
            operators.add(operator);
            operands.add(evaluate(term.getJSONObject("operand"), values, paths));
        }
        while (!operators.isEmpty()) apply(operands, operators.remove(operators.size() - 1));
        return operands.get(0);
    }

    private static int precedence(String operator) {
        if ("*".equals(operator) || "/".equals(operator) || "%".equals(operator)) return 6;
        if ("+".equals(operator) || "-".equals(operator)) return 5;
        if (">".equals(operator) || ">=".equals(operator) || "<".equals(operator) || "<=".equals(operator)) return 4;
        if ("==".equals(operator) || "!=".equals(operator)) return 3;
        if ("&&".equals(operator)) return 2;
        if ("||".equals(operator)) return 1;
        return -1;
    }

    private static void apply(List<Object> values, String operator) {
        Object right = values.remove(values.size() - 1);
        Object left = values.remove(values.size() - 1);
        if ("+".equals(operator) && (!(left instanceof Number) || !(right instanceof Number))) {
            values.add(String.valueOf(left == null ? "" : left) + String.valueOf(right == null ? "" : right));
        } else if ("+".equals(operator)) values.add(number(left).add(number(right)));
        else if ("-".equals(operator)) values.add(number(left).subtract(number(right)));
        else if ("*".equals(operator)) values.add(number(left).multiply(number(right)));
        else if ("/".equals(operator)) {
            if (number(right).compareTo(BigDecimal.ZERO) == 0) throw new IllegalArgumentException("幂等键除数不能为零");
            values.add(number(left).divide(number(right), java.math.MathContext.DECIMAL128));
        } else if ("%".equals(operator)) values.add(number(left).remainder(number(right)));
        else if ("&&".equals(operator) || "||".equals(operator)) {
            boolean leftValue = booleanValue(left), rightValue = booleanValue(right);
            values.add("&&".equals(operator) ? leftValue && rightValue : leftValue || rightValue);
        } else {
            int compared = compare(left, right);
            values.add("==".equals(operator) ? compared == 0
                    : "!=".equals(operator) ? compared != 0
                    : ">".equals(operator) ? compared > 0
                    : ">=".equals(operator) ? compared >= 0
                    : "<".equals(operator) ? compared < 0 : compared <= 0);
        }
    }

    private static Object cast(String target, Object value) {
        if (value == null) return null;
        String type = text(target).toUpperCase();
        if ("STRING".equals(type)) return String.valueOf(value).trim();
        if ("NUMBER".equals(type) || "DECIMAL".equals(type)) return number(value);
        if ("INTEGER".equals(type) || "INT".equals(type) || "LONG".equals(type)) return number(value).longValueExact();
        if ("BOOLEAN".equals(type) || "BOOL".equals(type)) return Boolean.parseBoolean(String.valueOf(value));
        return value;
    }

    private static Object readPath(Object root, String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (current instanceof Map) current = ((Map<?, ?>) current).get(part);
            else return null;
        }
        return current;
    }

    private static String identity(JSONObject operand) {
        if (operand != null && operand.getLong("refId") != null && !text(operand.getString("refType")).isEmpty()) {
            return text(operand.getString("refType")).toUpperCase() + ":" + operand.getLong("refId");
        }
        return "EXPR:" + sha256(CanonicalJson.write(identityShape(operand)));
    }

    private static Object identityShape(Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String kind = text(object.getString("kind")).toUpperCase();
            Map<String, Object> result = new TreeMap<>();
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                String key = entry.getKey();
                if ("REFERENCE".equals(kind) && Set.of("code", "value", "label").contains(key)) continue;
                result.put(key, identityShape(entry.getValue()));
            }
            return result;
        }
        if (value instanceof JSONArray) {
            List<Object> result = new ArrayList<>();
            for (Object item : (JSONArray) value) result.add(identityShape(item));
            return result;
        }
        if (value instanceof Map) {
            Map<String, Object> result = new TreeMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                result.put(String.valueOf(entry.getKey()), identityShape(entry.getValue()));
            }
            return result;
        }
        if (value instanceof Collection) {
            List<Object> result = new ArrayList<>();
            for (Object item : (Collection<?>) value) result.add(identityShape(item));
            return result;
        }
        return value;
    }

    private static String canonical(Object value) {
        return CanonicalJson.write(normalized(value));
    }

    private static Object normalized(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return number(value).stripTrailingZeros();
        if (value instanceof Map) {
            Map<String, Object> ordered = new TreeMap<>();
            ((Map<?, ?>) value).forEach((key, item) -> ordered.put(String.valueOf(key), normalized(item)));
            return ordered;
        }
        if (value instanceof Collection) {
            List<Object> result = new ArrayList<>();
            for (Object item : (Collection<?>) value) result.add(normalized(item));
            return result;
        }
        if (value.getClass().isArray()) {
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++) {
                result.add(normalized(java.lang.reflect.Array.get(value, i)));
            }
            return result;
        }
        return value;
    }

    private static boolean blank(Object value) {
        if (value == null) return true;
        if (value instanceof String) return ((String) value).trim().isEmpty();
        if (value instanceof Collection) return ((Collection<?>) value).isEmpty();
        if (value instanceof Map) return ((Map<?, ?>) value).isEmpty();
        return value.getClass().isArray() && java.lang.reflect.Array.getLength(value) == 0;
    }

    private static void validatePure(JSONObject operand, Map<String, String> paths, boolean enforceReferencePaths) {
        if (operand == null) throw new IllegalArgumentException("幂等键表达式不能为空");
        String kind = text(operand.getString("kind")).toUpperCase();
        switch (kind) {
            case "LITERAL": return;
            case "REFERENCE":
                Long id = operand.getLong("refId");
                String type = text(operand.getString("refType")).toUpperCase();
                if (id == null || type.isEmpty()) throw new IllegalArgumentException("幂等键字段引用缺少稳定ID");
                if (FORBIDDEN_REFERENCE_TYPES.contains(type)) throw new IllegalArgumentException("幂等键不能引用运行时来源: " + type);
                if (enforceReferencePaths && !paths.containsKey(type + ":" + id)) {
                    throw new IllegalArgumentException("幂等键引用不存在: " + type + ":" + id);
                }
                return;
            case "FUNCTION":
                if (operand.getLong("functionId") != null) throw new IllegalArgumentException("幂等键不能调用自定义函数");
                String code = operand.getString("functionCode");
                if (!PURE_BUILTINS.contains(code) || NON_DETERMINISTIC_FUNCTIONS.contains(code)) {
                    throw new IllegalArgumentException("幂等键函数不是纯内置函数: " + code);
                }
                JSONArray args = operand.getJSONArray("args");
                if (args != null) for (int i = 0; i < args.size(); i++) validatePure(args.getJSONObject(i), paths, enforceReferencePaths);
                return;
            case "OPERATION":
                JSONArray terms = operand.getJSONArray("terms");
                if (terms == null || terms.isEmpty()) throw new IllegalArgumentException("幂等键运算项不足");
                for (int i = 0; i < terms.size(); i++) {
                    JSONObject term = terms.getJSONObject(i);
                    if (term == null || term.getJSONObject("operand") == null) throw new IllegalArgumentException("幂等键运算项不能为空");
                    if (i > 0 && !OPERATORS.contains(term.getString("operator"))) throw new IllegalArgumentException("幂等键不支持运算符: " + term.getString("operator"));
                    validatePure(term.getJSONObject("operand"), paths, enforceReferencePaths);
                }
                return;
            case "CAST": validatePure(operand.getJSONObject("operand"), paths, enforceReferencePaths); return;
            case "ACCESS": validatePure(operand.getJSONObject("target"), paths, enforceReferencePaths); validatePure(operand.getJSONObject("accessor"), paths, enforceReferencePaths); return;
            case "ARRAY":
                JSONArray items = operand.getJSONArray("items");
                if (items == null) throw new IllegalArgumentException("幂等键数组缺少 items");
                for (int i = 0; i < items.size(); i++) validatePure(items.getJSONObject(i), paths, enforceReferencePaths);
                return;
            default: throw new IllegalArgumentException("幂等键不支持节点: " + kind);
        }
    }

    private static boolean containsStableReference(JSONObject operand) {
        if (operand == null) return false;
        String kind = text(operand.getString("kind")).toUpperCase();
        if ("REFERENCE".equals(kind)) return operand.getLong("refId") != null
                && !text(operand.getString("refType")).isEmpty();
        if ("FUNCTION".equals(kind)) {
            JSONArray args = operand.getJSONArray("args");
            if (args != null) for (int i = 0; i < args.size(); i++) {
                if (containsStableReference(args.getJSONObject(i))) return true;
            }
            return false;
        }
        if ("OPERATION".equals(kind)) {
            JSONArray terms = operand.getJSONArray("terms");
            if (terms != null) for (int i = 0; i < terms.size(); i++) {
                JSONObject term = terms.getJSONObject(i);
                if (term != null && containsStableReference(term.getJSONObject("operand"))) return true;
            }
            return false;
        }
        if ("CAST".equals(kind)) return containsStableReference(operand.getJSONObject("operand"));
        if ("ACCESS".equals(kind)) return containsStableReference(operand.getJSONObject("target"))
                || containsStableReference(operand.getJSONObject("accessor"));
        if ("ARRAY".equals(kind)) {
            JSONArray items = operand.getJSONArray("items");
            if (items != null) for (int i = 0; i < items.size(); i++) {
                if (containsStableReference(items.getJSONObject(i))) return true;
            }
        }
        return false;
    }

    private static Map<String, String> normalizedReferencePaths(Map<String, String> paths) {
        Map<String, String> result = new LinkedHashMap<>();
        if (paths == null) return result;
        for (Map.Entry<String, String> entry : paths.entrySet()) {
            String key = entry.getKey();
            String[] parts = key == null ? new String[0] : key.trim().split(":", -1);
            if (parts.length != 2 || text(parts[0]).isEmpty() || !parts[1].matches("[1-9][0-9]*")) {
                throw new IllegalArgumentException("幂等键引用键无效: " + key);
            }
            result.put(text(parts[0]).toUpperCase() + ":" + parts[1], entry.getValue());
        }
        return result;
    }

    private static int compare(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) return number(left).compareTo(number(right));
        if (left == null || right == null) return left == right ? 0 : left == null ? -1 : 1;
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return number(value).compareTo(BigDecimal.ZERO) != 0;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Set<String> pureBuiltinCodes() {
        Set<String> result = new LinkedHashSet<>();
        for (RuleFunction function : BuiltinFunctionCatalog.definitions()) {
            String implementation = function.getImplClass();
            if (function.getFuncCode() != null
                    && "JAVA".equalsIgnoreCase(function.getImplType())
                    && implementation != null
                    && !implementation.contains("RandomBuiltinFunctions")
                    && !implementation.contains("RuntimeContextBuiltinFunctions")
                    && !implementation.contains("ImageInputFunctions")) {
                result.add(function.getFuncCode());
            }
        }
        result.removeAll(NON_DETERMINISTIC_FUNCTIONS);
        return Collections.unmodifiableSet(result);
    }

    private static BigDecimal number(Object value) { return new BigDecimal(String.valueOf(value)); }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception e) { throw new IllegalStateException("SHA-256 不可用", e); }
    }

    public record Result(String canonicalKey, String keyHash, String inputDigest, int componentCount) { }
}
