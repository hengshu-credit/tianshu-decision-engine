package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSONObject;

/** 将来源状态操作符编译为严格按 refType + refId 查询 sidecar 的函数调用。 */
final class SourceStatusExpressionBuilder {

    private SourceStatusExpressionBuilder() {
    }

    static boolean hasStableReference(JSONObject operand) {
        return operand != null && operand.get("refId") != null && !empty(operand.getString("refType"));
    }

    static String build(JSONObject operand, String operator) {
        SourceStatusOperators.Expectation expectation = SourceStatusOperators.expectation(operator);
        if (expectation == null) {
            throw new IllegalArgumentException("不支持的来源状态操作符: " + operator);
        }
        if (!hasStableReference(operand)) {
            throw new IllegalArgumentException("来源状态判断必须使用带 refType 和 refId 的受管字段引用");
        }
        return "sourceStatus(" + quote(operand.getString("refType")) + ", "
                + quote(String.valueOf(operand.get("refId"))) + ", "
                + quote(expectation.dimension) + ", " + quote(expectation.expected) + ")";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
