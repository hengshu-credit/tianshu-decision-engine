package com.hengshucredit.rule.server.transfer;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.dto.GovernanceReviewRequest;
import com.hengshucredit.rule.model.dto.GovernanceSubmitRequest;
import com.hengshucredit.rule.model.dto.RuleLifecycleActionRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.governance.GovernanceApprovalService;
import com.hengshucredit.rule.server.governance.GovernedResourceAdapter;
import com.hengshucredit.rule.server.governance.ResourceSnapshot;
import com.hengshucredit.rule.server.governance.GovernanceResourceTypes;
import com.hengshucredit.rule.server.governance.GovernanceSecretCodec;
import com.hengshucredit.rule.server.mapper.GovernedResourceMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper;
import com.hengshucredit.rule.server.mapper.RuleVersionBindingMapper;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleDraftService;
import com.hengshucredit.rule.server.service.RuleLifecycleService;
import com.hengshucredit.rule.server.service.RuleProjectService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 将已确认的离线包写入目标环境；过程复用治理审批及规则草稿机制。 */
@Service
public class OfflineResourceImportService {
    @Resource private com.hengshucredit.rule.server.governance.GovernedResourceAdapterRegistry adapterRegistry;
    @Resource private GovernanceApprovalService approvalService;
    @Resource private GovernedResourceMapper governedResourceMapper;
    @Resource private GovernedResourceVersionMapper governedResourceVersionMapper;
    @Resource private RuleVersionBindingMapper ruleVersionBindingMapper;
    @Resource private com.hengshucredit.rule.server.mapper.RuleDefinitionMapper ruleDefinitionMapper;
    @Resource private RuleDefinitionService definitionService;
    @Resource private RuleLifecycleService lifecycleService;
    @Resource private RuleDraftService draftService;
    @Resource private RuleProjectService projectService;
    @Resource private GovernanceSecretCodec secretCodec;
    @Resource private com.hengshucredit.rule.server.governance.GovernanceResourceBootstrapService governanceBootstrapService;
    private final TransferBundleCodec codec = new TransferBundleCodec();

    @Transactional
    public Map<String, Object> apply(byte[] bytes, TransferImportOptions options, String actor) {
        if (options == null) throw new IllegalArgumentException("请选择导入作用范围及目标项目");
        if (actor == null || actor.isBlank()) actor = ConsoleOperatorResolver.SYSTEM_CONSOLE;
        TransferBundle decodedBundle = codec.decode(bytes).bundle();
        List<TransferBundle.Resource> selectedResources = TransferSelection.select(decodedBundle, options);
        Set<String> selectedKeys = selectedResources.stream().map(TransferBundle.Resource::key)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        TransferBundle bundle = new TransferBundle(decodedBundle.roots().stream()
                .filter(selectedKeys::contains).toList(), selectedResources, decodedBundle.warnings());
        boolean global = "GLOBAL".equals(options.normalizedScope());
        boolean createProject = !global && Boolean.TRUE.equals(options.createProject());
        boolean hasProjectResource = bundle.resources().stream().anyMatch(resource ->
                TransferKey.parse(resource.key()).type() == TransferResourceType.PROJECT);
        if (!"GLOBAL".equals(options.normalizedScope()) && (options.targetProjectId() == null || options.targetProjectId() <= 0)
                && !Boolean.TRUE.equals(options.createProject())
                && (options.projectBindings() == null || options.projectBindings().isEmpty())
                && (bundle.resources().stream().anyMatch(resource -> !"GLOBAL".equalsIgnoreCase(String.valueOf(resource.configuration().get("scope"))))
                || hasProjectResource)) {
            throw new IllegalArgumentException("项目级离线资源导入必须选择目标项目");
        }
        Map<String, TransferBundle.Resource> byKey = bundle.resources().stream()
                .collect(java.util.stream.Collectors.toMap(TransferBundle.Resource::key, value -> value,
                        (left, right) -> left, LinkedHashMap::new));
        List<TransferBundle.Resource> ordered = topologicalOrder(bundle.resources());
        validateProjectTargets(ordered, options, createProject);
        Map<String, Long> idMap = new LinkedHashMap<>();
        if (options.projectBindings() != null) {
            options.projectBindings().forEach((sourceId, targetId) -> {
                try {
                    long parsedSourceId = Long.parseLong(String.valueOf(sourceId));
                    if (parsedSourceId > 0 && targetId != null && targetId > 0) {
                        idMap.put("PROJECT:" + parsedSourceId, targetId);
                    }
                } catch (RuntimeException ignored) {
                    // 非数字源项目键在预检阶段提示，导入不按名称猜测映射。
                }
            });
        }
        options.normalizedResourceBindings().forEach((sourceKey, targetId) -> {
            if (targetId == null || targetId <= 0) {
                throw new IllegalArgumentException("资源关联目标 ID 无效: " + sourceKey);
            }
            TransferKey target = TransferKey.parse(sourceKey);
            validateExternalTarget(target, targetId, options);
            idMap.put(sourceKey, targetId);
        });
        validateExternalReferences(ordered, idMap, options);
        Map<String, Map<Long, Long>> childMap = new HashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();
        Long effectiveTargetProjectId = global ? Long.valueOf(0) : options.targetProjectId();
        boolean createdProject = false;
        if (createProject) {
            TransferBundle.Resource template = ordered.stream()
                    .filter(resource -> TransferKey.parse(resource.key()).type() == TransferResourceType.PROJECT)
                    .findFirst().orElse(null);
            Map<String, Object> projectConfig = template == null ? Map.of() : copy(template.configuration());
            if (template != null && "SUFFIX".equals(options.normalizedResourcePolicy())) {
                addSuffix(projectConfig, TransferResourceType.PROJECT, options.normalizedSuffix());
            }
            effectiveTargetProjectId = importProject(projectConfig, options, actor);
            if (template != null) idMap.put(template.key(), effectiveTargetProjectId);
            createdProject = true;
        }
        for (TransferBundle.Resource resource : ordered) {
            TransferKey sourceKey = TransferKey.parse(resource.key());
            if (sourceKey.type() == TransferResourceType.RULE_VERSION) {
                if (!Boolean.TRUE.equals(options.publishRules())) {
                    throw new IllegalArgumentException("固定版本绑定必须在目标规则发布后重新建立；确认导入时请开启“导入并发布规则”或先手工发布对应规则: " + resource.key());
                }
                Long targetBinding = rebindRuleVersion(resource, idMap);
                idMap.put(resource.key(), targetBinding);
                result.add(row(resource, targetBinding, "REBOUND", "按目标规则业务版本重建并使用最新代次"));
                continue;
            }
            if (sourceKey.type() == TransferResourceType.RULE
                    && resource.references().stream().anyMatch(reference -> reference.targetKey().startsWith("RULE_VERSION:"))) {
                if (!Boolean.TRUE.equals(options.publishRules())) {
                    throw new IllegalArgumentException("包含固定版本引用的规则需要目标规则发布后重建版本绑定；请开启“导入并发布规则”: " + resource.key());
                }
            }
            if (sourceKey.type() == TransferResourceType.PROJECT && "GLOBAL".equals(options.normalizedScope())) {
                idMap.put(resource.key(), 0L);
                result.add(row(resource, 0L, "SKIPPED", "全局导入不创建项目，项目级资源将按全局范围写入"));
                continue;
            }
            Map<String, Object> configuration = copy(resource.configuration());
            if (sourceKey.type() == TransferResourceType.PROJECT) {
                // 项目归属仅由显式 ID / 新建选项决定，不受普通资源冲突策略影响。
                if (!createProject) {
                    Long targetId = resolveTargetProjectId(Map.of("projectId", sourceKey.id()), options, options.targetProjectId());
                    idMap.put(resource.key(), targetId);
                    result.add(row(resource, targetId, "BOUND", "已绑定目标项目"));
                    continue;
                }
                result.add(row(resource, effectiveTargetProjectId, "CREATED", null));
                continue;
            }
            applyTargetScope(configuration, sourceKey.type(), options, effectiveTargetProjectId);
            Long resourceTargetProjectId = number(configuration.get("projectId"));
            ExistingResource existing = findExisting(sourceKey.type(), configuration, resourceTargetProjectId);
            Long existingId = existing == null ? null : existing.id();
            rewriteReferences(configuration, resource, byKey, idMap, childMap, options.normalizedFieldBindings());
            String policy = sourceKey.type() == TransferResourceType.VARIABLE
                    ? options.normalizedVariablePolicy() : options.normalizedResourcePolicy();
            if (sourceKey.type() == TransferResourceType.RULE && ruleDefinitionMapper != null) {
                RuleDefinition occupied = ruleDefinitionMapper.selectOne(new LambdaQueryWrapper<RuleDefinition>()
                        .eq(RuleDefinition::getRuleCode, configuration.get("ruleCode")));
                if (occupied != null && !occupied.getId().equals(existingId)) {
                    if (!"SUFFIX".equals(policy)) throw new IllegalArgumentException("规则编码在其他项目或未发布规则中已存在，请选择新建并追加后缀: " + configuration.get("ruleCode"));
                    addSuffix(configuration, sourceKey.type(), options.normalizedSuffix());
                    existingId = null;
                }
            }
            if (existingId != null && "REUSE".equals(policy)) {
                if (!TransferResourceComparison.reusable(sourceKey.type(), configuration, existing.configuration(), existing.status())) {
                    throw new IllegalArgumentException("资源不能复用（类型、配置、启用状态或环境凭据未匹配）: " + resource.key()
                            + "；请选择覆盖已有配置或新建并追加后缀，变量类型冲突只能新建并追加后缀");
                }
                idMap.put(resource.key(), existingId);
                if (sourceKey.type() == TransferResourceType.DATA_OBJECT) childMap.put(resource.key(),
                        reusedFieldMap(resource.configuration(), existing.configuration()));
                result.add(row(resource, existingId, "REUSED", null));
                continue;
            }
            if (existingId != null && "OVERWRITE".equals(policy)) {
                // Existing resources are updated only through the governance approval service.
            } else if (existingId != null) {
                addSuffix(configuration, sourceKey.type(), options.normalizedSuffix());
                existingId = null;
            }
            Map<Long, Long> importedFieldIds = sourceKey.type() == TransferResourceType.DATA_OBJECT && existingId != null
                    ? prepareObjectOverwrite(configuration, existing.configuration()) : Map.of();
            if (sourceKey.type() == TransferResourceType.DATA_OBJECT && existingId != null) configuration.put("id", existingId);
            ResourceSnapshot snapshot = ResourceSnapshot.ofJson(CanonicalJson.write(configuration));
            Long targetId;
            if (sourceKey.type() == TransferResourceType.RULE) {
                if (existingId == null && ruleDefinitionMapper != null && ruleDefinitionMapper.selectOne(new LambdaQueryWrapper<RuleDefinition>()
                        .eq(RuleDefinition::getRuleCode, configuration.get("ruleCode"))) != null) {
                    throw new IllegalArgumentException("目标规则编码已被占用，请修改新建后缀: " + configuration.get("ruleCode"));
                }
                targetId = importRuleDraft(configuration, existingId, options, resourceTargetProjectId, actor);
            } else {
                Map<Long, Long> appliedFields = new HashMap<>();
                targetId = approveResource(sourceKey.type(), existingId, options, resourceTargetProjectId, snapshot, actor, appliedFields);
                if (sourceKey.type() == TransferResourceType.DATA_OBJECT) {
                    if (importedFieldIds.isEmpty()) childMap.put(resource.key(), appliedFields);
                    else {
                        Map<Long, Long> sourceToApplied = new HashMap<>();
                        importedFieldIds.forEach((sourceId, draftId) -> {
                            Long appliedId = appliedFields.get(draftId);
                            if (appliedId == null) throw new IllegalStateException("覆盖数据对象未返回实际字段 ID: " + sourceId);
                            sourceToApplied.put(sourceId, appliedId);
                        });
                        childMap.put(resource.key(), sourceToApplied);
                    }
                }
            }
            idMap.put(resource.key(), targetId);
            result.add(row(resource, targetId, existingId == null ? "CREATED" : "OVERWRITTEN", null));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "APPLIED");
        response.put("roots", bundle.roots());
        response.put("resourceIdMapping", idMap);
        response.put("resources", result);
        response.put("listDataIncluded", false);
        response.put("targetProjectId", effectiveTargetProjectId);
        response.put("projectCreated", createdProject);
        return response;
    }

    /** 迁移时确认字段身份；源环境数字 ID 绝不能直接作为目标环境 UPDATE 的依据。 */
    private Map<Long, Long> prepareObjectOverwrite(Map<String, Object> configuration, Map<String, Object> existing) {
        Map<Long, Long> ids = new LinkedHashMap<>(TransferObjectFieldIndex.of(configuration)
                .matchingIds(TransferObjectFieldIndex.of(existing)));
        Set<Long> occupied = new HashSet<>();
        for (Object value : list(existing.get("fields"))) occupied.add(number(object(value).get("id")));
        long temporary = -1;
        for (Object value : list(configuration.get("fields"))) {
            Long sourceId = number(object(value).get("id"));
            if (!ids.containsKey(sourceId)) {
                while (occupied.contains(temporary)) temporary--;
                ids.put(sourceId, temporary--);
            }
        }
        for (Object value : list(configuration.get("fields"))) {
            Map<String, Object> field = object(value);
            Long parent = number(field.get("parentFieldId"));
            field.put("id", ids.get(number(field.get("id"))));
            if (parent != null) field.put("parentFieldId", ids.get(parent));
        }
        return ids;
    }

    private Long approveResource(TransferResourceType type, Long existingId, TransferImportOptions options,
                                 Long targetProjectId, ResourceSnapshot snapshot, String actor, Map<Long, Long> appliedFields) {
        GovernanceDraftRequest draft = new GovernanceDraftRequest();
        draft.setResourceType(type.name());
        draft.setResourceId(existingId);
        draft.setProjectId(projectId(type, options, targetProjectId));
        draft.setAction(existingId == null ? "CREATE" : "UPDATE");
        draft.setSnapshotJson(snapshot.snapshotJson());
        draft.setEffectiveStatus("ACTIVE");
        draft.setChangeSummary("离线配置迁移");
        var request = approvalService.createDraft(draft, actor);
        approvalService.submit(request.getId(), new GovernanceSubmitRequest() {{ setComment("离线配置迁移自动提交"); }}, actor);
        var approved = approvalService.approve(request.getId(), new GovernanceReviewRequest() {{ setComment("离线配置迁移自动审批"); }}, actor,
                applied -> appliedFields.putAll(applied.fieldIdMapping()));
        if (!"APPROVED".equals(approved.getStatus()))
            throw new IllegalStateException("离线资源审批未生效: " + type + "，状态: " + approved.getStatus());
        if (approved.getResourceId() == null || approved.getResourceId() <= 0)
            throw new IllegalStateException("离线资源审批未返回目标资源 ID: " + type);
        return approved.getResourceId();
    }

    private Long importRuleDraft(Map<String, Object> configuration, Long existingId,
                                 TransferImportOptions options, Long targetProjectId, String actor) {
        if (existingId != null) throw new IllegalArgumentException("规则覆盖必须通过规则审批流程选择目标修订");
        RuleDefinition definition = JSON.parseObject(CanonicalJson.write(configuration), RuleDefinition.class);
        definition.setId(null);
        definition.setProjectId(projectId(TransferResourceType.RULE, options, targetProjectId));
        definition.setScope("GLOBAL".equals(options.normalizedScope()) ? "GLOBAL" : "PROJECT");
        RuleDefinition created = definitionService.createWithContent(definition);
        RuleRevision revision = lifecycleService.createDraft(created.getId(), null);
        Map<String, Object> content = object(configuration.get("content"));
        RuleDraftSaveRequest save = new RuleDraftSaveRequest();
        save.setDefinitionId(created.getId());
        save.setRevisionId(revision.getId());
        save.setLockVersion(revision.getLockVersion() == null ? 0 : revision.getLockVersion());
        save.setModelJson(String.valueOf(content.getOrDefault("modelJson", "{}")));
        save.setOpenApiConfigJson(content.get("openApiConfigJson") == null ? null : String.valueOf(content.get("openApiConfigJson")));
        save.setUpdateOpenApiConfig(true);
        var saved = draftService.save(save);
        if (Boolean.TRUE.equals(options.publishRules())) {
            RuleRevision savedRevision = saved.getRevision();
            RuleLifecycleActionRequest action = new RuleLifecycleActionRequest();
            action.setComment("离线配置迁移按显式选项发布规则");
            lifecycleService.submit(savedRevision.getId(), action);
            lifecycleService.approve(savedRevision.getId(), action);
            lifecycleService.publish(savedRevision.getId(), action);
        }
        return created.getId();
    }

    private Long rebindRuleVersion(TransferBundle.Resource resource, Map<String, Long> idMap) {
        Map<String, Object> configuration = resource.configuration();
        Long sourceDefinitionId = number(configuration.get("definitionId"));
        Integer versionNo = integer(configuration.get("versionNo"));
        if (sourceDefinitionId == null || versionNo == null || versionNo <= 0) {
            throw new IllegalArgumentException("固定版本包缺少源规则或业务版本号: " + resource.key());
        }
        Long targetDefinitionId = idMap.get("RULE:" + sourceDefinitionId);
        if (targetDefinitionId == null) {
            throw new IllegalArgumentException("固定版本对应的目标规则尚未导入: " + resource.key());
        }
        var binding = ruleVersionBindingMapper.selectOne(new LambdaQueryWrapper<com.hengshucredit.rule.model.entity.RuleVersionBinding>()
                .eq(com.hengshucredit.rule.model.entity.RuleVersionBinding::getDefinitionId, targetDefinitionId)
                .eq(com.hengshucredit.rule.model.entity.RuleVersionBinding::getVersionNo, versionNo)
                .eq(com.hengshucredit.rule.model.entity.RuleVersionBinding::getStatus, 1)
                .last("LIMIT 1"));
        if (binding == null) {
            throw new IllegalStateException("目标规则尚未生成业务版本 " + versionNo + "，请先导入并发布对应版本后重试: " + resource.key());
        }
        return binding.getId();
    }

    static List<TransferBundle.Resource> topologicalOrder(List<TransferBundle.Resource> resources) {
        Map<String, TransferBundle.Resource> byKey = resources.stream().collect(java.util.stream.Collectors.toMap(
                TransferBundle.Resource::key, value -> value, (left, right) -> left, LinkedHashMap::new));
        List<TransferBundle.Resource> ordered = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        while (ordered.size() < resources.size()) {
            boolean progressed = false;
            for (TransferBundle.Resource resource : resources) {
                if (done.contains(resource.key())) continue;
                boolean ready = resource.references().stream().allMatch(reference ->
                        reference.external() || !byKey.containsKey(reference.targetKey())
                                || done.contains(reference.targetKey()));
                if (ready) { ordered.add(resource); done.add(resource.key()); progressed = true; }
            }
            if (!progressed) throw new IllegalArgumentException("离线资源依赖形成循环，无法安全导入");
        }
        return ordered;
    }

    private void rewriteReferences(Map<String, Object> configuration, TransferBundle.Resource resource,
                                   Map<String, TransferBundle.Resource> byKey, Map<String, Long> idMap,
                                   Map<String, Map<Long, Long>> childMap,
                                   Map<String, Long> fieldBindings) {
        for (TransferBundle.Reference reference : resource.references()) {
            Long target = idMap.get(reference.targetKey());
            if (target == null) throw new IllegalArgumentException("上游资源尚未导入: " + reference.targetKey());
            if (reference.childPath() != null) {
                String referenceKey = TransferSelection.referenceKey(resource.key(), reference);
                Long mapped = reference.external() ? fieldBindings.get(referenceKey) : null;
                if (mapped == null && !reference.external()) {
                    Object sourceChild = TransferJsonPath.read(byKey.get(reference.targetKey()).configuration(), reference.childPath());
                    mapped = childMap.getOrDefault(reference.targetKey(), Map.of()).get(number(sourceChild));
                }
                if (mapped == null) throw new IllegalArgumentException("数据对象子字段无法映射: " + reference.targetKey());
                target = mapped;
            }
            TransferJsonPath.replace(configuration, reference.path(), target);
        }
    }

    private void validateExternalReferences(List<TransferBundle.Resource> resources,
                                            Map<String, Long> idMap,
                                            TransferImportOptions options) {
        for (TransferBundle.Resource resource : resources) {
            for (TransferBundle.Reference reference : resource.references()) {
                if (!reference.external()) continue;
                Long targetId = idMap.get(reference.targetKey());
                if (targetId == null) {
                    throw new IllegalArgumentException("请为外部关联选择目标资源: " + reference.targetKey());
                }
                if (reference.childPath() != null
                        && options.normalizedFieldBindings().get(
                        TransferSelection.referenceKey(resource.key(), reference)) == null) {
                    throw new IllegalArgumentException("请为外部数据对象关联选择目标字段: " + reference.targetKey());
                }
                if (reference.childPath() != null) {
                    Long fieldId = options.normalizedFieldBindings().get(
                            TransferSelection.referenceKey(resource.key(), reference));
                    validateExternalField(TransferKey.parse(reference.targetKey()), targetId, fieldId);
                }
            }
        }
    }

    private void validateExternalField(TransferKey target, Long targetId, Long fieldId) {
        if (target.type() != TransferResourceType.DATA_OBJECT || fieldId == null) {
            throw new IllegalArgumentException("外部字段关联必须指向数据对象字段: " + target.value());
        }
        Map<String, Object> configuration = CanonicalJson.readMap(
                adapterRegistry.require("DATA_OBJECT").loadEffective(targetId).snapshotJson());
        Object fields = configuration.get("fields");
        if (!(fields instanceof List<?> list) || list.stream().noneMatch(value ->
                value instanceof Map<?, ?> map && String.valueOf(map.get("id")).equals(String.valueOf(fieldId)))) {
            throw new IllegalArgumentException("目标数据对象不包含所选字段: " + fieldId);
        }
    }

    private void validateExternalTarget(TransferKey target, Long targetId,
                                        TransferImportOptions options) {
        if (target.type() == TransferResourceType.RULE_VERSION) {
            var binding = ruleVersionBindingMapper.selectById(targetId);
            if (binding == null || !Integer.valueOf(1).equals(binding.getStatus())) {
                throw new IllegalArgumentException("目标规则版本不存在或已下线: " + target.value());
            }
            return;
        }
        if (target.type() == TransferResourceType.PROJECT) {
            if (projectService.getById(targetId) == null) {
                throw new IllegalArgumentException("目标项目不存在: " + target.value());
            }
            return;
        }
        GovernedResourceAdapter adapter = adapterRegistry.require(target.type().name());
        ResourceSnapshot snapshot = adapter.loadEffective(targetId);
        if (snapshot == null) throw new IllegalArgumentException("目标资源不存在: " + target.value());
        if (!"GLOBAL".equals(options.normalizedScope())) {
            Map<String, Object> config = CanonicalJson.readMap(snapshot.snapshotJson());
            Long projectId = number(config.get("projectId"));
            if (projectId != null && options.targetProjectId() != null
                    && projectId > 0 && !projectId.equals(options.targetProjectId())) {
                throw new IllegalArgumentException("目标资源不属于所选项目: " + target.value());
            }
        }
    }

    /** 按完整父子结构产生 ID 映射，字段查询顺序变化不能改变引用关系。 */
    private Map<Long, Long> reusedFieldMap(Map<String, Object> source, Map<String, Object> target) {
        return TransferObjectFieldIndex.of(source).matchingIds(TransferObjectFieldIndex.of(target));
    }

    private ExistingResource findExisting(TransferResourceType type, Map<String, Object> config, Long projectId) {
        String code = String.valueOf(config.get(type.codeField));
        if (code == null || "null".equals(code) || governedResourceMapper == null) return null;
        if (projectId == null || projectId < 0) return null;
        for (GovernedResource row : governedResourceMapper.selectList(new LambdaQueryWrapper<GovernedResource>()
                .eq(GovernedResource::getResourceType, type.name()).eq(GovernedResource::getProjectId, projectId))) {
            if (row.getEffectiveVersionId() == null) continue;
            GovernedResourceVersion version = governedResourceVersionMapper.selectById(row.getEffectiveVersionId());
            if (version == null) continue;
            Map<String, Object> current = CanonicalJson.readMap(version.getSnapshotJson());
            if (type == TransferResourceType.DATA_OBJECT) {
                current = CanonicalJson.readMap(adapterRegistry.require(type.name()).loadEffective(row.getResourceId()).snapshotJson());
            }
            if (!code.equals(String.valueOf(current.get(type.codeField)))) continue;
            if (type == TransferResourceType.MODEL && secretCodec != null) {
                current = secretCodec.restore(new ResourceSnapshot(version.getSnapshotJson(), row.getEffectiveStatus(),
                        version.getSecretPayloadCiphertext(), version.getSecretDigest()));
            }
            return new ExistingResource(row.getResourceId(), current, row.getEffectiveStatus());
        }
        return null;
    }

    private record ExistingResource(Long id, Map<String, Object> configuration, String status) { }

    /** 先验证整包归属，禁止写入一部分后才发现目标项目无效。 */
    private void validateProjectTargets(List<TransferBundle.Resource> resources, TransferImportOptions options,
                                        boolean createProject) {
        if ("GLOBAL".equals(options.normalizedScope())) return;
        if (createProject) {
            if (options.targetProjectId() != null || (options.projectBindings() != null && !options.projectBindings().isEmpty())) {
                throw new IllegalArgumentException("新建项目不能同时指定已有项目绑定");
            }
            long projectCount = resources.stream().filter(resource -> TransferKey.parse(resource.key()).type() == TransferResourceType.PROJECT).count();
            if (projectCount > 1) {
                throw new IllegalArgumentException("包含多个源项目时请显式配置项目绑定，不能同时新建单一目标项目");
            }
            if (projectCount == 0 && (options.projectCode() == null || options.projectCode().isBlank())) {
                throw new IllegalArgumentException("新建项目必须填写项目编码");
            }
            return;
        }
        Set<Long> targets = new LinkedHashSet<>();
        for (TransferBundle.Resource resource : resources) {
            TransferKey source = TransferKey.parse(resource.key());
            if (source.type() == TransferResourceType.RULE_VERSION) continue;
            Map<String, Object> config = source.type() == TransferResourceType.PROJECT
                    ? Map.of("projectId", source.id()) : resource.configuration();
            targets.add(resolveTargetProjectId(config, options, options.targetProjectId()));
        }
        for (Long id : targets) {
            RuleProject target = projectService.getById(id);
            if (target == null || Integer.valueOf(-1).equals(target.getStatus())) {
                throw new IllegalArgumentException("目标项目不存在或已删除，ID=" + id);
            }
        }
    }

    private void applyTargetScope(Map<String, Object> config, TransferResourceType type, TransferImportOptions options,
                                  Long targetProjectId) {
        if (type == TransferResourceType.PROJECT) return;
        config.put("scope", options.normalizedScope());
        config.put("projectId", "GLOBAL".equals(options.normalizedScope())
                ? 0L : resolveTargetProjectId(config, options, targetProjectId));
    }
    private Long projectId(TransferResourceType type, TransferImportOptions options, Long targetProjectId) {
        return type == TransferResourceType.PROJECT ? null : "GLOBAL".equals(options.normalizedScope()) ? 0L : targetProjectId;
    }

    private Long importProject(Map<String, Object> configuration, TransferImportOptions options, String actor) {
        String projectCode = firstText(options.projectCode(), configuration.get("projectCode"));
        String projectName = firstText(options.projectName(), configuration.get("projectName"), projectCode);
        if (projectCode == null || projectCode.isBlank()) throw new IllegalArgumentException("新建项目必须填写项目编码");
        RuleProject project = JSON.parseObject(CanonicalJson.write(configuration), RuleProject.class);
        project.setId(null);
        project.setProjectCode(projectCode);
        project.setProjectName(projectName);
        project.setStatus(1);
        project.setAccessToken(null);
        project.setTraceScopeCode(null);
        project.setCreateBy(actor);
        project.setUpdateBy(actor);
        projectService.createProjectWithToken(project);
        if (project.getId() == null) throw new IllegalStateException("新建项目未返回目标 ID");
        if (governanceBootstrapService != null) governanceBootstrapService.ensure(GovernanceResourceTypes.PROJECT, project.getId());
        return project.getId();
    }

    private String firstText(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }
    private String firstText(Object... values) {
        for (Object value : values) if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
        return null;
    }
    private Long resolveTargetProjectId(Map<String, Object> configuration, TransferImportOptions options,
                                        Long fallback) {
        if ("GLOBAL".equals(options.normalizedScope())) return 0L;
        Object sourceProject = configuration.get("projectId");
        if (options.projectBindings() != null && sourceProject != null) {
            Long mapped = options.projectBindings().get(String.valueOf(sourceProject));
            if (mapped != null && mapped > 0) return mapped;
        }
        if (fallback != null && fallback > 0) return fallback;
        throw new IllegalArgumentException("项目级资源缺少目标项目绑定");
    }
    private void addSuffix(Map<String, Object> config, TransferResourceType type, String suffix) {
        if (config.get(type.codeField) != null) config.put(type.codeField, config.get(type.codeField) + suffix);
        if (config.get(type.nameField) != null && !type.nameField.equals(type.codeField)) config.put(type.nameField, config.get(type.nameField) + suffix);
    }
    @SuppressWarnings("unchecked") private Map<String, Object> copy(Map<String, Object> config) { return JSON.parseObject(JSON.toJSONString(config), Map.class); }
    @SuppressWarnings("unchecked") private Map<String, Object> object(Object value) { return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of(); }
    private List<?> list(Object value) { return value instanceof List<?> list ? list : List.of(); }
    private Long number(Object value) { try { return value == null ? null : Long.valueOf(String.valueOf(value)); } catch (RuntimeException ignored) { return null; } }
    private Integer integer(Object value) { try { return value == null ? null : Integer.valueOf(String.valueOf(value)); } catch (RuntimeException ignored) { return null; } }
    private Map<String, Object> row(TransferBundle.Resource resource, Long id, String status, String message) {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("sourceKey", resource.key()); value.put("targetId", id); value.put("status", status); value.put("message", message); return value;
    }
}
