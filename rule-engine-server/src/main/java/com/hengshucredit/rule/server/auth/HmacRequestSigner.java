package com.hengshucredit.rule.server.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

public final class HmacRequestSigner {

    private HmacRequestSigner() {
    }

    public static String signHex(String secret, String method, String requestUri, String rawQuery,
                                 String timestamp, String nonce, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(canonical(method, requestUri, rawQuery, timestamp, nonce, body)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to sign project request", e);
        }
    }

    public static String canonical(String method, String requestUri, String rawQuery,
                                   String timestamp, String nonce, byte[] body) {
        return value(method) + "\n"
                + value(requestUri) + "\n"
                + value(rawQuery) + "\n"
                + value(timestamp) + "\n"
                + value(nonce) + "\n"
                + sha256Hex(body == null ? new byte[0] : body);
    }

    public static String sha256Hex(byte[] value) {
        try {
            return toHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to hash project request body", e);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String toHex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte b : value) {
            builder.append(String.format("%02x", b & 0xff));
        }
        return builder.toString();
    }
}
