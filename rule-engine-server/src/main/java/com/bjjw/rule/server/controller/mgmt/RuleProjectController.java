package com.bjjw.rule.server.controller.mgmt;

import com.bjjw.rule.model.dto.ApiDocDTO;
import com.bjjw.rule.model.entity.RuleProject;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.RuleProjectService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rule/project")
public class RuleProjectController {

    @Resource
    private RuleProjectService projectService;

    @GetMapping("/list")
    public R<IPage<RuleProject>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String createBeginTime,
            @RequestParam(required = false) String createEndTime) {
        return R.ok(projectService.pageList(pageNum, pageSize, keyword, projectCode, projectName, status, createBeginTime, createEndTime));
    }

    @GetMapping("/{id}")
    public R<RuleProject> get(@PathVariable Long id) {
        return R.ok(projectService.getById(id));
    }

    @PostMapping
    public R<Map<String, Object>> create(@RequestBody RuleProject project) {
        String token = projectService.createProjectWithToken(project);
        Map<String, Object> result = new HashMap<>();
        result.put("project", project);
        result.put("accessToken", token);
        return R.ok(result);
    }

    @PutMapping
    public R<Void> update(@RequestBody RuleProject project) {
        projectService.updateById(project);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        projectService.removeById(id);
        return R.ok();
    }
    
    /**
     * 获取项目Token脱敏显示
     */
    @GetMapping("/{id}/token/masked")
    public R<String> getMaskedToken(@PathVariable Long id) {
        String maskedToken = projectService.getMaskedToken(id);
        return R.ok(maskedToken);
    }

    /**
     * 获取项目完整Token（需登录后查看）
     */
    @GetMapping("/{id}/token/full")
    public R<String> getFullToken(@PathVariable Long id) {
        String token = projectService.getFullToken(id);
        return R.ok(token);
    }

    /**
     * 重新生成项目AccessToken（支持禁用旧Token后重新生成）
     */
    @PostMapping("/{id}/token/regenerate")
    public R<String> regenerateToken(@PathVariable Long id) {
        String token = projectService.regenerateToken(id);
        return R.ok(token);
    }

    /**
     * 导出项目API文档
     */
    @GetMapping("/{id}/api-doc")
    public R<ApiDocDTO> exportApiDoc(@PathVariable Long id) {
        return R.ok(projectService.exportApiDoc(id));
    }
}
