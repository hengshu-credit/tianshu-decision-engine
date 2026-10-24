package com.hengshucredit.rule.server.audit;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.model.entity.ConsoleOperationLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConsoleOperationAudit implements HandlerInterceptor {
    static final String STATE = ConsoleOperationAudit.class.getName();
    private final OperationLogMetadataResolver metadata;

    static class State {
        final ConsoleOperationLog log = new ConsoleOperationLog();
        final long started = System.nanoTime();
        boolean authorized;
        boolean executionFailed;
        ConsoleOperationPage page;
        String tab;
        String governanceAction;
        String approvalId;
        boolean deletionApproval;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        State state = state(request);
        if (state == null || !(handler instanceof HandlerMethod)) return true;
        state.authorized = true;
        capture(request, null);
        if ("DELETE".equals(state.log.getAction())) completeMetadata(state.log);
        return true;
    }

    public void capture(HttpServletRequest request, Object body) {
        State state = state(request);
        if (state == null || !state.authorized) return;
        if (body != null && "POST".equals(request.getMethod()) && request.getRequestURI().matches("/api/rule/console/(accounts|roles)")) {
            state.log.setAction(OperationLogMetadataResolver.property(body, "id") == null ? "CREATE" : "UPDATE");
        }
        if (body != null && request.getRequestURI().equals("/api/rule/definition/save")) {
            state.log.setAction(OperationLogMetadataResolver.property(body, "id") == null ? "CREATE" : "UPDATE");
        }
        if (body != null && request.getRequestURI().endsWith("/designer/drafts")) {
            state.log.setAction("OVERWRITE".equals(OperationLogMetadataResolver.property(body, "saveMode")) ? "UPDATE" : "CREATE");
        }
        if (request.getRequestURI().equals("/api/rule/governance/drafts")) {
            state.governanceAction = governanceAction(body);
            if (state.governanceAction != null) state.log.setAction(state.governanceAction);
        }
        // 请求仅用于提取固定元信息，不保存或序列化请求参数及正文。
        metadata.request(state.log, request, body);
    }

    public void result(HttpServletRequest request, Object response) {
        State state = state(request);
        if (state == null) return;
        Object data = null;
        if (response instanceof ApiResponse<?> body) { state.log.setResultCode(body.status().httpStatus()); data = body.getData(); }

        if (!state.authorized || data == null) return;
        if (java.util.Set.of("EXECUTE", "DEBUG").contains(state.log.getAction()) &&
                (Boolean.FALSE.equals(OperationLogMetadataResolver.property(data, "success")) ||
                 Boolean.FALSE.equals(OperationLogMetadataResolver.property(OperationLogMetadataResolver.property(data, "execution"), "success")))) state.executionFailed = true;
        if (state.log.getResultCode() != null && state.log.getResultCode() >= 400) return;
        Object approvalId = OperationLogMetadataResolver.property(data, "approvalRequestId");
        if (approvalId != null && approvalId.toString().matches("[0-9]{1,19}")) state.approvalId = approvalId.toString();
        state.deletionApproval = "DELETE".equals(state.log.getAction()) && Boolean.FALSE.equals(OperationLogMetadataResolver.property(data, "deleted")) && state.approvalId != null;
        if (data instanceof com.hengshucredit.rule.server.governance.GovernanceRequestView) state.governanceAction = governanceAction(data);
        metadata.describe(state.log, data);
    }

    public void finish(HttpServletRequest request) {
        State state = state(request);
        if (state == null) return;
        if (!state.authorized) metadata.request(state.log, request, null);
        if (state.page != null) metadata.page(state.log, state.page);
        if (state.authorized && !"DENIED".equals(state.log.getOutcome())) completeMetadata(state.log);
        state.log.setChangeSummary(limit(summary(state), 160));
    }

    private void completeMetadata(ConsoleOperationLog record) {
        try { metadata.complete(record); }
        catch (RuntimeException error) { log.warn("操作日志读取对象元信息失败: {}", record.getRequestPath(), error); }
    }

    private String governanceAction(Object body) {
        String value = java.util.Objects.toString(OperationLogMetadataResolver.property(body, "action"), "");
        return java.util.Set.of("CREATE", "UPDATE", "DELETE", "ENABLE", "DISABLE", "RESTORE").contains(value) ? value : null;
    }

    private String summary(State state) {
        ConsoleOperationLog record = state.log;
        String path = record.getRequestPath();
        String type = record.getTargetType();
        String object = type == null ? "" : switch (type) {
            case "RULE", "RULE_REVISION" -> "规则";
            case "CONSOLE_USER" -> "账户";
            case "CONSOLE_ROLE" -> "角色";
            case "SYSTEM_SETTING" -> "系统配置";
            case "DATA_OBJECT" -> "数据对象";
            case "VARIABLE" -> "变量";
            case "RULE_INPUT_FIELD" -> "规则输入字段";
            case "RULE_OUTPUT_FIELD" -> "规则输出字段";
            case "MODEL_INPUT_FIELD" -> "模型输入字段";
            case "MODEL_OUTPUT_FIELD", "MODEL_OUTPUT" -> "模型输出字段";
            case "DATA_OBJECT_FIELD", "DATA_FIELD" -> "对象字段";
            case "EXTERNAL_API" -> "外数 API";
            case "APPROVAL_REQUEST" -> "审批申请";
            default -> "";
        };
        if (object.isEmpty()) object = Map.ofEntries(
                Map.entry("project", "项目"), Map.entry("rule", "规则"), Map.entry("field", "变量或数据对象"),
                Map.entry("list", "名单"), Map.entry("model", "模型"), Map.entry("function", "函数"),
                Map.entry("database", "数据库配置"), Map.entry("datasource", "外数配置"), Map.entry("experiment", "分流实验"),
                Map.entry("billing", "账单"), Map.entry("approval", "审批申请"), Map.entry("account", "账户或角色"),
                Map.entry("transfer", "迁移配置"), Map.entry("log", "执行日志"), Map.entry("lineage", "血缘"), Map.entry("dashboard", "看板"))
                .getOrDefault(record.getModule(), "对象");
        ConsoleOperationPage page = ConsoleOperationPage.resolve(record.getPagePath());
        String subject = record.getTargetName() == null ? object : object + "「" + limit(record.getTargetName(), 48) + "」";
        if (record.getTargetId() != null) subject += " #" + limit(record.getTargetId(), 32);
        if (record.getTargetVersion() != null) subject += "（" + limit(record.getTargetVersion(), 40) + "）";
        if ("PAGE_VIEW".equals(record.getAction()) && record.getTargetId() == null && page != null) subject = page.label();
        String action = switch (record.getAction()) {
            case "READ", "PAGE_VIEW", "FIELD_VIEW" -> "查看";
            case "FIELD_SELECT" -> "选择";
            case "CREATE" -> "新增";
            case "DELETE" -> "删除";
            case "DEBUG" -> "调试";
            case "EXECUTE" -> "执行";
            case "COMPILE" -> "提交编译：";
            case "PUBLISH" -> path.endsWith("/designer/publish") ? "提交发布审批：" : "发布";
            case "OFFLINE" -> "下线";
            case "ROLLBACK", "RESTORE" -> "申请回滚";
            case "IMPORT" -> "导入";
            case "EXPORT" -> "导出";
            case "SUBMIT" -> "提交审批：";
            case "APPROVE" -> "批准审批：";
            case "REJECT" -> "驳回审批：";
            case "CANCEL" -> "取消";
            case "ENABLE" -> "申请启用";
            case "DISABLE" -> "申请停用";
            default -> "修改";
        };
        if ("LOGIN".equals(record.getAction())) return "登录控制台";
        if ("LOGOUT".equals(record.getAction())) return "退出控制台";
        if (state.deletionApproval) action = "发起删除审批：";
        String detail = "";
        if (path.endsWith("/permissions")) detail = "权限";
        else if (path.endsWith("/password")) { action = "重置"; detail = "密码"; }
        else if (path.endsWith("/status")) detail = "启用状态";
        else if (path.contains("/api-scenarios") && !"DEBUG".equals(record.getAction())) detail = "测试用例";
        else if (path.contains("/designer/drafts") || path.endsWith("/revisions/draft")) detail = "草稿";
        else if (path.contains("/script/") || path.contains("/scriptMode/")) detail = "脚本配置";
        if (state.governanceAction != null && path.equals("/api/rule/governance/drafts")) action = "发起" + action;
        String summary = action + subject + (detail.isEmpty() ? "" : "的" + detail);
        if (state.governanceAction != null && path.equals("/api/rule/governance/drafts")) summary += "审批申请";
        if (state.approvalId != null) summary += "；审批 #" + state.approvalId;
        if (page != null && !subject.equals(page.label())) summary += " / " + page.label();
        if (state.tab != null && !state.tab.isBlank()) summary += " / 页签「" + state.tab + "」";
        return summary;
    }

    static State state(HttpServletRequest request) { return (State) request.getAttribute(STATE); }
    static String limit(String value, int length) { return value == null ? null : value.substring(0, Math.min(value.length(), length)); }
}
