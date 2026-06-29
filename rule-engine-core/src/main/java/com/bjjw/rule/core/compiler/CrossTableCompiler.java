package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.LinkedHashSet;

/**
 * 交叉表编译器：将行列条件交叉矩阵编译为 QLExpress 脚本。
 * 支持左侧条件列 + 顶部条件行，交叉单元格为命中动作。
 * 通过 {@link VarContext} 将 varCode 解析为正确的 scriptName。
 *
 * <p>VarContext 通过参数传递，不使用 ThreadLocal。
 */
public class CrossTableCompiler implements RuleCompiler {

    private VarContext varContext;

    @Override
    public CompileResult compile(String modelJson) {
        return compile(modelJson, null);
    }

    @Override
    public CompileResult compile(String modelJson, VarContext varContext) {
        this.varContext = varContext;
        return doCompile(modelJson);
    }

    private CompileResult doCompile(String modelJson) {
        try {
            JSONObject model = JSON.parseObject(modelJson);
            JSONArray rowDefs = model.getJSONArray("rowDefs");
            JSONArray colDefs = model.getJSONArray("colDefs");
            JSONArray cells = model.getJSONArray("cells");
            JSONObject defaultAction = model.getJSONObject("defaultAction");

            if ((cells == null || cells.isEmpty()) && defaultAction == null) {
                return CompileResult.fail("交叉表模型缺少必要字段: cells 或 defaultAction");
            }

            LinkedHashSet<String> outputVarCodes = new LinkedHashSet<>();

            StringBuilder script = new StringBuilder();
            script.append("// 交叉表编译脚本\n");

            // 初始化输出变量（默认 null）
            if (!outputVarCodes.isEmpty()) {
                RuleScriptResultCollector.prependOutputNullInits(script, outputVarCodes);
            }

            // 行列维度定义
            if (rowDefs != null) {
                for (int i = 0; i < rowDefs.size(); i++) {
                    JSONObject rd = rowDefs.getJSONObject(i);
                    Long varId = rd.containsKey("_varId") ? rd.getLong("_varId") : null;
                    String refType = rd.getString("_refType");
                    String varCode = rd.getString("varCode");
                    String varType = rd.getString("varType");
                    if (varType == null) varType = "STRING";

                    script.append("// 行条件: ").append(varCode).append("\n");
                    // 输出变量初始化（交叉表命中时赋值），通过 VarContext 解析为正确的 scriptName
                    String outVar = rd.getString("resultVar");
                    if (outVar != null && !outVar.isEmpty()) {
                        String resolved = resolveVar(varId, refType, outVar, this.varContext);
                        outputVarCodes.add(resolved);
                        script.append(resolved).append(" = null;\n");
                    }

                    JSONArray conditions = rd.getJSONArray("conditions");
                    if (conditions != null && !conditions.isEmpty()) {
                        script.append(compileRowCondition(varId, refType, varCode, varType, conditions, this.varContext));
                    }
                }
            }

            if (colDefs != null) {
                for (int i = 0; i < colDefs.size(); i++) {
                    JSONObject cd = colDefs.getJSONObject(i);
                    Long varId = cd.containsKey("_varId") ? cd.getLong("_varId") : null;
                    String refType = cd.getString("_refType");
                    String varCode = cd.getString("varCode");
                    String varType = cd.getString("varType");
                    if (varType == null) varType = "STRING";

                    script.append("// 列条件: ").append(varCode).append("\n");
                    JSONArray conditions = cd.getJSONArray("conditions");
                    if (conditions != null && !conditions.isEmpty()) {
                        script.append(compileRowCondition(varId, refType, varCode, varType, conditions, this.varContext));
                    }
                }
            }

            // 交叉单元格
            if (cells != null && !cells.isEmpty()) {
                for (int i = 0; i < cells.size(); i++) {
                    JSONObject cell = cells.getJSONObject(i);
                    int row = cell.getIntValue("row");
                    int col = cell.getIntValue("col");
                    JSONObject action = cell.getJSONObject("action");

                    script.append("// [").append(row).append(", ").append(col).append("]\n");
                    script.append("if (");
                    boolean hasRow = row < (rowDefs != null ? rowDefs.size() : 0);
                    boolean hasCol = col < (colDefs != null ? colDefs.size() : 0);

                    if (hasRow) {
                        JSONObject rd = rowDefs.getJSONObject(row);
                        script.append(compileCellVarPredicate(rd, true, this.varContext));
                    }
                    if (hasRow && hasCol) {
                        script.append(" && ");
                    }
                    if (hasCol) {
                        JSONObject cd = colDefs.getJSONObject(col);
                        script.append(compileCellVarPredicate(cd, false, this.varContext));
                    }
                    if (!hasRow && !hasCol) {
                        script.append("true");
                    }
                    script.append(") {\n");
                    if (action != null) {
                        script.append("    ");
                        String varCode = action.getString("varCode");
                        String value = action.getString("value");
                        if (varCode != null && value != null) {
                            String varType = action.getString("varType");
                            if (varType == null) varType = "STRING";
                            Long varId = action.containsKey("_varId") ? action.getLong("_varId") : null;
                            String refType = action.getString("_refType");
                            String resolved = resolveVar(varId, refType, varCode, this.varContext);
                            if ("STRING".equals(varType) || "ENUM".equals(varType)) {
                                script.append(resolved).append(" = \"")
                                      .append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append("\";\n");
                            } else {
                                script.append(resolved).append(" = ").append(value).append(";\n");
                            }
                            outputVarCodes.add(resolved);
                        }
                    }
                    script.append("}\n");
                }
            }

            // 默认动作
            if (defaultAction != null) {
                script.append("// 默认动作\n");
                String varCode = defaultAction.getString("varCode");
                String value = defaultAction.getString("value");
                if (varCode != null && value != null) {
                    String varType = defaultAction.getString("varType");
                    if (varType == null) varType = "STRING";
                    Long varId = defaultAction.containsKey("_varId") ? defaultAction.getLong("_varId") : null;
                    String refType = defaultAction.getString("_refType");
                    String resolved = resolveVar(varId, refType, varCode, this.varContext);
                    if ("STRING".equals(varType) || "ENUM".equals(varType)) {
                        script.append(resolved).append(" = \"")
                              .append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append("\";\n");
                    } else {
                        script.append(resolved).append(" = ").append(value).append(";\n");
                    }
                    outputVarCodes.add(resolved);
                }
            }

            if (!outputVarCodes.isEmpty()) {
                RuleScriptResultCollector.prependOutputNullInits(script, outputVarCodes);
                RuleScriptResultCollector.appendResultMapReturn(script, outputVarCodes);
            }

            return CompileResult.ok(script.toString(), "QLEXPRESS");
        } catch (Exception e) {
            return CompileResult.fail("交叉表编译失败: " + e.getMessage());
        }
    }

    /**
     * 编译行条件（条件段）：varCode op value AND ...。
     * 例如 age >= 18 && age < 60
     */
    private static String compileRowCondition(Long varId, String refType, String varCode, String varType, JSONArray conditions, VarContext varContext) {
        if (conditions == null || conditions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("//   ").append(varCode).append(" 条件段: ");
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) sb.append(" && ");
            JSONObject c = conditions.getJSONObject(i);
            String op = c.getString("operator");
            String val = c.getString("value");
            if (op == null) op = "==";
            sb.append(resolveVar(varId, refType, varCode, varContext)).append(" ").append(op).append(" ");
            if ("STRING".equals(varType) || "ENUM".equals(varType)) {
                sb.append("\"").append(val.replace("\"", "\\\"")).append("\"");
            } else {
                sb.append(val);
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * 编译单元格内的变量条件（从 row/col 定义中提取单条件，或从 conditionRoot 中解析）。
     */
    private static String compileCellVarPredicate(JSONObject def, boolean isRow, VarContext varContext) {
        if (def == null) return "true";

        Long varId = def.containsKey("_varId") ? def.getLong("_varId") : null;
        String refType = def.getString("_refType");
        String varCode = def.getString("varCode");
        String varType = def.getString("varType");
        if (varType == null) varType = "STRING";

        // 优先用 conditionRoot（结构化条件树）
        JSONObject root = def.getJSONObject("conditionRoot");
        if (root != null && !root.isEmpty() && "group".equals(root.getString("type"))) {
            return compileConditionNode(root, varContext);
        }

        // 回退到 conditions 数组
        JSONArray conditions = def.getJSONArray("conditions");
        if (conditions == null || conditions.isEmpty()) {
            return "true";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) sb.append(" && ");
            JSONObject c = conditions.getJSONObject(i);
            String op = c.getString("operator");
            String val = c.getString("value");
            if (op == null) op = "==";
            if (val == null) {
                sb.append("true");
                continue;
            }
            String scriptName = resolveVar(varId, refType, varCode, varContext);
            sb.append(scriptName).append(" ").append(op).append(" ");
            if ("STRING".equals(varType) || "ENUM".equals(varType)) {
                sb.append("\"").append(val.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            } else {
                sb.append(val);
            }
        }
        return sb.toString();
    }

    private static String compileConditionNode(JSONObject node, VarContext varContext) {
        if (node == null || node.isEmpty()) return "true";
        String type = node.getString("type");
        if ("group".equals(type)) {
            String op = node.getString("op");
            if (op == null) op = "AND";
            JSONArray children = node.getJSONArray("children");
            if (children == null || children.isEmpty()) return "true";
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) sb.append("AND".equals(op) ? " && " : " || ");
                sb.append(compileConditionNode(children.getJSONObject(i), varContext));
            }
            sb.append(")");
            return sb.toString();
        }
        if ("leaf".equals(type)) {
            return compileLeaf(node, varContext);
        }
        return "true";
    }

    private static String compileLeaf(JSONObject leaf, VarContext varContext) {
        Long varId = leaf.containsKey("_varId") ? leaf.getLong("_varId") : null;
        String refType = leaf.getString("_refType");
        String varCode = leaf.getString("varCode");
        if (varCode == null || varCode.trim().isEmpty()) return "true";
        String op = leaf.getString("operator");
        if (op == null) op = "==";
        if ("*".equals(op)) return "true";
        String value = leaf.getString("value");
        if (value == null || value.isEmpty()) return "true";
        String varType = leaf.getString("varType");
        if (varType == null) varType = "STRING";
        String scriptName = resolveVar(varId, refType, varCode, varContext);
        String rhs;
        if ("STRING".equals(varType) || "ENUM".equals(varType) || "DATE".equals(varType)) {
            rhs = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        } else {
            rhs = value;
        }
        return scriptName + " " + op + " " + rhs;
    }

    /**
     * 通过 VarContext 解析脚本变量名。
     */
    private static String resolveVar(Long varId, String varCode, VarContext varContext) {
        return resolveVar(varId, null, varCode, varContext);
    }

    private static String resolveVar(Long varId, String refType, String varCode, VarContext varContext) {
        if (varContext != null) {
            return varContext.resolveVar(varId, refType, varCode);
        }
        return varCode != null ? varCode : "";
    }
}
