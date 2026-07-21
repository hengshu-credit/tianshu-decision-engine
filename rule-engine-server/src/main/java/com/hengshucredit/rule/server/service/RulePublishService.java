package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.model.dto.RulePushMessage;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionVersion;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionVersionMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.openapi.OpenApiContract;
import com.hengshucredit.rule.server.openapi.OpenApiContractCodec;
import com.hengshucredit.rule.server.openapi.OpenRequestMapper;
import com.hengshucredit.rule.server.publish.RulePushService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RulePublishService {

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleProjectService projectService;

    @Resource
    private RuleDefinitionContentMapper contentMapper;

    @Resource
    private RuleDefinitionVersionMapper versionMapper;

    @Resource
    private RulePublishedMapper publishedMapper;

    @Resource
    private RulePushService pushService;

    @Resource
    private RuleCompileService compileService;

    @Resource
    private RuleFunctionService functionService;

    @Resource
    private FunctionRegistrar functionRegistrar;

    @Resource
    private RuleCallCycleService ruleCallCycleService;

    @Resource
    private RuleReferenceIntegrityService referenceIntegrityService;

    /**
     * 将 SCRIPT 函数定义拼接到编译脚本前面，使客户端同步后可直接执行
     */
    private String buildFullScript(String compiledScript, Long projectId) {
        List<RuleFunction> allFuncs = functionService.listByProject(projectId);
        List<RuleFunction> scriptFuncs = allFuncs.stream()
                .filter(f -> "SCRIPT".equals(f.getImplType()))
                .collect(Collectors.toList());
        String funcPrefix = functionRegistrar.buildScriptFunctionPrefix(scriptFuncs);
        if (funcPrefix.isEmpty()) {
            return compiledScript;
        }
        return funcPrefix + compiledScript;
    }

    @Transactional
    public String publish(Long definitionId, String changeLog) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            return "规则定义不存在";
        }

        RuleDefinitionContent content = definitionService.getContent(definitionId);
        if (content == null) {
            return "规则内容不存在";
        }

        String openApiConfigJson;
        try {
            openApiConfigJson = validateOpenApiContract(definitionId, content.getOpenApiConfigJson());
        } catch (IllegalArgumentException e) {
            return "开放接口配置无效: " + e.getMessage();
        }

        if (referenceIntegrityService != null) {
            try {
                referenceIntegrityService.assertValid(definitionId, definition.getProjectId(), content.getModelJson());
            } catch (IllegalArgumentException e) {
                return e.getMessage();
            }
        }

        String cycleError = ruleCallCycleService.validateNoCycle(definitionId, content.getModelJson());
        if (cycleError != null) {
            return cycleError;
        }

        if (content.getCompileStatus() != 1) {
            CompileResult compileResult = compileService.compile(definitionId);
            if (!compileResult.isSuccess()) {
                return "编译失败: " + compileResult.getErrorMessage();
            }
            content = definitionService.getContent(definitionId);
            try {
                openApiConfigJson = validateOpenApiContract(definitionId, content.getOpenApiConfigJson());
            } catch (IllegalArgumentException e) {
                return "开放接口配置无效: " + e.getMessage();
            }
        }

        int newVersion = (definition.getPublishedVersion() != null ? definition.getPublishedVersion() : 0) + 1;

        String fullScript = buildFullScript(content.getCompiledScript(), definition.getProjectId());

        String projectCode = null;
        if (definition.getProjectId() != null) {
            RuleProject project = projectService.getById(definition.getProjectId());
            if (project != null) {
                projectCode = project.getProjectCode();
            }
        }

        RuleDefinitionVersion version = new RuleDefinitionVersion();
        version.setDefinitionId(definitionId);
        version.setVersion(newVersion);
        version.setModelJson(content.getModelJson());
        version.setCompiledScript(content.getCompiledScript());
        version.setCompiledType(content.getCompiledType());
        version.setOpenApiConfigJson(openApiConfigJson);
        version.setChangeLog(changeLog);
        versionMapper.insert(version);

        RulePublished existing = publishedMapper.selectOne(
                new LambdaQueryWrapper<RulePublished>().eq(RulePublished::getRuleCode, definition.getRuleCode()));
        if (existing != null) {
            existing.setVersion(newVersion);
            existing.setModelType(definition.getModelType());
            existing.setCompiledScript(fullScript);
            existing.setCompiledType(content.getCompiledType());
            existing.setModelJson(content.getModelJson());
            existing.setProjectCode(projectCode);
            existing.setStatus(1);
            existing.setPublishTime(LocalDateTime.now());
            existing.setOfflineTime(null);
            publishedMapper.updateById(existing);
        } else {
            RulePublished published = new RulePublished();
            published.setRuleCode(definition.getRuleCode());
            published.setDefinitionId(definitionId);
            published.setProjectCode(projectCode);
            published.setVersion(newVersion);
            published.setModelType(definition.getModelType());
            published.setCompiledScript(fullScript);
            published.setCompiledType(content.getCompiledType());
            published.setModelJson(content.getModelJson());
            published.setStatus(1);
            publishedMapper.insert(published);
        }
        publishedMapper.updateOpenApiConfigByDefinitionId(definitionId, openApiConfigJson);

        definition.setPublishedVersion(newVersion);
        definition.setStatus(1);
        definitionService.updateById(definition);

        RulePushMessage pushMessage = new RulePushMessage();
        pushMessage.setRuleCode(definition.getRuleCode());
        pushMessage.setVersion(newVersion);
        pushMessage.setModelType(definition.getModelType());
        pushMessage.setCompiledScript(fullScript);
        pushMessage.setCompiledType(content.getCompiledType());
        pushMessage.setModelJson(content.getModelJson());
        List<RuleDefinitionOutputField> outputFields = definitionService.listOutputFields(definitionId);
        if (outputFields != null) {
            pushMessage.setOutputScriptNames(outputFields.stream()
                    .map(RuleDefinitionOutputField::getScriptName)
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toList()));
        }
        pushMessage.setProjectCode(projectCode);
        pushMessage.setPublishTime(System.currentTimeMillis());
        pushMessage.setAction("PUBLISH");
        pushService.push(pushMessage);

        return null;
    }

    private String validateOpenApiContract(Long definitionId, String configJson) {
        String normalized = OpenApiContractCodec.validateAndNormalize(configJson);
        if (normalized == null) return null;
        OpenApiContract contract = OpenApiContractCodec.parse(normalized);
        if (!contract.isEnabled() || contract.getRequestMappings().isEmpty()) return normalized;
        Set<String> availableReferences = new HashSet<>();
        List<RuleDefinitionInputField> fields = definitionService.listInputFields(definitionId);
        if (fields != null) {
            for (RuleDefinitionInputField field : fields) {
                if (Integer.valueOf(0).equals(field.getStatus())
                        || field.getScriptName() == null || field.getScriptName().trim().isEmpty()) {
                    continue;
                }
                String reference = OpenRequestMapper.referenceKey(field.getRefType(), field.getVarId());
                if (reference != null) availableReferences.add(reference);
            }
        }
        OpenApiContractCodec.validateRequestReferences(contract, availableReferences);
        return normalized;
    }

    @Transactional
    public String unpublish(Long definitionId) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) return "规则定义不存在";

        RulePublished published = publishedMapper.selectOne(
                new LambdaQueryWrapper<RulePublished>().eq(RulePublished::getRuleCode, definition.getRuleCode()));
        if (published != null) {
            published.setStatus(0);
            published.setOfflineTime(LocalDateTime.now());
            publishedMapper.updateById(published);
        }

        definition.setStatus(2);
        definitionService.updateById(definition);

        RulePushMessage pushMessage = new RulePushMessage();
        pushMessage.setRuleCode(definition.getRuleCode());
        if (published != null) {
            pushMessage.setProjectCode(published.getProjectCode());
        }
        pushMessage.setAction("UNPUBLISH");
        pushMessage.setPublishTime(System.currentTimeMillis());
        pushService.push(pushMessage);

        return null;
    }
}
