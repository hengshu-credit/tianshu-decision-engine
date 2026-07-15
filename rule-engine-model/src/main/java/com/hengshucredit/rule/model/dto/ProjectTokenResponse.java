package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectTokenResponse {
    private String accessToken;
    private String tokenType;
    private String tokenCode;
    private String projectCode;
    private String authCode;
    private String authType;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime graceExpiresAt;
    private Integer expiresInSeconds;
    private Integer graceExpiresInSeconds;
}
