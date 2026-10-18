package com.hengshucredit.rule.server.consolelogin;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class ConsoleMailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Resource
    private RuleEngineConsoleLoginProperties properties;

    public boolean configured() {
        return mailSender != null
                && properties.getMail() != null
                && properties.getMail().getFrom() != null
                && !properties.getMail().getFrom().isBlank();
    }

    public void sendVerification(String email, String username, String link) {
        send(email, "天枢决策引擎邮箱验证", "您好，" + username + "：\n\n"
                + "请点击以下链接验证邮箱并激活账户：\n" + link
                + "\n\n链接将在 " + properties.getMail().getVerificationTtlMinutes()
                + " 分钟后失效。若非本人操作，请忽略此邮件。", "邮箱验证服务未配置");
    }

    public void sendPasswordReset(String email, String username, String link) {
        send(email, "天枢决策引擎密码重置", "您好，" + username + "：\n\n"
                + "请点击以下链接设置新密码：\n" + link
                + "\n\n链接将在 " + properties.getMail().getPasswordResetTtlMinutes()
                + " 分钟后失效。若非本人操作，请忽略此邮件。", "密码找回邮件服务未配置");
    }

    private void send(String email, String subject, String text,
                      String unavailableMessage) {
        if (!configured()) throw new IllegalStateException(unavailableMessage);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMail().getFrom());
        message.setTo(email);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
