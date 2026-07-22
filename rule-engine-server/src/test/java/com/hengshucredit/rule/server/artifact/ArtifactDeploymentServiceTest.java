package com.hengshucredit.rule.server.artifact;

import com.hengshucredit.rule.model.dto.ArtifactDeployRequest;
import com.hengshucredit.rule.model.dto.ArtifactImportResult;
import com.hengshucredit.rule.model.entity.ArtifactDeployment;
import com.hengshucredit.rule.model.entity.ArtifactResourceBinding;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.DecisionArtifactComponent;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArtifactDeploymentServiceTest {

    @Test
    public void importVerifiesDigestAndIsIdempotent() {
        FixtureService service = new FixtureService();
        byte[] archive = portableArtifact();
        String packageDigest = Sha256Digests.bytes(archive);

        ArtifactImportResult first = service.importArtifact(archive, packageDigest, "alice");
        service.artifacts.put(first.getArtifactId(), service.insertedArtifact);
        service.byDigest.put(first.getArtifactDigest(), service.insertedArtifact);
        ArtifactImportResult second = service.importArtifact(archive, packageDigest, "bob");

        Assert.assertFalse(first.isDuplicate());
        Assert.assertTrue(second.isDuplicate());
        Assert.assertEquals(first.getArtifactId(), second.getArtifactId());
        Assert.assertEquals(List.of("BINDING:VARIABLE:7"), first.getRequiredBindingComponentIds());
        Assert.assertThrows(IllegalArgumentException.class,
                () -> service.importArtifact(archive, String.valueOf('0').repeat(64), "alice"));
    }

    @Test
    public void incompatibleRuntimeIsRejectedBeforePersistence() {
        FixtureService service = new FixtureService();
        service.compatible = false;

        Assert.assertThrows(IllegalArgumentException.class,
                () -> service.importArtifact(portableArtifact(), null, "alice"));
        Assert.assertEquals(0, service.artifactInsertCount);
    }

    @Test
    public void deployRequiresExplicitTargetAndEveryBinding() {
        FixtureService service = importedFixture();
        ArtifactDeployRequest request = new ArtifactDeployRequest();
        request.setArtifactId(1L);
        request.setEnvironmentCode("uat");
        request.setTargetRuleCode("same_code_is_not_a_binding");

        Assert.assertThrows(IllegalArgumentException.class,
                () -> service.deploy(request, "alice"));

        request.setTargetDefinitionId(90L);
        Assert.assertThrows(IllegalArgumentException.class,
                () -> service.deploy(request, "alice"));

        request.setBindings(Map.of("BINDING:VARIABLE:7", 501L));
        ArtifactDeployment deployment = service.deploy(request, "alice");
        Assert.assertEquals("DEPLOYED", deployment.getStatus());
        Assert.assertEquals(Long.valueOf(90L), deployment.getTargetDefinitionId());
        Assert.assertEquals(1, service.bindings.size());
    }

    @Test
    public void createRuleDeploymentUsesExplicitRequestInsteadOfSourceCodeLookup() {
        FixtureService service = importedFixture();
        ArtifactDeployRequest request = new ArtifactDeployRequest();
        request.setArtifactId(1L);
        request.setEnvironmentCode("prod");
        request.setCreateRule(true);
        request.setTargetProjectId(9L);
        request.setTargetRuleCode("Target_Exact_Code");
        request.setTargetRuleName("Target rule");
        request.setTargetModelType("TABLE");
        request.setBindings(Map.of("BINDING:VARIABLE:7", 501L));

        ArtifactDeployment deployment = service.deploy(request, "alice");

        Assert.assertEquals(Long.valueOf(91L), deployment.getTargetDefinitionId());
        Assert.assertEquals("Target_Exact_Code", service.createdDefinition.getRuleCode());
        Assert.assertEquals(0, service.ruleCodeLookupCount);
    }

    @Test
    public void artifactAndDeploymentAuditViewsExcludeArchiveBytesAndExposeBindings() {
        FixtureService service = new FixtureService();
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(8L);
        artifact.setArtifactDigest("digest");
        artifact.setPackageContent(new byte[]{1, 2, 3});
        service.artifacts.put(8L, artifact);
        DecisionArtifactComponent component = new DecisionArtifactComponent();
        component.setArtifactId(8L);
        component.setComponentId("VARIABLE:7");
        service.auditComponents.add(component);
        ArtifactDeployment deployment = new ArtifactDeployment();
        deployment.setId(12L);
        deployment.setArtifactId(8L);
        service.auditDeployments.add(deployment);
        ArtifactResourceBinding binding = new ArtifactResourceBinding();
        binding.setDeploymentId(12L);
        binding.setComponentId("BINDING:VARIABLE:7");
        binding.setTargetResourceId(501L);
        service.auditBindings.add(binding);

        Map<String, Object> detail = service.describeArtifact(8L);

        Assert.assertEquals("digest", detail.get("artifactDigest"));
        Assert.assertFalse(detail.containsKey("packageContent"));
        Assert.assertEquals(1, ((List<?>) detail.get("components")).size());
        Assert.assertEquals(1, service.listDeployments(8L).size());
        Assert.assertEquals(Long.valueOf(501L), service.listBindings(12L).get(0).getTargetResourceId());
    }

    private static FixtureService importedFixture() {
        FixtureService service = new FixtureService();
        byte[] bytes = portableArtifact();
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(1L);
        artifact.setArtifactDigest(new DecisionArtifactPackageCodec().decode(bytes).getArtifactDigest());
        artifact.setPackageDigest(Sha256Digests.bytes(bytes));
        artifact.setPackageContent(bytes);
        service.artifacts.put(1L, artifact);
        RuleDefinition target = new RuleDefinition();
        target.setId(90L);
        target.setProjectId(9L);
        service.definitions.put(90L, target);
        return service;
    }

    private static byte[] portableArtifact() {
        DecisionArtifactPackage artifact = new DecisionArtifactPackage();
        artifact.putMetadata("javaMajor", 17);
        artifact.putMetadata("qlExpressVersion", "4.1.0");
        artifact.addComponent("rule/model.json", "application/json", "{}".getBytes(StandardCharsets.UTF_8));
        artifact.addComponent("runtime/compiled.ql", "text/plain",
                "return true;".getBytes(StandardCharsets.UTF_8));
        artifact.addComponent("bindings/variables/7.json", "application/json",
                "{}".getBytes(StandardCharsets.UTF_8), Map.of(
                        "componentId", "BINDING:VARIABLE:7",
                        "resourceType", "BINDING",
                        "embeddingMode", "EXPLICIT_BINDING",
                        "targetResourceType", "VARIABLE"));
        return new DecisionArtifactPackageCodec().encode(artifact);
    }

    private static final class FixtureService extends ArtifactDeploymentService {
        private final Map<Long, DecisionArtifact> artifacts = new HashMap<>();
        private final Map<String, DecisionArtifact> byDigest = new HashMap<>();
        private final Map<Long, RuleDefinition> definitions = new HashMap<>();
        private final List<ArtifactResourceBinding> bindings = new ArrayList<>();
        private final List<DecisionArtifactComponent> auditComponents = new ArrayList<>();
        private final List<ArtifactDeployment> auditDeployments = new ArrayList<>();
        private final List<ArtifactResourceBinding> auditBindings = new ArrayList<>();
        private DecisionArtifact insertedArtifact;
        private int artifactInsertCount;
        private boolean compatible = true;
        private RuleDefinition createdDefinition;
        private int ruleCodeLookupCount;
        private long deploymentId = 1L;

        @Override
        protected RuntimeCompatibilityService.CompatibilityReport compatibility(
                DecisionArtifactPackage artifactPackage) {
            return compatible ? RuntimeCompatibilityService.CompatibilityReport.compatible()
                    : RuntimeCompatibilityService.CompatibilityReport.incompatible("java incompatible");
        }

        @Override
        protected DecisionArtifact findArtifactByDigest(String digest) {
            return byDigest.get(digest);
        }

        @Override
        protected DecisionArtifact loadArtifact(Long artifactId) {
            return artifacts.get(artifactId);
        }

        @Override
        protected void insertArtifact(DecisionArtifact artifact) {
            artifactInsertCount++;
            artifact.setId(1L);
            insertedArtifact = artifact;
        }

        @Override
        protected void insertComponent(DecisionArtifactComponent component) {
        }

        @Override
        protected RuleDefinition loadDefinition(Long definitionId) {
            return definitions.get(definitionId);
        }

        @Override
        protected Long createTargetDefinition(ArtifactDeployRequest request) {
            createdDefinition = new RuleDefinition();
            createdDefinition.setId(91L);
            createdDefinition.setRuleCode(request.getTargetRuleCode());
            createdDefinition.setRuleName(request.getTargetRuleName());
            definitions.put(91L, createdDefinition);
            return 91L;
        }

        @Override
        protected void validateTargetBinding(String targetResourceType, Long targetResourceId,
                                             Long targetProjectId) {
            if (!Long.valueOf(501L).equals(targetResourceId)) {
                throw new IllegalArgumentException("binding target missing");
            }
        }

        @Override
        protected void activateImportedArtifact(Long artifactId, Long definitionId, String actor) {
        }

        @Override
        protected void insertDeployment(ArtifactDeployment deployment) {
            deployment.setId(deploymentId++);
        }

        @Override
        protected void insertBinding(ArtifactResourceBinding binding) {
            bindings.add(binding);
        }

        @Override
        protected List<DecisionArtifactComponent> loadComponents(Long artifactId) {
            return auditComponents;
        }

        @Override
        protected List<ArtifactDeployment> loadDeployments(Long artifactId) {
            return auditDeployments;
        }

        @Override
        protected List<ArtifactResourceBinding> loadBindings(Long deploymentId) {
            return auditBindings;
        }
    }
}
