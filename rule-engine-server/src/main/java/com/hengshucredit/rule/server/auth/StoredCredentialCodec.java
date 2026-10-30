package com.hengshucredit.rule.server.auth;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.security.EncryptedCredentialTypeHandler;
import com.hengshucredit.rule.model.security.EncryptedJsonCredentialTypeHandler;
import org.apache.ibatis.session.Configuration;
import org.springframework.stereotype.Component;

@Component
public class StoredCredentialCodec {
    private static final String JSON_KEY = "_credentialCiphertext";
    private final CredentialCipher cipher;

    public StoredCredentialCodec(CredentialCipher cipher) { this.cipher = cipher; }

    public void register(Configuration configuration) {
        // Register by handler class only: ordinary String columns must never be encrypted.
        configuration.getTypeHandlerRegistry().register((Class<String>) null,
                new EncryptedCredentialTypeHandler(cipher::encrypt, cipher::decrypt));
        configuration.getTypeHandlerRegistry().register((Class<String>) null,
                new EncryptedJsonCredentialTypeHandler(this::encryptJson, this::decryptJson));
    }

    public String encryptJson(String value) {
        if (value == null) return null;
        JSONObject envelope = new JSONObject();
        envelope.put(JSON_KEY, cipher.encrypt(value));
        return envelope.toJSONString();
    }

    public boolean isEncryptedJson(String value) {
        if (value == null || !JSON.isValidObject(value)) return false;
        JSONObject object = JSON.parseObject(value);
        return object.size() == 1 && object.get(JSON_KEY) instanceof String;
    }

    public String decryptJson(String value) {
        if (value == null) return null;
        if (!isEncryptedJson(value)) throw new IllegalArgumentException("敏感配置尚未完成加密迁移");
        return cipher.decrypt(JSON.parseObject(value).getString(JSON_KEY));
    }
}
