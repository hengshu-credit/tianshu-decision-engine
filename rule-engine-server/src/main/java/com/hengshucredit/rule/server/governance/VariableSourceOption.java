package com.hengshucredit.rule.server.governance;

public record VariableSourceOption(
        Long id,
        String code,
        String name,
        String scope,
        Long projectId,
        Long parentId,
        String parentName,
        String listType) {

    public VariableSourceOption(Long id, String code, String name, String scope,
                                Long projectId, Long parentId, String parentName) {
        this(id, code, name, scope, projectId, parentId, parentName, null);
    }
}
