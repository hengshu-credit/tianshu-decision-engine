package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.mapper.RuleExecutionLogMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class RuleExecutionLogServiceTest {
    @Test
    public void emptyTraceDoesNotReuseAnExternalPhysicalRowId() {
        RuleExecutionLogService service = new RuleExecutionLogService() {
            @Override public boolean save(RuleExecutionLog value) {
                assertEquals(null, value.getId());
                return true;
            }
        };
        RuleExecutionLog value = new RuleExecutionLog(); value.setId(99L); value.setTraceId("");
        service.saveLogical(value);
    }

    @Before
    public void initializeLogMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RuleExecutionLog.class);
    }

    @Test
    public void batchUsesConsistentTraceLockOrderWithoutChangingTraceValues() {
        List<String> writes = new java.util.ArrayList<>();
        RuleExecutionLogService service = new RuleExecutionLogService() {
            @Override public void saveLogical(RuleExecutionLog log) { writes.add(log.getTraceId()); }
        };
        ReflectionTestUtils.setField(service, "baseMapper", writeMapper(null, new AtomicInteger()));
        List<RuleExecutionLog> logs = new java.util.ArrayList<>();
        for (String trace : List.of("b", "A", "a", "B")) {
            RuleExecutionLog log = new RuleExecutionLog();
            log.setTraceId(trace);
            logs.add(log);
        }
        service.saveLogicalBatch(logs);
        assertEquals(List.of("A", "a", "b", "B"), writes);
        assertEquals("调用方原始批次顺序不应被原地修改", "b", logs.get(0).getTraceId());
    }

    @Test
    public void rejectsUnconfirmedInsertWithOrWithoutTraceIdentity() {
        for (String trace : Arrays.asList(null, "trace-new")) {
            RuleExecutionLogService service = new RuleExecutionLogService();
            ReflectionTestUtils.setField(service, "baseMapper", writeMapper(null, new AtomicInteger()));
            ReflectionTestUtils.setField(service, "entityClass", RuleExecutionLog.class);
            RuleExecutionLog log = new RuleExecutionLog();
            log.setTraceId(trace);
            log.setRuleCode("risk-rule");
            assertThrows("insert返回0不能当成日志已持久化", IllegalStateException.class,
                    () -> service.saveLogical(log));
        }
    }

    @Test
    public void rejectsUnconfirmedLogicalLogUpdate() {
        RuleExecutionLog existing = new RuleExecutionLog();
        existing.setId(7L);
        existing.setTraceId("trace-update");
        existing.setAttemptNo(1);
        existing.setCreateTime(java.time.LocalDateTime.of(2026, 10, 8, 12, 0));
        RuleExecutionLogService service = new RuleExecutionLogService();
        AtomicInteger writes = new AtomicInteger();
        ReflectionTestUtils.setField(service, "baseMapper", writeMapper(existing, writes));
        ReflectionTestUtils.setField(service, "entityClass", RuleExecutionLog.class);
        RuleExecutionLog next = new RuleExecutionLog();
        next.setTraceId(existing.getTraceId());
        next.setAttemptNo(2);
        next.setSuccess(1);

        IllegalStateException failure = assertThrows("update返回0不能确认新的执行结果", IllegalStateException.class,
                () -> service.saveLogical(next));
        assertEquals("执行日志更新未生效", failure.getMessage());
        assertEquals(1, writes.get());
    }

    @Test
    public void persistedNewerAttemptRemainsALegitimateNoOp() {
        RuleExecutionLog existing = new RuleExecutionLog();
        existing.setId(7L);
        existing.setTraceId("trace-newer");
        existing.setAttemptNo(3);
        existing.setCreateTime(java.time.LocalDateTime.of(2026, 10, 8, 12, 0));
        RuleExecutionLogService service = new RuleExecutionLogService();
        AtomicInteger writes = new AtomicInteger();
        ReflectionTestUtils.setField(service, "baseMapper", writeMapper(existing, writes));
        ReflectionTestUtils.setField(service, "entityClass", RuleExecutionLog.class);
        RuleExecutionLog older = new RuleExecutionLog();
        older.setTraceId(existing.getTraceId());
        older.setAttemptNo(2);

        service.saveLogical(older);

        assertEquals("旧尝试重放不应覆盖数据库中更高尝试的结果", 0, writes.get());
    }

    private static RuleExecutionLogMapper writeMapper(RuleExecutionLog existing, AtomicInteger writes) {
        return (RuleExecutionLogMapper) Proxy.newProxyInstance(RuleExecutionLogMapper.class.getClassLoader(),
                new Class<?>[]{RuleExecutionLogMapper.class}, (proxy, method, args) -> {
                    if ("selectOne".equals(method.getName())) return existing;
                    if ("lockLogicalIdentity".equals(method.getName())) return 1;
                    if ("selectLogicalIdentity".equals(method.getName())) {
                        return new RuleExecutionLogMapper.LogicalIdentity((String) args[0],
                                existing == null ? null : existing.getId(), existing == null ? null : existing.getCreateTime());
                    }
                    if ("selectLegacyLogicalRecord".equals(method.getName()) || "selectLogicalRecord".equals(method.getName())) return existing;
                    if ("insert".equals(method.getName()) || "updateById".equals(method.getName()) || "update".equals(method.getName())) {
                        writes.incrementAndGet();
                        return 0;
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType().isPrimitive()) return 0;
                    return null;
                });
    }

    @Test
    public void keepsClientArtifactAttributionWhenAsyncReportArrivesAfterNewPublish() {
        RuleExecutionLogService service = new RuleExecutionLogService() {
            @Override
            protected RulePublished findPublished(String ruleCode, String projectCode) {
                RulePublished current = new RulePublished();
                current.setVersion(8);
                current.setRevisionId(88L);
                current.setArtifactDigest("current-digest");
                return current;
            }
        };
        RuleExecutionLog log = new RuleExecutionLog();
        log.setRuleCode("risk_rule");
        log.setRuleVersion(7);
        log.setRevisionId(77L);
        log.setArtifactDigest("executed-digest");

        service.applyPublishedAttribution(log, "project-a");

        assertEquals(Integer.valueOf(7), log.getRuleVersion());
        assertEquals(Long.valueOf(77L), log.getRevisionId());
        assertEquals("executed-digest", log.getArtifactDigest());
    }


}
