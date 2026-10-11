package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleBillingConfig;
import com.hengshucredit.rule.model.entity.RuleDefinition;

import java.time.LocalDateTime;
import java.util.List;

/** 恢复事务测试只替换写入端；读取端明确返回无计费项，避免未注入Mapper导致意外失败。 */
class NoPriceBillingService extends RuleBillingService {
    @Override protected List<RuleBillingConfig> findActiveEngineConfigs(
            RuleDefinition definition, LocalDateTime now, Long projectId) {
        return List.of();
    }
}
