package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.dto.ProjectAuthDTO;
import com.hengshucredit.rule.model.dto.ProjectAuthSaveRequest;
import com.hengshucredit.rule.model.dto.ProjectAuthTokenDTO;
import com.hengshucredit.rule.model.entity.RuleAuthAccessLog;
import com.hengshucredit.rule.server.service.ProjectAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/rule/project/{projectId:\\d+}/auth")
public class ProjectAuthController {

    @Resource
    private ProjectAuthService projectAuthService;

    @GetMapping
    public ApiResponse<List<ProjectAuthDTO>> list(@PathVariable Long projectId) {
        return execute(() -> projectAuthService.listAuths(projectId));
    }

    @PostMapping
    public ApiResponse<ProjectAuthDTO> create(@PathVariable Long projectId,
                                    @RequestBody ProjectAuthSaveRequest request,
                                    HttpServletRequest httpRequest) {
        return execute(() -> {
            ProjectAuthDTO result = projectAuthService.createAuth(projectId, request);
            projectAuthService.recordManagementAccess(httpRequest, projectId, result.getId(), null);
            return result;
        });
    }

    @PutMapping("/{authId:\\d+}")
    public ApiResponse<ProjectAuthDTO> update(@PathVariable Long projectId,
                                    @PathVariable Long authId,
                                    @RequestBody ProjectAuthSaveRequest request,
                                    HttpServletRequest httpRequest) {
        return execute(() -> {
            ProjectAuthDTO result = projectAuthService.updateAuth(projectId, authId, request);
            projectAuthService.recordManagementAccess(httpRequest, projectId, authId, null);
            return result;
        });
    }

    @PutMapping("/{authId:\\d+}/status")
    public ApiResponse<ProjectAuthDTO> updateStatus(@PathVariable Long projectId,
                                          @PathVariable Long authId,
                                          @RequestParam Integer status,
                                          HttpServletRequest httpRequest) {
        return execute(() -> {
            ProjectAuthDTO result = projectAuthService.setAuthStatus(projectId, authId, status);
            projectAuthService.recordManagementAccess(httpRequest, projectId, authId, null);
            return result;
        });
    }

    @GetMapping("/{authId:\\d+}/full")
    public ApiResponse<ProjectAuthDTO> getFull(@PathVariable Long projectId,
                                     @PathVariable Long authId,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        noStore(response);
        return execute(() -> {
            ProjectAuthDTO result = projectAuthService.getFullAuth(projectId, authId);
            projectAuthService.recordManagementAccess(request, projectId, authId, null);
            return result;
        });
    }

    @PostMapping("/{authId:\\d+}/regenerate")
    public ApiResponse<ProjectAuthDTO> regenerate(@PathVariable Long projectId,
                                        @PathVariable Long authId,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        noStore(response);
        return execute(() -> {
            ProjectAuthDTO result = projectAuthService.regenerateAuthSecret(projectId, authId);
            projectAuthService.recordManagementAccess(request, projectId, authId, null);
            return result;
        });
    }

    @GetMapping("/{authId:\\d+}/tokens")
    public ApiResponse<IPage<ProjectAuthTokenDTO>> listTokens(
            @PathVariable Long projectId,
            @PathVariable Long authId,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return execute(() -> projectAuthService.pageTokens(projectId, authId, pageNum, pageSize));
    }

    @GetMapping("/{authId:\\d+}/tokens/{tokenId:\\d+}/full")
    public ApiResponse<ProjectAuthTokenDTO> getFullToken(@PathVariable Long projectId,
                                               @PathVariable Long authId,
                                               @PathVariable Long tokenId,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        noStore(response);
        return execute(() -> {
            ProjectAuthTokenDTO result = projectAuthService.getFullToken(projectId, authId, tokenId);
            projectAuthService.recordManagementAccess(request, projectId, authId, tokenId);
            return result;
        });
    }

    @PostMapping("/{authId:\\d+}/tokens/{tokenId:\\d+}/revoke")
    public ApiResponse<ProjectAuthTokenDTO> revokeToken(@PathVariable Long projectId,
                                              @PathVariable Long authId,
                                              @PathVariable Long tokenId,
                                              HttpServletRequest request) {
        return execute(() -> {
            ProjectAuthTokenDTO result = projectAuthService.revokeToken(projectId, authId, tokenId);
            projectAuthService.recordManagementAccess(request, projectId, authId, tokenId);
            return result;
        });
    }

    @GetMapping("/access-logs")
    public ApiResponse<IPage<RuleAuthAccessLog>> listAccessLogs(
            @PathVariable Long projectId,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String authType,
            @RequestParam(required = false) String authCode,
            @RequestParam(required = false) String tokenCode,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime) {
        return execute(() -> projectAuthService.pageAccessLogs(
                projectId, pageNum, pageSize, authType, authCode, tokenCode, success, beginTime, endTime));
    }

    private <T> ApiResponse<T> execute(Supplier<T> action) {
        try {
            return ApiResponse.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PARAMETER_INVALID, e.getMessage());
        }
    }

    private void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }
}
