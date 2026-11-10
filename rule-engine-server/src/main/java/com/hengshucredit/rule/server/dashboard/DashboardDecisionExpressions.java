package com.hengshucredit.rule.server.dashboard;

import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.compiler.OperandCompiler;
import com.hengshucredit.rule.server.openapi.RestrictedJsonPath;
import com.hengshucredit.rule.server.service.OperandValueResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 看板只读取执行日志和已授权常量，不执行规则、外数调用或用户函数。 */
public final class DashboardDecisionExpressions {
    private DashboardDecisionExpressions() { }

    public record Binding(List<String> path, Object constant) {
        boolean dynamic() { return path != null; }
        Object read(Map<String, Object> context) {
            Object value = context;
            if (!dynamic()) return constant;
            for (String key : path) value = value instanceof Map<?, ?> map ? map.get(key) : null;
            return value;
        }
    }

    public record Prepared(List<List<JSONObject>> groups, Map<String, Binding> bindings) {
        public boolean dynamic() { return bindings.values().stream().anyMatch(Binding::dynamic); }

        public List<Set<String>> values(Map<String, Object> context, boolean staticOnly) {
            Map<String, Object> references = new LinkedHashMap<>();
            bindings.forEach((key, binding) -> references.put(key, binding.read(context)));
            List<Set<String>> result = new ArrayList<>();
            for (List<JSONObject> group : groups) {
                Set<String> values = new LinkedHashSet<>();
                for (JSONObject operand : group) {
                    if (staticOnly && hasDynamicReference(operand, bindings)) continue;
                    Object value;
                    try {
                        value = OperandValueResolver.resolve(operand, Map.of(), references);
                    } catch (RuntimeException error) {
                        throw new IllegalArgumentException("决策结果表达式计算失败，请检查取值和运算", error);
                    }
                    if (!(value instanceof String || value instanceof Number || value instanceof Boolean)
                            || value.toString().isBlank()) {
                        throw new IllegalArgumentException("决策结果表达式必须返回非空文本、数值或布尔值");
                    }
                    values.add(DashboardMapping.normalizeDecisionValue(value.toString()));
                }
                result.add(values);
            }
            return result;
        }
    }

    public static Prepared prepare(DashboardMapping.DecisionValues values, Function<JSONObject, Binding> resolver) {
        var groups = List.of(values.passOperands(), values.reviewOperands(), values.rejectOperands());
        Map<String, Binding> bindings = new LinkedHashMap<>();
        int[] nodes = {0};
        for (var group : groups) for (var operand : group) {
            validate(operand, resolver, bindings, nodes, 0);
            OperandCompiler.compile(operand, null);
        }
        return new Prepared(groups, bindings);
    }

    private static void validate(JSONObject node, Function<JSONObject, Binding> resolver,
                                 Map<String, Binding> bindings, int[] nodes, int depth) {
        if (node == null || depth > 32 || ++nodes[0] > 256) throw new IllegalArgumentException("决策结果表达式为空或过于复杂");
        String kind = node.getString("kind");
        if (!Set.of("LITERAL", "REFERENCE", "PATH", "OPERATION", "ACCESS", "CAST", "ARRAY").contains(kind == null ? "" : kind))
            throw new IllegalArgumentException("看板表达式仅支持阈值、字段路径和组合运算");
        if ("FIXED".equals(node.getString("versionMode"))) throw new IllegalArgumentException("看板引用使用当前生效版本");
        if ("REFERENCE".equals(kind) || "PATH".equals(kind)) {
            if (node.getLong("refId") == null || node.getLong("refId") <= 0 || node.getString("refType") == null)
                throw new IllegalArgumentException("看板路径必须按 ID 绑定取值来源");
            String suffix = node.getString("relativePath");
            if (suffix != null && !suffix.isEmpty()) {
                if (suffix.length() > 512) throw new IllegalArgumentException("取值路径过长");
                RestrictedJsonPath.read(Map.of(), "$" + suffix);
            }
            String key = node.getString("refType") + ":" + node.getLong("refId");
            bindings.put(key, resolver.apply(node));
        }
        for (var child : children(node)) validate(child, resolver, bindings, nodes, depth + 1);
    }

    private static boolean hasDynamicReference(JSONObject node, Map<String, Binding> bindings) {
        Binding binding = bindings.get(node.getString("refType") + ":" + node.getLong("refId"));
        return binding != null && binding.dynamic() || children(node).stream().anyMatch(child -> hasDynamicReference(child, bindings));
    }

    private static List<JSONObject> children(JSONObject node) {
        List<JSONObject> result = new ArrayList<>();
        switch (node.getString("kind")) {
            case "OPERATION" -> {
                var terms = node.getJSONArray("terms");
                if (terms != null) for (int i = 0; i < terms.size(); i++) {
                    var term = terms.getJSONObject(i);
                    if (term == null) throw new IllegalArgumentException("运算项不能为空");
                    if (term.getJSONObject("operand") != null) result.add(term.getJSONObject("operand"));
                }
            }
            case "ACCESS" -> { result.add(node.getJSONObject("target")); result.add(node.getJSONObject("accessor")); }
            case "CAST" -> result.add(node.getJSONObject("operand"));
            case "ARRAY" -> { var items = node.getJSONArray("items"); if (items != null) result.addAll(items.toJavaList(JSONObject.class)); }
            default -> { }
        }
        return result;
    }
}
