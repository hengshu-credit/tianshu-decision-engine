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

import jakarta.annotation.Resource;
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
    private ProjectFilterService projectFilterService;

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

    public Map<Long, String> buildFunctionCodeMap(Long projectId) {
        return listByProject(projectId).stream()
                .filter(function -> function.getId() != null && function.getFuncCode() != null
                        && !function.getFuncCode().trim().isEmpty())
                .collect(Collectors.toMap(RuleFunction::getId, RuleFunction::getFuncCode, (left, right) -> left));
    }

    public Map<Long, Integer> buildFunctionArityMap(Long projectId) {
        return listByProject(projectId).stream()
                .filter(function -> function.getId() != null)
                .collect(Collectors.toMap(RuleFunction::getId,
                        function -> parseParamNames(function.getParamsJson()).size(), (left, right) -> left));
    }

    /**
     * 按项目分页查询函数（管理页面使用）
     * @param scope 作用域筛选：GLOBAL/PROJECT，null 表示不限制
     */
    public IPage<RuleFunction> pageByProject(Long projectId, int pageNum, int pageSize, String scope,
                                              String projectCode, String projectName, String funcCode, String funcLabel,
                                              String implType) {
        ProjectFilterService.ProjectMatches projectMatches = null;
        if ((projectCode != null && !projectCode.isEmpty())
                || (projectName != null && !projectName.isEmpty())) {
            projectMatches = projectFilterService.resolve(projectCode, projectName);
            if (projectMatches.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
        }
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
        if (implType != null && !implType.isEmpty()) {
            wrapper.eq(RuleFunction::getImplType, implType);
        }
        if (projectMatches != null) {
            wrapper.eq(RuleFunction::getScope, SCOPE_PROJECT)
                    .in(RuleFunction::getProjectId, projectMatches.getProjectIds());
        }
        wrapper.orderByDesc(RuleFunction::getUpdateTime)
                .orderByDesc(RuleFunction::getId);
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
                                        String projectCode, String projectName, String funcCode, String funcLabel,
                                        String implType) {
        ProjectFilterService.ProjectMatches projectMatches = null;
        if ((projectCode != null && !projectCode.isEmpty())
                || (projectName != null && !projectName.isEmpty())) {
            projectMatches = projectFilterService.resolve(projectCode, projectName);
            if (projectMatches.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
        }
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
        if (implType != null && !implType.isEmpty()) {
            wrapper.eq(RuleFunction::getImplType, implType);
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
        }
        if (projectMatches != null) {
            wrapper.eq(RuleFunction::getScope, SCOPE_PROJECT)
                    .in(RuleFunction::getProjectId, projectMatches.getProjectIds());
        }
        wrapper.orderByDesc(RuleFunction::getUpdateTime)
                .orderByDesc(RuleFunction::getId);
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

    public Object invoke(Long functionId, List<Object> args) {
        RuleFunction function = getById(functionId);
        if (function == null) {
            throw new IllegalArgumentException("转换函数不存在: " + functionId);
        }
        if (!Integer.valueOf(1).equals(function.getStatus())) {
            throw new IllegalArgumentException("转换函数已停用: " + function.getFuncCode());
        }
        return invokeSnapshot(function, args);
    }

    public Object invokeSnapshot(RuleFunction function, List<Object> args) {
        if (function == null) {
            throw new IllegalArgumentException("冻结转换函数不存在");
        }
        List<String> paramNames = parseParamNames(function.getParamsJson());
        List<Object> values = args == null ? Collections.emptyList() : args;
        if (paramNames.size() != values.size()) {
            throw new IllegalArgumentException("转换函数 " + function.getFuncCode() + " 参数数量应为 "
                    + paramNames.size() + "，实际为 " + values.size());
        }
        Map<String, Object> context = new LinkedHashMap<>();
        for (int i = 0; i < paramNames.size(); i++) {
            context.put(paramNames.get(i), values.get(i));
        }
        String implType = function.getImplType() == null ? "SCRIPT" : function.getImplType().trim().toUpperCase();
        registerFunctionIfNeeded(function, implType);
        RuleResult result = qlExpressEngine.execute(buildFunctionTestScript(function, implType), context, false);
        if (!result.isSuccess()) {
            throw new IllegalArgumentException("转换函数 " + function.getFuncCode() + " 执行失败: " + result.getErrorMessage());
        }
        return result.getResult();
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
