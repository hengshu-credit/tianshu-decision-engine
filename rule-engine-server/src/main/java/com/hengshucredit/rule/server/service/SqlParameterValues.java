package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 数据库变量和数据对象共享稳定引用与类型转换。 */
final class SqlParameterValues {
    private SqlParameterValues() { }
    static String referencePath(Map<?, ?> operand, Map<String, String> paths) {
        String type = String.valueOf(operand.get("refType"));
        String key = type + ":" + operand.get("refId");
        if (!Set.of("VARIABLE", "CONSTANT", "DATA_OBJECT", "MODEL_OUTPUT").contains(type)
                || operand.get("refId") == null || paths == null || !paths.containsKey(key)) {
            throw new IllegalArgumentException("SQL 参数引用字段不存在或已停用：" + key);
        }
        return paths.get(key);
    }
    static List<Object> resolve(Object configured, Map<String, Object> values, Map<String, String> paths, Map<String, Object> constants) {
        if (configured == null) return List.of();
        if (!(configured instanceof List<?> items)) throw new IllegalArgumentException("SQL 参数必须是操作数数组");
        List<Object> result = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> operand)) throw new IllegalArgumentException("SQL 参数必须通过内容选择器配置字段或固定值");
            if ("REFERENCE".equals(operand.get("kind"))) {
                String path = referencePath(operand, paths);
                String key = operand.get("refType") + ":" + operand.get("refId");
                result.add(constants.containsKey(key) ? constants.get(key) : ExternalApiRequestPlan.read(values, path));
            } else if ("LITERAL".equals(operand.get("kind"))) {
                result.add("NULL".equals(operand.get("valueType")) ? null : OperandValueResolver.resolve(JSON.toJSONString(operand), values));
            } else throw new IllegalArgumentException("SQL 参数仅支持稳定字段引用和固定值");
        }
        return result;
    }
}
