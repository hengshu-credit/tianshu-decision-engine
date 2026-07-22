package com.hengshucredit.rule.server.artifact;

import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.DecisionArtifactComponent;
import com.hengshucredit.rule.model.entity.RuleRevision;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DecisionArtifactServiceTest {

    @Test
    public void approvedArtifactContainsFrozenRuleSchemasAndDependencies() {
        FixtureService service = new FixtureService();
        byte[] dependencyContent = "{\"varCode\":\"Customer_ID\"}"
                .getBytes(StandardCharsets.UTF_8);
        service.closure = RuleDependencyClosureService.DependencyClosure.of(
                Collections.singletonList(new ArtifactDependency("VARIABLE:7", "VARIABLE", 7L,
                        null, "variables/7.json", "application/json", "EMBEDDED",
                        Sha256Digests.bytes(dependencyContent), dependencyContent, Map.of())),
                Collections.emptyList());

        DecisionArtifact artifact = service.buildApprovedArtifact(200L, "alice");

        Assert.assertEquals(Long.valueOf(1L), artifact.getId());
        Assert.assertEquals(64, artifact.getArtifactDigest().length());
        Assert.assertEquals(64, artifact.getPackageDigest().length());
        DecisionArtifactPackageCodec.DecodedPackage decoded =
                new DecisionArtifactPackageCodec().decode(artifact.getPackageContent());
        Assert.assertEquals(artifact.getArtifactDigest(), decoded.getArtifactDigest());
        Assert.assertNotNull(decoded.getArtifactPackage().getComponent("rule/model.json"));
        Assert.assertNotNull(decoded.getArtifactPackage().getComponent("rule/compiled.ql"));
        Assert.assertNotNull(decoded.getArtifactPackage().getComponent("runtime/compiled.ql"));
        Assert.assertNotNull(decoded.getArtifactPackage().getComponent("schemas/input.schema.json"));
        Assert.assertNotNull(decoded.getArtifactPackage().getComponent("variables/7.json"));
        Assert.assertNotNull(decoded.getArtifactPackage().getComponent("validation/report.json"));
        Assert.assertNotNull(decoded.getArtifactPackage().getComponent("rule/open-api.json"));
        Assert.assertEquals(8, service.components.size());
    }

    @Test
    public void sameContentDigestReusesExistingImmutableArtifact() {
        FixtureService service = new FixtureService();

        DecisionArtifact first = service.buildApprovedArtifact(200L, "alice");
        service.existing = first;
        DecisionArtifact second = service.buildApprovedArtifact(200L, "bob");

        Assert.assertSame(first, second);
        Assert.assertEquals(1, service.insertCount);
    }

    @Test
    public void exportedBindingCarriesTargetTypeRequiredByRealDeployment() {
        FixtureService service = new FixtureService();
        byte[] bindingContent = CanonicalJson.writeBytes(Map.of(
                "sourceComponentId", "VARIABLE:7",
                "targetResourceType", "DB_DATASOURCE"));
        service.closure = RuleDependencyClosureService.DependencyClosure.of(
                List.of(new ArtifactDependency("BINDING:VARIABLE:7", "BINDING", 7L,
                        null, "bindings/variables/7.json", "application/json", "EXPLICIT_BINDING",
                        Sha256Digests.bytes(bindingContent), bindingContent, Map.of())),
                Collections.emptyList());

        DecisionArtifact artifact = service.buildApprovedArtifact(200L, "alice");

        DecisionArtifactPackage.Component binding = new DecisionArtifactPackageCodec()
                .decode(artifact.getPackageContent()).getArtifactPackage()
                .getComponent("bindings/variables/7.json");
        Assert.assertEquals("DB_DATASOURCE", binding.getMetadata().get("targetResourceType"));
    }

    private static final class FixtureService extends DecisionArtifactService {
        private final RuleRevision revision = new RuleRevision();
        private final RulePreflightReport report = new RulePreflightReport();
        private RuleDependencyClosureService.DependencyClosure closure =
                RuleDependencyClosureService.DependencyClosure.of(Collections.emptyList(), Collections.emptyList());
        private DecisionArtifact existing;
        private int insertCount;
        private final List<DecisionArtifactComponent> components = new ArrayList<>();

        private FixtureService() {
            revision.setId(200L);
            revision.setDefinitionId(100L);
            revision.setState("REVIEW");
            revision.setModelJson("{\"type\":\"TABLE\"}");
            revision.setOpenApiConfigJson("{\"enabled\":false}");
            report.setRevisionId(200L);
            report.setValid(true);
            report.setCompiledScript("return true;");
            report.setCompiledType("QLEXPRESS");
            report.setInputSchemaJson("{\"type\":\"object\"}");
            report.setOutputSchemaJson("{\"type\":\"object\"}");
            report.setSchemaCompatibilityJson("{\"changes\":[]}");
            report.setContentDigest(digest('c'));
            report.setDependencyDigest(digest('d'));
        }

        @Override
        protected RuleRevision loadRevision(Long revisionId) {
            return revision;
        }

        @Override
        protected RulePreflightReport preflight(Long revisionId) {
            return report;
        }

        @Override
        protected RuleDependencyClosureService.DependencyClosure resolveDependencies(
                Long definitionId, Long revisionId) {
            return closure;
        }

        @Override
        protected DecisionArtifact findByDigest(String artifactDigest) {
            return existing != null && artifactDigest.equals(existing.getArtifactDigest()) ? existing : null;
        }

        @Override
        protected void insertArtifact(DecisionArtifact artifact) {
            insertCount++;
            artifact.setId(1L);
        }

        @Override
        protected void insertComponent(DecisionArtifactComponent component) {
            components.add(component);
        }
    }

    private static String digest(char value) {
        return String.valueOf(value).repeat(64);
    }
}
