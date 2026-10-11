package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 只记录已交付前缀；调用方持有 journal 目录锁并串行化所有文件操作。 */
class JournalReplayProgress {
    private static final int MAX_CHECKPOINT_BYTES = 4096;
    private final Path dataFile;
    private final Path checkpointFile;
    private long offset;
    private long observedSize;
    private MessageDigest prefix = sha256();

    JournalReplayProgress(Path dataFile) {
        this.dataFile = dataFile;
        checkpointFile = dataFile.resolveSibling(dataFile.getFileName() + ".cursor");
    }

    long offset() { return offset; }

    void restore() throws IOException {
        if (!Files.exists(checkpointFile)) return;
        Checkpoint saved;
        try (var input = Files.newInputStream(checkpointFile)) {
            byte[] bytes = input.readNBytes(MAX_CHECKPOINT_BYTES + 1);
            if (bytes.length > MAX_CHECKPOINT_BYTES) throw new IOException("journal 恢复进度文件超过上限");
            try { saved = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8), Checkpoint.class); }
            catch (RuntimeException error) { throw new IOException("journal 恢复进度文件无法解析", error); }
        }
        if (saved == null || !Integer.valueOf(1).equals(saved.version()) || saved.offset() == null
                || saved.observedSize() == null || saved.offset() < 0 || saved.observedSize() < saved.offset()
                || saved.prefixSha256() == null || !saved.prefixSha256().matches("[0-9a-f]{64}")) {
            throw new IOException("journal 恢复进度字段无效");
        }
        if (!metadataChecksum(saved.version(), saved.offset(), saved.observedSize(), saved.prefixSha256()).equals(saved.checksum())) {
            throw new IOException("journal 恢复进度完整性校验失败");
        }
        if (saved.offset() == 0 && !emptyDigest().equals(saved.prefixSha256())) {
            throw new IOException("journal 空前缀的摘要无效");
        }
        if (!Files.exists(dataFile)) {
            // 已确认整份文件后删除数据、清零进度之间退出，可安全完成清理。
            if (saved.offset().equals(saved.observedSize())) {
                resetAfterReplacement();
                return;
            }
            throw new IOException("journal 数据文件缺失，但恢复进度仍有未确认尾部");
        }
        long size = Files.size(dataFile);
        if (size < saved.observedSize()) throw new IOException("journal 数据文件比已记录的长度更短");
        MessageDigest restored = sha256();
        hashRange(0, saved.offset(), restored);
        if (!saved.prefixSha256().equals(digestHex(restored, saved.offset()))) {
            throw new IOException("journal 原始前缀与恢复进度摘要不一致，不能跳过记录");
        }
        offset = saved.offset();
        observedSize = saved.observedSize();
        prefix = restored;
        if (offset > 0 && offset == size) finishCompletedFile();
    }

    void acknowledge(long nextOffset) throws IOException {
        if (nextOffset <= offset) return;
        long size = Files.size(dataFile);
        if (size < observedSize || nextOffset > size) throw new IOException("journal 文件长度与确认位置不一致");
        MessageDigest nextPrefix = copyDigest(prefix, offset);
        hashRange(offset, nextOffset, nextPrefix);
        Checkpoint next = checkpoint(nextOffset, size, digestHex(nextPrefix, nextOffset));
        persist(next);
        offset = nextOffset;
        observedSize = size;
        prefix = nextPrefix;
        if (offset == size) finishCompletedFile();
    }

    /** 兼容失败侧重写：新文件从0开始，旧进度残留会由启动摘要检查识别。 */
    void resetAfterReplacement() throws IOException {
        offset = 0;
        observedSize = 0;
        prefix = sha256();
        persist(checkpoint(0, 0, emptyDigest()));
    }

    private void finishCompletedFile() throws IOException {
        // acknowledge已持久化整份文件的长度与摘要；崩溃后可区分正常回收与未确认数据丢失。
        Files.deleteIfExists(dataFile);
        resetAfterReplacement();
    }

    void persist(Checkpoint checkpoint) throws IOException {
        Path temporary = checkpointFile.resolveSibling(checkpointFile.getFileName() + ".tmp");
        byte[] bytes = JSON.toJSONString(checkpoint).getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        // 不支持原子替换时保留数据与旧进度，最多重复交付，不能冒险跳过未确认事件。
        Files.move(temporary, checkpointFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private MessageDigest copyDigest(MessageDigest digest, long throughOffset) throws IOException {
        try { return (MessageDigest) digest.clone(); }
        catch (CloneNotSupportedException unsupported) {
            // 默认JDK SHA-256支持clone；其他provider仍可安全恢复，但需重新核对前缀。
            MessageDigest copy = sha256();
            hashRange(0, throughOffset, copy);
            return copy;
        }
    }

    private String digestHex(MessageDigest digest, long throughOffset) throws IOException {
        return HexFormat.of().formatHex(copyDigest(digest, throughOffset).digest());
    }

    private void hashRange(long from, long to, MessageDigest digest) throws IOException {
        if (from == to) return;
        try (FileChannel channel = FileChannel.open(dataFile, StandardOpenOption.READ)) {
            channel.position(from);
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            long position = from;
            while (position < to) {
                buffer.clear();
                buffer.limit((int) Math.min(buffer.capacity(), to - position));
                int count = channel.read(buffer);
                if (count <= 0) throw new IOException("journal 确认前缀无法完整读取");
                position += count;
                buffer.flip();
                digest.update(buffer);
            }
        }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("JRE不支持SHA-256", error); }
    }

    private static String emptyDigest() { return HexFormat.of().formatHex(sha256().digest()); }

    private static Checkpoint checkpoint(long offset, long size, String prefixHash) {
        return new Checkpoint(1, offset, size, prefixHash, metadataChecksum(1, offset, size, prefixHash));
    }

    private static String metadataChecksum(int version, long offset, long size, String prefixHash) {
        String state = version + "|" + offset + "|" + size + "|" + prefixHash;
        return HexFormat.of().formatHex(sha256().digest(state.getBytes(StandardCharsets.UTF_8)));
    }

    record Checkpoint(Integer version, Long offset, Long observedSize, String prefixSha256, String checksum) { }
}
