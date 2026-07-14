package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import com.hengshucredit.rule.model.entity.RuleModelInputField;

/** 在模型调用等 Java 运行时场景中解析统一 Operand。 */
public final class OperandValueResolver {

    private OperandValueResolver() {
    }

    public static Object resolve(String operandJson, Map<String, Object> values) {
        return resolve(operandJson, values, java.util.Collections.emptyMap());
    }

    public static Object resolve(String operandJson, Map<String, Object> values,
                                 Map<String, Object> referenceValues) {
        if (operandJson == null || operandJson.trim().isEmpty()) return null;
        return resolve(JSON.parseObject(operandJson), values, referenceValues);
    }

    public static Object resolve(JSONObject operand, Map<String, Object> values) {
        return resolve(operand, values, java.util.Collections.emptyMap());
    }

    public static Object resolve(JSONObject operand, Map<String, Object> values,
                                 Map<String, Object> referenceValues) {
        if (operand == null) throw new IllegalArgumentException("表达式参数不能为空");
        String kind = operand.getString("kind");
        if (kind == null || kind.trim().isEmpty()) throw new IllegalArgumentException("表达式节点类型不能为空");
        if ("LITERAL".equals(kind)) return literal(operand.get("value"), operand.getString("valueType"));
        if ("PATH".equals(kind) || "REFERENCE".equals(kind)) {
            if ("REFERENCE".equals(kind)
                    && (operand.getLong("refId") == null || empty(operand.getString("refType")))) {
                throw new IllegalArgumentException("受管字段引用缺少 ID 或引用类型");
            }
            if ("REFERENCE".equals(kind) && "CONSTANT".equalsIgnoreCase(operand.getString("refType"))) {
                Long refId = operand.getLong("refId");
                String refKey = refId == null ? null : "CONSTANT:" + refId;
                if (refKey == null || referenceValues == null || !referenceValues.containsKey(refKey)) {
                    throw new IllegalArgumentException("常量引用不存在、已停用或值不合法，ID=" + refId);
                }
                return referenceValues.get(refKey);
            }
            String path = firstText(operand.getString("value"), operand.getString("code"));
            return readPath(values, path);
        }
        if ("FUNCTION".equals(kind)) return call(operand, values, referenceValues);
        if ("OPERATION".equals(kind)) return operation(operand, values, referenceValues);
        if ("ACCESS".equals(kind)) return access(operand, values, referenceValues);
        if ("CAST".equals(kind)) return cast(operand.getString("targetType"),
                resolveRequired(operand.getJSONObject("operand"), values, referenceValues));
        if ("ARRAY".equals(kind)) return resolveArray(operand.getJSONArray("items"), values, referenceValues);
        if ("LIST_QUERY".equals(kind)) {
            throw new IllegalArgumentException("名单查询节点只能由服务端名单执行器解析");
        }
        throw new IllegalArgumentException("不支持的表达式节点类型: " + kind);
    }

    public static Set<String> collectPaths(String operandJson) {
        Set<String> paths = new LinkedHashSet<>();
        if (operandJson == null || operandJson.trim().isEmpty()) return paths;
        collectPaths(JSON.parseObject(operandJson), paths);
        return paths;
    }

    public static Map<String, Object> bindModelInputs(List<RuleModelInputField> fields, Map<String, Object> values) {
        return bindModelInputs(fields, values, java.util.Collections.emptyMap());
    }

    public static Map<String, Object> bindModelInputs(List<RuleModelInputField> fields, Map<String, Object> values,
                                                       Map<String, Object> referenceValues) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (values != null) result.putAll(values);
        if (fields == null) return result;
        for (RuleModelInputField field : fields) {
            String fieldName = firstText(field.getFieldName(), field.getScriptName());
            if (fieldName == null) continue;
            Object value = resolve(field.getSourceOperand(), values, referenceValues);
            boolean sourceIsConstant = isConstantReference(field.getSourceOperand());
            boolean defaultIsConstant = false;
            if (value == null && !sourceIsConstant) {
                value = readPath(values, firstText(field.getScriptName(), field.getFieldName()));
            }
            if (value == null && !sourceIsConstant) {
                value = resolve(field.getDefaultOperand(), values, referenceValues);
                defaultIsConstant = isConstantReference(field.getDefaultOperand());
            }
            if (value == null && !sourceIsConstant && !defaultIsConstant
                    && field.getDefaultValue() != null && !field.getDefaultValue().trim().isEmpty()) {
                value = parseJsonOrRaw(field.getDefaultValue());
            }
            result.put(fieldName, value);
        }
        return result;
    }

    public static void write(String operandJson, Map<String, Object> values, Object value) {
        if (operandJson == null || operandJson.trim().isEmpty() || values == null) return;
        JSONObject operand = JSON.parseObject(operandJson);
        String kind = operand.getString("kind");
        if (!"PATH".equals(kind) && !"REFERENCE".equals(kind)) return;
        String path = firstText(operand.getString("value"), operand.getString("code"));
        if (path == null || path.trim().isEmpty()) return;
        String[] parts = path.split("\\.");
        Map<String, Object> current = values;
        for (int i = 0; i < parts.length - 1; i++) {
            Object nested = current.get(parts[i]);
            if (!(nested instanceof Map)) {
                nested = new java.util.LinkedHashMap<String, Object>();
                current.put(parts[i], nested);
            }
            current = (Map<String, Object>) nested;
        }
        current.put(parts[parts.length - 1], value);
    }

    private static void collectPaths(JSONObject operand, Set<String> paths) {
        if (operand == null) return;
        String kind = operand.getString("kind");
        if ("PATH".equals(kind) || "REFERENCE".equals(kind)) {
            String path = firstText(operand.getString("value"), operand.getString("code"));
            if (path != null && !path.trim().isEmpty()) paths.add(path);
            return;
        }
        for (JSONObject child : children(operand)) collectPaths(child, paths);
    }

    private static Object call(JSONObject operand, Map<String, Object> values,
                               Map<String, Object> referenceValues) {
        String code = operand.getString("functionCode");
        JSONArray args = operand.getJSONArray("args");
        List<Object> resolved = new ArrayList<>();
        if (args != null) {
            for (int i = 0; i < args.size(); i++) {
                resolved.add(resolve(args.getJSONObject(i), values, referenceValues));
            }
        }
        if ("max".equals(code)) return numericExtreme(resolved, true);
        if ("min".equals(code)) return numericExtreme(resolved, false);
        if ("sum".equals(code)) return sum(resolved);
        if ("count".equals(code)) return resolved.size();
        if ("numCeil".equals(code)) return numberArg(resolved, 0, code).setScale(0, RoundingMode.CEILING);
        if ("numFloor".equals(code)) return numberArg(resolved, 0, code).setScale(0, RoundingMode.FLOOR);
        if ("numRoundInteger".equals(code)) return numberArg(resolved, 0, code).setScale(0, RoundingMode.HALF_UP);
        throw new IllegalArgumentException("模型字段 Operand 暂不支持运行时方法: " + code);
    }

    private static Object operation(JSONObject operand, Map<String, Object> values,
                                    Map<String, Object> referenceValues) {
        String operator = operand.getString("operator");
        JSONArray operands = operand.getJSONArray("operands");
        if (empty(operator)) throw new IllegalArgumentException("运算符不能为空");
        if (operands == null || operands.isEmpty()) throw new IllegalArgumentException("运算参数不能为空");
        List<Object> resolved = resolveArray(operands, values, referenceValues);
        if (resolved.size() == 1) {
            Object value = resolved.get(0);
            if ("!".equals(operator)) return !booleanValue(value);
            if ("-".equals(operator)) return number(value).negate();
            if ("+".equals(operator)) return number(value);
            throw new IllegalArgumentException("不支持的一元运算符: " + operator);
        }
        if ("&&".equals(operator) || "||".equals(operator)) {
            boolean result = "&&".equals(operator);
            for (Object value : resolved) {
                if ("&&".equals(operator)) result = result && booleanValue(value);
                else result = result || booleanValue(value);
            }
            return result;
        }
        if (isComparison(operator)) return compare(resolved.get(0), resolved.get(1), operator);
        Object result = resolved.get(0);
        for (int i = 1; i < resolved.size(); i++) result = arithmetic(result, resolved.get(i), operator);
        return result;
    }

    private static Object arithmetic(Object left, Object right, String operator) {
        if ("+".equals(operator) && (!(left instanceof Number) || !(right instanceof Number))) {
            return String.valueOf(left == null ? "" : left) + String.valueOf(right == null ? "" : right);
        }
        BigDecimal a = number(left);
        BigDecimal b = number(right);
        if ("+".equals(operator)) return a.add(b);
        if ("-".equals(operator)) return a.subtract(b);
        if ("*".equals(operator)) return a.multiply(b);
        if ("/".equals(operator)) {
            if (BigDecimal.ZERO.compareTo(b) == 0) throw new IllegalArgumentException("除数不能为零");
            return a.divide(b, MathContext.DECIMAL128).stripTrailingZeros();
        }
        if ("%".equals(operator)) {
            if (BigDecimal.ZERO.compareTo(b) == 0) throw new IllegalArgumentException("除数不能为零");
            return a.remainder(b);
        }
        throw new IllegalArgumentException("不支持的运算符: " + operator);
    }

    private static boolean isComparison(String operator) {
        return "==".equals(operator) || "!=".equals(operator) || ">".equals(operator)
                || ">=".equals(operator) || "<".equals(operator) || "<=".equals(operator);
    }

    private static boolean compare(Object left, Object right, String operator) {
        int compared;
        if (left instanceof Number && right instanceof Number) compared = number(left).compareTo(number(right));
        else if (left == null || right == null) compared = left == right ? 0 : (left == null ? -1 : 1);
        else compared = String.valueOf(left).compareTo(String.valueOf(right));
        if ("==".equals(operator)) return compared == 0;
        if ("!=".equals(operator)) return compared != 0;
        if (">".equals(operator)) return compared > 0;
        if (">=".equals(operator)) return compared >= 0;
        if ("<".equals(operator)) return compared < 0;
        return compared <= 0;
    }

    private static Object access(JSONObject operand, Map<String, Object> values,
                                 Map<String, Object> referenceValues) {
        Object target = resolveRequired(operand.getJSONObject("target"), values, referenceValues);
        Object accessor = resolveRequired(operand.getJSONObject("accessor"), values, referenceValues);
        if ("INDEX".equalsIgnoreCase(operand.getString("accessType"))) {
            int index = number(accessor).intValueExact();
            if (target instanceof List) {
                List<?> list = (List<?>) target;
                return index >= 0 && index < list.size() ? list.get(index) : null;
            }
            if (target != null && target.getClass().isArray()) {
                return index >= 0 && index < Array.getLength(target) ? Array.get(target, index) : null;
            }
            return null;
        }
        if (target instanceof Map) return ((Map<?, ?>) target).get(String.valueOf(accessor));
        if (target instanceof JSONObject) return ((JSONObject) target).get(String.valueOf(accessor));
        return null;
    }

    private static Object cast(String targetType, Object value) {
        if (empty(targetType)) throw new IllegalArgumentException("转换目标类型不能为空");
        String type = targetType.trim().toUpperCase();
        if ("STRING".equals(type)) return value == null ? null : String.valueOf(value);
        if (isDecimal(type) || "INTEGER".equals(type) || "INT".equals(type) || "LONG".equals(type)) return number(value);
        if ("BOOLEAN".equals(type) || "BOOL".equals(type)) return booleanValue(value);
        if ("LIST".equals(type) || "ARRAY".equals(type) || "SET".equals(type)) return listValue(value);
        if ("MAP".equals(type) || "OBJECT".equals(type)) return mapValue(value);
        throw new IllegalArgumentException("不支持的转换目标类型: " + targetType);
    }

    private static List<Object> resolveArray(JSONArray items, Map<String, Object> values,
                                             Map<String, Object> referenceValues) {
        List<Object> result = new ArrayList<>();
        if (items == null) return result;
        for (int i = 0; i < items.size(); i++) {
            result.add(resolveRequired(items.getJSONObject(i), values, referenceValues));
        }
        return result;
    }

    private static Object resolveRequired(JSONObject operand, Map<String, Object> values,
                                          Map<String, Object> referenceValues) {
        if (operand == null) throw new IllegalArgumentException("表达式参数不能为空");
        return resolve(operand, values, referenceValues);
    }

    private static Object numericExtreme(List<Object> values, boolean max) {
        BigDecimal result = null;
        Object selected = null;
        for (Object value : values) {
            if (value == null) continue;
            BigDecimal number = new BigDecimal(String.valueOf(value));
            if (result == null || (max ? number.compareTo(result) > 0 : number.compareTo(result) < 0)) {
                result = number;
                selected = value;
            }
        }
        return selected;
    }

    private static Object sum(List<Object> values) {
        BigDecimal result = BigDecimal.ZERO;
        for (Object value : values) if (value != null) result = result.add(new BigDecimal(String.valueOf(value)));
        return result;
    }

    private static Object literal(Object value, String valueType) {
        String type = valueType == null ? "STRING" : valueType.trim().toUpperCase();
        String text = value == null ? "" : String.valueOf(value);
        if ("BOOLEAN".equals(type) || "BOOL".equals(type)) return Boolean.valueOf(text);
        if ("INTEGER".equals(type) || "INT".equals(type) || "LONG".equals(type) || isDecimal(type)) return new BigDecimal(text);
        if ("LIST".equals(type) || "ARRAY".equals(type)) return JSON.parseArray(text);
        if ("MAP".equals(type) || "OBJECT".equals(type)) return JSON.parseObject(text);
        return text;
    }

    private static boolean isDecimal(String type) {
        return "NUMBER".equals(type) || "DOUBLE".equals(type) || "FLOAT".equals(type)
                || "DECIMAL".equals(type) || "BIGDECIMAL".equals(type) || "PROBABILITY".equals(type);
    }

    private static BigDecimal number(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new IllegalArgumentException("数值不能为空");
        }
        try {
            return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无法转换为数值: " + value, e);
        }
    }

    private static BigDecimal numberArg(List<Object> values, int index, String code) {
        if (values == null || values.size() <= index) throw new IllegalArgumentException(code + " 缺少参数");
        return number(values.get(index));
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return BigDecimal.ZERO.compareTo(number(value)) != 0;
        String text = value == null ? "" : String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text)) return false;
        throw new IllegalArgumentException("无法转换为布尔值: " + value);
    }

    private static List<Object> listValue(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof Collection) return new ArrayList<>((Collection<?>) value);
        if (value.getClass().isArray()) {
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) result.add(Array.get(value, i));
            return result;
        }
        if (value instanceof String) return new ArrayList<Object>(JSON.parseArray((String) value));
        return new ArrayList<>(Collections.singletonList(value));
    }

    private static Map<String, Object> mapValue(Object value) {
        if (value == null) return new LinkedHashMap<>();
        if (value instanceof Map) return new LinkedHashMap<>((Map<String, Object>) value);
        if (value instanceof String) return JSON.parseObject((String) value, LinkedHashMap.class);
        throw new IllegalArgumentException("无法转换为 Map: " + value);
    }

    private static List<JSONObject> children(JSONObject operand) {
        if (operand == null) return Collections.emptyList();
        List<JSONObject> children = new ArrayList<>();
        String kind = operand.getString("kind");
        if ("FUNCTION".equals(kind)) addChildren(children, operand.getJSONArray("args"));
        else if ("OPERATION".equals(kind)) addChildren(children, operand.getJSONArray("operands"));
        else if ("ARRAY".equals(kind)) addChildren(children, operand.getJSONArray("items"));
        else if ("ACCESS".equals(kind)) {
            children.add(operand.getJSONObject("target"));
            children.add(operand.getJSONObject("accessor"));
        } else if ("CAST".equals(kind)) children.add(operand.getJSONObject("operand"));
        return children;
    }

    private static void addChildren(List<JSONObject> target, JSONArray values) {
        if (values == null) return;
        for (int i = 0; i < values.size(); i++) target.add(values.getJSONObject(i));
    }

    private static Object readPath(Map<String, Object> values, String path) {
        if (values == null || path == null || path.trim().isEmpty()) return null;
        if (values.containsKey(path)) return values.get(path);
        Object current = values;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map) || !((Map<?, ?>) current).containsKey(part)) return null;
            current = ((Map<?, ?>) current).get(part);
        }
        return current;
    }

    private static Object parseJsonOrRaw(String value) {
        try {
            return JSON.parse(value);
        } catch (Exception ignored) {
            return value;
        }
    }

    private static boolean isConstantReference(String operandJson) {
        if (operandJson == null || operandJson.trim().isEmpty()) return false;
        try {
            JSONObject operand = JSON.parseObject(operandJson);
            return "REFERENCE".equals(operand.getString("kind"))
                    && "CONSTANT".equalsIgnoreCase(operand.getString("refType"));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String firstText(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first;
        return second;
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
