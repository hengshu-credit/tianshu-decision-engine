package com.bjjw.rule.server.service;

import com.bjjw.rule.core.function.AggregateBuiltinFunctionRegistry;
import com.bjjw.rule.core.engine.QLExpressEngine;
import com.bjjw.rule.model.dto.RuleResult;
import com.bjjw.rule.model.entity.RuleDefinition;
import com.bjjw.rule.model.entity.RuleDefinitionContent;
import com.bjjw.rule.model.entity.RuleExecutionLog;
import com.bjjw.rule.model.entity.RuleFunction;
import com.bjjw.rule.model.entity.RulePublished;
import com.bjjw.rule.model.entity.RuleProject;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RuleExecuteService {

    @Resource
    private QLExpressEngine qlExpressEngine;

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleProjectService projectService;

    @Resource
    private RuleExecutionLogService logService;

    @Resource
    private RuleFunctionService functionService;

    @Resource
    private FunctionRegistrar functionRegistrar;

    @Resource
    private RuleBillingService billingService;

    @Resource
    private VariableSourceResolver variableSourceResolver;

    public RuleResult testExecute(Long definitionId, Map<String, Object> params) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则定义不存在");
            return r;
        }

        RuleDefinitionContent content = definitionService.getContent(definitionId);
        if (content == null || content.getCompileStatus() != 1) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则尚未编译成功，请先编译");
            return r;
        }

        // 加载项目自定义函数并注册到引擎
        String funcPrefix = prepareProjectFunctions(definition.getProjectId(), true);

        String fullScript = funcPrefix.isEmpty()
                ? content.getCompiledScript()
                : funcPrefix + "\n" + content.getCompiledScript();
        Map<String, Object> executeParams = variableSourceResolver.resolve(definition.getProjectId(), params);
        RuleResult result = qlExpressEngine.execute(fullScript, executeParams, true);

        RuleExecutionLog log = new RuleExecutionLog();
        log.setRuleCode(definition.getRuleCode());
        if (definition.getProjectId() != null) {
            RuleProject project = projectService.getById(definition.getProjectId());
            if (project != null) {
                log.setProjectCode(project.getProjectCode());
            }
        }
        log.setRuleVersion(definition.getCurrentVersion());
        log.setModelType(definition.getModelType());
        log.setSource("SERVER");
        log.setInputParams(JSON.toJSONString(executeParams));
        log.setOutputResult(JSON.toJSONString(result.getResult()));
        log.setSuccess(result.isSuccess() ? 1 : 0);
        log.setErrorMessage(result.getErrorMessage());
        log.setExecuteTimeMs(result.getExecuteTimeMs());
        if (result.getTraces() != null) {
            log.setTraceInfo(JSON.toJSONString(result.getTraces()));
        }
        logService.save(log);
        billingService.recordEngineExecution(definition, result.isSuccess(), result.getExecuteTimeMs(), result.getErrorMessage());

        return result;
    }

    public RuleResult executePublished(RulePublished published, Map<String, Object> params,
                                       Long projectId, String clientAppName) {
        if (published == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("瑙勫垯鏈壘鍒?");
            return r;
        }

        RuleDefinition definition = definitionService.getById(published.getDefinitionId());
        Long executionProjectId = projectId != null ? projectId : (definition == null ? null : definition.getProjectId());
        prepareProjectFunctions(executionProjectId, false);

        Map<String, Object> safeParams = params == null ? Collections.emptyMap() : params;
        Map<String, Object> executeParams = variableSourceResolver.resolve(executionProjectId, safeParams);
        RuleResult result = qlExpressEngine.execute(published.getCompiledScript(), executeParams, true);

        RuleExecutionLog log = new RuleExecutionLog();
        log.setRuleCode(published.getRuleCode());
        log.setProjectCode(published.getProjectCode());
        if (log.getProjectCode() == null && executionProjectId != null) {
            RuleProject project = projectService.getById(executionProjectId);
            if (project != null) {
                log.setProjectCode(project.getProjectCode());
            }
        }
        log.setRuleVersion(published.getVersion());
        log.setModelType(published.getModelType());
        log.setSource("CLIENT_SERVER");
        log.setClientAppName(clientAppName);
        log.setInputParams(JSON.toJSONString(executeParams));
        log.setOutputResult(JSON.toJSONString(result.getResult()));
        log.setSuccess(result.isSuccess() ? 1 : 0);
        log.setErrorMessage(result.getErrorMessage());
        log.setExecuteTimeMs(result.getExecuteTimeMs());
        if (result.getTraces() != null) {
            log.setTraceInfo(JSON.toJSONString(result.getTraces()));
        }
        logService.save(log);
        billingService.recordEngineExecution(definition, result.isSuccess(), result.getExecuteTimeMs(), result.getErrorMessage());

        return result;
    }

    private String prepareProjectFunctions(Long projectId, boolean includeScriptPrefix) {
        List<RuleFunction> allFuncs = functionService.listByProject(projectId);
        List<RuleFunction> javaFuncs = allFuncs.stream()
                .filter(f -> "JAVA".equals(f.getImplType())).collect(Collectors.toList());
        List<RuleFunction> beanFuncs = allFuncs.stream()
                .filter(f -> "BEAN".equals(f.getImplType())).collect(Collectors.toList());

        functionRegistrar.registerJavaFunctions(javaFuncs, qlExpressEngine.getRunner());
        functionRegistrar.registerBeanFunctions(beanFuncs, qlExpressEngine.getRunner());
        AggregateBuiltinFunctionRegistry.register(qlExpressEngine.getRunner());

        if (!includeScriptPrefix) {
            return "";
        }
        List<RuleFunction> scriptFuncs = allFuncs.stream()
                .filter(f -> "SCRIPT".equals(f.getImplType())).collect(Collectors.toList());
        return functionRegistrar.buildScriptFunctionPrefix(scriptFuncs);
    }
}
