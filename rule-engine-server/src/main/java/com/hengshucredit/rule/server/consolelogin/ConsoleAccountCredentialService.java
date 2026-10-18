package com.hengshucredit.rule.server.consolelogin;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hengshucredit.rule.model.entity.ConsoleAccountToken;
import com.hengshucredit.rule.model.entity.ConsoleSecurityAuditLog;
import com.hengshucredit.rule.model.entity.ConsoleUser;
import com.hengshucredit.rule.server.mapper.ConsoleAccountTokenMapper;
import com.hengshucredit.rule.server.mapper.ConsoleSecurityAuditLogMapper;
import com.hengshucredit.rule.server.mapper.ConsoleRoleMapper;
import com.hengshucredit.rule.server.mapper.ConsoleUserMapper;
import com.hengshucredit.rule.server.mapper.ConsoleUserRoleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ConsoleAccountCredentialService {

    private static final String EMAIL_VERIFY = "EMAIL_VERIFY";
    private static final String PASSWORD_RESET = "PASSWORD_RESET";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private ConsoleUserMapper userMapper;
    @Resource
    private ConsoleAccountTokenMapper tokenMapper;
    @Resource
    private ConsoleSecurityAuditLogMapper auditLogMapper;
    @Resource
    private ConsoleRoleMapper roleMapper;
    @Resource
    private ConsoleUserRoleMapper userRoleMapper;
    @Resource
    private ConsoleMailService mailService;
    @Resource
    private RuleEngineConsoleLoginProperties properties;

    public boolean mailConfigured() {
        return mailService.configured();
    }

    public boolean breakGlassConfigured() {
        return properties.isBreakGlassRecoveryEnabled()
                && properties.getBreakGlassRecoveryKey() != null
                && !properties.getBreakGlassRecoveryKey().isBlank();
    }

    @Transactional
    public void register(String username, String displayName, String email,
                         String password, String baseUrl, String requestIp) {
        if (!properties.isRegistrationEnabled()) {
            throw new IllegalStateException("控制台注册未启用");
        }
        ensureMailConfigured("注册邮件服务未配置");
        String normalizedUsername = requireUsername(username);
        String normalizedEmail = requireEmail(email);
        requirePassword(password);
        if (userMapper.selectOne(new LambdaQueryWrapper<ConsoleUser>()
                .eq(ConsoleUser::getUsername, normalizedUsername)
                .last("LIMIT 1")) != null) {
            throw new IllegalArgumentException("用户名或邮箱不可用");
        }
        if (userMapper.selectOne(new LambdaQueryWrapper<ConsoleUser>()
                .eq(ConsoleUser::getEmail, normalizedEmail)
                .last("LIMIT 1")) != null) {
            throw new IllegalArgumentException("用户名或邮箱不可用");
        }
        ConsoleUser user = new ConsoleUser();
        user.setUsername(normalizedUsername);
        user.setDisplayName(displayName == null || displayName.isBlank()
                ? normalizedUsername : displayName.trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(BCrypt.hashpw(password));
        user.setRegistrationStatus("PENDING");
        user.setRegistrationSource("SELF_REGISTER");
        user.setStatus(0);
        user.setPermissionVersion(1L);
        user.setCreateBy(normalizedUsername);
        user.setUpdateBy(normalizedUsername);
        userMapper.insert(user);
        String token = issueToken(user.getId(), EMAIL_VERIFY,
                properties.getMail().getVerificationTtlMinutes(), requestIp);
        mailService.sendVerification(normalizedEmail, user.getDisplayName(),
                buildLink(baseUrl, "/register", token));
        appendAudit(user, "REGISTER_REQUEST", requestIp);
    }

    @Transactional
    public void verifyEmail(String token, String requestIp) {
        ConsoleAccountToken accountToken = requireToken(token, EMAIL_VERIFY);
        ConsoleUser user = requireUser(accountToken.getUserId());
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setRegistrationStatus("ACTIVE");
        user.setStatus(1);
        user.setUpdateBy(user.getUsername());
        userMapper.updateById(user);
        consume(accountToken);
        appendAudit(user, "EMAIL_VERIFY", requestIp);
    }

    @Transactional
    public void requestPasswordReset(String identifier, String baseUrl,
                                      String requestIp) {
        if (!properties.isPasswordRecoveryEnabled()) return;
        ensureMailConfigured("密码找回邮件服务未配置");
        String value = identifier == null ? "" : identifier.trim();
        ConsoleUser user = value.isEmpty() ? null
                : userMapper.selectOne(new LambdaQueryWrapper<ConsoleUser>()
                .and(wrapper -> wrapper.eq(ConsoleUser::getUsername, value)
                        .or().eq(ConsoleUser::getEmail,
                                value.toLowerCase(Locale.ROOT)))
                .eq(ConsoleUser::getStatus, 1)
                .eq(ConsoleUser::getRegistrationStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (user == null || user.getEmail() == null
                || user.getEmail().isBlank()
                || user.getEmailVerifiedAt() == null) return;
        String token = issueToken(user.getId(), PASSWORD_RESET,
                properties.getMail().getPasswordResetTtlMinutes(), requestIp);
        mailService.sendPasswordReset(user.getEmail(), user.getDisplayName(),
                buildLink(baseUrl, "/password-reset", token));
        appendAudit(user, "PASSWORD_RESET_REQUEST", requestIp);
    }

    @Transactional
    public void resetPassword(String token, String password, String requestIp) {
        requirePassword(password);
        ConsoleAccountToken accountToken = requireToken(token, PASSWORD_RESET);
        ConsoleUser user = requireUser(accountToken.getUserId());
        user.setPasswordHash(BCrypt.hashpw(password));
        user.setPermissionVersion(nextVersion(user));
        user.setUpdateBy(user.getUsername());
        userMapper.updateById(user);
        consume(accountToken);
        appendAudit(user, "PASSWORD_RESET_SUCCESS", requestIp);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword,
                               String password, String requestIp) {
        requirePassword(password);
        ConsoleUser user = requireUser(userId);
        if (!Integer.valueOf(1).equals(user.getStatus())
                || !BCrypt.checkpw(currentPassword == null ? "" : currentPassword,
                user.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码错误");
        }
        user.setPasswordHash(BCrypt.hashpw(password));
        user.setPermissionVersion(nextVersion(user));
        user.setUpdateBy(user.getUsername());
        userMapper.updateById(user);
        appendAudit(user, "PASSWORD_CHANGE", requestIp);
    }

    @Transactional
    public void emergencyReset(String username, String recoveryKey,
                               String password, String requestIp) {
        requirePassword(password);
        if (!breakGlassConfigured()) {
            throw new IllegalStateException("超级管理员应急恢复未启用");
        }
        ConsoleUser user = username == null ? null
                : userMapper.selectOne(new LambdaQueryWrapper<ConsoleUser>()
                .eq(ConsoleUser::getUsername, username.trim())
                .last("LIMIT 1"));
        if (user == null || !isSuperAdministrator(user.getId())
                || !constantTimeEquals(properties.getBreakGlassRecoveryKey(), recoveryKey)) {
            throw new IllegalArgumentException("应急恢复信息无效");
        }
        user.setPasswordHash(BCrypt.hashpw(password));
        user.setPermissionVersion(nextVersion(user));
        user.setUpdateBy(user.getUsername());
        userMapper.updateById(user);
        appendAudit(user, "SUPER_ADMIN_EMERGENCY_PASSWORD_RESET", requestIp);
    }

    public Profile profile(Long userId) {
        ConsoleUser user = requireUser(userId);
        return new Profile(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getEmail(), user.getEmailVerifiedAt(),
                user.getRegistrationStatus());
    }

    @Transactional
    public Profile updateProfile(Long userId, String displayName, String email,
                                 String baseUrl, String requestIp) {
        ConsoleUser user = requireUser(userId);
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("显示名称不能为空");
        }
        user.setDisplayName(displayName.trim());
        String normalizedEmail = email == null || email.isBlank()
                ? null : requireEmail(email);
        boolean emailChanged = normalizedEmail != null
                && !normalizedEmail.equalsIgnoreCase(user.getEmail());
        if (emailChanged) {
            ConsoleUser existing = userMapper.selectOne(new LambdaQueryWrapper<ConsoleUser>()
                    .eq(ConsoleUser::getEmail, normalizedEmail)
                    .ne(ConsoleUser::getId, userId)
                    .last("LIMIT 1"));
            if (existing != null) throw new IllegalArgumentException("邮箱已被其他账户使用");
            ensureMailConfigured("注册邮件服务未配置");
            user.setEmail(normalizedEmail);
            user.setEmailVerifiedAt(null);
        }
        user.setUpdateBy(user.getUsername());
        userMapper.updateById(user);
        appendAudit(user, "PROFILE_UPDATE", requestIp);
        if (emailChanged) {
            String token = issueToken(user.getId(), EMAIL_VERIFY,
                    properties.getMail().getVerificationTtlMinutes(), requestIp);
            mailService.sendVerification(normalizedEmail, user.getDisplayName(),
                    buildLink(baseUrl, "/register", token));
        }
        return profile(userId);
    }

    private String issueToken(Long userId, String purpose, int ttlMinutes,
                              String requestIp) {
        tokenMapper.update(null, new LambdaUpdateWrapper<ConsoleAccountToken>()
                .eq(ConsoleAccountToken::getUserId, userId)
                .eq(ConsoleAccountToken::getPurpose, purpose)
                .isNull(ConsoleAccountToken::getConsumedAt)
                .set(ConsoleAccountToken::getConsumedAt, LocalDateTime.now()));
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ConsoleAccountToken row = new ConsoleAccountToken();
        row.setUserId(userId);
        row.setPurpose(purpose);
        row.setTokenHash(sha256(token));
        row.setExpiresAt(LocalDateTime.now().plusMinutes(Math.max(5, ttlMinutes)));
        row.setRequestIp(requestIp);
        tokenMapper.insert(row);
        return token;
    }

    private ConsoleAccountToken requireToken(String token, String purpose) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("链接无效或已过期");
        }
        ConsoleAccountToken row = tokenMapper.selectOne(
                new LambdaQueryWrapper<ConsoleAccountToken>()
                        .eq(ConsoleAccountToken::getTokenHash, sha256(token))
                        .eq(ConsoleAccountToken::getPurpose, purpose)
                        .isNull(ConsoleAccountToken::getConsumedAt)
                        .gt(ConsoleAccountToken::getExpiresAt, LocalDateTime.now())
                        .last("LIMIT 1"));
        if (row == null) throw new IllegalArgumentException("链接无效或已过期");
        return row;
    }

    private void consume(ConsoleAccountToken token) {
        token.setConsumedAt(LocalDateTime.now());
        tokenMapper.updateById(token);
    }

    private ConsoleUser requireUser(Long userId) {
        ConsoleUser user = userMapper.selectById(userId);
        if (user == null) throw new IllegalArgumentException("账户不存在");
        return user;
    }

    private String requireUsername(String username) {
        String value = username == null ? "" : username.trim();
        if (value.length() < 3 || value.length() > 64) {
            throw new IllegalArgumentException("用户名长度必须为3到64位");
        }
        return value;
    }

    private String requireEmail(String email) {
        String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(value).matches() || value.length() > 256) {
            throw new IllegalArgumentException("请输入有效邮箱");
        }
        return value;
    }

    private void requirePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("密码不能少于8位");
        }
        if (password.length() > 256) {
            throw new IllegalArgumentException("密码不能超过256位");
        }
    }

    private void ensureMailConfigured(String message) {
        if (!mailService.configured()) throw new IllegalStateException(message);
    }

    private boolean isSuperAdministrator(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<com.hengshucredit.rule.model.entity.ConsoleUserRole>()
                        .eq(com.hengshucredit.rule.model.entity.ConsoleUserRole::getUserId, userId))
                .stream()
                .map(com.hengshucredit.rule.model.entity.ConsoleUserRole::getRoleId)
                .map(roleMapper::selectById)
                .anyMatch(role -> role != null && "SUPER_ADMIN".equals(role.getRoleCode()));
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String buildLink(String baseUrl, String path, String token) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.isEmpty()) throw new IllegalStateException("邮件链接地址未配置");
        return base.replaceAll("/+$", "") + "/#" + path + "?token=" + token;
    }

    private long nextVersion(ConsoleUser user) {
        return user.getPermissionVersion() == null
                ? 1L : user.getPermissionVersion() + 1L;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256不可用", impossible);
        }
    }

    private void appendAudit(ConsoleUser user, String action, String requestIp) {
        if (auditLogMapper == null) return;
        ConsoleSecurityAuditLog log = new ConsoleSecurityAuditLog();
        log.setUserId(user.getId());
        log.setUsername(user.getUsername());
        log.setAction(action);
        log.setTargetType("CONSOLE_USER");
        log.setTargetId(String.valueOf(user.getId()));
        log.setClientIp(requestIp);
        auditLogMapper.insert(log);
    }

    public record Profile(Long userId, String username, String displayName,
                          String email, LocalDateTime emailVerifiedAt,
                          String registrationStatus) {
    }
}
