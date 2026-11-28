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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.security.PublicKey;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.sshd.sftp.common.SftpConstants.*;

public final class EngineSftpServer {
    private static final AttributeKey<EngineSftpAccounts.Identity> IDENTITY = new AttributeKey<>();
    private static final Set<Integer> OPERATIONS = Set.of(SSH_FXP_INIT, SSH_FXP_OPEN, SSH_FXP_CLOSE, SSH_FXP_READ,
            SSH_FXP_WRITE, SSH_FXP_STAT, SSH_FXP_LSTAT, SSH_FXP_FSTAT, SSH_FXP_OPENDIR, SSH_FXP_READDIR,
            SSH_FXP_REMOVE, SSH_FXP_MKDIR, SSH_FXP_RMDIR, SSH_FXP_REALPATH, SSH_FXP_RENAME, SSH_FXP_EXTENDED);
    private static final Set<Integer> MUTATIONS = Set.of(SSH_FXP_WRITE, SSH_FXP_REMOVE, SSH_FXP_MKDIR, SSH_FXP_RMDIR, SSH_FXP_RENAME);
    private static final Set<String> EXTENSIONS = Set.of("posix-rename@openssh.com", "limits@openssh.com", "fsync@openssh.com");
    private final Path directory;
    private final String username;
    private final byte[] password;
    private static final AttributeKey<Boolean> SERVICE = new AttributeKey<>();
    private final EngineSftpAccounts accounts;
    private final EngineSftpProtection protection;
    private final SshServer server = SshServer.setUpDefaultServer();

    EngineSftpServer(Path directory, EngineSftpAccounts accounts, EngineSftpProtection protection, String bind, int port, String username, String password) {
        if (username.isBlank() || username.matches("u[0-9]+(-p[0-9]+)?") || password.isBlank())
            throw new IllegalArgumentException("请配置独立的引擎 SFTP 服务账户与密码");
        this.directory = directory.toAbsolutePath().normalize();
        this.username = username;
        this.password = password.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        this.accounts = accounts;
        this.protection = protection;
        server.setHost(bind);
        server.setPort(port);
    }

    @PostConstruct
    void start() throws IOException {
        var keys = hostKey(directory);
        server.setKeyPairProvider(keys);
        server.setUserAuthFactories(List.of(UserAuthPublicKeyFactory.INSTANCE,
                org.apache.sshd.server.auth.password.UserAuthPasswordFactory.INSTANCE));
        server.setPasswordAuthenticator((login, supplied, session) -> authenticate(session, login, supplied, null));
        server.setPublickeyAuthenticator((login, key, session) -> authenticate(session, login, null, key));
        server.addSessionListener(new org.apache.sshd.common.session.SessionListener() {
            @Override public void sessionEvent(org.apache.sshd.common.session.Session session, Event event) {
                if (event == Event.Authenticated) {
                    var id = session.getAttribute(IDENTITY);
                    protection.authenticated(id == null ? "SERVICE" : id.subject());
                }
            }
        });
        server.setForwardingFilter(RejectAllForwardingFilter.INSTANCE);
        server.setFileSystemFactory(new VirtualFileSystemFactory() {
            @Override public Path getUserHomeDir(SessionContext session) throws IOException {
                Path storage = directory.resolve("files");
                if (Boolean.TRUE.equals(session.getAttribute(SERVICE))) return Files.createDirectories(storage);
                var id = session.getAttribute(IDENTITY);
                if (id == null) throw new IOException("需要身份验证");
                return Files.createDirectories(id.home(storage));
            }
        });
        var sftp = new SftpSubsystemFactory.Builder().build();
        sftp.setFileSystemAccessor(new org.apache.sshd.sftp.server.SftpFileSystemAccessor() {
            @Override public java.nio.channels.SeekableByteChannel openFile(org.apache.sshd.sftp.server.SftpSubsystemProxy subsystem,
                    FileHandle handle, Path path, String id, Set<? extends java.nio.file.OpenOption> options,
                    java.nio.file.attribute.FileAttribute<?>... attrs) throws IOException {
                if (readOnly(subsystem.getSession()) && options.stream().anyMatch(o ->
                        o != StandardOpenOption.READ && o != java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    throw new java.nio.file.AccessDeniedException("SFTP 账户只读");
                return org.apache.sshd.sftp.server.SftpFileSystemAccessor.super.openFile(subsystem,handle,path,id,options,attrs);
            }
        });
        sftp.addSftpEventListener(new SftpEventListener() {
            @Override public void received(ServerSession session, int type, int requestId) throws IOException {
                if (readOnly(session) && MUTATIONS.contains(type))
                    throw new java.nio.file.AccessDeniedException("SFTP 账户只读");
                if (!OPERATIONS.contains(type)) throw new IOException("不支持该文件操作");
                if (!Boolean.TRUE.equals(session.getAttribute(SERVICE)) && !accounts.active(session.getAttribute(IDENTITY))) {
                    session.close(true);
                    throw new IOException("账户授权已变更");
                }
            }
            @Override public void receivedExtension(ServerSession session, String extension, int requestId) throws IOException {
                if (readOnly(session) && !"limits@openssh.com".equals(extension)) throw new java.nio.file.AccessDeniedException("SFTP 账户只读");
                if (!EXTENSIONS.contains(extension))
                    throw new IOException("不支持该 SFTP 扩展");
            }
            @Override public void reading(ServerSession session, String handle, FileHandle file, long offset,
                                          byte[] bytes, int start, int length) throws IOException {
                checkFileRate(session);
            }
            @Override public void writing(ServerSession session, String handle, FileHandle file, long offset,
                                          byte[] bytes, int start, int length) throws IOException {
                checkFileRate(session);
                long position = file.isOpenAppend() ? file.getFileChannel().size() : offset;
                long limit = Boolean.TRUE.equals(session.getAttribute(SERVICE)) ? 512L << 20 : 256L << 20;
                if (position < 0 || position > limit - length) throw new IOException("文件超过单文件大小限制");
            }
        });
        server.setSubsystemFactories(List.of(sftp));
        var key = keys.loadKeys(null).iterator().next().getPublic();
        org.slf4j.LoggerFactory.getLogger(EngineSftpServer.class).info("SFTP host fingerprint {}",
                org.apache.sshd.common.config.keys.KeyUtils.getFingerPrint(org.apache.sshd.common.digest.BuiltinDigests.sha256, key));
        server.start();
    }

    private boolean authenticate(ServerSession session, String login, String supplied, PublicKey key) {
        String address = peer(session);
        if (username.equals(login)) {
            boolean accepted = protection.authenticate(address, "SERVICE", () -> supplied != null
                    && java.security.MessageDigest.isEqual(password, supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    supplied == null ? null : session.getSessionId());
            if (accepted) session.setAttribute(SERVICE, true);
            return accepted;
        }
        String subject = accounts.subject(login);
        var identity = new AtomicReference<EngineSftpAccounts.Identity>();
        boolean accepted = protection.authenticate(address, subject, () -> {
            if (subject != null && subject.startsWith("USER:") && key != null)
                identity.set(accounts.authenticate(login, key));
            else if (supplied != null)
                identity.set(accounts.authenticateCredential(login, subject, address, supplied));
            return identity.get() != null;
        });
        if (accepted) session.setAttribute(IDENTITY, identity.get());
        return accepted;
    }

    private static String peer(ServerSession session) {
        var socket = (java.net.InetSocketAddress)session.getIoSession().getRemoteAddress();
        return socket.getAddress().getHostAddress();
    }

    private static boolean readOnly(ServerSession session) {
        var identity = session.getAttribute(IDENTITY);
        return identity != null && identity.mode() == EngineSftpAccounts.AccessMode.READ_ONLY;
    }

    private void checkFileRate(ServerSession session) throws IOException {
        var identity = session.getAttribute(IDENTITY);
        if (identity == null) return;
        while (!protection.allowFileRequest(identity.subject())) {
            if (!session.isOpen()) throw new IOException("上传账户会话已关闭");
            try { Thread.sleep(1000 - Math.floorMod(System.currentTimeMillis(), 1000)); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("文件传输已中断", interrupted);
            }
            if (!accounts.active(identity)) throw new IOException("上传账户授权已变更");
        }
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
