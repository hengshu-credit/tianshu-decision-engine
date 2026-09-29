package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 数据库和数据库 outbox 同时不可写时的本地写前日志。
 * 每条记录先 fsync，再允许请求链路结束；恢复后由执行持久化线程逐条重放。
 */
@Service
public class DurableExecutionPersistenceJournal {
    private static final String DEFAULT_FILE = "execution-persistence.ndjson";

    @Value("${rule-engine.execution-persistence.journal-enabled:true}")
    private boolean enabled = true;
    @Value("${rule-engine.execution-persistence.journal-dir:./logs/execution-persistence-journal}")
    private String directory = "./logs/execution-persistence-journal";
    @Value("${rule-engine.execution-persistence.journal-max-entry-bytes:10485760}")
    private long maxEntryBytes = 10 * 1024 * 1024L;
    private Path file;

    @PostConstruct
    public void initialize() {
        if (!enabled) return;
        try {
            Path dir = Path.of(directory).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            file = dir.resolve(DEFAULT_FILE);
        } catch (IOException error) {
            enabled = false;
        }
    }

    public synchronized boolean append(Entry entry) {
        if (!enabled || file == null || entry == null) return false;
        try {
            byte[] bytes = (JSON.toJSONString(entry) + System.lineSeparator())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (bytes.length > maxEntryBytes || maxEntryBytes <= 0) return false;
            Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    public synchronized List<Entry> read(int limit) {
        if (!enabled || file == null || !Files.exists(file) || limit <= 0) return List.of();
        try {
            List<Entry> result = new ArrayList<>();
            for (String line : Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
                if (line == null || line.isBlank()) continue;
                try {
                    result.add(JSON.parseObject(line, Entry.class));
                    if (result.size() >= limit) break;
                } catch (RuntimeException ignored) {
                    // 损坏行不自动删除；返回空集合让调用方保留原文件等待人工恢复。
                    return List.of();
                }
            }
            return result;
        } catch (IOException error) {
            return List.of();
        }
    }

    public synchronized void replace(List<Entry> remaining) {
        if (!enabled || file == null) return;
        List<Entry> safe = remaining == null ? Collections.emptyList() : remaining;
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            List<String> lines = new ArrayList<>(safe.size());
            for (Entry entry : safe) lines.add(JSON.toJSONString(entry));
            Files.write(temp, lines, java.nio.charset.StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // 旧文件仍保留，下一轮继续重放。
        }
    }

    public synchronized java.util.Map<String, Object> snapshot() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("path", file == null ? directory : file.toString());
        try { result.put("bytes", file == null || !Files.exists(file) ? 0L : Files.size(file)); }
        catch (IOException ignored) { result.put("bytes", null); }
        result.put("pending", read(10000).size());
        return result;
    }

    public record Entry(String eventId, String logJson, String definitionJson,
                        boolean success, Long costTimeMs, String errorMessage,
                        Long projectId, String projectCode, Long authId,
                        String authCode, String authType, Long tokenId,
                        String tokenCode, String authPhase,
                        boolean logPending, boolean billingPending) { }
}
