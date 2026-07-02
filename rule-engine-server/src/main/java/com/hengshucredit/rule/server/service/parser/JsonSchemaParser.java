package com.hengshucredit.rule.server.service.parser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.dto.ParsedConstant;
import com.hengshucredit.rule.model.dto.ParsedConstantGroup;
import com.hengshucredit.rule.model.dto.ParsedField;
import com.hengshucredit.rule.model.dto.ParsedObject;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 将 JSON 样本解析为数据对象结构。
 *
 * <p>铁律四实现：单个数据对象直接支持复杂嵌套逻辑，不生成多个平级子对象。
 * 嵌套关系通过字段的 parentFieldId 表达——每个 JSONObject 字段都会递归解析其内部字段，
 * 子字段的 parentFieldId 指向父 JSONObject 字段的临时 ID，从而形成树状结构。</p>
 *
 * <p>示例：输入 {"code":200,"data":{"score":0.67}}
 * → 根对象字段：[code(NUMBER), data(OBJECT)]
 *   → data 的子字段：[score(NUMBER, parentFieldId → data字段)]
 * → 最终表现为单个数据对象，含嵌套字段</p>
 *
 * <p>对于同名嵌套路径（如多处出现 "data"），每层嵌套均作为独立节点，
 * 通过 path（如 "data.request_id"）确保 scriptName 全局唯一。</p>
 */
@Component
public class JsonSchemaParser {

    /**
     * Parse a JSON sample into a ParsedObject.
     *
     * <p>支持单层和复杂多层嵌套：
     * - 单层：{"name":"test","age":30} → 字段 name, age
     * - 嵌套：{"code":200,"data":{"request_id":"xxx","data":{"score":0.67}}}
     *   → 根字段 code, data(OBJECT)
     *   → data 的子字段：request_id, model_id, task_id, status, data(OBJECT)
     *   → data.data 的子字段：status, score, model_id, task_info, inference_info
     * → 所有字段均在同一个数据对象中，通过 parentFieldId 形成嵌套层级</p>
     */
    public ParsedObject parseObject(String jsonContent, String objectCode) {
        JSONObject json = JSON.parseObject(jsonContent);
        ParsedObject obj = new ParsedObject();
        obj.setObjectCode(objectCode);
        obj.setObjectLabel(objectCode);
        obj.setScriptName(objectCode);
        // 递归解析，parentFieldId 为 null 表示顶层字段；scriptName 保存对象内相对路径
        parseJsonRecursive(json, obj, null, "");
        return obj;
    }

    /**
     * 递归解析 JSON 对象，将所有字段（含嵌套）加入根对象的 fields 列表。
     *
     * @param json           当前层级的 JSON 对象
     * @param root           根 ParsedObject，fields 均加入此对象
     * @param parentFieldId  父字段 ID（顶层为 null），用于表达嵌套关系
     * @param parentPath     父级路径前缀（如 "data.request"，用于生成 scriptName）
     */
    private void parseJsonRecursive(JSONObject json, ParsedObject root,
                                    Long parentFieldId, String parentPath) {
        for (Map.Entry<String, Object> entry : json.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            Long tempFieldId = generateTempFieldId();
            String fieldPath = parentPath == null || parentPath.isEmpty() ? key : parentPath + "." + key;

            ParsedField field = new ParsedField();
            field.setFieldName(key);
            field.setFieldLabel(key);
            field.setTempId(tempFieldId);
            field.setScriptName(fieldPath);
            field.setParentFieldId(parentFieldId);

            if (value instanceof JSONObject) {
                // 嵌套对象：类型为 OBJECT，递归解析子字段
                field.setVarType("OBJECT");
                parseJsonRecursive((JSONObject) value, root, tempFieldId, fieldPath);
            } else if (value instanceof JSONArray) {
                field.setVarType("LIST");
                JSONArray arr = (JSONArray) value;
                if (!arr.isEmpty()) {
                    Object first = arr.get(0);
                    if (first instanceof JSONObject) {
                        field.setGenericType("OBJECT");
                        // 数组元素为对象时，递归解析第一个元素的结构
                        parseJsonRecursive((JSONObject) first, root, tempFieldId, fieldPath);
                    } else {
                        field.setGenericType(inferPrimitiveType(first));
                    }
                } else {
                    field.setGenericType("STRING");
                }
            } else {
                field.setVarType(inferPrimitiveType(value));
            }
            root.getFields().add(field);
        }
    }

    private long tempIdCounter = 1;

    private synchronized long generateTempFieldId() {
        return tempIdCounter++;
    }

    /**
     * 解析扁平 JSON 为常量列表（无常量组概念时占位元数据仅用于解析器内部）。
     */
    public ParsedConstantGroup parseConstants(String jsonContent) {
        return parseConstants(jsonContent, "IMPORT", "导入的常量");
    }

    /**
     * Parse flat JSON key-value pairs as constants.
     * All top-level primitive keys become constants with inferred types.
     */
    public ParsedConstantGroup parseConstants(String jsonContent, String groupCode, String groupLabel) {
        JSONObject json = JSON.parseObject(jsonContent);
        ParsedConstantGroup group = new ParsedConstantGroup();
        group.setGroupCode(groupCode);
        group.setGroupLabel(groupLabel);
        group.setScriptName(groupCode);

        for (Map.Entry<String, Object> entry : json.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof JSONObject || value instanceof JSONArray) {
                continue;
            }

            ParsedConstant pc = new ParsedConstant();
            pc.setConstCode(key);
            pc.setConstLabel(key);
            pc.setScriptName(key);
            pc.setConstType(inferPrimitiveType(value));
            pc.setConstValue(value == null ? "" : String.valueOf(value));
            group.getConstants().add(pc);
        }
        return group;
    }

    private String inferPrimitiveType(Object value) {
        if (value == null) return "STRING";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof Number) return "NUMBER";
        return "STRING";
    }
}
