package com.hengshucredit.rule.server.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import com.hengshucredit.rule.server.governance.GovernanceApprovalService;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(com.hengshucredit.rule.server.security.ConsoleAccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleConsoleAccessDenied(
            com.hengshucredit.rule.server.security.ConsoleAccessDeniedException e) {
        return ResponseEntity.status(403).body(Result.fail(403, e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(
            NoResourceFoundException ignored) {
        return ResponseEntity.status(404)
                .body(Result.fail(404, "资源不存在"));
    }

    @ExceptionHandler(RuleGovernanceException.class)
    public ResponseEntity<R<Map<String, Object>>> handleRuleGovernance(
            RuleGovernanceException e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errorCode", e.getCode());
        data.put("issues", e.getIssues());
        R<Map<String, Object>> body = R.fail(e.getHttpStatus(), e.getMessage());
        body.setData(data);
        return ResponseEntity.status(e.getHttpStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(
            GovernanceApprovalService.GovernanceStateException.class)
    public Result<Map<String, Object>> handleGovernanceState(
            GovernanceApprovalService.GovernanceStateException e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errorCode", e.getCode());
        Result<Map<String, Object>> result =
                Result.fail(409, e.getMessage());
        result.setData(data);
        return result;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        return Result.fail(400, e.getMessage());
    }
}
