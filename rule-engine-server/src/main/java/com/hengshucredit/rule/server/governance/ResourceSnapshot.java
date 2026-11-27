package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.artifact.CanonicalJson;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ResourceSnapshot(String snapshotJson,
                               String effectiveStatus,
                               String secretPayloadCiphertext,
                               String secretDigest) {
    public ResourceSnapshot {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            throw new IllegalArgumentException("资源快照不能为空");
        }
        effectiveStatus = effectiveStatus == null
                ? "ACTIVE" : effectiveStatus;
    }

    private static final Set<String> JSON_FIELDS = Set.of("modelJson", "openApiConfigJson", "paramsJson",
            "sourceConfig", "executionConfig", "modelConfig", "authConfig", "authApiConfig", "queryConfig",
            "headerConfig", "requestMapping", "responseMapping", "cacheKeyConfig", "payloadCaptureConfig",
            "successCondition", "exceptionCondition", "retryCondition", "tokenFailureCondition", "billingCondition");

    /** Compare sanitized business fields and the stable credential digest, never randomized ciphertext. */
    static boolean sameConfiguration(String type, ResourceSnapshot left, ResourceSnapshot right) {
        if (!Objects.equals(left.secretDigest(), right.secretDigest())) return false;
        Map<String, Object> a = businessFields(type, left);
        Map<String, Object> b = businessFields(type, right);
        // Update and import callers may specify the root ID solely in the authenticated request target.
        if (a.containsKey("id")) b.putIfAbsent("id", a.get("id"));
        if (!a.keySet().equals(b.keySet())) return false;
        for (var entry : a.entrySet()) {
            Object previous = entry.getValue();
            Object proposed = b.get(entry.getKey());
            if (Objects.equals(previous, proposed)) continue;
            if (!(previous instanceof String oldJson && proposed instanceof String newJson
                    && JSON_FIELDS.contains(entry.getKey()) && CanonicalJson.sameDocument(oldJson, newJson))) return false;
        }
        return true;
    }

    private static Map<String, Object> businessFields(String type, ResourceSnapshot snapshot) {
        var fields = CanonicalJson.readMap(snapshot.snapshotJson());
        fields.keySet().removeAll(Set.of("createTime", "updateTime", "createBy", "updateBy"));
        fields.values().removeIf(Objects::isNull);
        if ("VARIABLE".equals(type) && List.of().equals(fields.get("options"))) fields.remove("options");
        return fields;
    }

    public static ResourceSnapshot ofJson(String snapshotJson) {
        return new ResourceSnapshot(snapshotJson, "ACTIVE", null, null);
    }
}
