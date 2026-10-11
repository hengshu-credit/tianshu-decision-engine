package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.GovernedResourceVersion;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Business versions have mutable heads; their revision records remain immutable. */
public final class ResourceVersionLines {
    private ResourceVersionLines() { }

    public static List<GovernedResourceVersion> heads(List<GovernedResourceVersion> history) {
        Map<Integer, GovernedResourceVersion> result = new LinkedHashMap<>();
        history.stream().sorted(Comparator.comparing(GovernedResourceVersion::getVersionNo).reversed())
                .forEach(version -> result.putIfAbsent(version.getDisplayVersion(), version));
        return result.values().stream().sorted(Comparator.comparing(GovernedResourceVersion::getDisplayVersion).reversed()).toList();
    }

    public static GovernedResourceVersion head(List<GovernedResourceVersion> history, GovernedResourceVersion anchor) {
        return heads(history).stream().filter(version -> Objects.equals(version.getDisplayVersion(), anchor.getDisplayVersion()))
                .filter(version -> !Boolean.TRUE.equals(version.getDeleted())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("指定业务版本已删除或不存在"));
    }

    public static boolean published(GovernedResourceVersion version) {
        return version != null && !Boolean.TRUE.equals(version.getDeleted())
                && java.util.Set.of("ACTIVE", "ENABLED", "PUBLISHED", "1").contains(String.valueOf(version.getEffectiveStatus()));
    }

    public static ResourceSnapshot snapshot(GovernedResourceVersion version) {
        return new ResourceSnapshot(version.getSnapshotJson(), version.getEffectiveStatus(),
                version.getSecretPayloadCiphertext(), version.getSecretDigest());
    }
}
