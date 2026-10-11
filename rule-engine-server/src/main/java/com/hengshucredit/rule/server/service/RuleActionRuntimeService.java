package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
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
            Object result;
            try {
                if (options.isOfflineReplay()) result = Map.of("status", "SKIPPED_REPLAY");
                else {
                    var config = JSON.parseObject(configJson);
                    var ids = config.getJSONArray("listIds");
                    for (int i = 0; i < ids.size(); i++) ids.set(i, bound(snapshot, "LIST_LIBRARY", ids.getLong(i)));
                    result = listActions.submit(projectId, config, values, RuntimeContextBridge.currentContext().startedAt());
                }
            } catch (RuntimeException error) {
                result = Map.of("status", "REJECTED", "error", String.valueOf(error.getMessage()));
            }
            RuntimeContextBridge.addTraceEvent(Map.of("type", "LIST_ACTION", "label", "异步名单落库", "result", result));
            return result;
        });
    }

    private Long bound(ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot, String type, Long id) {
        return snapshot == null ? id : snapshot.getBindings().getOrDefault("BINDING:" + type + ":" + id, id);
    }
}
