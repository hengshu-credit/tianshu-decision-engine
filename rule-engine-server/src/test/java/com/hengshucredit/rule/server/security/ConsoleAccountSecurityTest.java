package com.hengshucredit.rule.server.security;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.model.dto.*;
import com.hengshucredit.rule.server.consolelogin.*;
import com.hengshucredit.rule.server.mapper.*;
import com.hengshucredit.rule.server.security.*;
import com.hengshucredit.rule.server.service.*;
import com.hengshucredit.rule.server.controller.mgmt.ConsoleRoleController;
import com.hengshucredit.rule.server.common.PlatformResponseAdvice;
import com.hengshucredit.rule.server.config.MybatisPlusConfig;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.*;
import org.springframework.web.method.HandlerMethod;
import org.junit.*;
import org.junit.runner.JUnitCore;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Account state transitions and cross-module authorization, with isolated database and captured mail. */
public class ConsoleAccountSecurityTest {
    static class MailCapture extends ConsoleMailService {
        String link;
        @Override public boolean configured() { return true; }
        @Override public void sendVerification(String email, String name, String link) { this.link = link; }
        @Override public void sendPasswordReset(String email, String name, String link) { this.link = link; }
        String token() { return link.substring(link.indexOf("?token=") + 7); }
    }
    static class Fixture implements AutoCloseable {
        final JdbcTemplate jdbc;
        final SqlSessionTemplate sessions;
        ConsoleAccountCredentialService credential = new ConsoleAccountCredentialService();
        ConsoleAccountManagementService accounts = new ConsoleAccountManagementService();
        DatabaseConsoleAccountService login = new DatabaseConsoleAccountService();
        final ConsolePermissionService permissions = new ConsolePermissionService();
        final RuleEngineConsoleLoginProperties props = new RuleEngineConsoleLoginProperties();
        final MailCapture mail = new MailCapture();
        Fixture() throws Exception {
            var ds = new DriverManagerDataSource("jdbc:h2:mem:audit" + UUID.randomUUID().toString().replace("-", "") + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
            jdbc = new JdbcTemplate(ds);
            String schema = new org.springframework.core.io.ClassPathResource("sql/schema.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            for (String table : List.of("rule_project", "console_user_project", "console_user_project_permission", "console_security_lock", "console_user", "console_account_token", "console_role", "console_permission", "console_user_role", "console_role_permission", "console_user_permission_override", "console_security_audit_log")) {
                int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `" + table + "`");
                String ddl = schema.substring(start, schema.indexOf(';', start) + 1).replaceFirst("\\) ENGINE=.*;", ")");
                jdbc.execute(ddl);
            }
            jdbc.update("INSERT INTO console_security_lock (id) VALUES (1)");
            var config = new MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(true);
            config.setLogImpl(NoLoggingImpl.class);
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds);
            factory.setConfiguration(config);
            factory.setGlobalConfig(new GlobalConfig().setBanner(false).setMetaObjectHandler(new MybatisPlusConfig().metaObjectHandler()));
            var sf = factory.getObject();
            for (Class<?> type : List.of(ConsoleUserMapper.class, ConsoleAccountTokenMapper.class, ConsoleRoleMapper.class, ConsolePermissionMapper.class, ConsoleUserRoleMapper.class, ConsoleRolePermissionMapper.class, ConsoleUserPermissionOverrideMapper.class, ConsoleSecurityAuditLogMapper.class, ConsoleUserProjectMapper.class, ConsoleUserProjectPermissionMapper.class)) config.addMapper(type);
            sessions = new SqlSessionTemplate(sf);
            for (Object target : List.of(credential, accounts, login, permissions)) {
                for (var field : target.getClass().getDeclaredFields()) {
                    if (field.getType().getPackageName().endsWith(".mapper")) ReflectionTestUtils.setField(target, field.getName(), sessions.getMapper(field.getType()));
                }
            }
            props.setEnabled(true); props.setRegistrationEnabled(true); props.setPasswordRecoveryEnabled(true);
            ReflectionTestUtils.setField(credential, "properties", props);
            ReflectionTestUtils.setField(credential, "mailService", mail);
            ReflectionTestUtils.setField(accounts, "permissionService", permissions);
            ReflectionTestUtils.setField(login, "permissionService", permissions);
            credential = transactional(credential, ConsoleAccountCredentialService.class, ds);
            accounts = transactional(accounts, ConsoleAccountManagementService.class, ds);
            login = transactional(login, DatabaseConsoleAccountService.class, ds);
            ReflectionTestUtils.setField(credential, "databaseAccounts", login);
        }
        DatabaseConsoleAccountService.ConsoleLoginResult initialize() {
            login.initializeAdministrator("admin", "admin", "AdminPass123!");
            return login.authenticate("admin", "AdminPass123!");
        }
        String recoveryCode() throws Exception {
            try (var connection = jdbc.getDataSource().getConnection()) { return ConsoleAdministratorRecovery.issue(connection); }
        }
        private <T> T transactional(T target, Class<T> type, javax.sql.DataSource dataSource) {
            var proxy = new org.springframework.aop.framework.ProxyFactory(target);
            proxy.setProxyTargetClass(true);
            var advice = new org.springframework.transaction.interceptor.TransactionInterceptor();
            advice.setTransactionManager(new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
            advice.setTransactionAttributeSource(new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource());
            proxy.addAdvice(advice);
            return type.cast(proxy.getProxy());
        }
        ConsoleUser user(String name) {
            var user = new ConsoleUser(); user.setUsername(name); user.setDisplayName(name);
            user.setEmail(name + "@example.test"); user.setEmailVerifiedAt(LocalDateTime.now());
            user.setRegistrationStatus("ACTIVE"); user.setRegistrationSource("ADMIN");
            user.setStatus(1); user.setPermissionVersion(1L); user.setPasswordHash(BCrypt.hashpw("OldPass123!"));
            sessions.getMapper(ConsoleUserMapper.class).insert(user); return user;
        }
        ConsoleAuthController controller() {
            var c = new ConsoleAuthController();
            ReflectionTestUtils.setField(c, "consoleLoginProperties", props);
            ReflectionTestUtils.setField(c, "databaseAccountService", login);
            ReflectionTestUtils.setField(c, "credentialService", credential); return c;
        }
        MockHttpSession session(ConsoleUser user) {
            var s = new MockHttpSession(); s.setAttribute(props.getSessionUserIdAttribute(), user.getId());
            s.setAttribute(props.getSessionUsernameAttribute(), user.getUsername());
            s.setAttribute(props.getSessionPermissionVersionAttribute(), user.getPermissionVersion()); return s;
        }
        @Override public void close() { jdbc.execute("SHUTDOWN"); }
    }

    @Test public void revokedSessionMustNotUpdateProfile() throws Exception {
        try (var f = new Fixture()) {
            var user = f.user("alice"); var old = f.session(user);
            f.accounts.resetPassword(user.getId(), "AdminNew123!", "admin");
            var mvc = MockMvcBuilders.standaloneSetup(f.controller()).setControllerAdvice(new PlatformResponseAdvice()).build();
            int status = mvc.perform(put("/api/auth/console/profile").session(old).contentType("application/json")
                    .content("{\"displayName\":\"changed-from-revoked-session\",\"email\":\"alice@example.test\"}"))
                    .andReturn().getResponse().getStatus();
            assertEquals("stale session was accepted and stored name=" + f.credential.profile(user.getId()).displayName(), 401, status);
        }
    }
    @Test public void newEmailMustLoseVerifiedState() throws Exception {
        try (var f = new Fixture()) {
            var user = f.user("alice");
            var result = f.credential.updateProfile(user.getId(), "Alice", "new@example.test", "https://console.example.test", "127.0.0.1");
            assertEquals("new@example.test", result.email());
            assertNull("new mailbox retained previous verification timestamp", result.emailVerifiedAt());
        }
    }
    @Test public void emailVerificationMustNotUndoAdminDisable() throws Exception {
        try (var f = new Fixture()) {
            var user = f.user("alice");
            f.credential.updateProfile(user.getId(), "Alice", "new@example.test", "https://console.example.test", "127.0.0.1");
            f.accounts.changeUserStatus(user.getId(), 0, "admin");
            assertThrows(IllegalArgumentException.class, () -> f.credential.verifyEmail(f.mail.token(), "127.0.0.1"));
            assertEquals("old email verification link reactivated disabled account", Integer.valueOf(0), f.sessions.getMapper(ConsoleUserMapper.class).selectById(user.getId()).getStatus());
        }
    }
    @Test public void adminResetMustInvalidateOutstandingResetLink() throws Exception {
        try (var f = new Fixture()) {
            var user = f.user("alice");
            f.credential.requestPasswordReset("alice", "https://console.example.test", "127.0.0.1");
            String oldToken = f.mail.token();
            f.accounts.resetPassword(user.getId(), "AdminNew123!", "admin");
            assertThrows("old link overwrites freshly reset password", IllegalArgumentException.class,
                    () -> f.credential.resetPassword(oldToken, "LinkNew123!", "127.0.0.1"));
        }
    }
    @Test public void resetEmailMustIgnoreUntrustedOrigin() throws Exception {
        try (var f = new Fixture()) {
            f.user("alice");
            var req = new MockHttpServletRequest(); req.setScheme("https"); req.setServerName("console.example.test"); req.setServerPort(443);
            req.addHeader("Origin", "https://untrusted.example.test");
            var body = new ConsoleAuthController.IdentifierRequest(); body.setIdentifier("alice");
            assertThrows(IllegalStateException.class, () -> f.controller().requestPasswordRecovery(body, req));
            assertNull(f.mail.link);
        }
    }
    @Test public void registrationMustNotBlockFirstAdminBootstrap() throws Exception {
        try (var f = new Fixture()) {
            assertThrows(IllegalStateException.class, () -> f.credential.register("alice", "Alice", "alice@example.test", "NewPass123!", "https://console.example.test", "127.0.0.1"));
            assertTrue(f.initialize().authenticated());
        }
    }
    @Test public void roleAssignerMustLoadRequiredRoleCatalog() throws Exception {
        var props = new RuleEngineConsoleLoginProperties(); props.setEnabled(true);
        var permissions = new ConsolePermissionService() {
            @Override public boolean hasPermission(Long id, String code) { return "account:role-assign".equals(code); }
        };
        var request = new MockHttpServletRequest("GET", "/api/rule/console/roles");
        request.getSession().setAttribute(props.getSessionUserIdAttribute(), 9L);
        var response = new MockHttpServletResponse();
        boolean allowed = new ConsolePermissionInterceptor(props, permissions).preHandle(request, response,
                new HandlerMethod(new ConsoleRoleController(), ConsoleRoleController.class.getMethod("roles")));
        assertTrue("only role assignment permission gets HTTP " + response.getStatus() + " for role catalog required by UI", allowed);
    }
    @Test public void lineageMustEnforceResourceVisibility() throws Exception {
        Class<?> testClass = Class.forName("com.hengshucredit.rule.server.service.RuleLineageServiceTest");
        testClass.getMethod("initTableInfo").invoke(null);
        var factory = testClass.getDeclaredMethod("serviceWithProjectBranch", boolean.class); factory.setAccessible(true);
        var service = (RuleLineageService) factory.invoke(null, false);
        var props = new RuleEngineConsoleLoginProperties(); props.setEnabled(true);
        var request = new MockHttpServletRequest(); request.getSession().setAttribute(props.getSessionUserIdAttribute(), 9L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            var guard = new ConsoleAccessGuard(props, new ConsolePermissionService() {
                @Override public boolean hasPermission(Long id, String code) { return "lineage:view".equals(code); }
            });
            ReflectionTestUtils.setField(service, "accessGuard", guard);
            assertThrows(ConsoleAccessDeniedException.class, () -> service.options("VARIABLE", null, null));
            assertThrows("graph exposes resource names and references denied by options", ConsoleAccessDeniedException.class,
                    () -> service.graph("VARIABLE", 1L, "ALL", 3));
        } finally { RequestContextHolder.resetRequestAttributes(); }
    }
    @Test public void administratorEditsMustKeepEmailsUnique() throws Exception {
        try (var f = new Fixture()) {
            assertTrue(f.initialize().authenticated());
            for (String name : List.of("alice", "bob")) {
                var request = new ConsoleUserSaveRequest(); request.setUsername(name); request.setPassword("NewPass123!"); request.setEmail("shared@example.test");
                if (name.equals("alice")) f.accounts.saveUser(request, "admin");
                else assertThrows(IllegalArgumentException.class, () -> f.accounts.saveUser(request, "admin"));
            }
            assertEquals("two active accounts share a verified recovery mailbox; recovery uses LIMIT 1", Integer.valueOf(1),
                    f.jdbc.queryForObject("SELECT COUNT(*) FROM console_user WHERE email='shared@example.test'", Integer.class));
        }
    }
    @Test public void systemRoleRenameMustNotBreakEmergencyRecovery() throws Exception {
        try (var f = new Fixture()) {
            assertTrue(f.initialize().authenticated());
            var role = f.accounts.listRoles().get(0);
            var request = new ConsoleRoleSaveRequest(); request.setId(role.getId()); request.setRoleCode("RENAMED_ADMIN"); request.setRoleName("Administrator");
            assertThrows(IllegalArgumentException.class, () -> f.accounts.saveRole(request, "admin"));
            String recoveryCode = f.recoveryCode();
            boolean recovered = true;
            try { f.credential.emergencyReset("admin", recoveryCode, "Emergency123!", "127.0.0.1"); }
            catch (IllegalArgumentException e) { recovered = false; }
            assertTrue("renaming system role makes the correct emergency recovery key unusable", recovered);
        }
    }
    @Test public void controlEmptyDatabaseCanBootstrap() throws Exception {
        try (var f = new Fixture()) {
            var result = f.initialize();
            assertTrue(result.authenticated()); assertTrue(result.permissions().contains("account:manage"));
        }
    }
    @Test public void artifactDownloadMustRespectEmbeddedFunctionVisibility() throws Exception {
        var pack = new com.hengshucredit.rule.server.artifact.DecisionArtifactPackage();
        byte[] functionJson = "{\"id\":7,\"implScript\":\"return confidentialFixture;\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        pack.addComponent("functions/7.json", "application/json", functionJson, Map.of("resourceType", "FUNCTION", "resourceId", 7L));
        var codec = new com.hengshucredit.rule.server.artifact.DecisionArtifactPackageCodec();
        var artifact = new DecisionArtifact(); artifact.setId(5L); artifact.setPackageContent(codec.encode(pack));
        var deployment = new com.hengshucredit.rule.server.artifact.ArtifactDeploymentService() {
            @Override protected DecisionArtifact loadArtifact(Long id) { return artifact; }
        };
        var controller = new com.hengshucredit.rule.server.controller.mgmt.DecisionArtifactController();
        ReflectionTestUtils.setField(controller, "deploymentService", deployment);
        var props = new RuleEngineConsoleLoginProperties(); props.setEnabled(true);
        var permissions = new ConsolePermissionService() {
            @Override public boolean hasPermission(Long id, String code) { return "rule:view".equals(code); }
        };
        ReflectionTestUtils.setField(deployment, "accessGuard", new ConsoleAccessGuard(props, permissions));
        var session = new MockHttpSession(); session.setAttribute(props.getSessionUserIdAttribute(), 9L);
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new com.hengshucredit.rule.server.common.GlobalExceptionHandler())
                .addInterceptors(new ConsolePermissionInterceptor(props, permissions)).build();
        var response = mvc.perform(get("/api/rule/artifact/5/download").session(session)).andReturn().getResponse();
        assertEquals("rule:view alone downloads embedded function source despite no function:view", 403, response.getStatus());
    }
    @Test public void controlBogusAndConsumedTokensAreRejected() throws Exception {
        try (var f = new Fixture()) {
            f.user("alice");
            assertThrows(IllegalArgumentException.class, () -> f.credential.resetPassword("bogus", "NewPass123!", "127.0.0.1"));
            f.credential.requestPasswordReset("alice", "https://console.example.test", "127.0.0.1");
            String token = f.mail.token();
            f.credential.resetPassword(token, "NewPass123!", "127.0.0.1");
            assertThrows(IllegalArgumentException.class, () -> f.credential.resetPassword(token, "OtherPass123!", "127.0.0.1"));
        }
    }
    @Test public void controlMeRejectsRevokedSession() throws Exception {
        try (var f = new Fixture()) {
            var user = f.user("alice"); var old = f.session(user);
            f.accounts.resetPassword(user.getId(), "AdminNew123!", "admin");
            var mvc = MockMvcBuilders.standaloneSetup(f.controller()).setControllerAdvice(new PlatformResponseAdvice()).build();
            assertEquals(401, mvc.perform(get("/api/auth/console/me").session(old)).andReturn().getResponse().getStatus());
        }
    }
}
