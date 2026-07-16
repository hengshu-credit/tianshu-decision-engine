package com.hengshucredit.rule.core.compiler;

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
            String resultTarget = resolveResultTarget(model.getJSONObject("resultVar"), varContext);

            StringBuilder script = new StringBuilder();
            script.append("_ruleSetHits = [];\n");
            script.append("_ruleSetHitCount = 0;\n");
            if (serial) {
                script.append("_ruleSetMatched = false;\n");
            }

            for (int i = 0; i < entries.size(); i++) {
                appendRule(script, entries.get(i), serial, varContext, i);
            }
            script.append("_ruleSetResult = _ruleSetHits;\n");
            if (resultTarget != null) {
                script.append(resultTarget).append(" = _ruleSetHits;\n");
                script.append("setRuntimeValue(").append(ActionOperandCompiler.quoteString(resultTarget))
                        .append(", ").append(resultTarget).append(");\n");
            }
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

    private String resolveResultTarget(JSONObject resultVar, VarContext varContext) {
        if (resultVar == null || resultVar.isEmpty()) {
            return null;
        }
        JSONObject operand = resultVar.getJSONObject("operand");
        String valueType = operand != null ? operand.getString("valueType") : resultVar.getString("varType");
        if (!"LIST".equalsIgnoreCase(valueType)) {
            throw new IllegalArgumentException("规则集命中结果输出字段必须是 LIST 类型");
        }

        Long refId = operand != null && operand.getLong("refId") != null
                ? operand.getLong("refId") : resultVar.getLong("_varId");
        String refType = firstNonBlank(operand != null ? operand.getString("refType") : null,
                resultVar.getString("_refType"));
        if (refId == null || refType == null) {
            throw new IllegalArgumentException("规则集命中结果输出字段缺少稳定字段 ID 或引用类型");
        }
        if (!"VARIABLE".equalsIgnoreCase(refType) && !"DATA_OBJECT".equalsIgnoreCase(refType)) {
            throw new IllegalArgumentException("规则集命中结果输出字段仅支持普通变量或数据对象字段");
        }

        String code;
        if (operand == null) {
            code = resultVar.getString("varCode");
        } else {
            String kind = operand.getString("kind");
            if (!"PATH".equals(kind) && !"REFERENCE".equals(kind)) {
                throw new IllegalArgumentException("规则集命中结果输出字段必须是字段引用");
            }
            code = firstNonBlank(operand.getString("code"), operand.getString("value"));
        }
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("规则集命中结果输出字段路径不能为空");
        }

        String resultTarget = varContext != null ? varContext.getScriptName(refType, refId) : null;
        if (resultTarget == null || resultTarget.trim().isEmpty()) {
            throw new IllegalArgumentException("规则集命中结果输出字段引用不存在或已停用，"
                    + refType.trim().toUpperCase() + ":" + refId);
        }
        return resultTarget;
    }

    private void appendRule(StringBuilder script, RuleEntry entry, boolean serial, VarContext varContext, int maxPreviousHits) {
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
        script.append("    _ruleSetHit = {\"ruleCode\": \"").append(escape(entry.ruleCode))
                .append("\", \"ruleName\": \"").append(escape(entry.ruleName))
                .append("\", \"priority\": ").append(entry.priority)
                .append(", \"order\": ").append(entry.order).append("};\n");
        appendHitListAssignment(script, maxPreviousHits);
        if (serial) {
            script.append("    _ruleSetMatched = true;\n");
        }
        script.append("}\n");
    }

    private void appendHitListAssignment(StringBuilder script, int maxPreviousHits) {
        script.append("    if (_ruleSetHitCount == 0) {\n");
        script.append("        _ruleSetHits = [_ruleSetHit];\n");
        for (int i = 1; i <= maxPreviousHits; i++) {
            script.append("    } else if (_ruleSetHitCount == ").append(i).append(") {\n");
            script.append("        _ruleSetHits = [");
            for (int j = 0; j < i; j++) {
                if (j > 0) {
                    script.append(", ");
                }
                script.append("_ruleSetHits[").append(j).append("]");
            }
            script.append(", _ruleSetHit];\n");
        }
        script.append("    }\n");
        script.append("    _ruleSetHitCount = _ruleSetHitCount + 1;\n");
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
