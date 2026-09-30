package com.hengshucredit.rule.server.transfer;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 项目级离线导入必须按显式项目绑定解析目标 ID，不能按名称猜测。 */
public class OfflineResourceImportServiceTest {
    @Test
    public void sourceProjectBindingWinsOverSingleTargetFallback() {
        OfflineResourceImportService service = new OfflineResourceImportService();
        TransferImportOptions options = new TransferImportOptions(
                99L, "PROJECT", "REUSE", "SUFFIX", "_imported", false,
                null, null, Map.of("7", 42L));
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("projectId", 7L);

        Long target = ReflectionTestUtils.invokeMethod(service, "resolveTargetProjectId",
                configuration, options, 99L);

        assertEquals(Long.valueOf(42L), target);
    }

    @Test(expected = IllegalArgumentException.class)
    public void projectImportWithoutExplicitBindingIsRejected() {
        OfflineResourceImportService service = new OfflineResourceImportService();
        TransferImportOptions options = new TransferImportOptions(
                null, "PROJECT", "REUSE", "SUFFIX", "_imported", false,
                null, null, Map.of());
        ReflectionTestUtils.invokeMethod(service, "resolveTargetProjectId",
                Map.of("projectId", 7L), options, null);
    }

    @Test
    public void fixedVersionCycleIsOrderedByTargetRuleBeforeCallerRule() {
        OfflineResourceImportService service = new OfflineResourceImportService();
        TransferBundle.Resource caller = new TransferBundle.Resource(
                "RULE:1", Map.of("ruleCode", "caller"),
                List.of(new TransferBundle.Reference("/fixedVersion", "RULE_VERSION:9", null)), List.of());
        TransferBundle.Resource binding = new TransferBundle.Resource(
                "RULE_VERSION:9", Map.of("definitionId", 2L, "versionNo", 1),
                List.of(new TransferBundle.Reference("/definitionId", "RULE:2", null)), List.of());
        TransferBundle.Resource target = new TransferBundle.Resource(
                "RULE:2", Map.of("ruleCode", "target"), List.of(), List.of());

        @SuppressWarnings("unchecked")
        List<TransferBundle.Resource> ordered = ReflectionTestUtils.invokeMethod(
                service, "topologicalOrder", List.of(caller, binding, target));

        assertEquals(List.of("RULE:2", "RULE_VERSION:9", "RULE:1"),
                ordered.stream().map(TransferBundle.Resource::key).toList());
    }

    @Test
    public void selectedImportAllowsAnExternalReferenceToBeResolvedByThePanel() {
        OfflineResourceImportService service = new OfflineResourceImportService();
        TransferBundle.Resource selected = new TransferBundle.Resource(
                "RULE:1", Map.of("ruleCode", "caller"),
                List.of(new TransferBundle.Reference("/dependencyId", "VARIABLE:9", null,
                        true, "customer_id", "客户编号")), List.of());

        @SuppressWarnings("unchecked")
        List<TransferBundle.Resource> ordered = ReflectionTestUtils.invokeMethod(
                service, "topologicalOrder", List.of(selected));

        assertEquals(List.of("RULE:1"), ordered.stream().map(TransferBundle.Resource::key).toList());
        assertTrue(ordered.get(0).references().get(0).external());
    }
}
