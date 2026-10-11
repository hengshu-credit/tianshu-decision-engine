package com.hengshucredit.rule.server.governance;

import java.util.List;

public record GovernancePreflightReport(
        boolean valid,
        List<GovernanceIssue> errors,
        List<GovernanceIssue> warnings,
        List<ResolvedDependency> dependencies,
        String dependencyDigest) {

    public GovernancePreflightReport {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        dependencies = dependencies == null
                ? List.of() : List.copyOf(dependencies);
    }

    public record ResolvedDependency(
            String targetResourceType,
            Long targetResourceId,
            Long targetVersionId,
            Integer targetVersionNo,
            String referencePath,
            String relationType,
            boolean required,
            String resolutionStatus,
            String targetDigest,
            String issueCode,
            String issueMessage,
            DependencyVersionSelection versionSelection) {
        public ResolvedDependency(String targetResourceType, Long targetResourceId, Long targetVersionId, Integer targetVersionNo,
                                  String referencePath, String relationType, boolean required, String resolutionStatus,
                                  String targetDigest, String issueCode, String issueMessage) {
            this(targetResourceType, targetResourceId, targetVersionId, targetVersionNo, referencePath, relationType,
                    required, resolutionStatus, targetDigest, issueCode, issueMessage, null);
        }
    }
}
