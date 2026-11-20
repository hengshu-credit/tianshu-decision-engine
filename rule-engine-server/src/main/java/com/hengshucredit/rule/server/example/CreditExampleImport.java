package com.hengshucredit.rule.server.example;

import org.springframework.transaction.PlatformTransactionManager;
import org.mybatis.spring.SqlSessionTemplate;
import com.hengshucredit.rule.server.governance.GovernanceDependencyService;
import com.hengshucredit.rule.server.governance.GovernedResourceAdapterRegistry;
import com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper;
import com.hengshucredit.rule.server.mapper.GovernanceApprovalRequestMapper;
import com.hengshucredit.rule.server.governance.ResourceSnapshot;
import com.hengshucredit.rule.server.dashboard.DashboardDefaultsInitializer;
import java.util.ArrayList;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.DecisionArtifactService;
import com.hengshucredit.rule.server.artifact.RulePreflightValidationService;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.governance.GovernanceResourceBootstrapService;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelVersionMapper;
import com.hengshucredit.rule.server.service.ModelFileMigration;
import com.hengshucredit.rule.server.service.ModelFileStore;
import com.hengshucredit.rule.server.service.ModelVersionTestService;
import com.hengshucredit.rule.server.service.RuleModelService;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Explicit local CLI. The import uses the same compilers, credential store and package signing as publication. */
public final class CreditExampleImport {
    public static final String COMMAND = "--load-credit-example";
    private static final String MAIN_A_FILE = "main_a_probability.pmml";
    private static final int MAIN_A_SIZE = 232796;
    private static final String MAIN_A_SHA256 = "3b314bb47df0cd17342e12b6e0a1219988d4dace0e1ab1991fa34aeea700bddb";
    private final ApplicationContext context;
    private final JdbcTemplate db;
    private final CredentialCipher cipher;
    private final Map<String, Object> keys;
    private final List<Long> importedVersionIds = new ArrayList<>();

    public CreditExampleImport(ApplicationContext context, Path credentials) throws java.io.IOException {
        this.context = context;
        this.db = context.getBean(JdbcTemplate.class);
        this.cipher = context.getBean(CredentialCipher.class);
        this.keys = CanonicalJson.readMap(Files.readAllBytes(credentials));
    }

    public void load(Path sql) throws Exception {
        List<String> lines = Files.readAllLines(sql);
        // The named lock must outlive the transaction (including its commit).
        try (Connection lock = context.getBean(DataSource.class).getConnection();
             PreparedStatement acquire = lock.prepareStatement("SELECT GET_LOCK(CONCAT('tianshu:initial-data:', DATABASE()),120)");
             PreparedStatement release = lock.prepareStatement("SELECT RELEASE_LOCK(CONCAT('tianshu:initial-data:', DATABASE()))")) {
            try (var result = acquire.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) throw new IllegalStateException("样例导入锁超时");
            }
            try {
                new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
                        .executeWithoutResult(tx -> importRows(lines));
            } finally {
                release.execute();
            }
        }
    }

    private void importRows(List<String> lines) {
        var owners = db.queryForList("SELECT create_by FROM rule_project WHERE project_code IN ('HENGSHUCREDIT','HENGSHU_SUPPLIER') FOR UPDATE", String.class);
        if (!owners.isEmpty()) {
            if (owners.size() != 2 || !owners.stream().allMatch("TIANSHU_CREDIT_EXAMPLE"::equals))
                throw new IllegalStateException("同编码项目已存在，已保留现有项目。请选择独立演示库");
            String auth = db.queryForObject("SELECT secret_ciphertext FROM rule_project_auth a JOIN rule_project p ON p.id=a.project_id WHERE p.project_code='HENGSHUCREDIT' AND a.auth_code='CREDIT_HTTP'", String.class);
            if (!key("credit_api").equals(cipher.decrypt(auth))) throw new IllegalStateException("演示凭据卷与目标库不匹配");
            verifyPublication();
            upgradeMainA();
            CreditOverdueUpgrade.apply(context, db);
            installCases(lines);
            return;
        }
        credentials();
        importedVersionIds.clear();
        int lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            if ("-- @test-cases".equals(line)) break;
            if (line.isBlank()) continue;
            try {
                switch (line) {
                    case "-- @encrypt-json" -> encryptTemplate();
                    case "-- @approved-resource" -> approvedResource();
                    case "-- @store-main-a" -> storeMainA();
                    case "-- @freeze-model" -> freezeModel();
                    case "-- @dependencies" -> finishDependencies();
                    case "-- @build-artifact" -> buildArtifact();
                    default -> {
                        if (line.startsWith("-- @")) throw new IllegalArgumentException("未知导入命令");
                        if (!line.startsWith("--")) db.execute(line);
                    }
                }
            } catch (RuntimeException error) {
                // JDBC exceptions may contain bound confidential data; only expose safe SQL diagnostics.
                Throwable cause = error;
                while (cause != null && !(cause instanceof java.sql.SQLException)) cause = cause.getCause();
                if (cause instanceof java.sql.SQLException sql) throw new IllegalStateException("样例 SQL 第 " + lineNumber
                        + " 行失败，SQLState=" + sql.getSQLState() + "，错误码=" + sql.getErrorCode());
                throw new IllegalStateException("样例第 " + lineNumber + " 行：" + error.getMessage(), error);
            }
        }
        verifyPublication();
        installCases(lines);
        System.out.println("衡枢真信与模拟供应商已导入");
    }

    private void installCases(List<String> lines) {
        int marker = lines.indexOf("-- @test-cases");
        if (marker < 0) return; // Older release fixture.
        int added = 0;
        for (String line : lines.subList(marker + 1, lines.size())) {
            if (line.isBlank() || line.startsWith("--")) continue;
            if (line.startsWith("SET ")) db.execute(line);
            else if (line.startsWith("INSERT INTO rule_api_doc_scenario ") || line.startsWith("INSERT INTO rule_variable_test_case ") || line.startsWith("INSERT INTO rule_api_test_case ") || line.startsWith("INSERT INTO rule_model_test_case ")) added += db.update(line);
            else throw new IllegalStateException("不支持的用例导入语句");
        }
        System.out.println("样例测试用例新增 " + added + " 条");
    }

    private void verifyPublication() {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM rule_published r JOIN rule_definition d ON d.id=r.definition_id JOIN rule_project p ON p.id=d.project_id JOIN decision_artifact a ON a.id=r.artifact_id AND a.artifact_digest=r.artifact_digest WHERE p.project_code IN ('HENGSHUCREDIT','HENGSHU_SUPPLIER') AND r.status=1", Integer.class);
        if (count == null || count < 20) throw new IllegalStateException("样例依赖或发布制品不完整");
    }

    private String key(String name) {
        return Objects.requireNonNull((String) keys.get(name), "缺少演示凭据 " + name);
    }

    private void credentials() {
        String host = System.getenv().getOrDefault("EXAMPLE_SUPPLIER_HOST", "example-supplier");
        String dbHost = System.getenv().getOrDefault("EXAMPLE_DB_HOST", "mysql");
        int dbPort = Integer.parseInt(System.getenv().getOrDefault("EXAMPLE_DB_PORT", "3306"));
        int sftpPort = Integer.parseInt(System.getenv().getOrDefault("EXAMPLE_SFTP_PORT", "22084"));
        int httpPort = Integer.parseInt(System.getenv().getOrDefault("EXAMPLE_HTTP_PORT", "28084"));
        String jdbc = "jdbc:mysql://" + dbHost + ":" + dbPort + "/information_schema?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        db.update("SET @credit_example_loader=1, @db_host=?, @db_port=?, @db_jdbc_url=?, @db_username=?, @db_password=?, @sftp_host=?, @sftp_port=?, @sftp_password=?, @sftp_known_hosts=?, @supplier_url=?, @credit_lookup=?, @credit_secret=?, @basic_lookup=?, @basic_secret=?, @basic_identifier=?",
                dbHost, dbPort, jdbc, key("db_user"), cipher.encrypt(key("db_password")), host, sftpPort,
                cipher.encrypt(key("sftp")), cipher.encrypt("[" + host + "]:" + sftpPort + " " + key("host_public")),
                "http://" + host + ":" + httpPort, cipher.lookupKey("API_KEY", key("credit_api")), cipher.encrypt(key("credit_api")),
                cipher.lookupKey("BASIC", "demo_supplier_gateway"), cipher.encrypt(key("engine")), cipher.encrypt("demo_supplier_gateway"));
    }

    private void encryptTemplate() {
        String template = db.queryForObject("SELECT @credential_template", String.class);
        for (String name : List.of("court", "black", "late", "multi", "income", "fraud", "credit", "pboc"))
            template = template.replace("\"__GENERATE_" + name + "__\"", CanonicalJson.write(key(name)));
        db.update("SET @credential_json=?", CanonicalJson.write(Map.of("_credentialCiphertext", cipher.encrypt(template))));
    }

    private void approvedResource() {
        String type = db.queryForObject("SELECT @source_type", String.class);
        Long id = db.queryForObject("SELECT @source_id", Long.class);
        var resource = context.getBean(GovernanceResourceBootstrapService.class).ensure(type, id);
        if ("EXTERNAL_API".equals(type)) {
            db.update("UPDATE governed_resource g JOIN rule_external_api_config a ON a.id=g.resource_id JOIN rule_external_datasource d ON d.id=a.datasource_id SET g.project_id=d.project_id WHERE g.id=?", resource.getId());
        }
        // Only approved, effective source revisions are exported. Their normalized target snapshot
        // carries local IDs and freshly encrypted secrets; audit events are not copied.
        db.update("""
                INSERT INTO governance_approval_request(request_no, resource_type, resource_id, project_id, action, status,
                  draft_snapshot_json, submitted_snapshot_json, snapshot_digest, secret_payload_ciphertext, secret_digest,
                  applicant, reviewer, submit_time, review_time, change_summary)
                SELECT ?, v.resource_type,v.resource_id,g.project_id,@source_action,'APPROVED',v.snapshot_json,v.snapshot_json,
                  v.snapshot_digest,v.secret_payload_ciphertext,v.secret_digest,@source_applicant,@source_reviewer,
                  @source_submitted,@source_reviewed,'导入样例已批准的生效配置'
                FROM governed_resource_version v JOIN governed_resource g ON g.id=v.governed_resource_id WHERE v.id=?
                """, "EXAMPLE-" + UUID.randomUUID(), resource.getEffectiveVersionId());
        db.update("UPDATE governed_resource_version SET approval_request_id=LAST_INSERT_ID(),business_version=@source_business_version WHERE id=?", resource.getEffectiveVersionId());
        db.update("UPDATE governed_resource SET effective_version_no=@source_business_version WHERE id=?", resource.getId());
        importedVersionIds.add(resource.getEffectiveVersionId());
    }

    private void finishDependencies() {
        var deps = context.getBean(GovernanceDependencyService.class);
        var registry = context.getBean(GovernedResourceAdapterRegistry.class);
        var versions = context.getBean(GovernedResourceVersionMapper.class);
        var approvals = context.getBean(GovernanceApprovalRequestMapper.class);
        context.getBean(SqlSessionTemplate.class).clearCache();
        for (Long versionId : importedVersionIds) {
            var v = versions.selectById(versionId);
            var adapter = registry.require(v.getResourceType());
            var snapshot = new ResourceSnapshot(v.getSnapshotJson(),
                    v.getEffectiveStatus(), v.getSecretPayloadCiphertext(), v.getSecretDigest());
            var report = deps.preflight(v.getResourceType(), v.getResourceId(), adapter.collectDependencies(snapshot), List.of());
            if (!report.valid()) throw new IllegalStateException("样例依赖不完整 " + v.getResourceType() + " " + report.errors());
            var approval = approvals.selectById(v.getApprovalRequestId());
            deps.persist(approval, report, versionId);
            db.update("UPDATE governance_approval_request SET dependency_digest=?, validation_report_json=? WHERE id=?",
                    report.dependencyDigest(), CanonicalJson.write(report), approval.getId());
        }
        context.getBean(DashboardDefaultsInitializer.class).initialize();
    }

    private void storeMainA() {
        byte[] original = db.queryForObject("SELECT @main_a_original", byte[].class);
        if (original == null || original.length != MAIN_A_SIZE || !MAIN_A_SHA256.equals(Sha256Digests.bytes(original)))
            throw new IllegalArgumentException("主 A 卡 V9 原件长度或 SHA-256 不匹配");
        Long project = db.queryForObject("SELECT @project_1", Long.class);
        String file = context.getBean(ModelFileStore.class).accept(project, MAIN_A_FILE, original);
        db.update("SET @main_a_file=?, @main_a_original=NULL", file);
    }

    private record MainAVersion(long projectId, long modelId, long versionId) {}

    private void upgradeMainA() {
        var versions = db.query("""
                SELECT m.project_id,m.id,v.id FROM rule_model_version v JOIN rule_model m ON m.id=v.model_id
                JOIN rule_project p ON p.id=m.project_id
                WHERE p.project_code='HENGSHUCREDIT' AND m.model_code='MAIN_A' AND v.version=9
                AND v.model_file_name=? AND v.model_digest=? AND v.model_file_size=? AND v.model_format='PMML' FOR UPDATE
                """, (rs, n) -> new MainAVersion(rs.getLong(1),rs.getLong(2),rs.getLong(3)),
                MAIN_A_FILE, MAIN_A_SHA256, MAIN_A_SIZE);
        if (versions.size() != 1) throw new IllegalStateException("未找到与发行包匹配的主 A 卡 V9 原件，请核对模型版本与摘要");
        var target = versions.get(0);
        context.getBean(ModelFileMigration.class).migrateVersion(target.modelId(), target.versionId(), "TIANSHU_CREDIT_EXAMPLE");
        context.getBean(SqlSessionTemplate.class).clearCache();
        // Validate the version's own reference before the frozen package can materialize an embedded original.
        var version = context.getBean(RuleModelVersionMapper.class).selectById(target.versionId());
        ModelFileStore.bytes(context.getBean(ModelFileStore.class), target.projectId(), MAIN_A_FILE, "PMML",
                version.getModelContent(), MAIN_A_SHA256);
        context.getBean(ModelVersionTestService.class).executableModel(version);
    }

    private void freezeModel() {
        long id = db.queryForObject("SELECT @sample_model", Long.class);
        var models = context.getBean(RuleModelService.class);
        var model = context.getBean(RuleModelMapper.class).selectById(id);
        var files = context.getBean(ModelFileStore.class);
        String file = files.relocate(model.getProjectId(), model.getModelFileName(), model.getModelContent(), model.getModelDigest());
        if (!file.equals(model.getModelContent())) { // Older example SQL supplied an inline original.
            db.update("UPDATE rule_model SET model_content=? WHERE id=?", file, id);
            db.update("UPDATE rule_model_version SET model_content=? WHERE id=@sample_model_version AND model_id=?", file, id);
            model.setModelContent(file);
        }
        var fields = models.getDetail(id);
        model.setInputFields(fields.getInputFields());
        model.setOutputFields(fields.getOutputFields());
        String snapshot = context.getBean(ModelVersionTestService.class).capture(model);
        Object unavailable = CanonicalJson.readMap(snapshot).get("unavailableReasons");
        if (!(unavailable instanceof List<?> issues) || !issues.isEmpty()) throw new IllegalStateException("主 A 卡依赖校验失败：" + unavailable);
        db.update("UPDATE rule_model_version SET execution_snapshot_json=?, execution_snapshot_digest=? WHERE id=@sample_model_version AND model_id=?", snapshot, Sha256Digests.text(snapshot), id);
        context.getBean(GovernanceResourceBootstrapService.class).ensure("MODEL", id);
    }

    private void buildArtifact() {
        long revision = db.queryForObject("SELECT @revision", Long.class);
        var analysis = context.getBean(RulePreflightValidationService.class).analyzeForPublication(revision);
        var report = analysis.report();
        if (!report.isValid()) throw new IllegalStateException("发布预检失败：" + report.getErrors());
        db.update("UPDATE rule_revision SET content_digest=?,compiled_script=?,compiled_type=?,input_schema_json=?,output_schema_json=? WHERE id=?",
                report.getContentDigest(),report.getCompiledScript(),report.getCompiledType(),report.getInputSchemaJson(),report.getOutputSchemaJson(),revision);
        // JDBC and MyBatis share this transaction; invalidate the session's old revision projection.
        context.getBean(SqlSessionTemplate.class).clearCache();
        var artifact = context.getBean(DecisionArtifactService.class).buildApprovedArtifact(revision, "TIANSHU_CREDIT_EXAMPLE", analysis);
        db.update("UPDATE rule_revision SET artifact_id=?, state='PUBLISHED',validation_report_digest=? WHERE id=?", artifact.getId(),Sha256Digests.text(artifact.getValidationReportJson()),revision);
        db.update("UPDATE rule_definition_version SET artifact_id=?,artifact_digest=?,compiled_script=?,compiled_type=? WHERE revision_id=?",artifact.getId(),artifact.getArtifactDigest(),report.getCompiledScript(),report.getCompiledType(),revision);
        db.update("UPDATE rule_published SET artifact_id=?,artifact_digest=?,compiled_script=?,compiled_type=? WHERE revision_id=?", artifact.getId(),artifact.getArtifactDigest(),report.getCompiledScript(),report.getCompiledType(),revision);
        db.update("UPDATE rule_definition_content SET compiled_script=?,compiled_type=? WHERE definition_id=?", report.getCompiledScript(),report.getCompiledType(),artifact.getDefinitionId());
        context.getBean(SqlSessionTemplate.class).clearCache();
        context.getBean(GovernanceResourceBootstrapService.class).ensure("RULE", artifact.getDefinitionId());
    }
}
