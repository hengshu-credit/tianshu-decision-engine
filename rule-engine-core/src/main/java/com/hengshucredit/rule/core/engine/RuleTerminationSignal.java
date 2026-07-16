package com.hengshucredit.rule.core.engine;

/**
 * 受控的整体规则终止信号，由规则运行入口捕获并转换为成功结果。
 */
public final class RuleTerminationSignal extends RuntimeException {

    public RuleTerminationSignal() {
        super(null, null, false, false);
    }
}
