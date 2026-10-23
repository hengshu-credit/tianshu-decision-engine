package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.RuleReferenceCatalog;
import com.hengshucredit.rule.model.dto.RuleTestExecutionIdentity;
import com.hengshucredit.rule.model.dto.RuleTestExecutionSource;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleDefinitionVersion;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RuleReferenceCatalogService {
    @Resource private com.hengshucredit.rule.server.security.ConsoleAccessGuard accessGuard;
    @Resource private RuleDefinitionService definitionService;
    @Resource private RuleRevisionMapper revisionMapper;
    @Resource private RuleReferenceNamespaceGuard namespaceGuard;
    @Resource private RuleTestTargetResolver targetResolver;
    public RuleReferenceCatalog getCatalog(Long definitionId, String sourceType, String sourceId) {
        RuleTestExecutionSource source = new RuleTestExecutionSource();
        source.setSourceType(sourceType); source.setSourceId(sourceId); source.validate(null);
        long selectedId;
        try { selectedId = Long.parseLong(sourceId); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("引用目录必须指定有效的来源 ID"); }
        if (selectedId <= 0) throw new IllegalArgumentException("引用目录必须指定有效的来源 ID");
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) throw new IllegalArgumentException("规则不存在");
        RuleRevision revision = null;
        RuleDefinitionVersion version = null;
        if ("REVISION".equals(sourceType)) {
            revision = revisionMapper.selectById(selectedId);
            if (revision == null || !definitionId.equals(revision.getDefinitionId())) throw new IllegalArgumentException("来源修订不存在或不属于当前规则");
        } else if ("VERSION".equals(sourceType)) {
            version = definitionService.getVersionById(definitionId, selectedId);
            if (version == null) throw new IllegalArgumentException("来源版本不存在或不属于当前规则");
            revision = version.getRevisionId() == null ? null : revisionMapper.selectById(version.getRevisionId());
            if (revision != null && !definitionId.equals(revision.getDefinitionId())) throw new IllegalArgumentException("版本修订归属不一致");
        } else if (!"WORKSPACE".equals(sourceType) || !definitionId.equals(selectedId)) {
            throw new IllegalArgumentException("引用目录来源无效");
        }

        RuleReferenceCatalog catalog = new RuleReferenceCatalog();
        RuleTestExecutionIdentity identity = new RuleTestExecutionIdentity();
        identity.setDefinitionId(String.valueOf(definitionId)); identity.setSourceType(sourceType); identity.setSourceId(sourceId);
        if (revision != null) {
            identity.setRevisionId(String.valueOf(revision.getId())); identity.setRevisionNo(revision.getRevisionNo());
            identity.setRevisionState(revision.getState());
            if ("DRAFT".equals(revision.getState())) identity.setLockVersion(revision.getLockVersion());
        }
        if (version != null) identity.setVersionNo(RuleVersionBindingService.businessVersion(version));
        catalog.setExecutionIdentity(identity);
        try {
            var proof = namespaceGuard.inspectNamespace(definition, sourceType, selectedId);
            catalog.setImported(proof.imported()); catalog.setLocalEditingAllowed(!proof.imported());
        } catch (RuleGovernanceException invalidOrigin) {
            catalog.getDiagnostics().add(invalidOrigin.getMessage());
        }

        if ("WORKSPACE".equals(sourceType) || revision != null && "DRAFT".equals(revision.getState())) {
            if (catalog.isLocalEditingAllowed()) {
                catalog.setStatus("AVAILABLE"); catalog.setReferenceNamespace("LOCAL");
            } else catalog.getDiagnostics().add("该草稿的引用来源尚未物化或无法验证；保留原配置，请先通过离线迁移完成明确资源绑定。");
            return catalog;
        }

        try {
            // Namespace proof controls re-editing only. The selected artifact is the historical fact.
            RuleTestTargetResolver.Target target = targetResolver.resolve(definitionId, source, null, null, null);
            catalog.setExecutionIdentity(target.identity()); catalog.setSelectedArtifactImported(target.snapshot().isImported());
            projectSnapshot(catalog, target.snapshot());
            if (accessGuard != null) {
                if (!accessGuard.can("field:view")) { catalog.getVariables().clear(); catalog.getObjectTree().clear(); }
                if (!accessGuard.can("model:view")) catalog.getModels().clear();
                if (!accessGuard.can("function:view")) catalog.getFunctions().clear();
                if (!accessGuard.can("datasource:view")) catalog.getApis().clear();
                if (!accessGuard.can("list:view")) catalog.getLists().clear();
                catalog.getDisplayLabels().entrySet().removeIf(entry -> !accessGuard.can(
                        com.hengshucredit.rule.server.security.ConsoleAccessGuard.resourceModule(entry.getKey().split(":", 2)[0]) + ":view"));
            }
            catalog.setStatus("AVAILABLE");
            if (!catalog.isLocalEditingAllowed()) catalog.getDiagnostics().add("当前展示的是所选制品自身保存的引用。其来源未完成本地资源绑定，只能查看和测试冻结内容；编辑请先完成离线迁移。");
        } catch (IllegalArgumentException | IllegalStateException unavailable) {
            catalog.setLocalEditingAllowed(false);
            catalog.getDiagnostics().add(unavailable.getMessage());
        }
        return catalog;
    }

    private void projectSnapshot(RuleReferenceCatalog catalog, ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot) {
        for (var variable : snapshot.getVariables()) catalog.getVariables().add(row(variable,
                "id", "varCode", "varLabel", "scriptName", "varType", "varSource", "defaultValue", "exampleValue", "valueRange", "recordResult", "status"));
        Map<String, Map<String, Object>> objects = new LinkedHashMap<>();
        for (var object : snapshot.getDataObjects()) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("object", row(object, "id", "objectCode", "objectLabel", "scriptName"));
            group.put("variables", new ArrayList<Map<String, Object>>()); objects.put(object.getId().toString(), group);
        }
        for (var field : snapshot.getDataObjectFields()) {
            String objectId = field.getObjectId() == null ? "missing:" + field.getId() : field.getObjectId().toString();
            Map<String, Object> group = objects.computeIfAbsent(objectId, ignored -> {
                Map<String, Object> object = new LinkedHashMap<>(); object.put("id", field.getObjectId() == null ? null : objectId);
                object.put("objectCode", field.getObjectCode()); object.put("scriptName", field.getObjectScriptName());
                Map<String, Object> value = new LinkedHashMap<>(); value.put("object", object); value.put("variables", new ArrayList<Map<String, Object>>()); return value;
            });
            @SuppressWarnings("unchecked") List<Map<String, Object>> fields = (List<Map<String, Object>>) group.get("variables");
            fields.add(row(field, "id", "objectId", "varCode", "varLabel", "scriptName", "varType", "parentFieldId", "refVariableId", "refObjectId", "referenceMode", "genericType", "recordResult", "status"));
        }
        catalog.getObjectTree().addAll(objects.values());
        for (var model : snapshot.getModels()) {
            Map<String, Object> value = row(model, "id", "modelCode", "modelName", "modelType", "modelFormat", "currentVersion");
            value.put("inputFields", fields(model.getInputFields())); value.put("outputFields", fields(model.getOutputFields())); catalog.getModels().add(value);
        }
        for (var function : snapshot.getFunctions()) catalog.getFunctions().add(row(function, "id", "funcCode", "funcName", "returnType", "paramsJson", "implType"));
        for (var pinned : snapshot.getResourceVersions().values()) {
            var payload = pinned.getJSONObject("payload");
            String type = pinned.getString("refType");
            if ("VARIABLE".equals(type) || "CONSTANT".equals(type)) {
                Map<String, Object> variable = row(payload.toJavaObject(com.hengshucredit.rule.model.entity.RuleVariable.class),
                        "id", "varCode", "varLabel", "scriptName", "varType", "varSource", "defaultValue", "status");
                variable.put("version", pinned.getInteger("version")); catalog.getVariables().add(variable);
            } else if ("FUNCTION".equals(type)) {
                Map<String, Object> function = row(payload.toJavaObject(RuleFunction.class), "id", "funcCode", "funcName", "returnType", "paramsJson", "implType");
                function.put("version", pinned.getInteger("version")); catalog.getFunctions().add(function);
            } else if (type != null && type.startsWith("MODEL")) {
                var metadata = com.alibaba.fastjson.JSON.parseObject(payload.getString("executionSnapshotJson")).getJSONObject("model");
                var model = metadata.toJavaObject(com.hengshucredit.rule.model.entity.RuleModel.class);
                Map<String, Object> value = row(model, "id", "modelCode", "modelName", "modelType", "modelFormat", "currentVersion");
                value.put("inputFields", fields(model.getInputFields())); value.put("outputFields", fields(model.getOutputFields())); catalog.getModels().add(value);
            } else if ("DATA_OBJECT".equals(type) || "DATA_OBJECT_ROOT".equals(type)) {
                Map<String, Object> object = row(payload.toJavaObject(com.hengshucredit.rule.model.entity.RuleDataObject.class), "id", "objectCode", "objectLabel", "scriptName");
                List<Map<String, Object>> fields = new ArrayList<>();
                for (var field : payload.getJSONArray("fields").toJavaList(com.hengshucredit.rule.model.entity.RuleDataObjectField.class)) {
                    Map<String, Object> value = row(field, "id", "objectId", "varCode", "varLabel", "scriptName", "varType", "parentFieldId", "genericType", "status");
                    value.put("version", pinned.getInteger("version")); fields.add(value);
                }
                catalog.getObjectTree().add(Map.of("object", object, "variables", fields));
            } else if ("EXTERNAL_API".equals(type)) {
                var api = payload.toJavaObject(com.hengshucredit.rule.model.entity.RuleExternalApiConfig.class);
                Map<String, Object> metadata = row(api, "id", "apiCode", "apiName");
                metadata.put("resultFields", ExternalApiResponseSchema.catalog(api));
                catalog.getApis().add(metadata);
            }
        }
        for (var rule : snapshot.getNestedRules()) {
            Map<String, Object> value = row(rule, "ruleCode", "ruleName", "modelType", "version", "versionBindingId", "bindingGeneration", "revisionId", "artifactDigest");
            value.put("id", rule.getDefinitionId() == null ? null : rule.getDefinitionId().toString());
            value.put("inputFields", fields(rule.getInputFields())); value.put("outputFields", fields(rule.getOutputFields())); catalog.getRules().add(value);
        }
        for (Map<String, Object> binding : snapshot.getDisplayBindings()) {
            Object id = binding.get("sourceResourceId"); if (id == null) continue;
            String kind = String.valueOf(binding.get("targetResourceType"));
            Map<String, Object> value = new LinkedHashMap<>(); value.put("id", String.valueOf(id));
            if ("EXTERNAL_API".equals(kind)) {
                value.put("apiCode", binding.get("sourceResourceCode")); value.put("apiName", binding.get("sourceResourceName")); catalog.getApis().add(value);
            } else if ("LIST_LIBRARY".equals(kind)) {
                value.put("listCode", binding.get("sourceResourceCode")); value.put("listName", binding.get("sourceResourceName")); catalog.getLists().add(value);
            }
        }
        for (var field : snapshot.getInputFields()) if (field.getRefType() != null && field.getVarId() != null && field.getFieldLabel() != null)
            catalog.getDisplayLabels().put(field.getRefType() + ":" + field.getVarId(), field.getFieldLabel());
        for (var field : snapshot.getOutputFields()) if (field.getRefType() != null && field.getVarId() != null && field.getFieldLabel() != null)
            catalog.getDisplayLabels().putIfAbsent(field.getRefType() + ":" + field.getVarId(), field.getFieldLabel());
    }

    private List<Map<String, Object>> fields(List<?> fields) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (fields != null) for (Object field : fields) rows.add(row(field, "id", "varId", "refType", "modelId", "fieldName", "fieldLabel", "scriptName", "fieldType", "defaultValue", "exampleValue", "validValues", "required", "recordResult"));
        return rows;
    }

    private Map<String, Object> row(Object bean, String... names) {
        Map<String, Object> result = new LinkedHashMap<>();
        var properties = new BeanWrapperImpl(bean);
        // Read only whitelisted properties: never serialize model bytes or function/source implementations just to filter them.
        for (String name : names) if (properties.isReadableProperty(name)) {
            Object value = properties.getPropertyValue(name);
            result.put(name, value != null && ("id".equals(name) || name.endsWith("Id")) ? String.valueOf(value) : value);
        }
        return result;
    }
}
