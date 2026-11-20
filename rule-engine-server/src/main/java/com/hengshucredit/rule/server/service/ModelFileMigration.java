package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.artifact.DecisionArtifactPackageCodec;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** 显式的服务器维护命令。每行先验签、扫描并持久化文件，再比较原值更新数据库；中断可重跑。 */
@Service
public class ModelFileMigration {
    public static final String COMMAND = "--migrate-model-files";
    private final JdbcTemplate db;
    private final ModelFileStore files;
    private final CredentialCipher cipher;

    public ModelFileMigration(JdbcTemplate db, ModelFileStore files, CredentialCipher cipher) {
        this.db = db;
        this.files = files;
        this.cipher = cipher;
    }

    public int migrate() {
        governedModelReferences(null);
        return models(false, 0, Long.MAX_VALUE) + models(true, 0, Long.MAX_VALUE)
                + snapshots(0, Long.MAX_VALUE) + artifacts(null, null);
    }

    /** The example importer may relocate only its own model and the selected frozen version. */
    public void migrateVersion(long modelId, long versionId, String artifactCreator) {
        Long owner = db.queryForObject("SELECT model_id FROM rule_model_version WHERE id=? FOR UPDATE", Long.class, versionId);
        if (!Long.valueOf(modelId).equals(owner)) throw new IllegalArgumentException("模型版本归属不匹配");
        governedModelReferences(modelId);
        if (Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM rule_model m JOIN rule_model_version v "
                + "ON v.model_id=m.id AND v.version=m.current_version AND v.model_digest=m.model_digest WHERE m.id=? AND v.id=?)",
                Boolean.class, modelId, versionId))) models(false, modelId - 1, modelId);
        models(true, versionId - 1, versionId);
        snapshots(versionId - 1, versionId);
        artifacts(modelId, artifactCreator);
    }

    private int models(boolean version, long last, long end) {
        String source = version ? "rule_model_version r JOIN rule_model m ON m.id=r.model_id" : "rule_model r";
        String owner = version ? "m.project_id" : "r.project_id";
        String table = version ? "rule_model_version" : "rule_model";
        int count = 0;
        while (true) {
            var rows = db.queryForList("SELECT r.id," + owner + " AS owner_id,r.model_file_name,r.model_format,r.model_digest,r.model_content FROM "
                    + source + " WHERE r.id>? AND r.id<=? AND r.model_content IS NOT NULL AND r.model_content<>'' "
                    + "ORDER BY r.id LIMIT 1", last, end);
            if (rows.isEmpty()) return count;
            var row = rows.get(0);
            last = ((Number)row.get("id")).longValue();
            Long project = row.get("owner_id") == null ? null : ((Number)row.get("owner_id")).longValue();
            String format = (String) row.get("model_format");
            if (format == null || format.isBlank()) throw new IllegalStateException(table + " 缺少模型格式 id=" + last);
            String name = (String) row.get("model_file_name");
            if (name == null || name.isBlank()) name = "model." + format.toLowerCase(Locale.ROOT);
            String old = (String) row.get("model_content");
            if (ModelFileStore.isReference(old)) {
                ensureModel(project, format, old, (String) row.get("model_digest"));
                continue;
            }
            String ref = files.relocate(project, name, old, (String) row.get("model_digest"));
            int changed = db.update("UPDATE " + source + " SET r.model_content=? WHERE r.id=? AND SHA2(r.model_content,256)=? "
                            + "AND r.model_file_name <=> ? AND r.model_format <=> ? AND " + owner + " <=> ?",
                    ref, last, Sha256Digests.text(old), row.get("model_file_name"), format, project);
            if (changed != 1) throw new IllegalStateException("模型在迁移中变更，请重新执行：" + table + " id=" + last);
            count++;
        }
    }

    private int snapshots(long last, long end) {
        int changed = 0;
        var codec = new DecisionArtifactPackageCodec();
        while (true) {
            var rows = db.queryForList("SELECT id,execution_snapshot_json,execution_snapshot_digest FROM rule_model_version "
                    + "WHERE id>? AND id<=? AND execution_snapshot_json IS NOT NULL AND JSON_CONTAINS_PATH(execution_snapshot_json,'one','$.packageContent','$.packageFile') ORDER BY id LIMIT 1", last, end);
            if (rows.isEmpty()) return changed;
            var row = rows.get(0);
            last = ((Number)row.get("id")).longValue();
            String old = (String)row.get("execution_snapshot_json");
            String digest = (String)row.get("execution_snapshot_digest");
            var saved = CanonicalJson.readModelSnapshot(old);
            if (saved.get("packageFile") instanceof String file) ensurePackage(file, (String)saved.get("packageDigest"));
            var snapshot = ModelFileStore.checkedSnapshot(files, old, digest);
            byte[] bytes = ModelFileStore.snapshotBytes(files, snapshot);
            var decoded = codec.decode(bytes);
            if (!decoded.getPackageDigest().equals(snapshot.get("packageDigest"))
                    || !decoded.getArtifactDigest().equals(snapshot.get("artifactDigest"))) throw new IllegalArgumentException("历史模型制品摘要不匹配 id=" + last);
            if (saved.containsKey("packageFile")) continue;
            files.screenEmbeddedModels(decoded.getArtifactPackage());
            snapshot.remove("packageContent");
            snapshot.put("packageFile", files.savePackage(bytes));
            snapshot.put("legacyInlineDigest", digest);
            String compact = com.hengshucredit.rule.server.artifact.CanonicalJson.write(snapshot);
            ModelFileStore.checkedSnapshot(files, compact, digest);
            if (db.update("UPDATE rule_model_version SET execution_snapshot_json=? WHERE id=? AND SHA2(execution_snapshot_json,256)=?",
                    compact, last, Sha256Digests.text(old)) != 1) throw new IllegalStateException("历史模型快照在迁移中变更 id=" + last);
            changed++;
        }
    }

    private int artifacts(Long modelId, String creator) {
        long last = 0;
        int count = 0;
        var codec = new DecisionArtifactPackageCodec();
        while (true) {
            String query = "SELECT a.id,a.package_content,a.package_digest,a.artifact_digest FROM decision_artifact a WHERE a.id>? "
                    + "AND (LEFT(a.package_content,2)=_binary'PK' OR LEFT(a.package_content,11)=_binary'ts-file-v1:') ";
            if (modelId != null) query += "AND a.create_by=? AND EXISTS (SELECT 1 FROM decision_artifact_component c "
                    + "WHERE c.artifact_id=a.id AND ((c.component_type='MODEL' OR c.package_path LIKE 'resources/versions/MODEL-%') AND c.source_id=? "
                    + "OR LEFT(c.package_path,32)='resources/versions/MODEL_OUTPUT-' AND EXISTS "
                    + "(SELECT 1 FROM rule_model_output_field f WHERE f.id=c.source_id AND f.model_id=?))) ";
            var rows = db.queryForList(query + "ORDER BY a.id LIMIT 1", modelId == null ? new Object[]{last} : new Object[]{last, creator, modelId, modelId});
            if (rows.isEmpty()) return count;
            var row = rows.get(0);
            last = ((Number)row.get("id")).longValue();
            byte[] original = (byte[]) row.get("package_content");
            boolean reference = original[0] == 't';
            if (reference) {
                ensurePackage(new String(original, StandardCharsets.UTF_8), (String)row.get("package_digest"));
                original = files.resolveArtifact(original, (String)row.get("package_digest"));
            }
            var decoded = codec.decode(original);
            if (!decoded.getPackageDigest().equals(row.get("package_digest")) || !decoded.getArtifactDigest().equals(row.get("artifact_digest")))
                throw new IllegalStateException("决策制品摘要不匹配 id=" + last);
            if (reference) continue;
            files.screenEmbeddedModels(decoded.getArtifactPackage());
            byte[] ref = files.artifactContent(decoded.getArtifactPackage(), original);
            if (ref == original) continue;
            if (db.update("UPDATE decision_artifact SET package_content=? WHERE id=? AND SHA2(package_content,256)=?",
                    ref, last, decoded.getPackageDigest()) != 1) throw new IllegalStateException("决策制品在迁移中变更 id=" + last);
            count++;
        }
    }

    private void ensurePackage(String ref, String digest) {
        if (!ref.startsWith("ts-file-v1:packages/") || digest == null || digest.isBlank())
            throw new IllegalArgumentException("模型制品引用或摘要无效");
        files.ensureShared(ref, digest);
    }

    private void ensureModel(Long project, String format, String ref, String digest) {
        String prefix = "ts-file-v1:models/" + (project == null ? 0L : project) + "/";
        if (!ref.startsWith(prefix)) throw new IllegalArgumentException("模型原件不属于当前项目");
        if (format != null && !ref.endsWith("." + format.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("模型原件格式与元数据不一致");
        files.ensureShared(ref, digest);
    }

    /** Unpublished replacements can be referenced only by the encrypted governance payload. Keep audit bytes intact. */
    private void governedModelReferences(Long modelId) {
        for (boolean draft : new boolean[]{true, false}) {
            String source = draft ? "governance_approval_request" : "governed_resource_version";
            String snapshot = draft ? "COALESCE(submitted_snapshot_json,draft_snapshot_json)" : "snapshot_json";
            String query = "SELECT id,secret_payload_ciphertext,secret_digest," + snapshot + " AS metadata FROM " + source
                    + " WHERE resource_type='MODEL' AND secret_payload_ciphertext IS NOT NULL AND id>? "
                    + (modelId == null ? "" : "AND resource_id=? ") + "ORDER BY id LIMIT 1";
            long last = 0;
            while (true) {
                var rows = db.queryForList(query, modelId == null ? new Object[]{last} : new Object[]{last, modelId});
                if (rows.isEmpty()) break;
                var row = rows.get(0);
                last = ((Number) row.get("id")).longValue();
                String plaintext = cipher.decrypt((String) row.get("secret_payload_ciphertext"));
                Object content = CanonicalJson.readModelSnapshot(plaintext).get("/modelContent");
                if (!(content instanceof String ref) || !ModelFileStore.isReference(ref)) continue;
                if (!Sha256Digests.text(plaintext).equals(row.get("secret_digest"))) throw new IllegalStateException("模型治理敏感摘要不匹配 id=" + last);
                var meta = CanonicalJson.readModelSnapshot((String)row.get("metadata"));
                Long project = meta.get("projectId") == null ? null : Long.valueOf(meta.get("projectId").toString());
                ensureModel(project, (String)meta.get("modelFormat"), ref, (String)meta.get("modelDigest"));
            }
        }
    }
}
