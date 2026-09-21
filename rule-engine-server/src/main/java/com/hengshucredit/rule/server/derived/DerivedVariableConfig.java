package com.hengshucredit.rule.server.derived;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 衍生配置中只有 currentInputs 属于本次请求；历史侧引用不能成为对外入参。 */
public final class DerivedVariableConfig {
    public static final Set<String> NUMERIC_AGGREGATES = Set.of(
            "SUM", "AVG", "STDDEV", "VARIANCE", "SAMPLE_STDDEV", "SAMPLE_VARIANCE");
    public static final Set<String> AGGREGATES = Set.of(
            "COUNT", "DISTINCT_COUNT", "SUM", "MAX", "MIN", "AVG", "STDDEV", "VARIANCE",
            "SAMPLE_STDDEV", "SAMPLE_VARIANCE", "STRING_LENGTH", "CUSTOM");

    private DerivedVariableConfig() { }

    public static List<JSONObject> currentInputs(JSONObject config) {
        List<JSONObject> inputs = new ArrayList<>();
        if ("EXPRESSION".equals(config.getString("mode"))) {
            add(inputs, config.getJSONObject("expression"));
        } else if ("HISTORY".equals(config.getString("mode"))) {
            List<JSONObject> steps = objects(config, "steps");
            if (!steps.isEmpty()) inputs.addAll(objects(steps.get(0), "inputs"));
            collectFilterInputs(config, inputs);
            for (JSONObject step : steps) collectFilterInputs(step, inputs);
            JSONObject geo = config.getJSONObject("geo");
            if (geo != null) {
                for (String key : List.of("longitude", "latitude", "radiusMeters")) add(inputs, geo.getJSONObject(key));
            }
        }
        return inputs;
    }

    public static void validate(JSONObject config) {
        require(config != null, "请配置衍生逻辑");
        if ("EXPRESSION".equals(config.getString("mode"))) {
            require(config.getJSONObject("expression") != null, "请配置衍生表达式");
        } else {
            require("HISTORY".equals(config.getString("mode")), "衍生方式必须是表达式或历史进件统计");
            require(config.getString("scope") != null && Set.of("RULE", "PROJECT", "GLOBAL").contains(config.getString("scope")), "请选择历史统计范围");
            Long window = config.getLong("window");
            require(window != null && window > 0 && window <= 36500, "时间窗口必须是 1 至 36500 的整数");
            require(new java.math.BigDecimal(String.valueOf(config.get("window"))).compareTo(java.math.BigDecimal.valueOf(window)) == 0,
                    "时间窗口必须是整数，不能截断小数");
            require(config.getString("windowUnit") != null && Set.of("MINUTE", "HOUR", "DAY").contains(config.getString("windowUnit")), "时间窗口单位无效");
            require(config.getString("aggregate") != null && AGGREGATES.contains(config.getString("aggregate")), "请选择支持的统计方法");
            String mode = config.getString("recordMode");
            require(mode == null || Set.of("ALL", "LATEST_PER_SUBJECT").contains(mode), "进件取值方式无效");
            List<JSONObject> subjects = objects(config, "subjectFields");
            if ("DISTINCT_COUNT".equals(config.getString("aggregate")) || "LATEST_PER_SUBJECT".equals(mode)) {
                require(!subjects.isEmpty(), "请选择单字段或联合主体 key");
            }
            subjects.forEach(DerivedVariableConfig::fieldKey);
            if (!Set.of("COUNT", "DISTINCT_COUNT").contains(config.getString("aggregate"))) {
                fieldKey(config.getJSONObject("valueField"));
            }
            if ("CUSTOM".equals(config.getString("aggregate"))) {
                require(config.getLong("functionId") != null && config.getLong("functionId") > 0, "请选择自定义聚合函数");
            }
            List<JSONObject> steps = objects(config, "steps");
            require(steps.size() <= 8, "关联步骤不能超过 8 层");
            for (int i = 0; i < steps.size(); i++) {
                JSONObject step = steps.get(i);
                List<JSONObject> fields = objects(step, "fields");
                List<JSONObject> sources = objects(step, i == 0 ? "inputs" : "fromFields");
                require(!fields.isEmpty() && fields.size() == sources.size(), "每层关联的来源与历史匹配字段数量必须一致");
                fields.forEach(DerivedVariableConfig::fieldKey);
                if (i > 0) sources.forEach(DerivedVariableConfig::fieldKey);
                validateFilters(step);
            }
            validateFilters(config);
            JSONObject geo = config.getJSONObject("geo");
            if (geo != null) {
                fieldKey(geo.getJSONObject("longitudeField"));
                fieldKey(geo.getJSONObject("latitudeField"));
                for (String key : List.of("longitude", "latitude", "radiusMeters")) {
                    require(geo.getJSONObject(key) != null, "请完整配置地理范围的中心点和半径");
                }
            }
        }
        for (JSONObject input : currentInputs(config)) validateOperand(input);
    }

    private static void validateFilters(JSONObject config) {
        for (JSONObject filter : objects(config, "filters")) {
            fieldKey(filter.getJSONObject("field"));
            require(filter.getString("operator") != null && Set.of("EQ", "NE", "GT", "GE", "LT", "LE", "IS_NULL", "NOT_NULL").contains(filter.getString("operator")), "历史筛选操作符无效");
            if (!Set.of("IS_NULL", "NOT_NULL").contains(filter.getString("operator"))) {
                require(filter.getJSONObject("value") != null, "请配置历史筛选比较值");
            }
        }
    }

    private static void validateOperand(JSONObject operand) {
        String kind = operand.getString("kind");
        require(kind != null && Set.of("REFERENCE", "LITERAL", "FUNCTION", "OPERATION", "ACCESS", "CAST", "ARRAY").contains(kind),
                "衍生表达式必须通过字段 ID 引用，不支持自由路径或脚本");
        if ("REFERENCE".equals(kind)) fieldKey(operand);
        for (Object value : operand.values()) {
            if (value instanceof JSONObject child && child.containsKey("kind")) validateOperand(child);
            if (value instanceof JSONArray array) {
                for (Object item : array) {
                    if (item instanceof JSONObject child) {
                        if (child.containsKey("kind")) validateOperand(child);
                        else if (child.getJSONObject("operand") != null) validateOperand(child.getJSONObject("operand"));
                    }
                }
            }
        }
    }

    public static String fieldKey(JSONObject field) {
        require(field != null && "REFERENCE".equals(field.getString("kind")), "请选择按 ID 关联的字段");
        String type = field.getString("refType");
        Long id = field.getLong("refId");
        require(type != null && Set.of("VARIABLE", "CONSTANT", "DATA_OBJECT", "MODEL_OUTPUT").contains(type)
                && id != null && id > 0, "字段引用类型或 ID 无效");
        return type + ":" + id;
    }

    public static List<JSONObject> objects(JSONObject config, String key) {
        JSONArray array = config.getJSONArray(key);
        List<JSONObject> result = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getJSONObject(i);
                require(item != null, "配置项 " + key + " 不能为空");
                result.add(item);
            }
        }
        return result;
    }

    private static void collectFilterInputs(JSONObject config, List<JSONObject> inputs) {
        for (JSONObject filter : objects(config, "filters")) add(inputs, filter.getJSONObject("value"));
    }

    private static void add(List<JSONObject> inputs, JSONObject operand) {
        if (operand != null) inputs.add(operand);
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
