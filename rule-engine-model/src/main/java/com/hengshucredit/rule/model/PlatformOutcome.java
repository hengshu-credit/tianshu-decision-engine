package com.hengshucredit.rule.model;

/** 只有明确的执行结果实现此契约，不推断业务数据中 success/code 字段的含义。 */
public interface PlatformOutcome {
    PlatformCode platformStatus();
    String platformDetail();
    default String platformTraceId() { return null; }
}
