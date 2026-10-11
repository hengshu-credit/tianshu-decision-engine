package com.hengshucredit.rule.server.dashboard;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DashboardOperationsServiceTest {

    private final DashboardCriteria criteria = new DashboardCriteria(
            9L, "P9", LocalDateTime.of(2026, 8, 1, 0, 0),
            LocalDateTime.of(2026, 8, 2, 0, 0));

    @Test
    public void invisibleModulesAreNotQueried() {
        FakeRepository repository = new FakeRepository();
        DashboardOperationsService service =
                new DashboardOperationsService(repository);

        DashboardResponses.Operations result = service.analyze(criteria,
                access("rule:view"));

        assertTrue(result.ruleExecution().visible());
        assertFalse(result.database().visible());
        assertFalse(result.lists().visible());
        assertFalse(result.datasource().visible());
        assertFalse(result.billing().visible());
        assertEquals(List.of("execution", "rules"), repository.calls);
    }

    @Test
    public void computesPercentilesFoundRateAndKeepsCurrenciesSeparate() {
        FakeRepository repository = new FakeRepository();
        List<Long> durations = new ArrayList<>();
        for (long value = 1L; value <= 100L; value++) durations.add(value);
        durations.add(null);
        durations.add(-1L);
        repository.executionDurations = durations;
        repository.datasourceCalls = List.of(
                call(true, true, true, 100L),
                call(true, false, true, 300L),
                call(false, false, true, 500L));
        repository.billing = List.of(
                new DashboardQueryRepository.BillingAmount("CNY",
                        new BigDecimal("12.30")),
                new DashboardQueryRepository.BillingAmount("USD",
                        new BigDecimal("4.50")));
        DashboardOperationsService service =
                new DashboardOperationsService(repository);

        DashboardResponses.Operations result = service.analyze(criteria,
                access("rule:view", "database:view", "list:view",
                        "datasource:view", "billing:view"));

        assertEquals(100L, result.ruleExecution().timing().sampleCount());
        assertEquals(95D, result.ruleExecution().timing().p95Ms(), 0D);
        assertEquals(99D, result.ruleExecution().timing().p99Ms(), 0D);
        assertEquals(3L, result.datasource().calls().requestCount());
        assertEquals(1L, result.datasource().calls().foundCount());
        assertEquals(1D / 3D, result.datasource().calls().foundRate(), 0D);
        assertEquals(2, result.billing().amounts().size());
        assertEquals(new BigDecimal("12.30"),
                result.billing().amounts().get(0).amount());
    }

    private DashboardQueryRepository.RuntimeCall call(boolean success,
                                                       boolean found,
                                                       boolean providerRequest,
                                                       Long cost) {
        return new DashboardQueryRepository.RuntimeCall(success, success,
                found, providerRequest, cost);
    }

    private DashboardAccessContext access(String... permissions) {
        return new DashboardAccessContext(false, 7L, "tester",
                Set.of(permissions));
    }

    private static class FakeRepository extends DashboardQueryRepository {
        private final List<String> calls = new ArrayList<>();
        private List<Long> executionDurations = List.of();
        private List<RuntimeCall> datasourceCalls = List.of();
        private List<BillingAmount> billing = List.of();

        private FakeRepository() {
            super(null);
        }

        @Override
        public List<Long> executionDurations(DashboardCriteria criteria) {
            calls.add("execution");
            return executionDurations;
        }

        @Override
        public List<RuntimeCall> runtimeCalls(DashboardCriteria criteria,
                                              String moduleType) {
            calls.add(moduleType.toLowerCase());
            return "DATASOURCE".equals(moduleType)
                    ? datasourceCalls : List.of();
        }

        @Override
        public DashboardQueryRepository.ResourceCounts resourceCounts(
                DashboardCriteria criteria) {
            calls.add("resources");
            return new ResourceCounts(2L, 3L, 4L,
                    List.of(new CategoryCount("BLACK", 2L)));
        }

        @Override
        public List<BillingAmount> billingByCurrency(
                DashboardCriteria criteria) {
            calls.add("billing");
            return billing;
        }

        @Override
        public long downstreamRuleCount(DashboardCriteria criteria) {
            calls.add("rules");
            return 5L;
        }
    }
}
