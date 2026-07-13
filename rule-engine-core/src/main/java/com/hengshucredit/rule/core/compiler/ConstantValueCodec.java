package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.math.BigDecimal;
import java.util.Locale;

/** 常量值的类型校验、QLExpress 表达式生成与 Java 运行时解析。 */
public final class ConstantValueCodec {

    private ConstantValueCodec() {
    }

    public static String normalize(String varType, String rawValue) {
        String type = normalizeType(varType);
        if (rawValue == null) {
            throw invalid(type, "常量值不能为空");
        }
        if (isStringType(type)) {
            return rawValue;
        }

        String value = rawValue.trim();
        if (value.isEmpty()) {
            throw invalid(type, "常量值不能为空白");
        }
        if ("null".equals(value)) {
            return value;
        }
        if (isBooleanType(type)) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw invalid(type, "布尔常量只能填写 true 或 false");
            }
            return value.toLowerCase(Locale.ROOT);
        }
        if (isNumberType(type)) {
            if ("Infinity".equals(value) || "-Infinity".equals(value)) {
                if (!"DOUBLE".equals(type)) {
                    throw invalid(type, "只有 DOUBLE 常量支持正无穷或负无穷");
                }
                return value;
            }
            try {
                new BigDecimal(value);
                return value;
            } catch (NumberFormatException e) {
                throw invalid(type, "数值格式不正确");
            }
        }
        if (isListType(type)) {
            Object parsed = parseJson(type, value);
            if (!(parsed instanceof JSONArray)) {
                throw invalid(type, "列表常量必须是 JSON 数组");
            }
            return value;
        }
        if (isMapType(type)) {
            Object parsed = parseJson(type, value);
            if (!(parsed instanceof JSONObject)) {
                throw invalid(type, "映射常量必须是 JSON 对象");
            }
            return value;
        }
        return rawValue;
    }

    public static String toQlExpression(String varType, String rawValue) {
        String type = normalizeType(varType);
        String value = normalize(type, rawValue);
        if (!isStringType(type) && "null".equals(value)) {
            return "null";
        }
        if (isStringType(type)) {
            return quoteString(value);
        }
        if ("DOUBLE".equals(type) && "Infinity".equals(value)) {
            return "1.0 / 0.0";
        }
        if ("DOUBLE".equals(type) && "-Infinity".equals(value)) {
            return "-1.0 / 0.0";
        }
        if (isMapType(type) && "{}".equals(value)) {
            return "jsonParse('{}')";
        }
        return value;
    }

    public static Object toRuntimeValue(String varType, String rawValue) {
        String type = normalizeType(varType);
        String value = normalize(type, rawValue);
        if (!isStringType(type) && "null".equals(value)) {
            return null;
        }
        if (isStringType(type)) {
            return value;
        }
        if (isBooleanType(type)) {
            return Boolean.valueOf(value);
        }
        if ("DOUBLE".equals(type) && "Infinity".equals(value)) {
            return Double.POSITIVE_INFINITY;
        }
        if ("DOUBLE".equals(type) && "-Infinity".equals(value)) {
            return Double.NEGATIVE_INFINITY;
        }
        if (isIntegerType(type)) {
            BigDecimal number = new BigDecimal(value);
            if ("LONG".equals(type)) {
                return number.longValueExact();
            }
            return number.intValueExact();
        }
        if (isNumberType(type)) {
            if ("DECIMAL".equals(type) || "BIGDECIMAL".equals(type)) {
                return new BigDecimal(value);
            }
            return Double.valueOf(value);
        }
        if (isListType(type)) {
            return JSON.parseArray(value);
        }
        if (isMapType(type)) {
            return JSON.parseObject(value);
        }
        return value;
    }

    private static Object parseJson(String type, String value) {
        try {
            return JSON.parse(value);
        } catch (RuntimeException e) {
            throw invalid(type, "JSON 格式不正确");
        }
    }

    private static String quoteString(String value) {
        String escaped = value.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
        return "'" + escaped + "'";
    }

    private static String normalizeType(String varType) {
        String type = varType == null ? "STRING" : varType.trim().toUpperCase(Locale.ROOT);
        return type.isEmpty() ? "STRING" : type;
    }

    private static boolean isStringType(String type) {
        return "STRING".equals(type) || "DATE".equals(type) || "ENUM".equals(type);
    }

    private static boolean isBooleanType(String type) {
        return "BOOLEAN".equals(type) || "BOOL".equals(type);
    }

    private static boolean isIntegerType(String type) {
        return "BYTE".equals(type) || "SHORT".equals(type) || "INT".equals(type)
                || "INTEGER".equals(type) || "LONG".equals(type);
    }

    private static boolean isNumberType(String type) {
        return isIntegerType(type) || "FLOAT".equals(type) || "DOUBLE".equals(type)
                || "DECIMAL".equals(type) || "BIGDECIMAL".equals(type)
                || "NUMBER".equals(type) || "PROBABILITY".equals(type);
    }

    private static boolean isListType(String type) {
        return "LIST".equals(type) || "ARRAY".equals(type) || "SET".equals(type)
                || "COLLECTION".equals(type) || "VECTOR".equals(type);
    }

    private static boolean isMapType(String type) {
        return "MAP".equals(type) || "OBJECT".equals(type);
    }

    private static IllegalArgumentException invalid(String type, String reason) {
        return new IllegalArgumentException("常量类型[" + type + "]：" + reason);
    }
}
