package com.hengshucredit.rule.server.artifact;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleModelVersion;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleFunctionMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelVersionMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.hengshucredit.rule.server.service.OperandDependencyCollector;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class RuleDependencyClosureService {
    @Resource
    private RuleDefinitionService definitionService;
    @Resource
    private RuleRevisionMapper revisionMapper;
    @Resource
    private RuleVariableMapper variableMapper;
    @Resource
    private RuleModelMapper modelMapper;
    @Resource
    private RuleModelVersionMapper modelVersionMapper;
    @Resource
    private RuleModelInputFieldMapper modelInputFieldMapper;
    @Resource
    private RuleModelOutputFieldMapper modelOutputFieldMapper;
    @Resource
    private RuleFunctionMapper functionMapper;
    @Resource
    private RuleDataObjectFieldMapper dataObjectFieldMapper;
    @Resource
    private RuleDataObjectMapper dataObjectMapper;
    @Resource
    private RulePublishedMapper publishedMapper;

    public DependencyClosure resolve(Long definitionId, Long revisionId) {
        List<RuleValidationIssue> issues = new ArrayList<>();
        Map<String, ArtifactDependency> dependencies = new TreeMap<>();
        RuleRevision revision = revisionId == null ? null : loadRevision(revisionId);
        if (revisionId != null && revision == null) {
            issues.add(RuleValidationIssue.error("REVISION_NOT_FOUND", "$", "规则修订不存在"));
            return DependencyClosure.of(Collections.emptyList(), issues);
        }
        if (revision != null && !definitionId.equals(revision.getDefinitionId())) {
            issues.add(RuleValidationIssue.error("REVISION_DEFINITION_MISMATCH", "$", "修订不属于指定规则"));
            return DependencyClosure.of(Collections.emptyList(), issues);
        }
        collectDefinition(definitionId, revision, dependencies, issues,
                new LinkedHashSet<>(), new LinkedHashSet<>());
        return DependencyClosure.of(new ArrayList<>(dependencies.values()), issues);
    }

    private void collectDefinition(Long definitionId, RuleRevision revision,
                                   Map<String, ArtifactDependency> dependencies,
                                   List<RuleValidationIssue> issues,
                                   Set<Long> visitingRules, Set<Long> visitedRules) {
        if (!visitingRules.add(definitionId)) {
            issues.add(RuleValidationIssue.error("RULE_DEPENDENCY_CYCLE", "$", "RULE", definitionId,
                    "规则依赖形成循环，ruleId=" + definitionId));
            return;
        }
        if (visitedRules.contains(definitionId)) {
            visitingRules.remove(definitionId);
            return;
        }
        RuleDefinition definition = loadDefinition(definitionId);
        if (definition == null) {
            issues.add(RuleValidationIssue.error("DEPENDENCY_NOT_FOUND", "$", "RULE", definitionId,
                    "规则依赖不存在"));
            visitingRules.remove(definitionId);
            return;
        }
        if (!active(definition.getStatus())) {
            issues.add(RuleValidationIssue.error("INACTIVE_DEPENDENCY", "$", "RULE", definitionId,
                    "规则依赖已停用"));
        }

        String modelJson;
        if (revision != null) {
            modelJson = revision.getModelJson();
        } else {
            RulePublished published = loadPublishedRule(definitionId);
            if (published == null || !active(published.getStatus())) {
                issues.add(RuleValidationIssue.error("RULE_DEPENDENCY_NOT_PUBLISHED", "$",
                        "RULE", definitionId, "被调用规则尚未发布或已下线"));
                visitingRules.remove(definitionId);
                return;
            }
            modelJson = published.getModelJson();
            addRuleSnapshot(definition, published, dependencies);
        }
        if (modelJson == null || modelJson.isBlank()) {
            issues.add(RuleValidationIssue.error("EMPTY_RULE_CONTENT", "$", "RULE", definitionId,
                    "规则内容为空"));
        } else {
            collectStructuredReferences(modelJson, definition.getProjectId(), dependencies,
                    issues, visitingRules, visitedRules);
        }
        for (RuleDefinitionInputField field : safe(loadInputFields(definitionId))) {
            if (active(field.getStatus())) {
                collectFieldReference(field.getRefType(), field.getVarId(), definition.getProjectId(),
                        "input." + field.getFieldName(), dependencies, issues);
            }
        }
        for (RuleDefinitionOutputField field : safe(loadOutputFields(definitionId))) {
            if (active(field.getStatus())) {
                collectFieldReference(field.getRefType(), field.getVarId(), definition.getProjectId(),
                        "output." + field.getFieldName(), dependencies, issues);
            }
        }
        visitingRules.remove(definitionId);
        visitedRules.add(definitionId);
    }

    private void collectStructuredReferences(String modelJson, Long projectId,
                                             Map<String, ArtifactDependency> dependencies,
                                             List<RuleValidationIssue> issues,
                                             Set<Long> visitingRules, Set<Long> visitedRules) {
        Object root;
        try {
            root = JSON.parse(modelJson);
        } catch (RuntimeException e) {
            issues.add(RuleValidationIssue.error("INVALID_MODEL_JSON", "$", "规则模型 JSON 无法解析"));
            return;
        }
        for (OperandDependencyCollector.Reference reference
                : OperandDependencyCollector.collectReferences(root)) {
            String type = normalize(reference.getRefType());
            if ("FUNCTION".equals(type)) {
                if (reference.getRefId() == null) {
                    if (reference.getDisplayCode() == null || reference.getDisplayCode().isBlank()) {
                        issues.add(RuleValidationIssue.error("MISSING_FUNCTION_ID", reference.getPath(),
                                "函数引用既无 functionId 也无内置函数编码"));
                    } else {
                        addBuiltinFunctionRequirement(reference.getDisplayCode(), dependencies);
                    }
                } else {
                    addFunction(reference.getRefId(), projectId, reference.getPath(), dependencies, issues);
                }
            } else if ("RULE".equals(type)) {
                if (reference.getRefId() == null) {
                    issues.add(RuleValidationIssue.error("MISSING_RULE_ID", reference.getPath(),
                            "规则调用缺少 ruleId，禁止通过规则编码关联"));
                } else {
                    collectDefinition(reference.getRefId(), null, dependencies, issues,
                            visitingRules, visitedRules);
                }
            } else {
                collectFieldReference(type, reference.getRefId(), projectId,
                        reference.getPath(), dependencies, issues);
            }
        }
    }

    private void collectFieldReference(String rawType, Long refId, Long projectId, String path,
                                       Map<String, ArtifactDependency> dependencies,
                                       List<RuleValidationIssue> issues) {
        String type = normalize(rawType);
        if (type == null || refId == null) {
            issues.add(RuleValidationIssue.error("MISSING_REFERENCE_ID", path,
                    "引用缺少 refType 或 refId，禁止通过 code/label 解析"));
            return;
        }
        switch (type) {
            case "VARIABLE", "CONSTANT" -> addVariable(type, refId, projectId, path, dependencies, issues);
            case "MODEL" -> addModel(refId, projectId, path, dependencies, issues);
            case "MODEL_OUTPUT" -> addModelOutput(refId, projectId, path, dependencies, issues);
            case "DATA_OBJECT" -> addDataObjectField(refId, projectId, path, dependencies, issues);
            default -> issues.add(RuleValidationIssue.error("UNSUPPORTED_REFERENCE_TYPE", path,
                    type, refId, "不支持的引用类型: " + type));
        }
    }

    private void addVariable(String refType, Long variableId, Long projectId, String path,
                             Map<String, ArtifactDependency> dependencies,
                             List<RuleValidationIssue> issues) {
        RuleVariable variable = loadVariable(variableId);
        if (variable == null) {
            issues.add(RuleValidationIssue.error("DEPENDENCY_NOT_FOUND", path, refType, variableId,
                    "变量或常量不存在"));
            return;
        }
        if (!active(variable.getStatus()) || !available(variable.getScope(), variable.getProjectId(), projectId)) {
            issues.add(RuleValidationIssue.error("INACTIVE_DEPENDENCY", path, refType, variableId,
                    "变量或常量已停用或不属于当前项目"));
            return;
        }
        String actualType = "CONSTANT".equals(variable.getVarSource()) ? "CONSTANT" : "VARIABLE";
        if (!actualType.equals(refType)) {
            issues.add(RuleValidationIssue.error("REFERENCE_TYPE_MISMATCH", path, refType, variableId,
                    "引用类型与资源类型不一致"));
            return;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", variable.getId());
        snapshot.put("projectId", variable.getProjectId());
        snapshot.put("scope", variable.getScope());
        snapshot.put("varCode", variable.getVarCode());
        snapshot.put("varLabel", variable.getVarLabel());
        snapshot.put("scriptName", variable.getScriptName());
        snapshot.put("varType", variable.getVarType());
        snapshot.put("varSource", variable.getVarSource());
        snapshot.put("sourceConfig", variable.getSourceConfig());
        snapshot.put("defaultValue", variable.getDefaultValue());
        snapshot.put("valueRange", variable.getValueRange());
        snapshot.put("exampleValue", variable.getExampleValue());
        snapshot.put("description", variable.getDescription());
        snapshot.put("sortOrder", variable.getSortOrder());
        snapshot.put("status", variable.getStatus());
        addJsonDependency(actualType + ":" + variableId, actualType, variableId, null,
                "variables/" + variableId + ".json", "EMBEDDED", snapshot, dependencies);
        if (externalSource(variable.getVarSource())) {
            addExternalSourceBindings(variable, actualType, path, dependencies, issues);
        }
    }

    private void addExternalSourceBindings(RuleVariable variable, String actualType, String path,
                                           Map<String, ArtifactDependency> dependencies,
                                           List<RuleValidationIssue> issues) {
        JSONObject config;
        try {
            config = variable.getSourceConfig() == null || variable.getSourceConfig().isBlank()
                    ? new JSONObject() : JSON.parseObject(variable.getSourceConfig());
        } catch (RuntimeException error) {
            issues.add(RuleValidationIssue.error("INVALID_SOURCE_CONFIG", path,
                    actualType, variable.getId(), "外部变量 sourceConfig 不是有效 JSON"));
            return;
        }
        String source = variable.getVarSource().toUpperCase(Locale.ROOT);
        if ("API".equals(source) || "EXTERNAL".equals(source)) {
            addExternalBinding(variable, actualType, "EXTERNAL_API", config.getLong("apiConfigId"),
                    path, dependencies, issues);
        } else if ("DB".equals(source) || "DATABASE".equals(source)) {
            addExternalBinding(variable, actualType, "DB_DATASOURCE", config.getLong("datasourceId"),
                    path, dependencies, issues);
        } else if ("LIST".equals(source)) {
            JSONArray ids = config.getJSONArray("listIds");
            if (ids == null || ids.isEmpty()) {
                addExternalBinding(variable, actualType, "LIST_LIBRARY", null,
                        path, dependencies, issues);
                return;
            }
            for (int index = 0; index < ids.size(); index++) {
                addExternalBinding(variable, actualType, "LIST_LIBRARY", ids.getLong(index),
                        path + ".listIds[" + index + "]", dependencies, issues);
            }
        }
    }

    private void addExternalBinding(RuleVariable variable, String actualType,
                                    String targetResourceType, Long sourceResourceId, String path,
                                    Map<String, ArtifactDependency> dependencies,
                                    List<RuleValidationIssue> issues) {
        if (sourceResourceId == null) {
            issues.add(RuleValidationIssue.error("EXTERNAL_BINDING_SOURCE_ID_MISSING", path,
                    actualType, variable.getId(), "外部变量缺少可绑定的源资源 ID"));
            return;
        }
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("sourceComponentId", actualType + ":" + variable.getId());
        binding.put("sourceType", variable.getVarSource());
        binding.put("sourceResourceId", sourceResourceId);
        binding.put("targetResourceType", targetResourceType);
        String componentId = "BINDING:" + targetResourceType + ":" + sourceResourceId;
        addJsonDependency(componentId, "BINDING", sourceResourceId, null,
                "bindings/resources/" + targetResourceType.toLowerCase(Locale.ROOT)
                        + "/" + sourceResourceId + ".json",
                "EXPLICIT_BINDING", binding, dependencies);
    }

    private void addModelOutput(Long outputFieldId, Long projectId, String path,
                                Map<String, ArtifactDependency> dependencies,
                                List<RuleValidationIssue> issues) {
        RuleModelOutputField output = loadModelOutputField(outputFieldId);
        if (output == null || output.getModelId() == null) {
            issues.add(RuleValidationIssue.error("DEPENDENCY_NOT_FOUND", path,
                    "MODEL_OUTPUT", outputFieldId, "模型输出字段不存在"));
            return;
        }
        addModel(output.getModelId(), projectId, path, dependencies, issues);
    }

    private void addModel(Long modelId, Long projectId, String path,
                          Map<String, ArtifactDependency> dependencies,
                          List<RuleValidationIssue> issues) {
        RuleModel model = loadModel(modelId);
        if (model == null) {
            issues.add(RuleValidationIssue.error("DEPENDENCY_NOT_FOUND", path, "MODEL", modelId,
                    "模型不存在"));
            return;
        }
        if (!active(model.getStatus()) || !available(model.getScope(), model.getProjectId(), projectId)) {
            issues.add(RuleValidationIssue.error("INACTIVE_DEPENDENCY", path, "MODEL", modelId,
                    "模型已停用或不属于当前项目"));
            return;
        }
        Integer version = model.getCurrentVersion();
        if (version == null || version <= 0) {
            issues.add(RuleValidationIssue.error("MODEL_VERSION_MISSING", path, "MODEL", modelId,
                    "模型没有可固定的版本"));
            return;
        }
        RuleModelVersion snapshot = loadModelVersion(modelId, version);
        if (snapshot == null) {
            issues.add(RuleValidationIssue.error("MODEL_VERSION_MISSING", path, "MODEL", modelId,
                    "模型版本快照不存在: " + version));
            return;
        }
        if (snapshot.getStatus() != null && !active(snapshot.getStatus())) {
            issues.add(RuleValidationIssue.error("INACTIVE_DEPENDENCY", path, "MODEL", modelId,
                    "模型版本已停用: " + version));
            return;
        }
        byte[] content = decodeModelContent(snapshot.getModelContent() == null
                ? model.getModelContent() : snapshot.getModelContent());
        String digest = validDigest(snapshot.getModelDigest()) ? snapshot.getModelDigest()
                : validDigest(model.getModelDigest()) ? model.getModelDigest() : Sha256Digests.bytes(content);
        String format = firstText(snapshot.getModelFormat(), model.getModelFormat(), "MODEL");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("modelFormat", format);
        metadata.put("inputSchemaJson", firstText(snapshot.getInputSchemaJson(), model.getInputSchemaJson(), null));
        metadata.put("outputSchemaJson", firstText(snapshot.getOutputSchemaJson(), model.getOutputSchemaJson(), null));
        metadata.put("runtimeConstraintsJson", firstText(snapshot.getRuntimeConstraintsJson(),
                model.getRuntimeConstraintsJson(), null));
        Map<String, Object> modelSnapshot = new LinkedHashMap<>();
        modelSnapshot.put("id", model.getId());
        modelSnapshot.put("projectId", model.getProjectId());
        modelSnapshot.put("scope", model.getScope());
        modelSnapshot.put("modelCode", model.getModelCode());
        modelSnapshot.put("modelName", model.getModelName());
        modelSnapshot.put("modelType", model.getModelType());
        modelSnapshot.put("modelFormat", format);
        modelSnapshot.put("modelFileName", firstText(snapshot.getModelFileName(), model.getModelFileName(), null));
        modelSnapshot.put("modelFileSize", snapshot.getModelFileSize() == null
                ? model.getModelFileSize() : snapshot.getModelFileSize());
        modelSnapshot.put("modelDigest", digest);
        modelSnapshot.put("modelConfig", firstText(snapshot.getModelConfig(), model.getModelConfig(), null));
        modelSnapshot.put("inputSchemaJson", firstText(snapshot.getInputSchemaJson(), model.getInputSchemaJson(), null));
        modelSnapshot.put("outputSchemaJson", firstText(snapshot.getOutputSchemaJson(), model.getOutputSchemaJson(), null));
        modelSnapshot.put("validationReportJson", firstText(snapshot.getValidationReportJson(), model.getValidationReportJson(), null));
        modelSnapshot.put("runtimeConstraintsJson", firstText(snapshot.getRuntimeConstraintsJson(), model.getRuntimeConstraintsJson(), null));
        modelSnapshot.put("executionTimeoutMs", model.getExecutionTimeoutMs());
        modelSnapshot.put("currentVersion", version);
        modelSnapshot.put("status", 1);
        metadata.put("model", modelSnapshot);
        metadata.put("inputFields", beanMaps(loadModelInputFields(modelId)));
        metadata.put("outputFields", beanMaps(loadModelOutputFields(modelId)));
        ArtifactDependency dependency = new ArtifactDependency("MODEL:" + modelId + ":" + version,
                "MODEL", modelId, version,
                "models/" + modelId + "/" + version + "." + format.toLowerCase(Locale.ROOT),
                "application/octet-stream", "EMBEDDED", digest, content, metadata);
        dependencies.putIfAbsent(dependency.getComponentId(), dependency);
    }

    private void addFunction(Long functionId, Long projectId, String path,
                             Map<String, ArtifactDependency> dependencies,
                             List<RuleValidationIssue> issues) {
        RuleFunction function = loadFunction(functionId);
        if (function == null) {
            issues.add(RuleValidationIssue.error("DEPENDENCY_NOT_FOUND", path, "FUNCTION", functionId,
                    "函数不存在"));
            return;
        }
        if (!active(function.getStatus()) || !available(function.getScope(), function.getProjectId(), projectId)) {
            issues.add(RuleValidationIssue.error("INACTIVE_DEPENDENCY", path, "FUNCTION", functionId,
                    "函数已停用或不属于当前项目"));
            return;
        }
        String implType = firstText(function.getImplType(), null, "SCRIPT").toUpperCase(Locale.ROOT);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", function.getId());
        snapshot.put("projectId", function.getProjectId());
        snapshot.put("scope", function.getScope());
        snapshot.put("funcCode", function.getFuncCode());
        snapshot.put("funcName", function.getFuncName());
        snapshot.put("paramsJson", function.getParamsJson());
        snapshot.put("returnType", function.getReturnType());
        snapshot.put("implType", implType);
        snapshot.put("status", function.getStatus());
        String mode;
        if ("SCRIPT".equals(implType)) {
            snapshot.put("implScript", function.getImplScript());
            mode = "EMBEDDED";
        } else {
            snapshot.put("implClass", function.getImplClass());
            snapshot.put("implMethod", function.getImplMethod());
            snapshot.put("implBeanName", function.getImplBeanName());
            mode = "RUNTIME_REQUIREMENT";
        }
        addJsonDependency("FUNCTION:" + functionId, "FUNCTION", functionId, null,
                "functions/" + functionId + ".json", mode, snapshot, dependencies);
    }

    private void addBuiltinFunctionRequirement(String functionCode,
                                               Map<String, ArtifactDependency> dependencies) {
        Map<String, Object> snapshot = Map.of("functionCode", functionCode, "builtin", true);
        addJsonDependency("BUILTIN_FUNCTION:" + functionCode, "BUILTIN_FUNCTION", null, null,
                "runtime/builtin-functions/" + Sha256Digests.text(functionCode) + ".json",
                "RUNTIME_REQUIREMENT", snapshot, dependencies);
    }

    private void addDataObjectField(Long fieldId, Long projectId, String path,
                                    Map<String, ArtifactDependency> dependencies,
                                    List<RuleValidationIssue> issues) {
        RuleDataObjectField field = loadDataObjectField(fieldId);
        if (field == null) {
            issues.add(RuleValidationIssue.error("DEPENDENCY_NOT_FOUND", path,
                    "DATA_OBJECT", fieldId, "数据对象字段不存在"));
            return;
        }
        if (!active(field.getStatus()) || !available(field.getScope(), field.getProjectId(), projectId)) {
            issues.add(RuleValidationIssue.error("INACTIVE_DEPENDENCY", path,
                    "DATA_OBJECT", fieldId, "数据对象字段已停用或不属于当前项目"));
            return;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", field.getId());
        snapshot.put("objectId", field.getObjectId());
        snapshot.put("varCode", field.getVarCode());
        snapshot.put("scriptName", field.getScriptName());
        snapshot.put("varType", field.getVarType());
        snapshot.put("genericType", field.getGenericType());
        snapshot.put("refObjectId", field.getRefObjectId());
        addJsonDependency("DATA_OBJECT:" + fieldId, "DATA_OBJECT", fieldId, null,
                "data-object-fields/" + fieldId + ".json", "EMBEDDED", snapshot, dependencies);
        if (field.getObjectId() != null) {
            RuleDataObject object = loadDataObject(field.getObjectId());
            if (object == null || !active(object.getStatus())) {
                issues.add(RuleValidationIssue.error("DEPENDENCY_NOT_FOUND", path,
                        "DATA_OBJECT_ROOT", field.getObjectId(), "字段所属数据对象不存在或已停用"));
            } else if (object.getSourceType() != null && !object.getSourceType().isBlank()) {
                Map<String, Object> binding = new LinkedHashMap<>();
                binding.put("sourceComponentId", "DATA_OBJECT:" + fieldId);
                binding.put("sourceType", object.getSourceType());
                binding.put("sourceObjectId", object.getId());
                binding.put("targetResourceType", "DATA_OBJECT_ROOT");
                addJsonDependency("BINDING:DATA_OBJECT:" + object.getId(), "BINDING", object.getId(),
                        null, "bindings/data-objects/" + object.getId() + ".json",
                        "EXPLICIT_BINDING", binding, dependencies);
            }
        }
    }

    private void addRuleSnapshot(RuleDefinition definition, RulePublished published,
                                 Map<String, ArtifactDependency> dependencies) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("definitionId", definition.getId());
        snapshot.put("ruleCode", published.getRuleCode());
        snapshot.put("ruleName", definition.getRuleName());
        snapshot.put("modelType", definition.getModelType());
        snapshot.put("version", published.getVersion());
        snapshot.put("modelJson", published.getModelJson());
        snapshot.put("compiledScript", published.getCompiledScript());
        snapshot.put("compiledType", published.getCompiledType());
        snapshot.put("inputFields", beanMaps(loadInputFields(definition.getId())));
        snapshot.put("outputFields", beanMaps(loadOutputFields(definition.getId())));
        Integer version = published.getVersion();
        String componentId = "RULE:" + definition.getId() + ":" + (version == null ? 0 : version);
        addJsonDependency(componentId, "RULE", definition.getId(), version,
                "rules/" + definition.getId() + ".json", "EMBEDDED", snapshot, dependencies);
    }

    private void addJsonDependency(String componentId, String type, Long id, Integer version,
                                   String path, String mode, Map<String, Object> snapshot,
                                   Map<String, ArtifactDependency> dependencies) {
        byte[] content = CanonicalJson.writeBytes(snapshot);
        Map<String, Object> metadata = "EXPLICIT_BINDING".equals(mode)
                ? new LinkedHashMap<>(snapshot) : Collections.emptyMap();
        ArtifactDependency dependency = new ArtifactDependency(componentId, type, id, version,
                path, "application/json", mode, Sha256Digests.bytes(content), content,
                metadata);
        dependencies.putIfAbsent(componentId, dependency);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> beanMaps(List<?> values) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object value : safe(values)) {
            result.add(JSON.parseObject(JSON.toJSONString(value), LinkedHashMap.class));
        }
        return result;
    }

    private byte[] decodeModelContent(String content) {
        if (content == null) return new byte[0];
        try {
            return Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException ignored) {
            return content.getBytes(StandardCharsets.UTF_8);
        }
    }

    private boolean externalSource(String source) {
        if (source == null) return false;
        String normalized = source.toUpperCase(Locale.ROOT);
        return "API".equals(normalized) || "DB".equals(normalized)
                || "DATABASE".equals(normalized) || "LIST".equals(normalized)
                || "EXTERNAL".equals(normalized);
    }

    private boolean available(String scope, Long ownerProjectId, Long projectId) {
        return scope == null || "GLOBAL".equalsIgnoreCase(scope)
                || (ownerProjectId != null && ownerProjectId.equals(projectId));
    }

    private boolean active(Integer status) {
        return status == null || status == 1;
    }

    private boolean validDigest(String digest) {
        return digest != null && digest.matches("[0-9a-f]{64}");
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String firstText(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return fallback;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    protected RuleDefinition loadDefinition(Long definitionId) {
        return definitionService.getById(definitionId);
    }

    protected RuleRevision loadRevision(Long revisionId) {
        return revisionMapper.selectById(revisionId);
    }

    protected RulePublished loadPublishedRule(Long definitionId) {
        if (publishedMapper == null) return null;
        return publishedMapper.selectOne(new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId, definitionId)
                .eq(RulePublished::getStatus, 1)
                .last("LIMIT 1"));
    }

    protected RuleDefinitionContent loadContent(Long definitionId) {
        return definitionService.getContent(definitionId);
    }

    protected List<RuleDefinitionInputField> loadInputFields(Long definitionId) {
        return definitionService.listInputFields(definitionId);
    }

    protected List<RuleDefinitionOutputField> loadOutputFields(Long definitionId) {
        return definitionService.listOutputFields(definitionId);
    }

    protected RuleVariable loadVariable(Long variableId) {
        return variableMapper.selectById(variableId);
    }

    protected RuleModel loadModel(Long modelId) {
        return modelMapper.selectById(modelId);
    }

    protected RuleModelVersion loadModelVersion(Long modelId, Integer version) {
        return modelVersionMapper.selectOne(new LambdaQueryWrapper<RuleModelVersion>()
                .eq(RuleModelVersion::getModelId, modelId)
                .eq(RuleModelVersion::getVersion, version));
    }

    protected RuleModelOutputField loadModelOutputField(Long outputFieldId) {
        return modelOutputFieldMapper.selectById(outputFieldId);
    }

    protected List<RuleModelInputField> loadModelInputFields(Long modelId) {
        if (modelInputFieldMapper == null) return Collections.emptyList();
        return modelInputFieldMapper.selectList(new LambdaQueryWrapper<RuleModelInputField>()
                .eq(RuleModelInputField::getModelId, modelId)
                .orderByAsc(RuleModelInputField::getSortOrder));
    }

    protected List<RuleModelOutputField> loadModelOutputFields(Long modelId) {
        if (modelOutputFieldMapper == null) return Collections.emptyList();
        return modelOutputFieldMapper.selectList(new LambdaQueryWrapper<RuleModelOutputField>()
                .eq(RuleModelOutputField::getModelId, modelId)
                .orderByAsc(RuleModelOutputField::getSortOrder));
    }

    protected RuleFunction loadFunction(Long functionId) {
        return functionMapper.selectById(functionId);
    }

    protected RuleDataObjectField loadDataObjectField(Long fieldId) {
        return dataObjectFieldMapper.selectById(fieldId);
    }

    protected RuleDataObject loadDataObject(Long objectId) {
        return dataObjectMapper.selectById(objectId);
    }

    public static final class DependencyClosure {
        private final List<ArtifactDependency> dependencies;
        private final List<RuleValidationIssue> issues;
        private final String dependencyDigest;

        private DependencyClosure(List<ArtifactDependency> dependencies,
                                  List<RuleValidationIssue> issues, String dependencyDigest) {
            this.dependencies = dependencies;
            this.issues = issues;
            this.dependencyDigest = dependencyDigest;
        }

        public static DependencyClosure of(List<ArtifactDependency> dependencies,
                                           List<RuleValidationIssue> issues) {
            List<ArtifactDependency> sorted = new ArrayList<>(dependencies == null
                    ? Collections.emptyList() : dependencies);
            sorted.sort(java.util.Comparator.comparing(ArtifactDependency::getComponentId));
            List<Map<String, Object>> digestSource = sorted.stream().map(dependency -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("componentId", dependency.getComponentId());
                item.put("digest", dependency.getContentDigest());
                item.put("embeddingMode", dependency.getEmbeddingMode());
                return item;
            }).toList();
            return new DependencyClosure(List.copyOf(sorted), List.copyOf(issues == null
                    ? Collections.emptyList() : issues),
                    Sha256Digests.bytes(CanonicalJson.writeBytes(digestSource)));
        }

        public List<ArtifactDependency> getDependencies() {
            return dependencies;
        }

        public List<RuleValidationIssue> getIssues() {
            return issues;
        }

        public String getDependencyDigest() {
            return dependencyDigest;
        }

        public boolean hasErrors() {
            return issues.stream().anyMatch(issue -> "ERROR".equals(issue.getSeverity()));
        }
    }
}
