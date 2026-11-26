package com.hengshucredit.rule.server.sftp;

import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import com.hengshucredit.rule.server.upload.UploadSecurity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** SFTP 客户端和引擎使用同一交接目录。原件和制品在交接目录外冻结。 */
@Service
public final class EngineSftpFiles {
    private final Path root;
    private final ConsoleProjectAccess access;

    public EngineSftpFiles(@Value("${rule-engine.sftp.directory:${rule-engine.model.storage-directory:./data/model-files}/engine-sftp}") String directory,
                          ConsoleProjectAccess access) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
        this.access = access;
    }

    Path root() { return root; }

    Path home(long account, Long project) {
        if (account <= 0) throw new IllegalArgumentException("缺少上传账户");
        return (project == null ? root.resolve("private") : projectRoot(project)).resolve("u" + account);
    }

    private Path projectRoot(Long project) {
        if (project == null || project <= 0) throw new IllegalArgumentException("读取项目 SFTP 文件需要确定执行项目");
        return root.resolve("projects").resolve(project.toString());
    }

    private Path resolve(Path base, String address) {
        if (address == null || address.isBlank() || address.length() > 1024) throw new IllegalArgumentException("无效的 SFTP 文件路径");
        Path target = base;
        for (String part : address.replaceFirst("^/", "").split("/", -1)) {
            UploadSecurity.filename(part);
            if (part.contains(":") || part.endsWith(".") || part.endsWith(" ")) throw new IllegalArgumentException("无效的文件路径");
            target = target.resolve(part);
            if (Files.isSymbolicLink(target)) throw new IllegalArgumentException("文件路径不能包含链接");
        }
        if (!target.normalize().startsWith(base) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("指定的 SFTP 文件不存在");
        return target;
    }

    public byte[] readProject(Long project, String path) {
        var file = new InboxFile(resolve(projectRoot(project), path));
        try {
            return UploadSecurity.read(file, 10L * 1024 * 1024);
        } catch (IOException e) { throw new IllegalArgumentException("项目 SFTP 文件读取失败"); }
    }

    public MultipartFile input(MultipartFile uploaded, String address) {
        if (address == null || address.isBlank()) {
            if (uploaded == null) throw new IllegalArgumentException("请选择文件或填写个人 SFTP 文件路径");
            return uploaded;
        }
        if (uploaded != null) throw new IllegalArgumentException("请选择一种文件来源");
        var scope = access.current();
        if (scope == null || scope.userId() == null) throw new IllegalArgumentException("需要已登录的控制台账户");
        return new InboxFile(resolve(home(scope.userId(), null), address));
    }

    private record InboxFile(Path path) implements MultipartFile {
        public String getName() { return "file"; }
        public String getOriginalFilename() { return path.getFileName().toString(); }
        public String getContentType() { return "application/octet-stream"; }
        public boolean isEmpty() { return getSize() == 0; }
        public long getSize() {
            try { return Files.size(path); }
            catch (IOException e) { throw new IllegalArgumentException("SFTP 文件不可用"); }
        }
        public InputStream getInputStream() throws IOException { return Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS); }
        public byte[] getBytes() throws IOException { return UploadSecurity.read(this, 268435456); }
        public void transferTo(File target) throws IOException {
            try (var input = getInputStream()) { Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        }
    }
}
