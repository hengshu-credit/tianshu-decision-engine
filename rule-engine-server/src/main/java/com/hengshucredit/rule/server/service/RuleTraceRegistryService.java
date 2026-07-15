package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hengshucredit.rule.core.trace.TraceIdGenerator;
import com.hengshucredit.rule.model.entity.RuleTraceRegistry;
import com.hengshucredit.rule.server.mapper.RuleTraceRegistryMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class RuleTraceRegistryService extends ServiceImpl<RuleTraceRegistryMapper, RuleTraceRegistry> {

    private static final int MAX_ALLOCATE_ATTEMPTS = 8;

    public String allocate(String typeCode, String scopeType, String scopeCode,
                           Long projectId, String resourceType, Long resourceId,
                           String resourceCode, String parentTraceId) {
        DuplicateKeyException lastCollision = null;
        for (int attempt = 0; attempt < MAX_ALLOCATE_ATTEMPTS; attempt++) {
            RuleTraceRegistry registry = new RuleTraceRegistry();
            registry.setTraceId(TraceIdGenerator.generate(typeCode, scopeType, scopeCode));
            registry.setTraceType(typeCode);
            registry.setScopeType(scopeType);
            registry.setScopeCode(scopeCode);
            registry.setProjectId(projectId);
            registry.setResourceType(resourceType);
            registry.setResourceId(resourceId);
            registry.setResourceCode(resourceCode);
            registry.setParentTraceId(parentTraceId);
            try {
                insertRegistry(registry);
                return registry.getTraceId();
            } catch (DuplicateKeyException e) {
                lastCollision = e;
            }
        }
        throw new IllegalStateException("Trace ID连续碰撞，无法开始执行", lastCollision);
    }

    public void registerExisting(RuleTraceRegistry registry) {
        if (registry == null || !TraceIdGenerator.isValid(registry.getTraceId())) {
            throw new IllegalArgumentException("Trace ID格式不合法");
        }
        try {
            insertRegistry(registry);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("Trace ID已存在: " + registry.getTraceId(), e);
        }
    }

    protected void insertRegistry(RuleTraceRegistry registry) {
        baseMapper.insert(registry);
    }
}
