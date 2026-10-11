package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hengshucredit.rule.core.trace.TraceIdGenerator;
import com.hengshucredit.rule.model.entity.RuleTraceRegistry;
import com.hengshucredit.rule.server.mapper.RuleTraceRegistryMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Objects;

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

    /** 客户端传输允许同一身份重投；不能重新归属到另一项目或资源。 */
    public boolean registerClientTrace(RuleTraceRegistry registry) {
        if (registry == null || !TraceIdGenerator.isValid(registry.getTraceId())) {
            throw new ClientLogValidationException(400, "Trace ID格式不合法");
        }
        try {
            insertRegistry(registry);
            return true;
        } catch (DuplicateKeyException duplicate) {
            validateClientTrace(registry);
            return false;
        }
    }

    public void validateClientTrace(RuleTraceRegistry registry) {
        if (!sameIdentity(findRegisteredTrace(registry.getTraceId()), registry)) {
            throw new ClientLogValidationException(409, "Trace ID已关联到不同的项目或资源");
        }
    }

    protected RuleTraceRegistry findRegisteredTrace(String traceId) {
        return baseMapper.selectForUpdate(traceId);
    }

    static boolean sameIdentity(RuleTraceRegistry left, RuleTraceRegistry right) {
        return left != null && right != null
                && Objects.equals(left.getTraceType(), right.getTraceType())
                && Objects.equals(left.getScopeType(), right.getScopeType())
                && Objects.equals(left.getScopeCode(), right.getScopeCode())
                && Objects.equals(left.getProjectId(), right.getProjectId())
                && Objects.equals(left.getResourceType(), right.getResourceType())
                && Objects.equals(left.getResourceId(), right.getResourceId())
                && (left.getResourceId() != null || Objects.equals(left.getResourceCode(), right.getResourceCode()))
                && Objects.equals(left.getParentTraceId(), right.getParentTraceId());
    }
}
