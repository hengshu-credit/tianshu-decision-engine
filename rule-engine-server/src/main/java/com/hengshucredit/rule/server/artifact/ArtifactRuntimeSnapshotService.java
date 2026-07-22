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
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.ArtifactDeploymentMapper;
import com.hengshucredit.rule.server.mapper.ArtifactResourceBindingMapper;
import com.hengshucredit.rule.server.mapper.DecisionArtifactMapper;
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

    private final DecisionArtifactPackageCodec codec = new DecisionArtifactPackageCodec();

    public RuntimeSnapshot load(Long artifactId, Long definitionId, Long executionProjectId) {
        DecisionArtifact artifact = loadArtifact(artifactId);
        if (artifact == null || artifact.getPackageContent() == null) {
            throw new IllegalStateException("已发布规则缺少不可变决策制品");
        }
        DecisionArtifactPackageCodec.DecodedPackage decoded = codec.decode(artifact.getPackageContent());
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

        RuntimeSnapshot snapshot = new RuntimeSnapshot();
        snapshot.artifactId = artifactId;
        snapshot.artifactDigest = artifact.getArtifactDigest();
        snapshot.modelType = text(decoded.getArtifactPackage().getMetadata().get("modelType"));
        snapshot.bindings.putAll(bindings);
        for (DecisionArtifactPackage.Component component
                : decoded.getArtifactPackage().getComponents().values()) {
            String resourceType = text(component.getMetadata().get("resourceType"));
            if ("rule/model.json".equals(component.getPath())) {
                snapshot.modelJson = new String(component.getContent(), StandardCharsets.UTF_8);
            } else if ("runtime/compiled.ql".equals(component.getPath())) {
                snapshot.compiledScript = new String(component.getContent(), StandardCharsets.UTF_8);
            } else if ("VARIABLE".equals(resourceType) || "CONSTANT".equals(resourceType)) {
                RuleVariable variable = JSON.parseObject(component.getContent(), RuleVariable.class);
                variable.setProjectId(executionProjectId);
                variable.setScope("PROJECT");
                variable.setStatus(1);
                applyVariableBinding(variable, bindings);
                snapshot.variables.add(variable);
            } else if ("FUNCTION".equals(resourceType)) {
                RuleFunction function = JSON.parseObject(component.getContent(), RuleFunction.class);
                function.setProjectId(executionProjectId);
                function.setScope("PROJECT");
                function.setStatus(1);
                snapshot.functions.add(function);
            } else if ("MODEL".equals(resourceType)) {
                snapshot.models.add(model(component, executionProjectId));
            } else if ("RULE".equals(resourceType)) {
                snapshot.nestedRules.add(JSON.parseObject(component.getContent(), NestedRuleSnapshot.class));
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

    private RuleModel model(DecisionArtifactPackage.Component component, Long executionProjectId) {
        Object modelMetadata = component.getMetadata().get("model");
        if (modelMetadata == null) {
            throw new IllegalStateException("模型制品组件缺少冻结元数据: " + component.getPath());
        }
        RuleModel model = JSON.parseObject(JSON.toJSONString(modelMetadata), RuleModel.class);
        model.setProjectId(executionProjectId);
        model.setScope("PROJECT");
        model.setStatus(1);
        model.setModelContent(Base64.getEncoder().encodeToString(component.getContent()));
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
            replaceSingle(config, "datasourceId", "DB_DATASOURCE", bindings, fallback);
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
        private Long artifactId;
        private String artifactDigest;
        private String modelType;
        private String modelJson;
        private String compiledScript;
        private final List<RuleVariable> variables = new ArrayList<>();
        private final List<RuleModel> models = new ArrayList<>();
        private final List<RuleFunction> functions = new ArrayList<>();
        private final List<RuleDefinitionInputField> inputFields = new ArrayList<>();
        private final List<RuleDefinitionOutputField> outputFields = new ArrayList<>();
        private final List<NestedRuleSnapshot> nestedRules = new ArrayList<>();
        private final Map<String, Long> bindings = new LinkedHashMap<>();

        public Long getArtifactId() { return artifactId; }
        public String getArtifactDigest() { return artifactDigest; }
        public String getModelType() { return modelType; }
        public String getModelJson() { return modelJson; }
        public String getCompiledScript() { return compiledScript; }
        public List<RuleVariable> getVariables() { return variables; }
        public List<RuleModel> getModels() { return models; }
        public List<RuleFunction> getFunctions() { return functions; }
        public List<RuleDefinitionInputField> getInputFields() { return inputFields; }
        public List<RuleDefinitionOutputField> getOutputFields() { return outputFields; }
        public List<NestedRuleSnapshot> getNestedRules() { return nestedRules; }
        public Map<String, Long> getBindings() { return bindings; }

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
