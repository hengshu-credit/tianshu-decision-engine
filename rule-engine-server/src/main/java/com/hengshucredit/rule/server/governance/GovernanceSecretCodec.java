package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class GovernanceSecretCodec {

    public static final String SECRET_STATE_KEY = "_secretConfigured";

    private final CredentialCipher cipher;

    public GovernanceSecretCodec(CredentialCipher cipher) {
        this.cipher = cipher;
    }

    public ResourceSnapshot normalize(ResourceSnapshot draft,
                                      Set<String> sensitiveKeys) {
        Map<String, Object> publicValue =
                new LinkedHashMap<>(CanonicalJson.readMap(
                        draft.snapshotJson()));
        Set<String> cleared = CredentialChanges.clearFields(publicValue.remove(CredentialChanges.CLEAR_FIELD),
                sensitiveKeys == null ? Set.of() : sensitiveKeys, publicValue::get);
        String ciphertext = draft.secretPayloadCiphertext();
        Map<String, Object> previousSecrets = ciphertext == null || ciphertext.isBlank()
                ? Map.of() : CanonicalJson.readMap(cipher.decrypt(ciphertext));
        Map<String, Object> secrets = new LinkedHashMap<>(previousSecrets);
        // A removed nested configuration must not regain its former credentials.
        secrets.keySet().removeIf(path -> !hasParent(publicValue, path));
        Map<String, Boolean> configured = new LinkedHashMap<>();
        extract(publicValue, "", normalizeKeys(sensitiveKeys),
                secrets);
        // Empty strings survive entity updates (SQL NULL would be skipped by updateById).
        cleared.forEach(field -> secrets.put("/" + escape(field), ""));
        secrets.forEach((path, value) -> configured.put(path, isConfigured(value)));

        String digest = draft.secretDigest();
        if (!secrets.equals(previousSecrets)) {
            String secretJson = secrets.isEmpty() ? null : CanonicalJson.write(secrets);
            ciphertext = secretJson == null ? null : cipher.encrypt(secretJson);
            digest = secretJson == null ? null : Sha256Digests.text(secretJson);
        }
        publicValue.remove(SECRET_STATE_KEY);
        if (!configured.isEmpty()) {
            publicValue.put(SECRET_STATE_KEY, configured);
        }
        return new ResourceSnapshot(
                CanonicalJson.write(publicValue),
                draft.effectiveStatus(),
                ciphertext,
                digest);
    }

    public Map<String, Object> restore(ResourceSnapshot snapshot) {
        Map<String, Object> value = new LinkedHashMap<>(
                CanonicalJson.readMap(snapshot.snapshotJson()));
        value.remove(SECRET_STATE_KEY);
        if (snapshot.secretPayloadCiphertext() == null
                || snapshot.secretPayloadCiphertext().isBlank()) {
            return value;
        }
        Map<String, Object> secrets = CanonicalJson.readMap(
                cipher.decrypt(snapshot.secretPayloadCiphertext()));
        for (Map.Entry<String, Object> entry : secrets.entrySet()) {
            restorePath(value, entry.getKey(), entry.getValue());
        }
        return value;
    }

    private void extract(Object node,
                         String path,
                         Set<String> sensitiveKeys,
                         Map<String, Object> secrets) {
        if (node instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rawMap;
            List<String> keys = new ArrayList<>(map.keySet());
            for (String key : keys) {
                if (SECRET_STATE_KEY.equals(key)) {
                    continue;
                }
                Object value = map.get(key);
                String childPath = path + "/" + escape(key);
                if (sensitiveKeys.contains(
                        key.toLowerCase(Locale.ROOT))) {
                    if (!secrets.containsKey(childPath) || isConfigured(value)) {
                        secrets.put(childPath, value);
                    }
                    map.remove(key);
                } else {
                    extract(value, childPath, sensitiveKeys, secrets);
                }
            }
        } else if (node instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                extract(list.get(index), path + "/" + index,
                        sensitiveKeys, secrets);
            }
        }
    }

    private boolean hasParent(Map<String, Object> root, String path) {
        String[] parts = path.split("/");
        Object current = root;
        for (int i = 1; i < parts.length - 1; i++) {
            String part = unescape(parts[i]);
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else if (current instanceof List<?> list) {
                int index = Integer.parseInt(part);
                current = index < list.size() ? list.get(index) : null;
            } else {
                return false;
            }
        }
        return current instanceof Map<?, ?>;
    }

    private void restorePath(Map<String, Object> root,
                             String path,
                             Object secret) {
        String[] parts = path.split("/");
        Object current = root;
        for (int i = 1; i < parts.length; i++) {
            String part = unescape(parts[i]);
            boolean last = i == parts.length - 1;
            if (current instanceof Map<?, ?> rawMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map =
                        (Map<String, Object>) rawMap;
                if (last) {
                    map.put(part, secret);
                    return;
                }
                current = map.get(part);
            } else if (current instanceof List<?> list) {
                int index = Integer.parseInt(part);
                if (last) {
                    @SuppressWarnings("unchecked")
                    List<Object> values = (List<Object>) list;
                    values.set(index, secret);
                    return;
                }
                current = list.get(index);
            }
            if (current == null) {
                throw new IllegalArgumentException(
                        "敏感字段路径与资源快照不匹配: " + path);
            }
        }
    }

    private Set<String> normalizeKeys(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }
        return keys.stream()
                .map(key -> key.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean isConfigured(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private String unescape(String value) {
        return value.replace("~1", "/").replace("~0", "~");
    }
}
