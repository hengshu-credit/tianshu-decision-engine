package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.qlexpress4.Express4Runner;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RuleTerminationResultCollector;
import com.hengshucredit.rule.core.engine.RuleTerminationSignal;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.core.trace.TraceIdGenerator;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.dto.RuleTraceFrame;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RuleRuntimeInvoker {

    private static final Logger log = LoggerFactory.getLogger(RuleRuntimeInvoker.class);
    private static final Class<?>[] ONE_STRING = new Class<?>[]{String.class};
    private static final Class<?>[] TWO_STRINGS = new Class<?>[]{String.class, String.class};
    private static final Class<?>[] NO_ARGS = new Class<?>[]{};

    @Resource
    private RulePublishedMapper publishedMapper;

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleProjectService projectService;

    @Resource
    private VariableSourceResolver variableSourceResolver;

    @Resource
    private QLExpressEngine qlExpressEngine;

    @Resource
    private ExecutionParameterBinder executionParameterBinder;

    @Resource
    private RuleTraceRegistryService traceRegistryService;

    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final ThreadLocal<RuleExecutionSession> currentSession = new ThreadLocal<>();

    public void register(Express4Runner runner) {
        if (runner == null || !registered.compareAndSet(false, true)) {
            return;
        }
        try {
            runner.addFunctionOfServiceMethod("executeRule", this, "executeRule", ONE_STRING);
            runner.addFunctionOfServiceMethod("executeRuleField", this, "executeRuleField", TWO_STRINGS);
            runner.addFunctionOfServiceMethod("executeRuleById", this, "executeRuleById", ONE_STRING);
            runner.addFunctionOfServiceMethod("executeRuleFieldById", this, "executeRuleFieldById", TWO_STRINGS);
            runner.addFunctionOfServiceMethod("terminateAllRules", this, "terminateAllRules", NO_ARGS);
        } catch (Exception e) {
            registered.set(false);
            log.warn("Register rule runtime functions failed: {}", e.getMessage());
        }
    }

    public void enter(String ruleCode, Long projectId, String projectCode, Map<String, Object> context) {
        enter(ruleCode, projectId, projectCode, context, false);
    }

    public void enter(String ruleCode, Long projectId, String projectCode,
                      Map<String, Object> context, boolean testMode) {
        RuleDefinition definition = new RuleDefinition();
        definition.setRuleCode(ruleCode);
        definition.setRuleName(ruleCode);
        definition.setProjectId(projectId);
        definition.setModelType("SCRIPT");
        definition.setScope(projectId != null && projectId > 0 ? "PROJECT" : "GLOBAL");
        enter(definition, projectCode, context, context, testMode);
    }

    public void enter(RuleDefinition definition, String projectCode,
                      Map<String, Object> values, Map<String, Object> originalInput,
                      boolean testMode) {
        enter(definition, definition == null ? null : definition.getProjectId(), projectCode,
                values, originalInput, testMode, resolveDefinitionModelJson(definition));
    }

    public void enter(RuleDefinition definition, String projectCode,
                      Map<String, Object> values, Map<String, Object> originalInput,
                      boolean testMode, String modelJson) {
        enter(definition, definition == null ? null : definition.getProjectId(), projectCode,
                values, originalInput, testMode, modelJson);
    }

    public void enter(RuleDefinition definition, Long executionProjectId, String projectCode,
                      Map<String, Object> values, Map<String, Object> originalInput,
                      boolean testMode) {
        enter(definition, executionProjectId, projectCode, values, originalInput,
                testMode, resolveDefinitionModelJson(definition));
    }

    public void enter(RuleDefinition definition, Long executionProjectId, String projectCode,
                      Map<String, Object> values, Map<String, Object> originalInput,
                      boolean testMode, String modelJson) {
        if (definition == null) {
            throw new IllegalArgumentException("规则定义不能为空");
        }
        RuleTraceFrame rootTrace = createTraceFrame(definition, projectCode, null, modelJson);
        RuleExecutionSession session = new RuleExecutionSession(
                executionProjectId, projectCode, values, originalInput, testMode,
                definition.getRuleCode(), rootTrace, resolveOutputScriptNames(definition.getId()));
        currentSession.set(session);
        RuntimeContextBridge.bind(this::writeRuntimeValue);
        RuntimeContextBridge.bindTraceEventListener(event -> {
            RuleTraceFrame currentTrace = session.currentTrace();
            if (currentTrace != null) {
                currentTrace.getEvents().add(event);
            }
        });
        Map<String, Object> ruleContext = new LinkedHashMap<>();
        ruleContext.put("id", definition.getId());
        ruleContext.put("code", definition.getRuleCode());
        ruleContext.put("name", hasText(definition.getRuleName())
                ? definition.getRuleName() : definition.getRuleCode());
        ruleContext.put("projectId", executionProjectId);
        ruleContext.put("projectCode", projectCode);
        ruleContext.put("traceId", rootTrace.getTraceId());
        RuntimeContextBridge.setRuleContext(ruleContext, Collections.<String>emptyList());
    }

    public RuleExecutionSession currentSession() {
        return currentSession.get();
    }

    public void completeRoot(RuleResult result) {
        RuleExecutionSession session = currentSession.get();
        if (session == null || result == null) {
            return;
        }
        RuleTraceFrame rootTrace = session.getRootTrace();
        rootTrace.setExpressionTrace(result.getTraces() == null
                ? Collections.<Object>emptyList() : result.getTraces());
        rootTrace.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
        rootTrace.setDurationMs(result.getExecuteTimeMs());
        result.setTraceId(rootTrace.getTraceId());
        result.setTraces(Collections.<Object>singletonList(rootTrace));
    }

    public void exit() {
        RuntimeContextBridge.clear();
        currentSession.remove();
    }

    public Object executeRule(String ruleCode) {
        return doExecuteRule(ruleCode);
    }

    public Object executeRuleField(String ruleCode, String outputField) {
        Object result = doExecuteRule(ruleCode);
        return extractOutput(result, outputField);
    }

    public Object executeRuleById(String ruleId) {
        return doExecuteRule(parseRuleId(ruleId), null);
    }

    public Object executeRuleFieldById(String ruleId, String outputField) {
        Object result = doExecuteRule(parseRuleId(ruleId), null);
        return extractOutput(result, outputField);
    }

    public Object terminateAllRules() {
        if (currentSession.get() == null) {
            throw new IllegalStateException("terminateAllRules 只能在规则执行过程中调用");
        }
        throw new RuleTerminationSignal();
    }

    public Map<String, Object> collectTerminationResult() {
        RuleExecutionSession session = currentSession.get();
        if (session == null) {
            return Collections.emptyMap();
        }
        return RuleTerminationResultCollector.collect(
                session.getValues(), session.getRootOutputScriptNames());
    }

    private Object extractOutput(Object result, String outputField) {
        if (!hasText(outputField) || result == null) {
            return result;
        }
        if (result instanceof Map) {
            return ((Map<?, ?>) result).get(outputField);
        }
        if (result instanceof JSONObject) {
            return ((JSONObject) result).get(outputField);
        }
        return null;
    }

    private Object doExecuteRule(String ruleCode) {
        return doExecuteRule(null, ruleCode);
    }

    private Object doExecuteRule(Long definitionId, String ruleCode) {
        if (definitionId == null && !hasText(ruleCode)) {
            throw new IllegalArgumentException("调用规则标识不能为空");
        }
        RuleExecutionSession session = currentSession.get();
        if (session == null) {
            throw new IllegalStateException("executeRule 只能在规则执行过程中调用");
        }
        RuleDefinition definition = definitionId == null
                ? findDefinitionForTest(ruleCode, session.getCurrentProjectId())
                : definitionService.getById(definitionId);
        String targetRuleCode = definition != null && hasText(definition.getRuleCode())
                ? definition.getRuleCode() : ruleCode;
        if (session.getRuleStack().contains(targetRuleCode)) {
            throw new IllegalStateException("规则调用存在循环: "
                    + buildCyclePath(session.getRuleStack(), targetRuleCode));
        }
        RuleDefinitionContent currentContent = session.isTestMode() && definition != null
                ? definitionService.getContent(definition.getId()) : null;
        RulePublished published = null;
        String compiledScript;
        Long targetDefinitionId;
        boolean useCurrentContent = currentContent != null
                && Integer.valueOf(1).equals(currentContent.getCompileStatus());
        if (useCurrentContent) {
            compiledScript = currentContent.getCompiledScript();
            targetDefinitionId = definition.getId();
        } else {
            published = definitionId == null
                    ? findPublishedRule(ruleCode, session.getCurrentProjectId(), session.getCurrentProjectCode())
                    : findPublishedRule(definitionId, session.getCurrentProjectId(), session.getCurrentProjectCode());
            if (published == null) {
                throw new IllegalArgumentException("调用规则不存在、未编译或未发布: "
                        + (definitionId == null ? ruleCode : definitionId));
            }
            compiledScript = published.getCompiledScript();
            targetDefinitionId = published.getDefinitionId();
            if (definition == null) {
                definition = definitionService.getById(targetDefinitionId);
                targetRuleCode = definition != null && hasText(definition.getRuleCode())
                        ? definition.getRuleCode() : published.getRuleCode();
            }
        }
        String publishedProjectCode = published == null ? null : published.getProjectCode();
        Long previousProjectId = session.getCurrentProjectId();
        String previousProjectCode = session.getCurrentProjectCode();
        Map<String, Object> previousRule = RuntimeContextBridge.currentRule();
        List<String> previousMatchedConditions = RuntimeContextBridge.currentMatchedConditions();
        Map<String, Map<String, Object>> previousSourceStates = RuntimeContextBridge.currentSourceStates();
        Long projectId = definition != null ? definition.getProjectId() : previousProjectId;
        String projectCode = hasText(publishedProjectCode)
                ? publishedProjectCode : resolveProjectCode(projectId);
        String childModelJson = useCurrentContent
                ? currentContent.getModelJson() : (published == null ? null : published.getModelJson());
        RuleTraceFrame childTrace = createTraceFrame(definition, projectCode,
                session.currentTrace().getTraceId(), childModelJson);
        session.currentTrace().getChildren().add(childTrace);
        session.getTraceStack().addLast(childTrace);
        session.getRuleStack().addLast(targetRuleCode);
        long childStart = System.currentTimeMillis();
        try {
            session.setCurrentProjectId(projectId);
            session.setCurrentProjectCode(projectCode);
            Map<String, Object> childRule = new LinkedHashMap<>();
            childRule.put("id", targetDefinitionId);
            childRule.put("code", targetRuleCode);
            childRule.put("name", definition != null && hasText(definition.getRuleName())
                    ? definition.getRuleName() : targetRuleCode);
            childRule.put("projectId", projectId);
            childRule.put("projectCode", projectCode);
            childRule.put("traceId", childTrace.getTraceId());
            RuntimeContextBridge.setRuleContext(childRule, Collections.<String>emptyList());

            VariableResolveOptions options = VariableResolveOptions.defaults();
            options.setStatusReferenceKeys(SourceStatusUsage.scan(childModelJson));
            Set<String> requiredNames = requiredInputNames(targetDefinitionId);
            options.setRequiredScriptNames(requiredNames);
            List<RuleDefinitionInputField> childFields = definitionService.listInputFields(targetDefinitionId);
            Map<String, Object> boundParams = executionParameterBinder.bindRuleInputs(
                    childFields, session.getValues(), options);
            session.getValues().putAll(boundParams);
            variableSourceResolver.resolveInto(projectId, session.getValues(), options);
            RuntimeContextBridge.replaceSourceStates(options.getSourceStates());
            RuleResult result = qlExpressEngine.execute(compiledScript, session.getValues(), true);
            childTrace.setExpressionTrace(result.getTraces() == null
                    ? Collections.<Object>emptyList() : result.getTraces());
            childTrace.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
            if (!result.isSuccess()) {
                throw new IllegalStateException("执行调用规则失败[" + targetRuleCode + "]: " + result.getErrorMessage());
            }
            return result.getResult();
        } catch (RuleTerminationSignal e) {
            childTrace.setStatus("SUCCESS");
            throw e;
        } catch (RuntimeException e) {
            childTrace.setStatus("FAILED");
            throw e;
        } finally {
            childTrace.setDurationMs(System.currentTimeMillis() - childStart);
            session.getRuleStack().removeLast();
            session.getTraceStack().removeLast();
            session.setCurrentProjectId(previousProjectId);
            session.setCurrentProjectCode(previousProjectCode);
            RuntimeContextBridge.setRuleContext(previousRule, previousMatchedConditions);
            RuntimeContextBridge.replaceSourceStates(previousSourceStates);
        }
    }

    private RulePublished findPublishedRule(String ruleCode, Long projectId, String projectCode) {
        LambdaQueryWrapper<RulePublished> wrapper = new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getRuleCode, ruleCode)
                .eq(RulePublished::getStatus, 1);
        applyProjectScope(wrapper, projectId, projectCode);
        return publishedMapper.selectOne(wrapper);
    }

    private RulePublished findPublishedRule(Long definitionId, Long projectId, String projectCode) {
        LambdaQueryWrapper<RulePublished> wrapper = new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId, definitionId)
                .eq(RulePublished::getStatus, 1);
        applyProjectScope(wrapper, projectId, projectCode);
        return publishedMapper.selectOne(wrapper);
    }

    private void applyProjectScope(LambdaQueryWrapper<RulePublished> wrapper,
                                   Long projectId, String projectCode) {
        if (hasText(projectCode) || (projectId != null && projectId > 0)) {
            wrapper.and(w -> {
                boolean hasProjectCode = hasText(projectCode);
                if (hasProjectCode) {
                    w.eq(RulePublished::getProjectCode, projectCode);
                    if (projectId != null && projectId > 0) {
                        w.or().exists(buildLinkedGlobalRuleExistsSql(projectId));
                    }
                } else {
                    w.exists(buildLinkedGlobalRuleExistsSql(projectId));
                }
            });
        }
    }

    private RuleDefinition findDefinitionForTest(String ruleCode, Long projectId) {
        if (!hasText(ruleCode)) {
            return null;
        }
        LambdaQueryWrapper<RuleDefinition> wrapper = new LambdaQueryWrapper<RuleDefinition>()
                .eq(RuleDefinition::getRuleCode, ruleCode)
                .eq(RuleDefinition::getStatus, 1);
        if (projectId != null && projectId > 0) {
            wrapper.and(w -> w.eq(RuleDefinition::getProjectId, projectId)
                    .or().eq(RuleDefinition::getScope, "GLOBAL"));
        }
        return definitionService.getOne(wrapper, false);
    }

    private Set<String> requiredInputNames(Long definitionId) {
        Set<String> names = new LinkedHashSet<>();
        if (definitionId == null) {
            return names;
        }
        List<RuleDefinitionInputField> fields = definitionService.listInputFields(definitionId);
        if (fields == null) {
            return names;
        }
        for (RuleDefinitionInputField field : fields) {
            if (field != null && hasText(field.getScriptName())) {
                names.add(field.getScriptName().trim());
            }
        }
        return names;
    }

    private List<String> resolveOutputScriptNames(Long definitionId) {
        if (definitionService == null || definitionId == null) {
            return Collections.emptyList();
        }
        List<RuleDefinitionOutputField> fields = definitionService.listOutputFields(definitionId);
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> names = new java.util.ArrayList<>();
        for (RuleDefinitionOutputField field : fields) {
            if (field != null && hasText(field.getScriptName())) {
                names.add(field.getScriptName().trim());
            }
        }
        return names;
    }

    private String resolveProjectCode(Long projectId) {
        if (projectId == null) {
            return null;
        }
        RuleProject project = projectService.getById(projectId);
        return project == null ? null : project.getProjectCode();
    }

    private RuleTraceFrame createTraceFrame(RuleDefinition definition, String projectCode,
                                            String parentTraceId, String modelJson) {
        String modelType = definition != null && hasText(definition.getModelType())
                ? definition.getModelType() : "SCRIPT";
        Long projectId = definition == null ? null : definition.getProjectId();
        String definitionScope = definition == null ? null : definition.getScope();
        boolean global = "GLOBAL".equalsIgnoreCase(definitionScope)
                || projectId == null || projectId <= 0;
        String scopeType = global ? "G" : "P";
        String scopeCode = global ? TraceIdGenerator.GLOBAL_SCOPE_CODE : resolveTraceScopeCode(projectId);
        String typeCode = TraceIdGenerator.ruleTypeCode(modelType);
        String ruleCode = definition == null ? null : definition.getRuleCode();
        String traceId = traceRegistryService == null
                ? TraceIdGenerator.generate(typeCode, scopeType, scopeCode)
                : traceRegistryService.allocate(typeCode, scopeType, scopeCode, projectId,
                        "RULE", definition == null ? null : definition.getId(), ruleCode, parentTraceId);

        RuleTraceFrame trace = new RuleTraceFrame();
        trace.setTraceId(traceId);
        trace.setRuleId(definition == null ? null : definition.getId());
        trace.setRuleCode(ruleCode);
        trace.setRuleName(definition != null && hasText(definition.getRuleName())
                ? definition.getRuleName() : ruleCode);
        trace.setModelType(modelType);
        trace.setModelJson(modelJson);
        trace.setScope(global ? "GLOBAL" : "PROJECT");
        trace.setStatus("RUNNING");
        return trace;
    }

    private String resolveDefinitionModelJson(RuleDefinition definition) {
        if (definitionService == null || definition == null || definition.getId() == null) {
            return null;
        }
        RuleDefinitionContent content = definitionService.getContent(definition.getId());
        return content == null ? null : content.getModelJson();
    }

    private String resolveTraceScopeCode(Long projectId) {
        if (projectService != null) {
            RuleProject project = projectService.getById(projectId);
            if (project != null && hasText(project.getTraceScopeCode())) {
                return project.getTraceScopeCode();
            }
        }
        return TraceIdGenerator.projectScopeCode(projectId);
    }

    @SuppressWarnings("unchecked")
    private void writeRuntimeValue(String path, Object value) {
        RuleExecutionSession session = currentSession.get();
        if (session == null || !hasText(path)) {
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = session.getValues();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;
            if (i == parts.length - 1) {
                current.put(part, value);
            } else {
                Object child = current.get(part);
                if (!(child instanceof Map)) {
                    child = new LinkedHashMap<String, Object>();
                    current.put(part, child);
                }
                current = (Map<String, Object>) child;
            }
        }
    }

    private static String buildLinkedGlobalRuleExistsSql(Long projectId) {
        return "SELECT 1 FROM rule_definition_ref rdr " +
                "WHERE rdr.definition_id = rule_published.definition_id " +
                "AND rdr.project_id = " + projectId;
    }

    private static String buildCyclePath(Deque<String> stack, String next) {
        StringBuilder sb = new StringBuilder();
        boolean started = false;
        for (String item : stack) {
            if (!started && item.equals(next)) {
                started = true;
            }
            if (started) {
                if (sb.length() > 0) sb.append(" -> ");
                sb.append(item);
            }
        }
        if (sb.length() > 0) sb.append(" -> ");
        sb.append(next);
        return sb.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static Long parseRuleId(String value) {
        if (!hasText(value)) {
            throw new IllegalArgumentException("调用规则ID不能为空");
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("调用规则ID格式错误: " + value, e);
        }
    }

}
