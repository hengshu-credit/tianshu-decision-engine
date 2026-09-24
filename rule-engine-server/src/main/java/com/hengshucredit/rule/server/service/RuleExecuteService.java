package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.core.engine.RuleTerminationSignal;
import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private DecisionExecutionPersistence executionPersistence;

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

    @Resource
    private ArtifactRuntimeSnapshotService artifactRuntimeSnapshotService;

    @Resource
    private DataObjectFieldReferenceResolver dataObjectFieldReferenceResolver;

    @Resource
    private com.hengshucredit.rule.server.derived.DerivedVariableService derivedVariableService;

    public RuleResult testExecute(Long definitionId, Map<String, Object> params) {
        return testExecute(definitionId, params, null);
    }

    public RuleResult testExecute(Long definitionId, Map<String, Object> params,
                                  Long executionProjectId) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则定义不存在");
            return r;
        }

        if (!isValidTestProject(definition, executionProjectId)) {
            return failedResult("当前规则未关联到所选项目，无法按该项目执行测试");
        }

        RuleDefinitionContent content = definitionService.getContent(definitionId);
        if (content == null || content.getCompileStatus() == null || content.getCompileStatus() != 1) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则尚未编译成功，请先编译");
            return r;
        }

        return executeTest(definition, content.getCompiledScript(), content.getModelJson(),
                definition.getModelType(),
                definitionService.listInputFields(definitionId), params,
                effectiveProjectId(definition, executionProjectId));
    }

    public RuleResult testExecutePreview(Long definitionId, String modelJson, String modelType,
                                         Map<String, Object> params) {
        return testExecutePreview(definitionId, modelJson, modelType, params,
                null);
    }

    public RuleResult testExecutePreview(Long definitionId, String modelJson, String modelType,
                                         Map<String, Object> params,
                                         Long executionProjectId) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            return failedResult("规则定义不存在");
        }
        if (!isValidTestProject(definition, executionProjectId)) {
            return failedResult("当前规则未关联到所选项目，无法按该项目执行测试");
        }
        Long effectiveProjectId = effectiveProjectId(
                definition, executionProjectId);
        CompileResult compileResult = compileService.compilePreview(definitionId, modelJson, modelType);
        if (!compileResult.isSuccess()) {
            return failedResult(compileResult.getErrorMessage());
        }
        RuleFieldAnalyzer.ResolvedFields fields = ruleFieldAnalyzer.resolveFields(
                definitionId, modelJson, modelType, effectiveProjectId);
        String effectiveModelType = modelType == null || modelType.trim().isEmpty()
                ? definition.getModelType() : modelType.trim().toUpperCase(java.util.Locale.ROOT);
        return executeTest(definition, compileResult.getCompiledScript(), modelJson,
                effectiveModelType,
                fields.getInputFields(), params, effectiveProjectId);
    }

    private RuleResult executeTest(RuleDefinition definition, String compiledScript, String modelJson,
                                   String modelType,
                                   List<RuleDefinitionInputField> inputFields,
                                   Map<String, Object> params,
                                   Long executionProjectId) {
        List<RuleFunction> functions = functionService.listByProject(executionProjectId);
        Map<String, CustomFunction> functionBindings = prepareFunctions(functions);
        String funcPrefix = functionRegistrar.buildScriptFunctionPrefix(functions);
        String fullScript = funcPrefix.isEmpty() ? compiledScript : funcPrefix + "\n" + compiledScript;
        List<RuleDefinitionInputField> directFields = directInputFields(modelJson, modelType, executionProjectId);
        DataObjectFieldReferenceResolver.ReferencePlan referencePlan =
                referencePlan(null, directFields);
        Set<String> explicitReferenceTargets = referencePlan.captureExplicitTargets(params);
        VariableResolveOptions resolveOptions = withInputFields(
                VariableResolveOptions.defaults(), inputFields, modelJson, directFields);
        includeReferenceSources(resolveOptions, referencePlan);
        Map<String, Object> executeParams = bindInputs(
                referencePlan.mergeBindingFields(inputFields), params, resolveOptions);
        Map<String, Object> originalInput = snapshotMap(executeParams);
        String projectCode = null;
        if (executionProjectId != null) {
            RuleProject project = projectService.getById(executionProjectId);
            if (project != null) {
                projectCode = project.getProjectCode();
            }
        }
        runtimeRuleInvoker.enter(definition, executionProjectId, projectCode,
                executeParams, originalInput, true, modelJson);
        bindInvocationCache(resolveOptions);
        long executionStart = System.currentTimeMillis();
        java.time.LocalDateTime historyStartedAt = RuntimeContextBridge.currentContext().startedAt();
        RuleResult result = new RuleResult();
        try (var ignored = RuntimeContextBridge.currentContext().bindFunctions(functionBindings)) {
            bindHistoryDefaults(executionProjectId, resolveOptions);
            try (var context = RuleVariableExecutionContext.prepare(modelType, executeParams,
                    resolveOptions, referencePlan, explicitReferenceTargets,
                    () -> variableSourceResolver.resolveInto(executionProjectId, executeParams, resolveOptions))) {
                result = qlExpressEngine.execute(qlExpressEngine.prepare(fullScript), context, true,
                        RuntimeContextBridge.currentContext());
            }
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
        log.setRootRuleId(definition.getId());
        log.setExecutionProjectId(executionProjectId);
        log.setStartedAt(historyStartedAt);
        log.setHistoryFields(JSON.toJSONString(resolveOptions.getInvocationCache().historySnapshot(inputFields, originalInput, executeParams),
                com.alibaba.fastjson.serializer.SerializerFeature.WriteMapNullValue));
        log.setInputParams(toJsonSafely(originalInput));
        log.setOutputResult(toJsonSafely(result.getResult()));
        log.setSuccess(result.isSuccess() ? 1 : 0);
        log.setErrorMessage(result.getErrorMessage());
        log.setExecuteTimeMs(result.getExecuteTimeMs());
        if (result.getTraces() != null) {
            log.setTraceInfo(toJsonSafely(result.getTraces()));
        }
        ProjectAuthContext billingContext = executionProjectId == null
                ? null : ProjectAuthContext.direct(
                executionProjectId, projectCode, null, null, null);
        persistExecution(log, definition, result, billingContext);

        return result;
    }

    private RuleResult failedResult(String message) {
        RuleResult result = new RuleResult();
        result.setSuccess(false);
        result.setErrorMessage(message);
        return result;
    }

    private boolean isValidTestProject(RuleDefinition definition,
                                       Long executionProjectId) {
        return executionProjectId == null
                || definitionService.isDefinitionAvailableInProject(
                definition.getId(), executionProjectId);
    }

    private Long effectiveProjectId(RuleDefinition definition,
                                    Long executionProjectId) {
        return executionProjectId == null
                ? definition.getProjectId() : executionProjectId;
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
        ArtifactRuntimeSnapshotService.RuntimeSnapshot runtimeSnapshot = published.getArtifactId() == null
                ? null : artifactRuntimeSnapshotService.load(
                        published.getArtifactId(), published.getDefinitionId(), executionProjectId);
        Map<String, CustomFunction> functionBindings = prepareFunctions(runtimeSnapshot == null
                ? functionService.listByProject(executionProjectId) : runtimeSnapshot.getFunctions());

        List<RuleDefinitionInputField> inputFields = runtimeSnapshot == null
                ? definitionService.listInputFields(published.getDefinitionId())
                : runtimeSnapshot.getInputFields();
        String runtimeModelJson = runtimeSnapshot == null || runtimeSnapshot.getModelJson() == null
                ? published.getModelJson() : runtimeSnapshot.getModelJson();
        String runtimeModelType = runtimeSnapshot == null || runtimeSnapshot.getModelType() == null
                ? published.getModelType() : runtimeSnapshot.getModelType();
        String runtimeScript = runtimeSnapshot == null || runtimeSnapshot.getCompiledScript() == null
                ? published.getCompiledScript() : runtimeSnapshot.getCompiledScript();
        // Published artifacts already carry ID-bound fields. Never re-resolve them against live metadata.
        // 公共输入投影只保留原始字段；数据对象路径仍需从冻结模型建立
        // DATA_OBJECT -> 源变量绑定，才能自动触发 API/名单等运行时来源。
        List<RuleDefinitionInputField> directFields = runtimeSnapshot == null
                ? directInputFields(runtimeModelJson, runtimeModelType, executionProjectId)
                : ruleFieldAnalyzer.extractFrozenModelInputFields(runtimeModelJson, runtimeModelType, runtimeSnapshot);
        DataObjectFieldReferenceResolver.ReferencePlan referencePlan =
                referencePlan(runtimeSnapshot, directFields);
        Set<String> explicitReferenceTargets = referencePlan.captureExplicitTargets(params);
        VariableResolveOptions effectiveOptions = withInputFields(resolveOptions, inputFields,
                runtimeModelJson, directFields);
        if (runtimeSnapshot != null) {
            com.hengshucredit.rule.server.derived.DerivedVariableService.prepareSnapshot(
                    effectiveOptions, runtimeModelJson, runtimeSnapshot);
        }
        includeReferenceSources(effectiveOptions, referencePlan);
        Map<String, Object> executeParams = bindInputs(
                referencePlan.mergeBindingFields(inputFields), params, effectiveOptions);
        Map<String, Object> originalInput = snapshotMap(executeParams);
        String projectCode = published.getProjectCode();
        if (projectCode == null && executionProjectId != null) {
            RuleProject project = projectService.getById(executionProjectId);
            if (project != null) {
                projectCode = project.getProjectCode();
            }
        }
        RuleDefinition executionDefinition = runtimeSnapshot == null && definition != null
                ? definition : publishedDefinition(published, executionProjectId);
        if (runtimeSnapshot != null) {
            executionDefinition.setModelType(runtimeModelType);
        }
        if (runtimeSnapshot == null) {
            runtimeRuleInvoker.enter(executionDefinition, executionProjectId, projectCode,
                    executeParams, originalInput, false, runtimeModelJson);
        } else {
            runtimeRuleInvoker.enterArtifact(executionDefinition, executionProjectId, projectCode,
                    executeParams, originalInput, false, runtimeModelJson,
                    runtimeSnapshot);
        }
        bindInvocationCache(effectiveOptions);
        runtimeRuleInvoker.setTraceEnabled(collectTrace);
        long executionStart = System.currentTimeMillis();
        java.time.LocalDateTime historyStartedAt = RuntimeContextBridge.currentContext().startedAt();
        RuleResult result = new RuleResult();
        try (var ignored = RuntimeContextBridge.currentContext().bindFunctions(functionBindings)) {
            bindHistoryDefaults(executionProjectId, effectiveOptions);
            try (var context = RuleVariableExecutionContext.prepare(runtimeModelType, executeParams,
                    effectiveOptions, referencePlan, explicitReferenceTargets, () -> {
                        if (runtimeSnapshot == null) {
                            variableSourceResolver.resolveInto(executionProjectId, executeParams, effectiveOptions);
                        } else {
                            variableSourceResolver.resolveIntoSnapshot(runtimeSnapshot.getVariables(),
                                    runtimeSnapshot.getModels(), runtimeSnapshot.getFunctions(), executeParams, effectiveOptions);
                        }
                    })) {
                result = qlExpressEngine.execute(qlExpressEngine.prepare(runtimeScript), context, collectTrace,
                        RuntimeContextBridge.currentContext());
            }
        } catch (RuleTerminationSignal e) {
            result.setSuccess(true);
            result.setResult(runtimeRuleInvoker.collectTerminationResult());
        } catch (RuntimeException e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        } finally {
            result.setExecuteTimeMs(System.currentTimeMillis() - executionStart);
            collectDeclaredOutputsIfNeeded(result, runtimeModelType);
            runtimeRuleInvoker.completeRoot(result);
            runtimeRuleInvoker.exit();
        }

        RuleExecutionLog log = new RuleExecutionLog();
        log.setTraceId(result.getTraceId());
        log.setRuleCode(published.getRuleCode());
        log.setProjectCode(projectCode);
        log.setRuleVersion(published.getVersion());
        log.setRevisionId(published.getRevisionId());
        log.setArtifactDigest(published.getArtifactDigest());
        log.setModelType(published.getModelType());
        log.setSource(source == null ? "CLIENT_SERVER" : source);
        log.setRootRuleId(published.getDefinitionId());
        log.setExecutionProjectId(executionProjectId);
        log.setStartedAt(historyStartedAt);
        if (runtimeSnapshot == null || !runtimeSnapshot.isImported()) {
            log.setHistoryFields(JSON.toJSONString(effectiveOptions.getInvocationCache().historySnapshot(inputFields, originalInput, executeParams),
                    com.alibaba.fastjson.serializer.SerializerFeature.WriteMapNullValue));
        }
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
        persistExecution(isExperimentSource(source) ? null : log, definition, result, authContext);

        return new ExecutionOutcome(result, executeParams);
    }

    private void persistExecution(RuleExecutionLog log, RuleDefinition definition,
                                  RuleResult result, ProjectAuthContext authContext) {
        if (executionPersistence != null) {
            executionPersistence.offer(log, definition, result.isSuccess(),
                    result.getExecuteTimeMs(), result.getErrorMessage(), authContext);
            return;
        }
        if (log != null) logService.save(log);
        billingService.recordEngineExecution(definition, result.isSuccess(),
                result.getExecuteTimeMs(), result.getErrorMessage(), authContext);
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
                                                   String modelJson, List<RuleDefinitionInputField> directFields) {
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
        for (RuleDefinitionInputField field : directFields) {
            addRequiredScriptName(names, field);
        }
        // 衍生变量是内部计算节点，不应进入对外输入字段投影；但其本身仍必须
        // 进入本次解析集合，否则脚本只会读到未取值的同名变量。
        if (modelJson != null && !modelJson.trim().isEmpty()) {
            try {
                names.addAll(OperandDependencyCollector.collectReferenceDisplayCodes(
                        JSON.parse(modelJson), "VARIABLE"));
            } catch (RuntimeException ignored) {
                // 编译阶段会给出模型 JSON 诊断；执行入口不在此处吞掉编译错误。
            }
        }
        effective.setRequiredScriptNames(names);
        return effective;
    }

    private void bindInvocationCache(VariableResolveOptions options) {
        RuleExecutionSession session = runtimeRuleInvoker.currentSession();
        if (session != null) {
            options.setInvocationCache(session.getInvocationCache());
        } else if (options.getInvocationCache() == null) {
            options.setInvocationCache(new VariableResolutionInvocationCache());
        }
    }

    private void bindHistoryDefaults(Long projectId, VariableResolveOptions options) {
        if (derivedVariableService == null) return;
        var defaults = derivedVariableService.historyDefaults(projectId);
        var objectPaths = new LinkedHashMap<>(derivedVariableService.objectReferencePaths(projectId));
        if (options.getDataObjectReferencePaths() != null) objectPaths.putAll(options.getDataObjectReferencePaths());
        var session = runtimeRuleInvoker.currentSession();
        if (session != null) options.getInvocationCache().registerObjectInputs(objectPaths, session.getOriginalInput());
        options.getInvocationCache().registerHistoryDefaults(defaults);
        for (var field : defaults.values()) {
            if ("API".equals(field.source())) RuntimeContextBridge.currentContext().registerExternalDefaultField(
                    field.apiId(), field.key(), field.apiResultPath(), field.inputRootKey());
        }
    }

    private void addRequiredScriptName(Set<String> names, RuleDefinitionInputField field) {
        if (field != null && field.getScriptName() != null && !field.getScriptName().trim().isEmpty()) {
            names.add(field.getScriptName().trim());
        }
    }

    private List<RuleDefinitionInputField> directInputFields(
            String modelJson, String modelType, Long projectId) {
        if (ruleFieldAnalyzer == null || modelJson == null || modelJson.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return ruleFieldAnalyzer.resolveDirectModelInputFields(modelJson, modelType, projectId);
    }

    private DataObjectFieldReferenceResolver.ReferencePlan referencePlan(
            ArtifactRuntimeSnapshotService.RuntimeSnapshot runtimeSnapshot,
            List<RuleDefinitionInputField> directFields) {
        if (dataObjectFieldReferenceResolver == null) {
            return DataObjectFieldReferenceResolver.ReferencePlan.empty();
        }
        if (runtimeSnapshot == null) {
            return dataObjectFieldReferenceResolver.resolveLive(directFields);
        }
        return dataObjectFieldReferenceResolver.resolveSnapshot(
                directFields, runtimeSnapshot.getDataObjectFields(),
                runtimeSnapshot.getVariables());
    }

    private void includeReferenceSources(
            VariableResolveOptions options,
            DataObjectFieldReferenceResolver.ReferencePlan referencePlan) {
        Set<String> names = options.getRequiredScriptNames() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(options.getRequiredScriptNames());
        names.addAll(referencePlan.requiredSourceNames());
        options.setRequiredScriptNames(names);
    }

    private Map<String, Object> bindInputs(List<RuleDefinitionInputField> fields, Map<String, Object> params,
                                           VariableResolveOptions options) {
        Map<String, Object> safeParams = params == null ? Collections.emptyMap() : params;
        return executionParameterBinder.bindRuleInputs(fields, safeParams, options);
    }

    private Map<String, CustomFunction> prepareFunctions(List<RuleFunction> functions) {
        runtimeRuleInvoker.register(qlExpressEngine.getRunner());
        return functionRegistrar.prepareFunctions(functions, qlExpressEngine.getRunner());
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
