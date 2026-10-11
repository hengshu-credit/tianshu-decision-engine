package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.core.trace.TraceIdGenerator;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleTraceRegistry;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ClientExecutionLogIngestionServiceTest {
    @Test
    public void kafkaUsesBoundProjectAndClearsForgedServerFactsAndCredentials() {
        Fixture f = new Fixture();
        RuleExecutionLog entry = entry();
        entry.setProjectCode("old-or-forged-name");
        entry.setSource("SERVER");
        entry.setRootRuleId(999L);
        entry.setExecutionProjectId(99L);
        entry.setHistoryFields("forged");
        entry.setId(200L);
        entry.setAuthId(8L);
        entry.setAuthCode("forged");
        entry.setAuthType("BASIC");
        entry.setTokenId(9L);
        entry.setTokenCode("forged");
        entry.setAuthPhase("VALID");
        entry.setRevisionId(5L);
        entry.setArtifactDigest("actual-sdk-version");
        entry.setRuleVersion(3);

        assertEquals(1, f.service.ingestKafka(entry, 1L));
        RuleExecutionLog saved = f.logs.get(0);
        assertEquals("CURRENT_PROJECT", saved.getProjectCode());
        assertEquals("CLIENT", saved.getSource());
        assertNull(saved.getId());
        assertNull(saved.getRootRuleId());
        assertNull(saved.getExecutionProjectId());
        assertNull(saved.getHistoryFields());
        assertNull(saved.getAuthId());
        assertNull(saved.getAuthCode());
        assertNull(saved.getAuthType());
        assertNull(saved.getTokenId());
        assertNull(saved.getTokenCode());
        assertNull(saved.getAuthPhase());
        assertEquals(Long.valueOf(5L), saved.getRevisionId());
        assertEquals("actual-sdk-version", saved.getArtifactDigest());
        assertEquals(Long.valueOf(1L), f.billedScopes.get(0).getProjectId());
        assertEquals(Long.valueOf(1L), f.registry.rows.get(entry.getTraceId()).getProjectId());
    }

    @Test
    public void httpThenKafkaReplayKeepsOriginalAuthAndDoesNotBillTwice() {
        Fixture f = new Fixture();
        RuleExecutionLog first = entry();
        RuleExecutionLog replay = copy(first);
        ProjectAuthContext auth = ProjectAuthContext.temporary(1L, "CURRENT_PROJECT",
                11L, "BASIC_MAIN", "BASIC", 21L, "TOKEN_A", "VALID");

        assertEquals(1, f.service.ingestHttp(List.of(first), auth));
        assertEquals(0, f.service.ingestKafka(replay, 1L));

        assertEquals(1, f.logs.size());
        assertEquals(1, f.billedScopes.size());
        assertEquals("BASIC_MAIN", f.logs.get(0).getAuthCode());
        assertEquals("TOKEN_A", f.logs.get(0).getTokenCode());
    }

    @Test
    public void duplicateWithinHttpBatchWritesAndBillsOnlyOnce() {
        Fixture f = new Fixture();
        RuleExecutionLog first = entry();
        assertEquals(1, f.service.ingestHttp(List.of(first, copy(first)), auth()));
        assertEquals(1, f.logs.size());
        assertEquals(1, f.billedScopes.size());
    }

    @Test
    public void duplicateRootCannotAppendUnpersistedChildRegistrations() {
        Fixture f = new Fixture();
        RuleExecutionLog first = entry();
        assertEquals(1, f.service.ingestKafka(first, 1L));
        RuleExecutionLog replay = copy(first);
        String child = TraceIdGenerator.generate("QL", "P", "0001");
        replay.setTraceInfo("[{\"traceId\":\"" + replay.getTraceId()
                + "\",\"ruleCode\":\"RULE\",\"children\":[{\"traceId\":\"" + child
                + "\",\"ruleCode\":\"CHILD\"}]}]");
        assertEquals(409, assertThrows(ClientLogValidationException.class,
                () -> f.service.ingestKafka(replay, 1L)).getStatusCode());
        assertFalse(f.registry.rows.containsKey(child));
        assertEquals(1, f.registry.rows.size());
        assertEquals(1, f.logs.size());
    }

    @Test
    public void validatesEveryTraceBeforeRegisteringAnything() {
        Fixture f = new Fixture();
        RuleExecutionLog valid = entry();
        RuleExecutionLog wrongProject = entry();
        wrongProject.setTraceId(TraceIdGenerator.generate("QL", "P", "0002"));

        assertEquals(403, assertThrows(ClientLogValidationException.class,
                () -> f.service.ingestHttp(List.of(valid, wrongProject), auth())).getStatusCode());
        assertTrue(f.registry.rows.isEmpty());
        assertTrue(f.logs.isEmpty());
        assertTrue(f.billedScopes.isEmpty());
    }

    @Test
    public void malformedInnerTraceDoesNotLeakPayloadOrRetryAsDatabaseFailure() {
        Fixture f = new Fixture();
        RuleExecutionLog bad = entry();
        bad.setTraceInfo("[{secret_customer_payload");
        ClientLogValidationException error = assertThrows(ClientLogValidationException.class,
                () -> f.service.ingestKafka(bad, 1L));
        assertEquals(400, error.getStatusCode());
        assertFalse(error.getMessage().contains("secret_customer_payload"));
        assertNull(error.getCause());
        assertTrue(f.registry.rows.isEmpty());
    }

    @Test
    public void traceTreeCannotForgeRuleIdOrUseAnotherRoot() {
        Fixture f = new Fixture();
        RuleExecutionLog bad = entry();
        bad.setTraceInfo("[{\"traceId\":\"" + bad.getTraceId() + "\",\"ruleCode\":\"RULE\",\"ruleId\":999}]");
        assertEquals(403, assertThrows(ClientLogValidationException.class,
                () -> f.service.ingestKafka(bad, 1L)).getStatusCode());
        assertTrue(f.registry.rows.isEmpty());
        bad.setTraceInfo("[{\"traceId\":\"" + bad.getTraceId() + "\",\"ruleCode\":\"OTHER\",\"ruleId\":12}]");
        assertEquals(400, assertThrows(ClientLogValidationException.class,
                () -> f.service.ingestKafka(bad, 1L)).getStatusCode());
        assertTrue(f.logs.isEmpty());
    }

    @Test
    public void globalTraceRetainsCallingProjectAndRejectsCrossProjectReplay() {
        Fixture f = new Fixture();
        f.global = true;
        RuleExecutionLog first = entry();
        first.setTraceId(TraceIdGenerator.generate("QL", "G", "0000"));
        assertEquals(1, f.service.ingestKafka(first, 1L));
        assertEquals(Long.valueOf(1L), f.registry.rows.get(first.getTraceId()).getProjectId());
        assertEquals("G", f.registry.rows.get(first.getTraceId()).getScopeType());
        assertEquals(Long.valueOf(1L), f.billedScopes.get(0).getProjectId());

        assertEquals(409, assertThrows(ClientLogValidationException.class,
                () -> f.service.ingestKafka(copy(first), 2L)).getStatusCode());
        assertEquals(1, f.logs.size());
    }

    @Test
    public void kafkaRejectsMissingTraceDisabledProjectAndUnavailableRule() {
        Fixture f = new Fixture();
        RuleExecutionLog missing = entry();
        missing.setTraceId(null);
        assertEquals(400, assertThrows(ClientLogValidationException.class,
                () -> f.service.ingestKafka(missing, 1L)).getStatusCode());
        f.projectEnabled = false;
        assertEquals(403, assertThrows(ClientLogValidationException.class,
                () -> f.service.ingestKafka(entry(), 1L)).getStatusCode());
        f.projectEnabled = true;
        f.ruleAccessible = false;
        assertEquals(403, assertThrows(ClientLogValidationException.class,
                () -> f.service.ingestKafka(entry(), 1L)).getStatusCode());
        assertTrue(f.registry.rows.isEmpty());
    }

    @Test
    public void billingFailureRollsBackWholeHttpBatchAndKafkaRecordThroughServiceProxy() {
        for (boolean kafka : List.of(false, true)) {
            Fixture f = new Fixture();
            RecordingTransactionManager tx = new RecordingTransactionManager();
            ClientExecutionLogIngestionService proxied = proxy(f.service, tx);
            f.requireTransaction = true;
            f.failBilling = true;
            RuleExecutionLog first = entry();
            List<RuleExecutionLog> batch = List.of(first, entry());

            assertThrows(DataAccessResourceFailureException.class, () -> {
                if (kafka) proxied.ingestKafka(first, 1L);
                else proxied.ingestHttp(batch, auth());
            });
            assertEquals(0, tx.commits);
            assertEquals(1, tx.rollbacks);
            assertTrue("登记必须随计费失败回滚", f.registry.rows.isEmpty());
            assertTrue("日志不能在事务提交前写入已提交列表", f.logs.isEmpty());
            assertTrue(f.billedScopes.isEmpty());

            f.failBilling = false;
            assertEquals(kafka ? 1 : 2, kafka ? proxied.ingestKafka(first, 1L)
                    : proxied.ingestHttp(batch, auth()));
            assertEquals(1, tx.commits);
            assertEquals(kafka ? 1 : 2, f.logs.size());
            assertEquals(kafka ? 1 : 2, f.billedScopes.size());
        }
    }

    private static ClientExecutionLogIngestionService proxy(ClientExecutionLogIngestionService service,
                                                             RecordingTransactionManager tx) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(tx);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        interceptor.afterPropertiesSet();
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return (ClientExecutionLogIngestionService) factory.getProxy();
    }

    private static ProjectAuthContext auth() {
        return ProjectAuthContext.direct(1L, "CURRENT_PROJECT", 11L, "BASIC_MAIN", "BASIC");
    }

    private static RuleExecutionLog entry() {
        RuleExecutionLog entry = new RuleExecutionLog();
        entry.setTraceId(TraceIdGenerator.generate("QL", "P", "0001"));
        entry.setRuleCode("RULE");
        entry.setSuccess(1);
        entry.setExecuteTimeMs(10L);
        return entry;
    }

    private static RuleExecutionLog copy(RuleExecutionLog entry) {
        return JSON.parseObject(JSON.toJSONString(entry), RuleExecutionLog.class);
    }

    private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }

    private static class Fixture {
        final ClientExecutionLogIngestionService service = new ClientExecutionLogIngestionService();
        final List<RuleExecutionLog> logs = new ArrayList<>();
        final List<ProjectAuthContext> billedScopes = new ArrayList<>();
        final MemoryRegistry registry = new MemoryRegistry();
        boolean global;
        boolean projectEnabled = true;
        boolean ruleAccessible = true;
        boolean failBilling;
        boolean requireTransaction;

        Fixture() {
            ReflectionTestUtils.setField(service, "logService", new RuleExecutionLogService() {
                @Override public void applyPublishedAttribution(RuleExecutionLog log, String projectCode) { }
                @Override public void saveLogicalBatch(Collection<RuleExecutionLog> entries) {
                    checkTransaction();
                    List<RuleExecutionLog> copies = entries.stream().map(ClientExecutionLogIngestionServiceTest::copy).toList();
                    afterCommit(() -> logs.addAll(copies));
                }
            });
            ReflectionTestUtils.setField(service, "billingService", new RuleBillingService() {
                @Override public boolean isRuleAccessible(Long projectId, String code) { return ruleAccessible; }
                @Override protected RuleDefinition findDefinitionByRuleCode(String code, Long projectId) {
                    RuleDefinition rule = new RuleDefinition();
                    rule.setId(12L);
                    rule.setRuleCode(code);
                    rule.setModelType("SCRIPT");
                    rule.setScope(global ? "GLOBAL" : "PROJECT");
                    return rule;
                }
                @Override public void recordEngineExecutionLog(RuleExecutionLog log, ProjectAuthContext auth) {
                    checkTransaction();
                    if (failBilling) throw new DataAccessResourceFailureException("database unavailable");
                    afterCommit(() -> billedScopes.add(auth));
                }
            });
            ReflectionTestUtils.setField(service, "traceRegistryService", registry);
            ReflectionTestUtils.setField(service, "projectService", new RuleProjectService() {
                @Override public RuleProject getById(Serializable id) {
                    RuleProject project = new RuleProject();
                    project.setId((Long) id);
                    project.setProjectCode("CURRENT_PROJECT");
                    project.setStatus(projectEnabled ? 1 : 0);
                    return project;
                }
            });
        }

        void checkTransaction() {
            if (requireTransaction) assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        }
        void afterCommit(Runnable action) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) { action.run(); return; }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        }

        class MemoryRegistry extends RuleTraceRegistryService {
            final Map<String, RuleTraceRegistry> rows = new LinkedHashMap<>();
            @Override protected void insertRegistry(RuleTraceRegistry row) {
                checkTransaction();
                if (rows.putIfAbsent(row.getTraceId(), row) != null) throw new DuplicateKeyException("duplicate");
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) rows.remove(row.getTraceId());
                        }
                    });
                }
            }
            @Override protected RuleTraceRegistry findRegisteredTrace(String id) { return rows.get(id); }
        }
    }
}

