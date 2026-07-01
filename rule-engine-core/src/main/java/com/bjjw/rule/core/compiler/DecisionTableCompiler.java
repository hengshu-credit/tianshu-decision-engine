package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.LinkedHashSet;

/**
 * 决策表：将 JSON 模型编译为 QLExpress 脚本。
 * 条件支持每条规则上的 {@code conditionRoot} 树（与/或嵌套）或旧版「条件列 + 行条件数组」。
 * 动作支持每条规则内自带变量定义（varCode/varType/value），或旧版「全局 actions 列 + 行上仅 value」。
 *
 * <p>通过 {@link VarContext} 将模型 JSON 中的 varCode 解析为正确的 scriptName，
 * 解决变量管理中 varCode 与 scriptName 大小写不一致导致的运行时找不到变量问题。
 *
 * <p>VarContext 通过参数传递，不使用 ThreadLocal。
 */
public class DecisionTableCompiler implements RuleCompiler {

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
            String hitPolicy = model.getString("hitPolicy");
            if (hitPolicy == null) hitPolicy = "FIRST";
            JSONArray legacyColumnDefs = model.getJSONArray("conditions");
            if (legacyColumnDefs == null) legacyColumnDefs = new JSONArray();
            JSONArray globalActionDefs = model.getJSONArray("actions");
            if (globalActionDefs == null) globalActionDefs = new JSONArray();
            JSONArray rules = model.getJSONArray("rules");

            if (rules == null) {
                return CompileResult.fail("决策表模型缺少必要字段: rules");
            }

            LinkedHashSet<String> outputVarCodes = collectOutputVarCodes(rules, globalActionDefs, this.varContext);

            StringBuilder script = new StringBuilder();
            boolean isFirst = "FIRST".equals(hitPolicy);
            boolean isUnique = "UNIQUE".equals(hitPolicy);
            if (isUnique) {
                script.append("_uniqueHitCount = 0;\n");
            }

            for (int i = 0; i < rules.size(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                JSONArray ruleConditions = rule.getJSONArray("conditions");
                JSONArray ruleActions = rule.getJSONArray("actions");

                if (ruleConditions == null) ruleConditions = new JSONArray();
                if (ruleActions == null) ruleActions = new JSONArray();

                if (i == 0 || !isFirst) {
                    script.append("if (");
                } else {
                    script.append(" else if (");
                }

                script.append(buildRulePredicate(rule, ruleConditions, legacyColumnDefs, this.varContext));
                script.append(") {\n");

                if (isUnique) {
                    script.append("    _uniqueHitCount = _uniqueHitCount + 1;\n");
                }
                appendRuleAssignments(script, ruleActions, globalActionDefs, this.varContext);

                script.append("}");
                if (!isFirst) script.append("\n");
            }
            script.append("\n");
            if (isUnique) {
                script.append("if (_uniqueHitCount > 1) {\n");
                script.append("    UNIQUE_HIT_POLICY_MATCHED_MULTIPLE_RULES();\n");
                script.append("}\n");
            }

            if (!outputVarCodes.isEmpty()) {
                RuleScriptResultCollector.prependOutputNullInits(script, outputVarCodes);
                RuleScriptResultCollector.appendResultMapReturn(script, outputVarCodes);
            }

            return CompileResult.ok(script.toString(), "QLEXPRESS");
        } catch (Exception e) {
            return CompileResult.fail("决策表编译失败: " + e.getMessage());
        }
    }

    /**
     * 汇总所有规则中会出现的输出变量（用于结果 Map 与初始化）。
     * 输出变量名通过 {@link VarContext} 解析为 scriptName（与条件解析一致），
     * 保证结果 Map 的键名与 QLExpress 上下文中实际使用的变量名一致。
     */
    static LinkedHashSet<String> collectOutputVarCodes(JSONArray rules, JSONArray globalActionDefs, VarContext varContext) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (int i = 0; i < rules.size(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            JSONArray ruleActions = rule.getJSONArray("actions");
            if (ruleActions == null) continue;
            for (int k = 0; k < ruleActions.size(); k++) {
                JSONObject act = ruleActions.getJSONObject(k);
                if (act == null) continue;
                String vc = null;
                Long varId = act.containsKey("_varId") ? act.getLong("_varId") : null;
                String refType = act.getString("_refType");
                if (act.containsKey("varCode")) {
                    vc = act.getString("varCode");
                } else if (globalActionDefs != null && k < globalActionDefs.size()) {
                    JSONObject def = globalActionDefs.getJSONObject(k);
                    if (def != null) {
                        vc = def.getString("varCode");
                        if (varId == null && def.containsKey("_varId")) {
                            varId = def.getLong("_varId");
                        }
                        if (refType == null) {
                            refType = def.getString("_refType");
                        }
                    }
                }
                if (vc != null && !vc.trim().isEmpty()) {
                    String resolved = resolveVar(varId, refType, vc, varContext);
                    set.add(resolved);
                }
            }
        }
        return set;
    }

    /**
     * 同上，无 VarContext 的兼容重载。
     */
    static LinkedHashSet<String> collectOutputVarCodes(JSONArray rules, JSONArray globalActionDefs) {
        return collectOutputVarCodes(rules, globalActionDefs, null);
    }

    /**
     * 输出单条规则 then 体内的赋值语句。
     * 输出变量名通过 {@link VarContext} 解析为 scriptName（与条件解析一致），
     * 保证 QLExpress 中赋值的变量名与脚本输出期望一致。
     */
    static void appendRuleAssignments(StringBuilder script, JSONArray ruleActions, JSONArray globalActionDefs, VarContext varContext) {
        for (int k = 0; k < ruleActions.size(); k++) {
            JSONObject act = ruleActions.getJSONObject(k);
            JSONObject actDef = k < globalActionDefs.size() ? globalActionDefs.getJSONObject(k) : null;

            String varCode;
            String varType;
            String value;
            Long varId = null;
            String refType = null;

            if (act != null && act.containsKey("varCode")) {
                varCode = act.getString("varCode");
                varType = act.getString("varType");
                if (varType == null) varType = "STRING";
                value = act.getString("value");
                varId = act.containsKey("_varId") ? act.getLong("_varId") : null;
                refType = act.getString("_refType");
            } else {
                varCode = actDef != null ? actDef.getString("varCode") : "out" + k;
                varType = actDef != null ? actDef.getString("varType") : "NUMBER";
                value = act != null ? act.getString("value") : null;
                varId = actDef != null && actDef.containsKey("_varId") ? actDef.getLong("_varId") : null;
                refType = actDef != null ? actDef.getString("_refType") : null;
            }

            if (varCode == null || varCode.trim().isEmpty()) {
                continue;
            }
            if (value == null) {
                continue;
            }

            String resolvedVar = resolveVar(varId, refType, varCode.trim(), varContext);
            script.append("    ").append(resolvedVar).append(" = ");
            if ("STRING".equals(varType) || "ENUM".equals(varType)) {
                script.append("\"").append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            } else {
                script.append(value);
            }
            script.append(";\n");
        }
    }

    /**
     * 同上，无 VarContext 的兼容重载。
     */
    static void appendRuleAssignments(StringBuilder script, JSONArray ruleActions, JSONArray globalActionDefs) {
        appendRuleAssignments(script, ruleActions, globalActionDefs, null);
    }

    /**
     * 生成单条规则的条件布尔表达式：优先 {@code conditionRoot}，否则旧版列对齐的 AND。
     */
    static String buildRulePredicate(JSONObject rule, JSONArray legacyRuleConditions, JSONArray legacyColumnDefs, VarContext varContext) {
        JSONObject root = rule.getJSONObject("conditionRoot");
        if (root != null && !root.isEmpty()) {
            // compileConditionNode 同时处理 group 和 leaf 类型
            return compileConditionNode(root, varContext);
        }
        return compileLegacyFlatAnd(legacyRuleConditions, legacyColumnDefs, varContext);
    }

    /**
     * 递归编译条件树节点（组或叶）。
     */
    static String compileConditionNode(JSONObject node, VarContext varContext) {
        if (node == null || node.isEmpty()) {
            return "true";
        }
        String type = node.getString("type");
        if ("group".equals(type)) {
            String op = node.getString("op");
            if (op == null) op = "AND";
            JSONArray children = node.getJSONArray("children");
            if (children == null || children.isEmpty()) {
                return "true";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) {
                    sb.append("AND".equals(op) ? " && " : " || ");
                }
                JSONObject ch = children.getJSONObject(i);
                sb.append(compileConditionNode(ch, varContext));
            }
            sb.append(")");
            return sb.toString();
        }
        if ("leaf".equals(type)) {
            return compileLeaf(node, varContext);
        }
        return "true";
    }

    /**
     * 编译叶条件：比较左侧变量与常量或其它变量。
     */
    static String compileLeaf(JSONObject leaf, VarContext varContext) {
        Long varId = leaf.containsKey("_varId") ? leaf.getLong("_varId") : null;
        String refType = leaf.getString("_refType");
        String varCode = leaf.getString("varCode");
        if (varCode == null || varCode.trim().isEmpty()) {
            return "true";
        }
        String operator = leaf.getString("operator");
        if (operator == null) operator = "==";
        if ("*".equals(operator)) {
            return "true";
        }

        String valueKind = leaf.getString("valueKind");
        if (valueKind == null) valueKind = "CONST";

        if ("VAR".equalsIgnoreCase(valueKind)) {
            String right = leaf.getString("value");
            if (right == null || right.trim().isEmpty()) {
                return "true";
            }
            return resolveVar(varId, refType, varCode, varContext) + " " + operator + " " + right.trim();
        }

        String value = leaf.getString("value");
        if (value == null || value.isEmpty()) {
            return "true";
        }

        String varType = leaf.getString("varType");
        if (varType == null) varType = "STRING";

        String rhs = formatConstantRhs(varType, value);
        return resolveVar(varId, refType, varCode, varContext) + " " + operator + " " + rhs;
    }

    /**
     * 通过 VarContext 解析变量引用名。
     * 优先通过 varId 精确查 scriptName，回退到 varCode 查找。
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

    /**
     * 按类型格式化常量右侧（引号与转义）。
     */
    static String formatConstantRhs(String varType, String value) {
        if ("STRING".equals(varType) || "ENUM".equals(varType) || "DATE".equals(varType)) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return value;
    }

    /**
     * 旧版：多列条件，全部 AND。
     * 通过 VarContext 将 varCode 解析为正确的 scriptName。
     */
    static String compileLegacyFlatAnd(JSONArray ruleConditions, JSONArray conditions, VarContext varContext) {
        if (ruleConditions == null || ruleConditions.isEmpty()) {
            return "true";
        }
        StringBuilder script = new StringBuilder();
        for (int j = 0; j < ruleConditions.size(); j++) {
            if (j > 0) script.append(" && ");
            JSONObject cond = ruleConditions.getJSONObject(j);
            JSONObject condDef = j < conditions.size() ? conditions.getJSONObject(j) : null;
            JSONObject source = condDef != null ? condDef : cond;
            Long varId = source != null && source.containsKey("_varId") ? source.getLong("_varId") : null;
            String refType = source != null ? source.getString("_refType") : null;
            String varCode = source != null ? source.getString("varCode") : "var" + j;
            if (varCode == null || varCode.trim().isEmpty()) {
                varCode = "var" + j;
            }
            String operator = cond.getString("operator");
            if (operator == null || operator.trim().isEmpty()) {
                operator = "==";
            }
            String value = cond.getString("value");

            if (value == null || value.isEmpty()) {
                script.append("true");
                continue;
            }

            String varType = source != null ? source.getString("varType") : "STRING";
            if (varType == null || varType.trim().isEmpty()) {
                varType = "STRING";
            }
            String resolvedVar = resolveVar(varId, refType, varCode, varContext);
            script.append(resolvedVar).append(" ").append(operator).append(" ");
            if ("STRING".equals(varType) || "ENUM".equals(varType)) {
                script.append("\"").append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            } else {
                script.append(value);
            }
        }
        return script.toString();
    }
}
