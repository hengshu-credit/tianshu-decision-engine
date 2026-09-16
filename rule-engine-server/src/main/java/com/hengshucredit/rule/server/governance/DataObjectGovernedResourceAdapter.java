package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDataObjectFieldOption;
import com.hengshucredit.rule.server.service.DataObjectFieldReferenceValidator;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldOptionMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataObjectGovernedResourceAdapter
        extends AggregateEntityGovernedResourceAdapter<RuleDataObject> {

    private final RuleDataObjectFieldMapper fieldMapper;
    private final RuleDataObjectFieldOptionMapper optionMapper;
    private final DataObjectFieldReferenceValidator referenceValidator;

    public DataObjectGovernedResourceAdapter(
            SimpleEntityGovernedResourceAdapter.EntityStore<RuleDataObject>
                    store,
            RuleDataObjectFieldMapper fieldMapper,
            RuleDataObjectFieldOptionMapper optionMapper,
            GovernanceSecretCodec secretCodec) {
        this(store, fieldMapper, optionMapper, secretCodec, null);
    }

    public DataObjectGovernedResourceAdapter(
            SimpleEntityGovernedResourceAdapter.EntityStore<RuleDataObject>
                    store,
            RuleDataObjectFieldMapper fieldMapper,
            RuleDataObjectFieldOptionMapper optionMapper,
            GovernanceSecretCodec secretCodec,
            RuleVariableMapper variableMapper) {
        this(store, fieldMapper, optionMapper, secretCodec, variableMapper, null);
    }

    public DataObjectGovernedResourceAdapter(
            SimpleEntityGovernedResourceAdapter.EntityStore<RuleDataObject> store,
            RuleDataObjectFieldMapper fieldMapper,
            RuleDataObjectFieldOptionMapper optionMapper,
            GovernanceSecretCodec secretCodec,
            RuleVariableMapper variableMapper,
            RuleDataObjectMapper objectMapper) {
        super(new SimpleEntityGovernedResourceAdapter<>(
                GovernanceResourceTypes.DATA_OBJECT,
                RuleDataObject.class,
                store,
                RuleDataObject::getId,
                RuleDataObject::setId,
                RuleDataObject::getStatus,
                RuleDataObject::setStatus,
                Set.of("objectCode", "objectLabel", "objectType"),
                Set.of(),
                secretCodec));
        this.fieldMapper = fieldMapper;
        this.optionMapper = optionMapper;
        this.referenceValidator = new DataObjectFieldReferenceValidator(variableMapper, objectMapper, fieldMapper);
    }

    @Override
    protected void enrichSnapshot(Long resourceId,
                                  Map<String, Object> snapshot) {
        List<RuleDataObjectField> fields = fieldMapper.selectList(
                new LambdaQueryWrapper<RuleDataObjectField>()
                        .eq(RuleDataObjectField::getObjectId, resourceId)
                        .orderByAsc(RuleDataObjectField::getSortOrder)
                        .orderByAsc(RuleDataObjectField::getId));
        List<Map<String, Object>> values = new ArrayList<>();
        for (RuleDataObjectField field : fields) {
            Map<String, Object> value = new LinkedHashMap<>(
                    CanonicalJson.readMap(JSON.toJSONString(field)));
            value.put("options", optionMapper.selectList(
                    new LambdaQueryWrapper<RuleDataObjectFieldOption>()
                            .eq(RuleDataObjectFieldOption::getFieldId,
                                    field.getId())
                            .orderByAsc(
                                    RuleDataObjectFieldOption::getSortOrder)
                            .orderByAsc(
                                    RuleDataObjectFieldOption::getId)));
            values.add(value);
        }
        snapshot.put("fields", values);
    }

    @Override
    protected boolean isOwnershipReference(
            ResourceDependencyRef dependency) {
        return isCollectionOwnershipReference(
                dependency, "fields", "objectId");
    }

    @Override
    protected void applyAggregate(Long resourceId,
                                  Map<String, Object> snapshot) {
        List<RuleDataObjectField> existing = fieldMapper.selectList(
                new LambdaQueryWrapper<RuleDataObjectField>()
                        .eq(RuleDataObjectField::getObjectId, resourceId));
        Map<Long, RuleDataObjectField> existingById = new HashMap<>();
        existing.forEach(field -> existingById.put(field.getId(), field));

        List<FieldDraft> drafts = parseDrafts(snapshot);
        Set<Long> retainedIds = new HashSet<>();
        for (FieldDraft draft : drafts) {
            if (draft.field().getId() != null
                    && existingById.containsKey(draft.field().getId())) {
                retainedIds.add(draft.field().getId());
            }
        }
        for (RuleDataObjectField removed : existing) {
            if (!retainedIds.contains(removed.getId())) {
                optionMapper.delete(new LambdaQueryWrapper<
                                RuleDataObjectFieldOption>()
                        .eq(RuleDataObjectFieldOption::getFieldId,
                                removed.getId()));
                fieldMapper.deleteById(removed.getId());
            }
        }

        Map<Long, Long> remappedIds = new HashMap<>();
        List<FieldDraft> pending = new ArrayList<>(drafts);
        int guard = pending.size() + 1;
        while (!pending.isEmpty() && guard-- > 0) {
            boolean progressed = false;
            for (int index = pending.size() - 1; index >= 0; index--) {
                FieldDraft draft = pending.get(index);
                Long parentId = draft.field().getParentFieldId();
                if (parentId != null
                        && !retainedIds.contains(parentId)
                        && !remappedIds.containsKey(parentId)) {
                    continue;
                }
                RuleDataObjectField field = draft.field();
                Long clientId = field.getId();
                field.setObjectId(resourceId);
                field.setProjectId(longValue(snapshot.get("projectId")));
                field.setScope(stringValue(snapshot.get("scope")));
                field.setStatus(field.getStatus() == null
                        ? 1 : field.getStatus());
                if (parentId != null
                        && remappedIds.containsKey(parentId)) {
                    field.setParentFieldId(remappedIds.get(parentId));
                }
                if (clientId != null
                        && existingById.containsKey(clientId)) {
                    fieldMapper.updateById(field);
                } else {
                    field.setId(null);
                    fieldMapper.insert(field);
                    if (clientId != null) {
                        remappedIds.put(clientId, field.getId());
                    }
                }
                replaceOptions(field.getId(), draft.options());
                pending.remove(index);
                progressed = true;
            }
            if (!progressed) {
                throw new IllegalArgumentException(
                        "数据对象字段存在缺失或循环的父字段引用");
            }
        }
    }

    @Override
    protected void validateAggregate(Map<String, Object> snapshot,
                                     List<GovernanceIssue> issues) {
        List<FieldDraft> fields = parseDrafts(snapshot);
        RuleDataObject owner = JSON.parseObject(CanonicalJson.write(snapshot), RuleDataObject.class);
        List<RuleDataObjectField> referenceFields = fields.stream().map(FieldDraft::field).toList();
        Set<Long> ids = new HashSet<>();
        for (FieldDraft draft : fields) {
            RuleDataObjectField field = draft.field();
            if (field.getId() != null) {
                ids.add(field.getId());
            }
            if (field.getVarCode() == null
                    || field.getVarCode().isBlank()
                    || field.getVarType() == null
                    || field.getVarType().isBlank()) {
                issues.add(GovernanceIssue.error(
                        "DATA_OBJECT_FIELD_INVALID",
                        "数据对象字段编码和类型不能为空",
                        GovernanceResourceTypes.DATA_OBJECT,
                        longValue(snapshot.get("id")),
                        "$.fields"));
            }
            referenceValidator.validate(owner, field, referenceFields).forEach(issue ->
                    issues.add(GovernanceIssue.error(issue.getCode(), issue.getMessage(),
                            GovernanceResourceTypes.DATA_OBJECT, owner.getId(), issue.getPath())));
        }
        for (FieldDraft draft : fields) {
            RuleDataObjectField field = draft.field();
            Long parentId = field.getParentFieldId();
            if (parentId != null && !ids.contains(parentId)) {
                issues.add(GovernanceIssue.error(
                        "DATA_OBJECT_PARENT_FIELD_MISSING",
                        "数据对象字段引用的父字段不在当前聚合快照中",
                        GovernanceResourceTypes.DATA_OBJECT,
                        longValue(snapshot.get("id")),
                        "$.fields[*].parentFieldId"));
            }
        }
    }

    private List<FieldDraft> parseDrafts(Map<String, Object> snapshot) {
        Object raw = snapshot.get("fields");
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        List<FieldDraft> drafts = new ArrayList<>();
        for (Object value : values) {
            Map<String, Object> map = CanonicalJson.readMap(
                    CanonicalJson.write(value));
            RuleDataObjectField field = JSON.parseObject(
                    CanonicalJson.write(map),
                    RuleDataObjectField.class);
            List<RuleDataObjectFieldOption> options = JSON.parseArray(
                    JSON.toJSONString(map.get("options")),
                    RuleDataObjectFieldOption.class);
            drafts.add(new FieldDraft(field,
                    options == null ? List.of() : options));
        }
        return drafts;
    }

    private void replaceOptions(
            Long fieldId, List<RuleDataObjectFieldOption> options) {
        optionMapper.delete(new LambdaQueryWrapper<
                        RuleDataObjectFieldOption>()
                .eq(RuleDataObjectFieldOption::getFieldId, fieldId));
        for (int index = 0; index < options.size(); index++) {
            RuleDataObjectFieldOption option = options.get(index);
            option.setId(null);
            option.setFieldId(fieldId);
            option.setSortOrder(index);
            optionMapper.insert(option);
        }
    }

    private Long longValue(Object value) {
        return value instanceof Number number
                ? number.longValue() : null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record FieldDraft(
            RuleDataObjectField field,
            List<RuleDataObjectFieldOption> options) {
    }
}
