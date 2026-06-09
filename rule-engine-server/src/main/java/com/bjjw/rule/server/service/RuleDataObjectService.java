package com.bjjw.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bjjw.rule.model.dto.ParsedField;
import com.bjjw.rule.model.dto.ParsedObject;
import com.bjjw.rule.model.entity.RuleDataObject;
import com.bjjw.rule.model.entity.RuleDataObjectField;
import com.bjjw.rule.model.entity.RuleDataObjectFieldOption;
import com.bjjw.rule.server.mapper.RuleDataObjectFieldMapper;
import com.bjjw.rule.server.mapper.RuleDataObjectFieldOptionMapper;
import com.bjjw.rule.server.mapper.RuleDataObjectMapper;
import com.bjjw.rule.server.service.parser.DdlTableParser;
import com.bjjw.rule.server.service.parser.JavaEntityParser;
import com.bjjw.rule.server.service.parser.JsonSchemaParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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
        m.put("varSource", "INPUT");
        m.put("sortOrder", f.getSortOrder());
        m.put("status", f.getStatus());
        m.put("objectField", Boolean.TRUE);
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
                varCount += batchCreateFields(projectId, scope, obj.getId(), po, null);
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
                varCount += batchCreateFields(projectId, scope, obj.getId(), po, null);
                objectCount++;
                for (ParsedObject nested : po.getNestedObjects()) {
                    RuleDataObject nestedObj = findOrCreateObject(projectId, scope, nested.getObjectCode(), nested.getScriptName(), objectType, "JAVA", null);
                    nestedObj.setParentObjectId(obj.getId());
                    updateById(nestedObj);
                    varCount += batchCreateFields(projectId, scope, nestedObj.getId(), nested, null);
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
            int varCount = batchCreateFields(projectId, scope, obj.getId(), parsed, null);
            int objectCount = 1;

            for (ParsedObject nested : parsed.getNestedObjects()) {
                RuleDataObject nestedObj = findOrCreateObject(projectId, scope, nested.getObjectCode(), nested.getScriptName(), objectType, "JSON", null);
                nestedObj.setParentObjectId(obj.getId());
                updateById(nestedObj);
                varCount += batchCreateFields(projectId, scope, nestedObj.getId(), nested, null);
                objectCount++;
            }
            result.put("success", true);
            result.put("objectCount", objectCount);
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
        wrapper.orderByDesc(RuleDataObject::getCreateTime);
        return list(wrapper);
    }

    /**
     * 仅查询指定项目的数据对象（不包含全局）
     */
    public List<RuleDataObject> listByProjectOnly(Long projectId) {
        LambdaQueryWrapper<RuleDataObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleDataObject::getProjectId, projectId)
               .eq(RuleDataObject::getScope, SCOPE_PROJECT)
               .orderByDesc(RuleDataObject::getCreateTime);
        return list(wrapper);
    }

    /**
     * 仅查询全局数据对象
     */
    public List<RuleDataObject> listGlobalOnly() {
        LambdaQueryWrapper<RuleDataObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleDataObject::getScope, SCOPE_GLOBAL)
               .orderByDesc(RuleDataObject::getCreateTime);
        return list(wrapper);
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
        return super.updateById(entity);
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
     * 获取指定项目的变量树（包含全局和项目级）
     */
    public List<Map<String, Object>> getVariableTree(Long projectId) {
        List<RuleDataObject> objects = listByProject(projectId);
        List<RuleDataObjectField> allFields = new ArrayList<>();

        if (projectId != null && projectId > 0) {
            // 获取项目级字段
            allFields = objectFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDataObjectField>()
                            .eq(RuleDataObjectField::getScope, SCOPE_PROJECT)
                            .eq(RuleDataObjectField::getProjectId, projectId)
                            .orderByAsc(RuleDataObjectField::getSortOrder));
            // 获取全局字段
            List<RuleDataObjectField> globalFields = objectFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDataObjectField>()
                            .eq(RuleDataObjectField::getScope, SCOPE_GLOBAL)
                            .orderByAsc(RuleDataObjectField::getSortOrder));
            allFields.addAll(globalFields);
        } else {
            // 只查询全局字段
            allFields = objectFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDataObjectField>()
                            .eq(RuleDataObjectField::getScope, SCOPE_GLOBAL)
                            .orderByAsc(RuleDataObjectField::getSortOrder));
        }

        Map<Long, List<RuleDataObjectField>> byObject = allFields.stream()
                .collect(Collectors.groupingBy(RuleDataObjectField::getObjectId));

        List<Map<String, Object>> tree = new ArrayList<>();
        for (RuleDataObject obj : objects) {
            Map<String, Object> node = new HashMap<>();
            node.put("object", obj);
            String objScriptName = obj.getScriptName();
            List<Map<String, Object>> vars = byObject.getOrDefault(obj.getId(), Collections.emptyList()).stream()
                    .map(RuleDataObjectService::toVariableRow)
                    .peek(m -> {
                        // 前缀对象路径，使 varCode/varLabel 展示为 "User.age" 而非 "age"
                        String fieldVarCode = (String) m.get("varCode");
                        String fieldScriptName = (String) m.get("scriptName");
                        String fieldVarLabel = (String) m.get("varLabel");
                        if (objScriptName != null && fieldVarCode != null) {
                            m.put("varCode", objScriptName + "." + fieldVarCode);
                        }
                        if (objScriptName != null && fieldScriptName != null) {
                            m.put("scriptName", objScriptName + "." + fieldScriptName);
                        }
                        if (objScriptName != null && fieldVarLabel != null) {
                            m.put("varLabel", objScriptName + "." + fieldVarLabel);
                        }
                    })
                    .collect(Collectors.toList());
            node.put("variables", vars);
            tree.add(node);
        }
        return tree;
    }

    /**
     * 获取所有项目的数据对象树（未选项目时使用，显示所有项目的数据对象）
     */
    public List<Map<String, Object>> getVariableTreeAll() {
        List<RuleDataObject> objects = getBaseMapper().selectList(
                new LambdaQueryWrapper<RuleDataObject>()
                        .orderByAsc(RuleDataObject::getProjectId)
                        .orderByAsc(RuleDataObject::getId));
        List<RuleDataObjectField> allFields = objectFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDataObjectField>()
                        .orderByAsc(RuleDataObjectField::getProjectId)
                        .orderByAsc(RuleDataObjectField::getId));

        Map<Long, List<RuleDataObjectField>> byObject = allFields.stream()
                .collect(Collectors.groupingBy(RuleDataObjectField::getObjectId));

        List<Map<String, Object>> tree = new ArrayList<>();
        for (RuleDataObject obj : objects) {
            Map<String, Object> node = new HashMap<>();
            node.put("object", obj);
            String objScriptName = obj.getScriptName();
            List<Map<String, Object>> vars = byObject.getOrDefault(obj.getId(), Collections.emptyList()).stream()
                    .map(RuleDataObjectService::toVariableRow)
                    .peek(m -> {
                        // 前缀对象路径，使 varCode/varLabel 展示为 "User.age" 而非 "age"
                        String fieldVarCode = (String) m.get("varCode");
                        String fieldScriptName = (String) m.get("scriptName");
                        String fieldVarLabel = (String) m.get("varLabel");
                        if (objScriptName != null && fieldVarCode != null) {
                            m.put("varCode", objScriptName + "." + fieldVarCode);
                        }
                        if (objScriptName != null && fieldScriptName != null) {
                            m.put("scriptName", objScriptName + "." + fieldScriptName);
                        }
                        if (objScriptName != null && fieldVarLabel != null) {
                            m.put("varLabel", objScriptName + "." + fieldVarLabel);
                        }
                    })
                    .collect(Collectors.toList());
            node.put("variables", vars);
            tree.add(node);
        }
        return tree;
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
        objectFieldOptionMapper.delete(new LambdaQueryWrapper<RuleDataObjectFieldOption>()
                .eq(RuleDataObjectFieldOption::getFieldId, fieldId));
        objectFieldMapper.deleteById(fieldId);
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

    private int batchCreateFields(Long projectId, String scope, Long objectId, ParsedObject parsed, Long parentFieldId) {
        int count = 0;
        int order = 0;
        // GLOBAL 场景 projectId 可能为 null，统一转为 0 保持与对象一致
        Long normProjectId = (projectId == null || projectId == 0L) ? 0L : projectId;
        for (ParsedField field : parsed.getFields()) {
            String varCode = field.getFieldName();
            LambdaQueryWrapper<RuleDataObjectField> existWrapper = new LambdaQueryWrapper<>();
            existWrapper.eq(RuleDataObjectField::getObjectId, objectId)
                        .eq(RuleDataObjectField::getVarCode, varCode);
            RuleDataObjectField existing = objectFieldMapper.selectOne(existWrapper);

            if (existing != null) {
                existing.setVarType(field.getVarType());
                existing.setParentFieldId(parentFieldId);
                existing.setRefObjectCode(field.getRefObjectCode());
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
}
