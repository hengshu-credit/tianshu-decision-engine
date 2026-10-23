package com.hengshucredit.rule.server.security;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.Collection;
import java.util.Map;

/** SQL 行过滤保护原记录，这里额外验证写入的新归属和子资源的父实体。 */
@Component
public class ConsoleDataWriteInterceptor implements InnerInterceptor {
    private final ConsoleProjectAccess access;
    private final ConsoleDataPermissionHandler policy;
    private final JdbcTemplate jdbc;

    public ConsoleDataWriteInterceptor(ConsoleProjectAccess access, ConsoleDataPermissionHandler policy, JdbcTemplate jdbc) {
        this.access = access; this.policy = policy; this.jdbc = jdbc;
    }

    @Override public void beforeUpdate(Executor executor, MappedStatement statement, Object parameter) {
        if (statement.getId().contains(".mapper.Console") || access.current() == null) return;
        inspect(parameter, statement.getSqlCommandType());
    }

    private void inspect(Object value, SqlCommandType command) {
        if (value == null) return;
        if (value instanceof Map<?, ?> map) {
            map.values().stream().distinct().forEach(item -> inspect(item, command));
            return;
        }
        if (value instanceof Collection<?> list) { list.forEach(item -> inspect(item, command)); return; }
        if (value instanceof AbstractWrapper<?, ?, ?>) return;
        TableName annotation = value.getClass().getAnnotation(TableName.class);
        if (annotation == null || annotation.value().startsWith("console_")) return;
        String table = annotation.value();
        var meta = SystemMetaObject.forObject(value);
        Object id = meta.hasGetter("id") ? meta.getValue("id") : null;
        if (command != SqlCommandType.INSERT && id != null) requireRow(table, id);
        String module = ConsoleDataPermissionHandler.moduleForTable(table);
        Long project = "rule_project".equals(table) ? number(id) : number(meta.hasGetter("projectId") ? meta.getValue("projectId") : null);
        if (module != null) {
            Object resourceScope = meta.hasGetter("scope") ? meta.getValue("scope") : null;
            if (command != SqlCommandType.INSERT && id != null && !"rule_project".equals(table) && (resourceScope == null || project == null)) {
                var stored = jdbc.queryForMap("SELECT scope, project_id FROM " + table + " WHERE id = ?", id);
                if (resourceScope == null) resourceScope = stored.get("scope");
                if (project == null) project = number(stored.get("project_id"));
            }
            boolean global = "GLOBAL".equals(resourceScope);
            String action = approving() ? "approval:approve" : module + ":edit";
            if (global) access.requireGlobalEditable();
            if (!("rule_project".equals(table) && command == SqlCommandType.INSERT)) access.requirePermission(action, global ? null : project);
            else if (!access.current().allProjects() || !access.current().permissions().contains(action)) throw new ConsoleAccessDeniedException("创建项目需要不受项目范围限制的项目管理权限");
        }
        if ("decision_artifact".equals(table) && command == SqlCommandType.INSERT && (number(meta.getValue("definitionId")) == null || number(meta.getValue("definitionId")) <= 0)) {
            access.requirePermission("rule:edit", null);
        }
        if (meta.hasGetter("scope") && meta.getValue("scope") != null) {
            String scope = String.valueOf(meta.getValue("scope"));
            Long projectId = number(meta.hasGetter("projectId") ? meta.getValue("projectId") : null);
            if (!"GLOBAL".equals(scope) && !"PROJECT".equals(scope)) throw new IllegalArgumentException("资源范围必须为 GLOBAL 或 PROJECT");
            access.requireScope(scope, projectId);
            if ("GLOBAL".equals(scope) && projectId != null && projectId > 0) throw new IllegalArgumentException("全局资源不能指定所属项目");
        } else if (meta.hasGetter("projectId")) {
            Long projectId = number(meta.getValue("projectId"));
            if (projectId != null && projectId > 0) access.requireProject(projectId);
        }
        String parent = switch (table) {
            case "rule_definition_input_field", "rule_definition_output_field", "rule_definition_content", "rule_definition_version", "rule_revision",
                 "rule_version_binding", "rule_api_doc_scenario", "rule_regression_evidence", "rule_regression_evidence_latest", "rule_published", "decision_artifact" -> "definitionId:rule_definition";
            case "rule_model_input_field", "rule_model_output_field", "rule_model_version" -> "modelId:rule_model";
            case "rule_data_object_field" -> "objectId:rule_data_object";
            case "rule_data_object_field_option" -> "fieldId:rule_data_object_field";
            case "rule_variable_option" -> "variableId:rule_variable";
            case "rule_function_version" -> "functionId:rule_function";
            case "rule_external_api_config", "rule_external_api_trial" -> "datasourceId:rule_external_datasource";
            case "rule_list_record", "rule_list_record_log", "rule_list_change_batch" -> "listId:rule_list_library";
            case "rule_list_change_item" -> "batchId:rule_list_change_batch";
            case "rule_experiment_group", "rule_experiment_version" -> "experimentId:rule_experiment";
            case "artifact_deployment" -> "targetDefinitionId:rule_definition";
            case "artifact_resource_binding" -> "deploymentId:artifact_deployment";
            default -> null;
        };
        if (parent != null) {
            String[] parts = parent.split(":");
            Long parentId = number(meta.getValue(parts[0]));
            if (parentId != null && parentId > 0) requireRow(parts[1], parentId);
        }
    }

    public void requireRow(String table, Object id) {
        if (access.current() == null) return;
        if (!table.matches("[a-z_]+")) throw new IllegalArgumentException("无效资源类型");
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " sec_target WHERE sec_target.id = ? AND ("
                + policy.forMutation(() -> policy.predicate(table, "sec_target")) + ")", Long.class, id);
        if (count == null || count == 0) throw new ConsoleAccessDeniedException("资源不存在或未获项目/内容访问权限");
    }

    public void validateEntity(Object entity) { if (access.current() != null) inspect(entity, SqlCommandType.UPDATE); }

    private boolean approving() {
        if (!(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes attrs)) return false;
        return attrs.getRequest().getRequestURI().matches("/api/rule/governance/requests/[0-9]+/(approve|reject)");
    }

    private Long number(Object value) { return value instanceof Number number ? number.longValue() : null; }
}
