package com.hengshucredit.rule.server.transfer;

/** 按实际完成的工作单元换算阶段进度，子包使用各自的区间。 */
@FunctionalInterface
interface TransferProgress {
    TransferProgress NONE = (percent, message) -> {};

    void report(int percent, String message);

    default TransferProgress range(int from, int to, String prefix) {
        return (percent, message) -> report(from + (to - from) * percent / 100, prefix + message);
    }
}
