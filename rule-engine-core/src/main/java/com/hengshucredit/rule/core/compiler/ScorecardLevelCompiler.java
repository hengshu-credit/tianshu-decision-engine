package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/** 两类评分卡共用的可选等级赋值，未配置目标或区间时不产生隐式输出。 */
public final class ScorecardLevelCompiler {
    private ScorecardLevelCompiler() { }

    public static JSONObject getTarget(JSONObject model) {
        // 显式清空新字段后不能恢复旧区间中的赋值目标。
        if (model.containsKey("levelVar")) return model.getJSONObject("levelVar");
        JSONArray thresholds = model.getJSONArray("thresholds");
        if (thresholds == null || thresholds.isEmpty()) return null;
        JSONObject first = thresholds.getJSONObject(0);
        if (first == null || blank(first.getString("resultVar"))) return null;
        JSONObject target = new JSONObject();
        target.put("varCode", first.getString("resultVar"));
        target.put("_varId", first.get("_varId"));
        target.put("_refType", first.get("_refType"));
        return target;
    }

    public static boolean isEnabled(JSONObject model) {
        JSONArray thresholds = model.getJSONArray("thresholds");
        JSONObject target = getTarget(model);
        return thresholds != null && !thresholds.isEmpty() && target != null
                && (target.getJSONObject("operand") != null || target.get("_varId") != null || !blank(target.getString("varCode")));
    }

    public static String append(StringBuilder script, JSONObject model, String scoreVar, VarContext context) {
        if (!isEnabled(model)) return null;
        JSONArray thresholds = model.getJSONArray("thresholds");
        IntervalValidator.validateThresholds(thresholds);
        JSONObject target = getTarget(model);
        JSONObject operand = target.getJSONObject("operand");
        String levelVar;
        if (operand != null) {
            String kind = operand.getString("kind");
            if ((!"REFERENCE".equals(kind) && !"PATH".equals(kind))
                    || "CONSTANT".equalsIgnoreCase(operand.getString("refType"))
                    || "FIXED".equalsIgnoreCase(operand.getString("versionMode"))) {
                throw new IllegalArgumentException("等级赋值字段必须是可写字段或路径");
            }
            levelVar = OperandCompiler.compile(operand, context);
        } else {
            Long id = target.getLong("_varId");
            String type = target.getString("_refType");
            if (context == null && (id != null || !blank(type))) {
                throw new IllegalArgumentException("受管字段引用缺少变量上下文");
            }
            levelVar = context == null ? target.getString("varCode") : context.resolveVar(id, type, target.getString("varCode"));
        }
        if (scoreVar.equals(levelVar)) throw new IllegalArgumentException("评分赋值字段与等级赋值字段不能相同");
        script.append("\n// ---- 等级判定 ----\n").append(levelVar).append(" = \"未知\";\n");
        for (int i = 0; i < thresholds.size(); i++) {
            JSONObject threshold = thresholds.getJSONObject(i);
            script.append(i == 0 ? "if (" : " else if (")
                    .append(scoreVar).append(" >= ").append(threshold.getDoubleValue("min"))
                    .append(" && ").append(scoreVar).append(" < ").append(threshold.getDoubleValue("max"))
                    .append(") {\n    ").append(levelVar).append(" = ");
            JSONObject value = threshold.getJSONObject("resultOperand");
            script.append(value != null ? OperandCompiler.compile(value, context)
                    : OperandCompiler.compileLiteral(threshold.getString("result"), "STRING"));
            script.append("\n}");
        }
        script.append("\n");
        return levelVar;
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
