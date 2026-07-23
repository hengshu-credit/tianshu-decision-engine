package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleFieldValidation;
import com.hengshucredit.rule.server.mapper.RuleFieldValidationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@DependsOn("schemaSyncService")
public class BuiltinFieldValidationMetadataInitializer {

    private static final Logger log =
            LoggerFactory.getLogger(BuiltinFieldValidationMetadataInitializer.class);

    @Resource
    private RuleFieldValidationMapper validationMapper;

    @PostConstruct
    public void initialize() {
        try {
            List<RuleFieldValidation> definitions = BuiltinFieldValidationCatalog.definitions();
            List<String> codes = definitions.stream()
                    .map(RuleFieldValidation::getValidationCode)
                    .collect(Collectors.toList());
            List<RuleFieldValidation> stored = validationMapper.selectList(
                    new LambdaQueryWrapper<RuleFieldValidation>()
                            .eq(RuleFieldValidation::getScope, RuleFieldValidationService.SCOPE_GLOBAL)
                            .eq(RuleFieldValidation::getProjectId, 0L)
                            .in(RuleFieldValidation::getValidationCode, codes));
            Map<String, RuleFieldValidation> existingByCode =
                    (stored == null ? Collections.<RuleFieldValidation>emptyList() : stored)
                            .stream()
                            .collect(Collectors.toMap(
                                    RuleFieldValidation::getValidationCode,
                                    Function.identity(),
                                    (left, right) -> left));

            int inserted = 0;
            int updated = 0;
            for (RuleFieldValidation definition : definitions) {
                RuleFieldValidation existing = existingByCode.get(definition.getValidationCode());
                if (existing == null) {
                    validationMapper.insert(definition);
                    inserted++;
                } else if (metadataChanged(existing, definition)) {
                    copyManagedMetadata(existing, definition);
                    validationMapper.updateById(existing);
                    updated++;
                }
            }
            if (inserted > 0 || updated > 0) {
                log.info("[BuiltinFieldValidation] 系统内置字段校验规则已同步: inserted={}, updated={}",
                        inserted, updated);
            }
        } catch (Exception e) {
            log.warn("[BuiltinFieldValidation] 同步系统内置字段校验规则失败: {}", e.getMessage());
        }
    }

    private boolean metadataChanged(RuleFieldValidation existing, RuleFieldValidation definition) {
        return !Objects.equals(existing.getValidationName(), definition.getValidationName())
                || !Objects.equals(existing.getValidationType(), definition.getValidationType())
                || !Objects.equals(existing.getValidationValue(), definition.getValidationValue())
                || !Objects.equals(existing.getErrorMessage(), definition.getErrorMessage())
                || !Objects.equals(existing.getDescription(), definition.getDescription())
                || !Objects.equals(existing.getStatus(), definition.getStatus());
    }

    private void copyManagedMetadata(RuleFieldValidation target, RuleFieldValidation definition) {
        target.setValidationName(definition.getValidationName());
        target.setValidationType(definition.getValidationType());
        target.setValidationValue(definition.getValidationValue());
        target.setErrorMessage(definition.getErrorMessage());
        target.setDescription(definition.getDescription());
        target.setStatus(definition.getStatus());
    }
}
