package com.hengshucredit.rule.runtime;

import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.PlatformCode;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RuntimeErrorController implements ErrorController {
    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        PlatformCode code = PlatformCode.fromHttpStatus(value instanceof Number status ? status.intValue() : 500);
        if (code == PlatformCode.SUCCESS) code = PlatformCode.SYSTEM_ERROR;
        return ResponseEntity.status(code.httpStatus()).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(ApiResponse.fail(code, null));
    }
}
