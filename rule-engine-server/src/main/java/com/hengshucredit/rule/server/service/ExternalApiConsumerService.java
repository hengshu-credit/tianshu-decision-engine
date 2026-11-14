package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 变量与数据对象共用的完整链路入口；只在根执行会话内复用，失败也不重新发起。 */
@Service
public class ExternalApiConsumerService {
    @Resource private RuleExternalApiConfigMapper apiConfigMapper;
    @Resource private ExternalApiInvokeService invokeService;
    @Resource private RuleVariableService variableService;
    @Resource private com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper datasourceMapper;
    @Resource private EngineFieldValidationService fieldValidationService;

    public Map<String, Object> resolve(Long projectId, Long apiId, Map<String, Object> binding,
                                       Map<String, Object> values, VariableResolveOptions options) {
        return resolve(projectId, apiId, binding, values, options, null);
    }

    public Map<String, Object> resolve(Long projectId, Long apiId, Map<String, Object> binding,
                                       Map<String, Object> values, VariableResolveOptions options,
                                       String sourceKey) {
        return resolve(projectId, apiId, binding, values, options, sourceKey, values);
    }

    public Map<String, Object> resolve(Long projectId, Long apiId, Map<String, Object> binding,
                                       Map<String, Object> values, VariableResolveOptions options, String sourceKey,
                                       Map<String, Object> sourceValues) {
        return resolveSelected(projectId, apiId, null, binding, values, options, sourceKey, sourceValues);
    }

    public Map<String, Object> resolveVersion(Long projectId, RuleExternalApiConfig snapshot,
                                              Map<String, Object> values, VariableResolveOptions options, String sourceKey) {
        return resolveSelected(projectId, snapshot.getId(), snapshot, Map.of(), values, options, sourceKey, values);
    }

    private Map<String, Object> resolveSelected(Long projectId, Long apiId, RuleExternalApiConfig snapshot,
                                               Map<String, Object> binding, Map<String, Object> values,
                                               VariableResolveOptions options, String sourceKey, Map<String, Object> sourceValues) {
        if (options.getInvocationCache() == null) options.setInvocationCache(new VariableResolutionInvocationCache());
        if (options.isOfflineReplay() || options.isSkipApiSources()) {
            options.getInvocationCache().recordReplayMissing("EXTERNAL_API", "API:" + apiId, String.valueOf(apiId));
            return Map.of("success", false, "status", Map.of("outcome", "NOT_EXECUTED"));
        }
        RuleExternalApiConfig api = snapshot == null ? options.getInvocationCache().api(apiId, apiConfigMapper::selectById) : snapshot;
        if (api == null || api.getStatus() != null && api.getStatus() != 1) throw new IllegalArgumentException("外数 API 不存在或已停用: " + apiId);
        com.hengshucredit.rule.model.entity.RuleExternalDatasource datasource = null;
        if (datasourceMapper != null) {
            datasource = options.getInvocationCache().datasource(api.getDatasourceId(), datasourceMapper::selectById);
            if (datasource == null || !Integer.valueOf(1).equals(datasource.getStatus()) || !"GLOBAL".equals(datasource.getScope())
                    && !java.util.Objects.equals(projectId, datasource.getProjectId())) throw new IllegalArgumentException("外数 API 不属于当前项目可用范围");
        }
        Map<String, String> paths = options.getDerivedReferencePaths();
        if (paths == null) paths = variableService.buildRefScriptNameMap(projectId);
        ExternalApiRequestPlan.validateBinding(binding);
        Map<String, Object> overrides = binding.get("requestOverrides") instanceof Map<?, ?> raw
                ? JSON.parseObject(JSON.toJSONString(raw)) : Map.of();
        RuleExternalApiConfig selected = invokeService.selectRequestBranch(api, values);
        var specification = overrides.isEmpty() ? null : ExternalApiRequestPlan.specification(api);
        if (specification != null && specification.getJSONArray("requestBranches") != null) {
            var declared = new java.util.ArrayList<>(ExternalApiRequestPlan.fields(specification));
            for (Object raw : specification.getJSONArray("requestBranches")) declared.addAll(ExternalApiRequestPlan.fields(JSON.parseObject(JSON.toJSONString(raw))));
            for (String id : overrides.keySet()) {
                if (declared.stream().noneMatch(field -> id.equals(field.getString("id")) && field.getBooleanValue("overridable"))) throw new IllegalArgumentException("API 参数不能覆盖: " + id);
            }
            Map<String, Object> active = new LinkedHashMap<>();
            for (var field : ExternalApiRequestPlan.fields(ExternalApiRequestPlan.specification(selected))) {
                if (overrides.containsKey(field.getString("id"))) active.put(field.getString("id"), overrides.get(field.getString("id")));
            }
            overrides = active;
        }
        ExternalApiRequestPlan plan = ExternalApiRequestPlan.prepare(selected, values, overrides, paths, datasource == null ? null : datasource.getAuthConfig(), invokeService.linkedRequestInputs(selected));
        if (fieldValidationService != null) {
            fieldValidationService.validateApiPlan(projectId, plan);
        }
        String key = plan.identity();
        VariableResolutionInvocationCache cache = options.getInvocationCache().originalCalls();
        // 检查点与 single-flight 使用同一请求身份，变量/对象换取值路径或增加中间值不会重复提交。
        VariableResolutionInvocationCache.SourceStep previous = cache.originalCall(key);
        final Map<String, Object> pendingMetadata = previous != null && "WAITING_EXTERNAL".equals(previous.getStatus())
                ? previous.getMetadata() : null;
        if (cache.hasSourceStepListener() && previous == null) cache.requireOriginalIdentity(sourceKey, apiId, key);
        if (sourceKey != null) cache.associateResponse(sourceKey, key);
        try {
            return cache.resolve(key, () -> {
                try {
                    if (previous != null && "SUCCESS".equals(previous.getStatus())) {
                        var original = previous.getResponse();
                        if (original != null) return original;
                    }
                    Map<String, Object> response;
                    if (cache.hasSourceStepListener()) {
                        if (previous != null && "UNKNOWN".equals(previous.getStatus())) {
                            throw ExternalApiWaitingException.manualReview("原外数请求的受理状态尚未确认，已阻止重复提交");
                        }
                        if (pendingMetadata == null) {
                            // 先记录发送意图。进程中断且缺少回执时，恢复方据此阻止第二次提交。
                            saveCall(cache, key, sourceKey, "UNKNOWN", null, Map.of("apiConfigId", apiId));
                        }
                        response = invokeService.invokeCheckpointed(plan, pendingMetadata,
                                receipt -> saveCall(cache, key, sourceKey, "WAITING_EXTERNAL", null, receipt));
                        saveCall(cache, key, sourceKey, Boolean.FALSE.equals(response.get("success")) ? "FAILED" : "SUCCESS",
                                response, Map.of("apiConfigId", apiId));
                    } else {
                        response = pendingMetadata == null ? invokeService.invokePlanned(plan) : invokeService.invokePending(plan, pendingMetadata);
                        if (pendingMetadata != null) saveCall(cache, key, sourceKey, "SUCCESS", response, Map.of("apiConfigId", apiId));
                    }
                    return response;
                } catch (ExternalApiInvokeService.ApiInvokeException error) {
                    Map<String, Object> pendingCall = error.getPendingAsync();
                    if (pendingCall != null && !pendingCall.isEmpty()) {
                        saveCall(cache, key, sourceKey, "WAITING_EXTERNAL", null, pendingCall);
                    } else if (pendingMetadata != null && cache.hasSourceStepListener() && error.isUnknown()) {
                        saveCall(cache, key, sourceKey, "WAITING_EXTERNAL", null, pendingMetadata);
                    } else if (cache.hasSourceStepListener() && !error.isUnknown()) {
                        saveCall(cache, key, sourceKey, "FAILED", null, Map.of("apiConfigId", apiId));
                    }
                    throw error;
                }
            });
        } catch (ExternalApiInvokeService.ApiInvokeException error) {
            Object configured = binding.get("exceptionStrategy");
            String strategy = configured == null || String.valueOf(configured).isBlank()
                    ? api.getExceptionStrategy() : String.valueOf(configured);
            if ("WAIT".equalsIgnoreCase(strategy)) {
                throw new ExternalApiWaitingException("外数异常已按 WAIT 策略暂停订单，请使用 root trace 查询或恢复", error);
            }
            throw error;
        }
    }

    private static void saveCall(VariableResolutionInvocationCache cache, String key, String sourceKey, String status,
                                 Map<String, Object> result, Map<String, Object> metadata) {
        var details = new LinkedHashMap<>(metadata);
        if (sourceKey != null) details.put("orderSourceKey", sourceKey);
        cache.completeStep(new VariableResolutionInvocationCache.SourceStep(key, "EXTERNAL_API", null, null, null,
                status, null, result, Map.of(), details));
    }

    public static Object select(Map<String, Object> response, String path) {
        return ExternalApiRequestPlan.read(response, path == null || path.isBlank() ? "body" : path);
    }

    public static void recordStatus(Map<String, Object> response, VariableResolveOptions options, String refType, Long refId) {
        options.recordSourceState(refType, refId, "OUTCOME", response.getOrDefault("sourceOutcome", Boolean.FALSE.equals(response.get("success")) ? "ERROR" : "SUCCESS"));
        if (response.get("status") instanceof Map<?, ?> status) {
            options.recordSourceState(refType, refId, "HTTP_STATUS", status.get("httpStatus"));
            options.recordSourceState(refType, refId, "BILLED", Boolean.TRUE.equals(status.get("billed")) ? "TRUE" : "FALSE");
            options.recordSourceState(refType, refId, "RETRY_COUNT", status.get("retryCount"));
            options.recordSourceState(refType, refId, "RETRIED", status.get("retryCount") instanceof Number count && count.intValue() > 0 ? "TRUE" : "FALSE");
            options.recordSourceState(refType, refId, "CIRCUIT_OPEN", Boolean.TRUE.equals(status.get("circuitOpen")) ? "TRUE" : "FALSE");
            options.recordSourceState(refType, refId, "EXCEPTION", Boolean.TRUE.equals(status.get("exception")) ? "TRUE" : "FALSE");
        }
    }

    /** 显式传入的对象叶子（含 null/false/0/空串）优先。 */
    public static Object merge(Object existing, Object incoming) {
        if (!(existing instanceof Map<?, ?> current) || !(incoming instanceof Map<?, ?> source)) return existing;
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        current.forEach((key, value) -> {
            String name = String.valueOf(key);
            result.put(name, result.containsKey(name) ? merge(value, result.get(name)) : value);
        });
        return result;
    }
}
