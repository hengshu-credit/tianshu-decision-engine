package com.hengshucredit.rule.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengshucredit.rule.model.dto.RuleTestSchema;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelVersion;
import com.hengshucredit.rule.server.artifact.ArtifactDependency;
import com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.DecisionArtifactPackage;
import com.hengshucredit.rule.server.artifact.DecisionArtifactPackageCodec;
import com.hengshucredit.rule.server.artifact.RuleDependencyClosureService;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.derived.DerivedVariableService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 模型版本测试只消费该版本保存的资源；没有完整快照时不回读当前配置补齐。 */
@Service
public class ModelVersionTestService {
    @Resource private RuleDependencyClosureService dependencyClosure;
    @Resource private ArtifactRuntimeSnapshotService runtimeSnapshots;
    @Resource @Lazy private RuleTestSchemaService schemas;
    @Resource @Lazy private VariableSourceResolver variableSourceResolver;
    @Resource private ObjectMapper objectMapper;
    private final DecisionArtifactPackageCodec codec = new DecisionArtifactPackageCodec();
    private final RuleTestExpectationService exactJson = new RuleTestExpectationService();

    public String capture(RuleModel model) {
        var closure = dependencyClosure.resolveModelSnapshot(model);
        List<String> unavailable = new ArrayList<>();
        closure.getIssues().stream().filter(issue -> "ERROR".equals(issue.getSeverity()))
                .forEach(issue -> unavailable.add(issue.getMessage()));
        DecisionArtifactPackage pack = new DecisionArtifactPackage();
        pack.putMetadata("rootModelId", model.getId());
        pack.putMetadata("rootModelVersion", model.getCurrentVersion());
        for (ArtifactDependency dependency : closure.getDependencies()) {
            Map<String, Object> metadata = new LinkedHashMap<>(dependency.getMetadata());
            metadata.put("resourceType", dependency.getResourceType());
            metadata.put("componentId", dependency.getComponentId());
            metadata.put("resourceId", dependency.getResourceId());
            metadata.put("version", dependency.getVersion());
            metadata.put("embeddingMode", dependency.getEmbeddingMode());
            pack.addComponent(dependency.getComponentPath(), dependency.getMediaType(), dependency.getContent(), metadata);
            if ("EXPLICIT_BINDING".equals(dependency.getEmbeddingMode())) {
                unavailable.add("该版本包含尚未冻结执行配置的外部资源：" + dependency.getComponentId());
            }
            if ("MODEL".equals(dependency.getResourceType()) && !model.getId().equals(dependency.getResourceId())
                    && !metadata.containsKey("configurationDigest")) {
                unavailable.add("依赖模型版本缺少完整配置快照：" + dependency.getComponentId());
            }
            if ("DATA_OBJECT".equals(dependency.getResourceType())) {
                Object source = CanonicalJson.readMap(dependency.getContent()).get("objectSourceType");
                if (source != null && Set.of("API", "DB", "DATABASE").contains(String.valueOf(source).toUpperCase(java.util.Locale.ROOT))) {
                    unavailable.add("该版本的数据对象来源尚无独立执行快照：" + dependency.getComponentId());
                }
            }
        }
        byte[] encoded = codec.encode(pack);
        var decoded = codec.decode(encoded);
        RuleTestSchema schema = null;
        try {
            var runtime = runtimeSnapshots.loadModelVersion(encoded, decoded.getArtifactDigest(), decoded.getPackageDigest(), model.getProjectId());
            schema = schemas.buildModelSnapshot(model, runtime);
        } catch (IllegalArgumentException error) {
            unavailable.add("模型版本输入结构无法完整解析：" + error.getMessage());
        }
        Map<String, Object> metadata = exactJson.readJsonObject(CanonicalJson.write(model), "模型元信息");
        metadata.remove("modelContent");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("formatVersion", 1);
        snapshot.put("model", metadata);
        snapshot.put("schema", schema);
        snapshot.put("packageContent", Base64.getEncoder().encodeToString(encoded));
        snapshot.put("packageDigest", decoded.getPackageDigest());
        snapshot.put("artifactDigest", decoded.getArtifactDigest());
        snapshot.put("unavailableReasons", unavailable.stream().distinct().toList());
        return CanonicalJson.write(snapshot);
    }

    public Map<String, Object> context(RuleModelVersion version) {
        Loaded loaded = load(version);
        Map<String, Object> result = attribution(version, loaded.model());
        Map<String, Object> model = exactJson.readJsonObject(CanonicalJson.write(loaded.metadata()), "模型元信息");
        model.remove("modelContent");
        // 只转换实体身份字段；样例和模型业务配置保持原类型与原文。
        for (String key : List.of("id", "projectId")) if (model.get(key) != null) model.put(key, String.valueOf(model.get(key)));
        for (String collection : List.of("inputFields", "outputFields")) {
            if (model.get(collection) instanceof List<?> fields) for (Object item : fields) {
                if (item instanceof Map<?, ?> raw) {
                    @SuppressWarnings("unchecked") Map<String, Object> field = (Map<String, Object>) raw;
                    for (String key : List.of("id", "modelId", "varId")) {
                        if (field.get(key) != null) field.put(key, String.valueOf(field.get(key)));
                    }
                }
            }
        }
        result.put("model", model);
        result.put("schema", loaded.schema());
        return result;
    }

    public Map<String, Object> execute(RuleModelVersion version, Map<String, Object> params) {
        return execute(version, params, VariableResolveOptions.defaults());
    }

    public RuleModel executableModel(RuleModelVersion version) {
        return load(version).model();
    }

    public Map<String, Object> execute(RuleModelVersion version, Map<String, Object> params, VariableResolveOptions options) {
        Loaded loaded = load(version);
        var runtime = loaded.runtime();
        RuleModel model = loaded.model();
        options.setRequiredScriptNames(Set.of(model.getModelCode()));
        options.setRequiredNamesUpstreamOnly(true);
        if (options.getExecutionProjectId() == null) options.setExecutionProjectId(model.getProjectId());
        options.setRuntimeSnapshot(runtime);
        DerivedVariableService.prepareSnapshot(options, null, runtime);
        variableSourceResolver.resolveIntoSnapshot(runtime.getVariables(), runtime.getModels(), runtime.getFunctions(),
                params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params), options);
        Map<String, Object> result = new LinkedHashMap<>(options.getInvocationCache().resolve("MODEL:" + model.getId(),
                () -> { throw new IllegalStateException("选定模型版本未进入执行计划"); }));
        result.putAll(attribution(version, model));
        return result;
    }

    private Map<String, Object> attribution(RuleModelVersion version, RuleModel model) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelId", String.valueOf(version.getModelId()));
        result.put("modelCode", model.getModelCode());
        result.put("modelName", model.getModelName());
        result.put("projectId", model.getProjectId() == null ? null : String.valueOf(model.getProjectId()));
        result.put("projectCode", model.getProjectCode());
        result.put("version", version.getVersion());
        result.put("modelDigest", model.getModelDigest());
        result.put("configurationDigest", version.getExecutionSnapshotDigest());
        return result;
    }

    private Loaded load(RuleModelVersion version) {
        return load(version, true);
    }

    public RuleModel restoreModel(RuleModelVersion version) {
        return restoreModel(version, false);
    }

    public RuleModel restoreModel(RuleModelVersion version, boolean preserveFieldIds) {
        Loaded loaded = load(version, false);
        RuleModel restored = loaded.metadata();
        restored.setModelContent(loaded.model().getModelContent());
        // 字段是配置内容；历史物理行ID不能覆盖当前行，审批落库后重新捕获实际ID。
        restored.getInputFields().forEach(field -> { if (!preserveFieldIds) field.setId(null); field.setModelId(restored.getId()); });
        restored.getOutputFields().forEach(field -> { if (!preserveFieldIds) field.setId(null); field.setModelId(restored.getId()); });
        return restored;
    }

    private Loaded load(RuleModelVersion version, boolean requireExecutable) {
        if (version == null) throw new IllegalArgumentException("模型版本不存在");
        String json = version.getExecutionSnapshotJson();
        if (json == null || json.isBlank() || version.getExecutionSnapshotDigest() == null) {
            throw new IllegalArgumentException("该历史模型版本缺少完整执行快照，不能执行；请保存并审批新的模型配置版本");
        }
        if (!Sha256Digests.text(json).equals(version.getExecutionSnapshotDigest())) {
            throw new IllegalArgumentException("模型版本执行快照摘要校验失败");
        }
        Map<String, Object> snapshot = exactJson.readJsonObject(json, "模型版本快照");
        if (!"1".equals(String.valueOf(snapshot.get("formatVersion")))) throw new IllegalArgumentException("模型版本执行快照格式不支持");
        if (!(snapshot.get("model") instanceof Map<?, ?> rawModel)
                || !String.valueOf(version.getModelId()).equals(String.valueOf(rawModel.get("id")))
                || !String.valueOf(version.getVersion()).equals(String.valueOf(rawModel.get("currentVersion")))) {
            throw new IllegalArgumentException("模型版本执行快照归属不匹配");
        }
        Object reasons = snapshot.get("unavailableReasons");
        if (requireExecutable && reasons instanceof List<?> list && !list.isEmpty()) {
            throw new IllegalArgumentException("该模型版本暂不能完整复现：" + String.join("；", list.stream().map(String::valueOf).toList()));
        }
        if (requireExecutable && !(snapshot.get("schema") instanceof Map<?, ?>)) throw new IllegalArgumentException("模型版本缺少冻结测试字段");
        RuleTestSchema schema = objectMapper.convertValue(snapshot.get("schema"), RuleTestSchema.class);
        RuleModel modelMetadata = objectMapper.convertValue(rawModel, RuleModel.class);
        byte[] content;
        try { content = Base64.getDecoder().decode(String.valueOf(snapshot.get("packageContent"))); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("模型版本执行制品内容损坏", error); }
        var runtime = runtimeSnapshots.loadModelVersion(content, String.valueOf(snapshot.get("artifactDigest")),
                String.valueOf(snapshot.get("packageDigest")), modelMetadata.getProjectId());
        RuleModel root = runtime.getModels().stream().filter(model -> version.getModelId().equals(model.getId())
                && version.getVersion().equals(model.getCurrentVersion())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("模型版本执行制品缺少对应模型"));
        if (!java.util.Objects.equals(root.getModelDigest(), modelMetadata.getModelDigest())) {
            throw new IllegalArgumentException("模型版本文件摘要不一致");
        }
        if (!root.getModelDigest().equals(Sha256Digests.bytes(Base64.getDecoder().decode(root.getModelContent())))) {
            throw new IllegalArgumentException("模型版本文件内容与摘要不一致");
        }
        return new Loaded(root, modelMetadata, schema, runtime);
    }

    private record Loaded(RuleModel model, RuleModel metadata, RuleTestSchema schema, ArtifactRuntimeSnapshotService.RuntimeSnapshot runtime) { }
}
