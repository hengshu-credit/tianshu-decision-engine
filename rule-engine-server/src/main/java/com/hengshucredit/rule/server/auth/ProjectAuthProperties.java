package com.hengshucredit.rule.server.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "rule-engine.project-auth")
public class ProjectAuthProperties {
    private String activeKeyId = "v1";
    private Map<String, String> masterKeys = new LinkedHashMap<>();
    private long hmacTimeWindowSeconds = 300L;
    private int tokenFailureLimit = 10;
    private long tokenFailureWindowSeconds = 300L;
    private long tokenLockSeconds = 900L;
    private List<String> trustedProxyCidrs = new ArrayList<>();
    private int guardRegistryMaxEntries = 2048;

    @PostConstruct
    public void validateMasterKeys() {
        if (activeKeyId == null || activeKeyId.trim().isEmpty()) {
            throw new IllegalStateException("rule-engine.project-auth.active-key-id must be configured");
        }
        String activeKey = masterKeys.get(activeKeyId);
        if (activeKey == null || activeKey.trim().isEmpty()) {
            throw new IllegalStateException("Active project auth master key must be configured");
        }
        if ("tianshu-dev-auth-master-key-change-me".equals(activeKey)
                || activeKey.length() < 32
                || activeKey.chars().distinct().count() < 8) {
            throw new IllegalStateException(
                    "Project auth master key must be a private, non-repetitive value of at least 32 characters");
        }
    }
}
