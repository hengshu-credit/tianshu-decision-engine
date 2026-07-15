package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExternalApiSecurityService {

    Object prepareRequest(String endpointUrl, Object requestBody, String authApiConfig) {
        Map<String, Object> config = parseMap(authApiConfig);
        String securityProfile = text(config.get("securityProfile"));
        if ("BAIHANG_HMAC_SHA1_3DES".equals(securityProfile)) {
            return prepareBaihang(requestBody, nestedMap(config.get("securityConfig")));
        }
        if (!"TIANCHUANG_MD5_SORTED".equals(securityProfile)) {
            return requestBody;
        }
        Map<String, Object> body = requireMap(requestBody, "天创报文安全方案要求JSON对象请求体");
        if (text(body.get("appId")).isEmpty()) {
            throw new IllegalArgumentException("天创appId不能为空");
        }
        Map<String, Object> securityConfig = nestedMap(config.get("securityConfig"));
        String tokenId = text(securityConfig.get("tokenId"));
        if (tokenId.isEmpty()) {
            throw new IllegalArgumentException("天创tokenId不能为空");
        }

        List<String> fieldNames = new ArrayList<>();
        for (String fieldName : body.keySet()) {
            if (!"appId".equals(fieldName) && !"tokenKey".equals(fieldName)) {
                fieldNames.add(fieldName);
            }
        }
        Collections.sort(fieldNames);
        StringBuilder params = new StringBuilder();
        for (String fieldName : fieldNames) {
            if (params.length() > 0) {
                params.append(',');
            }
            params.append(fieldName).append('=').append(text(body.get(fieldName)));
        }

        Map<String, Object> result = new LinkedHashMap<>(body);
        result.put("tokenKey", md5Hex(endpointUrl + tokenId + params));
        return result;
    }

    Object processResponse(Object responseBody, String authApiConfig) {
        Map<String, Object> config = parseMap(authApiConfig);
        if (!"BAIHANG_HMAC_SHA1_3DES".equals(text(config.get("securityProfile")))
                || !(responseBody instanceof Map)) {
            return responseBody;
        }
        Map<String, Object> result = requireMap(responseBody, "百行响应必须是JSON对象");
        Object encrypted = result.get("response");
        if (encrypted == null || text(encrypted).isEmpty()) {
            return responseBody;
        }
        byte[] key = tripleDesKey(nestedMap(config.get("securityConfig")));
        result.put("response", decryptJson(text(encrypted), key));
        return result;
    }

    private Map<String, Object> prepareBaihang(Object requestBody, Map<String, Object> securityConfig) {
        Map<String, Object> business = requireMap(requestBody, "百行报文安全方案要求JSON对象请求体");
        String requestRefId = text(business.remove("requestRefId"));
        if (requestRefId.isEmpty()) {
            requestRefId = UUID.randomUUID().toString().replace("-", "");
        }
        if (!business.containsKey("applyDate") || text(business.get("applyDate")).isEmpty()) {
            business.put("applyDate", new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
        }
        String secretId = text(securityConfig.get("secretId"));
        if (secretId.isEmpty()) {
            throw new IllegalArgumentException("百行secretId不能为空");
        }
        byte[] key = tripleDesKey(securityConfig);
        String encryptedRequest = encrypt(JSON.toJSONString(business).getBytes(StandardCharsets.UTF_8), key);
        String canonical = "requestRefId=" + requestRefId + "&secretId=" + secretId;

        Map<String, Object> head = new LinkedHashMap<>();
        head.put("requestRefId", requestRefId);
        head.put("secretId", secretId);
        head.put("signature", hmacSha1(canonical, key));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("head", head);
        result.put("request", encryptedRequest);
        return result;
    }

    private byte[] tripleDesKey(Map<String, Object> securityConfig) {
        String secretKey = text(securityConfig.get("secretKey"));
        if (secretKey.isEmpty()) {
            throw new IllegalArgumentException("百行secretKey不能为空");
        }
        try {
            byte[] key = Base64.getDecoder().decode(secretKey);
            if (key.length != 24) {
                throw new IllegalArgumentException("百行secretKey解码后必须为24字节");
            }
            return key;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("百行secretKey必须是Base64编码的24字节密钥");
        }
    }

    private String encrypt(byte[] value, byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "DESede"));
            return Base64.getEncoder().encodeToString(cipher.doFinal(value));
        } catch (Exception e) {
            throw new IllegalStateException("百行请求报文加密失败");
        }
    }

    private Object decryptJson(String value, byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "DESede"));
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(value));
            return JSON.parse(new String(plain, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalArgumentException("百行响应报文解密失败");
        }
    }

    private String hmacSha1(String value, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("百行请求签名失败");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMap(Object value, String message) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(message);
        }
        return new LinkedHashMap<>((Map<String, Object>) value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Object value) {
        if (value instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) value);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object parsed = JSON.parse(json);
        return parsed instanceof Map ? new LinkedHashMap<>((Map<String, Object>) parsed) : new LinkedHashMap<>();
    }

    private String md5Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("生成天创tokenKey失败", e);
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
