package com.hengshucredit.rule.server.dashboard;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hengshucredit.rule.server.dashboard.DashboardMapping.JsonSource.INPUT;
import static com.hengshucredit.rule.server.dashboard.DashboardMapping.MappingSource.SYSTEM_DEFAULT;
import static com.hengshucredit.rule.server.dashboard.DashboardMapping.RefType.VARIABLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DashboardApplicationServiceTest {

    @Test
    public void latestExecutionWinsAndDecisionDefaultsSupportBothValueFamilies() {
        List<DashboardQueryRepository.ApplicationRow> rows = new ArrayList<>();
        rows.add(row(1L, "P1", "T1", "REQ-1", "0", "D1",
                "12", "999", "120.1", "30.2", time(1)));
        rows.add(row(2L, "P1", "T2", "REQ-1", "102", "D2",
                "24", "3000", "120.2", "30.3", time(2)));
        rows.add(row(3L, "P1", "T3", "REQ-2", "PASS", "D2",
                "12", "100000", "120.2", "30.3", time(3)));
        rows.add(row(4L, "P2", "T4", "REQ-1", "REJECT", "D3",
                "6", "0", "120.2", "30.3", time(4)));

        DashboardResponses.Applications result = service(rows).analyze(
                criteria(), access("rule:view"), validMappings());

        assertEquals(3L, result.summary().applicationCount());
        assertEquals(1L, result.summary().passCount());
        assertEquals(1L, result.summary().reviewCount());
        assertEquals(1L, result.summary().rejectCount());
        assertEquals(2L, result.summary().deviceCount());
        assertEquals(1D / 3D, result.summary().passRate(), 0.000001D);
        assertFalse(result.summary().empty());
        DashboardResponses.GeoPoint grid = result.geo().points().stream()
                .filter(point -> point.longitude() == 120.2D
                        && point.latitude() == 30.3D)
                .findFirst()
                .orElseThrow();
        assertEquals(3L, grid.count());
        assertEquals(1L, grid.passCount());
        assertEquals(1L, grid.reviewCount());
        assertEquals(1L, grid.rejectCount());
    }

    @Test
    public void periodAndAmountUseExactBusinessBucketsAndReportExcludedRows() {
        List<DashboardQueryRepository.ApplicationRow> rows = List.of(
                row(1L, "P1", "T1", "1", "0", "D1",
                        "12", "999.99", null, null, time(1)),
                row(2L, "P1", "T2", "2", "0", "D2",
                        "12.0", "1000", null, null, time(2)),
                row(3L, "P1", "T3", "3", "0", "D3",
                        "-1", "2999.99", null, null, time(3)),
                row(4L, "P1", "T4", "4", "0", "D4",
                        "abc", "3000", null, null, time(4)),
                row(5L, "P1", "T5", "5", "0", "D5",
                        "6", "100000", null, null, time(5)),
                row(6L, "P1", "T6", "6", "0", "D6",
                        "7", "Infinity", null, null, time(6)));

        DashboardResponses.Applications result = service(rows).analyze(
                criteria(), access("rule:view"), validMappings());

        assertEquals(4L, result.periods().validCount());
        assertEquals(2L, result.periods().excludedCount());
        assertEquals(2L, item(result.periods(), "12").count());
        assertEquals(5L, result.amounts().validCount());
        assertEquals(1L, result.amounts().excludedCount());
        assertEquals(1L, item(result.amounts(), "[0,1000)").count());
        assertEquals(2L, item(result.amounts(), "[1000,3000)").count());
        assertEquals(1L, item(result.amounts(), "[3000,5000)").count());
        assertEquals(1L, item(result.amounts(), "[100000,+∞)").count());
    }

    @Test
    public void coordinatesKeepOnlyFiniteInRangeValuesWithoutMutatingRows() {
        List<DashboardQueryRepository.ApplicationRow> rows = new ArrayList<>();
        rows.add(row(1L, "P1", "T1", "1", "0", "D1",
                "1", "1", "180", "90", time(1)));
        rows.add(row(2L, "P1", "T2", "2", "0", "D2",
                "1", "1", "-180", "-90", time(2)));
        rows.add(row(3L, "P1", "T3", "3", "0", "D3",
                "1", "1", "Infinity", "30", time(3)));
        rows.add(row(4L, "P1", "T4", "4", "0", "D4",
                "1", "1", "181", "30", time(4)));
        rows.add(row(5L, "P1", "T5", "5", "0", "D5",
                "1", "1", "120.126", "30.124", time(5)));
        rows.add(row(6L, "P1", "T6", "6", "0", "D6",
                "1", "1", "120.125", "30.125", time(6)));

        DashboardResponses.Applications result = service(rows).analyze(
                criteria(), access("rule:view"), validMappings());

        assertEquals(4L, result.geo().validCount());
        assertEquals(2L, result.geo().excludedCount());
        assertEquals(Long.valueOf(1L), result.geo().excludedReasons()
                .get("INVALID_NUMBER"));
        assertEquals(Long.valueOf(1L), result.geo().excludedReasons()
                .get("OUT_OF_RANGE"));
        assertTrue(result.geo().points().stream().anyMatch(point ->
                point.longitude() == 120.13D && point.latitude() == 30.12D));
        assertTrue(result.geo().points().stream().anyMatch(point ->
                point.longitude() == 120.13D && point.latitude() == 30.13D));
        assertEquals("Infinity", rows.get(2).longitude());
        assertEquals("181", rows.get(3).longitude());
    }

    @Test
    public void rulePermissionHidesApplicationsWithoutQueryingRows() {
        CountingRepository repository = new CountingRepository(List.of());
        DashboardResponses.Applications result =
                new DashboardApplicationService(repository).analyze(
                        criteria(), access("database:view"), validMappings());

        assertFalse(result.visible());
        assertEquals(0, repository.applicationQueries);
    }

    private DashboardApplicationService service(
            List<DashboardQueryRepository.ApplicationRow> rows) {
        return new DashboardApplicationService(new CountingRepository(rows));
    }

    private DashboardResponses.DistributionItem item(
            DashboardResponses.Distribution distribution, String key) {
        return distribution.items().stream()
                .filter(value -> key.equals(value.key()))
                .findFirst()
                .orElseThrow();
    }

    private DashboardAccessContext access(String... permissions) {
        return new DashboardAccessContext(false, 7L, "alice",
                Set.of(permissions));
    }

    private DashboardCriteria criteria() {
        return new DashboardCriteria(null, null,
                LocalDateTime.of(2026, 8, 20, 0, 0),
                LocalDateTime.of(2026, 8, 26, 23, 59));
    }

    private DashboardMapping.ResolvedMappings validMappings() {
        Map<DashboardMetricField, DashboardMapping.ResolvedMapping> values =
                new EnumMap<>(DashboardMetricField.class);
        for (DashboardMetricField field : DashboardMetricField.values()) {
            DashboardMapping.DecisionValues decisions =
                    field == DashboardMetricField.DECISION_RESULT
                            ? new DashboardMapping.DecisionValues(
                            Set.of("0", "100", "PASS"),
                            Set.of("2", "102", "REVIEW"),
                            Set.of("1", "101", "REJECT"))
                            : null;
            values.put(field, new DashboardMapping.ResolvedMapping(field,
                    VARIABLE, field.defaultRefId(), INPUT,
                    "$.\"" + field.name() + "\"", field.name(),
                    SYSTEM_DEFAULT, true, null, decisions));
        }
        return new DashboardMapping.ResolvedMappings(values);
    }

    private DashboardQueryRepository.ApplicationRow row(
            Long id, String projectCode, String traceId, String requestId,
            String decision, String device, String period, String amount,
            String longitude, String latitude, LocalDateTime createTime) {
        return new DashboardQueryRepository.ApplicationRow(id, projectCode,
                traceId, requestId, decision, device, period, amount,
                longitude, latitude, createTime);
    }

    private LocalDateTime time(int minute) {
        return LocalDateTime.of(2026, 8, 20, 0, minute);
    }

    private static class CountingRepository extends DashboardQueryRepository {
        private final List<ApplicationRow> rows;
        private int applicationQueries;

        private CountingRepository(List<ApplicationRow> rows) {
            super(null);
            this.rows = rows;
        }

        @Override
        public List<ApplicationRow> applicationRows(
                DashboardCriteria criteria,
                DashboardMapping.ResolvedMappings mappings) {
            applicationQueries++;
            return rows;
        }
    }
}
