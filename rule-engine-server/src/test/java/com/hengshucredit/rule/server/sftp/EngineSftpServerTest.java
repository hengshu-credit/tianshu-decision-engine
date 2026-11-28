package com.hengshucredit.rule.server.sftp;

import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.security.KeyPairGenerator;
import java.util.Set;
import static org.junit.Assert.*;

public class EngineSftpServerTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();

    @Test public void uploadAndReadUseAuthorizedProjectAndRevocationStopsActiveSession() throws Exception {
        try (var fixture = new EngineSftpFixture(files.getRoot().toPath())) {
            var accounts = fixture.accounts;
            var server = fixture.server;
            var scope = new ConsoleProjectAccess.Scope(7L, Set.of(12L), Set.of());
            var storage = new EngineSftpFiles(fixture.nodeB, new ConsoleProjectAccess() {
                @Override public Scope current() { return scope; }
            }, accounts);
            assertThrows(IllegalArgumentException.class, () -> storage.input(null, null));
            var key = KeyPairGenerator.getInstance("EC").generateKeyPair();
            assertNull(accounts.authenticate("u7", key.getPublic()));
            accounts.configure(7, PublicKeyEntry.toString(key.getPublic()), false);
            accounts.configure(7, "", false);
            assertNull(accounts.authenticate("u7-p13", key.getPublic()));
            assertNotNull(accounts.authenticate("u7", key.getPublic()));
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
                        assertThrows(com.hengshucredit.rule.server.security.ConsoleAccessDeniedException.class, () -> storage.readProject(13L, "u7/request.json"));
                        assertThrows(IllegalArgumentException.class, () -> storage.input(null, "request.json"));
                        accounts.configure(7, null, true);
                        assertThrows(IOException.class, () -> sftp.stat("request.json"));
                    }
                }
            }
        }
    }

    @Test public void twoAccountsInOneProjectSeeTheirOwnFilesAndReadOnlyCannotChangeThem() throws Exception {
        try (var fixture = new EngineSftpFixture(files.getRoot().toPath()); var client = SshClient.setUpDefaultClient()) {
            fixture.db.update("INSERT INTO console_user(id,username,display_name,password_hash) VALUES(8,'receiver','接收方','unused')");
            var first = KeyPairGenerator.getInstance("EC").generateKeyPair();
            var second = KeyPairGenerator.getInstance("EC").generateKeyPair();
            fixture.accounts.configure(7,PublicKeyEntry.toString(first.getPublic()),false);
            fixture.accounts.configure(8,PublicKeyEntry.toString(second.getPublic()),false);
            client.start();
            try (var sa=client.connect("u7-p12","127.0.0.1",fixture.server.port()).verify(5000).getSession();
                 var sb=client.connect("u8-p12","127.0.0.1",fixture.server.port()).verify(5000).getSession()) {
                sa.addPublicKeyIdentity(first); sa.auth().verify(5000);
                sb.addPublicKeyIdentity(second); sb.auth().verify(5000);
                try (var a=SftpClientFactory.instance().createSftpClient(sa); var b=SftpClientFactory.instance().createSftpClient(sb)) {
                    try (var out=a.write("report.txt")) { out.write(7); }
                    assertThrows(IOException.class,()->b.stat("report.txt"));
                    try (var out=b.write("report.txt")) { out.write(8); }
                    try (var in=a.read("report.txt")) { assertEquals(7,in.read()); }
                    fixture.accounts.setMode(8,EngineSftpAccounts.AccessMode.READ_ONLY);
                    assertThrows(IOException.class,()->b.stat("report.txt"));
                }
            }
            try (var session=client.connect("u8-p12","127.0.0.1",fixture.server.port()).verify(5000).getSession()) {
                session.addPublicKeyIdentity(second); session.auth().verify(5000);
                try (var reader=SftpClientFactory.instance().createSftpClient(session)) {
                    assertThrows(IOException.class,()->{try(var out=reader.write("report.txt")){out.write(9);}});
                    try(var in=reader.read("report.txt")){assertEquals(8,in.read());}
                }
            }
        }
    }

    @Test(timeout=20000) public void transferWaitsForTheNextWindowWithoutLosingFileBytes() throws Exception {
        try (var fixture = new EngineSftpFixture(files.getRoot().toPath()); var client = SshClient.setUpDefaultClient()) {
            fixture.protection.save(new EngineSftpProtection.Policy(60,1,5,60,60,0));
            var key = KeyPairGenerator.getInstance("EC").generateKeyPair();
            fixture.accounts.configure(7,PublicKeyEntry.toString(key.getPublic()),false);
            client.start();
            try (var session=client.connect("u7","127.0.0.1",fixture.server.port()).verify(5000).getSession()) {
                session.addPublicKeyIdentity(key);
                session.auth().verify(5000);
                try (var sftp=SftpClientFactory.instance().createSftpClient(session)) {
                    long firstWindow = System.currentTimeMillis() / 1000;
                    try(var file=sftp.open("paced.bin",org.apache.sshd.sftp.client.SftpClient.OpenMode.Write,
                            org.apache.sshd.sftp.client.SftpClient.OpenMode.Create)) {
                        for (int i=0;i<3;i++) sftp.write(file,i,new byte[]{(byte)i},0,1);
                    }
                    assertTrue("每秒一次时，三次写入至少占用三个窗口", System.currentTimeMillis() / 1000 >= firstWindow + 2);
                    try(var in=sftp.read("paced.bin")) { assertArrayEquals(new byte[]{0,1,2},in.readAllBytes()); }
                }
            }
        }
    }

    @Test public void legitimateBlockedServiceCanStartConsoleButCannotReadUntilUnblocked() throws Exception {
        try (var fixture = new EngineSftpFixture(files.getRoot().toPath())) {
            fixture.protection.save(new EngineSftpProtection.Policy(120,256,1,60,900,0));
            var wrong = new DefaultSftpClient("127.0.0.1",fixture.server.port(),"system","incorrect",fixture.pin,fixture.protection);
            assertThrows(IllegalStateException.class,wrong::start);
            var cold = new DefaultSftpClient("127.0.0.1",fixture.server.port(),"system",fixture.password,fixture.pin,fixture.protection);
            try {
                cold.start();
                // 同一时刻、同一来源的错误密码不能借用其他节点的封禁回执。
                var invalid = new DefaultSftpClient("127.0.0.1",fixture.server.port(),"system","still-incorrect",fixture.pin,fixture.protection);
                assertThrows(IllegalStateException.class,invalid::start);
                assertThrows(IllegalStateException.class,()->cold.contains("/managed/sample"));
                for (var ban : fixture.protection.bans(1,10).getRecords()) fixture.protection.unblock(ban.subject(),ban.blockedUntil());
                assertFalse(cold.contains("/managed/sample"));
            } finally { cold.close(); }
        }
    }

    @Test public void independentBusinessNodesShareFrozenAndPendingFiles() throws Exception {
        try (var fixture = new EngineSftpFixture(files.getRoot().toPath())) {
            var a = new SftpObjectStore(fixture.nodeA, null);
            var b = new SftpObjectStore(fixture.nodeB, null);
            byte[] bytes = "image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String digest = com.hengshucredit.rule.server.artifact.Sha256Digests.bytes(bytes);
            String ref = "ledger/png/" + digest;
            a.put(ref, digest, bytes);
            assertArrayEquals(bytes, b.read(ref, digest, bytes.length));
            String frozen = "ts-file-v1:packages/" + digest.substring(0, 2) + "/snapshot--" + digest + ".zip";
            var archive = files.newFile().toPath();
            java.nio.file.Files.write(archive, bytes);
            a.putArchive(frozen, digest, archive, bytes.length);
            assertArrayEquals(bytes, b.read(frozen, digest, bytes.length));
            String original = "ts-file-v1:models/12/" + digest.substring(0, 2) + "/model--" + digest + ".onnx";
            b.put(original, digest, bytes);
            assertArrayEquals(bytes, a.read(original, digest, bytes.length));
            assertFalse(a.contains(original.replace("/12/", "/13/")));
            try (var objects = java.nio.file.Files.walk(files.getRoot().toPath().resolve("files/managed/objects"))) {
                assertEquals(1, objects.filter(java.nio.file.Files::isRegularFile).count());
            }
            String pending = "ledger/pending/10/" + digest;
            a.put(pending, digest, bytes);
            b.removePending(10, digest);
            assertFalse(a.contains(pending));
            var wrongPin = new DefaultSftpClient("127.0.0.1", fixture.server.port(), "system", fixture.password, "SHA256:incorrect");
            assertThrows(IllegalStateException.class, wrongPin::start);
            int port = fixture.server.port();
            fixture.server.stop();
            var restarted = new EngineSftpServer(files.getRoot().toPath(), fixture.accounts, fixture.protection, "127.0.0.1", port, "system", fixture.password);
            restarted.start();
            try {
                assertArrayEquals(bytes, b.read(original, digest, bytes.length));
            } finally { restarted.stop(); }
        }
    }
}
