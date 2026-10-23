package com.hengshucredit.rule.model;

/** 按异常类型归类，不依赖可能变化的异常文本。业务模块可用 PlatformException 保留来源。 */
public final class PlatformErrors {
    private PlatformErrors() { }

    public static PlatformCode classify(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof PlatformException platform) return platform.platformStatus();
            if (current instanceof java.sql.SQLException) return PlatformCode.DATABASE_ERROR;
            if (current instanceof java.util.concurrent.TimeoutException
                    || current instanceof java.net.SocketTimeoutException) return PlatformCode.REQUEST_TIMEOUT;
            if (current instanceof java.util.concurrent.RejectedExecutionException) return PlatformCode.CAPACITY_EXCEEDED;
            if (current == current.getCause()) break;
        }
        if (error instanceof IllegalArgumentException) return PlatformCode.PARAMETER_INVALID;
        if (error instanceof SecurityException) return PlatformCode.PERMISSION_DENIED;
        return PlatformCode.SYSTEM_ERROR;
    }

    public static String detail(Throwable error) {
        return error instanceof PlatformException platform ? platform.getDetail() : error.getMessage();
    }
}
