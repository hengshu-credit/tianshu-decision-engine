package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图结构 → QLExpress 脚本生成器（共享工具类）
 *
 * 将 nodes/edges 图递归展开为单一 QLExpress 脚本。
 * 支持节点类型：start、end、task（含 actionData）、decision（if/else）、join（汇合透传）。
 * 被 DecisionTreeCompiler 和 DecisionFlowCompiler 共用。
 */
public class GraphScriptGenerator {

    /**
     * 根据图结构生成 QLExpress 脚本。
     *
     * @param nodeMap    节点 id → 节点 JSON
     * @param outEdgeMap 节点 id → 出边列表
     * @param startId    开始节点 id
     * @param varContext 变量上下文（用于解析 varCode → scriptName），可为 null
     * @return 生成的 QLExpress 脚本
     */
    public static String generate(Map<String, JSONObject> nodeMap,
                                  Map<String, List<JSONObject>> outEdgeMap,
                                  String startId, VarContext varContext) {
        StringBuilder script = new StringBuilder();
        Set<String> visited = new HashSet<>();
        generateScript(startId, null, nodeMap, outEdgeMap, script, visited, 0, varContext);
        return script.toString().trim();
    }

    /**
     * 同上，兼容无 VarContext 的旧调用。
     */
    public static String generate(Map<String, JSONObject> nodeMap,
                                  Map<String, List<JSONObject>> outEdgeMap,
                                  String startId) {
        return generate(nodeMap, outEdgeMap, startId, null);
    }

    private static void generateScript(String nodeId, String stopAt,
                                       Map<String, JSONObject> nodeMap,
                                       Map<String, List<JSONObject>> outEdgeMap,
                                       StringBuilder script, Set<String> visited, int indent,
                                       VarContext varContext) {
        if (nodeId == null || nodeId.equals(stopAt)) return;
        if (visited.contains(nodeId)) return;
        visited.add(nodeId);

        JSONObject node = nodeMap.get(nodeId);
        if (node == null) return;
        String type = node.getString("type");
        String name = node.getString("name");

        if ("start".equals(type) || "join".equals(type)) {
            List<JSONObject> out = outEdgeMap.getOrDefault(nodeId, Collections.emptyList());
            if (!out.isEmpty()) {
                generateScript(out.get(0).getString("target"), stopAt, nodeMap, outEdgeMap, script, visited, indent, varContext);
            }
        } else if ("task".equals(type)) {
            JSONArray actionData = node.getJSONArray("actionData");
            String qlScript;
            if (actionData != null && !actionData.isEmpty()) {
                qlScript = ActionDataCompiler.compile(actionData, varContext);
            } else {
                qlScript = node.getString("qlExpressScript");
            }
            if (qlScript != null && !qlScript.trim().isEmpty()) {
                appendIndent(script, indent);
                script.append("// ").append(name != null ? name : "脚本任务").append("\n");
                for (String line : qlScript.trim().split("\n")) {
                    appendIndent(script, indent);
                    script.append(line).append("\n");
                }
                script.append("\n");
            }
            List<JSONObject> out = outEdgeMap.getOrDefault(nodeId, Collections.emptyList());
            if (!out.isEmpty()) {
                generateScript(out.get(0).getString("target"), stopAt, nodeMap, outEdgeMap, script, visited, indent, varContext);
            }
        } else if ("decision".equals(type)) {
            List<JSONObject> out = outEdgeMap.getOrDefault(nodeId, Collections.emptyList());
            if (out.isEmpty()) return;

            String mergeNode = findMergeNode(nodeId, outEdgeMap, nodeMap);

            JSONObject defaultEdge = null;
            List<JSONObject> condEdges = new ArrayList<>();
            for (JSONObject edge : out) {
                if (isConditionalEdge(edge)) {
                    condEdges.add(edge);
                } else {
                    defaultEdge = edge;
                }
            }

            if (condEdges.isEmpty() && defaultEdge != null) {
                generateScript(defaultEdge.getString("target"), stopAt, nodeMap, outEdgeMap, script, visited, indent, varContext);
            } else {
                for (int i = 0; i < condEdges.size(); i++) {
                    JSONObject edge = condEdges.get(i);
                    String condExpr = resolveConditionExpression(edge, varContext);
                    appendIndent(script, indent);
                    if (i == 0) {
                        script.append("if (").append(condExpr).append(") {\n");
                    } else {
                        script.append("} else if (").append(condExpr).append(") {\n");
                    }
                    Set<String> branchVisited = new HashSet<>(visited);
                    generateScript(edge.getString("target"), mergeNode, nodeMap, outEdgeMap, script, branchVisited, indent + 1, varContext);
                }
                if (defaultEdge != null) {
                    appendIndent(script, indent);
                    script.append("} else {\n");
                    Set<String> branchVisited = new HashSet<>(visited);
                    generateScript(defaultEdge.getString("target"), mergeNode, nodeMap, outEdgeMap, script, branchVisited, indent + 1, varContext);
                }
                appendIndent(script, indent);
                script.append("}\n\n");
            }

            if (mergeNode != null) {
                generateScript(mergeNode, stopAt, nodeMap, outEdgeMap, script, visited, indent, varContext);
            }
        }
    }

    /**
     * 找到决策节点的汇合点（后续所有分支的第一个公共节点，优先选择 join 类型）
     */
    static String findMergeNode(String decisionNodeId,
                                Map<String, List<JSONObject>> outEdgeMap,
                                Map<String, JSONObject> nodeMap) {
        List<JSONObject> edges = outEdgeMap.getOrDefault(decisionNodeId, Collections.emptyList());
        if (edges.size() < 2) return null;

        List<Set<String>> branchReachable = new ArrayList<>();
        for (JSONObject edge : edges) {
            Set<String> reachable = new LinkedHashSet<>();
            Queue<String> queue = new LinkedList<>();
            queue.add(edge.getString("target"));
            while (!queue.isEmpty()) {
                String nid = queue.poll();
                if (reachable.contains(nid)) continue;
                reachable.add(nid);
                for (JSONObject e : outEdgeMap.getOrDefault(nid, Collections.emptyList())) {
                    queue.add(e.getString("target"));
                }
            }
            branchReachable.add(reachable);
        }

        if (branchReachable.isEmpty()) return null;

        Set<String> common = new LinkedHashSet<>(branchReachable.get(0));
        for (int i = 1; i < branchReachable.size(); i++) {
            common.retainAll(branchReachable.get(i));
        }
        if (common.isEmpty()) return null;

        for (String nid : common) {
            JSONObject n = nodeMap.get(nid);
            if (n != null && "join".equals(n.getString("type"))) {
                return nid;
            }
        }
        return common.iterator().next();
    }

    private static void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) sb.append("    ");
    }

    private static String resolveConditionExpression(JSONObject edge, VarContext varContext) {
        JSONObject conditionConfig = getConditionConfig(edge);
        if (hasUsableCondition(conditionConfig)) {
            return compileConditionNode(conditionConfig, varContext);
        }
        String expr = edge.getString("conditionExpression");
        if (expr == null || expr.trim().isEmpty() || varContext == null) {
            return expr;
        }
        Matcher matcher = Pattern.compile("^(\\S+)\\s*(==|!=|>=|<=|>|<|in)\\s*(.+)$").matcher(expr.trim());
        if (!matcher.matches()) {
            return expr;
        }
        Long leftId = edge.containsKey("leftVarId") ? edge.getLong("leftVarId") : null;
        String leftRefType = edge.getString("leftRefType");
        String left = varContext.resolveVar(leftId, leftRefType, matcher.group(1));
        String right = matcher.group(3);
        Long rightId = edge.containsKey("rightVarId") ? edge.getLong("rightVarId") : null;
        String rightRefType = edge.getString("rightRefType");
        if (rightId != null) {
            right = varContext.resolveVar(rightId, rightRefType, right);
        }
        return left + " " + matcher.group(2) + " " + right;
    }

    private static boolean isConditionalEdge(JSONObject edge) {
        JSONObject conditionConfig = getConditionConfig(edge);
        if (hasUsableCondition(conditionConfig)) return true;
        String condExpr = edge.getString("conditionExpression");
        return condExpr != null && !condExpr.trim().isEmpty();
    }

    private static JSONObject getConditionConfig(JSONObject edge) {
        if (edge == null) return null;
        Object raw = edge.get("conditionConfig");
        if (raw instanceof JSONObject) {
            return (JSONObject) raw;
        }
        if (raw instanceof String) {
            String text = ((String) raw).trim();
            if (text.startsWith("{") && text.endsWith("}")) {
                return JSONObject.parseObject(text);
            }
        }
        return null;
    }

    private static boolean hasUsableCondition(JSONObject node) {
        if (node == null) return false;
        if (Boolean.TRUE.equals(node.getBoolean("fallback"))) return false;
        String type = node.getString("type");
        if ("leaf".equals(type)) {
            String op = node.getString("operator");
            if (op == null || op.trim().isEmpty()) op = "==";
            if ("*".equals(op)) return false;
            String left = node.getString("varCode");
            if (left == null || left.trim().isEmpty()) return false;
            String valueKind = node.getString("valueKind");
            Object value = node.get("value");
            return value != null && !String.valueOf(value).trim().isEmpty();
        }
        if ("group".equals(type)) {
            JSONArray children = node.getJSONArray("children");
            if (children == null) return false;
            for (int i = 0; i < children.size(); i++) {
                JSONObject child = children.getJSONObject(i);
                if (hasUsableCondition(child)) return true;
            }
        }
        return false;
    }

    private static String compileConditionNode(JSONObject node, VarContext varContext) {
        if (node == null) return "true";
        String type = node.getString("type");
        if ("leaf".equals(type)) {
            return compileConditionLeaf(node, varContext);
        }
        if ("group".equals(type)) {
            JSONArray children = node.getJSONArray("children");
            List<String> expressions = new ArrayList<>();
            if (children != null) {
                for (int i = 0; i < children.size(); i++) {
                    String expr = compileConditionNode(children.getJSONObject(i), varContext);
                    if (expr != null && !expr.trim().isEmpty()) {
                        expressions.add(expr);
                    }
                }
            }
            if (expressions.isEmpty()) return "true";
            String joiner = "OR".equals(node.getString("op")) ? " || " : " && ";
            return "(" + String.join(joiner, expressions) + ")";
        }
        return "true";
    }

    private static String compileConditionLeaf(JSONObject leaf, VarContext varContext) {
        String leftCode = leaf.getString("varCode");
        if (leftCode == null || leftCode.trim().isEmpty()) return "true";
        String op = leaf.getString("operator");
        if (op == null || op.trim().isEmpty()) op = "==";
        if ("*".equals(op)) return "true";

        Long leftId = getLongAny(leaf, "_varId", "varId", "leftVarId");
        String leftRefType = firstNotEmpty(leaf.getString("_refType"), leaf.getString("refType"), leaf.getString("leftRefType"));
        String left = varContext != null ? varContext.resolveVar(leftId, leftRefType, leftCode) : leftCode;

        String right;
        if ("VAR".equals(leaf.getString("valueKind"))) {
            String rightCode = leaf.getString("value");
            if (rightCode == null || rightCode.trim().isEmpty()) return "true";
            Long rightId = getLongAny(leaf, "_rightVarId", "rightVarId");
            String rightRefType = firstNotEmpty(leaf.getString("_rightRefType"), leaf.getString("rightRefType"));
            right = varContext != null ? varContext.resolveVar(rightId, rightRefType, rightCode) : rightCode;
        } else {
            Object value = leaf.get("value");
            if (value == null || String.valueOf(value).trim().isEmpty()) return "true";
            right = formatConditionConstant(leaf.getString("varType"), value);
        }
        return left + " " + op + " " + right;
    }

    private static Long getLongAny(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.containsKey(key) && obj.get(key) != null) {
                return obj.getLong(key);
            }
        }
        return null;
    }

    private static String firstNotEmpty(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return null;
    }

    private static String formatConditionConstant(String varType, Object value) {
        String text = String.valueOf(value);
        if ("STRING".equals(varType) || "ENUM".equals(varType) || "DATE".equals(varType)) {
            return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return text;
    }
}
