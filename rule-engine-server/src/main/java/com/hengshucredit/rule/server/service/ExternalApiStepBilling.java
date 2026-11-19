package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import java.util.Map;
import java.util.regex.Pattern;

/** 供应商在一次异步提交的原始响应里明确返回布尔收费标志。 */
record ExternalApiStepBilling(String stepId, String flagPath) {
    static final String MODE = "STEP_RESPONSE";
    private static final Pattern FLAG_PATH = Pattern.compile(
            "response\\.body(?:\\.[\\p{L}_$][\\p{L}\\p{N}_$-]*|\\[\\d+]|\\['[^'\\r\\n]+'\\]|\\[\"[^\"\\r\\n]+\"\\])+");

    static ExternalApiStepBilling from(JSONObject billing) {
        return billing != null && MODE.equalsIgnoreCase(billing.getString("mode"))
                ? new ExternalApiStepBilling(billing.getString("stepId"), billing.getString("flagPath")) : null;
    }

    void validate(RuleExternalApiConfig api) {
        if (!"ASYNC".equalsIgnoreCase(api.getRequestMode())) throw new IllegalArgumentException("步骤响应计费需要异步 HTTP 接口");
        var steps = ExternalApiRequestPlan.specification(api).getJSONArray("steps");
        boolean eligible = steps != null && steps.toJavaList(JSONObject.class).stream().anyMatch(step ->
                stepId != null && stepId.equals(step.getString("id")) && "HTTP".equals(step.getString("type"))
                        && step.getJSONObject("poll") == null);
        if (!eligible) throw new IllegalArgumentException("请选择有效的非轮询 HTTP 提交步骤作为计费步骤");
        if (flagPath == null || !FLAG_PATH.matcher(flagPath).matches())
            throw new IllegalArgumentException("收费标志须为 response.body 下的单个布尔字段路径");
    }

    boolean charge(Map<String,Object> response) {
        return Boolean.TRUE.equals(response.get("success")) && !Boolean.TRUE.equals(response.get("cached"))
                && !Boolean.TRUE.equals(response.get("fallback")) && !"COALESCED".equals(response.get("cacheStatus"))
                && Boolean.TRUE.equals(ExternalApiRequestPlan.read(response, flagPath));
    }
}
