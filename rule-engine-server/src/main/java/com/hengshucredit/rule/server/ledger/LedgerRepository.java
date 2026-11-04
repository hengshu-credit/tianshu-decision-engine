package com.hengshucredit.rule.server.ledger;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.server.security.ConsoleAccessDeniedException;
import com.hengshucredit.rule.server.security.ConsoleDataPermissionHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 所有台账 JDBC 读取统一追加与 MyBatis 相同的范围条件。 */
@Repository
public class LedgerRepository {
    static final Set<String> TABLES = Set.of("rule_ledger_article", "rule_ledger_revision", "rule_ledger_event", "rule_ledger_asset");
    final JdbcTemplate jdbc;
    private final ConsoleDataPermissionHandler policy;
    public LedgerRepository(JdbcTemplate jdbc, ConsoleDataPermissionHandler policy) { this.jdbc = jdbc; this.policy = policy; }
    public String visible(String table, String alias) { return "(" + policy.predicate(table, alias) + ")"; }
    public String visibleDraft(String alias) { return policy.ledgerDraftPredicate(alias); }
    public Map<String, Object> one(String table, long id, boolean lock) {
        if (!TABLES.contains(table)) throw new IllegalArgumentException("无效台账资源");
        var rows = jdbc.queryForList("SELECT item.* FROM " + table + " item WHERE item.id = ? AND " + visible(table, "item") + (lock ? " FOR UPDATE" : ""), id);
        if (rows.isEmpty()) throw new ConsoleAccessDeniedException("内容不存在或无访问权限");
        var row = normalize(rows.get(0));
        for (String key : List.of("raw_content", "content")) {
            if (row.get(key) instanceof java.sql.Blob blob) {
                try { row.put(key, blob.getBytes(1, Math.toIntExact(blob.length()))); }
                catch (java.sql.SQLException error) { throw new IllegalStateException("素材内容读取失败", error); }
            }
        }
        return row;
    }
    public long insert(String sql, Object... values) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            return statement;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("未生成台账记录 ID");
        return key.getKey().longValue();
    }
    public Page<Map<String, Object>> page(String columns, String fromWhere, String order, List<Object> args, int pageNum, int pageSize) {
        int page = Math.max(1, pageNum), size = Math.max(1, Math.min(100, pageSize));
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + fromWhere, Long.class, args.toArray());
        var parameters = new java.util.ArrayList<>(args); parameters.add(size); parameters.add((long) (page - 1) * size);
        var rows = jdbc.queryForList("SELECT " + columns + " " + fromWhere + " ORDER BY " + order + " LIMIT ? OFFSET ?", parameters.toArray()).stream().map(this::normalize).toList();
        return new Page<Map<String, Object>>(page, size, total == null ? 0 : total).setRecords(rows);
    }
    public boolean referenced(String type, long id) {
        Long count = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM rule_ledger_revision_reference WHERE ref_type = ? AND ref_id = ?) + (SELECT COUNT(*) FROM rule_ledger_draft_reference WHERE ref_type = ? AND ref_id = ?)", Long.class, type, id, type, id);
        return count != null && count > 0;
    }
    public String projectName(Object id) {
        long project = LedgerAccess.number(id);
        if (project <= 0) return null;
        var names = jdbc.queryForList("SELECT project_name FROM rule_project WHERE id=?", String.class, project);
        return names.isEmpty() ? "项目 #" + project : names.get(0);
    }
    private Map<String, Object> normalize(Map<String, Object> row) {
        row.replaceAll((key, value) -> value instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime() : value);
        return row;
    }
}
