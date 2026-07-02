package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.qlexpress4.Express4Runner;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleDefinition;
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

    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final ThreadLocal<ExecutionFrame> currentFrame = new ThreadLocal<>();

    public void register(Express4Runner runner) {
        if (runner == null || !registered.compareAndSet(false, true)) {
            return;
        }
        try {
            runner.addFunctionOfServiceMethod("executeRule", this, "executeRule", ONE_STRING);
            runner.addFunctionOfServiceMethod("executeRuleField", this, "executeRuleField", TWO_STRINGS);
        } catch (Exception e) {
            registered.set(false);
            log.warn("Register rule runtime functions failed: {}", e.getMessage());
        }
    }

    public void enter(String ruleCode, Long projectId, String projectCode, Map<String, Object> context) {
        ExecutionFrame frame = new ExecutionFrame();
        frame.projectId = projectId;
        frame.projectCode = projectCode;
        frame.context = context == null ? Collections.emptyMap() : context;
        if (hasText(ruleCode)) {
            frame.stack.addLast(ruleCode);
        }
        currentFrame.set(frame);
    }

    public void exit() {
        currentFrame.remove();
    }

    public Object executeRule(String ruleCode) {
        return doExecuteRule(ruleCode);
    }

    public Object executeRuleField(String ruleCode, String outputField) {
        Object result = doExecuteRule(ruleCode);
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
        if (!hasText(ruleCode)) {
            throw new IllegalArgumentException("调用规则编码不能为空");
        }
        ExecutionFrame frame = currentFrame.get();
        if (frame == null) {
            throw new IllegalStateException("executeRule 只能在规则执行过程中调用");
        }
        if (frame.stack.contains(ruleCode)) {
            throw new IllegalStateException("规则调用存在循环: " + buildCyclePath(frame.stack, ruleCode));
        }
        RulePublished published = findPublishedRule(ruleCode, frame.projectId, frame.projectCode);
        if (published == null) {
            throw new IllegalArgumentException("调用规则不存在或未发布: " + ruleCode);
        }
        RuleDefinition definition = definitionService.getById(published.getDefinitionId());
        Long previousProjectId = frame.projectId;
        String previousProjectCode = frame.projectCode;
        Map<String, Object> previousContext = frame.context;
        frame.stack.addLast(ruleCode);
        try {
            Long projectId = definition != null ? definition.getProjectId() : previousProjectId;
            String projectCode = hasText(published.getProjectCode()) ? published.getProjectCode() : resolveProjectCode(projectId);
            VariableResolveOptions options = VariableResolveOptions.defaults();
            Set<String> requiredNames = requiredInputNames(published.getDefinitionId());
            if (!requiredNames.isEmpty()) {
                options.setRequiredScriptNames(requiredNames);
            }
            Map<String, Object> executeParams = variableSourceResolver.resolve(projectId, previousContext, options);
            frame.projectId = projectId;
            frame.projectCode = projectCode;
            frame.context = executeParams;
            RuleResult result = qlExpressEngine.execute(published.getCompiledScript(), executeParams, false);
            if (!result.isSuccess()) {
                throw new IllegalStateException("执行调用规则失败[" + ruleCode + "]: " + result.getErrorMessage());
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
        if (hasText(projectCode) || projectId != null) {
            wrapper.and(w -> {
                boolean hasProjectCode = hasText(projectCode);
                if (hasProjectCode) {
                    w.eq(RulePublished::getProjectCode, projectCode);
                    if (projectId != null) {
                        w.or().exists(buildLinkedGlobalRuleExistsSql(projectId));
                    }
                } else {
                    w.exists(buildLinkedGlobalRuleExistsSql(projectId));
                }
            });
        }
        return publishedMapper.selectOne(wrapper);
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

    private static class ExecutionFrame {
        private Long projectId;
        private String projectCode;
        private Map<String, Object> context;
        private final Deque<String> stack = new ArrayDeque<>();
    }
}
