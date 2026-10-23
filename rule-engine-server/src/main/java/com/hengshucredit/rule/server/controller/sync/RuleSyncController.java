package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService;
import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.service.RuleExecuteService;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleFunctionService;
import com.hengshucredit.rule.server.service.RuleIdempotencyService;
import com.hengshucredit.rule.server.service.RuleVariableService;
import com.hengshucredit.rule.server.openapi.OpenApiContract;
import com.hengshucredit.rule.server.openapi.OpenApiContractCodec;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rule/sync")
public class RuleSyncController {
    @Resource private com.hengshucredit.rule.server.service.RuleVersionBindingService versionBindingService;
    @Resource private com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService artifactSnapshotService;

    @GetMapping("/by-id/{definitionId}")
    public ApiResponse<RulePublished> getById(@PathVariable Long definitionId,
                                  @RequestParam(required = false) Long versionBindingId, HttpServletRequest request) {
        ProjectScope scope = resolveProjectScope(request);
        if (scope == null) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.TOKEN_INVALID, "Unauthorized project token");
        RulePublished latest = publishedMapper.selectOne(appendProjectScope(new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId, definitionId).eq(RulePublished::getStatus, 1), scope));
        if (latest == null) return ApiResponse.fail(PlatformCode.RULE_NOT_FOUND, "Rule not found");
        RulePublished selected = versionBindingService.resolvePublished(latest, versionBindingId);
        return ApiResponse.ok(withOutputScriptNames(selected));
    }

    @Resource
    private RulePublishedMapper publishedMapper;

    @Resource
    private RuleFunctionService functionService;

    @Resource
    private RuleExecuteService executeService;

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleVariableService variableService;

    @Resource
    private RuleIdempotencyService idempotencyService;

    @GetMapping("/{ruleCode}")
    public ApiResponse<RulePublished> getByCode(@PathVariable String ruleCode, HttpServletRequest request) {
        ProjectScope scope = resolveProjectScope(request);
        if (scope == null) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.TOKEN_INVALID, "Unauthorized project token");
        }
        RulePublished published = publishedMapper.selectOne(
                appendProjectScope(new LambdaQueryWrapper<RulePublished>()
                        .eq(RulePublished::getRuleCode, ruleCode)
                        .eq(RulePublished::getStatus, 1), scope));
        return published == null ? ApiResponse.fail(PlatformCode.RULE_NOT_FOUND, null) : ApiResponse.ok(withOutputScriptNames(published));
    }

    @GetMapping("/all")
    public ApiResponse<List<RulePublished>> getAll(HttpServletRequest request) {
        ProjectScope scope = resolveProjectScope(request);
        if (scope == null) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.TOKEN_INVALID, "Unauthorized project token");
        }
        List<RulePublished> list = publishedMapper.selectList(
                appendProjectScope(new LambdaQueryWrapper<RulePublished>()
                        .eq(RulePublished::getStatus, 1), scope));
        for (int index = 0; index < list.size(); index++) list.set(index, withOutputScriptNames(list.get(index)));
        return ApiResponse.ok(list);
    }

    @GetMapping("/versions")
    public ApiResponse<Map<String, Integer>> getVersions(HttpServletRequest request) {
        ProjectScope scope = resolveProjectScope(request);
        if (scope == null) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.TOKEN_INVALID, "Unauthorized project token");
        }
        List<RulePublished> list = publishedMapper.selectList(
                appendProjectScope(new LambdaQueryWrapper<RulePublished>()
                        .select(RulePublished::getRuleCode, RulePublished::getVersion)
                        .eq(RulePublished::getStatus, 1), scope));
        Map<String, Integer> versions = list.stream()
                .collect(Collectors.toMap(RulePublished::getRuleCode, RulePublished::getVersion));
        return ApiResponse.ok(versions);
    }

    @PostMapping("/execute/{ruleCode}")
    public ApiResponse<RuleResult> execute(@PathVariable String ruleCode,
                                 @RequestBody Map<String, Object> body,
                                 HttpServletRequest request) {
        return executeBody(ruleCode, body, request);
    }

    @PostMapping(value = "/execute/{ruleCode}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RuleResult> executeMultipart(@PathVariable String ruleCode,
                                          MultipartHttpServletRequest request) {
        return executeBody(ruleCode, buildMultipartBody(request.getParameterMap(), request.getFileMap()), request);
    }

    private ApiResponse<RuleResult> executeBody(String ruleCode, Map<String, Object> body, HttpServletRequest request) {
        ProjectScope scope = resolveProjectScope(request);
        if (scope == null) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.TOKEN_INVALID, "Unauthorized project token");
        }
        RulePublished published = publishedMapper.selectOne(
                appendProjectScope(new LambdaQueryWrapper<RulePublished>()
                        .eq(RulePublished::getRuleCode, ruleCode)
                        .eq(RulePublished::getStatus, 1), scope));
        if (published == null) {
            return ApiResponse.fail(PlatformCode.RULE_NOT_FOUND, "Rule not found");
        }
        Long executionProjectId = scope.global ? resolveDefinitionProjectId(published) : scope.projectId;
        if (executionProjectId == null || (!scope.global && executionProjectId <= 0)) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "Rule project not found");
        }
        com.hengshucredit.rule.model.RuleExecutionRequest input;
        try { input = com.hengshucredit.rule.model.RuleExecutionRequest.fromBody(body); }
        catch (com.hengshucredit.rule.model.PlatformException error) { return ApiResponse.fail(error); }
        Map<String, Object> params = input.getParams();
        String clientAppName = input.getClientAppName();
        boolean traceEnabled = input.getTraceEnabled() == null || input.getTraceEnabled();
        RuleIdempotencyService.Decision idempotency = idempotencyService == null
                ? null : idempotencyService.begin(published, executionProjectId, params);
        if (idempotency == null) {
            return ApiResponse.ok(executeService.executePublished(published, params, executionProjectId, clientAppName,
                    ProjectAuthContext.from(request), traceEnabled, traceEnabled));
        }
        if (idempotency.getStatus() == RuleIdempotencyService.Status.COMPLETED) return ApiResponse.ok(idempotency.getResult());
        if (idempotency.getStatus() == RuleIdempotencyService.Status.CONFLICT) {
            ApiResponse<RuleResult> response = ApiResponse.fail(PlatformCode.IDEMPOTENCY_CONFLICT, null);
            response.setData(idempotencyStatus(idempotency, "CONFLICT"));
            return response;
        }
        if (idempotency.getStatus() == RuleIdempotencyService.Status.IN_PROGRESS) {
            ApiResponse<RuleResult> response = ApiResponse.fail(PlatformCode.EXECUTION_IN_PROGRESS, null);
            response.setData(idempotencyStatus(idempotency, "IN_PROGRESS"));
            return response;
        }
        try {
            RuleResult result = idempotencyService.withDecision(idempotency, () -> executeService.executePublished(
                    published, params, executionProjectId, clientAppName,
                    ProjectAuthContext.from(request), traceEnabled, traceEnabled));
            idempotencyService.complete(idempotency, result, null);
            return ApiResponse.ok(result);
        } catch (RuntimeException error) {
            idempotencyService.release(idempotency, error.getMessage());
            throw error;
        }
    }

    static Map<String, Object> buildMultipartBody(Map<String, String[]> fields,
                                                   Map<String, MultipartFile> files) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach((path, values) -> {
                Object value = values == null || values.length == 0
                        ? ""
                        : values.length == 1 ? values[0] : Arrays.asList(values);
                putNestedValue(body, path, value);
            });
        }
        if (files != null) {
            files.forEach((path, file) -> putNestedValue(body, path, file));
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private static void putNestedValue(Map<String, Object> target, String path, Object value) {
        if (path == null || path.trim().isEmpty()) {
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (index == parts.length - 1) {
                current.put(part, value);
            } else {
                Object child = current.get(part);
                if (!(child instanceof Map)) {
                    child = new LinkedHashMap<String, Object>();
                    current.put(part, child);
                }
                current = (Map<String, Object>) child;
            }
        }
    }

    /**
     * 同步项目下所有已启用的函数定义（JAVA/BEAN/SCRIPT），供客户端 SDK 拉取并注册
     */
    @GetMapping("/functions/{projectId}")
    public ApiResponse<List<RuleFunction>> syncFunctions(@PathVariable Long projectId, HttpServletRequest request) {
        ProjectScope scope = resolveProjectScope(request);
        if (scope == null) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.TOKEN_INVALID, "Unauthorized project token");
        }
        if (!scope.global && !isAuthorizedProject(projectId, scope.projectId)) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PERMISSION_DENIED, "Project token does not match requested project");
        }
        return ApiResponse.ok(functionService.listByProject(projectId));
    }

    @GetMapping("/functions/all")
    public ApiResponse<List<RuleFunction>> syncAllFunctions(HttpServletRequest request) {
        ProjectScope scope = resolveProjectScope(request);
        if (scope == null || !scope.global) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PERMISSION_DENIED, "Global access is required");
        }
        return ApiResponse.ok(functionService.listAllEnabled());
    }

    private LambdaQueryWrapper<RulePublished> appendProjectScope(LambdaQueryWrapper<RulePublished> wrapper,
                                                                 ProjectScope scope) {
        if (scope.global) {
            return wrapper;
        }
        wrapper.and(w -> {
            boolean hasProjectCode = scope.projectCode != null && !scope.projectCode.isEmpty();
            if (hasProjectCode) {
                w.eq(RulePublished::getProjectCode, scope.projectCode);
                if (scope.projectId != null) {
                    w.or().exists(buildLinkedGlobalRuleExistsSql(scope.projectId));
                }
            } else if (scope.projectId != null) {
                w.exists(buildLinkedGlobalRuleExistsSql(scope.projectId));
            } else {
                w.apply("1 = 0");
            }
        });
        return wrapper;
    }

    private static String buildLinkedGlobalRuleExistsSql(Long projectId) {
        return "SELECT 1 FROM rule_definition_ref rdr " +
                "WHERE rdr.definition_id = rule_published.definition_id " +
                "AND rdr.project_id = " + projectId;
    }

    private RulePublished withOutputScriptNames(RulePublished published) {
        if (published == null || published.getDefinitionId() == null) {
            return published;
        }
        var ownerDefinition = safeDefinition(published.getDefinitionId());
        if (published.getProjectId() == null && ownerDefinition != null) {
            published.setProjectId(ownerDefinition.getProjectId());
        }
        if (versionBindingService != null && published.getVersionBindingId() == null) published = versionBindingService.resolvePublished(published, null);
        List<RuleDefinitionOutputField> fields;
        ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot = null;
        if (published.getArtifactId() != null && artifactSnapshotService != null) {
            snapshot = artifactSnapshotService.load(published.getArtifactId(), published.getDefinitionId(),
                    definitionService.getById(published.getDefinitionId()).getProjectId());
            fields = snapshot.getOutputFields();
            published.setFrozenFunctions(List.copyOf(snapshot.getFunctions()));
            published.setOpenApiConfigJson(snapshot.getOpenApiConfigJson());
            published.setImported(snapshot.isImported());
            published.setImportBindings(new LinkedHashMap<>(snapshot.getBindings()));
        } else {
            fields = definitionService.listOutputFields(published.getDefinitionId());
        }
        if (fields != null) {
            published.setOutputScriptNames(fields.stream()
                    .map(RuleDefinitionOutputField::getScriptName)
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toList()));
        }
        markServerExecutionRequirement(published, snapshot);
        return published;
    }

    private RuleResult idempotencyStatus(RuleIdempotencyService.Decision decision, String status) {
        RuleResult result = new RuleResult();
        result.setSuccess(false);
        result.setExecutionStatus(status);
        result.setTraceId(decision == null ? null : decision.getRootTraceId());
        result.setRevisionId(decision == null ? null : decision.getCurrentRevisionId());
        return result;
    }

    private void markServerExecutionRequirement(RulePublished published,
                                                ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot) {
        published.setRequiresServerExecution(false);
        published.setServerExecutionReason(null);
        var script = published.getCompiledScript();
        if (script != null && (script.contains("externalApiValue(") || script.contains("writeRuleList("))) {
            published.setRequiresServerExecution(true);
            published.setServerExecutionReason("包含外数调用或异步名单写入");
            return;
        }
        List<RuleVariable> variables;
        if (snapshot != null) {
            if (!snapshot.getResourceVersions().isEmpty()) {
                published.setRequiresServerExecution(true);
                published.setServerExecutionReason("包含资源固定版本执行");
                return;
            }
            variables = snapshot.getVariables();
        } else {
            // 没有制品的旧协议无法证明依赖闭包，保留原先按项目当前配置的保守门禁。
            var definition = safeDefinition(published.getDefinitionId());
            variables = definition == null || variableService == null ? List.of()
                    : variableService.listByProject(definition.getProjectId(), null);
        }
        for (var variable : variables) {
            String source = variable == null || variable.getVarSource() == null ? ""
                    : variable.getVarSource().trim().toUpperCase(Locale.ROOT);
            if ("API".equals(source) || "DB".equals(source)
                    || "LIST".equals(source) || "SFTP".equals(source) || "DERIVED".equals(source)) {
                published.setRequiresServerExecution(true);
                published.setServerExecutionReason("包含" + source + "变量来源");
                return;
            }
        }
        if (snapshot != null) {
            if (!snapshot.getModels().isEmpty()) {
                published.setRequiresServerExecution(true);
                published.setServerExecutionReason("包含模型执行");
                return;
            }
            for (var field : snapshot.getDataObjectFields()) {
                String source = field == null || field.getObjectSourceType() == null ? ""
                        : field.getObjectSourceType().trim().toUpperCase(Locale.ROOT);
                if ("API".equals(source) || "DB".equals(source) || "DATABASE".equals(source)) {
                    published.setRequiresServerExecution(true);
                    published.setServerExecutionReason("包含" + source + "数据对象来源");
                    return;
                }
            }
        }
        OpenApiContract contract = OpenApiContractCodec.parse(published.getOpenApiConfigJson());
        if (contract.getIdempotency() != null && contract.getIdempotency().isEnabled()) {
            published.setRequiresServerExecution(true);
            published.setServerExecutionReason("包含服务端幂等与恢复");
        }
    }

    private com.hengshucredit.rule.model.entity.RuleDefinition safeDefinition(Long definitionId) {
        if (definitionService == null || definitionId == null) return null;
        try {
            return definitionService.getById(definitionId);
        } catch (RuntimeException ignored) {
            // 同步单测或无持久化预览上下文可能没有 Mapper；不阻断规则同步。
            return null;
        }
    }

    private ProjectScope resolveProjectScope(HttpServletRequest request) {
        ProjectAuthContext auth = ProjectAuthContext.from(request);
        if (auth != null && auth.isGlobalAccess()) {
            return new ProjectScope(null, null, true);
        }
        Long projectId = asLong(request.getAttribute("projectId"));
        String projectCode = asString(request.getAttribute("projectCode"));
        if (projectId == null) {
            return null;
        }
        return new ProjectScope(projectId, projectCode, false);
    }

    private Long resolveDefinitionProjectId(RulePublished published) {
        if (published == null || published.getDefinitionId() == null || definitionService == null) return null;
        RuleDefinition definition = definitionService.getById(published.getDefinitionId());
        return definition == null ? null : definition.getProjectId();
    }

    static boolean isAuthorizedProject(Long requestedProjectId, Long tokenProjectId) {
        return requestedProjectId != null && tokenProjectId != null && requestedProjectId.equals(tokenProjectId);
    }

    private static Long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && !((String) value).isEmpty()) {
            return Long.valueOf((String) value);
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static class ProjectScope {
        private final Long projectId;
        private final String projectCode;
        private final boolean global;

        private ProjectScope(Long projectId, String projectCode, boolean global) {
            this.projectId = projectId;
            this.projectCode = projectCode;
            this.global = global;
        }
    }
}
