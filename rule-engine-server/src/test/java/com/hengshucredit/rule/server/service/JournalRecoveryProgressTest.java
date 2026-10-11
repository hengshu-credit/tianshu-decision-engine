package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class JournalRecoveryProgressTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void malformedRecordDoesNotHideTheHealthyPrefixOrReportAnEmptyQueue() throws Exception {
        Path directory = temporary.newFolder().toPath();
        Path file = journalFile(directory);
        var first = entry("before-corruption");
        Files.writeString(file, line(first) + "not-json\n" + line(entry("after-corruption")), StandardCharsets.UTF_8);
        byte[] original = Files.readAllBytes(file);
        try (var journal = open(directory)) {
            assertEquals(List.of(first), journal.read(100));
            var snapshot = journal.snapshot();
            assertEquals("RECOVERY_REQUIRED", snapshot.get("status"));
            assertNull("损坏之后的总数未知，不能伪报0", snapshot.get("pending"));
            assertEquals(false, snapshot.get("available"));
            assertArrayEquals(original, Files.readAllBytes(file));
        }
    }

    @Test
    public void validJsonWithoutFinalNewlineIsNotACompleteRecoverableRecord() throws Exception {
        Path directory = temporary.newFolder().toPath();
        var first = entry("complete");
        Files.writeString(journalFile(directory), line(first) + JSON.toJSONString(entry("unfinished")), StandardCharsets.UTF_8);
        try (var journal = open(directory)) {
            assertEquals(List.of(first), journal.read(100));
            assertEquals("RECOVERY_REQUIRED", journal.snapshot().get("status"));
            assertFalse(journal.append(entry("must-not-follow-a-torn-tail")));
        }
    }

    @Test
    public void snapshotLimitsBytesEvenWhenEntryCountHasNotReachedItsLimit() throws Exception {
        Path directory = temporary.newFolder().toPath();
        var large = new DurableExecutionPersistenceJournal.Entry("large", "{\"inputParams\":\"" + "x".repeat(16384) + "\"}", null,
                true, 1L, null, null, null, null, null, null, null, null, null, true, false);
        try (var writer = Files.newBufferedWriter(journalFile(directory), StandardCharsets.UTF_8)) {
            for (int i = 0; i < 1500; i++) writer.write(line(large));
        }
        try (var journal = open(directory)) {
            var snapshot = journal.snapshot();
            assertEquals("READY", snapshot.get("status"));
            int sampled = ((Number) snapshot.get("pending")).intValue();
            assertTrue("快照应读出受控的有效前缀", sampled > 0);
            assertTrue("不能为了不足10000条而读取整个大文件", sampled < 1500);
            assertEquals(true, snapshot.get("pendingTruncated"));
        }
    }

    @Test
    public void confirmedPrefixAdvancesAcrossRestartWithoutRewritingPendingFile() throws Exception {
        Path directory = temporary.newFolder().toPath();
        var first = entry("first");
        var second = entry("second");
        var third = entry("third");
        byte[] original;
        try (var journal = open(directory)) {
            assertTrue(journal.append(first));
            assertTrue(journal.append(second));
            assertTrue(journal.append(third));
            original = Files.readAllBytes(journalFile(directory));
            assertEquals(List.of(first), journal.read(1));
            journal.replacePrefix(1, List.of());
            assertArrayEquals("确认前缀不能反复重写整个待恢复文件", original, Files.readAllBytes(journalFile(directory)));
            assertEquals(List.of(second, third), journal.read(10));
        }
        try (var restarted = open(directory)) {
            assertEquals(List.of(second, third), restarted.read(10));
            restarted.replacePrefix(2, List.of());
            assertTrue(restarted.read(10).isEmpty());
            assertEquals(0L, restarted.snapshot().get("bytes"));
            assertTrue(restarted.append(entry("next-cycle")));
        }
        try (var restarted = open(directory)) {
            assertEquals(List.of("next-cycle"), restarted.read(10).stream().map(DurableExecutionPersistenceJournal.Entry::eventId).toList());
        }
    }

    @Test
    public void healthyPrefixBeforeDamageCanBeAcknowledgedWithoutRemovingDamagedBytes() throws Exception {
        Path directory = temporary.newFolder().toPath();
        var first = entry("durable-prefix");
        Path file = journalFile(directory);
        Files.writeString(file, line(first) + "broken-tail", StandardCharsets.UTF_8);
        byte[] original = Files.readAllBytes(file);
        try (var journal = open(directory)) {
            assertEquals(List.of(first), journal.read(100));
            journal.replacePrefix(1, List.of());
            assertTrue("已确认前缀不应在每轮都重新交付", journal.read(100).isEmpty());
            assertArrayEquals("损坏字节必须保留用于恢复", original, Files.readAllBytes(file));
        }
        try (var restarted = open(directory)) {
            assertTrue(restarted.read(100).isEmpty());
            assertEquals("RECOVERY_REQUIRED", restarted.snapshot().get("status"));
        }
    }

    @Test
    public void changedCheckpointSourceIsDiagnosedAndNeverSkipped() throws Exception {
        Path directory = temporary.newFolder().toPath();
        var first = entry("first");
        var second = entry("second");
        try (var journal = open(directory)) {
            journal.append(first);
            journal.append(second);
            journal.replacePrefix(1, List.of());
        }
        var changed = entry("other");
        Files.writeString(journalFile(directory), line(changed) + line(second), StandardCharsets.UTF_8);
        try (var restarted = open(directory)) {
            assertEquals(List.of(changed, second), restarted.read(100));
            assertEquals("RECOVERY_REQUIRED", restarted.snapshot().get("status"));
            assertFalse(restarted.append(entry("not-before-investigation")));
        }
    }

    @Test
    public void unavailableOutboxDoesNotRewriteAnUnchangedJournal() throws Exception {
        Path directory = temporary.newFolder().toPath();
        AtomicInteger attempts = new AtomicInteger();
        RuleExecutionPersistenceOutboxService outbox = new RuleExecutionPersistenceOutboxService() {
            @Override public com.hengshucredit.rule.model.entity.RuleExecutionPersistenceOutbox accept(DurableExecutionPersistenceJournal.Entry entry) {
                attempts.incrementAndGet();
                throw new IllegalStateException("controlled unavailable outbox");
            }
        };
        try (var journal = open(directory);
             var persistence = new DecisionExecutionPersistence(new RuleExecutionLogService(), new RuleBillingService(), 100, outbox)) {
            journal.append(entry("first"));
            journal.append(entry("second"));
            FileTime unchanged = FileTime.fromMillis(1000);
            Files.setLastModifiedTime(journalFile(directory), unchanged);
            ReflectionTestUtils.setField(persistence, "journal", journal);
            persistence.replayJournal();
            assertEquals(1, attempts.get());
            assertEquals("没有交付成功时不应改写整份日志", unchanged, Files.getLastModifiedTime(journalFile(directory)));
            assertEquals(2, journal.read(100).size());
        }
    }

    @Test
    public void rewrittenPendingSidesAreRecoveredFromZeroWhenTheOldCursorSurvivesACrash() throws Exception {
        Path directory = temporary.newFolder().toPath();
        Path data = journalFile(directory);
        Path cursor = data.resolveSibling(data.getFileName() + ".cursor");
        var second = new DurableExecutionPersistenceJournal.Entry("second", "{}", "{}", true, 1L,
                null, null, null, null, null, null, null, null, null, true, true);
        var retry = new DurableExecutionPersistenceJournal.Entry("second", "{}", "{}", true, 1L,
                null, null, null, null, null, null, null, null, null, false, true);
        byte[] rewritten;
        try (var journal = open(directory)) {
            journal.append(entry("first"));
            journal.append(second);
            journal.append(entry("third"));
            journal.replacePrefix(1, List.of());
            byte[] oldCursor = Files.readAllBytes(cursor);
            journal.replacePrefix(1, List.of(retry));
            assertEquals(List.of(retry, entry("third")), journal.read(100));
            rewritten = Files.readAllBytes(data);
            // 模拟数据原子替换完成，而清零后的cursor还未持久化的崩溃中间状态。
            Files.write(cursor, oldCursor);
        }
        try (var restarted = open(directory)) {
            assertEquals("RECOVERY_REQUIRED", restarted.snapshot().get("status"));
            assertEquals(List.of(retry, entry("third")), restarted.read(100));
            assertArrayEquals(rewritten, Files.readAllBytes(data));
            assertFalse(restarted.append(entry("not-before-recovery")));
        }
    }

    @Test
    public void appendCannotConfirmEntriesWhichItsOwnReaderWouldReject() throws Exception {
        Path directory = temporary.newFolder().toPath();
        var missingIdentity = new DurableExecutionPersistenceJournal.Entry(null, "{}", null, true, 1L,
                null, null, null, null, null, null, null, null, null, true, false);
        var noPendingSide = new DurableExecutionPersistenceJournal.Entry("nothing", "{}", null, true, 1L,
                null, null, null, null, null, null, null, null, null, false, false);
        try (var journal = open(directory)) {
            assertFalse(journal.append(missingIdentity));
            assertFalse(journal.append(noPendingSide));
            assertEquals("READY", journal.snapshot().get("status"));
            assertTrue(journal.append(entry("valid")));
            assertEquals(List.of(entry("valid")), journal.read(100));
        }
    }

    private static DurableExecutionPersistenceJournal open(Path directory) {
        var journal = new DurableExecutionPersistenceJournal();
        ReflectionTestUtils.setField(journal, "directory", directory.toString());
        journal.initialize();
        return journal;
    }

    private static Path journalFile(Path directory) { return directory.resolve("execution-persistence.ndjson"); }
    private static String line(DurableExecutionPersistenceJournal.Entry entry) { return JSON.toJSONString(entry) + System.lineSeparator(); }
    private static DurableExecutionPersistenceJournal.Entry entry(String id) {
        return new DurableExecutionPersistenceJournal.Entry(id, "{}", null, true, 1L,
                null, null, null, null, null, null, null, null, null, true, false);
    }
}
