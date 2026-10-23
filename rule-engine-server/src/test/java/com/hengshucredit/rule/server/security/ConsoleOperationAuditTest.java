package com.hengshucredit.rule.server.security;

import com.hengshucredit.rule.model.ApiResponse;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hengshucredit.rule.model.entity.ConsoleOperationLog;
import com.hengshucredit.rule.server.audit.*;
import com.hengshucredit.rule.server.auth.TrustedClientAddressResolver;
import com.hengshucredit.rule.server.common.GlobalExceptionHandler;
import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import org.junit.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class ConsoleOperationAuditTest {
    @Test public void pageVisitsResolveRuleIdentityAndDistinguishDetailFromDesigner() throws Exception {
        for (String[] sample : new String[][]{{"/rule/12", "规则详情页"}, {"/designer/table/12?sourceType=REVISION&sourceId=99&token=PRIVATE", "决策表设计页"}}) {
            var logs = new Logs();
            mvc(new NamedRuleMetadata(), logs, true).perform(post("/api/rule/console/activity").session(session())
                    .contentType(MediaType.APPLICATION_JSON).content(JSON.toJSONString(Map.of("action", "PAGE_VIEW", "pagePath", sample[0], "name", "规则详情"))))
                    .andExpect(status().isOk());
            Assert.assertEquals("RULE", logs.saved.getTargetType()); Assert.assertEquals("12", logs.saved.getTargetId());
            Assert.assertEquals("授信准入", logs.saved.getTargetName());
            Assert.assertTrue(logs.saved.getChangeSummary().contains("授信准入"));
            Assert.assertTrue(logs.saved.getChangeSummary().contains(sample[1]));
            Assert.assertFalse(logs.saved.getPagePath().contains("PRIVATE"));
            if (sample[0].contains("sourceId")) Assert.assertTrue(logs.saved.getPagePath().contains("sourceId=99"));
            assertMetadataOnly(logs.saved);
        }
    }

    @Test public void publishAndDebugHaveSpecificActionsAndKeepRuleAndRevisionLocation() throws Exception {
        var logs = new Logs();
        var mvc = mvc(new NamedRuleMetadata(), logs, true);
        mvc.perform(post("/api/rule/definition/12/designer/publish").session(session())
                .header("X-Console-Page", "/designer/table/12?sourceType=REVISION&sourceId=99")
                .contentType(MediaType.APPLICATION_JSON).content("{\"revisionId\":99}")).andExpect(status().isOk());
        Assert.assertEquals("PUBLISH", logs.saved.getAction());
        Assert.assertEquals("12", logs.saved.getTargetId());
        Assert.assertTrue(logs.saved.getChangeSummary().contains("发布审批"));
        Assert.assertTrue(logs.saved.getChangeSummary().contains("授信准入"));
        Assert.assertEquals("修订 4", logs.saved.getTargetVersion());
        mvc.perform(post("/api/rule/definition/12/api-scenarios/execute-validated").session(session())
                .header("X-Console-Page", "/test?definitionId=12")).andExpect(status().isOk());
        Assert.assertEquals("DEBUG", logs.saved.getAction());
        Assert.assertEquals("FAILED", logs.saved.getOutcome());
        Assert.assertTrue(logs.saved.getChangeSummary().contains("规则调试页"));
        Assert.assertEquals("v3", logs.saved.getTargetVersion());
        assertMetadataOnly(logs.saved);
    }

    @Test public void creationDeletionAndDraftOverwriteAreNotCollapsedIntoGenericModification() throws Exception {
        var logs = new Logs(); var mvc = mvc(new NamedRuleMetadata(), logs, true);
        mvc.perform(post("/api/rule/definition/save").session(session()).contentType(MediaType.APPLICATION_JSON).content("{\"ruleName\":\"新规则\"}"));
        Assert.assertEquals("CREATE", logs.saved.getAction()); Assert.assertEquals("22", logs.saved.getTargetId());
        Assert.assertTrue(logs.saved.getChangeSummary().contains("新增规则「新规则」"));
        mvc.perform(delete("/api/rule/definition/12").session(session()).header("X-Console-Page", "/rule/12"));
        Assert.assertEquals("DELETE", logs.saved.getAction()); Assert.assertTrue(logs.saved.getChangeSummary().contains("删除规则「授信准入」 #12"));
        mvc.perform(post("/api/rule/governance/rules/12/delete").session(session()).header("X-Console-Page", "/rule"));
        Assert.assertEquals("DELETE", logs.saved.getAction()); Assert.assertEquals("RULE", logs.saved.getTargetType());
        Assert.assertTrue(logs.saved.getChangeSummary().contains("删除审批"));
        Assert.assertTrue(logs.saved.getChangeSummary().contains("审批 #21"));
        for (String mode : List.of("NEW", "OVERWRITE")) {
            mvc.perform(post("/api/rule/definition/12/designer/drafts").session(session())
                    .header("X-Console-Page", "/designer/table/12?sourceType=REVISION&sourceId=99")
                    .contentType(MediaType.APPLICATION_JSON).content(JSON.toJSONString(Map.of("saveMode", mode, "revisionId", 99))));
            Assert.assertEquals("NEW".equals(mode) ? "CREATE" : "UPDATE", logs.saved.getAction());
            Assert.assertEquals("12", logs.saved.getTargetId());
            Assert.assertTrue(logs.saved.getChangeSummary().contains("草稿"));
        }
    }

    @Test public void revisionEndpointKeepsOwningRuleAsTargetAndTabVisitKeepsItsLocation() throws Exception {
        var request = new org.springframework.mock.web.MockHttpServletRequest("DELETE", "/api/rule/definition/12/revisions/99");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "12", "revisionId", "99"));
        var record = new ConsoleOperationLog(); new OperationLogMetadataResolver(Map.of()).request(record, request, null);
        Assert.assertEquals("RULE", record.getTargetType()); Assert.assertEquals("12", record.getTargetId()); Assert.assertEquals("ID 99", record.getTargetVersion());
        var logs = new Logs();
        mvc(new NamedRuleMetadata(), logs, true).perform(post("/api/rule/console/activity").session(session()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(Map.of("action", "PAGE_VIEW", "pagePath", "/rule/12?focus=lifecycle", "name", "页签 · 版本历史", "resourceId", "999"))));
        Assert.assertEquals("12", logs.saved.getTargetId()); Assert.assertTrue(logs.saved.getChangeSummary().contains("版本历史"));
        Assert.assertTrue(logs.saved.getChangeSummary().contains("授信准入"));
    }
    @Test public void recordsOnlyIdentityVersionOutcomeAndSummaryForSmallAndLargePayloads() throws Exception {
        for (int size : new int[]{20, 1_000_000}) {
            var logs = new Logs();
            mvc(new Metadata(), logs, true).perform(put("/api/rule/project/3").session(session())
                    .header("X-Console-Page", "/project/3?token=private")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.toJSONString(Map.of("name", "项目名称", "script", "PRIVATE".repeat(size), "password", "do-not-log", "username", "forged-actor"))))
                    .andExpect(status().isOk());
            var row = logs.saved;
            Assert.assertEquals("actual-admin", row.getUsername()); Assert.assertEquals(Long.valueOf(7), row.getUserId());
            Assert.assertEquals("项目名称", row.getTargetName()); Assert.assertEquals("3", row.getTargetId());
            Assert.assertEquals("v4", row.getTargetVersion()); Assert.assertEquals("SUCCESS", row.getOutcome());
            Assert.assertTrue(row.getChangeSummary().contains("修改项目「项目名称」 #3"));
            Assert.assertTrue(row.getChangeSummary().contains("项目详情页")); Assert.assertEquals("/project/3", row.getPagePath());
            assertMetadataOnly(row);
            Assert.assertTrue(JSON.toJSONString(row).getBytes(StandardCharsets.UTF_8).length < 2048);
        }
    }

    @Test public void deniedRequestKeepsResultWithoutLoadingResource() throws Exception {
        var metadata = new Metadata(); var logs = new Logs();
        mvc(metadata, logs, false).perform(put("/api/rule/project/3").session(session())
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"PRIVATE\"}"))
                .andExpect(status().isForbidden());
        Assert.assertEquals("DENIED", logs.saved.getOutcome()); Assert.assertEquals(0, metadata.lookups);
        assertMetadataOnly(logs.saved);
    }

    @Test public void businessFailureWithHttp200DoesNotStoreFailurePayload() throws Exception {
        var logs = new Logs();
        mvc(new Metadata(), logs, true).perform(post("/api/rule/project/failure").session(session())).andExpect(status().isOk());
        Assert.assertEquals("FAILED", logs.saved.getOutcome()); Assert.assertEquals(Integer.valueOf(409), logs.saved.getResultCode());
        assertMetadataOnly(logs.saved);
    }

    @Test public void unsuccessfulExecutionIsRecordedAsFailure() throws Exception {
        var logs = new Logs();
        mvc(new Metadata(), logs, true).perform(post("/api/rule/project/1/test").session(session())).andExpect(status().isOk());
        Assert.assertEquals("FAILED", logs.saved.getOutcome()); Assert.assertEquals(Integer.valueOf(200), logs.saved.getResultCode());
        assertMetadataOnly(logs.saved);
    }

    @Test public void metadataDoesNotInvokeContentGettersOrCompareSnapshots() {
        ConsoleOperationLog log = new ConsoleOperationLog();
        new OperationLogMetadataResolver(Map.of()).describe(log, new ContentBearingResource());
        Assert.assertEquals("模型名称", log.getTargetName()); Assert.assertEquals("v8", log.getTargetVersion());
        Assert.assertEquals("19", log.getTargetId());
    }

    @Test public void databaseLookupSelectsOnlyNameAndVersionColumns() {
        RuleModelMapper mapper = (RuleModelMapper) Proxy.newProxyInstance(RuleModelMapper.class.getClassLoader(), new Class<?>[]{RuleModelMapper.class}, (proxy, method, args) -> {
            Assert.assertEquals("selectMaps", method.getName());
            var query = (QueryWrapper<?>) args[0];
            Assert.assertEquals("id,model_name AS name,current_version AS version", query.getSqlSelect());
            return List.of(Map.of("id", 5L, "name", "只读元信息", "version", 3));
        });
        ConsoleOperationLog log = new ConsoleOperationLog(); log.setTargetType("MODEL"); log.setTargetId("5");
        new OperationLogMetadataResolver(Map.of("ruleModelMapper", mapper)).complete(log);
        Assert.assertEquals("只读元信息", log.getTargetName()); Assert.assertEquals("v3", log.getTargetVersion());
    }

    @Test public void requestVersionUsesSelectedHistoricalVersionAndProjectFiltersAreNotObjects() {
        var resolver = new OperationLogMetadataResolver(Map.of());
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/rule/model/version/5/2");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("modelId", "5", "version", "2"));
        ConsoleOperationLog log = new ConsoleOperationLog(); resolver.request(log, request, null);
        Assert.assertEquals("5", log.getTargetId()); Assert.assertEquals("v2", log.getTargetVersion());
        request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/rule/model/project/5/all");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("projectId", "5"));
        log = new ConsoleOperationLog(); resolver.request(log, request, null);
        Assert.assertNull(log.getTargetId()); Assert.assertNull(log.getTargetVersion());
    }

    private void assertMetadataOnly(ConsoleOperationLog log) {
        String json = JSON.toJSONString(log);
        for (String forbidden : List.of("PRIVATE", "do-not-log", "requestJson", "beforeJson", "afterJson", "changesJson", "resultJson")) Assert.assertFalse(forbidden, json.contains(forbidden));
    }

    private MockMvc mvc(OperationLogMetadataResolver metadata, ConsoleOperationLogService logs, boolean allowed) {
        var properties = new RuleEngineConsoleLoginProperties(); properties.setEnabled(true);
        var permission = new ConsolePermissionService() { @Override public boolean hasPermission(Long userId, String code) { return allowed; } };
        var audit = new ConsoleOperationAudit(metadata);
        return MockMvcBuilders.standaloneSetup(new TestController(), new ConsoleOperationLogController(logs, new ConsoleAccessGuard(properties, permission)))
                .setControllerAdvice(new ConsoleOperationBodyAdvice(audit), new GlobalExceptionHandler())
                .addInterceptors(new ConsolePermissionInterceptor(properties, permission), audit)
                .addFilters(new ConsoleOperationAuditFilter(properties, new TrustedClientAddressResolver(null), audit, logs)).build();
    }
    private MockHttpSession session() {
        var session = new MockHttpSession(); session.setAttribute("RULE_CONSOLE_USER_ID", 7L); session.setAttribute("RULE_CONSOLE_USERNAME", "actual-admin"); return session;
    }
    private static class Logs extends ConsoleOperationLogService {
        ConsoleOperationLog saved;
        Logs() { super(null); }
        @Override public void append(ConsoleOperationLog log) { saved = log; }
    }
    private static class Metadata extends OperationLogMetadataResolver {
        int lookups;
        Metadata() { super(Map.of()); }
        @Override public void complete(ConsoleOperationLog log) { lookups++; }
    }
    private static class NamedRuleMetadata extends Metadata {
        @Override public void complete(ConsoleOperationLog log) {
            super.complete(log);
            if ("RULE".equals(log.getTargetType()) && "12".equals(log.getTargetId())) log.setTargetName("授信准入");
        }
    }
    public static class ContentBearingResource {
        public Long getId() { return 19L; }
        public String getModelName() { return "模型名称"; }
        public Integer getCurrentVersion() { return 8; }
        public String getModelContent() { throw new AssertionError("不能读取模型文件"); }
        public String getSnapshotJson() { throw new AssertionError("不能读取完整快照"); }
        public String getSourceConfig() { throw new AssertionError("不能读取操作内容"); }
    }
    @RestController static class TestController {
        @PostMapping("/api/rule/definition/save") public ApiResponse<Map<String, Object>> createRule(@RequestBody Map<String, Object> body) { return ApiResponse.ok(Map.of("id", 22, "ruleName", "新规则")); }
        @DeleteMapping("/api/rule/definition/{id}") public ApiResponse<Void> deleteRule() { return ApiResponse.ok(); }
        @PostMapping("/api/rule/governance/rules/{id}/delete") @RequirePermission("rule:edit")
        public ApiResponse<Map<String, Object>> requestDeletion() { return ApiResponse.ok(Map.of("deleted", false, "approvalRequestId", 21)); }
        @PostMapping("/api/rule/definition/{id}/designer/drafts") public ApiResponse<Map<String, Object>> saveDraft(@PathVariable Long id, @RequestBody Map<String, Object> body) {
            return ApiResponse.ok(Map.of("revision", Map.of("id", 99, "definitionId", id, "revisionNo", 4)));
        }
        @PostMapping("/api/rule/definition/{id}/designer/publish") public ApiResponse<Map<String, Object>> publish(@PathVariable Long id) {
            return ApiResponse.ok(Map.of("revision", Map.of("id", 99, "definitionId", id, "revisionNo", 4, "state", "REVIEW", "modelJson", "PRIVATE_CONTENT"), "approvalRequestId", 21));
        }
        @PostMapping("/api/rule/definition/{definitionId}/api-scenarios/execute-validated") public ApiResponse<Map<String, Object>> debug(@PathVariable Long definitionId) {
            return ApiResponse.ok(Map.of("execution", Map.of("success", false, "executionIdentity", Map.of("definitionId", definitionId, "versionNo", 3)), "responseJson", "PRIVATE_RESPONSE"));
        }
        @PutMapping("/api/rule/project/{id}") public ApiResponse<Map<String, Object>> save(@RequestBody Map<String, Object> body) { return ApiResponse.ok(Map.of("id", 3, "projectName", body.get("name"), "currentVersion", 4, "responseContent", "PRIVATE_RESPONSE")); }
        @PostMapping("/api/rule/project/failure") public ApiResponse<Void> failure() { return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_CONFLICT, "PRIVATE_ERROR_CONTENT"); }
        @PostMapping("/api/rule/project/1/test") public ApiResponse<Map<String, Object>> test() { return ApiResponse.ok(Map.of("success", false, "errorMessage", "PRIVATE_EXECUTION_RESULT")); }
    }
}
