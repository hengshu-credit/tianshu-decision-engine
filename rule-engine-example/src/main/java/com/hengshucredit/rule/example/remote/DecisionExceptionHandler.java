package com.hengshucredit.rule.example.remote;

import com.hengshucredit.rule.client.http.RuleHttpException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class DecisionExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> invalid(Exception ignored) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "请检查 ruleCode、规则白名单及 params 对象"));
    }

    @ExceptionHandler(RuleHttpException.class)
    public ResponseEntity<Map<String, Object>> upstream(RuleHttpException error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", error.getMessage());
        result.put("upstreamHttpStatus", error.getHttpStatus());
        result.put("platformCode", error.getPlatformCode());
        return ResponseEntity.status(502).body(result);
    }
}
