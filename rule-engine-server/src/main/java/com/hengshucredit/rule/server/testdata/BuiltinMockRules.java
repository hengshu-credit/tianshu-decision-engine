package com.hengshucredit.rule.server.testdata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** 内置基础生成规则。旧目录标识原地升级，字段绑定继续使用原 ID。 */
public final class BuiltinMockRules {
    private record Seed(String key, String name, String type, String generator, String configJson,
                        String originalMatchNames) {}

    private static final List<Seed> SEEDS = List.of(
            new Seed("text", "示例文本", "STRING", "TEXT", "null", "*"),
            new Seed("number", "常用数值", "NUMBER", "INTEGER", "[0,100]", "*"),
            new Seed("bool", "随机布尔值", "BOOLEAN", "BOOLEAN", "null", "*"),
            new Seed("date", "近期日期", "DATE", "DATE", "null", "*"),
            new Seed("list", "示例列表", "LIST", "FIXED", "[]", "*"),
            new Seed("object", "空对象", "OBJECT", "FIXED", "{}", ""),
            new Seed("mobile", "手机号码", "STRING", "PHONE", "null", "mobile,phone,phoneNumber,手机号,手机号码"),
            new Seed("email", "电子邮箱", "STRING", "EMAIL", "null", "email,emailAddress,电子邮箱"),
            new Seed("request", "请求流水号", "STRING", "UUID", "null", "requestId,traceId,请求流水号"),
            new Seed("age", "年龄", "NUMBER", "INTEGER", "[18,75]", "age,年龄"),
            new Seed("score", "信用评分", "NUMBER", "INTEGER", "[300,850]", "creditScore,信用评分"),
            new Seed("amount", "申请金额", "NUMBER", "INTEGER", "[1000,100000]", "amount,loanAmount,申请金额"),
            new Seed("decimal", "两位小数", "NUMBER", "DECIMAL", "[0,1000,2]", null),
            new Seed("choice", "随机候选文本", "STRING", "CHOICE", "[\"A\",\"B\",\"C\"]", null),
            new Seed("template", "带日期的流水号", "STRING", "TEMPLATE", "\"REQ-{date}-{digits}\"", null),
            new Seed("datetime", "日期时间", "DATETIME", "DATETIME", "null", null),
            new Seed("timestamp", "毫秒时间戳", "TIMESTAMP", "TIMESTAMP", "null", null));

    private BuiltinMockRules() {}

    public static void install(Connection connection) throws SQLException {
        try (var migrate = connection.prepareStatement("""
                UPDATE rule_mock_rule SET match_names=IF(BINARY match_names=BINARY ?, '', match_names),
                       seed_key=CONCAT('v2.', seed_key) WHERE seed_key=?
                """);
             var insert = connection.prepareStatement("""
                INSERT INTO rule_mock_rule (seed_key, name, value_type, match_names, generator, config_json)
                VALUES (?, ?, ?, '', ?, ?)
                ON DUPLICATE KEY UPDATE seed_key=rule_mock_rule.seed_key
                """)) {
            for (Seed seed : SEEDS) {
                String key = seed.key();
                if (seed.originalMatchNames() != null) {
                    migrate.setString(1, seed.originalMatchNames());
                    migrate.setString(2, key);
                    migrate.addBatch();
                    key = "v2." + key;
                }
                insert.setString(1, key);
                insert.setString(2, seed.name());
                insert.setString(3, seed.type());
                insert.setString(4, seed.generator());
                insert.setString(5, seed.configJson());
                insert.addBatch();
            }
            migrate.executeBatch();
            insert.executeBatch();
        }
    }
}
