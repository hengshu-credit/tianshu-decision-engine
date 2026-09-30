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

    public Map<String, Object> resolve(Long projectId, Long apiId, Map<String, Object> binding,
                                       Map<String, Object> values, VariableResolveOptions options) {
        return resolve(projectId, apiId, binding, values, options, null);
    }

    public Map<String, Object> resolve(Long projectId, Long apiId, Map<String, Object> binding,
                                       Map<String, Object> values, VariableResolveOptions options,
                                       String sourceKey) {
        if (options.getInvocationCache() == null) options.setInvocationCache(new VariableResolutionInvocationCache());
        if (options.isOfflineReplay() || options.isSkipApiSources()) {
            options.getInvocationCache().recordReplayMissing("EXTERNAL_API", "API:" + apiId, String.valueOf(apiId));
            return Map.of("success", false, "status", Map.of("outcome", "NOT_EXECUTED"));
        }
        RuleExternalApiConfig api = apiConfigMapper.selectById(apiId);
        if (api == null || api.getStatus() != null && api.getStatus() != 1) throw new IllegalArgumentException("外数 API 不存在或已停用: " + apiId);
        if (datasourceMapper != null) {
            var datasource = datasourceMapper.selectById(api.getDatasourceId());
            if (datasource == null || !Integer.valueOf(1).equals(datasource.getStatus()) || !"GLOBAL".equals(datasource.getScope())
                    && !java.util.Objects.equals(projectId, datasource.getProjectId())) throw new IllegalArgumentException("外数 API 不属于当前项目可用范围");
        }
        Map<String, String> paths = options.getDerivedReferencePaths();
        if (paths == null) paths = variableService.buildRefScriptNameMap(projectId);
        Map<String, Object> overrides = binding.get("requestOverrides") instanceof Map<?, ?> raw
                ? JSON.parseObject(JSON.toJSONString(raw)) : Map.of();
        RuleExternalApiConfig selected = invokeService.selectRequestBranch(api, values);
        if (ExternalApiRequestPlan.specification(api) != null && ExternalApiRequestPlan.specification(api).getJSONArray("requestBranches") != null) {
            var declared = new java.util.ArrayList<>(ExternalApiRequestPlan.fields(ExternalApiRequestPlan.specification(api)));
            var specification = ExternalApiRequestPlan.specification(api);
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
        ExternalApiRequestPlan plan = ExternalApiRequestPlan.prepare(selected, values, overrides, paths);
        String key = plan.identity();
        VariableResolutionInvocationCache cache = options.getInvocationCache();
        // 检查点与 single-flight 使用同一请求身份，变量/对象换取值路径或增加中间值不会重复提交。
        VariableResolutionInvocationCache.SourceStep pending = cache.pendingStep(key);
        final Map<String, Object> pendingMetadata = pending == null ? null : pending.getMetadata();
        if (sourceKey != null) cache.associateResponse(sourceKey, key);
        try {
            return cache.resolve(key, () -> {
                try {
                    Map<String, Object> response = pendingMetadata == null
                            ? invokeService.invokePlanned(plan) : invokeService.invokePending(plan, pendingMetadata);
                    if (pendingMetadata != null) {
                        cache.completeStep(new VariableResolutionInvocationCache.SourceStep(
                                key, "EXTERNAL_API", null, null, null, "SUCCESS", null, response,
                                Map.of(), Map.of("apiConfigId", apiId)));
                    }
                    return response;
                } catch (ExternalApiInvokeService.ApiInvokeException error) {
                    Map<String, Object> pendingCall = error.getPendingAsync();
                    if (pendingCall != null && !pendingCall.isEmpty()) {
                        cache.completeStep(new VariableResolutionInvocationCache.SourceStep(
                                key, "EXTERNAL_API", null, null, null, "WAITING_EXTERNAL", null, null,
                                Map.of(), pendingCall));
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
