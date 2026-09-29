package com.hengshucredit.rule.server.transfer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.dto.GovernanceReviewRequest;
import com.hengshucredit.rule.model.dto.GovernanceSubmitRequest;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.governance.GovernanceApprovalService;
import com.hengshucredit.rule.server.mapper.GovernedResourceMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper;
import com.hengshucredit.rule.server.service.RuleProjectService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** 通过真实 ZIP 解码和 apply 验证归属；只替换数据库及治理写入边界。 */
public class OfflineProjectBindingTest {
    @BeforeClass
    public static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), GovernedResource.class);
    }

    @Test
    public void explicitBindingIsIndependentOfAllResourcePolicies() {
        for (String policy : List.of("OVERWRITE", "REUSE", "SUFFIX")) {
            Fixture f = new Fixture();
            Map<String, Object> result = f.service.apply(bundle(), options(99L, policy, false, null, Map.of()), "tester");
            assertEquals(99L, mapping(result).get("PROJECT:7"));
            assertEquals("BOUND", ((Map<?, ?>) ((List<?>) result.get("resources")).get(0)).get("status"));
            assertEquals(false, result.get("projectCreated"));
            assertTrue(f.created.isEmpty());
            assertEquals(1, f.drafts.size());
            assertEquals(99L, f.drafts.get(0).getProjectId().longValue());
            assertEquals(99L, ((Number) snapshot(f.drafts.get(0)).get("projectId")).longValue());
        }
    }

    @Test
    public void sourceMappingControlsConflictLookupAsWellAsSnapshot() {
        Fixture f = new Fixture();
        f.seed("FUNCTION", 88L, 42L, Map.of("funcCode", "fn"));
        Map<String, Object> result = f.service.apply(bundle(), options(99L, "OVERWRITE", false, null, Map.of("7", 42L)), "tester");
        assertEquals(42L, mapping(result).get("PROJECT:7"));
        assertEquals("UPDATE", f.drafts.get(0).getAction());
        assertEquals(88L, f.drafts.get(0).getResourceId().longValue());
        assertEquals(42L, f.drafts.get(0).getProjectId().longValue());
        assertEquals(42L, ((Number) snapshot(f.drafts.get(0)).get("projectId")).longValue());
    }

    @Test
    public void newProjectDoesNotReuseASameCodeSourceProjectInTargetEnvironment() {
        Fixture f = new Fixture();
        f.seed("PROJECT", 8L, 0L, Map.of("projectCode", "source"));
        Map<String, Object> result = f.service.apply(bundle(), options(null, "REUSE", true, "new_target", Map.of()), "tester");
        assertEquals(true, result.get("projectCreated"));
        assertEquals(1, f.created.size());
        assertEquals("new_target", f.created.get(0).getProjectCode());
        assertEquals(100L, mapping(result).get("PROJECT:7"));
        assertEquals(100L, f.drafts.get(0).getProjectId().longValue());
    }

    @Test
    public void matchingProjectCodeIsNotAnImplicitBinding() {
        Fixture f = new Fixture();
        f.seed("PROJECT", 8L, 0L, Map.of("projectCode", "source"));
        assertThrows(IllegalArgumentException.class, () -> f.service.apply(bundle(),
                options(null, "REUSE", false, null, Map.of("9", 42L)), "tester"));
        assertTrue(f.drafts.isEmpty());
        assertTrue(f.created.isEmpty());
    }

    @Test
    public void missingOrDeletedTargetIsRejectedBeforeAnyWrites() {
        for (boolean deleted : List.of(false, true)) {
            Fixture f = new Fixture();
            if (deleted) f.projects.get(99L).setStatus(-1);
            else f.projects.remove(99L);
            assertThrows(IllegalArgumentException.class, () -> f.service.apply(bundle(),
                    options(99L, "SUFFIX", false, null, Map.of()), "tester"));
            assertTrue(f.drafts.isEmpty());
            assertTrue(f.created.isEmpty());
        }
    }

    @Test
    public void globalImportSkipsProjectAndIgnoresStaleCreationFlag() {
        Fixture f = new Fixture();
        TransferImportOptions options = new TransferImportOptions(99L, "GLOBAL", "REUSE", "OVERWRITE",
                "_copy", true, "must_not_create", null, Map.of());
        Map<String, Object> result = f.service.apply(bundle(), options, "tester");
        assertEquals(0L, mapping(result).get("PROJECT:7"));
        assertEquals(false, result.get("projectCreated"));
        assertTrue(f.created.isEmpty());
        assertEquals(0L, f.drafts.get(0).getProjectId().longValue());
        assertEquals("GLOBAL", snapshot(f.drafts.get(0)).get("scope"));
    }

    @Test
    public void previewDoesNotReportSourceCodeCollisionForExplicitProjectBinding() {
        Fixture f = new Fixture();
        f.seed("PROJECT", 8L, 0L, Map.of("projectCode", "source"));
        Map<String, Object> preview = f.previewService.preview(bundle(), options(99L, "OVERWRITE", false, null, Map.of()));
        assertEquals(0, preview.get("conflictCount"));
    }

    @Test
    public void previewNewProjectUsesRequestedCodeAndDoesNotRequireExistingTarget() {
        Fixture f = new Fixture();
        f.seed("PROJECT", 8L, 0L, Map.of("projectCode", "source"));
        Map<String, Object> preview = f.previewService.preview(bundle(), options(null, "REUSE", true, "new_target", Map.of()));
        assertEquals(0, preview.get("conflictCount"));
        f.seed("PROJECT", 9L, 0L, Map.of("projectCode", "new_target"));
        preview = f.previewService.preview(bundle(), options(null, "REUSE", true, "new_target", Map.of()));
        List<?> conflicts = (List<?>) preview.get("conflicts");
        assertEquals(1, conflicts.size());
        assertEquals("PROJECT:9", ((TransferConflict) conflicts.get(0)).existingResourceKey());
    }

    @Test
    public void previewUsesMappedProjectForResourceConflicts() {
        Fixture f = new Fixture();
        f.seed("FUNCTION", 88L, 42L, Map.of("funcCode", "fn"));
        var result = f.previewService.preview(bundle(), options(99L, "OVERWRITE", false, null, Map.of("7", 42L)));
        List<?> conflicts = (List<?>) result.get("conflicts");
        assertEquals(1, conflicts.size());
        assertEquals("FUNCTION:88", ((TransferConflict) conflicts.get(0)).existingResourceKey());
    }

    @Test
    public void globalResourcePackageCanCreateAndBindANewProject() {
        Fixture f = new Fixture();
        byte[] bytes = new TransferBundleCodec().encode(new TransferBundle(List.of("FUNCTION:51"), List.of(
                new TransferBundle.Resource("FUNCTION:51", Map.of("id", 51L, "funcCode", "fn", "scope", "GLOBAL", "projectId", 0L),
                        List.of(), List.of())), List.of()));
        var result = f.service.apply(bytes, options(null, "SUFFIX", true, "new_target", Map.of()), "tester");
        assertEquals(true, result.get("projectCreated"));
        assertEquals(100L, result.get("targetProjectId"));
        assertEquals("new_target", f.created.get(0).getProjectCode());
        assertEquals("PROJECT", snapshot(f.drafts.get(0)).get("scope"));
        assertEquals(100L, f.drafts.get(0).getProjectId().longValue());
    }

    @Test
    public void conflictingNewAndExistingTargetChoicesAreRejectedBeforeWrites() {
        Fixture f = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> f.service.apply(bundle(), options(99L, "REUSE", true, "new", Map.of()), "tester"));
        assertTrue(f.created.isEmpty());
        assertTrue(f.drafts.isEmpty());
    }

    private static TransferImportOptions options(Long target, String policy, boolean create, String code, Map<String, Long> bindings) {
        return new TransferImportOptions(target, "PROJECT", "REUSE", policy, "_copy", create, code, "目标项目", bindings);
    }

    private static byte[] bundle() {
        return new TransferBundleCodec().encode(new TransferBundle(List.of("FUNCTION:51"), List.of(
                new TransferBundle.Resource("FUNCTION:51", Map.of("id", 51L, "funcCode", "fn", "scope", "PROJECT", "projectId", 7L),
                        List.of(new TransferBundle.Reference("/projectId", "PROJECT:7", null)), List.of()),
                new TransferBundle.Resource("PROJECT:7", Map.of("id", 7L, "projectCode", "source", "projectName", "源项目"), List.of(), List.of())), List.of()));
    }

    private static Map<?, ?> mapping(Map<String, Object> result) { return (Map<?, ?>) result.get("resourceIdMapping"); }
    private static Map<String, Object> snapshot(GovernanceDraftRequest draft) { return CanonicalJson.readMap(draft.getSnapshotJson()); }

    static class Fixture {
        final OfflineResourceImportService service = new OfflineResourceImportService();
        final OfflineResourceTransferService previewService = new OfflineResourceTransferService();
        final Map<Long, RuleProject> projects = new HashMap<>();
        final List<RuleProject> created = new ArrayList<>();
        final List<GovernanceDraftRequest> drafts = new ArrayList<>();
        final List<GovernedResource> resources = new ArrayList<>();
        final Map<Long, GovernedResourceVersion> versions = new HashMap<>();

        Fixture() {
            for (long id : new long[]{42, 99}) { RuleProject project = new RuleProject(); project.setId(id); project.setStatus(1); projects.put(id, project); }
            ReflectionTestUtils.setField(service, "projectService", new RuleProjectService() {
                @Override public RuleProject getById(Serializable id) { return projects.get(id); }
                @Override public String createProjectWithToken(RuleProject project) {
                    project.setId(100L); created.add(project); projects.put(100L, project); return "test-only";
                }
            });
            ReflectionTestUtils.setField(service, "approvalService", new GovernanceApprovalService() {
                @Override public GovernanceApprovalRequest createDraft(GovernanceDraftRequest draft, String actor) {
                    drafts.add(draft); return request();
                }
                @Override public GovernanceApprovalRequest submit(Long id, GovernanceSubmitRequest input, String actor) { return request(); }
                @Override public GovernanceApprovalRequest approve(Long id, GovernanceReviewRequest input, String actor) { return request(); }
                private GovernanceApprovalRequest request() {
                    GovernanceDraftRequest draft = drafts.get(drafts.size() - 1);
                    GovernanceApprovalRequest result = new GovernanceApprovalRequest(); result.setId(1L);
                    result.setResourceId(draft.getResourceId() == null ? 200L : draft.getResourceId()); return result;
                }
            });
            ReflectionTestUtils.setField(service, "governedResourceMapper", Proxy.newProxyInstance(
                    GovernedResourceMapper.class.getClassLoader(), new Class<?>[]{GovernedResourceMapper.class}, (proxy, method, args) -> {
                        if (!"selectList".equals(method.getName())) throw new AssertionError(method.getName());
                        LambdaQueryWrapper<?> query = (LambdaQueryWrapper<?>) args[0]; query.getSqlSegment();
                        var values = query.getParamNameValuePairs().values();
                        return resources.stream().filter(row -> values.contains(row.getResourceType())
                                && ("PROJECT".equals(row.getResourceType()) || values.contains(row.getProjectId()))).toList();
                    }));
            ReflectionTestUtils.setField(service, "governedResourceVersionMapper", Proxy.newProxyInstance(
                    GovernedResourceVersionMapper.class.getClassLoader(), new Class<?>[]{GovernedResourceVersionMapper.class},
                    (proxy, method, args) -> versions.get(args[0])));
            ReflectionTestUtils.setField(previewService, "governedResourceMapper", ReflectionTestUtils.getField(service, "governedResourceMapper"));
            ReflectionTestUtils.setField(previewService, "governedResourceVersionMapper", ReflectionTestUtils.getField(service, "governedResourceVersionMapper"));
        }
        void seed(String type, Long id, Long projectId, Map<String, Object> config) {
            GovernedResource row = new GovernedResource(); row.setResourceType(type); row.setResourceId(id);
            row.setEffectiveStatus("ACTIVE");
            row.setProjectId(projectId); row.setEffectiveVersionId(id); resources.add(row);
            GovernedResourceVersion version = new GovernedResourceVersion(); version.setId(id);
            version.setSnapshotJson(CanonicalJson.write(config)); versions.put(id, version);
        }
    }
}
