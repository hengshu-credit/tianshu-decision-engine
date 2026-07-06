package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleFunctionVersion;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.mapper.RuleFunctionMapper;
import com.hengshucredit.rule.server.mapper.RuleFunctionVersionMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RuleFunctionService {

    /** 作用域：全局 */
    public static final String SCOPE_GLOBAL = "GLOBAL";
    /** 作用域：项目级 */
    public static final String SCOPE_PROJECT = "PROJECT";
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    @Resource
    private RuleFunctionMapper functionMapper;

    @Resource
    private RuleFunctionVersionMapper functionVersionMapper;

    @Resource
    private RuleProjectMapper projectMapper;

    @Resource
    private QLExpressEngine qlExpressEngine;

    @Resource
    private FunctionRegistrar functionRegistrar;

    /** 填充函数列表的项目名称 */
    private void fillProjectName(List<RuleFunction> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> projectIds = list.stream()
                .filter(f -> f.getProjectId() != null && f.getProjectId() > 0)
                .map(RuleFunction::getProjectId)
                .distinct()
                .collect(Collectors.toList());
        if (projectIds.isEmpty()) return;
        Map<Long, String> nameMap = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(RuleProject::getId, RuleProject::getProjectName, (a, b) -> a));
        list.forEach(f -> {
            if (f.getProjectId() != null && f.getProjectId() > 0) {
                f.setProjectName(nameMap.get(f.getProjectId()));
            }
        });
    }

    /**
     * 按项目查询全部启用函数（SDK 同步场景使用）
     * 同时查询全局函数和指定项目的函数
     */
    public List<RuleFunction> listByProject(Long projectId) {
        LambdaQueryWrapper<RuleFunction> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null && projectId > 0) {
            // 同时查询全局函数和指定项目的函数
            wrapper.and(w -> w
                    .eq(RuleFunction::getScope, SCOPE_GLOBAL)
                    .or()
                    .eq(RuleFunction::getScope, SCOPE_PROJECT)
                    .eq(RuleFunction::getProjectId, projectId)
            );
        } else {
            // 只查询全局函数
            wrapper.eq(RuleFunction::getScope, SCOPE_GLOBAL);
        }
        wrapper.eq(RuleFunction::getStatus, 1)
               .orderByAsc(RuleFunction::getFuncCode);
        return functionMapper.selectList(wrapper);
    }

    /**
     * 仅查询指定项目的函数（不包含全局函数）
     */
    public List<RuleFunction> listByProjectOnly(Long projectId) {
        LambdaQueryWrapper<RuleFunction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleFunction::getProjectId, projectId)
               .eq(RuleFunction::getScope, SCOPE_PROJECT)
               .eq(RuleFunction::getStatus, 1)
               .orderByAsc(RuleFunction::getFuncCode);
        return functionMapper.selectList(wrapper);
    }

    /**
     * 仅查询全局函数
     */
    public List<RuleFunction> listGlobalOnly() {
        LambdaQueryWrapper<RuleFunction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleFunction::getScope, SCOPE_GLOBAL)
               .eq(RuleFunction::getStatus, 1)
               .orderByAsc(RuleFunction::getFuncCode);
        return functionMapper.selectList(wrapper);
    }

    /**
     * 按项目分页查询函数（管理页面使用）
     * @param scope 作用域筛选：GLOBAL/PROJECT，null 表示不限制
     */
    public IPage<RuleFunction> pageByProject(Long projectId, int pageNum, int pageSize, String scope,
                                              String projectCode, String projectName, String funcCode, String funcLabel) {
        LambdaQueryWrapper<RuleFunction> wrapper = new LambdaQueryWrapper<>();
        if (scope != null && !scope.isEmpty()) {
            wrapper.eq(RuleFunction::getScope, scope);
        }
        if (projectId != null && projectId > 0) {
            if (scope == null || scope.isEmpty()) {
                // 无 scope 限制时，同时查询全局和项目级
                wrapper.and(w -> w
                        .eq(RuleFunction::getScope, SCOPE_GLOBAL)
                        .or()
                        .eq(RuleFunction::getScope, SCOPE_PROJECT)
                        .eq(RuleFunction::getProjectId, projectId)
                );
            } else if (SCOPE_PROJECT.equals(scope)) {
                wrapper.eq(RuleFunction::getProjectId, projectId);
            }
            // GLOBAL 情况下 projectId 不作为过滤条件
        }
        // 精确匹配函数编码
        if (funcCode != null && !funcCode.isEmpty()) {
            wrapper.like(RuleFunction::getFuncCode, funcCode);
        }
        // 精确匹配函数名称
        if (funcLabel != null && !funcLabel.isEmpty()) {
            wrapper.like(RuleFunction::getFuncName, funcLabel);
        }
        // 通过 projectCode 或 projectName 进行筛选
        if (projectCode != null && !projectCode.isEmpty()) {
            List<Long> projectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<RuleProject>().like(RuleProject::getProjectCode, projectCode))
                    .stream().map(RuleProject::getId).collect(Collectors.toList());
            if (!projectIds.isEmpty()) {
                wrapper.and(w -> w.in(RuleFunction::getProjectId, projectIds)
                        .or()
                        .eq(RuleFunction::getScope, SCOPE_GLOBAL));
            } else {
                wrapper.eq(RuleFunction::getScope, SCOPE_GLOBAL);
            }
        } else if (projectName != null && !projectName.isEmpty()) {
            List<Long> projectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<RuleProject>().like(RuleProject::getProjectName, projectName))
                    .stream().map(RuleProject::getId).collect(Collectors.toList());
            if (!projectIds.isEmpty()) {
                wrapper.and(w -> w.in(RuleFunction::getProjectId, projectIds)
                        .or()
                        .eq(RuleFunction::getScope, SCOPE_GLOBAL));
            } else {
                wrapper.eq(RuleFunction::getScope, SCOPE_GLOBAL);
            }
        }
        wrapper.orderByAsc(RuleFunction::getFuncCode);
        IPage<RuleFunction> result = functionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillProjectName(result.getRecords());
        return result;
    }

    /**
     * 查询所有函数（分页，未选项目时使用）
     * @param scope 作用域筛选：GLOBAL/PROJECT，null 表示全部
     * @note 无 projectId/projectCode/projectName 时，默认只查询全局函数（scope=GLOBAL），
     *       避免返回所有项目级函数造成数据泄露和界面混乱。
     */
    public IPage<RuleFunction> pageAll(int pageNum, int pageSize, String scope, Long projectId,
                                        String projectCode, String projectName, String funcCode, String funcLabel) {
        LambdaQueryWrapper<RuleFunction> wrapper = new LambdaQueryWrapper<>();
        if (scope != null && !scope.isEmpty()) {
            wrapper.eq(RuleFunction::getScope, scope);
        }
        // 精确匹配函数编码
        if (funcCode != null && !funcCode.isEmpty()) {
            wrapper.like(RuleFunction::getFuncCode, funcCode);
        }
        // 精确匹配函数名称
        if (funcLabel != null && !funcLabel.isEmpty()) {
            wrapper.like(RuleFunction::getFuncName, funcLabel);
        }
        // projectId 精确匹配（优先于 projectCode/projectName）
        if (projectId != null && projectId > 0) {
            if (scope == null || scope.isEmpty()) {
                wrapper.and(w -> w
                        .eq(RuleFunction::getScope, SCOPE_GLOBAL)
                        .or()
                        .eq(RuleFunction::getScope, SCOPE_PROJECT)
                        .eq(RuleFunction::getProjectId, projectId)
                );
            } else if (SCOPE_PROJECT.equals(scope)) {
                wrapper.eq(RuleFunction::getProjectId, projectId);
            }
        } else if (projectCode != null && !projectCode.isEmpty()) {
            List<Long> projectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<RuleProject>().like(RuleProject::getProjectCode, projectCode))
                    .stream().map(RuleProject::getId).collect(Collectors.toList());
            if (!projectIds.isEmpty()) {
                wrapper.and(w -> w.in(RuleFunction::getProjectId, projectIds)
                        .or()
                        .eq(RuleFunction::getScope, SCOPE_GLOBAL));
            } else {
                wrapper.eq(RuleFunction::getScope, SCOPE_GLOBAL);
            }
        } else if (projectName != null && !projectName.isEmpty()) {
            List<Long> projectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<RuleProject>().like(RuleProject::getProjectName, projectName))
                    .stream().map(RuleProject::getId).collect(Collectors.toList());
            if (!projectIds.isEmpty()) {
                wrapper.and(w -> w.in(RuleFunction::getProjectId, projectIds)
                        .or()
                        .eq(RuleFunction::getScope, SCOPE_GLOBAL));
            } else {
                wrapper.eq(RuleFunction::getScope, SCOPE_GLOBAL);
            }
        } else {
            // 无任何项目筛选条件时，返回所有数据（全局+项目级），便于管理控制台查看全量资源
            // 仅在用户显式指定了 scope 时才做 scope 过滤
        }
        wrapper.orderByAsc(RuleFunction::getScope, RuleFunction::getProjectId, RuleFunction::getFuncCode);
        IPage<RuleFunction> result = functionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillProjectName(result.getRecords());
        return result;
    }

    public RuleFunction getById(Long id) {
        return functionMapper.selectById(id);
    }

    public Map<String, Object> testFunction(Long functionId, Map<String, Object> params) {
        RuleFunction function = getById(functionId);
        if (function == null) {
            throw new IllegalArgumentException("Function not found");
        }
        Map<String, Object> context = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        String implType = function.getImplType() == null ? "SCRIPT" : function.getImplType().trim().toUpperCase();
        registerFunctionIfNeeded(function, implType);
        RuleResult ruleResult = qlExpressEngine.execute(buildFunctionTestScript(function, implType), context, true);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", ruleResult.isSuccess());
        result.put("result", ruleResult.getResult());
        result.put("errorMessage", ruleResult.getErrorMessage());
        result.put("executeTimeMs", ruleResult.getExecuteTimeMs());
        result.put("params", context);
        result.put("functionCode", function.getFuncCode());
        if (ruleResult.getTraces() != null) {
            result.put("traces", ruleResult.getTraces());
        }
        return result;
    }

    public void create(RuleFunction func) {
        // 确保 scope 有默认值
        if (func.getScope() == null || func.getScope().isEmpty()) {
            func.setScope(SCOPE_PROJECT);
        }
        functionMapper.insert(func);
        saveVersionSnapshot(func.getId(), "create");
    }

    public void update(RuleFunction func) {
        functionMapper.updateById(func);
        saveVersionSnapshot(func.getId(), "update");
    }

    public void delete(Long id) {
        functionMapper.deleteById(id);
    }

    public List<RuleFunctionVersion> listVersions(Long functionId) {
        return functionVersionMapper.selectList(new LambdaQueryWrapper<RuleFunctionVersion>()
                .eq(RuleFunctionVersion::getFunctionId, functionId)
                .orderByDesc(RuleFunctionVersion::getVersion));
    }

    public RuleFunctionVersion getVersion(Long functionId, Integer version) {
        return functionVersionMapper.selectOne(new LambdaQueryWrapper<RuleFunctionVersion>()
                .eq(RuleFunctionVersion::getFunctionId, functionId)
                .eq(RuleFunctionVersion::getVersion, version));
    }

    public Map<String, Object> compareVersions(Long functionId, Integer leftVersion, Integer rightVersion) {
        RuleFunctionVersion left = getVersion(functionId, leftVersion);
        RuleFunctionVersion right = getVersion(functionId, rightVersion);
        if (left == null || right == null) {
            throw new IllegalArgumentException("Version not found");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("left", left);
        result.put("right", right);
        result.put("functionChanged", !sameText(left.getFunctionJson(), right.getFunctionJson()));
        return result;
    }

    public RuleFunction rollbackToVersion(Long functionId, Integer version) {
        RuleFunctionVersion snapshot = getVersion(functionId, version);
        if (snapshot == null) {
            throw new IllegalArgumentException("Version not found");
        }
        RuleFunction restored = JSON.parseObject(snapshot.getFunctionJson(), RuleFunction.class);
        restored.setId(functionId);
        functionMapper.updateById(restored);
        saveVersionSnapshot(functionId, "rollback to v" + version);
        return functionMapper.selectById(functionId);
    }

    private void saveVersionSnapshot(Long functionId, String changeLog) {
        if (functionId == null) return;
        RuleFunction function = functionMapper.selectById(functionId);
        if (function == null) return;
        RuleFunctionVersion version = new RuleFunctionVersion();
        version.setFunctionId(functionId);
        version.setVersion(nextVersion(functionId));
        version.setFunctionJson(JSON.toJSONString(function));
        version.setChangeLog(changeLog);
        version.setPublishTime(LocalDateTime.now());
        functionVersionMapper.insert(version);
    }

    private int nextVersion(Long functionId) {
        List<RuleFunctionVersion> versions = listVersions(functionId);
        if (versions == null || versions.isEmpty()) return 1;
        Integer current = versions.get(0).getVersion();
        return (current == null ? 0 : current) + 1;
    }

    private boolean sameText(String left, String right) {
        if (left == null) return right == null;
        return left.equals(right);
    }

    private void registerFunctionIfNeeded(RuleFunction function, String implType) {
        if ("JAVA".equals(implType)) {
            functionRegistrar.registerJavaFunctions(Collections.singletonList(function), qlExpressEngine.getRunner());
        } else if ("BEAN".equals(implType)) {
            functionRegistrar.registerBeanFunctions(Collections.singletonList(function), qlExpressEngine.getRunner());
        }
    }

    private String buildFunctionTestScript(RuleFunction function, String implType) {
        String functionCode = requireIdentifier(function.getFuncCode(), "函数编码");
        List<String> paramNames = parseParamNames(function.getParamsJson());
        StringBuilder script = new StringBuilder();
        if ("SCRIPT".equals(implType)) {
            script.append(functionRegistrar.buildScriptFunctionPrefix(Collections.singletonList(function)));
        }
        script.append("__function_test_result = ")
                .append(functionCode)
                .append("(")
                .append(String.join(", ", paramNames))
                .append(");\n");
        script.append("__function_test_result");
        return script.toString();
    }

    private List<String> parseParamNames(String paramsJson) {
        List<String> names = new ArrayList<>();
        if (paramsJson == null || paramsJson.trim().isEmpty()) {
            return names;
        }
        try {
            JSONArray array = JSON.parseArray(paramsJson);
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getJSONObject(i);
                if (item == null) continue;
                String name = item.getString("name");
                if (name != null && !name.trim().isEmpty()) {
                    names.add(requireIdentifier(name.trim(), "函数参数名"));
                }
            }
            return names;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("函数参数配置不是合法 JSON");
        }
    }

    private String requireIdentifier(String value, String label) {
        if (value == null || !IDENTIFIER_PATTERN.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException(label + "不是合法标识符: " + value);
        }
        return value.trim();
    }
}
