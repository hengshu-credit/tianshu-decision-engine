package com.hengshucredit.rule.server.artifact;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.ArtifactDeployment;
import com.hengshucredit.rule.model.entity.ArtifactResourceBinding;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.RuleVariable;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class ArtifactRuntimeSnapshotServiceTest {

    @Test
    public void loadsFrozenVariablesAndAppliesExplicitTargetBinding() {
        DecisionArtifactPackage artifactPackage = new DecisionArtifactPackage();
        artifactPackage.addComponent("variables/7.json", "application/json",
                CanonicalJson.writeBytes(Map.of(
                        "id", 7,
                        "varCode", "riskData",
                        "scriptName", "riskData",
                        "varType", "NUMBER",
                        "varSource", "DB",
                        "sourceConfig", "{\"datasourceId\":99,\"sql\":\"select 1\"}",
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
                JSON.parseObject(variable.getSourceConfig()).getLong("datasourceId"));
    }
}
