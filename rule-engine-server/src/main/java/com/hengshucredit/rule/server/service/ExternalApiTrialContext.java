package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.server.artifact.CanonicalJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 仅管理端试运行安装的调用域；正式 API 调用不读取或创建试运行状态。 */
final class ExternalApiTrialContext implements AutoCloseable {
    private static final long STEP_IDENTITY_RANGE = 1L << 16;
    private static final long MAX_ROW_ID = (Long.MAX_VALUE - (STEP_IDENTITY_RANGE - 1)) / STEP_IDENTITY_RANGE;
    private static final ThreadLocal<ExternalApiTrialContext> CURRENT = new ThreadLocal<>();
    private final ExternalApiTrialContext previous = CURRENT.get();
    final String trialId;
    final long expiresAt;
    final long rootIdentity;
    final JSONObject snapshot;
    private final Runnable verifyLease;
    private final Consumer<Map<String, Object>> persist;
    private final Set<String> callbacks = new LinkedHashSet<>();
    private final Set<String> tokenKeys = new LinkedHashSet<>();
    private final Map<Long, String> callIds = new LinkedHashMap<>();
    private Map<String, Object> continuation;

    ExternalApiTrialContext(String trialId, long rowId, long expiresAt, JSONObject snapshot,
                            Map<String, Object> saved, Runnable verifyLease,
                            Consumer<Map<String, Object>> persist) {
        this.trialId = trialId;
        this.expiresAt = expiresAt;
        this.snapshot = snapshot;
        this.rootIdentity = rootIdentity(rowId);
        callIds.put(rootIdentity, trialId);
        Set<String> uniqueCalls = new LinkedHashSet<>();
        uniqueCalls.add(trialId);
        JSONObject indexes = snapshot.getJSONObject("stepIndexes");
        JSONObject stepCalls = snapshot.getJSONObject("stepCallIds");
        if (indexes != null) for (String step : indexes.keySet()) {
            String call = stepCalls == null ? null : stepCalls.getString(step);
            try {
                if (call == null || call.length() != 36 || !UUID.fromString(call).toString().equals(call)
                        || !uniqueCalls.add(call) || callIds.putIfAbsent(stepIdentity(step), call) != null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("试运行快照缺少有效且独立的步骤调用标识，请核对原调用后重新创建", invalid);
            }
        }
        this.verifyLease = verifyLease;
        this.persist = persist;
        if (saved != null) {
            if (saved.get("continuation") instanceof Map<?, ?> map) continuation = copy(map);
            if (saved.get("callbackIds") instanceof Iterable<?> ids) ids.forEach(id -> callbacks.add(String.valueOf(id)));
            if (saved.get("tokenKeys") instanceof Iterable<?> keys) keys.forEach(key -> tokenKeys.add(String.valueOf(key)));
        }
        CURRENT.set(this);
    }

    static ExternalApiTrialContext current() { return CURRENT.get(); }

    static <T> T outside(Supplier<T> operation) {
        ExternalApiTrialContext previous = current();
        CURRENT.remove();
        try { return operation.get(); }
        finally { if (previous != null) CURRENT.set(previous); }
    }

    static void check() {
        ExternalApiTrialContext context = current();
        if (context != null) {
            if (System.currentTimeMillis() >= context.expiresAt) throw new Stopped("试运行已过期");
            context.verifyLease.run();
        }
    }

    static void checkpoint(Map<String, Object> pending) {
        ExternalApiTrialContext context = current();
        if (context != null) {
            context.continuation = pending == null ? null : copy(pending);
            context.persist.accept(context.state());
        }
    }

    void callback(String id) {
        callbacks.add(id);
        persist.accept(state());
    }

    void token(String key) {
        if (tokenKeys.add(key)) persist.accept(state());
    }

    Map<String, Object> state() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("continuation", continuation);
        state.put("callbackIds", new ArrayList<>(callbacks));
        state.put("tokenKeys", new ArrayList<>(tokenKeys));
        return state;
    }

    RuleExternalDatasource datasource(Long id) {
        JSONObject sources = snapshot.getJSONObject("datasources");
        return sources == null ? null : sources.getObject(String.valueOf(id), RuleExternalDatasource.class);
    }

    RuleExternalApiConfig api(Long id) {
        JSONObject apis = snapshot.getJSONObject("apis");
        return apis == null ? null : apis.getObject(String.valueOf(id), RuleExternalApiConfig.class);
    }

    long stepIdentity(String stepId) {
        JSONObject indexes = snapshot.getJSONObject("stepIndexes");
        Integer ordinal = indexes == null ? null : indexes.getInteger(stepId);
        if (ordinal == null || ordinal <= 0 || ordinal > 65535) throw new IllegalArgumentException("试运行步骤不在冻结快照中");
        return Math.subtractExact(rootIdentity, ordinal.longValue());
    }

    /**
     * 用负数地址隔离试运行临时资源，并为每个步骤预留低 16 位序号空间。
     * 先校验再计算，避免恶意或损坏的数据库 ID 把清理流程打成 ArithmeticException。
     */
    static long rootIdentity(long rowId) {
        if (rowId <= 0 || rowId > MAX_ROW_ID) {
            throw new IllegalArgumentException("试运行内部身份超出可用范围");
        }
        return -rowId * STEP_IDENTITY_RANGE;
    }

    String callId(Long identity) {
        String value = callIds.get(identity == null ? rootIdentity : identity);
        if (value == null) throw new IllegalArgumentException("调用身份未包含在试运行冻结快照中");
        return value;
    }

    static Long resourceId(Long id) { return id != null && id < 0 && current() != null ? null : id; }

    static Map<String, Object> copy(Map<?, ?> value) {
        // 共享对象按业务值复制，不能让 Fastjson 的 $ref 改变恢复时的路径取值。
        return JSON.parseObject(CanonicalJson.write(value), new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    @Override public void close() {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }

    static final class Stopped extends IllegalStateException {
        Stopped(String message) { super(message); }
    }
}
