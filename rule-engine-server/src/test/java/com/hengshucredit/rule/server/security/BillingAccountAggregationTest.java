package com.hengshucredit.rule.server.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hengshucredit.rule.server.mapper.RuleBillingSummaryMapper;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.Assert.*;

public class BillingAccountAggregationTest {
    @Test public void aggregationKeepsOwnersSeparateAndCrossGrantStillRespectsProjects() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:billing-owner" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        String schema = new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8);
        for (String table : new String[]{"rule_billing_record", "rule_billing_summary"}) {
            int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `" + table + "`");
            String ddl = schema.substring(start, schema.indexOf(';', start) + 1).replaceFirst("(?s)\\) ENGINE=.*;", ")")
                    .replaceAll("(?i)(\\b(?:KEY|INDEX)\\s+`)([^`]+)(`)", "$1" + table + "_$2$3");
            jdbc.execute(ddl);
        }
        for (int[] row : new int[][]{{8,9,10}, {7,9,20}, {8,10,99}}) {
            jdbc.update("INSERT INTO rule_billing_record(actor_user_id,project_id,project_code,billing_code,billing_target,target_ref_id,auth_id,currency,quantity,amount,success,occur_time) VALUES(?,?,'P','FEE','ENGINE',1,1,'CNY',1,?,1,'2026-11-04 12:00:00')", row[0], row[1], row[2]);
        }
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration);
        factory.setGlobalConfig(new GlobalConfig().setBanner(false));
        var sessions = factory.getObject(); sessions.getConfiguration().addMapper(RuleBillingSummaryMapper.class);
        var mapper = new SqlSessionTemplate(sessions).getMapper(RuleBillingSummaryMapper.class);
        var date = LocalDate.of(2026,11,4);
        assertEquals(3, mapper.aggregateDate(date, date.atStartOfDay(), date.plusDays(1).atStartOfDay()));
        var scope = new java.util.concurrent.atomic.AtomicReference<>(new ConsoleProjectAccess.Scope(8L, Set.of(9L), Set.of("billing:view")));
        var policy = new ConsoleDataPermissionHandler(new ConsoleProjectAccess() { @Override public Scope current() { return scope.get(); } });
        assertEquals(0, new BigDecimal("10").compareTo(jdbc.queryForObject(policy.filterSql("SELECT SUM(total_amount) FROM rule_billing_summary"), BigDecimal.class)));
        scope.set(new ConsoleProjectAccess.Scope(8L, Set.of(9L), Set.of("billing:view", "account:cross-record-view"), false, true, Map.of()));
        assertEquals(0, new BigDecimal("30").compareTo(jdbc.queryForObject(policy.filterSql("SELECT SUM(total_amount) FROM rule_billing_summary"), BigDecimal.class)));
        jdbc.execute("SHUTDOWN");
    }
}
