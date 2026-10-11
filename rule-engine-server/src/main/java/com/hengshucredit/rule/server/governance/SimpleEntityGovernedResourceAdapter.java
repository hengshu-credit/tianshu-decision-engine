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
    private static final Set<String> BUSINESS_VALUE_KEYS = Set.of(
            "testParams", "testSampleParams", "sampleParams", "defaultValue", "optionValue");

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
    public boolean supportsDraftWorkspace() { return true; }

    @Override
    public ResourceSnapshot loadEffective(Long resourceId) {
        T entity = store.load(resourceId);
        if (entity == null) {
            throw new IllegalArgumentException(
                    resourceType + " 资源不存在: " + resourceId);
        }
        return normalizeDraft(new ResourceSnapshot(
                JSON.toJSONString(entity),
                statusGetter != null && Integer.valueOf(-1).equals(statusGetter.apply(entity)) ? "DELETED" : statusGetter != null
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
        return dependenciesIn(draft.snapshotJson());
    }

    static List<ResourceDependencyRef> dependenciesIn(String snapshotJson) {
        List<ResourceDependencyRef> dependencies = new ArrayList<>();
        collect(CanonicalJson.readMap(snapshotJson),
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
            case "DRAFT_CREATE", "DRAFT_UPDATE" -> "DRAFT";
            case "DELETE", "DRAFT_DELETE" -> "DELETED";
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
        if (context.insertRoot()) {
            idSetter.accept(entity, null);
            store.insert(entity);
        } else {
            if (context.resourceId() == null) {
                throw new IllegalArgumentException(
                        "非创建操作必须指定资源 ID");
            }
            idSetter.accept(entity, context.resourceId());
            if (context.projectionOnly()) store.restore(entity); else store.update(entity);
        }
        Long resourceId = idGetter.apply(entity);
        if (resourceId == null) {
            throw new IllegalStateException(
                    "资源写入后没有返回 ID");
        }
        return new AppliedResource(resourceId,
                context.nextVersionNo(), effectiveStatus, null);
    }

    private static void collect(Object value,
                         String path,
                         List<ResourceDependencyRef> dependencies) {
        if (value instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rawMap;
            if ("LITERAL".equals(map.get("kind"))) return;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (BUSINESS_VALUE_KEYS.contains(entry.getKey())) continue;
                Long id = longValue(entry.getValue());
                String targetType = dependencyType(
                        entry.getKey(), map);
                if (targetType != null && id != null && id > 0) {
                    DependencyVersionSelection selection = DependencyVersionSelection.forReference(entry.getKey(), map, id);
                    if (selection != null) targetType = "CONSTANT".equals(selection.refType()) ? "VARIABLE" : selection.refType();
                    dependencies.add(new ResourceDependencyRef(
                            targetType, id, targetType,
                            path + "." + entry.getKey(),
                            "REFERENCES", true, selection));
                }
                if ((Set.of("script", "requestScript", "responseScript", "conditionExpression", "expression").contains(entry.getKey())
                        || "implScript".equals(entry.getKey()) && "SCRIPT".equals(map.get("implType")))
                        && entry.getValue() instanceof String script && !script.isBlank()) {
                    var scriptTree = new com.alibaba.fastjson.JSONObject(); scriptTree.put("script", script);
                    for (var reference : com.hengshucredit.rule.server.service.OperandDependencyCollector.collectReferences(scriptTree)) {
                        DependencyVersionSelection selection = "FIXED".equals(reference.getVersionMode())
                                ? new DependencyVersionSelection(reference.getRefType(), reference.getRefId(),
                                "RULE".equals(reference.getRefType()) ? "RULE_BINDING" : java.util.Objects.toString(reference.getVersionSource(), "SNAPSHOT"),
                                "RULE".equals(reference.getRefType()) ? reference.getVersionBindingId() : reference.getVersionId()) : null;
                        String type = "CONSTANT".equals(reference.getRefType()) ? "VARIABLE" : reference.getRefType();
                        dependencies.add(new ResourceDependencyRef(type, reference.getRefId(), type,
                                path + "." + entry.getKey() + reference.getPath().substring("$.script".length()), "REFERENCES", true, selection));
                    }
                }
                Object referenceIds = entry.getValue();
                if (targetType != null && referenceIds instanceof String text && text.stripLeading().startsWith("[")) {
                    referenceIds = JSON.parseArray(text);
                }
                if (targetType != null && referenceIds instanceof List<?> ids) {
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
                Object nested;
                try {
                    nested = JSON.parse(trimmed);
                } catch (com.alibaba.fastjson.JSONException ignored) {
                    // User-authored scripts and templates may resemble JSON.
                    return;
                }
                if (nested instanceof Map<?, ?> || nested instanceof List<?>) collect(nested, path + "[json]", dependencies);
            }
        }
    }

    private static String dependencyType(String key,
                                  Map<String, Object> owner) {
        return switch (key) {
            case "projectId" -> "PROJECT";
            case "modelId", "refModelId" -> "MODEL";
            case "variableId", "refVariableId" -> "VARIABLE";
            case "_varId" -> refType(owner.get("_refType") == null
                    ? owner.get("refType") : owner.get("_refType"));
            case "leftVarId" -> refType(owner.get("leftRefType"));
            case "rightVarId" -> refType(owner.get("rightRefType"));
            case "_targetVarId" -> refType(owner.get("_targetRefType"));
            case "_condVarId" -> refType(owner.get("_condVarRefType"));
            case "_matchVarId" -> refType(owner.get("_matchVarRefType"));
            case "_checkVarId" -> refType(owner.get("_checkVarRefType"));
            case "_rightVarId" -> refType(owner.get("_rightRefType"));
            case "varId", "refId" -> refType(owner.get("refType"));
            case "functionId" -> "FUNCTION";
            case "definitionId", "ruleId" -> "RULE";
            case "experimentId" -> "EXPERIMENT";
            case "datasourceId" -> owner.containsKey("sql")
                    ? "DATABASE" : "EXTERNAL_DATASOURCE";
            case "dbDatasourceId" -> "DATABASE";
            case "apiConfigId", "apiId" -> "EXTERNAL_API";
            case "listId", "listIds", "listLibraryId" -> "LIST_LIBRARY";
            case "validationRuleId", "validationRuleIds" -> "FIELD_VALIDATION";
            case "objectId", "parentObjectId", "refObjectId",
                    "requestObjectId", "responseObjectId" ->
                    "DATA_OBJECT";
            default -> null;
        };
    }

    private static String refType(Object value) {
        if (value == null) {
            return "VARIABLE";
        }
        String type = String.valueOf(value)
                .toUpperCase(Locale.ROOT);
        return switch (type) {
            case "MODEL", "MODEL_OUTPUT", "RULE", "EXPERIMENT", "EXTERNAL_API", "EXTERNAL_DATASOURCE",
                 "DATABASE", "LIST_LIBRARY", "FIELD_VALIDATION" -> type;
            case "FUNCTION" -> "FUNCTION";
            case "DATA_OBJECT", "OBJECT" -> "DATA_OBJECT";
            default -> "VARIABLE";
        };
    }

    private static Long longValue(Object value) {
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
        default void restore(T entity) { update(entity); }
    }
}
