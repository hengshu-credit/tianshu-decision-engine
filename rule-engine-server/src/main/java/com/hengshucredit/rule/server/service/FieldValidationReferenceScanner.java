package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleExperiment;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleExperimentMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 扫描所有已经落库的字段校验绑定，供生命周期保护使用。 */
@Service
public class FieldValidationReferenceScanner {

    @Resource
    private RuleDefinitionInputFieldMapper inputFieldMapper;

    @Resource
    private RuleExternalApiConfigMapper apiConfigMapper;

    @Resource
    private RuleExperimentMapper experimentMapper;

    public boolean referenced(Long validationId) {
        if (validationId == null) return false;
        if (referencedByRuleInputs(validationId)) return true;
        if (referencedByExternalApis(validationId)) return true;
        return referencedByExperiments(validationId);
    }

    static boolean containsValidationRuleId(Object value, Long validationId) {
        if (value == null || validationId == null) return false;
        if (value instanceof Number number) {
            return validationId.equals(number.longValue());
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (!(trimmed.startsWith("[") || trimmed.matches("-?\\d+"))) {
                return false;
            }
            try {
                return containsValidationRuleId(JSON.parse(trimmed), validationId);
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object child = entry.getValue();
                if ("validationRuleIds".equals(key)
                        && containsId(child, validationId)) return true;
                if (containsValidationRuleId(child, validationId)) return true;
            }
            return false;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object child : iterable) {
                if (containsValidationRuleId(child, validationId)) return true;
            }
        }
        return false;
    }

    private boolean referencedByRuleInputs(Long validationId) {
        List<RuleDefinitionInputField> fields = inputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionInputField>()
                        .inSql(RuleDefinitionInputField::getDefinitionId,
                                "SELECT id FROM rule_definition WHERE status <> -1")
                        .isNotNull(RuleDefinitionInputField::getValidationRuleIds));
        for (RuleDefinitionInputField field : fields == null
                ? Collections.<RuleDefinitionInputField>emptyList() : fields) {
            if (containsId(field.getValidationRuleIds(), validationId)) return true;
        }
        return false;
    }

    private boolean referencedByExternalApis(Long validationId) {
        List<RuleExternalApiConfig> configs = apiConfigMapper.selectList(
                new LambdaQueryWrapper<RuleExternalApiConfig>()
                        .ne(RuleExternalApiConfig::getStatus, -1)
                        .isNotNull(RuleExternalApiConfig::getExecutionConfig));
        for (RuleExternalApiConfig config : configs == null
                ? Collections.<RuleExternalApiConfig>emptyList() : configs) {
            if (containsJsonReference(config.getExecutionConfig(), validationId)) return true;
        }
        return false;
    }

    private boolean referencedByExperiments(Long validationId) {
        List<RuleExperiment> experiments = experimentMapper.selectList(
                new LambdaQueryWrapper<RuleExperiment>()
                        .ne(RuleExperiment::getStatus, -1)
                        .isNotNull(RuleExperiment::getInputValidationConfig));
        for (RuleExperiment experiment : experiments == null
                ? Collections.<RuleExperiment>emptyList() : experiments) {
            if (containsJsonReference(experiment.getInputValidationConfig(), validationId)) return true;
        }
        return false;
    }

    private boolean containsJsonReference(String text, Long validationId) {
        if (text == null || text.isBlank()) return false;
        try {
            return containsValidationRuleId(JSON.parse(text), validationId);
        } catch (RuntimeException invalidJson) {
            // 配置本身无效时保持保守策略，避免删除规则后留下无法追溯的引用。
            return true;
        }
    }

    private static boolean containsId(Object value, Long validationId) {
        if (value instanceof Number number) return validationId.equals(number.longValue());
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) return false;
            if (!(trimmed.startsWith("[") || trimmed.matches("-?\\d+"))) return false;
            try {
                Object parsed = JSON.parse(trimmed);
                return containsId(parsed, validationId);
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) if (containsId(item, validationId)) return true;
        }
        return false;
    }
}
