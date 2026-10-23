package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.*;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.artifact.RulePreflightValidationService;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import org.junit.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.Assert.*;

public class RuleRegressionEvidenceServiceTest {
    @Test public void draftPublicationUsesComputedDigestBeforeArtifactHasBeenCreated() {
        Fixture service = new Fixture();
        RuleRevision draft = new RuleRevision(); draft.setId(20L); draft.setDefinitionId(9L); draft.setState("DRAFT");
        service.complete(service.beginSaved(), execution(true), validation("PASSED"));
        assertNull("草稿尚未生成正式制品摘要", draft.getContentDigest());
        service.requirePublication(draft);
        service.dependencies = "changed";
        assertThrows(IllegalArgumentException.class, () -> service.requirePublication(draft));
    }
    @Test public void noCasesAllowsPublicationWithExplicitWarning() {
        Fixture service = new Fixture(); service.cases.clear();
        RulePreflightReport report = new RulePreflightReport(); report.setRevisionId(20L);
        report.setContentDigest("content"); report.setDependencyDigest("deps");
        service.appendGate(9L, report);
        assertTrue(report.isValid()); assertTrue(report.getRegression().isAllowed());
        assertEquals("NO_REGRESSION_CASES", report.getWarnings().get(0).getCode());
    }

    @Test public void pendingFailedAndStaleReportsCannotBeUsedAsPassedEvidence() {
        Fixture service = new Fixture();
        assertFalse(service.gate().isAllowed());
        var run = service.beginSaved();
        assertFalse(service.gate().isAllowed());
        service.complete(run, execution(true), validation("PASSED"));
        assertTrue(service.gate().isAllowed());
        service.dependencies = "changed";
        assertEquals(1, service.gate().getStaleCount());
        var next = service.beginSaved();
        service.complete(next, execution(true), validation("FAILED"));
        assertFalse(service.gate().isAllowed());
        assertEquals(1, service.gate().getFailedCount());
    }

    @Test public void changedExpectationAndTemporaryOverridesDoNotQualify() {
        Fixture service = new Fixture();
        var diagnostic = service.begin(9L, service.cases.get(0), target(), "{\"params\":{\"changed\":1}}", null, "{}", 1L);
        service.complete(diagnostic, execution(true), validation("PASSED"));
        assertEquals(Integer.valueOf(0), service.rows.get(0).getEligible());
        assertFalse(service.gate().isAllowed());
        service.complete(service.beginSaved(), execution(true), validation("PASSED"));
        assertTrue(service.gate().isAllowed());
        service.cases.get(0).setResponseJson("{\"different\":1}");
        assertEquals(1, service.gate().getStaleCount());
    }

    @Test public void dependencyChangeDuringExecutionMarksTheAttemptStale() {
        Fixture service = new Fixture(); var run = service.beginSaved();
        service.dependencies = "changed";
        service.complete(run, execution(true), validation("PASSED"));
        assertEquals("STALE", service.rows.get(0).getState());
        assertFalse(service.gate().isAllowed());
    }

    @Test public void fingerprintsPreserveLargeNumbersAndAllDecimalDigits() {
        Fixture service = new Fixture();
        String left = service.scenarioDigest("{\"params\":{\"id\":9007199254740993,\"n\":0.123456789012345678901}}", null, "{}");
        String right = service.scenarioDigest("{\"params\":{\"id\":9007199254740993,\"n\":0.123456789012345678902}}", null, "{}");
        assertNotEquals(left, right);
        assertEquals(left, service.scenarioDigest("{ \"params\": {\"n\":0.123456789012345678901,\"id\":9007199254740993} }", null, "{}"));
    }

    private static RuleTestTargetResolver.Target target() {
        RuleDefinition definition = new RuleDefinition(); definition.setId(9L);
        RuleTestExecutionIdentity identity = new RuleTestExecutionIdentity();
        identity.setSourceType("REVISION"); identity.setSourceId("20"); identity.setRevisionId("20");
        identity.setModelDigest(Sha256Digests.text("{}"));
        return new RuleTestTargetResolver.Target(definition, 1L, "{}", "SCRIPT", "return 1;",
                new RuleFieldAnalyzer.ResolvedFields(List.of(), List.of()), null, null, identity);
    }
    private static RuleResult execution(boolean success) {
        RuleResult result = new RuleResult(); result.setSuccess(success); result.setExecutionIdentity(target().identity()); return result;
    }
    private static TestExpectationResult validation(String status) {
        TestExpectationResult result = new TestExpectationResult(); result.setStatus(status); result.setMessage(status); return result;
    }
    private static class Fixture extends RuleRegressionEvidenceService {
        final List<RuleApiDocScenario> cases = new ArrayList<>();
        final List<RuleRegressionEvidence> rows = new ArrayList<>();
        String dependencies = "deps";
        Fixture() {
            ReflectionTestUtils.setField(this, "expectationService", new RuleTestExpectationService());
            RuleApiDocScenario scenario = new RuleApiDocScenario(); scenario.setId(7L); scenario.setDefinitionId(9L);
            scenario.setScenarioName("固定样例"); scenario.setStatus(1); scenario.setRequestJson("{\"params\":{}}"); scenario.setResponseJson("{}"); cases.add(scenario);
        }
        Run beginSaved() { var c = cases.get(0); return begin(9L, c, target(), c.getRequestJson(), c.getExpectationJson(), c.getResponseJson(), 1L); }
        RuleRegressionGate gate() { return check(9L, 20L, "content", dependencies); }
        @Override protected Stamp currentStamp(Long definition, Long revision) { return new Stamp("content", dependencies,
                RulePreflightValidationService.fieldDigest(target().fields()), target().identity().getModelDigest(), "return 1;", true); }
        @Override protected List<RuleApiDocScenario> enabledCases(Long definition) { return cases; }
        @Override protected RuleApiDocScenario loadCase(Long id) { return cases.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null); }
        @Override protected List<RuleRegressionEvidence> latestEvidence(Long definition, Long revision) {
            Map<Long, RuleRegressionEvidence> latest = new LinkedHashMap<>();
            rows.stream().filter(row -> Integer.valueOf(1).equals(row.getEligible())).forEach(row -> latest.put(row.getScenarioId(), row));
            return new ArrayList<>(latest.values());
        }
        @Override protected String actor() { return "tester"; }
        @Override protected void lockDefinition(Long id) { assertEquals(Long.valueOf(9), id); }
        @Override protected void insert(RuleRegressionEvidence row) { row.setId((long) rows.size() + 1); rows.add(copy(row)); }
        @Override protected void completeUpdate(RuleRegressionEvidence row) {
            int index = row.getId().intValue() - 1;
            assertEquals("RUNNING", rows.get(index).getState()); rows.set(index, copy(row));
        }
        private RuleRegressionEvidence copy(RuleRegressionEvidence row) { var copy = new RuleRegressionEvidence(); BeanUtils.copyProperties(row, copy); return copy; }
    }
}
