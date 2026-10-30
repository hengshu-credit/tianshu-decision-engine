package com.hengshucredit.rule.model.security;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.UnaryOperator;

/** 每个 SqlSessionFactory 注册自己的加解密实例，密钥不进入共享 model 或静态状态。 */
public class EncryptedCredentialTypeHandler extends BaseTypeHandler<String> {
    private final UnaryOperator<String> encrypt;
    private final UnaryOperator<String> decrypt;

    public EncryptedCredentialTypeHandler() {
        this(value -> { throw new IllegalStateException("凭据加密器未配置"); },
                value -> { throw new IllegalStateException("凭据解密器未配置"); });
    }

    public EncryptedCredentialTypeHandler(UnaryOperator<String> encrypt, UnaryOperator<String> decrypt) {
        this.encrypt = encrypt;
        this.decrypt = decrypt;
    }

    @Override public void setNonNullParameter(PreparedStatement statement, int index, String value, JdbcType type) throws SQLException {
        statement.setString(index, encrypt.apply(value));
    }
    @Override public String getNullableResult(ResultSet result, String column) throws SQLException { return decode(result.getString(column)); }
    @Override public String getNullableResult(ResultSet result, int column) throws SQLException { return decode(result.getString(column)); }
    @Override public String getNullableResult(CallableStatement statement, int column) throws SQLException { return decode(statement.getString(column)); }
    private String decode(String value) { return value == null ? null : decrypt.apply(value); }
}
