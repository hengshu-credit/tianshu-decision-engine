package com.bjjw.rule.server.service.parser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bjjw.rule.model.dto.*;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 将 JSON 样本解析为数据对象结构。
 *
 * <p>铁律四实现：单个数据对象直接支持嵌套逻辑，嵌套字段（varType=OBJECT/LIST with genericType=OBJECT）
 * 通过 refObjectCode 表达引用关系（refObjectId 由服务层解析为数据库 ID）。</p>
 */
@Component
public class JsonSchemaParser {

    /**
     * Parse a JSON sample into a ParsedObject.
     *
     * <p>铁律四：不再为每个嵌套 JSONObject 创建独立 ParsedObject（平级子对象），而是将嵌套字段
     * 直接放在根对象的 fields 列表中，varType 设为 OBJECT/LIST，refObjectCode 设为嵌套对象编码。
     * 服务层在写入数据库时将 refObjectCode 解析为 refObjectId。</p>
     *
     * <p>示例：输入 {"code":200,"data":{"score":0.67}}
     * → 根对象字段：[code(NUMBER), data(OBJECT,refObjectCode=data)]
     * → 不再生成 data 子对象，所有 data 内部字段平铺到根对象</p>
     */
    public ParsedObject parseObject(String jsonContent, String objectCode) {
        JSONObject json = JSON.parseObject(jsonContent);
        return parseJsonObject(json, objectCode);
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

    /**
     * 铁律四：所有字段（包括嵌套）均放在根对象 fields 中，通过 refObjectCode 表达引用。
     * 不再创建 nestedObjects —— 嵌套关系通过字段的 varType=OBJECT/LIST + refObjectCode 表达。
     */
    private ParsedObject parseJsonObject(JSONObject json, String objectCode) {
        ParsedObject obj = new ParsedObject();
        obj.setObjectCode(objectCode);
        obj.setObjectLabel(objectCode);
        obj.setScriptName(objectCode);
        // 铁律四：不再递归生成子对象，所有字段（含嵌套）均放在根对象 fields 中

        for (Map.Entry<String, Object> entry : json.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            ParsedField field = new ParsedField();
            field.setFieldName(key);
            field.setFieldLabel(key);
            field.setScriptName(key);

            if (value instanceof JSONObject) {
                // 铁律四：字段直接引用嵌套对象编码，不再递归创建子 ParsedObject
                field.setVarType("OBJECT");
                field.setRefObjectCode(key);
                // 注意：不再添加 nestedObjects，嵌套结构由 refObjectCode + 服务层 refObjectId 表达
            } else if (value instanceof JSONArray) {
                field.setVarType("LIST");
                JSONArray arr = (JSONArray) value;
                if (!arr.isEmpty()) {
                    Object first = arr.get(0);
                    if (first instanceof JSONObject) {
                        field.setGenericType("OBJECT");
                        // 铁律四：数组泛型为对象时，refObjectCode 指向嵌套对象编码
                        field.setRefObjectCode(key);
                        // 不再为数组元素创建子对象
                    } else {
                        field.setGenericType(inferPrimitiveType(first));
                    }
                } else {
                    field.setGenericType("STRING");
                }
            } else {
                field.setVarType(inferPrimitiveType(value));
            }
            obj.getFields().add(field);
        }
        return obj;
    }

    private String inferPrimitiveType(Object value) {
        if (value == null) return "STRING";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof Number) return "NUMBER";
        return "STRING";
    }

}
