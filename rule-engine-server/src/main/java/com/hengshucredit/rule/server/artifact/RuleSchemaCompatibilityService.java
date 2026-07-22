package com.hengshucredit.rule.server.artifact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class RuleSchemaCompatibilityService {

    public CompatibilityReport compare(Map<String, Object> previousInput,
                                       Map<String, Object> previousOutput,
                                       Map<String, Object> currentInput,
                                       Map<String, Object> currentOutput) {
        List<SchemaChange> changes = new ArrayList<>();
        compareInput(properties(previousInput), required(previousInput),
                properties(currentInput), required(currentInput), changes);
        compareOutput(properties(previousOutput), required(previousOutput),
                properties(currentOutput), required(currentOutput), changes);
        return new CompatibilityReport(changes);
    }

    public CompatibilityReport compare(String previousInputJson, String previousOutputJson,
                                       String currentInputJson, String currentOutputJson) {
        return compare(CanonicalJson.readMap(previousInputJson), CanonicalJson.readMap(previousOutputJson),
                CanonicalJson.readMap(currentInputJson), CanonicalJson.readMap(currentOutputJson));
    }

    private void compareInput(Map<String, Object> previous, Set<String> previousRequired,
                              Map<String, Object> current, Set<String> currentRequired,
                              List<SchemaChange> changes) {
        for (String name : sortedUnion(previous.keySet(), current.keySet())) {
            if (!current.containsKey(name)) {
                changes.add(change("INPUT_REMOVED", name, true, "输入字段被删除"));
            } else if (!previous.containsKey(name)) {
                boolean breaking = currentRequired.contains(name);
                changes.add(change(breaking ? "REQUIRED_INPUT_ADDED" : "OPTIONAL_INPUT_ADDED",
                        name, breaking, breaking ? "新增必填输入字段" : "新增可选输入字段"));
            } else {
                compareProperty("INPUT", name, previous.get(name), current.get(name), changes);
                if (!previousRequired.contains(name) && currentRequired.contains(name)) {
                    changes.add(change("INPUT_REQUIRED_TIGHTENED", name, true, "输入字段由可选变为必填"));
                } else if (previousRequired.contains(name) && !currentRequired.contains(name)) {
                    changes.add(change("INPUT_REQUIRED_RELAXED", name, false, "输入字段由必填变为可选"));
                }
            }
        }
    }

    private void compareOutput(Map<String, Object> previous, Set<String> previousRequired,
                               Map<String, Object> current, Set<String> currentRequired,
                               List<SchemaChange> changes) {
        for (String name : sortedUnion(previous.keySet(), current.keySet())) {
            if (!current.containsKey(name)) {
                changes.add(change("OUTPUT_REMOVED", name, true, "输出字段被删除"));
            } else if (!previous.containsKey(name)) {
                changes.add(change("OUTPUT_ADDED", name, false, "新增输出字段"));
            } else {
                compareProperty("OUTPUT", name, previous.get(name), current.get(name), changes);
                if (previousRequired.contains(name) && !currentRequired.contains(name)) {
                    changes.add(change("OUTPUT_REQUIRED_RELAXED", name, true, "必有输出变为可选输出"));
                }
            }
        }
    }

    private void compareProperty(String direction, String name, Object previousProperty,
                                 Object currentProperty, List<SchemaChange> changes) {
        Map<String, Object> previous = property(previousProperty);
        Map<String, Object> current = property(currentProperty);
        Object previousType = previous.get("type");
        Object currentType = current.get("type");
        if (!java.util.Objects.equals(previousType, currentType)) {
            changes.add(new SchemaChange(direction + "_TYPE_CHANGED", name, true,
                    "字段类型由 " + previousType + " 变为 " + currentType));
        }
        Object previousFormat = previous.get("format");
        Object currentFormat = current.get("format");
        if (!java.util.Objects.equals(previousFormat, currentFormat)) {
            changes.add(new SchemaChange(direction + "_FORMAT_CHANGED", name, true,
                    "字段格式由 " + previousFormat + " 变为 " + currentFormat));
        }
        Map<String, Object> previousConstraints = constraints(previous);
        Map<String, Object> currentConstraints = constraints(current);
        if (!java.util.Objects.equals(previousConstraints, currentConstraints)) {
            changes.add(new SchemaChange(direction + "_CONSTRAINT_CHANGED", name, true,
                    "字段约束发生变化"));
        }
    }

    private Map<String, Object> constraints(Map<String, Object> property) {
        Map<String, Object> constraints = new java.util.TreeMap<>(property);
        constraints.remove("type");
        constraints.remove("format");
        constraints.remove("title");
        constraints.remove("description");
        constraints.remove("$comment");
        return constraints;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> properties(Map<String, Object> schema) {
        Object value = schema == null ? null : schema.get("properties");
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> property(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Collections.emptyMap();
    }

    private Set<String> required(Map<String, Object> schema) {
        Object value = schema == null ? null : schema.get("required");
        if (!(value instanceof List<?> list)) {
            return Collections.emptySet();
        }
        Set<String> required = new HashSet<>();
        for (Object item : list) {
            if (item instanceof String name) {
                required.add(name);
            }
        }
        return required;
    }

    private Set<String> sortedUnion(Set<String> first, Set<String> second) {
        Set<String> names = new TreeSet<>(first);
        names.addAll(second);
        return names;
    }

    private SchemaChange change(String changeType, String fieldName,
                                boolean breaking, String message) {
        return new SchemaChange(changeType, fieldName, breaking, message);
    }

    public static final class CompatibilityReport {
        private final List<SchemaChange> changes;

        private CompatibilityReport(List<SchemaChange> changes) {
            this.changes = List.copyOf(changes);
        }

        public List<SchemaChange> getChanges() {
            return changes;
        }

        public boolean hasBreakingChanges() {
            return changes.stream().anyMatch(SchemaChange::isBreaking);
        }
    }

    public static final class SchemaChange {
        private final String changeType;
        private final String fieldName;
        private final boolean breaking;
        private final String message;

        private SchemaChange(String changeType, String fieldName, boolean breaking, String message) {
            this.changeType = changeType;
            this.fieldName = fieldName;
            this.breaking = breaking;
            this.message = message;
        }

        public String getChangeType() {
            return changeType;
        }

        public String getFieldName() {
            return fieldName;
        }

        public boolean isBreaking() {
            return breaking;
        }

        public String getMessage() {
            return message;
        }
    }
}
