package com.hengshucredit.rule.server.consolelogin;

import com.hengshucredit.rule.server.system.SystemBrandAssetService;
import com.hengshucredit.rule.server.system.SystemSettingsService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConsoleMailService {
    @Autowired(required = false) private JavaMailSender mailSender;
    @Autowired(required = false) private SystemSettingsService settings;
    @Autowired(required = false) private SystemBrandAssetService assets;
    @Resource private RuleEngineConsoleLoginProperties properties;

    public boolean configured() {
        if (settings == null) return mailSender != null && properties.getMail() != null
                && properties.getMail().getFrom() != null && !properties.getMail().getFrom().isBlank();
        Map<String, Object> config = settings.mail();
        return !String.valueOf(config.get("email")).isBlank() && !String.valueOf(config.get("host")).isBlank()
                && config.get("password") instanceof String password && !password.isBlank();
    }
    public void sendVerification(String email, String username, String link) {
        send(email, "账户邮箱验证", username, "请验证您的邮箱以完成账户激活，之后由管理员为您分配业务权限。",
                link, "验证邮箱并激活", verificationTtlMinutes(), null);
    }
    public void sendPasswordReset(String email, String username, String link) {
        send(email, "密码重置通知", username, "我们收到了您的密码重置请求，请点击下方按钮设置新密码。",
                link, "设置新密码", passwordResetTtlMinutes(), null);
    }
    public void test(String recipient, Map<String, Object> config) {
        test(recipient, config, null);
    }
    public void test(String recipient, Map<String, Object> config, com.hengshucredit.rule.server.governance.ResourceSnapshot base) {
        if (settings == null) throw new IllegalStateException("邮件配置服务未就绪");
        send(recipient, "通知邮箱测试", "管理员", "您已收到通知邮箱测试邮件，系统后续通知将使用同一邮箱及品牌版式。",
                null, null, 0, settings.mailDraft(config, base));
    }
    public void sendSystemNotification(String email, String title, String text) {
        send(email, title, "管理员", text, null, null, 0, null);
    }
    public String baseUrl() { return settings == null ? properties.getMail().getBaseUrl() : (String) settings.access().get("baseUrl"); }
    public boolean registrationEnabled() { return settings == null ? properties.isRegistrationEnabled() : Boolean.TRUE.equals(settings.access().get("registrationEnabled")); }
    public boolean passwordRecoveryEnabled() { return settings == null ? properties.isPasswordRecoveryEnabled() : Boolean.TRUE.equals(settings.access().get("passwordRecoveryEnabled")); }
    public int verificationTtlMinutes() { return settings == null ? properties.getMail().getVerificationTtlMinutes() : ((Number) settings.access().get("verificationTtlMinutes")).intValue(); }
    public int passwordResetTtlMinutes() { return settings == null ? properties.getMail().getPasswordResetTtlMinutes() : ((Number) settings.access().get("passwordResetTtlMinutes")).intValue(); }

    private void send(String email, String title, String username, String text, String link, String action, int ttl, Map<String, Object> draft) {
        if (email == null || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("收件邮箱格式无效");
        if (draft == null && !configured()) throw new IllegalStateException("通知邮箱尚未配置");
        Map<String, Object> brand = settings == null ? Map.of("systemName", "天枢决策引擎", "subtitle", "天工开物，枢衡定策") : settings.publicBrand();
        Map<String, Object> config = draft == null && settings != null ? settings.mail() : draft;
        JavaMailSender sender = config == null ? mailSender : sender(config);
        String from = config == null ? properties.getMail().getFrom() : (String) config.get("email");
        String name = (String) brand.get("systemName");
        String logoSource = "cid:tianshu-logo";
        if (settings != null) {
            String url = (String) settings.get("brand").get("logoUrl");
            if (url != null && !url.isEmpty()) {
                if (url.startsWith("/") && !baseUrl().isBlank()) logoSource = java.net.URI.create(baseUrl()).resolve(url).toString();
                else if (!url.startsWith("/")) logoSource = url;
            }
        }
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(from, name); helper.setTo(email); helper.setSubject(name + " · " + title);
            String plain = title + "\n\n尊敬的" + username + "：\n" + text
                    + (link == null ? "" : "\n" + link + "\n链接有效期：" + ttl + " 分钟") + "\n\n上海衡枢真信科技有限公司\nhttps://www.hengshucredit.com";
            helper.setText(plain, html(name, (String) brand.get("subtitle"), title, username, text, link, action, ttl, logoSource));
            if (assets != null && "cid:tianshu-logo".equals(logoSource)) helper.addInline("tianshu-logo", new ByteArrayResource(assets.logo()), "image/png");
            sender.send(message);
        } catch (org.springframework.mail.MailException | jakarta.mail.MessagingException | java.io.IOException error) {
            throw new IllegalStateException("邮件发送失败，请检查服务器地址、端口、加密方式及授权码", error);
        }
    }

    private JavaMailSender sender(Map<String, Object> config) {
        var sender = new JavaMailSenderImpl();
        sender.setHost((String) config.get("host")); sender.setPort(((Number) config.get("port")).intValue());
        sender.setUsername((String) config.get("email")); sender.setPassword((String) config.get("password"));
        var props = sender.getJavaMailProperties(); props.setProperty("mail.smtp.auth", "true");
        boolean tls = "TLS".equals(config.get("encryption"));
        props.setProperty("mail.smtp.ssl.enable", String.valueOf(tls));
        props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        props.setProperty("mail.smtp.starttls.enable", String.valueOf(!tls));
        props.setProperty("mail.smtp.starttls.required", String.valueOf(!tls));
        props.setProperty("mail.smtp.connectiontimeout", "10000"); props.setProperty("mail.smtp.timeout", "10000"); props.setProperty("mail.smtp.writetimeout", "10000");
        return sender;
    }

    static String html(String name, String subtitle, String title, String username, String text, String link, String action, int ttl) throws java.io.IOException {
        return html(name, subtitle, title, username, text, link, action, ttl, "cid:tianshu-logo");
    }
    static String html(String name, String subtitle, String title, String username, String text, String link, String action, int ttl, String logoSource) throws java.io.IOException {
        if (!"cid:tianshu-logo".equals(logoSource)) SystemSettingsService.validateImageUrl(logoSource);
        if (link != null) {
            var uri = java.net.URI.create(link);
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException("邮件操作链接无效");
        }
        String button = link == null ? "" : "<p style=\"padding:10px 12px;background:#f3f3ff;border-left:3px solid #2934ea\">链接有效期：" + ttl
                + " 分钟</p><p><a href=\"" + escape(link) + "\" style=\"display:block;padding:12px;background:#2934ea;color:#fff;text-align:center;text-decoration:none;border-radius:4px\">"
                + escape(action) + "</a></p><p style=\"font-size:12px;color:#657087\">若非您本人操作，请忽略此邮件。请勿向他人转发此链接。</p>";
        String template = new ClassPathResource("brand/notification.html").getContentAsString(StandardCharsets.UTF_8);
        Map<String, String> values = Map.of("name", escape(name), "subtitle", escape(subtitle), "title", escape(title),
                "username", escape(username), "text", escape(text), "action", button, "logo", escape(logoSource));
        Matcher matcher = Pattern.compile("\\{\\{([a-z]+)}}").matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(result, Matcher.quoteReplacement(values.getOrDefault(matcher.group(1), "")));
        matcher.appendTail(result); return result.toString();
    }
    private static String escape(String value) { return HtmlUtils.htmlEscape(value == null ? "" : value, StandardCharsets.UTF_8.name()); }
}
