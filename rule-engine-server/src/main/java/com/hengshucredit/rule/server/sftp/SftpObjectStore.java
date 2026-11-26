package com.hengshucredit.rule.server.sftp;

import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.service.MysqlModelBlobStore;
import com.hengshucredit.rule.server.service.SharedFileStore;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 业务层完成资源授权后，按不可变引用访问统一文件仓。 */
@Component
public final class SftpObjectStore implements SharedFileStore {
    private final DefaultSftpClient client;
    private final MysqlModelBlobStore legacy;

    public SftpObjectStore(DefaultSftpClient client, MysqlModelBlobStore legacy) {
        this.client = client;
        this.legacy = legacy;
    }

    private String path(String ref) {
        String key = ref.startsWith("ts-file-v1:") ? ref.substring("ts-file-v1:".length()) : ref;
        return "/managed/" + DefaultSftpClient.relative(key);
    }

    public boolean contains(String ref) { return client.contains(path(ref)); }

    public void put(String ref, String digest, byte[] bytes) {
        if (ref.startsWith("ts-file-v1:")) {
            modelObject(ref, digest, new ByteArrayInputStream(bytes), bytes.length);
        } else {
            client.publish(path(ref), digest, new ByteArrayInputStream(bytes), bytes.length);
        }
    }

    public void putArchive(String ref, String digest, Path archive, int length) {
        try (var input = Files.newInputStream(archive)) { modelObject(ref, digest, input, length); }
        catch (IOException e) { throw new IllegalStateException("无法读取待保存的冻结制品", e); }
    }

    private static String objectPath(String digest) {
        if (digest == null || !digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("无效的文件摘要");
        return "/managed/objects/" + digest.substring(0, 2) + "/" + digest;
    }

    private void modelObject(String ref, String digest, java.io.InputStream stream, long length) {
        client.publish(objectPath(digest), digest, stream, length);
        byte[] binding = digest.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        client.publish(path(ref), Sha256Digests.bytes(binding), new ByteArrayInputStream(binding), binding.length);
    }

    public boolean restoreFromDatabase(String ref, String expected, long limit) {
        if (!legacy.contains(ref)) return false;
        byte[] bytes = legacy.read(ref, expected, limit);
        put(ref, expected, bytes);
        return true;
    }

    public void removePending(long assetId, String digest) {
        client.remove(path("ledger/pending/" + assetId + "/" + digest));
    }

    public byte[] read(String ref, String expected, long limit) {
        if (ref.startsWith("ledger/png/") && !contains(ref)) restoreFromDatabase(ref, expected, limit);
        String source = path(ref);
        if (ref.startsWith("ts-file-v1:")) {
            String bound = new String(client.read(source, 64), java.nio.charset.StandardCharsets.US_ASCII);
            if (!bound.equals(expected)) throw new IllegalStateException("SFTP 文件引用与模型摘要不匹配");
            source = objectPath(bound);
        }
        byte[] bytes = client.read(source, limit);
        if (!Sha256Digests.bytes(bytes).equals(expected)) throw new IllegalStateException("SFTP 文件摘要不匹配");
        return bytes;
    }
}
