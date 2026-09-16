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
import java.util.Map;

@RestController
public class ExternalApiCallbackController {
    private final ExternalApiCallbackStore callbacks;

    public ExternalApiCallbackController(ExternalApiCallbackStore callbacks) { this.callbacks = callbacks; }

    @PostMapping("/api/external-callback/{invocationId}")
    public ResponseEntity<Map<String, Object>> callback(@PathVariable String invocationId,
                                                       @RequestHeader HttpHeaders headers,
                                                       HttpServletRequest request) throws IOException {
        try {
            callbacks.accept(invocationId, headers, request.getInputStream().readNBytes(1048577));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
