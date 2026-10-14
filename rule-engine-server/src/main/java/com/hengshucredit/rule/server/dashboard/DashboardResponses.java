package com.hengshucredit.rule.server.dashboard;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DashboardResponses {

    private DashboardResponses() {
    }

    public record Metadata(Long projectId,
                           String projectCode,
                           LocalDateTime startTime,
                           LocalDateTime endTime) {
        public static Metadata from(DashboardCriteria criteria) {
            return new Metadata(criteria.projectId(), criteria.projectCode(),
                    criteria.startTime(), criteria.endTime());
        }
    }

    public record MappingIssue(DashboardMetricField metricField,
                               String message) {
    }

    public record ApplicationSummary(long applicationCount,
                                     long passCount,
                                     long reviewCount,
                                     long rejectCount,
                                     long unclassifiedCount,
                                     long deviceCount,
                                     double passRate,
                                     double reviewRate,
                                     boolean empty) {
    }

    public record DistributionItem(String key,
                                   String label,
                                   long count,
                                   double rate) {
    }

    public record Distribution(List<DistributionItem> items,
                               long validCount,
                               long excludedCount) {
        public Distribution {
            items = items == null ? Collections.emptyList()
                    : List.copyOf(items);
        }

        public static Distribution empty() {
            return new Distribution(Collections.emptyList(), 0L, 0L);
        }
    }

    public record GeoPoint(double longitude,
                           double latitude,
                           long count,
                           long passCount,
                           long reviewCount,
                           long rejectCount) {
    }

    public record GeoDistribution(List<GeoPoint> points,
                                  long validCount,
                                  long excludedCount,
                                  Map<String, Long> excludedReasons) {
        public GeoDistribution {
            points = points == null ? Collections.emptyList()
                    : List.copyOf(points);
            excludedReasons = excludedReasons == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(
                    new LinkedHashMap<>(excludedReasons));
        }

        public static GeoDistribution empty() {
            return new GeoDistribution(Collections.emptyList(), 0L, 0L,
                    Collections.emptyMap());
        }
    }

    public record Applications(boolean visible,
                               Metadata metadata,
                               ApplicationSummary summary,
                               Distribution periods,
                               Distribution amounts,
                               GeoDistribution geo,
                               List<MappingIssue> mappingIssues) {
        public Applications {
            mappingIssues = mappingIssues == null ? Collections.emptyList()
                    : List.copyOf(mappingIssues);
        }

        public static Applications hidden(DashboardCriteria criteria) {
            return new Applications(false, Metadata.from(criteria),
                    new ApplicationSummary(0L, 0L, 0L, 0L, 0L, 0L,
                            0D, 0D, true),
                    Distribution.empty(), Distribution.empty(),
                    GeoDistribution.empty(), Collections.emptyList());
        }
    }

    public record Timing(long sampleCount,
                         double avgMs,
                         double p95Ms,
                         double p99Ms,
                         Distribution distribution) {
        public static Timing empty() {
            return new Timing(0L, 0D, 0D, 0D, Distribution.empty());
        }
    }

    public record CallSummary(long requestCount,
                              long successCount,
                              long failedCount,
                              long foundCount,
                              double foundRate,
                              Timing timing) {
        public static CallSummary empty() {
            return new CallSummary(0L, 0L, 0L, 0L, 0D, Timing.empty());
        }
    }

    public record RuleExecutionSection(boolean visible, Timing timing) {
        public static RuleExecutionSection hidden() {
            return new RuleExecutionSection(false, Timing.empty());
        }
    }

    public record ModuleSection(boolean visible,
                                long resourceCount,
                                CallSummary calls) {
        public static ModuleSection hidden() {
            return new ModuleSection(false, 0L, CallSummary.empty());
        }
    }

    public record ListSection(boolean visible,
                              long libraryCount,
                              Distribution categories,
                              CallSummary calls) {
        public static ListSection hidden() {
            return new ListSection(false, 0L, Distribution.empty(),
                    CallSummary.empty());
        }
    }

    public record RuleCountSection(boolean visible, long count) {
        public static RuleCountSection hidden() {
            return new RuleCountSection(false, 0L);
        }
    }

    public record CurrencyAmount(String currency, BigDecimal amount) {
    }

    public record BillingSection(boolean visible,
                                 List<CurrencyAmount> amounts) {
        public BillingSection {
            amounts = amounts == null ? Collections.emptyList()
                    : List.copyOf(amounts);
        }

        public static BillingSection hidden() {
            return new BillingSection(false, Collections.emptyList());
        }
    }

    public record Operations(Metadata metadata,
                             RuleExecutionSection ruleExecution,
                             ModuleSection database,
                             ListSection lists,
                             ModuleSection datasource,
                             RuleCountSection downstreamRules,
                             BillingSection billing) {
    }

    public record RuleSetHit(String ruleCode,
                             String ruleName,
                             long applicationCount,
                             long hitCount,
                             double hitRate) {
    }

    public record RuleSetHitSection(boolean visible,
                                    List<RuleSetHit> items,
                                    Map<?, ?> coverage) {
        public RuleSetHitSection {
            items = items == null ? Collections.emptyList()
                    : List.copyOf(items);
            coverage = coverage == null ? Map.of() : Map.copyOf(coverage);
        }

        public RuleSetHitSection(boolean visible, List<RuleSetHit> items) { this(visible, items, Map.of()); }

        public static RuleSetHitSection hidden() {
            return new RuleSetHitSection(false, Collections.emptyList());
        }
    }

    public record ApprovalSection(boolean visible,
                                  long totalCount,
                                  List<DistributionItem> statuses) {
        public ApprovalSection {
            statuses = statuses == null ? Collections.emptyList()
                    : List.copyOf(statuses);
        }

        public static ApprovalSection hidden() {
            return new ApprovalSection(false, 0L, Collections.emptyList());
        }
    }

    public record Governance(Metadata metadata,
                             RuleSetHitSection ruleSetHits,
                             ApprovalSection approvals) {
    }

    public record SettingField(DashboardMetricField metricField,
                               String label,
                               DashboardMapping.ResolvedMapping mapping) {
    }

    public record ReferenceOption(DashboardMapping.RefType refType,
                                  Long refId,
                                  String label,
                                  String valueType,
                                  String scope,
                                  Long projectId) {
    }

    public record Settings(boolean editable,
                           Long projectId,
                           boolean projectOverride,
                           List<SettingField> fields,
                           List<ReferenceOption> options) {
        public Settings {
            fields = fields == null ? Collections.emptyList()
                    : List.copyOf(fields);
            options = options == null ? Collections.emptyList()
                    : List.copyOf(options);
        }
    }
}
