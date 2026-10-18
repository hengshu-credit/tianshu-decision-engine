package com.hengshucredit.rule.server.consolelogin;

import com.hengshucredit.rule.server.common.R;
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
 * 登录校验委托 {@link ConsoleLoginAuthenticator}：自定义 Bean 覆盖默认的 yml builtin 账号校验。
 */
@RestController
@RequestMapping("/api/auth")
public class ConsoleAuthController {

    @Resource
    private RuleEngineConsoleLoginProperties consoleLoginProperties;

    @Autowired(required = false)
    private ConsoleLoginAuthenticator consoleLoginAuthenticator;

    @Autowired(required = false)
    private DatabaseConsoleAccountService databaseAccountService;

    @Autowired(required = false)
    private ConsoleAccountCredentialService credentialService;

    /**
     * 返回是否启用用户名密码登录，供前端决定是否展示登录页与携带 Cookie。
     */
    @GetMapping("/console/config")
    public R<Map<String, Object>> config() {
        Map<String, Object> body = new HashMap<>(5);
        body.put("loginEnabled", consoleLoginProperties.isEnabled());
        body.put("registrationEnabled", consoleLoginProperties.isRegistrationEnabled()
                && credentialService != null && credentialService.mailConfigured());
        body.put("passwordRecoveryEnabled", consoleLoginProperties.isPasswordRecoveryEnabled()
                && credentialService != null && credentialService.mailConfigured());
        body.put("breakGlassRecoveryEnabled", consoleLoginProperties.isBreakGlassRecoveryEnabled()
                && credentialService != null && credentialService.breakGlassConfigured());
        body.put("passwordMinLength", 8);
        return R.ok(body);
    }

    @PostMapping("/console/register")
    public R<Void> register(@RequestBody RegistrationRequest body,
                            HttpServletRequest request) {
        requireCredentialService();
        if (body == null) return R.fail("注册信息不能为空");
        credentialService.register(body.getUsername(), body.getDisplayName(),
                body.getEmail(), body.getPassword(), baseUrl(request),
                request.getRemoteAddr());
        return R.ok();
    }

    @PostMapping("/console/register/verify")
    public R<Void> verifyRegistration(@RequestBody TokenRequest body,
                                      HttpServletRequest request) {
        requireCredentialService();
        credentialService.verifyEmail(body == null ? null : body.getToken(),
                request.getRemoteAddr());
        return R.ok();
    }

    @PostMapping("/console/password-recovery/request")
    public R<Void> requestPasswordRecovery(@RequestBody IdentifierRequest body,
                                           HttpServletRequest request) {
        requireCredentialService();
        credentialService.requestPasswordReset(
                body == null ? null : body.getIdentifier(), baseUrl(request),
                request.getRemoteAddr());
        return R.ok();
    }

    @PostMapping("/console/password-recovery/confirm")
    public R<Void> confirmPasswordRecovery(@RequestBody PasswordResetRequest body,
                                           HttpServletRequest request) {
        requireCredentialService();
        credentialService.resetPassword(body == null ? null : body.getToken(),
                body == null ? null : body.getPassword(),
                request.getRemoteAddr());
        return R.ok();
    }

    @PostMapping("/console/emergency-password-reset")
    public R<Void> emergencyPasswordReset(@RequestBody EmergencyResetRequest body,
                                          HttpServletRequest request) {
        requireCredentialService();
        credentialService.emergencyReset(
                body == null ? null : body.getUsername(),
                body == null ? null : body.getRecoveryKey(),
                body == null ? null : body.getPassword(),
                request.getRemoteAddr());
        return R.ok();
    }

    /**
     * 兼容旧控制台的登录状态查询，同时返回当前登录配置与会话状态。
     */
    @GetMapping("/status")
    public R<Map<String, Object>> status(HttpServletRequest request) {
        boolean loginEnabled = consoleLoginProperties.isEnabled();
        HttpSession session = request.getSession(false);
        Object username = session == null
                ? null
                : session.getAttribute(consoleLoginProperties.getSessionUsernameAttribute());
        Map<String, Object> body = new HashMap<>(3);
        body.put("loginEnabled", loginEnabled);
        body.put("authenticated", !loginEnabled || username != null);
        body.put("username", username == null ? null : username.toString());
        return R.ok(body);
    }

    /**
     * 校验用户名密码并建立会话；启用登录时才会校验成功。
     */
    @PostMapping("/console/login")
    public R<Map<String, Object>> login(@RequestBody LoginRequest body,
                                        HttpServletRequest request) {
        if (!consoleLoginProperties.isEnabled()) {
            return R.fail("控制台用户名密码登录未启用");
        }
        if (databaseAccountService == null && consoleLoginAuthenticator == null) {
            return R.fail("登录认证组件未就绪");
        }
        if (body == null || body.getUsername() == null || body.getPassword() == null) {
            return R.fail("用户名或密码不能为空");
        }
        String username = body.getUsername().trim();
        if (username.isEmpty()) {
            return R.fail("用户名不能为空");
        }
        DatabaseConsoleAccountService.ConsoleLoginResult loginResult = null;
        if (databaseAccountService != null) {
            loginResult = databaseAccountService.authenticate(
                    username, body.getPassword());
            if (!loginResult.authenticated()) {
                return R.fail(401, "用户名或密码错误");
            }
        } else if (!consoleLoginAuthenticator.authenticate(
                username, body.getPassword())) {
                return R.fail(401, "用户名或密码错误");
        }
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(consoleLoginProperties.getSessionUsernameAttribute(), username);
        Map<String, Object> data;
        if (loginResult == null) {
            data = new HashMap<>(2);
            data.put("username", username);
        } else {
            session.setAttribute(consoleLoginProperties.getSessionUserIdAttribute(),
                    loginResult.userId());
            session.setAttribute(
                    consoleLoginProperties.getSessionPermissionVersionAttribute(),
                    loginResult.permissionVersion());
            data = loginResultData(loginResult);
        }
        return R.ok(data);
    }

    /**
     * 销毁当前会话。
     */
    @PostMapping("/console/logout")
    public R<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return R.ok();
    }

    @GetMapping("/console/profile")
    public R<ConsoleAccountCredentialService.Profile> profile(
            HttpServletRequest request) {
        if (!isLoggedIn(request)) return R.fail(401, "未登录");
        requireCredentialService();
        return R.ok(credentialService.profile(currentUserId(request)));
    }

    @PutMapping("/console/profile")
    public R<ConsoleAccountCredentialService.Profile> updateProfile(
            @RequestBody ProfileRequest body, HttpServletRequest request) {
        if (!isLoggedIn(request)) return R.fail(401, "未登录");
        requireCredentialService();
        return R.ok(credentialService.updateProfile(
                currentUserId(request), body == null ? null : body.getDisplayName(),
                body == null ? null : body.getEmail(), baseUrl(request),
                request.getRemoteAddr()));
    }

    @PostMapping("/console/password/change")
    public R<Void> changePassword(@RequestBody PasswordChangeRequest body,
                                  HttpServletRequest request) {
        if (!isLoggedIn(request)) return R.fail(401, "未登录");
        requireCredentialService();
        credentialService.changePassword(currentUserId(request),
                body == null ? null : body.getCurrentPassword(),
                body == null ? null : body.getPassword(),
                request.getRemoteAddr());
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return R.ok();
    }

    /**
     * 查询当前登录用户名；未启用登录时返回 data 为 null；启用但未登录返回 401。
     */
    @GetMapping("/console/me")
    public R<Map<String, Object>> me(HttpServletRequest request) {
        if (!consoleLoginProperties.isEnabled()) {
            return R.ok(null);
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return R.fail(401, "未登录");
        }
        Object u = session.getAttribute(consoleLoginProperties.getSessionUsernameAttribute());
        if (u == null) {
            return R.fail(401, "未登录");
        }
        Object userIdValue = session.getAttribute(
                consoleLoginProperties.getSessionUserIdAttribute());
        if (databaseAccountService != null && userIdValue instanceof Number number) {
            DatabaseConsoleAccountService.ConsoleLoginResult current =
                    databaseAccountService.currentUser(number.longValue());
            if (!current.authenticated()) {
                ConsoleSessionAuthInterceptor.clearIdentity(session, consoleLoginProperties);
                return R.fail(401, "账户已停用或不存在");
            }
            if (!java.util.Objects.equals(current.permissionVersion(), session.getAttribute(
                    consoleLoginProperties.getSessionPermissionVersionAttribute()))) {
                ConsoleSessionAuthInterceptor.clearIdentity(session, consoleLoginProperties);
                return R.fail(401, "会话权限已变更，请重新登录");
            }
            return R.ok(loginResultData(current));
        }
        Map<String, Object> data = new HashMap<>(2);
        data.put("username", u.toString());
        return R.ok(data);
    }

    private Map<String, Object> loginResultData(
            DatabaseConsoleAccountService.ConsoleLoginResult result) {
        Map<String, Object> data = new HashMap<>(8);
        data.put("userId", result.userId());
        data.put("username", result.username());
        data.put("displayName", result.displayName());
        data.put("permissionVersion", result.permissionVersion());
        data.put("roleCodes", result.roleCodes());
        data.put("permissions", result.permissions());
        data.put("bootstrapped", result.bootstrapped());
        return data;
    }

    private void requireCredentialService() {
        if (credentialService == null) {
            throw new IllegalStateException("账户凭证服务未就绪");
        }
    }

    private boolean isLoggedIn(HttpServletRequest request) {
        if (!consoleLoginProperties.isEnabled()) return false;
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(
                consoleLoginProperties.getSessionUserIdAttribute()) != null;
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
        if (configured != null && !configured.isBlank()) return configured;
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) return origin;
        return request.getScheme() + "://" + request.getServerName()
                + ((request.getServerPort() == 80 || request.getServerPort() == 443)
                ? "" : ":" + request.getServerPort());
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
