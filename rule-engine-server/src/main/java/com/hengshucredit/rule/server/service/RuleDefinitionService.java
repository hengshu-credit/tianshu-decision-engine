package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveResponse;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.dto.RuleQueryDTO;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.hengshucredit.rule.server.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RuleDefinitionService extends ServiceImpl<RuleDefinitionMapper, RuleDefinition> {

    @Resource
    private RuleDefinitionContentMapper contentMapper;

    @Resource
    @Lazy
    private RuleLifecycleService lifecycleService;

    @Resource
    @Lazy
    private RuleDraftService ruleDraftService;

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
    private RuleFieldValidationService fieldValidationService;

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
        wrapper.orderByDesc(RuleDefinition::getCreateTime)
                .orderByDesc(RuleDefinition::getId);
        return page(new Page<>(query.getPageNumOrDefault(), query.getPageSizeOrDefault()), wrapper);
    }

    private LambdaQueryWrapper<RuleDefinition> buildWrapper(RuleQueryDTO query) {
        LambdaQueryWrapper<RuleDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(RuleDefinition::getStatus, -1);
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
        if (lifecycleService != null && definition.getId() != null) {
            lifecycleService.requireEditableDraft(definition.getId());
        }
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
        throw draftContractRequired();
    }

    public void saveContent(Long definitionId, String modelJson, String openApiConfigJson) {
        throw draftContractRequired();
    }

    public RuleDraftSaveResponse saveContent(
            RuleDraftSaveRequest request) {
        RuleDraftSaveResponse saved =
                ruleDraftService.save(request);
        refreshParentFields(request.getDefinitionId());
        return saved;
    }

    @Transactional
    public RuleDraftSaveResponse saveTemporaryDraft(Long definitionId,
            com.hengshucredit.rule.model.dto.RuleDraftSourceRequest request) {
        RuleDraftSaveResponse saved = lifecycleService.saveTemporaryDraft(definitionId, request);
        refreshParentFields(definitionId);
        return saved;
    }

    public List<RuleDefinitionVersion> listVersions(Long definitionId) {
        return versionMapper.selectList(new LambdaQueryWrapper<RuleDefinitionVersion>()
                .eq(RuleDefinitionVersion::getDefinitionId, definitionId)
                .orderByDesc(RuleDefinitionVersion::getVersion));
    }

    public RuleDefinitionVersion getVersionById(
            Long definitionId, Long versionId) {
        if (definitionId == null || versionId == null) {
            return null;
        }
        RuleDefinitionVersion snapshot = versionMapper.selectById(versionId);
        return snapshot != null
                && definitionId.equals(snapshot.getDefinitionId())
                ? snapshot : null;
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
        result.put("openApiConfigChanged", !equalsText(left.getOpenApiConfigJson(), right.getOpenApiConfigJson()));
        return result;
    }

    @Transactional
    public void rollbackToVersion(Long definitionId, Integer version) {
        RuleDefinition definition = getById(definitionId);
        if (definition == null) {
            throw new IllegalArgumentException("Rule not found");
        }
        RuleRevision draft = lifecycleService.createDraft(definitionId, null);
        RuleDefinitionVersion snapshot = getVersion(definitionId, version);
        if (snapshot == null) {
            throw new IllegalArgumentException("Version not found");
        }

        ruleDraftService.save(draftRequest(
                draft, snapshot.getModelJson(),
                snapshot.getOpenApiConfigJson(), true));
        RuleDefinitionContent saved = getContent(definitionId);
        if (saved != null) {
            saved.setCompileMessage("rollback to v" + version);
            contentMapper.updateById(saved);
        }
        refreshParentFields(definitionId);
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
        throw draftContractRequired();
    }

    public RuleDraftSaveResponse refreshFields(
            RuleDraftSaveRequest request) {
        return saveContent(request);
    }

    /**
     * 技术人员手动编辑脚本，直接写入 compiledScript，跳过编译器。
     * compileStatus 置为 1（成功），compileMessage 标注来源，scriptMode 置为 script。
     * 若规则已发布，自动同步更新已发布脚本并推送给客户端。
     */
    @Transactional
    public void saveScript(Long definitionId, String script) {
        throw draftContractRequired();
    }

    @Transactional
    public RuleDraftSaveResponse saveScript(
            RuleDraftSaveRequest request) {
        return saveContent(request);
    }

    private RuleGovernanceException draftContractRequired() {
        String code = "DRAFT_SAVE_CONTRACT_REQUIRED";
        String message = "写操作必须显式提供 revisionId、lockVersion 和 modelJson";
        return new RuleGovernanceException(
                400, code, message,
                Collections.singletonList(
                        RuleValidationIssue.error(
                                code, "$", message)));
    }

    private RuleDraftSaveRequest draftRequest(
            RuleRevision draft, String modelJson, String openApiConfigJson,
            boolean updateOpenApiConfig) {
        RuleDraftSaveRequest request = new RuleDraftSaveRequest();
        request.setDefinitionId(draft.getDefinitionId());
        request.setRevisionId(draft.getId());
        request.setLockVersion(draft.getLockVersion());
        request.setModelJson(modelJson);
        request.setOpenApiConfigJson(openApiConfigJson);
        request.setUpdateOpenApiConfig(updateOpenApiConfig);
        return request;
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
        wrapper.ne(RuleDefinition::getStatus, -1);

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
        wrapper.orderByDesc(RuleDefinition::getCreateTime)
                .orderByDesc(RuleDefinition::getId);
        IPage<RuleDefinition> result = page(new Page<>(pageNum, pageSize), wrapper);
        attachFieldMetadata(result.getRecords());
        attachProjectBindings(result.getRecords(), projectId);
        return result;
    }

    private void attachProjectBindings(List<RuleDefinition> definitions,
                                       Long projectId) {
        if (definitions == null || definitions.isEmpty()
                || projectId == null || projectId <= 0) {
            return;
        }
        List<Long> definitionIds = definitions.stream()
                .filter(definition -> definition != null
                        && "GLOBAL".equals(definition.getScope())
                        && definition.getId() != null)
                .map(RuleDefinition::getId)
                .toList();
        if (definitionIds.isEmpty()) {
            return;
        }
        Map<Long, Long> bindingIds = refMapper.selectList(
                        new LambdaQueryWrapper<RuleDefinitionRef>()
                                .eq(RuleDefinitionRef::getProjectId,
                                        projectId)
                                .in(RuleDefinitionRef::getDefinitionId,
                                        definitionIds))
                .stream()
                .collect(Collectors.toMap(
                        RuleDefinitionRef::getDefinitionId,
                        RuleDefinitionRef::getId,
                        (left, right) -> left));
        definitions.forEach(definition -> definition.setProjectBindingId(
                bindingIds.get(definition.getId())));
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
        if (lifecycleService != null) {
            lifecycleService.requireEditableDraft(definitionId);
        }
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
    @Transactional
    public void updateInputField(Long fieldId, RuleDefinitionInputField field) {
        RuleDefinitionInputField existing = inputFieldMapper.selectById(fieldId);
        if (existing == null) {
            throw new IllegalArgumentException("输入字段不存在");
        }
        if (lifecycleService != null) {
            lifecycleService.requireEditableDraft(existing.getDefinitionId());
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
        if (field.getValidationOverride() != null) {
            int override = Integer.valueOf(1).equals(field.getValidationOverride()) ? 1 : 0;
            existing.setValidationOverride(override);
            if (override == 1) {
                RuleDefinition definition = getById(existing.getDefinitionId());
                Long projectId = definition == null ? null : definition.getProjectId();
                existing.setValidationRuleIds(fieldValidationService.validateRuleIds(
                        projectId, field.getValidationRuleIds()));
            } else {
                existing.setValidationRuleIds(null);
            }
        }
        inputFieldMapper.updateById(existing);
        refreshParentFields(existing.getDefinitionId());
    }

    private void refreshParentFields(Long childDefinitionId) {
        refreshParentFields(childDefinitionId, new LinkedHashSet<Long>());
    }

    private void refreshParentFields(Long childDefinitionId, Set<Long> visited) {
        if (childDefinitionId == null || contentMapper == null || !visited.add(childDefinitionId)) return;
        List<RuleDefinitionContent> contents = contentMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionContent>());
        if (contents == null || contents.isEmpty()) return;
        for (RuleDefinitionContent content : contents) {
            if (content == null || content.getDefinitionId() == null
                    || Objects.equals(content.getDefinitionId(), childDefinitionId)
                    || !callsRule(content.getModelJson(), childDefinitionId)) {
                continue;
            }
            RuleDefinition parent = getById(content.getDefinitionId());
            if (parent == null) continue;
            fieldAnalyzer.analyzeAndPersist(parent.getId(), content.getModelJson(),
                    parent.getModelType(), parent.getProjectId());
            refreshParentFields(parent.getId(), visited);
        }
    }

    private boolean callsRule(String modelJson, Long definitionId) {
        for (RuleCallCycleService.RuleCallRef ref : RuleCallCycleService.collectRuleCallRefs(modelJson)) {
            if (Objects.equals(ref.getRuleId(), definitionId)) return true;
        }
        return false;
    }

    /**
     * 更新规则输出字段（关联变量映射）
     */
    public void updateOutputField(Long fieldId, RuleDefinitionOutputField field) {
        RuleDefinitionOutputField existing = outputFieldMapper.selectById(fieldId);
        if (existing == null) {
            throw new IllegalArgumentException("输出字段不存在");
        }
        if (lifecycleService != null) {
            lifecycleService.requireEditableDraft(existing.getDefinitionId());
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
