package com.hengshucredit.rule.server.transfer;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.dto.GovernanceReviewRequest;
import com.hengshucredit.rule.model.dto.GovernanceSubmitRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.governance.GovernanceApprovalService;
import com.hengshucredit.rule.server.governance.GovernedResourceAdapter;
import com.hengshucredit.rule.server.governance.GovernedResourceAdapterRegistry;
import com.hengshucredit.rule.server.governance.ResourceSnapshot;
import com.hengshucredit.rule.server.governance.GovernanceResourceTypes;
import com.hengshucredit.rule.server.mapper.GovernedResourceMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper;
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
    @Resource private GovernedResourceAdapterRegistry adapterRegistry;
    @Resource private GovernanceApprovalService approvalService;
    @Resource private GovernedResourceMapper governedResourceMapper;
    @Resource private GovernedResourceVersionMapper governedResourceVersionMapper;
    @Resource private RuleDefinitionService definitionService;
    @Resource private RuleLifecycleService lifecycleService;
    @Resource private RuleDraftService draftService;
    @Resource private RuleProjectService projectService;
    @Resource private com.hengshucredit.rule.server.governance.GovernanceResourceBootstrapService governanceBootstrapService;
    private final TransferBundleCodec codec = new TransferBundleCodec();

    @Transactional
    public Map<String, Object> apply(byte[] bytes, TransferImportOptions options, String actor) {
        if (actor == null || actor.isBlank()) actor = ConsoleOperatorResolver.SYSTEM_CONSOLE;
        TransferBundle bundle = codec.decode(bytes).bundle();
        boolean hasProjectResource = bundle.resources().stream().anyMatch(resource ->
                TransferKey.parse(resource.key()).type() == TransferResourceType.PROJECT);
        if (Boolean.TRUE.equals(options.createProject()) && !hasProjectResource) {
            throw new IllegalArgumentException("新建项目必须随离线包导入项目配置");
        }
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
        Map<String, Map<Long, Long>> childMap = new HashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();
        Long effectiveTargetProjectId = options.targetProjectId();
        boolean createdProject = false;
        for (TransferBundle.Resource resource : ordered) {
            TransferKey sourceKey = TransferKey.parse(resource.key());
            if (sourceKey.type() == TransferResourceType.RULE_VERSION) {
                throw new IllegalArgumentException("固定版本绑定必须在目标规则发布后重新建立，请先导入并发布对应规则: " + resource.key());
            }
            if (sourceKey.type() == TransferResourceType.RULE
                    && resource.references().stream().anyMatch(reference -> reference.targetKey().startsWith("RULE_VERSION:"))) {
                throw new IllegalArgumentException("包含固定版本引用的规则需要目标规则发布后重建版本绑定: " + resource.key());
            }
            if (sourceKey.type() == TransferResourceType.PROJECT && "GLOBAL".equals(options.normalizedScope())) {
                idMap.put(resource.key(), 0L);
                result.add(row(resource, 0L, "SKIPPED", "全局导入不创建项目，项目级资源将按全局范围写入"));
                continue;
            }
            Long existingId = sourceKey.type() == TransferResourceType.PROJECT && idMap.containsKey(resource.key())
                    ? idMap.get(resource.key()) : sourceKey.type() == TransferResourceType.PROJECT
                    && !Boolean.TRUE.equals(options.createProject())
                    && options.targetProjectId() != null && options.targetProjectId() > 0
                    ? options.targetProjectId() : findExisting(sourceKey.type(), resource.configuration(), options);
            String policy = sourceKey.type() == TransferResourceType.VARIABLE
                    ? options.normalizedVariablePolicy() : options.normalizedResourcePolicy();
            if (existingId != null && ("REUSE".equals(policy) || "IDENTICAL".equals(policy))) {
                idMap.put(resource.key(), existingId);
                if (sourceKey.type() == TransferResourceType.DATA_OBJECT) childMap.put(resource.key(),
                        fieldMap(resource.configuration(), sourceKey, existingId));
                result.add(row(resource, existingId, "REUSED", null));
                continue;
            }
            Map<String, Object> configuration = copy(resource.configuration());
            if (sourceKey.type() == TransferResourceType.PROJECT) {
                if (existingId != null) {
                    if ("OVERWRITE".equals(policy)) {
                        throw new IllegalArgumentException("项目不能覆盖导入，请选择绑定已有项目或使用新编码新建");
                    }
                    idMap.put(resource.key(), existingId);
                    result.add(row(resource, existingId, "BOUND", "已绑定目标项目"));
                    effectiveTargetProjectId = effectiveTargetProjectId == null ? existingId : effectiveTargetProjectId;
                    continue;
                }
                if (!Boolean.TRUE.equals(options.createProject())) {
                    throw new IllegalArgumentException("目标项目不存在，请选择已有项目或勾选新建项目");
                }
                if (createdProject) {
                    throw new IllegalArgumentException("一个离线包只能在本次导入中新建一个目标项目");
                }
                if (!options.normalizedSuffix().isBlank() && "SUFFIX".equals(policy)) {
                    addSuffix(configuration, sourceKey.type(), options.normalizedSuffix());
                }
                Long targetId = importProject(configuration, options, actor);
                idMap.put(resource.key(), targetId);
                effectiveTargetProjectId = targetId;
                createdProject = true;
                result.add(row(resource, targetId, "CREATED", null));
                continue;
            }
            applyTargetScope(configuration, sourceKey.type(), options, effectiveTargetProjectId);
            Long resourceTargetProjectId = number(configuration.get("projectId"));
            if (existingId != null && "OVERWRITE".equals(policy)) {
                // Existing resources are updated only through the governance approval service.
            } else if (existingId != null) {
                addSuffix(configuration, sourceKey.type(), options.normalizedSuffix());
                existingId = null;
            }
            rewriteReferences(configuration, resource, byKey, idMap, childMap);
            ResourceSnapshot snapshot = ResourceSnapshot.ofJson(CanonicalJson.write(configuration));
            Long targetId;
            if (sourceKey.type() == TransferResourceType.RULE) {
                targetId = importRuleDraft(configuration, existingId, options, resourceTargetProjectId, actor);
            } else {
                targetId = approveResource(sourceKey.type(), existingId, options, resourceTargetProjectId, snapshot, actor);
            }
            idMap.put(resource.key(), targetId);
            if (sourceKey.type() == TransferResourceType.DATA_OBJECT) {
                childMap.put(resource.key(), fieldMap(configuration, sourceKey, targetId));
            }
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

    private Long approveResource(TransferResourceType type, Long existingId, TransferImportOptions options,
                                 Long targetProjectId, ResourceSnapshot snapshot, String actor) {
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
        var approved = approvalService.approve(request.getId(), new GovernanceReviewRequest() {{ setComment("离线配置迁移自动审批"); }}, actor);
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
        draftService.save(save);
        return created.getId();
    }

    private List<TransferBundle.Resource> topologicalOrder(List<TransferBundle.Resource> resources) {
        Map<String, TransferBundle.Resource> byKey = resources.stream().collect(java.util.stream.Collectors.toMap(
                TransferBundle.Resource::key, value -> value, (left, right) -> left, LinkedHashMap::new));
        List<TransferBundle.Resource> ordered = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        while (ordered.size() < resources.size()) {
            boolean progressed = false;
            for (TransferBundle.Resource resource : resources) {
                if (done.contains(resource.key())) continue;
                boolean ready = resource.references().stream().allMatch(reference -> byKey.containsKey(reference.targetKey()) && done.contains(reference.targetKey()));
                if (ready) { ordered.add(resource); done.add(resource.key()); progressed = true; }
            }
            if (!progressed) throw new IllegalArgumentException("离线资源依赖形成循环，无法安全导入");
        }
        return ordered;
    }

    private void rewriteReferences(Map<String, Object> configuration, TransferBundle.Resource resource,
                                   Map<String, TransferBundle.Resource> byKey, Map<String, Long> idMap,
                                   Map<String, Map<Long, Long>> childMap) {
        for (TransferBundle.Reference reference : resource.references()) {
            Long target = idMap.get(reference.targetKey());
            if (target == null) throw new IllegalArgumentException("上游资源尚未导入: " + reference.targetKey());
            if (reference.childPath() != null) {
                Object sourceChild = TransferJsonPath.read(byKey.get(reference.targetKey()).configuration(), reference.childPath());
                Long mapped = childMap.getOrDefault(reference.targetKey(), Map.of()).get(number(sourceChild));
                if (mapped == null) throw new IllegalArgumentException("数据对象子字段无法映射: " + reference.targetKey());
                target = mapped;
            }
            TransferJsonPath.replace(configuration, reference.path(), target);
        }
    }

    private Map<Long, Long> fieldMap(Map<String, Object> source, TransferKey sourceKey, Long targetId) {
        try {
            Map<String, Object> target = CanonicalJson.readMap(adapterRegistry.require(sourceKey.type().name()).loadEffective(targetId).snapshotJson());
            List<?> sourceFields = list(source.get("fields"));
            List<?> targetFields = list(target.get("fields"));
            Map<Long, Long> result = new HashMap<>();
            for (Object left : sourceFields) {
                Map<String, Object> leftMap = object(left);
                for (Object right : targetFields) {
                    Map<String, Object> rightMap = object(right);
                    if (String.valueOf(leftMap.get("varCode")).equals(String.valueOf(rightMap.get("varCode")))
                            && String.valueOf(leftMap.get("varType")).equals(String.valueOf(rightMap.get("varType")))) {
                        Long sourceId = number(leftMap.get("id")); Long targetFieldId = number(rightMap.get("id"));
                        if (sourceId != null && targetFieldId != null) result.put(sourceId, targetFieldId);
                    }
                }
            }
            return result;
        } catch (RuntimeException ignored) { return Map.of(); }
    }

    private Long findExisting(TransferResourceType type, Map<String, Object> config, TransferImportOptions options) {
        String code = String.valueOf(config.get(type.codeField));
        if (code == null || "null".equals(code) || governedResourceMapper == null) return null;
        if (type == TransferResourceType.PROJECT) {
            for (GovernedResource row : governedResourceMapper.selectList(new LambdaQueryWrapper<GovernedResource>()
                    .eq(GovernedResource::getResourceType, type.name()))) {
                if (row.getEffectiveVersionId() == null) continue;
                GovernedResourceVersion version = governedResourceVersionMapper.selectById(row.getEffectiveVersionId());
                if (version != null && code.equals(String.valueOf(CanonicalJson.readMap(version.getSnapshotJson()).get(type.codeField)))) {
                    return row.getResourceId();
                }
            }
            return null;
        }
        long projectId = "GLOBAL".equals(options.normalizedScope()) ? 0L : options.targetProjectId() == null ? -1L : options.targetProjectId();
        if (projectId < 0) return null;
        for (GovernedResource row : governedResourceMapper.selectList(new LambdaQueryWrapper<GovernedResource>()
                .eq(GovernedResource::getResourceType, type.name()).eq(GovernedResource::getProjectId, projectId))) {
            if (row.getEffectiveVersionId() == null) continue;
            GovernedResourceVersion version = governedResourceVersionMapper.selectById(row.getEffectiveVersionId());
            if (version != null && code.equals(String.valueOf(CanonicalJson.readMap(version.getSnapshotJson()).get(type.codeField)))) return row.getResourceId();
        }
        return null;
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
    private Map<String, Object> row(TransferBundle.Resource resource, Long id, String status, String message) {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("sourceKey", resource.key()); value.put("targetId", id); value.put("status", status); value.put("message", message); return value;
    }
}
