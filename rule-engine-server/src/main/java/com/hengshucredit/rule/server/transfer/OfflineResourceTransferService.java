package com.hengshucredit.rule.server.transfer;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.governance.GovernedResourceAdapter;
import com.hengshucredit.rule.server.governance.GovernedResourceAdapterRegistry;
import com.hengshucredit.rule.server.governance.ResourceDependencyRef;
import com.hengshucredit.rule.server.governance.ResourceSnapshot;
import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import com.hengshucredit.rule.server.mapper.GovernedResourceMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper;
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
            "asyncCallbackConfig", "conditionConfig", "inputFields", "outputFields");
    private static final Set<TransferResourceType> DATA_FREE_TYPES = Set.of(
            TransferResourceType.LIST_LIBRARY);

    @Resource
    private GovernedResourceAdapterRegistry adapterRegistry;
    @Resource
    private GovernedResourceMapper governedResourceMapper;
    @Resource
    private GovernedResourceVersionMapper governedResourceVersionMapper;
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
            GovernedResourceAdapter adapter = adapterRegistry.require(key.type().name());
            ResourceSnapshot snapshot = adapter.loadEffective(key.id());
            Map<String, Object> configuration = CanonicalJson.readMap(snapshot.snapshotJson());
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
                try { TransferJsonPath.read(configuration, pointer); }
                catch (RuntimeException missingPath) {
                    if (dependency.required()) throw new IllegalArgumentException("离线导出引用路径不存在: " + key.value() + pointer, missingPath);
                    warnings.add("可选依赖引用路径不存在: " + key.value() + pointer);
                    continue;
                }
                String targetKey = new TransferKey(targetType, dependency.targetResourceId()).value();
                references.add(new TransferBundle.Reference(pointer, targetKey, null));
                queue.addLast(new TransferKey(targetType, dependency.targetResourceId()));
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
        for (TransferBundle.Resource resource : decoded.bundle().resources()) {
            TransferKey key = TransferKey.parse(resource.key());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", resource.key());
            item.put("resourceType", key.type().name());
            item.put("sourceResourceId", key.id());
            item.put("resourceCode", identity(resource.configuration(), key.type()));
            item.put("scope", resource.configuration().get("scope"));
            item.put("sourceProjectId", resource.configuration().get("projectId"));
            item.put("referenceCount", resource.references().size());
            item.put("requiredEnvironmentFields", resource.requiredEnvironmentFields());
            item.put("configurationOnly", DATA_FREE_TYPES.contains(key.type()));
            resources.add(item);
            TransferConflict conflict = findConflict(resource, key, options);
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
        result.put("variablePolicy", options.normalizedVariablePolicy());
        result.put("resourcePolicy", options.normalizedResourcePolicy());
        result.put("suffix", options.normalizedSuffix());
        result.put("conflicts", conflicts);
        result.put("conflictCount", conflicts.size());
        result.put("requiresTargetProjectSelection", resources.stream().anyMatch(item ->
                !"GLOBAL".equalsIgnoreCase(String.valueOf(item.getOrDefault("scope", "")))));
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
                appendPointer(pointer, piece.substring(bracket + 1, close));
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

    private TransferConflict findConflict(TransferBundle.Resource resource, TransferKey key,
                                         TransferImportOptions options) {
        String code = identity(resource.configuration(), key.type());
        if (code == null || governedResourceMapper == null || governedResourceVersionMapper == null) return null;
        long targetProject = "GLOBAL".equals(options.normalizedScope()) ? 0L : options.targetProjectId() == null ? -1L : options.targetProjectId();
        if (targetProject < 0) return new TransferConflict(resource.key(), key.type().name(), code,
                "TARGET_PROJECT_REQUIRED", null, "SELECT_PROJECT", "项目级资源需要先选择目标项目");
        List<GovernedResource> candidates = governedResourceMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GovernedResource>()
                .eq(GovernedResource::getResourceType, key.type().name()).eq(GovernedResource::getProjectId, targetProject));
        for (GovernedResource candidate : candidates) {
            GovernedResourceVersion version = candidate.getEffectiveVersionId() == null ? null
                    : governedResourceVersionMapper.selectById(candidate.getEffectiveVersionId());
            if (version == null) continue;
            Map<String, Object> current = CanonicalJson.readMap(version.getSnapshotJson());
            if (!code.equals(identity(current, key.type()))) continue;
            boolean sameType = key.type() != TransferResourceType.VARIABLE
                    || String.valueOf(resource.configuration().get("varType")).equals(String.valueOf(current.get("varType")));
            String conflictType = sameType && comparable(resource.configuration()).equals(comparable(current))
                    ? "IDENTICAL" : (sameType ? "CONFIG_CONFLICT" : "TYPE_CONFLICT");
            String action = key.type() == TransferResourceType.VARIABLE
                    ? (sameType ? options.normalizedVariablePolicy() : "SUFFIX") : options.normalizedResourcePolicy();
            return new TransferConflict(resource.key(), key.type().name(), code, conflictType,
                    key.type().name() + ":" + candidate.getResourceId(), action,
                    "目标环境已存在同编码资源，请确认复用、覆盖或添加后缀");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> comparable(Map<String, Object> input) {
        Map<String, Object> copy = (Map<String, Object>) JSON.parseObject(JSON.toJSONString(input), Map.class);
        removeRecursive(copy, Set.of("id", "projectId", "definitionId", "datasourceId", "createTime", "updateTime", "accessToken"));
        return copy;
    }

    @SuppressWarnings("unchecked")
    private void removeRecursive(Object value, Set<String> keys) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            keys.forEach(map::remove);
            new ArrayList<>(map.values()).forEach(item -> removeRecursive(item, keys));
        } else if (value instanceof List<?> list) {
            list.forEach(item -> removeRecursive(item, keys));
        }
    }
}
