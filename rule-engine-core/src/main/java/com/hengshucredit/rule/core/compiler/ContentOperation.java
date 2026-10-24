package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.function.AggregateBuiltinFunctions;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** 内容表达式复用条件的谓词语义，保留四则与逻辑运算优先级。 */
public final class ContentOperation {
    private ContentOperation() { }
    private static final Set<String> UNARY = Set.of("is_null", "not_null", "is_empty", "not_empty", "is_true", "is_false");
    private static final Set<String> BINARY = Set.of("contains", "not_contains", "starts_with", "not_starts_with", "ends_with", "not_ends_with",
            "regex_match", "not_regex_match", "in_array", "not_in_array", "contains_any", "contains_all", "has_key", "not_has_key", "has_value", "not_has_value",
            "array_element_contains", "array_element_not_contains", "array_element_starts_with", "array_element_ends_with", "size_eq", "size_gt", "size_gte", "size_lt", "size_lte");
    private static final AggregateBuiltinFunctions FUNCTIONS = new AggregateBuiltinFunctions();
    public static boolean unary(String op) { return op != null && UNARY.contains(op); }
    public static boolean predicate(String op) { return unary(op) || op != null && BINARY.contains(op); }
    public static int precedence(String op) {
        if (unary(op)) return 7;
        if (op == null) return -1;
        return switch (op) { case "*", "/", "%" -> 6; case "+", "-" -> 5; case ">", ">=", "<", "<=" -> 4;
            case "==", "!=" -> 3; case "&&" -> 2; case "||" -> 1; default -> predicate(op) ? 4 : -1; };
    }
    public static String compile(JSONArray terms, Function<JSONObject, String> compiler) {
        List<String> values = new ArrayList<>(), operators = new ArrayList<>();
        for (int i = 0; i < terms.size(); i++) {
            JSONObject term = terms.getJSONObject(i);
            if (term == null) throw new IllegalArgumentException("运算项不能为空");
            String op = term.getString("operator");
            if (i == 0) {
                if (op != null && !op.isBlank()) throw new IllegalArgumentException("首项不能设置运算符");
                values.add(compiler.apply(term.getJSONObject("operand"))); continue;
            }
            if (precedence(op) < 0) throw new IllegalArgumentException("不支持的运算符: " + op);
            if (unary(op)) { values.add(ConditionExpressionBuilder.build(values.remove(values.size() - 1), "", op, null, true)); continue; }
            while (!operators.isEmpty() && precedence(operators.get(operators.size() - 1)) >= precedence(op)) apply(values, operators);
            operators.add(op); values.add(compiler.apply(term.getJSONObject("operand")));
        }
        while (!operators.isEmpty()) apply(values, operators);
        return "(" + values.get(0) + ")";
    }
    private static void apply(List<String> values, List<String> operators) {
        String op = operators.remove(operators.size() - 1), right = values.remove(values.size() - 1), left = values.remove(values.size() - 1);
        if ("contains_any".equals(op) || "contains_all".equals(op)) values.add(("contains_any".equals(op) ? "containsAnyValue(" : "containsAllValues(") + left + ", " + right + ")");
        else values.add(predicate(op) ? ConditionExpressionBuilder.build(left, "", op, right, true) : "(" + left + " " + op + " " + right + ")");
    }
    public static boolean evaluate(String op, Object left, Object right) {
        if ("is_null".equals(op)) return left == null;
        if ("not_null".equals(op)) return left != null;
        if ("is_empty".equals(op)) return FUNCTIONS.isBlank(left);
        if ("not_empty".equals(op)) return !FUNCTIONS.isBlank(left);
        if ("is_true".equals(op)) return Boolean.TRUE.equals(left);
        if ("is_false".equals(op)) return Boolean.FALSE.equals(left);
        boolean negative = op.startsWith("not_") || "array_element_not_contains".equals(op);
        if (negative && FUNCTIONS.isBlank(left)) return false;
        String positive = op.startsWith("not_") ? op.substring(4) : op.replace("_not_", "_");
        boolean result = switch (positive) {
            case "contains" -> FUNCTIONS.containsValue(left, right);
            case "starts_with" -> FUNCTIONS.startsWithValue(left, right);
            case "ends_with" -> FUNCTIONS.endsWithValue(left, right);
            case "regex_match" -> FUNCTIONS.regexMatchValue(left, right);
            case "in_array" -> FUNCTIONS.containsValue(right, left);
            case "contains_any" -> FUNCTIONS.containsAnyValue(left, right);
            case "contains_all" -> FUNCTIONS.containsAllValues(left, right);
            case "has_key" -> FUNCTIONS.hasKey(left, right);
            case "has_value" -> FUNCTIONS.hasMapValue(left, right);
            case "array_element_contains" -> FUNCTIONS.containsElementValue(left, right);
            case "array_element_starts_with" -> FUNCTIONS.elementStartsWithValue(left, right);
            case "array_element_ends_with" -> FUNCTIONS.elementEndsWithValue(left, right);
            default -> {
                if (!positive.startsWith("size_")) throw new IllegalArgumentException("不支持的运算符: " + op);
                int compared = BigDecimal.valueOf(FUNCTIONS.sizeOfValue(left)).compareTo(new BigDecimal(String.valueOf(right)));
                yield switch (positive) { case "size_eq" -> compared == 0; case "size_gt" -> compared > 0; case "size_gte" -> compared >= 0;
                    case "size_lt" -> compared < 0; case "size_lte" -> compared <= 0; default -> throw new IllegalArgumentException("不支持的运算符: " + op); };
            }
        };
        return negative ? !result : result;
    }
}
