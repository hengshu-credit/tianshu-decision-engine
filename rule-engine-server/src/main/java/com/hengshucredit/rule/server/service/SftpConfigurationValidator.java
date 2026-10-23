package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleSftpDatasource;
import java.util.LinkedHashMap;
import java.util.Map;

/** 在线测试与审批共用的连接参数约束；省略的可选参数由现有默认值处理。 */
public final class SftpConfigurationValidator {
    private SftpConfigurationValidator() { }

    public static Map<String, String> issues(RuleSftpDatasource value) {
        Map<String, String> issues = new LinkedHashMap<>();
        String scope = value.getScope() == null ? "PROJECT" : value.getScope();
        if (!"GLOBAL".equals(scope) && !"PROJECT".equals(scope)) issues.put("scope", "请选择项目级或全局作用范围");
        if ("PROJECT".equals(scope) && (value.getProjectId() == null || value.getProjectId() <= 0)) issues.put("projectId", "项目级 SFTP 数据源必须选择所属项目");
        range(issues, "port", value.getPort(), 1, 65535, "端口");
        range(issues, "maxPoolSize", value.getMaxPoolSize(), 1, 100, "最大连接数");
        range(issues, "minIdle", value.getMinIdle(), 0, 100, "最小空闲连接数");
        int max = value.getMaxPoolSize() == null ? 8 : value.getMaxPoolSize();
        int min = value.getMinIdle() == null ? 1 : value.getMinIdle();
        if (min > max) issues.put("minIdle", "最小空闲连接数不能超过最大连接数");
        range(issues, "connectTimeoutMs", value.getConnectTimeoutMs(), 100, 600000, "连接超时(ms)");
        range(issues, "sessionTimeoutMs", value.getSessionTimeoutMs(), 100, 600000, "会话超时(ms)");
        range(issues, "socketTimeoutMs", value.getSocketTimeoutMs(), 100, 600000, "Socket 超时(ms)");
        range(issues, "borrowTimeoutMs", value.getBorrowTimeoutMs(), 100, 600000, "借用超时(ms)");
        range(issues, "idleTimeoutMs", value.getIdleTimeoutMs(), 1000, 86400000, "空闲超时(ms)");
        return issues;
    }

    private static void range(Map<String, String> issues, String field, Number value, long min, long max, String label) {
        if (value != null && (value.longValue() < min || value.longValue() > max)) issues.put(field, label + "必须在 " + min + " 到 " + max + " 之间");
    }
}
