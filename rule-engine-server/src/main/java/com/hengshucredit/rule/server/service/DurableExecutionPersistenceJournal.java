package com.hengshucredit.rule.server.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;

/**
 * 数据库和数据库 outbox 同时不可写时的本地写前日志。
 * 每条记录先 fsync，再允许请求链路结束；恢复后由执行持久化线程逐条重放。
 */
@Service
public class DurableExecutionPersistenceJournal implements AutoCloseable {
    public DurableExecutionPersistenceJournal() { }

    /** 为独立业务恢复队列复用已有校验、fsync、目录锁及检查点协议。 */
    DurableExecutionPersistenceJournal(String directory) { this.directory = directory; }
    private static final String DEFAULT_FILE = "execution-persistence.ndjson";
    private static final long READ_BATCH_BYTES = 16 * 1024 * 1024L;
    private static final java.util.Set<Path> OWNED_DIRECTORIES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DurableExecutionPersistenceJournal.class);

    @Value("${rule-engine.execution-persistence.journal-enabled:true}")
    private boolean enabled = true;
    @Value("${rule-engine.execution-persistence.journal-dir:./logs/execution-persistence-journal}")
    private String directory = "./logs/execution-persistence-journal";
    @Value("${rule-engine.execution-persistence.journal-max-entry-bytes:10485760}")
    private long maxEntryBytes = 10 * 1024 * 1024L;
    private Path file;
    private JournalReplayProgress replayProgress;
    private Path ownedDirectory;
    private FileChannel ownershipChannel;
    private FileLock ownershipLock;
    private String status = "NOT_INITIALIZED";
    // 本实例成功 force 的字节边界，不表示历史请求是否已经收到确认。
    private long confirmedBytes;
    private Long failureOffset;
    private String failureReason;

    @PostConstruct
    public synchronized void initialize() {
        if (!"NOT_INITIALIZED".equals(status)) return;
        if (!enabled) {
            status = "DISABLED";
            return;
        }
        try {
            if (maxEntryBytes <= 0) throw new IOException("journal 单条字节上限必须为正");
            Path dir = Path.of(directory).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            dir = dir.toRealPath();
            file = dir.resolve(DEFAULT_FILE);
            // 某些平台关闭同文件的其他 channel 会释放 JVM 所持锁，先在进程内拒绝重复打开。
            if (!OWNED_DIRECTORIES.add(dir)) {
                fail("OWNERSHIP_CONFLICT", null, new IOException("journal 目录已被本进程其他实例占用"));
                return;
            }
            ownedDirectory = dir;
            // 锁文件不会被替换或删除；ndjson 原子替换期间仍保持目录所有权。
            ownershipChannel = FileChannel.open(dir.resolve(DEFAULT_FILE + ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                ownershipLock = ownershipChannel.tryLock();
            } catch (OverlappingFileLockException occupied) {
                fail("OWNERSHIP_CONFLICT", null, occupied);
                releaseOwnership();
                return;
            }
            if (ownershipLock == null) {
                fail("OWNERSHIP_CONFLICT", null, new IOException("journal 目录已被其他进程占用"));
                releaseOwnership();
                return;
            }
            replayProgress = new JournalReplayProgress(file);
            if (Files.exists(file)) {
                try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    long size = channel.size();
                    if (size > 0) {
                        ByteBuffer lastByte = ByteBuffer.allocate(1);
                        if (channel.read(lastByte, size - 1) != 1) throw new IOException("无法读取 journal 尾部");
                        if (lastByte.array()[0] != '\n') {
                            fail("RECOVERY_REQUIRED", size, new IOException("journal 尾部记录未完整结束，原字节已保留"));
                        }
                    }
                    if (!"RECOVERY_REQUIRED".equals(status)) {
                        channel.force(true);
                        confirmedBytes = size;
                    }
                }
            }
            try {
                replayProgress.restore();
            } catch (IOException invalidProgress) {
                fail("RECOVERY_REQUIRED", 0L, invalidProgress);
                return;
            }
            if (!Files.exists(file)) confirmedBytes = 0;
            if ("NOT_INITIALIZED".equals(status)) status = "READY";
        } catch (IOException error) {
            fail("INITIALIZATION_FAILED", null, error);
            releaseOwnership();
        }
    }

    public synchronized boolean append(Entry entry) {
        if (!available() || JournalRecordReader.validate(entry) != null) return false;
        long offset = confirmedBytes;
        try {
            byte[] bytes = JournalRecordReader.encode(entry);
            if (bytes.length > maxEntryBytes || maxEntryBytes <= 0) return false;
            offset = Files.exists(file) ? Files.size(file) : 0;
            writeAndForce(bytes);
            confirmedBytes = offset + bytes.length;
            return true;
        } catch (IOException error) {
            fail("WRITE_FAILED", offset, error);
            return false;
        }
    }

    void writeAndForce(byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    public synchronized List<Entry> read(int limit) {
        JournalRecordReader.Batch batch = readBatch(limit, Math.max(READ_BATCH_BYTES, maxEntryBytes));
        return batch == null ? List.of() : batch.records().stream().map(JournalRecordReader.Record::entry).toList();
    }

    private JournalRecordReader.Batch readBatch(int limit, long byteBudget) {
        if (!readable() || file == null || limit <= 0) return null;
        if (!Files.exists(file)) {
            if (confirmedBytes > 0 && "READY".equals(status)) {
                fail("RECOVERY_REQUIRED", null, new IOException("journal 数据文件意外缺失"));
            }
            return null;
        }
        JournalRecordReader.Batch batch = JournalRecordReader.read(file, replayProgress.offset(), limit, byteBudget, maxEntryBytes);
        if ((batch.outcome() == JournalRecordReader.Outcome.CORRUPT
                || batch.outcome() == JournalRecordReader.Outcome.INCOMPLETE_TAIL
                || batch.outcome() == JournalRecordReader.Outcome.IO_ERROR) && "READY".equals(status)) {
            fail("RECOVERY_REQUIRED", batch.failureOffset(), new IOException(batch.reason()));
        }
        return batch;
    }

    /** 单个恢复消费者按读取顺序确认；空replacement只推进持久进度，不重写未处理尾部。 */
    public synchronized void replacePrefix(int prefixCount, List<Entry> replacement) {
        if (!readable() || file == null || !Files.exists(file) || prefixCount < 0) return;
        List<Entry> safe = replacement == null ? Collections.emptyList() : replacement;
        JournalRecordReader.Batch batch = readBatch(Math.max(1, prefixCount), Math.max(READ_BATCH_BYTES, maxEntryBytes));
        if (batch == null || batch.records().size() != prefixCount) return;
        long nextOffset = prefixCount == 0 ? batch.safeEndOffset() : batch.records().get(prefixCount - 1).endOffset();
        if (safe.isEmpty()) {
            try {
                replayProgress.acknowledge(nextOffset);
                if (!Files.exists(file)) confirmedBytes = 0;
            } catch (IOException error) {
                fail("REPLACEMENT_FAILED", nextOffset, error);
            }
            return;
        }
        if (!available()) return;
        Path temp = file.resolveSibling(file.getFileName() + ".prefix.tmp");
        try {
            try (FileChannel input = FileChannel.open(file, StandardOpenOption.READ);
                 FileChannel output = FileChannel.open(temp, StandardOpenOption.CREATE,
                         StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                for (Entry entry : safe) {
                    writeReplacementEntry(output, entry);
                }
                input.position(nextOffset);
                ByteBuffer bytes = ByteBuffer.allocate(8192);
                while (input.read(bytes) > 0) {
                    bytes.flip();
                    while (bytes.hasRemaining()) output.write(bytes);
                    bytes.clear();
                }
                output.force(true);
            }
            replaceAtomically(temp);
            replayProgress.resetAfterReplacement();
            confirmedBytes = Files.size(file);
        } catch (IOException error) {
            // 不降级非原子覆盖；原文件与已生成的暂存字节都保留供恢复诊断。
            fail("REPLACEMENT_FAILED", null, error);
        } finally {
            if (available()) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
            }
        }
    }

    public synchronized void replace(List<Entry> remaining) {
        if (!available() || file == null) return;
        List<Entry> safe = remaining == null ? Collections.emptyList() : remaining;
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                for (Entry entry : safe) writeReplacementEntry(channel, entry);
                channel.force(true);
            }
            replaceAtomically(temp);
            replayProgress.resetAfterReplacement();
            confirmedBytes = Files.size(file);
        } catch (IOException error) {
            fail("REPLACEMENT_FAILED", null, error);
        }
    }

    public synchronized java.util.Map<String, Object> snapshot() {
        JournalRecordReader.Batch sample = available() ? readBatch(10000, READ_BATCH_BYTES) : null;
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("status", status);
        result.put("available", available());
        result.put("readable", readable());
        result.put("confirmedBytes", confirmedBytes);
        result.put("replayedBytes", replayProgress == null ? null : replayProgress.offset());
        result.put("failureOffset", failureOffset);
        result.put("failureReason", failureReason);
        result.put("path", file == null ? directory : file.toString());
        try { result.put("bytes", file == null || !Files.exists(file) ? 0L : Files.size(file)); }
        catch (IOException ignored) { result.put("bytes", null); }
        result.put("pending", !available() ? null : sample == null ? 0 : sample.records().size());
        result.put("pendingTruncated", !available() ? null : sample != null && sample.outcome() == JournalRecordReader.Outcome.LIMIT);
        // 以下完整性信息只描述本次有界采样，不能据此断言尚未扫描的整份文件已校验。
        Integer verified = !available() ? null : sample == null ? 0
                : (int) sample.records().stream().filter(JournalRecordReader.Record::checksumVerified).count();
        Integer legacy = verified == null ? null : sample == null ? 0 : sample.records().size() - verified;
        boolean truncated = sample != null && sample.outcome() == JournalRecordReader.Outcome.LIMIT;
        String integrity = verified == null || (verified == 0 && legacy == 0 && truncated) ? "UNKNOWN"
                : verified == 0 && legacy == 0 ? "EMPTY"
                : verified == 0 ? "LEGACY_UNVERIFIED" : legacy == 0 ? "VERIFIED" : "MIXED";
        result.put("integritySampleStatus", integrity);
        result.put("sampleVerifiedRecords", verified);
        result.put("sampleLegacyRecords", legacy);
        result.put("integritySampleTruncated", !available() ? null : truncated);
        return result;
    }

    private void writeReplacementEntry(FileChannel channel, Entry entry) throws IOException {
        String invalid = JournalRecordReader.validate(entry);
        if (invalid != null) throw new IOException(invalid);
        byte[] encoded = JournalRecordReader.encode(entry);
        if (maxEntryBytes <= 0 || encoded.length > maxEntryBytes) {
            throw new IOException("journal 替换记录含摘要封装后超过字节上限");
        }
        ByteBuffer bytes = ByteBuffer.wrap(encoded);
        while (bytes.hasRemaining()) channel.write(bytes);
    }

    void replaceAtomically(Path source) throws IOException {
        Files.move(source, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private boolean readable() {
        return enabled && !"CLOSED".equals(status) && ownershipLock != null && ownershipLock.isValid();
    }

    private boolean available() {
        return "READY".equals(status) && readable();
    }

    private void fail(String state, Long offset, Throwable error) {
        status = state;
        failureOffset = offset;
        failureReason = error.getClass().getSimpleName() + ": " + error.getMessage();
        log.error("执行 journal 不可写，状态={}，位置={}，原因={}", state, offset, failureReason);
    }

    private void releaseOwnership() {
        try {
            if (ownershipLock != null && ownershipLock.isValid()) ownershipLock.release();
        } catch (IOException error) {
            log.warn("释放执行 journal 文件锁失败", error);
        } finally {
            ownershipLock = null;
            try {
                if (ownershipChannel != null) ownershipChannel.close();
            } catch (IOException error) {
                log.warn("关闭执行 journal 所有权通道失败", error);
            } finally {
                ownershipChannel = null;
                if (ownedDirectory != null) OWNED_DIRECTORIES.remove(ownedDirectory);
                ownedDirectory = null;
            }
        }
    }

    @PreDestroy
    @Override
    public synchronized void close() {
        status = "CLOSED";
        releaseOwnership();
    }

    public record Entry(String eventId, String logJson, String definitionJson,
                        boolean success, Long costTimeMs, String errorMessage,
                        Long projectId, String projectCode, Long authId,
                        String authCode, String authType, Long tokenId,
                        String tokenCode, String authPhase,
                        boolean logPending, boolean billingPending, String billingSnapshotJson) {
        /** 历史 journal 没有计费快照，保留旧构造入口与读取兼容。 */
        public Entry(String eventId, String logJson, String definitionJson,
                     boolean success, Long costTimeMs, String errorMessage,
                     Long projectId, String projectCode, Long authId,
                     String authCode, String authType, Long tokenId,
                     String tokenCode, String authPhase,
                     boolean logPending, boolean billingPending) {
            this(eventId, logJson, definitionJson, success, costTimeMs, errorMessage,
                    projectId, projectCode, authId, authCode, authType, tokenId,
                    tokenCode, authPhase, logPending, billingPending, null);
        }
    }
}
