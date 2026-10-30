package com.hengshucredit.rule.model.security;

import java.util.function.UnaryOperator;

/** 保留 JSON 列类型，存储的是带版本标记的密文对象。 */
public class EncryptedJsonCredentialTypeHandler extends EncryptedCredentialTypeHandler {
    public EncryptedJsonCredentialTypeHandler() { super(); }
    public EncryptedJsonCredentialTypeHandler(UnaryOperator<String> encrypt, UnaryOperator<String> decrypt) { super(encrypt, decrypt); }
}
