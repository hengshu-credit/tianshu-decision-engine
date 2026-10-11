package com.hengshucredit.rule.core.compiler;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

final class ConditionExpressionBuilder {
    private ConditionExpressionBuilder() {}

    static String build(String left, String varType, String operator, String value, boolean valueIsVariable) {
        if (isRangeOperator(operator)) {
            List<String> parts = rangeBounds(varType, value, valueIsVariable);
            if (empty(left)) throw new IllegalArgumentException("区间条件必须选择比较字段");
            String rangeLeft = left;
            String lower = formatConstant(varType, parts.get(0));
            String upper = formatConstant(varType, parts.get(1));
            if (isDateType(varType)) {
                rangeLeft = dateMillis(left);
                lower = dateMillis(lower);
                upper = dateMillis(upper);
            }
            String expr = "(" + rangeLeft + " >= " + lower + " && " + rangeLeft + " <= " + upper + ")";
            return "between".equals(operator) ? expr : "(!isBlank(" + left + ") && !" + expr + ")";
        }
        if (empty(left)) return "true";
        String op = empty(operator) ? "==" : operator;
        if ("*".equals(op)) return "true";
        if ("is_null".equals(op)) return left + " == null";
        if ("not_null".equals(op)) return left + " != null";
        if ("is_empty".equals(op)) return "isBlank(" + left + ")";
        if ("not_empty".equals(op)) return "isNotBlank(" + left + ")";
        if ("is_true".equals(op)) return left + " == true";
        if ("is_false".equals(op)) return left + " == false";
        if (value == null) return "false";

        String rhs = valueIsVariable ? value.trim() : formatConstant(varType, value);
        if ("==".equals(op) || "!=".equals(op) || ">".equals(op) || ">=".equals(op) || "<".equals(op) || "<=".equals(op)) {
            if (isDateType(varType)) {
                return dateMillis(left) + " " + op + " " + dateMillis(rhs);
            }
            return left + " " + op + " " + rhs;
        }
        if ("contains".equals(op)) return "containsValue(" + left + ", " + rhs + ")";
        if ("not_contains".equals(op)) return "(!isBlank(" + left + ") && !containsValue(" + left + ", " + rhs + "))";
        if ("starts_with".equals(op)) return "startsWithValue(" + left + ", " + rhs + ")";
        if ("not_starts_with".equals(op)) return "(!isBlank(" + left + ") && !startsWithValue(" + left + ", " + rhs + "))";
        if ("ends_with".equals(op)) return "endsWithValue(" + left + ", " + rhs + ")";
        if ("not_ends_with".equals(op)) return "(!isBlank(" + left + ") && !endsWithValue(" + left + ", " + rhs + "))";
        if ("regex_match".equals(op)) return "regexMatchValue(" + left + ", " + rhs + ")";
        if ("not_regex_match".equals(op)) return "(!isBlank(" + left + ") && !regexMatchValue(" + left + ", " + rhs + "))";
        if ("in_array".equals(op)) return "containsValue(" + rhs + ", " + left + ")";
        if ("not_in_array".equals(op)) return "(!isBlank(" + left + ") && !containsValue(" + rhs + ", " + left + "))";
        if ("in_list".equals(op)) return "isInLists(" + left + ", " + rhs + ")";
        if ("not_in_list".equals(op)) return "(!isBlank(" + left + ") && !isInLists(" + left + ", " + rhs + "))";
        if ("in".equals(op)) return left + " in " + formatList(varType, value);
        if ("not_in".equals(op)) return "(!isBlank(" + left + ") && !(" + left + " in " + formatList(varType, value) + "))";
        if ("contains_any".equals(op) || "contains_all".equals(op)) {
            List<String> parts = splitValues(value);
            if (parts.isEmpty()) return "true";
            StringBuilder sb = new StringBuilder();
            sb.append("contains_any".equals(op) ? "containsAnyValue(" : "containsAllValues(");
            sb.append(left).append(", ").append(formatList(null, value)).append(")");
            return sb.toString();
        }
        if ("has_key".equals(op)) return "hasKey(" + left + ", " + rhs + ")";
        if ("not_has_key".equals(op)) return "(!isBlank(" + left + ") && !hasKey(" + left + ", " + rhs + "))";
        if ("has_value".equals(op)) return "hasMapValue(" + left + ", " + rhs + ")";
        if ("not_has_value".equals(op)) return "(!isBlank(" + left + ") && !hasMapValue(" + left + ", " + rhs + "))";
        if ("array_element_contains".equals(op)) return "containsElementValue(" + left + ", " + rhs + ")";
        if ("array_element_not_contains".equals(op)) return "(!isBlank(" + left + ") && !containsElementValue(" + left + ", " + rhs + "))";
        if ("array_element_starts_with".equals(op)) return "elementStartsWithValue(" + left + ", " + rhs + ")";
        if ("array_element_ends_with".equals(op)) return "elementEndsWithValue(" + left + ", " + rhs + ")";
        String sizeOperator = sizeOperator(op);
        if (sizeOperator != null) return "sizeOfValue(" + left + ") " + sizeOperator + " " + rhs;
        return left + " " + op + " " + rhs;
    }

    static boolean isRangeOperator(String operator) {
        return "between".equals(operator) || "not_between".equals(operator);
    }

    static List<String> rangeBounds(String varType, String value, boolean valueIsVariable) {
        if (valueIsVariable) throw new IllegalArgumentException("区间条件必须分别填写上下界常量");
        String[] parts = value == null ? new String[0] : value.split(",", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("区间条件必须完整填写下界和上界，且只能包含两个边界");
        }
        String lower = parts[0].trim();
        String upper = parts[1].trim();
        boolean date = isDateType(varType);
        BigDecimal lowerValue = rangeValue(lower, date);
        BigDecimal upperValue = rangeValue(upper, date);
        if (lowerValue == null || upperValue == null) {
            throw new IllegalArgumentException(date ? "区间上下界必须是有效日期或时间" : "区间上下界必须是有效数字");
        }
        if (lowerValue.compareTo(upperValue) > 0) throw new IllegalArgumentException("区间下界不能大于上界");
        return List.of(lower, upper);
    }

    private static BigDecimal rangeValue(String value, boolean date) {
        // dateToMillis 对 10 位及以上整数文本按 Long 毫秒处理，保留这一既有含义。
        if (date && value.matches("^-?\\d{10,}$")) {
            try { return BigDecimal.valueOf(Long.parseLong(value)); }
            catch (NumberFormatException ignored) { return null; }
        }
        return IntervalValidator.comparableValue(value, date);
    }

    private static String sizeOperator(String operator) {
        if ("size_eq".equals(operator)) return "==";
        if ("size_gt".equals(operator)) return ">";
        if ("size_gte".equals(operator)) return ">=";
        if ("size_lt".equals(operator)) return "<";
        if ("size_lte".equals(operator)) return "<=";
        return null;
    }

    static String formatConstant(String varType, String value) {
        if (value == null) return "\"\"";
        String type = normalizeType(varType);
        if ("NUMBER".equals(type)) return value;
        if ("BOOLEAN".equals(type)) return Boolean.parseBoolean(value) ? "true" : "false";
        if ("LIST".equals(type) || "MAP".equals(type) || empty(type)) return formatSmartConstant(value);
        return quote(value);
    }

    private static String formatList(String varType, String value) {
        String type = normalizeType(varType);
        List<String> parts = splitValues(value);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("NUMBER".equals(type) ? parts.get(i) : formatSmartConstant(parts.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String formatSmartConstant(String value) {
        String raw = value == null ? "" : value;
        String text = raw.trim();
        if ("true".equals(text) || "false".equals(text) || "null".equals(text)) return text;
        try {
            Double.parseDouble(text);
            return text;
        } catch (NumberFormatException ignored) {
            return quote(raw);
        }
    }

    private static List<String> splitValues(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        String[] parts = value.split(",");
        for (String part : parts) {
            if (part != null && !part.trim().isEmpty()) result.add(part.trim());
        }
        return result;
    }

    private static String normalizeType(String varType) {
        if (varType == null) return "";
        String type = varType.trim().toUpperCase();
        if ("BYTE".equals(type) || "SHORT".equals(type) || "INT".equals(type) || "INTEGER".equals(type)
                || "LONG".equals(type) || "FLOAT".equals(type) || "DOUBLE".equals(type)
                || "DECIMAL".equals(type) || "BIGDECIMAL".equals(type)) {
            return "NUMBER";
        }
        if ("BOOL".equals(type)) return "BOOLEAN";
        if ("ARRAY".equals(type) || "SET".equals(type) || "COLLECTION".equals(type)) return "LIST";
        return type;
    }

    private static boolean isDateType(String varType) {
        String type = normalizeType(varType);
        return "DATE".equals(type) || "DATETIME".equals(type) || "TIMESTAMP".equals(type)
                || "LOCALDATE".equals(type) || "LOCALDATETIME".equals(type);
    }

    private static String dateMillis(String expression) {
        return "dateToMillis(" + expression + ")";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
