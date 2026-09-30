package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.transfer.OfflineResourceTransferService;
import com.hengshucredit.rule.server.transfer.TransferRootRequest;
import com.hengshucredit.rule.server.transfer.TransferImportOptions;
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
    @Resource private OfflineResourceTransferService service;
    @Resource private OfflineResourceImportService importService;
    @Resource private ConsoleOperatorResolver operatorResolver;
    @Resource private RuleLineageService lineageService;
    @Resource private OfflineTransferLogService logService;

    @GetMapping("/resources")
    @RequirePermission("rule:view")
    public R<Page<Map<String, Object>>> resources(@RequestParam String nodeType,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Long projectId,
                                                 @RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return R.ok(lineageService.pageOptions(nodeType, keyword, projectId, pageNum, pageSize));
        } catch (IllegalArgumentException error) {
            return R.fail(422, error.getMessage());
        }
    }

    @PostMapping(value = "/export", produces = "application/zip")
    @RequirePermission("rule:view")
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

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("rule:view")
    public R<Map<String, Object>> preview(@RequestPart("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) return R.fail(422, "离线迁移包不能为空");
            return R.ok(service.preview(file.getBytes()));
        } catch (IllegalArgumentException error) {
            return R.fail(422, error.getMessage());
        } catch (Exception error) {
            return R.fail(500, "读取离线迁移包失败: " + error.getMessage());
        }
    }

    @PostMapping(value = "/preview/options", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("rule:view")
    public R<Map<String, Object>> previewWithOptions(@RequestPart("file") MultipartFile file,
                                                     @RequestPart(value = "options", required = false) String options) {
        try {
            if (file == null || file.isEmpty()) return R.fail(422, "离线迁移包不能为空");
            TransferImportOptions parsed = options == null || options.isBlank()
                    ? new TransferImportOptions(null, "PROJECT", "REUSE", "SUFFIX", "_imported", false, null, null, Map.of())
                    : JSON.parseObject(options, TransferImportOptions.class);
            return R.ok(service.preview(file.getBytes(), parsed));
        } catch (IllegalArgumentException error) {
            return R.fail(422, error.getMessage());
        } catch (Exception error) {
            return R.fail(500, "读取离线迁移包失败: " + error.getMessage());
        }
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("rule:edit")
    public R<Map<String, Object>> importPackage(@RequestPart("file") MultipartFile file,
                                                @RequestPart("options") String options) {
        try {
            if (file == null || file.isEmpty()) return R.fail(422, "离线迁移包不能为空");
            TransferImportOptions parsed = JSON.parseObject(options, TransferImportOptions.class);
            String operator = operatorResolver == null ? ConsoleOperatorResolver.SYSTEM_CONSOLE : operatorResolver.resolve();
            Map<String, Object> result = importService.apply(file.getBytes(), parsed, operator);
            result.put("options", parsed);
            if (logService != null) logService.recordImport(file.getBytes(), result, operator, null);
            return R.ok(result);
        } catch (IllegalArgumentException error) {
            if (logService != null && file != null) {
                try { logService.recordImport(file.getBytes(), Map.of(), ConsoleOperatorResolver.SYSTEM_CONSOLE, error.getMessage()); } catch (Exception ignored) { }
            }
            return R.fail(422, error.getMessage());
        } catch (IllegalStateException error) {
            if (logService != null && file != null) {
                try { logService.recordImport(file.getBytes(), Map.of(), ConsoleOperatorResolver.SYSTEM_CONSOLE, error.getMessage()); } catch (Exception ignored) { }
            }
            return R.fail(409, error.getMessage());
        } catch (Exception error) {
            if (logService != null && file != null) {
                try { logService.recordImport(file.getBytes(), Map.of(),
                        ConsoleOperatorResolver.SYSTEM_CONSOLE, error.getMessage()); } catch (Exception ignored) { }
            }
            return R.fail(500, "导入离线配置失败: " + error.getMessage());
        }
    }

    @GetMapping("/logs")
    @RequirePermission("rule:view")
    public R<com.baomidou.mybatisplus.core.metadata.IPage<OfflineTransferLog>> logs(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String operationType) {
        return R.ok(logService.page(pageNum, pageSize, operationType));
    }

    @GetMapping("/logs/{id}")
    @RequirePermission("rule:view")
    public R<OfflineTransferLog> logDetail(@PathVariable Long id) {
        OfflineTransferLog log = logService.getById(id);
        return log == null ? R.fail(404, "迁移日志不存在") : R.ok(log);
    }
}
