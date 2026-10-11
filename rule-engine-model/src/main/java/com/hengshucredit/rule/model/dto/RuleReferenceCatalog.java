package com.hengshucredit.rule.model.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Display-only references belonging to one selected source; never contains executable implementations. */
@Data
public class RuleReferenceCatalog {
    private String status = "UNAVAILABLE";
    private String referenceNamespace = "ARTIFACT_SOURCE";
    private RuleTestExecutionIdentity executionIdentity;
    private boolean imported;
    private boolean selectedArtifactImported;
    private boolean localEditingAllowed;
    private List<Map<String, Object>> variables = new ArrayList<>();
    private List<Map<String, Object>> objectTree = new ArrayList<>();
    private List<Map<String, Object>> models = new ArrayList<>();
    private List<Map<String, Object>> functions = new ArrayList<>();
    private List<Map<String, Object>> rules = new ArrayList<>();
    private List<Map<String, Object>> apis = new ArrayList<>();
    private List<Map<String, Object>> lists = new ArrayList<>();
    private Map<String, String> displayLabels = new LinkedHashMap<>();
    private List<String> diagnostics = new ArrayList<>();
}
