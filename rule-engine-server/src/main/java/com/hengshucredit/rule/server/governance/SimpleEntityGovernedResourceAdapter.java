package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.server.artifact.CanonicalJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class SimpleEntityGovernedResourceAdapter<T>
        implements GovernedResourceAdapter {

    private static final Set<String> READ_ONLY_KEYS = Set.of(
            "createTime", "updateTime", "accessToken");

    private final String resourceType;
    private final Class<T> entityType;
    private final EntityStore<T> store;
    private final Function<T, Long> idGetter;
    private final BiConsumer<T, Long> idSetter;
    private final Function<T, Integer> statusGetter;
    private final BiConsumer<T, Integer> statusSetter;
    private final Set<String> requiredKeys;
    private final Set<String> sensitiveKeys;
    private final GovernanceSecretCodec secretCodec;

    public SimpleEntityGovernedResourceAdapter(
            String resourceType,
            Class<T> entityType,
            EntityStore<T> store,
            Function<T, Long> idGetter,
            BiConsumer<T, Long> idSetter,
            Function<T, Integer> statusGetter,
            BiConsumer<T, Integer> statusSetter,
            Set<String> requiredKeys,
            Set<String> sensitiveKeys,
            GovernanceSecretCodec secretCodec) {
        this.resourceType = resourceType;
        this.entityType = entityType;
        this.store = store;
        this.idGetter = idGetter;
        this.idSetter = idSetter;
        this.statusGetter = statusGetter;
        this.statusSetter = statusSetter;
        this.requiredKeys = requiredKeys == null
                ? Set.of() : Set.copyOf(requiredKeys);
        this.sensitiveKeys = sensitiveKeys == null
                ? Set.of() : Set.copyOf(sensitiveKeys);
        this.secretCodec = secretCodec;
    }

    @Override
    public String resourceType() {
        return resourceType;
    }

    @Override
    public ResourceSnapshot loadEffective(Long resourceId) {
        T entity = store.load(resourceId);
        if (entity == null) {
            throw new IllegalArgumentException(
                    resourceType + " 资源不存在: " + resourceId);
        }
        return normalizeDraft(new ResourceSnapshot(
                JSON.toJSONString(entity),
                statusGetter != null
                        && Integer.valueOf(0).equals(
                        statusGetter.apply(entity))
                        ? "DISABLED" : "ACTIVE",
                null, null));
    }

    @Override
    public ResourceSnapshot normalizeDraft(ResourceSnapshot draft) {
        Map<String, Object> value = new LinkedHashMap<>(
                CanonicalJson.readMap(draft.snapshotJson()));
        READ_ONLY_KEYS.forEach(value::remove);
        return secretCodec.normalize(new ResourceSnapshot(
                        CanonicalJson.write(value),
                        draft.effectiveStatus(),
                        draft.secretPayloadCiphertext(),
                        draft.secretDigest()),
                sensitiveKeys);
    }

    @Override
    public List<GovernanceIssue> validate(ResourceSnapshot draft) {
        Map<String, Object> value =
                CanonicalJson.readMap(draft.snapshotJson());
        List<GovernanceIssue> issues = new ArrayList<>();
        for (String requiredKey : requiredKeys) {
            Object required = value.get(requiredKey);
            if (required == null || String.valueOf(required).isBlank()) {
                issues.add(GovernanceIssue.error(
                        "RESOURCE_FIELD_REQUIRED",
                        requiredKey + " 不能为空",
                        resourceType, longValue(value.get("id")),
                        "$." + requiredKey));
            }
        }
        return issues;
    }

    @Override
    public List<ResourceDependencyRef> collectDependencies(
            ResourceSnapshot draft) {
        List<ResourceDependencyRef> dependencies = new ArrayList<>();
        collect(CanonicalJson.readMap(draft.snapshotJson()),
                "$", dependencies);
        return dependencies.stream().distinct().toList();
    }

    @Override
    public ResourceDiff diff(ResourceSnapshot left,
                             ResourceSnapshot right) {
        return JsonResourceDiff.compare(left, right);
    }

    @Override
    public AppliedResource apply(ApprovalApplyContext context) {
        Map<String, Object> restored = secretCodec.restore(
                context.snapshot());
        T entity = JSON.parseObject(
                CanonicalJson.write(restored), entityType);
        String action = context.action() == null
                ? "UPDATE"
                : context.action().toUpperCase(Locale.ROOT);
        String effectiveStatus = switch (action) {
            case "DELETE" -> "DELETED";
            case "DISABLE" -> "DISABLED";
            case "CREATE", "ENABLE", "RESTORE" -> "ACTIVE";
            default -> context.snapshot().effectiveStatus() == null
                    ? "ACTIVE" : context.snapshot().effectiveStatus();
        };
        if (statusSetter != null) {
            statusSetter.accept(entity,
                    "ACTIVE".equals(effectiveStatus) ? 1
                            : "DELETED".equals(effectiveStatus) ? -1 : 0);
        }
        if ("CREATE".equals(action)) {
            idSetter.accept(entity, null);
            store.insert(entity);
        } else {
            if (context.resourceId() == null) {
                throw new IllegalArgumentException(
                        "非创建操作必须指定资源 ID");
            }
            idSetter.accept(entity, context.resourceId());
            store.update(entity);
        }
        Long resourceId = idGetter.apply(entity);
        if (resourceId == null) {
            throw new IllegalStateException(
                    "资源写入后没有返回 ID");
        }
        return new AppliedResource(resourceId,
                context.nextVersionNo(), effectiveStatus, null);
    }

    private void collect(Object value,
                         String path,
                         List<ResourceDependencyRef> dependencies) {
        if (value instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rawMap;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Long id = longValue(entry.getValue());
                String targetType = dependencyType(
                        entry.getKey(), map);
                if (targetType != null && id != null && id > 0) {
                    dependencies.add(new ResourceDependencyRef(
                            targetType, id, targetType,
                            path + "." + entry.getKey(),
                            "REFERENCES", true));
                }
                if (targetType != null
                        && entry.getValue() instanceof List<?> ids) {
                    for (int index = 0; index < ids.size(); index++) {
                        Long itemId = longValue(ids.get(index));
                        if (itemId != null && itemId > 0) {
                            dependencies.add(new ResourceDependencyRef(
                                    targetType, itemId, targetType,
                                    path + "." + entry.getKey()
                                            + "[" + index + "]",
                                    "REFERENCES", true));
                        }
                    }
                }
                collect(entry.getValue(),
                        path + "." + entry.getKey(), dependencies);
            }
        } else if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                collect(list.get(index), path + "[" + index + "]",
                        dependencies);
            }
        } else if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                try {
                    Object nested = JSON.parse(trimmed);
                    if (nested instanceof Map<?, ?>
                            || nested instanceof List<?>) {
                        collect(nested, path + "[json]",
                                dependencies);
                    }
                } catch (RuntimeException ignored) {
                    // User-authored scripts and templates may resemble JSON.
                }
            }
        }
    }

    private String dependencyType(String key,
                                  Map<String, Object> owner) {
        return switch (key) {
            case "projectId" -> "PROJECT";
            case "modelId", "refModelId" -> "MODEL";
            case "variableId", "refVariableId", "leftVarId", "rightVarId" ->
                    "VARIABLE";
            case "varId", "refId" -> refType(owner.get("refType"));
            case "functionId" -> "FUNCTION";
            case "definitionId", "ruleId" -> "RULE";
            case "experimentId" -> "EXPERIMENT";
            case "datasourceId" -> owner.containsKey("sql")
                    ? "DATABASE" : "EXTERNAL_DATASOURCE";
            case "dbDatasourceId" -> "DATABASE";
            case "apiConfigId", "apiId" -> "EXTERNAL_API";
            case "listId", "listIds" -> "LIST_LIBRARY";
            case "objectId", "parentObjectId", "refObjectId",
                    "requestObjectId", "responseObjectId" ->
                    "DATA_OBJECT";
            default -> null;
        };
    }

    private String refType(Object value) {
        if (value == null) {
            return "VARIABLE";
        }
        String type = String.valueOf(value)
                .toUpperCase(Locale.ROOT);
        return switch (type) {
            case "MODEL" -> "MODEL";
            case "FUNCTION" -> "FUNCTION";
            case "DATA_OBJECT", "OBJECT" -> "DATA_OBJECT";
            default -> "VARIABLE";
        };
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public interface EntityStore<T> {
        T load(Long id);

        void insert(T entity);

        void update(T entity);
    }
}
