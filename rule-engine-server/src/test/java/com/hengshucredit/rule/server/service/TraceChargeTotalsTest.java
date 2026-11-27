package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hengshucredit.rule.server.mapper.RuleBillingRecordMapper;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class TraceChargeTotalsTest {
    @Test
    public void totalsUseTheExactTraceProjectAndCurrencyAcrossAllRows() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        try {
            jdbc.execute("CREATE TABLE rule_billing_record(root_trace_id VARCHAR(32), project_id BIGINT, currency VARCHAR(8), amount DECIMAL(18,6))");
            jdbc.update("INSERT INTO rule_billing_record VALUES ('abc',1,'USD',0.012345),('abc',1,'USD',12.020000),"
                    + "('abc',1,'CNY',0),('abc',2,'USD',50),('abc-other',1,'USD',20)");
            var builder = new MybatisSqlSessionFactoryBean();
            builder.setDataSource(source);
            builder.setConfiguration(new MybatisConfiguration());
            var sessions = builder.getObject();
            sessions.getConfiguration().addMapper(RuleBillingRecordMapper.class);
            var mapper = new SqlSessionTemplate(sessions).getMapper(RuleBillingRecordMapper.class);
            assertEquals(List.of(new RuleBillingRecordMapper.TraceTotal("CNY", "0.000000"),
                    new RuleBillingRecordMapper.TraceTotal("USD", "12.032345")), mapper.totalsByTrace("abc", 1L));
            assertEquals(List.of(), mapper.totalsByTrace("missing", 1L));
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }
}
