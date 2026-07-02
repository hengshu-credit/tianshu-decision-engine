package com.hengshucredit.rule.server.service;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class VariableResolveOptions {
    private boolean skipApiSources;
    private boolean forceRefreshSource;
    private LocalDateTime listMatchTime;
    private Set<String> requiredScriptNames;

    public static VariableResolveOptions defaults() {
        return new VariableResolveOptions();
    }
}
