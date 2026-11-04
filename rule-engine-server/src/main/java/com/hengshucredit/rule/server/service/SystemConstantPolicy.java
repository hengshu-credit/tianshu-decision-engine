package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleVariable;

/** 以数据库中的系统身份为准，不信任草稿中的编码、来源或标记。 */
public final class SystemConstantPolicy {
    private SystemConstantPolicy() { }

    public static void requireMutable(RuleVariable stored) {
        requireMutableFlag(stored == null ? null : stored.getSystemConstant());
    }

    public static void requireMutableFlag(Boolean systemConstant) {
        if (Boolean.TRUE.equals(systemConstant)) {
            throw new IllegalArgumentException("系统常量只读，不允许修改、停用、删除或恢复版本");
        }
    }
}
