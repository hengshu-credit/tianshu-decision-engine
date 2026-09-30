package com.hengshucredit.rule.core.engine;

/** 受控暂停信号，交给执行协调器保留订单与检查点，不转换为普通脚本失败。 */
public class RuleSuspensionSignal extends IllegalStateException {
    public RuleSuspensionSignal(String message, Throwable cause) {
        super(message, cause);
    }
}
