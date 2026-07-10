package com.hengshucredit.rule.core.compiler;

/**
 * 动作值操作数编译器。
 * 未携带显式引用元数据的函数参数属于字面量，禁止仅因内容形似标识符而当作变量。
 */
final class ActionOperandCompiler {

    private ActionOperandCompiler() {
    }

    static String compileLiteral(String value) {
        String text = value == null ? "" : value.trim();
        if ("true".equals(text) || "false".equals(text) || "null".equals(text) || isNumber(text)) {
            return text;
        }
        if (isQuoted(text)) {
            return text;
        }
        return quoteString(text);
    }

    static String quoteString(String value) {
        return "\"" + (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }

    private static boolean isNumber(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            new java.math.BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isQuoted(String value) {
        if (value == null || value.length() < 2) {
            return false;
        }
        return (value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"));
    }
}
