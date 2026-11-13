package com.hengshucredit.rule.server.ledger;

import com.hengshucredit.rule.server.service.ReservedContentCode;

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
    private final LedgerTypeService types;
    public LedgerQueryService(LedgerRepository repository, LedgerAccess access, LedgerArticleService articles, LedgerTypeService types) { this.repository = repository; this.access = access; this.articles = articles; this.types = types; }
    public record Filter(String scope, Long projectId, List<Long> eventIds, String category, String keyword, LocalDateTime start, LocalDateTime end) { }
    private record Query(String where, List<Object> args) { }

    private Query query(Filter filter) {
        access.userId();
        String where = "FROM rule_ledger_revision item WHERE " + repository.visible("rule_ledger_revision", "item");
        var args = new ArrayList<Object>();
        if (filter.scope() != null && !filter.scope().isBlank()) { where += " AND item.scope=?"; args.add(filter.scope()); }
        if (filter.projectId() != null) { where += " AND item.project_id=?"; args.add(filter.projectId()); }
        if (filter.category() != null && !filter.category().isBlank()) {
            var type = types.resolve(filter.category(), false);
            where += " AND (item.category=? OR item.category=? OR EXISTS (SELECT 1 FROM rule_ledger_revision_reference type_ref JOIN rule_ledger_event_type event_type ON event_type.event_id=type_ref.ref_id JOIN rule_ledger_event type_event ON type_event.id=event_type.event_id WHERE type_ref.revision_id=item.id AND type_ref.ref_type='EVENT' AND event_type.type_id=? AND " + repository.visible("rule_ledger_event", "type_event") + "))";
            args.add(String.valueOf(type.get("id"))); args.add(type.get("legacy_code")); args.add(type.get("id"));
        }
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
        Query base = query(new Filter(filters.scope(), filters.projectId(), filters.eventIds(), filters.category(), filters.keyword(), period.atDay(1).atStartOfDay(), null));
        var monthArgs = new ArrayList<>(base.args());
        monthArgs.add(period.plusMonths(1).atDay(1).atStartOfDay());
        Query query = new Query(base.where() + " AND item.activity_time<?", monthArgs);
        var days = repository.queryForList("SELECT CAST(item.activity_time AS DATE) AS activity_day,COUNT(*) AS total,SUM(CASE WHEN item.version_no=1 THEN 1 ELSE 0 END) AS created_count " + query.where() + " GROUP BY CAST(item.activity_time AS DATE) ORDER BY activity_day", query.args().toArray());
        if (days.isEmpty()) return List.of();
        // 每日最多八个预览版本：数据库内分组限量，避免整月正文加载和逐日查询。
        var previews = repository.queryForList("SELECT id,title,version_no,activity_day FROM (SELECT item.id,item.title,item.version_no,item.activity_time,CAST(item.activity_time AS DATE) AS activity_day,ROW_NUMBER() OVER (PARTITION BY CAST(item.activity_time AS DATE) ORDER BY item.activity_time DESC,item.id DESC) AS preview_rank "
                + query.where() + ") preview WHERE preview_rank<=8 ORDER BY activity_day,activity_time DESC,id DESC", query.args().toArray());
        var eventTitles = new LinkedHashMap<Long, List<String>>();
        if (!previews.isEmpty()) {
            var ids = previews.stream().map(row -> row.get("id")).toList();
            var events = repository.queryForList("SELECT link.revision_id,event.title FROM rule_ledger_event event JOIN rule_ledger_revision_reference link ON link.ref_id=event.id AND link.ref_type='EVENT' WHERE link.revision_id IN ("
                    + String.join(",", java.util.Collections.nCopies(ids.size(), "?")) + ") AND " + repository.visible("rule_ledger_event", "event") + " ORDER BY link.revision_id,event.id", ids.toArray());
            for (var event : events) eventTitles.computeIfAbsent(number(event.get("revision_id")), ignored -> new ArrayList<>()).add(String.valueOf(event.get("title")));
        }
        var dailyTitles = new LinkedHashMap<String, java.util.LinkedHashSet<String>>();
        for (var row : previews) {
            var titles = dailyTitles.computeIfAbsent(row.get("activity_day").toString(), ignored -> new java.util.LinkedHashSet<>());
            var names = eventTitles.getOrDefault(number(row.get("id")), List.of());
            if (names.isEmpty()) titles.add(row.get("title") + " · v" + row.get("version_no")); else titles.addAll(names);
        }
        var result = new ArrayList<Map<String, Object>>();
        for (var day : days) {
            var date = java.time.LocalDate.parse(day.get("activity_day").toString());
            var titles = dailyTitles.getOrDefault(date.toString(), new java.util.LinkedHashSet<>());
            result.add(Map.of("date", date.toString(), "count", number(day.get("total")), "created", number(day.get("created_count")), "modified", number(day.get("total")) - number(day.get("created_count")), "titles", titles.stream().limit(8).toList()));
        }
        return result;
    }
    public List<Map<String, Object>> events(String keyword, String scope, Long projectId) {
        access.userId(); String where = " WHERE " + repository.visible("rule_ledger_event", "item"); var args = new ArrayList<Object>();
        if (keyword != null && !keyword.isBlank()) { where += " AND item.title LIKE ?"; args.add("%" + keyword + "%"); }
        if (scope != null && !scope.isBlank()) { where += " AND item.scope=?"; args.add(scope); }
        if (projectId != null) { where += " AND item.project_id=?"; args.add(projectId); }
        return repository.queryForList("SELECT item.*,link.type_id,category.type_name,state.options_json AS status_options_json FROM rule_ledger_event item LEFT JOIN rule_ledger_event_type link ON link.event_id=item.id LEFT JOIN rule_ledger_type category ON category.id=link.type_id LEFT JOIN rule_ledger_event_state state ON state.event_id=item.id" + where + " ORDER BY item.update_time DESC,item.id DESC LIMIT 200", args.toArray()).stream().map(this::eventView).toList();
    }
    public Page<Map<String,Object>> manageEvents(String keyword, Long typeId, int page, int size) {
        access.requireRead();
        String where = "FROM rule_ledger_event item LEFT JOIN rule_ledger_event_type link ON link.event_id=item.id LEFT JOIN rule_ledger_type category ON category.id=link.type_id LEFT JOIN rule_ledger_event_state state ON state.event_id=item.id WHERE " + repository.visible("rule_ledger_event", "item");
        var args = new ArrayList<Object>();
        if (keyword != null && !keyword.isBlank()) { where += " AND item.title LIKE ?"; args.add("%" + keyword + "%"); }
        if (typeId != null) { where += " AND link.type_id=?"; args.add(typeId); }
        var result = repository.page("item.*,link.type_id,category.type_name,state.options_json AS status_options_json", where, "item.update_time DESC,item.id DESC", args, page, size);
        result.setRecords(result.getRecords().stream().map(this::eventView).toList()); return result;
    }
    public Map<String, Object> event(long id) {
        var row = repository.one("rule_ledger_event", id, false);
        var type = repository.queryForList("SELECT link.type_id,category.type_name FROM rule_ledger_event_type link JOIN rule_ledger_type category ON category.id=link.type_id WHERE link.event_id=?", id);
        if (!type.isEmpty()) row.putAll(type.get(0));
        var states = repository.queryForList("SELECT options_json AS status_options_json FROM rule_ledger_event_state WHERE event_id=?", id);
        if (!states.isEmpty()) row.putAll(states.get(0));
        return eventView(row);
    }
    @Transactional
    public Map<String, Object> saveEvent(Long id, LedgerRequests.Event input) {
        access.userId(); access.requireWrite();
        String title = LedgerArticleService.text(input.title(), 200, "事件名称");
        if (title.isBlank()) throw new IllegalArgumentException("请填写事件名称");
        var current = id == null ? null : repository.one("rule_ledger_event", id, false);
        String code = LedgerArticleService.text(input.code() == null && current != null ? String.valueOf(current.get("event_code")) : input.code(), 128, "事件编码");
        if (code.isBlank()) throw new IllegalArgumentException("请填写唯一的事件编码");
        ReservedContentCode.requireAllowed(code);
        if (number(repository.queryForObject("SELECT COUNT(*) FROM rule_ledger_event WHERE event_code=? AND id<>?", Long.class, code, id == null ? 0L : id)) > 0) throw new IllegalArgumentException("事件编码已存在或已使用，请更换编码");
        List<LedgerRequests.StatusOption> options = input.statusOptions();
        if (options == null) {
            // 旧客户端不传枚举时保留现有配置；旧事件首次编辑沿用原有三种状态。
            if (id == null) options = legacyStatuses(null);
            else {
                var visible = repository.one("rule_ledger_event", id, false);
                var states = repository.queryForList("SELECT options_json FROM rule_ledger_event_state WHERE event_id=?", id);
                options = statuses(states.isEmpty() ? null : states.get(0).get("options_json"), String.valueOf(visible.get("status")));
            }
        }
        options = validateStatuses(options);
        if (input.status() == null || options.stream().noneMatch(option -> option.value().equals(input.status()))) throw new IllegalArgumentException("当前状态必须从本事件配置的枚举值中选择");
        access.requireTarget(input.scope(), input.projectId());
        if (input.typeId() != null) types.resolve(String.valueOf(input.typeId()), true);
        Long project = "PROJECT".equals(input.scope()) ? input.projectId() : null;
        try {
        if (id == null) id = repository.insert("INSERT INTO rule_ledger_event (event_code,title,status,scope,project_id,owner_user_id,owner_name) VALUES (?,?,?,?,?,?,?)", code, title, input.status(), input.scope(), project, access.userId(), access.actor());
        else {
            var row = repository.one("rule_ledger_event", id, true); access.requireEdit("rule_ledger_event", row); LedgerAccess.version(row, input.version());
            if ((!input.scope().equals(row.get("scope")) || number(row.get("project_id")) != (project == null ? 0 : project)) && repository.referenced("EVENT", id)) throw new IllegalArgumentException("事件已被文章引用，不能改变归属范围");
            repository.update("UPDATE rule_ledger_event SET event_code=?,title=?,status=?,scope=?,project_id=?,lock_version=lock_version+1,update_time=CURRENT_TIMESTAMP WHERE id=?", code, title, input.status(), input.scope(), project, id);
        }
        } catch (org.springframework.dao.DuplicateKeyException conflict) { throw new IllegalArgumentException("事件编码已存在或已使用，请更换编码"); }
        repository.update("DELETE FROM rule_ledger_event_type WHERE event_id=?", id);
        if (input.typeId() != null) repository.update("INSERT INTO rule_ledger_event_type(event_id,type_id) VALUES (?,?)", id, input.typeId());
        repository.update("DELETE FROM rule_ledger_event_state WHERE event_id=?", id);
        var serialized = new com.alibaba.fastjson.JSONArray();
        for (var option : options) { var entry = new com.alibaba.fastjson.JSONObject(); entry.put("value", option.value()); entry.put("label", option.label()); serialized.add(entry); }
        repository.update("INSERT INTO rule_ledger_event_state(event_id,options_json) VALUES (?,?)", id, serialized.toJSONString());
        return event(id);
    }
    @Transactional
    public void deleteEvent(long id, Long version) {
        var row = repository.one("rule_ledger_event", id, true); access.requireEdit("rule_ledger_event", row); LedgerAccess.version(row, version);
        if (repository.referenced("EVENT", id)) throw new IllegalArgumentException("事件已被文章或历史版本引用，不能删除");
        repository.update("UPDATE rule_ledger_event SET deleted=1,lock_version=lock_version+1 WHERE id=?", id);
        repository.update("DELETE FROM rule_ledger_event_type WHERE event_id=?", id);
        repository.update("DELETE FROM rule_ledger_event_state WHERE event_id=?", id);
    }
    private List<LedgerRequests.StatusOption> legacyStatuses(String current) {
        var result = new ArrayList<>(List.of(new LedgerRequests.StatusOption("FOLLOWING", "跟进中"), new LedgerRequests.StatusOption("ACCEPTANCE", "待验收"), new LedgerRequests.StatusOption("DONE", "已完成")));
        if (current != null && !current.isBlank() && result.stream().noneMatch(option -> option.value().equals(current))) result.add(new LedgerRequests.StatusOption(current, current));
        return result;
    }
    private List<LedgerRequests.StatusOption> statuses(Object json, String current) {
        if (json == null) return legacyStatuses(current);
        var result = new ArrayList<LedgerRequests.StatusOption>();
        var entries = com.alibaba.fastjson.JSON.parseArray(String.valueOf(json));
        for (int i = 0; i < entries.size(); i++) { var item = entries.getJSONObject(i); result.add(new LedgerRequests.StatusOption(item.getString("value"), item.getString("label"))); }
        return result;
    }
    private List<LedgerRequests.StatusOption> validateStatuses(List<LedgerRequests.StatusOption> options) {
        if (options == null || options.isEmpty() || options.size() > 50) throw new IllegalArgumentException("请配置 1 至 50 个事件状态枚举");
        var values = new java.util.HashSet<String>();
        for (var option : options) {
            if (option == null || option.value() == null || option.value().isBlank() || option.value().length() > 24) throw new IllegalArgumentException("状态值不能为空，且不能超过 24 个字符");
            if (!values.add(option.value())) throw new IllegalArgumentException("事件状态枚举值不能重复");
            if (option.label() != null && option.label().length() > 80) throw new IllegalArgumentException("状态显示名称不能超过 80 个字符");
        }
        return List.copyOf(options);
    }
    private Map<String, Object> eventView(Map<String, Object> row) {
        var value = new LinkedHashMap<String, Object>();
        value.put("code",row.get("event_code"));
        var options = statuses(row.get("status_options_json"), String.valueOf(row.get("status")));
        value.put("statusOptions", options);
        value.put("statusLabel", options.stream().filter(option -> option.value().equals(row.get("status"))).map(option -> option.label() == null || option.label().isBlank() ? option.value() : option.label()).findFirst().orElse(String.valueOf(row.get("status"))));
        value.put("id",row.get("id")); value.put("title",row.get("title")); value.put("status",row.get("status")); value.put("scope",row.get("scope")); value.put("projectId",row.get("project_id")); value.put("version",row.get("lock_version")); value.put("canEdit",access.canEdit(row)); value.put("typeId",row.get("type_id")); value.put("typeName",row.get("type_name")); return value;
    }
    public List<Map<String, Object>> assetReferences(long id) {
        repository.one("rule_ledger_asset", id, false);
        var result = new ArrayList<Map<String, Object>>();
        var rows = repository.queryForList("SELECT item.id,item.article_id,item.version_no,item.title,item.publish_time FROM rule_ledger_revision item JOIN rule_ledger_revision_reference ref ON ref.revision_id=item.id AND ref.ref_type='ASSET' WHERE ref.ref_id=? AND " + repository.visible("rule_ledger_revision", "item") + " ORDER BY item.publish_time DESC LIMIT 100", id);
        for (var row : rows) result.add(Map.of("articleId",row.get("article_id"),"revisionId",row.get("id"),"versionNo",row.get("version_no"),"title",row.get("title"),"type","PUBLISHED"));
        var drafts = repository.queryForList("SELECT item.id FROM rule_ledger_article item JOIN rule_ledger_draft_reference ref ON ref.article_id=item.id AND ref.ref_type='ASSET' WHERE ref.ref_id=? AND " + repository.visibleDraft("item") + " AND " + repository.visible("rule_ledger_article", "item"), id);
        for (var row : drafts) result.add(Map.of("articleId",row.get("id"),"title","我的草稿 #"+row.get("id"),"type","DRAFT"));
        return result;
    }
}
