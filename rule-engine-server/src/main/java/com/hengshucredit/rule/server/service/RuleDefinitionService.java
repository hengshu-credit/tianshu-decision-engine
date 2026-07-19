package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.model.dto.RulePushMessage;
import com.hengshucredit.rule.model.dto.RuleQueryDTO;
import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.core.compiler.ScriptPassthroughCompiler;
import com.hengshucredit.rule.core.engine.QLExpressEngineFactory;
import com.hengshucredit.rule.server.mapper.*;
import com.hengshucredit.rule.server.publish.RulePushService;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Resource
    private RuleReferenceIntegrityService referenceIntegrityService;

    @Resource
    private RuleDefinitionVersionMapper versionMapper;

    @Resource
    private RuleCallCycleService ruleCallCycleService;

    @Resource
    private RuleApiDocScenarioService apiDocScenarioService;

    public IPage<RuleDefinition> pageList(RuleQueryDTO query) {
        LambdaQueryWrapper<RuleDefinition> wrapper = buildWrapper(query);
        wrapper.orderByDesc(RuleDefinition::getCreateTime);
        return page(new Page<>(query.getPageNumOrDefault(), query.getPageSizeOrDefault()), wrapper);
    }

    private LambdaQueryWrapper<RuleDefinition> buildWrapper(RuleQueryDTO query) {
        LambdaQueryWrapper<RuleDefinition> wrapper = new LambdaQueryWrapper<>();
        if (query.getProjectId() != null) {
            wrapper.eq(RuleDefinition::getProjectId, query.getProjectId());
        }
        if (query.getModelType() != null && !query.getModelType().isEmpty()) {
            wrapper.eq(RuleDefinition::getModelType, query.getModelType());
        }
        if (query.getProjectName() != null && !query.getProjectName().isEmpty()) {
            wrapper.like(RuleDefinition::getProjectName, query.getProjectName());
        }
        if (query.getScope() != null && !query.getScope().isEmpty()) {
            wrapper.eq(RuleDefinition::getScope, query.getScope());
        }
        if (query.getStatus() != null && !query.getStatus().isEmpty()) {
            wrapper.eq(RuleDefinition::getStatus, query.getStatus());
        }
        if (query.getRuleCode() != null && !query.getRuleCode().isEmpty()) {
            wrapper.like(RuleDefinition::getRuleCode, query.getRuleCode());
        }
        if (query.getRuleName() != null && !query.getRuleName().isEmpty()) {
            wrapper.like(RuleDefinition::getRuleName, query.getRuleName());
        }
        if (query.getProjectCode() != null && !query.getProjectCode().isEmpty()) {
            wrapper.like(RuleDefinition::getProjectCode, query.getProjectCode());
        }
        if (query.getPublishedVersion() != null && !query.getPublishedVersion().isEmpty()) {
            wrapper.eq(RuleDefinition::getPublishedVersion, query.getPublishedVersion());
        }
        if (query.getCreateBeginTime() != null && !query.getCreateBeginTime().isEmpty()) {
            wrapper.ge(RuleDefinition::getCreateTime, java.time.LocalDateTime.parse(query.getCreateBeginTime() + " 00:00:00", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (query.getCreateEndTime() != null && !query.getCreateEndTime().isEmpty()) {
            wrapper.le(RuleDefinition::getCreateTime, java.time.LocalDateTime.parse(query.getCreateEndTime() + " 23:59:59", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (query.getUpdateBeginTime() != null && !query.getUpdateBeginTime().isEmpty()) {
            wrapper.ge(RuleDefinition::getUpdateTime, java.time.LocalDateTime.parse(query.getUpdateBeginTime() + " 00:00:00", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (query.getUpdateEndTime() != null && !query.getUpdateEndTime().isEmpty()) {
            wrapper.le(RuleDefinition::getUpdateTime, java.time.LocalDateTime.parse(query.getUpdateEndTime() + " 23:59:59", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.and(w -> w.like(RuleDefinition::getRuleName, query.getKeyword())
                              .or()
                              .like(RuleDefinition::getRuleCode, query.getKeyword()));
        }
        return wrapper;
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
        definition.setStatus(0);
        definition.setPublishedVersion(null);
        save(definition);
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setDefinitionId(definition.getId());
        content.setModelJson("{}");
        content.setCompileStatus(0);
        contentMapper.insert(content);

        // 创建时触发一次字段解析，确保规则详情页能正确展示出入参
        fieldAnalyzer.analyzeAndPersist(definition.getId(), "{}", definition.getModelType(), definition.getProjectId());

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
        apiDocScenarioService.deleteByDefinition(id);
        removeById(id);
        contentMapper.delete(new LambdaQueryWrapper<RuleDefinitionContent>()
                .eq(RuleDefinitionContent::getDefinitionId, id));
    }

    public RuleDefinitionContent getContent(Long definitionId) {
        return contentMapper.selectOne(new LambdaQueryWrapper<RuleDefinitionContent>()
                .eq(RuleDefinitionContent::getDefinitionId, definitionId));
    }

    public void saveContent(Long definitionId, String modelJson) {
        RuleDefinition definition = getById(definitionId);
        if (definition != null) {
            if (referenceIntegrityService != null) {
                referenceIntegrityService.assertValid(definitionId, definition.getProjectId(), modelJson);
            }
            String cycleError = ruleCallCycleService.validateNoCycle(definitionId, modelJson);
            if (cycleError != null) {
                throw new IllegalArgumentException(cycleError);
            }
        }
        RuleDefinitionContent content = getContent(definitionId);
        if (content != null) {
            content.setModelJson(modelJson);
            content.setCompileStatus(0);
            contentMapper.updateById(content);
        }
        if (definition != null) {
            definition.setCurrentVersion(definition.getCurrentVersion() + 1);
            updateById(definition);
            // 从模型内容中解析输入/输出字段并持久化到独立字段表
            // 从变量管理表补充真实元信息（varLabel / varType / scriptName）
            fieldAnalyzer.analyzeAndPersist(definitionId, modelJson, definition.getModelType(), definition.getProjectId());
        }
    }

    public List<RuleDefinitionVersion> listVersions(Long definitionId) {
        return versionMapper.selectList(new LambdaQueryWrapper<RuleDefinitionVersion>()
                .eq(RuleDefinitionVersion::getDefinitionId, definitionId)
                .orderByDesc(RuleDefinitionVersion::getVersion));
    }

    public RuleDefinitionVersion getVersion(Long definitionId, Integer version) {
        return versionMapper.selectOne(new LambdaQueryWrapper<RuleDefinitionVersion>()
                .eq(RuleDefinitionVersion::getDefinitionId, definitionId)
                .eq(RuleDefinitionVersion::getVersion, version));
    }

    public Map<String, Object> compareVersions(Long definitionId, Integer leftVersion, Integer rightVersion) {
        RuleDefinitionVersion left = getVersion(definitionId, leftVersion);
        RuleDefinitionVersion right = getVersion(definitionId, rightVersion);
        if (left == null || right == null) {
            throw new IllegalArgumentException("Version not found");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("left", left);
        result.put("right", right);
        result.put("modelJsonChanged", !equalsText(left.getModelJson(), right.getModelJson()));
        result.put("compiledScriptChanged", !equalsText(left.getCompiledScript(), right.getCompiledScript()));
        return result;
    }

    @Transactional
    public void rollbackToVersion(Long definitionId, Integer version) {
        RuleDefinition definition = getById(definitionId);
        if (definition == null) {
            throw new IllegalArgumentException("Rule not found");
        }
        RuleDefinitionVersion snapshot = getVersion(definitionId, version);
        if (snapshot == null) {
            throw new IllegalArgumentException("Version not found");
        }

        RuleDefinitionContent content = getContent(definitionId);
        boolean newContent = content == null;
        if (newContent) {
            content = new RuleDefinitionContent();
            content.setDefinitionId(definitionId);
        }
        content.setModelJson(snapshot.getModelJson());
        content.setCompiledScript(snapshot.getCompiledScript());
        content.setCompiledType(snapshot.getCompiledType());
        content.setCompileStatus(1);
        content.setCompileMessage("rollback to v" + version);
        content.setCompileTime(LocalDateTime.now());
        if (newContent) {
            contentMapper.insert(content);
        } else {
            contentMapper.updateById(content);
        }

        definition.setCurrentVersion((definition.getCurrentVersion() == null ? 0 : definition.getCurrentVersion()) + 1);
        updateById(definition);
        fieldAnalyzer.analyzeAndPersist(definitionId, snapshot.getModelJson(), definition.getModelType(), definition.getProjectId());
    }

    private boolean equalsText(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
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
        CompileResult compileResult = compileManualScript(script);
        String compiledScript = compileResult.getCompiledScript();
        content.setCompiledScript(compiledScript);
        content.setCompiledType("QLEXPRESS");
        content.setCompileStatus(1);
        content.setCompileMessage("手动编辑脚本（已校验并包装结果）");
        content.setCompileTime(LocalDateTime.now());
        content.setScriptMode("script");
        contentMapper.updateById(content);

        RuleDefinition definition = getById(definitionId);
        if (definition != null && definition.getStatus() == 1) {
            syncPublishedScript(definition, compiledScript);
        }
    }

    private CompileResult compileManualScript(String script) {
        CompileResult compileResult = new ScriptPassthroughCompiler().compile(script);
        if (!compileResult.isSuccess()) {
            throw new IllegalArgumentException(compileResult.getErrorMessage());
        }
        validateCompiledScript(compileResult.getCompiledScript());
        return compileResult;
    }

    private void validateCompiledScript(String script) {
        try {
            QLExpressEngineFactory.getInstance().execute(script, Collections.emptyMap(),
                    com.alibaba.qlexpress4.QLOptions.builder().cache(false).build());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (isScriptSyntaxError(msg)) {
                throw new IllegalArgumentException(msg, e);
            }
        }
    }

    private boolean isScriptSyntaxError(String msg) {
        String lower = msg.toLowerCase();
        return lower.contains("parse") || lower.contains("syntax")
                || lower.contains("unexpected") || lower.contains("token")
                || lower.contains("瑙ｆ瀽") || lower.contains("璇硶");
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

    public boolean isDefinitionAvailableInProject(Long definitionId, Long projectId) {
        if (definitionId == null || projectId == null) return false;
        RuleDefinition definition = getById(definitionId);
        if (definition == null) return false;
        if (projectId.equals(definition.getProjectId())) return true;
        boolean global = "GLOBAL".equals(definition.getScope())
                || definition.getProjectId() == null
                || definition.getProjectId() == 0L;
        if (!global || projectId == 0L) return global && projectId == 0L;
        Long refCount = refMapper.selectCount(new LambdaQueryWrapper<RuleDefinitionRef>()
                .eq(RuleDefinitionRef::getDefinitionId, definitionId)
                .eq(RuleDefinitionRef::getProjectId, projectId));
        return refCount != null && refCount > 0;
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
            wrapper.like(RuleDefinition::getRuleCode, ruleCode);
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
        IPage<RuleDefinition> result = page(new Page<>(pageNum, pageSize), wrapper);
        attachFieldMetadata(result.getRecords());
        return result;
    }

    private void attachFieldMetadata(List<RuleDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) return;
        List<Long> definitionIds = new ArrayList<>();
        for (RuleDefinition definition : definitions) {
            if (definition != null && definition.getId() != null) definitionIds.add(definition.getId());
        }
        if (definitionIds.isEmpty()) return;

        List<RuleDefinitionInputField> inputFields = inputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionInputField>()
                        .in(RuleDefinitionInputField::getDefinitionId, definitionIds)
                        .orderByAsc(RuleDefinitionInputField::getDefinitionId)
                        .orderByAsc(RuleDefinitionInputField::getSortOrder));
        Map<Long, List<RuleDefinitionInputField>> inputsByDefinition = new LinkedHashMap<>();
        for (RuleDefinitionInputField field : inputFields) {
            inputsByDefinition.computeIfAbsent(field.getDefinitionId(), key -> new ArrayList<>()).add(field);
        }

        List<RuleDefinitionOutputField> outputFields = outputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionOutputField>()
                        .in(RuleDefinitionOutputField::getDefinitionId, definitionIds)
                        .orderByAsc(RuleDefinitionOutputField::getDefinitionId)
                        .orderByAsc(RuleDefinitionOutputField::getSortOrder));
        Map<Long, List<RuleDefinitionOutputField>> outputsByDefinition = new LinkedHashMap<>();
        for (RuleDefinitionOutputField field : outputFields) {
            outputsByDefinition.computeIfAbsent(field.getDefinitionId(), key -> new ArrayList<>()).add(field);
        }
        for (RuleDefinition definition : definitions) {
            definition.setInputFieldsJson(inputsByDefinition.getOrDefault(definition.getId(), Collections.emptyList()));
            definition.setOutputFieldsJson(outputsByDefinition.getOrDefault(definition.getId(), Collections.emptyList()));
        }
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
        existing.setRefType(field.getRefType());
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
        existing.setRefType(field.getRefType());
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



    }
