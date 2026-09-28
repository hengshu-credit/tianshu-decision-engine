package com.hengshucredit.rule.core.engine;

/**
 * Optional raw lookup used while applying an explicit runtime write.
 * Implementations can bypass lazy read semantics without changing normal Map reads.
 */
public interface RuntimeWriteTarget {
    Object runtimeValue(String key);
}
