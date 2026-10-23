package com.hengshucredit.rule.server.consolelogin;

import com.hengshucredit.rule.model.ApiResponse;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理端控制台登录、登出及当前用户查询；配置查询在关闭登录时仍可用。
 * 首次注册和此后的登录均以持久化账户为准，不接受环境文件中的控制台凭证。
 */
@RestController
@RequestMapping("/api/auth")
public class ConsoleAuthController {

    @Resource
    private RuleEngineConsoleLoginProperties consoleLoginProperties;

    @Autowired(required = false)
    private DatabaseConsoleAccountService databaseAccountService;

    @Autowired(required = false)
    private ConsoleAccountCredentialService credentialService;
    @Resource
    private ConsoleAuthRateLimiter rateLimiter;
    @Autowired(required = false)
    private com.hengshucredit.rule.server.security.ConsolePermissionService permissionService;

    /**
     * 返回是否启用用户名密码登录，供前端决定是否展示登录页与携带 Cookie。
     */
    @GetMapping("/console/config")
    public ApiResponse<Map<String, Object>> config() {
        Map<String, Object> body = new HashMap<>(5);
        body.put("loginEnabled", consoleLoginProperties.isEnabled());
        boolean initializationRequired = consoleLoginProperties.isEnabled() && databaseAccountService != null
                && databaseAccountService.initializationRequired();
        body.put("initializationRequired", initializationRequired);
        body.put("registrationEnabled", consoleLoginProperties.isRegistrationEnabled()
                && credentialService != null && credentialService.mailConfigured() && credentialService.registrationReady());
        body.put("passwordRecoveryEnabled", consoleLoginProperties.isPasswordRecoveryEnabled()
                && credentialService != null && credentialService.mailConfigured());
        body.put("breakGlassRecoveryEnabled", consoleLoginProperties.isEnabled()
                && credentialService != null && credentialService.breakGlassConfigured());
        body.put("passwordMinLength", 8);
        return ApiResponse.ok(body);
    }

    @PostMapping("/console/initialize")
    public ApiResponse<Void> initialize(@RequestBody RegistrationRequest body, HttpServletRequest request) {
        if (!consoleLoginProperties.isEnabled()) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PERMISSION_DENIED, "控制台登录未启用");
        if (databaseAccountService == null) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.SERVICE_UNAVAILABLE, "账户服务未就绪");
        if (body == null) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.REQUIRED_FIELD_MISSING, "初始化信息不能为空");
        throttle("initialize", body.getUsername(), request);
        databaseAccountService.initializeAdministrator(body.getUsername(), body.getDisplayName(), body.getPassword());
        return ApiResponse.ok();
    }

    @PostMapping("/console/register")
    public ApiResponse<Void> register(@RequestBody RegistrationRequest body,
                            HttpServletRequest request) {
        requireCredentialService();
        if (body == null) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.REQUIRED_FIELD_MISSING, "注册信息不能为空");
        throttle("register", body.getEmail(), request);
        credentialService.register(body.getUsername(), body.getDisplayName(),
                body.getEmail(), body.getPassword(), baseUrl(request),
                request.getRemoteAddr());
        return ApiResponse.ok();
    }

    @PostMapping("/console/register/verify")
    public ApiResponse<Void> verifyRegistration(@RequestBody TokenRequest body,
                                      HttpServletRequest request) {
        requireCredentialService();
        credentialService.verifyEmail(body == null ? null : body.getToken(),
                request.getRemoteAddr());
        return ApiResponse.ok();
    }

    @PostMapping("/console/password-recovery/request")
    public ApiResponse<Void> requestPasswordRecovery(@RequestBody IdentifierRequest body,
                                           HttpServletRequest request) {
        requireCredentialService();
        throttle("password-mail", body == null ? null : body.getIdentifier(), request);
        credentialService.requestPasswordReset(
                body == null ? null : body.getIdentifier(), baseUrl(request),
                request.getRemoteAddr());
        return ApiResponse.ok();
    }

    @PostMapping("/console/password-recovery/confirm")
    public ApiResponse<Void> confirmPasswordRecovery(@RequestBody PasswordResetRequest body,
                                           HttpServletRequest request) {
        requireCredentialService();
        credentialService.resetPassword(body == null ? null : body.getToken(),
                body == null ? null : body.getPassword(),
                request.getRemoteAddr());
        return ApiResponse.ok();
    }

    @PostMapping("/console/emergency-password-reset")
    public ApiResponse<Void> emergencyPasswordReset(@RequestBody EmergencyResetRequest body,
                                          HttpServletRequest request) {
        requireCredentialService();
        throttle("emergency", body == null ? null : body.getUsername(), request);
        credentialService.emergencyReset(
                body == null ? null : body.getUsername(),
                body == null ? null : body.getRecoveryKey(),
                body == null ? null : body.getPassword(),
                request.getRemoteAddr());
        return ApiResponse.ok();
    }

    /**
     * 兼容旧控制台的登录状态查询，同时返回当前登录配置与会话状态。
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status(HttpServletRequest request) {
        boolean loginEnabled = consoleLoginProperties.isEnabled();
        HttpSession session = request.getSession(false);
        Object username = session == null
                ? null
                : session.getAttribute(consoleLoginProperties.getSessionUsernameAttribute());
        Map<String, Object> body = new HashMap<>(3);
        body.put("loginEnabled", loginEnabled);
        boolean authenticated = !loginEnabled || (databaseAccountService == null ? username != null : isLoggedIn(request));
        body.put("authenticated", authenticated);
        body.put("username", !authenticated || username == null ? null : username.toString());
        return ApiResponse.ok(body);
    }

    /**
     * 校验用户名密码并建立会话；启用登录时才会校验成功。
     */
    @PostMapping("/console/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest body,
                                        HttpServletRequest request) {
        if (!consoleLoginProperties.isEnabled()) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PERMISSION_DENIED, "控制台用户名密码登录未启用");
        }
        if (databaseAccountService == null) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.SERVICE_UNAVAILABLE, "登录认证组件未就绪");
        }
        if (body == null || body.getUsername() == null || body.getPassword() == null) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.REQUIRED_FIELD_MISSING, "用户名或密码不能为空");
        }
        String username = body.getUsername().trim();
        if (username.isEmpty()) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.REQUIRED_FIELD_MISSING, "用户名不能为空");
        }
        throttle("login", username, request);
        var loginResult = databaseAccountService.authenticate(username, body.getPassword());
        if (!loginResult.authenticated()) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.CREDENTIAL_INVALID, "用户名或密码错误");
        }
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(consoleLoginProperties.getSessionUsernameAttribute(), loginResult.username());
        session.setAttribute(consoleLoginProperties.getSessionOperatorNameAttribute(), loginResult.operatorName());
        session.setAttribute(consoleLoginProperties.getSessionUserIdAttribute(), loginResult.userId());
        session.setAttribute(consoleLoginProperties.getSessionPermissionVersionAttribute(), loginResult.permissionVersion());
        return ApiResponse.ok(loginResultData(loginResult));
    }

    /**
     * 销毁当前会话。
     */
    @PostMapping("/console/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponse.ok();
    }

    @GetMapping("/console/profile")
    public ApiResponse<ConsoleAccountCredentialService.Profile> profile(
            HttpServletRequest request) {
        if (!isLoggedIn(request)) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.LOGIN_REQUIRED, null);
        requireCredentialService();
        return ApiResponse.ok(credentialService.profile(currentUserId(request)));
    }

    @PutMapping("/console/profile")
    public ApiResponse<ConsoleAccountCredentialService.Profile> updateProfile(
            @RequestBody ProfileRequest body, HttpServletRequest request) {
        if (!isLoggedIn(request)) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.LOGIN_REQUIRED, null);
        requireCredentialService();
        return ApiResponse.ok(credentialService.updateProfile(
                currentUserId(request), body == null ? null : body.getDisplayName(),
                body == null ? null : body.getEmail(), consoleLoginProperties.getMail().getBaseUrl(),
                request.getRemoteAddr()));
    }

    @PostMapping("/console/password/change")
    public ApiResponse<Void> changePassword(@RequestBody PasswordChangeRequest body,
                                  HttpServletRequest request) {
        if (!isLoggedIn(request)) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.LOGIN_REQUIRED, null);
        throttle("password-change", String.valueOf(currentUserId(request)), request);
        requireCredentialService();
        credentialService.changePassword(currentUserId(request),
                body == null ? null : body.getCurrentPassword(),
                body == null ? null : body.getPassword(),
                request.getRemoteAddr());
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ApiResponse.ok();
    }

    /**
     * 查询当前登录用户名；未启用登录时返回 data 为 null；启用但未登录返回 401。
     */
    @GetMapping("/console/me")
    public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
        if (!consoleLoginProperties.isEnabled()) {
            return ApiResponse.ok(null);
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.LOGIN_REQUIRED, null);
        }
        Object u = session.getAttribute(consoleLoginProperties.getSessionUsernameAttribute());
        if (u == null) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.LOGIN_REQUIRED, null);
        }
        Object userIdValue = session.getAttribute(
                consoleLoginProperties.getSessionUserIdAttribute());
        if (databaseAccountService != null && userIdValue instanceof Number number) {
            DatabaseConsoleAccountService.ConsoleLoginResult current =
                    databaseAccountService.currentUser(number.longValue());
            if (!current.authenticated()) {
                ConsoleSessionAuthInterceptor.clearIdentity(session, consoleLoginProperties);
                return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.ACCOUNT_DISABLED, null);
            }
            if (!java.util.Objects.equals(current.permissionVersion(), session.getAttribute(
                    consoleLoginProperties.getSessionPermissionVersionAttribute()))) {
                ConsoleSessionAuthInterceptor.clearIdentity(session, consoleLoginProperties);
                return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.LOGIN_REQUIRED, "会话权限已变更，请重新登录");
            }
            return ApiResponse.ok(loginResultData(current));
        }
        Map<String, Object> data = new HashMap<>(2);
        data.put("username", u.toString());
        return ApiResponse.ok(data);
    }

    private Map<String, Object> loginResultData(
            DatabaseConsoleAccountService.ConsoleLoginResult result) {
        Map<String, Object> data = new HashMap<>(8);
        data.put("userId", result.userId());
        data.put("username", result.username());
        data.put("operatorName", result.operatorName());
        data.put("operatorNames", result.operatorNames());
        data.put("displayName", result.displayName());
        data.put("permissionVersion", result.permissionVersion());
        data.put("roleCodes", result.roleCodes());
        data.put("permissions", result.permissions());
        if (permissionService != null) {
            var scope = permissionService.scope(result.userId());
            data.put("defaultPermissions", scope.permissions());
            data.put("projectAccessMode", scope.allProjects() ? "ALL" : "SELECTED");
            data.put("projectIds", scope.projectIds());
            data.put("globalEditable", scope.globalEditable());
            data.put("projectPermissionOverrides", scope.projectOverrides());
        }
        data.put("bootstrapped", result.bootstrapped());
        return data;
    }

    private void requireCredentialService() {
        if (credentialService == null) {
            throw new IllegalStateException("账户凭证服务未就绪");
        }
    }

    @PostMapping("/console/register/resend")
    public ApiResponse<Void> resendRegistration(@RequestBody IdentifierRequest body, HttpServletRequest request) {
        requireCredentialService();
        throttle("register-mail", body == null ? null : body.getIdentifier(), request);
        credentialService.resendRegistration(body == null ? null : body.getIdentifier(), baseUrl(request), request.getRemoteAddr());
        return ApiResponse.ok();
    }

    @PostMapping("/console/profile/email-verification")
    public ApiResponse<Void> resendEmailVerification(HttpServletRequest request) {
        if (!isLoggedIn(request)) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.LOGIN_REQUIRED, null);
        requireCredentialService();
        throttle("verify-mail", String.valueOf(currentUserId(request)), request);
        credentialService.resendEmailVerification(currentUserId(request), baseUrl(request), request.getRemoteAddr());
        return ApiResponse.ok();
    }

    private void throttle(String action, String identifier, HttpServletRequest request) {
        if (rateLimiter != null) rateLimiter.check(action, identifier, request.getRemoteAddr());
    }

    private boolean isLoggedIn(HttpServletRequest request) {
        if (!consoleLoginProperties.isEnabled()) return false;
        HttpSession session = request.getSession(false);
        if (session == null) return false;
        Object id = session.getAttribute(consoleLoginProperties.getSessionUserIdAttribute());
        if (!(id instanceof Number number) || databaseAccountService == null) return false;
        var current = databaseAccountService.currentUser(number.longValue());
        if (!current.authenticated() || !java.util.Objects.equals(current.permissionVersion(),
                session.getAttribute(consoleLoginProperties.getSessionPermissionVersionAttribute()))) {
            ConsoleSessionAuthInterceptor.clearIdentity(session, consoleLoginProperties);
            return false;
        }
        return true;
    }

    private Long currentUserId(HttpServletRequest request) {
        Object value = request.getSession(false).getAttribute(
                consoleLoginProperties.getSessionUserIdAttribute());
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("登录账户无效");
        }
    }

    private String baseUrl(HttpServletRequest request) {
        String configured = consoleLoginProperties.getMail().getBaseUrl();
        if (configured == null || configured.isBlank()) throw new IllegalStateException("请配置可信的控制台邮件链接地址 CONSOLE_MAIL_BASE_URL");
        java.net.URI uri;
        try { uri = java.net.URI.create(configured.trim()); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("控制台邮件链接地址无效", e); }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("控制台邮件链接地址必须为可信的 HTTP(S) 地址，且不含认证信息、查询或片段");
        }
        return configured.trim();
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    public static class RegistrationRequest {
        private String username;
        private String displayName;
        private String email;
        private String password;
    }

    @Data
    public static class TokenRequest {
        private String token;
    }

    @Data
    public static class IdentifierRequest {
        private String identifier;
    }

    @Data
    public static class PasswordResetRequest {
        private String token;
        private String password;
    }

    @Data
    public static class PasswordChangeRequest {
        private String currentPassword;
        private String password;
    }

    @Data
    public static class EmergencyResetRequest {
        private String username;
        private String recoveryKey;
        private String password;
    }

    @Data
    public static class ProfileRequest {
        private String displayName;
        private String email;
    }
}
