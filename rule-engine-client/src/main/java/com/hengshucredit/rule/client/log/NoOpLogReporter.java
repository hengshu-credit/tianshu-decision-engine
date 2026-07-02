package com.hengshucredit.rule.client.log;

import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import java.util.List;

public class NoOpLogReporter implements ExecutionLogReporter {
    @Override
    public void report(List<RuleExecutionLog> logs) {
        // intentionally empty
    }
}
