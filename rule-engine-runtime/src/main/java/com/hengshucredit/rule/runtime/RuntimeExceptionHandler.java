package com.hengshucredit.rule.runtime;

import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.model.PlatformErrors;
import com.hengshucredit.rule.client.http.RuleHttpException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RuntimeExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> failure(Exception error) {
        PlatformCode code = PlatformErrors.classify(error);
        if (error instanceof org.springframework.web.ErrorResponse response) code = PlatformCode.fromHttpStatus(response.getStatusCode().value());
        if (error instanceof org.springframework.web.bind.MissingServletRequestParameterException
                || error instanceof org.springframework.web.bind.MissingRequestHeaderException
                || error instanceof org.springframework.web.multipart.support.MissingServletRequestPartException) code = PlatformCode.REQUIRED_FIELD_MISSING;
        if (error instanceof org.springframework.http.converter.HttpMessageNotReadableException) code = PlatformCode.MALFORMED_REQUEST;
        else if (error instanceof org.springframework.web.servlet.resource.NoResourceFoundException
                || error instanceof org.springframework.web.servlet.NoHandlerFoundException) code = PlatformCode.RESOURCE_NOT_FOUND;
        else if (error instanceof org.springframework.web.HttpRequestMethodNotSupportedException) code = PlatformCode.METHOD_NOT_ALLOWED;
        else if (error instanceof org.springframework.web.HttpMediaTypeNotSupportedException) code = PlatformCode.MEDIA_TYPE_UNSUPPORTED;
        ApiResponse<Object> response = ApiResponse.fail(code, PlatformErrors.detail(error));
        var builder = ResponseEntity.status(code.httpStatus()).contentType(org.springframework.http.MediaType.APPLICATION_JSON);
        if (error instanceof RuleHttpException upstream) {
            response.setTraceId(upstream.getTraceId());
            if (upstream.getExecutionStatus() != null) response.setData(java.util.Map.of("executionStatus", upstream.getExecutionStatus()));
            if (upstream.getTraceId() != null) builder.header("X-Trace-Id", upstream.getTraceId());
            if (upstream.getRetryAfterMs() != null) builder.header("Retry-After", String.valueOf(Math.max(1, (upstream.getRetryAfterMs() + 999) / 1000)));
        }
        return builder.body(response);
    }
}
