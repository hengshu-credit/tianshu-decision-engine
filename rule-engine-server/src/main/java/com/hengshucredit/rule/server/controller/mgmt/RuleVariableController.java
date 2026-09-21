package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.RuleValidationResult;
import com.hengshucredit.rule.model.dto.VariableSourcePreviewRequest;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.entity.RuleVariableOption;
import com.hengshucredit.rule.server.common.R;
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
    public R<com.hengshucredit.rule.model.dto.RuleTestSchema> derivedSchema(@RequestBody RuleVariable variable) {
        sourceReferenceValidator.validateOrThrow(variable);
        return R.ok(ruleTestSchemaService.buildDerivedDraft(variable));
    }

    /** 健康检查，用于验证变量管理接口是否正常注册 */
    @GetMapping("/health")
    public R<String> health() {
        return R.ok("ok");
    }

    /**
     * 分页查询变量；{@code varSource=CONSTANT} 用于常量列表；
     * {@code standaloneOnly=true} 且未指定 varSource 时排除常量（变量列表 Tab）。
     * @param scope 作用域筛选：GLOBAL/PROJECT，null 表示不限制
     */
    @GetMapping("/list")
    public R<IPage<RuleVariable>> list(
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
        return R.ok(variableService.pageList(pageNum, pageSize, projectId, varType, keyword, standaloneOnly, varSource, scope, projectCode, projectName, varCode, varLabel));
    }

    /** 从 Java 常量类批量生成常量生命周期审批草稿。 */
    @PostMapping("/import/constants/java")
    @RequirePermission("approval:submit")
    public R<Map<String, Object>> importConstantsJava(@RequestBody Map<String, String> body) {
        String pidStr = body.get("projectId");
        Long projectId = pidStr != null && !pidStr.isEmpty() ? Long.valueOf(pidStr) : null;
        String scope = body.getOrDefault("scope", "PROJECT");
        String javaSource = body.get("javaSource");
        Map<String, Object> result =
                governanceBatchImportService.importConstantsFromJava(
                        projectId, scope, javaSource,
                        operatorResolver.resolve());
        return R.ok(result);
    }

    /** 从扁平 JSON 批量生成常量生命周期审批草稿。 */
    @PostMapping("/import/constants/json")
    @RequirePermission("approval:submit")
    public R<Map<String, Object>> importConstantsJson(@RequestBody Map<String, String> body) {
        String pidStr = body.get("projectId");
        Long projectId = pidStr != null && !pidStr.isEmpty() ? Long.valueOf(pidStr) : null;
        String scope = body.getOrDefault("scope", "PROJECT");
        String jsonContent = body.get("jsonContent");
        Map<String, Object> result =
                governanceBatchImportService.importConstantsFromJson(
                        projectId, scope, jsonContent,
                        operatorResolver.resolve());
        return R.ok(result);
    }

    @GetMapping("/project/{projectId:\\d+}")
    public R<List<RuleVariable>> listByProject(@PathVariable Long projectId,
            @RequestParam(required = false) String varSource) {
        return R.ok(variableService.listByProject(projectId, varSource));
    }

    @GetMapping("/source-options")
    public R<VariableSourceCatalog> sourceOptions(
            @RequestParam String scope,
            @RequestParam(required = false) Long projectId) {
        try {
            return R.ok(sourceReferenceValidator.catalog(scope, projectId));
        } catch (IllegalArgumentException exception) {
            return R.fail(exception.getMessage());
        }
    }

    @PostMapping("/preview")
    public R<Map<String, Object>> previewVariable(
            @RequestBody VariableSourcePreviewRequest request) {
        try {
            if (request == null || request.getVariable() == null) {
                return R.fail("字段草稿不能为空");
            }
            sourceReferenceValidator.validateOrThrow(request.getVariable());
            return R.ok(variableSourceResolver.previewVariable(
                    request.getVariable(), request.getParams()));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return R.fail(exception.getMessage());
        }
    }

    @GetMapping("/{id:\\d+}")
    public R<RuleVariable> get(@PathVariable Long id) {
        return R.ok(variableService.getById(id));
    }

    @PostMapping("/{variableId:\\d+}/test")
    public R<Map<String, Object>> testVariable(@PathVariable Long variableId,
                                               @RequestBody(required = false) Map<String, Object> params) {
        try {
            return R.ok(variableSourceResolver.testVariable(variableId, params));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping
    @GovernedProjectionMutation
    public R<RuleVariable> create(@RequestBody RuleVariable variable) {
        if (variable.getScriptName() == null || variable.getScriptName().isEmpty()) {
            variable.setScriptName(variable.getVarCode());
        }
        try {
            variableService.save(variable);
            return R.ok(variable);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @PutMapping
    @GovernedProjectionMutation
    public R<Void> update(@RequestBody RuleVariable variable) {
        try {
            variableService.updateById(variable);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /** 将项目级变量（含常量）转为全局变量。 */
    @PostMapping("/toGlobal/{id:\\d+}")
    @GovernedProjectionMutation
    public R<Void> toGlobal(@PathVariable Long id) {
        try {
            variableService.toGlobal(id);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @DeleteMapping("/{id:\\d+}")
    @GovernedProjectionMutation
    public R<Void> delete(@PathVariable Long id) {
        variableService.deleteWithOptions(id);
        return R.ok();
    }

    @GetMapping("/{variableId:\\d+}/options")
    public R<List<RuleVariableOption>> getOptions(@PathVariable Long variableId) {
        return R.ok(variableService.getOptions(variableId));
    }

    @PostMapping("/{variableId:\\d+}/options")
    @GovernedProjectionMutation
    public R<Void> saveOptions(@PathVariable Long variableId, @RequestBody List<RuleVariableOption> options) {
        variableService.saveOptions(variableId, options);
        return R.ok();
    }

    @GetMapping("/tree/{projectId:\\d+}")
    public R<Map<String, Object>> tree(@PathVariable Long projectId) {
        return R.ok(dataObjectService.getVariableTree(projectId));
    }

    @PostMapping("/batch-validate/{projectId:\\d+}")
    public R<List<RuleValidationResult>> batchValidate(@PathVariable Long projectId) {
        return R.ok(batchTestService.validateProjectRules(projectId));
    }

    /** 全局批量验证（不限定项目） */
    @PostMapping("/batch-validate")
    public R<List<RuleValidationResult>> batchValidateAll() {
        return R.ok(batchTestService.validateAllRules());
    }

    private void trySyncSchema() {
        try {
            schemaSyncService.syncAndGetStatus();
        } catch (Exception ignored) {
        }
    }
}
