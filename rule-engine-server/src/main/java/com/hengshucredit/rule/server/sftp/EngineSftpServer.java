package com.hengshucredit.rule.server.sftp;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.sshd.common.AttributeRepository.AttributeKey;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.UserAuthPublicKeyFactory;
import org.apache.sshd.server.forward.RejectAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.SftpEventListener;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;

import static org.apache.sshd.sftp.common.SftpConstants.*;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
public final class EngineSftpServer {
    private static final AttributeKey<EngineSftpAccounts.Identity> IDENTITY = new AttributeKey<>();
    private static final Set<Integer> OPERATIONS = Set.of(SSH_FXP_INIT, SSH_FXP_OPEN, SSH_FXP_CLOSE, SSH_FXP_READ,
            SSH_FXP_WRITE, SSH_FXP_STAT, SSH_FXP_LSTAT, SSH_FXP_FSTAT, SSH_FXP_OPENDIR, SSH_FXP_READDIR,
            SSH_FXP_REMOVE, SSH_FXP_MKDIR, SSH_FXP_RMDIR, SSH_FXP_REALPATH, SSH_FXP_RENAME, SSH_FXP_EXTENDED);
    private final EngineSftpFiles files;
    private final EngineSftpAccounts accounts;
    private final SshServer server = SshServer.setUpDefaultServer();

    EngineSftpServer(EngineSftpFiles files, EngineSftpAccounts accounts,
                     @Value("${rule-engine.sftp.bind:127.0.0.1}") String bind,
                     @Value("${rule-engine.sftp.port:2222}") int port) {
        this.files = files;
        this.accounts = accounts;
        server.setHost(bind);
        server.setPort(port);
    }

    @PostConstruct
    void start() throws IOException {
        server.setKeyPairProvider(hostKey(files.root()));
        server.setUserAuthFactories(List.of(UserAuthPublicKeyFactory.INSTANCE));
        server.setPublickeyAuthenticator((name, key, session) -> {
            var identity = accounts.authenticate(name, key);
            if (identity == null) return false;
            session.setAttribute(IDENTITY, identity);
            return true;
        });
        server.setForwardingFilter(RejectAllForwardingFilter.INSTANCE);
        server.setFileSystemFactory(new VirtualFileSystemFactory() {
            @Override public Path getUserHomeDir(SessionContext session) throws IOException {
                var id = session.getAttribute(IDENTITY);
                if (id == null) throw new IOException("需要身份验证");
                return Files.createDirectories(files.home(id.user(), id.project()));
            }
        });
        var sftp = new SftpSubsystemFactory.Builder().build();
        sftp.addSftpEventListener(new SftpEventListener() {
            @Override public void received(ServerSession session, int type, int requestId) throws IOException {
                if (!OPERATIONS.contains(type)) throw new IOException("不支持该文件操作");
                if (!accounts.active(session.getAttribute(IDENTITY))) {
                    session.close(true);
                    throw new IOException("账户授权已变更");
                }
            }
            @Override public void receivedExtension(ServerSession session, String extension, int requestId) throws IOException {
                if (!Set.of("posix-rename@openssh.com", "limits@openssh.com", "fsync@openssh.com").contains(extension))
                    throw new IOException("不支持该 SFTP 扩展");
            }
            @Override public void writing(ServerSession session, String handle, FileHandle file, long offset,
                                          byte[] bytes, int start, int length) throws IOException {
                long position = file.isOpenAppend() ? file.getFileChannel().size() : offset;
                if (position < 0 || position > 268435456L - length) throw new IOException("文件超过 256 MiB 限制");
            }
        });
        server.setSubsystemFactories(List.of(sftp));
        server.start();
    }

    private FileKeyPairProvider hostKey(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path key = directory.resolve("host-key");
        try (var channel = FileChannel.open(directory.resolve("host-key.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            if (!Files.exists(key)) {
                Path temp = Files.createTempDirectory(directory, ".host-key-");
                Path pending = temp.resolve("key");
                try {
                    var generator = new SimpleGeneratorHostKeyProvider(pending);
                    generator.setAlgorithm("EC");
                    if (generator.loadKeys(null).isEmpty() || !Files.isRegularFile(pending)) throw new IOException("无法生成 SFTP 主机密钥");
                    Files.move(pending, key, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } finally {
                    Files.deleteIfExists(pending);
                    Files.deleteIfExists(temp);
                }
            }
        }
        return new FileKeyPairProvider(key);
    }

    int port() { return server.getPort(); }

    @PreDestroy
    void stop() throws IOException { server.stop(true); }
}
