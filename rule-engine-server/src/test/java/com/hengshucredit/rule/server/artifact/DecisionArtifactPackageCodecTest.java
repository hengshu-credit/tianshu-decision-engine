package com.hengshucredit.rule.server.artifact;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class DecisionArtifactPackageCodecTest {

    private final DecisionArtifactPackageCodec codec = new DecisionArtifactPackageCodec();

    @Test
    public void sameLogicalArtifactProducesIdenticalPackageBytes() {
        DecisionArtifactPackage first = new DecisionArtifactPackage();
        first.putMetadata("revisionId", 12L);
        first.putMetadata("ruleCode", "CaseSensitive_Rule");
        first.addComponent("variables/20.json", "application/json", bytes("{\"name\":\"Customer_ID\"}"));
        first.addComponent("rule/model.json", "application/json", bytes("{\"type\":\"decisionTable\"}"));

        DecisionArtifactPackage second = new DecisionArtifactPackage();
        second.putMetadata("ruleCode", "CaseSensitive_Rule");
        second.putMetadata("revisionId", 12L);
        second.addComponent("rule/model.json", "application/json", bytes("{\"type\":\"decisionTable\"}"));
        second.addComponent("variables/20.json", "application/json", bytes("{\"name\":\"Customer_ID\"}"));

        byte[] firstBytes = codec.encode(first);
        byte[] secondBytes = codec.encode(second);

        Assert.assertArrayEquals(firstBytes, secondBytes);
        DecisionArtifactPackageCodec.DecodedPackage decoded = codec.decode(firstBytes);
        Assert.assertEquals(decoded.getPackageDigest(), Sha256Digests.bytes(firstBytes));
        Assert.assertArrayEquals(bytes("{\"name\":\"Customer_ID\"}"),
                decoded.getArtifactPackage().getComponent("variables/20.json").getContent());
        Assert.assertEquals(64, decoded.getArtifactDigest().length());
    }

    @Test
    public void decodeRejectsComponentWhoseDigestDoesNotMatchManifest() {
        DecisionArtifactPackage artifact = new DecisionArtifactPackage();
        artifact.addComponent("rule/model.json", "application/json", bytes("{\"v\":1}"));
        byte[] validPackage = codec.encode(artifact);
        byte[] tamperedPackage = ZipTestSupport.replaceEntry(validPackage,
                "rule/model.json", bytes("{\"v\":2}"));

        IllegalArgumentException error = Assert.assertThrows(IllegalArgumentException.class,
                () -> codec.decode(tamperedPackage));

        Assert.assertTrue(error.getMessage().contains("摘要"));
    }

    @Test
    public void componentPathsCannotEscapeArtifactRoot() {
        DecisionArtifactPackage artifact = new DecisionArtifactPackage();

        IllegalArgumentException error = Assert.assertThrows(IllegalArgumentException.class,
                () -> artifact.addComponent("../secret.txt", "text/plain", bytes("secret")));

        Assert.assertTrue(error.getMessage().contains("路径"));
    }

    @Test
    public void decodeStopsWhileStreamingBeforeUncompressedLimitIsExceeded() {
        DecisionArtifactPackageCodec boundedCodec = new DecisionArtifactPackageCodec(10, 16);
        DecisionArtifactPackage artifact = new DecisionArtifactPackage();
        artifact.addComponent("rule/model.json", "application/json",
                "0123456789abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException error = Assert.assertThrows(IllegalArgumentException.class,
                () -> boundedCodec.decode(codec.encode(artifact)));

        Assert.assertTrue(error.getMessage().contains("解压大小超过限制"));
    }

    @Test
    public void fieldProjectionVerifiesAndDiscardsModelWeights() {
        DecisionArtifactPackage pack = new DecisionArtifactPackage();
        pack.addComponent("rule/fields.json", "application/json", bytes("[]"));
        pack.addComponent("models/face.onnx", "application/octet-stream", bytes("weights"), Map.of("resourceType", "MODEL"));
        var encoded = codec.encodePackage(pack, true);
        var paths = java.util.Set.of("rule/fields.json");
        var fields = codec.decode(encoded.bytes(), paths);
        Assert.assertEquals(encoded.artifactDigest(), fields.getArtifactDigest());
        Assert.assertEquals(paths, fields.getArtifactPackage().getComponents().keySet());
        var catalog = codec.decodeCatalog(encoded.bytes());
        var model = catalog.getArtifactPackage().getComponent("models/face.onnx");
        Assert.assertEquals("MODEL", model.getMetadata().get("resourceType"));
        Assert.assertEquals(Sha256Digests.bytes(bytes("weights")), model.digest());
        Assert.assertThrows(IllegalStateException.class, model::getContent);
        byte[] corrupt = ZipTestSupport.replaceEntry(encoded.bytes(), "models/face.onnx", bytes("changed"));
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.decode(corrupt, paths));
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.decodeCatalog(corrupt));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class ZipTestSupport {
        private static byte[] replaceEntry(byte[] source, String path, byte[] replacement) {
            Map<String, byte[]> entries = DecisionArtifactPackageCodecTestZip.read(source);
            entries.put(path, replacement);
            return DecisionArtifactPackageCodecTestZip.write(new LinkedHashMap<>(entries));
        }
    }
}
