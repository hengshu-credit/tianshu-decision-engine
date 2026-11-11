package com.hengshucredit.rule.server.security;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

/** 列表、总数、按 ID 读取及更新/删除使用相同范围；子资源从所属实体继承。 */
@Component
public class ConsoleDataPermissionHandler implements MultiDataPermissionHandler {
    private final ConsoleProjectAccess access;
    private final ThreadLocal<Boolean> mutation = new ThreadLocal<>();
    public ConsoleDataPermissionHandler(ConsoleProjectAccess access) { this.access = access; }

    private static final Map<String, String> ROOT_MODULES = Map.ofEntries(
            Map.entry("rule_system_setting", "system"),
            Map.entry("rule_definition", "rule"), Map.entry("rule_variable", "field"),
            Map.entry("rule_data_object", "field"), Map.entry("rule_data_object_field", "field"),
            Map.entry("rule_field_validation", "field"), Map.entry("rule_model", "model"),
            Map.entry("rule_function", "function"), Map.entry("rule_list_library", "list"),
            Map.entry("rule_external_datasource", "datasource"), Map.entry("rule_db_datasource", "database"),
            Map.entry("rule_sftp_datasource", "database"), Map.entry("rule_billing_config", "billing"));
    private static final Map<String, String> RESOURCE_TABLES = Map.ofEntries(
            Map.entry("SYSTEM_SETTING", "rule_system_setting"),
            Map.entry("RULE", "rule_definition"), Map.entry("RULE_PROJECT_BINDING", "rule_definition_ref"),
            Map.entry("VARIABLE", "rule_variable"), Map.entry("CONSTANT", "rule_variable"),
            Map.entry("DATA_OBJECT", "rule_data_object"), Map.entry("DATA_OBJECT_ROOT", "rule_data_object"),
            Map.entry("DATA_FIELD", "rule_data_object_field"), Map.entry("DATA_OBJECT_FIELD", "rule_data_object_field"),
            Map.entry("FIELD_VALIDATION", "rule_field_validation"), Map.entry("MODEL", "rule_model"),
            Map.entry("MODEL_VERSION", "rule_model_version"),
            Map.entry("RULE_VERSION", "rule_version_binding"),
            Map.entry("FUNCTION", "rule_function"), Map.entry("LIST", "rule_list_library"),
            Map.entry("LIST_LIBRARY", "rule_list_library"), Map.entry("LIST_RECORD_BATCH", "rule_list_change_batch"),
            Map.entry("EXTERNAL_DATASOURCE", "rule_external_datasource"), Map.entry("DATASOURCE", "rule_external_datasource"),
            Map.entry("EXTERNAL_API", "rule_external_api_config"), Map.entry("API", "rule_external_api_config"),
            Map.entry("DATABASE", "rule_db_datasource"), Map.entry("DB", "rule_db_datasource"),
            Map.entry("DB_DATASOURCE", "rule_db_datasource"), Map.entry("SFTP_DATASOURCE", "rule_sftp_datasource"),
            Map.entry("SFTP", "rule_sftp_datasource"), Map.entry("BILLING_CONFIG", "rule_billing_config"),
            Map.entry("EXPERIMENT", "rule_experiment"), Map.entry("PROJECT", "rule_project"));

    @Override public Expression getSqlSegment(Table table, Expression where, String statementId) {
        String name = table.getName().replace("`", "").toLowerCase(java.util.Locale.ROOT);
        if (isGrantDirectory(statementId) || (name.startsWith("console_") && !Set.of("console_operation_log", "console_security_audit_log").contains(name))
                || !(name.startsWith("rule_") || name.startsWith("govern") || name.startsWith("artifact_")
                || name.startsWith("decision_artifact") || name.equals("resource_impact_analysis") || name.equals("offline_transfer_log") || Set.of("console_operation_log", "console_security_audit_log").contains(name))) return null;
        ConsoleProjectAccess.Scope scope = access.current();
        if (scope == null) return null;
        String alias = table.getAlias() == null ? table.getFullyQualifiedName() : table.getAlias().getName();
        String predicate = predicate(scope, name, alias, 0);
        if ("com.hengshucredit.rule.server.mapper.GovernedResourceMapper.linkBaselineVersion".equals(statementId)
                && "governed_resource".equals(name)) {
            Boolean previous = mutation.get(); mutation.set(false);
            try { predicate = predicate(scope, name, alias, 0); }
            finally { if (previous == null) mutation.remove(); else mutation.set(previous); }
        }
        if ("com.hengshucredit.rule.server.mapper.GovernedResourceMapper.selectProjectReferenceMetadata".equals(statementId)
                && "governed_resource".equals(name)) predicate = alias + ".resource_type = 'PROJECT' AND " + projectLabels(scope, alias + ".project_id");
        if ("com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper.selectProjectReferenceMetadata".equals(statementId)
                && "governed_resource_version".equals(name)) predicate = alias + ".resource_type = 'PROJECT' AND EXISTS (SELECT 1 FROM governed_resource sec_project_ref WHERE sec_project_ref.id = "
                + alias + ".governed_resource_id AND " + projectLabels(scope, "sec_project_ref.project_id") + ")";
        if ("rule_project".equals(name) && statementId != null && statementId.startsWith("com.hengshucredit.rule.server.mapper.ConsoleUserProjectMapper.browseOption")) {
            predicate = projectLabels(scope, alias + ".id");
        }
        if (predicate == null) return null;
        try { return CCJSqlParserUtil.parseCondExpression("(" + predicate + ")"); }
        catch (Exception error) { throw new IllegalStateException("无法应用控制台项目权限: " + name, error); }
    }

    private boolean isGrantDirectory(String id) {
        return id != null && (id.equals("com.hengshucredit.rule.server.mapper.ConsoleUserProjectMapper.grantOptions")
                || id.equals("com.hengshucredit.rule.server.mapper.ConsoleUserProjectMapper.grantTargetExists"));
    }

    public String filterSql(String sql) {
        if (access.current() == null) return sql;
        return new com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor(this).parserSingle(sql, "console-jdbc");
    }

    public void requireHistoryScope(String historyScope, Long projectId) {
        var scope = access.current();
        if (scope == null) return;
        if ("GLOBAL".equals(historyScope)) {
            if (!scope.allProjectPermission("log:view")) throw new ConsoleAccessDeniedException("全局历史统计需要所有项目的日志查看权限");
        } else access.requirePermission("log:view", projectId);
    }

    public <T> T forMutation(java.util.function.Supplier<T> operation) {
        Boolean previous = mutation.get(); mutation.set(true);
        try { return operation.get(); }
        finally { if (previous == null) mutation.remove(); else mutation.set(previous); }
    }

    private String actionPermission(String module) {
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes attrs
                && attrs.getRequest().getRequestURI().matches("/api/rule/governance/requests/[0-9]+/(approve|reject)")) return "approval:approve";
        return module + ":edit";
    }

    public static String resourceTable(String type) {
        String table = RESOURCE_TABLES.get(type);
        if (table == null) throw new IllegalArgumentException("不支持的资源类型: " + type);
        return table;
    }

    /** JDBC 统计与 MyBatis 使用同一表达式；表和别名只接受代码中固定值。 */
    public String predicate(String table, String alias) {
        var scope = access.current();
        return scope == null ? "1 = 1" : java.util.Objects.requireNonNullElse(predicate(scope, table, alias, 0), "1 = 1");
    }

    public String predicate(ConsoleProjectAccess.Scope scope, String table, String a, int depth) {
        String resource = resourcePredicate(scope, table, a, depth);
        String owner = recordOwner(scope, table, a);
        return owner == null ? resource : resource == null ? owner : "(" + resource + ") AND (" + owner + ")";
    }

    private String recordOwner(ConsoleProjectAccess.Scope scope, String table, String a) {
        if (scope.globalCan("account:cross-record-view")) return null;
        String user = scope.userId() == null ? "NULL" : String.valueOf(scope.userId());
        if (Set.of("console_operation_log", "console_security_audit_log").contains(table)) return a + ".user_id = " + user;
        if ("governed_resource_version".equals(table)) return "(" + a + ".actor_user_id = " + user
                + " OR EXISTS (SELECT 1 FROM governance_approval_request sec_owner_request WHERE sec_owner_request.id = "
                + a + ".approval_request_id AND sec_owner_request.actor_user_id = " + user + "))";
        if (Set.of("rule_execution_log", "rule_execution_log_current", "rule_runtime_call_log", "rule_billing_record",
                "rule_experiment_execution_log", "rule_list_record_log", "offline_transfer_log", "governance_approval_request",
                "rule_definition_version", "rule_model_version", "rule_function_version", "rule_experiment_version",
                "rule_revision", "rule_auth_access_log", "rule_billing_summary").contains(table)) return a + ".actor_user_id = " + user;
        if ("rule_version_binding".equals(table)) return "EXISTS (SELECT 1 FROM rule_definition_version sec_owner_snapshot WHERE sec_owner_snapshot.id = " + a + ".snapshot_id AND sec_owner_snapshot.actor_user_id = " + user + ")";
        if ("rule_list_change_batch".equals(table)) return "EXISTS (SELECT 1 FROM governance_approval_request sec_owner_batch WHERE sec_owner_batch.id = " + a + ".approval_request_id AND sec_owner_batch.actor_user_id = " + user + ")";
        if (Set.of("rule_lifecycle_event", "rule_regression_evidence").contains(table)) {
            return "EXISTS (SELECT 1 FROM rule_revision sec_owner_revision WHERE sec_owner_revision.id = " + a
                    + ".revision_id AND sec_owner_revision.actor_user_id = " + user + ")";
        }
        return null;
    }

    private String resourcePredicate(ConsoleProjectAccess.Scope scope, String table, String a, int depth) {
        if (table.startsWith("rule_ledger_")) return ledgerPredicate(scope, table, a, depth);
        if ("rule_system_setting".equals(table)) return scope.globalCan(Boolean.TRUE.equals(mutation.get()) ? actionPermission("system") : "system:view") ? "1 = 1" : "1 = 0";
        String module = ROOT_MODULES.get(table);
        if (module != null) {
            String global = scope.globalCan(module + ":view") ? a + ".scope = 'GLOBAL'" : "1 = 0";
            String own = "(" + global + " OR (" + a + ".scope = 'PROJECT' AND " + projectPermission(scope, a + ".project_id", module + ":view") + "))";
            if (Boolean.TRUE.equals(mutation.get())) {
                String action = actionPermission(module);
                String globalWrite = scope.globalCan(action) ? a + ".scope = 'GLOBAL'" : "1 = 0";
                own += " AND (" + globalWrite + " OR (" + a + ".scope = 'PROJECT' AND " + projectPermission(scope, a + ".project_id", action) + "))";
                if ("rule_variable".equals(table)) own += " AND " + a + ".system_constant = 0";
            }
            if ("rule_data_object_field".equals(table)) own += " AND (" + a + ".object_id IS NULL OR " + a + ".object_id = 0 OR " + child(scope, a, "object_id", "rule_data_object", depth) + ")";
            return own;
        }
        return switch (table) {
            case "rule_project" -> (Boolean.TRUE.equals(mutation.get()) || projectContentRequest() ? projectPermission(scope, a + ".id", "project:view")
                    : projectLabels(scope, a + ".id")) + mutationPermission(scope, a + ".id", "project", false);
            case "rule_project_auth", "rule_project_auth_token", "rule_auth_access_log" -> projectPermission(scope, a + ".project_id", "project:view") + mutationPermission(scope, a + ".project_id", "project", false);
            case "rule_definition_input_field", "rule_definition_output_field", "rule_definition_content", "rule_definition_version",
                 "rule_revision", "rule_api_doc_scenario", "rule_regression_evidence_latest", "rule_published", "rule_lifecycle_event", "rule_designer_save_operation",
                 "rule_publish_outbox", "rule_version_binding" -> child(scope, a, "definition_id", "rule_definition", depth);
            case "rule_regression_evidence" -> child(scope, a, "definition_id", "rule_definition", depth)
                    + " AND " + scopedPermission(scope, a + ".project_id", "rule:view");
            case "decision_artifact" -> "(" + (scope.globalCan(Boolean.TRUE.equals(mutation.get()) ? "rule:edit" : "rule:view")
                    ? "(" + a + ".definition_id IS NULL OR " + a + ".definition_id = 0)" : "1 = 0") + " OR " + child(scope, a, "definition_id", "rule_definition", depth) + ")";
            case "rule_definition_ref" -> project(scope, a + ".project_id", false) + " AND " + child(scope, a, "definition_id", "rule_definition", depth);
            case "rule_model_input_field", "rule_model_output_field", "rule_model_version" -> child(scope, a, "model_id", "rule_model", depth);
            case "rule_model_ref" -> project(scope, a + ".project_id", false) + " AND " + child(scope, a, "model_id", "rule_model", depth);
            case "rule_variable_option" -> child(scope, a, "variable_id", "rule_variable", depth);
            case "rule_data_object_field_option" -> child(scope, a, "field_id", "rule_data_object_field", depth);
            case "rule_function_version" -> child(scope, a, "function_id", "rule_function", depth);
            case "rule_external_api_config", "rule_external_api_trial" -> child(scope, a, "datasource_id", "rule_external_datasource", depth);
            case "rule_list_record", "rule_list_record_log", "rule_list_change_batch" -> child(scope, a, "list_id", "rule_list_library", depth);
            case "rule_list_change_item" -> child(scope, a, "batch_id", "rule_list_change_batch", depth);
            case "rule_experiment" -> scopedPermission(scope, a + ".project_id", "experiment:view") + mutationPermission(scope, a + ".project_id", "experiment", true);
            case "rule_experiment_group", "rule_experiment_version", "rule_experiment_execution_log", "rule_experiment_execution_state" -> child(scope, a, "experiment_id", "rule_experiment", depth);
            case "decision_artifact_component" -> child(scope, a, "artifact_id", "decision_artifact", depth);
            case "artifact_deployment" -> child(scope, a, "target_definition_id", "rule_definition", depth);
            case "artifact_resource_binding" -> child(scope, a, "deployment_id", "artifact_deployment", depth);
            case "governed_resource", "governance_approval_request" -> resourceModule(scope, a + ".resource_type", a + ".project_id");
            case "governed_resource_version" -> child(scope, a, "governed_resource_id", "governed_resource", depth);
            case "governance_approval_event", "governance_dependency_snapshot" -> child(scope, a, "request_id", "governance_approval_request", depth);
            case "resource_impact_analysis" -> resource(scope, a + ".resource_type", a + ".resource_id", depth);
            case "rule_execution_log", "rule_execution_log_current" -> executionLog(scope, a);
            case "rule_execution_statistics", "rule_execution_statistics_current" -> "EXISTS (SELECT 1 FROM rule_execution_log sec_log WHERE sec_log.id = "
                    + a + ".id AND sec_log.create_time = " + a + ".create_time AND " + executionLog(scope, "sec_log") + ")";
            case "rule_set_statistics_event" -> "EXISTS (SELECT 1 FROM rule_execution_log sec_log WHERE sec_log.id = " + a
                    + ".log_id AND sec_log.create_time = " + a + ".create_time AND " + executionLog(scope, "sec_log") + ")";
            case "rule_runtime_call_log" -> "(" + Map.of("MODEL", "model:view", "DATABASE", "database:view", "SFTP", "database:view", "DATASOURCE", "datasource:view", "LIST", "list:view")
                    .entrySet().stream().map(entry -> "(" + a + ".module_type = '" + entry.getKey() + "' AND " + logScope(scope, a, entry.getValue()) + ")").collect(java.util.stream.Collectors.joining(" OR ")) + ")";
            case "rule_billing_record", "rule_billing_summary" -> logScope(scope, a, "billing:view");
            case "rule_execution_persistence_outbox" -> logScope(scope, a, "log:view");
            case "rule_execution_state" -> project(scope, a + ".project_id", true) + " AND " + child(scope, a, "definition_id", "rule_definition", depth);
            case "rule_application_history", "rule_trace_registry", "rule_list_action_receipt" -> scopedPermission(scope, a + ".project_id", "log:view");
            case "rule_execution_log_identity" -> "EXISTS (SELECT 1 FROM rule_execution_log sec_log WHERE sec_log.id = " + a
                    + ".log_id AND sec_log.create_time = " + a + ".log_create_time AND " + executionLog(scope, "sec_log") + ")";
            case "rule_execution_checkpoint" -> "EXISTS (SELECT 1 FROM rule_execution_state sec_state WHERE sec_state.root_trace_id = " + a + ".root_trace_id AND " + project(scope, "sec_state.project_id", true) + ")";
            // 汇总刷新为系统维护任务，不包含业务明细；响应另行去除全库计数和其他发起人身份。
            case "rule_billing_summary_task", "rule_billing_summary_lock" -> null;
            // 公共默认仅含共享映射；字段本身仍经变量/对象查询逐项授权，修改入口须有 system:edit。
            case "rule_dashboard_default" -> Boolean.TRUE.equals(mutation.get()) && !scope.globalCan("system:edit") ? "1 = 0" : null;
            case "offline_transfer_log" -> transferLog(scope, a, depth);
            case "console_operation_log" -> "(" + (scope.globalCan("system:audit") ? a + ".module = 'system' OR " : "")
                    + ledgerVisitPredicate(scope, a) + a + ".target_type IN ('CONSOLE_USER','CONSOLE_ROLE','ACCOUNT','ROLE') OR ("
                    + a + ".target_id REGEXP '^[0-9]+$' AND " + resource(scope, a + ".target_type", a + ".target_id", depth) + "))";
            case "console_security_audit_log" -> scope.globalCan("account:audit") ? null : "1 = 0";
            default -> table.startsWith("rule_") || table.startsWith("governance_") || table.startsWith("artifact_") ? "1 = 0" : null;
        };
    }

    private String ledgerVisitPredicate(ConsoleProjectAccess.Scope scope, String a) {
        if (scope.userId() == null || !scope.can("ledger:view")) return "";
        // 首页及新建页没有文章归属。受限账号只查看自己无项目参数的入口事件。
        String entry = a + ".target_type = 'LEDGER_PAGE' AND " + a + ".target_id IS NULL";
        if (!scope.allProjectPermission("ledger:view")) {
            entry += " AND " + a + ".user_id = " + scope.userId()
                    + " AND " + a + ".page_path IN ('/ledger','/ledger/articles/new')";
        }
        String project = a + ".target_type = 'LEDGER_PROJECT' AND " + a + ".target_id REGEXP '^[0-9]+$' AND "
                + projectPermission(scope, a + ".target_id", "ledger:view");
        String article = a + ".target_type = 'LEDGER_ARTICLE' AND " + a + ".target_id REGEXP '^[0-9]+$'"
                + " AND EXISTS (SELECT 1 FROM rule_ledger_article audit_article WHERE audit_article.id = " + a + ".target_id AND ("
                + ledgerPredicate(scope, "rule_ledger_article", "audit_article", 0) + "))";
        return "(" + a + ".module = 'ledger' AND " + a + ".action = 'PAGE_VIEW' AND (("
                + entry + ") OR (" + project + ") OR (" + article + "))) OR ";
    }

    private String project(ConsoleProjectAccess.Scope s, String column, boolean global) {
        String assigned = s.allProjects() ? column + " > 0" : s.projectIds().isEmpty() ? "1 = 0" : column + " IN (" + s.projectSql() + ")";
        return global ? "(" + column + " IS NULL OR " + column + " = 0 OR " + assigned + ")" : "(" + assigned + ")";
    }

    /** 台账属于团队内容；私有草稿和待检素材不借用审计日志跨账户权限。 */
    private String ledgerPredicate(ConsoleProjectAccess.Scope s, String table, String a, int depth) {
        if (s.userId() == null) return "1 = 0";
        String permission = Boolean.TRUE.equals(mutation.get()) ? "ledger:edit" : "ledger:view";
        String own = a + ".owner_user_id = " + s.userId();
        String shared = "((" + a + ".scope = 'GLOBAL' AND " + (s.globalCan(permission) ? "1 = 1" : "1 = 0")
                + ") OR (" + a + ".scope = 'PROJECT' AND " + projectPermission(s, a + ".project_id", permission) + "))";
        String privateAccess = "(" + a + ".scope = 'PRIVATE' AND " + own + " AND " + (s.can(permission) ? "1 = 1" : "1 = 0") + ")";
        return switch (table) {
            case "rule_ledger_article" -> a + ".deleted = 0 AND ((" + privateAccess + " AND " + ledgerDraftScope(s, a, permission) + ") OR " + shared + ")";
            case "rule_ledger_asset" -> "(" + privateAccess + " OR (" + a + ".scan_status = 'CLEAN' AND " + shared + "))";
            case "rule_ledger_event" -> a + ".deleted = 0 AND " + shared;
            case "rule_ledger_revision" -> shared + " AND EXISTS (SELECT 1 FROM rule_ledger_article ledger_parent WHERE ledger_parent.id = " + a + ".article_id AND (" + ledgerPredicate(s, "rule_ledger_article", "ledger_parent", depth + 1) + "))";
            case "rule_ledger_revision_reference" -> child(s, a, "revision_id", "rule_ledger_revision", depth);
            case "rule_ledger_draft_reference" -> "EXISTS (SELECT 1 FROM rule_ledger_article ledger_draft WHERE ledger_draft.id = " + a
                    + ".article_id AND ledger_draft.deleted = 0 AND ledger_draft.draft_user_id = " + s.userId()
                    + " AND " + ledgerDraftScope(s, "ledger_draft", permission) + " AND (" + ledgerPredicate(s, "rule_ledger_article", "ledger_draft", depth + 1) + "))";
            default -> "1 = 0";
        };
    }
    private String ledgerDraftScope(ConsoleProjectAccess.Scope s, String a, String permission) {
        return "((" + a + ".draft_scope IS NULL OR " + a + ".draft_scope='PRIVATE') OR (" + a + ".draft_scope='GLOBAL' AND "
                + (s.globalCan(permission) ? "1 = 1" : "1 = 0") + ") OR (" + a + ".draft_scope='PROJECT' AND " + projectPermission(s, a + ".draft_project_id", permission) + "))";
    }
    public String ledgerDraftPredicate(String alias) {
        var scope = access.current();
        if (scope == null || scope.userId() == null) return "1 = 0";
        return "(" + alias + ".draft_user_id=" + scope.userId() + " AND " + ledgerDraftScope(scope, alias, "ledger:view") + ")";
    }

    private String transferLog(ConsoleProjectAccess.Scope scope, String alias, int depth) {
        if (scope.userId() == null) return "1 = 0";
        String r = "sec_transfer";
        String allowed = "((" + r + ".resource_id > 0 AND " + resource(scope, r + ".resource_type", r + ".resource_id", depth + 1)
                + ") OR (" + r + ".resource_id = 0 AND " + resourceModule(scope, r + ".resource_type", "0") + ")"
                + (scope.globalCan("transfer:view") ? " OR " + r + ".resource_type = 'TRANSFER_LOG'" : "") + ")";
        return "EXISTS (SELECT 1 FROM offline_transfer_log_resource " + r + " WHERE " + r + ".log_id = " + alias + ".id)"
                + " AND NOT EXISTS (SELECT 1 FROM offline_transfer_log_resource " + r + " WHERE " + r + ".log_id = " + alias + ".id AND NOT " + allowed + ")";
    }

    private String executionLog(ConsoleProjectAccess.Scope scope, String alias) {
        String owner = recordOwner(scope, "rule_execution_log", alias);
        return (owner == null ? "" : "(" + owner + ") AND ") + "(" + projectPermission(scope, alias + ".execution_project_id", "log:view")
                + (scope.globalCan("log:view") ? " OR ((" + alias + ".execution_project_id IS NULL OR " + alias + ".execution_project_id = 0) AND ("
                + alias + ".project_code IS NULL OR " + alias + ".project_code = ''))" : "")
                + ")";
    }

    private String logScope(ConsoleProjectAccess.Scope scope, String alias, String permission) {
        return "(" + projectPermission(scope, alias + ".project_id", permission)
                + (scope.globalCan(permission) ? " OR ((" + alias + ".project_id IS NULL OR " + alias + ".project_id = 0) AND ("
                + alias + ".project_code IS NULL OR " + alias + ".project_code = ''))" : "")
                + ")";
    }

    public static String moduleForTable(String table) { return "rule_project".equals(table) ? "project" : ROOT_MODULES.get(table); }

    private String child(ConsoleProjectAccess.Scope s, String alias, String column, String parent, int depth) {
        String p = "sec_parent_" + depth;
        return "EXISTS (SELECT 1 FROM " + parent + " " + p + " WHERE " + p + ".id = " + alias + "." + column + " AND (" + predicate(s, parent, p, depth + 1) + "))";
    }

    private String resourceModule(ConsoleProjectAccess.Scope s, String type, String projectId) {
        return "(" + RESOURCE_TABLES.keySet().stream().sorted().map(value -> "(" + type + " = '" + value + "' AND "
                + scopedPermission(s, projectId, ConsoleAccessGuard.resourceModule(value) + ":view")
                + mutationPermission(s, projectId, ConsoleAccessGuard.resourceModule(value), true) + ")").collect(java.util.stream.Collectors.joining(" OR ")) + ")";
    }

    private String mutationPermission(ConsoleProjectAccess.Scope scope, String column, String module, boolean global) {
        if (!Boolean.TRUE.equals(mutation.get())) return "";
        String code = actionPermission(module);
        return " AND " + (global ? scopedPermission(scope, column, code) : projectPermission(scope, column, code));
    }

    private String scopedPermission(ConsoleProjectAccess.Scope scope, String column, String permission) {
        return "(" + (scope.globalCan(permission) ? "(" + column + " IS NULL OR " + column + " = 0)" : "1 = 0")
                + " OR " + projectPermission(scope, column, permission) + ")";
    }

    private String projectPermission(ConsoleProjectAccess.Scope scope, String column, String permission) {
        boolean inherited = scope.permissions().contains(permission);
        String effect = inherited ? "DENY" : "ALLOW";
        String ids = scope.projectOverrides().entrySet().stream().filter(entry -> effect.equals(entry.getValue().get(permission)))
                .map(Map.Entry::getKey).sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String allowed = ids.isEmpty() ? (inherited ? "1 = 1" : "1 = 0") : column + (inherited ? " NOT IN (" : " IN (") + ids + ")";
        return "(" + project(scope, column, false) + " AND (" + allowed + "))";
    }

    private String projectLabels(ConsoleProjectAccess.Scope scope, String column) {
        return "(" + ConsolePermissionCodes.allCodes().stream().filter(code -> code.endsWith(":view") && ConsoleProjectAccess.isProjectPermission(code))
                .sorted().map(code -> projectPermission(scope, column, code)).collect(java.util.stream.Collectors.joining(" OR ")) + ")";
    }

    private boolean projectContentRequest() {
        if (!(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes attrs)) return false;
        String uri = attrs.getRequest().getRequestURI();
        return (uri.equals("/api/rule/project") || uri.startsWith("/api/rule/project/")) && !uri.startsWith("/api/rule/project/options");
    }

    private String resource(ConsoleProjectAccess.Scope s, String type, String id, int depth) {
        String p = "sec_resource_" + depth;
        return "(" + RESOURCE_TABLES.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> "(" + type + " = '" + entry.getKey()
                + "' AND EXISTS (SELECT 1 FROM " + entry.getValue() + " " + p + " WHERE " + p + ".id = " + id + " AND ("
                + predicate(s, entry.getValue(), p, depth + 1) + ")))" ).collect(java.util.stream.Collectors.joining(" OR ")) + ")";
    }
}
