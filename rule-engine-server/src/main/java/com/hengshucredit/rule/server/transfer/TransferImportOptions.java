package com.hengshucredit.rule.server.transfer;

import java.util.Map;

/**
 * 只描述预览/导入选择，不在反序列化时执行任何数据库写入。
 * projectBindings 的 key 是离线包中的源项目 ID 字符串，value 是目标项目 ID；
 * 项目级导入必须使用 targetProjectId、projectBindings 或 createProject 三者之一明确给出归属。
 * publishRules 只有显式开启时才会把导入的规则发布并重建固定版本绑定，默认保持草稿。
 */
public record TransferImportOptions(Long targetProjectId,
                                    String targetScope,
                                    String variablePolicy,
                                    String resourcePolicy,
                                    String suffix,
                                    Boolean createProject,
                                    String projectCode,
                                    String projectName,
                                    Map<String, Long> projectBindings,
                                    Boolean publishRules) {
    public TransferImportOptions(Long targetProjectId, String targetScope,
                                 String variablePolicy, String resourcePolicy,
                                 String suffix, Boolean createProject,
                                 String projectCode, String projectName,
                                 Map<String, Long> projectBindings) {
        this(targetProjectId, targetScope, variablePolicy, resourcePolicy, suffix,
                createProject, projectCode, projectName, projectBindings, false);
    }
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
