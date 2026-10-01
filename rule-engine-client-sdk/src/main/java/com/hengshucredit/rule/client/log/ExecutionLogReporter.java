package com.hengshucredit.rule.client.log;

import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import java.util.List;

public interface ExecutionLogReporter extends AutoCloseable {
    default void start() {
        // Most external reporters are managed by their owning container.
    }

    void report(List<RuleExecutionLog> logs);

    @Override
    default void close() {
        // Most external reporters do not own resources. HTTP overrides this to flush its queue.
    }
}
