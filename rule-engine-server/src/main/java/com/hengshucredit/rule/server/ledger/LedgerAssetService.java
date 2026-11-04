package com.hengshucredit.rule.server.ledger;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.model.PlatformException;
import com.hengshucredit.rule.model.dto.LedgerRequests;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import static com.hengshucredit.rule.server.ledger.LedgerAccess.number;

@Service
public class LedgerAssetService {
    static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    private static final Semaphore PROCESSING = new Semaphore(2);
    private static final String TABLE = "rule_ledger_asset";
    private static final String COLUMNS = "item.id,item.name,item.content_type,item.size_bytes,item.source_size,item.width,item.height,item.scope,item.project_id,item.owner_user_id,item.owner_name,item.scan_status,item.scan_message,item.lock_version,item.create_time,item.update_time";
    private final LedgerRepository repository;
    private final LedgerAccess access;
    private final LedgerAntivirus antivirus;
    public LedgerAssetService(LedgerRepository repository, LedgerAccess access, LedgerAntivirus antivirus) { this.repository = repository; this.access = access; this.antivirus = antivirus; }

    public Page<Map<String, Object>> list(String scope, Long projectId, String status, String keyword, int page, int size) {
        access.userId();
        String where = "FROM " + TABLE + " item WHERE " + repository.visible(TABLE, "item");
        var args = new ArrayList<Object>();
        if (scope != null && !scope.isBlank()) { where += " AND item.scope = ?"; args.add(scope); }
        if (projectId != null) { where += " AND item.project_id = ?"; args.add(projectId); }
        if (status != null && !status.isBlank()) { where += " AND item.scan_status = ?"; args.add(status); }
        if (keyword != null && !keyword.isBlank()) { where += " AND item.name LIKE ?"; args.add("%" + keyword + "%"); }
        var result = repository.page(COLUMNS, where, "item.update_time DESC,item.id DESC", args, page, size);
        result.setRecords(result.getRecords().stream().map(this::metadata).toList());
        return result;
    }

    @Transactional
    public Map<String, Object> upload(MultipartFile file) throws IOException {
        access.userId(); access.requireWrite();
        if (file.isEmpty() || file.getSize() > MAX_UPLOAD_BYTES) throw new IllegalArgumentException("图片大小必须在 5 MB 以内");
        String name = filename(file.getOriginalFilename());
        byte[] bytes = file.getBytes();
        String type = detect(bytes, name);
        long id = repository.insert("INSERT INTO rule_ledger_asset (name,content_type,size_bytes,source_size,sha256,scope,owner_user_id,owner_name,scan_status,raw_content) VALUES (?,?,?,?,?,'PRIVATE',?,?,'PENDING',?)",
                name, type, bytes.length, bytes.length, digest(bytes), access.userId(), access.actor(), bytes);
        return process(repository.one(TABLE, id, true));
    }

    @Transactional
    public Map<String, Object> retry(long id, Long version) {
        var row = repository.one(TABLE, id, true);
        access.requireEdit(TABLE, row); LedgerAccess.version(row, version);
        if ("CLEAN".equals(row.get("scan_status"))) return metadata(row);
        return process(row);
    }

    private Map<String, Object> process(Map<String, Object> row) {
        if (!PROCESSING.tryAcquire()) return metadata(row);
        try {
            byte[] bytes = (byte[]) row.get("raw_content");
            if (bytes == null) throw new IllegalArgumentException("原始素材已不可用");
            var result = antivirus.scan(bytes);
            byte[] png = null; Integer width = null, height = null;
            if ("CLEAN".equals(result.status())) {
                try {
                    var image = normalizeImage(bytes);
                    png = image.bytes(); width = image.width(); height = image.height();
                } catch (IOException | RuntimeException error) {
                    result = new LedgerAntivirus.Result("QUARANTINED", "图片格式或尺寸校验失败，素材已隔离");
                }
            }
            repository.jdbc.update("UPDATE rule_ledger_asset SET scan_status=?,scan_message=?,content=?,raw_content=?,content_type=?,size_bytes=?,width=?,height=?,lock_version=lock_version+1,update_time=CURRENT_TIMESTAMP WHERE id=?",
                    result.status(), result.message(), png, png == null ? bytes : null, png == null ? row.get("content_type") : "image/png", png == null ? number(row.get("source_size")) : png.length,
                    width, height, row.get("id"));
            return metadata(repository.one(TABLE, number(row.get("id")), false));
        } finally { PROCESSING.release(); }
    }

    @Transactional
    public Map<String, Object> update(long id, LedgerRequests.Asset input) {
        var row = repository.one(TABLE, id, true);
        access.requireEdit(TABLE, row); LedgerAccess.version(row, input.version());
        String name = filename(input.name());
        String scope = input.scope(); Long projectId = input.projectId();
        boolean moving = !java.util.Objects.equals(scope, row.get("scope")) || ("PROJECT".equals(scope) && number(row.get("project_id")) != (projectId == null ? 0 : projectId));
        if (moving) {
            if (!"CLEAN".equals(row.get("scan_status"))) throw new IllegalArgumentException("检测通过后才可调整素材共享范围");
            if (repository.referenced("ASSET", id) && !canSharePrivate(row)) throw new IllegalArgumentException("已共享素材被文章或历史版本引用，不能改变访问范围");
            if (!"PRIVATE".equals(scope)) access.requireTarget(scope, projectId);
            else if (number(row.get("owner_user_id")) != access.userId()) throw new IllegalArgumentException("不能将他人的素材变更为私有");
        }
        if (!Set.of("PRIVATE", "GLOBAL", "PROJECT").contains(scope)) throw new IllegalArgumentException("无效素材范围");
        repository.jdbc.update("UPDATE rule_ledger_asset SET name=?,scope=?,project_id=?,lock_version=lock_version+1,update_time=CURRENT_TIMESTAMP WHERE id=?", name, scope, "PROJECT".equals(scope) ? projectId : null, id);
        return metadata(repository.one(TABLE, id, false));
    }

    @Transactional
    public void delete(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id <= 0)) throw new IllegalArgumentException("请选择 1 至 100 个有效素材");
        for (long id : ids.stream().distinct().sorted().toList()) {
            var row = repository.one(TABLE, id, true); access.requireEdit(TABLE, row);
            if (repository.referenced("ASSET", id)) throw new IllegalArgumentException("素材已被草稿或历史版本引用，不能删除");
        }
        for (long id : ids.stream().distinct().toList()) repository.jdbc.update("DELETE FROM rule_ledger_asset WHERE id=?", id);
    }

    public byte[] content(long id) {
        var row = repository.one(TABLE, id, false);
        if (!"CLEAN".equals(row.get("scan_status"))) throw new PlatformException(PlatformCode.RESOURCE_CONFLICT, "素材尚未通过安全检测，不能预览或下载");
        return (byte[]) row.get("content");
    }
    public Map<String, Object> get(long id) { return metadata(repository.one(TABLE, id, false)); }

    /** 调用方已锁住文章；锁素材后再建立引用，避免与改范围/删除并发。 */
    public void requireReference(long id, String targetScope, Long projectId, boolean publishing, long articleId) {
        var asset = repository.one(TABLE, id, true);
        if (!publishing) return;
        if (!"CLEAN".equals(asset.get("scan_status"))) throw new IllegalArgumentException("文章含待检或隔离素材，不能发布");
        if ("PRIVATE".equals(asset.get("scope"))) {
            if (number(asset.get("owner_user_id")) != access.userId()) throw new IllegalArgumentException("不能发布他人的私有素材");
            Long count = repository.jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM rule_ledger_revision_reference WHERE ref_type='ASSET' AND ref_id=?) + (SELECT COUNT(*) FROM rule_ledger_draft_reference WHERE ref_type='ASSET' AND ref_id=? AND article_id<>?)", Long.class, id, id, articleId);
            if (count != null && count > 0) throw new IllegalArgumentException("私有素材被其他草稿引用，请先在素材库明确共享范围");
            access.requireTarget(targetScope, projectId);
            repository.jdbc.update("UPDATE rule_ledger_asset SET scope=?,project_id=?,lock_version=lock_version+1,update_time=CURRENT_TIMESTAMP WHERE id=?", targetScope, "PROJECT".equals(targetScope) ? projectId : null, id);
        } else if (!"GLOBAL".equals(asset.get("scope")) && !("PROJECT".equals(targetScope) && number(asset.get("project_id")) == (projectId == null ? 0 : projectId))) {
            throw new IllegalArgumentException("项目素材不能在其他项目或全局文章中发布，请使用同范围或全局素材");
        }
    }

    Map<String, Object> metadata(Map<String, Object> row) {
        var result = new LinkedHashMap<String, Object>();
        String[] source = {"id","name","content_type","size_bytes","source_size","width","height","scope","project_id","owner_user_id","owner_name","scan_status","scan_message","lock_version","create_time","update_time"};
        String[] target = {"id","name","contentType","sizeBytes","sourceSize","width","height","scope","projectId","ownerUserId","ownerName","scanStatus","scanMessage","version","createTime","updateTime"};
        for (int i = 0; i < source.length; i++) result.put(target[i], row.get(source[i]));
        result.put("projectName", repository.projectName(row.get("project_id")));
        boolean canManage = access.canEdit(row);
        boolean referenced = repository.referenced("ASSET", number(row.get("id")));
        result.put("canManage", canManage);
        result.put("canChangeScope", canManage && "CLEAN".equals(row.get("scan_status")) && (!referenced || canSharePrivate(row)));
        result.put("contentUrl", "CLEAN".equals(row.get("scan_status")) ? "/api/rule/ledger/assets/" + row.get("id") + "/content" : null);
        result.put("referenced", referenced);
        return result;
    }
    private boolean canSharePrivate(Map<String, Object> row) {
        if (!"PRIVATE".equals(row.get("scope")) || number(row.get("owner_user_id")) != access.userId()) return false;
        Long published = repository.jdbc.queryForObject("SELECT COUNT(*) FROM rule_ledger_revision_reference WHERE ref_type='ASSET' AND ref_id=?", Long.class, row.get("id"));
        return published != null && published == 0;
    }
    static String filename(String name) {
        if (name == null || name.isBlank() || name.length() > 255 || name.chars().anyMatch(value -> value < 32 || value == 127 || value == '/' || value == '\\')) throw new IllegalArgumentException("素材名称不能为空，且不能包含路径或控制字符");
        return name;
    }
    static String detect(byte[] bytes, String filename) {
        String name = filename.toLowerCase(Locale.ROOT);
        boolean png = bytes.length >= 8 && bytes[0] == (byte) 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71 && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
        boolean jpeg = bytes.length >= 3 && bytes[0] == (byte) 255 && bytes[1] == (byte) 216 && bytes[2] == (byte) 255;
        boolean webp = bytes.length >= 12 && new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF") && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
        if (png && name.endsWith(".png")) return "image/png";
        if (jpeg && (name.endsWith(".jpg") || name.endsWith(".jpeg"))) return "image/jpeg";
        if (webp && name.endsWith(".webp")) return "image/webp";
        throw new IllegalArgumentException("只接受内容与扩展名一致的 PNG、JPEG、WebP 图片");
    }
    record NormalizedImage(byte[] bytes, int width, int height) { }
    static NormalizedImage normalizeImage(byte[] bytes) throws IOException {
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("无效图片");
            var reader = readers.next();
            try {
                if (!Set.of("png", "jpeg", "jpg", "webp").contains(reader.getFormatName().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("不支持此图片格式");
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 16000000) throw new IllegalArgumentException("图片尺寸超过限制");
                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(reader.read(0), "png", output)) throw new IllegalArgumentException("图片不能转换为安全格式");
                if (output.size() > 16 * 1024 * 1024) throw new IllegalArgumentException("图片解码后过大");
                return new NormalizedImage(output.toByteArray(), width, height);
            } finally { reader.dispose(); }
        }
    }
    static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
