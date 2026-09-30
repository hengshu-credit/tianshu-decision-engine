package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 将 API 或数据库来源装载到数据对象，显式传入的对象字段优先。 */
@Service
public class DataObjectSourceResolver {
    private final ThreadLocal<Set<Long>> resolving = ThreadLocal.withInitial(LinkedHashSet::new);

    @Resource
    private RuleDataObjectFieldMapper fieldMapper;
    @Resource
    private RuleDataObjectMapper objectMapper;
    @Resource
    private ExternalApiInvokeService externalApiInvokeService;
    @Resource private ExternalApiConsumerService externalApiConsumerService;
    @Resource private VariableSourceResolver variableSourceResolver;
    @Resource
    private DBConnectPools dbConnectPools;

    public void resolve(Long projectId, List<RuleDefinitionInputField> directFields,
                        Map<String, Object> values, VariableResolveOptions options) {
        resolve(projectId, directFields, values, options, null);
    }

    /** 制品执行使用字段快照；普通执行使用当前主表对象。 */
    public void resolve(Long projectId, List<RuleDefinitionInputField> directFields,
                        Map<String, Object> values, VariableResolveOptions options,
                        List<RuleDataObjectField> snapshotFields) {
        if (values == null || directFields == null || directFields.isEmpty()) return;
        VariableResolveOptions effective = options == null ? VariableResolveOptions.defaults() : options;
        effective.setExecutionProjectId(projectId);
        if (effective.isOfflineReplay()) return;
        if (effective.getInvocationCache() == null) {
            effective.setInvocationCache(new VariableResolutionInvocationCache());
        }
        Map<String, ObjectBinding> bindings = new LinkedHashMap<>();
        for (RuleDefinitionInputField direct : directFields) {
            if (!isDataObject(direct) || direct.getVarId() == null) continue;
            Set<String> required = effective.getRequiredScriptNames();
            String target = pathOf(direct);
            if (required != null && required.stream().noneMatch(path -> target != null && path != null
                    && (target.equals(path) || target.startsWith(path + ".") || path.startsWith(target + ".")))) continue;
            RuleDataObjectField field = findField(direct.getVarId(), snapshotFields);
            if (field == null) continue;
            String key = field.getObjectId() + ":" + (field.getSourceConfig() == null ? "" : field.getSourceConfig());
            ObjectBinding binding = bindings.computeIfAbsent(key, ignored -> new ObjectBinding());
            if (binding.owner == null) {
                binding.owner = owner(field);
                if (binding.owner != null && field.getSourceConfig() != null && !field.getSourceConfig().isBlank()) {
                    Map<String, Object> config = new LinkedHashMap<>(binding.owner.config);
                    Map<String, Object> child = parseJson(field.getSourceConfig());
                    Map<String, Object> overrides = new LinkedHashMap<>();
                    if (config.get("requestOverrides") instanceof Map<?, ?> rootOverrides) rootOverrides.forEach((id, value) -> overrides.put(String.valueOf(id), value));
                    if (child.get("requestOverrides") instanceof Map<?, ?> childOverrides) childOverrides.forEach((id, value) -> overrides.put(String.valueOf(id), value));
                    config.putAll(child);
                    config.put("requestOverrides", overrides);
                    config.put("bindingMode", "FIELDS");
                    binding.owner = new SourceOwner(binding.owner.id, binding.owner.sourceType, binding.owner.sourceContent,
                            binding.owner.scriptName, binding.owner.objectCode, binding.owner.objectLabel, config);
                }
            }
            binding.fields.add(new FieldBinding(field, pathOf(direct)));
        }
        for (ObjectBinding binding : bindings.values()) {
            if (binding.owner == null || !supportsSource(binding.owner.sourceType)) continue;
            if (effective.isSkipApiSources() && "API".equalsIgnoreCase(binding.owner.sourceType)) continue;
            if (binding.fields.stream().noneMatch(item -> !present(values, item.targetPath))) continue;
            Set<Long> active = resolving.get();
            if (!active.add(binding.owner.id)) throw new IllegalArgumentException("数据对象外数依赖存在循环，对象 ID=" + binding.owner.id);
            try {
            if (variableSourceResolver != null && "API".equalsIgnoreCase(binding.owner.sourceType)) {
                variableSourceResolver.resolveApiDependencies(projectId, binding.owner.config, values, effective);
            }
            Map<String, Object> response = load(projectId, binding.owner, values, effective);
            binding.fields.forEach(item -> ExternalApiConsumerService.recordStatus(response, effective, "DATA_OBJECT", item.field.getId()));
            Object payload = payload(binding.owner, response);
            String mode = upper(binding.owner.config.get("bindingMode"));
            if (payload == null && !"FIELDS".equals(mode)) continue;
            Set<String> provided = new LinkedHashSet<>();
            binding.fields.forEach(item -> { if (present(values, item.targetPath)) provided.add(item.targetPath); });
            if (!"FIELDS".equals(mode)) {
                String root = firstText(binding.owner.scriptName, binding.owner.objectCode);
                if (root != null) mergePath(values, root, payload);
            }
                for (FieldBinding item : binding.fields) {
                    if (item.targetPath == null || provided.contains(item.targetPath)) continue;
                    if (!"FIELDS".equals(mode) && (item.field.getSourcePath() == null || item.field.getSourcePath().isBlank())) continue;
                    String sourcePath = firstText(item.field.getSourcePath(), item.field.getVarCode());
                    boolean absolute = item.field.getSourcePath() != null && sourcePath != null
                            && (sourcePath.startsWith("$") || sourcePath.matches("(?:body|response|request|authentication|steps|status)(?:[.\\[].*)?"));
                    Object origin = absolute ? response : payload;
                    Object value = readPath(origin, sourcePath);
                    if (value != null || containsPath(origin, sourcePath)) {
                        setPath(values, item.targetPath, value);
                    }
                }
            } catch (RuntimeException error) {
                boolean waiting = error instanceof ExternalApiWaitingException
                        || error instanceof com.hengshucredit.rule.core.engine.RuleSuspensionSignal
                        || (error instanceof ExternalApiInvokeService.ApiInvokeException api
                        && "WAIT".equalsIgnoreCase(api.getExceptionStrategy()));
                binding.fields.forEach(item -> {
                    effective.recordSourceState("DATA_OBJECT", item.field.getId(), "OUTCOME",
                            waiting ? "WAITING_EXTERNAL" : "ERROR");
                    effective.recordSourceState("DATA_OBJECT", item.field.getId(), "EXCEPTION", "TRUE");
                });
                if (waiting) {
                    if (error instanceof com.hengshucredit.rule.core.engine.RuleSuspensionSignal) throw error;
                    throw new ExternalApiWaitingException("数据对象外数等待恢复", error);
                }
                if (binding.fields.stream().noneMatch(item -> effective.requiresSourceStatus("DATA_OBJECT", item.field.getId()))) throw error;
            } finally { active.remove(binding.owner.id); if (active.isEmpty()) resolving.remove(); }
        }
    }

    void resolveReferencedPaths(Long projectId, Set<String> requiredPaths, Map<String, Object> values, VariableResolveOptions options) {
        List<RuleDefinitionInputField> references = new ArrayList<>();
        options.getDerivedReferencePaths().forEach((key, path) -> {
            if (!key.startsWith("DATA_OBJECT:") || present(values, path)) return;
            if (requiredPaths.stream().noneMatch(required -> required.equals(path) || required.startsWith(path + ".") || path.startsWith(required + "."))) return;
            RuleDefinitionInputField direct = new RuleDefinitionInputField();
            direct.setVarId(Long.valueOf(key.substring("DATA_OBJECT:".length()))); direct.setRefType("DATA_OBJECT"); direct.setScriptName(path);
            references.add(direct);
        });
        resolve(projectId, references, values, options, options.getRuntimeSnapshot() == null ? null : options.getRuntimeSnapshot().getDataObjectFields());
    }

    private RuleDataObjectField findField(Long id, List<RuleDataObjectField> snapshotFields) {
        if (snapshotFields != null) {
            for (RuleDataObjectField field : snapshotFields) {
                if (field != null && id.equals(field.getId())) return field;
            }
            return null;
        }
        return fieldMapper == null ? null : fieldMapper.selectById(id);
    }

    private SourceOwner owner(RuleDataObjectField field) {
        if (field.getObjectSourceType() != null || field.getObjectSourceContent() != null) {
            return new SourceOwner(field.getObjectId(), field.getObjectSourceType(),
                    field.getObjectSourceContent(), field.getObjectScriptName(), field.getObjectCode(), null,
                    supportsSource(field.getObjectSourceType()) ? parseJson(field.getObjectSourceContent()) : Map.of());
        }
        RuleDataObject object = field.getObjectId() == null || objectMapper == null
                ? null : objectMapper.selectById(field.getObjectId());
        if (object == null) return null;
        return new SourceOwner(object.getId(), object.getSourceType(), object.getSourceContent(),
                firstText(object.getScriptName(), object.getObjectCode()), object.getObjectCode(),
                object.getObjectLabel(), supportsSource(object.getSourceType()) ? parseJson(object.getSourceContent()) : Map.of());
    }

    private Map<String, Object> load(Long projectId, SourceOwner owner, Map<String, Object> values,
                                     VariableResolveOptions options) {
        if ("API".equalsIgnoreCase(owner.sourceType) && externalApiConsumerService != null) {
            Long apiId = longValue(owner.config.get("apiConfigId"));
            if (apiId == null) throw new IllegalArgumentException("数据对象 API 来源缺少 apiConfigId");
            Map<String, Object> mapped = mappedParams(owner.config.get("paramMapping"), values);
            return externalApiConsumerService.resolve(projectId, apiId, owner.config, mapped, options);
        }
        Long datasourceId = longValue(firstNonNull(owner.config.get("dbDatasourceId"), owner.config.get("datasourceId")));
        String sql = text(owner.config.get("sql"));
        List<Object> params = "DB".equalsIgnoreCase(owner.sourceType)
                || "DATABASE".equalsIgnoreCase(owner.sourceType)
                ? queryParams(owner.config.get("params"), values) : List.of();
        DatabaseQueryOptions queryOptions = DatabaseQueryOptions.from(owner.config, 1);
        Map<String, Object> cacheIdentity = new LinkedHashMap<>();
        cacheIdentity.put("projectId", projectId);
        cacheIdentity.put("ownerId", owner.id);
        cacheIdentity.put("sourceType", owner.sourceType);
        cacheIdentity.put("sourceConfig", owner.config);
        cacheIdentity.put("datasourceId", datasourceId);
        cacheIdentity.put("sql", sql);
        cacheIdentity.put("params", params);
        cacheIdentity.put("maxRows", queryOptions.maxRows());
        cacheIdentity.put("queryTimeoutSeconds", queryOptions.queryTimeoutSeconds());
        String key = "DATA_OBJECT_SOURCE:" + owner.id + ":"
                + Sha256Digests.text(CanonicalJson.write(cacheIdentity));
        return options.getInvocationCache().resolve(key, () -> {
            if ("API".equalsIgnoreCase(owner.sourceType)) {
                Long apiId = longValue(owner.config.get("apiConfigId"));
                if (apiId == null) throw new IllegalArgumentException("数据对象 API 来源缺少 apiConfigId");
                return externalApiInvokeService.invoke(apiId, mappedParams(owner.config.get("paramMapping"), values));
            }
            if (datasourceId == null) throw new IllegalArgumentException("数据对象数据库来源缺少 datasourceId");
            if (sql == null) throw new IllegalArgumentException("数据对象数据库来源缺少查询 SQL");
            List<Map<String, Object>> rows;
            try {
                rows = dbConnectPools.query(datasourceId, sql, params,
                        1, queryOptions.queryTimeoutSeconds());
            } catch (Exception e) {
                throw new IllegalStateException("数据对象数据库查询失败：" + e.getMessage(), e);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("rows", rows == null ? Collections.emptyList() : rows);
            if (rows != null && !rows.isEmpty()) result.putAll(rows.get(0));
            return result;
        });
    }

    private Object payload(SourceOwner owner, Map<String, Object> response) {
        if (response == null) return null;
        if ("DB".equalsIgnoreCase(owner.sourceType) || "DATABASE".equalsIgnoreCase(owner.sourceType)) {
            Object rows = response.get("rows");
            if (rows instanceof List<?> list) return list.isEmpty() ? null : list.get(0);
            return response;
        }
        String path = firstText(text(owner.config.get("resultPath")), "body");
        return ExternalApiConsumerService.select(response, path);
    }

    private Map<String, Object> mappedParams(Object rawMapping, Map<String, Object> values) {
        if (!(rawMapping instanceof Map<?, ?> mapping) || mapping.isEmpty()) return new LinkedHashMap<>(values);
        Map<String, Object> result = new LinkedHashMap<>();
        mapping.forEach((key, raw) -> {
            if (key == null) return;
            String path = text(raw);
            Object value = path == null ? raw : readPath(values, stripTemplate(path));
            if (value == null && path != null && !containsPath(values, stripTemplate(path))) value = raw;
            result.put(String.valueOf(key), value);
        });
        return result;
    }

    private List<Object> queryParams(Object rawParams, Map<String, Object> values) {
        if (!(rawParams instanceof List<?> list)) return new ArrayList<>();
        List<Object> result = new ArrayList<>();
        for (Object raw : list) {
            if (raw instanceof Map<?, ?> map) {
                String kind = upper(map.get("kind"));
                if ("REFERENCE".equals(kind)) {
                    String path = firstText(text(map.get("path")), text(map.get("code")), text(map.get("value")));
                    result.add(readPath(values, path));
                } else if (map.containsKey("value")) {
                    result.add(map.get("value"));
                } else {
                    result.add(raw);
                }
            } else if (raw instanceof String text && text.startsWith("${") && text.endsWith("}")) {
                result.add(readPath(values, text.substring(2, text.length() - 1).trim()));
            } else {
                result.add(raw);
            }
        }
        return result;
    }

    private boolean supportsSource(String sourceType) {
        return "API".equalsIgnoreCase(sourceType) || "DB".equalsIgnoreCase(sourceType)
                || "DATABASE".equalsIgnoreCase(sourceType);
    }

    private boolean isDataObject(RuleDefinitionInputField field) {
        return field != null && "DATA_OBJECT".equalsIgnoreCase(text(field.getRefType()));
    }

    private String pathOf(RuleDefinitionInputField field) {
        return firstText(field == null ? null : field.getScriptName(), field == null ? null : field.getFieldName());
    }

    private Map<String, Object> parseJson(String content) {
        if (content == null || content.trim().isEmpty()) return new LinkedHashMap<>();
        try { return JSON.parseObject(content); }
        catch (RuntimeException e) { throw new IllegalArgumentException("数据对象来源配置不是合法 JSON", e); }
    }

    private Object firstNonNull(Object first, Object second) { return first == null ? second : first; }
    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null || text(value) == null) return null;
        try { return Long.valueOf(text(value)); } catch (NumberFormatException e) { return null; }
    }
    private String upper(Object value) { return text(value) == null ? "" : text(value).toUpperCase(Locale.ROOT); }
    private String text(Object value) { return value == null ? null : text(String.valueOf(value)); }
    private String text(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private String firstText(String... values) { for (String value : values) if (text(value) != null) return text(value); return null; }
    private String stripTemplate(String value) {
        String result = value.trim();
        if (result.startsWith("${") && result.endsWith("}")) return result.substring(2, result.length() - 1).trim();
        if (result.startsWith("$.")) return result.substring(2);
        return result;
    }

    private boolean present(Map<String, Object> values, String path) {
        return com.hengshucredit.rule.server.derived.HistoryFieldValues.present(values, path);
    }
    private boolean containsPath(Object root, String path) {
        return ExternalApiRequestPlan.present(root, path);
    }
    private Object readPath(Object root, String path) {
        return ExternalApiRequestPlan.read(root, path);
    }
    private Integer index(String value) { try { return Integer.valueOf(value); } catch (Exception e) { return null; } }
    @SuppressWarnings("unchecked")
    private void mergePath(Map<String, Object> values, String path, Object payload) {
        if (!containsPath(values, path)) { setPath(values, path, copy(payload)); return; }
        Object current = readPath(values, path);
        if (current instanceof Map<?, ?> currentMap && payload instanceof Map<?, ?> payloadMap) {
            setPath(values, path, ExternalApiConsumerService.merge(currentMap, payloadMap));
        }
    }
    @SuppressWarnings("unchecked")
    private void mergeMap(Map<String, Object> target, Map<?, ?> source) {
        source.forEach((key, value) -> {
            String name = String.valueOf(key);
            Object existing = target.get(name);
            if (existing instanceof Map<?, ?> && value instanceof Map<?, ?>) mergeMap((Map<String, Object>) existing, (Map<?, ?>) value);
            else if (!target.containsKey(name)) target.put(name, copy(value));
        });
    }
    @SuppressWarnings("unchecked")
    private void setPath(Map<String, Object> values, String path, Object value) {
        String[] parts = stripTemplate(path).split("\\.");
        Map<String, Object> current = values;
        for (int i = 0; i < parts.length; i++) {
            if (i == parts.length - 1) { current.put(parts[i], copy(value)); return; }
            Object child = current.get(parts[i]);
            if (!(child instanceof Map<?, ?>)) { child = new LinkedHashMap<String, Object>(); current.put(parts[i], child); }
            current = (Map<String, Object>) child;
        }
    }
    @SuppressWarnings("unchecked")
    private Object copy(Object value) {
        if (value instanceof Map<?, ?> map) { Map<String, Object> result = new LinkedHashMap<>(); map.forEach((k, v) -> result.put(String.valueOf(k), copy(v))); return result; }
        if (value instanceof List<?> list) { List<Object> result = new ArrayList<>(); list.forEach(item -> result.add(copy(item))); return result; }
        return value;
    }

    private static final class ObjectBinding {
        private SourceOwner owner;
        private final List<FieldBinding> fields = new ArrayList<>();
    }
    private record FieldBinding(RuleDataObjectField field, String targetPath) { }
    private record SourceOwner(Long id, String sourceType, String sourceContent, String scriptName,
                               String objectCode, String objectLabel, Map<String, Object> config) { }
}
