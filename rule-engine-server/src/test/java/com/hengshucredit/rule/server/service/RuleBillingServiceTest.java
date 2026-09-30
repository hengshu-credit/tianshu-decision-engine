package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleBillingConfig;
import com.hengshucredit.rule.model.entity.RuleBillingRecord;
import com.hengshucredit.rule.model.entity.RuleBillingSummary;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.auth.ProjectAuthType;
import org.springframework.dao.DuplicateKeyException;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class RuleBillingServiceTest {

    @Test public void apiApprovalSynchronizesBillingAndOneCallIdProducesOneRecord() {
        ApiBilling service = new ApiBilling();
        var api = new com.hengshucredit.rule.model.entity.RuleExternalApiConfig();
        api.setId(7L); api.setApiName("本地接口"); api.setStatus(1); api.setUnitPrice(new BigDecimal("0.20"));
        api.setExecutionConfig("{\"version\":2,\"billingBranches\":[{\"id\":\"charge\",\"bill\":true}]}");
        var source = new com.hengshucredit.rule.model.entity.RuleExternalDatasource();
        source.setScope("GLOBAL");
        service.syncApiBilling(api, source);
        assertEquals("API_7", service.config.getBillingCode());
        assertEquals(Long.valueOf(7), service.config.getTargetRefId());
        api.setExecutionCallId("same-chain");
        org.junit.Assert.assertTrue(service.recordApiExecution(api, source, true, 2L, null));
        org.junit.Assert.assertTrue(service.recordApiExecution(api, source, true, 2L, null));
        assertEquals(1, service.records.size());
        assertEquals(new BigDecimal("0.200000"), service.records.get(0).getAmount());
        api.setExecutionConfig("{\"version\":2}");
        service.syncApiBilling(api, source);
        assertEquals(Integer.valueOf(0), service.config.getStatus());
        org.junit.Assert.assertFalse(service.recordApiExecution(api, source, true, 2L, null));
    }

    private static class ApiBilling extends RuleBillingService {
        private RuleBillingConfig config;
        private final List<RuleBillingRecord> records = new ArrayList<>();
        @Override public RuleBillingConfig getOne(com.baomidou.mybatisplus.core.conditions.Wrapper<RuleBillingConfig> query) { return config; }
        @Override public boolean save(RuleBillingConfig value) { config = value; config.setId(1L); return true; }
        @Override public boolean updateById(RuleBillingConfig value) { config = value; return true; }
        @Override public List<RuleBillingConfig> list(com.baomidou.mybatisplus.core.conditions.Wrapper<RuleBillingConfig> query) {
            return config != null && Integer.valueOf(1).equals(config.getStatus()) ? List.of(config) : List.of();
        }
        @Override protected void insertRecord(RuleBillingRecord record) {
            if (findByBillingDedupKey(record.getBillingDedupKey()) != null) throw new DuplicateKeyException("duplicate");
            records.add(record);
        }
        @Override protected RuleBillingRecord findByBillingDedupKey(String key) {
            return records.stream().filter(record -> key.equals(record.getBillingDedupKey())).findFirst().orElse(null);
        }
    }

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
    public void sharedRuleBillingUsesAuthenticatedCallingProject() {
        InMemoryBillingService service = serviceWithConfig();
        RuleDefinition globalDefinition = definition();
        globalDefinition.setProjectId(0L);
        globalDefinition.setProjectCode(null);
        ProjectAuthContext context = ProjectAuthContext.direct(9L, "calling-project", 11L,
                "BASIC_MAIN", ProjectAuthType.BASIC);

        service.recordEngineExecution(globalDefinition, true, 12L, null, context);

        RuleBillingRecord record = service.records.get(0);
        assertEquals(Long.valueOf(9L), record.getProjectId());
        assertEquals("calling-project", record.getProjectCode());
        assertEquals(Long.valueOf(9L), service.configProjectId);
    }

    @Test
    public void engineBillingUsesStableDedupKeyOnlyWhenRootTraceExists() {
        InMemoryBillingService service = serviceWithConfig();
        RuleDefinition definition = definition();
        definition.setExecutionTraceId("RPG000720260925120000000ABCDEF123456");

        service.recordEngineExecution(definition, true, 12L, null);

        RuleBillingRecord record = service.records.get(0);
        assertEquals(definition.getExecutionTraceId(), record.getRootTraceId());
        assertNotNull(record.getBillingDedupKey());
        assertEquals(64, record.getBillingDedupKey().length());
    }

    @Test
    public void engineBillingWithoutRootTraceKeepsLegacyNonIdempotentInsertPath() {
        InMemoryBillingService service = serviceWithConfig();

        service.recordEngineExecution(definition(), true, 12L, null);

        assertNull(service.records.get(0).getBillingDedupKey());
    }

    @Test
    public void concurrentEngineBillingDuplicateIsUpdatedByDatabaseOwnedRow() {
        ConcurrentBillingService service = new ConcurrentBillingService();
        RuleBillingConfig config = new RuleBillingConfig();
        config.setBillingCode("ENGINE_CALL");
        config.setBillingName("规则调用");
        config.setBillingTarget("ENGINE");
        config.setChargeType("COUNT");
        config.setUnitPrice(BigDecimal.ONE);
        config.setCurrency("CNY");
        config.setStatus(1);
        service.configs = Collections.singletonList(config);

        RuleDefinition definition = definition();
        definition.setExecutionTraceId("RPG000720260925120000000ABCDEF123456");
        service.recordEngineExecution(definition, true, 12L, null);

        assertEquals(1, service.updateCount);
        assertEquals(Long.valueOf(77L), service.updatedRecord.getId());
        assertNotNull(service.lookupKeyRootTrace);
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
        protected List<RuleBillingConfig> configs = Collections.emptyList();
        private final List<RuleBillingRecord> records = new ArrayList<>();
        private final List<RuleBillingSummary> summaries = new ArrayList<>();
        private Long configProjectId;

        @Override
        protected List<RuleBillingConfig> findActiveEngineConfigs(RuleDefinition definition,
                                                                  LocalDateTime now,
                                                                  Long executionProjectId) {
            configProjectId = executionProjectId;
            return configs;
        }

        @Override
        protected RuleProject findProject(Long projectId) {
            RuleProject project = new RuleProject();
            project.setId(projectId);
            project.setProjectCode(Long.valueOf(9L).equals(projectId) ? "calling-project" : "credit");
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

    private static final class ConcurrentBillingService extends InMemoryBillingService {
        private int updateCount;
        private RuleBillingRecord updatedRecord;
        private String lookupKeyRootTrace;

        @Override
        protected void insertRecord(RuleBillingRecord record) {
            throw new DuplicateKeyException("duplicate billing_dedup_key");
        }

        @Override
        protected RuleBillingRecord findByBillingDedupKey(String billingDedupKey) {
            lookupKeyRootTrace = billingDedupKey;
            RuleBillingRecord existing = new RuleBillingRecord();
            existing.setId(77L);
            return existing;
        }

        @Override
        protected void updateRecord(RuleBillingRecord record) {
            updateCount++;
            updatedRecord = record;
        }
    }
}
