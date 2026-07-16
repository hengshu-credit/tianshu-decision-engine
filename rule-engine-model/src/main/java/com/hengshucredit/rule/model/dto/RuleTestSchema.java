package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 统一测试字段结构响应。 */
@Data
public class RuleTestSchema {
    private List<ResolvedField> inputs = new ArrayList<>();
    private List<ResolvedField> runtimeNodes = new ArrayList<>();
    private List<ResolvedField> outputs = new ArrayList<>();
    private Map<String, Object> sampleParams = new LinkedHashMap<>();
    private List<String> diagnostics = new ArrayList<>();
}
