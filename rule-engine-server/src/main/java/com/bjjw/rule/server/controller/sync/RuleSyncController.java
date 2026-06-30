package com.bjjw.rule.server.controller.sync;

import com.bjjw.rule.model.dto.RuleResult;
import com.bjjw.rule.model.entity.RuleFunction;
import com.bjjw.rule.model.entity.RulePublished;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.mapper.RulePublishedMapper;
import com.bjjw.rule.server.service.RuleExecuteService;
import com.bjjw.rule.server.service.RuleFunctionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rule/sync")
public class RuleSyncController {

    @Resource
    private RulePublishedMapper publishedMapper;

    @Resource
    private RuleFunctionService functionService;

    @Resource
    private RuleExecuteService executeService;

    @GetMapping("/{ruleCode}")
    public R<RulePublished> getByCode(@PathVariable String ruleCode, HttpServletRequest request) {
        ProjectScope scope = resolveProjectScope(request);
        if (scope == null) {
            return R.fail(401, "Unauthorized project token");
        }
        RulePublished published = publishedMapper.selectOne(
                appendProjectScope(new LambdaQueryWrapper<RulePublished>()
                        .eq(RulePublished::getRuleCode, ruleCode)
                        .eq(RulePublished::getStatus, 1), scope));
        return R.ok(published);
    }

    @GetMapping("/all")
    public R<List<RulePublished>> getAll(HttpServletRequest request) {
        ProjectScope scope = resolveProjectScope(request);
        if (scope == null) {
            return R.fail(401, "Unauthorized project token");
        }
        List<RulePublished> list = publishedMapper.selectList(
                appendProjectScope(new LambdaQueryWrapper<RulePublished>()
                        .eq(RulePublished::getStatus, 1), scope));
        return R.ok(list);
    }

    @GetMapping("/versions")
    public R<Map<String, Integer>> getVersions(HttpServletRequest request) {
        ProjectScope scope = resolveProjectScope(request);
        if (scope == null) {
            return R.fail(401, "Unauthorized project token");
        }
        List<RulePublished> list = publishedMapper.selectList(
                appendProjectScope(new LambdaQueryWrapper<RulePublished>()
                        .select(RulePublished::getRuleCode, RulePublished::getVersion)
                        .eq(RulePublished::getStatus, 1), scope));
        Map<String, Integer> versions = list.stream()
                .collect(Collectors.toMap(RulePublished::getRuleCode, RulePublished::getVersion));
        return R.ok(versions);
    }

    @PostMapping("/execute/{ruleCode}")
    public R<RuleResult> execute(@PathVariable String ruleCode,
                                 @RequestBody(required = false) Map<String, Object> body,
                                 HttpServletRequest request) {
        ProjectScope scope = resolveProjectScope(request);
        if (scope == null) {
            return R.fail(401, "Unauthorized project token");
        }
        RulePublished published = publishedMapper.selectOne(
                appendProjectScope(new LambdaQueryWrapper<RulePublished>()
                        .eq(RulePublished::getRuleCode, ruleCode)
                        .eq(RulePublished::getStatus, 1), scope));
        if (published == null) {
            return R.fail(404, "Rule not found");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body != null && body.get("params") instanceof Map
                ? (Map<String, Object>) body.get("params")
                : Collections.emptyMap();
        String clientAppName = body == null || body.get("clientAppName") == null
                ? null
                : String.valueOf(body.get("clientAppName"));
        return R.ok(executeService.executePublished(published, params, scope.projectId, clientAppName));
    }

    /**
     * 同步项目下所有已启用的函数定义（JAVA/BEAN/SCRIPT），供客户端 SDK 拉取并注册
     */
    @GetMapping("/functions/{projectId}")
    public R<List<RuleFunction>> syncFunctions(@PathVariable Long projectId, HttpServletRequest request) {
        ProjectScope scope = resolveProjectScope(request);
        if (scope == null) {
            return R.fail(401, "Unauthorized project token");
        }
        if (!isAuthorizedProject(projectId, scope.projectId)) {
            return R.fail(403, "Project token does not match requested project");
        }
        return R.ok(functionService.listByProject(projectId));
    }

    private LambdaQueryWrapper<RulePublished> appendProjectScope(LambdaQueryWrapper<RulePublished> wrapper,
                                                                 ProjectScope scope) {
        wrapper.and(w -> {
            boolean hasProjectCode = scope.projectCode != null && !scope.projectCode.isEmpty();
            if (hasProjectCode) {
                w.eq(RulePublished::getProjectCode, scope.projectCode);
                if (scope.projectId != null) {
                    w.or().exists(buildLinkedGlobalRuleExistsSql(scope.projectId));
                }
            } else if (scope.projectId != null) {
                w.exists(buildLinkedGlobalRuleExistsSql(scope.projectId));
            } else {
                w.apply("1 = 0");
            }
        });
        return wrapper;
    }

    private static String buildLinkedGlobalRuleExistsSql(Long projectId) {
        return "SELECT 1 FROM rule_definition_ref rdr " +
                "WHERE rdr.definition_id = rule_published.definition_id " +
                "AND rdr.project_id = " + projectId;
    }

    private ProjectScope resolveProjectScope(HttpServletRequest request) {
        Long projectId = asLong(request.getAttribute("projectId"));
        String projectCode = asString(request.getAttribute("projectCode"));
        if (projectId == null) {
            return null;
        }
        return new ProjectScope(projectId, projectCode);
    }

    static boolean isAuthorizedProject(Long requestedProjectId, Long tokenProjectId) {
        return requestedProjectId != null && tokenProjectId != null && requestedProjectId.equals(tokenProjectId);
    }

    private static Long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && !((String) value).isEmpty()) {
            return Long.valueOf((String) value);
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static class ProjectScope {
        private final Long projectId;
        private final String projectCode;

        private ProjectScope(Long projectId, String projectCode) {
            this.projectId = projectId;
            this.projectCode = projectCode;
        }
    }
}
