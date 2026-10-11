package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleBillingConfig;
import com.hengshucredit.rule.model.entity.RuleBillingRecord;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.auth.ProjectAuthType;
import com.hengshucredit.rule.server.mapper.RuleBillingRecordMapper;
import org.springframework.dao.DuplicateKeyException;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class RuleBillingServiceTest {
    @Test public void billingRetainsExecutedVersionWhenLatestChangesBeforePersistence() {
        KeyedBillingService service = new KeyedBillingService();
        service.configs = List.of(config(501L, "PROJECT", 7L, "VERSION_TEST", "1"));
        RuleDefinition definition = definition(); definition.setExecutionTraceId("version-attribution");
        definition.setPublishedVersion(9); definition.setExecutionVersion(1); definition.setExecutionRevisionId(41L);
        definition.setExecutionArtifactDigest("executed-artifact");
        String snapshot = service.captureEngineExecutionBilling(definition, true, 12L, null, null);
        definition.setExecutionVersion(2); definition.setExecutionRevisionId(42L);
        service.recordEngineExecutionSnapshot(snapshot);
        RuleBillingRecord record = service.ledger.values().iterator().next();
        assertEquals(definition.getId(), record.getRuleId()); assertEquals(Integer.valueOf(1), record.getRuleVersion());
        assertEquals(Long.valueOf(41), record.getRevisionId()); assertEquals("executed-artifact", record.getArtifactDigest());
    }

    @Test
    public void billingAttemptsTreatNullAsFirstAndOnlyAcceptEqualOrNewerFacts() {
        Integer[][] attempts = {{null, null}, {null, 1}, {1, null}, {3, 1}, {1, 3}, {2, 2}, {3, null}};
        boolean[] acceptsIncoming = {true, true, true, false, true, true, false};
        for (int index = 0; index < attempts.length; index++) {
            KeyedBillingService service = new KeyedBillingService();
            RuleDefinition definition = definition();
            definition.setExecutionTraceId("attempt-matrix-" + index);
            definition.setExecutionAttemptNo(attempts[index][0]);
            service.configs = List.of(config(501L, "PROJECT", 7L, "ENGINE_CALL", "3"));
            String first = service.captureEngineExecutionBilling(definition, true, 30L, "old-error", null);
            service.recordEngineExecutionSnapshot(first);
            RuleBillingRecord original = service.ledger.values().iterator().next();
            definition.setExecutionAttemptNo(attempts[index][1]);
            service.configs = List.of(config(501L, "PROJECT", 7L, "ENGINE_CALL", "9"));
            String next = service.captureEngineExecutionBilling(definition, true, 90L, null, null);
            RuleBillingRecord incoming = com.alibaba.fastjson.JSON.parseObject(next, EngineBillingSnapshot.class).records().get(0);

            service.recordEngineExecutionSnapshot(next);

            assertEquals(1, service.ledger.size());
            RuleBillingRecord saved = service.ledger.values().iterator().next();
            RuleBillingRecord expected = acceptsIncoming[index] ? incoming : original;
            String scenario = "stored=" + attempts[index][0] + ", incoming=" + attempts[index][1];
            assertEquals(scenario, expected.getUnitPrice(), saved.getUnitPrice());
            assertEquals(scenario, expected.getAmount(), saved.getAmount());
            assertEquals(scenario, expected.getOccurTime(), saved.getOccurTime());
            assertEquals(scenario, expected.getErrorMessage(), saved.getErrorMessage());
            assertEquals(scenario, expected.getAttemptNo(), saved.getAttemptNo());
        }
    }

    @Test
    public void sameCodeGlobalAndProjectBillingItemsRemainTwoIndependentCharges() {
        KeyedBillingService service = new KeyedBillingService();
        service.configs = List.of(config(501L, "GLOBAL", 0L, "SAME_CODE", "1"),
                config(502L, "PROJECT", 7L, "SAME_CODE", "2"));
        RuleDefinition definition = definition();
        definition.setExecutionTraceId("root-with-two-billing-items");
        definition.setExecutionAttemptNo(1);
        String snapshot = service.captureEngineExecutionBilling(definition, true, 12L, null, null);

        service.recordEngineExecutionSnapshot(snapshot);
        service.recordEngineExecutionSnapshot(snapshot);

        assertEquals("不同计费配置ID不能因编码相同互相覆盖，重复恢复也不能新增", 2, service.ledger.size());
        assertEquals(java.util.Set.of(501L, 502L), service.ledger.values().stream()
                .map(RuleBillingRecord::getBillingConfigId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(new BigDecimal("3.000000"), service.ledger.values().stream()
                .map(RuleBillingRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    public void anExistingUnidentifiedEngineBillBlocksNewIdBasedChargesForThatTrace() {
        KeyedBillingService service = new KeyedBillingService();
        RuleDefinition definition = definition();
        definition.setExecutionTraceId("root-with-legacy-bill");
        service.configs = List.of(config(null, "PROJECT", 7L, "SAME_CODE", "1"));
        service.recordLegacyEngineExecution(definition, true, 12L, null, null);
        String original = com.alibaba.fastjson.JSON.toJSONString(service.ledger);
        service.configs = List.of(config(501L, "PROJECT", 7L, "SAME_CODE", "2"));
        String snapshot = service.captureEngineExecutionBilling(definition, true, 12L, null, null);

        assertThrows("旧账单无配置ID时不能按编码猜测关联，也不能另插新key重复收费", IllegalStateException.class,
                () -> service.recordEngineExecutionSnapshot(snapshot));

        assertEquals(original, com.alibaba.fastjson.JSON.toJSONString(service.ledger));
        assertEquals(1, service.ledger.size());
    }

    @Test
    public void legacyBillsForAnotherTraceDoNotBlockNewCharges() {
        KeyedBillingService service = new KeyedBillingService();
        RuleDefinition old = definition();
        old.setExecutionTraceId("older-trace");
        service.configs = List.of(config(null, "PROJECT", 7L, "SAME_CODE", "1"));
        service.recordLegacyEngineExecution(old, true, 12L, null, null);
        RuleDefinition current = definition();
        current.setExecutionTraceId("current-trace");
        service.configs = List.of(config(501L, "PROJECT", 7L, "SAME_CODE", "2"));

        service.recordEngineExecutionSnapshot(service.captureEngineExecutionBilling(current, true, 12L, null, null));

        assertEquals(2, service.ledger.size());
    }

    @Test
    public void anIdBearingSnapshotCannotRetainAnOldCodeBasedDedupKey() {
        KeyedBillingService service = new KeyedBillingService();
        RuleDefinition definition = definition();
        definition.setExecutionTraceId("root-with-stored-snapshot");
        service.configs = List.of(config(501L, "GLOBAL", 0L, "SAME_CODE", "1"),
                config(502L, "PROJECT", 7L, "SAME_CODE", "2"));
        EngineBillingSnapshot snapshot = com.alibaba.fastjson.JSON.parseObject(
                service.captureEngineExecutionBilling(definition, true, 12L, null, null), EngineBillingSnapshot.class);
        // 模拟此前已持久化的快照：明细有真实configId，但旧版key只由编码构成。
        for (RuleBillingRecord record : snapshot.records()) record.setBillingDedupKey("same-old-code-key");

        service.recordEngineExecutionSnapshot(com.alibaba.fastjson.JSON.toJSONString(snapshot));

        assertEquals("有ID的事实应按ID恢复，不能继续执行旧快照内冲突的派生key", 2, service.ledger.size());
    }

    private static RuleBillingConfig config(Long id, String scope, Long project, String code, String price) {
        RuleBillingConfig config = new RuleBillingConfig();
        config.setId(id);
        config.setScope(scope);
        config.setProjectId(project);
        config.setBillingTarget("ENGINE");
        config.setBillingCode(code);
        config.setBillingName(code);
        config.setChargeType("COUNT");
        config.setStatus(1);
        config.setUnitPrice(new BigDecimal(price));
        return config;
    }

    private static final class KeyedBillingService extends InMemoryBillingService {
        private final java.util.Map<String, RuleBillingRecord> ledger = new java.util.LinkedHashMap<>();

        @Override protected void insertRecord(RuleBillingRecord record) {
            if (ledger.containsKey(record.getBillingDedupKey())) throw new DuplicateKeyException("billing key exists");
            record.setId((long) ledger.size() + 1);
            ledger.put(record.getBillingDedupKey(), copy(record));
        }
        @Override protected RuleBillingRecord findByBillingDedupKey(String key) { return ledger.get(key); }
        @Override protected void updateRecord(RuleBillingRecord record) { ledger.put(record.getBillingDedupKey(), copy(record)); }
        @Override protected boolean hasLegacyEngineRecord(String trace) {
            return ledger.values().stream().anyMatch(record -> trace.equals(record.getRootTraceId())
                    && "ENGINE".equals(record.getBillingTarget()) && record.getBillingConfigId() == null);
        }
        private RuleBillingRecord copy(RuleBillingRecord record) {
            return com.alibaba.fastjson.JSON.parseObject(com.alibaba.fastjson.JSON.toJSONString(record), RuleBillingRecord.class);
        }
    }

    @Test
    public void malformedOrFailedSnapshotsNeverFallBackToCurrentPrices() {
        InMemoryBillingService service = serviceWithConfig();
        for (String json : List.of("{}", "null", "{\"version\":2,\"capturedAt\":\"2026-10-08T12:00:00\",\"records\":[]}",
                "{\"version\":1,\"capturedAt\":\"2026-10-08T12:00:00\"}",
                "{\"version\":1,\"capturedAt\":\"2026-10-08T12:00:00\",\"records\":[],\"captureError\":\"unavailable\"}")) {
            assertThrows(IllegalStateException.class, () -> service.recordEngineExecutionSnapshot(json));
        }
        assertEquals(0, service.records.size());
        assertNull("非法快照不应重新读取价格", service.configProjectId);
    }

    @Test
    public void snapshotValidatesAllAmountsBeforeWritingAnyRecord() {
        InMemoryBillingService service = serviceWithConfig();
        String json = service.captureEngineExecutionBilling(definition(), true, 25L, null, null);
        EngineBillingSnapshot snapshot = com.alibaba.fastjson.JSON.parseObject(json, EngineBillingSnapshot.class);
        RuleBillingRecord invalid = com.alibaba.fastjson.JSON.parseObject(
                com.alibaba.fastjson.JSON.toJSONString(snapshot.records().get(0)), RuleBillingRecord.class);
        invalid.setAmount(new BigDecimal("99"));
        EngineBillingSnapshot corrupt = new EngineBillingSnapshot(1, snapshot.capturedAt(),
                List.of(snapshot.records().get(0), invalid), null);
        assertThrows(IllegalStateException.class, () -> service.recordEngineExecutionSnapshot(
                com.alibaba.fastjson.JSON.toJSONString(corrupt)));
        assertEquals(0, service.records.size());
    }

    @Test
    public void legacyRecoveryExplicitlyMarksItsCurrentPriceBasis() {
        InMemoryBillingService service = serviceWithConfig();
        service.recordLegacyEngineExecution(definition(), true, 12L, null, null);
        assertEquals("LEGACY_REPLAY", service.records.get(0).getBillingBasis());
    }

    @Test
    public void rejectsUnconfirmedBillingInsertWithOrWithoutDedupKey() {
        for (String trace : java.util.Arrays.asList(null, "trace-new")) {
            RuleBillingService service = mapperBackedService(null, false, new AtomicInteger());
            RuleDefinition definition = definition();
            definition.setExecutionTraceId(trace);
            assertThrows("mapper insert返回0不能当成账单已写入", IllegalStateException.class,
                    () -> service.recordEngineExecution(definition, true, 12L, null));
        }
    }

    @Test
    public void rejectsUnconfirmedUpdateOfAnExistingBillingRecord() {
        RuleBillingRecord existing = new RuleBillingRecord();
        existing.setId(77L);
        existing.setAttemptNo(1);
        AtomicInteger updates = new AtomicInteger();
        RuleBillingService service = mapperBackedService(existing, true, updates);
        RuleDefinition definition = definition();
        definition.setExecutionTraceId("trace-update");
        definition.setExecutionAttemptNo(2);

        assertThrows("已存在旧账单不能掩盖新尝试更新失败", IllegalStateException.class,
                () -> service.recordEngineExecution(definition, true, 12L, null));
        assertEquals(1, updates.get());
    }

    @Test
    public void persistedNewerBillingAttemptDoesNotRequireAnotherWrite() {
        RuleBillingRecord existing = new RuleBillingRecord();
        existing.setId(77L);
        existing.setAttemptNo(3);
        AtomicInteger updates = new AtomicInteger();
        RuleBillingService service = mapperBackedService(existing, true, updates);
        RuleDefinition definition = definition();
        definition.setExecutionTraceId("trace-newer");
        definition.setExecutionAttemptNo(2);

        service.recordEngineExecution(definition, true, 12L, null);

        assertEquals("唯一键已经对应更高尝试，重放旧账单应正常去重", 0, updates.get());
    }

    private RuleBillingService mapperBackedService(RuleBillingRecord existing, boolean duplicate,
                                                   AtomicInteger updates) {
        RuleBillingRecordMapper mapper = (RuleBillingRecordMapper) Proxy.newProxyInstance(
                RuleBillingRecordMapper.class.getClassLoader(), new Class<?>[]{RuleBillingRecordMapper.class},
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName())) {
                        if (duplicate) throw new DuplicateKeyException("existing billing dedup key");
                        return 0;
                    }
                    if ("selectOne".equals(method.getName())) return existing;
                    if ("updateIfNoNewerAttempt".equals(method.getName())) { updates.incrementAndGet(); return 0; }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType().isPrimitive()) return 0;
                    return null;
                });
        RuleBillingService service = new RuleBillingService() {
            @Override protected List<RuleBillingConfig> findActiveEngineConfigs(RuleDefinition definition,
                    LocalDateTime now, Long projectId) {
                RuleBillingConfig config = new RuleBillingConfig();
                config.setBillingCode("ENGINE_CALL");
                config.setUnitPrice(BigDecimal.ONE);
                return Collections.singletonList(config);
            }
            @Override protected RuleProject findProject(Long projectId) { return null; }
        };
        ReflectionTestUtils.setField(service, "recordMapper", mapper);
        return service;
    }

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

    @Test public void trialBillingReplayKeepsFirstPriceOccurrenceAndFailureFactsWithoutFormalResourceId() {
        ApiBilling service = new ApiBilling();
        var api = new com.hengshucredit.rule.model.entity.RuleExternalApiConfig();
        api.setId(-65536L); api.setApiCode("DRAFT_PRICE"); api.setApiName("草稿报价");
        api.setUnitPrice(new BigDecimal("0.250000")); api.setExecutionCallId("trial-call-1");
        var source = new com.hengshucredit.rule.model.entity.RuleExternalDatasource();
        source.setDatasourceCode("LOCAL_PROVIDER"); source.setScope("GLOBAL");
        service.recordApiTrialExecution("trial-1", api, source, false, 123L, "等待供应商回调");
        RuleBillingRecord first = service.records.get(0);
        assertEquals("DRAFT_TRIAL", first.getBillingBasis());
        assertEquals("trial-1", first.getRootTraceId());
        assertNull(first.getTargetRefId());
        assertEquals(Integer.valueOf(0), first.getSuccess());
        assertEquals(new BigDecimal("0.250000"), first.getAmount());
        String firstFacts = com.alibaba.fastjson.JSON.toJSONString(first);
        // 恢复不是新的供应商提交，不能把首次计费时间/失败事实/价格偷偷改成恢复时的值。
        api.setUnitPrice(new BigDecimal("9.99"));
        service.recordApiTrialExecution("trial-1", api, source, true, 321L, null);
        assertEquals(1, service.records.size());
        assertEquals(firstFacts, com.alibaba.fastjson.JSON.toJSONString(service.records.get(0)));
        api.setExecutionCallId("trial-call-2");
        service.recordApiTrialExecution("trial-2", api, source, true, 12L, null);
        assertEquals(2, service.records.size());
        assertEquals(new BigDecimal("9.990000"), service.records.get(1).getAmount());
    }

    @Test public void trialBillingCannotWriteWithoutActualCallIdentity() {
        ApiBilling service = new ApiBilling();
        var api = new com.hengshucredit.rule.model.entity.RuleExternalApiConfig();
        var source = new com.hengshucredit.rule.model.entity.RuleExternalDatasource();
        assertThrows(IllegalArgumentException.class, () -> service.recordApiTrialExecution("trial", api, source, true, 1L, null));
        org.junit.Assert.assertTrue(service.records.isEmpty());
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

    private InMemoryBillingService serviceWithConfig() {
        InMemoryBillingService service = new InMemoryBillingService();
        RuleBillingConfig config = new RuleBillingConfig();
        config.setId(31L);
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

    private static class InMemoryBillingService extends RuleBillingService {
        protected List<RuleBillingConfig> configs = Collections.emptyList();
        private final List<RuleBillingRecord> records = new ArrayList<>();
        private Long configProjectId;

        @Override protected boolean hasLegacyEngineRecord(String trace) {
            return records.stream().anyMatch(record -> trace.equals(record.getRootTraceId())
                    && "ENGINE".equals(record.getBillingTarget()) && record.getBillingConfigId() == null);
        }

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
