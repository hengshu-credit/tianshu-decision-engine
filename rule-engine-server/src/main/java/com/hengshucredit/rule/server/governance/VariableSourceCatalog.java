package com.hengshucredit.rule.server.governance;

import java.util.List;

public record VariableSourceCatalog(
        List<VariableSourceOption> apiOptions,
        List<VariableSourceOption> databaseOptions,
        List<VariableSourceOption> listOptions,
        List<VariableSourceOption> sftpOptions) {
    public VariableSourceCatalog(List<VariableSourceOption> apiOptions,
                                 List<VariableSourceOption> databaseOptions,
                                 List<VariableSourceOption> listOptions) {
        this(apiOptions, databaseOptions, listOptions, List.of());
    }
}
