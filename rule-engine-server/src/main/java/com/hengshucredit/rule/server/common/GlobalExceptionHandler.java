package com.hengshucredit.rule.server.common;

import com.hengshucredit.rule.model.ApiResponse;

import lombok.extern.slf4j.Slf4j;
import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.model.PlatformErrors;
import com.hengshucredit.rule.model.PlatformException;
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
    public ResponseEntity<ApiResponse<Void>> handleConsoleAccessDenied(
            com.hengshucredit.rule.server.security.ConsoleAccessDeniedException e) {
        return ResponseEntity.status(403).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PERMISSION_DENIED, e.getMessage()));
    }

    @ExceptionHandler({NoResourceFoundException.class, org.springframework.web.servlet.NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            Exception ignored) {
        return ResponseEntity.status(404).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "资源不存在"));
    }

    @ExceptionHandler(RuleGovernanceException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleRuleGovernance(
            RuleGovernanceException e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errorCode", e.getCode());
        data.put("issues", e.getIssues());
        PlatformCode code = e.getHttpStatus() == 409 ? PlatformCode.GOVERNANCE_CONFLICT
                : e.getHttpStatus() == 422 ? PlatformCode.PREFLIGHT_FAILED
                : PlatformCode.fromHttpStatus(e.getHttpStatus());
        ApiResponse<Map<String, Object>> body = ApiResponse.fail(code, e.getMessage());
        body.setData(data);
        return ResponseEntity.status(e.getHttpStatus()).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse response) {
            return json(ApiResponse.fail(PlatformCode.fromHttpStatus(response.getStatusCode().value()), e.getMessage()));
        }
        log.error("Unhandled exception", e);
        return json(ApiResponse.fail(PlatformErrors.classify(e), PlatformErrors.detail(e)));
    }

    @ExceptionHandler(
            GovernanceApprovalService.GovernanceStateException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleGovernanceState(
            GovernanceApprovalService.GovernanceStateException e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errorCode", e.getCode());
        ApiResponse<Map<String, Object>> result =
                ApiResponse.fail(PlatformCode.GOVERNANCE_CONFLICT, e.getMessage());
        result.setData(data);
        return json(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return json(ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PARAMETER_INVALID, e.getMessage()));
    }

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatform(PlatformException e) {
        return json(ApiResponse.fail(e.platformStatus(), e.getDetail()));
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleRequest(Exception e) {
        return json(ApiResponse.fail(PlatformCode.MALFORMED_REQUEST, e.getMessage()));
    }

    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> handleMissingField(Exception e) {
        return json(ApiResponse.fail(PlatformCode.REQUIRED_FIELD_MISSING, e.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(Exception e) {
        return json(ApiResponse.fail(PlatformCode.PARAMETER_INVALID, e.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(Exception e) {
        return json(ApiResponse.fail(PlatformCode.METHOD_NOT_ALLOWED, e.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(Exception e) {
        return json(ApiResponse.fail(PlatformCode.MEDIA_TYPE_UNSUPPORTED, e.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUpload(Exception e) {
        return json(ApiResponse.fail(PlatformCode.REQUEST_TOO_LARGE, null));
    }

    @ExceptionHandler(com.hengshucredit.rule.server.service.FieldValidationException.class)
    public ResponseEntity<?> handleFieldValidation(com.hengshucredit.rule.server.service.FieldValidationException e) {
        ApiResponse<Object> body = ApiResponse.fail(PlatformCode.PARAMETER_INVALID, e.getMessage());
        body.setData(e.getViolations());
        return json(body);
    }
    private <T> ResponseEntity<ApiResponse<T>> json(ApiResponse<T> body) {
        return ResponseEntity.status(body.status().httpStatus()).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body);
    }
}
