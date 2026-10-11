package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.server.mapper.RuleFunctionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

/**
 * 启动时仅补齐缺失的内置函数；已有配置必须保留，由审批流程管理变更。
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
            for (RuleFunction definition : BuiltinFunctionCatalog.definitions()) {
                RuleFunction existing = findExisting(definition.getFuncCode());
                if (existing == null) {
                    functionMapper.insert(definition);
                    inserted++;
                }
            }
            if (inserted > 0) {
                log.info("[BuiltinFunction] 缺失内置函数元数据已补齐: inserted={}", inserted);
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

}
