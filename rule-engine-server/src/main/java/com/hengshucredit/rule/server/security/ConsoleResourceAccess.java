package com.hengshucredit.rule.server.security;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 在缓存、异步任务及治理快照边界按真实资源归属校验，不使用客户端声称的 projectId。 */
@Service
public class ConsoleResourceAccess {
    private final ConsoleProjectAccess access;
    private final ConsoleDataPermissionHandler policy;
    private final JdbcTemplate jdbc;
    public ConsoleResourceAccess(ConsoleProjectAccess access, ConsoleDataPermissionHandler policy, JdbcTemplate jdbc) {
        this.access = access; this.policy = policy; this.jdbc = jdbc;
    }

    public void require(String type, Long id, String action) {
        if (access.current() == null) return;
        Long projectId = projectForResource(type, id);
        access.requirePermission(ConsoleAccessGuard.resourceModule(type) + ":view", projectId);
        if (!"view".equals(action)) access.requirePermission(ConsoleAccessGuard.resourceModule(type) + ":" + action, projectId);
    }

    public void requirePermission(String type, Long id, String permission) {
        if (access.current() == null) return;
        Long projectId = projectForResource(type, id);
        access.requirePermission(ConsoleAccessGuard.resourceModule(type) + ":view", projectId);
        access.requirePermission(permission, projectId);
    }

    public void requireGovernancePermission(Long id, String permission) {
        if (access.current() == null) return;
        var rows = jdbc.queryForList("SELECT project_id, resource_type FROM governance_approval_request sec_request WHERE id = ? AND ("
                + policy.predicate("governance_approval_request", "sec_request") + ")", id);
        if (rows.isEmpty()) throw new ConsoleAccessDeniedException("审批单不存在或无访问权限");
        access.requirePermission(permission, number(rows.get(0).get("project_id")));
        if ("approval:submit".equals(permission)) {
            String module = ConsoleAccessGuard.resourceModule(String.valueOf(rows.get(0).get("resource_type")));
            access.requirePermission(module + ":edit", number(rows.get(0).get("project_id")));
            access.requirePermission(module + ":submit", number(rows.get(0).get("project_id")));
        }
    }

    public Long projectForResource(String type, Long id) {
        String table = ConsoleDataPermissionHandler.resourceTable(type);
        if (id == null || id <= 0) throw new IllegalArgumentException("资源ID不能为空");
        String columns = switch (table) {
            case "rule_project" -> "id AS project_id";
            case "rule_external_api_config" -> "datasource_id";
            case "rule_list_change_batch" -> "list_id";
            case "rule_model_version" -> "model_id";
            case "rule_version_binding" -> "definition_id";
            case "rule_definition_ref", "rule_experiment" -> "project_id";
            default -> "project_id, scope";
        };
        var rows = jdbc.queryForList("SELECT " + columns + " FROM " + table + " sec_owner WHERE sec_owner.id = ? AND ("
                + policy.predicate(table, "sec_owner") + ")", id);
        if (rows.isEmpty()) throw new ConsoleAccessDeniedException("资源不存在或未获项目/内容访问权限");
        var row = rows.get(0);
        if (table.equals("rule_external_api_config")) return projectForResource("EXTERNAL_DATASOURCE", number(row.get("datasource_id")));
        if (table.equals("rule_list_change_batch")) return projectForResource("LIST_LIBRARY", number(row.get("list_id")));
        if (table.equals("rule_model_version")) return projectForResource("MODEL", number(row.get("model_id")));
        if (table.equals("rule_version_binding")) return projectForResource("RULE", number(row.get("definition_id")));
        if ("GLOBAL".equals(row.get("scope"))) return 0L;
        return number(row.get("project_id"));
    }

    public Long draftProject(String type, Long resourceId, String json) {
        JSONObject snapshot = JSON.parseObject(json);
        if (snapshot == null) throw new IllegalArgumentException("资源配置不能为空");
        Long projectId;
        if ("PROJECT".equals(type)) projectId = resourceId != null && resourceId > 0 ? resourceId : null;
        else if ("EXTERNAL_API".equals(type)) projectId = projectForResource("EXTERNAL_DATASOURCE", snapshot.getLong("datasourceId"));
        else if ("LIST_RECORD_BATCH".equals(type)) projectId = projectForResource("LIST_LIBRARY", snapshot.getLong("listId"));
        else if ("GLOBAL".equals(snapshot.getString("scope"))) projectId = 0L;
        else projectId = snapshot.getLong("projectId");
        if (access.current() != null) {
            if (resourceId != null && resourceId > 0) require(type, resourceId, "edit");
            if (!("PROJECT".equals(type) && (resourceId == null || resourceId <= 0))) {
                if (projectId != null && projectId > 0) access.requireProject(projectId);
                access.requirePermission(ConsoleAccessGuard.resourceModule(type) + ":edit", projectId);
            }
        }
        return projectId;
    }

    private Long number(Object value) { return value instanceof Number number ? number.longValue() : null; }
}
