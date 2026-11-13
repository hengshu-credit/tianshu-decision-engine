package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.server.service.ReservedContentCode;
import com.hengshucredit.rule.server.service.BuiltinFunctionCatalog;

import java.util.ArrayList;
import java.util.List;

import static com.hengshucredit.rule.server.governance.GovernanceResourceTypes.*;

/** Inspect only identifier declarations. References, literal values and external data keep their own names. */
public final class ResourceCodeValidation {
    public record Issue(String path, String message) { }

    private ResourceCodeValidation() { }

    static List<Issue> inspect(String type, ResourceSnapshot snapshot, boolean existingBuiltinFunction) {
        String field = ReservedContentCode.resourceField(type);
        if (field == null) return List.of();
        JSONObject value = JSON.parseObject(snapshot.snapshotJson());
        List<Issue> issues = new ArrayList<>();
        if (!existingBuiltinFunction) check(issues, "$", value, field);
        if (VARIABLE.equals(type) || DATA_OBJECT.equals(type)) check(issues, "$", value, "scriptName");
        switch (type) {
            case DATA_OBJECT -> rows(issues, value, "fields", "$", "varCode", "scriptName");
            case EXPERIMENT -> rows(issues, value, "groups", "$", "groupCode");
            case FUNCTION -> {
                List<String> builtinParams = existingBuiltinFunction
                        ? BuiltinFunctionCatalog.parameterNames(value.getString(field)) : List.of();
                if (value.get("paramsJson") != null && JSON.isValidArray(value.getString("paramsJson"))) {
                    JSONArray params = JSON.parseArray(value.getString("paramsJson"));
                    for (int i = 0; i < params.size(); i++) {
                        if (params.get(i) instanceof JSONObject param && param.getString("name") != null && !builtinParams.contains(param.getString("name")))
                            check(issues, "$.paramsJson[" + i + "]", param, "name");
                    }
                }
            }
            case RULE -> {
                JSONObject content = value.getJSONObject("content");
                if (content != null) issues.addAll(designer(value.getString("modelType"), content.getString("modelJson")));
            }
            default -> { }
        }
        return issues;
    }

    public static List<Issue> designer(String type, String modelJson) {
        boolean graph = "TREE".equals(type) || "FLOW".equals(type);
        if ((!graph && !"RULE_SET".equals(type)) || !JSON.isValidObject(modelJson)) return List.of();
        JSONObject model = JSON.parseObject(modelJson);
        List<Issue> issues = new ArrayList<>();
        if (graph) {
            JSONObject canvas = model.getJSONObject("logicflow");
            if (canvas != null && canvas.get("nodes") instanceof JSONArray nodes) {
                for (int i = 0; i < nodes.size(); i++) {
                    if (nodes.get(i) instanceof JSONObject node && node.get("properties") instanceof JSONObject props)
                        check(issues, "$.logicflow.nodes[" + i + "].properties", props, "nodeCode");
                }
            }
        } else {
            rows(issues, model, "rules", "$", "ruleCode");
        }
        return issues;
    }

    private static void rows(List<Issue> issues, JSONObject owner, String key, String path, String... fields) {
        Object raw = owner.get(key);
        if (raw instanceof String json && JSON.isValidArray(json)) raw = JSON.parseArray(json);
        if (!(raw instanceof JSONArray array)) return;
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof JSONObject row)) continue;
            for (String field : fields) check(issues, path + "." + key + "[" + i + "]", row, field);
        }
    }

    private static void check(List<Issue> issues, String path, JSONObject row, String field) {
        String error = ReservedContentCode.error(row.getString(field));
        if (error != null) issues.add(new Issue(path + "." + field, error));
    }

    public static void requireValid(List<Issue> issues) {
        if (!issues.isEmpty()) {
            Issue first = issues.get(0);
            throw new IllegalArgumentException(first.path() + " " + first.message());
        }
    }
}
