package com.hengshucredit.rule.server.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;

/** 在 Mapper/业务缓存初始化前转换历史明文；失败停止启动，不降级为明文读取。 */
@Component
@DependsOn("initialDataInitializer")
public class CredentialStorageMigration {
    private record Columns(String table, boolean json, List<String> names) { }
    private static final List<Columns> TABLES = List.of(
            new Columns("rule_db_datasource", false, List.of("password", "ssh_password", "ssh_private_key", "ssh_passphrase")),
            new Columns("rule_sftp_datasource", false, List.of("password", "private_key", "passphrase", "known_hosts")),
            new Columns("rule_external_datasource", true, List.of("auth_config")),
            new Columns("rule_external_api_config", true, List.of("auth_api_config", "header_config", "async_callback_config")));
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final CredentialCipher cipher;
    private final StoredCredentialCodec codec;

    public CredentialStorageMigration(DataSource dataSource, CredentialCipher cipher, StoredCredentialCodec codec) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.cipher = cipher;
        this.codec = codec;
    }

    @PostConstruct
    public void migrate() {
        for (Columns columns : TABLES) {
            // Base64/GCM expansion must not truncate former maximum-length passwords or private keys.
            if (!columns.json()) for (String column : columns.names()) {
                String type = jdbc.queryForObject("SELECT DATA_TYPE FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?", String.class, columns.table(), column);
                if (!"longtext".equalsIgnoreCase(type)) jdbc.execute("ALTER TABLE `" + columns.table()
                        + "` MODIFY COLUMN `" + column + "` LONGTEXT DEFAULT NULL COMMENT 'AES-GCM encrypted credential'");
            }
            long cursor = 0;
            while (true) {
                long after = cursor;
                Long next = transaction.execute(status -> migrateBatch(columns, after));
                if (next == null) break;
                cursor = next;
            }
        }
    }

    private Long migrateBatch(Columns columns, long cursor) {
        String names = columns.names().stream().map(name -> "`" + name + "`").collect(java.util.stream.Collectors.joining(","));
        var rows = jdbc.queryForList("SELECT id," + names + " FROM `" + columns.table() + "` WHERE id>? ORDER BY id LIMIT 200 FOR UPDATE", cursor);
        if (rows.isEmpty()) return null;
        for (var row : rows) for (String column : columns.names()) {
            Object raw = row.get(column);
            if (raw == null) continue;
            String value = raw instanceof byte[] bytes ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) : raw.toString();
            boolean encrypted = columns.json() ? codec.isEncryptedJson(value) : value.startsWith("v1:");
            if (encrypted) {
                // An unknown key or damaged ciphertext must never be re-encrypted as if it were plaintext.
                if (columns.json()) codec.decryptJson(value); else cipher.decrypt(value);
            } else {
                String stored = columns.json() ? codec.encryptJson(value) : cipher.encrypt(value);
                jdbc.update("UPDATE `" + columns.table() + "` SET `" + column + "`=? WHERE id=?", stored, row.get("id"));
            }
        }
        return ((Number) rows.get(rows.size() - 1).get("id")).longValue();
    }
}
