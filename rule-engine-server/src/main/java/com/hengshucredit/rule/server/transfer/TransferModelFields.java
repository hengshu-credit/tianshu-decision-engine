package com.hengshucredit.rule.server.transfer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 模型字段按导出的聚合位置导入；重写引用时使用实际入库的字段 ID。 */
final class TransferModelFields {
    private TransferModelFields() { }

    static String outputPath(Map<String,Object> model, long fieldId) {
        List<Map<String,Object>> fields = fields(model, "outputFields");
        for (int i = 0; i < fields.size(); i++) {
            if (Objects.equals(id(fields.get(i)), fieldId)) return "/outputFields/" + i + "/id";
        }
        throw new IllegalArgumentException("所选模型版本不包含输出字段 ID " + fieldId);
    }

    static void prepare(Map<String,Object> incoming, Map<String,Object> existing) {
        for (String collection : List.of("inputFields", "outputFields")) {
            var source = fields(incoming, collection);
            var target = existing == null ? List.<Map<String,Object>>of() : fields(existing, collection);
            for (int i = 0; i < source.size(); i++) {
                var field = source.get(i);
                field.remove("modelId");
                field.remove("id");
                if (i < target.size() && sameSlot(field, target.get(i))) {
                    field.put("id", id(target.get(i)));
                }
            }
        }
    }

    static Map<Long,Long> outputMapping(Map<String,Object> source, Map<String,Object> applied) {
        var originals = fields(source, "outputFields");
        var targets = fields(applied, "outputFields");
        Map<Long,Long> mapping = new LinkedHashMap<>();
        for (int i = 0; i < originals.size(); i++) {
            Long old = id(originals.get(i));
            if (old == null) continue;
            if (i >= targets.size()) throw new IllegalArgumentException("目标模型缺少导入的输出字段");
            var target = targets.get(i);
            if (!sameSlot(originals.get(i), target)) throw new IllegalArgumentException("模型输出字段的导入结果与源契约不一致");
            Long actual = id(target);
            if (actual == null || mapping.put(old,actual) != null) throw new IllegalArgumentException("模型输出字段身份不完整或重复");
        }
        return mapping;
    }

    private static boolean sameSlot(Map<String,Object> source, Map<String,Object> target) {
        for (String key : List.of("fieldName","fieldType","featureName")) {
            if (!Objects.equals(source.get(key),target.get(key))) return false;
        }
        return true;
    }

    private static Long id(Map<String,Object> field) {
        Object value = field.get("id");
        return value == null ? null : Long.valueOf(value.toString());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> fields(Map<String,Object> model,String key) {
        return model.get(key) instanceof List<?> rows ? (List<Map<String,Object>>) rows : List.of();
    }
}
