package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectAuthTokenDTO {
    private Long id;
    private Long projectId;
    private Long authId;
    private String tokenCode;
    private String accessToken;
    private String tokenMasked;
    private LocalDateTime issuedTime;
    private LocalDateTime expireTime;
    private LocalDateTime graceExpireTime;
    private LocalDateTime lastUsedTime;
    private LocalDateTime revokedTime;
    private Integer status;
}
