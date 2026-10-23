package com.hengshucredit.rule.server.security;

import com.hengshucredit.rule.model.dto.ConsoleUserSaveRequest;
import com.hengshucredit.rule.model.dto.ConsoleUserPermissionRequest;
import org.junit.Test;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public class ConsoleSingleAdministratorTest {
    @Test public void accountCreationCannotAssignSecondSuperAdministrator() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            f.initialize();
            var request = new ConsoleUserSaveRequest(); request.setUsername("second_owner"); request.setPassword("SecondOwner123!");
            request.setRoleIds(List.of(f.accounts.listRoles().get(0).getId()));
            assertThrows(IllegalArgumentException.class, () -> f.accounts.saveUser(request, "admin"));
            assertEquals(Integer.valueOf(1), f.jdbc.queryForObject("SELECT COUNT(*) FROM console_user", Integer.class));
        }
    }

    @Test public void existingAccountCannotGainOrReplaceTheSystemAdministratorRole() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            var owner = f.initialize(); var other = f.user("ordinary");
            var request = new ConsoleUserPermissionRequest(); request.setRoleIds(List.of(f.accounts.listRoles().get(0).getId()));
            assertThrows(IllegalArgumentException.class, () -> f.accounts.saveUserPermissions(other.getId(), request, "admin"));
            request.setRoleIds(List.of());
            assertThrows(IllegalArgumentException.class, () -> f.accounts.saveUserPermissions(owner.userId(), request, "admin"));
        }
    }

    @Test public void recoveryConsolidatesLegacySuperAdministratorsWithoutCreatingAccounts() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            var owner = f.initialize(); var legacy = f.user("new_owner");
            f.jdbc.update("INSERT INTO console_user_role(user_id,role_id) VALUES (?,?)", legacy.getId(), f.accounts.listRoles().get(0).getId());
            String code = f.recoveryCode();
            assertFalse("legacy extra administrator still logs in", f.login.authenticate("new_owner", "OldPass123!").authenticated());
            f.credential.emergencyReset("new_owner", code, "MergedOwner123!", "127.0.0.1");
            assertEquals(owner.userId(), f.login.authenticate("new_owner", "MergedOwner123!").userId());
            assertEquals(1, f.accounts.listUsers().size());
        }
    }

    @Test public void consolidationIsIdempotentAndKeepsBothOwnershipNamesAndBusinessContent() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            var owner = f.initialize(); var legacy = f.user("new_owner"); var ordinary = f.user("ordinary");
            f.jdbc.update("INSERT INTO console_user_role(user_id,role_id) VALUES (?,?)", legacy.getId(), f.accounts.listRoles().get(0).getId());
            f.jdbc.update("INSERT INTO rule_project(project_code,project_name) VALUES ('KEPT','保留项目')");
            var content = f.jdbc.queryForList("SELECT * FROM rule_project");
            var ordinaryBefore = f.jdbc.queryForMap("SELECT * FROM console_user WHERE id=?", ordinary.getId());
            try (var connection = f.jdbc.getDataSource().getConnection()) {
                com.hengshucredit.rule.server.consolelogin.ConsoleAdministratorMerge.consolidate(connection);
                Long version = f.jdbc.queryForObject("SELECT permission_version FROM console_user WHERE id=?", Long.class, owner.userId());
                com.hengshucredit.rule.server.consolelogin.ConsoleAdministratorMerge.consolidate(connection);
                assertEquals(version, f.jdbc.queryForObject("SELECT permission_version FROM console_user WHERE id=?", Long.class, owner.userId()));
            }
            var merged = f.sessions.getMapper(com.hengshucredit.rule.server.mapper.ConsoleUserMapper.class).selectById(legacy.getId());
            assertEquals(owner.userId(), merged.getMergedIntoUserId());
            assertEquals("new_owner", merged.getMergedUsername());
            assertEquals("new_owner", merged.getOperatorName());
            assertEquals(Integer.valueOf(0), merged.getStatus());
            assertEquals(Set.of("admin", "new_owner"), f.login.authenticate("admin", "AdminPass123!").operatorNames());
            assertEquals(2, f.accounts.listUsers().size());
            assertEquals(Integer.valueOf(1), f.jdbc.queryForObject("SELECT COUNT(*) FROM console_user_role ur JOIN console_role r ON r.id=ur.role_id WHERE r.role_code='SUPER_ADMIN'", Integer.class));
            assertEquals(content, f.jdbc.queryForList("SELECT * FROM rule_project"));
            assertEquals(ordinaryBefore, f.jdbc.queryForMap("SELECT * FROM console_user WHERE id=?", ordinary.getId()));

            var resolver = new com.hengshucredit.rule.server.service.ConsoleOperatorResolver();
            org.springframework.test.util.ReflectionTestUtils.setField(resolver, "userMapper", f.sessions.getMapper(com.hengshucredit.rule.server.mapper.ConsoleUserMapper.class));
            var approvals = new com.hengshucredit.rule.server.governance.GovernanceApprovalService();
            org.springframework.test.util.ReflectionTestUtils.setField(approvals, "operatorResolver", resolver);
            assertTrue(approvals.ownsRequest("admin", "new_owner"));
            assertFalse(approvals.ownsRequest("admin", "ordinary"));
        }
    }

    @Test public void mergedAccountCannotBeReenabledOrItsNamesClaimedByAnOrdinaryAccount() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            f.initialize(); var legacy = f.user("new_owner");
            f.jdbc.update("UPDATE console_user SET operator_name='original_owner' WHERE id=?", legacy.getId());
            f.jdbc.update("INSERT INTO console_user_role(user_id,role_id) VALUES (?,?)", legacy.getId(), f.accounts.listRoles().get(0).getId());
            f.recoveryCode();
            assertThrows(IllegalArgumentException.class, () -> f.accounts.changeUserStatus(legacy.getId(), 1, "admin"));
            assertThrows(IllegalArgumentException.class, () -> f.accounts.saveUserPermissions(legacy.getId(), new ConsoleUserPermissionRequest(), "admin"));
            for (String name : List.of("new_owner", "original_owner")) {
                var request = new ConsoleUserSaveRequest(); request.setUsername(name); request.setPassword("OrdinaryPass123!");
                assertThrows(IllegalArgumentException.class, () -> f.accounts.saveUser(request, "admin"));
            }
        }
    }

    @Test public void consolidationFailureRollsBackAccountChanges() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            f.initialize(); var legacy = f.user("new_owner");
            f.jdbc.update("INSERT INTO console_user_role(user_id,role_id) VALUES (?,?)", legacy.getId(), f.accounts.listRoles().get(0).getId());
            f.jdbc.execute("DROP TABLE console_security_audit_log");
            var users = f.jdbc.queryForList("SELECT * FROM console_user ORDER BY id");
            var roles = f.jdbc.queryForList("SELECT * FROM console_user_role ORDER BY id");
            try (var connection = f.jdbc.getDataSource().getConnection()) {
                assertThrows(java.sql.SQLException.class, () -> com.hengshucredit.rule.server.consolelogin.ConsoleAdministratorMerge.consolidate(connection));
            }
            assertEquals(users, f.jdbc.queryForList("SELECT * FROM console_user ORDER BY id"));
            assertEquals(roles, f.jdbc.queryForList("SELECT * FROM console_user_role ORDER BY id"));
        }
    }
}
