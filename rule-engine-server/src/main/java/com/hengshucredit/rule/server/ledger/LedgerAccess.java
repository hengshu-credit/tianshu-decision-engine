package com.hengshucredit.rule.server.ledger;

import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.model.PlatformException;
import com.hengshucredit.rule.server.security.ConsoleAccessDeniedException;
import com.hengshucredit.rule.server.security.ConsoleDataWriteInterceptor;
import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Objects;

@Service
public class LedgerAccess {
    private final ConsoleProjectAccess access;
    private final ConsoleDataWriteInterceptor writes;
    private final ConsoleOperatorResolver operator;
    private final JdbcTemplate jdbc;
    public LedgerAccess(ConsoleProjectAccess access, ConsoleDataWriteInterceptor writes, ConsoleOperatorResolver operator, JdbcTemplate jdbc) {
        this.access = access; this.writes = writes; this.operator = operator; this.jdbc = jdbc;
    }
    public long userId() {
        var scope = access.current();
        if (scope == null || scope.userId() == null || scope.userId() <= 0) throw new ConsoleAccessDeniedException("台账记录需要登录控制台账户");
        return scope.userId();
    }
    public String actor() { userId(); return operator.resolve(); }
    public void requireWrite() { userId(); if (!access.current().can("ledger:edit")) throw new ConsoleAccessDeniedException("无台账编辑权限"); }
    public void requireTarget(String scope, Long projectId) {
        userId();
        if (!"GLOBAL".equals(scope) && !"PROJECT".equals(scope)) throw new IllegalArgumentException("请选择全局或具体项目");
        if ("GLOBAL".equals(scope)) {
            if (projectId != null && projectId > 0) throw new IllegalArgumentException("全局内容不能指定项目");
            access.requireGlobalEditable(); access.requirePermission("ledger:edit", null);
        } else {
            if (projectId == null || projectId <= 0) throw new IllegalArgumentException("请选择所属项目");
            access.requirePermission("ledger:edit", projectId);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM rule_project WHERE id = ?", Long.class, projectId) != 1) throw new IllegalArgumentException("项目不存在");
        }
    }
    public boolean canEdit(Map<String, Object> row) {
        long user = userId();
        var scope = access.current();
        boolean owner = number(row.get("owner_user_id")) == user;
        if ("PRIVATE".equals(row.get("scope"))) return owner && scope.can("ledger:edit") && draftScope(row, "ledger:edit");
        Long project = "GLOBAL".equals(row.get("scope")) ? null : number(row.get("project_id"));
        return scope.can("ledger:edit", project) && (owner || scope.can("ledger:manage", project))
                && (number(row.get("has_draft")) == 0 || number(row.get("draft_user_id")) != user || draftScope(row, "ledger:edit"));
    }
    public void requireEdit(String table, Map<String, Object> row) {
        if (!canEdit(row)) throw new ConsoleAccessDeniedException("只允许维护本人或获管理授权的台账内容");
        writes.requireRow(table, row.get("id"));
    }
    public void requireDraft(Map<String, Object> row) {
        if (number(row.get("has_draft")) != 0 && number(row.get("draft_user_id")) != userId()) {
            throw new ConsoleAccessDeniedException("该文章存在其他作者的私有草稿，不能读取或覆盖");
        }
        if (number(row.get("has_draft")) != 0 && !canReadDraft(row)) throw new ConsoleAccessDeniedException("当前账户已无权查看草稿的目标范围");
    }
    public boolean canReadDraft(Map<String, Object> row) { return number(row.get("draft_user_id")) == userId() && draftScope(row, "ledger:view"); }
    private boolean draftScope(Map<String, Object> row, String permission) {
        Object scope = row.get("draft_scope");
        if (scope == null || "PRIVATE".equals(scope)) return true;
        return access.current().can(permission, "PROJECT".equals(scope) ? number(row.get("draft_project_id")) : null);
    }
    public static void version(Map<String, Object> row, Long expected) {
        if (expected == null || !Objects.equals(number(row.get("lock_version")), expected)) {
            throw new PlatformException(PlatformCode.RESOURCE_CONFLICT, "内容已变更，请重新加载后再保存");
        }
    }
    public static long number(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
}
