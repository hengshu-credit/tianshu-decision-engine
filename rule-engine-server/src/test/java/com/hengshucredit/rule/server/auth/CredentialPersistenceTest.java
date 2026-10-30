package com.hengshucredit.rule.server.auth;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.*;
import org.junit.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

public class CredentialPersistenceTest {
    private JdbcTemplate jdbc;
    private SqlSessionTemplate sessions;
    private CredentialCipher cipher;
    private StoredCredentialCodec codec;

    @Before public void setup() throws Exception {
        var datasource = new DriverManagerDataSource("jdbc:h2:mem:credentials" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(datasource);
        String schema = new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8);
        for (String table : List.of("rule_db_datasource", "rule_sftp_datasource", "rule_external_datasource", "rule_external_api_config")) {
            int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `" + table + "`");
            String ddl = schema.substring(start, schema.indexOf(';', start) + 1).replaceFirst("\\) ENGINE=.*;", ")")
                    .replaceAll("(?i)\\bJSON\\b", "LONGTEXT")
                    .replaceAll("(?i)(\\b(?:KEY|INDEX)\\s+`)([^`]+)(`)", "$1" + table + "_$2$3");
            jdbc.execute(ddl);
        }
        var properties = new ProjectAuthProperties(); properties.setActiveKeyId("test");
        properties.setMasterKeys(Map.of("test", UUID.randomUUID().toString()));
        cipher = new CredentialCipher(properties); codec = new StoredCredentialCodec(cipher);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true); codec.register(config);
        assertEquals(org.apache.ibatis.type.StringTypeHandler.class, config.getTypeHandlerRegistry().getTypeHandler(String.class).getClass());
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(datasource); factory.setConfiguration(config);
        factory.setGlobalConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig().setBanner(false)
                .setMetaObjectHandler(new com.hengshucredit.rule.server.config.MybatisPlusConfig().metaObjectHandler()));
        var sf = factory.getObject();
        config.addMapper(RuleDbDatasourceMapper.class); config.addMapper(RuleSftpDatasourceMapper.class);
        config.addMapper(RuleExternalDatasourceMapper.class); config.addMapper(RuleExternalApiConfigMapper.class);
        sessions = new SqlSessionTemplate(sf);
    }

    @After public void cleanup() { if (jdbc != null) jdbc.execute("SHUTDOWN"); }

    @Test public void databaseAndSftpSecretsRoundTripWithoutMutatingInputOrPlaintextStorage() {
        var db = new RuleDbDatasource(); db.setDatasourceCode("db"); db.setDatasourceName("DB");
        db.setJdbcUrl("jdbc:mysql://localhost/example"); db.setPassword("secret-db"); db.setSshPassword("secret-ssh");
        db.setSshPrivateKey("private-key".repeat(1000)); db.setSshPassphrase("key-passphrase");
        var mapper = sessions.getMapper(RuleDbDatasourceMapper.class); mapper.insert(db);
        assertEquals("secret-db", db.getPassword());
        assertEquals(db.getPassword(), mapper.selectById(db.getId()).getPassword());
        for (String column : List.of("password", "ssh_password", "ssh_private_key", "ssh_passphrase")) {
            String raw = jdbc.queryForObject("SELECT " + column + " FROM rule_db_datasource WHERE id=?", String.class, db.getId());
            assertTrue(raw.startsWith("v1:test:")); assertFalse(raw.contains("secret")); assertFalse(raw.contains("private-key"));
        }
        RuleDbDatasource partial = new RuleDbDatasource(); partial.setId(db.getId()); partial.setDatasourceName("Renamed"); mapper.updateById(partial);
        assertEquals("secret-db", mapper.selectById(db.getId()).getPassword());
        partial.setPassword(""); mapper.updateById(partial);
        assertEquals("", mapper.selectById(db.getId()).getPassword());
        assertNotEquals("", jdbc.queryForObject("SELECT password FROM rule_db_datasource WHERE id=?", String.class, db.getId()));
        var sftp = new RuleSftpDatasource(); sftp.setDatasourceCode("sftp"); sftp.setDatasourceName("SFTP"); sftp.setHost("localhost"); sftp.setUsername("test");
        sftp.setPassword("sftp-secret"); sftp.setPrivateKey("sftp-key"); sftp.setPassphrase("sftp-pass"); sftp.setKnownHosts("host-key");
        var sftpMapper = sessions.getMapper(RuleSftpDatasourceMapper.class); sftpMapper.insert(sftp);
        assertEquals("sftp-key", sftpMapper.selectById(sftp.getId()).getPrivateKey());
        assertEquals("sftp-secret", cipher.decrypt(jdbc.queryForObject("SELECT password FROM rule_sftp_datasource WHERE id=?", String.class, sftp.getId())));
    }

    @Test public void jsonCredentialColumnsContainOnlyAuthenticatedCiphertext() {
        String secret = "{\"token\":\"private-token\",\"password\":\"private-password\"}";
        var ds = new RuleExternalDatasource(); ds.setDatasourceCode("external"); ds.setDatasourceName("External"); ds.setAuthConfig(secret); ds.setBaseUrl("https://example.test");
        var mapper = sessions.getMapper(RuleExternalDatasourceMapper.class); mapper.insert(ds);
        assertEquals(secret, mapper.selectById(ds.getId()).getAuthConfig());
        String raw = jdbc.queryForObject("SELECT auth_config FROM rule_external_datasource WHERE id=?", String.class, ds.getId());
        assertTrue(codec.isEncryptedJson(raw)); assertFalse(raw.contains("private-token")); assertEquals(secret, codec.decryptJson(raw));
        var api = new RuleExternalApiConfig(); api.setDatasourceId(ds.getId()); api.setApiCode("test"); api.setApiName("Test"); api.setEndpointUrl("https://example.test");
        api.setAuthApiConfig(secret); api.setHeaderConfig(secret); api.setAsyncCallbackConfig(secret);
        var apis = sessions.getMapper(RuleExternalApiConfigMapper.class); apis.insert(api);
        assertEquals(secret, apis.selectById(api.getId()).getHeaderConfig());
        for (String column : List.of("auth_api_config", "header_config", "async_callback_config")) {
            raw = jdbc.queryForObject("SELECT " + column + " FROM rule_external_api_config WHERE id=?", String.class, api.getId());
            assertTrue(codec.isEncryptedJson(raw)); assertFalse(raw.contains("private-password"));
        }
        jdbc.update("UPDATE rule_external_datasource SET auth_config=? WHERE id=?", secret, ds.getId());
        assertThrows(RuntimeException.class, () -> mapper.selectById(ds.getId()));
    }

    @Test public void wrongKeyAndTamperingAreRejectedAndEncryptionIsRandomized() {
        String first = codec.encryptJson("{\"token\":\"secret\"}");
        assertNotEquals(first, codec.encryptJson("{\"token\":\"secret\"}"));
        var other = new ProjectAuthProperties(); other.setActiveKeyId("test"); other.setMasterKeys(Map.of("test", "different-key"));
        assertThrows(IllegalArgumentException.class, () -> new StoredCredentialCodec(new CredentialCipher(other)).decryptJson(first));
        var envelope = com.alibaba.fastjson.JSON.parseObject(first);
        String text = envelope.getString("_credentialCiphertext");
        envelope.put("_credentialCiphertext", text.substring(0, text.length() - 5) + "AAAAA");
        assertThrows(IllegalArgumentException.class, () -> codec.decryptJson(envelope.toJSONString()));
    }
}
