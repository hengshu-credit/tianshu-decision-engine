package com.hengshucredit.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.dto.ProjectAuthDTO;
import com.hengshucredit.rule.model.dto.ProjectAuthSaveRequest;
import com.hengshucredit.rule.model.dto.ProjectAuthTokenDTO;
import com.hengshucredit.rule.model.entity.RuleAuthAccessLog;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.ProjectAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/rule/project/{projectId:\\d+}/auth")
public class ProjectAuthController {

    @Resource
    private ProjectAuthService projectAuthService;

    @GetMapping
    public R<List<ProjectAuthDTO>> list(@PathVariable Long projectId) {
        return execute(() -> projectAuthService.listAuths(projectId));
    }

    @PostMapping
    public R<ProjectAuthDTO> create(@PathVariable Long projectId,
                                    @RequestBody ProjectAuthSaveRequest request) {
        return execute(() -> projectAuthService.createAuth(projectId, request));
    }

    @PutMapping("/{authId:\\d+}")
    public R<ProjectAuthDTO> update(@PathVariable Long projectId,
                                    @PathVariable Long authId,
                                    @RequestBody ProjectAuthSaveRequest request) {
        return execute(() -> projectAuthService.updateAuth(projectId, authId, request));
    }

    @PutMapping("/{authId:\\d+}/status")
    public R<ProjectAuthDTO> updateStatus(@PathVariable Long projectId,
                                          @PathVariable Long authId,
                                          @RequestParam Integer status) {
        return execute(() -> projectAuthService.setAuthStatus(projectId, authId, status));
    }

    @GetMapping("/{authId:\\d+}/full")
    public R<ProjectAuthDTO> getFull(@PathVariable Long projectId,
                                     @PathVariable Long authId) {
        return execute(() -> projectAuthService.getFullAuth(projectId, authId));
    }

    @GetMapping("/{authId:\\d+}/tokens")
    public R<IPage<ProjectAuthTokenDTO>> listTokens(
            @PathVariable Long projectId,
            @PathVariable Long authId,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return execute(() -> projectAuthService.pageTokens(projectId, authId, pageNum, pageSize));
    }

    @GetMapping("/{authId:\\d+}/tokens/{tokenId:\\d+}/full")
    public R<ProjectAuthTokenDTO> getFullToken(@PathVariable Long projectId,
                                               @PathVariable Long authId,
                                               @PathVariable Long tokenId) {
        return execute(() -> projectAuthService.getFullToken(projectId, authId, tokenId));
    }

    @PostMapping("/{authId:\\d+}/tokens/{tokenId:\\d+}/revoke")
    public R<ProjectAuthTokenDTO> revokeToken(@PathVariable Long projectId,
                                              @PathVariable Long authId,
                                              @PathVariable Long tokenId) {
        return execute(() -> projectAuthService.revokeToken(projectId, authId, tokenId));
    }

    @GetMapping("/access-logs")
    public R<IPage<RuleAuthAccessLog>> listAccessLogs(
            @PathVariable Long projectId,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String authCode,
            @RequestParam(required = false) String tokenCode,
            @RequestParam(required = false) Integer success) {
        return execute(() -> projectAuthService.pageAccessLogs(
                projectId, pageNum, pageSize, authCode, tokenCode, success));
    }

    private <T> R<T> execute(Supplier<T> action) {
        try {
            return R.ok(action.get());
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        }
    }
}
