package com.bjjw.rule.server.controller.mgmt;

import com.bjjw.rule.core.compiler.CompileResult;
import com.bjjw.rule.model.dto.RuleResult;
import com.bjjw.rule.model.entity.RuleDefinition;
import com.bjjw.rule.model.entity.RuleDefinitionContent;
import com.bjjw.rule.model.entity.RuleDefinitionRef;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.RuleCompileService;
import com.bjjw.rule.server.service.RuleDefinitionService;
import com.bjjw.rule.server.service.RuleExecuteService;
import com.bjjw.rule.server.service.RulePublishService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/definition")
public class RuleDefinitionController {

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleCompileService compileService;

    @Resource
    private RuleExecuteService executeService;

    @Resource
    private RulePublishService publishService;

    @GetMapping("/list")
    public R<IPage<RuleDefinition>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String publishedVersion,
            @RequestParam(required = false) String createBeginTime,
            @RequestParam(required = false) String createEndTime,
            @RequestParam(required = false) String updateBeginTime,
            @RequestParam(required = false) String updateEndTime,
            @RequestParam(required = false) String keyword) {
        return R.ok(definitionService.pageList(pageNum, pageSize, projectId, modelType, keyword, projectName, scope, status, ruleCode, ruleName, projectCode, publishedVersion, createBeginTime, createEndTime, updateBeginTime, updateEndTime));
    }

    /**
     * 获取全局规则列表（用于添加到项目）
     */
    @GetMapping("/global-list")
    public R<IPage<RuleDefinition>> globalList(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) String createBeginTime,
            @RequestParam(required = false) String createEndTime,
            @RequestParam(required = false) String updateBeginTime,
            @RequestParam(required = false) String updateEndTime,
            @RequestParam(required = false) String keyword) {
        return R.ok(definitionService.pageList(pageNum, pageSize, 0L, modelType, keyword, null, "GLOBAL", null, ruleCode, ruleName, null, null, createBeginTime, createEndTime, updateBeginTime, updateEndTime));
    }

    /**
     * 将全局规则添加到项目
     */
    @PostMapping("/add-global-to-project")
    public R<RuleDefinitionRef> addGlobalToProject(@RequestBody Map<String, Long> body) {
        Long definitionId = body.get("definitionId");
        Long projectId = body.get("projectId");
        if (definitionId == null || projectId == null) {
            return R.fail("definitionId 和 projectId 不能为空");
        }
        try {
            RuleDefinitionRef ref = definitionService.addGlobalRuleToProject(definitionId, projectId);
            return R.ok(ref);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取项目规则列表（包含项目级规则和已关联的全局规则）
     */
    @GetMapping("/project-list/{projectId}")
    public R<IPage<RuleDefinition>> projectList(
            @PathVariable Long projectId,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String createBeginTime,
            @RequestParam(required = false) String createEndTime,
            @RequestParam(required = false) String updateBeginTime,
            @RequestParam(required = false) String updateEndTime,
            @RequestParam(required = false) String keyword) {
        return R.ok(definitionService.pageListForProject(pageNum, pageSize, projectId, modelType, keyword, scope, status, ruleCode, ruleName, createBeginTime, createEndTime, updateBeginTime, updateEndTime));
    }

    @GetMapping("/{id}")
    public R<RuleDefinition> get(@PathVariable Long id) {
        return R.ok(definitionService.getById(id));
    }

    @PostMapping
    public R<RuleDefinition> create(@RequestBody RuleDefinition definition) {
        return R.ok(definitionService.createWithContent(definition));
    }

    @PutMapping
    public R<Void> update(@RequestBody RuleDefinition definition) {
        definitionService.updateWithProjectInfo(definition);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        definitionService.deleteWithContent(id);
        return R.ok();
    }

    @GetMapping("/content/{definitionId}")
    public R<RuleDefinitionContent> getContent(@PathVariable Long definitionId) {
        return R.ok(definitionService.getContent(definitionId));
    }

    @PostMapping("/save")
    public R<Void> saveContent(@RequestBody Map<String, Object> body) {
        Long definitionId = Long.valueOf(body.get("definitionId").toString());
        String modelJson = body.get("modelJson").toString();
        definitionService.saveContent(definitionId, modelJson);
        return R.ok();
    }

    @PostMapping("/compile/{definitionId}")
    public R<CompileResult> compile(@PathVariable Long definitionId) {
        return R.ok(compileService.compile(definitionId));
    }

    @PostMapping("/execute")
    public R<RuleResult> execute(@RequestBody Map<String, Object> body) {
        Long definitionId = Long.valueOf(body.get("definitionId").toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body.get("params") != null
                ? (Map<String, Object>) body.get("params")
                : Collections.emptyMap();
        return R.ok(executeService.testExecute(definitionId, params));
    }

    @PostMapping("/publish/{definitionId}")
    public R<Void> publish(@PathVariable Long definitionId, @RequestBody(required = false) Map<String, Object> body) {
        String changeLog = body != null ? (String) body.get("changeLog") : null;
        String error = publishService.publish(definitionId, changeLog);
        return error == null ? R.ok() : R.fail(error);
    }

    @PostMapping("/unpublish/{definitionId}")
    public R<Void> unpublish(@PathVariable Long definitionId) {
        String error = publishService.unpublish(definitionId);
        return error == null ? R.ok() : R.fail(error);
    }

    /**
     * 技术人员直接保存脚本（脚本模式），跳过可视化编译器。
     * 请求体: { "script": "..." }
     */
    @PostMapping("/script/{definitionId:\\d+}")
    public R<Void> saveScript(@PathVariable Long definitionId,
                               @RequestBody Map<String, String> body) {
        String script = body.get("script");
        if (script == null || script.trim().isEmpty()) {
            return R.fail("脚本内容不能为空");
        }
        definitionService.saveScript(definitionId, script.trim());
        return R.ok();
    }

    /**
     * 更新编辑模式（visual/script）
     * 请求体: { "scriptMode": "visual" | "script" }
     */
    @PostMapping("/scriptMode/{definitionId:\\d+}")
    public R<Void> updateScriptMode(@PathVariable Long definitionId,
                                     @RequestBody Map<String, String> body) {
        String mode = body.get("scriptMode");
        if (mode == null || (!"visual".equals(mode) && !"script".equals(mode))) {
            return R.fail("scriptMode 必须为 visual 或 script");
        }
        definitionService.updateScriptMode(definitionId, mode);
        return R.ok();
    }

    /**
     * 脚本模式下验证脚本语法（不覆盖可视化模型编译结果）
     * 请求体: { "script": "..." }
     */
    @PostMapping("/validateScript/{definitionId:\\d+}")
    public R<CompileResult> validateScript(@PathVariable Long definitionId,
                                            @RequestBody Map<String, String> body) {
        String script = body.get("script");
        if (script == null || script.trim().isEmpty()) {
            return R.fail("脚本内容不能为空");
        }
        return R.ok(compileService.validateScript(script.trim()));
    }
}
