package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.HexFormat;

/** 按字节预算读取版本化记录及旧 NDJSON；不修改文件、取得所有权或推进持久化游标。 */
final class JournalRecordReader {
    private static final int CHUNK_BYTES = 8192;

    enum Outcome { END, LIMIT, CORRUPT, INCOMPLETE_TAIL, IO_ERROR }

    record Record(DurableExecutionPersistenceJournal.Entry entry, long endOffset, boolean checksumVerified) { }

    record Batch(List<Record> records, long startOffset, long safeEndOffset, long scannedBytes,
                 Outcome outcome, Long failureOffset, String reason) {
        Batch { records = List.copyOf(records); }
    }

    private JournalRecordReader() { }

    /** 摘要覆盖原 Entry JSON 的 UTF-8 字节；字符串载荷避免对象重序列化造成摘要歧义。 */
    static byte[] encode(DurableExecutionPersistenceJournal.Entry entry) {
        String invalid = validate(entry);
        if (invalid != null) throw new IllegalArgumentException(invalid);
        String payload = JSON.toJSONString(entry);
        JSONObject envelope = new JSONObject(true);
        envelope.put("journalRecordVersion", 1);
        envelope.put("payload", payload);
        envelope.put("payloadSha256", payloadDigest(payload));
        return (JSON.toJSONString(envelope) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * startOffset 应为调用方保存的行边界。scannedBytes 含块预读，但始终不超过 byteBudget；
     * safeEndOffset 只越过已返回的完整有效记录及空白行。maxEntryBytes 包括行尾 LF/CRLF。
     */
    static Batch read(Path file, long startOffset, int entryLimit, long byteBudget, long maxEntryBytes) {
        if (startOffset < 0 || entryLimit <= 0 || byteBudget <= 0 || maxEntryBytes <= 0) {
            throw new IllegalArgumentException("journal 读取偏移必须非负，条数和字节预算必须为正");
        }
        List<Record> records = new ArrayList<>();
        long safeEndOffset = startOffset;
        long scannedBytes = 0;
        long lineStart = startOffset;
        long position = startOffset;
        long lineBytes = 0;
        int chunkSize = (int) Math.min(CHUNK_BYTES, byteBudget);
        ByteBuffer chunk = ByteBuffer.allocate(chunkSize);
        ByteArrayOutputStream line = new ByteArrayOutputStream((int) Math.min(chunkSize, maxEntryBytes));
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            if (startOffset > channel.size()) {
                return new Batch(records, startOffset, safeEndOffset, scannedBytes, Outcome.IO_ERROR,
                        startOffset, "读取偏移超过 journal 文件末尾");
            }
            channel.position(startOffset);
            while (scannedBytes < byteBudget) {
                chunk.clear();
                chunk.limit((int) Math.min(chunkSize, byteBudget - scannedBytes));
                int count = channel.read(chunk);
                if (count < 0) {
                    return new Batch(records, startOffset, safeEndOffset, scannedBytes,
                            lineBytes == 0 ? Outcome.END : Outcome.INCOMPLETE_TAIL,
                            lineBytes == 0 ? null : lineStart, lineBytes == 0 ? null : "末尾记录缺少换行边界");
                }
                if (count == 0) throw new IOException("journal 读取未取得进展");
                scannedBytes += count;
                chunk.flip();
                while (chunk.hasRemaining()) {
                    byte value = chunk.get();
                    position++;
                    if (++lineBytes > maxEntryBytes) {
                        return new Batch(records, startOffset, safeEndOffset, scannedBytes, Outcome.CORRUPT,
                                lineStart, "单条 journal 记录超过字节上限");
                    }
                    if (value != '\n') {
                        line.write(value);
                        continue;
                    }
                    String text;
                    try {
                        text = decoder.decode(ByteBuffer.wrap(line.toByteArray())).toString();
                    } catch (CharacterCodingException invalidUtf8) {
                        return new Batch(records, startOffset, safeEndOffset, scannedBytes, Outcome.CORRUPT,
                                lineStart, "journal 记录不是有效 UTF-8");
                    }
                    if (!text.isBlank()) {
                        DurableExecutionPersistenceJournal.Entry entry;
                        boolean checksumVerified = false;
                        try {
                            JSONObject object = JSON.parseObject(text);
                            if (object != null && (object.containsKey("journalRecordVersion")
                                    || object.containsKey("payload") || object.containsKey("payloadSha256"))) {
                                // 任一新格式字段出现就不能降级为未校验的旧记录。
                                if (!Integer.valueOf(1).equals(object.get("journalRecordVersion"))
                                        || !(object.get("payload") instanceof String payload)
                                        || !(object.get("payloadSha256") instanceof String checksum)
                                        || !checksum.matches("[0-9a-f]{64}")) {
                                    return new Batch(records, startOffset, safeEndOffset, scannedBytes, Outcome.CORRUPT,
                                            lineStart, "journal 记录版本或摘要字段无效");
                                }
                                if (!payloadDigest(payload).equals(checksum)) {
                                    return new Batch(records, startOffset, safeEndOffset, scannedBytes, Outcome.CORRUPT,
                                            lineStart, "journal 记录载荷摘要不一致");
                                }
                                text = payload;
                                checksumVerified = true;
                            }
                            entry = JSON.parseObject(text, DurableExecutionPersistenceJournal.Entry.class);
                        } catch (RuntimeException invalidJson) {
                            return new Batch(records, startOffset, safeEndOffset, scannedBytes, Outcome.CORRUPT,
                                    lineStart, "journal 外层 JSON 无法解析");
                        }
                        String invalid = validate(entry);
                        if (invalid != null) {
                            return new Batch(records, startOffset, safeEndOffset, scannedBytes, Outcome.CORRUPT,
                                    lineStart, invalid);
                        }
                        records.add(new Record(entry, position, checksumVerified));
                    }
                    safeEndOffset = position;
                    lineStart = position;
                    lineBytes = 0;
                    line.reset();
                    if (records.size() >= entryLimit) {
                        return new Batch(records, startOffset, safeEndOffset, scannedBytes, Outcome.LIMIT, null, null);
                    }
                }
            }
            // 不多读一个字节探测 EOF，否则会突破调用方预算；文件长度仅用于确定已到达 EOF。
            boolean atEnd = position >= channel.size();
            Outcome outcome = !atEnd ? Outcome.LIMIT : lineBytes == 0 ? Outcome.END : Outcome.INCOMPLETE_TAIL;
            return new Batch(records, startOffset, safeEndOffset, scannedBytes, outcome,
                    outcome == Outcome.INCOMPLETE_TAIL ? lineStart : null,
                    outcome == Outcome.INCOMPLETE_TAIL ? "末尾记录缺少换行边界" : null);
        } catch (IOException error) {
            return new Batch(records, startOffset, safeEndOffset, scannedBytes, Outcome.IO_ERROR,
                    lineStart, "journal 读取失败: " + error.getClass().getSimpleName());
        }
    }

    private static String payloadDigest(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("JRE不支持SHA-256", unavailable);
        }
    }

    static String validate(DurableExecutionPersistenceJournal.Entry entry) {
        if (entry == null) return "journal 记录不能为 null";
        if (entry.eventId() == null || entry.eventId().isBlank()) return "journal 记录缺少事件身份";
        if (!entry.logPending() && !entry.billingPending()) return "journal 记录没有待恢复内容";
        if (entry.logPending() && (entry.logJson() == null || entry.logJson().isBlank())) return "journal 记录缺少待恢复日志载荷";
        if (entry.billingPending() && (entry.definitionJson() == null || entry.definitionJson().isBlank())) return "journal 记录缺少待恢复计费载荷";
        return null;
    }
}
