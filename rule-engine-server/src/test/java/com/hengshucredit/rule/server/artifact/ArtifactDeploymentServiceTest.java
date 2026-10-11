package com.hengshucredit.rule.server.artifact;

import com.hengshucredit.rule.model.dto.ArtifactDeployRequest;
import com.hengshucredit.rule.model.dto.ArtifactImportResult;
import com.hengshucredit.rule.model.entity.ArtifactDeployment;
import com.hengshucredit.rule.model.entity.ArtifactResourceBinding;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.DecisionArtifactComponent;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleProject;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArtifactDeploymentServiceTest {

    @Test public void deletedTargetCannotBeRevivedByArtifactDeployment() {
        FixtureService service = importedFixture();
        service.definitions.get(90L).setStatus(-1);
        IllegalArgumentException error = Assert.assertThrows(IllegalArgumentException.class,
                () -> service.deploy(existingRequest(9L), "alice"));
        Assert.assertTrue(error.getMessage().contains("删除"));
        assertNoDeploymentWrites(service);
    }

    @Test public void explicitProjectMustOwnTheSelectedExistingTarget() {
        for (long requestedProject : new long[]{0L, 10L}) {
            FixtureService service = importedFixture();
            service.projects.put(10L, project(10L, 1));
            IllegalArgumentException error = Assert.assertThrows(IllegalArgumentException.class,
                    () -> service.deploy(existingRequest(requestedProject), "alice"));
            Assert.assertTrue(error.getMessage().contains("项目"));
            assertNoDeploymentWrites(service);
        }
    }

    @Test public void createRejectsMissingDeletedAndNegativeProjectsBeforeCreatingRule() {
        for (long targetProject : new long[]{99L, 9L, -1L}) {
            FixtureService service = importedFixture();
            service.projects.get(9L).setStatus(-1);
            ArtifactDeployRequest request = createRequest();
            request.setTargetProjectId(targetProject);
            IllegalArgumentException error = Assert.assertThrows(IllegalArgumentException.class,
                    () -> service.deploy(request, "alice"));
            Assert.assertTrue(error.getMessage().contains("项目"));
            assertNoDeploymentWrites(service);
        }
    }

    @Test public void existingTargetCannotDeployIntoItsMissingOrDeletedProject() {
        for (boolean missing : new boolean[]{true, false}) {
            FixtureService service = importedFixture();
            if (missing) service.projects.remove(9L); else service.projects.get(9L).setStatus(-1);
            // 兼容旧客户端未传targetProjectId时，也必须核查规则自身归属。
            IllegalArgumentException error = Assert.assertThrows(IllegalArgumentException.class,
                    () -> service.deploy(existingRequest(null), "alice"));
            Assert.assertTrue(error.getMessage().contains("项目"));
            assertNoDeploymentWrites(service);
        }
    }

    @Test public void existingTargetIdMustExistBeforeAnyDeploymentWrite() {
        FixtureService service = importedFixture();
        service.definitions.remove(90L);
        Assert.assertThrows(IllegalArgumentException.class,
                () -> service.deploy(existingRequest(9L), "alice"));
        assertNoDeploymentWrites(service);
    }

    @Test public void globalZeroIsExplicitAndDoesNotRequireAProjectZeroRecord() {
        FixtureService service = importedFixture();
        service.projects.clear();
        RuleDefinition target = service.definitions.get(90L);
        target.setProjectId(0L); target.setScope("GLOBAL"); target.setStatus(0);
        service.deploy(existingRequest(0L), "alice");
        Assert.assertEquals(Long.valueOf(0L), service.lastBindingProjectId);
        ArtifactDeployRequest create = createRequest(); create.setTargetProjectId(0L);
        service.deploy(create, "alice");
        Assert.assertEquals(Long.valueOf(0L), service.createdDefinition.getProjectId());
        Assert.assertEquals(2, service.activationCount);
    }

    @Test public void legacyExplicitGlobalTargetWithNullProjectUsesGlobalBindingContext() {
        FixtureService service = importedFixture();
        service.projects.clear();
        RuleDefinition target = service.definitions.get(90L);
        target.setScope("GLOBAL"); target.setProjectId(null);
        service.deploy(existingRequest(0L), "alice");
        Assert.assertEquals(Long.valueOf(0L), service.lastBindingProjectId);
    }

    @Test public void projectScopedTargetWithoutOwnerIsNotImplicitlyGlobal() {
        FixtureService service = importedFixture();
        RuleDefinition target = service.definitions.get(90L);
        target.setScope("PROJECT"); target.setProjectId(null);
        Assert.assertThrows(IllegalArgumentException.class,
                () -> service.deploy(existingRequest(null), "alice"));
        assertNoDeploymentWrites(service);
    }

    @Test public void disabledProjectAndUnpublishedOrOfflineTargetRemainDeployable() {
        FixtureService service = importedFixture();
        service.projects.get(9L).setStatus(0);
        service.definitions.get(90L).setStatus(0);
        Assert.assertEquals(Long.valueOf(90L), service.deploy(existingRequest(9L), "alice").getTargetDefinitionId());
        Assert.assertEquals(Long.valueOf(91L), service.deploy(createRequest(), "alice").getTargetDefinitionId());
        Assert.assertEquals(2, service.activationCount);
    }

    private static void assertNoDeploymentWrites(FixtureService service) {
        Assert.assertNull("目标校验失败不能创建规则", service.createdDefinition);
        Assert.assertEquals("不能写入部署记录", 1L, service.deploymentId);
        Assert.assertTrue("不能写入绑定记录", service.bindings.isEmpty());
        Assert.assertEquals("不能激活制品", 0, service.activationCount);
    }

    private static RuleProject project(long id, int status) {
        RuleProject project = new RuleProject(); project.setId(id); project.setStatus(status);
        return project;
    }

    private static ArtifactDeployRequest existingRequest(Long targetProjectId) {
        ArtifactDeployRequest request = new ArtifactDeployRequest();
        request.setArtifactId(1L); request.setEnvironmentCode("uat"); request.setTargetDefinitionId(90L);
        request.setTargetProjectId(targetProjectId); request.setBindings(Map.of("BINDING:VARIABLE:7", 501L));
        return request;
    }

    @Test public void newTargetUsesPackageModelTypeWithoutRequiringTechnicalInput() {
        FixtureService service = importedFixture();
        ArtifactDeployRequest request = createRequest();
        ArtifactDeployment deployment = service.deploy(request, "alice");
        Assert.assertEquals(Long.valueOf(91L), deployment.getTargetDefinitionId());
        Assert.assertEquals("TABLE", service.createdDefinition.getModelType());
        Assert.assertNull("派生元数据不应修改调用方原请求", request.getTargetModelType());
    }

    @Test public void mismatchedRequestedModelTypeIsRejectedBeforeCreatingTarget() {
        FixtureService service = importedFixture();
        ArtifactDeployRequest request = createRequest(); request.setTargetModelType("SCORE");
        Assert.assertThrows(IllegalArgumentException.class, () -> service.deploy(request, "alice"));
        Assert.assertNull(service.createdDefinition); Assert.assertTrue(service.bindings.isEmpty());
    }

    @Test public void existingTargetWithDifferentModelTypeIsRejectedBeforeDeployment() {
        FixtureService service = importedFixture(); service.definitions.get(90L).setModelType("SCORE");
        ArtifactDeployRequest request = new ArtifactDeployRequest();
        request.setArtifactId(1L); request.setEnvironmentCode("test"); request.setTargetDefinitionId(90L);
        request.setBindings(Map.of("BINDING:VARIABLE:7", 501L));
        Assert.assertThrows(IllegalArgumentException.class, () -> service.deploy(request, "alice"));
        Assert.assertEquals(1L, service.deploymentId); Assert.assertTrue(service.bindings.isEmpty());
    }

    @Test public void importReceiptAndDeploymentOptionsExposeTheSamePackageModelType() {
        FixtureService service = new FixtureService();
        ArtifactImportResult result = service.importArtifact(portableArtifact(), null, "alice");
        Assert.assertEquals("TABLE", com.alibaba.fastjson.JSON.parseObject(com.alibaba.fastjson.JSON.toJSONString(result)).getString("modelType"));
        service.artifacts.put(result.getArtifactId(), service.insertedArtifact);
        Assert.assertEquals("TABLE", service.deploymentOptions(result.getArtifactId(), 9L).get("modelType"));
    }

    @Test public void deploymentOptionsDoNotExposeMetadataFromMismatchedStoredPackage() {
        FixtureService service = importedFixture();
        service.artifacts.get(1L).setPackageDigest("changed");
        Assert.assertThrows(IllegalArgumentException.class, () -> service.deploymentOptions(1L, 9L));
    }

    @Test public void missingOrUnsupportedPackageModelTypeCannotBeGuessedByCaller() {
        for (String type : new String[]{null, "SCORECARD", "UNKNOWN"}) {
            FixtureService service = importedFixture();
            DecisionArtifact artifact = service.artifacts.get(1L);
            byte[] content = portableArtifact(type);
            artifact.setPackageContent(content); artifact.setPackageDigest(Sha256Digests.bytes(content));
            artifact.setArtifactDigest(new DecisionArtifactPackageCodec().decode(content).getArtifactDigest());
            ArtifactDeployRequest request = createRequest(); request.setTargetModelType("TABLE");
            Assert.assertThrows(IllegalArgumentException.class, () -> service.deploy(request, "alice"));
            Assert.assertNull(service.createdDefinition);
        }
    }

    @Test public void nonCanonicalPackageTypesAreRejectedBeforeImportPersistence() {
        for (String type : new String[]{" FLOW ", " SCRIPT ", "flow", "script", "Flow", "SCRIPT\n"}) {
            byte[] content = portableArtifact(type);
            FixtureService importer = new FixtureService();
            Assert.assertThrows(type + " import", IllegalArgumentException.class,
                    () -> importer.importArtifact(content, null, "alice"));
            Assert.assertEquals("不应持久化非规范制品", 0, importer.artifactInsertCount);
        }
    }

    @Test public void nonCanonicalPackageTypesAreRejectedByDeploymentOptions() {
        for (String type : new String[]{" FLOW ", " SCRIPT ", "flow", "script", "Flow", "SCRIPT\n"}) {
            FixtureService service = importedFixture();
            DecisionArtifact artifact = service.artifacts.get(1L);
            byte[] content = portableArtifact(type);
            artifact.setPackageContent(content); artifact.setPackageDigest(Sha256Digests.bytes(content));
            artifact.setArtifactDigest(new DecisionArtifactPackageCodec().decode(content).getArtifactDigest());
            Assert.assertThrows(type + " options", IllegalArgumentException.class,
                    () -> service.deploymentOptions(1L, 9L));
        }
    }

    @Test public void nonCanonicalPackageTypesAreRejectedBeforeTargetCreation() {
        for (String type : new String[]{" FLOW ", " SCRIPT ", "flow", "script", "Flow", "SCRIPT\n"}) {
            FixtureService service = importedFixture();
            DecisionArtifact artifact = service.artifacts.get(1L);
            byte[] content = portableArtifact(type);
            artifact.setPackageContent(content); artifact.setPackageDigest(Sha256Digests.bytes(content));
            artifact.setArtifactDigest(new DecisionArtifactPackageCodec().decode(content).getArtifactDigest());
            Assert.assertThrows(type + " deploy", IllegalArgumentException.class,
                    () -> service.deploy(createRequest(), "alice"));
            Assert.assertNull("部署前不得创建目标", service.createdDefinition);
            Assert.assertEquals(1L, service.deploymentId);
            Assert.assertTrue(service.bindings.isEmpty());
        }
    }

    @Test public void canonicalPackageTypesRemainUnchangedThroughImportAndAutomaticDeployment() {
        for (String type : new String[]{"FLOW", "SCRIPT"}) {
            FixtureService service = importedFixture();
            byte[] content = portableArtifact(type);
            DecisionArtifact artifact = service.artifacts.get(1L);
            artifact.setPackageContent(content); artifact.setPackageDigest(Sha256Digests.bytes(content));
            artifact.setArtifactDigest(new DecisionArtifactPackageCodec().decode(content).getArtifactDigest());
            Assert.assertEquals(type, service.importArtifact(content, null, "alice").getModelType());
            Assert.assertEquals(type, service.deploymentOptions(1L, 9L).get("modelType"));
            service.deploy(createRequest(), "alice");
            Assert.assertEquals(type, service.createdDefinition.getModelType());
            Assert.assertArrayEquals(content, artifact.getPackageContent());
        }
    }

    private static ArtifactDeployRequest createRequest() {
        ArtifactDeployRequest request = new ArtifactDeployRequest();
        request.setArtifactId(1L); request.setEnvironmentCode("test"); request.setCreateRule(true);
        request.setTargetProjectId(9L); request.setTargetRuleCode("Exact_User_Code"); request.setTargetRuleName("目标规则");
        request.setBindings(Map.of("BINDING:VARIABLE:7", 501L));
        return request;
    }

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
        target.setModelType("TABLE");
        service.definitions.put(90L, target);
        service.projects.put(9L, project(9L, 1));
        return service;
    }

    private static byte[] portableArtifact() {
        return portableArtifact("TABLE");
    }

    private static byte[] portableArtifact(String modelType) {
        DecisionArtifactPackage artifact = new DecisionArtifactPackage();
        if (modelType != null) artifact.putMetadata("modelType", modelType);
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
        private final Map<Long, RuleProject> projects = new HashMap<>();
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
        private int activationCount;
        private Long lastBindingProjectId;

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
        protected RuleProject loadProject(Long projectId) {
            return projects.get(projectId);
        }

        @Override
        protected Long createTargetDefinition(ArtifactDeployRequest request) {
            createdDefinition = new RuleDefinition();
            createdDefinition.setId(91L);
            createdDefinition.setRuleCode(request.getTargetRuleCode());
            createdDefinition.setRuleName(request.getTargetRuleName());
            createdDefinition.setModelType(request.getTargetModelType());
            createdDefinition.setProjectId(request.getTargetProjectId());
            definitions.put(91L, createdDefinition);
            return 91L;
        }

        @Override
        protected void validateTargetBinding(String targetResourceType, Long targetResourceId,
                                             Long targetProjectId) {
            lastBindingProjectId = targetProjectId;
            if (!Long.valueOf(501L).equals(targetResourceId)) {
                throw new IllegalArgumentException("binding target missing");
            }
        }

        @Override
        protected void activateImportedArtifact(Long artifactId, Long definitionId, String actor) {
            activationCount++;
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
