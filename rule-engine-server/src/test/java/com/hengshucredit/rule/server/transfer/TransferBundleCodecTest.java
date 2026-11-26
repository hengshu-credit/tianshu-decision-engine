package com.hengshucredit.rule.server.transfer;

import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TransferBundleCodecTest {
    @org.junit.Test
    public void base64ModelKeepsItsOriginalBytesAcrossPackingBlocks() {
        byte[] payload = new byte[150_007];
        new java.util.Random(17).nextBytes(payload);
        var source = new com.hengshucredit.rule.server.artifact.DecisionArtifactPackage();
        var expected = new com.hengshucredit.rule.server.artifact.DecisionArtifactPackage();
        String name = "models/test.onnx";
        expected.addComponent(name, "application/octet-stream", payload);
        source.addBase64Component(name, "application/octet-stream", java.util.Base64.getEncoder().encodeToString(payload));
        var codec = new com.hengshucredit.rule.server.artifact.DecisionArtifactPackageCodec();
        org.junit.Assert.assertArrayEquals(codec.encode(expected), codec.encode(source));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> source.addBase64Component("models/invalid", "application/octet-stream", "aaaa=aaa"));
    }


    private final TransferBundleCodec codec = new TransferBundleCodec();

    @Test
    public void sharedExtensionIsPackagedOnceAndTheBindingNeverBecomesEditableConfiguration() {
        byte[] bytes = "test library content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = com.hengshucredit.rule.server.artifact.Sha256Digests.bytes(bytes);
        var library = new TransferFunctionLibraries.Library(hash, bytes);
        var javaFn = new TransferBundle.Resource("FUNCTION:10", Map.of("implType","JAVA","implClass","sample.Functions"),List.of(),List.of());
        var beanFn = new TransferBundle.Resource("FUNCTION:11", Map.of("implType","BEAN","implBeanName","sampleBean"),List.of(),List.of());
        var bundle = new TransferBundle(List.of(javaFn.key(),beanFn.key()),List.of(javaFn,beanFn),List.of());
        byte[] zip = codec.encode(bundle, Map.of(javaFn.key(),library,beanFn.key(),library));
        var archive = new com.hengshucredit.rule.server.artifact.DecisionArtifactPackageCodec().decode(zip).getArtifactPackage();
        Assert.assertEquals("3",archive.getMetadata().get("resourceFormatVersion"));
        Assert.assertEquals(3,archive.getComponents().size());
        Assert.assertArrayEquals(bytes,archive.getComponent("function-jars/"+hash+".jar").getContent());
        var decoded = codec.decode(zip);
        Assert.assertEquals(2,decoded.libraries().size());
        for (var fn : decoded.bundle().resources()) {
            Assert.assertEquals(hash,decoded.libraries().get(fn.key()).digest());
            Assert.assertFalse(fn.configuration().containsKey("_transferFunctionJar"));
        }
        var missing = new TransferFunctionLibraries("unused-test-extension-directory");
        Assert.assertNotNull(missing.issue(decoded.libraries().get(javaFn.key()),javaFn.configuration(),new java.util.HashMap<>()));
        Assert.assertTrue(codec.decode(codec.encode(bundle)).libraries().isEmpty());
    }

    @Test
    public void mismatchedDeclaredJarDigestFailsEvenWithAnIntactOuterManifest() {
        var fn = new TransferBundle.Resource("FUNCTION:1", Map.of("implType","JAVA","implClass","sample.A"),List.of(),List.of());
        var bundle = new TransferBundle(List.of(fn.key()),List.of(fn),List.of());
        var invalid = new TransferFunctionLibraries.Library("0".repeat(64),new byte[]{1});
        byte[] zip = codec.encode(bundle, Map.of(fn.key(),invalid));
        Assert.assertThrows(IllegalArgumentException.class,()->codec.decode(zip));
    }

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

    @Test
    public void externalReferenceCanBePackagedWithoutItsUpstreamResource() {
        TransferBundle bundle = new TransferBundle(List.of("VARIABLE:1"), List.of(
                new TransferBundle.Resource("VARIABLE:1", Map.of("sourceConfig", "{\"apiConfigId\":7}"),
                        List.of(new TransferBundle.Reference("/sourceConfig/@json/apiConfigId",
                                "EXTERNAL_API:7", null, true, "credit_api", "授信接口")), List.of())), List.of());

        TransferBundle.Resource resource = codec.decode(codec.encode(bundle)).bundle().resources().get(0);

        Assert.assertTrue(resource.references().get(0).external());
        Assert.assertEquals("credit_api", resource.references().get(0).targetCode());
        Assert.assertEquals("授信接口", resource.references().get(0).targetName());
    }
}
