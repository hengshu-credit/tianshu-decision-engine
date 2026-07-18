package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.service.RuleExecuteService;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleFunctionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    @Resource
    private RuleDefinitionService definitionService;

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
        return R.ok(withOutputScriptNames(published));
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
        for (RulePublished published : list) {
            withOutputScriptNames(published);
        }
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
        return executeBody(ruleCode, body, request);
    }

    @PostMapping(value = "/execute/{ruleCode}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<RuleResult> executeMultipart(@PathVariable String ruleCode,
                                          MultipartHttpServletRequest request) {
        return executeBody(ruleCode, buildMultipartBody(request.getParameterMap(), request.getFileMap()), request);
    }

    private R<RuleResult> executeBody(String ruleCode, Map<String, Object> body, HttpServletRequest request) {
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
        return R.ok(executeService.executePublished(published, params, scope.projectId, clientAppName,
                ProjectAuthContext.from(request)));
    }

    static Map<String, Object> buildMultipartBody(Map<String, String[]> fields,
                                                   Map<String, MultipartFile> files) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach((path, values) -> {
                Object value = values == null || values.length == 0
                        ? ""
                        : values.length == 1 ? values[0] : Arrays.asList(values);
                putNestedValue(body, path, value);
            });
        }
        if (files != null) {
            files.forEach((path, file) -> putNestedValue(body, path, file));
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private static void putNestedValue(Map<String, Object> target, String path, Object value) {
        if (path == null || path.trim().isEmpty()) {
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (index == parts.length - 1) {
                current.put(part, value);
            } else {
                Object child = current.get(part);
                if (!(child instanceof Map)) {
                    child = new LinkedHashMap<String, Object>();
                    current.put(part, child);
                }
                current = (Map<String, Object>) child;
            }
        }
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

    private RulePublished withOutputScriptNames(RulePublished published) {
        if (published == null || published.getDefinitionId() == null) {
            return published;
        }
        List<RuleDefinitionOutputField> fields = definitionService.listOutputFields(published.getDefinitionId());
        if (fields != null) {
            published.setOutputScriptNames(fields.stream()
                    .map(RuleDefinitionOutputField::getScriptName)
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toList()));
        }
        return published;
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
