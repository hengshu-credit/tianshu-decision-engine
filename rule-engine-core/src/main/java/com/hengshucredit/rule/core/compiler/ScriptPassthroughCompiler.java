package com.hengshucredit.rule.core.compiler;

import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QL脚本直通编译器：不做模型转换，仅将脚本原文存入编译结果。
 * 适用于技术人员直接编写 QLExpress 脚本的场景。
 */
public class ScriptPassthroughCompiler implements RuleCompiler {

    private static final Pattern ASSIGNMENT_PATTERN = Pattern.compile(
            "(?m)^\\s*([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)\\s*=(?!=).*");

    @Override
    public CompileResult compile(String modelJson) {
        if (modelJson == null || modelJson.trim().isEmpty() || "{}".equals(modelJson.trim())) {
            return CompileResult.fail("脚本内容为空，请先编写脚本再编译");
        }

        try {
            com.alibaba.fastjson.JSONObject model = com.alibaba.fastjson.JSON.parseObject(modelJson);
            String script = model.getString("script");
            if (script == null || script.trim().isEmpty()) {
                return CompileResult.fail("脚本内容为空，请先编写脚本再编译");
            }
            return CompileResult.ok(wrapResultIfNeeded(script), "QLEXPRESS");
        } catch (Exception e) {
            return CompileResult.ok(wrapResultIfNeeded(modelJson), "QLEXPRESS");
        }
    }

    private String wrapResultIfNeeded(String script) {
        if (script == null || script.contains("_result")) {
            return script;
        }
        LinkedHashSet<String> assignedVars = collectAssignedVars(script);
        if (assignedVars.isEmpty()) {
            return script;
        }
        StringBuilder wrapped = new StringBuilder(script.trim());
        wrapped.append("\n");
        RuleScriptResultCollector.appendResultMapReturn(wrapped, assignedVars);
        return wrapped.toString();
    }

    private LinkedHashSet<String> collectAssignedVars(String script) {
        LinkedHashSet<String> vars = new LinkedHashSet<>();
        Matcher matcher = ASSIGNMENT_PATTERN.matcher(script);
        while (matcher.find()) {
            String var = matcher.group(1);
            if (var != null && !"_result".equals(var)) {
                vars.add(var.trim());
            }
        }
        return vars;
    }
}
