package com.hengshucredit.rule.server.consolelogin;

import com.hengshucredit.rule.core.util.LocalEnvironmentProperties;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** 仅从服务器命令行签发恢复码，不启动 HTTP 服务，不改动业务表。 */
public final class ConsoleAdministratorRecovery {
    public static final String COMMAND = "--console-admin-recovery-code";
    private ConsoleAdministratorRecovery() { }

    public static int run() {
        Map<String, Object> settings = new LinkedHashMap<>(LocalEnvironmentProperties.load(Path.of(""), ".env.server"));
        settings.putAll(System.getenv());
        System.getProperties().forEach((key, value) -> settings.put(String.valueOf(key), value));
        String url = setting(settings, "SPRING_DATASOURCE_URL", "jdbc:mysql://"
                + setting(settings, "MYSQL_HOST", "localhost") + ":" + setting(settings, "MYSQL_PORT", "3306")
                + "/" + setting(settings, "MYSQL_DATABASE", "rule_engine")
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai");
        try (Connection connection = DriverManager.getConnection(url,
                setting(settings, "SPRING_DATASOURCE_USERNAME", setting(settings, "MYSQL_USERNAME", "")),
                setting(settings, "SPRING_DATASOURCE_PASSWORD", setting(settings, "MYSQL_PASSWORD", "")))) {
            String code = issue(connection);
            System.out.println("一次性超管恢复码（30分钟有效，再次签发会撤销旧码）：");
            System.out.println(code);
            System.out.println("在控制台“管理员应急恢复”页面输入此码、新用户名和新密码；无需原用户名。业务内容不会被清除。");
            return 0;
        } catch (IllegalStateException error) {
            System.err.println(error.getMessage());
        } catch (SQLException error) {
            // 数据库异常可能包含连接凭据；仅输出错误码供运维定位。
            System.err.println("无法签发恢复码，请确认 .env.server 的数据库连接及系统初始化状态。SQLState="
                    + error.getSQLState() + ", code=" + error.getErrorCode());
        }
        return 1;
    }

    public static String issue(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            Long preferredId;
            try (var statement = connection.prepareStatement("SELECT initialized_at, administrator_user_id FROM console_security_lock WHERE id=1 FOR UPDATE");
                 var row = statement.executeQuery()) {
                if (!row.next() || row.getTimestamp(1) == null) throw new IllegalStateException("系统尚未初始化，请先启动服务并通过注册页面创建超管。");
                long value = row.getLong(2); preferredId = row.wasNull() ? null : value;
            }
            var owner = ConsoleAdministratorMerge.mergeLocked(connection, preferredId);
            if (owner == null) throw new IllegalStateException("未找到可恢复的超级管理员。请核对数据库中的账户和角色，勿删除业务数据或重新初始化。");
            Long userId = owner.id();
            String username = owner.username();
            try (var statement = connection.prepareStatement("UPDATE console_account_token SET consumed_at=CURRENT_TIMESTAMP WHERE purpose=? AND consumed_at IS NULL")) {
                statement.setString(1, ConsoleAccountCredentialService.ADMIN_RECOVERY); statement.executeUpdate();
            }
            byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
            String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            try (var statement = connection.prepareStatement("INSERT INTO console_account_token (user_id,purpose,token_hash,expires_at,request_ip) VALUES (?,?,?,?,?)")) {
                statement.setLong(1, userId); statement.setString(2, ConsoleAccountCredentialService.ADMIN_RECOVERY);
                statement.setString(3, Sha256Digests.text(code)); statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now().plusMinutes(30)));
                statement.setString(5, "SERVER_CLI"); statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("INSERT INTO console_security_audit_log (user_id,username,action,target_type,target_id,client_ip) VALUES (?,?,'ADMIN_RECOVERY_CODE_ISSUED','CONSOLE_USER',?,'SERVER_CLI')")) {
                statement.setLong(1, userId); statement.setString(2, username); statement.setString(3, String.valueOf(userId)); statement.executeUpdate();
            }
            connection.commit();
            return code;
        } catch (SQLException | RuntimeException error) {
            connection.rollback(); throw error;
        } finally { connection.setAutoCommit(autoCommit); }
    }

    private static String setting(Map<String, Object> values, String name, String fallback) {
        return values.containsKey(name) ? String.valueOf(values.get(name)) : fallback;
    }
}
