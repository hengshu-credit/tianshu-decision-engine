package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 管理端真实试运行：冻结配置，不向正式接口表写入任何数据。 */
@Service
public class ExternalApiTrialService {
    private static final Set<String> ACTIVE = Set.of("CREATED", "RUNNING", "WAITING");
    @Resource private ExternalApiTrialStore store;
    @Resource private ExternalApiInvokeService invoker;
    @Resource private RuleExternalApiConfigService configService;
    @Resource private RuleExternalApiConfigMapper apiMapper;
    @Resource private RuleExternalDatasourceMapper datasourceMapper;
    @Resource private RuleVariableService variableService;

    public Map<String, Object> create(String owner, String requestKey, RuleExternalApiConfig config,
                                       Map<String, Object> params) {
        if (config == null || config.getId() != null) throw new IllegalArgumentException("新建试运行不能指定正式 API ID");
        if (params == null) throw new IllegalArgumentException("试运行参数必须是 JSON 对象");
        if (requestKey == null || !UUID.fromString(requestKey).toString().equalsIgnoreCase(requestKey)) {
            throw new IllegalArgumentException("试运行请求标识必须是 UUID");
        }
        String digest = Sha256Digests.text(CanonicalJson.write(Map.of("config", config, "params", params)));
        ExternalApiTrialStore.State existing = store.findByRequestKey(owner, requestKey);
        if (existing != null) {
            sameRequest(existing, digest);
            return "CREATED".equals(existing.status()) ? execute(existing) : view(existing);
        }
        RuleExternalApiConfig prepared = configService.prepareTrial(config);
        JSONObject snapshot = freeze(prepared, params);
        ExternalApiTrialStore.State state = store.create(owner, requestKey, prepared.getDatasourceId(), digest,
                snapshot.toJSONString(), 1800);
        sameRequest(state, digest);
        return "CREATED".equals(state.status()) ? execute(state) : view(state);
    }

    public Map<String, Object> get(String owner, String trialId) { return view(require(owner, trialId)); }

    public List<Map<String, Object>> list(String owner) { return store.list(owner, 20).stream().map(this::view).toList(); }

    public Map<String, Object> resume(String owner, String trialId) {
        ExternalApiTrialStore.State state = require(owner, trialId);
        return "WAITING".equals(state.status()) || "CREATED".equals(state.status()) ? execute(state) : view(state);
    }

    public Map<String, Object> cancel(String owner, String trialId) {
        require(owner, trialId);
        return view(store.cancel(trialId, owner));
    }

    private ExternalApiTrialStore.State require(String owner, String id) {
        ExternalApiTrialStore.State state = store.get(id, owner);
        if (state == null) throw new IllegalArgumentException("试运行不存在或不属于当前用户");
        return state;
    }

    private void sameRequest(ExternalApiTrialStore.State state, String digest) {
        if (!state.snapshotDigest().equals(digest)) throw new IllegalArgumentException("同一试运行请求标识不能更改配置或参数，请创建新的试运行");
    }

    private JSONObject freeze(RuleExternalApiConfig config, Map<String, Object> params) {
        JSONObject snapshot = new JSONObject(true);
        snapshot.put("config", config);
        snapshot.put("params", params);
        JSONObject sources = new JSONObject(true);
        JSONObject apis = new JSONObject(true);
        JSONObject indexes = new JSONObject(true);
        JSONObject stepCallIds = new JSONObject(true);
        RuleExternalDatasource owner = captureDatasource(config.getDatasourceId(), sources);
        JSONObject spec = ExternalApiRequestPlan.specification(config);
        if (spec != null && spec.getJSONArray("steps") != null) {
            if (spec.getJSONArray("steps").size() > 65535) throw new IllegalArgumentException("试运行链路最多支持 65535 个步骤");
            int index = 0;
            for (Object raw : spec.getJSONArray("steps")) {
                JSONObject step = JSON.parseObject(JSON.toJSONString(raw));
                indexes.put(step.getString("id"), ++index);
                stepCallIds.put(step.getString("id"), UUID.randomUUID().toString());
                for (String key : List.of("apiConfigId", "authApiConfigId")) {
                    Long id = step.getLong(key);
                    if (id == null) continue;
                    RuleExternalApiConfig linked = apiMapper.selectById(id);
                    if (id <= 0 || linked == null || !Integer.valueOf(1).equals(linked.getStatus())) {
                        throw new IllegalArgumentException("步骤关联的正式 API 不存在或未启用");
                    }
                    RuleExternalDatasource source = captureDatasource(linked.getDatasourceId(), sources);
                    if (!"GLOBAL".equals(source.getScope()) && !java.util.Objects.equals(source.getProjectId(), owner.getProjectId())) {
                        throw new IllegalArgumentException("步骤 API 不属于当前可用范围");
                    }
                    apis.put(String.valueOf(id), linked);
                }
            }
        }
        snapshot.put("datasources", sources);
        snapshot.put("apis", apis);
        snapshot.put("stepIndexes", indexes);
        snapshot.put("stepCallIds", stepCallIds);
        snapshot.put("paths", variableService == null ? Map.of() : variableService.buildRefScriptNameMap(owner.getProjectId()));
        return JSON.parseObject(snapshot.toJSONString());
    }

    private RuleExternalDatasource captureDatasource(Long id, JSONObject sources) {
        RuleExternalDatasource source = id == null || id <= 0 ? null : datasourceMapper.selectById(id);
        if (source == null || !Integer.valueOf(1).equals(source.getStatus())) throw new IllegalArgumentException("试运行必须选择已审批生效且启用的正式数据源");
        sources.put(String.valueOf(id), source);
        return source;
    }

    private void checkDependencies(JSONObject snapshot) {
        for (Map.Entry<String, Object> entry : snapshot.getJSONObject("datasources").entrySet()) {
            RuleExternalDatasource source = datasourceMapper.selectById(Long.valueOf(entry.getKey()));
            if (source == null || !Integer.valueOf(1).equals(source.getStatus()) || !sameSnapshot(source, entry.getValue())) {
                throw new IllegalArgumentException("试运行引用的数据源已变更或停用，不能继续原快照；请核对已发请求后重新试运行");
            }
        }
        for (Map.Entry<String, Object> entry : snapshot.getJSONObject("apis").entrySet()) {
            RuleExternalApiConfig api = apiMapper.selectById(Long.valueOf(entry.getKey()));
            if (api == null || !Integer.valueOf(1).equals(api.getStatus()) || !sameSnapshot(api, entry.getValue())) {
                throw new IllegalArgumentException("试运行引用的正式 API 已变更或停用，不能继续原快照");
            }
        }
    }

    private boolean sameSnapshot(Object current, Object frozen) {
        if (!(frozen instanceof Map<?, ?> frozenFields)) return false;
        try {
            // Fastjson对独立Bean和嵌套Bean的LocalDateTime表示不同；按原实体类型还原后统一比较。
            // 保留所有字段（包括凭据、状态与时间），不能靠删除日期或忽略未知字段绕过冻结门禁。
            String currentCanonical = CanonicalJson.write(current);
            if (!CanonicalJson.readMap(currentCanonical).keySet().containsAll(frozenFields.keySet())) return false;
            Object restored = JSON.parseObject(JSON.toJSONString(frozen), current.getClass());
            return currentCanonical.equals(CanonicalJson.write(restored));
        } catch (RuntimeException invalidSnapshot) {
            return false;
        }
    }

    private Map<String, Object> execute(ExternalApiTrialStore.State requested) {
        String lease = UUID.randomUUID().toString();
        if (!store.claim(requested.trialId(), requested.owner(), lease, 30)) return view(require(requested.owner(), requested.trialId()));
        ExternalApiTrialStore.State initial = require(requested.owner(), requested.trialId());
        if (!"RUNNING".equals(initial.status()) || !lease.equals(initial.leaseOwner())) return view(initial);
        JSONObject snapshot;
        Map<String, Object> saved;
        try {
            snapshot = JSON.parseObject(initial.snapshotJson());
            saved = parseMap(initial.pendingJson());
        } catch (RuntimeException invalidSnapshot) {
            store.finish(initial.trialId(), lease, "FAILED", initial.pendingJson(), null, "试运行快照无法读取，已停止调用");
            return view(require(initial.owner(), initial.trialId()));
        }
        try (ExternalApiTrialContext trial = new ExternalApiTrialContext(initial.trialId(), initial.id(), initial.expiresAt(), snapshot,
                saved, () -> {
                    if (!store.isRunning(initial.trialId(), lease)) throw new ExternalApiTrialContext.Stopped("试运行已取消、过期或失去执行租约");
                }, pending -> {
                    if (!store.checkpoint(initial.trialId(), lease, JSON.toJSONString(pending))) throw new ExternalApiTrialContext.Stopped("试运行检查点未保存，已停止后续调用");
                }); RequestDeadlineContext.Scope deadline = RequestDeadlineContext.limit(sliceMillis())) {
            try {
                checkDependencies(snapshot);
                RuleExternalApiConfig config = snapshot.getObject("config", RuleExternalApiConfig.class);
                config.setId(trial.rootIdentity);
                config.setBillingSuppressed(false);
                Map<String, Object> pending = saved.get("continuation") instanceof Map<?, ?> map ? ExternalApiTrialContext.copy(map) : null;
                Map<String, Object> result = invoker.invokeTrial(config, snapshot.getJSONObject("params"), pending);
                boolean unknown = Boolean.FALSE.equals(result.get("success"))
                        && Boolean.TRUE.equals(ExternalApiRequestPlan.read(result, "status.requestIssued"))
                        && ExternalApiRequestPlan.read(result, "status.httpStatus") == null;
                String status = unknown ? "UNKNOWN" : Boolean.FALSE.equals(result.get("success")) ? "FAILED" : "SUCCEEDED";
                store.finish(initial.trialId(), lease, status, JSON.toJSONString(trial.state()), CanonicalJson.write(result),
                        result.get("errorMessage") == null ? null : String.valueOf(result.get("errorMessage")));
            } catch (ExternalApiInvokeService.BillingPersistenceException failure) {
                String observed = null;
                String message = failure.getMessage();
                try { observed = CanonicalJson.write(failure.getObservedResponse()); }
                catch (RuntimeException serializationFailure) {
                    // 已发调用的费用仍未确认，响应无法表示不能把 UNKNOWN 降级为普通失败。
                    message += "；响应快照无法序列化，原调用结果仍待核对";
                }
                store.finish(initial.trialId(), lease, "UNKNOWN", JSON.toJSONString(trial.state()),
                        observed, message);
            } catch (ExternalApiInvokeService.ApiInvokeException failure) {
                Map<String, Object> pending = failure.getPendingAsync();
                if (pending != null) ExternalApiTrialContext.checkpoint(pending);
                store.finish(initial.trialId(), lease, unknownOutcome(failure) ? "UNKNOWN" : pending != null ? "WAITING" : "FAILED",
                        JSON.toJSONString(trial.state()), null, failure.getMessage());
            } catch (ExternalApiTrialContext.Stopped stopped) {
                // 取消/租约丢失由持久会话状态裁决，绝不能被迟到结果改回成功。
            } catch (RuntimeException failure) {
                store.finish(initial.trialId(), lease, "FAILED", JSON.toJSONString(trial.state()), null, failure.getMessage());
            }
        } catch (RuntimeException initializationFailure) {
            store.finish(initial.trialId(), lease, "FAILED", initial.pendingJson(), null, initializationFailure.getMessage());
        }
        return view(require(initial.owner(), initial.trialId()));
    }

    int sliceMillis() { return 5000; }

    private boolean unknownOutcome(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ExternalApiInvokeService.ApiInvokeException api && api.isUnknown()) return true;
        }
        return false;
    }

    private Map<String, Object> view(ExternalApiTrialStore.State state) {
        if (state == null) throw new IllegalArgumentException("试运行不存在或不属于当前用户");
        JSONObject snapshot = JSON.parseObject(state.snapshotJson());
        if (!ACTIVE.contains(state.status())) {
            // 已提交状态不因 Redis 清理失败而伪装回滚；回调仍有与会话一致的 TTL。
            try { invoker.cleanTrialResources(state.id(), snapshot, parseMap(state.pendingJson())); }
            catch (RuntimeException error) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Trial cleanup failed: {}", state.trialId(), error); }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trialId", state.trialId());
        result.put("requestKey", state.requestKey());
        result.put("testMode", "DRAFT");
        result.put("status", state.status());
        result.put("createdAt", state.createdAt());
        result.put("expiresAt", state.expiresAt());
        result.put("configDigest", state.snapshotDigest());
        result.put("apiName", snapshot.getJSONObject("config").getString("apiName"));
        result.put("datasourceId", state.datasourceId());
        result.put("result", state.resultJson() == null ? null : restoreLegacyTraceArrays(JSON.parse(state.resultJson())));
        result.put("errorMessage", state.errorMessage());
        result.put("canResume", Set.of("CREATED", "WAITING").contains(state.status()));
        result.put("canCancel", ACTIVE.contains(state.status()));
        return result;
    }

    private Map<String, Object> parseMap(String json) { return json == null ? new LinkedHashMap<>() : JSON.parseObject(json); }

    /** 旧 Fastjson 把同一阶段数组的重复槽位写成 $ref；只恢复受控追踪字段，不解释供应商业务 JSON。 */
    @SuppressWarnings("unchecked")
    private Object restoreLegacyTraceArrays(Object result) {
        if (!(result instanceof Map<?, ?>)) return result;
        Deque<Object> pending = new ArrayDeque<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(result);
        while (!pending.isEmpty()) {
            Object value = pending.removeFirst();
            if (!visited.add(value)) continue;
            if (value instanceof Map<?, ?> raw) {
                Map<String, Object> api = (Map<String, Object>) raw;
                if (api.get("externalCall") instanceof Map<?, ?> rawCall) {
                    Map<String, Object> call = (Map<String, Object>) rawCall;
                    if (legacyTraceReference(api.get("traceSteps"))
                            && call.get("traceSteps") instanceof List<?> phases && !phases.isEmpty()) {
                        api.put("traceSteps", call.get("traceSteps"));
                    } else if (legacyTraceReference(call.get("traceSteps"))
                            && api.get("traceSteps") instanceof List<?> phases && !phases.isEmpty()) {
                        call.put("traceSteps", api.get("traceSteps"));
                    }
                    if (call.get("traceSteps") instanceof List<?> phases) pending.add(phases);
                }
                if (api.get("traceSteps") instanceof List<?> phases) pending.add(phases);
                if (api.get("steps") instanceof Map<?, ?> steps) {
                    for (Object step : steps.values()) if (step instanceof Map<?, ?>) pending.add(step);
                }
            } else if (value instanceof List<?> phases) {
                for (Object phase : phases) if (phase instanceof Map<?, ?> stage) {
                    if (stage.get("children") instanceof List<?> children) pending.add(children);
                    if ("WORKFLOW_STEP".equals(stage.get("type")) && stage.get("output") instanceof Map<?, ?> output) {
                        pending.add(output);
                    }
                }
            }
        }
        return result;
    }

    private boolean legacyTraceReference(Object value) {
        return value instanceof Map<?, ?> reference && reference.size() == 1
                && reference.get("$ref") instanceof String path && path.startsWith("$.");
    }
}
