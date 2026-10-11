package com.hengshucredit.rule.server.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.mapper.GovernedResourceMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Service
public class GovernanceResourceBootstrapService {

    @Resource
    private GovernedResourceMapper resourceMapper;
    @Resource
    private GovernedResourceVersionMapper versionMapper;
    @Resource
    private GovernedResourceAdapterRegistry adapterRegistry;

    @Transactional
    public GovernedResource ensure(String resourceType,
                                   Long resourceId) {
        String type = normalizeType(resourceType);
        GovernedResource existing = find(type, resourceId);
        if (existing != null) {
            return existing;
        }
        ResourceSnapshot snapshot = adapterRegistry.require(type)
                .loadEffective(resourceId);
        GovernedResource resource = new GovernedResource();
        resource.setResourceType(type);
        resource.setResourceId(resourceId);
        resource.setProjectId(projectId(type, resourceId,
                snapshot.snapshotJson()));
        resource.setEffectiveVersionNo(0);
        resource.setEffectiveStatus(snapshot.effectiveStatus());
        try {
            resourceMapper.insert(resource);
        } catch (DuplicateKeyException conflict) {
            GovernedResource concurrent = find(type, resourceId);
            if (concurrent != null) {
                return concurrent;
            }
            throw conflict;
        }

        GovernedResourceVersion version =
                new GovernedResourceVersion();
        version.setGovernedResourceId(resource.getId());
        version.setResourceType(type);
        version.setResourceId(resourceId);
        version.setVersionNo(1);
        Object businessVersion = CanonicalJson.readMap(snapshot.snapshotJson()).get("businessVersion");
        if (businessVersion instanceof Number number && number.intValue() > 0) version.setBusinessVersion(number.intValue());
        version.setSnapshotJson(snapshot.snapshotJson());
        version.setSnapshotDigest(
                Sha256Digests.text(snapshot.snapshotJson()));
        version.setSecretPayloadCiphertext(
                snapshot.secretPayloadCiphertext());
        version.setSecretDigest(snapshot.secretDigest());
        version.setEffectiveStatus(snapshot.effectiveStatus());
        version.setChangeSummary("接入统一生命周期时建立基准版本");
        version.setLegacySourceType(type);
        version.setLegacySourceId(resourceId);
        version.setCreateBy("SYSTEM_MIGRATION");
        versionMapper.insert(version);

        resource.setEffectiveVersionId(version.getId());
        resource.setEffectiveVersionNo(version.getDisplayVersion());
        resourceMapper.updateById(resource);
        return resource;
    }

    private GovernedResource find(String resourceType,
                                  Long resourceId) {
        return resourceMapper.selectOne(new LambdaQueryWrapper<
                        GovernedResource>()
                        .eq(GovernedResource::getResourceType,
                                resourceType)
                        .eq(GovernedResource::getResourceId, resourceId)
                        .last("LIMIT 1"));
    }

    private Long projectId(String resourceType,
                           Long resourceId,
                           String snapshotJson) {
        if (GovernanceResourceTypes.PROJECT.equals(resourceType)) {
            return resourceId;
        }
        Map<String, Object> value =
                CanonicalJson.readMap(snapshotJson);
        Object projectId = value.get("projectId");
        if (projectId instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private String normalizeType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("资源类型不能为空");
        }
        return resourceType.trim().toUpperCase(Locale.ROOT);
    }
}
