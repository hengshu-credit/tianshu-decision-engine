package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectFilterService {

    @Resource
    private RuleProjectMapper projectMapper;

    public ProjectMatches resolve(String projectCode, String projectName) {
        boolean hasCode = StringUtils.hasText(projectCode);
        boolean hasName = StringUtils.hasText(projectName);
        if (!hasCode && !hasName) {
            return ProjectMatches.inactive();
        }

        LambdaQueryWrapper<RuleProject> wrapper = new LambdaQueryWrapper<>();
        if (hasCode) {
            wrapper.like(RuleProject::getProjectCode, projectCode);
        }
        if (hasName) {
            wrapper.like(RuleProject::getProjectName, projectName);
        }
        List<RuleProject> projects = projectMapper.selectList(wrapper);
        return ProjectMatches.active(projects);
    }

    public static final class ProjectMatches {
        private final boolean active;
        private final List<Long> projectIds;
        private final List<String> projectCodes;

        private ProjectMatches(boolean active, List<Long> projectIds, List<String> projectCodes) {
            this.active = active;
            this.projectIds = projectIds;
            this.projectCodes = projectCodes;
        }

        private static ProjectMatches inactive() {
            return new ProjectMatches(false, Collections.emptyList(), Collections.emptyList());
        }

        private static ProjectMatches active(List<RuleProject> projects) {
            Set<Long> ids = new LinkedHashSet<>();
            Set<String> codes = new LinkedHashSet<>();
            if (projects != null) {
                for (RuleProject project : projects) {
                    if (project == null) continue;
                    if (project.getId() != null) ids.add(project.getId());
                    if (StringUtils.hasText(project.getProjectCode())) codes.add(project.getProjectCode());
                }
            }
            return new ProjectMatches(
                    true,
                    Collections.unmodifiableList(new ArrayList<>(ids)),
                    Collections.unmodifiableList(new ArrayList<>(codes)));
        }

        public boolean isActive() {
            return active;
        }

        public boolean isEmpty() {
            return projectIds.isEmpty();
        }

        public List<Long> getProjectIds() {
            return projectIds;
        }

        public List<String> getProjectCodes() {
            return projectCodes;
        }
    }
}
