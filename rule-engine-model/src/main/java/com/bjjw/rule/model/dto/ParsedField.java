package com.bjjw.rule.model.dto;

import lombok.Data;

@Data
public class ParsedField {
    private String fieldName;
    private String fieldLabel;
    /** 脚本中的字段名 */
    private String scriptName;
    private String varType;
    /** 引用对象编码（兼容旧逻辑，铁律四改进后以 refObjectId 为准） */
    private String refObjectCode;
    /** 引用对象 ID（铁律四：指向 rule_data_object.id，优先于 refObjectCode） */
    private Long refObjectId;
    private String genericType;
    /**
     * 父字段 ID（用于表达嵌套层级关系）。
     * 顶层字段 parentFieldId 为 null。
     * 嵌套在某个 OBJECT 字段内部的子字段，其 parentFieldId 指向父字段在同对象中的字段 ID。
     */
    private Long parentFieldId;
}
