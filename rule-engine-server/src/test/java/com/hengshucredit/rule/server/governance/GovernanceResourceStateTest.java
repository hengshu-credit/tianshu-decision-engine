package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.*;
import com.hengshucredit.rule.server.security.ConsoleAccessGuard;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class GovernanceResourceStateTest {
    @Test public void ruleDraftAndApprovedRevisionAreSeparatePublicationCandidatesInOneQuery() {
        var service = new GovernanceApprovalService();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "rule-state-test"), RuleRevision.class);
        ReflectionTestUtils.setField(service, "resourceMapper", Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{GovernedResourceMapper.class}, (p, method, args) -> List.of()));
        ReflectionTestUtils.setField(service, "requestMapper", Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{GovernanceApprovalRequestMapper.class}, (p, method, args) -> List.of()));
        var draft = new RuleRevision(); draft.setDefinitionId(7L); draft.setState("DRAFT");
        var approved = new RuleRevision(); approved.setDefinitionId(8L); approved.setState("APPROVED");
        var published = new RuleRevision(); published.setDefinitionId(9L); published.setState("PUBLISHED");
        AtomicInteger reads = new AtomicInteger();
        ReflectionTestUtils.setField(service, "ruleRevisionMapper", Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{RuleRevisionMapper.class}, (p, method, args) -> {
            assertEquals("selectList", method.getName()); reads.incrementAndGet();
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) args[0];
            query.getSqlSegment();
            var params = query.getParamNameValuePairs().values();
            assertTrue(params.contains("DRAFT")); assertTrue(params.contains("APPROVED")); assertFalse(params.contains("PUBLISHED"));
            return List.of(draft, approved, published).stream().filter(revision -> params.contains(revision.getState())).toList();
        }));
        var states = service.resourceStates("RULE", List.of(7L, 8L, 9L), "owner");
        assertTrue(states.get(0).hasRuleDraft()); assertFalse(states.get(0).hasApprovedRuleRevision());
        assertFalse(states.get(1).hasRuleDraft()); assertTrue(states.get(1).hasApprovedRuleRevision());
        assertFalse(states.get(2).hasRuleDraft()); assertFalse(states.get(2).hasApprovedRuleRevision());
        assertEquals(1, reads.get());
    }

    @Test public void productionVersionAndPendingChangeRemainSeparateAndReadsAreBatched() {
        var service = new GovernanceApprovalService();
        var resource = new GovernedResource();
        resource.setResourceId(7L); resource.setEffectiveStatus("ACTIVE"); resource.setEffectiveVersionId(90L);
        var request = new GovernanceApprovalRequest();
        request.setResourceId(7L); request.setId(100L); request.setStatus("PENDING"); request.setAction("UPDATE"); request.setApplicant("owner");
        AtomicInteger calls = new AtomicInteger();
        ReflectionTestUtils.setField(service, "accessGuard", new ConsoleAccessGuard(null, null) {
            @Override public void requireResource(String type, String action) { assertEquals("MODEL", type); assertEquals("view", action); }
        });
        ReflectionTestUtils.setField(service, "resourceMapper", Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{GovernedResourceMapper.class}, (p, method, args) -> {
            assertEquals("selectList", method.getName()); calls.incrementAndGet(); return List.of(resource);
        }));
        ReflectionTestUtils.setField(service, "requestMapper", Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{GovernanceApprovalRequestMapper.class}, (p, method, args) -> {
            assertEquals("selectLatestResourceRequests", method.getName()); assertEquals("MODEL", args[0]); assertEquals(List.of(7L, 8L), args[1]);
            calls.incrementAndGet(); return List.of(request);
        }));
        var states = service.resourceStates("MODEL", List.of(7L, 8L, 7L), "owner");
        assertEquals(2, states.size()); assertEquals(2, calls.get());
        assertEquals("ACTIVE", states.get(0).effectiveStatus()); assertTrue(states.get(0).hasEffectiveVersion());
        assertEquals("PENDING", states.get(0).requestStatus()); assertTrue(states.get(0).owned());
        assertEquals(Long.valueOf(100), states.get(0).requestId());
        assertNull(states.get(1).effectiveStatus()); assertNull(states.get(1).requestId());
        assertFalse(service.resourceStates("MODEL", List.of(7L, 8L), "another-user").get(0).owned());
    }

    @Test public void invalidAndOversizedBatchesAreRejectedBeforeQuerying() {
        var service = new GovernanceApprovalService();
        assertThrows(IllegalArgumentException.class, () -> service.resourceStates("MODEL", List.of(0L), "owner"));
        assertThrows(IllegalArgumentException.class, () -> service.resourceStates("MODEL", java.util.Collections.nCopies(101, 1L), "owner"));
        assertTrue(service.resourceStates("MODEL", List.of(), "owner").isEmpty());
    }

    @Test public void batchLookupCannotBypassModuleViewPermission() {
        var service = new GovernanceApprovalService();
        ReflectionTestUtils.setField(service, "accessGuard", new ConsoleAccessGuard(null, null) {
            @Override public void requireResource(String type, String action) { throw new SecurityException("denied"); }
        });
        assertThrows(SecurityException.class, () -> service.resourceStates("MODEL", List.of(7L), "reader"));
    }
}
