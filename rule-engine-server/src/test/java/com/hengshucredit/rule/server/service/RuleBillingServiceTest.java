package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleBillingConfig;
import com.hengshucredit.rule.model.entity.RuleBillingRecord;
import com.hengshucredit.rule.model.entity.RuleBillingSummary;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.auth.ProjectAuthType;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RuleBillingServiceTest {

    @Test
    public void engineBillingRecordStoresAuthAndIndividualTokenAttribution() {
        InMemoryBillingService service = serviceWithConfig();
        RuleDefinition definition = definition();
        ProjectAuthContext context = ProjectAuthContext.temporary(7L, "credit", 11L,
                "BASIC_MAIN", ProjectAuthType.BASIC, 21L, "TOKEN_A", "GRACE");

        service.recordEngineExecution(definition, true, 12L, null, context);

        RuleBillingRecord record = service.records.get(0);
        assertEquals(Long.valueOf(11L), record.getAuthId());
        assertEquals("BASIC_MAIN", record.getAuthCode());
        assertEquals(ProjectAuthType.BASIC, record.getAuthType());
        assertEquals(Long.valueOf(21L), record.getTokenId());
        assertEquals("TOKEN_A", record.getTokenCode());
        assertEquals("GRACE", record.getAuthPhase());
    }

    @Test
    public void dailySummaryGroupsTokensByAuthButKeepsDifferentAuthSeparate() {
        InMemoryBillingService service = new InMemoryBillingService();
        LocalDate date = LocalDate.of(2026, 7, 15);
        service.records.add(record(date, 11L, "BASIC_MAIN", 21L, "TOKEN_A"));
        service.records.add(record(date, 11L, "BASIC_MAIN", 22L, "TOKEN_B"));
        service.records.add(record(date, 12L, "API_PARTNER", 23L, "TOKEN_C"));

        assertEquals(2, service.refreshSummary(date));

        assertEquals(2, service.summaries.size());
        RuleBillingSummary basic = findSummary(service.summaries, "BASIC_MAIN");
        assertEquals(Long.valueOf(2L), basic.getTotalCount());
        assertEquals(Long.valueOf(11L), basic.getAuthId());
        assertEquals(ProjectAuthType.BASIC, basic.getAuthType());
    }

    private InMemoryBillingService serviceWithConfig() {
        InMemoryBillingService service = new InMemoryBillingService();
        RuleBillingConfig config = new RuleBillingConfig();
        config.setBillingCode("ENGINE_CALL");
        config.setBillingName("规则调用");
        config.setBillingTarget("ENGINE");
        config.setChargeType("COUNT");
        config.setUnitPrice(BigDecimal.ONE);
        config.setCurrency("CNY");
        config.setStatus(1);
        service.configs = Collections.singletonList(config);
        return service;
    }

    private RuleDefinition definition() {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(31L);
        definition.setProjectId(7L);
        definition.setProjectCode("credit");
        definition.setRuleCode("R001");
        return definition;
    }

    private RuleBillingRecord record(LocalDate date, Long authId, String authCode,
                                     Long tokenId, String tokenCode) {
        RuleBillingRecord record = new RuleBillingRecord();
        record.setProjectId(7L);
        record.setProjectCode("credit");
        record.setBillingCode("ENGINE_CALL");
        record.setBillingTarget("ENGINE");
        record.setTargetRefId(31L);
        record.setSuccess(1);
        record.setQuantity(BigDecimal.ONE);
        record.setAmount(BigDecimal.ONE);
        record.setCurrency("CNY");
        record.setCostTimeMs(10L);
        record.setOccurTime(date.atTime(12, 0));
        record.setAuthId(authId);
        record.setAuthCode(authCode);
        record.setAuthType(authCode.startsWith("BASIC") ? ProjectAuthType.BASIC : ProjectAuthType.API_KEY);
        record.setTokenId(tokenId);
        record.setTokenCode(tokenCode);
        record.setAuthPhase("VALID");
        return record;
    }

    private RuleBillingSummary findSummary(List<RuleBillingSummary> summaries, String authCode) {
        for (RuleBillingSummary summary : summaries) {
            if (authCode.equals(summary.getAuthCode())) return summary;
        }
        throw new AssertionError("Summary not found: " + authCode);
    }

    private static class InMemoryBillingService extends RuleBillingService {
        private List<RuleBillingConfig> configs = Collections.emptyList();
        private final List<RuleBillingRecord> records = new ArrayList<>();
        private final List<RuleBillingSummary> summaries = new ArrayList<>();

        @Override
        protected List<RuleBillingConfig> findActiveEngineConfigs(RuleDefinition definition,
                                                                  LocalDateTime now) {
            return configs;
        }

        @Override
        protected RuleProject findProject(Long projectId) {
            RuleProject project = new RuleProject();
            project.setId(projectId);
            project.setProjectCode("credit");
            return project;
        }

        @Override
        protected void insertRecord(RuleBillingRecord record) {
            records.add(record);
        }

        @Override
        protected void deleteSummaries(LocalDate summaryDate) {
            summaries.clear();
        }

        @Override
        protected List<RuleBillingRecord> findRecords(LocalDateTime begin, LocalDateTime end) {
            return new ArrayList<>(records);
        }

        @Override
        protected void insertSummary(RuleBillingSummary summary) {
            summaries.add(summary);
        }
    }
}
