package com.hengshucredit.rule.server.testdata;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 同一份目录用于控制台选项、内置规则初始化及服务端方法白名单。 */
public final class FakerMethods {
    record Method(String name, String label, String type, List<String> parameters, com.fasterxml.jackson.databind.JsonNode exampleArgs, List<String> resultTypes) {}
    record Group(String module, String label, List<Method> methods) {}
    record Entry(String key, String label, String type, String category, String module, List<String> parameters, List<String> resultTypes) {}
    static String methodKey(com.fasterxml.jackson.databind.JsonNode config) {
        return config.isTextual() ? config.textValue() : config.path("method").asText("");
    }
    static final Map<String, Entry> METHODS = load();

    private FakerMethods() {}

    private static Map<String, Entry> load() {
        try (InputStream in = FakerMethods.class.getResourceAsStream("/faker-methods.json")) {
            List<Group> groups = MockRule.JSON.readValue(in, new TypeReference<>() {});
            var entries = new LinkedHashMap<String, Entry>();
            for (var group : groups) {
                for (var method : group.methods()) {
                    String key = group.module() + "." + method.name();
                    var entry = new Entry(key, method.label(), method.type(),
                            group.label(), group.module(), method.parameters() == null ? List.of() : method.parameters(),
                            method.resultTypes() == null ? List.of(method.type()) : method.resultTypes());
                    if (key.length() > 32 || entries.put(key, entry) != null) {
                        throw new IllegalStateException("无效的 Faker 内置规则标识：" + key);
                    }
                }
            }
            return java.util.Collections.unmodifiableMap(entries);
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取 Faker 方法目录", ex);
        }
    }

    public static void install(Connection connection) throws SQLException {
        var existing = new HashMap<String, String>();
        try (var query = connection.prepareStatement("SELECT seed_key, name FROM rule_mock_rule WHERE seed_key IS NOT NULL");
             var rows = query.executeQuery()) {
            while (rows.next()) existing.put(rows.getString(1), rows.getString(2));
        }
        try (var insert = connection.prepareStatement("""
                INSERT INTO rule_mock_rule (seed_key, name, value_type, match_names, generator, config_json)
                VALUES (?, ?, ?, '', 'FAKER', ?)
                ON DUPLICATE KEY UPDATE seed_key=rule_mock_rule.seed_key
                """);
             var rename = connection.prepareStatement("""
                UPDATE rule_mock_rule SET name=?
                WHERE seed_key=? AND CAST(name AS BINARY(512))=CAST(? AS BINARY(512))
                """)) {
            for (Entry method : METHODS.values()) {
                if (existing.containsKey(method.key())) {
                    // 只改旧版本生成的完整默认名。已有规则 ID、用户命名和绑定均保留。
                    String oldName = method.category() + " · " + method.label();
                    if (oldName.equals(existing.get(method.key()))) {
                        rename.setString(1, method.label());
                        rename.setString(2, method.key());
                        rename.setString(3, oldName);
                        rename.addBatch();
                    }
                    continue;
                }
                insert.setString(1, method.key());
                insert.setString(2, method.label());
                insert.setString(3, method.type());
                insert.setString(4, MockRule.JSON.getNodeFactory().textNode(method.key()).toString());
                insert.addBatch();
            }
            rename.executeBatch();
            insert.executeBatch();
        }
    }
}
