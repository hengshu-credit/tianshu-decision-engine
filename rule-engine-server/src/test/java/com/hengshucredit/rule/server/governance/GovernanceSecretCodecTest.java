package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

public class GovernanceSecretCodecTest {

    @Test
    public void secretValuesNeverRemainInPublicSnapshotOrDiff() {
        GovernanceSecretCodec codec = codec();
        ResourceSnapshot normalized = codec.normalize(
                ResourceSnapshot.ofJson(
                        "{\"name\":\"db\",\"password\":\"top-secret\","
                                + "\"sshPrivateKey\":\"private-key\"}"),
                Set.of("password", "sshPrivateKey"));

        Assert.assertFalse(
                normalized.snapshotJson().contains("top-secret"));
        Assert.assertFalse(
                normalized.snapshotJson().contains("private-key"));
        Assert.assertNotNull(normalized.secretPayloadCiphertext());
        Assert.assertNotNull(normalized.secretDigest());

        Map<String, Object> restored = codec.restore(normalized);
        Assert.assertEquals("top-secret", restored.get("password"));
        Assert.assertEquals("private-key",
                restored.get("sshPrivateKey"));

        ResourceDiff diff = JsonResourceDiff.compare(
                ResourceSnapshot.ofJson("{\"name\":\"db\"}"),
                normalized);
        Assert.assertTrue(diff.fields().stream()
                .filter(ResourceDiff.FieldDiff::sensitive)
                .allMatch(field -> !"top-secret".equals(
                        field.rightValue())));
    }

    @Test
    public void existingCipherIsRetainedWhenDraftHasNoPlaintextSecret() {
        GovernanceSecretCodec codec = codec();
        ResourceSnapshot initial = codec.normalize(
                ResourceSnapshot.ofJson(
                        "{\"name\":\"db\",\"password\":\"top-secret\"}"),
                Set.of("password"));
        ResourceSnapshot next = codec.normalize(new ResourceSnapshot(
                        "{\"name\":\"db2\",\"_secretConfigured\":"
                                + "{\"$.password\":true}}",
                        "ACTIVE",
                        initial.secretPayloadCiphertext(),
                        initial.secretDigest()),
                Set.of("password"));

        Assert.assertEquals(initial.secretPayloadCiphertext(),
                next.secretPayloadCiphertext());
        Assert.assertEquals("top-secret",
                codec.restore(next).get("password"));
    }

    @Test
    public void blankMaskedSecretDoesNotOverwriteExistingCipher() {
        GovernanceSecretCodec codec = codec();
        ResourceSnapshot initial = codec.normalize(
                ResourceSnapshot.ofJson(
                        "{\"name\":\"db\",\"password\":\"top-secret\"}"),
                Set.of("password"));
        ResourceSnapshot next = codec.normalize(new ResourceSnapshot(
                        "{\"name\":\"db2\",\"password\":\"\"}",
                        "ACTIVE",
                        initial.secretPayloadCiphertext(),
                        initial.secretDigest()),
                Set.of("password"));

        Assert.assertEquals(initial.secretPayloadCiphertext(),
                next.secretPayloadCiphertext());
        Assert.assertEquals(initial.secretDigest(),
                next.secretDigest());
        Assert.assertEquals("top-secret",
                codec.restore(next).get("password"));
    }

    @Test
    public void replacingOneSecretPreservesOtherMaskedAndOmittedSecrets() {
        GovernanceSecretCodec codec = codec();
        Set<String> keys = Set.of("password", "sshPrivateKey", "sshPassphrase");
        ResourceSnapshot initial = codec.normalize(ResourceSnapshot.ofJson(
                "{\"password\":\"old-password\",\"sshPrivateKey\":\"private-key\","
                        + "\"sshPassphrase\":\"key-passphrase\"}"), keys);
        ResourceSnapshot next = codec.normalize(new ResourceSnapshot(
                "{\"password\":\"new-password\",\"sshPrivateKey\":\"\"}",
                "ACTIVE", initial.secretPayloadCiphertext(), initial.secretDigest()), keys);

        Assert.assertEquals(Map.of("password", "new-password", "sshPrivateKey", "private-key",
                "sshPassphrase", "key-passphrase"), codec.restore(next));
        Assert.assertNotEquals(initial.secretDigest(), next.secretDigest());
        Assert.assertFalse(next.snapshotJson().contains("private-key"));
        Assert.assertFalse(next.snapshotJson().contains("new-password"));
    }

    @Test
    public void blankNewCredentialIsNotReportedAsConfigured() {
        GovernanceSecretCodec codec = codec();
        Set<String> keys = Set.of("password", "sshPrivateKey");
        ResourceSnapshot initial = codec.normalize(ResourceSnapshot.ofJson(
                "{\"password\":\"old-password\"}"), keys);
        ResourceSnapshot next = codec.normalize(new ResourceSnapshot(
                "{\"password\":\"\",\"sshPrivateKey\":\"\"}",
                "ACTIVE", initial.secretPayloadCiphertext(), initial.secretDigest()), keys);

        Map<String, Object> snapshot = com.hengshucredit.rule.server.artifact.CanonicalJson.readMap(next.snapshotJson());
        Assert.assertEquals(Map.of("/password", true, "/sshPrivateKey", false),
                snapshot.get(GovernanceSecretCodec.SECRET_STATE_KEY));
        Assert.assertEquals("old-password", codec.restore(next).get("password"));
    }

    @Test
    public void removedNestedConfigurationDoesNotReintroduceItsCredential() {
        GovernanceSecretCodec codec = codec();
        ResourceSnapshot initial = codec.normalize(ResourceSnapshot.ofJson(
                "{\"auth\":{\"password\":\"old-password\"}}"), Set.of("password"));
        ResourceSnapshot next = codec.normalize(new ResourceSnapshot(
                "{\"name\":\"without-auth\"}", "ACTIVE",
                initial.secretPayloadCiphertext(), initial.secretDigest()), Set.of("password"));

        Assert.assertEquals(Map.of("name", "without-auth"), codec.restore(next));
        Assert.assertFalse(next.snapshotJson().contains("/auth/password"));
    }

    @Test
    public void submittedConfiguredFlagsCannotInventCredentials() {
        GovernanceSecretCodec codec = codec();
        ResourceSnapshot next = codec.normalize(ResourceSnapshot.ofJson(
                "{\"name\":\"db\",\"_secretConfigured\":{\"/password\":true}}"), Set.of("password"));
        Assert.assertFalse(next.snapshotJson().contains("/password"));
        Assert.assertEquals(Map.of("name", "db"), codec.restore(next));
    }

    @Test
    public void explicitClearSurvivesRenormalizingAndDoesNotClearOtherSecrets() {
        GovernanceSecretCodec codec = codec();
        Set<String> keys = Set.of("password", "sshPrivateKey");
        ResourceSnapshot initial = codec.normalize(ResourceSnapshot.ofJson(
                "{\"password\":\"old-password\",\"sshPrivateKey\":\"private-key\"}"), keys);
        ResourceSnapshot cleared = codec.normalize(new ResourceSnapshot(
                "{\"password\":\"\",\"clearCredentials\":[\"password\"]}", "ACTIVE",
                initial.secretPayloadCiphertext(), initial.secretDigest()), keys);
        Assert.assertEquals("", codec.restore(cleared).get("password"));
        Assert.assertEquals("private-key", codec.restore(cleared).get("sshPrivateKey"));
        Assert.assertFalse(cleared.snapshotJson().contains("clearCredentials"));
        ResourceSnapshot approved = codec.normalize(cleared, keys);
        Assert.assertEquals("", codec.restore(approved).get("password"));
        Assert.assertEquals(cleared.secretDigest(), approved.secretDigest());
        Assert.assertTrue(JsonResourceDiff.compare(initial, approved).fields().stream().anyMatch(
                field -> field.sensitive() && field.changed() && "未配置".equals(field.rightValue())));
        ResourceSnapshot empty = codec.normalize(new ResourceSnapshot(
                "{\"clearCredentials\":[\"sshPrivateKey\"]}", "ACTIVE",
                approved.secretPayloadCiphertext(), approved.secretDigest()), keys);
        Assert.assertEquals("", codec.restore(empty).get("sshPrivateKey"));
        Assert.assertTrue(JsonResourceDiff.compare(initial, empty).fields().stream().anyMatch(
                field -> "$.credentials".equals(field.key()) && "未配置".equals(field.rightValue())));
    }

    @Test
    public void clearRejectsUnknownFieldsAndConflictingReplacement() {
        GovernanceSecretCodec codec = codec();
        for (String invalid : java.util.List.of(
                "{\"clearCredentials\":[\"id\"]}",
                "{\"clearCredentials\":\"password\"}",
                "{\"password\":\"new-password\",\"clearCredentials\":[\"password\"]}")) {
            Assert.assertThrows(IllegalArgumentException.class,
                    () -> codec.normalize(ResourceSnapshot.ofJson(invalid), Set.of("password")));
        }
    }

    private GovernanceSecretCodec codec() {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setActiveKeyId("test");
        properties.setMasterKeys(Map.of(
                "test",
                "test-governance-master-key-32-characters-long"));
        return new GovernanceSecretCodec(
                new CredentialCipher(properties));
    }
}
