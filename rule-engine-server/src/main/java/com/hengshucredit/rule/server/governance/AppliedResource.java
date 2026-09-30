package com.hengshucredit.rule.server.governance;

public record AppliedResource(Long resourceId,
                              Integer versionNo,
                              String effectiveStatus,
                              Long artifactId,
                              java.util.Map<Long, Long> fieldIdMapping) {
    public AppliedResource(Long resourceId, Integer versionNo, String effectiveStatus, Long artifactId) {
        this(resourceId, versionNo, effectiveStatus, artifactId, java.util.Map.of());
    }

    public AppliedResource {
        fieldIdMapping = java.util.Map.copyOf(fieldIdMapping);
    }
}
