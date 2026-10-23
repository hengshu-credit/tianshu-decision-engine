package com.hengshucredit.rule.server.transfer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.governance.*;
import com.hengshucredit.rule.server.mapper.GovernedResourceMapper;
import com.hengshucredit.rule.server.security.ConsoleAccessGuard;
import com.hengshucredit.rule.server.service.ResourceVersionService;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleVersionBindingService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;

/** Resolves source metadata only during export, and target identities only during preview/import. */
@Service
public class TransferVersionService {
    @Resource private ResourceVersionService resources;
    @Resource private GovernanceApprovalService approvals;
    @Resource private GovernanceResourceBootstrapService bootstrap;
    @Resource private GovernedResourceMapper governedResources;
    @Resource private RuleVersionBindingService rules;
    @Resource private RuleDefinitionService definitions;
    @Resource private ConsoleAccessGuard access;

    static String ownerType(String type) {
        return switch (type) { case "CONSTANT" -> "VARIABLE"; case "MODEL_OUTPUT" -> "MODEL"; case "DATA_OBJECT_ROOT" -> "DATA_OBJECT"; default -> type; };
    }

    public List<Map<String, Object>> describe(Object configuration) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var reference : TransferVersionReferences.scan(configuration)) {
            access.requireResource(ownerType(reference.refType()), "view");
            Map<String, Object> row = reference.descriptor();
            if ("LATEST".equals(reference.versionSource())) {
                row.put("ownerId", reference.refId()); rows.add(row); continue;
            }
            if ("RULE".equals(reference.refType())) {
                var version = rules.resolveSnapshot(reference.refId(), reference.versionId());
                row.put("ownerId", reference.refId()); row.put("version", RuleVersionBindingService.businessVersion(version));
                row.put("sourceSnapshotId", version.getId()); row.put("digest", version.getArtifactDigest());
            } else {
                var metadata = resources.field(reference.refType(), reference.refId(), reference.versionId(),
                        "SNAPSHOT".equals(reference.versionSource()) ? null : reference.versionSource());
                row.put("ownerId", metadata.get("versionOwnerId")); row.put("version", metadata.get("displayVersion"));
                row.put("label", metadata.getOrDefault("label", metadata.get("funcName")));
                row.put("valueType", metadata.getOrDefault("valueType", metadata.get("returnType")));
                if ("FUNCTION".equals(reference.refType())) row.put("paramsJson", metadata.get("paramsJson"));
            }
            rows.add(row);
        }
        return rows;
    }

    /** Only public version/field metadata is returned; no scripts, credentials or binary model files. */
    public List<Map<String, Object>> candidates(String refType, Long ownerId) {
        String type = ownerType(refType); access.requireResource(type, "view");
        if ("RULE".equals(type)) {
            return rules.publishedVersions(ownerId).stream().filter(row -> !Boolean.FALSE.equals(row.get("available"))).map(row -> {
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("versionId", row.get("bindingId")); option.put("snapshotId", row.get("id"));
                option.put("version", row.get("version")); option.put("fields", List.of()); return option;
            }).toList();
        }
        bootstrap.ensure(type, ownerId);
        return ResourceVersionLines.heads(approvals.versions(type, ownerId)).stream().filter(ResourceVersionLines::published).map(version -> {
            JSONObject payload = JSON.parseObject(version.getSnapshotJson());
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("versionId", version.getId()); option.put("snapshotId", version.getId());
            option.put("version", version.getDisplayVersion()); option.put("revision", version.getVersionNo());
            option.put("fields", fields(refType, payload)); return option;
        }).toList();
    }

    private List<Map<String, Object>> fields(String type, JSONObject payload) {
        String collection = "MODEL_OUTPUT".equals(type) ? "outputFields" : "DATA_OBJECT".equals(type) ? "fields" : null;
        if (collection == null || payload.getJSONArray(collection) == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object raw : payload.getJSONArray(collection)) {
            JSONObject field = (JSONObject) JSON.toJSON(raw);
            Map<String, Object> row = new LinkedHashMap<>(); row.put("id", field.get("id"));
            row.put("label", first(field, "varLabel", "fieldLabel", "varCode", "fieldName"));
            row.put("valueType", first(field, "varType", "fieldType")); result.add(row);
        }
        return result;
    }

    public List<Map<String, Object>> preview(List<TransferBundle.Resource> selected, TransferImportOptions options) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var resource : selected) for (Map<String, Object> source : sourceDescriptors(resource)) {
            Map<String, Object> row = new LinkedHashMap<>(source);
            row.put("targetResourceType", ownerType(String.valueOf(source.get("refType"))));
            var binding = options.normalizedVersionBindings().get(String.valueOf(source.get("referenceKey")));
            row.put("binding", binding);
            Long projectId = options.projectBindings() == null ? options.targetProjectId()
                    : options.projectBindings().getOrDefault(String.valueOf(resource.configuration().get("projectId")), options.targetProjectId());
            try { row.put("target", target(source, binding, options.normalizedScope(), projectId)); row.put("valid", true); }
            catch (IllegalArgumentException | IllegalStateException invalid) { row.put("valid", false); row.put("message", invalid.getMessage()); }
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> sourceDescriptors(TransferBundle.Resource resource) {
        if (!resource.key().startsWith("RULE_VERSION:")) return TransferVersionReferences.descriptors(resource);
        Map<String, Object> row = new LinkedHashMap<>(); row.put("referenceKey", resource.key()); row.put("sourceResourceKey", resource.key());
        row.put("refType", "RULE"); row.put("refId", resource.configuration().get("definitionId")); row.put("ownerId", resource.configuration().get("definitionId"));
        row.put("versionId", TransferKey.parse(resource.key()).id()); row.put("version", resource.configuration().get("versionNo")); row.put("versionSource", "RULE_BINDING");
        return List.of(row);
    }

    public void validate(List<TransferBundle.Resource> selected, TransferImportOptions options) {
        for (var row : preview(selected, options)) if (!Boolean.TRUE.equals(row.get("valid")))
            throw new IllegalArgumentException(row.get("referenceKey") + ": " + row.get("message"));
    }

    public Long bindLegacyRule(TransferBundle.Resource source, TransferImportOptions options, Map<String, Long> idMap, Long projectId) {
        var descriptor = sourceDescriptors(source).get(0);
        var binding = options.normalizedVersionBindings().get(source.key());
        target(descriptor, binding, options.normalizedScope(), projectId);
        Long owner = idMap.get("RULE:" + descriptor.get("ownerId"));
        if (owner != null && !owner.equals(binding.resourceId())) throw new IllegalArgumentException("目标规则版本与资源关联不一致");
        return binding.versionId();
    }

    public Map<String, Object> rewrite(Map<String, Object> configuration, TransferBundle.Resource source,
                                       TransferImportOptions options, Map<String, Long> idMap, Long projectId) {
        Map<String, Map<String, Object>> descriptors = new LinkedHashMap<>();
        TransferVersionReferences.descriptors(source).forEach(row -> descriptors.put(String.valueOf(row.get("path")), row));
        Map<String, Object> mappings = new LinkedHashMap<>();
        Map<String, String> consistent = new HashMap<>();
        Map<String, List<ScriptEdit>> scriptEdits = new LinkedHashMap<>();
        for (var reference : TransferVersionReferences.scan(source.configuration())) {
            var descriptor = descriptors.get(reference.path());
            var binding = options.normalizedVersionBindings().get(reference.key(source.key()));
            Map<String, Object> target = target(descriptor, binding, "GLOBAL".equals(configuration.get("scope")) ? "GLOBAL" : options.normalizedScope(), projectId);
            String type = ownerType(reference.refType());
            Object ownerId = descriptor.get("ownerId");
            Long mappedOwner = ownerId == null ? null : idMap.get(type + ":" + ownerId);
            if (mappedOwner != null && !mappedOwner.equals(binding.resourceId())) throw new IllegalArgumentException("固定版本与资源映射的目标归属不一致: " + reference.key(source.key()));
            String sourceLine = type + ":" + Objects.toString(ownerId, String.valueOf(reference.refId())) + ":" + reference.versionSource() + ":" + reference.versionId();
            String targetLine = binding.resourceId() + ":" + binding.versionId();
            String previous = consistent.putIfAbsent(sourceLine, targetLine);
            if (previous != null && !previous.equals(targetLine)) throw new IllegalArgumentException("同一源版本的字段必须绑定同一个目标版本");
            if (reference.scriptPath() != null) {
                var edits = scriptEdits.computeIfAbsent(reference.scriptPath(), ignored -> new ArrayList<>());
                edits.add(new ScriptEdit(reference.idStart(), reference.idEnd(), JSON.toJSONString(String.valueOf(target.get("refId")))));
                if (reference.versionStart() != null) edits.add(new ScriptEdit(reference.versionStart(), reference.versionEnd(), JSON.toJSONString(("RULE".equals(type) ? "" : "BUSINESS_VERSION:") + binding.versionId())));
                mappings.put(reference.key(source.key()), target); continue;
            }
            @SuppressWarnings("unchecked") Map<String, Object> operand = new LinkedHashMap<>((Map<String, Object>) (reference.path().isEmpty() ? configuration : TransferJsonPath.read(configuration, reference.path())));
            operand.put(reference.idField(), target.get("refId")); operand.put(reference.versionField(), binding.versionId());
            operand.put("versionOwnerId", binding.resourceId()); operand.put("version", target.get("version")); operand.put("displayVersion", target.get("version"));
            if (!"RULE".equals(type)) operand.put("versionSource", "BUSINESS_VERSION");
            if (target.get("code") != null) operand.put("FUNCTION".equals(type) ? "functionCode" : "code", target.get("code"));
            if (target.get("label") != null) operand.put("label", target.get("label"));
            if (reference.path().isEmpty()) { configuration.clear(); configuration.putAll(operand); }
            else TransferJsonPath.replace(configuration, reference.path(), operand);
            for (String alias : reference.aliases()) TransferJsonPath.replace(configuration, alias, target.get("refId"));
            mappings.put(reference.key(source.key()), target);
        }
        scriptEdits.forEach((path, edits) -> {
            StringBuilder text = new StringBuilder(String.valueOf(TransferJsonPath.read(configuration, path)));
            edits.stream().sorted(Comparator.comparingInt(ScriptEdit::start).reversed()).forEach(edit -> text.replace(edit.start(), edit.end(), edit.value()));
            TransferJsonPath.replace(configuration, path, text.toString());
        });
        return mappings;
    }

    private record ScriptEdit(int start, int end, String value) { }

    protected Map<String, Object> target(Map<String, Object> source, TransferVersionBinding binding, String scope, Long projectId) {
        boolean latest = "LATEST".equals(source.get("versionSource"));
        if (binding == null || binding.resourceId() == null || !latest && (binding.versionId() == null || binding.expectedSnapshotId() == null))
            throw new IllegalArgumentException("请选择目标资源、版本和对应字段，并重新预检");
        String refType = String.valueOf(source.get("refType")), type = ownerType(refType);
        access.requireResource(type, "view");
        Map<String, Object> result = new LinkedHashMap<>(); result.put("resourceId", binding.resourceId()); result.put("versionId", binding.versionId());
        if ("RULE".equals(type)) {
            RuleDefinition definition = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                    && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                    ? definitions.getOne(new LambdaQueryWrapper<RuleDefinition>().eq(RuleDefinition::getId, binding.resourceId()).last("FOR UPDATE"))
                    : definitions.getById(binding.resourceId());
            if (definition == null || Integer.valueOf(-1).equals(definition.getStatus())) throw new IllegalArgumentException("目标规则不存在或已删除");
            checkScope(definition.getScope(), definition.getProjectId(), scope, projectId);
            result.put("ownerLabel", definition.getRuleName()); result.put("code", definition.getRuleCode());
            if (latest) {
                if (!Integer.valueOf(1).equals(definition.getStatus()) || definition.getPublishedVersion() == null) throw new IllegalArgumentException("目标规则尚未发布");
                result.put("refId", binding.resourceId()); result.put("mode", "LATEST"); return result;
            }
            var version = rules.resolveSnapshot(binding.resourceId(), binding.versionId());
            if (!Objects.equals(version.getId(), binding.expectedSnapshotId())) throw new IllegalStateException("目标规则版本已被覆盖，请重新预检");
            if (version.getArtifactId() == null || version.getRevisionId() == null) throw new IllegalArgumentException("目标规则版本缺少执行制品");
            result.put("refId", binding.resourceId()); result.put("snapshotId", version.getId()); result.put("version", RuleVersionBindingService.businessVersion(version));
            result.put("digest", version.getArtifactDigest()); return result;
        }
        GovernedResource owner = governedResources.selectOne(new LambdaQueryWrapper<GovernedResource>()
                .eq(GovernedResource::getResourceType, type).eq(GovernedResource::getResourceId, binding.resourceId())
                .last(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                        && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly(), "FOR UPDATE"));
        if (owner == null || "DELETED".equals(owner.getEffectiveStatus())) throw new IllegalArgumentException("目标资源不存在或已删除");
        var version = resources.configurationVersion(type, binding.resourceId(), binding.versionId());
        if (!version.getId().equals(binding.expectedSnapshotId())) throw new IllegalStateException("目标资源版本已被覆盖，请重新预检");
        JSONObject payload = JSON.parseObject(version.getSnapshotJson());
        result.put("ownerLabel", first(payload, "varLabel", "objectLabel", "modelName", "funcName", "apiName", "varCode", "objectCode", "modelCode", "funcCode", "apiCode"));
        if ("EXTERNAL_API".equals(type)) {
            // APIs inherit scope from their datasource; the runtime resolver validates this exact version's owner.
            var call = new com.hengshucredit.rule.core.engine.ResourceVersionCall(refType, binding.resourceId(), binding.versionId(), "", List.of(), null, null, "BUSINESS_VERSION");
            resources.validateScope(call, "GLOBAL".equals(scope) ? 0L : projectId);
        } else checkScope(payload.getString("scope"), payload.getLong("projectId"), scope, projectId);
        Long refId = binding.resourceId(); JSONObject field = payload;
        if (Set.of("DATA_OBJECT", "MODEL_OUTPUT").contains(refType)) {
            if (binding.fieldId() == null) throw new IllegalArgumentException("请选择目标版本中的字段");
            String collection = "MODEL_OUTPUT".equals(refType) ? "outputFields" : "fields";
            field = payload.getJSONArray(collection) == null ? null : payload.getJSONArray(collection).stream()
                    .map(raw -> (JSONObject) JSON.toJSON(raw)).filter(item -> Objects.equals(item.getLong("id"), binding.fieldId())).findFirst().orElse(null);
            if (field == null) throw new IllegalArgumentException("所选字段不属于目标版本");
            refId = binding.fieldId();
        }
        if ("CONSTANT".equals(refType) && !"CONSTANT".equals(payload.getString("varSource"))) throw new IllegalArgumentException("目标版本不是常量");
        String valueType = "MODEL".equals(refType) ? "OBJECT" : first(field, "varType", "fieldType", "returnType");
        if (source.get("valueType") != null && valueType != null && !String.valueOf(source.get("valueType")).equalsIgnoreCase(valueType))
            throw new IllegalArgumentException("目标版本字段类型与源版本不兼容");
        if ("FUNCTION".equals(type) && source.get("paramsJson") != null && !parameterTypes(String.valueOf(source.get("paramsJson"))).equals(parameterTypes(payload.getString("paramsJson"))))
            throw new IllegalArgumentException("目标函数版本的参数契约不兼容");
        result.put("refId", refId); result.put("snapshotId", version.getId()); result.put("version", version.getDisplayVersion()); result.put("digest", version.getSnapshotDigest());
        result.put("code", first(field, "scriptName", "varCode", "modelCode", "funcCode", "apiCode", "fieldName"));
        result.put("label", first(field, "varLabel", "modelName", "funcName", "apiName", "fieldLabel")); return result;
    }

    private List<String> parameterTypes(String json) {
        if (json == null || json.isBlank()) return List.of();
        return JSON.parseArray(json).stream().map(value -> (JSONObject) JSON.toJSON(value)).map(value -> Objects.toString(value.get("type"), Objects.toString(value.get("varType"), ""))).toList();
    }
    private static void checkScope(String targetScope, Long targetProject, String scope, Long projectId) {
        if (!"GLOBAL".equals(targetScope) && ("GLOBAL".equals(scope) || projectId == null || !Objects.equals(projectId, targetProject)))
            throw new IllegalArgumentException("目标版本不属于导入项目或全局范围");
    }
    private static String first(JSONObject value, String... keys) {
        for (String key : keys) { String text = value.getString(key); if (text != null && !text.isBlank()) return text; } return null;
    }
}
