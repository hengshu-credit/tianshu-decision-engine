package com.hengshucredit.rule.server.artifact;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.ArtifactDeployment;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.service.RuleFieldAnalyzer;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class PublishedRuleFieldSnapshotResolverTest {

    @Test public void importedPackageResolvesAgainstExplicitTargetRevisionWithoutRewritingSourceIds() {
        FixtureResolver resolver = importedResolver(false);
        byte[] original = resolver.artifact.getPackageContent().clone();
        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(resolver.published);
        Assert.assertTrue(fields.getDiagnostics().toString(), fields.getDiagnostics().isEmpty());
        Assert.assertEquals(Long.valueOf(7), fields.getInputFields().get(0).getVarId());
        Assert.assertEquals("frozen_input", fields.getInputFields().get(0).getScriptName());
        Assert.assertTrue(fields.getLocalOutputNames().contains("frozen_output"));
        Assert.assertNull(resolver.artifact.getDefinitionId());
        Assert.assertNull(resolver.artifact.getRevisionId());
        Assert.assertArrayEquals(original, resolver.artifact.getPackageContent());
    }

    @Test public void deduplicatedLocalArtifactCanBeExplicitlyDeployedToAnotherDefinition() {
        FixtureResolver resolver = importedResolver(true);
        Assert.assertTrue(resolver.resolve(resolver.published).getDiagnostics().toString(),
                resolver.resolve(resolver.published).getDiagnostics().isEmpty());
        Assert.assertEquals(Long.valueOf(101), resolver.artifact.getDefinitionId());
        Assert.assertEquals(Long.valueOf(301), resolver.artifact.getRevisionId());
    }

    @Test public void historicalImportedRevisionKeepsItsFrozenFieldContract() {
        FixtureResolver resolver = importedResolver(false);
        resolver.revision.setState("OFFLINE");
        Assert.assertTrue(resolver.resolve(resolver.published).getDiagnostics().isEmpty());
    }

    @Test public void importedIdsCoincidingWithTargetStillNeedDeploymentAndRevisionProof() {
        FixtureResolver resolver = importedResolver(false);
        resolver.published.setDefinitionId(101L); resolver.published.setRevisionId(301L);
        resolver.deployment.setTargetDefinitionId(101L);
        resolver.revision.setDefinitionId(101L); resolver.revision.setId(301L);
        Assert.assertTrue(resolver.resolve(resolver.published).getDiagnostics().isEmpty());
        resolver.deployment = null;
        assertInvalid(resolver.resolve(resolver.published));
    }

    @Test public void importedContractRejectsMissingOrMismatchedDeploymentAndRevision() {
        for (int scenario = 0; scenario < 10; scenario++) {
            FixtureResolver resolver = importedResolver(false);
            switch (scenario) {
                case 0 -> resolver.deployment = null;
                case 1 -> resolver.deployment.setArtifactId(999L);
                case 2 -> resolver.deployment.setTargetDefinitionId(999L);
                case 3 -> resolver.deployment.setStatus("FAILED");
                case 4 -> resolver.revision = null;
                case 5 -> resolver.revision.setId(999L);
                case 6 -> resolver.revision.setDefinitionId(999L);
                case 7 -> resolver.revision.setArtifactId(999L);
                case 8 -> resolver.revision.setContentDigest("wrong");
                case 9 -> resolver.artifact.setRevisionId(301L);
                default -> throw new AssertionError();
            }
            assertInvalid(resolver.resolve(resolver.published));
        }
    }

    @Test public void explicitDeploymentCannotHideMismatchedSourceEntityOrInvalidSourceMetadata() {
        FixtureResolver resolver = importedResolver(true);
        resolver.artifact.setDefinitionId(999L);
        assertInvalid(resolver.resolve(resolver.published));
        FixtureResolver malformed = importedResolver(false);
        malformed.artifact = rawArtifact(malformed.published, 101.5d, 301L, "SCRIPT", "[]", "[]", null);
        malformed.artifact.setDefinitionId(null); malformed.artifact.setRevisionId(null);
        malformed.revision.setContentDigest(malformed.artifact.getArtifactDigest());
        assertInvalid(malformed.resolve(malformed.published));
    }

    @Test public void ordinaryLocalPublicationDoesNotReadDeploymentOrTargetRevision() {
        FixtureResolver resolver = new FixtureResolver(); resolver.published = published(); resolver.artifact = artifact(resolver.published, true);
        Assert.assertTrue(resolver.resolve(resolver.published).getDiagnostics().isEmpty());
        Assert.assertEquals(0, resolver.deploymentReads); Assert.assertEquals(0, resolver.revisionReads);
    }

    private static FixtureResolver importedResolver(boolean reuseLocal) {
        FixtureResolver resolver = new FixtureResolver();
        RulePublished source = published();
        DecisionArtifact built = artifact(source, true);
        if (reuseLocal) resolver.artifact = built;
        else {
            ArtifactDeploymentService importer = new ArtifactDeploymentService() {
                @Override protected RuntimeCompatibilityService.CompatibilityReport compatibility(DecisionArtifactPackage ignored) {
                    return RuntimeCompatibilityService.CompatibilityReport.compatible();
                }
                @Override protected DecisionArtifact findArtifactByDigest(String digest) { return null; }
                @Override protected void insertArtifact(DecisionArtifact value) { value.setId(401L); resolver.artifact = value; }
                @Override protected void insertComponent(com.hengshucredit.rule.model.entity.DecisionArtifactComponent value) { }
            };
            importer.importArtifact(built.getPackageContent(), built.getPackageDigest(), "importer");
        }
        resolver.published = published(); resolver.published.setDefinitionId(900L); resolver.published.setRevisionId(901L);
        resolver.published.setArtifactDigest(resolver.artifact.getArtifactDigest());
        resolver.deployment = new ArtifactDeployment(); resolver.deployment.setId(1001L);
        resolver.deployment.setArtifactId(401L); resolver.deployment.setTargetDefinitionId(900L); resolver.deployment.setStatus("DEPLOYED");
        resolver.revision = new RuleRevision(); resolver.revision.setId(901L); resolver.revision.setDefinitionId(900L);
        resolver.revision.setArtifactId(401L); resolver.revision.setContentDigest(resolver.artifact.getArtifactDigest()); resolver.revision.setState("PUBLISHED");
        return resolver;
    }

    @Test
    public void repeatedReadsKeepFieldObjectsIndependentAndStillVerifyActualBytes() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = artifact(resolver.published, true);
        var first = resolver.resolve(resolver.published);
        first.getInputFields().get(0).setFieldName("caller-mutated");
        Assert.assertEquals("frozen_input", resolver.resolve(resolver.published).getInputFields().get(0).getFieldName());
        String declaredDigest = resolver.artifact.getArtifactDigest();
        resolver.artifact.setArtifactDigest("changed");
        assertInvalid(resolver.resolve(resolver.published));
        resolver.artifact.setArtifactDigest(declaredDigest);
        resolver.artifact.getPackageContent()[0] ^= 1;
        assertInvalid(resolver.resolve(resolver.published));
    }

    @Test
    public void resolvesFieldsOnlyFromValidatedArtifactComponents() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = artifact(resolver.published, true);

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        Assert.assertTrue(fields.getDiagnostics().toString(), fields.getDiagnostics().isEmpty());
        Assert.assertEquals("frozen_input", fields.getInputFields().get(0).getFieldName());
        Assert.assertEquals("frozen_output", fields.getOutputFields().get(0).getFieldName());
        Assert.assertTrue(fields.getLocalOutputNames().contains("frozen_output"));
        Assert.assertEquals("NUMBER", fields.getInputPropertySchemas().get("frozen_input"));
        Assert.assertEquals("SCRIPT", fields.getSnapshotModelType());
    }

    @Test
    public void missingArtifactIsBlockingAndNeverFallsBack() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        Assert.assertTrue(fields.getDiagnostics().stream().anyMatch(issue ->
                "FROZEN_REVISION_FIELD_SNAPSHOT_MISSING".equals(issue.getCode())
                        && Long.valueOf(301L).equals(issue.getRevisionId())));
        Assert.assertTrue(fields.getInputFields().isEmpty());
    }

    @Test
    public void corruptPackageIsBlockingAndNeverFallsBack() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = artifact(resolver.published, true);
        resolver.artifact.setPackageContent(new byte[]{1, 2, 3});

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        Assert.assertTrue(fields.getDiagnostics().stream().anyMatch(issue ->
                "FROZEN_REVISION_FIELD_SNAPSHOT_INVALID".equals(issue.getCode())));
        Assert.assertTrue(fields.getInputFields().isEmpty());
    }

    @Test
    public void missingRequiredFieldComponentIsBlocking() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = artifact(resolver.published, false);

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        Assert.assertTrue(fields.getDiagnostics().stream().anyMatch(issue ->
                "FROZEN_REVISION_FIELD_SNAPSHOT_MISSING".equals(issue.getCode())));
    }

    @Test
    public void manifestMetadataMismatchIsBlocking() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        RulePublished mismatchedManifest = published();
        mismatchedManifest.setRevisionId(302L);
        resolver.artifact = artifact(mismatchedManifest, true);
        resolver.artifact.setDefinitionId(resolver.published.getDefinitionId());
        resolver.artifact.setRevisionId(resolver.published.getRevisionId());
        resolver.published.setArtifactDigest(resolver.artifact.getArtifactDigest());

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        Assert.assertTrue(fields.getDiagnostics().stream().anyMatch(issue ->
                "FROZEN_REVISION_FIELD_SNAPSHOT_INVALID".equals(issue.getCode())));
    }

    @Test
    public void legacyLocalOutputUsesFrozenPackageModelTypeNotMutablePublishedType() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = artifact(resolver.published, true, false);
        resolver.published.setModelType("FLOW");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        Assert.assertTrue(fields.getDiagnostics().toString(), fields.getDiagnostics().isEmpty());
        Assert.assertTrue(fields.getLocalOutputNames().contains("frozen_output"));
    }

    @Test
    public void flowArtifactCannotDeclareLocalOutputs() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.published.setModelType("FLOW");
        resolver.artifact = artifact(resolver.published, true, true);

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void legacyFlowArtifactDoesNotInferNullReferenceOutputAsLocal() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.published.setModelType("FLOW");
        resolver.artifact = artifact(resolver.published, true, false);

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        Assert.assertTrue(fields.getDiagnostics().toString(), fields.getDiagnostics().isEmpty());
        Assert.assertTrue(fields.getLocalOutputNames().isEmpty());
        Assert.assertEquals("FLOW", fields.getSnapshotModelType());
    }

    @Test
    public void localOutputNameMustMatchNullReferenceSnapshotOutput() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = rawArtifact(
                resolver.published, 101L, 301L, "SCRIPT",
                "[]",
                "[{\"fieldName\":\"other\",\"scriptName\":\"other\",\"fieldType\":\"NUMBER\"}]",
                "{\"localOutputNames\":[\"forged\"],"
                        + "\"inputPropertySchemas\":{},\"outputPropertySchemas\":{}}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void referencedSnapshotOutputCannotBeDeclaredLocal() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = rawArtifact(
                resolver.published, 101L, 301L, "SCRIPT",
                "[]",
                "[{\"fieldName\":\"forged\",\"scriptName\":\"forged\","
                        + "\"fieldType\":\"NUMBER\",\"varId\":7,\"refType\":\"VARIABLE\"}]",
                "{\"localOutputNames\":[\"forged\"],"
                        + "\"inputPropertySchemas\":{},\"outputPropertySchemas\":{}}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void malformedFieldJsonIsInvalidInsteadOfEscaping() {
        FixtureResolver resolver = resolverWithRawComponents(
                "[{", "[]", "{\"localOutputNames\":[]}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void duplicateJsonKeyIsRejected() {
        FixtureResolver resolver = resolverWithRawComponents(
                "[]",
                "[{\"fieldName\":\"first\",\"fieldName\":\"second\"}]",
                "{\"localOutputNames\":[]}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void trailingJsonTokenIsRejected() {
        FixtureResolver resolver = resolverWithRawComponents(
                "[] trailing", "[]", "{\"localOutputNames\":[]}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void nullInputFieldArrayRootIsInvalid() {
        FixtureResolver resolver = resolverWithRawComponents(
                "null", "[]", "{\"localOutputNames\":[]}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void nullOutputFieldArrayRootIsInvalid() {
        FixtureResolver resolver = resolverWithRawComponents(
                "[]", "null", "{\"localOutputNames\":[]}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void nullInputFieldArrayElementIsInvalid() {
        FixtureResolver resolver = resolverWithRawComponents(
                "[null]", "[]", "{\"localOutputNames\":[]}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void nullOutputFieldArrayElementIsInvalid() {
        FixtureResolver resolver = resolverWithRawComponents(
                "[]", "[null]", "{\"localOutputNames\":[]}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void nullFieldResolutionRootIsInvalidInsteadOfEscaping() {
        FixtureResolver resolver = resolverWithRawComponents(
                "[]", "[]", "null");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    @Test
    public void fractionalManifestIdIsRejectedWithoutTruncation() {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = rawArtifact(
                resolver.published, 101.5d, 301L, "SCRIPT",
                "[]", "[]", "{\"localOutputNames\":[]}");

        RuleFieldAnalyzer.ResolvedFields fields = resolver.resolve(101L);

        assertInvalid(fields);
    }

    private static RulePublished published() {
        RulePublished published = new RulePublished();
        published.setDefinitionId(101L);
        published.setRevisionId(301L);
        published.setArtifactId(401L);
        published.setModelType("SCRIPT");
        published.setStatus(1);
        return published;
    }

    private static DecisionArtifact artifact(RulePublished published, boolean includeOutput) {
        return artifact(published, includeOutput, true);
    }

    private static DecisionArtifact artifact(
            RulePublished published, boolean includeOutput, boolean includeResolution) {
        RuleDefinitionInputField input = new RuleDefinitionInputField();
        input.setFieldName("frozen_input");
        input.setScriptName("frozen_input");
        input.setFieldType("NUMBER");
        input.setVarId(7L);
        input.setRefType("VARIABLE");
        input.setStatus(1);
        RuleDefinitionOutputField output = new RuleDefinitionOutputField();
        output.setFieldName("frozen_output");
        output.setScriptName("frozen_output");
        output.setFieldType("NUMBER");
        output.setStatus(1);

        DecisionArtifactPackage artifactPackage = new DecisionArtifactPackage();
        artifactPackage.putMetadata("definitionId", published.getDefinitionId());
        artifactPackage.putMetadata("revisionId", published.getRevisionId());
        artifactPackage.putMetadata("modelType", published.getModelType());
        artifactPackage.addComponent(PublishedRuleFieldSnapshotResolver.INPUT_FIELDS_PATH,
                "application/json", CanonicalJson.writeBytes(
                        JSON.parse(JSON.toJSONString(Collections.singletonList(input)))));
        if (includeOutput) {
            artifactPackage.addComponent(PublishedRuleFieldSnapshotResolver.OUTPUT_FIELDS_PATH,
                    "application/json", CanonicalJson.writeBytes(
                            JSON.parse(JSON.toJSONString(Collections.singletonList(output)))));
        }
        Map<String, Object> fieldResolution = new LinkedHashMap<>();
        fieldResolution.put("localOutputNames", Collections.singletonList("frozen_output"));
        fieldResolution.put("inputPropertySchemas",
                Collections.singletonMap("frozen_input", "NUMBER"));
        fieldResolution.put("outputPropertySchemas", Collections.emptyMap());
        if (includeResolution) {
            artifactPackage.addComponent(PublishedRuleFieldSnapshotResolver.FIELD_RESOLUTION_PATH,
                    "application/json", CanonicalJson.writeBytes(fieldResolution));
        }

        DecisionArtifactPackageCodec codec = new DecisionArtifactPackageCodec();
        byte[] packageContent = codec.encode(artifactPackage);
        DecisionArtifactPackageCodec.DecodedPackage decoded = codec.decode(packageContent);
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(published.getArtifactId());
        artifact.setDefinitionId(published.getDefinitionId());
        artifact.setRevisionId(published.getRevisionId());
        artifact.setArtifactDigest(decoded.getArtifactDigest());
        artifact.setPackageDigest(decoded.getPackageDigest());
        artifact.setPackageContent(packageContent);
        published.setArtifactDigest(decoded.getArtifactDigest());
        return artifact;
    }

    private static FixtureResolver resolverWithRawComponents(
            String inputs, String outputs, String resolution) {
        FixtureResolver resolver = new FixtureResolver();
        resolver.published = published();
        resolver.artifact = rawArtifact(
                resolver.published, 101L, 301L, "SCRIPT",
                inputs, outputs, resolution);
        return resolver;
    }

    private static DecisionArtifact rawArtifact(
            RulePublished published,
            Object manifestDefinitionId,
            Object manifestRevisionId,
            String modelType,
            String inputs,
            String outputs,
            String resolution) {
        DecisionArtifactPackage artifactPackage = new DecisionArtifactPackage();
        artifactPackage.putMetadata("definitionId", manifestDefinitionId);
        artifactPackage.putMetadata("revisionId", manifestRevisionId);
        artifactPackage.putMetadata("modelType", modelType);
        artifactPackage.addComponent(PublishedRuleFieldSnapshotResolver.INPUT_FIELDS_PATH,
                "application/json", inputs.getBytes(StandardCharsets.UTF_8));
        artifactPackage.addComponent(PublishedRuleFieldSnapshotResolver.OUTPUT_FIELDS_PATH,
                "application/json", outputs.getBytes(StandardCharsets.UTF_8));
        if (resolution != null) {
            artifactPackage.addComponent(PublishedRuleFieldSnapshotResolver.FIELD_RESOLUTION_PATH,
                    "application/json", resolution.getBytes(StandardCharsets.UTF_8));
        }
        DecisionArtifactPackageCodec codec = new DecisionArtifactPackageCodec();
        byte[] packageContent = codec.encode(artifactPackage);
        DecisionArtifactPackageCodec.DecodedPackage decoded = codec.decode(packageContent);
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(published.getArtifactId());
        artifact.setDefinitionId(published.getDefinitionId());
        artifact.setRevisionId(published.getRevisionId());
        artifact.setArtifactDigest(decoded.getArtifactDigest());
        artifact.setPackageDigest(decoded.getPackageDigest());
        artifact.setPackageContent(packageContent);
        published.setArtifactDigest(decoded.getArtifactDigest());
        return artifact;
    }

    private static void assertInvalid(RuleFieldAnalyzer.ResolvedFields fields) {
        Assert.assertTrue(fields.getDiagnostics().stream().anyMatch(issue ->
                "FROZEN_REVISION_FIELD_SNAPSHOT_INVALID".equals(issue.getCode())));
        Assert.assertTrue(fields.getInputFields().isEmpty());
        Assert.assertTrue(fields.getOutputFields().isEmpty());
        Assert.assertTrue(fields.getLocalOutputNames().isEmpty());
    }

    private static final class FixtureResolver extends PublishedRuleFieldSnapshotResolver {
        private RulePublished published;
        private DecisionArtifact artifact;
        private ArtifactDeployment deployment;
        private RuleRevision revision;
        private int deploymentReads;
        private int revisionReads;

        @Override protected ArtifactDeployment loadDeployment(Long artifactId, Long definitionId) { deploymentReads++; return deployment; }
        @Override protected RuleRevision loadTargetRevision(Long revisionId) { revisionReads++; return revision; }

        @Override
        protected RulePublished loadPublishedRule(Long definitionId) {
            return published;
        }

        @Override
        protected DecisionArtifact loadArtifact(Long artifactId) {
            return artifact;
        }
    }
}
