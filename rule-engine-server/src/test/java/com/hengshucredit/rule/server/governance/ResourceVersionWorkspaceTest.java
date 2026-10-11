package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.GovernanceApprovalRequestMapper;
import com.hengshucredit.rule.server.security.ConsoleAccessGuard;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.Assert.*;

public class ResourceVersionWorkspaceTest {
    @Test public void unapprovedBaselineIsNotAdvertisedAsLatestProduction() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "version-workspace"), GovernanceApprovalRequest.class);
        var service = new ResourceVersionManagementService();
        var resource = new GovernedResource(); resource.setId(1L); resource.setResourceId(7L); resource.setResourceType("RULE"); resource.setEffectiveVersionId(10L);
        var version = new GovernedResourceVersion(); version.setId(10L); version.setResourceId(7L); version.setResourceType("RULE"); version.setVersionNo(1); version.setEffectiveStatus("DISABLED"); version.setSnapshotJson("{\"id\":7}");
        ReflectionTestUtils.setField(service, "bootstrap", new GovernanceResourceBootstrapService() {
            @Override public GovernedResource ensure(String type, Long id) { return resource; }
        });
        ReflectionTestUtils.setField(service, "approvals", new GovernanceApprovalService() {
            @Override public List<GovernedResourceVersion> versions(String type, Long id) { return List.of(version); }
        });
        ReflectionTestUtils.setField(service, "access", new ConsoleAccessGuard(null, null) {
            @Override public boolean can(String permission) { return true; }
        });
        ReflectionTestUtils.setField(service, "requests", Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{GovernanceApprovalRequestMapper.class}, (p, m, a) -> List.of()));
        var workspace = service.workspace("RULE", 7L, "owner");
        assertNull(workspace.get("latestVersionId"));
        assertEquals(false, ((Map<?, ?>) ((List<?>) workspace.get("versions")).get(0)).get("latest"));
        version.setEffectiveStatus("ACTIVE");
        assertEquals(10L, service.workspace("RULE", 7L, "owner").get("latestVersionId"));
    }
}
