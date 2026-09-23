package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.derived.HistoryFieldValues;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 将数据对象字段引用的变量值组装到规则运行时对象路径。 */
@Service
public class DataObjectFieldReferenceResolver {

    @Resource
    private RuleDataObjectFieldMapper dataObjectFieldMapper;

    @Resource
    private RuleVariableMapper ruleVariableMapper;
    @Resource private RuleDataObjectMapper dataObjectMapper;

    public ReferencePlan resolveLive(List<RuleDefinitionInputField> directFields) {
        if (dataObjectFieldMapper == null || ruleVariableMapper == null) {
            return ReferencePlan.empty();
        }
        Map<Long, RuleDataObjectField> fields = new LinkedHashMap<>();
        Map<Long, RuleVariable> variables = new LinkedHashMap<>();
        for (RuleDefinitionInputField direct : safe(directFields)) {
            if (!isDataObjectReference(direct) || direct.getVarId() == null) {
                continue;
            }
            RuleDataObjectField field = dataObjectFieldMapper.selectById(direct.getVarId());
            if (field == null || !field.referencesValue()) {
                continue;
            }
            var owner = dataObjectMapper == null || field.getObjectId() == null ? null : dataObjectMapper.selectById(field.getObjectId());
            field.setLazyReference(owner != null && Boolean.TRUE.equals(owner.getLazyLoadReferences()));
            RuleVariable variable = ruleVariableMapper.selectById(field.getRefVariableId());
            if (variable != null) {
                fields.put(field.getId(), field);
                variables.put(variable.getId(), variable);
            }
        }
        return buildPlan(directFields, fields, variables);
    }

    public ReferencePlan resolveSnapshot(
            List<RuleDefinitionInputField> directFields,
            List<RuleDataObjectField> dataObjectFields,
            List<RuleVariable> variables) {
        Map<Long, RuleDataObjectField> fieldMap = new LinkedHashMap<>();
        for (RuleDataObjectField field : safe(dataObjectFields)) {
            if (field != null && field.getId() != null) {
                fieldMap.put(field.getId(), field);
            }
        }
        Map<Long, RuleVariable> variableMap = new LinkedHashMap<>();
        for (RuleVariable variable : safe(variables)) {
            if (variable != null && variable.getId() != null) {
                variableMap.put(variable.getId(), variable);
            }
        }
        return buildPlan(directFields, fieldMap, variableMap);
    }

    private ReferencePlan buildPlan(
            List<RuleDefinitionInputField> directFields,
            Map<Long, RuleDataObjectField> fields,
            Map<Long, RuleVariable> variables) {
        List<Binding> bindings = new ArrayList<>();
        Set<String> seenTargets = new LinkedHashSet<>();
        for (RuleDefinitionInputField direct : safe(directFields)) {
            if (!isDataObjectReference(direct) || direct.getVarId() == null) {
                continue;
            }
            RuleDataObjectField mappedField = fields.get(direct.getVarId());
            if (mappedField == null || !mappedField.referencesValue()) {
                continue;
            }
            RuleVariable source = variables.get(mappedField.getRefVariableId());
            String targetPath = pathOf(direct);
            String sourcePath = source == null ? null : firstText(source.getScriptName(), source.getVarCode());
            if (targetPath == null || sourcePath == null
                    || !seenTargets.add(targetPath.toLowerCase(Locale.ROOT))) {
                continue;
            }
            bindings.add(new Binding(targetPath, sourcePath,
                    copyTargetField(direct), sourceField(source), Boolean.TRUE.equals(mappedField.getLazyReference())
                    && !Set.of("OBJECT", "MAP").contains(mappedField.getVarType() == null ? "" : mappedField.getVarType())
                    && !Set.of("OBJECT", "MAP").contains(mappedField.getGenericType() == null ? "" : mappedField.getGenericType())
                    && mappedField.getRefObjectId() == null
                    && fields.values().stream().noneMatch(child -> mappedField.getId().equals(child.getParentFieldId()))));
        }
        Set<String> directSources = new LinkedHashSet<>();
        for (var direct : safe(directFields)) if (!isDataObjectReference(direct) && pathOf(direct) != null) directSources.add(pathOf(direct));
        return bindings.isEmpty() ? ReferencePlan.empty() : new ReferencePlan(bindings, directSources);
    }

    private RuleDefinitionInputField copyTargetField(RuleDefinitionInputField source) {
        RuleDefinitionInputField target = new RuleDefinitionInputField();
        target.setVarId(source.getVarId());
        target.setRefType(source.getRefType());
        target.setFieldName(source.getFieldName());
        target.setFieldLabel(source.getFieldLabel());
        target.setScriptName(source.getScriptName());
        target.setFieldType(source.getFieldType());
        target.setStatus(1);
        return target;
    }

    private RuleDefinitionInputField sourceField(RuleVariable source) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setVarId(source.getId());
        field.setRefType("CONSTANT".equalsIgnoreCase(source.getVarSource())
                ? "CONSTANT" : "VARIABLE");
        field.setFieldName(source.getVarCode());
        field.setFieldLabel(source.getVarLabel());
        field.setScriptName(firstText(source.getScriptName(), source.getVarCode()));
        field.setFieldType(source.getVarType());
        field.setDefaultValue(source.getDefaultValue());
        field.setExampleValue(source.getExampleValue());
        field.setStatus(1);
        return field;
    }

    private boolean isDataObjectReference(RuleDefinitionInputField field) {
        return field != null && "DATA_OBJECT".equalsIgnoreCase(text(field.getRefType()));
    }

    private static String pathOf(RuleDefinitionInputField field) {
        return field == null ? null : firstText(field.getScriptName(), field.getFieldName());
    }

    private static String firstText(String... values) {
        for (String value : values) {
            String text = text(value);
            if (text != null) return text;
        }
        return null;
    }

    private static String text(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private static final class Binding {
        private final String targetPath;
        private final String sourcePath;
        private final RuleDefinitionInputField targetField;
        private final RuleDefinitionInputField sourceField;
        private final boolean lazy;

        private Binding(String targetPath, String sourcePath,
                        RuleDefinitionInputField targetField,
                        RuleDefinitionInputField sourceField, boolean lazy) {
            this.targetPath = targetPath;
            this.sourcePath = sourcePath;
            this.targetField = targetField;
            this.sourceField = sourceField;
            this.lazy = lazy;
        }
    }

    public static final class ReferencePlan {
        private static final ReferencePlan EMPTY =
                new ReferencePlan(Collections.emptyList(), Set.of());

        private final List<Binding> bindings;
        private final Set<String> directSources;

        private ReferencePlan(List<Binding> bindings, Set<String> directSources) {
            this.bindings = Collections.unmodifiableList(new ArrayList<>(bindings));
            this.directSources = Set.copyOf(directSources);
        }

        public Set<String> withoutBindingNames(Set<String> names) {
            Set<String> result = new LinkedHashSet<>(names);
            for (Binding binding : bindings) {
                result.remove(binding.targetPath);
                if (!directSources.contains(binding.sourcePath)) result.remove(binding.sourcePath);
            }
            return result;
        }

        public Set<String> targetPaths() {
            Set<String> paths = new LinkedHashSet<>(); bindings.forEach(binding -> paths.add(binding.targetPath)); return paths;
        }

        public Set<String> lazyPaths() {
            Set<String> paths = new LinkedHashSet<>(); bindings.stream().filter(binding -> binding.lazy).forEach(binding -> paths.add(binding.targetPath)); return paths;
        }

        public Set<String> deferredStatusKeys() {
            Set<String> keys = new LinkedHashSet<>();
            for (Binding binding : bindings) if (binding.lazy) {
                keys.add(fieldKey(binding.targetField));
                if (!directSources.contains(binding.sourcePath)) keys.add(fieldKey(binding.sourceField));
            }
            return keys;
        }

        public Set<String> sourceStatusKeys(Set<String> selected, Set<String> original) {
            Set<String> keys = new LinkedHashSet<>(original); keys.removeAll(deferredStatusKeys());
            for (Binding binding : bindings) if (selected.contains(binding.sourcePath)
                    && (original.contains(fieldKey(binding.targetField)) || original.contains(fieldKey(binding.sourceField)))) keys.add(fieldKey(binding.sourceField));
            return keys;
        }

        public Set<String> sourcesForStatus(String key, Map<String, Object> values) {
            Set<String> names = new LinkedHashSet<>();
            for (Binding binding : bindings) if (binding.lazy && (fieldKey(binding.sourceField).equals(key)
                    || fieldKey(binding.targetField).equals(key) && !HistoryFieldValues.present(values, binding.targetPath))) names.add(binding.sourcePath);
            return names;
        }

        public void copyTargetStates(Map<String, Object> values, VariableResolveOptions options) {
            for (Binding binding : bindings) {
                if (!HistoryFieldValues.present(values, binding.targetPath)) continue;
                var state = options.getSourceStates().get(fieldKey(binding.sourceField));
                if (state != null) state.forEach((dimension, value) -> options.recordSourceState("DATA_OBJECT", binding.targetField.getVarId(), dimension, value));
                options.recordSourceState("DATA_OBJECT", binding.targetField.getVarId(), "PRESENCE", "PRESENT");
            }
        }

        public Set<String> missingSources(String path, Map<String, Object> values, boolean includeLazy) {
            Set<String> sources = new LinkedHashSet<>();
            for (Binding binding : bindings) {
                boolean matches = path == null || binding.targetPath.equals(path) || binding.targetPath.startsWith(path + ".") || path.startsWith(binding.targetPath + ".");
                if (matches && (includeLazy || !binding.lazy || binding.targetPath.equals(path)) && !HistoryFieldValues.present(values, binding.targetPath)) sources.add(binding.sourcePath);
            }
            return sources;
        }

        public static ReferencePlan empty() {
            return EMPTY;
        }

        public List<RuleDefinitionInputField> mergeBindingFields(
                List<RuleDefinitionInputField> inputFields) {
            Map<String, RuleDefinitionInputField> merged = new LinkedHashMap<>();
            for (RuleDefinitionInputField field : safe(inputFields)) {
                merged.putIfAbsent(fieldKey(field), field);
            }
            for (Binding binding : bindings) {
                merged.putIfAbsent(fieldKey(binding.targetField), binding.targetField);
                merged.putIfAbsent(fieldKey(binding.sourceField), binding.sourceField);
            }
            return new ArrayList<>(merged.values());
        }

        public Set<String> requiredSourceNames() {
            Set<String> names = new LinkedHashSet<>();
            for (Binding binding : bindings) {
                names.add(binding.sourcePath);
            }
            return names;
        }

        public Set<String> captureExplicitTargets(Map<String, Object> input) {
            Set<String> paths = new LinkedHashSet<>();
            for (Binding binding : bindings) {
                if (HistoryFieldValues.present(input, binding.targetPath)) {
                    paths.add(binding.targetPath);
                }
            }
            return paths;
        }

        public void apply(Map<String, Object> values, Set<String> explicitTargets) {
            apply(values, explicitTargets, requiredSourceNames());
        }

        public void apply(Map<String, Object> values, Set<String> explicitTargets, Set<String> resolvedNames) {
            Set<String> explicit = explicitTargets == null
                    ? Collections.emptySet() : explicitTargets;
            for (Binding binding : bindings) {
                if (explicit.contains(binding.targetPath) || HistoryFieldValues.present(values, binding.targetPath)
                        || !resolvedNames.contains(binding.sourcePath)) {
                    continue;
                }
                PathValue source = readPath(values, binding.sourcePath);
                if (source.present) {
                    setPath(values, binding.targetPath, source.value);
                }
            }
        }

        private static String fieldKey(RuleDefinitionInputField field) {
            if (field != null && field.getVarId() != null && text(field.getRefType()) != null) {
                return field.getRefType().trim().toUpperCase(Locale.ROOT)
                        + ":" + field.getVarId();
            }
            String path = pathOf(field);
            return "PATH:" + (path == null ? "" : path.toLowerCase(Locale.ROOT));
        }

        private static PathValue readPath(Map<String, Object> values, String path) {
            if (values == null || path == null) return PathValue.missing();
            if (values.containsKey(path)) {
                return new PathValue(true, values.get(path));
            }
            Object current = values;
            for (String part : path.split("\\.")) {
                if (!(current instanceof Map)
                        || !((Map<?, ?>) current).containsKey(part)) {
                    return PathValue.missing();
                }
                current = ((Map<?, ?>) current).get(part);
            }
            return new PathValue(true, current);
        }

        @SuppressWarnings("unchecked")
        private static void setPath(Map<String, Object> values, String path, Object value) {
            String[] parts = path.split("\\.");
            Map<String, Object> current = values;
            for (int i = 0; i < parts.length; i++) {
                if (i == parts.length - 1) {
                    current.put(parts[i], value);
                    return;
                }
                Object child = current.get(parts[i]);
                if (child == null) {
                    child = new LinkedHashMap<String, Object>();
                    current.put(parts[i], child);
                } else if (!(child instanceof Map)) {
                    throw new IllegalArgumentException("数据对象引用无法写入路径[" + path
                            + "]：中间节点[" + parts[i] + "]不是对象");
                }
                current = (Map<String, Object>) child;
            }
        }
    }

    private static final class PathValue {
        private final boolean present;
        private final Object value;

        private PathValue(boolean present, Object value) {
            this.present = present;
            this.value = value;
        }

        private static PathValue missing() {
            return new PathValue(false, null);
        }
    }
}
