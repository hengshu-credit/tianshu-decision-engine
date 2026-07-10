package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 统一字段引用描述。 */
@Data
public class ResolvedField {
    private Long refId;
    private String refType;
    private String code;
    private String label;
    private String scriptName;
    private String valueType;
    private String sourceType;
    private String defaultValue;
    private String exampleValue;
    private String validValues;
    private List<String> dependencies = new ArrayList<>();
}
