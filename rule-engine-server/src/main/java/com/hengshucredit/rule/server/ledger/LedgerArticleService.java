package com.hengshucredit.rule.server.ledger;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengshucredit.rule.model.dto.LedgerContent;
import com.hengshucredit.rule.model.dto.LedgerRequests;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import static com.hengshucredit.rule.server.ledger.LedgerAccess.number;

@Service
public class LedgerArticleService {
    static final String TABLE = "rule_ledger_article";
    private static final Pattern ASSET_REFERENCE = Pattern.compile("/api/rule/ledger/assets/([1-9][0-9]*)/content(?:\\b)");
    private final LedgerRepository repository;
    private final LedgerAccess access;
    private final LedgerAssetService assets;
    private final ObjectMapper json;
    private final LedgerTypeService types;
    public LedgerArticleService(LedgerRepository repository, LedgerAccess access, LedgerAssetService assets, ObjectMapper json, LedgerTypeService types) {
        this.repository = repository; this.access = access; this.assets = assets; this.json = json; this.types = types;
    }

    public Page<Map<String, Object>> list(String status, String scope, Long projectId, String keyword, int page, int size) {
        long user = access.userId();
        var args = new ArrayList<Object>();
        String where = "FROM rule_ledger_article item LEFT JOIN rule_ledger_revision latest ON latest.article_id=item.id AND latest.version_no=item.published_version AND "
                + repository.visible("rule_ledger_revision", "latest") + " WHERE " + repository.visible(TABLE, "item")
                + " AND (item.published_version=0 OR latest.id IS NOT NULL)";
        String ownDraft = "item.has_draft=1 AND " + repository.visibleDraft("item");
        if ("DRAFT".equals(status)) where += " AND " + ownDraft;
        if ("PUBLISHED".equals(status)) where += " AND item.published_version>0";
        if (scope != null && !scope.isBlank()) { where += " AND (CASE WHEN " + ownDraft + " THEN item.draft_scope ELSE item.scope END)=?"; args.add(scope); }
        if (projectId != null) { where += " AND (CASE WHEN " + ownDraft + " THEN item.draft_project_id ELSE item.project_id END)=?"; args.add(projectId); }
        if (keyword != null && !keyword.isBlank()) {
            where += " AND (latest.title LIKE ? OR latest.summary LIKE ? OR (" + ownDraft + " AND item.draft_json LIKE ?))";
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        String columns = "item.*,latest.id AS published_id,latest.title AS published_title,latest.summary AS published_summary,latest.publish_time AS published_time";
        var result = repository.page(columns, where, "item.update_time DESC,item.id DESC", args, page, size);
        result.setRecords(result.getRecords().stream().map(row -> {
            var response = articleInfo(row);
            boolean visibleDraft = number(row.get("has_draft")) == 1 && access.canReadDraft(row);
            LedgerContent draft = visibleDraft ? decode((String) row.get("draft_json")) : null;
            response.put("title", draft == null ? row.get("published_title") : draft.getTitle());
            response.put("summary", draft == null ? row.get("published_summary") : draft.getSummary());
            response.put("publishedRevisionId", row.get("published_id"));
            response.put("publishTime", row.get("published_time"));
            if (draft != null) { response.put("scope", row.get("draft_scope")); response.put("projectId", row.get("draft_project_id")); response.put("projectName", repository.projectName(row.get("draft_project_id"))); }
            return response;
        }).toList());
        return result;
    }

    public Map<String, Object> edit(long id) {
        var row = repository.one(TABLE, id, false);
        access.requireEdit(TABLE, row); access.requireDraft(row);
        var response = articleInfo(row);
        LedgerContent content = number(row.get("has_draft")) == 1 ? decode((String) row.get("draft_json")) : content(latest(row));
        if (Boolean.TRUE.equals(content.getSummaryAuto())) content.setSummary("");
        response.put("content", content);
        return response;
    }

    @Transactional
    public Map<String, Object> save(Long id, LedgerRequests.SaveDraft request) {
        access.userId(); access.requireWrite();
        LedgerContent content = checked(request.content(), false);
        if (content.getScope() != null && !content.getScope().isBlank() && !"PRIVATE".equals(content.getScope())) access.requireTarget(content.getScope(), content.getProjectId());
        Map<String, Object> row;
        if (id == null) {
            id = repository.insert("INSERT INTO rule_ledger_article (scope,owner_user_id,owner_name,draft_user_id,draft_json,draft_scope,draft_project_id) VALUES ('PRIVATE',?,?,?,?,?,?)",
                    access.userId(), access.actor(), access.userId(), encode(content), draftScope(content), "PROJECT".equals(content.getScope()) ? content.getProjectId() : null);
            row = repository.one(TABLE, id, true);
        } else {
            row = repository.one(TABLE, id, true);
            access.requireEdit(TABLE, row); access.requireDraft(row); LedgerAccess.version(row, request.version());
            repository.update("UPDATE rule_ledger_article SET draft_user_id=?,draft_json=?,draft_scope=?,draft_project_id=?,has_draft=1,lock_version=lock_version+1,update_time=CURRENT_TIMESTAMP WHERE id=?",
                    access.userId(), encode(content), draftScope(content), "PROJECT".equals(content.getScope()) ? content.getProjectId() : null, id);
        }
        checkReferences(content, id, false);
        replaceDraftReferences(id, content);
        return edit(id);
    }

    @Transactional
    public Map<String, Object> publish(long id, Long version) {
        var row = repository.one(TABLE, id, true);
        access.requireEdit(TABLE, row); access.requireDraft(row); LedgerAccess.version(row, version);
        if (number(row.get("has_draft")) != 1) throw new IllegalArgumentException("请先保存文章草稿");
        LedgerContent content = checked(decode((String) row.get("draft_json")), true);
        access.requireTarget(content.getScope(), content.getProjectId());
        checkReferences(content, id, true);
        int next = (int) number(row.get("published_version")) + 1;
        LocalDateTime now = LocalDateTime.now();
        String digest = LedgerAssetService.digest(encode(content).getBytes(StandardCharsets.UTF_8));
        if (next > 1 && digest.equals(latest(row).get("content_digest"))) throw new IllegalArgumentException("文章内容未发生变化，无需新增发布版本");
        long revisionId = repository.insert("INSERT INTO rule_ledger_revision (article_id,version_no,title,summary,summary_auto,body,category,scope,project_id,occurred_time,activity_time,publish_time,actor_user_id,actor_name,change_summary,content_digest) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, next, content.getTitle(), content.getSummary(), Boolean.TRUE.equals(content.getSummaryAuto()) ? 1 : 0, content.getBody(), content.getCategory(), content.getScope(), content.getProjectId(), content.getOccurredAt(), next == 1 ? content.getOccurredAt() : now, now, access.userId(), access.actor(), content.getChangeSummary(), digest);
        for (long eventId : content.getEventIds()) repository.update("INSERT INTO rule_ledger_revision_reference (revision_id,ref_type,ref_id) VALUES (?,'EVENT',?)", revisionId, eventId);
        for (long assetId : assetIds(content.getBody())) repository.update("INSERT INTO rule_ledger_revision_reference (revision_id,ref_type,ref_id) VALUES (?,'ASSET',?)", revisionId, assetId);
        repository.update("INSERT INTO rule_ledger_revision_reference (revision_id,ref_type,ref_id) VALUES (?,'CATEGORY',?)", revisionId, Long.valueOf(content.getCategory()));
        repository.update("UPDATE rule_ledger_article SET scope=?,project_id=?,published_version=?,has_draft=0,draft_json=NULL,draft_user_id=NULL,lock_version=lock_version+1,update_time=CURRENT_TIMESTAMP WHERE id=?",
                content.getScope(), content.getProjectId(), next, id);
        repository.update("DELETE FROM rule_ledger_draft_reference WHERE article_id=?", id);
        return revision(revisionId);
    }

    @Transactional
    public void discard(long id, Long version) {
        var row = repository.one(TABLE, id, true);
        access.requireEdit(TABLE, row); access.requireDraft(row); LedgerAccess.version(row, version);
        if (number(row.get("has_draft")) != 1) throw new IllegalArgumentException("文章没有待删除草稿");
        repository.update("DELETE FROM rule_ledger_draft_reference WHERE article_id=?", id);
        repository.update("UPDATE rule_ledger_article SET has_draft=0,draft_json=NULL,draft_user_id=NULL,deleted=?,lock_version=lock_version+1,update_time=CURRENT_TIMESTAMP WHERE id=?",
                number(row.get("published_version")) == 0 ? 1 : 0, id);
    }

    @Transactional
    public Map<String, Object> restore(long id, long revisionId, Long version) {
        var source = repository.one("rule_ledger_revision", revisionId, false);
        if (number(source.get("article_id")) != id) throw new IllegalArgumentException("历史版本不属于当前文章");
        LedgerContent restored = content(source);
        restored.setChangeSummary("基于 v" + source.get("version_no") + " 恢复为草稿");
        return save(id, new LedgerRequests.SaveDraft(version, restored));
    }

    public Map<String, Object> revision(long revisionId) {
        var row = repository.one("rule_ledger_revision", revisionId, false);
        return revisionView(row, true);
    }

    public Map<String, Object> workspace(long id) {
        var article = repository.one(TABLE, id, false);
        List<Map<String, Object>> rows = repository.queryForList("SELECT item.* FROM rule_ledger_revision item WHERE item.article_id=? AND " + repository.visible("rule_ledger_revision", "item") + " ORDER BY item.version_no DESC", id);
        var result = new LinkedHashMap<String, Object>();
        var history = new ArrayList<Map<String, Object>>(); var heads = new ArrayList<Map<String, Object>>();
        Long latestId = null;
        for (var row : rows) {
            long revisionId = number(row.get("id")); boolean latest = number(row.get("version_no")) == number(article.get("published_version"));
            if (latest) latestId = revisionId;
            var snapshot = new LinkedHashMap<String, Object>();
            snapshot.put("id", revisionId); snapshot.put("versionNo", row.get("version_no")); snapshot.put("revisionNo", 1);
            snapshot.put("snapshotJson", encode(content(row))); snapshot.put("changeSummary", row.get("change_summary"));
            snapshot.put("createBy", row.get("actor_name")); snapshot.put("createTime", time(row.get("publish_time"))); history.add(snapshot);
            var head = new LinkedHashMap<String, Object>(snapshot); head.put("versionId", revisionId); head.put("version", row.get("version_no")); head.put("revision", 1); head.put("latest", latest); head.put("deleted", false); heads.add(head);
        }
        var drafts = new ArrayList<Map<String, Object>>();
        if (number(article.get("has_draft")) == 1 && access.canReadDraft(article)) {
            var draft = new LinkedHashMap<String, Object>(); draft.put("id", id); draft.put("status", "EDITING"); draft.put("draftSnapshotJson", article.get("draft_json")); draft.put("actorUserId", access.userId()); draft.put("applicant", access.actor()); drafts.add(draft);
        }
        result.put("latestVersionId", latestId); result.put("versions", heads); result.put("history", history); result.put("drafts", drafts);
        result.put("actor", access.actor()); result.put("actorUserId", access.userId()); result.put("projectId", article.get("project_id")); result.put("scope", article.get("scope"));
        result.put("canEdit", canEdit(article)); result.put("articleVersion", article.get("lock_version"));
        return result;
    }

    public List<Map<String, Object>> versionEvents(long id) {
        repository.one(TABLE, id, false);
        return repository.queryForList("SELECT item.id,item.version_no,item.publish_time,item.actor_name,item.change_summary FROM rule_ledger_revision item WHERE item.article_id=? AND " + repository.visible("rule_ledger_revision", "item") + " ORDER BY item.version_no DESC", id).stream().map(row -> {
            var result = new LinkedHashMap<String, Object>(); result.put("id", row.get("id")); result.put("createTime", time(row.get("publish_time"))); result.put("actor", row.get("actor_name")); result.put("action", "PUBLISH"); result.put("comment", "发布 v" + row.get("version_no") + " · " + row.get("change_summary")); return (Map<String, Object>) result;
        }).toList();
    }

    Map<String, Object> revisionView(Map<String, Object> row, boolean includeBody) {
        var result = new LinkedHashMap<String, Object>();
        String[] fields = {"id","article_id","version_no","title","summary","category","scope","project_id","occurred_time","activity_time","publish_time","actor_user_id","actor_name","change_summary"};
        String[] labels = {"id","articleId","versionNo","title","summary","category","scope","projectId","occurredAt","activityTime","publishTime","actorUserId","actorName","changeSummary"};
        for (int i = 0; i < fields.length; i++) result.put(labels[i], row.get(fields[i]));
        result.put("projectName", repository.projectName(row.get("project_id")));
        if (includeBody) result.put("body", row.get("body"));
        result.put("events", visibleEvents(number(row.get("id"))));
        return result;
    }
    private List<Map<String, Object>> visibleEvents(long revisionId) {
        return repository.queryForList("SELECT item.id,item.title,item.status FROM rule_ledger_event item JOIN rule_ledger_revision_reference ref ON ref.ref_id=item.id AND ref.ref_type='EVENT' WHERE ref.revision_id=? AND " + repository.visible("rule_ledger_event", "item") + " ORDER BY item.id", revisionId).stream().map(row -> Map.<String,Object>of("id",row.get("id"),"title",row.get("title"),"status",row.get("status"))).toList();
    }
    private Map<String, Object> latest(Map<String, Object> article) {
        var rows = repository.queryForList("SELECT item.* FROM rule_ledger_revision item WHERE item.article_id=? AND item.version_no=? AND " + repository.visible("rule_ledger_revision", "item"), article.get("id"), article.get("published_version"));
        if (rows.isEmpty()) throw new IllegalArgumentException("文章尚无可访问的发布版本");
        return rows.get(0);
    }
    private LedgerContent content(Map<String, Object> revision) {
        var value = new LedgerContent(); value.setTitle((String) revision.get("title")); value.setSummary((String) revision.get("summary")); value.setBody((String) revision.get("body")); value.setCategory((String) revision.get("category")); value.setScope((String) revision.get("scope"));
        value.setSummaryAuto(number(revision.get("summary_auto")) == 1);
        value.setProjectId(number(revision.get("project_id")) > 0 ? number(revision.get("project_id")) : null);
        value.setOccurredAt(time(revision.get("occurred_time"))); value.setChangeSummary((String) revision.get("change_summary"));
        value.setEventIds(visibleEvents(number(revision.get("id"))).stream().map(row -> number(row.get("id"))).toList()); return value;
    }
    private Map<String, Object> articleInfo(Map<String, Object> row) {
        var result = new LinkedHashMap<String, Object>(); result.put("id",row.get("id")); result.put("scope",row.get("scope")); result.put("projectId",row.get("project_id")); result.put("ownerUserId",row.get("owner_user_id")); result.put("ownerName",row.get("owner_name")); result.put("version",row.get("lock_version")); result.put("publishedVersion",row.get("published_version"));
        result.put("hasDraft",number(row.get("has_draft"))==1 && access.canReadDraft(row)); result.put("canEdit",canEdit(row)); result.put("updateTime",row.get("update_time")); result.put("projectName",repository.projectName(row.get("project_id"))); return result;
    }
    private boolean canEdit(Map<String, Object> row) { return access.canEdit(row) && (number(row.get("has_draft")) == 0 || number(row.get("draft_user_id")) == access.userId()); }
    private String draftScope(LedgerContent value) { return Set.of("GLOBAL", "PROJECT").contains(value.getScope() == null ? "" : value.getScope()) ? value.getScope() : "PRIVATE"; }
    private void checkReferences(LedgerContent content, long articleId, boolean publishing) {
        for (long id : content.getEventIds().stream().sorted().toList()) repository.one("rule_ledger_event", id, true);
        for (long id : assetIds(content.getBody()).stream().sorted().toList()) assets.requireReference(id, content.getScope(), content.getProjectId(), publishing, articleId);
    }
    private void replaceDraftReferences(long id, LedgerContent content) {
        repository.update("DELETE FROM rule_ledger_draft_reference WHERE article_id=?", id);
        for (long ref : content.getEventIds()) repository.update("INSERT INTO rule_ledger_draft_reference (article_id,ref_type,ref_id) VALUES (?,'EVENT',?)", id, ref);
        for (long ref : assetIds(content.getBody())) repository.update("INSERT INTO rule_ledger_draft_reference (article_id,ref_type,ref_id) VALUES (?,'ASSET',?)", id, ref);
        if (content.getCategory() != null && !content.getCategory().isBlank()) repository.update("INSERT INTO rule_ledger_draft_reference (article_id,ref_type,ref_id) VALUES (?,'CATEGORY',?)", id, Long.valueOf(content.getCategory()));
    }
    static Set<Long> assetIds(String body) {
        // Markdown 反斜线、HTML 实体及 URL 会忽略的 ASCII 空白不能绕过素材引用与检测校验。
        String normalized = body == null ? "" : body.replaceAll("\\\\([\\p{Punct}])", "$1");
        for (int pass = 0; pass < 3; pass++) normalized = org.springframework.web.util.HtmlUtils.htmlUnescape(normalized);
        normalized = normalized.replace("\t", "").replace("\r", "").replace("\n", "");
        var ids = new LinkedHashSet<Long>(); var matcher = ASSET_REFERENCE.matcher(normalized);
        while (matcher.find()) { try { ids.add(Long.parseLong(matcher.group(1))); } catch (NumberFormatException error) { throw new IllegalArgumentException("无效素材 ID"); } }
        if (ids.size() > 100) throw new IllegalArgumentException("一篇文章最多引用 100 个素材");
        return ids;
    }
    private LedgerContent checked(LedgerContent value, boolean publishing) {
        if (value == null) throw new IllegalArgumentException("文章内容不能为空");
        value.setTitle(text(value.getTitle(), 200, "标题")); value.setBody(text(value.getBody(), 1024 * 1024, "正文")); value.setSummary(text(value.getSummary(), 400, "摘要")); value.setChangeSummary(text(value.getChangeSummary(), 400, "变更说明"));
        if (value.getSummaryAuto() == null) value.setSummaryAuto(value.getSummary().isBlank());
        if (value.getBody().getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) throw new IllegalArgumentException("正文不能超过 1 MB");
        if (value.getEventIds() != null && (value.getEventIds().size() > 20 || value.getEventIds().stream().anyMatch(id -> id == null || id <= 0))) throw new IllegalArgumentException("请选择不超过 20 个有效事件");
        value.setEventIds(value.getEventIds() == null ? List.of() : value.getEventIds().stream().distinct().sorted().toList());
        if (publishing || value.getCategory() != null && !value.getCategory().isBlank()) value.setCategory(String.valueOf(types.resolve(value.getCategory(), true).get("id")));
        if (publishing) {
            if (value.getTitle().isBlank() || value.getBody().isBlank()) throw new IllegalArgumentException("发布前请填写标题和正文");
            if (value.getOccurredAt() == null) throw new IllegalArgumentException("请选择发生时间");
            if (Boolean.TRUE.equals(value.getSummaryAuto()) || value.getSummary().isBlank()) { value.setSummaryAuto(true); value.setSummary(summary(value.getBody())); }
            if ("GLOBAL".equals(value.getScope())) value.setProjectId(null);
        }
        return value;
    }
    static String text(String value, int max, String label) { if (value == null) return ""; if (value.length() > max) throw new IllegalArgumentException(label + "长度超过限制"); return value; }
    static String summary(String body) { String value = body.replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "").replaceAll("[\\[\\]#*`>|]", " ").replaceAll("\\s+", " ").strip(); return value.substring(0, Math.min(150, value.length())); }
    private String encode(LedgerContent content) { try { return json.writeValueAsString(content); } catch (JsonProcessingException error) { throw new IllegalArgumentException("文章格式无效", error); } }
    private LedgerContent decode(String value) { try { return json.readValue(value, LedgerContent.class); } catch (JsonProcessingException error) { throw new IllegalStateException("文章草稿读取失败", error); } }
    static LocalDateTime time(Object value) { return value instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value; }
}
