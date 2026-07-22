package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.ArtifactImportResult;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.server.artifact.ArtifactDeploymentService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

public class DecisionArtifactControllerTest {

    @Test
    public void downloadReturnsImmutableBytesAndBothDigests() {
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(1L);
        artifact.setArtifactDigest(String.valueOf('a').repeat(64));
        artifact.setPackageDigest(String.valueOf('b').repeat(64));
        artifact.setPackageContent("zip".getBytes(StandardCharsets.UTF_8));
        DecisionArtifactController controller = new DecisionArtifactController();
        ReflectionTestUtils.setField(controller, "deploymentService", new ArtifactDeploymentService() {
            @Override
            public DecisionArtifact getArtifact(Long artifactId) {
                return artifact;
            }
        });

        ResponseEntity<byte[]> response = controller.download(1L);

        Assert.assertArrayEquals(artifact.getPackageContent(), response.getBody());
        Assert.assertEquals(artifact.getArtifactDigest(), response.getHeaders().getFirst("X-Artifact-Digest"));
        Assert.assertEquals(artifact.getPackageDigest(), response.getHeaders().getFirst("X-Package-Digest"));
        Assert.assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("artifact-1.zip"));
    }

    @Test
    public void importEndpointReturnsStructuredValidationFailure() {
        DecisionArtifactController controller = new DecisionArtifactController();
        ReflectionTestUtils.setField(controller, "deploymentService", new ArtifactDeploymentService() {
            @Override
            public ArtifactImportResult importArtifact(byte[] bytes, String expectedPackageDigest, String actor) {
                throw new IllegalArgumentException("制品组件摘要校验失败");
            }
        });

        var response = controller.importArtifact(new MockMultipartFile(
                "file", "artifact.zip", "application/zip", new byte[]{1}), null);

        Assert.assertEquals(422, response.getCode());
        Assert.assertTrue(response.getMessage().contains("摘要"));
    }
}
