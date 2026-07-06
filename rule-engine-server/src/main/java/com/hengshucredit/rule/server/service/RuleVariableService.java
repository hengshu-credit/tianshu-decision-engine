package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ParsedConstant;
import com.hengshucredit.rule.model.dto.ParsedConstantGroup;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.entity.RuleVariableOption;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableOptionMapper;
import com.hengshucredit.rule.server.service.parser.JavaEntityParser;
import com.hengshucredit.rule.server.service.parser.JsonSchemaParser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 项目变量与常量（{@code var_source=CONSTANT}）的持久化与分页查询；常量要求非空默认值。
 */
@Service
public class RuleVariableService extends ServiceImpl<RuleVariableMapper, RuleVariable> {

    /** 作用域：全局 */
    public static final String SCOPE_GLOBAL = "GLOBAL";
    /** 作用域：项目级 */
    public static final String SCOPE_PROJECT = "PROJECT";

    @Resource
    private RuleVariableOptionMapper optionMapper;

    @Resource
    private JavaEntityParser javaEntityParser;

    @Resource
    private JsonSchemaParser jsonSchemaParser;

    @Resource
    private RuleProjectMapper projectMapper;

    @Resource
    private RuleDataObjectMapper dataObjectMapper;

    @Resource
    private RuleDataObjectFieldMapper dataObjectFieldMapper;

    @Resource
    private RuleModelMapper modelMapper;

    @Resource
    private RuleModelOutputFieldMapper modelOutputFieldMapper;

    /** 填充变量列表的项目名称 */
    private void fillProjectName(List<RuleVariable> list) {
        if (list == null || list.isEmpty()) return;
        // 收集所有 projectId（排除 0/全局）
        List<Long> projectIds = list.stream()
                .filter(v -> v.getProjectId() != null && v.getProjectId() > 0)
                .map(RuleVariable::getProjectId)
                .distinct()
                .collect(Collectors.toList());
        if (projectIds.isEmpty()) return;
        // 批量查询项目名称
        Map<Long, String> nameMap = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(RuleProject::getId, RuleProject::getProjectName, (a, b) -> a));
        list.forEach(v -> {
            if (v.getProjectId() != null && v.getProjectId() > 0) {
                v.setProjectName(nameMap.get(v.getProjectId()));
            }
        });
    }

    /**
     * 分页列表：可选按类型、关键字过滤；{@code standaloneOnly=true} 时排除常量（供「变量列表」Tab）。
     * @param scope 作用域筛选：GLOBAL/PROJECT，null 表示不限制
     */
    public IPage<RuleVariable> pageList(int pageNum, int pageSize, Long projectId, String varType,
                                        String keyword, Boolean standaloneOnly, String varSource, String scope,
                                        String projectCode, String projectName, String varCode, String varLabel) {
        LambdaQueryWrapper<RuleVariable> wrapper = new LambdaQueryWrapper<>();
        if (scope != null && !scope.isEmpty()) {
            wrapper.eq(RuleVariable::getScope, scope);
        }
        if (projectId != null && projectId > 0) {
            if (scope == null || scope.isEmpty()) {
                // 无 scope 限制时，同时查询全局和项目级
                wrapper.and(w -> w
                        .eq(RuleVariable::getScope, SCOPE_GLOBAL)
                        .or()
                        .eq(RuleVariable::getScope, SCOPE_PROJECT)
                        .eq(RuleVariable::getProjectId, projectId)
                );
            } else if (SCOPE_PROJECT.equals(scope)) {
                wrapper.eq(RuleVariable::getProjectId, projectId);
            }
            // GLOBAL 情况下 projectId 不作为过滤条件
        }
        if (varType != null && !varType.isEmpty()) {
            wrapper.eq(RuleVariable::getVarType, varType);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(RuleVariable::getVarCode, keyword)
                    .or()
                    .like(RuleVariable::getVarLabel, keyword));
        }
        if (varSource != null && !varSource.isEmpty()) {
            wrapper.eq(RuleVariable::getVarSource, varSource);
        } else if (Boolean.TRUE.equals(standaloneOnly)) {
            wrapper.ne(RuleVariable::getVarSource, "CONSTANT");
        }
        // 前缀匹配变量编码（likeLeft: 输入 "AMOUNT" 匹配 "AMOUNT"、"AMOUNT_DISCOUNT" 等）
        if (varCode != null && !varCode.isEmpty()) {
            wrapper.like(RuleVariable::getVarCode, varCode);
        }
        // 前缀匹配变量名称
        if (varLabel != null && !varLabel.isEmpty()) {
            wrapper.like(RuleVariable::getVarLabel, varLabel);
        }
        // 通过 projectCode 或 projectName 进行筛选
        if (projectCode != null && !projectCode.isEmpty()) {
            List<Long> projectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<RuleProject>().like(RuleProject::getProjectCode, projectCode))
                    .stream().map(RuleProject::getId).collect(java.util.stream.Collectors.toList());
            if (!projectIds.isEmpty()) {
                wrapper.and(w -> w.in(RuleVariable::getProjectId, projectIds)
                        .or()
                        .eq(RuleVariable::getScope, SCOPE_GLOBAL));
            } else {
                // 没查到项目，默认查全局
                wrapper.eq(RuleVariable::getScope, SCOPE_GLOBAL);
            }
        } else if (projectName != null && !projectName.isEmpty()) {
            List<Long> projectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<RuleProject>().like(RuleProject::getProjectName, projectName))
                    .stream().map(RuleProject::getId).collect(java.util.stream.Collectors.toList());
            if (!projectIds.isEmpty()) {
                wrapper.and(w -> w.in(RuleVariable::getProjectId, projectIds)
                        .or()
                        .eq(RuleVariable::getScope, SCOPE_GLOBAL));
            } else {
                // 没查到项目，默认查全局
                wrapper.eq(RuleVariable::getScope, SCOPE_GLOBAL);
            }
        } else {
            // 无任何项目筛选条件时，返回所有数据（全局+项目级），便于管理控制台查看全量资源
            // 仅在用户显式指定了 scope 时才做 scope 过滤
        }
        wrapper.orderByAsc(RuleVariable::getSortOrder).orderByDesc(RuleVariable::getCreateTime);
        IPage<RuleVariable> result = page(new Page<>(pageNum, pageSize), wrapper);
        fillProjectName(result.getRecords());
        return result;
    }

    public List<RuleVariable> listByProject(Long projectId, String varSource) {
        LambdaQueryWrapper<RuleVariable> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null && projectId > 0) {
            // 同时查询全局变量和指定项目的变量
            wrapper.and(w -> w
                    .eq(RuleVariable::getScope, SCOPE_GLOBAL)
                    .or()
                    .eq(RuleVariable::getScope, SCOPE_PROJECT)
                    .eq(RuleVariable::getProjectId, projectId)
            );
        } else {
            // 只查询全局变量
            wrapper.eq(RuleVariable::getScope, SCOPE_GLOBAL);
        }
        if (varSource != null && !varSource.isEmpty()) {
            wrapper.eq(RuleVariable::getVarSource, varSource);
        }
        wrapper.eq(RuleVariable::getStatus, 1)
               .orderByAsc(RuleVariable::getSortOrder);
        return list(wrapper);
    }

    /**
     * 仅查询指定项目的变量（不包含全局变量）
     */
    public List<RuleVariable> listByProjectOnly(Long projectId) {
        LambdaQueryWrapper<RuleVariable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleVariable::getProjectId, projectId)
               .eq(RuleVariable::getScope, SCOPE_PROJECT)
               .eq(RuleVariable::getStatus, 1)
               .orderByAsc(RuleVariable::getSortOrder);
        return list(wrapper);
    }

    /**
     * 仅查询全局变量
     */
    public List<RuleVariable> listGlobalOnly() {
        LambdaQueryWrapper<RuleVariable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleVariable::getScope, SCOPE_GLOBAL)
               .eq(RuleVariable::getStatus, 1)
               .orderByAsc(RuleVariable::getSortOrder);
        return list(wrapper);
    }

    /**
     * 构建 VarContext 所需的映射表（varId → scriptName）。
     * 包含全局变量和指定项目的变量，返回的 map 可直接传入 {@link com.hengshucredit.rule.core.compiler.VarContext}。
     *
     * scriptName 规则：
     * - 若 scriptName 非空，使用 scriptName（后端统一驼峰）
     * - 若 scriptName 为空，回退到 varCode
     *
     * @param projectId 项目 ID（传 null 或 0 时仅查全局）
     * @return varId → scriptName 映射（永不为 null）
     */
    public Map<Long, String> buildVarIdScriptNameMap(Long projectId) {
        List<RuleVariable> vars;
        if (projectId != null && projectId > 0) {
            vars = listByProject(projectId, null);
        } else {
            vars = listGlobalOnly();
        }
        Map<Long, String> map = new HashMap<>();
        for (RuleVariable v : vars) {
            String scriptName = v.getScriptName();
            if (scriptName != null && !scriptName.trim().isEmpty()) {
                map.put(v.getId(), scriptName.trim());
            } else {
                // scriptName 为空时回退到 varCode（兼容旧数据）
                map.put(v.getId(), v.getVarCode());
            }
        }
        return map;
    }

    /**
     * 构建 varCode → scriptName 映射。
     * 用于编译器在 varId 缺失时，通过 varCode 回溯找到正确的 scriptName。
     * 包含全局变量和指定项目的变量。严格区分大小写。
     *
     * 匹配规则：
     * - 优先使用 scriptName 作为 key（对应前端的 refCode）
     * - 若 scriptName 为空，使用 varCode 作为 key
     *
     * @param projectId 项目 ID（传 null 或 0 时仅查全局）
     * @return varCode → scriptName 映射（永不为 null）
     */
    public Map<String, String> buildVarCodeScriptNameMap(Long projectId) {
        List<RuleVariable> vars;
        if (projectId != null && projectId > 0) {
            vars = listByProject(projectId, null);
        } else {
            vars = listGlobalOnly();
        }
        Map<String, String> map = new HashMap<>();
        for (RuleVariable v : vars) {
            String scriptName = v.getScriptName();
            if (scriptName != null && !scriptName.trim().isEmpty()) {
                // 优先用 scriptName 作为 key，对应前端的 refCode
                map.put(scriptName.trim(), scriptName.trim());
            }
            // 同时用 varCode 作为 key（兼容旧数据）
            if (v.getVarCode() != null && !v.getVarCode().isEmpty()) {
                String varCodeKey = v.getVarCode();
                // 若 scriptName 已存在则不覆盖（scriptName 优先）
                if (!map.containsKey(varCodeKey)) {
                    map.put(varCodeKey, scriptName != null && !scriptName.trim().isEmpty()
                            ? scriptName.trim() : v.getVarCode());
                }
            }
        }
        Map<Long, RuleDataObject> objectMap = buildObjectMap(projectId);
        for (RuleDataObjectField f : listObjectFields(projectId)) {
            String scriptName = buildObjectFieldScriptName(f, objectMap);
            addCodeMapping(map, f.getScriptName(), scriptName);
            addCodeMapping(map, f.getVarCode(), scriptName);
        }
        for (RuleModel m : listModels(projectId)) {
            String scriptName = trimToNull(m.getModelCode());
            if (scriptName == null) {
                continue;
            }
            addCodeMapping(map, m.getModelCode(), scriptName);
        }
        return map;
    }

    /**
     * 构建 refType:id → scriptName 映射。
     * 用于编译时按字段 ID + 字段类型精确反查，避免不同资源表 ID 冲突。
     */
    public Map<String, String> buildRefScriptNameMap(Long projectId) {
        Map<String, String> map = new HashMap<>();
        List<RuleVariable> vars = projectId != null && projectId > 0 ? listByProject(projectId, null) : listGlobalOnly();
        for (RuleVariable v : vars) {
            String scriptName = resolveVariableScriptName(v);
            if (v.getId() != null && scriptName != null) {
                String refType = "CONSTANT".equals(v.getVarSource()) ? "CONSTANT" : "VARIABLE";
                map.put(refKey(refType, v.getId()), scriptName);
            }
        }
        Map<Long, RuleDataObject> objectMap = buildObjectMap(projectId);
        for (RuleDataObjectField f : listObjectFields(projectId)) {
            String scriptName = buildObjectFieldScriptName(f, objectMap);
            if (f.getId() != null && scriptName != null) {
                map.put(refKey("DATA_OBJECT", f.getId()), scriptName);
            }
        }
        for (RuleModel m : listModels(projectId)) {
            String scriptName = trimToNull(m.getModelCode());
            if (m.getId() != null && scriptName != null) {
                map.put(refKey("MODEL", m.getId()), scriptName);
            }
        }
        Map<Long, String> modelCodeMap = listModels(projectId).stream()
                .filter(m -> m.getId() != null && trimToNull(m.getModelCode()) != null)
                .collect(Collectors.toMap(RuleModel::getId, m -> trimToNull(m.getModelCode()), (a, b) -> a));
        for (RuleModelOutputField f : listModelOutputFields(modelCodeMap.keySet())) {
            String modelCode = modelCodeMap.get(f.getModelId());
            String outputScript = trimToNull(f.getScriptName());
            if (outputScript == null) {
                outputScript = trimToNull(f.getFieldName());
            }
            if (f.getId() != null && modelCode != null && outputScript != null) {
                map.put(refKey("MODEL_OUTPUT", f.getId()), modelCode + "." + outputScript);
            }
        }
        return map;
    }

    private List<RuleDataObjectField> listObjectFields(Long projectId) {
        LambdaQueryWrapper<RuleDataObjectField> wrapper = new LambdaQueryWrapper<>();
        appendScopeCondition(wrapper, RuleDataObjectField::getScope, RuleDataObjectField::getProjectId, projectId);
        wrapper.eq(RuleDataObjectField::getStatus, 1);
        return dataObjectFieldMapper.selectList(wrapper);
    }

    private Map<Long, RuleDataObject> buildObjectMap(Long projectId) {
        LambdaQueryWrapper<RuleDataObject> wrapper = new LambdaQueryWrapper<>();
        appendScopeCondition(wrapper, RuleDataObject::getScope, RuleDataObject::getProjectId, projectId);
        wrapper.eq(RuleDataObject::getStatus, 1);
        return dataObjectMapper.selectList(wrapper).stream()
                .filter(o -> o.getId() != null)
                .collect(Collectors.toMap(RuleDataObject::getId, o -> o, (a, b) -> a));
    }

    private List<RuleModel> listModels(Long projectId) {
        LambdaQueryWrapper<RuleModel> wrapper = new LambdaQueryWrapper<>();
        appendScopeCondition(wrapper, RuleModel::getScope, RuleModel::getProjectId, projectId);
        wrapper.eq(RuleModel::getStatus, 1);
        return modelMapper.selectList(wrapper);
    }

    private List<RuleModelOutputField> listModelOutputFields(java.util.Set<Long> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return modelOutputFieldMapper.selectList(new LambdaQueryWrapper<RuleModelOutputField>()
                .in(RuleModelOutputField::getModelId, modelIds)
                .orderByAsc(RuleModelOutputField::getSortOrder)
                .orderByAsc(RuleModelOutputField::getId));
    }

    private <T> void appendScopeCondition(LambdaQueryWrapper<T> wrapper,
                                          com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> scopeColumn,
                                          com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> projectColumn,
                                          Long projectId) {
        if (projectId != null && projectId > 0) {
            wrapper.and(w -> w.eq(scopeColumn, SCOPE_GLOBAL)
                    .or()
                    .eq(scopeColumn, SCOPE_PROJECT)
                    .eq(projectColumn, projectId));
        } else {
            wrapper.eq(scopeColumn, SCOPE_GLOBAL);
        }
    }

    private String buildObjectFieldScriptName(RuleDataObjectField field, Map<Long, RuleDataObject> objectMap) {
        String fieldScript = trimToNull(field.getScriptName());
        if (fieldScript == null) {
            fieldScript = trimToNull(field.getVarCode());
        }
        if (fieldScript == null) {
            return null;
        }
        RuleDataObject object = objectMap.get(field.getObjectId());
        String objectScript = object != null ? trimToNull(object.getScriptName()) : null;
        if (objectScript == null && object != null) {
            objectScript = trimToNull(object.getObjectCode());
        }
        if (objectScript == null || fieldScript.equals(objectScript) || fieldScript.startsWith(objectScript + ".")) {
            return fieldScript;
        }
        return objectScript + "." + fieldScript;
    }

    private String resolveVariableScriptName(RuleVariable v) {
        String scriptName = trimToNull(v.getScriptName());
        return scriptName != null ? scriptName : trimToNull(v.getVarCode());
    }

    private void addCodeMapping(Map<String, String> map, String key, String scriptName) {
        String k = trimToNull(key);
        String v = trimToNull(scriptName);
        if (k != null && v != null && !map.containsKey(k)) {
            map.put(k, v);
        }
    }

    private String refKey(String refType, Long id) {
        return refType + ":" + id;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public boolean save(RuleVariable entity) {
        assertConstantHasDefault(entity);
        // 确保 scope 有默认值
        if (entity.getScope() == null || entity.getScope().isEmpty()) {
            entity.setScope(SCOPE_PROJECT);
        }
        // upsert：先查是否存在，存在则更新（保留 id），不存在则插入
        LambdaQueryWrapper<RuleVariable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleVariable::getScope, entity.getScope())
               .eq(RuleVariable::getProjectId, entity.getProjectId())
               .eq(RuleVariable::getVarCode, entity.getVarCode());
        RuleVariable existing = getBaseMapper().selectOne(wrapper);
        if (existing != null) {
            entity.setId(existing.getId());
            return super.updateById(entity);
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(RuleVariable entity) {
        assertConstantHasDefault(mergeForConstantCheck(entity));
        return super.updateById(entity);
    }

    /**
     * 部分更新时合并库里的 varSource、defaultValue，再校验常量默认值。
     */
    private RuleVariable mergeForConstantCheck(RuleVariable patch) {
        if (patch == null || patch.getId() == null) {
            return patch;
        }
        RuleVariable db = getById(patch.getId());
        if (db == null) {
            return patch;
        }
        RuleVariable m = new RuleVariable();
        m.setVarSource(patch.getVarSource() != null ? patch.getVarSource() : db.getVarSource());
        m.setDefaultValue(patch.getDefaultValue() != null ? patch.getDefaultValue() : db.getDefaultValue());
        return m;
    }

    /**
     * 常量（{@code var_source=CONSTANT}）必须配置非空默认值。
     */
    private void assertConstantHasDefault(RuleVariable v) {
        if (v == null || !"CONSTANT".equals(v.getVarSource())) {
            return;
        }
        String d = v.getDefaultValue();
        if (d == null || d.trim().isEmpty()) {
            throw new IllegalArgumentException("常量的默认值不能为空");
        }
    }

    /**
     * 从 Java 源码解析 static final 常量并写入 {@code rule_variable}（按 var_code  upsert）。
     * @param scope 作用域：GLOBAL/PROJECT
     */
    @Transactional
    public Map<String, Object> importConstantsFromJava(Long projectId, String scope, String javaSource) {
        Map<String, Object> result = new HashMap<>();
        try {
            ParsedConstantGroup parsed = javaEntityParser.parseConstants(javaSource);
            if (parsed == null || parsed.getConstants() == null || parsed.getConstants().isEmpty()) {
                result.put("success", false);
                result.put("error", "未能从 Java 源码中解析出任何常量，请检查源码格式是否正确");
                return result;
            }
            int count = batchUpsertConstants(projectId, scope, parsed);
            result.put("success", true);
            result.put("constantCount", count);
            result.put("groupCode", parsed.getGroupCode());
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Java 源码解析失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 从扁平 JSON 键值对导入常量（顶层仅基本类型键）。
     * @param scope 作用域：GLOBAL/PROJECT
     */
    @Transactional
    public Map<String, Object> importConstantsFromJson(Long projectId, String scope, String jsonContent) {
        Map<String, Object> result = new HashMap<>();
        try {
            ParsedConstantGroup parsed = jsonSchemaParser.parseConstants(jsonContent);
            if (parsed == null || parsed.getConstants() == null || parsed.getConstants().isEmpty()) {
                result.put("success", false);
                result.put("error", "未能从 JSON 中解析出任何常量，请确保 JSON 格式正确且包含顶层基本类型键值对");
                return result;
            }
            int count = batchUpsertConstants(projectId, scope, parsed);
            result.put("success", true);
            result.put("constantCount", count);
            result.put("groupCode", parsed.getGroupCode());
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "JSON 解析失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 批量插入或更新常量行，不依赖已删除的常量组表。
     * @param projectId 项目ID（0表示全局）
     * @param scope 作用域：GLOBAL/PROJECT
     */
    private int batchUpsertConstants(Long projectId, String scope, ParsedConstantGroup parsed) {
        int count = 0;
        int order = 0;
        for (ParsedConstant pc : parsed.getConstants()) {
            String val = pc.getConstValue();
            if (val == null || val.trim().isEmpty()) {
                throw new IllegalArgumentException("常量 [" + pc.getConstCode() + "] 缺少默认值");
            }
            RuleVariable existing = getBaseMapper().selectOne(
                    new LambdaQueryWrapper<RuleVariable>()
                            .eq(RuleVariable::getScope, scope)
                            .eq(RuleVariable::getProjectId, projectId)
                            .eq(RuleVariable::getVarCode, pc.getConstCode()));
            if (existing != null) {
                existing.setVarType(pc.getConstType());
                existing.setVarSource("CONSTANT");
                existing.setDefaultValue(val);
                if (pc.getConstLabel() != null && !pc.getConstLabel().isEmpty()) {
                    existing.setVarLabel(pc.getConstLabel());
                }
                if (pc.getScriptName() != null && (existing.getScriptName() == null || existing.getScriptName().isEmpty())) {
                    existing.setScriptName(pc.getScriptName());
                }
                getBaseMapper().updateById(existing);
            } else {
                RuleVariable var = new RuleVariable();
                var.setProjectId(projectId);
                var.setScope(scope);
                var.setVarCode(pc.getConstCode());
                var.setVarLabel(pc.getConstLabel() != null ? pc.getConstLabel() : pc.getConstCode());
                var.setScriptName(pc.getScriptName() != null ? pc.getScriptName() : pc.getConstCode());
                var.setVarType(pc.getConstType());
                var.setVarSource("CONSTANT");
                var.setDefaultValue(val);
                var.setSortOrder(order);
                var.setStatus(1);
                getBaseMapper().insert(var);
            }
            count++;
            order++;
        }
        return count;
    }

    @Transactional
    public void deleteWithOptions(Long id) {
        removeById(id);
        LambdaQueryWrapper<RuleVariableOption> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleVariableOption::getVariableId, id);
        optionMapper.delete(wrapper);
    }

    public List<RuleVariableOption> getOptions(Long variableId) {
        LambdaQueryWrapper<RuleVariableOption> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleVariableOption::getVariableId, variableId)
               .orderByAsc(RuleVariableOption::getSortOrder);
        return optionMapper.selectList(wrapper);
    }

    @Transactional
    public void saveOptions(Long variableId, List<RuleVariableOption> options) {
        LambdaQueryWrapper<RuleVariableOption> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleVariableOption::getVariableId, variableId);
        optionMapper.delete(wrapper);

        if (options != null) {
            for (int i = 0; i < options.size(); i++) {
                RuleVariableOption opt = options.get(i);
                opt.setId(null);
                opt.setVariableId(variableId);
                opt.setSortOrder(i);
                optionMapper.insert(opt);
            }
        }
    }
}
