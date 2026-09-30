package com.hengshucredit.rule.server.controller.open;

import com.hengshucredit.rule.core.trace.TraceIdGenerator;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.openapi.OpenApiContract;
import com.hengshucredit.rule.server.openapi.OpenApiContractService;
import com.hengshucredit.rule.server.openapi.OpenApiException;
import com.hengshucredit.rule.server.openapi.OpenApiStatus;
import com.hengshucredit.rule.server.openapi.OpenApiStatuses;
import com.hengshucredit.rule.server.openapi.OpenRequestMapper;
import com.hengshucredit.rule.server.openapi.OpenResponseRenderer;
import com.hengshucredit.rule.server.service.OpenRuleExecutionExecutor;
import com.hengshucredit.rule.server.service.FieldValidationException;
import com.hengshucredit.rule.server.service.FieldValidationViolation;
import com.hengshucredit.rule.server.service.RuleFieldValidationService;
import com.hengshucredit.rule.server.service.RuleExecuteService;
import com.hengshucredit.rule.server.service.RuleIdempotencyService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/open")
public class OpenRuleController {

    @Resource
    private OpenApiContractService contractService;

    @Resource
    private OpenRequestMapper requestMapper;

    @Resource
    private OpenResponseRenderer responseRenderer;

    @Resource
    private RuleExecuteService executeService;

    @Resource
    private OpenRuleExecutionExecutor executionExecutor;

    @Resource
    private RuleFieldValidationService fieldValidationService;

    @Resource
    private RuleIdempotencyService idempotencyService;

    @PostMapping("/execute/{ruleCode}")
    public ResponseEntity<Object> execute(@PathVariable String ruleCode,
                                          @RequestBody(required = false) Object body,
                                          @RequestHeader HttpHeaders headers,
                                          HttpServletRequest request) {
        OpenApiContractService.ResolvedContract resolved = null;
        String traceId = null;
        RuleIdempotencyService.Decision activeIdempotency = null;
        try {
            ProjectAuthContext authContext = ProjectAuthContext.from(request);
            if (authContext == null) {
                throw new OpenApiException(OpenApiStatuses.productUnauthorized());
            }
            resolved = contractService.resolve(authContext.getAuthId(), headers.getFirst("X-Auth-Code"), ruleCode);
            traceId = traceId(resolved);
            Map<String, Object> params = requestMapper.map(resolved.getContract(), body,
                    firstHeaders(headers), resolved.getInputReferences());
            fieldValidationService.validateDefinitionInput(
                    resolved.getPublished().getDefinitionId(), params);
            RuleIdempotencyService.Decision idempotency = idempotencyService == null ? null
                    : idempotencyService.begin(resolved.getPublished(), resolved.getProject().getId(), params);
            activeIdempotency = idempotency != null && idempotency.isClaimed() ? idempotency : null;
            if (idempotency == null) {
                OpenApiContractService.ResolvedContract executionContract = resolved;
                RuleResult result = executionExecutor.execute(() -> executeService.executePublished(
                        executionContract.getPublished(), params, executionContract.getProject().getId(),
                        "OPEN_API", authContext,
                        executionContract.getContract().isRecordTrace()
                                || executionContract.getContract().isReturnTrace(),
                        executionContract.getContract().isRecordTrace()));
                if (hasText(result.getTraceId())) traceId = result.getTraceId();
                OpenApiStatus status = result.isSuccess() ? OpenApiStatuses.success()
                        : "WAITING_EXTERNAL".equals(result.getExecutionStatus())
                        ? OpenApiStatuses.waitingExternal()
                        : "THROTTLED".equals(result.getExecutionStatus())
                        ? OpenApiStatuses.qpsConcurrencyExceeded()
                        : OpenApiStatuses.resultError(safeMessage(result.getErrorMessage(), "结果处理异常"));
                return render(resolved.getContract(), status,
                        traceId, result.isSuccess() ? outputValues(resolved, result) : errorValues(result.getErrorMessage()));
            }
            if (idempotency.getStatus() == RuleIdempotencyService.Status.COMPLETED) {
                RuleResult cached = idempotency.getResult();
                if (hasText(cached.getTraceId())) traceId = cached.getTraceId();
                OpenApiStatus cachedStatus = cached.isSuccess() ? OpenApiStatuses.success()
                        : "WAITING_EXTERNAL".equals(cached.getExecutionStatus())
                        ? OpenApiStatuses.waitingExternal()
                        : "THROTTLED".equals(cached.getExecutionStatus())
                        ? OpenApiStatuses.qpsConcurrencyExceeded()
                        : OpenApiStatuses.resultError(safeMessage(cached.getErrorMessage(), "结果处理异常"));
                return render(resolved.getContract(), cachedStatus,
                        traceId, cached.isSuccess() ? outputValues(resolved, cached) : errorValues(cached.getErrorMessage()));
            }
            if (idempotency.getStatus() == RuleIdempotencyService.Status.CONFLICT) {
                return render(resolved.getContract(), new OpenApiStatus(false, "400004",
                                "幂等键已被其他请求使用且请求内容不同", 409),
                        traceId, errorValues("幂等键已被其他请求使用且请求内容不同"));
            }
            if (idempotency.getStatus() == RuleIdempotencyService.Status.IN_PROGRESS) {
                return render(resolved.getContract(), new OpenApiStatus(false, "400005",
                                "相同幂等键的请求正在执行", 409),
                        traceId, errorValues("相同幂等键的请求正在执行"));
            }
            OpenApiContractService.ResolvedContract executionContract = resolved;
            RuleResult result = executionExecutor.execute(() -> idempotencyService.withDecision(idempotency,
                    () -> executeService.executePublished(
                            executionContract.getPublished(), params, executionContract.getProject().getId(),
                            "OPEN_API", authContext,
                            executionContract.getContract().isRecordTrace()
                                    || executionContract.getContract().isReturnTrace(),
                            executionContract.getContract().isRecordTrace())));
            if (hasText(result.getTraceId())) traceId = result.getTraceId();
            idempotencyService.complete(idempotency, result, null);
            activeIdempotency = null;
            if (!result.isSuccess()) {
                Map<String, Object> values = errorValues(result.getErrorMessage());
                if ("WAITING_EXTERNAL".equals(result.getExecutionStatus())) {
                    return render(resolved.getContract(), OpenApiStatuses.waitingExternal(), traceId, values);
                }
                if ("THROTTLED".equals(result.getExecutionStatus())) {
                    return render(resolved.getContract(), OpenApiStatuses.qpsConcurrencyExceeded(),
                            traceId, values);
                }
                return render(resolved.getContract(), OpenApiStatuses.resultError(
                        safeMessage(result.getErrorMessage(), "结果处理异常")),
                        traceId, values);
            }
            return render(resolved.getContract(), OpenApiStatuses.success(),
                    traceId, outputValues(resolved, result));
        } catch (OpenRuleExecutionExecutor.TimedOut e) {
            releaseIdempotency(activeIdempotency, e.getMessage());
            return renderSafely(resolved, OpenApiStatuses.requestTimeout(),
                    traceId, errorValues("请求处理超时"));
        } catch (OpenRuleExecutionExecutor.Busy e) {
            releaseIdempotency(activeIdempotency, e.getMessage());
            return renderSafely(resolved, OpenApiStatuses.qpsConcurrencyExceeded(),
                    traceId, errorValues("QPS 或并发超过限制"));
        } catch (OpenApiException e) {
            releaseIdempotency(activeIdempotency, e.getMessage());
            return renderSafely(resolved, e.getStatus(), traceId, errorValues(e.getMessage()));
        } catch (FieldValidationException e) {
            releaseIdempotency(activeIdempotency, e.getMessage());
            return renderSafely(resolved, OpenApiStatuses.parameterValidation(e.getMessage()),
                    traceId, validationErrorValues(e));
        } catch (IllegalArgumentException e) {
            releaseIdempotency(activeIdempotency, e.getMessage());
            return renderSafely(resolved, OpenApiStatuses.parameterValidation(
                    safeMessage(e.getMessage(), "入参校验失败")), traceId, errorValues(e.getMessage()));
        } catch (RuntimeException e) {
            releaseIdempotency(activeIdempotency, e.getMessage());
            return renderSafely(resolved, OpenApiStatuses.systemError(),
                    traceId, errorValues("系统执行异常"));
        }
    }

    private void releaseIdempotency(RuleIdempotencyService.Decision decision, String message) {
        if (decision != null && idempotencyService != null) {
            idempotencyService.release(decision, hasText(message) ? message : "规则执行异常");
        }
    }

    private ResponseEntity<Object> renderSafely(OpenApiContractService.ResolvedContract resolved,
                                                OpenApiStatus status, String traceId,
                                                Map<String, Object> values) {
        OpenApiContract contract = resolved == null ? contractService.defaultContract() : resolved.getContract();
        String actualTraceId = hasText(traceId) ? traceId : fallbackTraceId(resolved);
        try {
            return render(contract, status, actualTraceId, values);
        } catch (RuntimeException ignored) {
            return render(contractService.defaultContract(), status, actualTraceId, values);
        }
    }

    private ResponseEntity<Object> render(OpenApiContract contract, OpenApiStatus status,
                                          String traceId, Map<String, Object> values) {
        OpenResponseRenderer.RenderedResponse rendered = responseRenderer.render(contract, status, traceId, values);
        HttpHeaders responseHeaders = new HttpHeaders();
        rendered.getHeaders().forEach(responseHeaders::set);
        if (status.getHttpStatus() == 429) {
            responseHeaders.set("Retry-After", "1");
            responseHeaders.set("X-Rule-Queue-Depth", String.valueOf(
                    executionExecutor.snapshot().getOrDefault("queueDepth", 0)));
            responseHeaders.set("X-Rule-Limit-Name",
                    OpenApiStatuses.QPS_CONCURRENCY_EXCEEDED.equals(status.getCode())
                            ? "open-execution-queue" : "project-quota");
        }
        return ResponseEntity.status(rendered.getHttpStatus()).headers(responseHeaders).body(rendered.getBody());
    }

    private Map<String, Object> outputValues(OpenApiContractService.ResolvedContract resolved, RuleResult result) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("result", result.getResult());
        values.put("revisionId", result.getRevisionId());
        values.put("artifactDigest", result.getArtifactDigest());
        values.put("executionStatus", result.getExecutionStatus());
        values.put("attemptNo", result.getAttemptNo());
        values.put("resumed", result.isResumed());
        values.put("reusedCount", result.getReusedCount());
        values.put("reexecutedCount", result.getReexecutedCount());
        for (Map.Entry<String, String> entry : resolved.getOutputReferences().entrySet()) {
            values.put("output." + entry.getKey().replace(':', '.'), readPath(result.getResult(), entry.getValue()));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        for (OpenApiContract.ResponseMapping mapping : resolved.getContract().getResponseMappings()) {
            String reference = OpenRequestMapper.referenceKey(
                    mapping.getSourceRefType(), mapping.getSourceVarId());
            String sourcePath = resolved.getOutputReferences().get(reference);
            response.put(mapping.getTargetField(), readPath(result.getResult(), sourcePath));
        }
        values.put("response", response);
        if (resolved.getContract().isReturnTrace()) values.put("trace", result.getTraces());
        return values;
    }

    private Object readPath(Object root, String path) {
        if (!(root instanceof Map) || !hasText(path)) return null;
        Map<?, ?> map = (Map<?, ?>) root;
        if (map.containsKey(path)) return map.get(path);
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map) || !((Map<?, ?>) current).containsKey(part)) return null;
            current = ((Map<?, ?>) current).get(part);
        }
        return current;
    }

    private Map<String, Object> errorValues(String message) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("error.message", message);
        return values;
    }

    private Map<String, Object> validationErrorValues(FieldValidationException exception) {
        Map<String, Object> values = errorValues(exception.getMessage());
        FieldValidationViolation first = exception.getFirstViolation();
        if (first != null) {
            values.put("error.field", first.getField());
            values.put("error.validationId", first.getValidationId());
            values.put("error.validationCode", first.getValidationCode());
            values.put("error.validationName", first.getValidationName());
        }
        values.put("error.violations", exception.getViolations());
        return values;
    }

    private Map<String, String> firstHeaders(HttpHeaders headers) {
        Map<String, String> result = new LinkedHashMap<>();
        if (headers == null) return result;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            List<String> values = entry.getValue();
            result.put(entry.getKey(), values == null || values.isEmpty() ? null : values.get(0));
        }
        return result;
    }

    private String traceId(OpenApiContractService.ResolvedContract resolved) {
        String scopeCode = resolved.getProject().getTraceScopeCode();
        if (!hasText(scopeCode)) scopeCode = TraceIdGenerator.projectScopeCode(resolved.getProject().getId());
        return TraceIdGenerator.generate("AP", "P", scopeCode);
    }

    private String fallbackTraceId(OpenApiContractService.ResolvedContract resolved) {
        return resolved == null
                ? TraceIdGenerator.generate("AP", "G", TraceIdGenerator.GLOBAL_SCOPE_CODE)
                : traceId(resolved);
    }

    private String safeMessage(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
