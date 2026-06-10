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
}
