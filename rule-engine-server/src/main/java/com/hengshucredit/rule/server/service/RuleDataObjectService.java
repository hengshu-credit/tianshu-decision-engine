package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hengshucredit.rule.model.dto.ParsedField;
import com.hengshucredit.rule.model.dto.ParsedObject;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDataObjectFieldOption;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldOptionMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import com.hengshucredit.rule.server.service.parser.DdlTableParser;
import com.hengshucredit.rule.server.service.parser.JavaEntityParser;
import com.hengshucredit.rule.server.service.parser.JsonSchemaParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RuleDataObjectService extends ServiceImpl<RuleDataObjectMapper, RuleDataObject> {

    /** 作用域：全局 */
    public static final String SCOPE_GLOBAL = "GLOBAL";
    /** 作用域：项目级 */
    public static final String SCOPE_PROJECT = "PROJECT";

    @Resource
    private RuleDataObjectFieldMapper objectFieldMapper;

    @Resource
    private RuleDataObjectFieldOptionMapper objectFieldOptionMapper;

    @Resource
    private RuleProjectMapper projectMapper;

    @Resource
    private ProjectFilterService projectFilterService;

    @Resource
    private JavaEntityParser javaEntityParser;

    @Resource
    private JsonSchemaParser jsonSchemaParser;

    @Resource
    private DdlTableParser ddlTableParser;

    /**
     * 将对象字段转为与前端「变量行」一致的结构，并标记 {@code objectField=true}。
     */
    public static Map<String, Object> toVariableRow(RuleDataObjectField f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("projectId", f.getProjectId());
        m.put("scope", f.getScope());
        m.put("objectId", f.getObjectId());
        m.put("varCode", f.getVarCode());
        m.put("varLabel", f.getVarLabel());
        m.put("scriptName", f.getScriptName());
        m.put("varType", f.getVarType());
        m.put("refObjectCode", f.getRefObjectCode());
        m.put("refObjectId", f.getRefObjectId());
        m.put("genericType", f.getGenericType());
        m.put("parentFieldId", f.getParentFieldId());
        m.put("varSource", "INPUT");
        m.put("sortOrder", f.getSortOrder());
        m.put("status", f.getStatus());
        m.put("objectField", Boolean.TRUE);
        m.put("refType", "DATA_OBJECT");
        return m;
    }

    @Transactional
    public Map<String, Object> importFromDdl(Long projectId, String scope, String ddlSource, String objectType) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<ParsedObject> parsed = ddlTableParser.parseCreateTables(ddlSource);
            if (parsed == null || parsed.isEmpty()) {
                result.put("success", false);
                result.put("error", "未能从 DDL 中解析出任何表结构，请检查 DDL 语法是否正确");
                return result;
            }
            int objectCount = 0;
            int varCount = 0;
            for (int i = 0; i < parsed.size(); i++) {
                ParsedObject po = parsed.get(i);
                String src = (i == 0) ? ddlSource : null;
                RuleDataObject obj = findOrCreateObject(projectId, scope, po.getObjectCode(), po.getScriptName(), objectType, "DDL", src);
                varCount += batchCreateFields(projectId, scope, obj.getId(), po, null, null);
                objectCount++;
            }
            result.put("success", true);
            result.put("objectCount", objectCount);
            result.put("variableCount", varCount);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "DDL 解析失败: " + e.getMessage());
        }
        return result;
    }

    @Transactional
    public Map<String, Object> importFromJava(Long projectId, String scope, String javaSource, String objectType) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<ParsedObject> parsed = javaEntityParser.parseEntities(javaSource);
            if (parsed == null || parsed.isEmpty()) {
                result.put("success", false);
                result.put("error", "未能从 Java 源码中解析出任何类定义，请检查源码格式是否正确");
                return result;
            }
            int objectCount = 0;
            int varCount = 0;
            for (ParsedObject po : parsed) {
                RuleDataObject obj = findOrCreateObject(projectId, scope, po.getObjectCode(), po.getScriptName(), objectType, "JAVA", javaSource);
                varCount += batchCreateFields(projectId, scope, obj.getId(), po, null, null);
                objectCount++;
                for (ParsedObject nested : po.getNestedObjects()) {
                    RuleDataObject nestedObj = findOrCreateObject(projectId, scope, nested.getObjectCode(), nested.getScriptName(), objectType, "JAVA", null);
                    nestedObj.setParentObjectId(obj.getId());
                    updateById(nestedObj);
                    varCount += batchCreateFields(projectId, scope, nestedObj.getId(), nested, null, null);
                    objectCount++;
                }
            }
            result.put("success", true);
            result.put("objectCount", objectCount);
            result.put("variableCount", varCount);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Java 源码解析失败: " + e.getMessage());
        }
        return result;
    }

    @Transactional
    public Map<String, Object> importFromJson(Long projectId, String scope, String jsonContent, String objectCode, String objectType) {
        Map<String, Object> result = new HashMap<>();
        try {
            ParsedObject parsed = jsonSchemaParser.parseObject(jsonContent, objectCode);
            if (parsed == null || parsed.getFields() == null || parsed.getFields().isEmpty()) {
                result.put("success", false);
                result.put("error", "未能从 JSON 中解析出任何字段，请确保 JSON 格式正确");
                return result;
            }
            RuleDataObject obj = findOrCreateObject(projectId, scope, objectCode, parsed.getScriptName(), objectType, "JSON", jsonContent);
            // JSON 导入：先清除该对象下所有旧字段，再按层级重建
            deleteAllFieldsByObjectId(obj.getId());
            int varCount = batchCreateFieldsRecursive(projectId, scope, obj.getId(), parsed.getFields(), null);

            result.put("success", true);
            result.put("objectCount", 1);
            result.put("variableCount", varCount);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "JSON 解析失败: " + e.getMessage());
        }
        return result;
    }

    public List<RuleDataObject> listByProject(Long projectId) {
        LambdaQueryWrapper<RuleDataObject> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null && projectId > 0) {
            // 同时查询全局数据对象和指定项目的数据对象
            wrapper.and(w -> w
                    .eq(RuleDataObject::getScope, SCOPE_GLOBAL)
                    .or()
                    .eq(RuleDataObject::getScope, SCOPE_PROJECT)
                    .eq(RuleDataObject::getProjectId, projectId)
            );
        } else {
            // 只查询全局数据对象
            wrapper.eq(RuleDataObject::getScope, SCOPE_GLOBAL);
        }
        wrapper.orderByDesc(RuleDataObject::getUpdateTime)
                .orderByDesc(RuleDataObject::getId);
        return list(wrapper);
    }

    /**
     * 仅查询指定项目的数据对象（不包含全局）
     */
    public List<RuleDataObject> listByProjectOnly(Long projectId) {
        LambdaQueryWrapper<RuleDataObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleDataObject::getProjectId, projectId)
               .eq(RuleDataObject::getScope, SCOPE_PROJECT)
               .orderByDesc(RuleDataObject::getUpdateTime)
               .orderByDesc(RuleDataObject::getId);
        return list(wrapper);
    }

    /**
     * 仅查询全局数据对象
     */
    public List<RuleDataObject> listGlobalOnly() {
        LambdaQueryWrapper<RuleDataObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleDataObject::getScope, SCOPE_GLOBAL)
               .orderByDesc(RuleDataObject::getUpdateTime)
               .orderByDesc(RuleDataObject::getId);
        return list(wrapper);
    }

    public IPage<RuleDataObject> pageList(int pageNum, int pageSize, String scope, Long projectId,
                                          String projectCode, String projectName, String sourceType,
                                          String objectCode) {
        LambdaQueryWrapper<RuleDataObject> wrapper = buildObjectQuery(scope, projectId, projectCode, projectName, sourceType, objectCode);
        wrapper.orderByDesc(RuleDataObject::getUpdateTime)
                .orderByDesc(RuleDataObject::getId);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    public Map<String, Object> getObjectWithVariables(Long objectId) {
        RuleDataObject obj = getById(objectId);
        if (obj == null) return null;
        List<RuleDataObjectField> fields = objectFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDataObjectField>()
                        .eq(RuleDataObjectField::getObjectId, objectId)
                        .orderByAsc(RuleDataObjectField::getSortOrder));
        List<Map<String, Object>> rows = fields.stream().map(RuleDataObjectService::toVariableRow).collect(Collectors.toList());

        List<RuleDataObject> children = list(new LambdaQueryWrapper<RuleDataObject>()
                .eq(RuleDataObject::getParentObjectId, objectId));

        Map<String, Object> result = new HashMap<>();
        result.put("object", obj);
        result.put("variables", rows);
        result.put("children", children);
        return result;
    }

    @Override
    public boolean save(RuleDataObject entity) {
        // 确保 scope 有默认值
        if (entity.getScope() == null || entity.getScope().isEmpty()) {
            entity.setScope(SCOPE_PROJECT);
        }
        // upsert：先查是否存在，存在则更新，不存在则插入
        Long projectId = entity.getProjectId();
        // 归一化 projectId：GLOBAL 场景统一为 0
        Long normProjectId = (projectId == null || "GLOBAL".equals(entity.getScope())) ? 0L : projectId;
        entity.setProjectId(normProjectId);

        LambdaQueryWrapper<RuleDataObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleDataObject::getScope, entity.getScope())
               .and(w -> {
                   if (normProjectId != null && normProjectId != 0L) {
                       w.eq(RuleDataObject::getProjectId, normProjectId);
                   } else {
                       w.and(inner -> inner.eq(RuleDataObject::getProjectId, 0L).or().isNull(RuleDataObject::getProjectId));
                   }
               })
               .eq(RuleDataObject::getObjectCode, entity.getObjectCode());
        RuleDataObject existing = getBaseMapper().selectOne(wrapper);
        if (existing != null) {
            entity.setId(existing.getId());
            return super.updateById(entity);
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(RuleDataObject entity) {
        if (entity != null && entity.getId() != null && entity.getScope() != null) {
            RuleDataObject existing = getById(entity.getId());
            if (existing != null && !Objects.equals(existing.getScope(), entity.getScope())) {
                if (SCOPE_PROJECT.equals(existing.getScope()) && SCOPE_GLOBAL.equals(entity.getScope())) {
                    throw new IllegalArgumentException("请使用“转为全局”操作转换数据对象");
                }
                throw new IllegalArgumentException("数据对象作用范围不支持直接修改");
            }
        }
        return super.updateById(entity);
    }

    /**
     * 将项目级数据对象及其所属字段转为全局，保留对象和字段的原始编码。
     */
    @Transactional
    public void toGlobal(Long objectId) {
        RuleDataObject object = getById(objectId);
        if (object == null) {
            throw new IllegalArgumentException("数据对象不存在");
        }
        if (SCOPE_GLOBAL.equals(object.getScope())) {
            throw new IllegalArgumentException("该数据对象已是全局数据对象，无需转换");
        }
        long conflicts = count(new LambdaQueryWrapper<RuleDataObject>()
                .eq(RuleDataObject::getScope, SCOPE_GLOBAL)
                .eq(RuleDataObject::getObjectCode, object.getObjectCode())
                .ne(RuleDataObject::getId, objectId));
        if (conflicts > 0) {
            throw new IllegalArgumentException("数据对象编码「" + object.getObjectCode() + "」已被其他全局数据对象使用");
        }

        List<RuleDataObjectField> fields = objectFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDataObjectField>()
                        .eq(RuleDataObjectField::getObjectId, objectId));
        validateGlobalDependencies(object, fields);

        LocalDateTime now = LocalDateTime.now();
        int updated = getBaseMapper().update(null, new LambdaUpdateWrapper<RuleDataObject>()
                .eq(RuleDataObject::getId, objectId)
                .eq(RuleDataObject::getScope, SCOPE_PROJECT)
                .set(RuleDataObject::getScope, SCOPE_GLOBAL)
                .set(RuleDataObject::getProjectId, 0L)
                .set(RuleDataObject::getUpdateTime, now));
        if (updated != 1) {
            throw new IllegalArgumentException("数据对象状态已变化，请刷新后重试");
        }
        objectFieldMapper.update(null, new LambdaUpdateWrapper<RuleDataObjectField>()
                .eq(RuleDataObjectField::getObjectId, objectId)
                .set(RuleDataObjectField::getScope, SCOPE_GLOBAL)
                .set(RuleDataObjectField::getProjectId, 0L)
                .set(RuleDataObjectField::getUpdateTime, now));
    }

    private void validateGlobalDependencies(RuleDataObject object, List<RuleDataObjectField> fields) {
        Set<String> errors = new LinkedHashSet<>();
        validateReferencedObject(errors, "父对象", object.getParentObjectId(), object.getId());
        for (RuleDataObjectField field : fields) {
            String fieldName = field.getVarLabel() != null && !field.getVarLabel().isEmpty()
                    ? field.getVarLabel() : field.getVarCode();
            if (field.getRefObjectId() != null) {
                validateReferencedObject(errors, "字段「" + fieldName + "」", field.getRefObjectId(), object.getId());
            } else if (field.getRefObjectCode() != null && !field.getRefObjectCode().isEmpty()) {
                errors.add("字段「" + fieldName + "」引用对象未关联资源 ID");
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("数据对象转全局失败：" + String.join("；", errors));
        }
    }

    private void validateReferencedObject(Set<String> errors, String section, Long referencedId, Long convertingId) {
        if (referencedId == null || referencedId.equals(convertingId)) return;
        RuleDataObject referenced = getById(referencedId);
        if (referenced == null || !SCOPE_GLOBAL.equals(referenced.getScope())) {
            errors.add(section + "引用的对象不是全局资源：DATA_OBJECT:" + referencedId);
        }
    }

    @Transactional
    public void deleteWithVariables(Long objectId) {
        List<RuleDataObjectField> fields = objectFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDataObjectField>().eq(RuleDataObjectField::getObjectId, objectId));
        for (RuleDataObjectField f : fields) {
            objectFieldOptionMapper.delete(new LambdaQueryWrapper<RuleDataObjectFieldOption>()
                    .eq(RuleDataObjectFieldOption::getFieldId, f.getId()));
        }
        objectFieldMapper.delete(new LambdaQueryWrapper<RuleDataObjectField>().eq(RuleDataObjectField::getObjectId, objectId));

        List<RuleDataObject> children = list(new LambdaQueryWrapper<RuleDataObject>()
                .eq(RuleDataObject::getParentObjectId, objectId));
        for (RuleDataObject child : children) {
            deleteWithVariables(child.getId());
        }
        removeById(objectId);
    }

    public void updateObjectType(Long id, String objectType) {
        RuleDataObject obj = getById(id);
        if (obj != null) {
            obj.setObjectType(objectType);
            updateById(obj);
        }
    }

    /** 更新数据对象的脚本引用名 */
    public void updateScriptName(Long id, String scriptName) {
        RuleDataObject obj = getById(id);
        if (obj != null) {
            obj.setScriptName(scriptName);
            updateById(obj);
        }
    }

    /**
     * 获取指定项目的变量树（包含全局和项目级）。返回结构包含变量树和对象 ID→编码映射。
     * 铁律四：objectIdMap 供前端通过 refObjectId 展示引用对象名。
     */
    @Transactional
    public Map<String, Object> getVariableTree(Long projectId) {
        List<RuleDataObject> objects = listByProject(projectId);
        materializeJsonFieldsIfMissing(objects);
        List<RuleDataObjectField> allFields = collectFieldsByProject(projectId);
        Map<String, Object> result = new HashMap<>();
        result.put("tree", buildVariableTree(objects, allFields));
        result.put("objectIdMap", buildObjectIdMap(objects));
        return result;
    }

    /**
     * 获取所有项目的数据对象树（未选项目时使用，显示所有项目的数据对象）
     */
    @Transactional
    public Map<String, Object> getVariableTreeAll() {
        return getVariableTreeAll(null, null, null, null, null, null);
    }

    @Transactional
    public Map<String, Object> getVariableTreeAll(String scope, Long projectId, String projectCode,
                                                  String projectName, String sourceType, String objectCode) {
        List<RuleDataObject> objects = getBaseMapper().selectList(
                buildObjectQuery(scope, projectId, projectCode, projectName, sourceType, objectCode)
                        .orderByDesc(RuleDataObject::getUpdateTime)
                        .orderByDesc(RuleDataObject::getId));
        materializeJsonFieldsIfMissing(objects);
        List<Long> objectIds = objects.stream()
                .map(RuleDataObject::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<RuleDataObjectField> allFields = objectIds.isEmpty()
                ? Collections.emptyList()
                : objectFieldMapper.selectList(
                        new LambdaQueryWrapper<RuleDataObjectField>()
                                .in(RuleDataObjectField::getObjectId, objectIds)
                                .orderByAsc(RuleDataObjectField::getProjectId)
                                .orderByAsc(RuleDataObjectField::getSortOrder));
        Map<String, Object> result = new HashMap<>();
        result.put("tree", buildVariableTree(objects, allFields));
        result.put("objectIdMap", buildObjectIdMap(objects));
        return result;
    }

    private LambdaQueryWrapper<RuleDataObject> buildObjectQuery(String scope, Long projectId,
                                                               String projectCode, String projectName,
                                                               String sourceType, String objectCode) {
        ProjectFilterService.ProjectMatches projectMatches = null;
        if ((projectCode != null && !projectCode.isEmpty())
                || (projectName != null && !projectName.isEmpty())) {
            projectMatches = projectFilterService.resolve(projectCode, projectName);
        }
        LambdaQueryWrapper<RuleDataObject> wrapper = new LambdaQueryWrapper<>();
        if (scope != null && !scope.isEmpty()) {
            wrapper.eq(RuleDataObject::getScope, scope);
        }
        if (projectId != null && projectId > 0) {
            if (scope == null || scope.isEmpty()) {
                wrapper.and(w -> w.eq(RuleDataObject::getScope, SCOPE_GLOBAL)
                        .or()
                        .eq(RuleDataObject::getScope, SCOPE_PROJECT)
                        .eq(RuleDataObject::getProjectId, projectId));
            } else if (SCOPE_PROJECT.equals(scope)) {
                wrapper.eq(RuleDataObject::getProjectId, projectId);
            }
        }
        if (projectMatches != null) {
            if (projectMatches.isEmpty()) {
                wrapper.eq(RuleDataObject::getId, -1L);
            } else {
                wrapper.eq(RuleDataObject::getScope, SCOPE_PROJECT)
                        .in(RuleDataObject::getProjectId, projectMatches.getProjectIds());
            }
        }
        if (sourceType != null && !sourceType.isEmpty()) {
            wrapper.eq(RuleDataObject::getSourceType, sourceType);
        }
        if (objectCode != null && !objectCode.isEmpty()) {
            wrapper.like(RuleDataObject::getObjectCode, objectCode);
        }
        return wrapper;
    }

    /**
     * Older seed data may contain a JSON data object with sourceContent but no
     * rows in rule_data_object_field. Lazily materialize those fields so picker
     * APIs expose selectable object properties and callers can persist varId.
     */
    private void materializeJsonFieldsIfMissing(List<RuleDataObject> objects) {
        if (objects == null || objects.isEmpty()) return;
        for (RuleDataObject obj : objects) {
            if (obj == null || obj.getId() == null) continue;
            String sourceType = obj.getSourceType();
            String sourceContent = obj.getSourceContent();
            if (!"JSON".equalsIgnoreCase(sourceType) || sourceContent == null || sourceContent.trim().isEmpty()) {
                continue;
            }
            Long count = objectFieldMapper.selectCount(
                    new LambdaQueryWrapper<RuleDataObjectField>()
                            .eq(RuleDataObjectField::getObjectId, obj.getId()));
            if (count != null && count > 0) continue;
            try {
                ParsedObject parsed = jsonSchemaParser.parseObject(sourceContent, obj.getObjectCode());
                if (parsed != null && parsed.getFields() != null && !parsed.getFields().isEmpty()) {
                    batchCreateFieldsRecursive(obj.getProjectId(), obj.getScope(), obj.getId(), parsed.getFields(), null);
                }
            } catch (Exception ignored) {
                // Keep the tree endpoint tolerant; invalid sourceContent should not break the page.
            }
        }
    }

    /**
     * 铁律四：构建 id → objectCode 映射，供前端展示 refObjectId 对应的对象名。
     */
    private Map<Long, String> buildObjectIdMap(List<RuleDataObject> objects) {
        return objects.stream().collect(Collectors.toMap(RuleDataObject::getId, RuleDataObject::getObjectCode));
    }

    /**
     * 收集指定项目的字段（含全局和项目级）。
     */
    private List<RuleDataObjectField> collectFieldsByProject(Long projectId) {
        List<RuleDataObjectField> allFields = new ArrayList<>();
        if (projectId != null && projectId > 0) {
            List<RuleDataObjectField> projectFields = objectFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDataObjectField>()
                            .eq(RuleDataObjectField::getScope, SCOPE_PROJECT)
                            .eq(RuleDataObjectField::getProjectId, projectId)
                            .orderByAsc(RuleDataObjectField::getSortOrder));
            List<RuleDataObjectField> globalFields = objectFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDataObjectField>()
                            .eq(RuleDataObjectField::getScope, SCOPE_GLOBAL)
                            .orderByAsc(RuleDataObjectField::getSortOrder));
            allFields.addAll(projectFields);
            allFields.addAll(globalFields);
        } else {
            allFields = objectFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDataObjectField>()
                            .eq(RuleDataObjectField::getScope, SCOPE_GLOBAL)
                            .orderByAsc(RuleDataObjectField::getSortOrder));
        }
        return allFields;
    }

    /**
     * 将对象列表和字段列表构建为变量树。
     * 每个对象节点包含 object 元信息、可逐级展开的 variables，以及供选择器兼容使用的 flatVariables。
     * 字段的 varCode/varLabel 保留叶子名称，scriptName 规范为完整脚本路径（如 "User.age"）。
     */
    private List<Map<String, Object>> buildVariableTree(List<RuleDataObject> objects, List<RuleDataObjectField> allFields) {
        Map<Long, List<RuleDataObjectField>> byObject = allFields.stream()
                .collect(Collectors.groupingBy(RuleDataObjectField::getObjectId));

        List<Map<String, Object>> tree = new ArrayList<>();
        for (RuleDataObject obj : objects) {
            Map<String, Object> node = new HashMap<>();
            node.put("object", obj);
            String objScriptName = obj.getScriptName();
            List<Map<String, Object>> vars = buildNestedVariableRows(byObject.getOrDefault(obj.getId(), Collections.emptyList()), objScriptName);
            node.put("variables", vars);
            node.put("flatVariables", flattenRows(vars));
            tree.add(node);
        }
        return tree;
    }

    /**
     * 按 parentFieldId 组装对象字段树。
     */
    private static List<Map<String, Object>> buildNestedVariableRows(List<RuleDataObjectField> fields, String objScriptName) {
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }
        List<RuleDataObjectField> sorted = new ArrayList<>(fields);
        sorted.sort(Comparator
                .comparing((RuleDataObjectField f) -> f.getSortOrder() == null ? 0 : f.getSortOrder())
                .thenComparing(f -> f.getId() == null ? 0L : f.getId()));

        Map<Long, Map<String, Object>> rowById = new LinkedHashMap<>();
        Map<Long, List<Map<String, Object>>> childrenByParent = new HashMap<>();
        for (RuleDataObjectField field : sorted) {
            Map<String, Object> row = toVariableRow(field);
            normalizeObjectFieldScriptName(row, objScriptName);
            normalizeObjectFieldDisplay(row, objScriptName);
            rowById.put(field.getId(), row);
            if (field.getParentFieldId() != null) {
                childrenByParent.computeIfAbsent(field.getParentFieldId(), k -> new ArrayList<>()).add(row);
            }
        }

        List<Map<String, Object>> roots = new ArrayList<>();
        for (RuleDataObjectField field : sorted) {
            Map<String, Object> row = rowById.get(field.getId());
            List<Map<String, Object>> children = childrenByParent.get(field.getId());
            if (children != null && !children.isEmpty()) {
                row.put("children", children);
            }
            Long parentId = field.getParentFieldId();
            if (parentId == null || !rowById.containsKey(parentId)) {
                roots.add(row);
            }
        }
        return roots;
    }

    private static void normalizeObjectFieldScriptName(Map<String, Object> row, String objScriptName) {
        if (objScriptName == null || objScriptName.trim().isEmpty()) return;
        String fieldScriptName = (String) row.get("scriptName");
        if (fieldScriptName == null || fieldScriptName.trim().isEmpty()) {
            fieldScriptName = (String) row.get("varCode");
        }
        fieldScriptName = collapseDuplicateObjectPrefix(fieldScriptName, objScriptName);
        if (fieldScriptName != null && !fieldScriptName.trim().isEmpty()
                && !fieldScriptName.equals(objScriptName)
                && !fieldScriptName.startsWith(objScriptName + ".")) {
            row.put("scriptName", objScriptName + "." + fieldScriptName);
        } else if (fieldScriptName != null && !fieldScriptName.trim().isEmpty()) {
            row.put("scriptName", fieldScriptName);
        }
    }

    private static void normalizeObjectFieldDisplay(Map<String, Object> row, String objScriptName) {
        String scriptName = (String) row.get("scriptName");
        String path = stripObjectPrefix(scriptName, objScriptName);
        String displayCode = leafName(path);
        if (displayCode == null || displayCode.isEmpty()) {
            displayCode = leafName((String) row.get("varCode"));
        }
        if (displayCode == null || displayCode.isEmpty()) return;

        String oldCode = (String) row.get("varCode");
        row.put("varCode", displayCode);

        String label = (String) row.get("varLabel");
        if (label == null || label.trim().isEmpty()
                || label.equals(oldCode)
                || label.indexOf('.') >= 0) {
            row.put("varLabel", displayCode);
        }
    }

    private static String collapseDuplicateObjectPrefix(String value, String objScriptName) {
        if (value == null || objScriptName == null || objScriptName.trim().isEmpty()) return value;
        String prefix = objScriptName + ".";
        String duplicatePrefix = objScriptName + "." + objScriptName + ".";
        String result = value;
        while (result.startsWith(duplicatePrefix)) {
            result = prefix + result.substring(duplicatePrefix.length());
        }
        return result;
    }

    private static String stripObjectPrefix(String value, String objScriptName) {
        if (value == null) return null;
        if (objScriptName == null || objScriptName.trim().isEmpty()) return value;
        String prefix = objScriptName + ".";
        String result = value;
        while (result.startsWith(prefix)) {
            result = result.substring(prefix.length());
        }
        return result;
    }

    private static String leafName(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (text.isEmpty()) return text;
        int idx = text.lastIndexOf('.');
        return idx >= 0 ? text.substring(idx + 1) : text;
    }

    private static List<Map<String, Object>> flattenRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> flat = new ArrayList<>();
        flattenRows(rows, flat);
        return flat;
    }

    @SuppressWarnings("unchecked")
    private static void flattenRows(List<Map<String, Object>> rows, List<Map<String, Object>> flat) {
        if (rows == null) return;
        for (Map<String, Object> row : rows) {
            flat.add(row);
            Object children = row.get("children");
            if (children instanceof List) {
                flattenRows((List<Map<String, Object>>) children, flat);
            }
        }
    }

    @Transactional
    public RuleDataObjectField createObjectField(Long objectId, RuleDataObjectField field) {
        RuleDataObject obj = getById(objectId);
        if (obj == null) throw new IllegalArgumentException("数据对象不存在");
        field.setId(null);
        field.setProjectId(obj.getProjectId());
        field.setScope(obj.getScope());
        field.setObjectId(objectId);
        if (field.getScriptName() == null || field.getScriptName().isEmpty()) {
            field.setScriptName(field.getVarCode());
        }
        field.setStatus(1);
        if (field.getSortOrder() == null) {
            List<RuleDataObjectField> tail = objectFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDataObjectField>()
                            .eq(RuleDataObjectField::getObjectId, objectId)
                            .orderByDesc(RuleDataObjectField::getSortOrder)
                            .last("LIMIT 1"));
            int next = tail.isEmpty() ? 0 : tail.get(0).getSortOrder() + 1;
            field.setSortOrder(next);
        }
        objectFieldMapper.insert(field);
        return field;
    }

    @Transactional
    public void updateObjectField(RuleDataObjectField field) {
        objectFieldMapper.updateById(field);
    }

    @Transactional
    public void deleteObjectField(Long fieldId) {
        List<RuleDataObjectField> children = objectFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDataObjectField>().eq(RuleDataObjectField::getParentFieldId, fieldId));
        for (RuleDataObjectField child : children) {
            deleteObjectField(child.getId());
        }
        objectFieldOptionMapper.delete(new LambdaQueryWrapper<RuleDataObjectFieldOption>()
                .eq(RuleDataObjectFieldOption::getFieldId, fieldId));
        objectFieldMapper.deleteById(fieldId);
    }

    /**
     * 删除指定对象下的所有字段（不含子对象字段，仅删除 objectId 对应的字段）。
     * JSON 导入时用于先清空旧字段再重建，避免同名字段 + parentFieldId 组合冲突。
     */
    private void deleteAllFieldsByObjectId(Long objectId) {
        // 先删枚举选项
        List<RuleDataObjectField> fields = objectFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDataObjectField>().eq(RuleDataObjectField::getObjectId, objectId));
        for (RuleDataObjectField f : fields) {
            objectFieldOptionMapper.delete(new LambdaQueryWrapper<RuleDataObjectFieldOption>()
                    .eq(RuleDataObjectFieldOption::getFieldId, f.getId()));
        }
        objectFieldMapper.delete(new LambdaQueryWrapper<RuleDataObjectField>()
                .eq(RuleDataObjectField::getObjectId, objectId));
    }

    public List<RuleDataObjectFieldOption> getFieldOptions(Long fieldId) {
        return objectFieldOptionMapper.selectList(
                new LambdaQueryWrapper<RuleDataObjectFieldOption>()
                        .eq(RuleDataObjectFieldOption::getFieldId, fieldId)
                        .orderByAsc(RuleDataObjectFieldOption::getSortOrder));
    }

    @Transactional
    public void saveFieldOptions(Long fieldId, List<RuleDataObjectFieldOption> options) {
        objectFieldOptionMapper.delete(new LambdaQueryWrapper<RuleDataObjectFieldOption>()
                .eq(RuleDataObjectFieldOption::getFieldId, fieldId));
        if (options != null) {
            for (int i = 0; i < options.size(); i++) {
                RuleDataObjectFieldOption opt = options.get(i);
                opt.setId(null);
                opt.setFieldId(fieldId);
                opt.setSortOrder(i);
                objectFieldOptionMapper.insert(opt);
            }
        }
    }

    private RuleDataObject findOrCreateObject(Long projectId, String scope, String objectCode, String scriptName, String objectType, String sourceType, String sourceContent) {
        // GLOBAL 场景下 projectId 可能传 null，但数据库中存的是 0，需要兼容处理
        LambdaQueryWrapper<RuleDataObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleDataObject::getScope, scope)
               .and(w -> {
                   if (projectId != null && projectId != 0L) {
                       w.eq(RuleDataObject::getProjectId, projectId);
                   } else {
                       // null 和 0 都视为全局
                       w.and(inner -> inner.eq(RuleDataObject::getProjectId, 0L).or().isNull(RuleDataObject::getProjectId));
                   }
               })
               .eq(RuleDataObject::getObjectCode, objectCode);
        RuleDataObject existing = getOne(wrapper, false);
        if (existing != null) {
            existing.setObjectType(objectType);
            existing.setSourceType(sourceType);
            if (sourceContent != null) existing.setSourceContent(sourceContent);
            if (scriptName != null && existing.getScriptName() == null) existing.setScriptName(scriptName);
            updateById(existing);
            return existing;
        }
        RuleDataObject obj = new RuleDataObject();
        obj.setProjectId(projectId);
        obj.setScope(scope);
        obj.setObjectCode(objectCode);
        obj.setObjectLabel(objectCode);
        obj.setScriptName(scriptName != null ? scriptName : objectCode);
        obj.setObjectType(objectType);
        obj.setSourceType(sourceType);
        obj.setSourceContent(sourceContent);
        obj.setStatus(1);
        save(obj);
        return obj;
    }

        /**
     * 按 ParsedObject.fields 批量创建/更新字段（用于 Java/DDL 导入，parentFieldId 均为 null）。
     */
    private int batchCreateFields(Long projectId, String scope, Long objectId, ParsedObject parsed, Long parentFieldId, Map<String, Long> objectCodeToId) {
        int count = 0;
        int order = 0;
        Long normProjectId = (projectId == null || projectId == 0L) ? 0L : projectId;
        for (ParsedField field : parsed.getFields()) {
            String varCode = field.getFieldName();
            LambdaQueryWrapper<RuleDataObjectField> existWrapper = new LambdaQueryWrapper<>();
            existWrapper.eq(RuleDataObjectField::getObjectId, objectId)
                        .eq(RuleDataObjectField::getVarCode, varCode);
            if (parentFieldId == null) {
                existWrapper.isNull(RuleDataObjectField::getParentFieldId);
            } else {
                existWrapper.eq(RuleDataObjectField::getParentFieldId, parentFieldId);
            }
            RuleDataObjectField existing = objectFieldMapper.selectOne(existWrapper);

            Long resolvedRefObjectId = resolveRefObjectId(field.getRefObjectCode(), field.getRefObjectId(), scope, normProjectId, objectCodeToId);

            if (existing != null) {
                existing.setVarType(field.getVarType());
                existing.setParentFieldId(parentFieldId);
                existing.setRefObjectCode(field.getRefObjectCode());
                existing.setRefObjectId(resolvedRefObjectId);
                if (field.getFieldLabel() != null && !field.getFieldLabel().isEmpty()) {
                    existing.setVarLabel(field.getFieldLabel());
                }
                if (field.getScriptName() != null && existing.getScriptName() == null) {
                    existing.setScriptName(field.getScriptName());
                }
                objectFieldMapper.updateById(existing);
            } else {
                RuleDataObjectField f = new RuleDataObjectField();
                f.setProjectId(normProjectId);
                f.setScope(scope);
                f.setObjectId(objectId);
                f.setVarCode(varCode);
                f.setVarLabel(field.getFieldLabel() != null ? field.getFieldLabel() : varCode);
                f.setScriptName(field.getScriptName() != null ? field.getScriptName() : varCode);
                f.setVarType(field.getVarType());
                f.setRefObjectCode(field.getRefObjectCode());
                f.setRefObjectId(resolvedRefObjectId);
                f.setParentFieldId(parentFieldId);
                f.setSortOrder(order);
                f.setStatus(1);
                objectFieldMapper.insert(f);
            }
            count++;
            order++;
        }
        return count;
    }

    /**
     * 按层级递归写入字段列表。
     * 通过 ParsedField.parentFieldId 确定父子关系——当遇到 OBJECT/LIST 字段时，
     * 先将其插入数据库获取真实 ID，再递归处理其子字段。
     *
     * @param projectId   项目 ID
     * @param scope      作用域
     * @param objectId    所属数据对象 ID
     * @param fields      待写入的字段列表（来自 ParsedObject.fields）
     * @param parentTempId 父字段解析期临时 ID（顶层为 null）
     * @return 写入的字段总数（含所有层级）
     */
    private int batchCreateFieldsRecursive(Long projectId, String scope, Long objectId,
                                           List<ParsedField> fields, Long parentTempId) {
        return batchCreateFieldsRecursive(projectId, scope, objectId, fields, parentTempId, null);
    }

    private int batchCreateFieldsRecursive(Long projectId, String scope, Long objectId,
                                           List<ParsedField> fields, Long parentTempId, Long parentDbId) {
        Long normProjectId = (projectId == null || projectId == 0L) ? 0L : projectId;
        int order = 0;
        int count = 0;

        for (ParsedField field : fields) {
            // 只处理当前层级的字段（parentFieldId 匹配）
            if (!java.util.Objects.equals(field.getParentFieldId(), parentTempId)) {
                continue;
            }

            RuleDataObjectField f = new RuleDataObjectField();
            f.setProjectId(normProjectId);
            f.setScope(scope);
            f.setObjectId(objectId);
            f.setVarCode(field.getFieldName());
            f.setVarLabel(field.getFieldLabel() != null && !field.getFieldLabel().isEmpty() ? field.getFieldLabel() : field.getFieldName());
            f.setScriptName(field.getScriptName() != null ? field.getScriptName() : field.getFieldName());
            f.setVarType(field.getVarType());
            f.setRefObjectCode(field.getRefObjectCode());
            f.setRefObjectId(field.getRefObjectId());
            f.setGenericType(field.getGenericType());
            f.setParentFieldId(parentDbId);
            f.setSortOrder(order);
            f.setStatus(1);
            objectFieldMapper.insert(f);

            // 若是 OBJECT/LIST 类型，先写入当前字段，再递归写入子字段
            if ("OBJECT".equals(field.getVarType()) || "LIST".equals(field.getVarType())) {
                count += batchCreateFieldsRecursive(projectId, scope, objectId, fields, field.getTempId(), f.getId());
            }

            order++;
            count++;
        }
        return count;
    }

    /**
     * 铁律四：解析字段的引用对象 ID。
     * 优先级：refObjectId（已设置） > refObjectCode（需查询/创建目标对象）
     *
     * @param refObjectCode  字段中记录的引用对象编码
     * @param refObjectId    字段中记录的引用对象 ID（可能已由解析器设置）
     * @param scope          作用域
     * @param normProjectId  归一化的项目 ID
     * @param objectCodeToId 可选的缓存映射（避免重复查询）
     * @return 解析后的 refObjectId，找不到时返回 null
     */
    private Long resolveRefObjectId(String refObjectCode, Long refObjectId, String scope, Long normProjectId, Map<String, Long> objectCodeToId) {
        // 已有 ID 直接返回
        if (refObjectId != null && refObjectId > 0) {
            return refObjectId;
        }
        if (refObjectCode == null || refObjectCode.isEmpty()) {
            return null;
        }
        // 尝试从缓存获取
        if (objectCodeToId != null && objectCodeToId.containsKey(refObjectCode)) {
            return objectCodeToId.get(refObjectCode);
        }
        // 查询目标对象
        LambdaQueryWrapper<RuleDataObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleDataObject::getScope, scope)
               .eq(RuleDataObject::getProjectId, normProjectId)
               .eq(RuleDataObject::getObjectCode, refObjectCode);
        RuleDataObject target = getOne(wrapper, false);
        if (target != null) {
            if (objectCodeToId != null) {
                objectCodeToId.put(refObjectCode, target.getId());
            }
            return target.getId();
        }
        return null;
    }

    /**
     * 铁律四：批量将字段的 refObjectCode 解析为 refObjectId。
     * 对于引用了不存在的对象编码，先创建目标数据对象，再更新字段的 refObjectId。
     */
    private void resolveRefObjectIds(Long objectId, String scope, Long projectId) {
        Long normProjectId = (projectId == null || projectId == 0L) ? 0L : projectId;

        // 查询所有 varType 为 OBJECT/LIST 且有 refObjectCode 但无 refObjectId 的字段
        LambdaQueryWrapper<RuleDataObjectField> fieldWrapper = new LambdaQueryWrapper<>();
        fieldWrapper.eq(RuleDataObjectField::getObjectId, objectId)
                    .in(RuleDataObjectField::getVarType, "OBJECT", "LIST")
                    .isNotNull(RuleDataObjectField::getRefObjectCode)
                    .isNull(RuleDataObjectField::getRefObjectId);
        List<RuleDataObjectField> fieldsToResolve = objectFieldMapper.selectList(fieldWrapper);
        if (fieldsToResolve.isEmpty()) {
            return;
        }

        // 收集所有需要的目标对象编码
        Map<String, Long> objectCodeToId = new HashMap<>();
        for (RuleDataObjectField f : fieldsToResolve) {
            String code = f.getRefObjectCode();
            if (code != null && !code.isEmpty()) {
                LambdaQueryWrapper<RuleDataObject> objWrapper = new LambdaQueryWrapper<>();
                objWrapper.eq(RuleDataObject::getScope, scope)
                          .eq(RuleDataObject::getProjectId, normProjectId)
                          .eq(RuleDataObject::getObjectCode, code);
                RuleDataObject target = getOne(objWrapper, false);
                if (target != null) {
                    objectCodeToId.put(code, target.getId());
                } else {
                    // 铁律四：目标对象不存在时，按需创建（延迟创建，避免导入空对象）
                    RuleDataObject newObj = new RuleDataObject();
                    newObj.setProjectId(projectId);
                    newObj.setScope(scope);
                    newObj.setObjectCode(code);
                    newObj.setObjectLabel(code);
                    newObj.setScriptName(code);
                    newObj.setObjectType("INPUT");
                    newObj.setSourceType("JSON");
                    newObj.setStatus(1);
                    save(newObj);
                    objectCodeToId.put(code, newObj.getId());
                }
            }
        }

        // 更新字段的 refObjectId
        for (RuleDataObjectField f : fieldsToResolve) {
            Long resolvedId = objectCodeToId.get(f.getRefObjectCode());
            if (resolvedId != null) {
                f.setRefObjectId(resolvedId);
                objectFieldMapper.updateById(f);
            }
        }
    }
}
