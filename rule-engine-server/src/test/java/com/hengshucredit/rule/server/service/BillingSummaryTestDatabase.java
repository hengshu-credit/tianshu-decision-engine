package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hengshucredit.rule.server.config.MybatisPlusConfig;
import com.hengshucredit.rule.server.mapper.RuleBillingRecordMapper;
import com.hengshucredit.rule.server.mapper.RuleBillingSummaryMapper;
import com.hengshucredit.rule.server.mapper.RuleBillingSummaryTaskMapper;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** 每次测试独有的本机数据库，可使用多连接验证事务与恢复，不改动业务表。 */
final class BillingSummaryTestDatabase implements AutoCloseable {
    final DriverManagerDataSource source;
    final JdbcTemplate jdbc;
    final SqlSessionTemplate sessions;
    final RuleBillingService target;
    final RuleBillingService service;
    private final DriverManagerDataSource admin;
    final String database;
    private final boolean owner;

    BillingSummaryTestDatabase() throws Exception {
        this("codex_billing_" + UUID.randomUUID().toString().replace("-", ""), true);
    }

    BillingSummaryTestDatabase(String database, boolean owner) throws Exception {
        this.database = database;
        this.owner = owner;
        admin = (DriverManagerDataSource) BillingSummaryCurrencyIntegrationTest.localDataSource();
        admin.setUrl(admin.getUrl().replace("socketTimeout=15000", "socketTimeout=180000"));
        if (!database.matches("codex_billing_[a-f0-9]{32}")) throw new IllegalStateException("Invalid test database");
        if (owner) new JdbcTemplate(admin).execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
        String url = admin.getUrl();
        int query = url.indexOf('?');
        int slash = url.lastIndexOf('/', query);
        source = new DriverManagerDataSource(url.substring(0, slash + 1) + database + url.substring(query), admin.getUsername(), admin.getPassword());
        jdbc = new JdbcTemplate(source);
        try {
            if (!database.equals(jdbc.queryForObject("SELECT DATABASE()", String.class))) throw new IllegalStateException("Wrong test database");
            if (owner) {
                createTable("rule_billing_record");
                createTable("rule_billing_summary");
                createTable("rule_billing_summary_lock");
                createTable("rule_billing_summary_task");
            }
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setLogImpl(NoLoggingImpl.class);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source); factory.setConfiguration(configuration);
            factory.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(new MybatisPlusConfig().metaObjectHandler()));
            factory.setTransactionFactory(new SpringManagedTransactionFactory());
            var sessionFactory = factory.getObject();
            if (sessionFactory == null) throw new IllegalStateException("Missing mapper session factory");
            sessionFactory.getConfiguration().addMapper(RuleBillingRecordMapper.class);
            sessionFactory.getConfiguration().addMapper(RuleBillingSummaryMapper.class);
            sessionFactory.getConfiguration().addMapper(RuleBillingSummaryTaskMapper.class);
            sessions = new SqlSessionTemplate(sessionFactory);
            target = new RuleBillingService();
            ReflectionTestUtils.setField(target, "recordMapper", sessions.getMapper(RuleBillingRecordMapper.class));
            ReflectionTestUtils.setField(target, "summaryMapper", sessions.getMapper(RuleBillingSummaryMapper.class));
            ProxyFactory proxy = new ProxyFactory(target);
            proxy.setProxyTargetClass(true);
            TransactionInterceptor transactions = new TransactionInterceptor();
            transactions.setTransactionManager(new DataSourceTransactionManager(source));
            transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
            proxy.addAdvice(transactions);
            service = (RuleBillingService) proxy.getProxy();
        } catch (Exception | Error failure) {
            close();
            throw failure;
        }
    }

    void createTable(String table) throws Exception {
        if (!table.matches("rule_billing_[a-z_]+")) throw new IllegalArgumentException("Unexpected table");
        String schema = new ClassPathResource("sql/schema.sql").getContentAsString(StandardCharsets.UTF_8);
        int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `" + table + "`");
        if (start < 0) throw new IllegalStateException("Missing schema for " + table);
        jdbc.execute(schema.substring(start, schema.indexOf(';', start) + 1));
    }

    @Override public void close() {
        if (!owner) return;
        if (!database.matches("codex_billing_[a-f0-9]{32}")) throw new IllegalStateException("Invalid cleanup target");
        new JdbcTemplate(admin).execute("DROP DATABASE `" + database + "`");
    }
}
