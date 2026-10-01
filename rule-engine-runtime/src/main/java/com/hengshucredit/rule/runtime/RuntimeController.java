package com.hengshucredit.rule.runtime;

import com.hengshucredit.rule.client.http.RuleHttpException;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/** 单项目正式执行入口；接入令牌和上游项目令牌分别配置，均不返回给调用方。 */
@RestController
@RequestMapping("/api/rule")
public class RuntimeController {
    private final BiFunction<String, Map<String, Object>, RuleResult> executor;
    private final List<String> allowedCodes;
    private final byte[] authorization;

    public RuntimeController(BiFunction<String, Map<String, Object>, RuleResult> executor, Environment env) {
        this.executor = executor;
        this.allowedCodes = RuntimeConfiguration.allowedCodes(env);
        String token = RuntimeConfiguration.required(env, "RUNTIME_ACCESS_TOKEN");
        if (token.length() < 32) throw new IllegalArgumentException("RUNTIME_ACCESS_TOKEN 至少 32 位");
        this.authorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/execute/{ruleCode}")
    public ResponseEntity<?> execute(@PathVariable String ruleCode,
                                     @RequestHeader(value = "Authorization", defaultValue = "") String header,
                                     @RequestBody ExecuteRequest body) {
        if (!MessageDigest.isEqual(authorization, header.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "接入令牌无效"));
        }
        if (!allowedCodes.contains(ruleCode)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "规则不在允许执行的列表中"));
        }
        if (body == null || body.params() == null) throw new IllegalArgumentException("params 必须为对象");
        RuleResult result = executor.apply(ruleCode, body.params());
        return ResponseEntity.status(result.isSuccess() ? 200 : 422)
                .body(Map.of("code", result.isSuccess() ? 200 : 422, "data", result));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<?> invalid() {
        return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "请检查 params 对象"));
    }

    @ExceptionHandler(RuleHttpException.class)
    public ResponseEntity<?> upstream(RuleHttpException error) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("code", 502);
        body.put("message", "上游执行失败，结果可能未知，请勿盲目重试");
        body.put("traceId", error.getTraceId());
        body.put("executionStatus", error.getExecutionStatus());
        return ResponseEntity.status(502).body(body);
    }

    public record ExecuteRequest(Map<String, Object> params) { }
}
