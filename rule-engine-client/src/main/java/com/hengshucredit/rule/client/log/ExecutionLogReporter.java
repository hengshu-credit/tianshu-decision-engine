package com.hengshucredit.rule.client.log;

import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import java.util.List;

public interface ExecutionLogReporter {
    void report(List<RuleExecutionLog> logs);
}
