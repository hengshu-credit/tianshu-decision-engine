package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.model.dto.ProjectTokenResponse;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.service.ProjectAuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/rule/auth")
public class ProjectAuthTokenController {

    @Resource
    private ProjectAuthService projectAuthService;

    @PostMapping("/token")
    public ApiResponse<ProjectTokenResponse> issueToken(HttpServletRequest request) {
        ProjectAuthContext context = ProjectAuthContext.from(request);
        if (context == null || context.isTemporaryToken() || context.isGlobalAccess()) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.TOKEN_INVALID, "Base project credential is required");
        }
        try {
            return ApiResponse.ok(projectAuthService.issueToken(context));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.TOKEN_INVALID, e.getMessage());
        }
    }
}
