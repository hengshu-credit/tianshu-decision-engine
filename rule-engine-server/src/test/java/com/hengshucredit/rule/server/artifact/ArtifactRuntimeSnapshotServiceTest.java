package com.hengshucredit.rule.server.artifact;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.ArtifactDeployment;
import com.hengshucredit.rule.model.entity.ArtifactResourceBinding;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class ArtifactRuntimeSnapshotServiceTest {

    @Test
    public void reusesVerifiedDecodingWithoutSharingExecutionVariables() {
        DecisionArtifact artifact = variableArtifact();
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService() {
            protected DecisionArtifact loadArtifact(Long id) { return artifact; }
        };
        java.util.concurrent.atomic.AtomicInteger decodeCount = new java.util.concurrent.atomic.AtomicInteger();
        DecisionArtifactPackageCodec codec = new DecisionArtifactPackageCodec() {
            public DecodedPackage decode(byte[] bytes) {
                decodeCount.incrementAndGet();
                return super.decode(bytes);
            }
        };
        org.springframework.test.util.ReflectionTestUtils.setField(service, "codec", codec);
        var first = service.load(1L, 10L, 1L);
        first.getVariables().get(0).setVarCode("changed");
        first.getVariables().clear();
        var second = service.load(1L, 20L, 2L);
        Assert.assertEquals("age", second.getVariables().get(0).getVarCode());
        Assert.assertEquals(Long.valueOf(2), second.getVariables().get(0).getProjectId());
        Assert.assertEquals(1, decodeCount.get());
    }

    @Test
    public void cacheHitStillRejectsChangedContentOrDeclaredDigest() {
        DecisionArtifact artifact = variableArtifact();
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService() {
            protected DecisionArtifact loadArtifact(Long id) { return artifact; }
        };
        service.load(1L, 10L, 1L);
        String original = artifact.getArtifactDigest();
        artifact.setArtifactDigest("wrong");
        Assert.assertThrows(IllegalStateException.class, () -> service.load(1L, 10L, 1L));
        artifact.setArtifactDigest(original);
        artifact.getPackageContent()[0] ^= 1;
        Assert.assertThrows(RuntimeException.class, () -> service.load(1L, 10L, 1L));
    }

    @Test
    public void decodingCacheIsBoundedAndEvictsOldPackages() {
        var current = new java.util.concurrent.atomic.AtomicReference<DecisionArtifact>();
        var decodes = new java.util.concurrent.atomic.AtomicInteger();
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService() {
            protected DecisionArtifact loadArtifact(Long id) { return current.get(); }
        };
        org.springframework.test.util.ReflectionTestUtils.setField(service, "codec", new DecisionArtifactPackageCodec() {
            public DecodedPackage decode(byte[] bytes) { decodes.incrementAndGet(); return super.decode(bytes); }
        });
        for (int i = 0; i < 65; i++) { current.set(variableArtifact(i)); service.load(1L, 10L, 1L); }
        current.set(variableArtifact(0));
        service.load(1L, 10L, 1L);
        Assert.assertEquals(66, decodes.get());
        current.set(variableArtifact(64));
        service.load(1L, 10L, 1L);
        Assert.assertEquals(66, decodes.get());
    }

    private DecisionArtifact variableArtifact() { return variableArtifact(0); }

    private DecisionArtifact variableArtifact(int marker) {
        DecisionArtifactPackage pack = new DecisionArtifactPackage();
        pack.putMetadata("marker", marker);
        pack.addComponent("variables/7.json", "application/json",
                CanonicalJson.writeBytes(Map.of("id", 7, "varCode", "age", "varType", "NUMBER")),
                Map.of("resourceType", "VARIABLE"));
        byte[] bytes = new DecisionArtifactPackageCodec().encode(pack);
        var decoded = new DecisionArtifactPackageCodec().decode(bytes);
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(1L);
        artifact.setArtifactDigest(decoded.getArtifactDigest());
        artifact.setPackageDigest(decoded.getPackageDigest());
        artifact.setPackageContent(bytes);
        return artifact;
    }

    @Test
    public void loadsFrozenDataObjectFieldVariableMapping() {
        DecisionArtifactPackage artifactPackage = new DecisionArtifactPackage();
        artifactPackage.addComponent("data-object-fields/30.json", "application/json",
                CanonicalJson.writeBytes(Map.of(
                        "id", 30,
                        "objectId", 20,
                        "scriptName", "ageAlias",
                        "varType", "NUMBER",
                        "refVariableId", 7)),
                Map.of("componentId", "DATA_OBJECT:30",
                        "resourceType", "DATA_OBJECT",
                        "embeddingMode", "EMBEDDED"));
        byte[] packageBytes = new DecisionArtifactPackageCodec().encode(artifactPackage);
        DecisionArtifactPackageCodec.DecodedPackage encoded =
                new DecisionArtifactPackageCodec().decode(packageBytes);
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(1L);
        artifact.setArtifactDigest(encoded.getArtifactDigest());
        artifact.setPackageDigest(encoded.getPackageDigest());
        artifact.setPackageContent(packageBytes);
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService() {
            @Override
            protected DecisionArtifact loadArtifact(Long artifactId) {
                return artifact;
            }
        };

        ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot =
                service.load(1L, 100L, 9L);

        Assert.assertEquals(1, snapshot.getDataObjectFields().size());
        RuleDataObjectField field = snapshot.getDataObjectFields().get(0);
        Assert.assertEquals(Long.valueOf(30L), field.getId());
        Assert.assertEquals(Long.valueOf(7L), field.getRefVariableId());
    }

    @Test
    public void loadsFrozenVariablesAndAppliesExplicitTargetBinding() {
        for (String datasourceKey : List.of("datasourceId", "dbDatasourceId")) {
        DecisionArtifactPackage artifactPackage = new DecisionArtifactPackage();
        artifactPackage.addComponent("variables/7.json", "application/json",
                CanonicalJson.writeBytes(Map.of(
                        "id", 7,
                        "varCode", "riskData",
                        "scriptName", "riskData",
                        "varType", "NUMBER",
                        "varSource", "DB",
                        "sourceConfig", "{\"" + datasourceKey + "\":99,\"sql\":\"select 1\"}",
                        "status", 1)),
                Map.of("componentId", "VARIABLE:7", "resourceType", "VARIABLE",
                        "embeddingMode", "EMBEDDED"));
        artifactPackage.addComponent("bindings/variables/7.json", "application/json",
                CanonicalJson.writeBytes(Map.of(
                        "sourceComponentId", "VARIABLE:7",
                        "targetResourceType", "DB_DATASOURCE")),
                Map.of("componentId", "BINDING:VARIABLE:7", "resourceType", "BINDING",
                        "embeddingMode", "EXPLICIT_BINDING",
                        "targetResourceType", "DB_DATASOURCE"));
        byte[] packageBytes = new DecisionArtifactPackageCodec().encode(artifactPackage);
        DecisionArtifactPackageCodec.DecodedPackage encoded = new DecisionArtifactPackageCodec()
                .decode(packageBytes);
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(1L);
        artifact.setArtifactDigest(encoded.getArtifactDigest());
        artifact.setPackageDigest(encoded.getPackageDigest());
        artifact.setPackageContent(packageBytes);

        ArtifactDeployment deployment = new ArtifactDeployment();
        deployment.setId(2L);
        ArtifactResourceBinding binding = new ArtifactResourceBinding();
        binding.setDeploymentId(2L);
        binding.setComponentId("BINDING:VARIABLE:7");
        binding.setResourceType("DB_DATASOURCE");
        binding.setTargetResourceId(501L);
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService() {
            @Override
            protected DecisionArtifact loadArtifact(Long artifactId) {
                return artifact;
            }

            @Override
            protected ArtifactDeployment loadDeployment(Long artifactId, Long definitionId) {
                return deployment;
            }

            @Override
            protected List<ArtifactResourceBinding> loadBindings(Long deploymentId) {
                return List.of(binding);
            }
        };

        ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot = service.load(1L, 100L, 9L);

        Assert.assertEquals(1, snapshot.getVariables().size());
        RuleVariable variable = snapshot.getVariables().get(0);
        Assert.assertEquals(Long.valueOf(9L), variable.getProjectId());
        Assert.assertEquals(Long.valueOf(501L),
                JSON.parseObject(variable.getSourceConfig()).getLong(datasourceKey));
        binding.setTargetResourceId(502L);
        variable.setSourceConfig("{}");
        var reloaded = service.load(1L, 100L, 10L).getVariables().get(0);
        Assert.assertEquals(Long.valueOf(10L), reloaded.getProjectId());
        Assert.assertEquals(Long.valueOf(502L), JSON.parseObject(reloaded.getSourceConfig()).getLong(datasourceKey));
        }
    }
}
