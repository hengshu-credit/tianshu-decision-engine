package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.core.engine.RuleTerminationSignal;
import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.core.function.AggregateBuiltinFunctionRegistry;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    @Resource
    private RuleCompileService compileService;

    @Resource
    private RuleFieldAnalyzer ruleFieldAnalyzer;

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

        return executeTest(definition, content.getCompiledScript(), content.getModelJson(),
                definitionService.listInputFields(definitionId), params);
    }

    public RuleResult testExecutePreview(Long definitionId, String modelJson, String modelType,
                                         Map<String, Object> params) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            return failedResult("规则定义不存在");
        }
        CompileResult compileResult = compileService.compilePreview(definitionId, modelJson, modelType);
        if (!compileResult.isSuccess()) {
            return failedResult(compileResult.getErrorMessage());
        }
        RuleFieldAnalyzer.ResolvedFields fields = ruleFieldAnalyzer.resolveFields(
                definitionId, modelJson, modelType, definition.getProjectId());
        return executeTest(definition, compileResult.getCompiledScript(), modelJson,
                fields.getInputFields(), params);
    }

    private RuleResult executeTest(RuleDefinition definition, String compiledScript, String modelJson,
                                   List<RuleDefinitionInputField> inputFields,
                                   Map<String, Object> params) {
        String funcPrefix = prepareProjectFunctions(definition.getProjectId(), true);
        String fullScript = funcPrefix.isEmpty() ? compiledScript : funcPrefix + "\n" + compiledScript;
        VariableResolveOptions resolveOptions = withInputFields(
                VariableResolveOptions.defaults(), inputFields, modelJson, definition.getModelType());
        Map<String, Object> executeParams = bindInputs(inputFields, params, resolveOptions);
        Map<String, Object> originalInput = snapshotMap(executeParams);
        String projectCode = null;
        if (definition.getProjectId() != null) {
            RuleProject project = projectService.getById(definition.getProjectId());
            if (project != null) {
                projectCode = project.getProjectCode();
            }
        }
        runtimeRuleInvoker.enter(definition, projectCode, executeParams, originalInput, true, modelJson);
        long executionStart = System.currentTimeMillis();
        RuleResult result = new RuleResult();
        try {
            variableSourceResolver.resolveInto(definition.getProjectId(), executeParams, resolveOptions);
            RuntimeContextBridge.replaceSourceStates(resolveOptions.getSourceStates());
            result = qlExpressEngine.execute(fullScript, executeParams, true);
        } catch (RuleTerminationSignal e) {
            result.setSuccess(true);
            result.setResult(runtimeRuleInvoker.collectTerminationResult());
        } catch (RuntimeException e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        } finally {
            result.setExecuteTimeMs(System.currentTimeMillis() - executionStart);
            collectDeclaredOutputsIfNeeded(result, definition.getModelType());
            runtimeRuleInvoker.completeRoot(result);
            runtimeRuleInvoker.exit();
        }

        RuleExecutionLog log = new RuleExecutionLog();
        log.setTraceId(result.getTraceId());
        log.setRuleCode(definition.getRuleCode());
        log.setProjectCode(projectCode);
        log.setRuleVersion(definition.getCurrentVersion());
        log.setModelType(definition.getModelType());
        log.setSource("SERVER");
        log.setInputParams(toJsonSafely(originalInput));
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

    private RuleResult failedResult(String message) {
        RuleResult result = new RuleResult();
        result.setSuccess(false);
        result.setErrorMessage(message);
        return result;
    }

    private void collectDeclaredOutputsIfNeeded(RuleResult result, String modelType) {
        boolean visualModel = modelType != null && !"SCRIPT".equalsIgnoreCase(modelType.trim());
        if (result == null || !result.isSuccess() || (!visualModel && result.getResult() != null)) {
            return;
        }
        Map<String, Object> outputs = runtimeRuleInvoker.collectTerminationResult();
        if (!outputs.isEmpty()) {
            result.setResult(outputs);
        }
    }

    public RuleResult executePublished(RulePublished published, Map<String, Object> params,
                                       Long projectId, String clientAppName) {
        return executePublished(published, params, projectId, clientAppName, null);
    }

    public RuleResult executePublished(RulePublished published, Map<String, Object> params,
                                       Long projectId, String clientAppName,
                                       ProjectAuthContext authContext) {
        return executePublished(published, params, projectId, clientAppName, authContext, true, true);
    }

    public RuleResult executePublished(RulePublished published, Map<String, Object> params,
                                       Long projectId, String clientAppName,
                                       ProjectAuthContext authContext, boolean collectTrace,
                                       boolean recordTrace) {
        return executePublishedWithOptions(published, params, projectId, clientAppName,
                VariableResolveOptions.defaults(), "CLIENT_SERVER", authContext,
                collectTrace, recordTrace).getResult();
    }

    public ExecutionOutcome executePublishedWithOptions(RulePublished published, Map<String, Object> params,
                                                        Long projectId, String clientAppName,
                                                        VariableResolveOptions resolveOptions,
                                                        String source) {
        return executePublishedWithOptions(published, params, projectId, clientAppName,
                resolveOptions, source, null);
    }

    public ExecutionOutcome executePublishedWithOptions(RulePublished published, Map<String, Object> params,
                                                        Long projectId, String clientAppName,
                                                        VariableResolveOptions resolveOptions, String source,
                                                        ProjectAuthContext authContext) {
        return executePublishedWithOptions(published, params, projectId, clientAppName,
                resolveOptions, source, authContext, true, true);
    }

    public ExecutionOutcome executePublishedWithOptions(RulePublished published, Map<String, Object> params,
                                                        Long projectId, String clientAppName,
                                                        VariableResolveOptions resolveOptions, String source,
                                                        ProjectAuthContext authContext,
                                                        boolean collectTrace, boolean recordTrace) {
        if (published == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("已发布规则不存在");
            return new ExecutionOutcome(r, Collections.emptyMap());
        }

        RuleDefinition definition = definitionService.getById(published.getDefinitionId());
        Long executionProjectId = projectId != null ? projectId : (definition == null ? null : definition.getProjectId());
        prepareProjectFunctions(executionProjectId, false);

        List<RuleDefinitionInputField> inputFields = definitionService.listInputFields(published.getDefinitionId());
        VariableResolveOptions effectiveOptions = withInputFields(resolveOptions, inputFields,
                published.getModelJson(), published.getModelType());
        Map<String, Object> executeParams = bindInputs(inputFields, params, effectiveOptions);
        Map<String, Object> originalInput = snapshotMap(executeParams);
        String projectCode = published.getProjectCode();
        if (projectCode == null && executionProjectId != null) {
            RuleProject project = projectService.getById(executionProjectId);
            if (project != null) {
                projectCode = project.getProjectCode();
            }
        }
        RuleDefinition executionDefinition = definition == null
                ? publishedDefinition(published, executionProjectId) : definition;
        runtimeRuleInvoker.enter(executionDefinition, executionProjectId, projectCode,
                executeParams, originalInput, false, published.getModelJson());
        long executionStart = System.currentTimeMillis();
        RuleResult result = new RuleResult();
        try {
            variableSourceResolver.resolveInto(executionProjectId, executeParams, effectiveOptions);
            RuntimeContextBridge.replaceSourceStates(effectiveOptions.getSourceStates());
            result = qlExpressEngine.execute(published.getCompiledScript(), executeParams, collectTrace);
        } catch (RuleTerminationSignal e) {
            result.setSuccess(true);
            result.setResult(runtimeRuleInvoker.collectTerminationResult());
        } catch (RuntimeException e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        } finally {
            result.setExecuteTimeMs(System.currentTimeMillis() - executionStart);
            collectDeclaredOutputsIfNeeded(result, published.getModelType());
            runtimeRuleInvoker.completeRoot(result);
            runtimeRuleInvoker.exit();
        }

        RuleExecutionLog log = new RuleExecutionLog();
        log.setTraceId(result.getTraceId());
        log.setRuleCode(published.getRuleCode());
        log.setProjectCode(projectCode);
        log.setRuleVersion(published.getVersion());
        log.setModelType(published.getModelType());
        log.setSource(source == null ? "CLIENT_SERVER" : source);
        log.setClientAppName(clientAppName);
        applyAuthAttribution(log, authContext);
        log.setInputParams(toJsonSafely(originalInput));
        log.setOutputResult(toJsonSafely(result.getResult()));
        log.setSuccess(result.isSuccess() ? 1 : 0);
        log.setErrorMessage(result.getErrorMessage());
        log.setExecuteTimeMs(result.getExecuteTimeMs());
        if (recordTrace && result.getTraces() != null) {
            log.setTraceInfo(toJsonSafely(result.getTraces()));
        }
        if (!isExperimentSource(source)) {
            logService.save(log);
        }
        billingService.recordEngineExecution(definition, result.isSuccess(), result.getExecuteTimeMs(),
                result.getErrorMessage(), authContext);

        return new ExecutionOutcome(result, executeParams);
    }

    private RuleDefinition publishedDefinition(RulePublished published, Long executionProjectId) {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(published.getDefinitionId());
        definition.setProjectId(executionProjectId);
        definition.setRuleCode(published.getRuleCode());
        definition.setRuleName(published.getRuleCode());
        definition.setModelType(published.getModelType());
        definition.setScope(executionProjectId != null && executionProjectId > 0 ? "PROJECT" : "GLOBAL");
        return definition;
    }

    private boolean isExperimentSource(String source) {
        return source != null && source.startsWith("EXPERIMENT_");
    }

    private void applyAuthAttribution(RuleExecutionLog log, ProjectAuthContext authContext) {
        if (authContext == null) return;
        log.setAuthId(authContext.getAuthId());
        log.setAuthCode(authContext.getAuthCode());
        log.setAuthType(authContext.getAuthType());
        log.setTokenId(authContext.getTokenId());
        log.setTokenCode(authContext.getTokenCode());
        log.setAuthPhase(authContext.getAuthPhase());
    }

    private VariableResolveOptions withInputFields(VariableResolveOptions options,
                                                   List<RuleDefinitionInputField> inputFields,
                                                   String modelJson, String modelType) {
        VariableResolveOptions effective = options == null ? VariableResolveOptions.defaults() : options;
        if (effective.getStatusReferenceKeys() == null) {
            effective.setStatusReferenceKeys(SourceStatusUsage.scan(modelJson));
        }
        effective.getSourceStates().clear();
        if (effective.getRequiredScriptNames() != null) {
            return effective;
        }
        Set<String> names = new LinkedHashSet<>();
        if (inputFields != null) {
            for (RuleDefinitionInputField field : inputFields) {
                addRequiredScriptName(names, field);
            }
        }
        if (ruleFieldAnalyzer != null && modelJson != null && !modelJson.trim().isEmpty()) {
            for (RuleDefinitionInputField field : ruleFieldAnalyzer.extractDirectModelInputFields(modelJson, modelType)) {
                addRequiredScriptName(names, field);
            }
        }
        effective.setRequiredScriptNames(names);
        return effective;
    }

    private void addRequiredScriptName(Set<String> names, RuleDefinitionInputField field) {
        if (field != null && field.getScriptName() != null && !field.getScriptName().trim().isEmpty()) {
            names.add(field.getScriptName().trim());
        }
    }

    private Map<String, Object> bindInputs(List<RuleDefinitionInputField> fields, Map<String, Object> params,
                                           VariableResolveOptions options) {
        Map<String, Object> safeParams = params == null ? Collections.emptyMap() : params;
        return executionParameterBinder.bindRuleInputs(fields, safeParams, options);
    }

    private String prepareProjectFunctions(Long projectId, boolean includeScriptPrefix) {
        List<RuleFunction> allFuncs = functionService.listByProject(projectId);
        List<RuleFunction> javaFuncs = allFuncs.stream()
                .filter(f -> "JAVA".equals(f.getImplType())).collect(Collectors.toList());
        List<RuleFunction> beanFuncs = allFuncs.stream()
                .filter(f -> "BEAN".equals(f.getImplType())).collect(Collectors.toList());

        functionRegistrar.registerJavaFunctions(javaFuncs, qlExpressEngine.getRunner());
        functionRegistrar.registerBeanFunctions(beanFuncs, qlExpressEngine.getRunner());
        functionRegistrar.registerServerFunctions(qlExpressEngine.getRunner());
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

    private Map<String, Object> snapshotMap(Map<String, Object> source) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (source == null) {
            return snapshot;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            snapshot.put(entry.getKey(), snapshotValue(entry.getValue()));
        }
        return snapshot;
    }

    private Object snapshotValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                snapshot.put(String.valueOf(entry.getKey()), snapshotValue(entry.getValue()));
            }
            return snapshot;
        }
        if (value instanceof List) {
            List<Object> snapshot = new ArrayList<>();
            for (Object item : (List<?>) value) {
                snapshot.add(snapshotValue(item));
            }
            return snapshot;
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> snapshot = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) {
                snapshot.add(snapshotValue(Array.get(value, i)));
            }
            return snapshot;
        }
        return value;
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
