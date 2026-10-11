package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class GovernanceDependencyServiceTest {

    @Test
    public void fixedDependencyResolvesItsBusinessLineAndIgnoresUnrelatedLatestPublication() {
        TestService service = fixedService(81L);
        var selection = new DependencyVersionSelection("VARIABLE", 91L, "BUSINESS_VERSION", 71L);
        var report = service.preflight("RULE", 7L, List.of(new ResourceDependencyRef(
                "VARIABLE", 91L, "VARIABLE", "$.operand.refId", "REFERENCES", true, selection)), List.of());
        Assert.assertTrue(report.valid());
        Assert.assertEquals(Long.valueOf(81), report.dependencies().get(0).targetVersionId());
        Assert.assertEquals(Integer.valueOf(2), report.dependencies().get(0).targetVersionNo());
        Assert.assertEquals(selection, report.dependencies().get(0).versionSelection());
        service.target.setEffectiveVersionId(99L);
        Assert.assertTrue("最新版变更不影响固定业务版本", service.revalidate(report.dependencies()).valid());
    }

    @Test
    public void fixedDependencyOverwriteConflictsEvenWhenLatestIsUnchanged() {
        TestService service = fixedService(82L);
        var submitted = new GovernancePreflightReport.ResolvedDependency("VARIABLE", 91L, 81L, 2,
                "$.operand.refId", "REFERENCES", true, "RESOLVED", "fixed-digest", null, null,
                new DependencyVersionSelection("VARIABLE", 91L, "BUSINESS_VERSION", 71L));
        var report = service.revalidate(List.of(submitted));
        Assert.assertFalse(report.valid());
        Assert.assertEquals("DEPENDENCY_VERSION_CHANGED", report.errors().get(0).code());
        Assert.assertEquals(Long.valueOf(82), report.dependencies().get(0).targetVersionId());
    }

    private static TestService fixedService(long resolvedId) {
        TestService service = new TestService();
        service.target = resource(91L, 92L, 3, "ACTIVE"); service.target.setResourceType("VARIABLE");
        service.version = new GovernedResourceVersion(); service.version.setId(92L); service.version.setSnapshotDigest("latest-digest");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "resourceVersions", new com.hengshucredit.rule.server.service.ResourceVersionService() {
            @Override public com.alibaba.fastjson.JSONObject snapshot(com.hengshucredit.rule.core.engine.ResourceVersionCall call, Long projectId) {
                Assert.assertEquals("BUSINESS_VERSION", call.versionSource());
                Assert.assertEquals(Long.valueOf(71), call.versionId());
                return com.alibaba.fastjson.JSON.parseObject("{\"ownerId\":91,\"resolvedVersionId\":" + resolvedId + ",\"displayVersion\":2,\"digest\":\"fixed-digest\"}");
            }
        });
        return service;
    }

    @Test
    public void fixedSelectionSurvivesDependencyPersistenceAndDeletionIsRechecked() {
        TestService service = fixedService(81L);
        var selection = new DependencyVersionSelection("VARIABLE", 91L, "BUSINESS_VERSION", 71L);
        var report = service.preflight("RULE", 7L, List.of(new ResourceDependencyRef("VARIABLE", 91L,
                "VARIABLE", "$.refId", "REFERENCES", true, selection)), List.of());
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "fixed-dependency"), com.hengshucredit.rule.model.entity.GovernanceDependencySnapshot.class);
        java.util.List<com.hengshucredit.rule.model.entity.GovernanceDependencySnapshot> stored = new java.util.ArrayList<>();
        var mapper = java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{com.hengshucredit.rule.server.mapper.GovernanceDependencySnapshotMapper.class}, (proxy, method, args) -> {
                    if ("insert".equals(method.getName())) { stored.add((com.hengshucredit.rule.model.entity.GovernanceDependencySnapshot) args[0]); return 1; }
                    if ("selectList".equals(method.getName())) return stored;
                    return 0;
                });
        org.springframework.test.util.ReflectionTestUtils.setField(service, "dependencySnapshotMapper", mapper);
        var request = new com.hengshucredit.rule.model.entity.GovernanceApprovalRequest(); request.setId(11L); request.setResourceType("RULE"); request.setResourceId(7L);
        service.persist(request, report, null);
        Assert.assertEquals(selection, service.load(11L).get(0).versionSelection());
        Assert.assertTrue(service.revalidate(service.load(11L)).valid());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "resourceVersions", new com.hengshucredit.rule.server.service.ResourceVersionService() {
            @Override public com.alibaba.fastjson.JSONObject snapshot(com.hengshucredit.rule.core.engine.ResourceVersionCall call, Long projectId) {
                throw new IllegalArgumentException("指定业务版本已删除或不存在");
            }
        });
        Assert.assertEquals("DEPENDENCY_VERSION_UNAVAILABLE", service.revalidate(service.load(11L)).errors().get(0).code());
    }

    @Test
    public void fixedObjectFieldResolvesHistoricalOwnerInsteadOfLookingUpTheCurrentField() {
        TestService service = new TestService(); service.target = resource(91L, 92L, 3, "ACTIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "resourceVersions", new com.hengshucredit.rule.server.service.ResourceVersionService() {
            @Override public com.alibaba.fastjson.JSONObject snapshot(com.hengshucredit.rule.core.engine.ResourceVersionCall call, Long projectId) {
                Assert.assertEquals(Long.valueOf(9001), call.refId());
                return com.alibaba.fastjson.JSON.parseObject("{\"ownerId\":91,\"resolvedVersionId\":81,\"displayVersion\":2,\"digest\":\"fixed-digest\"}");
            }
        });
        var report = service.preflight("RULE", 7L, List.of(new ResourceDependencyRef("DATA_OBJECT", 9001L, "DATA_OBJECT",
                "$.refId", "REFERENCES", true, new DependencyVersionSelection("DATA_OBJECT", 9001L, "BUSINESS_VERSION", 71L))), List.of());
        Assert.assertTrue(report.valid());
        Assert.assertEquals(Long.valueOf(91), report.dependencies().get(0).targetResourceId());
        Assert.assertEquals(Long.valueOf(9001), report.dependencies().get(0).versionSelection().refId());
    }

    @Test
    public void fixedRuleBindingTracksItsNativeSnapshotRatherThanTheGovernanceLatest() {
        TestService service = new TestService(); service.target = resource(91L, 92L, 3, "ACTIVE"); service.target.setResourceType("RULE");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "ruleVersions", new com.hengshucredit.rule.server.service.RuleVersionBindingService() {
            @Override public com.hengshucredit.rule.model.entity.RuleDefinitionVersion resolveSnapshot(Long id, Long bindingId) {
                Assert.assertEquals(Long.valueOf(91), id); Assert.assertEquals(Long.valueOf(71), bindingId);
                var version = new com.hengshucredit.rule.model.entity.RuleDefinitionVersion(); version.setId(81L); version.setVersion(5);
                version.setBusinessVersion(2); version.setArtifactId(801L); version.setArtifactDigest("artifact-v2"); return version;
            }
        });
        var report = service.preflight("EXPERIMENT", 7L, List.of(new ResourceDependencyRef("RULE", 91L, "RULE",
                "$.groups[0].ruleId", "REFERENCES", true, new DependencyVersionSelection("RULE", 91L, "RULE_BINDING", 71L))), List.of());
        Assert.assertTrue(report.valid());
        Assert.assertEquals(Integer.valueOf(2), report.dependencies().get(0).targetVersionNo());
        Assert.assertEquals(Long.valueOf(81), report.dependencies().get(0).targetVersionId());
        service.target.setEffectiveVersionId(99L);
        Assert.assertTrue(service.revalidate(report.dependencies()).valid());
    }

    @Test
    public void immutableSnapshotSelectionUsesTheRuntimeSnapshotNamespace() {
        TestService service = new TestService(); service.target = resource(91L, 92L, 3, "ACTIVE");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "resourceVersions", new com.hengshucredit.rule.server.service.ResourceVersionService() {
            @Override public com.alibaba.fastjson.JSONObject snapshot(com.hengshucredit.rule.core.engine.ResourceVersionCall call, Long projectId) {
                Assert.assertNull(call.versionSource());
                return com.alibaba.fastjson.JSON.parseObject("{\"ownerId\":91,\"versionId\":71,\"displayVersion\":2,\"digest\":\"fixed-digest\"}");
            }
        });
        var report = service.preflight("RULE", 7L, List.of(new ResourceDependencyRef("VARIABLE", 91L, "VARIABLE",
                "$.refId", "REFERENCES", true, new DependencyVersionSelection("VARIABLE", 91L, "SNAPSHOT", 71L))), List.of());
        Assert.assertTrue("不可变快照仍须可审批", report.valid());
        Assert.assertEquals(Long.valueOf(71), report.dependencies().get(0).targetVersionId());
    }

    @Test
    public void approvedButUnpublishedDependencyCannotBeReferenced() {
        TestService service = new TestService();
        service.target = resource(91L, 15L, 4, "APPROVED");
        service.version = new GovernedResourceVersion();
        service.version.setId(15L);
        var report = service.preflight("RULE", 7L, List.of(new ResourceDependencyRef(
                "MODEL", 91L, "MODEL", "$.modelId", "REFERENCES", true)), List.of());
        Assert.assertFalse("审批通过不等于已发布", report.valid());
    }

    @Test
    public void missingRequiredDependencyBlocksSubmission() {
        TestService service = new TestService();
        ResourceDependencyRef dependency = new ResourceDependencyRef(
                "VARIABLE", 91L, "VARIABLE", "$.conditions[0].left",
                "REFERENCES", true);

        GovernancePreflightReport report = service.preflight(
                "RULE", 7L, List.of(dependency), List.of());

        Assert.assertFalse(report.valid());
        Assert.assertEquals("DEPENDENCY_MISSING",
                report.errors().get(0).code());
        Assert.assertEquals("MISSING",
                report.dependencies().get(0).resolutionStatus());
    }

    @Test
    public void inactiveDependencyBlocksSubmission() {
        TestService service = new TestService();
        service.target = resource(91L, 15L, 4, "OFFLINE");
        ResourceDependencyRef dependency = new ResourceDependencyRef(
                "MODEL", 91L, "MODEL", "$.modelId",
                "REFERENCES", true);

        GovernancePreflightReport report = service.preflight(
                "RULE", 7L, List.of(dependency), List.of());

        Assert.assertFalse(report.valid());
        Assert.assertEquals("DEPENDENCY_INACTIVE",
                report.errors().get(0).code());
    }

    @Test
    public void effectiveDependencyRecordsExactVersionAndDigest() {
        TestService service = new TestService();
        service.target = resource(91L, 15L, 4, "ACTIVE");
        service.version = new GovernedResourceVersion();
        service.version.setId(15L);
        service.version.setVersionNo(4);
        service.version.setSnapshotDigest("digest-v4");
        ResourceDependencyRef dependency = new ResourceDependencyRef(
                "MODEL", 91L, "MODEL", "$.modelId",
                "REFERENCES", true);

        GovernancePreflightReport report = service.preflight(
                "RULE", 7L, List.of(dependency), List.of());

        Assert.assertTrue(report.valid());
        Assert.assertEquals(Long.valueOf(15L),
                report.dependencies().get(0).targetVersionId());
        Assert.assertEquals("digest-v4",
                report.dependencies().get(0).targetDigest());
        Assert.assertNotNull(report.dependencyDigest());
    }

    @Test
    public void changedDependencyVersionConflictsAtApprovalTime() {
        TestService service = new TestService();
        service.target = resource(91L, 16L, 5, "ACTIVE");
        GovernancePreflightReport.ResolvedDependency submitted =
                new GovernancePreflightReport.ResolvedDependency(
                        "MODEL", 91L, 15L, 4, "$.modelId",
                        "REFERENCES", true, "RESOLVED", "digest-v4",
                        null, null);

        GovernancePreflightReport report = service.revalidate(
                List.of(submitted));

        Assert.assertFalse(report.valid());
        Assert.assertEquals("DEPENDENCY_VERSION_CHANGED",
                report.errors().get(0).code());
    }

    @Test
    public void changedDigestOnSameVersionConflictsAtApprovalTime() {
        TestService service = new TestService();
        service.target = resource(91L, 15L, 4, "ACTIVE");
        service.version = new GovernedResourceVersion();
        service.version.setId(15L);
        service.version.setVersionNo(4);
        service.version.setSnapshotDigest("digest-tampered");
        GovernancePreflightReport.ResolvedDependency submitted =
                new GovernancePreflightReport.ResolvedDependency(
                        "MODEL", 91L, 15L, 4, "$.modelId",
                        "REFERENCES", true, "RESOLVED", "digest-v4",
                        null, null);

        GovernancePreflightReport report = service.revalidate(
                List.of(submitted));

        Assert.assertFalse(report.valid());
        Assert.assertEquals("DEPENDENCY_DIGEST_CHANGED",
                report.errors().get(0).code());
    }

    @Test
    public void explicitReferenceTypeMismatchBlocksSubmission() {
        TestService service = new TestService();
        ResourceDependencyRef dependency = new ResourceDependencyRef(
                "MODEL", 91L, "VARIABLE", "$.modelId",
                "REFERENCES", true);

        GovernancePreflightReport report = service.preflight(
                "RULE", 7L, List.of(dependency), List.of());

        Assert.assertFalse(report.valid());
        Assert.assertEquals("DEPENDENCY_TYPE_MISMATCH",
                report.errors().get(0).code());
    }

    private static GovernedResource resource(Long resourceId,
                                             Long versionId,
                                             Integer versionNo,
                                             String status) {
        GovernedResource resource = new GovernedResource();
        resource.setResourceType("MODEL");
        resource.setResourceId(resourceId);
        resource.setEffectiveVersionId(versionId);
        resource.setEffectiveVersionNo(versionNo);
        resource.setEffectiveStatus(status);
        return resource;
    }

    private static class TestService extends GovernanceDependencyService {
        private GovernedResource target;
        private GovernedResourceVersion version;

        @Override
        protected GovernedResource findTarget(String resourceType,
                                              Long resourceId) {
            return target;
        }

        @Override
        protected GovernedResourceVersion findVersion(Long versionId) {
            return version;
        }
    }
}
