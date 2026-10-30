package com.hengshucredit.rule.server.auth;

import org.junit.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

/** 显式门禁：仅连接临时验证容器，在独立随机数据库中测试真实 MySQL JSON 与迁移事务。 */
public class CredentialStorageMigrationIntegrationTest {
    private JdbcTemplate admin;
    private String database;

    @After public void cleanup() {
        if (admin != null && database != null) {
            assertTrue(database.matches("credential_it_[0-9a-f]{32}"));
            admin.execute("DROP DATABASE `" + database + "`");
        }
    }

    @Test public void migrationEncryptsLegacyRowsIsRepeatableAndFailsClosedForWrongKey() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("credentialStorage.integration"));
        assertEquals("127.0.0.1", System.getenv("MYSQL_HOST"));
        assertEquals("13316", System.getenv("MYSQL_PORT"));
        String url = "jdbc:mysql://127.0.0.1:13316/";
        String password = System.getenv("MYSQL_ROOT_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, "root", password));
        database = "credential_it_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE `" + database + "`");
        var datasource = new DriverManagerDataSource(url + database, "root", password);
        var jdbc = new JdbcTemplate(datasource);
        String schema = new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8);
        for (String table : List.of("rule_db_datasource", "rule_sftp_datasource", "rule_external_datasource", "rule_external_api_config")) {
            int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `" + table + "`");
            jdbc.execute(schema.substring(start, schema.indexOf(';', start) + 1));
        }
        // Simulate former schema capacity and data, including null, empty, multibyte and maximum-length secrets.
        jdbc.execute("ALTER TABLE rule_db_datasource MODIFY password VARCHAR(512)");
        String secret = "旧密码".repeat(160);
        for (int i = 0; i < 205; i++) jdbc.update("INSERT INTO rule_db_datasource (datasource_code,datasource_name,jdbc_url,password) VALUES (?,?,?,?)",
                "db" + i, "DB", "jdbc:mysql://localhost/test", i == 0 ? "" : secret);
        String auth = "{\"token\":\"old-token\",\"scriptVariables\":[{\"value\":\"old-secret\"}]}";
        jdbc.update("INSERT INTO rule_external_datasource (datasource_code,datasource_name,base_url,auth_config) VALUES ('ext','Ext','https://example.test',?)", auth);
        jdbc.update("INSERT INTO rule_sftp_datasource (datasource_code,datasource_name,host,username,password,private_key) VALUES ('sftp','SFTP','localhost','test','old-sftp','old-key')");
        jdbc.update("INSERT INTO rule_external_api_config (datasource_id,api_code,api_name,endpoint_url,auth_api_config,header_config,async_callback_config) VALUES (1,'api','API','/',?,?,?)", auth, auth, auth);
        String storedAuthBefore = jdbc.queryForObject("SELECT auth_config FROM rule_external_datasource WHERE id=1", String.class);
        var keys = new ProjectAuthProperties(); keys.setActiveKeyId("test"); keys.setMasterKeys(Map.of("test", UUID.randomUUID().toString()));
        var cipher = new CredentialCipher(keys); var codec = new StoredCredentialCodec(cipher);
        var migration = new CredentialStorageMigration(datasource, cipher, codec); migration.migrate();
        assertEquals(Long.valueOf(205), jdbc.queryForObject("SELECT COUNT(*) FROM rule_db_datasource WHERE password LIKE 'v1:test:%'", Long.class));
        assertEquals(secret, cipher.decrypt(jdbc.queryForObject("SELECT password FROM rule_db_datasource WHERE id=205", String.class)));
        assertEquals("", cipher.decrypt(jdbc.queryForObject("SELECT password FROM rule_db_datasource WHERE id=1", String.class)));
        assertNull(jdbc.queryForObject("SELECT ssh_password FROM rule_db_datasource WHERE id=1", String.class));
        String saved = jdbc.queryForObject("SELECT auth_config FROM rule_external_datasource WHERE id=1", String.class);
        assertTrue(codec.isEncryptedJson(saved)); assertEquals(storedAuthBefore, codec.decryptJson(saved));
        assertEquals("old-sftp", cipher.decrypt(jdbc.queryForObject("SELECT password FROM rule_sftp_datasource WHERE id=1", String.class)));
        for (String column : List.of("auth_api_config", "header_config", "async_callback_config"))
            assertEquals(storedAuthBefore, codec.decryptJson(jdbc.queryForObject("SELECT " + column + " FROM rule_external_api_config WHERE id=1", String.class)));
        migration.migrate(); assertEquals(saved, jdbc.queryForObject("SELECT auth_config FROM rule_external_datasource WHERE id=1", String.class));
        keys.setMasterKeys(Map.of("test", "wrong-master-key"));
        assertThrows(IllegalArgumentException.class, migration::migrate);
        assertEquals(saved, jdbc.queryForObject("SELECT auth_config FROM rule_external_datasource WHERE id=1", String.class));
    }
}
