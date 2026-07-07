package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.server.mapper.RuleFunctionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Objects;

/**
 * 启动时补齐函数管理中的内置函数元数据，方便管理端直接查看和测试。
 */
@Service
public class BuiltinFunctionMetadataInitializer {

    private static final Logger log = LoggerFactory.getLogger(BuiltinFunctionMetadataInitializer.class);

    @Resource
    private RuleFunctionMapper functionMapper;

    @PostConstruct
    public void initialize() {
        try {
            int inserted = 0;
            int updated = 0;
            for (RuleFunction definition : BuiltinFunctionCatalog.definitions()) {
                RuleFunction existing = findExisting(definition.getFuncCode());
                if (existing == null) {
                    functionMapper.insert(definition);
                    inserted++;
                } else if (metadataChanged(existing, definition)) {
                    updateMetadata(existing, definition);
                    updated++;
                }
            }
            if (inserted > 0 || updated > 0) {
                log.info("[BuiltinFunction] 内置函数元数据已同步: inserted={}, updated={}", inserted, updated);
            }
        } catch (Exception e) {
            log.warn("[BuiltinFunction] 同步内置函数元数据失败，函数运行时注册不受影响: {}", e.getMessage());
        }
    }

    private RuleFunction findExisting(String funcCode) {
        return functionMapper.selectOne(new LambdaQueryWrapper<RuleFunction>()
                .eq(RuleFunction::getScope, RuleFunctionService.SCOPE_GLOBAL)
                .eq(RuleFunction::getProjectId, 0L)
                .eq(RuleFunction::getFuncCode, funcCode));
    }

    private boolean metadataChanged(RuleFunction existing, RuleFunction definition) {
        return !Objects.equals(existing.getFuncName(), definition.getFuncName())
                || !Objects.equals(existing.getDescription(), definition.getDescription())
                || !Objects.equals(existing.getParamsJson(), definition.getParamsJson())
                || !Objects.equals(existing.getReturnType(), definition.getReturnType())
                || !Objects.equals(existing.getImplType(), definition.getImplType())
                || !Objects.equals(existing.getImplScript(), definition.getImplScript())
                || !Objects.equals(existing.getImplClass(), definition.getImplClass())
                || !Objects.equals(existing.getImplMethod(), definition.getImplMethod())
                || !Objects.equals(existing.getImplBeanName(), definition.getImplBeanName());
    }

    private void updateMetadata(RuleFunction existing, RuleFunction definition) {
        functionMapper.update(null, new LambdaUpdateWrapper<RuleFunction>()
                .eq(RuleFunction::getId, existing.getId())
                .set(RuleFunction::getFuncName, definition.getFuncName())
                .set(RuleFunction::getDescription, definition.getDescription())
                .set(RuleFunction::getParamsJson, definition.getParamsJson())
                .set(RuleFunction::getReturnType, definition.getReturnType())
                .set(RuleFunction::getImplType, definition.getImplType())
                .set(RuleFunction::getImplScript, definition.getImplScript())
                .set(RuleFunction::getImplClass, definition.getImplClass())
                .set(RuleFunction::getImplMethod, definition.getImplMethod())
                .set(RuleFunction::getImplBeanName, definition.getImplBeanName()));
    }
}
