package com.hengshucredit.rule.server.auth;

/**
 * 认证 ID 维度的跨实例执行保护存储。
 *
 * 实现必须保证 acquire/renew/release 按 permitId 原子执行。Redis 不可用时
 * 应抛出 {@link DistributedUnavailable}，调用方不得静默退回本机限流。
 */
public interface ProjectExecutionGuardStore {

    AcquireResult acquire(Long authId, ProjectAccessPolicy policy, String permitId);

    boolean renew(Long authId, String permitId, long leaseMillis, long hardExpiryMillis);

    boolean release(Long authId, String permitId);

    final class AcquireResult {
        private final boolean allowed;
        private final ProjectExecutionGuard.Reason reason;
        private final long leaseMillis;
        private final long hardExpiryMillis;

        private AcquireResult(boolean allowed, ProjectExecutionGuard.Reason reason,
                              long leaseMillis, long hardExpiryMillis) {
            this.allowed = allowed;
            this.reason = reason;
            this.leaseMillis = leaseMillis;
            this.hardExpiryMillis = hardExpiryMillis;
        }

        public static AcquireResult allowed(long leaseMillis, long hardExpiryMillis) {
            return new AcquireResult(true, null, leaseMillis, hardExpiryMillis);
        }

        public static AcquireResult rejected(ProjectExecutionGuard.Reason reason) {
            return new AcquireResult(false, reason, 0L, 0L);
        }

        public boolean isAllowed() { return allowed; }
        public ProjectExecutionGuard.Reason getReason() { return reason; }
        public long getLeaseMillis() { return leaseMillis; }
        public long getHardExpiryMillis() { return hardExpiryMillis; }
    }

    /** Redis 连接、脚本执行或返回值异常时使用。 */
    class DistributedUnavailable extends RuntimeException {
        public DistributedUnavailable(Throwable cause) {
            super("分布式执行保护存储不可用", cause);
        }
    }
}
