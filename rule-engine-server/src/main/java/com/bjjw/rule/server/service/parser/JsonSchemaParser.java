package com.bjjw.rule.server.service.parser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bjjw.rule.model.dto.ParsedConstant;
import com.bjjw.rule.model.dto.ParsedConstantGroup;
import com.bjjw.rule.model.dto.ParsedField;
import com.bjjw.rule.model.dto.ParsedObject;
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
        // 递归解析，parentFieldId 为 null 表示顶层字段
        parseJsonRecursive(json, obj, null, objectCode, objectCode);
        return obj;
    }

    /**
     * 递归解析 JSON 对象，将所有字段（含嵌套）加入根对象的 fields 列表。
     *
     * @param json           当前层级的 JSON 对象
     * @param root           根 ParsedObject，fields 均加入此对象
     * @param parentFieldId  父字段 ID（顶层为 null），用于表达嵌套关系
     * @param parentPath     父级路径前缀（如 "data.request"，用于生成 scriptName）
     * @param objectCode     数据对象编码（用于 scriptName 生成）
     */
    private void parseJsonRecursive(JSONObject json, ParsedObject root,
                                    Long parentFieldId, String parentPath, String objectCode) {
        // 使用临时递增 ID 标记字段，用于子字段的 parentFieldId 引用
        // 注意：最终写入数据库时这些 ID 会被数据库自增 ID 替换，但 parentFieldId 关系不变
        Long tempFieldId = generateTempFieldId();

        for (Map.Entry<String, Object> entry : json.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            ParsedField field = new ParsedField();
            field.setFieldName(key);
            field.setFieldLabel(key);
            // scriptName 使用完整路径，保证同名嵌套字段不冲突
            field.setScriptName(key);
            field.setParentFieldId(parentFieldId);

            if (value instanceof JSONObject) {
                // 嵌套对象：类型为 OBJECT，递归解析子字段
                field.setVarType("OBJECT");
                // 子字段将 parentFieldId 指向当前字段，path 更新为 parentPath.key
                String childPath = parentPath.isEmpty() ? key : parentPath + "." + key;
                parseJsonRecursive((JSONObject) value, root, tempFieldId, childPath, objectCode);
            } else if (value instanceof JSONArray) {
                field.setVarType("LIST");
                JSONArray arr = (JSONArray) value;
                if (!arr.isEmpty()) {
                    Object first = arr.get(0);
                    if (first instanceof JSONObject) {
                        field.setGenericType("OBJECT");
                        // 数组元素为对象时，递归解析第一个元素的结构
                        String childPath = parentPath.isEmpty() ? key + "[0]" : parentPath + "." + key + "[0]";
                        parseJsonRecursive((JSONObject) first, root, tempFieldId, childPath, objectCode);
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