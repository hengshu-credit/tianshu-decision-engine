package com.hengshucredit.rule.server.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ModelValidationReport {
    private boolean valid;
    private String modelFormat;
    private String modelDigest;
    private String sampleStatus;
    private Map<String, Object> inputSchema;
    private Map<String, Object> outputSchema;
    private Map<String, Object> runtimeConstraints;
    private List<String> warnings = new ArrayList<>();
}
