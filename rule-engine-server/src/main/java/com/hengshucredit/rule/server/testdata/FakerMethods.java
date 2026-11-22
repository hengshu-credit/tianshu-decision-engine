package com.hengshucredit.rule.server.testdata;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 同一份目录用于控制台选项、内置规则初始化及服务端方法白名单。 */
public final class FakerMethods {
    record Method(String name, String label, String type) {}
    record Group(String module, String label, List<Method> methods) {}
    record Entry(String key, String label, String type) {}
    static final Map<String, Entry> METHODS = load();

    private FakerMethods() {}

    private static Map<String, Entry> load() {
        try (InputStream in = FakerMethods.class.getResourceAsStream("/faker-methods.json")) {
            List<Group> groups = MockRule.JSON.readValue(in, new TypeReference<>() {});
            var entries = new LinkedHashMap<String, Entry>();
            for (var group : groups) {
                for (var method : group.methods()) {
                    String key = group.module() + "." + method.name();
                    var entry = new Entry(key, group.label() + " · " + method.label(), method.type());
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
        var existing = new HashSet<String>();
        try (var query = connection.prepareStatement("SELECT seed_key FROM rule_mock_rule WHERE seed_key IS NOT NULL");
             var rows = query.executeQuery()) {
            while (rows.next()) existing.add(rows.getString(1));
        }
        try (var insert = connection.prepareStatement("""
                INSERT INTO rule_mock_rule (seed_key, name, value_type, match_names, generator, config_json)
                VALUES (?, ?, ?, '', 'FAKER', ?)
                ON DUPLICATE KEY UPDATE seed_key=rule_mock_rule.seed_key
                """)) {
            for (Entry method : METHODS.values()) {
                if (existing.contains(method.key())) continue;
                insert.setString(1, method.key());
                insert.setString(2, method.label());
                insert.setString(3, method.type());
                insert.setString(4, MockRule.JSON.getNodeFactory().textNode(method.key()).toString());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
