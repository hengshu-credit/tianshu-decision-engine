package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.function.AggregateBuiltinFunctionRegistry;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RulePublished;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Resource
    private RuleRuntimeInvoker runtimeRuleInvoker;

    @Resource
    private ExecutionParameterBinder executionParameterBinder;

    public RuleResult testExecute(Long definitionId, Map<String, Object> params) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则定义不存在");
            return r;
        }

        RuleDefinitionContent content = definitionService.getContent(definitionId);
        if (content == null || content.getCompileStatus() == null || content.getCompileStatus() != 1) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则尚未编译成功，请先编译");
            return r;
        }

        String funcPrefix = prepareProjectFunctions(definition.getProjectId(), true);
        String fullScript = funcPrefix.isEmpty()
                ? content.getCompiledScript()
                : funcPrefix + "\n" + content.getCompiledScript();
        VariableResolveOptions resolveOptions = withDefinitionInputFields(VariableResolveOptions.defaults(), definitionId);
        Map<String, Object> boundParams = bindDefinitionInputs(definitionId, params);
        Map<String, Object> executeParams = variableSourceResolver.resolve(definition.getProjectId(), boundParams, resolveOptions);
        String projectCode = null;
        if (definition.getProjectId() != null) {
            RuleProject project = projectService.getById(definition.getProjectId());
            if (project != null) {
                projectCode = project.getProjectCode();
            }
        }
        runtimeRuleInvoker.enter(definition.getRuleCode(), definition.getProjectId(), projectCode, executeParams);
        RuleResult result;
        try {
            result = qlExpressEngine.execute(fullScript, executeParams, true);
        } finally {
            runtimeRuleInvoker.exit();
        }

        RuleExecutionLog log = new RuleExecutionLog();
        log.setRuleCode(definition.getRuleCode());
        log.setProjectCode(projectCode);
        log.setRuleVersion(definition.getCurrentVersion());
        log.setModelType(definition.getModelType());
        log.setSource("SERVER");
        log.setInputParams(toJsonSafely(executeParams));
        log.setOutputResult(toJsonSafely(result.getResult()));
        log.setSuccess(result.isSuccess() ? 1 : 0);
        log.setErrorMessage(result.getErrorMessage());
        log.setExecuteTimeMs(result.getExecuteTimeMs());
        if (result.getTraces() != null) {
            log.setTraceInfo(toJsonSafely(result.getTraces()));
        }
        logService.save(log);
        billingService.recordEngineExecution(definition, result.isSuccess(), result.getExecuteTimeMs(), result.getErrorMessage());

        return result;
    }

    public RuleResult executePublished(RulePublished published, Map<String, Object> params,
                                       Long projectId, String clientAppName) {
        return executePublishedWithOptions(published, params, projectId, clientAppName,
                VariableResolveOptions.defaults(), "CLIENT_SERVER").getResult();
    }

    public ExecutionOutcome executePublishedWithOptions(RulePublished published, Map<String, Object> params,
                                                        Long projectId, String clientAppName,
                                                        VariableResolveOptions resolveOptions,
                                                        String source) {
        if (published == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("已发布规则不存在");
            return new ExecutionOutcome(r, Collections.emptyMap());
        }

        RuleDefinition definition = definitionService.getById(published.getDefinitionId());
        Long executionProjectId = projectId != null ? projectId : (definition == null ? null : definition.getProjectId());
        prepareProjectFunctions(executionProjectId, false);

        Map<String, Object> safeParams = bindDefinitionInputs(published.getDefinitionId(), params);
        VariableResolveOptions effectiveOptions = withDefinitionInputFields(resolveOptions, published.getDefinitionId());
        Map<String, Object> executeParams = variableSourceResolver.resolve(executionProjectId, safeParams, effectiveOptions);
        String projectCode = published.getProjectCode();
        if (projectCode == null && executionProjectId != null) {
            RuleProject project = projectService.getById(executionProjectId);
            if (project != null) {
                projectCode = project.getProjectCode();
            }
        }
        runtimeRuleInvoker.enter(published.getRuleCode(), executionProjectId, projectCode, executeParams);
        RuleResult result;
        try {
            result = qlExpressEngine.execute(published.getCompiledScript(), executeParams, true);
        } finally {
            runtimeRuleInvoker.exit();
        }

        RuleExecutionLog log = new RuleExecutionLog();
        log.setRuleCode(published.getRuleCode());
        log.setProjectCode(projectCode);
        log.setRuleVersion(published.getVersion());
        log.setModelType(published.getModelType());
        log.setSource(source == null ? "CLIENT_SERVER" : source);
        log.setClientAppName(clientAppName);
        log.setInputParams(toJsonSafely(executeParams));
        log.setOutputResult(toJsonSafely(result.getResult()));
        log.setSuccess(result.isSuccess() ? 1 : 0);
        log.setErrorMessage(result.getErrorMessage());
        log.setExecuteTimeMs(result.getExecuteTimeMs());
        if (result.getTraces() != null) {
            log.setTraceInfo(toJsonSafely(result.getTraces()));
        }
        logService.save(log);
        billingService.recordEngineExecution(definition, result.isSuccess(), result.getExecuteTimeMs(), result.getErrorMessage());

        return new ExecutionOutcome(result, executeParams);
    }

    private VariableResolveOptions withDefinitionInputFields(VariableResolveOptions options, Long definitionId) {
        VariableResolveOptions effective = options == null ? VariableResolveOptions.defaults() : options;
        if (definitionId == null || (effective.getRequiredScriptNames() != null && !effective.getRequiredScriptNames().isEmpty())) {
            return effective;
        }
        List<RuleDefinitionInputField> inputFields = definitionService.listInputFields(definitionId);
        if (inputFields == null || inputFields.isEmpty()) {
            return effective;
        }
        Set<String> names = new LinkedHashSet<>();
        for (RuleDefinitionInputField field : inputFields) {
            if (field != null && field.getScriptName() != null && !field.getScriptName().trim().isEmpty()) {
                names.add(field.getScriptName().trim());
            }
        }
        if (!names.isEmpty()) {
            effective.setRequiredScriptNames(names);
        }
        return effective;
    }

    private Map<String, Object> bindDefinitionInputs(Long definitionId, Map<String, Object> params) {
        Map<String, Object> safeParams = params == null ? Collections.emptyMap() : params;
        List<RuleDefinitionInputField> fields = definitionService.listInputFields(definitionId);
        return executionParameterBinder.bindRuleInputs(fields, safeParams);
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
        runtimeRuleInvoker.register(qlExpressEngine.getRunner());

        if (!includeScriptPrefix) {
            return "";
        }
        List<RuleFunction> scriptFuncs = allFuncs.stream()
                .filter(f -> "SCRIPT".equals(f.getImplType())).collect(Collectors.toList());
        return functionRegistrar.buildScriptFunctionPrefix(scriptFuncs);
    }

    private String toJsonSafely(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.toJSONString(value);
        } catch (StackOverflowError e) {
            return "{\"error\":\"JSON_SERIALIZE_STACK_OVERFLOW\"}";
        } catch (Exception e) {
            return "{\"error\":\"JSON_SERIALIZE_FAILED\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static class ExecutionOutcome {
        private final RuleResult result;
        private final Map<String, Object> executeParams;

        public ExecutionOutcome(RuleResult result, Map<String, Object> executeParams) {
            this.result = result;
            this.executeParams = executeParams;
        }

        public RuleResult getResult() {
            return result;
        }

        public Map<String, Object> getExecuteParams() {
            return executeParams;
        }
    }
}
