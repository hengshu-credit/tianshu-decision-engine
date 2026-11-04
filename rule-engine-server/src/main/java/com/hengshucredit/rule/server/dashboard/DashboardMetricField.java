package com.hengshucredit.rule.server.dashboard;

import java.util.Set;

import static com.hengshucredit.rule.server.dashboard.DashboardMapping.JsonSource.INPUT;
import static com.hengshucredit.rule.server.dashboard.DashboardMapping.JsonSource.OUTPUT;

public enum DashboardMetricField {
    REQUEST_ID("请求编号", INPUT, false),
    DEVICE_ID("设备编号", INPUT, false),
    LONGITUDE("经度", INPUT, true),
    LATITUDE("纬度", INPUT, true),
    PERIOD("期数", INPUT, true),
    AMOUNT("金额", INPUT, true),
    DECISION_RESULT("决策结果", OUTPUT, false);

    private static final Set<String> NON_SCALAR_TYPES =
            Set.of("OBJECT", "LIST", "MAP");

    private final String displayName;
    private final DashboardMapping.JsonSource defaultJsonSource;
    private final boolean numeric;

    DashboardMetricField(String displayName,
                         DashboardMapping.JsonSource defaultJsonSource,
                         boolean numeric) {
        this.displayName = displayName;
        this.defaultJsonSource = defaultJsonSource;
        this.numeric = numeric;
    }

    public String displayName() {
        return displayName;
    }

    public DashboardMapping.JsonSource defaultJsonSource() {
        return defaultJsonSource;
    }

    public boolean acceptsType(String type) {
        if (type == null || type.isBlank()) return false;
        String normalized = type.trim().toUpperCase();
        return numeric ? "NUMBER".equals(normalized)
                : !NON_SCALAR_TYPES.contains(normalized);
    }
}
