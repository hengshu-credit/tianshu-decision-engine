package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hengshucredit.rule.model.entity.RuleProject;
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

            var project = new RuleProject();
            project.setId(9L);
            project.setProjectCode("CURRENT_CODE");
            project.setStatus(1);
            var service = new ProjectWorkbenchService();
            // 其余资源的数量与本用例无关；执行日志使用真实 MyBatis 查询和独立内存库。
            for (var field : ProjectWorkbenchService.class.getDeclaredFields()) {
                Class<?> type = field.getType();
                if (!type.isInterface() || !type.getSimpleName().endsWith("Mapper")) continue;
                Object mapper = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                        (proxy, method, arguments) -> switch (method.getName()) {
                            case "selectById" -> project;
                            case "selectCount" -> 0L;
                            case "selectList" -> List.of();
                            default -> null;
                        });
                ReflectionTestUtils.setField(service, field.getName(), mapper);
            }
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            var factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(source);
            factoryBean.setConfiguration(configuration);
            var factory = factoryBean.getObject();
            assertNotNull(factory);
            factory.getConfiguration().addMapper(RuleExecutionLogMapper.class);
            ReflectionTestUtils.setField(service, "executionLogMapper",
                    new SqlSessionTemplate(factory).getMapper(RuleExecutionLogMapper.class));

            var result = service.getWorkbench(9L);

            assertEquals(Long.valueOf(2), result.getMetrics().getRecentExecutionCount());
            assertEquals(Long.valueOf(1), result.getMetrics().getRecentSuccessCount());
            assertEquals("owned-failure", result.getRecentExecution().getRuleCode());
            assertEquals(Integer.valueOf(0), result.getRecentExecution().getSuccess());
            assertTrue(result.getWarnings().toString(), result.getWarnings().isEmpty());
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }
}
