package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.qlexpress4.Express4Runner;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayDeque;
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

    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final ThreadLocal<ExecutionFrame> currentFrame = new ThreadLocal<>();

    public void register(Express4Runner runner) {
        if (runner == null || !registered.compareAndSet(false, true)) {
            return;
        }
        try {
            runner.addFunctionOfServiceMethod("executeRule", this, "executeRule", ONE_STRING);
            runner.addFunctionOfServiceMethod("executeRuleField", this, "executeRuleField", TWO_STRINGS);
            runner.addFunctionOfServiceMethod("executeRuleById", this, "executeRuleById", ONE_STRING);
            runner.addFunctionOfServiceMethod("executeRuleFieldById", this, "executeRuleFieldById", TWO_STRINGS);
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
        ExecutionFrame frame = new ExecutionFrame();
        frame.projectId = projectId;
        frame.projectCode = projectCode;
        frame.context = context == null ? new LinkedHashMap<>() : context;
        frame.testMode = testMode;
        if (hasText(ruleCode)) {
            frame.stack.addLast(ruleCode);
        }
        currentFrame.set(frame);
        RuntimeContextBridge.bind(this::writeRuntimeValue);
        Map<String, Object> ruleContext = new LinkedHashMap<>();
        ruleContext.put("code", ruleCode);
        ruleContext.put("name", ruleCode);
        ruleContext.put("projectId", projectId);
        ruleContext.put("projectCode", projectCode);
        RuntimeContextBridge.setRuleContext(ruleContext, Collections.<String>emptyList());
    }

    public void exit() {
        RuntimeContextBridge.clear();
        currentFrame.remove();
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
        ExecutionFrame frame = currentFrame.get();
        if (frame == null) {
            throw new IllegalStateException("executeRule 只能在规则执行过程中调用");
        }
        RuleDefinition definition = definitionId == null
                ? findDefinitionForTest(ruleCode, frame.projectId)
                : definitionService.getById(definitionId);
        String targetRuleCode = definition != null && hasText(definition.getRuleCode())
                ? definition.getRuleCode() : ruleCode;
        if (frame.stack.contains(targetRuleCode)) {
            throw new IllegalStateException("规则调用存在循环: " + buildCyclePath(frame.stack, targetRuleCode));
        }
        RuleDefinitionContent currentContent = frame.testMode && definition != null
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
                    ? findPublishedRule(ruleCode, frame.projectId, frame.projectCode)
                    : findPublishedRule(definitionId, frame.projectId, frame.projectCode);
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
        Long previousProjectId = frame.projectId;
        String previousProjectCode = frame.projectCode;
        Map<String, Object> previousContext = frame.context;
        frame.stack.addLast(targetRuleCode);
        try {
            Long projectId = definition != null ? definition.getProjectId() : previousProjectId;
            String projectCode = hasText(publishedProjectCode) ? publishedProjectCode : resolveProjectCode(projectId);
            VariableResolveOptions options = VariableResolveOptions.defaults();
            Set<String> requiredNames = requiredInputNames(targetDefinitionId);
            if (!requiredNames.isEmpty()) {
                options.setRequiredScriptNames(requiredNames);
            }
            List<RuleDefinitionInputField> childFields = definitionService.listInputFields(targetDefinitionId);
            Map<String, Object> boundParams = executionParameterBinder.bindRuleInputs(childFields, previousContext);
            Map<String, Object> executeParams = variableSourceResolver.resolve(projectId, boundParams, options);
            frame.projectId = projectId;
            frame.projectCode = projectCode;
            frame.context = executeParams;
            RuleResult result = qlExpressEngine.execute(compiledScript, executeParams, false);
            if (!result.isSuccess()) {
                throw new IllegalStateException("执行调用规则失败[" + targetRuleCode + "]: " + result.getErrorMessage());
            }
            return result.getResult();
        } finally {
            frame.stack.removeLast();
            frame.projectId = previousProjectId;
            frame.projectCode = previousProjectCode;
            frame.context = previousContext;
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

    private String resolveProjectCode(Long projectId) {
        if (projectId == null) {
            return null;
        }
        RuleProject project = projectService.getById(projectId);
        return project == null ? null : project.getProjectCode();
    }

    @SuppressWarnings("unchecked")
    private void writeRuntimeValue(String path, Object value) {
        ExecutionFrame frame = currentFrame.get();
        if (frame == null || !hasText(path)) {
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = frame.context;
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

    private static class ExecutionFrame {
        private Long projectId;
        private String projectCode;
        private Map<String, Object> context;
        private boolean testMode;
        private final Deque<String> stack = new ArrayDeque<>();
    }
}
