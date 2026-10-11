package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.Assert.*;

public class JournalReplayProgressTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void forcedTemporaryCheckpointFailurePreservesOldProgressAndAllDataBytes() throws Exception {
        Path data = data("first", "second", "third");
        byte[] original = Files.readAllBytes(data);
        class FailedMove extends JournalReplayProgress {
            private boolean failMove;
            FailedMove() { super(data); }
            @Override void persist(Checkpoint checkpoint) throws IOException {
                if (failMove) {
                    writeForced(temporaryCursor(data), JSON.toJSONBytes(checkpoint));
                    throw new IOException("injected failure after checkpoint force before move");
                }
                super.persist(checkpoint);
            }
        }
        FailedMove progress = new FailedMove();
        progress.acknowledge(end("first"));
        byte[] confirmedCursor = Files.readAllBytes(cursor(data));
        progress.failMove = true;

        assertThrows(IOException.class, () -> progress.acknowledge(end("first", "second")));

        assertEquals("正式游标未提交时内存位置不得前进", end("first"), progress.offset());
        assertArrayEquals(original, Files.readAllBytes(data));
        assertArrayEquals(confirmedCursor, Files.readAllBytes(cursor(data)));
        assertTrue("保留已force但未提交的暂存游标", Files.size(temporaryCursor(data)) > 0);
        JournalReplayProgress restarted = restored(data);
        assertEquals(end("first"), restarted.offset());
        assertEquals(List.of("second", "third"), pending(data, restarted));
        assertArrayEquals(original, Files.readAllBytes(data));
    }

    @Test
    public void completeCheckpointCommittedBeforeDataDeletionIsSafelyFinishedAfterRestart() throws Exception {
        Path data = data("first", "second");
        byte[] original = Files.readAllBytes(data);
        JournalReplayProgress crashed = failAfterCheckpointCommit(data);

        assertThrows(IOException.class, () -> crashed.acknowledge(original.length));

        assertEquals("未返回的确认不能提前更新内存游标", 0, crashed.offset());
        assertArrayEquals(original, Files.readAllBytes(data));
        assertEquals(original.length, JSON.parseObject(Files.readString(cursor(data))).getLongValue("offset"));
        JournalReplayProgress restarted = restored(data);
        assertEquals(0, restarted.offset());
        assertFalse("已交付整份文件应完成回收", Files.exists(data));
        assertEquals(0, JSON.parseObject(Files.readString(cursor(data))).getLongValue("offset"));
    }

    @Test
    public void fullCheckpointCannotDeleteAnAdditionalUnconfirmedTail() throws Exception {
        Path data = data("first");
        long firstEnd = Files.size(data);
        JournalReplayProgress crashed = failAfterCheckpointCommit(data);
        assertThrows(IOException.class, () -> crashed.acknowledge(firstEnd));
        Files.writeString(data, line("unconfirmed-tail"), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        byte[] withTail = Files.readAllBytes(data);

        JournalReplayProgress restarted = restored(data);

        assertEquals(firstEnd, restarted.offset());
        assertArrayEquals("旧完整游标只能确认旧长度，不能删掉后来追加的尾部", withTail, Files.readAllBytes(data));
        assertEquals(List.of("unconfirmed-tail"), pending(data, restarted));
    }

    @Test
    public void deletedCompleteDataAndFailedZeroCheckpointRecoverWithoutSkippingTheNextCycle() throws Exception {
        Path data = data("first", "second");
        long completeLength = Files.size(data);
        JournalReplayProgress progress = new JournalReplayProgress(data) {
            @Override void persist(Checkpoint checkpoint) throws IOException {
                if (checkpoint.offset() == 0) throw new IOException("injected zero checkpoint failure");
                super.persist(checkpoint);
            }
        };

        assertThrows(IOException.class, () -> progress.acknowledge(completeLength));

        assertFalse("只有已确认的整份数据才可回收", Files.exists(data));
        assertEquals("清零失败仍应保留已提交的完整游标", completeLength,
                JSON.parseObject(Files.readString(cursor(data))).getLongValue("offset"));
        JournalReplayProgress restarted = restored(data);
        assertEquals(0, restarted.offset());
        assertFalse(Files.exists(data));
        Files.writeString(data, line("next-cycle"), StandardCharsets.UTF_8);
        JournalReplayProgress nextCycle = restored(data);
        assertEquals(0, nextCycle.offset());
        assertEquals(List.of("next-cycle"), pending(data, nextCycle));
        nextCycle.acknowledge(Files.size(data));
        assertFalse(Files.exists(data));
        assertEquals(0, nextCycle.offset());
    }

    @Test
    public void missingDataWithOnlyAPartialCheckpointIsNeverTreatedAsCompletedCleanup() throws Exception {
        Path data = data("first", "still-pending");
        JournalReplayProgress progress = new JournalReplayProgress(data);
        progress.acknowledge(end("first"));
        byte[] saved = Files.readAllBytes(cursor(data));
        Files.delete(data);
        JournalReplayProgress restarted = new JournalReplayProgress(data);

        assertThrows(IOException.class, restarted::restore);

        assertEquals(0, restarted.offset());
        assertArrayEquals("不能把缺失的待处理数据伪装成已完成并清零游标", saved, Files.readAllBytes(cursor(data)));
        assertFalse(Files.exists(data));
    }

    @Test
    public void structurallyValidMetadataChangesAreRejectedWithoutSkippingData() throws Exception {
        Path data = data("first", "second", "third");
        byte[] original = Files.readAllBytes(data);
        new JournalReplayProgress(data).acknowledge(end("first"));
        JSONObject saved = JSON.parseObject(Files.readString(cursor(data)));
        // 仍满足 offset <= observedSize <= 文件长度，单靠字段范围与前缀摘要识别不了此损坏。
        saved.put("observedSize", saved.getLongValue("observedSize") - 1);
        byte[] changed = JSON.toJSONBytes(saved);
        Files.write(cursor(data), changed);
        JournalReplayProgress restarted = new JournalReplayProgress(data);

        assertThrows(IOException.class, restarted::restore);

        assertEquals(0, restarted.offset());
        assertArrayEquals(original, Files.readAllBytes(data));
        assertArrayEquals(changed, Files.readAllBytes(cursor(data)));
    }

    @Test
    public void corruptedEmptyPrefixDigestCannotMasqueradeAsNormalCleanupWhenDataIsAbsent() throws Exception {
        Path data = data("complete");
        new JournalReplayProgress(data).acknowledge(Files.size(data));
        assertFalse(Files.exists(data));
        JSONObject saved = JSON.parseObject(Files.readString(cursor(data)));
        saved.put("prefixSha256", "0".repeat(64));
        byte[] changed = JSON.toJSONBytes(saved);
        Files.write(cursor(data), changed);
        JournalReplayProgress restarted = new JournalReplayProgress(data);

        assertThrows(IOException.class, restarted::restore);

        assertEquals(0, restarted.offset());
        assertArrayEquals("损坏的游标应保留供诊断，不能悄悄改写为健康空状态", changed, Files.readAllBytes(cursor(data)));
        assertFalse(Files.exists(data));
    }

    @Test
    public void oversizedMalformedOrUnsignedCheckpointPreservesSourceAndStartsFromZero() throws Exception {
        Path data = data("first", "second");
        byte[] original = Files.readAllBytes(data);
        new JournalReplayProgress(data).acknowledge(end("first"));
        byte[] valid = Files.readAllBytes(cursor(data));
        JSONObject unsigned = JSON.parseObject(new String(valid, StandardCharsets.UTF_8));
        unsigned.keySet().retainAll(List.of("version", "offset", "observedSize", "prefixSha256"));
        List<byte[]> corrupted = List.of("x".repeat(4097).getBytes(StandardCharsets.UTF_8),
                "{broken".getBytes(StandardCharsets.UTF_8), JSON.toJSONBytes(unsigned));
        for (byte[] bytes : corrupted) {
            Files.write(cursor(data), bytes);
            JournalReplayProgress restarted = new JournalReplayProgress(data);
            assertThrows(IOException.class, restarted::restore);
            assertEquals(0, restarted.offset());
            assertArrayEquals(original, Files.readAllBytes(data));
            assertArrayEquals(bytes, Files.readAllBytes(cursor(data)));
        }
    }

    @Test
    public void changedOrTruncatedDataCannotReuseAnOlderPrefixCheckpoint() throws Exception {
        Path data = data("first", "second");
        byte[] original = Files.readAllBytes(data);
        new JournalReplayProgress(data).acknowledge(end("first"));
        byte[] saved = Files.readAllBytes(cursor(data));
        byte[] changed = original.clone();
        changed[0] ^= 1;
        for (byte[] bytes : List.of(changed, java.util.Arrays.copyOf(original, original.length - 1))) {
            Files.write(data, bytes);
            JournalReplayProgress restarted = new JournalReplayProgress(data);
            assertThrows(IOException.class, restarted::restore);
            assertEquals(0, restarted.offset());
            assertArrayEquals(bytes, Files.readAllBytes(data));
            assertArrayEquals(saved, Files.readAllBytes(cursor(data)));
        }
    }

    @Test
    public void appendBetweenAcknowledgementsPreservesAllUnconfirmedEventsAcrossRestart() throws Exception {
        Path data = data("first", "second");
        JournalReplayProgress progress = new JournalReplayProgress(data);
        progress.acknowledge(end("first"));
        Files.writeString(data, line("third"), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        byte[] original = Files.readAllBytes(data);
        progress.acknowledge(end("first", "second"));

        assertEquals(end("first", "second"), progress.offset());
        assertArrayEquals(original, Files.readAllBytes(data));
        JournalReplayProgress restarted = restored(data);
        assertEquals(end("first", "second"), restarted.offset());
        assertEquals(List.of("third"), pending(data, restarted));
        assertArrayEquals(original, Files.readAllBytes(data));
    }

    @Test
    public void acknowledgementPastFileEndCannotAdvanceOrReplaceTheLastCheckpoint() throws Exception {
        Path data = data("first", "second");
        byte[] original = Files.readAllBytes(data);
        JournalReplayProgress progress = new JournalReplayProgress(data);
        progress.acknowledge(end("first"));
        byte[] saved = Files.readAllBytes(cursor(data));

        assertThrows(IOException.class, () -> progress.acknowledge(original.length + 1L));

        assertEquals(end("first"), progress.offset());
        assertArrayEquals(original, Files.readAllBytes(data));
        assertArrayEquals(saved, Files.readAllBytes(cursor(data)));
    }

    private JournalReplayProgress failAfterCheckpointCommit(Path data) {
        return new JournalReplayProgress(data) {
            @Override void persist(Checkpoint checkpoint) throws IOException {
                super.persist(checkpoint);
                if (checkpoint.offset() > 0) throw new IOException("injected crash after checkpoint commit before cleanup");
            }
        };
    }

    private static JournalReplayProgress restored(Path data) throws IOException {
        JournalReplayProgress progress = new JournalReplayProgress(data);
        progress.restore();
        return progress;
    }

    private static List<String> pending(Path data, JournalReplayProgress progress) {
        var batch = JournalRecordReader.read(data, progress.offset(), 100, 1024 * 1024, 1024 * 1024);
        assertEquals(JournalRecordReader.Outcome.END, batch.outcome());
        return batch.records().stream().map(record -> record.entry().eventId()).toList();
    }

    private Path data(String... events) throws IOException {
        Path file = temporary.newFolder().toPath().resolve("execution-persistence.ndjson");
        StringBuilder contents = new StringBuilder();
        for (String event : events) contents.append(line(event));
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        return file;
    }

    private static long end(String... events) {
        long length = 0;
        for (String event : events) length += line(event).getBytes(StandardCharsets.UTF_8).length;
        return length;
    }

    private static String line(String event) {
        return JSON.toJSONString(new DurableExecutionPersistenceJournal.Entry(event, "{}", null, true, 1L, null,
                null, null, null, null, null, null, null, null, true, false)) + "\n";
    }

    private static Path cursor(Path data) { return data.resolveSibling(data.getFileName() + ".cursor"); }
    private static Path temporaryCursor(Path data) { return data.resolveSibling(data.getFileName() + ".cursor.tmp"); }

    private static void writeForced(Path file, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }
}
