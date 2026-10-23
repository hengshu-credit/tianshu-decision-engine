package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.dto.RulePushMessage;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
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

public class FunctionApprovalApplyServiceTest {

    @Test
    public void approvalApplyWritesThenPushesProjectFunctionForCreateUpdateAndDelete() {
        assertProjectAction("CREATE", 0L, "INSERT:riskScore", "FUNC_UPDATE");
        assertProjectAction("UPDATE", 31L, "UPDATE:riskScore", "FUNC_UPDATE");
        assertProjectAction("DISABLE", 31L, "UPDATE:riskScore", "FUNC_DELETE");
        assertProjectAction("DELETE", 31L, "UPDATE:riskScore", "FUNC_DELETE");
    }

    @Test
    public void approvalApplyBroadcastsGlobalFunctionAfterWritingProjection() {
        EventRecorder recorder = new EventRecorder();
        ApprovalService service = service(recorder, null, null,
                "CREATE", 0L, globalFunction());

        service.approve(9L, null, "reviewer");

        assertEquals(List.of("INSERT:globalRiskScore",
                "PUSH:rule:push:broadcast", "APPROVE"), recorder.events);
        assertPush(recorder, "rule:push:broadcast", "FUNC_UPDATE",
                "globalRiskScore", "GLOBAL", null);
    }

    @Test
    public void approvalApplyRejectsEmptyProjectCodeBeforeWritePushOrApprovalEvent() {
        assertInvalidProjectAction("CREATE", 0L, 18L, " ");
        assertInvalidProjectAction("UPDATE", 31L, 18L, " ");
        assertInvalidProjectAction("DELETE", 31L, 18L, " ");
    }

    @Test
    public void approvalApplyRejectsMissingProjectBeforeWritePushOrApprovalEvent() {
        assertInvalidProjectAction("CREATE", 0L, 19L, null);
        assertInvalidProjectAction("UPDATE", 31L, 19L, null);
        assertInvalidProjectAction("DELETE", 31L, 19L, null);
    }

    private static void assertProjectAction(String action, Long resourceId,
                                            String writeEvent,
                                            String pushAction) {
        EventRecorder recorder = new EventRecorder();
        ApprovalService service = service(recorder, 18L, "credit-app",
                action, resourceId, projectFunction());

        service.approve(9L, null, "reviewer");

        assertEquals(List.of(writeEvent, "PUSH:rule:push:credit-app",
                "APPROVE"), recorder.events);
        assertPush(recorder, "rule:push:credit-app", pushAction,
                "riskScore", "PROJECT", "credit-app");
    }

    private static void assertInvalidProjectAction(String action,
                                                   Long resourceId,
                                                   Long projectId,
                                                   String projectCode) {
        EventRecorder recorder = new EventRecorder();
        RuleFunction function = projectFunction();
        function.setProjectId(projectId);
        ApprovalService service = service(recorder,
                projectCode == null ? null : projectId, projectCode, action,
                resourceId, function);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.approve(9L, null, "reviewer"));

        assertEquals("项目函数所属项目不存在: " + projectId,
                exception.getMessage());
        assertEquals(List.of(), recorder.events);
        assertEquals(List.of(), recorder.messages);
    }

    private static void assertPush(EventRecorder recorder, String channel,
                                   String action, String funcCode,
                                   String scope, String projectCode) {
        assertEquals(1, recorder.messages.size());
        PushRecord push = recorder.messages.get(0);
        assertEquals(channel, push.channel);
        assertEquals(action, push.message.getAction());
        assertEquals(funcCode, push.message.getFuncCode());
        assertEquals(scope, push.message.getScope());
        assertEquals(projectCode, push.message.getProjectCode());
    }

    private static ApprovalService service(EventRecorder recorder,
                                           Long projectId,
                                           String projectCode,
                                           String action,
                                           Long resourceId,
                                           RuleFunction function) {
        FunctionGovernedResourceAdapter adapter = adapter(recorder,
                projectId, projectCode);
        return new ApprovalService(adapter, recorder.events, action, resourceId,
                function);
    }

    private static FunctionGovernedResourceAdapter adapter(
            EventRecorder recorder, Long projectId, String projectCode) {
        RulePushService redisPush = new RulePushService();
        ReflectionTestUtils.setField(redisPush, "stringRedisTemplate",
                new RecordingRedisTemplate(recorder));
        RuleFunctionPushService functionPush = new RuleFunctionPushService();
        ReflectionTestUtils.setField(functionPush, "projectService",
                projectService(projectId, projectCode));
        ReflectionTestUtils.setField(functionPush, "pushService", redisPush);
        return new FunctionGovernedResourceAdapter(
                new RecordingStore(recorder), new GovernanceSecretCodec(null),
                functionPush, new com.hengshucredit.rule.server.service.FunctionMethodCatalogService());
    }

    private static RuleProjectService projectService(Long id,
                                                     String projectCode) {
        return new RuleProjectService() {
            @Override
            public RuleProject getById(Serializable projectId) {
                if (id == null || !id.equals(projectId)) return null;
                RuleProject project = new RuleProject();
                project.setId(id);
                project.setProjectCode(projectCode);
                return project;
            }
        };
    }

    private static RuleFunction projectFunction() {
        RuleFunction function = new RuleFunction();
        function.setScope(RuleFunctionService.SCOPE_PROJECT);
        function.setProjectId(18L);
        function.setFuncCode("riskScore");
        function.setFuncName("riskScore");
        function.setImplType("SCRIPT");
        function.setImplScript("return 1;");
        return function;
    }

    private static RuleFunction globalFunction() {
        RuleFunction function = new RuleFunction();
        function.setScope(RuleFunctionService.SCOPE_GLOBAL);
        function.setProjectId(0L);
        function.setFuncCode("globalRiskScore");
        function.setFuncName("globalRiskScore");
        function.setImplType("SCRIPT");
        function.setImplScript("return 1;");
        return function;
    }

    private static class ApprovalService extends GovernanceApprovalService {
        private final GovernedResourceAdapter adapter;
        private final List<String> events;
        private final GovernanceApprovalRequest request;
        private final GovernedResource resource;

        private ApprovalService(GovernedResourceAdapter adapter,
                                List<String> events, String action,
                                Long resourceId, RuleFunction function) {
            this.adapter = adapter;
            this.events = events;
            request = new GovernanceApprovalRequest();
            request.setId(9L);
            request.setResourceType(GovernanceResourceTypes.FUNCTION);
            request.setResourceId(resourceId);
            request.setProjectId(function.getProjectId());
            request.setAction(action);
            request.setStatus("PENDING");
            request.setBaseVersionId("CREATE".equals(action) ? null : 4L);
            request.setSubmittedSnapshotJson(JSON.toJSONString(function));
            request.setSnapshotDigest("digest");
            request.setDependencyDigest("dep");
            resource = "CREATE".equals(action) ? null : resource(resourceId);
        }

        @Override
        protected GovernanceApprovalRequest requireRequest(Long requestId) {
            return request;
        }

        @Override
        protected ResourceSnapshot normalize(GovernanceApprovalRequest value,
                                             String snapshotJson) {
            return adapter.normalizeDraft(ResourceSnapshot.ofJson(snapshotJson));
        }

        @Override
        protected GovernancePreflightReport evaluate(
                GovernanceApprovalRequest value, ResourceSnapshot snapshot) {
            return new GovernancePreflightReport(true, List.of(), List.of(),
                    List.of(), "dep");
        }

        @Override
        protected List<GovernancePreflightReport.ResolvedDependency>
        loadDependencies(Long requestId) { return List.of(); }

        @Override
        protected GovernancePreflightReport revalidateDependencies(
                List<GovernancePreflightReport.ResolvedDependency> submitted) {
            return new GovernancePreflightReport(true, List.of(), List.of(),
                    submitted, "dep");
        }

        @Override
        protected GovernedResource findResource(String type, Long id) {
            return resource;
        }

        @Override
        protected void insertResource(GovernedResource value) { value.setId(19L); }

        @Override
        protected void persistResource(GovernedResource value) { }

        @Override
        protected GovernedResourceVersion persistVersion(
                GovernedResourceVersion value) { value.setId(25L); return value; }

        @Override
        protected void persistRequest(GovernanceApprovalRequest value) { }

        @Override
        protected void persistDependencies(GovernanceApprovalRequest value,
                                           GovernancePreflightReport report,
                                           Long versionId) { }

        @Override
        protected void bindDependencyVersion(Long requestId, Long versionId) { }

        @Override
        protected void appendEvent(GovernanceApprovalRequest value,
                                   String action, String fromStatus,
                                   String actor, String comment,
                                   String detailsJson) {
            events.add(action);
        }

        @Override
        protected GovernedResourceAdapter requireAdapter(String type) {
            return adapter;
        }

        private static GovernedResource resource(Long resourceId) {
            GovernedResource value = new GovernedResource();
            value.setId(15L);
            value.setResourceType(GovernanceResourceTypes.FUNCTION);
            value.setResourceId(resourceId);
            value.setEffectiveVersionId(4L);
            value.setEffectiveVersionNo(1);
            return value;
        }
    }

    private static class EventRecorder {
        private final List<String> events = new ArrayList<>();
        private final List<PushRecord> messages = new ArrayList<>();
    }

    private static class PushRecord {
        private final String channel;
        private final RulePushMessage message;

        private PushRecord(String channel, RulePushMessage message) {
            this.channel = channel;
            this.message = message;
        }
    }

    private static class RecordingStore implements
            SimpleEntityGovernedResourceAdapter.EntityStore<RuleFunction> {
        private final EventRecorder recorder;
        private RuleFunction stored;

        private RecordingStore(EventRecorder recorder) { this.recorder = recorder; }

        @Override public RuleFunction load(Long id) { return stored != null && id.equals(stored.getId()) ? stored : null; }

        @Override public void insert(RuleFunction entity) {
            entity.setId(31L);
            stored = entity;
            recorder.events.add("INSERT:" + entity.getFuncCode());
        }

        @Override public void update(RuleFunction entity) {
            stored = entity;
            recorder.events.add("UPDATE:" + entity.getFuncCode());
        }
    }

    private static class RecordingRedisTemplate extends StringRedisTemplate {
        private final EventRecorder recorder;
        private RecordingRedisTemplate(EventRecorder recorder) { this.recorder = recorder; }
        @Override public Long convertAndSend(String channel, Object message) {
            recorder.events.add("PUSH:" + channel);
            recorder.messages.add(new PushRecord(channel,
                    JSON.parseObject(String.valueOf(message),
                            RulePushMessage.class)));
            return 1L;
        }
    }
}
