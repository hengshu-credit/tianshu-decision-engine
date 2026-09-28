package com.hengshucredit.rule.server.transfer;

import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TransferBundleCodecTest {
    private final TransferBundleCodec codec = new TransferBundleCodec();

    @Test
    public void packageRoundTripKeepsStableKeysAndExcludesListRowsByContract() {
        Map<String, Object> variable = new LinkedHashMap<>();
        variable.put("projectId", 10L);
        variable.put("scope", "PROJECT");
        variable.put("varCode", "customerId");
        variable.put("sourceConfig", "{\"apiConfigId\":7}");
        TransferBundle bundle = new TransferBundle(
                List.of("VARIABLE:1"),
                List.of(
                        new TransferBundle.Resource("VARIABLE:1", variable,
                                List.of(new TransferBundle.Reference("/sourceConfig/@json/apiConfigId",
                                        "EXTERNAL_API:7", null)), List.of()),
                        new TransferBundle.Resource("EXTERNAL_API:7", Map.of("apiCode", "credit"),
                                List.of(), List.of("目标环境连接、地址和凭据需复核"))),
                List.of("名单记录数据未随配置包导出"));

        TransferBundleCodec.Decoded decoded = codec.decode(codec.encode(bundle));

        Assert.assertEquals(List.of("VARIABLE:1"), decoded.bundle().roots());
        Assert.assertEquals(2, decoded.bundle().resources().size());
        TransferBundle.Resource variableResource = decoded.bundle().resources().stream()
                .filter(resource -> "VARIABLE:1".equals(resource.key())).findFirst().orElseThrow();
        Assert.assertEquals("EXTERNAL_API:7", variableResource.references().get(0).targetKey());
        Assert.assertEquals(List.of("名单记录数据未随配置包导出"), decoded.bundle().warnings());
    }

    @Test
    public void packageRejectsMissingDependencyAndDuplicateRoot() {
        Map<String, Object> variable = new LinkedHashMap<>();
        variable.put("sourceConfig", "{\"apiConfigId\":7}");
        TransferBundle missing = new TransferBundle(List.of("VARIABLE:1"), List.of(
                new TransferBundle.Resource("VARIABLE:1", variable,
                        List.of(new TransferBundle.Reference("/sourceConfig/@json/apiConfigId", "EXTERNAL_API:7", null)), List.of())), List.of());
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.encode(missing));

        Assert.assertThrows(IllegalArgumentException.class, () -> new TransferBundleCodec().encode(
                new TransferBundle(List.of("VARIABLE:1", "VARIABLE:1"), List.of(
                        new TransferBundle.Resource("VARIABLE:1", Map.of("varCode", "x"), List.of(), List.of())), List.of())));
    }

    @Test
    public void jsonPointerCanReadAndReplaceNestedJsonString() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("sourceConfig", "{\"apiConfigId\":7,\"listIds\":[2]}");

        Assert.assertEquals(7L, ((Number) TransferJsonPath.read(root, "/sourceConfig/@json/apiConfigId")).longValue());
        TransferJsonPath.replace(root, "/sourceConfig/@json/apiConfigId", 9L);

        Assert.assertEquals("{\"apiConfigId\":9,\"listIds\":[2]}", root.get("sourceConfig"));
    }
}
