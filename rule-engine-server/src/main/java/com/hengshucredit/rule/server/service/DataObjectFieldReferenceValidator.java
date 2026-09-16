package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 对象字段选择引用与审批生效共用的校验，引用始终按稳定 ID 解析。 */
@Service
public class DataObjectFieldReferenceValidator {
    private final RuleVariableMapper variableMapper;
    private final RuleDataObjectMapper objectMapper;
    private final RuleDataObjectFieldMapper fieldMapper;

    public DataObjectFieldReferenceValidator(RuleVariableMapper variableMapper,
                                            RuleDataObjectMapper objectMapper,
                                            RuleDataObjectFieldMapper fieldMapper) {
        this.variableMapper = variableMapper;
        this.objectMapper = objectMapper;
        this.fieldMapper = fieldMapper;
    }

    public List<RuleValidationIssue> validateCandidate(Long objectId, RuleDataObjectField field) {
        RuleDataObject owner = objectMapper.selectById(objectId);
        if (owner == null) throw new IllegalArgumentException("所属数据对象不存在");
        List<RuleDataObjectField> fields = objectFields(objectId);
        if (field.getId() != null && fields.stream().noneMatch(item -> Objects.equals(item.getId(), field.getId()))) {
            throw new IllegalArgumentException("字段不属于当前数据对象");
        }
        return validate(owner, field, fields);
    }

    public List<RuleValidationIssue> validate(RuleDataObject owner, RuleDataObjectField field,
                                            List<RuleDataObjectField> fields) {
        List<RuleValidationIssue> issues = new ArrayList<>();
        if (field.getRefVariableId() != null && field.getRefObjectId() != null) {
            add(issues, field, "REFERENCE_CONFLICT", "只能绑定一个引用变量、常量或数据对象");
            return issues;
        }
        if (field.getRefVariableId() != null) {
            validateVariable(owner, field, fields, issues);
        } else if (field.getRefObjectId() != null) {
            validateObject(owner, field, fields, issues);
        } else if (field.getRefObjectCode() != null && !field.getRefObjectCode().isBlank()) {
            add(issues, field, "OBJECT_ID_REQUIRED", "引用对象编码不能建立关联，请从引用变量中选择数据对象");
        }
        return issues;
    }

    private void validateVariable(RuleDataObject owner, RuleDataObjectField field,
                                  List<RuleDataObjectField> fields, List<RuleValidationIssue> issues) {
        RuleVariable variable = variableMapper == null ? null : variableMapper.selectById(field.getRefVariableId());
        if (variable == null) {
            add(issues, field, "VARIABLE_NOT_FOUND", "引用的变量或常量不存在");
            return;
        }
        if (!Integer.valueOf(1).equals(variable.getStatus())) {
            add(issues, field, "VARIABLE_DISABLED", "引用的变量或常量未启用");
            return;
        }
        if (!available(owner, variable.getScope(), variable.getProjectId())) {
            add(issues, field, "VARIABLE_SCOPE_MISMATCH", "只能引用全局或所属项目的变量、常量");
        }
        if (!typeCompatible(field.getVarType(), variable.getVarType())) {
            add(issues, field, "VARIABLE_TYPE_MISMATCH", "字段类型 " + field.getVarType()
                    + " 与引用类型 " + variable.getVarType() + " 不兼容");
        }
        if ("CONSTANT".equals(variable.getVarSource()) && isList(field.getVarType()) && !normalized(field.getGenericType()).isEmpty()) {
            try {
                List<?> values = JSON.parseArray(variable.getDefaultValue());
                if (values == null || values.stream().filter(Objects::nonNull).anyMatch(value -> !typeCompatible(field.getGenericType(),
                        value instanceof Number ? "NUMBER" : value instanceof Boolean ? "BOOLEAN" : value instanceof Map ? "MAP" : value instanceof List ? "LIST" : "STRING"))) {
                    add(issues, field, "VARIABLE_TYPE_MISMATCH", "列表常量的元素类型与字段元素类型不兼容");
                }
            } catch (RuntimeException exception) {
                add(issues, field, "VARIABLE_TYPE_MISMATCH", "列表常量值不是有效的 JSON 数组");
            }
        }
        Set<Long> visited = new HashSet<>();
        Long parentId = field.getParentFieldId();
        while (parentId != null && visited.add(parentId)) {
            Long id = parentId;
            RuleDataObjectField parent = fields.stream().filter(item -> Objects.equals(id, item.getId())).findFirst().orElse(null);
            if (parent == null) break;
            if (isList(parent.getVarType())) {
                add(issues, field, "LIST_CHILD_REFERENCE_UNSUPPORTED", "列表元素内部字段不能直接取变量或常量值，请在列表字段上整体引用；内部对象仍可关联数据对象结构");
                break;
            }
            parentId = parent.getParentFieldId();
        }
    }

    private void validateObject(RuleDataObject owner, RuleDataObjectField field,
                                List<RuleDataObjectField> fields, List<RuleValidationIssue> issues) {
        String type = normalized(field.getVarType());
        String generic = normalized(field.getGenericType());
        if (!("OBJECT".equals(type) || "MAP".equals(type)
                || (isList(type) && (generic.isEmpty() || "OBJECT".equals(generic) || "MAP".equals(generic))))) {
            add(issues, field, "OBJECT_TYPE_MISMATCH", "数据对象只能绑定到对象字段或元素为对象的列表字段");
            return;
        }
        RuleDataObject target = objectMapper == null ? null : objectMapper.selectById(field.getRefObjectId());
        if (target == null || !Integer.valueOf(1).equals(target.getStatus())) {
            add(issues, field, "OBJECT_NOT_AVAILABLE", "引用的数据对象不存在或未启用");
            return;
        }
        if (!available(owner, target.getScope(), target.getProjectId())) {
            add(issues, field, "OBJECT_SCOPE_MISMATCH", "只能引用全局或所属项目的数据对象");
        }
        if (containsCycle(target.getId(), owner.getId(), new HashSet<>())) {
            add(issues, field, "OBJECT_REFERENCE_CYCLE", "该数据对象引用会形成循环，不能绑定");
        }
        if (field.getId() != null) {
            Set<String> inlineNames = new HashSet<>();
            fields.stream().filter(item -> Objects.equals(field.getId(), item.getParentFieldId()))
                    .forEach(item -> inlineNames.add(item.getVarCode()));
            if (objectFields(target.getId()).stream().anyMatch(item -> item.getParentFieldId() == null && inlineNames.contains(item.getVarCode()))) {
                add(issues, field, "OBJECT_FIELD_CONFLICT", "引用对象与当前内嵌子字段存在同名字段，请先解决结构冲突");
            }
        }
    }

    private boolean containsCycle(Long objectId, Long ownerId, Set<Long> visiting) {
        if (Objects.equals(objectId, ownerId) || !visiting.add(objectId)) return true;
        try {
            for (RuleDataObjectField field : objectFields(objectId)) {
                if (field.getRefObjectId() != null && containsCycle(field.getRefObjectId(), ownerId, visiting)) return true;
            }
            return false;
        } finally {
            visiting.remove(objectId);
        }
    }

    private List<RuleDataObjectField> objectFields(Long objectId) {
        List<RuleDataObjectField> fields = fieldMapper.selectList(new LambdaQueryWrapper<RuleDataObjectField>()
                .eq(RuleDataObjectField::getObjectId, objectId));
        return fields == null ? List.of() : fields;
    }

    private boolean available(RuleDataObject owner, String scope, Long projectId) {
        return "GLOBAL".equals(normalized(scope)) || ("PROJECT".equals(normalized(owner.getScope()))
                && "PROJECT".equals(normalized(scope)) && owner.getProjectId() != null
                && owner.getProjectId().equals(projectId));
    }

    private boolean typeCompatible(String leftType, String rightType) {
        String left = normalized(leftType);
        String right = normalized(rightType);
        if (left.isEmpty() || right.isEmpty()) return false;
        if (left.equals(right)) return true;
        if (isList(left) && isList(right)) return true;
        if (Set.of("OBJECT", "MAP").contains(left) && Set.of("OBJECT", "MAP").contains(right)) return true;
        Set<String> numbers = Set.of("NUMBER", "INTEGER", "INT", "LONG", "DOUBLE", "FLOAT", "DECIMAL", "PROBABILITY");
        return numbers.contains(left) && numbers.contains(right);
    }

    private boolean isList(String type) {
        return Set.of("LIST", "ARRAY", "SET").contains(normalized(type));
    }

    private String normalized(String type) {
        return type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
    }

    private void add(List<RuleValidationIssue> issues, RuleDataObjectField field, String code, String message) {
        issues.add(RuleValidationIssue.error("DATA_OBJECT_FIELD_" + code, "$.fields[" + field.getId() + "].reference", message));
    }
}
