package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleExecutionPersistenceOutbox;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.mapper.RuleExecutionPersistenceOutboxMapper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;

public class RuleExecutionPersistenceOutboxServiceTest {
    @Test
    public void enqueueStoresOnlyFailedSidesAndAuthAttribution() {
        AtomicReference<RuleExecutionPersistenceOutbox> captured = new AtomicReference<>();
        RuleExecutionPersistenceOutboxMapper mapper = mapper(captured);
        RuleExecutionPersistenceOutboxService service = new RuleExecutionPersistenceOutboxService();
        ReflectionTestUtils.setField(service, "mapper", mapper);

        RuleExecutionLog log = new RuleExecutionLog();
        log.setTraceId("root-1");
        RuleDefinition definition = new RuleDefinition();
        definition.setRuleCode("risk_rule");
        ProjectAuthContext auth = ProjectAuthContext.direct(7L, "project-a", 8L, "auth", "TOKEN");

        service.enqueue(log, definition, true, 12L, "", auth, true, false);

        RuleExecutionPersistenceOutbox row = captured.get();
        Assert.assertNotNull(row);
        Assert.assertEquals(Integer.valueOf(1), row.getLogPending());
        Assert.assertEquals(Integer.valueOf(0), row.getBillingPending());
        Assert.assertEquals(Long.valueOf(7L), row.getProjectId());
        Assert.assertEquals("project-a", row.getProjectCode());
        Assert.assertTrue(row.getLogJson().contains("root-1"));
        Assert.assertTrue(row.getDefinitionJson().contains("risk_rule"));
    }

    @Test
    public void replayClearsSuccessfulLogPendingSide() {
        AtomicReference<RuleExecutionPersistenceOutbox> updated = new AtomicReference<>();
        AtomicInteger logs = new AtomicInteger();
        RuleExecutionPersistenceOutbox row = row(true, false);
        RuleExecutionPersistenceOutboxService service = new RuleExecutionPersistenceOutboxService();
        ReflectionTestUtils.setField(service, "mapper", replayMapper(row, updated));
        ReflectionTestUtils.setField(service, "logService", new RuleExecutionLogService() {
            @Override public void saveLogical(RuleExecutionLog entity) { logs.incrementAndGet(); }
        });
        ReflectionTestUtils.setField(service, "billingService", new RuleBillingService());

        service.replayPending();

        Assert.assertEquals(1, logs.get());
        Assert.assertEquals(Integer.valueOf(0), row.getLogPending());
        Assert.assertEquals("DELIVERED", updated.get().getDeliveryStatus());
    }

    @Test
    public void replayKeepsBillingPendingAndMovesToDeadLetterAfterRetryLimit() {
        AtomicReference<RuleExecutionPersistenceOutbox> updated = new AtomicReference<>();
        RuleExecutionPersistenceOutbox row = row(false, true);
        RuleExecutionPersistenceOutboxService service = new RuleExecutionPersistenceOutboxService();
        ReflectionTestUtils.setField(service, "mapper", replayMapper(row, updated));
        ReflectionTestUtils.setField(service, "logService", new RuleExecutionLogService());
        ReflectionTestUtils.setField(service, "billingService", new RuleBillingService() {
            @Override public void recordEngineExecution(RuleDefinition definition, boolean success,
                                                         Long costTimeMs, String errorMessage,
                                                         ProjectAuthContext authContext) {
                throw new IllegalStateException("billing unavailable");
            }
        });
        ReflectionTestUtils.setField(service, "maxRetries", 1);

        service.replayPending();

        Assert.assertEquals(Integer.valueOf(1), row.getBillingPending());
        Assert.assertEquals("DEAD_LETTER", updated.get().getDeliveryStatus());
        Assert.assertTrue(updated.get().getLastError().contains("billing"));
    }

    @Test
    public void replayMissingLogPayloadDoesNotSilentlySucceed() {
        AtomicReference<RuleExecutionPersistenceOutbox> updated = new AtomicReference<>();
        RuleExecutionPersistenceOutbox row = row(true, false);
        row.setLogJson(null);
        RuleExecutionPersistenceOutboxService service = new RuleExecutionPersistenceOutboxService();
        ReflectionTestUtils.setField(service, "mapper", replayMapper(row, updated));
        ReflectionTestUtils.setField(service, "logService", new RuleExecutionLogService());
        ReflectionTestUtils.setField(service, "billingService", new RuleBillingService());
        ReflectionTestUtils.setField(service, "maxRetries", 1);

        service.replayPending();

        Assert.assertEquals(Integer.valueOf(1), row.getLogPending());
        Assert.assertEquals("DEAD_LETTER", updated.get().getDeliveryStatus());
    }

    private static RuleExecutionPersistenceOutbox row(boolean logPending, boolean billingPending) {
        RuleExecutionLog log = new RuleExecutionLog();
        log.setTraceId("trace-replay");
        RuleDefinition definition = new RuleDefinition();
        definition.setRuleCode("replay-rule");
        RuleExecutionPersistenceOutbox row = new RuleExecutionPersistenceOutbox();
        row.setId(1L);
        row.setLogJson(com.alibaba.fastjson.JSON.toJSONString(log));
        row.setDefinitionJson(com.alibaba.fastjson.JSON.toJSONString(definition));
        row.setSuccess(1);
        row.setLogPending(logPending ? 1 : 0);
        row.setBillingPending(billingPending ? 1 : 0);
        row.setRetryCount(0);
        row.setDeliveryStatus("PENDING");
        return row;
    }

    @SuppressWarnings("unchecked")
    private static RuleExecutionPersistenceOutboxMapper replayMapper(
            RuleExecutionPersistenceOutbox row, AtomicReference<RuleExecutionPersistenceOutbox> updated) {
        return (RuleExecutionPersistenceOutboxMapper) Proxy.newProxyInstance(
                RuleExecutionPersistenceOutboxMapper.class.getClassLoader(),
                new Class<?>[]{RuleExecutionPersistenceOutboxMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) return Collections.singletonList(row);
                    if ("claimPending".equals(method.getName())) return 1;
                    if ("updateClaimed".equals(method.getName())) { updated.set((RuleExecutionPersistenceOutbox) args[0]); return 1; }
                    if (method.getReturnType().isPrimitive()) return method.getReturnType() == boolean.class ? false : 0;
                    return null;
                });
    }

    @SuppressWarnings("unchecked")
    private static RuleExecutionPersistenceOutboxMapper mapper(AtomicReference<RuleExecutionPersistenceOutbox> captured) {
        return (RuleExecutionPersistenceOutboxMapper) Proxy.newProxyInstance(
                RuleExecutionPersistenceOutboxMapper.class.getClassLoader(),
                new Class<?>[]{RuleExecutionPersistenceOutboxMapper.class},
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName())) {
                        captured.set((RuleExecutionPersistenceOutbox) args[0]);
                        return 1;
                    }
                    if (method.getReturnType().isPrimitive()) return method.getReturnType() == boolean.class ? false : 0;
                    return null;
                });
    }
}
