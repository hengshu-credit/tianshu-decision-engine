package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ExecutionJournalOwnershipTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final List<DurableExecutionPersistenceJournal> opened = new ArrayList<>();

    @After
    public void closeJournals() throws Exception {
        for (DurableExecutionPersistenceJournal journal : opened) close(journal);
    }

    @Test
    public void secondInstanceCannotReadAppendOrReplaceAnOwnedDirectory() throws Exception {
        Path directory = temporary.newFolder().toPath();
        DurableExecutionPersistenceJournal owner = open(directory);
        assertTrue(owner.append(entry("first")));
        byte[] original = Files.readAllBytes(file(directory));
        DurableExecutionPersistenceJournal contender = open(directory);

        assertFalse("同目录第二实例不能确认写入", contender.append(entry("second")));
        assertTrue("未取得所有权不能参与恢复", contender.read(10).isEmpty());
        contender.replace(List.of());
        contender.replacePrefix(1, List.of());
        assertArrayEquals(original, Files.readAllBytes(file(directory)));
        assertEquals("OWNERSHIP_CONFLICT", contender.snapshot().get("status"));
        assertEquals(Boolean.FALSE, contender.snapshot().get("available"));
        assertNull("未持锁时积压不能伪装为零", contender.snapshot().get("pending"));

        close(owner);
        DurableExecutionPersistenceJournal successor = open(directory);
        assertEquals(List.of(entry("first")), successor.read(10));
        assertTrue(successor.append(entry("after-close")));
        assertFalse("旧的竞争实例不能绕过显式所有权获取", contender.append(entry("late")));
    }

    @Test
    public void directoryOwnershipSurvivesReplacingTheDataFile() throws Exception {
        Path directory = temporary.newFolder().toPath();
        DurableExecutionPersistenceJournal owner = open(directory);
        assertTrue(owner.append(entry("first")));
        owner.replacePrefix(1, List.of());
        assertTrue(owner.read(10).isEmpty());
        DurableExecutionPersistenceJournal contender = open(directory);
        assertFalse("不能把所有权锁放在会被替换的 ndjson 文件上", contender.append(entry("intruder")));
        assertTrue(owner.append(entry("owner")));
        assertEquals(List.of(entry("owner")), owner.read(10));
    }

    @Test
    public void closeIsFinalAndAllowsANewInstanceToTakeOver() throws Exception {
        Path directory = temporary.newFolder().toPath();
        DurableExecutionPersistenceJournal owner = open(directory);
        assertTrue(owner.append(entry("first")));
        close(owner);
        owner.initialize();
        assertFalse("关闭实例不能重新初始化或确认写入", owner.append(entry("late")));
        owner.replace(List.of());
        assertEquals("CLOSED", owner.snapshot().get("status"));
        DurableExecutionPersistenceJournal successor = open(directory);
        assertEquals(List.of(entry("first")), successor.read(10));
        assertTrue(successor.append(entry("second")));
    }

    @Test
    public void anotherJvmCannotOwnTheDirectoryUntilTheOwnerProcessExits() throws Exception {
        Path directory = temporary.newFolder().toPath();
        Path ready = directory.resolve("probe-ready");
        Path output = directory.resolve("probe-output.log");
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classPath, OwnerProcess.class.getName(), directory.toString(), ready.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(ready) && process.isAlive() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue("独立 JVM 应成功确认首条事件: " + Files.readString(output), Files.exists(ready));
            assertEquals("true", Files.readString(ready));
            DurableExecutionPersistenceJournal contender = open(directory);
            assertFalse("另一 JVM 持有目录时不能确认写入", contender.append(entry("intruder")));
            process.destroyForcibly();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            // Windows 进程已退出与文件锁完成内核清理之间可能有短暂间隔；必须最终真实取得所有权。
            DurableExecutionPersistenceJournal successor;
            long releaseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            do {
                successor = open(directory);
                if (Boolean.TRUE.equals(successor.snapshot().get("available"))) break;
                close(successor);
                Thread.sleep(20);
            } while (System.nanoTime() < releaseDeadline);
            assertEquals("进程退出后必须释放所有权", Boolean.TRUE, successor.snapshot().get("available"));
            assertEquals(List.of(entry("child-owner")), successor.read(10));
            assertTrue(successor.append(entry("new-owner")));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            assertTrue("子进程必须退出并释放文件句柄", process.waitFor(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void startupWithATornTailPreservesBytesAndRejectsFurtherAppends() throws Exception {
        Path directory = temporary.newFolder().toPath();
        String content = JSON.toJSONString(entry("confirmed")) + "\n" + "{\"eventId\":\"torn";
        Files.writeString(file(directory), content, StandardCharsets.UTF_8);
        byte[] original = Files.readAllBytes(file(directory));
        DurableExecutionPersistenceJournal journal = open(directory);

        assertFalse("不能在重启前留下的半行后继续确认新事件", journal.append(entry("next")));
        journal.replace(List.of());
        journal.replacePrefix(1, List.of(entry("must-not-rewrite-torn-file")));
        assertArrayEquals(original, Files.readAllBytes(file(directory)));
        assertEquals("RECOVERY_REQUIRED", journal.snapshot().get("status"));
        assertEquals(Boolean.FALSE, journal.snapshot().get("available"));
        assertEquals(List.of(entry("confirmed")), journal.read(1));
    }

    @Test
    public void validJsonWithoutItsFinalNewlineCannotBeFollowedByAnAcknowledgedAppend() throws Exception {
        Path directory = temporary.newFolder().toPath();
        byte[] unterminated = JSON.toJSONBytes(entry("missing-newline"));
        Files.write(file(directory), unterminated);
        DurableExecutionPersistenceJournal journal = open(directory);

        assertFalse("合法 JSON 也不能省略 NDJSON 记录边界后继续追加", journal.append(entry("next")));
        assertArrayEquals(unterminated, Files.readAllBytes(file(directory)));
        assertEquals("RECOVERY_REQUIRED", journal.snapshot().get("status"));
    }

    @Test
    public void partialAppendFailureCannotBeFollowedByAnotherAcknowledgedWrite() throws Exception {
        assertWriteFailureRemainsVisible(Fault.PARTIAL_WRITE);
    }

    @Test
    public void forceFailureCannotBeHiddenByLaterAppendOrReplacement() throws Exception {
        assertWriteFailureRemainsVisible(Fault.FORCE);
    }

    @Test
    public void unsupportedAtomicPrefixReplacementKeepsTheOriginalAndStagedBytes() throws Exception {
        assertUnsupportedAtomicReplacement(true);
    }

    @Test
    public void unsupportedAtomicWholeReplacementKeepsTheOriginalAndStagedBytes() throws Exception {
        assertUnsupportedAtomicReplacement(false);
    }

    private void assertUnsupportedAtomicReplacement(boolean prefix) throws Exception {
        Path directory = temporary.newFolder().toPath();
        DurableExecutionPersistenceJournal journal = new DurableExecutionPersistenceJournal() {
            @Override void replaceAtomically(Path source) throws IOException {
                throw new AtomicMoveNotSupportedException(source.toString(), file(directory).toString(), "injected");
            }
        };
        configure(journal, directory);
        assertTrue(journal.append(entry("first")));
        assertTrue(journal.append(entry("second")));
        byte[] original = Files.readAllBytes(file(directory));
        if (prefix) journal.replacePrefix(1, List.of(entry("retry")));
        else journal.replace(List.of(entry("second")));

        assertArrayEquals("不支持原子移动时不能降级覆盖唯一原文件", original, Files.readAllBytes(file(directory)));
        assertTrue("保留已force暂存文件供诊断", Files.exists(file(directory).resolveSibling(
                file(directory).getFileName() + (prefix ? ".prefix.tmp" : ".tmp"))));
        assertEquals("REPLACEMENT_FAILED", journal.snapshot().get("status"));
        assertFalse(journal.append(entry("later")));
        assertEquals(List.of(entry("first"), entry("second")), journal.read(10));
    }

    @Test
    public void oversizeRejectionDoesNotPoisonAHealthyJournal() throws Exception {
        Path directory = temporary.newFolder().toPath();
        DurableExecutionPersistenceJournal journal = open(directory);
        ReflectionTestUtils.setField(journal, "maxEntryBytes", 1L);
        assertFalse(journal.append(entry("too-large")));
        ReflectionTestUtils.setField(journal, "maxEntryBytes", 1024 * 1024L);
        journal.initialize();
        assertTrue(journal.append(entry("valid")));
        assertEquals(List.of(entry("valid")), journal.read(10));
    }

    private void assertWriteFailureRemainsVisible(Fault fault) throws Exception {
        Path directory = temporary.newFolder().toPath();
        FailingJournal journal = new FailingJournal(directory);
        configure(journal, directory);
        assertTrue(journal.append(entry("confirmed")));
        byte[] confirmed = Files.readAllBytes(file(directory));
        journal.fault = fault;
        assertFalse("注入的真实文件 I/O 故障必须返回未确认", journal.append(entry("uncertain")));
        byte[] failed = Files.readAllBytes(file(directory));
        assertTrue("故障应发生在字节已部分或全部写入以后", failed.length > confirmed.length);
        assertArrayEquals(confirmed, java.util.Arrays.copyOf(failed, confirmed.length));
        journal.fault = Fault.NONE;

        assertFalse("一次结果不确定的 append 后必须停止确认，不能盲目继续追加", journal.append(entry("later")));
        journal.initialize();
        assertFalse("重复初始化不能清除写入失败状态", journal.append(entry("after-reinitialize")));
        journal.replace(List.of());
        journal.replacePrefix(1, List.of(entry("must-not-rewrite-after-failure")));
        assertArrayEquals("故障字节和已确认前缀都应保留供诊断", failed, Files.readAllBytes(file(directory)));
        assertEquals(List.of(entry("confirmed")), journal.read(1));
        assertEquals("WRITE_FAILED", journal.snapshot().get("status"));
        assertEquals((long) confirmed.length, journal.snapshot().get("confirmedBytes"));
        assertEquals((long) confirmed.length, journal.snapshot().get("failureOffset"));
        assertTrue(String.valueOf(journal.snapshot().get("failureReason")).contains("IOException"));
        assertNull(journal.snapshot().get("pending"));
    }

    private DurableExecutionPersistenceJournal open(Path directory) {
        DurableExecutionPersistenceJournal journal = new DurableExecutionPersistenceJournal();
        configure(journal, directory);
        return journal;
    }

    private void configure(DurableExecutionPersistenceJournal journal, Path directory) {
        opened.add(journal);
        ReflectionTestUtils.setField(journal, "directory", directory.toString());
        journal.initialize();
    }

    private static void close(DurableExecutionPersistenceJournal journal) throws Exception {
        if ((Object) journal instanceof AutoCloseable closeable) closeable.close();
    }

    private static Path file(Path directory) { return directory.resolve("execution-persistence.ndjson"); }

    private static DurableExecutionPersistenceJournal.Entry entry(String id) {
        return new DurableExecutionPersistenceJournal.Entry(id, "{}", null, true, 1L, null,
                null, null, null, null, null, null, null, null, true, false);
    }

    private enum Fault { NONE, PARTIAL_WRITE, FORCE }

    private static final class FailingJournal extends DurableExecutionPersistenceJournal {
        private final Path testDirectory;
        private Fault fault = Fault.NONE;
        private FailingJournal(Path directory) { this.testDirectory = directory; }
        @Override void writeAndForce(byte[] bytes) throws IOException {
            if (fault == Fault.NONE) {
                super.writeAndForce(bytes);
                return;
            }
            try (FileChannel channel = FileChannel.open(file(testDirectory), StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, fault == Fault.PARTIAL_WRITE ? bytes.length / 2 : bytes.length);
                while (buffer.hasRemaining()) channel.write(buffer);
                throw new IOException(fault == Fault.PARTIAL_WRITE ? "injected partial write" : "injected force failure");
            }
        }
    }

    public static final class OwnerProcess {
        public static void main(String[] args) throws Exception {
            DurableExecutionPersistenceJournal journal = new DurableExecutionPersistenceJournal();
            ReflectionTestUtils.setField(journal, "directory", args[0]);
            journal.initialize();
            Files.writeString(Path.of(args[1]), String.valueOf(journal.append(entry("child-owner"))));
            try { System.in.read(); }
            finally { close(journal); }
        }
    }
}
