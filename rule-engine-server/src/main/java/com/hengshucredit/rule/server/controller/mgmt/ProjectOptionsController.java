package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.RuleProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** 业务筛选所需的项目标签目录，不包含项目配置或访问凭据。 */
@RestController
@RequestMapping("/api/rule/project/options")
@RequirePermission(value = "project:view", anyOf = {"rule:view", "field:view", "list:view", "datasource:view", "database:view", "model:view", "function:view", "experiment:view", "log:view", "billing:view", "approval:view", "transfer:view", "lineage:view"})
@RequiredArgsConstructor
public class ProjectOptionsController {
    private final com.hengshucredit.rule.server.mapper.ConsoleUserProjectMapper projects;

    @GetMapping
    public ApiResponse<IPage<com.hengshucredit.rule.server.mapper.ConsoleUserProjectMapper.ProjectOption>> list(@RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String projectCode, @RequestParam(required = false) String projectName,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(projects.browseOptions(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(Math.max(1, pageNum), Math.min(1000, Math.max(1, pageSize))),
                keyword, projectCode, projectName, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<com.hengshucredit.rule.server.mapper.ConsoleUserProjectMapper.ProjectOption> detail(@PathVariable Long id) {
        var project = projects.browseOption(id);
        if (project == null) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "项目不存在");
        return ApiResponse.ok(project);
    }
}
