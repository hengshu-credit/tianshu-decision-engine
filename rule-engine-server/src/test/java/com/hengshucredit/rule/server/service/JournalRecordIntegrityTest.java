package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class JournalRecordIntegrityTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void aPendingRecordChangedToAnotherValidJsonIsRejectedAndKeepsTheHealthyPrefix() throws Exception {
        Path directory = temporary.newFolder().toPath();
        try (var journal = open(directory)) {
            assertTrue(journal.append(entry("healthy")));
            assertTrue(journal.append(entry("victim")));
            assertTrue(journal.append(entry("after-damage")));
        }
        Path file = file(directory);
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        JSONObject wire = JSON.parseObject(lines.get(1));
        if (wire.containsKey("payload")) {
            JSONObject payload = JSON.parseObject(wire.getString("payload"));
            payload.put("logJson", "{\"score\":99}");
            wire.put("payload", JSON.toJSONString(payload));
        } else {
            // 同一行为回归也作用于旧裸Entry实现：语法仍合法，不能靠JSON解析失败侥幸拦截。
            wire.put("logJson", "{\"score\":99}");
        }
        lines.set(1, JSON.toJSONString(wire));
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        byte[] corrupted = Files.readAllBytes(file);

        try (var restarted = open(directory)) {
            assertEquals("不能把内容已改变的未确认事件交付给outbox", List.of(entry("healthy")), restarted.read(100));
            assertEquals("RECOVERY_REQUIRED", restarted.snapshot().get("status"));
            assertNull(restarted.snapshot().get("pending"));
            assertArrayEquals(corrupted, Files.readAllBytes(file));
            restarted.replacePrefix(1, List.of());
            assertTrue(restarted.read(100).isEmpty());
            assertArrayEquals("确认健康前缀不能删除损坏记录", corrupted, Files.readAllBytes(file));
        }
    }

    @Test
    public void newAppendsDeclareTheirFormatAndPreserveTheOriginalPayload() throws Exception {
        Path directory = temporary.newFolder().toPath();
        try (var journal = open(directory)) {
            assertTrue(journal.append(entry("checksummed")));
            JSONObject wire = JSON.parseObject(Files.readString(file(directory)));
            assertEquals(Integer.valueOf(1), wire.getInteger("journalRecordVersion"));
            assertNotNull(wire.getString("payload"));
            assertTrue(wire.getString("payloadSha256").matches("[0-9a-f]{64}"));
            assertEquals(entry("checksummed"), JSON.parseObject(wire.getString("payload"), DurableExecutionPersistenceJournal.Entry.class));
            assertEquals(List.of(entry("checksummed")), journal.read(100));
            assertEquals("VERIFIED", journal.snapshot().get("integritySampleStatus"));
            assertEquals(1, journal.snapshot().get("sampleVerifiedRecords"));
            assertEquals(0, journal.snapshot().get("sampleLegacyRecords"));
        }
    }

    @Test
    public void legacyRecordsRemainReadableUnmodifiedAndExplicitlyUnverified() throws Exception {
        Path directory = temporary.newFolder().toPath();
        byte[] legacy = (JSON.toJSONString(entry("legacy")) + "\r\n").getBytes(StandardCharsets.UTF_8);
        Files.write(file(directory), legacy);
        try (var journal = open(directory)) {
            assertEquals(List.of(entry("legacy")), journal.read(100));
            assertEquals("LEGACY_UNVERIFIED", journal.snapshot().get("integritySampleStatus"));
            assertEquals(0, journal.snapshot().get("sampleVerifiedRecords"));
            assertEquals(1, journal.snapshot().get("sampleLegacyRecords"));
            assertArrayEquals("读取旧文件不能自动重写历史记录", legacy, Files.readAllBytes(file(directory)));
            assertTrue(journal.append(entry("new")));
            byte[] mixed = Files.readAllBytes(file(directory));
            assertArrayEquals(legacy, java.util.Arrays.copyOf(mixed, legacy.length));
            assertEquals("MIXED", journal.snapshot().get("integritySampleStatus"));
            assertEquals(1, journal.snapshot().get("sampleVerifiedRecords"));
            assertEquals(1, journal.snapshot().get("sampleLegacyRecords"));
            journal.replacePrefix(1, List.of());
            assertArrayEquals("游标确认不迁移或重写混合格式文件", mixed, Files.readAllBytes(file(directory)));
        }
        try (var restarted = open(directory)) {
            assertEquals(List.of(entry("new")), restarted.read(100));
            assertEquals("VERIFIED", restarted.snapshot().get("integritySampleStatus"));
        }
    }

    @Test
    public void malformedEnvelopeMetadataCannotFallBackToLegacyParsing() throws Exception {
        Path templateDirectory = temporary.newFolder().toPath();
        JSONObject template;
        try (var journal = open(templateDirectory)) {
            journal.append(entry("new"));
            template = JSON.parseObject(Files.readString(file(templateDirectory)));
        }
        List<JSONObject> malformed = new ArrayList<>();
        for (String key : List.of("journalRecordVersion", "payload", "payloadSha256")) {
            JSONObject variant = new JSONObject(new java.util.LinkedHashMap<>(template));
            variant.remove(key);
            // 合法旧字段不能掩盖剩余的新格式标志。
            variant.putAll(JSON.parseObject(JSON.toJSONString(entry("fake-legacy"))));
            malformed.add(variant);
        }
        JSONObject future = new JSONObject(new java.util.LinkedHashMap<>(template));
        future.put("journalRecordVersion", 2);
        malformed.add(future);
        JSONObject checksum = new JSONObject(new java.util.LinkedHashMap<>(template));
        checksum.put("payloadSha256", "0".repeat(64));
        malformed.add(checksum);
        for (JSONObject record : malformed) {
            Path directory = temporary.newFolder().toPath();
            Files.writeString(file(directory), JSON.toJSONString(record) + "\n", StandardCharsets.UTF_8);
            byte[] original = Files.readAllBytes(file(directory));
            try (var journal = open(directory)) {
                assertTrue(journal.read(100).isEmpty());
                assertEquals("RECOVERY_REQUIRED", journal.snapshot().get("status"));
                assertArrayEquals(original, Files.readAllBytes(file(directory)));
            }
        }
    }

    @Test
    public void wireLengthIncludingEnvelopeAndLfIsTheSameForAdmissionAndReading() throws Exception {
        var entry = entry("wire-boundary");
        Path probe = temporary.newFolder().toPath();
        long wireLength;
        try (var journal = open(probe)) {
            assertTrue(journal.append(entry));
            wireLength = Files.size(file(probe));
        }
        Path exact = temporary.newFolder().toPath();
        try (var journal = open(exact)) {
            ReflectionTestUtils.setField(journal, "maxEntryBytes", wireLength);
            assertTrue(journal.append(entry));
            assertEquals(List.of(entry), journal.read(100));
            assertEquals("READY", journal.snapshot().get("status"));
            assertEquals(wireLength, Files.size(file(exact)));
        }
        Path shortByOne = temporary.newFolder().toPath();
        try (var journal = open(shortByOne)) {
            ReflectionTestUtils.setField(journal, "maxEntryBytes", wireLength - 1);
            assertFalse(journal.append(entry));
            assertFalse(Files.exists(file(shortByOne)));
            assertEquals("READY", journal.snapshot().get("status"));
        }
    }

    @Test
    public void fittingTheBarePayloadDoesNotBypassTheActualWireLimit() throws Exception {
        Path directory = temporary.newFolder().toPath();
        var entry = entry("payload-only-budget");
        long oldLength = (JSON.toJSONString(entry) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8).length;
        try (var journal = open(directory)) {
            ReflectionTestUtils.setField(journal, "maxEntryBytes", oldLength);
            assertFalse("摘要封装也必须计入接收上限", journal.append(entry));
            assertFalse(Files.exists(file(directory)));
        }
    }

    @Test
    public void rewritingFailedSidesChecksumsOnlyTheReplacementAndPreservesTheLegacyTailBytes() throws Exception {
        Path directory = temporary.newFolder().toPath();
        String first = JSON.toJSONString(entry("first")) + "\r\n";
        String legacyTail = JSON.toJSONString(entry("legacy-tail")) + "\r\n";
        Files.writeString(file(directory), first + legacyTail, StandardCharsets.UTF_8);
        try (var journal = open(directory)) {
            journal.replacePrefix(1, List.of(entry("retry")));
            List<String> lines = Files.readAllLines(file(directory));
            assertEquals(Integer.valueOf(1), JSON.parseObject(lines.get(0)).getInteger("journalRecordVersion"));
            byte[] bytes = Files.readAllBytes(file(directory));
            byte[] tail = legacyTail.getBytes(StandardCharsets.UTF_8);
            assertArrayEquals(tail, java.util.Arrays.copyOfRange(bytes, bytes.length - tail.length, bytes.length));
            assertEquals(List.of(entry("retry"), entry("legacy-tail")), journal.read(100));
            assertEquals("MIXED", journal.snapshot().get("integritySampleStatus"));
        }
    }

    @Test
    public void wholeReplacementAlsoUsesTheCheckedFormat() throws Exception {
        Path directory = temporary.newFolder().toPath();
        try (var journal = open(directory)) {
            journal.append(entry("first"));
            journal.replace(List.of(entry("remaining")));
            assertEquals(Integer.valueOf(1), JSON.parseObject(Files.readString(file(directory))).getInteger("journalRecordVersion"));
            assertEquals(List.of(entry("remaining")), journal.read(100));
            assertEquals("VERIFIED", journal.snapshot().get("integritySampleStatus"));
        }
    }

    @Test
    public void oversizedReplacementNeverOverwritesTheExistingRecoverableBytes() throws Exception {
        for (boolean prefix : List.of(true, false)) {
            Path directory = temporary.newFolder().toPath();
            try (var journal = open(directory)) {
                assertTrue(journal.append(entry("first")));
                byte[] original = Files.readAllBytes(file(directory));
                ReflectionTestUtils.setField(journal, "maxEntryBytes", (long) original.length);
                var tooLarge = new DurableExecutionPersistenceJournal.Entry("large", "x".repeat(4096), null, true, 1L,
                        null, null, null, null, null, null, null, null, null, true, false);
                if (prefix) journal.replacePrefix(1, List.of(tooLarge));
                else journal.replace(List.of(tooLarge));
                assertArrayEquals(original, Files.readAllBytes(file(directory)));
                assertEquals("REPLACEMENT_FAILED", journal.snapshot().get("status"));
                assertEquals(List.of(entry("first")), journal.read(100));
            }
        }
    }

    @Test
    public void checkedWireStillHonorsTheReadersBudgetAndPhysicalOffsets() throws Exception {
        Path directory = temporary.newFolder().toPath();
        long firstEnd;
        try (var journal = open(directory)) {
            journal.append(entry("first"));
            firstEnd = Files.size(file(directory));
            journal.append(entry("second"));
        }
        long budget = firstEnd + 7;
        var batch = JournalRecordReader.read(file(directory), 0, 100, budget, 4096);
        assertEquals(JournalRecordReader.Outcome.LIMIT, batch.outcome());
        assertEquals(budget, batch.scannedBytes());
        assertEquals(firstEnd, batch.safeEndOffset());
        assertEquals(List.of(entry("first")), batch.records().stream().map(JournalRecordReader.Record::entry).toList());
        assertTrue(batch.records().get(0).checksumVerified());
        var rest = JournalRecordReader.read(file(directory), firstEnd, 100, 4096, 4096);
        assertEquals(List.of(entry("second")), rest.records().stream().map(JournalRecordReader.Record::entry).toList());
        assertTrue(rest.records().get(0).checksumVerified());
    }

    @Test
    public void optionalBillingSnapshotIsPreservedAndIncludedInThePayloadDigest() throws Exception {
        Path directory = temporary.newFolder().toPath();
        var event = new DurableExecutionPersistenceJournal.Entry("with-billing", "{}", "{}", true, 1L,
                null, null, null, null, null, null, null, null, null, true, true, "{\"unitPrice\":1}");
        try (var journal = open(directory)) {
            assertTrue(journal.append(event));
            assertEquals(List.of(event), journal.read(100));
        }
        JSONObject wire = JSON.parseObject(Files.readString(file(directory)));
        JSONObject payload = JSON.parseObject(wire.getString("payload"));
        payload.put("billingSnapshotJson", "{\"unitPrice\":9}");
        wire.put("payload", JSON.toJSONString(payload));
        Files.writeString(file(directory), JSON.toJSONString(wire) + "\n", StandardCharsets.UTF_8);
        try (var journal = open(directory)) {
            assertTrue(journal.read(100).isEmpty());
            assertEquals("RECOVERY_REQUIRED", journal.snapshot().get("status"));
        }
    }

    private static DurableExecutionPersistenceJournal open(Path directory) {
        var journal = new DurableExecutionPersistenceJournal();
        ReflectionTestUtils.setField(journal, "directory", directory.toString());
        journal.initialize();
        return journal;
    }

    private static Path file(Path directory) { return directory.resolve("execution-persistence.ndjson"); }

    private static DurableExecutionPersistenceJournal.Entry entry(String id) {
        return new DurableExecutionPersistenceJournal.Entry(id, "{\"score\":88}", null, true, 1L, null,
                null, null, null, null, null, null, null, null, true, false);
    }
}
