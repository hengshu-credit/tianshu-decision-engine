package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 同一根执行（含子规则）的有序名单意图；冻结动作时的字段值、有效期起点和来源。 */
final class PendingListActions {
    VariableResolveOptions.ExperimentGroup experiment;
    private final Map<String, JSONObject> tasks = new LinkedHashMap<>();

    synchronized void add(JSONObject task) {
        tasks.putIfAbsent(task.getString("taskId"), task);
    }

    synchronized List<JSONObject> snapshot() {
        return new ArrayList<>(tasks.values());
    }

    synchronized boolean isEmpty() { return tasks.isEmpty(); }

    synchronized void restore(JSONArray saved) {
        if (saved != null) for (int i = 0; i < saved.size(); i++) add(saved.getJSONObject(i));
    }

    synchronized void clear() { tasks.clear(); }
}
