package com.hengshucredit.rule.server.service;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class VariableResolveOptions {
    private boolean skipApiSources;
    private boolean forceRefreshSource;
    /** 仅沿直接引用向上游展开依赖，不根据已满足输入推导无关的下游模型。 */
    private boolean requiredNamesUpstreamOnly;
    private LocalDateTime listMatchTime;
    private Set<String> requiredScriptNames;

    public static VariableResolveOptions defaults() {
        return new VariableResolveOptions();
    }
}
