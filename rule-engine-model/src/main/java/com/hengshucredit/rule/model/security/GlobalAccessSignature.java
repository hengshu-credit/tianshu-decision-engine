package com.hengshucredit.rule.model.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Ed25519 request signing shared by the server and both Java clients. */
public final class GlobalAccessSignature {

    public static final String ALGORITHM = "Ed25519";
    public static final String DEFAULT_KEY_ID = "default";
    public static final String KEY_ID_HEADER = "X-Rule-Global-Key-Id";
    public static final String TIMESTAMP_HEADER = "X-Rule-Global-Timestamp";
    public static final String NONCE_HEADER = "X-Rule-Global-Nonce";
    public static final String SIGNATURE_HEADER = "X-Rule-Global-Signature";

    private GlobalAccessSignature() {
    }

    public static AccessKeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            java.security.KeyPair pair = generator.generateKeyPair();
            return new AccessKeyPair(encode(pair.getPrivate()), encode(pair.getPublic()));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate global access key pair", e);
        }
    }

    public static String sign(String privateKey, String method, String requestUri, String rawQuery,
                              String timestamp, String nonce, byte[] body) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(decodePrivateKey(privateKey));
            signature.update(canonical(method, requestUri, rawQuery, timestamp, nonce, body)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to sign global access request", e);
        }
    }

    public static boolean verify(String publicKey, String method, String requestUri, String rawQuery,
                                 String timestamp, String nonce, byte[] body, String encodedSignature) {
        if (encodedSignature == null || encodedSignature.isBlank()) return false;
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(decodePublicKey(publicKey));
            signature.update(canonical(method, requestUri, rawQuery, timestamp, nonce, body)
                    .getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(encodedSignature));
        } catch (Exception e) {
            return false;
        }
    }

    public static String canonical(String method, String requestUri, String rawQuery,
                                   String timestamp, String nonce, byte[] body) {
        return value(method) + "\n"
                + value(requestUri) + "\n"
                + value(rawQuery) + "\n"
                + sha256Hex(body == null ? new byte[0] : body) + "\n"
                + value(timestamp) + "\n"
                + value(nonce);
    }

    public static PrivateKey decodePrivateKey(String encoded) {
        if (encoded == null || encoded.isBlank()) throw new IllegalArgumentException("Key is required");
        try {
            KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
            return factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded.trim())));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Ed25519 private key", e);
        }
    }

    public static PublicKey decodePublicKey(String encoded) {
        if (encoded == null || encoded.isBlank()) throw new IllegalArgumentException("Key is required");
        try {
            KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
            return factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded.trim())));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Ed25519 public key", e);
        }
    }

    private static String encode(java.security.Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private static String sha256Hex(byte[] value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash global access request", e);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    public record AccessKeyPair(String privateKey, String publicKey) {
    }
}
