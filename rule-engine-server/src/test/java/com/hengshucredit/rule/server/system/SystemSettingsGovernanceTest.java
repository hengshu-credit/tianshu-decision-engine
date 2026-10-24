package com.hengshucredit.rule.server.system;

import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.dto.GovernanceReviewRequest;
import com.hengshucredit.rule.model.dto.GovernanceSubmitRequest;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.governance.GovernanceApprovalService;
import com.hengshucredit.rule.server.governance.GovernanceDependencyService;
import com.hengshucredit.rule.server.governance.GovernancePreflightReport;
import com.hengshucredit.rule.server.governance.GovernanceRequestView;
import com.hengshucredit.rule.server.governance.GovernanceSecretCodec;
import com.hengshucredit.rule.server.governance.GovernedResourceAdapter;
import com.hengshucredit.rule.server.governance.ResourceSnapshot;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.Assert.*;

/** Uses the actual approval state machine, settings adapter, encryption and SQL persistence. */
public class SystemSettingsGovernanceTest {
    private SystemSettingsService settings;
    private ApprovalStore approvals;

    @Before public void setup() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:settingsApproval" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE rule_system_setting (id BIGINT PRIMARY KEY, config_json CLOB, secret_ciphertext CLOB, secret_digest VARCHAR(64), version BIGINT DEFAULT 0, update_by VARCHAR(128), update_time TIMESTAMP)");
        for (int id = 1; id <= 4; id++) jdbc.update("INSERT INTO rule_system_setting(id, config_json) VALUES (?, '{}')", id);
        var keys = new ProjectAuthProperties();
        keys.setActiveKeyId("test");
        keys.setMasterKeys(Map.of("test", "test-only-master-key-0123456789-abcdef"));
        var env = new StandardEnvironment();
        env.getPropertySources().remove("systemEnvironment");
        env.getPropertySources().remove("systemProperties");
        var login = new RuleEngineConsoleLoginProperties();
        settings = new SystemSettingsService(jdbc, new GovernanceSecretCodec(new CredentialCipher(keys)), login, env);
        approvals = new ApprovalStore(new SystemSettingGovernedResourceAdapter(settings, new SystemSettingsAccess(null, login)));
    }

    @Test public void sharedExternalCodeBecomesEffectiveOnlyAfterApprovalAndCanBeEditedAgain() {
        var value = settings.get("codes");
        value.put("enabled", true);
        value.put("mappings", Map.of(
                "100001", Map.of("code", "E1", "message", "请求参数错误"),
                "100002", Map.of("code", "E1", "message", "请补充必填参数")));
        var request = approvals.createDraft(draft(value), "owner");
        assertFalse(settings.mapping().enabled());
        approvals.submit(request.getId(), submission(request), "owner");
        assertFalse(settings.mapping().enabled());
        assertThrows(GovernanceApprovalService.GovernanceStateException.class,
                () -> approvals.saveDraft(request.getId(), draft(value), "owner"));
        approvals.approve(request.getId(), new GovernanceReviewRequest(), "reviewer");
        assertEquals("APPROVED", request.getStatus());
        assertEquals("E1", settings.mapping().code(PlatformCode.PARAMETER_INVALID));
        assertEquals("E1", settings.mapping().code(PlatformCode.REQUIRED_FIELD_MISSING));
        assertEquals("请补充必填参数", settings.mapping().message(PlatformCode.REQUIRED_FIELD_MISSING));
        assertEquals(1L, settings.mapping().version());
        assertEquals(1L, ((Number) CanonicalJson.readMap(approvals.versions.get(0).getSnapshotJson()).get("configVersion")).longValue());

        var next = settings.get("codes");
        next.put("enabled", false);
        var second = approvals.createDraft(draft(next), "owner");
        assertNotEquals(request.getId(), second.getId());
        approvals.submit(second.getId(), submission(second), "owner");
        approvals.approve(second.getId(), new GovernanceReviewRequest(), "reviewer");
        assertFalse(settings.mapping().enabled());
        assertEquals(2L, settings.mapping().version());
    }

    @Test public void rejectingMailDraftNeverChangesSenderOrCredentialAndBlankEditPreservesSecret() {
        var value = settings.get("mail");
        value.put("email", "notify@example.com");
        value.put("host", "smtp.example.com");
        value.put("password", "initial-test-secret");
        var request = approvals.createDraft(draft(value), "owner");
        assertFalse(CanonicalJson.write(GovernanceRequestView.from(request)).contains("initial-test-secret"));
        assertEquals("", settings.mail().get("email"));
        approvals.submit(request.getId(), submission(request), "owner");
        approvals.approve(request.getId(), new GovernanceReviewRequest(), "reviewer");
        assertEquals("initial-test-secret", settings.mail().get("password"));

        var edit = settings.get("mail");
        edit.put("password", "");
        edit.put("port", 465);
        edit.put("encryption", "TLS");
        var second = approvals.createDraft(draft(edit), "owner");
        var saved = CanonicalJson.readMap(second.getDraftSnapshotJson());
        saved.put("password", "");
        var save = draft(saved);
        save.setExpectedSnapshotDigest(second.getSnapshotDigest());
        approvals.saveDraft(second.getId(), save, "owner");
        approvals.submit(second.getId(), submission(second), "owner");
        approvals.approve(second.getId(), new GovernanceReviewRequest(), "reviewer");
        assertEquals("initial-test-secret", settings.mail().get("password"));
        assertEquals(465, settings.mail().get("port"));

        var rejected = settings.get("mail");
        rejected.put("email", "replacement@example.com");
        rejected.put("password", "rejected-test-secret");
        var third = approvals.createDraft(draft(rejected), "owner");
        approvals.submit(third.getId(), submission(third), "owner");
        approvals.reject(third.getId(), new GovernanceReviewRequest(), "reviewer");
        assertEquals("REJECTED", third.getStatus());
        assertEquals("notify@example.com", settings.mail().get("email"));
        assertEquals("initial-test-secret", settings.mail().get("password"));
        assertEquals(2, approvals.versions.size());
        assertFalse(CanonicalJson.write(GovernanceRequestView.from(third)).contains("rejected-test-secret"));
    }

    @Test public void invalidCodeAndStaleConfigurationCannotBePublished() {
        var value = settings.get("codes");
        value.put("enabled", true);
        value.put("mappings", Map.of("unknown", Map.of("code", "E1", "message", "错误")));
        var invalid = approvals.createDraft(draft(value), "owner");
        assertThrows(GovernanceApprovalService.GovernanceStateException.class,
                () -> approvals.submit(invalid.getId(), submission(invalid), "owner"));
        assertEquals("EDITING", invalid.getStatus());
        assertFalse(settings.mapping().enabled());

        value.put("mappings", Map.of("100001", Map.of("code", "E1", "message", "参数错误")));
        var corrected = draft(value);
        corrected.setExpectedSnapshotDigest(invalid.getSnapshotDigest());
        approvals.saveDraft(invalid.getId(), corrected, "owner");
        approvals.submit(invalid.getId(), submission(invalid), "owner");
        // Simulate a concurrently committed configuration change, without changing the pending snapshot.
        var concurrent = settings.normalize(ResourceSnapshot.ofJson(CanonicalJson.write(settings.get("codes"))));
        settings.persist(concurrent, "other-admin");
        assertThrows(IllegalArgumentException.class,
                () -> approvals.approve(invalid.getId(), new GovernanceReviewRequest(), "reviewer"));
        assertFalse(settings.mapping().enabled());
        assertEquals(1L, settings.mapping().version());
        assertTrue(approvals.versions.isEmpty());
    }

    private static GovernanceDraftRequest draft(Map<String, Object> value) {
        var request = new GovernanceDraftRequest();
        request.setResourceType(SystemSettingsService.RESOURCE_TYPE);
        request.setResourceId(((Number) value.get("id")).longValue());
        request.setProjectId(0L);
        request.setAction("UPDATE");
        request.setSnapshotJson(CanonicalJson.write(value));
        return request;
    }

    private static GovernanceSubmitRequest submission(GovernanceApprovalRequest request) {
        var submit = new GovernanceSubmitRequest();
        submit.setExpectedSnapshotDigest(request.getSnapshotDigest());
        return submit;
    }

    /** Only governance storage is in memory; validation and lifecycle operations use production code. */
    private static class ApprovalStore extends GovernanceApprovalService {
        final Map<Long, GovernedResource> resources = new HashMap<>();
        final Map<Long, GovernanceApprovalRequest> requests = new HashMap<>();
        final List<GovernedResourceVersion> versions = new ArrayList<>();
        private final GovernedResourceAdapter adapter;

        ApprovalStore(GovernedResourceAdapter adapter) {
            this.adapter = adapter;
            ReflectionTestUtils.setField(this, "dependencyService", new GovernanceDependencyService());
            for (long id = 2; id <= 4; id++) {
                var resource = new GovernedResource();
                resource.setId(id); resource.setResourceId(id); resource.setResourceType(SystemSettingsService.RESOURCE_TYPE);
                resource.setProjectId(0L); resource.setEffectiveStatus("ACTIVE");
                resource.setEffectiveVersionId(id); resource.setEffectiveVersionNo(1);
                resources.put(id, resource);
            }
        }
        @Override protected GovernedResourceAdapter requireAdapter(String type) { return adapter; }
        @Override protected GovernedResource findResource(String type, Long id) { return resources.get(id); }
        @Override protected void persistResource(GovernedResource value) { resources.put(value.getResourceId(), value); }
        @Override protected GovernanceApprovalRequest insertRequest(GovernanceApprovalRequest value) {
            value.setId(100L + requests.size()); requests.put(value.getId(), value); return value;
        }
        @Override protected GovernanceApprovalRequest requireRequest(Long id) { return Objects.requireNonNull(requests.get(id)); }
        @Override protected GovernanceApprovalRequest findActiveRequest(String key) {
            return requests.values().stream().filter(request -> key.equals(request.getActiveResourceKey())).findFirst().orElse(null);
        }
        @Override protected void persistRequest(GovernanceApprovalRequest value) { requests.put(value.getId(), value); }
        @Override protected GovernedResourceVersion persistVersion(GovernedResourceVersion value) {
            value.setId(200L + versions.size()); versions.add(value); return value;
        }
        @Override protected List<GovernancePreflightReport.ResolvedDependency> loadDependencies(Long id) { return List.of(); }
        @Override protected void persistDependencies(GovernanceApprovalRequest request, GovernancePreflightReport report, Long id) { }
        @Override protected void bindDependencyVersion(Long requestId, Long versionId) { }
        @Override protected void appendEvent(GovernanceApprovalRequest request, String action, String from, String actor, String comment, String details) { }
    }
}
