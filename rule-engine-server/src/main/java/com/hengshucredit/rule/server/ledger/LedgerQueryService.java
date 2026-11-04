package com.hengshucredit.rule.server.ledger;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.dto.LedgerRequests;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.hengshucredit.rule.server.ledger.LedgerAccess.number;

@Service
public class LedgerQueryService {
    private final LedgerRepository repository;
    private final LedgerAccess access;
    private final LedgerArticleService articles;
    public LedgerQueryService(LedgerRepository repository, LedgerAccess access, LedgerArticleService articles) { this.repository = repository; this.access = access; this.articles = articles; }
    public record Filter(String scope, Long projectId, List<Long> eventIds, String category, String keyword, LocalDateTime start, LocalDateTime end) { }
    private record Query(String where, List<Object> args) { }

    private Query query(Filter filter) {
        access.userId();
        String where = "FROM rule_ledger_revision item WHERE " + repository.visible("rule_ledger_revision", "item");
        var args = new ArrayList<Object>();
        if (filter.scope() != null && !filter.scope().isBlank()) { where += " AND item.scope=?"; args.add(filter.scope()); }
        if (filter.projectId() != null) { where += " AND item.project_id=?"; args.add(filter.projectId()); }
        if (filter.category() != null && !filter.category().isBlank()) { where += " AND item.category=?"; args.add(filter.category()); }
        if (filter.keyword() != null && !filter.keyword().isBlank()) { where += " AND (item.title LIKE ? OR item.summary LIKE ? OR item.body LIKE ?)"; for (int i = 0; i < 3; i++) args.add("%" + filter.keyword() + "%"); }
        if (filter.start() != null && filter.end() != null && filter.end().isBefore(filter.start())) throw new IllegalArgumentException("结束时间不能早于开始时间");
        if (filter.start() != null) { where += " AND item.activity_time>=?"; args.add(filter.start()); }
        if (filter.end() != null) { where += " AND item.activity_time<=?"; args.add(filter.end()); }
        List<Long> events = filter.eventIds() == null ? List.of() : filter.eventIds().stream().distinct().toList();
        if (events.size() > 20) throw new IllegalArgumentException("最多筛选 20 个事件");
        for (Long id : events) { if (id == null || id <= 0) throw new IllegalArgumentException("无效事件 ID"); repository.one("rule_ledger_event", id, false); }
        if (!events.isEmpty()) {
            where += " AND EXISTS (SELECT 1 FROM rule_ledger_revision_reference selected_ref WHERE selected_ref.revision_id=item.id AND selected_ref.ref_type='EVENT' AND selected_ref.ref_id IN (" + String.join(",", java.util.Collections.nCopies(events.size(), "?")) + "))";
            args.addAll(events);
        }
        return new Query(where, args);
    }
    public Page<Map<String, Object>> timeline(Filter filter, int page, int size) {
        Query query = query(filter);
        boolean eventView = filter.eventIds() != null && !filter.eventIds().isEmpty();
        var result = repository.page("item.id,item.article_id,item.version_no,item.title,item.summary,item.category,item.scope,item.project_id,item.occurred_time,item.activity_time,item.publish_time,item.actor_user_id,item.actor_name,item.change_summary", query.where(), eventView ? "item.activity_time ASC,item.id ASC" : "item.activity_time DESC,item.id DESC", query.args(), page, size);
        result.setRecords(result.getRecords().stream().map(row -> articles.revisionView(row, false)).toList());
        return result;
    }
    public List<Map<String, Object>> calendar(String month, Filter filters) {
        YearMonth period;
        try { period = YearMonth.parse(month); } catch (RuntimeException error) { throw new IllegalArgumentException("月份格式应为 YYYY-MM"); }
        Query query = query(new Filter(filters.scope(), filters.projectId(), filters.eventIds(), filters.category(), filters.keyword(), period.atDay(1).atStartOfDay(), period.atEndOfMonth().atTime(23, 59, 59)));
        var days = repository.jdbc.queryForList("SELECT CAST(item.activity_time AS DATE) AS activity_day,COUNT(*) AS total,SUM(CASE WHEN item.version_no=1 THEN 1 ELSE 0 END) AS created_count " + query.where() + " GROUP BY CAST(item.activity_time AS DATE) ORDER BY activity_day", query.args().toArray());
        var result = new ArrayList<Map<String, Object>>();
        for (var day : days) {
            var date = java.time.LocalDate.parse(day.get("activity_day").toString());
            var params = new ArrayList<>(query.args()); params.add(date.atStartOfDay()); params.add(date.plusDays(1).atStartOfDay());
            var rows = repository.jdbc.queryForList("SELECT item.id,item.title,item.version_no " + query.where() + " AND item.activity_time>=? AND item.activity_time<? ORDER BY item.activity_time DESC,item.id DESC LIMIT 8", params.toArray());
            var titles = new java.util.LinkedHashSet<String>();
            for (var row : rows) {
                var names = repository.jdbc.queryForList("SELECT event.title FROM rule_ledger_event event JOIN rule_ledger_revision_reference link ON link.ref_id=event.id AND link.ref_type='EVENT' WHERE link.revision_id=? AND " + repository.visible("rule_ledger_event", "event") + " ORDER BY event.id", String.class, row.get("id"));
                if (names.isEmpty()) titles.add(row.get("title") + " · v" + row.get("version_no")); else titles.addAll(names);
            }
            result.add(Map.of("date", date.toString(), "count", number(day.get("total")), "created", number(day.get("created_count")), "modified", number(day.get("total")) - number(day.get("created_count")), "titles", titles.stream().limit(8).toList()));
        }
        return result;
    }
    public List<Map<String, Object>> events(String keyword, String scope, Long projectId) {
        access.userId(); String where = " WHERE " + repository.visible("rule_ledger_event", "item"); var args = new ArrayList<Object>();
        if (keyword != null && !keyword.isBlank()) { where += " AND item.title LIKE ?"; args.add("%" + keyword + "%"); }
        if (scope != null && !scope.isBlank()) { where += " AND item.scope=?"; args.add(scope); }
        if (projectId != null) { where += " AND item.project_id=?"; args.add(projectId); }
        return repository.jdbc.queryForList("SELECT item.* FROM rule_ledger_event item" + where + " ORDER BY item.update_time DESC,item.id DESC LIMIT 200", args.toArray()).stream().map(this::eventView).toList();
    }
    public Map<String, Object> event(long id) { return eventView(repository.one("rule_ledger_event", id, false)); }
    @Transactional
    public Map<String, Object> saveEvent(Long id, LedgerRequests.Event input) {
        access.userId(); access.requireWrite();
        String title = LedgerArticleService.text(input.title(), 200, "事件名称");
        if (title.isBlank()) throw new IllegalArgumentException("请填写事件名称");
        if (input.status() == null || !Set.of("FOLLOWING", "ACCEPTANCE", "DONE").contains(input.status())) throw new IllegalArgumentException("无效事件状态");
        access.requireTarget(input.scope(), input.projectId());
        Long project = "PROJECT".equals(input.scope()) ? input.projectId() : null;
        if (id == null) id = repository.insert("INSERT INTO rule_ledger_event (title,status,scope,project_id,owner_user_id,owner_name) VALUES (?,?,?,?,?,?)", title, input.status(), input.scope(), project, access.userId(), access.actor());
        else {
            var row = repository.one("rule_ledger_event", id, true); access.requireEdit("rule_ledger_event", row); LedgerAccess.version(row, input.version());
            if ((!input.scope().equals(row.get("scope")) || number(row.get("project_id")) != (project == null ? 0 : project)) && repository.referenced("EVENT", id)) throw new IllegalArgumentException("事件已被文章引用，不能改变归属范围");
            repository.jdbc.update("UPDATE rule_ledger_event SET title=?,status=?,scope=?,project_id=?,lock_version=lock_version+1,update_time=CURRENT_TIMESTAMP WHERE id=?", title, input.status(), input.scope(), project, id);
        }
        return eventView(repository.one("rule_ledger_event", id, false));
    }
    @Transactional
    public void deleteEvent(long id, Long version) {
        var row = repository.one("rule_ledger_event", id, true); access.requireEdit("rule_ledger_event", row); LedgerAccess.version(row, version);
        if (repository.referenced("EVENT", id)) throw new IllegalArgumentException("事件已被文章或历史版本引用，不能删除");
        repository.jdbc.update("UPDATE rule_ledger_event SET deleted=1,lock_version=lock_version+1 WHERE id=?", id);
    }
    private Map<String, Object> eventView(Map<String, Object> row) {
        var value = new LinkedHashMap<String, Object>();
        value.put("id",row.get("id")); value.put("title",row.get("title")); value.put("status",row.get("status")); value.put("scope",row.get("scope")); value.put("projectId",row.get("project_id")); value.put("version",row.get("lock_version")); value.put("canEdit",access.canEdit(row)); return value;
    }
    public List<Map<String, Object>> assetReferences(long id) {
        repository.one("rule_ledger_asset", id, false);
        var result = new ArrayList<Map<String, Object>>();
        var rows = repository.jdbc.queryForList("SELECT item.id,item.article_id,item.version_no,item.title,item.publish_time FROM rule_ledger_revision item JOIN rule_ledger_revision_reference ref ON ref.revision_id=item.id AND ref.ref_type='ASSET' WHERE ref.ref_id=? AND " + repository.visible("rule_ledger_revision", "item") + " ORDER BY item.publish_time DESC LIMIT 100", id);
        for (var row : rows) result.add(Map.of("articleId",row.get("article_id"),"revisionId",row.get("id"),"versionNo",row.get("version_no"),"title",row.get("title"),"type","PUBLISHED"));
        var drafts = repository.jdbc.queryForList("SELECT item.id FROM rule_ledger_article item JOIN rule_ledger_draft_reference ref ON ref.article_id=item.id AND ref.ref_type='ASSET' WHERE ref.ref_id=? AND " + repository.visibleDraft("item") + " AND " + repository.visible("rule_ledger_article", "item"), id);
        for (var row : drafts) result.add(Map.of("articleId",row.get("id"),"title","我的草稿 #"+row.get("id"),"type","DRAFT"));
        return result;
    }
}
