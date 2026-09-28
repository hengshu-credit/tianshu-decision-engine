package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Limits persisted trace payloads without changing the in-memory execution result. */
final class TracePayloadBudget {
    private TracePayloadBudget() {
    }

    static String limit(String json, int maxBytes) {
        if (json == null || maxBytes <= 0 || json.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return json;
        }
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("truncated", true);
        marker.put("originalBytes", json.getBytes(StandardCharsets.UTF_8).length);
        marker.put("maxBytes", maxBytes);
        marker.put("message", "表达式追踪超过持久化大小上限，已截断；请求结果未截断");
        return JSON.toJSONString(marker);
    }
}
