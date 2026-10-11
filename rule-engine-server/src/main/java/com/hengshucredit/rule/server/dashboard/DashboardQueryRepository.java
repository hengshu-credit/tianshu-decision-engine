package com.hengshucredit.rule.server.dashboard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.hengshucredit.rule.server.dashboard.DashboardMetricField.AMOUNT;
import static com.hengshucredit.rule.server.dashboard.DashboardMetricField.DECISION_RESULT;
import static com.hengshucredit.rule.server.dashboard.DashboardMetricField.DEVICE_ID;
import static com.hengshucredit.rule.server.dashboard.DashboardMetricField.LATITUDE;
import static com.hengshucredit.rule.server.dashboard.DashboardMetricField.LONGITUDE;
import static com.hengshucredit.rule.server.dashboard.DashboardMetricField.PERIOD;
import static com.hengshucredit.rule.server.dashboard.DashboardMetricField.REQUEST_ID;

@Repository
public class DashboardQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public DashboardQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ApplicationRow> applicationRows(
            DashboardCriteria criteria,
            DashboardMapping.ResolvedMappings mappings) {
        List<Object> parameters = new ArrayList<>();
        String request = jsonValue(mappings.get(REQUEST_ID), parameters);
        String device = jsonValue(mappings.get(DEVICE_ID), parameters);
        String longitude = jsonValue(mappings.get(LONGITUDE), parameters);
        String latitude = jsonValue(mappings.get(LATITUDE), parameters);
        String period = jsonValue(mappings.get(PERIOD), parameters);
        String amount = jsonValue(mappings.get(AMOUNT), parameters);
        String decision = jsonValue(mappings.get(DECISION_RESULT), parameters);

        StringBuilder sql = new StringBuilder()
                .append("WITH extracted AS (")
                .append(" SELECT id, project_code, trace_id, create_time, ")
                .append(request).append(" AS request_value, ")
                .append(device).append(" AS device_value, ")
                .append(longitude).append(" AS longitude_value, ")
                .append(latitude).append(" AS latitude_value, ")
                .append(period).append(" AS period_value, ")
                .append(amount).append(" AS amount_value, ")
                .append(decision).append(" AS decision_value ")
                .append(" FROM rule_execution_log_current execution ")
                .append(" WHERE create_time >= ? AND create_time <= ? ");
        parameters.add(criteria.startTime());
        parameters.add(criteria.endTime());
        if (criteria.projectCode() != null) {
            sql.append(" AND execution.project_code = ? ");
            parameters.add(criteria.projectCode());
        }
        appendExecutionRuleFilter(sql, parameters, criteria,
                "execution.rule_code");
        sql.append("), ranked AS (")
                .append(" SELECT extracted.*, ROW_NUMBER() OVER (")
                .append(" PARTITION BY COALESCE(project_code, ''), CASE ")
                .append(" WHEN NULLIF(TRIM(request_value), '') IS NOT NULL ")
                .append(" THEN CONCAT('REQ:', TRIM(request_value)) ")
                .append(" WHEN NULLIF(TRIM(trace_id), '') IS NOT NULL ")
                .append(" THEN CONCAT('TRACE:', TRIM(trace_id)) ")
                .append(" ELSE CONCAT('ID:', id) END ")
                .append(" ORDER BY create_time DESC, id DESC) AS ranking_index ")
                .append(" FROM extracted)")
                .append(" SELECT id, project_code, trace_id, request_value, ")
                .append(" decision_value, device_value, period_value, ")
                .append(" amount_value, longitude_value, latitude_value, ")
                .append(" create_time FROM ranked WHERE ranking_index = 1");

        return jdbcTemplate.query(sql.toString(), this::applicationRow,
                parameters.toArray());
    }

    public List<Long> executionDurations(DashboardCriteria criteria) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT execution.execute_time_ms "
                        + "FROM rule_execution_log_current execution "
                        + "WHERE execution.create_time >= ? "
                        + "AND execution.create_time <= ? "
                        + "AND execution.execute_time_ms IS NOT NULL "
                        + "AND execution.execute_time_ms >= 0");
        parameters.add(criteria.startTime());
        parameters.add(criteria.endTime());
        appendProjectCode(sql, parameters, criteria,
                "execution.project_code");
        appendExecutionRuleFilter(sql, parameters, criteria,
                "execution.rule_code");
        return jdbcTemplate.query(sql.toString(),
                (rs, rowNumber) -> rs.getLong(1), parameters.toArray());
    }

    public List<RuntimeCall> runtimeCalls(DashboardCriteria criteria,
                                          String moduleType) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT success, request_success, found, provider_request, "
                        + "cost_time_ms FROM rule_runtime_call_log runtime_call "
                        + "WHERE runtime_call.create_time >= ? "
                        + "AND runtime_call.create_time <= ? "
                        + "AND runtime_call.module_type = ?");
        parameters.add(criteria.startTime());
        parameters.add(criteria.endTime());
        parameters.add(moduleType);
        if ("DATASOURCE".equals(moduleType)) {
            sql.append(" AND runtime_call.action_type = 'API_INVOKE' "
                    + "AND runtime_call.provider_request = 1");
        }
        appendProjectCode(sql, parameters, criteria,
                "runtime_call.project_code");
        appendRuntimeRuleFilter(sql, parameters, criteria);
        return jdbcTemplate.query(sql.toString(), (rs, rowNumber) ->
                        new RuntimeCall(rs.getBoolean("success"),
                                nullableBoolean(rs, "request_success"),
                                nullableBoolean(rs, "found"),
                                nullableBoolean(rs, "provider_request"),
                                nullableLong(rs, "cost_time_ms")),
                parameters.toArray());
    }

    public ResourceCounts resourceCounts(DashboardCriteria criteria) {
        List<Object> scopeParameters = scopeParameters(criteria);
        String scope = scopeSql(criteria, "resource");
        long databaseCount = count("SELECT COUNT(*) FROM rule_db_datasource "
                        + "resource WHERE resource.status = 1" + scope,
                scopeParameters);
        long listCount = count("SELECT COUNT(*) FROM rule_list_library "
                        + "resource WHERE resource.status = 1" + scope,
                scopeParameters);
        long apiCount = count("SELECT COUNT(*) "
                        + "FROM rule_external_api_config api "
                        + "JOIN rule_external_datasource resource "
                        + "ON resource.id = api.datasource_id "
                        + "WHERE api.status = 1 AND resource.status = 1" + scope,
                scopeParameters);
        List<CategoryCount> listCategories = jdbcTemplate.query(
                "SELECT resource.list_type, COUNT(*) AS item_count "
                        + "FROM rule_list_library resource "
                        + "WHERE resource.status = 1" + scope
                        + " GROUP BY resource.list_type "
                        + "ORDER BY item_count DESC, resource.list_type",
                (rs, rowNumber) -> new CategoryCount(
                        rs.getString("list_type"), rs.getLong("item_count")),
                scopeParameters.toArray());
        return new ResourceCounts(databaseCount, listCount, apiCount,
                listCategories);
    }

    public List<BillingAmount> billingByCurrency(DashboardCriteria criteria) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT currency, SUM(amount) AS total_amount "
                        + "FROM rule_billing_record billing_record "
                        + "WHERE billing_record.occur_time >= ? "
                        + "AND billing_record.occur_time <= ?");
        parameters.add(criteria.startTime());
        parameters.add(criteria.endTime());
        appendProjectCode(sql, parameters, criteria,
                "billing_record.project_code");
        appendExecutionRuleFilter(sql, parameters, criteria,
                "billing_record.rule_code");
        sql.append(" GROUP BY currency ORDER BY currency");
        return jdbcTemplate.query(sql.toString(), (rs, rowNumber) ->
                        new BillingAmount(rs.getString("currency"),
                                rs.getBigDecimal("total_amount")),
                parameters.toArray());
    }

    public long downstreamRuleCount(DashboardCriteria criteria) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        if (criteria.projectId() == null) {
            sql.append("SELECT COUNT(DISTINCT definition.id) ")
                    .append("FROM rule_definition definition ")
                    .append("WHERE definition.status = 1 AND ")
                    .append("(definition.scope = 'PROJECT' OR EXISTS (")
                    .append("SELECT 1 FROM rule_definition_ref ref ")
                    .append("WHERE ref.definition_id = definition.id))");
        } else {
            sql.append("SELECT COUNT(DISTINCT definition.id) ")
                    .append("FROM rule_definition definition ")
                    .append("WHERE definition.status = 1 AND ((")
                    .append("definition.scope = 'PROJECT' ")
                    .append("AND definition.project_id = ?) OR (")
                    .append("definition.scope = 'GLOBAL' AND EXISTS (")
                    .append("SELECT 1 FROM rule_definition_ref ref ")
                    .append("WHERE ref.definition_id = definition.id ")
                    .append("AND ref.project_id = ?)))");
            parameters.add(criteria.projectId());
            parameters.add(criteria.projectId());
        }
        appendDefinitionFilters(sql, parameters, criteria, "definition");
        return count(sql.toString(), parameters);
    }

    public List<CategoryCount> approvalCounts(DashboardCriteria criteria) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT approval.status, COUNT(*) AS item_count "
                        + "FROM governance_approval_request approval "
                        + "WHERE approval.create_time >= ? "
                        + "AND approval.create_time <= ? "
                        + "AND approval.resource_type IN ('RULE', "
                        + "'RULE_PROJECT_BINDING')");
        parameters.add(criteria.startTime());
        parameters.add(criteria.endTime());
        if (criteria.projectId() != null) {
            sql.append(" AND approval.project_id = ?");
            parameters.add(criteria.projectId());
        }
        appendApprovalRuleFilter(sql, parameters, criteria);
        sql.append(" GROUP BY approval.status");
        return jdbcTemplate.query(sql.toString(), (rs, rowNumber) ->
                        new CategoryCount(rs.getString("status"),
                                rs.getLong("item_count")),
                parameters.toArray());
    }

    private void appendProjectCode(StringBuilder sql,
                                   List<Object> parameters,
                                   DashboardCriteria criteria,
                                   String column) {
        if (criteria.projectCode() != null) {
            sql.append(" AND ").append(column).append(" = ?");
            parameters.add(criteria.projectCode());
        }
    }

    private void appendExecutionRuleFilter(StringBuilder sql,
                                           List<Object> parameters,
                                           DashboardCriteria criteria,
                                           String ruleCodeColumn) {
        if (criteria.ruleCode() != null) {
            sql.append(" AND ").append(ruleCodeColumn).append(" LIKE ?");
            parameters.add(like(criteria.ruleCode()));
        }
        if (criteria.ruleName() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM rule_definition ")
                    .append("dashboard_rule WHERE dashboard_rule.rule_code = ")
                    .append(ruleCodeColumn)
                    .append(" AND dashboard_rule.rule_name LIKE ?)");
            parameters.add(like(criteria.ruleName()));
        }
    }

    private void appendRuntimeRuleFilter(StringBuilder sql,
                                         List<Object> parameters,
                                         DashboardCriteria criteria) {
        if (!criteria.hasRuleFilter()) return;
        sql.append(" AND EXISTS (SELECT 1 FROM rule_trace_registry ")
                .append("dashboard_trace ");
        if (criteria.ruleName() != null) {
            sql.append("JOIN rule_definition dashboard_rule ON ")
                    .append("dashboard_rule.id = dashboard_trace.resource_id ");
        }
        sql.append("WHERE dashboard_trace.trace_id = ")
                .append("runtime_call.rule_trace_id ")
                .append("AND dashboard_trace.resource_type = 'RULE'");
        if (criteria.ruleCode() != null) {
            sql.append(" AND dashboard_trace.resource_code LIKE ?");
            parameters.add(like(criteria.ruleCode()));
        }
        if (criteria.ruleName() != null) {
            sql.append(" AND dashboard_rule.rule_name LIKE ?");
            parameters.add(like(criteria.ruleName()));
        }
        sql.append(")");
    }

    private void appendDefinitionFilters(StringBuilder sql,
                                         List<Object> parameters,
                                         DashboardCriteria criteria,
                                         String alias) {
        if (criteria.ruleCode() != null) {
            sql.append(" AND ").append(alias).append(".rule_code LIKE ?");
            parameters.add(like(criteria.ruleCode()));
        }
        if (criteria.ruleName() != null) {
            sql.append(" AND ").append(alias).append(".rule_name LIKE ?");
            parameters.add(like(criteria.ruleName()));
        }
    }

    private void appendApprovalRuleFilter(StringBuilder sql,
                                          List<Object> parameters,
                                          DashboardCriteria criteria) {
        if (!criteria.hasRuleFilter()) return;
        sql.append(" AND ((approval.resource_type = 'RULE' AND EXISTS (")
                .append("SELECT 1 FROM rule_definition approval_rule ")
                .append("WHERE approval_rule.id = approval.resource_id");
        appendDefinitionFilters(sql, parameters, criteria, "approval_rule");
        sql.append(")) OR (approval.resource_type = 'RULE_PROJECT_BINDING' ")
                .append("AND EXISTS (SELECT 1 FROM rule_definition_ref ")
                .append("approval_ref JOIN rule_definition approval_rule ")
                .append("ON approval_rule.id = approval_ref.definition_id ")
                .append("WHERE approval_ref.id = approval.resource_id");
        appendDefinitionFilters(sql, parameters, criteria, "approval_rule");
        sql.append(")))");
    }

    private String like(String value) {
        return "%" + value + "%";
    }

    private String scopeSql(DashboardCriteria criteria, String alias) {
        return criteria.projectId() == null ? ""
                : " AND (" + alias + ".scope = 'GLOBAL' OR "
                + alias + ".project_id = ?)";
    }

    private List<Object> scopeParameters(DashboardCriteria criteria) {
        return criteria.projectId() == null
                ? new ArrayList<>()
                : new ArrayList<>(List.of(criteria.projectId()));
    }

    private long count(String sql, List<Object> parameters) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class,
                parameters.toArray());
        return value == null ? 0L : value;
    }

    private Boolean nullableBoolean(ResultSet resultSet, String column)
            throws SQLException {
        boolean value = resultSet.getBoolean(column);
        return resultSet.wasNull() ? null : value;
    }

    private Long nullableLong(ResultSet resultSet, String column)
            throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private String jsonValue(DashboardMapping.ResolvedMapping mapping,
                             List<Object> parameters) {
        if (mapping == null || !mapping.valid() || mapping.jsonPath() == null) {
            return "NULL";
        }
        String column = mapping.jsonSource() == DashboardMapping.JsonSource.INPUT
                ? "input_params" : "output_result";
        parameters.add(mapping.jsonPath());
        return "CASE WHEN JSON_VALID(" + column + ") "
                + "THEN JSON_UNQUOTE(JSON_EXTRACT(" + column + ", ?)) END";
    }

    private ApplicationRow applicationRow(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new ApplicationRow(resultSet.getLong("id"),
                resultSet.getString("project_code"),
                resultSet.getString("trace_id"),
                resultSet.getString("request_value"),
                resultSet.getString("decision_value"),
                resultSet.getString("device_value"),
                resultSet.getString("period_value"),
                resultSet.getString("amount_value"),
                resultSet.getString("longitude_value"),
                resultSet.getString("latitude_value"),
                resultSet.getTimestamp("create_time").toLocalDateTime());
    }

    public record ApplicationRow(Long id,
                                 String projectCode,
                                 String traceId,
                                 String requestId,
                                 String decision,
                                 String device,
                                 String period,
                                 String amount,
                                 String longitude,
                                 String latitude,
                                 LocalDateTime createTime) {
    }

    public record RuntimeCall(boolean success,
                              Boolean requestSuccess,
                              Boolean found,
                              Boolean providerRequest,
                              Long costTimeMs) {
    }

    public record CategoryCount(String key, long count) {
    }

    public record ResourceCounts(long databaseCount,
                                 long listCount,
                                 long apiCount,
                                 List<CategoryCount> listCategories) {
    }

    public record BillingAmount(String currency, BigDecimal amount) {
    }
}
