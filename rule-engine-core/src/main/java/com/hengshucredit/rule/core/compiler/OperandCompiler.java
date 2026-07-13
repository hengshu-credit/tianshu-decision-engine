package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/** 将统一 Operand JSON 编译成 QLExpress 表达式。 */
public final class OperandCompiler {

    private OperandCompiler() {
    }

    public static String compile(JSONObject operand, VarContext varContext) {
        if (operand == null) return "";
        String kind = operand.getString("kind");
        if ("LITERAL".equals(kind)) {
            return compileLiteral(operand.get("value"), operand.getString("valueType"));
        }
        if ("PATH".equals(kind) || "REFERENCE".equals(kind)) {
            String code = firstText(operand.getString("code"), operand.getString("value"));
            if (varContext == null) return code;
            if ("REFERENCE".equals(kind) && "CONSTANT".equalsIgnoreCase(operand.getString("refType"))) {
                return varContext.resolveConstant(operand.getLong("refId"));
            }
            return varContext.resolveVar(operand.getLong("refId"), operand.getString("refType"), code);
        }
        if ("FUNCTION".equals(kind)) {
            String functionCode = operand.getString("functionCode");
            if (empty(functionCode)) return "";
            StringBuilder result = new StringBuilder(functionCode).append('(');
            JSONArray args = operand.getJSONArray("args");
            if (args != null) {
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) result.append(", ");
                    result.append(compile(args.getJSONObject(i), varContext));
                }
            }
            return result.append(')').toString();
        }
        return "";
    }

    public static String compileLiteral(Object value, String valueType) {
        String text = value == null ? "" : String.valueOf(value);
        String type = valueType == null ? "STRING" : valueType.trim().toUpperCase();
        if (isNumberType(type)) return text.trim();
        if ("BOOLEAN".equals(type) || "BOOL".equals(type)) {
            return Boolean.TRUE.equals(value) || "true".equals(text) ? "true" : "false";
        }
        if ("LIST".equals(type) || "ARRAY".equals(type) || "SET".equals(type)
                || "MAP".equals(type) || "OBJECT".equals(type)) {
            return text.trim();
        }
        return ActionOperandCompiler.quoteString(text);
    }

    private static boolean isNumberType(String type) {
        return "BYTE".equals(type) || "SHORT".equals(type) || "INT".equals(type)
                || "INTEGER".equals(type) || "LONG".equals(type) || "FLOAT".equals(type)
                || "DOUBLE".equals(type) || "DECIMAL".equals(type) || "BIGDECIMAL".equals(type)
                || "NUMBER".equals(type) || "PROBABILITY".equals(type);
    }

    private static String firstText(String first, String second) {
        if (!empty(first)) return first;
        return second == null ? "" : second;
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
