package com.hengshucredit.rule.client;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.qlexpress4.Express4Runner;
import com.hengshucredit.rule.client.cache.CachedRule;
import com.hengshucredit.rule.client.cache.L1MemoryCache;
import com.hengshucredit.rule.client.sync.HttpSyncClient;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
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
        ExecutionFrame frame = new ExecutionFrame();
        frame.context = context;
        if (hasText(ruleCode)) {
            frame.stack.addLast(ruleCode);
        }
        currentFrame.set(frame);
    }

    void exit() {
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
        Object previousContext = frame.context;
        frame.stack.addLast(ruleCode);
        try {
            RuleResult result = engine.execute(cached.getCompiledScript(), previousContext, config.isTraceEnabled());
            if (!result.isSuccess()) {
                throw new IllegalStateException("执行调用规则失败[" + ruleCode + "]: " + result.getErrorMessage());
            }
            return result.getResult();
        } finally {
            frame.stack.removeLast();
            frame.context = previousContext;
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
    }
}
