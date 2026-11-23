package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class DataObjectSchemaResolver {

    private final RuleDataObjectMapper objectMapper;
    private final RuleDataObjectFieldMapper fieldMapper;

    public DataObjectSchemaResolver(RuleDataObjectMapper objectMapper,
                                    RuleDataObjectFieldMapper fieldMapper) {
        this.objectMapper = objectMapper;
        this.fieldMapper = fieldMapper;
    }

    public ShapeResult resolveRoot(Long objectId, Long projectId) {
        List<RuleValidationIssue> diagnostics = new ArrayList<>();
        ResolutionContext context = new ResolutionContext(projectId, diagnostics);
        Set<String> visiting = new LinkedHashSet<>();
        List<RuleDataObjectField> roots = context.referencedRootFields(objectId, visiting);
        NodeShape shape = roots.isEmpty() ? openFor("OBJECT") : objectFromFields(roots, context, visiting);
        return new ShapeResult(shape.schema, diagnostics, shape.leafTypes);
    }

    public ShapeResult resolveField(Long fieldId, Long projectId) {
        List<RuleValidationIssue> diagnostics = new ArrayList<>();
        if (fieldId == null) {
            diagnostics.add(RuleValidationIssue.error(
                    "REFERENCE_NOT_FOUND", "$", "数据对象字段 ID 不能为空"));
            return new ShapeResult(Collections.emptyMap(), diagnostics, Collections.emptyMap());
        }
        RuleDataObjectField field = fieldMapper.selectById(fieldId);
        if (field == null) {
            diagnostics.add(RuleValidationIssue.error(
                    "REFERENCE_NOT_FOUND", "$.field." + fieldId, "数据对象字段不存在"));
            return new ShapeResult(Collections.emptyMap(), diagnostics, Collections.emptyMap());
        }
        if (!active(field.getStatus()) || !available(
                field.getScope(), field.getProjectId(), projectId)) {
            diagnostics.add(RuleValidationIssue.error(
                    "REFERENCE_TYPE_MISMATCH", "$.field." + fieldId,
                    "数据对象字段状态或项目范围不匹配"));
            return new ShapeResult(Collections.emptyMap(), diagnostics, Collections.emptyMap());
        }

        ResolutionContext context = new ResolutionContext(projectId, diagnostics);
        if (field.getObjectId() != null
                && context.object(field.getObjectId()) == null) {
            return new ShapeResult(
                    Collections.emptyMap(), diagnostics, Collections.emptyMap());
        }
        NodeShape shape = resolveField(field, context, new LinkedHashSet<>());
        return new ShapeResult(shape.schema, diagnostics, shape.leafTypes);
    }

    private NodeShape resolveField(RuleDataObjectField field,
                                   ResolutionContext context,
                                   Set<String> visiting) {
        String fieldKey = "FIELD:" + field.getId();
        if (!visiting.add(fieldKey)) {
            context.cycle(fieldKey);
            return openFor(field.getVarType());
        }
        try {
            String type = normalizedType(field.getVarType());
            if ("OBJECT".equals(type) || "MAP".equals(type)) {
                return resolveObject(field, context, visiting);
            }
            if ("LIST".equals(type) || "ARRAY".equals(type) || "SET".equals(type)) {
                return resolveList(field, context, visiting);
            }
            Map<String, String> leafTypes = new LinkedHashMap<>();
            leafTypes.put("", type);
            return new NodeShape(property(type), leafTypes);
        } finally {
            visiting.remove(fieldKey);
        }
    }

    private NodeShape resolveObject(RuleDataObjectField field,
                                    ResolutionContext context,
                                    Set<String> visiting) {
        List<RuleDataObjectField> inlineFields = context.inlineChildren(field.getId());
        List<RuleDataObjectField> referencedFields =
                context.referencedRootFields(field.getRefObjectId(), visiting);
        List<RuleDataObjectField> fields =
                mergeByStableId(inlineFields, referencedFields);
        if (fields.isEmpty()) {
            context.incomplete(field, "对象字段缺少可由稳定 ID 证明的子字段");
            Map<String, Object> schema = property("OBJECT");
            schema.put("additionalProperties", true);
            return new NodeShape(schema, Collections.emptyMap());
        }
        return objectFromFields(fields, context, visiting);
    }

    private NodeShape resolveList(RuleDataObjectField field,
                                  ResolutionContext context,
                                  Set<String> visiting) {
        Map<String, Object> schema = property("LIST");
        List<RuleDataObjectField> inlineFields = context.inlineChildren(field.getId());
        List<RuleDataObjectField> referencedFields =
                context.referencedRootFields(field.getRefObjectId(), visiting);
        List<RuleDataObjectField> objectFields =
                mergeByStableId(inlineFields, referencedFields);
        if (!objectFields.isEmpty()) {
            NodeShape items = objectFromFields(objectFields, context, visiting);
            schema.put("items", items.schema);
            Map<String, String> leafTypes = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : items.leafTypes.entrySet()) {
                leafTypes.put(prefixListItem(entry.getKey()), entry.getValue());
            }
            return new NodeShape(schema, leafTypes);
        }

        String genericType = normalizedType(field.getGenericType());
        if (genericType != null
                && !"OBJECT".equals(genericType) && !"MAP".equals(genericType)) {
            schema.put("items", property(genericType));
            return new NodeShape(schema, Collections.singletonMap("[]", genericType));
        }

        context.incomplete(field, "列表字段缺少可由稳定 ID 证明的元素结构");
        schema.put("items", Collections.emptyMap());
        return new NodeShape(schema, Collections.emptyMap());
    }

    private NodeShape objectFromFields(List<RuleDataObjectField> fields,
                                       ResolutionContext context,
                                       Set<String> visiting) {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Long> propertyIds = new LinkedHashMap<>();
        Map<String, String> leafTypes = new LinkedHashMap<>();
        for (RuleDataObjectField child : fields) {
            String propertyName = fieldName(child);
            if (propertyName == null || child.getId() == null) {
                context.incomplete(child, "对象子字段缺少稳定 ID 或脚本字段名");
                continue;
            }
            Long existingId = propertyIds.get(propertyName);
            if (existingId != null && !existingId.equals(child.getId())) {
                context.conflict(child, propertyName, existingId);
                continue;
            }
            if (existingId != null) {
                continue;
            }
            NodeShape childShape = resolveField(child, context, visiting);
            properties.put(propertyName, childShape.schema);
            propertyIds.put(propertyName, child.getId());
            appendLeaves(leafTypes, propertyName, childShape.leafTypes);
        }
        Map<String, Object> schema = property("OBJECT");
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        return new NodeShape(schema, leafTypes);
    }

    private List<RuleDataObjectField> mergeByStableId(
            List<RuleDataObjectField> inlineFields,
            List<RuleDataObjectField> referencedFields) {
        Map<Long, RuleDataObjectField> fieldsById = new LinkedHashMap<>();
        for (RuleDataObjectField field : inlineFields) {
            if (field != null && field.getId() != null) {
                fieldsById.putIfAbsent(field.getId(), field);
            }
        }
        for (RuleDataObjectField field : referencedFields) {
            if (field != null && field.getId() != null) {
                fieldsById.putIfAbsent(field.getId(), field);
            }
        }
        return new ArrayList<>(fieldsById.values());
    }

    private void appendLeaves(Map<String, String> target,
                              String propertyName,
                              Map<String, String> childLeaves) {
        for (Map.Entry<String, String> entry : childLeaves.entrySet()) {
            String childPath = entry.getKey();
            String path;
            if (childPath == null || childPath.isEmpty()) {
                path = propertyName;
            } else if (childPath.startsWith("[]")) {
                path = propertyName + childPath;
            } else {
                path = propertyName + "." + childPath;
            }
            target.putIfAbsent(path, entry.getValue());
        }
    }

    private String prefixListItem(String path) {
        return path == null || path.isEmpty() ? "[]" : "[]." + path;
    }

    private NodeShape openFor(String fieldType) {
        String type = normalizedType(fieldType);
        if ("LIST".equals(type) || "ARRAY".equals(type) || "SET".equals(type)) {
            Map<String, Object> schema = property("LIST");
            schema.put("items", Collections.emptyMap());
            return new NodeShape(schema, Collections.emptyMap());
        }
        Map<String, Object> schema = property("OBJECT");
        schema.put("additionalProperties", true);
        return new NodeShape(schema, Collections.emptyMap());
    }

    private Map<String, Object> property(String rawType) {
        String ruleType = normalizedType(rawType);
        if (ruleType == null) {
            ruleType = "OBJECT";
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", switch (ruleType) {
            case "INTEGER", "LONG", "SHORT", "BYTE" -> "integer";
            case "NUMBER", "DOUBLE", "FLOAT", "DECIMAL", "BIGDECIMAL" -> "number";
            case "BOOLEAN", "BOOL" -> "boolean";
            case "LIST", "ARRAY", "SET" -> "array";
            case "OBJECT", "MAP" -> "object";
            default -> "string";
        });
        if ("DATE".equals(ruleType)) {
            schema.put("format", "date");
        } else if ("DATETIME".equals(ruleType) || "LOCALDATETIME".equals(ruleType)) {
            schema.put("format", "date-time");
        }
        schema.put("x-rule-type", ruleType);
        return schema;
    }

    private String fieldName(RuleDataObjectField field) {
        if (field == null) {
            return null;
        }
        String scriptName = trimToNull(field.getScriptName());
        String name = scriptName != null ? scriptName : trimToNull(field.getVarCode());
        if (name == null) return null;
        // 导入保存对象内相对路径；嵌套 JSON Schema 的 properties 只使用当前一级属性名。
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private String normalizedType(String type) {
        String value = trimToNull(type);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean active(Integer status) {
        return Integer.valueOf(1).equals(status);
    }

    private boolean available(String scope, Long resourceProjectId, Long projectId) {
        return RuleVariableService.SCOPE_GLOBAL.equalsIgnoreCase(
                scope == null ? "" : scope.trim())
                || projectId == null
                || Objects.equals(resourceProjectId, projectId);
    }

    private final class ResolutionContext {

        private final Long projectId;
        private final List<RuleValidationIssue> diagnostics;
        private final Map<Long, RuleDataObject> objects = new LinkedHashMap<>();
        private final Set<Long> missingObjects = new LinkedHashSet<>();
        private final Map<Long, List<RuleDataObjectField>> inlineFields =
                new LinkedHashMap<>();
        private final Map<Long, List<RuleDataObjectField>> objectFields =
                new LinkedHashMap<>();
        private final Set<String> diagnosticKeys = new LinkedHashSet<>();

        private ResolutionContext(Long projectId,
                                  List<RuleValidationIssue> diagnostics) {
            this.projectId = projectId;
            this.diagnostics = diagnostics;
        }

        private List<RuleDataObjectField> inlineChildren(Long fieldId) {
            if (fieldId == null) {
                return Collections.emptyList();
            }
            return inlineFields.computeIfAbsent(fieldId, id -> {
                List<RuleDataObjectField> selected = fieldMapper.selectList(
                        new LambdaQueryWrapper<RuleDataObjectField>()
                                .eq(RuleDataObjectField::getParentFieldId, id)
                                .eq(RuleDataObjectField::getStatus, 1)
                                .orderByAsc(RuleDataObjectField::getSortOrder)
                                .orderByAsc(RuleDataObjectField::getId));
                List<RuleDataObjectField> result = new ArrayList<>();
                for (RuleDataObjectField field : selected == null
                        ? Collections.<RuleDataObjectField>emptyList() : selected) {
                    if (Objects.equals(field.getParentFieldId(), id)
                            && active(field.getStatus())
                            && available(field.getScope(), field.getProjectId(), projectId)) {
                        result.add(field);
                    }
                }
                return result;
            });
        }

        private List<RuleDataObjectField> referencedRootFields(
                Long objectId, Set<String> visiting) {
            if (objectId == null) {
                return Collections.emptyList();
            }
            String objectKey = "OBJECT:" + objectId;
            if (!visiting.add(objectKey)) {
                cycle(objectKey);
                return Collections.emptyList();
            }
            try {
                RuleDataObject object = object(objectId);
                if (object == null) {
                    return Collections.emptyList();
                }
                List<RuleDataObjectField> selected =
                        objectFields.computeIfAbsent(objectId, id -> {
                            List<RuleDataObjectField> values = fieldMapper.selectList(
                                    new LambdaQueryWrapper<RuleDataObjectField>()
                                            .eq(RuleDataObjectField::getObjectId, id)
                                            .eq(RuleDataObjectField::getStatus, 1)
                                            .orderByAsc(RuleDataObjectField::getSortOrder)
                                            .orderByAsc(RuleDataObjectField::getId));
                            List<RuleDataObjectField> result = new ArrayList<>();
                            for (RuleDataObjectField field : values == null
                                    ? Collections.<RuleDataObjectField>emptyList() : values) {
                                if (Objects.equals(field.getObjectId(), id)
                                        && active(field.getStatus())
                                        && available(field.getScope(), field.getProjectId(), projectId)) {
                                    result.add(field);
                                }
                            }
                            return result;
                        });
                List<RuleDataObjectField> roots = new ArrayList<>();
                for (RuleDataObjectField field : selected) {
                    if (field.getParentFieldId() == null) {
                        roots.add(field);
                    }
                }
                return roots;
            } finally {
                visiting.remove(objectKey);
            }
        }

        private RuleDataObject object(Long objectId) {
            if (objects.containsKey(objectId)) {
                return objects.get(objectId);
            }
            if (missingObjects.contains(objectId)) {
                return null;
            }
            RuleDataObject object = objectMapper.selectById(objectId);
            if (object == null) {
                missingObjects.add(objectId);
                addDiagnostic("REFERENCE_NOT_FOUND|OBJECT:" + objectId,
                        RuleValidationIssue.error(
                                "REFERENCE_NOT_FOUND", "$.object." + objectId,
                                "引用数据对象不存在"));
                return null;
            }
            if (!active(object.getStatus())
                    || !available(object.getScope(), object.getProjectId(), projectId)) {
                missingObjects.add(objectId);
                addDiagnostic("REFERENCE_TYPE_MISMATCH|OBJECT:" + objectId,
                        RuleValidationIssue.error(
                                "REFERENCE_TYPE_MISMATCH", "$.object." + objectId,
                                "引用数据对象状态或项目范围不匹配"));
                return null;
            }
            objects.put(objectId, object);
            return object;
        }

        private void incomplete(RuleDataObjectField field, String message) {
            Long fieldId = field == null ? null : field.getId();
            addDiagnostic("OBJECT_SHAPE_INCOMPLETE|FIELD:" + fieldId,
                    RuleValidationIssue.warning(
                            "OBJECT_SHAPE_INCOMPLETE",
                            "$.field." + fieldId, message));
        }

        private void conflict(RuleDataObjectField field,
                              String propertyName,
                              Long existingId) {
            addDiagnostic("OBJECT_SHAPE_CONFLICT|" + propertyName + "|"
                            + existingId + "|" + field.getId(),
                    RuleValidationIssue.error(
                            "OBJECT_SHAPE_CONFLICT",
                            "$.field." + field.getId(),
                            "同名对象属性对应不同稳定字段 ID: " + propertyName));
        }

        private void cycle(String key) {
            addDiagnostic("DEPENDENCY_CYCLE|" + key,
                    RuleValidationIssue.error(
                            "DEPENDENCY_CYCLE", "$." + key,
                            "数据对象字段依赖形成循环"));
        }

        private void addDiagnostic(String key, RuleValidationIssue issue) {
            if (diagnosticKeys.add(key)) {
                diagnostics.add(issue);
            }
        }
    }

    private static final class NodeShape {

        private final Map<String, Object> schema;
        private final Map<String, String> leafTypes;

        private NodeShape(Map<String, Object> schema,
                          Map<String, String> leafTypes) {
            this.schema = schema;
            this.leafTypes = leafTypes;
        }
    }

    public static final class ShapeResult {

        private final Map<String, Object> schema;
        private final List<RuleValidationIssue> diagnostics;
        private final Map<String, String> leafTypesByPath;

        public ShapeResult(Map<String, Object> schema,
                           List<RuleValidationIssue> diagnostics,
                           Map<String, String> leafTypesByPath) {
            this.schema = deepCopySchema(schema);
            this.diagnostics = List.copyOf(diagnostics);
            this.leafTypesByPath = Collections.unmodifiableMap(
                    new LinkedHashMap<>(leafTypesByPath));
        }

        public Map<String, Object> getSchema() {
            return deepCopySchema(schema);
        }

        public List<RuleValidationIssue> getDiagnostics() {
            return diagnostics;
        }

        public Map<String, String> getLeafTypesByPath() {
            return leafTypesByPath;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T deepCopyValue(T value) {
        if (value instanceof Map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
            }
            return (T) copy;
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<?>) value) {
                copy.add(deepCopyValue(item));
            }
            return (T) copy;
        }
        return value;
    }

    private static Map<String, Object> deepCopySchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return deepCopyValue(schema);
    }
}
