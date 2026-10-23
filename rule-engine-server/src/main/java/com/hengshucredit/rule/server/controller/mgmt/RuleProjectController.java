package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.model.dto.ApiDocDTO;
import com.hengshucredit.rule.model.dto.ProjectWorkbenchDTO;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.governance.GovernedProjectionMutation;
import com.hengshucredit.rule.server.service.RuleProjectService;
import com.hengshucredit.rule.server.service.ProjectAuthService;
import com.hengshucredit.rule.server.service.ProjectWorkbenchService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rule/project")
public class RuleProjectController {

    @Resource
    private RuleProjectService projectService;

    @Resource
    private ProjectAuthService projectAuthService;

    @Resource
    private ProjectWorkbenchService projectWorkbenchService;

    @GetMapping("/list")
    public ApiResponse<IPage<RuleProject>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String createBeginTime,
            @RequestParam(required = false) String createEndTime) {
        return ApiResponse.ok(projectService.pageList(pageNum, pageSize, keyword, projectCode, projectName, status, createBeginTime, createEndTime));
    }

    @GetMapping("/{id}")
    public ApiResponse<RuleProject> get(@PathVariable Long id) {
        return ApiResponse.ok(projectService.getById(id));
    }

    @GetMapping("/{id}/workbench")
    public ApiResponse<ProjectWorkbenchDTO> workbench(@PathVariable Long id) {
        return ApiResponse.ok(projectWorkbenchService.getWorkbench(id));
    }

    @PostMapping
    @GovernedProjectionMutation
    public ApiResponse<Map<String, Object>> create(@RequestBody RuleProject project,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        noStore(response);
        String token = projectService.createProjectWithToken(project);
        projectAuthService.recordLegacyManagementAccess(request, project.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("project", project);
        result.put("accessToken", token);
        return ApiResponse.ok(result);
    }

    @PutMapping
    @GovernedProjectionMutation
    public ApiResponse<Void> update(@RequestBody RuleProject project) {
        projectService.updateById(project);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @GovernedProjectionMutation
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.removeById(id);
        return ApiResponse.ok();
    }
    
    /**
     * 获取项目Token脱敏显示
     */
    @GetMapping("/{id}/token/masked")
    public ApiResponse<String> getMaskedToken(@PathVariable Long id) {
        String maskedToken = projectService.getMaskedToken(id);
        return ApiResponse.ok(maskedToken);
    }

    /**
     * 获取项目完整Token（需登录后查看）
     */
    @GetMapping("/{id}/token/full")
    public ApiResponse<String> getFullToken(@PathVariable Long id,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        noStore(response);
        String token = projectService.getFullToken(id);
        projectAuthService.recordLegacyManagementAccess(request, id);
        return ApiResponse.ok(token);
    }

    /**
     * 重新生成项目AccessToken（支持禁用旧Token后重新生成）
     */
    @PostMapping("/{id}/token/regenerate")
    public ApiResponse<String> regenerateToken(@PathVariable Long id,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        noStore(response);
        String token = projectService.regenerateToken(id);
        projectAuthService.recordLegacyManagementAccess(request, id);
        return ApiResponse.ok(token);
    }

    /**
     * 导出项目API文档
     */
    @GetMapping("/{id}/api-doc")
    public ApiResponse<ApiDocDTO> exportApiDoc(@PathVariable Long id) {
        return ApiResponse.ok(projectService.exportApiDoc(id));
    }

    private void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }
}
