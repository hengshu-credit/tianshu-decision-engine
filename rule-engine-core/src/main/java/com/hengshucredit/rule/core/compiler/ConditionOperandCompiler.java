package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSONObject;

/** 统一条件叶 Operand 编译逻辑。 */
final class ConditionOperandCompiler {

    private ConditionOperandCompiler() {
    }

    static boolean supports(JSONObject leaf) {
        return leaf != null && leaf.getJSONObject("leftOperand") != null;
    }

    static boolean hasUsableCondition(JSONObject leaf) {
        if (!supports(leaf)) return false;
        String operator = leaf.getString("operator");
        if (SourceStatusOperators.supports(operator)) {
            return SourceStatusExpressionBuilder.hasStableReference(leaf.getJSONObject("leftOperand"));
        }
        String left = OperandCompiler.compile(leaf.getJSONObject("leftOperand"), null);
        if (empty(left) || "*".equals(operator)) return false;
        if (isNoValueOperator(operator)) return true;
        return !empty(OperandCompiler.compile(leaf.getJSONObject("rightOperand"), null));
    }

    static String compile(JSONObject leaf, VarContext varContext) {
        JSONObject leftOperand = leaf.getJSONObject("leftOperand");
        String operator = leaf.getString("operator");
        if (SourceStatusOperators.supports(operator)) {
            return SourceStatusExpressionBuilder.build(leftOperand, operator);
        }
        String left = OperandCompiler.compile(leftOperand, varContext);
        if (empty(left)) return "true";
        if (isNoValueOperator(operator)) {
            return ConditionExpressionBuilder.build(left,
                    leftOperand == null ? null : leftOperand.getString("valueType"),
                    operator, null, false);
        }
        JSONObject rightOperand = leaf.getJSONObject("rightOperand");
        if (("in_list".equals(operator) || "not_in_list".equals(operator))
                && rightOperand != null && "LIST_QUERY".equals(rightOperand.getString("kind"))) {
            String expression = OperandCompiler.compileListQuery(rightOperand, left);
            return "not_in_list".equals(operator) ? "!" + expression : expression;
        }
        boolean literal = rightOperand != null && "LITERAL".equals(rightOperand.getString("kind"));
        String right = literal ? rightOperand.getString("value") : OperandCompiler.compile(rightOperand, varContext);
        return ConditionExpressionBuilder.build(left,
                leftOperand == null ? null : leftOperand.getString("valueType"),
                operator, right, !literal);
    }

    private static boolean isNoValueOperator(String operator) {
        return "is_null".equals(operator) || "not_null".equals(operator)
                || "is_empty".equals(operator) || "not_empty".equals(operator)
                || "is_true".equals(operator) || "is_false".equals(operator)
                || SourceStatusOperators.supports(operator);
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
