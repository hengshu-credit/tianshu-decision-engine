package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectAuthDTO {
    private Long id;
    private Long projectId;
    private String authCode;
    private String authName;
    private String authType;
    private String identifier;
    private String identifierMasked;
    private String secret;
    private String secretMasked;
    private String placement;
    private String parameterName;
    private String accessPolicyJson;
    private Integer asyncAccessLogEnabled;
    private Integer tokenTtlSeconds;
    private Integer tokenGraceSeconds;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
