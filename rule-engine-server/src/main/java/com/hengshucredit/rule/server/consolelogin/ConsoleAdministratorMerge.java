package com.hengshucredit.rule.server.consolelogin;

import com.alibaba.fastjson.JSON;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 历史重复超管合并为一个身份，保留历史账户ID及业务归属名，不删除业务内容。 */
public final class ConsoleAdministratorMerge {
    private ConsoleAdministratorMerge() { }

    public static void consolidate(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            Long preferredId;
            try (var statement = connection.prepareStatement("SELECT administrator_user_id FROM console_security_lock WHERE id=1 FOR UPDATE");
                 var row = statement.executeQuery()) {
                if (!row.next()) throw new IllegalStateException("账户安全锁未初始化");
                long id = row.getLong(1); preferredId = row.wasNull() ? null : id;
            }
            mergeLocked(connection, preferredId);
            connection.commit();
        } catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
        finally { connection.setAutoCommit(autoCommit); }
    }

    /** 调用方必须持有 console_security_lock，提交与回滚由调用方负责。 */
    static Owner mergeLocked(Connection connection, Long preferredId) throws SQLException {
        List<Owner> candidates = new ArrayList<>();
        try (var statement = connection.prepareStatement("""
                SELECT DISTINCT u.id,u.username,u.operator_name,u.status,u.registration_status
                FROM console_user u JOIN console_user_role ur ON ur.user_id=u.id
                JOIN console_role r ON r.id=ur.role_id
                WHERE r.role_code='SUPER_ADMIN' AND u.merged_into_user_id IS NULL ORDER BY u.id
                """); var rows = statement.executeQuery()) {
            while (rows.next()) candidates.add(new Owner(rows.getLong(1), rows.getString(2), rows.getString(3),
                    rows.getInt(4) == 1 && (rows.getString(5) == null || "ACTIVE".equals(rows.getString(5)))));
        }
        if (candidates.isEmpty()) return null;
        Owner owner = candidates.stream().filter(value -> Objects.equals(value.id(), preferredId) && value.active()).findFirst()
                .orElseGet(() -> candidates.stream().filter(Owner::active).findFirst()
                        .orElseGet(() -> candidates.stream().filter(value -> Objects.equals(value.id(), preferredId)).findFirst().orElse(candidates.get(0))));
        try (var statement = connection.prepareStatement("UPDATE console_security_lock SET administrator_user_id=? WHERE id=1")) {
            statement.setLong(1, owner.id()); statement.executeUpdate();
        }
        boolean merged = false;
        for (Owner duplicate : candidates) {
            if (duplicate.id().equals(owner.id())) continue;
            merged = true;
            String archiveName = "__merged_" + duplicate.id() + "_" + UUID.randomUUID().toString().replace("-", "");
            try (var statement = connection.prepareStatement("""
                    UPDATE console_user SET operator_name=?,username=?,merged_into_user_id=?,merged_username=?,status=0,
                    registration_status='MERGED',permission_version=COALESCE(permission_version,0)+1,
                    update_by='SYSTEM_ADMIN_MERGE',update_time=CURRENT_TIMESTAMP WHERE id=?
                    """)) {
                statement.setString(1, duplicate.operatorName() == null ? duplicate.username() : duplicate.operatorName());
                statement.setString(2, archiveName); statement.setLong(3, owner.id()); statement.setString(4, duplicate.username()); statement.setLong(5, duplicate.id()); statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("UPDATE console_user SET merged_into_user_id=? WHERE merged_into_user_id=?")) {
                statement.setLong(1, owner.id()); statement.setLong(2, duplicate.id()); statement.executeUpdate();
            }
            revokeTokens(connection, duplicate.id());
            try (var statement = connection.prepareStatement("INSERT INTO console_security_audit_log (user_id,username,action,target_type,target_id,details_json) VALUES (?,?,'ADMIN_ACCOUNT_MERGED','CONSOLE_USER',?,?)")) {
                statement.setLong(1, duplicate.id()); statement.setString(2, duplicate.username()); statement.setString(3, String.valueOf(owner.id()));
                statement.setString(4, JSON.toJSONString(Map.of("canonicalUserId", owner.id(), "mergedUserId", duplicate.id(), "previousUsername", duplicate.username())));
                statement.executeUpdate();
            }
        }
        try (var statement = connection.prepareStatement("DELETE FROM console_user_role WHERE role_id IN (SELECT id FROM console_role WHERE role_code='SUPER_ADMIN') AND user_id<>?")) {
            statement.setLong(1, owner.id()); statement.executeUpdate();
        }
        if (merged) {
            try (var statement = connection.prepareStatement("UPDATE console_user SET permission_version=COALESCE(permission_version,0)+1 WHERE id=?")) {
                statement.setLong(1, owner.id()); statement.executeUpdate();
            }
            revokeTokens(connection, owner.id());
        }
        return owner;
    }

    private static void revokeTokens(Connection connection, Long userId) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE console_account_token SET consumed_at=CURRENT_TIMESTAMP WHERE user_id=? AND consumed_at IS NULL")) {
            statement.setLong(1, userId); statement.executeUpdate();
        }
    }

    record Owner(Long id, String username, String operatorName, boolean active) { }
}
