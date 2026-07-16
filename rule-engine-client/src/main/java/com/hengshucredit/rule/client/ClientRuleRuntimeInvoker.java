package com.hengshucredit.rule.client;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.qlexpress4.Express4Runner;
import com.hengshucredit.rule.client.cache.CachedRule;
import com.hengshucredit.rule.client.cache.L1MemoryCache;
import com.hengshucredit.rule.client.sync.HttpSyncClient;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
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
        frame.context = context;
        frame.rootTrace = createTraceFrame(rule, null);
        frame.traceStack.addLast(frame.rootTrace);
        if (rule != null && hasText(rule.getRuleCode())) {
            frame.stack.addLast(rule.getRuleCode());
        }
        currentFrame.set(frame);
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
        frame.rootTrace.setExpressionTrace(result.getTraces() == null
                ? Collections.<Object>emptyList() : result.getTraces());
        frame.rootTrace.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
        frame.rootTrace.setDurationMs(result.getExecuteTimeMs());
        result.setTraceId(frame.rootTrace.getTraceId());
        result.setTraces(Collections.<Object>singletonList(frame.rootTrace));
    }

    void exit() {
        RuntimeContextBridge.clear();
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
        CachedRule cached = getCachedRule(ruleCode);
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
        try {
            setRuleContext(cached, childTrace.getTraceId());
            RuleResult result = engine.execute(cached.getCompiledScript(), frame.context, true);
            childTrace.setExpressionTrace(result.getTraces() == null
                    ? Collections.<Object>emptyList() : result.getTraces());
            childTrace.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
            if (!result.isSuccess()) {
                throw new IllegalStateException("执行调用规则失败[" + ruleCode + "]: " + result.getErrorMessage());
            }
            return result.getResult();
        } catch (RuntimeException e) {
            childTrace.setStatus("FAILED");
            throw e;
        } finally {
            childTrace.setDurationMs(System.currentTimeMillis() - childStart);
            frame.stack.removeLast();
            frame.traceStack.removeLast();
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
        private Object context;
        private final Deque<String> stack = new ArrayDeque<>();
        private final Deque<RuleTraceFrame> traceStack = new ArrayDeque<>();
        private RuleTraceFrame rootTrace;
    }
}
