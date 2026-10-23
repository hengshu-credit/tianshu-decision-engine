package com.hengshucredit.rule.server.governance;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Explicit clear is distinct from a masked blank value, which means preserve. */
public final class CredentialChanges {
    public static final String CLEAR_FIELD = "clearCredentials";
    public static final Set<String> DATABASE_FIELDS = Set.of("password", "sshPassword", "sshPrivateKey", "sshPassphrase");
    public static final Set<String> SFTP_FIELDS = Set.of("password", "privateKey", "passphrase", "knownHosts");

    private CredentialChanges() { }

    public static Set<String> clearFields(Object raw, Set<String> allowed, Function<String, Object> suppliedValue) {
        if (raw == null) return Set.of();
        if (!(raw instanceof List<?> fields)) throw new IllegalArgumentException("待清除凭据必须为字段列表");
        Set<String> result = new LinkedHashSet<>();
        for (Object item : fields) {
            if (!(item instanceof String field) || !allowed.contains(field))
                throw new IllegalArgumentException("不能清除非凭据字段");
            Object value = suppliedValue.apply(field);
            if (value != null && !value.toString().isBlank())
                throw new IllegalArgumentException("同一凭据不能同时替换和清除: " + field);
            result.add(field);
        }
        return result;
    }
}
