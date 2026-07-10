package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * actionData JSON → QLExpress 脚本生成器（后端 Java 版）
 *
 * 支持块类型：assign, if-block, switch-block, func-call, foreach, ternary, in-check, template-str
 * 通过 {@link VarContext} 将 condVar / checkVar / matchVar 等字段中的 varCode 解析为正确的 scriptName。
 */
public class ActionDataCompiler {

    /**
     * 无变量上下文的编译（兼容旧调用）。
     */
    public static String compile(JSONArray actionData) {
        return compile(actionData, null);
    }

    /**
     * 带变量上下文的编译，condVar / checkVar / matchVar 等将通过 VarContext 解析为 scriptName。
     * VarContext 通过参数传递，不使用 ThreadLocal。
     */
    public static String compile(JSONArray actionData, VarContext varContext) {
        if (actionData == null || actionData.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < actionData.size(); i++) {
            String code = compileBlock(actionData.getJSONObject(i), 0, varContext);
            if (code != null && !code.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(code);
            }
        }
        return sb.toString();
    }

    private static String compileBlock(JSONObject block, int indent, VarContext varContext) {
        if (block == null) return "";
        String type = block.getString("type");
        if (type == null) return "";
        switch (type) {
            case "assign": return compileAssign(block, indent, varContext);
            case "if-block": return compileIfBlock(block, indent, varContext);
            case "switch-block": return compileSwitchBlock(block, indent, varContext);
            case "func-call": return compileFuncCall(block, indent, varContext);
            case "foreach": return compileForeach(block, indent, varContext);
            case "ternary": return compileTernary(block, indent, varContext);
            case "in-check": return compileInCheck(block, indent, varContext);
            case "template-str": return compileTemplateStr(block, indent, varContext);
            case "rule-call": return compileRuleCall(block, indent, varContext);
            default: return "";
        }
    }

    private static String compileAssign(JSONObject b, int indent, VarContext varContext) {
        String target = b.getString("target");
        String value = b.getString("value");
        if (empty(target) || empty(value)) return "";
        Long varId = fieldVarId(b, "target");
        String refType = fieldRefType(b, "target");
        String resolvedTarget = resolveVar(varId, refType, target, varContext);
        StringBuilder sb = new StringBuilder();
        sb.append(pad(indent)).append(resolvedTarget).append(" = ").append(value);
        Boolean enableRounding = b.getBoolean("enableRounding");
        if (Boolean.TRUE.equals(enableRounding)) {
            Integer dp = b.getInteger("decimalPlaces");
            if (dp != null && dp >= 0) {
                String rm = b.getString("roundingMode");
                if (empty(rm)) rm = "HALF_UP";
                sb.append("\n").append(pad(indent))
                  .append(resolvedTarget).append(" = roundScale(")
                  .append(resolvedTarget).append(", ").append(dp).append(", ")
                  .append(quoteString(rm)).append(")");
            }
        }
        return appendRuntimeSync(sb.toString(), resolvedTarget, indent);
    }

    private static String compileIfBlock(JSONObject b, int indent, VarContext varContext) {
        JSONArray branches = b.getJSONArray("branches");
        if (branches == null || branches.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < branches.size(); i++) {
            JSONObject br = branches.getJSONObject(i);
            String bt = br.getString("type");
            if ("if".equals(bt)) sb.append(pad(indent)).append("if (").append(buildCond(br, varContext)).append(") {\n");
            else if ("elseif".equals(bt)) sb.append(pad(indent)).append("} else if (").append(buildCond(br, varContext)).append(") {\n");
            else sb.append(pad(indent)).append("} else {\n");
            sb.append(compileActions(br.getJSONArray("actions"), indent + 1, varContext));
        }
        sb.append(pad(indent)).append("}");
        return sb.toString();
    }

    private static String compileSwitchBlock(JSONObject b, int indent, VarContext varContext) {
        String matchVar = b.getString("matchVar");
        if (empty(matchVar)) return "";
        Long varId = fieldVarId(b, "matchVar");
        String refType = fieldRefType(b, "matchVar");
        String resolvedMatchVar = resolveVar(varId, refType, matchVar, varContext);
        StringBuilder sb = new StringBuilder();
        JSONArray cases = b.getJSONArray("cases");
        boolean hasCase = false;
        if (cases != null) {
            for (int i = 0; i < cases.size(); i++) {
                JSONObject c = cases.getJSONObject(i);
                String val = c.getString("value");
                if (empty(val)) continue;
                if (hasCase) sb.append(" else ");
                else sb.append(pad(indent));
                sb.append("if (").append(resolvedMatchVar).append(" == ").append(wrapSwitchCaseValue(val)).append(") {\n");
                sb.append(compileActions(c.getJSONArray("actions"), indent + 1, varContext));
                sb.append(pad(indent)).append("}");
                hasCase = true;
            }
        }
        JSONArray defaults = b.getJSONArray("defaultActions");
        if (defaults != null && !defaults.isEmpty()) {
            if (hasCase) sb.append(" else {\n");
            else sb.append(pad(indent)).append("if (true) {\n");
            sb.append(compileActions(defaults, indent + 1, varContext));
            sb.append(pad(indent)).append("}");
        }
        return sb.toString();
    }

    private static String compileFuncCall(JSONObject b, int indent, VarContext varContext) {
        String funcName = b.getString("funcName");
        if (empty(funcName)) return "";
        JSONArray args = b.getJSONArray("args");
        JSONArray argRefs = b.getJSONArray("_argRefs");
        StringBuilder ab = new StringBuilder();
        if (args != null) {
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) ab.append(", ");
                String arg = args.getString(i);
                JSONObject ref = argRefs != null && i < argRefs.size() ? argRefs.getJSONObject(i) : null;
                if (ref != null) {
                    Long argVarId = fieldVarId(ref, "arg");
                    String argRefType = fieldRefType(ref, "arg");
                    ab.append(resolveVar(argVarId, argRefType, arg, varContext));
                } else {
                    ab.append(ActionOperandCompiler.compileLiteral(arg));
                }
            }
        }
        String call = funcName + "(" + ab + ")";
        String target = b.getString("target");
        Long targetVarId = fieldVarId(b, "target");
        String targetRefType = fieldRefType(b, "target");
        if (empty(target)) {
            return pad(indent) + call;
        }
        String resolvedTarget = resolveVar(targetVarId, targetRefType, target, varContext);
        return appendRuntimeSync(pad(indent) + resolvedTarget + " = " + call, resolvedTarget, indent);
    }

    private static String compileForeach(JSONObject b, int indent, VarContext varContext) {
        String itemVar = b.getString("itemVar");
        String listExpr = b.getString("listExpr");
        if (empty(itemVar) || empty(listExpr)) return "";
        Long varId = b.containsKey("_varId") ? b.getLong("_varId") : null;
        String refType = b.getString("_refType");
        StringBuilder sb = new StringBuilder();
        sb.append(pad(indent)).append("for (").append(resolveVar(varId, refType, itemVar, varContext)).append(" : ").append(listExpr).append(") {\n");
        sb.append(compileActions(b.getJSONArray("actions"), indent + 1, varContext));
        sb.append(pad(indent)).append("}");
        return sb.toString();
    }

    private static String compileTernary(JSONObject b, int indent, VarContext varContext) {
        String target = b.getString("target");
        String condVar = b.getString("condVar");
        if (empty(target) || empty(condVar)) return "";
        Long targetVarId = fieldVarId(b, "target");
        String targetRefType = fieldRefType(b, "target");
        Long condVarId = fieldVarId(b, "condVar");
        String condRefType = fieldRefType(b, "condVar");
        String op = b.getString("condOp");
        if (empty(op)) op = "==";
        String cond = ConditionExpressionBuilder.build(resolveVar(condVarId, condRefType, condVar, varContext),
                b.getString("condVarType"), op, b.getString("condValue"), false);
        String tv = b.getString("trueValue");
        String fv = b.getString("falseValue");
        String resolvedTarget = resolveVar(targetVarId, targetRefType, target, varContext);
        return appendRuntimeSync(pad(indent) + resolvedTarget + " = " + cond + " ? " + (empty(tv) ? "\"\"" : tv) + " : " + (empty(fv) ? "\"\"" : fv), resolvedTarget, indent);
    }

    private static String compileInCheck(JSONObject b, int indent, VarContext varContext) {
        String target = b.getString("target");
        String checkVar = b.getString("checkVar");
        if (empty(target) || empty(checkVar)) return "";
        Long targetVarId = fieldVarId(b, "target");
        String targetRefType = fieldRefType(b, "target");
        Long checkVarId = fieldVarId(b, "checkVar");
        String checkRefType = fieldRefType(b, "checkVar");
        JSONArray vals = b.getJSONArray("inValues");
        StringBuilder vb = new StringBuilder();
        if (vals != null) {
            for (int i = 0; i < vals.size(); i++) {
                String v = vals.getString(i);
                if (v != null && !v.trim().isEmpty()) {
                    if (vb.length() > 0) vb.append(", ");
                    vb.append(wrapValue(v));
                }
            }
        }
        String tv = b.getString("trueValue");
        String fv = b.getString("falseValue");
        String resolvedTarget = resolveVar(targetVarId, targetRefType, target, varContext);
        return appendRuntimeSync(pad(indent) + resolvedTarget + " = " + resolveVar(checkVarId, checkRefType, checkVar, varContext) + " in [" + vb + "] ? " + (empty(tv) ? "true" : tv) + " : " + (empty(fv) ? "false" : fv), resolvedTarget, indent);
    }

    private static String compileTemplateStr(JSONObject b, int indent, VarContext varContext) {
        String target = b.getString("target");
        JSONArray parts = b.getJSONArray("parts");
        if (empty(target) || parts == null || parts.isEmpty()) return "";
        Long targetVarId = fieldVarId(b, "target");
        String targetRefType = fieldRefType(b, "target");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            JSONObject p = parts.getJSONObject(i);
            if ("expr".equals(p.getString("type"))) sb.append("${").append(p.getString("content")).append("}");
            else sb.append(p.getString("content"));
        }
        String resolvedTarget = resolveVar(targetVarId, targetRefType, target, varContext);
        return appendRuntimeSync(pad(indent) + resolvedTarget + " = \"" + sb.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"", resolvedTarget, indent);
    }

    private static String compileRuleCall(JSONObject b, int indent, VarContext varContext) {
        String ruleCode = b.getString("ruleCode");
        Long ruleId = b.getLong("ruleId");
        if (ruleId == null && empty(ruleCode)) return "";
        String outputField = b.getString("outputField");
        String call;
        if (ruleId != null) {
            call = empty(outputField)
                    ? "executeRuleById(" + quoteString(String.valueOf(ruleId)) + ")"
                    : "executeRuleFieldById(" + quoteString(String.valueOf(ruleId)) + ", " + quoteString(outputField) + ")";
        } else {
            call = empty(outputField)
                    ? "executeRule(" + quoteString(ruleCode) + ")"
                    : "executeRuleField(" + quoteString(ruleCode) + ", " + quoteString(outputField) + ")";
        }
        String target = b.getString("target");
        if (empty(target)) {
            return pad(indent) + call;
        }
        Long targetVarId = fieldVarId(b, "target");
        String targetRefType = fieldRefType(b, "target");
        String resolvedTarget = resolveVar(targetVarId, targetRefType, target, varContext);
        return appendRuntimeSync(pad(indent) + resolvedTarget + " = " + call, resolvedTarget, indent);
    }

    private static String compileActions(JSONArray actions, int indent, VarContext varContext) {
        if (actions == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < actions.size(); i++) {
            String code = compileBlock(actions.getJSONObject(i), indent, varContext);
            if (code != null && !code.isEmpty()) {
                sb.append(code).append("\n");
            }
        }
        return sb.toString();
    }

    private static String buildCond(JSONObject branch, VarContext varContext) {
        Long varId = fieldVarId(branch, "condVar");
        String refType = fieldRefType(branch, "condVar");
        String v = branch.getString("condVar");
        if (empty(v)) return "true";
        String op = branch.getString("condOp");
        if (empty(op)) op = "==";
        return ConditionExpressionBuilder.build(resolveVar(varId, refType, v, varContext),
                branch.getString("condVarType"), op, branch.getString("condValue"), false);
    }

    private static Long fieldVarId(JSONObject block, String field) {
        String key = fieldIdKey(field);
        if (key != null && block.containsKey(key)) {
            return block.getLong(key);
        }
        return block.containsKey("_varId") ? block.getLong("_varId") : null;
    }

    private static String fieldRefType(JSONObject block, String field) {
        String key = fieldRefTypeKey(field);
        String refType = key == null ? null : block.getString(key);
        return empty(refType) ? block.getString("_refType") : refType;
    }

    private static String fieldIdKey(String field) {
        if ("target".equals(field)) return "_targetVarId";
        if ("condVar".equals(field)) return "_condVarId";
        if ("matchVar".equals(field)) return "_matchVarId";
        if ("checkVar".equals(field)) return "_checkVarId";
        if ("arg".equals(field)) return "_varId";
        return null;
    }

    private static String fieldRefTypeKey(String field) {
        if ("target".equals(field)) return "_targetRefType";
        if ("condVar".equals(field)) return "_condVarRefType";
        if ("matchVar".equals(field)) return "_matchVarRefType";
        if ("checkVar".equals(field)) return "_checkVarRefType";
        if ("arg".equals(field)) return "_refType";
        return null;
    }

    /**
     * 通过 VarContext 解析脚本变量名。
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

    private static String wrapValue(String val) {
        if (val == null || val.isEmpty()) return "\"\"";
        String s = val.trim();
        if ("true".equals(s) || "false".equals(s) || "null".equals(s)) return s;
        try { Double.parseDouble(s); return s; } catch (NumberFormatException ignored) {}
        if (s.matches("[a-zA-Z_]\\w*(\\.\\w+)*")) return s;
        if (s.startsWith("\"") || s.startsWith("'")) return s;
        if (s.matches(".*[+\\-*/()><=!&|,\\[\\]{}].*")) return s;
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String quoteString(String value) {
        return ActionOperandCompiler.quoteString(value);
    }

    private static String appendRuntimeSync(String code, String resolvedTarget, int indent) {
        if (empty(code) || empty(resolvedTarget)) {
            return code;
        }
        return code + "\n" + pad(indent) + "setRuntimeValue("
                + quoteString(resolvedTarget) + ", " + resolvedTarget + ")";
    }

    private static boolean empty(String s) { return s == null || s.trim().isEmpty(); }

    private static String wrapSwitchCaseValue(String val) {
        if (val == null || val.isEmpty()) return "\"\"";
        String s = val.trim();
        if ("true".equals(s) || "false".equals(s) || "null".equals(s)) return s;
        try { Double.parseDouble(s); return s; } catch (NumberFormatException ignored) {}
        if (s.startsWith("\"") || s.startsWith("'")) return s;
        return quoteString(s);
    }

    private static String pad(int indent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) sb.append("    ");
        return sb.toString();
    }
}
