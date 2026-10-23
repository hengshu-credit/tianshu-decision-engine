package com.hengshucredit.rule.server.controller;

import com.hengshucredit.rule.server.service.ExternalApiCallbackStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.PlatformCode;

@RestController
public class ExternalApiCallbackController {
    private final ExternalApiCallbackStore callbacks;

    public ExternalApiCallbackController(ExternalApiCallbackStore callbacks) { this.callbacks = callbacks; }

    @PostMapping("/api/external-callback/{invocationId}")
    public ResponseEntity<ApiResponse<Void>> callback(@PathVariable String invocationId,
                                                       @RequestHeader HttpHeaders headers,
                                                       HttpServletRequest request) throws IOException {
        try {
            callbacks.accept(invocationId, headers, request.getInputStream().readNBytes(1048577));
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(ApiResponse.fail(PlatformCode.TOKEN_INVALID, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(PlatformCode.PARAMETER_INVALID, e.getMessage()));
        }
    }
}
