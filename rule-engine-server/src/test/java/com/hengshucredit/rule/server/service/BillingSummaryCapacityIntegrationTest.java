package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hengshucredit.rule.server.config.MybatisPlusConfig;
import com.hengshucredit.rule.server.mapper.RuleBillingRecordMapper;
import com.hengshucredit.rule.server.mapper.RuleBillingSummaryMapper;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Assume;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.Assert.*;

/** 显式容量门禁：独有数据库，100万条真实明细，建议以-Xmx128m验证Java堆有界。 */
public class BillingSummaryCapacityIntegrationTest {
    @Test(timeout = 300000)
    public void oneMillionRecordsAggregateWithBoundedJavaHeap() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("billingSummary.capacity"));
        String database = "codex_billing_" + UUID.randomUUID().toString().replace("-", "");
        assertTrue(database.matches("codex_billing_[a-f0-9]{32}"));
        System.out.println("BILLING_CAPACITY_DATABASE=" + database);
        DriverManagerDataSource admin = (DriverManagerDataSource) BillingSummaryCurrencyIntegrationTest.localDataSource();
        admin.setUrl(admin.getUrl().replace("socketTimeout=15000", "socketTimeout=180000"));
        try (Connection connection = admin.getConnection()) {
            boolean created = false;
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
                }
                created = true;
                connection.setCatalog(database);
                SingleConnectionDataSource source = new SingleConnectionDataSource(connection, true);
                JdbcTemplate jdbc = new JdbcTemplate(source);
                assertEquals(database, jdbc.queryForObject("SELECT DATABASE()", String.class));
                String schema = new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8);
                for (String table : new String[]{"rule_billing_record", "rule_billing_summary", "rule_billing_summary_lock"}) {
                    int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `" + table + "`");
                    assertTrue(start >= 0);
                    jdbc.execute(schema.substring(start, schema.indexOf(';', start) + 1));
                }
                jdbc.update("INSERT INTO rule_billing_record (project_id, project_code, billing_code, billing_target, "
                        + "target_ref_id, auth_id, currency, amount, occur_time, cost_time_ms) "
                        + "WITH RECURSIVE numbers AS (SELECT 0 AS n UNION ALL SELECT n+1 FROM numbers WHERE n<999) "
                        + "SELECT 7, 'capacity', 'CALL', 'ENGINE', 31, 11, IF(MOD(a.n,2)=0,'CNY','USD'), "
                        + "IF(MOD(a.n,2)=0,1.23,2.50), '2026-10-01 12:00:00', 10 FROM numbers a CROSS JOIN numbers b");
                assertEquals(Long.valueOf(1000000), jdbc.queryForObject("SELECT COUNT(*) FROM rule_billing_record", Long.class));

                MybatisConfiguration configuration = new MybatisConfiguration();
                configuration.setMapUnderscoreToCamelCase(true);
                configuration.setLogImpl(NoLoggingImpl.class);
                MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
                factory.setDataSource(source); factory.setConfiguration(configuration);
                factory.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(new MybatisPlusConfig().metaObjectHandler()));
                factory.setTransactionFactory(new SpringManagedTransactionFactory());
                SqlSessionFactory sessions = factory.getObject();
                assertNotNull(sessions);
                sessions.getConfiguration().addMapper(RuleBillingRecordMapper.class);
                sessions.getConfiguration().addMapper(RuleBillingSummaryMapper.class);
                SqlSessionTemplate template = new SqlSessionTemplate(sessions);
                RuleBillingService target = new RuleBillingService();
                ReflectionTestUtils.setField(target, "recordMapper", template.getMapper(RuleBillingRecordMapper.class));
                ReflectionTestUtils.setField(target, "summaryMapper", template.getMapper(RuleBillingSummaryMapper.class));
                ProxyFactory proxy = new ProxyFactory(target);
                proxy.setProxyTargetClass(true);
                TransactionInterceptor transactions = new TransactionInterceptor();
                transactions.setTransactionManager(new DataSourceTransactionManager(source));
                transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
                proxy.addAdvice(transactions);
                RuleBillingService service = (RuleBillingService) proxy.getProxy();
                long began = System.nanoTime();
                assertEquals(2, service.refreshSummary(LocalDate.of(2026, 10, 1)));
                assertEquals(Long.valueOf(1000000), jdbc.queryForObject("SELECT SUM(total_count) FROM rule_billing_summary", Long.class));
                assertEquals(new BigDecimal("615000.000000"), jdbc.queryForObject("SELECT total_amount FROM rule_billing_summary WHERE currency='CNY'", BigDecimal.class));
                assertEquals(new BigDecimal("1250000.000000"), jdbc.queryForObject("SELECT total_amount FROM rule_billing_summary WHERE currency='USD'", BigDecimal.class));
                System.out.println("BILLING_CAPACITY rows=1000000 groups=2 elapsedMs=" + ((System.nanoTime()-began)/1000000)
                        + " maxHeapBytes=" + Runtime.getRuntime().maxMemory());
                jdbc.update("UPDATE rule_billing_record SET billing_code=CONCAT('CALL_',MOD(id,20000))");
                int expectedGroups = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT billing_code,currency FROM rule_billing_record GROUP BY billing_code,currency) groups_expected", Integer.class);
                assertTrue(expectedGroups >= 20000);
                began = System.nanoTime();
                assertEquals(expectedGroups, service.refreshSummary(LocalDate.of(2026, 10, 1)));
                assertEquals(Long.valueOf(1000000), jdbc.queryForObject("SELECT SUM(total_count) FROM rule_billing_summary", Long.class));
                assertEquals(new BigDecimal("615000.000000"), jdbc.queryForObject("SELECT SUM(total_amount) FROM rule_billing_summary WHERE currency='CNY'", BigDecimal.class));
                assertEquals(new BigDecimal("1250000.000000"), jdbc.queryForObject("SELECT SUM(total_amount) FROM rule_billing_summary WHERE currency='USD'", BigDecimal.class));
                System.out.println("BILLING_CAPACITY rows=1000000 groups=" + expectedGroups + " elapsedMs=" + ((System.nanoTime()-began)/1000000)
                        + " maxHeapBytes=" + Runtime.getRuntime().maxMemory());
            } finally {
                if (created) {
                    assertTrue(database.matches("codex_billing_[a-f0-9]{32}"));
                    try (Connection cleanup = admin.getConnection(); var statement = cleanup.createStatement()) {
                        statement.execute("DROP DATABASE `" + database + "`");
                    }
                }
            }
        }
    }
}
