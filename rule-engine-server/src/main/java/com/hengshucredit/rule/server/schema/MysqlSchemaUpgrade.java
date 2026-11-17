package com.hengshucredit.rule.server.schema;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.hengshucredit.rule.server.schema.MysqlTableDefinition.id;
import static com.hengshucredit.rule.server.schema.MysqlTableDefinition.keyword;

/** Converges only the objects declared by the packaged schema, on the lock-owning MySQL session. */
public final class MysqlSchemaUpgrade {
    private static final Logger log = LoggerFactory.getLogger(MysqlSchemaUpgrade.class);
    private static final Pattern TABLE = Pattern.compile("(?is)^CREATE\\s+TABLE\\b.*");
    private static final Pattern SIZED = Pattern.compile("(varchar|varbinary|decimal)\\((\\d+)(?:,(\\d+))?\\)( unsigned)?");
    private static final String IDENT = "(?:`(?:``|[^`])+`|[a-zA-Z_][a-zA-Z0-9_$]*)";
    private static final Pattern FK = Pattern.compile("(?is)^CONSTRAINT\\s+" + IDENT
            + "\\s+FOREIGN\\s+KEY\\s*\\(([^()]*)\\)\\s+REFERENCES\\s+(" + IDENT + ")\\s*\\(([^()]*)\\)(.*)$");
    private static final Pattern RULE = Pattern.compile("(?i)\\s+ON\\s+(DELETE|UPDATE)\\s+(RESTRICT|NO\\s+ACTION|SET\\s+NULL|CASCADE)");
    private static final Set<String> LEGACY_API_JSON = Set.of("execution_config", "header_config", "query_config", "request_mapping",
            "response_mapping", "payload_capture_config", "auth_api_config", "cache_key_config", "success_condition", "exception_condition",
            "token_failure_condition", "retry_condition", "async_poll_config", "async_callback_config", "billing_condition", "fallback_value", "test_sample_params");
    private final Connection connection;
    private final String reference = "ts_schema_ref_" + UUID.randomUUID().toString().replace("-", "");
    private final Map<String, Contract> contracts = new LinkedHashMap<>();
    private final List<String> setup = new ArrayList<>(), finish = new ArrayList<>();
    private final List<Change> changes = new ArrayList<>(), constraints = new ArrayList<>();
    private String step = "读取更新包";
    private int applied;

    private record Column(String type, String collation, boolean nullable, String initial, String extra, String ddl) {
        boolean generated() { return extra.contains("VIRTUAL GENERATED") || extra.contains("STORED GENERATED"); }
        String expression() {
            if (!generated()) return "";
            int start = keyword(ddl, "GENERATED");
            int end = keyword(ddl, extra.contains("STORED GENERATED") ? "STORED" : "VIRTUAL");
            return ddl.substring(start, end);
        }
        boolean equivalent(Column other) {
            // SHOW CREATE is canonical for both temporary and persistent tables. SHOW FULL COLUMNS
            // represents empty Extra and expression defaults differently for temporary tables.
            return ddl.equals(other.ddl) && Objects.equals(collation, other.collation);
        }
        String fixedAttributes() {
            // Defaults and ON UPDATE timestamps are schema-managed. Do not silently change identity,
            // visibility, storage or generation semantics.
            return "NULL".equals(extra) ? "" : extra.replace("DEFAULT_GENERATED", "").replaceAll("(?i)on update CURRENT_TIMESTAMP(?:\\(\\d*\\))?", "").trim();
        }
    }
    private record Contract(MysqlTableDefinition ddl, MysqlTableDefinition.Storage storage, String comment,
                            Map<String, Column> columns, Map<String, String> indexes) { }
    private record TableInfo(String type, String engine, String collation, String comment, boolean partitioned) { }
    private record Change(String object, String action, String sql) { }

    public MysqlSchemaUpgrade(Connection connection) { this.connection = connection; }

    /** Call once per instance. The caller holds the schema lock through data initialization and commit. */
    public int run(String schema) throws SQLException {
        long start = System.nanoTime();
        String fingerprint;
        try {
            fingerprint = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(schema.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        log.info("[SchemaUpgrade] 开始 database={} schemaSha256={}", connection.getCatalog(), fingerprint);
        try {
            Map<String, MysqlTableDefinition> tables = new LinkedHashMap<>();
            for (String sql : MysqlTableDefinition.statements(schema)) {
                if (TABLE.matcher(sql).matches()) {
                    var table = MysqlTableDefinition.parse(sql);
                    if (tables.putIfAbsent(table.name, table) != null) throw blocked(table.name, "schema 重复定义表");
                } else if (sql.matches("(?is)^SET\\s+(?:NAMES|CHARACTER_SET_CONNECTION)\\s+.*")) setup.add(sql);
                else if (sql.matches("(?is)^(?:INSERT|SET|PREPARE|EXECUTE|DEALLOCATE|CREATE OR REPLACE (?:SQL SECURITY INVOKER )?VIEW)\\s+.*"))
                    finish.add(sql);
                else throw blocked("schema.sql", "不支持的语句；表结构请写入 CREATE TABLE，数据迁移请使用版本脚本");
            }
            if (tables.isEmpty()) throw blocked("schema.sql", "没有表定义");
            for (String sql : setup) execute(sql);
            requireSafeSession();
            Map<String, TableInfo> live = tableInfo();
            for (var table : tables.values()) {
                step = "核对 " + table.name;
                var actual = live.get(table.name);
                if (actual != null && !"BASE TABLE".equals(actual.type)) throw blocked(table.name, "同名对象不是基本表");
                // Let the installed MySQL version normalize defaults, generated expressions, index prefixes
                // and collation, without creating a reference database or permanent comparison tables.
                String options = table.options;
                var storage = table.storage();
                if (actual != null && storage.collation() == null && matchesCharset(storage.charset(), actual.collation)) {
                    options += " COLLATE=" + actual.collation;
                }
                execute(table.create(reference, true, options));
                try {
                    var normalized = showDefinition(reference);
                    contracts.put(table.name, new Contract(table, storage, stringValue(storage.commentLiteral()),
                            columns(reference, normalized), normalized.indexes));
                } finally { execute("DROP TEMPORARY TABLE " + id(reference)); }
                if (contracts.size() % 25 == 0)
                    log.info("[SchemaUpgrade] 读取目标结构 {}/{} tables", contracts.size(), tables.size());
            }
            plan(live);
            int planned = changes.size() + constraints.size();
            log.info("[SchemaUpgrade] 比对完成 tables={} planned={}", tables.size(), planned);
            for (var change : changes) apply(change);
            for (var change : constraints) apply(change);
            if (planned > 0) {
                changes.clear(); constraints.clear();
                plan(tableInfo());
                if (!changes.isEmpty() || !constraints.isEmpty())
                    throw blocked("复核", "仍有未收敛的结构差异: " + (!changes.isEmpty() ? changes : constraints).get(0).object);
            }
            step = "初始化系统常量和视图";
            for (String sql : finish) execute(sql);
            log.info("[SchemaUpgrade] 结构就绪 database={} tables={} applied={} elapsedMs={}",
                    connection.getCatalog(), tables.size(), applied, elapsed(start));
            return applied;
        } catch (SQLException e) {
            // MySQL may put business values in "Duplicate entry" and conversion errors. Keep
            // the failing operation and vendor diagnostics in the startup exception, not the row.
            String reason = switch (e.getErrorCode()) {
                case 1062 -> "唯一键存在重复值";
                case 1451, 1452 -> "外键引用不完整或存在冲突";
                case 1044, 1142 -> "当前库权限不足（含 CREATE TEMPORARY TABLES）";
                case 1205, 1213 -> "数据库锁超时或死锁";
                default -> "请核对该对象的 DDL、数据与数据库权限";
            };
            String detail = step + ": " + reason + " (SQLState=" + e.getSQLState() + ", MySQL=" + e.getErrorCode() + ")";
            log.error("[SchemaUpgrade] 升级失败，停止启动 applied={} elapsedMs={} 原因={}", applied, elapsed(start), detail);
            throw new SQLException(detail, e.getSQLState(), e.getErrorCode());
        } catch (RuntimeException e) {
            log.error("[SchemaUpgrade] 升级失败，停止启动 step={} applied={} elapsedMs={} 原因={}", step, applied, elapsed(start), e.getMessage());
            throw e;
        }
    }

    private void requireSafeSession() throws SQLException {
        try (var s = connection.createStatement(); var r = s.executeQuery("SELECT @@session.foreign_key_checks, @@session.unique_checks, @@session.sql_mode")) {
            r.next();
            String mode = r.getString(3);
            if (!r.getBoolean(1) || !r.getBoolean(2) || !(mode.contains("STRICT_TRANS_TABLES") || mode.contains("STRICT_ALL_TABLES"))
                    || mode.contains("NO_BACKSLASH_ESCAPES") || mode.contains("ANSI_QUOTES"))
                throw blocked("MySQL 会话", "需要严格 SQL 模式、外键和唯一性检查，以及标准 MySQL 引号与转义规则");
        }
    }

    private void plan(Map<String, TableInfo> live) throws SQLException {
        for (var contract : contracts.values()) {
            var target = contract.ddl;
            String table = target.name;
            step = "比对 " + table;
            var info = live.get(table);
            if (info == null) {
                changes.add(new Change(table, "建表", target.create(table, false, target.options)));
                target.foreignKeys.forEach((key, ddl) -> addForeign(table, key, ddl));
                continue;
            }
            String collate = contract.storage.collation();
            if (!info.engine.equalsIgnoreCase(contract.storage.engine()) || !matchesCharset(contract.storage.charset(), info.collation)
                    || collate != null && !collate.equalsIgnoreCase(info.collation)) throw blocked(table, "存储引擎、字符集或表排序规则不同");
            MysqlPartitionContract.verify(connection, target, info.partitioned);
            var actual = showDefinition(table);
            var existing = columns(table, actual);
            List<String> clauses = new ArrayList<>(), labels = new ArrayList<>();
            for (var entry : contract.columns.entrySet()) {
                String name = entry.getKey();
                var wanted = entry.getValue();
                var current = existing.get(name);
                boolean legacyRow = table.equals("rule_list_change_item") && name.equals("source_row") && existing.containsKey("row_number");
                if (legacyRow && current != null) throw blocked(table + "." + name, "新旧行号列同时存在，需确认数据归属");
                if (legacyRow) current = existing.get("row_number");
                String operation;
                if (current == null) {
                    if (!wanted.nullable && wanted.initial == null && !wanted.generated() && hasRows(table, null))
                        throw blocked(table + "." + name, "非空表新增必填列没有默认值，需要先回填");
                    operation = "ADD COLUMN ";
                } else {
                    if (!legacyRow && current.equivalent(wanted)) continue;
                    boolean compatibleType = (canWiden(current.type, wanted.type) && Objects.equals(current.collation, wanted.collation))
                            || compatibleLegacyPayload(table, name, current.type, wanted.type);
                    if (!compatibleType || !current.fixedAttributes().equals(wanted.fixedAttributes()) || !current.expression().equals(wanted.expression()))
                        throw blocked(table + "." + name, "列类型、排序规则、生成表达式或特殊属性不能无损转换 (" + current.type + " -> " + wanted.type + ")");
                    if (current.nullable && !wanted.nullable && hasRows(table, legacyRow ? "row_number" : name))
                        throw blocked(table + "." + name, "存在 NULL，需要先回填再改为必填");
                    operation = legacyRow ? "CHANGE COLUMN `row_number` " : "MODIFY COLUMN ";
                }
                clauses.add(operation + target.columns.get(name));
                labels.add(operation.trim() + " " + name);
            }
            for (var entry : contract.indexes.entrySet()) {
                String name = entry.getKey(), ddl = entry.getValue(), before = actual.indexes.get(name);
                if (ddl.equals(before)) continue;
                if (before != null) {
                    if ("PRIMARY".equals(name) || isUnique(before) != isUnique(ddl))
                        throw blocked(table + "." + name, "主键或索引唯一性变化");
                    clauses.add("DROP INDEX " + id(name));
                }
                clauses.add("ADD " + target.indexes.get(name));
                labels.add((before == null ? "新增索引 " : "替换索引 ") + name);
            }
            // Only these exact legacy constraints are retired: model I/O now uses ref_type,
            // and model_code uniqueness is scoped to a project. Leave operator-added keys alone.
            if (table.equals("rule_model") && contract.indexes.containsKey("uk_model_code_scope")
                    && !contract.indexes.containsKey("uk_model_code")
                    && "UNIQUE KEY `uk_model_code` (`model_code`)".equals(actual.indexes.get("uk_model_code"))) {
                clauses.add("DROP INDEX `uk_model_code`"); labels.add("移除历史全局模型编码索引");
            }
            String obsolete = switch (table) {
                case "rule_model_input_field" -> "fk_input_var";
                case "rule_model_output_field" -> "fk_output_var";
                default -> "";
            };
            if (actual.foreignKeys.containsKey(obsolete) && !target.foreignKeys.containsKey(obsolete)
                    && foreignForm(actual.foreignKeys.get(obsolete)).equals(new Foreign(List.of("var_id"), "rule_variable", List.of("id"), "SET NULL", "RESTRICT"))) {
                clauses.add("DROP FOREIGN KEY " + id(obsolete)); labels.add("移除历史变量单类型外键 " + obsolete);
            }
            if (!contract.comment.equals(info.comment)) {
                clauses.add("COMMENT=" + contract.storage.commentLiteral()); labels.add("更新表注释");
            }
            if (!clauses.isEmpty()) changes.add(new Change(table, String.join("; ", labels),
                    "ALTER TABLE " + id(table) + " " + String.join(", ", clauses)));
            for (var entry : target.foreignKeys.entrySet()) {
                String key = entry.getKey(), before = actual.foreignKeys.get(key);
                if (before == null) {
                    var wanted = foreignForm(entry.getValue());
                    for (var existingKey : actual.foreignKeys.entrySet()) {
                        if (!target.foreignKeys.containsKey(existingKey.getKey())
                                && foreignForm(existingKey.getValue()).columns.equals(wanted.columns))
                            throw blocked(table + "." + key, "相同列已有不同名称的外键 " + existingKey.getKey() + "，需核对引用语义");
                    }
                    addForeign(table, key, entry.getValue());
                }
                else if (!foreignForm(before).equals(foreignForm(entry.getValue())))
                    throw blocked(table + "." + key, "同名外键的引用目标或更新/删除规则不同");
            }
        }
    }

    private record Foreign(List<String> columns, String parent, List<String> referencedColumns, String onDelete, String onUpdate) { }

    private static Foreign foreignForm(String value) {
        var fk = FK.matcher(value);
        if (!fk.matches()) throw blocked("外键", "不支持跨库或未命名的约束");
        String delete = "RESTRICT", update = "RESTRICT", suffix = fk.group(4).stripTrailing();
        var rule = RULE.matcher(suffix);
        for (int at = 0; at < suffix.length(); at = rule.end()) {
            if (!rule.region(at, suffix.length()).lookingAt()) throw blocked("外键", "不支持的引用动作");
            String action = rule.group(2).toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").replace("NO ACTION", "RESTRICT");
            if ("DELETE".equalsIgnoreCase(rule.group(1))) delete = action; else update = action;
        }
        return new Foreign(keyColumns(fk.group(1)), MysqlTableDefinition.unquote(fk.group(2)), keyColumns(fk.group(3)), delete, update);
    }
    private static List<String> keyColumns(String names) {
        return java.util.Arrays.stream(names.split(",")).map(String::trim).map(MysqlTableDefinition::unquote).toList();
    }
    private static boolean isUnique(String ddl) { return ddl.startsWith("UNIQUE ") || ddl.startsWith("PRIMARY "); }

    private void addForeign(String table, String key, String ddl) {
        constraints.add(new Change(table + "." + key, "新增外键", "ALTER TABLE " + id(table) + " ADD " + ddl));
    }
    private void apply(Change change) throws SQLException {
        step = change.object + " " + change.action;
        long begin = System.nanoTime();
        log.info("[SchemaUpgrade] 执行 object={} action={}", change.object, change.action);
        execute(change.sql);
        applied++;
        log.info("[SchemaUpgrade] 成功 object={} elapsedMs={}", change.object, elapsed(begin));
    }

    static boolean canWiden(String from, String to) {
        if (from.equals(to)) return true;
        var a = SIZED.matcher(from); var b = SIZED.matcher(to);
        if (a.matches() && b.matches() && a.group(1).equals(b.group(1)) && Objects.equals(a.group(4), b.group(4))) {
            int x = Integer.parseInt(a.group(2)), y = Integer.parseInt(b.group(2));
            int xs = a.group(3) == null ? 0 : Integer.parseInt(a.group(3)), ys = b.group(3) == null ? 0 : Integer.parseInt(b.group(3));
            return ys >= xs && y - ys >= x - xs;
        }
        if (from.endsWith(" unsigned") != to.endsWith(" unsigned")) return false;
        String first = from.replace(" unsigned", ""), last = to.replace(" unsigned", "");
        for (var family : List.of(List.of("tinyint", "smallint", "mediumint", "int", "bigint"),
                List.of("tinytext", "text", "mediumtext", "longtext"), List.of("tinyblob", "blob", "mediumblob", "longblob")))
            if (family.contains(first) && family.indexOf(last) > family.indexOf(first)) return true;
        return from.startsWith("varchar(") && (to.equals("mediumtext") || to.equals("longtext"));
    }

    private MysqlTableDefinition showDefinition(String name) throws SQLException {
        try (var s = connection.createStatement(); var r = s.executeQuery("SHOW CREATE TABLE " + id(name))) {
            r.next(); return MysqlTableDefinition.parse(r.getString(2), true);
        }
    }
    private Map<String, Column> columns(String table, MysqlTableDefinition defs) throws SQLException {
        Map<String, Column> out = new LinkedHashMap<>();
        try (var s = connection.createStatement(); var r = s.executeQuery("SHOW FULL COLUMNS FROM " + id(table))) {
            while (r.next()) {
                String name = r.getString("Field");
                out.put(name, new Column(r.getString("Type"), r.getString("Collation"), "YES".equals(r.getString("Null")),
                        r.getString("Default"), Objects.toString(r.getString("Extra"), ""), defs.columns.get(name)));
            }
        }
        return out;
    }
    private Map<String, TableInfo> tableInfo() throws SQLException {
        Map<String, TableInfo> out = new LinkedHashMap<>();
        try (var s = connection.createStatement(); var r = s.executeQuery("SELECT table_name,table_type,engine,table_collation,table_comment,create_options FROM information_schema.tables WHERE table_schema=DATABASE()")) {
            while (r.next()) out.put(r.getString(1), new TableInfo(r.getString(2), r.getString(3), r.getString(4), r.getString(5),
                    Objects.toString(r.getString(6), "").contains("partitioned")));
        }
        return out;
    }
    private boolean hasRows(String table, String nullColumn) throws SQLException {
        try (var s = connection.createStatement(); var r = s.executeQuery("SELECT 1 FROM " + id(table)
                + (nullColumn == null ? "" : " WHERE " + id(nullColumn) + " IS NULL") + " LIMIT 1")) { return r.next(); }
    }
    private boolean compatibleLegacyPayload(String table, String column, String from, String to) throws SQLException {
        if (table.equals("rule_api_doc_scenario") && Set.of("request_json", "response_json").contains(column))
            return "json".equals(from) && "longtext".equals(to);
        if (!"json".equals(to) || !(from.endsWith("text") || from.startsWith("varchar("))
                || !(table.equals("rule_external_api_config") && LEGACY_API_JSON.contains(column)
                || table.equals("rule_external_datasource") && column.equals("auth_config"))) return false;
        try (var s = connection.createStatement(); var r = s.executeQuery("SELECT 1 FROM " + id(table)
                + " WHERE " + id(column) + " IS NOT NULL AND NOT JSON_VALID(" + id(column) + ") LIMIT 1")) {
            if (r.next()) throw blocked(table + "." + column, "历史外数配置含非法 JSON，原值未修改");
        }
        return true;
    }
    private String stringValue(String literal) throws SQLException {
        try (var s = connection.createStatement(); var r = s.executeQuery("SELECT " + literal)) { r.next(); return r.getString(1); }
    }
    private static boolean matchesCharset(String charset, String collation) {
        return collation != null && collation.toLowerCase(Locale.ROOT).startsWith(charset.toLowerCase(Locale.ROOT) + "_");
    }
    static IllegalStateException blocked(String object, String reason) {
        return new IllegalStateException(object + ": " + reason + "；请在维护窗口备份并执行适用的显式迁移，再重启");
    }
    private void execute(String sql) throws SQLException { try (var s = connection.createStatement()) { s.execute(sql); } }
    private static long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }
}
