package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.RuleTestExecutionIdentity;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService;
import com.hengshucredit.rule.server.controller.mgmt.RuleDefinitionController;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import com.hengshucredit.rule.server.security.RequirePermission;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class RuleReferenceCatalogServiceTest {
    @Test public void pinnedScriptMetadataKeepsVersionIdentityAndExcludesImplementations() {
        Fixture fixture = new Fixture();
        fixture.snapshot.getResourceVersions().put("VARIABLE:548:948", com.alibaba.fastjson.JSON.parseObject("{\"refType\":\"VARIABLE\",\"refId\":548,\"versionId\":948,\"versionSource\":\"BUSINESS_VERSION\",\"version\":7,\"displayVersion\":3,\"payload\":{\"id\":548,\"varCode\":\"Age_Original\",\"varLabel\":\"历史年龄\",\"varType\":\"INTEGER\",\"sourceConfig\":\"SECRET\"}}"));
        var catalog = fixture.service.getCatalog(101L, "REVISION", "201");
        var item = catalog.getVersionReferences().get(0);
        assertEquals("548", item.get("refId")); assertEquals("948", item.get("versionId"));
        assertEquals("BUSINESS_VERSION", item.get("versionSource")); assertEquals("Age_Original", item.get("code"));
        assertEquals("历史年龄", item.get("label")); assertEquals("INTEGER", item.get("valueType"));
        assertEquals(3, item.get("displayVersion"));
        assertFalse(com.alibaba.fastjson.JSON.toJSONString(item).contains("SECRET"));
    }
    @Test public void localPreArtifactVersionCanBeReadAndEditedWithoutInventingFrozenDependencies() {
        Fixture fixture = new Fixture();
        fixture.version = new RuleDefinitionVersion();
        fixture.version.setId(301L); fixture.version.setDefinitionId(101L); fixture.version.setVersion(2);
        var catalog = fixture.service.getCatalog(101L, "VERSION", "301");
        assertEquals("AVAILABLE", catalog.getStatus());
        assertEquals("LOCAL", catalog.getReferenceNamespace());
        assertTrue(catalog.isLocalEditingAllowed());
        assertEquals("VERSION", catalog.getExecutionIdentity().getSourceType());
        assertEquals("301", catalog.getExecutionIdentity().getSourceId());
        assertEquals(Integer.valueOf(2), catalog.getExecutionIdentity().getVersionNo());
        assertTrue(catalog.getVariables().isEmpty());
        assertEquals(0, fixture.resolvedTargets);
    }

    @Test public void legacyVersionWithUnverifiableOriginCannotUseCurrentEnvironmentReferences() {
        Fixture fixture = new Fixture();
        fixture.version = new RuleDefinitionVersion();
        fixture.version.setId(301L); fixture.version.setDefinitionId(101L); fixture.version.setVersion(2);
        fixture.foreignOrigin = true;
        var catalog = fixture.service.getCatalog(101L, "VERSION", "301");
        assertEquals("UNAVAILABLE", catalog.getStatus());
        assertFalse(catalog.isLocalEditingAllowed());
        assertEquals(0, fixture.resolvedTargets);
    }

    @Test public void localUnpublishedRevisionWithoutArtifactCanStillBeEdited() {
        Fixture fixture = new Fixture();
        fixture.revision.setState("REJECTED"); fixture.revision.setArtifactId(null);
        var catalog = fixture.service.getCatalog(101L, "REVISION", "201");
        assertEquals("AVAILABLE", catalog.getStatus());
        assertEquals("LOCAL", catalog.getReferenceNamespace());
        assertTrue(catalog.isLocalEditingAllowed());
        assertEquals(0, fixture.resolvedTargets);
    }

    @Test public void damagedModernArtifactNeverFallsBackToLiveReferences() {
        Fixture fixture = new Fixture(); fixture.invalidArtifact = true;
        var catalog = fixture.service.getCatalog(101L, "REVISION", "201");
        assertEquals("UNAVAILABLE", catalog.getStatus());
        assertFalse(catalog.isLocalEditingAllowed());
        assertEquals("ARTIFACT_SOURCE", catalog.getReferenceNamespace());
        assertEquals(1, fixture.resolvedTargets);
    }

    @Test public void frozenCatalogKeepsExactIdsAndNamesButExcludesRuntimeSecretsAndCode() {
        Fixture fixture = new Fixture();
        var catalog = fixture.service.getCatalog(101L, "REVISION", "201");
        assertEquals("AVAILABLE", catalog.getStatus());
        assertEquals("ARTIFACT_SOURCE", catalog.getReferenceNamespace());
        assertEquals("9007199254740993", catalog.getVariables().get(0).get("id"));
        assertEquals("Frozen_Age", catalog.getVariables().get(0).get("scriptName"));
        assertEquals("发布时年龄", catalog.getVariables().get(0).get("varLabel"));
        assertTrue(catalog.isLocalEditingAllowed());
        String json = com.alibaba.fastjson.JSON.toJSONString(catalog);
        assertFalse(json.contains("SECRET")); assertFalse(json.contains("implScript")); assertFalse(json.contains("modelContent"));
    }

    @Test public void localDraftClassificationDoesNotCompileOrNeedOtherDirectoryPermissions() {
        Fixture fixture = new Fixture(); fixture.revision.setState("DRAFT");
        var catalog = fixture.service.getCatalog(101L, "REVISION", "201");
        assertEquals("AVAILABLE", catalog.getStatus()); assertEquals("LOCAL", catalog.getReferenceNamespace());
        assertTrue(catalog.isLocalEditingAllowed()); assertEquals(0, fixture.resolvedTargets);
    }

    @Test public void unsafeDraftCannotBorrowAncestorCatalogToRelabelItsCurrentModel() {
        Fixture fixture = new Fixture(); fixture.revision.setState("DRAFT"); fixture.foreignOrigin = true;
        var catalog = fixture.service.getCatalog(101L, "REVISION", "201");
        assertEquals("UNAVAILABLE", catalog.getStatus()); assertTrue(catalog.isImported());
        assertFalse(catalog.isLocalEditingAllowed()); assertTrue(catalog.getVariables().isEmpty());
        assertFalse(catalog.getDiagnostics().isEmpty()); assertEquals(0, fixture.resolvedTargets);
    }

    @Test public void unsafePublishedDescendantDisplaysItsOwnArtifactWhileEditingRemainsBlocked() {
        Fixture fixture = new Fixture(); fixture.foreignOrigin = true;
        var catalog = fixture.service.getCatalog(101L, "REVISION", "201");
        assertEquals("AVAILABLE", catalog.getStatus()); assertTrue(catalog.isImported());
        assertFalse(catalog.isLocalEditingAllowed()); assertEquals(1, fixture.resolvedTargets);
        assertEquals("Frozen_Age", catalog.getVariables().get(0).get("scriptName"));
        assertEquals("201", catalog.getExecutionIdentity().getSourceId());
    }

    @Test public void directoryEndpointOnlyRequiresRuleViewPermission() throws Exception {
        var method = RuleDefinitionController.class.getMethod("referenceCatalog", Long.class, String.class, String.class);
        assertEquals("rule:view", method.getAnnotation(RequirePermission.class).value());
    }

    private static final class Fixture {
        final RuleReferenceCatalogService service = new RuleReferenceCatalogService();
        final RuleDefinition definition = new RuleDefinition();
        final RuleRevision revision = new RuleRevision();
        final ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot = new ArtifactRuntimeSnapshotService.RuntimeSnapshot();
        boolean foreignOrigin;
        boolean invalidArtifact;
        RuleDefinitionVersion version;
        int resolvedTargets;
        Fixture() {
            definition.setId(101L); revision.setId(201L); revision.setDefinitionId(101L); revision.setState("PUBLISHED"); revision.setArtifactId(601L);
            var variable = new RuleVariable(); variable.setId(9007199254740993L); variable.setScriptName("Frozen_Age"); variable.setVarCode("Frozen_Age");
            variable.setVarLabel("发布时年龄"); variable.setVarSource("INPUT"); variable.setVarType("NUMBER"); variable.setSourceConfig("SECRET_API_KEY"); snapshot.getVariables().add(variable);
            var function = new RuleFunction(); function.setId(700L); function.setFuncCode("calcFrozen"); function.setFuncName("冻结函数"); function.setImplScript("SECRET_FUNCTION_BODY"); snapshot.getFunctions().add(function);
            var model = new RuleModel(); model.setId(701L); model.setModelCode("Frozen_Model"); model.setModelContent("SECRET_MODEL_CONTENT"); snapshot.getModels().add(model);
            wire();
        }
        void wire() {
            ReflectionTestUtils.setField(service, "definitionService", new RuleDefinitionService() {
                @Override public RuleDefinition getById(Serializable id) { return definition; }
                @Override public RuleDefinitionVersion getVersionById(Long id, Long versionId) { return version; }
            });
            ReflectionTestUtils.setField(service, "revisionMapper", Proxy.newProxyInstance(RuleRevisionMapper.class.getClassLoader(), new Class<?>[]{RuleRevisionMapper.class},
                    (proxy, method, args) -> "selectById".equals(method.getName()) ? revision : null));
            ReflectionTestUtils.setField(service, "namespaceGuard", new RuleReferenceNamespaceGuard() {
                @Override public NamespaceProof inspectNamespace(RuleDefinition current, String type, Long id) { return new NamespaceProof(foreignOrigin, foreignOrigin ? revision : null); }
            });
            ReflectionTestUtils.setField(service, "targetResolver", new RuleTestTargetResolver() {
                @Override public Target resolve(Long id, com.hengshucredit.rule.model.dto.RuleTestExecutionSource source, String json, String type, Long project) {
                    resolvedTargets++; assertEquals("201", source.getSourceId());
                    if (invalidArtifact) throw new IllegalArgumentException("制品摘要不一致");
                    var identity = new RuleTestExecutionIdentity(); identity.setDefinitionId("101"); identity.setSourceType("REVISION"); identity.setSourceId("201");
                    return new Target(definition, null, "{}", "SCORE", "5", new RuleFieldAnalyzer.ResolvedFields(List.of(), List.of()), new RulePublished(), snapshot, identity);
                }
            });
        }
    }
}
