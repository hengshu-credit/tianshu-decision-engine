package com.hengshucredit.rule.server.sftp;

import com.hengshucredit.rule.server.upload.UploadSecurity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.auth.password.UserAuthPasswordFactory;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
public final class DefaultSftpClient {
    private final SshClient ssh = SshClient.setUpDefaultClient();
    private final String host, user, password, fingerprint;
    private final int port;
    private ClientSession session;

    public DefaultSftpClient(@Value("${engine.sftp.host:127.0.0.1}") String host,
                             @Value("${engine.sftp.port:2222}") int port,
                             @Value("${engine.sftp.username:engine-storage}") String user,
                             @Value("${engine.sftp.password:}") String password,
                             @Value("${engine.sftp.host-fingerprint:}") String fingerprint) {
        this.host = host; this.port = port; this.user = user;
        this.password = password; this.fingerprint = fingerprint.trim();
    }

    @PostConstruct
    public void start() throws IOException {
        if (host.isBlank() || user.isBlank() || password.isBlank() || !fingerprint.startsWith("SHA256:"))
            throw new IllegalStateException("请配置默认 SFTP 地址、服务账号、密码和 SHA256 主机指纹");
        ssh.setServerKeyVerifier((session, address, key) ->
                fingerprint.equals(KeyUtils.getFingerPrint(BuiltinDigests.sha256, key)));
        ssh.setUserAuthFactories(List.of(UserAuthPasswordFactory.INSTANCE));
        ssh.start();
        try (var sftp = channel()) {
            directories(sftp, "/managed");
        } catch (IOException | RuntimeException e) {
            close();
            throw new IllegalStateException("默认 SFTP 启动连接失败，请检查可达性、主机指纹和服务账号权限", e);
        }
    }

    private synchronized SftpClient channel() throws IOException {
        if (session != null && session.isOpen() && session.isAuthenticated()) {
            try { return SftpClientFactory.instance().createSftpClient(session); }
            catch (IOException disconnected) { session.close(true); }
        }
        session = ssh.connect(user, host, port).verify(10000).getSession();
        try {
            session.addPasswordIdentity(password);
            session.auth().verify(10000);
            return SftpClientFactory.instance().createSftpClient(session);
        } catch (IOException | RuntimeException e) {
            session.close(true);
            throw e;
        }
    }

    @PreDestroy public synchronized void close() {
        if (session != null) session.close(true);
        ssh.stop();
    }

    static String relative(String value) {
        if (value == null || value.isBlank() || value.length() > 1024) throw new IllegalArgumentException("无效的 SFTP 文件路径");
        String path = value.startsWith("/") ? value.substring(1) : value;
        for (String part : path.split("/", -1)) {
            UploadSecurity.filename(part);
            if (part.contains(":") || part.endsWith(".") || part.endsWith(" ")) throw new IllegalArgumentException("无效的 SFTP 文件路径");
        }
        return path;
    }

    private static SftpClient.Attributes stat(SftpClient sftp, String path) throws IOException {
        try { return sftp.lstat(path); }
        catch (SftpException e) {
            if (e.getStatus() == SftpConstants.SSH_FX_NO_SUCH_FILE || e.getStatus() == SftpConstants.SSH_FX_NO_SUCH_PATH) return null;
            throw e;
        }
    }

    private static void directories(SftpClient sftp, String directory) throws IOException {
        String path = "";
        for (String name : directory.substring(1).split("/")) {
            path += "/" + name;
            var attrs = stat(sftp, path);
            if (attrs == null) {
                try { sftp.mkdir(path); }
                catch (SftpException race) {
                    attrs = stat(sftp, path);
                    if (attrs == null || !attrs.isDirectory()) throw race;
                }
            } else if (!attrs.isDirectory()) throw new IOException("默认 SFTP 的父目录无效");
        }
    }

    private static SftpClient.Attributes file(SftpClient sftp, String path) throws IOException {
        for (int end = path.indexOf('/', 1); end > 0; end = path.indexOf('/', end + 1)) {
            var dir = stat(sftp, path.substring(0, end));
            if (dir == null) return null;
            if (!dir.isDirectory()) throw new IOException("默认 SFTP 路径包含非目录项");
        }
        var attrs = stat(sftp, path);
        if (attrs != null && !attrs.isRegularFile()) throw new IOException("默认 SFTP 只允许读取普通文件");
        return attrs;
    }

    void remove(String path) {
        try (var sftp = channel()) {
            if (file(sftp, path) != null) sftp.remove(path);
        } catch (IOException e) { throw new IllegalStateException("无法清理 SFTP 暂存文件", e); }
    }

    public boolean contains(String path) {
        try (var sftp = channel()) { return file(sftp, path) != null; }
        catch (IOException e) { throw new IllegalStateException("无法查询默认 SFTP", e); }
    }

    long size(String path) {
        try (var sftp = channel()) {
            var attrs = file(sftp, path);
            if (attrs == null) throw new IllegalArgumentException("指定的 SFTP 文件不存在");
            return attrs.getSize();
        } catch (IOException e) { throw new IllegalStateException("默认 SFTP 文件查询失败", e); }
    }

    InputStream open(String path) throws IOException {
        var sftp = channel();
        try {
            if (file(sftp, path) == null) throw new IOException("指定的 SFTP 文件不存在");
            return new java.io.FilterInputStream(sftp.read(path)) {
                @Override public void close() throws IOException {
                    try { super.close(); } finally { sftp.close(); }
                }
            };
        } catch (IOException | RuntimeException e) {
            sftp.close();
            throw e;
        }
    }

    public byte[] read(String path, long limit) {
        try (var sftp = channel()) {
            var attrs = file(sftp, path);
            if (attrs == null) throw new IllegalArgumentException("指定的 SFTP 文件不存在");
            if (attrs.getSize() <= 0 || attrs.getSize() > limit) throw new IllegalArgumentException("文件为空或超过读取上限");
            byte[] bytes = new byte[Math.toIntExact(attrs.getSize())];
            try (var in = sftp.read(path)) {
                if (in.readNBytes(bytes, 0, bytes.length) != bytes.length || in.read() != -1)
                    throw new IOException("读取期间文件大小发生变化");
            }
            return bytes;
        } catch (IOException e) { throw new IllegalStateException("默认 SFTP 文件读取失败", e); }
    }

    public void publish(String path, String expected, InputStream source, long length) {
        try (var sftp = channel()) {
            String parent = path.substring(0, path.lastIndexOf('/'));
            directories(sftp, parent);
            if (file(sftp, path) != null) {
                verify(sftp, path, expected, length);
                return;
            }
            String temp = parent + "/.upload-" + UUID.randomUUID();
            try {
                var sha = sha256();
                try (var out = sftp.write(temp, SftpClient.OpenMode.Create, SftpClient.OpenMode.Exclusive, SftpClient.OpenMode.Write)) {
                    long sent = new DigestInputStream(source, sha).transferTo(out);
                    if (sent != length || !HexFormat.of().formatHex(sha.digest()).equals(expected))
                        throw new IOException("待保存文件的长度或摘要不匹配");
                }
                try { sftp.rename(temp, path); }
                catch (IOException race) {
                    if (file(sftp, path) == null) throw race;
                    verify(sftp, path, expected, length);
                }
            } finally {
                if (stat(sftp, temp) != null) sftp.remove(temp);
            }
        } catch (IOException e) { throw new IllegalStateException("默认 SFTP 文件写入失败", e); }
    }

    private static void verify(SftpClient sftp, String path, String expected, long length) throws IOException {
        if (sftp.stat(path).getSize() != length) throw new IOException("目标文件长度不匹配");
        var digest = sha256();
        try (var in = new DigestInputStream(sftp.read(path), digest)) {
            if (in.transferTo(java.io.OutputStream.nullOutputStream()) != length || !HexFormat.of().formatHex(digest.digest()).equals(expected))
                throw new IOException("目标文件摘要不匹配");
        }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
