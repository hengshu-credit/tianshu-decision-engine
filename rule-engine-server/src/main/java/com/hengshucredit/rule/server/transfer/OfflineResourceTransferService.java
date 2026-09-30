package com.hengshucredit.rule.server.transfer;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.governance.GovernedResourceAdapter;
import com.hengshucredit.rule.server.governance.GovernedResourceAdapterRegistry;
import com.hengshucredit.rule.server.governance.ResourceDependencyRef;
import com.hengshucredit.rule.server.governance.ResourceSnapshot;
import com.hengshucredit.rule.server.governance.GovernanceSecretCodec;
import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import com.hengshucredit.rule.server.mapper.GovernedResourceMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper;
import com.hengshucredit.rule.server.mapper.RuleVersionBindingMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionVersionMapper;
import com.hengshucredit.rule.model.entity.RuleVersionBinding;
import com.hengshucredit.rule.model.entity.RuleDefinitionVersion;
import com.hengshucredit.rule.server.service.OperandDependencyCollector;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 生成跨环境配置包。这里不写磁盘、不写制品表，只有用户点击导出接口时才在内存中生成 ZIP。
 * 名单记录、执行日志、账单和访问凭据永远不进入配置包。
 */
@Service
public class OfflineResourceTransferService {
    private static final Set<String> JSON_FIELDS = Set.of(
            "sourceConfig", "authApiConfig", "headerConfig", "queryConfig",
            "requestMapping", "responseMapping", "bodyTemplate", "asyncPollConfig",
            "asyncCallbackConfig", "conditionConfig", "inputFields", "outputFields", "modelJson");
    private static final Set<TransferResourceType> DATA_FREE_TYPES = Set.of(
            TransferResourceType.LIST_LIBRARY);

    @Resource
    private GovernedResourceAdapterRegistry adapterRegistry;
    @Resource
    private GovernedResourceMapper governedResourceMapper;
    @Resource
    private GovernedResourceVersionMapper governedResourceVersionMapper;
    @Resource
    private GovernanceSecretCodec secretCodec;
    @Resource
    private RuleVersionBindingMapper ruleVersionBindingMapper;
    @Resource
    private RuleDefinitionVersionMapper ruleDefinitionVersionMapper;
    @Resource
    private com.hengshucredit.rule.server.mapper.RuleDefinitionMapper ruleDefinitionMapper;
    private final TransferBundleCodec codec = new TransferBundleCodec();

    @Transactional(readOnly = true)
    public byte[] export(List<TransferRootRequest> requests) {
        List<TransferKey> roots = normalizeRoots(requests);
        Map<String, TransferBundle.Resource> resources = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        Deque<TransferKey> queue = new ArrayDeque<>(roots);
        Set<String> visiting = new LinkedHashSet<>();
        while (!queue.isEmpty()) {
            TransferKey key = queue.removeFirst();
            if (resources.containsKey(key.value())) continue;
            if (!visiting.add(key.value())) throw new IllegalArgumentException("离线导出依赖形成循环: " + key.value());
            if (key.type() == TransferResourceType.RULE_VERSION) {
                resources.put(key.value(), exportRuleVersion(key, queue, warnings));
                visiting.remove(key.value());
                continue;
            }
            GovernedResourceAdapter adapter = adapterRegistry.require(key.type().name());
            ResourceSnapshot snapshot = adapter.loadEffective(key.id());
            Map<String, Object> configuration = CanonicalJson.readMap(snapshot.snapshotJson());
            // 模型文件不是凭据：还原后打入迁移包，目标治理适配器会再次加密保存。
            // 外数/数据库/鉴权敏感字段不还原，必须在目标环境重新配置。
            if (key.type() == TransferResourceType.MODEL && secretCodec != null) {
                configuration = secretCodec.restore(snapshot);
            }
            List<TransferBundle.Reference> references = new ArrayList<>();
            for (ResourceDependencyRef dependency : adapter.collectDependencies(snapshot)) {
                if (dependency.targetResourceType() == null || dependency.targetResourceId() == null) {
                    if (dependency.required()) throw new IllegalArgumentException("离线导出存在缺失依赖: " + key.value());
                    continue;
                }
                TransferResourceType targetType;
                try { targetType = TransferResourceType.valueOf(dependency.targetResourceType().trim().toUpperCase(Locale.ROOT)); }
                catch (RuntimeException unsupported) {
                    if (dependency.required()) throw new IllegalArgumentException("离线包不支持上游依赖: " + dependency.targetResourceType());
                    warnings.add("已跳过可选依赖 " + dependency.targetResourceType() + ":" + dependency.targetResourceId());
                    continue;
                }
                if (targetType == TransferResourceType.LIST_RECORD_BATCH) {
                    warnings.add("名单记录数据未随配置包导出: " + dependency.targetResourceId());
                    continue;
                }
                String pointer = toPointer(dependency.referencePath());
                Object referencedValue = null;
                try { TransferJsonPath.read(configuration, pointer); }
                catch (RuntimeException missingPath) {
                    if (dependency.required()) throw new IllegalArgumentException("离线导出引用路径不存在: " + key.value() + pointer, missingPath);
                    warnings.add("可选依赖引用路径不存在: " + key.value() + pointer);
                    continue;
                }
                referencedValue = TransferJsonPath.read(configuration, pointer);
                String targetKey = new TransferKey(targetType, dependency.targetResourceId()).value();
                String childPath = childPath(targetType, dependency.targetResourceId(), referencedValue);
                references.add(new TransferBundle.Reference(pointer, targetKey, childPath));
                queue.addLast(new TransferKey(targetType, dependency.targetResourceId()));
            }
            if (key.type() == TransferResourceType.RULE) {
                collectFixedRuleVersions(configuration, references, queue, warnings);
            }
            resources.put(key.value(), new TransferBundle.Resource(key.value(), configuration,
                    references, requiredEnvironmentFields(key.type(), configuration)));
            visiting.remove(key.value());
        }
        return codec.encode(new TransferBundle(roots.stream().map(TransferKey::value).toList(),
                new ArrayList<>(resources.values()), warnings));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> preview(byte[] bytes) {
        return preview(bytes, new TransferImportOptions(null, "PROJECT", "REUSE", "SUFFIX", "_imported", false, null, null, Map.of()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> preview(byte[] bytes, TransferImportOptions options) {
        TransferBundleCodec.Decoded decoded = codec.decode(bytes);
        List<Map<String, Object>> resources = new ArrayList<>();
        List<TransferConflict> conflicts = new ArrayList<>();
        Map<String, PreviewTarget> targets = new LinkedHashMap<>();
        for (TransferBundle.Resource resource : OfflineResourceImportService.topologicalOrder(decoded.bundle().resources())) {
            TransferKey key = TransferKey.parse(resource.key());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", resource.key());
            item.put("resourceType", key.type().name());
            item.put("sourceResourceId", key.id());
            item.put("resourceCode", identity(resource.configuration(), key.type()));
            item.put("scope", resource.configuration().get("scope"));
            item.put("sourceProjectId", resource.configuration().get("projectId"));
            item.put("referenceCount", resource.references().size());
            item.put("references", resource.references().stream().map(reference -> {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("targetKey", reference.targetKey());
                edge.put("path", reference.path());
                edge.put("childPath", reference.childPath());
                return edge;
            }).toList());
            item.put("requiredEnvironmentFields", resource.requiredEnvironmentFields());
            item.put("configurationOnly", DATA_FREE_TYPES.contains(key.type()));
            resources.add(item);
            TransferConflict conflict = findConflict(resource, key, options, targets);
            if (conflict != null) conflicts.add(conflict);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("packageKind", TransferBundleCodec.PACKAGE_KIND);
        result.put("packageDigest", decoded.packageDigest());
        result.put("roots", decoded.bundle().roots());
        result.put("resources", resources);
        result.put("warnings", decoded.bundle().warnings());
        result.put("listDataIncluded", false);
        result.put("targetScope", options.normalizedScope());
        result.put("targetProjectId", options.targetProjectId());
        result.put("createProject", Boolean.TRUE.equals(options.createProject()));
        result.put("projectCode", options.projectCode());
        result.put("projectName", options.projectName());
        result.put("projectBindings", options.projectBindings() == null ? Map.of() : options.projectBindings());
        result.put("publishRules", Boolean.TRUE.equals(options.publishRules()));
        result.put("variablePolicy", options.normalizedVariablePolicy());
        result.put("resourcePolicy", options.normalizedResourcePolicy());
        result.put("suffix", options.normalizedSuffix());
        result.put("conflicts", conflicts);
        result.put("conflictCount", conflicts.size());
        result.put("requiresTargetProjectSelection", resources.stream().anyMatch(item ->
                !"PROJECT".equals(item.get("resourceType"))
                        && !"GLOBAL".equalsIgnoreCase(String.valueOf(item.getOrDefault("scope", "")))
                        && !Boolean.TRUE.equals(options.createProject())));
        result.put("requiresProjectCreation", !"GLOBAL".equals(options.normalizedScope())
                && Boolean.TRUE.equals(options.createProject()));
        return result;
    }

    private List<TransferKey> normalizeRoots(List<TransferRootRequest> requests) {
        if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("请选择至少一个导出资源");
        LinkedHashMap<String, TransferKey> unique = new LinkedHashMap<>();
        for (TransferRootRequest request : requests) {
            TransferKey key = request.key();
            unique.put(key.value(), key);
        }
        return new ArrayList<>(unique.values());
    }

    private TransferBundle.Resource exportRuleVersion(TransferKey key, Deque<TransferKey> queue,
                                                       List<String> warnings) {
        if (ruleVersionBindingMapper == null || ruleDefinitionVersionMapper == null)
            throw new IllegalStateException("规则版本迁移依赖版本绑定数据源");
        RuleVersionBinding binding = ruleVersionBindingMapper.selectById(key.id());
        if (binding == null) throw new IllegalArgumentException("指定规则版本绑定不存在: " + key.value());
        RuleDefinitionVersion snapshot = binding.getSnapshotId() == null ? null : ruleDefinitionVersionMapper.selectById(binding.getSnapshotId());
        if (snapshot == null) throw new IllegalArgumentException("指定规则版本快照不存在: " + key.value());
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("id", binding.getId()); config.put("definitionId", binding.getDefinitionId());
        config.put("versionNo", binding.getVersionNo()); config.put("generation", binding.getGeneration());
        config.put("status", binding.getStatus()); config.put("snapshotId", binding.getSnapshotId());
        config.put("snapshot", CanonicalJson.readMap(CanonicalJson.write(snapshot)));
        List<TransferBundle.Reference> refs = List.of(
                new TransferBundle.Reference("/definitionId", "RULE:" + binding.getDefinitionId(), null));
        queue.addLast(new TransferKey(TransferResourceType.RULE, binding.getDefinitionId()));
        return new TransferBundle.Resource(key.value(), config, refs,
                List.of("目标环境需要在规则发布后重新建立该业务版本绑定"));
    }

    private void collectFixedRuleVersions(Map<String, Object> configuration,
                                          List<TransferBundle.Reference> references,
                                          Deque<TransferKey> queue,
                                          List<String> warnings) {
        Map<String, Object> content = object(configuration.get("content"));
        Object modelJson = content.get("modelJson");
        if (!(modelJson instanceof String script) || script.isBlank()) return;
        Object model;
        try { model = JSON.parse(script); } catch (RuntimeException invalid) {
            warnings.add("规则模型 JSON 无法解析，未能分析固定版本引用: " + configuration.get("ruleCode"));
            return;
        }
        for (OperandDependencyCollector.Reference reference : OperandDependencyCollector.collectReferences(model)) {
            if (!"RULE".equalsIgnoreCase(reference.getRefType())
                    || !"FIXED".equalsIgnoreCase(reference.getVersionMode())
                    || reference.getVersionBindingId() == null) continue;
            String bindingKey = new TransferKey(TransferResourceType.RULE_VERSION,
                    reference.getVersionBindingId()).value();
            String pointer = "/content/modelJson/@json" + toPointer(reference.getPath());
            references.add(new TransferBundle.Reference(pointer, bindingKey, null));
            queue.addLast(new TransferKey(TransferResourceType.RULE_VERSION, reference.getVersionBindingId()));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private String toPointer(String path) {
        if (path == null || !path.startsWith("$")) throw new IllegalArgumentException("上游引用路径无效: " + path);
        String value = path.substring(1);
        if (value.isEmpty()) return "/";
        StringBuilder pointer = new StringBuilder();
        String[] pieces = value.split("\\.");
        for (String piece : pieces) {
            if (piece.isBlank()) continue;
            int bracket = piece.indexOf('[');
                if (bracket >= 0) {
                appendPointer(pointer, piece.substring(0, bracket));
                int close = piece.indexOf(']', bracket);
                if (close < 0) throw new IllegalArgumentException("上游引用路径无效: " + path);
                    String bracketToken = piece.substring(bracket + 1, close);
                    if ("json".equalsIgnoreCase(bracketToken)) pointer.append("/@json");
                    else appendPointer(pointer, bracketToken);
            } else {
                if (!pointer.isEmpty() && JSON_FIELDS.contains(lastToken(pointer))) pointer.append("/@json");
                appendPointer(pointer, piece);
            }
        }
        return pointer.toString();
    }

    private void appendPointer(StringBuilder pointer, String token) {
        if (token == null || token.isBlank()) return;
        pointer.append('/').append(token.replace("~", "~0").replace("/", "~1"));
    }

    private String lastToken(StringBuilder pointer) {
        int slash = pointer.lastIndexOf("/");
        String token = slash < 0 ? pointer.toString() : pointer.substring(slash + 1);
        return token.replace("~1", "/").replace("~0", "~");
    }

    private List<String> requiredEnvironmentFields(TransferResourceType type, Map<String, Object> configuration) {
        List<String> fields = new ArrayList<>();
        Object configured = configuration.get("_secretConfigured");
        if (configured instanceof Map<?, ?> map) map.keySet().forEach(key -> fields.add(String.valueOf(key)));
        if ((type == TransferResourceType.FUNCTION)
                && ("JAVA".equalsIgnoreCase(String.valueOf(configuration.get("implType")))
                || "BEAN".equalsIgnoreCase(String.valueOf(configuration.get("implType"))))) {
            fields.add("目标环境需提供同名 Java 类或 Spring Bean");
        }
        if (type == TransferResourceType.DATABASE || type == TransferResourceType.EXTERNAL_DATASOURCE
                || type == TransferResourceType.EXTERNAL_API) {
            fields.add("目标环境连接、地址和凭据需复核");
        }
        return fields.stream().distinct().toList();
    }

    private String identity(Map<String, Object> configuration, TransferResourceType type) {
        Object value = configuration.get(type.codeField);
        return value == null ? null : String.valueOf(value);
    }

    private String childPath(TransferResourceType targetType, long targetId, Object referencedValue) {
        if (targetType != TransferResourceType.DATA_OBJECT || !(referencedValue instanceof Number number)) return null;
        try {
            Map<String, Object> object = CanonicalJson.readMap(adapterRegistry.require(targetType.name())
                    .loadEffective(targetId).snapshotJson());
            Object fields = object.get("fields");
            if (!(fields instanceof List<?> list)) return null;
            for (int index = 0; index < list.size(); index++) {
                if (list.get(index) instanceof Map<?, ?> field
                        && String.valueOf(field.get("id")).equals(String.valueOf(number.longValue()))) {
                    return "/fields/" + index + "/id";
                }
            }
        } catch (RuntimeException ignored) {
            // Child ownership is advisory in the package; the root dependency remains required.
        }
        return null;
    }

    private TransferConflict findConflict(TransferBundle.Resource resource, TransferKey key,
                                         TransferImportOptions options, Map<String, PreviewTarget> targets) {
        String code = identity(resource.configuration(), key.type());
        if (key.type() == TransferResourceType.PROJECT) {
            if ("GLOBAL".equals(options.normalizedScope())) {
                targets.put(resource.key(), new PreviewTarget(0L, Map.of(), true));
                return null;
            }
            if (!Boolean.TRUE.equals(options.createProject())) {
                Long bound = options.projectBindings() == null ? null : options.projectBindings().get(String.valueOf(key.id()));
                if (bound == null || bound <= 0) bound = options.targetProjectId();
                if (bound != null && bound > 0) targets.put(resource.key(), new PreviewTarget(bound, Map.of(), true));
                return bound != null && bound > 0 ? null : new TransferConflict(resource.key(), key.type().name(), code,
                        "TARGET_PROJECT_REQUIRED", null, "SELECT_PROJECT", "请选择目标项目 ID 或新建项目，不能按源项目编码自动绑定");
            }
            if (options.projectCode() != null && !options.projectCode().isBlank()) code = options.projectCode().trim();
            else if ("SUFFIX".equals(options.normalizedResourcePolicy())) code += options.normalizedSuffix();
            if (code == null || governedResourceMapper == null || governedResourceVersionMapper == null) return null;
            List<GovernedResource> projects = governedResourceMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GovernedResource>()
                            .eq(GovernedResource::getResourceType, key.type().name()));
            for (GovernedResource candidate : projects) {
                if (candidate.getEffectiveVersionId() == null) continue;
                GovernedResourceVersion version = governedResourceVersionMapper.selectById(candidate.getEffectiveVersionId());
                if (version == null) continue;
                Map<String, Object> current = CanonicalJson.readMap(version.getSnapshotJson());
                if (!code.equals(identity(current, key.type()))) continue;
                return new TransferConflict(resource.key(), key.type().name(), code,
                        "CONFIG_CONFLICT", "PROJECT:" + candidate.getResourceId(), "SELECT_PROJECT",
                        "目标环境已存在同编码项目，请绑定已有项目或修改项目编码后新建");
            }
            return null;
        }
        if (code == null || governedResourceMapper == null || governedResourceVersionMapper == null) return null;
        boolean newProject = !"GLOBAL".equals(options.normalizedScope()) && Boolean.TRUE.equals(options.createProject());
        if (newProject && key.type() != TransferResourceType.RULE) return null;
        long targetProject = "GLOBAL".equals(options.normalizedScope()) ? 0L : options.targetProjectId() == null ? -1L : options.targetProjectId();
        if (!"GLOBAL".equals(options.normalizedScope()) && options.projectBindings() != null) {
            Long mapped = options.projectBindings().get(String.valueOf(resource.configuration().get("projectId")));
            if (mapped != null && mapped > 0) targetProject = mapped;
        }
        com.hengshucredit.rule.model.entity.RuleDefinition occupiedRule = key.type() == TransferResourceType.RULE && ruleDefinitionMapper != null
                ? ruleDefinitionMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.hengshucredit.rule.model.entity.RuleDefinition>()
                .eq(com.hengshucredit.rule.model.entity.RuleDefinition::getRuleCode, code)) : null;
        if (occupiedRule != null && (newProject || !java.util.Objects.equals(occupiedRule.getProjectId(), targetProject))) {
            return ruleCodeConflict(resource, code, occupiedRule.getId());
        }
        if (newProject) return null;
        if (targetProject < 0) return new TransferConflict(resource.key(), key.type().name(), code,
                "TARGET_PROJECT_REQUIRED", null, "SELECT_PROJECT", "项目级资源需要先选择目标项目");
        List<GovernedResource> candidates = governedResourceMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GovernedResource>()
                .eq(GovernedResource::getResourceType, key.type().name()).eq(GovernedResource::getProjectId, targetProject));
        for (GovernedResource candidate : candidates) {
            GovernedResourceVersion version = candidate.getEffectiveVersionId() == null ? null
                    : governedResourceVersionMapper.selectById(candidate.getEffectiveVersionId());
            if (version == null) continue;
            Map<String, Object> current = CanonicalJson.readMap(version.getSnapshotJson());
            if (key.type() == TransferResourceType.DATA_OBJECT) {
                current = CanonicalJson.readMap(adapterRegistry.require(key.type().name()).loadEffective(candidate.getResourceId()).snapshotJson());
            }
            if (!code.equals(identity(current, key.type()))) continue;
            if (key.type() == TransferResourceType.MODEL && secretCodec != null) {
                current = secretCodec.restore(new ResourceSnapshot(version.getSnapshotJson(), candidate.getEffectiveStatus(),
                        version.getSecretPayloadCiphertext(), version.getSecretDigest()));
            }
            Map<String, Object> incoming = previewConfiguration(resource, targets);
            String conflictType = TransferResourceComparison.conflict(key.type(), incoming, current, candidate.getEffectiveStatus());
            String action = key.type() == TransferResourceType.VARIABLE
                    ? options.normalizedVariablePolicy() : options.normalizedResourcePolicy();
            boolean canReuse = TransferResourceComparison.reusable(key.type(), incoming, current, candidate.getEffectiveStatus());
            if (("REUSE".equals(action) && canReuse) || "OVERWRITE".equals(action)) {
                boolean reused = "REUSE".equals(action) && canReuse;
                Map<String, Long> childIds = key.type() == TransferResourceType.DATA_OBJECT && reused
                        ? previewFieldIds(resource.configuration(), current) : Map.of();
                targets.put(resource.key(), new PreviewTarget(candidate.getResourceId(), childIds, reused));
            }
            if ("REUSE".equals(action) && !canReuse) action = "SUFFIX";
            return new TransferConflict(resource.key(), key.type().name(), code, conflictType,
                    key.type().name() + ":" + candidate.getResourceId(), action,
                    canReuse ? "目标环境已存在可复用的同编码资源，请确认复用、覆盖或添加后缀"
                            : "同编码资源的类型、配置、状态或环境凭据未匹配，不能复用；请更改为覆盖或添加后缀（类型冲突只能添加后缀）");
        }
        return occupiedRule == null ? null : ruleCodeConflict(resource, code, occupiedRule.getId());
    }

    private TransferConflict ruleCodeConflict(TransferBundle.Resource resource, String code, Long existingId) {
        return new TransferConflict(resource.key(), "RULE", code, "RULE_CODE_CONFLICT", "RULE:" + existingId, "SUFFIX",
                "规则编码在全库唯一；其他项目或未发布规则已使用该编码，请选择新建并追加后缀，不能跨项目复用或覆盖");
    }

    private Map<String, Object> previewConfiguration(TransferBundle.Resource resource, Map<String, PreviewTarget> targets) {
        Map<String, Object> value = CanonicalJson.readMap(CanonicalJson.write(resource.configuration()));
        for (TransferBundle.Reference reference : resource.references()) {
            PreviewTarget target = targets.get(reference.targetKey());
            Object replacement = "UNRESOLVED_TARGET:" + reference.targetKey();
            if (target != null) {
                if (reference.childPath() == null) replacement = target.id();
                else if (target.reused()) {
                    Long childId = target.childIds().get(reference.childPath());
                    if (childId != null) replacement = childId;
                }
            }
            TransferJsonPath.replace(value, reference.path(), replacement);
        }
        return value;
    }

    private Map<String, Long> previewFieldIds(Map<String, Object> source, Map<String, Object> target) {
        Map<Long, Long> ids = TransferObjectFieldIndex.of(source).matchingIds(TransferObjectFieldIndex.of(target));
        Map<String, Long> result = new LinkedHashMap<>();
        if (source.get("fields") instanceof List<?> fields) {
            for (int i = 0; i < fields.size(); i++) {
                Map<?, ?> field = (Map<?, ?>) fields.get(i);
                Long targetId = ids.get(Long.valueOf(String.valueOf(field.get("id"))));
                if (targetId != null) result.put("/fields/" + i + "/id", targetId);
            }
        }
        return result;
    }

    private record PreviewTarget(Long id, Map<String, Long> childIds, boolean reused) { }

}
