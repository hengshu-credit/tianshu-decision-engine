package com.hengshucredit.rule.server.dashboard;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DashboardOperationsService {

    private static final List<DurationBucket> DURATION_BUCKETS = List.of(
            new DurationBucket("[0,10)", "0–9 ms", 10L),
            new DurationBucket("[10,50)", "10–49 ms", 50L),
            new DurationBucket("[50,200)", "50–199 ms", 200L),
            new DurationBucket("[200,1000)", "200–999 ms", 1000L),
            new DurationBucket("[1000,3000)", "1–3 s", 3000L),
            new DurationBucket("[3000,+∞)", "3 s 及以上", null));

    private final DashboardQueryRepository repository;

    public DashboardOperationsService(DashboardQueryRepository repository) {
        this.repository = repository;
    }

    public DashboardResponses.Operations analyze(
            DashboardCriteria criteria,
            DashboardAccessContext access) {
        DashboardResponses.RuleExecutionSection ruleExecution =
                DashboardResponses.RuleExecutionSection.hidden();
        DashboardResponses.ModuleSection database =
                DashboardResponses.ModuleSection.hidden();
        DashboardResponses.ListSection lists =
                DashboardResponses.ListSection.hidden();
        DashboardResponses.ModuleSection datasource =
                DashboardResponses.ModuleSection.hidden();
        DashboardResponses.RuleCountSection rules =
                DashboardResponses.RuleCountSection.hidden();
        DashboardResponses.BillingSection billing =
                DashboardResponses.BillingSection.hidden();

        if (access.can("rule:view")) {
            ruleExecution = new DashboardResponses.RuleExecutionSection(true,
                    timing(repository.executionDurations(criteria)));
            rules = new DashboardResponses.RuleCountSection(true,
                    repository.downstreamRuleCount(criteria));
        }

        DashboardQueryRepository.ResourceCounts resources = null;
        if (access.can("database:view") || access.can("list:view")
                || access.can("datasource:view")) {
            resources = repository.resourceCounts(criteria);
        }
        if (access.can("database:view")) {
            database = new DashboardResponses.ModuleSection(true,
                    resources.databaseCount(), calls(repository.runtimeCalls(
                    criteria, "DATABASE"), false));
        }
        if (access.can("list:view")) {
            lists = new DashboardResponses.ListSection(true,
                    resources.listCount(), categories(
                    resources.listCategories(), resources.listCount()),
                    calls(repository.runtimeCalls(criteria, "LIST"), false));
        }
        if (access.can("datasource:view")) {
            datasource = new DashboardResponses.ModuleSection(true,
                    resources.apiCount(), calls(repository.runtimeCalls(
                    criteria, "DATASOURCE"), true));
        }
        if (access.can("billing:view")) {
            List<DashboardResponses.CurrencyAmount> amounts = repository
                    .billingByCurrency(criteria).stream()
                    .map(value -> new DashboardResponses.CurrencyAmount(
                            value.currency(), value.amount()))
                    .toList();
            billing = new DashboardResponses.BillingSection(true, amounts);
        }
        return new DashboardResponses.Operations(
                DashboardResponses.Metadata.from(criteria), ruleExecution,
                database, lists, datasource, rules, billing);
    }

    private DashboardResponses.CallSummary calls(
            List<DashboardQueryRepository.RuntimeCall> source,
            boolean providerOnly) {
        List<Long> durations = new ArrayList<>();
        long requests = 0L;
        long success = 0L;
        long found = 0L;
        for (DashboardQueryRepository.RuntimeCall call
                : source == null
                ? Collections.<DashboardQueryRepository.RuntimeCall>emptyList()
                : source) {
            if (call == null || (providerOnly
                    && !Boolean.TRUE.equals(call.providerRequest()))) {
                continue;
            }
            requests++;
            boolean requestSucceeded = call.requestSuccess() == null
                    ? call.success() : call.requestSuccess();
            if (requestSucceeded) success++;
            if (Boolean.TRUE.equals(call.found())) found++;
            if (call.costTimeMs() != null && call.costTimeMs() >= 0L) {
                durations.add(call.costTimeMs());
            }
        }
        return new DashboardResponses.CallSummary(requests, success,
                requests - success, found, rate(found, requests),
                timing(durations));
    }

    private DashboardResponses.Timing timing(List<Long> source) {
        List<Long> values = source == null ? new ArrayList<>() : source.stream()
                .filter(value -> value != null && value >= 0L)
                .sorted()
                .toList();
        if (values.isEmpty()) return DashboardResponses.Timing.empty();
        long total = values.stream().mapToLong(Long::longValue).sum();
        long[] bucketCounts = new long[DURATION_BUCKETS.size()];
        for (Long value : values) bucketCounts[durationBucket(value)]++;
        List<DashboardResponses.DistributionItem> items = new ArrayList<>();
        for (int index = 0; index < DURATION_BUCKETS.size(); index++) {
            DurationBucket bucket = DURATION_BUCKETS.get(index);
            items.add(new DashboardResponses.DistributionItem(bucket.key(),
                    bucket.label(), bucketCounts[index],
                    rate(bucketCounts[index], values.size())));
        }
        return new DashboardResponses.Timing(values.size(),
                (double) total / values.size(), percentile(values, 0.95D),
                percentile(values, 0.99D),
                new DashboardResponses.Distribution(items, values.size(), 0L));
    }

    private DashboardResponses.Distribution categories(
            List<DashboardQueryRepository.CategoryCount> source,
            long total) {
        List<DashboardResponses.DistributionItem> items = source.stream()
                .map(item -> new DashboardResponses.DistributionItem(
                        item.key(), item.key(), item.count(),
                        rate(item.count(), total)))
                .toList();
        return new DashboardResponses.Distribution(items, total, 0L);
    }

    private int durationBucket(long value) {
        for (int index = 0; index < DURATION_BUCKETS.size(); index++) {
            Long upper = DURATION_BUCKETS.get(index).upperExclusive();
            if (upper == null || value < upper) return index;
        }
        return DURATION_BUCKETS.size() - 1;
    }

    private long percentile(List<Long> sorted, double percentile) {
        int index = Math.max(0,
                (int) Math.ceil(percentile * sorted.size()) - 1);
        return sorted.get(index);
    }

    private double rate(long numerator, long denominator) {
        return denominator == 0L ? 0D : (double) numerator / denominator;
    }

    private record DurationBucket(String key,
                                  String label,
                                  Long upperExclusive) {
    }
}
