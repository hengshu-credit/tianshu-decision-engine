package com.hengshucredit.rule.server.service;

/** 外数按 WAIT 策略暂停当前订单，等待业务按 root trace 恢复。 */
public final class ExternalApiWaitingException extends com.hengshucredit.rule.core.engine.RuleSuspensionSignal {
    public ExternalApiWaitingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExternalApiWaitingException(String message) {
        super(message, null);
    }
}
