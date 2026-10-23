package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.artifact.CanonicalJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class JsonResourceDiff {

    private JsonResourceDiff() {
    }

    public static ResourceDiff compare(ResourceSnapshot left,
                                       ResourceSnapshot right) {
        Map<String, Object> leftValue = snapshotMap(left);
        Map<String, Object> rightValue = snapshotMap(right);
        Map<String, Boolean> leftSecrets =
                secretStates(leftValue.remove(
                        GovernanceSecretCodec.SECRET_STATE_KEY));
        Map<String, Boolean> rightSecrets =
                secretStates(rightValue.remove(
                        GovernanceSecretCodec.SECRET_STATE_KEY));
        List<ResourceDiff.FieldDiff> fields = new ArrayList<>();
        compareValue("$", "资源配置", leftValue, rightValue, fields);
        addSecretStateDiffs(leftSecrets, rightSecrets, fields);
        if (!Objects.equals(secretDigest(left),
                secretDigest(right))) {
            fields.add(new ResourceDiff.FieldDiff(
                    "$.credentials", "凭据内容",
                    credentialLabel(left), credentialLabel(right),
                    "MODIFIED", true, true));
        }
        long changed = fields.stream()
                .filter(ResourceDiff.FieldDiff::changed)
                .count();
        return new ResourceDiff(
                changed == 0 ? "无配置差异" : "共 " + changed + " 项变更",
                fields,
                CanonicalJson.write(Map.of(
                        "left", leftValue,
                        "right", rightValue)));
    }

    private static void compareValue(
            String path,
            String label,
            Object left,
            Object right,
            List<ResourceDiff.FieldDiff> fields) {
        if (left instanceof Map<?, ?>
                || right instanceof Map<?, ?>) {
            Map<String, Object> leftValues = stringMap(
                    left instanceof Map<?, ?> map ? map : null);
            Map<String, Object> rightValues = stringMap(
                    right instanceof Map<?, ?> map ? map : null);
            Set<String> keys = new LinkedHashSet<>(leftValues.keySet());
            keys.addAll(rightValues.keySet());
            for (String key : keys) {
                compareValue(path + "." + key, key,
                        leftValues.get(key), rightValues.get(key), fields);
            }
            return;
        }
        if (left instanceof List<?>
                || right instanceof List<?>) {
            List<?> leftValues = left instanceof List<?> list
                    ? list : List.of();
            List<?> rightValues = right instanceof List<?> list
                    ? list : List.of();
            int size = Math.max(leftValues.size(), rightValues.size());
            for (int index = 0; index < size; index++) {
                compareValue(path + "[" + index + "]",
                        label + "[" + index + "]",
                        index < leftValues.size()
                                ? leftValues.get(index) : null,
                        index < rightValues.size()
                                ? rightValues.get(index) : null,
                        fields);
            }
            return;
        }
        boolean changed = !Objects.equals(left, right);
        fields.add(new ResourceDiff.FieldDiff(
                path, label, left, right,
                changeType(left, right, changed),
                false, changed));
    }

    private static void addSecretStateDiffs(
            Map<String, Boolean> left,
            Map<String, Boolean> right,
            List<ResourceDiff.FieldDiff> fields) {
        Set<String> paths = new LinkedHashSet<>(left.keySet());
        paths.addAll(right.keySet());
        for (String path : paths) {
            Boolean leftValue = left.get(path);
            Boolean rightValue = right.get(path);
            boolean changed = !Objects.equals(leftValue, rightValue);
            fields.add(new ResourceDiff.FieldDiff(
                    "$.credentials" + path,
                    "凭据 " + path,
                    configuredLabel(leftValue),
                    configuredLabel(rightValue),
                    changeType(leftValue, rightValue, changed),
                    true, changed));
        }
    }

    private static String credentialLabel(ResourceSnapshot snapshot) {
        Map<String, Boolean> configured = secretStates(snapshotMap(snapshot).get(GovernanceSecretCodec.SECRET_STATE_KEY));
        if (!configured.isEmpty() && configured.values().stream().noneMatch(Boolean.TRUE::equals)) return "未配置";
        return snapshot == null || snapshot.secretDigest() == null
                ? "未配置" : "已配置（内容受保护）";
    }

    private static String secretDigest(ResourceSnapshot snapshot) {
        return snapshot == null ? null : snapshot.secretDigest();
    }

    private static String configuredLabel(Boolean value) {
        return Boolean.TRUE.equals(value) ? "已配置" : "未配置";
    }

    private static String changeType(Object left,
                                     Object right,
                                     boolean changed) {
        if (!changed) {
            return "UNCHANGED";
        }
        if (left == null) {
            return "ADDED";
        }
        if (right == null) {
            return "REMOVED";
        }
        return "MODIFIED";
    }

    private static Map<String, Object> snapshotMap(
            ResourceSnapshot snapshot) {
        return snapshot == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(CanonicalJson.readMap(
                snapshot.snapshotJson()));
    }

    private static Map<String, Boolean> secretStates(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Boolean> result = new LinkedHashMap<>();
        map.forEach((key, configured) -> result.put(
                String.valueOf(key), Boolean.TRUE.equals(configured)));
        return result;
    }

    private static Map<String, Object> stringMap(Map<?, ?> value) {
        if (value == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) ->
                result.put(String.valueOf(key), item));
        return result;
    }
}
