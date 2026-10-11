package com.hengshucredit.rule.server.artifact;

import com.hengshucredit.rule.model.dto.RuleRevisionFieldView;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.controller.mgmt.RuleDefinitionController;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.RuleFieldAnalyzer;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;

import static org.junit.Assert.*;

public class RuleRevisionFieldViewServiceTest {
    @Test public void frozenRevisionReadsArtifactWithoutUsingCurrentFieldProjectionOrVariableNames() {
        Fixture service = new Fixture();
        byte[] original = service.artifact.getPackageContent().clone();
        RuleRevisionFieldView view = service.getFields(101L, 301L);
        assertEquals("AVAILABLE", view.getStatus());
        assertEquals("FROZEN_ARTIFACT", view.getSource());
        assertEquals("ARTIFACT_SOURCE", view.getReferenceNamespace());
        assertEquals("FROZEN_REFERENCE_IDS", view.getValidationSource());
        assertTrue(view.isReadOnly());
        assertEquals("SCRIPT", view.getModelType());
        assertEquals("9007199254740993", view.getInputFields().get(0).get("id"));
        assertEquals("9007199254740995", view.getInputFields().get(0).get("varId"));
        assertEquals("冻结输入 原样", view.getInputFields().get(0).get("fieldLabel"));
        assertEquals("Exact_Input", view.getInputFields().get(0).get("fieldName"));
        assertEquals("[9007199254740997]", view.getInputFields().get(0).get("validationRuleIds"));
        assertEquals(Set.of("localResult"), view.getLocalOutputNames());
        assertEquals(Map.of("type", "number"), view.getInputPropertySchemas().get("Exact_Input"));
        assertEquals(0, service.draftReads);
        assertArrayEquals(original, service.artifact.getPackageContent());
    }

    @Test public void importedOfflineRevisionKeepsSourceFieldsDespiteCollidingLocalIds() {
        Fixture service = new Fixture();
        service.imported(901L, 902L);
        RuleRevisionFieldView view = service.getFields(901L, 902L);
        assertEquals("AVAILABLE", view.getStatus());
        assertEquals("901", view.getDefinitionId()); assertEquals("902", view.getRevisionId());
        assertEquals("OFFLINE", view.getRevisionState());
        assertEquals("导入修订的字段不能由空管理投影替代", 1, view.getInputFields().size());
        assertEquals("冻结输入 原样", view.getInputFields().get(0).get("fieldLabel"));
        assertEquals("9007199254740995", view.getInputFields().get(0).get("varId"));
        assertEquals(service.artifact.getArtifactDigest(), view.getArtifactDigest());
        assertEquals(0, service.draftReads);
    }

    @Test public void multipleDraftViewsAnalyzeTheirExactModelAndUseCurrentOverrideSemanticsWithoutWriting() {
        Fixture service = new Fixture();
        service.revision.setState("DRAFT"); service.revision.setModelJson("{\"draft\":\"A\"}");
        RuleRevision second = new RuleRevision(); second.setId(302L); second.setDefinitionId(101L); second.setState("DRAFT"); second.setModelJson("{\"draft\":\"B\"}");
        service.otherRevision = second;
        RuleRevisionFieldView first = service.getFields(101L, 301L);
        RuleRevisionFieldView next = service.getFields(101L, 302L);
        assertEquals(List.of("{\"draft\":\"A\"}", "{\"draft\":\"B\"}"), service.analyzedModels);
        assertEquals("DRAFT_ANALYSIS", first.getSource());
        assertEquals("CURRENT_ENVIRONMENT", first.getReferenceNamespace());
        assertEquals("CURRENT_CONFIGURATION", first.getValidationSource());
        assertTrue(first.isReadOnly()); assertTrue(next.isReadOnly());
        assertEquals("{\"draft\":\"A\"}", first.getInputFields().get(0).get("fieldName"));
        assertEquals("{\"draft\":\"B\"}", next.getInputFields().get(0).get("fieldName"));
        assertEquals(1, first.getInputFields().get(0).get("validationOverride"));
        assertEquals("[77]", first.getInputFields().get(0).get("validationRuleIds"));
        assertEquals(0, service.artifactReads);
    }

    @Test public void draftDiagnosticsRemainVisibleWithExactStringIdentity() {
        Fixture service = new Fixture(); service.revision.setState("DRAFT"); service.revision.setModelJson("{}");
        service.draftIssue = RuleValidationIssue.error("REFERENCE_MISSING", "$.condition", "VARIABLE", 9007199254740995L, "草稿引用不存在");
        RuleRevisionFieldView view = service.getFields(101L, 301L);
        assertEquals("INVALID", view.getStatus());
        assertEquals("DRAFT_ANALYSIS", view.getSource());
        assertEquals("9007199254740995", view.getDiagnostics().get(0).get("resourceId"));
        assertEquals("301", view.getDiagnostics().get(0).get("revisionId"));
    }

    @Test public void missingOrNonObjectDraftContentIsInvalidInsteadOfPretendingAnEmptyFieldContract() {
        for (String model : new String[]{null, "", " ", "null", "[]"}) {
            Fixture service = new Fixture(); service.revision.setState("DRAFT"); service.revision.setModelJson(model);
            RuleRevisionFieldView view = service.getFields(101L, 301L);
            assertEquals(String.valueOf(model), "INVALID", view.getStatus());
            assertEquals("DRAFT_ANALYSIS", view.getSource());
            assertFalse(view.getDiagnostics().isEmpty());
            assertEquals(0, service.draftReads);
        }
    }

    @Test public void diagnosticDetailReferenceIdsAreAlsoExactStrings() {
        Fixture service = new Fixture(); service.revision.setState("DRAFT"); service.revision.setModelJson("{}");
        service.draftIssue = RuleValidationIssue.error("REFERENCE_MISSING", "$.condition", "不存在")
                .withSafeDetail("sourceId", 9007199254740997L);
        RuleRevisionFieldView view = service.getFields(101L, 301L);
        assertEquals("9007199254740997", ((Map<?, ?>) view.getDiagnostics().get(0).get("details")).get("sourceId"));
    }

    @Test public void revisionBelongingToAnotherDefinitionIsRejectedBeforeReadingArtifact() {
        Fixture service = new Fixture();
        service.revision.setDefinitionId(999L);
        assertThrows(IllegalArgumentException.class, () -> service.getFields(101L, 301L));
        assertEquals(0, service.artifactReads); assertEquals(0, service.draftReads);
    }

    @Test public void missingLegacyOrReviewArtifactIsExplainedRatherThanZeroFieldsOrCurrentProjection() {
        for (String state : List.of("APPROVED", "PUBLISHED", "OFFLINE", "REVIEW", "REJECTED")) {
            Fixture service = new Fixture(); service.revision.setState(state); service.revision.setArtifactId(null);
            RuleRevisionFieldView view = service.getFields(101L, 301L);
            assertEquals(state, "UNAVAILABLE", view.getStatus());
            assertFalse(view.getDiagnostics().isEmpty());
            assertEquals(0, service.draftReads);
        }
    }

    @Test public void badPackageOrRevisionDigestIsInvalidWithoutLiveFallback() {
        for (int scenario = 0; scenario < 4; scenario++) {
            Fixture service = new Fixture();
            switch (scenario) {
                case 0 -> service.revision.setContentDigest("changed");
                case 1 -> service.artifact.getPackageContent()[0] ^= 1;
                case 2 -> service.artifact.setArtifactDigest("changed");
                case 3 -> service.artifact.setRevisionId(999L);
                default -> throw new AssertionError();
            }
            RuleRevisionFieldView view = service.getFields(101L, 301L);
            assertEquals("scenario=" + scenario, "INVALID", view.getStatus());
            assertFalse(view.getDiagnostics().isEmpty());
            assertEquals(0, service.draftReads);
        }
    }

    @Test public void importedRevisionRequiresDeploymentAndTargetDigestEvenWhenSourceIdsCoincide() {
        for (int scenario = 0; scenario < 3; scenario++) {
            Fixture service = new Fixture(); service.imported(101L, 301L);
            if (scenario == 0) service.deployment = null;
            if (scenario == 1) service.deployment.setTargetDefinitionId(999L);
            if (scenario == 2) service.revision.setContentDigest("wrong-target-digest");
            assertEquals("INVALID", service.getFields(101L, 301L).getStatus());
        }
    }

    @Test public void absentAndMalformedComponentsDifferFromVerifiedEmptyFields() {
        Fixture absent = new Fixture(); absent.artifact = artifact(null, "[]");
        assertEquals("UNAVAILABLE", absent.getFields(101L, 301L).getStatus());
        Fixture malformed = new Fixture(); malformed.artifact = artifact("{}", "[]");
        assertEquals("INVALID", malformed.getFields(101L, 301L).getStatus());
        Fixture empty = new Fixture(); empty.artifact = artifact("[]", "[]");
        RuleRevisionFieldView view = empty.getFields(101L, 301L);
        assertEquals("AVAILABLE", view.getStatus()); assertTrue(view.getInputFields().isEmpty()); assertTrue(view.getOutputFields().isEmpty());
    }

    @Test public void endpointIsReadOnlyAndRequiresRuleViewPermission() throws Exception {
        var method = RuleDefinitionController.class.getMethod("getRevisionFields", Long.class, Long.class);
        assertEquals("/{definitionId}/revisions/{revisionId}/fields", method.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("rule:view", method.getAnnotation(RequirePermission.class).value());
        Fixture service = new Fixture();
        RuleDefinitionController controller = new RuleDefinitionController();
        ReflectionTestUtils.setField(controller, "revisionFieldViewService", service);
        assertEquals(200, controller.getRevisionFields(101L, 301L).getCode());
        service.revision.setDefinitionId(999L);
        assertEquals(400, controller.getRevisionFields(101L, 301L).getCode());
    }

    private static final class Fixture extends RuleRevisionFieldViewService {
        RuleDefinition definition = new RuleDefinition();
        RuleRevision revision = new RuleRevision(), otherRevision;
        DecisionArtifact artifact = artifact("[{\"id\":9007199254740993,\"definitionId\":101,\"varId\":9007199254740995,\"refType\":\"VARIABLE\",\"fieldName\":\"Exact_Input\",\"fieldLabel\":\"冻结输入 原样\",\"scriptName\":\"Exact_Input\",\"fieldType\":\"NUMBER\",\"validationRuleIds\":\"[9007199254740997]\"}]",
                "[{\"fieldName\":\"localResult\",\"scriptName\":\"localResult\",\"fieldLabel\":\"冻结输出\",\"fieldType\":\"NUMBER\"}]");
        ArtifactDeployment deployment;
        RuleValidationIssue draftIssue;
        int draftReads, artifactReads;
        final List<String> analyzedModels = new ArrayList<>();
        Fixture() {
            definition.setId(101L); definition.setProjectId(7L); definition.setModelType("FLOW");
            revision.setId(301L); revision.setDefinitionId(101L); revision.setRevisionNo(3); revision.setState("PUBLISHED");
            revision.setArtifactId(601L); revision.setContentDigest("content-digest");
            ReflectionTestUtils.setField(this, "namespaceGuard", new com.hengshucredit.rule.server.service.RuleReferenceNamespaceGuard() {
                @Override public NamespaceProof inspectNamespace(RuleDefinition definition, String type, Long id) { return new NamespaceProof(false, null); }
            });
            ReflectionTestUtils.setField(this, "snapshotResolver", new PublishedRuleFieldSnapshotResolver() {
                @Override protected DecisionArtifact loadArtifact(Long id) { return artifact; }
                @Override protected ArtifactDeployment loadDeployment(Long artifactId, Long definitionId) { return deployment; }
                @Override protected RuleRevision loadTargetRevision(Long id) { return revision; }
                @Override protected RulePublished loadPublishedRule(Long id) { throw new AssertionError("不能把所选历史修订换成当前发布"); }
            });
        }
        void imported(Long definitionId, Long revisionId) {
            artifact.setDefinitionId(null); artifact.setRevisionId(null);
            definition.setId(definitionId); revision.setDefinitionId(definitionId); revision.setId(revisionId); revision.setState("OFFLINE");
            revision.setContentDigest(artifact.getArtifactDigest());
            deployment = new ArtifactDeployment(); deployment.setId(701L); deployment.setArtifactId(601L); deployment.setTargetDefinitionId(definitionId); deployment.setStatus("DEPLOYED");
        }
        @Override protected RuleDefinition loadDefinition(Long id) { return Objects.equals(id, definition.getId()) ? definition : null; }
        @Override protected RuleRevision loadRevision(Long id) { return otherRevision != null && Objects.equals(id, otherRevision.getId()) ? otherRevision : Objects.equals(id, revision.getId()) ? revision : null; }
        @Override protected DecisionArtifact loadArtifact(Long id) { artifactReads++; return artifact; }
        @Override protected RuleFieldAnalyzer.ResolvedFields analyzeDraft(RuleDefinition definition, RuleRevision revision) {
            draftReads++; analyzedModels.add(revision.getModelJson());
            RuleDefinitionInputField input = new RuleDefinitionInputField(); input.setFieldName(revision.getModelJson()); input.setValidationOverride(1); input.setValidationRuleIds("[77]");
            return new RuleFieldAnalyzer.ResolvedFields(List.of(input), List.of(), draftIssue == null ? List.of() : List.of(draftIssue), Set.of(), Map.of(), Map.of());
        }
    }

    private static DecisionArtifact artifact(String inputs, String outputs) {
        DecisionArtifactPackage pack = new DecisionArtifactPackage();
        pack.putMetadata("definitionId", 101L); pack.putMetadata("revisionId", 301L); pack.putMetadata("modelType", "SCRIPT"); pack.putMetadata("contentDigest", "content-digest");
        if (inputs != null) pack.addComponent("rule/input-fields.json", "application/json", inputs.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (outputs != null) pack.addComponent("rule/output-fields.json", "application/json", outputs.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (outputs != null && outputs.contains("localResult")) {
            pack.addComponent("rule/field-resolution.json", "application/json", CanonicalJson.writeBytes(Map.of("localOutputNames", List.of("localResult"), "inputPropertySchemas", Map.of("Exact_Input", Map.of("type", "number")))));
        }
        byte[] bytes = new DecisionArtifactPackageCodec().encode(pack);
        var decoded = new DecisionArtifactPackageCodec().decode(bytes);
        DecisionArtifact artifact = new DecisionArtifact(); artifact.setId(601L); artifact.setDefinitionId(101L); artifact.setRevisionId(301L);
        artifact.setPackageContent(bytes); artifact.setPackageDigest(decoded.getPackageDigest()); artifact.setArtifactDigest(decoded.getArtifactDigest());
        return artifact;
    }
}
