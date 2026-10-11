package com.hengshucredit.rule.server.governance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.*;
import com.hengshucredit.rule.server.security.ConsoleAccessGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ResourceVersionDeletionTest {
    @Test public void latestOnlyConsumerDoesNotPreventDeletingAnOlderBusinessVersion() {
        var service = new ResourceVersionManagementService();
        for (var entity : List.of(GovernedResource.class, GovernedResourceVersion.class))
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "delete-version"), entity);
        var resource = new GovernedResource(); resource.setResourceType("VARIABLE"); resource.setResourceId(7L); resource.setEffectiveVersionId(82L);
        var old = version(81L, 1); var latest = version(82L, 2);
        ReflectionTestUtils.setField(service, "access", new ConsoleAccessGuard(null, null) { @Override public boolean can(String permission) { return true; } });
        ReflectionTestUtils.setField(service, "resources", Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{GovernedResourceMapper.class}, (p, m, a) -> resource));
        ReflectionTestUtils.setField(service, "approvals", new GovernanceApprovalService() {
            @Override public GovernedResourceVersion versionHead(String type, Long id, Long anchor) { return old; }
            @Override public List<GovernedResourceVersion> versions(String type, Long id) { return List.of(latest, old); }
        });
        var impact = new ResourceVersionImpactService();
        ReflectionTestUtils.setField(impact, "consumers", Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResourceVersionConsumerMapper.class}, (p, m, a) ->
                "currentSnapshots".equals(m.getName()) ? List.of(java.util.Map.of("resourceType", "RULE", "resourceId", 9L,
                        "snapshotJson", "{\"operand\":{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":7,\"versionMode\":\"LATEST\"}}", "sourceLabel", "当前配置")) : List.of()));
        ReflectionTestUtils.setField(service, "versionImpact", impact);
        AtomicInteger writes = new AtomicInteger();
        ReflectionTestUtils.setField(service, "versions", Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{GovernedResourceVersionMapper.class}, (p, m, a) -> {
            if ("update".equals(m.getName())) { writes.incrementAndGet(); return 1; }
            return null;
        }));
        service.deleteVersion("VARIABLE", 7L, 81L, 81L, "admin");
        assertEquals(1, writes.get());
    }

    private static GovernedResourceVersion version(long id, int business) {
        var value = new GovernedResourceVersion(); value.setId(id); value.setResourceType("VARIABLE"); value.setResourceId(7L);
        value.setVersionNo(business); value.setBusinessVersion(business); value.setEffectiveStatus("ACTIVE"); value.setSnapshotJson("{\"id\":7}"); return value;
    }
}
