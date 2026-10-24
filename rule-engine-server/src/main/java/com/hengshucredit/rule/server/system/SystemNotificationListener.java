package com.hengshucredit.rule.server.system;

import com.hengshucredit.rule.model.entity.ConsoleOperationLog;
import com.hengshucredit.rule.server.audit.ConsoleOperationLogService;
import com.hengshucredit.rule.server.consolelogin.ConsoleMailService;
import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class SystemNotificationListener {
    private final JdbcTemplate jdbc;
    private final ConsoleMailService mail;
    private final ConsoleOperationLogService logs;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), task -> { Thread thread = new Thread(task, "system-notification-mail"); thread.setDaemon(true); return thread; });
    public SystemNotificationListener(JdbcTemplate jdbc, ConsoleMailService mail, ConsoleOperationLogService logs) { this.jdbc = jdbc; this.mail = mail; this.logs = logs; }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void changed(SystemNotification event) {
        try { executor.execute(() -> send(event)); }
        catch (java.util.concurrent.RejectedExecutionException error) { record(event.title(), false); }
    }
    private void send(SystemNotification event) {
        try {
            if (!mail.configured()) return;
            var recipients = jdbc.queryForList("SELECT DISTINCT u.email FROM console_user u JOIN console_user_role ur ON ur.user_id = u.id JOIN console_role r ON r.id = ur.role_id WHERE r.role_code = 'SUPER_ADMIN' AND r.status = 1 AND u.status = 1 AND u.merged_into_user_id IS NULL AND u.email_verified_at IS NOT NULL AND u.email IS NOT NULL", String.class);
            for (String email : recipients) mail.sendSystemNotification(email, event.title(), event.text());
            if (!recipients.isEmpty()) record(event.title(), true);
        } catch (RuntimeException error) { record(event.title(), false); }
    }
    private void record(String title, boolean success) {
        var log = new ConsoleOperationLog(); log.setUsername("SYSTEM"); log.setModule("system"); log.setAction("NOTIFY");
        log.setTargetType("SYSTEM_SETTING"); log.setTargetId("1"); log.setTargetName("系统邮件通知");
        log.setRequestMethod("INTERNAL"); log.setRequestPath("/api/rule/system/settings/notifications");
        log.setChangeSummary(title + (success ? "：邮件已提交 SMTP 服务" : "：邮件发送失败，请检查通知邮箱配置"));
        log.setOutcome(success ? "SUCCESS" : "FAILED"); log.setResultCode(success ? 200 : 502); log.setDurationMs(0L);
        try { logs.append(log); } catch (RuntimeException error) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("系统通知结果记录失败"); }
    }
    @PreDestroy public void close() { executor.shutdownNow(); }
}
