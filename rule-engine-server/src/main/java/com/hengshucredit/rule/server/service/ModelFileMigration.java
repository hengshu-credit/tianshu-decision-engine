package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.artifact.DecisionArtifactPackageCodec;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;

/** 显式的服务器维护命令。每行先验签、扫描并持久化文件，再比较原值更新数据库；中断可重跑。 */
@Service
public class ModelFileMigration {
    public static final String COMMAND = "--migrate-model-files";
    private final JdbcTemplate db;
    private final ModelFileStore files;

    public ModelFileMigration(JdbcTemplate db, ModelFileStore files) { this.db = db; this.files = files; }

    public int migrate() {
        return models(false) + models(true) + snapshots() + artifacts();
    }

    private int models(boolean version) {
        String source = version ? "rule_model_version r JOIN rule_model m ON m.id=r.model_id" : "rule_model r";
        String owner = version ? "m.project_id" : "r.project_id";
        String table = version ? "rule_model_version" : "rule_model";
        int count = 0;
        long last = 0;
        while (true) {
            var rows = db.queryForList("SELECT r.id," + owner + " AS owner_id,r.model_file_name,r.model_format,r.model_digest,r.model_content FROM "
                    + source + " WHERE r.id>? AND r.model_content IS NOT NULL AND r.model_content<>'' "
                    + "AND r.model_content NOT LIKE 'ts-file-v1:%' ORDER BY r.id LIMIT 1", last);
            if (rows.isEmpty()) return count;
            var row = rows.get(0);
            last = ((Number)row.get("id")).longValue();
            Long project = row.get("owner_id") == null ? null : ((Number)row.get("owner_id")).longValue();
            String format = (String) row.get("model_format");
            if (format == null || format.isBlank()) throw new IllegalStateException(table + " 缺少模型格式 id=" + last);
            String name = (String) row.get("model_file_name");
            if (name == null || name.isBlank()) name = "model." + format.toLowerCase(Locale.ROOT);
            String old = (String) row.get("model_content");
            String ref = files.relocate(project, name, old, (String) row.get("model_digest"));
            int changed = db.update("UPDATE " + source + " SET r.model_content=? WHERE r.id=? AND SHA2(r.model_content,256)=? "
                            + "AND r.model_file_name <=> ? AND r.model_format <=> ? AND " + owner + " <=> ?",
                    ref, last, Sha256Digests.text(old), row.get("model_file_name"), format, project);
            if (changed != 1) throw new IllegalStateException("模型在迁移中变更，请重新执行：" + table + " id=" + last);
            count++;
        }
    }

    private int snapshots() {
        long last = 0;
        int changed = 0;
        var codec = new DecisionArtifactPackageCodec();
        while (true) {
            var rows = db.queryForList("SELECT id,execution_snapshot_json,execution_snapshot_digest FROM rule_model_version "
                    + "WHERE id>? AND execution_snapshot_json IS NOT NULL AND JSON_CONTAINS_PATH(execution_snapshot_json,'one','$.packageContent') ORDER BY id LIMIT 1", last);
            if (rows.isEmpty()) return changed;
            var row = rows.get(0);
            last = ((Number)row.get("id")).longValue();
            String old = (String)row.get("execution_snapshot_json");
            String digest = (String)row.get("execution_snapshot_digest");
            var snapshot = ModelFileStore.checkedSnapshot(files, old, digest);
            byte[] bytes = ModelFileStore.snapshotBytes(files, snapshot);
            var decoded = codec.decode(bytes);
            if (!decoded.getPackageDigest().equals(snapshot.get("packageDigest"))
                    || !decoded.getArtifactDigest().equals(snapshot.get("artifactDigest"))) throw new IllegalArgumentException("历史模型制品摘要不匹配 id=" + last);
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

    private int artifacts() {
        long last = 0;
        int count = 0;
        var codec = new DecisionArtifactPackageCodec();
        while (true) {
            var rows = db.queryForList("SELECT id,package_content,package_digest FROM decision_artifact WHERE id>? "
                    + "AND LEFT(package_content,2)=_binary'PK' ORDER BY id LIMIT 1", last);
            if (rows.isEmpty()) return count;
            var row = rows.get(0);
            last = ((Number)row.get("id")).longValue();
            byte[] original = (byte[]) row.get("package_content");
            var decoded = codec.decode(original);
            if (!decoded.getPackageDigest().equals(row.get("package_digest"))) throw new IllegalStateException("决策制品摘要不匹配 id=" + last);
            files.screenEmbeddedModels(decoded.getArtifactPackage());
            byte[] ref = files.artifactContent(decoded.getArtifactPackage(), original);
            if (ref == original) continue;
            if (db.update("UPDATE decision_artifact SET package_content=? WHERE id=? AND SHA2(package_content,256)=?",
                    ref, last, decoded.getPackageDigest()) != 1) throw new IllegalStateException("决策制品在迁移中变更 id=" + last);
            count++;
        }
    }
}
