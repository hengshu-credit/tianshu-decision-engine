package com.hengshucredit.rule.server.transfer;

import java.util.Map;

/** 只描述预览/导入选择，不在反序列化时执行任何数据库写入。 */
public record TransferImportOptions(Long targetProjectId,
                                    String targetScope,
                                    String variablePolicy,
                                    String resourcePolicy,
                                    String suffix,
                                    Boolean createProject,
                                    String projectCode,
                                    String projectName,
                                    Map<String, Long> projectBindings) {
    public String normalizedScope() {
        return "GLOBAL".equalsIgnoreCase(targetScope) ? "GLOBAL" : "PROJECT";
    }
    public String normalizedVariablePolicy() {
        return "REUSE".equalsIgnoreCase(variablePolicy) ? "REUSE" : "SUFFIX";
    }
    public String normalizedResourcePolicy() {
        if ("OVERWRITE".equalsIgnoreCase(resourcePolicy)) return "OVERWRITE";
        if ("REUSE".equalsIgnoreCase(resourcePolicy)) return "REUSE";
        return "SUFFIX";
    }
    public String normalizedSuffix() {
        return suffix == null || suffix.isBlank() ? "_imported" : suffix.trim();
    }
}
