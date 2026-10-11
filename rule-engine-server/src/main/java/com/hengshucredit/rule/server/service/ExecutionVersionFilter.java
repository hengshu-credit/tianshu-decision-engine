package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.ExecutionVersionQuery;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class ExecutionVersionFilter {
    @Resource private RulePublishedMapper published;

    public Integer resolve(ExecutionVersionQuery query) {
        if (query == null || query.getVersionMode() == null || "ALL".equals(query.getVersionMode())) return null;
        if (query.getRuleId() == null || query.getRuleId() <= 0) throw new IllegalArgumentException("版本筛选必须选择具体规则");
        if ("FIXED".equals(query.getVersionMode())) {
            if (query.getRuleVersion() == null || query.getRuleVersion() <= 0) throw new IllegalArgumentException("请选择有效的业务版本号");
            return query.getRuleVersion();
        }
        if (!"LATEST".equals(query.getVersionMode())) throw new IllegalArgumentException("版本筛选必须为 ALL、LATEST 或 FIXED");
        RulePublished latest = published.selectOne(new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId, query.getRuleId()).eq(RulePublished::getStatus, 1).last("LIMIT 1"));
        return latest == null ? -1 : latest.getVersion();
    }
}
