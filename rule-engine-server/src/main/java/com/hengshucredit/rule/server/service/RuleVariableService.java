package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ParsedConstant;
import com.hengshucredit.rule.model.dto.ParsedConstantGroup;
import com.hengshucredit.rule.core.compiler.ConstantValueCodec;
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
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
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
    private ProjectFilterService projectFilterService;

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
        ProjectFilterService.ProjectMatches projectMatches = null;
        if ((projectCode != null && !projectCode.isEmpty())
                || (projectName != null && !projectName.isEmpty())) {
            projectMatches = projectFilterService.resolve(projectCode, projectName);
            if (projectMatches.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
        }
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
        if (projectMatches != null) {
            wrapper.eq(RuleVariable::getScope, SCOPE_PROJECT)
                    .in(RuleVariable::getProjectId, projectMatches.getProjectIds());
        }
        wrapper.orderByDesc(RuleVariable::getUpdateTime)
                .orderByDesc(RuleVariable::getId);
        IPage<RuleVariable> result = page(new Page<>(pageNum, pageSize), wrapper);
        fillProjectName(result.getRecords());
        return result;
    }

    public List<RuleVariable> listByProject(Long projectId, String varSource) {
        if (baseMapper == null) return Collections.emptyList();
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
        if (baseMapper == null) return Collections.emptyList();
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
        if (baseMapper == null) return Collections.emptyList();
        LambdaQueryWrapper<RuleVariable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleVariable::getScope, SCOPE_GLOBAL)
               .eq(RuleVariable::getStatus, 1)
               .orderByAsc(RuleVariable::getSortOrder);
        return list(wrapper);
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
        List<RuleModel> models = listModels(projectId);
        for (RuleModel m : models) {
            String scriptName = trimToNull(m.getModelCode());
            if (m.getId() != null && scriptName != null) {
                map.put(refKey("MODEL", m.getId()), scriptName);
            }
        }
        Map<Long, String> modelCodeMap = models.stream()
                .filter(m -> m.getId() != null && trimToNull(m.getModelCode()) != null)
                .collect(Collectors.toMap(RuleModel::getId, m -> trimToNull(m.getModelCode()), (a, b) -> a));
        for (RuleModelOutputField f : listModelOutputFields(modelCodeMap.keySet())) {
            String modelCode = modelCodeMap.get(f.getModelId());
            String outputScript = trimToNull(f.getFieldName());
            if (outputScript == null) {
                outputScript = trimToNull(f.getFeatureName());
            }
            if (outputScript == null) {
                outputScript = trimToNull(f.getScriptName());
            }
            if (f.getId() != null && modelCode != null && outputScript != null) {
                map.put(refKey("MODEL_OUTPUT", f.getId()), modelCode + "." + outputScript);
            }
        }
        return map;
    }

    /** 构建常量 ID → 可信 QLExpress 表达式映射。 */
    public Map<Long, String> buildRefConstantExpressionMap(Long projectId) {
        List<RuleVariable> vars = projectId != null && projectId > 0
                ? listByProject(projectId, null) : listGlobalOnly();
        Map<Long, String> map = new HashMap<>();
        for (RuleVariable variable : vars) {
            if (variable == null || variable.getId() == null
                    || variable.getStatus() == null || variable.getStatus() != 1
                    || !"CONSTANT".equals(variable.getVarSource())) {
                continue;
            }
            map.put(variable.getId(), ConstantValueCodec.toQlExpression(
                    variable.getVarType(), variable.getDefaultValue()));
        }
        return map;
    }

    /** 构建 CONSTANT:id → Java 运行时值映射。 */
    public Map<String, Object> buildRefConstantValueMap(Long projectId) {
        List<RuleVariable> vars = projectId != null && projectId > 0
                ? listByProject(projectId, null) : listGlobalOnly();
        Map<String, Object> map = new HashMap<>();
        for (RuleVariable variable : vars) {
            if (variable == null || variable.getId() == null
                    || variable.getStatus() == null || variable.getStatus() != 1
                    || !"CONSTANT".equals(variable.getVarSource())) {
                continue;
            }
            map.put(refKey("CONSTANT", variable.getId()), ConstantValueCodec.toRuntimeValue(
                    variable.getVarType(), variable.getDefaultValue()));
        }
        return map;
    }

    private List<RuleDataObjectField> listObjectFields(Long projectId) {
        if (dataObjectFieldMapper == null) return Collections.emptyList();
        LambdaQueryWrapper<RuleDataObjectField> wrapper = new LambdaQueryWrapper<>();
        appendScopeCondition(wrapper, RuleDataObjectField::getScope, RuleDataObjectField::getProjectId, projectId);
        wrapper.eq(RuleDataObjectField::getStatus, 1);
        return dataObjectFieldMapper.selectList(wrapper);
    }

    private Map<Long, RuleDataObject> buildObjectMap(Long projectId) {
        if (dataObjectMapper == null) return Collections.emptyMap();
        LambdaQueryWrapper<RuleDataObject> wrapper = new LambdaQueryWrapper<>();
        appendScopeCondition(wrapper, RuleDataObject::getScope, RuleDataObject::getProjectId, projectId);
        wrapper.eq(RuleDataObject::getStatus, 1);
        return dataObjectMapper.selectList(wrapper).stream()
                .filter(o -> o.getId() != null)
                .collect(Collectors.toMap(RuleDataObject::getId, o -> o, (a, b) -> a));
    }

    private List<RuleModel> listModels(Long projectId) {
        if (modelMapper == null) return Collections.emptyList();
        LambdaQueryWrapper<RuleModel> wrapper = new LambdaQueryWrapper<RuleModel>()
                .select(RuleModel.class, field -> !"model_content".equals(field.getColumn()));
        appendScopeCondition(wrapper, RuleModel::getScope, RuleModel::getProjectId, projectId);
        wrapper.eq(RuleModel::getStatus, 1);
        return modelMapper.selectList(wrapper);
    }

    private List<RuleModelOutputField> listModelOutputFields(java.util.Set<Long> modelIds) {
        if (modelOutputFieldMapper == null || modelIds == null || modelIds.isEmpty()) {
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
        validateAndNormalizeConstant(entity);
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
    @Transactional
    public boolean updateById(RuleVariable entity) {
        RuleVariable current = entity != null && entity.getId() != null ? getById(entity.getId()) : null;
        if (current != null && SCOPE_PROJECT.equals(current.getScope())
                && SCOPE_GLOBAL.equals(entity.getScope())) {
            String targetCode = entity.getVarCode() != null ? entity.getVarCode() : current.getVarCode();
            validateGlobalCodeAvailable(entity.getId(), targetCode);
            entity.setProjectId(0L);
        }
        RuleVariable merged = mergeForConstantCheck(entity);
        validateAndNormalizeConstant(merged);
        if (entity != null && entity.getDefaultValue() != null && merged != null
                && "CONSTANT".equals(merged.getVarSource())) {
            entity.setDefaultValue(merged.getDefaultValue());
        }
        return super.updateById(entity);
    }

    /**
     * 将项目级变量（含常量）转为全局变量，保持用户原始编码不变。
     */
    @Transactional
    public void toGlobal(Long variableId) {
        RuleVariable variable = getById(variableId);
        if (variable == null) {
            throw new IllegalArgumentException("变量不存在");
        }
        if (SCOPE_GLOBAL.equals(variable.getScope())) {
            throw new IllegalArgumentException("该变量已是全局变量，无需转换");
        }
        validateGlobalCodeAvailable(variable.getId(), variable.getVarCode());

        int updated = getBaseMapper().update(null, new LambdaUpdateWrapper<RuleVariable>()
                .eq(RuleVariable::getId, variableId)
                .eq(RuleVariable::getScope, SCOPE_PROJECT)
                .set(RuleVariable::getScope, SCOPE_GLOBAL)
                .set(RuleVariable::getProjectId, 0L)
                .set(RuleVariable::getUpdateTime, LocalDateTime.now()));
        if (updated != 1) {
            throw new IllegalArgumentException("变量状态已变化，请刷新后重试");
        }
    }

    private void validateGlobalCodeAvailable(Long variableId, String varCode) {
        if (varCode == null || varCode.isEmpty()) {
            throw new IllegalArgumentException("变量编码不能为空");
        }
        long conflicts = count(new LambdaQueryWrapper<RuleVariable>()
                .eq(RuleVariable::getScope, SCOPE_GLOBAL)
                .eq(RuleVariable::getVarCode, varCode)
                .ne(variableId != null, RuleVariable::getId, variableId));
        if (conflicts > 0) {
            throw new IllegalArgumentException("变量编码「" + varCode + "」已被其他全局变量使用");
        }
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
        m.setVarType(patch.getVarType() != null ? patch.getVarType() : db.getVarType());
        m.setDefaultValue(patch.getDefaultValue() != null ? patch.getDefaultValue() : db.getDefaultValue());
        return m;
    }

    /** 校验常量类型并将其值规范化；STRING 允许真正的空字符串。 */
    void validateAndNormalizeConstant(RuleVariable v) {
        if (v == null || !"CONSTANT".equals(v.getVarSource())) {
            return;
        }
        v.setDefaultValue(ConstantValueCodec.normalize(v.getVarType(), v.getDefaultValue()));
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
            if (val == null) {
                throw new IllegalArgumentException("常量 [" + pc.getConstCode() + "] 缺少默认值");
            }
            val = ConstantValueCodec.normalize(pc.getConstType(), val);
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
