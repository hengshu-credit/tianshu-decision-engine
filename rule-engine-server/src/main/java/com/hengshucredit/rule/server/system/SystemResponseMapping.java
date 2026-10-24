package com.hengshucredit.rule.server.system;

import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.ExternalCodeMapping;
import org.springframework.stereotype.Component;
import java.util.function.BiConsumer;

@Component
public class SystemResponseMapping {
    private final SystemSettingsService settings;
    private volatile ExternalCodeMapping cached = ExternalCodeMapping.DEFAULT;
    private volatile long expiresAt;
    public SystemResponseMapping(SystemSettingsService settings) { this.settings = settings; }
    public ExternalCodeMapping current() {
        if (System.nanoTime() < expiresAt) return cached;
        synchronized (this) {
            if (System.nanoTime() >= expiresAt) {
                try { cached = settings.mapping(); }
                catch (RuntimeException error) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("结果码映射读取失败，保留上一生效版本 {}", cached.version()); }
                expiresAt = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            }
            return cached;
        }
    }
    @org.springframework.transaction.event.TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void changed(SystemNotification notification) { expiresAt = 0; }
    public <T> ApiResponse<T> apply(ApiResponse<T> response, BiConsumer<String, String> header) {
        ExternalCodeMapping mapping = current();
        if (mapping.enabled()) {
            header.accept(ExternalCodeMapping.STANDARD_CODE_HEADER, response.status().code());
            header.accept(ExternalCodeMapping.VERSION_HEADER, Long.toString(mapping.version()));
        }
        return response.mapExternal(mapping);
    }
    public static boolean externalPath(String path) {
        return path != null && (path.startsWith("/api/rule/sync/") || path.startsWith("/api/sync/")
                || path.startsWith("/api/rule/runtime/") || path.startsWith("/api/rule/openapi/")
                || path.equals("/api/rule/auth/token") || path.equals("/api/rule/log/report"));
    }
}
