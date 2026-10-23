package com.hengshucredit.rule.server.artifact;

import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.DecisionArtifactComponent;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.service.RuleFieldAnalyzer;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DecisionArtifactServiceTest {
    @Test public void approvalReusesItsExactAnalysisAndRejectsChangedContent() {
        FixtureService service = new FixtureService();
        var analysis = service.analyzeRevision(200L);
        service.revision.setContentDigest(analysis.report().getContentDigest());
        DecisionArtifact artifact = service.buildApprovedArtifact(200L, "alice", analysis);
        Assert.assertNotNull(artifact.getPackageContent());
        Assert.assertEquals(1, service.revisionFieldResolveCount);
        Assert.assertEquals(1, service.revisionDependencyResolveCount);
        service.revision.setModelJson("{\"script\":\"changed\"}");
        Assert.assertThrows(IllegalStateException.class, () -> service.buildApprovedArtifact(200L, "alice", analysis));
        Assert.assertEquals(1, service.insertCount);
    }

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
        Assert.assertNotNull(decoded.getArtifactPackage().getComponent("rule/input-fields.json"));
        Assert.assertNotNull(decoded.getArtifactPackage().getComponent("rule/output-fields.json"));
        Assert.assertNotNull(decoded.getArtifactPackage().getComponent("rule/field-resolution.json"));
        Assert.assertNotNull(decoded.getArtifactPackage().getComponent("variables/7.json"));
        Assert.assertNotNull(decoded.getArtifactPackage().getComponent("validation/report.json"));
        Assert.assertNotNull(decoded.getArtifactPackage().getComponent("rule/open-api.json"));
        Assert.assertEquals(11, service.components.size());
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

    @Test
    public void approvedArtifactUsesOnlyFieldsResolvedFromItsRevision() {
        FixtureService service = new FixtureService();
        service.trackRevisionFields = true;
        service.resolved = resolvedWithLocalOutput("revision_input", "credit_score_v1");

        DecisionArtifact first = service.buildApprovedArtifact(200L, "alice");
        service.existing = first;
        DecisionArtifact second = service.buildApprovedArtifact(200L, "bob");

        Assert.assertSame(first, second);
        Assert.assertEquals(0, service.persistedInputLoadCount);
        Assert.assertEquals(0, service.persistedOutputLoadCount);
        Assert.assertEquals(0, service.legacyDependencyResolveCount);
        Assert.assertEquals(2, service.revisionFieldResolveCount);
        Assert.assertEquals(2, service.revisionDependencyResolveCount);
        Assert.assertSame(service.resolved, service.lastDependencyFields);
        DecisionArtifactPackage artifactPackage = new DecisionArtifactPackageCodec()
                .decode(first.getPackageContent()).getArtifactPackage();
        String inputFields = componentText(artifactPackage, "rule/input-fields.json");
        String outputFields = componentText(artifactPackage, "rule/output-fields.json");
        Assert.assertTrue(inputFields.contains("revision_input"));
        Assert.assertTrue(outputFields.contains("credit_score_v1"));
        Assert.assertFalse(inputFields.contains("stale_projection"));
        Assert.assertFalse(outputFields.contains("stale_projection"));
        Assert.assertEquals(first.getArtifactDigest(), second.getArtifactDigest());
        Assert.assertEquals(first.getPackageDigest(), second.getPackageDigest());
        Assert.assertEquals(1, service.insertCount);
    }

    @Test
    public void approvedArtifactFieldSnapshotsRoundTripWithIsoDateTimes() {
        FixtureService service = new FixtureService();
        RuleDefinitionInputField input = input("frozen_input");
        RuleDefinitionOutputField output = localOutput("frozen_output");
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 25, 9, 10, 11);
        input.setCreateTime(createdAt);
        output.setCreateTime(createdAt);
        service.resolved = new RuleFieldAnalyzer.ResolvedFields(
                Collections.singletonList(input),
                Collections.singletonList(output),
                Collections.emptyList(), Collections.singleton("frozen_output"),
                Collections.emptyMap(), Collections.emptyMap());

        DecisionArtifact artifact = service.buildApprovedArtifact(200L, "alice");
        RulePublished published = new RulePublished();
        published.setDefinitionId(100L);
        published.setRevisionId(200L);
        published.setArtifactId(artifact.getId());
        published.setArtifactDigest(artifact.getArtifactDigest());
        published.setModelType("SCRIPT");
        published.setStatus(1);
        PublishedRuleFieldSnapshotResolver resolver =
                new PublishedRuleFieldSnapshotResolver() {
                    @Override
                    protected RulePublished loadPublishedRule(Long definitionId) {
                        return published;
                    }

                    @Override
                    protected DecisionArtifact loadArtifact(Long artifactId) {
                        return artifact;
                    }
                };

        RuleFieldAnalyzer.ResolvedFields frozen = resolver.resolve(100L);

        Assert.assertTrue(frozen.getDiagnostics().toString(),
                frozen.getDiagnostics().isEmpty());
        Assert.assertEquals(createdAt, frozen.getInputFields().get(0).getCreateTime());
        Assert.assertEquals(createdAt, frozen.getOutputFields().get(0).getCreateTime());
        String inputJson = componentText(new DecisionArtifactPackageCodec()
                .decode(artifact.getPackageContent()).getArtifactPackage(),
                "rule/input-fields.json");
        Assert.assertTrue(inputJson, inputJson.contains("\"createTime\":\"2026-08-25T09:10:11\""));
        Assert.assertFalse(inputJson, inputJson.matches(".*\\\"createTime\\\":\\d+.*"));
    }

    private static RuleFieldAnalyzer.ResolvedFields resolvedWithLocalOutput(
            String inputName, String outputName) {
        return new RuleFieldAnalyzer.ResolvedFields(
                Collections.singletonList(input(inputName)),
                Collections.singletonList(localOutput(outputName)),
                Collections.emptyList(), Collections.singleton(outputName),
                Collections.emptyMap(), Collections.emptyMap());
    }

    private static RuleDefinitionInputField input(String name) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setFieldName(name);
        field.setScriptName(name);
        field.setFieldType("STRING");
        field.setVarId(7L);
        field.setRefType("VARIABLE");
        field.setStatus(1);
        return field;
    }

    private static RuleDefinitionOutputField localOutput(String name) {
        RuleDefinitionOutputField field = new RuleDefinitionOutputField();
        field.setFieldName(name);
        field.setScriptName(name);
        field.setFieldType("NUMBER");
        field.setStatus(1);
        return field;
    }

    private static String componentText(DecisionArtifactPackage artifactPackage, String path) {
        DecisionArtifactPackage.Component component = artifactPackage.getComponent(path);
        Assert.assertNotNull(component);
        return new String(component.getContent(), StandardCharsets.UTF_8);
    }

    private static final class FixtureService extends DecisionArtifactService {
        private final RuleRevision revision = new RuleRevision();
        private final RuleDefinition definition = new RuleDefinition();
        private final RulePreflightReport report = new RulePreflightReport();
        private RuleDependencyClosureService.DependencyClosure closure =
                RuleDependencyClosureService.DependencyClosure.of(Collections.emptyList(), Collections.emptyList());
        private RuleFieldAnalyzer.ResolvedFields resolved =
                new RuleFieldAnalyzer.ResolvedFields(Collections.emptyList(), Collections.emptyList());
        private DecisionArtifact existing;
        private int insertCount;
        private boolean trackRevisionFields;
        private int persistedInputLoadCount;
        private int persistedOutputLoadCount;
        private int legacyDependencyResolveCount;
        private int revisionFieldResolveCount;
        private int revisionDependencyResolveCount;
        private RuleFieldAnalyzer.ResolvedFields lastDependencyFields;
        private final List<DecisionArtifactComponent> components = new ArrayList<>();

        private FixtureService() {
            revision.setId(200L);
            revision.setDefinitionId(100L);
            revision.setState("REVIEW");
            revision.setModelJson("{\"type\":\"TABLE\"}");
            revision.setOpenApiConfigJson("{\"enabled\":false}");
            definition.setId(100L);
            definition.setProjectId(9L);
            definition.setModelType("SCRIPT");
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
        protected RuleDefinition loadDefinition(Long definitionId) {
            return definition;
        }

        @Override
        protected RulePreflightValidationService.Analysis analyzeRevision(Long revisionId) {
            var fields = resolveFields(definition, revision);
            return new RulePreflightValidationService.Analysis(preflight(revisionId), fields,
                    resolveDependencies(definition.getId(), revisionId, fields), Sha256Digests.text(revision.getModelJson()));
        }

        private RuleFieldAnalyzer.ResolvedFields resolveFields(
                RuleDefinition currentDefinition, RuleRevision currentRevision) {
            revisionFieldResolveCount++;
            return resolved;
        }

        private RulePreflightReport preflight(Long revisionId) {
            return report;
        }

        @Override
        protected RuleDependencyClosureService.DependencyClosure resolveDependencies(
                Long definitionId, Long revisionId) {
            legacyDependencyResolveCount++;
            return closure;
        }

        private RuleDependencyClosureService.DependencyClosure resolveDependencies(
                Long definitionId, Long revisionId, RuleFieldAnalyzer.ResolvedFields fields) {
            revisionDependencyResolveCount++;
            lastDependencyFields = fields;
            if (trackRevisionFields && (fields.getOutputFields().isEmpty()
                    || !fields.isLocalOutput(fields.getOutputFields().get(0)))) {
                return RuleDependencyClosureService.DependencyClosure.of(
                        Collections.emptyList(), Collections.singletonList(
                                com.hengshucredit.rule.model.dto.RuleValidationIssue.error(
                                        "MISSING_REFERENCE_ID", "output", "局部输出未被识别")));
            }
            return closure;
        }

        @Override
        protected List<RuleDefinitionInputField> loadInputFields(Long definitionId) {
            persistedInputLoadCount++;
            return trackRevisionFields
                    ? Collections.singletonList(input("stale_projection"))
                    : Collections.emptyList();
        }

        @Override
        protected List<RuleDefinitionOutputField> loadOutputFields(Long definitionId) {
            persistedOutputLoadCount++;
            return trackRevisionFields
                    ? Collections.singletonList(localOutput("stale_projection"))
                    : Collections.emptyList();
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
