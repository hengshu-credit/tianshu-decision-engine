package com.hengshucredit.rule.server.derived;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleFunctionMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.service.OperandDependencyCollector;
import com.hengshucredit.rule.server.service.RuleVariableService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.hengshucredit.rule.server.derived.DerivedVariableConfig.*;

@Service
public class DerivedVariableValidator {
    private static final Set<String> NUMERIC = Set.of("NUMBER", "DOUBLE", "INTEGER", "LONG", "FLOAT", "DECIMAL", "PROBABILITY", "INT");
    @Resource
    private RuleVariableService variables;
    @Resource
    private RuleDataObjectFieldMapper objectFields;
    @Resource
    private RuleModelOutputFieldMapper modelFields;
    @Resource
    private RuleFunctionMapper functions;

    public void validate(RuleVariable variable, JSONObject config) {
        DerivedVariableConfig.validate(config);
        Map<String, String> paths = variables.buildRefScriptNameMap(variable.getProjectId());
        for (var reference : OperandDependencyCollector.collectReferences(config)) {
            if ("FUNCTION".equals(reference.getRefType())) {
                if (reference.getRefId() == null) continue; // 内置函数由 Operand 执行器校验。
                var function = functions.selectById(reference.getRefId());
                require(function != null && Integer.valueOf(1).equals(function.getStatus()), "衍生函数不存在或已停用");
                require("GLOBAL".equals(function.getScope()) || variable.getProjectId() != null
                        && variable.getProjectId().equals(function.getProjectId()), "衍生函数不属于当前项目或全局");
            } else {
                require(paths.containsKey(reference.getRefType() + ":" + reference.getRefId()), "衍生引用字段不存在、停用或不属于当前范围");
            }
        }
        checkCycle(variable, variable, new HashSet<>());
        if (!"HISTORY".equals(config.getString("mode"))) return;
        for (JSONObject historical : DerivedVariableConfig.historicalFields(config)) {
            require(canReadHistory(historical, new HashSet<>()),
                    "历史统计默认仅支持请求入参和三方结果；请先为所选字段开启结果记录: " + fieldKey(historical));
        }
        String aggregate = config.getString("aggregate");
        JSONObject field = config.getJSONObject("valueField");
        String type = field == null ? "" : type(field);
        if (NUMERIC_AGGREGATES.contains(aggregate)) require(NUMERIC.contains(type), "该聚合方法只支持数值字段");
        if ("STRING_LENGTH".equals(aggregate)) require(Set.of("STRING", "ENUM").contains(type), "字符串长度只支持字符串或枚举字段");
        if (Set.of("MIN", "MAX").contains(aggregate)) {
            require(NUMERIC.contains(type) || Set.of("STRING", "ENUM", "DATE", "DATETIME").contains(type), "最大/最小值只支持数值、文本或日期字段");
        }
        if ("CUSTOM".equals(aggregate)) {
            var function = functions.selectById(config.getLong("functionId"));
            var params = JSON.parseArray(function.getParamsJson());
            require(params != null && params.size() == 1, "自定义聚合函数必须接收一个列表参数");
            String parameterType = params.getJSONObject(0).getString("type");
            require(parameterType != null && Set.of("LIST", "ARRAY", "OBJECT").contains(parameterType.toUpperCase(Locale.ROOT)), "自定义聚合函数参数应为列表类型");
            require(compatible(variable.getVarType(), function.getReturnType()), "衍生变量类型与自定义函数返回类型不一致");
        } else if (Set.of("MIN", "MAX").contains(aggregate)) {
            require(compatible(variable.getVarType(), type), "衍生变量类型必须与最大/最小值字段类型一致");
        } else {
            require(NUMERIC.contains(variable.getVarType()), "该统计方法的衍生变量类型必须是数值");
        }
        JSONObject geo = config.getJSONObject("geo");
        if (geo != null) {
            require(NUMERIC.contains(type(geo.getJSONObject("longitudeField")))
                    && NUMERIC.contains(type(geo.getJSONObject("latitudeField"))), "历史经纬度字段必须是数值类型");
        }
    }

    private boolean compatible(String first, String second) {
        return first != null && second != null && (first.equals(second) || NUMERIC.contains(first) && NUMERIC.contains(second));
    }

    private boolean canReadHistory(JSONObject field, Set<Long> visitingObjects) {
        Long id = field.getLong("refId");
        if ("VARIABLE".equals(field.getString("refType")) || "CONSTANT".equals(field.getString("refType"))) {
            RuleVariable variable = variables.getById(id);
            return variable != null && ("INPUT".equals(variable.getVarSource()) || "API".equals(variable.getVarSource()) || Boolean.TRUE.equals(variable.getRecordResult()));
        }
        if ("MODEL_OUTPUT".equals(field.getString("refType"))) {
            var output = modelFields.selectById(id);
            return output != null && Boolean.TRUE.equals(output.getRecordResult());
        }
        var object = objectFields.selectById(id);
        if (object == null || !visitingObjects.add(id)) return false;
        if (Boolean.TRUE.equals(object.getRecordResult())) return true;
        if (object.referencesValue()) {
            return canReadHistory(new JSONObject(Map.of("refType", "VARIABLE", "refId", object.getRefVariableId())), visitingObjects);
        }
        if (object.getParentFieldId() != null) {
            return canReadHistory(new JSONObject(Map.of("refType", "DATA_OBJECT", "refId", object.getParentFieldId())), visitingObjects);
        }
        return true; // 未绑定来源的对象字段仅能读取历史请求中的值；运行后结果需显式记录。
    }

    private String type(JSONObject reference) {
        Long id = reference.getLong("refId");
        return switch (reference.getString("refType")) {
            case "VARIABLE", "CONSTANT" -> variables.getById(id).getVarType();
            case "DATA_OBJECT" -> objectFields.selectById(id).getVarType();
            case "MODEL_OUTPUT" -> modelFields.selectById(id).getFieldType();
            default -> "";
        };
    }

    private void checkCycle(RuleVariable variable, RuleVariable draft, Set<Long> visiting) {
        if (!"DERIVED".equals(variable.getVarSource())) return;
        Long id = variable.getId();
        require(id == null || visiting.add(id), "衍生变量上游依赖存在循环");
        JSONObject config = JSON.parseObject(variable.getSourceConfig());
        for (JSONObject operand : currentInputs(config)) {
            for (var reference : OperandDependencyCollector.collectReferences(operand)) {
                Long upstreamId = reference.getRefId();
                if ("DATA_OBJECT".equals(reference.getRefType())) {
                    var field = objectFields.selectById(upstreamId);
                    upstreamId = field == null || !field.referencesValue() ? null : field.getRefVariableId();
                } else if (!"VARIABLE".equals(reference.getRefType())) continue;
                if (upstreamId == null) continue;
                RuleVariable upstream = upstreamId.equals(draft.getId()) ? draft : variables.getById(upstreamId);
                require(upstream != null, "衍生上游变量不存在");
                checkCycle(upstream, draft, visiting);
            }
        }
        visiting.remove(id);
    }
}
