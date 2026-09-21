package com.hengshucredit.rule.server.functions;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 可通过函数管理绑定的 GPS 距离与当日进件去重统计，不开放脚本反射权限。 */
@Service("geoFunctions")
public class GeoFunctions {
    private static final double EARTH_RADIUS_METERS = 6371008.8;
    private static final double ONE_KILOMETER_METERS = 1000.0;

    public double distanceMeters(double longitude1, double latitude1,
                                 double longitude2, double latitude2) {
        validateCoordinates(longitude1, latitude1);
        validateCoordinates(longitude2, latitude2);
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double latitudeSin = Math.sin(latitudeDelta / 2.0);
        double longitudeSin = Math.sin(longitudeDelta / 2.0);
        double haversine = latitudeSin * latitudeSin
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * longitudeSin * longitudeSin;
        haversine = Math.max(0.0, Math.min(1.0, haversine));
        return 2.0 * EARTH_RADIUS_METERS
                * Math.atan2(Math.sqrt(haversine), Math.sqrt(1.0 - haversine));
    }

    public int dailyApplicantCount(double longitude, double latitude,
                                   Object records, String queryDate) {
        validateCoordinates(longitude, latitude);
        if (queryDate == null) throw new IllegalArgumentException("统计日期不能为空");
        LocalDate.parse(queryDate);
        if (!(records instanceof Collection<?> history)) {
            throw new IllegalArgumentException("进件历史必须是记录数组");
        }
        Set<String> applicants = new HashSet<>();
        for (Object value : history) {
            if (!(value instanceof Map<?, ?> row) || !(row.get("进件日期") instanceof String date)) {
                throw new IllegalArgumentException("进件记录必须包含进件日期");
            }
            if (!queryDate.equals(date)) continue;
            if (!(row.get("身份证") instanceof String identity) || identity.isBlank()) {
                throw new IllegalArgumentException("当日进件记录的身份证不能为空");
            }
            if (!(row.get("经度") instanceof Number recordLongitude)
                    || !(row.get("纬度") instanceof Number recordLatitude)) {
                throw new IllegalArgumentException("当日进件记录的经纬度必须是数值");
            }
            double distance = distanceMeters(longitude, latitude,
                    recordLongitude.doubleValue(), recordLatitude.doubleValue());
            // 仅容纳球面计算的浮点舍入误差，不把米级范围放宽。
            if (distance <= ONE_KILOMETER_METERS + 0.000001) applicants.add(identity);
        }
        return applicants.size();
    }

    private void validateCoordinates(double longitude, double latitude) {
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("经纬度必须有限且在有效范围内");
        }
    }
}
