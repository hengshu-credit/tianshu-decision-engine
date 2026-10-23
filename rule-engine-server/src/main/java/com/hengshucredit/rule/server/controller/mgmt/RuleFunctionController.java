package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleFunctionVersion;
import com.hengshucredit.rule.model.dto.FunctionMethodCatalog;
import com.hengshucredit.rule.server.governance.GovernanceRequestView;
import com.hengshucredit.rule.server.governance.GovernedProjectionMutation;
import com.hengshucredit.rule.server.governance.LegacyGovernanceRestoreService;
import com.hengshucredit.rule.server.publish.RuleFunctionPushService;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import com.hengshucredit.rule.server.service.RuleFunctionService;
import com.hengshucredit.rule.server.service.FunctionMethodCatalogService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/function")
public class RuleFunctionController {

    @Resource
    private RuleFunctionService functionService;

    @Resource
    private RuleFunctionPushService functionPushService;

    @Resource
    private LegacyGovernanceRestoreService legacyRestoreService;

    @Resource
    private ConsoleOperatorResolver operatorResolver;

    @Resource
    private FunctionMethodCatalogService methodCatalogService;

    @PostMapping("/methods")
    @RequirePermission("function:view")
    public ApiResponse<FunctionMethodCatalog> inspectMethods(@RequestBody RuleFunction function) {
        return ApiResponse.ok(methodCatalogService.inspect(function));
    }

    @PostMapping("/signature-impact")
    @RequirePermission("function:view")
    public ApiResponse<com.hengshucredit.rule.model.dto.FunctionSignatureImpact> signatureImpact(@RequestBody RuleFunction function) {
        return ApiResponse.ok(functionService.signatureImpact(function));
    }

    /**
     * 按项目分页查询函数列表（管理页面）
     * @param scope 作用域筛选：GLOBAL/PROJECT，null 表示不限制
     */
    @GetMapping("/project/{projectId}")
    public ApiResponse<IPage<RuleFunction>> listByProject(
            @PathVariable Long projectId,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String funcCode,
            @RequestParam(required = false) String funcLabel,
            @RequestParam(required = false) String implType) {
        return ApiResponse.ok(functionService.pageByProject(projectId, pageNum, pageSize, scope, projectCode, projectName, funcCode, funcLabel, implType));
    }

    /**
     * 查询所有函数（分页，用于未选择项目时展示全部）
     * @param scope 作用域筛选：GLOBAL/PROJECT，null 表示全部
     */
    @GetMapping("/list")
    public ApiResponse<IPage<RuleFunction>> pageAll(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String funcCode,
            @RequestParam(required = false) String funcLabel,
            @RequestParam(required = false) String implType) {
        return ApiResponse.ok(functionService.pageAll(pageNum, pageSize, scope, projectId, projectCode, projectName, funcCode, funcLabel, implType));
    }

    /**
     * 按项目查询全部启用函数（设计器变量面板使用，非分页）
     */
    @GetMapping("/project/{projectId}/all")
    public ApiResponse<List<RuleFunction>> listAllByProject(@PathVariable Long projectId) {
        return ApiResponse.ok(functionService.listByProject(projectId));
    }

    @GetMapping("/{id}")
    public ApiResponse<RuleFunction> getById(@PathVariable Long id) {
        return ApiResponse.ok(functionService.getById(id));
    }

    @PostMapping
    @GovernedProjectionMutation
    public ApiResponse<Void> create(@RequestBody RuleFunction func) {
        var message = functionPushService.prepare(func, pushAction(func));
        functionService.create(func);
        functionPushService.push(message);
        return ApiResponse.ok();
    }

    @PutMapping
    @GovernedProjectionMutation
    public ApiResponse<Void> update(@RequestBody RuleFunction func) {
        var message = functionPushService.prepare(func, pushAction(func));
        functionService.update(func);
        functionPushService.push(message);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @GovernedProjectionMutation
    public ApiResponse<Void> delete(@PathVariable Long id) {
        RuleFunction func = functionService.getById(id);
        var message = func == null ? null
                : functionPushService.prepare(func, "FUNC_DELETE");
        functionService.delete(id);
        if (message != null) {
            functionPushService.push(message);
        }
        return ApiResponse.ok();
    }

    private String pushAction(RuleFunction function) {
        return function != null && Integer.valueOf(0).equals(function.getStatus())
                ? "FUNC_DELETE" : "FUNC_UPDATE";
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> params) {
        try {
            Map<String, Object> result = functionService.testFunction(id, params);
            return Boolean.FALSE.equals(result.get("success"))
                    ? ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.FUNCTION_EXECUTION_ERROR,
                            (String) result.get("errorMessage"), result) : ApiResponse.ok(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    /** 推送函数新增/更新消息 */
    @GetMapping("/versions/{functionId}")
    public ApiResponse<List<RuleFunctionVersion>> listVersions(@PathVariable Long functionId) {
        return ApiResponse.ok(functionService.listVersions(functionId));
    }

    @GetMapping("/version/{functionId}/{version}")
    public ApiResponse<RuleFunctionVersion> getVersion(@PathVariable Long functionId, @PathVariable Integer version) {
        RuleFunctionVersion snapshot = functionService.getVersion(functionId, version);
        return snapshot == null ? ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "Version not found") : ApiResponse.ok(snapshot);
    }

    @GetMapping("/versionCompare/{functionId}")
    public ApiResponse<Map<String, Object>> compareVersions(@PathVariable Long functionId,
                                                       @RequestParam Integer leftVersion,
                                                       @RequestParam Integer rightVersion) {
        try {
            return ApiResponse.ok(functionService.compareVersions(functionId, leftVersion, rightVersion));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    @PostMapping("/rollback/{functionId}/{version}")
    @RequirePermission("function:edit")
    public ApiResponse<GovernanceRequestView> rollback(
            @PathVariable Long functionId,
            @PathVariable Integer version,
            @RequestParam(defaultValue = "NEW") String restoreMode) {
        try {
            return ApiResponse.ok(GovernanceRequestView.from(
                    legacyRestoreService.restoreFunction(
                            functionId, version, restoreMode,
                            operatorResolver.resolve())));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

}
