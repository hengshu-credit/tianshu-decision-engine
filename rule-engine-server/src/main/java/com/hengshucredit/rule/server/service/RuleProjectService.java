package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ApiDocDTO;
import com.hengshucredit.rule.model.dto.ProjectAuthDTO;
import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.core.trace.TraceIdGenerator;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.artifact.PublishedRuleFieldSnapshotResolver;
import com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService;
import com.hengshucredit.rule.server.openapi.OpenApiContractCodec;
import com.hengshucredit.rule.server.artifact.RuleSchemaService;
import com.hengshucredit.rule.server.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class RuleProjectService extends ServiceImpl<RuleProjectMapper, RuleProject> {

    @Resource
    private ProjectAuthService projectAuthService;

    public IPage<RuleProject> pageList(int pageNum, int pageSize, String keyword, String projectCode, String projectName, Integer status, String createBeginTime, String createEndTime) {
        LambdaQueryWrapper<RuleProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(RuleProject::getStatus, -1);
        // 关键字搜索（模糊匹配编码或名称）
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w
                    .like(RuleProject::getProjectCode, keyword)
                    .or()
                    .like(RuleProject::getProjectName, keyword));
        }
        // 项目编码精确匹配
        if (projectCode != null && !projectCode.isEmpty()) {
            wrapper.like(RuleProject::getProjectCode, projectCode);
        }
        // 项目名称模糊匹配
        if (projectName != null && !projectName.isEmpty()) {
            wrapper.like(RuleProject::getProjectName, projectName);
        }
        // 启用状态筛选
        if (status != null) {
            wrapper.eq(RuleProject::getStatus, status);
        }
        // 创建时间范围
        if (createBeginTime != null && !createBeginTime.isEmpty()) {
            wrapper.ge(RuleProject::getCreateTime, createBeginTime + " 00:00:00");
        }
        if (createEndTime != null && !createEndTime.isEmpty()) {
            wrapper.le(RuleProject::getCreateTime, createEndTime + " 23:59:59");
        }
        wrapper.orderByDesc(RuleProject::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }
    
    /**
     * 创建项目并自动生成AccessToken
     */
    @Transactional
    public String createProjectWithToken(RuleProject project) {
        // 生成UUID Token
        String token = UUID.randomUUID().toString().replace("-", "");
        
        // Token 仅保存到加密鉴权配置，旧字段保持为空
        project.setAccessToken(null);
        
        // 保存项目
        save(project);
        project.setTraceScopeCode(TraceIdGenerator.projectScopeCode(project.getId()));
        updateById(project);
        projectAuthService.saveLegacyToken(project, token);
        
        log.info("Created project with access token: {}", project.getProjectCode());
        return token;
    }
    
    /**
     * 根据Token验证并返回项目
     */
    public RuleProject validateToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        
        ProjectAuthContext context = projectAuthService.authenticateLegacyToken(token);
        if (context == null) {
            log.warn("Token not found for any project");
            return null;
        }
        return getById(context.getProjectId());
    }
    
    /**
     * 获取Token脱敏显示
     */
    public String getMaskedToken(Long projectId) {
        return maskToken(projectAuthService.getLegacyToken(projectId));
    }

    /**
     * 获取完整Token（需登录后查看，不脱敏）
     */
    public String getFullToken(Long projectId) {
        return projectAuthService.getLegacyToken(projectId);
    }

    /**
     * 重新生成项目AccessToken（支持禁用旧Token后重新生成）
     */
    @Transactional
    public String regenerateToken(Long projectId) {
        String newToken = UUID.randomUUID().toString().replace("-", "");
        RuleProject project = getById(projectId);
        if (project != null) {
            projectAuthService.saveLegacyToken(project, newToken);
        }
        return newToken;
    }

    /**
     * 验证Token并返回项目（兼容 projectCode 的备用查询）
     */
    public RuleProject validateTokenOrCode(String tokenOrCode) {
        if (!StringUtils.hasText(tokenOrCode)) {
            return null;
        }
        RuleProject project = validateToken(tokenOrCode);
        if (project != null) {
            return project;
        }
        // 尝试按 projectCode 匹配
        LambdaQueryWrapper<RuleProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleProject::getProjectCode, tokenOrCode)
               .eq(RuleProject::getStatus, 1);
        return getOne(wrapper);
    }
    
    /**
     * Token脱敏显示
     */
    private String maskToken(String token) {
        if (!StringUtils.hasText(token) || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    @Resource
    private com.hengshucredit.rule.server.mapper.RuleDefinitionMapper definitionMapper;

    @Resource
    private com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper contentMapper;

    @Resource
    private com.hengshucredit.rule.server.mapper.RuleVariableMapper variableMapper;

    @Resource
    private com.hengshucredit.rule.server.mapper.RuleDataObjectMapper dataObjectMapper;

    @Resource
    private com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper fieldMapper;

    @Resource
    private com.hengshucredit.rule.server.mapper.RuleFunctionMapper functionMapper;

    @Resource
    private RuleDefinitionInputFieldMapper inputFieldMapper;

    @Resource
    private RuleDefinitionOutputFieldMapper outputFieldMapper;

    @Resource
    private RuleModelVarParser ruleModelVarParser;

    @Resource
    private RuleApiDocScenarioService apiDocScenarioService;

    @Resource
    private RulePublishedMapper publishedMapper;

    @Resource
    private PublishedRuleFieldSnapshotResolver publishedFieldSnapshotResolver;

    @Resource
    private ArtifactRuntimeSnapshotService artifactRuntimeSnapshotService;

    @Resource
    @org.springframework.context.annotation.Lazy
    private RuleFieldAnalyzer ruleFieldAnalyzer;

    /**
     * 导出项目API文档
     */
    public ApiDocDTO exportApiDoc(Long projectId) {
        ApiDocDTO doc = new ApiDocDTO();

        // 项目信息
        RuleProject project = getById(projectId);
        if (project == null) {
            return doc;
        }
        ApiDocDTO.ProjectInfo projectInfo = new ApiDocDTO.ProjectInfo();
        projectInfo.setId(project.getId());
        projectInfo.setProjectCode(project.getProjectCode());
        projectInfo.setProjectName(project.getProjectName());
        projectInfo.setDescription(project.getDescription());
        projectInfo.setStatus(project.getStatus());
        doc.setProject(projectInfo);

        List<ApiDocDTO.AuthenticationInfo> authenticationInfos = new ArrayList<>();
        List<ProjectAuthDTO> authentications = projectAuthService.listAuths(projectId);
        if (authentications != null) {
            for (ProjectAuthDTO authentication : authentications) {
                if (authentication == null || !Integer.valueOf(1).equals(authentication.getStatus())) continue;
                ApiDocDTO.AuthenticationInfo info = new ApiDocDTO.AuthenticationInfo();
                info.setAuthName(authentication.getAuthName());
                info.setAuthType(authentication.getAuthType());
                info.setPlacement(authentication.getPlacement());
                info.setParameterName(authentication.getParameterName());
                info.setTokenTtlSeconds(authentication.getTokenTtlSeconds());
                info.setTokenGraceSeconds(authentication.getTokenGraceSeconds());
                authenticationInfos.add(info);
            }
        }
        doc.setAuthentications(authenticationInfos);

        // ========== 1. 先构建变量列表和 Map ==========
        List<RuleVariable> variables = variableMapper.selectList(
                new LambdaQueryWrapper<RuleVariable>()
                        .and(w -> w
                                .eq(RuleVariable::getScope, "GLOBAL")
                                .or()
                                .eq(RuleVariable::getScope, "PROJECT")
                                .eq(RuleVariable::getProjectId, projectId))
                        .eq(RuleVariable::getStatus, 1)
                        .orderByAsc(RuleVariable::getSortOrder));
        List<ApiDocDTO.VariableInfo> varInfos = new ArrayList<>();
        java.util.Map<String, ApiDocDTO.VariableInfo> varCodeMap = new java.util.HashMap<>();
        java.util.Map<Long, ApiDocDTO.VariableInfo> varIdMap = new java.util.HashMap<>();
        for (RuleVariable var : variables) {
            ApiDocDTO.VariableInfo varInfo = new ApiDocDTO.VariableInfo();
            varInfo.setId(var.getId());
            varInfo.setVarCode(var.getVarCode());
            varInfo.setVarLabel(var.getVarLabel());
            varInfo.setVarType(var.getVarType());
            varInfo.setVarTypeLabel(getVarTypeLabel(var.getVarType()));
            varInfo.setVarSource(var.getVarSource());
            varInfo.setVarSourceLabel(getVarSourceLabel(var.getVarSource()));
            varInfo.setDefaultValue(var.getDefaultValue());
            varInfo.setValueRange(var.getValueRange());
            varInfo.setExampleValue(var.getExampleValue());
            varInfo.setDescription(var.getDescription());
            varInfo.setScriptName(var.getScriptName());
            varInfos.add(varInfo);
            varIdMap.put(var.getId(), varInfo);
            varCodeMap.put(var.getVarCode(), varInfo);
            if (var.getScriptName() != null && !var.getScriptName().trim().isEmpty()) {
                varCodeMap.put(var.getScriptName(), varInfo);
            }
        }
        doc.setVariables(varInfos);

        // ========== 2. 构建数据对象列表和 Map ==========
        List<RuleDataObject> dataObjects = dataObjectMapper.selectList(
                new LambdaQueryWrapper<RuleDataObject>()
                        .and(w -> w
                                .eq(RuleDataObject::getScope, "GLOBAL")
                                .or()
                                .eq(RuleDataObject::getScope, "PROJECT")
                                .eq(RuleDataObject::getProjectId, projectId))
                        .eq(RuleDataObject::getStatus, 1)
                        .orderByAsc(RuleDataObject::getCreateTime));
        List<ApiDocDTO.DataObjectInfo> doInfos = new ArrayList<>();
        java.util.Map<Long, ApiDocDTO.DataObjectInfo> dataObjectIdMap = new java.util.HashMap<>();
        java.util.Map<Long, RuleDataObjectField> dataObjectFieldIdMap = new java.util.HashMap<>();
        for (RuleDataObject obj : dataObjects) {
            ApiDocDTO.DataObjectInfo doInfo = new ApiDocDTO.DataObjectInfo();
            doInfo.setId(obj.getId());
            doInfo.setObjectCode(obj.getObjectCode());
            doInfo.setObjectLabel(obj.getObjectLabel());
            doInfo.setObjectType(obj.getObjectType());
            doInfo.setObjectTypeLabel(getObjectTypeLabel(obj.getObjectType()));
            doInfo.setSourceType(obj.getSourceType());
            doInfo.setSourceTypeLabel(getSourceTypeLabel(obj.getSourceType()));
            doInfo.setScriptName(obj.getScriptName());

            // 获取字段列表
            List<RuleDataObjectField> fields = fieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDataObjectField>()
                            .eq(RuleDataObjectField::getObjectId, obj.getId())
                            .eq(RuleDataObjectField::getStatus, 1)
                            .orderByAsc(RuleDataObjectField::getSortOrder));
            List<ApiDocDTO.FieldInfo> fieldInfos = new ArrayList<>();
            for (RuleDataObjectField field : fields) {
                dataObjectFieldIdMap.put(field.getId(), field);
                ApiDocDTO.FieldInfo fieldInfo = new ApiDocDTO.FieldInfo();
                fieldInfo.setId(field.getId());
                fieldInfo.setVarCode(field.getVarCode());
                fieldInfo.setVarLabel(field.getVarLabel());
                fieldInfo.setVarType(field.getVarType());
                fieldInfo.setVarTypeLabel(getVarTypeLabel(field.getVarType()));
                fieldInfo.setScriptName(field.getScriptName());
                fieldInfo.setRefObjectCode(field.getRefObjectCode());
                fieldInfo.setParentVarCode(field.getParentFieldId() != null ? getParentVarCode(field.getParentFieldId()) : null);
                fieldInfos.add(fieldInfo);
            }
            doInfo.setFields(fieldInfos);
            doInfos.add(doInfo);
            dataObjectIdMap.put(obj.getId(), doInfo);
        }
        doc.setDataObjects(doInfos);

        // ========== 3. 处理规则列表 ==========
        LambdaQueryWrapper<RuleDefinition> definitionWrapper = new LambdaQueryWrapper<RuleDefinition>()
                .eq(RuleDefinition::getStatus, 1);
        appendProjectRuleScope(definitionWrapper, projectId);
        definitionWrapper.orderByDesc(RuleDefinition::getCreateTime);
        List<RuleDefinition> definitions = definitionMapper.selectList(definitionWrapper);
        Map<Long, RulePublished> publishedByDefinition = null;
        if (publishedMapper != null) {
            publishedByDefinition = new LinkedHashMap<>();
            List<RulePublished> publishedRows = publishedMapper.selectList(
                    new LambdaQueryWrapper<RulePublished>().eq(RulePublished::getStatus, 1));
            for (RulePublished published : publishedRows == null ? Collections.<RulePublished>emptyList() : publishedRows) {
                if (published != null && published.getDefinitionId() != null) {
                    publishedByDefinition.put(published.getDefinitionId(), published);
                }
            }
        }
        List<ApiDocDTO.RuleInfo> ruleInfos = new ArrayList<>();
        for (RuleDefinition def : definitions) {
            RulePublished published = publishedByDefinition == null ? null : publishedByDefinition.get(def.getId());
            // In production the export must describe executable published assets only.
            // A null mapper is retained solely for lightweight legacy unit fixtures.
            if (publishedByDefinition != null && published == null) continue;
            ApiDocDTO.RuleInfo ruleInfo = new ApiDocDTO.RuleInfo();
            ruleInfo.setId(def.getId());
            ruleInfo.setRuleCode(def.getRuleCode());
            ruleInfo.setRuleName(def.getRuleName());
            ruleInfo.setModelType(def.getModelType());
            ruleInfo.setModelTypeLabel(getModelTypeLabel(def.getModelType()));
            ruleInfo.setDescription(def.getDescription());
            ruleInfo.setCurrentVersion(def.getCurrentVersion());
            ruleInfo.setPublishedVersion(def.getPublishedVersion());
            if (published != null) {
                ruleInfo.setPublishedVersion(published.getVersion());
                ruleInfo.setPublishedRevisionId(published.getRevisionId());
                ruleInfo.setPublishedArtifactDigest(published.getArtifactDigest());
                ruleInfo.setOpenApiEnabled(openApiEnabled(published.getOpenApiConfigJson()));
                ruleInfo.setOpenApiContract(openApiContractProjection(published.getOpenApiConfigJson()));
            }
            ruleInfo.setStatus(def.getStatus());
            ruleInfo.setStatusLabel(getStatusLabel(def.getStatus()));

            if (published != null && publishedFieldSnapshotResolver != null) {
                populatePublishedContract(ruleInfo, published, projectId, varIdMap, dataObjectIdMap,
                        dataObjectFieldIdMap);
            } else {
            // Legacy fixture path: only used when no published mapper/snapshot resolver is available.
            // Production exports always enter the immutable published contract path above.

            // 获取模型JSON
            RuleDefinitionContent content = contentMapper.selectOne(
                    new LambdaQueryWrapper<RuleDefinitionContent>()
                            .eq(RuleDefinitionContent::getDefinitionId, def.getId()));
            String modelJson = null;
            if (content != null) {
                modelJson = content.getModelJson();
            }

            // 解析模型的出入参变量代码
            RuleModelVarParser.ParseResult parseResult = ruleModelVarParser.parse(modelJson, def.getModelType());
            Set<String> inputCodes = parseResult.getInputCodes();
            Set<String> outputCodes = parseResult.getOutputCodes();
            List<RuleDefinitionInputField> persistedInputFields = inputFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDefinitionInputField>()
                            .eq(RuleDefinitionInputField::getDefinitionId, def.getId())
                            .orderByAsc(RuleDefinitionInputField::getSortOrder));
            boolean resolvedInputs = ruleFieldAnalyzer != null && modelJson != null && !modelJson.isBlank();
            if (resolvedInputs) {
                persistedInputFields = ruleFieldAnalyzer.resolveFields(def.getId(), modelJson,
                        def.getModelType(), projectId).getInputFields();
            }
            if (persistedInputFields != null && (resolvedInputs || !persistedInputFields.isEmpty())) {
                inputCodes = new LinkedHashSet<>();
                for (RuleDefinitionInputField field : persistedInputFields) {
                    ApiDocDTO.VariableInfo fieldInfo = toVariableInfo(field, varCodeMap);
                    addFieldCodes(inputCodes, field.getScriptName(), field.getFieldName());
                    putFieldInfo(varCodeMap, field.getScriptName(), fieldInfo);
                    putFieldInfo(varCodeMap, field.getFieldName(), fieldInfo);
                }
            }
            List<RuleDefinitionOutputField> persistedOutputFields = outputFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDefinitionOutputField>()
                            .eq(RuleDefinitionOutputField::getDefinitionId, def.getId())
                            .orderByAsc(RuleDefinitionOutputField::getSortOrder));
            if (persistedOutputFields != null && !persistedOutputFields.isEmpty()) {
                outputCodes = new LinkedHashSet<>();
                for (RuleDefinitionOutputField field : persistedOutputFields) {
                    ApiDocDTO.VariableInfo fieldInfo = toVariableInfo(field, varCodeMap);
                    addFieldCodes(outputCodes, field.getScriptName(), field.getFieldName());
                    putFieldInfo(varCodeMap, field.getScriptName(), fieldInfo);
                    putFieldInfo(varCodeMap, field.getFieldName(), fieldInfo);
                }
            }

            // 根据代码匹配完整的变量信息（输入）
            List<ApiDocDTO.VariableInfo> inputVars = new ArrayList<>();
            for (String code : inputCodes) {
                ApiDocDTO.VariableInfo vi = varCodeMap.get(code);
                if (vi != null) {
                    inputVars.add(vi);
                }
            }
            ruleInfo.setInputVariables(inputVars);

            // 根据代码匹配完整的变量信息（输出）
            List<ApiDocDTO.VariableInfo> outputVars = new ArrayList<>();
            for (String code : outputCodes) {
                ApiDocDTO.VariableInfo vi = varCodeMap.get(code);
                if (vi != null) {
                    outputVars.add(vi);
                }
            }
            ruleInfo.setOutputVariables(outputVars);

            // 根据变量代码推断输入/输出数据对象
            List<ApiDocDTO.DataObjectInfo> inputDOs = new ArrayList<>();
            List<ApiDocDTO.DataObjectInfo> outputDOs = new ArrayList<>();
            for (ApiDocDTO.DataObjectInfo doi : doInfos) {
                String objType = doi.getObjectType();
                if ("INPUT".equals(objType) || "INOUT".equals(objType)) {
                    boolean hasInputField = false;
                    boolean hasOutputField = false;
                    List<ApiDocDTO.FieldInfo> fields = doi.getFields();
                    if (fields != null) {
                        for (ApiDocDTO.FieldInfo f : fields) {
                            if (inputCodes.contains(f.getVarCode())) hasInputField = true;
                            if (outputCodes.contains(f.getVarCode())) hasOutputField = true;
                        }
                    }
                    if (hasInputField) inputDOs.add(doi);
                    if (hasOutputField) outputDOs.add(doi);
                } else if ("OUTPUT".equals(objType) || "INOUT".equals(objType)) {
                    boolean hasOutputField = false;
                    List<ApiDocDTO.FieldInfo> fields = doi.getFields();
                    if (fields != null) {
                        for (ApiDocDTO.FieldInfo f : fields) {
                            if (outputCodes.contains(f.getVarCode())) hasOutputField = true;
                        }
                    }
                    if (hasOutputField) outputDOs.add(doi);
                }
            }
            ruleInfo.setInputDataObjects(inputDOs);
            ruleInfo.setOutputDataObjects(outputDOs);
            }

            List<ApiDocDTO.ScenarioInfo> scenarioInfos = new ArrayList<>();
            List<RuleApiDocScenario> scenarios = apiDocScenarioService.listExportable(
                    def.getId(), def.getPublishedVersion());
            if (scenarios != null) {
                for (RuleApiDocScenario scenario : scenarios) {
                    ApiDocDTO.ScenarioInfo scenarioInfo = new ApiDocDTO.ScenarioInfo();
                    scenarioInfo.setId(scenario.getId());
                    scenarioInfo.setScenarioName(scenario.getScenarioName());
                    scenarioInfo.setDescription(scenario.getDescription());
                    scenarioInfo.setRequestJson(scenario.getRequestJson());
                    scenarioInfo.setResponseJson(scenario.getResponseJson());
                    scenarioInfo.setOuterCode(scenario.getOuterCode());
                    scenarioInfo.setBusinessCodePath(scenario.getBusinessCodePath());
                    scenarioInfo.setBusinessCode(scenario.getBusinessCode());
                    scenarioInfo.setSortOrder(scenario.getSortOrder());
                    scenarioInfos.add(scenarioInfo);
                }
            }
            ruleInfo.setScenarios(scenarioInfos);

            ruleInfos.add(ruleInfo);
        }
        doc.setRules(ruleInfos);

        // 自定义函数列表
        List<RuleFunction> functions = functionMapper.selectList(
                new LambdaQueryWrapper<RuleFunction>()
                        .and(w -> w
                                .eq(RuleFunction::getScope, "GLOBAL")
                                .or()
                                .eq(RuleFunction::getScope, "PROJECT")
                                .eq(RuleFunction::getProjectId, projectId))
                        .eq(RuleFunction::getStatus, 1)
                        .orderByAsc(RuleFunction::getCreateTime));
        List<ApiDocDTO.FunctionInfo> funcInfos = new ArrayList<>();
        for (RuleFunction func : functions) {
            ApiDocDTO.FunctionInfo funcInfo = new ApiDocDTO.FunctionInfo();
            funcInfo.setId(func.getId());
            funcInfo.setFuncCode(func.getFuncCode());
            funcInfo.setFuncName(func.getFuncName());
            funcInfo.setDescription(func.getDescription());
            funcInfo.setParamsJson(func.getParamsJson());
            funcInfo.setReturnType(func.getReturnType());
            funcInfo.setImplType(func.getImplType());
            funcInfo.setImplTypeLabel(getImplTypeLabel(func.getImplType()));
            funcInfos.add(funcInfo);
        }
        doc.setFunctions(funcInfos);

        return doc;
    }

    private void appendProjectRuleScope(LambdaQueryWrapper<RuleDefinition> wrapper, Long projectId) {
        if (projectId != null && projectId > 0) {
            wrapper.and(w -> w.eq(RuleDefinition::getProjectId, projectId)
                    .or()
                    .exists("SELECT 1 FROM rule_definition_ref rdr WHERE rdr.definition_id = rule_definition.id AND rdr.project_id = " + projectId));
        } else {
            wrapper.eq(RuleDefinition::getProjectId, 0L);
        }
    }

    /**
     * 用已发布制品中的字段快照生成对外契约。这里禁止回读工作稿或按名称反查资源，
     * 否则变量改名、字段下线后，已发布 API 文档会与实际执行制品不一致。
     */
    private void populatePublishedContract(ApiDocDTO.RuleInfo ruleInfo, RulePublished published,
                                           Long projectId,
                                           Map<Long, ApiDocDTO.VariableInfo> variableById,
                                           Map<Long, ApiDocDTO.DataObjectInfo> dataObjectById,
                                           Map<Long, RuleDataObjectField> dataObjectFieldById) {
        RuleFieldAnalyzer.ResolvedFields resolved = publishedFieldSnapshotResolver.resolve(published);
        List<String> diagnostics = new ArrayList<>();
        if (resolved.getDiagnostics() != null) {
            resolved.getDiagnostics().forEach(issue -> {
                if (issue != null) {
                    String text = issue.getMessage();
                    diagnostics.add((issue.getCode() == null ? "" : issue.getCode() + ": ")
                            + (text == null ? "字段快照校验失败" : text));
                }
            });
        }
        if (!diagnostics.isEmpty()) {
            ruleInfo.setSchemaTrust("UNVERIFIED");
            ruleInfo.setSchemaDiagnostics(diagnostics);
            ruleInfo.setInputVariables(Collections.emptyList());
            ruleInfo.setOutputVariables(Collections.emptyList());
            ruleInfo.setInputDataObjects(Collections.emptyList());
            ruleInfo.setOutputDataObjects(Collections.emptyList());
            ruleInfo.setInputSchema(Collections.emptyMap());
            ruleInfo.setOutputSchema(Collections.emptyMap());
            return;
        }

        Map<Long, ApiDocDTO.VariableInfo> frozenVariableById = variableById;
        Map<Long, RuleDataObjectField> frozenDataObjectFieldById = dataObjectFieldById;
        if (artifactRuntimeSnapshotService != null) {
            try {
                ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot = artifactRuntimeSnapshotService.load(
                        published.getArtifactId(), published.getDefinitionId(), projectId);
                frozenVariableById = new LinkedHashMap<>();
                for (RuleVariable variable : snapshot.getVariables()) {
                    if (variable != null && variable.getId() != null) {
                        frozenVariableById.put(variable.getId(), toVariableInfo(variable));
                    }
                }
                frozenDataObjectFieldById = new LinkedHashMap<>();
                for (RuleDataObjectField field : snapshot.getDataObjectFields()) {
                    if (field != null && field.getId() != null) {
                        frozenDataObjectFieldById.put(field.getId(), field);
                    }
                }
            } catch (RuntimeException e) {
                ruleInfo.setSchemaTrust("UNVERIFIED");
                ruleInfo.setSchemaDiagnostics(Collections.singletonList(
                        "ARTIFACT_SNAPSHOT_INVALID: " + (e.getMessage() == null
                                ? "已发布制品依赖快照无法加载" : e.getMessage())));
                ruleInfo.setInputVariables(Collections.emptyList());
                ruleInfo.setOutputVariables(Collections.emptyList());
                ruleInfo.setInputDataObjects(Collections.emptyList());
                ruleInfo.setOutputDataObjects(Collections.emptyList());
                ruleInfo.setInputSchema(Collections.emptyMap());
                ruleInfo.setOutputSchema(Collections.emptyMap());
                return;
            }
        }

        for (RuleDefinitionInputField field : resolved.getInputFields()) {
            if (missingFrozenReference(field.getRefType(), field.getVarId(),
                    frozenVariableById, frozenDataObjectFieldById)) {
                diagnostics.add("FROZEN_REFERENCE_MISSING: 输入字段引用未包含在制品依赖快照中: "
                        + field.getRefType() + ":" + field.getVarId());
            }
        }
        for (RuleDefinitionOutputField field : resolved.getOutputFields()) {
            if (missingFrozenReference(field.getRefType(), field.getVarId(),
                    frozenVariableById, frozenDataObjectFieldById)) {
                diagnostics.add("FROZEN_REFERENCE_MISSING: 输出字段引用未包含在制品依赖快照中: "
                        + field.getRefType() + ":" + field.getVarId());
            }
        }
        if (!diagnostics.isEmpty()) {
            ruleInfo.setSchemaTrust("UNVERIFIED");
            ruleInfo.setSchemaDiagnostics(diagnostics);
            ruleInfo.setInputVariables(Collections.emptyList());
            ruleInfo.setOutputVariables(Collections.emptyList());
            ruleInfo.setInputDataObjects(Collections.emptyList());
            ruleInfo.setOutputDataObjects(Collections.emptyList());
            ruleInfo.setInputSchema(Collections.emptyMap());
            ruleInfo.setOutputSchema(Collections.emptyMap());
            return;
        }

        List<RuleDefinitionInputField> requestFields = new ArrayList<>();
        for (RuleDefinitionInputField field : resolved.getInputFields()) {
            if (isPublishedRequestField(field, frozenVariableById)) {
                requestFields.add(field);
            }
        }
        List<RuleDefinitionOutputField> responseFields = new ArrayList<>();
        for (RuleDefinitionOutputField field : resolved.getOutputFields()) {
            if (field != null && !resolved.isLocalOutput(field)) {
                responseFields.add(field);
            }
        }

        List<ApiDocDTO.VariableInfo> inputs = new ArrayList<>();
        List<ApiDocDTO.VariableInfo> outputs = new ArrayList<>();
        Set<Long> inputObjectIds = new LinkedHashSet<>();
        Set<Long> outputObjectIds = new LinkedHashSet<>();
        for (RuleDefinitionInputField field : requestFields) {
            inputs.add(toFrozenInputInfo(field, frozenVariableById, frozenDataObjectFieldById));
            Long objectId = objectId(field, frozenDataObjectFieldById);
            if (objectId != null) inputObjectIds.add(objectId);
        }
        for (RuleDefinitionOutputField field : responseFields) {
            outputs.add(toFrozenOutputInfo(field, frozenVariableById, frozenDataObjectFieldById));
            Long objectId = objectId(field, frozenDataObjectFieldById);
            if (objectId != null) outputObjectIds.add(objectId);
        }
        RuleSchemaService schemaService = new RuleSchemaService();
        RuleSchemaService.SchemaSnapshot schemas = schemaService.build(requestFields, responseFields);
        ruleInfo.setSchemaTrust("VERIFIED");
        ruleInfo.setSchemaDiagnostics(Collections.emptyList());
        ruleInfo.setInputVariables(inputs);
        ruleInfo.setOutputVariables(outputs);
        ruleInfo.setInputSchema(schemas.getInputSchema());
        ruleInfo.setOutputSchema(schemas.getOutputSchema());
        ruleInfo.setInputDataObjects(selectDataObjects(inputObjectIds, dataObjectById));
        ruleInfo.setOutputDataObjects(selectDataObjects(outputObjectIds, dataObjectById));
    }

    private boolean isPublishedRequestField(RuleDefinitionInputField field,
                                             Map<Long, ApiDocDTO.VariableInfo> variableById) {
        if (field == null || field.getVarId() == null || field.getRefType() == null) return false;
        if ("DATA_OBJECT".equalsIgnoreCase(field.getRefType())) return true;
        if (!"VARIABLE".equalsIgnoreCase(field.getRefType())) return false;
        ApiDocDTO.VariableInfo variable = variableById.get(field.getVarId());
        return variable != null && "INPUT".equalsIgnoreCase(variable.getVarSource());
    }

    private boolean missingFrozenReference(String refType, Long refId,
                                           Map<Long, ApiDocDTO.VariableInfo> variableById,
                                           Map<Long, RuleDataObjectField> dataObjectFieldById) {
        if (refType == null || refId == null) return false;
        if ("VARIABLE".equalsIgnoreCase(refType) || "CONSTANT".equalsIgnoreCase(refType)) {
            return !variableById.containsKey(refId);
        }
        if ("DATA_OBJECT".equalsIgnoreCase(refType)) {
            return !dataObjectFieldById.containsKey(refId);
        }
        return false;
    }

    private Long objectId(RuleDefinitionInputField field, Map<Long, RuleDataObjectField> dataObjectFieldById) {
        if (field == null || !"DATA_OBJECT".equalsIgnoreCase(field.getRefType())) return null;
        RuleDataObjectField dataField = dataObjectFieldById.get(field.getVarId());
        return dataField == null ? null : dataField.getObjectId();
    }

    private Long objectId(RuleDefinitionOutputField field, Map<Long, RuleDataObjectField> dataObjectFieldById) {
        if (field == null || !"DATA_OBJECT".equalsIgnoreCase(field.getRefType())) return null;
        RuleDataObjectField dataField = dataObjectFieldById.get(field.getVarId());
        return dataField == null ? null : dataField.getObjectId();
    }

    private List<ApiDocDTO.DataObjectInfo> selectDataObjects(Set<Long> objectIds,
                                                               Map<Long, ApiDocDTO.DataObjectInfo> dataObjectById) {
        List<ApiDocDTO.DataObjectInfo> result = new ArrayList<>();
        for (Long objectId : objectIds) {
            ApiDocDTO.DataObjectInfo info = dataObjectById.get(objectId);
            if (info != null) result.add(info);
        }
        return result;
    }

    private ApiDocDTO.VariableInfo toFrozenInputInfo(RuleDefinitionInputField field,
                                                       Map<Long, ApiDocDTO.VariableInfo> variableById,
                                                       Map<Long, RuleDataObjectField> dataObjectFieldById) {
        ApiDocDTO.VariableInfo base = variableById.get(field.getVarId());
        RuleDataObjectField dataField = dataObjectFieldById.get(field.getVarId());
        ApiDocDTO.VariableInfo info = copyVariableInfo(base);
        if (dataField != null) {
            info.setVarCode(firstNonBlank(field.getFieldName(), dataField.getVarCode()));
            info.setVarLabel(firstNonBlank(field.getFieldLabel(), dataField.getVarLabel()));
            info.setVarType(firstNonBlank(field.getFieldType(), dataField.getVarType()));
            info.setVarTypeLabel(getVarTypeLabel(info.getVarType()));
            info.setVarSource("DATA_OBJECT");
            info.setVarSourceLabel("数据对象字段");
        } else {
            info.setVarCode(firstNonBlank(field.getFieldName(), info.getVarCode()));
            info.setVarLabel(firstNonBlank(field.getFieldLabel(), info.getVarLabel()));
            info.setVarType(firstNonBlank(field.getFieldType(), info.getVarType()));
            info.setVarTypeLabel(getVarTypeLabel(info.getVarType()));
        }
        info.setId(field.getVarId());
        info.setScriptName(firstNonBlank(field.getScriptName(), info.getScriptName()));
        info.setDefaultValue(field.getDefaultValue());
        info.setRequired(field.getDefaultValue() == null || field.getDefaultValue().isBlank());
        return info;
    }

    private ApiDocDTO.VariableInfo toFrozenOutputInfo(RuleDefinitionOutputField field,
                                                        Map<Long, ApiDocDTO.VariableInfo> variableById,
                                                        Map<Long, RuleDataObjectField> dataObjectFieldById) {
        ApiDocDTO.VariableInfo base = variableById.get(field.getVarId());
        RuleDataObjectField dataField = dataObjectFieldById.get(field.getVarId());
        ApiDocDTO.VariableInfo info = copyVariableInfo(base);
        if (dataField != null) {
            info.setVarCode(firstNonBlank(field.getFieldName(), dataField.getVarCode()));
            info.setVarLabel(firstNonBlank(field.getFieldLabel(), dataField.getVarLabel()));
            info.setVarType(firstNonBlank(field.getFieldType(), dataField.getVarType()));
        } else {
            info.setVarCode(firstNonBlank(field.getFieldName(), info.getVarCode()));
            info.setVarLabel(firstNonBlank(field.getFieldLabel(), info.getVarLabel()));
            info.setVarType(firstNonBlank(field.getFieldType(), info.getVarType()));
        }
        info.setId(field.getVarId());
        info.setVarTypeLabel(getVarTypeLabel(info.getVarType()));
        info.setScriptName(firstNonBlank(field.getScriptName(), info.getScriptName()));
        info.setRequired(true);
        return info;
    }

    private ApiDocDTO.VariableInfo copyVariableInfo(ApiDocDTO.VariableInfo source) {
        ApiDocDTO.VariableInfo target = new ApiDocDTO.VariableInfo();
        if (source == null) return target;
        target.setId(source.getId());
        target.setVarCode(source.getVarCode());
        target.setVarLabel(source.getVarLabel());
        target.setVarType(source.getVarType());
        target.setVarTypeLabel(source.getVarTypeLabel());
        target.setVarSource(source.getVarSource());
        target.setVarSourceLabel(source.getVarSourceLabel());
        target.setDefaultValue(source.getDefaultValue());
        target.setValueRange(source.getValueRange());
        target.setExampleValue(source.getExampleValue());
        target.setDescription(source.getDescription());
        target.setScriptName(source.getScriptName());
        target.setRequired(source.getRequired());
        return target;
    }

    private ApiDocDTO.VariableInfo toVariableInfo(RuleVariable variable) {
        ApiDocDTO.VariableInfo info = new ApiDocDTO.VariableInfo();
        info.setId(variable.getId());
        info.setVarCode(variable.getVarCode());
        info.setVarLabel(variable.getVarLabel());
        info.setVarType(variable.getVarType());
        info.setVarTypeLabel(getVarTypeLabel(variable.getVarType()));
        info.setVarSource(variable.getVarSource());
        info.setVarSourceLabel(getVarSourceLabel(variable.getVarSource()));
        info.setDefaultValue(variable.getDefaultValue());
        info.setValueRange(variable.getValueRange());
        info.setExampleValue(variable.getExampleValue());
        info.setDescription(variable.getDescription());
        info.setScriptName(variable.getScriptName());
        return info;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private boolean openApiEnabled(String configJson) {
        if (configJson == null || configJson.isBlank()) return false;
        try {
            return JSON.parseObject(configJson).getBooleanValue("enabled");
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private Map<String, Object> openApiContractProjection(String configJson) {
        if (configJson == null || configJson.isBlank()) return Collections.emptyMap();
        try {
            String normalized = OpenApiContractCodec.validateAndNormalize(configJson);
            com.alibaba.fastjson.JSONObject object = JSON.parseObject(normalized);
            if (object == null) return Collections.emptyMap();
            com.alibaba.fastjson.JSONObject headers = object.getJSONObject("responseHeaders");
            if (headers != null) {
                object.remove("responseHeaders");
                object.put("responseHeaderNames", new ArrayList<>(headers.keySet()));
            }
            return new LinkedHashMap<>(object);
        } catch (RuntimeException ignored) {
            return Collections.emptyMap();
        }
    }

    private void addFieldCodes(Set<String> codes, String scriptName, String fieldName) {
        if (scriptName != null && !scriptName.trim().isEmpty()) {
            codes.add(scriptName);
        }
        if (fieldName != null && !fieldName.trim().isEmpty()) {
            codes.add(fieldName);
        }
    }

    private void putFieldInfo(java.util.Map<String, ApiDocDTO.VariableInfo> map, String key, ApiDocDTO.VariableInfo info) {
        if (key != null && !key.trim().isEmpty() && info != null && !map.containsKey(key)) {
            map.put(key, info);
        }
    }

    private ApiDocDTO.VariableInfo toVariableInfo(RuleDefinitionInputField field,
            java.util.Map<String, ApiDocDTO.VariableInfo> varCodeMap) {
        ApiDocDTO.VariableInfo matched = firstMatchedVarInfo(varCodeMap, field.getScriptName(), field.getFieldName());
        if (matched != null) {
            return matched;
        }
        ApiDocDTO.VariableInfo info = new ApiDocDTO.VariableInfo();
        info.setId(field.getVarId());
        info.setVarCode(field.getFieldName());
        info.setVarLabel(field.getFieldLabel());
        info.setVarType(field.getFieldType());
        info.setVarTypeLabel(getVarTypeLabel(field.getFieldType()));
        info.setVarSource(field.getRefType());
        info.setVarSourceLabel(field.getRefType());
        info.setDefaultValue(field.getDefaultValue());
        info.setScriptName(field.getScriptName());
        return info;
    }

    private ApiDocDTO.VariableInfo toVariableInfo(RuleDefinitionOutputField field,
            java.util.Map<String, ApiDocDTO.VariableInfo> varCodeMap) {
        ApiDocDTO.VariableInfo matched = firstMatchedVarInfo(varCodeMap, field.getScriptName(), field.getFieldName());
        if (matched != null) {
            return matched;
        }
        ApiDocDTO.VariableInfo info = new ApiDocDTO.VariableInfo();
        info.setId(field.getVarId());
        info.setVarCode(field.getFieldName());
        info.setVarLabel(field.getFieldLabel());
        info.setVarType(field.getFieldType());
        info.setVarTypeLabel(getVarTypeLabel(field.getFieldType()));
        info.setVarSource(field.getRefType());
        info.setVarSourceLabel(field.getRefType());
        info.setScriptName(field.getScriptName());
        return info;
    }

    private ApiDocDTO.VariableInfo firstMatchedVarInfo(java.util.Map<String, ApiDocDTO.VariableInfo> varCodeMap,
            String scriptName, String fieldName) {
        if (scriptName != null && varCodeMap.containsKey(scriptName)) {
            return varCodeMap.get(scriptName);
        }
        if (fieldName != null && varCodeMap.containsKey(fieldName)) {
            return varCodeMap.get(fieldName);
        }
        return null;
    }

    private String getModelTypeLabel(String modelType) {
        if (modelType == null) return "";
        switch (modelType) {
            case "TABLE": return "决策表";
            case "TREE": return "决策树";
            case "FLOW": return "决策流";
            case "CROSS":
            case "CROSS_TABLE": return "交叉表";
            case "SCORE":
            case "SCORE_CARD": return "评分卡";
            case "CROSS_ADV":
            case "CROSS_TABLE_ADV": return "复杂交叉表";
            case "SCORE_ADV":
            case "SCORE_CARD_ADV": return "复杂评分卡";
            case "RULE_SET": return "规则集";
            case "SCRIPT": return "QL脚本";
            default: return modelType;
        }
    }

    private String getStatusLabel(Integer status) {
        if (status == null) return "";
        return status == 1 ? "启用" : "禁用";
    }

    private String getVarTypeLabel(String varType) {
        if (varType == null) return "";
        switch (varType) {
            case "STRING": return "字符串";
            case "INTEGER": return "整数";
            case "DECIMAL": return "小数";
            case "BOOLEAN": return "布尔";
            case "DATE": return "日期";
            case "DATETIME": return "日期时间";
            case "LIST": return "列表";
            case "MAP": return "对象";
            default: return varType;
        }
    }

    private String getVarSourceLabel(String varSource) {
        if (varSource == null) return "";
        switch (varSource) {
            case "INPUT": return "输入参数";
            case "COMPUTED": return "计算变量";
            case "DERIVED": return "衍生变量";
            case "CONSTANT": return "常量";
            case "DB": return "数据库查询";
            case "API": return "API调用";
            case "LIST": return "名单查询";
            default: return varSource;
        }
    }

    private String getObjectTypeLabel(String objectType) {
        if (objectType == null) return "";
        switch (objectType) {
            case "OBJECT": return "对象";
            case "ARRAY": return "数组";
            default: return objectType;
        }
    }

    private String getSourceTypeLabel(String sourceType) {
        if (sourceType == null) return "";
        switch (sourceType) {
            case "JAVA": return "Java实体";
            case "JSON": return "JSON Schema";
            default: return sourceType;
        }
    }

    private String getImplTypeLabel(String implType) {
        if (implType == null) return "";
        switch (implType) {
            case "SCRIPT": return "QLExpress脚本";
            case "JAVA": return "Java类";
            case "BEAN": return "Spring Bean";
            default: return implType;
        }
    }

    private String getParentVarCode(Long parentFieldId) {
        if (parentFieldId == null) return null;
        RuleDataObjectField parent = fieldMapper.selectById(parentFieldId);
        return parent != null ? parent.getVarCode() : null;
    }
}
