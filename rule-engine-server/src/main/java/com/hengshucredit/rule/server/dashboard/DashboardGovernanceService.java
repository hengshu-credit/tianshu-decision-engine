package com.hengshucredit.rule.server.dashboard;

import com.hengshucredit.rule.model.enums.GovernanceRequestStatus;
import com.hengshucredit.rule.server.service.RuleExecutionLogService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DashboardGovernanceService {

    private final DashboardQueryRepository repository;
    private final RuleExecutionLogService executionLogService;

    public DashboardGovernanceService(
            DashboardQueryRepository repository,
            RuleExecutionLogService executionLogService) {
        this.repository = repository;
        this.executionLogService = executionLogService;
    }

    public DashboardResponses.Governance analyze(
            DashboardCriteria criteria,
            DashboardAccessContext access) {
        DashboardResponses.RuleSetHitSection hits =
                DashboardResponses.RuleSetHitSection.hidden();
        DashboardResponses.ApprovalSection approvals =
                DashboardResponses.ApprovalSection.hidden();
        if (access.can("rule:view")) {
            Map<String, Object> stats = executionLogService.ruleSetStats(
                    criteria.projectCode(), null, null, criteria.startTime(),
                    criteria.endTime());
            hits = new DashboardResponses.RuleSetHitSection(true,
                    ruleSetHits(stats, criteria), stats.get("coverage") instanceof Map<?, ?> coverage ? coverage : Map.of());
        }
        if (access.can("approval:view")) {
            approvals = approvals(repository.approvalCounts(criteria));
        }
        return new DashboardResponses.Governance(
                DashboardResponses.Metadata.from(criteria), hits, approvals);
    }

    private List<DashboardResponses.RuleSetHit> ruleSetHits(
            Map<String, Object> stats,
            DashboardCriteria criteria) {
        Object value = stats == null ? null : stats.get("ruleSets");
        if (!(value instanceof List<?> rows)) return List.of();
        List<DashboardResponses.RuleSetHit> result = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> item)) continue;
            result.add(new DashboardResponses.RuleSetHit(
                    text(item.get("ruleCode")), text(item.get("ruleName")),
                    longValue(item.get("evaluationCount")),
                    longValue(item.get("hitCount")),
                    doubleValue(item.get("hitRate"))));
        }
        return result.stream()
                .filter(item -> matches(item, criteria))
                .sorted(
                        Comparator.comparingLong(
                                DashboardResponses.RuleSetHit::hitCount)
                                .reversed()
                                .thenComparing(Comparator.comparingLong(
                                        DashboardResponses.RuleSetHit::applicationCount)
                                        .reversed())
                                .thenComparing(item -> item.ruleCode() == null
                                        ? "" : item.ruleCode()))
                .limit(10)
                .toList();
    }

    private boolean matches(DashboardResponses.RuleSetHit item,
                            DashboardCriteria criteria) {
        return contains(item.ruleCode(), criteria.ruleCode())
                && contains(item.ruleName(), criteria.ruleName());
    }

    private boolean contains(String value, String filter) {
        if (filter == null) return true;
        return value != null && value.toLowerCase(Locale.ROOT)
                .contains(filter.toLowerCase(Locale.ROOT));
    }

    private DashboardResponses.ApprovalSection approvals(
            List<DashboardQueryRepository.CategoryCount> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (rows != null) {
            for (DashboardQueryRepository.CategoryCount row : rows) {
                counts.put(row.key(), row.count());
            }
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        List<DashboardResponses.DistributionItem> items = new ArrayList<>();
        for (GovernanceRequestStatus status : GovernanceRequestStatus.values()) {
            long count = counts.getOrDefault(status.name(), 0L);
            items.add(new DashboardResponses.DistributionItem(status.name(),
                    status.name(), count, total == 0L
                    ? 0D : (double) count / total));
        }
        return new DashboardResponses.ApprovalSection(true, total, items);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0D;
    }
}
