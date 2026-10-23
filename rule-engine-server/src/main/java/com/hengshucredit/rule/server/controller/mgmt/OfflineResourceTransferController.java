package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.transfer.OfflineResourceTransferService;
import com.hengshucredit.rule.server.transfer.TransferRootRequest;
import com.hengshucredit.rule.server.transfer.TransferImportOptions;
import com.hengshucredit.rule.server.transfer.TransferExportRequest;
import com.hengshucredit.rule.server.transfer.OfflineResourceImportService;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import com.hengshucredit.rule.server.service.RuleLineageService;
import com.hengshucredit.rule.server.service.OfflineTransferLogService;
import com.hengshucredit.rule.model.entity.OfflineTransferLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 迁移包只处理配置；名单记录另走名单内容导出接口。 */
@RestController
@RequestMapping("/api/rule/transfer")
public class OfflineResourceTransferController {
    @Resource private com.hengshucredit.rule.server.transfer.TransferVersionService versionTransfers;

    @GetMapping("/version-candidates")
    @RequirePermission("transfer:view")
    public ApiResponse<List<Map<String, Object>>> versionCandidates(@RequestParam String refType, @RequestParam Long resourceId) {
        return ApiResponse.ok(versionTransfers.candidates(refType, resourceId));
    }
    @Resource private OfflineResourceTransferService service;
    @Resource private OfflineResourceImportService importService;
    @Resource private ConsoleOperatorResolver operatorResolver;
    @Resource private RuleLineageService lineageService;
    @Resource private OfflineTransferLogService logService;

    @GetMapping("/resources")
    @RequirePermission("transfer:view")
    public ApiResponse<Page<Map<String, Object>>> resources(@RequestParam String nodeType,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Long projectId,
                                                 @RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return ApiResponse.ok(lineageService.pageOptions(nodeType, keyword, projectId, pageNum, pageSize));
        } catch (com.hengshucredit.rule.server.security.ConsoleAccessDeniedException denied) {
            throw denied;
        } catch (IllegalArgumentException error) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.REQUEST_REJECTED, error.getMessage());
        }
    }

    @PostMapping(value = "/export", produces = "application/zip")
    @RequirePermission("transfer:export")
    public ResponseEntity<byte[]> export(@RequestBody List<TransferRootRequest> roots) {
        String operator = operatorResolver == null ? ConsoleOperatorResolver.SYSTEM_CONSOLE : operatorResolver.resolve();
        try {
            byte[] bytes = service.export(roots);
            if (logService != null) logService.recordExport(roots, bytes, operator);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tianshu-resource-transfer.zip")
                .header("X-Transfer-Package", "TIANSHU_RESOURCE_TRANSFER")
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(bytes.length)
                .body(bytes);
        } catch (RuntimeException error) {
            if (logService != null) logService.recordFailure("EXPORT", roots, operator, error.getMessage());
            throw error;
        }
    }

    @PostMapping(value = "/export/options", produces = "application/zip")
    @RequirePermission("transfer:export")
    public ResponseEntity<byte[]> exportWithOptions(@RequestBody TransferExportRequest request) {
        String operator = operatorResolver == null ? ConsoleOperatorResolver.SYSTEM_CONSOLE : operatorResolver.resolve();
        List<TransferRootRequest> roots = request == null ? List.of() : request.roots();
        try {
            byte[] bytes = service.export(roots, request == null || request.recursive());
            if (logService != null) logService.recordExport(roots, bytes, operator);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tianshu-resource-transfer.zip")
                    .header("X-Transfer-Package", "TIANSHU_RESOURCE_TRANSFER")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (RuntimeException error) {
            if (logService != null) logService.recordFailure("EXPORT", roots, operator, error.getMessage());
            throw error;
        }
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("transfer:import")
    public ApiResponse<Map<String, Object>> preview(@RequestPart("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.REQUEST_REJECTED, "离线迁移包不能为空");
            return ApiResponse.ok(service.preview(file.getBytes()));
        } catch (com.hengshucredit.rule.server.security.ConsoleAccessDeniedException denied) {
            throw denied;
        } catch (IllegalArgumentException error) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.REQUEST_REJECTED, error.getMessage());
        } catch (Exception error) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.SYSTEM_ERROR, "读取离线迁移包失败: " + error.getMessage());
        }
    }

    @PostMapping(value = "/preview/options", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("transfer:import")
    public ApiResponse<Map<String, Object>> previewWithOptions(@RequestPart("file") MultipartFile file,
                                                     @RequestPart(value = "options", required = false) String options) {
        try {
            if (file == null || file.isEmpty()) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.REQUEST_REJECTED, "离线迁移包不能为空");
            TransferImportOptions parsed = options == null || options.isBlank()
                    ? new TransferImportOptions(null, "PROJECT", "REUSE", "SUFFIX", "_imported", false, null, null, Map.of())
                    : JSON.parseObject(options, TransferImportOptions.class);
            return ApiResponse.ok(service.preview(file.getBytes(), parsed));
        } catch (com.hengshucredit.rule.server.security.ConsoleAccessDeniedException denied) {
            throw denied;
        } catch (IllegalArgumentException error) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.REQUEST_REJECTED, error.getMessage());
        } catch (Exception error) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.SYSTEM_ERROR, "读取离线迁移包失败: " + error.getMessage());
        }
    }

    @GetMapping("/resource-fields")
    @RequirePermission("transfer:view")
    public ApiResponse<List<Map<String, Object>>> resourceFields(@RequestParam String resourceType,
                                                        @RequestParam Long resourceId) {
        return ApiResponse.ok(service.resourceFields(resourceType, resourceId));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("transfer:import")
    public ApiResponse<Map<String, Object>> importPackage(@RequestPart("file") MultipartFile file,
                                                @RequestPart("options") String options) {
        try {
            if (file == null || file.isEmpty()) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.REQUEST_REJECTED, "离线迁移包不能为空");
            TransferImportOptions parsed = JSON.parseObject(options, TransferImportOptions.class);
            String operator = operatorResolver == null ? ConsoleOperatorResolver.SYSTEM_CONSOLE : operatorResolver.resolve();
            Map<String, Object> result = importService.apply(file.getBytes(), parsed, operator);
            result.put("options", parsed);
            if (logService != null) logService.recordImport(file.getBytes(), result, operator, null);
            return ApiResponse.ok(result);
        } catch (com.hengshucredit.rule.server.security.ConsoleAccessDeniedException denied) {
            throw denied;
        } catch (IllegalArgumentException error) {
            if (logService != null && file != null) {
                try { logService.recordImport(file.getBytes(), Map.of(), ConsoleOperatorResolver.SYSTEM_CONSOLE, error.getMessage()); } catch (Exception ignored) { }
            }
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.REQUEST_REJECTED, error.getMessage());
        } catch (IllegalStateException error) {
            if (logService != null && file != null) {
                try { logService.recordImport(file.getBytes(), Map.of(), ConsoleOperatorResolver.SYSTEM_CONSOLE, error.getMessage()); } catch (Exception ignored) { }
            }
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_CONFLICT, error.getMessage());
        } catch (Exception error) {
            if (logService != null && file != null) {
                try { logService.recordImport(file.getBytes(), Map.of(),
                        ConsoleOperatorResolver.SYSTEM_CONSOLE, error.getMessage()); } catch (Exception ignored) { }
            }
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.SYSTEM_ERROR, "导入离线配置失败: " + error.getMessage());
        }
    }

    @GetMapping("/logs")
    @RequirePermission("transfer:view")
    public ApiResponse<com.baomidou.mybatisplus.core.metadata.IPage<OfflineTransferLog>> logs(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String operationType) {
        return ApiResponse.ok(logService.page(pageNum, pageSize, operationType));
    }

    @GetMapping("/logs/{id}")
    @RequirePermission("transfer:view")
    public ApiResponse<OfflineTransferLog> logDetail(@PathVariable Long id) {
        OfflineTransferLog log = logService.getById(id);
        return log == null ? ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "迁移日志不存在") : ApiResponse.ok(log);
    }

    @GetMapping("/logs/{id}/lineage")
    @RequirePermission("transfer:view")
    public ApiResponse<Map<String, Object>> logLineage(@PathVariable Long id) {
        if (logService.getById(id) == null) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "迁移日志不存在");
        return ApiResponse.ok(logService.lineage(id));
    }
}
