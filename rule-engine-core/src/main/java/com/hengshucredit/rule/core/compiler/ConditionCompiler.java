package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.Set;

/** 编译必填的可视化条件，拒绝会被通用设计器当作默认命中的空分支。 */
public final class ConditionCompiler {
    private static final Set<String> OPERATORS = Set.of(
            "==", "!=", ">", ">=", "<", "<=", "is_null", "not_null", "is_empty", "not_empty", "is_true", "is_false",
            "contains", "not_contains", "starts_with", "not_starts_with", "ends_with", "not_ends_with",
            "regex_match", "not_regex_match", "in_array", "not_in_array", "in_list", "not_in_list", "in", "not_in",
            "between", "not_between", "contains_any", "contains_all", "has_key", "not_has_key", "has_value", "not_has_value",
            "array_element_contains", "array_element_not_contains", "array_element_starts_with", "array_element_ends_with",
            "size_eq", "size_gt", "size_gte", "size_lt", "size_lte");

    private ConditionCompiler() { }

    public static String compileRequired(JSONObject root, VarContext context) {
        validate(root, "条件", context);
        return DecisionTableCompiler.compileConditionNode(root, context);
    }

    private static void validate(JSONObject node, String position, VarContext context) {
        if (node == null) throw new IllegalArgumentException(position + "不能为空");
        if ("group".equals(node.getString("type"))) {
            String op = node.getString("op");
            if (op != null && !"AND".equals(op) && !"OR".equals(op)) {
                throw new IllegalArgumentException(position + "组的关系必须为 AND 或 OR");
            }
            JSONArray children = node.getJSONArray("children");
            if (children == null || children.isEmpty()) throw new IllegalArgumentException(position + "组至少需要一个条件");
            for (int i = 0; i < children.size(); i++) validate(children.getJSONObject(i), position + "." + (i + 1), context);
            return;
        }
        if (!"leaf".equals(node.getString("type"))) throw new IllegalArgumentException(position + "类型无效");
        String op = node.getString("operator");
        if (op == null || !OPERATORS.contains(op) && !SourceStatusOperators.supports(op)) {
            throw new IllegalArgumentException(position + "的运算符无效");
        }
        boolean complete = ConditionOperandCompiler.supports(node)
                ? ConditionOperandCompiler.hasUsableCondition(node, context)
                : node.getString("varCode") != null && !node.getString("varCode").isBlank()
                  && (ConditionOperandCompiler.isNoValueOperator(op) || node.get("value") != null);
        if (!complete) {
            throw new IllegalArgumentException(position + "未配置完整，请选择比较字段、运算符和所需的判断值");
        }
    }
}
