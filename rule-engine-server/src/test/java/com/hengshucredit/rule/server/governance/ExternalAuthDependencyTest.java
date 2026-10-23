package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.service.RuleVariableService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.Assert.*;

public class ExternalAuthDependencyTest {
    private GovernanceSecretCodec codec() {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setActiveKeyId("test");
        properties.setMasterKeys(Map.of("test", "test-governance-master-key-32-characters-long"));
        return new GovernanceSecretCodec(new CredentialCipher(properties));
    }

    private ResourceSnapshot draft(String property) {
        return new ResourceSnapshot(CanonicalJson.write(Map.of("datasourceCode", "source", "datasourceName", "来源", "protocol", "HTTP", "scope", "GLOBAL",
                property, CanonicalJson.write(Map.of("token", Map.of("kind", "REFERENCE", "refType", "VARIABLE", "refId", "9007199254740993", "code", "oldName"),
                        "responseSample", Map.of("refId", 88, "refType", "VARIABLE"))))), "ACTIVE", null, null);
    }

    @Test public void encryptedApiAuthStillContributesItsStableDependencyWithoutSampleNoise() {
        var adapter = new ExternalApiGovernedResourceAdapter(null, codec(), null, null);
        ResourceSnapshot normalized = adapter.normalizeDraft(draft("authApiConfig"));
        assertFalse(normalized.snapshotJson().contains("oldName"));
        var references = adapter.collectDependencies(normalized);
        assertEquals(1, references.size());
        assertEquals(Long.valueOf("9007199254740993"), references.get(0).targetResourceId());
        assertEquals("VARIABLE", references.get(0).targetResourceType());
    }

    @Test public void datasourceValidatesVisibleIdsAndKeepsEncryptedDependencies() {
        var adapter = new ExternalDatasourceGovernedResourceAdapter(null, null, codec());
        ReflectionTestUtils.setField(adapter, "variableService", new RuleVariableService() {
            @Override public Map<String, String> buildRefScriptNameMap(Long projectId) {
                assertEquals(Long.valueOf(0), projectId);
                return Map.of("VARIABLE:9007199254740993", "renamed");
            }
        });
        ResourceSnapshot normalized = adapter.normalizeDraft(draft("authConfig"));
        assertTrue(adapter.validate(normalized).isEmpty());
        assertEquals(Long.valueOf("9007199254740993"), adapter.collectDependencies(normalized).get(0).targetResourceId());
        ReflectionTestUtils.setField(adapter, "variableService", new RuleVariableService() {
            @Override public Map<String, String> buildRefScriptNameMap(Long projectId) { return Map.of(); }
        });
        assertFalse(adapter.validate(normalized).isEmpty());
    }
}
