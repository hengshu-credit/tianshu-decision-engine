package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.transfer.TransferBundle;
import com.hengshucredit.rule.model.entity.OfflineTransferLog;
import com.hengshucredit.rule.server.transfer.OfflineResourceTransferService.ExportedPackage;
import com.hengshucredit.rule.server.transfer.TransferBundleCodec;
import com.hengshucredit.rule.server.transfer.TransferRootRequest;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OfflineTransferLogServiceTest {
    @Test
    public void exportCountsSharedDependencyOnceAndKeepsRequestedRootsSeparate() {
        var dependency = new TransferBundle.Reference("/dependencyId", "VARIABLE:9", null);
        var roots = List.of("RULE:1", "RULE:2");
        var bundle = new TransferBundle(roots, List.of(
                new TransferBundle.Resource("RULE:1", Map.of("dependencyId", 9), List.of(dependency), List.of()),
                new TransferBundle.Resource("RULE:2", Map.of("dependencyId", 9), List.of(dependency), List.of()),
                new TransferBundle.Resource("VARIABLE:9", Map.of("varCode", "score"), List.of(), List.of())), List.of());
        var logs = new java.util.ArrayList<OfflineTransferLog>();
        var service = new OfflineTransferLogService() {
            @Override public boolean save(OfflineTransferLog log) { return logs.add(log); }
        };
        service.recordExport(List.of(new TransferRootRequest("RULE", 1L), new TransferRootRequest("RULE", 2L)),
                new ExportedPackage(bundle, new TransferBundleCodec().encode(bundle)), "tester");
        var log = logs.get(0);
        assertEquals(Integer.valueOf(3), log.getResourceCount());
        assertEquals(roots, com.alibaba.fastjson.JSON.parseArray(log.getRootsJson(), String.class));
        assertEquals(3, com.alibaba.fastjson.JSON.parseObject(log.getContentJson()).getJSONObject("lineage")
                .getJSONArray("nodes").size());
    }
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
