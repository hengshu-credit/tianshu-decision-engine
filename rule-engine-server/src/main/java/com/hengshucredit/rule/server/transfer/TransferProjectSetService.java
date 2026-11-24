package com.hengshucredit.rule.server.transfer;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengshucredit.rule.model.dto.GovernanceReviewRequest;
import com.hengshucredit.rule.model.dto.GovernanceSubmitRequest;
import com.hengshucredit.rule.model.dto.RuleListRecordChangeRequest;
import com.hengshucredit.rule.model.entity.OfflineTransferLog;
import com.hengshucredit.rule.model.entity.RuleListRecord;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.governance.GovernanceApprovalService;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import com.hengshucredit.rule.server.security.ConsoleResourceAccess;
import com.hengshucredit.rule.server.service.OfflineTransferLogService;
import com.hengshucredit.rule.server.service.RuleListChangeBatchService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 子包使用彼此独立的源 ID 空间；整个合集在同一个数据库事务中生效。 */
@Service
@RequiredArgsConstructor
public class TransferProjectSetService {
    private static final int MAX_BYTES = 512 * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final OfflineResourceTransferService transfer;
    private final OfflineResourceImportService importer;
    private final OfflineTransferLogService logs;
    private final TransferInstallationBindings installation;

    @Resource private RuleListChangeBatchService changes;
    @Resource private GovernanceApprovalService approvals;
    @Resource private ConsoleResourceAccess access;

    public record ExampleRecord(String libraryKey, String itemType, String itemContent, String reason) { }
    @Resource private RuleRevisionMapper revisions;

    @Resource private ConsoleProjectAccess projectAccess;

    public record Member(String code, String sha256, List<ExampleRecord> records, boolean restorePublished) {
        public Member { records = records == null ? List.of() : List.copyOf(records); }
    }
    public record Manifest(String format, List<Member> projects) { }
    public record Project(String code, int resources, int settings, int exampleRecords, boolean installationBound, List<String> issues) { }
    public record Step(String type, String code, String targetId, String actionId, String digest, Integer lockVersion) { }
    public record Result(String digest, List<Project> projects, boolean ready, boolean applied, int publicationCount, List<Step> steps) { }

    @Transactional(readOnly = true)
    public Result preview(MultipartFile file) { return process(file, null, null); }

    @Transactional
    public Result apply(MultipartFile file, String digest, String actor) {
        if (digest == null || !digest.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("请先预检项目合集");
        return process(file, digest, actor);
    }

    private Result process(MultipartFile file, String expected, String actor) {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) throw new IllegalArgumentException("项目合集为空或超过 512 MiB");
        try (var zip = new ZipInputStream(file.getInputStream())) {
            byte[] header = read(zip, "project-set.json", 64 * 1024);
            String digest = Sha256Digests.bytes(header);
            if (expected != null && !digest.equals(expected)) throw new IllegalArgumentException("项目合集已变化，请重新预检");
            Manifest manifest = JSON.readValue(header, Manifest.class);
            if (!"TIANSHU_PROJECT_SET_V1".equals(manifest.format()) || manifest.projects() == null
                    || manifest.projects().isEmpty() || manifest.projects().size() > 16)
                throw new IllegalArgumentException("项目合集清单无效");
            int remaining = MAX_BYTES - header.length;
            var codes = new HashSet<String>();
            List<Project> projects = new ArrayList<>();
            int publicationCount = 0, recordedMembers = 0;
            var recordedSteps = new ArrayList<Step>();
            List<Step> steps = new ArrayList<>();
            for (Member member : manifest.projects()) {
                if (member == null || member.code() == null || member.code().isBlank() || !codes.add(member.code())
                        || member.sha256() == null || !member.sha256().matches("[a-f0-9]{64}"))
                    throw new IllegalArgumentException("项目编码或子包摘要无效");
                ZipEntry entry = checkedEntry(zip, "projects/" + (projects.size() + 1) + ".zip", remaining);
                remaining -= Math.toIntExact(entry.getSize());
                var spool = Files.createTempFile("tianshu-project-set-", ".zip");
                try {
                    var hasher = sha256();
                    if (Files.copy(new DigestInputStream(zip, hasher), spool,
                            StandardCopyOption.REPLACE_EXISTING) != entry.getSize())
                        throw new IllegalArgumentException("项目子包大小不匹配");
                    if (!member.sha256().equals(HexFormat.of().formatHex(hasher.digest())))
                        throw new IllegalArgumentException("子包摘要不符：" + member.code());
                    var decoded = new TransferBundleCodec().decodeDeferred(spool);
                    if (!member.sha256().equals(decoded.packageDigest())) throw new IllegalArgumentException("子包摘要不符：" + member.code());
                    var options = new TransferImportOptions(null, "PROJECT", "REUSE", "REUSE", "", true, member.code(), null, Map.of());
                    var preview = transfer.preview(decoded, options);
                    var profile = installation.load(member.sha256());
                    Project project = summarize(member, preview, profile);
                    projects.add(project);
                    Map<String,String> published = new LinkedHashMap<>();
                    if (member.restorePublished()) for (Object item : (List<?>) preview.get("resources")) {
                        var row = (Map<?,?>)item;
                        if (Boolean.TRUE.equals(row.get("sourcePublished")))
                            published.put((String)row.get("key"), (String)row.get("resourceCode"));
                    }
                    publicationCount += published.size();
                    if (expected == null && member.restorePublished()) {
                        var fromLog = previousSteps(digest, member.sha256());
                        if (fromLog != null) { recordedMembers++; recordedSteps.addAll(fromLog); }
                    }
                    if (expected != null) {
                        if (!project.issues().isEmpty()) throw new IllegalArgumentException(member.code() + "：" + String.join("，", project.issues()));
                        var applied = importer.apply(decoded, options, actor, profile, true);
                        addExamples(member, applied, actor);
                        var memberSteps = new ArrayList<Step>();
                        addSteps(applied, published, memberSteps);
                        steps.addAll(memberSteps);
                        applied.put("projectSetDigest", digest);
                        applied.put("publicationSteps", memberSteps);
                        logs.recordDecodedImport(decoded, decoded.packageDigest(), applied, actor, null);
                    }
                } finally { Files.deleteIfExists(spool); }
            }
            if (zip.getNextEntry() != null) throw new IllegalArgumentException("合集含清单外文件");
            if (expected == null && recordedMembers == projects.size() && publicationCount > 0)
                return new Result(digest, projects, false, true, publicationCount, recordedSteps);
            return new Result(digest, projects, projects.stream().allMatch(p -> p.issues().isEmpty()), expected != null, publicationCount, steps);
        } catch (IOException invalid) {
            throw new IllegalArgumentException("无法读取项目合集：" + invalid.getMessage(), invalid);
        }
    }

    private static Project summarize(Member member, Map<String,Object> preview, TransferInstallationBindings.Profile profile) {
        String code = member.code();
        if (member.records().size() > 100) throw new IllegalArgumentException("每个子包最多附带 100 条显式名单样例");
        var libraries = new HashSet<String>();
        var keys = new HashSet<String>();
        var rows = (List<?>) preview.get("resources");
        int projectCount = 0, settings = 0;
        for (Object item : rows) {
            var row = (Map<?,?>) item;
            keys.add(String.valueOf(row.get("key")));
            if ("LIST_LIBRARY".equals(row.get("resourceType")) && "PROJECT".equals(row.get("scope")))
                libraries.add(String.valueOf(row.get("key")));
            if ("PROJECT".equals(row.get("resourceType"))) {
                projectCount++;
                if (!code.equals(row.get("resourceCode"))) throw new IllegalArgumentException("清单项目与子包不匹配：" + code);
            }
            if (row.get("pageSettingCount") instanceof Number number) settings += number.intValue();
        }
        if (projectCount != 1) throw new IllegalArgumentException("每个子包必须包含一个项目");
        List<String> issues = new ArrayList<>();
        for (Object value : (List<?>) preview.get("conflicts")) {
            if (value instanceof TransferConflict conflict) issues.add(conflict.message());
        }
        if (!((List<?>) preview.get("associations")).isEmpty()) issues.add("存在需要手工绑定的外部资源");
        if (((List<?>) preview.get("versionAssociations")).stream().map(item -> (Map<?,?>) item)
                .anyMatch(item -> !Boolean.TRUE.equals(item.get("valid")))) issues.add("存在需要手工绑定的版本");
        if (!((List<?>) preview.get("missingFunctionLibraries")).isEmpty()) issues.add("缺少目标函数库");
        for (var seed : member.records()) {
            if (!libraries.contains(seed.libraryKey()) || seed.itemContent() == null || seed.itemContent().isBlank()
                    || seed.itemType() == null || seed.itemType().isBlank())
                throw new IllegalArgumentException("名单样例须绑定子包内的项目级名单并提供类型和内容");
        }
        profile.validateKeys(keys);
        return new Project(code, rows.size(), settings, member.records().size(), profile.configured(), issues);
    }

    /** 只从当前账号的真实成功导入记录恢复，不按可变项目编码猜测目标。 */
    private List<Step> previousSteps(String setDigest, String memberDigest) {
        var scope = projectAccess.current();
        if (scope == null || scope.userId() == null || !scope.can("transfer:view")) return null;
        var query = new QueryWrapper<OfflineTransferLog>();
        query.select("content_json").eq("actor_user_id", scope.userId()).eq("operation_type","IMPORT")
                .eq("status","SUCCESS").eq("package_digest",memberDigest)
                .apply("JSON_UNQUOTE(JSON_EXTRACT(content_json,'$.result.projectSetDigest'))={0}", setDigest)
                .orderByDesc("id").last("LIMIT 1");
        var log = logs.getBaseMapper().selectOne(query);
        if (log == null) return null;
        var content = CanonicalJson.readMap(log.getContentJson());
        if (!(content.get("result") instanceof Map<?,?> result) || !(result.get("publicationSteps") instanceof List<?> raw)
                || result.get("targetProjectId") == null) return null;
        long projectId = Long.parseLong(result.get("targetProjectId").toString());
        access.requirePermission("PROJECT", projectId, "transfer:import");
        return raw.stream().map(value -> JSON.convertValue(value, Step.class)).toList();
    }

    private void addSteps(Map<String,Object> applied, Map<String,String> requested, List<Step> steps) {
        for (Object value : (List<?>)applied.get("resources")) {
            var row = (Map<?,?>)value;
            String source = (String)row.get("sourceKey");
            if (!requested.containsKey(source)) continue;
            var key = TransferKey.parse(source);
            long target = ((Number)row.get("targetId")).longValue();
            if (key.type() == TransferResourceType.EXPERIMENT && "DRAFT".equals(row.get("status"))) {
                steps.add(new Step("EXPERIMENT",requested.get(source),Long.toString(target),
                        row.get("approvalRequestId").toString(),(String)row.get("snapshotDigest"),null));
            } else if (key.type() == TransferResourceType.RULE && "CREATED".equals(row.get("status"))) {
                var draft = revisions.selectOne(new QueryWrapper<RuleRevision>()
                        .eq("definition_id",target).eq("state","DRAFT"));
                if (draft == null) throw new IllegalStateException("缺少本次导入的规则草稿");
                steps.add(new Step("RULE", requested.get(source),Long.toString(target),draft.getId().toString(),
                        draft.getContentDigest(), draft.getLockVersion()));
            }
        }
    }

    private void addExamples(Member member, Map<String,Object> result, String actor) {
        if (member.records().isEmpty()) return;
        var created = new HashMap<String,Long>();
        for (Object raw : (List<?>)result.get("resources")) {
            var row = (Map<?,?>)raw;
            if ("CREATED".equals(row.get("status")) && row.get("targetId") instanceof Number id)
                created.put((String) row.get("sourceKey"), id.longValue());
        }
        for (var item : member.records()) {
            Long target = created.get(item.libraryKey());
            if (target == null) throw new IllegalArgumentException("名单样例只能加入本次新建的项目名单");
            access.require("LIST_LIBRARY", target, "edit");
            for (String permission : List.of("list:submit","approval:submit","approval:approve"))
                access.requirePermission("LIST_LIBRARY", target, permission);
            var record = new RuleListRecord();
            record.setItemType(item.itemType()); record.setItemContent(item.itemContent());
            record.setReason(item.reason()); record.setStatus(1);
            var request = new RuleListRecordChangeRequest();
            request.setOperation("ADD"); request.setRecord(record);
            var batch = changes.stageSingle(target, request, actor);
            if (!batch.isSubmittable()) throw new IllegalArgumentException("名单样例校验失败：" + item.libraryKey());
            var submit = new GovernanceSubmitRequest();
            submit.setComment("项目合集预置名单样例");
            approvals.submit(batch.getApprovalRequestId(), submit, actor);
            var review = new GovernanceReviewRequest();
            review.setComment(submit.getComment());
            if (!"APPROVED".equals(approvals.approve(batch.getApprovalRequestId(), review, actor).getStatus()))
                throw new IllegalStateException("名单样例审批未生效");
        }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static ZipEntry checkedEntry(ZipInputStream zip, String path, int limit) throws IOException {
        ZipEntry entry = zip.getNextEntry();
        if (entry == null || !Objects.equals(entry.getName(), path) || entry.getMethod() != ZipEntry.STORED
                || entry.getSize() <= 0 || entry.getSize() > limit)
            throw new IllegalArgumentException("项目合集文件缺失或长度无效：" + path);
        return entry;
    }

    private static byte[] read(ZipInputStream zip, String path, int limit) throws IOException {
        ZipEntry entry = checkedEntry(zip, path, limit);
        byte[] bytes = new byte[(int) entry.getSize()];
        if (zip.readNBytes(bytes, 0, bytes.length) != bytes.length || zip.read() != -1) throw new IllegalArgumentException("子包长度不符：" + path);
        return bytes;
    }
}
