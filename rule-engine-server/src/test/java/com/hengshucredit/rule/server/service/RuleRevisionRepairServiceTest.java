package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveResponse;
import com.hengshucredit.rule.model.dto.RuleRevisionRepairRequest;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleLifecycleEvent;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RuleRevisionRepairServiceTest {
    private static final String ICEKREDIT_SCRIPT =
            "credit_score_v1 = icekredit_vn_credit_profile_features.credit_score_v1;\n"
                    + "credit_apply_count_1m = "
                    + "icekredit_vn_credit_profile_features.credit_apply_count_1m;\n"
                    + "_result = {\"credit_score_v1\": credit_score_v1, "
                    + "\"credit_apply_count_1m\": credit_apply_count_1m}\n"
                    + "_result";

    private Fixture fixture;
    private RuleRevisionRepairService service;

    @Before
    public void setUp() {
        fixture = new Fixture();
        fixture.definition = definition(30L, 4L);
        fixture.currentModelJson = modelWithoutRefs(ICEKREDIT_SCRIPT);
        fixture.variable = activeVariable(302L, 4L,
                "icekredit_vn_credit_profile_features", "OBJECT", "API");
        fixture.revisions = List.of(revisionWithRefs(6L,
                ref(302L, "VARIABLE",
                        "icekredit_vn_credit_profile_features")));
        FixtureRepairService target =
                new FixtureRepairService(fixture);
        TransactionInterceptor interceptor =
                new TransactionInterceptor(
                        new FixtureTransactionManager(fixture),
                        new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(interceptor);
        service = (RuleRevisionRepairService)
                proxyFactory.getProxy();
    }

    @Test
    public void previewRecoversOnlyStableRefsFromSameRuleHistory() {
        fixture.currentModelJson = modelWithoutRefs(ICEKREDIT_SCRIPT);
        fixture.revisions = List.of(revisionWithRefs(6L,
                ref(302L, "VARIABLE",
                        "icekredit_vn_credit_profile_features")));

        RuleRevisionRepairService.RepairPreview preview =
                service.preview(30L);

        assertEquals(List.of("VARIABLE:302"),
                preview.getRecoverableReferenceKeys());
        assertTrue(preview.getUnresolvedInputs().isEmpty());
        assertTrue(preview.isProjectionDrift());
        assertTrue(codes(preview).contains(
                "REVISION_PROJECTION_DRIFT"));
        assertEquals(64, preview.getPreviewDigest().length());
    }

    @Test
    public void sameCodeVariableNotPresentInHistoryIsNeverRecovered() {
        fixture.currentModelJson = modelWithoutRefs(ICEKREDIT_SCRIPT);
        fixture.revisions = Collections.emptyList();
        fixture.sameCodeVariable = activeVariable(302L, 4L,
                "icekredit_vn_credit_profile_features", "OBJECT", "API");

        RuleRevisionRepairService.RepairPreview preview =
                service.preview(30L);

        assertTrue(preview.getRecoverableReferenceKeys().isEmpty());
        assertEquals(2, preview.getUnresolvedInputs().size());
    }

    @Test
    public void currentCompatibilityRefsAbsentFromFrozenHistoryAreNeverRecovered() {
        fixture.currentModelJson = modelWithRefs(
                ICEKREDIT_SCRIPT,
                ref(302L, "VARIABLE",
                        "icekredit_vn_credit_profile_features"));
        fixture.revisions = Collections.emptyList();

        RuleRevisionRepairService.RepairPreview preview =
                service.preview(30L);

        assertTrue(preview.getRecoverableReferenceKeys().isEmpty());
        assertEquals(2, preview.getUnresolvedInputs().size());
        assertNull(preview.getSourceRevisionId());
    }

    @Test
    public void fractionalHistoricalReferenceIdIsRejected() {
        assertInvalidHistoricalId(new BigDecimal("302.5"));
    }

    @Test
    public void zeroHistoricalReferenceIdIsRejected() {
        assertInvalidHistoricalId(BigInteger.ZERO);
    }

    @Test
    public void negativeHistoricalReferenceIdIsRejected() {
        assertInvalidHistoricalId(BigInteger.valueOf(-302L));
    }

    @Test
    public void overflowingHistoricalReferenceIdIsRejected() {
        assertInvalidHistoricalId(
                new BigInteger("9223372036854775808"));
    }

    @Test
    public void historicalIdFromAnotherProjectIsRejected() {
        fixture.revisions = List.of(revisionWithRefs(6L,
                ref(302L, "VARIABLE",
                        "icekredit_vn_credit_profile_features")));
        fixture.variable = activeVariable(302L, 99L,
                "icekredit_vn_credit_profile_features", "OBJECT", "API");

        assertTrue(codes(service.preview(30L))
                .contains("REFERENCE_TYPE_MISMATCH"));
    }

    @Test
    public void deletedHistoricalIdIsRejected() {
        fixture.revisions = List.of(revisionWithRefs(6L,
                ref(302L, "VARIABLE",
                        "icekredit_vn_credit_profile_features")));
        fixture.variable = null;

        assertTrue(codes(service.preview(30L))
                .contains("REFERENCE_NOT_FOUND"));
    }

    @Test
    public void changedPreviewDigestRejectsRepair() {
        RuleRevisionRepairService.RepairPreview preview =
                service.preview(30L);
        fixture.currentModelJson = "{\"script\":\"changed = 1\"}";
        RuleRevisionRepairRequest request =
                new RuleRevisionRepairRequest();
        request.setSourceRevisionId(preview.getSourceRevisionId());
        request.setPreviewDigest(preview.getPreviewDigest());

        assertThrows(IllegalStateException.class,
                () -> service.repair(30L, request));
    }

    @Test
    public void repairLocksDefinitionAndUsesSingleDraftSnapshot() {
        RuleRevision firstDraft = draft(100L);
        RuleRevision concurrentlyVisibleDraft = draft(101L);
        fixture.draft = firstDraft;
        fixture.currentDraftResponses = List.of(
                firstDraft, firstDraft, concurrentlyVisibleDraft);

        RuleRevisionRepairService.RepairPreview preview =
                service.preview(30L);
        assertEquals(0, fixture.lockDefinitionCalls);

        RuleRevision repaired = service.repair(30L,
                request(preview.getSourceRevisionId(),
                        preview.getPreviewDigest()));

        assertEquals(Long.valueOf(100L), repaired.getId());
        assertEquals(Long.valueOf(100L),
                fixture.savedRequest.getRevisionId());
        assertEquals(2, fixture.currentDraftCalls);
        assertEquals(1, fixture.lockDefinitionCalls);
    }

    @Test
    public void repairPreviewRejectsMultipleDraftsAfterOlderDraftWasOverwritten() {
        multipleDraftsWithOlderProjection();
        List<RuleRevision> before = fixture.revisions.stream()
                .map(RuleRevisionRepairServiceTest::copy).toList();

        RuleGovernanceException error = assertThrows(
                RuleGovernanceException.class, () -> service.preview(30L));

        assertEquals("DRAFT_ID_REQUIRED", error.getCode());
        assertEquals(409, error.getHttpStatus());
        assertEquals(before, fixture.revisions);
        assertEquals(before.get(0), fixture.draft);
        assertNull(fixture.savedRequest);
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    public void repairRejectsMultipleDraftsWithoutChangingEitherContentOrLock() {
        RuleRevisionRepairService.RepairPreview earlierPreview =
                multipleDraftsWithOlderProjection();
        List<RuleRevision> before = fixture.revisions.stream()
                .map(RuleRevisionRepairServiceTest::copy).toList();
        String beforeContent = fixture.currentModelJson;

        RuleGovernanceException error = assertThrows(
                RuleGovernanceException.class,
                () -> service.repair(30L, request(
                        earlierPreview.getSourceRevisionId(), earlierPreview.getPreviewDigest())));

        assertEquals("DRAFT_ID_REQUIRED", error.getCode());
        assertEquals(409, error.getHttpStatus());
        assertEquals(before, fixture.revisions);
        assertEquals(before.get(0), fixture.draft);
        assertEquals(beforeContent, fixture.currentModelJson);
        assertNull(fixture.savedRequest);
        assertTrue(fixture.events.isEmpty());
    }

    private RuleRevisionRepairService.RepairPreview multipleDraftsWithOlderProjection() {
        RuleRevision frozen = fixture.revisions.get(0);
        RuleRevision older = draft(100L);
        older.setRevisionNo(7);
        older.setModelJson(fixture.currentModelJson);
        older.setLockVersion(1);
        fixture.draft = older;
        fixture.revisions = List.of(older, frozen);
        RuleRevisionRepairService.RepairPreview earlierPreview = service.preview(30L);

        RuleRevision newer = draft(101L);
        newer.setRevisionNo(8);
        newer.setModelJson(modelWithoutRefs("_result = 99"));
        newer.setLockVersion(1);
        older.setModelJson(modelWithoutRefs(ICEKREDIT_SCRIPT + "\n// older draft edited again"));
        older.setLockVersion(2);
        fixture.currentModelJson = older.getModelJson();
        fixture.revisions = List.of(newer, older, frozen);
        fixture.draft = newer;
        return earlierPreview;
    }

    @Test
    public void repairCreatesDraftAndLeavesFrozenRevisionUntouched() {
        RuleRevision frozen = revisionWithRefs(6L,
                ref(302L, "VARIABLE",
                        "icekredit_vn_credit_profile_features"));
        String before = frozen.getModelJson();
        fixture.revisions = List.of(frozen);
        RuleRevisionRepairService.RepairPreview preview =
                service.preview(30L);

        RuleRevision repaired = service.repair(30L,
                request(preview.getSourceRevisionId(),
                        preview.getPreviewDigest()));

        assertEquals("DRAFT", repaired.getState());
        assertEquals(before, frozen.getModelJson());
        assertNotEquals(frozen.getId(), repaired.getId());
        assertEquals(1, fixture.events.size());
        assertEquals("REPAIR_DRAFT",
                fixture.events.get(0).getAction());
        JSONObject repairedModel =
                JSON.parseObject(repaired.getModelJson());
        assertEquals(302L, repairedModel
                .getJSONArray("scriptVarRefs")
                .getJSONObject(0).getLongValue("varId"));
        String details = fixture.events.get(0).getDetailsJson();
        assertTrue(details.contains("\"referenceId\":302"));
        assertTrue(details.contains("\"refType\":\"VARIABLE\""));
        assertTrue(!details.contains("refCode"));
        assertTrue(!details.contains(ICEKREDIT_SCRIPT));
    }

    @Test
    public void unresolvedInputsRejectRepairBeforeDraftCreation() {
        fixture.variable = null;
        RuleRevisionRepairService.RepairPreview preview =
                service.preview(30L);

        RuleGovernanceException error = assertThrows(
                RuleGovernanceException.class,
                () -> service.repair(30L,
                        request(preview.getSourceRevisionId(),
                                preview.getPreviewDigest())));

        assertEquals(422, error.getHttpStatus());
        assertEquals("SCRIPT_INPUT_REF_MISSING",
                error.getCode());
        assertTrue(codes(error).contains(
                "SCRIPT_INPUT_REF_MISSING"));
        assertEquals(null, fixture.draft);
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    public void postSaveMissingInputRollsBackDraftContentAndFields() {
        fixture.draft = draft(100L);
        fixture.inputFields.add(input("legacy_input"));
        fixture.outputFields.add(output("legacy_output"));
        fixture.events.add(event("EXISTING"));
        RuleRevision beforeDraft = copy(fixture.draft);
        String beforeContent = fixture.currentModelJson;
        List<RuleDefinitionInputField> beforeInputs =
                copyInputs(fixture.inputFields);
        List<RuleDefinitionOutputField> beforeOutputs =
                copyOutputs(fixture.outputFields);
        List<RuleLifecycleEvent> beforeEvents =
                copyEvents(fixture.events);
        fixture.forceMissingOnSave = true;
        RuleRevisionRepairService.RepairPreview preview =
                service.preview(30L);

        RuleGovernanceException error = assertThrows(
                RuleGovernanceException.class,
                () -> service.repair(30L,
                        request(preview.getSourceRevisionId(),
                                preview.getPreviewDigest())));

        assertEquals(422, error.getHttpStatus());
        assertTrue(codes(error).contains(
                "SCRIPT_INPUT_REF_MISSING"));
        assertTrue(fixture.rollbackOccurred);
        assertNotEquals(beforeDraft,
                fixture.preRollbackDraft);
        assertNotEquals(beforeContent,
                fixture.preRollbackContent);
        assertNotEquals(beforeInputs,
                fixture.preRollbackInputs);
        assertNotEquals(beforeOutputs,
                fixture.preRollbackOutputs);
        assertEquals(beforeDraft, fixture.draft);
        assertEquals(beforeContent,
                fixture.currentModelJson);
        assertEquals(beforeInputs, fixture.inputFields);
        assertEquals(beforeOutputs, fixture.outputFields);
        assertEquals(beforeEvents, fixture.events);
        assertFalse(fixture.events.stream().anyMatch(
                item -> "REPAIR_DRAFT".equals(
                        item.getAction())));
    }

    @Test
    public void eventInsertFailureRollsBackAllRepairWrites() {
        fixture.draft = draft(100L);
        fixture.inputFields.add(input("legacy_input"));
        fixture.outputFields.add(output("legacy_output"));
        fixture.events.add(event("EXISTING"));
        RuleRevision beforeDraft = copy(fixture.draft);
        String beforeContent = fixture.currentModelJson;
        List<RuleDefinitionInputField> beforeInputs =
                copyInputs(fixture.inputFields);
        List<RuleDefinitionOutputField> beforeOutputs =
                copyOutputs(fixture.outputFields);
        List<RuleLifecycleEvent> beforeEvents =
                copyEvents(fixture.events);
        fixture.failEventInsert = true;
        RuleRevisionRepairService.RepairPreview preview =
                service.preview(30L);

        assertThrows(IllegalStateException.class,
                () -> service.repair(30L,
                        request(preview.getSourceRevisionId(),
                                preview.getPreviewDigest())));

        assertTrue(fixture.rollbackOccurred);
        assertNotEquals(beforeDraft,
                fixture.preRollbackDraft);
        assertNotEquals(beforeContent,
                fixture.preRollbackContent);
        assertNotEquals(beforeInputs,
                fixture.preRollbackInputs);
        assertNotEquals(beforeOutputs,
                fixture.preRollbackOutputs);
        assertNotEquals(beforeEvents,
                fixture.preRollbackEvents);
        assertEquals(beforeDraft, fixture.draft);
        assertEquals(beforeContent,
                fixture.currentModelJson);
        assertEquals(beforeInputs, fixture.inputFields);
        assertEquals(beforeOutputs, fixture.outputFields);
        assertEquals(beforeEvents, fixture.events);
    }

    private RuleDefinition definition(Long id, Long projectId) {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(id);
        definition.setProjectId(projectId);
        definition.setModelType("SCRIPT");
        return definition;
    }

    private String modelWithoutRefs(String script) {
        JSONObject model = new JSONObject(true);
        model.put("script", script);
        return model.toJSONString();
    }

    private String modelWithRefs(
            String script, JSONObject... refs) {
        JSONObject model = JSON.parseObject(
                modelWithoutRefs(script));
        JSONArray array = new JSONArray();
        Collections.addAll(array, refs);
        model.put("scriptVarRefs", array);
        return model.toJSONString();
    }

    private RuleRevision revisionWithRefs(
            Long id, JSONObject... refs) {
        JSONObject model = JSON.parseObject(
                modelWithoutRefs(ICEKREDIT_SCRIPT));
        JSONArray array = new JSONArray();
        Collections.addAll(array, refs);
        model.put("scriptVarRefs", array);
        RuleRevision revision = new RuleRevision();
        revision.setId(id);
        revision.setDefinitionId(30L);
        revision.setRevisionNo(id.intValue());
        revision.setState("APPROVED");
        revision.setModelJson(model.toJSONString());
        revision.setContentDigest("revision-digest-" + id);
        revision.setLockVersion(0);
        return revision;
    }

    private RuleRevision draft(Long id) {
        RuleRevision draft = new RuleRevision();
        draft.setId(id);
        draft.setDefinitionId(30L);
        draft.setState("DRAFT");
        draft.setBaseRevisionId(6L);
        draft.setLockVersion(0);
        return draft;
    }

    private static RuleDefinitionInputField input(String name) {
        RuleDefinitionInputField field =
                new RuleDefinitionInputField();
        field.setDefinitionId(30L);
        field.setVarId(302L);
        field.setRefType("VARIABLE");
        field.setFieldName(name);
        field.setScriptName(name);
        field.setFieldType("OBJECT");
        field.setStatus(1);
        return field;
    }

    private static RuleDefinitionOutputField output(String name) {
        RuleDefinitionOutputField field =
                new RuleDefinitionOutputField();
        field.setDefinitionId(30L);
        field.setFieldName(name);
        field.setScriptName(name);
        field.setFieldType("NUMBER");
        field.setStatus(1);
        return field;
    }

    private static RuleLifecycleEvent event(String action) {
        RuleLifecycleEvent event =
                new RuleLifecycleEvent();
        event.setDefinitionId(30L);
        event.setRevisionId(100L);
        event.setAction(action);
        event.setFromState("DRAFT");
        event.setToState("DRAFT");
        return event;
    }

    private static RuleRevision copy(RuleRevision source) {
        if (source == null) {
            return null;
        }
        RuleRevision copy = new RuleRevision();
        copy.setId(source.getId());
        copy.setDefinitionId(source.getDefinitionId());
        copy.setRevisionNo(source.getRevisionNo());
        copy.setState(source.getState());
        copy.setBaseRevisionId(source.getBaseRevisionId());
        copy.setModelJson(source.getModelJson());
        copy.setContentDigest(source.getContentDigest());
        copy.setValidationReportDigest(
                source.getValidationReportDigest());
        copy.setLockVersion(source.getLockVersion());
        return copy;
    }

    private static List<RuleDefinitionInputField> copyInputs(
            List<RuleDefinitionInputField> fields) {
        List<RuleDefinitionInputField> copies =
                new ArrayList<>();
        for (RuleDefinitionInputField source : fields) {
            RuleDefinitionInputField copy =
                    new RuleDefinitionInputField();
            copy.setId(source.getId());
            copy.setDefinitionId(source.getDefinitionId());
            copy.setVarId(source.getVarId());
            copy.setRefType(source.getRefType());
            copy.setFieldName(source.getFieldName());
            copy.setScriptName(source.getScriptName());
            copy.setFieldType(source.getFieldType());
            copy.setStatus(source.getStatus());
            copies.add(copy);
        }
        return copies;
    }

    private static List<RuleDefinitionOutputField> copyOutputs(
            List<RuleDefinitionOutputField> fields) {
        List<RuleDefinitionOutputField> copies =
                new ArrayList<>();
        for (RuleDefinitionOutputField source : fields) {
            RuleDefinitionOutputField copy =
                    new RuleDefinitionOutputField();
            copy.setId(source.getId());
            copy.setDefinitionId(source.getDefinitionId());
            copy.setVarId(source.getVarId());
            copy.setRefType(source.getRefType());
            copy.setFieldName(source.getFieldName());
            copy.setScriptName(source.getScriptName());
            copy.setFieldType(source.getFieldType());
            copy.setStatus(source.getStatus());
            copies.add(copy);
        }
        return copies;
    }

    private static List<RuleLifecycleEvent> copyEvents(
            List<RuleLifecycleEvent> events) {
        List<RuleLifecycleEvent> copies =
                new ArrayList<>();
        for (RuleLifecycleEvent source : events) {
            RuleLifecycleEvent copy =
                    new RuleLifecycleEvent();
            copy.setId(source.getId());
            copy.setDefinitionId(source.getDefinitionId());
            copy.setRevisionId(source.getRevisionId());
            copy.setAction(source.getAction());
            copy.setFromState(source.getFromState());
            copy.setToState(source.getToState());
            copy.setActor(source.getActor());
            copy.setContentDigest(source.getContentDigest());
            copy.setValidationReportDigest(
                    source.getValidationReportDigest());
            copy.setDetailsJson(source.getDetailsJson());
            copies.add(copy);
        }
        return copies;
    }

    private void assertInvalidHistoricalId(Object rawId) {
        fixture.revisions = List.of(revisionWithRefs(
                6L, rawRef(rawId, "VARIABLE",
                        "icekredit_vn_credit_profile_features")));

        RuleRevisionRepairService.RepairPreview preview =
                service.preview(30L);

        assertTrue(preview.getRecoverableReferenceKeys().isEmpty());
        assertEquals(2, preview.getUnresolvedInputs().size());
        assertTrue(codes(preview).contains(
                "SCRIPT_REFERENCE_INVALID"));
        assertNull(preview.getSourceRevisionId());
    }

    private JSONObject ref(Long id, String type, String code) {
        return rawRef(id, type, code);
    }

    private JSONObject rawRef(
            Object id, String type, String code) {
        JSONObject ref = new JSONObject(true);
        ref.put("varId", id);
        ref.put("refType", type);
        ref.put("refCode", code);
        return ref;
    }

    private RuleRevisionRepairRequest request(
            Long sourceRevisionId, String previewDigest) {
        RuleRevisionRepairRequest request =
                new RuleRevisionRepairRequest();
        request.setSourceRevisionId(sourceRevisionId);
        request.setPreviewDigest(previewDigest);
        return request;
    }

    private List<String> codes(
            RuleRevisionRepairService.RepairPreview preview) {
        return preview.getIssues().stream()
                .map(RuleValidationIssue::getCode)
                .collect(Collectors.toList());
    }

    private List<String> codes(
            RuleGovernanceException error) {
        return error.getIssues().stream()
                .map(RuleValidationIssue::getCode)
                .collect(Collectors.toList());
    }

    private RuleVariable activeVariable(
            Long id, Long projectId, String scriptRoot,
            String varType, String varSource) {
        RuleVariable variable = new RuleVariable();
        variable.setId(id);
        variable.setProjectId(projectId);
        variable.setVarCode(scriptRoot);
        variable.setScriptName(scriptRoot);
        variable.setVarType(varType);
        variable.setVarSource(varSource);
        variable.setScope("PROJECT");
        variable.setStatus(1);
        return variable;
    }

    private static final class Fixture {
        private RuleDefinition definition;
        private String currentModelJson;
        private List<RuleRevision> revisions =
                Collections.emptyList();
        private RuleVariable variable;
        @SuppressWarnings("unused")
        private RuleVariable sameCodeVariable;
        private RuleRevision draft;
        private List<RuleRevision> currentDraftResponses =
                Collections.emptyList();
        private int currentDraftCalls;
        private int lockDefinitionCalls;
        private RuleDraftSaveRequest savedRequest;
        private boolean forceMissingOnSave;
        private boolean failEventInsert;
        private boolean rollbackOccurred;
        private RuleRevision preRollbackDraft;
        private String preRollbackContent;
        private List<RuleDefinitionInputField>
                preRollbackInputs;
        private List<RuleDefinitionOutputField>
                preRollbackOutputs;
        private List<RuleLifecycleEvent>
                preRollbackEvents;
        private final List<RuleDefinitionInputField>
                inputFields = new ArrayList<>();
        private final List<RuleDefinitionOutputField>
                outputFields = new ArrayList<>();
        private final List<RuleLifecycleEvent> events =
                new ArrayList<>();
    }

    private static class FixtureRepairService
            extends RuleRevisionRepairService {
        private final Fixture fixture;

        FixtureRepairService(Fixture fixture) {
            this.fixture = fixture;
        }

        @Override
        protected RuleDefinition loadDefinition(Long definitionId) {
            return fixture.definition != null
                    && fixture.definition.getId().equals(definitionId)
                    ? fixture.definition : null;
        }

        @Override
        protected RuleDefinition lockDefinition(Long definitionId) {
            fixture.lockDefinitionCalls++;
            return loadDefinition(definitionId);
        }

        @Override
        protected RuleDefinitionContent loadContent(
                Long definitionId) {
            RuleDefinitionContent content =
                    new RuleDefinitionContent();
            content.setDefinitionId(definitionId);
            content.setModelJson(fixture.currentModelJson);
            return content;
        }

        @Override
        protected List<RuleRevision> loadRevisions(
                Long definitionId) {
            return fixture.revisions;
        }

        @Override
        protected List<RuleDefinitionInputField> loadInputFields(
                Long definitionId) {
            return fixture.inputFields;
        }

        @Override
        protected List<RuleDefinitionOutputField> loadOutputFields(
                Long definitionId) {
            return fixture.outputFields;
        }

        @Override
        protected QLScriptFieldResolver.ValidatedScriptReference
        validateReference(
                Long projectId, Long refId, String refType) {
            RuleVariable variable = fixture.variable;
            if (variable == null
                    || !variable.getId().equals(refId)
                    || !projectId.equals(variable.getProjectId())
                    || !"VARIABLE".equals(refType)) {
                return null;
            }
            return new QLScriptFieldResolver
                    .ValidatedScriptReference(
                    refId, refType, variable.getScriptName(),
                    variable.getVarType());
        }

        @Override
        protected RuleValidationIssue invalidReferenceIssue(
                Long projectId, Long refId, String refType,
                String historicalRefCode, Long revisionId) {
            String code = fixture.variable == null
                    ? "REFERENCE_NOT_FOUND"
                    : "REFERENCE_TYPE_MISMATCH";
            return RuleValidationIssue.error(
                    code, "$.script." + historicalRefCode,
                    "历史稳定引用不可用")
                    .withReference(refType, refId)
                    .withRevisionId(revisionId);
        }

        @Override
        protected RuleRevision currentDraft(Long definitionId) {
            int index = fixture.currentDraftCalls++;
            if (index < fixture.currentDraftResponses.size()) {
                return fixture.currentDraftResponses.get(index);
            }
            return fixture.draft;
        }

        @Override
        protected RuleRevision createDraft(
                Long definitionId, Long baseRevisionId) {
            if (fixture.draft == null) {
                fixture.draft = new RuleRevision();
                fixture.draft.setId(100L);
                fixture.draft.setDefinitionId(definitionId);
                fixture.draft.setState("DRAFT");
                fixture.draft.setBaseRevisionId(baseRevisionId);
                fixture.draft.setLockVersion(0);
            }
            return fixture.draft;
        }

        @Override
        protected RuleDraftSaveResponse saveDraft(
                RuleDraftSaveRequest request) {
            fixture.savedRequest = request;
            RuleRevision saved = new RuleRevision();
            saved.setId(request.getRevisionId());
            saved.setDefinitionId(request.getDefinitionId());
            saved.setState("DRAFT");
            saved.setBaseRevisionId(
                    fixture.draft.getBaseRevisionId());
            saved.setModelJson(request.getModelJson());
            saved.setLockVersion(request.getLockVersion() + 1);
            fixture.draft = saved;
            fixture.currentModelJson = request.getModelJson();
            fixture.inputFields.clear();
            fixture.inputFields.add(input("repaired_input"));
            fixture.outputFields.clear();
            fixture.outputFields.add(output("repaired_output"));
            RuleDraftSaveResponse response =
                    new RuleDraftSaveResponse();
            response.setRevision(saved);
            response.setCompileSuccess(true);
            if (fixture.forceMissingOnSave) {
                response.setIssues(Collections.singletonList(
                        RuleValidationIssue.error(
                                "SCRIPT_INPUT_REF_MISSING",
                                "$.script.input",
                                "输入缺少稳定 ID")));
            }
            return response;
        }

        @Override
        protected void insertRepairEvent(
                RuleLifecycleEvent event) {
            fixture.events.add(event);
            if (fixture.failEventInsert) {
                throw new IllegalStateException(
                        "event insert failed after write");
            }
        }

        @Override
        protected String actor() {
            return "repair-test";
        }
    }

    private static final class FixtureTransactionManager
            extends AbstractPlatformTransactionManager {
        private final Fixture fixture;
        private RuleRevision draftSnapshot;
        private String contentSnapshot;
        private List<RuleDefinitionInputField> inputSnapshot;
        private List<RuleDefinitionOutputField> outputSnapshot;
        private List<RuleLifecycleEvent> eventSnapshot;

        private FixtureTransactionManager(Fixture fixture) {
            this.fixture = fixture;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(
                Object transaction,
                TransactionDefinition definition) {
            draftSnapshot = copy(fixture.draft);
            contentSnapshot = fixture.currentModelJson;
            inputSnapshot = copyInputs(
                    fixture.inputFields);
            outputSnapshot = copyOutputs(
                    fixture.outputFields);
            eventSnapshot = copyEvents(
                    fixture.events);
        }

        @Override
        protected void doCommit(
                DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(
                DefaultTransactionStatus status) {
            fixture.preRollbackDraft =
                    copy(fixture.draft);
            fixture.preRollbackContent =
                    fixture.currentModelJson;
            fixture.preRollbackInputs =
                    copyInputs(fixture.inputFields);
            fixture.preRollbackOutputs =
                    copyOutputs(fixture.outputFields);
            fixture.preRollbackEvents =
                    copyEvents(fixture.events);

            fixture.draft = copy(draftSnapshot);
            fixture.currentModelJson = contentSnapshot;
            fixture.inputFields.clear();
            fixture.inputFields.addAll(
                    copyInputs(inputSnapshot));
            fixture.outputFields.clear();
            fixture.outputFields.addAll(
                    copyOutputs(outputSnapshot));
            fixture.events.clear();
            fixture.events.addAll(
                    copyEvents(eventSnapshot));
            fixture.rollbackOccurred = true;
        }
    }
}
