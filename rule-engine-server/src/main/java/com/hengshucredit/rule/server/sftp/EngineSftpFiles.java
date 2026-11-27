package com.hengshucredit.rule.server.sftp;

import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import com.hengshucredit.rule.server.upload.UploadSecurity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** 项目和个人收件目录使用同一远程连接，路径按真实身份限定。 */
@Service
public class EngineSftpFiles {
    private final DefaultSftpClient client;
    private final ConsoleProjectAccess access;

    public EngineSftpFiles(DefaultSftpClient client, ConsoleProjectAccess access) {
        this.client = client;
        this.access = access;
    }

    public String projectPath(Long project, String path) {
        if (project == null || project <= 0) throw new IllegalArgumentException("读取项目 SFTP 文件需要确定执行项目");
        String relative = DefaultSftpClient.relative(path);
        int slash = relative.indexOf('/');
        if (slash < 0 || !relative.substring(0,slash).matches("u[1-9][0-9]{0,17}")) throw new IllegalArgumentException("请提供带上传账户的项目文件路径");
        var scope = access == null ? null : access.current();
        if (scope != null && (!scope.allowsProject(project) || scope.userId() == null || !relative.startsWith("u" + scope.userId() + "/")))
            throw new com.hengshucredit.rule.server.security.ConsoleAccessDeniedException("仅可读取本人在授权项目中的文件");
        return relative;
    }

    public byte[] readProject(Long project, String path) {
        return client.read("/projects/" + project + "/" + projectPath(project,path), 10L << 20);
    }

    public MultipartFile input(MultipartFile uploaded, String address) {
        if (address == null || address.isBlank()) {
            if (uploaded == null) throw new IllegalArgumentException("请选择文件或填写个人 SFTP 文件路径");
            return uploaded;
        }
        if (uploaded != null) throw new IllegalArgumentException("请选择一种文件来源");
        var scope = access.current();
        if (scope == null || scope.userId() == null) throw new IllegalArgumentException("需要已登录的控制台账户");
        String path = "/private/u" + scope.userId() + "/" + DefaultSftpClient.relative(address);
        return new InboxFile(client, path, client.size(path));
    }

    private record InboxFile(DefaultSftpClient client, String path, long size) implements MultipartFile {
        public String getName() { return "file"; }
        public String getOriginalFilename() { return path.substring(path.lastIndexOf('/') + 1); }
        public String getContentType() { return "application/octet-stream"; }
        public boolean isEmpty() { return getSize() == 0; }
        public long getSize() { return size; }
        public InputStream getInputStream() throws IOException { return client.open(path); }
        public byte[] getBytes() throws IOException { return UploadSecurity.read(this, 268435456); }
        public void transferTo(File target) throws IOException {
            try (var input = getInputStream()) { Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        }
    }
}
