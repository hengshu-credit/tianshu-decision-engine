package com.bjjw.rule.server.service;

import com.bjjw.rule.model.entity.*;
import com.bjjw.rule.model.dto.RulePushMessage;
import com.bjjw.rule.server.mapper.*;
import com.bjjw.rule.server.publish.RulePushService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RuleDefinitionService extends ServiceImpl<RuleDefinitionMapper, RuleDefinition> {

    @Resource
    private RuleDefinitionContentMapper contentMapper;

    @Resource
    private RulePublishedMapper publishedMapper;

    @Resource
    private RulePushService pushService;

    @Resource
    private RuleProjectService projectService;

    @Resource
    private RuleDefinitionRefMapper refMapper;

    @Resource
    private RuleDefinitionInputFieldMapper inputFieldMapper;

    @Resource
    private RuleDefinitionOutputFieldMapper outputFieldMapper;

    @Resource
    private RuleFieldAnalyzer fieldAnalyzer;

    public IPage<RuleDefinition> pageList(int pageNum, int pageSize, Long projectId, String modelType, String keyword, String projectName, String scope, String status, String ruleCode, String ruleName, String projectCode, String publishedVersion, String createBeginTime, String createEndTime, String updateBeginTime, String updateEndTime) {
        LambdaQueryWrapper<RuleDefinition> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(RuleDefinition::getProjectId, projectId);
        }
        if (modelType != null && !modelType.isEmpty()) {
            wrapper.eq(RuleDefinition::getModelType, modelType);
        }
        if (projectName != null && !projectName.isEmpty()) {
            wrapper.like(RuleDefinition::getProjectName, projectName);
        }
        if (scope != null && !scope.isEmpty()) {
            wrapper.eq(RuleDefinition::getScope, scope);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(RuleDefinition::getStatus, status);
        }
        if (ruleCode != null && !ruleCode.isEmpty()) {
            wrapper.eq(RuleDefinition::getRuleCode, ruleCode);
        }
        if (ruleName != null && !ruleName.isEmpty()) {
            wrapper.like(RuleDefinition::getRuleName, ruleName);
        }
        if (projectCode != null && !projectCode.isEmpty()) {
            wrapper.eq(RuleDefinition::getProjectCode, projectCode);
        }
        if (publishedVersion != null && !publishedVersion.isEmpty()) {
            wrapper.eq(RuleDefinition::getPublishedVersion, publishedVersion);
        }
        if (createBeginTime != null && !createBeginTime.isEmpty()) {
            wrapper.ge(RuleDefinition::getCreateTime, java.time.LocalDateTime.parse(createBeginTime + " 00:00:00", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (createEndTime != null && !createEndTime.isEmpty()) {
            wrapper.le(RuleDefinition::getCreateTime, java.time.LocalDateTime.parse(createEndTime + " 23:59:59", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (updateBeginTime != null && !updateBeginTime.isEmpty()) {
            wrapper.ge(RuleDefinition::getUpdateTime, java.time.LocalDateTime.parse(updateBeginTime + " 00:00:00", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (updateEndTime != null && !updateEndTime.isEmpty()) {
            wrapper.le(RuleDefinition::getUpdateTime, java.time.LocalDateTime.parse(updateEndTime + " 23:59:59", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(RuleDefinition::getRuleName, keyword)
                              .or()
                              .like(RuleDefinition::getRuleCode, keyword));
        }
        wrapper.orderByDesc(RuleDefinition::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Transactional
    public RuleDefinition createWithContent(RuleDefinition definition) {
        // 全局规则 projectId 为 0，自动填充 scope
        if (definition.getProjectId() == null || definition.getProjectId() == 0) {
            definition.setProjectId(0L);
            definition.setScope("GLOBAL");
            definition.setProjectCode(null);
            definition.setProjectName(null);
        } else {
            definition.setScope("PROJECT");
            // 填充项目编码和名称
            RuleProject project = projectService.getById(definition.getProjectId());
            if (project != null) {
                definition.setProjectCode(project.getProjectCode());
                definition.setProjectName(project.getProjectName());
            }
        }
        save(definition);
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setDefinitionId(definition.getId());
        content.setModelJson("{}");
        content.setCompileStatus(0);
        contentMapper.insert(content);
        return definition;
    }

    /**
     * 更新规则，同时根据 projectId 填充 projectCode 和 projectName。
     * 兼容 projectId 变更、projectId 清零（全局规则）等场景。
     */
    @Transactional
    public void updateWithProjectInfo(RuleDefinition definition) {
        populateProjectInfo(definition);
        updateById(definition);
    }

    /**
     * 统一根据 projectId 填充 scope、projectCode、projectName。
     * 供 create / update 共用。
     */
    private void populateProjectInfo(RuleDefinition definition) {
        if (definition.getProjectId() == null || definition.getProjectId() == 0) {
            definition.setProjectId(0L);
            definition.setScope("GLOBAL");
            definition.setProjectCode(null);
            definition.setProjectName(null);
        } else {
            definition.setScope("PROJECT");
            RuleProject project = projectService.getById(definition.getProjectId());
            if (project != null) {
                definition.setProjectCode(project.getProjectCode());
                definition.setProjectName(project.getProjectName());
            }
        }
    }

    @Transactional
    public void deleteWithContent(Long id) {
        inputFieldMapper.delete(new LambdaQueryWrapper<RuleDefinitionInputField>()
                .eq(RuleDefinitionInputField::getDefinitionId, id));
        outputFieldMapper.delete(new LambdaQueryWrapper<RuleDefinitionOutputField>()
                .eq(RuleDefinitionOutputField::getDefinitionId, id));
        removeById(id);
        contentMapper.delete(new LambdaQueryWrapper<RuleDefinitionContent>()
                .eq(RuleDefinitionContent::getDefinitionId, id));
    }

    public RuleDefinitionContent getContent(Long definitionId) {
        return contentMapper.selectOne(new LambdaQueryWrapper<RuleDefinitionContent>()
                .eq(RuleDefinitionContent::getDefinitionId, definitionId));
    }

    public void saveContent(Long definitionId, String modelJson) {
        RuleDefinitionContent content = getContent(definitionId);
        if (content != null) {
            content.setModelJson(modelJson);
            content.setCompileStatus(0);
            contentMapper.updateById(content);
        }
        RuleDefinition definition = getById(definitionId);
        if (definition != null) {
            definition.setCurrentVersion(definition.getCurrentVersion() + 1);
            updateById(definition);
            // 从模型内容中解析输入/输出字段并持久化到独立字段表
            // 从变量管理表补充真实元信息（varLabel / varType / scriptName）
            fieldAnalyzer.analyzeAndPersist(definitionId, modelJson, definition.getModelType(), definition.getProjectId());
        }
    }

    /**
     * 从模型内容重新解析输入/输出字段并持久化。
     * 用于刷新规则详情页的字段列表。
     */
    public void refreshFields(Long definitionId, String modelJson, String modelType) {
        RuleDefinition definition = getById(definitionId);
        Long projectId = (definition != null) ? definition.getProjectId() : null;
        fieldAnalyzer.analyzeAndPersist(definitionId, modelJson, modelType, projectId);
    }

    /**
     * 技术人员手动编辑脚本，直接写入 compiledScript，跳过编译器。
     * compileStatus 置为 1（成功），compileMessage 标注来源，scriptMode 置为 script。
     * 若规则已发布，自动同步更新已发布脚本并推送给客户端。
     */
    @Transactional
    public void saveScript(Long definitionId, String script) {
        RuleDefinitionContent content = getContent(definitionId);
        if (content == null) {
            throw new IllegalArgumentException("规则内容不存在，definitionId=" + definitionId);
        }
        content.setCompiledScript(script);
        content.setCompiledType("QLEXPRESS");
        content.setCompileStatus(1);
        content.setCompileMessage("手动编辑脚本（已跳过编译器）");
        content.setCompileTime(LocalDateTime.now());
        content.setScriptMode("script");
        contentMapper.updateById(content);

        RuleDefinition definition = getById(definitionId);
        if (definition != null && definition.getStatus() == 1) {
            syncPublishedScript(definition, script);
        }
    }

    /**
     * 将手动编辑的脚本同步到已发布表，并推送给客户端
     */
    private void syncPublishedScript(RuleDefinition definition, String script) {
        RulePublished published = publishedMapper.selectOne(
                new LambdaQueryWrapper<RulePublished>()
                        .eq(RulePublished::getRuleCode, definition.getRuleCode()));
        if (published == null) {
            return;
        }
        published.setCompiledScript(script);
        published.setPublishTime(LocalDateTime.now());
        publishedMapper.updateById(published);

        RulePushMessage msg = new RulePushMessage();
        msg.setRuleCode(definition.getRuleCode());
        msg.setVersion(published.getVersion());
        msg.setModelType(definition.getModelType());
        msg.setCompiledScript(script);
        msg.setCompiledType("QLEXPRESS");
        msg.setProjectCode(published.getProjectCode());
        msg.setPublishTime(System.currentTimeMillis());
        msg.setAction("PUBLISH");
        pushService.push(msg);
    }

    /**
     * 更新编辑模式（visual/script）
     */
    /**
     * 将全局规则添加到项目中
     * 在关联表记录关联关系（用于前端筛选）
     * @param definitionId 全局规则ID
     * @param projectId 项目ID
     * @return 关联记录
     */
    @Transactional
    public RuleDefinitionRef addGlobalRuleToProject(Long definitionId, Long projectId) {
        RuleDefinition globalRule = getById(definitionId);
        if (globalRule == null) {
            throw new IllegalArgumentException("规则不存在，id=" + definitionId);
        }
        if (!"GLOBAL".equals(globalRule.getScope())) {
            throw new IllegalArgumentException("只能添加全局规则到项目");
        }
        RuleProject project = projectService.getById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在，id=" + projectId);
        }

        // 检查是否已添加过
        Long existCount = refMapper.selectCount(new LambdaQueryWrapper<RuleDefinitionRef>()
                .eq(RuleDefinitionRef::getDefinitionId, definitionId)
                .eq(RuleDefinitionRef::getProjectId, projectId));
        if (existCount > 0) {
            throw new IllegalArgumentException("该全局规则已添加到当前项目");
        }

        // 创建关联记录
        RuleDefinitionRef ref = new RuleDefinitionRef();
        ref.setDefinitionId(definitionId);
        ref.setProjectId(projectId);
        ref.setCreateTime(LocalDateTime.now());
        refMapper.insert(ref);

        return ref;
    }

    /**
     * 获取项目中可用的规则（包括项目级规则和已添加的全局规则）
     * 已添加的全局规则通过关联表 rule_definition_ref 来记录
     */
    public IPage<RuleDefinition> pageListForProject(int pageNum, int pageSize, Long projectId, String modelType, String keyword, String scope, String status, String ruleCode, String ruleName, String createBeginTime, String createEndTime, String updateBeginTime, String updateEndTime) {
        LambdaQueryWrapper<RuleDefinition> wrapper = new LambdaQueryWrapper<>();

        // 查询条件：项目级规则 OR 已关联到该项目的全局规则（通过关联表）
        if (projectId != null && projectId > 0) {
            // 使用子查询来获取关联的全局规则ID
            wrapper.and(w -> w
                    .eq(RuleDefinition::getProjectId, projectId)
                    .or()
                    .exists("SELECT 1 FROM rule_definition_ref rdr WHERE rdr.definition_id = rule_definition.id AND rdr.project_id = " + projectId));
        }

        if (modelType != null && !modelType.isEmpty()) {
            wrapper.eq(RuleDefinition::getModelType, modelType);
        }
        if (scope != null && !scope.isEmpty()) {
            wrapper.eq(RuleDefinition::getScope, scope);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(RuleDefinition::getStatus, status);
        }
        if (ruleCode != null && !ruleCode.isEmpty()) {
            wrapper.eq(RuleDefinition::getRuleCode, ruleCode);
        }
        if (ruleName != null && !ruleName.isEmpty()) {
            wrapper.like(RuleDefinition::getRuleName, ruleName);
        }
        if (createBeginTime != null && !createBeginTime.isEmpty()) {
            wrapper.ge(RuleDefinition::getCreateTime, java.time.LocalDateTime.parse(createBeginTime + " 00:00:00", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (createEndTime != null && !createEndTime.isEmpty()) {
            wrapper.le(RuleDefinition::getCreateTime, java.time.LocalDateTime.parse(createEndTime + " 23:59:59", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (updateBeginTime != null && !updateBeginTime.isEmpty()) {
            wrapper.ge(RuleDefinition::getUpdateTime, java.time.LocalDateTime.parse(updateBeginTime + " 00:00:00", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (updateEndTime != null && !updateEndTime.isEmpty()) {
            wrapper.le(RuleDefinition::getUpdateTime, java.time.LocalDateTime.parse(updateEndTime + " 23:59:59", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(RuleDefinition::getRuleName, keyword)
                              .or()
                              .like(RuleDefinition::getRuleCode, keyword));
        }
        wrapper.orderByDesc(RuleDefinition::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    public void updateScriptMode(Long definitionId, String scriptMode) {
        RuleDefinitionContent content = getContent(definitionId);
        if (content != null) {
            content.setScriptMode(scriptMode);
            contentMapper.updateById(content);
        }
    }

    // ========== 规则字段管理 ==========

    /**
     * 获取规则详情（含输入输出字段）
     */
    public RuleDefinition getDetail(Long definitionId) {
        RuleDefinition definition = getById(definitionId);
        if (definition == null) return null;

        definition.setInputFieldsJson(inputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionInputField>()
                        .eq(RuleDefinitionInputField::getDefinitionId, definitionId)
                        .orderByAsc(RuleDefinitionInputField::getSortOrder)));

        definition.setOutputFieldsJson(outputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionOutputField>()
                        .eq(RuleDefinitionOutputField::getDefinitionId, definitionId)
                        .orderByAsc(RuleDefinitionOutputField::getSortOrder)));

        return definition;
    }

    /**
     * 获取规则的输入字段列表
     */
    public List<RuleDefinitionInputField> listInputFields(Long definitionId) {
        return inputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionInputField>()
                        .eq(RuleDefinitionInputField::getDefinitionId, definitionId)
                        .orderByAsc(RuleDefinitionInputField::getSortOrder));
    }

    /**
     * 获取规则的输出字段列表
     */
    public List<RuleDefinitionOutputField> listOutputFields(Long definitionId) {
        return outputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionOutputField>()
                        .eq(RuleDefinitionOutputField::getDefinitionId, definitionId)
                        .orderByAsc(RuleDefinitionOutputField::getSortOrder));
    }

    /**
     * 更新规则输入字段（关联变量映射）
     */
    public void updateInputField(Long fieldId, RuleDefinitionInputField field) {
        RuleDefinitionInputField existing = inputFieldMapper.selectById(fieldId);
        if (existing == null) {
            throw new IllegalArgumentException("输入字段不存在");
        }
        existing.setVarId(field.getVarId());
        existing.setScriptName(field.getScriptName());
        existing.setFieldLabel(field.getFieldLabel());
        existing.setFieldType(field.getFieldType());
        existing.setMissingValue(field.getMissingValue());
        existing.setDefaultValue(field.getDefaultValue());
        existing.setTransformType(field.getTransformType());
        existing.setTransformParams(field.getTransformParams());
        existing.setValidValues(field.getValidValues());
        inputFieldMapper.updateById(existing);
    }

    /**
     * 更新规则输出字段（关联变量映射）
     */
    public void updateOutputField(Long fieldId, RuleDefinitionOutputField field) {
        RuleDefinitionOutputField existing = outputFieldMapper.selectById(fieldId);
        if (existing == null) {
            throw new IllegalArgumentException("输出字段不存在");
        }
        existing.setVarId(field.getVarId());
        existing.setScriptName(field.getScriptName());
        existing.setFieldLabel(field.getFieldLabel());
        existing.setFieldType(field.getFieldType());
        existing.setTransformType(field.getTransformType());
        existing.setTransformParams(field.getTransformParams());
        outputFieldMapper.updateById(existing);
    }

    /**
     * 删除规则时级联删除字段
     */
    @Transactional
    public void deleteWithFields(Long definitionId) {
        inputFieldMapper.delete(new LambdaQueryWrapper<RuleDefinitionInputField>()
                .eq(RuleDefinitionInputField::getDefinitionId, definitionId));
        outputFieldMapper.delete(new LambdaQueryWrapper<RuleDefinitionOutputField>()
                .eq(RuleDefinitionOutputField::getDefinitionId, definitionId));
        deleteWithContent(definitionId);
    }

    /**
     * 迁移规则旧 JSON 字段到独立表
     * 仅迁移关联了变量（varId 非空）的字段
     */
    @Transactional
    public int migrateFields(Long definitionId) {
        RuleDefinition definition = getById(definitionId);
        if (definition == null) return 0;

        int count = 0;

        // 迁移输入字段
        String inputJson = definition.getInputFields();
        if (inputJson != null && !inputJson.isEmpty()) {
            try {
                List<?> rawList = JSON.parseArray(inputJson);
                int order = 0;
                for (Object item : rawList) {
                    if (!(item instanceof java.util.Map)) continue;
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> f = (java.util.Map<String, Object>) item;
                    Long varId = parseVarId(f.get("varId"));
                    if (varId == null) {
                        order++;
                        continue; // 跳过未关联变量的字段
                    }
                    RuleDefinitionInputField field = new RuleDefinitionInputField();
                    field.setDefinitionId(definitionId);
                    field.setVarId(varId);
                    field.setFieldName(str(f.get("fieldName")));
                    field.setFieldLabel(str(f.get("fieldLabel")));
                    field.setScriptName(str(f.get("scriptName")));
                    field.setFieldType(str(f.get("fieldType")));
                    field.setMissingValue(str(f.get("missingValue")));
                    field.setDefaultValue(str(f.get("defaultValue")));
                    field.setValidValues(str(f.get("validValues")));
                    field.setTransformType(str(f.get("transformType")));
                    field.setTransformParams(str(f.get("transformParams")));
                    field.setSortOrder(order++);
                    field.setStatus(1);
                    field.setCreateTime(LocalDateTime.now());
                    inputFieldMapper.insert(field);
                    count++;
                }
            } catch (Exception e) {
                // 解析失败，跳过
            }
        }

        // 迁移输出字段
        String outputJson = definition.getOutputFields();
        if (outputJson != null && !outputJson.isEmpty()) {
            try {
                List<?> rawList = JSON.parseArray(outputJson);
                int order = 0;
                for (Object item : rawList) {
                    if (!(item instanceof java.util.Map)) continue;
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> f = (java.util.Map<String, Object>) item;
                    Long varId = parseVarId(f.get("varId"));
                    if (varId == null) {
                        order++;
                        continue; // 跳过未关联变量的字段
                    }
                    RuleDefinitionOutputField field = new RuleDefinitionOutputField();
                    field.setDefinitionId(definitionId);
                    field.setVarId(varId);
                    field.setFieldName(str(f.get("fieldName")));
                    field.setFieldLabel(str(f.get("fieldLabel")));
                    field.setScriptName(str(f.get("scriptName")));
                    field.setFieldType(str(f.get("fieldType")));
                    field.setTransformType(str(f.get("transformType")));
                    field.setTransformParams(str(f.get("transformParams")));
                    field.setSortOrder(order++);
                    field.setStatus(1);
                    field.setCreateTime(LocalDateTime.now());
                    outputFieldMapper.insert(field);
                    count++;
                }
            } catch (Exception e) {
                // 解析失败，跳过
            }
        }

        return count;
    }

    /**
     * 全量迁移所有规则的旧 JSON 字段到独立表
     */
    @Transactional
    public int migrateAllFields() {
        List<RuleDefinition> all = list();
        int total = 0;
        for (RuleDefinition def : all) {
            total += migrateFields(def.getId());
        }
        return total;
    }

    private Long parseVarId(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(val.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String str(Object val) {
        if (val == null) return null;
        return val.toString();
    }
}
