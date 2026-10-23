package com.hengshucredit.rule.runtime;

import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.model.RuleExecutionRequest;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
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
    private final BiFunction<String, com.hengshucredit.rule.model.RuleExecutionRequest, RuleResult> executor;
    private final List<String> allowedCodes;
    private final byte[] authorization;
    private final boolean globalAccess;

    public RuntimeController(BiFunction<String, com.hengshucredit.rule.model.RuleExecutionRequest, RuleResult> executor, Environment env) {
        this.executor = executor;
        this.allowedCodes = RuntimeConfiguration.allowedCodes(env);
        this.globalAccess = com.hengshucredit.rule.client.auth.ClientAuthConfig.GLOBAL_KEY
                .equalsIgnoreCase(env.getProperty("RULE_AUTH_TYPE", com.hengshucredit.rule.client.auth.ClientAuthConfig.LEGACY_TOKEN));
        String token = RuntimeConfiguration.required(env, "RUNTIME_ACCESS_TOKEN");
        if (token.length() < 32) throw new IllegalArgumentException("RUNTIME_ACCESS_TOKEN 至少 32 位");
        this.authorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/execute/{ruleCode}")
    public ResponseEntity<?> execute(@PathVariable String ruleCode,
                                     @RequestHeader(value = "Authorization", defaultValue = "") String header,
                                     @RequestBody Map<String, Object> body) {
        if (!MessageDigest.isEqual(authorization, header.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(401).body(ApiResponse.fail(PlatformCode.TOKEN_INVALID, "接入令牌无效"));
        }
        if (!globalAccess && !allowedCodes.contains(ruleCode)) {
            return ResponseEntity.status(403).body(ApiResponse.fail(PlatformCode.PRODUCT_UNAUTHORIZED, "规则不在允许执行的列表中"));
        }
        RuleExecutionRequest input = RuleExecutionRequest.fromBody(body);
        RuleResult result = executor.apply(ruleCode, input);
        ApiResponse<RuleResult> response = ApiResponse.ok(result);
        var builder = ResponseEntity.status(response.status().httpStatus());
        if (result.getTraceId() != null) builder.header("X-Trace-Id", result.getTraceId());
        if (response.status().httpStatus() == 429) builder.header("Retry-After", String.valueOf(
                result.getRetryAfterMs() == null ? 1 : Math.max(1, (result.getRetryAfterMs() + 999) / 1000)));
        return builder.body(response);
    }

}
