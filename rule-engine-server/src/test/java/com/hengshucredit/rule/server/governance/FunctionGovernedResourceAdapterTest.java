package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.publish.RuleFunctionPushService;
import com.hengshucredit.rule.server.publish.RulePushService;
import com.hengshucredit.rule.server.service.RuleFunctionService;
import com.hengshucredit.rule.server.service.RuleProjectService;
import org.junit.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FunctionGovernedResourceAdapterTest {

    public static class NativeMethods { public double choose(double amount) { return amount; } }

    @Test public void unresolvedTargetAndLegacyMappingAreVisibleWarningsNotClaimsOfExactValidation() {
        List<String> events = new ArrayList<>();
        var adapter = adapter(new RecordingStore(events), events, projectService(18L, "credit-app"));
        RuleFunction function = projectFunction(18L, "riskScore");
        function.setImplType("JAVA"); function.setImplClass("sdk.only.Methods"); function.setImplMethod("choose");
        function.setParamsJson("[{\"name\":\"amount\",\"type\":\"NUMBER\",\"javaType\":\"double\"}]");
        var missing = adapter.validate(ResourceSnapshot.ofJson(JSON.toJSONString(function)));
        assertTrue(missing.stream().anyMatch(issue -> "FUNCTION_TARGET_UNVERIFIED".equals(issue.code()) && !issue.isError()));
        function.setImplClass(NativeMethods.class.getName()); function.setParamsJson("[{\"name\":\"amount\",\"type\":\"NUMBER\"}]");
        var legacy = adapter.validate(ResourceSnapshot.ofJson(JSON.toJSONString(function)));
        assertTrue(legacy.stream().anyMatch(issue -> "FUNCTION_LEGACY_SIGNATURE".equals(issue.code()) && issue.message().contains("choose(double)")));
        assertEquals(List.of(), events);
    }

    @Test public void preflightRejectsBrokenParameterJson() {
        List<String> events = new ArrayList<>();
        var adapter = adapter(new RecordingStore(events), events, projectService(18L, "credit-app"));
        RuleFunction function = projectFunction(18L, "riskScore"); function.setParamsJson("not-json");
        var issues = adapter.validate(ResourceSnapshot.ofJson(JSON.toJSONString(function)));
        assertTrue(issues.stream().anyMatch(issue -> issue.isError() && issue.message().contains("JSON")));
        assertEquals(List.of(), events);
    }

    @Test public void preflightRejectsAnExactSignatureThatDoesNotExist() {
        List<String> events = new ArrayList<>();
        var adapter = adapter(new RecordingStore(events), events, projectService(18L, "credit-app"));
        RuleFunction function = projectFunction(18L, "riskScore");
        function.setImplType("JAVA"); function.setImplClass(NativeMethods.class.getName()); function.setImplMethod("choose");
        function.setParamsJson("[{\"name\":\"amount\",\"type\":\"NUMBER\",\"javaType\":\"long\"}]");
        var issues = adapter.validate(ResourceSnapshot.ofJson(JSON.toJSONString(function)));
        assertTrue(issues.stream().anyMatch(issue -> issue.isError() && issue.message().contains("choose(long)")));
        assertEquals(List.of(), events);
    }

    @Test public void applyRejectsBrokenParametersBeforeAnyWriteOrPush() {
        List<String> events = new ArrayList<>();
        var adapter = adapter(new RecordingStore(events), events, projectService(18L, "credit-app"));
        RuleFunction function = projectFunction(18L, "riskScore"); function.setParamsJson("[null]");
        assertThrows(IllegalArgumentException.class, () -> adapter.apply(context(31L, "UPDATE", function)));
        assertEquals(List.of(), events);
    }

    @Test public void deletingInvalidLegacyParametersStillRetiresTheFunction() {
        List<String> events = new ArrayList<>();
        var adapter = adapter(new RecordingStore(events), events, projectService(18L, "credit-app"));
        RuleFunction function = projectFunction(18L, "riskScore"); function.setParamsJson("not-json");
        adapter.apply(context(31L, "DELETE", function));
        assertEquals(List.of("UPDATE:riskScore", "PUSH:rule:push:credit-app"), events);
    }

    @Test
    public void projectCreateUpdateAndDeleteWriteBeforePushingOnlyToProjectChannel() {
        List<String> events = new ArrayList<>();
        RecordingStore store = new RecordingStore(events);
        FunctionGovernedResourceAdapter adapter = adapter(store, events,
                projectService(18L, "credit-app"));
        RuleFunction function = projectFunction(18L, "riskScore");

        adapter.apply(context(null, "CREATE", function));
        adapter.apply(context(31L, "UPDATE", function));
        adapter.apply(context(31L, "DELETE", function));

        assertEquals(List.of(
                "INSERT:riskScore", "PUSH:rule:push:credit-app",
                "UPDATE:riskScore", "PUSH:rule:push:credit-app",
                "UPDATE:riskScore", "PUSH:rule:push:credit-app"), events);
        assertEquals(Integer.valueOf(-1), store.lastUpdated.getStatus());
    }

    @Test
    public void globalFunctionBroadcastsAfterProjectionWriteWithoutProjectCode() {
        List<String> events = new ArrayList<>();
        RecordingStore store = new RecordingStore(events);
        FunctionGovernedResourceAdapter adapter = adapter(store, events,
                projectService(null, null));
        RuleFunction function = new RuleFunction();
        function.setScope(RuleFunctionService.SCOPE_GLOBAL);
        function.setProjectId(0L);
        function.setFuncCode("globalRiskScore");

        adapter.apply(context(null, "CREATE", function));

        assertEquals(List.of("INSERT:globalRiskScore",
                "PUSH:rule:push:broadcast"), events);
    }

    @Test
    public void projectFunctionWithoutAuthoritativeProjectCodeIsRejectedBeforeWriteOrPush() {
        List<String> events = new ArrayList<>();
        RecordingStore store = new RecordingStore(events);
        FunctionGovernedResourceAdapter adapter = adapter(store, events,
                projectService(18L, " "));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> adapter.apply(context(null, "CREATE",
                        projectFunction(18L, "riskScore"))));

        assertEquals("项目函数所属项目不存在: 18", exception.getMessage());
        assertEquals(List.of(), events);
    }

    private static FunctionGovernedResourceAdapter adapter(
            RecordingStore store, List<String> events,
            RuleProjectService projectService) {
        RulePushService redisPush = new RulePushService();
        ReflectionTestUtils.setField(redisPush, "stringRedisTemplate",
                new RecordingRedisTemplate(events));
        RuleFunctionPushService functionPush = new RuleFunctionPushService();
        ReflectionTestUtils.setField(functionPush, "projectService", projectService);
        ReflectionTestUtils.setField(functionPush, "pushService", redisPush);
        return new FunctionGovernedResourceAdapter(store,
                new GovernanceSecretCodec(null), functionPush, new com.hengshucredit.rule.server.service.FunctionMethodCatalogService());
    }

    private static ApprovalApplyContext context(Long resourceId,
                                                String action,
                                                RuleFunction function) {
        return new ApprovalApplyContext(1L, resourceId, 1, action,
                ResourceSnapshot.ofJson(JSON.toJSONString(function)),
                "reviewer", null);
    }

    private static RuleFunction projectFunction(Long projectId,
                                                String funcCode) {
        RuleFunction function = new RuleFunction();
        function.setScope(RuleFunctionService.SCOPE_PROJECT);
        function.setProjectId(projectId);
        function.setFuncCode(funcCode);
        function.setFuncName(funcCode);
        function.setImplType("SCRIPT");
        return function;
    }

    private static RuleProjectService projectService(Long id,
                                                     String projectCode) {
        return new RuleProjectService() {
            @Override
            public RuleProject getById(Serializable projectId) {
                if (id == null || !id.equals(projectId)) {
                    return null;
                }
                RuleProject project = new RuleProject();
                project.setId(id);
                project.setProjectCode(projectCode);
                return project;
            }
        };
    }

    private static class RecordingStore implements
            SimpleEntityGovernedResourceAdapter.EntityStore<RuleFunction> {
        private final List<String> events;
        private long nextId = 30L;
        private RuleFunction lastUpdated;

        private RecordingStore(List<String> events) {
            this.events = events;
        }

        @Override
        public RuleFunction load(Long id) {
            return null;
        }

        @Override
        public void insert(RuleFunction entity) {
            entity.setId(nextId++);
            events.add("INSERT:" + entity.getFuncCode());
        }

        @Override
        public void update(RuleFunction entity) {
            lastUpdated = entity;
            events.add("UPDATE:" + entity.getFuncCode());
        }
    }

    private static class RecordingRedisTemplate extends StringRedisTemplate {
        private final List<String> events;

        private RecordingRedisTemplate(List<String> events) {
            this.events = events;
        }

        @Override
        public Long convertAndSend(String channel, Object message) {
            events.add("PUSH:" + channel);
            return 1L;
        }
    }
}
