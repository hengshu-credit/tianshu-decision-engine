package com.hengshucredit.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.dto.RuleListChangeBatchResult;
import com.hengshucredit.rule.model.dto.RuleListRecordChangeRequest;
import com.hengshucredit.rule.model.entity.RuleListLibrary;
import com.hengshucredit.rule.model.entity.RuleListRecord;
import com.hengshucredit.rule.model.entity.RuleListRecordLog;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import com.hengshucredit.rule.server.service.RuleListChangeBatchService;
import com.hengshucredit.rule.server.service.RuleListService;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/rule/list")
public class RuleListController {

    @Resource
    private RuleListService listService;

    @Resource
    private RuleListChangeBatchService changeBatchService;

    @Resource
    private ConsoleOperatorResolver operatorResolver;

    @GetMapping("/library")
    public R<IPage<RuleListLibrary>> listLibraries(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String listType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return R.ok(listService.pageLibraries(pageNum, pageSize, projectId, projectCode, projectName,
                scope, listType, status, keyword));
    }

    @GetMapping("/library/{id:\\d+}")
    public R<RuleListLibrary> getLibrary(@PathVariable Long id) {
        return R.ok(listService.getById(id));
    }

    @GetMapping("/{listId:\\d+}/record")
    public R<IPage<RuleListRecord>> listRecords(
            @PathVariable Long listId,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean effectiveOnly) {
        return R.ok(listService.pageRecords(listId, pageNum, pageSize, itemType, status, keyword, effectiveOnly));
    }

    @PostMapping("/{listId:\\d+}/change-batch")
    @RequirePermission("approval:submit")
    public R<RuleListChangeBatchResult> stageRecordChange(
            @PathVariable Long listId,
            @RequestBody RuleListRecordChangeRequest request) {
        try {
            return R.ok(changeBatchService.stageSingle(listId, request, operatorResolver.resolve()));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/{listId:\\d+}/log")
    public R<IPage<RuleListRecordLog>> listLogs(
            @PathVariable Long listId,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long recordId,
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) String itemContent,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return R.ok(listService.pageLogs(listId, pageNum, pageSize, recordId, itemType, itemContent,
                operation, keyword, startTime, endTime));
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() throws Exception {
        return workbookResponse(listService.createTemplateWorkbook(), "名单导入模板.xlsx");
    }

    @GetMapping("/{listId:\\d+}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long listId) throws Exception {
        RuleListLibrary library = listService.getById(listId);
        String prefix = library != null && library.getListCode() != null ? library.getListCode() : "list";
        return workbookResponse(listService.exportRecords(listId), prefix + "-名单内容.xlsx");
    }

    @PostMapping("/{listId:\\d+}/import")
    @RequirePermission("approval:submit")
    public R<RuleListChangeBatchResult> importRecords(
            @PathVariable Long listId,
            @RequestPart("file") MultipartFile file) {
        try {
            return R.ok(changeBatchService.stageImport(listId, file, operatorResolver.resolve()));
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    private ResponseEntity<byte[]> workbookResponse(Workbook workbook, String fileName) throws Exception {
        try (Workbook wb = workbook; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }
}
