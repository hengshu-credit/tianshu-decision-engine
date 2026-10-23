package com.hengshucredit.rule.server.dashboard;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.controller.mgmt.DashboardController;
import com.hengshucredit.rule.server.security.ConsolePermissionService;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DashboardControllerTest {

    @Test
    public void loginDisabledSettingsAreReadOnlyAndCannotBeSaved() {
        Fixture fixture = new Fixture(false);
        MockHttpServletRequest request = new MockHttpServletRequest();

        ApiResponse<DashboardResponses.Settings> get = fixture.controller.settings(
                null, request);
        ApiResponse<DashboardResponses.Settings> put = fixture.controller.saveSettings(
                null, new DashboardController.SettingsUpdateRequest(
                        Collections.emptyMap()), request);

        assertEquals("000000", get.getCode());
        assertFalse(get.getData().editable());
        assertEquals("301002", put.getCode());
        assertEquals(0, fixture.mappings.saveCalls);
    }

    @Test
    public void saveAlwaysUsesCurrentSessionUser() {
        Fixture fixture = new Fixture(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(
                fixture.properties.getSessionUserIdAttribute(), 9L);
        request.getSession().setAttribute(
                fixture.properties.getSessionUsernameAttribute(), "reviewer");

        ApiResponse<DashboardResponses.Settings> result = fixture.controller.saveSettings(
                3L, new DashboardController.SettingsUpdateRequest(
                        Collections.emptyMap()), request);

        assertEquals("000000", result.getCode());
        assertEquals(Long.valueOf(9L), fixture.mappings.savedUserId);
        assertEquals("reviewer", fixture.mappings.savedUsername);
        assertEquals(Long.valueOf(3L), fixture.mappings.savedProjectId);
    }

    @Test
    public void dashboardEndpointsPassRuleFiltersIntoCriteria() {
        Fixture fixture = new Fixture(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 2, 0, 0);

        fixture.controller.applications(3L, "RC_RISK", "风险审批",
                start, end, request);

        assertEquals("RC_RISK", fixture.criteria.ruleCode);
        assertEquals("风险审批", fixture.criteria.ruleName);
    }

    private static class Fixture {
        private final RuleEngineConsoleLoginProperties properties =
                new RuleEngineConsoleLoginProperties();
        private final FakeMappings mappings = new FakeMappings();
        private final FakeCriteria criteria = new FakeCriteria();
        private final DashboardController controller;

        private Fixture(boolean loginEnabled) {
            properties.setEnabled(loginEnabled);
            DashboardAccessService access = new DashboardAccessService(
                    properties, new ConsolePermissionService() {
                        @Override
                        public java.util.Set<String> effectivePermissions(
                                Long userId) {
                            return java.util.Set.of("rule:view");
                        }
                    });
            controller = new DashboardController(access, criteria,
                    mappings, new FakeApplications(), new FakeOperations(),
                    new FakeGovernance());
        }
    }

    private static class FakeCriteria extends DashboardCriteriaService {
        private String ruleCode;
        private String ruleName;

        private FakeCriteria() {
            super(null);
        }

        @Override
        public DashboardCriteria resolve(Long projectId,
                                         LocalDateTime startTime,
                                         LocalDateTime endTime) {
            return new DashboardCriteria(projectId,
                    projectId == null ? null : "P" + projectId,
                    LocalDateTime.of(2026, 8, 1, 0, 0),
                    LocalDateTime.of(2026, 8, 2, 0, 0));
        }

        @Override
        public DashboardCriteria resolve(Long projectId, String ruleCode,
                                         String ruleName,
                                         LocalDateTime startTime,
                                         LocalDateTime endTime) {
            this.ruleCode = ruleCode;
            this.ruleName = ruleName;
            return new DashboardCriteria(projectId,
                    projectId == null ? null : "P" + projectId,
                    ruleCode, ruleName,
                    LocalDateTime.of(2026, 8, 1, 0, 0),
                    LocalDateTime.of(2026, 8, 2, 0, 0));
        }
    }

    private static class FakeMappings extends DashboardMappingService {
        private int saveCalls;
        private Long savedUserId;
        private String savedUsername;
        private Long savedProjectId;

        private FakeMappings() {
            super(null, new DashboardMappingRepository() {
                @Override
                public com.hengshucredit.rule.model.entity.RuleVariable
                findVariable(Long id) {
                    return null;
                }
            });
        }

        @Override
        public DashboardMapping.ResolvedMappings resolve(
                Long userId, Long projectId) {
            return new DashboardMapping.ResolvedMappings(
                    Collections.emptyMap());
        }

        @Override
        public DashboardResponses.Settings settings(Long userId,
                                                     Long projectId,
                                                     boolean editable) {
            return new DashboardResponses.Settings(editable, projectId,
                    false, Collections.emptyList(), Collections.emptyList());
        }

        @Override
        public DashboardMapping.ResolvedMappings saveScope(
                Long userId, String username, Long projectId,
                Map<DashboardMetricField,
                        DashboardMapping.StoredMapping> mappings) {
            saveCalls++;
            savedUserId = userId;
            savedUsername = username;
            savedProjectId = projectId;
            return new DashboardMapping.ResolvedMappings(
                    Collections.emptyMap());
        }
    }

    private static class FakeApplications extends DashboardApplicationService {
        private FakeApplications() {
            super(null);
        }

        @Override
        public DashboardResponses.Applications analyze(
                DashboardCriteria criteria,
                DashboardAccessContext access,
                DashboardMapping.ResolvedMappings mappings) {
            return DashboardResponses.Applications.hidden(criteria);
        }
    }

    private static class FakeOperations extends DashboardOperationsService {
        private FakeOperations() {
            super(null);
        }
    }

    private static class FakeGovernance extends DashboardGovernanceService {
        private FakeGovernance() {
            super(null, null);
        }
    }
}
