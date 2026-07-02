package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则模型字段解析器。
 * 在规则保存时从 modelJson 中分析输入/输出变量，持久化到 rule_definition_input_field / rule_definition_output_field 表。
 */
@Service
public class RuleFieldAnalyzer {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*");
    private static final Set<String> SCRIPT_KEYWORDS = new HashSet<>(Arrays.asList(
            "if", "else", "for", "while", "switch", "case", "default", "break", "continue", "return",
            "true", "false", "null", "and", "or", "in", "new", "var", "let", "const", "do",
            "try", "catch", "finally", "throw", "class", "import", "package"
    ));

    @Resource
    private RuleDefinitionInputFieldMapper inputFieldMapper;

    @Resource
    private RuleDefinitionOutputFieldMapper outputFieldMapper;

    @Resource
    private RuleVariableMapper ruleVariableMapper;

    @Resource
    private RuleDataObjectFieldMapper dataObjectFieldMapper;

    @Resource
    private RuleDataObjectMapper dataObjectMapper;

    @Resource
    private RuleModelMapper modelMapper;

    @Resource
    private RuleModelInputFieldMapper modelInputFieldMapper;

    @Resource
    private RuleModelOutputFieldMapper modelOutputFieldMapper;

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
        Map<String, String> existingInputRefTypeMap = getExistingRefTypeMap(definitionId, true);
        Map<String, String> existingOutputRefTypeMap = getExistingRefTypeMap(definitionId, false);
        Map<String, FieldRef> explicitRefMap = collectExplicitRefs(modelJson);

        // 从变量管理表查询元信息：varCode -> {varLabel, varType, scriptName, id}
        Map<String, Map<String, Object>> varMetaMap = buildVarMetaMap(projectId);

        // 先补齐变量元信息，模型引用会展开为模型自身输入字段。
        List<RuleDefinitionInputField> preparedInputFields = new ArrayList<>();
        for (RuleDefinitionInputField field : inputFields) {
            applyExplicitRef(field, explicitRefMap);
            enrichFieldFromMeta(field, varMetaMap, existingInputVarMap, existingInputRefTypeMap);
            preparedInputFields.add(field);
        }
        preparedInputFields = expandModelInputFields(preparedInputFields, varMetaMap);

        List<RuleDefinitionOutputField> preparedOutputFields = new ArrayList<>();
        for (RuleDefinitionOutputField field : outputFields) {
            applyExplicitRef(field, explicitRefMap);
            enrichFieldFromMeta(field, varMetaMap, existingOutputVarMap, existingOutputRefTypeMap);
            preparedOutputFields.add(field);
        }

        // 删除旧字段
        inputFieldMapper.delete(new LambdaQueryWrapper<RuleDefinitionInputField>()
                .eq(RuleDefinitionInputField::getDefinitionId, definitionId));
        outputFieldMapper.delete(new LambdaQueryWrapper<RuleDefinitionOutputField>()
                .eq(RuleDefinitionOutputField::getDefinitionId, definitionId));

        // 写入新字段（补充变量元信息 + 恢复已有的 varId 关联）
        int inputOrder = 0;
        for (RuleDefinitionInputField field : preparedInputFields) {
            field.setDefinitionId(definitionId);
            field.setSortOrder(inputOrder++);
            field.setStatus(1);
            field.setCreateTime(LocalDateTime.now());
            inputFieldMapper.insert(field);
        }

        int outputOrder = 0;
        for (RuleDefinitionOutputField field : preparedOutputFields) {
            field.setDefinitionId(definitionId);
            field.setSortOrder(outputOrder++);
            field.setStatus(1);
            field.setCreateTime(LocalDateTime.now());
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
            varWrapper.and(w -> w.eq(RuleVariable::getScope, RuleVariableService.SCOPE_GLOBAL)
                    .or()
                    .eq(RuleVariable::getScope, RuleVariableService.SCOPE_PROJECT)
                    .eq(RuleVariable::getProjectId, projectId));
        } else {
            varWrapper.eq(RuleVariable::getScope, RuleVariableService.SCOPE_GLOBAL);
        }
        varWrapper.eq(RuleVariable::getStatus, 1);
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
                meta.put("sourceConfig", v.getSourceConfig());
                meta.put("refType", "CONSTANT".equals(v.getVarSource()) ? "CONSTANT" : "VARIABLE");
                map.put(key, meta);
            }
        }

        // 查询数据对象字段（rule_data_object_field）
        Map<Long, RuleDataObject> objectMap = buildObjectMap(projectId);
        LambdaQueryWrapper<RuleDataObjectField> fieldWrapper = new LambdaQueryWrapper<>();
        if (projectId != null && projectId > 0) {
            fieldWrapper.and(w -> w.eq(RuleDataObjectField::getScope, RuleVariableService.SCOPE_GLOBAL)
                    .or()
                    .eq(RuleDataObjectField::getScope, RuleVariableService.SCOPE_PROJECT)
                    .eq(RuleDataObjectField::getProjectId, projectId));
        } else {
            fieldWrapper.eq(RuleDataObjectField::getScope, RuleVariableService.SCOPE_GLOBAL);
        }
        fieldWrapper.eq(RuleDataObjectField::getStatus, 1);
        List<RuleDataObjectField> doFields = dataObjectFieldMapper.selectList(fieldWrapper);
        for (RuleDataObjectField f : doFields) {
            String scriptName = buildObjectFieldScriptName(f, objectMap);
            String key = scriptName != null ? scriptName.toLowerCase() : null;
            if (key != null && !map.containsKey(key)) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("id", f.getId());
                meta.put("varLabel", f.getVarLabel());
                meta.put("varType", f.getVarType());
                meta.put("scriptName", scriptName);
                meta.put("varCode", f.getVarCode());
                meta.put("varSource", "dataObject");
                meta.put("refType", "DATA_OBJECT");
                map.put(key, meta);
            }
        }

        LambdaQueryWrapper<RuleModel> modelWrapper = new LambdaQueryWrapper<>();
        if (projectId != null && projectId > 0) {
            modelWrapper.and(w -> w.eq(RuleModel::getScope, RuleVariableService.SCOPE_GLOBAL)
                    .or()
                    .eq(RuleModel::getScope, RuleVariableService.SCOPE_PROJECT)
                    .eq(RuleModel::getProjectId, projectId));
        } else {
            modelWrapper.eq(RuleModel::getScope, RuleVariableService.SCOPE_GLOBAL);
        }
        modelWrapper.eq(RuleModel::getStatus, 1);
        List<RuleModel> models = modelMapper.selectList(modelWrapper);
        for (RuleModel m : models) {
            String modelCode = trimToNull(m.getModelCode());
            if (modelCode != null && !map.containsKey(modelCode.toLowerCase())) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("id", m.getId());
                meta.put("varLabel", m.getModelName());
                meta.put("varType", "MODEL");
                meta.put("scriptName", modelCode);
                meta.put("varCode", modelCode);
                meta.put("varSource", "MODEL");
                meta.put("refType", "MODEL");
                map.put(modelCode.toLowerCase(), meta);
            }
        }
        appendModelOutputMeta(map, models);

        return map;
    }

    private void appendModelOutputMeta(Map<String, Map<String, Object>> map, List<RuleModel> models) {
        if (models == null || models.isEmpty() || modelOutputFieldMapper == null) {
            return;
        }
        Map<Long, String> modelCodeMap = new HashMap<>();
        Map<Long, String> modelNameMap = new HashMap<>();
        for (RuleModel model : models) {
            String modelCode = trimToNull(model.getModelCode());
            if (model.getId() != null && modelCode != null) {
                modelCodeMap.put(model.getId(), modelCode);
                modelNameMap.put(model.getId(), trimToNull(model.getModelName()));
            }
        }
        if (modelCodeMap.isEmpty()) {
            return;
        }
        List<RuleModelOutputField> fields = modelOutputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleModelOutputField>()
                        .in(RuleModelOutputField::getModelId, modelCodeMap.keySet())
                        .orderByAsc(RuleModelOutputField::getSortOrder)
                        .orderByAsc(RuleModelOutputField::getId));
        for (RuleModelOutputField field : fields) {
            String modelCode = modelCodeMap.get(field.getModelId());
            String outputScript = firstNonBlank(field.getScriptName(), field.getFieldName());
            if (modelCode == null || outputScript == null) {
                continue;
            }
            String scriptName = modelCode + "." + outputScript;
            if (map.containsKey(scriptName.toLowerCase())) {
                continue;
            }
            Map<String, Object> meta = new HashMap<>();
            meta.put("id", field.getId());
            meta.put("varLabel", firstNonBlank(modelNameMap.get(field.getModelId()), modelCode)
                    + "/" + firstNonBlank(field.getFieldLabel(), field.getFieldName(), outputScript));
            meta.put("varType", firstNonBlank(field.getFieldType(), "STRING"));
            meta.put("scriptName", scriptName);
            meta.put("varCode", outputScript);
            meta.put("varSource", "MODEL_OUTPUT");
            meta.put("refType", "MODEL_OUTPUT");
            map.put(scriptName.toLowerCase(), meta);
        }
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
            Map<String, Long> existingVarMap,
            Map<String, String> existingRefTypeMap) {
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
                field.setRefType(existingRefTypeMap.get(field.getScriptName()));
            }
            // 若无已有关联但 meta 中有 id，自动关联
            if (field.getVarId() == null) {
                Object id = meta.get("id");
                if (id instanceof Long) {
                    field.setVarId((Long) id);
                }
            }
            if (field.getRefType() == null) {
                field.setRefType((String) meta.get("refType"));
            }
        }
    }

    /**
     * 用变量元信息丰富输出字段。
     */
    private void enrichFieldFromMeta(RuleDefinitionOutputField field,
            Map<String, Map<String, Object>> varMetaMap,
            Map<String, Long> existingVarMap,
            Map<String, String> existingRefTypeMap) {
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
                field.setRefType(existingRefTypeMap.get(field.getScriptName()));
            }
            if (field.getVarId() == null) {
                Object id = meta.get("id");
                if (id instanceof Long) {
                    field.setVarId((Long) id);
                }
            }
            if (field.getRefType() == null) {
                field.setRefType((String) meta.get("refType"));
            }
        }
    }

    private List<RuleDefinitionInputField> expandModelInputFields(List<RuleDefinitionInputField> inputFields,
            Map<String, Map<String, Object>> varMetaMap) {
        List<RuleDefinitionInputField> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (RuleDefinitionInputField field : inputFields) {
            String refType = normalizeRefType(field.getRefType());
            RuleDefinitionInputField listDependency = buildListDependencyField(field, varMetaMap);
            if (listDependency != null) {
                enrichFieldFromMeta(listDependency, varMetaMap, Collections.emptyMap(), Collections.emptyMap());
                addInputFieldIfAbsent(result, seen, listDependency);
                continue;
            }
            if (!"MODEL".equals(refType) || field.getVarId() == null || modelInputFieldMapper == null) {
                addInputFieldIfAbsent(result, seen, field);
                continue;
            }

            List<RuleModelInputField> modelFields = modelInputFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleModelInputField>()
                            .eq(RuleModelInputField::getModelId, field.getVarId())
                            .and(w -> w.isNull(RuleModelInputField::getStatus).or().eq(RuleModelInputField::getStatus, 1))
                            .orderByAsc(RuleModelInputField::getSortOrder)
                            .orderByAsc(RuleModelInputField::getId));
            if (modelFields == null || modelFields.isEmpty()) {
                addInputFieldIfAbsent(result, seen, field);
                continue;
            }

            for (RuleModelInputField modelField : modelFields) {
                RuleDefinitionInputField expanded = copyModelInputField(modelField);
                enrichFieldFromMeta(expanded, varMetaMap, Collections.emptyMap(), Collections.emptyMap());
                addInputFieldIfAbsent(result, seen, expanded);
            }
        }
        return result;
    }

    private RuleDefinitionInputField buildListDependencyField(RuleDefinitionInputField field,
            Map<String, Map<String, Object>> varMetaMap) {
        Map<String, Object> meta = findFieldMeta(field, varMetaMap);
        if (meta == null || !"LIST".equals(meta.get("varSource"))) {
            return null;
        }
        JSONObject config = parseObject((String) meta.get("sourceConfig"));
        String queryField = firstNonBlank(config.getString("queryField"), config.getString("queryPath"), config.getString("field"));
        if (queryField == null) {
            return null;
        }
        if (queryField.startsWith("$.")) {
            queryField = queryField.substring(2);
        }
        RuleDefinitionInputField dependency = new RuleDefinitionInputField();
        dependency.setFieldName(firstNonBlank(config.getString("queryFieldName"), leafName(queryField), queryField));
        dependency.setFieldLabel(firstNonBlank(config.getString("queryFieldLabel"), dependency.getFieldName()));
        dependency.setScriptName(queryField);
        dependency.setFieldType(firstNonBlank(config.getString("queryFieldType"), "STRING"));
        dependency.setVarId(config.getLong("queryVarId"));
        dependency.setRefType(normalizeRefType(config.getString("queryRefType")));
        dependency.setStatus(1);
        dependency.setCreateTime(LocalDateTime.now());
        return dependency;
    }

    private Map<String, Object> findFieldMeta(RuleDefinitionInputField field, Map<String, Map<String, Object>> varMetaMap) {
        String scriptName = trimToNull(field.getScriptName());
        if (scriptName != null) {
            Map<String, Object> meta = varMetaMap.get(scriptName.toLowerCase());
            if (meta != null) {
                return meta;
            }
        }
        String fieldName = trimToNull(field.getFieldName());
        return fieldName != null ? varMetaMap.get(fieldName.toLowerCase()) : null;
    }

    private JSONObject parseObject(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(json);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private RuleDefinitionInputField copyModelInputField(RuleModelInputField modelField) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        String scriptName = firstNonBlank(modelField.getScriptName(), modelField.getFieldName());
        String displayName = firstNonBlank(modelField.getFieldName(), leafName(scriptName));
        field.setVarId(modelField.getVarId());
        field.setRefType(normalizeRefType(modelField.getRefType()));
        field.setFieldName(displayName);
        field.setFieldLabel(firstNonBlank(modelField.getFieldLabel(), displayName, scriptName));
        field.setScriptName(scriptName);
        field.setFieldType(firstNonBlank(modelField.getFieldType(), "STRING"));
        field.setMissingValue(modelField.getMissingValue());
        field.setDefaultValue(modelField.getDefaultValue());
        field.setValidValues(modelField.getValidValues());
        field.setTransformType(modelField.getTransformType());
        field.setTransformParams(modelField.getTransformParams());
        field.setStatus(1);
        field.setCreateTime(LocalDateTime.now());
        return field;
    }

    private void addInputFieldIfAbsent(List<RuleDefinitionInputField> fields, Set<String> seen, RuleDefinitionInputField field) {
        String key = normalizeRefType(field.getRefType()) + ":" + firstNonBlank(field.getScriptName(), field.getFieldName());
        if (key == null || key.endsWith(":null")) {
            key = firstNonBlank(field.getScriptName(), field.getFieldName());
        }
        if (key == null) return;
        String normalized = key.toLowerCase();
        if (seen.add(normalized)) {
            fields.add(field);
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

    private Map<String, String> getExistingRefTypeMap(Long definitionId, boolean isInput) {
        Map<String, String> map = new HashMap<>();
        if (isInput) {
            List<RuleDefinitionInputField> fields = inputFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDefinitionInputField>()
                            .eq(RuleDefinitionInputField::getDefinitionId, definitionId)
                            .isNotNull(RuleDefinitionInputField::getVarId));
            for (RuleDefinitionInputField f : fields) {
                if (f.getScriptName() != null && f.getVarId() != null) {
                    map.put(f.getScriptName(), normalizeRefType(f.getRefType()));
                }
            }
        } else {
            List<RuleDefinitionOutputField> fields = outputFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDefinitionOutputField>()
                            .eq(RuleDefinitionOutputField::getDefinitionId, definitionId)
                            .isNotNull(RuleDefinitionOutputField::getVarId));
            for (RuleDefinitionOutputField f : fields) {
                if (f.getScriptName() != null && f.getVarId() != null) {
                    map.put(f.getScriptName(), normalizeRefType(f.getRefType()));
                }
            }
        }
        return map;
    }

    private Map<String, FieldRef> collectExplicitRefs(String modelJson) {
        Map<String, FieldRef> refs = new HashMap<>();
        try {
            Object root = JSON.parse(modelJson);
            collectExplicitRefsRecursive(root, refs);
        } catch (Exception ignored) {
            // 字段提取后续会走原有解析异常路径，这里只作为引用信息补充。
        }
        return refs;
    }

    private void collectExplicitRefsRecursive(Object node, Map<String, FieldRef> refs) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            Long refId = obj.containsKey("_varId") ? obj.getLong("_varId") : null;
            if (refId == null && obj.containsKey("varId")) {
                refId = obj.getLong("varId");
            }
            String refType = normalizeRefType(obj.getString("_refType"));
            if (refType == null) {
                refType = normalizeRefType(obj.getString("refType"));
            }
            if (refId != null) {
                addExplicitRef(refs, obj.getString("varCode"), refId, refType);
                addExplicitRef(refs, obj.getString("refCode"), refId, refType);
                addExplicitRef(refs, obj.getString("condVar"), refId, refType);
                addExplicitRef(refs, obj.getString("target"), refId, refType);
                addExplicitRef(refs, obj.getString("matchVar"), refId, refType);
                addExplicitRef(refs, obj.getString("itemVar"), refId, refType);
                addExplicitRef(refs, obj.getString("checkVar"), refId, refType);
                addExplicitRef(refs, obj.getString("resultVar"), refId, refType);
            }
            addExplicitFieldRef(refs, obj, "target", "_targetVarId", "_targetRefType");
            addExplicitFieldRef(refs, obj, "condVar", "_condVarId", "_condVarRefType");
            addExplicitFieldRef(refs, obj, "matchVar", "_matchVarId", "_matchVarRefType");
            addExplicitFieldRef(refs, obj, "checkVar", "_checkVarId", "_checkVarRefType");
            addExplicitFieldRef(refs, obj, "value", "_rightVarId", "_rightRefType");
            for (Object value : obj.values()) {
                collectExplicitRefsRecursive(value, refs);
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (Object value : arr) {
                collectExplicitRefsRecursive(value, refs);
            }
        }
    }

    private void addExplicitFieldRef(Map<String, FieldRef> refs, JSONObject obj, String codeField,
                                     String idField, String refTypeField) {
        Long refId = obj.containsKey(idField) ? obj.getLong(idField) : null;
        if (refId == null) return;
        addExplicitRef(refs, obj.getString(codeField), refId, normalizeRefType(obj.getString(refTypeField)));
    }

    private void addExplicitRef(Map<String, FieldRef> refs, String code, Long refId, String refType) {
        String key = trimToNull(code);
        if (key != null && refId != null) {
            refs.put(key, new FieldRef(refId, refType));
            refs.put(key.toLowerCase(), new FieldRef(refId, refType));
        }
    }

    private void applyExplicitRef(RuleDefinitionInputField field, Map<String, FieldRef> explicitRefMap) {
        FieldRef ref = findExplicitRef(field, explicitRefMap);
        if (ref != null) {
            field.setVarId(ref.refId);
            field.setRefType(ref.refType);
        }
    }

    private void applyExplicitRef(RuleDefinitionOutputField field, Map<String, FieldRef> explicitRefMap) {
        FieldRef ref = findExplicitRef(field, explicitRefMap);
        if (ref != null) {
            field.setVarId(ref.refId);
            field.setRefType(ref.refType);
        }
    }

    private FieldRef findExplicitRef(RuleDefinitionInputField field, Map<String, FieldRef> explicitRefMap) {
        FieldRef ref = explicitRefMap.get(field.getScriptName());
        if (ref == null && field.getScriptName() != null) {
            ref = explicitRefMap.get(field.getScriptName().toLowerCase());
        }
        if (ref == null) ref = explicitRefMap.get(field.getFieldName());
        if (ref == null && field.getFieldName() != null) {
            ref = explicitRefMap.get(field.getFieldName().toLowerCase());
        }
        return ref;
    }

    private FieldRef findExplicitRef(RuleDefinitionOutputField field, Map<String, FieldRef> explicitRefMap) {
        FieldRef ref = explicitRefMap.get(field.getScriptName());
        if (ref == null && field.getScriptName() != null) {
            ref = explicitRefMap.get(field.getScriptName().toLowerCase());
        }
        if (ref == null) ref = explicitRefMap.get(field.getFieldName());
        if (ref == null && field.getFieldName() != null) {
            ref = explicitRefMap.get(field.getFieldName().toLowerCase());
        }
        return ref;
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
            case "RULE_SET":
                extractFromRuleSet(model, varCodes, true);
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
            case "RULE_SET":
                extractFromRuleSet(model, varCodes, false);
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
        if ("VAR".equals(getString(node, "valueKind"))) {
            String rightVarCode = getString(node, "value");
            if (rightVarCode != null && !rightVarCode.isEmpty()) {
                inputVars.add(rightVarCode);
            }
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

    // ==================== 规则集 ====================

    private void extractFromRuleSet(JSONObject model, Set<String> varCodes, boolean isInput) {
        JSONArray rules = model.getJSONArray("rules");
        if (rules == null) {
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            if (rule == null) {
                continue;
            }
            if (isInput) {
                JSONObject condRoot = rule.getJSONObject("conditionRoot");
                if (condRoot != null) {
                    collectVarCodesFromConditionTree(condRoot, varCodes);
                }
                JSONArray conditions = rule.getJSONArray("conditions");
                if (conditions != null) {
                    for (int j = 0; j < conditions.size(); j++) {
                        collectVarCodesFromConditionTree(conditions.getJSONObject(j), varCodes);
                    }
                }
            }
            collectActionDataVars(rule.getJSONArray("actionData"), varCodes, isInput);
        }
        if (isInput && !varCodes.isEmpty()) {
            Set<String> outputVars = new LinkedHashSet<>();
            extractFromRuleSet(model, outputVars, false);
            varCodes.removeAll(outputVars);
        }
    }

    // ==================== 决策树 / 决策流 ====================

    private void extractFromGraphModel(JSONObject model, Set<String> varCodes, boolean isInput) {
        JSONArray nodes = model.getJSONArray("nodes");
        if (nodes == null && model.containsKey("graph")) {
            nodes = model.getJSONObject("graph").getJSONArray("nodes");
        }
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                if (isInput) {
                    extractGraphInputVars(node, varCodes);
                } else {
                    extractGraphOutputVars(node, varCodes);
                }
            }
        }

        JSONArray edges = model.getJSONArray("edges");
        if (edges == null && model.containsKey("graph")) {
            edges = model.getJSONObject("graph").getJSONArray("edges");
        }
        if (edges != null) {
            for (int i = 0; i < edges.size(); i++) {
                JSONObject edge = edges.getJSONObject(i);
                if (isInput) {
                    extractGraphInputVars(edge, varCodes);
                }
            }
        }

        if (isInput && !varCodes.isEmpty()) {
            Set<String> outputVars = new LinkedHashSet<>();
            extractFromGraphModel(model, outputVars, false);
            varCodes.removeAll(outputVars);
        }
    }

    private void extractGraphInputVars(JSONObject obj, Set<String> varCodes) {
        if (obj == null) return;
        collectConditionRefs(obj, varCodes);
        JSONArray actionData = obj.getJSONArray("actionData");
        if (actionData != null) {
            collectActionDataVars(actionData, varCodes, true);
        }
        JSONObject data = obj.getJSONObject("data");
        if (data != null && data != obj) {
            extractGraphInputVars(data, varCodes);
        }
        JSONObject properties = obj.getJSONObject("properties");
        if (properties != null && properties != obj) {
            extractGraphInputVars(properties, varCodes);
        }
    }

    private void extractGraphOutputVars(JSONObject obj, Set<String> varCodes) {
        if (obj == null) return;
        String type = normalizeType(getString(obj, "type"));
        boolean mayContainOutput = type == null || type.contains("task") || type.contains("action");
        if (mayContainOutput) {
            addVarName(varCodes, getString(obj, "target"));
            addVarName(varCodes, getString(obj, "outputVar"));
            JSONObject resultVar = obj.getJSONObject("resultVar");
            if (resultVar != null) {
                addVarName(varCodes, getString(resultVar, "varCode"));
            }
            JSONArray actionData = obj.getJSONArray("actionData");
            if (actionData != null) {
                collectActionDataVars(actionData, varCodes, false);
            }
        }
        JSONObject data = obj.getJSONObject("data");
        if (data != null && data != obj) {
            extractGraphOutputVars(data, varCodes);
        }
        JSONObject properties = obj.getJSONObject("properties");
        if (properties != null && properties != obj) {
            extractGraphOutputVars(properties, varCodes);
        }
    }

    private void collectActionDataVars(JSONArray actionData, Set<String> varCodes, boolean isInput) {
        if (actionData == null) return;
        for (int i = 0; i < actionData.size(); i++) {
            Object item = actionData.get(i);
            if (item instanceof JSONObject) {
                collectActionBlockVars((JSONObject) item, varCodes, isInput);
            }
        }
    }

    private void collectActionBlockVars(JSONObject block, Set<String> varCodes, boolean isInput) {
        if (block == null) return;
        String type = getString(block, "type");
        if (!isInput) {
            addVarName(varCodes, getString(block, "target"));
            addVarName(varCodes, getString(block, "outputVar"));
            if (type == null || "action".equals(type)) {
                addVarName(varCodes, getString(block, "varCode"));
            }
        }

        if (isInput) {
            if ("assign".equals(type)) {
                extractIdentifiersFromExpression(getString(block, "value"), varCodes);
            } else if ("func-call".equals(type)) {
                JSONArray args = block.getJSONArray("args");
                if (args != null) {
                    for (int i = 0; i < args.size(); i++) {
                        extractIdentifiersFromExpression(args.getString(i), varCodes);
                    }
                }
            } else if ("foreach".equals(type)) {
                extractIdentifiersFromExpression(getString(block, "listExpr"), varCodes);
            } else if ("ternary".equals(type)) {
                addVarName(varCodes, getString(block, "condVar"));
                extractIdentifiersFromExpression(getString(block, "trueValue"), varCodes);
                extractIdentifiersFromExpression(getString(block, "falseValue"), varCodes);
            } else if ("in-check".equals(type)) {
                addVarName(varCodes, getString(block, "checkVar"));
            } else if ("template-str".equals(type)) {
                JSONArray parts = block.getJSONArray("parts");
                if (parts != null) {
                    for (int i = 0; i < parts.size(); i++) {
                        JSONObject part = parts.getJSONObject(i);
                        if ("expr".equals(getString(part, "type"))) {
                            extractIdentifiersFromExpression(getString(part, "content"), varCodes);
                        }
                    }
                }
            } else {
                collectConditionRefs(block, varCodes);
                extractIdentifiersFromExpression(getString(block, "value"), varCodes);
            }
        }

        if ("if-block".equals(type)) {
            JSONArray branches = block.getJSONArray("branches");
            if (branches != null) {
                for (int i = 0; i < branches.size(); i++) {
                    JSONObject branch = branches.getJSONObject(i);
                    if (isInput) {
                        addVarName(varCodes, getString(branch, "condVar"));
                        extractIdentifiersFromExpression(getString(branch, "condition"), varCodes);
                    }
                    collectActionDataVars(branch.getJSONArray("actions"), varCodes, isInput);
                }
            }
        } else if ("switch-block".equals(type)) {
            if (isInput) {
                addVarName(varCodes, getString(block, "matchVar"));
            }
            JSONArray cases = block.getJSONArray("cases");
            if (cases != null) {
                for (int i = 0; i < cases.size(); i++) {
                    collectActionDataVars(cases.getJSONObject(i).getJSONArray("actions"), varCodes, isInput);
                }
            }
            collectActionDataVars(block.getJSONArray("defaultActions"), varCodes, isInput);
        } else if ("foreach".equals(type)) {
            collectActionDataVars(block.getJSONArray("actions"), varCodes, isInput);
        }
    }

    private void collectConditionRefs(JSONObject obj, Set<String> varCodes) {
        if (obj == null) return;
        addVarName(varCodes, getString(obj, "varCode"));
        addVarName(varCodes, getString(obj, "condVar"));
        addVarName(varCodes, getString(obj, "leftVar"));
        addVarName(varCodes, getString(obj, "matchVar"));
        addVarName(varCodes, getString(obj, "checkVar"));

        JSONObject condVar = obj.getJSONObject("condVar");
        if (condVar != null) {
            addVarName(varCodes, getString(condVar, "varCode"));
        }
        JSONObject left = obj.getJSONObject("left");
        if (left != null) {
            collectConditionRefs(left, varCodes);
        }
        JSONObject right = obj.getJSONObject("right");
        if (right != null) {
            collectConditionRefs(right, varCodes);
        }
        JSONObject conditionRoot = obj.getJSONObject("conditionRoot");
        if (conditionRoot != null) {
            collectVarCodesFromConditionTree(conditionRoot, varCodes);
        }
        JSONObject conditionConfig = obj.getJSONObject("conditionConfig");
        if (conditionConfig != null) {
            collectVarCodesFromConditionTree(conditionConfig, varCodes);
        }
        String[] exprKeys = { "condition", "conditionExpression", "expression", "leftExpr", "rightExpr" };
        for (String key : exprKeys) {
            String expr = getString(obj, key);
            if (expr != null && !expr.isEmpty()) {
                extractIdentifiersFromExpression(expr, varCodes);
            }
        }
        JSONArray children = obj.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                Object child = children.get(i);
                if (child instanceof JSONObject) {
                    collectConditionRefs((JSONObject) child, varCodes);
                }
            }
        }
    }

    private void extractVarCodesFromConditionString(String condition, Set<String> varCodes) {
        if (condition == null || condition.isEmpty()) return;
        extractIdentifiersFromExpression(condition, varCodes);
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

        Set<String> assignedVars = collectAssignedVars(script);
        Set<String> explicitResultKeys = collectExplicitResultKeys(script);
        if (!isInput && !explicitResultKeys.isEmpty()) {
            varCodes.addAll(explicitResultKeys);
            return;
        }
        Set<String> explicitScriptRefs = collectScriptRefCodes(model);
        if (!explicitScriptRefs.isEmpty()) {
            Set<String> usedRefs = new LinkedHashSet<>();
            for (String ref : explicitScriptRefs) {
                if (scriptContainsIdentifier(script, ref)) {
                    usedRefs.add(ref);
                }
            }
            if (isInput) {
                for (String ref : usedRefs) {
                    if (!assignedVars.contains(ref)) {
                        addVarName(varCodes, ref);
                    }
                }
            } else {
                for (String ref : usedRefs) {
                    if (assignedVars.contains(ref)) {
                        addVarName(varCodes, ref);
                    }
                }
            }
        }

        if (isInput) {
            Set<String> identifiers = extractIdentifiersFromScript(script);
            identifiers.removeAll(assignedVars);
            varCodes.addAll(identifiers);
        } else {
            for (String assignedVar : assignedVars) {
                if (!"_result".equals(assignedVar)) {
                    varCodes.add(assignedVar);
                }
            }
        }
    }

    private Set<String> collectAssignedVars(String script) {
        Set<String> assignedVars = new LinkedHashSet<>();
        String[] lines = script.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;

            int eqIdx = findAssignmentIndex(line);
            if (eqIdx <= 0) continue;
            String left = extractAssignmentTarget(line.substring(0, eqIdx));
            if (isValidVarName(left)) {
                assignedVars.add(left);
            }
        }
        return assignedVars;
    }

    private Set<String> collectScriptRefCodes(JSONObject model) {
        Set<String> refs = new LinkedHashSet<>();
        JSONArray scriptVarRefs = model.getJSONArray("scriptVarRefs");
        if (scriptVarRefs == null) return refs;
        for (int i = 0; i < scriptVarRefs.size(); i++) {
            JSONObject ref = scriptVarRefs.getJSONObject(i);
            addVarName(refs, getString(ref, "refCode"));
        }
        return refs;
    }

    private Set<String> collectExplicitResultKeys(String script) {
        Set<String> keys = new LinkedHashSet<>();
        String sanitized = stripComments(script);
        Pattern resultPattern = Pattern.compile("_result\\s*=\\s*\\{([\\s\\S]*?)\\}");
        Matcher resultMatcher = resultPattern.matcher(sanitized);
        while (resultMatcher.find()) {
            String body = resultMatcher.group(1);
            Matcher keyMatcher = Pattern.compile("\"([^\"]+)\"\\s*:|'([^']+)'\\s*:|([A-Za-z_][A-Za-z0-9_.]*)\\s*:").matcher(body);
            while (keyMatcher.find()) {
                String key = firstNonBlank(keyMatcher.group(1), keyMatcher.group(2), keyMatcher.group(3));
                addVarName(keys, key);
            }
        }
        return keys;
    }

    private Set<String> extractIdentifiersFromScript(String script) {
        Set<String> identifiers = new LinkedHashSet<>();
        String sanitized = stripCommentsAndStrings(script);
        Matcher matcher = IDENTIFIER_PATTERN.matcher(sanitized);
        while (matcher.find()) {
            String token = matcher.group();
            if (isScriptKeyword(token)) continue;
            if (isFunctionCall(sanitized, matcher.end())) continue;
            if (token.startsWith("java.") || token.startsWith("com.") || token.startsWith("org.")) continue;
            addVarName(identifiers, token);
        }
        return identifiers;
    }

    private void extractIdentifiersFromExpression(String expr, Set<String> varCodes) {
        if (expr == null || expr.isEmpty()) return;
        Set<String> identifiers = extractIdentifiersFromScript(expr);
        varCodes.addAll(identifiers);
    }

    private boolean scriptContainsIdentifier(String script, String identifier) {
        if (script == null || identifier == null || identifier.isEmpty()) return false;
        String sanitized = stripCommentsAndStrings(script);
        Matcher matcher = IDENTIFIER_PATTERN.matcher(sanitized);
        while (matcher.find()) {
            if (identifier.equals(matcher.group())) return true;
        }
        return false;
    }

    private boolean isFunctionCall(String source, int end) {
        int i = end;
        while (i < source.length() && Character.isWhitespace(source.charAt(i))) i++;
        return i < source.length() && source.charAt(i) == '(';
    }

    private boolean isScriptKeyword(String token) {
        if (token == null) return true;
        return SCRIPT_KEYWORDS.contains(token.toLowerCase());
    }

    private String stripCommentsAndStrings(String script) {
        StringBuilder sb = new StringBuilder(script.length());
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : '\0';
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    sb.append(c);
                } else {
                    sb.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    sb.append("  ");
                    i++;
                } else {
                    sb.append(c == '\n' ? '\n' : ' ');
                }
                continue;
            }
            if (!inSingle && !inDouble && c == '/' && next == '/') {
                inLineComment = true;
                sb.append("  ");
                i++;
                continue;
            }
            if (!inSingle && !inDouble && c == '/' && next == '*') {
                inBlockComment = true;
                sb.append("  ");
                i++;
                continue;
            }
            if (!inDouble && c == '\'' && !isEscaped(script, i)) {
                inSingle = !inSingle;
                sb.append(' ');
                continue;
            }
            if (!inSingle && c == '"' && !isEscaped(script, i)) {
                inDouble = !inDouble;
                sb.append(' ');
                continue;
            }
            sb.append(inSingle || inDouble ? (c == '\n' ? '\n' : ' ') : c);
        }
        return sb.toString();
    }

    private String stripComments(String script) {
        StringBuilder sb = new StringBuilder(script.length());
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : '\0';
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    sb.append(c);
                } else {
                    sb.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    sb.append("  ");
                    i++;
                } else {
                    sb.append(c == '\n' ? '\n' : ' ');
                }
                continue;
            }
            if (!inSingle && !inDouble && c == '/' && next == '/') {
                inLineComment = true;
                sb.append("  ");
                i++;
                continue;
            }
            if (!inSingle && !inDouble && c == '/' && next == '*') {
                inBlockComment = true;
                sb.append("  ");
                i++;
                continue;
            }
            if (!inDouble && c == '\'' && !isEscaped(script, i)) {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"' && !isEscaped(script, i)) {
                inDouble = !inDouble;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private boolean isEscaped(String text, int index) {
        int slashCount = 0;
        for (int i = index - 1; i >= 0 && text.charAt(i) == '\\'; i--) {
            slashCount++;
        }
        return slashCount % 2 == 1;
    }

    private int findAssignmentIndex(String line) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (!inDouble && c == '\'' && !isEscaped(line, i)) {
                inSingle = !inSingle;
                continue;
            }
            if (!inSingle && c == '"' && !isEscaped(line, i)) {
                inDouble = !inDouble;
                continue;
            }
            if (inSingle || inDouble || c != '=') continue;
            char prev = i > 0 ? line.charAt(i - 1) : '\0';
            char next = i + 1 < line.length() ? line.charAt(i + 1) : '\0';
            if (prev == '=' || prev == '!' || prev == '<' || prev == '>' || next == '=' || next == '>') {
                continue;
            }
            return i;
        }
        return -1;
    }

    private String extractAssignmentTarget(String left) {
        String target = trimToNull(left);
        if (target == null) return null;
        int parenIdx = target.indexOf('(');
        if (parenIdx > 0) {
            target = target.substring(0, parenIdx).trim();
        }
        int spaceIdx = target.lastIndexOf(' ');
        if (spaceIdx >= 0) {
            target = target.substring(spaceIdx + 1).trim();
        }
        return target;
    }

    private void addVarName(Set<String> varCodes, String value) {
        String name = trimToNull(value);
        if (name != null && isValidVarName(name) && !isScriptKeyword(name)) {
            varCodes.add(name);
        }
    }

    private String normalizeType(String type) {
        String t = trimToNull(type);
        return t == null ? null : t.toLowerCase();
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

    private Map<Long, RuleDataObject> buildObjectMap(Long projectId) {
        LambdaQueryWrapper<RuleDataObject> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null && projectId > 0) {
            wrapper.and(w -> w.eq(RuleDataObject::getScope, RuleVariableService.SCOPE_GLOBAL)
                    .or()
                    .eq(RuleDataObject::getScope, RuleVariableService.SCOPE_PROJECT)
                    .eq(RuleDataObject::getProjectId, projectId));
        } else {
            wrapper.eq(RuleDataObject::getScope, RuleVariableService.SCOPE_GLOBAL);
        }
        wrapper.eq(RuleDataObject::getStatus, 1);
        Map<Long, RuleDataObject> map = new HashMap<>();
        for (RuleDataObject object : dataObjectMapper.selectList(wrapper)) {
            if (object.getId() != null) {
                map.put(object.getId(), object);
            }
        }
        return map;
    }

    private String buildObjectFieldScriptName(RuleDataObjectField field, Map<Long, RuleDataObject> objectMap) {
        String fieldScript = trimToNull(field.getScriptName());
        if (fieldScript == null) {
            fieldScript = trimToNull(field.getVarCode());
        }
        if (fieldScript == null) {
            return null;
        }
        RuleDataObject object = objectMap.get(field.getObjectId());
        String objectScript = object != null ? trimToNull(object.getScriptName()) : null;
        if (objectScript == null && object != null) {
            objectScript = trimToNull(object.getObjectCode());
        }
        if (objectScript == null || fieldScript.equals(objectScript) || fieldScript.startsWith(objectScript + ".")) {
            return fieldScript;
        }
        return objectScript + "." + fieldScript;
    }

    private String normalizeRefType(String refType) {
        String type = trimToNull(refType);
        return type != null ? type.toUpperCase() : null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String leafName(String path) {
        String value = trimToNull(path);
        if (value == null) {
            return null;
        }
        int idx = value.lastIndexOf('.');
        return idx >= 0 ? value.substring(idx + 1) : value;
    }

    private static class FieldRef {
        private final Long refId;
        private final String refType;

        private FieldRef(Long refId, String refType) {
            this.refId = refId;
            this.refType = refType;
        }
    }
}
