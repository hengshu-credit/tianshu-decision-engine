package com.hengshucredit.rule.server.service.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengshucredit.rule.model.dto.ParsedConstant;
import com.hengshucredit.rule.model.dto.ParsedConstantGroup;
import com.hengshucredit.rule.model.dto.ParsedField;
import com.hengshucredit.rule.model.dto.ParsedObject;
import org.springframework.stereotype.Component;

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

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private JsonNode readObject(String content) {
        try {
            JsonNode value = JSON.readTree(content);
            if (value == null || !value.isObject()) throw new IllegalArgumentException("JSON 顶层必须是对象");
            return value;
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("JSON 格式有误：" + error.getOriginalMessage(), error);
        }
    }

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
        JsonNode json = readObject(jsonContent);
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
    private void parseJsonRecursive(JsonNode json, ParsedObject root,
                                    Long parentFieldId, String parentPath) {
        for (var entry : json.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            Long tempFieldId = generateTempFieldId();
            String fieldPath = parentPath == null || parentPath.isEmpty() ? key : parentPath + "." + key;

            ParsedField field = new ParsedField();
            field.setFieldName(key);
            field.setFieldLabel(key);
            field.setTempId(tempFieldId);
            field.setScriptName(fieldPath);
            field.setParentFieldId(parentFieldId);
            // 审批快照需要先分配父字段 ID，再绑定子字段；同时保持样本中的字段顺序。
            root.getFields().add(field);

            if (value.isObject()) {
                // 嵌套对象：类型为 OBJECT，递归解析子字段
                field.setVarType("OBJECT");
                parseJsonRecursive(value, root, tempFieldId, fieldPath);
            } else if (value.isArray()) {
                field.setVarType("LIST");
                JsonNode arr = value;
                JsonNode first = arr.valueStream().filter(item -> !item.isNull()).findFirst().orElse(null);
                if (first != null) {
                    String elementType = first.isObject() ? "OBJECT" : first.isArray() ? "LIST" : inferPrimitiveType(first);
                    if (arr.valueStream().filter(item -> !item.isNull()).anyMatch(item -> !elementType.equals(
                            item.isObject() ? "OBJECT" : item.isArray() ? "LIST" : inferPrimitiveType(item)))) {
                        throw new IllegalArgumentException("数组 [" + fieldPath + "] 的元素类型不一致，请提供同类型的样本");
                    }
                    if (first.isObject()) {
                        field.setGenericType("OBJECT");
                        // 数组元素为对象时，递归解析第一个元素的结构
                        parseJsonRecursive(first, root, tempFieldId, fieldPath);
                    } else {
                        field.setGenericType(elementType);
                    }
                }
            } else {
                field.setVarType(inferPrimitiveType(value));
            }
        }
    }

    private long tempIdCounter = 1;

    private synchronized long generateTempFieldId() {
        return tempIdCounter++;
    }

    /**
     * 将 JSON 顶层键值解析为常量；数组和对象分别保存为 LIST 和 MAP 常量。
     */
    public ParsedConstantGroup parseConstants(String jsonContent) {
        return parseConstants(jsonContent, "IMPORT", "导入的常量");
    }

    /**
     * Parse JSON key-value pairs without dropping structured values.
     */
    public ParsedConstantGroup parseConstants(String jsonContent, String groupCode, String groupLabel) {
        JsonNode json = readObject(jsonContent);
        ParsedConstantGroup group = new ParsedConstantGroup();
        group.setGroupCode(groupCode);
        group.setGroupLabel(groupLabel);
        group.setScriptName(groupCode);

        for (var entry : json.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isNull()) throw new IllegalArgumentException("字段 [" + key + "] 不能为 null，请提供明确的值");

            ParsedConstant pc = new ParsedConstant();
            pc.setConstCode(key);
            pc.setConstLabel(key);
            pc.setScriptName(key);
            pc.setConstType(value.isObject() ? "MAP" : value.isArray() ? "LIST" : inferPrimitiveType(value));
            pc.setConstValue(value.isObject() || value.isArray() ? value.toString() : value.asText());
            group.getConstants().add(pc);
        }
        return group;
    }

    private String inferPrimitiveType(JsonNode value) {
        if (value == null) return "STRING";
        if (value.isBoolean()) return "BOOLEAN";
        if (value.isNumber()) return "NUMBER";
        return "STRING";
    }
}
