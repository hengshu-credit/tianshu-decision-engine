package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.mapper.RuleApiDocScenarioMapper;
import com.hengshucredit.rule.server.mapper.RuleExecutionLogMapper;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class ProjectWorkbenchExecutionOwnershipTest {
    @Test
    public void countsEnabledScenariosByDistinctVisibleRule() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:coverage_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        try {
            jdbc.execute("CREATE TABLE rule_api_doc_scenario (id BIGINT PRIMARY KEY, definition_id BIGINT, status INT)");
            jdbc.update("INSERT INTO rule_api_doc_scenario VALUES (1,11,1),(2,11,1),(3,12,0),(4,90,1)");
            var first = new RuleDefinition();
            first.setId(11L);
            var second = new RuleDefinition();
            second.setId(12L);
            var service = workbench(List.of(first, second));
            ReflectionTestUtils.setField(service, "scenarioMapper", mapper(source, RuleApiDocScenarioMapper.class));
            var partial = service.getWorkbench(9L);
            var step = partial.getChecks().stream().filter(item -> "TEST".equals(item.getCode())).findFirst().orElseThrow();
            assertEquals(Long.valueOf(2), partial.getMetrics().getTestScenarioCount());
            assertEquals(Long.valueOf(1), partial.getMetrics().getTestCoveredRuleCount());
            assertEquals(Long.valueOf(12), partial.getMetrics().getFirstUncoveredRuleId());
            assertEquals("ACTION_REQUIRED", step.getStatus());
            assertEquals("CONFIGURE_TEST_CASES", step.getActionCode());

            jdbc.update("UPDATE rule_api_doc_scenario SET status=1 WHERE id=3");
            var complete = service.getWorkbench(9L);
            assertEquals(Long.valueOf(3), complete.getMetrics().getTestScenarioCount());
            assertNull(complete.getMetrics().getFirstUncoveredRuleId());
            assertEquals("READY", complete.getChecks().stream().filter(item -> "TEST".equals(item.getCode()))
                    .findFirst().orElseThrow().getStatus());
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }

    @Test
    public void executionSummaryUsesStableProjectIdInsteadOfHistoricalCode() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:workbench_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        try {
            jdbc.execute("CREATE TABLE rule_execution_log_current (id BIGINT PRIMARY KEY, execution_project_id BIGINT,"
                    + " project_code VARCHAR(100), rule_code VARCHAR(100), success INT, create_time TIMESTAMP)");
            LocalDateTime now = LocalDateTime.now();
            jdbc.update("INSERT INTO rule_execution_log_current VALUES (?, ?, ?, ?, ?, ?)",
                    1L, 9L, "OLD_CODE", "owned-success", 1, now.minusMinutes(2));
            jdbc.update("INSERT INTO rule_execution_log_current VALUES (?, ?, ?, ?, ?, ?)",
                    2L, 9L, "OLD_CODE", "owned-failure", 0, now.minusMinutes(1));
            jdbc.update("INSERT INTO rule_execution_log_current VALUES (?, ?, ?, ?, ?, ?)",
                    3L, 10L, "CURRENT_CODE", "other-project", 1, now);
            jdbc.update("INSERT INTO rule_execution_log_current VALUES (?, ?, ?, ?, ?, ?)",
                    4L, null, "CURRENT_CODE", "unknown-owner", 1, now);
            jdbc.update("INSERT INTO rule_execution_log_current VALUES (?, ?, ?, ?, ?, ?)",
                    5L, 9L, "OLD_CODE", "old-owned", 1, now.minusDays(2));
            jdbc.execute("ALTER TABLE rule_execution_log_current ADD (trace_id VARCHAR(80), rule_version INT,"
                    + " execute_time_ms BIGINT, source VARCHAR(32), trace_info CLOB)");
            jdbc.update("UPDATE rule_execution_log_current SET trace_id='owned-trace',rule_version=3,"
                    + "execute_time_ms=21,source='SERVER',trace_info=REPEAT('x',65536) WHERE id=2");

            var service = workbench(List.of());
            ReflectionTestUtils.setField(service, "executionLogMapper", mapper(source, RuleExecutionLogMapper.class));

            var result = service.getWorkbench(9L);

            assertEquals(Long.valueOf(2), result.getMetrics().getRecentExecutionCount());
            assertEquals(Long.valueOf(1), result.getMetrics().getRecentSuccessCount());
            assertEquals("owned-failure", result.getRecentExecution().getRuleCode());
            assertEquals("owned-trace", result.getRecentExecution().getTraceId());
            assertEquals(Long.valueOf(21), result.getRecentExecution().getExecuteTimeMs());
            assertEquals(Integer.valueOf(0), result.getRecentExecution().getSuccess());
            assertTrue(result.getWarnings().toString(), result.getWarnings().isEmpty());
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }

    private ProjectWorkbenchService workbench(List<RuleDefinition> rules) {
        var project = new RuleProject();
        project.setId(9L);
        project.setProjectCode("CURRENT_CODE");
        project.setStatus(1);
        var service = new ProjectWorkbenchService();
        for (var field : ProjectWorkbenchService.class.getDeclaredFields()) {
            Class<?> type = field.getType();
            if (!type.isInterface() || !type.getSimpleName().endsWith("Mapper")) continue;
            var stub = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "selectById" -> project;
                        case "selectCount", "selectLogicalCount" -> 0L;
                        case "selectList" -> rules;
                        default -> null;
                    });
            ReflectionTestUtils.setField(service, field.getName(), stub);
        }
        return service;
    }

    private <T> T mapper(DriverManagerDataSource source, Class<T> type) throws Exception {
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        var factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(source);
        factoryBean.setConfiguration(configuration);
        var factory = factoryBean.getObject();
        assertNotNull(factory);
        factory.getConfiguration().addMapper(type);
        return new SqlSessionTemplate(factory).getMapper(type);
    }
}
