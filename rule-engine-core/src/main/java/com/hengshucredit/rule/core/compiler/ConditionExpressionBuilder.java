package com.hengshucredit.rule.core.compiler;

import java.util.ArrayList;
import java.util.List;

final class ConditionExpressionBuilder {
    private ConditionExpressionBuilder() {}

    static String build(String left, String varType, String operator, String value, boolean valueIsVariable) {
        if (empty(left)) return "true";
        String op = empty(operator) ? "==" : operator;
        if ("*".equals(op)) return "true";
        if ("is_null".equals(op)) return left + " == null";
        if ("not_null".equals(op)) return left + " != null";
        if ("is_empty".equals(op)) return "isBlank(" + left + ")";
        if ("not_empty".equals(op)) return "isNotBlank(" + left + ")";
        if ("is_true".equals(op)) return left + " == true";
        if ("is_false".equals(op)) return left + " == false";
        if (empty(value)) return "true";

        String rhs = valueIsVariable ? value.trim() : formatConstant(varType, value);
        if ("==".equals(op) || "!=".equals(op) || ">".equals(op) || ">=".equals(op) || "<".equals(op) || "<=".equals(op)) {
            return left + " " + op + " " + rhs;
        }
        if ("contains".equals(op)) return "containsValue(" + left + ", " + rhs + ")";
        if ("not_contains".equals(op)) return "!containsValue(" + left + ", " + rhs + ")";
        if ("starts_with".equals(op)) return "startsWithValue(" + left + ", " + rhs + ")";
        if ("not_starts_with".equals(op)) return "!startsWithValue(" + left + ", " + rhs + ")";
        if ("ends_with".equals(op)) return "endsWithValue(" + left + ", " + rhs + ")";
        if ("not_ends_with".equals(op)) return "!endsWithValue(" + left + ", " + rhs + ")";
        if ("regex_match".equals(op)) return "regexMatchValue(" + left + ", " + rhs + ")";
        if ("not_regex_match".equals(op)) return "!regexMatchValue(" + left + ", " + rhs + ")";
        if ("in_array".equals(op)) return "containsValue(" + rhs + ", " + left + ")";
        if ("not_in_array".equals(op)) return "!containsValue(" + rhs + ", " + left + ")";
        if ("in_list".equals(op)) return "isInLists(" + left + ", " + rhs + ")";
        if ("not_in_list".equals(op)) return "!isInLists(" + left + ", " + rhs + ")";
        if ("in".equals(op)) return left + " in " + formatList(varType, value);
        if ("not_in".equals(op)) return "!(" + left + " in " + formatList(varType, value) + ")";
        if ("between".equals(op) || "not_between".equals(op)) {
            List<String> parts = splitValues(value);
            if (parts.size() < 2) return "true";
            String expr = "(" + left + " >= " + formatConstant(varType, parts.get(0))
                    + " && " + left + " <= " + formatConstant(varType, parts.get(1)) + ")";
            return "between".equals(op) ? expr : "!" + expr;
        }
        if ("contains_any".equals(op) || "contains_all".equals(op)) {
            List<String> parts = splitValues(value);
            if (parts.isEmpty()) return "true";
            StringBuilder sb = new StringBuilder();
            sb.append("contains_any".equals(op) ? "containsAnyValue(" : "containsAllValues(");
            sb.append(left).append(", ").append(formatList(null, value)).append(")");
            return sb.toString();
        }
        if ("has_key".equals(op)) return "hasKey(" + left + ", " + rhs + ")";
        if ("not_has_key".equals(op)) return "!hasKey(" + left + ", " + rhs + ")";
        if ("has_value".equals(op)) return "hasMapValue(" + left + ", " + rhs + ")";
        if ("not_has_value".equals(op)) return "!hasMapValue(" + left + ", " + rhs + ")";
        if ("array_element_contains".equals(op)) return "containsElementValue(" + left + ", " + rhs + ")";
        if ("array_element_not_contains".equals(op)) return "!containsElementValue(" + left + ", " + rhs + ")";
        if ("array_element_starts_with".equals(op)) return "elementStartsWithValue(" + left + ", " + rhs + ")";
        if ("array_element_ends_with".equals(op)) return "elementEndsWithValue(" + left + ", " + rhs + ")";
        String sizeOperator = sizeOperator(op);
        if (sizeOperator != null) return "sizeOfValue(" + left + ") " + sizeOperator + " " + rhs;
        return left + " " + op + " " + rhs;
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
        String text = value == null ? "" : value.trim();
        if ("true".equals(text) || "false".equals(text) || "null".equals(text)) return text;
        try {
            Double.parseDouble(text);
            return text;
        } catch (NumberFormatException ignored) {
            return quote(text);
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

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
