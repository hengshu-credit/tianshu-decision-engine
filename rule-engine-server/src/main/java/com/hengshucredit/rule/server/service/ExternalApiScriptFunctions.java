package com.hengshucredit.rule.server.service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 外数 API 脚本可使用的受控编码、摘要和密码函数。 */
public class ExternalApiScriptFunctions {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public String apiUuid32() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public long apiTimestampMillis() {
        return System.currentTimeMillis();
    }

    public String apiTimestamp(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("时间格式不能为空");
        }
        return new SimpleDateFormat(pattern, Locale.ROOT).format(new Date());
    }

    public String apiUrlEncode(String value) {
        if (value == null) return null;
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new IllegalArgumentException("URL 编码失败");
        }
    }

    public String apiBase64Encode(String value) {
        if (value == null) return null;
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public String apiBase64Decode(String value) {
        if (value == null) return null;
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Base64 解码失败");
        }
    }

    public String apiMd5(String value) {
        return digest("MD5", value);
    }

    public String apiSha1(String value) {
        return digest("SHA-1", value);
    }

    public String apiSha256(String value) {
        return digest("SHA-256", value);
    }

    public String apiSm3(String value) {
        return value == null ? null : toHex(sm3(value.getBytes(StandardCharsets.UTF_8)));
    }

    @SuppressWarnings("unchecked")
    public Object apiPut(Object target, String key, Object value) {
        if (!(target instanceof Map)) {
            throw new IllegalArgumentException("apiPut 目标必须是 Map");
        }
        ((Map<String, Object>) target).put(key, value);
        return target;
    }

    @SuppressWarnings("unchecked")
    public Object apiRemove(Object target, String key) {
        if (!(target instanceof Map)) {
            throw new IllegalArgumentException("apiRemove 目标必须是 Map");
        }
        ((Map<String, Object>) target).remove(key);
        return target;
    }

    public String apiHmacSha1Base64(String value, String key) {
        return hmacBase64("HmacSHA1", value, key);
    }

    public String apiHmacSha256Base64(String value, String key) {
        return hmacBase64("HmacSHA256", value, key);
    }

    public String apiTripleDesEncryptBase64(String value, String base64Key) {
        if (value == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(decodeTripleDesKey(base64Key), "DESede"));
            return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("3DES 加密失败，请检查密钥格式");
        }
    }

    public String apiTripleDesDecryptBase64(String value, String base64Key) {
        if (value == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decodeTripleDesKey(base64Key), "DESede"));
            return new String(cipher.doFinal(Base64.getDecoder().decode(value)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("3DES 解密失败，请检查密钥或密文格式");
        }
    }

    public String apiRsaEncryptBase64(String value, String publicKeyText) {
        if (value == null) return null;
        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decodePem(publicKeyText)));
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            int blockSize = (((RSAKey) publicKey).getModulus().bitLength() + 7) / 8 - 11;
            return Base64.getEncoder().encodeToString(processBlocks(
                    cipher, value.getBytes(StandardCharsets.UTF_8), blockSize));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("RSA 加密失败，请检查公钥格式");
        }
    }

    public String apiRsaSignBase64(String value, String privateKeyText, String algorithm) {
        if (value == null) return null;
        String normalized = normalizeSignatureAlgorithm(algorithm);
        try {
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decodePem(privateKeyText)));
            Signature signature = Signature.getInstance(normalized);
            signature.initSign(privateKey);
            signature.update(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("RSA 签名失败，请检查私钥格式和签名算法");
        }
    }

    private String digest(String algorithm, String value) {
        if (value == null) return null;
        try {
            return toHex(MessageDigest.getInstance(algorithm).digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("当前运行环境不支持摘要算法: " + algorithm);
        }
    }

    private String hmacBase64(String algorithm, String value, String key) {
        if (value == null || key == null || key.isEmpty()) {
            throw new IllegalArgumentException("HMAC 原文和密钥不能为空");
        }
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("当前运行环境不支持 " + algorithm);
        }
    }

    private byte[] decodeTripleDesKey(String base64Key) {
        byte[] key = Base64.getDecoder().decode(base64Key == null ? "" : base64Key);
        if (key.length != 24) {
            throw new IllegalArgumentException("invalid 3DES key length");
        }
        return key;
    }

    private byte[] decodePem(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("empty PEM key");
        }
        String normalized = text.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private String normalizeSignatureAlgorithm(String algorithm) {
        String normalized = algorithm == null ? "SHA256withRSA" : algorithm.trim();
        if (!"SHA256withRSA".equalsIgnoreCase(normalized)
                && !"SHA1withRSA".equalsIgnoreCase(normalized)
                && !"MD5withRSA".equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException("不支持的 RSA 签名算法");
        }
        return normalized;
    }

    private byte[] processBlocks(Cipher cipher, byte[] input, int blockSize) throws GeneralSecurityException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (int offset = 0; offset < input.length; offset += blockSize) {
                int length = Math.min(blockSize, input.length - offset);
                output.write(cipher.doFinal(input, offset, length));
            }
            return output.toByteArray();
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException("RSA block processing failed", e);
        }
    }

    private byte[] sm3(byte[] input) {
        int paddedLength = ((input.length + 9 + 63) / 64) * 64;
        byte[] padded = new byte[paddedLength];
        System.arraycopy(input, 0, padded, 0, input.length);
        padded[input.length] = (byte) 0x80;
        long bitLength = (long) input.length * 8;
        for (int i = 0; i < 8; i++) {
            padded[paddedLength - 1 - i] = (byte) (bitLength >>> (i * 8));
        }
        int[] state = {
                0x7380166f, 0x4914b2b9, 0x172442d7, 0xda8a0600,
                0xa96f30bc, 0x163138aa, 0xe38dee4d, 0xb0fb0e4e
        };
        int[] w = new int[68];
        int[] w1 = new int[64];
        for (int offset = 0; offset < padded.length; offset += 64) {
            for (int i = 0; i < 16; i++) {
                int index = offset + i * 4;
                w[i] = (padded[index] & 0xff) << 24
                        | (padded[index + 1] & 0xff) << 16
                        | (padded[index + 2] & 0xff) << 8
                        | (padded[index + 3] & 0xff);
            }
            for (int i = 16; i < 68; i++) {
                w[i] = p1(w[i - 16] ^ w[i - 9] ^ Integer.rotateLeft(w[i - 3], 15))
                        ^ Integer.rotateLeft(w[i - 13], 7) ^ w[i - 6];
            }
            for (int i = 0; i < 64; i++) w1[i] = w[i] ^ w[i + 4];
            int a = state[0], b = state[1], c = state[2], d = state[3];
            int e = state[4], f = state[5], g = state[6], h = state[7];
            for (int i = 0; i < 64; i++) {
                int t = i < 16 ? 0x79cc4519 : 0x7a879d8a;
                int ss1 = Integer.rotateLeft(Integer.rotateLeft(a, 12) + e + Integer.rotateLeft(t, i), 7);
                int ss2 = ss1 ^ Integer.rotateLeft(a, 12);
                int tt1 = ff(a, b, c, i) + d + ss2 + w1[i];
                int tt2 = gg(e, f, g, i) + h + ss1 + w[i];
                d = c;
                c = Integer.rotateLeft(b, 9);
                b = a;
                a = tt1;
                h = g;
                g = Integer.rotateLeft(f, 19);
                f = e;
                e = p0(tt2);
            }
            state[0] ^= a; state[1] ^= b; state[2] ^= c; state[3] ^= d;
            state[4] ^= e; state[5] ^= f; state[6] ^= g; state[7] ^= h;
        }
        byte[] result = new byte[32];
        for (int i = 0; i < state.length; i++) {
            result[i * 4] = (byte) (state[i] >>> 24);
            result[i * 4 + 1] = (byte) (state[i] >>> 16);
            result[i * 4 + 2] = (byte) (state[i] >>> 8);
            result[i * 4 + 3] = (byte) state[i];
        }
        return result;
    }

    private int ff(int x, int y, int z, int round) {
        return round < 16 ? x ^ y ^ z : (x & y) | (x & z) | (y & z);
    }

    private int gg(int x, int y, int z, int round) {
        return round < 16 ? x ^ y ^ z : (x & y) | (~x & z);
    }

    private int p0(int value) {
        return value ^ Integer.rotateLeft(value, 9) ^ Integer.rotateLeft(value, 17);
    }

    private int p1(int value) {
        return value ^ Integer.rotateLeft(value, 15) ^ Integer.rotateLeft(value, 23);
    }

    private String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            chars[i * 2] = HEX[value >>> 4];
            chars[i * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(chars);
    }
}
