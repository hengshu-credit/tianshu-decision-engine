package com.hengshucredit.rule.server.dashboard;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DashboardQueryRepositoryTest {

    @Test
    public void dynamicDecisionContextUsesSelectedJsonSourceAndStaticMappingsDoNotLoadPayloads() {
        var jdbc = new CapturingJdbcTemplate();
        var repository = new DashboardQueryRepository(jdbc);
        var operand = com.alibaba.fastjson.JSON.parseObject("{kind:'REFERENCE',refType:'VARIABLE',refId:9,code:'payload'}");
        var values = new DashboardMapping.DecisionValues(null, null, null, null, null, null, List.of(operand), null, null);
        var expressions = DashboardDecisionExpressions.prepare(values,
                node -> new DashboardDecisionExpressions.Binding(List.of("payload"), null));
        for (var source : DashboardMapping.JsonSource.values()) {
            var mapping = new DashboardMapping.ResolvedMapping(DashboardMetricField.DECISION_RESULT,
                    DashboardMapping.RefType.VARIABLE, 1L, source, "$.decision", "结果",
                    DashboardMapping.MappingSource.USER_DEFAULT, true, null, values, expressions);
            repository.applicationRows(criteria(), new DashboardMapping.ResolvedMappings(
                    java.util.Map.of(DashboardMetricField.DECISION_RESULT, mapping)));
            assertTrue(jdbc.sql.contains((source == DashboardMapping.JsonSource.INPUT ? "input_params" : "output_result") + " AS decision_context"));
            assertTrue(jdbc.sql.contains("ranking_index = 1"));
        }
        repository.applicationRows(criteria(), new DashboardMapping.ResolvedMappings(null));
        assertTrue(jdbc.sql.contains("NULL AS decision_context"));
    }

    @Test
    public void applicationQueryDoesNotUseWindowFunctionNameAsAlias() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        DashboardQueryRepository repository =
                new DashboardQueryRepository(jdbcTemplate);

        repository.applicationRows(new DashboardCriteria(null, null,
                        LocalDateTime.of(2026, 8, 20, 0, 0),
                        LocalDateTime.of(2026, 8, 27, 23, 59)),
                new DashboardMapping.ResolvedMappings(null));

        assertFalse("MySQL 8 reserves ROW_NUMBER and rejects it as an "
                        + "unquoted alias",
                jdbcTemplate.sql.contains(" AS row_number "));
    }

    @Test
    public void executionQueriesBindRuleCodeAndNameFilters() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        DashboardQueryRepository repository =
                new DashboardQueryRepository(jdbcTemplate);
        DashboardCriteria criteria = criteria();

        repository.applicationRows(criteria,
                new DashboardMapping.ResolvedMappings(null));

        assertTrue(jdbcTemplate.sql.contains("rule_code LIKE ?"));
        assertTrue(jdbcTemplate.sql.contains("rule_name LIKE ?"));
        assertTrue(Arrays.asList(jdbcTemplate.args).contains("%RC_RISK%"));
        assertTrue(Arrays.asList(jdbcTemplate.args).contains("%风险审批%"));
    }

    @Test
    public void runtimeAndBillingQueriesUseTheirRuleAttribution() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        DashboardQueryRepository repository =
                new DashboardQueryRepository(jdbcTemplate);
        DashboardCriteria criteria = criteria();

        repository.runtimeCalls(criteria, "DATASOURCE");
        assertTrue(jdbcTemplate.sql.contains("rule_trace_registry"));
        assertTrue(jdbcTemplate.sql.contains("rule_definition"));
        assertTrue(Arrays.asList(jdbcTemplate.args).contains("%RC_RISK%"));
        assertTrue(Arrays.asList(jdbcTemplate.args).contains("%风险审批%"));

        repository.billingByCurrency(criteria);
        assertTrue(jdbcTemplate.sql.contains("rule_code LIKE ?"));
        assertTrue(jdbcTemplate.sql.contains("rule_name LIKE ?"));
    }

    private DashboardCriteria criteria() {
        return new DashboardCriteria(null, null, "RC_RISK", "风险审批",
                LocalDateTime.of(2026, 8, 20, 0, 0),
                LocalDateTime.of(2026, 8, 27, 23, 59));
    }

    private static class CapturingJdbcTemplate extends JdbcTemplate {
        private String sql;
        private Object[] args;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper,
                                 Object... args) {
            this.sql = sql;
            this.args = args;
            return List.of();
        }
    }
}
