package com.hengshucredit.rule.client;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.qlexpress4.Express4Runner;
import com.hengshucredit.rule.client.cache.CachedRule;
import com.hengshucredit.rule.client.cache.L1MemoryCache;
import com.hengshucredit.rule.client.sync.HttpSyncClient;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.core.engine.RuleTerminationResultCollector;
import com.hengshucredit.rule.core.engine.RuleTerminationSignal;
import com.hengshucredit.rule.core.trace.TraceIdGenerator;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.dto.RuleTraceFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class ClientRuleRuntimeInvoker {

    private static final Logger log = LoggerFactory.getLogger(ClientRuleRuntimeInvoker.class);
    private static final Class<?>[] ONE_STRING = new Class<?>[]{String.class};
    private static final Class<?>[] TWO_STRINGS = new Class<?>[]{String.class, String.class};
    private static final Class<?>[] NO_ARGS = new Class<?>[]{};

    private final L1MemoryCache l1Cache;
    private final HttpSyncClient httpSyncClient;
    private final QLExpressEngine engine;
    private final RuleEngineClientConfig config;
    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final ThreadLocal<ExecutionFrame> currentFrame = new ThreadLocal<>();

    ClientRuleRuntimeInvoker(L1MemoryCache l1Cache, HttpSyncClient httpSyncClient,
                             QLExpressEngine engine, RuleEngineClientConfig config) {
        this.l1Cache = l1Cache;
        this.httpSyncClient = httpSyncClient;
        this.engine = engine;
        this.config = config;
    }

    void register(Express4Runner runner) {
        if (runner == null || !registered.compareAndSet(false, true)) {
            return;
        }
        try {
            runner.addFunctionOfServiceMethod("executeRule", this, "executeRule", ONE_STRING);
            runner.addFunctionOfServiceMethod("executeRuleField", this, "executeRuleField", TWO_STRINGS);
            runner.addFunctionOfServiceMethod("executeRuleById", this, "executeRuleById", ONE_STRING);
            runner.addFunctionOfServiceMethod("executeRuleFieldById", this, "executeRuleFieldById", TWO_STRINGS);
            runner.addFunctionOfServiceMethod("executeRuleVersionById", this, "executeRuleVersionById", TWO_STRINGS);
            runner.addFunctionOfServiceMethod("executeRuleVersionFieldById", this, "executeRuleVersionFieldById", new Class<?>[]{String.class, String.class, String.class});
            runner.addFunctionOfServiceMethod("terminateAllRules", this, "terminateAllRules", NO_ARGS);
        } catch (Exception e) {
            registered.set(false);
            log.warn("Register client rule runtime functions failed: {}", e.getMessage());
        }
    }

    void enter(String ruleCode, Object context) {
        CachedRule cached = getCachedRule(ruleCode);
        if (cached == null) {
            throw new IllegalArgumentException("调用规则不存在或未同步: " + ruleCode);
        }
        enter(cached, context);
    }

    void enter(CachedRule rule, Object context) {
        ExecutionFrame frame = new ExecutionFrame();
        frame.currentRule = rule;
        frame.context = context;
        frame.rootOutputScriptNames = rule == null || rule.getOutputScriptNames() == null
                ? Collections.<String>emptyList() : rule.getOutputScriptNames();
        frame.rootTrace = createTraceFrame(rule, null);
        frame.traceStack.addLast(frame.rootTrace);
        if (rule != null && hasText(rule.getRuleCode())) {
            frame.stack.addLast(rule.getRuleCode());
        }
        currentFrame.set(frame);
        frame.contextScope = RuntimeContextBridge.install(frame.requestContext);
        RuntimeContextBridge.bind(this::writeRuntimeValue);
        RuntimeContextBridge.bindTraceEventListener(event -> {
            RuleTraceFrame currentTrace = frame.traceStack.peekLast();
            if (currentTrace != null) {
                currentTrace.getEvents().add(event);
            }
        });
        setRuleContext(rule, frame.rootTrace.getTraceId());
    }

    void completeRoot(RuleResult result) {
        ExecutionFrame frame = currentFrame.get();
        if (frame == null || result == null) {
            return;
        }
        if (config.isTraceEnabled()) {
            frame.rootTrace.setExpressionTrace(result.getTraces() == null
                    ? Collections.<Object>emptyList() : result.getTraces());
            frame.rootTrace.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
            frame.rootTrace.setDurationMs(result.getExecuteTimeMs());
            result.setTraceId(frame.rootTrace.getTraceId());
            result.setTraces(Collections.<Object>singletonList(frame.rootTrace));
        } else {
            result.setTraces(null);
        }
    }

    com.hengshucredit.rule.core.engine.RequestContext requestContext() {
        return currentFrame.get().requestContext;
    }

    void exit() {
        ExecutionFrame frame = currentFrame.get();
        if (frame != null) frame.contextScope.close();
        currentFrame.remove();
    }

    public Object executeRule(String ruleCode) {
        return doExecuteRule(ruleCode);
    }

    public Object executeRuleById(String ruleId) { return executeVersion(ruleId, null); }
    public Object executeRuleVersionById(String ruleId, String bindingId) { return executeVersion(ruleId, bindingId); }
    public Object executeRuleFieldById(String ruleId, String field) { return output(executeVersion(ruleId, null), field); }
    public Object executeRuleVersionFieldById(String ruleId, String bindingId, String field) { return output(executeVersion(ruleId, bindingId), field); }
    private Object output(Object value, String field) { return value instanceof Map<?, ?> map ? map.get(field) : null; }
    private Object executeVersion(String rawId, String rawBinding) {
        Long id = positiveId(rawId), binding = rawBinding == null ? null : positiveId(rawBinding);
        ExecutionFrame frame = currentFrame.get();
        if (frame == null) throw new IllegalStateException("规则调用只能在执行期间使用");
        if (frame.currentRule != null && frame.currentRule.isImported()) {
            Map<String, Long> bindings = frame.currentRule.getImportBindings();
            Long mappedId = bindings == null ? null : bindings.get("RULE:" + id);
            if (mappedId == null) throw new IllegalStateException("导入制品缺少规则 ID 绑定");
            id = mappedId;
            if (binding != null) {
                binding = bindings.get("RULE_VERSION:" + binding);
                if (binding == null) throw new IllegalStateException("导入制品缺少指定版本绑定");
            }
        }
        String key = id + ":" + (binding == null ? "LATEST" : binding);
        CachedRule rule = frame.resolvedRules.get(key);
        if (rule == null) {
            rule = l1Cache.getById(id, binding);
            if (rule == null) {
                rule = httpSyncClient.fetchRuleById(id, binding);
                if (rule == null || !id.equals(rule.getDefinitionId()) || (binding != null && !binding.equals(rule.getVersionBindingId())))
                    throw new IllegalArgumentException("指定规则版本不存在或不可访问: " + key);
                l1Cache.put(rule);
            }
            frame.resolvedRules.put(key, rule);
        }
        return doExecuteRule(rule.getRuleCode(), rule);
    }
    private static Long positiveId(String id) {
        if (id == null || !id.matches("[1-9][0-9]*")) throw new IllegalArgumentException("规则或版本 ID 无效");
        return Long.valueOf(id);
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

    public Object terminateAllRules() {
        if (currentFrame.get() == null) {
            throw new IllegalStateException("terminateAllRules 只能在规则执行过程中调用");
        }
        throw new RuleTerminationSignal();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> collectTerminationResult() {
        ExecutionFrame frame = currentFrame.get();
        if (frame == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> values;
        if (frame.context instanceof Map) {
            values = (Map<String, Object>) frame.context;
        } else {
            values = JSONObject.parseObject(JSONObject.toJSONString(frame.context));
        }
        return RuleTerminationResultCollector.collect(values, frame.rootOutputScriptNames);
    }

    private Object doExecuteRule(String ruleCode) {
        ExecutionFrame frame = currentFrame.get();
        if (frame != null && frame.currentRule != null && frame.currentRule.isImported())
            throw new IllegalStateException("导入制品的规则调用必须使用显式 ID 绑定");
        return doExecuteRule(ruleCode, null);
    }
    private Object doExecuteRule(String ruleCode, CachedRule selected) {
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
        CachedRule cached = selected == null ? getCachedRule(ruleCode) : selected;
        if (cached == null) {
            throw new IllegalArgumentException("调用规则不存在或未同步: " + ruleCode);
        }
        RuleTraceFrame childTrace = createTraceFrame(cached, frame.traceStack.peekLast().getTraceId());
        frame.traceStack.peekLast().getChildren().add(childTrace);
        frame.traceStack.addLast(childTrace);
        Map<String, Object> previousRule = RuntimeContextBridge.currentRule();
        List<String> previousMatchedConditions = RuntimeContextBridge.currentMatchedConditions();
        frame.stack.addLast(ruleCode);
        long childStart = System.currentTimeMillis();
        CachedRule caller = frame.currentRule;
        try {
            frame.currentRule = cached;
            setRuleContext(cached, childTrace.getTraceId());
            RuleResult result = engine.execute(engine.prepare(cached.getCompiledScript()), frame.context, config.isTraceEnabled(), frame.requestContext);
            childTrace.setExpressionTrace(result.getTraces() == null
                    ? Collections.<Object>emptyList() : result.getTraces());
            childTrace.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
            if (!result.isSuccess()) {
                throw new IllegalStateException("执行调用规则失败[" + ruleCode + "]: " + result.getErrorMessage());
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
            frame.stack.removeLast();
            frame.traceStack.removeLast();
            frame.currentRule = caller;
            RuntimeContextBridge.setRuleContext(previousRule, previousMatchedConditions);
        }
    }

    private RuleTraceFrame createTraceFrame(CachedRule rule, String parentTraceId) {
        String modelType = rule == null || !hasText(rule.getModelType()) ? "SCRIPT" : rule.getModelType();
        boolean global = rule == null || !hasText(rule.getProjectCode());
        String scopeType = global ? "G" : "P";
        String scopeCode = global ? TraceIdGenerator.GLOBAL_SCOPE_CODE
                : TraceIdGenerator.projectScopeCode(config.getProjectId());
        RuleTraceFrame trace = new RuleTraceFrame();
        trace.setTraceId(TraceIdGenerator.generate(
                TraceIdGenerator.ruleTypeCode(modelType), scopeType, scopeCode));
        trace.setRuleCode(rule == null ? null : rule.getRuleCode());
        if (rule != null) {
            trace.setRuleId(rule.getDefinitionId()); trace.setRuleVersion(rule.getVersion());
            trace.setRevisionId(rule.getRevisionId()); trace.setArtifactDigest(rule.getArtifactDigest());
            trace.setVersionBindingId(rule.getVersionBindingId()); trace.setBindingGeneration(rule.getBindingGeneration());
        }
        trace.setRuleName(rule == null ? null : rule.getRuleCode());
        trace.setModelType(modelType);
        trace.setModelJson(rule == null ? null : rule.getModelJson());
        trace.setScope(global ? "GLOBAL" : "PROJECT");
        trace.setStatus("RUNNING");
        return trace;
    }

    private void setRuleContext(CachedRule rule, String traceId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("code", rule == null ? null : rule.getRuleCode());
        context.put("name", rule == null ? null : rule.getRuleCode());
        context.put("projectId", config.getProjectId());
        context.put("projectCode", rule == null ? null : rule.getProjectCode());
        context.put("traceId", traceId);
        RuntimeContextBridge.setRuleContext(context, Collections.<String>emptyList());
    }

    @SuppressWarnings("unchecked")
    private void writeRuntimeValue(String path, Object value) {
        ExecutionFrame frame = currentFrame.get();
        if (frame == null || !(frame.context instanceof Map) || !hasText(path)) {
            return;
        }
        Map<String, Object> current = (Map<String, Object>) frame.context;
        String[] parts = path.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
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

    private CachedRule getCachedRule(String ruleCode) {
        CachedRule cached = l1Cache.get(ruleCode);
        if (cached == null) {
            cached = httpSyncClient.fetchRule(ruleCode);
            if (cached != null) {
                l1Cache.put(cached);
            }
        }
        return cached;
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
        private final com.hengshucredit.rule.core.engine.RequestContext requestContext =
                new com.hengshucredit.rule.core.engine.RequestContext();
        private RuntimeContextBridge.ContextScope contextScope;
        private CachedRule currentRule;
        private final Map<String, CachedRule> resolvedRules = new LinkedHashMap<>();
        private Object context;
        private final Deque<String> stack = new ArrayDeque<>();
        private final Deque<RuleTraceFrame> traceStack = new ArrayDeque<>();
        private List<String> rootOutputScriptNames = Collections.emptyList();
        private RuleTraceFrame rootTrace;
    }
}
