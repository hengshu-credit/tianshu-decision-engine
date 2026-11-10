package com.hengshucredit.rule.server.security;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.dto.ConsoleUserPermissionRequest;
import com.hengshucredit.rule.model.entity.ConsoleUser;
import com.hengshucredit.rule.server.common.GlobalExceptionHandler;
import com.hengshucredit.rule.server.controller.mgmt.ConsoleAccountController;
import com.hengshucredit.rule.server.mapper.ConsoleUserMapper;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用真实权限合并、数据库和 HTTP 入口，验证关闭单项权限后不能借账户管理绕过。 */
public class ConsoleAccountActionPermissionTest {
    @Test
    public void personalPasswordChangeRequiresItsOwnPermissionAndPreservesPasswordWhenDenied() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            f.initialize();
            ConsoleUser demo = f.user("demo");
            // 管理账户、重置他人密码均不能隐式授予修改本人密码。
            grant(f, demo, "account:manage", "account:password-reset");
            var mvc = MockMvcBuilders.standaloneSetup(f.controller()).setControllerAdvice(new GlobalExceptionHandler()).build();
            demo = f.sessions.getMapper(ConsoleUserMapper.class).selectById(demo.getId());
            String originalHash = demo.getPasswordHash();
            mvc.perform(post("/api/auth/console/password/change").session(f.session(demo)).contentType("application/json")
                    .content("{\"currentPassword\":\"OldPass123!\",\"password\":\"ChangedPass123!\"}"))
                    .andExpect(status().isForbidden());
            assertEquals(originalHash, f.sessions.getMapper(ConsoleUserMapper.class).selectById(demo.getId()).getPasswordHash());
            grant(f, demo, "account:password-change");
            demo = f.sessions.getMapper(ConsoleUserMapper.class).selectById(demo.getId());
            var session = f.session(demo);
            mvc.perform(post("/api/auth/console/password/change").session(session).contentType("application/json")
                    .content("{\"currentPassword\":\"OldPass123!\",\"password\":\"ChangedPass123!\"}"))
                    .andExpect(status().isOk());
            assertTrue(session.isInvalid());
            assertTrue(cn.hutool.crypto.digest.BCrypt.checkpw("ChangedPass123!",
                    f.sessions.getMapper(ConsoleUserMapper.class).selectById(demo.getId()).getPasswordHash()));
            assertFalse(f.permissions.effectivePermissions(demo.getId()).contains("account:password-reset"));
        }
    }

    @Test
    public void recoveryCannotBypassRevocationIncludingAlreadyIssuedLinks() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            f.initialize();
            ConsoleUser demo = f.user("demo");
            f.credential.requestPasswordReset("demo", "https://console.example.test", "127.0.0.1");
            assertNull(f.mail.link);
            grant(f, demo, "account:password-change");
            f.credential.requestPasswordReset("demo", "https://console.example.test", "127.0.0.1");
            String token = f.mail.token();
            var revoked = new ConsoleUserPermissionRequest();
            revoked.setPermissionOverrides(Map.of("account:password-change", "DENY"));
            f.accounts.saveUserPermissions(demo.getId(), revoked, "admin");
            assertThrows(ConsoleAccessDeniedException.class, () -> f.credential.resetPassword(token, "BypassPass123!", "127.0.0.1"));
            assertEquals(demo.getPasswordHash(), f.sessions.getMapper(ConsoleUserMapper.class).selectById(demo.getId()).getPasswordHash());
            f.mail.link = null;
            f.credential.requestPasswordReset("demo", "https://console.example.test", "127.0.0.1");
            assertNull(f.mail.link);
        }
    }

    @Test
    public void accountManagementCannotReplaceDedicatedActionPermissions() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            f.initialize();
            ConsoleUser manager = f.user("manager");
            ConsoleUser target = f.user("target");
            grant(f, manager, "account:manage");
            MockMvc mvc = mvc(f);
            String hash = target.getPasswordHash();
            mvc.perform(put("/api/rule/console/accounts/{id}/password", target.getId()).session(f.session(manager))
                    .contentType("application/json").content("{\"password\":\"ChangedPass123!\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/rule/console/accounts/{id}/permissions", target.getId()).session(f.session(manager))
                    .contentType("application/json").content("{\"permissionOverrides\":{\"role:manage\":\"ALLOW\"}}"))
                    .andExpect(status().isForbidden());
            assertEquals(hash, f.sessions.getMapper(ConsoleUserMapper.class).selectById(target.getId()).getPasswordHash());
            assertTrue(f.permissions.effectivePermissions(target.getId()).isEmpty());
        }
    }

    @Test
    public void ordinarySaveRejectsEveryAuthorizationFieldAndExistingPasswordBeforeWriting() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            f.initialize();
            ConsoleUser manager = f.user("manager");
            ConsoleUser target = f.user("target");
            grant(f, manager, "account:manage");
            MockMvc mvc = mvc(f);
            Map<String, Object> fields = Map.of(
                    "roleIds", List.of(), "permissionOverrides", Map.of("role:manage", "ALLOW"),
                    "projectIds", List.of(), "projectAccessMode", "ALL", "globalEditable", true,
                    "projectPermissionOverrides", Map.of(), "password", "ChangedPass123!");
            for (var field : fields.entrySet()) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("id", target.getId()); body.put("username", target.getUsername());
                body.put("displayName", "must-not-change"); body.put(field.getKey(), field.getValue());
                mvc.perform(post("/api/rule/console/accounts").session(f.session(manager))
                        .contentType("application/json").content(JSON.toJSONString(body)))
                        .andExpect(status().isForbidden());
                ConsoleUser stored = f.sessions.getMapper(ConsoleUserMapper.class).selectById(target.getId());
                assertEquals(field.getKey(), target.getDisplayName(), stored.getDisplayName());
                assertEquals(field.getKey(), target.getPasswordHash(), stored.getPasswordHash());
                assertEquals(field.getKey(), target.getPermissionVersion(), stored.getPermissionVersion());
            }
            mvc.perform(post("/api/rule/console/accounts").session(f.session(manager)).contentType("application/json")
                    .content(JSON.toJSONString(Map.of("id", target.getId(), "username", "target", "displayName", "Updated"))))
                    .andExpect(status().isOk());
            assertEquals("Updated", f.sessions.getMapper(ConsoleUserMapper.class).selectById(target.getId()).getDisplayName());
        }
    }

    @Test
    public void accountCreationAllowsInitialPasswordButCannotSmuggleGrants() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            f.initialize();
            ConsoleUser manager = f.user("manager");
            grant(f, manager, "account:manage");
            MockMvc mvc = mvc(f);
            mvc.perform(post("/api/rule/console/accounts").session(f.session(manager)).contentType("application/json")
                    .content("{\"username\":\"blocked\",\"password\":\"InitialPass123!\",\"permissionOverrides\":{\"role:manage\":\"ALLOW\"}}"))
                    .andExpect(status().isForbidden());
            assertEquals(Long.valueOf(0), f.jdbc.queryForObject("SELECT COUNT(*) FROM console_user WHERE username='blocked'", Long.class));
            mvc.perform(post("/api/rule/console/accounts").session(f.session(manager)).contentType("application/json")
                    .content("{\"username\":\"created\",\"password\":\"InitialPass123!\"}"))
                    .andExpect(status().isOk());
            Long created = f.jdbc.queryForObject("SELECT id FROM console_user WHERE username='created'", Long.class);
            assertTrue(f.permissions.effectivePermissions(created).isEmpty());
        }
    }

    @Test
    public void dedicatedPermissionsWorkIndependentlyAndExplicitDenialTakesEffect() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            f.initialize();
            ConsoleUser manager = f.user("manager");
            ConsoleUser target = f.user("target");
            grant(f, manager, "account:role-assign", "account:password-reset");
            MockMvc mvc = mvc(f);
            mvc.perform(put("/api/rule/console/accounts/{id}/permissions", target.getId()).session(f.session(manager))
                    .contentType("application/json").content("{\"permissionOverrides\":{\"rule:view\":\"ALLOW\"}}"))
                    .andExpect(status().isOk());
            assertTrue(f.permissions.effectivePermissions(target.getId()).contains("rule:view"));
            mvc.perform(put("/api/rule/console/accounts/{id}/password", target.getId()).session(f.session(manager))
                    .contentType("application/json").content("{\"password\":\"ChangedPass123!\"}"))
                    .andExpect(status().isOk());
            String changedHash = f.sessions.getMapper(ConsoleUserMapper.class).selectById(target.getId()).getPasswordHash();
            assertTrue(cn.hutool.crypto.digest.BCrypt.checkpw("ChangedPass123!", changedHash));
            var revoked = new ConsoleUserPermissionRequest();
            revoked.setPermissionOverrides(Map.of("account:manage", "ALLOW", "account:password-reset", "DENY", "account:role-assign", "DENY"));
            f.accounts.saveUserPermissions(manager.getId(), revoked, "admin");
            mvc.perform(put("/api/rule/console/accounts/{id}/password", target.getId()).session(f.session(manager))
                    .contentType("application/json").content("{\"password\":\"RevokedPass123!\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/rule/console/accounts/{id}/permissions", target.getId()).session(f.session(manager))
                    .contentType("application/json").content("{\"permissionOverrides\":{}}"))
                    .andExpect(status().isForbidden());
            assertEquals(changedHash, f.sessions.getMapper(ConsoleUserMapper.class).selectById(target.getId()).getPasswordHash());
            assertTrue(f.permissions.effectivePermissions(target.getId()).contains("rule:view"));
        }
    }

    private void grant(ConsoleAccountSecurityTest.Fixture f, ConsoleUser user, String... codes) {
        var request = new ConsoleUserPermissionRequest();
        Map<String, String> grants = new LinkedHashMap<>();
        for (String code : codes) grants.put(code, "ALLOW");
        request.setPermissionOverrides(grants);
        f.accounts.saveUserPermissions(user.getId(), request, "admin");
    }

    private MockMvc mvc(ConsoleAccountSecurityTest.Fixture f) {
        ReflectionTestUtils.setField(f.accounts, "accessGuard", new ConsoleAccessGuard(f.props, f.permissions));
        var projects = new ConsoleProjectAccess();
        ReflectionTestUtils.setField(projects, "properties", f.props);
        ReflectionTestUtils.setField(projects, "permissions", f.permissions);
        ReflectionTestUtils.setField(f.accounts, "projectAccess", projects);
        var controller = new ConsoleAccountController();
        ReflectionTestUtils.setField(controller, "accountService", f.accounts);
        ReflectionTestUtils.setField(controller, "operatorResolver", new ConsoleOperatorResolver());
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new ConsolePermissionInterceptor(f.props, f.permissions)).build();
    }

    @Test
    public void administratorCannotUseResetOrAccountSaveToBypassOwnPasswordDenial() throws Exception {
        try (var f = new ConsoleAccountSecurityTest.Fixture()) {
            f.initialize();
            ConsoleUser manager = f.user("manager");
            grant(f, manager, "account:manage", "account:password-reset");
            MockMvc mvc = mvc(f);
            mvc.perform(put("/api/rule/console/accounts/{id}/password", manager.getId()).session(f.session(manager))
                    .contentType("application/json").content("{\"password\":\"BypassPass123!\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/rule/console/accounts").session(f.session(manager)).contentType("application/json")
                    .content(JSON.toJSONString(Map.of("id", manager.getId(), "username", "manager", "password", "BypassPass123!"))))
                    .andExpect(status().isForbidden());
            assertEquals(manager.getPasswordHash(), f.sessions.getMapper(ConsoleUserMapper.class).selectById(manager.getId()).getPasswordHash());
            grant(f, manager, "account:manage", "account:password-reset", "account:password-change");
            mvc.perform(put("/api/rule/console/accounts/{id}/password", manager.getId()).session(f.session(manager))
                    .contentType("application/json").content("{\"password\":\"AllowedPass123!\"}"))
                    .andExpect(status().isOk());
            assertTrue(cn.hutool.crypto.digest.BCrypt.checkpw("AllowedPass123!",
                    f.sessions.getMapper(ConsoleUserMapper.class).selectById(manager.getId()).getPasswordHash()));
        }
    }
}
