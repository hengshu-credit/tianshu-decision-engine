package com.hengshucredit.rule.server.artifact;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.ArtifactDeployRequest;
import com.hengshucredit.rule.model.dto.ArtifactImportResult;
import com.hengshucredit.rule.model.entity.ArtifactDeployment;
import com.hengshucredit.rule.model.entity.ArtifactResourceBinding;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.DecisionArtifactComponent;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleListLibrary;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.ArtifactDeploymentMapper;
import com.hengshucredit.rule.server.mapper.ArtifactResourceBindingMapper;
import com.hengshucredit.rule.server.mapper.DecisionArtifactComponentMapper;
import com.hengshucredit.rule.server.mapper.DecisionArtifactMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleFunctionMapper;
import com.hengshucredit.rule.server.mapper.RuleListLibraryMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleLifecycleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ArtifactDeploymentService {
    @Resource
    private DecisionArtifactMapper artifactMapper;
    @Resource
    private DecisionArtifactComponentMapper componentMapper;
    @Resource
    private ArtifactDeploymentMapper deploymentMapper;
    @Resource
    private ArtifactResourceBindingMapper bindingMapper;
    @Resource
    private RuntimeCompatibilityService compatibilityService;
    @Resource
    private RuleDefinitionService definitionService;
    @Resource
    private RuleLifecycleService lifecycleService;
    @Resource
    private RuleVariableMapper variableMapper;
    @Resource
    private RuleModelMapper modelMapper;
    @Resource
    private RuleFunctionMapper functionMapper;
    @Resource
    private RuleDataObjectMapper dataObjectMapper;
    @Resource
    private RuleExternalApiConfigMapper externalApiConfigMapper;
    @Resource
    private RuleExternalDatasourceMapper externalDatasourceMapper;
    @Resource
    private RuleDbDatasourceMapper dbDatasourceMapper;
    @Resource
    private RuleListLibraryMapper listLibraryMapper;

    private final DecisionArtifactPackageCodec codec = new DecisionArtifactPackageCodec();

    @Transactional
    public ArtifactImportResult importArtifact(byte[] bytes, String expectedPackageDigest, String actor) {
        DecisionArtifactPackageCodec.DecodedPackage decoded = codec.decode(bytes);
        if (expectedPackageDigest != null && !expectedPackageDigest.isBlank()
                && !expectedPackageDigest.equals(decoded.getPackageDigest())) {
            throw new IllegalArgumentException("制品包摘要与上传声明不一致");
        }
        RuntimeCompatibilityService.CompatibilityReport compatibility =
                compatibility(decoded.getArtifactPackage());
        if (!compatibility.isCompatible()) {
            throw new IllegalArgumentException("目标运行时不兼容: " + compatibility.getErrors().get(0));
        }
        DecisionArtifact existing = findArtifactByDigest(decoded.getArtifactDigest());
        if (existing != null) {
            if (!decoded.getPackageDigest().equals(existing.getPackageDigest())) {
                throw new IllegalArgumentException("相同制品摘要对应不同包摘要");
            }
            return importResult(existing, decoded.getArtifactPackage(), compatibility, true);
        }

        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setArtifactDigest(decoded.getArtifactDigest());
        artifact.setPackageDigest(decoded.getPackageDigest());
        artifact.setFormatVersion(DecisionArtifactPackage.FORMAT_VERSION);
        artifact.setManifestJson(manifestJson(decoded.getArtifactPackage(), decoded.getArtifactDigest()));
        DecisionArtifactPackage.Component validation =
                decoded.getArtifactPackage().getComponent("validation/report.json");
        artifact.setValidationReportJson(validation == null ? null
                : new String(validation.getContent(), java.nio.charset.StandardCharsets.UTF_8));
        artifact.setRuntimeConstraintsJson(CanonicalJson.write(Map.of(
                "source", "IMPORTED", "compatibility", compatibility)));
        artifact.setPackageContent(bytes);
        artifact.setPackageSize((long) bytes.length);
        artifact.setCreateBy(actor);
        artifact.setCreateTime(LocalDateTime.now());
        insertArtifact(artifact);

        for (DecisionArtifactPackage.Component component
                : decoded.getArtifactPackage().getComponents().values()) {
            Map<String, Object> metadata = component.getMetadata();
            DecisionArtifactComponent record = new DecisionArtifactComponent();
            record.setArtifactId(artifact.getId());
            record.setComponentId(text(metadata.get("componentId"), "IMPORTED:" + component.getPath()));
            record.setComponentType(text(metadata.get("resourceType"), "RULE"));
            record.setSourceType(text(metadata.get("resourceType"), "IMPORTED"));
            record.setSourceId(longValue(metadata.get("resourceId")));
            record.setSourceVersion(integerValue(metadata.get("version")));
            record.setPackagePath(component.getPath());
            record.setMediaType(component.getMediaType());
            record.setContentDigest(Sha256Digests.bytes(component.getContent()));
            record.setContentSize((long) component.getContent().length);
            record.setMetadataJson(CanonicalJson.write(metadata));
            record.setCreateTime(LocalDateTime.now());
            insertComponent(record);
        }
        return importResult(artifact, decoded.getArtifactPackage(), compatibility, false);
    }

    @Transactional
    public ArtifactDeployment deploy(ArtifactDeployRequest request, String actor) {
        validateRequest(request);
        DecisionArtifact artifact = loadArtifact(request.getArtifactId());
        if (artifact == null) throw new IllegalArgumentException("决策制品不存在");
        DecisionArtifactPackageCodec.DecodedPackage decoded = codec.decode(artifact.getPackageContent());
        if (!artifact.getArtifactDigest().equals(decoded.getArtifactDigest())
                || !artifact.getPackageDigest().equals(decoded.getPackageDigest())) {
            throw new IllegalArgumentException("数据库中的决策制品摘要校验失败");
        }
        RuntimeCompatibilityService.CompatibilityReport compatibility =
                compatibility(decoded.getArtifactPackage());
        if (!compatibility.isCompatible()) {
            throw new IllegalArgumentException("目标运行时不兼容: " + compatibility.getErrors().get(0));
        }

        Long definitionId;
        Long targetProjectId;
        if (Boolean.TRUE.equals(request.getCreateRule())) {
            definitionId = createTargetDefinition(request);
            targetProjectId = request.getTargetProjectId();
        } else {
            RuleDefinition target = loadDefinition(request.getTargetDefinitionId());
            if (target == null) throw new IllegalArgumentException("显式指定的目标规则不存在");
            definitionId = target.getId();
            targetProjectId = target.getProjectId();
        }

        Map<String, String> requirements = bindingRequirements(decoded.getArtifactPackage());
        Map<String, Long> requestedBindings = request.getBindings() == null
                ? Collections.emptyMap() : request.getBindings();
        Set<String> missing = new LinkedHashSet<>(requirements.keySet());
        missing.removeAll(requestedBindings.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("缺少显式资源绑定: " + missing);
        }
        Set<String> unknown = new LinkedHashSet<>(requestedBindings.keySet());
        unknown.removeAll(requirements.keySet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("包含制品未声明的资源绑定: " + unknown);
        }
        for (Map.Entry<String, String> requirement : requirements.entrySet()) {
            Long targetId = requestedBindings.get(requirement.getKey());
            if (targetId == null) throw new IllegalArgumentException("绑定目标 ID 不能为空: " + requirement.getKey());
            validateTargetBinding(requirement.getValue(), targetId, targetProjectId);
        }

        ArtifactDeployment deployment = new ArtifactDeployment();
        deployment.setArtifactId(artifact.getId());
        deployment.setEnvironmentCode(request.getEnvironmentCode());
        deployment.setTargetDefinitionId(definitionId);
        deployment.setCreateRule(Boolean.TRUE.equals(request.getCreateRule()) ? 1 : 0);
        deployment.setStatus("DEPLOYED");
        deployment.setCompatibilityReportJson(CanonicalJson.write(compatibility));
        deployment.setBindingReportJson(CanonicalJson.write(requestedBindings));
        deployment.setDeployBy(actor);
        deployment.setDeployTime(LocalDateTime.now());
        deployment.setCreateTime(LocalDateTime.now());
        insertDeployment(deployment);

        for (Map.Entry<String, String> requirement : requirements.entrySet()) {
            ArtifactResourceBinding binding = new ArtifactResourceBinding();
            binding.setDeploymentId(deployment.getId());
            binding.setComponentId(requirement.getKey());
            binding.setResourceType(requirement.getValue());
            binding.setTargetResourceId(requestedBindings.get(requirement.getKey()));
            binding.setBindingDigest(Sha256Digests.text(CanonicalJson.write(Map.of(
                    "componentId", requirement.getKey(),
                    "resourceType", requirement.getValue(),
                    "targetResourceId", requestedBindings.get(requirement.getKey())))));
            binding.setCreateTime(LocalDateTime.now());
            insertBinding(binding);
        }
        activateImportedArtifact(artifact.getId(), definitionId, actor);
        return deployment;
    }

    public DecisionArtifact getArtifact(Long artifactId) {
        return loadArtifact(artifactId);
    }

    public Map<String, Object> describeArtifact(Long artifactId) {
        DecisionArtifact artifact = loadArtifact(artifactId);
        if (artifact == null) throw new IllegalArgumentException("决策制品不存在");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", artifact.getId());
        detail.put("definitionId", artifact.getDefinitionId());
        detail.put("revisionId", artifact.getRevisionId());
        detail.put("artifactDigest", artifact.getArtifactDigest());
        detail.put("packageDigest", artifact.getPackageDigest());
        detail.put("formatVersion", artifact.getFormatVersion());
        detail.put("manifestJson", artifact.getManifestJson());
        detail.put("validationReportJson", artifact.getValidationReportJson());
        detail.put("runtimeConstraintsJson", artifact.getRuntimeConstraintsJson());
        detail.put("packageSize", artifact.getPackageSize());
        detail.put("createBy", artifact.getCreateBy());
        detail.put("createTime", artifact.getCreateTime());
        detail.put("components", loadComponents(artifactId));
        return detail;
    }

    public List<ArtifactDeployment> listDeployments(Long artifactId) {
        if (loadArtifact(artifactId) == null) throw new IllegalArgumentException("决策制品不存在");
        return safe(loadDeployments(artifactId));
    }

    public List<ArtifactResourceBinding> listBindings(Long deploymentId) {
        if (deploymentId == null) throw new IllegalArgumentException("deploymentId 不能为空");
        return safe(loadBindings(deploymentId));
    }

    private ArtifactImportResult importResult(DecisionArtifact artifact,
                                              DecisionArtifactPackage artifactPackage,
                                              RuntimeCompatibilityService.CompatibilityReport compatibility,
                                              boolean duplicate) {
        ArtifactImportResult result = new ArtifactImportResult();
        result.setArtifactId(artifact.getId());
        result.setArtifactDigest(artifact.getArtifactDigest());
        result.setPackageDigest(artifact.getPackageDigest());
        result.setDuplicate(duplicate);
        result.setCompatible(compatibility.isCompatible());
        result.setCompatibilityReportJson(CanonicalJson.write(compatibility));
        result.setRequiredBindingComponentIds(new ArrayList<>(bindingRequirements(artifactPackage).keySet()));
        return result;
    }

    private void validateRequest(ArtifactDeployRequest request) {
        if (request == null || request.getArtifactId() == null) {
            throw new IllegalArgumentException("artifactId 不能为空");
        }
        boolean create = Boolean.TRUE.equals(request.getCreateRule());
        if (create == (request.getTargetDefinitionId() != null)) {
            throw new IllegalArgumentException("必须且只能选择 createRule=true 或显式 targetDefinitionId");
        }
        if (create && (blank(request.getTargetRuleCode()) || blank(request.getTargetRuleName())
                || blank(request.getTargetModelType()) || request.getTargetProjectId() == null)) {
            throw new IllegalArgumentException("新建目标规则必须显式提供项目、规则编码、名称和模型类型");
        }
    }

    private Map<String, String> bindingRequirements(DecisionArtifactPackage artifactPackage) {
        Map<String, String> result = new java.util.TreeMap<>();
        for (DecisionArtifactPackage.Component component : artifactPackage.getComponents().values()) {
            Map<String, Object> metadata = component.getMetadata();
            if (!"EXPLICIT_BINDING".equals(metadata.get("embeddingMode"))) continue;
            String componentId = text(metadata.get("componentId"), null);
            String targetType = text(metadata.get("targetResourceType"), null);
            if (componentId == null || targetType == null) {
                throw new IllegalArgumentException("显式绑定组件缺少 componentId 或 targetResourceType: "
                        + component.getPath());
            }
            if (result.put(componentId, targetType) != null) {
                throw new IllegalArgumentException("显式绑定组件 ID 重复: " + componentId);
            }
        }
        return result;
    }

    private String manifestJson(DecisionArtifactPackage artifactPackage, String digest) {
        List<Map<String, Object>> components = new ArrayList<>();
        for (DecisionArtifactPackage.Component component : artifactPackage.getComponents().values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", component.getPath());
            item.put("mediaType", component.getMediaType());
            item.put("size", component.getContent().length);
            item.put("digest", Sha256Digests.bytes(component.getContent()));
            item.put("metadata", component.getMetadata());
            components.add(item);
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("formatVersion", DecisionArtifactPackage.FORMAT_VERSION);
        manifest.put("artifactDigest", digest);
        manifest.put("metadata", artifactPackage.getMetadata());
        manifest.put("components", components);
        return CanonicalJson.write(manifest);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String text(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    protected RuntimeCompatibilityService.CompatibilityReport compatibility(
            DecisionArtifactPackage artifactPackage) {
        return compatibilityService.validate(artifactPackage);
    }

    protected DecisionArtifact findArtifactByDigest(String digest) {
        return artifactMapper.selectOne(new LambdaQueryWrapper<DecisionArtifact>()
                .eq(DecisionArtifact::getArtifactDigest, digest).last("LIMIT 1"));
    }

    protected DecisionArtifact loadArtifact(Long artifactId) {
        return artifactMapper.selectById(artifactId);
    }

    protected void insertArtifact(DecisionArtifact artifact) {
        artifactMapper.insert(artifact);
    }

    protected void insertComponent(DecisionArtifactComponent component) {
        componentMapper.insert(component);
    }

    protected RuleDefinition loadDefinition(Long definitionId) {
        return definitionService.getById(definitionId);
    }

    protected Long createTargetDefinition(ArtifactDeployRequest request) {
        RuleDefinition definition = new RuleDefinition();
        definition.setProjectId(request.getTargetProjectId());
        definition.setRuleCode(request.getTargetRuleCode());
        definition.setRuleName(request.getTargetRuleName());
        definition.setModelType(request.getTargetModelType());
        definition.setDescription("由不可变决策制品跨环境创建");
        return definitionService.createWithContent(definition).getId();
    }

    protected void validateTargetBinding(String targetResourceType, Long targetResourceId,
                                         Long targetProjectId) {
        switch (targetResourceType) {
            case "VARIABLE" -> {
                RuleVariable resource = variableMapper.selectById(targetResourceId);
                assertActiveAndOwned(resource == null ? null : resource.getStatus(),
                        resource == null ? null : resource.getScope(),
                        resource == null ? null : resource.getProjectId(), targetProjectId, targetResourceType);
            }
            case "MODEL" -> {
                RuleModel resource = modelMapper.selectById(targetResourceId);
                assertActiveAndOwned(resource == null ? null : resource.getStatus(),
                        resource == null ? null : resource.getScope(),
                        resource == null ? null : resource.getProjectId(), targetProjectId, targetResourceType);
            }
            case "FUNCTION" -> {
                RuleFunction resource = functionMapper.selectById(targetResourceId);
                assertActiveAndOwned(resource == null ? null : resource.getStatus(),
                        resource == null ? null : resource.getScope(),
                        resource == null ? null : resource.getProjectId(), targetProjectId, targetResourceType);
            }
            case "DATA_OBJECT_ROOT" -> {
                RuleDataObject resource = dataObjectMapper.selectById(targetResourceId);
                assertActiveAndOwned(resource == null ? null : resource.getStatus(),
                        resource == null ? null : resource.getScope(),
                        resource == null ? null : resource.getProjectId(), targetProjectId, targetResourceType);
            }
            case "DB_DATASOURCE" -> {
                RuleDbDatasource resource = dbDatasourceMapper.selectById(targetResourceId);
                assertActiveAndOwned(resource == null ? null : resource.getStatus(),
                        resource == null ? null : resource.getScope(),
                        resource == null ? null : resource.getProjectId(), targetProjectId, targetResourceType);
            }
            case "LIST_LIBRARY" -> {
                RuleListLibrary resource = listLibraryMapper.selectById(targetResourceId);
                assertActiveAndOwned(resource == null ? null : resource.getStatus(),
                        resource == null ? null : resource.getScope(),
                        resource == null ? null : resource.getProjectId(), targetProjectId, targetResourceType);
            }
            case "EXTERNAL_API" -> {
                RuleExternalApiConfig api = externalApiConfigMapper.selectById(targetResourceId);
                if (api == null || !active(api.getStatus())) {
                    throw new IllegalArgumentException("绑定目标不存在或已停用: EXTERNAL_API:" + targetResourceId);
                }
                RuleExternalDatasource datasource = externalDatasourceMapper.selectById(api.getDatasourceId());
                assertActiveAndOwned(datasource == null ? null : datasource.getStatus(),
                        datasource == null ? null : datasource.getScope(),
                        datasource == null ? null : datasource.getProjectId(), targetProjectId, targetResourceType);
            }
            default -> throw new IllegalArgumentException("不支持的目标绑定资源类型: " + targetResourceType);
        }
    }

    private void assertActiveAndOwned(Integer status, String scope, Long ownerProjectId,
                                      Long targetProjectId, String type) {
        if (!active(status) || !(scope == null || "GLOBAL".equalsIgnoreCase(scope)
                || (ownerProjectId != null && ownerProjectId.equals(targetProjectId)))) {
            throw new IllegalArgumentException("绑定目标不存在、已停用或不属于目标项目: " + type);
        }
    }

    private boolean active(Integer status) {
        return status != null && status == 1;
    }

    protected void activateImportedArtifact(Long artifactId, Long definitionId, String actor) {
        lifecycleService.activateImportedArtifact(artifactId, definitionId, actor);
    }

    protected void insertDeployment(ArtifactDeployment deployment) {
        deploymentMapper.insert(deployment);
    }

    protected void insertBinding(ArtifactResourceBinding binding) {
        bindingMapper.insert(binding);
    }

    protected List<DecisionArtifactComponent> loadComponents(Long artifactId) {
        return componentMapper.selectList(new LambdaQueryWrapper<DecisionArtifactComponent>()
                .eq(DecisionArtifactComponent::getArtifactId, artifactId)
                .orderByAsc(DecisionArtifactComponent::getId));
    }

    protected List<ArtifactDeployment> loadDeployments(Long artifactId) {
        return deploymentMapper.selectList(new LambdaQueryWrapper<ArtifactDeployment>()
                .eq(ArtifactDeployment::getArtifactId, artifactId)
                .orderByDesc(ArtifactDeployment::getId));
    }

    protected List<ArtifactResourceBinding> loadBindings(Long deploymentId) {
        return bindingMapper.selectList(new LambdaQueryWrapper<ArtifactResourceBinding>()
                .eq(ArtifactResourceBinding::getDeploymentId, deploymentId)
                .orderByAsc(ArtifactResourceBinding::getId));
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
