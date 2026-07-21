package com.hengshucredit.rule.server.service;

import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

/** 当前同步请求的单调时钟截止时间。 */
public final class RequestDeadlineContext {
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();
    private static volatile LongSupplier nanoTime = System::nanoTime;

    private RequestDeadlineContext() {
    }

    public static void start(int timeoutMs) {
        startAt(nanoTime.getAsLong(), timeoutMs);
    }

    public static long currentNanoTime() {
        return nanoTime.getAsLong();
    }

    public static void startAt(long startedAtNanos, int timeoutMs) {
        if (timeoutMs > 0) DEADLINE.set(startedAtNanos + timeoutMs * 1_000_000L);
        else DEADLINE.remove();
    }

    public static Scope limit(int timeoutMs) {
        Long previous = DEADLINE.get();
        if (timeoutMs > 0) {
            long limited = nanoTime.getAsLong() + timeoutMs * 1_000_000L;
            if (previous == null || limited < previous) DEADLINE.set(limited);
        }
        return new Scope(previous);
    }

    public static int remainingMillis() {
        Long deadline = DEADLINE.get();
        if (deadline == null) return Integer.MAX_VALUE;
        long remaining = deadline - nanoTime.getAsLong();
        if (remaining <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, (remaining + 999_999L) / 1_000_000L);
    }

    public static void check() throws TimeoutException {
        if (remainingMillis() == 0) throw new TimeoutException("项目请求总超时");
    }

    public static void clear() {
        DEADLINE.remove();
    }

    static void setNanoTimeSource(LongSupplier source) {
        nanoTime = source;
    }

    static void resetNanoTimeSource() {
        nanoTime = System::nanoTime;
    }

    public static final class Scope implements AutoCloseable {
        private Long previous;
        private boolean closed;

        private Scope(Long previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) DEADLINE.remove();
            else DEADLINE.set(previous);
            previous = null;
        }
    }
}
