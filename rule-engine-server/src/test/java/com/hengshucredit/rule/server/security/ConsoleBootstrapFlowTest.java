package com.hengshucredit.rule.server.security;

import com.hengshucredit.rule.server.common.PlatformResponseAdvice;
import org.junit.Test;
import com.hengshucredit.rule.server.common.GlobalExceptionHandler;
import com.hengshucredit.rule.server.consolelogin.ConsoleAdministratorRecovery;
import com.hengshucredit.rule.server.consolelogin.DatabaseConsoleAccountService;
import com.hengshucredit.rule.server.mapper.ConsoleUserMapper;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.concurrent.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

public class ConsoleBootstrapFlowTest {
    @Test public void firstAdministratorRegistersWithoutMailOrEnvironmentCredentials() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            f.props.setRegistrationEnabled(false);
            var mvc = MockMvcBuilders.standaloneSetup(f.controller()).setControllerAdvice(new PlatformResponseAdvice()).build();
            var response = mvc.perform(post("/api/auth/console/initialize").contentType("application/json")
                    .content("{\"username\":\"owner\",\"displayName\":\"系统管理员\",\"password\":\"InitialOwner123!\"}"))
                    .andReturn().getResponse();
            assertEquals(response.getContentAsString(), 200, response.getStatus());
            var login = f.login.authenticate("owner", "InitialOwner123!");
            assertTrue(login.authenticated());
            assertTrue(login.roleCodes().contains("SUPER_ADMIN"));
            assertTrue(login.permissions().contains("account:manage"));
            assertFalse(f.login.initializationRequired());
            assertFalse(f.login.authenticate("admin", "AdminPass123!").authenticated());
        }
    }

    @Test public void persistentInitializationCannotBeRepeatedAfterRestartOrUserDeletion() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            var owner = f.initialize();
            var restarted = new DatabaseConsoleAccountService();
            for (var field : DatabaseConsoleAccountService.class.getDeclaredFields()) {
                if (field.getType().getPackageName().endsWith(".mapper")) ReflectionTestUtils.setField(restarted, field.getName(), f.sessions.getMapper(field.getType()));
            }
            ReflectionTestUtils.setField(restarted, "permissionService", f.permissions);
            assertFalse(restarted.initializationRequired());
            assertEquals(owner.userId(), restarted.authenticate("admin", "AdminPass123!").userId());
            assertThrows(com.hengshucredit.rule.model.PlatformException.class,
                    () -> f.login.initializeAdministrator("other", "Other", "AnotherPass123!"));
            f.jdbc.update("DELETE FROM console_user");
            assertFalse("deleting accounts must not reopen anonymous bootstrap", restarted.initializationRequired());
        }
    }

    @Test public void concurrentFirstRegistrationsCreateExactlyOneAdministrator() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            var pool = Executors.newFixedThreadPool(2);
            var start = new CountDownLatch(1);
            try {
                var futures = new java.util.ArrayList<Future<Boolean>>();
                for (String name : List.of("ownerA", "ownerB")) futures.add(pool.submit(() -> {
                    start.await();
                    try { f.login.initializeAdministrator(name, name, "InitialOwner123!"); return true; }
                    catch (com.hengshucredit.rule.model.PlatformException expected) { return false; }
                }));
                start.countDown();
                int successes = 0;
                for (var result : futures) if (result.get(20, TimeUnit.SECONDS)) successes++;
                assertEquals(1, successes);
                assertEquals(Integer.valueOf(1), f.jdbc.queryForObject("SELECT COUNT(*) FROM console_user", Integer.class));
                assertEquals(Integer.valueOf(1), f.jdbc.queryForObject("SELECT COUNT(*) FROM console_user_role", Integer.class));
            } finally { pool.shutdownNow(); }
        }
    }

    @Test public void recoveryChangesBothCredentialsOnceAndKeepsUserIdRolesAndBusinessContent() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            var owner = f.initialize();
            var user = f.sessions.getMapper(ConsoleUserMapper.class).selectById(owner.userId());
            var oldSession = f.session(user);
            f.jdbc.update("INSERT INTO rule_project (project_code,project_name,access_token,create_by) VALUES ('KEPT','保留项目','kept-token','admin')");
            var content = f.jdbc.queryForList("SELECT * FROM rule_project");
            var roles = f.jdbc.queryForList("SELECT * FROM console_user_role");
            String code = f.recoveryCode();
            assertFalse(f.jdbc.queryForObject("SELECT token_hash FROM console_account_token WHERE purpose='ADMIN_RECOVERY'", String.class).contains(code));
            f.credential.emergencyReset("recovered_owner", code, "RecoveredPass123!", "127.0.0.1");
            var recovered = f.login.authenticate("recovered_owner", "RecoveredPass123!");
            assertTrue(recovered.authenticated());
            assertEquals(owner.userId(), recovered.userId());
            assertEquals(owner.permissions(), recovered.permissions());
            assertEquals(roles, f.jdbc.queryForList("SELECT * FROM console_user_role"));
            assertEquals(content, f.jdbc.queryForList("SELECT * FROM rule_project"));
            assertFalse(f.login.authenticate("admin", "AdminPass123!").authenticated());
            assertThrows(IllegalArgumentException.class, () -> f.credential.emergencyReset("hijacker", code, "AnotherPass123!", "127.0.0.1"));
            var mvc = MockMvcBuilders.standaloneSetup(f.controller()).setControllerAdvice(new PlatformResponseAdvice(), new GlobalExceptionHandler()).build();
            assertEquals(401, mvc.perform(get("/api/auth/console/profile").session(oldSession)).andReturn().getResponse().getStatus());
        }
    }

    @Test public void expiredReissuedAndConflictingRecoveryRequestsDoNotOverwriteAccounts() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            f.initialize(); f.user("occupied");
            String old = f.recoveryCode(), current = f.recoveryCode();
            assertThrows(IllegalArgumentException.class, () -> f.credential.emergencyReset("owner2", old, "RecoveredPass123!", "127.0.0.1"));
            assertThrows(IllegalArgumentException.class, () -> f.credential.emergencyReset("occupied", current, "RecoveredPass123!", "127.0.0.1"));
            assertTrue(f.login.authenticate("admin", "AdminPass123!").authenticated());
            assertTrue(f.login.authenticate("occupied", "OldPass123!").authenticated());
            // 冲突失败不会消费恢复码；改用未占用名称仍可继续恢复。
            f.credential.emergencyReset("owner2", current, "RecoveredPass123!", "127.0.0.1");
            String expired = f.recoveryCode();
            f.jdbc.update("UPDATE console_account_token SET expires_at=TIMESTAMP '2000-01-01 00:00:00' WHERE purpose='ADMIN_RECOVERY'");
            assertThrows(IllegalArgumentException.class, () -> f.credential.emergencyReset("owner3", expired, "RecoveredPass123!", "127.0.0.1"));
        }
    }

    @Test public void recoveryCannotCreateAnAdministratorBeforeInitialization() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture(); var connection = f.jdbc.getDataSource().getConnection()) {
            assertThrows(IllegalStateException.class, () -> ConsoleAdministratorRecovery.issue(connection));
            assertTrue(f.login.initializationRequired());
        }
    }

    @Test public void renamedAdministratorKeepsExistingDraftAndTransferOwnershipIdentity() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            f.initialize();
            f.credential.emergencyReset("new_owner", f.recoveryCode(), "RecoveredPass123!", "127.0.0.1");
            var mvc = MockMvcBuilders.standaloneSetup(f.controller()).setControllerAdvice(new PlatformResponseAdvice(), new GlobalExceptionHandler()).build();
            var logged = mvc.perform(post("/api/auth/console/login").contentType("application/json")
                    .content("{\"username\":\"new_owner\",\"password\":\"RecoveredPass123!\"}")).andReturn();
            assertEquals(200, logged.getResponse().getStatus());
            var resolver = new com.hengshucredit.rule.server.service.ConsoleOperatorResolver();
            ReflectionTestUtils.setField(resolver, "consoleLoginProperties", f.props);
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                    new org.springframework.web.context.request.ServletRequestAttributes(logged.getRequest()));
            try { assertEquals("admin", resolver.resolve()); }
            finally { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }
            var reusedName = new com.hengshucredit.rule.model.dto.ConsoleUserSaveRequest();
            reusedName.setUsername("admin"); reusedName.setPassword("OtherOwner123!");
            assertThrows(IllegalArgumentException.class, () -> f.accounts.saveUser(reusedName, "new_owner"));
            assertThrows(IllegalArgumentException.class, () -> f.credential.register("admin", "Other", "other@example.test", "OtherOwner123!", "https://console.example.test", "127.0.0.1"));
        }
    }
}
