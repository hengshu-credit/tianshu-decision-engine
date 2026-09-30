package com.hengshucredit.rule.server.transfer;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 比较可迁移配置。仅去掉已知实体的归属/审计字段，业务 JSON 和外部引用 ID 必须保留。 */
final class TransferResourceComparison {
    private static final Set<String> METADATA = Set.of("id", "projectId", "scope", "projectCode", "projectName",
            "createTime", "updateTime", "createBy", "updateBy");
    private static final Set<String> JSON_FIELDS = Set.of("sourceConfig", "paramsJson", "modelConfig", "modelJson",
            "headerConfig", "queryConfig", "requestMapping", "responseMapping", "bodyTemplate", "authConfig",
            "authApiConfig", "asyncPollConfig", "asyncCallbackConfig", "conditionConfig", "executionConfig",
            "payloadCaptureConfig", "openApiConfigJson", "inputSchemaJson", "outputSchemaJson");

    private TransferResourceComparison() { }

    static boolean sameVariableType(Map<String, Object> source, Map<String, Object> target) {
        Object type = source.get("varType");
        return type instanceof String text && !text.isBlank() && type.equals(target.get("varType"))
                && Objects.equals("CONSTANT".equals(source.get("varSource")), "CONSTANT".equals(target.get("varSource")));
    }

    static String conflict(TransferResourceType type, Map<String, Object> source, Map<String, Object> target,
                           String effectiveStatus) {
        if (!"ACTIVE".equals(effectiveStatus)) return "INACTIVE";
        if (type == TransferResourceType.VARIABLE && !sameVariableType(source, target)) return "TYPE_CONFLICT";
        // 凭据不进入迁移包，不能根据“已配置”标记断言两份凭据内容相同。
        if (configuredSecrets(source) || configuredSecrets(target)) return "ENVIRONMENT_REVIEW_REQUIRED";
        if (type == TransferResourceType.MODEL && (source.get("modelContent") == null || target.get("modelContent") == null)) return "CONFIG_CONFLICT";
        return CanonicalJson.write(normalize(type, source)).equals(CanonicalJson.write(normalize(type, target)))
                ? "IDENTICAL" : "CONFIG_CONFLICT";
    }

    static boolean reusable(TransferResourceType type, Map<String, Object> source, Map<String, Object> target,
                            String effectiveStatus) {
        return "ACTIVE".equals(effectiveStatus) && (type == TransferResourceType.VARIABLE
                ? sameVariableType(source, target) : "IDENTICAL".equals(conflict(type, source, target, effectiveStatus)));
    }

    private static boolean configuredSecrets(Map<String, Object> config) {
        return config.get("_secretConfigured") instanceof Map<?, ?> secrets && secrets.values().stream().anyMatch(Boolean.TRUE::equals);
    }

    private static Map<String, Object> normalize(TransferResourceType type, Map<String, Object> input) {
        Map<String, Object> config = CanonicalJson.readMap(CanonicalJson.write(input));
        entity(config);
        switch (type) {
            case VARIABLE -> children(config.get("options"), "variableId");
            case DATA_OBJECT -> {
                TransferObjectFieldIndex index = TransferObjectFieldIndex.of(config);
                if (config.get("fields") instanceof List<?>) {
                    List<Map<String, Object>> fields = index.sortedFields();
                    for (Map<String, Object> field : fields) {
                        Object parent = field.get("parentFieldId");
                        if (parent != null) field.put("parentFieldId", index.path(Long.valueOf(String.valueOf(parent))));
                        children(field.get("options"), "fieldId");
                    }
                    children(fields, "objectId");
                    config.put("fields", fields);
                }
            }
            case MODEL -> {
                config.remove("currentVersion"); config.remove("publishedVersion");
                children(config.get("inputFields"), "modelId");
                children(config.get("outputFields"), "modelId");
            }
            case EXPERIMENT -> { config.remove("currentVersion"); children(config.get("groups"), "experimentId"); }
            case RULE -> {
                config.remove("currentVersion"); config.remove("publishedVersion");
                Map<String, Object> content = object(config.get("content"));
                entity(content); content.remove("definitionId"); content.remove("compileTime"); content.remove("compileMessage");
                children(config.get("inputFieldsJson"), "definitionId");
                children(config.get("outputFieldsJson"), "definitionId");
            }
            default -> { }
        }
        return config;
    }

    private static void entity(Map<String, Object> value) {
        METADATA.forEach(value::remove);
        for (String key : JSON_FIELDS) {
            if (value.get(key) instanceof String text) {
                try {
                    Object parsed = JSON.parse(text);
                    if (parsed instanceof Map || parsed instanceof List) value.put(key, parsed);
                } catch (RuntimeException ignored) { /* 非 JSON 模板保留原文参与比较。 */ }
            }
        }
    }

    private static void children(Object value, String ownerKey) {
        if (!(value instanceof List<?> items)) return;
        for (Object child : items) { Map<String, Object> map = object(child); entity(map); map.remove(ownerKey); }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : new HashMap<>();
    }
}
