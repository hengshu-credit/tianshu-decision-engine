package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BoundedAsyncLogWriterTest {

    @Test
    public void fullDetailQueueFallsBackToReservedMetadataQueueWithoutBlocking() throws Exception {
        List<String> written = Collections.synchronizedList(new ArrayList<>());
        BoundedAsyncLogWriter<String> writer = new BoundedAsyncLogWriter<>(2, 10, 10, 1000,
                "test-log-writer", value -> "meta:" + value, written::addAll);

        assertTrue(writer.offer("detail-1"));
        assertTrue(writer.offer("detail-2"));
        assertEquals(1, writer.detailSize());
        assertEquals(1, writer.metadataSize());
        assertEquals(1L, writer.getDroppedBodies());

        writer.start();
        writer.close();
        assertTrue(written.contains("detail-1"));
        assertTrue(written.contains("meta:detail-2"));
    }
}
