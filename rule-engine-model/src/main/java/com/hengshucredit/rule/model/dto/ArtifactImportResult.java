package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ArtifactImportResult {
    private Long artifactId;
    private String artifactDigest;
    private String packageDigest;
    private boolean duplicate;
    private boolean compatible;
    private String compatibilityReportJson;
    private List<String> requiredBindingComponentIds = new ArrayList<>();
}
