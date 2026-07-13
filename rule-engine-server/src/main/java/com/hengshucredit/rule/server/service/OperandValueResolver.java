package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
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
        if (operand == null) return null;
        String kind = operand.getString("kind");
        if ("LITERAL".equals(kind)) return literal(operand.get("value"), operand.getString("valueType"));
        if ("PATH".equals(kind) || "REFERENCE".equals(kind)) {
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
        return null;
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
        if ("FUNCTION".equals(kind)) {
            JSONArray args = operand.getJSONArray("args");
            if (args != null) for (int i = 0; i < args.size(); i++) collectPaths(args.getJSONObject(i), paths);
        }
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
        throw new IllegalArgumentException("模型字段 Operand 暂不支持运行时方法: " + code);
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
        if ("INTEGER".equals(type) || "INT".equals(type)) return Integer.valueOf(text);
        if ("LONG".equals(type)) return Long.valueOf(text);
        if (isDecimal(type)) return new BigDecimal(text).doubleValue();
        if ("LIST".equals(type) || "ARRAY".equals(type)) return JSON.parseArray(text);
        if ("MAP".equals(type) || "OBJECT".equals(type)) return JSON.parseObject(text);
        return text;
    }

    private static boolean isDecimal(String type) {
        return "NUMBER".equals(type) || "DOUBLE".equals(type) || "FLOAT".equals(type)
                || "DECIMAL".equals(type) || "BIGDECIMAL".equals(type) || "PROBABILITY".equals(type);
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
}
