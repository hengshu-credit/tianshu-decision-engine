package com.hengshucredit.rule.server.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "rule-engine.project-auth")
public class ProjectAuthProperties {
    private String activeKeyId = "v1";
    private Map<String, String> masterKeys = new LinkedHashMap<>();
    private long hmacTimeWindowSeconds = 300L;
    private int tokenFailureLimit = 10;
    private long tokenFailureWindowSeconds = 300L;
    private long tokenLockSeconds = 900L;
}
