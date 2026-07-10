package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 规则、模型或变量解析后的统一依赖计划。 */
@Data
public class ResolutionPlan {
    private List<ResolvedField> externalInputs = new ArrayList<>();
    private List<ResolvedField> runtimeNodes = new ArrayList<>();
    private List<ResolvedField> outputs = new ArrayList<>();
    private List<String> diagnostics = new ArrayList<>();
}
