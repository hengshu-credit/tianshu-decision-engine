package com.hengshucredit.rule.core.function;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

/** 决策引擎不可逆字段摘要函数。 */
public class DigestBuiltinFunctions {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public String md5(String text) {
        return digest("MD5", text);
    }

    public String sha1(String text) {
        return digest("SHA-1", text);
    }

    public String sha256(String text) {
        return digest("SHA-256", text);
    }

    public String hmacSha256(String text, String key) {
        if (text == null || key == null || key.isEmpty()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("当前运行环境不支持 HMAC-SHA256", e);
        }
    }

    private static String digest(String algorithm, String text) {
        if (text == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return toHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("当前运行环境不支持摘要算法: " + algorithm, e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            chars[i * 2] = HEX[value >>> 4];
            chars[i * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(chars);
    }
}
