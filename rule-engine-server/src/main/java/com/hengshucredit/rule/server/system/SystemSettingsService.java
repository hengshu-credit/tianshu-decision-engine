package com.hengshucredit.rule.server.system;

import com.hengshucredit.rule.model.ExternalCodeMapping;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.governance.GovernanceSecretCodec;
import com.hengshucredit.rule.server.governance.ResourceSnapshot;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 全局配置在数据库中按组原子保存；只有明确允许公开的品牌字段对匿名请求可见。 */
@Service
public class SystemSettingsService {
    public static final String RESOURCE_TYPE = "SYSTEM_SETTING";
    public static final Map<Long, String> GROUPS = Map.of(1L, "brand", 2L, "mail", 3L, "access", 4L, "codes");
    private static final Map<String, Object> WATERMARK_DEFAULTS = Map.of(
            "watermarkEnabled", false, "watermarkContent", "{系统名称} {用户名} {登录时间}",
            "watermarkTransparency", 88, "watermarkLayout", "DIAGONAL", "watermarkDensity", "NORMAL");
    private final JdbcTemplate jdbc;
    private final GovernanceSecretCodec secrets;
    private final RuleEngineConsoleLoginProperties login;
    private final ConfigurableEnvironment environment;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.context.ApplicationEventPublisher events;

    public SystemSettingsService(JdbcTemplate jdbc, GovernanceSecretCodec secrets,
                                 RuleEngineConsoleLoginProperties login, ConfigurableEnvironment environment) {
        this.jdbc = jdbc; this.secrets = secrets; this.login = login; this.environment = environment;
    }

    public long id(String group) {
        return GROUPS.entrySet().stream().filter(row -> row.getValue().equals(group)).mapToLong(Map.Entry::getKey)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("不支持的系统配置分组"));
    }

    public ResourceSnapshot snapshot(long id) {
        String group = group(id);
        var rows = jdbc.queryForList("SELECT config_json, secret_ciphertext, secret_digest, version FROM rule_system_setting WHERE id = ?", id);
        if (rows.isEmpty()) throw new IllegalStateException("系统配置表尚未初始化");
        var row = rows.get(0);
        Map<String, Object> value = new LinkedHashMap<>(defaults(group));
        boolean managed = deploymentManaged(group);
        if (!managed) value.putAll(CanonicalJson.readMap((String) row.get("config_json")));
        value.put("id", id); value.put("scope", "GLOBAL"); value.put("projectId", 0L);
        value.put("settingName", label(group)); value.put("group", group);
        value.put("configVersion", ((Number) row.get("version")).longValue());
        String cipher = managed ? null : (String) row.get("secret_ciphertext");
        // 兼容尚未由页面接管的邮件凭据；公开快照始终只含已配置状态。
        if (cipher == null && "mail".equals(group) && !environment.getProperty("spring.mail.password", "").isBlank()) {
            value.put("password", environment.getProperty("spring.mail.password"));
        }
        return secrets.normalize(new ResourceSnapshot(CanonicalJson.write(value), "ACTIVE", cipher, managed ? null : (String) row.get("secret_digest")), Set.of("password"));
    }

    public Map<String, Object> get(String group) { return CanonicalJson.readMap(snapshot(id(group)).snapshotJson()); }
    public Map<String, Object> mail() { return secrets.restore(snapshot(2)); }
    public Map<String, Object> mailDraft(Map<String, Object> config) {
        return mailDraft(config, null);
    }
    public Map<String, Object> mailDraft(Map<String, Object> config, ResourceSnapshot base) {
        ResourceSnapshot current = base == null ? snapshot(2) : base;
        Map<String, Object> value = new LinkedHashMap<>(CanonicalJson.readMap(current.snapshotJson()));
        if (config != null) value.putAll(config);
        if (number(value.get("id"), "配置 ID") != 2) throw new IllegalArgumentException("邮件测试配置无效");
        ResourceSnapshot draft = normalize(new ResourceSnapshot(CanonicalJson.write(value), "ACTIVE", current.secretPayloadCiphertext(), current.secretDigest()));
        validate(draft); return secrets.restore(draft);
    }
    public Map<String, Object> access() { return get("access"); }
    public ExternalCodeMapping mapping() {
        Map<String, Object> config = get("codes");
        return ExternalCodeMapping.from(((Number) config.get("configVersion")).longValue(), config);
    }

    public Map<String, Object> publicBrand() {
        Map<String, Object> brand = get("brand");
        String logo = brandImageUrl(brand, "logo");
        String favicon = brandImageUrl(brand, "favicon");
        Map<String, Object> result = new LinkedHashMap<>(Map.of("systemName", brand.get("systemName"), "subtitle", brand.get("subtitle"),
                "logoUrl", logo, "faviconUrl", favicon.isEmpty() ? logo : favicon,
                "version", brand.get("configVersion")));
        WATERMARK_DEFAULTS.keySet().forEach(key -> result.put(key, brand.get(key)));
        return result;
    }

    private String brandImageUrl(Map<String, Object> brand, String field) {
        String uploaded = assetUrl(brand.get(field + "Id"));
        return uploaded.isEmpty() ? (String) brand.get(field + "Url") : uploaded;
    }

    public ResourceSnapshot normalize(ResourceSnapshot draft) {
        Map<String, Object> value = CanonicalJson.readMap(draft.snapshotJson());
        long id = number(value.get("id"), "系统配置 ID");
        String group = group(id);
        // Older clients and saved configurations did not include image URL fields.
        if ("brand".equals(group)) {
            value.putIfAbsent("logoUrl", ""); value.putIfAbsent("faviconUrl", "");
            // 旧页面保存品牌时保留当前水印策略，避免遗漏字段导致意外关闭。
            if (WATERMARK_DEFAULTS.keySet().stream().anyMatch(key -> !value.containsKey(key))) {
                Map<String, Object> current = get("brand");
                for (String key : WATERMARK_DEFAULTS.keySet()) if (!value.containsKey(key)) value.put(key, current.get(key));
            }
        }
        Set<String> allowed = new java.util.HashSet<>(defaults(group).keySet());
        allowed.addAll(Set.of("id", "scope", "projectId", "settingName", "group", "configVersion", GovernanceSecretCodec.SECRET_STATE_KEY));
        if (value.keySet().stream().anyMatch(key -> !allowed.contains(key))) throw new IllegalArgumentException("系统配置包含未知字段");
        if (!group.equals(value.get("group")) || !"GLOBAL".equals(value.get("scope"))
                || number(value.get("projectId"), "项目范围") != 0) throw new IllegalArgumentException("系统配置必须为全局范围");
        for (String key : defaults(group).keySet()) {
            if (!"password".equals(key) && !value.containsKey(key)) throw new IllegalArgumentException("配置字段缺失: " + key);
        }
        value.put("settingName", label(group));
        return secrets.normalize(new ResourceSnapshot(CanonicalJson.write(value), "ACTIVE", draft.secretPayloadCiphertext(), draft.secretDigest()), Set.of("password"));
    }

    public void validate(ResourceSnapshot snapshot) {
        Map<String, Object> value = secrets.restore(snapshot);
        String group = group(number(value.get("id"), "系统配置 ID"));
        number(value.get("configVersion"), "配置版本");
        switch (group) {
            case "brand" -> {
                bool(value, "watermarkEnabled");
                text(value, "watermarkContent", 200, true);
                long transparency = number(value.get("watermarkTransparency"), "水印透明度");
                if (transparency > 100) throw new IllegalArgumentException("水印透明度应为 0–100 的整数");
                if (!(value.get("watermarkLayout") instanceof String layout) || !Set.of("DIAGONAL", "HORIZONTAL", "VERTICAL").contains(layout)) throw new IllegalArgumentException("水印排列方式无效");
                if (!(value.get("watermarkDensity") instanceof String density) || !Set.of("SPARSE", "NORMAL", "DENSE").contains(density)) throw new IllegalArgumentException("水印密度无效");
                text(value, "systemName", 40, true); text(value, "subtitle", 100, false);
                requireAsset(value.get("logoId")); requireAsset(value.get("faviconId"));
                for (String field : List.of("logo", "favicon")) {
                    String url = text(value, field + "Url", 2048, false);
                    validateImageUrl(url);
                    if (!url.isEmpty() && !String.valueOf(value.get(field + "Id")).isEmpty()) throw new IllegalArgumentException("图片地址与上传图片只能选择一种");
                }
            }
            case "mail" -> {
                text(value, "email", 256, false); text(value, "host", 253, false); text(value, "password", 4096, false);
                int port = Math.toIntExact(number(value.get("port"), "SMTP 端口"));
                if (port < 1 || port > 65535) throw new IllegalArgumentException("SMTP 端口必须为 1–65535");
                if (!Set.of("STARTTLS", "TLS").contains(value.get("encryption"))) throw new IllegalArgumentException("请选择 STARTTLS 或 SSL/TLS");
                String email = (String) value.get("email"), host = (String) value.get("host");
                if (!email.isEmpty() || !host.isEmpty()) {
                    if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("通知邮箱格式无效");
                    if (host.isBlank() || host.matches(".*[\\s/\\\\].*")) throw new IllegalArgumentException("SMTP 主机不能包含协议、路径或空白");
                    if (!(value.get("password") instanceof String password) || password.isBlank()) throw new IllegalArgumentException("请填写邮箱授权码");
                }
                ensureEditable(group);
            }
            case "access" -> {
                bool(value, "registrationEnabled"); bool(value, "passwordRecoveryEnabled");
                String base = text(value, "baseUrl", 2048, false);
                if (!base.isEmpty()) validateBaseUrl(base);
                for (String key : List.of("verificationTtlMinutes", "passwordResetTtlMinutes")) {
                    long ttl = number(value.get(key), "链接有效期");
                    if (ttl < 5 || ttl > 1440) throw new IllegalArgumentException("链接有效期应为 5–1440 分钟");
                }
                if (Boolean.TRUE.equals(value.get("registrationEnabled")) || Boolean.TRUE.equals(value.get("passwordRecoveryEnabled"))) {
                    if (base.isBlank()) throw new IllegalArgumentException("请配置控制台访问地址");
                    Map<String, Object> mail = mail();
                    if (String.valueOf(mail.get("email")).isBlank() || String.valueOf(mail.get("host")).isBlank()) {
                        throw new IllegalArgumentException("请先配置并启用通知邮箱");
                    }
                }
                ensureEditable(group);
            }
            case "codes" -> ExternalCodeMapping.from(0, value);
            default -> throw new IllegalArgumentException("不支持的配置分组");
        }
    }

    @Transactional
    public Map<String, Object> saveBrand(Map<String, Object> value, String actor) {
        if (value == null) throw new IllegalArgumentException("品牌配置不能为空");
        if (number(value.get("id"), "配置 ID") != 1) throw new IllegalArgumentException("只允许直接保存品牌配置");
        ResourceSnapshot snapshot = normalize(ResourceSnapshot.ofJson(CanonicalJson.write(value)));
        validate(snapshot); persist(snapshot, actor); return publicBrand();
    }

    public void persist(ResourceSnapshot snapshot, String actor) {
        Map<String, Object> value = CanonicalJson.readMap(snapshot.snapshotJson());
        long id = number(value.get("id"), "配置 ID"), version = number(value.get("configVersion"), "配置版本");
        if (jdbc.update("UPDATE rule_system_setting SET config_json = ?, secret_ciphertext = ?, secret_digest = ?, version = version + 1, update_by = ?, update_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?",
                snapshot.snapshotJson(), snapshot.secretPayloadCiphertext(), snapshot.secretDigest(), actor, id, version) != 1) {
            throw new IllegalArgumentException("系统配置已被更新，请重新加载后再保存");
        }
        if (events != null) events.publishEvent(new SystemNotification("系统配置变更通知", label(group(id)) + "已更新并生效，请登录控制台查看变更记录。"));
    }

    public boolean deploymentManaged(String group) {
        if (!Set.of("mail", "access").contains(group)) return false;
        String[] keys = "mail".equals(group) ? new String[]{"CONSOLE_MAIL_FROM", "CONSOLE_SMTP_HOST", "CONSOLE_SMTP_PORT", "CONSOLE_SMTP_PASSWORD", "CONSOLE_SMTP_USERNAME", "CONSOLE_SMTP_STARTTLS", "spring.mail.host", "spring.mail.port", "spring.mail.username", "spring.mail.password", "rule-engine.console-login.mail.from"}
                : new String[]{"CONSOLE_REGISTRATION_ENABLED", "CONSOLE_PASSWORD_RECOVERY_ENABLED", "CONSOLE_MAIL_BASE_URL", "CONSOLE_VERIFICATION_TTL_MINUTES", "CONSOLE_PASSWORD_RESET_TTL_MINUTES", "rule-engine.console-login.registration-enabled", "rule-engine.console-login.password-recovery-enabled", "rule-engine.console-login.mail.base-url", "rule-engine.console-login.mail.verification-ttl-minutes", "rule-engine.console-login.mail.password-reset-ttl-minutes"};
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!Set.of("systemEnvironment", "systemProperties", "commandLineArgs").contains(source.getName())) continue;
            for (String key : keys) if (source.getProperty(key) != null && !source.getProperty(key).toString().isBlank()) return true;
        }
        return false;
    }

    private void ensureEditable(String group) { if (deploymentManaged(group)) throw new IllegalArgumentException("当前分组由部署环境覆盖，请先移除环境覆盖再通过页面维护"); }
    private String group(long id) { String group = GROUPS.get(id); if (group == null) throw new IllegalArgumentException("系统配置不存在"); return group; }
    private String label(String group) { return Map.of("brand", "基本信息", "mail", "通知邮箱", "access", "登录与安全", "codes", "对外结果码").get(group); }
    private String assetUrl(Object id) { return id == null || id.toString().isEmpty() ? "" : "/api/auth/console/brand/assets/" + id; }
    private void requireAsset(Object id) {
        if (!(id instanceof String text)) throw new IllegalArgumentException("图片引用必须为资源 ID");
        if (text.isEmpty()) return;
        if (jdbc.queryForObject("SELECT COUNT(*) FROM rule_system_asset WHERE id = ?", Long.class, text) != 1L) throw new IllegalArgumentException("图片资源不存在");
    }

    private Map<String, Object> defaults(String group) {
        return switch (group) {
            case "brand" -> {
                Map<String, Object> brand = new LinkedHashMap<>(Map.of("systemName", "天枢决策引擎", "subtitle", "天工开物，枢衡定策", "logoId", "", "faviconId", "", "logoUrl", "", "faviconUrl", ""));
                brand.putAll(WATERMARK_DEFAULTS);
                yield brand;
            }
            case "mail" -> Map.of("email", login.getMail().getFrom(), "host", environment.getProperty("spring.mail.host", ""),
                    "port", environment.getProperty("spring.mail.port", Integer.class, 587), "encryption", environment.getProperty("spring.mail.properties.mail.smtp.ssl.enable", Boolean.class, false) ? "TLS" : "STARTTLS", "password", "");
            case "access" -> Map.of("registrationEnabled", login.isRegistrationEnabled(), "passwordRecoveryEnabled", login.isPasswordRecoveryEnabled(),
                    "baseUrl", login.getMail().getBaseUrl(), "verificationTtlMinutes", login.getMail().getVerificationTtlMinutes(), "passwordResetTtlMinutes", login.getMail().getPasswordResetTtlMinutes());
            case "codes" -> Map.of("enabled", false, "mappings", Map.of());
            default -> throw new IllegalArgumentException("配置分组不存在");
        };
    }

    public static void validateBaseUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!Set.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) { throw new IllegalArgumentException("控制台地址必须是无凭据、查询串及片段的 HTTP(S) 地址"); }
    }
    public static void validateImageUrl(String value) {
        if (value.isEmpty()) return;
        try {
            URI uri = URI.create(value);
            if (value.contains("\\") || value.chars().anyMatch(Character::isWhitespace) || uri.getUserInfo() != null) throw new IllegalArgumentException();
            boolean local = value.startsWith("/") && !value.startsWith("//") && uri.getRawAuthority() == null;
            boolean remote = ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null;
            if (!local && !remote) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) { throw new IllegalArgumentException("图片地址必须是 HTTP(S) 地址或以 / 开头的本站路径，不能包含登录凭据"); }
    }
    private static void bool(Map<String, Object> value, String key) { if (!(value.get(key) instanceof Boolean)) throw new IllegalArgumentException(key + " 必须为布尔值"); }
    public static long number(Object value, String name) {
        if (!(value instanceof Number number) || number.doubleValue() != number.longValue() || number.longValue() < 0) throw new IllegalArgumentException(name + " 必须是非负整数");
        return number.longValue();
    }
    private static String text(Map<String, Object> value, String key, int max, boolean required) {
        Object raw = value.get(key);
        if (raw == null && "password".equals(key)) return "";
        if (!(raw instanceof String text) || text.length() > max || required && text.isBlank()) throw new IllegalArgumentException(key + " 格式或长度无效");
        return text;
    }
}
