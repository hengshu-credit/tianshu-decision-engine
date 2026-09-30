package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ExternalApiConsumerServiceTest {
    private RuleExternalApiConfig api() {
        RuleExternalApiConfig value = new RuleExternalApiConfig(); value.setId(9L); value.setStatus(1);
        value.setExecutionConfig("{\"version\":2,\"requestFields\":[{\"id\":\"phone\",\"location\":\"JSON\",\"path\":\"phone\",\"required\":true,\"overridable\":true,\"value\":{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":1}}]}");
        return value;
    }
    private ExternalApiConsumerService service(AtomicInteger calls, boolean fail) {
        ExternalApiConsumerService service = new ExternalApiConsumerService();
        RuleExternalApiConfig api = api();
        ReflectionTestUtils.setField(service, "apiConfigMapper", Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{RuleExternalApiConfigMapper.class}, (p, m, a) -> api));
        ReflectionTestUtils.setField(service, "invokeService", new ExternalApiInvokeService() {
            @Override Map<String, Object> invokePlanned(ExternalApiRequestPlan plan) {
                calls.incrementAndGet(); if (fail) throw new IllegalStateException("provider failure");
                return Map.of("body", Map.of("phone", plan.values().get("phone")));
            }
        });
        return service;
    }
    private VariableResolveOptions options() {
        var options = VariableResolveOptions.defaults(); options.setDerivedReferencePaths(Map.of("VARIABLE:1", "applicant", "VARIABLE:2", "contact"));
        options.setInvocationCache(new VariableResolutionInvocationCache()); return options;
    }
    @Test public void variablesObjectsAndChildrenShareSingleFlightRegardlessOfTheirResultPath() {
        AtomicInteger calls = new AtomicInteger(); var service = service(calls, false); var options = options();
        var first = CompletableFuture.supplyAsync(() -> service.resolve(1L, 9L, Map.of("resultPath", "body"), Map.of("applicant", "111"), options));
        var second = CompletableFuture.supplyAsync(() -> service.resolve(1L, 9L, Map.of("resultPath", "body.phone"), Map.of("applicant", "111", "unrelated", 3), options));
        assertEquals(first.join(), second.join()); assertEquals(1, calls.get());
        service.resolve(1L, 9L, Map.of(), Map.of("applicant", "111"), options()); assertEquals(2, calls.get());
    }
    @Test public void changedOverridableInputsAreIndependentButEachRunsOnce() {
        AtomicInteger calls = new AtomicInteger(); var service = service(calls, false); var options = options();
        Map<String, Object> input = Map.of("applicant", "111", "contact", "222");
        Map<String, Object> binding = Map.of("requestOverrides", Map.of("phone", Map.of("kind", "REFERENCE", "refType", "VARIABLE", "refId", 2)));
        assertEquals("111", ExternalApiConsumerService.select(service.resolve(1L, 9L, Map.of(), input, options), "body.phone"));
        assertEquals("222", ExternalApiConsumerService.select(service.resolve(1L, 9L, binding, input, options), "body.phone"));
        service.resolve(1L, 9L, binding, input, options); assertEquals(2, calls.get());
    }
    @Test public void failuresAreRememberedAndRequiredValidationNeverCallsProvider() {
        AtomicInteger calls = new AtomicInteger(); var service = service(calls, true); var options = options();
        assertThrows(IllegalArgumentException.class, () -> service.resolve(1L, 9L, Map.of(), Map.of(), options)); assertEquals(0, calls.get());
        for (int i = 0; i < 3; i++) assertThrows(IllegalStateException.class, () -> service.resolve(1L, 9L, Map.of(), Map.of("applicant", "111"), options));
        assertEquals(1, calls.get());
    }
    @Test public void offlineReplayDoesNotCallProvider() {
        AtomicInteger calls = new AtomicInteger(); var service = service(calls, false); var options = options(); options.setOfflineReplay(true);
        service.resolve(1L, 9L, Map.of(), Map.of("applicant", "111"), options); assertEquals(0, calls.get());
    }

    @Test public void pendingSourceUsesPollOnlyResumePath() {
        AtomicInteger normalCalls = new AtomicInteger();
        AtomicInteger resumedCalls = new AtomicInteger();
        ExternalApiConsumerService service = service(normalCalls, false);
        ReflectionTestUtils.setField(service, "invokeService", new ExternalApiInvokeService() {
            @Override Map<String, Object> invokePlanned(ExternalApiRequestPlan plan) {
                normalCalls.incrementAndGet();
                return Map.of("body", Map.of("phone", "normal"));
            }

            @Override Map<String, Object> invokePending(ExternalApiRequestPlan plan, Map<String, Object> pending) {
                resumedCalls.incrementAndGet();
                assertEquals("task-9", pending.get("taskId"));
                return Map.of("body", Map.of("phone", "resumed"));
            }
        });
        VariableResolveOptions options = options();
        String requestIdentity = ExternalApiRequestPlan.prepare(api(), Map.of("applicant", "111"), Map.of(),
                Map.of("VARIABLE:1", "applicant")).identity();
        options.getInvocationCache().completeStep(new VariableResolutionInvocationCache.SourceStep(
                requestIdentity, "EXTERNAL_API", "cfg", "input", "deps", "WAITING_EXTERNAL", null, null,
                Map.of(), Map.of("taskId", "task-9", "apiConfigId", 9L)));
        Map<String, Object> response = service.resolve(1L, 9L, Map.of(), Map.of("applicant", "111"), options,
                "VARIABLE:7");
        assertEquals("resumed", ExternalApiConsumerService.select(response, "body.phone"));
        assertEquals(0, normalCalls.get());
        assertEquals(1, resumedCalls.get());
    }

    @Test public void changedRequestIdentityCannotResumeAnotherPendingTask() {
        AtomicInteger normalCalls = new AtomicInteger();
        AtomicInteger resumedCalls = new AtomicInteger();
        ExternalApiConsumerService service = service(normalCalls, false);
        ReflectionTestUtils.setField(service, "invokeService", new ExternalApiInvokeService() {
            @Override Map<String, Object> invokePlanned(ExternalApiRequestPlan plan) {
                normalCalls.incrementAndGet();
                return Map.of("body", Map.of("phone", "new"));
            }

            @Override Map<String, Object> invokePending(ExternalApiRequestPlan plan, Map<String, Object> pending) {
                resumedCalls.incrementAndGet();
                return Map.of("body", Map.of("phone", "wrong-order"));
            }
        });
        VariableResolveOptions options = options();
        String originalIdentity = ExternalApiRequestPlan.prepare(api(), Map.of("applicant", "111"), Map.of(),
                Map.of("VARIABLE:1", "applicant")).identity();
        options.getInvocationCache().completeStep(new VariableResolutionInvocationCache.SourceStep(
                originalIdentity, "EXTERNAL_API", "cfg", "input", "deps", "WAITING_EXTERNAL", null, null,
                Map.of(), Map.of("taskId", "task-9", "apiConfigId", 9L)));
        Map<String, Object> response = service.resolve(1L, 9L, Map.of(), Map.of("applicant", "222"), options,
                "VARIABLE:7");
        assertEquals("new", ExternalApiConsumerService.select(response, "body.phone"));
        assertEquals(1, normalCalls.get());
        assertEquals(0, resumedCalls.get());
    }
}
