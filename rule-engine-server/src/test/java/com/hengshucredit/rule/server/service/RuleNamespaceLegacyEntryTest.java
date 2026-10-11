package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveResponse;
import com.hengshucredit.rule.model.dto.RuleDraftSourceRequest;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.model.enums.RuleDraftSourceType;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class RuleNamespaceLegacyEntryTest {
    @Test public void legacyCompileMustRejectForeignWorkspaceBeforeReadingLiveCatalogOrWritingContent() {
        RuleDefinition definition = definition();
        RuleDefinitionContent content = content();
        AtomicInteger liveReads = new AtomicInteger(), writes = new AtomicInteger();
        boolean[] rejectNamespace = {true};
        RuleCompileService service = new RuleCompileService() {
            @Override protected void requireLocalWorkspace(RuleDefinition actualDefinition, RuleDefinitionContent actualContent) {
                assertSame(definition, actualDefinition); assertSame(content, actualContent);
                if (rejectNamespace[0]) throw migrationRequired();
            }
        };
        ReflectionTestUtils.setField(service, "definitionService", definitions(definition, content));
        ReflectionTestUtils.setField(service, "ruleCallCycleService", new RuleCallCycleService() {
            @Override public String validateNoCycle(Long id, String modelJson) { return null; }
        });
        ReflectionTestUtils.setField(service, "variableService", new RuleVariableService() {
            @Override public Map<String, String> buildRefScriptNameMap(Long projectId) { liveReads.incrementAndGet(); return Map.of("VARIABLE:7", "Current_B"); }
            @Override public Map<Long, String> buildRefConstantExpressionMap(Long projectId) { liveReads.incrementAndGet(); return Map.of(); }
        });
        ReflectionTestUtils.setField(service, "functionService", new RuleFunctionService() {
            @Override public Map<Long, String> buildFunctionCodeMap(Long projectId) { liveReads.incrementAndGet(); return Map.of(); }
            @Override public Map<Long, Integer> buildFunctionArityMap(Long projectId) { liveReads.incrementAndGet(); return Map.of(); }
            @Override public java.util.Set<Long> buildNativeFunctionIds(Long projectId) { liveReads.incrementAndGet(); return java.util.Set.of(); }
        });
        ReflectionTestUtils.setField(service, "contentMapper", mapper(writes));
        assertEquals("REFERENCE_NAMESPACE_MIGRATION_REQUIRED", assertThrows(RuleGovernanceException.class,
                () -> service.compile(30L)).getCode());
        assertEquals(0, liveReads.get()); assertEquals(0, writes.get());
        assertEquals("frozen-script", content.getCompiledScript());
        rejectNamespace[0] = false;
        assertTrue(service.compile(30L).isSuccess());
        assertEquals(5, liveReads.get()); assertEquals(1, writes.get());
        assertEquals("return 1;", content.getCompiledScript());
    }

    @Test public void restoringForeignVersionMustUseSelectedVersionProvenanceInsteadOfCurrentDraftBase() {
        RuleDefinition definition = definition(); RuleDefinitionContent content = content();
        RuleDefinitionVersion version = new RuleDefinitionVersion(); version.setId(70L); version.setDefinitionId(30L);
        version.setVersion(2); version.setModelJson("{\"_varId\":7}");
        RuleDefinitionService service = new RuleDefinitionService() {
            @Override public RuleDefinition getById(Serializable id) { return definition; }
            @Override public RuleDefinitionContent getContent(Long id) { return content; }
            @Override public RuleDefinitionVersion getVersion(Long id, Integer businessVersion) { return version; }
        };
        AtomicInteger defaultDrafts = new AtomicInteger(), saves = new AtomicInteger(), writes = new AtomicInteger();
        ReflectionTestUtils.setField(service, "lifecycleService", new RuleLifecycleService() {
            @Override public RuleRevision createDraft(Long id, Long baseRevisionId) {
                defaultDrafts.incrementAndGet(); RuleRevision draft = new RuleRevision(); draft.setId(8L); draft.setDefinitionId(id); draft.setLockVersion(0); return draft;
            }
            @Override public RuleDraftSaveResponse createDraftFromSource(Long id, RuleDraftSourceRequest request) {
                assertEquals(Long.valueOf(30), id); assertEquals(RuleDraftSourceType.VERSION, request.getSourceType());
                assertEquals(Long.valueOf(70), request.getSourceId());
                throw migrationRequired();
            }
        });
        ReflectionTestUtils.setField(service, "ruleDraftService", new RuleDraftService() {
            @Override public RuleDraftSaveResponse save(RuleDraftSaveRequest request) { saves.incrementAndGet(); return new RuleDraftSaveResponse(); }
        });
        ReflectionTestUtils.setField(service, "contentMapper", mapper(writes));
        assertEquals("REFERENCE_NAMESPACE_MIGRATION_REQUIRED", assertThrows(RuleGovernanceException.class,
                () -> service.rollbackToVersion(30L, 2)).getCode());
        assertEquals(0, defaultDrafts.get()); assertEquals(0, saves.get()); assertEquals(0, writes.get());
    }

    private RuleDefinitionService definitions(RuleDefinition definition, RuleDefinitionContent content) {
        return new RuleDefinitionService() {
            @Override public RuleDefinition getById(Serializable id) { return definition; }
            @Override public RuleDefinitionContent getContent(Long id) { return content; }
        };
    }
    private RuleDefinition definition() {
        RuleDefinition definition = new RuleDefinition(); definition.setId(30L); definition.setProjectId(1L); definition.setModelType("SCRIPT"); return definition;
    }
    private RuleDefinitionContent content() {
        RuleDefinitionContent content = new RuleDefinitionContent(); content.setId(3L); content.setDefinitionId(30L);
        content.setModelJson("{\"script\":\"return 1;\"}"); content.setCompiledScript("frozen-script"); return content;
    }
    private static RuleGovernanceException migrationRequired() {
        return new RuleGovernanceException(409, "REFERENCE_NAMESPACE_MIGRATION_REQUIRED", "请使用离线配置迁移", List.of());
    }
    private RuleDefinitionContentMapper mapper(AtomicInteger writes) {
        return (RuleDefinitionContentMapper) Proxy.newProxyInstance(RuleDefinitionContentMapper.class.getClassLoader(), new Class<?>[]{RuleDefinitionContentMapper.class},
                (proxy, method, args) -> {
                    if ("updateById".equals(method.getName())) { writes.incrementAndGet(); return 1; }
                    if ("selectList".equals(method.getName())) return List.of();
                    return null;
                });
    }
}
