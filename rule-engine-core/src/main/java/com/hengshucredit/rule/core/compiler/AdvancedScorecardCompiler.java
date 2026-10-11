package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.LinkedHashSet;

/**
 * 复杂评分卡编译器：支持分组维度 + 结构化条件。
 * 同一维度内的规则互斥（if/else if），维度间得分累加。
 * 通过 {@link VarContext} 将 varCode 解析为正确的 scriptName。
 *
 * <p>VarContext 通过参数传递，不使用 ThreadLocal。
 */
public class AdvancedScorecardCompiler implements RuleCompiler {

    @Override
    public CompileResult compile(String modelJson) {
        return compile(modelJson, null);
    }

    @Override
    public CompileResult compile(String modelJson, VarContext varContext) {
        return doCompile(modelJson, varContext);
    }

    private CompileResult doCompile(String modelJson, VarContext varContext) {
        try {
            JSONObject model = JSON.parseObject(modelJson);
            double initialScore = model.getDoubleValue("initialScore");
            JSONObject resultVar = model.getJSONObject("resultVar");
            JSONArray dimensionGroups = model.getJSONArray("dimensionGroups");

            Long resVarId = resultVar != null && resultVar.containsKey("_varId") ? resultVar.getLong("_varId") : null;
            String resRefType = resultVar != null ? resultVar.getString("_refType") : null;
            JSONObject resultOperand = resultVar != null ? resultVar.getJSONObject("operand") : null;
            String varCode = resultVar == null ? null : resultVar.getString("varCode");
            if (resultVar == null || (resultOperand == null && resVarId == null
                    && (varCode == null || varCode.trim().isEmpty()))) {
                throw new IllegalArgumentException("复杂评分卡必须配置结果变量");
            }
            String resCode = resultOperand != null
                    ? OperandCompiler.compile(resultOperand, varContext)
                    : resolveVar(resVarId, resRefType, varCode, varContext);

            StringBuilder script = new StringBuilder();
            script.append(resCode).append(" = ").append(initialScore).append(";\n");

            if (dimensionGroups != null) {
                for (int g = 0; g < dimensionGroups.size(); g++) {
                    JSONObject group = dimensionGroups.getJSONObject(g);
                    String groupLabel = group.getString("groupLabel");
                    JSONArray dimensions = group.getJSONArray("dimensions");
                    if (dimensions == null) continue;

                    script.append("\n// ---- ").append(groupLabel != null ? groupLabel : "维度组" + (g + 1)).append(" ----\n");

                    for (int d = 0; d < dimensions.size(); d++) {
                        JSONObject dim = dimensions.getJSONObject(d);
                        String dimLabel = dim.getString("varLabel");
                        JSONArray rules = dim.getJSONArray("rules");
                        if (rules == null || rules.isEmpty()) continue;
                        double groupWeight = readWeight(group, "groupWeight");
                        double dimensionWeight = readWeight(dim, "dimensionWeight");

                        String dimScoreVar = "_dim_" + g + "_" + d;
                        script.append(dimScoreVar).append(" = 0;\n");

                        for (int r = 0; r < rules.size(); r++) {
                            JSONObject rule = rules.getJSONObject(r);
                            JSONArray conditions = rule.getJSONArray("conditions");
                            double score = rule.getDoubleValue("score");

                            script.append(r == 0 ? "if (" : " else if (");
                            appendConditions(script, conditions, varContext);
                            script.append(") {\n    ");
                            script.append(dimScoreVar).append(" = ").append(score).append(";\n}");
                        }
                        script.append("\n");

                        script.append("// ").append(dimLabel != null ? dimLabel : "维度" + (d + 1))
                              .append(" 得分累加\n");
                        script.append(resCode).append(" = ").append(resCode)
                              .append(" + ").append(dimScoreVar)
                              .append(" * ").append(formatNumber(groupWeight * dimensionWeight))
                              .append(";\n");
                    }
                }
            }

            String levelVar = ScorecardLevelCompiler.append(script, model, resCode, varContext);
            LinkedHashSet<String> outVars = new LinkedHashSet<>();
            outVars.add(resCode);
            if (levelVar != null) outVars.add(levelVar);
            RuleScriptResultCollector.prependOutputNullInits(script, outVars);
            RuleScriptResultCollector.appendResultMapReturn(script, outVars);

            return CompileResult.ok(script.toString(), "QLEXPRESS");
        } catch (Exception e) {
            return CompileResult.fail("复杂评分卡编译失败: " + e.getMessage());
        }
    }



    /** 将结构化条件数组拼接为 QLExpress 表达式 */
    private void appendConditions(StringBuilder sb, JSONArray conditions, VarContext varContext) {
        if (conditions == null || conditions.isEmpty()) {
            sb.append("true");
            return;
        }
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) sb.append(" && ");
            JSONObject cond = conditions.getJSONObject(i);
            if (ConditionOperandCompiler.supports(cond)) {
                sb.append(ConditionOperandCompiler.compile(cond, varContext));
                continue;
            }
            Long varId = cond.containsKey("_varId") ? cond.getLong("_varId") : null;
            String refType = cond.getString("_refType");
            String varCode = cond.getString("varCode");
            String operator = cond.getString("operator");
            String value = cond.getString("value");

            String scriptName = resolveVar(varId, refType, varCode, varContext);
            sb.append(ConditionExpressionBuilder.build(scriptName, cond.getString("varType"), operator, value, false));
        }
    }

    private String resolveVar(Long varId, String varCode, VarContext varContext) {
        return resolveVar(varId, null, varCode, varContext);
    }

    private static double readWeight(JSONObject holder, String label) {
        if (holder == null || !holder.containsKey("weight")) return 1.0d;
        double value = holder.getDoubleValue("weight");
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            throw new IllegalArgumentException(label + "必须是大于或等于 0 的有限数字");
        }
        return value;
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value)) return String.format(java.util.Locale.ROOT, "%.0f", value);
        return Double.toString(value);
    }

    private String resolveVar(Long varId, String refType, String varCode, VarContext varContext) {
        boolean managedReference = varId != null || (refType != null && !refType.trim().isEmpty());
        if (managedReference && varContext == null) {
            throw new IllegalArgumentException("受管字段引用缺少变量上下文");
        }
        if (varContext != null) return varContext.resolveVar(varId, refType, varCode);
        return varCode != null ? varCode : "";
    }

}
