package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 精确修订的只读字段视图；ID以字符串传输，避免浏览器丢失大整数精度。 */
@Data
public class RuleRevisionFieldView {
    private String definitionId;
    private String revisionId;
    private Integer revisionNo;
    private String revisionState;
    private String status;
    private String source;
    private boolean readOnly = true;
    private String referenceNamespace;
    private String validationSource;
    private String modelType;
    private String artifactId;
    private String artifactDigest;
    private List<Map<String, Object>> inputFields = new ArrayList<>();
    private List<Map<String, Object>> outputFields = new ArrayList<>();
    private List<Map<String, Object>> diagnostics = new ArrayList<>();
    private Map<String, Object> inputPropertySchemas = new LinkedHashMap<>();
    private Map<String, Object> outputPropertySchemas = new LinkedHashMap<>();
    private Set<String> localOutputNames = new LinkedHashSet<>();
}
