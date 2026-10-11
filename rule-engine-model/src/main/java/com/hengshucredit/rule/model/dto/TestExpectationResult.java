package com.hengshucredit.rule.model.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class TestExpectationResult {
    /** PASSED / FAILED / NOT_CONFIGURED / INVALID / NOT_EVALUATED；与执行状态独立。 */
    private String status;
    private String message;
    private List<Check> checks = new ArrayList<>();
    private List<Diff> diffs = new ArrayList<>();
    private boolean truncated;

    public record Check(String refType, String refId, String label, String operator, String status,
                        String message, String expectedJson, String actualJson, boolean actualMissing) { }
    public record Diff(String path, String expectedJson, String actualJson,
                       boolean expectedMissing, boolean actualMissing) { }
}
