package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** 批量补充展示信息；查询范围和各资源的回填规则由调用方保留。 */
final class ProjectDisplaySupport {
    private ProjectDisplaySupport() { }

    static <T> void fill(List<T> rows, Function<T, Long> projectId,
                         RuleProjectMapper mapper, BiConsumer<T, RuleProject> apply) {
        if (rows == null || rows.isEmpty()) return;
        Set<Long> ids = new LinkedHashSet<>();
        for (T row : rows) {
            Long id = projectId.apply(row);
            if (id != null && id > 0) ids.add(id);
        }
        if (ids.isEmpty()) return;
        Map<Long, RuleProject> projects = new LinkedHashMap<>();
        for (RuleProject project : mapper.selectByIds(ids)) {
            projects.putIfAbsent(project.getId(), project);
        }
        for (T row : rows) apply.accept(row, projects.get(projectId.apply(row)));
    }
}
