package com.hengshucredit.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.common.R;
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
    private final RuleProjectService projects;

    @GetMapping
    public R<IPage<Map<String, Object>>> list(@RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String projectCode, @RequestParam(required = false) String projectName,
            @RequestParam(required = false) Integer status) {
        return R.ok(projects.pageList(Math.max(1, pageNum), Math.min(1000, Math.max(1, pageSize)), keyword,
                projectCode, projectName, status, null, null).convert(this::label));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        RuleProject project = projects.getById(id);
        if (project == null || Integer.valueOf(-1).equals(project.getStatus())) return R.fail(404, "项目不存在");
        return R.ok(label(project));
    }

    private Map<String, Object> label(RuleProject project) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", project.getId()); row.put("projectCode", project.getProjectCode());
        row.put("projectName", project.getProjectName()); row.put("status", project.getStatus());
        return row;
    }
}
