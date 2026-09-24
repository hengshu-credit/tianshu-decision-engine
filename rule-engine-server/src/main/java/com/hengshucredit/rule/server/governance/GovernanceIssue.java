package com.hengshucredit.rule.server.governance;

public record GovernanceIssue(String severity,
                              String code,
                              String message,
                              String resourceType,
                              Long resourceId,
                              String referencePath,
                              String fixPath,
                              String title,
                              String nextAction) {
    public GovernanceIssue(String severity, String code, String message,
                           String resourceType, Long resourceId,
                           String referencePath, String fixPath) {
        this(severity, code, message, resourceType, resourceId, referencePath,
                fixPath, defaultTitle(code), defaultNextAction(code));
    }

    public boolean isError() {
        return "ERROR".equalsIgnoreCase(severity);
    }

    public static GovernanceIssue error(String code, String message,
                                        String resourceType, Long resourceId,
                                        String referencePath) {
        return new GovernanceIssue(
                "ERROR", code, message, resourceType, resourceId,
                referencePath, null, defaultTitle(code), defaultNextAction(code));
    }

    private static String defaultTitle(String code) {
        if (code == null || code.isBlank()) return "资源校验问题";
        if (code.contains("DEPENDENCY")) return "依赖校验未通过";
        if (code.contains("REFERENCE")) return "引用关系需要修复";
        return "资源校验问题";
    }

    private static String defaultNextAction(String code) {
        if (code != null && code.contains("DEPENDENCY")) return "修复依赖后重新执行预检";
        return "按引用位置修复后重新执行预检";
    }
}
