package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** 用本部署密钥为不同用途派生 HMAC 密钥，不向制品或缓存键输出可离线猜测的凭据摘要。 */
@Component
public final class LocalModelBindingSignature {
    private final ProjectAuthProperties keys;

    public LocalModelBindingSignature(ProjectAuthProperties keys) {
        this.keys = keys;
    }

    String activeKeyId() {
        return keys.getActiveKeyId();
    }

    public String sign(String domain, String value) {
        return sign(activeKeyId(), domain, value);
    }

    String sign(String keyId, String domain, String value) {
        String material = keyId == null ? null : keys.getMasterKeys().get(keyId);
        if (material == null || material.isBlank()) throw new IllegalArgumentException("本机绑定签名密钥不可用");
        if (domain == null || domain.isBlank()) throw new IllegalArgumentException("缺少本机签名用途");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(material.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] domainKey = mac.doFinal(("tianshu.local-binding.v1:" + domain).getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(domainKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("无法生成本机绑定签名", ex);
        }
    }
}
