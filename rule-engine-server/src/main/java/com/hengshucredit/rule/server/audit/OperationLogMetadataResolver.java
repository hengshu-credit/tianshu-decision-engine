package com.hengshucredit.rule.server.audit;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.ConsoleOperationLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import java.util.*;

/** 只取对象身份和版本白名单列，绝不读取配置正文、快照或模型文件。 */
@Component
@RequiredArgsConstructor
public class OperationLogMetadataResolver {
    private final Map<String, BaseMapper<?>> mappers;

    public void request(ConsoleOperationLog log, HttpServletRequest request, Object body) {
        String path = request.getRequestURI().replaceFirst("^/api/rule/", "");
        if (path.startsWith("transfer/")) {
            log.setTargetType("TRANSFER_PACKAGE");
            log.setTargetName("迁移配置包");
            Object roots = body instanceof Collection<?> ? body : property(body, "roots");
            if (roots instanceof Collection<?> items) {
                List<String> identities = new ArrayList<>();
                for (Object item : items) {
                    if (identities.size() == 3) break;
                    String type = text(property(item, "resourceType"), 32), id = identifier(property(item, "resourceId"));
                    if (type != null && source(type) != null && id != null) identities.add(type + ":" + id);
                }
                log.setTargetId(ConsoleOperationAudit.limit(String.join(",", identities), 128));
                log.setTargetName("迁移配置包（" + items.size() + "个根对象）");
            }
            if (request instanceof org.springframework.web.multipart.MultipartHttpServletRequest multipart) {
                var file = multipart.getFile("file");
                if (file != null) log.setTargetName(text(file.getOriginalFilename(), 255));
            }
            return;
        }
        String type = switch (path.split("/", 2)[0]) {
            case "project" -> "PROJECT";
            case "definition", "expression" -> "RULE";
            case "artifact" -> "DECISION_ARTIFACT";
            case "variable" -> "VARIABLE";
            case "field-validation" -> "FIELD_VALIDATION";
            case "dataobject" -> path.startsWith("dataobject/field") ? "DATA_OBJECT_FIELD" : "DATA_OBJECT";
            case "list" -> "LIST_LIBRARY";
            case "model" -> "MODEL";
            case "function" -> "FUNCTION";
            case "database" -> "DATABASE";
            case "datasource" -> path.startsWith("datasource/api-config") ? "EXTERNAL_API" : "EXTERNAL_DATASOURCE";
            case "experiment" -> "EXPERIMENT";
            case "billing" -> "BILLING_CONFIG";
            case "governance" -> "APPROVAL_REQUEST";
            case "console" -> path.startsWith("console/accounts") ? "CONSOLE_USER" : path.startsWith("console/roles") ? "CONSOLE_ROLE" : null;
            default -> null;
        };
        if (type == null) return;
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (path.startsWith("governance/rules/")) { type = "RULE"; log.setModule("rule"); }
        if (path.startsWith("governance/resources/")) {
            String resourceType = text(property(variables, "type"), 64);
            if (source(resourceType) != null) type = resourceType;
        }
        if ((property(variables, "fieldId") != null || path.matches("model/(inputField|outputField)/.*")) && (type.equals("RULE") || type.equals("MODEL"))) {
            type += path.toLowerCase(Locale.ROOT).contains("output") ? "_OUTPUT_FIELD" : "_INPUT_FIELD";
        }
        log.setTargetType(type);
        String targetId = null;
        for (String key : List.of("fieldId", "definitionId", "ruleId", "id", "variableId", "modelId", "functionId", "experimentId", "objectId", "listId", "resourceId", "artifactId")) {
            targetId = identifier(property(variables, key));
            if (targetId != null) break;
        }
        if (targetId == null && "PROJECT".equals(type)) targetId = identifier(property(variables, "projectId"));
        if (targetId == null) for (String key : List.of("refId", "ownerId", "resourceId")) {
            targetId = identifier(request.getParameter(key));
            if (targetId != null) break;
        }
        if (targetId == null) targetId = identifier(property(body, "id"));
        if (targetId == null) targetId = identifier(property(body, "definitionId"));
        if (targetId != null) log.setTargetId(targetId);
        String selectedVersion = version(variables);
        if (selectedVersion == null) selectedVersion = version(Map.of(
                "version", Objects.toString(request.getParameter("version"), ""),
                "versionId", Objects.toString(request.getParameter("versionId"), "")));
        describe(log, body);
        if (selectedVersion == null) selectedVersion = sourceVersion(property(body, "executionSource"));
        if (selectedVersion == null && "RULE".equals(type)) selectedVersion = sourceVersion(ConsoleOperationPage.parameters(log.getPagePath()));
        if (selectedVersion != null) log.setTargetVersion(selectedVersion);
    }

    public void page(ConsoleOperationLog log, ConsoleOperationPage page) {
        log.setTargetType(page.resourceType());
        log.setTargetId(page.resourceId());
        log.setTargetName(page.resourceId() == null ? page.label() : null);
        log.setTargetVersion(sourceVersion(ConsoleOperationPage.parameters(log.getPagePath())));
        if (log.getTargetVersion() == null) log.setTargetVersion(version(ConsoleOperationPage.parameters(log.getPagePath())));
    }

    public void describe(ConsoleOperationLog log, Object data) {
        if (data instanceof com.hengshucredit.rule.server.governance.GovernanceRequestDetail detail) data = detail.request();
        if (data == null || data instanceof Collection<?>) return;
        if (data instanceof com.hengshucredit.rule.server.governance.GovernanceRequestView approval &&
                (approval.resourceId() == null || approval.resourceId() <= 0)) {
            log.setTargetType("APPROVAL_REQUEST"); log.setTargetId(String.valueOf(approval.id())); log.setTargetName(approval.requestNo());
            return;
        }
        Object revision = property(data, "revision");
        if (revision != null) data = revision;
        if ("RULE".equals(log.getTargetType())) {
            String definitionId = identifier(property(data, "definitionId"));
            if (definitionId != null) log.setTargetId(definitionId);
        }
        String resourceType = text(property(data, "resourceType"), 64);
        String resourceId = identifier(property(data, "resourceId"));
        if (resourceType != null && source(resourceType) != null && resourceId != null && !"0".equals(resourceId)) {
            log.setTargetType(resourceType); log.setTargetId(resourceId);
        }
        if (log.getTargetId() == null) log.setTargetId(identifier(property(data, "id")));
        for (String name : List.of("ruleName", "projectName", "varLabel", "objectLabel", "modelName", "funcName", "apiName", "datasourceName", "listName", "experimentName", "displayName", "username", "roleName", "fieldLabel", "validationName", "billingName")) {
            String value = text(property(data, name), 255);
            if (value != null) { log.setTargetName(value); break; }
        }
        String version = version(data);
        if (version == null) version = version(property(data, "executionIdentity"));
        if (version == null) version = version(property(property(data, "execution"), "executionIdentity"));
        if (version != null) log.setTargetVersion(version);
    }

    public void complete(ConsoleOperationLog log) {
        Source source = source(log.getTargetType());
        if (source == null || log.getTargetId() == null || !log.getTargetId().matches("[1-9][0-9]{0,18}")) return;
        if (log.getTargetName() == null || log.getTargetVersion() == null && source.version() != null) {
            List<String> columns = new ArrayList<>(List.of("id"));
            if (source.name() != null) columns.add(source.name() + " AS name");
            if (source.version() != null) columns.add(source.version() + " AS version");
            Map<String, Object> row = select(source.mapper(), new QueryWrapper<>().select(columns).eq("id", log.getTargetId()));
            if (log.getTargetName() == null) log.setTargetName(text(row.get("name"), 255));
            if (log.getTargetVersion() == null) {
                String value = version(row);
                log.setTargetVersion(value != null && "RULE_REVISION".equals(log.getTargetType()) ? "修订 " + value.substring(1) : value);
            }
        }
        if (log.getTargetVersion() == null && !log.getTargetType().startsWith("CONSOLE_") && !"RULE_REVISION".equals(log.getTargetType()) && !"APPROVAL_REQUEST".equals(log.getTargetType())) {
            Map<String, Object> row = select("governedResourceMapper", new QueryWrapper<>()
                    .select("effective_version_no AS version").eq("resource_type", log.getTargetType()).eq("resource_id", log.getTargetId()));
            log.setTargetVersion(version(row));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> select(String name, QueryWrapper<Object> query) {
        BaseMapper<Object> mapper = (BaseMapper<Object>) mappers.get(name);
        if (mapper == null) return Map.of();
        List<Map<String, Object>> rows = mapper.selectMaps(query.last("LIMIT 1"));
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    static Object property(Object bean, String name) {
        if (bean == null || bean instanceof CharSequence || bean instanceof Number || bean instanceof Collection<?> || bean.getClass().isArray()) return null;
        if (bean instanceof Map<?, ?> map) return map.get(name);
        var wrapper = new BeanWrapperImpl(bean);
        return wrapper.isReadableProperty(name) ? wrapper.getPropertyValue(name) : null;
    }

    static String text(Object value, int limit) {
        if (!(value instanceof CharSequence) && !(value instanceof Number)) return null;
        String text = value.toString().strip();
        return text.isEmpty() ? null : text.substring(0, Math.min(limit, text.length()));
    }

    private static String identifier(Object value) {
        String text = text(value, 128);
        return text != null && text.matches("[A-Za-z0-9_-]{1,128}") ? text : null;
    }

    private static String version(Object value) {
        for (String key : List.of("versionNo", "businessVersion", "currentVersion", "revisionNo", "version", "modelVersion", "versionId", "revisionId")) {
            Object raw = property(value, key);
            if (!(raw instanceof Number) && !(raw instanceof String)) continue;
            String text = raw.toString();
            if (!text.matches("[A-Za-z0-9_.:-]{1,48}")) continue;
            return (key.endsWith("Id") ? "ID " : key.equals("revisionNo") ? "修订 " : "v") + text;
        }
        return null;
    }

    private static String sourceVersion(Object value) {
        String id = identifier(property(value, "sourceId"));
        if (id == null || !id.matches("[0-9]{1,19}")) return null;
        return switch (Objects.toString(property(value, "sourceType"), "")) {
            case "REVISION" -> "修订 ID " + id;
            case "VERSION" -> "版本 ID " + id;
            case "WORKSPACE" -> "工作区 ID " + id;
            default -> null;
        };
    }

    private Source source(String type) {
        if (type == null) return null;
        return switch (type) {
            case "PROJECT" -> new Source("ruleProjectMapper", "project_name", null);
            case "RULE" -> new Source("ruleDefinitionMapper", "rule_name", "current_version");
            case "RULE_REVISION" -> new Source("ruleRevisionMapper", null, "revision_no");
            case "VARIABLE" -> new Source("ruleVariableMapper", "var_label", null);
            case "FIELD_VALIDATION" -> new Source("ruleFieldValidationMapper", "validation_name", null);
            case "RULE_INPUT_FIELD" -> new Source("ruleDefinitionInputFieldMapper", "field_label", null);
            case "RULE_OUTPUT_FIELD" -> new Source("ruleDefinitionOutputFieldMapper", "field_label", null);
            case "MODEL_INPUT_FIELD" -> new Source("ruleModelInputFieldMapper", "field_label", null);
            case "MODEL_OUTPUT_FIELD" -> new Source("ruleModelOutputFieldMapper", "field_label", null);
            case "DATA_OBJECT" -> new Source("ruleDataObjectMapper", "object_label", null);
            case "DATA_OBJECT_FIELD" -> new Source("ruleDataObjectFieldMapper", "var_label", null);
            case "LIST_LIBRARY" -> new Source("ruleListLibraryMapper", "list_name", null);
            case "MODEL" -> new Source("ruleModelMapper", "model_name", "current_version");
            case "FUNCTION" -> new Source("ruleFunctionMapper", "func_name", null);
            case "DATABASE" -> new Source("ruleDbDatasourceMapper", "datasource_name", null);
            case "EXTERNAL_API" -> new Source("ruleExternalApiConfigMapper", "api_name", null);
            case "EXTERNAL_DATASOURCE" -> new Source("ruleExternalDatasourceMapper", "datasource_name", null);
            case "EXPERIMENT" -> new Source("ruleExperimentMapper", "experiment_name", null);
            case "BILLING_CONFIG" -> new Source("ruleBillingConfigMapper", "billing_name", null);
            case "APPROVAL_REQUEST" -> new Source("governanceApprovalRequestMapper", "request_no", null);
            case "CONSOLE_USER" -> new Source("consoleUserMapper", "display_name", null);
            case "CONSOLE_ROLE" -> new Source("consoleRoleMapper", "role_name", null);
            default -> null;
        };
    }

    private record Source(String mapper, String name, String version) { }
}
