package com.hengshucredit.rule.core.engine;

import com.hengshucredit.rule.model.dto.RuleValidationIssue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** 决策表唯一命中冲突，使用规则行位置生成可定位的业务诊断。 */
public final class DecisionTableConflictException extends IllegalStateException {
    private final List<Integer> rows;

    public DecisionTableConflictException(String rowNumbers) {
        this(rowNumbers == null || rowNumbers.isBlank() ? List.of() : Arrays.stream(rowNumbers.split(",", 21))
                .filter(value -> !value.isBlank()).limit(20).map(Integer::valueOf).toList());
    }

    private DecisionTableConflictException(List<Integer> rows) {
        super("唯一命中（UNIQUE）冲突：" + (rows.isEmpty() ? "多条规则" : "第 "
                + rows.stream().map(String::valueOf).collect(Collectors.joining("、")) + " 行规则")
                + "同时满足条件。" + (rows.isEmpty()
                ? "当前为历史编译脚本，冲突检查前可能已执行动作，请核对执行记录并重新编译发布。"
                : "本次未执行动作。") + "请检查重叠条件或调整命中策略。");
        this.rows = rows;
    }

    public List<RuleValidationIssue> issues() {
        return rows.stream().filter(row -> row > 0).map(row -> RuleValidationIssue
                .error("UNIQUE_CONFLICT", "$.rules[" + (row - 1) + "]", "此行与其他规则行同时命中，本次未执行动作")
                .withTitle("第 " + row + " 行同时命中")
                .withNextAction("调整重叠条件，或选择合适的命中策略，再执行测试")).toList();
    }
}
