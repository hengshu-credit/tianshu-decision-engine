package com.hengshucredit.rule.server.sftp;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hengshucredit.rule.model.dto.ProjectAuthSaveRequest;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.mapper.RuleAuthInstitutionMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectAuthMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import com.hengshucredit.rule.server.service.ProjectAuthService;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.Assert.*;

public class EngineSftpCredentialAccessTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();

    @Test public void managementCreatesEnablesRotatesAndDisablesCredentialsUsedByTheSshListener() throws Exception {
        try (var fixture = new EngineSftpFixture(directory.getRoot().toPath()); var client = SshClient.setUpDefaultClient()) {
            var management = management(fixture);
            client.start();
            for (String type : java.util.List.of("BASIC", "API_KEY", "HMAC_SHA256")) {
                var form = new ProjectAuthSaveRequest();
                form.setAuthName("机构凭据" + type);
                form.setInstitutionName("机构" + type);
                form.setAuthType(type);
                form.setManagementProjectId(12L);
                form.setIdentifier("partner-" + type);
                form.setSecret("secret-" + type);
                form.setSftpEnabled(false);
                var created = management.createInstitutionAuth(form);
                assertNull(created.getProjectId());
                var enable = new ProjectAuthSaveRequest();
                enable.setSftpEnabled(true);
                var enabled = management.updateAuth(null, created.getId(), enable);
                String login = management.getFullAuth(null, created.getId()).getSftpLogin();
                String storedLogin = fixture.db.queryForObject("SELECT sftp_login FROM rule_project_auth WHERE id=?",
                        String.class, created.getId());
                assertEquals(EngineSftpCredentials.loginHash(login), fixture.db.queryForObject(
                        "SELECT sftp_login_hash FROM rule_project_auth WHERE id=?", String.class, created.getId()));
                if ("API_KEY".equals(type)) {
                    assertTrue(login.startsWith("auth-"));
                    assertEquals(login, enabled.getSftpLogin());
                    assertEquals(login, storedLogin);
                } else {
                    assertNull(enabled.getSftpLogin());
                    assertNull(storedLogin);
                    assertEquals(form.getIdentifier(), login);
                }
                try (var session = connect(client, fixture, login, form.getSecret(), null);
                     var sftp = SftpClientFactory.instance().createSftpClient(session)) {
                    try (var output = sftp.write("input.txt")) { output.write(42); }
                    var rotate = new ProjectAuthSaveRequest();
                    rotate.setSecret("rotated-" + type);
                    management.updateAuth(null, created.getId(), rotate);
                    assertEquals(login, management.getFullAuth(null, created.getId()).getSftpLogin());
                    assertThrows(IOException.class, () -> sftp.stat("input.txt"));
                    assertThrows(IOException.class, () -> connect(client, fixture, login, form.getSecret(), null));
                    try (var rotated = connect(client, fixture, login, rotate.getSecret(), null);
                         var reader = SftpClientFactory.instance().createSftpClient(rotated)) {
                        try (var in = reader.read("input.txt")) { assertEquals(42, in.read()); }
                        management.setAuthStatus(null, created.getId(), 0);
                        assertThrows(IOException.class, () -> reader.stat("input.txt"));
                    }
                }
            }
        }
    }

    private static ProjectAuthService management(EngineSftpFixture fixture) throws Exception {
        var properties = new ProjectAuthProperties();
        properties.setActiveKeyId("test");
        properties.setMasterKeys(java.util.Map.of("test", java.util.UUID.randomUUID().toString()));
        var service = new ProjectAuthService(new CredentialCipher(properties));
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(fixture.db.getDataSource());
        factory.setConfiguration(config);
        factory.setGlobalConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig().setBanner(false)
                .setMetaObjectHandler(new com.hengshucredit.rule.server.config.MybatisPlusConfig().metaObjectHandler()));
        var sessions = new SqlSessionTemplate(factory.getObject());
        config.addMapper(RuleProjectAuthMapper.class);
        config.addMapper(RuleAuthInstitutionMapper.class);
        config.addMapper(RuleProjectMapper.class);
        ReflectionTestUtils.setField(service, "authMapper", sessions.getMapper(RuleProjectAuthMapper.class));
        ReflectionTestUtils.setField(service, "institutions", sessions.getMapper(RuleAuthInstitutionMapper.class));
        ReflectionTestUtils.setField(service, "projectMapper", sessions.getMapper(RuleProjectMapper.class));
        return service;
    }

    @Test public void directCredentialsAuthenticateToIndependentInboxesAndRevocationCutsOffOpenSessions() throws Exception {
        try (var fixture = new EngineSftpFixture(directory.getRoot().toPath()); var client = SshClient.setUpDefaultClient()) {
            String apiKey = "x".repeat(90) + "actual-end";
            fixture.db.update("INSERT INTO rule_auth_institution(id,institution_name) VALUES(7,'甲'),(8,'乙')");
            insert(fixture, 21, 7L, null, "BASIC", "sender-basic", "secret-one");
            insert(fixture, 22, 8L, null, "API_KEY", "auth-two", apiKey);
            insert(fixture, 23, 7L, null, "HMAC_SHA256", "hmac-client-id", "http-signing-secret");
            insert(fixture, 24, null, 12L, "BASIC", "project-basic", "secret-four");
            client.start();
            try (var first = connect(client, fixture, "sender-basic", "secret-one", null);
                 var second = connect(client, fixture, "auth-two", apiKey, null);
                 var third = connect(client, fixture, "hmac-client-id", "http-signing-secret", null);
                 var fourth = connect(client, fixture, "project-basic", "secret-four", null);
                 var a = SftpClientFactory.instance().createSftpClient(first);
                 var b = SftpClientFactory.instance().createSftpClient(second);
                 var c = SftpClientFactory.instance().createSftpClient(third);
                 var d = SftpClientFactory.instance().createSftpClient(fourth)) {
                try (var output = a.write("shared.bin")) { output.write(21); }
                assertThrows(IOException.class, () -> b.stat("shared.bin"));
                assertThrows(IOException.class, () -> c.stat("shared.bin"));
                assertThrows(IOException.class, () -> d.stat("shared.bin"));
                try (var output = b.write("shared.bin")) { output.write(22); }
                try (var output = c.write("shared.bin")) { output.write(23); }
                try (var output = d.write("shared.bin")) { output.write(24); }
                assertArrayEquals(new byte[]{21}, fixture.nodeB.read("/organizations/i7/a21/shared.bin", 100));
                assertArrayEquals(new byte[]{22}, fixture.nodeB.read("/organizations/i8/a22/shared.bin", 100));
                assertArrayEquals(new byte[]{23}, fixture.nodeB.read("/organizations/i7/a23/shared.bin", 100));
                assertArrayEquals(new byte[]{24}, fixture.nodeB.read("/projects/12/a24/shared.bin", 100));

                var files = new EngineSftpFiles(fixture.nodeB, null, fixture.accounts);
                var auth = com.hengshucredit.rule.server.auth.ProjectAuthContext.institution(7L, 21L, "auth21", "BASIC", null, true);
                assertArrayEquals(new byte[]{21}, files.readCredential(auth, "shared.bin"));
                assertThrows(IllegalArgumentException.class, () -> files.credentialPath(auth, "../a23/shared.bin"));
                var wrongOwner = com.hengshucredit.rule.server.auth.ProjectAuthContext.institution(7L, 22L, "auth22", "API_KEY", null, true);
                assertThrows(com.hengshucredit.rule.server.security.ConsoleAccessDeniedException.class,
                        () -> files.readCredential(wrongOwner, "shared.bin"));

                assertThrows(IOException.class, () -> connect(client, fixture, "auth-two", "x".repeat(90) + "wrong-end", null));
                assertThrows(IOException.class, () -> connect(client, fixture, "hmac-client-id", apiKey, null));
                assertThrows(IOException.class, () -> connect(client, fixture, "system", "http-signing-secret", null));
                var wrongKey = KeyPairGenerator.getInstance("EC").generateKeyPair();
                assertThrows(IOException.class, () -> connect(client, fixture, "hmac-client-id", null, wrongKey));
                fixture.db.update("UPDATE rule_project_auth SET sftp_revision=sftp_revision+1 WHERE id=23");
                assertThrows(IOException.class, () -> c.stat("shared.bin"));
                fixture.db.update("UPDATE rule_auth_institution SET status=0 WHERE id=7");
                assertThrows(com.hengshucredit.rule.server.security.ConsoleAccessDeniedException.class,
                        () -> files.credentialPath(auth, "shared.bin"));
                assertThrows(IOException.class, () -> a.stat("shared.bin"));
                assertThrows(IOException.class, () -> connect(client, fixture, "sender-basic", "secret-one", null));
                assertEquals(1, b.stat("shared.bin").getSize());
                fixture.db.update("UPDATE rule_project SET status=0 WHERE id=12");
                assertThrows(IOException.class, () -> d.stat("shared.bin"));
            }
        }
    }

    @Test public void credentialsRespectSourceWhitelistExactLoginAndReservedNamespaces() throws Exception {
        try (var fixture = new EngineSftpFixture(directory.getRoot().toPath())) {
            insert(fixture, 25, null, 12L, "BASIC", "Case-Sensitive", "secret-five");
            fixture.db.update("UPDATE rule_project_auth SET access_policy_json=? WHERE id=25",
                    "{\"ipWhitelist\":[\"192.0.2.0/24\"]}");
            assertNull(fixture.accounts.subject("case-sensitive"));
            assertNull(fixture.accounts.authenticateCredential("case-sensitive", "AUTH:25", "192.0.2.5", "secret-five"));
            assertNull(fixture.accounts.authenticateCredential("Case-Sensitive", "AUTH:25", "198.51.100.1", "secret-five"));
            assertNotNull(fixture.accounts.authenticateCredential("Case-Sensitive", "AUTH:25", "192.0.2.5", "secret-five"));
            for (String reserved : new String[]{"u7", "u7-p12", "engine-storage", "system"}) {
                assertThrows(IllegalArgumentException.class, () -> EngineSftpCredentials.validateLogin(reserved, "system"));
            }
        }
    }

    private static void insert(EngineSftpFixture fixture, long id, Long institution, Long project,
                               String type, String login, String password) {
        fixture.db.update("INSERT INTO rule_project_auth(id,institution_id,project_id,auth_code,auth_name,auth_type,"
                        + "lookup_key,secret_ciphertext,sftp_enabled,sftp_login,sftp_login_hash,sftp_password_hash) "
                        + "VALUES(?,?,?,?,?,?,?,'unused',1,?,?,?)", id, institution, project, "auth" + id,
                "接入方" + id, type, "lookup-" + id, "API_KEY".equals(type) ? login : null,
                EngineSftpCredentials.loginHash(login), EngineSftpCredentials.hashPassword(password));
    }

    private static ClientSession connect(SshClient client, EngineSftpFixture fixture, String login,
                                         String password, KeyPair key) throws IOException {
        var session = client.connect(login, "127.0.0.1", fixture.server.port()).verify(5000).getSession();
        try {
            if (key != null) session.addPublicKeyIdentity(key);
            if (password != null) session.addPasswordIdentity(password);
            session.auth().verify(5000);
            return session;
        } catch (IOException failed) {
            session.close();
            throw failed;
        }
    }
}
