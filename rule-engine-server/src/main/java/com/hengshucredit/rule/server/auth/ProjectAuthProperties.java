package com.hengshucredit.rule.server.auth;

import com.hengshucredit.rule.model.security.GlobalAccessSignature;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "rule-engine.project-auth")
public class ProjectAuthProperties {
    /** Stable ID derived from the active master key; it is not a user-facing setting. */
    private String activeKeyId;
    private Map<String, String> masterKeys = new LinkedHashMap<>();
    private long hmacTimeWindowSeconds = 300L;
    private int tokenFailureLimit = 10;
    private long tokenFailureWindowSeconds = 300L;
    private long tokenLockSeconds = 900L;
    private List<String> trustedProxyCidrs = new ArrayList<>();
    private int guardRegistryMaxEntries = 2048;
    private GlobalAccess globalAccess = new GlobalAccess();

    @PostConstruct
    public void validateMasterKeys() {
        String activeKey = resolveActiveKey();
        if (activeKey == null || activeKey.trim().isEmpty()) {
            throw new IllegalStateException("Project auth master key must be configured");
        }
        if ("tianshu-dev-auth-master-key-change-me".equals(activeKey)
                || activeKey.length() < 32
                || activeKey.chars().distinct().count() < 8) {
            throw new IllegalStateException(
                    "Project auth master key must be a private, non-repetitive value of at least 32 characters");
        }
        if (activeKeyId == null || activeKeyId.trim().isEmpty()) {
            activeKeyId = generatedKeyId(activeKey);
            masterKeys.putIfAbsent(activeKeyId, activeKey);
            // Existing v2 ciphertexts remain decryptable after the setting is removed.
            masterKeys.putIfAbsent("v2", activeKey);
        }
        if (globalAccess != null && globalAccess.isEnabled()) {
            if (globalAccess.getKeyId() == null || globalAccess.getKeyId().trim().isEmpty()) {
                throw new IllegalStateException("Global access key id must be configured");
            }
            if (globalAccess.getPublicKey() == null || globalAccess.getPublicKey().trim().isEmpty()) {
                throw new IllegalStateException("Global access public key must be configured");
            }
            if (globalAccess.getTimeWindowSeconds() <= 0 || globalAccess.getTimeWindowSeconds() > 86400) {
                throw new IllegalStateException("Global access time window must be between 1 and 86400 seconds");
            }
            GlobalAccessSignature.decodePublicKey(globalAccess.getPublicKey());
        }
    }

    private String resolveActiveKey() {
        if (activeKeyId != null && !activeKeyId.trim().isEmpty()) {
            return masterKeys.get(activeKeyId);
        }
        if (masterKeys == null) {
            return null;
        }
        return masterKeys.values().stream()
                .filter(value -> value != null && !value.trim().isEmpty())
                .findFirst()
                .orElse(null);
    }

    private String generatedKeyId(String masterKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int index = 0; index < 8; index++) {
                hex.append(String.format("%02x", digest[index] & 0xff));
            }
            return "auto-" + hex;
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("Unable to generate project auth key ID", error);
        }
    }

    @Data
    public static class GlobalAccess {
        private boolean enabled;
        private String keyId = GlobalAccessSignature.DEFAULT_KEY_ID;
        private String publicKey;
        private long timeWindowSeconds = 300L;
    }
}
