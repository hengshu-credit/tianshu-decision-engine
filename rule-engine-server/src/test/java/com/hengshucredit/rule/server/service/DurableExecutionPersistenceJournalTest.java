package com.hengshucredit.rule.server.service;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DurableExecutionPersistenceJournalTest {
    @Test
    public void appendReadAndReplaceAreDurableAndPreservePendingSides() throws Exception {
        Path directory = Files.createTempDirectory("tianshu-journal-");
        DurableExecutionPersistenceJournal journal = new DurableExecutionPersistenceJournal();
        ReflectionTestUtils.setField(journal, "directory", directory.toString());
        ReflectionTestUtils.setField(journal, "enabled", true);
        ReflectionTestUtils.setField(journal, "maxEntryBytes", 1024 * 1024L);
        journal.initialize();

        DurableExecutionPersistenceJournal.Entry entry = new DurableExecutionPersistenceJournal.Entry(
                "event-1", "{\"traceId\":\"t1\"}", "{\"id\":1}", true,
                12L, null, 3L, "P", 4L, "A", "TOKEN", 5L, "T", "OPEN",
                true, false);
        assertTrue(journal.append(entry));
        assertEquals(List.of(entry), journal.read(10));

        journal.replace(List.of(entry));
        assertEquals(List.of(entry), journal.read(10));
        journal.replace(List.of());
        assertEquals(List.of(), journal.read(10));
    }
}
