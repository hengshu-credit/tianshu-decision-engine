package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.core.compiler.ConstantValueCodec;
import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.dto.ParsedConstant;
import com.hengshucredit.rule.model.dto.ParsedConstantGroup;
import com.hengshucredit.rule.model.dto.ParsedField;
import com.hengshucredit.rule.model.dto.ParsedObject;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.enums.GovernanceAction;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.hengshucredit.rule.server.service.parser.DdlTableParser;
import com.hengshucredit.rule.server.service.parser.JavaEntityParser;
import com.hengshucredit.rule.server.service.parser.JsonSchemaParser;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses legacy batch imports into lifecycle drafts. Effective projection
 * tables are only changed after the generated requests are approved.
 */
@Service
public class GovernanceBatchImportService {

    @Resource
    private JavaEntityParser javaEntityParser;
    @Resource
    private JsonSchemaParser jsonSchemaParser;
    @Resource
    private DdlTableParser ddlTableParser;
    @Resource
    private RuleVariableMapper variableMapper;
    @Resource
    private RuleDataObjectMapper dataObjectMapper;
    @Resource
    private GovernedResourceAdapterRegistry adapterRegistry;
    @Resource
    private GovernanceApprovalService approvalService;

    @Transactional
    public Map<String, Object> importConstantsFromJava(
            Long projectId, String scope, String javaSource, String actor) {
        ParsedConstantGroup parsed;
        try {
            parsed = javaEntityParser.parseConstants(javaSource);
        } catch (RuntimeException exception) {
            return failure(exception.getMessage());
        }
        if (parsed == null || parsed.getConstants() == null
                || parsed.getConstants().isEmpty()) {
            return failure("未能从 Java 源码中解析出任何常量，请检查源码格式");
        }
        return importConstants(projectId, scope, parsed, actor);
    }

    @Transactional
    public Map<String, Object> importConstantsFromJson(
            Long projectId, String scope, String jsonContent, String actor) {
        ParsedConstantGroup parsed;
        try {
            parsed = jsonSchemaParser.parseConstants(jsonContent);
        } catch (RuntimeException exception) {
            return failure(exception.getMessage());
        }
        if (parsed == null || parsed.getConstants() == null
                || parsed.getConstants().isEmpty()) {
            return failure("未能从 JSON 中解析出任何常量，请提供非空对象，值可为基本类型、数组或对象");
        }
        return importConstants(projectId, scope, parsed, actor);
    }

    @Transactional
    public Map<String, Object> importDataObjectsFromJava(
            Long projectId, String scope, String javaSource,
            String objectType, String actor) {
        List<ParsedObject> parsed;
        try {
            parsed = javaEntityParser.parseEntities(javaSource);
        } catch (RuntimeException exception) {
            return failure(exception.getMessage());
        }
        if (parsed == null || parsed.isEmpty()) {
            return failure("未能从 Java 源码中解析出任何类定义，请检查源码格式");
        }
        return importDataObjects(projectId, scope, objectType,
                "JAVA", javaSource, parsed, actor);
    }

    @Transactional
    public Map<String, Object> importDataObjectsFromJson(
            Long projectId, String scope, String jsonContent,
            String objectCode, String objectType, String actor) {
        ParsedObject parsed;
        try {
            parsed = jsonSchemaParser.parseObject(jsonContent, objectCode);
        } catch (RuntimeException exception) {
            return failure(exception.getMessage());
        }
        if (parsed == null || parsed.getFields() == null
                || parsed.getFields().isEmpty()) {
            return failure("未能从 JSON 中解析出任何字段，请确保 JSON 格式正确");
        }
        return importDataObjects(projectId, scope, objectType,
                "JSON", jsonContent, List.of(parsed), actor);
    }

    @Transactional
    public Map<String, Object> importDataObjectsFromDdl(
            Long projectId, String scope, String ddlSource,
            String objectType, String actor) {
        List<ParsedObject> parsed;
        try {
            parsed = ddlTableParser.parseCreateTables(ddlSource);
        } catch (RuntimeException exception) {
            return failure(exception.getMessage());
        }
        if (parsed == null || parsed.isEmpty()) {
            return failure("未能从 DDL 中解析出任何表结构，请检查 DDL 语法");
        }
        return importDataObjects(projectId, scope, objectType,
                "DDL", ddlSource, parsed, actor);
    }

    Map<String, Object> importConstants(
            Long projectId, String scope, ParsedConstantGroup parsed,
            String actor) {
        String normalizedScope = normalizeScope(scope, projectId);
        Long effectiveProjectId = effectiveProjectId(
                normalizedScope, projectId);
        List<ConstantDraft> pending = new ArrayList<>();
        Set<String> importedCodes = new LinkedHashSet<>();
        int order = 0;
        for (ParsedConstant constant : parsed.getConstants()) {
            if (constant.getConstCode() == null
                    || constant.getConstCode().isBlank()) {
                throw new IllegalArgumentException("导入常量的编码不能为空");
            }
            if (constant.getConstValue() == null) {
                throw new IllegalArgumentException(
                        "常量 [" + constant.getConstCode() + "] 缺少默认值");
            }
            RuleVariable existing = findVariable(
                    effectiveProjectId, normalizedScope,
                    constant.getConstCode());
            if (!importedCodes.add(constant.getConstCode())) {
                return failure("同一批次存在重复常量编码：" + constant.getConstCode());
            }
            if (existing != null && !"CONSTANT".equals(existing.getVarSource())) {
                return failure("常量编码 [" + constant.getConstCode() + "] 已被普通变量占用，请调整编码后导入");
            }
            Map<String, Object> snapshot = existing == null
                    ? new LinkedHashMap<>()
                    : loadEffective(GovernanceResourceTypes.VARIABLE,
                    existing.getId());
            snapshot.put("projectId", effectiveProjectId);
            snapshot.put("scope", normalizedScope);
            snapshot.put("varCode", constant.getConstCode());
            snapshot.put("varLabel", firstNotBlank(
                    constant.getConstLabel(), constant.getConstCode()));
            snapshot.put("scriptName", firstNotBlank(
                    constant.getScriptName(), constant.getConstCode()));
            snapshot.put("varType", constant.getConstType());
            snapshot.put("varSource", "CONSTANT");
            snapshot.put("defaultValue", ConstantValueCodec.normalize(
                    constant.getConstType(), constant.getConstValue()));
            snapshot.put("sortOrder", order++);
            snapshot.put("status", 1);
            snapshot.putIfAbsent("options", List.of());
            pending.add(new ConstantDraft(existing, snapshot));
        }

        List<Long> requestIds = new ArrayList<>();
        for (ConstantDraft item : pending) {
            GovernanceDraftRequest draft = draft(
                    GovernanceResourceTypes.VARIABLE,
                    item.existing() == null ? null : item.existing().getId(),
                    effectiveProjectId,
                    action(item.existing() != null,
                            item.existing() == null
                                    ? null : item.existing().getStatus()),
                    item.snapshot(),
                    "批量导入常量 "
                            + item.snapshot().get("varCode"));
            requestIds.add(createDraft(draft, actor).getId());
        }
        Map<String, Object> result = success(requestIds);
        result.put("constantCount", pending.size());
        result.put("groupCode", parsed.getGroupCode());
        return result;
    }

    Map<String, Object> importDataObjects(
            Long projectId, String scope, String objectType,
            String sourceType, String sourceContent,
            List<ParsedObject> parsedObjects, String actor) {
        String normalizedScope = normalizeScope(scope, projectId);
        Long effectiveProjectId = effectiveProjectId(
                normalizedScope, projectId);
        List<ParsedObject> objects = distinctObjects(parsedObjects);
        ReferenceResolution references = resolveObjectReferences(
                effectiveProjectId, normalizedScope, objects);
        if (!references.missing().isEmpty()
                || !references.batchPending().isEmpty()) {
            List<String> conflicts = new ArrayList<>(
                    references.missing());
            conflicts.addAll(references.batchPending());
            String message = references.batchPending().isEmpty()
                    ? "导入内容存在未生效的对象依赖："
                    + String.join("、", references.missing())
                    : "导入内容引用了同批次尚未生效的数据对象，"
                    + "请先单独导入并完成审批："
                    + String.join("、", references.batchPending());
            if (!references.missing().isEmpty()
                    && !references.batchPending().isEmpty()) {
                message += "；其他缺失依赖："
                        + String.join("、", references.missing());
            }
            Map<String, Object> result = failure(message);
            result.put("conflicts", conflicts);
            if (!references.batchPending().isEmpty()) {
                result.put("batchPendingDependencies",
                        references.batchPending());
            }
            return result;
        }

        List<ObjectDraft> pending = new ArrayList<>();
        int fieldCount = 0;
        for (ParsedObject parsed : objects) {
            if (parsed.getObjectCode() == null
                    || parsed.getObjectCode().isBlank()) {
                throw new IllegalArgumentException("导入数据对象的编码不能为空");
            }
            RuleDataObject existing = findDataObject(
                    effectiveProjectId, normalizedScope,
                    parsed.getObjectCode());
            Map<String, Object> snapshot = existing == null
                    ? new LinkedHashMap<>()
                    : loadEffective(GovernanceResourceTypes.DATA_OBJECT,
                    existing.getId());
            snapshot.put("projectId", effectiveProjectId);
            snapshot.put("scope", normalizedScope);
            snapshot.put("objectCode", parsed.getObjectCode());
            snapshot.put("objectLabel", firstNotBlank(
                    parsed.getObjectLabel(), parsed.getObjectCode()));
            snapshot.put("scriptName", firstNotBlank(
                    parsed.getScriptName(), parsed.getObjectCode()));
            snapshot.put("objectType", objectType);
            snapshot.put("sourceType", sourceType);
            snapshot.put("sourceContent", sourceContent);
            snapshot.put("status", 1);
            List<Map<String, Object>> fields = mergeFields(
                    snapshot.get("fields"), parsed.getFields(),
                    effectiveProjectId, normalizedScope);
            snapshot.put("fields", fields);
            fieldCount += parsed.getFields() == null
                    ? 0 : parsed.getFields().size();
            pending.add(new ObjectDraft(existing, snapshot));
        }

        List<Long> requestIds = new ArrayList<>();
        for (ObjectDraft item : pending) {
            GovernanceDraftRequest draft = draft(
                    GovernanceResourceTypes.DATA_OBJECT,
                    item.existing() == null ? null : item.existing().getId(),
                    effectiveProjectId,
                    action(item.existing() != null,
                            item.existing() == null
                                    ? null : item.existing().getStatus()),
                    item.snapshot(),
                    "批量导入数据对象 "
                            + item.snapshot().get("objectCode"));
            requestIds.add(createDraft(draft, actor).getId());
        }
        Map<String, Object> result = success(requestIds);
        result.put("objectCount", pending.size());
        result.put("variableCount", fieldCount);
        return result;
    }

    protected RuleVariable findVariable(
            Long projectId, String scope, String code) {
        LambdaQueryWrapper<RuleVariable> wrapper =
                new LambdaQueryWrapper<RuleVariable>()
                        .eq(RuleVariable::getScope, scope)
                        .eq(RuleVariable::getVarCode, code);
        if (projectId == null || projectId == 0L) {
            wrapper.and(value -> value
                    .isNull(RuleVariable::getProjectId)
                    .or().eq(RuleVariable::getProjectId, 0L));
        } else {
            wrapper.eq(RuleVariable::getProjectId, projectId);
        }
        return variableMapper.selectOne(wrapper.last("LIMIT 1"));
    }

    protected RuleDataObject findDataObject(
            Long projectId, String scope, String code) {
        LambdaQueryWrapper<RuleDataObject> wrapper =
                new LambdaQueryWrapper<RuleDataObject>()
                        .eq(RuleDataObject::getScope, scope)
                        .eq(RuleDataObject::getObjectCode, code);
        if (projectId == null || projectId == 0L) {
            wrapper.and(value -> value
                    .isNull(RuleDataObject::getProjectId)
                    .or().eq(RuleDataObject::getProjectId, 0L));
        } else {
            wrapper.eq(RuleDataObject::getProjectId, projectId);
        }
        return dataObjectMapper.selectOne(wrapper.last("LIMIT 1"));
    }

    protected Map<String, Object> loadEffective(
            String resourceType, Long resourceId) {
        ResourceSnapshot snapshot = adapterRegistry.require(resourceType)
                .loadEffective(resourceId);
        return new LinkedHashMap<>(
                CanonicalJson.readMap(snapshot.snapshotJson()));
    }

    protected GovernanceApprovalRequest createDraft(
            GovernanceDraftRequest draft, String actor) {
        return approvalService.createDraft(draft, actor);
    }

    private ReferenceResolution resolveObjectReferences(
            Long projectId, String scope, List<ParsedObject> objects) {
        Set<String> batchObjectCodes = new LinkedHashSet<>();
        for (ParsedObject object : objects) {
            if (object.getObjectCode() != null
                    && !object.getObjectCode().isBlank()) {
                batchObjectCodes.add(object.getObjectCode());
            }
        }
        Set<String> missing = new LinkedHashSet<>();
        Set<String> batchPending = new LinkedHashSet<>();
        for (ParsedObject object : objects) {
            if (object.getFields() == null) {
                continue;
            }
            for (ParsedField field : object.getFields()) {
                if (field.getRefObjectId() != null
                        && field.getRefObjectId() > 0) {
                    RuleDataObject target = dataObjectMapper == null
                            ? null : dataObjectMapper.selectById(
                            field.getRefObjectId());
                    if (target == null || inactive(target)) {
                        missing.add("DATA_OBJECT:"
                                + field.getRefObjectId());
                    }
                    continue;
                }
                if (field.getRefObjectCode() == null
                        || field.getRefObjectCode().isBlank()) {
                    continue;
                }
                RuleDataObject target = findActiveReferencedObject(
                        projectId, scope, field.getRefObjectCode());
                if (target == null) {
                    if (batchObjectCodes.contains(
                            field.getRefObjectCode())) {
                        batchPending.add(field.getRefObjectCode());
                    } else {
                        missing.add(field.getRefObjectCode());
                    }
                } else {
                    field.setRefObjectId(target.getId());
                }
            }
        }
        return new ReferenceResolution(
                new ArrayList<>(missing),
                new ArrayList<>(batchPending));
    }

    private RuleDataObject findActiveReferencedObject(
            Long projectId, String scope, String code) {
        RuleDataObject target = findDataObject(projectId, scope, code);
        if (target != null && !inactive(target)) {
            return target;
        }
        if (!"GLOBAL".equals(scope)) {
            target = findDataObject(null, "GLOBAL", code);
        }
        return target != null && !inactive(target) ? target : null;
    }

    private List<Map<String, Object>> mergeFields(
            Object rawExisting, List<ParsedField> parsedFields,
            Long projectId, String scope) {
        List<Map<String, Object>> fields = maps(rawExisting);
        Map<String, Map<String, Object>> existingByKey = new HashMap<>();
        long nextTemporaryId = -1L;
        for (Map<String, Object> field : fields) {
            field.remove("objectId");
            field.remove("projectId");
            field.remove("scope");
            field.remove("createTime");
            field.remove("updateTime");
            existingByKey.put(fieldKey(
                    longValue(field.get("parentFieldId")),
                    stringValue(field.get("varCode"))), field);
            Long id = longValue(field.get("id"));
            if (id != null && id < 0) {
                nextTemporaryId = Math.min(nextTemporaryId, id - 1);
            }
        }
        Map<Long, Long> parsedIds = new HashMap<>();
        List<ParsedField> incoming = parsedFields == null
                ? List.of() : parsedFields;
        int order = 0;
        for (ParsedField parsed : incoming) {
            Long parentId = parsed.getParentFieldId() == null
                    ? null : parsedIds.get(parsed.getParentFieldId());
            if (parsed.getParentFieldId() != null && parentId == null) {
                throw new IllegalArgumentException(
                        "导入字段的父子顺序无效：" + parsed.getFieldName());
            }
            String key = fieldKey(parentId, parsed.getFieldName());
            Map<String, Object> field = existingByKey.get(key);
            if (field == null) {
                field = new LinkedHashMap<>();
                field.put("id", nextTemporaryId--);
                field.put("options", List.of());
                fields.add(field);
                existingByKey.put(key, field);
            }
            Long fieldId = longValue(field.get("id"));
            if (parsed.getTempId() != null) {
                parsedIds.put(parsed.getTempId(), fieldId);
            }
            field.put("projectId", projectId == null ? 0L : projectId);
            field.put("scope", scope);
            field.put("varCode", parsed.getFieldName());
            field.put("varLabel", firstNotBlank(
                    parsed.getFieldLabel(), parsed.getFieldName()));
            field.put("scriptName", firstNotBlank(
                    parsed.getScriptName(), parsed.getFieldName()));
            field.put("varType", parsed.getVarType());
            field.put("refObjectCode", parsed.getRefObjectCode());
            field.put("refObjectId", parsed.getRefObjectId());
            field.put("genericType", parsed.getGenericType());
            field.put("parentFieldId", parentId);
            field.put("sortOrder", order++);
            field.put("status", 1);
            field.putIfAbsent("options", List.of());
        }
        return fields;
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            result.add(new LinkedHashMap<>(
                    CanonicalJson.readMap(CanonicalJson.write(item))));
        }
        return result;
    }

    private List<ParsedObject> distinctObjects(
            List<ParsedObject> parsedObjects) {
        Map<String, ParsedObject> distinct = new LinkedHashMap<>();
        if (parsedObjects != null) {
            for (ParsedObject object : parsedObjects) {
                if (object != null) {
                    if (distinct.putIfAbsent(object.getObjectCode(), object) != null) {
                        throw new IllegalArgumentException("同一批次存在重复数据对象编码：" + object.getObjectCode());
                    }
                }
            }
        }
        return new ArrayList<>(distinct.values());
    }

    private GovernanceDraftRequest draft(
            String resourceType, Long resourceId, Long projectId,
            String action, Map<String, Object> snapshot,
            String summary) {
        GovernanceDraftRequest draft = new GovernanceDraftRequest();
        draft.setResourceType(resourceType);
        draft.setResourceId(resourceId);
        draft.setProjectId(projectId);
        draft.setAction(action);
        draft.setSnapshotJson(CanonicalJson.write(snapshot));
        draft.setChangeSummary(summary);
        return draft;
    }

    private String action(boolean exists, Integer status) {
        if (!exists) {
            return GovernanceAction.CREATE.name();
        }
        return Integer.valueOf(-1).equals(status)
                ? GovernanceAction.RESTORE.name()
                : GovernanceAction.UPDATE.name();
    }

    private String normalizeScope(String scope, Long projectId) {
        String normalized = scope == null
                ? "PROJECT" : scope.trim().toUpperCase(Locale.ROOT);
        if (!"GLOBAL".equals(normalized)
                && !"PROJECT".equals(normalized)) {
            throw new IllegalArgumentException("作用域仅支持 GLOBAL 或 PROJECT");
        }
        if ("PROJECT".equals(normalized) && projectId == null) {
            throw new IllegalArgumentException("项目级导入必须选择所属项目");
        }
        return normalized;
    }

    private Long effectiveProjectId(String scope, Long projectId) {
        return "GLOBAL".equals(scope)
                ? 0L : projectId;
    }

    private boolean inactive(RuleDataObject object) {
        return object.getStatus() != null && object.getStatus() != 1;
    }

    private String fieldKey(Long parentId, String code) {
        return String.valueOf(parentId) + "\u0000" + code;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNotBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Map<String, Object> success(List<Long> requestIds) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("requestCount", requestIds.size());
        result.put("requestIds", requestIds);
        return result;
    }

    private Map<String, Object> failure(String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", error);
        return result;
    }

    private record ConstantDraft(
            RuleVariable existing, Map<String, Object> snapshot) {
    }

    private record ObjectDraft(
            RuleDataObject existing, Map<String, Object> snapshot) {
    }

    private record ReferenceResolution(
            List<String> missing,
            List<String> batchPending) {
    }
}
