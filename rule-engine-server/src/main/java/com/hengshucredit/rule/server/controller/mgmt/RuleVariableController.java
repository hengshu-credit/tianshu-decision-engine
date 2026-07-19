package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.RuleValidationResult;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.entity.RuleVariableOption;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.BatchTestService;
import com.hengshucredit.rule.server.service.RuleDataObjectService;
import com.hengshucredit.rule.server.service.RuleVariableService;
import com.hengshucredit.rule.server.service.SchemaSyncService;
import com.hengshucredit.rule.server.service.VariableSourceResolver;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
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

    /** 从 Java 常量类批量导入常量（写入 rule_variable，var_source=CONSTANT） */
    @PostMapping("/import/constants/java")
    public R<Map<String, Object>> importConstantsJava(@RequestBody Map<String, String> body) {
        String pidStr = body.get("projectId");
        Long projectId = pidStr != null && !pidStr.isEmpty() ? Long.valueOf(pidStr) : null;
        String scope = body.getOrDefault("scope", "PROJECT");
        String javaSource = body.get("javaSource");
        Map<String, Object> result = variableService.importConstantsFromJava(projectId, scope, javaSource);
        trySyncSchema();
        return R.ok(result);
    }

    /** 从扁平 JSON 批量导入常量 */
    @PostMapping("/import/constants/json")
    public R<Map<String, Object>> importConstantsJson(@RequestBody Map<String, String> body) {
        String pidStr = body.get("projectId");
        Long projectId = pidStr != null && !pidStr.isEmpty() ? Long.valueOf(pidStr) : null;
        String scope = body.getOrDefault("scope", "PROJECT");
        String jsonContent = body.get("jsonContent");
        Map<String, Object> result = variableService.importConstantsFromJson(projectId, scope, jsonContent);
        trySyncSchema();
        return R.ok(result);
    }

    @GetMapping("/project/{projectId:\\d+}")
    public R<List<RuleVariable>> listByProject(@PathVariable Long projectId,
            @RequestParam(required = false) String varSource) {
        return R.ok(variableService.listByProject(projectId, varSource));
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
    public R<Void> toGlobal(@PathVariable Long id) {
        try {
            variableService.toGlobal(id);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        variableService.deleteWithOptions(id);
        return R.ok();
    }

    @GetMapping("/{variableId:\\d+}/options")
    public R<List<RuleVariableOption>> getOptions(@PathVariable Long variableId) {
        return R.ok(variableService.getOptions(variableId));
    }

    @PostMapping("/{variableId:\\d+}/options")
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
