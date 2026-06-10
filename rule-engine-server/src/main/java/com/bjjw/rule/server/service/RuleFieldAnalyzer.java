package com.bjjw.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bjjw.rule.model.entity.RuleDataObjectField;
import com.bjjw.rule.model.entity.RuleDefinitionInputField;
import com.bjjw.rule.model.entity.RuleDefinitionOutputField;
import com.bjjw.rule.model.entity.RuleVariable;
import com.bjjw.rule.server.mapper.RuleDataObjectFieldMapper;
import com.bjjw.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.bjjw.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.bjjw.rule.server.mapper.RuleVariableMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 规则模型字段解析器。
 * 在规则保存时从 modelJson 中分析输入/输出变量，持久化到 rule_definition_input_field / rule_definition_output_field 表。
 */
@Service
public class RuleFieldAnalyzer {

    @Resource
    private RuleDefinitionInputFieldMapper inputFieldMapper;

    @Resource
    private RuleDefinitionOutputFieldMapper outputFieldMapper;

    @Resource
    private RuleVariableMapper ruleVariableMapper;

    @Resource
    private RuleDataObjectFieldMapper dataObjectFieldMapper;

    /**
     * 解析模型内容，提取输入/输出变量，持久化到字段表。
     * 写入字段时，优先通过 projectId 从变量管理表（rule_variable / rule_data_object_field）
     * 查询真实元信息（varLabel / varType / scriptName），若查不到再使用模型中的原始值。
     *
     * @param definitionId 规则ID
     * @param modelJson    设计器保存的模型 JSON
     * @param modelType    模型类型：TABLE/TREE/FLOW/CROSS/SCORE/CROSS_ADV/SCORE_ADV/SCRIPT
     * @param projectId    所属项目ID（查询变量元信息用，0 表示全局）
     */
    @Transactional
    public void analyzeAndPersist(Long definitionId, String modelJson, String modelType, Long projectId) {
        if (modelJson == null || modelJson.isEmpty() || "{}".equals(modelJson)) {
            return;
        }

        // 解析字段
        List<RuleDefinitionInputField> inputFields = extractInputFields(modelJson, modelType);
        List<RuleDefinitionOutputField> outputFields = extractOutputFields(modelJson, modelType);

        // 收集已有的 varId 映射（保留用户关联的变量）
        Map<String, Long> existingInputVarMap = getExistingVarIdMap(definitionId, true);
        Map<String, Long> existingOutputVarMap = getExistingVarIdMap(definitionId, false);

        // 从变量管理表查询元信息：varCode -> {varLabel, varType, scriptName, id}
        Map<String, Map<String, Object>> varMetaMap = buildVarMetaMap(projectId);

        // 删除旧字段
        inputFieldMapper.delete(new LambdaQueryWrapper<RuleDefinitionInputField>()
                .eq(RuleDefinitionInputField::getDefinitionId, definitionId));
        outputFieldMapper.delete(new LambdaQueryWrapper<RuleDefinitionOutputField>()
                .eq(RuleDefinitionOutputField::getDefinitionId, definitionId));

        // 写入新字段（补充变量元信息 + 恢复已有的 varId 关联）
        int inputOrder = 0;
        for (RuleDefinitionInputField field : inputFields) {
            field.setDefinitionId(definitionId);
            field.setSortOrder(inputOrder++);
            field.setStatus(1);
            field.setCreateTime(LocalDateTime.now());
            enrichFieldFromMeta(field, varMetaMap, existingInputVarMap);
            inputFieldMapper.insert(field);
        }

        int outputOrder = 0;
        for (RuleDefinitionOutputField field : outputFields) {
            field.setDefinitionId(definitionId);
            field.setSortOrder(outputOrder++);
            field.setStatus(1);
            field.setCreateTime(LocalDateTime.now());
            enrichFieldFromMeta(field, varMetaMap, existingOutputVarMap);
            outputFieldMapper.insert(field);
        }
    }

    /**
     * 从 rule_variable 和 rule_data_object_field 表中查询变量元信息，
     * 构造 varCode（小写） -> {varLabel, varType, scriptName, id} 的映射。
     * 优先使用 scriptName 匹配；若 scriptName 为空则用 varCode 匹配。
     */
    private Map<String, Map<String, Object>> buildVarMetaMap(Long projectId) {
        Map<String, Map<String, Object>> map = new HashMap<>();

        // 查询普通变量和常量（rule_variable）
        LambdaQueryWrapper<RuleVariable> varWrapper = new LambdaQueryWrapper<>();
        if (projectId != null && projectId > 0) {
            varWrapper.eq(RuleVariable::getProjectId, projectId);
        } else {
            varWrapper.eq(RuleVariable::getProjectId, 0);
        }
        varWrapper.in(RuleVariable::getStatus, Arrays.asList(1, null));
        List<RuleVariable> vars = ruleVariableMapper.selectList(varWrapper);
        for (RuleVariable v : vars) {
            String key = getVarKey(v);
            if (key != null && !map.containsKey(key)) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("id", v.getId());
                meta.put("varLabel", v.getVarLabel());
                meta.put("varType", v.getVarType());
                meta.put("scriptName", v.getScriptName());
                meta.put("varCode", v.getVarCode());
                meta.put("varSource", v.getVarSource());
                map.put(key, meta);
            }
        }

        // 查询数据对象字段（rule_data_object_field）
        LambdaQueryWrapper<RuleDataObjectField> fieldWrapper = new LambdaQueryWrapper<>();
        if (projectId != null && projectId > 0) {
            fieldWrapper.eq(RuleDataObjectField::getProjectId, projectId);
        } else {
            fieldWrapper.eq(RuleDataObjectField::getProjectId, 0);
        }
        fieldWrapper.in(RuleDataObjectField::getStatus, Arrays.asList(1, null));
        List<RuleDataObjectField> doFields = dataObjectFieldMapper.selectList(fieldWrapper);
        for (RuleDataObjectField f : doFields) {
            String key = getFieldKey(f);
            if (key != null && !map.containsKey(key)) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("id", f.getId());
                meta.put("varLabel", f.getVarLabel());
                meta.put("varType", f.getVarType());
                meta.put("scriptName", f.getScriptName());
                meta.put("varCode", f.getVarCode());
                meta.put("varSource", "dataObject");
                map.put(key, meta);
            }
        }

        return map;
    }

    /**
     * 获取普通变量的匹配键：优先 scriptName（小写），否则 varCode（小写）
     */
    private String getVarKey(RuleVariable v) {
        if (v.getScriptName() != null && !v.getScriptName().isEmpty()) {
            return v.getScriptName().toLowerCase();
        }
        if (v.getVarCode() != null && !v.getVarCode().isEmpty()) {
            return v.getVarCode().toLowerCase();
        }
        return null;
    }

    /**
     * 获取数据对象字段的匹配键：格式为 "对象scriptName.字段scriptName"
     * 先尝试精确匹配 scriptName，若字段本身无 scriptName 则用 varCode
     */
    private String getFieldKey(RuleDataObjectField f) {
        if (f.getScriptName() != null && !f.getScriptName().isEmpty()) {
            return f.getScriptName().toLowerCase();
        }
        if (f.getVarCode() != null && !f.getVarCode().isEmpty()) {
            return f.getVarCode().toLowerCase();
        }
        return null;
    }

    /**
     * 用变量元信息丰富字段：fieldLabel / varType / scriptName / varId。
     * 已有用户关联的 varId 保留；若未关联但 metaMap 中有对应变量，则自动填充 varId。
     */
    private void enrichFieldFromMeta(RuleDefinitionInputField field,
            Map<String, Map<String, Object>> varMetaMap,
            Map<String, Long> existingVarMap) {
        String fieldCode = field.getScriptName() != null ? field.getScriptName().toLowerCase() : null;
        if (fieldCode == null) return;

        Map<String, Object> meta = varMetaMap.get(fieldCode);
        if (meta != null) {
            // 补充变量元信息
            if (field.getFieldLabel() == null || field.getFieldLabel().isEmpty() || field.getFieldLabel().equals(field.getFieldName())) {
                String varLabel = (String) meta.get("varLabel");
                if (varLabel != null && !varLabel.isEmpty()) {
                    field.setFieldLabel(varLabel);
                }
            }
            String varType = (String) meta.get("varType");
            if (varType != null && !varType.isEmpty() && "STRING".equals(field.getFieldType())) {
                field.setFieldType(varType);
            }
            String scriptName = (String) meta.get("scriptName");
            if (scriptName != null && !scriptName.isEmpty()) {
                field.setScriptName(scriptName);
            }
            // 自动关联 varId（若已有用户关联则保留）
            if (field.getVarId() == null && existingVarMap.containsKey(field.getScriptName())) {
                field.setVarId(existingVarMap.get(field.getScriptName()));
            }
            // 若无已有关联但 meta 中有 id，自动关联
            if (field.getVarId() == null) {
                Object id = meta.get("id");
                if (id instanceof Long) {
                    field.setVarId((Long) id);
                }
            }
        }
    }

    /**
     * 用变量元信息丰富输出字段。
     */
    private void enrichFieldFromMeta(RuleDefinitionOutputField field,
            Map<String, Map<String, Object>> varMetaMap,
            Map<String, Long> existingVarMap) {
        String fieldCode = field.getScriptName() != null ? field.getScriptName().toLowerCase() : null;
        if (fieldCode == null) return;

        Map<String, Object> meta = varMetaMap.get(fieldCode);
        if (meta != null) {
            if (field.getFieldLabel() == null || field.getFieldLabel().isEmpty() || field.getFieldLabel().equals(field.getFieldName())) {
                String varLabel = (String) meta.get("varLabel");
                if (varLabel != null && !varLabel.isEmpty()) {
                    field.setFieldLabel(varLabel);
                }
            }
            String varType = (String) meta.get("varType");
            if (varType != null && !varType.isEmpty() && "STRING".equals(field.getFieldType())) {
                field.setFieldType(varType);
            }
            String scriptName = (String) meta.get("scriptName");
            if (scriptName != null && !scriptName.isEmpty()) {
                field.setScriptName(scriptName);
            }
            if (field.getVarId() == null && existingVarMap.containsKey(field.getScriptName())) {
                field.setVarId(existingVarMap.get(field.getScriptName()));
            }
            if (field.getVarId() == null) {
                Object id = meta.get("id");
                if (id instanceof Long) {
                    field.setVarId((Long) id);
                }
            }
        }
    }

    /**
     * 收集已存在的 varId 映射（scriptName -> varId）
     */
    private Map<String, Long> getExistingVarIdMap(Long definitionId, boolean isInput) {
        Map<String, Long> map = new HashMap<>();
        if (isInput) {
            List<RuleDefinitionInputField> fields = inputFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDefinitionInputField>()
                            .eq(RuleDefinitionInputField::getDefinitionId, definitionId)
                            .isNotNull(RuleDefinitionInputField::getVarId));
            for (RuleDefinitionInputField f : fields) {
                if (f.getScriptName() != null && f.getVarId() != null) {
                    map.put(f.getScriptName(), f.getVarId());
                }
            }
        } else {
            List<RuleDefinitionOutputField> fields = outputFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDefinitionOutputField>()
                            .eq(RuleDefinitionOutputField::getDefinitionId, definitionId)
                            .isNotNull(RuleDefinitionOutputField::getVarId));
            for (RuleDefinitionOutputField f : fields) {
                if (f.getScriptName() != null && f.getVarId() != null) {
                    map.put(f.getScriptName(), f.getVarId());
                }
            }
        }
        return map;
    }

    /**
     * 提取输入字段
     */
    public List<RuleDefinitionInputField> extractInputFields(String modelJson, String modelType) {
        List<RuleDefinitionInputField> fields = new ArrayList<>();
        JSONObject model = JSON.parseObject(modelJson);
        if (model == null) return fields;

        Set<String> varCodes = new LinkedHashSet<>();
        String type = modelType != null ? modelType.toUpperCase() : "";

        switch (type) {
            case "TABLE":
                extractFromDecisionTable(model, varCodes);
                break;
            case "TREE":
            case "FLOW":
                extractFromGraphModel(model, varCodes, true);
                break;
            case "CROSS":
                extractFromCrossTable(model, varCodes);
                break;
            case "SCORE":
                extractFromScorecard(model, varCodes);
                break;
            case "CROSS_ADV":
                extractFromAdvancedCrossTable(model, varCodes);
                break;
            case "SCORE_ADV":
                extractFromAdvancedScorecard(model, varCodes);
                break;
            case "SCRIPT":
                extractFromScript(model, varCodes, true);
                break;
            default:
                extractAllVarCodes(model, varCodes);
        }

        int order = 0;
        for (String varCode : varCodes) {
            RuleDefinitionInputField field = new RuleDefinitionInputField();
            field.setFieldName(varCode);
            field.setScriptName(varCode);
            field.setFieldLabel(varCode);
            field.setFieldType(inferFieldType(varCode));
            field.setSortOrder(order++);
            field.setStatus(1);
            field.setCreateTime(LocalDateTime.now());
            fields.add(field);
        }

        return fields;
    }

    /**
     * 提取输出字段
     */
    public List<RuleDefinitionOutputField> extractOutputFields(String modelJson, String modelType) {
        List<RuleDefinitionOutputField> fields = new ArrayList<>();
        JSONObject model = JSON.parseObject(modelJson);
        if (model == null) return fields;

        Set<String> varCodes = new LinkedHashSet<>();
        String type = modelType != null ? modelType.toUpperCase() : "";

        switch (type) {
            case "TABLE":
                extractOutputFromDecisionTable(model, varCodes);
                break;
            case "TREE":
            case "FLOW":
                extractFromGraphModel(model, varCodes, false);
                break;
            case "CROSS":
                extractOutputFromCrossTable(model, varCodes);
                break;
            case "SCORE":
                extractOutputFromScorecard(model, varCodes);
                break;
            case "CROSS_ADV":
                extractOutputFromAdvancedCrossTable(model, varCodes);
                break;
            case "SCORE_ADV":
                extractOutputFromAdvancedScorecard(model, varCodes);
                break;
            case "SCRIPT":
                extractFromScript(model, varCodes, false);
                break;
            default:
                // 默认不提取输出字段
        }

        int order = 0;
        for (String varCode : varCodes) {
            RuleDefinitionOutputField field = new RuleDefinitionOutputField();
            field.setFieldName(varCode);
            field.setScriptName(varCode);
            field.setFieldLabel(varCode);
            field.setFieldType(inferFieldType(varCode));
            field.setSortOrder(order++);
            field.setStatus(1);
            field.setCreateTime(LocalDateTime.now());
            fields.add(field);
        }

        return fields;
    }

    // ==================== 决策表 ====================

    private void extractFromDecisionTable(JSONObject model, Set<String> inputVars) {
        // 从 conditions 提取输入变量
        JSONArray conditions = model.getJSONArray("conditions");
        if (conditions != null) {
            for (int i = 0; i < conditions.size(); i++) {
                JSONObject cond = conditions.getJSONObject(i);
                String varCode = getString(cond, "varCode");
                if (varCode != null && !varCode.isEmpty()) {
                    inputVars.add(varCode);
                }
                // 递归提取条件树中的变量
                collectVarCodesFromConditionTree(cond, inputVars);
            }
        }
        // 从 rules 的 conditionRoot 提取
        JSONArray rules = model.getJSONArray("rules");
        if (rules != null) {
            for (int i = 0; i < rules.size(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                JSONObject condRoot = rule.getJSONObject("conditionRoot");
                if (condRoot != null) {
                    collectVarCodesFromConditionTree(condRoot, inputVars);
                }
            }
        }
    }

    private void collectVarCodesFromConditionTree(JSONObject node, Set<String> inputVars) {
        if (node == null) return;
        String varCode = getString(node, "varCode");
        if (varCode != null && !varCode.isEmpty()) {
            inputVars.add(varCode);
        }
        // 处理子条件（AND/OR 组）
        JSONArray children = node.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                JSONObject child = children.getJSONObject(i);
                collectVarCodesFromConditionTree(child, inputVars);
            }
        }
        // 处理左操作数
        JSONObject left = node.getJSONObject("left");
        if (left != null) {
            collectVarCodesFromConditionTree(left, inputVars);
        }
        // 处理右操作数（如果是变量引用）
        JSONObject right = node.getJSONObject("right");
        if (right != null) {
            collectVarCodesFromConditionTree(right, inputVars);
        }
    }

    private void extractOutputFromDecisionTable(JSONObject model, Set<String> outputVars) {
        // 从 actions 提取输出变量（动作列的 varCode）
        JSONArray actions = model.getJSONArray("actions");
        if (actions != null) {
            for (int i = 0; i < actions.size(); i++) {
                JSONObject action = actions.getJSONObject(i);
                String varCode = getString(action, "varCode");
                if (varCode != null && !varCode.isEmpty()) {
                    outputVars.add(varCode);
                }
            }
        }
        // 从 rules 的 actions 提取
        JSONArray rules = model.getJSONArray("rules");
        if (rules != null) {
            for (int i = 0; i < rules.size(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                JSONArray ruleActions = rule.getJSONArray("actions");
                if (ruleActions != null) {
                    for (int j = 0; j < ruleActions.size(); j++) {
                        JSONObject action = ruleActions.getJSONObject(j);
                        String varCode = getString(action, "varCode");
                        if (varCode != null && !varCode.isEmpty()) {
                            outputVars.add(varCode);
                        }
                    }
                }
            }
        }
    }

    // ==================== 决策树 / 决策流 ====================

    private void extractFromGraphModel(JSONObject model, Set<String> varCodes, boolean isInput) {
        // 从 nodes 数组中提取变量
        JSONArray nodes = model.getJSONArray("nodes");
        if (nodes == null && model.containsKey("graph")) {
            nodes = model.getJSONObject("graph").getJSONArray("nodes");
        }
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                String type = getString(node, "type");
                JSONObject properties = node.getJSONObject("properties");
                if (properties == null) continue;

                if (isInput) {
                    // 输入变量：条件节点
                    if ("condition".equals(type) || "CONDITION".equals(type) || "conditionNode".equals(type)) {
                        extractVarFromConditionProps(properties, varCodes);
                    }
                } else {
                    // 输出变量：任务节点 / 动作节点
                    if ("task".equals(type) || "TASK".equals(type) || "action".equals(type) || "ACTION".equals(type) || "taskNode".equals(type) || "actionNode".equals(type)) {
                        extractVarFromActionProps(properties, varCodes);
                    }
                }
            }
        }

        // 兼容 LogicFlow 格式：{ nodes: [...], edges: [...] }
        JSONArray graphNodes = model.getJSONArray("nodes");
        if (graphNodes != null) {
            for (int i = 0; i < graphNodes.size(); i++) {
                JSONObject node = graphNodes.getJSONObject(i);
                extractVarFromNodeData(node, varCodes, isInput);
            }
        }
    }

    private void extractVarFromNodeData(JSONObject node, Set<String> varCodes, boolean isInput) {
        // 兼容多种 LogicFlow 数据格式
        JSONObject data = node.getJSONObject("data");
        if (data == null) data = node;

        String type = getString(data, "type");
        if (type == null) type = getString(node, "type");

        JSONObject props = data.getJSONObject("properties");
        if (props == null) props = data;

        if (isInput) {
            if (type != null && (type.contains("condition") || type.contains("Condition"))) {
                extractVarFromConditionProps(props, varCodes);
            }
        } else {
            if (type != null && (type.contains("task") || type.contains("action") || type.contains("Action") || type.contains("Task"))) {
                extractVarFromActionProps(props, varCodes);
            }
        }
    }

    private void extractVarFromConditionProps(JSONObject props, Set<String> varCodes) {
        // 条件节点的变量
        String varCode = getString(props, "varCode");
        if (varCode != null && !varCode.isEmpty()) varCodes.add(varCode);

        // 兼容 varCode 嵌套
        JSONObject condVar = props.getJSONObject("condVar");
        if (condVar != null) {
            String cv = getString(condVar, "varCode");
            if (cv != null && !cv.isEmpty()) varCodes.add(cv);
        }

        // 条件表达式中的变量引用
        String condition = getString(props, "condition");
        if (condition != null && !condition.isEmpty()) {
            extractVarCodesFromConditionString(condition, varCodes);
        }

        // 兼容 leftVar
        String leftVar = getString(props, "leftVar");
        if (leftVar != null && !leftVar.isEmpty()) varCodes.add(leftVar);
    }

    private void extractVarFromActionProps(JSONObject props, Set<String> varCodes) {
        // 动作节点的输出变量
        String varCode = getString(props, "varCode");
        if (varCode != null && !varCode.isEmpty()) varCodes.add(varCode);

        // 兼容 resultVar
        JSONObject resultVar = props.getJSONObject("resultVar");
        if (resultVar != null) {
            String rv = getString(resultVar, "varCode");
            if (rv != null && !rv.isEmpty()) varCodes.add(rv);
        }

        // 动作数据中的变量引用
        JSONArray actionData = props.getJSONArray("actionData");
        if (actionData != null) {
            for (int i = 0; i < actionData.size(); i++) {
                JSONObject action = actionData.getJSONObject(i);
                String av = getString(action, "varCode");
                if (av != null && !av.isEmpty()) varCodes.add(av);
            }
        }

        // 兼容 outputVar / outputVars
        String outputVar = getString(props, "outputVar");
        if (outputVar != null && !outputVar.isEmpty()) varCodes.add(outputVar);
    }

    private void extractVarCodesFromConditionString(String condition, Set<String> varCodes) {
        // 简单解析：提取赋值语句左侧的变量
        // 格式如: income > 10000, taxRate * 0.13, 等
        if (condition == null || condition.isEmpty()) return;
        // 提取等号左侧变量: varCode = value
        int eqIdx = condition.indexOf('=');
        if (eqIdx > 0) {
            String left = condition.substring(0, eqIdx).trim();
            if (!left.isEmpty() && isValidVarName(left)) {
                varCodes.add(left);
            }
        }
        // 提取比较运算符左侧的变量
        String[] ops = { ">= ", "<= ", "!= ", "== ", "> ", "< " };
        for (String op : ops) {
            int idx = condition.indexOf(op);
            if (idx > 0) {
                String left = condition.substring(0, idx).trim();
                if (!left.isEmpty() && isValidVarName(left)) {
                    varCodes.add(left);
                }
            }
        }
    }

    private boolean isValidVarName(String name) {
        if (name == null || name.isEmpty()) return false;
        char c = name.charAt(0);
        if (!Character.isLetter(c) && c != '_') return false;
        for (int i = 1; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_' && ch != '.') return false;
        }
        // 排除明显不是变量名的关键词
        String lower = name.toLowerCase();
        if (lower.equals("true") || lower.equals("false") || lower.equals("null") || lower.equals("and") || lower.equals("or")) {
            return false;
        }
        return true;
    }

    // ==================== 交叉表 ====================

    private void extractFromCrossTable(JSONObject model, Set<String> inputVars) {
        JSONObject rowVar = model.getJSONObject("rowVar");
        if (rowVar != null) {
            String varCode = getString(rowVar, "varCode");
            if (varCode != null && !varCode.isEmpty()) inputVars.add(varCode);
        }
        JSONObject colVar = model.getJSONObject("colVar");
        if (colVar != null) {
            String varCode = getString(colVar, "varCode");
            if (varCode != null && !varCode.isEmpty()) inputVars.add(varCode);
        }
    }

    private void extractOutputFromCrossTable(JSONObject model, Set<String> outputVars) {
        JSONObject resultVar = model.getJSONObject("resultVar");
        if (resultVar != null) {
            String varCode = getString(resultVar, "varCode");
            if (varCode != null && !varCode.isEmpty()) outputVars.add(varCode);
        }
    }

    // ==================== 评分卡 ====================

    private void extractFromScorecard(JSONObject model, Set<String> inputVars) {
        JSONArray scoreItems = model.getJSONArray("scoreItems");
        if (scoreItems != null) {
            for (int i = 0; i < scoreItems.size(); i++) {
                JSONObject item = scoreItems.getJSONObject(i);
                String varCode = getString(item, "condVar");
                if (varCode != null && !varCode.isEmpty()) inputVars.add(varCode);
                // 兼容 condition
                String condition = getString(item, "condition");
                if (condition != null && !condition.isEmpty()) {
                    extractVarCodesFromConditionString(condition, inputVars);
                }
            }
        }
    }

    private void extractOutputFromScorecard(JSONObject model, Set<String> outputVars) {
        JSONObject resultVar = model.getJSONObject("resultVar");
        if (resultVar != null) {
            String varCode = getString(resultVar, "varCode");
            if (varCode != null && !varCode.isEmpty()) outputVars.add(varCode);
        }
    }

    // ==================== 复杂交叉表 ====================

    private void extractFromAdvancedCrossTable(JSONObject model, Set<String> inputVars) {
        JSONArray rowDimensions = model.getJSONArray("rowDimensions");
        if (rowDimensions != null) {
            for (int i = 0; i < rowDimensions.size(); i++) {
                extractDimensionVar(rowDimensions.getJSONObject(i), inputVars);
            }
        }
        JSONArray colDimensions = model.getJSONArray("colDimensions");
        if (colDimensions != null) {
            for (int i = 0; i < colDimensions.size(); i++) {
                extractDimensionVar(colDimensions.getJSONObject(i), inputVars);
            }
        }
    }

    private void extractDimensionVar(JSONObject dim, Set<String> varCodes) {
        String varCode = getString(dim, "varCode");
        if (varCode != null && !varCode.isEmpty()) varCodes.add(varCode);
        // 兼容嵌套结构
        JSONObject condVar = dim.getJSONObject("condVar");
        if (condVar != null) {
            String cv = getString(condVar, "varCode");
            if (cv != null && !cv.isEmpty()) varCodes.add(cv);
        }
    }

    private void extractOutputFromAdvancedCrossTable(JSONObject model, Set<String> outputVars) {
        JSONObject resultVar = model.getJSONObject("resultVar");
        if (resultVar != null) {
            String varCode = getString(resultVar, "varCode");
            if (varCode != null && !varCode.isEmpty()) outputVars.add(varCode);
        }
    }

    // ==================== 复杂评分卡 ====================

    private void extractFromAdvancedScorecard(JSONObject model, Set<String> inputVars) {
        JSONArray dimensionGroups = model.getJSONArray("dimensionGroups");
        if (dimensionGroups != null) {
            for (int i = 0; i < dimensionGroups.size(); i++) {
                JSONObject group = dimensionGroups.getJSONObject(i);
                JSONArray dimensions = group.getJSONArray("dimensions");
                if (dimensions != null) {
                    for (int j = 0; j < dimensions.size(); j++) {
                        JSONObject dim = dimensions.getJSONObject(j);
                        String varCode = getString(dim, "varCode");
                        if (varCode != null && !varCode.isEmpty()) inputVars.add(varCode);
                        // 兼容 condition
                        String condition = getString(dim, "condition");
                        if (condition != null && !condition.isEmpty()) {
                            extractVarCodesFromConditionString(condition, inputVars);
                        }
                    }
                }
            }
        }
    }

    private void extractOutputFromAdvancedScorecard(JSONObject model, Set<String> outputVars) {
        JSONObject resultVar = model.getJSONObject("resultVar");
        if (resultVar != null) {
            String varCode = getString(resultVar, "varCode");
            if (varCode != null && !varCode.isEmpty()) outputVars.add(varCode);
        }
    }

    // ==================== QL 脚本 ====================

    private void extractFromScript(JSONObject model, Set<String> varCodes, boolean isInput) {
        String script = getString(model, "script");
        if (script == null || script.isEmpty()) return;

        // 简单解析：从脚本中提取变量
        // 输入变量：赋值语句左侧（output = ...）的左侧操作数
        // 输出变量：赋值语句左侧
        String[] lines = script.split("\n");
        Set<String> assignedVars = new HashSet<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;

            // 匹配赋值语句: var = expression 或 var.Prop = expression
            int eqIdx = line.indexOf('=');
            if (eqIdx > 0 && !line.substring(0, eqIdx).trim().contains("==")) {
                String left = line.substring(0, eqIdx).trim();
                // 清理可能的括号或运算
                int parenIdx = left.indexOf('(');
                if (parenIdx > 0) left = left.substring(0, parenIdx).trim();
                if (isValidVarName(left)) {
                    assignedVars.add(left);
                }
            }
        }

        if (isInput) {
            // 输入变量：从赋值右侧提取引用的变量
            // 简单实现：排除已赋值的变量，剩余的都是输入变量
            // 实际应用中需要更复杂的脚本分析，这里做简化处理
        } else {
            // 输出变量：赋值语句左侧的变量
            varCodes.addAll(assignedVars);
        }
    }

    // ==================== 通用提取（兜底） ====================

    private void extractAllVarCodes(JSONObject model, Set<String> varCodes) {
        // 递归扫描所有 varCode 字段
        collectVarCodesRecursive(model, varCodes);
    }

    private void collectVarCodesRecursive(JSONObject obj, Set<String> varCodes) {
        if (obj == null) return;
        for (String key : obj.keySet()) {
            Object val = obj.get(key);
            if ("varCode".equals(key) || "scriptName".equals(key)) {
                String vc = obj.getString(key);
                if (vc != null && !vc.isEmpty() && isValidVarName(vc)) {
                    varCodes.add(vc);
                }
            } else if (val instanceof JSONObject) {
                collectVarCodesRecursive((JSONObject) val, varCodes);
            } else if (val instanceof JSONArray) {
                JSONArray arr = (JSONArray) val;
                for (int i = 0; i < arr.size(); i++) {
                    Object item = arr.get(i);
                    if (item instanceof JSONObject) {
                        collectVarCodesRecursive((JSONObject) item, varCodes);
                    }
                }
            }
        }
    }

    // ==================== 工具方法 ====================

    private String getString(JSONObject obj, String key) {
        if (obj == null) return null;
        Object val = obj.get(key);
        return val != null ? val.toString() : null;
    }

    private String inferFieldType(String varCode) {
        if (varCode == null) return "STRING";
        String lower = varCode.toLowerCase();
        if (lower.contains("rate") || lower.contains("ratio") || lower.contains("amount") || lower.contains("score") || lower.contains("percent")) {
            return "DOUBLE";
        }
        if (lower.contains("count") || lower.contains("num") || lower.contains("qty") || lower.contains("total")) {
            return "INTEGER";
        }
        if (lower.contains("flag") || lower.contains("is") || lower.contains("has") || lower.contains("enable")) {
            return "BOOLEAN";
        }
        return "STRING";
    }
}