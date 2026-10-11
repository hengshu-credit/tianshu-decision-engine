package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.ExecutionVersionQuery;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.config.MybatisPlusConfig;
import com.hengshucredit.rule.server.mapper.RuleBillingVersionSummaryMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import static org.junit.Assert.*;

public class BillingVersionSummaryIntegrationTest {
    @Test(timeout = 60000)
    public void exactVersionAndLatestAggregateAttributedFactsWithoutMixingCurrenciesOrUnknownHistory() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("billingSummary.integration"));
        try (var database = new BillingSummaryTestDatabase()) {
            var config = database.sessions.getSqlSessionFactory().getConfiguration();
            config.addMapper(RuleBillingVersionSummaryMapper.class);
            config.addInterceptor(new MybatisPlusConfig().mybatisPlusInterceptor());
            var service = new RuleBillingVersionSummaryService();
            ReflectionTestUtils.setField(service, "mapper", database.sessions.getMapper(RuleBillingVersionSummaryMapper.class));
            ReflectionTestUtils.setField(service, "projects", new ProjectFilterService());
            var filter = new ExecutionVersionFilter();
            var latest = new RulePublished(); latest.setDefinitionId(164L); latest.setVersion(3);
            ReflectionTestUtils.setField(filter, "published", Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{RulePublishedMapper.class}, (p,m,a) -> latest));
            ReflectionTestUtils.setField(service, "versions", filter);
            for (int index=0; index<6; index++) {
                database.jdbc.update("INSERT INTO rule_billing_record(project_id,project_code,billing_code,billing_target,target_ref_id,auth_id,currency,rule_id,rule_version,revision_id,success,quantity,amount,cost_time_ms,occur_time) VALUES(7,'version_test','VERSION_CALL','ENGINE',164,11,?,?,?,?,?,1,?,?,'2026-10-11 23:59:59')",
                        index==3 ? "USD" : "CNY", index==5 ? 165 : 164,
                        index==4 ? null : index==2 ? 3 : 1, index==2 ? 172 : 168,
                        index==1 ? 0 : 1, index==1 ? "0.02" : "0.01", index==1 ? 30 : 10);
            }
            database.jdbc.update("INSERT INTO rule_billing_record(project_id,project_code,billing_code,billing_target,target_ref_id,rule_id,rule_version,amount,occur_time) VALUES(7,'version_test','VERSION_CALL','ENGINE',164,164,1,99,'2026-10-12 00:00:00')");
            var query = new ExecutionVersionQuery(); query.setRuleId(164L); query.setVersionMode("FIXED"); query.setRuleVersion(1);
            var first = service.page(1,1,"ENGINE","VERSION_CALL",null,null,null,null,"2026-10-11","2026-10-11",query);
            assertEquals(2,first.getTotal()); assertEquals(1,first.getRecords().size());
            var cny=first.getRecords().get(0); assertEquals("CNY",cny.getCurrency()); assertEquals("version_test",cny.getProjectCode());
            assertEquals(Long.valueOf(2),cny.getTotalCount()); assertEquals(Long.valueOf(1),cny.getSuccessCount()); assertEquals(Long.valueOf(1),cny.getFailCount());
            assertEquals(new BigDecimal("0.030000"),cny.getTotalAmount()); assertEquals(new BigDecimal("20.00"),cny.getAvgCostTimeMs());
            var second = service.page(2,1,"ENGINE","VERSION_CALL",null,null,null,null,"2026-10-11","2026-10-11",query);
            assertEquals("USD",second.getRecords().get(0).getCurrency());
            query.setVersionMode("LATEST");
            var current = service.page(1,10,null,null,null,null,null,null,"2026-10-11","2026-10-11",query);
            assertEquals(1,current.getTotal()); assertEquals(Long.valueOf(1),current.getRecords().get(0).getTotalCount());
            assertEquals(new BigDecimal("0.010000"),current.getRecords().get(0).getTotalAmount());
            query.setVersionMode("FIXED"); query.setRuleVersion(1); query.setRevisionId(172L);
            assertEquals(0,service.page(1,10,null,null,null,null,null,null,"2026-10-11","2026-10-11",query).getTotal());
            assertEquals(Integer.valueOf(0),database.jdbc.queryForObject("SELECT COUNT(*) FROM rule_billing_summary",Integer.class));
        }
    }
}
