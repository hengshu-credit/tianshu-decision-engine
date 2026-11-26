package com.hengshucredit.rule.server.sftp;

import com.hengshucredit.rule.server.security.ConsolePermissionService;
import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import com.hengshucredit.rule.server.sql.TestSchemaTables;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.Assert.*;

public class EngineSftpServerTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();

    @Test public void uploadAndReadUseAuthorizedProjectAndRevocationStopsActiveSession() throws Exception {
        var db = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        TestSchemaTables.create(db, List.of("engine_sftp_account"));
        db.update("INSERT INTO console_user(id,username,display_name,password_hash) VALUES(7,'uploader','上传方','unused')");
        db.execute("CREATE TABLE rule_project(id BIGINT PRIMARY KEY)");
        db.update("INSERT INTO rule_project VALUES(12),(13)");
        var scope = new ConsoleProjectAccess.Scope(7L, Set.of(12L), Set.of());
        var accounts = new EngineSftpAccounts(db, new ConsolePermissionService() {
            @Override public ConsoleProjectAccess.Scope scope(Long user) { return scope; }
        });
        var storage = new EngineSftpFiles(files.getRoot().toString(), new ConsoleProjectAccess() {
            @Override public Scope current() { return scope; }
        });
        assertThrows(IllegalArgumentException.class, () -> storage.input(null, null));
        var key = KeyPairGenerator.getInstance("EC").generateKeyPair();
        assertNull(accounts.authenticate("u7", key.getPublic()));
        accounts.configure(7, PublicKeyEntry.toString(key.getPublic()), false);
        accounts.configure(7, "", false);
        assertNull(accounts.authenticate("u7-p13", key.getPublic()));
        assertNotNull(accounts.authenticate("u7", key.getPublic()));
        var server = new EngineSftpServer(storage, accounts, "127.0.0.1", 0);
        server.start();
        try (var client = SshClient.setUpDefaultClient()) {
            client.start();
            client.addPublicKeyIdentity(key);
            try (var session = client.connect("u7-p12", "127.0.0.1", server.port()).verify(5000).getSession()) {
                session.auth().verify(5000);
                try (var sftp = SftpClientFactory.instance().createSftpClient(session)) {
                    byte[] body = "{\"score\":73}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    try (var stream = sftp.write("request.part")) { stream.write(body); }
                    sftp.rename("request.part", "request.json");
                    assertArrayEquals(body, storage.readProject(12L, "u7/request.json"));
                    assertThrows(IllegalArgumentException.class, () -> storage.readProject(13L, "u7/request.json"));
                    assertThrows(IllegalArgumentException.class, () -> storage.input(null, "request.json"));
                    accounts.configure(7, null, true);
                    assertThrows(IOException.class, () -> sftp.stat("request.json"));
                }
            }
        } finally { server.stop(); }
    }
}
