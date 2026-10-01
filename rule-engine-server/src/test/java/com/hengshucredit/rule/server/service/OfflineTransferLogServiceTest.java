package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.transfer.TransferBundle;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OfflineTransferLogServiceTest {
    @Test
    public void lineageSnapshotKeepsExistingNodesAndSkipsMissingReferences() {
        OfflineTransferLogService service = new OfflineTransferLogService();
        TransferBundle bundle = new TransferBundle(List.of("RULE:1"), List.of(
                new TransferBundle.Resource("RULE:1", Map.of("ruleCode", "risk"), List.of(
                        new TransferBundle.Reference("/dependencyId", "VARIABLE:9", null)), List.of()),
                new TransferBundle.Resource("VARIABLE:9", Map.of("varCode", "score"), List.of(), List.of())), List.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> graph = ReflectionTestUtils.invokeMethod(service, "lineageSnapshot", bundle, List.of("RULE:1"));

        assertEquals(1, ((List<?>) graph.get("nodes")).size());
        assertTrue(((List<?>) graph.get("edges")).isEmpty());
    }

    @Test
    public void lineageSnapshotContainsEdgesWhenBothResourcesExist() {
        OfflineTransferLogService service = new OfflineTransferLogService();
        TransferBundle bundle = new TransferBundle(List.of("RULE:1"), List.of(
                new TransferBundle.Resource("RULE:1", Map.of("ruleCode", "risk"), List.of(
                        new TransferBundle.Reference("/dependencyId", "VARIABLE:9", null)), List.of()),
                new TransferBundle.Resource("VARIABLE:9", Map.of("varCode", "score"), List.of(), List.of())), List.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> graph = ReflectionTestUtils.invokeMethod(service, "lineageSnapshot", bundle, null);

        assertEquals(2, ((List<?>) graph.get("nodes")).size());
        assertEquals(1, ((List<?>) graph.get("edges")).size());
    }
}
