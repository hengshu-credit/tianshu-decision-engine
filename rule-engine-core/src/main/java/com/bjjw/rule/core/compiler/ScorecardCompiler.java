package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.LinkedHashSet;

/**
 * 评分卡编译器：将特征分段打分模型编译为 QLExpress 脚本。
 * 支持初始分数 + 评分项命中加分 + 权重配置 + 等级阈值判定。
 * 通过 {@link VarContext} 将条件中的 varCode 解析为正确的 scriptName。
 */
public class ScorecardCompiler implements RuleCompiler {

    private static final ThreadLocal<VarContext> CTX = new ThreadLocal<>();

    @Override
    public CompileResult compile(String modelJson) {
        return compile(modelJson, null);
    }

    @Override
    public CompileResult compile(String modelJson, VarContext varContext) {
        CTX.set(varContext);
        try {
            return doCompile(modelJson);
        } finally {
            CTX.remove();
        }
    }

    private CompileResult doCompile(String modelJson) {
        try {
            JSONObject model = JSON.parseObject(modelJson);
            double initialScore = model.getDoubleValue("initialScore");
            JSONObject resultVar = model.getJSONObject("resultVar");
            JSONArray scoreItems = model.getJSONArray("scoreItems");
            JSONArray thresholds = model.getJSONArray("thresholds");

            String resCode = resultVar != null ? resultVar.getString("varCode") : "totalScore";

            StringBuilder script = new StringBuilder();
            script.append(resCode).append(" = ").append(initialScore).append(";\n");

            if (scoreItems != null) {
                for (int i = 0; i < scoreItems.size(); i++) {
                    JSONObject item = scoreItems.getJSONObject(i);
                    String rawCondition = item.getString("condition");
                    double score = item.getDoubleValue("score");
                    double weight = item.containsKey("weight") ? item.getDoubleValue("weight") : 1.0;

                    script.append("// 评分项 #").append(i + 1).append("\n");

                    if (rawCondition != null && !rawCondition.isEmpty()) {
                        // 旧版：直接使用 condition 脚本表达式
                        script.append("if (").append(rawCondition).append(") {\n");
                        script.append("    ").append(resCode).append(" = ").append(resCode)
                              .append(" + ").append(score * weight).append(";\n");
                        script.append("}\n");
                    } else {
                        // 新版：结构化字段 condVar / condOperator / condValue
                        String condVar = item.getString("condVar");
                        String condOp = item.getString("condOperator");
                        String condValue = item.getString("condValue");
                        if (condVar != null && !condVar.isEmpty() && condValue != null) {
                            if (condOp == null) condOp = "==";
                            Long varId = item.containsKey("_varId") ? item.getLong("_varId") : null;
                            String scriptName = resolveVar(varId, condVar);
                            script.append("if (").append(scriptName).append(" ").append(condOp).append(" ");
                            script.append(formatRhs(condValue)).append(") {\n");
                            script.append("    ").append(resCode).append(" = ").append(resCode)
                                  .append(" + ").append(score * weight).append(";\n");
                            script.append("}\n");
                        }
                    }
                }
            }

            // 等级阈值判定（thresholds）
            String levelVar = "riskLevel";
            if (thresholds != null && !thresholds.isEmpty()) {
                JSONObject firstTh = thresholds.getJSONObject(0);
                if (firstTh != null && firstTh.containsKey("resultVar")) {
                    String rv = firstTh.getString("resultVar");
                    if (rv != null && !rv.isEmpty()) {
                        levelVar = rv;
                    }
                }
                script.append("\n").append(levelVar).append(" = \"未知\";\n");
                for (int i = 0; i < thresholds.size(); i++) {
                    JSONObject th = thresholds.getJSONObject(i);
                    double min = th.getDoubleValue("min");
                    double max = th.getDoubleValue("max");
                    String result = th.getString("result");

                    script.append(i == 0 ? "if (" : " else if (");
                    script.append(resCode).append(" >= ").append(min).append(" && ")
                          .append(resCode).append(" < ").append(max).append(") {\n");
                    script.append("    ").append(levelVar).append(" = \"")
                          .append(escapeForQlDoubleQuotedString(result)).append("\"\n}");
                }
                script.append("\n");

                LinkedHashSet<String> outVars = new LinkedHashSet<>();
                outVars.add(resCode);
                outVars.add(levelVar);
                RuleScriptResultCollector.prependOutputNullInits(script, outVars);
                RuleScriptResultCollector.appendResultMapReturn(script, outVars);
            } else {
                LinkedHashSet<String> outVars = new LinkedHashSet<>();
                outVars.add(resCode);
                RuleScriptResultCollector.prependOutputNullInits(script, outVars);
                RuleScriptResultCollector.appendResultMapReturn(script, outVars);
            }

            return CompileResult.ok(script.toString(), "QLEXPRESS");
        } catch (Exception e) {
            return CompileResult.fail("评分卡编译失败: " + e.getMessage());
        }
    }

    private static String resolveVar(Long varId, String varCode) {
        VarContext ctx = CTX.get();
        if (ctx != null) {
            return ctx.resolveVar(varId, varCode);
        }
        return varCode != null ? varCode : "";
    }

    private static String formatRhs(String value) {
        if (value == null || value.isEmpty()) return "\"\"";
        try { Double.parseDouble(value); return value; } catch (NumberFormatException ignored) {}
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String escapeForQlDoubleQuotedString(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}