package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 规则模型变量解析器
 * 参考 rule-engine-builder-ui/src/views/test/RuleTest.vue 中 collectVarCodes 系列方法
 */
@Component
public class RuleModelVarParser {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");

    private static final Set<String> KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "true", "false", "null", "undefined", "NaN", "Infinity",
            "and", "or", "not",
            "lt", "le", "gt", "ge", "eq", "ne",
            "div", "mod", "in", "between",
            "if", "else", "for", "while", "return", "function"
    )));

    /**
     * 解析结果：输入变量代码集合、输出变量代码集合
     */
    @Data
    public static class ParseResult {
        /** 输入变量代码集合 */
        private Set<String> inputCodes = new HashSet<>();
        /** 输出变量代码集合 */
        private Set<String> outputCodes = new HashSet<>();
    }

    /**
     * 解析规则模型的输入/输出变量代码
     * @param modelJson 模型 JSON 字符串
     * @param modelType 模型类型：TABLE/TREE/FLOW/RULE_SET/CROSS/CROSS_ADV/SCORE/SCORE_ADV/SCRIPT
     * @return 解析结果
     */
    public ParseResult parse(String modelJson, String modelType) {
        ParseResult result = new ParseResult();
        if (modelJson == null || modelJson.isEmpty()) {
            return result;
        }
        try {
            JSONObject model = JSON.parseObject(modelJson);
            if (model == null) {
                return result;
            }
            switch (modelType) {
                case "TABLE":
                    parseTableModel(model, result);
                    break;
                case "TREE":
                    parseTreeModel(model, result);
                    break;
                case "FLOW":
                    parseFlowModel(model, result);
                    break;
                case "RULE_SET":
                    parseRuleSetModel(model, result);
                    break;
                case "CROSS":
                    parseCrossModel(model, result);
                    break;
                case "CROSS_ADV":
                    parseCrossAdvModel(model, result);
                    break;
                case "SCORE":
                    parseScoreModel(model, result);
                    break;
                case "SCORE_ADV":
                    parseScoreAdvModel(model, result);
                    break;
                case "SCRIPT":
                    parseScriptModel(model, result);
                    break;
                default:
                    parseFallback(model, result);
                    break;
            }
        } catch (Exception e) {
            // 解析失败返回空结果
        }
        return result;
    }

    // ==================== 决策表 ====================

    private void parseTableModel(JSONObject model, ParseResult result) {
        // 从 rules 中提取
        JSONArray rules = model.getJSONArray("rules");
        if (rules != null) {
            for (int i = 0; i < rules.size(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                // 条件树
                extractFromConditionRoot(rule.getJSONObject("conditionRoot"), result.getInputCodes());
                // 条件数组（兼容旧格式）
                extractFromConditions(rule.getJSONArray("conditions"), result.getInputCodes());
                // 动作：输出变量
                JSONArray actions = rule.getJSONArray("actions");
                if (actions != null) {
                    for (int j = 0; j < actions.size(); j++) {
                        JSONObject a = actions.getJSONObject(j);
                        if (a.containsKey("varCode") && !a.getString("varCode").isEmpty()) {
                            result.getOutputCodes().add(a.getString("varCode"));
                        }
                        if (a.containsKey("target") && !a.getString("target").isEmpty()) {
                            result.getOutputCodes().add(a.getString("target"));
                        }
                    }
                }
            }
        }
        // 顶层 conditions 和 actions（兼容旧格式）
        extractFromConditions(model.getJSONArray("conditions"), result.getInputCodes());
        JSONArray topActions = model.getJSONArray("actions");
        if (topActions != null) {
            for (int j = 0; j < topActions.size(); j++) {
                JSONObject a = topActions.getJSONObject(j);
                if (a.containsKey("varCode") && !a.getString("varCode").isEmpty()) {
                    result.getOutputCodes().add(a.getString("varCode"));
                }
                if (a.containsKey("target") && !a.getString("target").isEmpty()) {
                    result.getOutputCodes().add(a.getString("target"));
                }
            }
        }
    }

    // ==================== 决策树 ====================

    private void parseTreeModel(JSONObject model, ParseResult result) {
        // 从 nodes 提取
        JSONArray nodes = model.getJSONArray("nodes");
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                extractFromTreeNode(nodes.getJSONObject(i), result);
            }
        }
        // 从 edges 提取
        JSONArray edges = model.getJSONArray("edges");
        if (edges != null) {
            for (int i = 0; i < edges.size(); i++) {
                extractFromExpr(edges.getJSONObject(i).getString("conditionExpression"), result.getInputCodes());
            }
        }
        // 顶层 conditionRoot
        extractFromConditionRoot(model.getJSONObject("conditionRoot"), result.getInputCodes());
    }

    private void extractFromTreeNode(JSONObject node, ParseResult result) {
        // varCode
        if (node.containsKey("varCode") && !node.getString("varCode").isEmpty()) {
            result.getInputCodes().add(node.getString("varCode"));
        }
        // conditionExpression
        extractFromExpr(node.getString("conditionExpression"), result.getInputCodes());
        // condition
        extractConditionObj(node.get("condition"), result.getInputCodes());
        // actionData
        extractFromActionData(node.getJSONArray("actionData"), result);
        // children
        JSONArray children = node.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                extractFromTreeNode(children.getJSONObject(i), result);
            }
        }
    }

    // ==================== 决策流 ====================

    private void parseFlowModel(JSONObject model, ParseResult result) {
        // 从 nodes 提取
        JSONArray nodes = model.getJSONArray("nodes");
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                extractFromFlowNode(nodes.getJSONObject(i), result);
            }
        }
        // 从 edges 提取
        JSONArray edges = model.getJSONArray("edges");
        if (edges != null) {
            for (int i = 0; i < edges.size(); i++) {
                extractFromExpr(edges.getJSONObject(i).getString("conditionExpression"), result.getInputCodes());
            }
        }
        // 顶层 conditionRoot
        extractFromConditionRoot(model.getJSONObject("conditionRoot"), result.getInputCodes());
    }

    private void extractFromFlowNode(JSONObject node, ParseResult result) {
        if (node.containsKey("varCode") && !node.getString("varCode").isEmpty()) {
            result.getInputCodes().add(node.getString("varCode"));
        }
        extractFromExpr(node.getString("conditionExpression"), result.getInputCodes());
        extractConditionObj(node.get("condition"), result.getInputCodes());

        // inputVars
        JSONObject props = node.getJSONObject("properties");
        if (props != null) {
            JSONArray inputVars = props.getJSONArray("inputVars");
            if (inputVars != null) {
                for (int i = 0; i < inputVars.size(); i++) {
                    JSONObject v = inputVars.getJSONObject(i);
                    if (v.containsKey("varCode") && !v.getString("varCode").isEmpty()) {
                        result.getInputCodes().add(v.getString("varCode"));
                    }
                }
            }
        }
        // actionData
        extractFromActionData(node.getJSONArray("actionData"), result);
        // properties.actionData
        if (props != null) {
            extractFromActionData(props.getJSONArray("actionData"), result);
        }
        // children
        JSONArray children = node.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                extractFromFlowNode(children.getJSONObject(i), result);
            }
        }
    }

    // ==================== 规则集 ====================

    private void parseRuleSetModel(JSONObject model, ParseResult result) {
        JSONArray rules = model.getJSONArray("rules");
        if (rules == null) {
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            if (rule == null) {
                continue;
            }
            extractFromConditionRoot(rule.getJSONObject("conditionRoot"), result.getInputCodes());
            extractFromConditions(rule.getJSONArray("conditions"), result.getInputCodes());
            extractFromActionData(rule.getJSONArray("actionData"), result);
        }
    }

    // ==================== 交叉表 ====================

    private void parseCrossModel(JSONObject model, ParseResult result) {
        JSONObject rowVar = model.getJSONObject("rowVar");
        if (rowVar != null && rowVar.containsKey("varCode") && !rowVar.getString("varCode").isEmpty()) {
            result.getInputCodes().add(rowVar.getString("varCode"));
        }
        JSONObject colVar = model.getJSONObject("colVar");
        if (colVar != null && colVar.containsKey("varCode") && !colVar.getString("varCode").isEmpty()) {
            result.getInputCodes().add(colVar.getString("varCode"));
        }
        JSONObject resultVar = model.getJSONObject("resultVar");
        if (resultVar != null && resultVar.containsKey("varCode") && !resultVar.getString("varCode").isEmpty()) {
            result.getOutputCodes().add(resultVar.getString("varCode"));
        }
    }

    // ==================== 复杂交叉表 ====================

    private void parseCrossAdvModel(JSONObject model, ParseResult result) {
        // rowDimensions
        JSONArray rowDims = model.getJSONArray("rowDimensions");
        if (rowDims != null) {
            for (int i = 0; i < rowDims.size(); i++) {
                JSONObject dim = rowDims.getJSONObject(i);
                if (dim.containsKey("varCode") && !dim.getString("varCode").isEmpty()) {
                    result.getInputCodes().add(dim.getString("varCode"));
                }
                extractFromConditionRoot(dim.getJSONObject("conditionRoot"), result.getInputCodes());
            }
        }
        // colDimensions
        JSONArray colDims = model.getJSONArray("colDimensions");
        if (colDims != null) {
            for (int i = 0; i < colDims.size(); i++) {
                JSONObject dim = colDims.getJSONObject(i);
                if (dim.containsKey("varCode") && !dim.getString("varCode").isEmpty()) {
                    result.getInputCodes().add(dim.getString("varCode"));
                }
                extractFromConditionRoot(dim.getJSONObject("conditionRoot"), result.getInputCodes());
            }
        }
        // resultVar
        JSONObject resultVar = model.getJSONObject("resultVar");
        if (resultVar != null && resultVar.containsKey("varCode") && !resultVar.getString("varCode").isEmpty()) {
            result.getOutputCodes().add(resultVar.getString("varCode"));
        }
    }

    // ==================== 评分卡 ====================

    private void parseScoreModel(JSONObject model, ParseResult result) {
        JSONArray items = model.getJSONArray("scoreItems");
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                // conditionRoot（树形条件）
                extractFromConditionRoot(item.getJSONObject("conditionRoot"), result.getInputCodes());
                // condition（字符串表达式）
                extractFromExpr(item.getString("condition"), result.getInputCodes());
                // condVar
                if (item.containsKey("condVar") && !item.getString("condVar").isEmpty()) {
                    result.getInputCodes().add(item.getString("condVar"));
                }
            }
        }
        // resultVar
        JSONObject resultVar = model.getJSONObject("resultVar");
        if (resultVar != null && resultVar.containsKey("varCode") && !resultVar.getString("varCode").isEmpty()) {
            result.getOutputCodes().add(resultVar.getString("varCode"));
        }
    }

    // ==================== 复杂评分卡 ====================

    private void parseScoreAdvModel(JSONObject model, ParseResult result) {
        JSONArray groups = model.getJSONArray("dimensionGroups");
        if (groups != null) {
            for (int i = 0; i < groups.size(); i++) {
                JSONObject group = groups.getJSONObject(i);
                JSONArray dims = group.getJSONArray("dimensions");
                if (dims != null) {
                    for (int j = 0; j < dims.size(); j++) {
                        JSONObject dim = dims.getJSONObject(j);
                        if (dim.containsKey("varCode") && !dim.getString("varCode").isEmpty()) {
                            result.getInputCodes().add(dim.getString("varCode"));
                        }
                        JSONArray rules = dim.getJSONArray("rules");
                        if (rules != null) {
                            for (int k = 0; k < rules.size(); k++) {
                                JSONObject rule = rules.getJSONObject(k);
                                if (rule.containsKey("condVar") && !rule.getString("condVar").isEmpty()) {
                                    result.getInputCodes().add(rule.getString("condVar"));
                                }
                                extractFromExpr(rule.getString("condition"), result.getInputCodes());
                                extractFromConditionRoot(rule.getJSONObject("conditionRoot"), result.getInputCodes());
                            }
                        }
                    }
                }
            }
        }
        // resultVar
        JSONObject resultVar = model.getJSONObject("resultVar");
        if (resultVar != null && resultVar.containsKey("varCode") && !resultVar.getString("varCode").isEmpty()) {
            result.getOutputCodes().add(resultVar.getString("varCode"));
        }
    }

    // ==================== QL脚本 ====================

    private void parseScriptModel(JSONObject model, ParseResult result) {
        // inputVars
        JSONArray inputVars = model.getJSONArray("inputVars");
        if (inputVars != null) {
            for (int i = 0; i < inputVars.size(); i++) {
                JSONObject v = inputVars.getJSONObject(i);
                if (v.containsKey("varCode") && !v.getString("varCode").isEmpty()) {
                    result.getInputCodes().add(v.getString("varCode"));
                }
            }
        }
        // 从 script 内容中提取变量引用（简单标识符提取）
        String script = model.getString("script");
        extractIdentifiers(script, result.getInputCodes());
    }

    // ==================== 兜底解析 ====================

    private void parseFallback(JSONObject model, ParseResult result) {
        recursiveExtract(model, result);
    }

    private void recursiveExtract(JSONObject obj, ParseResult result) {
        if (obj == null) return;
        for (String key : obj.keySet()) {
            Object val = obj.get(key);
            if (val == null) continue;

            if ("varCode".equals(key)) {
                String code = obj.getString("varCode");
                if (code != null && !code.isEmpty()) {
                    result.getInputCodes().add(code);
                }
            } else if ("conditionExpression".equals(key)) {
                if (val instanceof String) {
                    extractFromExpr((String) val, result.getInputCodes());
                } else if (val instanceof JSONObject) {
                    recursiveExtract((JSONObject) val, result);
                }
            } else if ("condition".equals(key)) {
                extractConditionObj(val, result.getInputCodes());
            } else if ("actions".equals(key) && val instanceof JSONArray) {
                JSONArray actions = (JSONArray) val;
                for (int i = 0; i < actions.size(); i++) {
                    JSONObject a = actions.getJSONObject(i);
                    if (a.containsKey("varCode") && !a.getString("varCode").isEmpty()) {
                        result.getOutputCodes().add(a.getString("varCode"));
                    }
                    if (a.containsKey("target") && !a.getString("target").isEmpty()) {
                        result.getOutputCodes().add(a.getString("target"));
                    }
                }
            } else if (val instanceof JSONObject) {
                recursiveExtract((JSONObject) val, result);
            } else if (val instanceof JSONArray) {
                JSONArray arr = (JSONArray) val;
                for (int i = 0; i < arr.size(); i++) {
                    Object item = arr.get(i);
                    if (item instanceof JSONObject) {
                        recursiveExtract((JSONObject) item, result);
                    }
                }
            }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 conditions 数组提取变量代码
     */
    private void extractFromConditions(JSONArray conditions, Set<String> codes) {
        if (conditions == null) return;
        for (int i = 0; i < conditions.size(); i++) {
            JSONObject c = conditions.getJSONObject(i);
            if (c.containsKey("varCode") && !c.getString("varCode").isEmpty()) {
                codes.add(c.getString("varCode"));
            }
            String expr = c.getString("conditionExpression");
            if (expr != null && !expr.isEmpty()) {
                // 从 "varCode > 5" 格式中提取变量名
                int opIdx = findOperatorIndex(expr);
                if (opIdx > 0) {
                    String var = expr.substring(0, opIdx).trim();
                    if (isIdentifier(var)) codes.add(var);
                }
            }
        }
    }

    /**
     * 从 conditionRoot 递归提取变量代码
     */
    private void extractFromConditionRoot(JSONObject root, Set<String> codes) {
        if (root == null) return;
        // 叶子节点
        if ("leaf".equals(root.getString("type")) && root.containsKey("varCode") && !root.getString("varCode").isEmpty()) {
            codes.add(root.getString("varCode"));
        }
        if (root.containsKey("varCode") && !root.getString("varCode").isEmpty()) {
            codes.add(root.getString("varCode"));
        }
        // conditionExpression
        String expr = root.getString("conditionExpression");
        extractFromExpr(expr, codes);
        // condition
        extractConditionObj(root.get("condition"), codes);
        // children
        JSONArray children = root.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                extractFromConditionRoot(children.getJSONObject(i), codes);
            }
        }
    }

    /**
     * 从 actionData 数组提取变量代码
     */
    private void extractFromActionData(JSONArray actionData, ParseResult result) {
        if (actionData == null) return;
        for (int i = 0; i < actionData.size(); i++) {
            JSONObject a = actionData.getJSONObject(i);
            String type = a.getString("type");
            if ("assign".equals(type)) {
                // target 是输出变量
                if (a.containsKey("target") && !a.getString("target").isEmpty()) {
                    result.getOutputCodes().add(a.getString("target"));
                }
                // value 中可能包含输入变量
                String val = a.getString("value");
                if (val != null && !val.isEmpty() && !isStringLiteral(val)) {
                    extractIdentifiers(val, result.getInputCodes());
                }
            } else if ("func-call".equals(type)) {
                if (a.containsKey("target") && !a.getString("target").isEmpty()) {
                    result.getOutputCodes().add(a.getString("target"));
                }
                // args 是输入参数
                JSONArray args = a.getJSONArray("args");
                JSONArray argRefs = a.getJSONArray("_argRefs");
                if (args != null && argRefs != null) {
                    for (int j = 0; j < args.size(); j++) {
                        JSONObject ref = j < argRefs.size() ? argRefs.getJSONObject(j) : null;
                        String arg = args.getString(j);
                        if (hasExplicitArgRef(ref) && arg != null && !arg.isEmpty()) {
                            result.getInputCodes().add(arg);
                        }
                    }
                }
            } else if ("if-block".equals(type)) {
                JSONArray branches = a.getJSONArray("branches");
                if (branches != null) {
                    for (int j = 0; j < branches.size(); j++) {
                        JSONObject branch = branches.getJSONObject(j);
                        addCode(branch.getString("condVar"), result.getInputCodes());
                        extractFromActionData(branch.getJSONArray("actions"), result);
                    }
                }
            } else if ("switch-block".equals(type)) {
                addCode(a.getString("matchVar"), result.getInputCodes());
                JSONArray cases = a.getJSONArray("cases");
                if (cases != null) {
                    for (int j = 0; j < cases.size(); j++) {
                        extractFromActionData(cases.getJSONObject(j).getJSONArray("actions"), result);
                    }
                }
                extractFromActionData(a.getJSONArray("defaultActions"), result);
            } else if ("foreach".equals(type)) {
                extractIdentifiers(a.getString("listExpr"), result.getInputCodes());
                extractFromActionData(a.getJSONArray("actions"), result);
            } else if ("ternary".equals(type)) {
                addCode(a.getString("condVar"), result.getInputCodes());
                addCode(a.getString("target"), result.getOutputCodes());
            } else if ("in-check".equals(type)) {
                addCode(a.getString("checkVar"), result.getInputCodes());
                addCode(a.getString("target"), result.getOutputCodes());
            } else if ("template-str".equals(type)) {
                addCode(a.getString("target"), result.getOutputCodes());
                JSONArray parts = a.getJSONArray("parts");
                if (parts != null) {
                    for (int j = 0; j < parts.size(); j++) {
                        JSONObject part = parts.getJSONObject(j);
                        if ("expr".equals(part.getString("type"))) {
                            extractIdentifiers(part.getString("content"), result.getInputCodes());
                        }
                    }
                }
            } else if ("rule-call".equals(type)) {
                addCode(a.getString("target"), result.getOutputCodes());
            }
        }
    }

    private void addCode(String code, Set<String> codes) {
        if (code != null && !code.isEmpty()) {
            codes.add(code);
        }
    }

    private boolean hasExplicitArgRef(JSONObject ref) {
        return ref != null && (ref.getLong("_varId") != null || ref.getLong("varId") != null);
    }

    /**
     * 从条件表达式字符串中提取变量名
     */
    private void extractFromExpr(String expr, Set<String> codes) {
        if (expr == null || expr.isEmpty()) return;
        if (expr instanceof String) {
            String str = (String) expr;
            java.util.regex.Matcher m = IDENTIFIER_PATTERN.matcher(str);
            while (m.find()) {
                String token = m.group(1);
                if (!KEYWORDS.contains(token)) {
                    codes.add(token);
                }
            }
        }
    }

    /**
     * 提取 condition 对象中的变量（可能是字符串或对象）
     */
    private void extractConditionObj(Object cond, Set<String> codes) {
        if (cond == null) return;
        if (cond instanceof String) {
            extractFromExpr((String) cond, codes);
        } else if (cond instanceof JSONObject) {
            extractFromConditionRoot((JSONObject) cond, codes);
        }
    }

    /**
     * 从脚本内容中提取所有标识符（过滤关键字）
     */
    private void extractIdentifiers(String script, Set<String> codes) {
        if (script == null || script.isEmpty()) return;
        java.util.regex.Matcher m = IDENTIFIER_PATTERN.matcher(script);
        while (m.find()) {
            String token = m.group(1);
            if (!KEYWORDS.contains(token)) {
                codes.add(token);
            }
        }
    }

    /**
     * 判断字符串是否是字符串字面量
     */
    private boolean isStringLiteral(String s) {
        if (s == null || s.length() < 2) return false;
        String trimmed = s.trim();
        return (trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"));
    }

    /**
     * 判断字符串是否是合法标识符
     */
    private boolean isIdentifier(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }

    /**
     * 从表达式中找到操作符的位置（用于兼容旧格式 "var > 5"）
     */
    private int findOperatorIndex(String expr) {
        if (expr == null) return -1;
        // 找到操作符的位置：==, !=, >=, <=, >, <, =, !=
        int idx = expr.indexOf("==");
        if (idx >= 0) return idx;
        idx = expr.indexOf("!=");
        if (idx >= 0) return idx;
        idx = expr.indexOf(">=");
        if (idx >= 0) return idx;
        idx = expr.indexOf("<=");
        if (idx >= 0) return idx;
        idx = expr.indexOf("=");
        if (idx >= 0) return idx;
        idx = expr.indexOf(">");
        if (idx >= 0) return idx;
        idx = expr.indexOf("<");
        if (idx >= 0) return idx;
        return -1;
    }
}
