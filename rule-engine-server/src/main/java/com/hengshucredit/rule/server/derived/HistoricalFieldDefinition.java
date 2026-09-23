package com.hengshucredit.rule.server.derived;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleVariable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;

/** 历史侧字段资格来自资源 ID 与执行快照；展示名称不构成历史关联身份。 */
public record HistoricalFieldDefinition(String key, String path, String source, boolean recordResult,
        String inputRootKey, Long apiId, String apiResultPath) {
    public boolean queryable() { return "INPUT".equals(source) || "API".equals(source) || recordResult; }

    public static Map<String, HistoricalFieldDefinition> build(List<RuleVariable> variables,
            List<RuleDataObjectField> fields, List<RuleModel> models, Map<String, String> paths) {
        Map<String, HistoricalFieldDefinition> result = new LinkedHashMap<>();
        Map<Long, RuleVariable> variableMap = new LinkedHashMap<>();
        for (RuleVariable variable : variables) {
            variableMap.put(variable.getId(), variable);
            String key = ("CONSTANT".equals(variable.getVarSource()) ? "CONSTANT:" : "VARIABLE:") + variable.getId();
            Long apiId = null;
            String apiPath = null;
            if ("API".equals(variable.getVarSource()) && variable.getSourceConfig() != null) {
                var config = JSON.parseObject(variable.getSourceConfig());
                apiId = config.getLong("apiConfigId");
                apiPath = config.getString("resultPath");
                if (apiPath == null || apiPath.isBlank()) apiPath = "body";
            }
            result.put(key, new HistoricalFieldDefinition(key, paths.get(key), variable.getVarSource(),
                    Boolean.TRUE.equals(variable.getRecordResult()), key, apiId, apiPath));
        }
        Map<Long, RuleDataObjectField> fieldMap = new LinkedHashMap<>();
        fields.forEach(field -> fieldMap.put(field.getId(), field));
        for (RuleDataObjectField field : fields) {
            RuleDataObjectField anchor = field;
            var visited = new HashSet<Long>();
            while (!anchor.referencesValue() && anchor.getParentFieldId() != null && visited.add(anchor.getId())) {
                var parent = fieldMap.get(anchor.getParentFieldId());
                if (parent == null) break;
                anchor = parent;
            }
            RuleVariable variable = anchor.referencesValue() ? variableMap.get(anchor.getRefVariableId()) : null;
            String key = "DATA_OBJECT:" + field.getId();
            String path = paths.get(key);
            HistoricalFieldDefinition source = variable == null ? null : result.get(
                    ("CONSTANT".equals(variable.getVarSource()) ? "CONSTANT:" : "VARIABLE:") + variable.getId());
            String apiPath = source == null ? null : source.apiResultPath();
            if (apiPath != null && source.path() != null && path != null && path.startsWith(source.path() + ".")) {
                apiPath += path.substring(source.path().length());
            }
            result.put(key, new HistoricalFieldDefinition(key, path, source == null ? "INPUT" : source.source(),
                    Boolean.TRUE.equals(field.getRecordResult()) || source != null && source.recordResult(),
                    source == null ? "DATA_OBJECT:" + anchor.getId() : source.inputRootKey(),
                    source == null ? null : source.apiId(), apiPath));
        }
        for (RuleModel model : models) {
            if (model.getOutputFields() == null) continue;
            for (var field : model.getOutputFields()) {
                String key = "MODEL_OUTPUT:" + field.getId();
                result.put(key, new HistoricalFieldDefinition(key, paths.get(key), "MODEL", Boolean.TRUE.equals(field.getRecordResult()), null, null, null));
            }
        }
        return result;
    }
}
