package com.bjjw.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjjw.rule.model.entity.RuleFunction;
import com.bjjw.rule.model.entity.RuleProject;
import com.bjjw.rule.server.mapper.RuleFunctionMapper;
import com.bjjw.rule.server.mapper.RuleProjectMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RuleFunctionService {

    /** 作用域：全局 */
    public static final String SCOPE_GLOBAL = "GLOBAL";
    /** 作用域：项目级 */
    public static final String SCOPE_PROJECT = "PROJECT";

    @Resource
    private RuleFunctionMapper functionMapper;

    @Resource
    private RuleProjectMapper projectMapper;

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
            wrapper.eq(RuleFunction::getFuncCode, funcCode);
        }
        // 精确匹配函数名称
        if (funcLabel != null && !funcLabel.isEmpty()) {
            wrapper.eq(RuleFunction::getFuncName, funcLabel);
        }
        // 通过 projectCode 或 projectName 进行筛选
        if (projectCode != null && !projectCode.isEmpty()) {
            List<Long> projectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<RuleProject>().eq(RuleProject::getProjectCode, projectCode))
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
                    new LambdaQueryWrapper<RuleProject>().eq(RuleProject::getProjectName, projectName))
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
            wrapper.eq(RuleFunction::getFuncCode, funcCode);
        }
        // 精确匹配函数名称
        if (funcLabel != null && !funcLabel.isEmpty()) {
            wrapper.eq(RuleFunction::getFuncName, funcLabel);
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
                    new LambdaQueryWrapper<RuleProject>().eq(RuleProject::getProjectCode, projectCode))
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
                    new LambdaQueryWrapper<RuleProject>().eq(RuleProject::getProjectName, projectName))
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

    public void create(RuleFunction func) {
        // 确保 scope 有默认值
        if (func.getScope() == null || func.getScope().isEmpty()) {
            func.setScope(SCOPE_PROJECT);
        }
        functionMapper.insert(func);
    }

    public void update(RuleFunction func) {
        functionMapper.updateById(func);
    }

    public void delete(Long id) {
        functionMapper.deleteById(id);
    }
}
