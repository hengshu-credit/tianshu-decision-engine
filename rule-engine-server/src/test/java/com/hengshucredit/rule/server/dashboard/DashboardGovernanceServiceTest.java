package com.hengshucredit.rule.server.dashboard;

import com.hengshucredit.rule.server.service.RuleExecutionLogService;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DashboardGovernanceServiceTest {

    private final DashboardCriteria criteria = new DashboardCriteria(
            9L, "P9", LocalDateTime.of(2026, 8, 1, 0, 0),
            LocalDateTime.of(2026, 8, 2, 0, 0));

    @Test
    public void invisibleSectionsAreNotQueried() {
        FakeRepository repository = new FakeRepository();
        FakeLogService logs = new FakeLogService();
        DashboardGovernanceService service =
                new DashboardGovernanceService(repository, logs);

        DashboardResponses.Governance result = service.analyze(criteria,
                access());

        assertFalse(result.ruleSetHits().visible());
        assertFalse(result.approvals().visible());
        assertEquals(0, repository.approvalQueries);
        assertEquals(0, logs.queries);
    }

    @Test
    public void sortsRuleSetTopAndFillsAllApprovalStatuses() {
        FakeRepository repository = new FakeRepository();
        repository.approvals = List.of(
                new DashboardQueryRepository.CategoryCount("PENDING", 2L),
                new DashboardQueryRepository.CategoryCount("APPROVED", 3L));
        FakeLogService logs = new FakeLogService();
        logs.result = ruleSetStats();
        DashboardGovernanceService service =
                new DashboardGovernanceService(repository, logs);

        DashboardResponses.Governance result = service.analyze(criteria,
                access("rule:view", "approval:view"));

        assertTrue(result.ruleSetHits().visible());
        assertEquals("R2", result.ruleSetHits().items().get(0).ruleCode());
        assertEquals(10, result.ruleSetHits().items().size());
        assertTrue(result.approvals().visible());
        assertEquals(6, result.approvals().statuses().size());
        assertEquals(5L, result.approvals().totalCount());
        assertEquals("EDITING",
                result.approvals().statuses().get(0).key());
        assertEquals(1, logs.queries);
        assertEquals("P9", logs.projectCode);
    }

    @Test
    public void ruleSetTopUsesBothRuleCodeAndNameFilters() {
        FakeRepository repository = new FakeRepository();
        FakeLogService logs = new FakeLogService();
        logs.result = ruleSetStats();
        DashboardGovernanceService service =
                new DashboardGovernanceService(repository, logs);
        DashboardCriteria filtered = new DashboardCriteria(
                9L, "P9", "R2", "规则2",
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 2, 0, 0));

        DashboardResponses.Governance result = service.analyze(filtered,
                access("rule:view"));

        assertEquals(1, result.ruleSetHits().items().size());
        assertEquals("R2", result.ruleSetHits().items().get(0).ruleCode());
    }

    private Map<String, Object> ruleSetStats() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ruleCode", index == 2 ? "R2" : "R" + index);
            row.put("ruleName", "规则" + index);
            row.put("evaluationCount", 20L + index);
            row.put("hitCount", index == 2 ? 100L : index);
            row.put("hitRate", index == 2 ? 5D : (double) index / 20D);
            rows.add(row);
        }
        return Map.of("ruleSets", rows);
    }

    @Test
    public void ruleSetSectionKeepsCoverageFromExecutionStatistics() {
        FakeLogService logs = new FakeLogService();
        Map<String, Object> coverage = Map.of("pendingCount", 5L, "invalidCount", 2L);
        logs.result = Map.of("ruleSets", List.of(), "coverage", coverage);
        DashboardResponses.Governance result = new DashboardGovernanceService(new FakeRepository(), logs)
                .analyze(criteria, access("rule:view"));
        assertEquals(coverage, result.ruleSetHits().coverage());
        assertTrue(result.ruleSetHits().items().isEmpty());
    }

    private DashboardAccessContext access(String... permissions) {
        return new DashboardAccessContext(false, 7L, "tester",
                Set.of(permissions));
    }

    private static class FakeRepository extends DashboardQueryRepository {
        private int approvalQueries;
        private List<CategoryCount> approvals = List.of();

        private FakeRepository() {
            super(null);
        }

        @Override
        public List<CategoryCount> approvalCounts(
                DashboardCriteria criteria) {
            approvalQueries++;
            return approvals;
        }
    }

    private static class FakeLogService extends RuleExecutionLogService {
        private int queries;
        private String projectCode;
        private Map<String, Object> result = Map.of();

        @Override
        public Map<String, Object> ruleSetStats(String projectCode,
                                                String projectName,
                                                String ruleCode,
                                                LocalDateTime startTime,
                                                LocalDateTime endTime) {
            queries++;
            this.projectCode = projectCode;
            return result;
        }
    }
}
