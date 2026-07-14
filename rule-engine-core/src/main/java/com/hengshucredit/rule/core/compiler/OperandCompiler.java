package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/** 将统一 Operand JSON 编译成 QLExpress 表达式。 */
public final class OperandCompiler {

    private OperandCompiler() {
    }

    public static String compile(JSONObject operand, VarContext varContext) {
        if (operand == null) throw new IllegalArgumentException("表达式参数不能为空");
        String kind = operand.getString("kind");
        if (empty(kind)) throw new IllegalArgumentException("表达式节点类型不能为空");
        if ("LITERAL".equals(kind)) {
            return compileLiteral(operand.get("value"), operand.getString("valueType"));
        }
        if ("PATH".equals(kind) || "REFERENCE".equals(kind)) {
            String code = firstText(operand.getString("code"), operand.getString("value"));
            if (empty(code)) throw new IllegalArgumentException("字段路径不能为空");
            if ("REFERENCE".equals(kind)
                    && (operand.getLong("refId") == null || empty(operand.getString("refType")))) {
                throw new IllegalArgumentException("受管字段引用缺少 ID 或引用类型");
            }
            if (varContext == null) return code;
            if ("REFERENCE".equals(kind) && "CONSTANT".equalsIgnoreCase(operand.getString("refType"))) {
                return varContext.resolveConstant(operand.getLong("refId"));
            }
            return varContext.resolveVar(operand.getLong("refId"), operand.getString("refType"), code);
        }
        if ("FUNCTION".equals(kind)) {
            String functionCode = operand.getString("functionCode");
            if (empty(functionCode)) throw new IllegalArgumentException("方法编码不能为空");
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
        if ("OPERATION".equals(kind)) {
            String operator = operand.getString("operator");
            if (empty(operator)) throw new IllegalArgumentException("运算符不能为空");
            JSONArray operands = operand.getJSONArray("operands");
            if (operands == null || operands.isEmpty()) throw new IllegalArgumentException("运算参数不能为空");
            if (operands.size() == 1) {
                return "(" + operator + compileRequired(operands, 0, varContext) + ")";
            }
            StringBuilder result = new StringBuilder("(");
            for (int i = 0; i < operands.size(); i++) {
                if (i > 0) result.append(' ').append(operator).append(' ');
                result.append(compileRequired(operands, i, varContext));
            }
            return result.append(')').toString();
        }
        if ("ACCESS".equals(kind)) {
            String accessType = operand.getString("accessType");
            String function = "INDEX".equalsIgnoreCase(accessType) ? "arrGet" : "objGet";
            return function + "(" + compileRequired(operand.getJSONObject("target"), varContext)
                    + ", " + compileRequired(operand.getJSONObject("accessor"), varContext) + ")";
        }
        if ("CAST".equals(kind)) {
            String targetType = operand.getString("targetType");
            if (empty(targetType)) throw new IllegalArgumentException("转换目标类型不能为空");
            return castFunction(targetType) + "("
                    + compileRequired(operand.getJSONObject("operand"), varContext) + ")";
        }
        if ("ARRAY".equals(kind)) {
            JSONArray items = operand.getJSONArray("items");
            StringBuilder result = new StringBuilder("[");
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) result.append(", ");
                    result.append(compileRequired(items, i, varContext));
                }
            }
            return result.append(']').toString();
        }
        if ("LIST_QUERY".equals(kind)) {
            JSONArray listIds = operand.getJSONArray("listIds");
            JSONArray itemTypes = operand.getJSONArray("itemTypes");
            String combinationMode = operand.getString("combinationMode");
            String matchMode = operand.getString("matchMode");
            if (listIds == null || listIds.isEmpty()) throw new IllegalArgumentException("名单查询至少选择一个名单");
            if (empty(combinationMode)) throw new IllegalArgumentException("名单组合模式不能为空");
            if (empty(matchMode)) throw new IllegalArgumentException("名单匹配模式不能为空");
            return "listQuery(" + compileArrayValues(listIds, "NUMBER") + ", "
                    + compileArrayValues(itemTypes, "STRING") + ", "
                    + compileLiteral(combinationMode, "STRING") + ", "
                    + compileLiteral(matchMode, "STRING") + ")";
        }
        throw new IllegalArgumentException("不支持的表达式节点类型: " + kind);
    }

    private static String compileRequired(JSONArray values, int index, VarContext varContext) {
        JSONObject operand = values == null ? null : values.getJSONObject(index);
        return compileRequired(operand, varContext);
    }

    private static String compileRequired(JSONObject operand, VarContext varContext) {
        if (operand == null) throw new IllegalArgumentException("表达式参数不能为空");
        return compile(operand, varContext);
    }

    private static String compileArrayValues(JSONArray values, String valueType) {
        StringBuilder result = new StringBuilder("[");
        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) result.append(", ");
                result.append(compileLiteral(values.get(i), valueType));
            }
        }
        return result.append(']').toString();
    }

    private static String castFunction(String targetType) {
        String type = targetType.trim().toUpperCase();
        if (isNumberType(type)) return "toNumberValue";
        if ("BOOLEAN".equals(type) || "BOOL".equals(type)) return "toBooleanValue";
        if ("LIST".equals(type) || "ARRAY".equals(type) || "SET".equals(type)) return "toListValue";
        if ("MAP".equals(type) || "OBJECT".equals(type)) return "toMapValue";
        return "toStringValue";
    }

    static String compileListQuery(JSONObject operand, String queryExpression) {
        if (operand == null || !"LIST_QUERY".equals(operand.getString("kind"))) {
            throw new IllegalArgumentException("名单查询配置无效");
        }
        JSONArray listIds = operand.getJSONArray("listIds");
        JSONArray itemTypes = operand.getJSONArray("itemTypes");
        String combinationMode = operand.getString("combinationMode");
        String matchMode = operand.getString("matchMode");
        if (listIds == null || listIds.isEmpty()) throw new IllegalArgumentException("名单查询至少选择一个名单");
        if (empty(queryExpression)) throw new IllegalArgumentException("名单查询值不能为空");
        if (empty(combinationMode)) throw new IllegalArgumentException("名单组合模式不能为空");
        if (empty(matchMode)) throw new IllegalArgumentException("名单匹配模式不能为空");
        return "listMatch([" + queryExpression + "], " + compileArrayValues(listIds, "NUMBER") + ", "
                + compileLiteral(combinationMode, "STRING") + ", " + compileLiteral(matchMode, "STRING") + ", "
                + compileArrayValues(itemTypes, "STRING") + ")";
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
