package com.hengshucredit.rule.model.entity;

/** 控制台审计事实的稳定账户归属；不能由调用方声明。 */
public interface AccountOwnedRecord {
    Long getActorUserId();
    void setActorUserId(Long actorUserId);
}
