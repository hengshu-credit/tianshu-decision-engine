package com.hengshucredit.rule.server.derived;

import com.hengshucredit.rule.server.functions.GeoFunctions;
import com.hengshucredit.rule.server.security.ConsoleDataPermissionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact COUNT over stable, recorded fields. Bounds only prune candidates; the final test uses spherical distance. */
@Repository
public class IndexedHistoryCount {
    public record Region(String longitudeKey, String latitudeKey, double longitude, double latitude, double radius) {
        public Region {
            if (!Double.isFinite(radius) || radius < 0) throw new IllegalArgumentException("地理半径必须是有限的非负米数");
            new GeoFunctions().distanceMeters(longitude, latitude, longitude, latitude);
        }
    }
    public record Selection(String scope, Long projectId, Long ruleId, LocalDateTime before, Region region) {}
    public record Count(long candidates, long matched) {}
    private record SpatialWindow(String scope, Long projectId, Long ruleId, Region region, LocalDateTime hour, long revision) {}
    private final com.github.benmanes.caffeine.cache.Cache<SpatialWindow, Long> closedWindows =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(32768).build();
    private final JdbcTemplate jdbc;
    @Autowired private ExecutionLogHistoryRepository history;
    @Autowired(required = false) private ConsoleDataPermissionHandler permissions;
    public IndexedHistoryCount(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void initializeEmptyIndex() {
        if (!jdbc.queryForList("SELECT format_version FROM rule_history_index_state WHERE format_version=1", Integer.class).isEmpty()) return;
        // An existing installation is enabled by the transactional backfill, never by a partial dual write.
        jdbc.update("INSERT INTO rule_history_index_state(format_version) SELECT 1 WHERE NOT EXISTS "
                + "(SELECT 1 FROM rule_execution_log WHERE source IN ('CLIENT_SERVER','EXPERIMENT_LIVE') "
                + "AND success=1 AND started_at IS NOT NULL AND history_fields IS NOT NULL) "
                + "ON DUPLICATE KEY UPDATE format_version=1");
    }

    /** All statements see a single snapshot. Empty results mean a required field needs legacy/API resolution. */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<LocalDateTime, Count> count(Selection selection, List<LocalDateTime> windows) {
        if (windows.isEmpty()) throw new IllegalArgumentException("缺少历史统计窗口");
        if (permissions != null) permissions.requireHistoryScope(selection.scope(), selection.projectId());
        if (jdbc.queryForList("SELECT format_version FROM rule_history_index_state WHERE format_version=1", Integer.class).isEmpty())
            return Map.of();
        var earliest = windows.stream().min(LocalDateTime::compareTo).orElseThrow();
        Region region = selection.region();
        // Aggregate buckets have no account dimension; scoped console reads must use the row permission predicate.
        boolean useBuckets = permissions == null || "1 = 1".equals(permissions.predicate("rule_history_root", "r"));
        long[] candidates = useBuckets ? bucketTotals(selection, earliest, windows, null)
                : totals("rule_history_root r", "r", selection, earliest, windows, "", List.of());
        long[] matches = candidates;
        boolean emptyWindow = Arrays.stream(candidates).allMatch(total -> total == 0);
        if (region != null && !emptyWindow) {
            // A missing key can have a historical API value even if it is an input today. Never treat it as zero.
            if (region.longitudeKey().equals(region.latitudeKey())) return Map.of();
            long[] valid = useBuckets ? bucketTotals(selection, earliest, windows, region)
                    : totals("rule_history_numeric_field f", "f", selection, earliest, windows,
                    " AND ((f.field_key=? AND (f.value_kind='Z' OR f.numeric_value BETWEEN -180 AND 180))"
                            + " OR (f.field_key=? AND (f.value_kind='Z' OR f.numeric_value BETWEEN -90 AND 90)))",
                    List.of(region.longitudeKey(), region.latitudeKey()));
            boolean needsRecovery = false;
            for (int i = 0; i < windows.size(); i++) needsRecovery |= valid[i] != candidates[i] * 2L;
            List<Object> args = new ArrayList<>();
            args.add(region.latitudeKey());
            args.add(region.longitudeKey());
            StringBuilder filter = new StringBuilder(" AND lat.field_key=? AND f.field_key=? ");
            if (!"GLOBAL".equals(selection.scope())) {
                filter.append(" AND lat.execution_project_id=? ");
                args.add(selection.projectId());
            }
            double angle = (region.radius() + 0.000001) / GeoFunctions.EARTH_RADIUS_METERS;
            double latitudeSpan = Math.toDegrees(angle) + 1e-8;
            filter.append(" AND lat.numeric_value BETWEEN ? AND ? ");
            args.add(Math.max(-90, region.latitude()-latitudeSpan));
            args.add(Math.min(90, region.latitude()+latitudeSpan));
            if (Math.abs(region.latitude()) + latitudeSpan < 90) {
                double span = Math.toDegrees(Math.asin(Math.min(1, Math.sin(angle) / Math.cos(Math.toRadians(region.latitude()))))) + 1e-8;
                double lo = region.longitude()-span, hi = region.longitude()+span;
                if (lo >= -180 && hi <= 180) {
                    filter.append(" AND f.numeric_value BETWEEN ? AND ? ");
                    args.add(lo);
                    args.add(hi);
                } else {
                    filter.append(" AND (f.numeric_value >= ? OR f.numeric_value <= ?) ");
                    args.add(lo < -180 ? lo + 360 : lo);
                    args.add(hi > 180 ? hi - 360 : hi);
                }
            }
            filter.append(" AND 2 * ? * ASIN(SQRT(LEAST(1,GREATEST(0,POW(SIN(RADIANS(lat.numeric_value-?)/2),2)"
                    + "+COS(RADIANS(?))*COS(RADIANS(lat.numeric_value))*POW(SIN(RADIANS(f.numeric_value-?)/2),2))))) <= ?");
            args.add(GeoFunctions.EARTH_RADIUS_METERS);
            args.add(region.latitude());
            args.add(region.latitude());
            args.add(region.longitude());
            args.add(region.radius() + 0.000001);
            String table = spatialTable("GLOBAL".equals(selection.scope()) ? "idx_history_value_global" : "idx_history_value_project");
            matches = useBuckets ? spatialTotals(table, selection, windows, filter.toString(), args)
                    : totals(table, "f", selection, earliest, windows, filter.toString(), args);
            if (needsRecovery) {
                if (history == null) return Map.of();
                var geo = new GeoFunctions();
                for (var row : history.spatialExceptions(selection, earliest, useBuckets)) {
                    Object lon = row.fields().get(region.longitudeKey());
                    Object lat = row.fields().get(region.latitudeKey());
                    if (lon == null || lat == null) continue;
                    if (geo.distanceMeters(region.longitude(), region.latitude(), HistoryQuery.number(lon).doubleValue(),
                            HistoryQuery.number(lat).doubleValue()) <= region.radius() + 0.000001) {
                        for (int i = 0; i < windows.size(); i++) if (!row.occurredAt().isBefore(windows.get(i))) matches[i]++;
                    }
                }
            }
        }
        Map<LocalDateTime, Count> result = new LinkedHashMap<>();
        for (int i = 0; i < windows.size(); i++) result.put(windows.get(i), new Count(candidates[i], matches[i]));
        return Map.copyOf(result);
    }

    private static String spatialTable(String index) {
        return "rule_history_numeric_field f FORCE INDEX (" + index + ") STRAIGHT_JOIN rule_history_numeric_field lat "
                + "FORCE INDEX (PRIMARY) ON lat.log_id=f.log_id AND lat.log_create_time=f.log_create_time";
    }

    private long[] spatialTotals(String table, Selection selection, List<LocalDateTime> windows, String filter, List<Object> args) {
        var end = selection.before().truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        var earliest = windows.stream().min(LocalDateTime::compareTo).orElseThrow().truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        var versions = new java.util.TreeMap<LocalDateTime, Long>();
        StringBuilder sql = new StringBuilder("SELECT b.bucket_hour,COALESCE(SUM(v.revision),0) FROM rule_history_bucket b "
                + "LEFT JOIN rule_history_bucket_revision v ON v.execution_project_id=b.execution_project_id AND v.root_rule_id=b.root_rule_id "
                + "AND v.bucket_hour=b.bucket_hour WHERE b.field_key='' AND b.bucket_hour>=? AND b.bucket_hour<?");
        List<Object> params = new ArrayList<>(List.of(Timestamp.valueOf(earliest), Timestamp.valueOf(end)));
        if (!"GLOBAL".equals(selection.scope())) { sql.append(" AND b.execution_project_id=?"); params.add(selection.projectId()); }
        if ("RULE".equals(selection.scope())) { sql.append(" AND b.root_rule_id=?"); params.add(selection.ruleId()); }
        jdbc.query(sql + " GROUP BY b.bucket_hour", rs -> {
            versions.put(rs.getTimestamp(1).toLocalDateTime(), rs.getLong(2));
        }, params.toArray());
        var hourly = new java.util.TreeMap<LocalDateTime, Long>();
        var missing = new java.util.TreeMap<LocalDateTime, SpatialWindow>();
        versions.forEach((hour, revision) -> {
            var key = new SpatialWindow(selection.scope(), "GLOBAL".equals(selection.scope()) ? null : selection.projectId(),
                    "RULE".equals(selection.scope()) ? selection.ruleId() : null, selection.region(), hour, revision);
            Long cached = closedWindows.getIfPresent(key);
            if (cached == null) missing.put(hour, key); else hourly.put(hour, cached);
        });
        // Read adjacent missing hours together; split sparse ranges to avoid rescanning already cached history.
        String partialTable = spatialTable("idx_history_field_window");
        while (!missing.isEmpty()) {
            LocalDateTime first = missing.firstKey(), until = first.plusHours(1);
            while (missing.containsKey(until) && until.isBefore(first.plusDays(7))) until = until.plusHours(1);
            var bucketRows = spatialHours(until.equals(first.plusHours(1)) ? partialTable : table, selection, first, until, filter, args);
            for (var it = missing.headMap(until, false).entrySet().iterator(); it.hasNext();) {
                var entry = it.next();
                long count = bucketRows.getOrDefault(entry.getKey(), 0L);
                closedWindows.put(entry.getValue(), count);
                hourly.put(entry.getKey(), count);
                it.remove();
            }
        }
        long tail = end.isBefore(selection.before()) ? totals(partialTable, "f", selection, end, List.of(end), filter, args)[0] : 0;
        long[] result = new long[windows.size()];
        for (int i = 0; i < windows.size(); i++) {
            var start = windows.get(i);
            if (!start.isBefore(end)) {
                result[i] = start.equals(end) ? tail : totals(partialTable, "f", selection, start, List.of(start), filter, args)[0];
                continue;
            }
            var floor = start.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
            var firstHour = start.equals(floor) ? floor : floor.plusHours(1);
            long count = tail;
            count += hourly.tailMap(firstHour).values().stream().mapToLong(Long::longValue).sum();
            if (!start.equals(floor)) {
                var boundary = new Selection(selection.scope(), selection.projectId(), selection.ruleId(), firstHour, selection.region());
                count += totals(partialTable, "f", boundary, start, List.of(start), filter, args)[0];
            }
            result[i] = count;
        }
        return result;
    }

    private Map<LocalDateTime, Long> spatialHours(String table, Selection selection, LocalDateTime from, LocalDateTime until,
                                                  String filter, List<Object> arguments) {
        StringBuilder sql = new StringBuilder("SELECT DATE_FORMAT(f.started_at,'%Y-%m-%d %H:00:00') AS h,COUNT(*) FROM ").append(table);
        if ("RULE".equals(selection.scope())) sql.append(" JOIN rule_history_root r ON r.log_id=f.log_id AND r.log_create_time=f.log_create_time");
        sql.append(" WHERE f.started_at>=? AND f.started_at<?");
        List<Object> params = new ArrayList<>(List.of(Timestamp.valueOf(from), Timestamp.valueOf(until)));
        if (!"GLOBAL".equals(selection.scope())) { sql.append(" AND f.execution_project_id=?"); params.add(selection.projectId()); }
        if ("RULE".equals(selection.scope())) { sql.append(" AND r.root_rule_id=?"); params.add(selection.ruleId()); }
        sql.append(filter).append(" GROUP BY h"); params.addAll(arguments);
        Map<LocalDateTime, Long> rows = new LinkedHashMap<>();
        jdbc.query(connection -> {
            var ps = connection.prepareStatement(sql.toString());
            ps.setQueryTimeout(10);
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            return ps;
        }, (org.springframework.jdbc.core.RowCallbackHandler) rs -> rows.put(rs.getTimestamp(1).toLocalDateTime(), rs.getLong(2)));
        return rows;
    }

    private long[] bucketTotals(Selection selection, LocalDateTime earliest, List<LocalDateTime> windows, Region region) {
        var endHour = selection.before().truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        String contribution = region == null ? "sample_count" : "(null_count + CASE WHEN min_value >= "
                + "(CASE WHEN field_key=? THEN -180 ELSE -90 END) AND max_value <= "
                + "(CASE WHEN field_key=? THEN 180 ELSE 90 END) THEN numeric_count ELSE 0 END)";
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < windows.size(); i++) {
            if (i > 0) sql.append(',');
            var start = windows.get(i);
            var floor = start.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
            var ceiling = floor.equals(start) ? floor : floor.plusHours(1);
            sql.append("COALESCE(SUM(CASE WHEN bucket_hour>=? THEN ").append(contribution).append(" ELSE 0 END),0)");
            params.add(Timestamp.valueOf(ceiling));
            if (region != null) { params.add(region.longitudeKey()); params.add(region.longitudeKey()); }
        }
        sql.append(" FROM rule_history_bucket WHERE bucket_hour>=? AND bucket_hour<?");
        params.add(Timestamp.valueOf(earliest.truncatedTo(java.time.temporal.ChronoUnit.HOURS)));
        params.add(Timestamp.valueOf(endHour));
        if (region == null) sql.append(" AND field_key=''");
        else {
            sql.append(" AND field_key IN (?,?)");
            params.add(region.longitudeKey());
            params.add(region.latitudeKey());
        }
        if (!"GLOBAL".equals(selection.scope())) {
            DerivedVariableConfig.require(selection.projectId() != null && selection.projectId() > 0, "当前项目范围需要明确执行项目");
            sql.append(" AND execution_project_id=?"); params.add(selection.projectId());
        }
        if ("RULE".equals(selection.scope())) {
            DerivedVariableConfig.require(selection.ruleId() != null, "当前规则范围需要根规则标识");
            sql.append(" AND root_rule_id=?"); params.add(selection.ruleId());
        }
        long[] result = counts(sql.toString(), params, windows.size());
        // Rows in either partial hour are counted exactly, with the same cutoff as the full hours.
        String table = region == null ? "rule_history_root r" : "rule_history_numeric_field f";
        String alias = region == null ? "r" : "f";
        String valid = region == null ? "" : " AND ((f.field_key=? AND (f.value_kind='Z' OR f.numeric_value BETWEEN -180 AND 180))"
                + " OR (f.field_key=? AND (f.value_kind='Z' OR f.numeric_value BETWEEN -90 AND 90)))";
        List<Object> keys = region == null ? List.of() : List.of(region.longitudeKey(), region.latitudeKey());
        long endCount = endHour.isBefore(selection.before())
                ? totals(table, alias, selection, endHour, List.of(endHour), valid, keys)[0] : 0;
        for (int i = 0; i < windows.size(); i++) {
            var start = windows.get(i);
            if (!start.isBefore(endHour)) {
                result[i] = totals(table, alias, selection, start, List.of(start), valid, keys)[0];
            } else {
                result[i] += endCount;
                var floor = start.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
                if (!start.equals(floor)) {
                    var boundary = new Selection(selection.scope(), selection.projectId(), selection.ruleId(), floor.plusHours(1), region);
                    result[i] += totals(table, alias, boundary, start, List.of(start), valid, keys)[0];
                }
            }
        }
        return result;
    }

    private long[] totals(String table, String alias, Selection selection, LocalDateTime earliest,
                          List<LocalDateTime> windows, String extra, List<Object> args) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<Object> params = new ArrayList<>();
        for (LocalDateTime from : windows) {
            if (!params.isEmpty()) sql.append(',');
            sql.append("COUNT(CASE WHEN ").append(alias).append(".started_at>=? THEN 1 END)");
            params.add(Timestamp.valueOf(from));
        }
        sql.append(" FROM ").append(table);
        if ("RULE".equals(selection.scope()) && "f".equals(alias))
            sql.append(" JOIN rule_history_root r ON r.log_id=f.log_id AND r.log_create_time=f.log_create_time");
        sql.append(" WHERE ").append(alias).append(".started_at>=? AND ").append(alias).append(".started_at<?");
        params.add(Timestamp.valueOf(earliest)); params.add(Timestamp.valueOf(selection.before()));
        if (!"GLOBAL".equals(selection.scope())) {
            DerivedVariableConfig.require(selection.projectId() != null && selection.projectId() > 0,"当前项目范围需要明确执行项目");
            sql.append(" AND ").append(alias).append(".execution_project_id=?"); params.add(selection.projectId());
            if ("RULE".equals(selection.scope())) {
                DerivedVariableConfig.require(selection.ruleId() != null,"当前规则范围需要从规则测试或正式规则入口执行");
                sql.append(" AND r.root_rule_id=?"); params.add(selection.ruleId());
            }
        }
        if (permissions != null) sql.append(" AND (").append(permissions.predicate(
                "r".equals(alias) ? "rule_history_root" : "rule_history_numeric_field", alias)).append(')');
        sql.append(extra); params.addAll(args);
        return counts(sql.toString(), params, windows.size());
    }

    private long[] counts(String sql, List<Object> params, int columns) {
        return jdbc.query(connection -> {
            var ps = connection.prepareStatement(sql);
            ps.setQueryTimeout(10);
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            return ps;
        }, rs -> {
            if (!rs.next()) throw new IllegalStateException("无法取得历史进件统计");
            long[] totals = new long[columns];
            for (int i = 0; i < totals.length; i++) totals[i] = rs.getLong(i + 1);
            return totals;
        });
    }
}
