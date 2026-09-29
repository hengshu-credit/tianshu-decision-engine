package com.hengshucredit.rule.server.transfer;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

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
}
