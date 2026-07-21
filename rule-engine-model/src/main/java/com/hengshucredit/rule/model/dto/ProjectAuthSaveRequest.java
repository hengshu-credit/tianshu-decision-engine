package com.hengshucredit.rule.model.dto;

import lombok.Data;

@Data
public class ProjectAuthSaveRequest {
    private Long id;
    private String authCode;
    private String authName;
    private String authType;
    private String identifier;
    private String secret;
    private String placement;
    private String parameterName;
    private String accessPolicyJson;
    private Integer asyncAccessLogEnabled;
    private Integer tokenTtlSeconds;
    private Integer tokenGraceSeconds;
    private Integer status;
}
