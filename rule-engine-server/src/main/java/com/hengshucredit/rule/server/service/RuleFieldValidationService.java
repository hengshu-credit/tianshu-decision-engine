package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleFieldValidation;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleFieldValidationMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RuleFieldValidationService extends ServiceImpl<RuleFieldValidationMapper, RuleFieldValidation> {

    public static final String SCOPE_GLOBAL = "GLOBAL";
    public static final String SCOPE_PROJECT = "PROJECT";
    private static final Set<String> VALIDATION_TYPES = new LinkedHashSet<>(java.util.Arrays.asList(
            "REQUIRED", "REGEX", "MIN_VALUE", "MAX_VALUE", "MIN_LENGTH", "MAX_LENGTH", "IN", "NOT_IN"));

    @Resource
    private RuleDefinitionInputFieldMapper inputFieldMapper;

    @Resource
    private RuleProjectMapper projectMapper;

    @Resource
    private FieldValidationEvaluator evaluator;

    public IPage<RuleFieldValidation> pageList(int pageNum, int pageSize, Long projectId,
                                               String scope, String validationType, String keyword) {
        LambdaQueryWrapper<RuleFieldValidation> query = new LambdaQueryWrapper<>();
        if (hasText(scope)) query.eq(RuleFieldValidation::getScope, scope.trim().toUpperCase(Locale.ROOT));
        if (projectId != null && projectId > 0) {
            query.eq(RuleFieldValidation::getProjectId, projectId);
        }
        if (hasText(validationType)) {
            query.eq(RuleFieldValidation::getValidationType, validationType.trim().toUpperCase(Locale.ROOT));
        }
        if (hasText(keyword)) {
            query.and(w -> w.like(RuleFieldValidation::getValidationCode, keyword.trim()).or()
                    .like(RuleFieldValidation::getValidationName, keyword.trim()));
        }
        query.orderByDesc(RuleFieldValidation::getUpdateTime).orderByDesc(RuleFieldValidation::getId);
        IPage<RuleFieldValidation> page = page(new Page<>(pageNum, pageSize), query);
        decorateRules(page.getRecords());
        return page;
    }

    public List<RuleFieldValidation> listAvailable(Long projectId) {
        LambdaQueryWrapper<RuleFieldValidation> query = new LambdaQueryWrapper<RuleFieldValidation>()
                .eq(RuleFieldValidation::getStatus, 1);
        if (projectId != null && projectId > 0) {
            query.and(w -> w.eq(RuleFieldValidation::getScope, SCOPE_GLOBAL).or()
                    .eq(RuleFieldValidation::getScope, SCOPE_PROJECT)
                    .eq(RuleFieldValidation::getProjectId, projectId));
        } else {
            query.eq(RuleFieldValidation::getScope, SCOPE_GLOBAL);
        }
        query.orderByAsc(RuleFieldValidation::getScope)
                .orderByAsc(RuleFieldValidation::getValidationCode);
        List<RuleFieldValidation> result = list(query);
        decorateRules(result);
        return result;
    }

    @Transactional
    public RuleFieldValidation createRule(RuleFieldValidation rule) {
        normalizeAndValidate(rule);
        rejectReservedCode(rule.getValidationCode());
        ensureUnique(rule);
        if (rule.getStatus() == null) rule.setStatus(1);
        save(rule);
        return rule;
    }

    @Transactional
    public void updateRule(RuleFieldValidation rule) {
        RuleFieldValidation existing =
                rule == null || rule.getId() == null ? null : getById(rule.getId());
        if (existing == null) {
            throw new IllegalArgumentException("字段校验规则不存在");
        }
        if (BuiltinFieldValidationCatalog.isBuiltin(existing)) {
            throw new IllegalArgumentException("系统内置校验规则不可编辑");
        }
        normalizeAndValidate(rule);
        rejectReservedCode(rule.getValidationCode());
        ensureUnique(rule);
        updateById(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        RuleFieldValidation existing = id == null ? null : getById(id);
        if (existing == null) throw new IllegalArgumentException("字段校验规则不存在");
        if (BuiltinFieldValidationCatalog.isBuiltin(existing)) {
            throw new IllegalArgumentException("系统内置校验规则不可删除");
        }
        List<RuleDefinitionInputField> fields = inputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionInputField>()
                        .isNotNull(RuleDefinitionInputField::getValidationRuleIds));
        for (RuleDefinitionInputField field : fields == null
                ? Collections.<RuleDefinitionInputField>emptyList() : fields) {
            if (parseIds(field.getValidationRuleIds()).contains(id)) {
                throw new IllegalArgumentException("字段校验规则已被规则输入字段引用，不能删除");
            }
        }
        removeById(id);
    }

    public String validateRuleIds(Long projectId, String json) {
        List<Long> ids = parseIds(json);
        if (ids.size() > 64) throw new IllegalArgumentException("单个字段最多关联64条校验规则");
        if (ids.isEmpty()) return "[]";
        List<RuleFieldValidation> rules = listByIds(ids);
        Map<Long, RuleFieldValidation> byId = rules.stream().collect(
                Collectors.toMap(RuleFieldValidation::getId, Function.identity()));
        for (Long id : ids) {
            RuleFieldValidation rule = byId.get(id);
            if (rule == null || Integer.valueOf(0).equals(rule.getStatus()) || !visibleToProject(rule, projectId)) {
                throw new IllegalArgumentException("字段校验规则不存在、已停用或不属于当前项目: " + id);
            }
        }
        return JSON.toJSONString(ids);
    }

    public void validateDefinitionInput(Long definitionId, Map<String, Object> params) {
        List<RuleDefinitionInputField> fields = inputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionInputField>()
                        .eq(RuleDefinitionInputField::getDefinitionId, definitionId)
                        .and(w -> w.isNull(RuleDefinitionInputField::getStatus)
                                .or().eq(RuleDefinitionInputField::getStatus, 1))
                        .orderByAsc(RuleDefinitionInputField::getSortOrder)
                        .orderByAsc(RuleDefinitionInputField::getId));
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (RuleDefinitionInputField field : fields == null
                ? Collections.<RuleDefinitionInputField>emptyList() : fields) {
            ids.addAll(parseIds(field.getValidationRuleIds()));
        }
        if (ids.isEmpty()) return;
        List<RuleFieldValidation> rules = listByIds(ids);
        List<FieldValidationViolation> violations = evaluator.validate(fields, rules, params);
        if (!violations.isEmpty()) throw new FieldValidationException(violations);
    }

    private void normalizeAndValidate(RuleFieldValidation rule) {
        if (rule == null) throw new IllegalArgumentException("字段校验规则不能为空");
        rule.setValidationCode(required(rule.getValidationCode(), "校验编码不能为空"));
        rule.setValidationName(required(rule.getValidationName(), "校验名称不能为空"));
        rule.setErrorMessage(required(rule.getErrorMessage(), "错误提示不能为空"));
        String type = required(rule.getValidationType(), "校验类型不能为空")
                .trim().toUpperCase(Locale.ROOT);
        if (!VALIDATION_TYPES.contains(type)) throw new IllegalArgumentException("不支持的字段校验类型: " + type);
        rule.setValidationType(type);
        String scope = hasText(rule.getScope()) ? rule.getScope().trim().toUpperCase(Locale.ROOT) : SCOPE_PROJECT;
        if (!SCOPE_GLOBAL.equals(scope) && !SCOPE_PROJECT.equals(scope)) {
            throw new IllegalArgumentException("作用范围只能是GLOBAL或PROJECT");
        }
        rule.setScope(scope);
        if (SCOPE_GLOBAL.equals(scope)) {
            rule.setProjectId(0L);
        } else if (rule.getProjectId() == null || rule.getProjectId() <= 0
                || projectMapper.selectById(rule.getProjectId()) == null) {
            throw new IllegalArgumentException("项目级字段校验必须选择有效项目");
        }
        validateValue(type, rule.getValidationValue());
    }

    private void validateValue(String type, String value) {
        if ("REQUIRED".equals(type)) return;
        String expected = required(value, "当前校验类型必须填写校验值");
        if ("REGEX".equals(type)) Pattern.compile(expected);
        if ("MIN_VALUE".equals(type) || "MAX_VALUE".equals(type)) new BigDecimal(expected.trim());
        if ("MIN_LENGTH".equals(type) || "MAX_LENGTH".equals(type)) {
            if (Integer.parseInt(expected.trim()) < 0) throw new IllegalArgumentException("长度不能小于0");
        }
        if ("IN".equals(type) || "NOT_IN".equals(type)) {
            String trimmed = expected.trim();
            List<Object> values = trimmed.startsWith("[")
                    ? JSON.parseArray(trimmed, Object.class) : java.util.Arrays.asList(trimmed.split(","));
            if (values.isEmpty()) throw new IllegalArgumentException("集合校验值不能为空");
        }
    }

    private void ensureUnique(RuleFieldValidation rule) {
        LambdaQueryWrapper<RuleFieldValidation> query = new LambdaQueryWrapper<RuleFieldValidation>()
                .eq(RuleFieldValidation::getScope, rule.getScope())
                .eq(RuleFieldValidation::getProjectId, rule.getProjectId())
                .eq(RuleFieldValidation::getValidationCode, rule.getValidationCode());
        if (rule.getId() != null) query.ne(RuleFieldValidation::getId, rule.getId());
        if (count(query) > 0) throw new IllegalArgumentException("同一作用范围内校验编码已存在");
    }

    private boolean visibleToProject(RuleFieldValidation rule, Long projectId) {
        if (SCOPE_GLOBAL.equals(rule.getScope())) return true;
        return projectId != null && projectId > 0 && projectId.equals(rule.getProjectId());
    }

    private List<Long> parseIds(String json) {
        if (!hasText(json)) return Collections.emptyList();
        try {
            List<Long> parsed = JSON.parseArray(json, Long.class);
            if (parsed == null) return Collections.emptyList();
            return new ArrayList<>(new LinkedHashSet<>(parsed));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("字段校验规则ID必须是JSON数组", e);
        }
    }

    private void decorateRules(List<RuleFieldValidation> rules) {
        if (rules == null || rules.isEmpty()) return;
        for (RuleFieldValidation rule : rules) {
            rule.setBuiltIn(BuiltinFieldValidationCatalog.isBuiltin(rule));
        }
        List<Long> projectIds = rules.stream().map(RuleFieldValidation::getProjectId)
                .filter(id -> id != null && id > 0).distinct().collect(Collectors.toList());
        if (projectIds.isEmpty()) return;
        Map<Long, String> names = projectMapper.selectBatchIds(projectIds).stream().collect(
                Collectors.toMap(RuleProject::getId, RuleProject::getProjectName, (left, right) -> left));
        for (RuleFieldValidation rule : rules) rule.setProjectName(names.get(rule.getProjectId()));
    }

    private void rejectReservedCode(String code) {
        if (BuiltinFieldValidationCatalog.isReservedCode(code)) {
            throw new IllegalArgumentException("校验编码为系统内置规则保留");
        }
    }

    private String required(String value, String message) {
        if (!hasText(value)) throw new IllegalArgumentException(message);
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
