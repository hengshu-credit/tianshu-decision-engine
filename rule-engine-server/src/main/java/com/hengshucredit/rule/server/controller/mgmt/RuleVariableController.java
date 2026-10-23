package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.model.dto.RuleValidationResult;
import com.hengshucredit.rule.model.dto.VariableSourcePreviewRequest;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.entity.RuleVariableOption;
import com.hengshucredit.rule.server.governance.GovernanceBatchImportService;
import com.hengshucredit.rule.server.governance.GovernedProjectionMutation;
import com.hengshucredit.rule.server.governance.VariableSourceCatalog;
import com.hengshucredit.rule.server.governance.VariableSourceReferenceValidator;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.BatchTestService;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import com.hengshucredit.rule.server.service.RuleDataObjectService;
import com.hengshucredit.rule.server.service.RuleVariableService;
import com.hengshucredit.rule.server.service.SchemaSyncService;
import com.hengshucredit.rule.server.service.VariableSourceResolver;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/variable")
public class RuleVariableController {

    @Resource
    private RuleVariableService variableService;

    @Resource
    private RuleDataObjectService dataObjectService;

    @Resource
    private BatchTestService batchTestService;

    @Resource
    private SchemaSyncService schemaSyncService;

    @Resource
    private VariableSourceResolver variableSourceResolver;

    @Resource
    private GovernanceBatchImportService governanceBatchImportService;

    @Resource
    private ConsoleOperatorResolver operatorResolver;

    @Resource
    private VariableSourceReferenceValidator sourceReferenceValidator;

    @Resource
    private com.hengshucredit.rule.server.service.RuleTestSchemaService ruleTestSchemaService;

    @PostMapping("/derived-schema")
    @RequirePermission("field:view")
    public ApiResponse<com.hengshucredit.rule.model.dto.RuleTestSchema> derivedSchema(@RequestBody RuleVariable variable) {
        sourceReferenceValidator.validateOrThrow(variable);
        return ApiResponse.ok(ruleTestSchemaService.buildDerivedDraft(variable));
    }

    /** 健康检查，用于验证变量管理接口是否正常注册 */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.ok("ok");
    }

    /**
     * 分页查询变量；{@code varSource=CONSTANT} 用于常量列表；
     * {@code standaloneOnly=true} 且未指定 varSource 时排除常量（变量列表 Tab）。
     * @param scope 作用域筛选：GLOBAL/PROJECT，null 表示不限制
     */
    @GetMapping("/list")
    public ApiResponse<IPage<RuleVariable>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String varType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean standaloneOnly,
            @RequestParam(required = false) String varSource,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String varCode,
            @RequestParam(required = false) String varLabel) {
        return ApiResponse.ok(variableService.pageList(pageNum, pageSize, projectId, varType, keyword, standaloneOnly, varSource, scope, projectCode, projectName, varCode, varLabel));
    }

    /** 从 Java 常量类批量生成常量生命周期审批草稿。 */
    @PostMapping("/import/constants/java")
    @RequirePermission("approval:submit")
    public ApiResponse<Map<String, Object>> importConstantsJava(@RequestBody Map<String, String> body) {
        String pidStr = body.get("projectId");
        Long projectId = pidStr != null && !pidStr.isEmpty() ? Long.valueOf(pidStr) : null;
        String scope = body.getOrDefault("scope", "PROJECT");
        String javaSource = body.get("javaSource");
        Map<String, Object> result =
                governanceBatchImportService.importConstantsFromJava(
                        projectId, scope, javaSource,
                        operatorResolver.resolve());
        return ApiResponse.ok(result);
    }

    /** 从扁平 JSON 批量生成常量生命周期审批草稿。 */
    @PostMapping("/import/constants/json")
    @RequirePermission("approval:submit")
    public ApiResponse<Map<String, Object>> importConstantsJson(@RequestBody Map<String, String> body) {
        String pidStr = body.get("projectId");
        Long projectId = pidStr != null && !pidStr.isEmpty() ? Long.valueOf(pidStr) : null;
        String scope = body.getOrDefault("scope", "PROJECT");
        String jsonContent = body.get("jsonContent");
        Map<String, Object> result =
                governanceBatchImportService.importConstantsFromJson(
                        projectId, scope, jsonContent,
                        operatorResolver.resolve());
        return ApiResponse.ok(result);
    }

    @GetMapping("/project/{projectId:\\d+}")
    public ApiResponse<List<RuleVariable>> listByProject(@PathVariable Long projectId,
            @RequestParam(required = false) String varSource) {
        return ApiResponse.ok(variableService.listByProject(projectId, varSource));
    }

    @GetMapping("/source-options")
    public ApiResponse<VariableSourceCatalog> sourceOptions(
            @RequestParam String scope,
            @RequestParam(required = false) Long projectId) {
        try {
            return ApiResponse.ok(sourceReferenceValidator.catalog(scope, projectId));
        } catch (IllegalArgumentException exception) {
            return ApiResponse.fail(exception);
        }
    }

    @PostMapping("/preview")
    public ApiResponse<Map<String, Object>> previewVariable(
            @RequestBody VariableSourcePreviewRequest request) {
        try {
            if (request == null || request.getVariable() == null) {
                return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.REQUIRED_FIELD_MISSING, "字段草稿不能为空");
            }
            sourceReferenceValidator.validateOrThrow(request.getVariable());
            return ApiResponse.ok(variableSourceResolver.previewVariable(
                    request.getVariable(), request.getParams()));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ApiResponse.fail(exception);
        }
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<RuleVariable> get(@PathVariable Long id) {
        return ApiResponse.ok(variableService.getById(id));
    }

    @PostMapping("/{variableId:\\d+}/test")
    public ApiResponse<Map<String, Object>> testVariable(@PathVariable Long variableId,
                                               @RequestBody(required = false) Map<String, Object> params) {
        try {
            return ApiResponse.ok(variableSourceResolver.testVariable(variableId, params));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(e);
        }
    }

    @PostMapping
    @GovernedProjectionMutation
    public ApiResponse<RuleVariable> create(@RequestBody RuleVariable variable) {
        if (variable.getScriptName() == null || variable.getScriptName().isEmpty()) {
            variable.setScriptName(variable.getVarCode());
        }
        try {
            variableService.save(variable);
            return ApiResponse.ok(variable);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    @PutMapping
    @GovernedProjectionMutation
    public ApiResponse<Void> update(@RequestBody RuleVariable variable) {
        try {
            variableService.updateById(variable);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    /** 将项目级变量（含常量）转为全局变量。 */
    @PostMapping("/toGlobal/{id:\\d+}")
    @GovernedProjectionMutation
    public ApiResponse<Void> toGlobal(@PathVariable Long id) {
        try {
            variableService.toGlobal(id);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    @DeleteMapping("/{id:\\d+}")
    @GovernedProjectionMutation
    public ApiResponse<Void> delete(@PathVariable Long id) {
        variableService.deleteWithOptions(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{variableId:\\d+}/options")
    public ApiResponse<List<RuleVariableOption>> getOptions(@PathVariable Long variableId) {
        return ApiResponse.ok(variableService.getOptions(variableId));
    }

    @PostMapping("/{variableId:\\d+}/options")
    @GovernedProjectionMutation
    public ApiResponse<Void> saveOptions(@PathVariable Long variableId, @RequestBody List<RuleVariableOption> options) {
        variableService.saveOptions(variableId, options);
        return ApiResponse.ok();
    }

    @GetMapping("/tree/{projectId:\\d+}")
    public ApiResponse<Map<String, Object>> tree(@PathVariable Long projectId) {
        return ApiResponse.ok(dataObjectService.getVariableTree(projectId));
    }

    @PostMapping("/batch-validate/{projectId:\\d+}")
    public ApiResponse<List<RuleValidationResult>> batchValidate(@PathVariable Long projectId) {
        return ApiResponse.ok(batchTestService.validateProjectRules(projectId));
    }

    /** 全局批量验证（不限定项目） */
    @PostMapping("/batch-validate")
    public ApiResponse<List<RuleValidationResult>> batchValidateAll() {
        return ApiResponse.ok(batchTestService.validateAllRules());
    }

    private void trySyncSchema() {
        try {
            schemaSyncService.syncAndGetStatus();
        } catch (Exception ignored) {
        }
    }
}
