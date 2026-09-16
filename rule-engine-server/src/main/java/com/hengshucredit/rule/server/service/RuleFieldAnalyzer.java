package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.script.QLScriptAnalysis;
import com.hengshucredit.rule.core.script.QLScriptAnalyzer;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleModelInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.hengshucredit.rule.server.artifact.PublishedRuleFieldSnapshotResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hengshucredit.rule.server.service.VariableSourceResolver;

import jakarta.annotation.Resource;
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

    private static final Pattern EXPRESSION_IDENTIFIER_PATTERN =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*");
    private static final Set<String> EXPRESSION_KEYWORDS = new HashSet<>(Arrays.asList(
            "if", "else", "for", "while", "switch", "case", "default", "break", "continue", "return",
            "true", "false", "null", "and", "or", "in", "new", "var", "let", "const", "do",
            "try", "catch", "finally", "throw", "class", "import", "package"
    ));

    @Resource
    private RuleDefinitionInputFieldMapper inputFieldMapper;

    @Resource
    private RuleDefinitionOutputFieldMapper outputFieldMapper;
    @Resource
    private PublishedRuleFieldSnapshotResolver publishedFieldSnapshotResolver;

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

    @Resource
    private VariableSourceResolver variableSourceResolver;

    @Resource
    private RuleExternalApiConfigMapper externalApiConfigMapper;

    @Resource
    private RuleExternalDatasourceMapper externalDatasourceMapper;

    @Resource
    private QLScriptFieldResolver qlScriptFieldResolver;

    @Resource
    private DataObjectSchemaResolver dataObjectSchemaResolver;

    /**
     * 解析模型内容，提取输入/输出变量，持久化到字段表。
     * 写入字段时，优先通过 projectId 从变量管理表（rule_variable / rule_data_object_field）
     * 仅通过 varId + refType 查询真实元信息（varLabel / varType / scriptName）。
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

        ResolvedFields resolvedFields = resolveFields(definitionId, modelJson, modelType, projectId);
        persistResolvedFields(definitionId, resolvedFields);
    }

    /**
     * 持久化已经解析完成的字段投影，不重新解析或按名称回绑引用。
     */
    @Transactional
    public void persistResolvedFields(Long definitionId, ResolvedFields resolvedFields) {
        if (definitionId == null || resolvedFields == null) {
            throw new IllegalArgumentException("definitionId 和 resolvedFields 不能为空");
        }
        List<RuleDefinitionInputField> preparedInputFields = resolvedFields.getInputFields();
        List<RuleDefinitionOutputField> preparedOutputFields = resolvedFields.getOutputFields();

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
            if (inputFieldMapper.insert(field) != 1) {
                throw new IllegalStateException(
                        "规则输入字段投影写入失败");
            }
        }

        int outputOrder = 0;
        for (RuleDefinitionOutputField field : preparedOutputFields) {
            field.setDefinitionId(definitionId);
            field.setSortOrder(outputOrder++);
            field.setStatus(1);
            field.setCreateTime(LocalDateTime.now());
            if (outputFieldMapper.insert(field) != 1) {
                throw new IllegalStateException(
                        "规则输出字段投影写入失败");
            }
        }
    }

    /** 使用与持久化完全相同的规则解析字段，但不写数据库。 */
    public ResolvedFields resolveFields(Long definitionId, String modelJson, String modelType, Long projectId) {
        if ("SCRIPT".equalsIgnoreCase(modelType)) {
            return resolveScriptFields(definitionId, modelJson, projectId);
        }
        List<RuleDefinitionInputField> inputFields = extractInputFields(modelJson, modelType);
        List<RuleDefinitionOutputField> outputFields = extractOutputFields(modelJson, modelType);
        Map<String, Long> existingInputVarMap = definitionId == null
                ? Collections.emptyMap() : getExistingVarIdMap(definitionId, true);
        Map<String, Long> existingOutputVarMap = definitionId == null
                ? Collections.emptyMap() : getExistingVarIdMap(definitionId, false);
        Map<String, String> existingInputRefTypeMap = definitionId == null
                ? Collections.emptyMap() : getExistingRefTypeMap(definitionId, true);
        Map<String, String> existingOutputRefTypeMap = definitionId == null
                ? Collections.emptyMap() : getExistingRefTypeMap(definitionId, false);
        Map<String, RuleDefinitionInputField> existingInputFieldMap = definitionId == null
                ? Collections.emptyMap() : getExistingInputFieldMap(definitionId);
        Map<String, FieldRef> explicitRefMap = collectExplicitRefs(modelJson);
        Map<String, Map<String, Object>> varMetaMap = buildVarMetaMap(projectId);
        ResolvedFields ruleCallFields = resolveRuleCallFields(definitionId, modelJson);

        List<RuleDefinitionInputField> preparedInputFields = new ArrayList<>();
        for (RuleDefinitionInputField field : inputFields) {
            applyExplicitRef(field, explicitRefMap);
            enrichFieldFromMeta(field, varMetaMap, existingInputVarMap, existingInputRefTypeMap);
            preparedInputFields.add(field);
        }
        preparedInputFields.addAll(ruleCallFields.getInputFields());

        List<RuleDefinitionOutputField> preparedOutputFields = new ArrayList<>();
        Set<RuleDefinitionOutputField> localRuleCallOutputs =
                Collections.newSetFromMap(new IdentityHashMap<>());
        for (RuleDefinitionOutputField field : outputFields) {
            applyExplicitRef(field, explicitRefMap);
            enrichFieldFromMeta(field, varMetaMap, existingOutputVarMap, existingOutputRefTypeMap);
            preparedOutputFields.add(field);
        }
        for (RuleDefinitionOutputField field : ruleCallFields.getOutputFields()) {
            preparedOutputFields.add(field);
            if (ruleCallFields.isLocalOutput(field)) {
                localRuleCallOutputs.add(field);
            }
        }
        preparedOutputFields = deduplicateOutputFields(preparedOutputFields);
        Set<String> retainedLocalOutputs =
                retainedLocalOutputNames(preparedOutputFields, localRuleCallOutputs);
        List<RuleDefinitionInputField> runtimeSourceFields =
                runtimeSourceFields(preparedInputFields, varMetaMap);
        preparedInputFields = expandModelInputFields(preparedInputFields, varMetaMap);
        preparedInputFields = mergeRuntimeSourceFields(
                preparedInputFields, runtimeSourceFields);
        preparedInputFields = removeOutputFields(preparedInputFields, preparedOutputFields);
        for (RuleDefinitionInputField field : preparedInputFields) {
            RuleDefinitionInputField existing = existingInputFieldMap.get(inputFieldKey(field));
            if (existing != null && Integer.valueOf(1).equals(existing.getValidationOverride())) {
                field.setValidationRuleIds(normalizeValidationRuleIds(existing.getValidationRuleIds()));
                field.setValidationOverride(1);
            } else if (field.getValidationOverride() == null) {
                field.setValidationOverride(0);
            }
        }
        return new ResolvedFields(
                preparedInputFields, preparedOutputFields,
                ruleCallFields.getDiagnostics(), retainedLocalOutputs,
                ruleCallFields.getInputPropertySchemas(),
                ruleCallFields.getOutputPropertySchemas());
    }

    private ResolvedFields resolveRuleCallFields(Long definitionId, String modelJson) {
        Map<String, OperandDependencyCollector.Reference> references = new LinkedHashMap<>();
        for (OperandDependencyCollector.Reference reference : OperandDependencyCollector.collectReferences(parseObject(modelJson))) {
            if ("RULE".equals(reference.getRefType()) && reference.getRefId() != null)
                references.put(reference.getRefId() + ":" + reference.getVersionBindingId(), reference);
        }
        boolean requiresVersionSnapshot = parseObject(modelJson).getString("script") != null
                || references.values().stream().anyMatch(ref -> "FIXED".equals(ref.getVersionMode()));
        if (definitionId != null && !requiresVersionSnapshot) {
            return new ResolvedFields(
                    loadRuleCallInputFields(modelJson),
                    loadRuleCallOutputFields(modelJson));
        }
        List<RuleDefinitionInputField> inputs = new ArrayList<>();
        List<RuleDefinitionOutputField> outputs = new ArrayList<>();
        List<RuleValidationIssue> diagnostics = new ArrayList<>();
        Set<RuleDefinitionOutputField> localCandidates =
                Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, Object> inputSchemas = new LinkedHashMap<>();
        Map<String, Object> outputSchemas = new LinkedHashMap<>();
        for (OperandDependencyCollector.Reference reference : references.values()) {
            Long ruleId = reference.getRefId();
            ResolvedFields fields;
            if (publishedFieldSnapshotResolver == null) {
                RuleValidationIssue issue = RuleValidationIssue.error(
                        "FROZEN_REVISION_FIELD_SNAPSHOT_MISSING", "$.ruleFields",
                        "RULE", ruleId, "已发布规则字段快照解析器不可用");
                fields = new ResolvedFields(
                        Collections.emptyList(), Collections.emptyList(),
                        Collections.singletonList(issue), Collections.emptySet(),
                        Collections.emptyMap(), Collections.emptyMap());
            } else {
                fields = "FIXED".equals(reference.getVersionMode())
                        ? publishedFieldSnapshotResolver.resolve(ruleId, reference.getVersionBindingId())
                        : publishedFieldSnapshotResolver.resolve(ruleId);
            }
            inputs.addAll(fields.getInputFields());
            for (RuleDefinitionOutputField output : fields.getOutputFields()) {
                outputs.add(output);
                if (fields.isLocalOutput(output)) {
                    localCandidates.add(output);
                }
            }
            diagnostics.addAll(fields.getDiagnostics());
            inputSchemas.putAll(fields.getInputPropertySchemas());
            outputSchemas.putAll(fields.getOutputPropertySchemas());
        }
        outputs = deduplicateOutputFields(outputs);
        Set<String> localOutputs = retainedLocalOutputNames(outputs, localCandidates);
        return new ResolvedFields(inputs, outputs, diagnostics, localOutputs,
                inputSchemas, outputSchemas);
    }

    private ResolvedFields resolveScriptFields(Long definitionId, String modelJson, Long projectId) {
        ResolvedFields scriptFields = qlScriptFieldResolver.resolve(modelJson, projectId);
        ResolvedFields calledFields = resolveRuleCallFields(definitionId, modelJson);
        if (!calledFields.getInputFields().isEmpty() || !calledFields.getOutputFields().isEmpty() || !calledFields.getDiagnostics().isEmpty()) {
            List<RuleDefinitionInputField> inputs = new ArrayList<>(scriptFields.getInputFields()); inputs.addAll(calledFields.getInputFields());
            List<RuleDefinitionOutputField> outputs = new ArrayList<>(scriptFields.getOutputFields()); outputs.addAll(calledFields.getOutputFields());
            List<RuleValidationIssue> issues = new ArrayList<>(scriptFields.getDiagnostics()); issues.addAll(calledFields.getDiagnostics());
            Set<String> localNames = new LinkedHashSet<>(scriptFields.getLocalOutputNames()); localNames.addAll(calledFields.getLocalOutputNames());
            Map<String, Object> inputSchemas = new LinkedHashMap<>(scriptFields.getInputPropertySchemas()); inputSchemas.putAll(calledFields.getInputPropertySchemas());
            Map<String, Object> outputSchemas = new LinkedHashMap<>(scriptFields.getOutputPropertySchemas()); outputSchemas.putAll(calledFields.getOutputPropertySchemas());
            scriptFields = new ResolvedFields(inputs, outputs, issues, localNames, inputSchemas, outputSchemas);
        }
        QLScriptAnalysis scriptAnalysis = new QLScriptAnalyzer()
                .analyze(parseObject(modelJson).getString("script"));
        List<RuleValidationIssue> diagnostics =
                new ArrayList<>(scriptFields.getDiagnostics());
        Map<String, Object> inputPropertySchemas =
                new LinkedHashMap<>(scriptFields.getInputPropertySchemas());
        Map<String, Object> outputPropertySchemas =
                new LinkedHashMap<>(scriptFields.getOutputPropertySchemas());
        Map<String, String> verifiedInputTypes = resolveScriptInputShapes(
                scriptFields.getInputFields(), projectId,
                diagnostics, inputPropertySchemas);
        Map<String, Map<String, Object>> varMetaMap = buildVarMetaMap(projectId);
        List<RuleDefinitionInputField> runtimeSourceFields =
                runtimeSourceFields(scriptFields.getInputFields(), varMetaMap);
        List<RuleDefinitionInputField> preparedInputFields =
                expandStableScriptInputFields(scriptFields.getInputFields(), varMetaMap);
        preparedInputFields = mergeRuntimeSourceFields(
                preparedInputFields, runtimeSourceFields);
        List<RuleDefinitionOutputField> preparedOutputFields =
                deduplicateOutputFields(new ArrayList<>(scriptFields.getOutputFields()));
        Map<String, RuleDefinitionInputField> existingInputFieldMap = definitionId == null
                ? Collections.emptyMap() : getExistingInputFieldMap(definitionId);
        for (RuleDefinitionInputField field : preparedInputFields) {
            RuleDefinitionInputField existing = existingInputFieldMap.get(inputFieldKey(field));
            if (existing != null && Integer.valueOf(1).equals(existing.getValidationOverride())) {
                field.setValidationRuleIds(normalizeValidationRuleIds(existing.getValidationRuleIds()));
                field.setValidationOverride(1);
            } else if (field.getValidationOverride() == null) {
                field.setValidationOverride(0);
            }
        }
        addStableInputFieldSchemas(preparedInputFields, projectId,
                diagnostics, inputPropertySchemas);
        applyScriptOutputTypes(scriptAnalysis, preparedOutputFields,
                verifiedInputTypes, outputPropertySchemas);
        addStableOutputFieldSchemas(preparedOutputFields, projectId,
                diagnostics, outputPropertySchemas);
        return new ResolvedFields(
                preparedInputFields, preparedOutputFields,
                diagnostics, scriptFields.getLocalOutputNames(),
                inputPropertySchemas, outputPropertySchemas);
    }

    private Map<String, String> resolveScriptInputShapes(
            List<RuleDefinitionInputField> inputFields,
            Long projectId,
            List<RuleValidationIssue> diagnostics,
            Map<String, Object> inputPropertySchemas) {
        Map<String, String> verifiedTypes = new LinkedHashMap<>();
        Map<Long, ObjectShapeIndex> variableShapeCache = new HashMap<>();
        for (RuleDefinitionInputField field : inputFields) {
            String inputPath = trimToNull(field.getScriptName());
            String refType = normalizeRefType(field.getRefType());
            if (inputPath == null || field.getVarId() == null || refType == null) {
                continue;
            }
            if ("DATA_OBJECT".equals(refType)) {
                if (dataObjectSchemaResolver == null) {
                    continue;
                }
                Map<String, Object> schema = resolveDataObjectInputSchema(
                        field, projectId, diagnostics);
                if (schema == null) {
                    field.setFieldType(null);
                    inputPropertySchemas.remove(inputPath);
                    continue;
                }
                inputPropertySchemas.put(inputPath, schema);
                String type = shapeType(schema);
                if (type != null) {
                    verifiedTypes.put(normalizeIndexedPath(inputPath), type);
                    field.setFieldType(type);
                }
                continue;
            }
            if (!"VARIABLE".equals(refType) && !"CONSTANT".equals(refType)) {
                String type = trimToNull(field.getFieldType());
                if (type != null) {
                    verifiedTypes.put(normalizeIndexedPath(inputPath), type);
                    inputPropertySchemas.put(inputPath, propertySchema(type));
                }
                continue;
            }

            RuleVariable variable = ruleVariableMapper == null
                    ? null : ruleVariableMapper.selectById(field.getVarId());
            if (variable == null) {
                continue;
            }
            String scriptRoot = firstNonBlank(variable.getScriptName(), variable.getVarCode());
            String relativePath = relativePath(inputPath, scriptRoot);
            String variableType = trimToNull(variable.getVarType());
            if (relativePath == null) {
                continue;
            }
            if (relativePath.isEmpty()
                    && !isObjectOrList(variableType)) {
                field.setFieldType(variableType);
                verifiedTypes.put(normalizeIndexedPath(inputPath), variableType);
                inputPropertySchemas.put(inputPath, propertySchema(variableType));
                continue;
            }
            if (!isObjectOrList(variableType)) {
                addMissingDescendantDiagnostic(diagnostics, inputPath);
                field.setFieldType(null);
                inputPropertySchemas.remove(inputPath);
                continue;
            }

            ObjectShapeIndex shape = variableShapeCache.computeIfAbsent(
                    field.getVarId(),
                    ignored -> loadApiVariableShape(variable, projectId, diagnostics));
            if (relativePath.isEmpty()) {
                inputPropertySchemas.put(inputPath, shape.schema);
                verifiedTypes.put(normalizeIndexedPath(inputPath), variableType);
                field.setFieldType(variableType);
                continue;
            }
            String normalizedRelative = normalizeIndexedPath(relativePath);
            String leafType = shape.leafTypes.get(normalizedRelative);
            Map<String, Object> leafSchema = shape.schemasByPath.get(normalizedRelative);
            if (leafType == null || leafSchema == null) {
                addMissingDescendantDiagnostic(diagnostics, inputPath);
                field.setFieldType(null);
                inputPropertySchemas.remove(inputPath);
                continue;
            }
            field.setFieldType(leafType);
            verifiedTypes.put(normalizeIndexedPath(inputPath), leafType);
            inputPropertySchemas.put(inputPath, leafSchema);
        }
        return verifiedTypes;
    }

    private ObjectShapeIndex loadApiVariableShape(
            RuleVariable variable,
            Long projectId,
            List<RuleValidationIssue> diagnostics) {
        String scriptRoot = firstNonBlank(variable.getScriptName(), variable.getVarCode());
        if (!"API".equalsIgnoreCase(trimToNull(variable.getVarSource()))) {
            addOpenShapeDiagnostic(diagnostics, scriptRoot,
                    "对象变量缺少可由稳定 ID 证明的结构来源");
            return ObjectShapeIndex.open();
        }
        Long apiConfigId = parseObject(variable.getSourceConfig()).getLong("apiConfigId");
        if (apiConfigId == null || externalApiConfigMapper == null) {
            addApiChainDiagnostic(diagnostics, "REFERENCE_NOT_FOUND",
                    "$.apiConfig." + apiConfigId,
                    "API 对象变量缺少可用的稳定 apiConfigId");
            return ObjectShapeIndex.open();
        }
        RuleExternalApiConfig apiConfig = apiConfigId == null
                ? null : externalApiConfigMapper.selectById(apiConfigId);
        if (apiConfig == null) {
            addApiChainDiagnostic(diagnostics, "REFERENCE_NOT_FOUND",
                    "$.apiConfig." + apiConfigId, "API 配置不存在");
            return ObjectShapeIndex.open();
        }
        if (!Integer.valueOf(1).equals(apiConfig.getStatus())) {
            addApiChainDiagnostic(diagnostics, "REFERENCE_TYPE_MISMATCH",
                    "$.apiConfig." + apiConfigId, "API 配置未启用");
            return ObjectShapeIndex.open();
        }
        Long datasourceId = apiConfig.getDatasourceId();
        RuleExternalDatasource datasource =
                datasourceId == null || externalDatasourceMapper == null
                        ? null : externalDatasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            addApiChainDiagnostic(diagnostics, "REFERENCE_NOT_FOUND",
                    "$.datasource." + datasourceId, "API 所属外部数据源不存在");
            return ObjectShapeIndex.open();
        }
        if (!Integer.valueOf(1).equals(datasource.getStatus())
                || !resourceAvailable(
                datasource.getScope(), datasource.getProjectId(), projectId)) {
            addApiChainDiagnostic(diagnostics, "REFERENCE_TYPE_MISMATCH",
                    "$.datasource." + datasourceId,
                    "API 所属外部数据源未启用或不属于当前项目");
            return ObjectShapeIndex.open();
        }
        if (apiConfig.getResponseObjectId() == null) {
            addApiChainDiagnostic(diagnostics, "REFERENCE_NOT_FOUND",
                    "$.apiConfig." + apiConfigId,
                    "API 配置缺少稳定 responseObjectId");
            return ObjectShapeIndex.open();
        }
        return loadObjectShape(apiConfig.getResponseObjectId(), projectId,
                scriptRoot, diagnostics);
    }

    private ObjectShapeIndex loadObjectShape(
            Long objectId,
            Long projectId,
            String diagnosticPath,
            List<RuleValidationIssue> diagnostics) {
        if (dataObjectMapper == null || dataObjectFieldMapper == null
                || dataObjectSchemaResolver == null) {
            addOpenShapeDiagnostic(diagnostics, diagnosticPath,
                    "数据对象 Schema 解析器不可用");
            return ObjectShapeIndex.open();
        }
        RuleDataObject object = dataObjectMapper.selectById(objectId);
        if (object == null || !Integer.valueOf(1).equals(object.getStatus())
                || !resourceAvailable(object.getScope(), object.getProjectId(), projectId)) {
            addOpenShapeDiagnostic(diagnostics, diagnosticPath,
                    "API 响应对象不存在、未启用或不属于当前项目");
            return ObjectShapeIndex.open();
        }
        List<RuleDataObjectField> selected = dataObjectFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDataObjectField>()
                        .eq(RuleDataObjectField::getObjectId, objectId)
                        .eq(RuleDataObjectField::getStatus, 1)
                        .orderByAsc(RuleDataObjectField::getSortOrder)
                        .orderByAsc(RuleDataObjectField::getId));
        List<RuleDataObjectField> roots = new ArrayList<>();
        for (RuleDataObjectField field : selected == null
                ? Collections.<RuleDataObjectField>emptyList() : selected) {
            if (Objects.equals(objectId, field.getObjectId())
                    && field.getParentFieldId() == null
                    && Integer.valueOf(1).equals(field.getStatus())
                    && resourceAvailable(field.getScope(), field.getProjectId(), projectId)) {
                roots.add(field);
            }
        }
        if (roots.isEmpty()) {
            addOpenShapeDiagnostic(diagnostics, diagnosticPath,
                    "API 响应对象缺少可由稳定字段 ID 证明的根字段");
            return ObjectShapeIndex.open();
        }
        roots.sort(Comparator
                .comparing(RuleDataObjectField::getSortOrder,
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparing(RuleDataObjectField::getId,
                        Comparator.nullsLast(Long::compareTo)));

        Map<String, Object> rootProperties = new LinkedHashMap<>();
        Map<String, Long> rootPropertyIds = new LinkedHashMap<>();
        Map<String, Map<String, Object>> schemasByPath = new LinkedHashMap<>();
        Map<String, String> leafTypes = new LinkedHashMap<>();
        for (RuleDataObjectField root : roots) {
            String rootName = firstNonBlank(root.getScriptName(), root.getVarCode());
            if (rootName == null || root.getId() == null) {
                continue;
            }
            Long existingId = rootPropertyIds.get(rootName);
            if (existingId != null && !existingId.equals(root.getId())) {
                addDiagnosticIfAbsent(diagnostics, RuleValidationIssue.error(
                        "OBJECT_SHAPE_CONFLICT",
                        "$.field." + root.getId(),
                        "API 响应对象同名根字段对应不同稳定字段 ID: " + rootName));
                continue;
            }
            if (existingId != null) {
                continue;
            }
            DataObjectSchemaResolver.ShapeResult shape =
                    dataObjectSchemaResolver.resolveField(root.getId(), projectId);
            mergeDiagnostics(diagnostics, shape.getDiagnostics());
            Map<String, Object> rootSchema = shape.getSchema();
            rootProperties.put(rootName, rootSchema);
            rootPropertyIds.put(rootName, root.getId());
            indexStableSchema(
                    rootName, rootSchema, schemasByPath, leafTypes);
        }
        Map<String, Object> schema = propertySchema("OBJECT");
        schema.put("properties", rootProperties);
        schema.put("additionalProperties", false);
        return new ObjectShapeIndex(schema, schemasByPath, leafTypes);
    }

    @SuppressWarnings("unchecked")
    private void indexStableSchema(
            String path,
            Map<String, Object> schema,
            Map<String, Map<String, Object>> schemasByPath,
            Map<String, String> typesByPath) {
        String normalizedPath = normalizeIndexedPath(path);
        schemasByPath.putIfAbsent(normalizedPath, schema);
        String type = shapeType(schema);
        if (type != null) {
            typesByPath.putIfAbsent(normalizedPath, type);
        }

        Object properties = schema.get("properties");
        if (properties instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) properties).entrySet()) {
                if (entry.getKey() instanceof String
                        && entry.getValue() instanceof Map) {
                    indexStableSchema(
                            path + "." + entry.getKey(),
                            (Map<String, Object>) entry.getValue(),
                            schemasByPath, typesByPath);
                }
            }
        }
        Object items = schema.get("items");
        if (items instanceof Map && !((Map<?, ?>) items).isEmpty()) {
            indexStableSchema(
                    path + "[]",
                    (Map<String, Object>) items,
                    schemasByPath, typesByPath);
        }
    }

    private void addStableInputFieldSchemas(
            List<RuleDefinitionInputField> fields,
            Long projectId,
            List<RuleValidationIssue> diagnostics,
            Map<String, Object> schemas) {
        if (dataObjectSchemaResolver == null) {
            return;
        }
        for (RuleDefinitionInputField field : fields) {
            if (!"DATA_OBJECT".equals(normalizeRefType(field.getRefType()))
                    || field.getVarId() == null) {
                continue;
            }
            Map<String, Object> schema = resolveDataObjectInputSchema(
                    field, projectId, diagnostics);
            if (schema == null) {
                continue;
            }
            String fieldName = trimToNull(field.getFieldName());
            if (fieldName != null) {
                schemas.put(fieldName, schema);
            }
            String type = shapeType(schema);
            if (type != null) {
                field.setFieldType(type);
            }
        }
    }

    private Map<String, Object> resolveDataObjectInputSchema(
            RuleDefinitionInputField field,
            Long projectId,
            List<RuleValidationIssue> diagnostics) {
        DataObjectSchemaResolver.ShapeResult shape =
                dataObjectSchemaResolver.resolveField(field.getVarId(), projectId);
        mergeDiagnostics(diagnostics, shape.getDiagnostics());
        QLScriptFieldResolver.ValidatedScriptReference reference =
                qlScriptFieldResolver.validateReference(
                        projectId, field.getVarId(), "DATA_OBJECT");
        String inputPath = trimToNull(field.getScriptName());
        String stableRoot = reference == null ? null : reference.getScriptRoot();
        String descendantPath = relativePath(inputPath, stableRoot);
        if (descendantPath == null) {
            addMissingDescendantDiagnostic(diagnostics, inputPath);
            return null;
        }
        if (descendantPath.isEmpty()) {
            return shape.getSchema();
        }
        Map<String, Map<String, Object>> schemasByPath = new LinkedHashMap<>();
        Map<String, String> typesByPath = new LinkedHashMap<>();
        indexStableSchema(
                stableRoot, shape.getSchema(), schemasByPath, typesByPath);
        Map<String, Object> descendantSchema =
                schemasByPath.get(normalizeIndexedPath(inputPath));
        if (descendantSchema == null) {
            addMissingDescendantDiagnostic(diagnostics, inputPath);
        }
        return descendantSchema;
    }

    private void addStableOutputFieldSchemas(
            List<RuleDefinitionOutputField> fields,
            Long projectId,
            List<RuleValidationIssue> diagnostics,
            Map<String, Object> schemas) {
        if (dataObjectSchemaResolver == null) {
            return;
        }
        for (RuleDefinitionOutputField field : fields) {
            if (!"DATA_OBJECT".equals(normalizeRefType(field.getRefType()))
                    || field.getVarId() == null) {
                continue;
            }
            DataObjectSchemaResolver.ShapeResult shape =
                    dataObjectSchemaResolver.resolveField(field.getVarId(), projectId);
            mergeDiagnostics(diagnostics, shape.getDiagnostics());
            String fieldName = trimToNull(field.getFieldName());
            if (fieldName != null) {
                schemas.put(fieldName, shape.getSchema());
            }
            String type = shapeType(shape.getSchema());
            if (type != null) {
                field.setFieldType(type);
            }
        }
    }

    private void applyScriptOutputTypes(
            QLScriptAnalysis analysis,
            List<RuleDefinitionOutputField> outputFields,
            Map<String, String> verifiedInputTypes,
            Map<String, Object> outputSchemas) {
        Map<String, QLScriptAnalysis.OutputField> analysisByName =
                new LinkedHashMap<>();
        for (QLScriptAnalysis.OutputField output : analysis.getPublicOutputs()) {
            analysisByName.putIfAbsent(output.getName(), output);
        }
        Map<String, String> visibleAssignments = analysis.hasExplicitResult()
                ? Collections.emptyMap() : analysis.getLocalAssignments();
        for (RuleDefinitionOutputField field : outputFields) {
            String outputName = firstNonBlank(field.getScriptName(), field.getFieldName());
            QLScriptAnalysis.OutputField analyzed = analysisByName.get(outputName);
            if (analyzed == null) {
                continue;
            }
            String type = resolveExpressionType(
                    analyzed.getSourceExpression(),
                    visibleAssignments,
                    verifiedInputTypes,
                    new LinkedHashSet<>());
            if (type == null) {
                if (analysis.hasExplicitResult() || usesDynamicIndex(
                        analyzed.getSourceExpression(),
                        visibleAssignments,
                        new LinkedHashSet<>())) {
                    field.setFieldType(null);
                    outputSchemas.remove(outputName);
                }
                continue;
            }
            field.setFieldType(type);
            outputSchemas.put(outputName, propertySchema(type));
        }
    }

    private String resolveExpressionType(
            String expression,
            Map<String, String> localAssignments,
            Map<String, String> verifiedInputTypes,
            Set<String> visiting) {
        String value = trimOuterParentheses(trimToNull(expression));
        if (value == null) {
            return null;
        }
        String verified = verifiedInputTypes.get(normalizeIndexedPath(value));
        if (verified != null) {
            return verified;
        }
        if (localAssignments.containsKey(value) && visiting.add(value)) {
            try {
                return resolveExpressionType(localAssignments.get(value),
                        localAssignments, verifiedInputTypes, visiting);
            } finally {
                visiting.remove(value);
            }
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return "STRING";
        }
        if (value.matches("[-+]?\\d+")) {
            return "INTEGER";
        }
        if (value.matches("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+)")) {
            return "NUMBER";
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return "BOOLEAN";
        }
        if (value.startsWith("{") && value.endsWith("}")) {
            return "OBJECT";
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            return "LIST";
        }
        return null;
    }

    private boolean usesDynamicIndex(
            String expression,
            Map<String, String> localAssignments,
            Set<String> visiting) {
        String value = trimOuterParentheses(trimToNull(expression));
        if (value == null) {
            return false;
        }
        Matcher indexMatcher = Pattern.compile("\\[([^\\]]*)]").matcher(value);
        while (indexMatcher.find()) {
            String index = indexMatcher.group(1).trim();
            if (!index.matches("-?\\d+")
                    && !((index.startsWith("\"") && index.endsWith("\""))
                    || (index.startsWith("'") && index.endsWith("'")))) {
                return true;
            }
        }
        if (localAssignments.containsKey(value) && visiting.add(value)) {
            try {
                return usesDynamicIndex(
                        localAssignments.get(value), localAssignments, visiting);
            } finally {
                visiting.remove(value);
            }
        }
        return false;
    }

    private String trimOuterParentheses(String value) {
        String current = value;
        while (current != null && current.length() >= 2
                && current.startsWith("(") && current.endsWith(")")) {
            current = trimToNull(current.substring(1, current.length() - 1));
        }
        return current;
    }

    private void addMissingDescendantDiagnostic(
            List<RuleValidationIssue> diagnostics, String inputPath) {
        RuleValidationIssue issue = RuleValidationIssue.error(
                        "SCRIPT_INPUT_REF_MISSING",
                        "$.script." + inputPath,
                        "脚本对象后代无法由稳定字段 ID 证明")
                .withSafeDetail("fieldPath", inputPath);
        addDiagnosticIfAbsent(diagnostics, issue);
    }

    private void addOpenShapeDiagnostic(
            List<RuleValidationIssue> diagnostics,
            String path,
            String message) {
        addDiagnosticIfAbsent(diagnostics, RuleValidationIssue.warning(
                "OBJECT_SHAPE_INCOMPLETE",
                "$.script." + (path == null ? "" : path), message));
    }

    private void addApiChainDiagnostic(
            List<RuleValidationIssue> diagnostics,
            String code,
            String path,
            String message) {
        addDiagnosticIfAbsent(
                diagnostics, RuleValidationIssue.error(code, path, message));
    }

    private void mergeDiagnostics(
            List<RuleValidationIssue> diagnostics,
            List<RuleValidationIssue> additions) {
        for (RuleValidationIssue issue : additions) {
            addDiagnosticIfAbsent(diagnostics, issue);
        }
    }

    private void addDiagnosticIfAbsent(
            List<RuleValidationIssue> diagnostics,
            RuleValidationIssue addition) {
        for (RuleValidationIssue existing : diagnostics) {
            if (Objects.equals(existing.getCode(), addition.getCode())
                    && Objects.equals(existing.getPath(), addition.getPath())
                    && Objects.equals(existing.getSeverity(), addition.getSeverity())) {
                return;
            }
        }
        diagnostics.add(addition);
    }

    private String relativePath(String fullPath, String root) {
        String path = trimToNull(fullPath);
        String scriptRoot = trimToNull(root);
        if (path == null || scriptRoot == null) {
            return null;
        }
        if (path.equals(scriptRoot)) {
            return "";
        }
        return path.startsWith(scriptRoot + ".")
                ? path.substring(scriptRoot.length() + 1) : null;
    }

    private String normalizeIndexedPath(String path) {
        String value = trimToNull(path);
        return value == null ? null : value.replaceAll("\\[[^\\]]*]", "[]");
    }

    private boolean isObjectOrList(String type) {
        String value = trimToNull(type);
        if (value == null) {
            return false;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        return "OBJECT".equals(normalized) || "MAP".equals(normalized)
                || "LIST".equals(normalized) || "ARRAY".equals(normalized)
                || "SET".equals(normalized);
    }

    private boolean resourceAvailable(
            String scope, Long resourceProjectId, Long projectId) {
        return RuleVariableService.SCOPE_GLOBAL.equalsIgnoreCase(
                scope == null ? "" : scope.trim())
                || projectId == null
                || Objects.equals(resourceProjectId, projectId);
    }

    private String shapeType(Map<String, Object> schema) {
        Object ruleType = schema == null ? null : schema.get("x-rule-type");
        return ruleType instanceof String ? (String) ruleType : null;
    }

    private Map<String, Object> propertySchema(String type) {
        String ruleType = trimToNull(type);
        ruleType = ruleType == null ? "OBJECT" : ruleType.toUpperCase(Locale.ROOT);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", switch (ruleType) {
            case "INTEGER", "LONG", "SHORT", "BYTE" -> "integer";
            case "NUMBER", "DOUBLE", "FLOAT", "DECIMAL", "BIGDECIMAL" -> "number";
            case "BOOLEAN", "BOOL" -> "boolean";
            case "LIST", "ARRAY", "SET" -> "array";
            case "OBJECT", "MAP" -> "object";
            default -> "string";
        });
        if ("DATE".equals(ruleType)) {
            schema.put("format", "date");
        } else if ("DATETIME".equals(ruleType)
                || "LOCALDATETIME".equals(ruleType)) {
            schema.put("format", "date-time");
        }
        schema.put("x-rule-type", ruleType);
        return schema;
    }

    private List<RuleDefinitionInputField> expandStableScriptInputFields(
            List<RuleDefinitionInputField> inputFields,
            Map<String, Map<String, Object>> varMetaMap) {
        List<RuleDefinitionInputField> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (RuleDefinitionInputField field : inputFields) {
            if (field.getVarId() == null || normalizeRefType(field.getRefType()) == null) {
                addInputFieldIfAbsent(result, seen, field);
                continue;
            }
            Map<String, Map<String, Object>> stableMetaMap = new HashMap<>(varMetaMap);
            Map<String, Object> stableMeta =
                    findMetaById(field.getVarId(), field.getRefType(), varMetaMap);
            String scriptName = trimToNull(field.getScriptName());
            if (stableMeta != null && scriptName != null) {
                stableMetaMap.put(scriptName.toLowerCase(), stableMeta);
            }
            for (RuleDefinitionInputField expanded
                    : expandModelInputFields(Collections.singletonList(field), stableMetaMap)) {
                addInputFieldIfAbsent(result, seen, expanded);
            }
        }
        return result;
    }

    /** 将模型或变量入口字段递归展开到 INPUT / DATA_OBJECT 叶子。 */
    public List<RuleDefinitionInputField> resolveInputFields(List<RuleDefinitionInputField> fields, Long projectId) {
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Map<String, Object>> varMetaMap = buildVarMetaMap(projectId);
        List<RuleDefinitionInputField> prepared = new ArrayList<>();
        for (RuleDefinitionInputField field : fields) {
            enrichFieldFromMeta(field, varMetaMap, Collections.emptyMap(), Collections.emptyMap());
            prepared.add(field);
        }
        return expandModelInputFields(prepared, varMetaMap);
    }

    public static class ResolvedFields {
        private final List<RuleDefinitionInputField> inputFields;
        private final List<RuleDefinitionOutputField> outputFields;
        private final List<RuleValidationIssue> diagnostics;
        private final Set<String> localOutputNames;
        private final Map<String, Object> inputPropertySchemas;
        private final Map<String, Object> outputPropertySchemas;
        private final String snapshotModelType;

        public ResolvedFields(List<RuleDefinitionInputField> inputFields,
                              List<RuleDefinitionOutputField> outputFields) {
            this(inputFields, outputFields, Collections.emptyList(), Collections.emptySet(),
                    Collections.emptyMap(), Collections.emptyMap(), null);
        }

        public ResolvedFields(List<RuleDefinitionInputField> inputFields,
                              List<RuleDefinitionOutputField> outputFields,
                              List<RuleValidationIssue> diagnostics,
                              Set<String> localOutputNames,
                              Map<String, Object> inputPropertySchemas,
                              Map<String, Object> outputPropertySchemas) {
            this(inputFields, outputFields, diagnostics, localOutputNames,
                    inputPropertySchemas, outputPropertySchemas, null);
        }

        public ResolvedFields(List<RuleDefinitionInputField> inputFields,
                              List<RuleDefinitionOutputField> outputFields,
                              List<RuleValidationIssue> diagnostics,
                              Set<String> localOutputNames,
                              Map<String, Object> inputPropertySchemas,
                              Map<String, Object> outputPropertySchemas,
                              String snapshotModelType) {
            this.inputFields = immutableList(inputFields);
            this.outputFields = immutableList(outputFields);
            this.diagnostics = immutableList(diagnostics);
            this.localOutputNames = localOutputNames == null
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(localOutputNames));
            this.inputPropertySchemas = immutableMap(inputPropertySchemas);
            this.outputPropertySchemas = immutableMap(outputPropertySchemas);
            this.snapshotModelType = snapshotModelType;
        }

        public List<RuleDefinitionInputField> getInputFields() {
            return inputFields;
        }

        public List<RuleDefinitionOutputField> getOutputFields() {
            return outputFields;
        }

        public List<RuleValidationIssue> getDiagnostics() {
            return diagnostics;
        }

        public Set<String> getLocalOutputNames() {
            return localOutputNames;
        }

        public Map<String, Object> getInputPropertySchemas() {
            return inputPropertySchemas;
        }

        public Map<String, Object> getOutputPropertySchemas() {
            return outputPropertySchemas;
        }

        public String getSnapshotModelType() {
            return snapshotModelType;
        }

        public boolean isLocalOutput(RuleDefinitionOutputField field) {
            if (field == null) {
                return false;
            }
            if (field.getVarId() != null || field.getRefType() != null) {
                return false;
            }
            String name = field.getScriptName() != null
                    ? field.getScriptName() : field.getFieldName();
            return name != null && localOutputNames.contains(name);
        }

        private static <T> List<T> immutableList(List<T> values) {
            return values == null || values.isEmpty()
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(values));
        }

        private static Map<String, Object> immutableMap(Map<String, Object> values) {
            if (values == null || values.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                snapshot.put(entry.getKey(), immutableValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(snapshot);
        }

        private static Object immutableValue(Object value) {
            if (value instanceof Map) {
                Map<?, ?> source = (Map<?, ?>) value;
                Map<Object, Object> snapshot = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : source.entrySet()) {
                    snapshot.put(entry.getKey(), immutableValue(entry.getValue()));
                }
                return Collections.unmodifiableMap(snapshot);
            }
            if (value instanceof List) {
                List<?> source = (List<?>) value;
                List<Object> snapshot = new ArrayList<>(source.size());
                for (Object item : source) {
                    snapshot.add(immutableValue(item));
                }
                return Collections.unmodifiableList(snapshot);
            }
            if (value instanceof Set) {
                Set<?> source = (Set<?>) value;
                Set<Object> snapshot = new LinkedHashSet<>();
                for (Object item : source) {
                    snapshot.add(immutableValue(item));
                }
                return Collections.unmodifiableSet(snapshot);
            }
            return value;
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
                meta.put("defaultValue", v.getDefaultValue());
                meta.put("exampleValue", v.getExampleValue());
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
                meta.put("refVariableId", f.getRefVariableId());
                map.put(key, meta);
            }
        }

        LambdaQueryWrapper<RuleModel> modelWrapper = new LambdaQueryWrapper<RuleModel>()
                .select(RuleModel.class, field -> !"model_content".equals(field.getColumn()));
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
            String outputScript = firstNonBlank(field.getFieldName(), field.getFeatureName(), field.getScriptName());
            if (modelCode == null || outputScript == null) {
                continue;
            }
            String scriptName = modelCode + "." + outputScript;
            if (map.containsKey(scriptName.toLowerCase())) {
                continue;
            }
            Map<String, Object> meta = new HashMap<>();
            meta.put("id", field.getId());
            meta.put("modelId", field.getModelId());
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
        Map<String, Object> meta = findMetaById(field.getVarId(), field.getRefType(), varMetaMap);
        if (meta != null) {
            // 补充变量元信息
            if (field.getFieldLabel() == null || field.getFieldLabel().isEmpty() || field.getFieldLabel().equals(field.getFieldName())) {
                String varLabel = (String) meta.get("varLabel");
                if (varLabel != null && !varLabel.isEmpty()) {
                    field.setFieldLabel(varLabel);
                }
            }
            String varType = (String) meta.get("varType");
            if (varType != null && !varType.isEmpty()) {
                field.setFieldType(varType);
            }
            String scriptName = (String) meta.get("scriptName");
            if (scriptName != null && !scriptName.isEmpty()) {
                field.setScriptName(scriptName);
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
        Map<String, Object> meta = findMetaById(field.getVarId(), field.getRefType(), varMetaMap);
        if (meta != null) {
            if (field.getFieldLabel() == null || field.getFieldLabel().isEmpty() || field.getFieldLabel().equals(field.getFieldName())) {
                String varLabel = (String) meta.get("varLabel");
                if (varLabel != null && !varLabel.isEmpty()) {
                    field.setFieldLabel(varLabel);
                }
            }
            String varType = (String) meta.get("varType");
            if (varType != null && !varType.isEmpty()) {
                field.setFieldType(varType);
            }
            String scriptName = (String) meta.get("scriptName");
            if (scriptName != null && !scriptName.isEmpty()) {
                field.setScriptName(scriptName);
            }
        }
    }

    /**
     * 展开输入字段：把非最原始（依赖其他变量/模型的）字段穿透到最底层依赖字段。
     * 规则输入仅应展示业务系统真正需要提供的原始字段，因此模型输出、DB/API/计算等
     * 派生变量会被展开为其底层依赖，递归直到 INPUT/CONSTANT/DATA_OBJECT 等叶子字段。
     */
    private Map<String, Object> findMetaById(Long varId, String refType, Map<String, Map<String, Object>> varMetaMap) {
        if (varId == null || normalizeRefType(refType) == null
                || varMetaMap == null || varMetaMap.isEmpty()) {
            return null;
        }
        String normalizedRefType = normalizeRefType(refType);
        for (Map<String, Object> meta : varMetaMap.values()) {
            Object id = meta.get("id");
            if (!(id instanceof Long) || !varId.equals(id)) {
                continue;
            }
            String metaRefType = normalizeRefType((String) meta.get("refType"));
            if (normalizedRefType.equals(metaRefType)) {
                return meta;
            }
        }
        return null;
    }

    private List<RuleDefinitionInputField> expandModelInputFields(List<RuleDefinitionInputField> inputFields,
            Map<String, Map<String, Object>> varMetaMap) {
        List<RuleDefinitionInputField> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        for (RuleDefinitionInputField field : inputFields) {
            expandFieldRecursive(field, varMetaMap, seen, visited, result);
        }
        return result;
    }

    private List<RuleDefinitionInputField> runtimeSourceFields(
            List<RuleDefinitionInputField> inputFields,
            Map<String, Map<String, Object>> varMetaMap) {
        List<RuleDefinitionInputField> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (RuleDefinitionInputField field : inputFields) {
            Map<String, Object> meta = findFieldMeta(field, varMetaMap);
            String source = meta == null
                    ? null : normalizeRefType((String) meta.get("varSource"));
            if ("LIST".equals(source) || "API".equals(source)
                    || "DB".equals(source)) {
                addInputFieldIfAbsent(result, seen, field);
            }
        }
        return result;
    }

    private List<RuleDefinitionInputField> mergeRuntimeSourceFields(
            List<RuleDefinitionInputField> inputFields,
            List<RuleDefinitionInputField> runtimeSourceFields) {
        List<RuleDefinitionInputField> result = new ArrayList<>(inputFields);
        Set<String> seen = new LinkedHashSet<>();
        for (RuleDefinitionInputField field : result) {
            seen.add(inputFieldKey(field));
        }
        for (RuleDefinitionInputField field : runtimeSourceFields) {
            addInputFieldIfAbsent(result, seen, field);
        }
        return result;
    }

    private List<RuleDefinitionInputField> loadRuleCallInputFields(String modelJson) {
        List<RuleDefinitionInputField> result = new ArrayList<>();
        if (inputFieldMapper == null || modelJson == null || modelJson.isEmpty()) {
            return result;
        }
        Set<Long> ruleIds = new LinkedHashSet<>();
        collectRuleCallIds(parseObject(modelJson), ruleIds);
        for (Long ruleId : ruleIds) {
            List<RuleDefinitionInputField> fields = inputFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDefinitionInputField>()
                            .eq(RuleDefinitionInputField::getDefinitionId, ruleId)
                            .and(w -> w.isNull(RuleDefinitionInputField::getStatus).or().eq(RuleDefinitionInputField::getStatus, 1))
                            .orderByAsc(RuleDefinitionInputField::getSortOrder)
                            .orderByAsc(RuleDefinitionInputField::getId));
            if (fields == null) {
                continue;
            }
            for (RuleDefinitionInputField field : fields) {
                result.add(copyDefinitionInputField(field));
            }
        }
        return result;
    }

    private List<RuleDefinitionOutputField> loadRuleCallOutputFields(String modelJson) {
        List<RuleDefinitionOutputField> result = new ArrayList<>();
        if (outputFieldMapper == null || modelJson == null || modelJson.isEmpty()) {
            return result;
        }
        Set<Long> ruleIds = new LinkedHashSet<>();
        collectRuleCallIds(parseObject(modelJson), ruleIds);
        for (Long ruleId : ruleIds) {
            List<RuleDefinitionOutputField> fields = outputFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDefinitionOutputField>()
                            .eq(RuleDefinitionOutputField::getDefinitionId, ruleId)
                            .and(w -> w.isNull(RuleDefinitionOutputField::getStatus).or().eq(RuleDefinitionOutputField::getStatus, 1))
                            .orderByAsc(RuleDefinitionOutputField::getSortOrder)
                            .orderByAsc(RuleDefinitionOutputField::getId));
            if (fields == null) {
                continue;
            }
            for (RuleDefinitionOutputField field : fields) {
                result.add(copyDefinitionOutputField(field));
            }
        }
        return result;
    }

    private void collectRuleCallIds(Object value, Set<Long> ruleIds) {
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            if ("rule-call".equals(obj.getString("type")) && obj.getLong("ruleId") != null) {
                ruleIds.add(obj.getLong("ruleId"));
            }
            for (String key : obj.keySet()) {
                collectRuleCallIds(obj.get(key), ruleIds);
            }
        } else if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            for (int i = 0; i < arr.size(); i++) {
                collectRuleCallIds(arr.get(i), ruleIds);
            }
        }
    }

    private RuleDefinitionInputField copyDefinitionInputField(RuleDefinitionInputField source) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setVarId(source.getVarId());
        field.setRefType(source.getRefType());
        field.setFieldName(source.getFieldName());
        field.setFieldLabel(source.getFieldLabel());
        field.setScriptName(source.getScriptName());
        field.setFieldType(source.getFieldType());
        field.setMissingValue(source.getMissingValue());
        field.setDefaultValue(source.getDefaultValue());
        field.setExampleValue(source.getExampleValue());
        field.setValidValues(source.getValidValues());
        field.setTransformType(source.getTransformType());
        field.setTransformParams(source.getTransformParams());
        field.setValidationRuleIds(normalizeValidationRuleIds(source.getValidationRuleIds()));
        field.setValidationOverride(0);
        field.setStatus(source.getStatus());
        return field;
    }

    private RuleDefinitionOutputField copyDefinitionOutputField(RuleDefinitionOutputField source) {
        RuleDefinitionOutputField field = new RuleDefinitionOutputField();
        field.setVarId(source.getVarId());
        field.setRefType(source.getRefType());
        field.setFieldName(source.getFieldName());
        field.setFieldLabel(source.getFieldLabel());
        field.setScriptName(source.getScriptName());
        field.setFieldType(source.getFieldType());
        field.setTransformType(source.getTransformType());
        field.setTransformParams(source.getTransformParams());
        field.setValidValues(source.getValidValues());
        field.setStatus(source.getStatus());
        return field;
    }

    private List<RuleDefinitionOutputField> deduplicateOutputFields(List<RuleDefinitionOutputField> fields) {
        Map<String, RuleDefinitionOutputField> dedup = new LinkedHashMap<>();
        for (RuleDefinitionOutputField field : fields) {
            String scriptName = trimToNull(field.getScriptName());
            String fieldName = trimToNull(field.getFieldName());
            String key = scriptName != null ? scriptName.toLowerCase()
                    : (fieldName == null ? null : fieldName.toLowerCase());
            if (key != null) {
                dedup.putIfAbsent(key, field);
            }
        }
        return new ArrayList<>(dedup.values());
    }

    private Set<String> retainedLocalOutputNames(
            List<RuleDefinitionOutputField> retainedOutputs,
            Set<RuleDefinitionOutputField> localCandidates) {
        Set<String> names = new LinkedHashSet<>();
        for (RuleDefinitionOutputField output : retainedOutputs) {
            if (!localCandidates.contains(output)) {
                continue;
            }
            String name = trimToNull(output.getScriptName());
            if (name == null) {
                name = trimToNull(output.getFieldName());
            }
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private List<RuleDefinitionInputField> removeOutputFields(List<RuleDefinitionInputField> inputFields,
            List<RuleDefinitionOutputField> outputFields) {
        if (inputFields == null || inputFields.isEmpty() || outputFields == null || outputFields.isEmpty()) {
            return inputFields;
        }
        Set<String> outputNames = new HashSet<>();
        for (RuleDefinitionOutputField output : outputFields) {
            String scriptName = trimToNull(output.getScriptName());
            if (scriptName != null) {
                outputNames.add(scriptName.toLowerCase());
            }
        }
        if (outputNames.isEmpty()) {
            return inputFields;
        }
        List<RuleDefinitionInputField> result = new ArrayList<>();
        for (RuleDefinitionInputField input : inputFields) {
            String scriptName = trimToNull(input.getScriptName());
            if (scriptName == null || !outputNames.contains(scriptName.toLowerCase())) {
                result.add(input);
            }
        }
        return result;
    }

    private void expandFieldRecursive(RuleDefinitionInputField field,
            Map<String, Map<String, Object>> varMetaMap,
            Set<String> seen, Set<String> visited,
            List<RuleDefinitionInputField> result) {
        String scriptName = trimToNull(field.getScriptName());
        String refType = normalizeRefType(field.getRefType());
        Map<String, Object> meta = scriptName != null ? varMetaMap.get(scriptName.toLowerCase()) : null;
        if (meta == null) {
            meta = findMetaById(field.getVarId(), field.getRefType(), varMetaMap);
            if (meta != null) {
                String metaScriptName = trimToNull((String) meta.get("scriptName"));
                if (metaScriptName != null) {
                    field.setScriptName(metaScriptName);
                    scriptName = metaScriptName;
                }
                String metaType = trimToNull((String) meta.get("varType"));
                if (metaType != null && ("STRING".equals(field.getFieldType()) || field.getFieldType() == null)) {
                    field.setFieldType(metaType);
                }
            }
        }
        String varSource = meta != null ? (String) meta.get("varSource") : null;
        applySampleValuesFromMeta(field, meta);

        if ("CONSTANT".equals(refType) || "CONSTANT".equals(varSource)) {
            return;
        }

        if ("DATA_OBJECT".equals(refType) && meta != null
                && meta.get("refVariableId") instanceof Long) {
            Map<String, Object> referencedMeta = findMetaById(
                    (Long) meta.get("refVariableId"), "VARIABLE", varMetaMap);
            if (referencedMeta == null) {
                referencedMeta = findMetaById((Long) meta.get("refVariableId"), "CONSTANT", varMetaMap);
            }
            if (referencedMeta != null) {
                expandFieldRecursive(inputFieldFromMeta(referencedMeta),
                        varMetaMap, seen, visited, result);
                return;
            }
        }

        // 防环：同一 (refType:scriptName) 只展开一次，避免变量/模型相互引用导致死循环
        String visitKey = (refType != null ? refType : "NONE") + ":" + (scriptName != null ? scriptName.toLowerCase() : "null");
        boolean alreadyVisited = !visited.add(visitKey);
        if (alreadyVisited) {
            addInputFieldIfAbsent(result, seen, field);
            return;
        }

        // 名单查询变量展开为查询依赖；仅无法解析依赖时保留源字段
        if ("VARIABLE".equals(refType) && "LIST".equals(varSource)) {
            List<RuleDefinitionInputField> listDependencies =
                    buildListDependencyFields(field, varMetaMap);
            for (RuleDefinitionInputField listDependency : listDependencies) {
                enrichFieldFromMeta(listDependency, varMetaMap, Collections.emptyMap(), Collections.emptyMap());
                expandFieldRecursive(listDependency, varMetaMap, seen, visited, result);
            }
            if (listDependencies.isEmpty()) {
                addInputFieldIfAbsent(result, seen, field);
            }
            visited.remove(visitKey);
            return;
        }

        // 仅当未展开过且携带 varId 时递归穿透（visited 仅防止重复展开，不阻止叶子落库）
        if (field.getVarId() != null && ("MODEL".equals(refType) || "MODEL_OUTPUT".equals(refType))) {
            expandModelOrOutputFields(field, meta, varMetaMap, seen, visited, result);
            visited.remove(visitKey);
            return;
        }
        if ("DB".equals(varSource) || "API".equals(varSource) || "COMPUTED".equals(varSource)) {
            expandSourceVariableFields(field, meta, varMetaMap, seen, visited, result);
            visited.remove(visitKey);
            return;
        }

        // 叶子字段（INPUT / CONSTANT / DATA_OBJECT / 未知引用 / 无法解析的依赖）：保留
        addInputFieldIfAbsent(result, seen, field);
        visited.remove(visitKey);
    }

    private RuleDefinitionInputField inputFieldFromMeta(Map<String, Object> meta) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setVarId((Long) meta.get("id"));
        field.setRefType(normalizeRefType((String) meta.get("refType")));
        field.setFieldName(firstNonBlank(
                (String) meta.get("varCode"), (String) meta.get("scriptName")));
        field.setFieldLabel(firstNonBlank(
                (String) meta.get("varLabel"), field.getFieldName()));
        field.setScriptName((String) meta.get("scriptName"));
        field.setFieldType(firstNonBlank((String) meta.get("varType"), "STRING"));
        field.setDefaultValue((String) meta.get("defaultValue"));
        field.setExampleValue((String) meta.get("exampleValue"));
        field.setStatus(1);
        field.setCreateTime(LocalDateTime.now());
        return field;
    }

    private void expandModelOrOutputFields(RuleDefinitionInputField field, Map<String, Object> meta,
            Map<String, Map<String, Object>> varMetaMap, Set<String> seen, Set<String> visited,
            List<RuleDefinitionInputField> result) {
        if (field.getVarId() == null) {
            addInputFieldIfAbsent(result, seen, field);
            return;
        }
        Long modelId = resolveModelId(field, meta);
        if (modelId == null) {
            addInputFieldIfAbsent(result, seen, field);
            return;
        }
        List<RuleModelInputField> modelFields = loadModelInputFields(modelId);
        if (modelFields == null || modelFields.isEmpty()) {
            addInputFieldIfAbsent(result, seen, field);
            return;
        }
        int before = result.size();
        boolean hasOperandBinding = false;
        boolean hasResolvedDependency = false;
        for (RuleModelInputField modelField : modelFields) {
            if (trimToNull(modelField.getSourceOperand()) != null || trimToNull(modelField.getDefaultOperand()) != null) {
                hasOperandBinding = true;
            }
            List<RuleDefinitionInputField> expandedFields = copyModelInputFields(modelField);
            if (!expandedFields.isEmpty()) {
                hasResolvedDependency = true;
            }
            for (RuleDefinitionInputField expanded : expandedFields) {
                enrichFieldFromMeta(expanded, varMetaMap, Collections.emptyMap(), Collections.emptyMap());
                expandFieldRecursive(expanded, varMetaMap, seen, visited, result);
            }
        }
        if (result.size() == before && !hasOperandBinding && !hasResolvedDependency) {
            addInputFieldIfAbsent(result, seen, field);
        }
    }

    /**
     * 加载模型输入字段。抽成独立方法便于测试覆盖（子类重写或注入 mapper）。
     */
    protected List<RuleModelInputField> loadModelInputFields(Long modelId) {
        if (modelInputFieldMapper == null || modelId == null) {
            return java.util.Collections.emptyList();
        }
        return modelInputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleModelInputField>()
                        .eq(RuleModelInputField::getModelId, modelId)
                        .and(w -> w.isNull(RuleModelInputField::getStatus).or().eq(RuleModelInputField::getStatus, 1))
                        .orderByAsc(RuleModelInputField::getSortOrder)
                        .orderByAsc(RuleModelInputField::getId));
    }

    private Long resolveModelId(RuleDefinitionInputField field, Map<String, Object> meta) {
        String refType = normalizeRefType(field.getRefType());
        if ("MODEL".equals(refType)) {
            return field.getVarId();
        }
        if ("MODEL_OUTPUT".equals(refType)) {
            if (meta != null && meta.get("modelId") instanceof Long) {
                return (Long) meta.get("modelId");
            }
            if (modelOutputFieldMapper != null) {
                RuleModelOutputField mof = modelOutputFieldMapper.selectById(field.getVarId());
                if (mof != null) {
                    return mof.getModelId();
                }
            }
        }
        return null;
    }

    private void expandSourceVariableFields(RuleDefinitionInputField field, Map<String, Object> meta,
            Map<String, Map<String, Object>> varMetaMap, Set<String> seen, Set<String> visited,
            List<RuleDefinitionInputField> result) {
        if (meta == null) {
            addInputFieldIfAbsent(result, seen, field);
            return;
        }
        String varSource = (String) meta.get("varSource");
        String sourceConfig = (String) meta.get("sourceConfig");
        if (varSource == null) {
            addInputFieldIfAbsent(result, seen, field);
            return;
        }
        Set<String> depNames = new LinkedHashSet<>();
        if ("COMPUTED".equals(varSource)) {
            String expr = null;
            if (sourceConfig != null) {
                JSONObject obj = parseObject(sourceConfig);
                expr = firstNonBlank(obj.getString("expression"), obj.getString("computeExpression"), obj.getString("script"));
            }
            if (expr == null) {
                expr = sourceConfig;
            }
            if (expr != null) {
                depNames.addAll(collectExpressionIdentifiers(expr));
            }
        } else if ("API".equals(varSource)) {
            List<RuleDefinitionInputField> requestFields = loadApiRequestObjectFields(sourceConfig);
            if (!requestFields.isEmpty()) {
                for (RuleDefinitionInputField requestField : requestFields) {
                    expandFieldRecursive(requestField, varMetaMap, seen, visited, result);
                }
                return;
            }
            if (variableSourceResolver != null) {
                RuleVariable variable = new RuleVariable();
                variable.setScriptName(trimToNull(field.getScriptName()));
                variable.setVarSource(varSource);
                variable.setSourceConfig(sourceConfig);
                depNames.addAll(variableSourceResolver.collectVariableDependencies(variable));
            }
        } else if (variableSourceResolver != null) {
            RuleVariable variable = new RuleVariable();
            variable.setScriptName(trimToNull(field.getScriptName()));
            variable.setVarSource(varSource);
            variable.setSourceConfig(sourceConfig);
            depNames.addAll(variableSourceResolver.collectVariableDependencies(variable));
        }
        int before = result.size();
        for (String depName : depNames) {
            RuleDefinitionInputField depField = new RuleDefinitionInputField();
            depField.setScriptName(depName);
            depField.setFieldName(depName);
            depField.setFieldLabel(depName);
            depField.setFieldType(inferFieldType(depName));
            depField.setStatus(1);
            depField.setCreateTime(LocalDateTime.now());
            enrichFieldFromMeta(depField, varMetaMap, Collections.emptyMap(), Collections.emptyMap());
            Map<String, Object> depMeta = findFieldMeta(depField, varMetaMap);
            if (depField.getRefType() == null && depMeta != null && depMeta.get("varSource") != null) {
                depField.setRefType("VARIABLE");
            }
            String depScriptName = trimToNull(depField.getScriptName());
            String depRefType = normalizeRefType(depField.getRefType());
            String depVisitKey = (depRefType != null ? depRefType : "NONE") + ":"
                    + (depScriptName != null ? depScriptName.toLowerCase() : "null");
            if (visited.contains(depVisitKey)) {
                addInputFieldIfAbsent(result, seen, field);
            }
            expandFieldRecursive(depField, varMetaMap, seen, visited, result);
        }
        if ("COMPUTED".equals(varSource) && result.size() == before) {
            addInputFieldIfAbsent(result, seen, field);
        }
    }

    private void applySampleValuesFromMeta(RuleDefinitionInputField field, Map<String, Object> meta) {
        if (field == null || meta == null) {
            return;
        }
        if ((field.getDefaultValue() == null || field.getDefaultValue().isEmpty()) && meta.get("defaultValue") instanceof String) {
            field.setDefaultValue((String) meta.get("defaultValue"));
        }
        if ((field.getExampleValue() == null || field.getExampleValue().isEmpty()) && meta.get("exampleValue") instanceof String) {
            field.setExampleValue((String) meta.get("exampleValue"));
        }
    }

    private List<RuleDefinitionInputField> buildListDependencyFields(
            RuleDefinitionInputField field,
            Map<String, Map<String, Object>> varMetaMap) {
        Map<String, Object> meta = findFieldMeta(field, varMetaMap);
        if (meta == null || !"LIST".equals(meta.get("varSource"))) {
            return Collections.emptyList();
        }
        JSONObject config = parseObject((String) meta.get("sourceConfig"));
        JSONArray operands = config.getJSONArray("queryOperands");
        List<RuleDefinitionInputField> dependencies = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (operands != null) {
            for (int i = 0; i < operands.size(); i++) {
                JSONObject operand = operands.getJSONObject(i);
                if (operand == null) continue;
                for (JSONObject reference
                        : OperandValueResolver.collectReferences(
                                operand.toJSONString())) {
                    String refType = normalizeRefType(
                            reference.getString("refType"));
                    if ("CONSTANT".equals(refType)) continue;
                    String path = firstNonBlank(
                            reference.getString("value"),
                            reference.getString("code"));
                    if (path == null || !seen.add(path.toLowerCase())) continue;
                    RuleDefinitionInputField dependency =
                            new RuleDefinitionInputField();
                    dependency.setFieldName(path);
                    dependency.setFieldLabel(firstNonBlank(
                            reference.getString("label"), path));
                    dependency.setScriptName(path);
                    dependency.setFieldType(firstNonBlank(
                            reference.getString("valueType"), "STRING"));
                    dependency.setVarId(reference.getLong("refId"));
                    dependency.setRefType(refType);
                    dependency.setStatus(1);
                    dependency.setCreateTime(LocalDateTime.now());
                    dependencies.add(dependency);
                }
            }
        }
        if (!dependencies.isEmpty()) {
            return dependencies;
        }
        RuleDefinitionInputField legacy =
                buildListDependencyField(field, varMetaMap);
        return legacy == null
                ? Collections.emptyList()
                : Collections.singletonList(legacy);
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
        field.setDefaultValue(modelField.getDefaultValue());
        field.setValidValues(modelField.getValidValues());
        field.setTransformType(modelField.getTransformType());
        field.setTransformParams(modelField.getTransformParams());
        field.setStatus(1);
        field.setCreateTime(LocalDateTime.now());
        return field;
    }

    private List<RuleDefinitionInputField> copyModelInputFields(RuleModelInputField modelField) {
        List<JSONObject> operands = new ArrayList<>();
        collectReferenceOperands(parseObject(modelField.getSourceOperand()), operands);
        collectReferenceOperands(parseObject(modelField.getDefaultOperand()), operands);
        if (operands.isEmpty()) {
            if (trimToNull(modelField.getSourceOperand()) != null || trimToNull(modelField.getDefaultOperand()) != null) {
                return Collections.emptyList();
            }
            return Collections.singletonList(copyModelInputField(modelField));
        }
        List<RuleDefinitionInputField> fields = new ArrayList<>();
        for (JSONObject operand : operands) {
            RuleDefinitionInputField field = new RuleDefinitionInputField();
            String scriptName = firstNonBlank(operand.getString("code"), operand.getString("value"));
            field.setVarId(operand.getLong("refId"));
            field.setRefType(normalizeRefType(operand.getString("refType")));
            field.setFieldName(leafName(scriptName));
            field.setFieldLabel(firstNonBlank(operand.getString("label"), field.getFieldName(), scriptName));
            field.setScriptName(scriptName);
            field.setFieldType(firstNonBlank(operand.getString("valueType"), modelField.getFieldType(), "STRING"));
            field.setStatus(1);
            field.setCreateTime(LocalDateTime.now());
            fields.add(field);
        }
        return fields;
    }

    private void collectReferenceOperands(JSONObject operand, List<JSONObject> result) {
        if (operand == null) return;
        String kind = operand.getString("kind");
        if ("PATH".equals(kind) || "REFERENCE".equals(kind)) {
            result.add(operand);
            return;
        }
        if ("FUNCTION".equals(kind)) collectReferenceOperands(operand.getJSONArray("args"), result);
        else if ("OPERATION".equals(kind)) collectOperationTerms(operand.getJSONArray("terms"), result);
        else if ("ARRAY".equals(kind)) collectReferenceOperands(operand.getJSONArray("items"), result);
        else if ("ACCESS".equals(kind)) {
            collectReferenceOperands(operand.getJSONObject("target"), result);
            collectReferenceOperands(operand.getJSONObject("accessor"), result);
        } else if ("CAST".equals(kind)) collectReferenceOperands(operand.getJSONObject("operand"), result);
    }

    private void collectReferenceOperands(JSONArray operands, List<JSONObject> result) {
        if (operands == null) return;
        for (int i = 0; i < operands.size(); i++) {
            collectReferenceOperands(operands.getJSONObject(i), result);
        }
    }

    private void collectOperationTerms(JSONArray terms, List<JSONObject> result) {
        if (terms == null) return;
        for (int i = 0; i < terms.size(); i++) {
            JSONObject term = terms.getJSONObject(i);
            collectReferenceOperands(term == null ? null : term.getJSONObject("operand"), result);
        }
    }

    private void addInputFieldIfAbsent(List<RuleDefinitionInputField> fields, Set<String> seen, RuleDefinitionInputField field) {
        String normalized = inputFieldKey(field);
        if ("NAME:".equals(normalized)) return;
        if (seen.add(normalized)) {
            fields.add(field);
            return;
        }
        for (RuleDefinitionInputField existing : fields) {
            if (normalized.equals(inputFieldKey(existing))) {
                mergeInputValidation(existing, field);
                break;
            }
        }
    }

    private List<RuleDefinitionInputField> loadApiRequestObjectFields(String sourceConfig) {
        if (externalApiConfigMapper == null || dataObjectMapper == null || dataObjectFieldMapper == null) {
            return Collections.emptyList();
        }
        Long apiConfigId = parseObject(sourceConfig).getLong("apiConfigId");
        if (apiConfigId == null) {
            return Collections.emptyList();
        }
        RuleExternalApiConfig apiConfig = externalApiConfigMapper.selectById(apiConfigId);
        if (apiConfig == null || apiConfig.getRequestObjectId() == null) {
            return Collections.emptyList();
        }
        RuleDataObject object = dataObjectMapper.selectById(apiConfig.getRequestObjectId());
        if (object == null) {
            return Collections.emptyList();
        }
        List<RuleDataObjectField> fields = dataObjectFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDataObjectField>()
                        .eq(RuleDataObjectField::getObjectId, object.getId())
                        .eq(RuleDataObjectField::getStatus, 1)
                        .orderByAsc(RuleDataObjectField::getSortOrder)
                        .orderByAsc(RuleDataObjectField::getId));
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, RuleDataObject> objectMap = Collections.singletonMap(object.getId(), object);
        List<RuleDefinitionInputField> result = new ArrayList<>();
        for (RuleDataObjectField source : fields) {
            String scriptName = buildObjectFieldScriptName(source, objectMap);
            if (scriptName == null) {
                continue;
            }
            RuleDefinitionInputField target = new RuleDefinitionInputField();
            target.setVarId(source.getId());
            target.setRefType("DATA_OBJECT");
            target.setFieldName(firstNonBlank(source.getVarCode(), source.getScriptName(), scriptName));
            target.setFieldLabel(firstNonBlank(source.getVarLabel(), target.getFieldName()));
            target.setScriptName(scriptName);
            target.setFieldType(firstNonBlank(source.getVarType(), "STRING"));
            target.setStatus(1);
            target.setCreateTime(LocalDateTime.now());
            result.add(target);
        }
        return result;
    }

    private void mergeInputValidation(RuleDefinitionInputField outer, RuleDefinitionInputField inherited) {
        if (outer == null || inherited == null || Integer.valueOf(1).equals(outer.getValidationOverride())) {
            return;
        }
        String inheritedRules = normalizeValidationRuleIds(inherited.getValidationRuleIds());
        if (!"[]".equals(inheritedRules)) {
            outer.setValidationRuleIds(inheritedRules);
        } else if (outer.getValidationRuleIds() == null) {
            outer.setValidationRuleIds("[]");
        }
        outer.setValidationOverride(0);
    }

    private Map<String, RuleDefinitionInputField> getExistingInputFieldMap(Long definitionId) {
        Map<String, RuleDefinitionInputField> result = new HashMap<>();
        List<RuleDefinitionInputField> fields = inputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionInputField>()
                        .eq(RuleDefinitionInputField::getDefinitionId, definitionId));
        for (RuleDefinitionInputField field : fields == null
                ? Collections.<RuleDefinitionInputField>emptyList() : fields) {
            result.put(inputFieldKey(field), field);
        }
        return result;
    }

    private String inputFieldKey(RuleDefinitionInputField field) {
        String refType = normalizeRefType(field.getRefType());
        if (refType != null && field.getVarId() != null) {
            return refType + ":" + field.getVarId();
        }
        String name = firstNonBlank(field.getScriptName(), field.getFieldName());
        return "NAME:" + (name == null ? "" : name.toLowerCase());
    }

    private String normalizeValidationRuleIds(String json) {
        if (json == null || json.trim().isEmpty()) return "[]";
        try {
            List<Long> ids = JSON.parseArray(json, Long.class);
            return JSON.toJSONString(ids == null ? Collections.emptyList() : new ArrayList<>(new LinkedHashSet<>(ids)));
        } catch (RuntimeException ignored) {
            return "[]";
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
            if (refId == null && obj.containsKey("refId")) {
                refId = obj.getLong("refId");
            }
            String refType = normalizeRefType(obj.getString("_refType"));
            if (refType == null) {
                refType = normalizeRefType(obj.getString("refType"));
            }
            if (refId != null) {
                addExplicitRef(refs, obj.getString("varCode"), refId, refType);
                addExplicitRef(refs, obj.getString("refCode"), refId, refType);
                addExplicitRef(refs, obj.getString("code"), refId, refType);
                addExplicitRef(refs, obj.getString("value"), refId, refType);
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
        if (ref == null) {
            ref = findRootVariableExplicitRef(field, explicitRefMap);
        }
        if (ref != null) {
            field.setVarId(ref.refId);
            field.setRefType(ref.refType);
        }
    }

    private FieldRef findRootVariableExplicitRef(RuleDefinitionInputField field,
                                                  Map<String, FieldRef> explicitRefMap) {
        String path = trimToNull(field.getScriptName());
        if (path == null) {
            path = trimToNull(field.getFieldName());
        }
        while (path != null) {
            int separator = path.lastIndexOf('.');
            if (separator <= 0) {
                return null;
            }
            path = path.substring(0, separator);
            FieldRef ref = explicitRefMap.get(path);
            if (ref == null) {
                ref = explicitRefMap.get(path.toLowerCase());
            }
            if (ref != null && "VARIABLE".equals(normalizeRefType(ref.refType))) {
                return ref;
            }
        }
        return null;
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
                return qlScriptFieldResolver.resolve(modelJson, null).getInputFields();
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
     * 提取规则模型中带稳定引用的变量、模型或模型输出字段，供执行阶段触发来源解析。
     * 普通脚本标识符没有显式引用身份，不能加入变量来源解析范围。
     */
    public List<RuleDefinitionInputField> extractDirectModelInputFields(String modelJson, String modelType) {
        if ("SCRIPT".equalsIgnoreCase(modelType)) {
            if (qlScriptFieldResolver == null) {
                return Collections.emptyList();
            }
            List<RuleDefinitionInputField> result = new ArrayList<>();
            for (RuleDefinitionInputField field
                    : qlScriptFieldResolver.resolve(modelJson, null).getInputFields()) {
                String refType = normalizeRefType(field.getRefType());
                if ("VARIABLE".equals(refType) || "MODEL".equals(refType)
                        || "MODEL_OUTPUT".equals(refType)) {
                    result.add(field);
                }
            }
            return result;
        }
        List<RuleDefinitionInputField> result = new ArrayList<>();
        Map<String, FieldRef> explicitRefMap = collectExplicitRefs(modelJson);
        for (RuleDefinitionInputField field : extractInputFields(modelJson, modelType)) {
            applyExplicitRef(field, explicitRefMap);
            String refType = normalizeRefType(field.getRefType());
            if ("VARIABLE".equals(refType) || "MODEL".equals(refType) || "MODEL_OUTPUT".equals(refType)) {
                result.add(field);
            }
        }
        return result;
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
                return qlScriptFieldResolver.resolve(modelJson, null).getOutputFields();
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
        if ("RULE_SET".equals(type)) {
            applyRuleSetResultVarMetadata(model, fields);
        }

        return fields;
    }

    private void applyRuleSetResultVarMetadata(JSONObject model, List<RuleDefinitionOutputField> fields) {
        JSONObject resultVar = model.getJSONObject("resultVar");
        if (resultVar == null) return;
        JSONObject operand = resultVar.getJSONObject("operand");
        String code = firstNonBlank(operand != null ? operand.getString("code") : null,
                operand != null ? operand.getString("value") : null,
                resultVar.getString("varCode"));
        if (code == null) return;
        for (RuleDefinitionOutputField field : fields) {
            if (!code.equals(field.getScriptName())) continue;
            String fieldType = firstNonBlank(operand != null ? operand.getString("valueType") : null,
                    resultVar.getString("varType"));
            if (fieldType != null) field.setFieldType(fieldType);
            Long refId = operand != null && operand.getLong("refId") != null
                    ? operand.getLong("refId") : resultVar.getLong("_varId");
            String refType = firstNonBlank(operand != null ? operand.getString("refType") : null,
                    resultVar.getString("_refType"));
            field.setVarId(refId);
            field.setRefType(normalizeRefType(refType));
            String label = firstNonBlank(resultVar.getString("varLabel"),
                    operand != null ? operand.getString("label") : null);
            if (label != null) field.setFieldLabel(label);
            return;
        }
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
                JSONArray ruleActions = rule.getJSONArray("actions");
                if (ruleActions != null) {
                    for (int j = 0; j < ruleActions.size(); j++) {
                        OperandDependencyCollector.collect(ruleActions.getJSONObject(j).getJSONObject("valueOperand"), inputVars);
                    }
                }
            }
        }
    }

    private void collectVarCodesFromConditionTree(JSONObject node, Set<String> inputVars) {
        if (node == null) return;
        OperandDependencyCollector.collect(node.getJSONObject("leftOperand"), inputVars);
        OperandDependencyCollector.collect(node.getJSONObject("rightOperand"), inputVars);
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
                OperandDependencyCollector.collect(action.getJSONObject("targetOperand"), outputVars);
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
                        OperandDependencyCollector.collect(action.getJSONObject("targetOperand"), outputVars);
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
        if (!isInput) {
            JSONObject resultVar = model.getJSONObject("resultVar");
            if (resultVar != null) {
                JSONObject operand = resultVar.getJSONObject("operand");
                if (operand != null) {
                    OperandDependencyCollector.collect(operand, varCodes);
                } else {
                    String varCode = resultVar.getString("varCode");
                    if (varCode != null && !varCode.trim().isEmpty()) varCodes.add(varCode);
                }
            }
        }
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
            Set<String> localVars = new LinkedHashSet<>();
            collectActionLocalVars(model, localVars);
            varCodes.removeAll(localVars);
        }
    }

    private void collectActionLocalVars(Object value, Set<String> localVars) {
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            if ("foreach".equals(getString(obj, "type"))) {
                addVarName(localVars, getString(obj, "itemVar"));
            }
            for (String key : obj.keySet()) {
                collectActionLocalVars(obj.get(key), localVars);
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.size(); i++) {
                collectActionLocalVars(array.get(i), localVars);
            }
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

    private boolean hasExplicitArgRef(JSONObject ref) {
        return ref != null && (ref.getLong("_varId") != null || ref.getLong("varId") != null);
    }

    private void collectActionBlockVars(JSONObject block, Set<String> varCodes, boolean isInput) {
        if (block == null) return;
        String type = getString(block, "type");
        boolean disabledRuleCallMapping = "rule-call".equals(type)
                && block.containsKey("enableOutputMapping")
                && !Boolean.TRUE.equals(block.getBoolean("enableOutputMapping"));
        if (!isInput && !disabledRuleCallMapping) {
            OperandDependencyCollector.collect(block.getJSONObject("targetOperand"), varCodes);
            addVarName(varCodes, getString(block, "target"));
            addVarName(varCodes, getString(block, "outputVar"));
            if (type == null || "action".equals(type)) {
                addVarName(varCodes, getString(block, "varCode"));
            }
        }

        if (isInput) {
            OperandDependencyCollector.collect(block.getJSONObject("valueOperand"), varCodes);
            OperandDependencyCollector.collect(block.getJSONObject("leftOperand"), varCodes);
            OperandDependencyCollector.collect(block.getJSONObject("rightOperand"), varCodes);
            OperandDependencyCollector.collect(block.getJSONObject("matchOperand"), varCodes);
            OperandDependencyCollector.collect(block.getJSONObject("listOperand"), varCodes);
            OperandDependencyCollector.collect(block.getJSONObject("checkOperand"), varCodes);
            OperandDependencyCollector.collect(block.getJSONObject("trueOperand"), varCodes);
            OperandDependencyCollector.collect(block.getJSONObject("falseOperand"), varCodes);
            JSONArray operandArgs = block.getJSONArray("args");
            if (operandArgs != null) {
                for (int i = 0; i < operandArgs.size(); i++) {
                    Object arg = operandArgs.get(i);
                    if (arg instanceof JSONObject) OperandDependencyCollector.collect((JSONObject) arg, varCodes);
                }
            }
            if ("assign".equals(type)) {
                extractIdentifiersFromExpression(getString(block, "value"), varCodes);
            } else if ("func-call".equals(type)) {
                JSONArray args = block.getJSONArray("args");
                JSONArray argRefs = block.getJSONArray("_argRefs");
                if (args != null && argRefs != null) {
                    for (int i = 0; i < args.size(); i++) {
                        JSONObject ref = i < argRefs.size() ? argRefs.getJSONObject(i) : null;
                        if (hasExplicitArgRef(ref)) {
                            addVarName(varCodes, args.getString(i));
                        }
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
                JSONArray inOperands = block.getJSONArray("inOperands");
                if (inOperands != null) for (int i = 0; i < inOperands.size(); i++) OperandDependencyCollector.collect(inOperands.getJSONObject(i), varCodes);
            } else if ("template-str".equals(type)) {
                JSONArray parts = block.getJSONArray("parts");
                if (parts != null) {
                    for (int i = 0; i < parts.size(); i++) {
                        JSONObject part = parts.getJSONObject(i);
                        OperandDependencyCollector.collect(part.getJSONObject("operand"), varCodes);
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
                        OperandDependencyCollector.collect(branch.getJSONObject("leftOperand"), varCodes);
                        OperandDependencyCollector.collect(branch.getJSONObject("rightOperand"), varCodes);
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
                    if (isInput) OperandDependencyCollector.collect(cases.getJSONObject(i).getJSONObject("valueOperand"), varCodes);
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
        OperandDependencyCollector.collect(obj.getJSONObject("leftOperand"), varCodes);
        OperandDependencyCollector.collect(obj.getJSONObject("rightOperand"), varCodes);

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
        OperandDependencyCollector.collect(rowVar == null ? null : rowVar.getJSONObject("operand"), inputVars);
        OperandDependencyCollector.collect(colVar == null ? null : colVar.getJSONObject("operand"), inputVars);
        collectOperandValues(model.get("rowHeaderOperands"), inputVars);
        collectOperandValues(model.get("colHeaderOperands"), inputVars);
        collectOperandValues(model.get("cellOperands"), inputVars);
    }

    private void extractOutputFromCrossTable(JSONObject model, Set<String> outputVars) {
        JSONObject resultVar = model.getJSONObject("resultVar");
        if (resultVar != null) {
            String varCode = getString(resultVar, "varCode");
            if (varCode != null && !varCode.isEmpty()) outputVars.add(varCode);
        }
        OperandDependencyCollector.collect(resultVar == null ? null : resultVar.getJSONObject("operand"), outputVars);
    }

    // ==================== 评分卡 ====================

    private void extractFromScorecard(JSONObject model, Set<String> inputVars) {
        JSONArray scoreItems = model.getJSONArray("scoreItems");
        if (scoreItems != null) {
            for (int i = 0; i < scoreItems.size(); i++) {
                JSONObject item = scoreItems.getJSONObject(i);
                OperandDependencyCollector.collect(item.getJSONObject("leftOperand"), inputVars);
                OperandDependencyCollector.collect(item.getJSONObject("rightOperand"), inputVars);
                String varCode = getString(item, "condVar");
                if (varCode != null && !varCode.isEmpty()) inputVars.add(varCode);
                // 兼容 condition
                String condition = getString(item, "condition");
                if (condition != null && !condition.isEmpty()) {
                    extractVarCodesFromConditionString(condition, inputVars);
                }
            }
        }
        collectThresholdOperands(model.getJSONArray("thresholds"), inputVars);
    }

    private void extractOutputFromScorecard(JSONObject model, Set<String> outputVars) {
        JSONObject resultVar = model.getJSONObject("resultVar");
        if (resultVar != null) {
            String varCode = getString(resultVar, "varCode");
            if (varCode != null && !varCode.isEmpty()) outputVars.add(varCode);
        }
        OperandDependencyCollector.collect(resultVar == null ? null : resultVar.getJSONObject("operand"), outputVars);
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
        OperandDependencyCollector.collect(dim.getJSONObject("operand"), varCodes);
        String varCode = getString(dim, "varCode");
        if (varCode != null && !varCode.isEmpty()) varCodes.add(varCode);
        // 兼容嵌套结构
        JSONObject condVar = dim.getJSONObject("condVar");
        if (condVar != null) {
            String cv = getString(condVar, "varCode");
            if (cv != null && !cv.isEmpty()) varCodes.add(cv);
        }
        JSONArray segments = dim.getJSONArray("segments");
        if (segments != null) for (int i = 0; i < segments.size(); i++) {
            JSONObject segment = segments.getJSONObject(i);
            OperandDependencyCollector.collect(segment.getJSONObject("valueOperand"), varCodes);
            OperandDependencyCollector.collect(segment.getJSONObject("minOperand"), varCodes);
            OperandDependencyCollector.collect(segment.getJSONObject("maxOperand"), varCodes);
        }
    }

    private void extractOutputFromAdvancedCrossTable(JSONObject model, Set<String> outputVars) {
        JSONObject resultVar = model.getJSONObject("resultVar");
        if (resultVar != null) {
            String varCode = getString(resultVar, "varCode");
            if (varCode != null && !varCode.isEmpty()) outputVars.add(varCode);
        }
        OperandDependencyCollector.collect(resultVar == null ? null : resultVar.getJSONObject("operand"), outputVars);
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
                        OperandDependencyCollector.collect(dim.getJSONObject("operand"), inputVars);
                        String varCode = getString(dim, "varCode");
                        if (varCode != null && !varCode.isEmpty()) inputVars.add(varCode);
                        // 兼容 condition
                        String condition = getString(dim, "condition");
                        if (condition != null && !condition.isEmpty()) {
                            extractVarCodesFromConditionString(condition, inputVars);
                        }
                        JSONArray rules = dim.getJSONArray("rules");
                        if (rules != null) for (int k = 0; k < rules.size(); k++) {
                            JSONArray conditions = rules.getJSONObject(k).getJSONArray("conditions");
                            if (conditions != null) for (int n = 0; n < conditions.size(); n++) {
                                JSONObject conditionItem = conditions.getJSONObject(n);
                                OperandDependencyCollector.collect(conditionItem.getJSONObject("leftOperand"), inputVars);
                                OperandDependencyCollector.collect(conditionItem.getJSONObject("rightOperand"), inputVars);
                            }
                        }
                    }
                }
            }
        }
        collectThresholdOperands(model.getJSONArray("thresholds"), inputVars);
    }

    private void extractOutputFromAdvancedScorecard(JSONObject model, Set<String> outputVars) {
        JSONObject resultVar = model.getJSONObject("resultVar");
        if (resultVar != null) {
            String varCode = getString(resultVar, "varCode");
            if (varCode != null && !varCode.isEmpty()) outputVars.add(varCode);
        }
        OperandDependencyCollector.collect(resultVar == null ? null : resultVar.getJSONObject("operand"), outputVars);
    }

    private void collectThresholdOperands(JSONArray thresholds, Set<String> inputVars) {
        if (thresholds == null) return;
        for (int i = 0; i < thresholds.size(); i++) {
            OperandDependencyCollector.collect(thresholds.getJSONObject(i).getJSONObject("resultOperand"), inputVars);
        }
    }

    private void collectOperandValues(Object value, Set<String> vars) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.getString("kind") != null) OperandDependencyCollector.collect(object, vars);
            else for (Object nested : object.values()) collectOperandValues(nested, vars);
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.size(); i++) collectOperandValues(array.get(i), vars);
        }
    }

    private Set<String> collectExpressionIdentifiers(String expression) {
        Set<String> identifiers = new LinkedHashSet<>();
        String sanitized = sanitizeExpression(expression);
        Matcher matcher = EXPRESSION_IDENTIFIER_PATTERN.matcher(sanitized);
        while (matcher.find()) {
            String token = matcher.group();
            if (isExpressionKeyword(token)) continue;
            if (isFunctionCall(sanitized, matcher.end())) continue;
            if (token.startsWith("java.") || token.startsWith("com.") || token.startsWith("org.")) continue;
            addVarName(identifiers, token);
        }
        return identifiers;
    }

    private void extractIdentifiersFromExpression(String expr, Set<String> varCodes) {
        if (expr == null || expr.isEmpty()) return;
        varCodes.addAll(collectExpressionIdentifiers(expr));
    }

    private boolean isFunctionCall(String source, int end) {
        int i = end;
        while (i < source.length() && Character.isWhitespace(source.charAt(i))) i++;
        return i < source.length() && source.charAt(i) == '(';
    }

    private boolean isExpressionKeyword(String token) {
        if (token == null) return true;
        return EXPRESSION_KEYWORDS.contains(token.toLowerCase());
    }

    private String sanitizeExpression(String expression) {
        StringBuilder sb = new StringBuilder(expression.length());
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            char next = i + 1 < expression.length() ? expression.charAt(i + 1) : '\0';
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
            if (!inDouble && c == '\'' && !isEscaped(expression, i)) {
                inSingle = !inSingle;
                sb.append(' ');
                continue;
            }
            if (!inSingle && c == '"' && !isEscaped(expression, i)) {
                inDouble = !inDouble;
                sb.append(' ');
                continue;
            }
            sb.append(inSingle || inDouble ? (c == '\n' ? '\n' : ' ') : c);
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

    private void addVarName(Set<String> varCodes, String value) {
        String name = trimToNull(value);
        if (name != null && isValidVarName(name) && !isExpressionKeyword(name)) {
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

    private static final class ObjectShapeIndex {

        private final Map<String, Object> schema;
        private final Map<String, Map<String, Object>> schemasByPath;
        private final Map<String, String> leafTypes;

        private ObjectShapeIndex(
                Map<String, Object> schema,
                Map<String, Map<String, Object>> schemasByPath,
                Map<String, String> leafTypes) {
            this.schema = schema;
            this.schemasByPath = schemasByPath;
            this.leafTypes = leafTypes;
        }

        private static ObjectShapeIndex open() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("x-rule-type", "OBJECT");
            schema.put("additionalProperties", true);
            return new ObjectShapeIndex(
                    schema, Collections.emptyMap(), Collections.emptyMap());
        }
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
