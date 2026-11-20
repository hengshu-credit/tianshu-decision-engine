package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.DecisionArtifactPackage;
import com.hengshucredit.rule.server.artifact.DecisionArtifactPackageCodec;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** Old references may be unique to a draft, historical version or decision artifact. */
public class ModelFileMigrationTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void verifiesEveryReferencedFileWithoutRewritingExistingReferenceRows() throws Exception {
        Path root = temp.getRoot().toPath();
        ModelFileStore files = ModelFileStoreTest.store(root, "CLEAN");
        var properties = new ProjectAuthProperties();
        properties.setActiveKeyId("test");
        properties.getMasterKeys().put("test","test-only-main-a-key");
        var cipher = new CredentialCipher(properties);
        String head = original(files, "head"), version = original(files, "version");
        String draft = original(files, "draft"), history = original(files, "history");
        String frozen = archive(files, "model-version");
        String decision = archive(files, "decision-artifact");
        String snapshot = CanonicalJson.write(Map.of("packageFile", frozen, "packageDigest", ModelFileStore.contentDigest(frozen),
                "artifactDigest", artifactDigest(files,frozen)));
        String decisionDigest = artifactDigest(files,decision);
        String metadata = CanonicalJson.write(Map.of("modelFormat","PMML","modelFileName","main.pmml","projectId",21));
        JdbcTemplate db = new JdbcTemplate() {
            @Override public List<Map<String,Object>> queryForList(String sql, Object... params) {
                if ((long)params[0] != 0L) return List.of();
                if (sql.contains("governance_approval_request") || sql.contains("governed_resource_version")) {
                    String ref = sql.contains("governance_approval_request") ? draft : history;
                    String payload = CanonicalJson.write(Map.of("/modelContent",ref));
                    return List.of(Map.of("id", 1L, "secret_payload_ciphertext", cipher.encrypt(payload),
                            "secret_digest", Sha256Digests.text(payload), "metadata", metadata));
                }
                if (sql.contains(" AS owner_id")) {
                    String ref = sql.contains("rule_model_version r") ? version : head;
                    return List.of(Map.of("id", 1L, "owner_id", 21L, "model_file_name", "main.pmml", "model_format", "PMML",
                            "model_digest", ModelFileStore.contentDigest(ref), "model_content",ref));
                }
                if (sql.contains("execution_snapshot_json")) {
                    return List.of(Map.of("id", 1L, "execution_snapshot_json", snapshot, "execution_snapshot_digest", Sha256Digests.text(snapshot)));
                }
                if (sql.contains("FROM decision_artifact a")) {
                    return List.of(Map.of("id", 1L, "package_content", decision.getBytes(StandardCharsets.UTF_8),
                            "package_digest", ModelFileStore.contentDigest(decision),"artifact_digest",decisionDigest));
                }
                throw new AssertionError(sql);
            }
        };
        var migration = new ModelFileMigration(db, files, cipher);
        assertEquals(0, migration.migrate());
        for (String ref : List.of(head, version, draft, history, frozen, decision)) {
            Path file = root.resolve(ref.substring("ts-file-v1:".length()));
            byte[] previous = Files.readAllBytes(file);
            Files.delete(file);
            try { assertThrows("missing backing file " + ref, IllegalStateException.class, migration::migrate); }
            finally { Files.write(file, previous); }
        }
        assertEquals(0, migration.migrate());
    }

    private static String original(ModelFileStore store, String tag) {
        return store.accept(21L,"main.pmml",("<PMML version='4.4'><!--" + tag + "--></PMML>").getBytes(StandardCharsets.UTF_8));
    }

    private static String artifactDigest(ModelFileStore store, String reference) {
        return new DecisionArtifactPackageCodec().decode(store.packageBytes(reference,ModelFileStore.contentDigest(reference))).getArtifactDigest();
    }

    private static String archive(ModelFileStore store, String tag) {
        var pack = new DecisionArtifactPackage();
        pack.putMetadata("testPurpose", tag);
        pack.addComponent("models/1/9.pmml","application/xml","<PMML version='4.4'/>".getBytes(StandardCharsets.UTF_8),
                Map.of("resourceType","MODEL","modelFormat","PMML"));
        return store.savePackage(new DecisionArtifactPackageCodec().encode(pack));
    }
}
