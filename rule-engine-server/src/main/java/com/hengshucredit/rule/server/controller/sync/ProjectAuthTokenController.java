package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.model.dto.ProjectTokenResponse;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.common.R;
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
    public R<ProjectTokenResponse> issueToken(HttpServletRequest request) {
        ProjectAuthContext context = ProjectAuthContext.from(request);
        if (context == null || context.isTemporaryToken()) {
            return R.fail(401, "Base project credential is required");
        }
        try {
            return R.ok(projectAuthService.issueToken(context));
        } catch (IllegalArgumentException e) {
            return R.fail(401, e.getMessage());
        }
    }
}
