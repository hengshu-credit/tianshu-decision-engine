package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONArray;

import java.math.BigDecimal;

/** 统一条件叶 Operand 编译逻辑。 */
final class ConditionOperandCompiler {

    private ConditionOperandCompiler() {
    }

    static boolean supports(JSONObject leaf) {
        return leaf != null && (leaf.getJSONObject("leftOperand") != null || typedCollection(leaf) != null);
    }

    static boolean hasUsableCondition(JSONObject leaf) {
        if (!supports(leaf)) return false;
        String operator = leaf.getString("operator");
        JSONObject collection = typedCollection(leaf);
        if (collection != null) validateCollection(collection);
        if (ConditionExpressionBuilder.isRangeOperator(operator)) validateRange(leaf);
        if (SourceStatusOperators.supports(operator)) {
            return SourceStatusExpressionBuilder.hasStableReference(leaf.getJSONObject("leftOperand"));
        }
        JSONObject leftOperand = leaf.getJSONObject("leftOperand");
        String left = leftOperand == null && collection != null ? "" : OperandCompiler.compile(leftOperand, null);
        requireRangeLeft(operator, left);
        requireCollectionLeft(collection, left);
        if (empty(left) || "*".equals(operator)) return false;
        if (isNoValueOperator(operator)) return true;
        return !empty(OperandCompiler.compile(leaf.getJSONObject("rightOperand"), null));
    }

    static String compile(JSONObject leaf, VarContext varContext) {
        JSONObject leftOperand = leaf.getJSONObject("leftOperand");
        String operator = leaf.getString("operator");
        JSONObject collection = typedCollection(leaf);
        if (collection != null) validateCollection(collection);
        if (ConditionExpressionBuilder.isRangeOperator(operator)) validateRange(leaf);
        if (SourceStatusOperators.supports(operator)) {
            return SourceStatusExpressionBuilder.build(leftOperand, operator);
        }
        String left = leftOperand == null && collection != null ? "" : OperandCompiler.compile(leftOperand, varContext);
        requireRangeLeft(operator, left);
        requireCollectionLeft(collection, left);
        if (empty(left)) return "true";
        if (collection != null) {
            String predicate = left + " in " + OperandCompiler.compile(collection, varContext);
            return "not_in".equals(operator) ? "(!isBlank(" + left + ") && !(" + predicate + "))" : predicate;
        }
        if (isNoValueOperator(operator)) {
            return ConditionExpressionBuilder.build(left,
                    leftOperand == null ? null : leftOperand.getString("valueType"),
                    operator, null, false);
        }
        JSONObject rightOperand = leaf.getJSONObject("rightOperand");
        if (("in_list".equals(operator) || "not_in_list".equals(operator))
                && rightOperand != null && "LIST_QUERY".equals(rightOperand.getString("kind"))) {
            String expression = OperandCompiler.compileListQuery(rightOperand, left);
            return "not_in_list".equals(operator)
                    ? "(!isBlank(" + left + ") && !(" + expression + "))" : expression;
        }
        boolean literal = rightOperand != null && "LITERAL".equals(rightOperand.getString("kind"));
        String right = literal ? rightOperand.getString("value") : OperandCompiler.compile(rightOperand, varContext);
        return ConditionExpressionBuilder.build(left,
                leftOperand == null ? null : leftOperand.getString("valueType"),
                operator, right, !literal);
    }

    private static void validateRange(JSONObject leaf) {
        JSONObject left = leaf.getJSONObject("leftOperand");
        JSONObject right = leaf.getJSONObject("rightOperand");
        if (left == null) throw new IllegalArgumentException("区间条件必须选择比较字段");
        ConditionExpressionBuilder.rangeBounds(left.getString("valueType"), right == null ? null : right.getString("value"),
                right != null && !"LITERAL".equals(right.getString("kind")));
    }

    private static void requireRangeLeft(String operator, String left) {
        if (ConditionExpressionBuilder.isRangeOperator(operator) && empty(left)) {
            throw new IllegalArgumentException("区间条件必须选择比较字段");
        }
    }

    static JSONObject typedCollection(JSONObject leaf) {
        String operator = leaf.getString("operator");
        JSONObject right = leaf.getJSONObject("rightOperand");
        return isTypedCollection(operator, right) ? right : null;
    }

    static boolean isTypedCollection(String operator, JSONObject right) {
        return ("in".equals(operator) || "not_in".equals(operator)) && right != null && "ARRAY".equals(right.getString("kind"));
    }

    static void validateCollection(JSONObject collection) {
        JSONArray items = collection.getJSONArray("items");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("集合至少需要填写一项");
        for (int index = 0; index < items.size(); index++) {
            JSONObject item = items.getJSONObject(index);
            if (item == null) throw new IllegalArgumentException("集合第 " + (index + 1) + " 项内容不能为空");
            JSONObject source = item.getJSONObject("operand");
            String literalType = item.getString("valueType");
            if ("LITERAL".equals(item.getString("kind")) && literalType != null
                    && OperandCompiler.isNumberType(literalType.trim().toUpperCase(java.util.Locale.ROOT))) {
                requireCollectionNumber(item.getString("value"), index);
            }
            // 新逐项数字使用现有 CAST(String -> NUMBER)，在QL解析前保留十进制精度。
            if ("CAST".equals(item.getString("kind")) && "NUMBER".equals(item.getString("targetType"))
                    && source != null && "LITERAL".equals(source.getString("kind")) && "STRING".equals(source.getString("valueType"))) {
                requireCollectionNumber(source.getString("value"), index);
            }
        }
    }

    private static void requireCollectionNumber(String value, int index) {
        try { new BigDecimal(value == null ? "" : value.trim()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("集合第 " + (index + 1) + " 项必须填写有效数字"); }
    }

    private static void requireCollectionLeft(JSONObject collection, String left) {
        if (collection != null && empty(left)) throw new IllegalArgumentException("集合判断必须选择比较字段");
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
