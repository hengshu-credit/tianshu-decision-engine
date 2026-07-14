package com.hengshucredit.rule.server.auth;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class CredentialCipher {

    private static final String FORMAT_VERSION = "v1";
    private static final int IV_LENGTH = 12;

    private final ProjectAuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialCipher(ProjectAuthProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String value) {
        if (value == null) {
            return null;
        }
        String keyId = properties.getActiveKeyId();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(keyId), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();
            return FORMAT_VERSION + ":" + keyId + ":" + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt project credential", e);
        }
    }

    public String decrypt(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(":", 3);
        if (parts.length != 3 || !FORMAT_VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported credential ciphertext format");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(parts[2]);
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Invalid credential ciphertext");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, iv.length);
            System.arraycopy(payload, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(parts[1]), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Unable to decrypt project credential", e);
        }
    }

    public String lookupKey(String authType, String identifier) {
        if (authType == null || identifier == null) {
            return null;
        }
        return sha256Hex(authType + ":" + identifier);
    }

    private SecretKeySpec key(String keyId) {
        String material = properties.getMasterKeys().get(keyId);
        if (material == null || material.isEmpty()) {
            throw new IllegalStateException("Missing project authentication master key: " + keyId);
        }
        try {
            byte[] key = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(key, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to initialize project authentication key", e);
        }
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b & 0xff));
            }
            return builder.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to hash project credential", e);
        }
    }
}
