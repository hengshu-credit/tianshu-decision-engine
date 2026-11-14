package com.hengshucredit.rule.server.service;

/** 外数按 WAIT 策略暂停当前订单，等待业务按 root trace 恢复。 */
public final class ExternalApiWaitingException extends com.hengshucredit.rule.core.engine.RuleSuspensionSignal {
    private final boolean manualReview;

    public ExternalApiWaitingException(String message, Throwable cause) {
        this(message, cause, false);
    }

    private ExternalApiWaitingException(String message, Throwable cause, boolean manualReview) {
        super(message, cause);
        this.manualReview = manualReview;
    }

    static ExternalApiWaitingException manualReview(String message) {
        return new ExternalApiWaitingException(message, null, true);
    }

    public static boolean requiresReview(Throwable failure) {
        for (Throwable e = failure; e != null; e = e.getCause()) {
            if (e instanceof ExternalApiWaitingException wait && wait.manualReview) return true;
            if (e == e.getCause()) break;
        }
        return false;
    }

    public ExternalApiWaitingException(String message) {
        this(message, null);
    }
}
