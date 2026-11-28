package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.dto.ProjectAuthDTO;
import com.hengshucredit.rule.model.dto.ProjectAuthSaveRequest;
import com.hengshucredit.rule.server.auth.ProjectAccessPolicy;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.ProjectAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rule/institution-auth")
@RequirePermission("institution-auth:manage")
public class InstitutionAuthController {
    private final ProjectAuthService auth;
    private final ConsoleProjectAccess access;

    public InstitutionAuthController(ProjectAuthService auth, ConsoleProjectAccess access) {
        this.auth = auth;
        this.access = access;
    }

    @GetMapping
    @RequirePermission(value = "institution-auth:view", anyOf = "institution-auth:manage")
    public ApiResponse<List<ProjectAuthDTO>> list() {
        return ApiResponse.ok(auth.listInstitutionAuths());
    }

    @PostMapping
    public ApiResponse<ProjectAuthDTO> create(@RequestBody ProjectAuthSaveRequest body, HttpServletRequest request) {
        requireManage();
        if (body.getManagementProjectId() != null) access.requireProject(body.getManagementProjectId());
        return audited(auth.createInstitutionAuth(body), request);
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse<ProjectAuthDTO> update(@PathVariable Long id, @RequestBody ProjectAuthSaveRequest body,
                                               HttpServletRequest request) {
        requireManage();
        return audited(auth.updateAuth(null, id, body), request);
    }

    @PutMapping("/{id:\\d+}/status")
    public ApiResponse<ProjectAuthDTO> status(@PathVariable Long id, @RequestParam Integer status, HttpServletRequest request) {
        requireManage();
        return audited(auth.setAuthStatus(null, id, status), request);
    }

    @GetMapping("/{id:\\d+}/full")
    public ApiResponse<ProjectAuthDTO> full(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response) {
        requireManage();
        response.setHeader("Cache-Control", "no-store");
        return audited(auth.getFullAuth(null, id), request);
    }

    @PostMapping("/{id:\\d+}/regenerate")
    public ApiResponse<ProjectAuthDTO> regenerate(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response) {
        requireManage();
        response.setHeader("Cache-Control", "no-store");
        return audited(auth.regenerateAuthSecret(null, id), request);
    }

    private void requireManage() {
        access.requirePermission("institution-auth:manage", null);
        access.requireGlobalEditable();
    }

    private ApiResponse<ProjectAuthDTO> audited(ProjectAuthDTO dto, HttpServletRequest request) {
        ProjectAuthContext subject = ProjectAuthContext.institution(dto.getInstitutionId(), dto.getId(),
                dto.getAuthCode(), dto.getAuthType(), new ProjectAccessPolicy(), false);
        auth.recordAccess(request, subject.withToken(null, null, "MANAGEMENT"), true, null);
        return ApiResponse.ok(dto);
    }
}
