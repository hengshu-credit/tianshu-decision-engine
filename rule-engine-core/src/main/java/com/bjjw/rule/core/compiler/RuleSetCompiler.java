package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 规则集：多条独立规则按优先级和页面顺序执行，返回命中的规则列表。
 *
 * <p>每条规则复用决策表的条件树编译与决策流的 actionData 动作编译。
 */
public class RuleSetCompiler implements RuleCompiler {

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
            if (modelJson == null || modelJson.trim().isEmpty()) {
                return CompileResult.fail("规则集模型不能为空");
            }
            JSONObject model = JSON.parseObject(modelJson);
            if (model == null) {
                return CompileResult.fail("规则集模型不能为空");
            }
            JSONArray rules = model.getJSONArray("rules");
            if (rules == null) {
                return CompileResult.fail("规则集模型缺少必要字段: rules");
            }

            String executionMode = model.getString("executionMode");
            boolean serial = executionMode == null || "SERIAL".equalsIgnoreCase(executionMode);
            List<RuleEntry> entries = normalizeRules(rules);

            StringBuilder script = new StringBuilder();
            script.append("_ruleSetHits = new java.util.ArrayList();\n");
            if (serial) {
                script.append("_ruleSetMatched = false;\n");
            }

            for (RuleEntry entry : entries) {
                appendRule(script, entry, serial, varContext);
            }
            script.append("_ruleSetResult = _ruleSetHits;\n");
            script.append("_ruleSetResult\n");

            return CompileResult.ok(script.toString(), "QLEXPRESS");
        } catch (Exception e) {
            return CompileResult.fail("规则集编译失败: " + e.getMessage());
        }
    }

    private List<RuleEntry> normalizeRules(JSONArray rules) {
        List<RuleEntry> entries = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            if (rule == null || isDisabled(rule)) {
                continue;
            }
            RuleEntry entry = new RuleEntry();
            entry.rule = rule;
            entry.order = i + 1;
            entry.priority = rule.containsKey("priority") ? rule.getIntValue("priority") : 1;
            entry.ruleCode = firstNonBlank(rule.getString("ruleCode"), String.format("R%04d", i + 1));
            entry.ruleName = firstNonBlank(rule.getString("ruleName"), entry.ruleCode);
            entries.add(entry);
        }
        Collections.sort(entries, new Comparator<RuleEntry>() {
            @Override
            public int compare(RuleEntry a, RuleEntry b) {
                int priorityCompare = Integer.compare(b.priority, a.priority);
                if (priorityCompare != 0) {
                    return priorityCompare;
                }
                return Integer.compare(a.order, b.order);
            }
        });
        return entries;
    }

    private void appendRule(StringBuilder script, RuleEntry entry, boolean serial, VarContext varContext) {
        JSONArray ruleConditions = entry.rule.getJSONArray("conditions");
        if (ruleConditions == null) {
            ruleConditions = new JSONArray();
        }
        String predicate = DecisionTableCompiler.buildRulePredicate(entry.rule, ruleConditions, new JSONArray(), varContext);
        script.append("if (");
        if (serial) {
            script.append("!_ruleSetMatched && ");
        }
        script.append("(").append(predicate).append(")) {\n");

        appendActionData(script, entry.rule.getJSONArray("actionData"), varContext);
        script.append("    _ruleSetHits.add({\"ruleCode\": \"").append(escape(entry.ruleCode))
                .append("\", \"ruleName\": \"").append(escape(entry.ruleName))
                .append("\", \"priority\": ").append(entry.priority)
                .append(", \"order\": ").append(entry.order).append("});\n");
        if (serial) {
            script.append("    _ruleSetMatched = true;\n");
        }
        script.append("}\n");
    }

    private void appendActionData(StringBuilder script, JSONArray actionData, VarContext varContext) {
        String actionScript = ActionDataCompiler.compile(actionData, varContext);
        if (actionScript == null || actionScript.trim().isEmpty()) {
            return;
        }
        String[] lines = actionScript.split("\\r?\\n");
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            script.append("    ").append(line).append("\n");
        }
    }

    private boolean isDisabled(JSONObject rule) {
        if (rule.containsKey("enabled") && Boolean.FALSE.equals(rule.getBoolean("enabled"))) {
            return true;
        }
        return rule.containsKey("status") && rule.getIntValue("status") == 0;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        return second;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static class RuleEntry {
        private JSONObject rule;
        private String ruleCode;
        private String ruleName;
        private int priority;
        private int order;
    }
}
