package com.hengshucredit.rule.server.security;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.lang.reflect.Type;
import java.util.Map;

/** 菜单可由任一项目权限打开；真实操作还必须在本次目标资源所属项目中获准。 */
@RestControllerAdvice
public class ConsoleTargetAuthorization extends RequestBodyAdviceAdapter implements HandlerInterceptor {
    private final ConsoleProjectAccess projects;
    private final ConsoleResourceAccess resources;
    public ConsoleTargetAuthorization(ConsoleProjectAccess projects, ConsoleResourceAccess resources) { this.projects = projects; this.resources = resources; }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        authorize(request, null);
        return true;
    }
    @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        if (projects.current() != null && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                && body != null && !(body instanceof java.util.Collection<?>) && !(body instanceof byte[])) {
            authorize(attrs.getRequest(), targetFields(body));
        }
        return body;
    }

    private JSONObject targetFields(Object body) {
        JSONObject target = new JSONObject();
        var bean = body instanceof Map<?, ?> ? null : new org.springframework.beans.BeanWrapperImpl(body);
        for (String key : java.util.List.of("id", "projectId", "scope", "definitionId", "variableId", "modelId", "functionId", "experimentId", "objectId", "fieldId", "listId", "apiConfigId", "datasourceId", "config")) {
            Object value = body instanceof Map<?, ?> map ? map.get(key) : bean.isReadableProperty(key) ? bean.getPropertyValue(key) : null;
            if (value != null) target.put(key, key.equals("config") ? targetFields(value) : value);
        }
        return target;
    }

    private void authorize(HttpServletRequest request, JSONObject body) {
        if (projects.current() == null) return;
        String code = (String) request.getAttribute(ConsolePermissionInterceptor.AUTHORIZED_PERMISSION);
        Object raw = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        Map<?, ?> path = raw instanceof Map<?, ?> map ? map : Map.of();
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/rule/governance/requests/") && code != null && (code.equals("approval:approve") || code.equals("approval:submit"))) {
            Long id = id(path, request, null, "id");
            if (id != null) resources.requireGovernancePermission(id, code);
            return;
        }
        if (uri.matches("/api/rule/governance/rules/[0-9]+/delete")) {
            resources.require("RULE", id(path, request, body, "id"), "edit"); return;
        }
        if (code == null && path.get("type") != null && path.get("id") != null) {
            String type = String.valueOf(path.get("type"));
            String action = "GET".equals(request.getMethod()) && !uri.endsWith("/deletion-impact") ? "view" : "edit";
            resources.require(type, Long.valueOf(path.get("id").toString()), action);
            return;
        }
        boolean primaryDetail = uri.matches("/api/rule/(project|definition|variable|dataobject|model|function|database|sftp|list|experiment)/[0-9]+")
                || uri.matches("/api/rule/datasource/(api-config/)?[0-9]+") || uri.matches("/api/rule/dataobject/field/[0-9]+");
        if (code == null || !ConsoleProjectAccess.isProjectPermission(code) || (code.endsWith(":view") && !primaryDetail)
                || (code.startsWith("approval:") && !code.equals("approval:submit")) || code.startsWith("transfer:")) return;
        String namespace = uri.substring("/api/rule/".length()).split("/", 2)[0];
        String type = switch (namespace) {
            case "project" -> "PROJECT";
            case "definition", "expression" -> "RULE";
            case "variable" -> "VARIABLE";
            case "field-validation" -> "FIELD_VALIDATION";
            case "dataobject" -> uri.contains("/field/") ? "DATA_FIELD" : "DATA_OBJECT";
            case "model" -> "MODEL";
            case "function" -> "FUNCTION";
            case "database" -> "DATABASE";
            case "sftp" -> "SFTP";
            case "datasource" -> uri.contains("/api-config") ? "EXTERNAL_API" : "EXTERNAL_DATASOURCE";
            case "list" -> "LIST_LIBRARY";
            case "experiment" -> "EXPERIMENT";
            case "billing" -> "BILLING_CONFIG";
            default -> null;
        };
        if (type == null) return;
        if (body != null && uri.contains("/api-config/trials") && body.getJSONObject("config") != null) body = body.getJSONObject("config");
        String namedId = switch (type) {
            case "PROJECT" -> "projectId"; case "RULE" -> "definitionId"; case "VARIABLE" -> "variableId";
            case "MODEL" -> "modelId"; case "FUNCTION" -> "functionId"; case "EXPERIMENT" -> "experimentId";
            case "DATA_OBJECT" -> "objectId"; case "DATA_FIELD" -> "fieldId"; case "LIST_LIBRARY" -> "listId";
            case "FIELD_VALIDATION" -> "validationId";
            case "EXTERNAL_API" -> "apiConfigId"; default -> "datasourceId";
        };
        Long resourceId = id(path, request, body, "id", namedId);
        if (resourceId == null && type.equals("MODEL") && path.containsKey("versionId")) {
            resourceId = id(path, request, body, "versionId"); type = "MODEL_VERSION";
        }
        if (resourceId == null && type.equals("EXTERNAL_API")) {
            resourceId = id(path, request, body, "datasourceId"); type = "EXTERNAL_DATASOURCE";
        }
        if (resourceId != null && resourceId > 0) {
            resources.requirePermission(type, resourceId, code);
            if (code.equals("approval:submit")) { resources.require(type, resourceId, "edit"); resources.require(type, resourceId, "submit"); }
        } else if (body != null && !"PROJECT".equals(type) && !uri.contains("/trials/")) {
            Long projectId = "GLOBAL".equals(body.getString("scope")) ? null : body.getLong("projectId");
            projects.requirePermission(code, projectId);
        }
    }

    private Long id(Map<?, ?> path, HttpServletRequest request, JSONObject body, String... names) {
        for (String name : names) {
            Object value = path.get(name);
            if (value == null) value = request.getParameter(name);
            if (value == null && body != null) value = body.get(name);
            if (value != null && value.toString().matches("[0-9]+")) return Long.valueOf(value.toString());
        }
        return null;
    }
}
