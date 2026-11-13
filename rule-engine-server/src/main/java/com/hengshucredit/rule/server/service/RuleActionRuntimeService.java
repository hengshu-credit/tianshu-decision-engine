package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.dto.RuleResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import com.hengshucredit.rule.core.engine.RequestContext;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class RuleActionRuntimeService {
    @Resource private ExternalApiConsumerService externalApis;
    @Resource private VariableSourceResolver variables;
    @Resource private AsyncListActionService listActions;

    public RequestContext.ExternalValueScope bindExternalValues(Long projectId, Map<String, Object> values,
            VariableResolveOptions options, ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot) {
        return RuntimeContextBridge.bindExternalValueResolver((apiId, path) -> {
            Long effectiveId = bound(snapshot, "EXTERNAL_API", apiId);
            variables.resolveApiDependencies(projectId, Map.of("apiConfigId", effectiveId), values, options);
            var response = externalApis.resolve(projectId, effectiveId, Map.of(), values, options, "DIRECT_EXTERNAL_API:" + effectiveId);
            ExternalApiConsumerService.recordStatus(response, options, "EXTERNAL_API", apiId);
            return ExternalApiConsumerService.select(response, path);
        });
    }

    public RequestContext.ListWriteScope bindListWrites(Long projectId, VariableResolveOptions options,
            ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot) {
        return RuntimeContextBridge.currentContext().bindListWriteResolver((configJson, values) -> {
            try {
                var result = prepareListWrite(projectId, options, snapshot, configJson, values);
                trace(result);
                return result;
            } catch (RuntimeException error) {
                trace(Map.of("status", "REJECTED", "error", String.valueOf(error.getMessage())));
                throw error;
            }
        });
    }

    private Map<String, Object> prepareListWrite(Long projectId, VariableResolveOptions options,
            ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot, String configJson, Object values) {
        if (options.isOfflineReplay()) return Map.of("status", "SKIPPED_REPLAY");
        var cache = options.getInvocationCache();
        var pending = cache == null ? null : cache.listActions();
        var experiment = pending == null ? null : pending.experiment;
        if (options.isDryRun() && (experiment == null || !"TEST".equals(experiment.groupType()))) {
            return Map.of("status", "SKIPPED_DRY_RUN");
        }
        var config = JSON.parseObject(configJson);
        if (options.isDryRun() && !Boolean.TRUE.equals(config.get("writeInTestGroup"))) {
            return Map.of("status", "SKIPPED_DRY_RUN");
        }
        var ids = config.getJSONArray("listIds");
        for (int i = 0; i < ids.size(); i++) ids.set(i, bound(snapshot, "LIST_LIBRARY", ids.getLong(i)));
        String id = cache == null ? null : cache.listTaskId(projectId, RuntimeContextBridge.currentRule(), config, values);
        var task = listActions.prepare(projectId, config, values, RuntimeContextBridge.currentContext().startedAt(), id, experiment);
        if (config.containsKey("writeOnFailure") || config.containsKey("writeInTestGroup")
                || pending != null && !pending.isEmpty()) {
            if (pending == null) throw new IllegalStateException("名单执行条件缺少根规则上下文");
            pending.add(task);
            return Map.of("taskId", task.getString("taskId"), "status", "PENDING_ROOT");
        }
        // 旧制品继续在命中时持久化，先前已有待决动作时则保留同一根执行的写入顺序。
        var result = listActions.submitTask(task);
        requireAccepted(result);
        return result;
    }

    void finishListWrites(VariableResolveOptions options, RuleResult result) {
        if (options.getInvocationCache() == null) return;
        var pending = options.getInvocationCache().listActions();
        if (pending.isEmpty() || "WAITING_EXTERNAL".equals(result.getExecutionStatus())
                || "UNKNOWN".equals(result.getExecutionStatus()) || "THROTTLED".equals(result.getExecutionStatus())) return;
        var selected = new ArrayList<JSONObject>();
        for (var task : pending.snapshot()) {
            if (!result.isSuccess() && Boolean.FALSE.equals(task.getJSONObject("config").get("writeOnFailure"))) {
                trace(Map.of("taskId", task.getString("taskId"), "status", "SKIPPED_FAILURE"));
            } else {
                task.getJSONObject("rule").put("outcome", result.isSuccess() ? "SUCCESS" : "FAILED");
                selected.add(task);
            }
        }
        if (!selected.isEmpty()) {
            var handoff = listActions.submitBatch(selected);
            for (var task : selected) {
                var event = new LinkedHashMap<>(handoff);
                event.put("taskId", task.getString("taskId"));
                trace(event);
            }
            if ("REJECTED".equals(handoff.get("status"))) {
                result.setSuccess(false);
                if (result.getPlatformCode() == null) {
                    result.setPlatformCode(com.hengshucredit.rule.model.PlatformCode.SYSTEM_ERROR.code());
                }
                result.setExecutionStatus("FAILED_RETRYABLE");
                String message = String.valueOf(handoff.get("error"));
                result.setErrorMessage(result.getErrorMessage() == null ? message : result.getErrorMessage() + "；" + message);
                return;
            }
        }
        pending.clear();
    }

    private static void trace(Map<String, Object> result) {
        RuntimeContextBridge.addTraceEvent(Map.of("type", "LIST_ACTION", "label", "异步名单落库", "result", result));
    }

    private static void requireAccepted(Map<String, Object> result) {
        if ("REJECTED".equals(result.get("status"))) throw new IllegalStateException(String.valueOf(result.get("error")));
    }

    private Long bound(ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot, String type, Long id) {
        return snapshot == null ? id : snapshot.getBindings().getOrDefault("BINDING:" + type + ":" + id, id);
    }
}
