package com.hengshucredit.rule.server.artifact;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.ArtifactDeployment;
import com.hengshucredit.rule.model.entity.ArtifactResourceBinding;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.ArtifactDeploymentMapper;
import com.hengshucredit.rule.server.mapper.ArtifactResourceBindingMapper;
import com.hengshucredit.rule.server.mapper.DecisionArtifactMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从不可变制品构建一次执行所需的冻结依赖视图。这里不回读变量、模型或函数主表。
 */
@Service
public class ArtifactRuntimeSnapshotService {
    @Resource
    private DecisionArtifactMapper artifactMapper;
    @Resource
    private ArtifactDeploymentMapper deploymentMapper;
    @Resource
    private ArtifactResourceBindingMapper bindingMapper;
    @Resource
    private RuleDataObjectMapper dataObjectMapper;

    private final DecisionArtifactPackageCodec codec = new DecisionArtifactPackageCodec();
    private static final int MAX_DECODED_PACKAGES = 64;
    private static final long MAX_DECODED_BYTES = 64L * 1024 * 1024;
    private final Map<String, CachedPackage> decodedPackages = new LinkedHashMap<>(16, 0.75f, true);
    private long decodedBytes;
    private static final int MAX_MODEL_CONTENTS = 64;
    private static final long MAX_MODEL_CONTENT_BYTES = 32L * 1024 * 1024;
    private final Map<ModelContentKey, String> modelContents = new LinkedHashMap<>(16, 0.75f, true);
    private long modelContentBytes;

    public RuntimeSnapshot load(Long artifactId, Long definitionId, Long executionProjectId) {
        DecisionArtifact artifact = loadArtifact(artifactId);
        if (artifact == null || artifact.getPackageContent() == null) {
            throw new IllegalStateException("已发布规则缺少不可变决策制品");
        }
        DecisionArtifactPackageCodec.DecodedPackage decoded = decodeVerified(artifact);
        if (!same(artifact.getArtifactDigest(), decoded.getArtifactDigest())
                || !same(artifact.getPackageDigest(), decoded.getPackageDigest())) {
            throw new IllegalStateException("已发布决策制品摘要校验失败");
        }

        ArtifactDeployment deployment = loadDeployment(artifactId, definitionId);
        Map<String, Long> bindings = new LinkedHashMap<>();
        if (deployment != null) {
            for (ArtifactResourceBinding binding : safe(loadBindings(deployment.getId()))) {
                if (binding.getComponentId() != null && binding.getTargetResourceId() != null) {
                    bindings.put(binding.getComponentId(), binding.getTargetResourceId());
                }
            }
        }

        return buildSnapshot(decoded, bindings, deployment != null, artifactId, artifact.getArtifactDigest(), executionProjectId);
    }

    /** 与规则制品共用有界解码缓存；模型测试没有部署重绑定或当前目录补齐。 */
    public RuntimeSnapshot loadModelVersion(byte[] content, String artifactDigest, String packageDigest, Long projectId) {
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setPackageContent(content);
        artifact.setArtifactDigest(artifactDigest);
        artifact.setPackageDigest(packageDigest);
        var decoded = decodeVerified(artifact);
        return buildSnapshot(decoded, Collections.emptyMap(), false, null, artifactDigest, projectId);
    }

    private RuntimeSnapshot buildSnapshot(DecisionArtifactPackageCodec.DecodedPackage decoded,
                                          Map<String, Long> bindings, boolean imported,
                                          Long artifactId, String artifactDigest, Long executionProjectId) {
        RuntimeSnapshot snapshot = new RuntimeSnapshot();
        snapshot.imported = imported;
        snapshot.artifactId = artifactId;
        snapshot.artifactDigest = artifactDigest;
        snapshot.modelType = text(decoded.getArtifactPackage().getMetadata().get("modelType"));
        snapshot.bindings.putAll(bindings);
        Map<Long, RuleDataObject> boundDataObjects = new LinkedHashMap<>();
        for (DecisionArtifactPackage.Component component
                : decoded.getArtifactPackage().getComponents().values()) {
            String resourceType = text(component.getMetadata().get("resourceType"));
            if ("rule/model.json".equals(component.getPath())) {
                snapshot.modelJson = new String(component.getContent(), StandardCharsets.UTF_8);
            } else if ("rule/open-api.json".equals(component.getPath())) {
                snapshot.openApiConfigJson = new String(component.getContent(), StandardCharsets.UTF_8);
            } else if ("runtime/compiled.ql".equals(component.getPath())) {
                snapshot.compiledScript = new String(component.getContent(), StandardCharsets.UTF_8);
            } else if ("VARIABLE".equals(resourceType) || "CONSTANT".equals(resourceType)) {
                RuleVariable variable = JSON.parseObject(component.getContent(), RuleVariable.class);
                variable.setProjectId(executionProjectId);
                variable.setScope("PROJECT");
                variable.setStatus(1);
                applyVariableBinding(variable, bindings);
                snapshot.variables.add(variable);
            } else if ("RESOURCE_VERSION".equals(resourceType)) {
                JSONObject version = JSON.parseObject(component.getContent());
                snapshot.resourceVersions.put(version.getString("key"), version);
            } else if ("FUNCTION".equals(resourceType)) {
                RuleFunction function = JSON.parseObject(component.getContent(), RuleFunction.class);
                function.setProjectId(executionProjectId);
                function.setScope("PROJECT");
                function.setStatus(1);
                snapshot.functions.add(function);
            } else if ("MODEL".equals(resourceType)) {
                snapshot.models.add(model(component, executionProjectId,
                        decoded.getPackageDigest()));
            } else if ("DATA_OBJECT".equals(resourceType)) {
                RuleDataObjectField dataObjectField = JSON.parseObject(
                        component.getContent(), RuleDataObjectField.class);
                applyDataObjectBinding(dataObjectField, bindings, boundDataObjects);
                snapshot.dataObjectFields.add(dataObjectField);
                JSONObject field = JSON.parseObject(component.getContent());
                if (field.getString("referencePath") != null) {
                    snapshot.referencePaths.put("DATA_OBJECT:" + field.getLong("id"), field.getString("referencePath"));
                }
            } else if ("RULE".equals(resourceType)) {
                snapshot.nestedRules.add(JSON.parseObject(component.getContent(), NestedRuleSnapshot.class));
            } else if ("BINDING".equals(resourceType)) {
                JSONObject binding = JSON.parseObject(component.getContent());
                Map<String, Object> display = new LinkedHashMap<>();
                for (String key : List.of("sourceResourceId", "sourceResourceCode", "sourceResourceName", "targetResourceType"))
                    if (binding.containsKey(key)) display.put(key, binding.get(key));
                snapshot.displayBindings.add(display);
            } else if ("rule/input-fields.json".equals(component.getPath())) {
                snapshot.inputFields.addAll(JSON.parseArray(
                        new String(component.getContent(), StandardCharsets.UTF_8),
                        RuleDefinitionInputField.class));
            } else if ("rule/output-fields.json".equals(component.getPath())) {
                snapshot.outputFields.addAll(JSON.parseArray(
                        new String(component.getContent(), StandardCharsets.UTF_8),
                        RuleDefinitionOutputField.class));
            }
        }
        return snapshot;
    }

    private void applyDataObjectBinding(RuleDataObjectField field, Map<String, Long> bindings,
                                        Map<Long, RuleDataObject> boundDataObjects) {
        if (field == null || field.getObjectId() == null || bindings == null || bindings.isEmpty()
                || dataObjectMapper == null) return;
        Long targetId = bindings.get("BINDING:DATA_OBJECT:" + field.getObjectId());
        if (targetId == null) return;
        if (!boundDataObjects.containsKey(targetId)) {
            boundDataObjects.put(targetId, dataObjectMapper.selectById(targetId));
        }
        RuleDataObject target = boundDataObjects.get(targetId);
        if (target == null) return;
        field.setObjectSourceType(target.getSourceType());
        field.setObjectSourceContent(target.getSourceContent());
    }

    DecisionArtifactPackageCodec.DecodedPackage decodeVerified(DecisionArtifact artifact) {
        byte[] content = artifact.getPackageContent();
        String digest = Sha256Digests.bytes(content);
        // 每次验证实际字节，不能仅凭数据库里的摘要命中缓存而漏过内容被修改的情况。
        if (!same(digest, artifact.getPackageDigest())) {
            throw new IllegalStateException("已发布决策制品摘要校验失败");
        }
        synchronized (decodedPackages) {
            CachedPackage cached = decodedPackages.get(digest);
            if (cached != null) return cached.decoded();
        }
        // 解压在锁外进行；缓存只保留未绑定的组件，每次执行仍创建独立变量和模型。
        DecisionArtifactPackageCodec.DecodedPackage decoded = codec.decode(content);
        if (!same(decoded.getArtifactDigest(), artifact.getArtifactDigest())) {
            throw new IllegalStateException("已发布决策制品摘要校验失败");
        }
        long weight = CanonicalJson.writeBytes(decoded.getArtifactPackage().getMetadata()).length;
        for (DecisionArtifactPackage.Component component : decoded.getArtifactPackage().getComponents().values()) {
            weight += component.getContent().length + CanonicalJson.writeBytes(component.getMetadata()).length;
        }
        if (weight <= MAX_DECODED_BYTES) {
            synchronized (decodedPackages) {
                CachedPackage existing = decodedPackages.get(digest);
                if (existing != null) return existing.decoded();
                while (!decodedPackages.isEmpty() && (decodedPackages.size() >= MAX_DECODED_PACKAGES
                        || decodedBytes + weight > MAX_DECODED_BYTES)) {
                    var oldest = decodedPackages.entrySet().iterator();
                    decodedBytes -= oldest.next().getValue().weight();
                    oldest.remove();
                }
                decodedPackages.put(digest, new CachedPackage(decoded, weight));
                decodedBytes += weight;
            }
        }
        return decoded;
    }

    private record CachedPackage(DecisionArtifactPackageCodec.DecodedPackage decoded, long weight) { }

    private record ModelContentKey(String packageDigest, String path) { }

    private String modelContent(DecisionArtifactPackage.Component component, String packageDigest) {
        ModelContentKey key = new ModelContentKey(packageDigest, component.getPath());
        synchronized (modelContents) {
            String cached = modelContents.get(key);
            if (cached != null) return cached;
        }
        String content = Base64.getEncoder().encodeToString(component.getContent());
        // 独立预算避免大字符串挤掉已解压包，多模型轮转时不能退化为重复解压。
        // 按每字符两字节保守计量，缓存只持有不可变字符串，不持有执行DTO或模型字节数组。
        long weight = 2L * content.length();
        if (weight <= MAX_MODEL_CONTENT_BYTES) {
            synchronized (modelContents) {
                String existing = modelContents.get(key);
                if (existing != null) return existing;
                while (!modelContents.isEmpty() && (modelContents.size() >= MAX_MODEL_CONTENTS
                        || modelContentBytes + weight > MAX_MODEL_CONTENT_BYTES)) {
                    var oldest = modelContents.entrySet().iterator();
                    modelContentBytes -= 2L * oldest.next().getValue().length();
                    oldest.remove();
                }
                modelContents.put(key, content);
                modelContentBytes += weight;
            }
        }
        return content;
    }

    private RuleModel model(DecisionArtifactPackage.Component component, Long executionProjectId,
                            String packageDigest) {
        Object modelMetadata = component.getMetadata().get("model");
        if (modelMetadata == null) {
            throw new IllegalStateException("模型制品组件缺少冻结元数据: " + component.getPath());
        }
        RuleModel model = JSON.parseObject(JSON.toJSONString(modelMetadata), RuleModel.class);
        model.setProjectId(executionProjectId);
        model.setScope("PROJECT");
        model.setStatus(1);
        model.setModelContent(modelContent(component, packageDigest));
        model.setInputFields(parseList(component.getMetadata().get("inputFields"), RuleModelInputField.class));
        model.setOutputFields(parseList(component.getMetadata().get("outputFields"), RuleModelOutputField.class));
        return model;
    }

    private <T> List<T> parseList(Object value, Class<T> type) {
        if (value == null) return new ArrayList<>();
        JSONArray array = JSON.parseArray(JSON.toJSONString(value));
        List<T> result = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            result.add(array.getObject(i, type));
        }
        return result;
    }

    private void applyVariableBinding(RuleVariable variable, Map<String, Long> bindings) {
        if (variable == null || variable.getSourceConfig() == null || bindings.isEmpty()) return;
        JSONObject config = JSON.parseObject(variable.getSourceConfig());
        Long fallback = bindings.get("BINDING:VARIABLE:" + variable.getId());
        String source = variable.getVarSource() == null ? "" : variable.getVarSource().toUpperCase();
        if ("API".equals(source) || "EXTERNAL".equals(source)) {
            replaceSingle(config, "apiConfigId", "EXTERNAL_API", bindings, fallback);
        } else if ("DB".equals(source) || "DATABASE".equals(source)) {
            replaceSingle(config, config.containsKey("dbDatasourceId") ? "dbDatasourceId" : "datasourceId",
                    "DB_DATASOURCE", bindings, fallback);
        } else if ("SFTP".equals(source)) {
            replaceSingle(config, "sftpDatasourceId", "SFTP", bindings, fallback);
        } else if ("LIST".equals(source)) {
            JSONArray ids = config.getJSONArray("listIds");
            if (ids != null) {
                JSONArray rebound = new JSONArray();
                for (int i = 0; i < ids.size(); i++) {
                    Long sourceId = ids.getLong(i);
                    Long targetId = bindings.get("BINDING:LIST_LIBRARY:" + sourceId);
                    rebound.add(targetId == null ? (fallback == null ? sourceId : fallback) : targetId);
                }
                config.put("listIds", rebound);
            } else if (fallback != null) {
                config.put("listIds", List.of(fallback));
            }
        }
        variable.setSourceConfig(config.toJSONString());
    }

    private void replaceSingle(JSONObject config, String key, String type,
                               Map<String, Long> bindings, Long fallback) {
        Long sourceId = config.getLong(key);
        Long targetId = sourceId == null ? null : bindings.get("BINDING:" + type + ":" + sourceId);
        if (targetId == null) targetId = fallback;
        if (targetId != null) config.put(key, targetId);
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean same(String left, String right) {
        return left != null && left.equals(right);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    protected DecisionArtifact loadArtifact(Long artifactId) {
        return artifactMapper.selectById(artifactId);
    }

    protected ArtifactDeployment loadDeployment(Long artifactId, Long definitionId) {
        if (deploymentMapper == null) return null;
        return deploymentMapper.selectOne(new LambdaQueryWrapper<ArtifactDeployment>()
                .eq(ArtifactDeployment::getArtifactId, artifactId)
                .eq(ArtifactDeployment::getTargetDefinitionId, definitionId)
                .eq(ArtifactDeployment::getStatus, "DEPLOYED")
                .orderByDesc(ArtifactDeployment::getId)
                .last("LIMIT 1"));
    }

    protected List<ArtifactResourceBinding> loadBindings(Long deploymentId) {
        if (bindingMapper == null) return Collections.emptyList();
        return bindingMapper.selectList(new LambdaQueryWrapper<ArtifactResourceBinding>()
                .eq(ArtifactResourceBinding::getDeploymentId, deploymentId)
                .orderByAsc(ArtifactResourceBinding::getId));
    }

    public static final class RuntimeSnapshot {
        private boolean imported;
        public boolean isImported() { return imported; }
        private Long artifactId;
        private String artifactDigest;
        private String modelType;
        private String modelJson;
        private String openApiConfigJson;
        private String compiledScript;
        private final List<RuleVariable> variables = new ArrayList<>();
        private final List<RuleModel> models = new ArrayList<>();
        private final List<RuleFunction> functions = new ArrayList<>();
        private final List<RuleDefinitionInputField> inputFields = new ArrayList<>();
        private final List<RuleDefinitionOutputField> outputFields = new ArrayList<>();
        private final List<RuleDataObjectField> dataObjectFields = new ArrayList<>();
        private final List<NestedRuleSnapshot> nestedRules = new ArrayList<>();
        private final List<Map<String, Object>> displayBindings = new ArrayList<>();
        private final Map<String, Long> bindings = new LinkedHashMap<>();
        private final Map<String, String> referencePaths = new LinkedHashMap<>();
        private final Map<String, JSONObject> resourceVersions = new LinkedHashMap<>();
        public Map<String, JSONObject> getResourceVersions() { return resourceVersions; }

        public Long getArtifactId() { return artifactId; }
        public String getArtifactDigest() { return artifactDigest; }
        public String getModelType() { return modelType; }
        public String getModelJson() { return modelJson; }
        public String getOpenApiConfigJson() { return openApiConfigJson; }
        public String getCompiledScript() { return compiledScript; }
        public List<RuleVariable> getVariables() { return variables; }
        public List<RuleModel> getModels() { return models; }
        public List<RuleFunction> getFunctions() { return functions; }
        public List<RuleDefinitionInputField> getInputFields() { return inputFields; }
        public List<RuleDefinitionOutputField> getOutputFields() { return outputFields; }
        public List<RuleDataObjectField> getDataObjectFields() { return dataObjectFields; }
        public List<NestedRuleSnapshot> getNestedRules() { return nestedRules; }
        public List<Map<String, Object>> getDisplayBindings() { return displayBindings; }
        public Map<String, Long> getBindings() { return bindings; }
        public Map<String, String> getReferencePaths() { return referencePaths; }

        public NestedRuleSnapshot findNestedRule(Long definitionId, String ruleCode) {
            for (NestedRuleSnapshot rule : nestedRules) {
                if (definitionId != null && definitionId.equals(rule.getDefinitionId())) return rule;
                if (definitionId == null && ruleCode != null && ruleCode.equals(rule.getRuleCode())) return rule;
            }
            return null;
        }
    }

    public static final class NestedRuleSnapshot {
        private Long definitionId;
        private String ruleCode;
        private String ruleName;
        private String modelType;
        private Integer version;
        private Long versionBindingId;
        private Long bindingGeneration;
        private Long revisionId;
        private String artifactDigest;
        private String modelJson;
        private String compiledScript;
        private String compiledType;
        private List<RuleDefinitionInputField> inputFields = new ArrayList<>();
        private List<RuleDefinitionOutputField> outputFields = new ArrayList<>();

        public Long getDefinitionId() { return definitionId; }
        public void setDefinitionId(Long definitionId) { this.definitionId = definitionId; }
        public String getRuleCode() { return ruleCode; }
        public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        public String getModelType() { return modelType; }
        public void setModelType(String modelType) { this.modelType = modelType; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public Long getVersionBindingId() { return versionBindingId; }
        public void setVersionBindingId(Long value) { versionBindingId = value; }
        public Long getBindingGeneration() { return bindingGeneration; }
        public void setBindingGeneration(Long value) { bindingGeneration = value; }
        public Long getRevisionId() { return revisionId; }
        public void setRevisionId(Long value) { revisionId = value; }
        public String getArtifactDigest() { return artifactDigest; }
        public void setArtifactDigest(String value) { artifactDigest = value; }
        public String getModelJson() { return modelJson; }
        public void setModelJson(String modelJson) { this.modelJson = modelJson; }
        public String getCompiledScript() { return compiledScript; }
        public void setCompiledScript(String compiledScript) { this.compiledScript = compiledScript; }
        public String getCompiledType() { return compiledType; }
        public void setCompiledType(String compiledType) { this.compiledType = compiledType; }
        public List<RuleDefinitionInputField> getInputFields() { return inputFields; }
        public void setInputFields(List<RuleDefinitionInputField> inputFields) {
            this.inputFields = inputFields == null ? new ArrayList<>() : inputFields;
        }
        public List<RuleDefinitionOutputField> getOutputFields() { return outputFields; }
        public void setOutputFields(List<RuleDefinitionOutputField> outputFields) {
            this.outputFields = outputFields == null ? new ArrayList<>() : outputFields;
        }
    }
}
