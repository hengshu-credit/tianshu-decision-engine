package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.server.artifact.Sha256Digests;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** 仅保留统计必需字段；原始输入、输出与条件值仍只在原追踪中。 */
record RuleSetTraceSummary(String status, String json) {
    static RuleSetTraceSummary fromTrace(String trace) {
        if (trace == null || trace.isBlank()) return empty("NO_TRACE");
        try {
            Object root = JSON.parse(trace);
            if (root instanceof JSONObject object && object.getBooleanValue("truncated")) return empty("TRUNCATED");
            if (!(root instanceof JSONObject) && !(root instanceof JSONArray)) return empty("INVALID");
            ArrayDeque<Object> pending = new ArrayDeque<>();
            pending.add(root);
            List<JSONObject> frames = new ArrayList<>();
            while (!pending.isEmpty()) {
                Object node = pending.removeLast();
                if (node instanceof JSONArray array) {
                    for (int i = array.size() - 1; i >= 0; i--) if (array.get(i) != null) pending.add(array.get(i));
                    continue;
                }
                if (!(node instanceof JSONObject frame)) continue;
                if ("RULE_SET".equalsIgnoreCase(frame.getString("modelType"))) addFrame(frame, frames);
                JSONArray children = frame.getJSONArray("children");
                if (children != null) pending.add(children);
            }
            return new RuleSetTraceSummary("READY", JSON.toJSONString(frames));
        } catch (RuntimeException malformed) {
            return empty("INVALID");
        }
    }

    private static void addFrame(JSONObject frame, List<JSONObject> frames) {
        JSONArray events = frame.getJSONArray("events");
        if (events == null) return;
        boolean observed = false, anyHit = false;
        Boolean summaryHit = null;
        List<JSONObject> items = new ArrayList<>();
        for (Object value : events) {
            if (!(value instanceof JSONObject event) || !event.getBooleanValue("evaluated")) continue;
            if ("RULE_SET_SUMMARY".equals(event.getString("type"))) {
                observed = true;
                summaryHit = event.getBooleanValue("hit");
            } else if ("RULE_SET_ITEM".equals(event.getString("type"))) {
                observed = true;
                boolean hit = event.getBooleanValue("hit");
                anyHit |= hit;
                String code = event.getString("ruleCode"), name = event.getString("ruleName");
                JSONObject item = new JSONObject(true);
                item.put("key", Sha256Digests.text(code != null && !code.isBlank() ? "CODE:" + code : "NAME:" + name));
                item.put("ruleCode", code); item.put("ruleName", name); item.put("hit", hit ? 1 : 0);
                items.add(item);
            }
        }
        if (!observed) return;
        Long id = frame.getLong("ruleId");
        String code = frame.getString("ruleCode");
        JSONObject summary = new JSONObject(true);
        summary.put("key", id == null ? "CODE:" + Sha256Digests.text(String.valueOf(code)) : "ID:" + id);
        summary.put("ruleId", id); summary.put("ruleCode", code); summary.put("ruleName", frame.getString("ruleName"));
        summary.put("hit", (summaryHit == null ? anyHit : summaryHit) ? 1 : 0);
        summary.put("failed", "FAILED".equalsIgnoreCase(frame.getString("status")) ? 1 : 0);
        summary.put("cost", Math.max(frame.getLongValue("durationMs"), 0L));
        summary.put("items", items);
        frames.add(summary);
    }

    private static RuleSetTraceSummary empty(String status) { return new RuleSetTraceSummary(status, "[]"); }
}
