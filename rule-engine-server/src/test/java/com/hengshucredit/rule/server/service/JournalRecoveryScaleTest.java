package com.hengshucredit.rule.server.service;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** 文件读取/确认规模门禁，不包含真实数据库接受或业务写入耗时。 */
public class JournalRecoveryScaleTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test(timeout = 1800000)
    public void oneMillionRecordsResumeWithoutRewritingTheUnconfirmedFile() throws Exception {
        Assume.assumeTrue("设置 -DexecutionJournal.scale=true 验证百万条文件恢复", Boolean.getBoolean("executionJournal.scale"));
        Path directory = temporary.newFolder().toPath();
        assertTrue("规模验证至少需要1GiB临时可用空间", Files.getFileStore(directory).getUsableSpace() > 1024 * 1024 * 1024L);
        Path file = directory.resolve("execution-persistence.ndjson");
        int total = 1_000_000;
        boolean versioned = Boolean.getBoolean("executionJournal.scale.versioned");
        try (var writer = new java.io.BufferedOutputStream(Files.newOutputStream(file))) {
            for (int i = 0; i < total; i++) {
                if (versioned) {
                    writer.write(JournalRecordReader.encode(new DurableExecutionPersistenceJournal.Entry(
                            "scale-" + i, "{}", null, true, null, null,
                            null, null, null, null, null, null, null, null, true, false)));
                } else {
                    writer.write(("{\"eventId\":\"scale-" + i + "\",\"logJson\":\"{}\",\"success\":true,"
                            + "\"logPending\":true,\"billingPending\":false}\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        long originalBytes = Files.size(file);
        FileTime originalModified = Files.getLastModifiedTime(file);
        long started = System.nanoTime();
        int recovered = 0;
        int batches = 0;
        // 中间关闭并重建实例，必须校验游标并从未确认位置恢复。
        for (int phase = 1; phase <= 2; phase++) {
            try (var journal = new DurableExecutionPersistenceJournal()) {
                ReflectionTestUtils.setField(journal, "directory", directory.toString());
                journal.initialize();
                assertEquals("READY", journal.snapshot().get("status"));
                int target = total / 2 * phase;
                while (recovered < target) {
                    var batch = journal.read(1000);
                    assertFalse("到达总数前必须继续读到待恢复记录", batch.isEmpty());
                    for (var entry : batch) assertEquals("不应重复、遗漏或跳过事件", "scale-" + recovered++, entry.eventId());
                    journal.replacePrefix(batch.size(), List.of());
                    batches++;
                    if (recovered < total) {
                        assertEquals("未确认尾部不能通过全文件重写反复搬移", originalBytes, Files.size(file));
                        assertEquals(originalModified, Files.getLastModifiedTime(file));
                    }
                }
                if (phase == 2) {
                    assertTrue(journal.read(1000).isEmpty());
                    assertEquals(0, journal.snapshot().get("pending"));
                    assertEquals(0L, journal.snapshot().get("bytes"));
                }
            }
        }
        assertEquals(total, recovered);
        assertEquals(1000, batches);
        System.out.println("JOURNAL_FILE_SCALE records=" + recovered + " bytes=" + originalBytes
                + " format=" + (versioned ? "CHECKSUM_V1" : "LEGACY")
                + " batches=" + batches + " elapsedMs=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                + " includesDatabaseDelivery=false");
    }
}
