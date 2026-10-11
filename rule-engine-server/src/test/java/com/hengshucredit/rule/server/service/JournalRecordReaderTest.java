package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.hengshucredit.rule.server.service.JournalRecordReader.Outcome.*;
import static org.junit.Assert.*;

public class JournalRecordReaderTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void readsUtf8CrLfAndBlankLinesWithPhysicalByteOffsets() throws Exception {
        var first = entry("中文事件", "{\"姓名\":\"甲\"}", true, false);
        String leading = " \r\n\n";
        String firstLine = JSON.toJSONString(first) + "\r\n";
        String ending = "\t\r\n";
        Path file = file(leading + firstLine + ending);

        var batch = read(file, 0, 10, 4096, 4096);

        assertEquals(END, batch.outcome());
        assertEquals(List.of(first), batch.records().stream().map(JournalRecordReader.Record::entry).toList());
        assertEquals(bytes(leading + firstLine), batch.records().get(0).endOffset());
        assertEquals(Files.size(file), batch.safeEndOffset());
        assertEquals(Files.size(file), batch.scannedBytes());
        assertNull(batch.failureOffset());
        assertNull(batch.reason());
        assertThrows(UnsupportedOperationException.class, () -> batch.records().clear());
    }

    @Test
    public void corruptionReturnsTheHealthyPrefixAndNeverChangesTheFile() throws Exception {
        String first = line("first");
        String content = first + "{broken}\n" + line("unread");
        Path file = file(content);
        byte[] original = Files.readAllBytes(file);

        var batch = read(file, 0, 10, 4096, 4096);

        assertEquals(CORRUPT, batch.outcome());
        assertEquals(List.of("first"), ids(batch));
        assertEquals(bytes(first), batch.safeEndOffset());
        assertEquals(Long.valueOf(bytes(first)), batch.failureOffset());
        assertNotNull(batch.reason());
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    public void incompleteTailKeepsHealthyRecordsAndTheIncompleteBytes() throws Exception {
        String first = line("first");
        Path file = file(first + "{\"eventId\":\"half");
        byte[] original = Files.readAllBytes(file);
        var batch = read(file, 0, 10, 4096, 4096);

        assertEquals(INCOMPLETE_TAIL, batch.outcome());
        assertEquals(List.of("first"), ids(batch));
        assertEquals(bytes(first), batch.safeEndOffset());
        assertEquals(Long.valueOf(bytes(first)), batch.failureOffset());
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    public void validJsonAtEofWithoutLfIsStillIncomplete() throws Exception {
        Path file = file(JSON.toJSONString(entry("complete-json", "{}", true, false)));
        var batch = read(file, 0, 10, 4096, 4096);
        assertEquals(INCOMPLETE_TAIL, batch.outcome());
        assertTrue(batch.records().isEmpty());
        assertEquals(0, batch.safeEndOffset());
        assertEquals(Long.valueOf(0), batch.failureOffset());
    }

    @Test
    public void rejectsNullMissingIdentityAndInconsistentPendingSides() throws Exception {
        List<String> invalid = List.of("null", "{}", JSON.toJSONString(entry(" ", "{}", true, false)),
                JSON.toJSONString(entry("nothing-pending", "{}", false, false)),
                JSON.toJSONString(entry("missing-log", " ", true, false)),
                JSON.toJSONString(entry("missing-billing", "{}", false, true)));
        for (String record : invalid) {
            var batch = read(file(record + "\n"), 0, 10, 4096, 4096);
            assertEquals(record, CORRUPT, batch.outcome());
            assertTrue(batch.records().isEmpty());
            assertEquals(0, batch.safeEndOffset());
            assertEquals(Long.valueOf(0), batch.failureOffset());
        }
    }

    @Test
    public void preservesInnerBusinessJsonWithoutParsingOrRewritingIt() throws Exception {
        var event = entry("raw-payload", "intentionally not inner JSON", true, false);
        var batch = read(file(JSON.toJSONString(event) + "\n"), 0, 10, 4096, 4096);
        assertEquals(END, batch.outcome());
        assertEquals(event, batch.records().get(0).entry());
    }

    @Test
    public void lineSizeLimitIncludesItsTerminatingLf() throws Exception {
        String value = line("size-boundary");
        Path file = file(value);
        var accepted = read(file, 0, 10, 4096, bytes(value));
        assertEquals(END, accepted.outcome());
        assertEquals(1, accepted.records().size());
        var rejected = read(file, 0, 10, 4096, bytes(value) - 1);
        assertEquals(CORRUPT, rejected.outcome());
        assertTrue(rejected.records().isEmpty());
        assertEquals(0, rejected.safeEndOffset());
    }

    @Test
    public void oversizedLineCannotCauseUnboundedReadingOrDiscardTheHealthyPrefix() throws Exception {
        String first = line("first");
        Path file = file(first + " ".repeat(100_000) + "\n");
        var batch = read(file, 0, 10, 1024, 256);

        assertEquals(CORRUPT, batch.outcome());
        assertEquals(List.of("first"), ids(batch));
        assertEquals(bytes(first), batch.safeEndOffset());
        assertEquals(Long.valueOf(bytes(first)), batch.failureOffset());
        assertTrue(batch.scannedBytes() <= 1024);
    }

    @Test
    public void byteBudgetWithinALineReturnsLimitAndCanBeRetriedFromTheSafeOffset() throws Exception {
        String first = line("first");
        Path file = file(first + line("second"));
        long budget = bytes(first) + 5;
        var batch = read(file, 0, 10, budget, 4096);

        assertEquals(LIMIT, batch.outcome());
        assertEquals(List.of("first"), ids(batch));
        assertEquals(bytes(first), batch.safeEndOffset());
        assertEquals(budget, batch.scannedBytes());
        assertNull(batch.failureOffset());
        assertNull(batch.reason());
        var remaining = read(file, batch.safeEndOffset(), 10, 4096, 4096);
        assertEquals(END, remaining.outcome());
        assertEquals(List.of("second"), ids(remaining));
        assertEquals(bytes(first), remaining.startOffset());
        assertEquals(Files.size(file), remaining.safeEndOffset());
    }

    @Test
    public void aBudgetSmallerThanTheFirstLineDoesNotTreatItsPrefixAsCorruption() throws Exception {
        Path file = file(line("first"));
        var batch = read(file, 0, 10, 3, 4096);
        assertEquals(LIMIT, batch.outcome());
        assertTrue(batch.records().isEmpty());
        assertEquals(0, batch.safeEndOffset());
        assertEquals(3, batch.scannedBytes());
        assertNull(batch.failureOffset());
    }

    @Test
    public void entryLimitNeverAdvancesOverUnreturnedRecords() throws Exception {
        String first = line("first");
        Path file = file(first + line("second"));
        var batch = read(file, 0, 1, 4096, 4096);
        assertEquals(LIMIT, batch.outcome());
        assertEquals(List.of("first"), ids(batch));
        assertEquals(bytes(first), batch.safeEndOffset());
        assertTrue(batch.scannedBytes() <= 4096);
        assertEquals(List.of("second"), ids(read(file, batch.safeEndOffset(), 10, 4096, 4096)));
    }

    @Test
    public void invalidUtf8IsCorruptInsteadOfSilentlyReplaced() throws Exception {
        String first = line("first");
        Path file = file(first);
        Files.write(file, new byte[]{(byte) 0xC3, 0x28, '\n'}, java.nio.file.StandardOpenOption.APPEND);
        var batch = read(file, 0, 10, 4096, 4096);
        assertEquals(CORRUPT, batch.outcome());
        assertEquals(List.of("first"), ids(batch));
        assertEquals(Long.valueOf(bytes(first)), batch.failureOffset());
    }

    @Test
    public void multibyteCharactersMayCrossChunkBoundaries() throws Exception {
        var event = entry("utf8-chunks", "甲".repeat(6000), true, false);
        Path file = file(JSON.toJSONString(event) + "\n");
        var batch = read(file, 0, 10, 30_000, 30_000);
        assertEquals(END, batch.outcome());
        assertEquals(List.of(event), batch.records().stream().map(JournalRecordReader.Record::entry).toList());
        assertEquals(Files.size(file), batch.scannedBytes());
    }

    @Test
    public void emptyFilesAndOffsetsAtEofHaveNoPendingBytes() throws Exception {
        Path empty = file("");
        assertEquals(END, read(empty, 0, 10, 10, 100).outcome());
        Path populated = file(line("first"));
        var batch = read(populated, Files.size(populated), 10, 10, 100);
        assertEquals(END, batch.outcome());
        assertEquals(Files.size(populated), batch.safeEndOffset());
        assertEquals(0, batch.scannedBytes());
    }

    @Test
    public void missingFilesAndOffsetsPastEofAreExplicitIoErrors() throws Exception {
        var missing = read(temporary.getRoot().toPath().resolve("missing"), 0, 10, 100, 100);
        assertEquals(IO_ERROR, missing.outcome());
        assertEquals(0, missing.scannedBytes());
        Path file = file(line("first"));
        var past = read(file, Files.size(file) + 1, 10, 100, 100);
        assertEquals(IO_ERROR, past.outcome());
        assertEquals(0, past.scannedBytes());
        assertEquals(Long.valueOf(Files.size(file) + 1), past.failureOffset());
    }

    @Test
    public void invalidBudgetsAreRejectedBeforeIo() throws Exception {
        Path file = file("");
        assertThrows(IllegalArgumentException.class, () -> read(file, -1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> read(file, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> read(file, 0, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> read(file, 0, 1, 1, 0));
    }

    private JournalRecordReader.Batch read(Path file, long offset, int entries, long budget, long maxLine) {
        return JournalRecordReader.read(file, offset, entries, budget, maxLine);
    }

    private Path file(String contents) throws Exception {
        Path file = temporary.newFile().toPath();
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        return file;
    }

    private static List<String> ids(JournalRecordReader.Batch batch) {
        return batch.records().stream().map(record -> record.entry().eventId()).toList();
    }

    private static long bytes(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }

    private static String line(String id) { return JSON.toJSONString(entry(id, "{}", true, false)) + "\n"; }

    private static DurableExecutionPersistenceJournal.Entry entry(String id, String logJson, boolean log, boolean billing) {
        return new DurableExecutionPersistenceJournal.Entry(id, logJson, null, true, 1L, null,
                null, null, null, null, null, null, null, null, log, billing);
    }
}
