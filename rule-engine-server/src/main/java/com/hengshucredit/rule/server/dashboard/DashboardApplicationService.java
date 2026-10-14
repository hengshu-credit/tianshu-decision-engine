package com.hengshucredit.rule.server.dashboard;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class DashboardApplicationService {

    private static final List<AmountBucket> AMOUNT_BUCKETS = List.of(
            new AmountBucket("[0,1000)", "0–999", BigDecimal.ZERO,
                    new BigDecimal("1000")),
            new AmountBucket("[1000,3000)", "1,000–2,999",
                    new BigDecimal("1000"), new BigDecimal("3000")),
            new AmountBucket("[3000,5000)", "3,000–4,999",
                    new BigDecimal("3000"), new BigDecimal("5000")),
            new AmountBucket("[5000,10000)", "5,000–9,999",
                    new BigDecimal("5000"), new BigDecimal("10000")),
            new AmountBucket("[10000,30000)", "10,000–29,999",
                    new BigDecimal("10000"), new BigDecimal("30000")),
            new AmountBucket("[30000,50000)", "30,000–49,999",
                    new BigDecimal("30000"), new BigDecimal("50000")),
            new AmountBucket("[50000,100000)", "50,000–99,999",
                    new BigDecimal("50000"), new BigDecimal("100000")),
            new AmountBucket("[100000,+∞)", "100,000及以上",
                    new BigDecimal("100000"), null));

    private final DashboardQueryRepository repository;

    public DashboardApplicationService(DashboardQueryRepository repository) {
        this.repository = repository;
    }

    public DashboardResponses.Applications analyze(
            DashboardCriteria criteria,
            DashboardAccessContext access,
            DashboardMapping.ResolvedMappings mappings) {
        if (!access.can("rule:view")) {
            return DashboardResponses.Applications.hidden(criteria);
        }
        List<DashboardQueryRepository.ApplicationRow> rows = latest(
                repository.applicationRows(criteria, mappings));
        long pass = 0L;
        long review = 0L;
        long reject = 0L;
        Set<String> devices = new HashSet<>();
        TreeMap<Long, Long> periods = new TreeMap<>();
        long excludedPeriods = 0L;
        long[] amounts = new long[AMOUNT_BUCKETS.size()];
        long excludedAmounts = 0L;
        Map<GridKey, GridStats> grids = new TreeMap<>();
        Map<String, Long> coordinateExclusions = new LinkedHashMap<>();

        DashboardMapping.ResolvedMapping decisionMapping = mappings.get(
                DashboardMetricField.DECISION_RESULT);
        DashboardMapping.DecisionValues decisions = decisionMapping == null
                ? null : decisionMapping.decisionValues();

        for (DashboardQueryRepository.ApplicationRow row : rows) {
            Decision classification = classify(row.decision(), decisions);
            if (classification == Decision.PASS) pass++;
            if (classification == Decision.REVIEW) review++;
            if (classification == Decision.REJECT) reject++;
            if (hasText(row.device())) devices.add(row.device().trim());

            Long period = nonNegativeInteger(row.period());
            if (period == null) {
                excludedPeriods++;
            } else {
                periods.merge(period, 1L, Long::sum);
            }

            BigDecimal amount = nonNegativeDecimal(row.amount());
            if (amount == null) {
                excludedAmounts++;
            } else {
                amounts[amountBucket(amount)]++;
            }

            Coordinate coordinate = coordinate(row.longitude(), row.latitude());
            if (coordinate.reason != null) {
                coordinateExclusions.merge(coordinate.reason, 1L, Long::sum);
            } else {
                grids.computeIfAbsent(new GridKey(coordinate.longitude,
                        coordinate.latitude), key -> new GridStats())
                        .add(classification);
            }
        }

        long count = rows.size();
        long unclassified = count - pass - review - reject;
        DashboardResponses.ApplicationSummary summary =
                new DashboardResponses.ApplicationSummary(count, pass, review,
                        reject, unclassified, devices.size(), rate(pass, count),
                        rate(review, count), count == 0L);
        DashboardResponses.Distribution periodDistribution = periods(
                periods, count - excludedPeriods, excludedPeriods);
        DashboardResponses.Distribution amountDistribution = amounts(
                amounts, count - excludedAmounts, excludedAmounts);
        DashboardResponses.GeoDistribution geo = geo(grids,
                count - coordinateExclusions.values().stream()
                        .mapToLong(Long::longValue).sum(),
                coordinateExclusions);
        return new DashboardResponses.Applications(true,
                DashboardResponses.Metadata.from(criteria), summary,
                periodDistribution, amountDistribution, geo,
                mappingIssues(mappings));
    }

    private List<DashboardQueryRepository.ApplicationRow> latest(
            List<DashboardQueryRepository.ApplicationRow> source) {
        Map<String, DashboardQueryRepository.ApplicationRow> latest =
                new LinkedHashMap<>();
        if (source == null) return List.of();
        for (DashboardQueryRepository.ApplicationRow row : source) {
            if (row == null || row.id() == null) continue;
            String key = applicationKey(row);
            DashboardQueryRepository.ApplicationRow current = latest.get(key);
            if (current == null || newer(row, current)) latest.put(key, row);
        }
        return new ArrayList<>(latest.values());
    }

    private String applicationKey(DashboardQueryRepository.ApplicationRow row) {
        String project = row.projectCode() == null ? "" : row.projectCode();
        if (hasText(row.requestId())) {
            return project + "|REQ:" + row.requestId().trim();
        }
        if (hasText(row.traceId())) {
            return project + "|TRACE:" + row.traceId().trim();
        }
        return project + "|ID:" + row.id();
    }

    private boolean newer(DashboardQueryRepository.ApplicationRow candidate,
                          DashboardQueryRepository.ApplicationRow current) {
        if (candidate.createTime() == null) return false;
        if (current.createTime() == null) return true;
        int time = candidate.createTime().compareTo(current.createTime());
        return time > 0 || (time == 0 && candidate.id() > current.id());
    }

    private DashboardResponses.Distribution periods(Map<Long, Long> counts,
                                                     long valid,
                                                     long excluded) {
        List<Map.Entry<Long, Long>> ordered = new ArrayList<>(counts.entrySet());
        ordered.sort(Map.Entry.<Long, Long>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));
        List<DashboardResponses.DistributionItem> items = new ArrayList<>();
        long other = 0L;
        for (int index = 0; index < ordered.size(); index++) {
            Map.Entry<Long, Long> entry = ordered.get(index);
            if (index < 7) {
                String value = String.valueOf(entry.getKey());
                items.add(new DashboardResponses.DistributionItem(value,
                        value + "期", entry.getValue(),
                        rate(entry.getValue(), valid)));
            } else {
                other += entry.getValue();
            }
        }
        if (other > 0L) {
            items.add(new DashboardResponses.DistributionItem("OTHER", "其他",
                    other, rate(other, valid)));
        }
        return new DashboardResponses.Distribution(items, valid, excluded);
    }

    private DashboardResponses.Distribution amounts(long[] counts,
                                                     long valid,
                                                     long excluded) {
        List<DashboardResponses.DistributionItem> items = new ArrayList<>();
        for (int index = 0; index < AMOUNT_BUCKETS.size(); index++) {
            AmountBucket bucket = AMOUNT_BUCKETS.get(index);
            items.add(new DashboardResponses.DistributionItem(bucket.key,
                    bucket.label, counts[index], rate(counts[index], valid)));
        }
        return new DashboardResponses.Distribution(items, valid, excluded);
    }

    private DashboardResponses.GeoDistribution geo(Map<GridKey, GridStats> grids,
                                                    long valid,
                                                    Map<String, Long> excluded) {
        List<DashboardResponses.GeoPoint> points = grids.entrySet().stream()
                .map(entry -> new DashboardResponses.GeoPoint(
                        entry.getKey().longitude.doubleValue(),
                        entry.getKey().latitude.doubleValue(), entry.getValue().count,
                        entry.getValue().passCount, entry.getValue().reviewCount,
                        entry.getValue().rejectCount))
                .sorted(Comparator.comparingLong(
                        DashboardResponses.GeoPoint::count).reversed()
                        .thenComparingDouble(
                                DashboardResponses.GeoPoint::longitude)
                        .thenComparingDouble(
                                DashboardResponses.GeoPoint::latitude))
                .toList();
        long excludedCount = excluded.values().stream()
                .mapToLong(Long::longValue).sum();
        return new DashboardResponses.GeoDistribution(points, valid,
                excludedCount, excluded);
    }

    private List<DashboardResponses.MappingIssue> mappingIssues(
            DashboardMapping.ResolvedMappings mappings) {
        if (mappings == null) return List.of();
        return mappings.values().values().stream()
                .filter(value -> !value.valid())
                .map(value -> new DashboardResponses.MappingIssue(
                        value.metricField(), value.errorMessage()))
                .toList();
    }

    private Decision classify(String value,
                              DashboardMapping.DecisionValues decisions) {
        if (!hasText(value) || decisions == null) return Decision.UNKNOWN;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (decisions.pass().contains(normalized)) return Decision.PASS;
        if (decisions.review().contains(normalized)) return Decision.REVIEW;
        if (decisions.reject().contains(normalized)) return Decision.REJECT;
        return Decision.UNKNOWN;
    }

    private Long nonNegativeInteger(String value) {
        BigDecimal decimal = decimal(value);
        if (decimal == null || decimal.signum() < 0) return null;
        try {
            return decimal.stripTrailingZeros().longValueExact();
        } catch (ArithmeticException ignored) {
            return null;
        }
    }

    private BigDecimal nonNegativeDecimal(String value) {
        BigDecimal decimal = decimal(value);
        return decimal == null || decimal.signum() < 0 ? null : decimal;
    }

    private BigDecimal decimal(String value) {
        if (!hasText(value)) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int amountBucket(BigDecimal value) {
        for (int index = 0; index < AMOUNT_BUCKETS.size(); index++) {
            AmountBucket bucket = AMOUNT_BUCKETS.get(index);
            if (bucket.upper == null || value.compareTo(bucket.upper) < 0) {
                return index;
            }
        }
        return AMOUNT_BUCKETS.size() - 1;
    }

    private Coordinate coordinate(String longitude, String latitude) {
        if (!hasText(longitude) || !hasText(latitude)) {
            return Coordinate.excluded("EMPTY");
        }
        BigDecimal lng = decimal(longitude);
        BigDecimal lat = decimal(latitude);
        if (lng == null || lat == null) {
            return Coordinate.excluded("INVALID_NUMBER");
        }
        if (lng.compareTo(new BigDecimal("-180")) < 0
                || lng.compareTo(new BigDecimal("180")) > 0
                || lat.compareTo(new BigDecimal("-90")) < 0
                || lat.compareTo(new BigDecimal("90")) > 0) {
            return Coordinate.excluded("OUT_OF_RANGE");
        }
        return new Coordinate(lng.setScale(2, RoundingMode.HALF_UP),
                lat.setScale(2, RoundingMode.HALF_UP), null);
    }

    private double rate(long numerator, long denominator) {
        return denominator == 0L ? 0D : (double) numerator / denominator;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private enum Decision {
        PASS,
        REVIEW,
        REJECT,
        UNKNOWN
    }

    private record AmountBucket(String key,
                                String label,
                                BigDecimal lower,
                                BigDecimal upper) {
    }

    private record Coordinate(BigDecimal longitude,
                              BigDecimal latitude,
                              String reason) {
        private static Coordinate excluded(String reason) {
            return new Coordinate(null, null, reason);
        }
    }

    private record GridKey(BigDecimal longitude,
                           BigDecimal latitude)
            implements Comparable<GridKey> {
        @Override
        public int compareTo(GridKey other) {
            int longitudeOrder = longitude.compareTo(other.longitude);
            return longitudeOrder != 0 ? longitudeOrder
                    : latitude.compareTo(other.latitude);
        }
    }

    private static final class GridStats {
        private long count;
        private long passCount;
        private long reviewCount;
        private long rejectCount;

        private void add(Decision decision) {
            count++;
            if (decision == Decision.PASS) passCount++;
            if (decision == Decision.REVIEW) reviewCount++;
            if (decision == Decision.REJECT) rejectCount++;
        }
    }
}
