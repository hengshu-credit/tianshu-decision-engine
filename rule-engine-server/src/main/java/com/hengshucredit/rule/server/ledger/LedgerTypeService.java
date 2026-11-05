package com.hengshucredit.rule.server.ledger;

import com.hengshucredit.rule.model.dto.LedgerRequests;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import static com.hengshucredit.rule.server.ledger.LedgerAccess.number;

/** 全局分类字典；新内容引用稳定 ID，旧枚举仅用于兼容读取。 */
@Service
@org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
@org.springframework.context.annotation.DependsOn("schemaSyncService")
public class LedgerTypeService {
    private final LedgerRepository repository;
    private final LedgerAccess access;
    public LedgerTypeService(LedgerRepository repository, LedgerAccess access) { this.repository = repository; this.access = access; }
    @jakarta.annotation.PostConstruct
    public void initializeLegacyTypes() {
        // 将原先固定枚举迁移为可管理字典；不覆盖已改名或已删除的记录。
        String[][] values = { {"PRODUCT", "产品改动"}, {"STRATEGY", "策略改动"}, {"RELEASE", "规则上线"}, {"INCIDENT", "生产事故"}, {"MEETING", "周会结论"}, {"WORK", "日常工作"}, {"OTHER", "其他"} };
        for (int i = 0; i < values.length; i++) repository.update("INSERT INTO rule_ledger_type(legacy_code,type_code,type_name,sort_order) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE legacy_code=legacy_code", values[i][0], values[i][0], values[i][1], (i + 1) * 10);
    }
    public List<Map<String, Object>> list() {
        access.requireRead();
        return repository.queryForList("SELECT id,type_code,legacy_code,type_name,sort_order,lock_version FROM rule_ledger_type WHERE deleted=0 ORDER BY sort_order,id").stream().map(this::view).toList();
    }
    public Map<String, Object> resolve(String value, boolean lock) {
        access.requireRead();
        if (value == null || value.isBlank()) throw new IllegalArgumentException("请选择事件类型");
        List<Map<String,Object>> rows;
        if (value.matches("[1-9][0-9]*")) {
            long id;
            try { id = Long.parseLong(value); } catch (NumberFormatException error) { throw new IllegalArgumentException("事件类型 ID 无效"); }
            rows = repository.queryForList("SELECT * FROM rule_ledger_type WHERE id=? AND deleted=0" + (lock ? " FOR UPDATE" : ""), id);
        } else rows = repository.queryForList("SELECT * FROM rule_ledger_type WHERE legacy_code=? AND deleted=0" + (lock ? " FOR UPDATE" : ""), value);
        if (rows.isEmpty()) throw new IllegalArgumentException("事件类型不存在或已删除，请重新选择");
        return rows.get(0);
    }
    @Transactional
    public Map<String,Object> save(Long id, LedgerRequests.EventType input) {
        access.requireTypeManagement();
        String name = LedgerArticleService.text(input.name(), 100, "类型名称");
        if (name.isBlank()) throw new IllegalArgumentException("请填写类型名称");
        var current = id == null ? null : resolve(String.valueOf(id), true);
        String code = LedgerArticleService.text(input.code() == null && current != null ? String.valueOf(current.get("type_code")) : input.code(), 128, "类型编码");
        if (code.isBlank()) throw new IllegalArgumentException("请填写唯一的类型编码");
        if (number(repository.queryForObject("SELECT COUNT(*) FROM rule_ledger_type WHERE type_code=? AND id<>?", Long.class, code, id == null ? 0L : id)) > 0) throw new IllegalArgumentException("类型编码已存在或已使用，请更换编码");
        int order = input.sortOrder() == null ? 0 : input.sortOrder();
        try {
        if (id == null) id = repository.insert("INSERT INTO rule_ledger_type(type_code,type_name,sort_order) VALUES (?,?,?)", code, name, order);
        else {
            var row = resolve(String.valueOf(id), true); LedgerAccess.version(row, input.version());
            repository.update("UPDATE rule_ledger_type SET type_code=?,type_name=?,sort_order=?,lock_version=lock_version+1 WHERE id=?", code, name, order, id);
        }
        } catch (org.springframework.dao.DuplicateKeyException conflict) { throw new IllegalArgumentException("类型编码已存在或已使用，请更换编码"); }
        return view(resolve(String.valueOf(id), false));
    }
    @Transactional
    public void delete(long id, Long version) {
        access.requireTypeManagement();
        var row = resolve(String.valueOf(id), true); LedgerAccess.version(row, version);
        Long events = repository.queryForObject("SELECT COUNT(*) FROM rule_ledger_event_type WHERE type_id=?", Long.class, id);
        Long revisions = repository.queryForObject("SELECT COUNT(*) FROM rule_ledger_revision WHERE category=? OR category=?", Long.class, String.valueOf(id), row.get("legacy_code"));
        var patterns = new java.util.ArrayList<Object>(); patterns.add("%\"category\":\"" + id + "\"%");
        if (row.get("legacy_code") != null) patterns.add("%\"category\":\"" + row.get("legacy_code") + "\"%");
        Long legacyDrafts = repository.queryForObject("SELECT COUNT(*) FROM rule_ledger_article WHERE deleted=0 AND has_draft=1 AND (draft_json LIKE ?" + (patterns.size() == 2 ? " OR draft_json LIKE ?" : "") + ")", Long.class, patterns.toArray());
        if (number(events) > 0 || number(revisions) > 0 || number(legacyDrafts) > 0 || repository.referenced("CATEGORY", id)) throw new IllegalArgumentException("类型已被事件、草稿或历史文章引用，不能删除；可以修改名称");
        repository.update("UPDATE rule_ledger_type SET deleted=1,lock_version=lock_version+1 WHERE id=?", id);
    }
    private Map<String,Object> view(Map<String,Object> row) {
        var result = new LinkedHashMap<String,Object>(); result.put("id", row.get("id")); result.put("code", row.get("type_code")); result.put("value", String.valueOf(row.get("id"))); result.put("legacyCode", row.get("legacy_code")); result.put("name", row.get("type_name")); result.put("label", row.get("type_name")); result.put("sortOrder", row.get("sort_order")); result.put("version", row.get("lock_version")); result.put("canManage", access.canManageTypes()); return result;
    }
}
