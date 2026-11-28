package com.hengshucredit.rule.server.sftp;

import com.hengshucredit.rule.server.sql.TestSchemaTables;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class EngineSftpFixture implements AutoCloseable {
    final JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
    final EngineSftpAccounts accounts = new EngineSftpAccounts(db);
    final EngineSftpProtection protection = new EngineSftpProtection(db,new org.springframework.jdbc.datasource.DataSourceTransactionManager(db.getDataSource()));
    final EngineSftpServer server;
    final String password = UUID.randomUUID().toString();
    final String pin;
    public final DefaultSftpClient nodeA, nodeB;

    public EngineSftpFiles files(com.hengshucredit.rule.server.security.ConsoleProjectAccess access) {
        return new EngineSftpFiles(nodeB, access, accounts);
    }

    public JdbcTemplate database() { return db; }

    public EngineSftpFixture(Path root) throws Exception {
        TestSchemaTables.create(db, List.of("engine_sftp_account","engine_sftp_policy","engine_sftp_auth_state","rule_project_auth"));
        db.update("INSERT INTO console_user(id,username,display_name,password_hash,project_access_mode) VALUES(7,'sender','上传方','unused','SELECTED')");
        db.update("INSERT INTO rule_project(id,project_code,project_name) VALUES(12,'p12','十二'),(13,'p13','十三')");
        db.execute("CREATE TABLE console_user_project(user_id BIGINT,project_id BIGINT)");
        db.update("INSERT INTO console_user_project VALUES(7,12)");
        server = new EngineSftpServer(root, accounts, protection, "127.0.0.1", 0, "system", password);
        server.start();
        pin = KeyUtils.getFingerPrint(BuiltinDigests.sha256, new FileKeyPairProvider(root.resolve("host-key")).loadKeys(null).iterator().next().getPublic());
        nodeA = connect(); nodeB = connect();
    }

    private DefaultSftpClient connect() throws Exception {
        var client = new DefaultSftpClient("127.0.0.1", server.port(), "system", password, pin);
        client.start();
        return client;
    }

    @Override public void close() throws java.io.IOException { nodeA.close(); nodeB.close(); server.stop(); }
}
