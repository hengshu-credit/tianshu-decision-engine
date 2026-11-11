package com.hengshucredit.rule.server.dashboard;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hengshucredit.rule.server.dashboard.DashboardMetricField.*;

@Component
@DependsOn("initialDataInitializer")
public class DashboardDefaultsInitializer {
    private static final Map<DashboardMetricField, String> FIELD_CODES = Map.of(
            REQUEST_ID, "request_id", DEVICE_ID, "device_id", LONGITUDE, "gps_longitude",
            LATITUDE, "gps_latitude", PERIOD, "period", AMOUNT, "amount", DECISION_RESULT, "result");
    private static final Map<String, String> DECISION_CODES = Map.of(
            "DECISION_PASS", "passRefIds", "DECISION_REVIEW", "reviewRefIds", "DECISION_REJECT", "rejectRefIds");

    private final JdbcTemplate jdbc;

    public DashboardDefaultsInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Variable(long id, String type, String source) { }

    @PostConstruct
    public void initialize() {
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM rule_dashboard_default WHERE id = 1)", Boolean.class))) return;

        var codes = new HashSet<>(FIELD_CODES.values());
        codes.addAll(DECISION_CODES.keySet());
        Map<String, Variable> variables = new HashMap<>();
        new NamedParameterJdbcTemplate(jdbc).query("""
                SELECT id, var_code, var_type, var_source FROM rule_variable
                WHERE scope = 'GLOBAL' AND project_id = 0 AND status = 1 AND var_code IN (:codes)
                """, Map.of("codes", codes), rs -> {
            variables.put(rs.getString("var_code"),
                    new Variable(rs.getLong("id"), rs.getString("var_type"), rs.getString("var_source")));
        });

        var mappings = new EnumMap<DashboardMetricField, Object>(DashboardMetricField.class);
        FIELD_CODES.forEach((field, code) -> {
            Variable variable = variables.get(code);
            if (variable == null || "CONSTANT".equals(variable.source()) || !field.acceptsType(variable.type())) return;
            var mapping = new LinkedHashMap<String, Object>();
            mapping.put("refType", "VARIABLE");
            mapping.put("refId", variable.id());
            mapping.put("jsonSource", field.defaultJsonSource().name());
            if (field == DECISION_RESULT) {
                Map<String, List<Long>> decisions = new HashMap<>();
                DECISION_CODES.forEach((constantCode, key) -> {
                    Variable constant = variables.get(constantCode);
                    if (constant != null && "CONSTANT".equals(constant.source())) {
                        decisions.put(key, List.of(constant.id()));
                    }
                });
                // 显式保存空组，缺失的常量不能回退到旧版本的字面值。
                mapping.put("decisionValues", decisions);
            }
            mappings.put(field, mapping);
        });
        if (mappings.isEmpty()) return;
        try {
            jdbc.update("INSERT INTO rule_dashboard_default (id, mappings_json, version, update_by) VALUES (1, ?, 1, 'system')",
                    JSON.toJSONString(mappings));
        } catch (DuplicateKeyException alreadyConfigured) {
            // 其他实例或管理员已先保存，保留已提交的值和版本。
        }
    }
}
