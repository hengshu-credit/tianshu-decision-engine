package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.server.dashboard.DashboardAccessContext;
import com.hengshucredit.rule.server.dashboard.DashboardAccessService;
import com.hengshucredit.rule.server.dashboard.DashboardApplicationService;
import com.hengshucredit.rule.server.dashboard.DashboardCriteria;
import com.hengshucredit.rule.server.dashboard.DashboardCriteriaService;
import com.hengshucredit.rule.server.dashboard.DashboardGovernanceService;
import com.hengshucredit.rule.server.dashboard.DashboardMapping;
import com.hengshucredit.rule.server.dashboard.DashboardMappingService;
import com.hengshucredit.rule.server.dashboard.DashboardMetricField;
import com.hengshucredit.rule.server.dashboard.DashboardOperationsService;
import com.hengshucredit.rule.server.dashboard.DashboardResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/dashboard")
public class DashboardController {

    private final DashboardAccessService accessService;
    private final DashboardCriteriaService criteriaService;
    private final DashboardMappingService mappingService;
    private final DashboardApplicationService applicationService;
    private final DashboardOperationsService operationsService;
    private final DashboardGovernanceService governanceService;

    public DashboardController(
            DashboardAccessService accessService,
            DashboardCriteriaService criteriaService,
            DashboardMappingService mappingService,
            DashboardApplicationService applicationService,
            DashboardOperationsService operationsService,
            DashboardGovernanceService governanceService) {
        this.accessService = accessService;
        this.criteriaService = criteriaService;
        this.mappingService = mappingService;
        this.applicationService = applicationService;
        this.operationsService = operationsService;
        this.governanceService = governanceService;
    }

    @GetMapping("/applications")
    public ApiResponse<DashboardResponses.Applications> applications(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime endTime,
            HttpServletRequest request) {
        DashboardAccessContext access = accessService.resolve(request);
        DashboardCriteria criteria = criteriaService.resolve(projectId,
                ruleCode, ruleName, startTime, endTime);
        DashboardMapping.ResolvedMappings mappings = mappingService.resolve(
                access.userId(), projectId);
        return ApiResponse.ok(applicationService.analyze(criteria, access, mappings));
    }

    @GetMapping("/operations")
    public ApiResponse<DashboardResponses.Operations> operations(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime endTime,
            HttpServletRequest request) {
        DashboardAccessContext access = accessService.resolve(request);
        DashboardCriteria criteria = criteriaService.resolve(projectId,
                ruleCode, ruleName, startTime, endTime);
        return ApiResponse.ok(operationsService.analyze(criteria, access));
    }

    @GetMapping("/governance")
    public ApiResponse<DashboardResponses.Governance> governance(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime endTime,
            HttpServletRequest request) {
        DashboardAccessContext access = accessService.resolve(request);
        DashboardCriteria criteria = criteriaService.resolve(projectId,
                ruleCode, ruleName, startTime, endTime);
        return ApiResponse.ok(governanceService.analyze(criteria, access));
    }

    @GetMapping("/settings")
    public ApiResponse<DashboardResponses.Settings> settings(
            @RequestParam(required = false) Long projectId,
            HttpServletRequest request) {
        DashboardAccessContext access = accessService.resolve(request);
        validateProject(projectId);
        return ApiResponse.ok(mappingService.settings(access.userId(), projectId,
                access.userId() != null));
    }

    @PutMapping("/settings")
    public ApiResponse<DashboardResponses.Settings> saveSettings(
            @RequestParam(required = false) Long projectId,
            @RequestBody SettingsUpdateRequest body,
            HttpServletRequest request) {
        DashboardAccessContext access = accessService.resolve(request);
        if (access.userId() == null) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PERMISSION_DENIED, "当前模式仅支持查看系统默认映射");
        }
        validateProject(projectId);
        mappingService.saveScope(access.userId(), access.username(), projectId,
                body == null ? null : body.mappings());
        return ApiResponse.ok(mappingService.settings(access.userId(), projectId, true));
    }

    @DeleteMapping("/settings/{metricField}")
    public ApiResponse<DashboardResponses.Settings> deleteSetting(
            @RequestParam(required = false) Long projectId,
            @PathVariable DashboardMetricField metricField,
            HttpServletRequest request) {
        DashboardAccessContext access = accessService.resolve(request);
        if (access.userId() == null) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PERMISSION_DENIED, "当前模式仅支持查看系统默认映射");
        }
        validateProject(projectId);
        mappingService.deleteField(access.userId(), access.username(),
                projectId, metricField);
        return ApiResponse.ok(mappingService.settings(access.userId(), projectId, true));
    }

    private void validateProject(Long projectId) {
        if (projectId != null) criteriaService.resolve(projectId, null, null);
    }

    public record SettingsUpdateRequest(
            Map<DashboardMetricField,
                    DashboardMapping.StoredMapping> mappings) {
    }
}
