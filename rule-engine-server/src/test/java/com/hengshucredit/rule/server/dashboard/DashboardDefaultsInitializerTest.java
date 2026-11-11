package com.hengshucredit.rule.server.dashboard;

import com.alibaba.fastjson.JSON;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

public class DashboardDefaultsInitializerTest {
    private JdbcTemplate jdbc;
    private DashboardDefaultsInitializer initializer;

    @Before
    public void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
                CREATE TABLE rule_variable (id BIGINT PRIMARY KEY, scope VARCHAR(16), project_id BIGINT,
                    var_code VARCHAR(128), var_type VARCHAR(32), var_source VARCHAR(32), status INT DEFAULT 1)
                """);
        jdbc.execute("CREATE TABLE rule_dashboard_default (id INT PRIMARY KEY, mappings_json TEXT, version BIGINT, update_by VARCHAR(64))");
        initializer = new DashboardDefaultsInitializer(jdbc);
    }

    @Test
    public void emptyEnvironmentStaysEmptyAndCanBindResourcesOnALaterStart() {
        initializer.initialize();
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM rule_variable", Integer.class));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM rule_dashboard_default", Integer.class));
        jdbc.update("""
                INSERT INTO rule_variable VALUES
                (731, 'GLOBAL', 0, 'amount', 'NUMBER', 'INPUT', 1),
                (35, 'PROJECT', 9, 'amount', 'NUMBER', 'INPUT', 1),
                (36, 'GLOBAL', 0, 'period', 'NUMBER', 'INPUT', 0),
                (37, 'GLOBAL', 0, 'gps_latitude', 'OBJECT', 'INPUT', 1),
                (38, 'GLOBAL', 0, 'device_id', 'STRING', 'CONSTANT', 1),
                (39, 'GLOBAL', 0, 'REQUEST_ID', 'STRING', 'INPUT', 1),
                (51, 'GLOBAL', 0, 'result', 'ENUM', 'COMPUTED', 1),
                (800, 'GLOBAL', 0, 'DECISION_PASS', 'NUMBER', 'CONSTANT', 1),
                (801, 'PROJECT', 9, 'DECISION_REVIEW', 'NUMBER', 'CONSTANT', 1),
                (802, 'GLOBAL', 0, 'DECISION_REJECT', 'NUMBER', 'CONSTANT', 1)
                """);
        var variables = jdbc.queryForList("SELECT * FROM rule_variable ORDER BY id");
        initializer.initialize();
        var mapping = JSON.parseObject(jdbc.queryForObject("SELECT mappings_json FROM rule_dashboard_default", String.class));
        assertEquals(Set.of("AMOUNT", "DECISION_RESULT"), mapping.keySet());
        assertEquals(Long.valueOf(731), mapping.getJSONObject("AMOUNT").getLong("refId"));
        assertEquals("INPUT", mapping.getJSONObject("AMOUNT").getString("jsonSource"));
        var decision = mapping.getJSONObject("DECISION_RESULT");
        assertEquals(Long.valueOf(51), decision.getLong("refId"));
        assertEquals("OUTPUT", decision.getString("jsonSource"));
        var values = decision.getJSONObject("decisionValues");
        assertEquals(Set.of("passRefIds", "rejectRefIds"), values.keySet());
        assertEquals(List.of(800L), values.getJSONArray("passRefIds").toJavaList(Long.class));
        assertEquals(List.of(802L), values.getJSONArray("rejectRefIds").toJavaList(Long.class));
        var saved = jdbc.queryForMap("SELECT * FROM rule_dashboard_default");
        initializer.initialize();
        assertEquals(saved, jdbc.queryForMap("SELECT * FROM rule_dashboard_default"));
        assertEquals(variables, jdbc.queryForList("SELECT * FROM rule_variable ORDER BY id"));
    }

    @Test
    public void preservesAnExplicitlyEmptyAdminConfiguration() {
        jdbc.update("INSERT INTO rule_variable VALUES (73, 'GLOBAL', 0, 'amount', 'NUMBER', 'INPUT', 1)");
        jdbc.update("INSERT INTO rule_dashboard_default VALUES (1, '{}', 2, 'admin')");
        var saved = jdbc.queryForMap("SELECT * FROM rule_dashboard_default");
        initializer.initialize();
        assertEquals(saved, jdbc.queryForMap("SELECT * FROM rule_dashboard_default"));
    }
}
