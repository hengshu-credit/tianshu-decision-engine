package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.model.dto.RuleValidationResult;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;

@Service
public class BatchTestService {

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleCompileService compileService;

    @Resource
    private RuleExecuteService executeService;

    @Resource
    private RuleVariableMapper variableMapper;

    /**
     * Compile and optionally test-execute all rules in a project.
     * Returns a validation result per rule.
     */
    public List<RuleValidationResult> validateProjectRules(Long projectId) {
        List<RuleDefinition> definitions = definitionService.list(
                new LambdaQueryWrapper<RuleDefinition>()
                        .eq(RuleDefinition::getProjectId, projectId));
        Map<String, Object> defaultParams = buildDefaultParams(projectId);
        return doValidate(definitions, defaultParams);
    }

    private Map<String, Object> buildDefaultParams(Long projectId) {
        List<RuleVariable> vars = variableMapper.selectList(
                new LambdaQueryWrapper<RuleVariable>()
                        .eq(projectId != null, RuleVariable::getProjectId, projectId)
                        .eq(RuleVariable::getStatus, 1));

        Map<String, Object> params = new HashMap<>();
        for (RuleVariable v : vars) {
            String value = v.getDefaultValue() != null ? v.getDefaultValue()
                    : v.getExampleValue();
            if (value == null || value.isEmpty()) continue;

            switch (v.getVarType()) {
                case "NUMBER":
                    try { params.put(v.getVarCode(), Double.parseDouble(value)); } catch (NumberFormatException ignored) {}
                    break;
                case "BOOLEAN":
                    params.put(v.getVarCode(), Boolean.parseBoolean(value));
                    break;
                default:
                    params.put(v.getVarCode(), value);
                    break;
            }
        }
        return params;
    }

    /**
     * Compile and test-execute ALL rules (no project filter).
     * Used when user clicks "验证规则" without selecting a project.
     */
    public List<RuleValidationResult> validateAllRules() {
        List<RuleDefinition> definitions = definitionService.list();
        Map<String, Object> defaultParams = buildDefaultParams(null);
        return doValidate(definitions, defaultParams);
    }

    private List<RuleValidationResult> doValidate(List<RuleDefinition> definitions, Map<String, Object> defaultParams) {
        List<RuleValidationResult> results = new ArrayList<>();
        for (RuleDefinition def : definitions) {
            RuleValidationResult vr = new RuleValidationResult();
            vr.setDefinitionId(def.getId());
            vr.setRuleCode(def.getRuleCode());
            vr.setRuleName(def.getRuleName());
            vr.setModelType(def.getModelType());

            try {
                CompileResult cr = compileService.compile(def.getId());
                vr.setCompileOk(cr.isSuccess());
                if (!cr.isSuccess()) {
                    vr.setErrorMsg("编译失败: " + cr.getErrorMessage());
                    vr.setExecuteOk(false);
                    results.add(vr);
                    continue;
                }
            } catch (Exception e) {
                vr.setCompileOk(false);
                vr.setErrorMsg("编译异常: " + e.getMessage());
                vr.setExecuteOk(false);
                results.add(vr);
                continue;
            }

            try {
                RuleResult rr = executeService.testExecute(def.getId(), defaultParams);
                vr.setExecuteOk(rr.isSuccess());
                if (!rr.isSuccess()) {
                    vr.setErrorMsg("执行失败: " + rr.getErrorMessage());
                }
            } catch (Exception e) {
                vr.setExecuteOk(false);
                vr.setErrorMsg("执行异常: " + e.getMessage());
            }
            results.add(vr);
        }
        return results;
    }
}
