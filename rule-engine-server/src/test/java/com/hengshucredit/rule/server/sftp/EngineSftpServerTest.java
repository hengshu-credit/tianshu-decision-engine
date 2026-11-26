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
            });
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
                        assertThrows(IllegalArgumentException.class, () -> storage.readProject(13L, "u7/request.json"));
                        assertThrows(IllegalArgumentException.class, () -> storage.input(null, "request.json"));
                        accounts.configure(7, null, true);
                        assertThrows(IOException.class, () -> sftp.stat("request.json"));
                    }
                }
            }
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
            var restarted = new EngineSftpServer(files.getRoot().toPath(), fixture.accounts, "127.0.0.1", port, "system", fixture.password);
            restarted.start();
            try {
                assertArrayEquals(bytes, b.read(original, digest, bytes.length));
            } finally { restarted.stop(); }
        }
    }
}
