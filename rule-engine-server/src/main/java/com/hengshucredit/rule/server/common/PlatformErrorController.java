package com.hengshucredit.rule.server.common;

import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.PlatformCode;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Servlet 容器错误也遵守平台协议，避免回落到另一套 timestamp/status/error 结构。 */
@RestController
public class PlatformErrorController implements ErrorController {
    @RequestMapping("${server.error.path:/error}")
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        PlatformCode code = PlatformCode.fromHttpStatus(value instanceof Number status ? status.intValue() : 500);
        if (code == PlatformCode.SUCCESS) code = PlatformCode.SYSTEM_ERROR;
        return org.springframework.http.ResponseEntity.status(code.httpStatus())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(ApiResponse.fail(code, null));
    }
}
