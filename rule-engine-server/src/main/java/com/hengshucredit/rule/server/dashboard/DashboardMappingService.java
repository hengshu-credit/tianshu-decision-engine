package com.hengshucredit.rule.server.dashboard;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.compiler.ConstantValueCodec;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.service.ConsoleUserPreferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hengshucredit.rule.server.dashboard.DashboardMapping.JsonSource.INPUT;
import static com.hengshucredit.rule.server.dashboard.DashboardMapping.MappingSource.PROJECT_OVERRIDE;
import static com.hengshucredit.rule.server.dashboard.DashboardMapping.MappingSource.SYSTEM_DEFAULT;
import static com.hengshucredit.rule.server.dashboard.DashboardMapping.MappingSource.USER_DEFAULT;
import static com.hengshucredit.rule.server.dashboard.DashboardMapping.RefType.DATA_OBJECT_FIELD;
import static com.hengshucredit.rule.server.dashboard.DashboardMapping.RefType.VARIABLE;

@Service
public class DashboardMappingService {

    public static final String PREFERENCE_KEY = "DASHBOARD_MAPPING";

    private static final Logger LOGGER = LoggerFactory.getLogger(
            DashboardMappingService.class);
    private static final DashboardMapping.DecisionValues DEFAULT_DECISIONS =
            new DashboardMapping.DecisionValues(
                    Set.of("0", "100", "PASS"),
                    Set.of("2", "102", "REVIEW"),
                    Set.of("1", "101", "REJECT"));

    private final ConsoleUserPreferenceService preferenceService;
    private final DashboardMappingRepository referenceRepository;

    public DashboardMappingService(
            ConsoleUserPreferenceService preferenceService,
            DashboardMappingRepository referenceRepository) {
        this.preferenceService = preferenceService;
        this.referenceRepository = referenceRepository;
    }

    public DashboardMapping.ResolvedMappings resolve(Long userId,
                                                      Long projectId) {
        StoredPreference preference = load(userId);
        var defaults = parseScope(JSON.parseObject(referenceRepository.findSystemDefaults().mappingsJson()));
        EnumMap<DashboardMetricField, DashboardMapping.ResolvedMapping> result =
                new EnumMap<>(DashboardMetricField.class);
        Map<DashboardMetricField, DashboardMapping.StoredMapping> project =
                projectId == null
                        ? Collections.emptyMap()
                        : preference.projects.getOrDefault(
                        String.valueOf(projectId), Collections.emptyMap());
        for (DashboardMetricField field : DashboardMetricField.values()) {
            DashboardMapping.StoredMapping mapping;
            DashboardMapping.MappingSource source;
            if (project.containsKey(field)) {
                mapping = project.get(field);
                source = PROJECT_OVERRIDE;
            } else if (preference.defaults.containsKey(field)) {
                mapping = preference.defaults.get(field);
                source = USER_DEFAULT;
            } else {
                mapping = defaults.get(field);
                source = SYSTEM_DEFAULT;
            }
            result.put(field, resolveOne(field, mapping, source, projectId,
                    false));
        }
        return new DashboardMapping.ResolvedMappings(result);
    }

    public DashboardMapping.ResolvedMappings saveScope(
            Long userId,
            String username,
            Long projectId,
            Map<DashboardMetricField, DashboardMapping.StoredMapping> mappings) {
        if (mappings == null) {
            throw new IllegalArgumentException("字段映射不能为空");
        }
        EnumMap<DashboardMetricField, DashboardMapping.StoredMapping> validated =
                new EnumMap<>(DashboardMetricField.class);
        for (Map.Entry<DashboardMetricField, DashboardMapping.StoredMapping> entry
                : mappings.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("字段映射包含空项");
            }
            DashboardMapping.ResolvedMapping resolved = resolveOne(
                    entry.getKey(), entry.getValue(), USER_DEFAULT,
                    projectId, projectId == null);
            if (!resolved.valid()) {
                throw new IllegalArgumentException(resolved.errorMessage());
            }
            validated.put(entry.getKey(), normalizedStored(
                    entry.getKey(), entry.getValue()));
        }

        StoredPreference preference = load(userId);
        if (projectId == null) {
            preference.defaults.clear();
            preference.defaults.putAll(validated);
        } else {
            preference.projects.put(String.valueOf(projectId), validated);
        }
        preferenceService.save(userId, PREFERENCE_KEY,
                serialize(preference), username);
        return resolve(userId, projectId);
    }

    public DashboardResponses.Settings settings(Long userId,
                                                 Long projectId,
                                                 boolean editable) {
        StoredPreference preference = load(userId);
        DashboardMapping.ResolvedMappings resolved = resolve(userId, projectId);
        boolean projectOverride = projectId != null
                && preference.projects.containsKey(String.valueOf(projectId));
        return settings(resolved, projectId, editable, projectOverride, null);
    }

    public DashboardResponses.Settings systemSettings() {
        var stored = referenceRepository.findSystemDefaults();
        var defaults = parseScope(JSON.parseObject(stored.mappingsJson()));
        var resolved = new EnumMap<DashboardMetricField, DashboardMapping.ResolvedMapping>(DashboardMetricField.class);
        for (var field : DashboardMetricField.values()) {
            resolved.put(field, resolveOne(field, defaults.get(field), SYSTEM_DEFAULT, null, true));
        }
        return settings(new DashboardMapping.ResolvedMappings(resolved), null, true, false, stored.version());
    }

    @org.springframework.transaction.annotation.Transactional
    public DashboardResponses.Settings saveSystemDefaults(String username, Long version,
            Map<DashboardMetricField, DashboardMapping.StoredMapping> mappings) {
        if (version == null || version < 0 || mappings == null) throw new IllegalArgumentException("公共默认配置或版本不能为空");
        var validated = new EnumMap<DashboardMetricField, DashboardMapping.StoredMapping>(DashboardMetricField.class);
        for (var entry : mappings.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) throw new IllegalArgumentException("字段映射包含空项");
            var resolved = resolveOne(entry.getKey(), entry.getValue(), SYSTEM_DEFAULT, null, true);
            if (!resolved.valid()) throw new IllegalArgumentException(resolved.errorMessage());
            validated.put(entry.getKey(), normalizedStored(entry.getKey(), entry.getValue()));
        }
        referenceRepository.saveSystemDefaults(JSON.toJSONString(serializeScope(validated)), version, username);
        return systemSettings();
    }

    private DashboardResponses.Settings settings(DashboardMapping.ResolvedMappings resolved,
            Long projectId, boolean editable, boolean projectOverride, Long defaultVersion) {
        List<DashboardResponses.SettingField> fields = new ArrayList<>();
        for (DashboardMetricField field : DashboardMetricField.values()) {
            fields.add(new DashboardResponses.SettingField(field,
                    field.displayName(), resolved.get(field)));
        }
        List<DashboardResponses.ReferenceOption> options = new ArrayList<>();
        for (RuleVariable variable
                : referenceRepository.listVariables(projectId)) {
            options.add(new DashboardResponses.ReferenceOption(VARIABLE,
                    variable.getId(), displayLabel(variable.getVarLabel(), variable.getVarCode()), variable.getVarType(),
                    variable.getScope(), variable.getProjectId(), variable.getVarCode(), variable.getVarSource(),
                    "CONSTANT".equals(variable.getVarSource()) ? variable.getDefaultValue() : null));
        }
        for (RuleDataObjectField field
                : referenceRepository.listDataObjectFields(projectId)) {
            RuleDataObject object = referenceRepository.findDataObject(
                    field.getObjectId());
            if (object == null || !Integer.valueOf(1).equals(object.getStatus())) {
                continue;
            }
            String objectLabel = displayLabel(object.getObjectLabel(),
                    object.getObjectCode());
            options.add(new DashboardResponses.ReferenceOption(
                    DATA_OBJECT_FIELD, field.getId(), objectLabel + "."
                    + displayLabel(field.getVarLabel(), field.getVarCode()),
                    field.getVarType(), field.getScope(), field.getProjectId(),
                    String.join(".", objectFieldPath(object, field)), "INPUT", null));
        }
        return new DashboardResponses.Settings(editable, projectId,
                projectOverride, fields, options, defaultVersion);
    }

    public DashboardMapping.ResolvedMappings deleteField(
            Long userId,
            String username,
            Long projectId,
            DashboardMetricField field) {
        if (field == null) throw new IllegalArgumentException("指标字段不能为空");
        StoredPreference preference = load(userId);
        if (projectId == null) {
            preference.defaults.remove(field);
        } else {
            Map<DashboardMetricField, DashboardMapping.StoredMapping> project =
                    preference.projects.get(String.valueOf(projectId));
            if (project != null) {
                project.remove(field);
                if (project.isEmpty()) {
                    preference.projects.remove(String.valueOf(projectId));
                }
            }
        }
        preferenceService.save(userId, PREFERENCE_KEY,
                serialize(preference), username);
        return resolve(userId, projectId);
    }

    private DashboardMapping.ResolvedMapping resolveOne(
            DashboardMetricField field,
            DashboardMapping.StoredMapping mapping,
            DashboardMapping.MappingSource source,
            Long projectId,
            boolean personalDefault) {
        if (mapping == null) {
            return invalid(field, new DashboardMapping.StoredMapping(null, null, field.defaultJsonSource(), null),
                    source, "公共默认尚未配置，请联系管理员或设置个人映射");
        }
        if (mapping.refType() == null
                || mapping.refId() == null || mapping.refId() <= 0L
                || mapping.jsonSource() == null) {
            return invalid(field, mapping, source, "字段引用不完整");
        }
        DashboardMapping.DecisionValues decisions;
        try {
            decisions = resolveDecisions(field, mapping.decisionValues(), projectId, personalDefault);
        } catch (IllegalArgumentException error) {
            return invalid(field, mapping, source, error.getMessage());
        }
        if (mapping.refType() == VARIABLE) {
            RuleVariable variable = referenceRepository.findVariable(
                    mapping.refId());
            if (variable == null || !Integer.valueOf(1).equals(variable.getStatus())) {
                return invalid(field, mapping, source, "字段引用不存在、已停用或无权访问");
            }
            String scopeError = scopeError(variable.getScope(),
                    variable.getProjectId(), projectId, personalDefault);
            if (scopeError != null) return invalid(field, mapping, source, scopeError);
            if (!field.acceptsType(variable.getVarType())) {
                return invalid(field, mapping, source, "字段类型不适用于该指标");
            }
            String error = decisionError(field, decisions);
            if (error != null) return invalid(field, mapping, source, error);
            return valid(field, mapping, source,
                    jsonPath(List.of(variable.getVarCode())),
                    variable.getVarLabel(), decisions);
        }

        if (mapping.refType() == DATA_OBJECT_FIELD) {
            RuleDataObjectField dataField = referenceRepository
                    .findDataObjectField(mapping.refId());
            if (dataField == null
                    || !Integer.valueOf(1).equals(dataField.getStatus())) {
                return invalid(field, mapping, source, "字段引用不存在、已停用或无权访问");
            }
            String scopeError = scopeError(dataField.getScope(),
                    dataField.getProjectId(), projectId, personalDefault);
            if (scopeError != null) return invalid(field, mapping, source, scopeError);
            if (!field.acceptsType(dataField.getVarType())) {
                return invalid(field, mapping, source, "字段类型不适用于该指标");
            }
            RuleDataObject object = referenceRepository.findDataObject(
                    dataField.getObjectId());
            if (object == null || !Integer.valueOf(1).equals(object.getStatus())) {
                return invalid(field, mapping, source, "字段所属数据对象不存在或已停用");
            }
            List<String> path = objectFieldPath(object, dataField);
            String error = decisionError(field, decisions);
            if (error != null) return invalid(field, mapping, source, error);
            return valid(field, mapping, source, jsonPath(path),
                    object.getObjectLabel() + "." + dataField.getVarLabel(), decisions);
        }
        return invalid(field, mapping, source, "不支持的字段引用类型");
    }

    private List<String> objectFieldPath(RuleDataObject object,
                                         RuleDataObjectField dataField) {
        List<String> reversed = new ArrayList<>();
        RuleDataObjectField current = dataField;
        Set<Long> visited = new LinkedHashSet<>();
        while (current != null) {
            if (current.getId() != null && !visited.add(current.getId())) {
                throw new IllegalArgumentException("数据对象字段层级存在循环");
            }
            reversed.add(current.getVarCode());
            current = current.getParentFieldId() == null
                    ? null
                    : referenceRepository.findDataObjectField(
                    current.getParentFieldId());
        }
        Collections.reverse(reversed);
        List<String> path = new ArrayList<>();
        path.add(object.getObjectCode());
        path.addAll(reversed);
        return path;
    }

    private String scopeError(String scope, Long referenceProjectId,
                              Long projectId, boolean personalDefault) {
        boolean global = "GLOBAL".equalsIgnoreCase(scope)
                || Long.valueOf(0L).equals(referenceProjectId);
        if (personalDefault && !global) return "默认配置只能引用全局字段";
        if (!global && (projectId == null
                || !projectId.equals(referenceProjectId))) {
            return "字段引用不属于当前项目";
        }
        return null;
    }

    private String decisionError(DashboardMetricField field,
                                 DashboardMapping.DecisionValues values) {
        if (field != DashboardMetricField.DECISION_RESULT || values == null) {
            return null;
        }
        DashboardMapping.DecisionValues normalized = normalize(values);
        if (normalized.pass().isEmpty() || normalized.review().isEmpty()
                || normalized.reject().isEmpty()) {
            return "通过、人审和拒绝取值均不能为空";
        }
        Set<String> all = new LinkedHashSet<>();
        int total = normalized.pass().size() + normalized.review().size()
                + normalized.reject().size();
        all.addAll(normalized.pass());
        all.addAll(normalized.review());
        all.addAll(normalized.reject());
        return all.size() == total ? null : "通过、人审和拒绝取值不能重叠";
    }

    private DashboardMapping.ResolvedMapping valid(
            DashboardMetricField field,
            DashboardMapping.StoredMapping mapping,
            DashboardMapping.MappingSource source,
            String path,
            String label, DashboardMapping.DecisionValues decisions) {
        return new DashboardMapping.ResolvedMapping(field, mapping.refType(),
                mapping.refId(), mapping.jsonSource(), path, label, source,
                true, null, decisions);
    }

    private DashboardMapping.ResolvedMapping invalid(
            DashboardMetricField field,
            DashboardMapping.StoredMapping mapping,
            DashboardMapping.MappingSource source,
            String message) {
        return new DashboardMapping.ResolvedMapping(field,
                mapping == null ? null : mapping.refType(),
                mapping == null ? null : mapping.refId(),
                mapping == null ? null : mapping.jsonSource(),
                null, null, source, false, message,
                decisionValues(field,
                        mapping == null ? null : mapping.decisionValues()));
    }

    private DashboardMapping.StoredMapping normalizedStored(
            DashboardMetricField field,
            DashboardMapping.StoredMapping mapping) {
        return new DashboardMapping.StoredMapping(mapping.refType(),
                mapping.refId(), mapping.jsonSource(),
                decisionValues(field, mapping.decisionValues()));
    }

    private DashboardMapping.DecisionValues decisionValues(
            DashboardMetricField field,
            DashboardMapping.DecisionValues values) {
        if (field != DashboardMetricField.DECISION_RESULT) return null;
        return normalize(values == null ? DEFAULT_DECISIONS : values);
    }

    private DashboardMapping.DecisionValues normalize(
            DashboardMapping.DecisionValues values) {
        return new DashboardMapping.DecisionValues(
                normalizeSet(values.pass()), normalizeSet(values.review()),
                normalizeSet(values.reject()), values.passRefIds(), values.reviewRefIds(), values.rejectRefIds());
    }

    private DashboardMapping.DecisionValues resolveDecisions(DashboardMetricField field,
            DashboardMapping.DecisionValues values, Long projectId, boolean personalDefault) {
        var normalized = decisionValues(field, values);
        if (normalized == null) return null;
        return new DashboardMapping.DecisionValues(
                resolveConstants(normalized.passRefIds(), normalized.pass(), projectId, personalDefault),
                resolveConstants(normalized.reviewRefIds(), normalized.review(), projectId, personalDefault),
                resolveConstants(normalized.rejectRefIds(), normalized.reject(), projectId, personalDefault),
                normalized.passRefIds(), normalized.reviewRefIds(), normalized.rejectRefIds());
    }

    private Set<String> resolveConstants(Set<Long> ids, Set<String> legacy, Long projectId, boolean personalDefault) {
        if (ids.isEmpty()) return legacy;
        Set<String> values = new LinkedHashSet<>();
        for (Long id : ids) {
            RuleVariable constant = id == null ? null : referenceRepository.findVariable(id);
            if (constant == null || !Integer.valueOf(1).equals(constant.getStatus()) || !"CONSTANT".equals(constant.getVarSource())) {
                throw new IllegalArgumentException("决策结果常量不存在、已停用或无权访问");
            }
            String scope = scopeError(constant.getScope(), constant.getProjectId(), projectId, personalDefault);
            if (scope != null) throw new IllegalArgumentException(scope);
            Object value = ConstantValueCodec.toRuntimeValue(constant.getVarType(), constant.getDefaultValue());
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean) || value.toString().isBlank()) {
                throw new IllegalArgumentException("决策结果必须引用非空的文本、数值或布尔常量");
            }
            values.add(DashboardMapping.normalizeDecisionValue(String.valueOf(value)));
        }
        return values;
    }

    private Set<String> normalizeSet(Set<String> source) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (source != null) {
            for (String value : source) {
                if (value != null && !value.trim().isEmpty()) {
                    normalized.add(DashboardMapping.normalizeDecisionValue(value));
                }
            }
        }
        return normalized;
    }

    private StoredPreference load(Long userId) {
        if (userId == null) return new StoredPreference();
        String stored = preferenceService.find(userId, PREFERENCE_KEY);
        if (stored == null || stored.isBlank()) return new StoredPreference();
        try {
            JSONObject root = JSON.parseObject(stored);
            if (root == null || root.getIntValue("schemaVersion") != 1) {
                return new StoredPreference();
            }
            StoredPreference result = new StoredPreference();
            result.defaults.putAll(parseScope(root.getJSONObject("defaults")));
            JSONObject projects = root.getJSONObject("projects");
            if (projects != null) {
                for (String projectId : projects.keySet()) {
                    result.projects.put(projectId,
                            parseScope(projects.getJSONObject(projectId)));
                }
            }
            return result;
        } catch (RuntimeException exception) {
            LOGGER.warn("用户 {} 的数据看板字段偏好无效，已使用系统默认: {}",
                    userId, exception.getMessage());
            return new StoredPreference();
        }
    }

    private Map<DashboardMetricField, DashboardMapping.StoredMapping> parseScope(
            JSONObject object) {
        EnumMap<DashboardMetricField, DashboardMapping.StoredMapping> result =
                new EnumMap<>(DashboardMetricField.class);
        if (object == null) return result;
        for (String key : object.keySet()) {
            try {
                DashboardMetricField field = DashboardMetricField.valueOf(key);
                JSONObject value = object.getJSONObject(key);
                if (value == null) continue;
                DashboardMapping.RefType refType = DashboardMapping.RefType
                        .valueOf(value.getString("refType"));
                DashboardMapping.JsonSource jsonSource = DashboardMapping.JsonSource
                        .valueOf(value.getString("jsonSource"));
                DashboardMapping.DecisionValues decisions = parseDecisions(
                        value.getJSONObject("decisionValues"));
                result.put(field, new DashboardMapping.StoredMapping(refType,
                        value.getLong("refId"), jsonSource, decisions));
            } catch (RuntimeException ignored) {
                // 单个未知或损坏字段不影响其他有效映射。
            }
        }
        return result;
    }

    private DashboardMapping.DecisionValues parseDecisions(JSONObject value) {
        if (value == null) return null;
        return new DashboardMapping.DecisionValues(
                jsonSet(value, "pass"), jsonSet(value, "review"),
                jsonSet(value, "reject"), jsonIds(value, "passRefIds"), jsonIds(value, "reviewRefIds"), jsonIds(value, "rejectRefIds"));
    }

    private Set<Long> jsonIds(JSONObject value, String key) {
        return value.getJSONArray(key) == null ? Set.of() : new LinkedHashSet<>(value.getJSONArray(key).toJavaList(Long.class));
    }

    private Set<String> jsonSet(JSONObject value, String key) {
        List<String> strings = value.getJSONArray(key) == null
                ? Collections.emptyList()
                : value.getJSONArray(key).toJavaList(String.class);
        return new LinkedHashSet<>(strings);
    }

    private String serialize(StoredPreference preference) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("defaults", serializeScope(preference.defaults));
        Map<String, Object> projects = new LinkedHashMap<>();
        for (Map.Entry<String, Map<DashboardMetricField,
                DashboardMapping.StoredMapping>> entry
                : preference.projects.entrySet()) {
            projects.put(entry.getKey(), serializeScope(entry.getValue()));
        }
        root.put("projects", projects);
        return JSON.toJSONString(root);
    }

    private Map<String, Object> serializeScope(
            Map<DashboardMetricField, DashboardMapping.StoredMapping> scope) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<DashboardMetricField, DashboardMapping.StoredMapping> entry
                : scope.entrySet()) {
            DashboardMapping.StoredMapping mapping = entry.getValue();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("refType", mapping.refType().name());
            value.put("refId", mapping.refId());
            value.put("jsonSource", mapping.jsonSource().name());
            if (mapping.decisionValues() != null) {
                Map<String, Object> decisions = new LinkedHashMap<>();
                var decisionsValue = mapping.decisionValues();
                decisions.put("pass", new ArrayList<>(decisionsValue.passRefIds().isEmpty() ? decisionsValue.pass() : Set.of()));
                decisions.put("review", new ArrayList<>(decisionsValue.reviewRefIds().isEmpty() ? decisionsValue.review() : Set.of()));
                decisions.put("reject", new ArrayList<>(decisionsValue.rejectRefIds().isEmpty() ? decisionsValue.reject() : Set.of()));
                decisions.put("passRefIds", new ArrayList<>(decisionsValue.passRefIds()));
                decisions.put("reviewRefIds", new ArrayList<>(decisionsValue.reviewRefIds()));
                decisions.put("rejectRefIds", new ArrayList<>(decisionsValue.rejectRefIds()));
                value.put("decisionValues", decisions);
            }
            result.put(entry.getKey().name(), value);
        }
        return result;
    }

    private String jsonPath(List<String> components) {
        StringBuilder path = new StringBuilder("$");
        for (String component : components) {
            if (component == null || component.isEmpty()) {
                throw new IllegalArgumentException("字段编码不能为空");
            }
            path.append(".\"")
                    .append(component.replace("\\", "\\\\")
                            .replace("\"", "\\\""))
                    .append("\"");
        }
        return path.toString();
    }

    private String displayLabel(String label, String code) {
        if (label == null || label.isBlank()) return code;
        return label;
    }

    private static final class StoredPreference {
        private final Map<DashboardMetricField, DashboardMapping.StoredMapping>
                defaults = new EnumMap<>(DashboardMetricField.class);
        private final Map<String, Map<DashboardMetricField,
                DashboardMapping.StoredMapping>> projects =
                new LinkedHashMap<>();
    }
}
