package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.core.compiler.ScriptPassthroughCompiler;
import com.hengshucredit.rule.model.dto.*;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RuleDesignerServiceTest {
    @Test
    public void pureCompileReturnsScriptAndPreflightWithoutWrites() {
        Fixture service = new Fixture();
        service.governanceFailure = true;
        RuleDesignerCompileResponse result = service.compile(30L, request("r1"));
        assertTrue(result.isCompileSuccess());
        assertEquals("return 42;", result.getCompiledScript());
        assertFalse(result.getPreflightReport().isValid());
        assertTrue(service.revisions.isEmpty());
        assertTrue(service.operations.isEmpty());
        assertEquals(0, service.saves);
    }

    @Test
    public void newCreatesIndependentDraftAndRetryReturnsOriginalSnapshot() {
        Fixture service = new Fixture();
        RuleDesignerDraftRequest firstRequest = request("r1");
        RuleDraftSaveResponse first = service.save(30L, firstRequest);
        RuleDraftSaveResponse second = service.save(30L, request("r2"));
        assertNotEquals(first.getRevision().getId(), second.getRevision().getId());
        assertEquals(2, service.revisions.size());
        assertEquals(first, service.save(30L, firstRequest));
        assertEquals(2, service.saves);
        firstRequest.setModelJson("{\"script\":\"return 99;\"}");
        assertEquals("REQUEST_ID_REUSED", assertThrows(RuleGovernanceException.class,
                () -> service.save(30L, firstRequest)).getCode());
    }

    @Test
    public void overwriteChangesOnlySelectedDraftAndRejectsStaleLock() {
        Fixture service = new Fixture();
        RuleRevision first = service.save(30L, request("r1")).getRevision();
        RuleRevision second = service.save(30L, request("r2")).getRevision();
        RuleDesignerDraftRequest overwrite = request("r3");
        overwrite.setSaveMode("OVERWRITE");
        overwrite.setRevisionId(first.getId());
        overwrite.setLockVersion(first.getLockVersion());
        overwrite.setModelJson("{\"script\":\"return 99;\"}");
        RuleRevision updated = service.save(30L, overwrite).getRevision();
        assertEquals(first.getId(), updated.getId());
        assertEquals(Integer.valueOf(first.getLockVersion() + 1), updated.getLockVersion());
        assertEquals("{\"script\":\"return 42;\"}", service.revisions.get(second.getId()).getModelJson());
        assertEquals(2, service.revisions.size());
        overwrite.setRequestId("stale");
        assertEquals("DRAFT_LOCK_CONFLICT", assertThrows(RuleGovernanceException.class,
                () -> service.save(30L, overwrite)).getCode());
    }

    @Test
    public void unfinishedConfigurationCanBeSavedButPreflightStillBlocksPublication() {
        Fixture service = new Fixture();
        RuleDesignerDraftRequest invalid = request("invalid");
        invalid.setModelJson("{\"script\":\"return {\"}");
        assertFalse(service.compile(30L, invalid).isCompileSuccess());
        RuleDraftSaveResponse unfinished = service.save(30L, invalid);
        assertFalse(unfinished.isCompileSuccess());
        assertEquals("DRAFT", unfinished.getRevision().getState());
        assertEquals(invalid.getModelJson(), unfinished.getRevision().getModelJson());
        assertEquals("COMPILE_FAILED", unfinished.getIssues().get(0).getCode());
        assertEquals(1, unfinished.getIssues().size());
        assertFalse(service.compile(30L, invalid).getPreflightReport().isValid());
        assertEquals(unfinished, service.save(30L, invalid));
        assertEquals(1, service.saves);
        service.governanceFailure = true;
        RuleDraftSaveResponse saved = service.save(30L, request("valid"));
        assertTrue(saved.isCompileSuccess());
        assertEquals("DRAFT", saved.getRevision().getState());
        assertEquals("DEPENDENCY_NOT_FOUND", saved.getIssues().get(0).getCode());
    }

    private static RuleDesignerDraftRequest request(String key) {
        RuleDesignerDraftRequest request = new RuleDesignerDraftRequest();
        request.setSaveMode("NEW");
        request.setRequestId(key);
        request.setModelJson("{\"script\":\"return 42;\"}");
        return request;
    }

    private static class Fixture extends RuleDesignerService {
        final Map<Long, RuleRevision> revisions = new LinkedHashMap<>();
        final Map<String, RuleDesignerSaveOperation> operations = new HashMap<>();
        boolean governanceFailure;
        int saves;
        @Override protected void lockDefinition(Long id) { }
        @Override protected String actor() { return "editor"; }
        @Override protected RuleDesignerSaveOperation findOperation(Long id, String key) { return operations.get(key); }
        @Override protected void insertOperation(RuleDesignerSaveOperation operation) { operations.put(operation.getRequestId(), operation); }
        @Override protected RuleRevision prepare(Long id, RuleDesignerCompileRequest request) {
            RuleRevision revision = new RuleRevision();
            revision.setDefinitionId(id);
            revision.setState("DRAFT");
            revision.setLockVersion(0);
            revision.setModelJson(request.getModelJson());
            return revision;
        }
        @Override protected RulePreflightReport preview(RuleRevision revision) {
            CompileResult result = new ScriptPassthroughCompiler().compile(revision.getModelJson());
            RulePreflightReport report = new RulePreflightReport();
            if (result.isSuccess()) {
                report.setCompiledScript(result.getCompiledScript());
                report.setCompiledType(result.getCompiledType());
            } else report.getErrors().add(RuleValidationIssue.error("COMPILE_FAILED", "$", result.getErrorMessage()));
            if (governanceFailure) report.getErrors().add(RuleValidationIssue.error("DEPENDENCY_NOT_FOUND", "$", "missing"));
            report.setValid(report.getErrors().isEmpty());
            return report;
        }
        @Override protected void insertDraft(RuleRevision revision) {
            revision.setId((long) revisions.size() + 1);
            revisions.put(revision.getId(), copy(revision));
        }
        @Override protected RuleRevision editable(Long id, Long revisionId) {
            RuleRevision revision = revisions.get(revisionId);
            if (revision == null || !id.equals(revision.getDefinitionId()) || !"DRAFT".equals(revision.getState()))
                throw new RuleGovernanceException(409, "FROZEN_REVISION_WRITE_REJECTED", "frozen", List.of());
            return copy(revision);
        }
        @Override protected RuleDraftSaveResponse saveDraft(RuleDraftSaveRequest request) {
            RuleRevision revision = copy(revisions.get(request.getRevisionId()));
            revision.setModelJson(request.getModelJson());
            revision.setLockVersion(request.getLockVersion() + 1);
            revisions.put(revision.getId(), copy(revision));
            saves++;
            RuleDraftSaveResponse response = new RuleDraftSaveResponse();
            response.setRevision(revision);
            response.setDesignVersion(saves);
            CompileResult compiled = new ScriptPassthroughCompiler().compile(request.getModelJson());
            response.setCompileSuccess(compiled.isSuccess());
            response.setCompileMessage(compiled.getErrorMessage());
            if (!compiled.isSuccess()) response.setIssues(List.of(
                    RuleValidationIssue.error("COMPILE_FAILED", "$.script", compiled.getErrorMessage())));
            return response;
        }
        @Override protected void recordSave(RuleRevision revision, String mode) { }
        private RuleRevision copy(RuleRevision revision) { return JSON.parseObject(JSON.toJSONString(revision), RuleRevision.class); }
    }
}
